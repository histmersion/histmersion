package com.histmersion.db

import com.histmersion.util.database.Transactor

import com.histmersion.domain.research.articles.ArticleFixtures

import com.histmersion.domain.library.sources.SourceFixtures

import com.histmersion.domain.catalogue.places.PlaceFixtures

import com.histmersion.domain.catalogue.events.*
import com.histmersion.domain.catalogue.evidence.*

import com.histmersion.util.jobs.JobRepo

import com.histmersion.api.*
import com.histmersion.files.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.GoalPlans.{LinkKind, ScheduleKind}

import com.augustnagro.magnum.*
import com.histmersion.util.data.exchange.TestExchange
import java.nio.file.{Files, Path}
import javax.sql.DataSource
import com.histmersion.values.{IssueKind, PlaceKind, SourceStatus}
import io.scalaland.chimney.dsl.*
import com.histmersion.util.data.exchange.{BundleFormat, BundleReader}
import com.histmersion.util.data.exchange.{BundleExport, MergeRepo}
import com.histmersion.domain.automation.goals.{GoalLinkRepo, GoalRepo}
import com.histmersion.domain.automation.managers.ManagerRepo
import com.histmersion.util.data.exchange.{BundleImport, MergeState}
import com.histmersion.util.database.Sql

/** What a bundle carries and a merge decides beyond M5, against real Postgres (docs/research-merge-import.md §7.8): a
  * collection's own places and named periods (V052) and not its seeds, a goal's plan, links and schedules (#881, V054),
  * old article slugs, events matched across two ids, label renames that keep an alias (V053), and what
  * `merge list | show | ledger` read back. Two databases of their own per case, as `MergeSuite`. Skipped without
  * HISTMERSION_TEST_DB_URL.
  */
