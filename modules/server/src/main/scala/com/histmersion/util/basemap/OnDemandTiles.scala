package com.histmersion
package util.basemap

import com.histmersion.{ByteCount, ByteOffset}
import com.histmersion.config.BasemapOnDemandConfig
import com.histmersion.util.storage.{ByteSource, Store}

import io.circe.Json

import java.io.IOException
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.{CompletableFuture, ConcurrentHashMap, Semaphore, TimeUnit}
import com.histmersion.api.basemap.Basemap
import io.github.iltotore.iron.constraint.any.Pure

/** A layer's tiles read when a page shows them, beyond what was downloaded (docs/research-terrain-basemap.md,
  * "On-demand relief"; #3760 for the map and the sea floor): the server reads just those tiles from the provider — by
  * HTTP range requests from its planet archive ([[OnDemandTiles.Source.Planet]], the access its documentation shows for
  * extracts), or one GET a tile from a tile URL ([[OnDemandTiles.Source.Template]]) — `transform`s each (the vector
  * layer drops what the download drops) and keeps it in a bounded cache ([[DiskLru]], files `.<ext>`) in `store`, next
  * to the extract, so an area seen once shows again offline.
  *
  * A planet's header, root and the leaf directories read (11 KB each, 4 096 tiles) are kept on the disk too, under
  * `remote/`, with the file's size and ETag: when the provider replaces the file (another ETag), they are read again;
  * the tiles kept stay.
  *
  * Politeness: at most `concurrency` requests at a time and `requestsPerSecond` begun each second; one request per tile
  * (two when its leaf directory is new); a tile asked for twice at once is read once. After a failed request (offline)
  * none is made for `retryAfterSeconds` — the page gets no tile there, quickly, and asks again later. `http`, `now` and
  * `sleep` are the seams a test substitutes.
  */
