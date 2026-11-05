package com.histmersion.util.basemap

import com.histmersion.config.BasemapOnDemandConfig
import com.histmersion.util.storage.Store

import io.circe.Json

import java.io.IOException
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.{CompletableFuture, ConcurrentHashMap, Semaphore, TimeUnit}
import com.histmersion.api.basemap.Basemap

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
    now: () => OnDemandTiles.NowResult = () => System.currentTimeMillis(),
    sleep: OnDemandTiles.SleepArg1 => Unit = ms => Thread.sleep(ms),
    ext: OnDemandTiles.Ext = "webp",
    transform: Array[Byte] => Either[OnDemandTiles.TransformResultPart1, Option[Array[Byte]]] = b => Right(Some(b))
)(using clock: java.time.Clock):

  private def url(): com.histmersion.util.basemap.OnDemandTiles.UrlResult = source match
    case OnDemandTiles.Source.Planet(u)   => u()
    case OnDemandTiles.Source.Template(u) => u()

  val cache: DiskLru = DiskLru(store.sub(OnDemandTiles.Tiles), () => (cfg().cacheGb * 1e9).toLong, ext)(using clock)
  private val remoteFiles = store.sub(OnDemandTiles.Remote)

  private def done(written: Either[Store.Error, ?]): Unit = written.left.foreach(e => throw IOException(e.message))

  /** What the planet file is: its header and root, the leaves read, its size and ETag. */
  final private case class Remote(url: Remote.Url, header: PmTiles.Header, root: Vector[PmTiles.Entry], file: RemoteFile)

  object Remote:
    type Url = Url.T
    object Url extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  // the open remote description (re-read when the file changes) and when requests may start again; under the lock
  @volatile private var remote: Option[Remote] = None
  @volatile private var offlineUntil = 0L
  private var nextStart = 0L
  private val leaves = ConcurrentHashMap[Long, Vector[PmTiles.Entry]]()
  private val inflight = ConcurrentHashMap[String, CompletableFuture[Either[String, Option[Array[Byte]]]]]()
  private lazy val gate = Semaphore(cfg().concurrency.max(1), true)

  def status: Basemap.OnDemand =
    val c = cfg()
    Basemap.OnDemand(
      c.enabled,
      c.maxZoom,
      store.where(),
      cache.entries,
      cache.bytes,
      (c.cacheGb * 1e9).toLong,
      Option.when(offlineUntil > now())(java.time.Instant.ofEpochMilli(offlineUntil).toString)
    )

  /** Whether `z` is one this serves (deeper than the extract's `extractMax`, to the configured depth). */
  def serves(z: com.histmersion.util.basemap.OnDemandTiles.ServesZ, extractMax: com.histmersion.util.basemap.OnDemandTiles.ServesExtractMax): com.histmersion.util.basemap.OnDemandTiles.ServesResult = cfg().enabled && z > extractMax && z <= cfg().maxZoom

  /** Whether it serves tile `z` at all (on, and not deeper than its configured depth) — outside the extract. */
  def servesAt(z: com.histmersion.util.basemap.OnDemandTiles.ServesAtZ): com.histmersion.util.basemap.OnDemandTiles.ServesAtResult = cfg().enabled && z >= 0 && z <= cfg().maxZoom

  /** One request to the provider: within the concurrency and the rate. */
  private def request[A](f: => Either[String, A]): Either[com.histmersion.util.basemap.OnDemandTiles.RequestResultPart1, A] =
    if !gate.tryAcquire(60, TimeUnit.SECONDS) then Left("too many tile requests waiting")
    else
      try
        val wait = synchronized {
          val t = now()
          val start = math.max(t, nextStart)
          nextStart = start + (1000.0 / cfg().requestsPerSecond.max(0.1)).toLong
          start - t
        }
        if wait > 0 then sleep(wait)
        f
      finally gate.release()

  private def remoteJson(r: Remote) = Json.obj(
    "url" -> Json.fromString(r.url),
    "size" -> Json.fromLong(r.file.size),
    "etag" -> r.file.etag.fold(Json.Null)(Json.fromString)
  )

  /** The planet's description: in memory, else from `remote/` (when it names the same URL), else read. */
  private def described(): Either[com.histmersion.util.basemap.OnDemandTiles.DescribedResultPart1, Remote] =
    val u = url()
    remote
      .filter(_.url == u)
      .map(Right(_))
      .getOrElse(synchronized {
        remote.filter(_.url == u).map(Right(_)).getOrElse {
          val fromDisk = scala.util
            .Try {
              val j =
                io.circe.parser.parse(remoteFiles.get(OnDemandTiles.RemoteJson).get.text()).toOption.get.hcursor
              val head = remoteFiles.get(OnDemandTiles.Head).get.bytes()
              (
                j.get[String]("url").toOption.get,
                j.get[Long]("size").toOption.get,
                j.get[Option[String]]("etag").toOption.flatten,
                head
              )
            }
            .toOption
            .filter(_._1 == u)
          val head: Either[String, (Array[Byte], RemoteFile)] = fromDisk match
            case Some((_, size, etag, h)) => Right((h, RemoteFile(size, etag)))
            case None                     =>
              val _ = remoteFiles.deleteTree(Store.Key.Root)
              leaves.clear()
              request(http().bytes(u, 0, PmTiles.RootBytes)).map { (b, f) =>
                done(remoteFiles.write(OnDemandTiles.Head, b))
                (b, f)
              }
          for
            hf <- head
            (b, f) = hf
            h <- PmTiles.parseHeader(b)
            _ <- Either.cond(h.rootOffset + h.rootLength <= b.length, (), "root directory outside the first 16 KiB")
            root <- PmTiles
              .decompress(b.slice(h.rootOffset.toInt, (h.rootOffset + h.rootLength).toInt), h.internalCompression)
              .flatMap(PmTiles.decodeDirectory)
          yield
            val r = Remote(u, h, root, f)
            if fromDisk.isEmpty then
              done(remoteFiles.write(OnDemandTiles.RemoteJson, remoteJson(r).noSpaces.getBytes(UTF_8)))
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

  private def leaf(r: Remote, e: PmTiles.Entry): Either[com.histmersion.util.basemap.OnDemandTiles.LeafResultPart1, Vector[PmTiles.Entry]] =
    Option(leaves.get(e.offset)).map(Right(_)).getOrElse {
      val f = Store.Key.applyUnsafe(s"leaf-${e.offset}-${e.length}.bin")
      val raw = remoteFiles.get(f).filter(_.size.value == e.length) match
        case Some(kept) => Right(kept.bytes())
        case None       =>
          request(http().bytes(r.url, r.header.leafOffset + e.offset, e.length)).flatMap { (b, got) =>
            if got.etag != r.file.etag || got.size != r.file.size then Left(OnDemandTiles.Changed)
            else
              done(remoteFiles.write(f, b))
              Right(b)
          }
      raw.flatMap(b => PmTiles.decompress(b, r.header.internalCompression)).flatMap(PmTiles.decodeDirectory).map { d =>
        if leaves.size > 512 then leaves.clear()
        leaves.put(e.offset, d)
        d
      }
    }

  /** The planet's entry for a tile, through its leaf directories. */
  private def entry(r: Remote, z: com.histmersion.util.basemap.OnDemandTiles.EntryZ, x: com.histmersion.util.basemap.OnDemandTiles.EntryX, y: com.histmersion.util.basemap.OnDemandTiles.EntryY): Either[com.histmersion.util.basemap.OnDemandTiles.EntryResultPart1, Option[PmTiles.Entry]] =
    val id = PmTiles.tileId(z, x, y)
    def walk(d: Vector[PmTiles.Entry], depth: Int): Either[String, Option[PmTiles.Entry]] =
      PmTiles.find(d, id) match
        case None                  => Right(None)
        case Some(e) if !e.isLeaf  => Right(Some(e))
        case Some(_) if depth >= 3 => Left("PMTiles directories nested too deep")
        case Some(e)               => leaf(r, e).flatMap(walk(_, depth + 1))
    if z < r.header.minZoom || z > r.header.maxZoom then Right(None) else walk(r.root, 0)

  private def read(z: com.histmersion.util.basemap.OnDemandTiles.ReadZ, x: com.histmersion.util.basemap.OnDemandTiles.ReadX, y: com.histmersion.util.basemap.OnDemandTiles.ReadY): Either[com.histmersion.util.basemap.OnDemandTiles.ReadResultPart1, Option[com.histmersion.util.basemap.OnDemandTiles.ReadResultPart2]] =
    val raw = source match
      case OnDemandTiles.Source.Planet(_)   => readPlanet(z, x, y)
      case OnDemandTiles.Source.Template(u) =>
        request(http().file(u().replace("{z}", z.toString).replace("{x}", x.toString).replace("{y}", y.toString)))
    raw.flatMap(_.fold(Right(None))(transform))

  private def readPlanet(z: com.histmersion.util.basemap.OnDemandTiles.ReadPlanetZ, x: com.histmersion.util.basemap.OnDemandTiles.ReadPlanetX, y: com.histmersion.util.basemap.OnDemandTiles.ReadPlanetY): Either[com.histmersion.util.basemap.OnDemandTiles.ReadPlanetResultPart1, Option[com.histmersion.util.basemap.OnDemandTiles.ReadPlanetResultPart2]] =
    def once(): Either[String, Option[Array[Byte]]] =
      for
        r <- described()
        e <- entry(r, z, x, y)
        b <- e.fold(Right(None)) { e =>
          request(http().bytes(r.url, r.header.dataOffset + e.offset, e.length)).flatMap { (b, got) =>
            if got.etag != r.file.etag || got.size != r.file.size then Left(OnDemandTiles.Changed) else Right(Some(b))
          }
        }
      yield b
    once() match
      case Left(OnDemandTiles.Changed) => forget(); once()
      case other                       => other

  /** The tile `z/x/y`: from the cache, else from the provider (then cached). `Right(None)`: there is none (outside the
    * configured zooms, or the provider has none); `Left`: it could not be read now.
    */
  def tile(z: com.histmersion.util.basemap.OnDemandTiles.TileZ, x: com.histmersion.util.basemap.OnDemandTiles.TileX, y: com.histmersion.util.basemap.OnDemandTiles.TileY): Either[com.histmersion.util.basemap.OnDemandTiles.TileResultPart1, Option[com.histmersion.util.basemap.OnDemandTiles.TileResultPart2]] =
    val c = cfg()
    if !c.enabled || z > c.maxZoom || x < 0 || y < 0 || x >= (1L << z) || y >= (1L << z) then Right(None)
    else
      val key = s"$z/$x/$y"
      cache.get(key) match
        case Some(hit)                    => Right(hit)
        case None if offlineUntil > now() => Left("the provider was unreachable a moment ago; not asked again yet")
        case None                         =>
          val mine = CompletableFuture[Either[String, Option[Array[Byte]]]]()
          Option(inflight.putIfAbsent(key, mine)) match
            case Some(other) =>
              scala.util
                .Try(other.get(120, TimeUnit.SECONDS))
                .toEither
                .left
                .map(e => s"tile $key: ${e.getMessage}")
                .flatten
            case None =>
              try
                val r = read(z, x, y)
                r match
                  case Right(b) => cache.put(key, b)
                  case Left(e)  =>
                    offlineUntil = now() + c.retryAfterSeconds * 1000L
                    scribe.warn(s"basemap: on-demand tile $key (${store.where()}) not read: $e")
                mine.complete(r)
                r
              catch
                case e: Throwable =>
                  mine.complete(Left(s"tile $key: ${e.getClass.getSimpleName}"))
                  throw e
              finally { val _ = inflight.remove(key, mine) }

object OnDemandTiles:
  type ReadResultPart2 = ReadResultPart2.T
  object ReadResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadPlanetResultPart2 = ReadPlanetResultPart2.T
  object ReadPlanetResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TileResultPart2 = TileResultPart2.T
  object TileResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]

  type NowResult = NowResult.T
  object NowResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SleepArg1 = SleepArg1.T
  object SleepArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Ext = Ext.T
  object Ext extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TransformResultPart1 = TransformResultPart1.T
  object TransformResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type UrlResult = UrlResult.T
  object UrlResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ServesResult = ServesResult.T
  object ServesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ServesZ = ServesZ.T
  object ServesZ extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ServesExtractMax = ServesExtractMax.T
  object ServesExtractMax extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ServesAtResult = ServesAtResult.T
  object ServesAtResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ServesAtZ = ServesAtZ.T
  object ServesAtZ extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RequestResultPart1 = RequestResultPart1.T
  object RequestResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DescribedResultPart1 = DescribedResultPart1.T
  object DescribedResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LeafResultPart1 = LeafResultPart1.T
  object LeafResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EntryResultPart1 = EntryResultPart1.T
  object EntryResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EntryZ = EntryZ.T
  object EntryZ extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EntryX = EntryX.T
  object EntryX extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EntryY = EntryY.T
  object EntryY extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadResultPart1 = ReadResultPart1.T
  object ReadResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadZ = ReadZ.T
  object ReadZ extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadX = ReadX.T
  object ReadX extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadY = ReadY.T
  object ReadY extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadPlanetResultPart1 = ReadPlanetResultPart1.T
  object ReadPlanetResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadPlanetZ = ReadPlanetZ.T
  object ReadPlanetZ extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadPlanetX = ReadPlanetX.T
  object ReadPlanetX extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadPlanetY = ReadPlanetY.T
  object ReadPlanetY extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TileResultPart1 = TileResultPart1.T
  object TileResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TileZ = TileZ.T
  object TileZ extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TileX = TileX.T
  object TileX extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TileY = TileY.T
  object TileY extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val Tiles = Store.Key.applyUnsafe("tiles")
  private val Remote = Store.Key.applyUnsafe("remote")
  private val RemoteJson = Store.Key.applyUnsafe("remote.json")
  private val Head = Store.Key.applyUnsafe("head.bin")

  /** The provider's file changed under a read (another ETag or size). */
  val Changed = "the provider's file changed"

  /** Where tiles are read from: a PMTiles planet archive (range requests), or a tile URL with `{z}/{x}/{y}` (one GET a
    * tile; a 404 is "none").
    */
  enum Source:
    case Planet(url: () => Source.Planet.UrlResult)
    case Template(url: () => Source.Template.UrlResult)

  object Source:
    object Planet:
      type UrlResult = UrlResult.T
      object UrlResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    object Template:
      type UrlResult = UrlResult.T
      object UrlResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
