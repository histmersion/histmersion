package com.histmersion
package plugins.media

import com.histmersion.plugin.GazetteerRef

import com.histmersion.plugin.MediaItem
import com.histmersion.plugin.GazetteerPlace
import com.histmersion.lint.{tolerated, LintRule}

/** Gazetteer ids in the URI forms providers write them: `(scheme, id)` of a Pleiades, GeoNames or Wikidata URI, as a
  * [[com.histmersion.plugin.MediaItem]]'s `ids` carry them. Pure.
  */
object GazetteerUri:

  private val PleiadesUri = """^https?://pleiades\.stoa\.org/places/([0-9]+)(?:[/#?].*)?$""".r
  private val GeoNamesUri = """^https?://(?:www\.|sws\.)?geonames\.org/([0-9]+)(?:[/#?].*)?$""".r
  private val WikidataUri = """^https?://(?:www\.)?wikidata\.org/(?:entity|wiki)/(Q[1-9][0-9]*)(?:[/#?].*)?$""".r

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def of(uri: GazetteerPlace.Id): Option[(GazetteerRef.Scheme, GazetteerRef.Id)] =
    Option(uri).map(_.value.trim).getOrElse("") match
      case PleiadesUri(id) => Some(GazetteerRef.Scheme("pleiades") -> GazetteerRef.Id(id))
      case GeoNamesUri(id) => Some(GazetteerRef.Scheme("geonames") -> GazetteerRef.Id(id))
      case WikidataUri(id) => Some(GazetteerRef.Scheme("wikidata") -> GazetteerRef.Id(id))
      case _               => None
