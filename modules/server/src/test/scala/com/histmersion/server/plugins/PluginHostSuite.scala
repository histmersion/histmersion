package com.histmersion.server.plugins

import com.histmersion.core.{PluginSetting, PluginState, PluginStatus}
import com.histmersion.plugin.*
import com.histmersion.plugin.PluginConfig.given
import hearth.kindlings.sconfigderivation.ConfigReader
import org.ekrich.config.{Config, ConfigFactory}

import java.nio.file.{Files, Path}
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.atomic.AtomicInteger
import scala.jdk.CollectionConverters.*

// ── test plugins (their defaults are under src/test/resources/META-INF/histmersion/plugins/) ──────

final case class EchoConfig(greeting: String, maxItems: Int, mirrors: List[String], token: Option[Secret])
    derives ConfigReader

/** Records what it was given; registers nothing. */
final class EchoPlugin(seen: scala.collection.mutable.Buffer[String] = scala.collection.mutable.Buffer.empty,
    override val info: PluginInfo = PluginInfo("echo", "0.1.0", Spi.Version)) extends Plugin:
  type Config = EchoConfig
  override def defaultsResource = "META-INF/histmersion/plugins/echo.conf"
  def register(c: EchoConfig, ctx: PluginContext) =
    seen += s"${c.greeting}|${c.maxItems}|${c.mirrors.mkString(",")}|${c.token.map(ctx.secret)}"
    Right(())

final case class MakerConfig(vault: String, fail: String) derives ConfigReader

/** Registers a fake vault: `<vault>:<ref>` resolves to `v(<ref>)`; `missing` is an error. Counts calls. */
final class MakerPlugin(calls: AtomicInteger = AtomicInteger(), id: String = "maker",
    escape: scala.collection.mutable.Buffer[PluginContext] = scala.collection.mutable.Buffer.empty) extends Plugin:
  type Config = MakerConfig
  val info = PluginInfo(id, "0.2.0", Spi.Version)
  override def defaultsResource = "META-INF/histmersion/plugins/maker.conf"
  def register(c: MakerConfig, ctx: PluginContext) =
    escape += ctx
    val vault = new SecretVault:
      val name = c.vault
      def resolve(ref: String) =
        calls.incrementAndGet()
        if ref == "missing" then Left(s"no entry '$ref'")
        else if ref == "boom" then throw new IllegalStateException("kaput")
        else Right(s"v($ref)")
    for
      _ <- ctx.secretVaults.add(c.vault, vault)
      _ <- if c.fail.isEmpty then Right(()) else Left(c.fail)
    yield ()

final case class ConsumerConfig(token: Secret, extra: List[Secret]) derives ConfigReader

/** Needs secrets; records the values it got (the test asserts on them, nothing else ever sees them). */
final class ConsumerPlugin(got: scala.collection.mutable.Buffer[String], id: String = "consumer") extends Plugin:
  type Config = ConsumerConfig
  val info = PluginInfo(id, "1.0.0", Spi.Version)
  override def defaultsResource = "META-INF/histmersion/plugins/consumer.conf"
  def register(c: ConsumerConfig, ctx: PluginContext) =
    (c.token :: c.extra).foreach(s => ctx.secret(s).foreach(got += _))
    Right(())

final class ThrowingPlugin extends Plugin:
  type Config = MakerConfig
  val info = PluginInfo("thrower", "0", Spi.Version)
  override def defaultsResource = "META-INF/histmersion/plugins/maker.conf"
  def register(c: MakerConfig, ctx: PluginContext) = throw new RuntimeException("bang")

