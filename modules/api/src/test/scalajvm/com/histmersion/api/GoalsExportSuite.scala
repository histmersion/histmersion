package com.histmersion
package api

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.automation.Goal.Plans.*
import com.histmersion.api.automation.{Goal, GoalsExport, ItemStatus}
import com.histmersion.api.catalogue.Year

/** `meta/GOALS.md` (task #906): the pure renderer. The fixture mirrors the live goals' shape — a few top-level goals
  * with nested sub-goals, article and queue-item links (done and open), one goal with a period — plus the parts the
  * live data does not have yet (a source, a search, a schedule, an approved plan, a dangling link). No database, no
  * server.
  */
class GoalsExportSuite extends munit.FunSuite:

  private def goal(
      slug: Goal.Slug,
      title: Goal.Title,
      status: Goal.StatusValue,
      parent: Option[Goal.Slug] = None,
      notes: Option[Goal.Notes] = None,
      tags: List[Goal.Tags] = Nil,
      regions: List[Goal.Regions] = Nil,
      period: Option[(Year, Year)] = None
  ) =
    Goal(
      slug,
      title,
      status,
      notes,
      tags,
      regions,
      period.map(_._1),
      period.map(_._2),
      parent,
      updatedAt = Some(Goal.UpdatedAt("2026-10-05 17:15:24.891961+02"))
    )

  private def article(slug: GoalLink.Ref, title: GoalLink.Title) =
    GoalLink(LinkKind.Article, slug, None, Some(title), Goal.Plans.GoalLink.CreatedAt("2026-10-05T15:00:00Z"))
  private def item(id: GoalLink.QueueNumber, text: GoalLink.Title) =
    GoalLink(
      LinkKind.QueueItem,
      GoalLink.Ref(id.value.toString),
      None,
      Some(text),
      Goal.Plans.GoalLink.CreatedAt("2026-10-05T15:00:00Z")
    )

  private val views: List[GoalView] = List(
    GoalView(
      goal(
        Goal.Slug("bronze-age"),
        Goal.Title("The Bronze Age and earlier; the Late Bronze Age collapse"),
        Goal.StatusValue("compiled"),
        notes = Some(
          Goal.Notes(
            "The Bronze Age and the periods before it; the Late Bronze Age collapse — causes, sequence, debates."
          )
        ),
        tags = List(Goal.Tags("bronze_age"), Goal.Tags("bronze_age_collapse"))
      )
    ),
    GoalView(
      goal(
        Goal.Slug("bronze-age-collapse"),
        Goal.Title("The Late Bronze Age collapse"),
        Goal.StatusValue("compiled"),
        Some(Goal.Slug("bronze-age")),
        notes = Some(Goal.Notes("Climate-primary vs stressor debate, resilience critiques.")),
        period = Some((Year(-1300), Year(-1050)))
      ),
      links = List(
        item(
          GoalLink.QueueNumber(522),
          GoalLink.Title("Dafna Langgut, Climate and Environment in the Southern Levant, 1300–300 BCE")
        ),
        article(GoalLink.Ref("sea_peoples"), GoalLink.Title("The Sea Peoples")),
        item(
          GoalLink.QueueNumber(495),
          GoalLink.Title("Ingest 10.1007/s10963-021-09163-7 (hybrid OA) — Meyer & Knapp 2021")
        ),
        article(GoalLink.Ref("bronze_age_collapse"), GoalLink.Title("Late Bronze Age Collapse (~1200 BCE)")),
        GoalLink(
          LinkKind.QueueItem,
          Goal.Plans.GoalLink.Ref("9999"),
          None,
          None,
          Goal.Plans.GoalLink.CreatedAt("2026-10-05T15:00:00Z")
        ),
        GoalLink(
          LinkKind.Source,
          Goal.Plans.GoalLink.Ref("cline2014_year"),
          Some(Goal.Plans.GoalLink.Note("the anchor narrative")),
          Some(Goal.Plans.GoalLink.Title("1177 B.C.: The Year Civilization Collapsed"))
        ),
        GoalLink(LinkKind.Query, Goal.Plans.GoalLink.Ref("late bronze age collapse drought"))
      ),
      schedules = List(
        GoalSchedule(
          Goal.Slug("bronze-age-collapse"),
          ScheduleKind.Currency,
          Goal.Plans.GoalSchedule.Cron("0 6 * * 1"),
          enabled = Goal.Plans.GoalSchedule.Enabled(true),
          budgetUsd = Some(GoalSchedule.BudgetUsd(0.5)),
          nextRunAt = Some(Goal.Plans.GoalSchedule.NextRunAt("2026-10-12T06:00:00Z")),
          lastNote = Some(GoalSchedule.LastNote("started run abc"))
        )
      )
    ),
    GoalView(
      goal(
        Goal.Slug("homeric-historicity"),
        Goal.Title("Homeric historicity: Troy, Ahhiyawa and oral tradition"),
        Goal.StatusValue("compiled"),
        Some(Goal.Slug("bronze-age"))
      ),
      links = List(article(GoalLink.Ref("ahhiyawa"), GoalLink.Title("Ahhiyawa")))
    ),
    GoalView(
      goal(
        Goal.Slug("comparative-religion"),
        Goal.Title("Comparative religion and folklore"),
        Goal.StatusValue("compiled")
      )
    ),
    GoalView(
      goal(
        Goal.Slug("egyptian-religion"),
        Goal.Title("Egyptian religion"),
        Goal.StatusValue("reviewed"),
        Some(Goal.Slug("comparative-religion"))
      ),
      links =
        List(item(GoalLink.QueueNumber(601), GoalLink.Title("Acquire Assmann, The Search for God in Ancient Egypt")))
    ),
    GoalView(
      goal(
        Goal.Slug("mystery-cults"),
        Goal.Title("Mystery cults and the ergot hypothesis"),
        Goal.StatusValue("reviewed"),
        Some(Goal.Slug("comparative-religion"))
      ),
      plan = Some(
        Plan(
          Goal.Slug("mystery-cults"),
          Goal.Plans.Plan.Rationale("Eleusis first, then the ergot debate."),
          subGoals = List(PlannedGoal(Goal.Slug("eleusis"), Goal.Title("Eleusis"))),
          queries = List(Goal.Plans.Plan.Queries("kykeon ergot"))
        )
      )
    ),
    GoalView(
      goal(Goal.Slug("eleusis"), Goal.Title("Eleusis"), Goal.StatusValue("planned"), Some(Goal.Slug("mystery-cults")))
    ),
    GoalView(goal(Goal.Slug("historiography"), Goal.Title("Historiography of the field"), Goal.StatusValue("seeding")))
  )
  private val status = Map(
    GoalLink.QueueNumber(495) -> ItemStatus.Wire("done"),
    GoalLink.QueueNumber(522) -> ItemStatus.Wire("open"),
    GoalLink.QueueNumber(601) -> ItemStatus.Wire("done")
  )

  /** The sample in the task report: the whole file for the fixture above. */
  private val expected =
    """# 🎯 GOALS — research goals
      |
      |_Generated by `histmersion export` / `histmersion queue export` from the Histmersion database. Do not edit: the next export overwrites this file. The source of truth is Histmersion (`histmersion goals …`)._
      |
      |8 goal(s): 3 top-level, 5 sub-goal(s) · planned 1 · seeding 1 · compiled 4 · reviewed 2
      |
      |---
      |
      |## The Bronze Age and earlier; the Late Bronze Age collapse
      |
      |`bronze-age` · compiled<BR>
      |**Tags:** bronze_age, bronze_age_collapse
      |
      |The Bronze Age and the periods before it; the Late Bronze Age collapse — causes, sequence, debates.
      |
      |### The Late Bronze Age collapse
      |
      |`bronze-age-collapse` · compiled<BR>
      |**Period:** 1300 BCE–1050 BCE
      |
      |Climate-primary vs stressor debate, resilience critiques.
      |
      |**Articles**
      |- [[bronze_age_collapse]] — Late Bronze Age Collapse (~1200 BCE)
      |- [[sea_peoples]] — The Sea Peoples
      |
      |**Sources**
      |- [[cline2014_year]] — 1177 B.C.: The Year Civilization Collapsed · the anchor narrative
      |
      |**Searches**
      |- "late bronze age collapse drought"
      |
      |**Queue items**
      |- [x] `#495` Ingest 10.1007/s10963-021-09163-7 (hybrid OA) — Meyer & Knapp 2021
      |- [ ] `#522` Dafna Langgut, Climate and Environment in the Southern Levant, 1300–300 BCE
      |- `#9999` (not found)
      |
      |**Schedules**
      |- currency · `0 6 * * 1` · on · cap 0.5000 USD
      |
      |### Homeric historicity: Troy, Ahhiyawa and oral tradition
      |
      |`homeric-historicity` · compiled
      |
      |**Articles**
      |- [[ahhiyawa]] — Ahhiyawa
      |
      |## Comparative religion and folklore
      |
      |`comparative-religion` · compiled
      |
      |### Egyptian religion
      |
      |`egyptian-religion` · reviewed
      |
      |**Queue items**
      |- [x] `#601` Acquire Assmann, The Search for God in Ancient Egypt
      |
      |### Mystery cults and the ergot hypothesis
      |
      |`mystery-cults` · reviewed
      |
      |**Approved plan**
      |
      |Eleusis first, then the ergot debate.
      |
      |- sub-goal `eleusis` — Eleusis
      |- query: kykeon ergot
      |
      |#### Eleusis
      |
      |`eleusis` · planned
      |
      |## Historiography of the field
      |
      |`historiography` · seeding
      |""".stripMargin.replace("<BR>", "  ") // a Markdown hard line break: two trailing spaces

  private def diff(a: String, b: String): String =
    val (as, bs) = (a.linesIterator.toVector, b.linesIterator.toVector)
    (0 until math.max(as.size, bs.size))
      .flatMap { i =>
        (as.lift(i), bs.lift(i)) match
          case (x, y) if x == y => None
          case (x, y)           =>
            Some(s"line ${i + 1}:\n  expected: ${x.getOrElse("<eof>")}\n  actual:   ${y.getOrElse("<eof>")}")
      }
      .take(10)
      .mkString("\n")

  test("the whole file: header, summary, the tree nested by heading depth, links, schedules, plan"):
    val out = GoalsExport.render(views, status)
    assertEquals(out, expected, diff(expected, out))

  test("it says it is an export: generated, overwritten, Histmersion is the source of truth"):
    val head = GoalsExport.render(Nil, Map.empty).linesIterator.take(3).mkString("\n")
    assert(head.startsWith(GoalsExport.Header), head)
    assert(head.contains("Generated") && head.contains("overwrites") && head.contains("source of truth"), head)

  test("no goals renders the header and a placeholder"):
    val out = GoalsExport.render(Nil, Map.empty)
    assert(out.contains("0 goal(s): 0 top-level, 0 sub-goal(s)"), out)
    assert(out.endsWith("_No goals._\n"), out)

  test("deterministic: the order rows and links arrive in does not change the file"):
    val shuffled = scala.util.Random(906).shuffle(views).map(v => v.modify(_.links).setTo(v.links.reverse))
    assertEquals(GoalsExport.render(shuffled, status), GoalsExport.render(views, status))

  test("values that change on their own (updated_at, a schedule's next/last firing) are left out"):
    val ticked = views.map { v =>
      v.modify(_.goal)
        .setTo(v.goal.modify(_.updatedAt).setTo(Some(Goal.UpdatedAt("2027-01-01 00:00:00+00"))))
        .modify(_.schedules)
        .setTo(
          v.schedules.map(
            _.copy(
              nextRunAt = Some(Goal.Plans.GoalSchedule.NextRunAt("2027-01-01T06:00:00Z")),
              lastRunAt = Some(Goal.Plans.GoalSchedule.LastRunAt("2026-12-31T06:00:00Z")),
              lastRun = Some(Goal.Plans.GoalSchedule.LastRun("run-xyz")),
              lastNote = Some(Goal.Plans.GoalSchedule.LastNote("skipped: budget"))
            )
          )
        )
    }
    assertEquals(GoalsExport.render(ticked, status), GoalsExport.render(views, status))

  test("a queue item's state comes from its own list; one that no longer exists keeps its id"):
    val out = GoalsExport.render(
      views,
      status.updated(GoalLink.QueueNumber(522), ItemStatus.Wire("done")).removed(GoalLink.QueueNumber(495))
    )
    assert(out.contains("- [x] `#522` Dafna Langgut"), out)
    assert(out.contains("- `#495` (not found)"), out)

  test("an article or source link whose target is gone is marked, not dropped"):
    val gone = List(
      GoalView(
        goal(Goal.Slug("g"), Goal.Title("G"), Goal.StatusValue("planned")),
        links = List(
          GoalLink(LinkKind.Article, Goal.Plans.GoalLink.Ref("deleted_article"), None, None),
          GoalLink(LinkKind.Source, Goal.Plans.GoalLink.Ref("gone2020"), Some(Goal.Plans.GoalLink.Note("why")), None)
        )
      )
    )
    val out = GoalsExport.render(gone, Map.empty)
    assert(out.contains("- [[deleted_article]] — (not found)"), out)
    assert(out.contains("- [[gone2020]] — (not found) · why"), out)

  test("multi-line text stays on its list line"):
    val v = List(
      GoalView(
        goal(Goal.Slug("g"), Goal.Title("G"), Goal.StatusValue("planned")),
        links = List(item(GoalLink.QueueNumber(1), GoalLink.Title("first line\n  continued")))
      )
    )
    assert(
      GoalsExport
        .render(v, Map(GoalLink.QueueNumber(1) -> ItemStatus.Wire("open")))
        .contains("- [ ] `#1` first line continued")
    )

  test("a goal whose parent is missing, or that sits in a parent cycle, is still shown — at the top level"):
    val odd = List(
      GoalView(
        goal(Goal.Slug("orphan"), Goal.Title("Orphan"), Goal.StatusValue("planned"), Some(Goal.Slug("deleted-parent")))
      ),
      GoalView(goal(Goal.Slug("a"), Goal.Title("A"), Goal.StatusValue("planned"), Some(Goal.Slug("b")))),
      GoalView(goal(Goal.Slug("b"), Goal.Title("B"), Goal.StatusValue("planned"), Some(Goal.Slug("a"))))
    )
    val placed = GoalsExport.tree(odd).map((v, d) => v.goal.slug -> d)
    assertEquals(placed, List("orphan" -> 0, "a" -> 0, "b" -> 0))
    val out = GoalsExport.render(odd, Map.empty)
    assert(out.contains("**Parent:** `deleted-parent` (not found)"), out)
    assert(out.contains("**Parent:** `b`\n") || out.contains("**Parent:** `b`  \n"), out)
    assertEquals(GoalsExport.summary(odd.map(_.goal)), "3 goal(s): 1 top-level, 2 sub-goal(s) · planned 3")

  test("headings stop at ######: a deep tree does not produce invalid Markdown"):
    val chain = (0 to 6).toList.map(i =>
      GoalView(
        goal(
          Goal.Slug(s"g$i"),
          Goal.Title(s"G$i"),
          Goal.StatusValue("planned"),
          Option.when(i > 0)(Goal.Slug(s"g${i - 1}"))
        )
      )
    )
    val out = GoalsExport.render(chain, Map.empty)
    assert(out.contains("\n###### G4\n") && out.contains("\n###### G6\n") && !out.contains("#######"), out)

  test("an unknown status still counts, after the lifecycle ones"):
    val gs = List(
      goal(Goal.Slug("a"), Goal.Title("A"), Goal.StatusValue("done")),
      goal(Goal.Slug("b"), Goal.Title("B"), Goal.StatusValue("paused")),
      goal(Goal.Slug("c"), Goal.Title("C"), Goal.StatusValue("planned"))
    )
    assertEquals(GoalsExport.summary(gs), "3 goal(s): 3 top-level, 0 sub-goal(s) · planned 1 · done 1 · paused 1")
