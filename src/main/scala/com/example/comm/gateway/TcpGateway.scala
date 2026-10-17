package com.example.comm.gateway

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.io.Tcp
import akka.util.ByteString
import com.example.comm.protocol.{Envelope, Protocol}
import java.net.InetSocketAddress

object TcpGateway:
  sealed trait Command
  case class Bind(port: Int) extends Command
  case class Send(conn: ActorRef[Tcp.Command], data: Array[Byte]) extends Command
  case class Received(conn: ActorRef[Tcp.Command], remote: InetSocketAddress, env: Envelope) extends Command

  def apply(handler: ActorRef[Received]): Behavior[Command] =
    Behaviors.setup { ctx =>
      val tcp = ctx.system.classicSystem.actorOf(akka.io.IO(Tcp)(ctx.system.classicSystem), "tcp-io")
      // Simplified; in production use akka.io.Tcp extension properly
      Behaviors.receiveMessage {
        case Bind(port) =>
          ctx.log.info(s"TCP gateway requested bind on $port")
          // Binding logic would go here via Tcp.Bind
          Behaviors.same
        case _ => Behaviors.same
      }
    }
