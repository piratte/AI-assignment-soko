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

	// private List<EDirection> result;
	
    private Map<String, Integer> visitedStatesWithLevel;

	private Set<Tuple<Integer,Integer>> deadends;

	// private ArrayList<Tuple<Integer, Integer>> boxes;

	private static int[][] heuristicValueBase;

	private int depthLimit;

	private static int runs;
	
	@Override
	protected List<EDirection> think(BoardCompact board) {
		if (runs != 0) {
		    System.err.println("Another search attempt!");
		    System.exit(1);
        }
		
		// INIT SEARCH
        ArrayList<EDirection> result;

		// create a dead-end database
		this.deadends = createDeadendDB(board);

		// pre-compute heuristic
		heuristicValueBase = preComputeHeuristicBase(board);

        this.visitedStatesWithLevel = new HashMap<String, Integer>();
		//this.visitedBoardsHashes = new HashSet<String>();
		//this.visitedList = new ArrayList<String>(200);
		
		// DEBUG
		System.out.println();
		System.out.println("===== BOARD =====");
		board.debugPrint();
		System.out.println("=================");
		
		// FIRE THE SEARCH
		long searchStartMillis = System.currentTimeMillis();

		//depthLimit = 70;
		//dfs(new SearchState(board, 0, 70, result), EActionType.INVALID, EDirection.NONE); // the number marks how deep we will search (the longest plan we will consider)

        result = ida_star(board);
		
		runs++;
		
		long searchTime = System.currentTimeMillis() - searchStartMillis;
		
		printResults(searchTime, result);
				
		return result;
	}


    private ArrayList<EDirection> ida_star(BoardCompact board) {
        depthLimit = computeHeuristicValue(board);
        ResultStruct resStruct;

        do {
            resStruct = search(new SearchState(board, new ArrayList<EDirection>()), depthLimit);
            depthLimit = resStruct.getT();
        } while (!resStruct.found || depthLimit != Integer.MAX_VALUE);

        if (resStruct.found) return resStruct.getMoves();
        else return null;
    }

    private ResultStruct search(SearchState searchState, int depthLimit) {
	    searchState.getCost();
	    if (searchState.getCost() > depthLimit) {
	        return new ResultStruct(searchState.getCost(), null, false);
	    }

        // check for box in a deadend
        if (deadendCheck(searchState.getStateBoard())) return new ResultStruct(Integer.MAX_VALUE, null, false);

	    // check for visited state
        String boardCode = encodeBoard(searchState.getStateBoard());
        // level is bigger -> more available steps
        if (visitedStatesWithLevel.containsKey(boardCode) && visitedStatesWithLevel.get(boardCode) <= searchState.getCost()) {
            return new ResultStruct(searchState.getCost(), null, false);
        }
        visitedStatesWithLevel.put(boardCode, searchState.getLevel());

	    if (searchState.getStateBoard().isVictory()) {
	        return new ResultStruct(searchState.getCost(), searchState.getMoves(), true);
        }

        int min = Integer.MAX_VALUE;
        List<CAction> actions = getPossibleActions(searchState.getStateBoard());

        ResultStruct result;
        for (CAction action : actions) {
            result = search(searchState.applyAction(action), depthLimit);
            if (result.isFound()) return result;
            if (result.getT() < min) min = result.getT();
        }
        return new ResultStruct(min, null, false);
    }

    static int computeHeuristicValue(BoardCompact board) {
	    int result = 0;
        for (int xInd = 0; xInd < board.width(); xInd++) {
            for (int yInd = 0; yInd < board.height(); yInd++){
                if (CTile.isSomeBox(board.tile(xInd,yInd))) {
                    result += heuristicValueBase[xInd][yInd];
                }
            }
        }
        return result;
    }

    private int computeHeuristicValue(ArrayList<Tuple<Integer, Integer>> boxes) {
        int result = 0;
        for (Tuple<Integer, Integer> boxPos : boxes) {
            result += heuristicValueBase[boxPos.x][boxPos.y];
        }
        return result;
    }


    private boolean dfs(SearchState searchState, EActionType previousType, EDirection previousDirection) {
        BoardCompact board = searchState.getStateBoard().clone();
        ArrayList<EDirection> result = searchState.getMoves();
        //board.debugPrint();
        int curHeurVal = searchState.getHeuristicVal();

        if (searchState.getLevel() > depthLimit) return false; // DEPTH-LIMITED


        if (previousType == EActionType.PUSH) {
            // check for box in a deadend
            if (deadendCheck(board, previousDirection)) return false;
            curHeurVal = updateHeuristic(board, previousDirection, curHeurVal);
        }


        String boardCode = encodeBoard(board);
        // level is bigger -> more available steps
        if (visitedStatesWithLevel.containsKey(boardCode) && visitedStatesWithLevel.get(boardCode) <= searchState.getLevel()) { //(visitedBoardsHashes.contains(boardCode)) {
            return false;
        }
        visitedStatesWithLevel.put(boardCode, searchState.getLevel());

        // COLLECT POSSIBLE ACTIONS

        List<CAction> actions = getPossibleActions(board);

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
            if (dfs(new SearchState(board, searchState.getLevel() + 1, curHeurVal, moves), action.getType(), action.getDirection())) {
                // SOLUTION FOUND!
                return true;
            }

            board = reverseAction(board, action);
        }
        return false;
    }

    private List<CAction> getPossibleActions(BoardCompact board) {
        List<CAction> actions = new ArrayList<CAction>(4);

        for (CPush push : CPush.getActions()) {
            if (push.isPossible(board)) {
                actions.add(push);
            }
        }

        for (CMove move : CMove.getActions()) {
            if (move.isPossible(board)) {
                actions.add(move);
            }
        }
        return actions;
    }

    private ArrayList<Tuple<Integer, Integer>> getAllBoxesPositions(BoardCompact board) {
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

	private int[][] preComputeHeuristicBase(BoardCompact board) {
		// init search board
		int[][] result = new int[board.width()][board.height()];
		for (int xInd = 0; xInd < board.width(); xInd++) {
			for (int yInd = 0; yInd < board.height(); yInd++){
				result[xInd][yInd] = -1;
			}
		}

        int[][] prevIter;
		do {
            prevIter = cloneArray(result);
			for (int widthInd = 0; widthInd < board.width(); widthInd++) {
				for (int heightInd = 0; heightInd < board.height(); heightInd++) {
					// assign heuristic value for targets
					if (CTile.forSomeBox(board.tile(widthInd,heightInd))) {
						result[widthInd][heightInd] = 0;
					}

					// board tile is not a wall and the heuristic was not yet computed
					if ((!CTile.isWall(board.tile(widthInd,heightInd))) && result[widthInd][heightInd] != -1) {
						result = updateHeuristicBase(result, widthInd, heightInd);
					}

				}
			}
		} while (!Arrays.deepEquals(prevIter,result));

        return result;
	}

    /**
     * Clones the provided array
     *
     * @param src source
     * @return a new clone of the provided array
     */
    private static int[][] cloneArray(int[][] src) {
        int length = src.length;
        int[][] target = new int[length][src[0].length];
        for (int i = 0; i < length; i++) {
            System.arraycopy(src[i], 0, target[i], 0, src[i].length);
        }
        return target;
    }

    private int[][] updateHeuristicBase(int[][] input, int x, int y) {
		int[][] result = input.clone();
		int curBest = result[x][y] + 1;
		for (int d : new int[]{-1,1}) {
			int cur = result[x][y + d];
			if (cur >= curBest || cur == -1) {
                result[x][y + d] = curBest;
			}

			cur = result[x + d][y];
			if (cur >= curBest || cur == -1) {
                result[x + d][y] = curBest;
			}
		}

		return result;
	}

	private boolean deadendCheck(final BoardCompact board) {
        for (Tuple<Integer, Integer> pos : getAllBoxesPositions(board)) {
            if (deadends.contains(pos)) return true;
        }
        return false;
    }

    private boolean deadendCheck(final BoardCompact board, EDirection previousDirection) {
        if (deadends.contains(getNewBoxPossition(board, previousDirection))) {
            System.out.println("Deadend detected:");
            board.debugPrint();
            return true;
        }
        return false;
    }

    private Tuple<Integer, Integer> getNewBoxPossition( final BoardCompact board, EDirection previousDirection) {
        return new Tuple<Integer, Integer>(board.playerX + previousDirection.dX,
                board.playerY + previousDirection.dY);
    }

    /*
    private int updateHeuristic( final BoardCompact board, EDirection previousDirection, int curHeurVal) {
        // update heuristic
        boxes.remove(new Tuple<Integer, Integer>(board.playerX, board.playerY));
        int newBoxX = board.playerX + previousDirection.dX;
        int newBoxY = board.playerY + previousDirection.dY;
        boxes.add(new Tuple<Integer, Integer>(newBoxX, newBoxY));
        curHeurVal -= heuristicValueBase[board.playerX][board.playerY];
        curHeurVal += heuristicValueBase[newBoxX][newBoxY];
        return curHeurVal;
    }*/


    private Set<Tuple<Integer, Integer>> createDeadendDB(final BoardCompact board) {
		Set<Tuple<Integer, Integer>> result = new HashSet<Tuple<Integer, Integer>>();
        for (int widthInd = 1; widthInd < board.width()-1; widthInd ++) {
            for (int heightInd = 1; heightInd < board.height()-1; heightInd++) {
                if (isDeadendCorner(board, widthInd, heightInd)) {
                    result.add(new Tuple<Integer, Integer>(widthInd,heightInd));
                }
            }
        }

        //TODO: detect deadend straights

		return result;
	}

    private boolean isDeadendCorner(final BoardCompact board, int widthInd, int heightInd) {
        if (CTile.forSomeBox(board.tile(widthInd,heightInd))) return false;

	    int horizScore = 0;
	    int verticScore = 0;
        if (CTile.isWall(board.tile(widthInd-1,heightInd))) horizScore++;
        if (CTile.isWall(board.tile(widthInd+1,heightInd))) horizScore++;
        if (CTile.isWall(board.tile(widthInd,heightInd-1))) verticScore++;
        if (CTile.isWall(board.tile(widthInd,heightInd+1))) verticScore++;
        //noinspection SimplifiableIfStatement for the sake of code readibility
        if (horizScore == 0 || verticScore == 0) return false;

	    return horizScore + verticScore > 1;
    }

    /*
    private BoardCompact reverseAction(final BoardCompact board, CAction action) {

		result.remove(result.size()-1);
		action.reverse(board);
        return board;
    }*/

	private String encodeBoard(final BoardCompact board) {
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

	private void printResults(long searchTime, ArrayList<EDirection> result) {
		System.out.println("SEARCH TOOK:   " + searchTime + " ms");
		//System.out.println("NODES IN SET:  " + visitedBoardsHashes.size());
		System.out.println("NODES VISITED: " + visitedStatesWithLevel.size());
		System.out.println("PERFORMANCE:   " + ((double)visitedStatesWithLevel.size() / (double)searchTime * 1000) + " nodes/sec");
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
	    final X x;
	    final Y y;
	    Tuple(X x, Y y) {
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

            //noinspection unchecked
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

    private static class SearchState {
        private final BoardCompact stateBoard;
        private ArrayList<EDirection> moves;

        private SearchState(BoardCompact stateBoard, ArrayList<EDirection> moves) {
            this.stateBoard = stateBoard;
            this.moves = moves;
        }

        BoardCompact getStateBoard() {
            return stateBoard;
        }

        int getLevel() {
            return moves.size();
        }

        int getHeuristicVal() {
            return computeHeuristicValue(this.stateBoard);
        }

        int getCost() {
            return this.getHeuristicVal() + moves.size();
        }

        ArrayList<EDirection> getMoves() {
            return moves;
        }

        public SearchState applyAction(CAction action) {
            ArrayList<EDirection> newMoves = new ArrayList<EDirection>(moves.size());
            for (int i = 0; i < moves.size(); i++) newMoves.add(moves.get(i));

            BoardCompact newBoard = this.stateBoard.clone();
            action.perform(newBoard);
            return new SearchState(newBoard, newMoves);
        }
    }

    private class ResultStruct {
        // TODO: found = true <=> moves != null
        boolean found;
        int t;
        ArrayList<EDirection> moves;

        public ResultStruct(int t, ArrayList<EDirection> moves, boolean foundSolution) {
            this.t = t;
            this.moves = moves;
            this.found = foundSolution;
        }

        public boolean isFound() {

            return found;
        }

        public void setFound(boolean found) {
            this.found = found;
        }

        public int getT() {
            return t;
        }

        public void setT(int t) {
            this.t = t;
        }

        public ArrayList<EDirection> getMoves() {
            return moves;
        }

        public void setMoves(ArrayList<EDirection> moves) {
            this.moves = moves;
        }
    }
}


/*
 * Bugs:
 *      
 * 
 * 
 */
