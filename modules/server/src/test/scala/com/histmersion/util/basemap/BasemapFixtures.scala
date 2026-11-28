package com.histmersion
package util.basemap
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.{ByteCount, ByteOffset}
import com.histmersion.util.storage.{Location, Reservation, Storage, Store}

import java.nio.file.{Files, Path}
import java.util.concurrent.atomic.AtomicInteger
import scala.util.Using
import com.histmersion.lint.{tolerated, LintRule}

/** Small PMTiles archives built in a test, and a [[RangeHttp]] that answers from a local file — no network anywhere in
  * the base-map suites.
  */
object BasemapFixtures:

  /** The base map's store over a test's directory. */
  def store(dir: Path): Store = Storage.at(Location.Basemap, dir)

  /** A writer of the archive at `out`. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def writer(out: Path): PmTilesWriter =
    val a = out.toAbsolutePath
    PmTilesWriter(store(a.getParent), Store.Key.applyUnsafe(a.getFileName.toString))

  /** The archive at `p`, opened. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  def open(p: Path): Either[Problem, PmTiles.File] =
    val a = p.toAbsolutePath
    store(a.getParent)
      .get(Store.Key.applyUnsafe(a.getFileName.toString))
      .toRight(Problem.Unclassified(Legacy.Text(s"$p: no such file")))
      .flatMap(PmTiles.File.open(_))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def commands(cs: Int*): Vector[Mvt.GeometryCommand] = cs.map(Mvt.GeometryCommand(_)).toVector

  private val KindKey = Mvt.PropKey("kind")
  private val NameKey = Mvt.PropKey("name")
  private val NameEnKey = Mvt.PropKey("name:en")

  /** A square ring over the whole tile (MVT commands: MoveTo, LineTo ×3, ClosePath). */
  private val square = commands(9, 0, 0, 26, 8192, 0, 0, 8192, 8191, 0, 15)
  private val line = commands(9, 100, 100, 10, 400, 400)

  /** A vector tile with what a Protomaps tile holds: land, the sea (and its name), a river and a canal, natural and
    * farmed land cover, land use, a peak and a restaurant, a road, a town, and a layer an older build has
    * (`physical_point`).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def vectorTile(z: PmTiles.Zoom, x: PmTiles.TileColumn, y: PmTiles.TileRow): PmTiles.PlainBytes =
    Mvt.tile(
      List(
        Mvt.layer(Mvt.LayerName("earth"), List((Mvt.GeomType(3), square, Map(KindKey -> Mvt.PropText("earth"))))),
        Mvt.layer(
          Mvt.LayerName("water"),
          List(
            (Mvt.GeomType(3), square, Map(KindKey -> Mvt.PropText("ocean"))),
            (Mvt.GeomType(2), line, Map(KindKey -> Mvt.PropText("river"))),
            (Mvt.GeomType(2), line, Map(KindKey -> Mvt.PropText("canal"))),
            (
              Mvt.GeomType(1),
              commands(9, 40, 40),
              Map(
                KindKey -> Mvt.PropText("sea"),
                NameKey -> Mvt.PropText("Aegean Sea"),
                NameEnKey -> Mvt.PropText("Aegean Sea")
              )
            )
          )
        ),
        Mvt.layer(
          Mvt.LayerName("landcover"),
          List(
            (Mvt.GeomType(3), square, Map(KindKey -> Mvt.PropText("forest"))),
            (Mvt.GeomType(3), square, Map(KindKey -> Mvt.PropText("farmland")))
          )
        ),
        Mvt.layer(
          Mvt.LayerName("landuse"),
          List((Mvt.GeomType(3), square, Map(KindKey -> Mvt.PropText("residential"))))
        ),
        Mvt.layer(
          Mvt.LayerName("pois"),
          List(
            (
              Mvt.GeomType(1),
              commands(9, 60, 60),
              Map(KindKey -> Mvt.PropText("peak"), NameKey -> Mvt.PropText("Olympus"))
            ),
            (Mvt.GeomType(1), commands(9, 70, 70), Map(KindKey -> Mvt.PropText("restaurant")))
          )
        ),
        Mvt.layer(
          Mvt.LayerName("roads"),
          List(
            (
              Mvt.GeomType(2),
              line,
              Map(KindKey -> Mvt.PropText("highway"), NameKey -> Mvt.PropText(s"A$z-$x-$y"))
            )
          )
        ),
        Mvt.layer(
          Mvt.LayerName("places"),
          List(
            (
              Mvt.GeomType(1),
              commands(9, 50, 50),
              Map(KindKey -> Mvt.PropText("locality"), NameKey -> Mvt.PropText("Zzville"))
            )
          )
        ),
        Mvt.layer(
          Mvt.LayerName("physical_point"),
          List(
            (
              Mvt.GeomType(1),
              commands(9, 80, 80),
              Map(KindKey -> Mvt.PropText("mountain_range"), NameKey -> Mvt.PropText("Pindus"))
            )
          )
        )
      )
    )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def header(t: PmTiles.TileType, c: PmTiles.Compression, maxZoom: PmTiles.Zoom) =
    PmTiles.Header(
      PmTiles.Header.RootOffset(0),
      PmTiles.Header.RootLength(0),
      PmTiles.Header.MetadataOffset(0),
      PmTiles.Header.MetadataLength(0),
      PmTiles.Header.LeafOffset(0),
      PmTiles.Header.LeafLength(0),
      PmTiles.Header.DataOffset(0),
      PmTiles.Header.DataLength(0),
      PmTiles.Header.AddressedTiles(0),
      PmTiles.Header.TileEntries(0),
      PmTiles.Header.TileContents(0),
      PmTiles.Header.Clustered.Clustered,
      PmTiles.Compression.Gzip,
      c,
      t,
      PmTiles.Header.MinZoom(0),
      maxZoom,
      PmTiles.Header.MinLon(-180),
      PmTiles.Header.MinLat(-85.05),
      PmTiles.Header.MaxLon(180),
      PmTiles.Header.MaxLat(85.05),
      PmTiles.Header.CenterZoom(0),
      PmTiles.Header.CenterLon(0),
      PmTiles.Header.CenterLat(0)
    )

  /** A world archive of zooms 0…`maxZoom`, every tile from `tile` (stored with `compression`). */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def archive(out: Path, maxZoom: PmTiles.Zoom, t: PmTiles.TileType, c: PmTiles.Compression, extra: Json = Json.obj())(
      tile: (PmTiles.Zoom, PmTiles.TileColumn, PmTiles.TileRow) => PmTiles.PlainBytes
  ): Path =
    val w = writer(out)
    val all = for
      z <- 0 to maxZoom.value
      x <- 0L until (1L << z)
      y <- 0L until (1L << z)
    yield (
      PmTiles.tileId(PmTiles.Zoom(z), PmTiles.TileColumn(x), PmTiles.TileRow(y)),
      (PmTiles.Zoom(z), PmTiles.TileColumn(x), PmTiles.TileRow(y))
    )
    all.sortBy[Long](_._1.value).foreach { case (id, (z, x, y)) =>
      w.add(id, PmTiles.TileBytes(PmTiles.compress(tile(z, x, y), c).value))
    }
    val meta = Json.obj(
      "name" -> Json.fromString("fixture"),
      "vector_layers" -> Json.arr(
        List("earth", "water", "landcover", "landuse", "roads", "places").map(l =>
          Json.obj("id" -> Json.fromString(l))
        )*
      )
    )
    w.finish(header(t, c, maxZoom), meta.deepMerge(extra))
    out

  /** `extra`: metadata merged in — `{"histmersion": {"keepAll": true}}` makes it a `--keep-all` extract. */
  def vectorArchive(out: Path, maxZoom: PmTiles.Zoom = PmTiles.Zoom(4), extra: Json = Json.obj()): Path =
    archive(out, maxZoom, PmTiles.TileType.Mvt, PmTiles.Compression.Gzip, extra)(vectorTile)

  /** A terrain archive whose tiles are just their own name (a fetch never decodes them). */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def terrainArchive(out: Path, maxZoom: PmTiles.Zoom = PmTiles.Zoom(4)): Path =
    archive(out, maxZoom, PmTiles.TileType.Webp, PmTiles.Compression.None)((z, x, y) =>
      PmTiles.PlainBytes(s"RIFF$z/$x/$y".getBytes)
    )

  /** Answers range requests from `file` as a CDN does (206, Content-Range, an ETag), counting them. `etag` may change
    * between requests (a build replaced mid-download).
    */
  final class LocalRange(
      file: Path,
      var etag: RemoteFile.Etag = RemoteFile.Etag("\"v1\""),
      builds: RangeHttp.ResponseText = RangeHttp.ResponseText("[]")
  ) extends RangeHttp:
    val requests = AtomicInteger(0)
    val downloads = AtomicInteger(0)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def size = RemoteFile.Size(Files.size(file))
    def text(url: RangeHttp.Url): Either[Problem, RangeHttp.ResponseText] = Right(builds)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def bytes(
        url: RangeHttp.Url,
        offset: ByteOffset,
        length: ByteCount
    ): Either[Problem, (RangeHttp.ResponseBytes, RemoteFile)] =
      requests.incrementAndGet()
      val all = Files.readAllBytes(file)
      val end = math.min(all.length.toLong, offset.value + length.value)
      Right((RangeHttp.ResponseBytes(all.slice(offset.value.toInt, end.toInt)), RemoteFile(size, Some(etag))))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def download(
        url: RangeHttp.Url,
        offset: ByteOffset,
        length: ByteCount,
        to: Reservation
    ): Either[Problem, RemoteFile] =
      requests.incrementAndGet()
      downloads.incrementAndGet()
      val all = Files.readAllBytes(file)
      Using.resource(to.output())(_.write(all.slice(offset.value.toInt, (offset.value + length.value).toInt)))
      Right(RemoteFile(size, Some(etag)))
