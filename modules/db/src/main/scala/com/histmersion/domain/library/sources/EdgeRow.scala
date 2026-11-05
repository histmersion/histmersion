package com.histmersion.domain.library.sources

import com.augustnagro.magnum.*

/** An edge's row: the source that makes it, its target, its kind. */
final private[library] case class EdgeRow(src: EdgeRow.Src, dst: EdgeRow.Dst, kind: EdgeRow.Kind) derives DbCodec

object EdgeRow:
  type Src = Src.T
  object Src extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Dst = Dst.T
  object Dst extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Kind = Kind.T
  object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
