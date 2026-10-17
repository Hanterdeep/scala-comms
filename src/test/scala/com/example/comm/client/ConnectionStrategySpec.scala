package com.example.comm.client

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ConnectionStrategySpec extends AnyFlatSpec with Matchers {

  class FakeMethod(val name: String, val priority: Int, rtt: Option[Long]) extends ConnectionMethod {
    def probe(host: String, port: Int): Option[Long] = rtt
    def connect(host: String, port: Int, system: akka.actor.typed.ActorSystem[_]): Unit = ()
  }

  override def withFixture(test: NoArgTest) = {
    ConnectionStrategy.clear()
    try super.withFixture(test)
    finally ConnectionStrategy.clear()
  }

  "ConnectionStrategy" should "prefer lowest RTT" in {
    ConnectionStrategy.register(new FakeMethod("slow", 1, Some(100)))
    ConnectionStrategy.register(new FakeMethod("fast", 2, Some(10)))
    val best = ConnectionStrategy.bestConnection(Seq(("host", 1)))
    best.map(_._1.name) should be(Some("fast"))
  }

  it should "break ties by protocol priority" in {
    ConnectionStrategy.register(new FakeMethod("udp", 1, Some(50)))
    ConnectionStrategy.register(new FakeMethod("tcp", 2, Some(50)))
    val best = ConnectionStrategy.bestConnection(Seq(("host", 1)))
    best.map(_._1.name) should be(Some("udp"))
  }

  it should "skip methods that fail to probe" in {
    ConnectionStrategy.register(new FakeMethod("bad", 1, None))
    ConnectionStrategy.register(new FakeMethod("ok", 2, Some(20)))
    val best = ConnectionStrategy.bestConnection(Seq(("host", 1)))
    best.map(_._1.name) should be(Some("ok"))
  }

  it should "return None when no candidates succeed" in {
    ConnectionStrategy.register(new FakeMethod("bad", 1, None))
    ConnectionStrategy.bestConnection(Seq(("host", 1))) should be(None)
  }
}
