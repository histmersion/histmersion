package com.histmersion.domain.media.imports

/** A place or event to ask a provider about: its Wikidata item (empty when the import needs none) and its name (for the
  * report).
  */
final case class Target(targetType: String, id: String, qid: String, label: String)
