// =============================================================
// GIUServer.java  –  GIU-Net UDP Chat Server
// CNET101 Spring 2026 Project
//
// Team Leader ID : 19001366
// Port Derivation: Last 4 digits of Leader ID = 1366
//                  1366 >= 1024, so Port = 1366
//
// Handshake string expected from client:
//   HELLO-GIU-19001366-15
// Server replies with:
//   OK-GIU-19001366-15
//
// Sum derivation:
//   19001366 -> last digit 6
//   19008287 -> last digit 7
//   19008541 -> last digit 1
//   19010121 -> last digit 1
//   Sum = 6 + 7 + 1 + 1 = 15
// =============================================================

import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class GIUServer {

    // ---------- team-specific constants ----------
    private static final int    PORT          = 1366;
    private static final String LEADER_ID     = "19001366";
    private static final int    DIGIT_SUM     = 15;
    private static final String EXPECTED_HELLO = "HELLO-GIU-" + LEADER_ID + "-" + DIGIT_SUM;
    private static final String OK_REPLY       = "OK-GIU-"    + LEADER_ID + "-" + DIGIT_SUM;

    // ---------- protocol constants ----------
    private static final int    TIMEOUT_MS    = 3000;   // 3-second ACK wait
    private static final int    MAX_BUF       = 65535;

    // ---------- shared state ----------
    private static DatagramSocket socket;
    private static InetAddress    clientAddr;
    private static int            clientPort;

    // seq numbers
    private static final AtomicInteger sendSeq = new AtomicInteger(0);

    // ACK signalling: receiving thread puts arrived ACK seq here
    private static final LinkedBlockingQueue<Integer> ackQueue = new LinkedBlockingQueue<>();

    // graceful shutdown flag
    private static volatile boolean running = false;

    // =========================================================
    // Packet helpers
    // =========================================================

    /** Build a raw GIU-Net packet: [seqHigh, seqLow, payload bytes...] */
    static byte[] buildPacket(int seq, String payload) {
        byte[] payloadBytes = payload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] packet = new byte[2 + payloadBytes.length];
        packet[0] = (byte) ((seq >> 8) & 0xFF);
        packet[1] = (byte) (seq & 0xFF);
        System.arraycopy(payloadBytes, 0, packet, 2, payloadBytes.length);
        return packet;
    }

    /** Read the 16-bit sequence number from a raw packet (big-endian, unsigned). */
    static int readSeq(byte[] packet) {
        return (Byte.toUnsignedInt(packet[0]) << 8) | Byte.toUnsignedInt(packet[1]);
    }

    /** Read the payload string from a raw packet. */
    static String readPayload(byte[] packet, int length) {
        if (length <= 2) return "";
        return new String(packet, 2, length - 2, java.nio.charset.StandardCharsets.UTF_8);
    }

    // =========================================================
    // Send helpers
    // =========================================================

    static void sendPacket(byte[] data) throws Exception {
        DatagramPacket dp = new DatagramPacket(data, data.length, clientAddr, clientPort);
        socket.send(dp);
        GIULogger.log("SEND", data, System.currentTimeMillis());
    }

    static void sendPayload(int seq, String payload) throws Exception {
        byte[] data = buildPacket(seq, payload);
        sendPacket(data);
    }

    // =========================================================
    // Handshake
    // =========================================================

    static boolean performHandshake() throws Exception {
        byte[] buf = new byte[MAX_BUF];
        DatagramPacket incoming = new DatagramPacket(buf, buf.length);

        System.out.println("[SERVER] Waiting for HELLO handshake on port " + PORT + "...");
        socket.receive(incoming);
        GIULogger.log("RECV", incoming.getData(), System.currentTimeMillis());

        clientAddr = incoming.getAddress();
        clientPort = incoming.getPort();

        String payload = readPayload(incoming.getData(), incoming.getLength());
        System.out.println("[SERVER] Received: " + payload);

        if (EXPECTED_HELLO.equals(payload)) {
            sendPayload(0, OK_REPLY);
            System.out.println("[SERVER] Handshake accepted. Connection established.");
            return true;
        } else {
            sendPayload(0, "REJECTED");
            System.out.println("[SERVER] Handshake REJECTED. Wrong team string: " + payload);
            return false;
        }
    }

    // =========================================================
    // Receiver thread  –  runs continuously after handshake
    // =========================================================

    static class ReceiverThread extends Thread {
        @Override
        public void run() {
            byte[] buf = new byte[MAX_BUF];
            while (running) {
                try {
                    DatagramPacket dp = new DatagramPacket(buf, buf.length);
                    socket.receive(dp);
                    GIULogger.log("RECV", dp.getData(), System.currentTimeMillis());

                    int seq     = readSeq(dp.getData());
                    String payload = readPayload(dp.getData(), dp.getLength());

                    if (payload.startsWith("ACK:")) {
                        // Put the acknowledged seq number into the queue for the sender
                        int ackedSeq = Integer.parseInt(payload.substring(4).trim());
                        ackQueue.put(ackedSeq);

                    } else if ("EXIT".equals(payload)) {
                        System.out.println("[Connection closed by remote.]");
                        running = false;
                        socket.close();

                    } else {
                        // DATA packet  –  print and send ACK
                        System.out.println("[RECEIVED] " + payload);
                        sendPayload(0, "ACK:" + seq);
                    }

                } catch (Exception e) {
                    if (running) System.err.println("[RECV ERROR] " + e.getMessage());
                }
            }
        }
    }

    // =========================================================
    // Sender logic (runs on main thread after handshake)
    // =========================================================

    static void senderLoop() {
        java.util.Scanner scanner = new java.util.Scanner(System.in);
        System.out.println("[SERVER] You can now chat. Type EXIT to quit.");

        while (running) {
            if (!scanner.hasNextLine()) break;
            String line = scanner.nextLine().trim();

            if ("EXIT".equals(line)) {
                try {
                    sendPayload(0, "EXIT");
                } catch (Exception e) {
                    System.err.println("[SEND ERROR] " + e.getMessage());
                }
                running = false;
                socket.close();
                break;
            }

            int seq = sendSeq.incrementAndGet();
            boolean acked = false;

            for (int attempt = 0; attempt < 2; attempt++) {
                try {
                    sendPayload(seq, line);
                    // Wait up to 3 seconds for ACK
                    Integer arrivedAck = ackQueue.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    if (arrivedAck != null && arrivedAck == seq) {
                        acked = true;
                        break;
                    }
                    // Wrong or no ACK
                    if (attempt == 0) System.out.println("[TIMEOUT] Retrying...");

                } catch (Exception e) {
                    if (running) System.err.println("[SEND ERROR] " + e.getMessage());
                }
            }

            if (!acked) {
                System.out.println("[CONNECTION LOST]");
                running = false;
                socket.close();
                break;
            }
        }
    }

    // =========================================================
    // Main
    // =========================================================

    public static void main(String[] args) throws Exception {
        socket = new DatagramSocket(PORT);
        System.out.println("[SERVER] GIU-Net Server started on port " + PORT);

        if (!performHandshake()) {
            socket.close();
            return;
        }

        running = true;

        ReceiverThread receiver = new ReceiverThread();
        receiver.setDaemon(true);
        receiver.start();

        senderLoop();

        System.out.println("[SERVER] Shutting down.");
    }
}
