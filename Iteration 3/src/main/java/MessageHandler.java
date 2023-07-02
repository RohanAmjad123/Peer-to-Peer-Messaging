package main.java;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * MessageHandler is a class that handles all UDP messages for a Peer object.
 * The MessageHandler class implements the Runnable interface.
 * The MessageHandler object handles messages outlined by a specifc protocol and
 * then calls the appropriate methods in the Peer object.
 * 
 * @author Rohan Amjad UCID: 30062188
 * @version 1.0
 * @since 1.0
 */
public class MessageHandler implements Runnable {
    private DatagramSocket udpSocket;
    private Peer p;
    private byte[] buf;
    private DatagramPacket packet;
    private String msg;

    /**
     * Class constructor that specifies the UDP socket to receive messages from and
     * the Peer object that this MessageHandler is handling messages for.
     * 
     * @param udpSocket the DatagramSocket that this MessageHandler will listen
     *                  from
     * @param p         the Peer object that this MessageHandler is handling
     *                  messages for
     */
    MessageHandler(DatagramSocket udpSocket, Peer p) {
        this.udpSocket = udpSocket;
        this.p = p;
    }

    /**
     * Listens for messages received on this MessageHanlder object's UDP socket and
     * handles them accordingly.
     */
    @Override
    public void run() {

        while (!p.getStop()) {
            buf = new byte[1024];
            msg = "";
            packet = new DatagramPacket(buf, buf.length);

            try {
                this.udpSocket.receive(packet);
                msg = new String(packet.getData(), 0, packet.getLength());
            } catch (Exception e) {
                e.printStackTrace();
            }

            String msgType = processMessage(msg)[0];
            String msgContent = processMessage(msg)[1];
            switch (msgType) {
                case "peer":
                    String peerIP = msgContent.split(":")[0];
                    int peerPort = Integer.parseInt(msgContent.split(":")[1]);
                    p.addPeer(new PeerLocation(peerIP, peerPort), packet);
                    break;
                case "snip":
                    try {
                        Snippet s = processSnippet(msgContent, packet);
                        p.addSnippet(s);
                    } catch (Exception e) {
                        e.printStackTrace();
                        break;
                    }
                    break;
                case "stop":
                    System.out.println("Received stop");
                    try {
                        this.udpSocket.setSoTimeout(25000);
                    } catch (Exception e) {
                        e.printStackTrace();
                        break;
                    }
                    InetAddress senderIP = packet.getAddress();
                    int senderPort = packet.getPort();
                    String ack = "ack" + p.TEAMNAME;
                    buf = ack.getBytes();
                    packet = new DatagramPacket(buf, buf.length, senderIP, senderPort);
                    try {
                        this.udpSocket.send(packet);
                        System.out.println(
                                "Sending stop ack " + ack + " to " + senderIP.getHostAddress() + ":" + senderPort);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    p.stop();
                    System.out.println("Stopping UDP...");
                    while (true) {
                        try {
                            packet = new DatagramPacket(buf, buf.length);
                            this.udpSocket.receive(packet);
                            msg = new String(packet.getData(), 0, packet.getLength());
                            if (!processMessage(msg)[0].equals("stop"))
                                continue;
                            System.out.println("Received stop");
                            senderIP = packet.getAddress();
                            senderPort = packet.getPort();
                            packet = new DatagramPacket(buf, buf.length, senderIP, senderPort);
                            System.out.println(
                                    "Sending stop ack " + ack + " to " + senderIP.getHostAddress() + ":" + senderPort);
                            this.udpSocket.send(packet);
                        } catch (Exception e) {
                            e.printStackTrace();
                            break;
                        }
                    }
                    break;
            }
        }
    }

    /**
     * Processes a message received from this MessageHandler object's UDP socket.
     * 
     * @param msg the message that was received from the UDP socket
     * @return a String array containing the different parts of the processed
     *         message
     */
    public static String[] processMessage(String msg) {
        String[] processedMessage;
        try {
            msg = msg.trim();
            String msgType = msg.substring(0, 4).toLowerCase();
            String msgContent = msg.substring(4).trim();

            if (msgType.equals("peer")) {
                String ip = msgContent.split(":")[0];
                int port = Integer.parseInt(msgContent.split(":")[1]);
                if (port < 0 || port > 65535)
                    throw new Exception();
                if (!ip.matches("(\\b25[0-5]|\\b2[0-4][0-9]|\\b[01]?[0-9][0-9]?)(\\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)){3}"))
                    throw new Exception();
            }

            processedMessage = new String[] { msgType, msgContent };
        } catch (Exception e) {
            e.printStackTrace();
            processedMessage = new String[] { "", "" };
        }
        return processedMessage;
    }

    /**
     * Processes the snippet message that is received from this MessageHandler
     * object's UDP socket.
     * 
     * @param snippet the snippet message received
     * @param d       the DatagramPacket this snippet was in
     * @throws Exception if the snippet message received from the UDP socket does
     *                   not follow the protocol
     * @return a Snippet object containing the contents of the snippet message
     */
    public static Snippet processSnippet(String snippet, DatagramPacket d) throws Exception {
        Snippet s;
        String[] splitSnippet = snippet.split(" ");
        int timestamp = Integer.parseInt(splitSnippet[0]);
        String content = "";
        for (int i = 1; i < splitSnippet.length; i++) {
            content += splitSnippet[i] + " ";
        }
        content = content.strip();
        String sourceIP = d.getAddress().getHostAddress();
        int sourcePort = d.getPort();
        s = new Snippet(content, new PeerLocation(sourceIP, sourcePort), timestamp);
        return s;
    }
}