package com.example.comm.protocol

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import com.example.comm.storage.StoredMessage

class HistoryValidationSpec extends AnyFlatSpec with Matchers:

  private def msg(id: String, prev: String, payload: Array[Byte], ts: Long): StoredMessage =
    val ch = com.example.comm.storage.LocalStore.contentHash(prev, payload, ts)
    StoredMessage(id, "chat", "sender", payload, prev, ch, ts)

  "HistoryValidation" should "accept a valid chain" in:
    val m1 = msg("1", "genesis", "a".getBytes, 1L)
    val m2 = msg("2", m1.contentHash, "b".getBytes, 2L)
    HistoryValidation.verifyChain(Seq(m1, m2)) should be(true)

  it should "reject a broken chain" in:
    val m1 = msg("1", "genesis", "a".getBytes, 1L)
    val bad = m1.copy(contentHash = "tampered")
    HistoryValidation.verifyChain(Seq(bad)) should be(false)

  it should "accept an empty history" in:
    HistoryValidation.verifyChain(Seq.empty) should be(true)
