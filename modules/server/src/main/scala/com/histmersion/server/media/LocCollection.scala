package com.histmersion.server.media

import com.histmersion.pluginhost.{allowed, PluginMedia, Polite}

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.db.MediaRepo.PlaceFacts
import com.histmersion.values.MediaRole

/** The Library of Congress's maps: a search, then each item for its rights and files. A 403 is loc.gov's bot challenge
  * to automated clients — reported as such, never worked around.
  */
final class LocCollection(val conf: MediaCollectionConfig, thumbPx: Int) extends Collection:
  val provider = MediaProvider.LibraryOfCongress
  val role = MediaRole.Map
  private def explain(e: String): String =
    if e.contains("HTTP 403") then
      s"$e — loc.gov refuses automated clients with a bot challenge at the moment; not worked around"
    else e
  def search(fetch: Polite, place: PlaceFacts): Either[String, List[Either[String, MediaRecord]]] =
    fetch
      .json(LibraryOfCongress.searchUrl(conf.apiUrl, place.label, conf.candidates))
      .flatMap(LibraryOfCongress.results)
      .left
      .map(explain)
      .map(
        _.take(conf.candidates.max(0)).map(id =>
          fetch
            .json(LibraryOfCongress.itemUrl(conf.apiUrl, id))
            .flatMap(LibraryOfCongress.record(_, conf.apiUrl, conf.maxYear, conf.displayPx, thumbPx))
            .left
            .map(e => s"item $id: ${explain(e)}")
        )
      )
  override def matches(rec: MediaRecord, place: MediaMatch.PlaceInfo, resolve: MediaMatch.Resolve) =
    MediaMatch.map(rec, place, resolve, conf.maxYear)