class PluginHostSuite extends munit.FunSuite:
  private def conf(s: String): Config = ConfigFactory.parseString(s)
  private def env(kv: (String, String)*): Config = ConfigFactory.parseMap(kv.toMap.asJava)

  private final class Logs:
    val lines = scala.collection.mutable.Buffer.empty[String]
    val services = HostServices(() => new PluginHttp { def get(u: String, h: Map[String, String]) = Left("offline") },
      ConfigFactory.empty, (id, warn, m) => lines += s"${if warn then "W" else "I"} $id: $m")
  private def services(e: Config = ConfigFactory.empty) =
    val l = Logs(); (l, l.services.copy(env = e))

  private def build(plugins: List[Plugin], user: String, e: Config = ConfigFactory.empty) =
    val (l, s) = services(e)
    (PluginHost.build(plugins, conf(user), e, s), l)

  private def statusOf(g: Generation, id: String): PluginStatus = g.report.plugins.find(_.id == id).getOrElse(fail(s"no $id"))

  // ── the pure config contract (spike A's tests, moved over and extended) ───────────────────

  test("block: env overrides the defaults declare > the user's keys > the defaults; env keys are not kept"):
    val user = conf("histmersion.plugins.x { a = 2, b = 3 }")
    val defs = conf("a = 1\nb = 1\nb = ${?X_B}\nc = 1\nc = ${?X_C}")
    val b    = PluginHost.block(user, defs, env("X_B" -> "7", "X_C" -> "8", "HOME" -> "/h"), "x").toOption.get
    assertEquals((b.getInt("a"), b.getInt("b"), b.getInt("c")), (2, 7, 8), "the env beats the file (b)")
    assertEquals(b.root.keySet.asScala.toSet, Set("a", "b", "c"))

  test("block: no user block → the defaults; an unresolvable substitution is an error naming the block"):
    assertEquals(PluginHost.block(ConfigFactory.empty, conf("a = 1"), ConfigFactory.empty, "x").map(_.getInt("a")), Right(1))
    assert(PluginHost.block(ConfigFactory.empty, conf("a = ${NOPE}"), ConfigFactory.empty, "x")
      .left.exists(_.startsWith("histmersion.plugins.x")))

  test("unknownBlocks lists the blocks no plugin claims; `order` is the host's key"):
    val user = conf("histmersion.plugins { order = [hello], hello { enabled = true }, helo { enabled = true } }")
    assertEquals(PluginHost.unknownBlocks(user, Set("hello")), List("helo"))
    assertEquals(PluginHost.unknownBlocks(ConfigFactory.empty, Set("hello")), Nil)
    assertEquals(PluginHost.unknownBlocks(conf("histmersion.plugins = 3"), Set("hello")), Nil)

  test("check refuses a foreign SPI version, a duplicate id, a non-kebab id and the reserved key"):
    val ok = PluginInfo("a", "1", 1)
    assertEquals(PluginHost.check(List(ok, ok.copy(id = "1password")), 1), Map.empty)
    val errs = PluginHost.check(List(ok, ok, ok.copy(id = "B_x"), ok.copy(id = "c", spiVersion = 2), ok.copy(id = "order")), 1)
    assertEquals(errs.keySet, Set("a", "B_x", "c", "order"))
    assert(errs("a").contains("claimed by 2 plugins"))
    assert(errs("c").contains("built for SPI 2, this host speaks 1"))
    assert(errs("B_x").contains("kebab-case"))
    assert(errs("order").contains("not a plugin id"))

  test("order: histmersion.plugins.order first, as listed, then the rest by id; unknown entries warned"):
    assertEquals(PluginHost.order(List("c", "a", "b"), Nil), (List("a", "b", "c"), Nil))
    val (o, w) = PluginHost.order(List("c", "a", "b"), List("c", "zz", "c"))
    assertEquals(o, List("c", "a", "b"))
    assertEquals(w, List("histmersion.plugins.order names 'zz', which is no plugin in this binary"))
    assertEquals(PluginHost.configuredOrder(conf("histmersion.plugins.order = [b, a]"), ConfigFactory.empty), Right(List("b", "a")))
    assert(PluginHost.configuredOrder(conf("histmersion.plugins.order = b"), ConfigFactory.empty).left.exists(_.contains("must be a list")))
    assertEquals(PluginHost.configuredOrder(ConfigFactory.empty, ConfigFactory.empty), Right(Nil))

  test("digest is order-independent and changes with any jar"):
    val a = List("a.jar" -> "11", "b.jar" -> "22")
    assertEquals(PluginHost.digest(a), PluginHost.digest(a.reverse))
    assertNotEquals(PluginHost.digest(a), PluginHost.digest(List("a.jar" -> "11", "b.jar" -> "23")))
    assertNotEquals(PluginHost.digest(a), PluginHost.digest(a.take(1)))
    assertEquals(PluginHost.digest(Nil).length, 64)

  test("decode: kebab-case keys into the plugin's own case class; `enabled` is the host's"):
    val p = EchoPlugin()
    val c = conf("enabled = true\ngreeting = g\nmax-items = \"3\"\nmirrors = []\ntoken = null")
    assertEquals(PluginHost.decode(p, c), Right(EchoConfig("g", 3, Nil, None)))

  test("decode: the derived reader alone names the block, NOT the key, of a wrong type (why typeCheck exists)"):
    val e = PluginHost.decode(EchoPlugin(), conf("greeting = g\nmax-items = lots\nmirrors = []\ntoken = null"))
    assert(e.left.exists(_.startsWith("histmersion.plugins.echo")), e)

  test("typeCheck names the key, against the kinds in the plugin's defaults; a null default takes anything"):
    val defs = conf("max-items = 1\nname = n\nmirrors = []\ntoken = null\nnested { depth = 2 }")
    def errs(s: String) = PluginHost.typeCheck("test", conf(s), defs)
    assertEquals(errs("histmersion.plugins.test { max-items = \"7\", name = 3, enabled = true, token = \"env:X\" }"), Nil)
    assertEquals(errs("histmersion.plugins.test { max-items = lots }"),
      List("histmersion.plugins.test.max-items must be a number, not \"lots\""))
    assertEquals(errs("histmersion.plugins.test { mirrors = x }"),
      List("histmersion.plugins.test.mirrors must be a list [ … ], not \"x\""))
    assertEquals(errs("histmersion.plugins.test { maxitems = 1 }"),
      List("histmersion.plugins.test.maxitems: unknown setting (not in the plugin's defaults)"))
    assertEquals(errs("histmersion.plugins.test { enabled = maybe }"), List("histmersion.plugins.test.enabled must be true or false"))
    assertEquals(errs("histmersion.plugins.test.nested.depth = deep"),
      List("histmersion.plugins.test.nested.depth must be a number, not \"deep\""))
    assertEquals(errs("histmersion.plugins.test.nested.width = 1"),
      List("histmersion.plugins.test.nested.width: unknown setting (not in the plugin's defaults)"))
    assertEquals(errs("histmersion.plugins.other { x = 1 }"), Nil)

  test("enabled: a boolean or a boolean string; absent is disabled"):
    assertEquals(PluginHost.enabled(conf("enabled = true"), "x"), Right(true))
    assertEquals(PluginHost.enabled(conf("enabled = \"false\""), "x"), Right(false))
    assertEquals(PluginHost.enabled(ConfigFactory.empty, "x"), Right(false))
    assert(PluginHost.enabled(conf("enabled = sometimes"), "x").isLeft)

  test("secretsIn walks case classes, options, lists and maps, in order, without repeats"):
    val a = Secret.parse("env:A").toOption.get
    val b = Secret.parse("v:b").toOption.get
    val c = Secret.parse("value:c").toOption.get
    final case class Inner(s: Option[Secret], m: Map[String, Secret])
    final case class Outer(x: Int, first: Secret, inner: Inner, list: List[Secret], none: Option[Secret])
    assertEquals(PluginHost.secretsIn(Outer(1, a, Inner(Some(b), Map("k" -> c)), List(a, b), None)), List(a, b, c))
    assertEquals(PluginHost.secretsIn(EchoConfig("g", 1, Nil, None)), Nil)

  test("settings: the effective block without `enabled`, a literal value: masked, references shown"):
    val s = PluginHost.settings(conf(
      "enabled = true\npassword = \"value:hunter2\"\ntoken = \"env:T\"\nkey = null\nn = 3\nl = [a]\nsub { x = \"keepassxc:a#b\" }"))
    assertEquals(s, List(PluginSetting("key", "null"), PluginSetting("l", "[\"a\"]"), PluginSetting("n", "3"),
      PluginSetting("password", "value:***"), PluginSetting("sub.x", "keepassxc:a#b"), PluginSetting("token", "env:T")))
    assert(!s.toString.contains("hunter2"))
    val nested = PluginHost.settings(conf("tokens = [\"value:abc\", \"env:B\"]\nmap = [{ p = \"value:xyz\" }]"))
    assertEquals(nested.map(_.key), List("map", "tokens"))
    assert(!nested.toString.contains("abc") && !nested.toString.contains("xyz"), nested)
    assert(nested.exists(_.value.contains("env:B")), nested)
    assertEquals(PluginHost.settings(conf("account = \"\"")), List(PluginSetting("account", "\"\"")))

  test("resolve: value: is the literal, env: the variable, a vault its answer; errors name the reference only"):
    val vault = new SecretVault { val name = "kp"; def resolve(r: String) = if r == "x" then Right("vx") else Left("locked") }
    val e = env("TOKEN" -> "t0k")
    assertEquals(PluginHost.resolve(SecretRef.Value("lit"), e, Map.empty), Right("lit"))
    assertEquals(PluginHost.resolve(SecretRef.Env("TOKEN"), e, Map.empty), Right("t0k"))
    assertEquals(PluginHost.resolve(SecretRef.Env("NOPE"), e, Map.empty),
      Left("secret env:NOPE: environment variable NOPE is not set"))
    assertEquals(PluginHost.resolve(SecretRef.Vault("kp", "x"), e, Map("kp" -> vault)), Right("vx"))
    assertEquals(PluginHost.resolve(SecretRef.Vault("kp", "y"), e, Map("kp" -> vault)), Left("secret kp:y: locked"))
    assert(PluginHost.resolve(SecretRef.Vault("op", "y"), e, Map("kp" -> vault)).left.exists(_.contains("no secret vault 'op'")))

  // ── a generation ────────────────────────────────────────────────────────────────────────────

  test("a plugin is disabled unless enabled; its settings are still reported"):
    val (g, _) = build(List(EchoPlugin()), "")
    val st = statusOf(g, "echo")
    assertEquals((st.state, st.enabled), (PluginState.Disabled.wire, false))
    assert(st.settings.contains(PluginSetting("greeting", "hello from the defaults")), st.settings)
    assert(g.report.ok)

  test("an enabled plugin gets its typed config: the user's file over its defaults, an env override over both"):
    val seen = scala.collection.mutable.Buffer.empty[String]
    val (g, _) = build(List(EchoPlugin(seen)),
      """histmersion.plugins.echo { enabled = true, greeting = "from the file", max-items = 6, mirrors = [x, y] }""",
      env("HISTMERSION_PLUGIN_ECHO_MAX_ITEMS" -> "9"))
    assertEquals(statusOf(g, "echo").state, PluginState.Registered.wire)
    assertEquals(seen.toList, List("from the file|9|x,y|None"))

  test("enabled can come from the environment through a defaults line, and as a string"):
    val seen = scala.collection.mutable.Buffer.empty[String]
    val (g, _) = build(List(EchoPlugin(seen)), """histmersion.plugins.echo.enabled = "true"""")
    assertEquals(statusOf(g, "echo").state, PluginState.Registered.wire)

  test("a vault plugin's registration is committed and listed; a consumer's secrets resolve through it"):
    val got   = scala.collection.mutable.Buffer.empty[String]
    val calls = AtomicInteger()
    val (g, _) = build(List(MakerPlugin(calls), ConsumerPlugin(got)),
      """histmersion.plugins {
        |  maker.enabled = true
        |  consumer { enabled = true, token = "fake:a", extra = ["fake:a", "env:E", "fake:b"] }
        |}""".stripMargin, env("E" -> "e-val"))
    assertEquals(g.registries.secretVaults.keys.toList, List("fake"))
    assertEquals(statusOf(g, "maker").provides, List("secret-vault:fake"))
    assertEquals(statusOf(g, "consumer").state, PluginState.Registered.wire)
    assertEquals(got.toList, List("v(a)", "v(a)", "e-val", "v(b)"))
    assertEquals(calls.get, 2, "one vault call per reference per generation (a, b), then the generation's cache")
    // the values never reach the report
    assert(!g.report.toString.contains("v(a)") && !g.report.toString.contains("e-val"), g.report)
    assertEquals(statusOf(g, "consumer").settings.find(_.key == "token").map(_.value), Some("fake:a"))

  test("a consumer ordered before its vault waits for it (deferred, then registered)"):
    val got = scala.collection.mutable.Buffer.empty[String]
    // "consumer" sorts before "maker", and order puts it first explicitly too
    val (g, _) = build(List(MakerPlugin(), ConsumerPlugin(got)),
      """histmersion.plugins { order = [consumer, maker], maker.enabled = true, consumer { enabled = true, token = "fake:t" } }""")
    assertEquals(statusOf(g, "consumer").state, PluginState.Registered.wire)
    assertEquals(got.toList, List("v(t)"))
    assertEquals(g.report.plugins.map(_.id), List("consumer", "maker"), "the report keeps the configured order")

  test("a secret naming a vault nobody registered fails that plugin only, naming the vault"):
    val got = scala.collection.mutable.Buffer.empty[String]
    val (g, logs) = build(List(EchoPlugin(), ConsumerPlugin(got)),
      """histmersion.plugins { echo.enabled = true, consumer { enabled = true, token = "keepassxc:x#password" } }""")
    val st = statusOf(g, "consumer")
    assertEquals(st.state, PluginState.Failed.wire)
    assertEquals(st.error,
      Some("histmersion.plugins.consumer: no secret vault 'keepassxc' is registered (enable the plugin that provides it)"))
    assertEquals(statusOf(g, "echo").state, PluginState.Registered.wire)
    assert(!g.report.ok)
    assert(logs.lines.exists(_.startsWith("W consumer: histmersion.plugins.consumer: no secret vault 'keepassxc'")), logs.lines)
    assertEquals(got.toList, Nil, "register was never called")

  test("an unresolvable secret (unset env, a vault error, a vault that throws) fails registration with the reference"):
    def run(token: String) =
      val (g, _) = build(List(MakerPlugin(), ConsumerPlugin(scala.collection.mutable.Buffer.empty)),
        s"""histmersion.plugins { maker.enabled = true, consumer { enabled = true, token = "$token" } }""")
      statusOf(g, "consumer").error
    assertEquals(run("env:CONSUMER_UNSET"),
      Some("histmersion.plugins.consumer: secret env:CONSUMER_UNSET: environment variable CONSUMER_UNSET is not set"))
    assertEquals(run("fake:missing"), Some("histmersion.plugins.consumer: secret fake:missing: no entry 'missing'"))
    assertEquals(run("fake:boom"),
      Some("histmersion.plugins.consumer: secret fake:boom: vault 'fake' failed: IllegalStateException"))

  test("value: works, warns, and is masked in the report"):
    val got = scala.collection.mutable.Buffer.empty[String]
    val (g, logs) = build(List(ConsumerPlugin(got)),
      """histmersion.plugins.consumer { enabled = true, token = "value:hunter2" }""")
    val st = statusOf(g, "consumer")
    assertEquals(st.state, PluginState.Registered.wire)
    assertEquals(got.toList, List("hunter2"))
    assert(st.warnings.exists(_.contains("a secret is written into the config")), st.warnings)
    assert(logs.lines.exists(_.contains("a secret is written into the config")))
    assert(!g.report.toString.contains("hunter2"), g.report)
    assert(!logs.lines.mkString.contains("hunter2"))

  test("staged registries: a plugin whose register fails contributes nothing; one that throws too"):
    val (g, _) = build(List(MakerPlugin(), ThrowingPlugin(), EchoPlugin()),
      """histmersion.plugins { maker { enabled = true, fail = "no licence" }, thrower.enabled = true, echo.enabled = true }""")
    assertEquals(g.registries.secretVaults, scala.collection.immutable.ListMap.empty[String, SecretVault],
      "the vault maker added before failing is discarded")
    assertEquals(statusOf(g, "maker").error, Some("histmersion.plugins.maker: no licence"))
    assertEquals(statusOf(g, "maker").provides, Nil)
    assertEquals(statusOf(g, "thrower").error, Some("histmersion.plugins.thrower: register threw RuntimeException: bang"))
    assertEquals(statusOf(g, "echo").state, PluginState.Registered.wire)

  test("a registry refuses a second vault of the same name, a reserved or mismatched name, and adds after register"):
    val escaped = scala.collection.mutable.Buffer.empty[PluginContext]
    val (g, _) = build(List(MakerPlugin(id = "maker", escape = escaped), MakerPlugin(id = "other")),
      """histmersion.plugins { order = [maker, other], maker.enabled = true, other.enabled = true }""")
    // `other` reads maker.conf too, so both register a vault named `fake`
    assertEquals(statusOf(g, "maker").state, PluginState.Registered.wire)
    assertEquals(statusOf(g, "other").error, Some("histmersion.plugins.other: secret-vault 'fake' is already registered"))
    val late = escaped.head.secretVaults.add("late", new SecretVault { val name = "late"; def resolve(r: String) = Right("") })
    assert(late.left.exists(_.contains("registration is over")), late)
    val (g2, _) = build(List(MakerPlugin()), """histmersion.plugins.maker { enabled = true, vault = env }""")
    assert(statusOf(g2, "maker").error.exists(_.contains("reserved secret scheme")), statusOf(g2, "maker"))
    val (g3, _) = build(List(MakerPlugin()), """histmersion.plugins.maker { enabled = true, vault = "Bad_Name" }""")
    assert(statusOf(g3, "maker").error.exists(_.contains("a vault name is")), statusOf(g3, "maker"))

  test("a mistyped key fails that plugin with the key named; the others still register"):
    val (g, _) = build(List(EchoPlugin(), MakerPlugin()),
      """histmersion.plugins { echo { enabled = true, max-items = many }, maker.enabled = true }""")
    assertEquals(statusOf(g, "echo").error, Some("histmersion.plugins.echo.max-items must be a number, not \"many\""))
    assertEquals(statusOf(g, "echo").enabled, true)
    assertEquals(statusOf(g, "maker").state, PluginState.Registered.wire)

  test("refused plugins (foreign SPI, duplicate ids) are reported and never registered; config warnings collected"):
    val seen = scala.collection.mutable.Buffer.empty[String]
    val old  = EchoPlugin(seen, PluginInfo("echo", "0.0.1", Spi.Version + 1))
    val (g, logs) = build(List(old, MakerPlugin(), MakerPlugin()),
      """histmersion.plugins { order = [ghost], echo.enabled = true, maker.enabled = true, helo.enabled = true }""")
    assertEquals(g.report.plugins.map(p => p.id -> p.state),
      List("echo" -> "refused", "maker" -> "refused", "maker" -> "refused"))
    assertEquals(seen.toList, Nil)
    assertEquals(g.registries.secretVaults.size, 0)
    assertEquals(g.report.warnings, List(
      "histmersion.plugins.order names 'ghost', which is no plugin in this binary",
      "histmersion.plugins.helo configures no plugin in this binary — ignored"))
    assert(logs.lines.exists(_.contains("helo configures no plugin")))
    assertEquals(g.report.spiVersion, Spi.Version)

  test("builds are independent: no state survives from one generation to the next"):
    val calls = AtomicInteger()
    val got   = scala.collection.mutable.Buffer.empty[String]
    val plugins = List(MakerPlugin(calls), ConsumerPlugin(got))
    val user = """histmersion.plugins { maker.enabled = true, consumer { enabled = true, token = "fake:a" } }"""
    val (g1, _) = build(plugins, user)
    val (g2, _) = build(plugins, user)
    assertEquals(calls.get, 2, "the secret is resolved again for the second generation (no process-wide cache)")
    assert(g1.registries.secretVaults("fake") ne g2.registries.secretVaults("fake"))
    val (off, _) = build(plugins, "")
    assertEquals(off.registries.secretVaults.size, 0)

  // ── the real thing: the built-in vaults on the server's class path ──────────────────────────

  test("discovery finds the built-in vault plugins; by default all are disabled and the report is ok"):
    val (found, errs) = PluginHost.discover(getClass.getClassLoader)
    assertEquals(errs, Nil)
    assertEquals(found.map(_.info.id).sorted, List("1password", "bitwarden", "keepassxc"))
    val (g, _) = build(found, "")
    assertEquals(g.report.plugins.map(p => p.id -> p.state),
      List("1password" -> "disabled", "bitwarden" -> "disabled", "keepassxc" -> "disabled"))
    assert(g.report.ok)

  test("a key starting with a digit is a plain HOCON key: histmersion.plugins.1password"):
    val (found, _) = PluginHost.discover(getClass.getClassLoader)
    val (g, _) = build(found, """histmersion.plugins.1password { enabled = true, cli = "/nonexistent/op" }
                                |histmersion.plugins { bitwarden { timeout-seconds = "5" } }""".stripMargin)
    assertEquals(statusOf(g, "1password").error, Some("histmersion.plugins.1password: /nonexistent/op is not an executable file"))
    assert(statusOf(g, "bitwarden").settings.contains(PluginSetting("timeout-seconds", "5")))

  test("end to end: bitwarden's session comes from the keepassxc vault (a fake keepassxc-cli on a temp PATH)"):
    val d = Files.createTempDirectory("histmersion-fake-bin")
    val cli = d.resolve("keepassxc-cli")
    Files.writeString(cli, "#!/bin/sh\nIFS= read -r pw\n[ \"$pw\" = \"kp-master\" ] || { echo 'Invalid credentials' >&2; exit 1; }\n" +
      "for a; do e=$a; done\nprintf 'session-from-%s\\n' \"$e\"\n")
    Files.setPosixFilePermissions(cli, PosixFilePermissions.fromString("rwx------"))
    Files.writeString(d.resolve("bw"), "#!/bin/sh\nexit 0\n")
    Files.setPosixFilePermissions(d.resolve("bw"), PosixFilePermissions.fromString("rwx------"))
    val db = Files.writeString(d.resolve("v.kdbx"), "")
    val (found, _) = PluginHost.discover(getClass.getClassLoader)
    val e = env("PATH" -> d.toString, "KP_PASSWORD" -> "kp-master")
    val (g, logs) = build(found,
      s"""histmersion.plugins {
         |  keepassxc { enabled = true, database = "$db", password = "env:KP_PASSWORD" }
         |  bitwarden { enabled = true, session = "keepassxc:Histmersion/bw#password" }
         |}""".stripMargin, e)
    assertEquals(g.report.plugins.map(p => p.id -> p.state),
      List("1password" -> "disabled", "bitwarden" -> "registered", "keepassxc" -> "registered"))
    assertEquals(g.registries.secretVaults.keys.toList, List("keepassxc", "bitwarden"),
      "bitwarden (sorted first) waited for keepassxc")
    assertEquals(g.registries.secretVaults("keepassxc").resolve("x"), Right("session-from-x"))
    val shown = g.report.toString + logs.lines.mkString
    assert(!shown.contains("session-from") && !shown.contains("kp-master"), shown)
    assert(statusOf(g, "bitwarden").settings.contains(PluginSetting("session", "keepassxc:Histmersion/bw#password")))
