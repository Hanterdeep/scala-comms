package com.example.comm.media

import akka.actor.typed.ActorSystem
import dev.onvoid.webrtc._
import dev.onvoid.webrtc.media.audio.AudioOptions
import dev.onvoid.webrtc.media.video._
import dev.onvoid.webrtc.media.video.desktop._
import scala.collection.mutable
import scala.jdk.CollectionConverters._

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
  private val factory: PeerConnectionFactory = {
    val options = new PeerConnectionFactory.Options()
    new PeerConnectionFactory(options)
  }

  private val sessions = mutable.Map.empty[String, RTCPeerConnection]
  private val desktopSources = mutable.Map.empty[String, VideoDesktopSource]

  private val rtcConfig: RTCConfiguration = {
    val cfg = new RTCConfiguration()
    cfg.iceServers = java.util.List.of(
      new RTCIceServer("stun:stun.l.google.com:19302"),
      new RTCIceServer("stun:stun1.l.google.com:19302")
    )
    cfg.sdpSemantics = RTCSdpSemantics.UNIFIED_PLAN
    cfg
  }

  def startSession(
    peerId: String,
    mode: MediaMode,
    onOffer: MediaOffer => Unit,
    onIce: IceCandidate => Unit
  ): Unit = {
    val observer = new RTCPeerConnectionObserver {
      override def onIceCandidate(event: RTCIceCandidate): Unit =
        onIce(IceCandidate(event.sdp, event.sdpMid, event.sdpMLineIndex))

      override def onTrack(event: RTCTrackEvent): Unit =
        system.log.info(s"Remote track received from $peerId: ${event.track.getKind}")

      override def onConnectionChange(state: RTCPeerConnectionState): Unit =
        system.log.info(s"Media connection to $peerId: $state")
    }

    val pc = factory.createPeerConnection(rtcConfig, observer)
    sessions(peerId) = pc

    mode match {
      case MediaMode.Voice =>
        addAudioTrack(pc)
      case MediaMode.CameraVideo =>
        addAudioTrack(pc)
        addCameraTrack(pc)
      case MediaMode.ScreenShare | MediaMode.GameShare =>
        addAudioTrack(pc)
        addDesktopTrack(pc, peerId, mode == MediaMode.GameShare)
    }

    val constraints = new RTCOfferOptions()
    constraints.offerToReceiveAudio = true
    constraints.offerToReceiveVideo = mode != MediaMode.Voice
    pc.createOffer(constraints, new CreateSessionDescriptionObserver {
      override def onSuccess(desc: RTCSessionDescription): Unit =
        pc.setLocalDescription(desc, new SetSessionDescriptionObserver {
          override def onSuccess(): Unit =
            onOffer(MediaOffer(desc.sdp, mode))
          override def onFailure(error: String): Unit =
            system.log.error(s"setLocalDescription failed: $error")
        })
      override def onFailure(error: String): Unit =
        system.log.error(s"createOffer failed: $error")
    })
  }

  def handleOffer(peerId: String, offer: MediaOffer, onAnswer: MediaAnswer => Unit, onIce: IceCandidate => Unit): Unit = {
    val observer = new RTCPeerConnectionObserver {
      override def onIceCandidate(event: RTCIceCandidate): Unit =
        onIce(IceCandidate(event.sdp, event.sdpMid, event.sdpMLineIndex))
      override def onTrack(event: RTCTrackEvent): Unit =
        system.log.info(s"Remote track from $peerId")
      override def onConnectionChange(state: RTCPeerConnectionState): Unit =
        system.log.info(s"Media state $peerId: $state")
    }
    val pc = factory.createPeerConnection(rtcConfig, observer)
    sessions(peerId) = pc

    val remote = new RTCSessionDescription(RTCSdpType.OFFER, offer.sdp)
    pc.setRemoteDescription(remote, new SetSessionDescriptionObserver {
      override def onSuccess(): Unit =
        pc.createAnswer(new RTCAnswerOptions(), new CreateSessionDescriptionObserver {
          override def onSuccess(desc: RTCSessionDescription): Unit =
            pc.setLocalDescription(desc, new SetSessionDescriptionObserver {
              override def onSuccess(): Unit =
                onAnswer(MediaAnswer(desc.sdp))
              override def onFailure(error: String): Unit = ()
            })
          override def onFailure(error: String): Unit = ()
        })
      override def onFailure(error: String): Unit =
        system.log.error(s"setRemoteDescription failed: $error")
    })
  }

  def addIceCandidate(peerId: String, cand: IceCandidate): Unit =
    sessions.get(peerId).foreach { pc =>
      val ice = new RTCIceCandidate(cand.sdpMid, cand.sdpMLineIndex, cand.candidate)
      pc.addIceCandidate(ice)
    }

  def stopSession(peerId: String): Unit = {
    sessions.get(peerId).foreach(_.close())
    sessions.remove(peerId)
    desktopSources.get(peerId).foreach(_.stop())
    desktopSources.remove(peerId)
  }

  def close(): Unit = {
    sessions.keys.foreach(stopSession)
    factory.dispose()
  }

  private def addAudioTrack(pc: RTCPeerConnection): Unit = {
    val audioSource = factory.createAudioSource(new AudioOptions())
    val audioTrack = factory.createAudioTrack("audio", audioSource)
    pc.addTrack(audioTrack, java.util.List.of("stream"))
  }

  private def addCameraTrack(pc: RTCPeerConnection): Unit = {
    val devices = VideoCapture.getVideoDevices
    if (!devices.isEmpty) {
      val capturer = new VideoCapture()
      capturer.setVideoCaptureDevice(devices.get(0))
      val cap = new VideoCaptureCapability()
      cap.width = 1920
      cap.height = 1080
      cap.maxFPS = 30
      capturer.setVideoCaptureCapability(cap)
      val source = factory.createVideoSource()
      val track = factory.createVideoTrack("camera", source)
      pc.addTrack(track, java.util.List.of("stream"))
    } else {
      system.log.warn("No camera devices found")
    }
  }

  private def addDesktopTrack(pc: RTCPeerConnection, peerId: String, gameMode: Boolean): Unit = {
    val screenCapturer = new ScreenCapturer()
    val screens = screenCapturer.getDesktopSources
    screenCapturer.dispose()

    if (screens.isEmpty) {
      system.log.warn("No screens available for capture")
      return
    }

    val source = new VideoDesktopSource()
    source.setFrameRate(if (gameMode) 60 else 30)
    source.setMaxFrameSize(1920, 1080)
    source.setSourceId(screens.get(0).id, false)
    source.start()
    desktopSources(peerId) = source

    val videoSource = factory.createVideoSource()
    val track = factory.createVideoTrack(if (gameMode) "game" else "screen", videoSource)
    pc.addTrack(track, java.util.List.of("stream"))

    val pm = new PowerManagement()
    pm.enableUserActivity()
  }

  def listDesktopSources(): (Seq[DesktopSource], Seq[DesktopSource]) = {
    val sc = new ScreenCapturer()
    val wc = new WindowCapturer()
    val screens = sc.getDesktopSources.asScala.toSeq
    val windows = wc.getDesktopSources.asScala.toSeq
    sc.dispose()
    wc.dispose()
    (screens, windows)
  }
}
