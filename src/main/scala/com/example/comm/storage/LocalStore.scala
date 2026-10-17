package com.example.comm.storage

import com.example.comm.crypto.Crypto
import java.security.MessageDigest
import java.sql.{Connection, DriverManager, PreparedStatement, ResultSet}
import java.util.UUID
import scala.util.Try

/**
 * Local encrypted store + verifiable hash-chain history.
 * Each message links to the previous one via hash; clients can recompute
 * and verify that no message in the chain was altered or deleted.
 * Images are stored as encrypted blobs addressed by content hash.
 */
case class StoredMessage(
  id: String,
  chatId: String,          // peer id or group id
  senderId: String,
  encryptedPayload: Array[Byte],
  prevHash: String,        // hex SHA-256 of previous message (or "genesis")
  contentHash: String,     // hex SHA-256 of (prevHash + encryptedPayload + timestamp)
  timestamp: Long,
  isImage: Boolean = false,
  imageContentHash: Option[String] = None
)

object LocalStore:
  def sha256Hex(data: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(data).map("%02x".format(_)).mkString

  def contentHash(prevHash: String, payload: Array[Byte], ts: Long): String =
    val combined = (prevHash + ":" + ts).getBytes("UTF-8") ++ payload
    sha256Hex(combined)

class LocalStore(dbPath: String = "communicator.db"):
  private val url = s"jdbc:sqlite:$dbPath"
  Class.forName("org.sqlite.JDBC")

  private def withConn[T](f: Connection => T): T =
    val conn = DriverManager.getConnection(url)
    try f(conn) finally conn.close()

  def init(): Unit = withConn { conn =>
    val stmt = conn.createStatement()
    stmt.execute(
      """CREATE TABLE IF NOT EXISTS messages (
        id TEXT PRIMARY KEY,
        chat_id TEXT NOT NULL,
        sender_id TEXT NOT NULL,
        encrypted_payload BLOB NOT NULL,
        prev_hash TEXT NOT NULL,
        content_hash TEXT NOT NULL,
        timestamp INTEGER NOT NULL,
        is_image INTEGER DEFAULT 0,
        image_content_hash TEXT
      )"""
    )
    stmt.execute(
      """CREATE TABLE IF NOT EXISTS images (
        content_hash TEXT PRIMARY KEY,
        encrypted_blob BLOB NOT NULL,
        mime_type TEXT
      )"""
    )
    stmt.execute("CREATE INDEX IF NOT EXISTS idx_chat ON messages(chat_id, timestamp)")
  }

  def appendMessage(chatId: String, senderId: String, encrypted: Array[Byte], isImage: Boolean = false, imageHash: Option[String] = None): StoredMessage =
    withConn { conn =>
      val prev = lastHash(conn, chatId).getOrElse("genesis")
      val ts = System.currentTimeMillis()
      val cHash = LocalStore.contentHash(prev, encrypted, ts)
      val id = UUID.randomUUID().toString
      val ps = conn.prepareStatement(
        """INSERT INTO messages (id, chat_id, sender_id, encrypted_payload, prev_hash, content_hash, timestamp, is_image, image_content_hash)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"""
      )
      ps.setString(1, id)
      ps.setString(2, chatId)
      ps.setString(3, senderId)
      ps.setBytes(4, encrypted)
      ps.setString(5, prev)
      ps.setString(6, cHash)
      ps.setLong(7, ts)
      ps.setInt(8, if isImage then 1 else 0)
      ps.setString(9, imageHash.orNull)
      ps.executeUpdate()
      StoredMessage(id, chatId, senderId, encrypted, prev, cHash, ts, isImage, imageHash)
    }

  def lastHash(conn: Connection, chatId: String): Option[String] =
    val ps = conn.prepareStatement(
      "SELECT content_hash FROM messages WHERE chat_id = ? ORDER BY timestamp DESC LIMIT 1"
    )
    ps.setString(1, chatId)
    val rs = ps.executeQuery()
    if rs.next() then Some(rs.getString(1)) else None

  def loadHistory(chatId: String): Seq[StoredMessage] = withConn { conn =>
    val ps = conn.prepareStatement(
      "SELECT * FROM messages WHERE chat_id = ? ORDER BY timestamp ASC"
    )
    ps.setString(1, chatId)
    val rs = ps.executeQuery()
    val buf = scala.collection.mutable.ArrayBuffer.empty[StoredMessage]
    while rs.next() do
      buf += StoredMessage(
        rs.getString("id"),
        rs.getString("chat_id"),
        rs.getString("sender_id"),
        rs.getBytes("encrypted_payload"),
        rs.getString("prev_hash"),
        rs.getString("content_hash"),
        rs.getLong("timestamp"),
        rs.getInt("is_image") == 1,
        Option(rs.getString("image_content_hash"))
      )
    buf.toSeq
  }

  /**
   * Validates the entire chain for a chat.
   * Returns true only if every message's contentHash matches the recomputed
   * hash and links correctly to the previous message. Detects tampering or deletion.
   */
  def validateHistory(chatId: String): Boolean =
    val history = loadHistory(chatId)
    if history.isEmpty then return true
    var expectedPrev = "genesis"
    history.forall { msg =>
      val ok = msg.prevHash == expectedPrev &&
               msg.contentHash == LocalStore.contentHash(msg.prevHash, msg.encryptedPayload, msg.timestamp)
      expectedPrev = msg.contentHash
      ok
    }

  def storeImage(encryptedBlob: Array[Byte], mime: String = "application/octet-stream"): String =
    val hash = LocalStore.sha256Hex(encryptedBlob)
    withConn { conn =>
      val ps = conn.prepareStatement(
        "INSERT OR IGNORE INTO images (content_hash, encrypted_blob, mime_type) VALUES (?, ?, ?)"
      )
      ps.setString(1, hash)
      ps.setBytes(2, encryptedBlob)
      ps.setString(3, mime)
      ps.executeUpdate()
    }
    hash

  def loadImage(contentHash: String): Option[Array[Byte]] = withConn { conn =>
    val ps = conn.prepareStatement("SELECT encrypted_blob FROM images WHERE content_hash = ?")
    ps.setString(1, contentHash)
    val rs = ps.executeQuery()
    if rs.next() then Some(rs.getBytes(1)) else None
  }
