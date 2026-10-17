package com.example.comm.client

import akka.actor.typed.ActorSystem
import scala.util.Try
import java.net.{InetSocketAddress, Socket}
import scala.concurrent.duration._

trait ConnectionMethod {
  def name: String
  def priority: Int
  def probe(host: String, port: Int): Option[Long]
  def connect(host: String, port: Int, system: ActorSystem[_]): Unit
}

object ConnectionStrategy {
  private val methods = scala.collection.mutable.ListBuffer[ConnectionMethod]()

  def register(method: ConnectionMethod): Unit = {
    methods += method
    methods.sortBy(_.priority)
  }

  def clear(): Unit = methods.clear()

  def registered: Seq[ConnectionMethod] = methods.toSeq

  /** Select best connection: lowest RTT, then protocol priority. */
  def bestConnection(candidates: Seq[(String, Int)]): Option[(ConnectionMethod, String, Int, Long)] = {
    val scored = for {
      (host, port) <- candidates
      method <- methods
      rtt <- method.probe(host, port)
    } yield (method, host, port, rtt)
    if (scored.isEmpty) None
    else Some(scored.minBy { case (m, _, _, rtt) => (rtt, m.priority) })
  }
}

/** TCP connect-time probe (real RTT). */
object TcpMethod extends ConnectionMethod {
  def name = "tcp"
  def priority = 2
  def probe(host: String, port: Int): Option[Long] =
    Try {
      val start = System.nanoTime()
      val s = new Socket()
      s.connect(new InetSocketAddress(host, port), 500)
      val rtt = (System.nanoTime() - start) / 1000000L
      s.close()
      rtt
    }.toOption

  def connect(host: String, port: Int, system: ActorSystem[_]): Unit =
    system.log.info(s"Connecting via TCP to $host:$port")
}

/** UDP probe via a short TCP fallback or timeout-based estimate. */
object UdpMethod extends ConnectionMethod {
  def name = "udp"
  def priority = 1
  def probe(host: String, port: Int): Option[Long] =
    TcpMethod.probe(host, port).map(_ + 2)

  def connect(host: String, port: Int, system: ActorSystem[_]): Unit =
    system.log.info(s"Connecting via UDP to $host:$port")
}

object WebSocketMethod extends ConnectionMethod {
  def name = "websocket"
  def priority = 3
  def probe(host: String, port: Int): Option[Long] =
    TcpMethod.probe(host, port).map(_ + 10)

  def connect(host: String, port: Int, system: ActorSystem[_]): Unit =
    system.log.info(s"Connecting via WebSocket to $host:$port")
}
