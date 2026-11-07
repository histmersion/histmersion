package com.histmersion.server

import com.histmersion.util.settings.{info, ConfigSource}

import com.histmersion.api.plugins.PluginsMarkdown
import com.histmersion.config.{AppConfig, Environment, UserPaths}
import com.histmersion.domain.library.acquisition.CoreBudget
import ox.{never, supervised}
import com.histmersion.util.console.Console

/** The server binary: the command line, the config and the plugins on the main thread, the bundled runtime when there
  * is one, then [[Composition]] and the listener.
  */
@main def histmersionServer(args: String*): Unit =
  // the command line first: an argument the server does not know ends it HERE, before the config, the
  // bundled runtime or the database is touched. The usage and the self-check report are the binary's
  // own output to a terminal, the one place this server writes to stdout/stderr directly.
  val console = Console.system
  val mode = ServerArgs.parse(args.toList) match
    case Right(ServerMode.Help) =>
      console.out.println(ServerArgs.Usage)
      sys.exit(0)
    case Right(m)  => m
    case Left(err) =>
      console.err.println(s"histmersion-server: $err\n\n${ServerArgs.Usage}")
      sys.exit(1)
  // netty's pooled allocator mishandles ByteBuf refCnt under native-image → use unpooled buffers.
  // (Safe: config never parses system properties, so these dotted keys don't reach sconfig.)
  System.setProperty("io.netty.allocator.type", "unpooled")
  System.setProperty("io.netty.leakDetection.level", "disabled")
  // the ONE CORE budget of this process: every plugin generation's `core` fetcher and the new-source
  // search's `core` index spend from it (kept in the database once it is open)
  val startupClock = java.time.Clock.systemUTC()
  val core = CoreBudget(() => startupClock.instant())
  // eager, on the main thread (native-image StackValue constraint): reference.conf < the user config
  // file < the environment. A broken file stops the start — running on settings the user did not
  // ask for is worse than not running. A reload reads the file again with THIS environment.
  val (loadedConfig, generation) = AppConfig.loadWithUserFile() match
    case Right(l) =>
      l.file.path.foreach(p =>
        if l.file.loaded.value then scribe.info(s"config file $p read")
        else scribe.info(s"no config file at $p (defaults and environment only)")
      )
      l.file.warnings.foreach(w => scribe.warn(w.value))
      // the plugins, also on the main thread and before anything is opened: each block layered over
      // the plugin's defaults, type-checked, decoded, its secrets resolved, registered (staged). A
      // plugin that fails contributes nothing and the server still starts; --self-check says so.
      (l, ServerPlugins.load(l, core = core))
    case Left(err) =>
      scribe.error(s"$err")
      sys.exit(3)
  val loaded = loadedConfig.config
  // the environment the settings were read with, and the paths and the clock: the only reads of them
  val environment = Environment.of(loadedConfig.env)
  val system =
    SystemContext(startupClock, environment, UserPaths(environment, UserPaths.OsName(System.getProperty("os.name", ""))))
  mode match
    case ServerMode.SelfCheck =>
      val zones = TimeZoneCheck.jdk()
      console.out.println(PluginsMarkdown.selfCheck(loadedConfig.file.info, generation.report, List((zones._1, zones._2.value))))
      sys.exit(if generation.report.ok && zones._1 then 0 else 2)
    case _ => ()
  val reread = () =>
    AppConfig.loadWithUserFile(loadedConfig.env, loadedConfig.file.path).left.map(p => ConfigSource.Problem(p.value))
  // the native bundle: run the bundled Postgres + converter as children and point the config at them;
  // a reloaded config is pointed at them the same way (the children themselves are restart-only)
  val explicit = environment.nonBlank(com.histmersion.config.Environment.VariablesKey("HISTMERSION_RUNTIME")).map(_.value.toLowerCase)
  val bundled = loaded.runtime.enabled.value || (explicit.isEmpty && Supervisor.bundled(Supervisor.currentExecutable))
  val (cfg, source) =
    if !bundled then (loaded, ConfigSource(reread))
    else
      try (Supervisor.start(loaded, system.userPaths), Supervisor.reloadSource(loaded, reread, system.userPaths))
      catch
        case e: Throwable =>
          scribe.error(s"Histmersion could not start its bundled runtime: ${e.getMessage}")
          sys.exit(3)
  // a LaunchAgent (launchd names its job in XPC_SERVICE_NAME) or a systemd unit (INVOCATION_ID) starts the
  // server again when it exits with an error: there, Settings can restart it by ending it with that code
  val underService =
    environment.get(com.histmersion.config.Environment.VariablesKey("XPC_SERVICE_NAME")).exists(_.value.contains("histmersion")) || environment
      .get(com.histmersion.config.Environment.VariablesKey("INVOCATION_ID"))
      .isDefined
  val restart = Option.when(underService) { () =>
    val code = cfg.server.restartExitCode
    val _ = Thread.ofVirtual().start { () =>
      Thread.sleep(500) // the answer goes out first
      scribe.info(s"restarting: exit ${code.value}, for the service to start the server again")
      sys.exit(code.value)
    }
  }
  supervised {
    val srv =
      try
        Composition.build(
          Composition.hotConfig(cfg, loadedConfig.file.info, Some(loadedConfig)),
          Some(source),
          Composition.Host(cfg.server.host.value),
          Composition.Port(cfg.server.port.value),
          None,
          autoInit = cfg.init.auto,
          plugins = generation,
          core = Some(core),
          restart = restart,
          system = system
        )
      catch
        case e: Throwable =>
          scribe.error(s"Histmersion failed to start: ${e.getClass.getName}: ${e.getMessage}", e)
          sys.exit(3)
    val _ = srv.start()
    scribe.info(
      s"Histmersion listening on http://${cfg.server.host}:${cfg.server.port} (public ${cfg.server.publicUrl})"
    )
    never
  }
