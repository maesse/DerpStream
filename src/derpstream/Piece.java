package derpstream;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;

/**
 *
 * @author Mads
 */
// Download job
public final class Piece implements Comparable<Piece> {
    public final int pieceIndex;
    public final URL URL;
    public ByteBuffer data;

    public Piece(int index, String chunkPath) {
        this.pieceIndex = index;
        try {
            this.URL = new URL(DerpStream.MAINPATH + chunkPath);
        } catch(MalformedURLException ex) {
            // Whatever. It shouldn't happen.
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public int compareTo(Piece o) {
        return Integer.compare(pieceIndex, o.pieceIndex);
    }
    
    @Override
    public String toString() {
        return "Piece " + pieceIndex;
    }
}
