package com.histmersion.server

import com.histmersion.core.*
import com.histmersion.db.StatsRepo
import com.histmersion.server.agents.AgentTrees
import com.histmersion.server.basemap.BasemapService
import com.histmersion.server.importers.GazetteerLoad

import java.nio.file.{Files, Path}
import java.time.Instant
import java.util.concurrent.{CompletableFuture, ExecutionException, TimeUnit, TimeoutException}
import java.util.concurrent.atomic.AtomicReference
import javax.sql.DataSource

/** The Storage part of About: every place this server keeps data, found from its config the way the
  * code that writes there finds it (the same resolvers, never a guess), and measured with a bounded
  * walk ([[DirSize]]). [[StorageCache]] runs it in the background, so About itself never walks a disk.
  *
  * {{{
  *   database          pg_database_size + the largest tables; in the bundle also <data>/pg on disk
  *   backups           backups.dir (count, total, newest; backups.keep)
  *   corpus            converter.spool-dir (the stored originals) · bundles.dir · acquire.spool-dir · corpus.dir
  *   caches            the gazetteer extracts and drop folder · basemap.dir · any other <cache dir>/<x>
  *   install           the bundle (bin, pg, converter) · its relink kit · <data>/toolchain · <data>/plugins · agents
  *   logs              <data>/logs
  *   other             whatever else is in the data directory
  * }}} */
