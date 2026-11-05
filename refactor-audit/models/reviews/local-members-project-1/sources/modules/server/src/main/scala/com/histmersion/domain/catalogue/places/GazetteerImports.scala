package com.histmersion.domain.catalogue.places

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

/** How the gazetteer comes in: the bundled seed, the pinned extracts and the drop folder ([[GazetteerLoad]]), a source
  * a plugin registers ([[ImportRunner]]) and the land ([[NaturalEarthLand]]). The composition root gives the stores,
  * the downloads (`download` into a file, `text` a url's text), the plugins' sources and the client they read through;
  * `cfg` is read at each call.
  */
final class GazetteerImports(
    db: Transactor,
    places: PlaceService,
    geometry: PlaceGeometry,
    stores: () => GazetteerLoad.Stores,
    cfg: () => AppConfig,
    download: GazetteerLoad.Download,
    text: GazetteerImports.TextArg1 => Either[GazetteerImports.TextResultPart1, GazetteerImports.TextResultPart2],
    registered: () => ListMap[GazetteerImports.RegisteredResultKey, GazetteerSource],
    http: () => Plugin.Http
):
  private def settings: GazetteerLoad.Settings = GazetteerLoad.settings(cfg(), stores())

  /** `GET /api/places/sources` — the pinned extracts, the LPF drop folder, and what was imported. */
  def sources(): List[Gazetteer.SourceInfo] = GazetteerLoad.list(db, settings)

  /** One external source imported (or, `apply = false`, dry-run); `note` reports progress on the job. */
  def load(kind: Gazetteer.Kind, apply: com.histmersion.domain.catalogue.places.GazetteerImports.LoadApply, note: com.histmersion.domain.catalogue.places.GazetteerImports.LoadNoteArg1 => Unit): Either[com.histmersion.domain.catalogue.places.GazetteerImports.LoadResultPart1, List[Gazetteer.LoadCounts]] =
    GazetteerLoad.load(db, settings, kind, apply, note, download)

  /** The bundled gazetteer, the way `histmersion places seed` imports it: [[PlaceSeed.files]] order, then a second pass
    * over every file that reported a dangling parent, then `finding_places`.
    */
  def seed(seed: Store, note: com.histmersion.domain.catalogue.places.GazetteerImports.SeedNoteArg1 => Unit): Either[com.histmersion.domain.catalogue.places.GazetteerImports.SeedResultPart1, com.histmersion.domain.catalogue.places.GazetteerImports.SeedResultPart2] =
    if !seed.isDirectory(Store.Key.Root) then Left(s"no gazetteer seed at ${seed.where()} (set init.data-dir)")
    else
      def entries(dir: Store.Key): List[PlaceSeed.Entry[Store.Key]] =
        seed
          .list(dir)
          .filter(e => e.isFile || e.isDirectory)
          .map(e => PlaceSeed.Entry(e.key.name, e.isDirectory, e.key))
      val files = PlaceSeed.files(entries(Store.Key.Root), entries)
      val batch = math.max(1, cfg().places.importBatch)
      def importFile(
          f: Store.Key,
          kind: Option[com.histmersion.domain.catalogue.places.GazetteerImports.SeedImportFileKind]
      ): Either[com.histmersion.domain.catalogue.places.GazetteerImports.SeedImportFileResultPart1, com.histmersion.api.catalogue.Place.ImportResult] =
        seed
          .get(f)
          .toRight(s"${f.value}: not found")
          .flatMap(blob => Lpf.parse(blob.text(), kind.getOrElse(Place.Kind.Region)).left.map(e => s"${f.value}: $e"))
          .map(_.grouped(batch).foldLeft(com.histmersion.api.catalogue.Place.ImportResult(0, 0, 0, Nil)) { (sum, b) =>
            val r = places.importPlaces(b, Dataset.Seed)
            com.histmersion.api.catalogue.Place.ImportResult(
              sum.places + r.places,
              sum.names + r.names,
              sum.relations + r.relations,
              (sum.failed ++ r.failed).take(20)
            )
          })
      def pass(fs: List[(Store.Key, Option[com.histmersion.domain.catalogue.places.GazetteerImports.SeedPassFsPart2])]): Either[com.histmersion.domain.catalogue.places.GazetteerImports.SeedPassResultPart1, (com.histmersion.domain.catalogue.places.GazetteerImports.SeedPassResultPart2Part1, List[(Store.Key, Option[com.histmersion.domain.catalogue.places.GazetteerImports.SeedPassResultPart2Part2Part2])])] =
        fs.zipWithIndex.foldLeft[Either[String, (Int, List[(Store.Key, Option[String])])]](Right((0, Nil))) {
          case (acc, ((f, kind), i)) =>
            acc.flatMap { (n, retry) =>
              note(s"gazetteer: ${f.value} (${i + 1}/${fs.size})")
              importFile(f, kind).map(r => (n + r.places, if r.failed.isEmpty then retry else retry :+ (f -> kind)))
            }
        }
      if files.isEmpty then Left(s"no JSON files under ${seed.where()}")
      else
        for
          first <- pass(files)
          _ <- if first._2.isEmpty then Right((0, Nil)) else pass(first._2)
        yield
          note("gazetteer: matching findings to places")
          val r = geometry.rebuildFindingPlaces(cfg().places.pointToleranceKm)
          s"${first._1} place(s) from ${files.size} file(s); ${r.links} finding→place link(s)"

  /** Natural Earth's land downloaded and every polity clipped to it again (a dry run counts the polygons). */
  def land(dryRun: com.histmersion.domain.catalogue.places.GazetteerImports.LandDryRun): Either[com.histmersion.domain.catalogue.places.GazetteerImports.LandResultPart1, com.histmersion.api.catalogue.Place.ImportResult] =
    NaturalEarthLand.run(text, geometry, dryRun)

  /** The source a plugin registered under `name` (or a built-in's), `unknown` when there is none. */
  def source(name: com.histmersion.domain.catalogue.places.GazetteerImports.SourceName, unknown: com.histmersion.domain.catalogue.places.GazetteerImports.SourceUnknown): Either[com.histmersion.domain.catalogue.places.GazetteerImports.SourceResultPart1, GazetteerSource] =
    BuiltinGazetteers.resolve(name, registered(), unknown)

  /** The names the sources are known by, a plugin's among them. */
  def available: List[com.histmersion.domain.catalogue.places.GazetteerImports.AvailableResult] = BuiltinGazetteers.available(registered())

  /** The third-party sources the plugins register. */
  def thirdParty: List[com.histmersion.domain.catalogue.places.GazetteerImports.ThirdPartyResult] = BuiltinGazetteers.thirdParty(registered())

  /** A plugin's source read and its places upserted in batches (a dry run counts them), then the findings matched. */
  def fetch(
      source: GazetteerSource,
      dryRun: com.histmersion.domain.catalogue.places.GazetteerImports.FetchDryRun
  ): Either[com.histmersion.domain.catalogue.places.GazetteerImports.FetchResultPart1, com.histmersion.api.catalogue.Place.ImportResult] =
    ImportRunner.fetchAndImport(source, http(), places, geometry, dryRun, cfg().places.pointToleranceKm)

