package main.java;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Scanner;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Peer is a class that represents a peer process in a peer-to-peer distributed
 * messaging system.
 * The Peer object encapsulates various peer methods and peer specific data.
 * The Peer object requires an active central registry that follows an outlined
 * messaging protocol to function.
 * The Peer object assumes that every peer in the peer-to-peer distributed
 * messaging
 * system follows the outlined messaging protocol.
 * The Peer object runs various concurrent operations while interacting with
 * other peers.
 * The Peer object provides a user-interface for end-users to participate in the
 * peer-to-peer distributed messaging system by sending their own messages.
 * 
 * @author Rohan Amjad UCID: 30062188
 * @version 1.2
 * @since 1.0
 */
public class Peer {

    private ConcurrentHashMap<PeerLocation, Date> peers = new ConcurrentHashMap<PeerLocation, Date>();
    private ConcurrentHashMap<PeerLocation, Vector<PeerLocation>> sourcePeers = new ConcurrentHashMap<PeerLocation, Vector<PeerLocation>>();
    private ConcurrentHashMap<PeerLocation, Date> sources = new ConcurrentHashMap<PeerLocation, Date>();
    private Vector<String> peersSent = new Vector<String>();
    private Vector<String> peersReceived = new Vector<String>();
    private PriorityBlockingQueue<Snippet> snippetQueue = new PriorityBlockingQueue<Snippet>();
    private PriorityBlockingQueue<Snippet> snippetsInSystem = new PriorityBlockingQueue<Snippet>();

    private DatagramSocket udpSocket;
    private PeerLocation location;

    private boolean stop = false;
    private int timestamp = 0;

    public final String TEAMNAME = "Rohan Amjad 30062188";

    ExecutorService e = Executors.newFixedThreadPool(5);

    BufferedReader br;
    Scanner s;

