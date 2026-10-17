package com.example.comm.media

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.config.ConfigFactory

class MediaSessionManagerSpec extends AnyFlatSpec with Matchers {

  private var portCounter = 5000
  private def withSystem[T](f: ActorSystem[_] => T): T = {
    val port = synchronized { portCounter += 1; portCounter }
    val config = ConfigFactory.parseString(
      s"""
        akka.actor.provider = local
        akka.remote.artery.canonical.port = $port
        akka.remote.artery.canonical.hostname = 127.0.0.1
      """
    )
    val sys = ActorSystem[Nothing](Behaviors.empty, s"media-test-$port", config)
    try f(sys)
    finally sys.terminate()
  }

  "MediaSessionManager" should "produce a real offer SDP with ice-ufrag and fingerprint for voice" in {
    withSystem { sys =>
      val m = new MediaSessionManager(sys, "me")
      try {
        var offer: Option[MediaOffer] = None
        var iceCount = 0
        m.startSession("peer", MediaMode.Voice, o => offer = Some(o), _ => iceCount += 1)
        offer should be(defined)
        val sdp = offer.get.sdp
        sdp should include("v=0")
        sdp.toLowerCase should include("a=ice-ufrag")
        sdp.toLowerCase should include("a=fingerprint")
        sdp should include("opus")
        m.activeSessions.get("peer") should be(Some(MediaMode.Voice))
      } finally m.close()
    }
  }

  it should "produce a valid offer for screen share without crashing in headless" in {
    withSystem { sys =>
      val m = new MediaSessionManager(sys, "me")
      try {
        var offer: Option[MediaOffer] = None
        m.startSession("peer", MediaMode.ScreenShare, o => offer = Some(o), _ => ())
        offer should be(defined)
        offer.get.sdp should include("v=0")
        // Video line appears only when a display is available; headless must still succeed
      } finally m.close()
    }
  }

  it should "emit Protocol envelopes via startSessionSignaled" in {
    withSystem { sys =>
      val m = new MediaSessionManager(sys, "me")
      try {
        val envelopes = scala.collection.mutable.ListBuffer.empty[com.example.comm.protocol.Envelope]
        m.startSessionSignaled("peer", MediaMode.Voice, env => envelopes += env)
        envelopes should not be empty
        envelopes.head.msgType should be(com.example.comm.protocol.MessageType.MediaOffer)
      } finally m.close()
    }
  }

  it should "list desktop sources without crashing" in {
    withSystem { sys =>
      val m = new MediaSessionManager(sys, "me")
      try {
        val (screens, windows) = m.listDesktopSources()
        screens should not be null
        windows should not be null
      } finally m.close()
    }
  }

  it should "stop a session" in {
    withSystem { sys =>
      val m = new MediaSessionManager(sys, "me")
      try {
        m.startSession("peer", MediaMode.Voice, _ => (), _ => ())
        m.stopSession("peer")
        m.activeSessions should be(empty)
      } finally m.close()
    }
  }
}
