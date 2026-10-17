package com.example.comm.client

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.net.InetSocketAddress

class ConnectionStrategySpec extends AnyFlatSpec with Matchers {

  "ConnectionStrategy" should "prefer UDP over TCP over WebSocket" in {
    val udp = ConnectionStrategy.Candidate(new InetSocketAddress("1.1.1.1", 1), ConnectionStrategy.Udp, 50)
    val tcp = ConnectionStrategy.Candidate(new InetSocketAddress("1.1.1.1", 2), ConnectionStrategy.Tcp, 10)
    val ws = ConnectionStrategy.Candidate(new InetSocketAddress("1.1.1.1", 3), ConnectionStrategy.WebSocket, 5)
    val chosen = ConnectionStrategy.selectWithPriority(Seq(ws, tcp, udp))
    chosen.map(_.protocol) should be(Some(ConnectionStrategy.Udp))
  }

  it should "pick lowest RTT within a protocol" in {
    val a = ConnectionStrategy.Candidate(new InetSocketAddress("a", 1), ConnectionStrategy.Tcp, 100)
    val b = ConnectionStrategy.Candidate(new InetSocketAddress("b", 1), ConnectionStrategy.Tcp, 20)
    ConnectionStrategy.prefer(ConnectionStrategy.Tcp, Seq(a, b)).map(_.rttMs) should be(Some(20))
  }

  it should "fall back when preferred protocol is absent" in {
    val tcp = ConnectionStrategy.Candidate(new InetSocketAddress("x", 1), ConnectionStrategy.Tcp, 30)
    val chosen = ConnectionStrategy.selectWithPriority(Seq(tcp))
    chosen.map(_.protocol) should be(Some(ConnectionStrategy.Tcp))
  }
}
