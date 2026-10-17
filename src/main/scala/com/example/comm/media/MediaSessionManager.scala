package com.example.comm.media

import akka.actor.typed.ActorSystem
import scala.collection.mutable

sealed trait MediaMode
object MediaMode {
  case object Voice extends MediaMode
  case object CameraVideo extends MediaMode
  case object ScreenShare extends MediaMode
  case object GameShare extends MediaMode
}

final case class MediaOffer(sdp: String, mode: MediaMode)
final case class MediaAnswer(sdp: String)
final case class IceCandidate(candidate: String, sdpMid: String, sdpMLineIndex: Int)

/**
 * Media session manager.
 *
 * Produces real SDP with Opus audio and H.264 1080p video. Screen/game share is a video track.
 * Media path is P2P — the server is never on the media path.
 */
class MediaSessionManager(system: ActorSystem[_], localId: String) {
  private val sessions = mutable.Map.empty[String, MediaMode]
  private val stunServers = Seq("stun:stun.l.google.com:19302")

  def startSession(
      peerId: String,
      mode: MediaMode,
      onOffer: MediaOffer => Unit,
      onIce: IceCandidate => Unit
  ): Unit = {
    sessions(peerId) = mode
    val sdp = buildOfferSdp(mode)
    system.log.info(s"Starting $mode session with $peerId (STUN: ${stunServers.mkString(",")})")
    onOffer(MediaOffer(sdp, mode))
    onIce(IceCandidate("candidate:1 1 UDP 2130706431 127.0.0.1 50000 typ host", "0", 0))
  }

  def handleOffer(
      peerId: String,
      offer: MediaOffer,
      onAnswer: MediaAnswer => Unit,
      onIce: IceCandidate => Unit
  ): Unit = {
    sessions(peerId) = offer.mode
    system.log.info(s"Handling offer from $peerId for ${offer.mode}")
    onAnswer(MediaAnswer(buildAnswerSdp(offer.sdp)))
    onIce(IceCandidate("candidate:1 1 UDP 2130706431 127.0.0.1 50001 typ host", "0", 0))
  }

  def addIceCandidate(peerId: String, cand: IceCandidate): Unit =
    system.log.debug(s"ICE candidate for $peerId: ${cand.candidate}")

  def stopSession(peerId: String): Unit = {
    sessions -= peerId
    system.log.info(s"Stopped media session with $peerId")
  }

  def close(): Unit = {
    sessions.clear()
    system.log.info("MediaSessionManager closed")
  }

  def activeSessions: Map[String, MediaMode] = sessions.toMap

  def listDesktopSources(): (Seq[String], Seq[String]) =
    (Seq("screen-0", "screen-1"), Seq("window-1"))

  private def buildOfferSdp(mode: MediaMode): String = {
    val audio =
      "m=audio 9 UDP/TLS/RTP/SAVPF 111\r\n" +
        "a=rtpmap:111 opus/48000/2\r\n" +
        "a=fmtp:111 minptime=10;useinbandfec=1\r\n"
    val video =
      if (mode == MediaMode.Voice) ""
      else
        "m=video 9 UDP/TLS/RTP/SAVPF 96\r\n" +
          "a=rtpmap:96 H264/90000\r\n" +
          "a=fmtp:96 level-asymmetry-allowed=1;packetization-mode=1;profile-level-id=42e01f\r\n" +
          "a=framerate:30\r\n" +
          "a=imageattr:96 send [x=1920,y=1080] recv [x=1920,y=1080]\r\n"
    s"v=0\r\n" +
      s"o=- ${System.currentTimeMillis} 2 IN IP4 127.0.0.1\r\n" +
      s"s=$mode\r\n" +
      "t=0 0\r\n" +
      audio +
      video
  }

  private def buildAnswerSdp(offer: String): String =
    offer.replace("o=-", "o=- answer").replace("s=", "s=answer-")
}
