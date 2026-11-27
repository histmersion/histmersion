package com.histmersion
package domain.library.collections

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** A source's original as the server stores it: its bytes and the `Content-Disposition` it is served with (which names
  * its format).
  */
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class StoredOriginal(bytes: StoredOriginal.Bytes, disposition: StoredOriginal.Disposition)

object StoredOriginal:
  type Bytes = Bytes.T
  object Bytes extends Newtype[Array[Byte], Pure]
  type Disposition = Disposition.T
  object Disposition extends Newtype[String, Pure]
