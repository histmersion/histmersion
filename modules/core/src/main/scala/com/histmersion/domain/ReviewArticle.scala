package com.histmersion.domain

import com.histmersion.api.research.ArticleLint

import com.histmersion.api.{Plural, WireEnum}
import com.histmersion.api.research.{
  ArticleCoverage,
  ArticleEdits,
  CitedSource,
  Coverage,
  CoverageReason,
  CritiquePair,
  CritiquePairing,
  CritiqueReason,
  CritiqueRef,
  Critiques,
  CritiqueState,
  DriftKind
}
import com.histmersion.api.checks.Lint
import com.histmersion.api.given

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema

import com.histmersion.domain.AgentProposals.{Proposal, ProposalAnswer}
import com.histmersion.values.{ApprovalKind, WireEnumValues}

/** The `review-article` pipeline's typed answers and its pure logic (docs/research-embedded-agents.md §4.3): the
  * mechanical checks, the validation of each reviewer's answer, the merge of the panel (worst verdict wins,
  * deduplicated, escalation of findings the fixer rejected and a reviewer raised again), the gate, the validation of
  * the fixer's revision (a rejection must quote the record) and the bookkeeping lines. The server runs the steps;
  * everything here is pure and table-tested.
  *
  * JVM-only, like [[EnrichSource]], because quote matching needs `java.text.Normalizer`.
  */
