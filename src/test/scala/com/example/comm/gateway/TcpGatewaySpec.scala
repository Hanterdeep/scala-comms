package com.example.comm.gateway

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import com.example.comm.protocol.{Envelope, MessageType, Protocol}

class TcpGatewaySpec extends AnyFlatSpec with Matchers {

  "TcpGateway.Framer" should "reassemble a single frame" in {
    val env = Envelope(MessageType.Chat, "a", None, None, "hi".getBytes)
    val framed = TcpGateway.frame(Protocol.serialize(env))
    val framer = new TcpGateway.Framer
    val out = framer.feed(framed)
    out should have size 1
    out.head.msgType should be(MessageType.Chat)
  }

  it should "reassemble across chunks" in {
    val env = Envelope(MessageType.Ping, "b", None, None, Array.empty)
    val framed = TcpGateway.frame(Protocol.serialize(env))
    val framer = new TcpGateway.Framer
    val mid = framed.length / 2
    framer.feed(framed.take(mid)) should be(empty)
    val out = framer.feed(framed.drop(mid))
    out should have size 1
  }

  it should "handle multiple frames in one chunk" in {
    val e1 = Envelope(MessageType.Chat, "c", None, None, "one".getBytes)
    val e2 = Envelope(MessageType.Chat, "d", None, None, "two".getBytes)
    val both = TcpGateway.frame(Protocol.serialize(e1)) ++ TcpGateway.frame(Protocol.serialize(e2))
    val framer = new TcpGateway.Framer
    val out = framer.feed(both)
    out should have size 2
  }
}
