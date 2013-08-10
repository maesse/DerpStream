
package derpstream;

/**
 *
 * @author Mads
 */
public interface DerpStreamCallbacks {
    void startedDownload(int piece);
    void finishedDownload(int piece, int size);
    void retryingDownload(int piece);
    void fileCreated(String path);
    void finishedWriting(int piece);
    void skippedWriting(int piece);
    void pieceAvailable(int piece);
}
