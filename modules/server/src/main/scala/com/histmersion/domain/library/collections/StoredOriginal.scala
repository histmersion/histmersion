package com.histmersion
package domain.library.collections

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** A source's original as the server stores it: its bytes and the `Content-Disposition` it is served with (which names
  * its format).
  */
final case class StoredOriginal(bytes: StoredOriginal.Bytes, disposition: StoredOriginal.Disposition)

object StoredOriginal:
  type Bytes = Bytes.T
  object Bytes extends RefinedType[Array[Byte], Pure]
  type Disposition = Disposition.T
  object Disposition extends RefinedType[String, Pure]
