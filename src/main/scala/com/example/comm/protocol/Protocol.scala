package com.example.comm.protocol

import com.example.comm.crypto.Crypto
import com.example.comm.crypto.Crypto.KeyPair
import java.nio.ByteBuffer

sealed trait MessageType
object MessageType {
  case object HandshakeInit extends MessageType
  case object HandshakeResp extends MessageType
  case object Chat extends MessageType
  case object GroupChat extends MessageType
  case object Ping extends MessageType
  case object Pong extends MessageType
  case object Relay extends MessageType
  case object MediaOffer extends MessageType
  case object MediaAnswer extends MessageType
  case object MediaIce extends MessageType
  case object MediaStop extends MessageType

  val values: Array[MessageType] = Array(
    HandshakeInit,
    HandshakeResp,
    Chat,
    GroupChat,
    Ping,
    Pong,
    Relay,
    MediaOffer,
    MediaAnswer,
    MediaIce,
    MediaStop
  )

  def fromOrdinal(i: Int): MessageType = values(i)
}

final case class Envelope(
    msgType: MessageType,
    senderId: String,
    recipientId: Option[String],
    groupId: Option[String],
    payload: Array[Byte],
    signature: Option[Array[Byte]] = None,
    timestamp: Long = System.currentTimeMillis()
)

object Protocol {
  def serialize(env: Envelope): Array[Byte] = {
    val payload = env.payload
    val header = ByteBuffer.allocate(1 + 36 + 4 + payload.length + 8)
    header.put(MessageType.values.indexOf(env.msgType).toByte)
    header.put(env.senderId.getBytes("UTF-8").padTo(36, 0.toByte))
    header.putInt(payload.length)
    header.put(payload)
    header.putLong(env.timestamp)
    header.array()
  }

  def deserialize(bytes: Array[Byte]): Option[Envelope] =
    try {
      val buf = ByteBuffer.wrap(bytes)
      val t = MessageType.fromOrdinal(buf.get())
      val idBytes = new Array[Byte](36)
      buf.get(idBytes)
      val senderId = new String(idBytes).trim
      val len = buf.getInt
      val payload = new Array[Byte](len)
      buf.get(payload)
      val ts = buf.getLong
      Some(Envelope(t, senderId, None, None, payload, None, ts))
    } catch {
      case _: Exception => None
    }

  def handshakeInit(myKeyPair: KeyPair, myId: String): Envelope =
    Envelope(MessageType.HandshakeInit, myId, None, None, myKeyPair.publicKey)

  def handshakeResp(myKeyPair: KeyPair, theirPub: Array[Byte], myId: String): (Envelope, Array[Byte]) = {
    val shared = Crypto.deriveSharedSecret(myKeyPair.privateKey, theirPub)
    val env = Envelope(MessageType.HandshakeResp, myId, None, None, myKeyPair.publicKey)
    (env, shared)
  }

  def mediaOffer(myId: String, peerId: String, sdp: String): Envelope =
    Envelope(MessageType.MediaOffer, myId, Some(peerId), None, sdp.getBytes("UTF-8"))

  def mediaAnswer(myId: String, peerId: String, sdp: String): Envelope =
    Envelope(MessageType.MediaAnswer, myId, Some(peerId), None, sdp.getBytes("UTF-8"))

  def mediaIce(myId: String, peerId: String, candidate: String, mid: String, mLine: Int): Envelope = {
    val payload = s"$mid|$mLine|$candidate"
    Envelope(MessageType.MediaIce, myId, Some(peerId), None, payload.getBytes("UTF-8"))
  }
}
