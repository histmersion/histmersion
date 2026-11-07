package com.histmersion.util.storage

import java.io.InputStream

/** A content-addressed set of files, each kept as `<sha256>.<ext>`: found by its digest, and stored only when its
  * content hashes to its name.
  */
trait ContentStore:
  /** The file with this digest, and its extension. */
  def find(sha256: com.histmersion.Sha256): Option[(ByteSource, com.histmersion.util.storage.ContentStore.Ext)]

  /** Keep `in` as `<sha256>.<ext>` unless a file with the digest is there already; `Left` with why when it could not be
    * — including content that does not hash to `sha256`.
    */
  def store(sha256: com.histmersion.Sha256, ext: com.histmersion.util.storage.ContentStore.Ext, in: InputStream): Either[com.histmersion.util.storage.ContentStore.Problem, Unit]

object ContentStore:
  type Digest = Digest.T
  object Digest extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Ext = Ext.T
  object Ext extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  /** A server that stores no originals. */


  val none: ContentStore = new ContentStore:
    def find(sha256: com.histmersion.Sha256): Option[(ByteSource, com.histmersion.util.storage.ContentStore.Ext)] = None
    def store(sha256: com.histmersion.Sha256, ext: com.histmersion.util.storage.ContentStore.Ext, in: InputStream): Either[com.histmersion.util.storage.ContentStore.Problem, Unit] =
      Left(com.histmersion.util.storage.ContentStore.Problem("this server stores no originals"))

  /** An extension safe in a file name: short and alphanumeric, else `bin`. Pure. */
  def safeExt(ext: com.histmersion.util.storage.ContentStore.Ext): com.histmersion.util.storage.ContentStore.Ext =
    val e = ext.trim.toLowerCase(java.util.Locale.ROOT)
    if e.nonEmpty && e.length <= 8 && e.forall(_.isLetterOrDigit) then e else com.histmersion.util.storage.ContentStore.Ext("bin")

  /** The lower-case hex of a digest. Pure. */
  def hex(digest: com.histmersion.util.storage.ContentStore.Digest): com.histmersion.Sha256 = java.util.HexFormat.of().formatHex(digest)
