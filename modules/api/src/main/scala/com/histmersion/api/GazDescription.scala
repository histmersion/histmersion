package com.histmersion.api

/** What a record says the place IS (LPF `descriptions[]`: Wikidata's schema:description, a dataset's own note), in one
  * language (`""` unknown).
  */
final case class GazDescription(text: String, lang: String = "")
