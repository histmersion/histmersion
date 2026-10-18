package com.histmersion.server

import sttp.tapir.server.netty.sync.{NettySyncServer, NettySyncServerOptions}
import sttp.tapir.server.netty.NettyConfig
import scala.concurrent.duration.*
import com.histmersion.core.{AppConfig, ConfigFileInfo, LoadedConfig, PluginsMarkdown, UserConfigFile}
import com.histmersion.server.acquire.*
import com.histmersion.server.hot.{Hot, PinRequests}
import com.histmersion.server.plugins.{Generation, Plugins}
import ox.{Ox, never, releaseAfterScope, supervised}

/** Histmersion server — direct-style (Netty sync). Composition root: config → the hot component graph
  * (pools, migrations, plugins; docs/research-plugins.md §3.6) → Handlers → HTTP (REST + pages +
  * static) and MCP endpoints → bind. */
object Main:

  /** Build a configured (not yet started) server. `port = 0` → ephemeral port (tests use
    * `supervised { server(...).start() }`; `main` binds it in its own scope). Its components — the
    * pools above all — belong to the enclosing Ox scope and close with it. */
  def server(cfg: AppConfig, host: String, port: Int)(using Ox): NettySyncServer =
    build(HotConfig.fixed(cfg), None, host, port, None)

  /** Same, with an explicit OCR sidecar client — the seam an HTTP test uses to ingest a document
    * through a fake converter (plan §1.8). With `source`, the server can reload its config: the load it
    * was started from, and how to read it again. */
  def server(cfg: AppConfig, host: String, port: Int, converter: ConverterClient,
      autoInit: Boolean = false, onHandlers: Handlers => Unit = _ => (),
      configFile: ConfigFileInfo = ConfigFileInfo(), plugins: Generation = Plugins.offline,
      source: Option[(LoadedConfig, ConfigSource)] = None)(using Ox): NettySyncServer =
    build(hotConfig(cfg, configFile, source.map(_._1)), source.map(_._2), host, port, Some(converter), autoInit,
      onHandlers, plugins)

  private def hotConfig(cfg: AppConfig, configFile: ConfigFileInfo, loaded: Option[LoadedConfig]): HotConfig =
    loaded match
      case Some(l) => HotConfig(cfg, l.copy(config = cfg))
      case None =>
        HotConfig(cfg, LoadedConfig(cfg, UserConfigFile(configFile.path.map(java.nio.file.Path.of(_)), configFile.loaded,
          configFile.unknownKeys)))

  /** The composition root proper: the hot component graph first, then the handlers over it, then the
    * listener. `converter` `None` = the sidecar client from the config, read at each call (a reload
    * may move it). */
  private def build(hc: HotConfig, source: Option[ConfigSource], host: String, port: Int,
      converter: Option[ConverterClient], autoInit: Boolean = false, onHandlers: Handlers => Unit = _ => (),
      plugins: Generation = Plugins.offline)(using Ox): NettySyncServer =
    val sg = ServerGraph.start(hc, source, Some(plugins)).fold(e => throw IllegalStateException(e), identity)
    releaseAfterScope(sg.close())
    val deps = sg.deps(Maintenance())
    val cfg  = hc.app // the listener's settings: restart-only (ConfigReload.RestartOnly)
    val h    = Handlers(deps, converter.getOrElse(ConverterClient.fromConfig(deps.cfg.converter)))
    onHandlers(h) // a test's handle on this server's handlers (its run tokens); nothing in production
    val _    = h.ingest.start()   // reconcile interrupted jobs, then start the bounded worker pool
    val acq  = acquireHandlers(sg, deps, h)
    // embedded agents: the acquisition toolset first (enrich-source checks metadata, refs, assess;
    // the skeptic reads followups); then an interrupted run is PAUSED (resumable), queued ones
    // re-enqueued; no pool at all unless agents.enabled
    h.agents.attach(com.histmersion.server.agents.AgentUpstream.of(acq))
    val mcp = McpTools(h, acq)
    h.agents.attachTools(mcp.toolNames) // what the generated definitions' placeholders may name
    val paused = h.agents.start()
    if paused > 0 then scribe.info(s"$paused interrupted agent run(s) paused; resume them with histmersion agent resume")
    // the worker pools stop with the server, before its pools close (released in reverse order)
    releaseAfterScope { h.agents.stop(); h.ingest.stop() }
    // a swapped database: the agent lease re-acquired on it (and given up on the old one when that
    // generation closes), its interrupted jobs and runs reconciled as at a start
    leases(sg, h).left.foreach(e => throw IllegalStateException(e))
    sg.addWatch().left.foreach(e => throw IllegalStateException(e))
    // first run (#720): the bundled gazetteer / period presets into empty tables — a background job,
    // started AFTER the reconciliation above so it is not failed as "interrupted". Only `main` asks:
    // a test server must never seed its database behind the test's back
    if autoInit then
      try { val _ = h.bootstrap.autoStart() }
      catch case e: Exception => scribe.warn(s"first-run import not started: ${e.getMessage}")
    // every request pinned to the generation current when it arrived (hot.PinRequests)
    val options = NettySyncServerOptions.customiseInterceptors.prependInterceptor(PinRequests(sg.graph)).options
    val srv = NettySyncServer(options)
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
        // always in place: the key is read at each request (a reload may set, change or clear it)
        val _ = pipeline.addAfter(codec, "histmersion-auth", AuthHandler(() => deps.cfg.auth.apiKey, h.agents.tokens))
        // first of all: the agent MCP endpoint answers only to a live run token, and only with its agent's tools
        val _ = pipeline.addAfter(codec, "histmersion-run-token", com.histmersion.server.agents.RunTokenGate(h.agents.tokens))
      }
    }

  /** The `leases` node: on a database swap, the agent lease is claimed on the new database before the
    * swap is published (and released on the old one when its generation closes); once published, the
    * new database's interrupted jobs are failed and its runs reconciled, as a start does. */
  private def leases(sg: ServerGraph, h: Handlers): Either[String, Hot[Boolean]] =
    val lease = h.agents.lease
    sg.graph.node[Boolean](ServerGraph.Leases, deps = List(sg.migrations, sg.pool),
      onPublish = held => {
        val failed = h.ingest.start()
        if failed > 0 then scribe.info(s"the new database had $failed interrupted job(s): failed them")
        if held then h.agents.adoptDatabase()
        else if lease.started then
          scribe.warn(s"agent lease: held by ${lease.holderText} on the new database — this server's agents " +
            "start no runs there until it is free")
      }) { ctx =>
      val p = ctx(sg.pool)
      releaseAfterScope(lease.releaseOn(p))
      Right(if ctx.previous.isEmpty then lease.isHeld else lease.claimOn(p))
    }

  /** Stream 2.6's acquisition toolset: the clients (a hot node: rebuilt when `acquire.*` or the plugins
    * change) and the ingest job the fetched PDF is handed to — [[QueuedIngest]], which writes the
    * metadata straight away and hands the file to stream 2.4's OCR queue (§1.8). */
  private def acquireHandlers(sg: ServerGraph, deps: Deps, h: Handlers): AcquireHandlers =
    val clients = sg.graph.node[AcquireClients](ServerGraph.Acquisition, deps = List(sg.plugins),
      keys = List("histmersion.acquire", "histmersion.media.max-retry-after-seconds")) { ctx =>
      Right(acquireClients(ctx.config.app, ctx(sg.plugins)))
    }.fold(e => throw IllegalStateException(e), identity)
    AcquireHandlers(
      deps,
      discovery = clients.peek.discovery,
      citations = clients.peek.citations,
      oa = clients.peek.oa,
      wiki = clients.peek.wiki,
      archive = clients.peek.archive,
      retractionWatch = clients.peek.retractionWatch,
      jobs = QueuedIngest(h.ingest, deps.ds, deps.cfg.limits.chunkChars, deps.cfg.places.pointToleranceKm))

  /** The acquisition clients for one config and one plugin generation: one HTTP client (`acquire.tls`
    * included), the upstream clients built over it, and the chain of the generation's source fetchers
    * (the built-in archive-org, oa-unpaywall, oa-core, oa-openalex, oa-landing, pmc and arxiv unless
    * switched off) in `acquire.chain` order. */
  private def acquireClients(app: AppConfig, plugins: Generation): AcquireClients =
    val cfg = app.acquire
    // the one outbound policy's Retry-After cap is the media one (`media.max-retry-after-seconds`)
    // `acquire.tls` (#1568): more roots than the JDK's, only when asked for
    val tls = OutboundTls.configure(cfg.tls)
    tls.foreach((_, line) => scribe.info(line))
    val http     = SttpHttp(cfg, maxRetryAfterSeconds = app.media.maxRetryAfterSeconds, tls = tls.map(_._1))
    val openalex = OpenAlexCrossref(cfg, http)
    val (chain, notes) = FetchChain.of(plugins.registries.sourceFetchers, cfg.chainIds, cfg.allowUnauthorised)
    notes.foreach(n => scribe.info(n))
    scribe.info(chain.plan match
      case Nil  => "acquire: no source fetcher is registered — every fetch is a miss"
      case plan => s"acquire: chain ${plan.map(_._1).mkString(" → ")}")
    AcquireClients(openalex, SemanticScholar(cfg, http, openalex), chain, Wikipedia(cfg, http),
      ArchiveOrg(cfg, http), RetractionWatch(cfg, http))

  def main(args: Array[String]): Unit =
    // the command line first: an argument the server does not know ends it HERE, before the config, the
    // bundled runtime or the database is touched. The usage and the self-check report are the binary's
    // own output to a terminal, the one place this server writes to stdout/stderr directly.
    val mode = ServerArgs.parse(args.toList) match
      case Right(ServerMode.Help) =>
        System.out.println(ServerArgs.Usage)
        sys.exit(0)
      case Right(m) => m
      case Left(err) =>
        System.err.println(s"histmersion-server: $err\n\n${ServerArgs.Usage}")
        sys.exit(1)
    // netty's pooled allocator mishandles ByteBuf refCnt under native-image → use unpooled buffers.
    // (Safe: config never parses system properties, so these dotted keys don't reach sconfig.)
    System.setProperty("io.netty.allocator.type", "unpooled")
    System.setProperty("io.netty.leakDetection.level", "disabled")
    // eager, on the main thread (native-image StackValue constraint): reference.conf < the user config
    // file < the environment. A broken file stops the start — running on settings the user did not
    // ask for is worse than not running. A reload reads the file again with THIS environment.
    val (loadedConfig, generation) = AppConfig.loadWithUserFile() match
      case Right(l) =>
        l.file.path.foreach(p =>
          if l.file.loaded then scribe.info(s"config file $p read")
          else scribe.info(s"no config file at $p (defaults and environment only)"))
        l.file.warnings.foreach(w => scribe.warn(w))
        // the plugins, also on the main thread and before anything is opened: each block layered over
        // the plugin's defaults, type-checked, decoded, its secrets resolved, registered (staged). A
        // plugin that fails contributes nothing and the server still starts; --self-check says so.
        (l, Plugins.load(l))
      case Left(err) =>
        scribe.error(err)
        sys.exit(3)
    val loaded = loadedConfig.config
    mode match
      case ServerMode.SelfCheck =>
        System.out.println(PluginsMarkdown.selfCheck(loadedConfig.file.info, generation.report))
        sys.exit(if generation.report.ok then 0 else 2)
      case _ => ()
    val reread = () => AppConfig.loadWithUserFile(loadedConfig.env, loadedConfig.file.path)
    // the native bundle: run the bundled Postgres + converter as children and point the config at them;
    // a reloaded config is pointed at them the same way (the children themselves are restart-only)
    val explicit = Option(System.getenv("HISTMERSION_RUNTIME")).map(_.trim.toLowerCase).filter(_.nonEmpty)
    val bundled  = loaded.runtime.enabled || (explicit.isEmpty && Supervisor.bundled(Supervisor.currentExecutable))
    val (cfg, source) =
      if !bundled then (loaded, ConfigSource(reread))
      else
        try (Supervisor.start(loaded), Supervisor.reloadSource(loaded, reread))
        catch
          case e: Throwable =>
            scribe.error(s"Histmersion could not start its bundled runtime: ${e.getMessage}")
            sys.exit(3)
    supervised {
      val srv =
        try build(hotConfig(cfg, loadedConfig.file.info, Some(loadedConfig)), Some(source), cfg.server.host,
          cfg.server.port, None, autoInit = cfg.init.auto, plugins = generation)
        catch
          case e: Throwable =>
            scribe.error(s"Histmersion failed to start: ${e.getClass.getName}: ${e.getMessage}", e)
            sys.exit(3)
      val _ = srv.start()
      scribe.info(s"Histmersion listening on http://${cfg.server.host}:${cfg.server.port} (public ${cfg.server.publicUrl})")
      never
    }
