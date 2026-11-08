package com.histmersion
package db

import com.histmersion.api.Problem
import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.util.database.Transactor

import com.histmersion.domain.research.articles.ArticleFixtures

import com.histmersion.domain.library.sources.SourceFixtures

import com.histmersion.domain.catalogue.places.PlaceFixtures

import com.histmersion.domain.catalogue.events.*
import com.histmersion.domain.catalogue.evidence.*

import com.histmersion.util.jobs.JobRepo

import com.histmersion.api.{Dataset, TimeSpan}
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Goal.Plans.{LinkKind, ScheduleKind}

import com.augustnagro.magnum.*
import com.histmersion.util.data.exchange.TestExchange
import java.nio.file.{Files, Path}
import javax.sql.DataSource
import io.scalaland.chimney.dsl.*
import com.histmersion.util.data.exchange.{BundleFormat, BundleReader}
import com.histmersion.util.data.exchange.{BundleExport, MergeRepo}
import com.histmersion.domain.automation.goals.{GoalLinkRepo, GoalRepo}
import com.histmersion.domain.automation.managers.ManagerRepo
import com.histmersion.util.data.exchange.{BundleImport, ImportOptions, MergeState}
import com.histmersion.util.database.Sql
import com.histmersion.util.data.exchange.BundleEntities
import com.histmersion.api.catalogue.{EventPut, Period, Place}
import com.histmersion.api.exchange.{MergeApplyResult, MergeJobState, MergePlan, MergeResolution, MergeResolutions}
import com.histmersion.api.library.{Ingest, Source}
import com.histmersion.api.research.Article
import com.histmersion.api.exchange.MergeIssue as Issue
import com.histmersion.api.automation.Goal
import com.histmersion.api.exchange.Bundle
import com.histmersion.util.database.given
import com.histmersion.config.DbConfig
import com.histmersion.util.jobs.Job
import com.histmersion.util.storage.ContentDirectory
import com.histmersion.util.data.exchange.BundleImporter
import com.histmersion.util.storage.ByteSource
import com.histmersion.util.data.exchange.MergeRow
import com.histmersion.api.catalogue.Year
import com.histmersion.domain.catalogue.events.BEventRow
import com.histmersion.api.catalogue.EventSummary
import com.histmersion.api.library.Citekey
import com.histmersion.domain.automation.goals.BGoalRow
import com.histmersion.api.automation.Managers
import com.histmersion.domain.research.Slug
import com.histmersion.util.identity.AliasTable
import com.histmersion.api.library.Filter
import com.histmersion.domain.catalogue.places.PlaceScope

/** What a bundle carries and a merge decides beyond M5, against real Postgres (docs/research-merge-import.md §7.8): a
  * collection's own places and named periods (V052) and not its seeds, a goal's plan, links and schedules (#881, V054),
  * old article slugs, events matched across two ids, label renames that keep an alias (V053), and what
  * `merge list | show | ledger` read back. Two databases of their own per case, as `MergeSuite`. Skipped without
  * HISTMERSION_TEST_DB_URL.
  */
