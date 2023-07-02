/**
 * PeerLocation is a class that represents the location: IP and Port of an
 * external peer in a peer-to-peer distributed messaging system.
 * 
 * @author Rohan Amjad UCID: 30062188
 * @version 1.0
 * @since 1.0
 */
public class PeerLocation {
    private String IP;
    private int port;

    /**
     * Class constructor that specifies the IP address and Port of this PeerLocation
     * object.
     * 
     * @param IP   the IP of the peer
     * @param port the Port of the peer
     */
    PeerLocation(String IP, int port) {
        this.IP = IP;
        this.port = port;
    }

    /**
     * Gets the IP address of this PeerLocation object.
     * 
     * @return a String containing the IP address of this PeerLocation object
     */
    public String getIP() {
        return this.IP;
    }

    /**
     * Gets the Port of this PeerLocation object.
     * 
     * @return the Port of this PeerLocation object
     */
    public int getPort() {
        return this.port;
    }

    /**
     * Checks the equality of this PeerLocation object with another object.
     * This PeerLocation object is equal to another Object if the other Object is
     * this object, or if the other Object has the same IP address and Port as this
     * PeerLocation object.
     * 
     * @param o the Object that this PeerLocation object is being compared with
     * @return <code>true</code> if this PeerLocation object is equal to the Object
     *         o
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PeerLocation)) {
            return false;
        }

        PeerLocation p = (PeerLocation) o;
        return this.IP.equals(p.getIP()) && Integer.compare(this.port, p.getPort()) == 0;

    }

    /**
     * Gets the hash code of this PeerLocation object.
     * The hash code of this PeerLocation object is constructed using this
     * PeerLocation object's IP address and Port
     * 
     * @return the hash code of this PeerLocation object
     */
    @Override
    public int hashCode() {
        int hash = 17;
        hash = 31 * hash + IP.hashCode();
        hash = 31 * hash + port;
        return hash;
    }
}