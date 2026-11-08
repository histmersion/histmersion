package com.histmersion
package domain.library.sources

import com.augustnagro.magnum.*
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.domain.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure

/** A source's last write, in epoch milliseconds. */
final private[sources] case class CitekeyMillis(citekey: Citekey, millis: CitekeyMillis.Millis) derives DbCodec

object CitekeyMillis:
  type Millis = Millis.T
  object Millis extends Newtype[Long, Pure]
