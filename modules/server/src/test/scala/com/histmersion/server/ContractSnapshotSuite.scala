package com.histmersion
package server
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.json.RawJson

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.Api
import com.histmersion.api.settings.ConfigEdit
import com.histmersion.api.plugins.PluginsReport
import com.histmersion.config.{AppConfig, LoadedConfig}
import com.histmersion.util.settings.{SettingsService, SettingsViews}
import com.histmersion.server.agents.{Generated, PromptBudget, ToolboxOrder}
import org.ekrich.config.ConfigFactory
import ox.supervised
import sttp.tapir.server.netty.sync.NettySyncServer

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.util.concurrent.atomic.AtomicReference
import com.histmersion.util.storage.Stores
import com.histmersion.util.storage.Resources
import com.histmersion.util.system.JvmHome
import com.histmersion.domain.library.collections.CollectionsControllers
import com.histmersion.domain.library.collections.CollectionsService
import com.histmersion.domain.library.collections.RefusingCollectionLibrary
import com.histmersion.server.agents.ClaudeCode
import com.histmersion.domain.library.acquisition.CoreBudget
import com.histmersion.lint.{LintRule, tolerated}

/** The surfaces clients and agents depend on, written to `src/test/resources/contract/` and compared byte for byte: the
  * OpenAPI document of every REST endpoint, the MCP `tools/list`, every agent role's tools and answer schemas, the
  * plugin report of a stock generation and the settings view of `reference.conf`. A refactoring that changes any of
  * them fails here; an intended change rewrites them with `HISTMERSION_UPDATE_CONTRACT=1` and shows in the diff.
  *
  * No database: the handlers run over a data source that refuses every connection, which is what the `query` tool's
  * description falls back to when the schema cannot be read. The environment is empty, so no setting of the machine
  * running the suite reaches a snapshot.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class ContractSnapshotSuite extends munit.FunSuite:

  private given Stores = Stores.disk
  private given Resources = Resources.classpath(getClass.getClassLoader)
  private given java.time.Clock = java.time.Clock.systemUTC()
  private given JvmHome = JvmHome(java.nio.file.Path.of("target/test-home").toAbsolutePath)
  private val dir = Path.of("modules/server/src/test/resources/contract")
  private val update = sys.env.get("HISTMERSION_UPDATE_CONTRACT").contains("1")

  private def check(name: String, actual: String): Unit =
    val file = dir.resolve(name)
    val text = if actual.endsWith("\n") then actual else actual + "\n"
    if update then
      Files.createDirectories(dir)
      val _ = Files.writeString(file, text, UTF_8)
    else
      assert(Files.exists(file), s"$file is missing; write it with HISTMERSION_UPDATE_CONTRACT=1")
      val expected = Files.readString(file, UTF_8)
      if expected != text then
        val firstDiff = expected.lines
          .toArray(new Array[String](_))
          .zip(text.lines.toArray(new Array[String](_)))
          .indexWhere((a, b) => a != b)
        fail(
          s"$name differs from the snapshot (first differing line ${firstDiff + 1}); if the change is intended, " +
            "rewrite it with HISTMERSION_UPDATE_CONTRACT=1"
        )

  private val env = ConfigFactory.empty
  private val loaded: LoadedConfig = AppConfig.loadWithUserText(env, "").fold(e => throw AssertionError(e), identity)

  test("openapi.json: the OpenAPI document of Api.all"):
    val doc = OpenApiDocument.of(Api.all)
    check("openapi.json", OpenApiDocument.json(Api.all).spaces2)
    // an enum without its WireEnum schema is derived from its case objects: anonymous `type`, `type1`, ... schemas
    val anonymous = doc.components.toList.flatMap(_.schemas.keys).filter(_.matches("type[0-9]*")).sorted
    assertEquals(anonymous, Nil, "a wire enum lacks `given Schema[E] = WireEnum.schema` (and its codec)")

  test("mcp-tools.json: the MCP tools/list"):
    val deps = Deps(loaded.config, RefusingDataSource)
    val h = ServerWiring(deps, OfflineConverter())
    val acq = AcquisitionTesting.controllers(
      deps,
      discovery = ???,
      citations = ???,
      oa = ???,
      wiki = ???,
      retractionWatch = ???,
      jobs = AcquisitionTesting.skeleton(deps)
    )
    val collections = CollectionsControllers(
      CollectionsService.InMemory(
        java.util.concurrent.atomic.AtomicReference(Nil),
        RefusingCollectionLibrary(),
        () => scala.collection.immutable.ListMap.empty,
        CollectionsService.Limit(8)
      ),
      deps.maintenance
    )
    val mcp = McpTools(h, acq, collections, () => deps.cfg.mcp)
    val body = supervised {
      val b = NettySyncServer().host("127.0.0.1").port(0).addEndpoint(mcp.endpoint).start()
      try
        val req = HttpRequest
          .newBuilder(URI.create(s"http://127.0.0.1:${b.port}/mcp"))
          .header("content-type", "application/json")
          .header("accept", "application/json, text/event-stream")
          .POST(HttpRequest.BodyPublishers.ofString("""{"jsonrpc":"2.0","id":1,"method":"tools/list"}"""))
          .build()
        HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString()).body
      finally b.stop()
    }
    val json = RawJson.parse(body.dropWhile(_ != '{')).fold(e => throw AssertionError(e.toString), identity)
    check("mcp-tools.json", json.spaces2)

  test("toolbox.json: every agent tool, and each agent role's tools and answer schema"):
    val defs = Generated.defs()
    val tools = Json.obj(ToolboxOrder.names.map(t => t.value -> PromptBudget.toolJson(t))*)
    val roles = Json.obj(defs.agents.sortBy(_.name.value).map { a =>
      a.name.value -> Json.obj(
        "tools" -> a.tools.map(t => t.value: String).asJson,
        "answer" -> PromptBudget.answers
          .get(a.name)
          .fold(Json.Null)(s =>
            RawJson.parse(ClaudeCode.schemaJson(s).value).fold(e => Json.fromString(e.toString), identity)
          )
      )
    }*)
    check("toolbox.json", Json.obj("tools" -> tools, "roles" -> roles).spaces2)

  test("plugins.json: the plugin report of a stock generation"):
    val report: PluginsReport =
      ServerPlugins.load(loaded, core = CoreBudget(() => java.time.Instant.now(java.time.Clock.systemUTC()))).report
    check("plugins.json", report.asJson.spaces2)

  test("settings.json: the settings view of reference.conf"):
    val page: ConfigEdit.Page = SettingsService
      .InMemory(AtomicReference(None), env)
      .read(None)
      .map(SettingsViews.page(_, restartable = false))
      .fold(e => throw AssertionError(e.toString), identity)
    check("settings.json", withoutProcessEnvironment(page.asJson).spaces2)

  /** sconfig resolves `${?VAR}` in reference.conf against the process environment whatever environment the load is
    * given, so a variable set where the suite runs (a forked test group sets `HISTMERSION_SHUTDOWN_GRACE`) shows in
    * that setting's effective value. The snapshot shows the default there instead.
    */
  private def withoutProcessEnvironment(page: Json): Json =
    def field(f: Json): Json =
      val set = f.cursor.get[String]("envVar").toOption.exists(sys.env.contains)
      f.cursor.downField("default").focus.filter(_ => set).fold(f)(d => f.mapObject(_.updated("effective", d)))
    def section(s: Json): Json =
      s.mapObject(o => o.updated("fields", o("fields").fold(Json.arr())(_.mapArray(_.map(field)))))
    page.mapObject(o => o.updated("sections", o("sections").fold(Json.arr())(_.mapArray(_.map(section)))))

/** A data source with no database behind it: every connection is refused. */
private object RefusingDataSource extends javax.sql.DataSource:
  private given Stores = Stores.disk
  private given Resources = Resources.classpath(getClass.getClassLoader)
  private given java.time.Clock = java.time.Clock.systemUTC()
  private given JvmHome = JvmHome(java.nio.file.Path.of("target/test-home").toAbsolutePath)
  private def refuse = throw java.sql.SQLException("no database in the contract snapshots")
  def getConnection(): java.sql.Connection = refuse
  def getConnection(user: String, password: String): java.sql.Connection = refuse
  def getLogWriter(): java.io.PrintWriter = null
  def setLogWriter(out: java.io.PrintWriter): Unit = ()
  def setLoginTimeout(seconds: Int): Unit = ()
  def getLoginTimeout(): Int = 0
  def getParentLogger(): java.util.logging.Logger = java.util.logging.Logger.getGlobal
  def unwrap[T](iface: Class[T]): T = throw java.sql.SQLException("not a wrapper")
  def isWrapperFor(iface: Class[?]): Boolean = false
