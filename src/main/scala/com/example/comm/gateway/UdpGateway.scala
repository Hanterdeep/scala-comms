package com.example.comm.gateway

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.io.Udp
import akka.util.ByteString
import com.example.comm.protocol.{Envelope, Protocol}
import java.net.InetSocketAddress

object UdpGateway {
  sealed trait Command
  case class Bind(port: Int) extends Command
  case class Send(to: InetSocketAddress, data: Array[Byte]) extends Command
  case class Received(from: InetSocketAddress, env: Envelope) extends Command

  def apply(handler: ActorRef[Received]): Behavior[Command] =
    Behaviors.setup { ctx =>
      val udp = ctx.spawn(udpManager(handler), "udp-manager")
      Behaviors.receiveMessage {
        case Bind(port) =>
          udp ! Udp.Bind(udp, new InetSocketAddress("0.0.0.0", port))
          Behaviors.same
        case Send(to, data) =>
          udp ! Udp.Send(ByteString(data), to)
          Behaviors.same
        case _ => Behaviors.same
      }
    }

  private def udpManager(handler: ActorRef[Received]): Behavior[Udp.Command] =
    Behaviors.setup { ctx =>
      Behaviors.receiveMessage {
        case Udp.Bound(local) =>
          ctx.log.info(s"UDP gateway bound on $local")
          Behaviors.same
        case Udp.Received(data, remote) =>
          Protocol.deserialize(data.toArray).foreach { env =>
            handler ! Received(remote, env)
          }
          Behaviors.same
        case _ => Behaviors.same
      }
    }
}
