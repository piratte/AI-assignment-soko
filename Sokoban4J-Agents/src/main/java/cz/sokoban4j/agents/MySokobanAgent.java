package cz.sokoban4j.agents;

import java.util.*;

import cz.sokoban4j.Sokoban;
import cz.sokoban4j.simulation.SokobanResult;
import cz.sokoban4j.simulation.actions.EDirection;
import cz.sokoban4j.simulation.actions.compact.CAction;
import cz.sokoban4j.simulation.actions.compact.CMove;
import cz.sokoban4j.simulation.actions.compact.CPush;
import cz.sokoban4j.simulation.actions.oop.EActionType;
import cz.sokoban4j.simulation.board.compact.BoardCompact;
import cz.sokoban4j.simulation.board.compact.CTile;

public class MySokobanAgent extends ArtificialAgent {

	private List<EDirection> result;
	
	private BoardCompact board;

	private int searchedNodes;

	//private List<String> visitedList;
    //private Set<String> visitedBoardsHashes;
    private Map<String, Integer> visitedStatesWithLevel;

	private Set<Tuple<Integer,Integer>> deadends;

	private ArrayList<Tuple<Integer, Integer>> boxes;

	private int[][] heuristicValue;

	private int depthLimit;

	private static int runs;
	
	@Override
	protected List<EDirection> think(BoardCompact board) {
		if (runs != 0) {
		    System.err.println("Another search attempt!");
		    System.exit(1);
        }
		
		// INIT SEARCH
		this.board = board;
		this.result = new ArrayList<EDirection>();
		boolean solutionFound = false;

		// create a dead-end database
		this.deadends = createDeadendDB();

		// pre-compute heuristic
		//this.heuristicValue = computeHeuristic();
		//System.exit(0);

		this.boxes = getAllBoxesPositions();


        this.visitedStatesWithLevel = new HashMap<String, Integer>();
		//this.visitedBoardsHashes = new HashSet<String>();
		//this.visitedList = new ArrayList<String>(200);
		
		// DEBUG
		System.out.println();
		System.out.println("===== BOARD =====");
		this.board.debugPrint();
		System.out.println("=================");
		
		// FIRE THE SEARCH

		searchedNodes = 0;

		long searchStartMillis = System.currentTimeMillis();

		depthLimit = 70;
		dfs(0, 70,EActionType.INVALID, EDirection.NONE); // the number marks how deep we will search (the longest plan we will consider)
		
		runs++;
		
		long searchTime = System.currentTimeMillis() - searchStartMillis;
		
		printResults(searchTime);
				
		return result;
	}

	private ArrayList<Tuple<Integer, Integer>> getAllBoxesPositions() {
		ArrayList<Tuple<Integer,Integer>> boxes = new ArrayList<Tuple<Integer, Integer>>(10);

		for (int xInd = 0; xInd < board.width(); xInd++) {
			for (int yInd = 0; yInd < board.height(); yInd++){
				if (CTile.isSomeBox(board.tile(xInd,yInd))) {
					boxes.add(new Tuple<Integer, Integer>(xInd,yInd));
				}
			}
		}

		return boxes;
	}

	// FIXME: !!!
	private int[][] computeHeuristic() {
		boolean changed = true;
		board.debugPrint();
		// init search board
		int[][] result = new int[board.width()][board.height()];
		for (int xInd = 0; xInd < board.width(); xInd++) {
			for (int yInd = 0; yInd < board.height(); yInd++){
				result[xInd][yInd] = -1;
			}
		}

		while (changed) {
			changed = false;
			for (int widthInd = 1; widthInd < board.width()-1; widthInd++) {
				for (int heightInd = 1; heightInd < board.height()-1; heightInd++) {
					// assign heuristic value for targets
					if (CTile.forSomeBox(board.tile(widthInd,heightInd))) {
						result[widthInd][heightInd] = 0;
					}

					// board tile is not a wall and the heuristic was not yet computed
					if ((!CTile.isWall(board.tile(widthInd,heightInd))) && result[widthInd][heightInd] != -1) {
						changed = updateHeuristic(result, widthInd, heightInd);
					}

				}
			}

			for (int xInd = board.width()-1; xInd >=0; xInd--) {
				for (int yInd = 0; yInd < board.height(); yInd++){
					if (CTile.isWall(board.tile(xInd,yInd))) System.out.print("##");
					else System.out.print(String.format("%2d",result[xInd][yInd]));
				}
				System.out.println();
			}
			System.out.println("==================");
		}

		return result;
	}

