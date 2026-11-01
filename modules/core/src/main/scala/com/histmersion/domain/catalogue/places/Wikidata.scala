package com.histmersion.domain.catalogue.places

import java.util.Locale

/** Wikidata item ids, as the gazetteer and the catalogues carry them. Pure. */
object Wikidata:

  val CommonsCategory = "P373"

  def isQid(s: String): Boolean = s.matches("Q[1-9][0-9]*")

  /** Pure: the item a gazetteer place id names (`wikidata_q132564` → `Q132564`). */
  def qidOfPlaceId(id: String): Option[String] =
    val s = id.trim.toLowerCase(Locale.ROOT)
    Option.when(s.startsWith("wikidata_q"))("Q" + s.drop("wikidata_q".length)).filter(isQid)
