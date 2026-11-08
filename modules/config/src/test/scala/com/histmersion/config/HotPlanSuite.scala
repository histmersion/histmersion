package com.histmersion
package config

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

import com.histmersion.config.testsupport.ComparedByValue.given

/** The pure half of the hot component graph (docs/research-plugins.md §3.6) and of a config reload (§3.5): order,
  * cycles, what a set of changed keys rebuilds; which keys changed, which apply in place and which wait for a restart.
  */
class HotPlanSuite extends munit.FunSuite:

  private val nodes = List(
    HotNode(HotNode.Name("pool"), Nil, List(HotNode.Keys("histmersion.db"))),
    HotNode(HotNode.Name("migrations"), List(HotNode.Deps("pool")), Nil),
    HotNode(
      HotNode.Name("query-pool"),
      List(HotNode.Deps("pool"), HotNode.Deps("migrations")),
      List(HotNode.Keys("histmersion.query"), HotNode.Keys("histmersion.db"))
    ),
    HotNode(HotNode.Name("lease"), List(HotNode.Deps("migrations")), Nil),
    HotNode(
      HotNode.Name("plugins"),
      Nil,
      List(HotNode.Keys("histmersion.plugins"), HotNode.Keys("histmersion.acquire"))
    ),
    HotNode(HotNode.Name("acquisition"), List(HotNode.Deps("plugins")), List(HotNode.Keys("histmersion.acquire"))),
    HotNode(HotNode.Name("watch"), Nil, List(HotNode.Keys("histmersion.config")))
  )

  test("order: dependencies first, declaration order among independent nodes"):
    assertEquals(
      HotPlan.order(nodes),
      Right(List("pool", "migrations", "query-pool", "lease", "plugins", "acquisition", "watch"))
    )
    // declared out of order: still built after what it needs
    val shuffled = List(
      HotNode(HotNode.Name("b"), List(HotNode.Deps("a")), Nil),
      HotNode(HotNode.Name("c"), Nil, Nil),
      HotNode(HotNode.Name("a"), Nil, Nil)
    )
    assertEquals(HotPlan.order(shuffled), Right(List("c", "a", "b")))

  test("order refuses a cycle, naming it; a dependency on no node; a name used twice"):
    val cyc = List(
      HotNode(HotNode.Name("a"), List(HotNode.Deps("c")), Nil),
      HotNode(HotNode.Name("b"), List(HotNode.Deps("a")), Nil),
      HotNode(HotNode.Name("c"), List(HotNode.Deps("b")), Nil),
      HotNode(HotNode.Name("free"), Nil, Nil)
    )
    val e = HotPlan.order(cyc).swap.getOrElse(fail("a cycle was ordered"))
    assert(e.value.contains("cycle"), e)
    assert(e.value.contains("a → c → b → a"), e)
    val self = HotPlan
      .order(List(HotNode(HotNode.Name("a"), List(HotNode.Deps("a")), Nil)))
      .swap
      .getOrElse(fail("self-loop ordered"))
    assert(self.value.contains("a → a"), self)
    assert(
      HotPlan
        .order(List(HotNode(HotNode.Name("a"), List(HotNode.Deps("nope")), Nil)))
        .swap
        .exists(_.value.contains("a → nope"))
    )
    assert(
      HotPlan
        .order(List(HotNode(HotNode.Name("a"), Nil, Nil), HotNode(HotNode.Name("a"), Nil, Nil)))
        .swap
        .exists(_.value.contains("twice"))
    )

  test("keyMatches: the key, a key under it, a block above it — not a sibling sharing a prefix"):
    assert(HotPlan.keyMatches(ConfigReload.ChangedKey("histmersion.db.url"), ConfigReload.Key("histmersion.db")))
    assert(HotPlan.keyMatches(ConfigReload.ChangedKey("histmersion.db"), ConfigReload.Key("histmersion.db")))
    assert(HotPlan.keyMatches(ConfigReload.ChangedKey("histmersion"), ConfigReload.Key("histmersion.db")))
    assert(!HotPlan.keyMatches(ConfigReload.ChangedKey("histmersion.dbx.url"), ConfigReload.Key("histmersion.db")))
    assert(!HotPlan.keyMatches(ConfigReload.ChangedKey("histmersion.query.user"), ConfigReload.Key("histmersion.db")))

  test("plan: the touched nodes and every dependent, in build order"):
    assertEquals(
      HotPlan.plan(nodes, List(ConfigReload.ChangedKey("histmersion.db.url"))),
      Right(List("pool", "migrations", "query-pool", "lease"))
    )
    assertEquals(
      HotPlan.plan(nodes, List(ConfigReload.ChangedKey("histmersion.query.password"))),
      Right(List("query-pool"))
    )
    assertEquals(
      HotPlan.plan(nodes, List(ConfigReload.ChangedKey("histmersion.plugins.keepassxc.enabled"))),
      Right(List("plugins", "acquisition"))
    )
    assertEquals(
      HotPlan.plan(nodes, List(ConfigReload.ChangedKey("histmersion.acquire.chain"))),
      Right(List("plugins", "acquisition"))
    )
    assertEquals(HotPlan.plan(nodes, List(ConfigReload.ChangedKey("histmersion.limits.search"))), Right(Nil))
    assertEquals(HotPlan.plan(nodes, Nil, forced = Set(HotNode.Name("lease"))), Right(List("lease")))
    assert(
      HotPlan
        .plan(
          List(HotNode(HotNode.Name("a"), List(HotNode.Deps("a")), List(HotNode.Keys("k")))),
          List(ConfigReload.ChangedKey("k"))
        )
        .isLeft
    )

  // ── ConfigReload ───────────────────────────────────────────────────────────────────────────────

  private def conf(text: String) = org.ekrich.config.ConfigFactory.parseString(text).resolve()

  test("changed: keys set in one and not the other, or set differently — the settings only, sorted"):
    val a = conf("""histmersion { db { url = "a", pool.max = 8 }, limits.search = 5, plugins.x.enabled = true }
                   |PATH = "/bin"""".stripMargin)
    val b = conf("""histmersion { db { url = "b", pool.max = 8 }, limits.search = 5, plugins.y.enabled = true }
                   |PATH = "/usr/bin"""".stripMargin)
    assertEquals(
      ConfigReload.changed(a, b),
      List("histmersion.db.url", "histmersion.plugins.x.enabled", "histmersion.plugins.y.enabled")
    )
    assertEquals(ConfigReload.changed(a, a), Nil)
    // a list is one setting
    assertEquals(
      ConfigReload.changed(conf("histmersion.l = [1, 2]"), conf("histmersion.l = [1, 3]")),
      List("histmersion.l")
    )

  test("split and effective: restart-only keys keep their running value"):
    val running =
      conf("""histmersion { server { port = 8000, public-url = "u" }, db.url = "a", runtime.pg-port = 1 }""")
    val next = conf("""histmersion { server { port = 9000, public-url = "v" }, db.url = "b", runtime.pg-port = 2 }""")
    val changed = ConfigReload.changed(running, next)
    val (applied, restart) = ConfigReload.split(changed)
    assertEquals(applied, List("histmersion.db.url", "histmersion.server.public-url"))
    assertEquals(restart, List("histmersion.runtime.pg-port", "histmersion.server.port"))
    val eff = ConfigReload.effective(running, next, changed)
    assertEquals(eff.getInt("histmersion.server.port"), 8000)
    assertEquals(eff.getInt("histmersion.runtime.pg-port"), 1)
    assertEquals(eff.getString("histmersion.db.url"), "b")
    assertEquals(eff.getString("histmersion.server.public-url"), "v")

  test("a reload of the user file: only what the file changed differs; the effective config decodes"):
    val d = Files.createTempDirectory("histmersion-reload")
    val f = d.resolve("histmersion.conf")
    val env = org.ekrich.config.ConfigFactory.parseMap(Map("HISTMERSION_DB_POOL_MAX" -> "3").asJava)
    Files.writeString(f, """histmersion { db.url = "jdbc:postgresql://h/a_test", limits.search = 7 }""")
    val first = AppConfig.loadWithUserFile(env, Some(f)).fold(p => fail(p.value), identity)
    Files.writeString(
      f,
      """histmersion { db.url = "jdbc:postgresql://h/b_test", limits.search = 7, server.port = 9123 }"""
    )
    val second = AppConfig.loadWithUserFile(env, Some(f)).fold(p => fail(p.value), identity)
    val changed = ConfigReload.changed(first.merged, second.merged)
    assertEquals(changed, List("histmersion.db.url", "histmersion.server.port"))
    val cfg = AppConfig
      .fromMerged(ConfigReload.effective(first.merged, second.merged, changed))
      .fold(p => fail(p.value), identity)
    assertEquals(cfg.db.url, "jdbc:postgresql://h/b_test")
    assertEquals(cfg.db.pool.max, 3) // the environment still wins
    assertEquals(cfg.server.port, first.config.server.port)
    assertEquals(cfg.config.watch, false)
