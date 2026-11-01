package com.histmersion.domain.catalogue.places

import com.histmersion.api.{Dataset, GazetteerKind, GazetteerLoadCounts, GazetteerSourceInfo, Lpf, PlaceImportResult}
import com.histmersion.config.AppConfig
import com.histmersion.files.PlaceSeed
import com.histmersion.plugin.{GazetteerSource, PluginHttp}
import com.histmersion.util.database.Transactor
import com.histmersion.util.storage.{Store, StoreKey}
import com.histmersion.values.PlaceKind

import scala.collection.immutable.ListMap

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
    text: String => Either[String, String],
    registered: () => ListMap[String, GazetteerSource],
    http: () => PluginHttp
):
  private def settings: GazetteerLoad.Settings = GazetteerLoad.settings(cfg(), stores())

  /** `GET /api/places/sources` — the pinned extracts, the LPF drop folder, and what was imported. */
  def sources(): List[GazetteerSourceInfo] = GazetteerLoad.list(db, settings)

  /** One external source imported (or, `apply = false`, dry-run); `note` reports progress on the job. */
  def load(kind: GazetteerKind, apply: Boolean, note: String => Unit): Either[String, List[GazetteerLoadCounts]] =
    GazetteerLoad.load(db, settings, kind, apply, note, download)

  /** The bundled gazetteer, the way `histmersion places seed` imports it: [[PlaceSeed.files]] order, then a second pass
    * over every file that reported a dangling parent, then `finding_places`.
    */
  def seed(seed: Store, note: String => Unit): Either[String, String] =
    if !seed.isDirectory(StoreKey.Root) then Left(s"no gazetteer seed at ${seed.where()} (set init.data-dir)")
    else
      def entries(dir: StoreKey): List[PlaceSeed.Entry[StoreKey]] =
        seed
          .list(dir)
          .filter(e => e.isFile || e.isDirectory)
          .map(e => PlaceSeed.Entry(e.key.name, e.isDirectory, e.key))
      val files = PlaceSeed.files(entries(StoreKey.Root), entries)
      val batch = math.max(1, cfg().places.importBatch)
      def importFile(f: StoreKey, kind: Option[String]): Either[String, PlaceImportResult] =
        seed
          .get(f)
          .toRight(s"${f.value}: not found")
          .flatMap(blob => Lpf.parse(blob.text(), kind.getOrElse(PlaceKind.Region)).left.map(e => s"${f.value}: $e"))
          .map(_.grouped(batch).foldLeft(PlaceImportResult(0, 0, 0, Nil)) { (sum, b) =>
            val r = places.importPlaces(b, Dataset.Seed)
            PlaceImportResult(
              sum.places + r.places,
              sum.names + r.names,
              sum.relations + r.relations,
              (sum.failed ++ r.failed).take(20)
            )
          })
      def pass(fs: List[(StoreKey, Option[String])]): Either[String, (Int, List[(StoreKey, Option[String])])] =
        fs.zipWithIndex.foldLeft[Either[String, (Int, List[(StoreKey, Option[String])])]](Right((0, Nil))) {
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
  def land(dryRun: Boolean): Either[String, PlaceImportResult] = NaturalEarthLand.run(text, geometry, dryRun)

  /** The source a plugin registered under `name` (or a built-in's), `unknown` when there is none. */
  def source(name: String, unknown: String): Either[String, GazetteerSource] =
    BuiltinGazetteers.resolve(name, registered(), unknown)

  /** The names the sources are known by, a plugin's among them. */
  def available: List[String] = BuiltinGazetteers.available(registered())

  /** The third-party sources the plugins register. */
  def thirdParty: List[String] = BuiltinGazetteers.thirdParty(registered())

  /** A plugin's source read and its places upserted in batches (a dry run counts them), then the findings matched. */
  def fetch(source: GazetteerSource, dryRun: Boolean): Either[String, PlaceImportResult] =
    ImportRunner.fetchAndImport(source, http(), places, geometry, dryRun, cfg().places.pointToleranceKm)
