import java.util.*;
import java.io.*;


class State 
{
    int player;
    char board[][] = new char[8][8];
    char movelist[][] = new char[48][12];
    int moveptr;
}


public class MyProg
{
    public static final int Clear = 0x1f;
    public static final int Empty= 0x00;
    public static final int Piece= 0x20;
    public static final int King= 0x60;
    public static final int Red= 0x00;
    public static final int White= 0x80;
 
    float SecPerMove;
    char[][] board = new char[8][8];
    char[] bestmove = new char[12];
    int me,cutoff,endgame;
    long NumNodes;
    int MaxDepth;

    /*** For the jump list ***/
    int jumpptr = 0;
    int jumplist[][] = new int[48][12];

    /*** For the move list ***/
    int moveptr = 0;
    int movelist[][] = new int[48][12];

    // For iterative deepening and time management
    long endTime; // Time when we need to stop searching
    boolean timeUp; // Flag to indicate if time is up
    int bestVal; // Value of the best move found so far
    char[] bestMoveAtCurrentDepth = new char[12]; // Best move at current depth

    Random random = new Random();

    public int number(char x) { return ((x)&0x1f); }
    public boolean empty(char x) { return ((((x)>>5)&0x03)==0?1:0) != 0; }
    public boolean piece(char x) { return ((((x)>>5)&0x03)==1?1:0) != 0; }
    public boolean KING(char x) { return ((((x)>>5)&0x03)==3?1:0) != 0; }
    public int color(char x) { return ((((x)>>7)&1)+1); }  

