# 🌐 GIU-Net: UDP Chat Application & Campus Network Simulation
> CNET101: Computer Networks — Spring 2026 Project
> German International University — Faculty of Informatics and Computer Science

---

## 👥 Team Information

| Name | Student ID |
|------|-----------|
| [Carlos Emad] | 19001366 ⭐ *(Leader)* |
| [Mark Tamer] | 19008287 |
| [Ahmed Roshedy] | 19008541 |
| [Omar Shaker] | 19010121 |

---

## 📋 Project Overview

This project has two parts:

- **Part 1 🖥️** — A custom UDP chat application built from scratch in Java, implementing our own handshake, sequence numbers, ACKs, and retransmission logic on top of raw UDP sockets.
- **Part 2 🔧** — A full campus network simulation in Cisco Packet Tracer using VLSM subnetting, static routing, HTTP services, and an optional ACL.

---

## ⚙️ Team-Specific Values

| Parameter | Value |
|-----------|-------|
| 🏷️ Leader ID | `19001366` |
| 🔌 Server Port | `1366` |
| 🤝 Client Handshake | `HELLO-GIU-19001366-15` |
| ✅ Server Reply | `OK-GIU-19001366-15` |
| 🌍 Base Network | `19.0.12.0/22` |

---

## 🗂️ Project Structure

```
GIUNet_19001366_SS26/
│
├── 📄 GIUServer.java          # UDP chat server
├── 📄 GIUClient.java          # UDP chat client
├── 📦 GIULogger.class         # Provided logger (from CMS)
├── 📊 giunet_session.log      # Binary session log (generated at runtime)
├── 🔬 capture.pcapng          # Wireshark capture of live session
├── 🖧  campus_network.pkt     # Cisco Packet Tracer topology file
└── 📝 Report.pdf              # Full project report
```

---

## 🚀 How to Run

### 📦 Prerequisites
- Java 17+
- `GIULogger.class` in the same folder as the `.java` files
- Both machines on the **same Wi-Fi or hotspot**

### 🔨 Compile
```bash
javac -cp . GIUServer.java GIUClient.java
```

### 🖥️ Run the Server
On the **server machine**:
```bash
java -cp . GIUServer
```
Expected output:
```
[SERVER] GIU-Net Server started on port 1366
[SERVER] Waiting for HELLO handshake...
```

### 💻 Run the Client
First, set `SERVER_IP` in `GIUClient.java` to the server machine's IPv4 address (run `ipconfig` on the server to find it).

On the **client machine**:
```bash
java -cp . GIUClient
```
Then type:
```
CONNECT
```

### 💬 Chat Commands
| Command | Action |
|---------|--------|
| Any text | Sends a chat message |
| `EXIT` | Closes the connection cleanly |

---

## 📡 GIU-Net Protocol

### Packet Format
```
[ Byte 0-1 ]  →  Sequence Number (16-bit, big-endian)
[ Byte 2+  ]  →  Payload (UTF-8 text)
```

### Packet Types
| Payload | Direction | Meaning |
|---------|-----------|---------|
| `HELLO-GIU-19001366-15` | Client → Server | Handshake initiation |
| `OK-GIU-19001366-15` | Server → Client | Handshake accepted |
| `REJECTED` | Server → Client | Wrong team string |
| `ACK:<seqnum>` | Either | Acknowledgment |
| `EXIT` | Either | Close connection |
| Any other text | Either | Chat message (DATA) |

### Handshake Flow
```
Client                        Server
  |                              |
  |--- HELLO-GIU-19001366-15 --->|
  |                              |  (validates string)
  |<--- OK-GIU-19001366-15 ------|
  |                              |
  |======= CHAT BEGINS ==========|
```

### Retransmission Logic
```
Send DATA  →  Wait 3 seconds for ACK
                ├── ACK received ✅  →  continue
                └── No ACK ❌  →  [TIMEOUT] Retrying...
                        ├── ACK received ✅  →  continue
                        └── No ACK ❌  →  [CONNECTION LOST] exit
```

---

## 🌍 Campus Network (Cisco Packet Tracer)

### VLSM Subnet Summary

| Subnet | Purpose | Network | Mask | Hosts |
|--------|---------|---------|------|-------|
| C | 🏠 Student Housing | 19.0.12.0 | /24 | 254 |
| B | 🖥️ Student Lab | 19.0.13.0 | /25 | 126 |
| A | 👔 Staff Offices | 19.0.13.128 | /26 | 62 |
| E | 🔒 Server DMZ | 19.0.13.192 | /28 | 14 |
| D | 🔗 WAN Link | 19.0.13.208 | /30 | 2 |

### Topology Overview
```
[Switch A - Lab]      [Switch D - Staff]    [Switch C - DMZ]
      |                      |                     |
      +-------------- Router 1 (Router0) ----------+
                             |
                        (WAN Serial Link)
                             |
                        Router 2 (Router1)
                             |
                      [Switch B - Housing]
```

---

## 🔍 Wireshark Capture

Filter used during capture:
```
udp.port == 1366
```

Capture shows:
- ✅ HELLO handshake packet with team verification string
- ✅ OK reply from server
- ✅ At least 3 DATA packets
- ✅ At least 3 ACK packets

---

## 📅 Deadlines

| Milestone | Date |
|-----------|------|
| 📝 Team Registration | May 11, 2026 |
| 📦 Final Submission | June 1, 2026 |
| 🎤 Evaluation Week | June 28, 2026 |

---

## 📚 Resources

- [Java DatagramSocket Docs](https://docs.oracle.com/en/java/docs/api/java.base/java/net/DatagramSocket.html)
- [Wireshark Download](https://www.wireshark.org)
- [Cisco Packet Tracer (NetAcad)](https://www.netacad.com)
- [VLSM Calculator (verify only)](https://www.subnet-calculator.com/vlsm.php)

---

