package com.histmersion.web

import scala.scalajs.js

/** Library's sections: the two lists, and the three ways to add sources. */
enum LibrarySection(val slug: LibrarySection.Slug, val label: LibrarySection.Label):
  case Sources extends LibrarySection("", "Sources")
  case Articles extends LibrarySection("articles", "Articles")
  case Upload extends LibrarySection("upload", "Upload a file")
  case Doi extends LibrarySection("doi", "Add by DOI")
  case Merge extends LibrarySection("merge", "Merge another collection")

  /** One of the two lists (the filter panel beside it), not a way to add sources. */
  def isList: com.histmersion.web.LibrarySection.IsListResult = this == Sources || this == Articles

object LibrarySection:
  type Slug = Slug.T
  object Slug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type IsListResult = IsListResult.T
  object IsListResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
