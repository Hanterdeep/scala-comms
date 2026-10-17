package com.example.comm.gateway

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.stream.scaladsl.Flow
import com.example.comm.protocol.{Envelope, Protocol}
import scala.concurrent.ExecutionContext

object WebSocketGateway {
  sealed trait Command
  case class Bind(host: String, port: Int) extends Command
  case class Received(env: Envelope, replyTo: ActorRef[Message]) extends Command

  def apply(handler: ActorRef[Received])(implicit system: ActorSystem[_]): Behavior[Command] =
    Behaviors.setup { ctx =>
      implicit val ec: ExecutionContext = system.executionContext
      Behaviors.receiveMessage {
        case Bind(host, port) =>
          val route = path("ws") {
            handleWebSocketMessages(wsFlow(handler))
          }
          Http().newServerAt(host, port).bind(route)
          ctx.log.info(s"WebSocket gateway bound on $host:$port")
          Behaviors.same
        case _ => Behaviors.same
      }
    }

  private def wsFlow(handler: ActorRef[Received]): Flow[Message, Message, Any] =
    Flow[Message].map {
      case TextMessage.Strict(text) =>
        Protocol.deserialize(text.getBytes).foreach { env =>
          // real impl would forward
        }
        TextMessage("ok")
      case _ => TextMessage("unsupported")
    }
}
