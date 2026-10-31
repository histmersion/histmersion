package com.histmersion.util.storage

import java.io.InputStream

/** A content-addressed set of files, each kept as `<sha256>.<ext>`: found by its digest, and stored only when its
  * content hashes to its name.
  */
trait ContentStore:
  /** The file with this digest, and its extension. */
  def find(sha256: String): Option[(ByteSource, String)]

  /** Keep `in` as `<sha256>.<ext>` unless a file with the digest is there already; `Left` with why when it could not
    * be — including content that does not hash to `sha256`.
    */
  def store(sha256: String, ext: String, in: InputStream): Either[String, Unit]

object ContentStore:
  /** An extension safe in a file name: short and alphanumeric, else `bin`. Pure. */
  def safeExt(ext: String): String =
    val e = ext.trim.toLowerCase(java.util.Locale.ROOT)
    if e.nonEmpty && e.length <= 8 && e.forall(_.isLetterOrDigit) then e else "bin"

  /** The lower-case hex of a digest. Pure. */
  def hex(digest: Array[Byte]): String = java.util.HexFormat.of().formatHex(digest)
