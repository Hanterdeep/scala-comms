package com.example.comm.gateway

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import com.example.comm.protocol.{Envelope, MessageType, Protocol}
import java.net.InetSocketAddress

class GatewayFramingSpec extends AnyFlatSpec with Matchers {

  "Protocol" should "round-trip an Envelope" in {
    val env = Envelope(MessageType.Chat, "sender123", None, None, "hello".getBytes)
    val bytes = Protocol.serialize(env)
    val back = Protocol.deserialize(bytes)
    back should be(defined)
    back.get.senderId.trim should be("sender123")
    back.get.msgType should be(MessageType.Chat)
  }

  it should "return None on garbage" in {
    Protocol.deserialize(Array[Byte](1, 2, 3)) should be(None)
  }

  "TcpGateway" should "frame and unframe a payload" in {
    val payload = "hello-tcp".getBytes
    val framed = TcpGateway.frame(payload)
    val unframed = TcpGateway.unframe(framed)
    unframed.map(_.toSeq) should be(Some(payload.toSeq))
  }

  it should "reject truncated frames" in {
    TcpGateway.unframe(Array[Byte](0, 0, 0, 5, 1, 2)) should be(None)
  }

  "UdpGateway" should "parse a valid datagram" in {
    val env = Envelope(MessageType.Ping, "udp-peer", None, None, Array.empty)
    val bytes = Protocol.serialize(env)
    val from = new InetSocketAddress("127.0.0.1", 9999)
    val parsed = UdpGateway.parse(bytes, from)
    parsed should be(defined)
    parsed.get.from should be(from)
  }
}
