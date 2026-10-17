package com.example.comm.protocol

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.example.comm.crypto.Crypto
import java.util.concurrent.ConcurrentHashMap

object MessageRouter {
  sealed trait Command
  case class Incoming(env: Envelope, replyTo: Option[ActorRef[Envelope]]) extends Command
  case class RegisterLocal(clientId: String, keys: Crypto.KeyPair) extends Command

  def apply(): Behavior[Command] =
    Behaviors.setup { ctx =>
      val localKeys = new ConcurrentHashMap[String, Crypto.KeyPair]()

      Behaviors.receiveMessage {
        case RegisterLocal(id, kp) =>
          localKeys.put(id, kp)
          ctx.log.info(s"Local identity registered (client-side only): $id")
          Behaviors.same

        case Incoming(env, replyTo) =>
          env.msgType match {
            case MessageType.HandshakeInit | MessageType.HandshakeResp =>
              ctx.log.debug(s"Relaying handshake from ${env.senderId}")
              replyTo.foreach(_ ! env)
            case MessageType.Chat | MessageType.GroupChat =>
              ctx.log.debug(s"Relaying encrypted chat from ${env.senderId}")
              replyTo.foreach(_ ! env)
            case _ =>
          }
          Behaviors.same
      }
    }
}
