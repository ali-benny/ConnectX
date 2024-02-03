package connectx.Bob42;

import java.util.concurrent.TimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
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
    private Integer[] moveOrder;
    private int LAST_TIME; //
    private double MAX_TIME;
    public Bob42() {
    }

    public void initPlayer(int M, int N, int X, boolean first, int timeout_in_secs) {
        myWin = first ? CXGameState.WINP1 : CXGameState.WINP2;
        yourWin = first ? CXGameState.WINP2 : CXGameState.WINP1;

        TIMEOUT = timeout_in_secs;
        START = System.currentTimeMillis(); // Save starting time
        LAST_TIME = N >= 40 ? TIMEOUT/2 : 3*TIMEOUT/4;
        MAX_TIME = N >= 40 ? (98.0 / 100.0) : (99.0 / 100.0);  // 90% del tempo per N >= 40, 99% per N < 40

        // move ordering
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
        if (System.currentTimeMillis() - START >= (TIMEOUT * MAX_TIME) * 1000) {
            throw new TimeoutException();
        }
    }

    /**
     * The selectSort function is a selection sort algorithm that sorts the available columns in descending order.
     * 
     *
     * @param board Mark and unmark columns
     * @param avlCol Store the columns that are available to be played
     * @param start Tell the function where to start from in the array
     *
     * @return The index of the maximum value in the array
     */
    public int selectSort(CXBoard board, Integer[] avlCol, int start){
        int maxIndex = start;
        long max = Integer.MIN_VALUE + 1;
        if (!board.fullColumn(avlCol[start])) {
            board.markColumn(avlCol[start]);
            max = searchTranspositionTable(board);
            board.unmarkColumn();
        }  
        try {
        for(int i = start+1; i < avlCol.length; i++) {
            checktimeout();
            if (!board.fullColumn(avlCol[i])) {
                board.markColumn(avlCol[i]);
                long val = searchTranspositionTable(board);
                board.unmarkColumn();
                if(val > max) {
                    maxIndex = i;
                    max = val;
                }
            }
        }
        } catch (TimeoutException e) {}
        int tmp = avlCol[start];         // mette in testa il valore maggiore trovato
        avlCol[start] = avlCol[maxIndex];
        avlCol[maxIndex] = tmp;
        return avlCol[start];
    }

    public int selectColumn(CXBoard board) {
        START = System.currentTimeMillis();
        
        int bestCol = -1;
        int alpha = Integer.MIN_VALUE + 1;      //! Deve esserci il +1 altrimenti non sono opposti
        int beta = Integer.MAX_VALUE;
        Integer[] avlCol = board.getAvailableColumns();
        int depth = getFreeCells(board).length >= 35 ? 20 : getFreeCells(board).length;

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
            while (depth >= getFreeCells(board).length){
            for (int i = 0; i < board.N; i++) {
                if (!board.fullColumn(moveOrder[i])) { // If the middlest column is not full
                    int col = selectSort(board, moveOrder, i);
                    board.markColumn(col);
                    if (board.gameState() == myWin){           // se fa vincere il giocatore, termina ritornando la colonna corrente
                    // System.err.println("W"+evaluate(board));
                        board.unmarkColumn();
                        return col;
                    }
                    int eval = alphaBeta_m(board, depth, alpha, beta);
                    if (eval > alpha) {
                        alpha = eval;
                        bestCol = col;
                    }
                    // System.err.println("eval "+eval+" ? "+board.gameState());
                    board.unmarkColumn();
                }
            }
            depth--;
                if (System.currentTimeMillis() - START > 4*TIMEOUT/5 * 1000)  // se 1/4 del tempo
                    break;
                if (System.currentTimeMillis() - START > TIMEOUT/2 * 1000)  // ho 3/4 del tempo a disposizione
                    depth -= 3;
            }
            if (bestCol == -1){         // No winning move found
                 // System.err.println("No winning move found/");
                CXCell[] freeCells = getFreeCells(board);
                depth = getFreeCells(board).length/2;
                int bestScore = Integer.MIN_VALUE + 1;
                avlCol = board.getAvailableColumns();
                int maxFreeCell = maxColFreeCells(board, freeCells, avlCol);

                try { 
                for (int i=0; i<avlCol.length; i++){ // If the middlest column is full, search for the first free cell in the moveOrder array (from the middlest column to the sides                    
                    board.markColumn(avlCol[i]);
                    int eval = alphaBeta_m(board, depth, alpha, beta);
                    board.unmarkColumn();
                    checktimeout();
                    // System.err.print(board.gameState());
                    if (board.gameState() != yourWin){
                        if (board.gameState() == myWin || eval > bestScore){
                            bestCol = avlCol[i];
                            alpha = alpha > eval ? eval : alpha;
                            bestScore = eval;        
                        } else if (bestScore == eval){
                            if (board.gameState() != yourWin || board.gameState() == myWin)
                                bestCol = avlCol[i];    
                        } 
                        if (bestScore == Integer.MIN_VALUE + 1 && i >= avlCol.length -1){
                            bestCol = selectSort(board, avlCol, i);
                            // System.err.println("@"+bestCol);
                            if (bestScore == Integer.MIN_VALUE + 1 && maxFreeCell != -1 && board.markColumn(bestCol) == yourWin){
                                // System.err.println("max: "+maxFreeCell+" best: "+bestCol);
                                bestCol = maxFreeCell;
                            }
                            board.unmarkColumn();
                        }
                        // System.err.println(avlCol[i]+" score: "+eval+" best: "+bestCol);
                    }
                    if (depth > 0) depth -= 2;
                    else break;
                }
                } catch (TimeoutException e) {
                    if (bestCol == -1 || board.fullColumn(bestCol)) { // bestCol could be illigal move
                        // System.err.println("using random col");
                        return avlCol[Math.abs(new Random().nextInt()) % avlCol.length];    
                    }
                }
                // System.err.println("Best column: "+bestCol);
            }
        }
        return bestCol;
    }

 private int maxColFreeCells(CXBoard board, CXCell[] freeCells, Integer[] column){
        int[] freeCellsXCol = new int[column.length];
        int maxFreeCells = 0;
        int maxFreeCellsColumn = -1;
        for (int i=0; i < column.length; i++){
            for (int j=0; j < freeCells.length; j++)
                if (freeCells[j].j == column[i] && !board.fullColumn(freeCells[j].j))
                    freeCellsXCol[i]++;
            if (freeCellsXCol[i] > maxFreeCells) {
                maxFreeCells = freeCellsXCol[i];
                maxFreeCellsColumn = column[i];
            }
        }
        return maxFreeCellsColumn;
    }

    private CXCell[] getFreeCells(CXBoard board) {
        List<CXCell> freeCells = new ArrayList<>();
        for (int i = 0; i < board.M; i++) {
            for (int j = 0; j < board.N; j++) {
                if (board.cellState(i, j) == CXCellState.FREE) {
                    freeCells.add(new CXCell(i, j, CXCellState.FREE));
                }
            }
        }
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

            int transpositionScore = searchTranspositionTable(board);
            if (transpositionScore != Integer.MIN_VALUE +1) { // If the score is already in the transposition table, return it
                return transpositionScore;
            }
            if (depth <= 0 || board.gameState() != CXGameState.OPEN) // If the depth is 0 or the game is over, return the evaluation
                return evaluate(board);
            if (System.currentTimeMillis() - START >= LAST_TIME * 1000)
                throw new TimeoutException();
            else checktimeout();        // manca 1% del timeout
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
        } catch (TimeoutException e) {}
        return evaluate(board);
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

            int transpositionScore = searchTranspositionTable(board);
            if (transpositionScore != Integer.MIN_VALUE+1) {
                return transpositionScore;
            }
            if (depth <= 0 || board.gameState() != CXGameState.OPEN)
                return evaluate(board);
            if (System.currentTimeMillis() - START >= LAST_TIME * 1000)
                throw new TimeoutException();
            else checktimeout();        // manca 1% del timeout
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
        } catch (TimeoutException e) {}
        return evaluate(board);
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
            score += evalDiag(board, true);
            return score;
        }
    }

            /** AUXILIAR FUNCTIONS FOR EVALUATE **/
    
    /**
     * The tryYourNextMove function is a helper function that simulates the opponent's next move.
     * It returns the score of the board after your opponent makes their next move.
     * 
     * @see #evaluate(CXBoard)
     *
     * @param board Access the board
     * @param row Determine the row of the cell on which to place a pawn
     * @param col Specify the column of the cell to be checked
     * @param pown Store the number of powns that each player has
     * @param combocount Store the number of combos that have been made
     *
     * @return potential score of the opponent's next move
     */
    private int tryYourNextMove(CXBoard board, int row, int col, int[] pown, int[] combocount){
        CXCellState cell = board.cellState(row, col);
        int score = 0;
        if (cell == CXCellState.FREE) {
            // Simulate opponent's move
            int[] pownCopy = Arrays.copyOf(pown, pown.length);
            int[] comboCopy = Arrays.copyOf(combocount, combocount.length);
            handlePownCombo(1, pownCopy, 2, comboCopy);

            // Evaluate potential score
            score = scoreCount(pownCopy, comboCopy);
        }
        return score;
    }
    /**
     * The handlePownCombo function is used to count the number of pawns in a row, and the number of consecutive rows.
     * 
     * @see #evaluate(CXBoard)
     * 
     * @param who Determine which player is playing
     * @param pown Keep track of the number of powns each player has
     * @param lastPown Keep track of the last player who played a pawn
     * @param combo Keep track of the number of times a player has won consecutively
     *
     * @return The lastpown and combo variables
     */
    public void handlePownCombo(int who, int[] pown, int lastPown, int[] combo){
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
            combo[lastPown] = 1;
        }
    }

    public int scoreCount(int[] pown, int[] combo){
        int score = 0;
        score += pown[0] - pown[1] + pown[2]*2;
        score += combo[0] * combo[0] * combo[0] * combo[0];
        score += combo[1] * combo[1] * combo[1] * combo[1];
        return score;
    }
    
    /**
     * comboCount function
     * Checks if either player has achieved the minimum combo length.
     * 
     *
     * @param minCombo Determine the minimum pown needed to get a combo
     * @param score Store the score how much combo for each player
     * @param combo Store the number of pown align for each player
     *
     * @return The score[] array, which is the first argument
     */
    public int[] comboCount(int minCombo, int[] score, int[] combo){
        if (combo[0] >= minCombo){
            score[0] += combo[0] * combo[0];
            score[0] = combo[0] >= minCombo*2-1 ? score[0] + combo[0]*combo[0] : score[0];
        }
        if (combo[1] >= minCombo){
            score[1] += combo[1] * combo[1];
            score[1] = combo[1] >= minCombo*2-1 ? score[1] + combo[1]*combo[1] : score[1];
        }
        return score;
    }
    
    public int evalRow(CXBoard board, int row){
        // System.err.printf(" eR ");
        int score = 0;  
        int lastPown = 2;      // di chi è l'ultima pedina incontrata (0 = mia, 1 = avversario, 2 = free)
        int[] pown = new int[3];        // pown[0] = mio, pown[1] = avversario, pown[2] = free
        int[] combocount = new int[2];       // combo[0] = combo mio, combo[1] = combo avversario
        // try{
        //     checktimeout();
        for (int i=0; i+board.X < board.N; i++){
            int[] combo = new int[2]; 
            for (int k = 0; k < board.X; k++) {    // combo[0] = combo mio, combo[1] = combo avversario
                CXCellState cell = board.cellState(row, i+k);
                if (cell == CXCellState.FREE){
                    lastPown = 2;
                    pown[lastPown]++;
                } else if (cell == CXCellState.P1){
                    handlePownCombo(0, pown, lastPown, combo);
                } else if (cell == CXCellState.P2){                
                    handlePownCombo(1, pown, lastPown, combo);
                }
            }
            combocount = comboCount(board.X/2, combocount, combo);
            score -= tryYourNextMove(board, row, i, pown, combocount);
        }
        // } catch (TimeoutException e) {}
        score += scoreCount(pown, combocount);
        return score*score*score;
    }

    private int evalCol(CXBoard board, int col){
        // System.err.printf(" eC ");
        int score = 0;  
        int lastPown = 2;      // di chi è l'ultima pedina incontrata (0 = mia, 1 = avversario, 2 = free)
        int[] pown = new int[3];        // pown[0] = mio, pown[1] = avversario, pown[2] = free
        int[] combocount = new int[2];       // combo[0] = combo mio, combo[1] = combo avversario
        // try{
        //     checktimeout();
        for (int i=0; i+ board.X < board.M; i++){
            int[] combo = new int[2];       // combo[0] = combo mio, combo[1] = combo avversario
            for (int k = 0; k < board.X; k++) {    // combo[0] = combo mio, combo[1] = combo avversario
                CXCellState cell = board.cellState(i+k, col);
                if (cell == CXCellState.FREE){
                    lastPown = 2;
                    pown[lastPown]++;
                } else if (cell == CXCellState.P1){
                    handlePownCombo(0, pown, lastPown, combo);
                } else if (cell == CXCellState.P2){
                    handlePownCombo(1, pown, lastPown, combo);
                }
            }
            combocount = comboCount(board.X/2, combocount, combo);
            score -= tryYourNextMove(board, i, col, pown, combocount);
        }
        // } catch (TimeoutException e) {}
        score += scoreCount(pown, combocount);
        return score*score*score;
    }

    private int evalDiag(CXBoard board, boolean inverse){
        // System.err.printf(" eD ");
        int invrow = inverse ? board.M : 0;
        int invcol = inverse ? board.N : 0;
        int score = 0;
        int lastPown = 2;      // di chi è l'ultima pedina incontrata (0 = mia, 1 = avversario, 2 = free)
        int[] pown = new int[3];        // pown[0] = mio, pown[1] = avversario, pown[2] = free
        int[] combocount = new int[2];       // combo[0] = combo mio, combo[1] = combo avversario

        // System.err.println("N: "+board.N+" M: "+board.M+" X: "+board.X+" row "+invrow+" -> "+(board.M - maxRow));
        // try{
        //     checktimeout();
        for (int row = invrow; row + board.X <= board.M - invrow; row++){
            for (int col = invcol; col + board.X <= board.N - invcol; col++){ 
                int[] combo = new int[2];       // combo[0] = combo mio, combo[1] = combo avversario
                for (int k = 0; k < board.X; k++){
                    if (board.cellState(row+k, col+k) == CXCellState.FREE){
                        lastPown = 2;
                        pown[lastPown]++;
                    } else if (board.cellState(row+k, col+k) == CXCellState.P1){                    
                        handlePownCombo(0, pown, lastPown, combo);
                    } else if (board.cellState(row+k, col+k) == CXCellState.P2){
                        handlePownCombo(1, pown, lastPown, combo);
                    }  
                }
                combocount = comboCount(board.X/2, combocount, combo);
                score -= tryYourNextMove(board, row, col, pown, combocount);
            }
        }
        // } catch (TimeoutException e) {}
        score += scoreCount(pown, combocount);
        return score*score*score;
    }
    
    /**
     * The hash function is used to hash the current state of the board.
     * @param board
     * @return hash code
     * 
     * @Time complexity: O(m*n) time
     */
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

    /**
     * Search the transposition table for  for the score of the given board state.
     * If it finds one, it returns the value associated with that board state. 
     * Otherwise, it returns Integer.MIN_VALUE+1 
     *
     * @param CXBoard board Get the hash of the board
     *
     * @return The score of the board from the transposition table if it exists, otherwise returns -inf
     * 
     */
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
