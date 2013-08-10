package derpstream;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;

/**
 *
 * @author Mads
 */
public class StreamInfo {
    // Information
    private final String path;
    private final String resolution;
    private final int bandwidth;
    
    // URL of the chunkinfo file
    private final URL chunkInfoPath;
    
    
    
    // unused
    private final String programid;

    private StreamInfo(String path, HashMap<String, String> properties) throws MalformedURLException {
        chunkInfoPath = new URL(DerpStream.MAINPATH + path);
        this.path = path;
        resolution = properties.get("RESOLUTION");
        if(properties.containsKey("BANDWIDTH")) {
            bandwidth = Integer.parseInt(properties.get("BANDWIDTH"));
        } else {
            bandwidth = -1;
        }
        programid = properties.get("PROGRAM-ID");
    }

    public URL getChunkInfoPath() {
        return chunkInfoPath;
    }
    
    

    @Override
    public String toString() {
        return String.format("%s, %s @ %.2f mbit/s", path, resolution, bandwidth/(1024*1024.0f));
    }
    
    public static ArrayList<StreamInfo> retrievePlaylist() throws IOException {
        // Download playlist
        String playlist = DerpStream.downloadString(new URL(DerpStream.MAINPATH + "playlist.m3u8"));
        
        // Verify format
        if(!DerpStream.isM3U(playlist)) throw new IllegalStateException("Got invalid playlist: " + playlist);
        
        // Extract information
        String extKey = "#EXT-X-STREAM-INF:";
        
        // Like a stack for file properties. 
        // Gets popped when a file is encountered.
        HashMap<String, String> extMap = new HashMap<>();
        
        ArrayList<StreamInfo> results = new ArrayList<>();
        
        String[] lines = playlist.split("\n");
        for (String line : lines) {
            if(line.isEmpty()) continue;
            
            if(line.startsWith("#")) {
                // Read extension info
                if(!line.startsWith(extKey)) continue;
                String extInfo = line.substring(extKey.length());
                // Read key-value pairs from extInfo
                String[] pairs = extInfo.split(",");
                for (String pair : pairs) {
                    // Split the pair into a key and value
                    String[] keyvalue = pair.split("=");
                    if(keyvalue.length != 2) System.out.println("Invalid keyvalue pair: " + pair);
                    else {
                        extMap.put(keyvalue[0], keyvalue[1]);
                    }
                }
            } else {
                // Assume file
                StreamInfo strInfo = new StreamInfo(line, extMap);
                results.add(strInfo);
                extMap.clear();
            }
        }
        
        return results;
    }
}
