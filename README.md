# Scala Communicator (Akka Cluster)

Peer-to-peer-style communicator with an Akka Cluster backbone.

## Properties

- **Server is stateless w.r.t. users**: never stores public keys, identities, or messages. Pure bootstrap + optional relay.
- **Clients are full cluster members**: any client can drop out without affecting the server or other clients.
- **P2P resilience**: once clients have joined, they track other members via cluster membership events. Known active peers remain visible and reachable even if the original server seed goes down.
- **Every node is a gateway**: server and each client expose UDP, TCP, and WebSocket endpoints so non-Akka clients can join.
- **Connection preference**:
  1. New clients always contact the server seed first.
  2. Thereafter they prefer the **nearest** reachable node (lowest RTT).
  3. Protocol priority: **UDP > TCP > WebSocket**.
- **Encryption**:
  - Handshake: X25519 ECDH (asymmetric). Public keys live only on clients.
  - Chat / group chat: AES-GCM (symmetric session keys derived per peer or group).
- **Local encrypted storage**: each client keeps its own SQLite database of encrypted messages and images.
- **Verifiable chat history**: messages form a hash chain (`prevHash` → `contentHash`). Any client can recompute the chain and detect tampering or deletion. History can be shared between participating clients and independently validated.
- **Images**: stored as encrypted blobs addressed by content hash (content-addressable).
- **Low-latency media** (voice, 1080p video, screen & game share):
  - WebRTC via `dev.onvoid.webrtc:webrtc-java` (native desktop capture).
  - Opus audio; hardware-accelerated H.264 / VP9 / AV1 video when available.
  - Desktop / window capture at 1920×1080 (and higher FPS for game share).
  - Signaling carried over the existing Envelope protocol so sessions work P2P among known clients even after the server disappears.
  - STUN for NAT traversal; TURN servers can be added for restrictive networks.
- **Extensibility**: implement `ConnectionMethod` and call `ConnectionStrategy.register(...)` to add new ways to reach a node.

## Run

```bash
# Terminal 1 – server
sbt "runMain com.example.comm.server.ServerApp"

# Terminal 2 – client
sbt "runMain com.example.comm.client.ClientApp 127.0.0.1 2551 2552"
```

Each client creates a local DB file `client-<fingerprint>.db`.

Native WebRTC libraries are loaded automatically by the webrtc-java dependency (platform-specific classifiers).

## Adding a new connection method

```scala
object QuicMethod extends ConnectionMethod {
  def name = "quic"
  def priority = 0          // higher preference than UDP
  def probe(host: String, port: Int) = Some(measureRtt(host, port))
  def connect(host: String, port: Int, system: ActorSystem[_]) = ...
}

ConnectionStrategy.register(QuicMethod)
```

## History validation example

```scala
val store = new LocalStore("my.db")
store.init()
// ... append messages ...
val ok = store.validateHistory("chat-id")   // true if chain is intact
```

Clients can exchange `Seq[StoredMessage]` and call `HistoryValidation.verifyChain` on the remote history before merging.

## Starting a media session

```scala
val media = new MediaSessionManager(system, myId)

media.startSession(
  peerId = "other-client-fingerprint",
  mode   = MediaMode.GameShare,   // or Voice, CameraVideo, ScreenShare
  onOffer = offer => send(Protocol.mediaOffer(myId, peerId, offer.sdp)),
  onIce   = ice   => send(Protocol.mediaIce(myId, peerId, ice.candidate, ice.sdpMid, ice.sdpMLineIndex))
)
```

Use `media.listDesktopSources()` to let the user pick a screen or window before starting a share.

## Notes

- Gateways are intentionally sketched so the wiring (Akka IO UDP/TCP + Akka HTTP WebSocket) can be completed without changing the strategy or crypto layers.
- Cluster membership events are observed; node departures are expected and harmless. Peers discovered via the cluster remain usable for P2P after the server disappears.
- All user-related cryptographic material and message content stay on the clients that own them. The server never sees plaintext or keys.
- Hash-chain validation detects alteration or deletion of historical messages without requiring a trusted server.
- Media is pure peer-to-peer WebRTC once signaling completes; the server is not on the media path, keeping latency minimal (typically well under 200 ms glass-to-glass on a good network).
- 1080p is the configured baseline; higher resolutions and frame rates are supported by the underlying capture and codec stack when the hardware allows.
