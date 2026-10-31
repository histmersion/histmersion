package com.histmersion.util.basemap

import com.histmersion.api.BasemapOnDemand
import com.histmersion.config.BasemapOnDemandConfig

import io.circe.Json

import java.nio.file.{Files, Path}
import java.util.concurrent.{CompletableFuture, ConcurrentHashMap, Semaphore, TimeUnit}

/** A layer's tiles read when a page shows them, beyond what was downloaded (docs/research-terrain-basemap.md,
  * "On-demand relief"; #3760 for the map and the sea floor): the server reads just those tiles from the provider — by
  * HTTP range requests from its planet archive ([[OnDemandTiles.Source.Planet]], the access its documentation shows for
  * extracts), or one GET a tile from a tile URL ([[OnDemandTiles.Source.Template]]) — `transform`s each (the vector
  * layer drops what the download drops) and keeps it in a bounded cache ([[DiskLru]], files `.<ext>`) next to the
  * extract, so an area seen once shows again offline.
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
    val dir: Path,
    cfg: () => BasemapOnDemandConfig,
    source: OnDemandTiles.Source,
    http: () => RangeHttp,
    now: () => Long = () => System.currentTimeMillis(),
    sleep: Long => Unit = ms => Thread.sleep(ms),
    ext: String = "webp",
    transform: Array[Byte] => Either[String, Option[Array[Byte]]] = b => Right(Some(b))
):

  private def url(): String = source match
    case OnDemandTiles.Source.Planet(u)   => u()
    case OnDemandTiles.Source.Template(u) => u()

  val cache: DiskLru = DiskLru(dir.resolve("tiles"), () => (cfg().cacheGb * 1e9).toLong, ext)
  private val remoteDir = dir.resolve("remote")

  /** What the planet file is: its header and root, the leaves read, its size and ETag. */
  final private case class Remote(url: String, header: PmTiles.Header, root: Vector[PmTiles.Entry], file: RemoteFile)

  // the open remote description (re-read when the file changes) and when requests may start again; under the lock
  @volatile private var remote: Option[Remote] = None
  @volatile private var offlineUntil = 0L
  private var nextStart = 0L
  private val leaves = ConcurrentHashMap[Long, Vector[PmTiles.Entry]]()
  private val inflight = ConcurrentHashMap[String, CompletableFuture[Either[String, Option[Array[Byte]]]]]()
  private lazy val gate = Semaphore(cfg().concurrency.max(1), true)

  def status: BasemapOnDemand =
    val c = cfg()
    BasemapOnDemand(
      c.enabled,
      c.maxZoom,
      dir.toString,
      cache.entries,
      cache.bytes,
      (c.cacheGb * 1e9).toLong,
      Option.when(offlineUntil > now())(java.time.Instant.ofEpochMilli(offlineUntil).toString)
    )

  /** Whether `z` is one this serves (deeper than the extract's `extractMax`, to the configured depth). */
  def serves(z: Int, extractMax: Int): Boolean = cfg().enabled && z > extractMax && z <= cfg().maxZoom

  /** Whether it serves tile `z` at all (on, and not deeper than its configured depth) — outside the extract. */
  def servesAt(z: Int): Boolean = cfg().enabled && z >= 0 && z <= cfg().maxZoom

  /** One request to the provider: within the concurrency and the rate. */
  private def request[A](f: => Either[String, A]): Either[String, A] =
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
  private def described(): Either[String, Remote] =
    val u = url()
    remote
      .filter(_.url == u)
      .map(Right(_))
      .getOrElse(synchronized {
        remote.filter(_.url == u).map(Right(_)).getOrElse {
          val fromDisk = scala.util
            .Try {
              val j = io.circe.parser.parse(Files.readString(remoteDir.resolve("remote.json"))).toOption.get.hcursor
              val head = Files.readAllBytes(remoteDir.resolve("head.bin"))
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
              BasemapService.deleteTree(remoteDir)
              leaves.clear()
              request(http().bytes(u, 0, PmTiles.RootBytes)).map { (b, f) =>
                Files.createDirectories(remoteDir)
                Files.write(remoteDir.resolve("head.bin"), b)
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
            if fromDisk.isEmpty then Files.writeString(remoteDir.resolve("remote.json"), remoteJson(r).noSpaces)
            remote = Some(r)
            r
        }
      })

  /** The provider replaced its file: forget what was read of it (the tiles kept stay). */
  private def forget(): Unit = synchronized {
    remote = None
    leaves.clear()
    val _ = BasemapService.deleteTree(remoteDir)
  }

  private def leaf(r: Remote, e: PmTiles.Entry): Either[String, Vector[PmTiles.Entry]] =
    Option(leaves.get(e.offset)).map(Right(_)).getOrElse {
      val f = remoteDir.resolve(s"leaf-${e.offset}-${e.length}.bin")
      val raw =
        if Files.isRegularFile(f) && Files.size(f) == e.length then Right(Files.readAllBytes(f))
        else
          request(http().bytes(r.url, r.header.leafOffset + e.offset, e.length)).flatMap { (b, got) =>
            if got.etag != r.file.etag || got.size != r.file.size then Left(OnDemandTiles.Changed)
            else
              Files.createDirectories(remoteDir)
              Files.write(f, b)
              Right(b)
          }
      raw.flatMap(b => PmTiles.decompress(b, r.header.internalCompression)).flatMap(PmTiles.decodeDirectory).map { d =>
        if leaves.size > 512 then leaves.clear()
        leaves.put(e.offset, d)
        d
      }
    }

  /** The planet's entry for a tile, through its leaf directories. */
  private def entry(r: Remote, z: Int, x: Long, y: Long): Either[String, Option[PmTiles.Entry]] =
    val id = PmTiles.tileId(z, x, y)
    def walk(d: Vector[PmTiles.Entry], depth: Int): Either[String, Option[PmTiles.Entry]] =
      PmTiles.find(d, id) match
        case None                  => Right(None)
        case Some(e) if !e.isLeaf  => Right(Some(e))
        case Some(_) if depth >= 3 => Left("PMTiles directories nested too deep")
        case Some(e)               => leaf(r, e).flatMap(walk(_, depth + 1))
    if z < r.header.minZoom || z > r.header.maxZoom then Right(None) else walk(r.root, 0)

  private def read(z: Int, x: Long, y: Long): Either[String, Option[Array[Byte]]] =
    val raw = source match
      case OnDemandTiles.Source.Planet(_)   => readPlanet(z, x, y)
      case OnDemandTiles.Source.Template(u) =>
        request(http().file(u().replace("{z}", z.toString).replace("{x}", x.toString).replace("{y}", y.toString)))
    raw.flatMap(_.fold(Right(None))(transform))

  private def readPlanet(z: Int, x: Long, y: Long): Either[String, Option[Array[Byte]]] =
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
  def tile(z: Int, x: Long, y: Long): Either[String, Option[Array[Byte]]] =
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
                    scribe.warn(s"basemap: on-demand tile $key (${dir.getFileName}) not read: $e")
                mine.complete(r)
                r
              catch
                case e: Throwable =>
                  mine.complete(Left(s"tile $key: ${e.getClass.getSimpleName}"))
                  throw e
              finally { val _ = inflight.remove(key, mine) }

object OnDemandTiles:
  /** The provider's file changed under a read (another ETag or size). */
  val Changed = "the provider's file changed"

  /** Where tiles are read from: a PMTiles planet archive (range requests), or a tile URL with `{z}/{x}/{y}` (one GET a
    * tile; a 404 is "none").
    */
  enum Source:
    case Planet(url: () => String)
    case Template(url: () => String)