object StorageProbe:

  /** One place to measure: `paths` are summed (several only for "other in the data directory"),
    * `shown` is the path reported (None: shown in `detail` instead, so nothing nests under it). */
  final case class Location(key: String, label: String, group: String, paths: List[Path], shown: Option[Path],
      detail: Option[String] = None, within: Option[String] = None)

  object Location:
    def at(key: String, label: String, group: String, p: Path, within: Option[String] = None): Location =
      val n = p.toAbsolutePath.normalize
      Location(key, label, group, List(n), Some(n), within = within)

  /** Where things are, worked out once per measurement from the config and the environment. */
  final case class Places(cfg: AppConfig, data: Option[Path], cache: Option[Path], home: Option[Path], bundled: Boolean)

  def places(cfg: AppConfig, env: String => Option[String], osName: String, exe: Option[Path]): Places =
    val bundled = cfg.runtime.enabled || (env("HISTMERSION_RUNTIME").forall(_.trim.isEmpty) && Supervisor.bundled(exe))
    val home = Option.when(bundled)(Supervisor.layout(cfg.runtime, exe, env, osName).toOption.map(_.home)).flatten
    Places(cfg, UserPaths.dataDir(cfg.runtime.dataDir, env, osName).map(_.toAbsolutePath.normalize),
      UserPaths.cacheDir(env), home, bundled)

  private def configured(s: String): Option[Path] =
    Option(s).map(_.trim).filter(_.nonEmpty).map(p => Path.of(AgentTrees.expand(p)))

  /** Every location, in the order About lists them. Reads the file system only to find the
    * leftovers of the data and cache directories (their children no other location covers). */
  def locations(p: Places, env: String => Option[String]): List[Location] =
    val cfg  = p.cfg
    val gaz  = GazetteerLoad.paths(cfg, env)
    val named: List[Location] = List(
      p.data.filter(_ => p.bundled).map(d => Location.at("database-cluster", "database files (bundled Postgres)", "database", d.resolve("pg"))),
      Some(Location.at("backups", "backups", "backups", Path.of(cfg.backups.dir))),
      configured(cfg.converter.spoolDir).map(Location.at("originals", "stored originals (uploads)", "corpus", _)),
      configured(cfg.acquire.spoolDir).map(Location.at("acquire-spool", "acquisition scratch", "corpus", _)),
      configured(cfg.corpus.dir).map(Location.at("corpus", "corpus folder (seed)", "corpus", _)),
      configured(cfg.bundles.dir).map(Location.at("bundles", "bundle scratch", "corpus", _)),
      Some(Location.at("gazetteer", "gazetteer extracts (cache)", "caches", gaz.extracts)),
      gaz.legacy.map(Location.at("gazetteer-data", "gazetteer folder (data)", "caches", _)),
      Some(Location.at("gazetteer-lpf", "gazetteer LPF drop folder", "caches", gaz.lpf)),
      Some(Location.at("basemap", "terrain base map (cache)", "caches", BasemapService.dir(cfg, env))),
      p.home.map(Location.at("install", "install (binaries, Postgres, converter)", "install", _)),
      p.home.map(h => Location.at("converter", "OCR converter (Python, models)", "install", h.resolve("converter"))),
      p.home.map(h => Location.at("relink-kit", "plugin relink kit", "install", h.resolve("kit"))),
      p.data.map(d => Location.at("toolchain", "GraalVM toolchain", "install", d.resolve("toolchain"))),
      p.data.map(d => Location.at("plugins", "plugins", "install", d.resolve("plugins"))),
      AgentTrees.root(cfg, env).map(Location.at("agents", "agent definitions", "install", _)),
      AgentTrees.codexHome(cfg, env).map(Location.at("codex-home", "Codex home of the agent runs (transcripts, state)", "other", _)),
      p.data.map(d => Location.at("logs", "logs", "logs", d.resolve("logs")))
    ).flatten
    val shown = named.flatMap(_.shown)
    def covered(c: Path) = shown.exists(s => Storage.under(s.toString, c.toString) || Storage.under(c.toString, s.toString))
    val caches = p.cache.toList.flatMap(DirSize.children).filterNot(covered).map(c =>
      Location.at(s"cache:${c.getFileName}", s"cache: ${c.getFileName}", "caches", c))
    val rest = p.data.toList.flatMap(d => DirSize.children(d).filterNot(covered) match
      case Nil  => Nil
      case some =>
        val names = some.map(_.getFileName.toString)
        List(Location("data-other", "other files in the data directory", "other", some, None,
          detail = Some(s"$d: ${names.take(8).mkString(", ")}${if names.size > 8 then s" and ${names.size - 8} more" else ""}"))))
    named ++ caches ++ rest

  /** The base-map files, one by one, and an unfinished download (`.<layer>.part`). */
  private def basemapDetail(dir: Path, budget: DirSize.Budget): Option[String] =
    val parts = BasemapLayer.values.toList.flatMap { l =>
      val f = dir.resolve(l.file)
      Option.when(Files.isRegularFile(f))(s"${l.wire} ${Backup.humanBytes(scala.util.Try(Files.size(f)).getOrElse(0L))}") ++
        Option.when(Files.isDirectory(dir.resolve(s".${l.wire}.part")))(
          s"${l.wire} download unfinished ${Backup.humanBytes(DirSize.measure(dir.resolve(s".${l.wire}.part"), budget).bytes)}")
    }
    // the tiles read on demand (#3760): the relief deeper, the map and the sea floor outside the region
    val caches = List("terrain-cache" -> "relief cache", "vector-cache" -> "map cache", "sea-cache" -> "sea-floor cache").flatMap { (d, label) =>
      val p = dir.resolve(d)
      Option.when(Files.isDirectory(p))(s"$label ${Backup.humanBytes(DirSize.measure(p, budget).bytes)}")
    }
    Option.when(parts.nonEmpty || caches.nonEmpty)((parts ++ caches).mkString(" · "))

  /** Measure every location within `budget`; the database through `ds` when there is one. */
  def measure(p: Places, env: String => Option[String], ds: Option[DataSource], backups: => Either[String, List[BackupInfo]],
      budget: DirSize.Budget, now: () => Instant = () => Instant.now()): StorageReport =
    val started = System.nanoTime()
    val cfg     = p.cfg
    val locs    = locations(p, env)
    val problems = List.newBuilder[String]
    val measured = locs.map { l =>
      val results = l.paths.map(DirSize.measure(_, budget))
      val exists  = results.exists(_.exists)
      val unread  = results.map(_.unreadable).sum
      if unread > 0 then problems += s"${l.label}: ${Plural.count(unread, "entry", "entries")} could not be read"
      val extra = if l.key == "basemap" then l.shown.flatMap(basemapDetail(_, budget)) else None
      StorageEntry(
        key = l.key, label = l.label, group = l.group,
        path = l.shown.map(_.toString),
        bytes = Option.when(exists)(results.map(_.bytes).sum),
        files = Option.when(exists)(results.map(_.files).sum),
        exists = exists,
        partial = results.exists(_.partial),
        within = l.within,
        detail = (l.detail.toList ++ extra.toList).reduceOption(_ + " · " + _),
        shrink = StorageAdvice.shrink(l.key, cfg.backups.keep),
        volume = l.paths.headOption.flatMap(DirSize.volume).map(_.id))
    }
    val volumes = locs.flatMap(_.paths.headOption).flatMap(DirSize.volume).groupBy(_.id).values.map(_.head).toList.sortBy(_.id)
    val (dbEntry, tables) = ds.fold((Option.empty[StorageEntry], List.empty[TableSize])) { d =>
      val size = scala.util.Try(StatsRepo.databaseSize(d)).toEither.left.map(e => s"database size: ${e.getMessage}")
      val top  = scala.util.Try(StatsRepo.largestTables(d, cfg.storage.topTables)).toEither.left.map(e => s"largest tables: ${e.getMessage}")
      size.left.foreach(problems += _)
      top.left.foreach(problems += _)
      (Some(StorageEntry("database", "database (Postgres)", "database",
        bytes = size.toOption.map(_._2), exists = size.isRight,
        within = Option.when(measured.exists(e => e.key == "database-cluster" && e.exists))("database-cluster"),
        detail = size.toOption.map(_._1), shrink = StorageAdvice.shrink("database", cfg.backups.keep))),
        top.getOrElse(Nil))
    }
    val listed = backups
    listed.left.foreach(e => problems += s"backups: $e")
    val usage = listed.toOption.map(bs =>
      BackupsUsage(bs.size, bs.map(_.bytes).sum, bs.headOption.map(_.file), cfg.backups.keep))
    val report = StorageReport(
      measuredAt = Some(now().toString),
      tookMillis = Some((System.nanoTime() - started) / 1_000_000L),
      entries = Storage.nest(dbEntry.toList ++ measured),
      tables = tables,
      volumes = volumes,
      backups = usage,
      lowFreeBytes = cfg.storage.lowFreeBytes,
      problems = problems.result() ++
        Option.when(budget.exhausted)(s"the measurement stopped at its cap (storage.max-millis ${cfg.storage.maxMillis}, " +
          s"storage.max-entries ${cfg.storage.maxEntries}): sizes marked \"at least\" are lower bounds").toList)
    Storage.withHints(report)

