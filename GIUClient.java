// =============================================================
// GIUClient.java  –  GIU-Net UDP Chat Client
// CNET101 Spring 2026 Project
//
// Team Leader ID : 19001366
// Port Derivation: Last 4 digits of Leader ID = 1366
//                  1366 >= 1024, so Port = 1366
//
// Handshake string sent by client:
//   HELLO-GIU-19001366-15
// Server expected reply:
//   OK-GIU-19001366-15
//
// Sum derivation:
//   19001366 -> last digit 6
//   19008287 -> last digit 7
//   19008541 -> last digit 1
//   19010121 -> last digit 1
//   Sum = 6 + 7 + 1 + 1 = 15
//
// IMPORTANT: Set SERVER_IP to the IPv4 address of the machine
//            running GIUServer before compiling.
// =============================================================

import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class GIUClient {

    // ---------- team-specific constants ----------
    private static final int    PORT          = 1366;
    private static final String LEADER_ID     = "19001366";
    private static final int    DIGIT_SUM     = 15;
    private static final String HELLO_PAYLOAD = "HELLO-GIU-" + LEADER_ID + "-" + DIGIT_SUM;
    private static final String EXPECTED_OK   = "OK-GIU-"    + LEADER_ID + "-" + DIGIT_SUM;

    // *** SET THIS to the server machine's IPv4 address ***
    private static final String SERVER_IP     = "192.168.137.1";

    // ---------- protocol constants ----------
    private static final int    TIMEOUT_MS    = 3000;
    private static final int    MAX_BUF       = 65535;

    // ---------- shared state ----------
    private static DatagramSocket socket;
    private static InetAddress    serverAddr;

    private static final AtomicInteger sendSeq = new AtomicInteger(0);
    private static final LinkedBlockingQueue<Integer> ackQueue = new LinkedBlockingQueue<>();
    private static volatile boolean running = false;

    // =========================================================
    // Packet helpers  (identical to server side)
    // =========================================================

    static byte[] buildPacket(int seq, String payload) {
        byte[] payloadBytes = payload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] packet = new byte[2 + payloadBytes.length];
        packet[0] = (byte) ((seq >> 8) & 0xFF);
        packet[1] = (byte) (seq & 0xFF);
        System.arraycopy(payloadBytes, 0, packet, 2, payloadBytes.length);
        return packet;
    }

    static int readSeq(byte[] packet) {
        return (Byte.toUnsignedInt(packet[0]) << 8) | Byte.toUnsignedInt(packet[1]);
    }

    static String readPayload(byte[] packet, int length) {
        if (length <= 2) return "";
        return new String(packet, 2, length - 2, java.nio.charset.StandardCharsets.UTF_8);
    }

    // =========================================================
    // Send helpers
    // =========================================================

    static void sendPacket(byte[] data) throws Exception {
        DatagramPacket dp = new DatagramPacket(data, data.length, serverAddr, PORT);
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
        // Socket is NOT opened until user types CONNECT
        java.util.Scanner scanner = new java.util.Scanner(System.in);
        System.out.println("[CLIENT] Type CONNECT to start the handshake.");

        while (true) {
            String input = scanner.nextLine().trim();
            if ("CONNECT".equalsIgnoreCase(input)) break;
            System.out.println("[CLIENT] Type CONNECT to begin.");
        }

        // Open the socket only now
        socket = new DatagramSocket();
        serverAddr = InetAddress.getByName(SERVER_IP);

        System.out.println("[CLIENT] Sending HELLO handshake to " + SERVER_IP + ":" + PORT);
        sendPayload(0, HELLO_PAYLOAD);

        // Wait for OK or REJECTED
        byte[] buf = new byte[MAX_BUF];
        DatagramPacket response = new DatagramPacket(buf, buf.length);
        socket.setSoTimeout(5000); // 5 s to receive handshake reply
        socket.receive(response);
        GIULogger.log("RECV", response.getData(), System.currentTimeMillis());
        socket.setSoTimeout(0);    // reset to blocking

        String reply = readPayload(response.getData(), response.getLength());
        System.out.println("[CLIENT] Server replied: " + reply);

        if (EXPECTED_OK.equals(reply)) {
            System.out.println("[CLIENT] Handshake successful. Connection established.");
            return true;
        } else {
            System.out.println("[CLIENT] Handshake REJECTED by server.");
            socket.close();
            return false;
        }
    }

    // =========================================================
    // Receiver thread
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

                    int seq        = readSeq(dp.getData());
                    String payload = readPayload(dp.getData(), dp.getLength());

                    if (payload.startsWith("ACK:")) {
                        int ackedSeq = Integer.parseInt(payload.substring(4).trim());
                        ackQueue.put(ackedSeq);

                    } else if ("EXIT".equals(payload)) {
                        System.out.println("[Connection closed by remote.]");
                        running = false;
                        socket.close();

                    } else {
                        // DATA packet
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
    // Sender loop (main thread)
    // =========================================================

    static void senderLoop() {
        java.util.Scanner scanner = new java.util.Scanner(System.in);
        System.out.println("[CLIENT] You can now chat. Type EXIT to quit.");

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

            int seq    = sendSeq.incrementAndGet();
            boolean acked = false;

            for (int attempt = 0; attempt < 2; attempt++) {
                try {
                    sendPayload(seq, line);
                    Integer arrivedAck = ackQueue.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    if (arrivedAck != null && arrivedAck == seq) {
                        acked = true;
                        break;
                    }
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
        if (!performHandshake()) return;

        running = true;

        ReceiverThread receiver = new ReceiverThread();
        receiver.setDaemon(true);
        receiver.start();

        senderLoop();

        System.out.println("[CLIENT] Shutting down.");
    }
}
