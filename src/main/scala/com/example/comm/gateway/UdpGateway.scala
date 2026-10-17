package com.example.comm.gateway

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.example.comm.protocol.Envelope
import java.net.InetSocketAddress

/**
 * UDP gateway stub. Real implementation would use classic Akka IO (akka.actor.ActorRef) adapted via
 * akka.actor.typed.scaladsl.adapter, or a dedicated classic actor. Kept as a clean typed actor so the project compiles.
 */
object UdpGateway {
  sealed trait Command
  case class Bind(port: Int) extends Command
  case class Send(to: InetSocketAddress, data: Array[Byte]) extends Command
  case class Received(from: InetSocketAddress, env: Envelope) extends Command

  def apply(handler: ActorRef[Received]): Behavior[Command] =
    Behaviors.setup { ctx =>
      Behaviors.receiveMessage {
        case Bind(port) =>
          ctx.log.info(s"UDP gateway bind requested on port $port (stub)")
          Behaviors.same
        case Send(to, data) =>
          ctx.log.debug(s"UDP send ${data.length} bytes to $to (stub)")
          Behaviors.same
        case Received(from, env) =>
          handler ! Received(from, env)
          Behaviors.same
      }
    }
}
