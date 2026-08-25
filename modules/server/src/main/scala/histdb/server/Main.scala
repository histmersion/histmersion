package histdb.server

import sttp.tapir.server.netty.sync.NettySyncServer
import histdb.core.AppConfig
import histdb.db.{Db, Migrate}

/** hist-db server — direct-style (Netty sync). Composition root: config → DataSource → migrate →
  * Handlers → HTTP (REST + pages + static) and MCP endpoints → bind. */
object Main:

  /** Build a configured (not yet started) server. `port = 0` → ephemeral port (tests use
    * `supervised { server(...).start() }`; `main` uses `startAndWait()`). */
  def server(cfg: AppConfig, host: String, port: Int): NettySyncServer =
    val ds = Db.dataSource(cfg.db)
    val v  = Migrate.run(ds)
    scribe.info(s"schema version $v")
    val deps = Deps(cfg, ds)
    val h    = Handlers(deps)
    NettySyncServer()
      .host(host)
      .port(port)
      .addEndpoint(McpTools(h).endpoint)
      .addEndpoints(HttpApi(deps, h).endpoints)

  def main(args: Array[String]): Unit =
    val _ = args
    // netty's pooled allocator mishandles ByteBuf refCnt under native-image → use unpooled buffers.
    // (Safe: config never parses system properties, so these dotted keys don't reach sconfig.)
    System.setProperty("io.netty.allocator.type", "unpooled")
    System.setProperty("io.netty.leakDetection.level", "disabled")
    val cfg = AppConfig.load() // eager, on the main thread (native-image StackValue constraint)
    val srv =
      try server(cfg, cfg.server.host, cfg.server.port)
      catch
        case e: Throwable =>
          scribe.error(s"hist-db failed to start: ${e.getClass.getName}: ${e.getMessage}", e)
          sys.exit(3)
    scribe.info(s"hist-db listening on http://${cfg.server.host}:${cfg.server.port} (public ${cfg.server.publicUrl})")
    srv.startAndWait()