/** The last [[StorageProbe]] measurement, reused for `ttl` and measured again in the background
  * after it — ONE measurement at a time, however many Abouts ask. A request that finds nothing
  * measured yet waits up to `wait` for the first one, then answers "measuring…"; one that finds an
  * old measurement gets it at once (marked measuring) while the next one runs.
  *
  * @param prepare called on the requesting thread (it pins the work to that thread's config
  *                generation), returns the measurement to run on another thread
  * @param spawn   runs it (a virtual thread; a test runs it inline or holds it) */
final class StorageCache(prepare: () => () => StorageReport, ttlMillis: () => Long,
    clock: () => Long = () => System.currentTimeMillis(),
    spawn: (() => Unit) => Unit = r => { val _ = Thread.ofVirtual().name("histmersion-storage").start(() => r()) }):

  private val last    = AtomicReference[Option[(Long, StorageReport)]](None)
  private val running = AtomicReference[Option[CompletableFuture[StorageReport]]](None)

  def get(waitMillis: Long): StorageReport =
    last.get match
      case Some((at, r)) if clock() - at < ttlMillis() => r
      case Some((_, r)) =>
        val _ = kick()
        r.copy(measuring = true)
      case None =>
        val f = kick()
        try f.get(math.max(0L, waitMillis), TimeUnit.MILLISECONDS)
        catch
          case _: TimeoutException     => StorageReport(measuring = true)
          case e: ExecutionException   => StorageReport(problems = List(s"storage: ${Option(e.getCause).getOrElse(e).getMessage}"))
          case _: InterruptedException => StorageReport(measuring = true)

  /** The measurement under way, or a new one. */
  private def kick(): CompletableFuture[StorageReport] =
    running.get match
      case Some(f) => f
      case None =>
        val f = CompletableFuture[StorageReport]()
        val claimed: Option[CompletableFuture[StorageReport]] = Some(f)
        if !running.compareAndSet(None, claimed) then kick()
        else
          val work = scala.util.Try(prepare())
          spawn { () =>
            try
              val r = work.get()
              last.set(Some((clock(), r)))
              f.complete(r)
            catch case e: Throwable =>
              scribe.warn(s"storage: the measurement failed: ${e.getMessage}")
              f.completeExceptionally(e)
            finally running.set(None)
            ()
          }
          f
