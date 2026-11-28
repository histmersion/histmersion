package com.histmersion
package domain
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec

import com.histmersion.util.problem.Text.sentence
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import scala.annotation.tailrec
import hearth.kindlings.optics.syntax.*
import com.histmersion.api.{Plural, WireEnum}
import com.histmersion.api.research.CritiqueRef
import com.histmersion.api.given

import com.histmersion.util.json.JsonCodec

import com.histmersion.domain.ReviewArticle.*
import com.histmersion.domain.ReviewArticle.Finding
import com.histmersion.api.WireEnumValues
import com.histmersion.domain.research.workflows.ReviewComparison
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.automation.Run
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.TwoCases

/** The review ledger of one article (docs/research-review-convergence.md §6): every finding the `review-article` panel
  * ever raised on it, under an id that never changes, with what became of it — open, fixed (verified by the reviewer
  * that raised it), rejected (with the record's sentence) or won't fix — and the lessons learned from rejections and
  * failed fixes. It persists across runs, so a later run starts from it; the reviewers and the fixer read it in their
  * prompts.
  *
  * Everything here is pure: the pipeline loads the ledger, threads it through its passes as a value (each pass's new
  * ledger is a checkpointed step output) and writes it back through the run's gate.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@movesTo(FeaturePlace.ResearchWorkflows)
object ReviewLedger:
  type ClosedLimit = ClosedLimit.T
  object ClosedLimit extends Newtype[Int, Pure]
  type PassageMargin = PassageMargin.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object PassageMargin extends Newtype[Int, Pure]
  type StubbornLimit = StubbornLimit.T
  object StubbornLimit extends Newtype[Int, Pure]
  type TaskText = TaskText.T
  object TaskText extends Newtype[String, Pure]
  type TaskTextLimit = TaskTextLimit.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object TaskTextLimit extends Newtype[Int, Pure]

  export automation.Run.Id as ApplyChecksRun
  export ReviewArticle.Merged.Round as ApplyChecksRound
  export automation.Run.Id as RecheckMechanicalRun
  export ReviewArticle.Merged.Round as RecheckMechanicalRound
  export automation.Run.Id as RecheckTermCountRun
  export ReviewArticle.Merged.Round as RecheckTermCountRound
  export automation.Run.Id as ApplyFixRun
  export ReviewArticle.Merged.Round as ApplyFixRound
  export automation.Run.Id as LearnRun
  export ReviewArticle.Merged.Round as LearnRound
  export automation.Run.Id as ApplyRecheckRun
  export ReviewArticle.Merged.Round as ApplyRecheckRound
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type ArticleTaskSlug = research.Slug
  val ArticleTaskSlug: research.Slug.type = research.Slug

  enum Status(val wire: WireEnumValues.Wire) derives CanEqual:
    case Open extends Status(WireEnumValues.Wire("open"))
    case Fixed extends Status(WireEnumValues.Wire("fixed"))
    case Rejected extends Status(WireEnumValues.Wire("rejected"))
    case WontFix extends Status(WireEnumValues.Wire("wont-fix"))

    /** A missing-critique finding the fixer answered with a stored critique reason (§6.8): the critique exists, and
      * does not bear on what the article cites the source for.
      */
    case SetAside extends Status(WireEnumValues.Wire("set-aside"))

  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  object Status:
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def parse(s: WireEnumValues.Wire): Option[Status] = values.find(_.wire.value == s.value.trim.toLowerCase)
    given WireEnumValues[Status]:
      def all: Array[Status] = values
      def wire(e: Status): WireEnumValues.Wire = e.wire
      def fromWire(s: WireEnumValues.Wire): Option[Status] = parse(s)
    given JsonValueCodec[Status] = WireEnum.codec

  /** Where a lesson came from: a rejection the fixer proved, or a reviewer's re-check. */
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  enum LessonSource(val wire: WireEnumValues.Wire):
    case Rejection extends LessonSource(WireEnumValues.Wire("rejection"))
    case Recheck extends LessonSource(WireEnumValues.Wire("recheck"))

  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  object LessonSource:
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def parse(s: WireEnumValues.Wire): Option[LessonSource] = values.find(_.wire.value == s.value.trim.toLowerCase)
    given WireEnumValues[LessonSource]:
      def all: Array[LessonSource] = values
      def wire(e: LessonSource): WireEnumValues.Wire = e.wire
      def fromWire(s: WireEnumValues.Wire): Option[LessonSource] = parse(s)
    given JsonValueCodec[LessonSource] = WireEnum.codec

  /** How a finding was closed: by whom (a reviewer's wire name, `fixer`, or `mechanical` for code), in which pass of
    * which run, and the evidence — the quote of the fixed text, or the record's sentence of a rejection (with its
    * citekey), or the reason a minor one is declined.
    */
  final case class Closure(
      by: Closure.By,
      round: Closure.Round,
      run: Closure.Run,
      note: Closure.Note = Closure.Note(""),
      quote: Closure.Quote = Closure.Quote(""),
      citekey: Option[Citekey] = None
  ) derives JsonCodec

  object Closure:
    export ReviewArticle.Reviewer.Wire as By
    export ReviewArticle.Merged.Round
    export automation.Run.Id as Run
    export ReviewArticle.Check.Note
    export ReviewArticle.Evidence.Quote

  /** One finding in the ledger. `claimed`: the fixer lists it as applied, and its reviewer has not checked it yet.
    * `notFixed`: the last check's word that it is not (or the rejection it was raised again after); `notFixedCount`:
    * how often it was found not fixed — by its reviewer, or because the fixer left it unanswered or claimed it without
    * the change code checks for (§6.9). `reopened`: how often a fixed finding came back. `deferred`: the run whose
    * FINAL pass raised it (or raised it to MAJOR) as a new finding — it does not make that run's verdict MAJOR, stays
    * open, and is the next run's work (§6.9).
    */
  final case class Entry(
      finding: Finding,
      run: Entry.Run,
      round: Entry.Round,
      status: Status = Status.Open,
      claimed: Entry.Claimed = Entry.Claimed.Open,
      notFixed: Option[Entry.NotFixed] = None,
      reopened: Entry.Reopened = Entry.Reopened(0),
      closed: Option[Closure] = None,
      notFixedCount: Entry.NotFixedCount = Entry.NotFixedCount(0),
      deferred: Option[Entry.Deferred] = None
  ) derives JsonCodec:
    def id: Finding.Id = finding.id
    def open: Boolean = status == Status.Open
    def major: Boolean = finding.major
    def reviewer: Reviewer = finding.reviewer

    /** Deferred by `run`'s final pass: not part of that run's verdict. */
    def deferredIn(run: Run.Id): Boolean = open && deferred.contains(run)

  object Entry:

    export automation.Run.Id as Run
    export ReviewArticle.Merged.Round
    enum Claimed derives CanEqual:
      case Claimed, Open
    object Claimed extends TwoCases[Claimed]:
      extension (claimed: Claimed) def claimed: Boolean = claimed == Claimed
    type NotFixed = NotFixed.T
    object NotFixed extends Newtype[String, Pure]
    type Reopened = Reopened.T
    object Reopened extends Newtype[Int, Pure]
    type NotFixedCount = NotFixedCount.T
    @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
    object NotFixedCount extends Newtype[Int, Pure]
    export automation.Run.Id as Deferred

  final case class Lesson(text: Lesson.Text, source: LessonSource, run: Lesson.Run, round: Lesson.Round)
      derives JsonCodec

  object Lesson:
    export ReviewArticle.Recheck.Lessons as Text
    export automation.Run.Id as Run
    export ReviewArticle.Merged.Round

  /** `task`: the article's one backlog item (#934), while it has one. */
  final case class Ledger(
      slug: Ledger.Slug,
      entries: List[Entry] = Nil,
      lessons: List[Lesson] = Nil,
      task: Option[Ledger.Task] = None
  ) derives JsonCodec:
    def open: List[Entry] = entries.filter(_.open)
    def openMajors: List[Entry] = open.filter(_.major)
    def entry(id: Finding.Id): Option[Entry] = entries.find(_.id == id)

    /** The open findings the fixer claims, of one reviewer: what its re-check verifies. */
    def claimed(r: Reviewer): List[Entry] = open.filter(e => e.claimed.claimed && e.reviewer == r)
    def verdict: Verdict = Verdict.of(open.map(_.finding))

  object Ledger:

    @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
    type Slug = research.Slug
    val Slug: research.Slug.type = research.Slug
    type Task = Task.T
    object Task extends Newtype[Int, Pure]

  /** At most this many lessons are kept (the newest). */
  val MaxLessons = 15

  // ── matching ───────────────────────────────────────────────────────────────────────────────────

  /** A mechanical finding is the same one when code says the same thing about the same passage: its kind, its place,
    * its problem up to the first colon (a coverage count changes as reasons are given; the passage does not) and its
    * quote.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def mechanicalKey(f: Finding): (String, String, String, String) =
    (
      f.kind.value,
      normalised(f.where.value).value,
      normalised(f.problem.head).value,
      normalised(f.quote.value).value
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def normalised(s: String): AgentVerify.NormalisedText = AgentVerify.normalise(AgentVerify.Text(s))

  /** Is `b` the finding `a` again? The same reviewer, and the same passage (quotes equal, or one inside the other when
    * both are at least 20 characters), or the same `where` and a problem that shares at least half its words
    * ([[CompareRuns.similarity]]). Mechanical findings compare by [[mechanicalKey]]; the cold reader's term-count
    * findings ([[TermCount]]) are one finding whatever the count or wording.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def same(a: Finding, b: Finding): Boolean =
    a.reviewer == b.reviewer && {
      if a.reviewer == Reviewer.Mechanical then mechanicalKey(a) == mechanicalKey(b)
      else if TermCount.is(a) || TermCount.is(b) then TermCount.is(a) && TermCount.is(b)
      else
        val (qa, qb) = (normalised(a.quote.value).value, normalised(b.quote.value).value)
        val quoted = qa.nonEmpty && qb.nonEmpty &&
          (qa == qb || (math.min(qa.length, qb.length) >= 20 && (qa.contains(qb) || qb.contains(qa))))
        quoted || (normalised(a.where.value) == normalised(b.where.value) &&
          CompareRuns
            .similarity(CompareRuns.Text(a.problem.value), CompareRuns.Text(b.problem.value))
            .value >= ReviewComparison.ProblemSimilarity)
    }

  /** The next ledger id of a reviewer: its letter and one more than the highest number it used. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def nextId(l: Ledger, r: Reviewer): Finding.Id =
    val used =
      l.entries.map(_.id.value).filter(_.startsWith(r.prefix.value)).flatMap(_.drop(r.prefix.value.length).toIntOption)
    Finding.Id(s"${r.prefix}${used.maxOption.getOrElse(0) + 1}")

  // ── transitions ────────────────────────────────────────────────────────────────────────────────

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def update(l: Ledger, id: Finding.Id)(f: Entry => Entry): Ledger =
    l.modify(_.entries).setTo(l.entries.map(e => if e.id == id then f(e) else e))

  /** Record a pass's findings (ids per pass, as the reviewers' answers were numbered). A finding the ledger has
    * already: an open one keeps its id (and is no longer "claimed": it was raised again); a fixed one is RE-OPENED (a
    * regression); a rejected one is re-opened and flagged escalated; a declined minor stays declined unless it comes
    * back MAJOR. Anything else is a new entry with the next id. Returns the ledger and the findings under their ledger
    * ids, in the order given.
    */
  @tolerated(LintRule.Copy, "pending G19 (lenses instead of .copy)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def absorb(
      l: Ledger,
      fs: List[Finding],
      run: Run.Id,
      round: Closure.Round
  ): (Ledger, List[Finding]) =
    @tailrec def absorbAll(
        rest: List[Finding],
        led: Ledger,
        out: Vector[Finding],
        seen: Set[Finding.Id]
    ): (Ledger, List[Finding]) =
      rest match
        case Nil       => (led, out.toList)
        case f :: tail =>
          led.entries.find(e => !seen(e.id) && same(e.finding, f)) match
            case Some(e) =>
              val id = e.id
              val next: Entry = e.status match
                case Status.Open =>
                  e.copy(
                    finding = f.copy(
                      id = id,
                      escalated =
                        (if f.escalated.escalated || e.finding.escalated.escalated then Finding.Escalated.Escalated
                         else Finding.Escalated.Regular)
                    ),
                    claimed = Entry.Claimed.Open
                  )
                case Status.Fixed =>
                  e.copy(
                    finding = f.copy(id = id),
                    status = Status.Open,
                    claimed = Entry.Claimed.Open,
                    notFixed = Some(
                      Entry.NotFixed(s"fixed in pass ${e.closed.fold(0)(_.round.value)}, raised again in pass $round")
                    ),
                    reopened = Entry.Reopened(e.reopened.value + 1),
                    closed = None
                  )
                case Status.Rejected =>
                  e.copy(
                    finding = f.copy(id = id, escalated = Finding.Escalated.Escalated),
                    status = Status.Open,
                    claimed = Entry.Claimed.Open,
                    notFixed = e.closed.map(c =>
                      Entry.NotFixed(s"rejected before${c.citekey.fold("")(ck => s" ([[$ck]])")}: ${c.note}".take(300))
                    ),
                    closed = None
                  )
                case Status.WontFix | Status.SetAside if f.major =>
                  e.copy(
                    finding = f.copy(id = id),
                    status = Status.Open,
                    claimed = Entry.Claimed.Open,
                    notFixed = e.closed
                      .filter(_ => e.status == Status.SetAside)
                      .map(c => Entry.NotFixed(s"set aside before: ${c.note}".take(300))),
                    closed = None
                  )
                case Status.WontFix | Status.SetAside => e
              absorbAll(tail, update(led, id)(_ => next), out :+ next.finding, seen + id)
            case None =>
              val id = nextId(led, f.reviewer)
              val g = f.copy(id = id)
              absorbAll(tail, led.modify(_.entries).using(_ :+ Entry(g, run, round)), out :+ g, seen + id)
    absorbAll(fs, l, Vector.empty, Set.empty)

  /** A reviewer's checks of its own findings: fixed → closed by that reviewer in this pass (with the quote of the fixed
    * text); not fixed → open, unclaimed, with the reviewer's note. A check of another reviewer's finding, or of a
    * closed one, changes nothing.
    */
  @tolerated(LintRule.Copy, "pending G19 (lenses instead of .copy)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
  def applyChecks(
      l: Ledger,
      r: Reviewer,
      checks: List[Check],
      run: ReviewLedger.ApplyChecksRun,
      round: ReviewLedger.ApplyChecksRound
  ): Ledger =
    checks.foldLeft(l) { (led, c) =>
      led.entry(c.id).filter(e => e.open && e.reviewer == r).fold(led) { _ =>
        update(led, c.id) { e =>
          if c.fixed.fixed then
            e.copy(
              status = Status.Fixed,
              claimed = Entry.Claimed.Open,
              notFixed = None,
              closed = Some(Closure(r.wire, round, run, Closure.Note(c.note.value), c.quote))
            )
          else
            e.copy(
              claimed = Entry.Claimed.Open,
              notFixed = Some(Entry.NotFixed(Option(c.note.value).filter(_.nonEmpty).getOrElse("not fixed"))),
              notFixedCount = Entry.NotFixedCount(e.notFixedCount.value + 1)
            )
        }
      }
    }

  /** The mechanical re-check, by code: an open mechanical finding that no longer recurs is fixed; one that recurs stays
    * open (unclaimed); a new one is opened.
    */
  @tolerated(LintRule.Copy, "pending G19 (lenses instead of .copy)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
  def recheckMechanical(
      l: Ledger,
      now: List[Finding],
      run: ReviewLedger.RecheckMechanicalRun,
      round: ReviewLedger.RecheckMechanicalRound
  ): (Ledger, List[Finding]) =
    val gone = l.open.filter(_.reviewer == Reviewer.Mechanical).filterNot(e => now.exists(same(e.finding, _)))
    val closed = gone.foldLeft(l)((led, e) =>
      update(led, e.id)(
        _.copy(
          status = Status.Fixed,
          claimed = Entry.Claimed.Open,
          notFixed = None,
          closed = Some(Closure(Reviewer.Mechanical.wire, round, run, Closure.Note("no longer found by the checks")))
        )
      )
    )
    absorb(closed, now, run, round)

  /** The term count after a full panel, by code: the cold reader's count is the whole of that finding, so an open count
    * entry the pass did not raise again (8 terms or fewer now) is fixed. One it did raise again is matched by
    * [[absorb]], which replaces it — a MAJOR stored by an earlier run becomes the pass's minor one under the same id.
    */
  @tolerated(LintRule.Copy, "pending G19 (lenses instead of .copy)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
  def recheckTermCount(
      l: Ledger,
      cold: List[Finding],
      run: ReviewLedger.RecheckTermCountRun,
      round: ReviewLedger.RecheckTermCountRound
  ): Ledger =
    if cold.exists(TermCount.is) then l
    else
      l.open
        .filter(e => TermCount.is(e.finding))
        .foldLeft(l)((led, e) =>
          update(led, e.id)(
            _.copy(
              status = Status.Fixed,
              claimed = Entry.Claimed.Open,
              notFixed = None,
              closed = Some(
                Closure(
                  Reviewer.Mechanical.wire,
                  round,
                  run,
                  Closure.Note("the cold reader's count is no longer above the threshold")
                )
              )
            )
          )
        )

  /** The fixer's verified revision: what it applied is claimed (its reviewer re-checks it), a rejection code verified
    * closes the finding with the record's sentence (and teaches a lesson), a declined minor is closed as won't fix.
    */
  @tolerated(LintRule.Copy, "pending G19 (lenses instead of .copy)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
  def applyFix(l: Ledger, f: Fix, run: ReviewLedger.ApplyFixRun, round: ReviewLedger.ApplyFixRound): Ledger =
    // what the fixer applied, and what code's pairing repair answered (§6.9): both re-checked by the reviewer
    val claimed = (f.applied ++ f.repaired).distinct.foldLeft(l)((led, id) =>
      led.entry(id).filter(_.open).fold(led)(_ => update(led, id)(_.copy(claimed = Entry.Claimed.Claimed)))
    )
    // what the fixer neither applied nor rejected, or claimed without the change: not fixed, with why
    val unanswered = f.notFixed.foldLeft(claimed)((led, w) =>
      led
        .entry(w.id)
        .filter(e => e.open && !e.claimed.claimed)
        .fold(led)(_ =>
          update(led, w.id)(e =>
            e.copy(
              notFixed = Some(Entry.NotFixed(s"pass $round: ${w.reason}".take(300))),
              notFixedCount = Entry.NotFixedCount(e.notFixedCount.value + 1)
            )
          )
        )
    )
    val rejected = f.rejected.foldLeft(unanswered)((led, r) =>
      led
        .entry(r.id)
        .filter(_.open)
        .fold(led)(_ =>
          update(led, r.id)(
            _.copy(
              status = Status.Rejected,
              claimed = Entry.Claimed.Open,
              closed = Some(
                Closure(Reviewer.Wire("fixer"), round, run, Closure.Note(s"${r.reason}"), r.quote, Some(r.citekey))
              )
            )
          )
        )
    )
    val declined = f.wontFix.foldLeft(rejected)((led, w) =>
      led
        .entry(w.id)
        .filter(e => e.open && !e.major)
        .fold(led)(_ =>
          update(led, w.id)(
            _.copy(
              status = Status.WontFix,
              claimed = Entry.Claimed.Open,
              closed = Some(Closure(Reviewer.Wire("fixer"), round, run, Closure.Note(s"${w.reason}")))
            )
          )
        )
    )
    // a critique set aside: closed with the reason stored with the article and the pair it is about
    val setAside = f.setAside.foldLeft(declined)((led, a) =>
      led
        .entry(a.id)
        .filter(_.open)
        .fold(led)(_ =>
          update(led, a.id)(e =>
            e.copy(
              finding = e.finding.copy(pair = Some(a.pair)),
              status = Status.SetAside,
              claimed = Entry.Claimed.Open,
              closed = Some(
                Closure(
                  Reviewer.Wire("fixer"),
                  round,
                  run,
                  Closure.Note(s"${a.reason}"),
                  Closure.Quote(a.pair.wire.value)
                )
              )
            )
          )
        )
    )
    learn(
      setAside,
      f.rejected.map(r => Lesson.Text(s"[[${r.citekey}]]: ${r.reason}")),
      LessonSource.Rejection,
      run,
      round
    )

  /** Add lessons: trimmed, at most [[LessonChars]] each, a lesson already held (normalised) not twice, the newest
    * [[MaxLessons]] kept.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
  def learn(
      l: Ledger,
      texts: List[Lesson.Text],
      source: LessonSource,
      run: ReviewLedger.LearnRun,
      round: ReviewLedger.LearnRound
  ): Ledger =
    val fresh = texts.map(_.value.trim.replaceAll("\\s+", " ").take(LessonChars)).filter(_.nonEmpty)
    val all = fresh.foldLeft(l.lessons.toVector) { (acc, t) =>
      if acc.exists(x => normalised(x.text.value) == normalised(t)) then acc
      else acc :+ Lesson(Lesson.Text(t), source, run, round)
    }
    l.modify(_.lessons).setTo(all.takeRight(MaxLessons).toList)

  /** A verification pass, for one reviewer: its checks, then its new findings and its lessons. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
  def applyRecheck(
      l: Ledger,
      rc: Recheck,
      run: ReviewLedger.ApplyRecheckRun,
      round: ReviewLedger.ApplyRecheckRound
  ): Ledger =
    val checked = applyChecks(l, rc.reviewer, rc.checks, run, round)
    learn(absorb(checked, rc.findings, run, round)._1, rc.lessons, LessonSource.Recheck, run, round)

  // ── what the steps read ────────────────────────────────────────────────────────────────────────

  /** The open findings as one pass's merged result — the fixer's input and the pass's verdict: the mechanical ones
    * first, then rules, cold reader, skeptic, each by id. `explainBack`, `emptySlots` and `proposals` are the pass's
    * own.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def merged(
      l: Ledger,
      round: Closure.Round,
      explainBack: Option[ReviewArticle.ColdReaderAnswer.ExplainBack] = None,
      emptySlots: List[ReviewArticle.ColdReaderAnswer.EmptySlots] = Nil,
      proposals: List[AgentProposals.Proposal] = Nil,
      run: Option[Run.Id] = None
  ): Merged =
    val fs = l.open
      .map(_.finding)
      .sortBy(f => (f.reviewer.ordinal, f.id.value.drop(f.reviewer.prefix.value.length).toIntOption.getOrElse(0)))
    // a MAJOR the run's final pass deferred is the next run's: it counts as minor in this verdict
    val deferred = run.toList.flatMap(r => l.open.filter(e => e.major && e.deferredIn(r)).map(_.id))
    val counted = fs.map(f => if deferred.contains(f.id) then f.modify(_.severity).setTo(Severity.Minor) else f)
    Merged(round, Verdict.of(counted), fs, explainBack, emptySlots, proposals, deferred)

  /** The notes the fixer reads under an open finding: a fix claimed before that its reviewer found wanting, a
    * regression, a rejection it was raised again after.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def notes(l: Ledger): Map[Finding.Id, String] =
    l.open
      .flatMap(e =>
        e.notFixed
          .map(n =>
            e.id -> s"not fixed yet${if e.notFixedCount.value > 1 then s" (${e.notFixedCount} times)" else ""} — $n"
          )
      )
      .toMap

  /** One line of a closed finding. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def closedLine(e: Entry): String =
    val at = Option(e.finding.where.value).map(_.trim).filter(_.nonEmpty).fold("")(w => s" (${w.take(60)})")
    val how = (e.status, e.closed) match
      case (Status.Fixed, Some(c))    => s"fixed, verified by ${c.by} in pass ${c.round}"
      case (Status.Rejected, Some(c)) =>
        s"rejected${c.citekey.fold("")(ck => s" — [[$ck]]: \"${c.quote.value.take(120)}\"")}"
      case (Status.WontFix, Some(c))  => s"won't fix — ${c.note.value.take(120)}"
      case (Status.SetAside, Some(c)) => s"set aside as not material (${c.quote}) — ${c.note.value.take(160)}"
      case (s, _)                     => s.wire.value
    s"${e.id} ${e.finding.severity.wire}$at: ${e.finding.problem.value.trim.take(140)} → $how"

  /** How much of the open findings a prompt shows: in full, one line each, or not at all. */
  enum OpenStyle:
    case Full, Lines, Omit

  /** The ledger as a prompt shows it (inside `<review-ledger>`): the open findings (`style`; with `only`, just that
    * reviewer's), the newest `closedMax` closed ones as one line each, and the lessons. Empty when there is nothing to
    * show.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def promptText(l: Ledger, style: OpenStyle, closedMax: ClosedLimit, only: Option[Reviewer] = None): String =
    val mine = (e: Entry) => only.forall(_ == e.reviewer)
    val open = l.open.filter(mine)
    val closed = l.entries.filterNot(_.open).filter(mine).takeRight(math.max(0, closedMax.value))
    val openPart = style match
      case OpenStyle.Omit    => ""
      case _ if open.isEmpty => ""
      case OpenStyle.Lines   =>
        "Open findings:\n" + open
          .map(e => s"- ${e.id} ${e.finding.severity.wire}: ${e.finding.problem.value.trim.take(160)}")
          .mkString("\n")
      case OpenStyle.Full =>
        "Open findings:\n" + findingsText(
          merged(l.modify(_.entries).setTo(open), Closure.Round(0)),
          notes(l).map((id, n) => id.value -> n)
        ) +
          (if open.exists(_.claimed.claimed) then
             "\n(The fixer claims to have fixed: " + open.filter(_.claimed.claimed).map(_.id).mkString(", ") + ".)"
           else "")
    val closedPart =
      if closed.isEmpty then ""
      else "Closed findings (one line each):\n" + closed.map(e => s"- ${closedLine(e)}").mkString("\n")
    val lessonPart = if l.lessons.isEmpty then "" else "Lessons:\n" + l.lessons.map(x => s"- ${x.text}").mkString("\n")
    List(openPart, closedPart, lessonPart).filter(_.nonEmpty).mkString("\n\n")

  // ── the edited passages ────────────────────────────────────────────────────────────────────────

  /** A run of changed paragraphs of the new body (with its margin), under the heading it sits in, and the paragraphs of
    * the old body that are gone.
    */
  final case class Passage(heading: Option[Passage.Heading], text: Passage.Text) derives JsonCodec

  object Passage:
    type Heading = Heading.T
    object Heading extends Newtype[String, Pure]
    type Text = Text.T
    object Text extends Newtype[String, Pure]
  final case class Edited(passages: List[Passage], removed: List[Edited.Removed]) derives JsonCodec:
    def isEmpty: Boolean = passages.isEmpty && removed.isEmpty

    /** The text a finding's quote is looked up in (to scope it). */
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def text: ReviewArticle.Fix.Body = ReviewArticle.Fix.Body(passages.map(_.text.value).mkString("\n\n"))

    /** As a prompt shows it. */
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def render: String =
      val ps = passages.map(p => p.heading.fold("")(h => s"[under: $h]\n") + p.text).mkString("\n\n---\n\n")
      val rs =
        if removed.isEmpty then ""
        else "\n\n---\n\nRemoved:\n" + removed.map(r => s"- ${r.value.take(300)}").mkString("\n")
      ps + rs

  object Edited:

    type Removed = Removed.T
    object Removed extends Newtype[String, Pure]

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def paragraphs(body: ReviewArticle.Fix.Body): Vector[String] =
    body.value.replace("\r\n", "\n").split("\n[ \t]*\n").map(_.trim).filter(_.nonEmpty).toVector

  private val Heading = "^#{1,6}\\s+(.+?)\\s*#*\\s*$".r

  /** The passages a fix changed: a paragraph-level diff of `before` and `after` (longest common subsequence of
    * whitespace-normalised paragraphs), each changed paragraph of `after` widened by `margin` paragraphs on each side,
    * adjacent ones joined; plus the paragraphs of `before` that are gone.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def editedPassages(
      before: ReviewArticle.Fix.Body,
      after: ReviewArticle.Fix.Body,
      margin: PassageMargin = PassageMargin(1)
  ): Edited =
    val a = paragraphs(before)
    val b = paragraphs(after)
    def key(p: String) = p.replaceAll("\\s+", " ")
    val (ka, kb) = (a.map(key), b.map(key))
    // LCS table, suffix form: t(i)(j) = LCS of a.drop(i), b.drop(j)
    val t = Array.ofDim[Int](a.size + 1, b.size + 1)
    for i <- a.indices.reverse; j <- b.indices.reverse do
      t(i)(j) = if ka(i) == kb(j) then t(i + 1)(j + 1) + 1 else math.max(t(i + 1)(j), t(i)(j + 1))
    @annotation.tailrec
    def walk(i: Int, j: Int, keptA: Set[Int], keptB: Set[Int]): (Set[Int], Set[Int]) =
      if i >= a.size || j >= b.size then (keptA, keptB)
      else if ka(i) == kb(j) then walk(i + 1, j + 1, keptA + i, keptB + j)
      else if t(i + 1)(j) >= t(i)(j + 1) then walk(i + 1, j, keptA, keptB)
      else walk(i, j + 1, keptA, keptB)
    val (keptA, keptB) = walk(0, 0, Set.empty, Set.empty)
    val changed = b.indices.filterNot(keptB)
    val m = math.max(0, margin.value)
    val shown = changed.flatMap(i => (i - m) to (i + m)).filter(b.indices.contains).distinct.sorted
    val runs = shown
      .foldLeft(List.empty[List[Int]]) {
        case (cur :: rest, i) if cur.head == i - 1 => (i :: cur) :: rest
        case (acc, i)                              => List(i) :: acc
      }
      .reverse
      .map(_.reverse)
    def headingOf(i: Int): Option[Passage.Heading] =
      (i to 0 by -1).iterator.map(b(_)).collectFirst {
        case p if p.linesIterator.nextOption().exists(Heading.matches) =>
          Passage.Heading(p.linesIterator.next() match { case Heading(h) => h.trim; case h => h })
      }
    Edited(
      runs.map(r => Passage(headingOf(r.head), Passage.Text(r.map(b(_)).mkString("\n\n")))),
      a.indices.filterNot(keptA).map(i => Edited.Removed(a(i))).toList
    )

  // ── convergence (docs/research-review-convergence.md §6.9) ──────────────────────────────────────

  /** THE FINAL PASS VERIFIES, IT DOES NOT DISCOVER. After the run's confirming panel (`after`, absorbed from `before`):
    * every open MAJOR that is NEW in it — an entry `before` did not have, or an open minor it raised to MAJOR — is
    * deferred to the next run (`deferred = run`), unless `regression` says the run itself broke it (a pair it had
    * addressed is open again; a factual error in text it wrote). A fixed finding raised again (reopened), a rejected or
    * set-aside one raised again, and an open MAJOR raised again are not new: they stand. Deferred entries stay open and
    * MAJOR — logged, in the backlog item, and the next run's pass 1 hands them to the fixer — but this run's verdict
    * ([[merged]] with the run) counts them as minor. Returns the ledger and the deferred ids.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def deferFinal(
      before: Ledger,
      after: Ledger,
      run: Run.Id,
      regression: Finding => Boolean
  ): (Ledger, List[Finding.Id]) =
    val fresh = after.open
      .filter(e => e.major && before.entry(e.id).forall(b => b.open && !b.major))
      .filterNot(e => regression(e.finding))
    val ids = fresh.map(_.id)
    (
      after
        .modify(_.entries)
        .setTo(after.entries.map(e => if ids.contains(e.id) then e.modify(_.deferred).setTo(Some(run)) else e)),
      ids
    )

  import Regression.Rationale

  /** One closed finding a revision would reopen, and why. */
  final case class Regression(id: Regression.Id, why: Rationale) derives JsonCodec:
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def line: String = s"$id (closed): $why"

  object Regression:

    /** Why the revision reopens the finding: the guard's account of what it found, not a failure. */
    type Rationale = Rationale.T
    object Rationale extends Newtype[String, Pure]

    export Finding.Id

  /** THE REGRESSION GUARD: what a revision (`before` → `after`) does to the findings the ledger has closed as fixed —
    * checked by code, before any reviewer reads it:
    *   - a finding with a critique pair (a missing critique) whose pair was answered in `before` (both cited in one
    *     paragraph, or a stored reason: `answered`) and is not in `after`;
    *   - a finding whose problem text (its quote, ≥ 12 characters) was gone from `before` and is back in `after`;
    *   - a citation the fix rests on (a `[[citekey]]` of the reviewer's quote of the fixed text) cited in `before` and
    *     nowhere in `after`.
    * `cites(body, ck)`: does the body cite the source `ck` (aliases resolved).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  def guard(
      l: Ledger,
      before: ReviewArticle.Fix.Body,
      after: ReviewArticle.Fix.Body,
      answered: (ReviewArticle.Fix.Body, CritiqueRef) => Boolean,
      cites: (ReviewArticle.Fix.Body, Citekey) => Boolean,
      minRatio: ReviewArticle.CritiqueRule.MinRatio
  ): List[Regression] =
    def in(q: String, body: ReviewArticle.Fix.Body) =
      AgentVerify
        .findQuote(
          AgentProposals.Proposal.Quote(q),
          AgentVerify.SourceBody(body.value),
          minRatio
        )
        .accepted
    l.entries.filter(_.status == Status.Fixed).flatMap { e =>
      val pair = e.finding.pair
        .filter(r => answered(before, r) && !answered(after, r))
        .map(r =>
          Regression(
            e.id,
            Rationale(
              s"[[${r.src}]] and [[${r.dst}]] are no longer cited in one paragraph — the critique it answered is open again"
            )
          )
        )
      val q = Option(e.finding.quote.value).map(_.trim).getOrElse("")
      val back = Option.when(q.length >= 12 && !in(q, before) && in(q, after))(
        Regression(e.id, Rationale(s"the text it fixed is back: \"${q.take(100)}\""))
      )
      val lost = e.closed.toList
        .flatMap(c => AgentVerify.wikilinks(AgentVerify.Text(c.quote.value)))
        .distinct
        .flatMap(ck => Citekey.parse(ck.value).toOption)
        .filter(ck => cites(before, ck) && !cites(after, ck))
        .map(ck =>
          Regression(
            e.id,
            Rationale(s"[[$ck]], which its fix cites, is cited nowhere any more")
          )
        )
      (pair.toList ++ back.toList ++ lost).take(1)
    }

  /** Open missing-critique findings found not fixed at least `times` times whose pair is still open: the ones that get
    * code's pairing repair, else a precise instruction (§6.9).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def stubborn(l: Ledger, times: StubbornLimit = StubbornLimit(2)): List[Entry] =
    l.open.filter(e => e.finding.pair.isDefined && e.notFixedCount.value >= times.value)

  // ── the backlog item (#934) ────────────────────────────────────────────────────────────────────

  /** The article's one backlog item while a MAJOR is open: how many, and each one's line. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def taskText(
      l: Ledger,
      runId: Run.Id,
      maxChars: TaskTextLimit = TaskTextLimit(1500)
  ): Option[TaskText] =
    val majors = l.openMajors
    Option.when(majors.nonEmpty) {
      val lines = majors.map { e =>
        val esc = (if e.finding.escalated.escalated then " [raised again after a rejection]" else "") +
          (if e.deferredIn(runId) then " [new in the final pass: deferred to the next run]" else "")
        val at = Option(e.finding.where.value).map(_.trim).filter(_.nonEmpty).fold("")(w => s" (${w.take(60)})")
        s"${e.id}$at: ${e.finding.problem.value.trim.take(160)}$esc"
      }
      val head =
        s"review-article [[${l.slug}]]: ${majors.size} open ${Plural.noun(Plural.Count(majors.size.toLong), Plural.Noun.Singular("MAJOR"))} — "
      val tail = s" (agent run ${runId.value.take(8)})"
      val body = lines.mkString("; ")
      val room = math.max(40, maxChars.value - head.length - tail.length)
      TaskText(head + (if body.length <= room then body else body.take(room - 1) + "…") + tail)
    }

  /** The text of the article's one collapsed item — as [[taskText]] writes it, or as the interactive skill does
    * (`review-article <slug>: 2 open MAJORs — …`) — so a run whose ledger lost the item's id rewrites the open one
    * instead of adding a second. Not a [[legacyTask]] (those are closed).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def articleTask(slug: ArticleTaskSlug, text: TaskText): Boolean =
    val t = text.value.trim
    !legacyTask(slug, TaskText(t)) &&
    (t.startsWith(s"review-article [[$slug]]: ") || t.startsWith(s"review-article $slug: ")) &&
    "^review-article \\S+: \\d+ open MAJORs?\\b".r.findPrefixOf(t).isDefined

  /** The text of the per-finding items earlier runs left for the article (before #934). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def legacyTask(slug: research.Slug, text: TaskText): Boolean =
    text.value.startsWith(s"review-article [[$slug]]: MAJOR ")
