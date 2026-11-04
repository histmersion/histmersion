package com.histmersion.server

import hearth.kindlings.optics.syntax.*

import com.histmersion.util.settings.ConfigSource

import com.histmersion.config.*
import com.histmersion.domain.*

import com.histmersion.util.data.maintenance.PgRelocation
import com.histmersion.util.process.{Processes, ProcessLaunch, ProcessOutput, RunningProcess}
import com.histmersion.util.storage.{Location, Storage}

import java.nio.file.{Files, Path}
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*
import com.histmersion.api.backup.Backup
import com.histmersion.util.storage.Store

/** The native bundle (nothing installed system-wide): the server runs the bundled PostgreSQL and OCR converter as its
  * own child processes. Enabled by `runtime.enabled` (`HISTMERSION_RUNTIME=true`).
  *
  * {{{
  *   <home>/bin/histmersion-server        this process
  *   <home>/pg/{bin,lib,share}       PostgreSQL 18 + PostGIS (runtime/postgres, conda-forge)
  *   <home>/converter/{python,app.py,models}   scripts/bundle/converter.sh
  *   <home>/db/{places,periods}      the first-run gazetteer and period presets (scripts/bundle/server.sh)
  *   <data>/pg                       the cluster (initdb on first run)
  *   <data>/logs/{postgres,converter}.log
  *   <data>/backups  <data>/uploads  <data>/bundles
  * }}}
  *
  * Postgres listens on 127.0.0.1 only and never on a Unix socket: macOS caps a socket path at 103 bytes, which a data
  * directory under "Application Support" can exceed. The converter is restarted with backoff when it dies; both stop in
  * reverse order when the server does.
  */
