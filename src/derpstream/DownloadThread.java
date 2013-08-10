package derpstream;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.logging.Logger;

/**
 *
 * @author Mads
 */
public final class DownloadThread implements Runnable {
    private static final Logger LOGGER = Logger.getLogger("derpstream");
    
    private final ChunkInfo chunkInfo;
    private final DerpStream derpStream;
            
    public DownloadThread(DerpStream derpStream, ChunkInfo chunkInfo) {
        this.chunkInfo = chunkInfo;
        this.derpStream = derpStream;
    }
    
    @Override
    public void run() {
        int badsInARow = 0;
        DerpStreamCallbacks callbacks = derpStream.getCallbacks();
        // 4mb buffer
        ByteBuffer workBuffer = ByteBuffer.allocate(1024 * 1024 * 8);
        while(derpStream.isRunning() && badsInARow < 3) {
            // Grab some work
            try {
                Piece p = chunkInfo.grabWork();


                LOGGER.fine("Downloading " + p);
                if(callbacks != null) {
                    callbacks.startedDownload(p.pieceIndex);
                }

                // Do the work
                ByteBuffer data = null;
                int ntry = 0;
                while(data == null) {
                    try {
                        ntry++;
                        data = downloadPiece(p, workBuffer);
                    } catch(SocketTimeoutException ex) {
                        if(ntry-1 >= DerpStream.MAX_RETRIES) break;
                        if(callbacks != null) {
                            callbacks.retryingDownload(p.pieceIndex);
                        }
                        LOGGER.warning("Retrying " + p + " (attempt " + (ntry+1) + ")");
                    } catch(IOException ex){
                        ex.printStackTrace(System.err);
                        break;
                    }
                }
                
                if(data == null) {
                    // Couldn't download it
                    badsInARow++;
                    chunkInfo.lostPiece(p);
                    continue;
                }
                
                badsInARow = 0;
                p.data = data;
                if(callbacks != null) {
                    callbacks.finishedDownload(p.pieceIndex, p.data.limit());
                }

                // Register the work
                chunkInfo.registerPiece(p);
            } catch(InterruptedException ex) {
                LOGGER.warning(Thread.currentThread().getName() + " interrupted.");
            }
        }
       
    }
    
    private static ByteBuffer downloadPiece(Piece p, ByteBuffer tempbuffer) throws IOException {
        tempbuffer.clear();
        
        int offset = 0;
        HttpURLConnection urlconn = (HttpURLConnection) p.URL.openConnection();
        urlconn.setConnectTimeout(3000);
        urlconn.setReadTimeout(3000);
        try (InputStream fileStream = urlconn.getInputStream()) {
            int nread = 0;
            do {
                offset += nread;
                nread = fileStream.read(tempbuffer.array(), offset, tempbuffer.capacity() - offset);
                if(tempbuffer.capacity() - offset <= 0) throw new IllegalStateException("tempbuffer overrun");
            } while(nread >= 0);
        }
        urlconn.disconnect();
        tempbuffer.limit(offset);

        // Create dedicated copy
        ByteBuffer dataCopy = ByteBuffer.allocate(offset);
        dataCopy.put(tempbuffer);
        dataCopy.flip();

        LOGGER.info("Downloaded " + offset/1024 + " kb");
        return dataCopy;
    }
}
