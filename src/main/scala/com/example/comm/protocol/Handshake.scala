package com.example.comm.protocol

import com.example.comm.crypto.{Crypto, SessionStore}

/**
 * Client-side handshake and encrypted chat. Public keys and session keys stay on the client.
 */
object Handshake {
  case class Hello(pubKey: Array[Byte])
  case class HelloAck(pubKey: Array[Byte])

  def initiate(myPrivate: Array[Byte], myPublic: Array[Byte]): Hello =
    Hello(myPublic)

  def respond(theirPublic: Array[Byte], myPrivate: Array[Byte], myPublic: Array[Byte]): (HelloAck, Array[Byte]) = {
    val shared = Crypto.deriveSharedSecret(myPrivate, theirPublic)
    (HelloAck(myPublic), shared)
  }

  def complete(theirPublic: Array[Byte], myPrivate: Array[Byte]): Array[Byte] =
    Crypto.deriveSharedSecret(myPrivate, theirPublic)

  def storePeerSession(peerId: String, sessionKey: Array[Byte]): Unit =
    SessionStore.putPeerKey(peerId, sessionKey)

  def encryptForPeer(peerId: String, plaintext: Array[Byte]): Option[(Array[Byte], Array[Byte])] =
    SessionStore.getPeerKey(peerId).map { key =>
      Crypto.aesGcmEncrypt(key, plaintext)
    }

  def decryptFromPeer(peerId: String, iv: Array[Byte], ciphertext: Array[Byte]): Option[Array[Byte]] =
    SessionStore.getPeerKey(peerId).flatMap { key =>
      Crypto.aesGcmDecrypt(key, iv, ciphertext).toOption
    }
}
