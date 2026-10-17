package com.example.comm.client

import akka.actor.typed.ActorSystem
import akka.cluster.typed.Cluster
import akka.cluster.ClusterEvent.{MemberEvent, MemberUp, MemberRemoved}
import com.example.comm.cluster.ClusterBootstrap
import com.example.comm.crypto.Crypto
import com.example.comm.media.{MediaSessionManager, MediaMode}
import com.example.comm.protocol.Protocol
import com.example.comm.storage.LocalStore
import scala.util.Random

object ClientApp {
  def main(args: Array[String]): Unit = {
    val seedHost = if (args.length > 0) args(0) else "127.0.0.1"
    val seedPort = if (args.length > 1) args(1).toInt else 2551
    val myPort = if (args.length > 2) args(2).toInt else 2552 + Random.nextInt(100)

    ConnectionStrategy.register(UdpMethod)
    ConnectionStrategy.register(TcpMethod)
    ConnectionStrategy.register(WebSocketMethod)

    val system = ClusterBootstrap.joinClient("Communicator", seedHost, seedPort, myPort)
    ClusterBootstrap.watchMembership(system)

    val myKeys = Crypto.generateKeyPair()
    val myId = Crypto.fingerprint(myKeys.publicKey)
    system.log.info(s"Client started. Pubkey fingerprint: $myId")

    val store = new LocalStore(s"client-$myId.db")
    store.init()
    system.log.info("Local store initialized (encrypted messages + images, hash-chained)")

    val media = new MediaSessionManager(system, myId)
    system.log.info("MediaSessionManager ready (WebRTC, Opus + hardware video codecs, desktop capture)")

    val cluster = Cluster(system)
    cluster.subscriptions ! akka.cluster.typed.Subscribe(
      system.systemActorOf(peerTracker(system, store), "peer-tracker"),
      classOf[MemberEvent]
    )

    val candidates = Seq((seedHost, seedPort))
    ConnectionStrategy.bestConnection(candidates).foreach { case (m, h, p, rtt) =>
      system.log.info(s"Selected ${m.name} to $h:$p (RTT ${rtt}ms)")
      m.connect(h, p, system)
    }

    val demoPayload = "hello from client".getBytes
    val (iv, ct) = Crypto.aesGcmEncrypt(myKeys.privateKey.take(32), demoPayload)
    val encrypted = iv ++ ct
    val stored = store.appendMessage("demo-chat", myId, encrypted)
    system.log.info(s"Stored message ${stored.id}, chain valid: ${store.validateHistory("demo-chat")}")

    Thread.sleep(Long.MaxValue)
  }

  private def peerTracker(system: ActorSystem[_], store: LocalStore): akka.actor.typed.Behavior[MemberEvent] =
    akka.actor.typed.scaladsl.Behaviors.receive { (ctx, msg) =>
      msg match {
        case MemberUp(member) =>
          ctx.log.info(s"Peer visible (P2P): ${member.address}")
        case MemberRemoved(member, _) =>
          ctx.log.info(s"Peer left: ${member.address}")
        case _ =>
      }
      akka.actor.typed.scaladsl.Behaviors.same
    }
}
