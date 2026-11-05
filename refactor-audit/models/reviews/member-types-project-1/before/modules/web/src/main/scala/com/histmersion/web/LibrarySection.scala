package com.histmersion.web

import scala.scalajs.js

/** Library's sections: the two lists, and the three ways to add sources. */
enum LibrarySection(val slug: String, val label: String):
  case Sources extends LibrarySection("", "Sources")
  case Articles extends LibrarySection("articles", "Articles")
  case Upload extends LibrarySection("upload", "Upload a file")
  case Doi extends LibrarySection("doi", "Add by DOI")
  case Merge extends LibrarySection("merge", "Merge another collection")

  /** One of the two lists (the filter panel beside it), not a way to add sources. */
  def isList: Boolean = this == Sources || this == Articles
