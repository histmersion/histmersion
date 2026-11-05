package com.histmersion.domain.media.assets

/** One image figure of a source, as the illustration's choice weighs it: uid, name, size. */
final case class SourceFigure(uid: SourceFigure.Uid, name: SourceFigure.Name, width: Option[SourceFigure.Width], height: Option[SourceFigure.Height], hasThumb: SourceFigure.HasThumb)

object SourceFigure:
  type Uid = Uid.T
  object Uid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Name = Name.T
  object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Width = Width.T
  object Width extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Height = Height.T
  object Height extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HasThumb = HasThumb.T
  object HasThumb extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
