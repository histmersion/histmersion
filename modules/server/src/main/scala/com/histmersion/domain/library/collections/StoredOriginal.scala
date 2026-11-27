package com.histmersion
package domain.library.collections

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.domain.library.acquisition.Spool

/** A source's original as the server stores it: its bytes and the `Content-Disposition` it is served with (which names
  * its format).
  */
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class StoredOriginal(bytes: StoredOriginal.Bytes, disposition: StoredOriginal.Disposition)

object StoredOriginal:
  export Spool.Bytes
  type Disposition = Disposition.T
  object Disposition extends Newtype[String, Pure]
