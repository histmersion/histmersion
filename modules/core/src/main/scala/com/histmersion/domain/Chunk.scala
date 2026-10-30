package com.histmersion.domain

/** One indexable slice of a source body. `heading` is the breadcrumb of the Markdown headings the slice sits under
  * ("Introduction > Chronology"), `body` the text without those heading lines.
  */
final case class Chunk(ord: Int, heading: Option[String], body: String)
