package com.example.comm.gateway

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.example.comm.protocol.{Envelope, Protocol}
import java.net.InetSocketAddress

/**
 * UDP gateway.
 *
 * Full classic Akka IO binding requires careful typed/classic adaptation. This version provides a clean typed interface
 * and a pure deserialize path that can be unit-tested; the IO bind can be completed by wiring
 * `Udp(ctx.system.toClassic).manager ! Udp.Bind(...)` once the adapter is verified in the target environment.
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
          ctx.log.info(s"UDP gateway bind requested on $port")
          Behaviors.same
        case Send(to, data) =>
          ctx.log.debug(s"UDP send ${data.length} bytes to $to")
          Behaviors.same
        case Received(from, env) =>
          handler ! Received(from, env)
          Behaviors.same
      }
    }

  /** Pure helper for tests and for the eventual IO receive path. */
  def parse(data: Array[Byte], from: InetSocketAddress): Option[Received] =
    Protocol.deserialize(data).map(env => Received(from, env))
}
