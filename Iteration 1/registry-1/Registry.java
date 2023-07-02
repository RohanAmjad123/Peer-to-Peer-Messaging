import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.*;

/**
 * Registry process that allows peers in the system to register as peers and ask for
 * the location of other peers in the system.
 * <p>
 * Communication with individual peers is managed using RequestProcessor.
 * @author Nathaly Verwaal
 *
 */
public class Registry {
	/** Number of threads allowed in the system.  Each thread manages communication with a peer */
	public static final int THREAD_POOL_SIZE = 10;
	/** Maximum number of peer addresses that will be send when communicating with a peer */
	public static final int MAX_NUM_OF_PEERS = 4;
	/** If no port number is provided when running the registry, this port number will be used. */
	public static final int DEFAULT_PORT_NUMBER = 55921;

	protected final static Logger LOGGER = Logger.getLogger(Registry.class.getName());
	
	/** Contains the peers we know about: no duplicates allowed */
	private ConcurrentHashMap<String, Peer> peers = new ConcurrentHashMap<String, Peer>();
	
	/** Port number used by this registry */
	protected int portNumber;
	
	/**
	 * Create registry to run at specified port number	
	 * @param aPortNumber port number to attempt running this registry at.
	 */
	public Registry(int aPortNumber) {
		portNumber = aPortNumber;
		LOGGER.setLevel(Level.INFO);
		System.setProperty("java.util.logging.SimpleFormatter.format",
				"%5$s %1$tF %1$tT%n");
	}
	
	/**
	 * Starts this registry and accepts connection requests from peers.  For each
	 * connection request, a RequestProcessor object is created and provided to the
	 * thread pool.
	 * @throws IOException if there are problems starting this registry server or if there
	 * are problems communication alonger a connection with a peer.
	 */
	public void start() throws IOException {
		ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
		try {
			ServerSocket server = new ServerSocket(portNumber);
			LOGGER.log(Level.INFO, "Server started at " + 
					server.getInetAddress().getLocalHost().getHostAddress() +
					":" + portNumber);
			while (true) {
				Socket sock = server.accept();
				LOGGER.log(Level.INFO, "Connection accepted with " + sock.getRemoteSocketAddress());
				executor.execute(new RequestProcessor(sock, this));				
			}
			//server.close();
		} catch (BindException be) {
			LOGGER.log(Level.SEVERE, "Unable to start registry at port " + portNumber);
		}
		executor.shutdown();
	}
	

	/** 
	 * Place the specified in the list of know peers.  If this peer already exists, the 
	 * existing peer will be replaced ensuring that the list of peers send to this new/updated
	 * peer is maintained.
	 * @param p Peer object to add to the list of know peers.
	 */
	public void addPeer(Peer p) {
		Peer replaced = peers.put(p.key(), p);
		if (replaced != null) p.peersSent = replaced.peersSent;
	}
	
	/**
	 * Gets MAX_NUM_OF_PEERS sized selection of randomly chosen known peers.
	 * @return the list of randomly chosen peers.
	 */
	public Peer[] getRandomPeerList() {
		int numOfPeers = peers.size();
		Peer[] peerValues = peers.values().toArray(new Peer[numOfPeers]);
		// If total number of known peers is less than the maximum, add all known peers 
		// to the list. Otherwise, we will take the first MAX_NUM_OF_PEERS from the 
		// list after doing a random shuffle.
		if (numOfPeers > MAX_NUM_OF_PEERS) {
			// shuffle the list to help randomly choose peers to send
			Random rand = new Random();
			for (int index = peerValues.length - 2; index >= 0; index--) {
				int randomIndex = rand.nextInt(peerValues.length - index) + index;
				Peer newValue = peerValues[randomIndex];
				peerValues[randomIndex] = peerValues[index];
				peerValues[index] = newValue;
			}
			
			// copy required number of peers from randomly shuffled array
			Peer[] reduced = new Peer[MAX_NUM_OF_PEERS];
			for (int index = 0; index < MAX_NUM_OF_PEERS; index++) {
				reduced[index] = peerValues[index];
			}
			peerValues = reduced;
		}
		return peerValues;
	}
	
	/**
	 * Starts the registry server. If a port number is provided as a runtime argument, 
	 * it will be used to start the registry.
	 * Otherwise, the port number provided as an argument will be used. 
	 * <p>
	 * If we can't start the registry server, the stack trace for the exception will
	 * be printed and the program ended.
	 * 
	 * @param args optional port number as a first argument.
	 */
	public static void main(String[] args)  {
		int portNumber = DEFAULT_PORT_NUMBER;
		if (args.length > 0) {
			try {
				portNumber = Integer.parseInt(args[0]);
			} catch (NumberFormatException nfe) {
				System.out.println("Expected first argument to be a port number.  Argument ignored.");
			}
		}
		Registry r = new Registry(portNumber);
		Thread t = new Thread() { 
			public void run() {
				try {
					r.start();
				} catch (IOException e) {
					// Show that an error occurred with exception info
					e.printStackTrace();
					// end program with a error code
					System.exit(1);
				}
			} 
		};
		t.start();
	}
}
