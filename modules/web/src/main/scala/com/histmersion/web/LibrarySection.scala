package com.histmersion
package web

import scala.scalajs.js
import io.github.iltotore.iron.constraint.any.Pure

/** Library's sections: the two lists, and the three ways to add sources. */
enum LibrarySection(val slug: LibrarySection.Slug, val label: LibrarySection.Label) derives CanEqual:
  case Sources extends LibrarySection(LibrarySection.Slug(""), LibrarySection.Label("Sources"))
  case Articles extends LibrarySection(LibrarySection.Slug("articles"), LibrarySection.Label("Articles"))
  case Upload extends LibrarySection(LibrarySection.Slug("upload"), LibrarySection.Label("Upload a file"))
  case Doi extends LibrarySection(LibrarySection.Slug("doi"), LibrarySection.Label("Add by DOI"))
  case Merge extends LibrarySection(LibrarySection.Slug("merge"), LibrarySection.Label("Merge another collection"))

  /** One of the two lists (the filter panel beside it), not a way to add sources. */
  def isList: Boolean = this == Sources || this == Articles

object LibrarySection:
  type Slug = Slug.T
  object Slug extends Newtype[String, Pure]
  type Label = Label.T
  object Label extends Newtype[String, Pure]

