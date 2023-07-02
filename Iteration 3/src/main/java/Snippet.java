package main.java;
/**
 * Snippet is a class that represents a snip message that is outlined in the
 * protocol.
 * The Snippet class implements the Comparable interface.
 * 
 * @author Rohan Amjad UCID: 30062188
 * @version 1.0
 * @since 1.0
 */
public class Snippet implements Comparable<Snippet> {
    private int timestamp;
    private PeerLocation sourcePeer;
    private String content;

    /**
     * Class constructor that specifies the content, the source peer, and the
     * timesatamp of this Snippet object.
     * 
     * @param content    the content of the snippet message
     * @param sourcePeer the source PeerLocation of the snippet message
     * @param timestamp  the timestamp the snippet message was sent
     */
    Snippet(String content, PeerLocation sourcePeer, int timestamp) {
        this.content = content;
        this.sourcePeer = sourcePeer;
        this.timestamp = timestamp;
    }

    /**
     * Gets the content of this Snippet object.
     * 
     * @return a String containing the content of this Snippet object
     */
    public String getContent() {
        return this.content;
    }

    /**
     * Gets the source PeerLocation this Snippet object.
     * 
     * @return a PeerLocation object containing the source peer informmation of this
     *         Snippet object
     */
    public PeerLocation getSourcePeer() {
        return this.sourcePeer;
    }

    /**
     * Gets the timestamp of this Snippet object.
     * 
     * @return the timestamp of this Snippet object
     */
    public int getTimestamp() {
        return this.timestamp;
    }

    /**
     * Compares this Snippet object with another Snippet object.
     * 
     * @param other the Snippet object that this Snippet object is being compared
     *              with
     * @return <code>1</code> if this Snippet object's timestamp less than,
     *         <code>0</code> if this Snippet object's timestamp is
     *         equal to, and <code>-1</code> if this Snippet object's timestamp is
     *         greater than the other Snippet object's timestamp
     *         Snippet object
     */
    @Override
    public int compareTo(Snippet other) {
        return Integer.compare(getTimestamp(), other.getTimestamp());
    }
}