package com.histmersion.util.storage

import java.io.InputStream

/** A content-addressed set of files, each kept as `<sha256>.<ext>`: found by its digest, and stored only when its
  * content hashes to its name.
  */
trait ContentStore:
  /** The file with this digest, and its extension. */
  def find(sha256: com.histmersion.util.storage.ContentStore.FindSha256): Option[(ByteSource, com.histmersion.util.storage.ContentStore.FindResultPart2)]

  /** Keep `in` as `<sha256>.<ext>` unless a file with the digest is there already; `Left` with why when it could not be
    * — including content that does not hash to `sha256`.
    */
  def store(sha256: com.histmersion.util.storage.ContentStore.StoreSha256, ext: com.histmersion.util.storage.ContentStore.StoreExt, in: InputStream): Either[com.histmersion.util.storage.ContentStore.StoreResultPart1, Unit]

object ContentStore:
  /** A server that stores no originals. */
  type HexDigest = HexDigest.T
  object HexDigest extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]

  type FindResultPart2 = FindResultPart2.T
  object FindResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FindSha256 = FindSha256.T
  object FindSha256 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StoreResultPart1 = StoreResultPart1.T
  object StoreResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StoreSha256 = StoreSha256.T
  object StoreSha256 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StoreExt = StoreExt.T
  object StoreExt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SafeExtResult = SafeExtResult.T
  object SafeExtResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SafeExtExt = SafeExtExt.T
  object SafeExtExt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HexResult = HexResult.T
  object HexResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val none: ContentStore = new ContentStore:
    def find(sha256: com.histmersion.util.storage.ContentStore.FindSha256): Option[(ByteSource, com.histmersion.util.storage.ContentStore.FindResultPart2)] = None
    def store(sha256: com.histmersion.util.storage.ContentStore.StoreSha256, ext: com.histmersion.util.storage.ContentStore.StoreExt, in: InputStream): Either[com.histmersion.util.storage.ContentStore.StoreResultPart1, Unit] =
      Left("this server stores no originals")

  /** An extension safe in a file name: short and alphanumeric, else `bin`. Pure. */
  def safeExt(ext: com.histmersion.util.storage.ContentStore.SafeExtExt): com.histmersion.util.storage.ContentStore.SafeExtResult =
    val e = ext.trim.toLowerCase(java.util.Locale.ROOT)
    if e.nonEmpty && e.length <= 8 && e.forall(_.isLetterOrDigit) then e else "bin"

  /** The lower-case hex of a digest. Pure. */
  def hex(digest: com.histmersion.util.storage.ContentStore.HexDigest): com.histmersion.util.storage.ContentStore.HexResult = java.util.HexFormat.of().formatHex(digest)
