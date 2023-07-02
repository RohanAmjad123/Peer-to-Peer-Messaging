package registry;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
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
 * Process a connection request from a peer in our system.  The registry that this request processor is 
 * created for could be in 'done' mode: this means we are trying to wrap up and close all processes in the system.
 * When we are in 'done' mode, we want a report from the process we are connected with.  Otherwise,
 * we'll connect with the peer to include this peer in the registry and provide it with a start list of
 * known peers in the system.
 * <p>
 * A request is processed by
 * requesting the team name, the source code produced by the team, the location where the peer will
 * receive and send UDP messages and by sending a 
 * (partial) list of peers in the system.  
 * <p>
 * At the end of the communication, when we are in 'done' mode, we'll ask 
 * for a report on all communication for grading purposes.
 * <p>
 * This version of the Registry will make requests in the following order:
 * 'get team name'<br>
 * 'get location'<br>
 * 'get code'<br>
 * 'receive peers'<br>
 * If we are in done mode, instead of 'receive peers', we'll request 'get report' instead.
 * <p>
 * In both case, the communication is ended with a 'close' request. 
 * <p>
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
	private static int runCounter = 1;

	/** The connection that we are processing */
	private Socket peerSocket = null;
	private BufferedWriter out;
	private BufferedReader in;
	
	private String peerLanguageExtension = "txt";
	
	/** The peer object we are creating for this connection */
	private Peer peer = null;
	
	/** The registry that received the connection request: it handles the list of peers */
	private Registry source = null;
	
	private String codeDirectory = "sourceCode" + runCounter;
	private String reportDirectory = "reports" + runCounter;
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
	 * @throws IOException 
	 */
	RequestProcessor(Socket aPeer, Registry aSource) throws IOException {
		assert(aPeer != null);
		peerSocket = aPeer;
		source = aSource;
		out = new BufferedWriter(new OutputStreamWriter(peerSocket.getOutputStream()));
		in = new BufferedReader(new InputStreamReader(peerSocket.getInputStream()));
		
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
			/* ------------- Updated --------------*/ 
			if (!source.done) {
				// We are in the usual running mode.  On all connection requests send list of peers.
				sendPeers();
			} else {
				// We are ending this simulation: get a report from all processes.
				getReport(addedPeer);
			}
			/* ------------- End updated code -----*/
			closePeer(addedPeer);
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
	private void closePeer(Peer addedPeer) throws IOException {
		assert(peerSocket != null);
		out.write("close\n");
		sendFileContent(codeDirectory + "/" + addedPeer.teamName + "SourceCode." + peerLanguageExtension);
		sendFileContent(reportDirectory + "/" + addedPeer.teamName + "Report.txt");
		out.flush();
		peerSocket.close();
	}

	/**
	 * Send content of text file along output channel.  To indicate the end of the file, the stream will
	 * be ended with <newline>...<newline>
	 * @param filename name of the text file to send along the output stream
	 */
	private void sendFileContent(String filename) {
		BufferedReader reader = null;
		try {
			try {
				reader = new BufferedReader(new FileReader(filename));
				String line = reader.readLine();
				while (line != null) {
					out.write(line + "\n");
					line = reader.readLine();
				}
			} catch (FileNotFoundException e) {
				out.write("file not found: " + filename);
			}
			out.write("\n...\n");
			if (reader != null) reader.close();		
		} catch (IOException ioe) {
			// do nothing if there was an error.  Close the file and return
			try {
				if (reader != null) reader.close();
			} catch (IOException e) {
				// can't close the file, nothing more to do.
				e.printStackTrace();
			}
		}
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
	private void sendPeers() throws IOException {
		log(Level.INFO, "sending");
		
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
			String toSend = p.address + ":" + p.getPort();
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
		System.out.println("Sending get team name request");
		out.write("get team name\n");
		out.flush();
		System.out.println("Waiting for team name");
		String teamName = in.readLine();
		System.out.println("Got team name");
		log(Level.INFO, "teamName " + teamName);
		return teamName;
	}
	
	/* ----------------------- Updated code (added method) -----------------*/
	/**
	 * Request location where peer can be contacted using UDP/IP protocol.  (Location where peer-to-peer
	 * connections can be made.) Communication protocol:
	 * 
	 * send: 'get location\n'
	 * receive: <ip address><colon><port><new line>
	 * 
	 * @return the location we received from the peer
	 * @throws IOException if there are any problems will communicating along the channel
	 */
	private String getLocation() throws IOException {
		System.out.println("Sending get location request");
		out.write("get location\n");
		out.flush();
		System.out.println("Waiting for location");
		String location = in.readLine();
		System.out.println("Got location");
		log(Level.INFO, "location " + location);
		return location;
	}
	
	/* ---------------------- End of updated code -------------------------*/
	
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
		peerLanguageExtension = in.readLine().toLowerCase();
		
		// store the date we are receiving the source code followed by the language 
		buf.append((new Date()).toString());
		buf.append("\n");
		buf.append(peerLanguageExtension + "\n");

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
		
		// Create directory for storing source code if it does not exist
		File dir = new File(codeDirectory);
		if (!dir.exists() || !dir.isDirectory()) {
			dir.mkdir();
		}
		
		// Create the file
		FileWriter codeFile = new FileWriter(codeDirectory + "/" + teamName + "SourceCode." + peerLanguageExtension);
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
	private Peer addPeer() throws IOException {
		
		String teamName = getTeamName(out, in);
		
		Peer peerToAdd = new Peer();
		
		/* ----------------------- updated code -------------------*/
		String location = getLocation();
		/* ----------------------- end of updated code ------------*/
		
		String[] addressInfo = location.split(":");
		if (addressInfo.length >=2) {
			peerToAdd.address = addressInfo[0];
			peerToAdd.setPort(Integer.parseInt(addressInfo[1]));
		}
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
	void getReport(Peer addedPeer) throws IOException {
		// request the report
		out.write("get report\n");
		out.flush();

		// create directory for storing reports if it doesn't exist
		File dir = new File(reportDirectory);
		if (!dir.exists() || !dir.isDirectory()) {
			dir.mkdir();
		}
		
		// read report send by peer and store in file
		String reportFilename = reportDirectory + "/" + addedPeer.teamName + "Report.txt";
		FileWriter reportFile = new FileWriter(reportFilename, true);
		readPeerList(in, reportFile);
		readListSources(in, reportFile);
		
		readSingleSources(in, reportFile);
		readSends(in, reportFile);
		readSnippets(in, reportFile);
		/* ------------------------------ Updated code ---------------------*/
		// Report will contain additional info related to snippet acks
		System.out.println("About to read acks from report");
		readAcks(in, reportFile);
		/*------------------------------- end of updated code --------------*/
		
		reportFile.close();
		
		// log that we have requested and stored the report
		log(Level.INFO, "report " + reportFilename);
		
	}
	
	/**
	 * Read the part of the report that contains the list of peers stored by the peer we're connected with.
	 * The format of this part of the report is:
	 * <num of peers (n)><newline>
	 * <ip_peer1>:<port_peer1><newline>
	 * <ip_peer2>:<port_peer2><newline>
	 * ...
	 * <ip_peer_n>:<port_peer_n><newline>
	 * 
	 * All data is expected to be textbase.  (So numbers should be send as a string, not a number.)
	 * 
	 * This information will be put in the filestream in the following format:
	 * <current date/time><newline>
	 * <num of peers> in list: <ip_peer1>:<port_peer1> <ip_peer2>:<port_peer2> ... <ip_peer_n>:<port_peer_n><newline>

	 * @param in stream that is the source of the information for the report
	 * @param out the place to store the information found in the report
	 * @throws IOException if there is a problem reading from the input stream, including incorrect formatting of the report.
	 */	
	private void readPeerList(BufferedReader in, FileWriter out) throws IOException {
		// setup a buffer to store the characters received in the format we
		// want and place the date of the report at the top
		out.write((new Date()).toString());
		out.write("\n");

		// get number of peers that our peer has stored.
		int numOfPeers = Integer.parseInt(in.readLine());
		out.write(numOfPeers + " in list: ");
		
		// get the peer list
		for (int counter = 0; counter < numOfPeers; counter++) {
			out.write(in.readLine());
			out.write('\n');
		}
//		out.write('\n');
	}

	/**
	 * Read the part of the report that contains all receipts of lists of peers.  (For this iteration there should
	 * only be one: the list we (the registry) send to this peer at start-up.)
	 * The format of this part of the report is:
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
	 * This information will be put in the filestream in the following format:
	 *	
	 * <num of sources> sources:<newline>
	 * Source: <ip><port><newline>
	 * Date: <date><newline>
	 * Num of peers received: <numOfPeers><newline>
	 * <ip_peer1>:<port_peer1> <ip_peer2>:<port_peer2> ... <ip_peer_k>:<port_peer_k><newline>
	 * 
	 * The last four lines are repeated for each source.
	 * 
	 * <current date/time><newline>
	 * <num of peers> in list: <ip_peer1>:<port_peer1> <ip_peer2>:<port_peer2> ... <ip_peer_n>:<port_peer_n><newline>
	 * 
	 * @param in stream that is the source of the information for the report
	 * @param out the place to store the information found in the report
	 * @throws IOException if there is a problem reading from the input stream, including incorrect formatting of the report.
	 */
	private void readListSources(BufferedReader in, FileWriter out) throws IOException {
		int numOfSources = Integer.parseInt(in.readLine());
		out.write(numOfSources + " sources:\n");
		for (int counter = 0; counter < numOfSources; counter++) {
			out.write("Source: ");
			out.write(in.readLine());
			out.write("\n");
			out.write("Date: " );
			out.write(in.readLine());
			out.write("\n");
			int numOfPeers = Integer.parseInt(in.readLine());
			out.write("Num of peers received: " + numOfPeers);
			out.write("\n");
			for (int pcounter = 0; pcounter < numOfPeers; pcounter++) {
				out.write(in.readLine());
				out.write(' ');
			}
			out.write("\n");
		}
	}

	
	/**
	 * One part of the communication between peers is peer list management.  This is done periodically sending
	 * a UDP message to all peers in the list.  The UDP message contains a information about a random peer.
	 * 
	 * Read the part of the report that contains the sequence of peers that was received by the reporting peer,
	 * who they were received from and which peer was send.
	 * The format of this part of the report is:
	 * <num of single peer received (n)><newline>
	 * <ip_source1>:<port_source1><space><ip_peer_in_message>:<port_peer_in_message><space><date received><newline>
	 * <ip_source2>:<port_source2><space><ip_peer_in_message>:<port_peer_in_message><space><date received><newline>
	 * ...
	 * <ip_source_n>:<port_source_n><space><ip_peer_in_message>:<port_peer_in_message><space><date received><newline>
	 * 
	 * All data is expected to be textbased.  (So numbers should be send as a string, not a number.)
	 * 
	 * This information will be put in the filestream in the following format:
	 * <num of single peer received (n)><newline>
	 * <ip_source1>:<port_source1><space><ip_peer_in_message>:<port_peer_in_message><space><date received><newline>
	 * <ip_source2>:<port_source2><space><ip_peer_in_message>:<port_peer_in_message><space><date received><newline>
	 * ...
	 * <ip_source_n>:<port_source_n><space><ip_peer_in_message>:<port_peer_in_message><space><date received><newline>

	 * @param in stream that is the source of the information for the report
	 * @param out the place to store the information found in the report
	 * @throws IOException if there is a problem reading from the input stream, including incorrect formatting of the report.
	 */	
	private void readSingleSources(BufferedReader in, FileWriter out) throws IOException {
		int numOfSources = Integer.parseInt(in.readLine());
		out.write(numOfSources + " single peers received:\n");
		for (int counter = 0; counter < numOfSources; counter++) {
			out.write(in.readLine());
			out.write('\n');
		}
	}

	/**
	 * One part of the communication between peers is peer list management.  This is done periodically sending
	 * a UDP message to all peers in the list.  The UDP message contains a information about a random peer.
	 * 
	 * Read the part of the report that contains the sequence of peers that were send by the reporting peer,
	 * who they were send to and which peer was send.
	 * The format of this part of the report is:
	 * <num of single peer sends (n)><newline>
	 * <ip_peer1>:<port_peer1><space><ip_random_peer>:<port_random_peer><space><date send><newline>
	 * <ip_peer2>:<port_peer2><space><ip_random_peer>:<port_random_peer><space><date send><newline>
	 * ...
	 * <ip_peer_n>:<port_peer_n><space><ip_random_peer>:<port_random_peer><space><date send><newline>
	 * 
	 * All data is expected to be textbased.  (So numbers should be send as a string, not a number.)
	 * 
	 * This information will be put in the filestream in the following format:
	 * <num of sends> sends:<newline>
	 * <ip_peer1>:<port_peer1><space><ip_random_peer>:<port_random_peer><space><date send><newline>
	 * <ip_peer2>:<port_peer2><space><ip_random_peer>:<port_random_peer><space><date send><newline>
	 * ...
	 * <ip_peer_n>:<port_peer_n><space><ip_random_peer>:<port_random_peer><space><date send><newline>

	 * @param in stream that is the source of the information for the report
	 * @param out the place to store the information found in the report
	 * @throws IOException if there is a problem reading from the input stream, including incorrect formatting of the report.
	 */	
	private void readSends(BufferedReader in, FileWriter out) throws IOException {
		int numOfSends = Integer.parseInt(in.readLine());
		out.write(numOfSends + " sends:\n");
		for (int counter = 0; counter < numOfSends; counter++) {
			out.write(in.readLine());
			out.write("\n");
		}
	}
	
	/**
	 * The purpose of the application is to provide an on-line message board where posts can be initiated by
	 * any peer and all peers receive all posts.  However, communication between peers is using UDP/IP, which
	 * is not reliable.  In this part of the report, the peer gives the list of posts it is aware of in
	 * Lamport's timestamp order.
	 * 
	 * The format of this part of the report is:
	 * <num of snippets (n)><newline>
	 * <timestamp1><space><snippet_content_1><space><ip_source_peer_1>:<port_source_peer_1><newline>
	 * <timestamp2><space><snippet_content_2><space><ip_source_peer_2>:<port_source_peer_2><newline>
	 * ...
	 * <timestampn><space><snippet_content_n><space><ip_source_peer_n>:<port_source_peer_n><newline>
	 * 
	 * All data is expected to be textbased.  (So numbers should be send as a string, not a number.)
	 * 
	 * This information will be put in the filestream unchanged

	 * @param in stream that is the source of the information for the report
	 * @param out the place to store the information found in the report
	 * @throws IOException if there is a problem reading from the input stream, including incorrect formatting of the report.
	 */	
	private void readSnippets(BufferedReader in, FileWriter out) throws IOException {
		int numOfSnippets = Integer.parseInt(in.readLine());
		out.write(numOfSnippets + " snippets:\n");
		for (int counter = 0; counter < numOfSnippets; counter++) {
			out.write(in.readLine());
			out.write("\n");
		}
	}
	
	/* ---------------------------------- Start updated code ------------------------------------*/

	private void readAcks(BufferedReader in, FileWriter out) throws IOException {
		int numOfAcks = Integer.parseInt(in.readLine());
		System.out.println("reading acks: " + numOfAcks);
		out.write(numOfAcks + " snippet acks received:\n");
		for (int counter = 0; counter < numOfAcks; counter++) {
			out.write(in.readLine());
			out.write("\n");
		}
	}
	
	/*------------------------------- end of updated code -------------------------*/
	
	/**
	 * Format logging message to include peer information
	 * @param level severity level of log message
	 * @param message data for log message
	 */
	private void log(Level level, String message) {
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
	private void log(Level level, String message, Exception e) {
		String logMessage = ((peerSocket != null) ? 
								peerSocket.getRemoteSocketAddress().toString() : "") +
							message;
		LOGGER.log(level, logMessage, e);
	}
	
	public static void nextRun() {
		LOGGER.log(Level.INFO, "------- RESTARTED SERVER --------------");
		runCounter++;
	}

}