class MergeExtrasDbSuite extends DbSuite with Bounded:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  private lazy val cfg = AppConfig.load()
  private lazy val merge = cfg.merge.transformInto[MergeSettings]
  private lazy val admin = testPool(cfg.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
  private val tmp = Files.createTempDirectory("histmersion-merge-extras")
  private lazy val importChunkChars = ImportOptions.ChunkChars(cfg.limits.chunkChars.value)
  private lazy val importToleranceKm = ImportOptions.ToleranceKm(cfg.places.pointToleranceKm.value)

  private def sha256Of(bundle: Path): Sha256 =
    Sha256.assume(BundleFormat.sha256Hex(Files.readAllBytes(bundle)).value)

  private def applyJob(ds: DataSource, id: Job.Id) =
    Transactor(ds).read(JobRepo.mergeApply(id))

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
  private lazy val scratch = ScratchDatabases("histmersion_mx", cfg.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))

  override def afterSuite(): Unit =
    if testDb.isDefined then scratch.dropAll()

  private def spool(n: String): Path = Files.createDirectories(tmp.resolve(n))

  private def exportOf(ds: DataSource, originals: Path): Path =
    val out = java.io.ByteArrayOutputStream()
    BundleExport
      .write(ds, out, None, ContentDirectory(originals), TestExchange.kinds)
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
        ContentDirectory(originals),
        importChunkChars,
        TestExchange.kinds,
        TestExchange.rules
      )
      .fold(e => fail(e.value), identity)

  private def importInto(ds: DataSource, bundle: Path, originals: Path): Bundle.ImportResult =
    BundleImporter
      .run(
        ds,
        ByteSource.file(bundle),
        sha256Of(bundle),
        BundleImport.Options(ImportOptions.DryRun(false), importChunkChars, importToleranceKm),
        ContentDirectory(originals),
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
    BundleImporter
      .apply(
        ds,
        ByteSource.file(bundle),
        sha256Of(bundle),
        BundleImport
          .Options(dryRun = ImportOptions.DryRun(false), importChunkChars, importToleranceKm),
        ContentDirectory(originals),
        res,
        merge,
        MergeRow.PlanHash(hash.value),
        () => Right("stub.dump"),
        TestExchange.kinds,
        TestExchange.rules,
        TestExchange.followUps
      )
      .fold(e => fail(e), identity)

  private def count(ds: DataSource, q: String): Int = connect(ds)(Sql.raw(Sql.Text(q)).frag.query[Int].run().head)
  private def text(ds: DataSource, q: String): Option[String] =
    connect(ds)(Sql.raw(Sql.Text(q)).frag.query[Option[String]].run().headOption.flatten)

  private def site(id: String, label: Place.Label, lon: Double, lat: Double): Place.Input =
    Place.Input(
      Place(
        Place.Id(id),
        label,
        Place.KindValue(com.histmersion.domain.catalogue.Place.Kind.Site),
        Some(Place.Geometry(s"""{"type":"Point","coordinates":[$lon,$lat]}""")),
        Some(Period(Year(-1650), Year(-1180)))
      ),
      List(Place.Name(Place.Name.Value(label.value)))
    )

  test("places and periods: the collection's own travel and import; the seeds do not; the dataset marks which"):
    val (work, home) = (fresh("pw"), fresh("ph"))
    val (sw, sh) = (spool("pw"), spool("ph"))
    val _ =
      PlaceFixtures.upsertPlaces(work, List(site("zz_seed_site", Place.Label("A seed site"), 30.0, 39.0)), Dataset.Seed)
    val _ =
      PlaceFixtures.upsertPlaces(work, List(site("zz_my_site", Place.Label("My site"), 34.6, 40.0)), Dataset.Local)
    val _ = connect(work)(
      sql"""INSERT INTO periods (id, label, authority, period, dataset)
            VALUES ('zz_my_period', 'My period', 'me', '[-1400,-1199)'::int8range, 'local')""".update.run()
    )
    EventFixtures.put(
      work,
      BEventRow.Id("zz_at_my_site"),
      EventPut(
        EventSummary.Label("At my site"),
        EventSummary.Kind("battle"),
        TimeSpan(Some(Year(-1300)), None, None, Some(Year(-1290))),
        placeId = Some(Place.Id("zz_my_site"))
      )
    )
    val bundle = exportOf(work, sw)
    val es = entitiesOf(bundle)
    assertEquals(es.places.map(_.id), List("zz_my_site"))
    assertEquals(es.periods.map(_.id), List("zz_my_period"))
    assertEquals(es.places.head.names.map(_.name), List("My site"))
    // home has only the seeds it imports itself — the event's place comes with the bundle
    val _ =
      PlaceFixtures.upsertPlaces(home, List(site("zz_seed_site", Place.Label("A seed site"), 30.0, 39.0)), Dataset.Seed)
    val r = importInto(home, bundle, sh)
    assert(r.applied.value, r.problems.mkString("\n"))
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
      List(
        site("zz_ugarit", Place.Label("Ugarit"), 35.78, 35.60),
        site("work_troy", Place.Label("Troy"), 26.2389, 39.9575)
      ),
      Dataset.Local
    )
    EventFixtures.put(
      work,
      BEventRow.Id("zz_troy_burns"),
      EventPut(
        EventSummary.Label("Troy burns"),
        EventSummary.Kind("destruction"),
        TimeSpan(Some(Year(-1190)), None, None, Some(Year(-1180))),
        placeId = Some(Place.Id("work_troy"))
      )
    )
    val _ =
      PlaceFixtures.upsertPlaces(home, List(site("zz_ugarit", Place.Label("Ugarit"), 35.70, 35.50)), Dataset.Local)
    val _ = PlaceFixtures.upsertPlaces(
      home,
      List(site("pleiades_550595", Place.Label("Troy"), 26.2390, 39.9576)),
      Dataset.Seed
    )
    val bundle = exportOf(work, sw)
    val p = planIn(home, bundle, sh)
    val q = p.issues.find(_.kind == Issue.Kind.PlaceDiffers).getOrElse(fail(p.issues.mkString("\n")))
    // the review screen gets both geometries as GeoJSON
    assert(q.detail.flatMap(_.place).flatMap(_.rightGeometry).exists(_.value.contains("\"Point\"")), q.detail.toString)
    assert(p.info.exists(_.value.contains("is the same place")), p.info.mkString("\n"))
    val a = applyIn(
      home,
      bundle,
      sh,
      MergeResolutions(List(MergeResolution(MergeResolution.Issue(q.id.value), MergeResolution.Decision("keep-right"))))
    )
    assert(a.applied.value, (a.problems ++ a.issues.map(_.title)).mkString("\n"))
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
        Citekey("zzgoal2020_src"),
        com.histmersion.api.library.Title("Linked"),
        (List("A")).map(com.histmersion.api.library.AuthorName.apply),
        None,
        (Some(2020)).map(Year.apply),
        None,
        Source.Status.Current,
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
    transact(work)(GoalRepo.put(com.histmersion.domain.automation.Goal.Slug("zz_goal"), Goal.Put(Goal.Title("A goal"))))
    transact(work)(
      GoalLinkRepo.setPlan(
        com.histmersion.domain.automation.Goal.Slug("zz_goal"),
        BGoalRow.Plan("""{"goal":"zz_goal","rationale":"work's plan"}""")
      )
    )
    transact(work)(
      GoalLinkRepo.setBudget(com.histmersion.domain.automation.Goal.Slug("zz_goal"), Some(BigDecimal("2.5")))
    )
    assert(
      transact(work)(
        GoalLinkRepo.link(
          com.histmersion.domain.automation.Goal.Slug("zz_goal"),
          LinkKind.Source,
          Goal.Plans.GoalLink.Ref("zzgoal2020_src"),
          Some(Goal.Plans.GoalLink.Note("why"))
        )
      )
    )
    assert(
      transact(work)(
        GoalLinkRepo.link(
          com.histmersion.domain.automation.Goal.Slug("zz_goal"),
          LinkKind.Query,
          Goal.Plans.GoalLink.Ref("hittite storm god"),
          None
        )
      )
    )
    transact(work)(
      GoalLinkRepo.setSchedule(
        com.histmersion.domain.automation.Goal.Slug("zz_goal"),
        ScheduleKind.Currency,
        Goal.Plans.GoalSchedule.Cron("@weekly"),
        Goal.Plans.GoalSchedule.Enabled(true),
        None,
        Some(java.time.Instant.now())
      )
    )
    val bundle = exportOf(work, sw)
    val g = entitiesOf(bundle).goals.find(_.slug.value == "zz_goal").getOrElse(fail("no goal"))
    assert(g.planApprovedAt.isDefined && g.budgetUsd.contains(2.5), g.toString)
    assertEquals(g.links.map(_.map(_.kind)), Some(List("query", "source")))
    assertEquals(g.schedules.map(_.map(_.cron)), Some(List("@weekly")))
    val r = importInto(home, bundle, sh)
    assert(r.applied.value, r.problems.mkString("\n"))
    assertEquals(
      connect(home)(GoalLinkRepo.planAndBudget(com.histmersion.domain.automation.Goal.Slug("zz_goal")))
        .flatMap(_._1)
        .exists(_.value.contains("work's plan")),
      true
    )
    assertEquals(
      Transactor(home).read(
        GoalLinkRepo.refsIn(com.histmersion.domain.automation.Goal.Slug("zz_goal"), LinkKind.Source)
      ),
      List("zzgoal2020_src")
    )
    val s =
      Transactor(home)
        .read(GoalLinkRepo.schedule(com.histmersion.domain.automation.Goal.Slug("zz_goal"), ScheduleKind.Currency))
        .getOrElse(fail("no schedule"))
    // brought in, it never fires here before a person saves it
    assertEquals((s.cron, s.nextRunAt), ("@weekly", None))
    assert(s.lastNote.exists(_.value.contains("save it once")), s.toString)
    assertEquals(importInto(home, bundle, sh).changed, 0)
    // changed on both sides afterwards: one goal; home's schedule stays, the links of both
    transact(home)(
      GoalLinkRepo.setSchedule(
        com.histmersion.domain.automation.Goal.Slug("zz_goal"),
        ScheduleKind.Currency,
        Goal.Plans.GoalSchedule.Cron("@daily"),
        Goal.Plans.GoalSchedule.Enabled(true),
        None,
        None
      )
    )
    assert(
      transact(home)(
        GoalLinkRepo.link(
          com.histmersion.domain.automation.Goal.Slug("zz_goal"),
          LinkKind.Query,
          Goal.Plans.GoalLink.Ref("ahhiyawa"),
          None
        )
      )
    )
    assert(
      transact(work)(
        GoalLinkRepo.link(
          com.histmersion.domain.automation.Goal.Slug("zz_goal"),
          LinkKind.Query,
          Goal.Plans.GoalLink.Ref("mycenae"),
          None
        )
      )
    )
    val again = exportOf(work, sw)
    val p = planIn(home, again, sh)
    assert(p.info.exists(_.value.contains("its currency schedule differs")), p.info.mkString("\n"))
    val a = applyIn(home, again, sh)
    assert(a.applied.value, (a.problems ++ a.issues.map(_.title)).mkString("\n"))
    assertEquals(
      Transactor(home)
        .read(GoalLinkRepo.refsIn(com.histmersion.domain.automation.Goal.Slug("zz_goal"), LinkKind.Query))
        .toSet,
      Set("hittite storm god", "ahhiyawa", "mycenae")
    )
    assertEquals(
      Transactor(home)
        .read(GoalLinkRepo.schedule(com.histmersion.domain.automation.Goal.Slug("zz_goal"), ScheduleKind.Currency))
        .map(_.cron),
      Some("@daily")
    )
    assertEquals(a.replanChanges, Some(0))

  test("goals (phase 3): a watcher's sweeps travel as the goal's schedules (cron, budget); its state stays home"):
    val (work, sw) = (fresh("ww"), spool("ww"))
    transact(work)(
      GoalRepo.put(com.histmersion.domain.automation.Goal.Slug("zz_goal"), Goal.Put(Goal.Title("A watched goal")))
    )
    assert(
      transact(work)(
        ManagerRepo.create(
          ManagerRepo.ManagerRow.Id("watch:zz_goal"),
          Managers.Kind.Watch,
          Some(Goal.Slug("zz_goal")),
          Managers.State.Paused,
          Managers
            .ManagerConfig(
              sweeps = Some(List(Managers.Sweep.Wire("acquisition"))),
              budgetUsd = Some(Managers.ManagerConfig.BudgetUsd(0.3))
            ),
          Some(Managers.ManagerInfo.Cron("@weekly")),
          None
        )
      )
    )
    val g = entitiesOf(exportOf(work, sw)).goals.find(_.slug.value == "zz_goal").getOrElse(fail("no goal"))
    assertEquals(
      g.schedules.map(_.map(x => (x.kind, x.cron, x.enabled, x.budgetUsd))),
      Some(List(("acquisition", "@weekly", true, Some(0.3))))
    )

  test("old article slugs travel; events under two ids that are one fold; merge list/show/ledger read it back"):
    val (work, home) = (fresh("aw"), fresh("ah"))
    val (sw, sh) = (spool("aw"), spool("ah"))
    val _ = ArticleFixtures.put(
      work,
      Article(
        Article.Slug("zz_first_slug"),
        Article.Title("An article"),
        Article.KindValue("concept"),
        Article.Body("Text.")
      )
    )
    val _ = ArticleFixtures
      .rename(work, Slug("zz_first_slug"), Slug("zz_second_slug"))
      .fold(e => fail(e.message.value), identity)
    val _ = PlaceFixtures.upsertPlaces(work, List(site("zz_kadesh", Place.Label("Kadesh"), 36.51, 34.56)), Dataset.Seed)
    val _ = PlaceFixtures.upsertPlaces(home, List(site("zz_kadesh", Place.Label("Kadesh"), 36.51, 34.56)), Dataset.Seed)
    EventFixtures.put(
      work,
      BEventRow.Id("work_kadesh"),
      EventPut(
        EventSummary.Label("Battle of Kadesh"),
        EventSummary.Kind("battle"),
        TimeSpan(Some(Year(-1274)), None, None, Some(Year(-1274))),
        placeId = Some(Place.Id("zz_kadesh")),
        description = Some(EventSummary.Description("work's account"))
      )
    )
    EventFixtures.put(
      home,
      BEventRow.Id("kadesh_1274"),
      EventPut(
        EventSummary.Label("Battle of Kadesh"),
        EventSummary.Kind("battle"),
        TimeSpan(Some(Year(-1275)), None, None, Some(Year(-1274))),
        placeId = Some(Place.Id("zz_kadesh"))
      )
    )
    val bundle = exportOf(work, sw)
    assertEquals(entitiesOf(bundle).articleAliases.map(_.alias), List("zz_first_slug"))
    val a = applyIn(home, bundle, sh)
    assert(a.applied.value, (a.problems ++ a.issues.map(_.title)).mkString("\n"))
    assertEquals(a.tally.eventsMerged, 1)
    assertEquals(count(home, "SELECT count(*)::int FROM events"), 1)
    assertEquals(text(home, "SELECT description FROM events WHERE id = 'kadesh_1274'"), Some("work's account"))
    assertEquals(ArticleFixtures.view(home, Slug("zz_first_slug")).map(_.article.slug), Some("zz_second_slug"))
    assertEquals(a.replanChanges, Some(0))
    // what the merge left, read back
    val list = Transactor(home).read(MergeRepo.list(PageSize(10)))
    assertEquals(list.map(_.planHash.isDefined), List(true))
    val m = list.head
    assert(m.redirects.value >= 1 && m.ledgerRows.value >= 2, m.toString)
    val d = Transactor(home)
      .read(MergeRepo.detail(MergeRepo.MergeIdOrPrefix(m.id.toString.take(8))))
      .fold(e => fail(e.message.value), identity)
    assertEquals(d.applied.map(_.tally.eventsMerged), Some(1))
    assertEquals(d.merge.backup, Some("stub.dump"))
    val evUid = connect(home)(sql"SELECT uid FROM events WHERE id = 'kadesh_1274'".query[java.util.UUID].run().head)
    val led =
      Transactor(home)
        .read(MergeRepo.ledger(Some(MergeRepo.UidText(evUid.toString)), None, PageSize(50), TestExchange.rules))
        .fold(e => fail(e.message.value), identity)
    assertEquals(led.entries.map(_.label), List(Some("kadesh_1274")))
    assertEquals(led.redirects.map(_.uid), List(evUid))
    assert(Transactor(home).read(MergeRepo.detail(MergeRepo.MergeIdOrPrefix("zzzz"))).isLeft)

  test("label renames keep the old label as an alias: refused when taken, dropped when renamed back"):
    val ds = fresh("rn")
    EventFixtures.put(ds, BEventRow.Id("zz_ev_a"), EventPut(EventSummary.Label("E"), EventSummary.Kind("battle")))
    EventFixtures.put(
      ds,
      BEventRow.Id("zz_ev_other"),
      EventPut(EventSummary.Label("Other"), EventSummary.Kind("battle"))
    )
    transact(ds)(GoalRepo.put(com.histmersion.domain.automation.Goal.Slug("zz_goal_a"), Goal.Put(Goal.Title("G"))))
    assert(EventFixtures.rename(ds, BEventRow.Id("zz_ev_a"), BEventRow.Id("zz_ev_b")).isRight)
    assertEquals(EventFixtures.renamedTo(ds, BEventRow.Id("zz_ev_a")), Some("zz_ev_b"))
    // an old id of one event is not free for another
    assert(
      EventFixtures
        .rename(ds, BEventRow.Id("zz_ev_other"), BEventRow.Id("zz_ev_a"))
        .left
        .exists(_.message.value.contains("old id of 'zz_ev_b'"))
    )
    assert(EventFixtures.rename(ds, BEventRow.Id("zz_ev_b"), BEventRow.Id("zz_ev_a")).isRight)
    assertEquals(EventFixtures.renamedTo(ds, BEventRow.Id("zz_ev_a")), None)
    assertEquals(EventFixtures.renamedTo(ds, BEventRow.Id("zz_ev_b")), Some("zz_ev_a"))
    assert(
      Transactor(ds)
        .transact(
          GoalRepo.renameIn(
            com.histmersion.domain.automation.Goal.Slug("zz_goal_a"),
            com.histmersion.domain.automation.Goal.Slug("zz_goal_b")
          )
        )
        .isRight
    )
    assertEquals(
      Transactor(ds).read(GoalRepo.renamedTo(com.histmersion.domain.automation.Goal.Slug("zz_goal_a"))),
      Some("zz_goal_b")
    )
    assert(
      Transactor(ds)
        .transact(
          GoalRepo.renameIn(
            com.histmersion.domain.automation.Goal.Slug("zz_goal_b"),
            com.histmersion.domain.automation.Goal.Slug("zz_goal_b")
          )
        )
        .isLeft
    )

  // #888: a place renamed over there (a new id under the same uid) kept its old id here, and the
  // bundle's events that name the new id pointed at nothing
  test("a place renamed over there is followed: the new id here, the old one an alias, events and names follow"):
    val (work, home) = (fresh("prw"), fresh("prh"))
    val (sw, sh) = (spool("prw"), spool("prh"))
    val _ =
      PlaceFixtures.upsertPlaces(work, List(site("zz_old_site", Place.Label("My site"), 34.6, 40.0)), Dataset.Local)
    EventFixtures.put(
      work,
      BEventRow.Id("zz_at_site"),
      EventPut(
        EventSummary.Label("At my site"),
        EventSummary.Kind("battle"),
        TimeSpan(Some(Year(-1300)), None, None, Some(Year(-1290))),
        placeId = Some(Place.Id("zz_old_site"))
      )
    )
    val first = importInto(home, exportOf(work, sw), sh)
    assert(first.applied.value, first.problems.mkString("\n"))
    // work renames the place: its event follows by key (V055)
    assert(
      PlaceFixtures
        .rename(
          work,
          com.histmersion.domain.catalogue.Place.Id("zz_old_site"),
          com.histmersion.domain.catalogue.Place.Id("zz_new_site")
        )
        .isRight
    )
    assertEquals(text(work, "SELECT place_id FROM events WHERE id = 'zz_at_site'"), Some("zz_new_site"))
    val bundle = exportOf(work, sw)
    val p = planIn(home, bundle, sh)
    assert(p.info.exists(_.value.contains("renamed zz_new_site in the bundle")), p.info.mkString("\n"))
    assertEquals(p.problems, Nil)
    val r = importInto(home, bundle, sh)
    assert(r.applied.value, r.problems.mkString("\n"))
    assertEquals(count(home, "SELECT count(*)::int FROM places WHERE id = 'zz_old_site'"), 0)
    assertEquals(text(home, "SELECT place_id FROM place_names WHERE name = 'My site'"), Some("zz_new_site"))
    assertEquals(text(home, "SELECT place_id FROM events WHERE id = 'zz_at_site'"), Some("zz_new_site"))
    assertEquals(PlaceFixtures.renamedTo(home, AliasTable.Id("zz_old_site")), Some("zz_new_site"))
    // the same bundle again changes nothing
    assertEquals(importInto(home, bundle, sh).changed, 0)

  test("a place renamed over there onto an id another place has here keeps ours; the bundle's events point at it"):
    val (work, home) = (fresh("pkw"), fresh("pkh"))
    val (sw, sh) = (spool("pkw"), spool("pkh"))
    val _ =
      PlaceFixtures.upsertPlaces(work, List(site("zz_old_site", Place.Label("My site"), 34.6, 40.0)), Dataset.Local)
    val first = importInto(home, exportOf(work, sw), sh)
    assert(first.applied.value, first.problems.mkString("\n"))
    val _ =
      PlaceFixtures.upsertPlaces(home, List(site("zz_taken", Place.Label("A seed site"), 30.0, 39.0)), Dataset.Seed)
    assert(
      PlaceFixtures
        .rename(
          work,
          com.histmersion.domain.catalogue.Place.Id("zz_old_site"),
          com.histmersion.domain.catalogue.Place.Id("zz_taken")
        )
        .isRight
    )
    EventFixtures.put(
      work,
      BEventRow.Id("zz_at_taken"),
      EventPut(
        EventSummary.Label("At the renamed site"),
        EventSummary.Kind("battle"),
        TimeSpan(Some(Year(-1300)), None, None, Some(Year(-1290))),
        placeId = Some(Place.Id("zz_taken"))
      )
    )
    val bundle = exportOf(work, sw)
    val p = planIn(home, bundle, sh)
    assert(!p.issues.exists(_.kind == Issue.Kind.PlaceDiffers), p.issues.map(_.title).mkString("\n"))
    assert(p.info.exists(_.value.contains("it keeps its id")), p.info.mkString("\n"))
    val r = importInto(home, bundle, sh)
    assert(r.applied.value, r.problems.mkString("\n"))
    assertEquals(text(home, "SELECT label FROM places WHERE id = 'zz_taken'"), Some("A seed site"))
    assertEquals(count(home, "SELECT count(*)::int FROM places WHERE id = 'zz_old_site'"), 1)
    assertEquals(text(home, "SELECT place_id FROM events WHERE id = 'zz_at_taken'"), Some("zz_old_site"))

  // #890: the old labels of events, goals and places travel as the old article slugs do
  test("old event ids, goal slugs and place ids travel in a bundle, are written here, and keep resolving"):
    val (work, home) = (fresh("alw"), fresh("alh"))
    val (sw, sh) = (spool("alw"), spool("alh"))
    val _ =
      PlaceFixtures.upsertPlaces(work, List(site("zz_pl_before", Place.Label("My site"), 34.6, 40.0)), Dataset.Local)
    EventFixtures.put(
      work,
      BEventRow.Id("zz_ev_before"),
      EventPut(EventSummary.Label("E"), EventSummary.Kind("battle"))
    )
    transact(work)(
      GoalRepo.put(com.histmersion.domain.automation.Goal.Slug("zz_goal_before"), Goal.Put(Goal.Title("G")))
    )
    assert(
      PlaceFixtures
        .rename(
          work,
          com.histmersion.domain.catalogue.Place.Id("zz_pl_before"),
          com.histmersion.domain.catalogue.Place.Id("zz_pl_after")
        )
        .isRight
    )
    assert(EventFixtures.rename(work, BEventRow.Id("zz_ev_before"), BEventRow.Id("zz_ev_after")).isRight)
    assert(
      Transactor(work)
        .transact(
          GoalRepo.renameIn(
            com.histmersion.domain.automation.Goal.Slug("zz_goal_before"),
            com.histmersion.domain.automation.Goal.Slug("zz_goal_after")
          )
        )
        .isRight
    )
    val bundle = exportOf(work, sw)
    val es = entitiesOf(bundle)
    assertEquals(es.eventAliases.map(_.alias), List("zz_ev_before"))
    assertEquals(es.goalAliases.map(_.alias), List("zz_goal_before"))
    assertEquals(es.placeAliases.map(_.alias), List("zz_pl_before"))
    val r = importInto(home, bundle, sh)
    assert(r.applied.value, r.problems.mkString("\n"))
    assertEquals(EventFixtures.renamedTo(home, BEventRow.Id("zz_ev_before")), Some("zz_ev_after"))
    assertEquals(
      Transactor(home).read(GoalRepo.renamedTo(com.histmersion.domain.automation.Goal.Slug("zz_goal_before"))),
      Some("zz_goal_after")
    )
    assertEquals(PlaceFixtures.renamedTo(home, AliasTable.Id("zz_pl_before")), Some("zz_pl_after"))
    // a filter on the OLD id is the filter on the place it names now, labelled with that place
    val scope = PlaceFixtures.scopeFor(home, List(Filter.Places("zz_pl_before")), None)
    assertEquals(scope.idsFor(PlaceScope.Value("zz_pl_before")), Some(List("zz_pl_after")))
    assertEquals(scope.labelFor(PlaceScope.Value("zz_pl_before")), "My site")
    // the same bundle again changes nothing, and an alias naming another record here is not written
    assertEquals(importInto(home, bundle, sh).changed, 0)
    EventFixtures.put(work, BEventRow.Id("zz_ev_x"), EventPut(EventSummary.Label("X"), EventSummary.Kind("battle")))
    assert(EventFixtures.rename(work, BEventRow.Id("zz_ev_x"), BEventRow.Id("zz_ev_y")).isRight)
    EventFixtures.put(
      home,
      BEventRow.Id("zz_ev_x"),
      EventPut(EventSummary.Label("Home's own X"), EventSummary.Kind("battle"))
    )
    val second = importInto(home, exportOf(work, sw), sh)
    assert(second.applied.value, second.problems.mkString("\n"))
    assertEquals(EventFixtures.renamedTo(home, BEventRow.Id("zz_ev_x")), None) // a live id here, never an alias

  test("place renames keep the old id as an alias: refused when taken, dropped when renamed back"):
    val ds = fresh("prn")
    val _ = PlaceFixtures.upsertPlaces(
      ds,
      List(site("zz_pl_a", Place.Label("A"), 30.0, 39.0), site("zz_pl_other", Place.Label("Other"), 31.0, 39.0)),
      Dataset.Local
    )
    assert(
      PlaceFixtures
        .rename(
          ds,
          com.histmersion.domain.catalogue.Place.Id("zz_pl_a"),
          com.histmersion.domain.catalogue.Place.Id("zz_pl_b")
        )
        .isRight
    )
    assertEquals(PlaceFixtures.renamedTo(ds, AliasTable.Id("zz_pl_a")), Some("zz_pl_b"))
    assertEquals(
      PlaceFixtures.get(ds, com.histmersion.domain.catalogue.Place.Id("zz_pl_b")).map(_.place.label),
      Some("A")
    )
    assert(
      PlaceFixtures
        .rename(
          ds,
          com.histmersion.domain.catalogue.Place.Id("zz_pl_other"),
          com.histmersion.domain.catalogue.Place.Id("zz_pl_a")
        )
        .left
        .exists(_.message.value.contains("old id of 'zz_pl_b'"))
    )
    assert(
      PlaceFixtures
        .rename(
          ds,
          com.histmersion.domain.catalogue.Place.Id("zz_pl_b"),
          com.histmersion.domain.catalogue.Place.Id("zz_pl_other")
        )
        .left
        .exists(_.message.value.contains("already the id"))
    )
    assert(
      PlaceFixtures
        .rename(
          ds,
          com.histmersion.domain.catalogue.Place.Id("zz_pl_b"),
          com.histmersion.domain.catalogue.Place.Id("zz_pl_a")
        )
        .isRight
    )
    assertEquals(PlaceFixtures.renamedTo(ds, AliasTable.Id("zz_pl_a")), None)
    assertEquals(PlaceFixtures.renamedTo(ds, AliasTable.Id("zz_pl_b")), Some("zz_pl_a"))

  // #888: the apply jobs were kept in memory and lost on a restart
  test("merge apply jobs are rows: running, then done with the report; a restart turns a running one interrupted"):
    val ds = fresh("job")
    val res = MergeApplyResult(
      java.util.UUID.randomUUID(),
      Bundle.Instance(java.util.UUID.randomUUID(), Some(Bundle.Instance.Label("work"))),
      applied = MergeApplyResult.Applied(true),
      MergeApplyResult.PlanHash("abc123"),
      notes = List(MergeApplyResult.Notes("a note"))
    )
    val a =
      Transactor(ds).transact(JobRepo.startMergeApply(MergeRepo.BundleName("a" * 64), MergeRow.PlanHash("abc123")))
    assert(Transactor(ds).read(JobRepo.mergeApplyRunning))
    assertEquals(
      applyJob(ds, a).map(j => (j.state, j.bundle, j.planHash)),
      Some((MergeJobState.Running, "a" * 64, "abc123"))
    )
    Transactor(ds).transact(JobRepo.finishMergeApply(a, Right(res)))
    assert(!Transactor(ds).read(JobRepo.mergeApplyRunning))
    assertEquals(
      applyJob(ds, a).map(j => (j.state, j.result)),
      Some((MergeJobState.Done, Some(res)))
    )
    val b =
      Transactor(ds).transact(JobRepo.startMergeApply(MergeRepo.BundleName("b" * 64), MergeRow.PlanHash("def456")))
    assertEquals(Transactor(ds).transact(JobRepo.failInterrupted()), 1) // the startup reconciliation
    assertEquals(applyJob(ds, b).map(_.state), Some(MergeJobState.Interrupted))
    // the thread that outlived nothing cannot overwrite it; a crash is failed with its reason
    Transactor(ds).transact(JobRepo.finishMergeApply(b, Right(res)))
    assertEquals(applyJob(ds, b).map(_.state), Some(MergeJobState.Interrupted))
    val c = Transactor(ds).transact(JobRepo.startMergeApply(MergeRepo.BundleName("c" * 64), MergeRow.PlanHash("0")))
    Transactor(ds).transact(JobRepo.finishMergeApply(c, Left(Problem("merge apply crashed: boom"))))
    assertEquals(
      applyJob(ds, c).map(j => (j.state, j.error)),
      Some((MergeJobState.Failed, Some("merge apply crashed: boom")))
    )
    // not a merge apply, or nothing: no answer
    assertEquals(Transactor(ds).read(JobRepo.mergeApply(Job.Id("00000000-0000-0000-0000-000000000000"))), None)
    val ocr = Transactor(ds).transact(
      JobRepo.create(
        Job.Kind.applyUnsafe(Ingest.KindIngest),
        Some(com.histmersion.domain.library.Citekey("zz2020_x")),
        None
      )
    )
    assertEquals(applyJob(ds, Job.Id.assume(ocr.id.value)), None)
