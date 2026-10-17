package com.example.comm.protocol

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.example.comm.crypto.Crypto
import java.util.concurrent.ConcurrentHashMap

/**
 * Pure relay. Never stores keys or user state.
 * Clients keep their own peer key cache.
 */
object MessageRouter:
  sealed trait Command
  case class Incoming(env: Envelope, replyTo: Option[ActorRef[Envelope]]) extends Command
  case class RegisterLocal(clientId: String, keys: Crypto.KeyPair) extends Command // only on client side

  def apply(): Behavior[Command] =
    Behaviors.setup { ctx =>
      // On the server this map stays empty by design.
      val localKeys = new ConcurrentHashMap[String, Crypto.KeyPair]()

      Behaviors.receiveMessage {
        case RegisterLocal(id, kp) =>
          localKeys.put(id, kp)
          ctx.log.info(s"Local identity registered (client-side only): $id")
          Behaviors.same

        case Incoming(env, replyTo) =>
          env.msgType match
            case MessageType.HandshakeInit | MessageType.HandshakeResp =>
              // Forward opaque; do not inspect keys
              ctx.log.debug(s"Relaying handshake from ${env.senderId}")
              replyTo.foreach(_ ! env)

            case MessageType.Chat | MessageType.GroupChat =>
              // Payload is already AES-GCM encrypted by sender
              ctx.log.debug(s"Relaying encrypted chat from ${env.senderId}")
              replyTo.foreach(_ ! env)

            case _ =>
          Behaviors.same
      }
    }
