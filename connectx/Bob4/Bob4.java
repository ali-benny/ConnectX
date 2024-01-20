package connectx.Bob4;

import java.util.concurrent.TimeoutException;
import java.lang.reflect.Array;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import connectx.CXBoard;
import connectx.CXCell;
import connectx.CXCellState;
import connectx.CXPlayer;
import connectx.CXGameState;

/**
 * Bob4 player
 * 
 * What's new:
 * - Handle depth: non va più in timeout per grandi alberi, ho troncato ad una profondità variabile in base al numero di colonne disponibili
 */


public class Bob4 implements CXPlayer {

    private CXGameState myWin;
    private CXGameState yourWin;
    private int TIMEOUT;
    private long START;
    private long[][][] zobristTable; // Zobrist table for hashing
    private Map<Long, Integer> transpositionTable;
    public Integer[] moveOrder;

    public Bob4() {
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
        if (board.numOfFreeCells() == 0) {
            // The board is full, the game is a draw
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
                        if (moveOrder[i] == -1)
                            System.err.println("!! "+board.numOfFreeCells());
                        return moveOrder[i];
                    }
                    int eval = alphaBeta_m(board, depth, alpha, beta);
                    if (eval > alpha) {
                        alpha = eval;
                        if (moveOrder[i] == -1)
                            System.err.println("!! "+board.numOfFreeCells());
                        bestCol = moveOrder[i];
                    }
                    board.unmarkColumn();
                }
                depth++;
            }
            if (bestCol == -1){
                System.err.println("Free cell = "+board.numOfFreeCells());
                System.err.println("Marked cell = "+board.numOfMarkedCells());  
                CXCell[] cell = board.getMarkedCells();
                bestCol = avlCol[0];         
            }
        }
        System.out.println("BestCol = "+bestCol);
        return bestCol;
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
            if (depth >= 10)
                depth = depth/9;
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
            return evaluate(board);
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
            if (depth >= 10)
                depth = depth/9;
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
            return evaluate(board);
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
        if (board.gameState() == myWin) {
            return Integer.MAX_VALUE;
        } else if (board.gameState() == yourWin) {
            return Integer.MIN_VALUE+1;
        } else if (board.gameState() == CXGameState.DRAW){
            return 0;
        } else // la partita non è finita
            return 1;
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
        return "Bob4";
    }
}
