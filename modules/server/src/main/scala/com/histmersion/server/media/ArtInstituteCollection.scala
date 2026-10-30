package com.histmersion.server.media

import com.histmersion.pluginhost.{allowed, PluginMedia, Polite}

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.db.MediaRepo.PlaceFacts
import com.histmersion.values.MediaRole

/** Chicago: one search answers the fields; images from its IIIF server. */
final class ArtInstituteCollection(val conf: MediaCollectionConfig, thumbPx: Int) extends Collection:
  val provider = MediaProvider.ArtInstitute
  val role = MediaRole.Object
  def search(fetch: Polite, place: PlaceFacts): Either[String, List[Either[String, MediaRecord]]] =
    fetch
      .json(ArtInstitute.searchUrl(conf.apiUrl, place.label, conf.candidates))
      .flatMap(ArtInstitute.records(_, conf.imageUrl, conf.displayPx, thumbPx))
