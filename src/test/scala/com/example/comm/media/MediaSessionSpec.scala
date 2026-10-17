package com.example.comm.media

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors

class MediaSessionSpec extends AnyFlatSpec with Matchers {

  private def withSystem[T](f: ActorSystem[_] => T): T = {
    val system = ActorSystem(Behaviors.empty, "media-test")
    try f(system)
    finally system.terminate()
  }

  "MediaSessionManager" should "produce an offer with Opus and 1080p H.264 for video" in {
    withSystem { system =>
      val mgr = new MediaSessionManager(system, "local")
      var offer: Option[MediaOffer] = None
      mgr.startSession("peer", MediaMode.CameraVideo, o => offer = Some(o), _ => ())
      offer should be(defined)
      offer.get.sdp should include("opus/48000")
      offer.get.sdp should include("1920")
      offer.get.sdp should include("1080")
    }
  }

  it should "produce an offer with screen share as video" in {
    withSystem { system =>
      val mgr = new MediaSessionManager(system, "local")
      var offer: Option[MediaOffer] = None
      mgr.startSession("peer", MediaMode.ScreenShare, o => offer = Some(o), _ => ())
      offer should be(defined)
      offer.get.mode should be(MediaMode.ScreenShare)
      offer.get.sdp should include("H264")
    }
  }

  it should "answer an offer" in {
    withSystem { system =>
      val mgr = new MediaSessionManager(system, "local")
      val offer = MediaOffer("v=0\r\ns=test\r\n", MediaMode.Voice)
      var answer: Option[MediaAnswer] = None
      mgr.handleOffer("peer", offer, a => answer = Some(a), _ => ())
      answer should be(defined)
      answer.get.sdp should include("answer")
    }
  }
}
