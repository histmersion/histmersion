package com.histmersion.db

import com.histmersion.core.*

import com.augustnagro.magnum.*

/** Named periods against a real Postgres (HISTMERSION_TEST_DB_URL); skipped otherwise (#719): the
  * upsert keeps the foreign-key order, reads the inclusive upper bound back, is idempotent, and the
  * placement it reports is what keeps a re-import from moving a period. Fixture ids are `zzr*`. */
class PeriodRepoSuite extends munit.FunSuite:
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val ds =
    val d = Db.dataSource(AppConfig.load().db.copy(url = testDb.get))
    Migrate.run(d); d

  private val ids = List("zzr_root", "zzr_bronze", "zzr_lba", "zzr_lm", "zzr_other")

  private def clean(): Unit =
    // children first: parent_id is ON DELETE SET NULL, but deleting leaves-first keeps it obvious
    ids.reverse.foreach(id => transact(ds)(sql"DELETE FROM periods WHERE id = $id".update.run()))

  override def beforeAll(): Unit = if testDb.nonEmpty then clean()
  override def afterAll(): Unit  = if testDb.nonEmpty then clean()

  private def hp(id: String, s: Long, e: Long, parent: Option[String] = None) =
    HistoricalPeriod(id, id.toUpperCase, Some(s"PeriodO $id · test"), Period(s, e), Some("Zzrland"), parent)

  test("upsert writes parents before children and reads the inclusive span back"):
    val rows = List(hp("zzr_root", -3000, -1000), hp("zzr_bronze", -3000, -1200, Some("zzr_root")),
      hp("zzr_lba", -1600, -1200, Some("zzr_bronze")), hp("zzr_lm", -1700, -1050, Some("zzr_bronze")))
    assertEquals(PeriodRepo.upsert(ds, rows), 4)
    assertEquals(PeriodRepo.get(ds, "zzr_lba"), Some(rows(2)))
    assertEquals(PeriodRepo.get(ds, "zzr_lba").map(_.period.end), Some(-1200L)) // not -1199
    assertEquals(PeriodRepo.children(ds, "zzr_bronze").map(_.id), Vector("zzr_lm", "zzr_lba")) // by start
    assertEquals(PeriodRepo.get(ds, "zzr_nope"), None)

  test("placement reports every stored id with its parent, None at the top"):
    assertEquals(PeriodRepo.placement(ds, List("zzr_root", "zzr_lba", "zzr_missing")),
      Map("zzr_root" -> None, "zzr_lba" -> Some("zzr_bronze")))
    assertEquals(PeriodRepo.placement(ds, Nil), Map.empty)

  test("re-running an upsert is idempotent and a re-parent sticks"):
    val before = PeriodRepo.count(ds)
    PeriodRepo.upsert(ds, List(hp("zzr_lba", -1600, -1200, Some("zzr_root"))))
    assertEquals(PeriodRepo.count(ds), before)
    assertEquals(PeriodRepo.get(ds, "zzr_lba").flatMap(_.parentId), Some("zzr_root"))

  test("the timeline presets are the parentless rows, and the list filter finds them by span"):
    PeriodRepo.upsert(ds, List(hp("zzr_other", 100, 200)))
    val presets = PlaceRepo.listPeriods(ds, Some("ZZR"), None, None, None).filter(_.parentId.isEmpty).map(_.id)
    assertEquals(presets.toSet, Set("zzr_root", "zzr_other"))
    assert(PeriodRepo.presetCount(ds) >= 2)
    // `to` alone is a span too (it used to be ignored without `from`)
    assertEquals(PlaceRepo.listPeriods(ds, Some("ZZR"), None, None, Some(-2500)).map(_.id).toSet,
      Set("zzr_root", "zzr_bronze"))
    assertEquals(PlaceRepo.listPeriods(ds, Some("ZZR"), Some("zzrland"), Some(150), None).map(_.id), Vector("zzr_other"))
