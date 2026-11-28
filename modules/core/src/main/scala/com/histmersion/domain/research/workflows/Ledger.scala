package com.histmersion
package domain.research.workflows

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import hearth.kindlings.optics.syntax.*
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.research.workflows.Finding
import com.histmersion.domain.research.workflows.Entry
import com.histmersion.domain.research.workflows.Verdict
import com.histmersion.domain.research.workflows.Lesson
import com.histmersion.domain.research.workflows.Reviewer
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.research
import com.histmersion.Newtype
import scala.annotation.tailrec
import com.histmersion.domain.automation.Run
import com.histmersion.domain.ReviewLedger.same
import com.histmersion.domain.research.workflows.Closure
import com.histmersion.domain.research.workflows.Status
import com.histmersion.domain.ReviewLedger
import com.histmersion.domain.research.workflows.Check
import com.histmersion.domain.ReviewArticle.*
import com.histmersion.domain.research.workflows.LessonSource
import com.histmersion.domain.research.workflows.Fix
import com.histmersion.domain.ReviewLedger.normalised
import com.histmersion.domain.ReviewLedger.MaxLessons
import com.histmersion.domain.research.workflows.Recheck
import com.histmersion.domain.research.workflows.Merged
import com.histmersion.domain.ReviewArticle
import com.histmersion.domain.research.workflows.Severity
import com.histmersion.domain.research.workflows.ColdReaderAnswer
import com.histmersion.domain.Proposal
import com.histmersion.domain.research.workflows.OpenStyle
import com.histmersion.domain.ReviewLedger.ClosedLimit
import com.histmersion.api.research.CritiqueRef
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.research.workflows.Regression.Rationale
import com.histmersion.domain.AgentVerify
import com.histmersion.domain.research.workflows.Regression
import com.histmersion.domain.research.workflows.CritiqueRule
import com.histmersion.domain.ReviewLedger.StubbornLimit
import com.histmersion.api.Plural
import com.histmersion.domain.ReviewLedger.TaskTextLimit
import com.histmersion.domain.ReviewLedger.TaskText

final case class Ledger(
    slug: Ledger.Slug,
    entries: List[Entry] = Nil,
    lessons: List[Lesson] = Nil,
    task: Option[Ledger.Task] = None
) derives JsonCodec:
  def open: List[Entry] = entries.filter(_.open)
  def openMajors: List[Entry] = open.filter(_.major)
  def entry(id: Finding.Id): Option[Entry] = entries.find(_.id == id)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def claimed(r: Reviewer): List[Entry] = open.filter(e => e.claimed.value && e.reviewer == r)
  def verdict: Verdict = Verdict.of(open.map(_.finding))

