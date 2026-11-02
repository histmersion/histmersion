package com.histmersion.api.catalogue
import com.histmersion.api.given
enum LinkScheme:
  case Pleiades, Wikidata, Geonames, Tgn, Whg

  def wire: String = toString.toLowerCase(java.util.Locale.ROOT)

object LinkScheme:
  def parse(s: String): Option[LinkScheme] = values.find(_.wire == s.trim.toLowerCase(java.util.Locale.ROOT))
