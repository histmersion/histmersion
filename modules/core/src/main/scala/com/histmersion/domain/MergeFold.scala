package com.histmersion
package domain

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.exchange.MergeDecision

import com.histmersion.util.data.exchange.{BundleFormat, Diff3}
import com.histmersion.util.text.Fingerprint
import com.histmersion.api.checks.Lint
import com.histmersion.api.automation.Queue
import com.histmersion.api.given

import com.histmersion.domain.automation.Goal
import com.histmersion.api.exchange.Bundle
import io.circe.Json
import io.github.iltotore.iron.constraint.any.Pure

/** How two records that are one are folded into one (docs/research-merge-import.md §5.4, milestones M4–M5): goals,
  * queue items and articles, and the small rules every kind's folds share (a source's and a finding's are the library's
  * `SourcePlan.fold` and `FindingPlan.fold`). The survivor is always THIS collection's row (left): it keeps its uid,
  * and the bundle's (right) uid becomes a redirect to it. Pure; the planner decides which records are one and what the
  * user chose, these functions only compute the result.
  *
  * Left is this collection, right the bundle — as everywhere in the merge.
  */
object MergeFold:
  private given CanEqual[Json, Json] = CanEqual.derived

  type ArticleBody = ArticleBody.T
  object ArticleBody extends Newtype[String, Pure]
  type ConflictCount = ConflictCount.T
  object ConflictCount extends Newtype[Int, Pure]
  type QueueKey = QueueKey.T
  object QueueKey extends Newtype[String, Pure]

  // ── small rules ─────────────────────────────────────────────────────────────────────────────

  /** The richer of two spellings of one value: more words (a subtitle), then more letters outside ASCII (diacritics
    * kept), then more capitals (title case); a tie keeps the left.
    */
  def richer[A](l: A, r: A)(text: A => String): A =
    def score(a: A) =
      val s = text(a)
      (
        Fingerprint.tokens(Fingerprint.TokensS(s)).size,
        s.count(c => c.isLetter && c > 127),
        s.count(_.isUpper),
        s.length
      )
    if summon[Ordering[(Int, Int, Int, Int)]].gt(score(r), score(l)) then r else l

  /** Left's values, then right's that are not already there (compared after `key`). */
  def union[A](l: List[A], r: List[A], key: A => String): List[A] =
    val seen = l.map(key).toSet
    (l ++ r.filterNot(x => seen(key(x)))).distinctBy(key)

  /** Tags compared after the canonical-tag normaliser (`non-canonical-tag`). */
  def unionTags[A](l: List[A], r: List[A])(text: A => String): List[A] =
    union(l, r, a => Lint.normaliseTag(Lint.Tag(text(a))).value)

  /** Topics and regions compared folded. */
  def unionNames[A](l: List[A], r: List[A])(text: A => String): List[A] =
    union(l, r, a => Fingerprint.fold(Fingerprint.FoldS(text(a).trim)).value)

  private def trimmed[A](o: Option[A])(text: A => String): Option[String] = o.map(a => text(a).trim).filter(_.nonEmpty)

  /** The smallest range covering both; an open bound stays open. */
  def hull(a: Bundle.Range, b: Bundle.Range): Bundle.Range =
    Bundle.Range(
      a.start.zip(b.start).map((x, y) => if x <= y then x else y),
      a.end.zip(b.end).map((x, y) => if x >= y then x else y)
    )

  /** Two ranges share a year (an open bound reaches any year; no range is compatible with any). */
  def overlap(a: Option[Bundle.Range], b: Option[Bundle.Range]): Boolean =
    a.zip(b).forall { (x, y) =>
      x.start.zip(y.end).forall((s, e) => s <= e) && y.start.zip(x.end).forall((s, e) => s <= e)
    }

  /** The narrower of two ranges (an open one is the widest); a missing one is no choice. */
  def narrower(a: Option[Bundle.Range], b: Option[Bundle.Range]): Option[Bundle.Range] =
    def width(r: Bundle.Range): Long = r.start.zip(r.end).map((s, e) => e.value - s.value).getOrElse(Long.MaxValue)
    (a, b) match
      case (Some(x), Some(y)) => Some(if width(y) < width(x) then y else x)
      case _                  => a.orElse(b)

  // ── goals ───────────────────────────────────────────────────────────────────────────────────

  /** One goal out of two (§5.4): the status further along `planned → … → done`, notes joined under a header naming
    * where the bundle's came from, tags and regions unioned; the links unioned, the schedules this collection's
    * ([[keepLocalSchedules]]), the run cap ours unless we have none, and the plan as [[planOutcome]] says —
    * `planDecision` answers a [[IssueKind.GoalPlanDiffers]].
    */
  def foldGoal(
      l: Bundle.Goal,
      r: Bundle.Goal,
      from: MergePlanner.Row.Label,
      planDecision: Option[MergeDecision.Kind] = None
  ): Bundle.Goal =
    def rank(s: Bundle.Goal.Status) = Goal.Status.all.map(_.value).indexOf(s.value)
    val notes = (trimmed(l.notes)(_.value), trimmed(r.notes)(_.value)) match
      case (Some(a), Some(b)) if a == b || a.contains(b) => Some(a)
      case (Some(a), Some(b))                            => Some(s"$a\n\n### From $from\n\n$b")
      case (a, b)                                        => a.orElse(b)
    val (plan, approved) = planOutcome(l, r) match
      case PlanOutcome.Theirs                                                        => (r.plan, r.planApprovedAt)
      case PlanOutcome.Differ if planDecision.contains(MergeDecision.Kind.KeepRight) => (r.plan, r.planApprovedAt)
      case _                                                                         => (l.plan, l.planApprovedAt)
    keepLocalSchedules(
      l.modify(_.status)
        .setTo(if rank(r.status) > rank(l.status) then r.status else l.status)
        .modify(_.notes)
        .setTo(notes.map(Bundle.Goal.Notes(_)))
        .modify(_.tags)
        .setTo(unionTags(l.tags, r.tags)(_.value))
        .modify(_.regions)
        .setTo(unionNames(l.regions, r.regions)(_.value))
        .modify(_.period)
        .setTo(l.period.orElse(r.period))
        .modify(_.parentUid)
        .setTo(l.parentUid.orElse(r.parentUid))
        .modify(_.plan)
        .setTo(plan)
        .modify(_.planApprovedAt)
        .setTo(approved)
        .modify(_.budgetUsd)
        .setTo(l.budgetUsd.orElse(r.budgetUsd))
        .modify(_.rev)
        .setTo(Bundle.Goal.Rev("")),
      r
    )

  /** What two approved plans of one goal come to. */
  enum PlanOutcome derives CanEqual:
    /** The same plan, or only ours: ours stays. */
    case Ours

    /** Only the bundle's, or the bundle's was approved later: take it. */
    case Theirs

    /** Two different plans and neither is known to be the newer: a person decides. */
    case Differ

  /** "Newer approved wins" (#881): equal or one missing is no question; two different plans are decided by their
    * approval times when both have one and they differ, else they are a question.
    */
  def planOutcome(l: Bundle.Goal, r: Bundle.Goal): PlanOutcome =
    (l.plan, r.plan) match
      case (_, None)                    => PlanOutcome.Ours
      case (None, Some(_))              => PlanOutcome.Theirs
      case (Some(a), Some(b)) if a == b => PlanOutcome.Ours
      case _                            =>
        (l.planApprovedAt, r.planApprovedAt) match
          case (Some(a), Some(b)) if b > a => PlanOutcome.Theirs
          case (Some(a), Some(b)) if a > b => PlanOutcome.Ours
          case _                           => PlanOutcome.Differ

  /** `l`'s links and `r`'s that `l` lacks (one link per kind and target: the uid when it has one, else the label), in a
    * stable order; `l`'s schedules, and `r`'s of a kind `l` has none of — this collection's schedules are kept (#881),
    * differences are reported by the plan. Absent when empty.
    */
  def keepLocalSchedules(l: Bundle.Goal, r: Bundle.Goal): Bundle.Goal =
    def key(k: Bundle.GoalLink) = (k.kind, k.refUid.fold(k.ref.value)(_.toString))
    val ls = l.links.getOrElse(Nil)
    val links = (ls ++ r.links.getOrElse(Nil).filterNot(x => ls.exists(y => key(y) == key(x))))
      .sortBy(k => (k.kind.value, k.ref.value, k.refUid.fold("")(_.toString)))
    val ss = l.schedules.getOrElse(Nil)
    val schedules = (ss ++ r.schedules.getOrElse(Nil).filterNot(x => ss.exists(_.kind == x.kind))).sortBy(_.kind.value)
    l.modify(_.links)
      .setTo(Option(links).filter(_.nonEmpty))
      .modify(_.schedules)
      .setTo(Option(schedules).filter(_.nonEmpty))

  /** The schedules of one kind that both goals have and that differ: (kind, ours, theirs). */
  def scheduleDifferences(
      l: Bundle.Goal,
      r: Bundle.Goal
  ): List[(Bundle.GoalSchedule.Kind, Bundle.GoalSchedule, Bundle.GoalSchedule)] =
    l.schedules
      .getOrElse(Nil)
      .flatMap(a => r.schedules.getOrElse(Nil).find(_.kind == a.kind).filter(_ != a).map(b => (a.kind, a, b)))

  // ── queue items ─────────────────────────────────────────────────────────────────────────────

  /** A queue item's text as compared: folded, whitespace collapsed. */
  def queueKey(t: Bundle.QueueItem.Text): QueueKey =
    QueueKey(Fingerprint.fold(Fingerprint.FoldS(t.value)).value.trim.replaceAll("\\s+", " "))

  /** One queue item out of two: `done` anywhere is done. */
  def foldQueue(l: Bundle.QueueItem, r: Bundle.QueueItem): Bundle.QueueItem =
    val done = l.status.value == Queue.Done || r.status.value == Queue.Done
    l.modify(_.status)
      .setTo(if done then Bundle.QueueItem.Status(Queue.Done) else l.status)
      .modify(_.doneAt)
      .setTo(if l.status.value == Queue.Done then l.doneAt else if done then r.doneAt else l.doneAt)
      .modify(_.topic)
      .setTo(l.topic.orElse(r.topic))
      .modify(_.reason)
      .setTo(l.reason.orElse(r.reason))
      .modify(_.rev)
      .setTo(Bundle.QueueItem.Rev(""))

  // ── articles (§5.4) ─────────────────────────────────────────────────────────────────────────

  /** What two versions of one article come to, given what each side's history holds. */
  enum ArticleOutcome derives CanEqual:
    /** The same text. */
    case Same

    /** The bundle's text is an earlier version of ours: ours stays. */
    case Ours

    /** Ours is an earlier version of the bundle's: fast-forward to it. */
    case Theirs

    /** Both changed since a common base, and the three-way merge is clean. */
    case Merged(body: ArticleOutcome.Merged.Body, baseSha: ArticleOutcome.Merged.BaseSha)

    /** Both changed since a common base, and `conflicts` regions changed differently on each side. */
    case Conflict(conflicts: ArticleOutcome.Conflict.Conflicts, baseSha: ArticleOutcome.Conflict.BaseSha)

    /** Nothing in the two histories is shared: no base to merge from. */
    case NoBase

  object ArticleOutcome:
    object Merged:
      export MergeFold.ArticleBody as Body
      type BaseSha = Sha256
      val BaseSha: Sha256.type = Sha256
    object Conflict:
      export MergeFold.ConflictCount as Conflicts
      type BaseSha = Sha256
      val BaseSha: Sha256.type = Sha256

  /** `localHistory` is this collection's recorded versions of the article (sha256 of the body, the body), newest first;
    * `foreignHistory` the sha256s of the bundle's. The common base is the newest of ours the bundle's history also has.
    */
  def articleOutcome(
      localBody: ArticleBody,
      localHistory: List[(Sha256, ArticleBody)],
      foreignBody: ArticleBody,
      foreignHistory: Set[Sha256]
  ): ArticleOutcome =
    val (ls, fs) = (shaOf(localBody), shaOf(foreignBody))
    if ls == fs then ArticleOutcome.Same
    else if localHistory.exists(_._1 == fs) then ArticleOutcome.Ours
    else if foreignHistory(ls) then ArticleOutcome.Theirs
    else
      localHistory.find((sha, _) => foreignHistory(sha)) match
        case None              => ArticleOutcome.NoBase
        case Some((sha, base)) =>
          val m = Diff3.merge(
            Diff3.MergeBase(base.value),
            Diff3.MergeLeft(localBody.value),
            Diff3.MergeRight(foreignBody.value)
          )
          if m.clean then ArticleOutcome.Merged(ArticleBody(m.text.value), sha)
          else ArticleOutcome.Conflict(ConflictCount(m.conflicts.value), sha)

  private def shaOf(body: ArticleBody): Sha256 =
    Sha256.assume(BundleFormat.sha256Hex(body.value).value)

  /** The fields of an article folded around a chosen body: left's title and kind, the summary that is there, tags and
    * regions unioned.
    */
  def foldArticle(l: Bundle.Article, r: Bundle.Article, body: ArticleBody): Bundle.Article =
    l.modify(_.body)
      .setTo(Bundle.Article.Body(body.value))
      .modify(_.summary)
      .setTo(if trimmed(l.summary)(_.value).isDefined then l.summary else r.summary.orElse(l.summary))
      .modify(_.tags)
      .setTo(unionTags(l.tags, r.tags)(_.value))
      .modify(_.regions)
      .setTo(unionNames(l.regions, r.regions)(_.value))
      .modify(_.period)
      .setTo(l.period.orElse(r.period))
      .modify(_.rev)
      .setTo(Bundle.Article.Rev(""))
