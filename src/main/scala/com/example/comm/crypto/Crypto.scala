package com.example.comm.crypto

import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator
import org.bouncycastle.crypto.params.{X25519KeyGenerationParameters, X25519PrivateKeyParameters, X25519PublicKeyParameters}
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.{SecureRandom, Security}
import javax.crypto.Cipher
import javax.crypto.spec.{GCMParameterSpec, SecretKeySpec}
import scala.util.Try

object Crypto {
  Security.addProvider(new BouncyCastleProvider)

  case class KeyPair(publicKey: Array[Byte], privateKey: Array[Byte])

  def generateKeyPair(): KeyPair = {
    val gen = new X25519KeyPairGenerator
    gen.init(new X25519KeyGenerationParameters(new SecureRandom))
    val kp = gen.generateKeyPair()
    val pub = kp.getPublic.asInstanceOf[X25519PublicKeyParameters].getEncoded
    val priv = kp.getPrivate.asInstanceOf[X25519PrivateKeyParameters].getEncoded
    KeyPair(pub, priv)
  }

  def deriveSharedSecret(myPrivate: Array[Byte], theirPublic: Array[Byte]): Array[Byte] = {
    val agreement = new X25519Agreement
    agreement.init(new X25519PrivateKeyParameters(myPrivate, 0))
    val shared = new Array[Byte](agreement.getAgreementSize)
    agreement.calculateAgreement(new X25519PublicKeyParameters(theirPublic, 0), shared, 0)
    shared
  }

  def aesGcmEncrypt(key: Array[Byte], plaintext: Array[Byte], aad: Array[Byte] = Array.empty): (Array[Byte], Array[Byte]) = {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding", "BC")
    val secretKey = new SecretKeySpec(key.take(32), "AES")
    val iv = new Array[Byte](12)
    new SecureRandom().nextBytes(iv)
    cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(128, iv))
    if (aad.nonEmpty) cipher.updateAAD(aad)
    val ciphertext = cipher.doFinal(plaintext)
    (iv, ciphertext)
  }

  def aesGcmDecrypt(key: Array[Byte], iv: Array[Byte], ciphertext: Array[Byte], aad: Array[Byte] = Array.empty): Try[Array[Byte]] =
    Try {
      val cipher = Cipher.getInstance("AES/GCM/NoPadding", "BC")
      val secretKey = new SecretKeySpec(key.take(32), "AES")
      cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(128, iv))
      if (aad.nonEmpty) cipher.updateAAD(aad)
      cipher.doFinal(ciphertext)
    }

  def fingerprint(pubKey: Array[Byte]): String =
    java.util.Base64.getEncoder.encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(pubKey)).take(16)
}
