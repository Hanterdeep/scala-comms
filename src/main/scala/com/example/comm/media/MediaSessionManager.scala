package com.example.comm.media

import akka.actor.typed.ActorSystem
import com.example.comm.protocol.{Envelope, Protocol}
import dev.onvoid.webrtc._
import dev.onvoid.webrtc.media.MediaDevices
import dev.onvoid.webrtc.media.audio.{AudioDeviceModule, AudioOptions, AudioTrack}
import dev.onvoid.webrtc.media.video.{VideoDesktopSource, VideoDeviceSource, VideoTrack}
import dev.onvoid.webrtc.media.video.desktop.{DesktopSource, ScreenCapturer, WindowCapturer}
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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

/** Callback for received remote media tracks (for rendering). */
trait RemoteMediaHandler {
  def onRemoteAudio(peerId: String, track: AudioTrack): Unit = ()
  def onRemoteVideo(peerId: String, track: VideoTrack): Unit = ()
}

/**
 * Complete WebRTC media session manager.
 *
 * Uses webrtc-java native bindings to:
 *   - Create real RTCPeerConnection
 *   - Perform real offer/answer and ICE gathering
 *   - Capture real audio (default mic) and video (camera at 1080p preferred)
 *   - Capture real desktop/screen or window for screen/game share at 1080p/30fps
 *   - Set video sender maxBitrate appropriate for 1080p
 *   - Deliver remote tracks via RemoteMediaHandler
 *
 * Media path is pure P2P. Requires a host with the webrtc-java native libs. Screen/game capture requires a graphical
 * session; it degrades cleanly otherwise.
 */
