package com.histmersion.cli

import scala.jdk.CollectionConverters.*

/** A field `calibre update` writes, by its `calibredb set_metadata --field` name. */
enum CalibreField(val name: String):
  case Title extends CalibreField("title")
  case Authors extends CalibreField("authors")
  case Pubdate extends CalibreField("pubdate")
  case Identifiers extends CalibreField("identifiers")
  case Tags extends CalibreField("tags")
  case Publisher extends CalibreField("publisher")
  case Comments extends CalibreField("comments")
