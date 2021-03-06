package cz.sokoban4j.simulation.board.oop;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cz.sokoban4j.simulation.board.compact.BoardCompact;
import cz.sokoban4j.simulation.board.oop.entities.BoxEntity;
import cz.sokoban4j.simulation.board.oop.entities.Entity;
import cz.sokoban4j.simulation.board.oop.entities.EntityFactory;
import cz.sokoban4j.simulation.board.oop.entities.PlayerEntity;
import cz.sokoban4j.utils.S4JLReader;
import cz.sokoban4j.utils.SokReader;
import cz.sokoban4j.utils.TextLevelS4JL;

public class Board {

	public final int width;
	
	public final int height;
	
	public final Tile[][] tiles;
	
	/**
	 * Auto-Filled if initialized via {@link #fromFileS4JL(File)}.
	 */
	public String level;
	
	public PlayerEntity player;
	
	public List<BoxEntity> boxes;
	
	public Board(int width, int height) {
		this.width = width;
		this.height = height;
		tiles = new Tile[width][height];
		for (int x = 0; x < width; ++x) {
			for (int y = 0; y < height; ++y) {
				Tile tile = new Tile();
				tile.tileX = x;
				tile.tileY = y;
				tiles[x][y] = tile;
			}			
		}
	}
	
	/**
	 * Must be called after you initialize 'tiles' to 
	 * initialize {@link #player} and {@link #boxes}.
	 */
	public void initEntities() {
		player = null;
		boxes = new ArrayList<BoxEntity>();
		
		for (int x = 0; x < width; ++x) {
			for (int y = 0; y < height; ++y) {
				if (tile(x, y).isPlayer()) player = (PlayerEntity) tile(x, y).entity;
				if (tile(x, y).isSomeBox()) boxes.add((BoxEntity) tile(x, y).entity);
			}			
		}
	}
	
	/**
	 * Returns tile on [X;Y] ... 0 &lt;= x &lt; {@link #width}, 0 &lt;= y &lt; {@link #height}. 
	 * @param x 0 &lt;= x &lt; {@link #width}
	 * @param y 0 &lt;= y &lt; {@link #height}
	 * @return
	 */
	public Tile tile(int x, int y) {
		return tiles[x][y];
	}
	
	/**
	 * Turns {@link Tile} resp. {@link Board#tiles} descriptions into 'tileFlags' using {@link Tile#computeTileFlag()}.
	 * @return
	 */
	public BoardCompact makeBoardCompact() {
		BoardCompact result = new BoardCompact(width, height);
		for (int x = 0; x < width; ++x) {
			for (int y = 0; y < height; ++y) {
				result.tiles[x][y] = tile(x, y).computeTileFlag();
			}
		}
		
		result.playerX = player.getTileX();
		result.playerY = player.getTileY();
		
		result.boxCount = 0;
		result.boxInPlaceCount = 0;
		
		for (BoxEntity box : boxes) {
			++result.boxCount;
			if (box.inPlace()) ++result.boxInPlaceCount;
		}
		
		return result;
	}
	
	/**
	 * Moves 'entity' unconditionally.
	 * @param entity
	 * @param tileX
	 * @param tileY
	 */
	public void move(Entity entity, int tileX, int tileY) {
		Tile sourceTile = entity.getTile();
		Tile destTile = tile(tileX, tileY);
		
		sourceTile.entity = null;
		destTile.entity = entity;
		entity.setTile(destTile);
	}
	
	/**
	 * Whether the board is in WIN-STATE == all boxes are in correct places.
	 * @return
	 */
	public boolean isVictory() {
		for (Entity box : boxes) {
			if (!box.getTile().forBox(box.getType())) return false;
		}
		return true;
	}

