package com.histmersion
package server.hot
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.{sentence, sentences}

import com.histmersion.testsupport.ComparedByValue.given

import ox.{releaseAfterScope, useInScope}

import java.util.concurrent.{ConcurrentLinkedQueue, CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import com.histmersion.config.HotNode
import com.histmersion.lint.{LintRule, tolerated}

/** The hot component graph without a database (docs/research-plugins.md §3.6): nodes built in scopes of their own, a
  * reload rebuilding what the changed keys concern and its dependents, a failed build publishing nothing, pins keeping
  * a generation open, retired scopes closed once free.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
@tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
class HotGraphSuite extends munit.FunSuite:

  type Conf = Map[String, String]

  /** A resource whose opening and closing is recorded. */
  final class Res(val name: String, log: ConcurrentLinkedQueue[String]):
    log.add(s"open $name")
    @volatile var closed = false
    def close(): Unit = { closed = true; log.add(s"close $name") }

  private val quiet = new Hot.Graph.Log:
    def info(m: Problem) = ()
    def warn(m: Problem) = ()

  final private class Fixture:
    val log = ConcurrentLinkedQueue[String]()
    val graph = Hot.Graph[Conf](Map("db" -> "a", "query" -> "q1", "other" -> "x"), quiet)
    val builds = AtomicInteger(0)
    @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
    val pool = graph
      .node[Res](Hot.Graph.Def.Name("pool"), keys = List(HotNode.Keys("db"))) { ctx =>
        builds.incrementAndGet()
        ctx.config("db") match
          case "broken" => Left(Problem.Unclassified(Legacy.Text("cannot connect")))
          case db       => Right(useInScope(Res(s"pool-$db", log))(_.close()))
      }
      .fold(p => fail(p.sentence), identity)
    val schema = graph
      .node[String](Hot.Graph.Def.Name("schema"), deps = List(pool)) { ctx =>
        Right(s"migrated ${ctx(pool).name}")
      }
      .fold(p => fail(p.sentence), identity)
    @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
    val query = graph
      .node[Res](Hot.Graph.Def.Name("query"), deps = List(pool), keys = List(HotNode.Keys("query"))) { ctx =>
        if ctx.config("query") == "bad" then Left(Problem.Unclassified(Legacy.Text("role cannot log in")))
        else Right(useInScope(Res(s"query-${ctx.config("query")}-on-${ctx(pool).name}", log))(_.close()))
      }
      .fold(p => fail(p.sentence), identity)
    val loose = graph
      .node[Res](Hot.Graph.Def.Name("loose"), keys = List(HotNode.Keys("other"))) { ctx =>
        Right(useInScope(Res(s"loose-${ctx.config("other")}", log))(_.close()))
      }
      .fold(p => fail(p.sentence), identity)
    def opened: List[String] = log.asScala.toList.filter(_.startsWith("open"))
    def closed: List[String] = log.asScala.toList.filter(_.startsWith("close"))

  private def withFixture(body: Fixture => Unit): Unit =
    val f = Fixture()
    try body(f)
    finally f.graph.close(1.second)

  test("nodes are built from the config and their dependencies' values"):
    withFixture { f =>
      assertEquals(f.pool.use(_.name), "pool-a")
      assertEquals(f.schema.use(identity), "migrated pool-a")
      assertEquals(f.query.use(_.name), "query-q1-on-pool-a")
      assertEquals(f.graph.generation, 0L)
      assertEquals(f.graph.nodes, List("pool", "schema", "query", "loose"))
    }

  test("a reload rebuilds the changed node and its dependents, carries the rest, closes the old"):
    withFixture { f =>
      val looseBefore = f.loose.peek
      val o = f.graph
        .reload(Map("db" -> "b", "query" -> "q1", "other" -> "x"), List(HotNode.Name("db")), drain = 2.seconds)
        .fold(p => fail(p.sentences.mkString("; ")), identity)
      assertEquals(o.generation, 1L)
      assertEquals(o.rebuilt, List("pool", "schema", "query"))
      assertEquals(o.draining, Nil)
      assertEquals(f.pool.use(_.name), "pool-b")
      assertEquals(f.schema.use(identity), "migrated pool-b")
      assertEquals(f.query.use(_.name), "query-q1-on-pool-b")
      assert(f.loose.peek eq looseBefore, "an untouched node is carried over as it is")
      // dependents closed before what they depend on
      assertEquals(f.closed, List("close query-q1-on-pool-a", "close pool-a"))
      assertEquals(f.graph.config("db"), "b")
    }

  test("a key no node declares publishes the new config and rebuilds nothing"):
    withFixture { f =>
      val o = f.graph
        .reload(Map("db" -> "a", "query" -> "q1", "other" -> "x", "limit" -> "5"), List(HotNode.Name("limit")))
        .fold(p => fail(p.sentences.mkString("; ")), identity)
      assertEquals(o.rebuilt, Nil)
      assertEquals(f.graph.config.get("limit"), Some("5"))
      assertEquals(f.builds.get, 1)
    }

  test("a failed build publishes nothing and closes what the reload had built"):
    withFixture { f =>
      // the pool builds, the query role does not: nothing changes, the new pool is closed again
      val e = f.graph.reload(
        Map("db" -> "b", "query" -> "bad", "other" -> "x"),
        List(HotNode.Name("db"), HotNode.Name("query"))
      )
      assert(
        e.swap.exists(m =>
          m.sentences.mkString("; ").contains("query") && m.sentences.mkString("; ").contains("role cannot log in")
        ),
        e.toString
      )
      assertEquals(f.graph.generation, 0L)
      assertEquals(f.graph.config("db"), "a")
      assertEquals(f.pool.use(_.name), "pool-a")
      assertEquals(f.query.use(_.name), "query-q1-on-pool-a")
      assert(f.closed.contains("close pool-b"), f.closed.toString)
      assert(!f.closed.exists(_.contains("pool-a")), f.closed.toString)
      // the first node failing: same
      val e2 = f.graph.reload(Map("db" -> "broken", "query" -> "q1", "other" -> "x"), List(HotNode.Name("db")))
      assert(e2.swap.exists(_.sentences.mkString("; ").contains("cannot connect")), e2.toString)
      assertEquals(f.pool.use(_.name), "pool-a")
    }

  test("an exception in a build is a failed build, not a crash"):
    withFixture { f =>
      val h = f.graph
        .node[Int](Hot.Graph.Def.Name("throws"), keys = List(HotNode.Keys("t"))) { ctx =>
          if ctx.config.get("t").contains("boom") then throw IllegalStateException("boom") else Right(1)
        }
        .fold(p => fail(p.sentence), identity)
      val e = f.graph.reload(Map("db" -> "a", "query" -> "q1", "other" -> "x", "t" -> "boom"), List(HotNode.Name("t")))
      assert(e.swap.exists(_.sentences.mkString("; ").contains("boom")), e.toString)
      assertEquals(h.use(identity), 1)
    }

  test("a node that would close a cycle, or names a node twice, is refused"):
    withFixture { f =>
      assert(
        f.graph
          .node[Int](Hot.Graph.Def.Name("pool"))(_ => Right(1))
          .swap
          .exists(_.sentence.contains("hot node name 'pool' already exists"))
      )
    }

  test("a pinned user keeps its generation: the old value stays open until it lets go"):
    withFixture { f =>
      val pinned = CountDownLatch(1)
      val release = CountDownLatch(1)
      val seen = ConcurrentLinkedQueue[String]()
      val user = Thread.ofVirtual().start { () =>
        f.graph.pinned {
          seen.add(f.pool.use(_.name))
          pinned.countDown()
          release.await()
          // after the reload: still the generation it started on
          seen.add(f.pool.use(_.name))
          seen.add(f.query.use(_.name))
          seen.add(f.graph.config("db"))
        }
      }
      assert(pinned.await(5, TimeUnit.SECONDS))
      val o = f.graph
        .reload(Map("db" -> "b", "query" -> "q1", "other" -> "x"), List(HotNode.Name("db")), drain = 200.millis)
        .fold(p => fail(p.sentences.mkString("; ")), identity)
      // the bound passed with the old generation still pinned: reported, not closed
      assertEquals(o.draining.size, 3, o.draining.toString)
      assert(o.draining.exists(_.value.startsWith("pool (generation 0")), o.draining.toString)
      assert(!f.closed.exists(_.contains("pool-a")), f.closed.toString)
      assertEquals(f.pool.use(_.name), "pool-b") // a new user sees the new generation
      release.countDown()
      user.join(5000)
      assertEquals(seen.asScala.toList, List("pool-a", "pool-a", "query-q1-on-pool-a", "a"))
      // the drain closes them once the pin is gone
      val deadline = System.nanoTime() + 5.seconds.toNanos
      while f.graph.stillDraining.nonEmpty && System.nanoTime() < deadline do Thread.sleep(10)
      assertEquals(f.graph.stillDraining, Nil)
      assertEquals(f.closed.toSet, Set("close query-q1-on-pool-a", "close pool-a"))
    }

  test("work carried to another thread runs on the generation it was carried from"):
    withFixture { f =>
      val work = f.graph.pinned(f.graph.carry(f.pool.use(_.name)))
      f.graph
        .reload(Map("db" -> "b", "query" -> "q1", "other" -> "x"), List(HotNode.Name("db")), drain = 50.millis)
        .fold(p => fail(p.sentences.mkString("; ")), identity)
      assert(!f.closed.exists(_.contains("pool-a")), "the carried work's generation stays open")
      val out = java.util.concurrent.CompletableFuture[String]()
      Thread.ofVirtual().start { () =>
        val _ = out.complete(work.run())
      }
      assertEquals(out.get(5, TimeUnit.SECONDS), "pool-a")
      val deadline = System.nanoTime() + 5.seconds.toNanos
      while !f.closed.contains("close pool-a") && System.nanoTime() < deadline do Thread.sleep(10)
      assert(f.closed.contains("close pool-a"), f.closed.toString)
      // a dropped carry releases its pin too
      val dropped = f.graph.carry(f.pool.use(_.name))
      f.graph
        .reload(Map("db" -> "c", "query" -> "q1", "other" -> "x"), List(HotNode.Name("db")), drain = 50.millis)
        .fold(p => fail(p.sentences.mkString("; ")), identity)
      dropped.drop()
      val d2 = System.nanoTime() + 5.seconds.toNanos
      while !f.closed.contains("close pool-b") && System.nanoTime() < d2 do Thread.sleep(10)
      assert(f.closed.contains("close pool-b"), f.closed.toString)
    }

  test("a retired value with its own idle check waits for it (a pool with a connection out)"):
    val out = AtomicInteger(1)
    val log = ConcurrentLinkedQueue[String]()
    val graph = Hot.Graph[Conf](Map("db" -> "a"), quiet)
    try
      val pool = graph
        .node[Res](Hot.Graph.Def.Name("pool"), keys = List(HotNode.Keys("db")), idle = _ => out.get == 0) { ctx =>
          Right(useInScope(Res(s"pool-${ctx.config("db")}", log))(_.close()))
        }
        .fold(p => fail(p.sentence), identity)
      val o =
        graph
          .reload(Map("db" -> "b"), List(HotNode.Name("db")), drain = 100.millis)
          .fold(p => fail(p.sentences.mkString("; ")), identity)
      assertEquals(o.draining.size, 1)
      assert(!log.contains("close pool-a"))
      out.set(0) // the last connection came back
      val deadline = System.nanoTime() + 5.seconds.toNanos
      while !log.contains("close pool-a") && System.nanoTime() < deadline do Thread.sleep(10)
      assert(log.contains("close pool-a"))
      assertEquals(pool.peek.name, "pool-b")
    finally graph.close(1.second)

  test("onPublish runs on the new generation, whatever the reloading thread is pinned to"):
    val graph = Hot.Graph[Conf](Map("db" -> "a"), quiet)
    try
      val seen = ConcurrentLinkedQueue[String]()
      val pool = graph
        .node[String](Hot.Graph.Def.Name("pool"), keys = List(HotNode.Keys("db")))(ctx => Right(ctx.config("db")))
        .fold(p => fail(p.sentence), identity)
      val _ = graph
        .node[String](
          Hot.Graph.Def.Name("hook"),
          deps = List(pool),
          onPublish = _ => { val _ = seen.add(pool.use(identity)) }
        ) { ctx =>
          Right(ctx(pool))
        }
        .fold(p => fail(p.sentence), identity)
      graph.pinned {
        graph
          .reload(Map("db" -> "b"), List(HotNode.Name("db")), drain = 50.millis)
          .fold(p => fail(p.sentences.mkString("; ")), identity)
        assertEquals(pool.use(identity), "a") // this thread's own pin
      }
      assertEquals(seen.asScala.toList, List("b"))
    finally graph.close(1.second)

  test("a failed build's own resources are released at once; close() closes everything"):
    val log = ConcurrentLinkedQueue[String]()
    val graph = Hot.Graph[Conf](Map("db" -> "a"), quiet)
    val h = graph
      .node[Res](Hot.Graph.Def.Name("pool"), keys = List(HotNode.Keys("db"))) { ctx =>
        val r = useInScope(Res(s"pool-${ctx.config("db")}", log))(_.close())
        releaseAfterScope(log.add(s"released ${r.name}"))
        if ctx.config("db") == "half" then Left(Problem.Unclassified(Legacy.Text("the second half failed")))
        else Right(r)
      }
      .fold(p => fail(p.sentence), identity)
    assert(graph.reload(Map("db" -> "half"), List(HotNode.Name("db"))).isLeft)
    assert(log.contains("close pool-half") && log.contains("released pool-half"), log.toString)
    graph.close(1.second)
    assert(log.contains("close pool-a"))
    assert(h.peek.closed)
    assert(
      graph
        .reload(Map("db" -> "z"), List(HotNode.Name("db")))
        .swap
        .exists(_.sentences.mkString("; ").contains("closed"))
    )
