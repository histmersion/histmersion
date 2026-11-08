package com.histmersion
package domain.media.assets

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** One image figure of a source, as the illustration's choice weighs it: uid, name, size. */
final case class SourceFigure(uid: SourceFigure.Uid, name: SourceFigure.Name, width: Option[SourceFigure.Width], height: Option[SourceFigure.Height], hasThumb: SourceFigure.HasThumb)

object SourceFigure:
  type Uid = Uid.T
  object Uid extends RefinedType[String, Pure]
  type Name = Name.T
  object Name extends RefinedType[String, Pure]
  type Width = Width.T
  object Width extends RefinedType[Int, Pure]
  type Height = Height.T
  object Height extends RefinedType[Int, Pure]
  type HasThumb = HasThumb.T
  object HasThumb extends RefinedType[Boolean, Pure]
