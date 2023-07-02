import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.Date;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Process a connection request from a peer in our system.  A request is processed by
 * requesting the team name, the source code produced by the team and by sending a 
 * (partial) list of peers in the system.  At the end of the communication, we'll ask 
 * for a report on all communication for grading purposes.
 * <p>
 * This version of the Registry will make requests in the following order:
 * 'get team name'<br>
 * 'get code'<br>
 * 'receive peers'<br>
 * 'get report'<br>
 * 'close'<br>
 * It is the best version to test your code since the order of operations is predictable. A peer 
 * should be able to handle these requests in any order however: the version we'll use for testing
 * can send requests in any order and can duplicate requests.  The only guarantee is that the 'close'
 * request will be the last request.
 * <p>
 * Information of all peer processes that connect with this registry will be stored.  The
 * registry should be restarted to clear the peer list on a regular bases.  In future the peer list
 * will be maintained based on aliveness of the peer.
 * <p>
 * The class logs all communication for verification purposes, including any errors in
 * communication.
 * 
 * @author Nathaly Verwaal
 *
 */
class RequestProcessor implements Runnable {
	private final static Logger LOGGER = Logger.getLogger(RequestProcessor.class.getName());

	/** The connection that we are processing */
	private Socket peerSocket = null;
	
	/** The peer object we are creating for this connection */
	private Peer peer = null;
	
	/** The registry that received the connection request: it handles the list of peers */
	private Registry source = null;
	
