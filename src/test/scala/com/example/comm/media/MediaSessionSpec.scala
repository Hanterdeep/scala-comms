package com.example.comm.media

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.config.ConfigFactory

class MediaSessionSpec extends AnyFlatSpec with Matchers {

  private var portCounter = 6000
  private def withSystem[T](f: ActorSystem[_] => T): T = {
    val port = synchronized { portCounter += 1; portCounter }
    val config = ConfigFactory.parseString(
      s"""
        akka.actor.provider = local
        akka.remote.artery.canonical.port = $port
        akka.remote.artery.canonical.hostname = 127.0.0.1
      """
    )
    val sys = ActorSystem[Nothing](Behaviors.empty, s"media-spec-$port", config)
    try f(sys)
    finally sys.terminate()
  }

  "MediaSessionManager" should "produce an offer with Opus and real ICE attributes" in {
    withSystem { sys =>
      val m = new MediaSessionManager(sys, "local")
      try {
        var offer: Option[MediaOffer] = None
        m.startSession("peer", MediaMode.Voice, o => offer = Some(o), _ => ())
        offer should be(defined)
        offer.get.sdp should include("opus")
        offer.get.sdp.toLowerCase should include("a=ice-ufrag")
      } finally m.close()
    }
  }

  it should "not crash on screen share in headless environment" in {
    withSystem { sys =>
      val m = new MediaSessionManager(sys, "local")
      try {
        var offer: Option[MediaOffer] = None
        noException should be thrownBy {
          m.startSession("peer", MediaMode.ScreenShare, o => offer = Some(o), _ => ())
        }
        offer should be(defined)
      } finally m.close()
    }
  }
}
