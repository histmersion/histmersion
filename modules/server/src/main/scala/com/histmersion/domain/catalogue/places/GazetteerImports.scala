package com.histmersion
package domain.catalogue.places
import com.histmersion.util.problem.Problem

import com.histmersion.api.Dataset
import com.histmersion.api.catalogue.Lpf
import com.histmersion.config.AppConfig
import com.histmersion.domain.catalogue.places.PlaceSeed
import com.histmersion.plugin.GazetteerSource
import com.histmersion.util.database.Transactor
import com.histmersion.util.storage.Store

import scala.collection.immutable.ListMap
import com.histmersion.domain.catalogue.Place
import com.histmersion.api.catalogue.Gazetteer
import com.histmersion.plugin.Plugin
import com.histmersion.api.jobs.JobInfo
import com.histmersion.pluginhost.Registries
import com.histmersion.server.HttpDownload
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.database.WritePermit
import com.histmersion.domain.Creator
import com.histmersion.domain.automation.workflows.InTreeWorkflow
import com.histmersion.lint.{tolerated, LintRule}

/** How the gazetteer comes in: the bundled seed, the pinned extracts and the drop folder ([[GazetteerLoad]]), a source
  * a plugin registers ([[ImportRunner]]) and the land ([[NaturalEarthLand]]). The composition root gives the stores,
  * the downloads (`download` into a file, `text` a url's text), the plugins' sources and the client they read through;
  * `cfg` is read at each call.
  */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
