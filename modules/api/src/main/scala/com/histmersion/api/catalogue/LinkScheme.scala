package com.histmersion.api.catalogue
import com.histmersion.api.given
enum LinkScheme:
  case Pleiades, Wikidata, Geonames, Tgn, Whg

  def wire: com.histmersion.api.catalogue.LinkScheme.Wire = _root_.com.histmersion.api.catalogue.LinkScheme.Wire(toString.toLowerCase(java.util.Locale.ROOT))

object LinkScheme:
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def parse(s: com.histmersion.api.catalogue.LinkScheme.Wire): Option[LinkScheme] = values.find(_.wire == _root_.com.histmersion.api.catalogue.LinkScheme.Wire((s).value.trim.toLowerCase(java.util.Locale.ROOT)))
