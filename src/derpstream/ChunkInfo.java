package derpstream;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.PriorityQueue;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Mads
 */
public final class ChunkInfo extends TimerTask {
    private static final Logger LOGGER = Logger.getLogger("derpstream");
    
    private static final int BUFFER_PIECES = 3;
    private final StreamInfo streamInfo;
    private final DerpStream derpStream;
    
    // Template for generating chunk links
    private final String chunkPath;
    private final LinkedBlockingDeque<Piece> pieceQueue = new LinkedBlockingDeque<>();
    private final PriorityQueue<Piece> bufferedPieces = new PriorityQueue<>();
    
    private long nextPieceTime; // when next piece becomes available
    private int maxValidPiece; // maximum valid piece that can be requested
    private int writePiece; // next piece to be written to disk.
    
    private final Object waitObj = new Object();

    ChunkInfo(DerpStream derpStream, StreamInfo streamInfo) throws IOException {
        this.streamInfo = streamInfo;
        this.derpStream = derpStream;

        // Download chunklist
        LOGGER.info("Getting latest chunklist...");
        String chunkList = DerpStream.downloadString(streamInfo.getChunkInfoPath());
        if(!DerpStream.isM3U(chunkList)) throw new IllegalStateException("Invalid chunklist: " + chunkList);

        // Parse current chunk index
        String search = "#EXT-X-MEDIA-SEQUENCE:";
        int start = chunkList.indexOf(search) + search.length();
        int end = chunkList.indexOf("\n", start);
        maxValidPiece = Integer.parseInt(chunkList.substring(start, end));
        writePiece = maxValidPiece - BUFFER_PIECES;
        LOGGER.info("Ok. Stream is at piece " + maxValidPiece + "\n");
        
        
        
        // Figure out chunkPath template
        String[] lines = chunkList.split("\n");
        String chunkPath = null;
        for (String line : lines) {
            if(!line.startsWith("#")) {
                if(line.contains(""+maxValidPiece)) {
                    chunkPath = line.replace("" + maxValidPiece, "%d");
                    LOGGER.info("Setting chunkpath: " + chunkPath);
                    break;
                }
            }
        }
        if(chunkPath == null) throw new IllegalStateException("Couldn't find chunkPath");
        this.chunkPath = chunkPath;
        
        // Enqueue valid pieces
        for (int i = 0; i < BUFFER_PIECES; i++) {
            pieceQueue.add(makePiece(writePiece+i));
        }

        // 10 seconds to next piece becomes available
        nextPieceTime = System.currentTimeMillis() + 10000;
    }
    
    // Increments the piece counter for every 10 seconds since start.
    public void updatePiece() {
        long time = System.currentTimeMillis();
        while(time >= nextPieceTime) {
            nextPieceTime += 10000;
            pieceQueue.add(makePiece(maxValidPiece));
            DerpStreamCallbacks callbacks = derpStream.getCallbacks();
            if(callbacks != null) {
                callbacks.pieceAvailable(maxValidPiece);
            }
            
            maxValidPiece++;
        }
    }
    
    public String getChunkPath() {
        return String.format(chunkPath, writePiece);
    }
    
    private Piece makePiece(int index) {
        return new Piece(index, String.format(chunkPath, index));
    }

    @Override
    public void run() {
        // Update pieces
        updatePiece();
    }
    
    void lostPiece(Piece p) {
        synchronized(bufferedPieces) {
            bufferedPieces.add(p);
        }
        
        synchronized(waitObj) {
            waitObj.notify();
        }
    }
    
    public void registerPiece(Piece p) {
        synchronized(bufferedPieces) {
            bufferedPieces.add(p);
        }
        
        synchronized(waitObj) {
            waitObj.notify();
        }
    }

    public Piece grabWork() throws InterruptedException {
        return pieceQueue.takeFirst();
    }

    void startWriting(FileOutputStream fos) throws IOException {
        DerpStreamCallbacks callbacks = derpStream.getCallbacks();
        
        while(derpStream.isRunning()) {
            
            // Write data to the file as it becomes available.
            synchronized(bufferedPieces) {
                while(bufferedPieces.size() > 0) {
                    Piece topPiece = bufferedPieces.peek();

                    // Not what we're looking for?
                    if(topPiece.pieceIndex != writePiece) break;

                    // Grab it!
                    Piece removedPiece = bufferedPieces.poll();

                    // Check it!
                    if(removedPiece != topPiece) throw new RuntimeException("Huh?");

                    if(topPiece.data != null) {
                        LOGGER.fine("Writing " + topPiece);

                        // Write it!
                        fos.getChannel().write(topPiece.data);
                        
                        if(callbacks != null) {
                            callbacks.finishedWriting(topPiece.pieceIndex);
                        }
                    } else {
                        LOGGER.warning("Skipping " + topPiece);
                        if(callbacks != null) {
                            callbacks.skippedWriting(topPiece.pieceIndex);
                        }
                    }
                    writePiece++;
                }
            }
            
            synchronized(waitObj) {
                try {
                    waitObj.wait(5000);
                } catch (InterruptedException ex) {
                    
                }
            }
        }
    }

    
}
