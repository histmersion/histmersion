package com.histmersion
package domain.media.assets

import io.github.iltotore.iron.constraint.any.Pure

/** One image figure of a source, as the illustration's choice weighs it: uid, name, size. */
final case class SourceFigure(
    uid: SourceFigure.Uid,
    name: SourceFigure.Name,
    width: Option[SourceFigure.Width],
    height: Option[SourceFigure.Height],
    hasThumb: SourceFigure.HasThumb
)

object SourceFigure:
  export MediaItemRow.Uid
  export AssetInfoRow.Name
  type Width = Width.T
  object Width extends Newtype[Int, Pure]
  type Height = Height.T
  object Height extends Newtype[Int, Pure]
  type HasThumb = HasThumb.T
  object HasThumb extends Newtype[Boolean, Pure]
