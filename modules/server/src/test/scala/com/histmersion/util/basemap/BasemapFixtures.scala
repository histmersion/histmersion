package com.histmersion.util.basemap

import com.histmersion.util.storage.{Location, Reservation, Storage, Store}
import io.circe.Json

import java.nio.file.{Files, Path}
import java.util.concurrent.atomic.AtomicInteger
import scala.util.Using

/** Small PMTiles archives built in a test, and a [[RangeHttp]] that answers from a local file — no network anywhere in
  * the base-map suites.
  */
object BasemapFixtures:

  /** The base map's store over a test's directory. */
  def store(dir: Path): Store = Storage.at(Location.Basemap, dir)

  /** A writer of the archive at `out`. */
  def writer(out: Path): PmTilesWriter =
    val a = out.toAbsolutePath
    PmTilesWriter(store(a.getParent), Store.Key.applyUnsafe(a.getFileName.toString))

  /** The archive at `p`, opened. */
  def open(p: Path): Either[String, PmTiles.File] =
    val a = p.toAbsolutePath
    store(a.getParent)
      .get(Store.Key.applyUnsafe(a.getFileName.toString))
      .toRight(s"$p: no such file")
      .flatMap(PmTiles.File.open(_))

  /** A square ring over the whole tile (MVT commands: MoveTo, LineTo ×3, ClosePath). */
  private val square = Vector(9, 0, 0, 26, 8192, 0, 0, 8192, 8191, 0, 15)
  private val line = Vector(9, 100, 100, 10, 400, 400)

  /** A vector tile with what a Protomaps tile holds: land, the sea (and its name), a river and a canal, natural and
    * farmed land cover, land use, a peak and a restaurant, a road, a town, and a layer an older build has
    * (`physical_point`).
    */
  def vectorTile(z: Int, x: Long, y: Long): Array[Byte] =
    Mvt.tile(
      List(
        Mvt.layer("earth", List((3, square, Map("kind" -> "earth")))),
        Mvt.layer(
          "water",
          List(
            (3, square, Map("kind" -> "ocean")),
            (2, line, Map("kind" -> "river")),
            (2, line, Map("kind" -> "canal")),
            (1, Vector(9, 40, 40), Map("kind" -> "sea", "name" -> "Aegean Sea", "name:en" -> "Aegean Sea"))
          )
        ),
        Mvt.layer("landcover", List((3, square, Map("kind" -> "forest")), (3, square, Map("kind" -> "farmland")))),
        Mvt.layer("landuse", List((3, square, Map("kind" -> "residential")))),
        Mvt.layer(
          "pois",
          List(
            (1, Vector(9, 60, 60), Map("kind" -> "peak", "name" -> "Olympus")),
            (1, Vector(9, 70, 70), Map("kind" -> "restaurant"))
          )
        ),
        Mvt.layer("roads", List((2, line, Map("kind" -> "highway", "name" -> s"A$z-$x-$y")))),
        Mvt.layer("places", List((1, Vector(9, 50, 50), Map("kind" -> "locality", "name" -> "Zzville")))),
        Mvt.layer("physical_point", List((1, Vector(9, 80, 80), Map("kind" -> "mountain_range", "name" -> "Pindus"))))
      )
    )

  private def header(t: PmTiles.TileType, c: PmTiles.Compression, maxZoom: Int) =
    PmTiles.Header(
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      true,
      PmTiles.Compression.Gzip,
      c,
      t,
      0,
      maxZoom,
      -180,
      -85.05,
      180,
      85.05,
      0,
      0,
      0
    )

  /** A world archive of zooms 0…`maxZoom`, every tile from `tile` (stored with `compression`). */
  def archive(out: Path, maxZoom: Int, t: PmTiles.TileType, c: PmTiles.Compression, extra: Json = Json.obj())(
      tile: (Int, Long, Long) => Array[Byte]
  ): Path =
    val w = writer(out)
    val all = for z <- 0 to maxZoom; x <- 0L until (1L << z); y <- 0L until (1L << z)
    yield (PmTiles.tileId(z, x, y), (z, x, y))
    all.sortBy(_._1).foreach { case (id, (z, x, y)) => w.add(id, PmTiles.compress(tile(z, x, y), c)) }
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
  def vectorArchive(out: Path, maxZoom: Int = 4, extra: Json = Json.obj()): Path =
    archive(out, maxZoom, PmTiles.TileType.Mvt, PmTiles.Compression.Gzip, extra)(vectorTile)

  /** A terrain archive whose tiles are just their own name (a fetch never decodes them). */
  def terrainArchive(out: Path, maxZoom: Int = 4): Path =
    archive(out, maxZoom, PmTiles.TileType.Webp, PmTiles.Compression.None)((z, x, y) => s"RIFF$z/$x/$y".getBytes)

  /** Answers range requests from `file` as a CDN does (206, Content-Range, an ETag), counting them. `etag` may change
    * between requests (a build replaced mid-download).
    */
  final class LocalRange(file: Path, var etag: String = "\"v1\"", builds: String = "[]") extends RangeHttp:
    val requests = AtomicInteger(0)
    val downloads = AtomicInteger(0)
    private def size = Files.size(file)
    def text(url: String): Either[String, String] = Right(builds)
    def bytes(url: String, offset: Long, length: Long): Either[String, (Array[Byte], RemoteFile)] =
      requests.incrementAndGet()
      val all = Files.readAllBytes(file)
      val end = math.min(all.length.toLong, offset + length)
      Right((all.slice(offset.toInt, end.toInt), RemoteFile(size, Some(etag))))
    def download(url: String, offset: Long, length: Long, to: Reservation): Either[String, RemoteFile] =
      requests.incrementAndGet()
      downloads.incrementAndGet()
      val all = Files.readAllBytes(file)
      Using.resource(to.output())(_.write(all.slice(offset.toInt, (offset + length).toInt)))
      Right(RemoteFile(size, Some(etag)))