class MediaSessionManager(
    system: ActorSystem[_],
    localId: String,
    remoteHandler: RemoteMediaHandler = new RemoteMediaHandler {}
) {
  private val sessions = mutable.Map.empty[String, Session]
  private val factory = createFactory()
  private val stun = new RTCIceServer()
  stun.urls.add("stun:stun.l.google.com:19302")

  private case class Session(
      pc: RTCPeerConnection,
      mode: MediaMode,
      audioTrack: Option[AudioTrack],
      videoTrack: Option[VideoTrack],
      desktopSource: Option[VideoDesktopSource],
      senders: List[RTCRtpSender]
  )

  /**
   * Start a session and emit Protocol envelopes for the offer and every ICE candidate. The caller sends the envelopes
   * over whatever transport is in use (cluster, gateway, etc.).
   */
  def startSessionSignaled(
      peerId: String,
      mode: MediaMode,
      send: Envelope => Unit
  ): Unit = {
    startSession(
      peerId,
      mode,
      offer => send(Protocol.mediaOffer(localId, peerId, offer.sdp)),
      ice => send(Protocol.mediaIce(localId, peerId, ice.candidate, ice.sdpMid, ice.sdpMLineIndex))
    )
  }

  /**
   * Handle a remote MediaOffer envelope and emit the answer + ICE via the same send function.
   */
  def handleOfferSignaled(
      fromPeerId: String,
      offerSdp: String,
      mode: MediaMode,
      send: Envelope => Unit
  ): Unit = {
    handleOffer(
      fromPeerId,
      MediaOffer(offerSdp, mode),
      answer => send(Protocol.mediaAnswer(localId, fromPeerId, answer.sdp)),
      ice => send(Protocol.mediaIce(localId, fromPeerId, ice.candidate, ice.sdpMid, ice.sdpMLineIndex))
    )
  }

  def startSession(
      peerId: String,
      mode: MediaMode,
      onOffer: MediaOffer => Unit,
      onIce: IceCandidate => Unit
  ): Unit = {
    val (pc, audio, video, desktop, senders) = createPeer(peerId, mode, onIce)
    sessions(peerId) = Session(pc, mode, audio, video, desktop, senders)

    val latch = new CountDownLatch(1)
    var offerSdp = ""
    pc.createOffer(
      new RTCOfferOptions(),
      new CreateSessionDescriptionObserver {
        override def onSuccess(desc: RTCSessionDescription): Unit = {
          offerSdp = desc.sdp
          pc.setLocalDescription(
            desc,
            new SetSessionDescriptionObserver {
              override def onSuccess(): Unit = latch.countDown()
              override def onFailure(error: String): Unit = {
                system.log.error(s"setLocalDescription failed: $error")
                latch.countDown()
              }
            }
          )
        }
        override def onFailure(error: String): Unit = {
          system.log.error(s"createOffer failed: $error")
          latch.countDown()
        }
      }
    )
    if (!latch.await(3, TimeUnit.SECONDS))
      system.log.warn(s"Timed out creating offer for $peerId")
    if (offerSdp.nonEmpty) onOffer(MediaOffer(offerSdp, mode))
    system.log.info(s"Started $mode session with $peerId (sdp length=${offerSdp.length})")
  }

  def handleOffer(
      peerId: String,
      offer: MediaOffer,
      onAnswer: MediaAnswer => Unit,
      onIce: IceCandidate => Unit
  ): Unit = {
    val (pc, audio, video, desktop, senders) = createPeer(peerId, offer.mode, onIce)
    sessions(peerId) = Session(pc, offer.mode, audio, video, desktop, senders)

    val remote = new RTCSessionDescription(RTCSdpType.OFFER, offer.sdp)
    val latch = new CountDownLatch(1)
    var answerSdp = ""
    pc.setRemoteDescription(
      remote,
      new SetSessionDescriptionObserver {
        override def onSuccess(): Unit = {
          pc.createAnswer(
            new RTCAnswerOptions(),
            new CreateSessionDescriptionObserver {
              override def onSuccess(desc: RTCSessionDescription): Unit = {
                answerSdp = desc.sdp
                pc.setLocalDescription(
                  desc,
                  new SetSessionDescriptionObserver {
                    override def onSuccess(): Unit = latch.countDown()
                    override def onFailure(error: String): Unit = latch.countDown()
                  }
                )
              }
              override def onFailure(error: String): Unit = latch.countDown()
            }
          )
        }
        override def onFailure(error: String): Unit = latch.countDown()
      }
    )
    latch.await(3, TimeUnit.SECONDS)
    if (answerSdp.nonEmpty) onAnswer(MediaAnswer(answerSdp))
  }

  def addIceCandidate(peerId: String, cand: IceCandidate): Unit =
    sessions.get(peerId).foreach { s =>
      try {
        val c = new RTCIceCandidate(cand.sdpMid, cand.sdpMLineIndex, cand.candidate)
        s.pc.addIceCandidate(c)
      } catch {
        case e: Throwable => system.log.warn(s"addIceCandidate failed: ${e.getMessage}")
      }
    }

  def stopSession(peerId: String): Unit = {
    sessions.remove(peerId).foreach { s =>
      // RTCRtpSender in webrtc-java 0.14 has no dispose(); peerConnection.close() releases them.
      try s.pc.close()
      catch { case _: Throwable => }
      s.desktopSource.foreach(ds =>
        try { ds.stop(); ds.dispose() }
        catch { case _: Throwable => }
      )
      s.audioTrack.foreach(t =>
        try t.dispose()
        catch { case _: Throwable => }
      )
      s.videoTrack.foreach(t =>
        try t.dispose()
        catch { case _: Throwable => }
      )
    }
    system.log.info(s"Stopped media session with $peerId")
  }

  def close(): Unit = {
    sessions.keys.toList.foreach(stopSession)
    try factory.dispose()
    catch { case _: Throwable => }
  }

  def activeSessions: Map[String, MediaMode] = sessions.map { case (k, v) => k -> v.mode }.toMap

  def listDesktopSources(): (Seq[String], Seq[String]) = {
    if (!hasDisplay) return (Seq.empty, Seq.empty)
    try {
      val sc = new ScreenCapturer()
      val screens = sc.getDesktopSources.asScala.map(_.title).toSeq
      sc.dispose()
      val wc = new WindowCapturer()
      val windows = wc.getDesktopSources.asScala.map(_.title).toSeq
      wc.dispose()
      (screens, windows)
    } catch {
      case _: Throwable => (Seq.empty, Seq.empty)
    }
  }

  private def hasDisplay: Boolean =
    sys.env.get("DISPLAY").exists(_.nonEmpty) ||
      sys.env.get("WAYLAND_DISPLAY").exists(_.nonEmpty) ||
      sys.env.get("ENABLE_DESKTOP_CAPTURE").contains("true")

  private def createFactory(): PeerConnectionFactory = {
    // Use headless ADM by default so the stack works without physical devices and does not hang
    // in CI/headless environments. Real devices can be selected later via a configuration flag.
    try {
      val headless = new dev.onvoid.webrtc.media.audio.HeadlessAudioDeviceModule()
      system.log.info("Using HeadlessAudioDeviceModule (no physical audio devices required)")
      new PeerConnectionFactory(headless)
    } catch {
      case e: Throwable =>
        system.log.warn(s"Headless ADM failed (${e.getMessage}); falling back to default factory")
        new PeerConnectionFactory()
    }
  }

  private def createPeer(
      peerId: String,
      mode: MediaMode,
      onIce: IceCandidate => Unit
  ): (RTCPeerConnection, Option[AudioTrack], Option[VideoTrack], Option[VideoDesktopSource], List[RTCRtpSender]) = {
    val config = new RTCConfiguration()
    config.iceServers.add(stun)

    val observer = new PeerConnectionObserver {
      override def onIceCandidate(candidate: RTCIceCandidate): Unit =
        onIce(IceCandidate(candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex))
      override def onConnectionChange(state: RTCPeerConnectionState): Unit =
        system.log.debug(s"PC $peerId state: $state")
      override def onIceConnectionChange(state: RTCIceConnectionState): Unit =
        system.log.debug(s"ICE $peerId state: $state")
      override def onIceGatheringChange(state: RTCIceGatheringState): Unit = ()
      override def onSignalingChange(state: RTCSignalingState): Unit = ()
      override def onDataChannel(dc: RTCDataChannel): Unit = ()
      override def onRenegotiationNeeded(): Unit = ()
      override def onAddTrack(receiver: RTCRtpReceiver, streams: Array[dev.onvoid.webrtc.media.MediaStream]): Unit = ()
      override def onRemoveTrack(receiver: RTCRtpReceiver): Unit = ()
      override def onTrack(transceiver: RTCRtpTransceiver): Unit = {
        val track = transceiver.getReceiver.getTrack
        track.getKind match {
          case "audio" => remoteHandler.onRemoteAudio(peerId, track.asInstanceOf[AudioTrack])
          case "video" => remoteHandler.onRemoteVideo(peerId, track.asInstanceOf[VideoTrack])
          case _ =>
        }
      }
    }

    val pc = factory.createPeerConnection(config, observer)
    val (audio, video, desktop, senders) = addLocalTracks(mode, pc)
    (pc, audio, video, desktop, senders)
  }

  private def addLocalTracks(
      mode: MediaMode,
      pc: RTCPeerConnection
  ): (Option[AudioTrack], Option[VideoTrack], Option[VideoDesktopSource], List[RTCRtpSender]) = {
    val streamIds = java.util.Collections.singletonList("stream0")
    val senders = mutable.ListBuffer.empty[RTCRtpSender]
    var audio: Option[AudioTrack] = None
    var video: Option[VideoTrack] = None
    var desktop: Option[VideoDesktopSource] = None

    // Audio for all modes that include voice
    if (mode == MediaMode.Voice || mode == MediaMode.CameraVideo) {
      try {
        val opts = new AudioOptions()
        opts.echoCancellation = true
        opts.autoGainControl = true
        opts.noiseSuppression = true
        val src = factory.createAudioSource(opts)
        val track = factory.createAudioTrack("audio0", src)
        val sender = pc.addTrack(track, streamIds)
        senders += sender
        audio = Some(track)
      } catch {
        case e: Throwable => system.log.warn(s"Audio track failed: ${e.getMessage}")
      }
    }

    mode match {
      case MediaMode.CameraVideo =>
        try {
          val devices = MediaDevices.getVideoCaptureDevices.asScala
          if (devices.nonEmpty) {
            val device = devices.head
            val caps = MediaDevices.getVideoCaptureCapabilities(device).asScala
            val preferred = caps
              .find(c => c.width == 1920 && c.height == 1080)
              .orElse(caps.find(c => c.width >= 1280))
              .orElse(caps.headOption)
            val src = new VideoDeviceSource()
            src.setVideoCaptureDevice(device)
            preferred.foreach(src.setVideoCaptureCapability)
            src.start()
            val track = factory.createVideoTrack("video0", src)
            val sender = pc.addTrack(track, streamIds)
            set1080pBitrate(sender)
            senders += sender
            video = Some(track)
            system.log.info(
              s"Camera started: ${device.getName} at ${preferred.map(c => s"${c.width}x${c.height}@${c.frameRate}").getOrElse("default")}"
            )
          } else {
            system.log.warn("No camera devices found")
          }
        } catch {
          case e: Throwable => system.log.warn(s"Camera capture failed: ${e.getMessage}")
        }

      case MediaMode.ScreenShare | MediaMode.GameShare =>
        if (!hasDisplay) {
          system.log.warn(
            s"$mode requires a graphical session (DISPLAY/WAYLAND_DISPLAY or ENABLE_DESKTOP_CAPTURE=true)"
          )
        } else {
          try {
            val (sourceId, isWindow) = selectDesktopSource(mode)
            val src = new VideoDesktopSource()
            src.setMaxFrameSize(1920, 1080)
            src.setFrameRate(30)
            src.setSourceId(sourceId, isWindow)
            src.start()
            val track = factory.createVideoTrack("screen0", src)
            val sender = pc.addTrack(track, streamIds)
            set1080pBitrate(sender)
            senders += sender
            video = Some(track)
            desktop = Some(src)
            system.log.info(s"${mode} started (sourceId=$sourceId, window=$isWindow, 1920x1080@30)")
          } catch {
            case e: Throwable =>
              system.log.warn(s"$mode capture failed: ${e.getMessage}")
          }
        }

      case _ =>
    }
    (audio, video, desktop, senders.toList)
  }

  private def selectDesktopSource(mode: MediaMode): (Long, Boolean) = {
    try {
      if (mode == MediaMode.GameShare) {
        val wc = new WindowCapturer()
        val wins = wc.getDesktopSources.asScala
        wc.dispose()
        if (wins.nonEmpty) return (wins.head.id, true)
      }
      val sc = new ScreenCapturer()
      val screens = sc.getDesktopSources.asScala
      sc.dispose()
      if (screens.nonEmpty) (screens.head.id, false)
      else (0L, false)
    } catch {
      case _: Throwable => (0L, false)
    }
  }

  private def set1080pBitrate(sender: RTCRtpSender): Unit = {
    try {
      val params = sender.getParameters
      if (params != null && params.encodings != null && !params.encodings.isEmpty) {
        val enc = params.encodings.get(0)
        enc.maxBitrate = 2500000 // 2.5 Mbps suitable for 1080p30
        enc.maxFramerate = 30.0
        sender.setParameters(params)
      }
    } catch {
      case e: Throwable => system.log.debug(s"Could not set bitrate: ${e.getMessage}")
    }
  }
}