    int calculatePositionalAdvantage(char[][] board) {
        int score = 0;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                char piece = board[y][x];
                if (!empty(piece) && color(piece) == me) {
                    // Encourage forward movement
                    score += (me == 1) ? (7 - y) : y;
    
                    // Additional scoring for kings
                    if (KING(piece)) {
                        score += 5;
                    }
                }
            }
        }
        return score;
    }    

    HashMap<String, Integer> boardHistory = new HashMap<>();

    String boardToString(char[][] board) {
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                if (x % 2 != y % 2) {
                    char piece = board[y][x];
                    if (empty(piece)) {
                        sb.append('.');
                    } else if (color(piece) == 1) { // Player 1
                        if (KING(piece)) sb.append('A');
                        else sb.append('a');
                    } else { // Player 2
                        if (KING(piece)) sb.append('B');
                        else sb.append('b');
                    }
                }
            }
        }
        return sb.toString();
    }

    void updateBoardHistory(char[][] board) {
        String boardStr = boardToString(board);
        int count = boardHistory.getOrDefault(boardStr, 0);
        boardHistory.put(boardStr, count + 1);
    }    

    public void memcpy(char[][] dest, char[][] src) {
        for (int y = 0; y < 8; y++)
            for (int x = 0; x < 8; x++)
                dest[y][x] = src[y][x];
    }

    public void memcpy(char[] dest, char[] src, int num) {
        for (int x = 0; x < num; x++)
            dest[x] = src[x];
    }

    public void memset(char[] arr, int val, int num)
    {
        for(int x=0;x<num;x++) arr[x]=(char)val;
    }

    /* Copy a square state */
    char CopyState(char dest, char src)
    {
        char state;
        
        dest &= Clear;
        state = (char)(src & 0xE0);
        dest |= state;
        return dest;
    }

    /* Reset board to initial configuration */
    void ResetBoard()
    {
        int x,y;
        char pos;

        pos = 0;
        for(y=0; y<8; y++)
        for(x=0; x<8; x++)
        {
            if(x%2 != y%2) {
                board[y][x] = pos;
                if(y<3 || y>4) board[y][x] |= Piece; else board[y][x] |= Empty;
                if(y<3) board[y][x] |= Red; 
                if(y>4) board[y][x] |= White;
                pos++;
            } else board[y][x] = 0;
        }
        endgame = 0;
    }

    /* Add a move to the legal move list */
    void AddMove(char move[])
    {
        int i;

        for(i=0; i<12; i++) movelist[moveptr][i] = move[i];
        moveptr++;
    }

    /* Finds legal non-jump moves for the King at position x,y */
    void FindKingMoves(char board[][], int x, int y) 
    {
        int i,j,x1,y1;
        char move[] = new char[12];

        memset(move,0,12);

        /* Check the four adjacent squares */
        for(j=-1; j<2; j+=2)
        for(i=-1; i<2; i+=2)
        {
            y1 = y+j; x1 = x+i;
            /* Make sure we're not off the edge of the board */
            if(y1<0 || y1>7 || x1<0 || x1>7) continue; 
            if(empty(board[y1][x1])) {  /* The square is empty, so we can move there */
                move[0] = (char)(number(board[y][x])+1);
                move[1] = (char)(number(board[y1][x1])+1);    
                AddMove(move);
            }
        }
    }

    /* Finds legal non-jump moves for the Piece at position x,y */
    void FindMoves(int player, char board[][], int x, int y) 
    {
        int i,j,x1,y1;
        char move[] = new char[12];

        memset(move,0,12);

        /* Check the two adjacent squares in the forward direction */
        if(player == 1) j = 1; else j = -1;
        for(i=-1; i<2; i+=2)
        {
            y1 = y+j; x1 = x+i;
            /* Make sure we're not off the edge of the board */
            if(y1<0 || y1>7 || x1<0 || x1>7) continue; 
            if(empty(board[y1][x1])) {  /* The square is empty, so we can move there */
                move[0] = (char)(number(board[y][x])+1);
                move[1] = (char)(number(board[y1][x1])+1);    
                AddMove(move);
            }
        }
    }

    /* Adds a jump sequence the the legal jump list */
    void AddJump(char move[])
    {
        int i;
        
        for(i=0; i<12; i++) jumplist[jumpptr][i] = move[i];
        jumpptr++;
    }

    /* Finds legal jump sequences for the King at position x,y */
    int FindKingJump(int player, char board[][], char move[], int len, int x, int y) 
    {
        int i,j,x1,y1,x2,y2,FoundJump = 0;
        char one,two;
        char mymove[] = new char[12];
        char myboard[][] = new char[8][8];

        memcpy(mymove,move,12);

        /* Check the four adjacent squares */
        for(j=-1; j<2; j+=2)
        for(i=-1; i<2; i+=2)
        {
            y1 = y+j; x1 = x+i;
            y2 = y+2*j; x2 = x+2*i;
            /* Make sure we're not off the edge of the board */
            if(y2<0 || y2>7 || x2<0 || x2>7) continue; 
            one = board[y1][x1];
            two = board[y2][x2];
            /* If there's an enemy piece adjacent, and an empty square after hum, we can jump */
            if(!empty(one) && color(one) != player && empty(two)) {
                /* Update the state of the board, and recurse */
                memcpy(myboard,board);
                myboard[y][x] &= Clear;
                myboard[y1][x1] &= Clear;
                mymove[len] = (char)(number(board[y2][x2])+1);
                FoundJump = FindKingJump(player,myboard,mymove,len+1,x+2*i,y+2*j);
                if(FoundJump==0) {
                    FoundJump = 1;
                    AddJump(mymove);
                }
            }
        }
        return FoundJump;
    }

    /* Finds legal jump sequences for the Piece at position x,y */
    int FindJump(int player, char board[][], char move[], int len, int x, int y) 
    {
        int i,j,x1,y1,x2,y2,FoundJump = 0;
        char one,two;
        char mymove[] = new char[12];
        char myboard[][] = new char[8][8];

        memcpy(mymove,move,12);

        /* Check the two adjacent squares in the forward direction */
        if(player == 1) j = 1; else j = -1;
        for(i=-1; i<2; i+=2)
        {
            y1 = y+j; x1 = x+i;
            y2 = y+2*j; x2 = x+2*i;
            /* Make sure we're not off the edge of the board */
            if(y2<0 || y2>7 || x2<0 || x2>7) continue; 
            one = board[y1][x1];
            two = board[y2][x2];
            /* If there's an enemy piece adjacent, and an empty square after him, we can jump */
            if(!empty(one) && color(one) != player && empty(two)) {
                /* Update the state of the board, and recurse */
                memcpy(myboard,board);
                myboard[y][x] &= Clear;
                myboard[y1][x1] &= Clear;
                mymove[len] = (char)(number(board[y2][x2])+1);
                FoundJump = FindJump(player,myboard,mymove,len+1,x+2*i,y+2*j);
                if(FoundJump==0) {
                    FoundJump = 1;
                    AddJump(mymove);
                }
            }
        }
        return FoundJump;
    }

    /* Determines all of the legal moves possible for a given state */
    int FindLegalMoves(State state)
    {
        int x,y;
        char move[] = new char[12], board[][] = new char[8][8];

        memset(move,0,12);
        jumpptr = moveptr = 0;
        memcpy(board,state.board);

        /* Loop through the board array, determining legal moves/jumps for each piece */
        for(y=0; y<8; y++)
        for(x=0; x<8; x++)
        {
            if(x%2 != y%2 && color(board[y][x]) == state.player && !empty(board[y][x])) {
                if(KING(board[y][x])) { /* King */
                    move[0] = (char)(number(board[y][x])+1);
                    FindKingJump(state.player,board,move,1,x,y);
                    if(jumpptr==0) FindKingMoves(board,x,y);
                } 
                else if(piece(board[y][x])) { /* Piece */
                    move[0] = (char)(number(board[y][x])+1);
                    FindJump(state.player,board,move,1,x,y);
                    if(jumpptr==0) FindMoves(state.player,board,x,y);    
                }
            }    
        }
        if(jumpptr!=0) {
            for(x=0; x<jumpptr; x++) 
            for(y=0; y<12; y++) 
            state.movelist[x][y] = (char)(jumplist[x][y]);
            state.moveptr = jumpptr;
        } 
        else {
            for(x=0; x<moveptr; x++) 
            for(y=0; y<12; y++) 
            state.movelist[x][y] = (char)(movelist[x][y]);
            state.moveptr = moveptr;
        }
        return (jumpptr+moveptr);
    }

    /* Converts a square label to it's x,y position */
    void NumberToXY(char num, int[] xy)
    {
        int i=0,newy,newx;

        for(newy=0; newy<8; newy++)
        for(newx=0; newx<8; newx++)
        {
            if(newx%2 != newy%2) {
                i++;
                if(i==(int) num) {
                    xy[0] = newx;
                    xy[1] = newy;
                    return;
                }
            }
        }
        xy[0] = 0; 
        xy[1] = 0;
    }

    /* Returns the length of a move */
    int MoveLength(char move[])
    {
        int i;

        i = 0;
        while(i<12 && move[i]!=0) i++;
        return i;
    }    

    /* Converts the text version of a move to its integer array version */
    int TextToMove(String mtext, char[] move) {
        int len = 0;
        String[] tokens = mtext.split("-");
        for (String token : tokens) {
            int val = Integer.parseInt(token);
            if (val <= 0 || val > 32)
                return 0;
            move[len] = (char) val;
            len++;
        }
        if (len < 2 || len > 12)
            return 0;
        else
            return len;
    }

    /* Converts the integer array version of a move to its text version */
    String MoveToText(char move[])
    {
        int i;
        char temp[] = new char[8];

        String mtext = "";
        if(move[0]!=0) 
        {
           mtext += ((int)(move[0]));
           for(i=1; i<12; i++) {
               if(move[i]!=0) {
                   mtext += "-";
                   mtext += ((int)(move[i]));
               }
           }
        }
        return mtext;
    }

    /* Performs a move on the board, updating the state of the board */
    void PerformMove(char board[][], char move[], int mlen) {
        int x, y, x1, y1, captured_x, captured_y;
        int xy[] = new int[2];
        int xy1[] = new int[2];
    
        // Starting position
        NumberToXY(move[0], xy);
        x = xy[0];
        y = xy[1];
    
        // For each step in the move
        for (int i = 1; i < mlen; i++) {
            NumberToXY(move[i], xy1);
            x1 = xy1[0];
            y1 = xy1[1];
    
            // If it's a jump (difference of 2 in x and y)
            if (Math.abs(x1 - x) == 2 && Math.abs(y1 - y) == 2) {
                captured_x = (x + x1) / 2;
                captured_y = (y + y1) / 2;
                board[captured_y][captured_x] &= Clear;
            }
    
            // Update x and y for next iteration
            x = x1;
            y = y1;
        }
    
        // Move the piece to the final position
        NumberToXY(move[0], xy);
        int start_x = xy[0];
        int start_y = xy[1];
        NumberToXY(move[mlen - 1], xy1);
        int end_x = xy1[0];
        int end_y = xy1[1];
    
        board[end_y][end_x] = CopyState(board[end_y][end_x], board[start_y][start_x]);
        // King me if reaching the last row
        if (end_y == 0 || end_y == 7)
            board[end_y][end_x] |= King;
        board[start_y][start_x] &= Clear;
    }
    

    // Evaluation function for the board state
    int Eval(State state) {
        int myPieces = 0, myKings = 0, oppPieces = 0, oppKings = 0;
        int myPlayer = me;
        int oppPlayer = 3 - me;

        for (int y = 0; y < 8; y++)
            for (int x = 0; x < 8; x++) {
                if (x % 2 != y % 2) {
                    char piece = state.board[y][x];
                    if (!empty(piece)) {
                        int pieceColor = color(piece);
                        if (pieceColor == myPlayer) {
                            if (KING(piece))
                                myKings++;
                            else if (piece(piece))
                                myPieces++;
                        } else if (pieceColor == oppPlayer) {
                            if (KING(piece))
                                oppKings++;
                            else if (piece(piece))
                                oppPieces++;
                        }
                    }
                }
            }

        int material = (myPieces + 2 * myKings) - (oppPieces + 2 * oppKings);
        int positionalAdvantage = calculatePositionalAdvantage(state.board);
    
        // Apply stronger penalty for repeated positions
        String boardStr = boardToString(state.board);
        int count = boardHistory.getOrDefault(boardStr, 0);
        if (count >= 2) {
            material -= 100; // Increased penalty
        }
    
        return material + positionalAdvantage;
    }

    // Time check function
    boolean timeIsUp() {
        return System.currentTimeMillis() >= endTime;
    }

    void FindBestMove(int player) {
        State state = new State();
        state.player = player;
        memcpy(state.board, board);
        memset(bestmove, 0, 12);
        bestVal = Integer.MIN_VALUE;
        timeUp = false;
        endTime = System.currentTimeMillis() + (long) (SecPerMove * 1000);
        int depth = 1;

        // Iterative deepening loop
        while (!timeUp) {
            bestMoveAtCurrentDepth = new char[12]; // Reset best move at this depth
            int val = MaxVal(state, depth, Integer.MIN_VALUE, Integer.MAX_VALUE, true);
            if (!timeUp) {
                // Update best move and value
                memcpy(bestmove, bestMoveAtCurrentDepth, 12);
                bestVal = val;
                System.err.println("Depth: " + depth + ", BestVal: " + bestVal);
                depth++;
                if (MaxDepth != -1 && depth > MaxDepth)
                    break;
            }
        }
    }

    // MaxVal function for minimax with alpha-beta pruning
    int MaxVal(State state, int depth, int alpha, int beta, boolean isRoot) {
        if (timeIsUp()) {
            timeUp = true;
            return Eval(state);
        }
        if (depth == 0) {
            return Eval(state);
        }
        int v = Integer.MIN_VALUE;
        int numMoves = FindLegalMoves(state);
        if (numMoves == 0) {
            return -9999; // Loss
        }
        for (int i = 0; i < state.moveptr; i++) {
            char[] move = new char[12];
            memcpy(move, state.movelist[i], 12);
    
            State childState = new State();
            childState.player = 3 - state.player;
            memcpy(childState.board, state.board);
            int mlen = MoveLength(move);
            PerformMove(childState.board, move, mlen);
    
            // Update board history
            String boardStr = boardToString(childState.board);
            int count = boardHistory.getOrDefault(boardStr, 0);
            boardHistory.put(boardStr, count + 1);
    
            // Check for repetition
            if (count + 1 >= 3) { // If board repeats three times
                boardHistory.put(boardStr, count); // Revert count
                continue; // Skip this move
            }
    
            int val = MinVal(childState, depth - 1, alpha, beta);
    
            // Restore board history
            if (count + 1 == 1) {
                boardHistory.remove(boardStr);
            } else {
                boardHistory.put(boardStr, count);
            }
    
            if (timeUp)
                return v;
    
            if (val > v) {
                v = val;
                if (isRoot) {
                    memcpy(bestMoveAtCurrentDepth, move, 12);
                }
            }
            if (v >= beta)
                return v;
            if (v > alpha)
                alpha = v;
        }
        return v;
    }
    
    

    // MinVal function for minimax with alpha-beta pruning
    int MinVal(State state, int depth, int alpha, int beta) {
        if (timeIsUp()) {
            timeUp = true;
            return Eval(state);
        }
        if (depth == 0) {
            return Eval(state);
        }
        int v = Integer.MAX_VALUE;
        int numMoves = FindLegalMoves(state);
        if (numMoves == 0) {
            return 9999; // Win
        }
        for (int i = 0; i < state.moveptr; i++) {
            char[] move = new char[12];
            memcpy(move, state.movelist[i], 12);
    
            State childState = new State();
            childState.player = 3 - state.player;
            memcpy(childState.board, state.board);
            int mlen = MoveLength(move);
            PerformMove(childState.board, move, mlen);
    
            // Update board history
            String boardStr = boardToString(childState.board);
            int count = boardHistory.getOrDefault(boardStr, 0);
            boardHistory.put(boardStr, count + 1);
    
            // Check for repetition
            if (count + 1 >= 3) { // If board repeats three times
                boardHistory.put(boardStr, count); // Revert count
                continue; // Skip this move
            }
            int val = MaxVal(childState, depth - 1, alpha, beta, false);

            // Restore board history
            if (count + 1 == 1) {
                boardHistory.remove(boardStr);
            } else {
                boardHistory.put(boardStr, count);
            }
            if (timeUp) {
                return v;
            }
            if (val < v) {
                v = val;
            }
            if (v <= alpha){
                return v;
            }
            if (v < beta) {
                beta = v;
            }
        }
        return v;
    }
    
    

    // Debugging         
    void printBoard(char[][] board) {
        for (int y = 7; y >= 0; y--) {
            for (int x = 0; x < 8; x++) {
                if (x % 2 != y % 2) {
                    char piece = board[y][x];
                    if (!empty(piece)) {
                        if (color(piece) == me) {
                            if (KING(piece))
                                System.err.print('A');
                            else
                                System.err.print('a');
                        } else {
                            if (KING(piece))
                                System.err.print('B');
                            else
                                System.err.print('b');
                        }
                    } else {
                        System.err.print('.');
                    }
                } else {
                    System.err.print(' ');
                }
            }
            System.err.println();
        }
        System.err.println();
    }
    

    public static void main(String argv[]) throws Exception
    {
        System.err.println("AAAAA");
        if(argv.length>=2) System.err.println("Argument:" + argv[1]);
        MyProg stupid = new MyProg();
        stupid.play(argv);
    }

    String myRead(BufferedReader br, int y)
    {
        String rval = "";
        char line[] = new char[1000];
        int x,len=0;
System.err.println("Java waiting for input");
        try
        {
           //while(!br.ready()) ;
           len = br.read(line, 0, y);
        }
        catch(Exception e) { System.err.println("Java wio exception"); }
        for(x=0;x<len;x++) rval += line[x];
System.err.println("Java read " + len + " chars: " + rval);
        return rval;
    }


    String myRead(BufferedReader br)
    {
        String rval = "";
        char line[] = new char[1000];
        int x,len=0;
System.err.println("Java waiting for input");
        try
        {
           //while(!br.ready()) ;
           len = br.read(line, 0, 1000);
        }
        catch(Exception e) { System.err.println("Java wio exception"); }
        for(x=0;x<len;x++) rval += line[x];
System.err.println("Java wRead " + rval);
        return rval;
    }

    public void play(String argv[]) throws Exception {
        char move[] = new char[12];
        int mlen, player1;
        String buf;
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        // Convert command line parameters
        SecPerMove = Float.parseFloat(argv[0]);
        MaxDepth = (argv.length == 2) ? Integer.parseInt(argv[1]) : -1;

        // Determine if we are Player1 or Player2
        buf = myRead(br, 7);
        if (buf.startsWith("Player1")) {
            System.err.println("Java is player 1");
            player1 = 1;
        } else {
            System.err.println("Java is player 2");
            player1 = 0;
        }
        me = player1 != 0 ? 1 : 2;

        // Set up the board
        ResetBoard();

        if (player1 != 0) {
            // If we are Player1, make the first move
            FindBestMove(me);
            if (bestmove[0] != 0) {
                mlen = MoveLength(bestmove);
                PerformMove(board, bestmove, mlen);
                printBoard(board);
                updateBoardHistory(board);
                buf = MoveToText(bestmove);
            } else
                System.exit(1); // No legal moves, we lose

            // Write the move to the pipe
            System.out.println(buf);
        }

        // Main game loop
        while (true) {
            // check for draw conditions
            String currentBoardStr = boardToString(board);
            if (boardHistory.getOrDefault(currentBoardStr, 0) >= 3) {
                System.err.println("Game is a draw due to threefold repetition.");
                System.exit(0);
            }

            // Read the opponent's move
            buf = myRead(br).trim();
            memset(move, 0, 12);

            // Update the board with the opponent's move
            mlen = TextToMove(buf, move);
            PerformMove(board, move, mlen);
            printBoard(board);
            updateBoardHistory(board);

            // Find our best move and update the board
            FindBestMove(me);
            if (bestmove[0] != 0) {
                mlen = MoveLength(bestmove);
                PerformMove(board, bestmove, mlen);
                printBoard(board);
                updateBoardHistory(board);
                buf = MoveToText(bestmove);
            } else
                System.exit(1); // No legal moves, we lose

            // Write the move to the pipe
            System.out.println(buf);
        }
    }
}

