package com.histmersion.db

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.util.database.Transactor

import com.histmersion.util.database.RangeSql

import com.histmersion.api.Write
import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import com.histmersion.domain.automation.goals.GoalRepo
import com.histmersion.util.database.{Migrate, Param}
import com.histmersion.api.catalogue.Period
import com.histmersion.api.automation.Goal
import com.histmersion.util.database.given

/** [[RangeSql]] (#1798 item 9): the pure literal/merge rules, and — on a test database — a goal or an asset patched
  * with ONE bound (it used to be silently ignored), read back through [[RangeSql.cols]].
  */
class RangeSqlSuite extends com.histmersion.db.DbSuite:

  // ── pure ───────────────────────────────────────────────────────────────────────────────────────
  test("literal: closed, open on either side, none"):
    assertEquals(RangeSql.literal(Some(com.histmersion.domain.DeepTime.SignedYear(-1200)), Some(com.histmersion.domain.DeepTime.SignedYear(-1000))), Some("[-1200,-999)"))
    assertEquals(RangeSql.literal(Some(com.histmersion.domain.DeepTime.SignedYear(-1200)), None), Some("[-1200,)"))
    assertEquals(RangeSql.literal(None, Some(com.histmersion.domain.DeepTime.SignedYear(-1000))), Some("(,-999)"))
    assertEquals(RangeSql.literal(None, None), None)

  test("bounds and always: every year when no bound is given; literal of a closed period"):
    assertEquals(RangeSql.bounds(None, None), "(,)")
    assertEquals(RangeSql.always(None), "(,)")
    assertEquals(RangeSql.always(Some(Period(com.histmersion.api.catalogue.Year(-1200), com.histmersion.api.catalogue.Year(-1000)))), "[-1200,-999)")
    assertEquals(RangeSql.literal(Some(Period(com.histmersion.api.catalogue.Year(5), com.histmersion.api.catalogue.Year(5)))), Some("[5,6)"))
    assertEquals(RangeSql.literal(Option.empty[Period]), None)
    assertEquals(RangeSql.literalOf(DeepTime.span(com.histmersion.domain.DeepTime.SignedYear(-1200L), com.histmersion.domain.DeepTime.SignedYear(-1000L))), Some("[-1200,-999)"))
    assertEquals(RangeSql.literalOf(None), None)

  test("the SQL spellings: inclusive reads, a range of inclusive bounds, an inclusive overlap"):
    assertEquals(RangeSql.cols("f.period"), "lower(f.period), upper(f.period) - 1")
    assertEquals(RangeSql.cols("f.period", "p0", "p1"), "lower(f.period) AS p0, upper(f.period) - 1 AS p1")
    assertEquals(RangeSql.range("d.lo", "d.hi"), "int8range(d.lo, d.hi + 1, '[)')")
    val o = RangeSql.overlaps("s.period", Period(com.histmersion.api.catalogue.Year(-10), com.histmersion.api.catalogue.Year(10)))
    assertEquals(o.text, "s.period && int8range(?::bigint, ?::bigint, '[]')")
    assertEquals(o.params, List(Param.L(com.histmersion.util.database.Param.L.V(-10)), Param.L(com.histmersion.util.database.Param.L.V(10))))

  test("merge: a bound given alone replaces that bound and keeps the other"):
    val stored = (Some(com.histmersion.domain.DeepTime.SignedYear(-1200L)), Some(com.histmersion.domain.DeepTime.SignedYear(-1000L)))
    assertEquals(RangeSql.merge(stored, Some(com.histmersion.domain.DeepTime.SignedYear(-1300)), None, "x"), Some(Right((Some(-1300L), Some(-1000L)))))
    assertEquals(RangeSql.merge(stored, None, Some(com.histmersion.domain.DeepTime.SignedYear(-900)), "x"), Some(Right((Some(-1200L), Some(-900L)))))
    assertEquals(RangeSql.merge(stored, None, None, "x"), None, "nothing named, nothing to write")
    assertEquals(RangeSql.merge((None, None), Some(com.histmersion.domain.DeepTime.SignedYear(-50)), None, "x"), Some(Right((Some(-50L), None))))

  test("merge: a result that starts after it ends is refused, naming both years"):
    val r = RangeSql.merge((Some(com.histmersion.domain.DeepTime.SignedYear(-1200L)), Some(com.histmersion.domain.DeepTime.SignedYear(-1000L))), Some(com.histmersion.domain.DeepTime.SignedYear(-900)), None, "goal 'g'")
    assert(
      r.exists(_.left.exists(e => e.message.value.contains("900 BCE") && e.code.contains(Write.CodeInvalid))),
      r.toString
    )

  test("period: closed only when both bounds are known"):
    assertEquals(RangeSql.period(Some(com.histmersion.domain.DeepTime.SignedYear(-5)), Some(com.histmersion.domain.DeepTime.SignedYear(5))), Some(Period(com.histmersion.api.catalogue.Year(-5), com.histmersion.api.catalogue.Year(5))))
    assertEquals(RangeSql.period(Some(com.histmersion.domain.DeepTime.SignedYear(-5)), None), None)

  // ── on a test database ────────────────────────────────────────────────────────────────────────
  private val testDb = TestDb.url
  private lazy val cfg = AppConfig.load()
  private lazy val ds =
    val d = testPool(cfg.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
    Migrate.run(d); d

  private def dbTest(name: String)(body: => Unit): Unit =
    test(name)(if testDb.isEmpty then assume(false, "HISTMERSION_TEST_DB_URL is not set") else body)

  dbTest("a goal patched with one bound changes that bound (#1798)"):
    connect(ds)(sql"DELETE FROM goals WHERE slug = 'zz_period_goal'".update.run())
    assertEquals(
      transact(ds)(
        GoalRepo.put(com.histmersion.domain.automation.Goal.Slug("zz_period_goal"), Goal.Put(com.histmersion.api.automation.Goal.Title("Period goal"), periodStart = Some(com.histmersion.api.catalogue.Year(-1200)), periodEnd = Some(com.histmersion.api.catalogue.Year(-1000))))
      ),
      Right(())
    )
    assertEquals(
      Transactor(ds).transact(GoalRepo.patchIn(com.histmersion.domain.automation.Goal.Slug("zz_period_goal"), Goal.Patch(periodStart = Some(com.histmersion.api.catalogue.Year(-1300))))),
      Right(())
    )
    val g = connect(ds)(GoalRepo.get(com.histmersion.domain.automation.Goal.Slug("zz_period_goal"))).get
    assertEquals((g.periodStart, g.periodEnd), (Some(-1300L), Some(-1000L)))
    assertEquals(
      Transactor(ds).transact(GoalRepo.patchIn(com.histmersion.domain.automation.Goal.Slug("zz_period_goal"), Goal.Patch(periodEnd = Some(com.histmersion.api.catalogue.Year(-1100))))),
      Right(())
    )
    val g2 = connect(ds)(GoalRepo.get(com.histmersion.domain.automation.Goal.Slug("zz_period_goal"))).get
    assertEquals((g2.periodStart, g2.periodEnd), (Some(-1300L), Some(-1100L)))
    // inverted: refused, stored period unchanged
    assert(Transactor(ds).transact(GoalRepo.patchIn(com.histmersion.domain.automation.Goal.Slug("zz_period_goal"), Goal.Patch(periodStart = Some(com.histmersion.api.catalogue.Year(-1000))))).isLeft)
    assertEquals(
      connect(ds)(GoalRepo.get(com.histmersion.domain.automation.Goal.Slug("zz_period_goal"))).map(x => (x.periodStart, x.periodEnd)),
      Some((Some(-1300L), Some(-1100L)))
    )
    connect(ds)(sql"DELETE FROM goals WHERE slug = 'zz_period_goal'".update.run())

  dbTest("a goal put with one bound stores an open period; an inverted one is refused"):
    connect(ds)(sql"DELETE FROM goals WHERE slug = 'zz_period_open'".update.run())
    assertEquals(transact(ds)(GoalRepo.put(com.histmersion.domain.automation.Goal.Slug("zz_period_open"), Goal.Put(com.histmersion.api.automation.Goal.Title("Open"), periodStart = Some(com.histmersion.api.catalogue.Year(-3300))))), Right(()))
    assertEquals(
      connect(ds)(GoalRepo.get(com.histmersion.domain.automation.Goal.Slug("zz_period_open"))).map(x => (x.periodStart, x.periodEnd)),
      Some((Some(-3300L), None))
    )
    assert(
      transact(ds)(GoalRepo.put(com.histmersion.domain.automation.Goal.Slug("zz_period_open"), Goal.Put(com.histmersion.api.automation.Goal.Title("Open"), periodStart = Some(com.histmersion.api.catalogue.Year(5)), periodEnd = Some(com.histmersion.api.catalogue.Year(-5))))).isLeft
    )
    connect(ds)(sql"DELETE FROM goals WHERE slug = 'zz_period_open'".update.run())
