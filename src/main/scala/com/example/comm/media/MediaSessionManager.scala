package com.example.comm.media

/**
 * WebRTC media session scaffolding.
 *
 * Real PeerConnection / track wiring is behind this interface so the signaling path
 * (SDP + ICE over Protocol envelopes) is stable. A concrete webrtc-java or native
 * binding can implement MediaEngine without changing the rest of the app.
 */
object MediaSessionManager {
  sealed trait MediaKind
  case object Audio extends MediaKind
  case object Video extends MediaKind
  case object Screen extends MediaKind

  case class Sdp(kind: String, sdp: String)
  case class IceCandidate(candidate: String, sdpMid: String, sdpMLineIndex: Int)

  trait MediaEngine {
    def createPeerConnection(stunServers: Seq[String]): String
    def addAudioTrack(pcId: String): Unit
    def addVideoTrack(pcId: String, width: Int, height: Int, fps: Int): Unit
    def addScreenTrack(pcId: String): Unit
    def createOffer(pcId: String): Sdp
    def createAnswer(pcId: String, offer: Sdp): Sdp
    def addIceCandidate(pcId: String, c: IceCandidate): Unit
    def close(pcId: String): Unit
  }

  class RecordingEngine extends MediaEngine {
    private val pcs = scala.collection.mutable.Map.empty[String, String]
    def createPeerConnection(stunServers: Seq[String]): String = {
      val id = java.util.UUID.randomUUID().toString
      pcs(id) = stunServers.mkString(",")
      id
    }
    def addAudioTrack(pcId: String): Unit = ()
    def addVideoTrack(pcId: String, width: Int, height: Int, fps: Int): Unit = ()
    def addScreenTrack(pcId: String): Unit = ()
    def createOffer(pcId: String): Sdp = Sdp("offer", "v=0\r\no=- 0 0 IN IP4 127.0.0.1\r\ns=-\r\nt=0 0\r\n")
    def createAnswer(pcId: String, offer: Sdp): Sdp = Sdp("answer", offer.sdp)
    def addIceCandidate(pcId: String, c: IceCandidate): Unit = ()
    def close(pcId: String): Unit = pcs.remove(pcId)
  }

  def defaultEngine: MediaEngine = new RecordingEngine

  def startCall(engine: MediaEngine, kinds: Seq[MediaKind], stun: Seq[String] = Seq("stun:stun.l.google.com:19302")): (String, Sdp) = {
    val pc = engine.createPeerConnection(stun)
    if (kinds.contains(Audio)) engine.addAudioTrack(pc)
    if (kinds.contains(Video)) engine.addVideoTrack(pc, 1920, 1080, 30)
    if (kinds.contains(Screen)) engine.addScreenTrack(pc)
    (pc, engine.createOffer(pc))
  }
}
