package com.example.comm.gateway

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.OverflowStrategy
import com.example.comm.protocol.{Envelope, Protocol}
import scala.concurrent.ExecutionContext

/**
 * WebSocket gateway that parses Envelopes, forwards them, and allows the handler to push replies.
 */
object WebSocketGateway {
  sealed trait Command
  case class Bind(host: String, port: Int) extends Command
  case class Received(env: Envelope, replyTo: ActorRef[Message]) extends Command
  case class ConnectionClosed(remote: String) extends Command
  case class ConnectionFailed(remote: String, reason: String) extends Command

  def apply(handler: ActorRef[Received])(implicit system: ActorSystem[_]): Behavior[Command] =
    Behaviors.setup { ctx =>
      implicit val ec: ExecutionContext = system.executionContext
      Behaviors.receiveMessage {
        case Bind(host, port) =>
          val route = path("ws") {
            handleWebSocketMessages(wsFlow(handler, ctx))
          }
          Http().newServerAt(host, port).bind(route)
          ctx.log.info(s"WebSocket gateway bound on $host:$port")
          Behaviors.same
        case ConnectionClosed(remote) =>
          ctx.log.info(s"WebSocket closed: $remote")
          Behaviors.same
        case ConnectionFailed(remote, reason) =>
          ctx.log.warning(s"WebSocket failed from $remote: $reason")
          Behaviors.same
        case _ => Behaviors.same
      }
    }

  private def wsFlow(
      handler: ActorRef[Received],
      ctx: akka.actor.typed.scaladsl.ActorContext[_]
  ): Flow[Message, Message, Any] = {
    val (outActor, outSource) =
      Source.actorRef[Message](completionMatcher = PartialFunction.empty, failureMatcher = PartialFunction.empty, bufferSize = 16, overflowStrategy = OverflowStrategy.dropHead)
        .preMaterialize()(ctx.system)
    val replyTo = outActor

    val inFlow = Flow[Message].mapConcat {
      case TextMessage.Strict(text) =>
        Protocol.deserialize(text.getBytes("UTF-8")) match {
          case Some(env) =>
            handler ! Received(env, replyTo)
            Nil
          case None =>
            List(TextMessage.Strict("malformed"))
        }
      case BinaryMessage.Strict(data) =>
        Protocol.deserialize(data.toArray) match {
          case Some(env) =>
            handler ! Received(env, replyTo)
            Nil
          case None =>
            List(TextMessage.Strict("malformed"))
        }
      case _ =>
        List(TextMessage.Strict("unsupported"))
    }

    Flow.fromSinkAndSourceCoupled(inFlow.to(Sink.ignore), outSource)
  }
}