object GazetteerImports:
  type SeedImportFileResultPart1 = SeedImportFileResultPart1.T
  object SeedImportFileResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SeedImportFileKind = SeedImportFileKind.T
  object SeedImportFileKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SeedPassResultPart1 = SeedPassResultPart1.T
  object SeedPassResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SeedPassResultPart2Part1 = SeedPassResultPart2Part1.T
  object SeedPassResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SeedPassResultPart2Part2Part2 = SeedPassResultPart2Part2Part2.T
  object SeedPassResultPart2Part2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SeedPassFsPart2 = SeedPassFsPart2.T
  object SeedPassFsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type TextArg1 = TextArg1.T
  object TextArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TextResultPart1 = TextResultPart1.T
  object TextResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TextResultPart2 = TextResultPart2.T
  object TextResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RegisteredResultKey = RegisteredResultKey.T
  object RegisteredResultKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type LoadResultPart1 = LoadResultPart1.T
  object LoadResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LoadApply = LoadApply.T
  object LoadApply extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LoadNoteArg1 = LoadNoteArg1.T
  object LoadNoteArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SeedResultPart1 = SeedResultPart1.T
  object SeedResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SeedResultPart2 = SeedResultPart2.T
  object SeedResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SeedNoteArg1 = SeedNoteArg1.T
  object SeedNoteArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LandResultPart1 = LandResultPart1.T
  object LandResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LandDryRun = LandDryRun.T
  object LandDryRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourceResultPart1 = SourceResultPart1.T
  object SourceResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourceName = SourceName.T
  object SourceName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourceUnknown = SourceUnknown.T
  object SourceUnknown extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AvailableResult = AvailableResult.T
  object AvailableResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ThirdPartyResult = ThirdPartyResult.T
  object ThirdPartyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FetchResultPart1 = FetchResultPart1.T
  object FetchResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FetchDryRun = FetchDryRun.T
  object FetchDryRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
