package com.histmersion.web

import scala.scalajs.js

/** Library's sections: the two lists, and the three ways to add sources. */
enum LibrarySection(val slug: LibrarySection.Slug, val label: LibrarySection.Label):
  case Sources extends LibrarySection(com.histmersion.web.LibrarySection.Slug(""), com.histmersion.web.LibrarySection.Label("Sources"))
  case Articles extends LibrarySection(com.histmersion.web.LibrarySection.Slug("articles"), com.histmersion.web.LibrarySection.Label("Articles"))
  case Upload extends LibrarySection(com.histmersion.web.LibrarySection.Slug("upload"), com.histmersion.web.LibrarySection.Label("Upload a file"))
  case Doi extends LibrarySection(com.histmersion.web.LibrarySection.Slug("doi"), com.histmersion.web.LibrarySection.Label("Add by DOI"))
  case Merge extends LibrarySection(com.histmersion.web.LibrarySection.Slug("merge"), com.histmersion.web.LibrarySection.Label("Merge another collection"))

  /** One of the two lists (the filter panel beside it), not a way to add sources. */
  def isList: Boolean = this == Sources || this == Articles

object LibrarySection:
  type Slug = Slug.T
  object Slug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

