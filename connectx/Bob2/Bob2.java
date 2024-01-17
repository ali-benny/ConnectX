package connectx.Bob2;

import java.util.concurrent.TimeoutException;
import java.util.HashMap;
import java.util.Map;

import connectx.CXBoard;
import connectx.CXCellState;
import connectx.CXPlayer;
import connectx.CXGameState;

public class Bob2 implements CXPlayer {

    private CXGameState myWin;
    private CXGameState yourWin;
    private int TIMEOUT;
    private long START;
    private Map<Long, Integer> transpositionTable;

    public Bob2() {
        transpositionTable = new HashMap<>();
    }

    public void initPlayer(int M, int N, int X, boolean first, int timeout_in_secs) {
        myWin = first ? CXGameState.WINP1 : CXGameState.WINP2;
        yourWin = first ? CXGameState.WINP2 : CXGameState.WINP1;

        TIMEOUT = timeout_in_secs;
        START = System.currentTimeMillis();
        transpositionTable.clear();
    }

    private void checktimeout() throws TimeoutException {
        if (System.currentTimeMillis() - START > TIMEOUT * 1000) {
            throw new TimeoutException();
        }
    }

    public int selectColumn(CXBoard board) {
        START = System.currentTimeMillis();
        if (board.numOfMarkedCells() == 0)
            return board.N / 2;
        int bestCol = -1;
        int alpha = Integer.MIN_VALUE;
        int beta = Integer.MAX_VALUE;
        Integer[] avlCol = board.getAvailableColumns();

        if (avlCol.length == 1)
            return avlCol[0];

        int depth = avlCol.length;
        for (int col = 0; col < avlCol.length; col++) {
            board.markColumn(avlCol[col]);
            int eval = alphaBeta(board, depth - 1, alpha, beta);
            board.unmarkColumn();

            if (eval > alpha) {
                alpha = eval;
                bestCol = avlCol[col];
            }
        }

        return bestCol;
    }

    private int alphaBeta(CXBoard board, int depth, int alpha, int beta) {
        try {
            checktimeout();

            long hash = board.hashCode(); // Use the hash code of the board as a unique identifier
            if (transpositionTable.containsKey(hash) && depth > 0) {
                return transpositionTable.get(hash); // Return stored value if available
            }

            if (depth == 0 || board.gameState() != CXGameState.OPEN)
                return evaluate(board);

            Integer[] avlCol = board.getAvailableColumns();
            for (int col : avlCol) {
                board.markColumn(col);
                int eval = -alphaBeta(board, depth - 1, -beta, -alpha);
                board.unmarkColumn();

                if (eval >= beta) {
                    transpositionTable.put(hash, beta); // Store the result in the transposition table
                    return beta;
                }

                if (eval > alpha) {
                    alpha = eval;
                }
            }

            transpositionTable.put(hash, alpha); // Store the result in the transposition table
            return alpha;
        } catch (TimeoutException e) {
            return evaluate(board);
        }
    }

    private int evaluate(CXBoard board) {
        if (board.gameState() == myWin) {
            return Integer.MAX_VALUE;
        } else if (board.gameState() == yourWin) {
            return Integer.MIN_VALUE;
        } else {
            return 0;
        }
    }

    public String playerName() {
        return "Bob2";
    }
}
