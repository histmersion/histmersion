package com.histmersion
package domain.library.sources

import com.augustnagro.magnum.*
import com.histmersion.util.database.NewtypeColumns.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** An edge's row: the source that makes it, its target, its kind. */
final private[library] case class EdgeRow(src: EdgeRow.Src, dst: EdgeRow.Dst, kind: EdgeRow.Kind) derives DbCodec

object EdgeRow:
  type Src = Src.T
  object Src extends RefinedType[String, Pure]
  type Dst = Dst.T
  object Dst extends RefinedType[String, Pure]
  type Kind = Kind.T
  object Kind extends RefinedType[String, Pure]
