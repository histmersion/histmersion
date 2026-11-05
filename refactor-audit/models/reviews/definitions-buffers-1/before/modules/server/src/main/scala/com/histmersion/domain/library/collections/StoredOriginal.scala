package com.histmersion.domain.library.collections

/** A source's original as the server stores it: its bytes and the `Content-Disposition` it is served with (which names
  * its format).
  */
final case class StoredOriginal(bytes: Array[StoredOriginal.Bytes], disposition: StoredOriginal.Disposition)

object StoredOriginal:
  type Bytes = Bytes.T
  object Bytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Byte, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Disposition = Disposition.T
  object Disposition extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
