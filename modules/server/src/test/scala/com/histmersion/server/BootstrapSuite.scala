package com.histmersion.server

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.db.{Db, Migrate, PeriodRepo}
import com.histmersion.util.jobs.JobRepo

import com.augustnagro.magnum.*
import java.nio.file.{Files, Path}

/** Named periods and the first-run job against a real Postgres (#719, #720); skipped without HISTMERSION_TEST_DB_URL.
  * The network is a function the test supplies, and the data directory is a temporary one holding the REAL committed
  * period seed — never the 16 000-place gazetteer, whose import re-materialises `finding_places` for the whole test
  * database other suites assert on.
  */
class BootstrapSuite extends com.histmersion.db.DbSuite:
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val base = AppConfig.load()
  private lazy val ds =
    val d = testPool(base.db.copy(url = testDb.get))
    Migrate.run(d); d

  private val repoRoot = Path.of(sys.props.getOrElse("histmersion.repoRoot", "."))
  private lazy val seedText = Files.readString(repoRoot.resolve("db/periods/periodo-seed.json"))
  private lazy val seedIds = PeriodO.parse(seedText).toOption.get.authorities.flatMap(a => a.periods.map(_.id))
  private val dump = scala.io.Source.fromResource("periods/periodo_dump.json").mkString
  private val fixtureIds = List("zzqauthiron", "zzqauthlba", "zzqauthbronze", "zzqauth", "zzqother01", "zzqother")

  /** A data directory with ONLY the period seed in it (no `places/`). */
  private def dataDir(): Path =
    val d = Files.createTempDirectory("histmersion-init")
    Files.createDirectories(d.resolve("periods"))
    Files.writeString(d.resolve("periods/periodo-seed.json"), seedText)
    d

  private def bootstrap(dir: Path, net: String => Either[String, String] = _ => Right(dump)) =
    val cfg = base.copy(db = base.db.copy(url = testDb.get), init = base.init.copy(dataDir = dir.toString))
    Bootstrap(Deps(cfg, ds), net)

  private def clean(): Unit =
    (fixtureIds ++ seedIds.reverse).foreach(id => transact(ds)(sql"DELETE FROM periods WHERE id = $id".update.run()))
    transact(ds)(sql"DELETE FROM seed_versions WHERE name = ${Bootstrap.PeriodSeed}".update.run())

  override def beforeAll(): Unit = if testDb.nonEmpty then clean()
  override def afterSuite(): Unit = if testDb.nonEmpty then clean()

  test("periods seed imports the bundled presets: the BSA's Bronze Age → Minoan → phases"):
    val r = bootstrap(dataDir()).seedPeriods().fold(e => fail(e.message), identity)
    assertEquals(r.periods, seedIds.size)
    assertEquals(r.roots, 0)
    // #771: the seed's two EAMENA Palaeolithic periods start in 14,000,000 BC — reported, not stored
    assertEquals(r.skipped.map(_.id), List("p0m64tdgpvd", "p0m64tdp282"))
    assertEquals(r.skippedCount, 2)
    assertEquals(PeriodRepo.get(ds, "p0m64tdp282"), None)
    val b = bootstrap(dataDir())
    val lm = b.period("p0ds9qjh4dh").fold(e => fail(e.message), identity) // Late Minoan (BSA)
    assertEquals(lm.period.period, Period(-1700, -1050))
    assertEquals(lm.parent.map(_.label), Some("Bronze Age Minoan"))
    assert(lm.children.map(_.label).contains("Late Minoan I"), lm.children.toString)
    assert(lm.period.authority.exists(_.startsWith("PeriodO p0ds9qjh4dh · The British School at Athens")))
    // the web timeline's presets are the seed's, each under its region heading
    val seed = PeriodO.parse(seedText).toOption.get
    assertEquals(r.presets, seed.presets.size)
    val aegean = PeriodRepo.get(ds, "p0ds9qjq7b2").fold(fail("no Bronze Age (Greece)"))(identity)
    assertEquals(
      (aegean.label, aegean.region, aegean.preset),
      ("Bronze Age", Some("Greece"), Some(PresetRegion.Aegean))
    )
    // ROAD's Eurasia-wide Bronze Age is still a period, but not a preset
    assertEquals(PeriodRepo.get(ds, "p0s2rwkwbsz").map(p => (p.label, p.preset)), Some(("Bronze Age", None)))

  test("a second seed is idempotent"):
    val before = PeriodRepo.count(ds)
    bootstrap(dataDir()).seedPeriods().fold(e => fail(e.message), identity)
    assertEquals(PeriodRepo.count(ds), before)

  // the presets of an install seeded before V062 were the parentless rows, under ROAD's umbrella
  test("a newer bundled seed re-applies itself on start: old nesting undone, the presets become the seed's"):
    val b = bootstrap(dataDir())
    b.seedPeriods().fold(e => fail(e.message), identity)
    assertEquals(b.plan(fetch = false, onlyEmpty = true).periodPresets, false) // current: nothing to do
    // an older install: version 1, the BSA's Bronze Age under ROAD's, a preset the seed does not name
    PeriodRepo.setSeedVersion(ds, Bootstrap.PeriodSeed, 1)
    transact(ds):
      val _ = sql"UPDATE periods SET parent_id = 'p0s2rwkwbsz', preset = NULL WHERE id = 'p0ds9qjq7b2'".update.run()
      val _ = sql"UPDATE periods SET preset = 'broad' WHERE id = 'p0s2rwkwbsz'".update.run()
    assertEquals(b.plan(fetch = false, onlyEmpty = true).periodPresets, true)
    b.seedPeriods().fold(e => fail(e.message), identity)
    val bsa = PeriodRepo.get(ds, "p0ds9qjq7b2").get
    assertEquals((bsa.parentId, bsa.preset), (None, Some(PresetRegion.Aegean)))
    assertEquals(PeriodRepo.get(ds, "p0s2rwkwbsz").flatMap(_.preset), None)
    assertEquals(PeriodRepo.seedVersion(ds, Bootstrap.PeriodSeed), PeriodO.parse(seedText).toOption.get.version)
    assertEquals(b.plan(fetch = false, onlyEmpty = true).periodPresets, false)

  test("a period fetch never takes a seed preset off the menu"):
    val b = bootstrap(dataDir())
    b.seedPeriods().fold(e => fail(e.message), identity)
    PeriodRepo.upsert(ds, List(PeriodRepo.get(ds, "p0ds9qjq7b2").get.copy(preset = None)))
    assertEquals(PeriodRepo.get(ds, "p0ds9qjq7b2").flatMap(_.preset), Some(PresetRegion.Aegean))

  test("a missing seed is an error naming where it looked"):
    val r = bootstrap(Files.createTempDirectory("histmersion-empty")).seedPeriods()
    assert(r.left.exists(_.message.contains("no period seed at")), r.toString)

  test("periods fetch periodo: the named collection only, under one authority node; re-runnable"):
    var asked = List.empty[String] // a test double is the one place a var is the simplest thing
    val b = bootstrap(dataDir(), url => { asked = asked :+ url; Right(dump) })
    val r = b.fetchPeriods("periodo", List("zzqauth")).fold(e => fail(e.message), identity)
    assertEquals(asked, List(base.periods.periodoUrl))
    assertEquals(r.periods, 4) // 3 periods + the authority node
    assertEquals(r.roots, 1)
    assertEquals(PeriodRepo.get(ds, "zzqother01"), None)
    val node = b.period("zzqauth").fold(e => fail(e.message), identity)
    assertEquals(node.period.label, "Zzq test chronology")
    assertEquals(node.period.period, Period(-3000, -500))
    assertEquals(node.children.map(_.id).toSet, Set("zzqauthbronze", "zzqauthiron"))
    assertEquals(PeriodRepo.get(ds, "zzqauthlba").flatMap(_.parentId), Some("zzqauthbronze"))
    // somebody moves a period; a re-fetch leaves it where it is
    transact(ds)(sql"UPDATE periods SET parent_id = NULL WHERE id = 'zzqauthiron'".update.run())
    b.fetchPeriods("periodo", List("zzqauth")).fold(e => fail(e.message), identity)
    assertEquals(PeriodRepo.get(ds, "zzqauthiron").flatMap(_.parentId), None)

  test("periods fetch: an unknown source, a missing collection and a dead network are errors"):
    val b = bootstrap(dataDir())
    assert(b.fetchPeriods("chronontology", Nil).left.exists(_.message.contains("unknown period source")))
    assert(b.fetchPeriods("periodo", List("zzqnope")).left.exists(_.message.contains("zzqnope")))
    val dead = bootstrap(dataDir(), _ => Left("HTTP 503"))
    assert(dead.fetchPeriods("periodo", List("zzqauth")).left.exists(_.message.contains("503")))

  test("init runs as a job: every step is attempted, a missing gazetteer seed is reported"):
    val b = bootstrap(dataDir())
    val job = b.init(fetch = false).fold(e => fail(e.message), identity)
    assertEquals(job.kind, Bootstrap.JobKind)
    val end = Iterator
      .continually { Thread.sleep(100); JobRepo.get(ds, job.id).get }
      .take(300)
      .find(_.isTerminal)
      .getOrElse(fail("the init job did not finish"))
    assertEquals(end.state, JobState.Failed)
    assert(end.error.exists(_.contains("gazetteer: no gazetteer seed at")), end.toString)
    assert(end.progress.exists(_.contains("period presets:")), end.toString) // the periods still went in
    assert(JobRepo.list(ds, None, 50).exists(_.id == job.id), "`histmersion jobs` lists it")
    // #770: the job says what it imports from the start; #771: and which period definitions it left out
    assertEquals(job.detail.flatMap(_.init), Some(InitPlan(gazetteer = true, periodPresets = true)))
    assertEquals(end.detail.flatMap(_.init), job.detail.flatMap(_.init))
    assertEquals(end.detail.map(_.skippedPeriodCount), Some(2))
    assertEquals(end.detail.toList.flatMap(_.skippedPeriods.map(_.id)), List("p0m64tdgpvd", "p0m64tdp282"))
    assert(end.progress.exists(_.contains("2 skipped")), end.toString)
