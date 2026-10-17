package com.histmersion.server

import sttp.tapir.server.netty.sync.NettySyncServer
import sttp.tapir.server.netty.NettyConfig
import scala.concurrent.duration.*
import com.histmersion.core.{AppConfig, ConfigFileInfo}
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
  def server(cfg: AppConfig, host: String, port: Int, converter: ConverterClient,
      autoInit: Boolean = false, onHandlers: Handlers => Unit = _ => (),
      configFile: ConfigFileInfo = ConfigFileInfo()): NettySyncServer =
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
    val deps = Deps(cfg, ds, queryDataSource(cfg, ds), configFile = configFile)
    val h    = Handlers(deps, converter)
    onHandlers(h) // a test's handle on this server's handlers (its run tokens); nothing in production
    val _    = h.ingest.start()   // reconcile interrupted jobs, then start the bounded worker pool
    val acq  = acquireHandlers(deps, h)
    // embedded agents: the acquisition toolset first (enrich-source checks metadata, refs, assess;
    // the skeptic reads followups); then an interrupted run is PAUSED (resumable), queued ones
    // re-enqueued; no pool at all unless agents.enabled
    h.agents.attach(com.histmersion.server.agents.AgentUpstream.of(acq))
    val mcp = McpTools(h, acq)
    h.agents.attachTools(mcp.toolNames) // what the generated definitions' placeholders may name
    val paused = h.agents.start()
    if paused > 0 then scribe.info(s"$paused interrupted agent run(s) paused; resume them with histmersion agent resume")
    // first run (#720): the bundled gazetteer / period presets into empty tables — a background job,
    // started AFTER the reconciliation above so it is not failed as "interrupted". Only `main` asks:
    // a test server must never seed its database behind the test's back
    if autoInit then
      try { val _ = h.bootstrap.autoStart() }
      catch case e: Exception => scribe.warn(s"first-run import not started: ${e.getMessage}")
    val srv = NettySyncServer()
      .host(host)
      .port(port)
      .modifyConfig(_.requestTimeout(cfg.server.requestTimeoutSeconds.seconds))   // netty default 20 s is shorter than a pg_dump
      .addEndpoint(mcp.endpoint)
      .addEndpoints(com.histmersion.server.agents.AgentsTools.agentEndpoints(h.agents))  // one per agent definition, behind run tokens
      .addEndpoints(HttpApi(deps, h, acq).endpoints)
    if cfg.auth.apiKey.nonEmpty then
      scribe.info("API key authentication enabled — write methods require Authorization: Bearer <key>")
    srv.modifyConfig { nc =>
      nc.initPipeline { inner => (pipeline, tapirHandler) =>
        NettyConfig.defaultInitPipeline(inner)(pipeline, tapirHandler)
        // right after the HTTP codec, BEFORE the streams handler turns a request with a body into a
        // streamed one: the auth gate (see AuthHandler) first, so an unauthorised write gets its 401
        // instead of a "100 Continue"; then the answer to `Expect: 100-continue`, which curl sends
        // for any body over 1 KB and then waits on — unanswered, the body arrived late or never
        val codec = pipeline.context(classOf[io.netty.handler.codec.http.HttpServerCodec]).name()
        val _ = pipeline.addAfter(codec, "histmersion-continue", io.netty.handler.codec.http.HttpServerExpectContinueHandler())
        if cfg.auth.apiKey.nonEmpty then
          val _ = pipeline.addAfter(codec, "histmersion-auth", AuthHandler(cfg.auth.apiKey, h.agents.tokens))
        // first of all: the agent MCP endpoint answers only to a live run token, and only with its agent's tools
        val _ = pipeline.addAfter(codec, "histmersion-run-token", com.histmersion.server.agents.RunTokenGate(h.agents.tokens))
      }
    }

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
    // the one outbound policy's Retry-After cap is the media one (`media.max-retry-after-seconds`)
    val http     = SttpHttp(cfg, maxRetryAfterSeconds = deps.cfg.media.maxRetryAfterSeconds)
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
    // eager, on the main thread (native-image StackValue constraint): reference.conf < the user config
    // file < the environment. A broken file stops the start — running on settings the user did not
    // ask for is worse than not running.
    val (loaded, configFile) = AppConfig.loadWithUserFile() match
      case Right(l) =>
        l.file.path.foreach(p =>
          if l.file.loaded then scribe.info(s"config file $p read")
          else scribe.info(s"no config file at $p (defaults and environment only)"))
        l.file.warnings.foreach(w => scribe.warn(w))
        (l.config, l.file.info)
      case Left(err) =>
        scribe.error(err)
        sys.exit(3)
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
      try server(cfg, cfg.server.host, cfg.server.port, ConverterClient.fromConfig(cfg.converter),
        autoInit = cfg.init.auto, configFile = configFile)
      catch
        case e: Throwable =>
          scribe.error(s"Histmersion failed to start: ${e.getClass.getName}: ${e.getMessage}", e)
          sys.exit(3)
    scribe.info(s"Histmersion listening on http://${cfg.server.host}:${cfg.server.port} (public ${cfg.server.publicUrl})")
    srv.startAndWait()