object Supervisor:

  final case class Layout(home: Path, data: Path):
    def pgBin: Path = home.resolve("pg").resolve("bin")
    def projData: Path = home.resolve("pg").resolve("share").resolve("proj")
    def cluster: Path = data.resolve("pg")
    def logs: Path = data.resolve("logs")
    def backups: Path = data.resolve("backups")
    def uploads: Path = data.resolve("uploads")
    def bundles: Path = data.resolve("bundles")
    def converterDir: Path = home.resolve("converter")
    def python: Path = converterDir.resolve("python").resolve("bin").resolve("python3")
    def models: Path = converterDir.resolve("models")
    def initData: Path = home.resolve("db")

  /** Where the bundle and its data live. Pure: `exe` is this executable, `env` the environment. */
  private[server] def layout(
      rc: RuntimeConfig,
      exe: Option[Path],
      env: String => Option[String],
      osName: String
  ): Either[String, Layout] =
    val home = Option(rc.home)
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(Path.of(_))
      .orElse(exe.flatMap(e => Option(e.toAbsolutePath.getParent)).flatMap(b => Option(b.getParent)))
    val data = UserPaths.dataDir(rc.dataDir, env, osName)
    for
      h <- home.toRight("runtime.home is empty and the executable's location is unknown (set HISTMERSION_HOME)")
      d <- data.toRight("runtime.data-dir is empty and HOME is unset (set HISTMERSION_DATA_DIR)")
    yield Layout(h.toAbsolutePath.normalize, d.toAbsolutePath.normalize)

  /** The server options every start uses: loopback TCP only, no Unix socket (see the class doc), and the memory
    * settings ([[pgSettings]]).
    */
  private[server] def pgOptions(port: Int, settings: List[(String, String)] = Nil): String =
    (s"-p $port -c listen_addresses=127.0.0.1 -c unix_socket_directories=''" ::
      settings.map((k, v) => s"-c $k=$v")).mkString(" ")

  private[server] def pgStart(l: Layout, port: Int, settings: List[(String, String)] = Nil): List[String] =
    List(
      l.pgBin.resolve("pg_ctl").toString,
      "-D",
      l.cluster.toString,
      "-l",
      l.logs.resolve("postgres.log").toString,
      "-w",
      "-t",
      "300",
      "-o",
      pgOptions(port, settings),
      "start"
    )

  private val MB = 1024L * 1024L

  /** A Postgres size for `bytes`, in whole megabytes. */
  private def mb(bytes: Long): String = s"${math.max(1L, bytes / MB)}MB"

  /** A configured size: a Postgres size (letters and digits only, so nothing can ride along into the `-o` string),
    * `auto` from `ramBytes`, or nothing (Postgres' default — also for `auto` when the machine's memory is unknown).
    */
  private def size(v: String, auto: Long => Long, ramBytes: Option[Long]): Option[String] =
    v.trim match
      case ""                                         => None
      case a if a.equalsIgnoreCase("auto")            => ramBytes.map(r => mb(auto(r)))
      case s if s.matches("[0-9]+\\s*(kB|MB|GB|TB)?") => Some(s.replaceAll("\\s", ""))
      case other                                      =>
        scribe.warn(s"runtime.postgres: '$other' is not a Postgres size (e.g. 512MB, 2GB) — left to Postgres")
        None

  /** Pure: the `-c` settings the bundled Postgres starts with, given the machine's RAM.
    *
    *   - `shared_buffers` `auto` = RAM/16 within 128 MB…2 GB: initdb's 128 MB holds a fraction of the gazetteer's
    *     indexes, so a place search or a text query re-read its pages through the kernel on every keystroke (2 GB on a
    *     48 GB machine: warm interactive p50s 10–60 % lower, 2026-10-09);
    *   - `effective_cache_size` `auto` = RAM/2 — what the planner may assume the OS caches;
    *   - `work_mem` — a sort or hash per query node (16 MB: a 24 000-row candidate sort stays in memory).
    */
  private[server] def pgSettings(t: PgTuning, ramBytes: Option[Long]): List[(String, String)] =
    List(
      "shared_buffers" -> size(t.sharedBuffers, r => math.max(128 * MB, math.min(2048 * MB, r / 16)), ramBytes),
      "effective_cache_size" -> size(t.effectiveCacheSize, r => math.max(512 * MB, r / 2), ramBytes),
      "work_mem" -> size(t.workMem, _ => 16 * MB, ramBytes)
    ).collect { case (k, Some(v)) => k -> v }

  /** The machine's physical memory, when the platform says: `hw.memsize` on macOS, `MemTotal` on Linux. */
  private def physicalMemory(osName: String): Option[Long] =
    scala.util
      .Try {
        if osName.toLowerCase.contains("mac") then
          processes
            .run(ProcessLaunch(List("/usr/sbin/sysctl", "-n", "hw.memsize")))
            .toOption
            .flatMap(_.lines.mkString("\n").trim.toLongOption)
        else
          Files
            .readAllLines(Path.of("/proc/meminfo"))
            .asScala
            .collectFirst { case l if l.startsWith("MemTotal:") => l.replaceAll("[^0-9]", "").toLong * 1024L }
      }
      .toOption
      .flatten

  private[server] def pgStop(l: Layout): List[String] =
    List(l.pgBin.resolve("pg_ctl").toString, "-D", l.cluster.toString, "-m", "fast", "-w", "stop")

  private[server] def initdb(l: Layout, user: String, pwFile: Path): List[String] =
    List(
      l.pgBin.resolve("initdb").toString,
      "-D",
      l.cluster.toString,
      "-U",
      user,
      "--auth=scram-sha-256",
      s"--pwfile=$pwFile",
      "-E",
      "UTF8",
      "--locale=C"
    )

  private[server] def converterCmd(l: Layout, port: Int): List[String] =
    List(l.python.toString, "-m", "uvicorn", "app:app", "--host", "127.0.0.1", "--port", port.toString)

  /** The configuration with every endpoint and directory pointed at the bundle. Settings the user changed explicitly (a
    * backups dir other than the default, a pg-bin-dir) are kept.
    */
  private[server] def configure(cfg: AppConfig, l: Layout, explicitBind: Option[String] = None): AppConfig =
    val dbName = Backup.parseJdbc(cfg.db.url).toOption.map(_.database).filter(_.nonEmpty).getOrElse("histmersion")
    cfg.copy(
      // a desktop install is private by default, so the bind address is loopback unless the user
      // says otherwise (set HISTMERSION_BIND=0.0.0.0 to share on the LAN — and set HISTMERSION_API_KEY then)
      server = cfg.server.modify(_.host).setTo(explicitBind.filter(_.nonEmpty).getOrElse("127.0.0.1")),
      db = cfg.db.modify(_.url).setTo(s"jdbc:postgresql://127.0.0.1:${cfg.runtime.pgPort}/$dbName"),
      converter =
        cfg.converter.modify(_.url).setTo(s"http://127.0.0.1:${cfg.runtime.converterPort}").modify(_.spoolDir).setTo(l.uploads.toString),
      backups = cfg.backups.modify(
        _.dir).setTo(if cfg.backups.dir == DefaultBackupsDir then l.backups.toString else cfg.backups.dir).modify(_.pgBinDir).setTo(Option(cfg.backups.pgBinDir).filter(_.nonEmpty).getOrElse(l.pgBin.toString)
      ),
      bundles = cfg.bundles
        .modify(_.dir)
        .setTo(if cfg.bundles.dir == DefaultBundlesDir then l.bundles.toString else cfg.bundles.dir),
      // the first-run data ships inside the bundle (scripts/bundle/server.sh copies db/ there)
      init = cfg.init
        .modify(_.dataDir)
        .setTo(if cfg.init.dataDir == DefaultInitDataDir then l.initData.toString else cfg.init.dataDir)
    )

  /** `backups.dir`'s reference.conf value — relative, i.e. meaningless for a bundle. */
  private val DefaultBackupsDir = "backups"

  /** `bundles.dir`'s reference.conf value — relative too. */
  private val DefaultBundlesDir = "bundles"

  /** `init.data-dir`'s reference.conf value — the repo's `db/`, relative to a checkout. */
  private val DefaultInitDataDir = "db"

  /** True when `exe` sits in a bundle (`<home>/bin/<exe>` beside `<home>/pg/bin/pg_ctl`): running the bundled server
    * needs no configuration at all.
    */
  def bundled(exe: Option[Path]): Boolean =
    exe
      .flatMap(e => Option(e.toAbsolutePath.getParent))
      .flatMap(b => Option(b.getParent))
      .exists(h => Files.isExecutable(h.resolve("pg").resolve("bin").resolve("pg_ctl")))

  def currentExecutable: Option[Path] =
    scala.util.Try(ProcessHandle.current().info().command().toScala).toOption.flatten.map(Path.of(_))

  // ── effects ──────────────────────────────────────────────────────────────────────────────────

  private val stopping = AtomicBoolean(false)
  private val converter = AtomicReference[Option[RunningProcess]](None)
  private val processes: Processes = Processes()

  /** Start (or adopt) the bundled Postgres and the converter, register their shutdown, and return the configuration
    * that points at them. Throws with an actionable message when the bundle is incomplete — the server cannot run
    * without its database.
    */
  def start(cfg: AppConfig, paths: UserPaths): AppConfig =
    val exe = currentExecutable
    val l = layout(cfg.runtime, exe, paths.environment.lookup, paths.osName)
      .fold(e => throw IllegalStateException(e), identity)
    if !Files.isExecutable(l.pgBin.resolve("pg_ctl")) then
      throw IllegalStateException(
        s"no PostgreSQL in the bundle: ${l.pgBin.resolve("pg_ctl")} is missing (HISTMERSION_HOME=${l.home})"
      )
    List(l.data, l.logs, l.backups, l.uploads, l.bundles).foreach(d => Files.createDirectories(d))
    scribe.info(s"runtime: bundle ${l.home}, data ${l.data}")
    PgRelocation
      .relocate(Storage.at(Location.Postgres, l.pgBin.getParent), paths.isMac, processes)
      .foreach(r =>
        scribe.info(
          s"runtime: relocated PostgreSQL to ${l.pgBin.getParent} " +
            s"(${r.files} files patched, ${r.signed} re-signed, ${r.tooLong} strings too long to patch)"
        )
      )

    val fresh = !Files.exists(l.cluster.resolve("PG_VERSION"))
    if fresh then initCluster(l, cfg.db.user, cfg.db.password)
    if run(List(l.pgBin.resolve("pg_ctl").toString, "-D", l.cluster.toString, "status"), l)._1 == 0 then
      scribe.info("runtime: postgres already running on this cluster — adopting it")
    else
      val settings = pgSettings(cfg.runtime.postgres, physicalMemory(paths.osName))
      scribe.info(s"runtime: postgres memory ${settings.map((k, v) => s"$k=$v").mkString(", ")}")
      val (rc, out) = run(pgStart(l, cfg.runtime.pgPort, settings), l)
      if rc != 0 then
        throw IllegalStateException(
          s"postgres did not start (exit $rc): ${out.takeRight(5).mkString("; ")} — see ${l.logs.resolve("postgres.log")}"
        )
      scribe.info(s"runtime: postgres up on 127.0.0.1:${cfg.runtime.pgPort}")
    val configured = configure(cfg, l, paths.environment.get("HISTMERSION_BIND"))
    ensureDatabase(configured)

    if Files.isExecutable(l.python) then superviseConverter(l, cfg.runtime.converterPort)
    else scribe.warn(s"runtime: no converter in the bundle (${l.python}); OCR ingest is unavailable")

    Runtime.getRuntime.addShutdownHook(Thread(() => shutdown(l)))
    configured

  /** How a config reload is fitted to the running bundle (docs/research-plugins.md §3.5): every reloaded config is
    * pointed at the bundled Postgres and converter the way [[start]] pointed the first one (the children themselves,
    * `runtime.*`, are restart-only), and a database the reload names is created in the bundled cluster before a pool
    * opens on it. `started` is the config [[start]] was given.
    */
  def reloadSource(started: AppConfig, read: () => Either[String, LoadedConfig], paths: UserPaths): ConfigSource =
    val l = layout(started.runtime, currentExecutable, paths.environment.lookup, paths.osName)
      .fold(e => throw IllegalStateException(e), identity)
    val bind = paths.environment.get("HISTMERSION_BIND")
    ConfigSource(
      read,
      c => configure(c, l, bind),
      c => scala.util.Try(ensureDatabase(c)).toEither.left.map(e => s"cannot create the database: ${e.getMessage}")
    )

  private def initCluster(l: Layout, user: String, password: String): Unit =
    val pw = Files.createTempFile("histmersion-pw", "")
    try
      Files.writeString(pw, password)
      Files.createDirectories(l.cluster.getParent)
      scribe.info(s"runtime: first run — initialising the database cluster in ${l.cluster}")
      val (rc, out) = run(initdb(l, user, pw), l)
      if rc != 0 then throw IllegalStateException(s"initdb failed (exit $rc): ${out.takeRight(5).mkString("; ")}")
    finally { val _ = Files.deleteIfExists(pw) }

  /** `CREATE DATABASE` on first run (the cluster starts with only `postgres`). */
  private def ensureDatabase(cfg: AppConfig): Unit =
    val target = Backup.parseJdbc(cfg.db.url).fold(e => throw IllegalStateException(e), identity)
    val adminUrl = cfg.db.url.take(cfg.db.url.lastIndexOf('/') + 1) + "postgres"
    val c = java.sql.DriverManager.getConnection(adminUrl, cfg.db.user, cfg.db.password)
    try
      val q = c.prepareStatement("SELECT 1 FROM pg_database WHERE datname = ?")
      q.setString(1, target.database)
      if !q.executeQuery().next() then
        val quoted = "\"" + target.database.replace("\"", "\"\"") + "\""
        val _ = c.createStatement().execute(s"CREATE DATABASE $quoted")
        scribe.info(s"runtime: created database ${target.database}")
    finally c.close()

  /** Run the converter; when it exits unexpectedly, restart it after 1, 2, 4 … 60 s (reset once a run has lasted a
    * minute).
    */
  private def superviseConverter(l: Layout, port: Int): Unit =
    val t = Thread.ofPlatform().daemon(true).name("histmersion-converter").unstarted { () =>
      var backoffMs = 1000L
      while !stopping.get() do
        val launch = ProcessLaunch(
          converterCmd(l, port),
          workingStore = Some(Storage.at(Location.Install, l.converterDir)),
          environment = Map(
            "DOCLING_ARTIFACTS_PATH" -> l.models.toString,
            "HF_HUB_OFFLINE" -> "1",
            "HISTMERSION_CONVERTER_PORT" -> port.toString
          ),
          output = ProcessOutput.AppendTo(Storage.at(Location.Logs, l.logs), Store.Key("converter.log"))
        )
        val started = System.nanoTime()
        val p = processes.start(launch).fold(e => throw java.io.IOException(e), identity)
        converter.set(Some(p))
        scribe.info(s"runtime: converter started on 127.0.0.1:$port (pid ${p.pid})")
        val code = p.waitFor()
        converter.set(None)
        if !stopping.get() then
          if (System.nanoTime() - started) > 60_000_000_000L then backoffMs = 1000L
          scribe.warn(
            s"runtime: converter exited ($code); restarting in ${backoffMs / 1000} s — see ${l.logs.resolve("converter.log")}"
          )
          Thread.sleep(backoffMs)
          backoffMs = math.min(backoffMs * 2, 60000L)
    }
    t.start()

  private def shutdown(l: Layout): Unit =
    stopping.set(true)
    converter.get().foreach { p =>
      p.destroy()
      if !p.waitFor(10, TimeUnit.SECONDS) then { val _ = p.destroyForcibly() }
    }
    val (rc, out) = run(pgStop(l), l)
    if rc != 0 then scribe.error(s"Histmersion: postgres stop failed (exit $rc): ${out.mkString("; ")}")

  /** Run a Postgres tool with the bundle's environment; returns (exit code, output lines). */
  private def run(cmd: List[String], l: Layout): (Int, List[String]) =
    processes
      .run(ProcessLaunch(cmd, environment = Map("PROJ_DATA" -> l.projData.toString)))
      .fold(e => throw java.io.IOException(e), r => (r.exitCode, r.lines))
