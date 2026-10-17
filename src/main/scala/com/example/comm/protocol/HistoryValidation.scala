package com.example.comm.protocol

import com.example.comm.storage.{LocalStore, StoredMessage}
import java.security.MessageDigest

/**
 * Verifiable history helpers.
 * Clients can exchange StoredMessage sequences and independently
 * validate the hash chain (no reliance on the server).
 */
object HistoryValidation:
  def verifyChain(messages: Seq[StoredMessage]): Boolean =
    if messages.isEmpty then true
    else
      var expectedPrev = "genesis"
      messages.forall { m =>
        val recomputed = LocalStore.contentHash(m.prevHash, m.encryptedPayload, m.timestamp)
        val ok = m.prevHash == expectedPrev && m.contentHash == recomputed
        expectedPrev = m.contentHash
        ok
      }

  def mergeHistories(local: Seq[StoredMessage], remote: Seq[StoredMessage]): Seq[StoredMessage] =
    // Prefer the longer valid chain; in practice use vector clocks or timestamps + signatures
    val all = (local ++ remote).distinctBy(_.id).sortBy(_.timestamp)
    if verifyChain(all) then all else local  // fall back to local if merge breaks chain
