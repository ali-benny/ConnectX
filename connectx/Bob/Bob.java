package connectx.Bob;

import connectx.CXBoard;
import connectx.CXPlayer;
import connectx.CXGameState;

import java.util.Random;

public class Bob implements CXPlayer{
  private CXGameState myWin;
	private CXGameState yourWin;
	private int  TIMEOUT;
	private long START;

  private long[][][] ZOBRIST_TABLE;


  //default empty constructor
  public Bob(){
  }


  //initPlayer
  public void initPlayer(int M, int N, int X, boolean first, int timeout_in_secs){
    myWin   = first ? CXGameState.WINP1 : CXGameState.WINP2; 
		yourWin = first ? CXGameState.WINP2 : CXGameState.WINP1;

    TIMEOUT = timeout_in_secs;
    START = System.currentTimeMillis(); // Save starting time

    Random rand = new Random(); 
    ZOBRIST_TABLE = new long[M][N][2]; //generate zobrist table
    for(int i = 0; i < M; i++){ 
      for(int j = 0; j < N; j++){
        ZOBRIST_TABLE[i][j][0] = rand.nextLong();
        ZOBRIST_TABLE[i][j][1] = rand.nextLong();
      }
    }
  }
  

  //selectColumn
  public int selectColumn(CXBoard B){
    START = System.currentTimeMillis(); // Save starting time

    return 0;
  }


  //playerName
  public String playerName(){
    return "Bob";
  }
}
