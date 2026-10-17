package com.example.comm.gateway

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.io.Tcp
import com.example.comm.protocol.Envelope
import java.net.InetSocketAddress

object TcpGateway {
  sealed trait Command
  case class Bind(port: Int) extends Command
  case class Send(conn: ActorRef[Tcp.Command], data: Array[Byte]) extends Command
  case class Received(conn: ActorRef[Tcp.Command], remote: InetSocketAddress, env: Envelope) extends Command

  def apply(handler: ActorRef[Received]): Behavior[Command] =
    Behaviors.setup { ctx =>
      Behaviors.receiveMessage {
        case Bind(port) =>
          ctx.log.info(s"TCP gateway requested bind on $port")
          Behaviors.same
        case _ => Behaviors.same
      }
    }
}
