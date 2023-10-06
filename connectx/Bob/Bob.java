package connectx.Bob;

import java.util.Random;
import java.util.concurrent.TimeoutException;

import connectx.CXBoard;
import connectx.CXCellState;
import connectx.CXPlayer;
import connectx.CXGameState;

// import java.util.Random;

public class Bob implements CXPlayer{
  // private long[][][] ZOBRIST_TABLE;

  // class Board extends CXBoard{
  //   public Board(int M, int N, int X){
  //     super(M, N, X);
  //   }

  //   public long hash = 0;
  //   public long getHash(){  // TODO da riguardare poi
  //     for(int i = 0; i < M; i++){
  //       for(int j = 0; j < N; j++){
  //         if(B[i][j] != CXCellState.FREE){
  //           hash ^= ZOBRIST_TABLE[i][j][B[i][j].ordinal() - 1];
  //         }
  //       }
  //     }
  //     return hash;
  //   }  
  // }
  
  private CXGameState myWin;
	private CXGameState yourWin;
	private int  TIMEOUT;
	private long START;

  
  //default empty constructor
  public Bob(){
  }


  //initPlayer
  public void initPlayer(int M, int N, int X, boolean first, int timeout_in_secs){
    myWin   = first ? CXGameState.WINP1 : CXGameState.WINP2; 
		yourWin = first ? CXGameState.WINP2 : CXGameState.WINP1;

    TIMEOUT = timeout_in_secs;
    START = System.currentTimeMillis(); // Save starting time

    // Random rand = new Random(); 
    // ZOBRIST_TABLE = new long[M][N][2]; //generate zobrist table
    // for(int i = 0; i < M; i++){ 
    //   for(int j = 0; j < N; j++){
    //     ZOBRIST_TABLE[i][j][0] = rand.nextLong();
    //     ZOBRIST_TABLE[i][j][1] = rand.nextLong();
    //   }
  }
  
  /**
   * Check if the time is over
   */
  private void checktimeout() throws TimeoutException{
    if(System.currentTimeMillis() - START > TIMEOUT * 1000){
      throw new TimeoutException();
    }
  }

  //selectColumn
  public int selectColumn(CXBoard board){
    START = System.currentTimeMillis(); // Save starting time
    if(board.numOfMarkedCells() == 0) return board.N/2; // first move in the middle
    int bestCol = -1;
    int alpha = Integer.MIN_VALUE;
    int beta = Integer.MAX_VALUE;
    Integer[] avlCol = board.getAvailableColumns();

    if (avlCol.length == 1) return avlCol[0]; // only one move available
    
    int depth = avlCol.length;
    for (int col=0; col < avlCol.length; col++) {
      board.markColumn(avlCol[col]);
      int eval = alphaBeta_m(board, depth - 1, alpha, beta);
      board.unmarkColumn();
      
      if (eval > alpha) {
        alpha = eval;
        bestCol = avlCol[col];
      }
    }

    return bestCol;
  }

  /**
   * AlphaBeta algorithm for maximizing player
   * 
   * @param board 
   * @param depth
   * @param alpha
   * @param beta  
   */
  private int alphaBeta_M(CXBoard board, int depth, int alpha, int beta) {
    try {
      checktimeout();

      if (depth == 0 || board.gameState() != CXGameState.OPEN) return evaluate(board);
      Integer[] avlCol = board.getAvailableColumns();
      for (int col=0; col < avlCol.length; col++) {
        // col è sicuramente available
        board.markColumn(avlCol[col]);
        int eval = alphaBeta_m(board, depth - 1, alpha, beta);
        board.unmarkColumn();
        
        if (eval >= beta)   
          return beta;  // cutoff
        if (eval > alpha)
          alpha = eval;
      }
      return alpha;
    }
    catch (TimeoutException e) {// Handle the timeout exception here
      return evaluate(board);
    }
  }

  /**
   * AlphaBeta algorithm for minimizing player
   * 
   * @param board 
   * @param depth
   * @param alpha
   * @param beta  
   */
  private int alphaBeta_m (CXBoard board, int depth, int alpha, int beta) {
    try {
      checktimeout();

      if (depth == 0 || board.gameState() != CXGameState.OPEN) return evaluate(board);
      Integer[] avlCol = board.getAvailableColumns();
      for (int col=0; col < avlCol.length; col++) {
        // col è sicuramente available
        board.markColumn(avlCol[col]);
        int eval = alphaBeta_M(board, depth - 1, alpha, beta);
        board.unmarkColumn();
        
        if (eval <= alpha)   
          return alpha;  // cutoff
        if (eval < beta)
          beta = eval;
      }
      return beta;
    }
    catch (TimeoutException e) {// Handle the timeout exception here
      return evaluate(board);
    }
  }

  private int evaluate(CXBoard board) {
    if (board.gameState() == myWin) {
      return Integer.MAX_VALUE;
    } else if (board.gameState() == yourWin) {
      return Integer.MIN_VALUE;   // - \inf
    } else {
      return 0;
    }
  }

  //playerName
  public String playerName(){
    return "Bob";
  }
}
