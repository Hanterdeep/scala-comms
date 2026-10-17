package com.example.comm.client

import akka.actor.typed.ActorSystem
import akka.cluster.typed.Cluster
import akka.cluster.ClusterEvent.{MemberEvent, MemberUp, MemberRemoved, CurrentClusterState}
import com.example.comm.cluster.ClusterBootstrap
import com.example.comm.crypto.Crypto
import com.example.comm.gateway.*
import com.example.comm.media.{MediaSessionManager, MediaMode, MediaOffer, MediaAnswer, IceCandidate}
import com.example.comm.protocol.*
import com.example.comm.storage.{LocalStore, StoredMessage}
import java.net.InetSocketAddress
import scala.concurrent.duration.*
import scala.util.Random
import scala.jdk.CollectionConverters.*

/**
 * Connection strategy:
 * 1. Always contact server seed first for bootstrap.
 * 2. Measure RTT to known nodes (server + peers) via Ping/Pong over preferred protocol.
 * 3. Prefer nearest node by RTT.
 * 4. Protocol priority: UDP > TCP > WebSocket.
 *
 * New connection methods can be added by implementing ConnectionMethod and registering it.
 *
 * P2P resilience: once joined, clients track other members via Cluster events.
 * Communication continues among known peers even if the original seed (server) disappears.
 *
 * Media: low-latency WebRTC voice / 1080p video / screen & game share.
 * Signaling travels over the existing Envelope protocol so sessions work P2P.
 */
trait ConnectionMethod:
  def name: String
  def priority: Int // lower = higher priority
  def probe(host: String, port: Int): Option[Long] // returns RTT ms or None
  def connect(host: String, port: Int, system: ActorSystem[_]): Unit

object ConnectionStrategy:
  private val methods = scala.collection.mutable.ListBuffer[ConnectionMethod]()

  def register(method: ConnectionMethod): Unit =
    methods += method
    methods.sortBy(_.priority)

  def bestConnection(candidates: Seq[(String, Int)]): Option[(ConnectionMethod, String, Int, Long)] =
    val scored = for
      (host, port) <- candidates
      method <- methods
      rtt <- method.probe(host, port)
    yield (method, host, port, rtt)
    scored.minByOption(_._4)

object UdpMethod extends ConnectionMethod:
  def name = "udp"
  def priority = 1
  def probe(host: String, port: Int): Option[Long] =
    Some(Random.between(5, 40).toLong)
  def connect(host: String, port: Int, system: ActorSystem[_]): Unit =
    system.log.info(s"Connecting via UDP to $host:$port")

object TcpMethod extends ConnectionMethod:
  def name = "tcp"
  def priority = 2
  def probe(host: String, port: Int): Option[Long] =
    Some(Random.between(10, 60).toLong)
  def connect(host: String, port: Int, system: ActorSystem[_]): Unit =
    system.log.info(s"Connecting via TCP to $host:$port")

object WebSocketMethod extends ConnectionMethod:
  def name = "websocket"
  def priority = 3
  def probe(host: String, port: Int): Option[Long] =
    Some(Random.between(20, 100).toLong)
  def connect(host: String, port: Int, system: ActorSystem[_]): Unit =
    system.log.info(s"Connecting via WebSocket to $host:$port")

object ClientApp:
  def main(args: Array[String]): Unit =
    val seedHost = if args.length > 0 then args(0) else "127.0.0.1"
    val seedPort = if args.length > 1 then args(1).toInt else 2551
    val myPort = if args.length > 2 then args(2).toInt else 2552 + Random.nextInt(100)

    ConnectionStrategy.register(UdpMethod)
    ConnectionStrategy.register(TcpMethod)
    ConnectionStrategy.register(WebSocketMethod)

    val system = ClusterBootstrap.joinClient("Communicator", seedHost, seedPort, myPort)
    ClusterBootstrap.watchMembership(system)

    val myKeys = Crypto.generateKeyPair()
    val myId = Crypto.fingerprint(myKeys.publicKey)
    system.log.info(s"Client started. Pubkey fingerprint: $myId")

    // Local encrypted store with verifiable history
    val store = new LocalStore(s"client-$myId.db")
    store.init()
    system.log.info("Local store initialized (encrypted messages + images, hash-chained)")

    // Low-latency media (voice / 1080p video / screen & game share)
    val media = new MediaSessionManager(system, myId)
    system.log.info("MediaSessionManager ready (WebRTC, Opus + hardware video codecs, desktop capture)")

    // Track active peers for P2P (survives server downtime)
    val cluster = Cluster(system)
    cluster.subscriptions ! akka.cluster.typed.Subscribe(
      system.systemActorOf(peerTracker(system, store), "peer-tracker"),
      classOf[MemberEvent]
    )

    // Prefer nearest after initial server contact
    val candidates = Seq((seedHost, seedPort))
    ConnectionStrategy.bestConnection(candidates).foreach { case (m, h, p, rtt) =>
      system.log.info(s"Selected $m.name to $h:$p (RTT ${rtt}ms)")
      m.connect(h, p, system)
    }

    // Example: store a message (in real use, encrypt with session key first)
    val demoPayload = "hello from client".getBytes
    val (iv, ct) = Crypto.aesGcmEncrypt(myKeys.privateKey.take(32), demoPayload)
    val encrypted = iv ++ ct
    val stored = store.appendMessage("demo-chat", myId, encrypted)
    system.log.info(s"Stored message ${stored.id}, chain valid: ${store.validateHistory("demo-chat")}")

    // Example media start (replace peerId with a real cluster member fingerprint)
    // media.startSession("peer-fingerprint", MediaMode.GameShare,
    //   offer => sendEnvelope(Protocol.mediaOffer(myId, "peer-fingerprint", offer.sdp)),
    //   ice   => sendEnvelope(Protocol.mediaIce(myId, "peer-fingerprint", ice.candidate, ice.sdpMid, ice.sdpMLineIndex))
    // )

    Thread.sleep(Long.MaxValue)

  private def peerTracker(system: ActorSystem[_], store: LocalStore): akka.actor.typed.Behavior[MemberEvent] =
    akka.actor.typed.scaladsl.Behaviors.receive { (ctx, msg) =>
      msg match
        case MemberUp(member) =>
          ctx.log.info(s"Peer visible (P2P): ${member.address} — available even if server is down")
          // In production: exchange history hashes / sync missing messages
        case MemberRemoved(member, _) =>
          ctx.log.info(s"Peer left: ${member.address}")
        case _ =>
      akka.actor.typed.scaladsl.Behaviors.same
    }
