package com.histmersion
package server

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.library.search.QueryAccess

import com.histmersion.util.data.maintenance.{ReadOnlyPool, WriteGate}

import com.histmersion.util.settings.{info, ConfigSource, HotConfig}

import com.histmersion.pluginhost.Generation

import com.histmersion.config.{AppConfig, ConfigReload}
import com.histmersion.api.settings.ReloadReport
import com.histmersion.util.database.{Db, Migrate}
import com.histmersion.server.hot.Hot
import ox.{forkDiscard, useInScope}

import java.nio.file.{FileSystems, Path, StandardWatchEventKinds}
import javax.sql.DataSource
import scala.concurrent.duration.*
import scala.util.control.NonFatal
import com.histmersion.domain.library.acquisition.CoreBudget
import io.github.iltotore.iron.constraint.any.Pure

/** The server's hot components (docs/research-plugins.md §3.6) and the config reload over them (§3.5):
  *
  * {{{
  * config ─► pool ─► migrations ─► query-pool
  *                             └─► leases (added by Main once the agents exist)
  *        ─► plugins ─► acquisition (added by Main)
  *        ─► watch (the config file watcher)
  * }}}
  *
  * `ds`/`queryDs` are the proxies every repo takes ([[HotDataSource]]); [[deps]] reads the config and the plugins from
  * the graph at each call. Nothing here is static: a second server in the same JVM (a test) has its own graph, pools
  * and all, closed with its Ox scope.
  */
final class ServerGraph private (
    val graph: Hot.Graph[HotConfig],
    source: Option[ConfigSource],
    val pool: Hot[DataSource],
    val migrations: Hot[Migrate.Version],
    val queryPool: Hot[ReadOnlyPool],
    val plugins: Hot[Generation],
    val core: CoreBudget
):

  val ds: DataSource = Hot.DataSource(pool)(identity)
  val queryDs: DataSource = Hot.DataSource(queryPool)(_.ds)

  /** The handlers' view: config, file and plugins from the graph at each call. */
  def deps(maintenance: WriteGate, restart: Option[() => Unit], system: SystemContext): Deps =
    new Deps(
      () => graph.config.app,
      ds,
      queryDs,
      maintenance,
      () => graph.config.loaded.file.info,
      () => plugins.peek,
      graph,
      () => reload(),
      loadedNow = Some(() => graph.config.loaded),
      restart = restart,
      clock = system.clock,
      environment = system.environment,
      userPaths = system.userPaths,
      timer = system.timer,
      pause = system.pause
    )

  /** Throw away the pooled connections of the pools in use now (after a restore: see [[Db.evict]]). */
  def evict(): Unit =
    Db.evict(ds)
    Db.evict(queryDs)

  private val reloadLock = Object()

  /** `histmersion config reload`: read the config file again; compare it with the running one; keep the restart-only
    * settings at their running value; rebuild what the rest concerns (and everything that depends on it) beside the
    * running components; switch when all of it built. Nothing is changed on any error.
    */
  def reload(): ReloadReport = reloadLock.synchronized {
    val running = graph.latest
    val gen = graph.latestGeneration
    val generation = ReloadReport.Generation(gen.value)
    val file = running.loaded.file.path.map(p => ReloadReport.File(p.toString))
    source match
      case None      => Deps.NoReload().modify(_.generation).setTo(generation)
      case Some(src) =>
        val report = (try src.read()
        catch case NonFatal(e) => Left(ConfigSource.Problem(s"reading the config failed: ${e.getMessage}"))) match
          case Left(e) =>
            ReloadReport(
              published = ReloadReport.Published(false),
              generation,
              errors = List(ReloadReport.Errors(e.value)),
              file = file
            )
          case Right(next) =>
            val changed = ConfigReload.changed(running.loaded.merged, next.merged)
            val (applied, restart) = ConfigReload.split(changed)
            val warnings = next.file.warnings.map(w => ReloadReport.Warnings(w.value))
            val base = ReloadReport(
              published = ReloadReport.Published(false),
              generation,
              restartOnly = restart.map(k => ReloadReport.RestartOnly(k.value)),
              warnings = warnings,
              file = file
            )
            if applied.isEmpty then base
            else
              val eff = ConfigReload.effective(running.loaded.merged, next.merged, changed)
              AppConfig.fromMerged(eff).map(src.adjust) match
                case Left(e) =>
                  base.modify(_.errors).setTo(List(ReloadReport.Errors(s"the config does not decode: $e")))
                case Right(app) =>
                  val hc = HotConfig(app, next.modify(_.config).setTo(app).modify(_.merged).setTo(eff))
                  graph.reload(
                    hc,
                    applied.map(k => Hot.Graph.Def.Name(k.value)),
                    drain = math.max(0, app.config.drainSeconds.value).seconds
                  ) match
                    case Left(e)  => base.modify(_.errors).setTo(List(ReloadReport.Errors(e.value)))
                    case Right(o) =>
                      base
                        .modify(_.published)
                        .setTo(ReloadReport.Published(true))
                        .modify(_.generation)
                        .setTo(ReloadReport.Generation(o.generation.value))
                        .modify(_.applied)
                        .setTo(applied.map(k => ReloadReport.Applied(k.value)))
                        .modify(_.rebuilt)
                        .setTo(o.rebuilt.map(r => ReloadReport.Rebuilt(r.value)))
                        .modify(_.plugins)
                        .setTo(
                          if o.rebuilt.exists(_.value == ServerGraph.Plugins) then
                            plugins.latest.rebuilt.map(i => ReloadReport.Plugins(i.value))
                          else Nil
                        )
                        .modify(_.draining)
                        .setTo(o.draining.map(d => ReloadReport.Draining(d.value)))
                        .modify(_.warnings)
                        .setTo(warnings ++ o.warnings.map(w => ReloadReport.Warnings(w.value)))
        ServerGraph.log(report)
        report
  }

  /** Watch the config file and reload `debounceMs` after its last change — a node of its own (`histmersion.config.*`),
    * so turning `watch` on or off is itself a reload, and the watcher stops with its scope. Off without a file to
    * watch.
    */
  private[server] def addWatch(): Either[ServerGraph.Problem, Hot[Boolean]] =
    graph
      .node[Boolean](Hot.Graph.Def.Name(ServerGraph.Watch), keys = List(Hot.Graph.Def.Keys("histmersion.config"))) {
        ctx =>
          val c = ctx.config.app.config
          val target = ctx.config.loaded.file.path.filter(_ => source.isDefined)
          target match
            case Some(f) if c.watch.value =>
              forkDiscard(
                ServerGraph.watch(
                  f,
                  math.max(50, c.watchDebounceMs.value).toLong,
                  () => {
                    // a reload rebuilds this very node (when `config.*` changed) and closes this scope: never run
                    // it on the watcher's own fork
                    val _ = Thread.ofVirtual().name("histmersion-config-reload").start { () =>
                      val _ = reload()
                    }
                  }
                )
              )
              scribe.info(s"config: watching $f for changes")
              Right(true)
            case _ => Right(false)
      }
      .left
      .map(p => ServerGraph.Problem(p.value))

  /** Close every component — the server's end. */
  def close(): Unit = graph.close()