	private boolean updateHeuristic(int[][] input, int x, int y) {
		boolean madeChange = false;
		int curBest = input[x][y] + 1;
		for (int d : new int[]{-1,1}) {
			int cur = input[x][y + d];
			if (cur > curBest || cur == -1) {
				input[x][y + d] = curBest;
				madeChange = true;
			}

			cur = input[x + d][y];
			if (cur > curBest || cur == -1) {
				input[x + d][y] = curBest;
				madeChange = true;
			}
		}

		return madeChange;
	}

	private boolean dfs(int level, int heuristicVal, EActionType previousType, EDirection previousDirection) {
		//board.debugPrint();
		int curHeurVal = heuristicVal;

		if (level > depthLimit) return false; // DEPTH-LIMITED

        if (previousType == EActionType.PUSH) {

        	// check for box in a deadend
			if (deadends.contains(getNewBoxPossition(previousDirection))) {
				System.out.println("Deadend detected:");
				board.debugPrint();
				return false;
			}

			// update heuristic
			boxes.remove(new Tuple<Integer, Integer>(board.playerX, board.playerY));
			int newBoxX = board.playerX + previousDirection.dX;
			int newBoxY = board.playerY + previousDirection.dY;
			boxes.add(new Tuple<Integer, Integer>(newBoxX, newBoxY));
			curHeurVal -= heuristicValue[board.playerX][board.playerY];
			curHeurVal += heuristicValue[newBoxX][newBoxY];
        }


		String boardCode = encodeBoard(board);
        /* level is bigger -> more available steps */
        if (visitedStatesWithLevel.containsKey(boardCode) && visitedStatesWithLevel.get(boardCode) <= level) { //(visitedBoardsHashes.contains(boardCode)) {
            return false;
		}
		visitedStatesWithLevel.put(boardCode,level);
		++searchedNodes;//Check for already visited state

		// COLLECT POSSIBLE ACTIONS

		List<CAction> actions = new ArrayList<CAction>(4);

		for (CPush push : CPush.getActions()) {
			if (push.isPossible(board)) {
				actions.add(push);
			}
		}

		for (CMove move : CMove.getActions()) {
			if (previousType == EActionType.MOVE && move.getDirection() == previousDirection.opposite()) {
				// DO NOT CONSIDER THE ACTION THE MOVES BACK
				continue;
			}

			if (move.isPossible(board)) {
				actions.add(move);
			}
		}

		// TRY ACTIONS
		for (CAction action : actions) {
			//System.err.println("Trying " + action);

			// PERFORM THE ACTION
			result.add(action.getDirection());
			action.perform(board);

			//System.err.println("Adding:");
			//board.debugPrint();

			// CHECK VICTORY
			if (board.isVictory()) {
				// SOLUTION FOUND!
				return true;
			}

			// CONTINUE THE SEARCH
			// TODO: continue from the least expensive state
			if (dfs(level+1, curHeurVal, action.getType(), action.getDirection())) {
				// SOLUTION FOUND!
				return true;
			}


			reverseAction(action);
		}
		return false;
	}

    private Tuple<Integer, Integer> getNewBoxPossition(EDirection previousDirection) {
        return new Tuple<Integer, Integer>(board.playerX + previousDirection.dX,
                board.playerY + previousDirection.dY);
    }


    private Set<Tuple<Integer, Integer>> createDeadendDB() {
		Set<Tuple<Integer, Integer>> result = new HashSet<Tuple<Integer, Integer>>();
        for (int widthInd = 1; widthInd < board.width()-1; widthInd ++) {
            for (int heightInd = 1; heightInd < board.height()-1; heightInd++) {
                if (isDeadendCorner(widthInd,heightInd)) {
                    result.add(new Tuple<Integer, Integer>(widthInd,heightInd));
                }
            }
        }

        //TODO: detect deadend straights

		return result;
	}

    private boolean isDeadendCorner(int widthInd, int heightInd) {
        if (CTile.forSomeBox(board.tile(widthInd,heightInd))) return false;

	    int horizScore = 0;
	    int verticScore = 0;
        if (CTile.isWall(board.tile(widthInd-1,heightInd))) horizScore++;
        if (CTile.isWall(board.tile(widthInd+1,heightInd))) horizScore++;
        if (CTile.isWall(board.tile(widthInd,heightInd-1))) verticScore++;
        if (CTile.isWall(board.tile(widthInd,heightInd+1))) verticScore++;
	    if (horizScore == 0 || verticScore == 0) return false;

	    return horizScore + verticScore > 1;
    }

