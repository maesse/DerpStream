package derpstream;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Timer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Mads
 */
public class DerpStream implements Runnable {
    public static final String MAINPATH = "http://cds.x5p5f3b8.hwcdn.net/valve/smil:Dota_English.smil/";
    public static int MAX_RETRIES = 5;
    private static final int MAX_THREADS = 1;
    
    // List of potential streams
    private ArrayList<StreamInfo> streamlist;
    
    // The active stream
    private StreamInfo selectedStream;
    
    private ChunkInfo chunkInfo;
    
    private FileOutputStream fos;
    
    // Threads
    private Timer chunkTimer;
    private Thread[] downloadThreads;
    private Thread fileWriteThread;
    
    private volatile boolean kill = false;
    
    private String fileSavePath;
    
    private ThreadGroup threadGroup = new ThreadGroup(Thread.currentThread().getThreadGroup(), "DerpGroup");
    
    private DerpStreamCallbacks callbacks;
    
    public DerpStream() throws IOException {
        System.out.println("DerpStream 1.0");
        
        // Get streams
        System.out.println("Retrieving playlist...");
        streamlist = StreamInfo.retrievePlaylist();
        
        // Print streams
        System.out.println(String.format("Found %d streams...", streamlist.size()));
        for (int i = 0; i < streamlist.size(); i++) {
            System.out.println(String.format("Stream %d: %s", i, streamlist.get(i)));
        }
        System.out.println("---");
    }
    
    public ArrayList<StreamInfo> getStreamList() {
        return streamlist;
    }

    public DerpStreamCallbacks getCallbacks() {
        return callbacks;
    }

    public void setCallbacks(DerpStreamCallbacks callbacks) {
        this.callbacks = callbacks;
    }
    
    public void runAndSave(StreamInfo stream, String savePath, boolean blocking) throws FileNotFoundException, IOException {
        // Select stream
        selectedStream = stream;
        System.out.println("Selecting stream: " + selectedStream);
        
        chunkInfo = new ChunkInfo(this, selectedStream);
        
        // Start sequence timer
        chunkTimer = new Timer("TimerThread", true);
        chunkTimer.schedule(chunkInfo, 10000, 10000);
        
        // Boot up the worker threads
        downloadThreads = new Thread[MAX_THREADS];
        for (int i = 0; i < MAX_THREADS; i++) {
            downloadThreads[i] = new Thread(threadGroup, new DownloadThread(this, chunkInfo), "DownloadThread-" + i);
            downloadThreads[i].start();
        }
        
        // Open output file
        savePath += chunkInfo.getChunkPath();
        System.out.println("Streaming to file: " + savePath);
        fileSavePath = savePath;
        fos = new FileOutputStream(savePath);
        
        if(callbacks != null) callbacks.fileCreated(savePath);
        
        if(blocking) {
            chunkInfo.startWriting(fos);
        } else {
            fileWriteThread = new Thread(threadGroup, this, "FileWriterThread");
            fileWriteThread.start();
        }
    }
    
    public void stopStreaming() {
        if(kill) return;
        
        // Mark for destruction
        kill = true;
        
        // Notify threads
        threadGroup.interrupt();
        chunkTimer.cancel();
        
        // Forget em
        fileWriteThread = null;
        chunkTimer = null;
        downloadThreads = null;
    }
    
    public static boolean isM3U(String file) {
        return file.startsWith("#EXTM3U");
    }
    
    public static String downloadString(URL path) throws IOException {
        int ntry = 0;
        while(true) {
            ntry++;
            try {
                byte[] buffer = new byte[1024];
                int offset = 0;

                HttpURLConnection urlConn = (HttpURLConnection) path.openConnection();
                urlConn.setConnectTimeout(3000);
                urlConn.setReadTimeout(3000);
                try (InputStream inputStream = urlConn.getInputStream()) {
                    int b;
                    while((b = inputStream.read()) != -1) {
                        if(offset == buffer.length) {
                            buffer = Arrays.copyOf(buffer, buffer.length * 2);
                        }
                        buffer[offset++] = (byte)(b & 0xff);
                    }
                }
                return new String(buffer, 0, offset, "UTF-8");
            } catch(SocketTimeoutException ex) {
                if(ntry >= 3) throw ex;
            }
        }
    }
    
    

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws IOException {
        DerpStream derp = new DerpStream();
        ArrayList<StreamInfo> streams = derp.getStreamList();
        derp.runAndSave(streams.get(1), "", true);
    }

    @Override
    public void run() {
        try {
            chunkInfo.startWriting(fos);
        } catch (IOException ex) {
            Logger.getLogger(DerpStream.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    boolean isRunning() {
        return !kill;
    }

    public String getFilePath() {
        return fileSavePath;
    }

    public void setMaxRetries(String text) {
        try {
            MAX_RETRIES = Integer.parseInt(text);
            if(MAX_RETRIES < 0) MAX_RETRIES = 0;
        } catch(NumberFormatException ex) {
            
        }
    }
    
    
    
}
