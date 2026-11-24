package com.histmersion
package domain.catalogue.places
import com.histmersion.util.problem.{Problem, ProblemLog}

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.Dataset
import com.histmersion.plugin.GazetteerSource
import com.histmersion.pluginhost.PluginGazetteers
import com.histmersion.api.catalogue.Place
import com.histmersion.plugin.Plugin
import com.histmersion.util.database.WritePermit
import com.histmersion.domain.Creator

/** Imports a gazetteer source a plugin registered (Cliopatria, AWMC and ChronOntology are Histmersion's own): the
  * source downloads and reads its dataset through the client the host lends it, the host checks and converts each place
  * ([[PluginGazetteers]]), then counts them (a dry run) or upserts them in batches and matches the findings to the
  * places again.
  */
object ImportRunner:
  private val BatchSize = 1000

  /** The places a run of `source` stores, in order, and the ones refused with the reason. */
  def places(
      source: GazetteerSource,
      http: Plugin.Http
  ): Either[Problem, (List[Place.Input], List[Place.ImportResult.Failed])] =
    PluginGazetteers.places(source, http).map { (items, refused) =>
      refused.foreach(why => scribe.warn(s"[import] refused $why"))
      (items, refused.map(why => Place.ImportResult.Failed(why.value)))
    }

  def fetchAndImport(
      source: GazetteerSource,
      http: Plugin.Http,
      places: PlaceService,
      geometry: PlaceGeometry,
      dryRun: GazetteerControllers.DryRun,
      toleranceKm: GeocodeSettings.ToleranceKm
  )(using WritePermit)(using Creator.New): Either[Problem, Place.ImportResult] =
    for
      (all, refused) <- ImportRunner.places(source, http)
      result <-
        if dryRun.value then
          Right(
            Place.ImportResult(
              Place.ImportResult.Places(all.size),
              Place.ImportResult.Names(0),
              Place.ImportResult.Relations(0),
              (Place.ImportResult.Failed("dry run") :: refused).take(20)
            )
          )
        else
          batchUpsert(all, places, geometry, toleranceKm)
            .map(r => r.modify(_.failed).setTo((refused ++ r.failed).take(20)))
    yield result

  private def batchUpsert(
      items: List[Place.Input],
      places: PlaceService,
      geometry: PlaceGeometry,
      toleranceKm: GeocodeSettings.ToleranceKm
  )(using WritePermit)(using Creator.New): Either[Problem, Place.ImportResult] =
    try
      val batches = items.grouped(BatchSize).toList
      val total = batches.zipWithIndex.foldLeft(
        Place
          .ImportResult(Place.ImportResult.Places(0), Place.ImportResult.Names(0), Place.ImportResult.Relations(0), Nil)
      ) { case (acc, (batch, i)) =>
        scribe.info(s"[import] upserting batch ${i + 1}/${batches.size} (${batch.size} places)…")
        val r = places.importPlaces(batch, Dataset.Seed)
        Place.ImportResult(
          Place.ImportResult.Places(acc.places.value + r.places.value),
          Place.ImportResult.Names(acc.names.value + r.names.value),
          Place.ImportResult.Relations(acc.relations.value + r.relations.value),
          (acc.failed ++ r.failed).take(20)
        )
      }
      scribe.info(s"[import] rebuilding finding_places…")
      val _ = geometry.rebuildFindingPlaces(toleranceKm)
      Right(total)
    catch case e: Exception => Left(ProblemLog.unexpected(e, Problem.Where("place upsert")))