object Ledger:

  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type Slug = research.Slug
  val Slug: research.Slug.type = research.Slug
  type Task = Task.T
  object Task extends Newtype[Int, Pure]

  extension (l: Ledger)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def nextId(r: Reviewer): Finding.Id =
      val used =
        l.entries
          .map(_.id.value)
          .filter(_.startsWith(r.prefix.value))
          .flatMap(_.drop(r.prefix.value.length).toIntOption)
      Finding.Id(s"${r.prefix}${used.maxOption.getOrElse(0) + 1}")

  private def update(l: Ledger, id: Finding.Id)(f: Entry => Entry): Ledger =
    l.modify(_.entries).setTo(l.entries.map(e => if e.id == id then f(e) else e))

  extension (l: Ledger)
    @tolerated(LintRule.Copy, "pending G19 (lenses instead of .copy)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def absorb(fs: List[Finding], run: Run.Id, round: Closure.Round): (Ledger, List[Finding]) =
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
                      finding =
                        f.copy(id = id, escalated = Finding.Escalated(f.escalated.value || e.finding.escalated.value)),
                      claimed = Entry.Claimed(false)
                    )
                  case Status.Fixed =>
                    e.copy(
                      finding = f.copy(id = id),
                      status = Status.Open,
                      claimed = Entry.Claimed(false),
                      notFixed = Some(
                        Entry.NotFixed(s"fixed in pass ${e.closed.fold(0)(_.round.value)}, raised again in pass $round")
                      ),
                      reopened = Entry.Reopened(e.reopened.value + 1),
                      closed = None
                    )
                  case Status.Rejected =>
                    e.copy(
                      finding = f.copy(id = id, escalated = Finding.Escalated(true)),
                      status = Status.Open,
                      claimed = Entry.Claimed(false),
                      notFixed = e.closed.map(c =>
                        Entry.NotFixed(
                          s"rejected before${c.citekey.fold("")(ck => s" ([[$ck]])")}: ${c.note}".take(300)
                        )
                      ),
                      closed = None
                    )
                  case Status.WontFix | Status.SetAside if f.major =>
                    e.copy(
                      finding = f.copy(id = id),
                      status = Status.Open,
                      claimed = Entry.Claimed(false),
                      notFixed = e.closed
                        .filter(_ => e.status == Status.SetAside)
                        .map(c => Entry.NotFixed(s"set aside before: ${c.note}".take(300))),
                      closed = None
                    )
                  case Status.WontFix | Status.SetAside => e
                absorbAll(tail, update(led, id)(_ => next), out :+ next.finding, seen + id)
              case None =>
                val id = led.nextId(f.reviewer)
                val g = f.copy(id = id)
                absorbAll(tail, led.modify(_.entries).using(_ :+ Entry(g, run, round)), out :+ g, seen + id)
      absorbAll(fs, l, Vector.empty, Set.empty)

  extension (l: Ledger)
    @tolerated(LintRule.Copy, "pending G19 (lenses instead of .copy)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def applyChecks(
        r: Reviewer,
        checks: List[Check],
        run: ReviewLedger.ApplyChecksRun,
        round: ReviewLedger.ApplyChecksRound
    ): Ledger =
      checks.foldLeft(l) { (led, c) =>
        led.entry(c.id).filter(e => e.open && e.reviewer == r).fold(led) { _ =>
          update(led, c.id) { e =>
            if c.fixed.value then
              e.copy(
                status = Status.Fixed,
                claimed = Entry.Claimed(false),
                notFixed = None,
                closed = Some(Closure(r.wire, round, run, Closure.Note(c.note.value), c.quote))
              )
            else
              e.copy(
                claimed = Entry.Claimed(false),
                notFixed = Some(Entry.NotFixed(Option(c.note.value).filter(_.nonEmpty).getOrElse("not fixed"))),
                notFixedCount = Entry.NotFixedCount(e.notFixedCount.value + 1)
              )
          }
        }
      }

  extension (l: Ledger)
    @tolerated(LintRule.Copy, "pending G19 (lenses instead of .copy)")
    def recheckMechanical(
        now: List[Finding],
        run: ReviewLedger.RecheckMechanicalRun,
        round: ReviewLedger.RecheckMechanicalRound
    ): (Ledger, List[Finding]) =
      val gone = l.open.filter(_.reviewer == Reviewer.Mechanical).filterNot(e => now.exists(same(e.finding, _)))
      val closed = gone.foldLeft(l)((led, e) =>
        update(led, e.id)(
          _.copy(
            status = Status.Fixed,
            claimed = Entry.Claimed(false),
            notFixed = None,
            closed = Some(Closure(Reviewer.Mechanical.wire, round, run, Closure.Note("no longer found by the checks")))
          )
        )
      )
      closed.absorb(now, run, round)

  extension (l: Ledger)
    @tolerated(LintRule.Copy, "pending G19 (lenses instead of .copy)")
    def recheckTermCount(
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
                claimed = Entry.Claimed(false),
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

  extension (l: Ledger)
    @tolerated(LintRule.Copy, "pending G19 (lenses instead of .copy)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def applyFix(f: Fix, run: ReviewLedger.ApplyFixRun, round: ReviewLedger.ApplyFixRound): Ledger =
      val claimed = (f.applied ++ f.repaired).distinct.foldLeft(l)((led, id) =>
        led.entry(id).filter(_.open).fold(led)(_ => update(led, id)(_.copy(claimed = Entry.Claimed(true))))
      )
      val unanswered = f.notFixed.foldLeft(claimed)((led, w) =>
        led
          .entry(w.id)
          .filter(e => e.open && !e.claimed.value)
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
                claimed = Entry.Claimed(false),
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
                claimed = Entry.Claimed(false),
                closed = Some(Closure(Reviewer.Wire("fixer"), round, run, Closure.Note(s"${w.reason}")))
              )
            )
          )
      )
      val setAside = f.setAside.foldLeft(declined)((led, a) =>
        led
          .entry(a.id)
          .filter(_.open)
          .fold(led)(_ =>
            update(led, a.id)(e =>
              e.copy(
                finding = e.finding.copy(pair = Some(a.pair)),
                status = Status.SetAside,
                claimed = Entry.Claimed(false),
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
      setAside.learn(
        f.rejected.map(r => Lesson.Text(s"[[${r.citekey}]]: ${r.reason}")),
        LessonSource.Rejection,
        run,
        round
      )

  extension (l: Ledger)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def learn(
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

  extension (l: Ledger)
    def applyRecheck(rc: Recheck, run: ReviewLedger.ApplyRecheckRun, round: ReviewLedger.ApplyRecheckRound): Ledger =
      val checked = l.applyChecks(rc.reviewer, rc.checks, run, round)
      checked.absorb(rc.findings, run, round)._1.learn(rc.lessons, LessonSource.Recheck, run, round)

  extension (l: Ledger)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def merged(
        round: Closure.Round,
        explainBack: Option[ColdReaderAnswer.ExplainBack] = None,
        emptySlots: List[ColdReaderAnswer.EmptySlots] = Nil,
        proposals: List[Proposal] = Nil,
        run: Option[Run.Id] = None
    ): Merged =
      val fs = l.open
        .map(_.finding)
        .sortBy(f => (f.reviewer.ordinal, f.id.value.drop(f.reviewer.prefix.value.length).toIntOption.getOrElse(0)))
      val deferred = run.toList.flatMap(r => l.open.filter(e => e.major && e.deferredIn(r)).map(_.id))
      val counted = fs.map(f => if deferred.contains(f.id) then f.modify(_.severity).setTo(Severity.Minor) else f)
      Merged(round, Verdict.of(counted), fs, explainBack, emptySlots, proposals, deferred)

  extension (l: Ledger)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def notes: Map[Finding.Id, String] =
      l.open
        .flatMap(e =>
          e.notFixed
            .map(n =>
              e.id -> s"not fixed yet${if e.notFixedCount.value > 1 then s" (${e.notFixedCount} times)" else ""} — $n"
            )
        )
        .toMap

  extension (l: Ledger)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def promptText(style: OpenStyle, closedMax: ClosedLimit, only: Option[Reviewer] = None): String =
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
          "Open findings:\n" + l
            .modify(_.entries)
            .setTo(open)
            .merged(Closure.Round(0))
            .findingsText(l.notes.map((id, n) => id.value -> n)) +
            (if open.exists(_.claimed.value) then
               "\n(The fixer claims to have fixed: " + open.filter(_.claimed.value).map(_.id).mkString(", ") + ".)"
             else "")
      val closedPart =
        if closed.isEmpty then ""
        else "Closed findings (one line each):\n" + closed.map(e => s"- ${e.closedLine}").mkString("\n")
      val lessonPart =
        if l.lessons.isEmpty then "" else "Lessons:\n" + l.lessons.map(x => s"- ${x.text}").mkString("\n")
      List(openPart, closedPart, lessonPart).filter(_.nonEmpty).mkString("\n\n")

  extension (before: Ledger)
    def deferFinal(after: Ledger, run: Run.Id, regression: Finding => Boolean): (Ledger, List[Finding.Id]) =
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

  extension (l: Ledger)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
    def guard(
        before: Fix.Body,
        after: Fix.Body,
        answered: (Fix.Body, CritiqueRef) => Boolean,
        cites: (Fix.Body, Citekey) => Boolean,
        minRatio: CritiqueRule.MinRatio
    ): List[Regression] =
      def in(q: String, body: Fix.Body) =
        AgentVerify
          .findQuote(
            Proposal.Quote(q),
            AgentVerify.SourceBody(body.value),
            AgentVerify.MinRatio(minRatio.value)
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

  extension (l: Ledger)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def stubborn(times: StubbornLimit = StubbornLimit(2)): List[Entry] =
      l.open.filter(e => e.finding.pair.isDefined && e.notFixedCount.value >= times.value)

  extension (l: Ledger)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def taskText(runId: Run.Id, maxChars: TaskTextLimit = TaskTextLimit(1500)): Option[TaskText] =
      val majors = l.openMajors
      Option.when(majors.nonEmpty) {
        val lines = majors.map { e =>
          val esc = (if e.finding.escalated.value then " [raised again after a rejection]" else "") +
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
