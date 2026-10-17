package com.example.comm.crypto

import java.util.concurrent.ConcurrentHashMap

/**
 * Client-side session key store. Never persisted on the server. Keys are derived from X25519 handshakes and used for
 * AES-GCM chat.
 */
object SessionStore {
  private val peerKeys = new ConcurrentHashMap[String, Array[Byte]]()
  private val groupKeys = new ConcurrentHashMap[String, Array[Byte]]()

  def putPeerKey(peerId: String, key: Array[Byte]): Unit =
    peerKeys.put(peerId, key)

  def getPeerKey(peerId: String): Option[Array[Byte]] =
    Option(peerKeys.get(peerId))

  def putGroupKey(groupId: String, key: Array[Byte]): Unit =
    groupKeys.put(groupId, key)

  def getGroupKey(groupId: String): Option[Array[Byte]] =
    Option(groupKeys.get(groupId))

  def clear(): Unit = {
    peerKeys.clear()
    groupKeys.clear()
  }
}
