package com.histmersion.spike.host

import com.histmersion.plugin.*
import org.ekrich.config.ConfigFactory

class PluginHostSuite extends munit.FunSuite:
  private def conf(s: String) = ConfigFactory.parseString(s)

  test("block: the user's keys over the plugin's defaults, env substitutions resolved") {
    val user = conf("histmersion.plugins.x { a = 2 }")
    val defs = conf("a = 1\nb = 1\nb = ${?X_B}")
    val env  = conf("X_B = 7")
    val b    = PluginHost.block(user, defs, env, "x").toOption.get
    assertEquals((b.getInt("a"), b.getInt("b")), (2, 7))
  }

  test("block: no user block → the defaults; an unresolvable substitution is an error naming the block") {
    assertEquals(PluginHost.block(ConfigFactory.empty, conf("a = 1"), ConfigFactory.empty, "x").map(_.getInt("a")), Right(1))
    assert(PluginHost.block(ConfigFactory.empty, conf("a = ${NOPE}"), ConfigFactory.empty, "x").left.exists(_.startsWith("histmersion.plugins.x")))
  }

  test("unknownBlocks lists the blocks no plugin claims") {
    val user = conf("histmersion.plugins { hello { enabled = true }, helo { enabled = true } }")
    assertEquals(PluginHost.unknownBlocks(user, Set("hello")), List("helo"))
    assertEquals(PluginHost.unknownBlocks(ConfigFactory.empty, Set("hello")), Nil)
  }

  test("check refuses a foreign SPI version, a duplicate id and a non-kebab id") {
    val ok = PluginInfo("a", PluginKind.SourceFetcher, "1", 1)
    assertEquals(PluginHost.check(List(ok), 1), Nil)
    val errs = PluginHost.check(List(ok, ok, ok.copy(id = "B_x"), ok.copy(id = "c", spiVersion = 2)), 1)
    assertEquals(errs.size, 3)
    assert(errs.exists(_.contains("claimed 2 times")))
    assert(errs.exists(_.contains("SPI 2")))
    assert(errs.exists(_.contains("kebab-case")))
  }

  test("digest is order-independent and changes with any jar") {
    val a = List("a.jar" -> "11", "b.jar" -> "22")
    assertEquals(PluginHost.digest(a), PluginHost.digest(a.reverse))
    assertNotEquals(PluginHost.digest(a), PluginHost.digest(List("a.jar" -> "11", "b.jar" -> "23")))
    assertNotEquals(PluginHost.digest(a), PluginHost.digest(a.take(1)))
  }

  test("ServiceLoader on the host's own classpath finds no plugin (hello is not a dependency)") {
    val user = conf("histmersion.plugins.hello { enabled = true }")
    val host = new HostServices:
      val http = new PluginHttp { def get(u: String, h: Map[String, String]) = Left("none") }
      def log(m: String) = ()
    assertEquals(PluginHost.load(user, ConfigFactory.empty, host).map(_.size), Right(0))
  }

final case class TestConf(maxItems: Int, name: String) derives hearth.kindlings.sconfigderivation.ConfigReader

final class TestPlugin extends SourceFetcherPlugin[TestConf]:
  val info = PluginInfo("test", PluginKind.SourceFetcher, "0", Spi.Version)
  def create(c: TestConf, h: HostServices) = Left("not started in tests")

class PluginDecodeSuite extends munit.FunSuite:
  test("decode: kebab-case keys into the plugin's own case class; `enabled` is the host's") {
    val c = ConfigFactory.parseString("enabled = true\nmax-items = 3\nname = n")
    val p = TestPlugin()
    assertEquals(PluginHost.decode(p, c), Right(TestConf(3, "n")))
  }

  test("decode: the derived reader alone names the block, NOT the key, of a wrong type") {
    val e = PluginHost.decode(TestPlugin(), ConfigFactory.parseString("max-items = lots\nname = n"))
    assertEquals(e, Left("histmersion.plugins.test: Expected NUMBER but got STRING"))
  }

  test("typeCheck names the key, against the kinds in the plugin's defaults") {
    val defs = ConfigFactory.parseString("max-items = 1\nname = n\nmirrors = []")
    def errs(s: String) = PluginHost.typeCheck("test", ConfigFactory.parseString(s), defs)
    assertEquals(errs("histmersion.plugins.test { max-items = \"7\", name = 3, enabled = true }"), Nil)
    assertEquals(errs("histmersion.plugins.test { max-items = lots }"),
      List("histmersion.plugins.test.max-items must be a number, not \"lots\""))
    assertEquals(errs("histmersion.plugins.test { mirrors = x }"),
      List("histmersion.plugins.test.mirrors must be a list [ … ], not \"x\""))
    assertEquals(errs("histmersion.plugins.test { maxitems = 1 }"),
      List("histmersion.plugins.test.maxitems: unknown setting (not in the plugin's defaults)"))
    assertEquals(errs("histmersion.plugins.test { enabled = maybe }"), List("histmersion.plugins.test.enabled must be true or false"))
  }
