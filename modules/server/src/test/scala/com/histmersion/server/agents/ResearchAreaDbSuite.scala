package com.histmersion
package server.agents

import com.histmersion.util.problem.Text.sentences

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.automation.agents.{AgentsControllers, LlmClient, ScriptedClient, TestRuns}

import com.augustnagro.magnum.*
import com.histmersion.config.*
import com.histmersion.api.automation.Agents.*
import com.histmersion.api.automation.Managers.*
import com.histmersion.util.database.Migrate
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.domain.automation.managers.ManagerRepo
import com.histmersion.server.{Deps, ServerWiring}
import com.histmersion.api.automation.Goal
import com.histmersion.domain.automation.Goal as DomainGoal
import com.histmersion.db.DbSuite
import com.histmersion.db.TestDb
import com.histmersion.api.automation.Agents
import com.histmersion.config.DbConfig
import com.histmersion.config.AgentsConfig
import com.histmersion.config.AgentRolesConfig
import com.histmersion.api.Api
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.util.database.TestCreators.given

/** The New research area as one server operation (G105, review finding W2), against a `*_test` database: the goal is
  * created and never replaced, and the goal, its plan run, its seeding manager and its watcher are written together or
  * not at all. The first test reproduces what the page did before: on a failed read it sent `PUT /api/goals/{slug}`,
  * which replaces a goal that exists. Models are scripted and down; no provider is called. Skipped without
  * HISTMERSION_TEST_DB_URL.
  */