    /**
     * Starts this Peer object by opening a UDP socket on which other peers in the
     * peer-to-peer distributed messaging system will communicate through, then
     * connects to the central registry to fetch an initial list of known peers, and
     * then starts the concurrent operations that this Peer object performs while in
     * the peer-to-peer distributed messaging system.
     * 
     * @param registryIP   the IP address of the central registry
     * @param registryPort the Port of the central registry
     */
    public void start(String registryIP, int registryPort) {
        startUDP();
        connectToRegistry(registryIP, registryPort);

        broadcastPeers(6);
        System.out.println("Broadcasting Peers...");

        e.execute(new MessageHandler(udpSocket, this));
        System.out.println("Handling UDP Messages...");

        displaySnippets();
        sendSnip();

        e.shutdown();
        try {
            while (!e.awaitTermination(5, TimeUnit.SECONDS))
                ;
        } catch (Exception e) {
            e.printStackTrace();
        }

        udpSocket.close();

        connectToRegistry(registryIP, registryPort);

        try {
            // s.close();
            br.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Opens a UDP socket on a randomly assigned Port on the current LAN.
     */
    private void startUDP() {
        try {
            udpSocket = new DatagramSocket();
            this.location = new PeerLocation(getPublicIPv4(), udpSocket.getLocalPort());
            System.out.println("UDP Server started at: " + this.location.getIP() + ":" + this.location.getPort() + " "
                    + getDateFormatted(getCurrentDate()));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Connects to the registry via a TCP connection and handles all messages with
     * the central registry.
     * 
     * @param registryIP   the IP address of the central registry
     * @param registryport the Port of the central registry
     */
    private void connectToRegistry(String registryIP, int registryPort) {
        Socket s;
        BufferedReader in;
        BufferedWriter out;

        try {
            // open new socket to the registry IP and port
            s = new Socket(registryIP, registryPort);

            // get input and output streams from socket
            in = new BufferedReader(new InputStreamReader(s.getInputStream()));
            out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream()));

            String request;

            // end flag
            boolean close = false;
            while (!close) {
                request = in.readLine();
                // switch to handle registry requests
                switch (request) {
                    case "get team name":
                        System.out.println(request);
                        sendTeamName(out);
                        break;
                    case "get code":
                        System.out.println(request);
                        sendCode(out);
                        break;
                    case "receive peers":
                        System.out.println(request);
                        getPeers(in, s);
                        break;
                    case "get report":
                        System.out.println(request);
                        sendReport(out);
                        break;
                    case "get location":
                        System.out.println(request);
                        sendLocation(out);
                        break;
                    case "close":
                        System.out.println(request);
                        close = true;
                        break;
                }
            }
            TimeUnit.MILLISECONDS.sleep(500);
            // close streams and socket
            in.close();
            out.close();
            s.close();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Asks the end-user for a team name and then sends that name to an output
     * stream.
     * 
     * @param out the BufferedWriter output stream
     */
    private void sendTeamName(BufferedWriter out) {
        try {
            // System.out.println("Enter your team name: ");
            // s = new Scanner(System.in);
            // while(!s.hasNextLine());
            // String teamName = s.nextLine();
            // String teamName = "Rohan Amjad 30062188";
            // this.teamName = teamName;
            String teamName = this.TEAMNAME + "\n";
            System.out.println("Sending team name: " + teamName);

            out.write(teamName);
            out.flush();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Sends the source code to an output stream.
     * 
     * @param out the BufferedWriter output stream
     */
    private void sendCode(BufferedWriter out) {
        File dir = new File("./main/java/");
        File[] files = dir.listFiles();

        try {
            StringBuilder sb = new StringBuilder();
            sb.append("java");

            for (File f : files) {
                if (f.getName().equals("Peer.java") || f.getName().equals("MessageHandler.java") ||
                        f.getName().equals("PeerLocation.java") || f.getName().equals("Snippet.java")) {
                    sb.append(readFile(f));
                }
            }

            // sb.append("\n");
            // sb.append("sample");

            sb.append("\n...\n");

            String code = sb.toString();

            System.out.println("Sending code...");

            // send code
            out.write(code);
            out.flush();

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * Generates and sends a report of this Peer object's interactions in the
     * peer-to-peer distributed messaging system to an output stream.
     * 
     * @param out the BufferedWriter output stream
     */
    private void sendReport(BufferedWriter out) {
        StringBuilder sb = new StringBuilder();

        // append number of peers
        sb.append(Integer.toString(peers.size()));
        sb.append("\n");

        // append peers
        for (PeerLocation key : peers.keySet()) {
            sb.append(key.getIP());
            sb.append(":");
            sb.append(key.getPort());
            sb.append("\n");
        }

        // append number of sources
        sb.append(Integer.toString(sources.size()));
        sb.append("\n");

        // append peers for each source
        for (PeerLocation key : sources.keySet()) {
            // append source
            sb.append(key.getIP());
            sb.append(":");
            sb.append(key.getPort());
            sb.append("\n");
            // append the date that peers were received from source
            sb.append(getDateFormatted(sources.get(key)));
            sb.append("\n");
            // append the number of peers received from source
            sb.append(sourcePeers.get(key).size());
            sb.append("\n");
            // append peers received from source
            for (PeerLocation p : sourcePeers.get(key)) {
                sb.append(p.getIP());
                sb.append(":");
                sb.append(p.getPort());
                sb.append("\n");
            }
        }

        // append number of peers received from udp
        sb.append(peersReceived.size());
        sb.append("\n");

        // append peers received from udp
        for (String s : peersReceived) {
            sb.append(s);
            sb.append("\n");
        }

        // append number of peers sent from udp
        sb.append(peersSent.size());
        sb.append("\n");

        // append peers sent from udp
        for (String s : peersSent) {
            sb.append(s);
            sb.append("\n");
        }

        // append number of snippets in the System
        sb.append(snippetsInSystem.size());
        sb.append("\n");

        // append snippets
        while (!snippetsInSystem.isEmpty()) {
            Snippet s = snippetsInSystem.poll();
            sb.append(s.getTimestamp());
            sb.append(" ");
            sb.append(s.getContent());
            sb.append(" ");
            sb.append(s.getSourcePeer().getIP());
            sb.append(":");
            sb.append(s.getSourcePeer().getPort());
            sb.append("\n");
        }

        // create report
        String report = sb.toString();

        System.out.println(report);

        // send report
        try {
            out.write(report);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Sends the location: IP and Port of this Peer object's UDP socket to an output stream.
     * 
     * @param out the BufferedWriter output stream
     */
    private void sendLocation(BufferedWriter out) {
        try {
            String udpLocation = this.location.getIP() + ":" + this.location.getPort() + "\n";
            System.out.println("Sending location: " + udpLocation);
            out.write(udpLocation);
            out.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Gets a list of peers from an input stream.
     * 
     * @param in the BufferedReader input stream
     * @param s  the socket from which peers are received
     */
    private void getPeers(BufferedReader in, Socket s) {
        // get the source IP and port
        int sourcePort = s.getPort();
        String sourceIP = s.getInetAddress().getHostAddress();
        PeerLocation source = new PeerLocation(sourceIP, sourcePort);

        try {
            // get the number of peers
            int numOfPeersFromSource = Integer.parseInt(in.readLine());

            Vector<PeerLocation> peersFromSource = new Vector<PeerLocation>();

            // add peers
            for (int i = 0; i < numOfPeersFromSource; i++) {
                String[] peerLocation = in.readLine().split(":");
                String peerIP = peerLocation[0];
                int peerPort = Integer.parseInt(peerLocation[1]);
                PeerLocation peer = new PeerLocation(peerIP, peerPort);
                peers.put(peer, getCurrentDate());
                peersFromSource.add(peer);
            }

            // add source
            sources.put(source, getCurrentDate());
            sourcePeers.put(source, peersFromSource);

            // print peers
            System.out.println("All Known Peers: ");
            for (PeerLocation key : peers.keySet()) {
                System.out.println(key.getIP() + ":" + key.getPort());
            }

            // print sources
            System.out.println("All Known Sources: ");
            for (PeerLocation key : sources.keySet()) {
                System.out.println(key.getIP() + ":" + key.getPort() + " " + sources.get(key));
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Adds a Peer to this Peer object's list of all known peers.
     * 
     * @param p      the PeerLocation the peer that is being added
     * @param packet the DatagramPacket that sent the Peer info
     */
    public void addPeer(PeerLocation p, DatagramPacket packet) {
        PeerLocation receivedFrom = new PeerLocation(packet.getAddress().getHostAddress(), packet.getPort());
        if (peers.containsKey(receivedFrom)) {
            peers.replace(receivedFrom, getCurrentDate());
        } else {
            peers.put(receivedFrom, getCurrentDate());
        }
        String received = packet.getAddress().getHostAddress() + ":" + packet.getPort() + " " +
                p.getIP() + ":" + p.getPort() + " " + getDateFormatted(getCurrentDate());
        peersReceived.add(received);
        if (peers.containsKey(p)) {
            return;
        } else {
            peers.put(p, getCurrentDate());
        }
        //System.out.println("Recv: " + received);
    }

    /**
     * Periodically broadcasts this Peer object's list of all known peers to all of its known peers.
     * 
     * @param seconds the number of seconds to wait between each broadcast
     */
    private void broadcastPeers(int seconds) {
        e.execute(new Runnable() {
            @Override
            public void run() {
                DatagramPacket packet;
                byte[] buf;
                String msg;

                while (!stop) {
                    // send peer i
                    for (PeerLocation i : peers.keySet()) {
                        if (!isAlive(peers.get(i), 10))
                            continue;
                        try {
                            msg = "peer" + i.getIP() + ":" + i.getPort();
                            buf = msg.getBytes();
                            // send peer i to all peers j
                            for (PeerLocation j : peers.keySet()) {
                                if (!isAlive(peers.get(j), 10))
                                    continue;
                                String sendToIP = j.getIP();
                                int sendToPort = j.getPort();
                                packet = new DatagramPacket(buf, buf.length, InetAddress.getByName(sendToIP),
                                        sendToPort);
                                udpSocket.send(packet);

                                String sent = sendToIP + ":" + sendToPort + " " + i.getIP() + ":" + i.getPort() + " "
                                        + getDateFormatted(getCurrentDate());
                                peersSent.add(sent);
                                //System.out.println("Sent: " + sent);
                            }
                            TimeUnit.SECONDS.sleep(seconds);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        });
    }

    /**
     * Sends a DatagramPacket containing a snippet message to this Peer object's UDP socket.
     */
    private void sendSnip() {
        e.execute(new Runnable() {
            @Override
            public void run() {
                DatagramPacket packet;
                byte[] buf;
                String msg;

                System.out.println("Send Message: ");
                br = new BufferedReader(new InputStreamReader(System.in));
                String content;
                while (!stop) {
                    try {
                        Thread.sleep(500);
                        if (!br.ready()) {
                            continue;
                        }
                        content = br.readLine();

                        timestamp += 1;

                        StringBuilder sb = new StringBuilder();
                        sb.append("snip");
                        sb.append(timestamp);
                        sb.append(" ");
                        sb.append(content);

                        msg = sb.toString();

                        buf = msg.getBytes();

                        for (PeerLocation i : peers.keySet()) {
                            if (!isAlive(peers.get(i), 10))
                                continue;
                            String sendToIP = i.getIP();
                            int sendToPort = i.getPort();
                            packet = new DatagramPacket(buf, buf.length, InetAddress.getByName(sendToIP), sendToPort);
                            udpSocket.send(packet);
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    /**
     * Adds a snippet message to this Peer object's snippet queue.
     * 
     * @param s the Snippet message being added
     */
    public void addSnippet(Snippet s) {
        timestamp = Math.max(timestamp, s.getTimestamp());
        if (peers.containsKey(s.getSourcePeer())) {
            peers.replace(s.getSourcePeer(), getCurrentDate());
        } else {
            peers.put(s.getSourcePeer(), getCurrentDate());
        }
        snippetQueue.add(s);
    }

    /**
     * Displays all snippet messages in this Peer object's snippet queue.
     */
    private void displaySnippets() {
        e.execute(new Runnable() {
            @Override
            public void run() {
                while (!stop) {
                    try {
                        Snippet s = snippetQueue.poll(5, TimeUnit.SECONDS);
                        if (s != null) {
                            snippetsInSystem.add(s);
                            System.out.println(s.getTimestamp() + " " + s.getContent() + " " + s.getSourcePeer().getIP()
                                    + ":" + s.getSourcePeer().getPort());
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    /**
     * Sets this Peer object's stop flag.
     */
    public void stop() {
        this.stop = true;
    }

    /**
     * Gets the status of this Peer object's stop flag.
     * 
     * @return <code>true</code> if this Peer object's stop flag is set
     */
    public boolean getStop() {
        return this.stop;
    }

    /**
     * Sets a new timestamp for this Peer object's timestamp.
     * 
     * @param t the new timestamp
     */
    public void setTimestamp(int t) {
        this.timestamp = t;
    }

    /**
     * Gets this Peer object's current timestamp.
     * 
     * @return the current timestamp
     */
    public int getTimestamp() {
        return this.timestamp;
    }

    /**
     * Reads the contents of a file.
     * 
     * @param f the File to read from
     * @return return a String containing the contents of the File
     */
    public static String readFile(File f) {
        BufferedReader br;
        String fileContents = "";

        try {
            // open FileReader stream to source code file
            br = new BufferedReader(new FileReader(f));

            StringBuilder sb = new StringBuilder();

            // read line by line from source code
            String line;
            while ((line = br.readLine()) != null) {
                // append each source code line
                sb.append("\n");
                sb.append(line);
            }

            fileContents = sb.toString();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return fileContents;
    }

    /**
     * Gets the current Date object.
     * 
     * @return a Date object containing the current date and time
     */
    public static Date getCurrentDate() {
        return Calendar.getInstance().getTime();
    }

    /**
     * Formats a Date object into a formatted date string.
     * 
     * @param d the Date object being formatted
     * @return a formatted String of the Date object
     */
    public static String getDateFormatted(Date d) {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String currDate = df.format(d);
        return currDate;
    }

    /**
     * Gets the public IPv4 address of this host machine by accessing <a>https://checkip.amazonaws.com</a>.
     * 
     * @return a String containing the public IPv4 address of this host machine
     */
    public static String getPublicIPv4() {
        URL whatIsMyIP;
        String IP = "unknown";
        BufferedReader in = null;
        try {
            whatIsMyIP = new URL("http://checkip.amazonaws.com");
            in = new BufferedReader(new InputStreamReader(whatIsMyIP.openStream()));
            IP = in.readLine();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return IP;
    }

    /**
     * Checks if the given date is not older than a number of seconds from the current date.
     * 
     * @param d       the Date object being compared with the current date
     * @param seconds the number of seconds 
     * @return <code>true</code> if the Date object is not older than a number of seconds from the current date
     */
    public static boolean isAlive(Date d, int seconds) {
        Date current = Calendar.getInstance().getTime();
        current.setTime(current.getTime() - (seconds * 1000));
        if (d.before(current)) {
            return false;
        }
        return true;
    }

    public static void main(String[] args) {
        // check if registry host IP and port are provided in command line args
        if (args.length == 0) {
            System.err.println("Usage: Host Port");
            System.exit(0);
        }

        // get registry host IP and port from command line args
        String registryIP = args[0];
        int registryPort = Integer.parseInt(args[1]);

        // new Peer
        Peer p = new Peer();

        Thread t = new Thread() {
            public void run() {
                p.start(registryIP, registryPort);
            }
        };
        t.start();

    }
}