	/**
	 * Setup up the logger to store logging messages in the file 'registryLogger.log' in the
	 * current directory.
	 */
	static {
		try {
			FileHandler fh = new FileHandler("RegistryLogger.log", true);
			fh.setFormatter(new SimpleFormatter());
			LOGGER.addHandler(fh);
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "Failed to setup logging file.  Logging to console instead.", e);
		}		
	}
	
	/**
	 * Constructor that sets up information needed to handle the connection request.
	 * @param aPeer the socket connection with the peer whose request we need to process.
	 * @param aSource the registry that received the connection request from the peer.
	 */
	RequestProcessor(Socket aPeer, Registry aSource) {
		assert(aPeer != null);
		peerSocket = aPeer;
		source = aSource;
	}
	
	/**
	 * Handles the communication with the peer that we are connected with.  It will first
	 * ask the name of the team that created this peer, store the information for this peer
	 * process, send a list of know peers, request a report from the peer then close the
	 * connection with the peer.
	 */
	@Override
	public void run() {
		assert(peerSocket != null);
		
		try {
			log(Level.INFO, "start");
			Peer addedPeer = addPeer();
			sendPeers();
			getReport(addedPeer);
			closePeer();
		} catch (IOException e) {
			log(Level.WARNING, "Problem processing socket.", e);
		}
	}
	
	/**
	 * We are done with this connection.  Warn other end that we are going to close the connection.
	 * <p>
	 * Communication protocol used:
	 * send 'close\n' along the channel.
	 * then close the channel
	 * 
	 * @throws IOException if the connection with the peer is broken unexpectedly or we are unable to 
	 * send a close command to the peer.
	 */
	protected void closePeer() throws IOException {
		assert(peerSocket != null);
		BufferedWriter out = new BufferedWriter(new OutputStreamWriter(peerSocket.getOutputStream()));
		out.write("close\n");
		out.flush();
		peerSocket.close();
	}

	/*
	 * Sends a list of peers in the system along the socket.  If the current peer object
	 * that we created for this channel already contains such a list of peers, we'll send this 
	 * list.  If it doesn't contain a list yet, we'll request the source Registry object
	 * for a random list of peers to send.
	 * <p>
	 * Communication protocol used:
	 * send 'receive peers\n'
	 * send <number of peers><newline> as a string
	 * for each peer: send <team name>,<ip address>,<port><newline>
	 * 
	 * @return the list of peers that were send along this channel.
	 * @throws IOException if the channel is unexpected closed or we are unable to send data
	 * along the channel.
	 */
	protected void sendPeers() throws IOException {
		log(Level.INFO, "sending");
		
		// Setup wrapper to aid writing to the channel
		BufferedWriter out = new BufferedWriter(new OutputStreamWriter(peerSocket.getOutputStream()));
		
		// let peer know that we are about to send peers and they should get ready to receive
		out.write("receive peers\n");
		out.flush();

		// Create the list of peers to send
		Peer[] peersToSend;
		if (peer == null || peer.peersSent == null || peer.peersSent.length < Registry.MAX_NUM_OF_PEERS) {
			// create a new list to send along the channel since none was set up for this peer yet.
			peersToSend = source.getRandomPeerList();
			if (peer != null) {
				peer.peersSent = peersToSend;
			}
		} else {
			// use the list of peers we already created for this peer
			peersToSend = peer.peersSent;
		}

		// Start record of communication
		StringBuffer logMessage = new StringBuffer();
		logMessage.append("sent ");
		
		// Indicate how many peers we'll send along the channel
		int length = peersToSend.length;
		logMessage.append(length);
		logMessage.append(" ");
		out.write("" + length + "\n");
		
		// Send each peer along the channel in the format <ip>:<port><newline>
		for (Peer p : peersToSend) {
			String toSend = p.address + ":" + p.port;
			out.write(toSend + "\n");
			logMessage.append(toSend + " ");
		}
		out.flush();
		log(Level.INFO, logMessage.toString());
	}
	
	/*
	 * Get the name of the team that created the code for this peer.  Communication protocol:
	 * 
	 * send: 'get team name\n'
	 * receive: <name of team><new line>
	 */
	private String getTeamName(BufferedWriter out, BufferedReader in) throws IOException {
		out.write("get team name\n");
		out.flush();
		String teamName = in.readLine();
		log(Level.INFO, "teamName " + teamName);
		return teamName;
	}
	
	/*
	 * Request the source code that the peer we are connected with is running.  Communication
	 * protocol:
	 * send 'get code\n'
	 * receive <programming language of source code><new line>
	 * receive <source code><new line>
	 * receive '...'
	 * 
	 * @return The date the code was received, the language the code was written in and the
	 * sequence of characters received peer process when we requested the code, 
	 * except for the '...' that indicates all code was sent.
	 */
	private String getCode(BufferedWriter out, BufferedReader in) throws IOException {
		// buffer to store source code
		StringBuffer buf = new StringBuffer();

		// Let peer know we want source code
		out.write("get code\n");
		out.flush();
		
		// read the name of the language the source code was written in
		String language = in.readLine();
		
		// store the date we are receiving the source code followed by the language 
		buf.append((new Date()).toString());
		buf.append("\n");
		buf.append(language + "\n");

		// read the source code one line at a time and place it in 'buf'
		String line = in.readLine();
		while (!line.equals("...")) {
			buf.append(line);
			buf.append("\n");
			line = in.readLine();
		}
		
		// return the data we recieved related to the source code for the peer
		return buf.toString();
	}
	
	/*
	 * Store the source code of the peer that we are currently connected with in a file.
	 * The file will be placed in the directory 'sourceCode' with file name 
	 * <team_name>SourceCode.txt
	 * 
	 * At the top of the file is the date the source code was received and the
	 * language used.  The name of the file created contains the team name followed by
	 * 'SourceCode.txt'.
	 */
	private void createCodeFile(String teamName, String code) throws IOException {
		String codeDirectory = "sourceCode";
		
		// Create directory for storing source code if it does not exist
		File dir = new File(codeDirectory);
		if (!dir.exists() || !dir.isDirectory()) {
			dir.mkdir();
		}
		
		// Create the file
		FileWriter codeFile = new FileWriter(codeDirectory + "/" + teamName + "SourceCode.txt", true);
		codeFile.write(code);
		codeFile.close();
		
		// log that we created the code file.
		log(Level.INFO, "code " + codeDirectory);
	}

	/**
	 * Create a peer object for the current connection.  Request the information from the peer
	 * along the channel with the peer and store this information.  Information requested: 
	 * team name, and the code that is used to run the peer.
	 * 
	 * @return the peer object created based on the info received from the peer along the channel.
	 * @throws IOException if the connection with the peer is unexpectedly closed or if
	 * we were unable to read or write along the channel.
	 */
	protected Peer addPeer() throws IOException {
		BufferedWriter out = new BufferedWriter(new OutputStreamWriter(peerSocket.getOutputStream()));
		BufferedReader in = new BufferedReader(new InputStreamReader(peerSocket.getInputStream()));
		
		String teamName = getTeamName(out, in);
		
		Peer peerToAdd = new Peer();
		peerToAdd.address = peerSocket.getInetAddress().getHostAddress();
		peerToAdd.port = peerSocket.getPort();
		peerToAdd.teamName = teamName;
		source.addPeer(peerToAdd);

		String code = getCode(out, in);
		createCodeFile(teamName, code);

		log(Level.INFO, "added " + peerToAdd.toString());
		
		return peerToAdd;
	}
	
	/**
	 * Ask the peer for a report of it's actions.  This should include the peers it has stored in 
	 * it's list of peers and a report of communication.  For this iteration, the only
	 * communication is with this Registry object.
	 * <p>
	 * The report for this team will be stored in the directory 'reports' in the file 
	 * <team name>Report.txt
	 * 
	 * @throws IOException if there is any problems communicating with the peer for this report request.
	 */
	protected void getReport(Peer addedPeer) throws IOException {
		BufferedWriter out = new BufferedWriter(new OutputStreamWriter(peerSocket.getOutputStream()));
		BufferedReader in = new BufferedReader(new InputStreamReader(peerSocket.getInputStream()));

		// request the report
		out.write("get report\n");
		out.flush();

		String reportDirectory = "reports";

		// create directory for storing reports if it doesn't exist
		File dir = new File(reportDirectory);
		if (!dir.exists() || !dir.isDirectory()) {
			dir.mkdir();
		}
		
		// read report send by peer and store in file
		String report = readReport(in);
		String reportFilename = reportDirectory + "/" + addedPeer.teamName + "Report.txt";
		FileWriter reportFile = new FileWriter(reportFilename, true);
		reportFile.write(report);
		reportFile.close();
		
		// log that we have requested and stored the report
		log(Level.INFO, "report " + reportFilename);
		
	}
	
	/**
	 * Read the report send by the peer.  The format of the report is expected to be as
	 * follows.  If this is incorrect, the report will likely not be recorded.
	 * <num of peers (n)><newline>
	 * <ip_peer1>:<port_peer1><newline>
	 * <ip_peer2>:<port_peer2><newline>
	 * ...
	 * <ip_peer_n>:<port_peer_n><newline>
	 * <num of sources (m)><newline>
	 * <ip_source_1>:<port_source_1><newline>
	 * <date that data was received from source 1><newline>
	 * <num of peers from source 1 (n1)><newline>
	 * <ip_peer1_source1>:<port_peer1_source1><newline>
	 * ...
	 * <ip_peer_n1_sourc1><port_peer_n1_source1><newline>
	 * <ip_source_2>:<port_source2><newline>
	 * ...
	 * <ip_source_m>:<port_source_m><newline>
	 * <date that data was received from source m><newline>
	 * <num of peers from source m (nm)><newline>
	 * <ip_peer1_source_m>:<port_peer1_source_m><newline>
	 * <ip_peer2_source_m>:<port_peer2_source_m><newline>
	 * ...
	 * <ip_peer_nm_source_m>:<port_peer_nm_source_m><newline>
	 *
	 * All data is expected to be textbase.  (So numbers should be send as a string, not a number.)
	 * 
	 * This information will be returned as a string in the following format:
	 * <current date/time><newline>
	 * <num of peers> in list: <ip_peer1>:<port_peer1> <ip_peer2>:<port_peer2> ... <ip_peer_n>:<port_peer_n><newline>
	 * <num of sources> sources:<newline>
	 * Source: <ip><port><newline>
	 * Date: <date><newline>
	 * Num of peers received: <numOfPeers><newline>
	 * <ip_peer1>:<port_peer1> <ip_peer2>:<port_peer2> ... <ip_peer_k>:<port_peer_k><newline>
	 * 
	 * The last four lines are repeated for each source.
	 * 
	 * @param in channel to read from
	 * @return the sequence of characters received from the peer
	 * @throws IOException if there were any problems while receive input from the peer
	 */
	private String readReport(BufferedReader in) throws IOException {
		// setup a buffer to store the characters received in the format we
		// want and place the date of the report at the top
		StringBuffer buf = new StringBuffer();
		buf.append((new Date()).toString());
		buf.append("\n");

		// get number of peers that our peer has stored.
		int numOfPeers = Integer.parseInt(in.readLine());
		buf.append(numOfPeers);
		buf.append(" in list: ");
		
		// get the peer list
		for (int counter = 0; counter < numOfPeers; counter++) {
			buf.append(in.readLine());
			buf.append(' ');
		}
		buf.append("\n");
		
		// get the information related to the sources for this peer list
		int numOfSources = Integer.parseInt(in.readLine());
		buf.append(numOfSources);
		buf.append(" sources:\n");
		for (int counter = 0; counter < numOfSources; counter++) {
			buf.append("Source: ");
			buf.append(in.readLine());
			buf.append("\n");
			buf.append("Date: " );
			buf.append(in.readLine());
			buf.append("\n");
			buf.append("Num of peers received: ");
			numOfPeers = Integer.parseInt(in.readLine());
			buf.append(numOfPeers);
			buf.append("\n");
			for (int pcounter = 0; pcounter < numOfPeers; pcounter++) {
				buf.append(in.readLine());
				buf.append(' ');
			}
			buf.append("\n");
		}
		
		return buf.toString();
	}
	
	/**
	 * Format logging message to include peer information
	 * @param level severity level of log message
	 * @param message data for log message
	 */
	protected void log(Level level, String message) {
		String logMessage = ((peerSocket != null) ? 
								peerSocket.getRemoteSocketAddress().toString() + " " : "") +
							message;
		LOGGER.log(level, logMessage);
	}

	/**
	 * Format logging message that resulted from exception to include peer information
	 * @param level severity level of log message
	 * @param message data for log message
	 * @param e exception that triggered this log message
	 */
	protected void log(Level level, String message, Exception e) {
		String logMessage = ((peerSocket != null) ? 
								peerSocket.getRemoteSocketAddress().toString() : "") +
							message;
		LOGGER.log(level, logMessage, e);
	}
}