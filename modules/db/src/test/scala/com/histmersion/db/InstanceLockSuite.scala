package com.histmersion
package db

import hearth.kindlings.optics.syntax.*

import com.histmersion.config.{AppConfig, DbConfig}
import com.histmersion.util.database.InstanceLock
import com.histmersion.util.problem.Problem
import com.histmersion.lint.{tolerated, LintRule}

/** One server per database (G90): the instance lock on the `*_test` database. Skipped without HISTMERSION_TEST_DB_URL.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class InstanceLockSuite extends DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private lazy val db: DbConfig =
    val base = AppConfig.load().db
    base.modify(_.url).setTo(DbConfig.Url(testDb.get))

  private lazy val scratch = ScratchDatabases("histmersion_lock", db)

  override protected def afterSuite(): Unit = if testDb.nonEmpty then scratch.dropAll()

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def holds(name: String) = InstanceLock.Holds(InstanceLock.Holder(s"histmersion-server $name"))

  /** The session of `holder` on `db`'s database, as the database lists it. */
  private def sessionOf(holder: String): Option[Int] =
    val c = testPool(db).getConnection
    try
      val st = c.prepareStatement("SELECT pid FROM pg_stat_activity WHERE application_name = ?")
      st.setString(1, holder)
      val rs = st.executeQuery()
      Option.when(rs.next())(rs.getInt(1))
    finally c.close()

  /** End `holder`'s session, and wait (bounded) until the lock's connection sees it gone. */
  private def terminate(holds: InstanceLock.Holds, holder: String): Unit =
    val pid = sessionOf(holder).getOrElse(fail("no session holds the lock"))
    val c = testPool(db).getConnection
    try { val _ = c.createStatement().execute(s"SELECT pg_terminate_backend($pid)") }
    finally c.close()
    val deadline = System.nanoTime() + 5_000_000_000L
    while holds.holds(db) && System.nanoTime() < deadline do Thread.sleep(20)

  test("a second server is refused, naming the first; the first keeps the lock"):
    val first = holds("first:1")
    val second = holds("second:2")
    val held = first.hold(db).fold(p => fail(s"$p"), identity)
    try
      second.hold(db) match
        case Left(Problem.Conflict.ServerHeld(holder)) =>
          assert(s"$holder".startsWith("histmersion-server first:1, Postgres session "), s"$holder")
        case other => fail(s"the second server was not refused as it should be: $other")
      assert(first.holds(db), "the first server still holds it")
    finally held.release()
    // released with its last hold: the second one takes it now
    second.hold(db).fold(p => fail(s"$p"), _.release())

  test("holds of one server on one database share one lock, given up with the last of them"):
    val one = holds("one:1")
    val a = one.hold(db).fold(p => fail(s"$p"), identity)
    val b = one.hold(db).fold(p => fail(s"$p"), identity)
    a.release()
    a.release() // a second release of a hold does nothing
    assert(holds("other:2").hold(db).isLeft, "still held through the second hold")
    b.release()
    holds("other:2").hold(db).fold(p => fail(s"$p"), _.release())

  test("servers on other databases of the same cluster do not meet"):
    val _ = scratch("other")
    val other = db.modify(_.url).setTo(scratch.urlOf("histmersion_lock_other_test"))
    val here = holds("here:1").hold(db).fold(p => fail(s"$p"), identity)
    try holds("there:2").hold(other).fold(p => fail(s"$p"), _.release())
    finally here.release()

  test("a lock whose session died is taken again; one another server took meanwhile is reported"):
    val mine = holds("mine:1")
    val held = mine.hold(db).fold(p => fail(s"$p"), identity)
    try
      terminate(mine, "histmersion-server mine:1")
      assert(!mine.holds(db), "the session is gone, and the lock with it")
      assertEquals(mine.renew(db), Right(()))
      assert(mine.holds(db), "taken again")
      // the session dies again, and another server takes the lock first
      terminate(mine, "histmersion-server mine:1")
      val thief = holds("thief:2").hold(db).fold(p => fail(s"$p"), identity)
      try
        mine.renew(db) match
          case Left(Problem.Conflict.ServerHeld(holder)) => assert(s"$holder".contains("thief:2"), s"$holder")
          case other                                     => fail(s"$other")
      finally thief.release()
    finally held.release()
