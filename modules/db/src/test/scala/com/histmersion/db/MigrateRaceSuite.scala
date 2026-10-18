package com.histmersion.db

import com.histmersion.core.*

import java.util.concurrent.{CyclicBarrier, Executors, TimeUnit}
import javax.sql.DataSource
import scala.jdk.CollectionConverters.*

/** Two servers migrating fresh databases of ONE cluster at the same moment (#828): the role V011
  * creates is cluster-global, so both used to try to create it and the second failed. Works in
  * databases of its own (`histmersion_race_*_test`) and a throwaway role, both dropped afterwards.
  * Skipped without HISTMERSION_TEST_DB_URL. */
class MigrateRaceSuite extends com.histmersion.db.DbSuite with Bounded:
  private val testDb                = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  private lazy val cfg              = AppConfig.load()
  private lazy val admin            = testPool(cfg.db.copy(url = testDb.get))
  private val created               = scala.collection.mutable.ListBuffer.empty[String]
  private val role                  = s"histmersion_race_${java.lang.Long.toHexString(System.nanoTime)}"

  private def exec(ds: DataSource, stmt: String): Unit =
    val c = ds.getConnection
    try { val st = c.createStatement(); try { val _ = st.execute(stmt) } finally st.close() }
    finally c.close()

  private def roleExists(name: String): Boolean =
    val c = admin.getConnection
    try
      val ps = c.prepareStatement("SELECT 1 FROM pg_roles WHERE rolname = ?")
      ps.setString(1, name)
      val rs = ps.executeQuery()
      try rs.next() finally { rs.close(); ps.close() }
    finally c.close()

  /** Run `n` copies of `f` released together by a barrier; every outcome, in order. */
  private def together[A](n: Int)(f: Int => A): List[scala.util.Try[A]] =
    val pool    = Executors.newFixedThreadPool(n)
    val barrier = CyclicBarrier(n)
    try
      val tasks = (0 until n).map(i => (() => scala.util.Try { barrier.await(); f(i) }): java.util.concurrent.Callable[scala.util.Try[A]])
      pool.invokeAll(tasks.asJava, 4, TimeUnit.MINUTES).asScala.toList.map(_.get)
    finally pool.shutdownNow(): Unit

  override def afterSuite(): Unit =
    if testDb.isDefined then
      created.foreach(n => exec(admin, s"DROP DATABASE IF EXISTS $n WITH (FORCE)"))
      exec(admin, s"DROP ROLE IF EXISTS $role")

  test("ensureRole from many sessions at once: every one succeeds, the role exists once"):
    val outcomes = together(8)(_ => Migrate.ensureRole(admin, role))
    assert(outcomes.forall(_.isSuccess), outcomes.collect { case scala.util.Failure(e) => e.getMessage })
    assert(roleExists(role))
    Migrate.ensureRole(admin, role) // and again, alone: idempotent

  test("two fresh databases of one cluster migrate at the same moment"):
    val names = List("a", "b").map(n => s"histmersion_race_${n}_test")
    names.foreach { n =>
      exec(admin, s"DROP DATABASE IF EXISTS $n WITH (FORCE)")
      exec(admin, s"CREATE DATABASE $n")
      created += n
    }
    val dss = names.map(n => testPool(cfg.db.copy(url = testDb.get.replaceFirst("/[^/?]+(\\?|$)", s"/$n$$1"))))
    val outcomes = together(2)(i => Migrate.run(dss(i)))
    assert(outcomes.forall(_.isSuccess), outcomes.collect { case scala.util.Failure(e) => e.getMessage })
    assertEquals(outcomes.map(_.get).distinct.size, 1, "both at the same version")