final class OnDemandTiles(
    val store: Store,
    cfg: () => BasemapOnDemandConfig,
    source: OnDemandTiles.Source,
    http: () => RangeHttp,
    now: () => OnDemandTiles.Instant = () => OnDemandTiles.Instant(System.currentTimeMillis()),
    sleep: BasemapFetch.Pace.GapMs => Unit = ms => Thread.sleep(ms.value),
    ext: DiskLru.Ext = DiskLru.Ext("webp"),
    transform: PmTiles.TileBytes => Either[OnDemandTiles.Problem, Option[PmTiles.TileBytes]] = b => Right(Some(b))
)(using clock: java.time.Clock):

  private def planetUrl(): RangeHttp.Url = source match
    case OnDemandTiles.Source.Planet(u)   => u()
    case OnDemandTiles.Source.Template(u) => RangeHttp.Url(u().value)

  val cache: DiskLru =
    DiskLru(store.sub(OnDemandTiles.Tiles), () => DiskLru.MaxBytes((cfg().cacheGb.value * 1e9).toLong), ext)(using clock)
  private val remoteFiles = store.sub(OnDemandTiles.Remote)

  private def done(written: Either[Store.Error, ?]): Unit =
    written.left.foreach(e => throw IOException(e.message.value))

  /** What the planet file is: its header and root, the leaves read, its size and ETag. */
  final private case class Remote(url: RangeHttp.Url, header: PmTiles.Header, root: Vector[PmTiles.Entry], file: RemoteFile)

  // the open remote description (re-read when the file changes) and when requests may start again; under the lock
  @volatile private var remote: Option[Remote] = None
  @volatile private var offlineUntil = 0L
  private var nextStart = 0L
  private val leaves = ConcurrentHashMap[Long, Vector[PmTiles.Entry]]()
  private val inflight =
    ConcurrentHashMap[String, CompletableFuture[Either[OnDemandTiles.Problem, Option[PmTiles.TileBytes]]]]()
  private lazy val gate = Semaphore(cfg().concurrency.value.max(1), true)

  def status: Basemap.OnDemand =
    val c = cfg()
    Basemap.OnDemand(
      Basemap.OnDemand.Enabled(c.enabled.value),
      Basemap.OnDemand.MaxZoom(c.maxZoom.value),
      Basemap.OnDemand.Dir(store.where().value),
      Basemap.OnDemand.Tiles(cache.entries.value),
      com.histmersion.api.ByteCount(cache.bytes.value),
      Basemap.OnDemand.MaxBytes((c.cacheGb.value * 1e9).toLong),
      Option.when(offlineUntil > now().value)(
        Basemap.OnDemand.OfflineUntil(java.time.Instant.ofEpochMilli(offlineUntil).toString)
      )
    )

  /** Whether `z` is one this serves (deeper than the extract's `extractMax`, to the configured depth). */
  def serves(z: PmTiles.Zoom, extractMax: BasemapFetch.Plan.MaxZoom): Boolean =
    cfg().enabled.value && z.value > extractMax.value && z.value <= cfg().maxZoom.value

  /** Whether it serves tile `z` at all (on, and not deeper than its configured depth) — outside the extract. */
  def servesAt(z: PmTiles.Zoom): Boolean = cfg().enabled.value && z.value >= 0 && z.value <= cfg().maxZoom.value

  /** One request to the provider: within the concurrency and the rate. */
  private def request[A](f: => Either[OnDemandTiles.Problem, A]): Either[OnDemandTiles.Problem, A] =
    if !gate.tryAcquire(60, TimeUnit.SECONDS) then Left(OnDemandTiles.Problem("too many tile requests waiting"))
    else
      try
        val wait = synchronized {
          val t: Long = now().value
          val start = math.max(t, nextStart)
          nextStart = start + (1000.0 / cfg().requestsPerSecond.value.max(0.1)).toLong
          start - t
        }
        if wait > 0 then sleep(BasemapFetch.Pace.GapMs(wait))
        f
      finally gate.release()

  private def remoteJson(r: Remote) = Json.obj(
    "url" -> Json.fromString(r.url.value),
    "size" -> Json.fromLong(r.file.size.value),
    "etag" -> r.file.etag.fold(Json.Null)(e => Json.fromString(e.value))
  )

  /** The planet's description: in memory, else from `remote/` (when it names the same URL), else read. */
  private def described(): Either[OnDemandTiles.Problem, Remote] =
    val u = planetUrl()
    remote
      .filter(_.url == u)
      .map(Right(_))
      .getOrElse(synchronized {
        remote.filter(_.url == u).map(Right(_)).getOrElse {
          val fromDisk = scala.util
            .Try {
              val j =
                io.circe.parser.parse(remoteFiles.get(OnDemandTiles.RemoteJson).get.text().value).toOption.get.hcursor
              val head = remoteFiles.get(OnDemandTiles.Head).get.bytes().value
              (
                j.get[String]("url").toOption.get,
                j.get[Long]("size").toOption.get,
                j.get[Option[String]]("etag").toOption.flatten,
                head
              )
            }
            .toOption
            .filter(_._1 == u.value)
          val head: Either[OnDemandTiles.Problem, (Array[Byte], RemoteFile)] = fromDisk match
            case Some((_, size, etag, h)) => Right((h, RemoteFile(RemoteFile.Size(size), etag.map(RemoteFile.Etag(_)))))
            case None                     =>
              val _ = remoteFiles.deleteTree(Store.Key.Root)
              leaves.clear()
              request(http().bytes(u, ByteOffset.applyUnsafe(0L), ByteCount.applyUnsafe(PmTiles.RootBytes.toLong))).map {
                (b, f) =>
                  done(remoteFiles.write(OnDemandTiles.Head, ByteSource.Content(b.value)))
                  (b.value, f)
              }
          for
            hf <- head
            (b, f) = hf
            h <- PmTiles.parseHeader(PmTiles.HeaderBytes(b))
            _ <- Either.cond(
              h.rootOffset.value + h.rootLength.value <= b.length,
              (),
              OnDemandTiles.Problem("root directory outside the first 16 KiB")
            )
            root <- PmTiles
              .decompress(
                PmTiles.CompressedBytes(
                  b.slice(h.rootOffset.value.toInt, (h.rootOffset.value + h.rootLength.value).toInt)
                ),
                h.internalCompression
              )
              .flatMap(PmTiles.decodeDirectory)
          yield
            val r = Remote(u, h, root, f)
            if fromDisk.isEmpty then
              done(
                remoteFiles.write(
                  OnDemandTiles.RemoteJson,
                  ByteSource.Content(remoteJson(r).noSpaces.getBytes(UTF_8))
                )
              )
            remote = Some(r)
            r
        }
      })

  /** The provider replaced its file: forget what was read of it (the tiles kept stay). */
  private def forget(): Unit = synchronized {
    remote = None
    leaves.clear()
    val _ = remoteFiles.deleteTree(Store.Key.Root)
  }

  private def leaf(r: Remote, e: PmTiles.Entry): Either[OnDemandTiles.Problem, Vector[PmTiles.Entry]] =
    Option(leaves.get(e.offset.value)).map(Right(_)).getOrElse {
      val f = Store.Key.applyUnsafe(s"leaf-${e.offset.value}-${e.length.value}.bin")
      val raw: Either[OnDemandTiles.Problem, Array[Byte]] = remoteFiles.get(f).filter(_.size.value == e.length.value) match
        case Some(kept) => Right(kept.bytes().value)
        case None       =>
          PmTiles.range(r.header.leafOffset, e).flatMap { (at, n) =>
            request(http().bytes(r.url, at, n)).flatMap { (b, got) =>
              if got.etag != r.file.etag || got.size != r.file.size then Left(OnDemandTiles.Changed)
              else
                done(remoteFiles.write(f, ByteSource.Content(b.value)))
                Right(b.value)
            }
          }
      raw
        .flatMap(b => PmTiles.decompress(PmTiles.CompressedBytes(b), r.header.internalCompression))
        .flatMap(PmTiles.decodeDirectory)
        .map { d =>
          if leaves.size > 512 then leaves.clear()
          leaves.put(e.offset.value, d)
          d
        }
    }

  /** The planet's entry for a tile, through its leaf directories. */
  private def entry(
      r: Remote,
      z: PmTiles.Zoom,
      x: PmTiles.TileColumn,
      y: PmTiles.TileRow
  ): Either[OnDemandTiles.Problem, Option[PmTiles.Entry]] =
    val id = PmTiles.tileId(z, x, y)
    def walk(d: Vector[PmTiles.Entry], depth: Int): Either[OnDemandTiles.Problem, Option[PmTiles.Entry]] =
      PmTiles.find(d, id) match
        case None                  => Right(None)
        case Some(e) if !e.isLeaf  => Right(Some(e))
        case Some(_) if depth >= 3 => Left(OnDemandTiles.Problem("PMTiles directories nested too deep"))
        case Some(e)               => leaf(r, e).flatMap(walk(_, depth + 1))
    if z < r.header.minZoom || z > r.header.maxZoom then Right(None) else walk(r.root, 0)

  private def read(
      z: PmTiles.Zoom,
      x: PmTiles.TileColumn,
      y: PmTiles.TileRow
  ): Either[OnDemandTiles.Problem, Option[PmTiles.TileBytes]] =
    val raw: Either[OnDemandTiles.Problem, Option[PmTiles.TileBytes]] = source match
      case OnDemandTiles.Source.Planet(_)   => readPlanet(z, x, y)
      case OnDemandTiles.Source.Template(u) =>
        request(
          http().file(
            RangeHttp.Url(
              u().value.replace("{z}", z.value.toString).replace("{x}", x.value.toString).replace("{y}", y.value.toString)
            )
          )
        ).map(_.map(b => PmTiles.TileBytes(b.value)))
    raw.flatMap(_.fold[Either[OnDemandTiles.Problem, Option[PmTiles.TileBytes]]](Right(None))(transform))

  private def readPlanet(
      z: PmTiles.Zoom,
      x: PmTiles.TileColumn,
      y: PmTiles.TileRow
  ): Either[OnDemandTiles.Problem, Option[PmTiles.TileBytes]] =
    def once(): Either[OnDemandTiles.Problem, Option[PmTiles.TileBytes]] =
      for
        r <- described()
        e <- entry(r, z, x, y)
        b <- e.fold[Either[OnDemandTiles.Problem, Option[PmTiles.TileBytes]]](Right(None)) { e =>
          PmTiles.range(r.header.dataOffset, e).flatMap { (at, n) =>
            request(http().bytes(r.url, at, n)).flatMap { (b, got) =>
              if got.etag != r.file.etag || got.size != r.file.size then Left(OnDemandTiles.Changed)
              else Right(Some(PmTiles.TileBytes(b.value)))
            }
          }
        }
      yield b
    once() match
      case Left(OnDemandTiles.Changed) => forget(); once()
      case other                       => other

  /** The tile `z/x/y`: from the cache, else from the provider (then cached). `Right(None)`: there is none (outside the
    * configured zooms, or the provider has none); `Left`: it could not be read now.
    */
  def tile(
      z: PmTiles.Zoom,
      x: PmTiles.TileColumn,
      y: PmTiles.TileRow
  ): Either[OnDemandTiles.Problem, Option[PmTiles.TileBytes]] =
    val c = cfg()
    val zoom: Int = z.value
    if !c.enabled.value || zoom > c.maxZoom.value || x.value < 0 || y.value < 0 || x.value >= (1L << zoom) || y.value >= (1L << zoom)
    then Right(None)
    else
      val key = DiskLru.Key(s"${z.value}/${x.value}/${y.value}")
      cache.get(key) match
        case Some(hit)                            => Right(hit.map(b => PmTiles.TileBytes(b.value)))
        case None if offlineUntil > now().value =>
          Left(OnDemandTiles.Problem("the provider was unreachable a moment ago; not asked again yet"))
        case None =>
          val mine = CompletableFuture[Either[OnDemandTiles.Problem, Option[PmTiles.TileBytes]]]()
          Option(inflight.putIfAbsent(key.value, mine)) match
            case Some(other) =>
              scala.util
                .Try(other.get(120, TimeUnit.SECONDS))
                .toEither
                .left
                .map(e => OnDemandTiles.Problem(s"tile ${key.value}: ${e.getMessage}"))
                .flatten
            case None =>
              try
                val r = read(z, x, y)
                r match
                  case Right(b) => cache.put(key, b.map(t => DiskLru.Bytes(t.value)))
                  case Left(e)  =>
                    offlineUntil = now().value + c.retryAfterSeconds.value * 1000L
                    scribe.warn(s"basemap: on-demand tile ${key.value} (${store.where().value}) not read: ${e.value}")
                mine.complete(r)
                r
              catch
                case e: Throwable =>
                  mine.complete(Left(OnDemandTiles.Problem(s"tile ${key.value}: ${e.getClass.getSimpleName}")))
                  throw e
              finally { val _ = inflight.remove(key.value, mine) }

object OnDemandTiles:
  type Instant = Instant.T
  object Instant extends Newtype[Long, Pure]
  type Problem = RangeHttp.Problem
  val Problem: RangeHttp.Problem.type = RangeHttp.Problem
  type UrlTemplate = UrlTemplate.T
  object UrlTemplate extends Newtype[String, Pure]

  private val Tiles = Store.Key.applyUnsafe("tiles")
  private val Remote = Store.Key.applyUnsafe("remote")
  private val RemoteJson = Store.Key.applyUnsafe("remote.json")
  private val Head = Store.Key.applyUnsafe("head.bin")

  /** The provider's file changed under a read (another ETag or size). */
  val Changed: Problem = Problem("the provider's file changed")

  /** Where tiles are read from: a PMTiles planet archive (range requests), or a tile URL with `{z}/{x}/{y}` (one GET a
    * tile; a 404 is "none").
    */
  enum Source derives CanEqual:
    case Planet(url: () => RangeHttp.Url)
    case Template(url: () => UrlTemplate)
