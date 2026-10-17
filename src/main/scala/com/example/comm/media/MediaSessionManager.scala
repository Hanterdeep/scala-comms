package com.example.comm.media

import akka.actor.typed.ActorSystem
import scala.collection.mutable
import scala.util.Random

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
 * Media session manager — signaling helper for WebRTC.
 *
 * Generates SDP offers/answers with Opus audio and H.264 1080p video constraints. Screen and game share are modeled as
 * video tracks.
 *
 * Note: This is a pure-Scala signaling layer. Actual peer connection, ICE gathering, DTLS, and media capture/encoding
 * require a native WebRTC library (e.g. webrtc-java or a separate media process). The generated SDP is structurally
 * valid for signaling but does not establish a real media path by itself. Media remains P2P; the server is never on the
 * media path.
 */
class MediaSessionManager(system: ActorSystem[_], localId: String) {
  private val sessions = mutable.Map.empty[String, MediaMode]
  private val stunServers = Seq("stun:stun.l.google.com:19302")
  private val iceUfrag = Random.alphanumeric.take(8).mkString
  private val icePwd = Random.alphanumeric.take(24).mkString

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
    // Simulate host candidate; real ICE would come from a PeerConnection.
    onIce(IceCandidate(s"candidate:1 1 UDP 2130706431 127.0.0.1 50000 typ host", "0", 0))
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
    onIce(IceCandidate(s"candidate:1 1 UDP 2130706431 127.0.0.1 50001 typ host", "0", 0))
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
        "a=fmtp:111 minptime=10;useinbandfec=1\r\n" +
        "a=rtcp-fb:111 transport-cc\r\n" +
        "a=ssrc:1111 cname:local\r\n"
    val video =
      if (mode == MediaMode.Voice) ""
      else
        "m=video 9 UDP/TLS/RTP/SAVPF 96\r\n" +
          "a=rtpmap:96 H264/90000\r\n" +
          "a=fmtp:96 level-asymmetry-allowed=1;packetization-mode=1;profile-level-id=42e01f\r\n" +
          "a=framerate:30\r\n" +
          "a=imageattr:96 send [x=1920,y=1080] recv [x=1920,y=1080]\r\n" +
          "a=rtcp-fb:96 nack\r\n" +
          "a=ssrc:2222 cname:local\r\n"
    s"v=0\r\n" +
      s"o=- ${System.currentTimeMillis} 2 IN IP4 127.0.0.1\r\n" +
      s"s=$mode\r\n" +
      "t=0 0\r\n" +
      s"a=ice-ufrag:$iceUfrag\r\n" +
      s"a=ice-pwd:$icePwd\r\n" +
      "a=fingerprint:sha-256 AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99\r\n" +
      "a=setup:actpass\r\n" +
      "a=group:BUNDLE 0 1\r\n" +
      audio +
      video
  }

  private def buildAnswerSdp(offer: String): String =
    offer
      .replace("a=setup:actpass", "a=setup:active")
      .replace("o=-", "o=- answer")
      .replace("s=", "s=answer-")
}
