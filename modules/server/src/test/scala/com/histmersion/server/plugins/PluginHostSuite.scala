package com.histmersion
package server.plugins
import com.histmersion.util.problem.{External, Legacy, Problem}
import com.histmersion.util.problem.Text.{sentence, sentences}

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.util.plugins.Plugins

import com.histmersion.pluginhost.{Generation, HostServices, PluginHost, ReservedNames}

import com.histmersion.api.Sha256
import com.histmersion.api.plugins.{PluginJar, PluginSetting, PluginState, PluginStatus}
import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given
import com.histmersion.api.VocabularyCodecs.given
import hearth.kindlings.sconfigderivation.ConfigReader
import org.ekrich.config.{Config, ConfigFactory}

import java.nio.file.{Files, Path}
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.atomic.AtomicInteger
import scala.jdk.CollectionConverters.*
import com.histmersion.plugin.Plugin
import com.histmersion.pluginhost.DiskLocalFiles
import com.histmersion.pluginhost.SystemProcesses
import com.histmersion.plugin.Secret
import com.histmersion.plugin.Registry
import com.histmersion.pluginhost.Registries
import com.histmersion.config.ConfigReload

// ── test plugins (their defaults are under src/test/resources/META-INF/histmersion/plugins/) ──────

final case class EchoConfig(greeting: String, maxItems: Int, mirrors: List[String], token: Option[Secret])
    derives ConfigReader

/** Records what it was given; registers nothing. */
final class EchoPlugin(
    seen: scala.collection.mutable.Buffer[String] = scala.collection.mutable.Buffer.empty,
    override val info: Plugin.Info =
      Plugin.Info(Plugin.Info.Id("echo"), Plugin.Info.Version("0.1.0"), Plugin.Info.SpiVersion(Spi.Version))
) extends Plugin:
  type Config = EchoConfig
  override def defaultsResource = Plugin.DefaultsResource("META-INF/histmersion/plugins/echo.conf")
  def register(c: EchoConfig, ctx: Plugin.Context) =
    seen += s"${c.greeting}|${c.maxItems}|${c.mirrors.mkString(",")}|${c.token.map(ctx.secret)}"
    Right(())

final case class MakerConfig(vault: String, fail: String) derives ConfigReader

/** Registers a fake vault: `<vault>:<ref>` resolves to `v(<ref>)`; `missing` is an error. Counts calls. */
final class MakerPlugin(
    calls: AtomicInteger = AtomicInteger(),
    id: Plugin.Info.Id = Plugin.Info.Id("maker"),
    escape: scala.collection.mutable.Buffer[Plugin.Context] = scala.collection.mutable.Buffer.empty
) extends Plugin:
  type Config = MakerConfig
  val info = Plugin.Info(id, Plugin.Info.Version("0.2.0"), Plugin.Info.SpiVersion(Spi.Version))
  override def defaultsResource = Plugin.DefaultsResource("META-INF/histmersion/plugins/maker.conf")
  def register(c: MakerConfig, ctx: Plugin.Context) =
    escape += ctx
    val vault = new Secret.Vault:
      val name = Secret.Vault.Name(c.vault)
      def resolve(ref: Secret.Vault.ResolveRef) =
        calls.incrementAndGet()
        if ref.value == "missing" then Left(Problem.Unclassified(Legacy.Text(s"no entry '$ref'")))
        else if ref.value == "boom" then throw new IllegalStateException("kaput")
        else Right(Secret.Resolved(s"v($ref)"))
    for
      _ <- ctx.secretVaults.add(Registry.AddId(c.vault), vault)
      _ <- if c.fail.isEmpty then Right(()) else Left(Problem.Unclassified(Legacy.Text(c.fail)))
    yield ()

/** Registers a vault that answers in batches: every `resolveAll` call is recorded with what it asked; `missing` is an
  * error, `unanswered` is left out of the answer. `resolve` alone is counted apart.
  */
final class BatchVaultPlugin(
    batches: scala.collection.mutable.Buffer[List[String]],
    singles: AtomicInteger,
    id: Plugin.Info.Id = Plugin.Info.Id("batcher")
) extends Plugin:
  type Config = MakerConfig
  val info = Plugin.Info(id, Plugin.Info.Version("0.3.0"), Plugin.Info.SpiVersion(Spi.Version))
  override def defaultsResource = Plugin.DefaultsResource("META-INF/histmersion/plugins/maker.conf")
  def register(c: MakerConfig, ctx: Plugin.Context) =
    ctx.secretVaults
      .add(
        Registry.AddId(c.vault),
        new Secret.Vault:
          val name = Secret.Vault.Name(c.vault)
          def resolve(ref: Secret.Vault.ResolveRef) = {
            singles.incrementAndGet(); Right(Secret.Resolved(s"one($ref)"))
          }
          override def resolveAll(refs: List[Secret.Vault.ResolveRef]) =
            batches += refs.map(_.value)
            refs
              .filterNot(_.value == "unanswered")
              .map(r =>
                r -> (if r.value == "missing" then Left(Problem.Unclassified(Legacy.Text(s"no entry '$r'")))
                      else Right(Secret.Resolved(s"b($r)")))
              )
              .toMap
      )

final case class ConsumerConfig(token: Secret, extra: List[Secret]) derives ConfigReader

/** Needs secrets; records the values it got (the test asserts on them, nothing else ever sees them). */
final class ConsumerPlugin(
    got: scala.collection.mutable.Buffer[String],
    id: Plugin.Info.Id = Plugin.Info.Id("consumer")
) extends Plugin:
  type Config = ConsumerConfig
  val info = Plugin.Info(id, Plugin.Info.Version("1.0.0"), Plugin.Info.SpiVersion(Spi.Version))
  override def defaultsResource = Plugin.DefaultsResource("META-INF/histmersion/plugins/consumer.conf")
  def register(c: ConsumerConfig, ctx: Plugin.Context) =
    (c.token :: c.extra).foreach(s => ctx.secret(s).foreach(v => got += v.value))
    Right(())

final class ThrowingPlugin extends Plugin:
  type Config = MakerConfig
  val info = Plugin.Info(Plugin.Info.Id("thrower"), Plugin.Info.Version("0"), Plugin.Info.SpiVersion(Spi.Version))
  override def defaultsResource = Plugin.DefaultsResource("META-INF/histmersion/plugins/maker.conf")
  def register(c: MakerConfig, ctx: Plugin.Context) = throw new RuntimeException("bang")

