package connectx.Bob3e;

import java.util.concurrent.TimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import connectx.CXBoard;
import connectx.CXCellState;
import connectx.CXPlayer;
import connectx.CXGameState;

public class Bob3e implements CXPlayer {

    private CXGameState myWin;
    private CXGameState yourWin;
    private int TIMEOUT;
    private long START;
    private long[][][] zobristTable; // Zobrist table for hashing
    private Map<Long, Integer> transpositionTable;
    public Integer[] moveOrder;

    public Bob3e() {
    }

    public void initPlayer(int M, int N, int X, boolean first, int timeout_in_secs) {
        myWin = first ? CXGameState.WINP1 : CXGameState.WINP2;
        yourWin = first ? CXGameState.WINP2 : CXGameState.WINP1;

        TIMEOUT = timeout_in_secs;
        START = System.currentTimeMillis(); // Save starting time

        moveOrder = new Integer[N];
        for (int i = 0; i < N; i++) {
            moveOrder[i] = N/2 + (i % 2 == 0 ? i/2 : -i/2 - 1); // moveOrder[0]=N/2, moveOrder[1]=N/2 -1, moveOrder[2]=N/2 +1, moveOrder[3]=N/2 -2, moveOrder[4]=N/2 +2, ...
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
        int alpha = Integer.MIN_VALUE;      // TODO: check +1 ?
        int beta = Integer.MAX_VALUE;
        Integer[] avlCol = board.getAvailableColumns();
        int depth = avlCol.length;

        if (board.numOfMarkedCells() == 0) {
            board.markColumn(moveOrder[0]); // Start from the middle column
            alphaBeta_m(board, depth, alpha, beta); // Fill the transposition table
            board.unmarkColumn();
            return moveOrder[0];
        } else {
            // if (avlCol.length == 1)
            //     return avlCol[0];
            for (int i = 0; i < board.N; i++) {
                if (!board.fullColumn(moveOrder[i])) { // If the middle column is not full
                    board.markColumn(moveOrder[i]);
                    int eval = alphaBeta_m(board, depth, alpha, beta);
                    board.unmarkColumn();
                    if (eval > alpha) {
                        alpha = eval;
                        bestCol = moveOrder[i];
                    }
                }
            }
        }
        return bestCol;
    }

    // AlphaBeta for maximizing player (M)
    private int alphaBeta_M(CXBoard board, int depth, int alpha, int beta) {
        try {
            checktimeout();

            int transpositionScore = searchTranspositionTable(board);
            if (transpositionScore != Integer.MIN_VALUE) { // If the score is already in the transposition table, return it
                return transpositionScore;
            }
            if (depth == 0 || board.gameState() != CXGameState.OPEN) // If the depth is 0 or the game is over, return the evaluation
                return evaluate(board);
            //Integer[] avlCol = board.getAvailableColumns();
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

    // AlphaBeta for minimizing player (m)
    private int alphaBeta_m(CXBoard board, int depth, int alpha, int beta) {
        try {
            checktimeout();

            int transpositionScore = searchTranspositionTable(board);
            if (transpositionScore != Integer.MIN_VALUE) {
                return transpositionScore;
            }
            if (depth == 0 || board.gameState() != CXGameState.OPEN)
                return evaluate(board);
            //Integer[] avlCol = board.getAvailableColumns();
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

    // Evaluate the board
    private int evaluate(CXBoard board) {
        int score = 0;
        if (board.gameState() == myWin) {
            score = Integer.MAX_VALUE;
        } else if (board.gameState() == yourWin) {
            score = Integer.MIN_VALUE;
        } else if (board.gameState() == CXGameState.DRAW){
            score = 0;
        } else {
            // Rows score
            for (int i = 0; i < board.M; i++) {
                for (int j = 0; j + board.X <= board.N; j++){
                    int countP1 = 0;
                    int countP2 = 0;
                    for (int k = 0; k < board.X; k++) {
                        if (board.cellState(i, j+k) == CXCellState.P1) {
                            countP1++;
                        } else if (board.cellState(i, j+k) == CXCellState.P2) {
                            countP2++;
                        } else if (board.cellState(i, j+k) == CXCellState.FREE) {
                            if (k > 0 && board.cellState(i, j+k-1) == CXCellState.P1) {
                                score += countP1; // Cella libera dopo combo P1 -> bonus per P1
                            } else if (k > 0 && board.cellState(i, j+k-1) == CXCellState.P2) {
                                score += countP2; // Cella libera dopo combo P2 -> score aumentato per favorire mossa che blocca P2
                            }
                        } 
                    }
                    if (countP1 > 0 && countP2 == 0) {
                        score += countP1 * countP1 * countP1;
                    } else if (countP2 > 0 && countP1 == 0) {
                        score -= countP2 * countP2 * countP2;
                    }
                }
            }

            // Columns score
            for (int j = 0; j < board.N; j++) {
                for (int i = 0; i + board.X <= board.M; i++){
                    int countP1 = 0;
                    int countP2 = 0;
                    for (int k = 0; k < board.X; k++) {
                        if (board.cellState(i+k, j) == CXCellState.P1) {
                            countP1++;
                        } else if (board.cellState(i+k, j) == CXCellState.P2) {
                            countP2++;
                        } else if (board.cellState(i+k, j) == CXCellState.FREE) {
                            if (k > 0 && board.cellState(i+k-1, j) == CXCellState.P1) {
                                score += countP1;
                            } else if (k > 0 && board.cellState(i+k-1, j) == CXCellState.P2) {
                                score += countP2;
                            }
                        }
                    }
                    if (countP1 > 0 && countP2 == 0) {
                        score += countP1 * countP1 * countP1;
                    } else if (countP2 > 0 && countP1 == 0) {
                        score -= countP2 * countP2 * countP2;
                    }
                }
            }

            // Diagonals score
            for (int i = 0; i + board.X < board.M; i++) {
                for (int j = 0; j + board.X < board.N; j++) {
                    int countP1 = 0;
                    int countP2 = 0;
                    for (int k = 0; k < board.X; k++) {
                        if (board.cellState(i+k, j+k) == CXCellState.P1) {
                            countP1++;
                        } else if (board.cellState(i+k, j+k) == CXCellState.P2) {
                            countP2++;
                        } else if (board.cellState(i+k, j+k) == CXCellState.FREE) {
                            if (k > 0 && board.cellState(i+k-1, j+k-1) == CXCellState.P1) {
                                score += countP1; 
                            } else if (k > 0 && board.cellState(i+k-1, j+k-1) == CXCellState.P2) {
                                score += countP2;
                            }
                        }
                    }
                    if (countP1 > 0 && countP2 == 0) {
                        score += countP1 * countP1 * countP1;
                    } else if (countP2 > 0 && countP1 == 0) {
                        score -= countP2 * countP2 * countP2;
                    }
                }
            }

            // Anti-diagonals score
            for (int i = 0; i + board.X < board.M; i++) {
                for (int j = board.X - 1; j < board.N; j++) {   // TODO: check for (...)
                    int countP1 = 0;
                    int countP2 = 0;
                    for (int k = 0; k < board.X; k++) {
                        if (board.cellState(i+k, j-k) == CXCellState.P1) {
                            countP1++;
                        } else if (board.cellState(i+k, j-k) == CXCellState.P2) {
                            countP2++;
                        } else if (board.cellState(i+k, j-k) == CXCellState.FREE) {
                            if (k > 0 && board.cellState(i+k-1, j-k+1) == CXCellState.P1) {
                                score += countP1;
                            } else if (k > 0 && board.cellState(i+k-1, j-k+1) == CXCellState.P2) {
                                score += countP2;
                            }
                        }
                    }
                    if (countP1 > 0 && countP2 == 0) {
                        score += countP1 * countP1 * countP1;
                    } else if (countP2 > 0 && countP1 == 0) {
                        score -= countP2 * countP2 * countP2;
                    }
                }
            }
        }
        return score;
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
            return Integer.MIN_VALUE;
        }
    }

    // Save the score of the board in the transposition table
    private void saveTranspositionTable(CXBoard board, int score) {
        long hash = hash(board);
        transpositionTable.put(hash, score);
    }

    public String playerName() {
        return "Bob3e";
    }
}
