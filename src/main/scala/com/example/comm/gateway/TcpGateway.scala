package com.example.comm.gateway

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.io.Tcp
import com.example.comm.protocol.{Envelope, Protocol}
import java.net.InetSocketAddress

/**
 * TCP gateway with length-prefixed framing helpers. The pure framing logic is tested; the Akka IO bind path can be
 * completed once classic/typed adaptation is verified.
 */
object TcpGateway {
  sealed trait Command
  case class Bind(port: Int) extends Command
  case class Send(conn: ActorRef[Tcp.Command], data: Array[Byte]) extends Command
  case class Received(conn: ActorRef[Tcp.Command], remote: InetSocketAddress, env: Envelope) extends Command

  def apply(handler: ActorRef[Received]): Behavior[Command] =
    Behaviors.setup { ctx =>
      Behaviors.receiveMessage {
        case Bind(port) =>
          ctx.log.info(s"TCP gateway bind requested on $port")
          Behaviors.same
        case Send(_, data) =>
          val _ = frame(data)
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
}
