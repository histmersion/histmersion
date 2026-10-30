package com.histmersion.db

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*

/** Named periods against a real Postgres (HISTMERSION_TEST_DB_URL); skipped otherwise (#719): the upsert keeps the
  * foreign-key order, reads the inclusive upper bound back, is idempotent, and the placement it reports is what keeps a
  * re-import from moving a period. Fixture ids are `zzr*`.
  */
class PeriodRepoSuite extends com.histmersion.db.DbSuite:
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val ds =
    val d = testPool(AppConfig.load().db.copy(url = testDb.get))
    Migrate.run(d); d

  private val ids = List("zzr_root", "zzr_bronze", "zzr_lba", "zzr_lm", "zzr_other", "zzr_gone", "zzr_mine")

  private def clean(): Unit =
    // children first: parent_id is ON DELETE SET NULL, but deleting leaves-first keeps it obvious
    ids.reverse.foreach(id => transact(ds)(sql"DELETE FROM periods WHERE id = $id".update.run()))

  override def beforeAll(): Unit = if testDb.nonEmpty then clean()
  override def afterSuite(): Unit = if testDb.nonEmpty then clean()

  private def hp(id: String, s: Long, e: Long, parent: Option[String] = None) =
    HistoricalPeriod(id, id.toUpperCase, Some(s"PeriodO $id · test"), Period(s, e), Some("Zzrland"), parent)

  test("upsert writes parents before children and reads the inclusive span back"):
    val rows = List(
      hp("zzr_root", -3000, -1000),
      hp("zzr_bronze", -3000, -1200, Some("zzr_root")),
      hp("zzr_lba", -1600, -1200, Some("zzr_bronze")),
      hp("zzr_lm", -1700, -1050, Some("zzr_bronze"))
    )
    assertEquals(PeriodRepo.upsert(ds, rows), 4)
    assertEquals(PeriodRepo.get(ds, "zzr_lba"), Some(rows(2)))
    assertEquals(PeriodRepo.get(ds, "zzr_lba").map(_.period.end), Some(-1200L)) // not -1199
    assertEquals(PeriodRepo.children(ds, "zzr_bronze").map(_.id), Vector("zzr_lm", "zzr_lba")) // by start
    assertEquals(PeriodRepo.get(ds, "zzr_nope"), None)

  test("placement reports every stored id with its parent, None at the top"):
    assertEquals(
      PeriodRepo.placement(ds, List("zzr_root", "zzr_lba", "zzr_missing")),
      Map("zzr_root" -> None, "zzr_lba" -> Some("zzr_bronze"))
    )
    assertEquals(PeriodRepo.placement(ds, Nil), Map.empty)

  test("re-running an upsert is idempotent and a re-parent sticks"):
    val before = PeriodRepo.count(ds)
    PeriodRepo.upsert(ds, List(hp("zzr_lba", -1600, -1200, Some("zzr_root"))))
    assertEquals(PeriodRepo.count(ds), before)
    assertEquals(PeriodRepo.get(ds, "zzr_lba").flatMap(_.parentId), Some("zzr_root"))

  test("the timeline presets are the rows filed under a region heading, and the list filter finds them by span"):
    PeriodRepo.upsert(ds, List(hp("zzr_other", 100, 200).copy(preset = Some(PresetRegion.Aegean))))
    val presets = PlaceRepo.listPeriods(ds, Some("ZZR"), None, None, None).filter(_.preset.isDefined)
    assertEquals(presets.map(p => p.id -> p.preset), Vector("zzr_other" -> Some(PresetRegion.Aegean)))
    assert(PeriodRepo.presetCount(ds) >= 1)
    // `to` alone is a span too (it used to be ignored without `from`)
    assertEquals(
      PlaceRepo.listPeriods(ds, Some("ZZR"), None, None, Some(-2500)).map(_.id).toSet,
      Set("zzr_root", "zzr_bronze")
    )
    assertEquals(
      PlaceRepo.listPeriods(ds, Some("ZZR"), Some("zzrland"), Some(150), None).map(_.id),
      Vector("zzr_other")
    )

  test("an upsert without a preset keeps the stored one; retainPresets takes the others off the menu"):
    PeriodRepo.upsert(ds, List(hp("zzr_other", 100, 200)))
    assertEquals(PeriodRepo.get(ds, "zzr_other").flatMap(_.preset), Some(PresetRegion.Aegean))
    PeriodRepo.upsert(ds, List(hp("zzr_root", -3000, -1000).copy(preset = Some(PresetRegion.Iran))))
    // keep every preset there is except zzr_other's
    val keep = PlaceRepo
      .listPeriods(ds, None, None, None, None)
      .filter(_.preset.isDefined)
      .map(_.id)
      .toList
      .filterNot(_ == "zzr_other")
    assert(PeriodRepo.retainPresets(ds, keep) >= 1)
    assertEquals(
      PeriodRepo.get(ds, "zzr_other").map(p => (p.preset, p.label)),
      Some((None, "ZZR_OTHER"))
    ) // the row stays
    assertEquals(PeriodRepo.get(ds, "zzr_root").flatMap(_.preset), Some(PresetRegion.Iran))

  test("deleteSeedExcept removes seed rows not kept and never a collection's own"):
    PeriodRepo.upsert(ds, List(hp("zzr_gone", 1, 2))) // a seed row
    PeriodRepo.upsert(ds, List(hp("zzr_mine", 1, 2)), Dataset.Local) // this collection's own
    val seedNow = connect(ds)(sql"SELECT id FROM periods WHERE dataset = 'seed'".query[String].run()).toList
    assertEquals(PeriodRepo.deleteSeedExcept(ds, seedNow.filterNot(_ == "zzr_gone")), 1)
    assertEquals(PeriodRepo.get(ds, "zzr_gone"), None)
    assert(PeriodRepo.get(ds, "zzr_mine").isDefined)

  test("the seed version is stored and moved"):
    val name = "zzr_seed"
    transact(ds)(sql"DELETE FROM seed_versions WHERE name = $name".update.run())
    assertEquals(PeriodRepo.seedVersion(ds, name), None)
    PeriodRepo.setSeedVersion(ds, name, 2)
    PeriodRepo.setSeedVersion(ds, name, 3)
    assertEquals(PeriodRepo.seedVersion(ds, name), Some(3))
    transact(ds)(sql"DELETE FROM seed_versions WHERE name = $name".update.run())

  // #769: a label search with `_` matched nothing (q=ZZR_ → 0 rows)
  test("the label search treats `_` as a literal character, not a wildcard"):
    PeriodRepo.upsert(ds, List(hp("zzr_root", -3000, -1000), hp("zzr_other", 100, 200)))
    // every stored fixture's label (`ZZR_…`) contains `ZZR_`
    assertEquals(
      PlaceRepo.listPeriods(ds, Some("ZZR_"), None, None, None).map(_.id).toSet.intersect(ids.toSet),
      PeriodRepo.placement(ds, ids).keySet
    )
    assertEquals(PlaceRepo.listPeriods(ds, Some("zzr_oth"), None, None, None).map(_.id), Vector("zzr_other"))
    assert(PlaceRepo.listPeriods(ds, Some("ZZ_R"), None, None, None).isEmpty, "`_` must not match any character")
