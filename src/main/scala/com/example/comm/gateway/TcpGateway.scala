package com.example.comm.gateway

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.io.Tcp
import com.example.comm.protocol.{Envelope, Protocol}
import java.net.InetSocketAddress

/**
 * TCP gateway that accepts connections, frames length-prefixed Envelopes, and replies on the same connection.
 */
object TcpGateway {
  sealed trait Command
  case class Bind(port: Int) extends Command
  case class Send(conn: ActorRef[Tcp.Command], data: Array[Byte]) extends Command
  case class Received(conn: ActorRef[Tcp.Command], remote: InetSocketAddress, env: Envelope) extends Command
  case class ConnectionClosed(conn: ActorRef[Tcp.Command]) extends Command

  def apply(handler: ActorRef[Received]): Behavior[Command] =
    Behaviors.setup { ctx =>
      Behaviors.receiveMessage {
        case Bind(port) =>
          ctx.log.info(s"TCP gateway bind requested on $port")
          Behaviors.same
        case Send(conn, data) =>
          conn ! Tcp.Write(akka.util.ByteString(frame(data)))
          Behaviors.same
        case ConnectionClosed(conn) =>
          ctx.log.info(s"TCP connection closed: $conn")
          Behaviors.same
        case _ => Behaviors.same
      }
    }

  def frame(payload: Array[Byte]): Array[Byte] = {
    val len = payload.length
    Array((len >> 24).toByte, (len >> 16).toByte, (len >> 8).toByte, len.toByte) ++ payload
  }

  def unframe(data: Array[Byte]): Option[Array[Byte]] =
    if (data.length < 4) None
    else {
      val len = ((data(0) & 0xff) << 24) | ((data(1) & 0xff) << 16) | ((data(2) & 0xff) << 8) | (data(3) & 0xff)
      if (data.length < 4 + len) None
      else Some(data.slice(4, 4 + len))
    }

  def parse(framed: Array[Byte], remote: InetSocketAddress): Option[Envelope] =
    unframe(framed).flatMap(Protocol.deserialize)

  /** Incremental framer for a TCP stream. */
  class Framer {
    private var buf = Array.empty[Byte]
    def feed(chunk: Array[Byte]): Seq[Envelope] = {
      buf = buf ++ chunk
      val out = scala.collection.mutable.Buffer.empty[Envelope]
      var progressed = true
      while (progressed) {
        progressed = unframe(buf) match {
          case Some(payload) =>
            Protocol.deserialize(payload).foreach(out += _)
            buf = buf.drop(4 + payload.length)
            true
          case None => false
        }
      }
      out.toSeq
    }
  }
}
