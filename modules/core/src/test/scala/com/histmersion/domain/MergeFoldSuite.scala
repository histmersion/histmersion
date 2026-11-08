package com.histmersion
package domain

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

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
import com.histmersion.domain.automation.Goal
import com.histmersion.domain.library.Source
import com.histmersion.util.data.exchange.Issue
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.library.Citekey

/** Folding two records that are one into one (docs/research-merge-import.md §5.4): goals, queue items, events and
  * articles (a source's and a finding's folds are the library's: `SourcePlanSuite`, `FindingPlanSuite`).
  */
class MergeFoldSuite extends munit.FunSuite:

  private val At = "2026-10-01T09:00:00.000000Z"
  test("goals, queue items and events folded"):
    val gl = MergeFixture
      .goal("g1", "zz_g", "Goal", home, Some("home's plan"))
      .copy(status = Bundle.Goal.Status(Goal.Status.Seeding))
    val gr = MergeFixture
      .goal("g2", "zz_g", "Goal", work, Some("work's plan"))
      .copy(status = Bundle.Goal.Status(Goal.Status.Compiled))
    val g = MergeFold.foldGoal(gl, gr, MergePlanner.Row.Label("work"))
    assertEquals(g.status, Goal.Status.Compiled)
    assertEquals(g.notes, Some("home's plan\n\n### From work\n\nwork's plan"))
    assertEquals(MergeFold.foldGoal(gl, gl.modify(_.uid).setTo(gr.uid), MergePlanner.Row.Label("work")).notes, gl.notes)
    val q = Bundle.QueueItem(
      uid("q1"),
      home,
      Bundle.QueueItem.List("tasks"),
      Bundle.QueueItem.Text("Find the Kuhrt reprint"),
      None,
      Bundle.QueueItem.Status(Queue.Open),
      None,
      Bundle.QueueItem.CreatedAt(At),
      None,
      Bundle.QueueItem.UpdatedAt(At)
    )
    val qd = q
      .modify(_.uid)
      .setTo(uid("q2"))
      .modify(_.text)
      .setTo(Bundle.QueueItem.Text("find the  kuhrt reprint"))
      .modify(_.status)
      .setTo(Bundle.QueueItem.Status(Queue.Done))
      .modify(_.doneAt)
      .setTo(Some(Bundle.QueueItem.DoneAt(At)))
    assertEquals(MergeFold.queueKey(q.text), MergeFold.queueKey(qd.text))
    assertEquals(MergeFold.foldQueue(q, qd).status, Queue.Done)
    assertEquals(MergeFold.foldQueue(q, qd).doneAt, Some(At))
    val e1 = MergeFixture.event("e1", "zz_e", "Here", home)
    val e2 = MergeFixture
      .event("e2", "zz_e", "There", work)
      .copy(datings =
        List(
          Bundle.EventDating(
            Citekey("a2000_x"),
            None,
            Some(Bundle.EventDating.NotBefore(-1200)),
            None,
            None,
            Some(Bundle.EventDating.NotAfter(-1190)),
            None,
            None
          )
        )
      )
    assert(EventPlan.sameCore(e1, e2))
    assert(!EventPlan.sameCore(e1, e2.modify(_.notAfter).setTo(Some(Bundle.Event.NotAfter(-1100)))))
    val e = EventPlan.foldEvent(e1, e2)
    assertEquals(e.label, "Here")
    assertEquals(e.datings.map(_.citekey), List("a2000_x"))

  test("articles: the same, ours ahead, theirs ahead, a clean three-way merge, a conflict, no base"):
    val base = MergeFold.ArticleBody("Intro.\n\nMiddle.\n\nEnd.")
    def sha(s: MergeFold.ArticleBody) = Sha256.assume(BundleFormat.sha256Hex(s.value).value)
    val ours = MergeFold.ArticleBody("Intro (home).\n\nMiddle.\n\nEnd.")
    val theirs = MergeFold.ArticleBody("Intro.\n\nMiddle.\n\nEnd (work).")
    assertEquals(MergeFold.articleOutcome(ours, Nil, ours, Set.empty), ArticleOutcome.Same)
    assertEquals(MergeFold.articleOutcome(ours, List(sha(base) -> base), base, Set.empty), ArticleOutcome.Ours)
    assertEquals(MergeFold.articleOutcome(base, Nil, theirs, Set(sha(base))), ArticleOutcome.Theirs)
    assertEquals(
      MergeFold.articleOutcome(ours, List(sha(base) -> base), theirs, Set(sha(base))),
      ArticleOutcome.Merged(ArticleOutcome.Merged.Body("Intro (home).\n\nMiddle.\n\nEnd (work)."), sha(base))
    )
    assertEquals(
      MergeFold.articleOutcome(
        ours,
        List(sha(base) -> base),
        MergeFold.ArticleBody("Intro (work).\n\nMiddle.\n\nEnd."),
        Set(sha(base))
      ),
      ArticleOutcome.Conflict(ArticleOutcome.Conflict.Conflicts(1), sha(base))
    )
    assertEquals(MergeFold.articleOutcome(ours, List(sha(base) -> base), theirs, Set.empty), ArticleOutcome.NoBase)
    // the newest common version is the base
    val older = MergeFold.ArticleBody("Intro.\n\nEnd.")
    assertEquals(
      MergeFold.articleOutcome(ours, List(sha(base) -> base, sha(older) -> older), theirs, Set(sha(older), sha(base))),
      ArticleOutcome.Merged(ArticleOutcome.Merged.Body("Intro (home).\n\nMiddle.\n\nEnd (work)."), sha(base))
    )