object ReviewArticle:

  // ── vocabularies ───────────────────────────────────────────────────────────────────────────────

  /** Who raised a finding. The prefix is the finding id's letter (`R3`, `C1`, `S2`, `M1`). */
  enum Reviewer(val wire: String, val prefix: String):
    case Mechanical extends Reviewer("mechanical", "M")
    case Rules extends Reviewer("rules", "R")
    case ColdReader extends Reviewer("cold-reader", "C")
    case Skeptic extends Reviewer("skeptic", "S")

  object Reviewer:
    def parse(s: String): Option[Reviewer] = values.find(_.wire == s.trim.toLowerCase)
    given WireEnumValues[Reviewer]:
      def all: Array[Reviewer] = Reviewer.values
      def wire(e: Reviewer): String = e.wire
      def fromWire(s: String): Option[Reviewer] = parse(s)
    given Encoder[Reviewer] = WireEnum.encoder
    given Decoder[Reviewer] = WireEnum.decoder
    given Schema[Reviewer] = WireEnum.schema

  enum Severity(val wire: String):
    case Major extends Severity("major")
    case Minor extends Severity("minor")

  object Severity:
    def parse(s: String): Option[Severity] = values.find(_.wire == s.trim.toLowerCase)
    given WireEnumValues[Severity]:
      def all: Array[Severity] = Severity.values
      def wire(e: Severity): String = e.wire
      def fromWire(s: String): Option[Severity] = parse(s)
    given Encoder[Severity] = WireEnum.encoder
    given Decoder[Severity] = WireEnum.decoder
    given Schema[Severity] = WireEnum.schema

  /** An article's (or one reviewer's) verdict, ordered: the worst one wins. */
  enum Verdict(val wire: String, val rank: Int):
    case Pass extends Verdict("PASS", 0)
    case Minor extends Verdict("MINOR", 1)
    case Major extends Verdict("MAJOR", 2)

  object Verdict:
    def parse(s: String): Option[Verdict] = values.find(_.wire == s.trim.toUpperCase)
    def worst(vs: Iterable[Verdict]): Verdict = vs.foldLeft(Pass)((a, b) => if b.rank > a.rank then b else a)

    /** What a list of findings amounts to on its own. */
    def of(fs: Iterable[Finding]): Verdict =
      if fs.exists(_.severity == Severity.Major) then Major else if fs.nonEmpty then Minor else Pass
    given WireEnumValues[Verdict]:
      def all: Array[Verdict] = Verdict.values
      def wire(e: Verdict): String = e.wire
      def fromWire(s: String): Option[Verdict] = parse(s)
    given Encoder[Verdict] = WireEnum.encoder
    given Decoder[Verdict] = WireEnum.decoder
    given Schema[Verdict] = WireEnum.schema

  /** The four slots of the cold reader's explain-back (question, answer, disagreement, open). */
  val ExplainSlots: List[String] = List("question", "answer", "disagreement", "open")

  // ── LLM answers (their JSON schemas are what each step is asked to fill) ────────────────────────

  /** The record a finding rests on: a citekey, where in its body (the `offset` get_source slices at, when the reviewer
    * read a slice) and the sentence copied from it. Code checks the quote is in that record before the fixer sees it,
    * so the fixer can act on it without searching again.
    */
  final case class EvidenceAnswer(citekey: String, offset: Option[Int] = None, quote: String)
      derives KindlingsCodecAsObject
  object EvidenceAnswer:
    given Schema[EvidenceAnswer] = KindlingsSchema
      .derived[EvidenceAnswer]
      .schema
      .modify(_.citekey)(_.description("The source whose record shows it"))
      .modify(_.offset)(
        _.description(
          "Where in that source's body the quote is (the get_source offset you read at); null if not from the body"
        )
      )
      .modify(_.quote)(_.description("The sentence of that record, copied EXACTLY from get_source"))

  /** At most this many records per finding (more are dropped unread). */
  val MaxEvidence = 3

  private def ChecksDescription: String =
    "One check per earlier finding of yours that the review ledger lists as open (empty when it lists none)"

  final case class FindingAnswer(
      severity: String,
      kind: String,
      where: String,
      quote: String,
      problem: String,
      fix: String,
      evidence: List[EvidenceAnswer] = Nil,
      pair: Option[String] = None
  ) derives KindlingsCodecAsObject
  object FindingAnswer:
    given Schema[FindingAnswer] = KindlingsSchema
      .derived[FindingAnswer]
      .schema
      .modify(_.severity)(_.description("major | minor — by the severity rules of your instructions"))
      .modify(_.kind)(
        _.description("The rule or kind your instructions name (e.g. synth, decorative, term, missing-critique)")
      )
      .modify(_.where)(_.description("The section heading, or the first 8 words of the passage"))
      .modify(_.quote)(_.description("The article's words the finding is about, copied exactly"))
      .modify(_.problem)(_.description("What is wrong, in one or two sentences"))
      .modify(_.fix)(_.description("What would fix it"))
      .modify(_.evidence)(
        _.description(
          s"The records the finding rests on (at most $MaxEvidence): citekey, offset, and the sentence copied exactly; " +
            "empty only when it rests on the article's own text"
        )
      )
      .modify(_.pair)(
        _.description(
          "missing-critique only: the critique-checklist pair it is about, as src>dst (the critic first); null otherwise"
        )
      )

  /** A cold reader's finding: the same, with no evidence — the reader sees no source. */
  final case class ReaderFindingAnswer(
      severity: String,
      kind: String,
      where: String,
      quote: String,
      problem: String,
      fix: String
  ) derives KindlingsCodecAsObject:
    def asFinding: FindingAnswer = FindingAnswer(severity, kind, where, quote, problem, fix)
  object ReaderFindingAnswer:
    given Schema[ReaderFindingAnswer] = KindlingsSchema
      .derived[ReaderFindingAnswer]
      .schema
      .modify(_.severity)(_.description("major | minor — by the severity rules of your instructions"))
      .modify(_.kind)(_.description("The kind your instructions name (e.g. term, opaque, why, lost)"))
      .modify(_.where)(_.description("The section heading, or the first 8 words of the passage"))
      .modify(_.quote)(_.description("The article's words the finding is about, copied exactly"))
      .modify(_.problem)(_.description("What is wrong, in one or two sentences"))
      .modify(_.fix)(_.description("What would fix it"))

  final case class RulesAnswer(
      verdict: String,
      findings: List[FindingAnswer] = Nil,
      filter: String,
      uncitedRelevant: List[String] = Nil,
      statusesSilent: List[String] = Nil,
      spotChecks: List[SpotCheckAnswer] = Nil,
      checks: List[CheckAnswer] = Nil
  ) derives KindlingsCodecAsObject
  object RulesAnswer:
    given Schema[RulesAnswer] = KindlingsSchema
      .derived[RulesAnswer]
      .schema
      .modify(_.verdict)(_.description("PASS | MINOR | MAJOR — the worst of your findings"))
      .modify(_.filter)(_.description("The filter you reconstructed and how many sources it returned"))
      .modify(_.uncitedRelevant)(
        _.description("Citekeys the filter returns that clearly belong and are neither cited nor under Not used")
      )
      .modify(_.statusesSilent)(
        _.description("Cited non-current, non-foundational citekeys whose status the article does not state")
      )
      .modify(_.spotChecks)(
        _.description(
          "Checks 5–6: cited claims whose records you read in this step — at least three, on different sources (all when fewer are cited)"
        )
      )
      .modify(_.checks)(_.description(ChecksDescription))

  /** One cited claim the rules reviewer checked against its record (checks 5–6 of its instructions): the source, the
    * article's words, the AttrScore verdict, and the record's sentence it rests on. Code accepts it only when the step
    * really read that source ([[checkSpotChecks]]).
    */
  final case class SpotCheckAnswer(citekey: String, claim: String, verdict: String, recordQuote: String)
      derives KindlingsCodecAsObject
  object SpotCheckAnswer:
    given Schema[SpotCheckAnswer] = KindlingsSchema
      .derived[SpotCheckAnswer]
      .schema
      .modify(_.citekey)(_.description("The cited source, read with get_source or passages in this step"))
      .modify(_.claim)(_.description("The article's words, copied exactly"))
      .modify(_.verdict)(_.description("attributable | extrapolatory | contradictory"))
      .modify(_.recordQuote)(_.description("The record's sentence it rests on, copied EXACTLY"))

  final case class ColdReaderAnswer(
      verdict: String,
      explainBack: String,
      emptySlots: List[String] = Nil,
      findings: List[ReaderFindingAnswer] = Nil,
      termsUndefined: Int,
      opaqueSentences: Int,
      lostAt: Option[String] = None,
      checks: List[CheckAnswer] = Nil
  ) derives KindlingsCodecAsObject
  object ColdReaderAnswer:
    given Schema[ColdReaderAnswer] = KindlingsSchema
      .derived[ColdReaderAnswer]
      .schema
      .modify(_.verdict)(_.description("PASS | MINOR | MAJOR — the worst of your findings"))
      .modify(_.explainBack)(
        _.description("Five plain sentences: the question, the answer, who disagrees with whom, what is open")
      )
      .modify(_.emptySlots)(
        _.description("The slot names you could not fill from the text; [] when all four are filled")
      )
      // the items: one of the slot names (Claude Code and Codex enforce the answer schema)
      .modify(_.emptySlots)(
        _.modifyUnsafe[String](Schema.ModifyCollectionElements)(
          _.validate(sttp.tapir.Validator.enumeration(ExplainSlots))
        )
      )
      .modify(_.lostAt)(_.description("The section where you stopped following; null if you never did"))
      .modify(_.checks)(_.description(ChecksDescription))

  final case class SkepticAnswer(
      verdict: String,
      findings: List[FindingAnswer] = Nil,
      proposals: List[ProposalAnswer] = Nil,
      claimsChecked: Int,
      checks: List[CheckAnswer] = Nil,
      notMaterial: List[NotMaterialAnswer] = Nil
  ) derives KindlingsCodecAsObject
  object SkepticAnswer:
    given Schema[SkepticAnswer] = KindlingsSchema
      .derived[SkepticAnswer]
      .schema
      .modify(_.verdict)(_.description("PASS | MINOR | MAJOR — the worst of your findings"))
      .modify(_.proposals)(
        _.description(
          "Database errors you noticed, as changes for a person to approve: a status, or a disagreement edge — each with a quote from the record"
        )
      )
      .modify(_.claimsChecked)(_.description("How many load-bearing claims you checked"))
      .modify(_.checks)(_.description(ChecksDescription))
      .modify(_.notMaterial)(
        _.description(
          "Each open pair of the critique checklist that does NOT bear on a claim the article makes, with why " +
            "(every open pair shown is either here or named by a missing-critique MAJOR finding)"
        )
      )

  /** The skeptic's ruling that one open checklist pair does not bear on the article: code stores the reason (the pair
    * becomes `reasoned`, §6.8).
    */
  final case class NotMaterialAnswer(pair: String, reason: String) derives KindlingsCodecAsObject
  object NotMaterialAnswer:
    given Schema[NotMaterialAnswer] = KindlingsSchema
      .derived[NotMaterialAnswer]
      .schema
      .modify(_.pair)(_.description("The open pair, as src>dst"))
      .modify(_.reason)(
        _.description(
          "One sentence: what the article cites the source for, and why the critique does not touch it (quote the article's words)"
        )
      )

  /** A reviewer's verdict on one of its own earlier findings (the review ledger, [[ReviewLedger]]): fixed or not, shown
    * by a quote of the CURRENT article.
    */
  final case class CheckAnswer(id: String, fixed: Boolean, quote: String, note: String) derives KindlingsCodecAsObject
  object CheckAnswer:
    given Schema[CheckAnswer] = KindlingsSchema
      .derived[CheckAnswer]
      .schema
      .modify(_.id)(_.description("The ledger id of your earlier finding (R4, C2, S7…)"))
      .modify(_.fixed)(_.description("true when the current article no longer has the problem"))
      .modify(_.quote)(
        _.description(
          "The current article's words that show it — the fixed text, or the problem still there — copied exactly; " +
            "empty only when the passage was deleted"
        )
      )
      .modify(_.note)(_.description("One sentence: what changed, or why it is not fixed (needed when not fixed)"))

  /** A verification pass's answer (the targeted re-check after a fix): the checks of the findings the fixer claims to
    * have fixed, the regressions found in the edited passages, and lessons for the fixer.
    */
  final case class RecheckAnswer(
      checks: List[CheckAnswer] = Nil,
      findings: List[FindingAnswer] = Nil,
      lessons: List[String] = Nil
  ) derives KindlingsCodecAsObject
  object RecheckAnswer:
    given Schema[RecheckAnswer] = KindlingsSchema
      .derived[RecheckAnswer]
      .schema
      .modify(_.checks)(_.description("One check per finding you were asked to verify"))
      .modify(_.findings)(
        _.description(
          "New problems in the EDITED passages (a regression the fix made); outside them only a factual error, with evidence"
        )
      )
      .modify(_.lessons)(
        _.description(
          "At most three short rules for the fixer, learned from a fix that did not fix or from a regression (each under 25 words)"
        )
      )

  final case class RejectionAnswer(id: String, reason: String, citekey: String, quote: String)
      derives KindlingsCodecAsObject
  object RejectionAnswer:
    given Schema[RejectionAnswer] = KindlingsSchema
      .derived[RejectionAnswer]
      .schema
      .modify(_.id)(_.description("The id of the finding you reject (R2, S1…)"))
      .modify(_.citekey)(_.description("The source whose record contradicts the finding"))
      .modify(_.quote)(
        _.description("The sentence of that record that contradicts it, copied EXACTLY (from get_source)")
      )

  final case class FixerAnswer(
      edits: List[ArticleEdits.EditAnswer] = Nil,
      applied: List[String] = Nil,
      rejected: List[RejectionAnswer] = Nil,
      unresolvedMoved: List[String] = Nil,
      notes: String,
      reasons: List[CoverageReason] = Nil,
      wontFix: List[WontFixAnswer] = Nil,
      setAside: List[SetAsideAnswer] = Nil
  ) derives KindlingsCodecAsObject
  object FixerAnswer:
    given Schema[FixerAnswer] = KindlingsSchema
      .derived[FixerAnswer]
      .schema
      .modify(_.edits)(
        _.description(
          "The changes to the article, in order, each anchored on text copied EXACTLY from the body — small and " +
            "local, never the whole article; code applies them"
        )
      )
      .modify(_.applied)(_.description("Ids of the findings you applied"))
      .modify(_.rejected)(_.description("Findings you reject, each with the record's contradicting sentence"))
      .modify(_.unresolvedMoved)(_.description("Claims you moved to the unresolved section"))
      .modify(_.notes)(_.description("What the next review round should know"))
      .modify(_.reasons)(
        _.description(
          "Why the article leaves out a source its filter returns, one per source (the generated Not used list shows them); " +
            "an empty reason removes one. Only for sources of the filter the article does not cite"
        )
      )
      .modify(_.wontFix)(
        _.description("Findings you decline, with the reason: a MINOR, or a coverage MAJOR when no filter is recorded")
      )
      .modify(_.setAside)(
        _.description(
          "missing-critique findings you answer by setting the critique aside as not material — the reason is stored " +
            "with the article and closes the finding (instead of an edit); with no id, a checklist pair your edits brought in"
        )
      )

  /** The fixer's answer to a missing-critique finding: the critique does not bear on what the article cites the source
    * for. Code stores the reason with the article and closes the finding (§6.8).
    */
  final case class SetAsideAnswer(id: String, pair: Option[String] = None, reason: String)
      derives KindlingsCodecAsObject
  object SetAsideAnswer:
    given Schema[SetAsideAnswer] = KindlingsSchema
      .derived[SetAsideAnswer]
      .schema
      .modify(_.id)(
        _.description(
          "The id of the missing-critique finding; empty for a checklist pair no finding names (one your edits brought in)"
        )
      )
      .modify(_.pair)(_.description("The pair, as src>dst — null to use the one the finding names"))
      .modify(_.reason)(
        _.description(
          "One sentence, after reading both records: what the article cites the source for, and why the critique does not touch it"
        )
      )

  final case class WontFixAnswer(id: String, reason: String) derives KindlingsCodecAsObject
  object WontFixAnswer:
    given Schema[WontFixAnswer] = KindlingsSchema
      .derived[WontFixAnswer]
      .schema
      .modify(_.id)(_.description("The id of the finding you decline"))
      .modify(_.reason)(_.description("Why, in one sentence"))

  // ── verified values (checkpointed) ──────────────────────────────────────────────────────────────

  final case class Finding(
      id: String,
      reviewer: Reviewer,
      severity: Severity,
      kind: String,
      where: String,
      quote: String,
      problem: String,
      fix: String,
      /** The records it rests on, each quote found in its record by code. */
      evidence: List[Evidence] = Nil,
      /** A reviewer raised it again after the fixer rejected it in an earlier round. */
      escalated: Boolean = false,
      /** A missing-critique finding's pair of the critique checklist (§6.8). */
      pair: Option[CritiqueRef] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema:
    def major: Boolean = severity == Severity.Major

    /** One line for a person or a queue item. */
    def line: String =
      val at = Option(where).map(_.trim).filter(_.nonEmpty).fold("")(w => s" ($w)")
      s"$id ${severity.wire}$at: ${problem.trim}"

  object Finding:
    given Schema[Finding] = summon[KindlingsSchema[Finding]].schema

  /** A verified [[EvidenceAnswer]]: `ratio` is how well the quote matched its record (1.0 = verbatim). */
  final case class Evidence(citekey: String, offset: Option[Int], quote: String, ratio: Double)
      derives KindlingsCodecAsObject,
        KindlingsSchema
  object Evidence:
    given Schema[Evidence] = summon[KindlingsSchema[Evidence]].schema

  /** A verified [[SpotCheckAnswer]]: the claim found in the article, the quote found in the record (`ratio`, as for
    * [[Evidence]]), and `tool` — the call of the step that read the source.
    */
  final case class SpotCheck(
      citekey: String,
      claim: String,
      verdict: String,
      recordQuote: String,
      ratio: Double,
      tool: String
  ) derives KindlingsCodecAsObject

  /** One reviewer's verified answer. */
  final case class Report(
      reviewer: Reviewer,
      verdict: Verdict,
      findings: List[Finding],
      explainBack: Option[String] = None,
      emptySlots: List[String] = Nil,
      proposals: List[Proposal] = Nil,
      /** Its verdicts on its own earlier findings (the ledger's open ones), each quote found in the body. */
      checks: List[Check] = Nil,
      /** The rules reviewer's verified spot-checks of cited claims (#1840). */
      spotChecks: List[SpotCheck] = Nil,
      /** The skeptic's verified rulings that an open checklist pair is not material (§6.8). */
      notMaterial: List[CritiqueReason] = Nil
  ) derives KindlingsCodecAsObject

  /** A verified [[CheckAnswer]]. */
  final case class Check(id: String, fixed: Boolean, quote: String, note: String) derives KindlingsCodecAsObject

  /** A verification pass's verified answer: one reviewer's checks, the new findings (scoped: a MAJOR outside the edited
    * passages that is not a factual error with evidence is recorded as minor) and its lessons.
    */
  final case class Recheck(
      reviewer: Reviewer,
      checks: List[Check],
      findings: List[Finding],
      lessons: List[String] = Nil
  ) derives KindlingsCodecAsObject

  /** The article as one round reviews it: its text and the token a patch must present. */
  final case class Snapshot(
      slug: String,
      title: String,
      kind: String,
      body: String,
      updatedAt: Option[String],
      truncated: Boolean,
      coverage: Option[Coverage] = None,
      cited: List[CitedSource] = Nil,
      /** The critique checklist of this body (§6.8), whether or not the article records a filter. */
      critiques: List[CritiquePair] = Nil
  ) derives KindlingsCodecAsObject:
    /** The sections code generates from the filter (#958), as the page shows them after the body. */
    def generated: Option[String] = coverage.map(ArticleCoverage.sections)

  /** One line per source the article cites — `[[citekey]] · year · status · title` — so a reviewer reads every cited
    * status from the brief instead of querying for it (a Codex rules step selected the whole `sources` table for it,
    * 2026-10-16). Empty when it cites none.
    */
  def citedLines(cited: List[CitedSource]): String =
    cited
      .map { c =>
        val status = c.status.fold("not in the corpus")(_.wire)
        val title = c.title.map(t => if t.length > 120 then t.take(119).trim + "…" else t)
        (s"- [[${c.citekey}]]" :: List(c.year.map(_.toString), Some(status), title).flatten).mkString(" · ")
      }
      .mkString("\n")

  /** The panel's merged result for one round. `deferred`: MAJORs new in the run's final pass, the next run's work
    * (§6.9) — open, but not in this verdict and not among [[majors]].
    */
  final case class Merged(
      round: Int,
      verdict: Verdict,
      findings: List[Finding],
      explainBack: Option[String],
      emptySlots: List[String],
      proposals: List[Proposal],
      deferred: List[String] = Nil
  ) derives KindlingsCodecAsObject:
    def majors: List[Finding] = findings.filter(f => f.major && !deferred.contains(f.id))
    def escalated: List[Finding] = findings.filter(_.escalated)
    def count(r: Reviewer): Int = findings.count(_.reviewer == r)

  final case class Rejection(id: String, reason: String, citekey: String, quote: String, ratio: Double)
      derives KindlingsCodecAsObject

  /** The fixer's verified revision: the body its edits produced, and every edit with what became of it (applied, or
    * skipped and why) — what a person reads to see exactly what changed. `unverified` = rejections whose quote code
    * could not find in the record: they do not count as rejections (the finding stays open).
    */
  final case class Fix(
      body: String,
      edits: List[ArticleEdits.EditResult],
      applied: List[String],
      rejected: List[Rejection],
      unverified: List[String],
      unresolvedMoved: List[String],
      notes: String,
      wordsBefore: Int,
      wordsAfter: Int,
      /** The coverage reasons the fixer set (an empty one removes), already checked against the coverage. */
      reasons: List[CoverageReason] = Nil,
      /** Minor findings declined, with the reason. */
      wontFix: List[WontFix] = Nil,
      /** Missing-critique findings set aside as not material, each with the pair and the reason stored. */
      setAside: List[SetAside] = Nil,
      /** Findings code's pairing repair answered (§6.9): claimed like the applied ones. */
      repaired: List[String] = Nil,
      /** What the pairing repair did, one line per citation added. */
      repairs: List[String] = Nil,
      /** Findings the fixer neither applied nor rejected, or claimed without the change code checks for: each with why
        * — the ledger records it as not fixed (§6.9).
        */
      notFixed: List[WontFix] = Nil,
      /** Pairs of the checklist set aside with no finding (a pair the revision brought in). */
      pairsAside: List[CritiqueReason] = Nil
  ) derives KindlingsCodecAsObject

  final case class WontFix(id: String, reason: String) derives KindlingsCodecAsObject

  /** A verified [[SetAsideAnswer]]: the finding, the checklist pair, the reason stored. */
  final case class SetAside(id: String, pair: CritiqueRef, reason: String) derives KindlingsCodecAsObject

  /** What a round's patch did: the article version that holds the PREVIOUS text (undo from it), or why nothing was
    * written.
    */
  final case class Patched(written: Boolean, previousVersion: Option[Long], conflict: Boolean)
      derives KindlingsCodecAsObject

  /** The run's result, for its log line and its queue items. */
  final case class Outcome(
      slug: String,
      rounds: Int,
      verdict: Verdict,
      open: List[Finding],
      applied: Int,
      rejected: Int,
      fixed: Boolean,
      approvals: Int
  ) derives KindlingsCodecAsObject

  // ── mechanical checks (§4.3 step 2) ─────────────────────────────────────────────────────────────

  private val HeadingLine = "^#{1,6}\\s+(.+?)\\s*#*\\s*$".r

  /** The headings of a body, in order, as written. */
  def headings(body: String): List[String] =
    body.linesIterator.collect { case HeadingLine(h) => h.trim }.toList

  private def norm(h: String): String = h.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z ]", "").trim

  /** The sections the guide (rule 4) makes compulsory, as alternatives of heading words. An `index` article (a map of
    * content) has none. `generated`: the article records a filter, so its Not used list is generated (#958) and the
    * body must not have one.
    */
  def requiredSections(kind: String, generated: Boolean = false): List[List[String]] =
    if kind.trim.equalsIgnoreCase("index") then Nil
    else
      List(List("disagreements"), List("unresolved", "open questions")) ++
        Option.when(!generated)(List("not used")).toList

  /** The finding kind of the coverage checks (#958): hand-written generated sections, and drift. */
  val CoverageKind = "coverage"

  /** The checks code can make with certainty, as `M<n>` findings: banned filler, a paragraph with no `[[citekey]]`,
    * escaped link brackets, a hand-written Sources cited heading, a missing compulsory section, a duplicated heading,
    * and `[[links]]` that resolve to nothing. With `generated` (the article records a filter, #958) a hand-written Not
    * used section or `_Method_` note is one too: code generates both, so a written one duplicates them and goes stale.
    */
  def mechanical(
      slug: String,
      kind: String,
      body: String,
      isTarget: String => Boolean,
      generated: Boolean = false
  ): List[Finding] =
    val hs = headings(body)
    def f(sev: Severity, kind: String, where: String, quote: String, problem: String, fix: String) =
      (sev, kind, where, quote, problem, fix)
    val filler = ArticleLint
      .articleFiller(List(slug -> body))
      .map(i =>
        f(
          Severity.Minor,
          "language",
          "body",
          "",
          s"banned filler — ${i.detail.stripPrefix("filler: ")}",
          "rewrite the sentence"
        )
      )
    // the lint rule's own test, one paragraph at a time (a link to an article is not a citation)
    val uncited = Lint
      .extractParagraphs(body)
      .filter(p => ArticleLint.articleUncitedParagraphs(List(slug -> p)).nonEmpty)
      .map(p =>
        f(
          Severity.Major,
          "citation",
          p.split("\\s+").take(8).mkString(" "),
          p.take(200),
          "a paragraph with no [[citekey]]",
          "cite the source the paragraph rests on, or move it to the unresolved section"
        )
      )
    val escaped = Option.when(body.contains("\\[\\["))(
      f(
        Severity.Major,
        "citation",
        "body",
        "\\[\\[",
        "escaped link brackets render as text and link nothing",
        "write [[citekey]] unescaped"
      )
    )
    val sourcesCited = hs
      .filter(h => norm(h) == "sources cited")
      .take(1)
      .map(h =>
        f(
          Severity.Major,
          "section",
          h,
          s"## $h",
          "a hand-written Sources cited section (the page generates it)",
          "delete the section"
        )
      )
    val handWritten =
      if !generated then Nil
      else
        hs.filter(h => norm(h).startsWith("not used"))
          .take(1)
          .map(h =>
            f(
              Severity.Major,
              CoverageKind,
              h,
              s"## $h",
              "a hand-written Not used section: the page generates it from the article's filter and its reasons",
              "delete the section; give each left-out source's reason in `reasons` instead"
            )
          ) ++
          body.linesIterator
            .map(_.trim)
            .find(_.startsWith("_Method"))
            .toList
            .map(l =>
              f(
                Severity.Major,
                CoverageKind,
                "Method",
                l.take(200),
                "a hand-written Method note: the page generates it (the filter, its counts, the statuses, the edges)",
                "delete the note"
              )
            )
    val missing = requiredSections(kind, generated)
      .filterNot(alts => hs.exists(h => alts.exists(a => norm(h).startsWith(a))))
      .map(alts =>
        f(
          Severity.Major,
          "section",
          "headings",
          "",
          s"no ${alts.map(a => s"'$a'").mkString(" or ")} section (writing guide rule 4)",
          "add the section"
        )
      )
    val dup = hs
      .groupBy(norm)
      .collect { case (_, xs) if xs.size > 1 => xs.head }
      .toList
      .sorted
      .map(h => f(Severity.Minor, "section", h, s"## $h", "a duplicated heading", "merge the two sections"))
    val dangling = AgentVerify
      .wikilinks(body)
      .filterNot(isTarget)
      .map(t =>
        f(
          Severity.Major,
          "citation",
          s"[[$t]]",
          s"[[$t]]",
          s"[[$t]] is neither a citekey nor an article",
          "link a real citekey or article"
        )
      )
    (filler ++ uncited ++ escaped ++ sourcesCited ++ handWritten ++ missing ++ dup ++ dangling).zipWithIndex.map {
      case ((sev, k, w, q, p, fx), i) => Finding(s"M${i + 1}", Reviewer.Mechanical, sev, k, w, q, p, fx)
    }

  /** The coverage drift of an article that records a filter (#958), as findings to follow the [[mechanical]] ones
    * (`M<from>`…): a source of the filter neither cited nor given a reason is MAJOR (the guide: every source ends in
    * Sources cited or Not used, with a reason); a reason for a source the body now cites, or one the filter no longer
    * returns, is minor (it is not shown).
    */
  def coverageDrift(c: Coverage, from: Int): List[Finding] =
    def keys(cks: List[String]) =
      val shown = cks.take(40).map(ck => s"[[$ck]]").mkString(", ")
      if cks.size > 40 then s"$shown (+${cks.size - 40} more)" else shown
    c.drift.zipWithIndex.map { (d, i) =>
      val (sev, problem, fix) = d.kind match
        case DriftKind.Unplaced =>
          (
            Severity.Major,
            s"${Plural.count(d.citekeys.size, "source")} the filter returns ${
                if d.citekeys.size == 1 then "is" else "are"
              } neither cited nor given a reason: ${keys(d.citekeys)}",
            "cite each one where its record bears on the question, or give its reason in `reasons` (read the record first)"
          )
        case DriftKind.ReasonForCited =>
          (
            Severity.Minor,
            s"a reason is stored for ${Plural.count(d.citekeys.size, "source")} the article cites: ${keys(d.citekeys)}",
            "remove the reason (an empty one in `reasons`)"
          )
        case DriftKind.ReasonOutside =>
          (
            Severity.Minor,
            s"a reason is stored for ${Plural.count(d.citekeys.size, "source")} the filter no longer returns: ${keys(d.citekeys)}",
            "remove the reason (an empty one in `reasons`)"
          )
      Finding(s"M${from + i}", Reviewer.Mechanical, sev, CoverageKind, ArticleCoverage.NotUsedHeading, "", problem, fix)
    }

  // ── validating a reviewer's answer ──────────────────────────────────────────────────────────────

  /** How a stated evidence record is checked: a verified [[Evidence]], or why it was dropped. */
  type EvidenceCheck = EvidenceAnswer => Either[String, Evidence]

  /** No evidence is checked (the cold reader has none). */
  val NoEvidence: EvidenceCheck = e => Left(s"[[${e.citekey}]]: this reviewer cites no records")

  /** The check every reviewer's evidence passes: a real citekey, and a quote that IS in its record (the OCR-tolerant
    * matcher the fixer's rejections use). `text` is the record as get_source shows it (summary, findings, notes, body).
    */
  def evidenceCheck(isCitekey: String => Boolean, text: String => Option[String], minRatio: Double): EvidenceCheck =
    e =>
      val ck = Option(e.citekey).map(_.trim.stripPrefix("[[").stripSuffix("]]").trim).getOrElse("")
      val q = Option(e.quote).map(_.trim).getOrElse("")
      if q.isEmpty then Left(s"[[$ck]]: no quote")
      else if !isCitekey(ck) then Left(s"'$ck' is not a citekey")
      else
        text(ck).map(t => AgentVerify.findQuote(q, t, minRatio)).filter(_.accepted) match
          case Some(m) => Right(Evidence(ck, e.offset.filter(_ >= 0), q, m.ratio))
          case None    => Left(s"the quote is not in the record of [[$ck]]: \"${q.take(80)}\"")

  /** Findings as stated → verified findings with code-assigned ids (`R1`…), plus what was dropped. A finding with no
    * problem, no `where`, or a severity outside the vocabulary is a problem to send back. Evidence that does not check
    * out is DROPPED with a warning (the finding stays; the fixer just does not get that record) — it is never a reason
    * to ask the reviewer again.
    */
  def checkFindings(
      r: Reviewer,
      as: List[FindingAnswer],
      ev: EvidenceCheck = NoEvidence
  ): (List[Finding], List[String], List[String]) =
    val checked = as.zipWithIndex.map { (a, i) =>
      val label = s"finding ${i + 1} (${Option(a.problem).getOrElse("").take(60)})"
      Severity.parse(Option(a.severity).getOrElse("")) match
        case None => Left(s"$label: severity '${a.severity}' is neither major nor minor")
        case Some(_) if Option(a.problem).forall(_.trim.isEmpty) => Left(s"$label: no problem stated")
        case Some(_) if Option(a.where).forall(_.trim.isEmpty)   =>
          Left(s"$label: no where stated — give the section heading or the passage's first words")
        case Some(s) => Right((s, a))
    }
    val built = checked.collect { case Right(v) => v }.zipWithIndex.map { case ((s, a), i) =>
      val id = s"${r.prefix}${i + 1}"
      val evs = Option(a.evidence).getOrElse(Nil).take(MaxEvidence).map(ev)
      val f = Finding(
        id,
        r,
        s,
        Option(a.kind).map(_.trim).getOrElse(""),
        Option(a.where).map(_.trim).getOrElse(""),
        Option(a.quote).map(_.trim).getOrElse(""),
        a.problem.trim,
        Option(a.fix).map(_.trim).getOrElse(""),
        evs.collect { case Right(e) => e },
        pair = Option(a.pair).flatten.flatMap(CritiqueRef.parse(_).toOption)
      )
      (f, evs.collect { case Left(m) => s"${r.wire}: $id evidence dropped — $m" })
    }
    (built.map(_._1), checked.collect { case Left(m) => m }, built.flatMap(_._2))

  /** The stated verdict must not be worse than the findings show (a MAJOR with no major finding is a finding left out);
    * a milder one is simply corrected. On the last attempt the findings decide.
    */
  private def verdictOf(stated: String, fs: List[Finding], last: Boolean): Either[String, Verdict] =
    val derived = Verdict.of(fs)
    Verdict.parse(Option(stated).getOrElse("")) match
      case None if !last && Option(stated).forall(_.trim.isEmpty) => Left("no verdict given — PASS, MINOR or MAJOR")
      case None if !last                             => Left(s"verdict '$stated' is not PASS, MINOR or MAJOR")
      case Some(v) if v.rank > derived.rank && !last =>
        Left(s"the verdict is ${v.wire} but no finding is ${v.wire.toLowerCase} — list the finding that makes it so")
      case _ => Right(derived)

  private def settle(
      r: Reviewer,
      verdict: Either[String, Verdict],
      fs: List[Finding],
      problems: List[String],
      dropped: List[String],
      last: Boolean
  )(build: Verdict => Report): Either[List[String], (Report, List[String])] =
    val all = problems ++ verdict.left.toOption.toList
    if all.nonEmpty && !last then Left(all)
    else Right((build(verdict.getOrElse(Verdict.of(fs))), all.map(p => s"${r.wire}: dropped — $p") ++ dropped))

  /** `critique`: a missing-critique MAJOR of the rules reviewer is calibrated like the skeptic's ([[checkCritiques]];
    * it owes no rulings and is never sent back for one).
    */
  def checkRules(
      a: RulesAnswer,
      last: Boolean,
      ev: EvidenceCheck = NoEvidence,
      prior: Option[Prior] = None,
      spots: Option[SpotRule] = None,
      critique: Option[CritiqueRule] = None
  ): Either[List[String], (Report, List[String])] =
    val (stated, ps, ds) = checkFindings(Reviewer.Rules, a.findings, ev)
    val (fs, _, kws) = critique.fold((stated, Nil, Nil))(c =>
      checkCritiques(Reviewer.Rules, stated, c.copy(shown = Nil, full = false), Nil, last)
    )
    val (cs, cps, cws) = checksOf(Reviewer.Rules, prior, a.checks, last)
    val (ss, sps, sws) = spots.fold((Nil, Nil, Nil))(checkSpotChecks(Option(a.spotChecks).getOrElse(Nil), _, ev, last))
    val verdict = verdictOf(a.verdict, stated, last).map(_ => Verdict.of(fs))
    settle(Reviewer.Rules, verdict, fs, ps ++ cps ++ sps, ds ++ cws ++ sws ++ kws, last)(v =>
      Report(Reviewer.Rules, v, fs, checks = cs, spotChecks = ss)
    )

  // ── spot-checks: the rules reviewer's lookups, verified (#1840) ────────────────────────────────

  /** The tools whose result shows a source's record: a spot-check rests on one of them. */
  val SpotCheckTools: Set[String] = Set("get_source", "passages")

  val SpotVerdicts: List[String] = List("attributable", "extrapolatory", "contradictory")

  /** What a full pass's spot-checks are held to: `required` of them verified on different sources; each claim in
    * `body`, each citekey one the article cites (`cited`), each read by one of `calls` — the tool calls of the step so
    * far, every attempt's (a retry amends its answer without looking the records up again).
    */
  final case class SpotRule(
      required: Int,
      cited: Set[String],
      body: String,
      calls: List[AgentVerify.ToolUse],
      minRatio: Double
  )

  /** How many spot-checks a full pass needs: `min` of them, or one per source the article cites in the corpus when it
    * cites fewer.
    */
  def spotChecksRequired(min: Int, citedInCorpus: Int): Int = math.max(0, math.min(min, citedInCorpus))

  /** Does tool call `u` read the record of `ck`? A get_source or passages call whose input names it. */
  def reads(u: AgentVerify.ToolUse, ck: String): Boolean =
    SpotCheckTools(u.tool.trim) && ck.nonEmpty &&
      Option(u.input).exists(_.toLowerCase(java.util.Locale.ROOT).contains(ck.toLowerCase(java.util.Locale.ROOT)))

  /** Verify the spot-checks: each needs a cited citekey, the claim in the article, a verdict from [[SpotVerdicts]], a
    * tool call of this step that read that source ([[reads]]) and the record's sentence found in its record (`ev`, the
    * check evidence passes). Fewer than `required` verified, on different sources, is a problem to send back — the
    * model's own count of its checks is not taken on trust (sonnet answered from the brief alone with a review ledger
    * in it, #1840); on the last attempt it is a warning. A spot-check that fails beyond those required is dropped with
    * a warning. Returns (verified, problems, warnings).
    */
  def checkSpotChecks(
      as: List[SpotCheckAnswer],
      rule: SpotRule,
      ev: EvidenceCheck,
      last: Boolean
  ): (List[SpotCheck], List[String], List[String]) =
    val results = as.zipWithIndex.map { (a, i) =>
      val ck = Option(a.citekey).map(_.trim.stripPrefix("[[").stripSuffix("]]").trim).getOrElse("")
      val claim = Option(a.claim).map(_.trim).getOrElse("")
      val verdict = Option(a.verdict).map(_.trim.toLowerCase(java.util.Locale.ROOT)).getOrElse("")
      val label = s"spot-check ${i + 1} ([[$ck]])"
      val checked: Either[String, SpotCheck] =
        if ck.isEmpty then Left(s"spot-check ${i + 1}: no citekey")
        else if rule.cited.nonEmpty && !rule.cited.exists(_.equalsIgnoreCase(ck)) then
          Left(s"$label: the article does not cite it — check a source it cites")
        else if !SpotVerdicts.contains(verdict) then
          Left(s"$label: verdict '${a.verdict}' is not one of ${SpotVerdicts.mkString(", ")}")
        else if claim.isEmpty || !AgentVerify.findQuote(claim, rule.body, rule.minRatio).accepted then
          Left(s"$label: the claim is not in the article — copy the article's words exactly")
        else
          rule.calls.find(reads(_, ck)) match
            case None =>
              Left(
                s"$label: no get_source or passages call for it in this step — read the record before reporting what it says"
              )
            case Some(call) =>
              ev(EvidenceAnswer(ck, None, Option(a.recordQuote).getOrElse(""))) match
                case Left(m)  => Left(s"$label: $m")
                case Right(e) => Right(SpotCheck(e.citekey, claim, verdict, e.quote, e.ratio, call.tool.trim))
      checked
    }
    val verified = results.collect { case Right(s) => s }
    val failed = results.collect { case Left(m) => m }
    val sources = verified.map(_.citekey.toLowerCase(java.util.Locale.ROOT)).distinct.size
    if sources >= rule.required then (verified, Nil, failed.map(m => s"rules: dropped — $m"))
    else
      val read = rule.calls.filter(u => SpotCheckTools(u.tool.trim)).size
      val short = s"spot-checks: $sources of the ${rule.required} required are verified (on different cited sources; " +
        s"this step made $read get_source/passages call(s)) — read the records now with get_source or passages, and " +
        "report each check with the record's sentence copied from the result"
      val problems = short :: failed
      if last then (verified, Nil, problems.map(p => s"rules: unverified — $p"))
      else (verified, problems, Nil)

  /** The finding code adds from the cold reader's `termsUndefined` count: above [[TermCountThreshold]] terms, MINOR at
    * any count. The count is inflated (the reader lists places, peoples and periods a general reader knows), and as a
    * MAJOR it made PASS unreachable (docs/research-review-convergence.md §4); the reader's individually named undefined
    * terms keep the severity the reader gives them. It stays a finding so the fixer sees it, and it is the one count
    * entry of the ledger ([[ReviewLedger.absorb]] matches it across counts and replaces an earlier MAJOR one).
    */
  object TermCount:
    val Kind = "term-count"
    val Where = "explain-back"
    def problem(n: Int): String = s"$n terms undefined (count only; the individual findings carry the severity)"
    val Fix = "define each on first use, and add a Glossary"

    /** The count finding — this one, or the MAJOR "N terms are undefined (more than 8)" earlier runs stored. */
    private val Problem = """^\d+ terms (are )?undefined\b.*""".r
    def is(f: Finding): Boolean =
      f.reviewer == Reviewer.ColdReader && Option(f.quote).forall(_.trim.isEmpty) &&
        AgentVerify.normalise(Option(f.where).getOrElse("")) == AgentVerify.normalise(Where) &&
        (f.kind == Kind || (f.kind == "term" && Problem.matches(Option(f.problem).getOrElse("").trim)))

  private val NoSlot = "^(none|no\\b|nothing|n/?a\\b|all\\b|-+$|—).*".r

  /** The reader's `emptySlots`, normalised (the schema asks for slot names; a model may still write prose): blanks and
    * "none…"/"n/a"/"all filled…" entries mean NO empty slot and are dropped; an entry whose first word is a slot name,
    * singular or plural ("open questions", "Disagreements:") is that slot. Returns the slots and the entries that are
    * neither.
    */
  def emptySlotsOf(raw: List[String]): (List[String], List[String]) =
    val entries = raw.map(_.trim.toLowerCase(java.util.Locale.ROOT)).filter(_.nonEmpty).filterNot(NoSlot.matches)
    val read = entries.map { e =>
      val first = e.takeWhile(_.isLetter)
      ExplainSlots.find(s => first == s || first == s + "s").toLeft(e)
    }
    (read.collect { case Left(s) => s }.distinct, read.collect { case Right(e) => e }.distinct)

  /** Above this many undefined terms code adds the (minor) count finding. */
  val TermCountThreshold = 8

  /** The cold reader's severity rules are partly mechanical: an empty explain-back slot is MAJOR whether or not the
    * model said so, and more than eight undefined terms is a MINOR count finding ([[TermCount]]) — code adds both. A
    * MAJOR verdict the reader states for the count alone is corrected, not sent back (its instructions may still call
    * the count major).
    */
  def checkColdReader(
      a: ColdReaderAnswer,
      last: Boolean,
      prior: Option[Prior] = None
  ): Either[List[String], (Report, List[String])] =
    val (stated, ps, _) = checkFindings(Reviewer.ColdReader, a.findings.map(_.asFinding))
    val (slots, odd) = emptySlotsOf(a.emptySlots)
    val unknown = odd.map(s => s"empty slot '$s' is not one of ${ExplainSlots.mkString(", ")}")
    val explain = Option(a.explainBack).map(_.trim).filter(_.nonEmpty)
    val extra =
      slots
        .filter(ExplainSlots.contains)
        .map(s =>
          (
            Severity.Major,
            "empty-slot",
            s"the explain-back has no '$s': the article does not say it",
            "state it in the opening section"
          )
        ) ++
        Option.when(a.termsUndefined > TermCountThreshold)(
          (Severity.Minor, TermCount.Kind, TermCount.problem(a.termsUndefined), TermCount.Fix)
        )
    val added = extra.zipWithIndex.map { case ((sev, k, p, fx), i) =>
      Finding(s"C${stated.size + i + 1}", Reviewer.ColdReader, sev, k, TermCount.Where, "", p, fx)
    }
    val fs = stated ++ added
    val (cs, cps, cws) = checksOf(Reviewer.ColdReader, prior, a.checks, last)
    val problems = ps ++ unknown ++ explain.fold(List("no explain-back"))(_ => Nil) ++ cps
    val countOnly =
      a.termsUndefined > TermCountThreshold && Verdict.parse(Option(a.verdict).getOrElse("")).contains(Verdict.Major)
    settle(Reviewer.ColdReader, verdictOf(a.verdict, fs, last || countOnly), fs, problems, cws, last)(v =>
      Report(Reviewer.ColdReader, v, fs, explain, slots.filter(ExplainSlots.contains), checks = cs)
    )

  /** The skeptic's proposals are verified like every other effect: vocabularies, citekeys, and a quote that is really
    * in the record. Unverifiable ones are dropped with a warning (they are not findings; nothing is lost by dropping
    * them). Its findings' evidence is checked the same way.
    */
  def checkSkeptic(
      a: SkepticAnswer,
      last: Boolean,
      isCitekey: String => Boolean,
      text: String => Option[String],
      minRatio: Double,
      prior: Option[Prior] = None,
      critique: Option[CritiqueRule] = None
  ): Either[List[String], (Report, List[String])] =
    val (stated, ps, ds) = checkFindings(Reviewer.Skeptic, a.findings, evidenceCheck(isCitekey, text, minRatio))
    val props = a.proposals.map(AgentProposals.check(_, isCitekey, text, minRatio))
    val verified = props.collect { case Right(p) => p }
    val (fs, kps, kws) = critique.fold((stated, Nil, Nil))(checkCritiques(Reviewer.Skeptic, stated, _, verified, last))
    val (nm, nps, nws) =
      critique.fold((Nil, Nil, Nil))(checkNotMaterial(Option(a.notMaterial).getOrElse(Nil), fs, _, last))
    val (cs, cps, cws) = checksOf(Reviewer.Skeptic, prior, a.checks, last)
    // the stated verdict is held to the findings as stated: one code records minor does not make it a finding left out
    val verdict = verdictOf(a.verdict, stated, last).map(_ => Verdict.of(fs))
    settle(Reviewer.Skeptic, verdict, fs, ps ++ cps ++ kps ++ nps, ds ++ cws ++ kws ++ nws, last)(v =>
      Report(Reviewer.Skeptic, v, fs, proposals = verified, checks = cs, notMaterial = nm)
    )
      .map((r, ws) => (r, ws ++ props.collect { case Left(m) => s"skeptic: dropped $m" }))

  // ── the critique checklist (docs/research-review-convergence.md §6.8) ──────────────────────────

  /** The finding kind of an omitted critique. */
  val MissingCritique = "missing-critique"

  def isMissingCritique(f: Finding): Boolean = f.kind.trim.toLowerCase(java.util.Locale.ROOT) == MissingCritique

  /** What a missing-critique finding is held to in one pass: the article's checklist (`pairs`), the open pairs the
    * skeptic was shown in full and must rule on (`shown`; empty for a re-check, the confirming panel and the rules
    * reviewer, which owe no rulings), whether an answer may be sent back for it (`full`: a full panel's skeptic), and
    * the body the pass reviewed (`body`; empty: the quote is not checked) with the quote matcher's threshold.
    */
  final case class CritiqueRule(
      pairs: List[CritiquePair],
      shown: List[CritiquePair],
      full: Boolean,
      body: String = "",
      minRatio: Double = 0.9
  )

  private def recordedMinor(f: Finding, why: String): Finding =
    f.copy(severity = Severity.Minor, problem = s"${f.problem.trim} (recorded minor: $why)")

  /** GRADE hedges and contest words: a sentence carrying one already qualifies its claim. */
  private val Hedge =
    ("(?i)(?<!\\p{L})(may|might|could|probably|possibly|perhaps|likely|unlikely|uncertain|tentative|tentatively|" +
      "disputed|contested|debated|controversial|challenged|challenges|questioned|questions|doubt|doubts|doubtful|doubted|" +
      "criticised|criticized|criticises|criticizes|critics|unproven|unproved|hypothetical|speculative|conjectural|" +
      "not settled|open question|remains open|is open)(?!\\p{L})").r

  private val SentenceEnd = "(?<=[.!?])[\"”’)]*\\s+(?=[\\p{Lu}\"“(\\[*])".r

  /** Words a full stop follows without ending a sentence (`Chang et al. (2015)`, `c. 1200 BCE`). */
  private val Abbrev = Set(
    "al",
    "c",
    "ca",
    "cf",
    "e.g",
    "i.e",
    "etc",
    "vs",
    "fl",
    "st",
    "mt",
    "no",
    "vol",
    "pp",
    "p",
    "ed",
    "eds",
    "fig",
    "dr",
    "mr",
    "mrs",
    "ms",
    "prof",
    "ff",
    "ch",
    "repr",
    "trans",
    "n"
  )

  /** A paragraph's sentences: split after `.`, `!` or `?` before a capital, a quote, a bracket or an emphasis mark —
    * never after an abbreviation or an initial (`M. L. West`).
    */
  def sentences(p: String): List[String] =
    val cuts = SentenceEnd
      .findAllMatchIn(p)
      .filter { m =>
        val word = p
          .substring(0, m.start)
          .split("[\\s(\\[]")
          .lastOption
          .getOrElse("")
          .stripSuffix(".")
          .stripSuffix(".")
          .replaceAll("^[^\\p{L}]+", "")
          .toLowerCase(java.util.Locale.ROOT)
        !(Abbrev(word) || (word.length == 1 && word.head.isLetter))
      }
      .map(_.end)
      .toList
    (0 :: cuts).zip(cuts :+ p.length).map((a, b) => p.substring(a, b).trim).filter(_.nonEmpty)

  /** The sentence of `body` that holds `quote` — the fewest consecutive sentences (at most four) when it spans more
    * than one, else its paragraph; None when the quote is not in the body.
    */
  def claimSentence(body: String, quote: String, minRatio: Double): Option[String] =
    val q = Option(quote).map(_.trim).getOrElse("")
    def has(t: String) = AgentVerify.findQuote(q, t, minRatio).accepted
    if q.isEmpty then None
    else
      Critiques.paragraphs(body).find(has).map { p =>
        val ss = sentences(p)
        (1 to math.min(4, ss.size)).iterator.flatMap(n => ss.sliding(n).map(_.mkString(" "))).find(has).getOrElse(p)
      }

  /** Does the text carry a hedge or a contest word (outside its links)? */
  def qualified(text: String): Boolean =
    Hedge.findFirstIn("""\[\[[^\]]*\]\]""".r.replaceAllIn(text, " ")).isDefined

  private val ClauseEnd = "(;|,\\s+(?:and|but|while|whereas)\\s|\\s(?:but|whereas)\\s)".r

  /** The words a claim stands in: the quote and what follows it in its sentence up to the end of its clause (`;`,
    * `, and …`, `but …`) — so a hedge or a status label on ANOTHER source in the same sentence ("Gray and Atkinson
    * (2003) dated the split… [[gray2003_language]], and Bouckaert et al. (2012, contested)…") does not qualify it. The
    * whole sentence when the quote's end is not found.
    */
  def claimWindow(sentence: String, quote: String): String =
    val ws = quote.split("[^\\p{L}\\p{N}]+").filter(_.nonEmpty).takeRight(3)
    if ws.isEmpty then sentence
    else
      val re = ("(?i)" + ws.map(java.util.regex.Pattern.quote).mkString("[^\\p{L}\\p{N}]+")).r
      re.findAllMatchIn(sentence).toList.lastOption.fold(sentence) { m =>
        val after = sentence.substring(m.end)
        val tail = ClauseEnd.findFirstMatchIn(after).fold(after)(c => after.substring(0, c.start))
        sentence.substring(0, m.end) + tail
      }

  private val StatusLabel = "(contested|challenged|disputed|superseded|debunked|retracted)"

  /** Does the sentence label the criticised work itself as disputed — its link followed by a status
    * (`([[smith1998_death]], contested — challenged by …)`, `[[x]] (contested`)? A short form of the citekey
    * (`[[knapp2016]]` for `knapp2016_seven`) counts.
    */
  def labelled(sentence: String, criticised: String): Boolean =
    val ck = criticised.trim.toLowerCase(java.util.Locale.ROOT)
    val forms = (ck :: Option.when(ck.contains("_"))(ck.takeWhile(_ != '_')).toList).map(java.util.regex.Pattern.quote)
    ("(?i)\\[\\[(" + forms.mkString("|") + ")\\]\\][\\s,;(—–-]{0,6}" + StatusLabel).r.findFirstIn(sentence).isDefined

  /** Is the claim `quote` states already qualified in `sentence`: a hedge or contest word in its own clause
    * ([[claimWindow]]), or a status label on the criticised work ([[labelled]])?
    */
  def qualifiedClaim(sentence: String, quote: String, criticised: String): Boolean =
    qualified(claimWindow(sentence, quote)) || labelled(sentence, criticised)

  /** SEVERITY CALIBRATION (§6.9). A missing-critique MAJOR stands only when the critique is a recorded edge the article
    * has not answered AND the article states the criticised claim unqualified:
    *   - its pair (`pair`, either direction; the finding then carries the checklist's direction) is an OPEN pair of the
    *     checklist — a pair addressed or reasoned is recorded minor, with why;
    *   - the article cites the criticised work ([[CritiquePair.criticised]]) — else it states none of its claims:
    *     minor;
    *   - its quote is the article's sentence that states the claim (or the conclusion the critique would change), found
    *     in the body, with no hedge or contest word in the claim's clause and no status label on the criticised work
    *     ([[qualifiedClaim]]) — a qualified one is minor; a quote not in the body is sent back on a full panel's
    *     skeptic, else minor;
    *   - no pair on the checklist means no recorded edge: minor (an edge the skeptic proposes waits for approval and
    *     joins the next run's checklist); an EMPTY `pair` on a full panel's skeptic is sent back (it may name a
    *     recorded pair), minor on the last attempt.
    * Minor missing-critique findings, and every other kind, pass unchanged. Returns (findings, problems to send back,
    * warnings).
    */
  def checkCritiques(
      r: Reviewer,
      fs: List[Finding],
      rule: CritiqueRule,
      proposals: List[Proposal],
      last: Boolean
  ): (List[Finding], List[String], List[String]) =
    val results = fs.map { f =>
      if !isMissingCritique(f) || !f.major then Right((f, None))
      else
        val label = s"${f.id} (missing-critique, major)"
        def minor(g: Finding, why: String) =
          Right((recordedMinor(g, why), Some(s"${r.wire}: $label — $why; recorded minor")))
        f.pair.flatMap(Critiques.find(rule.pairs, _)) match
          case Some(p) if p.open =>
            val g = f.copy(pair = Some(p.ref))
            if !p.criticised.cited then
              minor(g, s"the article does not cite [[${p.criticised.citekey}]], so it states none of its claims")
            else if rule.body.isEmpty then Right((g, None))
            else
              claimSentence(rule.body, f.quote, rule.minRatio) match
                case None if rule.full && !last =>
                  Left(
                    s"$label: its quote is not in the article — quote the article's sentence that states the criticised " +
                      "claim (or the conclusion the critique would change), copied exactly"
                  )
                case None => minor(g, "its quote is not in the article, so no unqualified claim is shown")
                case Some(sentence) if qualifiedClaim(sentence, f.quote, p.criticised.citekey) =>
                  minor(g, s"the article already qualifies the claim: \"${sentence.trim.take(120)}\"")
                case Some(_) => Right((g, None))
          case Some(p) =>
            val why =
              if p.state == CritiqueState.Addressed then
                s"${p.ref.wire} is addressed — both sources are cited in one paragraph"
              else s"${p.ref.wire} is set aside with a stored reason: \"${p.reason.getOrElse("").take(160)}\""
            minor(f.copy(pair = Some(p.ref)), why)
          case None if f.pair.isEmpty && rule.full && !last =>
            Left(
              s"$label names no pair of the critique checklist (its `pair` is empty): name the pair it is about in " +
                "`pair` (src>dst) — a disagreement with no recorded edge is a proposal, not a MAJOR"
            )
          case None =>
            val proposed = f.pair.exists(ref =>
              proposals.exists(pp =>
                pp.kind == ApprovalKind.Edge && pp.target.exists(t => CritiqueRef(pp.citekey, t).same(ref))
              )
            )
            val why = f.pair.fold("it names no checklist pair")(ref =>
              s"no edge is recorded between [[${ref.src}]] and [[${ref.dst}]]" +
                (if proposed then " — its proposed edge waits for approval and joins the next run's checklist" else "")
            )
            minor(f, why)
    }
    (
      results.collect { case Right((f, _)) => f },
      results.collect { case Left(p) => p },
      results.collect { case Right((_, Some(w))) => w }
    )

  /** One missing-critique finding per pair and pass (§6.9): of the findings naming the same two sources (either
    * direction), the skeptic's is kept — its pair is the checklist's — and the others are dropped, as is a non-skeptic
    * one whose pair an open skeptic entry of the ledger already holds (the cold reader repeating a ledger finding it
    * read; the rules reviewer the skeptic's). Returns the findings kept and a warning per one dropped.
    */
  def onePerPair(fs: List[Finding], heldBySkeptic: List[CritiqueRef]): (List[Finding], List[String]) =
    val withPair = fs.filter(f => isMissingCritique(f) && f.pair.isDefined)
    val keep = fs.filter { f =>
      if !isMissingCritique(f) || f.pair.isEmpty then true
      else
        val ref = f.pair.get
        val rivals = withPair.filter(g => g.pair.exists(_.same(ref)))
        val first = rivals.find(_.reviewer == Reviewer.Skeptic).getOrElse(rivals.head)
        val held = f.reviewer != Reviewer.Skeptic && heldBySkeptic.exists(_.same(ref))
        (first eq f) && !held
    }
    val dropped = fs.filterNot(f => keep.exists(_ eq f))
    (
      keep,
      dropped.map(f =>
        s"${f.reviewer.wire}: ${f.id} dropped — another missing-critique finding already names ${f.pair.fold("")(_.wire)}"
      )
    )

  /** The skeptic's rulings: every open pair it was shown in full is named by a missing-critique finding (MAJOR, or
    * minor after [[checkCritiques]]'s calibration) or ruled not material here, with a reason. A not-material ruling of
    * an open pair of the checklist becomes a [[CritiqueReason]] to store (one the skeptic also raised as a MAJOR is
    * dropped: the finding wins). A pair it did not rule on sends the answer back; on the last attempt it is a warning
    * (the pair stays open). Returns (reasons, problems, warnings).
    */
  def checkNotMaterial(
      as: List[NotMaterialAnswer],
      fs: List[Finding],
      rule: CritiqueRule,
      last: Boolean
  ): (List[CritiqueReason], List[String], List[String]) =
    val named = fs.filter(f => f.major && isMissingCritique(f)).flatMap(_.pair)
    val results = as.map { a =>
      val reason = Option(a.reason).map(_.trim.replaceAll("\\s+", " ")).getOrElse("")
      CritiqueRef.parse(Option(a.pair).getOrElse("")).flatMap { ref =>
        Critiques.find(rule.pairs, ref) match
          case None               => Left(s"not material ${ref.wire}: not a pair of the critique checklist")
          case Some(p) if !p.open => Left(s"not material ${p.ref.wire}: the pair is already ${p.state.wire}")
          case Some(p) if named.exists(_.same(p.ref)) =>
            Left(s"not material ${p.ref.wire}: a missing-critique MAJOR names it too (the finding stands)")
          case Some(_) if reason.isEmpty                          => Left(s"not material ${ref.wire}: no reason")
          case Some(p) if reason.length > CritiqueReason.MaxChars =>
            Left(
              s"not material ${p.ref.wire}: the reason is ${reason.length} characters (at most ${CritiqueReason.MaxChars})"
            )
          case Some(p) => Right(CritiqueReason(p.ref.src, p.ref.dst, reason))
      }
    }
    val ok = results.collect { case Right(c) => c }.distinctBy(c => (c.src, c.dst))
    val ruled = fs.filter(isMissingCritique).flatMap(_.pair) ++ ok.map(_.ref)
    val unruled = rule.shown.filter(p => p.open && !ruled.exists(_.same(p.ref))).map(_.ref.wire)
    val missing = Option
      .when(unruled.nonEmpty)(
        s"open pairs of the critique checklist without a ruling: ${unruled.mkString(", ")} — for each, a missing-critique " +
          "MAJOR naming it in `pair` (it bears on a claim the article makes), or a `notMaterial` entry with the reason"
      )
      .toList
    val dropped = results.collect { case Left(m) => s"skeptic: dropped — $m" }
    if last then (ok, Nil, dropped ++ missing.map(m => s"skeptic: $m (left open)"))
    else (ok, missing, dropped)

  // ── checks of earlier findings, and the verification pass (the review ledger) ──────────────────

  /** What a reviewer's checks are verified against: its earlier findings it was asked about (open in the ledger), and
    * the article as it is now.
    */
  final case class Prior(asked: List[Finding], body: String, minRatio: Double)

  /** A full panel's checks: only when the ledger listed findings of this reviewer; otherwise ignored. */
  private def checksOf(
      r: Reviewer,
      prior: Option[Prior],
      as: List[CheckAnswer],
      last: Boolean
  ): (List[Check], List[String], List[String]) =
    prior.filter(_.asked.nonEmpty).fold((Nil, Nil, Nil))(p => checkChecks(r, p, as, last))

  /** Verify a reviewer's checks of its earlier findings: one per finding asked about (a missing one leaves it open),
    * each id one of them, a note when it is not fixed, and each quote IN the current body — the fixed text, or the
    * problem still there. An empty quote passes only when there is nothing left to quote: the finding quoted nothing,
    * or its passage is gone. Returns (verified checks, problems to send back, warnings); on the last attempt the
    * problems become warnings (what failed stays open).
    */
  def checkChecks(
      r: Reviewer,
      prior: Prior,
      as: List[CheckAnswer],
      last: Boolean
  ): (List[Check], List[String], List[String]) =
    val byId = prior.asked.map(f => f.id -> f).toMap
    def gone(f: Finding) = f.quote.trim.isEmpty || !AgentVerify.findQuote(f.quote, prior.body, prior.minRatio).accepted
    val results = Option(as).getOrElse(Nil).map { c =>
      val id = Option(c.id).map(_.trim).getOrElse("")
      val q = Option(c.quote).map(_.trim).getOrElse("")
      val note = Option(c.note).map(_.trim).getOrElse("")
      val checked: Either[String, Check] = byId.get(id) match
        case None if id.isEmpty =>
          Left(s"a check with no id — give the id of the finding it checks (${byId.keys.toList.sorted.mkString(", ")})")
        case None =>
          Left(
            s"check of '$id': not one of the findings you were asked to check (${byId.keys.toList.sorted.mkString(", ")})"
          )
        case Some(_) if q.nonEmpty =>
          if AgentVerify.findQuote(q, prior.body, prior.minRatio).accepted then Right(Check(id, c.fixed, q, note))
          else Left(s"check of $id: the quote is not in the current article — copy it exactly: \"${q.take(80)}\"")
        case Some(f) if gone(f) => Right(Check(id, c.fixed, "", note))
        case Some(_)            =>
          Left(s"check of $id: no quote — quote the current text that shows it ${
              if c.fixed then "fixed" else "still there"
            }")
      checked match
        case Right(k) if !k.fixed && k.note.isEmpty =>
          Left(s"check of $id: no note — say why it is not fixed (the fixer reads it)")
        case other => other
    }
    val checks = results
      .collect { case Right(c) => c }
      .foldLeft(Vector.empty[Check])((acc, c) => if acc.exists(_.id == c.id) then acc else acc :+ c)
      .toList
    val missing =
      prior.asked.map(_.id).filterNot(checks.map(_.id).toSet).map(id => s"$id: no check — say whether it is fixed")
    val problems = results.collect { case Left(p) => p } ++ missing
    if last then (checks, Nil, problems.map(p => s"${r.wire}: $p (left open)"))
    else (checks, problems, Nil)

  /** A reviewer's word that a missing-critique finding is fixed, held to what code can see (§6.9): while its pair still
    * bears on the article — open, and the criticised work still cited — neither cited in one paragraph nor given a
    * reason, the check is recorded NOT fixed, with that as the note (a critique described without the work it answers
    * was "fixed" and raised again, run after run). `asked`: the findings the checks are of. Returns the checks and a
    * warning per one held.
    */
  def holdPairChecks(
      checks: List[Check],
      asked: List[Finding],
      pairs: List[CritiquePair]
  ): (List[Check], List[String]) =
    val held = checks.map { c =>
      asked.find(_.id == c.id).flatMap(_.pair).flatMap(Critiques.find(pairs, _)) match
        case Some(p) if c.fixed && p.bearing =>
          val why =
            s"[[${p.src.citekey}]] and [[${p.dst.citekey}]] are still not cited in one paragraph, and no reason is stored"
          (
            c.copy(
              fixed = false,
              note =
                s"$why (code's check)${Option(c.note).filter(_.trim.nonEmpty).fold("")(n => s"; the reviewer: $n")}"
                  .take(300)
            ),
            Some(s"${c.id}: checked as fixed, but $why — recorded not fixed")
          )
        case _ => (c, None)
    }
    (held.map(_._1), held.flatMap(_._2))

  /** Is `f`, raised by the run's final pass, a regression of this run rather than a discovery (§6.9)? A missing
    * critique whose pair was answered (addressed or reasoned) in the run's first snapshot (`start`) and is open now
    * (`now`); or a factual error ([[FactualKinds]], or a bridge claim, `synth`) whose quote lies in the text the run
    * changed (`edited`: the passages that differ from the first snapshot's body).
    */
  def finalRegression(
      f: Finding,
      start: List[CritiquePair],
      now: List[CritiquePair],
      edited: String,
      minRatio: Double
  ): Boolean =
    val pairBack = isMissingCritique(f) && f.pair.exists(ref =>
      Critiques.find(start, ref).exists(!_.open) && Critiques.find(now, ref).forall(_.open)
    )
    val kind = f.kind.trim.toLowerCase(java.util.Locale.ROOT)
    val wrote = (FactualKinds(kind) || kind == "synth") && f.quote.trim.nonEmpty && edited.trim.nonEmpty &&
      AgentVerify.findQuote(f.quote, edited, minRatio).accepted
    pairBack || wrote

  /** The kinds of finding that are factual errors: the only MAJORs a verification pass may raise outside the edited
    * passages, and only with evidence code verified.
    */
  val FactualKinds: Set[String] =
    Set("contradictory", "extrapolatory", "decorative", "recall", "date", "status", "settled-but-contested", "number")

  /** At most this many lessons per answer, each at most this long. */
  val MaxLessonsPerAnswer = 3
  val LessonChars = 200

  /** A verification pass's finding, scoped to the edited passages: inside them anything stands; outside them a MAJOR
    * stands only as a factual error with evidence, else it is recorded as minor (with the warning saying so).
    */
  def scope(f: Finding, edited: String, minRatio: Double): (Finding, Option[String]) =
    val inside = f.quote.trim.nonEmpty && AgentVerify.findQuote(f.quote, edited, minRatio).accepted
    if !f.major || inside || (FactualKinds(f.kind.trim.toLowerCase(java.util.Locale.ROOT)) && f.evidence.nonEmpty) then
      (f, None)
    else
      (
        f.copy(severity = Severity.Minor),
        Some(
          s"${f.reviewer.wire}: \"${f.problem.take(80)}\" is outside the edited passages and not a factual error with evidence — recorded as minor"
        )
      )

  /** Verify a verification pass's answer: the checks ([[checkChecks]]), the new findings ([[checkFindings]], then
    * [[scope]]d to `edited`, the text of the edited passages) and the lessons.
    */
  def checkRecheck(
      r: Reviewer,
      a: RecheckAnswer,
      prior: Prior,
      edited: String,
      ev: EvidenceCheck,
      last: Boolean,
      critiques: List[CritiquePair] = Nil
  ): Either[List[String], (Recheck, List[String])] =
    val (cs, cps, cws) = checkChecks(r, prior, a.checks, last)
    val (raw, ps, ds0) = checkFindings(r, Option(a.findings).getOrElse(Nil), ev)
    // a re-check is shown no checklist: a missing-critique MAJOR on no open pair is recorded minor
    val (fs, _, kws) =
      checkCritiques(r, raw, CritiqueRule(critiques, Nil, full = false, prior.body, prior.minRatio), Nil, last)
    val ds = ds0 ++ kws
    val scoped = fs.map(scope(_, edited, prior.minRatio))
    val lessons = Option(a.lessons)
      .getOrElse(Nil)
      .map(_.trim)
      .filter(_.nonEmpty)
      .distinct
      .take(MaxLessonsPerAnswer)
      .map(_.take(LessonChars))
    val problems = cps ++ ps
    if problems.nonEmpty && !last then Left(problems)
    else
      Right(
        (
          Recheck(r, cs, scoped.map(_._1), lessons),
          cws ++ ps.map(p => s"${r.wire}: dropped — $p") ++ ds ++ scoped.flatMap(_._2)
        )
      )

  // ── merge and gate (§4.3 steps 4–5) ─────────────────────────────────────────────────────────────

  private def key(f: Finding): (String, String) = (AgentVerify.normalise(f.where), AgentVerify.normalise(f.problem))

  /** Did a reviewer raise `f` before? Same reviewer, and the same quoted passage — or the same `where` + `problem` when
    * there is no quote (a model rewords its problem, rarely its quote).
    */
  def sameFinding(a: Finding, b: Finding): Boolean =
    a.reviewer == b.reviewer && {
      val (qa, qb) = (AgentVerify.normalise(a.quote), AgentVerify.normalise(b.quote))
      if qa.nonEmpty && qb.nonEmpty then qa == qb else key(a) == key(b)
    }

  /** One findings list for the round: mechanical first, then rules, cold reader, skeptic; duplicates (the same `where`
    * + `problem`, whoever raised it) dropped; a cold-reader and a skeptic finding on the same passage both KEPT (the
    * fixer must satisfy both). A finding the fixer rejected in an earlier round and a reviewer raises again is flagged
    * `escalated`. A dropped duplicate's evidence joins the finding that is kept. The verdict is the worst of every
    * report and every kept finding.
    */
  def merge(
      round: Int,
      mechanicalFindings: List[Finding],
      reports: List[Report],
      rejectedBefore: List[Finding]
  ): Merged =
    val all = mechanicalFindings ++ reports.sortBy(_.reviewer.ordinal).flatMap(_.findings)
    // a duplicate is dropped, but the records it cites join the finding that is kept
    val kept = all
      .foldLeft(Vector.empty[Finding]) { (acc, f) =>
        acc.indexWhere(g => key(g) == key(f)) match
          case -1 => acc :+ f
          case i  => acc.updated(i, acc(i).copy(evidence = mergeEvidence(acc(i).evidence, f.evidence)))
      }
      .toList
      .map(f => if rejectedBefore.exists(sameFinding(_, f)) then f.copy(escalated = true) else f)
    val verdict = Verdict.worst(reports.map(_.verdict) :+ Verdict.of(kept))
    val cold = reports.find(_.reviewer == Reviewer.ColdReader)
    Merged(
      round,
      verdict,
      kept,
      cold.flatMap(_.explainBack),
      cold.toList.flatMap(_.emptySlots),
      reports.flatMap(_.proposals).distinct
    )

  private def mergeEvidence(a: List[Evidence], b: List[Evidence]): List[Evidence] =
    (a ++ b)
      .foldLeft(Vector.empty[Evidence]) { (acc, e) =>
        if acc.exists(x => x.citekey == e.citekey && AgentVerify.normalise(x.quote) == AgentVerify.normalise(e.quote))
        then acc
        else acc :+ e
      }
      .take(MaxEvidence)
      .toList

  /** After a round: stop when nothing major is open, when no fixing was asked for, or when the rounds are used up.
    */
  def done(m: Merged, round: Int, rounds: Int, fix: Boolean): Boolean =
    m.majors.isEmpty || !fix || round >= rounds

  /** The panel rounds a run gets: what it asked for, else the configured default, never more than the cap. The fixer
    * works BETWEEN rounds, so a run that fixes gets at least two (one to find, one to check the fix).
    */
  def rounds(requested: Option[Int], default: Int, cap: Int, fix: Boolean): Int =
    val floor = if fix then 2 else 1
    math.max(floor, math.min(math.max(floor, cap), requested.getOrElse(default)))

  // ── the fixer (§4.3 step 6) ─────────────────────────────────────────────────────────────────────

  def words(s: String): Int = s.split("\\s+").count(_.nonEmpty)

  /** The fixer's turns: its agent's `max-turns`, plus `perFinding` for every finding that may need a lookup — each
    * MAJOR, and each minor of the rules reviewer or the skeptic (a factual one) — never more than `cap` (and never
    * fewer than `base`). Eighteen findings in twelve turns ran out.
    */
  def fixerTurns(base: Int, m: Merged, perFinding: Int, cap: Int): Int =
    val b = math.max(1, base)
    val n = m.findings.count(f => f.major || f.reviewer == Reviewer.Rules || f.reviewer == Reviewer.Skeptic)
    math.max(b, math.min(cap, b + math.max(0, perFinding) * n))

  /** What a body check needs from the corpus: does `[[x]]` name a source, does it resolve at all (a source, a short
    * form, an article), and the formatting ERRORS of a text (one line each, with no position, so the lines of two texts
    * compare).
    */
  final case class BodyCorpus(
      isCitekey: String => Boolean,
      isTarget: String => Boolean,
      formatErrors: String => List[String],
      generated: Boolean = false
  )

  /** THE check of a draft — the fixer's edits against the body the round reviewed, or a whole body — shared by the
    * pipeline's validation of the fixer's answer and the `check_edits` tool, so the two can never disagree. Edits apply
    * in order ([[ArticleEdits.applyAll]]); an edit whose text links to nothing is never applied. The result's problems:
    * each edit that did not apply; links to nothing; a hand-written Sources cited heading; up to three uncited
    * paragraphs; banned filler; and formatting errors the draft introduced (ones already in `before` are not the
    * draft's). FATAL (never written, even on a last attempt): an empty body, or a link problem `before` did not have.
    */
  def checkBody(
      slug: String,
      before: String,
      kind: String,
      draft: ArticleEdits.Draft,
      corpus: BodyCorpus
  ): ArticleEdits.ArticleCheck =
    def links(t: String) = AgentVerify.linkProblems(t, corpus.isCitekey, corpus.isTarget)
    val applied = draft match
      case ArticleEdits.Draft.Edits(es) =>
        ArticleEdits.applyAll(
          before,
          es,
          a => a.text.flatMap(t => links(t).headOption).map(p => s"its text is refused: $p")
        )
      case ArticleEdits.Draft.Body(t) => ArticleEdits.Applied(t.trim, Nil)
    val body = applied.body
    val bodyProblems =
      if body.trim.isEmpty then List("the body is empty")
      else
        val again = mechanical(slug, kind, body, corpus.isTarget, corpus.generated)
        val formatBefore = corpus.formatErrors(before).toSet
        links(body) ++
          again.filter(f => f.kind == "section" && f.problem.contains("Sources cited")).map(_.problem) ++
          again.filter(_.kind == CoverageKind).map(_.problem) ++
          again
            .filter(f => f.kind == "citation" && f.problem.contains("no [[citekey]]"))
            .take(3)
            .map(f => s"still ${f.problem}: \"${f.quote.take(80)}\"") ++
          again.filter(_.kind == "language").map(_.problem) ++
          corpus.formatErrors(body).filterNot(formatBefore).map(e => s"formatting: $e")
    val fatal = body.trim.isEmpty || links(body).toSet.diff(links(before).toSet).nonEmpty
    ArticleEdits.ArticleCheck(
      slug,
      kind,
      applied.results,
      applied.problems ++ bodyProblems,
      fatal,
      words(before),
      words(body),
      body
    )

  /** What code checks a revision against beyond the body (§6.9), all computed by the pipeline: `pairsOf` — the critique
    * checklist of a body; `regressions` — the closed ledger findings a revision (`before`, `after`) would reopen
    * ([[ReviewLedger.guard]]); `repair` — the mechanical pairing repair of a body ([[CritiquePairing.repair]]).
    */
  final case class FixGuards(
      pairsOf: String => List[CritiquePair],
      regressions: (String, String) => List[ReviewLedger.Regression],
      repair: String => (String, List[CritiquePairing.Hit])
  )

  /** One evaluation of a list of edits: the checked body, then the pairing repair of it, the checklist of the result
    * and the closed findings it would reopen.
    */
  final private case class Revision(
      check: ArticleEdits.ArticleCheck,
      body: String,
      hits: List[CritiquePairing.Hit],
      pairs: List[CritiquePair],
      regressions: List[ReviewLedger.Regression]
  )

  /** Verify a revision: its edits applied to the body the round reviewed and the result checked ([[checkBody]]); every
    * MAJOR finding either applied or rejected; no mechanical or cold-reader finding rejected (a reader's confusion is a
    * fact; a mechanical hit is certain); and every rejection backed by a quote that IS in the record of the source it
    * names — "a rejection you cannot quote is not a rejection". On the last attempt the edits that applied are kept if
    * the result is not fatal; the others, and every remaining problem, are reported ("kept despite"), and rejections
    * that cannot be verified are listed as `unverified` (still open).
    *
    * With `guards` (§6.9), also: the mechanical pairing repair is applied to the revised body (the findings whose pair
    * it answers are `repaired`); a missing-critique finding listed as applied must leave its pair answered (both cited
    * in one paragraph, or set aside); the revision must not bring in a citation whose recorded critiques it discusses
    * nowhere (cite the critic beside it, or set the pair aside with no finding id); and it must not reopen a closed
    * finding — on the last attempt the edits that reopen one are dropped. Every open finding the fixer neither applied,
    * rejected, declined nor set aside is listed in `notFixed`, with why.
    */
  def checkFix(
      a: FixerAnswer,
      m: Merged,
      slug: String,
      before: String,
      kind: String,
      corpus: BodyCorpus,
      isCitekey: String => Boolean,
      text: String => Option[String],
      minRatio: Double,
      last: Boolean,
      coverage: Option[Coverage] = None,
      critiques: List[CritiquePair] = Nil,
      guards: Option[FixGuards] = None
  ): Either[List[String], (Fix, List[String])] =
    val ids = m.findings.map(_.id).toSet
    val byId = m.findings.map(f => f.id -> f).toMap
    val applied0 = a.applied.map(_.trim).filter(_.nonEmpty).distinct
    val unknown = (applied0 ++ a.rejected.map(_.id.trim)).filterNot(ids).distinct.map(i => s"there is no finding '$i'")
    val rejections = a.rejected.map { r =>
      val id = r.id.trim
      val ck = r.citekey.trim
      byId.get(id) match
        case None                                         => Left(id -> s"rejection of '$id': no such finding")
        case Some(f) if f.reviewer == Reviewer.Mechanical =>
          Left(id -> s"$id is a mechanical check; it cannot be rejected, only fixed")
        case Some(f) if f.reviewer == Reviewer.ColdReader =>
          Left(id -> s"$id is a reader's confusion; it cannot be rejected — define or restructure instead")
        case Some(_) if Option(r.reason).forall(_.trim.isEmpty) =>
          Left(id -> s"rejection of $id gives no reason — say why the finding is wrong")
        case Some(_) if !isCitekey(ck) => Left(id -> s"rejection of $id names '$ck', which is not a citekey")
        case Some(_)                   =>
          val q = Option(r.quote).map(_.trim).getOrElse("")
          val hit = text(ck).map(t => AgentVerify.findQuote(q, t, minRatio))
          hit.filter(_.accepted) match
            case Some(qm) => Right(Rejection(id, r.reason.trim, ck, q, qm.ratio))
            case None     =>
              Left(
                id -> s"rejection of $id: the quote is not in the record of $ck — quote it exactly or apply the finding"
              )
    }
    val rejectedOk = rejections.collect { case Right(r) => r }
    val rejectedBad = rejections.collect { case Left(v) => v }
    val wont = Option(a.wontFix).getOrElse(Nil).map { w =>
      val id = Option(w.id).map(_.trim).getOrElse("")
      byId.get(id) match
        case None if id.isEmpty => Left("a won't-fix with no id — give the id of the minor finding you decline")
        case None               => Left(s"won't fix '$id': there is no such finding")
        case Some(f) if f.major && !needsFilter(f, coverage) =>
          Left(s"$id is MAJOR: apply it or reject it — only a minor finding can be declined")
        case Some(_) if Option(w.reason).forall(_.trim.isEmpty) => Left(s"won't fix $id: give the reason")
        case Some(_)                                            => Right(WontFix(id, w.reason.trim))
    }
    val edits = Option(a.edits).getOrElse(Nil)

    def evaluate(es: List[ArticleEdits.EditAnswer]): Revision =
      val check = checkBody(slug, before, kind, ArticleEdits.Draft.Edits(es), corpus)
      guards match
        case Some(g) if !check.fatal =>
          val (body, hits) = g.repair(check.body)
          Revision(check, body, hits, g.pairsOf(body), g.regressions(before, body))
        case _ => Revision(check, check.body, Nil, critiques, Nil)

    val first = evaluate(edits)
    // the pairs a set-aside may name: the checklist the round reviewed, and the revision's
    val (asides, pairsAside, asideProblems) =
      checkSetAside(Option(a.setAside).getOrElse(Nil), byId, critiques ++ first.pairs)
    val aside = asides.map(_.pair) ++ pairsAside.map(_.ref)
    def answered(r: CritiqueRef, rev: Revision) =
      aside.exists(_.same(r)) || Critiques.find(rev.pairs, r).forall(!_.bearing)

    // on the last attempt, the edits that reopen a closed finding are dropped, one at a time from the last
    val rev =
      if !last || first.regressions.isEmpty || guards.isEmpty then first
      else
        first.check.edits.filter(_.applied).map(_.index).reverse.foldLeft(first) { (cur, i) =>
          if cur.regressions.isEmpty then cur
          else
            val keep = cur.check.edits.filter(e => e.applied && e.index != i).map(_.index).toSet
            val trial = evaluate(edits.zipWithIndex.collect { case (e, j) if keep(j + 1) => e })
            if trial.regressions.size < cur.regressions.size && !trial.check.fatal then trial else cur
        }
    val dropped = first.check.edits.filter(_.applied).map(_.index).diff(rev.check.edits.filter(_.applied).map(_.index))
    val droppedNote = Option.when(dropped.nonEmpty)(
      s"edit(s) ${dropped.mkString(", ")} dropped: they reopened ${first.regressions.map(_.id).diff(rev.regressions.map(_.id)).mkString(", ")}"
    )

    // a missing critique listed as applied must leave its pair answered
    val pairUnanswered = applied0
      .flatMap(byId.get)
      .filter(f => guards.isDefined && isMissingCritique(f))
      .flatMap(f => f.pair.filterNot(answered(_, rev)).map(r => f.id -> r))
    def unansweredText(r: CritiqueRef) = s"[[${r.src}]] and [[${r.dst}]] are not cited in one paragraph"
    val pairProblems = pairUnanswered.map((id, r) =>
      s"$id is listed as applied, but ${unansweredText(r)} — cite both where the critique is discussed, or set it aside " +
        "(`setAside`) with a reason"
    )
    val applied = if last then applied0.filterNot(id => pairUnanswered.exists(_._1 == id)) else applied0
    // pairs the revision brought in: a work it now cites whose recorded critique it discusses nowhere
    val brought = rev.pairs
      .filter(p => p.bearing && !critiques.exists(_.ref.same(p.ref)) && !aside.exists(_.same(p.ref)))
      .foldLeft(Vector.empty[CritiquePair])((acc, p) => if acc.exists(_.ref.same(p.ref)) then acc else acc :+ p)
      .toList
    val broughtProblem = Option.when(brought.nonEmpty)(
      s"your edits cite ${brought.map(p => s"[[${p.criticised.citekey}]]").distinct.mkString(", ")}, whose recorded critiques " +
        s"no paragraph discusses: ${brought.map(p => s"${p.ref.wire} (${p.kinds.mkString("/")})").mkString(", ")} — for each, " +
        "cite the critic in the paragraph that cites the criticised work, or set the pair aside (`setAside` with its `pair`, " +
        "no `id`) with a reason"
    )
    val regressionProblems =
      rev.regressions.map(r => s"your edits reopen a closed finding — ${r.line}: keep what fixed it")

    // repaired: findings whose pair the pairing repair answered (not those the fixer claims)
    val repaired = m.findings
      .filter(f => isMissingCritique(f) && !applied.contains(f.id))
      .filter(f => f.pair.exists(r => rev.hits.exists(_.ref.same(r)) && answered(r, rev)))
      .map(_.id)

    val decided = applied.toSet ++ rejectedOk.map(_.id) ++ rejectedBad.map(_._1) ++ asides.map(_.id) ++ repaired
    val declinedOk = wont.collect { case Right(w) => w.id }.toSet
    val undecided =
      m.majors.filterNot(f => decided(f.id) || declinedOk(f.id) || pairUnanswered.exists(_._1 == f.id)).map { f =>
        if needsFilter(f, coverage) then
          s"major finding ${f.id} is neither applied, rejected nor declined — a coverage finding on an article that records " +
            "no filter needs the filter recorded, which no edit does: put it in wontFix with that reason (it stays open for a person)"
        else s"major finding ${f.id} is neither applied nor rejected"
      }
    val check = rev.check
    val stated = Option(a.reasons).getOrElse(Nil)
    val (reasons, reasonProblems) = checkReasons(stated, coverage, rev.body, corpus.isCitekey, m, applied.toSet)
    val noEdit = Option.when(
      applied.exists(ids) && !check.edits.exists(_.applied) && reasons.isEmpty && asides.isEmpty &&
        rev.hits.isEmpty
    )(s"you list ${applied.mkString(", ")} as applied, but no edit changed the article")
    val problems = check.problems ++ reasonProblems ++ noEdit.toList ++ unknown ++ undecided ++ rejectedBad.map(_._2) ++
      wont.collect { case Left(p) => p } ++ asideProblems ++ pairProblems ++ broughtProblem.toList ++ regressionProblems
    if problems.isEmpty || (last && !check.fatal) then
      val wontOk = wont.collect { case Right(w) => w }.filterNot(w => applied.contains(w.id)).distinctBy(_.id)
      val setAside = asides.filterNot(s => applied.contains(s.id) || rejectedOk.exists(_.id == s.id))
      // every open finding left unanswered, with why — the ledger records it as not fixed
      val answeredIds = applied.toSet ++ rejectedOk.map(_.id) ++ wontOk.map(_.id) ++ setAside.map(_.id) ++ repaired
      val notFixed = m.findings.filterNot(f => answeredIds(f.id) || m.deferred.contains(f.id)).map { f =>
        val why = pairUnanswered
          .find(_._1 == f.id)
          .map((_, r) => s"listed as applied, but ${unansweredText(r)}")
          .orElse(rejectedBad.find(_._1 == f.id).map(_ => "its rejection's quote is not in the record it names"))
          .getOrElse(if f.major then "the fixer neither applied nor rejected it" else "the fixer did not answer it")
        WontFix(f.id, why)
      }
      val fix = Fix(
        rev.body,
        check.edits,
        applied.filter(ids),
        rejectedOk,
        rejectedBad.map(_._1).filter(ids).distinct,
        a.unresolvedMoved.map(_.trim).filter(_.nonEmpty),
        Option(a.notes).map(_.trim).getOrElse(""),
        check.wordsBefore,
        words(rev.body),
        reasons,
        wontOk,
        setAside,
        repaired,
        rev.hits.map(_.line),
        notFixed,
        pairsAside
      )
      Right((fix, (problems.map(p => s"fixer: kept despite — $p") ++ droppedNote.map(n => s"fixer: $n").toList)))
    else Left(problems)

  /** The fixer's set-asides (§6.8): each names a missing-critique finding, the pair (its own, or the finding's), which
    * must be a pair of the article's critique checklist, and a one-line reason. An entry with NO id and a `pair` sets
    * aside a pair of the checklist that no finding names (one the revision brought in, §6.9). Returns the verified
    * finding set-asides, the pair-only reasons and the problems.
    */
  def checkSetAside(
      as: List[SetAsideAnswer],
      byId: Map[String, Finding],
      critiques: List[CritiquePair]
  ): (List[SetAside], List[CritiqueReason], List[String]) =
    def reasonOf(a: SetAsideAnswer) = Option(a.reason).map(_.trim.replaceAll("\\s+", " ")).getOrElse("")
    def held(label: String, r: CritiqueRef, reason: String): Either[String, CritiquePair] =
      Critiques.find(critiques, r) match
        case None => Left(s"$label: ${r.wire} is not a pair of the article's critique checklist (no recorded edge)")
        case Some(_) if reason.isEmpty                          => Left(s"$label: give the reason")
        case Some(_) if reason.length > CritiqueReason.MaxChars =>
          Left(s"$label: the reason is ${reason.length} characters (at most ${CritiqueReason.MaxChars})")
        case Some(p) => Right(p)
    val results = as.map { a =>
      val id = Option(a.id).map(_.trim).getOrElse("")
      val reason = reasonOf(a)
      val named = Option(a.pair).flatten.map(_.trim).filter(_.nonEmpty).map(CritiqueRef.parse)
      if id.isEmpty then
        named match
          case None => Left("a set-aside with neither an id nor a pair — give the finding's id, or the pair (src>dst)")
          case Some(Left(e))  => Left(s"set aside: $e")
          case Some(Right(r)) =>
            held(s"set aside ${r.wire}", r, reason).map(p => Right(CritiqueReason(p.ref.src, p.ref.dst, reason)))
      else
        byId.get(id) match
          case None                             => Left(s"set aside '$id': there is no such finding")
          case Some(f) if !isMissingCritique(f) =>
            Left(s"set aside $id: only a missing-critique finding can be set aside")
          case Some(f) =>
            val ref = named match
              case Some(r) => r
              case None    => f.pair.toRight(s"set aside $id: the finding names no pair — give it in `pair` (src>dst)")
            ref
              .flatMap(r =>
                held(s"set aside $id", r, reason).left
                  .map(m => if m.contains("no recorded edge") then s"$m — apply or reject the finding" else m)
              )
              .map(p => Left(SetAside(id, p.ref, reason)))
    }
    val ok = results.collect { case Right(v) => v }
    (
      ok.collect { case Left(s) => s }.distinctBy(_.id),
      ok.collect { case Right(r) => r }.distinctBy(r => (r.src, r.dst)),
      results.collect { case Left(p) => p }
    )

  /** A MAJOR the fixer may decline: a coverage finding on an article that records no filter. Its fix is recording the
    * filter (`histmersion article patch <slug> --filter-…`), which no edit and no fixer tool does; declined, it stays
    * open in the ledger for a person. Before this a Codex fixer left such findings out (sent back) and then listed them
    * as applied without an edit (2026-10-16).
    */
  def needsFilter(f: Finding, coverage: Option[Coverage]): Boolean =
    f.major && coverage.isEmpty && f.kind.trim.equalsIgnoreCase("coverage")

  /** The fixer's coverage reasons (#958) against the coverage the round reviewed: each names a source the filter
    * returns that the revised body leaves out (an empty one may remove any stored reason); an unplaced-sources finding
    * listed as applied leaves none unplaced. Returns the reasons that pass (written on the last attempt) and the
    * problems.
    */
  def checkReasons(
      stated: List[CoverageReason],
      coverage: Option[Coverage],
      body: String,
      isCitekey: String => Boolean,
      m: Merged,
      applied: Set[String]
  ): (List[CoverageReason], List[String]) =
    coverage match
      case None =>
        (
          Nil,
          Option
            .when(stated.nonEmpty)(
              "the article records no filter, so it has no generated Not used list: " +
                "leave `reasons` empty"
            )
            .toList
        )
      case Some(c) =>
        val citedAfter = AgentVerify.wikilinks(ArticleCoverage.withoutNotUsed(body)).filter(isCitekey).toSet
        val inFilter = (c.notUsed.map(_.source.citekey) ++ c.cited.map(_.citekey)).toSet
        val stored = c.reasons.map(r => r.citekey -> r.reason).toMap
        val checked = stated.map { r =>
          val ck = Option(r.citekey).map(_.trim.stripPrefix("[[").stripSuffix("]]").trim.toLowerCase).getOrElse("")
          val removes = Option(r.reason).forall(_.trim.isEmpty)
          if removes && stored.contains(ck) then Right(r.copy(citekey = ck))
          else if removes then Left(s"reason for [[$ck]]: empty, and no reason is stored for it")
          else if !inFilter(ck) then
            Left(s"reason for [[$ck]]: the filter does not return it — give reasons only for its sources")
          else if citedAfter(ck) then
            Left(s"reason for [[$ck]]: the article cites it — a reason is only for a source it leaves out")
          else CoverageReason.merge(Map.empty, List(r.copy(citekey = ck))).map(_ => r.copy(citekey = ck))
        }
        val ok = checked.collect { case Right(r) => r }
        val merged = CoverageReason.merge(stored, ok).getOrElse(stored)
        val unplacedIds = m.findings
          .filter(f =>
            f.reviewer == Reviewer.Mechanical && f.kind == CoverageKind &&
              f.major && f.problem.contains("neither cited nor given a reason")
          )
          .map(_.id)
        val left = ArticleCoverage.unplacedAfter(c, citedAfter, merged)
        val still = Option.when(unplacedIds.exists(applied) && left.nonEmpty)(
          s"${unplacedIds.filter(applied).mkString(", ")}: still neither cited nor given a reason: " +
            left.take(20).map(ck => s"[[$ck]]").mkString(", ") + (if left.size > 20 then s" (+${left.size - 20} more)"
                                                                  else "")
        )
        (ok, checked.collect { case Left(p) => p } ++ still.toList)

  // ── what the reviewers and the fixer are sent ───────────────────────────────────────────────────

  /** One evidence record, as the fixer reads it. */
  def evidenceLine(e: Evidence): String =
    val at = e.offset.fold("")(o => s" @$o")
    s"[[${e.citekey}]]$at: \"${e.quote.replace("\n", " ").take(400)}\""

  /** The findings, as the fixer reads them — each with the records the reviewer found, verified. */
  def findingsText(m: Merged, notes: Map[String, String] = Map.empty): String =
    m.findings
      .map { f =>
        val esc = if f.escalated then " [raised again after a rejection]" else ""
        s"- ${f.id} [${f.reviewer.wire}, ${f.severity.wire}${if f.kind.nonEmpty then s", ${f.kind}" else ""}]$esc " +
          s"where: ${f.where}\n  quote: ${f.quote}\n  problem: ${f.problem}\n  fix: ${f.fix}" +
          f.pair.fold("")(p => s"\n  pair: ${p.wire}") +
          f.evidence.map(e => s"\n  evidence: ${evidenceLine(e)}").mkString +
          notes.get(f.id).fold("")(n => s"\n  note: $n")
      }
      .mkString("\n")

  // ── bookkeeping (§4.3 step 8) ──────────────────────────────────────────────────────────────────

  /** The worklog line, in the form the review-article skill writes. */
  def logLine(o: Outcome, last: Merged, runId: String): String =
    val counts = List(Reviewer.Rules, Reviewer.ColdReader, Reviewer.Skeptic, Reviewer.Mechanical)
      .map(r => s"${r.prefix}:${last.count(r)}")
      .mkString(" ")
    val fixer = if o.fixed then s", fixer applied ${o.applied} / rejected ${o.rejected}" else ""
    s"review-article [[${o.slug}]]: round ${o.rounds} → ${o.verdict.wire} ($counts)$fixer (agent run ${runId.take(8)})"
