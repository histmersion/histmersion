package com.histmersion.domain

import scala.annotation.tailrec
import hearth.kindlings.optics.syntax.*
import com.histmersion.api.{Plural, WireEnum}
import com.histmersion.api.research.CritiqueRef
import com.histmersion.api.given

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import io.circe.{Decoder, Encoder}

import com.histmersion.domain.ReviewArticle.*
import com.histmersion.domain.ReviewArticle.Finding
import com.histmersion.api.WireEnumValues
import com.histmersion.domain.research.workflows.ReviewComparison

/** The review ledger of one article (docs/research-review-convergence.md §6): every finding the `review-article` panel
  * ever raised on it, under an id that never changes, with what became of it — open, fixed (verified by the reviewer
  * that raised it), rejected (with the record's sentence) or won't fix — and the lessons learned from rejections and
  * failed fixes. It persists across runs, so a later run starts from it; the reviewers and the fixer read it in their
  * prompts.
  *
  * Everything here is pure: the pipeline loads the ledger, threads it through its passes as a value (each pass's new
  * ledger is a checkpointed step output) and writes it back through the run's gate.
  */
object ReviewLedger:

  type MechanicalKeyResultPart1 = MechanicalKeyResultPart1.T
  object MechanicalKeyResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MechanicalKeyResultPart2 = MechanicalKeyResultPart2.T
  object MechanicalKeyResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MechanicalKeyResultPart3 = MechanicalKeyResultPart3.T
  object MechanicalKeyResultPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MechanicalKeyResultPart4 = MechanicalKeyResultPart4.T
  object MechanicalKeyResultPart4 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SameResult = SameResult.T
  object SameResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NextIdResult = NextIdResult.T
  object NextIdResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpdateId = UpdateId.T
  object UpdateId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AbsorbRun = AbsorbRun.T
  object AbsorbRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AbsorbRound = AbsorbRound.T
  object AbsorbRound extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyChecksRun = ApplyChecksRun.T
  object ApplyChecksRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyChecksRound = ApplyChecksRound.T
  object ApplyChecksRound extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecheckMechanicalRun = RecheckMechanicalRun.T
  object RecheckMechanicalRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecheckMechanicalRound = RecheckMechanicalRound.T
  object RecheckMechanicalRound extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecheckTermCountRun = RecheckTermCountRun.T
  object RecheckTermCountRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecheckTermCountRound = RecheckTermCountRound.T
  object RecheckTermCountRound extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyFixRun = ApplyFixRun.T
  object ApplyFixRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyFixRound = ApplyFixRound.T
  object ApplyFixRound extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LearnTexts = LearnTexts.T
  object LearnTexts extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LearnRun = LearnRun.T
  object LearnRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LearnRound = LearnRound.T
  object LearnRound extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyRecheckRun = ApplyRecheckRun.T
  object ApplyRecheckRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyRecheckRound = ApplyRecheckRound.T
  object ApplyRecheckRound extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergedRound = MergedRound.T
  object MergedRound extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergedExplainBack = MergedExplainBack.T
  object MergedExplainBack extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergedEmptySlots = MergedEmptySlots.T
  object MergedEmptySlots extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergedRun = MergedRun.T
  object MergedRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NotesResultPart1 = NotesResultPart1.T
  object NotesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NotesResultPart2 = NotesResultPart2.T
  object NotesResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClosedLineResult = ClosedLineResult.T
  object ClosedLineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PromptTextResult = PromptTextResult.T
  object PromptTextResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PromptTextClosedMax = PromptTextClosedMax.T
  object PromptTextClosedMax extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParagraphsResult = ParagraphsResult.T
  object ParagraphsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParagraphsBody = ParagraphsBody.T
  object ParagraphsBody extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EditedPassagesBefore = EditedPassagesBefore.T
  object EditedPassagesBefore extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EditedPassagesAfter = EditedPassagesAfter.T
  object EditedPassagesAfter extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EditedPassagesMargin = EditedPassagesMargin.T
  object EditedPassagesMargin extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeferFinalResultPart2 = DeferFinalResultPart2.T
  object DeferFinalResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeferFinalRun = DeferFinalRun.T
  object DeferFinalRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeferFinalRegressionResult = DeferFinalRegressionResult.T
  object DeferFinalRegressionResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GuardBefore = GuardBefore.T
  object GuardBefore extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GuardAfter = GuardAfter.T
  object GuardAfter extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GuardAnsweredArg1 = GuardAnsweredArg1.T
  object GuardAnsweredArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GuardAnsweredResult = GuardAnsweredResult.T
  object GuardAnsweredResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GuardCitesArg1 = GuardCitesArg1.T
  object GuardCitesArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GuardCitesArg2 = GuardCitesArg2.T
  object GuardCitesArg2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GuardCitesResult = GuardCitesResult.T
  object GuardCitesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GuardMinRatio = GuardMinRatio.T
  object GuardMinRatio extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StubbornTimes = StubbornTimes.T
  object StubbornTimes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TaskTextResult = TaskTextResult.T
  object TaskTextResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TaskTextMaxChars = TaskTextMaxChars.T
  object TaskTextMaxChars extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleTaskResult = ArticleTaskResult.T
  object ArticleTaskResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleTaskSlug = ArticleTaskSlug.T
  object ArticleTaskSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleTaskText = ArticleTaskText.T
  object ArticleTaskText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LegacyTaskResult = LegacyTaskResult.T
  object LegacyTaskResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LegacyTaskSlug = LegacyTaskSlug.T
  object LegacyTaskSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LegacyTaskText = LegacyTaskText.T
  object LegacyTaskText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  enum Status(val wire: Status.Wire):
    case Open extends Status("open")
    case Fixed extends Status("fixed")
    case Rejected extends Status("rejected")
    case WontFix extends Status("wont-fix")

    /** A missing-critique finding the fixer answered with a stored critique reason (§6.8): the critique exists, and
      * does not bear on what the article cites the source for.
      */
    case SetAside extends Status("set-aside")

  object Status:
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type ParseS = ParseS.T
    object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type WireResult = WireResult.T
    object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FromWireS = FromWireS.T
    object FromWireS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def parse(s: com.histmersion.domain.ReviewLedger.Status.ParseS): Option[Status] = values.find(_.wire == s.trim.toLowerCase)
    given WireEnumValues[Status]:
      def all: Array[Status] = Status.values
      def wire(e: Status): com.histmersion.domain.ReviewLedger.Status.WireResult = e.wire
      def fromWire(s: com.histmersion.domain.ReviewLedger.Status.FromWireS): Option[Status] = parse(s)
    given Encoder[Status] = WireEnum.encoder
    given Decoder[Status] = WireEnum.decoder

  /** Where a lesson came from: a rejection the fixer proved, or a reviewer's re-check. */
  enum LessonSource(val wire: LessonSource.Wire):
    case Rejection extends LessonSource("rejection")
    case Recheck extends LessonSource("recheck")

  object LessonSource:
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type ParseS = ParseS.T
    object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type WireResult = WireResult.T
    object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FromWireS = FromWireS.T
    object FromWireS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def parse(s: com.histmersion.domain.ReviewLedger.LessonSource.ParseS): Option[LessonSource] = values.find(_.wire == s.trim.toLowerCase)
    given WireEnumValues[LessonSource]:
      def all: Array[LessonSource] = LessonSource.values
      def wire(e: LessonSource): com.histmersion.domain.ReviewLedger.LessonSource.WireResult = e.wire
      def fromWire(s: com.histmersion.domain.ReviewLedger.LessonSource.FromWireS): Option[LessonSource] = parse(s)
    given Encoder[LessonSource] = WireEnum.encoder
    given Decoder[LessonSource] = WireEnum.decoder

  /** How a finding was closed: by whom (a reviewer's wire name, `fixer`, or `mechanical` for code), in which pass of
    * which run, and the evidence — the quote of the fixed text, or the record's sentence of a rejection (with its
    * citekey), or the reason a minor one is declined.
    */
  final case class Closure(
      by: Closure.By,
      round: Closure.Round,
      run: Closure.Run,
      note: Closure.Note = "",
      quote: Closure.Quote = "",
      citekey: Option[com.histmersion.domain.library.Citekey] = None
  ) derives KindlingsCodecAsObject

  object Closure:
    type By = By.T
    object By extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Round = Round.T
    object Round extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Run = Run.T
    object Run extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Note = Note.T
    object Note extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Quote = Quote.T
    object Quote extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

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
      claimed: Entry.Claimed = false,
      notFixed: Option[Entry.NotFixed] = None,
      reopened: Entry.Reopened = 0,
      closed: Option[Closure] = None,
      notFixedCount: Entry.NotFixedCount = 0,
      deferred: Option[Entry.Deferred] = None
  ) derives KindlingsCodecAsObject:
    def id: com.histmersion.domain.ReviewLedger.Entry.IdResult = finding.id
    def open: com.histmersion.domain.ReviewLedger.Entry.OpenResult = status == Status.Open
    def major: com.histmersion.domain.ReviewLedger.Entry.MajorResult = finding.major
    def reviewer: Reviewer = finding.reviewer

    /** Deferred by `run`'s final pass: not part of that run's verdict. */
    def deferredIn(run: com.histmersion.domain.ReviewLedger.Entry.DeferredInRun): com.histmersion.domain.ReviewLedger.Entry.DeferredInResult = open && deferred.contains(run)

  object Entry:
    type IdResult = IdResult.T
    object IdResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OpenResult = OpenResult.T
    object OpenResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MajorResult = MajorResult.T
    object MajorResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DeferredInResult = DeferredInResult.T
    object DeferredInResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DeferredInRun = DeferredInRun.T
    object DeferredInRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Run = Run.T
    object Run extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Round = Round.T
    object Round extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Claimed = Claimed.T
    object Claimed extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NotFixed = NotFixed.T
    object NotFixed extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Reopened = Reopened.T
    object Reopened extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NotFixedCount = NotFixedCount.T
    object NotFixedCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Deferred = Deferred.T
    object Deferred extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Lesson(text: Lesson.Text, source: LessonSource, run: Lesson.Run, round: Lesson.Round) derives KindlingsCodecAsObject

  object Lesson:
    type Text = Text.T
    object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Run = Run.T
    object Run extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Round = Round.T
    object Round extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** `task`: the article's one backlog item (#934), while it has one. */
  final case class Ledger(
      slug: Ledger.Slug,
      entries: List[Entry] = Nil,
      lessons: List[Lesson] = Nil,
      task: Option[Ledger.Task] = None
  ) derives KindlingsCodecAsObject:
    def open: List[Entry] = entries.filter(_.open)
    def openMajors: List[Entry] = open.filter(_.major)
    def entry(id: com.histmersion.domain.ReviewLedger.Ledger.EntryId): Option[Entry] = entries.find(_.id == id)

    /** The open findings the fixer claims, of one reviewer: what its re-check verifies. */
    def claimed(r: Reviewer): List[Entry] = open.filter(e => e.claimed && e.reviewer == r)
    def verdict: Verdict = Verdict.of(open.map(_.finding))

  object Ledger:
    type EntryId = EntryId.T
    object EntryId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Slug = Slug.T
    object Slug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Task = Task.T
    object Task extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** At most this many lessons are kept (the newest). */
  val MaxLessons = 15

  // ── matching ───────────────────────────────────────────────────────────────────────────────────

  /** A mechanical finding is the same one when code says the same thing about the same passage: its kind, its place,
    * its problem up to the first colon (a coverage count changes as reasons are given; the passage does not) and its
    * quote.
    */
  private def mechanicalKey(f: Finding): (com.histmersion.domain.ReviewLedger.MechanicalKeyResultPart1, com.histmersion.domain.ReviewLedger.MechanicalKeyResultPart2, com.histmersion.domain.ReviewLedger.MechanicalKeyResultPart3, com.histmersion.domain.ReviewLedger.MechanicalKeyResultPart4) =
    (
      f.kind,
      AgentVerify.normalise(f.where),
      AgentVerify.normalise(f.problem.takeWhile(_ != ':')),
      AgentVerify.normalise(f.quote)
    )

  /** Is `b` the finding `a` again? The same reviewer, and the same passage (quotes equal, or one inside the other when
    * both are at least 20 characters), or the same `where` and a problem that shares at least half its words
    * ([[CompareRuns.similarity]]). Mechanical findings compare by [[mechanicalKey]]; the cold reader's term-count
    * findings ([[TermCount]]) are one finding whatever the count or wording.
    */
  def same(a: Finding, b: Finding): com.histmersion.domain.ReviewLedger.SameResult =
    a.reviewer == b.reviewer && {
      if a.reviewer == Reviewer.Mechanical then mechanicalKey(a) == mechanicalKey(b)
      else if TermCount.is(a) || TermCount.is(b) then TermCount.is(a) && TermCount.is(b)
      else
        val (qa, qb) = (AgentVerify.normalise(a.quote), AgentVerify.normalise(b.quote))
        val quoted = qa.nonEmpty && qb.nonEmpty &&
          (qa == qb || (math.min(qa.length, qb.length) >= 20 && (qa.contains(qb) || qb.contains(qa))))
        quoted || (AgentVerify.normalise(a.where) == AgentVerify.normalise(b.where) &&
          CompareRuns.similarity(a.problem, b.problem) >= ReviewComparison.ProblemSimilarity)
    }

  /** The next ledger id of a reviewer: its letter and one more than the highest number it used. */
  def nextId(l: Ledger, r: Reviewer): com.histmersion.domain.ReviewLedger.NextIdResult =
    val used = l.entries.map(_.id).filter(_.startsWith(r.prefix)).flatMap(_.drop(r.prefix.length).toIntOption)
    s"${r.prefix}${used.maxOption.getOrElse(0) + 1}"

  // ── transitions ────────────────────────────────────────────────────────────────────────────────

  private def update(l: Ledger, id: com.histmersion.domain.ReviewLedger.UpdateId)(f: Entry => Entry): Ledger =
    l.modify(_.entries).setTo(l.entries.map(e => if e.id == id then f(e) else e))

  /** Record a pass's findings (ids per pass, as the reviewers' answers were numbered). A finding the ledger has
    * already: an open one keeps its id (and is no longer "claimed": it was raised again); a fixed one is RE-OPENED (a
    * regression); a rejected one is re-opened and flagged escalated; a declined minor stays declined unless it comes
    * back MAJOR. Anything else is a new entry with the next id. Returns the ledger and the findings under their ledger
    * ids, in the order given.
    */
  def absorb(l: Ledger, fs: List[Finding], run: com.histmersion.domain.ReviewLedger.AbsorbRun, round: com.histmersion.domain.ReviewLedger.AbsorbRound): (Ledger, List[Finding]) =
    @tailrec def absorbAll(
        rest: List[Finding],
        led: Ledger,
        out: Vector[Finding],
        seen: Set[String]
    ): (Ledger, List[Finding]) =
      rest match
        case Nil       => (led, out.toList)
        case f :: tail =>
          led.entries.find(e => !seen(e.id) && same(e.finding, f)) match
            case Some(e) =>
              val id = e.id
              val next: Entry = e.status match
                case Status.Open =>
                  e.copy(finding = f.copy(id = id, escalated = f.escalated || e.finding.escalated), claimed = false)
                case Status.Fixed =>
                  e.copy(
                    finding = f.copy(id = id),
                    status = Status.Open,
                    claimed = false,
                    notFixed = Some(s"fixed in pass ${e.closed.fold(0)(_.round)}, raised again in pass $round"),
                    reopened = e.reopened + 1,
                    closed = None
                  )
                case Status.Rejected =>
                  e.copy(
                    finding = f.copy(id = id, escalated = true),
                    status = Status.Open,
                    claimed = false,
                    notFixed = e.closed.map(c =>
                      s"rejected before${c.citekey.fold("")(ck => s" ([[$ck]])")}: ${c.note}".take(300)
                    ),
                    closed = None
                  )
                case Status.WontFix | Status.SetAside if f.major =>
                  e.copy(
                    finding = f.copy(id = id),
                    status = Status.Open,
                    claimed = false,
                    notFixed = e.closed
                      .filter(_ => e.status == Status.SetAside)
                      .map(c => s"set aside before: ${c.note}".take(300)),
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
  def applyChecks(l: Ledger, r: Reviewer, checks: List[Check], run: com.histmersion.domain.ReviewLedger.ApplyChecksRun, round: com.histmersion.domain.ReviewLedger.ApplyChecksRound): Ledger =
    checks.foldLeft(l) { (led, c) =>
      led.entry(c.id).filter(e => e.open && e.reviewer == r).fold(led) { _ =>
        update(led, c.id) { e =>
          if c.fixed then
            e.copy(
              status = Status.Fixed,
              claimed = false,
              notFixed = None,
              closed = Some(Closure(r.wire, round, run, c.note, c.quote))
            )
          else
            e.copy(
              claimed = false,
              notFixed = Some(Option(c.note).filter(_.nonEmpty).getOrElse("not fixed")),
              notFixedCount = e.notFixedCount + 1
            )
        }
      }
    }

  /** The mechanical re-check, by code: an open mechanical finding that no longer recurs is fixed; one that recurs stays
    * open (unclaimed); a new one is opened.
    */
  def recheckMechanical(l: Ledger, now: List[Finding], run: com.histmersion.domain.ReviewLedger.RecheckMechanicalRun, round: com.histmersion.domain.ReviewLedger.RecheckMechanicalRound): (Ledger, List[Finding]) =
    val gone = l.open.filter(_.reviewer == Reviewer.Mechanical).filterNot(e => now.exists(same(e.finding, _)))
    val closed = gone.foldLeft(l)((led, e) =>
      update(led, e.id)(
        _.copy(
          status = Status.Fixed,
          claimed = false,
          notFixed = None,
          closed = Some(Closure(Reviewer.Mechanical.wire, round, run, "no longer found by the checks"))
        )
      )
    )
    absorb(closed, now, run, round)

  /** The term count after a full panel, by code: the cold reader's count is the whole of that finding, so an open count
    * entry the pass did not raise again (8 terms or fewer now) is fixed. One it did raise again is matched by
    * [[absorb]], which replaces it — a MAJOR stored by an earlier run becomes the pass's minor one under the same id.
    */
  def recheckTermCount(l: Ledger, cold: List[Finding], run: com.histmersion.domain.ReviewLedger.RecheckTermCountRun, round: com.histmersion.domain.ReviewLedger.RecheckTermCountRound): Ledger =
    if cold.exists(TermCount.is) then l
    else
      l.open
        .filter(e => TermCount.is(e.finding))
        .foldLeft(l)((led, e) =>
          update(led, e.id)(
            _.copy(
              status = Status.Fixed,
              claimed = false,
              notFixed = None,
              closed = Some(
                Closure(
                  Reviewer.Mechanical.wire,
                  round,
                  run,
                  "the cold reader's count is no longer above the threshold"
                )
              )
            )
          )
        )

  /** The fixer's verified revision: what it applied is claimed (its reviewer re-checks it), a rejection code verified
    * closes the finding with the record's sentence (and teaches a lesson), a declined minor is closed as won't fix.
    */
  def applyFix(l: Ledger, f: Fix, run: com.histmersion.domain.ReviewLedger.ApplyFixRun, round: com.histmersion.domain.ReviewLedger.ApplyFixRound): Ledger =
    // what the fixer applied, and what code's pairing repair answered (§6.9): both re-checked by the reviewer
    val claimed = (f.applied ++ f.repaired).distinct.foldLeft(l)((led, id) =>
      led.entry(id).filter(_.open).fold(led)(_ => update(led, id)(_.copy(claimed = true)))
    )
    // what the fixer neither applied nor rejected, or claimed without the change: not fixed, with why
    val unanswered = f.notFixed.foldLeft(claimed)((led, w) =>
      led
        .entry(w.id)
        .filter(e => e.open && !e.claimed)
        .fold(led)(_ =>
          update(led, w.id)(e =>
            e.copy(notFixed = Some(s"pass $round: ${w.reason}".take(300)), notFixedCount = e.notFixedCount + 1)
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
              claimed = false,
              closed = Some(Closure("fixer", round, run, r.reason, r.quote, Some(r.citekey)))
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
            _.copy(status = Status.WontFix, claimed = false, closed = Some(Closure("fixer", round, run, w.reason)))
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
              claimed = false,
              closed = Some(Closure("fixer", round, run, a.reason, a.pair.wire))
            )
          )
        )
    )
    learn(setAside, f.rejected.map(r => s"[[${r.citekey}]]: ${r.reason}"), LessonSource.Rejection, run, round)

  /** Add lessons: trimmed, at most [[LessonChars]] each, a lesson already held (normalised) not twice, the newest
    * [[MaxLessons]] kept.
    */
  def learn(l: Ledger, texts: List[com.histmersion.domain.ReviewLedger.LearnTexts], source: LessonSource, run: com.histmersion.domain.ReviewLedger.LearnRun, round: com.histmersion.domain.ReviewLedger.LearnRound): Ledger =
    val fresh = texts.map(_.trim.replaceAll("\\s+", " ").take(LessonChars)).filter(_.nonEmpty)
    val all = fresh.foldLeft(l.lessons.toVector) { (acc, t) =>
      if acc.exists(x => AgentVerify.normalise(x.text) == AgentVerify.normalise(t)) then acc
      else acc :+ Lesson(t, source, run, round)
    }
    l.modify(_.lessons).setTo(all.takeRight(MaxLessons).toList)

  /** A verification pass, for one reviewer: its checks, then its new findings and its lessons. */
  def applyRecheck(l: Ledger, rc: Recheck, run: com.histmersion.domain.ReviewLedger.ApplyRecheckRun, round: com.histmersion.domain.ReviewLedger.ApplyRecheckRound): Ledger =
    val checked = applyChecks(l, rc.reviewer, rc.checks, run, round)
    learn(absorb(checked, rc.findings, run, round)._1, rc.lessons, LessonSource.Recheck, run, round)

  // ── what the steps read ────────────────────────────────────────────────────────────────────────

  /** The open findings as one pass's merged result — the fixer's input and the pass's verdict: the mechanical ones
    * first, then rules, cold reader, skeptic, each by id. `explainBack`, `emptySlots` and `proposals` are the pass's
    * own.
    */
  def merged(
      l: Ledger,
      round: com.histmersion.domain.ReviewLedger.MergedRound,
      explainBack: Option[com.histmersion.domain.ReviewLedger.MergedExplainBack] = None,
      emptySlots: List[com.histmersion.domain.ReviewLedger.MergedEmptySlots] = Nil,
      proposals: List[AgentProposals.Proposal] = Nil,
      run: Option[com.histmersion.domain.ReviewLedger.MergedRun] = None
  ): Merged =
    val fs = l.open
      .map(_.finding)
      .sortBy(f => (f.reviewer.ordinal, f.id.drop(f.reviewer.prefix.length).toIntOption.getOrElse(0)))
    // a MAJOR the run's final pass deferred is the next run's: it counts as minor in this verdict
    val deferred = run.toList.flatMap(r => l.open.filter(e => e.major && e.deferredIn(r)).map(_.id))
    val counted = fs.map(f => if deferred.contains(f.id) then f.modify(_.severity).setTo(Severity.Minor) else f)
    Merged(round, Verdict.of(counted), fs, explainBack, emptySlots, proposals, deferred)

  /** The notes the fixer reads under an open finding: a fix claimed before that its reviewer found wanting, a
    * regression, a rejection it was raised again after.
    */
  def notes(l: Ledger): Map[com.histmersion.domain.ReviewLedger.NotesResultPart1, com.histmersion.domain.ReviewLedger.NotesResultPart2] =
    l.open
      .flatMap(e =>
        e.notFixed
          .map(n => e.id -> s"not fixed yet${if e.notFixedCount > 1 then s" (${e.notFixedCount} times)" else ""} — $n")
      )
      .toMap

  /** One line of a closed finding. */
  def closedLine(e: Entry): com.histmersion.domain.ReviewLedger.ClosedLineResult =
    val at = Option(e.finding.where).map(_.trim).filter(_.nonEmpty).fold("")(w => s" (${w.take(60)})")
    val how = (e.status, e.closed) match
      case (Status.Fixed, Some(c))    => s"fixed, verified by ${c.by} in pass ${c.round}"
      case (Status.Rejected, Some(c)) => s"rejected${c.citekey.fold("")(ck => s" — [[$ck]]: \"${c.quote.take(120)}\"")}"
      case (Status.WontFix, Some(c))  => s"won't fix — ${c.note.take(120)}"
      case (Status.SetAside, Some(c)) => s"set aside as not material (${c.quote}) — ${c.note.take(160)}"
      case (s, _)                     => s.wire
    s"${e.id} ${e.finding.severity.wire}$at: ${e.finding.problem.trim.take(140)} → $how"

  /** How much of the open findings a prompt shows: in full, one line each, or not at all. */
  enum OpenStyle:
    case Full, Lines, Omit

  /** The ledger as a prompt shows it (inside `<review-ledger>`): the open findings (`style`; with `only`, just that
    * reviewer's), the newest `closedMax` closed ones as one line each, and the lessons. Empty when there is nothing to
    * show.
    */
  def promptText(l: Ledger, style: OpenStyle, closedMax: com.histmersion.domain.ReviewLedger.PromptTextClosedMax, only: Option[Reviewer] = None): com.histmersion.domain.ReviewLedger.PromptTextResult =
    val mine = (e: Entry) => only.forall(_ == e.reviewer)
    val open = l.open.filter(mine)
    val closed = l.entries.filterNot(_.open).filter(mine).takeRight(math.max(0, closedMax))
    val openPart = style match
      case OpenStyle.Omit    => ""
      case _ if open.isEmpty => ""
      case OpenStyle.Lines   =>
        "Open findings:\n" + open
          .map(e => s"- ${e.id} ${e.finding.severity.wire}: ${e.finding.problem.trim.take(160)}")
          .mkString("\n")
      case OpenStyle.Full =>
        "Open findings:\n" + findingsText(merged(l.modify(_.entries).setTo(open), 0), notes(l)) +
          (if open.exists(_.claimed) then
             "\n(The fixer claims to have fixed: " + open.filter(_.claimed).map(_.id).mkString(", ") + ".)"
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
  final case class Passage(heading: Option[Passage.Heading], text: Passage.Text) derives KindlingsCodecAsObject

  object Passage:
    type Heading = Heading.T
    object Heading extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Text = Text.T
    object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  final case class Edited(passages: List[Passage], removed: List[Edited.Removed]) derives KindlingsCodecAsObject:
    def isEmpty: com.histmersion.domain.ReviewLedger.Edited.IsEmptyResult = passages.isEmpty && removed.isEmpty

    /** The text a finding's quote is looked up in (to scope it). */
    def text: com.histmersion.domain.ReviewLedger.Edited.TextResult = passages.map(_.text).mkString("\n\n")

    /** As a prompt shows it. */
    def render: com.histmersion.domain.ReviewLedger.Edited.RenderResult =
      val ps = passages.map(p => p.heading.fold("")(h => s"[under: $h]\n") + p.text).mkString("\n\n---\n\n")
      val rs =
        if removed.isEmpty then "" else "\n\n---\n\nRemoved:\n" + removed.map(r => s"- ${r.take(300)}").mkString("\n")
      ps + rs

  object Edited:
    type IsEmptyResult = IsEmptyResult.T
    object IsEmptyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TextResult = TextResult.T
    object TextResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RenderResult = RenderResult.T
    object RenderResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Removed = Removed.T
    object Removed extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private def paragraphs(body: com.histmersion.domain.ReviewLedger.ParagraphsBody): Vector[com.histmersion.domain.ReviewLedger.ParagraphsResult] =
    body.replace("\r\n", "\n").split("\n[ \t]*\n").map(_.trim).filter(_.nonEmpty).toVector

  private val Heading = "^#{1,6}\\s+(.+?)\\s*#*\\s*$".r

  /** The passages a fix changed: a paragraph-level diff of `before` and `after` (longest common subsequence of
    * whitespace-normalised paragraphs), each changed paragraph of `after` widened by `margin` paragraphs on each side,
    * adjacent ones joined; plus the paragraphs of `before` that are gone.
    */
  def editedPassages(before: com.histmersion.domain.ReviewLedger.EditedPassagesBefore, after: com.histmersion.domain.ReviewLedger.EditedPassagesAfter, margin: com.histmersion.domain.ReviewLedger.EditedPassagesMargin = 1): Edited =
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
    val m = math.max(0, margin)
    val shown = changed.flatMap(i => (i - m) to (i + m)).filter(b.indices.contains).distinct.sorted
    val runs = shown
      .foldLeft(List.empty[List[Int]]) {
        case (cur :: rest, i) if cur.head == i - 1 => (i :: cur) :: rest
        case (acc, i)                              => List(i) :: acc
      }
      .reverse
      .map(_.reverse)
    def headingOf(i: Int): Option[String] =
      (i to 0 by -1).iterator.map(b(_)).collectFirst {
        case p if p.linesIterator.nextOption().exists(Heading.matches) =>
          p.linesIterator.next() match { case Heading(h) => h.trim; case h => h }
      }
    Edited(
      runs.map(r => Passage(headingOf(r.head), r.map(b(_)).mkString("\n\n"))),
      a.indices.filterNot(keptA).map(a(_)).toList
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
  def deferFinal(before: Ledger, after: Ledger, run: com.histmersion.domain.ReviewLedger.DeferFinalRun, regression: Finding => com.histmersion.domain.ReviewLedger.DeferFinalRegressionResult): (Ledger, List[com.histmersion.domain.ReviewLedger.DeferFinalResultPart2]) =
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

  /** One closed finding a revision would reopen, and why. */
  final case class Regression(id: Regression.Id, why: Regression.Why) derives KindlingsCodecAsObject:
    def line: com.histmersion.domain.ReviewLedger.Regression.LineResult = s"$id (closed): $why"

  object Regression:
    type LineResult = LineResult.T
    object LineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Why = Why.T
    object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** THE REGRESSION GUARD: what a revision (`before` → `after`) does to the findings the ledger has closed as fixed —
    * checked by code, before any reviewer reads it:
    *   - a finding with a critique pair (a missing critique) whose pair was answered in `before` (both cited in one
    *     paragraph, or a stored reason: `answered`) and is not in `after`;
    *   - a finding whose problem text (its quote, ≥ 12 characters) was gone from `before` and is back in `after`;
    *   - a citation the fix rests on (a `[[citekey]]` of the reviewer's quote of the fixed text) cited in `before` and
    *     nowhere in `after`.
    * `cites(body, ck)`: does the body cite the source `ck` (aliases resolved).
    */
  def guard(
      l: Ledger,
      before: com.histmersion.domain.ReviewLedger.GuardBefore,
      after: com.histmersion.domain.ReviewLedger.GuardAfter,
      answered: (com.histmersion.domain.ReviewLedger.GuardAnsweredArg1, CritiqueRef) => com.histmersion.domain.ReviewLedger.GuardAnsweredResult,
      cites: (com.histmersion.domain.ReviewLedger.GuardCitesArg1, com.histmersion.domain.ReviewLedger.GuardCitesArg2) => com.histmersion.domain.ReviewLedger.GuardCitesResult,
      minRatio: com.histmersion.domain.ReviewLedger.GuardMinRatio
  ): List[Regression] =
    def in(q: String, body: String) = AgentVerify.findQuote(q, body, minRatio).accepted
    l.entries.filter(_.status == Status.Fixed).flatMap { e =>
      val pair = e.finding.pair
        .filter(r => answered(before, r) && !answered(after, r))
        .map(r =>
          Regression(
            e.id,
            s"[[${r.src}]] and [[${r.dst}]] are no longer cited in one paragraph — the critique it answered is open again"
          )
        )
      val q = Option(e.finding.quote).map(_.trim).getOrElse("")
      val back = Option.when(q.length >= 12 && !in(q, before) && in(q, after))(
        Regression(e.id, s"the text it fixed is back: \"${q.take(100)}\"")
      )
      val lost = e.closed.toList
        .flatMap(c => AgentVerify.wikilinks(c.quote))
        .distinct
        .filter(ck => cites(before, ck) && !cites(after, ck))
        .map(ck => Regression(e.id, s"[[$ck]], which its fix cites, is cited nowhere any more"))
      (pair.toList ++ back.toList ++ lost).take(1)
    }

  /** Open missing-critique findings found not fixed at least `times` times whose pair is still open: the ones that get
    * code's pairing repair, else a precise instruction (§6.9).
    */
  def stubborn(l: Ledger, times: com.histmersion.domain.ReviewLedger.StubbornTimes = 2): List[Entry] =
    l.open.filter(e => e.finding.pair.isDefined && e.notFixedCount >= times)

  // ── the backlog item (#934) ────────────────────────────────────────────────────────────────────

  /** The article's one backlog item while a MAJOR is open: how many, and each one's line. */
  def taskText(l: Ledger, runId: com.histmersion.domain.automation.Run.Id, maxChars: com.histmersion.domain.ReviewLedger.TaskTextMaxChars = 1500): Option[com.histmersion.domain.ReviewLedger.TaskTextResult] =
    val majors = l.openMajors
    Option.when(majors.nonEmpty) {
      val lines = majors.map { e =>
        val esc = (if e.finding.escalated then " [raised again after a rejection]" else "") +
          (if e.deferredIn(runId) then " [new in the final pass: deferred to the next run]" else "")
        val at = Option(e.finding.where).map(_.trim).filter(_.nonEmpty).fold("")(w => s" (${w.take(60)})")
        s"${e.id}$at: ${e.finding.problem.trim.take(160)}$esc"
      }
      val head = s"review-article [[${l.slug}]]: ${majors.size} open ${Plural.noun(majors.size, "MAJOR")} — "
      val tail = s" (agent run ${runId.take(8)})"
      val body = lines.mkString("; ")
      val room = math.max(40, maxChars - head.length - tail.length)
      head + (if body.length <= room then body else body.take(room - 1) + "…") + tail
    }

  /** The text of the article's one collapsed item — as [[taskText]] writes it, or as the interactive skill does
    * (`review-article <slug>: 2 open MAJORs — …`) — so a run whose ledger lost the item's id rewrites the open one
    * instead of adding a second. Not a [[legacyTask]] (those are closed).
    */
  def articleTask(slug: com.histmersion.domain.ReviewLedger.ArticleTaskSlug, text: com.histmersion.domain.ReviewLedger.ArticleTaskText): com.histmersion.domain.ReviewLedger.ArticleTaskResult =
    val t = text.trim
    !legacyTask(slug, t) &&
    (t.startsWith(s"review-article [[$slug]]: ") || t.startsWith(s"review-article $slug: ")) &&
    "^review-article \\S+: \\d+ open MAJORs?\\b".r.findPrefixOf(t).isDefined

  /** The text of the per-finding items earlier runs left for the article (before #934). */
  def legacyTask(slug: com.histmersion.domain.ReviewLedger.LegacyTaskSlug, text: com.histmersion.domain.ReviewLedger.LegacyTaskText): com.histmersion.domain.ReviewLedger.LegacyTaskResult =
    text.startsWith(s"review-article [[$slug]]: MAJOR ")
