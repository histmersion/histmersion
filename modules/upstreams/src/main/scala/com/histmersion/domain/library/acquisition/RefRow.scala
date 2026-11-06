package com.histmersion.domain.library.acquisition
final case class RefRow(
    title: Option[RefRow.Title] = None,
    authors: List[RefRow.Authors] = Nil,
    year: Option[com.histmersion.plugin.Year] = None,
    doi: Option[com.histmersion.plugin.Doi] = None,
    raw: Option[RefRow.Raw] = None
)

object RefRow:
  type Title = com.histmersion.plugin.WorkKey.Title
  val Title: _root_.com.histmersion.plugin.WorkKey.Title.type = _root_.com.histmersion.plugin.WorkKey.Title
  type Authors = Authors.T
  object Authors extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Raw = Raw.T
  object Raw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
