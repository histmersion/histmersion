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
  type PlacesResultPart1 = PlacesResultPart1.T
  object PlacesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlacesResultPart2Part2 = PlacesResultPart2Part2.T
  object PlacesResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FetchAndImportResultPart1 = FetchAndImportResultPart1.T
  object FetchAndImportResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FetchAndImportDryRun = FetchAndImportDryRun.T
  object FetchAndImportDryRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FetchAndImportToleranceKm = FetchAndImportToleranceKm.T
  object FetchAndImportToleranceKm extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BatchUpsertResultPart1 = BatchUpsertResultPart1.T
  object BatchUpsertResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BatchUpsertToleranceKm = BatchUpsertToleranceKm.T
  object BatchUpsertToleranceKm extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val BatchSize = 1000

  /** The places a run of `source` stores, in order, and the ones refused with the reason. */
  def places(source: GazetteerSource, http: Plugin.Http): Either[com.histmersion.domain.catalogue.places.ImportRunner.PlacesResultPart1, (List[Place.Input], List[com.histmersion.domain.catalogue.places.ImportRunner.PlacesResultPart2Part2])] =
    PluginGazetteers.places(source, http).map { (items, refused) =>
      refused.foreach(why => scribe.warn(s"[import] refused $why"))
      (items, refused)
    }

  def fetchAndImport(
      source: GazetteerSource,
      http: Plugin.Http,
      places: PlaceService,
      geometry: PlaceGeometry,
      dryRun: com.histmersion.domain.catalogue.places.ImportRunner.FetchAndImportDryRun,
      toleranceKm: com.histmersion.domain.catalogue.places.ImportRunner.FetchAndImportToleranceKm
  ): Either[com.histmersion.domain.catalogue.places.ImportRunner.FetchAndImportResultPart1, Place.ImportResult] =
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
      toleranceKm: com.histmersion.domain.catalogue.places.ImportRunner.BatchUpsertToleranceKm
  ): Either[com.histmersion.domain.catalogue.places.ImportRunner.BatchUpsertResultPart1, Place.ImportResult] =
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
