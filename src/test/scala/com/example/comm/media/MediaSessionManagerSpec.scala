package com.example.comm.media

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.TryValues._
import org.scalatest.Assertions._
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors

class MediaSessionManagerSpec extends AnyFlatSpec with Matchers {

  private var portCounter = 2600
  private def withSystem[T](f: ActorSystem[_] => T): T = {
    val port = synchronized { portCounter += 1; portCounter }
    val config = com.typesafe.config.ConfigFactory.parseString(
      s"""
        akka.actor.provider = local
        akka.remote.artery.canonical.port = $port
      """
    )
    val sys = ActorSystem[Nothing](Behaviors.empty, s"media-test-$port", config)
    try f(sys)
    finally sys.terminate()
  }

  "MediaSessionManager" should "produce an offer with opus for voice" in {
    withSystem { sys =>
      val m = new MediaSessionManager(sys, "me")
      var offer: Option[MediaOffer] = None
      m.startSession("peer", MediaMode.Voice, o => offer = Some(o), _ => ())
      offer should be(defined)
      offer.get.sdp should include("opus")
      m.activeSessions.get("peer") should be(Some(MediaMode.Voice))
    }
  }

  it should "produce a valid offer for screen share (video when desktop capture enabled)" in {
    withSystem { sys =>
      val m = new MediaSessionManager(sys, "me")
      var offer: Option[MediaOffer] = None
      m.startSession("peer", MediaMode.ScreenShare, o => offer = Some(o), _ => ())
      offer should be(defined)
      offer.get.sdp should include("v=0")
      offer.get.sdp should include("a=ice-ufrag")
      // Full video track appears only when ENABLE_DESKTOP_CAPTURE=true on a graphical host
    }
  }

  it should "not throw when answering a minimal offer" in {
    withSystem { sys =>
      val m = new MediaSessionManager(sys, "me")
      var answer: Option[MediaAnswer] = None
      val offer = MediaOffer(
        "v=0\r\no=- 1 2 IN IP4 127.0.0.1\r\ns=-\r\nt=0 0\r\nm=audio 9 UDP/TLS/RTP/SAVPF 111\r\na=rtpmap:111 opus/48000/2\r\n",
        MediaMode.Voice
      )
      noException should be thrownBy {
        m.handleOffer("peer", offer, a => answer = Some(a), _ => ())
      }
    }
  }

  it should "list desktop sources" in {
    withSystem { sys =>
      val m = new MediaSessionManager(sys, "me")
      val (screens, windows) = m.listDesktopSources()
      screens should not be empty
    }
  }

  it should "stop a session" in {
    withSystem { sys =>
      val m = new MediaSessionManager(sys, "me")
      m.startSession("peer", MediaMode.Voice, _ => (), _ => ())
      m.stopSession("peer")
      m.activeSessions should be(empty)
    }
  }
}
