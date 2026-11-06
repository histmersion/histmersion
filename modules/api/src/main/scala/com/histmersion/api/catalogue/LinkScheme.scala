package com.histmersion.api.catalogue
import com.histmersion.api.given
enum LinkScheme:
  case Pleiades, Wikidata, Geonames, Tgn, Whg

  def wire: com.histmersion.api.catalogue.LinkScheme.WireResult = _root_.com.histmersion.api.catalogue.LinkScheme.WireResult(toString.toLowerCase(java.util.Locale.ROOT))

object LinkScheme:
  type WireResult = WireResult.T
  object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseS = ParseS.T
  object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def parse(s: com.histmersion.api.catalogue.LinkScheme.ParseS): Option[LinkScheme] = values.find(_.wire == _root_.com.histmersion.api.catalogue.LinkScheme.WireResult((s).value.trim.toLowerCase(java.util.Locale.ROOT)))
