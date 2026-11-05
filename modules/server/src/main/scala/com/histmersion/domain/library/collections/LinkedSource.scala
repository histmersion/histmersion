package com.histmersion.domain.library.collections

/** A source linked to an item of the Calibre library (`sources.calibre_id`), with what an audit compares. */
final case class LinkedSource(citekey: com.histmersion.domain.library.Citekey, id: LinkedSource.Id, title: LinkedSource.Title, authors: List[LinkedSource.Authors], year: Option[com.histmersion.Year])

object LinkedSource:
  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Title = Title.T
  object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Authors = Authors.T
  object Authors extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
