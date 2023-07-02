package registry;
public class Peer {
	String address;
	private int port;
	String teamName;
	boolean acked = false;
	
	Peer[] peersSent = null;
	
	String key() {
		return teamName;
	}
	public String toString() {
		return key() + " " + address + ":" + getPort();
	}
	int getPort() {
		return port;
	}
	void setPort(int port) {
		if (port > 0) {
			this.port = port;
		} else {
			port = 59921;
		}
		
	}
}
