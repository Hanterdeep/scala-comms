package com.example.comm.crypto

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CryptoSpec extends AnyFlatSpec with Matchers {

  "Crypto" should "generate a key pair" in {
    val kp = Crypto.generateKeyPair()
    kp.publicKey should not be empty
    kp.privateKey should not be empty
    kp.publicKey.length should be > 0
  }

  it should "derive the same shared secret from both sides" in {
    val a = Crypto.generateKeyPair()
    val b = Crypto.generateKeyPair()
    val sharedAB = Crypto.deriveSharedSecret(a.privateKey, b.publicKey)
    val sharedBA = Crypto.deriveSharedSecret(b.privateKey, a.publicKey)
    sharedAB should equal(sharedBA)
    sharedAB.length should be > 0
  }

  it should "encrypt and decrypt with AES-GCM" in {
    val key = Array.fill(32)(1.toByte)
    val plaintext = "hello world".getBytes("UTF-8")
    val (iv, ct) = Crypto.aesGcmEncrypt(key, plaintext)
    val decrypted = Crypto.aesGcmDecrypt(key, iv, ct).get
    decrypted should equal(plaintext)
  }

  it should "produce a short fingerprint" in {
    val kp = Crypto.generateKeyPair()
    val fp = Crypto.fingerprint(kp.publicKey)
    fp.length should be(16)
  }
}
