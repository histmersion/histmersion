package com.histmersion.upstreams.wikidata
final case class WikidataItem(id: String, statements: List[WikidataStatement], commonsCategory: Option[String])
