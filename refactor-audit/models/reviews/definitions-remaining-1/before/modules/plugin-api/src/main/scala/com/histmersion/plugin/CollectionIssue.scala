package com.histmersion.plugin

/** One thing an audit found wrong with a linked item: a short code (`Missing`, `Title`, …) and a sentence for a person.
  */
final case class CollectionIssue(code: String, label: String)
