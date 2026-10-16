package com.histmersion.core

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

class ConfigSuite extends munit.FunSuite:
  test("reference.conf loads with defaults"):
    val cfg = AppConfig.load()
    assert(cfg.server.port > 0)
    assert(cfg.db.url.startsWith("jdbc:postgresql://"))
    assert(cfg.ui.time.min < cfg.ui.time.defaultFrom)
    assert(cfg.limits.search > 0)

  test("env overrides (always strings) are accepted for numeric keys"):
    val env = org.ekrich.config.ConfigFactory.parseMap(
      java.util.Map.of("HISTMERSION_PORT", "8011", "HISTMERSION_SERVER_URL", "http://h:1"))
    val cfg = AppConfig.load(env)
    assertEquals(cfg.server.port, 8011)
    assertEquals(cfg.cli.serverUrl, "http://h:1")

  test("non-numeric env value is a clear error"):
    val env = org.ekrich.config.ConfigFactory.parseMap(java.util.Map.of("HISTMERSION_PORT", "eighty"))
    val e = intercept[RuntimeException](AppConfig.load(env))
    assert(e.getMessage.contains("'eighty' is not a int"), e.getMessage)

  // ── the user config file: env > file > reference.conf ────────────────────────────────────────

  private def env(kv: (String, String)*): org.ekrich.config.Config =
    org.ekrich.config.ConfigFactory.parseMap(kv.toMap.asJava)

  private def confFile(text: String): Path =
    val d = Files.createTempDirectory("histmersion-conf")
    Files.writeString(d.resolve("histmersion.conf"), text)

  private def loaded(e: org.ekrich.config.Config, file: Path): LoadedConfig =
    AppConfig.loadWithUserFile(e, Some(file)).fold(m => fail(m), identity)

  test("user file: the environment beats the file, the file beats reference.conf (a key with an env mapping)"):
    val f = confFile("histmersion { server { port = 8123 }, agents.step.max-tokens = 999 }")
    val fileOnly = loaded(env(), f).config
    assertEquals(fileOnly.server.port, 8123, "the file beats reference.conf")
    assertEquals(fileOnly.agents.step.maxTokens, 999)
    val both = loaded(env("HISTMERSION_PORT" -> "8011", "HISTMERSION_AGENTS_MAX_TOKENS" -> "77"), f).config
    assertEquals(both.server.port, 8011, "the env beats the file even though the file sets the key")
    assertEquals(both.agents.step.maxTokens, 77)
    assertEquals(AppConfig.load().server.port, 8000, "reference.conf alone is unchanged")

  test("user file: a key with no env mapping is taken from the file; other keys keep their env overrides"):
    val f = confFile("""histmersion.converter.health-cache-ms = 1234
                       |histmersion.lint.max-items = 42
                       |""".stripMargin)
    val c = loaded(env("HISTMERSION_DB_URL" -> "jdbc:postgresql://h:1/x"), f).config
    assertEquals(c.converter.healthCacheMs, 1234)
    assertEquals(c.lint.maxItems, 42)
    assertEquals(c.db.url, "jdbc:postgresql://h:1/x", "a key the file does not touch still takes its env var")
    assertEquals(c.server.port, 8000)

  test("user file: substitutions in the file read the environment"):
    val f = confFile("histmersion.corpus.dir = ${?MY_CORPUS}")
    assertEquals(loaded(env("MY_CORPUS" -> "/srv/c"), f).config.corpus.dir, "/srv/c")

  test("user file: a missing file means the defaults, and says so"):
    val missing = Files.createTempDirectory("histmersion-conf").resolve("histmersion.conf")
    val l = loaded(env(), missing)
    assertEquals(l.config, AppConfig.load(env()))
    assertEquals(l.file, UserConfigFile(Some(missing), loaded = false))
    assertEquals(l.file.info.loaded, false)

  test("user file: one that does not parse stops the load with an error naming the file"):
    val f = confFile("histmersion { server { port = 8123 \n")
    val e = AppConfig.loadWithUserFile(env(), Some(f)).swap.getOrElse(fail("a broken file loaded"))
    assert(e.startsWith(s"Histmersion config file $f: "), e)
    assert(!e.stripPrefix(s"Histmersion config file $f: ").startsWith(f.toString), s"the file named once: $e")

  test("user file: a value of the wrong type is an error naming the file and the key"):
    val f = confFile("histmersion.server.port = eighty")
    val e = AppConfig.loadWithUserFile(env(), Some(f)).swap.getOrElse(fail("a mistyped file loaded"))
    assert(e.contains(f.toString), e)
    assert(e.contains("port"), e)
    assert(e.contains("'histmersion.server.port' must be a number"), e)
    val shadowed = AppConfig.loadWithUserFile(env("HISTMERSION_PORT" -> "8011"), Some(f))
    assert(shadowed.swap.exists(_.contains("'histmersion.server.port' must be a number")),
      s"a mistyped setting is an error even while an env var shadows it: $shadowed")
    val nested = confFile("histmersion { agents { scheduler { enabled = sometimes } } }")
    val e2 = AppConfig.loadWithUserFile(env(), Some(nested)).swap.getOrElse(fail("loaded"))
    assert(e2.contains(nested.toString) && e2.contains("'histmersion.agents.scheduler.enabled' must be true or false"), e2)

  test("user file: env-style strings and plain numbers are accepted where the readers accept them"):
    val f = confFile("""histmersion.server.port = "8125"
                       |histmersion.agents.enabled = "true"
                       |histmersion.periods.periodo-collections = 5
                       |""".stripMargin)
    val c = loaded(env(), f).config
    assertEquals(c.server.port, 8125)
    assertEquals(c.agents.enabled, true)
    assertEquals(c.periods.periodoCollections, "5")

  test("user file: a single value where a block belongs is an error, not a silent wipe of the block"):
    val f = confFile("histmersion.agents = true")
    val e = AppConfig.loadWithUserFile(env(), Some(f)).swap.getOrElse(fail("loaded"))
    assert(e.contains(f.toString) && e.contains("histmersion.agents"), e)

  test("user file: unknown keys are reported as warnings, the rest still applies"):
    val f = confFile("""histmersion.agents.step.max-token = 5
                       |histmersion.server.port = 8124
                       |server.port = 1
                       |""".stripMargin)
    val l = loaded(env(), f)
    assertEquals(l.config.server.port, 8124)
    assertEquals(l.file.unknownKeys, List("histmersion.agents.step.max-token", "server.port"))
    val w = l.file.warnings
    assertEquals(w.size, 2)
    assert(w.head.contains(f.toString) && w.head.contains("histmersion.agents.step.max-token"), w.head)
    assert(w(1).contains("settings go under histmersion"), w(1))
    assertEquals(l.file.info, ConfigFileInfo(Some(f.toString), loaded = true, l.file.unknownKeys))

  test("claude-code env: the defaults switch features off; nothing about thinking, structured outputs or web fetch"):
    val cc = AppConfig.load(env()).agents.providers.claudeCode
    val set = cc.envChanges.collect { case (k, Some(v)) => k -> v }.toMap
    assertEquals(set.get("CLAUDE_CODE_DISABLE_ARTIFACT"), Some("1"))
    assert(set.keySet.forall(_.startsWith("CLAUDE_CODE_DISABLE_")) && set.values.forall(_ == "1"), set)
    List("THINKING", "ADAPTIVE_THINKING", "STRUCTURED_OUTPUTS", "WEB_FETCH", "REFUSAL_FALLBACK", "REFUSAL_RETRY",
      "MODEL_ACCESS_FALLBACK", "NONSTREAMING_FALLBACK", "NONESSENTIAL_TRAFFIC", "ATTACHMENTS")
      .foreach(n => assert(!set.contains(s"CLAUDE_CODE_DISABLE_$n"), n))
    assert(cc.disableSlashCommands)

  test("claude-code env: the file overrides a default, \"\" and null remove one, a new name is no unknown setting"):
    val f = confFile("""histmersion.agents.providers.claude-code {
                       |  disable-slash-commands = false
                       |  env { CLAUDE_CODE_DISABLE_CRON = "", CLAUDE_CODE_DISABLE_WORKFLOWS = null, MY_FLAG = "x", CLAUDE_CODE_DISABLE_ARTIFACT = "true" }
                       |}""".stripMargin)
    val l = loaded(env(), f)
    assertEquals(l.file.unknownKeys, Nil)
    val ch = l.config.agents.providers.claudeCode.envChanges.toMap
    assertEquals(ch.get("CLAUDE_CODE_DISABLE_CRON"), Some(None))
    assertEquals(ch.get("CLAUDE_CODE_DISABLE_WORKFLOWS"), Some(None))
    assertEquals(ch.get("MY_FLAG"), Some(Some("x")))
    assertEquals(ch.get("CLAUDE_CODE_DISABLE_ARTIFACT"), Some(Some("true")))
    assertEquals(ch.get("CLAUDE_CODE_DISABLE_BUNDLED_SKILLS"), Some(Some("1")), "the other defaults stay")
    assert(!l.config.agents.providers.claudeCode.disableSlashCommands)

  test("claude-code env: a variable in the server's own environment wins over the file and the default"):
    val f = confFile("""histmersion.agents.providers.claude-code.env { CLAUDE_CODE_DISABLE_CRON = "" }""")
    val l = loaded(env("CLAUDE_CODE_DISABLE_CRON" -> "0", "CLAUDE_CODE_DISABLE_ARTIFACT" -> "",
      "HISTMERSION_AGENTS_CLAUDE_DISABLE_SLASH_COMMANDS" -> "false"), f)
    val ch = l.config.agents.providers.claudeCode.envChanges.toMap
    assertEquals(ch.get("CLAUDE_CODE_DISABLE_CRON"), Some(Some("0")))
    assertEquals(ch.get("CLAUDE_CODE_DISABLE_ARTIFACT"), Some(None))
    assert(!l.config.agents.providers.claudeCode.disableSlashCommands)

  test("user file: no location at all (HOME unset) is the defaults"):
    val l = AppConfig.loadWithUserFile(env(), None).fold(m => fail(m), identity)
    assertEquals(l.file, UserConfigFile(None, loaded = false))

  test("the installer's template reads as a file with no settings"):
    val root = Path.of(sys.props.getOrElse("histmersion.repoRoot", "."))
    val t = root.resolve("scripts/bundle/histmersion.conf.template")
    val l = loaded(env(), t)
    assertEquals(l.file, UserConfigFile(Some(t), loaded = true))
    assertEquals(l.config, AppConfig.load(env()))
    // its commented example, uncommented, is all real settings
    val example = Files.readAllLines(t).asScala.dropWhile(!_.startsWith("# histmersion {"))
      .map(_.stripPrefix("#")).mkString("\n")
    val ex = loaded(env(), confFile(example))
    assertEquals(ex.file.unknownKeys, Nil)
    assertEquals(ex.config.agents.step.maxTokens, 8192)

  test("the env-override layer holds exactly the keys whose ${?VAR} is set"):
    val layer = ConfigLayers.envOverrides(ConfigLayers.reference,
      env("HISTMERSION_PORT" -> "9", "ANTHROPIC_API_KEY" -> "k", "UNRELATED" -> "x"))
    assertEquals(layer.entrySet.asScala.map(_.getKey).toSet,
      Set("histmersion.server.port", "histmersion.agents.providers.anthropic.api-key"))
    assertEquals(layer.getString("histmersion.server.port"), "9")

  test("the config file location: HISTMERSION_CONFIG, else XDG_CONFIG_HOME (default ~/.config) on every platform"):
    def e(kv: (String, String)*): String => Option[String] = kv.toMap.get
    assertEquals(UserPaths.configFile(e("HOME" -> "/Users/u")),
      Some(Path.of("/Users/u/.config/histmersion/histmersion.conf")))
    assertEquals(UserPaths.configFile(e("HOME" -> "/Users/u", "HISTMERSION_DATA_DIR" -> "/d")),
      Some(Path.of("/Users/u/.config/histmersion/histmersion.conf")), "the data dir never moves it")
    assertEquals(UserPaths.configFile(e("HOME" -> "/Users/u", "HISTMERSION_CONFIG" -> "~/h.conf")),
      Some(Path.of("/Users/u/h.conf")))
    assertEquals(UserPaths.configFile(e("HOME" -> "/home/u", "XDG_CONFIG_HOME" -> "/x")),
      Some(Path.of("/x/histmersion/histmersion.conf")))
    assertEquals(UserPaths.configFile(e()), None)
    assertEquals(UserPaths.dataDir("", e("HOME" -> "/home/u", "XDG_DATA_HOME" -> "/xd"), "Linux"),
      Some(Path.of("/xd/histmersion")))
    assertEquals(UserPaths.dataDir("/own", e("HOME" -> "/home/u"), "Linux"), Some(Path.of("/own")))

  test("About renders the config file line"):
    assertEquals(ConfigFileInfo.describe(ConfigFileInfo(Some("/c/h.conf"), loaded = true)), "/c/h.conf (read)")
    assertEquals(ConfigFileInfo.describe(ConfigFileInfo(Some("/c/h.conf"))),
      "/c/h.conf (not found: defaults and environment only)")
    assertEquals(ConfigFileInfo.describe(ConfigFileInfo(Some("/c/h.conf"), loaded = true, List("histmersion.x"))),
      "/c/h.conf (read; ignored unknown key: histmersion.x)")
    assertEquals(ConfigFileInfo.describe(ConfigFileInfo()), "none (no location: HOME is unset)")

  test("the map's geo feed has its own cap, above a listing page: a range draws every state in it"):
    val c = AppConfig.load()
    assertEquals(c.places.geoMaxLimit, 2000)
    assert(c.places.geoMaxLimit > c.limits.maxPage)
    val p = c.places.copy(geoMaxLimit = 2000)
    assertEquals(p.geoLimit(Some(400), 100), 400)
    assertEquals(p.geoLimit(Some(1744), 100), 1744)   // past limits.max-page
    assertEquals(p.geoLimit(Some(5000), 100), 2000)
    assertEquals(p.geoLimit(None, 100), 100)
    assertEquals(p.geoLimit(Some(0), 100), 100)
    assertEquals(p.geoLimit(Some(-3), 100), 100)
    assertEquals(c.places.copy(geoMaxLimit = 0).geoLimit(Some(50), 100), 1)

  /** Guard for plan §1.9: no port numbers / service URLs hardcoded anywhere in main sources. */
  test("no hardcoded ports or CDN hosts in src/main (only reference.conf may carry them)"):
    val root = Path.of(sys.props.getOrElse("histmersion.repoRoot", "."))
    val offenders = Files.walk(root.resolve("modules")).iterator.asScala
      .filter(p => p.toString.contains("/src/main/") && !p.toString.contains("/vendor/"))
      .filter(p => p.toString.matches(""".*\.(scala|js|html|css)$"""))
      .filter(p => Files.readString(p).matches("""(?s).*(\b(8000|8100|5432)\b|unpkg\.com|localhost:\d+).*"""))
      .map(root.relativize).toList
    assertEquals(offenders, Nil, "move these constants to reference.conf")