/** Uses a library that is not on the class path: what a plugin installed without its dependencies does. */
final class MissingLibraryPlugin extends Plugin:
  type Config = MakerConfig
  val info = Plugin.Info(Plugin.Info.Id("needs-lib"), Plugin.Info.Version("0"), Plugin.Info.SpiVersion(Spi.Version))
  override def defaultsResource = Plugin.DefaultsResource("META-INF/histmersion/plugins/maker.conf")
  def register(c: MakerConfig, ctx: Plugin.Context) = throw new NoClassDefFoundError("org/example/xml/Elem")

class PluginHostSuite extends munit.FunSuite:
  private def conf(s: String): Config = ConfigFactory.parseString(s)
  private def env(kv: (String, String)*): Config = ConfigFactory.parseMap(kv.toMap.asJava)

  final private class Logs:
    val lines = scala.collection.mutable.Buffer.empty[String]
    val services = HostServices(
      () =>
        new Plugin.Http {
          def get(u: Plugin.Http.Url, h: Map[Header.Name, Header.Value]) =
            Left(Problem.Unclassified(Legacy.Text("offline")))
        },
      ConfigFactory.empty,
      (id, warn, m) => lines += s"${if warn then "W" else "I"} $id: $m",
      files = new DiskLocalFiles,
      processes = new SystemProcesses
    )
  private def services(e: Config = ConfigFactory.empty) =
    val l = Logs(); (l, l.services.modify(_.env).setTo(e))

  private def build(plugins: List[Plugin], user: String, e: Config = ConfigFactory.empty) =
    val (l, s) = services(e)
    (PluginHost.build(plugins, conf(user), e, s), l)

  private def statusOf(g: Generation, id: String): PluginStatus =
    g.report.plugins.find(_.id.value == id).getOrElse(fail(s"no $id"))

  // ── the pure config contract (spike A's tests, moved over and extended) ───────────────────

  test("block: env overrides the defaults declare > the user's keys > the defaults; env keys are not kept"):
    val user = conf("histmersion.plugins.x { a = 2, b = 3 }")
    val defs = conf("a = 1\nb = 1\nb = ${?X_B}\nc = 1\nc = ${?X_C}")
    val b =
      PluginHost.block(user, defs, env("X_B" -> "7", "X_C" -> "8", "HOME" -> "/h"), Plugin.Info.Id("x")).toOption.get
    assertEquals((b.getInt("a"), b.getInt("b"), b.getInt("c")), (2, 7, 8), "the env beats the file (b)")
    assertEquals(b.root.keySet.asScala.toSet, Set("a", "b", "c"))

  test("block: no user block → the defaults; an unresolvable substitution is an error naming the block"):
    assertEquals(
      PluginHost.block(ConfigFactory.empty, conf("a = 1"), ConfigFactory.empty, Plugin.Info.Id("x")).map(_.getInt("a")),
      Right(1)
    )
    assert(
      PluginHost
        .block(ConfigFactory.empty, conf("a = ${NOPE}"), ConfigFactory.empty, Plugin.Info.Id("x"))
        .left
        .exists(_.sentence.startsWith("histmersion.plugins.x"))
    )

  test("unknownBlocks lists the blocks no plugin claims; `order` is the host's key"):
    val user = conf("histmersion.plugins { order = [hello], hello { enabled = true }, helo { enabled = true } }")
    assertEquals(PluginHost.unknownBlocks(user, Set(Plugin.Info.Id("hello"))), List("helo"))
    assertEquals(PluginHost.unknownBlocks(ConfigFactory.empty, Set(Plugin.Info.Id("hello"))), Nil)
    assertEquals(PluginHost.unknownBlocks(conf("histmersion.plugins = 3"), Set(Plugin.Info.Id("hello"))), Nil)

  test("check refuses a foreign SPI version, a duplicate id, a non-kebab id and the reserved key"):
    val ok = Plugin.Info(Plugin.Info.Id("a"), Plugin.Info.Version("1"), Plugin.Info.SpiVersion(1))
    assertEquals(
      PluginHost.check(List(ok, ok.modify(_.id).setTo(Plugin.Info.Id("1password"))), Plugin.Info.SpiVersion(1)),
      Map.empty
    )
    val errs =
      PluginHost.check(
        List(
          ok,
          ok,
          ok.modify(_.id).setTo(Plugin.Info.Id("B_x")),
          ok.modify(_.id).setTo(Plugin.Info.Id("c")).modify(_.spiVersion).setTo(Plugin.Info.SpiVersion(2)),
          ok.modify(_.id).setTo(Plugin.Info.Id("order"))
        ),
        Plugin.Info.SpiVersion(1)
      )
    assertEquals(errs.keySet, Set("a", "B_x", "c", "order"))
    assertEquals(errs(Plugin.Info.Id("a")), Problem.Plugin.Load(Problem.Plugin.Id("a"), Problem.Plugin.Fault.Duplicate))
    assert(errs(Plugin.Info.Id("c")).sentence.contains("built against plugin SPI 2; this Histmersion speaks SPI 1"))
    assert(errs(Plugin.Info.Id("B_x")).sentence.contains("'B_x' is not a registry name"))
    assertEquals(
      errs(Plugin.Info.Id("order")),
      Problem.Conflict.AlreadyExists(Problem.TypeName.of(Plugin.Info.Id), Problem.Raw("order"))
    )

  test("order: histmersion.plugins.order first, as listed, then the rest by id; unknown entries warned"):
    assertEquals(
      PluginHost.order(List(Plugin.Info.Id("c"), Plugin.Info.Id("a"), Plugin.Info.Id("b")), Nil),
      (List("a", "b", "c"), Nil)
    )
    val (o, w) = PluginHost.order(
      List(Plugin.Info.Id("c"), Plugin.Info.Id("a"), Plugin.Info.Id("b")),
      List(Plugin.Info.Id("c"), Plugin.Info.Id("zz"), Plugin.Info.Id("c"))
    )
    assertEquals(o, List("c", "a", "b"))
    assertEquals(w.map(_.sentence), List("histmersion.plugins.order names 'zz', which is no plugin in this binary"))
    assertEquals(
      PluginHost.configuredOrder(conf("histmersion.plugins.order = [b, a]"), ConfigFactory.empty),
      Right(List("b", "a"))
    )
    assert(
      PluginHost
        .configuredOrder(conf("histmersion.plugins.order = b"), ConfigFactory.empty)
        .left
        .exists(_.sentence.contains("must be a list"))
    )
    assertEquals(PluginHost.configuredOrder(ConfigFactory.empty, ConfigFactory.empty), Right(Nil))

  test("digest is order-independent and changes with any jar"):
    val a = List(PluginJar.File("a.jar") -> Sha256("11"), PluginJar.File("b.jar") -> Sha256("22"))
    assertEquals(PluginHost.digest(a), PluginHost.digest(a.reverse))
    assertNotEquals(
      PluginHost.digest(a),
      PluginHost.digest(List(PluginJar.File("a.jar") -> Sha256("11"), PluginJar.File("b.jar") -> Sha256("23")))
    )
    assertNotEquals(PluginHost.digest(a), PluginHost.digest(a.take(1)))
    assertEquals(PluginHost.digest(Nil).value.length, 64)

  test("decode: kebab-case keys into the plugin's own case class; `enabled` is the host's"):
    val p = EchoPlugin()
    val c = conf("enabled = true\ngreeting = g\nmax-items = \"3\"\nmirrors = []\ntoken = null")
    assertEquals(PluginHost.decode(p, c), Right(EchoConfig("g", 3, Nil, None)))

  test("decode: the derived reader alone names the block, NOT the key, of a wrong type (why typeCheck exists)"):
    val e = PluginHost.decode(EchoPlugin(), conf("greeting = g\nmax-items = lots\nmirrors = []\ntoken = null"))
    assert(e.left.exists(_.sentences.mkString("; ").startsWith("histmersion.plugins.echo")), e)

  test("typeCheck names the key, against the kinds in the plugin's defaults; a null default takes anything"):
    val defs = conf("max-items = 1\nname = n\nmirrors = []\ntoken = null\nnested { depth = 2 }")
    def errs(s: String) = PluginHost.typeCheck(Plugin.Info.Id("test"), conf(s), defs)
    assertEquals(
      errs("histmersion.plugins.test { max-items = \"7\", name = 3, enabled = true, token = \"env:X\" }"),
      Nil
    )
    assertEquals(
      errs("histmersion.plugins.test { max-items = lots }").map(_.sentence),
      List("histmersion.plugins.test.max-items must be a number, not text")
    )
    assertEquals(
      errs("histmersion.plugins.test { mirrors = x }").map(_.sentence),
      List("histmersion.plugins.test.mirrors must be a list [ … ], not text")
    )
    assertEquals(
      errs("histmersion.plugins.test { maxitems = 1 }").map(_.sentence),
      List("histmersion.plugins.test.maxitems is no setting of the plugin: its defaults do not declare it")
    )
    assertEquals(
      errs("histmersion.plugins.test { enabled = maybe }").map(_.sentence),
      List("histmersion.plugins.test.enabled must be true or false, not text")
    )
    assertEquals(
      errs("histmersion.plugins.test.nested.depth = deep").map(_.sentence),
      List("histmersion.plugins.test.nested.depth must be a number, not text")
    )
    assertEquals(
      errs("histmersion.plugins.test.nested.width = 1").map(_.sentence),
      List("histmersion.plugins.test.nested.width is no setting of the plugin: its defaults do not declare it")
    )
    assertEquals(errs("histmersion.plugins.other { x = 1 }"), Nil)

  test("enabled: a boolean or a boolean string; absent is disabled"):
    assertEquals(PluginHost.enabled(conf("enabled = true"), Plugin.Info.Id("x")), Right(true))
    assertEquals(PluginHost.enabled(conf("enabled = \"false\""), Plugin.Info.Id("x")), Right(false))
    assertEquals(PluginHost.enabled(ConfigFactory.empty, Plugin.Info.Id("x")), Right(false))
    assert(PluginHost.enabled(conf("enabled = sometimes"), Plugin.Info.Id("x")).isLeft)

  test("secretsIn walks case classes, options, lists and maps, in order, without repeats"):
    val a = Secret.parse(Secret.ParseText("env:A")).toOption.get
    val b = Secret.parse(Secret.ParseText("v:b")).toOption.get
    val c = Secret.parse(Secret.ParseText("value:c")).toOption.get
    final case class Inner(s: Option[Secret], m: Map[String, Secret])
    final case class Outer(x: Int, first: Secret, inner: Inner, list: List[Secret], none: Option[Secret])
    assertEquals(PluginHost.secretsIn(Outer(1, a, Inner(Some(b), Map("k" -> c)), List(a, b), None)), List(a, b, c))
    assertEquals(PluginHost.secretsIn(EchoConfig("g", 1, Nil, None)), Nil)

  test("settings: the effective block without `enabled`, a literal value: masked, references shown"):
    val s = PluginHost.settings(
      conf(
        "enabled = true\npassword = \"value:hunter2\"\ntoken = \"env:T\"\nkey = null\nn = 3\nl = [a]\nsub { x = \"keepassxc:a#b\" }"
      )
    )
    assertEquals(
      s,
      List(
        PluginSetting(PluginSetting.Key("key"), PluginSetting.Value("null")),
        PluginSetting(PluginSetting.Key("l"), PluginSetting.Value("[\"a\"]")),
        PluginSetting(PluginSetting.Key("n"), PluginSetting.Value("3")),
        PluginSetting(PluginSetting.Key("password"), PluginSetting.Value("value:***")),
        PluginSetting(PluginSetting.Key("sub.x"), PluginSetting.Value("keepassxc:a#b")),
        PluginSetting(PluginSetting.Key("token"), PluginSetting.Value("env:T"))
      )
    )
    assert(!s.toString.contains("hunter2"))
    val nested = PluginHost.settings(conf("tokens = [\"value:abc\", \"env:B\"]\nmap = [{ p = \"value:xyz\" }]"))
    assertEquals(nested.map(_.key), List("map", "tokens"))
    assert(!nested.toString.contains("abc") && !nested.toString.contains("xyz"), nested)
    assert(nested.exists(_.value.value.contains("env:B")), nested)
    assertEquals(
      PluginHost.settings(conf("account = \"\"")),
      List(PluginSetting(PluginSetting.Key("account"), PluginSetting.Value("\"\"")))
    )

  test("resolve: value: is the literal, env: the variable, a vault its answer; errors name the reference only"):
    val vault = new Secret.Vault {
      val name = Secret.Vault.Name("kp");
      def resolve(r: Secret.Vault.ResolveRef) = if r.value == "x" then Right(Secret.Resolved("vx"))
      else Left(Problem.Plugin.Vault(Problem.Plugin.Id("kp"), Problem.Plugin.Retrieval.Locked))
    }
    val e = env("TOKEN" -> "t0k")
    val key = Problem.SettingKey("histmersion.plugins.x")
    assertEquals(PluginHost.resolve(Secret.Ref.Value(Secret.Ref.Value.Literal("lit")), e, Map.empty, key), Right("lit"))
    assertEquals(PluginHost.resolve(Secret.Ref.Env(Secret.Ref.Env.Name("TOKEN")), e, Map.empty, key), Right("t0k"))
    assertEquals(
      PluginHost.resolve(Secret.Ref.Env(Secret.Ref.Env.Name("NOPE")), e, Map.empty, key),
      Left(Problem.Config.Setting(key, Problem.Config.Fault.Unset(Problem.SettingKey("NOPE"))))
    )
    assertEquals(
      PluginHost.resolve(
        Secret.Ref.Vault(Secret.Ref.Vault.Value("kp"), Secret.Ref.Vault.Ref("x")),
        e,
        Map(Secret.Ref.Vault.Value("kp") -> vault),
        key
      ),
      Right("vx")
    )
    assertEquals(
      PluginHost
        .resolve(
          Secret.Ref.Vault(Secret.Ref.Vault.Value("kp"), Secret.Ref.Vault.Ref("y")),
          e,
          Map(Secret.Ref.Vault.Value("kp") -> vault),
          key
        ),
      Left(Problem.Plugin.Vault(Problem.Plugin.Id("kp"), Problem.Plugin.Retrieval.Locked))
    )
    assert(
      PluginHost
        .resolve(
          Secret.Ref.Vault(Secret.Ref.Vault.Value("op"), Secret.Ref.Vault.Ref("y")),
          e,
          Map(Secret.Ref.Vault.Value("kp") -> vault),
          key
        ) == Left(Problem.Plugin.Vault(Problem.Plugin.Id("op"), Problem.Plugin.Retrieval.NoVault))
    )

  // ── a generation ────────────────────────────────────────────────────────────────────────────

  test("a plugin is disabled unless enabled; its settings are still reported"):
    val (g, _) = build(List(EchoPlugin()), "")
    val st = statusOf(g, "echo")
    assertEquals((st.state.value: String, st.enabled), (PluginState.Disabled.wire.value: String, false))
    assert(
      st.settings.contains(
        PluginSetting(PluginSetting.Key("greeting"), PluginSetting.Value("hello from the defaults"))
      ),
      st.settings
    )
    assert(g.report.ok)

  test("an enabled plugin gets its typed config: the user's file over its defaults, an env override over both"):
    val seen = scala.collection.mutable.Buffer.empty[String]
    val (g, _) = build(
      List(EchoPlugin(seen)),
      """histmersion.plugins.echo { enabled = true, greeting = "from the file", max-items = 6, mirrors = [x, y] }""",
      env("HISTMERSION_PLUGIN_ECHO_MAX_ITEMS" -> "9")
    )
    assertEquals(statusOf(g, "echo").state.value: String, PluginState.Registered.wire.value: String)
    assertEquals(seen.toList, List("from the file|9|x,y|None"))

  test("enabled can come from the environment through a defaults line, and as a string"):
    val seen = scala.collection.mutable.Buffer.empty[String]
    val (g, _) = build(List(EchoPlugin(seen)), """histmersion.plugins.echo.enabled = "true"""")
    assertEquals(statusOf(g, "echo").state.value: String, PluginState.Registered.wire.value: String)

  test("a vault plugin's registration is committed and listed; a consumer's secrets resolve through it"):
    val got = scala.collection.mutable.Buffer.empty[String]
    val calls = AtomicInteger()
    val (g, _) = build(
      List(MakerPlugin(calls), ConsumerPlugin(got)),
      """histmersion.plugins {
        |  maker.enabled = true
        |  consumer { enabled = true, token = "fake:a", extra = ["fake:a", "env:E", "fake:b"] }
        |}""".stripMargin,
      env("E" -> "e-val")
    )
    assertEquals(g.registries.secretVaults.keys.toList, List("fake"))
    assertEquals(statusOf(g, "maker").provides, List("secret-vault:fake"))
    assertEquals(statusOf(g, "consumer").state.value: String, PluginState.Registered.wire.value: String)
    assertEquals(got.toList, List("v(a)", "v(a)", "e-val", "v(b)"))
    assertEquals(calls.get, 2, "one vault call per reference per generation (a, b), then the generation's cache")
    // the values never reach the report
    assert(!g.report.toString.contains("v(a)") && !g.report.toString.contains("e-val"), g.report)
    assertEquals(statusOf(g, "consumer").settings.find(_.key.value == "token").map(_.value), Some("fake:a"))

  test("a consumer ordered before its vault waits for it (deferred, then registered)"):
    val got = scala.collection.mutable.Buffer.empty[String]
    // "consumer" sorts before "maker", and order puts it first explicitly too
    val (g, _) = build(
      List(MakerPlugin(), ConsumerPlugin(got)),
      """histmersion.plugins { order = [consumer, maker], maker.enabled = true, consumer { enabled = true, token = "fake:t" } }"""
    )
    assertEquals(statusOf(g, "consumer").state.value: String, PluginState.Registered.wire.value: String)
    assertEquals(got.toList, List("v(t)"))
    assertEquals(g.report.plugins.map(_.id), List("consumer", "maker"), "the report keeps the configured order")

  test("a secret naming a vault nobody registered fails that plugin only, naming the vault"):
    val got = scala.collection.mutable.Buffer.empty[String]
    val (g, logs) = build(
      List(EchoPlugin(), ConsumerPlugin(got)),
      """histmersion.plugins { echo.enabled = true, consumer { enabled = true, token = "keepassxc:x#password" } }"""
    )
    val st = statusOf(g, "consumer")
    assertEquals(st.state.value: String, PluginState.Failed.wire.value: String)
    assertEquals(
      st.errors,
      List(Problem.Plugin.Vault(Problem.Plugin.Id("keepassxc"), Problem.Plugin.Retrieval.NoVault))
    )
    assertEquals(statusOf(g, "echo").state.value: String, PluginState.Registered.wire.value: String)
    assert(!g.report.ok)
    assert(
      logs.lines.exists(_.startsWith("W consumer: secret vault keepassxc failed: no vault of that name is registered")),
      logs.lines
    )
    assertEquals(got.toList, Nil, "register was never called")

  test("an unresolvable secret (unset env, a vault error, a vault that throws) fails registration with the reference"):
    def run(token: String) =
      val (g, _) = build(
        List(MakerPlugin(), ConsumerPlugin(scala.collection.mutable.Buffer.empty)),
        s"""histmersion.plugins { maker.enabled = true, consumer { enabled = true, token = "$token" } }"""
      )
      statusOf(g, "consumer").errors.map(_.sentence)
    assertEquals(
      run("env:CONSUMER_UNSET"),
      List("histmersion.plugins.consumer reads CONSUMER_UNSET, which is not set")
    )
    assertEquals(run("fake:missing").map(_.contains("missing")), List(true))
    assert(
      run("fake:boom").exists(_.startsWith("secret vault fake failed unexpectedly (java.lang.IllegalStateException")),
      run("fake:boom")
    )

  test("value: works, warns, and is masked in the report"):
    val got = scala.collection.mutable.Buffer.empty[String]
    val (g, logs) =
      build(List(ConsumerPlugin(got)), """histmersion.plugins.consumer { enabled = true, token = "value:hunter2" }""")
    val st = statusOf(g, "consumer")
    assertEquals(st.state.value: String, PluginState.Registered.wire.value: String)
    assertEquals(got.toList, List("hunter2"))
    assert(st.warnings.exists(_.value.contains("a secret is written into the config")), st.warnings)
    assert(logs.lines.exists(_.contains("a secret is written into the config")))
    assert(!g.report.toString.contains("hunter2"), g.report)
    assert(!logs.lines.mkString.contains("hunter2"))

  test("staged registries: a plugin whose register fails contributes nothing; one that throws too"):
    val (g, _) = build(
      List(MakerPlugin(), ThrowingPlugin(), EchoPlugin()),
      """histmersion.plugins { maker { enabled = true, fail = "no licence" }, thrower.enabled = true, echo.enabled = true }"""
    )
    assertEquals(
      g.registries.secretVaults,
      scala.collection.immutable.ListMap.empty[String, Secret.Vault],
      "the vault maker added before failing is discarded"
    )
    assertEquals(statusOf(g, "maker").errors.map(_.sentence), List("no licence"))
    assertEquals(statusOf(g, "maker").provides, Nil)
    assertEquals(
      statusOf(g, "thrower").errors.map(_.sentence),
      List("plugin thrower did not load: loading it threw — java.lang.RuntimeException: bang")
    )
    assertEquals(statusOf(g, "echo").state.value: String, PluginState.Registered.wire.value: String)

  test("a plugin whose library is missing fails alone, naming the class and what to do; the others load"):
    val (g, _) = build(
      List(MissingLibraryPlugin(), EchoPlugin()),
      """histmersion.plugins { needs-lib.enabled = true, echo.enabled = true }"""
    )
    val st = statusOf(g, "needs-lib")
    assertEquals(st.state.value: String, PluginState.Failed.wire.value: String)
    assert(
      st.errors.exists(e =>
        e.sentence.contains("class 'org.example.xml.Elem' is missing") && e.sentence.contains("histmersion plugin add")
      ),
      st.errors
    )
    assertEquals(statusOf(g, "echo").state.value: String, PluginState.Registered.wire.value: String)

  test("missingClass: a NoClassDefFoundError or ClassNotFoundException, also as the cause; nothing else"):
    assertEquals(PluginHost.missingClass(NoClassDefFoundError("a/b/C")), Some("a.b.C"))
    assertEquals(PluginHost.missingClass(RuntimeException("x", ClassNotFoundException("a.b.D"))), Some("a.b.D"))
    assertEquals(PluginHost.missingClass(RuntimeException("x")), None)
    assertEquals(
      PluginHost.failure(RuntimeException("bang")),
      Problem.Plugin.Fault.Threw(External.Reported("java.lang.RuntimeException: bang"))
    )

  test("a registry refuses a second vault of the same name, a reserved or mismatched name, and adds after register"):
    val escaped = scala.collection.mutable.Buffer.empty[Plugin.Context]
    val (g, _) = build(
      List(MakerPlugin(id = Plugin.Info.Id("maker"), escape = escaped), MakerPlugin(id = Plugin.Info.Id("other"))),
      """histmersion.plugins { order = [maker, other], maker.enabled = true, other.enabled = true }"""
    )
    // `other` reads maker.conf too, so both register a vault named `fake`
    assertEquals(statusOf(g, "maker").state.value: String, PluginState.Registered.wire.value: String)
    assertEquals(
      statusOf(g, "other").errors,
      List(Problem.Conflict.AlreadyExists(Problem.TypeName.named[Secret.Vault], Problem.Raw("fake")))
    )
    val late =
      escaped.head.secretVaults.add(
        Registry.AddId("late"),
        new Secret.Vault {
          val name = Secret.Vault.Name("late");
          def resolve(r: Secret.Vault.ResolveRef) = Right(Secret.Resolved(""))
        }
      )
    assertEquals(late, Left(Problem.Refused.Policy(Problem.Refused.Rule.RegistrationOver)))
    val (g2, _) = build(List(MakerPlugin()), """histmersion.plugins.maker { enabled = true, vault = env }""")
    assertEquals(
      statusOf(g2, "maker").errors,
      List(Problem.Conflict.AlreadyExists(Problem.TypeName.named[Secret.Vault], Problem.Raw("env")))
    )
    val (g3, _) = build(List(MakerPlugin()), """histmersion.plugins.maker { enabled = true, vault = "Bad_Name" }""")
    assert(
      statusOf(g3, "maker").errors.exists(_.sentence.contains("'Bad_Name' is not a registry name")),
      statusOf(g3, "maker")
    )

  test("a mistyped key fails that plugin with the key named; the others still register"):
    val (g, _) = build(
      List(EchoPlugin(), MakerPlugin()),
      """histmersion.plugins { echo { enabled = true, max-items = many }, maker.enabled = true }"""
    )
    assertEquals(
      statusOf(g, "echo").errors.map(_.sentence),
      List("histmersion.plugins.echo.max-items must be a number, not text")
    )
    assertEquals(statusOf(g, "echo").enabled, true)
    assertEquals(statusOf(g, "maker").state.value: String, PluginState.Registered.wire.value: String)

  test("refused plugins (foreign SPI, duplicate ids) are reported and never registered; config warnings collected"):
    val seen = scala.collection.mutable.Buffer.empty[String]
    val old = EchoPlugin(
      seen,
      Plugin.Info(Plugin.Info.Id("echo"), Plugin.Info.Version("0.0.1"), Plugin.Info.SpiVersion(Spi.Version + 1))
    )
    val (g, logs) = build(
      List(old, MakerPlugin(), MakerPlugin()),
      """histmersion.plugins { order = [ghost], echo.enabled = true, maker.enabled = true, helo.enabled = true }"""
    )
    assertEquals(
      g.report.plugins.map(p => p.id -> p.state),
      List("echo" -> "refused", "maker" -> "refused", "maker" -> "refused")
    )
    assertEquals(seen.toList, Nil)
    assertEquals(g.registries.secretVaults.size, 0)
    assertEquals(
      g.report.warnings,
      List(
        "histmersion.plugins.order names 'ghost', which is no plugin in this binary",
        "histmersion.plugins.helo is no setting: it is ignored"
      )
    )
    assert(logs.lines.exists(_.contains("histmersion.plugins.helo is no setting")))
    assertEquals(g.report.spiVersion, Spi.Version)

  test("a vault is asked ONCE for every reference the generation's plugins hold, before any registers"):
    val batches = scala.collection.mutable.Buffer.empty[List[String]]
    val singles = AtomicInteger()
    val (got1, got2) = (scala.collection.mutable.Buffer.empty[String], scala.collection.mutable.Buffer.empty[String])
    val (g, _) = build(
      List(
        BatchVaultPlugin(batches, singles),
        ConsumerPlugin(got1, Plugin.Info.Id("c1")),
        ConsumerPlugin(got2, Plugin.Info.Id("c2"))
      ),
      """histmersion.plugins {
        |  batcher { enabled = true, vault = "fake" }
        |  c1 { enabled = true, token = "fake:a", extra = ["fake:b", "env:E"] }
        |  c2 { enabled = true, token = "fake:b", extra = ["fake:c"] }
        |}""".stripMargin,
      env("E" -> "e-val")
    )
    assertEquals(statusOf(g, "c1").state.value: String, PluginState.Registered.wire.value: String)
    assertEquals(statusOf(g, "c2").state.value: String, PluginState.Registered.wire.value: String)
    assertEquals(batches.toList, List(List("a", "b", "c")), "one call, every reference of the vault, each once")
    assertEquals(singles.get, 0)
    assertEquals((got1.toList, got2.toList), (List("b(a)", "b(b)", "e-val"), List("b(b)", "b(c)")))

  test(
    "a batch answer: a missing entry fails its plugin alone; an unanswered one is named; a throwing batch is asked one by one"
  ):
    val batches = scala.collection.mutable.Buffer.empty[List[String]]
    val got = scala.collection.mutable.Buffer.empty[String]
    val (g, _) = build(
      List(
        BatchVaultPlugin(batches, AtomicInteger()),
        ConsumerPlugin(got, Plugin.Info.Id("c1")),
        ConsumerPlugin(got, Plugin.Info.Id("c2")),
        ConsumerPlugin(got, Plugin.Info.Id("c3"))
      ),
      """histmersion.plugins {
        |  batcher.enabled = true
        |  c1 { enabled = true, token = "fake:missing" }
        |  c2 { enabled = true, token = "fake:unanswered" }
        |  c3 { enabled = true, token = "fake:ok" }
        |}""".stripMargin
    )
    assertEquals(statusOf(g, "c1").errors.map(_.sentence), List("no entry 'missing'"))
    assertEquals(
      statusOf(g, "c2").errors,
      List(Problem.Plugin.Vault(Problem.Plugin.Id("fake"), Problem.Plugin.Retrieval.Absent(Problem.Raw("unanswered"))))
    )
    assertEquals(statusOf(g, "c3").state.value: String, PluginState.Registered.wire.value: String)
    assertEquals(got.toList, List("b(ok)"))
    assertEquals(batches.size, 1)
    val throwing = new Secret.Vault:
      val name = Secret.Vault.Name("t")
      def resolve(r: Secret.Vault.ResolveRef) = if r.value == "b" then throw new IllegalStateException("kaput")
      else Right(Secret.Resolved(s"one($r)"))
      override def resolveAll(refs: List[Secret.Vault.ResolveRef]) = throw new IllegalStateException("kaput")
    assertEquals(
      PluginHost
        .resolveAll(Secret.Ref.Vault.Value("t"), List(Secret.Ref.Vault.Ref("a"), Secret.Ref.Vault.Ref("b")), throwing)
        .view
        .mapValues(_.left.map(_.sentences.mkString("; ").takeWhile(_ != ',')))
        .toMap,
      Map("a" -> Right("one(a)"), "b" -> Left("secret vault t failed unexpectedly (java.lang.IllegalStateException"))
    )

  test("a vault without resolveAll (an SPI-2 plugin compiled before it) is asked one reference at a time"):
    val default = new Secret.Vault {
      val name = Secret.Vault.Name("d");
      def resolve(r: Secret.Vault.ResolveRef) = if r.value == "x" then Left(Problem.Unclassified(Legacy.Text("locked")))
      else Right(Secret.Resolved(s"v($r)"))
    }
    assertEquals(
      PluginHost
        .resolveAll(
          Secret.Ref.Vault.Value("d"),
          List(Secret.Ref.Vault.Ref("a"), Secret.Ref.Vault.Ref("x"), Secret.Ref.Vault.Ref("a")),
          default
        )
        .view
        .mapValues(_.left.map(_.sentences.mkString("; ").takeWhile(_ != ',')))
        .toMap,
      Map("a" -> Right("v(a)"), "x" -> Left("locked"))
    )

  test("builds are independent: no state survives from one generation to the next"):
    val calls = AtomicInteger()
    val got = scala.collection.mutable.Buffer.empty[String]
    val plugins = List(MakerPlugin(calls), ConsumerPlugin(got))
    val user = """histmersion.plugins { maker.enabled = true, consumer { enabled = true, token = "fake:a" } }"""
    val (g1, _) = build(plugins, user)
    val (g2, _) = build(plugins, user)
    assertEquals(calls.get, 2, "the secret is resolved again for the second generation (no process-wide cache)")
    assert(
      g1.registries.secretVaults(Registries.SecretVaultsKey("fake")) ne g2.registries.secretVaults(
        Registries.SecretVaultsKey("fake")
      )
    )
    val (off, _) = build(plugins, "")
    assertEquals(off.registries.secretVaults.size, 0)

  // ── the real thing: the built-in vaults on the server's class path ──────────────────────────

  /** The vault plugins among the first-party ones on the server's class path. */
  private def vaults(found: List[Plugin]): List[Plugin] =
    found.filter(p => Set("1password", "bitwarden", "keepassxc")(p.info.id.value))

  test("discovery finds the built-in vault plugins; by default all are disabled and the report is ok"):
    val (found, errs) = PluginHost.discover(getClass.getClassLoader)
    assertEquals(errs, Nil)
    assertEquals(vaults(found).map(_.info.id.value).sorted, List("1password", "bitwarden", "keepassxc"))
    assert(
      found.map(_.info.id).forall(id => ReservedNames.firstPartyPlugins.contains(ReservedNames.Name(id.value))),
      found.map(_.info.id).toString
    )
    val (g, _) = build(vaults(found), "")
    assertEquals(
      g.report.plugins.map(p => p.id -> p.state),
      List("1password" -> "disabled", "bitwarden" -> "disabled", "keepassxc" -> "disabled")
    )
    assert(g.report.ok)

  test("a key starting with a digit is a plain HOCON key: histmersion.plugins.1password"):
    val (found, _) = PluginHost.discover(getClass.getClassLoader)
    val (g, _) = build(
      found,
      """histmersion.plugins.1password { enabled = true, cli = "/nonexistent/op" }
                                |histmersion.plugins { bitwarden { timeout-seconds = "5" } }""".stripMargin
    )
    assertEquals(statusOf(g, "1password").errors.map(_.sentence), List("/nonexistent/op is not executable"))
    assert(
      statusOf(g, "bitwarden").settings.contains(
        PluginSetting(PluginSetting.Key("timeout-seconds"), PluginSetting.Value("5"))
      )
    )

  test("end to end: bitwarden's session comes from the keepassxc vault (a fake keepassxc-cli on a temp PATH)"):
    val d = Files.createTempDirectory("histmersion-fake-bin")
    val cli = d.resolve("keepassxc-cli")
    Files.writeString(
      cli,
      "#!/bin/sh\nIFS= read -r pw\n[ \"$pw\" = \"kp-master\" ] || { echo 'Invalid credentials' >&2; exit 1; }\n" +
        "for a; do e=$a; done\nprintf 'session-from-%s\\n' \"$e\"\n"
    )
    Files.setPosixFilePermissions(cli, PosixFilePermissions.fromString("rwx------"))
    Files.writeString(d.resolve("bw"), "#!/bin/sh\nexit 0\n")
    Files.setPosixFilePermissions(d.resolve("bw"), PosixFilePermissions.fromString("rwx------"))
    val db = Files.writeString(d.resolve("v.kdbx"), "")
    val (found, _) = PluginHost.discover(getClass.getClassLoader)
    val e = env("PATH" -> d.toString, "KP_PASSWORD" -> "kp-master")
    val (g, logs) = build(
      vaults(found),
      s"""histmersion.plugins {
         |  keepassxc { enabled = true, database = "$db", password = "env:KP_PASSWORD" }
         |  bitwarden { enabled = true, session = "keepassxc:Histmersion/bw#password" }
         |}""".stripMargin,
      e
    )
    assertEquals(
      g.report.plugins.map(p => p.id -> p.state),
      List("1password" -> "disabled", "bitwarden" -> "registered", "keepassxc" -> "registered")
    )
    assertEquals(
      g.registries.secretVaults.keys.toList,
      List("keepassxc", "bitwarden"),
      "bitwarden (sorted first) waited for keepassxc"
    )
    assertEquals(
      g.registries.secretVaults(Registries.SecretVaultsKey("keepassxc")).resolve(Secret.Vault.ResolveRef("x")),
      Right("session-from-x")
    )
    val shown = g.report.toString + logs.lines.mkString
    assert(!shown.contains("session-from") && !shown.contains("kp-master"), shown)
    assert(
      statusOf(g, "bitwarden").settings.contains(
        PluginSetting(PluginSetting.Key("session"), PluginSetting.Value("keepassxc:Histmersion/bw#password"))
      )
    )

  test("end to end: four keepassxc references of three plugins are read in ONE keepassxc-cli open"):
    val d = Files.createTempDirectory("histmersion-fake-bin")
    val cli = d.resolve("keepassxc-cli")
    Files.writeString(
      cli,
      "#!/bin/sh\necho \"$1\" >> \"$(dirname \"$0\")/kp-calls\"\n" +
        "IFS= read -r pw\n[ \"$pw\" = \"kp-master\" ] || { echo 'Invalid credentials' >&2; exit 1; }\n" +
        "[ \"$1\" = open ] || { for a; do e=$a; done; printf 'value-of-%s\\n' \"$e\"; exit 0; }\n" +
        "printf 'v.kdbx> '\nwhile IFS= read -r line; do\n  printf '%s\\n' \"$line\"\n  [ \"$line\" = exit ] && exit 0\n" +
        "  printf 'value-of-%s\\n' \"${line##* }\"\n  printf 'v.kdbx> '\ndone\n"
    )
    Files.setPosixFilePermissions(cli, PosixFilePermissions.fromString("rwx------"))
    Files.writeString(d.resolve("bw"), "#!/bin/sh\nexit 0\n")
    Files.setPosixFilePermissions(d.resolve("bw"), PosixFilePermissions.fromString("rwx------"))
    val db = Files.writeString(d.resolve("v.kdbx"), "")
    val got = scala.collection.mutable.Buffer.empty[String]
    val (found, _) = PluginHost.discover(getClass.getClassLoader)
    val (g, logs) = build(
      found ++ List(ConsumerPlugin(got, Plugin.Info.Id("c1")), ConsumerPlugin(got, Plugin.Info.Id("c2"))),
      s"""histmersion.plugins {
         |  keepassxc { enabled = true, database = "$db", password = "env:KP_PASSWORD" }
         |  bitwarden { enabled = true, session = "keepassxc:Histmersion/bw" }
         |  c1 { enabled = true, token = "keepassxc:Histmersion/CORE", extra = ["keepassxc:Histmersion/Europeana"] }
         |  c2 { enabled = true, token = "keepassxc:Histmersion/api.data.gov" }
         |}""".stripMargin,
      env("PATH" -> d.toString, "KP_PASSWORD" -> "kp-master")
    )
    assertEquals(
      List("bitwarden", "c1", "c2", "keepassxc").map(statusOf(g, _).state.value: String),
      List.fill(4)(PluginState.Registered.wire.value: String)
    )
    assertEquals(Files.readAllLines(d.resolve("kp-calls")).asScala.toList, List("open"), "one unlock for all four")
    assertEquals(
      got.toList.sorted,
      List("value-of-Histmersion/CORE", "value-of-Histmersion/Europeana", "value-of-Histmersion/api.data.gov")
    )
    assert(!(g.report.toString + logs.lines.mkString).contains("value-of"))

  // ── a config reload: only the plugins whose block changed (docs/research-plugins.md §3.5) ────────

  test("reload: an unchanged plugin is carried over, a changed one registered again"):
    val seen = scala.collection.mutable.Buffer.empty[String]
    val calls = AtomicInteger()
    val makerUp = scala.collection.mutable.Buffer.empty[Plugin.Context]
    def plugins = List(
      EchoPlugin(seen),
      MakerPlugin(calls, escape = makerUp),
      ConsumerPlugin(scala.collection.mutable.Buffer.empty)
    )
    val before = "histmersion.plugins { echo { enabled = true, greeting = hi }, maker.enabled = true }"
    val (g1, _) = build(plugins, before)
    assertEquals(g1.rebuilt.toSet, Set("echo", "maker", "consumer"))
    assertEquals(makerUp.size, 1)
    // only echo's block changed: maker (a vault, unchanged) and consumer (disabled) are carried
    val changed = List(ConfigReload.Key("histmersion.plugins.echo.greeting"))
    val carried = Plugins.carried(g1, changed)
    assertEquals(carried.keySet, Set("maker", "consumer"))
    val (l, s) = services()
    val g2 = PluginHost.build(
      plugins,
      conf("histmersion.plugins { echo { enabled = true, greeting = hey }, maker.enabled = true }"),
      ConfigFactory.empty,
      s,
      carried = carried
    )
    assertEquals(g2.rebuilt, List("echo"))
    assertEquals(makerUp.size, 1, "maker was not registered again")
    assertEquals(seen.toList.map(_.takeWhile(_ != '|')), List("hi", "hey"))
    assertEquals(g2.registries.secretVaults.keySet, Set("fake"), "the carried vault is still registered")
    assertEquals(statusOf(g2, "maker").state, "registered")
    assertEquals(statusOf(g2, "consumer").state, "disabled")
    assertEquals(g2.report.plugins.map(_.id), g1.report.plugins.map(_.id), "the order is kept")

  test("reload: a vault plugin changed, `order` changed or a host key changed — every plugin is registered again"):
    val (g1, _) =
      build(List(EchoPlugin(), MakerPlugin()), "histmersion.plugins { echo.enabled = true, maker.enabled = true }")
    assertEquals(Plugins.carried(g1, List(ConfigReload.Key("histmersion.plugins.maker.vault"))), Map.empty)
    assertEquals(Plugins.carried(g1, List(ConfigReload.Key("histmersion.plugins.order"))), Map.empty)
    assertEquals(Plugins.carried(g1, List(ConfigReload.Key("histmersion.acquire.chain"))), Map.empty)
    assertEquals(Plugins.carried(g1, List(ConfigReload.Key("histmersion.plugins"))), Map.empty)
    // keys no plugin reads do not count
    assertEquals(
      Plugins.carried(g1, List(ConfigReload.Key("histmersion.limits.search"))).keySet,
      Set("echo", "maker")
    )

  test(
    "reload: a plugin holding a secret is never carried (a rotated password needs only a reload); a failed one is retried"
  ):
    val user =
      "histmersion.plugins { maker.enabled = true, consumer { enabled = true, token = \"fake:t\" }, echo { enabled = true, max-items = many } }"
    val (g1, _) = build(List(MakerPlugin(), ConsumerPlugin(scala.collection.mutable.Buffer.empty), EchoPlugin()), user)
    assertEquals(statusOf(g1, "consumer").state, "registered")
    assertEquals(statusOf(g1, "echo").state, "failed")
    assertEquals(g1.withSecrets, Set("consumer"))
    assertEquals(Plugins.carried(g1, List(ConfigReload.Key("histmersion.plugins.other.x"))).keySet, Set("maker"))

  test("pluginId: the plugin a key belongs to, quoted ids too; None for order and the block"):
    assertEquals(Plugins.pluginId(ConfigReload.Key("histmersion.plugins.pmc.enabled")), Some("pmc"))
    assertEquals(
      Plugins.pluginId(ConfigReload.Key("histmersion.plugins.\"1password\".enabled")),
      Some("1password")
    )
    assertEquals(Plugins.pluginId(ConfigReload.Key("histmersion.plugins.order")), None)
    assertEquals(Plugins.pluginId(ConfigReload.Key("histmersion.plugins")), None)
    assertEquals(Plugins.pluginId(ConfigReload.Key("histmersion.acquire.chain")), None)
