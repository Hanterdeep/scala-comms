package com.example.comm.protocol

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import com.example.comm.crypto.{Crypto, SessionStore}

class HandshakeSpec extends AnyFlatSpec with Matchers {

  "Handshake" should "derive matching session keys" in {
    SessionStore.clear()
    val a = Crypto.generateKeyPair()
    val b = Crypto.generateKeyPair()
    val hello = Handshake.initiate(a.privateKey, a.publicKey)
    val (ack, bShared) = Handshake.respond(hello.pubKey, b.privateKey, b.publicKey)
    val aShared = Handshake.complete(ack.pubKey, a.privateKey)
    aShared.toSeq should be(bShared.toSeq)
  }

  it should "encrypt and decrypt for a peer" in {
    SessionStore.clear()
    val a = Crypto.generateKeyPair()
    val b = Crypto.generateKeyPair()
    val shared = Crypto.deriveSharedSecret(a.privateKey, b.publicKey)
    Handshake.storePeerSession("peer-b", shared)
    val plain = "secret".getBytes
    val enc = Handshake.encryptForPeer("peer-b", plain)
    enc should be(defined)
    val (iv, ct) = enc.get
    val dec = Handshake.decryptFromPeer("peer-b", iv, ct)
    dec.map(_.toSeq) should be(Some(plain.toSeq))
  }

  it should "not store keys on the server side" in {
    SessionStore.clear()
    Handshake.storePeerSession("local-only", Array.fill(32)(1.toByte))
    SessionStore.getPeerKey("local-only") should be(defined)
  }
}
