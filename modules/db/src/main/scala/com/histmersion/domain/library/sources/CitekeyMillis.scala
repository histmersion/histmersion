package com.histmersion.domain.library.sources

import com.augustnagro.magnum.*
import com.histmersion.util.database.NewtypeColumns.given

/** A source's last write, in epoch milliseconds. */
final private[sources] case class CitekeyMillis(citekey: com.histmersion.domain.library.Citekey, millis: CitekeyMillis.Millis) derives DbCodec

object CitekeyMillis:
  type Millis = Millis.T
  object Millis extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
