package com.histmersion.domain

import com.histmersion.domain.catalogue.events.EventPlan

import com.histmersion.api.MergeFixture
import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import MergeFixture.{home, src, uid, work}
import MergeFold.ArticleOutcome
import com.histmersion.domain.library.Confidence
import com.histmersion.util.data.exchange.DecisionKind
import com.histmersion.util.data.exchange.BundleFormat
import com.histmersion.api.automation.Queue
import com.histmersion.api.exchange.{BundleEventDating, BundleQueueItem}
import com.histmersion.domain.automation.Goal
import com.histmersion.domain.library.Source
import com.histmersion.util.data.exchange.Issue

/** Folding two records that are one into one (docs/research-merge-import.md §5.4): goals, queue items, events and
  * articles (a source's and a finding's folds are the library's: `SourcePlanSuite`, `FindingPlanSuite`).
  */
class MergeFoldSuite extends munit.FunSuite:

  private val At = "2026-10-01T09:00:00.000000Z"
  test("goals, queue items and events folded"):
    val gl = MergeFixture.goal("g1", "zz_g", "Goal", home, Some("home's plan")).copy(status = Goal.Status.Seeding)
    val gr = MergeFixture.goal("g2", "zz_g", "Goal", work, Some("work's plan")).copy(status = Goal.Status.Compiled)
    val g = MergeFold.foldGoal(gl, gr, "work")
    assertEquals(g.status, Goal.Status.Compiled)
    assertEquals(g.notes, Some("home's plan\n\n### From work\n\nwork's plan"))
    assertEquals(MergeFold.foldGoal(gl, gl.copy(uid = gr.uid), "work").notes, gl.notes)
    val q = BundleQueueItem(uid("q1"), home, "tasks", "Find the Kuhrt reprint", None, Queue.Open, None, At, None, At)
    val qd = q.copy(uid = uid("q2"), text = "find the  kuhrt reprint", status = Queue.Done, doneAt = Some(At))
    assertEquals(MergeFold.queueKey(q.text), MergeFold.queueKey(qd.text))
    assertEquals(MergeFold.foldQueue(q, qd).status, Queue.Done)
    assertEquals(MergeFold.foldQueue(q, qd).doneAt, Some(At))
    val e1 = MergeFixture.event("e1", "zz_e", "Here", home)
    val e2 = MergeFixture
      .event("e2", "zz_e", "There", work)
      .copy(datings = List(BundleEventDating("a2000_x", None, Some(-1200), None, None, Some(-1190), None, None)))
    assert(EventPlan.sameCore(e1, e2))
    assert(!EventPlan.sameCore(e1, e2.copy(notAfter = Some(-1100))))
    val e = EventPlan.foldEvent(e1, e2)
    assertEquals(e.label, "Here")
    assertEquals(e.datings.map(_.citekey), List("a2000_x"))

  test("articles: the same, ours ahead, theirs ahead, a clean three-way merge, a conflict, no base"):
    val base = "Intro.\n\nMiddle.\n\nEnd."
    def sha(s: String) = BundleFormat.sha256Hex(s)
    val ours = "Intro (home).\n\nMiddle.\n\nEnd."
    val theirs = "Intro.\n\nMiddle.\n\nEnd (work)."
    assertEquals(MergeFold.articleOutcome(ours, Nil, ours, Set.empty), ArticleOutcome.Same)
    assertEquals(MergeFold.articleOutcome(ours, List(sha(base) -> base), base, Set.empty), ArticleOutcome.Ours)
    assertEquals(MergeFold.articleOutcome(base, Nil, theirs, Set(sha(base))), ArticleOutcome.Theirs)
    assertEquals(
      MergeFold.articleOutcome(ours, List(sha(base) -> base), theirs, Set(sha(base))),
      ArticleOutcome.Merged("Intro (home).\n\nMiddle.\n\nEnd (work).", sha(base))
    )
    assertEquals(
      MergeFold.articleOutcome(ours, List(sha(base) -> base), "Intro (work).\n\nMiddle.\n\nEnd.", Set(sha(base))),
      ArticleOutcome.Conflict(1, sha(base))
    )
    assertEquals(MergeFold.articleOutcome(ours, List(sha(base) -> base), theirs, Set.empty), ArticleOutcome.NoBase)
    // the newest common version is the base
    val older = "Intro.\n\nEnd."
    assertEquals(
      MergeFold.articleOutcome(ours, List(sha(base) -> base, sha(older) -> older), theirs, Set(sha(older), sha(base))),
      ArticleOutcome.Merged("Intro (home).\n\nMiddle.\n\nEnd (work).", sha(base))
    )
