package com.example.comm.media

import akka.actor.typed.ActorSystem
import scala.collection.mutable

/**
 * Media session manager.
 *
 * This is a structural stub that compiles and exposes the same public API the rest of the app expects. The real WebRTC
 * wiring (webrtc-java) should be filled in once the exact version's class/method surface is confirmed; the library's
 * PeerConnectionFactory / RTCPeerConnectionObserver / video source APIs differ across releases.
 *
 * Signaling still travels over the existing Protocol envelopes.
 */
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

class MediaSessionManager(system: ActorSystem[_], localId: String) {
  private val sessions = mutable.Set.empty[String]

  def startSession(
      peerId: String,
      mode: MediaMode,
      onOffer: MediaOffer => Unit,
      onIce: IceCandidate => Unit
  ): Unit = {
    sessions += peerId
    system.log.info(s"Starting $mode session with $peerId (stub)")
    // Real impl would create PeerConnection, add tracks, createOffer, then:
    onOffer(MediaOffer(s"v=0\r\no=- 0 0 IN IP4 127.0.0.1\r\ns=$mode\r\n", mode))
  }

  def handleOffer(
      peerId: String,
      offer: MediaOffer,
      onAnswer: MediaAnswer => Unit,
      onIce: IceCandidate => Unit
  ): Unit = {
    sessions += peerId
    system.log.info(s"Handling offer from $peerId for ${offer.mode} (stub)")
    onAnswer(MediaAnswer(s"v=0\r\no=- 0 0 IN IP4 127.0.0.1\r\ns=answer\r\n"))
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

  /** Placeholder for desktop source enumeration. */
  def listDesktopSources(): (Seq[String], Seq[String]) =
    (Seq("screen-0"), Seq.empty)
}
