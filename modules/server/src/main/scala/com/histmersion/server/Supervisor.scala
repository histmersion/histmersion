package com.histmersion
package server
import com.histmersion.util.problem.{External, Legacy, Problem, Problems, Raised}
import com.histmersion.util.problem.Text.sentence
import com.histmersion.util.database.InstanceLock

import hearth.kindlings.optics.syntax.*

import com.histmersion.util.settings.ConfigSource

import com.histmersion.config.*
import com.histmersion.domain.*

import com.histmersion.util.data.maintenance.PgRelocation
import com.histmersion.util.process.{Processes, ProcessLaunch, ProcessOutput, RunningProcess}
import com.histmersion.util.storage.{Location, Storage}

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, StandardCopyOption}
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*
import com.histmersion.api.backup.Backup
import com.histmersion.util.storage.Store
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.config.Environment
import com.histmersion.util.plugins.PluginKit
import com.histmersion.lint.{FeaturePlace, movesTo}
import com.histmersion.lint.{LintRule, tolerated}

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
@movesTo(FeaturePlace.Server)
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
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  private[server] def layout(
      rc: RuntimeConfig,
      exe: Option[Path],
      env: UserPaths.Lookup,
      osName: UserPaths.OsName
  ): Either[Problem, Layout] =
    val home = Option(rc.home.value)
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(Path.of(_))
      .orElse(exe.flatMap(e => Option(e.toAbsolutePath.getParent)).flatMap(b => Option(b.getParent)))
    val data = UserPaths.dataDir(rc.dataDir, env, osName)
    for
      h <- home.toRight(
        Problem.Unclassified(
          Legacy.Text("runtime.home is empty and the executable's location is unknown (set HISTMERSION_HOME)")
        )
      )
      d <- data.toRight(
        Problem.Unclassified(Legacy.Text("runtime.data-dir is empty and HOME is unset (set HISTMERSION_DATA_DIR)"))
      )
    yield Layout(h.toAbsolutePath.normalize, d.toAbsolutePath.normalize)

  /** The server options every start uses: loopback TCP only, no Unix socket (see the class doc), and the memory
    * settings ([[pgSettings]]).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private[server] def pgOptions(
      port: RuntimeConfig.PgPort,
      settings: List[(String, String)] = Nil
  ): ProcessLaunch.Command =
    ProcessLaunch.Command(
      (s"-p ${port.value} -c listen_addresses=127.0.0.1 -c unix_socket_directories=''" ::
        settings.map((k, v) => s"-c $k=$v")).mkString(" ")
    )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private[server] def pgStart(
      l: Layout,
      port: RuntimeConfig.PgPort,
      settings: List[(String, String)] = Nil
  ): List[ProcessLaunch.Command] =
    List(
      ProcessLaunch.Command(l.pgBin.resolve("pg_ctl").toString),
      ProcessLaunch.Command("-D"),
      ProcessLaunch.Command(l.cluster.toString),
      ProcessLaunch.Command("-l"),
      ProcessLaunch.Command(l.logs.resolve("postgres.log").toString),
      ProcessLaunch.Command("-w"),
      ProcessLaunch.Command("-t"),
      ProcessLaunch.Command("300"),
      ProcessLaunch.Command("-o"),
      pgOptions(port, settings),
      ProcessLaunch.Command("start")
    )

  /** Pure: the `-c` settings the bundled Postgres starts with, given the machine's RAM: each memory parameter's size
    * ([[PgTuning.sizes]]: `auto` by [[PgParameter.auto]], left to Postgres while the RAM is unknown or the setting is
    * empty), then `max_connections`. Every value is a typed size or count written by this code, so nothing can ride
    * along into the `-o` string.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private[server] def pgSettings(t: PgTuning, ramBytes: Option[ByteCount]): List[(String, String)] =
    t.sizes(ramBytes).map((p, bytes) => p.name -> s"${bytes.written}") :+
      ("max_connections" -> s"${t.maxConnections}")

  /** The machine's physical memory, when the platform says: `hw.memsize` on macOS, `MemTotal` on Linux. Read once, by
    * the entry point ([[SystemContext.memory]]).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def physicalMemory(osName: UserPaths.OsName): Option[ByteCount] =
    scala.util
      .Try {
        if UserPaths.isMac(osName) then
          processes
            .run(
              ProcessLaunch(
                List(
                  ProcessLaunch.Command("/usr/sbin/sysctl"),
                  ProcessLaunch.Command("-n"),
                  ProcessLaunch.Command("hw.memsize")
                )
              )
            )
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
      .flatMap(ByteCount.option(_))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private[server] def pgStop(l: Layout): List[ProcessLaunch.Command] =
    List(
      ProcessLaunch.Command(l.pgBin.resolve("pg_ctl").toString),
      ProcessLaunch.Command("-D"),
      ProcessLaunch.Command(l.cluster.toString),
      ProcessLaunch.Command("-m"),
      ProcessLaunch.Command("fast"),
      ProcessLaunch.Command("-w"),
      ProcessLaunch.Command("stop")
    )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private[server] def initdb(l: Layout, user: DbConfig.User, pwFile: Path): List[ProcessLaunch.Command] =
    List(
      ProcessLaunch.Command(l.pgBin.resolve("initdb").toString),
      ProcessLaunch.Command("-D"),
      ProcessLaunch.Command(l.cluster.toString),
      ProcessLaunch.Command("-U"),
      ProcessLaunch.Command(user.value),
      ProcessLaunch.Command("--auth=scram-sha-256"),
      ProcessLaunch.Command(s"--pwfile=$pwFile"),
      ProcessLaunch.Command("-E"),
      ProcessLaunch.Command("UTF8"),
      ProcessLaunch.Command("--locale=C")
    )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private[server] def converterCmd(l: Layout, port: RuntimeConfig.ConverterPort): List[ProcessLaunch.Command] =
    List(
      ProcessLaunch.Command(l.python.toString),
      ProcessLaunch.Command("-m"),
      ProcessLaunch.Command("uvicorn"),
      ProcessLaunch.Command("app:app"),
      ProcessLaunch.Command("--host"),
      ProcessLaunch.Command("127.0.0.1"),
      ProcessLaunch.Command("--port"),
      ProcessLaunch.Command(port.value.toString)
    )

  /** The configuration with every endpoint and directory pointed at the bundle. Settings the user changed explicitly (a
    * backups dir other than the default, a pg-bin-dir) are kept, and so are the addresses the server listens on
    * (`server.host`): loopback only unless the config file or `HISTMERSION_BIND` (comma-separated) lists others, such
    * as a tailnet address.
    */
  @tolerated(LintRule.Copy, "pending G19 (lenses instead of .copy)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private[server] def configure(cfg: AppConfig, l: Layout): AppConfig =
    val dbName = Backup
      .parseJdbc(Backup.JdbcUrl(cfg.db.url.value))
      .toOption
      .map(_.database.value)
      .filter(_.nonEmpty)
      .getOrElse("histmersion")
    cfg.copy(
      db = cfg.db.modify(_.url).setTo(DbConfig.Url(s"jdbc:postgresql://127.0.0.1:${cfg.runtime.pgPort.value}/$dbName")),
      converter = cfg.converter
        .modify(_.url)
        .setTo(ConverterConfig.Url(s"http://127.0.0.1:${cfg.runtime.converterPort.value}"))
        .modify(_.spoolDir)
        .setTo(ConverterConfig.SpoolDir(l.uploads.toString)),
      backups = cfg.backups
        .modify(_.dir)
        .setTo(if cfg.backups.dir.value == DefaultBackupsDir then BackupsConfig.Dir(l.backups.toString)
        else cfg.backups.dir)
        .modify(_.pgBinDir)
        .setTo(
          BackupsConfig.PgBinDir(Option(cfg.backups.pgBinDir.value).filter(_.nonEmpty).getOrElse(l.pgBin.toString))
        ),
      bundles = cfg.bundles
        .modify(_.dir)
        .setTo(if cfg.bundles.dir.value == DefaultBundlesDir then BundlesConfig.Dir(l.bundles.toString)
        else cfg.bundles.dir),
      // the first-run data ships inside the bundle (scripts/bundle/server.sh copies db/ there)
      init = cfg.init
        .modify(_.dataDir)
        .setTo(if cfg.init.dataDir.value == DefaultInitDataDir then InitConfig.DataDir(l.initData.toString)
        else cfg.init.dataDir)
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

  /** What became of a plugin file at the start. */
  enum PluginFile:
    /** It is not there, or already in the form this version reads. */
    case Kept

    /** It held a plugin's problem bare, and now holds it in a list. */
    case Rewritten

  extension (l: Layout)
    /** The plugin files an earlier version wrote with a bare problem (the set recorded beside the binary, the plugins
      * index), rewritten once into the list form this one reads; a file that cannot be rewritten is left as it is, and
      * said so.
      */
    private def rewritePluginFiles(): Unit =
      List(
        l.home.resolve("bin").resolve("histmersion-server.plugin-set.json") -> PluginKit.parseSet,
        l.data.resolve("plugins").resolve("index.json") -> PluginKit.parseIndex
      ).foreach { (file, read) =>
        file.rewrittenOnce(read) match
          case Right(PluginFile.Rewritten) => scribe.info(s"runtime: rewrote $file into the list form of its problems")
          case Right(PluginFile.Kept)      => ()
          case Left(problem)               => scribe.warn(s"runtime: left $file as it is: ${problem.sentence}")
      }

  extension (file: Path)
    /** The plugin file brought into the form this version reads ([[util.plugins.PluginKit.listedProblems]]), when it
      * holds a bare problem; `read` is today's reader of its kind. The new text must read with it; it is written beside
      * the file and moved over it in one step. A `Left` leaves the file exactly as it was.
      */
    private[server] def rewrittenOnce[A](read: String => Either[Problem, A]): Either[Problem, PluginFile] =
      if !Files.isRegularFile(file) then Right(PluginFile.Kept)
      else
        for
          text <- file.attempt(Problem.Io.Access.Read)(Files.readString(file, UTF_8))
          listed <- PluginKit.listedProblems(text, s"${file.getFileName}")
          outcome <- listed.fold[Either[Problem, PluginFile]](Right(PluginFile.Kept)) { rewritten =>
            read(rewritten).flatMap(_ => file.replacedBy(rewritten)).map(_ => PluginFile.Rewritten)
          }
        yield outcome

    /** `text` written beside the file, then moved over it in one step; the scratch file is removed when that fails. */
    private def replacedBy(text: String): Either[Problem, Unit] =
      val scratch = file.resolveSibling(s"${file.getFileName}.rewrite")
      val written =
        for
          _ <- scratch.attempt(Problem.Io.Access.Write)(Files.writeString(scratch, text + "\n", UTF_8))
          _ <- file.attempt(Problem.Io.Access.Move)(
            Files.move(scratch, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
          )
        yield ()
      written.left.foreach(_ => scala.util.Try(Files.deleteIfExists(scratch)))
      written

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def attempt[A](op: Problem.Io.Access)(run: => A): Either[Problem, A] =
      try Right(run)
      catch case e: java.io.IOException => Left(Problem.Io.File(op, Problem.Io.Path(s"$file"), External.Reported.of(e)))

  /** Start (or adopt) the bundled Postgres and the converter, register their shutdown, and return the configuration
    * that points at them. Throws with an actionable message when the bundle is incomplete — the server cannot run
    * without its database — or when the database pool does not fit in the connections Postgres is started with
    * ([[PgTuning.admits]]), before anything is started. `memory` is the machine's, which `auto` sizes from.
    *
    * One server per database: once its database is up, the instance lock is taken there in `holds` (kept for the
    * process's life); another server holding it ends the start with [[Raised]] ([[Problem.Conflict.ServerHeld]]) before
    * the converter starts and before a shutdown hook is registered — that hook would stop the Postgres the other server
    * runs on.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def start(cfg: AppConfig, paths: UserPaths, memory: Option[ByteCount], holds: InstanceLock.Holds): AppConfig =
    cfg.runtime.postgres.admits(cfg.db.pool).left.foreach(p => throw IllegalStateException(p.sentence))
    val exe = currentExecutable
    val l = layout(cfg.runtime, exe, paths.environment.lookup, paths.osName)
      .fold(e => throw IllegalStateException(e.sentence), identity)
    if !Files.isExecutable(l.pgBin.resolve("pg_ctl")) then
      throw IllegalStateException(
        s"no PostgreSQL in the bundle: ${l.pgBin.resolve("pg_ctl")} is missing (HISTMERSION_HOME=${l.home})"
      )
    List(l.data, l.logs, l.backups, l.uploads, l.bundles).foreach(d => Files.createDirectories(d))
    scribe.info(s"runtime: bundle ${l.home}, data ${l.data}")
    l.rewritePluginFiles()
    PgRelocation
      .relocate(Storage.at(Location.Postgres, l.pgBin.getParent), PgRelocation.MacOsMode(paths.isMac), processes)
      .foreach(r =>
        scribe.info(
          s"runtime: relocated PostgreSQL to ${l.pgBin.getParent} " +
            s"(${r.files} files patched, ${r.signed} re-signed, ${r.tooLong} strings too long to patch)"
        )
      )

    val fresh = !Files.exists(l.cluster.resolve("PG_VERSION"))
    if fresh then initCluster(l, cfg.db.user, cfg.db.password)
    if run(
        List(
          ProcessLaunch.Command(l.pgBin.resolve("pg_ctl").toString),
          ProcessLaunch.Command("-D"),
          ProcessLaunch.Command(l.cluster.toString),
          ProcessLaunch.Command("status")
        ),
        l
      )._1 == 0
    then scribe.info("runtime: postgres already running on this cluster — adopting it")
    else
      val settings = pgSettings(cfg.runtime.postgres, memory)
      scribe.info(s"runtime: postgres settings ${settings.map((k, v) => s"$k=$v").mkString(", ")}")
      val (rc, out) = run(pgStart(l, cfg.runtime.pgPort, settings), l)
      if rc != 0 then
        throw IllegalStateException(
          s"postgres did not start (exit $rc): ${out.takeRight(5).mkString("; ")} — see ${l.logs.resolve("postgres.log")}"
        )
      scribe.info(s"runtime: postgres up on 127.0.0.1:${cfg.runtime.pgPort}")
    val configured = configure(cfg, l)
    ensureDatabase(configured)
    holds.hold(configured.db).left.foreach(p => throw Raised(p))

    if Files.isExecutable(l.python) then superviseConverter(l, cfg.runtime.converterPort)
    else scribe.warn(s"runtime: no converter in the bundle (${l.python}); OCR ingest is unavailable")

    Runtime.getRuntime.addShutdownHook(Thread(() => shutdown(l)))
    configured

  /** How a config reload is fitted to the running bundle (docs/research-plugins.md §3.5): every reloaded config is
    * pointed at the bundled Postgres and converter the way [[start]] pointed the first one (the children themselves,
    * `runtime.*`, are restart-only), and a database the reload names is created in the bundled cluster before a pool
    * opens on it — once its pool is known to fit in the connections the running Postgres was started with (`started`'s,
    * [[PgTuning.admits]]). `started` is the config [[start]] was given.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  def reloadSource(
      started: AppConfig,
      read: () => Either[Problems, LoadedConfig],
      paths: UserPaths
  ): ConfigSource =
    val l = layout(started.runtime, currentExecutable, paths.environment.lookup, paths.osName)
      .fold(e => throw IllegalStateException(e.sentence), identity)
    ConfigSource(
      read,
      c => configure(c, l),
      c =>
        started.runtime.postgres
          .admits(c.db.pool)
          .flatMap(_ =>
            scala.util
              .Try(ensureDatabase(c))
              .toEither
              .left
              .map(e => Problem.Unclassified(Legacy.Text(s"cannot create the database: ${e.getMessage}")))
          )
    )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def initCluster(l: Layout, user: DbConfig.User, password: DbConfig.Password): Unit =
    val pw = Files.createTempFile("histmersion-pw", "")
    try
      Files.writeString(pw, password.value)
      Files.createDirectories(l.cluster.getParent)
      scribe.info(s"runtime: first run — initialising the database cluster in ${l.cluster}")
      val (rc, out) = run(initdb(l, user, pw), l)
      if rc != 0 then throw IllegalStateException(s"initdb failed (exit $rc): ${out.takeRight(5).mkString("; ")}")
    finally { val _ = Files.deleteIfExists(pw) }

  /** `CREATE DATABASE` on first run (the cluster starts with only `postgres`). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def ensureDatabase(cfg: AppConfig): Unit =
    val target =
      Backup.parseJdbc(Backup.JdbcUrl(cfg.db.url.value)).fold(e => throw IllegalStateException(e.sentence), identity)
    val dbUrl = cfg.db.url.value
    val adminUrl = dbUrl.take(dbUrl.lastIndexOf('/') + 1) + "postgres"
    val c = java.sql.DriverManager.getConnection(adminUrl, cfg.db.user.value, cfg.db.password.value)
    try
      val q = c.prepareStatement("SELECT 1 FROM pg_database WHERE datname = ?")
      q.setString(1, target.database.value)
      if !q.executeQuery().next() then
        val quoted = "\"" + target.database.value.replace("\"", "\"\"") + "\""
        val _ = c.createStatement().execute(s"CREATE DATABASE $quoted")
        scribe.info(s"runtime: created database ${target.database.value}")
    finally c.close()

  /** Run the converter; when it exits unexpectedly, restart it after 1, 2, 4 … 60 s (reset once a run has lasted a
    * minute).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def superviseConverter(l: Layout, port: RuntimeConfig.ConverterPort): Unit =
    val t = Thread.ofPlatform().daemon(true).name("histmersion-converter").unstarted { () =>
      var backoffMs = 1000L
      while !stopping.get() do
        val launch = ProcessLaunch(
          converterCmd(l, port),
          workingStore = Some(Storage.at(Location.Install, l.converterDir)),
          environment = Map(
            ProcessLaunch.EnvironmentKey("DOCLING_ARTIFACTS_PATH") -> ProcessLaunch.EnvironmentValue(l.models.toString),
            ProcessLaunch.EnvironmentKey("HF_HUB_OFFLINE") -> ProcessLaunch.EnvironmentValue("1"),
            ProcessLaunch.EnvironmentKey("HISTMERSION_CONVERTER_PORT") -> ProcessLaunch
              .EnvironmentValue(port.value.toString)
          ),
          output = ProcessOutput.AppendTo(Storage.at(Location.Logs, l.logs), Store.Key("converter.log"))
        )
        val started = System.nanoTime()
        val p = processes.start(launch).fold(e => throw java.io.IOException(e.sentence), identity)
        converter.set(Some(p))
        scribe.info(s"runtime: converter started on 127.0.0.1:${port.value} (pid ${p.pid})")
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

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def shutdown(l: Layout): Unit =
    stopping.set(true)
    converter.get().foreach { p =>
      p.destroy()
      if !p.waitFor(Processes.WaitTimeout(10), TimeUnit.SECONDS) then { val _ = p.destroyForcibly() }
    }
    val (rc, out) = run(pgStop(l), l)
    if rc != 0 then scribe.error(s"Histmersion: postgres stop failed (exit $rc): ${out.mkString("; ")}")

  /** Run a Postgres tool with the bundle's environment; returns (exit code, output lines). */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def run(cmd: List[ProcessLaunch.Command], l: Layout): (Int, List[String]) =
    processes
      .run(
        ProcessLaunch(
          cmd,
          environment =
            Map(ProcessLaunch.EnvironmentKey("PROJ_DATA") -> ProcessLaunch.EnvironmentValue(l.projData.toString))
        )
      )
      .fold(e => throw java.io.IOException(e.sentence), r => (r.exitCode.value, r.lines.map(_.value)))
