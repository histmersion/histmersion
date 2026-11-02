package com.histmersion.domain

import com.histmersion.util.data.exchange.{BundleFormat, Diff3}
import com.histmersion.util.text.Fingerprint
import com.histmersion.api.{
  BundleArticle,
  BundleGoal,
  BundleGoalLink,
  BundleGoalSchedule,
  BundleQueueItem,
  BundleRange,
  Lint,
  Queue
}
import com.histmersion.api.given

import com.histmersion.values.{DecisionKind, GoalStatus, IssueKind}

/** How two records that are one are folded into one (docs/research-merge-import.md §5.4, milestones M4–M5): goals,
  * queue items and articles, and the small rules every kind's folds share (a source's and a finding's are the library's
  * `SourcePlan.fold` and `FindingPlan.fold`). The survivor is always THIS collection's row (left): it keeps its uid,
  * and the bundle's (right) uid becomes a redirect to it. Pure; the planner decides which records are one and what the
  * user chose, these functions only compute the result.
  *
  * Left is this collection, right the bundle — as everywhere in the merge.
  */
object MergeFold:

  // ── small rules ─────────────────────────────────────────────────────────────────────────────

  /** The richer of two spellings of one value: more words (a subtitle), then more letters outside ASCII (diacritics
    * kept), then more capitals (title case); a tie keeps the left.
    */
  def richer(l: String, r: String): String =
    def score(s: String) =
      (Fingerprint.tokens(s).size, s.count(c => c.isLetter && c > 127), s.count(_.isUpper), s.length)
    if summon[Ordering[(Int, Int, Int, Int)]].gt(score(r), score(l)) then r else l

  /** Left's values, then right's that are not already there (compared after `key`). */
  def union(l: List[String], r: List[String], key: String => String): List[String] =
    val seen = l.map(key).toSet
    (l ++ r.filterNot(x => seen(key(x)))).distinctBy(key)

  /** Tags compared after the canonical-tag normaliser (`non-canonical-tag`). */
  def unionTags(l: List[String], r: List[String]): List[String] = union(l, r, Lint.normaliseTag)

  /** Topics and regions compared folded. */
  def unionNames(l: List[String], r: List[String]): List[String] = union(l, r, s => Fingerprint.fold(s.trim))

  private def text(o: Option[String]): Option[String] = o.map(_.trim).filter(_.nonEmpty)

  /** The smallest range covering both; an open bound stays open. */
  def hull(a: BundleRange, b: BundleRange): BundleRange =
    BundleRange(a.start.zip(b.start).map((x, y) => math.min(x, y)), a.end.zip(b.end).map((x, y) => math.max(x, y)))

  /** Two ranges share a year (an open bound reaches any year; no range is compatible with any). */
  def overlap(a: Option[BundleRange], b: Option[BundleRange]): Boolean =
    a.zip(b).forall { (x, y) =>
      x.start.zip(y.end).forall((s, e) => s <= e) && y.start.zip(x.end).forall((s, e) => s <= e)
    }

  /** The narrower of two ranges (an open one is the widest); a missing one is no choice. */
  def narrower(a: Option[BundleRange], b: Option[BundleRange]): Option[BundleRange] =
    def width(r: BundleRange): Long = r.start.zip(r.end).map((s, e) => e - s).getOrElse(Long.MaxValue)
    (a, b) match
      case (Some(x), Some(y)) => Some(if width(y) < width(x) then y else x)
      case _                  => a.orElse(b)

  // ── goals ───────────────────────────────────────────────────────────────────────────────────

  /** One goal out of two (§5.4): the status further along `planned → … → done`, notes joined under a header naming
    * where the bundle's came from, tags and regions unioned; the links unioned, the schedules this collection's
    * ([[keepLocalSchedules]]), the run cap ours unless we have none, and the plan as [[planOutcome]] says —
    * `planDecision` answers a [[IssueKind.GoalPlanDiffers]].
    */
  def foldGoal(l: BundleGoal, r: BundleGoal, from: String, planDecision: Option[DecisionKind] = None): BundleGoal =
    def rank(s: String) = GoalStatus.all.indexOf(s)
    val notes = (text(l.notes), text(r.notes)) match
      case (Some(a), Some(b)) if a == b || a.contains(b) => Some(a)
      case (Some(a), Some(b))                            => Some(s"$a\n\n### From $from\n\n$b")
      case (a, b)                                        => a.orElse(b)
    val (plan, approved) = planOutcome(l, r) match
      case PlanOutcome.Theirs                                                  => (r.plan, r.planApprovedAt)
      case PlanOutcome.Differ if planDecision.contains(DecisionKind.KeepRight) => (r.plan, r.planApprovedAt)
      case _                                                                   => (l.plan, l.planApprovedAt)
    keepLocalSchedules(
      l.copy(
        status = if rank(r.status) > rank(l.status) then r.status else l.status,
        notes = notes,
        tags = unionTags(l.tags, r.tags),
        regions = unionNames(l.regions, r.regions),
        period = l.period.orElse(r.period),
        parentUid = l.parentUid.orElse(r.parentUid),
        plan = plan,
        planApprovedAt = approved,
        budgetUsd = l.budgetUsd.orElse(r.budgetUsd),
        rev = ""
      ),
      r
    )

  /** What two approved plans of one goal come to. */
  enum PlanOutcome:
    /** The same plan, or only ours: ours stays. */
    case Ours

    /** Only the bundle's, or the bundle's was approved later: take it. */
    case Theirs

    /** Two different plans and neither is known to be the newer: a person decides. */
    case Differ

  /** "Newer approved wins" (#881): equal or one missing is no question; two different plans are decided by their
    * approval times when both have one and they differ, else they are a question.
    */
  def planOutcome(l: BundleGoal, r: BundleGoal): PlanOutcome =
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
  def keepLocalSchedules(l: BundleGoal, r: BundleGoal): BundleGoal =
    def key(k: BundleGoalLink) = (k.kind, k.refUid.fold(k.ref)(_.toString))
    val ls = l.links.getOrElse(Nil)
    val links = (ls ++ r.links.getOrElse(Nil).filterNot(x => ls.exists(y => key(y) == key(x))))
      .sortBy(k => (k.kind, k.ref, k.refUid.fold("")(_.toString)))
    val ss = l.schedules.getOrElse(Nil)
    val schedules = (ss ++ r.schedules.getOrElse(Nil).filterNot(x => ss.exists(_.kind == x.kind))).sortBy(_.kind)
    l.copy(links = Option(links).filter(_.nonEmpty), schedules = Option(schedules).filter(_.nonEmpty))

  /** The schedules of one kind that both goals have and that differ: (kind, ours, theirs). */
  def scheduleDifferences(l: BundleGoal, r: BundleGoal): List[(String, BundleGoalSchedule, BundleGoalSchedule)] =
    l.schedules
      .getOrElse(Nil)
      .flatMap(a => r.schedules.getOrElse(Nil).find(_.kind == a.kind).filter(_ != a).map(b => (a.kind, a, b)))

  // ── queue items ─────────────────────────────────────────────────────────────────────────────

  /** A queue item's text as compared: folded, whitespace collapsed. */
  def queueKey(t: String): String = Fingerprint.fold(t).trim.replaceAll("\\s+", " ")

  /** One queue item out of two: `done` anywhere is done. */
  def foldQueue(l: BundleQueueItem, r: BundleQueueItem): BundleQueueItem =
    val done = l.status == Queue.Done || r.status == Queue.Done
    l.copy(
      status = if done then Queue.Done else l.status,
      doneAt = if l.status == Queue.Done then l.doneAt else if done then r.doneAt else l.doneAt,
      topic = l.topic.orElse(r.topic),
      reason = l.reason.orElse(r.reason),
      rev = ""
    )

  // ── articles (§5.4) ─────────────────────────────────────────────────────────────────────────

  /** What two versions of one article come to, given what each side's history holds. */
  enum ArticleOutcome:
    /** The same text. */
    case Same

    /** The bundle's text is an earlier version of ours: ours stays. */
    case Ours

    /** Ours is an earlier version of the bundle's: fast-forward to it. */
    case Theirs

    /** Both changed since a common base, and the three-way merge is clean. */
    case Merged(body: String, baseSha: String)

    /** Both changed since a common base, and `conflicts` regions changed differently on each side. */
    case Conflict(conflicts: Int, baseSha: String)

    /** Nothing in the two histories is shared: no base to merge from. */
    case NoBase

  /** `localHistory` is this collection's recorded versions of the article (sha256 of the body, the body), newest first;
    * `foreignHistory` the sha256s of the bundle's. The common base is the newest of ours the bundle's history also has.
    */
  def articleOutcome(
      localBody: String,
      localHistory: List[(String, String)],
      foreignBody: String,
      foreignHistory: Set[String]
  ): ArticleOutcome =
    val (ls, fs) = (BundleFormat.sha256Hex(localBody), BundleFormat.sha256Hex(foreignBody))
    if ls == fs then ArticleOutcome.Same
    else if localHistory.exists(_._1 == fs) then ArticleOutcome.Ours
    else if foreignHistory(ls) then ArticleOutcome.Theirs
    else
      localHistory.find((sha, _) => foreignHistory(sha)) match
        case None              => ArticleOutcome.NoBase
        case Some((sha, base)) =>
          val m = Diff3.merge(base, localBody, foreignBody)
          if m.clean then ArticleOutcome.Merged(m.text, sha) else ArticleOutcome.Conflict(m.conflicts, sha)

  /** The fields of an article folded around a chosen body: left's title and kind, the summary that is there, tags and
    * regions unioned.
    */
  def foldArticle(l: BundleArticle, r: BundleArticle, body: String): BundleArticle =
    l.copy(
      body = body,
      summary = if text(l.summary).isDefined then l.summary else r.summary.orElse(l.summary),
      tags = unionTags(l.tags, r.tags),
      regions = unionNames(l.regions, r.regions),
      period = l.period.orElse(r.period),
      rev = ""
    )
