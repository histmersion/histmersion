package com.histmersion
package api.catalogue
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure
enum LinkScheme derives CanEqual:
  case Pleiades, Wikidata, Geonames, Tgn, Whg

  def wire: LinkScheme.Wire = LinkScheme.Wire(toString.toLowerCase(java.util.Locale.ROOT))

object LinkScheme:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  def parse(s: Wire): Option[LinkScheme] = values.find(_.wire == Wire((s).value.trim.toLowerCase(java.util.Locale.ROOT)))
