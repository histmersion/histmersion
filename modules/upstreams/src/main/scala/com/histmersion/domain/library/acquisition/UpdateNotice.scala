package com.histmersion.domain.library.acquisition
final case class UpdateNotice(kind: Option[UpdateNotice.Kind], label: Option[UpdateNotice.Label], doi: Option[com.histmersion.plugin.Doi])

object UpdateNotice:
  type Kind = Kind.T
  object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
