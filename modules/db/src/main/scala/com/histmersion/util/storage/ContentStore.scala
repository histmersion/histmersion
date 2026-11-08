package com.histmersion
package util.storage

import java.io.InputStream
import io.github.iltotore.iron.constraint.any.Pure

/** A content-addressed set of files, each kept as `<sha256>.<ext>`: found by its digest, and stored only when its
  * content hashes to its name.
  */
trait ContentStore:
  /** The file with this digest, and its extension. */
  def find(sha256: Sha256): Option[(ByteSource, ContentStore.Ext)]

  /** Keep `in` as `<sha256>.<ext>` unless a file with the digest is there already; `Left` with why when it could not be
    * — including content that does not hash to `sha256`.
    */
  def store(sha256: Sha256, ext: ContentStore.Ext, in: InputStream): Either[ContentStore.Problem, Unit]

object ContentStore:
  type Digest = Digest.T
  object Digest extends Newtype[Array[Byte], Pure]
  type Ext = Ext.T
  object Ext extends Newtype[String, Pure]
  type Problem = Problem.T
  object Problem extends Newtype[String, Pure]
  /** A server that stores no originals. */


  val none: ContentStore = new ContentStore:
    def find(sha256: Sha256): Option[(ByteSource, Ext)] = None
    def store(sha256: Sha256, ext: Ext, in: InputStream): Either[Problem, Unit] =
      Left(Problem("this server stores no originals"))

  /** An extension safe in a file name: short and alphanumeric, else `bin`. Pure. */
  def safeExt(ext: Ext): Ext =
    val e = ext.value.trim.toLowerCase(java.util.Locale.ROOT)
    if e.nonEmpty && e.length <= 8 && e.forall(_.isLetterOrDigit) then Ext(e) else Ext("bin")

  /** The lower-case hex of a digest. Pure. */
  def hex(digest: Digest): Sha256 = Sha256.applyUnsafe(java.util.HexFormat.of().formatHex(digest.value))