class MergeExtrasDbSuite extends com.histmersion.db.DbSuite with Bounded:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  private lazy val cfg = AppConfig.load()
  private lazy val merge = cfg.merge.transformInto[MergeSettings]
  private lazy val admin = testPool(cfg.db.copy(url = testDb.get))
  private val tmp = Files.createTempDirectory("histmersion-merge-extras")

  private def exec(ds: DataSource, stmt: String): Unit =
    val c = ds.getConnection
    try {
      val st = c.createStatement();
      try { val _ = st.execute(stmt) }
      finally st.close()
    } finally c.close()

  private def fresh(n: String): DataSource =
    val ds = scratch(n)
    ds

  // every database a test makes: a copy of one migrated once (#3765)
  private lazy val scratch = ScratchDatabases("histmersion_mx", cfg.db.copy(url = testDb.get))

  override def afterSuite(): Unit =
    if testDb.isDefined then scratch.dropAll()

  private def spool(n: String): Path = Files.createDirectories(tmp.resolve(n))

  private def exportOf(ds: DataSource, originals: Path): Path =
    val out = java.io.ByteArrayOutputStream()
    BundleExport
      .write(ds, out, None, com.histmersion.util.storage.ContentDirectory(originals), TestExchange.kinds)
      .fold(e => fail(e), identity)
    val f = Files.createTempFile(tmp, "bundle-", ".hmx")
    Files.write(f, out.toByteArray)

  private def entitiesOf(bundle: Path): BundleEntities =
    MergeState.withFile(bundle)(BundleReader.read(_, hashBlobs = false)).entities

  private def planIn(
      ds: DataSource,
      bundle: Path,
      originals: Path,
      res: MergeResolutions = MergeResolutions()
  ): MergePlan =
    MergeState
      .planFile(
        ds,
        bundle,
        res,
        merge,
        com.histmersion.util.storage.ContentDirectory(originals),
        cfg.limits.chunkChars,
        TestExchange.kinds,
        TestExchange.rules
      )
      .fold(e => fail(e), identity)

  private def importInto(ds: DataSource, bundle: Path, originals: Path): BundleImportResult =
    com.histmersion.util.data.exchange.BundleImporter
      .run(
        ds,
        com.histmersion.util.storage.ByteSource.file(bundle),
        BundleFormat.sha256Hex(Files.readAllBytes(bundle)),
        BundleImport.Options(false, cfg.limits.chunkChars, cfg.places.pointToleranceKm, SourceFixtures.referrers),
        com.histmersion.util.storage.ContentDirectory(originals),
        MergeResolutions(),
        merge,
        TestExchange.kinds,
        TestExchange.rules
      )
      .fold(e => fail(e), identity)

  private def applyIn(
      ds: DataSource,
      bundle: Path,
      originals: Path,
      res: MergeResolutions = MergeResolutions()
  ): MergeApplyResult =
    val hash = planIn(ds, bundle, originals, res).planHash
    com.histmersion.util.data.exchange.BundleImporter
      .apply(
        ds,
        com.histmersion.util.storage.ByteSource.file(bundle),
        BundleFormat.sha256Hex(Files.readAllBytes(bundle)),
        BundleImport
          .Options(dryRun = false, cfg.limits.chunkChars, cfg.places.pointToleranceKm, SourceFixtures.referrers),
        com.histmersion.util.storage.ContentDirectory(originals),
        res,
        merge,
        hash,
        () => Right("stub.dump"),
        TestExchange.kinds,
        TestExchange.rules,
        TestExchange.followUps
      )
      .fold(e => fail(e), identity)

  private def count(ds: DataSource, q: String): Int = connect(ds)(Sql.raw(q).frag.query[Int].run().head)
  private def text(ds: DataSource, q: String): Option[String] =
    connect(ds)(Sql.raw(q).frag.query[Option[String]].run().headOption.flatten)

  private def site(id: String, label: String, lon: Double, lat: Double): PlaceInput =
    PlaceInput(
      Place(
        id,
        label,
        PlaceKind.Site,
        Some(s"""{"type":"Point","coordinates":[$lon,$lat]}"""),
        Some(Period(-1650, -1180))
      ),
      List(PlaceName(label))
    )

  test("places and periods: the collection's own travel and import; the seeds do not; the dataset marks which"):
    val (work, home) = (fresh("pw"), fresh("ph"))
    val (sw, sh) = (spool("pw"), spool("ph"))
    val _ = PlaceFixtures.upsertPlaces(work, List(site("zz_seed_site", "A seed site", 30.0, 39.0)), Dataset.Seed)
    val _ = PlaceFixtures.upsertPlaces(work, List(site("zz_my_site", "My site", 34.6, 40.0)), Dataset.Local)
    val _ = connect(work)(
      sql"""INSERT INTO periods (id, label, authority, period, dataset)
            VALUES ('zz_my_period', 'My period', 'me', '[-1400,-1199)'::int8range, 'local')""".update.run()
    )
    EventFixtures.put(
      work,
      "zz_at_my_site",
      EventPut("At my site", "battle", TimeSpan(Some(-1300), None, None, Some(-1290)), placeId = Some("zz_my_site"))
    )
    val bundle = exportOf(work, sw)
    val es = entitiesOf(bundle)
    assertEquals(es.places.map(_.id), List("zz_my_site"))
    assertEquals(es.periods.map(_.id), List("zz_my_period"))
    assertEquals(es.places.head.names.map(_.name), List("My site"))
    // home has only the seeds it imports itself — the event's place comes with the bundle
    val _ = PlaceFixtures.upsertPlaces(home, List(site("zz_seed_site", "A seed site", 30.0, 39.0)), Dataset.Seed)
    val r = importInto(home, bundle, sh)
    assert(r.applied, r.problems.mkString("\n"))
    assertEquals(text(home, "SELECT dataset FROM places WHERE id = 'zz_my_site'"), Some("local"))
    assertEquals(text(home, "SELECT place_id FROM events WHERE id = 'zz_at_my_site'"), Some("zz_my_site"))
    assertEquals(text(home, "SELECT dataset FROM periods WHERE id = 'zz_my_period'"), Some("local"))
    // the place is home's own now: it travels on, and the same bundle again changes nothing
    assertEquals(entitiesOf(exportOf(home, sh)).places.map(_.id), List("zz_my_site"))
    assertEquals(importInto(home, bundle, sh).changed, 0)
    // a local place's names move its clock; deleting it leaves a tombstone (a seed's does not)
    val before = text(home, "SELECT updated_at::text FROM places WHERE id = 'zz_my_site'")
    exec(home, "INSERT INTO place_names (place_id, name, lang) VALUES ('zz_my_site', 'Another name', '')")
    assertNotEquals(text(home, "SELECT updated_at::text FROM places WHERE id = 'zz_my_site'"), before)
    exec(home, "DELETE FROM places WHERE id IN ('zz_my_site', 'zz_seed_site')")
    assertEquals(count(home, "SELECT count(*)::int FROM tombstones WHERE kind = 'place'"), 1)

  test("places: one id with two geometries asks, and keep-right takes the bundle's; one place under another id folds"):
    val (work, home) = (fresh("qw"), fresh("qh"))
    val (sw, sh) = (spool("qw"), spool("qh"))
    val _ = PlaceFixtures.upsertPlaces(
      work,
      List(site("zz_ugarit", "Ugarit", 35.78, 35.60), site("work_troy", "Troy", 26.2389, 39.9575)),
      Dataset.Local
    )
    EventFixtures.put(
      work,
      "zz_troy_burns",
      EventPut("Troy burns", "destruction", TimeSpan(Some(-1190), None, None, Some(-1180)), placeId = Some("work_troy"))
    )
    val _ = PlaceFixtures.upsertPlaces(home, List(site("zz_ugarit", "Ugarit", 35.70, 35.50)), Dataset.Local)
    val _ = PlaceFixtures.upsertPlaces(home, List(site("pleiades_550595", "Troy", 26.2390, 39.9576)), Dataset.Seed)
    val bundle = exportOf(work, sw)
    val p = planIn(home, bundle, sh)
    val q = p.issues.find(_.kind == IssueKind.PlaceDiffers).getOrElse(fail(p.issues.mkString("\n")))
    // the review screen gets both geometries as GeoJSON
    assert(q.detail.flatMap(_.place).flatMap(_.rightGeometry).exists(_.contains("\"Point\"")), q.detail.toString)
    assert(p.info.exists(_.contains("is the same place")), p.info.mkString("\n"))
    val a = applyIn(home, bundle, sh, MergeResolutions(List(MergeResolution(q.id, "keep-right"))))
    assert(a.applied, (a.problems ++ a.issues.map(_.title)).mkString("\n"))
    assertEquals(text(home, "SELECT ST_AsText(geom) FROM places WHERE id = 'zz_ugarit'"), Some("POINT(35.78 35.6)"))
    assertEquals(text(home, "SELECT place_id FROM events WHERE id = 'zz_troy_burns'"), Some("pleiades_550595"))
    assertEquals(count(home, "SELECT count(*)::int FROM places WHERE id = 'work_troy'"), 0)
    assertEquals(a.replanChanges, Some(0))

  test("goals (#881): the plan, its approval time, the run cap, links and schedules travel; ours are kept on a merge"):
    val (work, home) = (fresh("gw"), fresh("gh"))
    val (sw, sh) = (spool("gw"), spool("gh"))
    SourceFixtures.upsert(
      work,
      Source(
        "zzgoal2020_src",
        "Linked",
        List("A"),
        None,
        Some(2020),
        None,
        SourceStatus.Current,
        Nil,
        Nil,
        Nil,
        None,
        None,
        None,
        None,
        None,
        None
      ),
      cfg.limits.chunkChars
    )
    transact(work)(GoalRepo.put("zz_goal", GoalPut("A goal")))
    transact(work)(GoalLinkRepo.setPlan("zz_goal", """{"goal":"zz_goal","rationale":"work's plan"}"""))
    transact(work)(GoalLinkRepo.setBudget("zz_goal", Some(BigDecimal("2.5"))))
    assert(transact(work)(GoalLinkRepo.link("zz_goal", LinkKind.Source, "zzgoal2020_src", Some("why"))))
    assert(transact(work)(GoalLinkRepo.link("zz_goal", LinkKind.Query, "hittite storm god", None)))
    transact(work)(
      GoalLinkRepo.setSchedule("zz_goal", ScheduleKind.Currency, "@weekly", true, None, Some(java.time.Instant.now()))
    )
    val bundle = exportOf(work, sw)
    val g = entitiesOf(bundle).goals.find(_.slug == "zz_goal").getOrElse(fail("no goal"))
    assert(g.planApprovedAt.isDefined && g.budgetUsd.contains(2.5), g.toString)
    assertEquals(g.links.map(_.map(_.kind)), Some(List("query", "source")))
    assertEquals(g.schedules.map(_.map(_.cron)), Some(List("@weekly")))
    val r = importInto(home, bundle, sh)
    assert(r.applied, r.problems.mkString("\n"))
    assertEquals(
      connect(home)(GoalLinkRepo.planAndBudget("zz_goal")).flatMap(_._1).exists(_.contains("work's plan")),
      true
    )
    assertEquals(Transactor(home).read(GoalLinkRepo.refsIn("zz_goal", LinkKind.Source)), List("zzgoal2020_src"))
    val s =
      Transactor(home).read(GoalLinkRepo.schedule("zz_goal", ScheduleKind.Currency)).getOrElse(fail("no schedule"))
    // brought in, it never fires here before a person saves it
    assertEquals((s.cron, s.nextRunAt), ("@weekly", None))
    assert(s.lastNote.exists(_.contains("save it once")), s.toString)
    assertEquals(importInto(home, bundle, sh).changed, 0)
    // changed on both sides afterwards: one goal; home's schedule stays, the links of both
    transact(home)(GoalLinkRepo.setSchedule("zz_goal", ScheduleKind.Currency, "@daily", true, None, None))
    assert(transact(home)(GoalLinkRepo.link("zz_goal", LinkKind.Query, "ahhiyawa", None)))
    assert(transact(work)(GoalLinkRepo.link("zz_goal", LinkKind.Query, "mycenae", None)))
    val again = exportOf(work, sw)
    val p = planIn(home, again, sh)
    assert(p.info.exists(_.contains("its currency schedule differs")), p.info.mkString("\n"))
    val a = applyIn(home, again, sh)
    assert(a.applied, (a.problems ++ a.issues.map(_.title)).mkString("\n"))
    assertEquals(
      Transactor(home).read(GoalLinkRepo.refsIn("zz_goal", LinkKind.Query)).toSet,
      Set("hittite storm god", "ahhiyawa", "mycenae")
    )
    assertEquals(
      Transactor(home).read(GoalLinkRepo.schedule("zz_goal", ScheduleKind.Currency)).map(_.cron),
      Some("@daily")
    )
    assertEquals(a.replanChanges, Some(0))

  test("goals (phase 3): a watcher's sweeps travel as the goal's schedules (cron, budget); its state stays home"):
    val (work, sw) = (fresh("ww"), spool("ww"))
    transact(work)(GoalRepo.put("zz_goal", GoalPut("A watched goal")))
    assert(
      transact(work)(
        ManagerRepo.create(
          "watch:zz_goal",
          com.histmersion.api.Managers.Kind.Watch,
          Some("zz_goal"),
          com.histmersion.api.Managers.State.Paused,
          com.histmersion.api.Managers.ManagerConfig(sweeps = Some(List("acquisition")), budgetUsd = Some(0.3)),
          Some("@weekly"),
          None
        )
      )
    )
    val g = entitiesOf(exportOf(work, sw)).goals.find(_.slug == "zz_goal").getOrElse(fail("no goal"))
    assertEquals(
      g.schedules.map(_.map(x => (x.kind, x.cron, x.enabled, x.budgetUsd))),
      Some(List(("acquisition", "@weekly", true, Some(0.3))))
    )

  test("old article slugs travel; events under two ids that are one fold; merge list/show/ledger read it back"):
    val (work, home) = (fresh("aw"), fresh("ah"))
    val (sw, sh) = (spool("aw"), spool("ah"))
    val _ = ArticleFixtures.put(work, Article("zz_first_slug", "An article", "concept", "Text."))
    val _ = ArticleFixtures.rename(work, "zz_first_slug", "zz_second_slug").fold(e => fail(e.message), identity)
    val _ = PlaceFixtures.upsertPlaces(work, List(site("zz_kadesh", "Kadesh", 36.51, 34.56)), Dataset.Seed)
    val _ = PlaceFixtures.upsertPlaces(home, List(site("zz_kadesh", "Kadesh", 36.51, 34.56)), Dataset.Seed)
    EventFixtures.put(
      work,
      "work_kadesh",
      EventPut(
        "Battle of Kadesh",
        "battle",
        TimeSpan(Some(-1274), None, None, Some(-1274)),
        placeId = Some("zz_kadesh"),
        description = Some("work's account")
      )
    )
    EventFixtures.put(
      home,
      "kadesh_1274",
      EventPut(
        "Battle of Kadesh",
        "battle",
        TimeSpan(Some(-1275), None, None, Some(-1274)),
        placeId = Some("zz_kadesh")
      )
    )
    val bundle = exportOf(work, sw)
    assertEquals(entitiesOf(bundle).articleAliases.map(_.alias), List("zz_first_slug"))
    val a = applyIn(home, bundle, sh)
    assert(a.applied, (a.problems ++ a.issues.map(_.title)).mkString("\n"))
    assertEquals(a.tally.eventsMerged, 1)
    assertEquals(count(home, "SELECT count(*)::int FROM events"), 1)
    assertEquals(text(home, "SELECT description FROM events WHERE id = 'kadesh_1274'"), Some("work's account"))
    assertEquals(ArticleFixtures.view(home, "zz_first_slug").map(_.article.slug), Some("zz_second_slug"))
    assertEquals(a.replanChanges, Some(0))
    // what the merge left, read back
    val list = Transactor(home).read(MergeRepo.list(10))
    assertEquals(list.map(_.planHash.isDefined), List(true))
    val m = list.head
    assert(m.redirects >= 1 && m.ledgerRows >= 2, m.toString)
    val d = Transactor(home).read(MergeRepo.detail(m.id.toString.take(8))).fold(e => fail(e.message), identity)
    assertEquals(d.applied.map(_.tally.eventsMerged), Some(1))
    assertEquals(d.merge.backup, Some("stub.dump"))
    val evUid = connect(home)(sql"SELECT uid FROM events WHERE id = 'kadesh_1274'".query[java.util.UUID].run().head)
    val led =
      Transactor(home)
        .read(MergeRepo.ledger(Some(evUid.toString), None, 50, TestExchange.rules))
        .fold(e => fail(e.message), identity)
    assertEquals(led.entries.map(_.label), List(Some("kadesh_1274")))
    assertEquals(led.redirects.map(_.uid), List(evUid))
    assert(Transactor(home).read(MergeRepo.detail("zzzz")).isLeft)

  test("label renames keep the old label as an alias: refused when taken, dropped when renamed back"):
    val ds = fresh("rn")
    EventFixtures.put(ds, "zz_ev_a", EventPut("E", "battle"))
    EventFixtures.put(ds, "zz_ev_other", EventPut("Other", "battle"))
    transact(ds)(GoalRepo.put("zz_goal_a", GoalPut("G")))
    assert(EventFixtures.rename(ds, "zz_ev_a", "zz_ev_b").isRight)
    assertEquals(EventFixtures.renamedTo(ds, "zz_ev_a"), Some("zz_ev_b"))
    // an old id of one event is not free for another
    assert(EventFixtures.rename(ds, "zz_ev_other", "zz_ev_a").left.exists(_.message.contains("old id of 'zz_ev_b'")))
    assert(EventFixtures.rename(ds, "zz_ev_b", "zz_ev_a").isRight)
    assertEquals(EventFixtures.renamedTo(ds, "zz_ev_a"), None)
    assertEquals(EventFixtures.renamedTo(ds, "zz_ev_b"), Some("zz_ev_a"))
    assert(Transactor(ds).transact(GoalRepo.renameIn("zz_goal_a", "zz_goal_b")).isRight)
    assertEquals(Transactor(ds).read(GoalRepo.renamedTo("zz_goal_a")), Some("zz_goal_b"))
    assert(Transactor(ds).transact(GoalRepo.renameIn("zz_goal_b", "zz_goal_b")).isLeft)

  // #888: a place renamed over there (a new id under the same uid) kept its old id here, and the
  // bundle's events that name the new id pointed at nothing
  test("a place renamed over there is followed: the new id here, the old one an alias, events and names follow"):
    val (work, home) = (fresh("prw"), fresh("prh"))
    val (sw, sh) = (spool("prw"), spool("prh"))
    val _ = PlaceFixtures.upsertPlaces(work, List(site("zz_old_site", "My site", 34.6, 40.0)), Dataset.Local)
    EventFixtures.put(
      work,
      "zz_at_site",
      EventPut("At my site", "battle", TimeSpan(Some(-1300), None, None, Some(-1290)), placeId = Some("zz_old_site"))
    )
    val first = importInto(home, exportOf(work, sw), sh)
    assert(first.applied, first.problems.mkString("\n"))
    // work renames the place: its event follows by key (V055)
    assert(PlaceFixtures.rename(work, "zz_old_site", "zz_new_site").isRight)
    assertEquals(text(work, "SELECT place_id FROM events WHERE id = 'zz_at_site'"), Some("zz_new_site"))
    val bundle = exportOf(work, sw)
    val p = planIn(home, bundle, sh)
    assert(p.info.exists(_.contains("renamed zz_new_site in the bundle")), p.info.mkString("\n"))
    assertEquals(p.problems, Nil)
    val r = importInto(home, bundle, sh)
    assert(r.applied, r.problems.mkString("\n"))
    assertEquals(count(home, "SELECT count(*)::int FROM places WHERE id = 'zz_old_site'"), 0)
    assertEquals(text(home, "SELECT place_id FROM place_names WHERE name = 'My site'"), Some("zz_new_site"))
    assertEquals(text(home, "SELECT place_id FROM events WHERE id = 'zz_at_site'"), Some("zz_new_site"))
    assertEquals(PlaceFixtures.renamedTo(home, "zz_old_site"), Some("zz_new_site"))
    // the same bundle again changes nothing
    assertEquals(importInto(home, bundle, sh).changed, 0)

  test("a place renamed over there onto an id another place has here keeps ours; the bundle's events point at it"):
    val (work, home) = (fresh("pkw"), fresh("pkh"))
    val (sw, sh) = (spool("pkw"), spool("pkh"))
    val _ = PlaceFixtures.upsertPlaces(work, List(site("zz_old_site", "My site", 34.6, 40.0)), Dataset.Local)
    val first = importInto(home, exportOf(work, sw), sh)
    assert(first.applied, first.problems.mkString("\n"))
    val _ = PlaceFixtures.upsertPlaces(home, List(site("zz_taken", "A seed site", 30.0, 39.0)), Dataset.Seed)
    assert(PlaceFixtures.rename(work, "zz_old_site", "zz_taken").isRight)
    EventFixtures.put(
      work,
      "zz_at_taken",
      EventPut(
        "At the renamed site",
        "battle",
        TimeSpan(Some(-1300), None, None, Some(-1290)),
        placeId = Some("zz_taken")
      )
    )
    val bundle = exportOf(work, sw)
    val p = planIn(home, bundle, sh)
    assert(!p.issues.exists(_.kind == IssueKind.PlaceDiffers), p.issues.map(_.title).mkString("\n"))
    assert(p.info.exists(_.contains("it keeps its id")), p.info.mkString("\n"))
    val r = importInto(home, bundle, sh)
    assert(r.applied, r.problems.mkString("\n"))
    assertEquals(text(home, "SELECT label FROM places WHERE id = 'zz_taken'"), Some("A seed site"))
    assertEquals(count(home, "SELECT count(*)::int FROM places WHERE id = 'zz_old_site'"), 1)
    assertEquals(text(home, "SELECT place_id FROM events WHERE id = 'zz_at_taken'"), Some("zz_old_site"))

  // #890: the old labels of events, goals and places travel as the old article slugs do
  test("old event ids, goal slugs and place ids travel in a bundle, are written here, and keep resolving"):
    val (work, home) = (fresh("alw"), fresh("alh"))
    val (sw, sh) = (spool("alw"), spool("alh"))
    val _ = PlaceFixtures.upsertPlaces(work, List(site("zz_pl_before", "My site", 34.6, 40.0)), Dataset.Local)
    EventFixtures.put(work, "zz_ev_before", EventPut("E", "battle"))
    transact(work)(GoalRepo.put("zz_goal_before", GoalPut("G")))
    assert(PlaceFixtures.rename(work, "zz_pl_before", "zz_pl_after").isRight)
    assert(EventFixtures.rename(work, "zz_ev_before", "zz_ev_after").isRight)
    assert(Transactor(work).transact(GoalRepo.renameIn("zz_goal_before", "zz_goal_after")).isRight)
    val bundle = exportOf(work, sw)
    val es = entitiesOf(bundle)
    assertEquals(es.eventAliases.map(_.alias), List("zz_ev_before"))
    assertEquals(es.goalAliases.map(_.alias), List("zz_goal_before"))
    assertEquals(es.placeAliases.map(_.alias), List("zz_pl_before"))
    val r = importInto(home, bundle, sh)
    assert(r.applied, r.problems.mkString("\n"))
    assertEquals(EventFixtures.renamedTo(home, "zz_ev_before"), Some("zz_ev_after"))
    assertEquals(Transactor(home).read(GoalRepo.renamedTo("zz_goal_before")), Some("zz_goal_after"))
    assertEquals(PlaceFixtures.renamedTo(home, "zz_pl_before"), Some("zz_pl_after"))
    // a filter on the OLD id is the filter on the place it names now, labelled with that place
    val scope = PlaceFixtures.scopeFor(home, List("zz_pl_before"), None)
    assertEquals(scope.idsFor("zz_pl_before"), Some(List("zz_pl_after")))
    assertEquals(scope.labelFor("zz_pl_before"), "My site")
    // the same bundle again changes nothing, and an alias naming another record here is not written
    assertEquals(importInto(home, bundle, sh).changed, 0)
    EventFixtures.put(work, "zz_ev_x", EventPut("X", "battle"))
    assert(EventFixtures.rename(work, "zz_ev_x", "zz_ev_y").isRight)
    EventFixtures.put(home, "zz_ev_x", EventPut("Home's own X", "battle"))
    val second = importInto(home, exportOf(work, sw), sh)
    assert(second.applied, second.problems.mkString("\n"))
    assertEquals(EventFixtures.renamedTo(home, "zz_ev_x"), None) // a live id here, never an alias

  test("place renames keep the old id as an alias: refused when taken, dropped when renamed back"):
    val ds = fresh("prn")
    val _ = PlaceFixtures.upsertPlaces(
      ds,
      List(site("zz_pl_a", "A", 30.0, 39.0), site("zz_pl_other", "Other", 31.0, 39.0)),
      Dataset.Local
    )
    assert(PlaceFixtures.rename(ds, "zz_pl_a", "zz_pl_b").isRight)
    assertEquals(PlaceFixtures.renamedTo(ds, "zz_pl_a"), Some("zz_pl_b"))
    assertEquals(PlaceFixtures.get(ds, "zz_pl_b").map(_.place.label), Some("A"))
    assert(PlaceFixtures.rename(ds, "zz_pl_other", "zz_pl_a").left.exists(_.message.contains("old id of 'zz_pl_b'")))
    assert(PlaceFixtures.rename(ds, "zz_pl_b", "zz_pl_other").left.exists(_.message.contains("already the id")))
    assert(PlaceFixtures.rename(ds, "zz_pl_b", "zz_pl_a").isRight)
    assertEquals(PlaceFixtures.renamedTo(ds, "zz_pl_a"), None)
    assertEquals(PlaceFixtures.renamedTo(ds, "zz_pl_b"), Some("zz_pl_a"))

  // #888: the apply jobs were kept in memory and lost on a restart
  test("merge apply jobs are rows: running, then done with the report; a restart turns a running one interrupted"):
    val ds = fresh("job")
    val res = MergeApplyResult(
      java.util.UUID.randomUUID(),
      BundleInstance(java.util.UUID.randomUUID(), Some("work")),
      applied = true,
      "abc123",
      notes = List("a note")
    )
    val a = Transactor(ds).transact(JobRepo.startMergeApply("a" * 64, "abc123"))
    assert(Transactor(ds).read(JobRepo.mergeApplyRunning))
    assertEquals(
      Transactor(ds).read(JobRepo.mergeApply(a)).map(j => (j.state, j.bundle, j.planHash)),
      Some((MergeJobState.Running, "a" * 64, "abc123"))
    )
    Transactor(ds).transact(JobRepo.finishMergeApply(a, Right(res)))
    assert(!Transactor(ds).read(JobRepo.mergeApplyRunning))
    assertEquals(
      Transactor(ds).read(JobRepo.mergeApply(a)).map(j => (j.state, j.result)),
      Some((MergeJobState.Done, Some(res)))
    )
    val b = Transactor(ds).transact(JobRepo.startMergeApply("b" * 64, "def456"))
    assertEquals(Transactor(ds).transact(JobRepo.failInterrupted()), 1) // the startup reconciliation
    assertEquals(Transactor(ds).read(JobRepo.mergeApply(b)).map(_.state), Some(MergeJobState.Interrupted))
    // the thread that outlived nothing cannot overwrite it; a crash is failed with its reason
    Transactor(ds).transact(JobRepo.finishMergeApply(b, Right(res)))
    assertEquals(Transactor(ds).read(JobRepo.mergeApply(b)).map(_.state), Some(MergeJobState.Interrupted))
    val c = Transactor(ds).transact(JobRepo.startMergeApply("c" * 64, "0"))
    Transactor(ds).transact(JobRepo.finishMergeApply(c, Left("merge apply crashed: boom")))
    assertEquals(
      Transactor(ds).read(JobRepo.mergeApply(c)).map(j => (j.state, j.error)),
      Some((MergeJobState.Failed, Some("merge apply crashed: boom")))
    )
    // not a merge apply, or nothing: no answer
    assertEquals(Transactor(ds).read(JobRepo.mergeApply("no-such-job")), None)
    val ocr = Transactor(ds).transact(JobRepo.create(Ingest.KindIngest, Some("zz2020_x"), None))
    assertEquals(Transactor(ds).read(JobRepo.mergeApply(ocr.id)), None)
