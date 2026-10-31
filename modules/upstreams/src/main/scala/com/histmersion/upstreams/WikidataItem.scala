package com.histmersion.upstreams

/** A Wikidata item's picture statements and its Commons category (P373). */
final case class WikidataItem(id: String, statements: List[WikidataStatement], commonsCategory: Option[String])
