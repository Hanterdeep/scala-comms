package com.example.comm.protocol

import com.example.comm.storage.{LocalStore, StoredMessage}

object HistoryValidation {
  def verifyChain(messages: Seq[StoredMessage]): Boolean =
    if (messages.isEmpty) true
    else {
      var expectedPrev = "genesis"
      messages.forall { m =>
        val recomputed = LocalStore.contentHash(m.prevHash, m.encryptedPayload, m.timestamp)
        val ok = m.prevHash == expectedPrev && m.contentHash == recomputed
        expectedPrev = m.contentHash
        ok
      }
    }

  def mergeHistories(local: Seq[StoredMessage], remote: Seq[StoredMessage]): Seq[StoredMessage] = {
    val all = (local ++ remote).distinctBy(_.id).sortBy(_.timestamp)
    if (verifyChain(all)) all else local
  }
}