    private void reverseAction(CAction action) {
		result.remove(result.size()-1);
		action.reverse(board);
	}

	private String encodeBoard(BoardCompact board) {
		StringBuilder sb = new StringBuilder();
		sb.append(String.format("%d,%d,", board.playerX, board.playerY));

		for (int widthInd = 0; widthInd < board.width(); widthInd ++) {
			for (int heightInd = 0; heightInd < board.height(); heightInd++) {
				if (CTile.isSomeBox(board.tile(widthInd, heightInd))) {
					sb.append(String.format("%d,%d,%d,",
							CTile.getBoxNum(board.tile(widthInd, heightInd)), widthInd, heightInd));
				}
				// sb.append(board.tile(widthInd, heightInd));
			}
		}
		return sb.toString();
	}

	private void printResults(long searchTime) {
		System.out.println("SEARCH TOOK:   " + searchTime + " ms");
		//System.out.println("NODES IN SET:  " + visitedBoardsHashes.size());
		System.out.println("NODES VISITED: " + searchedNodes);
		System.out.println("PERFORMANCE:   " + ((double)searchedNodes / (double)searchTime * 1000) + " nodes/sec");
		System.out.println("SOLUTION:      " + (result.size() == 0 ? "NOT FOUND" : "FOUND in " + result.size() + " steps"));
		if (result.size() > 0) {
			System.out.print("STEPS:         ");
			for (EDirection winDirection : result) {
				System.out.print(winDirection + " -> ");
			}
			System.out.println("BOARD SOLVED!");
		}
		System.out.println("=================");
	}

	public static void main(String[] args) {
		SokobanResult result;
		runs = 0;
		
		// VISUALIZED GAME
		
		// WE CAN SOLVE FOLLOWING 4 LEVELS WITH THIS IMPLEMENTATION
		//result = Sokoban.playAgentLevel("Sokoban4J/levels/Easy/level0001.s4jl", new MySokobanAgent());   //  5 steps required
		//result = Sokoban.playAgentLevel("Sokoban4J/levels/Easy/level0002.1.s4jl", new MySokobanAgent()); // 13 steps required
		//result = Sokoban.playAgentLevel("Sokoban4J/levels/Easy/level0002.2.s4jl", new MySokobanAgent()); // 25 steps required
		//result = Sokoban.playAgentLevel("Sokoban4J/levels/Easy/level0002.3.s4jl", new MySokobanAgent()); // 37 steps required
		
		// THIS LEVEL IS BIT TOO MUCH
		result = Sokoban.playAgentLevel("Sokoban4J/levels/Easy/level0004.s4jl", new MySokobanAgent()); // 66 steps required
		
		// HEADLESS == SIMULATED-ONLY GAME
		//result = Sokoban.simAgentLevel("Sokoban4J/levels/Easy/level0001.s4jl", new MySokobanAgent());
		
		System.out.println("MySokobanAgent result: " + result.getResult());
		
		System.exit(0);	
	}
	
	public class Tuple<X, Y> { 
	    public final X x; 
	    public final Y y; 
	    public Tuple(X x, Y y) { 
	        this.x = x; 
	        this.y = y; 
	    }

	    @Override
	    public String toString() {
	        return "(" + x + "," + y + ")";
	    }

	    @Override
	    public boolean equals(Object other) {
	        if (other == this) {
	            return true;
	        }

	        if (!(other instanceof Tuple)){
	            return false;
	        }

	        Tuple<X,Y> other_ = (Tuple<X,Y>) other;

	        // this may cause NPE if nulls are valid values for x or y. The logic may be improved to handle nulls properly, if needed.
	        return other_.x.equals(this.x) && other_.y.equals(this.y);
	    }

	    @Override
	    public int hashCode() {
	        final int prime = 31;
	        int result = 1;
	        result = prime * result + ((x == null) ? 0 : x.hashCode());
	        result = prime * result + ((y == null) ? 0 : y.hashCode());
	        return result;
	    }
	}

}


/*
 * Bugs:
 *      
 * 
 * 
 */
