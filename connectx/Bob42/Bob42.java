package connectx.Bob42;

import java.util.concurrent.TimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import connectx.CXBoard;
import connectx.CXCell;
import connectx.CXCellState;
import connectx.CXPlayer;
import connectx.CXGameState;

/**
 * Bob42 player
 * 
 * What's new:
 * - Handle depth: non va più in timeout per grandi alberi, ho troncato ad una profondità variabile in base al numero di colonne disponibili
 */


public class Bob42 implements CXPlayer {

    private CXGameState myWin;
    private CXGameState yourWin;
    private int TIMEOUT;
    private long START;
    private long[][][] zobristTable; // Zobrist table for hashing
    private Map<Long, Integer> transpositionTable;
    public Integer[] moveOrder;

    public Bob42() {
    }

    public void initPlayer(int M, int N, int X, boolean first, int timeout_in_secs) {
        myWin = first ? CXGameState.WINP1 : CXGameState.WINP2;
        yourWin = first ? CXGameState.WINP2 : CXGameState.WINP1;

        TIMEOUT = timeout_in_secs;
        START = System.currentTimeMillis(); // Save starting time

        moveOrder = new Integer[N];
        for (int i = 0; i < N; i++) {
            moveOrder[i] = N/2 + (i % 2 == 0 ? i/2 : -i/2 - 1); // i=0, moveOrder[0]=N/2, moveOrder[1]=N/2 -1, moveOrder[2]=N/2 +1, moveOrder[3]=N/2 -2, moveOrder[4]=N/2 +2, ...
        }

        Random rand = new Random();
        zobristTable = new long[M][N][3]; // generate zobrist table (3 values for each cell: 0 = free, 1 = P1, 2 = P2)
        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                zobristTable[i][j][0] = rand.nextLong();
                zobristTable[i][j][1] = rand.nextLong();
                zobristTable[i][j][2] = rand.nextLong();
            }
        }
        transpositionTable = new HashMap<Long, Integer>();
    }

    // Check if the time is over
    private void checktimeout() throws TimeoutException {
        if (System.currentTimeMillis() - START > TIMEOUT * 1000) {
            throw new TimeoutException();
        }
    }

    public int selectColumn(CXBoard board) {
        START = System.currentTimeMillis();
        
        int bestCol = -1;
        int alpha = Integer.MIN_VALUE + 1;      //! Deve esserci il +1 altrimenti non sono opposti
        int beta = Integer.MAX_VALUE;
        Integer[] avlCol = board.getAvailableColumns();
        int depth = avlCol.length; 

        if (board.numOfFreeCells() == 0) {  // The board is full, the game is a draw
            System.err.println("The board is full, the game is a draw");
            return -1;
        }
        if (board.numOfMarkedCells() == 0) {
            board.markColumn(moveOrder[0]); // Start from the middle column
            alphaBeta_m(board, depth, alpha, beta); // Fill the transposition table
            board.unmarkColumn();
            return moveOrder[0];
        } else {
            for (int i = 0; i < board.N; i++) {
                if (!board.fullColumn(moveOrder[i])) { // If the middlest column is not full
                    board.markColumn(moveOrder[i]);
                    if (board.gameState() == myWin){           // se fa vincere il giocatore, termina ritornando la colonna corrente
                        board.unmarkColumn();
                        return moveOrder[i];
                    }
                    int eval = alphaBeta_m(board, depth, alpha, beta);
                    if (eval > alpha) {
                        alpha = eval;
                        bestCol = moveOrder[i];
                    }
                    board.unmarkColumn();
                }
                depth++;
            }
            if (bestCol == -1){         // No winning move found
                System.err.println("No winning move found");
                CXCell[] freeCells = getFreeCells(board);
                depth = board.getAvailableColumns().length;
                int lastScore = Integer.MIN_VALUE + 1;
                for (int i=0; i<freeCells.length; i++){ // If the middlest column is full, search for the first free cell in the moveOrder array (from the middlest column to the sides
                    try{
                        checktimeout();
                        for (CXCell cell : freeCells) {
                            board.markColumn(cell.j);
                            if (board.gameState() == myWin){           // se fa vincere il giocatore, termina ritornando la colonna corrente
                                board.unmarkColumn();
                                return cell.j;
                            }                             
                            int eval = alphaBeta_m(board, depth, alpha, beta);
                            if (eval > lastScore){
                                bestCol = cell.j;
                                lastScore = eval;
                            }
                            else if (i >= freeCells.length && bestCol == -1){
                                bestCol = cell.j;
                            } else if (lastScore >= eval){
                                // System.err.printf(" L ");
                                bestCol = freeCells[i].j;                                
                            } else
                                lastScore = eval;
                            board.unmarkColumn();
                        }
                        depth++;
                    } catch (TimeoutException e) {
                        System.err.printf("~Timeout~");
                        return bestCol;
                    }
                }
        System.err.println("Best column: "+bestCol);
            }
        }
        return bestCol;
    }

    public CXCell[] getFreeCells(CXBoard board) {
        List<CXCell> freeCells = new ArrayList<>();
        for (int i = 0; i < board.M; i++) {
            for (int j = 0; j < board.N; j++) {
                if (board.cellState(i, j) == CXCellState.FREE) {
                    freeCells.add(new CXCell(i, j, CXCellState.FREE));
                }
            }
        }
        System.err.println("Free cells: "+freeCells.size());
        return freeCells.toArray(new CXCell[freeCells.size()]);
    }

    /** 
     *  AlphaBeta for maximizing player (M)
     * 
     * @param board The current game state
     * @param depth The actual depth of the tree
     * @param alpha The alpha value
     * @param beta The beta value
     * @return The best score
     */
    private int alphaBeta_M(CXBoard board, int depth, int alpha, int beta) {
        try {
            checktimeout();

            int transpositionScore = searchTranspositionTable(board);
            if (transpositionScore != Integer.MIN_VALUE +1) { // If the score is already in the transposition table, return it
                return transpositionScore;
            }
            if (depth == 0 || board.gameState() != CXGameState.OPEN) // If the depth is 0 or the game is over, return the evaluation
                return evaluate(board);
            if (depth >= 8)
                depth = depth/5;
            for (int col = 0; col < board.N; col++) {
                if (!board.fullColumn(moveOrder[col])){
                    board.markColumn(moveOrder[col]);
                    int eval = alphaBeta_m(board, depth - 1, alpha, beta);
                    board.unmarkColumn();
                    if (eval >= beta)
                        return beta; // Cutoff
                    if (eval > alpha)
                        alpha = eval;
                }
            }
            saveTranspositionTable(board, alpha); // Save the score in the transposition table
            return alpha;
        } catch (TimeoutException e) {
            int eval = evaluate(board);
            saveTranspositionTable(board, eval);
            System.err.printf("$");
            return eval;
        }
    }

    /**
     * AlphaBeta for minimizing player (m)
     * 
     * @param board The current game state
     * @param depth The actual depth of the tree
     * @param alpha The alpha value
     * @param beta The beta value
     * @return The best score
     */
    private int alphaBeta_m(CXBoard board, int depth, int alpha, int beta) {
        try {
            checktimeout();

            int transpositionScore = searchTranspositionTable(board);
            if (transpositionScore != Integer.MIN_VALUE+1) {
                return transpositionScore;
            }
            if (depth == 0 || board.gameState() != CXGameState.OPEN)
                return evaluate(board);
            if (depth >= 8)
                depth = depth/5;
            for (int col = 0; col < board.N; col++) {
                if (!board.fullColumn(moveOrder[col])){
                    board.markColumn(moveOrder[col]);
                    int eval = alphaBeta_M(board, depth - 1, alpha, beta);
                    board.unmarkColumn();
                    if (eval <= alpha)
                        return alpha; // cutoff
                    if (eval < beta)
                        beta = eval;
                }
            }
            saveTranspositionTable(board, beta);
            return beta;
        } catch (TimeoutException e) {// Handle the timeout exception here
            int eval = evaluate(board);
            saveTranspositionTable(board, eval);
        System.err.printf("¬");
            return eval;
        }
    }

    /**
     * The evaluate function is used to evaluate the current state of the game.
     * It returns a value that represents how good or bad the current state of
     * the game is for this player. The higher this value, the better it is for
     * this player. The lower it, then worse it is for them.
     *
     * @param board Get the current game state
     *
     * @return A value of type int
     */
    private int evaluate(CXBoard board) {
        CXGameState gameState = board.gameState();
        if (gameState == myWin) {
            return Integer.MAX_VALUE;
        } else if (gameState == yourWin) {
            return Integer.MIN_VALUE + 1;
        } else if (gameState == CXGameState.DRAW) {
            return 0;
        } else {
            int score = 0;
            for (int i = 0; i < board.M; i++) {
                score += evalRow(board, i);
            }
            for (int i = 0; i < board.N; i++) {
                score += evalCol(board, i);
            }
            score += evalDiag(board, false);
            // System.err.printf(" E");
            score += evalDiag(board, true);
        // System.err.printf("_");
            return score;
        }
    }

    public void handlePownCombo(int who, int[] pown, int lastPown, int[] combo){
        // System.err.println("who: "+who+" lastPown: "+lastPown+" combo: "+combo[0]+" "+combo[1]);
        if (who == 0){
            pown[who]++;
        } else if (who == 1){
            pown[who]++;
        } else  // free cell
            return;
        
        if (lastPown == who){
            combo[who]++;
        } else {
            lastPown = who;
            combo[who] = 1;
        }
        // System.err.println("who: "+who+" lastPown: "+lastPown+" combo: "+combo[0]+" "+combo[1]);
        // System.err.println("=======");

    }

    public int scoreCount(int minCombo, int[] pown, int[] combo){
        int score = 0;
        score += pown[0] - pown[1] + pown[2];
        if (combo[0] >= minCombo){
            score += combo[0] * combo[0] * combo[0];
        }
        if (combo[1] >= minCombo){
            score -= combo[1] * combo[1] * combo[1];
        }
        return score;
    }

    public int evalRow(CXBoard board, int row){
        // System.err.printf(" eR ");
        int count = 0;  
        int lastPown = 2;      // di chi è l'ultima pedina incontrata (0 = mia, 1 = avversario, 2 = free)
        int[] pown = new int[3];        // pown[0] = mio, pown[1] = avversario, pown[2] = free
        int[] combo = new int[2];       // combo[0] = combo mio, combo[1] = combo avversario
        try{
            checktimeout();
        for (int i=0; i<board.N; i++){
            CXCellState cell = board.cellState(row, i);
            combo[0] = 0;
            if (cell == CXCellState.FREE){
                lastPown = 2;
                pown[lastPown]++;
            } else if (cell == CXCellState.P1){
                handlePownCombo(myWin == CXGameState.WINP1 ? 0 : 1, pown, lastPown, combo);
            } else if (cell == CXCellState.P2){                
                handlePownCombo(myWin == CXGameState.WINP1 ? 0 : 1, pown, lastPown, combo);
            }
        }
        } catch (TimeoutException e) {
            count = scoreCount(board.N/2, pown, combo);
        System.err.printf("R");
            return count*count*count;
        }
        count = scoreCount(board.N/2,pown, combo);
        return count*count*count;
    }

    private int evalCol(CXBoard board, int col){
        // System.err.printf(" eC ");
        int count = 0;  
        int lastPown = 2;      // di chi è l'ultima pedina incontrata (0 = mia, 1 = avversario, 2 = free)
        int[] pown = new int[3];        // pown[0] = mio, pown[1] = avversario, pown[2] = free
        int[] combo = new int[2];       // combo[0] = combo mio, combo[1] = combo avversario
        try{
            checktimeout();
        for (int i=0; i<board.M; i++){
            CXCellState cell = board.cellState(i, col);
            if (cell == CXCellState.FREE){
                lastPown = 2;
                pown[lastPown]++;
            } else if (cell == CXCellState.P1){
                handlePownCombo(myWin == CXGameState.WINP1 ? 0 : 1, pown, lastPown, combo);
            } else if (cell == CXCellState.P2){
                handlePownCombo(myWin == CXGameState.WINP1 ? 0 : 1, pown, lastPown, combo);
            }
        }
        count += pown[0] - pown[1] + pown[2];
        if (combo[0] >= board.X/2){
            count += combo[0] * combo[0] * combo[0];
        }
        if (combo[1] >= board.X/2){
            count -= combo[1] * combo[1] * combo[1];
        }
        } catch (TimeoutException e) {
            count = scoreCount(board.N/2, pown, combo);
        System.err.printf("C");
            return count*count*count;
        }
        count = scoreCount(board.N/2, pown, combo);
        return count*count*count;
    }

    private int evalDiag(CXBoard board, boolean inverse){
        // System.err.printf(" eD ");
        int invrow = inverse ? board.X-1 : 0;
        int maxRow = inverse ? 0 : board.X;
        int score = 0;
        int lastPown = 2;      // di chi è l'ultima pedina incontrata (0 = mia, 1 = avversario, 2 = free)
        int[] pown = new int[3];        // pown[0] = mio, pown[1] = avversario, pown[2] = free
        int[] combo = new int[2];       // combo[0] = combo mio, combo[1] = combo avversario

        // System.err.println("N: "+board.N+" M: "+board.M+" X: "+board.X+" row "+invrow+" -> "+(board.M - maxRow));
        try{
            checktimeout();
        for (int row = invrow; row <= board.M - maxRow; row++){
            for (int col = 0; col <= board.N - board.X; col++){         //!  
                // System.err.println(" col: "+col+" lim "+(board.N - board.X));
                int diagRow = inverse ? row - col : row + col;
                if (diagRow < 0 || diagRow >= board.M) continue; // Skip out of bounds
                if (board.cellState(diagRow, col) == CXCellState.FREE){
                    lastPown = 2;
                    pown[lastPown]++;
                } else if (board.cellState(diagRow, col) == CXCellState.P1){                    
                    handlePownCombo(myWin == CXGameState.WINP1 ? 0 : 1, pown, lastPown, combo);
                } else if (board.cellState(diagRow, col) == CXCellState.P2){
                    handlePownCombo(myWin == CXGameState.WINP1 ? 0 : 1, pown, lastPown, combo);
                }
            }
        }
        } catch (TimeoutException e) {
            score = scoreCount(board.N/2, pown, combo);
        System.err.printf("D");
            return score*score*score;
        }score = scoreCount(board.N/2, pown, combo);
        return score*score*score;
    }
    // Hash the board using the Zobrist table
    private long hash(CXBoard board) {
        long hash = 0;
        for (int i = 0; i < board.M; i++) {
            for (int j = 0; j < board.N; j++) {
                CXCellState cell = board.cellState(i, j);
                if (cell == CXCellState.FREE)
                    continue;
                int cellValue = cell == CXCellState.P1 ? 1 : 2;
                hash ^= zobristTable[i][j][cellValue]; // XOR
            }
        }
        return hash;
    }

    // Search the transposition table for the score of the board
    private int searchTranspositionTable(CXBoard board) {
        long hash = hash(board);
        if (transpositionTable.containsKey(hash)) {
            return transpositionTable.get(hash);
        } else {
            return Integer.MIN_VALUE+1;
        }
    }

    // Save the score of the board in the transposition table
    private void saveTranspositionTable(CXBoard board, int score) {
        long hash = hash(board);
        transpositionTable.put(hash, score);
    }

    public String playerName() {
        return "Bob42";
    }
}
