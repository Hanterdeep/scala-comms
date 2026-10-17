package com.example.comm.protocol

import com.example.comm.crypto.{Crypto, SessionStore}
import com.example.comm.storage.LocalStore

/**
 * End-to-end encrypted chat helpers. Handshake derives a session key stored only on the client. Chat payloads are
 * AES-GCM encrypted before leaving the client.
 */
object EncryptedChat {
  def performHandshake(myKeys: Crypto.KeyPair, theirPub: Array[Byte], myId: String, peerId: String): Array[Byte] = {
    val shared = Crypto.deriveSharedSecret(myKeys.privateKey, theirPub)
    val sessionKey = shared.take(32)
    SessionStore.putPeerKey(peerId, sessionKey)
    sessionKey
  }

  def encryptChat(peerId: String, plaintext: Array[Byte]): Option[(Array[Byte], Array[Byte])] =
    SessionStore.getPeerKey(peerId).map { key =>
      Crypto.aesGcmEncrypt(key, plaintext)
    }

  def decryptChat(peerId: String, iv: Array[Byte], ciphertext: Array[Byte]): Option[Array[Byte]] =
    SessionStore.getPeerKey(peerId).flatMap { key =>
      Crypto.aesGcmDecrypt(key, iv, ciphertext).toOption
    }

  def sendAndStore(
      store: LocalStore,
      chatId: String,
      senderId: String,
      peerId: String,
      plaintext: Array[Byte]
  ): Option[com.example.comm.storage.StoredMessage] =
    encryptChat(peerId, plaintext).map { case (iv, ct) =>
      val blob = iv ++ ct
      store.appendMessage(chatId, senderId, blob)
    }
}