final class GazetteerImports(
    db: Transactor,
    places: PlaceService,
    geometry: PlaceGeometry,
    stores: () => GazetteerLoad.Stores,
    cfg: () => AppConfig,
    download: GazetteerLoad.Download,
    text: HttpDownload.Url => Either[Problem, HttpDownload.Text],
    registered: () => ListMap[Registries.GazetteerSourcesKey, GazetteerSource],
    http: () => Plugin.Http,
    owners: () => Map[GazetteerSource, Plugin.Info.Id],
    clock: java.time.Clock
):
  private def settings: GazetteerLoad.Settings = GazetteerLoad.settings(cfg(), stores(), clock)

  def importerOf(source: GazetteerSource): Creator.New =
    InTreeWorkflow.imported(owners().get(source), InTreeWorkflow.Catalogue, InTreeWorkflow.GazetteerImporting)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def pointTolerance: GeocodeSettings.ToleranceKm =
    GeocodeSettings.ToleranceKm(cfg().places.pointToleranceKm.value)

  /** `GET /api/places/sources` — the pinned extracts, the LPF drop folder, and what was imported. */
  def sources(): List[Gazetteer.SourceInfo] = GazetteerLoad.list(db, settings)

  /** One external source imported (or, `apply = false`, dry-run); `note` reports progress on the job. */
  def load(
      kind: Gazetteer.Kind,
      apply: GazetteerLoad.LoadApply,
      note: JobInfo.Progress => Unit
  )(using WritePermit)(using Creator.New): Either[Problem, List[Gazetteer.LoadCounts]] =
    given Creator.New = Creator.code(InTreeWorkflow.Catalogue, InTreeWorkflow.GazetteerImporting)
    GazetteerLoad.load(db, settings, kind, apply, note, download)

  /** The bundled gazetteer, the way `histmersion places seed` imports it: [[PlaceSeed.files]] order, then a second pass
    * over every file that reported a dangling parent, then `finding_places`.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  def seed(seed: Store, note: JobInfo.Progress => Unit)(using
      WritePermit
  )(using Creator.New): Either[Problem, GazetteerImports.Summary] =
    if !seed.isDirectory(Store.Key.Root) then
      Left(
        Problem.Io.Absent(
          Problem.Io.Held.GazetteerSeed,
          Problem.Io.Path.of(seed.where()),
          Some(Problem.SettingKey("init.data-dir"))
        )
      )
    else
      def entries(dir: Store.Key): List[PlaceSeed.Entry[Store.Key]] =
        seed
          .list(dir)
          .filter(e => e.isFile || e.isDirectory)
          .map(e =>
            PlaceSeed.Entry(PlaceSeed.Entry.Name(e.key.name.value), PlaceSeed.Entry.Directory(e.isDirectory), e.key)
          )
      val files = PlaceSeed.files(entries(Store.Key.Root), entries)
      val batch = math.max(1, cfg().places.importBatch.value)
      def importFile(
          f: Store.Key,
          kind: Option[Place.Kind.Wire]
      )(using WritePermit)(using Creator.New): Either[Problem, com.histmersion.api.catalogue.Place.ImportResult] =
        seed
          .get(f)
          .toRight(Problem.Io.Wrong(Problem.Io.Path.of(seed.where(f)), Problem.Io.Kind.Present))
          .flatMap(blob =>
            Lpf
              .parse(
                blob.text().value,
                com.histmersion.api.catalogue.Place.KindValue(kind.fold(Place.Kind.Region)(_.value))
              )
              .left
              .map(_.inside(Problem.Where.of(f)))
          )
          .map(
            _.grouped(batch).foldLeft(
              com.histmersion.api.catalogue.Place.ImportResult(
                com.histmersion.api.catalogue.Place.ImportResult.Places(0),
                com.histmersion.api.catalogue.Place.ImportResult.Names(0),
                com.histmersion.api.catalogue.Place.ImportResult.Relations(0),
                Nil
              )
            ) { (sum, b) =>
              val r = places.importPlaces(b, Dataset.Seed)
              com.histmersion.api.catalogue.Place.ImportResult(
                com.histmersion.api.catalogue.Place.ImportResult.Places(sum.places.value + r.places.value),
                com.histmersion.api.catalogue.Place.ImportResult.Names(sum.names.value + r.names.value),
                com.histmersion.api.catalogue.Place.ImportResult.Relations(sum.relations.value + r.relations.value),
                (sum.failed ++ r.failed).take(20)
              )
            }
          )
      def pass(
          fs: List[(Store.Key, Option[Place.Kind.Wire])]
      )(using WritePermit)(using Creator.New): Either[Problem, (Int, List[(Store.Key, Option[Place.Kind.Wire])])] =
        fs.zipWithIndex.foldLeft[Either[Problem, (Int, List[(Store.Key, Option[Place.Kind.Wire])])]](
          Right((0, Nil))
        ) { case (acc, ((f, kind), i)) =>
          acc.flatMap { (n, retry) =>
            note(JobInfo.Progress(s"gazetteer: ${f.value} (${i + 1}/${fs.size})"))
            importFile(f, kind).map(r => (n + r.places.value, if r.failed.isEmpty then retry else retry :+ (f -> kind)))
          }
        }
      if files.isEmpty then Left(Problem.Io.Absent(Problem.Io.Held.SeedFiles, Problem.Io.Path.of(seed.where()), None))
      else
        for
          first <- pass(files)
          _ <- if first._2.isEmpty then Right((0, Nil)) else pass(first._2)
        yield
          note(JobInfo.Progress("gazetteer: matching findings to places"))
          val r = geometry.rebuildFindingPlaces(pointTolerance)
          GazetteerImports.Summary(s"${first._1} place(s) from ${files.size} file(s); ${r.links} finding→place link(s)")

  /** Natural Earth's land downloaded and every polity clipped to it again (a dry run counts the polygons). */
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  def land(
      dryRun: GazetteerControllers.DryRun
  )(using WritePermit): Either[Problem, com.histmersion.api.catalogue.Place.ImportResult] =
    NaturalEarthLand.run(text, geometry, dryRun)

  /** The source a plugin registered under `name` (or a built-in's), `unknown` when there is none. */
  def source(
      name: BuiltinGazetteers.Entry.Name,
      unknown: Problem
  ): Either[Problem, GazetteerSource] =
    BuiltinGazetteers.resolve(name, registered(), unknown)

  /** The names the sources are known by, a plugin's among them. */
  def available: List[BuiltinGazetteers.Entry.Name] = BuiltinGazetteers.available(registered())

  /** The third-party sources the plugins register. */
  def thirdParty: List[BuiltinGazetteers.Entry.Name] = BuiltinGazetteers.thirdParty(registered())

  /** A plugin's source read and its places upserted in batches (a dry run counts them), then the findings matched. */
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  def fetch(
      source: GazetteerSource,
      dryRun: GazetteerControllers.DryRun
  )(using WritePermit)(using Creator.New): Either[Problem, com.histmersion.api.catalogue.Place.ImportResult] =
    ImportRunner.fetchAndImport(source, http(), places, geometry, dryRun, pointTolerance)

object GazetteerImports:
  type Summary = Summary.T
  object Summary extends Newtype[String, Pure]
