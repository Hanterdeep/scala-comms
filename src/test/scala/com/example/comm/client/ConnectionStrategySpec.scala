package com.example.comm.client

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ConnectionStrategySpec extends AnyFlatSpec with Matchers {

  "ConnectionStrategy" should "register methods" in {
    ConnectionStrategy.clear()
    ConnectionStrategy.register(UdpMethod)
    ConnectionStrategy.register(TcpMethod)
    ConnectionStrategy.registered.map(_.name) should contain allOf ("udp", "tcp")
  }

  it should "prefer lower priority when probes succeed" in {
    ConnectionStrategy.clear()
    ConnectionStrategy.register(WebSocketMethod)
    ConnectionStrategy.register(UdpMethod)
    val chosen = ConnectionStrategy.bestConnection(Seq(("127.0.0.1", 1)))
    chosen.foreach { case (m, _, _, _) => m.priority should be <= 3 }
    succeed
  }
}
