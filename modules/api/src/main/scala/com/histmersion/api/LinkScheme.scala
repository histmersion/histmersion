package com.histmersion.api

/** An authority whose ids join places across sources. The wire spelling is `place_links.scheme`
  * ([[PlaceLink.Schemes]]).
  */
enum LinkScheme:
  case Pleiades, Wikidata, Geonames, Tgn, Whg

  def wire: String = toString.toLowerCase(java.util.Locale.ROOT)

object LinkScheme:
  def parse(s: String): Option[LinkScheme] = values.find(_.wire == s.trim.toLowerCase(java.util.Locale.ROOT))
