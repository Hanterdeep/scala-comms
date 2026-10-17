package com.example.comm.protocol

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import com.example.comm.crypto.{Crypto, SessionStore}
import com.example.comm.storage.LocalStore
import java.io.File

class EncryptedChatSpec extends AnyFlatSpec with Matchers {

  private def tempDb(): String = {
    val f = File.createTempFile("enc-chat-", ".db")
    f.deleteOnExit()
    f.getAbsolutePath
  }

  override def withFixture(test: NoArgTest) = {
    SessionStore.clear()
    try super.withFixture(test)
    finally SessionStore.clear()
  }

  "EncryptedChat" should "derive and store a session key on handshake" in {
    val a = Crypto.generateKeyPair()
    val b = Crypto.generateKeyPair()
    val key = EncryptedChat.performHandshake(a, b.publicKey, "me", "peer")
    key.length should be(32)
    SessionStore.getPeerKey("peer") should be(defined)
  }

  it should "encrypt and decrypt a chat message" in {
    val a = Crypto.generateKeyPair()
    val b = Crypto.generateKeyPair()
    EncryptedChat.performHandshake(a, b.publicKey, "me", "peer")
    val plain = "secret hello".getBytes
    val (iv, ct) = EncryptedChat.encryptChat("peer", plain).get
    val decrypted = EncryptedChat.decryptChat("peer", iv, ct).get
    decrypted should equal(plain)
  }

  it should "store an encrypted message in the local store" in {
    val a = Crypto.generateKeyPair()
    val b = Crypto.generateKeyPair()
    EncryptedChat.performHandshake(a, b.publicKey, "me", "peer")
    val store = new LocalStore(tempDb())
    store.init()
    val stored = EncryptedChat.sendAndStore(store, "chat1", "me", "peer", "hi".getBytes)
    stored should be(defined)
    store.validateHistory("chat1") should be(true)
  }

  it should "return None when no session key exists" in {
    EncryptedChat.encryptChat("unknown", "x".getBytes) should be(None)
  }
}
