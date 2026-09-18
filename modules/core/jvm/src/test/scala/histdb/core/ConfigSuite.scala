package histdb.core

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
      java.util.Map.of("HISTDB_PORT", "8011", "HISTDB_SERVER_URL", "http://h:1"))
    val cfg = AppConfig.load(env)
    assertEquals(cfg.server.port, 8011)
    assertEquals(cfg.cli.serverUrl, "http://h:1")

  test("non-numeric env value is a clear error"):
    val env = org.ekrich.config.ConfigFactory.parseMap(java.util.Map.of("HISTDB_PORT", "eighty"))
    val e = intercept[RuntimeException](AppConfig.load(env))
    assert(e.getMessage.contains("'eighty' is not a int"), e.getMessage)

  /** Guard for plan §1.9: no port numbers / service URLs hardcoded anywhere in main sources. */
  test("no hardcoded ports or CDN hosts in src/main (only reference.conf may carry them)"):
    val root = Path.of(sys.props.getOrElse("histdb.repoRoot", "."))
    val offenders = Files.walk(root.resolve("modules")).iterator.asScala
      .filter(p => p.toString.contains("/src/main/") && !p.toString.contains("/vendor/"))
      .filter(p => p.toString.matches(""".*\.(scala|js|html|css)$"""))
      .filter(p => Files.readString(p).matches("""(?s).*(\b(8000|8100|5432)\b|unpkg\.com|localhost:\d+).*"""))
      .map(root.relativize).toList
    assertEquals(offenders, Nil, "move these constants to reference.conf")
