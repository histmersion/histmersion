package com.histmersion.util.data.exchange

import java.io.OutputStream

/** A blob a bundle carries for a kind's lines: its digest, its size, and where its bytes are read from as it is written
  * into the archive.
  */
final case class BlobOut(sha256: BlobOut.Sha256, size: BlobOut.Size, copy: OutputStream => Unit)

object BlobOut:
  type Sha256 = Sha256.T
  object Sha256 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Size = Size.T
  object Size extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