object ServerGraph:
  type Problem = Problem.T
  object Problem extends Newtype[String, Pure]

  val Pool = "pool"
  val Migrations = "migrations"
  val QueryPool = "query-pool"
  val Plugins = "plugins"
  val Acquisition = "acquisition"
  val Leases = "leases"
  val Watch = "watch"

  /** Build the graph's first generation: the pools, the migrations, the plugins. `plugins` is a generation built
    * already (Main's, for `--self-check`; a test's), used as the first value. `Left` = the server cannot start (the
    * database is unreachable, a migration failed).
    */
  def start(
      initial: HotConfig,
      source: Option[ConfigSource],
      plugins: Option[Generation],
      core: CoreBudget
  ): Either[Problem, ServerGraph] =
    val graph = Hot.Graph[HotConfig](initial)
    val prepare = source.map(_.prepare).getOrElse((_: AppConfig) => Right(()))
    val built =
      for
        pool <- graph
          .node[DataSource](
            Hot.Graph.Def.Name(Pool),
            keys = List(Hot.Graph.Def.Keys("histmersion.db")),
            idle = ds => Db.activeConnections(ds).value == 0
          ) { ctx =>
            val db = ctx.config.app.db
            for
              _ <- prepare(ctx.config.app).left.map(e => Hot.Graph.Problem(e.value))
              p = useInScope(Db.pool(db))(_.close())
              _ <- Db.canConnect(p).left.map(e => Hot.Graph.Problem(s"cannot connect to ${redact(db.url.value)}: $e"))
            yield p
          }
        mig <- graph.node[Migrate.Version](Hot.Graph.Def.Name(Migrations), deps = List(pool)) { ctx =>
          val p = ctx(pool)
          try
            val v = Migrate.run(p)
            Db.ensureDatabaseSettings(p) // survives a restore that dropped them (see Db.databaseSettings)
            Db.evict(p) // sessions opened before this miss database-level settings
            scribe.info(s"schema version $v (${redact(ctx.config.app.db.url.value)})")
            forkDiscard(analyze(p))
            Right(v)
          catch
            case NonFatal(e) =>
              Left(Hot.Graph.Problem(s"migrating ${redact(ctx.config.app.db.url.value)} failed: ${e.getMessage}"))
        }
        q <- graph.node[ReadOnlyPool](
          Hot.Graph.Def.Name(QueryPool),
          deps = List(pool, mig),
          keys = List(Hot.Graph.Def.Keys("histmersion.query"), Hot.Graph.Def.Keys("histmersion.db")),
          idle = r => r.own.forall(Db.activeConnections(_).value == 0)
        ) { ctx =>
          Right(readOnly(ctx.config.app, ctx(pool)))
        }
        plug <- graph.node[Generation](
          Hot.Graph.Def.Name(Plugins),
          keys = com.histmersion.util.plugins.Plugins.Keys.map(k => Hot.Graph.Def.Keys(k.value))
        ) { ctx =>
          Right(ctx.previous match
            case None => plugins.getOrElse(ServerPlugins.load(ctx.config.loaded, core = core))
            case prev =>
              ServerPlugins.load(ctx.config.loaded, prev, ctx.changed.map(n => ConfigReload.ChangedKey(n.value)), core))
        }
      yield ServerGraph(graph, source, pool, mig, q, plug, core)
    built.left.foreach(_ => graph.close())
    built.left.map(p => Problem(p.value))

  /** The read-only role (migration V011) given its password and statement timeout from config here (a migration must
    * not carry a secret; see `reference.conf`), and its own small pool.
    *
    * If the application role may not `ALTER ROLE`, or the role cannot log in, this falls back to the application pool
    * and says so LOUDLY. That is a real reduction in safety — the statement check and the read-only transaction still
    * hold, but a superuser-only function is no longer out of reach — so it is an error-level log, not a debug line.
    * Failing to boot instead would take the whole server down over one optional endpoint.
    */
  private def readOnly(cfg: AppConfig, app: DataSource)(using ox.OxUnsupervised): ReadOnlyPool =
    QueryAccess
      .provision(app, cfg.query)
      .left
      .foreach(m => scribe.error(s"could not provision the read-only role '${cfg.query.user}': $m"))
    val q = useInScope(QueryAccess.pool(cfg.db, cfg.query))(_.close())
    Db.canConnect(q) match
      case Right(_) =>
        scribe.info(s"/api/query runs as the read-only role '${cfg.query.user}'")
        ReadOnlyPool(q, Some(q))
      case Left(m) =>
        scribe.error(
          s"the read-only role '${cfg.query.user}' cannot connect ($m) — /api/query will " +
            "run as the APPLICATION role, protected only by the statement check and the read-only " +
            "transaction. Grant the application role CREATEROLE, or create histmersion_query by hand."
        )
        ReadOnlyPool(app, None)

  /** ANALYZE the tables with no statistics, in the background (the migrations node's scope). */
  private def analyze(ds: DataSource): Unit =
    try
      val done = Db.analyzeStale(ds)
      if done.nonEmpty then scribe.info(s"analyzed tables with no statistics: ${done.mkString(", ")}")
    catch
      case _: InterruptedException => ()
      case NonFatal(e)             => scribe.warn(s"startup ANALYZE failed: ${e.getMessage}")

  /** A JDBC URL for the log: no query string (where a password could be). */
  def redact(url: String): String = url.takeWhile(_ != '?')

  private def log(r: ReloadReport): Unit =
    if r.errors.nonEmpty then
      scribe.warn(s"config reload failed, generation ${r.generation} stays: ${r.errors.mkString("; ")}")
    else if r.published.value then
      scribe.info(
        s"config reloaded: generation ${r.generation}; applied ${r.applied.mkString(", ")}" +
          (if r.rebuilt.isEmpty then "" else s"; rebuilt ${r.rebuilt.mkString(", ")}") +
          (if r.plugins.isEmpty then "" else s"; plugins ${r.plugins.mkString(", ")}") +
          (if r.restartOnly.isEmpty then "" else s"; restart-only, not applied: ${r.restartOnly.mkString(", ")}")
      )
    else if r.restartOnly.nonEmpty then
      scribe.info(s"config reload: only restart-only settings changed (${r.restartOnly.mkString(", ")}) — not applied")
    else scribe.info("config reload: nothing changed")

  /** Watch `file`'s directory and call `changed` once the file has been quiet for `debounceMs` after a change. Returns
    * when interrupted (the watch node's scope closing).
    */
  private def watch(file: Path, debounceMs: Long, changed: () => Unit): Unit =
    val dir = Option(file.getParent).getOrElse(Path.of("."))
    val ws = FileSystems.getDefault.newWatchService()
    try
      val _ = dir.register(
        ws,
        StandardWatchEventKinds.ENTRY_CREATE,
        StandardWatchEventKinds.ENTRY_MODIFY,
        StandardWatchEventKinds.ENTRY_DELETE
      )
      def ours(k: java.nio.file.WatchKey): Boolean =
        import scala.jdk.CollectionConverters.*
        given CanEqual[java.nio.file.WatchEvent.Kind[?], java.nio.file.WatchEvent.Kind[?]] = CanEqual.derived
        val hit = k
          .pollEvents()
          .asScala
          .exists(e =>
            Option(e.context()).exists(_.toString == file.getFileName.toString)
              || e.kind == StandardWatchEventKinds.OVERFLOW
          )
        val _ = k.reset()
        hit
      while true do
        if ours(ws.take()) then
          // the debounce: wait until no further event for the file arrives within the window
          var quiet = false // the window's state; local to this loop
          while !quiet do
            Option(ws.poll(debounceMs, java.util.concurrent.TimeUnit.MILLISECONDS)) match
              case Some(k) => val _ = ours(k)
              case None    => quiet = true
          changed()
    catch
      case _: InterruptedException                      => ()
      case _: java.nio.file.ClosedWatchServiceException => ()
      case NonFatal(e) => scribe.warn(s"config: watching $file stopped: ${e.getMessage}")
    finally ws.close()
