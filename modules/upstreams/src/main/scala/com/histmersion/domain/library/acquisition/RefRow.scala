package com.histmersion.domain.library.acquisition
final case class RefRow(
    title: Option[RefRow.Title] = None,
    authors: List[RefRow.Authors] = Nil,
    year: Option[com.histmersion.Year] = None,
    doi: Option[com.histmersion.domain.library.Doi] = None,
    raw: Option[RefRow.Raw] = None
)

object RefRow:
  type Title = Title.T
  object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Authors = Authors.T
  object Authors extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Raw = Raw.T
  object Raw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
