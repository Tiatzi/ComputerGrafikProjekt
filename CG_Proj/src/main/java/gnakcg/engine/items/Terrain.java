package gnakcg.engine.items;

import gnakcg.engine.graph.HeightMapMesh;
import gnakcg.engine.graph.Texture;
import gnakcg.engine.mapgenerator.HeightMapGenerator;
import org.joml.Vector2i;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Encapsulates the terrain as a whole. Keeps track in which {@link Chunk} the player
 * is currently located. Asynchronously generates the adjacent chunks and stores them in a map.
 * Maintains a list of GameItems(Chunks) which are to be rendered each cycle. This lists changes
 * depending on the current chunk of the player. Only the Adjacent Chunks and the current one
 * with the player are to be rendered.
 *
 * @author Anton K.
 * @author Gires N.
 */
public class Terrain {
    /**
     * The Chunk Size-> length of the height map generated by the HeightMapGenerator
     */
    public static final int CHUNK_SIZE = 256;
    /**
     * The Distance factor at which the music box is placed.
     * The greater, the easier the game -> the music Box will be located closer.
     */
    private float musicBoxDistanceFactor;
    private Chunk currentChunk;
    private final Map<Vector2i, Chunk> chunkPositionToChunksMap;
    private final Set<GameItem> gameItemsToRender = Collections.synchronizedSet(new HashSet<>());

    private final ChunkUpdater updater;

    public Collection<GameItem> getGameItems() {
        return gameItemsToRender;
    }

    public synchronized Vector2i getCurrentChunkPosition() {
        return currentChunk.getChunkPosition();
    }

    /**
     * Sets the current chunk of the player. Should be called each time the player moves.
     */
    public void setCurrentChunkPosition(Vector2i newChunkPosition) {
        if (newChunkPosition.equals(this.currentChunk.getChunkPosition()))
            return;
        this.currentChunk = chunkPositionToChunksMap.get(newChunkPosition);
        new Thread(updater).start();
    }

    public Terrain(float scale, float minY, float maxY, String textureFile, int textInc,
                   float musicBoxDistanceFactor, long seed) throws Exception {
        chunkPositionToChunksMap = Collections.synchronizedMap(new HashMap<>());
        this.musicBoxDistanceFactor = musicBoxDistanceFactor;
        HeightMapGenerator generator = new HeightMapGenerator(seed, 150, 8, 2, 0.5f);
        Texture texture = new Texture(textureFile);
        updater = new ChunkUpdater(scale, minY, maxY, textInc, texture, generator);

        //create the current chunk synchronously
        currentChunk = updater.createChunk(new Vector2i(0, 0));
        chunkPositionToChunksMap.put(new Vector2i(0, 0), currentChunk);
        //create the adjacent chunks
        new Thread(updater).start();
    }

    public float getHeight(float x, float z) {
        return currentChunk.getHeight(x, z);
    }

    /**
     * Positions the music box taking into account the song length,
     * step size and {@link Terrain#musicBoxDistanceFactor}
     */
    public Vector3f generateMusicBoxPosition(float terrainScale, float cameraStepSize, float songLengthInMiliSeconds) {
        //find and create the chunk
        Vector2i chunkCoordinates = generateBoxChunkCoordinates(terrainScale, cameraStepSize, songLengthInMiliSeconds);

        Chunk chunkWithMusicBox = updater.createChunk(chunkCoordinates);
        chunkPositionToChunksMap.put(chunkCoordinates, chunkWithMusicBox);

        Vector3f minimum = chunkWithMusicBox.getMinimumHeightPointInLocalCoordinates();
        return new Vector3f(chunkCoordinates.x * terrainScale + minimum.x, minimum.y, chunkCoordinates.y * terrainScale + minimum.z);
    }

    private Vector2i generateBoxChunkCoordinates(float terrainScale, float cameraStepSize, float songLengthInMiliSeconds) {
        float songLengthInSeconds = songLengthInMiliSeconds / 1000;
        //we assume the user can press a direction button 5 times per second at maximum
        float maxDistancePerSecond = terrainScale * cameraStepSize;
        float maximumDistanceInStraightLineForSongDuration = maxDistancePerSecond * songLengthInSeconds;
        int maxChunk = (int) (musicBoxDistanceFactor * maximumDistanceInStraightLineForSongDuration / terrainScale);

        Random r = ThreadLocalRandom.current();
        int signX = r.nextInt() > 0 ? 1 : -1;
        int signZ = r.nextInt() > 0 ? 1 : -1;
        int xRand = r.nextInt(2*maxChunk);
        int yRand = 2*maxChunk - xRand;
        int x = xRand * signX;
        int z =yRand * signZ;
        //avoid creating in the start chunk
        if (x == 0 && z == 0) {
            x = 1;
            z = -1;
        }
        return new Vector2i(x, z);
    }

    /**
     * Used to create adjacent chunks asynchronously.
     */
    private class ChunkUpdater implements Runnable {

        private final float scale;
        private final float minY;
        private final float maxY;
        private final int textInc;
        private final Texture texture;
        private final HeightMapGenerator generator;

        public ChunkUpdater(float scale, float minY, float maxY, int textInc, Texture texture, HeightMapGenerator generator) {
            this.scale = scale;
            this.minY = minY;
            this.maxY = maxY;
            this.textInc = textInc;
            this.texture = texture;
            this.generator = generator;
        }

        @Override
        public void run() {
            updateGameItemsToRender();
        }

        private Chunk createChunk(Vector2i chunkPosition) {
            float[][] heightMap = generator.generate(chunkPosition.y * (CHUNK_SIZE - 1) - 1, chunkPosition.x * (CHUNK_SIZE - 1) - 1, CHUNK_SIZE + 2, CHUNK_SIZE + 2);
            HeightMapMesh heightMapMesh = new HeightMapMesh(minY, maxY, heightMap, CHUNK_SIZE, CHUNK_SIZE, texture, textInc);
            float xDisplacement = chunkPosition.x * scale;
            float zDisplacement = chunkPosition.y * scale;

            Chunk terrainChunk = new Chunk(chunkPosition, heightMapMesh);
            terrainChunk.setScale(scale);
            terrainChunk.setPosition(xDisplacement, 0, zDisplacement);
            return terrainChunk;
        }

        private void updateGameItemsToRender() {
            Vector2i current = getCurrentChunkPosition();
            Collection<Vector2i> adjacentChunkPositions = currentChunk.getAdjacentChunksPositions();

            adjacentChunkPositions.forEach(this::createChunkIfNecessary);
            List<GameItem> newGameItems = new LinkedList<>();

            for (Vector2i adjacentChunkPosition : adjacentChunkPositions) {
                GameItem chunk = chunkPositionToChunksMap.get(adjacentChunkPosition);
                newGameItems.add(chunk);
            }

            newGameItems.add(chunkPositionToChunksMap.get(current));
            //remove the old ones and afterwards add all new
            gameItemsToRender.removeIf((item) -> !newGameItems.contains(item));
            gameItemsToRender.addAll(newGameItems);
        }

        private void createChunkIfNecessary(Vector2i chunkPosition) {
            if (!chunkPositionToChunksMap.containsKey(chunkPosition))
                chunkPositionToChunksMap.put(chunkPosition, createChunk(chunkPosition));
        }
    }
}
