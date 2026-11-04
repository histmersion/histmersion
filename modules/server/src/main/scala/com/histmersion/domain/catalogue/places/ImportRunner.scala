package com.histmersion.domain.catalogue.places

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.Dataset
import com.histmersion.plugin.GazetteerSource
import com.histmersion.pluginhost.PluginGazetteers
import com.histmersion.api.catalogue.Place
import com.histmersion.plugin.Plugin

/** Imports a gazetteer source a plugin registered (Cliopatria, AWMC and ChronOntology are Histmersion's own): the
  * source downloads and reads its dataset through the client the host lends it, the host checks and converts each place
  * ([[PluginGazetteers]]), then counts them (a dry run) or upserts them in batches and matches the findings to the
  * places again.
  */
object ImportRunner:
  private val BatchSize = 1000

  /** The places a run of `source` stores, in order, and the ones refused with the reason. */
  def places(source: GazetteerSource, http: Plugin.Http): Either[String, (List[Place.Input], List[String])] =
    PluginGazetteers.places(source, http).map { (items, refused) =>
      refused.foreach(why => scribe.warn(s"[import] refused $why"))
      (items, refused)
    }

  def fetchAndImport(
      source: GazetteerSource,
      http: Plugin.Http,
      places: PlaceService,
      geometry: PlaceGeometry,
      dryRun: Boolean,
      toleranceKm: Double
  ): Either[String, Place.ImportResult] =
    for
      (all, refused) <- ImportRunner.places(source, http)
      result <-
        if dryRun then Right(Place.ImportResult(all.size, 0, 0, ("dry run" :: refused).take(20)))
        else
          batchUpsert(all, places, geometry, toleranceKm)
            .map(r => r.modify(_.failed).setTo((refused ++ r.failed).take(20)))
    yield result

  private def batchUpsert(
      items: List[Place.Input],
      places: PlaceService,
      geometry: PlaceGeometry,
      toleranceKm: Double
  ): Either[String, Place.ImportResult] =
    try
      val batches = items.grouped(BatchSize).toList
      val total = batches.zipWithIndex.foldLeft(Place.ImportResult(0, 0, 0, Nil)) { case (acc, (batch, i)) =>
        scribe.info(s"[import] upserting batch ${i + 1}/${batches.size} (${batch.size} places)…")
        val r = places.importPlaces(batch, Dataset.Seed)
        Place.ImportResult(
          acc.places + r.places,
          acc.names + r.names,
          acc.relations + r.relations,
          (acc.failed ++ r.failed).take(20)
        )
      }
      scribe.info(s"[import] rebuilding finding_places…")
      val _ = geometry.rebuildFindingPlaces(toleranceKm)
      Right(total)
    catch case e: Exception => Left(s"upsert failed: ${e.getMessage}")
