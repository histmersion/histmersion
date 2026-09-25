package com.histmersion.server

import sttp.tapir.server.netty.sync.NettySyncServer
import sttp.tapir.server.netty.NettyConfig
import scala.concurrent.duration.*
import com.histmersion.core.AppConfig
import com.histmersion.db.{Db, Migrate}
import com.histmersion.server.acquire.*

/** Histmersion server — direct-style (Netty sync). Composition root: config → DataSource → migrate →
  * Handlers → HTTP (REST + pages + static) and MCP endpoints → bind. */
object Main:

  /** Build a configured (not yet started) server. `port = 0` → ephemeral port (tests use
    * `supervised { server(...).start() }`; `main` uses `startAndWait()`). */
  def server(cfg: AppConfig, host: String, port: Int): NettySyncServer =
    server(cfg, host, port, ConverterClient.fromConfig(cfg.converter))

  /** Same, with an explicit OCR sidecar client — the seam an HTTP test uses to ingest a document
    * through a fake converter (plan §1.8). */
  def server(cfg: AppConfig, host: String, port: Int, converter: ConverterClient): NettySyncServer =
    val ds = Db.dataSource(cfg.db)
    val v  = Migrate.run(ds)
    scribe.info(s"schema version $v")
    Db.ensureDatabaseSettings(ds) // survives a restore that dropped them (see Db.databaseSettings)
    Db.evictAll() // sessions opened before this miss database-level settings
    val _ = Thread.ofPlatform().daemon(true).name("histmersion-analyze").start { () =>
      try
        val done = Db.analyzeStale(ds)
        if done.nonEmpty then scribe.info(s"analyzed tables with no statistics: ${done.mkString(", ")}")
      catch case e: Throwable => scribe.warn(s"startup ANALYZE failed: ${e.getMessage}")
    }
    val deps = Deps(cfg, ds, queryDataSource(cfg, ds))
    val h    = Handlers(deps, converter)
    val _    = h.ingest.start()   // reconcile interrupted jobs, then start the bounded worker pool
    val acq  = acquireHandlers(deps, h)
    val srv = NettySyncServer()
      .host(host)
      .port(port)
      .modifyConfig(_.requestTimeout(cfg.server.requestTimeoutSeconds.seconds))   // netty default 20 s is shorter than a pg_dump
      .addEndpoint(McpTools(h, acq).endpoint)
      .addEndpoints(HttpApi(deps, h, acq).endpoints)
    if cfg.auth.apiKey.nonEmpty then
      scribe.info("API key authentication enabled — write methods require Authorization: Bearer <key>")
      srv.modifyConfig { nc =>
        nc.initPipeline { inner => (pipeline, tapirHandler) =>
          NettyConfig.defaultInitPipeline(inner)(pipeline, tapirHandler)
          val lastHandlerName = pipeline.context(pipeline.last()).name()
          pipeline.addBefore(lastHandlerName, "histmersion-auth", AuthHandler(cfg.auth.apiKey))
        }
      }
    else srv

  /** The DataSource `POST /api/query` executes on: the read-only `histmersion_query` role that migration
    * V011 created, given its password and statement timeout from config here (a migration must not
    * carry a secret; see `reference.conf`).
    *
    * If the application role may not `ALTER ROLE`, or the role cannot log in, this falls back to the
    * application DataSource and says so LOUDLY. That is a real reduction in safety — the statement
    * check and the read-only transaction still hold, but a superuser-only function is no longer out
    * of reach — so it is an error-level log, not a debug line. Failing to boot instead would take the
    * whole server down over one optional endpoint.
    */
  private def queryDataSource(cfg: AppConfig, ds: javax.sql.DataSource): javax.sql.DataSource =
    val provisioned = Db.provisionQueryRole(ds, cfg.query)
    provisioned.left.foreach(m =>
      scribe.error(s"could not provision the read-only role '${cfg.query.user}': $m"))
    val q = Db.queryDataSource(cfg.db, cfg.query)
    Db.canConnect(q) match
      case Right(_) =>
        scribe.info(s"/api/query runs as the read-only role '${cfg.query.user}'")
        q
      case Left(m) =>
        scribe.error(s"the read-only role '${cfg.query.user}' cannot connect ($m) — /api/query will " +
          "run as the APPLICATION role, protected only by the statement check and the read-only " +
          "transaction. Grant the application role CREATEROLE, or create histmersion_query by hand.")
        ds

  /** Stream 2.6's acquisition toolset: one HTTP client, the upstream clients built over it, and the
    * ingest job the fetched PDF is handed to — [[QueuedIngest]], which writes the metadata straight
    * away and hands the file to stream 2.4's OCR queue (§1.8). */
  private def acquireHandlers(deps: Deps, h: Handlers): AcquireHandlers =
    val cfg      = deps.cfg.acquire
    val http     = SttpHttp(cfg)
    val openalex = OpenAlexCrossref(cfg, http)
    AcquireHandlers(
      deps,
      discovery = openalex,
      citations = SemanticScholar(cfg, http, openalex),
      oa = OaFetch(cfg, http, openalex),
      wiki = Wikipedia(cfg, http),
      archive = ArchiveOrg(cfg, http),
      retractionWatch = RetractionWatch(cfg, http),
      jobs = QueuedIngest(h.ingest, deps.ds, deps.cfg.limits.chunkChars,
        deps.cfg.places.pointToleranceKm))

  def main(args: Array[String]): Unit =
    val _ = args
    // netty's pooled allocator mishandles ByteBuf refCnt under native-image → use unpooled buffers.
    // (Safe: config never parses system properties, so these dotted keys don't reach sconfig.)
    System.setProperty("io.netty.allocator.type", "unpooled")
    System.setProperty("io.netty.leakDetection.level", "disabled")
    val loaded = AppConfig.load() // eager, on the main thread (native-image StackValue constraint)
    // the native bundle: run the bundled Postgres + converter as children and point the config at them
    val cfg =
      val explicit = Option(System.getenv("HISTMERSION_RUNTIME")).map(_.trim.toLowerCase).filter(_.nonEmpty)
      val enabled  = loaded.runtime.enabled || (explicit.isEmpty && Supervisor.bundled(Supervisor.currentExecutable))
      if !enabled then loaded
      else
        try Supervisor.start(loaded)
        catch
          case e: Throwable =>
            scribe.error(s"Histmersion could not start its bundled runtime: ${e.getMessage}")
            sys.exit(3)
    val srv =
      try server(cfg, cfg.server.host, cfg.server.port)
      catch
        case e: Throwable =>
          scribe.error(s"Histmersion failed to start: ${e.getClass.getName}: ${e.getMessage}", e)
          sys.exit(3)
    scribe.info(s"Histmersion listening on http://${cfg.server.host}:${cfg.server.port} (public ${cfg.server.publicUrl})")
    srv.startAndWait()
