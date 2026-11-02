package com.histmersion.api.exchange
import com.histmersion.api.given
object Bundle:
  val Format = "histmersion-bundle"
  val FormatVersion = 1
  val Extension = ".hmx"
  val ManifestPath = "manifest.json"

  def blobPath(sha256: String): String = s"blobs/sha256/${sha256.take(2)}/$sha256"

  def blobSha(path: String): Option[String] =
    val prefix = "blobs/sha256/"
    Option
      .when(path.startsWith(prefix)) {
        val rest = path.drop(prefix.length)
        rest.drop(3)
      }
      .filter(s =>
        s.length == 64 && s.forall(c => c.isDigit || ('a' to 'f').contains(c)) &&
          path == blobPath(s)
      )
