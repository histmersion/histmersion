package com.histmersion.server

import com.histmersion.util.data.maintenance.WriteGate

import com.histmersion.util.ocr.ConverterClient

import com.histmersion.util.settings.{ConfigSource, HotConfig}

import com.histmersion.domain.library.acquisition.OpenAlexDiscovery

import com.histmersion.pluginhost.{upstreamSettings, FetchChain, Generation, HostLog, OutboundTls}

import sttp.tapir.server.netty.sync.{NettySyncServer, NettySyncServerOptions}
import sttp.tapir.server.netty.NettyConfig
import scala.concurrent.duration.*
import com.histmersion.config.{AppConfig, LoadedConfig, UserConfigFile}
import com.histmersion.api.ConfigFileInfo
import com.histmersion.domain.library.acquisition.*
import com.histmersion.server.hot.{Hot, PinRequests}
import ox.{releaseAfterScope, Ox}

/** The server's wiring: the hot component graph (pools, migrations, plugins) → ServerWiring → HTTP (REST + pages +
  * static) and MCP endpoints → bind. The entry point ([[histmersionServer]]) loads the config and calls it; a test
  * builds a server with [[server]].
  */
object Composition:

  /** Build a configured (not yet started) server. `port = 0` → ephemeral port (tests use
    * `supervised { server(...).start() }`; `main` binds it in its own scope). Its components — the pools above all —
    * belong to the enclosing Ox scope and close with it.
    */
  def server(cfg: AppConfig, host: String, port: Int)(using Ox): NettySyncServer =
    build(HotConfig.fixed(cfg), None, host, port, None)

  /** Same, with an explicit OCR sidecar client — the seam an HTTP test uses to ingest a document through a fake
    * converter (plan §1.8). With `source`, the server can reload its config: the load it was started from, and how to
    * read it again.
    */
  def server(
      cfg: AppConfig,
      host: String,
      port: Int,
      converter: ConverterClient,
      autoInit: Boolean = false,
      onWiring: ServerWiring => Unit = _ => (),
      configFile: ConfigFileInfo = ConfigFileInfo(),
      plugins: Generation = ServerPlugins.offline,
      source: Option[(LoadedConfig, ConfigSource)] = None,
      core: CoreBudget = CoreBudget(),
      system: SystemContext = SystemContext.isolated
  )(using Ox): NettySyncServer =
    build(
      hotConfig(cfg, configFile, source.map(_._1)),
      source.map(_._2),
      host,
      port,
      Some(converter),
      autoInit,
      onWiring,
      plugins,
      core,
      system = system
    )

  private[server] def hotConfig(cfg: AppConfig, configFile: ConfigFileInfo, loaded: Option[LoadedConfig]): HotConfig =
    loaded match
      case Some(l) => HotConfig(cfg, l.copy(config = cfg))
      case None    =>
        HotConfig(
          cfg,
          LoadedConfig(
            cfg,
            UserConfigFile(configFile.path.map(java.nio.file.Path.of(_)), configFile.loaded, configFile.unknownKeys)
          )
        )

  /** The listener with no shutdown grace (`server.shutdown-grace-seconds = 0`, the test suites' setting) also stops its
    * event loops at once. Left to tapir, a stop shuts its loops and then its own executor down with netty's defaults —
    * a quiet period of 2 s each, so every stop took 4 s whatever the grace, and the server suites stopped 73 servers
    * (#3765). Here the loops are this scope's, shut down with no quiet period when it ends; tapir's executor is never
    * started unless a stop of its own needs it.
    */
  private def closeAtOnce(nc: NettyConfig)(using Ox): NettyConfig =
    val loops = nc.eventLoopConfig.initEventLoopGroup()
    releaseAfterScope {
      val _ = loops.shutdownGracefully(0, 5, java.util.concurrent.TimeUnit.SECONDS).syncUninterruptibly()
    }
    nc.noGracefulShutdown
      .eventLoopConfig(nc.eventLoopConfig.copy(initEventLoopGroup = () => loops))
      .withDontShutdownEventLoopGroupOnClose

  /** The composition root proper: the hot component graph first, then the wiring over it, then the listener.
    * `converter` `None` = the sidecar client from the config, read at each call (a reload may move it).
    */
  private[server] def build(
      hc: HotConfig,
      source: Option[ConfigSource],
      host: String,
      port: Int,
      converter: Option[ConverterClient],
      autoInit: Boolean = false,
      onWiring: ServerWiring => Unit = _ => (),
      plugins: Generation = ServerPlugins.offline,
      core: CoreBudget = CoreBudget(),
      restart: Option[() => Unit] = None,
      system: SystemContext = SystemContext.isolated
  )(using Ox): NettySyncServer =
    val sg = ServerGraph.start(hc, source, Some(plugins), core).fold(e => throw IllegalStateException(e), identity)
    releaseAfterScope(sg.close())
    // the CORE budget, built before the database (the first plugin generation spends from it), kept in it
    // from here on: a restart does not hand the day's tokens out again
    core.persistTo(
      CoreQuotaStore.of(UpstreamQuotaService(com.histmersion.util.database.Transactor(sg.ds)))
    )
    val deps = sg.deps(WriteGate(), restart, system)
    val cfg = hc.app // the listener's settings: restart-only (ConfigReload.RestartOnly)
    val h = ServerWiring(deps, converter.getOrElse(ConverterWiring.client(deps.cfg.converter)))
    onWiring(h) // a test's handle on this server's wiring (its run tokens); nothing in production
    val _ = h.ingest.queue.start() // reconcile interrupted jobs, then start the bounded worker pool
    val acq = acquisition(sg, deps, h)
    // embedded agents: the acquisition toolset first (enrich-source checks metadata, refs, assess;
    // the skeptic reads followups); then an interrupted run is PAUSED (resumable), queued ones
    // re-enqueued; no pool at all unless agents.enabled
    com.histmersion.server.agents.EngineContributions
      .work(h.agents)
      .attach(com.histmersion.domain.library.workflows.AgentUpstream.of(acq.controllers))
    val collections = collectionsControllers(sg, deps, h, acq.controllers)
    val mcp = McpTools(h, acq.controllers, collections)
    h.agents.attachTools(mcp.toolNames) // what the generated definitions' placeholders may name
    val paused = h.agents.start()
    if paused > 0 then
      scribe.info(s"$paused interrupted agent run(s) paused; resume them with histmersion agent resume")
    // the worker pools stop with the server, before its pools close (released in reverse order)
    releaseAfterScope { h.agents.stop(); h.ingest.queue.stop() }
    // a swapped database: the agent lease re-acquired on it (and given up on the old one when that
    // generation closes), its interrupted jobs and runs reconciled as at a start
    leases(sg, h).left.foreach(e => throw IllegalStateException(e))
    sg.addWatch().left.foreach(e => throw IllegalStateException(e))
    // first run (#720): the bundled gazetteer / period presets into empty tables — a background job,
    // started AFTER the reconciliation above so it is not failed as "interrupted". Only `main` asks:
    // a test server must never seed its database behind the test's back
    if autoInit then
      try { val _ = h.bootstrap.autoStart() }
      catch
        case e: Exception => scribe.warn(s"first-run import not started: ${e.getMessage}")
    // every request pinned to the generation current when it arrived (hot.PinRequests)
    val options = NettySyncServerOptions.customiseInterceptors.prependInterceptor(PinRequests(sg.graph)).options
    val srv = NettySyncServer(options)
      .host(host)
      .port(port)
      .modifyConfig(
        _.requestTimeout(cfg.server.requestTimeoutSeconds.seconds)
      ) // netty default 20 s is shorter than a pg_dump
      // tapir waits this long on stop for the open connections — idle keep-alive ones included
      .modifyConfig(nc =>
        if cfg.server.shutdownGraceSeconds > 0 then
          nc.withGracefulShutdownTimeout(cfg.server.shutdownGraceSeconds.seconds)
        else closeAtOnce(nc)
      )
      .addEndpoint(mcp.endpoint)
      .addEndpoints(
        com.histmersion.domain.automation.agents.AgentsTools.agentEndpoints(h.agents)
      ) // one per agent definition, behind run tokens
      .addEndpoints(HttpApi(deps, h, acq.controllers, collections).endpoints)
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
        val _ = pipeline.addAfter(
          codec,
          "histmersion-continue",
          io.netty.handler.codec.http.HttpServerExpectContinueHandler()
        )
        // always in place: the key is read at each request (a reload may set, change or clear it)
        val _ = pipeline.addAfter(codec, "histmersion-auth", AuthHandler(() => deps.cfg.auth.apiKey, h.agents.tokens))
        // first of all: the agent MCP endpoint answers only to a live run token, and only with its agent's tools
        val _ =
          pipeline.addAfter(
            codec,
            "histmersion-run-token",
            com.histmersion.domain.automation.agents.RunTokenGate(h.agents.tokens)
          )
      }
    }

  /** The `leases` node: on a database swap, the agent lease is claimed on the new database before the swap is published
    * (and released on the old one when its generation closes); once published, the new database's interrupted jobs are
    * failed and its runs reconciled, as a start does.
    */
  private def leases(sg: ServerGraph, h: ServerWiring): Either[String, Hot[Boolean]] =
    val lease = h.agents.lease
    sg.graph.node[Boolean](
      ServerGraph.Leases,
      deps = List(sg.migrations, sg.pool),
      onPublish = held => {
        val failed = h.ingest.queue.start()
        if failed > 0 then scribe.info(s"the new database had $failed interrupted job(s): failed them")
        if held then h.agents.adoptDatabase()
        else if lease.started then
          scribe.warn(
            s"agent lease: held by ${lease.holderText} on the new database — this server's agents " +
              "start no runs there until it is free"
          )
      }
    ) { ctx =>
      val p = ctx(sg.pool)
      releaseAfterScope(lease.releaseOn(p))
      Right(if ctx.previous.isEmpty then lease.isHeld else lease.claimOn(p))
    }

  /** The library's acquisition: the clients (a hot node: rebuilt when `acquire.*` or the plugins change) and the ingest
    * the fetched copy is handed to, which writes the metadata straight away and hands the file to the OCR queue.
    */
  private def acquisition(sg: ServerGraph, deps: Deps, h: ServerWiring): AcquisitionWiring =
    val clients = sg.graph
      .node[AcquireClients](
        ServerGraph.Acquisition,
        deps = List(sg.plugins),
        keys = List("histmersion.acquire", "histmersion.media.max-retry-after-seconds")
      ) { ctx =>
        Right(acquireClients(ctx.config.app, ctx(sg.plugins), sg.core))
      }
      .fold(e => throw IllegalStateException(e), identity)
    AcquisitionWiring(deps, h.library.sourceStore, h.ingest, clients)

  /** The library's source collections (the Calibre library, archive.org): the service over the plugin generation's
    * collections, read at each call, and the library it writes through — the same handlers the endpoints run.
    */
  private def collectionsControllers(
      sg: ServerGraph,
      deps: Deps,
      h: ServerWiring,
      acq: AcquisitionControllers
  ): com.histmersion.domain.library.collections.CollectionsControllers =
    import com.histmersion.api.*
    import com.histmersion.domain.library.collections.*
    val library = new CollectionLibrary:
      def source(citekey: String) = h.library.sources.get(citekey, body = false)
      def create(citekey: String, put: SourcePut) = h.library.sources.put(citekey, put)
      def patch(citekey: String, patch: SourcePatch) = h.library.sources.patch(citekey, patch)
      def pushMeta(citekey: String) = h.calibreGaps.pushMeta(citekey)
      def pushed(req: CalibrePushRequest) = h.calibreGaps.pushed(req)
      def gaps(limit: Option[Int]) = h.calibreGaps.gaps(limit)
      def original(citekey: String) =
        h.ingest.controllers.original(citekey).flatMap { (file, disposition) =>
          scala.util
            .Try(file.bytes())
            .toEither
            .left
            .map(e => ApiError(s"cannot read the original of '$citekey': ${e.getMessage}"))
            .map(StoredOriginal(_, disposition))
        }
      def queueItem(id: Int) = h.workListView.list(Queue.Kind.Acquire.label, all = true).map(_.items.find(_.id == id))
      def ingest(params: IngestParams, bytes: Array[Byte]) =
        h.ingest.controllers.ingest(
          params,
          com.histmersion.domain.library.ingest.Upload.inline(params.filename.getOrElse("original"), bytes)
        )
      def putCover(params: SourceCoverParams, bytes: Array[Byte]) = h.media.covers.putCover(params, bytes)
      def openLibraryCover(req: MediaCoversRequest) = h.media.covers.covers(req)
      def archiveImport(item: ArchiveItem, file: ArchiveFile, req: ArchiveImportRequest) =
        acq.archiveImport(item, file, req)
    CollectionsControllers(
      CollectionsService(
        deps.transactor,
        library,
        () => sg.plugins.peek.registries.sourceCollections,
        deps.cfg.acquire.defaults.archiveRows.value
      ),
      deps.maintenance
    )

  /** The acquisition clients for one config and one plugin generation: one HTTP client (`acquire.tls` included), the
    * upstream clients built over it, and the chain of the generation's source fetchers (the built-in archive-org,
    * oa-unpaywall, oa-core, oa-openalex, oa-landing, pmc and arxiv unless switched off) in `acquire.chain` order.
    */
  private def acquireClients(app: AppConfig, plugins: Generation, core: CoreBudget): AcquireClients =
    val cfg = app.acquire
    // the one outbound policy's Retry-After cap is the media one (`media.max-retry-after-seconds`)
    // `acquire.tls` (#1568): more roots than the JDK's, only when asked for
    val tls = OutboundTls.configure(cfg.tls)
    tls.foreach((_, line) => scribe.info(line))
    val http = SttpHttp(cfg, maxRetryAfterSeconds = app.media.maxRetryAfterSeconds, tls = tls.map(_._1))
    val openalex =
      OpenAlexDiscovery(
        com.histmersion.upstreams.OpenAlexCrossref(cfg.upstreamSettings, HttpPlugin(http), HostLog.scribe.plugin)
      )
    val (chain, notes) = FetchChain.of(
      plugins.registries.sourceFetchers,
      cfg.chainIds,
      cfg.allowUnauthorised,
      plugins = plugins.registered
    )
    notes.foreach(n => scribe.info(n))
    scribe.info(chain.plan match
      case Nil  => "acquire: no source fetcher is registered — every fetch is a miss"
      case plan => s"acquire: chain ${plan.map(_._1).mkString(" → ")}")
    // CORE's search with the key the oa-core plugin resolved (its secret reference), on the same budget
    val coreKey = com.histmersion.util.plugins.Plugins.coreKey(plugins, cfg.core.key)
    val (indexes, alerts) = Indexes.of(cfg, http, coreKey, core)
    val generation = FetcherGeneration.of(chain.plan, plugins.report.plugins, cfg.contact.isDefined, coreKey.isDefined)
    AcquireClients(
      openalex,
      SemanticScholar(cfg, http),
      chain,
      Wikipedia(cfg, http),
      RetractionWatch(cfg, http),
      indexes,
      alerts,
      generation
    )