class ResearchAreaDbSuite extends DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  override val munitTimeout = scala.concurrent.duration.Duration(120, "s")

  private val Model = ModelRef(ProviderKind.Local, Agents.ModelRef.Model("scripted"))
  private val Area = DomainGoal.Slug("zzarea-tin")
  private val AreaSlug = Goal.Slug("zzarea-tin")
  private val Seed = ManagerInfo.Id("seed:zzarea-tin")
  private val Watcher = ManagerInfo.Id("watch:zzarea-tin")

  private def config(enabled: Boolean): AppConfig =
    val b = AppConfig.load()
    val m = Model.wire
    b.modify(_.db)
      .setTo(b.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
      .modify(_.agents)
      .setTo(
        b.agents
          .modify(_.enabled)
          .setTo(AgentsConfig.Enabled(enabled))
          .modify(_.prices)
          .setTo(AgentsConfig.Prices("scripted=1/2"))
          .modify(_.roles)
          .setTo(b.agents.roles.modify(_.planner).setTo(AgentRolesConfig.Planner(m.value)))
      )

  private lazy val ds = testPool(config(enabled = true).db)

  private def handlers(enabled: Boolean = true): (AgentsControllers, ServerWiring) =
    val c = ScriptedClient(Model)
    c.fail = true
    val deps = Deps(config(enabled), ds)
    val h = ServerWiring(deps)
    (AgentsControllers(ServerEngineSite(deps, h, Some(TestRuns.noonClock())), (_, _) => Right(c: LlmClient)), h)

  private def clean(): Unit =
    Migrate.run(ds)
    connect(ds):
      sql"DELETE FROM agent_managers WHERE goal_slug LIKE 'zzarea%' OR id LIKE '%:zzarea%'".update.run()
      sql"DELETE FROM jobs WHERE kind = 'agent' AND target_id LIKE 'zzarea%'".update.run()
      sql"DELETE FROM goal_aliases WHERE alias LIKE 'zzarea%' OR goal_slug LIKE 'zzarea%'".update.run()
      sql"DELETE FROM goals WHERE slug LIKE 'zzarea%'".update.run()

  override def beforeEach(context: BeforeEach): Unit = clean()
  override def afterEach(context: AfterEach): Unit = clean()

  private val existing = Goal.Put(Goal.Title("Zz tin, as the person wrote it"), notes = Some(Goal.Notes("keep me")))
  private val form = Goal.Put(Goal.Title("Zz tin, from the form"), notes = Some(Goal.Notes("the form's notes")))

  private def create(a: AgentsControllers, h: ServerWiring, in: Goal.AreaCreate): Either[Api.Error, Goal.AreaCreated] =
    h.maintenance.guard(WriteGate.OperationName("create_area"))(a.managing.createArea(in))

  private def goalTitle(): Option[String] =
    connect(ds)(sql"SELECT title FROM goals WHERE slug = 'zzarea-tin'".query[String].run().headOption)

  private def managers(): List[String] =
    connect(ds)(sql"SELECT id FROM agent_managers WHERE id LIKE '%:zzarea%' ORDER BY id".query[String].run().toList)

  private def runs(): List[String] =
    connect(ds)(
      sql"SELECT pipeline FROM jobs WHERE kind = 'agent' AND target_id = 'zzarea-tin'".query[String].run().toList
    )

  private def items(id: ManagerInfo.Id): List[String] =
    connect(ds)(
      sql"SELECT kind FROM manager_items WHERE manager_id = $id ORDER BY kind".query[String].run().toList
    )

  test("reproduction: the page's former sequence, a failed read then PUT /api/goals/{slug}, replaced the goal"):
    val (_, h) = handlers()
    assert(h.goals.put(Area, existing).isRight)
    // the read failed (a 500, a dropped connection, a tab that cannot decode the answer): the page went on to put
    assert(h.goals.put(Area, form).isRight)
    assertEquals(goalTitle(), Some("Zz tin, from the form"), "PUT is create-or-replace: the person's goal was replaced")

  test("a new research area over an existing goal is refused with 409, and nothing is written"):
    val (a, h) = handlers()
    assert(h.goals.put(Area, existing).isRight)
    val in = Goal.AreaCreate(
      AreaSlug,
      form,
      Goal.AreaCreate.Plan(true),
      Goal.AreaCreate.Seed(true),
      Some(ManagerConfig())
    )
    val refused = create(a, h, in).fold(identity, made => fail(s"created over an existing goal: $made"))
    assertEquals(refused.status.code, 409, refused.problems.sentences.mkString("; "))
    assertEquals(goalTitle(), Some("Zz tin, as the person wrote it"))
    assertEquals((managers(), runs()), (Nil, Nil))

  test("an old slug of another goal is refused: a new area never takes over a renamed goal's links"):
    val (a, h) = handlers()
    assert(h.goals.put(DomainGoal.Slug("zzarea-bronze"), existing).isRight)
    connect(ds)(sql"INSERT INTO goal_aliases (alias, goal_slug) VALUES ('zzarea-tin', 'zzarea-bronze')".update.run())
    val refused = create(a, h, Goal.AreaCreate(AreaSlug, form)).fold(identity, made => fail(s"created: $made"))
    assertEquals(refused.status.code, 409, refused.problems.sentences.mkString("; "))
    assertEquals(goalTitle(), None)

  test("a failure of a later step leaves nothing behind: no goal, no run, no manager"):
    val (a, h) = handlers()
    // a manager with the seeding manager's id already there (made without a goal): creating it fails inside the
    // transaction, after the goal and the plan run were written
    assert(connect(ds)(ManagerRepo.createIn(Seed, Kind.Seed, None, State.Paused, ManagerConfig(), None, None)))
    val in = Goal.AreaCreate(
      AreaSlug,
      form,
      Goal.AreaCreate.Plan(true),
      Goal.AreaCreate.Seed(true),
      Some(ManagerConfig())
    )
    val refused = create(a, h, in).fold(identity, made => fail(s"created: $made"))
    assertEquals(refused.status.code, 409, refused.problems.sentences.mkString("; "))
    assertEquals(goalTitle(), None)
    assertEquals(runs(), Nil)
    assertEquals(managers(), List(Seed.value), "only the leftover manager")

  test("a plan run that cannot start is checked before anything is written"):
    val (a, h) = handlers(enabled = false)
    val refused = create(a, h, Goal.AreaCreate(AreaSlug, form, Goal.AreaCreate.Plan(true)))
      .fold(identity, made => fail(s"created: $made"))
    assert(refused.problems.sentences.nonEmpty)
    assertEquals((goalTitle(), managers(), runs()), (None, Nil, Nil))

  test("the whole area is created in one operation: the goal, its plan run, its seeding manager and its watcher"):
    val (a, h) = handlers()
    val in = Goal.AreaCreate(
      AreaSlug,
      form,
      Goal.AreaCreate.Plan(true),
      Goal.AreaCreate.Seed(true),
      Some(ManagerConfig())
    )
    val made = create(a, h, in).fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(made.goal.slug, AreaSlug)
    assertEquals(made.goal.title, Goal.Title("Zz tin, from the form"))
    assert(made.run.isDefined, "the plan run")
    assertEquals((made.seed.map(_.id), made.watch.map(_.id)), (Some(Seed), Some(Watcher)))
    assertEquals(goalTitle(), Some("Zz tin, from the form"))
    assertEquals(runs(), List(Pipeline.PlanGoal.wire.value))
    assertEquals(managers(), List(Seed.value, Watcher.value))
    assertEquals(items(Seed), List(ProposeArticles))
    assertEquals(items(Watcher), List(AcquisitionSweep, CurrencyCheck))
    val events = connect(ds)(
      sql"SELECT manager_id || ' ' || text FROM manager_events WHERE manager_id LIKE '%:zzarea-tin' ORDER BY id"
        .query[String]
        .run()
        .toList
    )
    assert(events.exists(_.startsWith("seed:zzarea-tin created paused")), events)
    assert(events.exists(_.startsWith("watch:zzarea-tin created paused")), events)
    assert(events.exists(_.contains("zzarea-tin enqueued by a person")), events)

  test("an area with nothing ticked is just the goal; the same slug again is refused"):
    val (a, h) = handlers()
    val made =
      create(a, h, Goal.AreaCreate(AreaSlug, form)).fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals((made.run, made.seed, made.watch), (None, None, None))
    assertEquals((managers(), runs()), (Nil, Nil))
    assert(create(a, h, Goal.AreaCreate(AreaSlug, existing)).isLeft)
    assertEquals(goalTitle(), Some("Zz tin, from the form"))
