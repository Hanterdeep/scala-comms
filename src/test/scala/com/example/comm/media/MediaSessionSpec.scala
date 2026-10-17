package com.example.comm.media

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class MediaSessionSpec extends AnyFlatSpec with Matchers {

  "MediaSessionManager" should "start a call with audio and 1080p video" in {
    val engine = new MediaSessionManager.RecordingEngine
    val (pc, offer) = MediaSessionManager.startCall(
      engine,
      Seq(MediaSessionManager.Audio, MediaSessionManager.Video)
    )
    pc should not be empty
    offer.kind should be("offer")
  }

  it should "support screen share" in {
    val engine = new MediaSessionManager.RecordingEngine
    val (pc, _) = MediaSessionManager.startCall(
      engine,
      Seq(MediaSessionManager.Audio, MediaSessionManager.Screen)
    )
    pc should not be empty
  }

  it should "round-trip an answer" in {
    val engine = new MediaSessionManager.RecordingEngine
    val (pc, offer) = MediaSessionManager.startCall(engine, Seq(MediaSessionManager.Audio))
    val answer = engine.createAnswer(pc, offer)
    answer.kind should be("answer")
    answer.sdp should be(offer.sdp)
  }
}