	/**
	 * Throws {@link RuntimeException} if the board is invalid.
	 */
	public void validate() {
		int playerCount    = 0;
		int boxPlacesCount = 0;
		int boxCount       = 0;
		
		Map<Integer, Integer> specificBoxes = new HashMap<Integer, Integer>();
		Map<Integer, Integer> specificPlaces = new HashMap<Integer, Integer>();
		
		for (int x = 0; x < width; ++x) {
			for (int y = 0; y < height; ++y) {
				if (tile(x,y).isPlayer()) ++playerCount;
				if (tile(x,y).forSomeBox()) {
					++boxPlacesCount;
					Integer c = specificPlaces.get(tile(x,y).place.getBoxNum());
					if (c == null) c = 0;
					++c;
					specificPlaces.put(tile(x,y).place.getBoxNum(), c);					
				}
				if (tile(x,y).isSomeBox()) {
					++boxCount;
					Integer c = specificBoxes.get(tile(x,y).entity.getType().getBoxNum());
					if (c == null) c = 0;
					++c;
					specificBoxes.put(tile(x,y).entity.getType().getBoxNum(), c);
				}
				
			}
		}
		if (playerCount < 1) throw new RuntimeException("NO PLAYER ON THE BOARD");
		if (playerCount > 1) throw new RuntimeException("MORE THAN 1 PLAYER ON THE BOARD");
		if (boxCount <= 0) throw new RuntimeException("THERE ARE NO BOXES IN THE MAP");
		if (boxCount != boxPlacesCount) throw new RuntimeException("BOX(" + boxCount + ") and TARGET BOX PLACES(" + boxPlacesCount + ") COUNT MISMATCH");
		
		int anyBox = 0;
		for (Integer boxNum : specificBoxes.keySet()) {
			Integer specificBoxCount = specificBoxes.get(boxNum);
			Integer specificPlaceCount = specificPlaces.get(boxNum);
			if (specificPlaceCount == null) specificPlaceCount = 0;
			if (specificBoxCount > specificPlaceCount) {
				anyBox += specificBoxCount - specificPlaceCount;						
			}
		}
		
		Integer anyPlaceCount = specificPlaces.get(EPlace.BOX_ANY.getBoxNum());
		if (anyPlaceCount == null) anyPlaceCount = 0;
		if (anyBox > 0 && anyBox > anyPlaceCount) {
			throw new RuntimeException("Invalid/Incompatible combination of places for boxes.");
		}
	}
	
	// ==============
	// STATIC LOADERS
	// ==============
	
	/**
	 * Creates the board from file .s4jl.
	 * @param file
	 * @param levelNumber level number to load; 0-based
	 * @return
	 */
	public static Board fromFileS4JL(File file, int levelNumber) {
		try {
			return fromReaderS4JL(file.getName(), new FileReader(file), levelNumber);
		} catch (Exception e) {
			throw new RuntimeException("Failed to load Board from '" + file.getAbsolutePath() + "'.", e);
		}
	}
	
	/**
	 * Creates the board from the data given by reader (expects .s4jl format).
	 * @param levelName 
	 * @param textReader
	 * @param levelNumber level number to load; 0-based
	 * @return
	 */
	public static Board fromReaderS4JL(String levelName, Reader textReader, int levelNumber) {
		Board board = null;
		
		// INIT READER
		S4JLReader reader = new S4JLReader(levelName, textReader);
		
		// FIND CORRECT LEVEL
		for (int i = 0; i < levelNumber; ++i) {
			reader.readNext();
		}
		// EXTRACT LEVEL DATA
		TextLevelS4JL level = reader.readNext();
		
		// PARSE LEVEL DATA
		return fromTextLevel(level);
	}
	
	/**
	 * Creates the board from file .sok; reads 'levelNumber'-th level (0-based).
	 * @param file
	 * @param levelNumber level number to load; 0-based
	 * @return
	 */
	public static Board fromFileSok(File file, int levelNumber) {
		try {
			return fromReaderSok(file.getName(), new FileReader(file), levelNumber);
		} catch (Exception e) {
			throw new RuntimeException("Failed to load Board from '" + file.getAbsolutePath() + "'.", e);
		}
	}
	
	/**
	 * Creates the board from the data given by reader (expects .sok format).
	 * @param levelName
	 * @param textReader
	 * @param levelNumber level number to load; 0-based
	 * @return
	 */
	public static Board fromReaderSok(String levelName, Reader textReader, int levelNumber) {
		Board board = null;
		
		// INIT READER
		SokReader reader = new SokReader(levelName, textReader);
		
		// FIND CORRECT LEVEL
		for (int i = 0; i < levelNumber; ++i) {
			reader.readNext();
		}
		// EXTRACT LEVEL DATA
		TextLevelS4JL level = reader.readNext();
		reader.close();
		
		// PARSE LEVEL DATA
		return fromTextLevel(level);
	}
	
	/**
	 * Creates the board from {@link TextLevelS4JL} description.
	 * @param level
	 * @return
	 */
	public static Board fromTextLevel(TextLevelS4JL level) {
		if (level == null) throw new RuntimeException("Passed level is null.");
		level.validate();
		
		Board board = new Board(level.getWidth(), level.getHeight());
		
		board.level = level.getName();
		
		for (int y = 0; y < level.getHeight(); ++y) {
			String line = level.getMaze().get(y);
			for (int x = 0; x < level.getWidth(); ++x) {
				String symbol = line.substring(x, x+1);
				Tile tile = board.tile(x, y);
				tile.space = ESpace.fromSymbol(symbol);
				tile.entity = EntityFactory.createEntity(EEntity.fromSymbol(symbol), tile);
				tile.place = EPlace.fromSymbol(symbol);					
			}
		}
			
		board.initEntities();
			
		return board;
	}
	
}
