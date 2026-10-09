package com.histmersion.core

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema

import com.histmersion.core.AgentProposals.{Proposal, ProposalAnswer}

/** The `review-article` pipeline's typed answers and its pure logic (docs/research-embedded-agents.md
  * §4.3): the mechanical checks, the validation of each reviewer's answer, the merge of the panel
  * (worst verdict wins, deduplicated, escalation of findings the fixer rejected and a reviewer raised
  * again), the gate, the validation of the fixer's revision (a rejection must quote the record) and
  * the bookkeeping lines. The server runs the steps; everything here is pure and table-tested.
  *
  * JVM-only, like [[EnrichSource]], because quote matching needs `java.text.Normalizer`. */
object ReviewArticle:

  // ── vocabularies ───────────────────────────────────────────────────────────────────────────────

  /** Who raised a finding. The prefix is the finding id's letter (`R3`, `C1`, `S2`, `M1`). */
  enum Reviewer(val wire: String, val prefix: String):
    case Mechanical extends Reviewer("mechanical", "M")
    case Rules      extends Reviewer("rules", "R")
    case ColdReader extends Reviewer("cold-reader", "C")
    case Skeptic    extends Reviewer("skeptic", "S")

  object Reviewer:
    def parse(s: String): Option[Reviewer] = values.find(_.wire == s.trim.toLowerCase)
    given WireEnumValues[Reviewer]:
      def all: Array[Reviewer] = Reviewer.values
      def wire(e: Reviewer): String = e.wire
      def fromWire(s: String): Option[Reviewer] = parse(s)
    given Encoder[Reviewer] = WireEnum.encoder
    given Decoder[Reviewer] = WireEnum.decoder
    given Schema[Reviewer]  = WireEnum.schema

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
    given Schema[Severity]  = WireEnum.schema

  /** An article's (or one reviewer's) verdict, ordered: the worst one wins. */
  enum Verdict(val wire: String, val rank: Int):
    case Pass  extends Verdict("PASS", 0)
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
    given Schema[Verdict]  = WireEnum.schema

  /** The four slots of the cold reader's explain-back (question, answer, disagreement, open). */
  val ExplainSlots: List[String] = List("question", "answer", "disagreement", "open")

  // ── LLM answers (their JSON schemas are what each step is asked to fill) ────────────────────────

  /** The record a finding rests on: a citekey, where in its body (the `offset` get_source slices
    * at, when the reviewer read a slice) and the sentence copied from it. Code checks the quote is in
    * that record before the fixer sees it, so the fixer can act on it without searching again. */
  final case class EvidenceAnswer(citekey: String, offset: Option[Int] = None, quote: String)
      derives KindlingsCodecAsObject
  object EvidenceAnswer:
    given Schema[EvidenceAnswer] = KindlingsSchema.derived[EvidenceAnswer].schema
      .modify(_.citekey)(_.description("The source whose record shows it"))
      .modify(_.offset)(_.description("Where in that source's body the quote is (the get_source offset you read at); null if not from the body"))
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
      evidence: List[EvidenceAnswer] = Nil
  ) derives KindlingsCodecAsObject
  object FindingAnswer:
    given Schema[FindingAnswer] = KindlingsSchema.derived[FindingAnswer].schema
      .modify(_.severity)(_.description("major | minor — by the severity rules of your instructions"))
      .modify(_.kind)(_.description("The rule or kind your instructions name (e.g. synth, decorative, term, missing-critique)"))
      .modify(_.where)(_.description("The section heading, or the first 8 words of the passage"))
      .modify(_.quote)(_.description("The article's words the finding is about, copied exactly"))
      .modify(_.problem)(_.description("What is wrong, in one or two sentences"))
      .modify(_.fix)(_.description("What would fix it"))
      .modify(_.evidence)(_.description(
        s"The records the finding rests on (at most $MaxEvidence): citekey, offset, and the sentence copied exactly; " +
          "empty only when it rests on the article's own text"))

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
    given Schema[ReaderFindingAnswer] = KindlingsSchema.derived[ReaderFindingAnswer].schema
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
      accuracyChecked: Int,
      checks: List[CheckAnswer] = Nil
  ) derives KindlingsCodecAsObject
  object RulesAnswer:
    given Schema[RulesAnswer] = KindlingsSchema.derived[RulesAnswer].schema
      .modify(_.verdict)(_.description("PASS | MINOR | MAJOR — the worst of your findings"))
      .modify(_.filter)(_.description("The filter you reconstructed and how many sources it returned"))
      .modify(_.uncitedRelevant)(_.description("Citekeys the filter returns that clearly belong and are neither cited nor under Not used"))
      .modify(_.statusesSilent)(_.description("Cited non-current, non-foundational citekeys whose status the article does not state"))
      .modify(_.accuracyChecked)(_.description("How many cited claims you checked against the database"))
      .modify(_.checks)(_.description(ChecksDescription))

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
    given Schema[ColdReaderAnswer] = KindlingsSchema.derived[ColdReaderAnswer].schema
      .modify(_.verdict)(_.description("PASS | MINOR | MAJOR — the worst of your findings"))
      .modify(_.explainBack)(_.description("Five plain sentences: the question, the answer, who disagrees with whom, what is open"))
      .modify(_.emptySlots)(_.description("Slots you could not fill from the text: question | answer | disagreement | open"))
      .modify(_.lostAt)(_.description("The section where you stopped following; null if you never did"))
      .modify(_.checks)(_.description(ChecksDescription))

  final case class SkepticAnswer(
      verdict: String,
      findings: List[FindingAnswer] = Nil,
      proposals: List[ProposalAnswer] = Nil,
      claimsChecked: Int,
      checks: List[CheckAnswer] = Nil
  ) derives KindlingsCodecAsObject
  object SkepticAnswer:
    given Schema[SkepticAnswer] = KindlingsSchema.derived[SkepticAnswer].schema
      .modify(_.verdict)(_.description("PASS | MINOR | MAJOR — the worst of your findings"))
      .modify(_.proposals)(_.description("Database errors you noticed, as changes for a person to approve: a status, or a disagreement edge — each with a quote from the record"))
      .modify(_.claimsChecked)(_.description("How many load-bearing claims you checked"))
      .modify(_.checks)(_.description(ChecksDescription))

  /** A reviewer's verdict on one of its own earlier findings (the review ledger, [[ReviewLedger]]):
    * fixed or not, shown by a quote of the CURRENT article. */
  final case class CheckAnswer(id: String, fixed: Boolean, quote: String, note: String) derives KindlingsCodecAsObject
  object CheckAnswer:
    given Schema[CheckAnswer] = KindlingsSchema.derived[CheckAnswer].schema
      .modify(_.id)(_.description("The ledger id of your earlier finding (R4, C2, S7…)"))
      .modify(_.fixed)(_.description("true when the current article no longer has the problem"))
      .modify(_.quote)(_.description(
        "The current article's words that show it — the fixed text, or the problem still there — copied exactly; " +
          "empty only when the passage was deleted"))
      .modify(_.note)(_.description("One sentence: what changed, or why it is not fixed"))

  /** A verification pass's answer (the targeted re-check after a fix): the checks of the findings the
    * fixer claims to have fixed, the regressions found in the edited passages, and lessons for the fixer. */
  final case class RecheckAnswer(
      checks: List[CheckAnswer] = Nil,
      findings: List[FindingAnswer] = Nil,
      lessons: List[String] = Nil
  ) derives KindlingsCodecAsObject
  object RecheckAnswer:
    given Schema[RecheckAnswer] = KindlingsSchema.derived[RecheckAnswer].schema
      .modify(_.checks)(_.description("One check per finding you were asked to verify"))
      .modify(_.findings)(_.description(
        "New problems in the EDITED passages (a regression the fix made); outside them only a factual error, with evidence"))
      .modify(_.lessons)(_.description(
        "At most three short rules for the fixer, learned from a fix that did not fix or from a regression (each under 25 words)"))

  final case class RejectionAnswer(id: String, reason: String, citekey: String, quote: String)
      derives KindlingsCodecAsObject
  object RejectionAnswer:
    given Schema[RejectionAnswer] = KindlingsSchema.derived[RejectionAnswer].schema
      .modify(_.id)(_.description("The id of the finding you reject (R2, S1…)"))
      .modify(_.citekey)(_.description("The source whose record contradicts the finding"))
      .modify(_.quote)(_.description("The sentence of that record that contradicts it, copied EXACTLY (from get_source)"))

  final case class FixerAnswer(
      edits: List[ArticleEdits.EditAnswer] = Nil,
      applied: List[String] = Nil,
      rejected: List[RejectionAnswer] = Nil,
      unresolvedMoved: List[String] = Nil,
      notes: String,
      reasons: List[CoverageReason] = Nil,
      wontFix: List[WontFixAnswer] = Nil
  ) derives KindlingsCodecAsObject
  object FixerAnswer:
    given Schema[FixerAnswer] = KindlingsSchema.derived[FixerAnswer].schema
      .modify(_.edits)(_.description(
        "The changes to the article, in order, each anchored on text copied EXACTLY from the body — small and " +
          "local, never the whole article; code applies them"))
      .modify(_.applied)(_.description("Ids of the findings you applied"))
      .modify(_.rejected)(_.description("Findings you reject, each with the record's contradicting sentence"))
      .modify(_.unresolvedMoved)(_.description("Claims you moved to the unresolved section"))
      .modify(_.notes)(_.description("What the next review round should know"))
      .modify(_.reasons)(_.description(
        "Why the article leaves out a source its filter returns, one per source (the generated Not used list shows them); " +
          "an empty reason removes one. Only for sources of the filter the article does not cite"))
      .modify(_.wontFix)(_.description("MINOR findings you decline, each with the reason (a MAJOR is applied or rejected)"))

  final case class WontFixAnswer(id: String, reason: String) derives KindlingsCodecAsObject
  object WontFixAnswer:
    given Schema[WontFixAnswer] = KindlingsSchema.derived[WontFixAnswer].schema
      .modify(_.id)(_.description("The id of the minor finding you decline"))
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
      escalated: Boolean = false
  ) derives KindlingsCodecAsObject:
    def major: Boolean = severity == Severity.Major
    /** One line for a person or a queue item. */
    def line: String =
      val at = Option(where).map(_.trim).filter(_.nonEmpty).fold("")(w => s" ($w)")
      s"$id ${severity.wire}$at: ${problem.trim}"

  object Finding:
    given Schema[Finding] = KindlingsSchema.derived[Finding].schema

  /** A verified [[EvidenceAnswer]]: `ratio` is how well the quote matched its record (1.0 = verbatim). */
  final case class Evidence(citekey: String, offset: Option[Int], quote: String, ratio: Double)
      derives KindlingsCodecAsObject
  object Evidence:
    given Schema[Evidence] = KindlingsSchema.derived[Evidence].schema

  /** One reviewer's verified answer. */
  final case class Report(
      reviewer: Reviewer,
      verdict: Verdict,
      findings: List[Finding],
      explainBack: Option[String] = None,
      emptySlots: List[String] = Nil,
      proposals: List[Proposal] = Nil,
      /** Its verdicts on its own earlier findings (the ledger's open ones), each quote found in the body. */
      checks: List[Check] = Nil
  ) derives KindlingsCodecAsObject

  /** A verified [[CheckAnswer]]. */
  final case class Check(id: String, fixed: Boolean, quote: String, note: String) derives KindlingsCodecAsObject

  /** A verification pass's verified answer: one reviewer's checks, the new findings (scoped: a MAJOR
    * outside the edited passages that is not a factual error with evidence is recorded as minor) and
    * its lessons. */
  final case class Recheck(reviewer: Reviewer, checks: List[Check], findings: List[Finding], lessons: List[String] = Nil)
      derives KindlingsCodecAsObject

  /** The article as one round reviews it: its text and the token a patch must present. */
  final case class Snapshot(slug: String, title: String, kind: String, body: String, updatedAt: Option[String],
      truncated: Boolean, coverage: Option[Coverage] = None) derives KindlingsCodecAsObject:
    /** The sections code generates from the filter (#958), as the page shows them after the body. */
    def generated: Option[String] = coverage.map(ArticleCoverage.sections)

  /** The panel's merged result for one round. */
  final case class Merged(round: Int, verdict: Verdict, findings: List[Finding], explainBack: Option[String],
      emptySlots: List[String], proposals: List[Proposal]) derives KindlingsCodecAsObject:
    def majors: List[Finding]   = findings.filter(_.major)
    def escalated: List[Finding] = findings.filter(_.escalated)
    def count(r: Reviewer): Int = findings.count(_.reviewer == r)

  final case class Rejection(id: String, reason: String, citekey: String, quote: String, ratio: Double)
      derives KindlingsCodecAsObject

  /** The fixer's verified revision: the body its edits produced, and every edit with what became of it
    * (applied, or skipped and why) — what a person reads to see exactly what changed. `unverified` =
    * rejections whose quote code could not find in the record: they do not count as rejections (the
    * finding stays open). */
  final case class Fix(body: String, edits: List[ArticleEdits.EditResult], applied: List[String], rejected: List[Rejection],
      unverified: List[String], unresolvedMoved: List[String], notes: String, wordsBefore: Int, wordsAfter: Int,
      /** The coverage reasons the fixer set (an empty one removes), already checked against the coverage. */
      reasons: List[CoverageReason] = Nil,
      /** Minor findings declined, with the reason. */
      wontFix: List[WontFix] = Nil)
      derives KindlingsCodecAsObject

  final case class WontFix(id: String, reason: String) derives KindlingsCodecAsObject

  /** What a round's patch did: the article version that holds the PREVIOUS text (undo from it), or
    * why nothing was written. */
  final case class Patched(written: Boolean, previousVersion: Option[Long], conflict: Boolean)
      derives KindlingsCodecAsObject

  /** The run's result, for its log line and its queue items. */
  final case class Outcome(slug: String, rounds: Int, verdict: Verdict, open: List[Finding], applied: Int,
      rejected: Int, fixed: Boolean, approvals: Int) derives KindlingsCodecAsObject

  // ── mechanical checks (§4.3 step 2) ─────────────────────────────────────────────────────────────

  private val HeadingLine = "^#{1,6}\\s+(.+?)\\s*#*\\s*$".r

  /** The headings of a body, in order, as written. */
  def headings(body: String): List[String] =
    body.linesIterator.collect { case HeadingLine(h) => h.trim }.toList

  private def norm(h: String): String = h.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z ]", "").trim

  /** The sections the guide (rule 4) makes compulsory, as alternatives of heading words. An `index`
    * article (a map of content) has none. `generated`: the article records a filter, so its Not used
    * list is generated (#958) and the body must not have one. */
  def requiredSections(kind: String, generated: Boolean = false): List[List[String]] =
    if kind.trim.equalsIgnoreCase("index") then Nil
    else List(List("disagreements"), List("unresolved", "open questions")) ++
      Option.when(!generated)(List("not used")).toList

  /** The finding kind of the coverage checks (#958): hand-written generated sections, and drift. */
  val CoverageKind = "coverage"

  /** The checks code can make with certainty, as `M<n>` findings: banned filler, a paragraph with no
    * `[[citekey]]`, escaped link brackets, a hand-written Sources cited heading, a missing compulsory
    * section, a duplicated heading, and `[[links]]` that resolve to nothing. With `generated` (the
    * article records a filter, #958) a hand-written Not used section or `_Method_` note is one too:
    * code generates both, so a written one duplicates them and goes stale. */
  def mechanical(slug: String, kind: String, body: String, isTarget: String => Boolean,
      generated: Boolean = false): List[Finding] =
    val hs = headings(body)
    def f(sev: Severity, kind: String, where: String, quote: String, problem: String, fix: String) =
      (sev, kind, where, quote, problem, fix)
    val filler = Lint.articleFiller(List(slug -> body)).map(i =>
      f(Severity.Minor, "language", "body", "", s"banned filler — ${i.detail.stripPrefix("filler: ")}", "rewrite the sentence"))
    // the lint rule's own test, one paragraph at a time (a link to an article is not a citation)
    val uncited = Lint.extractParagraphs(body).filter(p => Lint.articleUncitedParagraphs(List(slug -> p)).nonEmpty)
      .map(p => f(Severity.Major, "citation", p.split("\\s+").take(8).mkString(" "), p.take(200),
        "a paragraph with no [[citekey]]", "cite the source the paragraph rests on, or move it to the unresolved section"))
    val escaped = Option.when(body.contains("\\[\\["))(
      f(Severity.Major, "citation", "body", "\\[\\[", "escaped link brackets render as text and link nothing", "write [[citekey]] unescaped"))
    val sourcesCited = hs.filter(h => norm(h) == "sources cited").take(1).map(h =>
      f(Severity.Major, "section", h, s"## $h", "a hand-written Sources cited section (the page generates it)", "delete the section"))
    val handWritten =
      if !generated then Nil
      else
        hs.filter(h => norm(h).startsWith("not used")).take(1).map(h =>
          f(Severity.Major, CoverageKind, h, s"## $h",
            "a hand-written Not used section: the page generates it from the article's filter and its reasons",
            "delete the section; give each left-out source's reason in `reasons` instead")) ++
        body.linesIterator.map(_.trim).find(_.startsWith("_Method")).toList.map(l =>
          f(Severity.Major, CoverageKind, "Method", l.take(200),
            "a hand-written Method note: the page generates it (the filter, its counts, the statuses, the edges)",
            "delete the note"))
    val missing = requiredSections(kind, generated).filterNot(alts => hs.exists(h => alts.exists(a => norm(h).startsWith(a))))
      .map(alts => f(Severity.Major, "section", "headings", "",
        s"no ${alts.map(a => s"'$a'").mkString(" or ")} section (writing guide rule 4)", "add the section"))
    val dup = hs.groupBy(norm).collect { case (_, xs) if xs.size > 1 => xs.head }.toList.sorted.map(h =>
      f(Severity.Minor, "section", h, s"## $h", "a duplicated heading", "merge the two sections"))
    val dangling = AgentVerify.wikilinks(body).filterNot(isTarget).map(t =>
      f(Severity.Major, "citation", s"[[$t]]", s"[[$t]]", s"[[$t]] is neither a citekey nor an article", "link a real citekey or article"))
    (filler ++ uncited ++ escaped ++ sourcesCited ++ handWritten ++ missing ++ dup ++ dangling).zipWithIndex.map {
      case ((sev, k, w, q, p, fx), i) => Finding(s"M${i + 1}", Reviewer.Mechanical, sev, k, w, q, p, fx)
    }

  /** The coverage drift of an article that records a filter (#958), as findings to follow the
    * [[mechanical]] ones (`M<from>`…): a source of the filter neither cited nor given a reason is
    * MAJOR (the guide: every source ends in Sources cited or Not used, with a reason); a reason for a
    * source the body now cites, or one the filter no longer returns, is minor (it is not shown). */
  def coverageDrift(c: Coverage, from: Int): List[Finding] =
    def keys(cks: List[String]) =
      val shown = cks.take(40).map(ck => s"[[$ck]]").mkString(", ")
      if cks.size > 40 then s"$shown (+${cks.size - 40} more)" else shown
    c.drift.zipWithIndex.map { (d, i) =>
      val (sev, problem, fix) = d.kind match
        case DriftKind.Unplaced => (Severity.Major,
          s"${Plural.count(d.citekeys.size, "source")} the filter returns ${if d.citekeys.size == 1 then "is" else "are"} neither cited nor given a reason: ${keys(d.citekeys)}",
          "cite each one where its record bears on the question, or give its reason in `reasons` (read the record first)")
        case DriftKind.ReasonForCited => (Severity.Minor,
          s"a reason is stored for ${Plural.count(d.citekeys.size, "source")} the article cites: ${keys(d.citekeys)}",
          "remove the reason (an empty one in `reasons`)")
        case DriftKind.ReasonOutside => (Severity.Minor,
          s"a reason is stored for ${Plural.count(d.citekeys.size, "source")} the filter no longer returns: ${keys(d.citekeys)}",
          "remove the reason (an empty one in `reasons`)")
      Finding(s"M${from + i}", Reviewer.Mechanical, sev, CoverageKind, ArticleCoverage.NotUsedHeading, "", problem, fix)
    }

  // ── validating a reviewer's answer ──────────────────────────────────────────────────────────────

  /** How a stated evidence record is checked: a verified [[Evidence]], or why it was dropped. */
  type EvidenceCheck = EvidenceAnswer => Either[String, Evidence]

  /** No evidence is checked (the cold reader has none). */
  val NoEvidence: EvidenceCheck = e => Left(s"[[${e.citekey}]]: this reviewer cites no records")

  /** The check every reviewer's evidence passes: a real citekey, and a quote that IS in its record
    * (the OCR-tolerant matcher the fixer's rejections use). `text` is the record as get_source shows
    * it (summary, findings, notes, body). */
  def evidenceCheck(isCitekey: String => Boolean, text: String => Option[String], minRatio: Double): EvidenceCheck = e =>
    val ck = Option(e.citekey).map(_.trim.stripPrefix("[[").stripSuffix("]]").trim).getOrElse("")
    val q  = Option(e.quote).map(_.trim).getOrElse("")
    if q.isEmpty then Left(s"[[$ck]]: no quote")
    else if !isCitekey(ck) then Left(s"'$ck' is not a citekey")
    else
      text(ck).map(t => AgentVerify.findQuote(q, t, minRatio)).filter(_.accepted) match
        case Some(m) => Right(Evidence(ck, e.offset.filter(_ >= 0), q, m.ratio))
        case None    => Left(s"the quote is not in the record of [[$ck]]: \"${q.take(80)}\"")

  /** Findings as stated → verified findings with code-assigned ids (`R1`…), plus what was dropped. A
    * finding with no problem or a severity outside the vocabulary is a problem to send back. Evidence
    * that does not check out is DROPPED with a warning (the finding stays; the fixer just does not get
    * that record) — it is never a reason to ask the reviewer again. */
  def checkFindings(r: Reviewer, as: List[FindingAnswer], ev: EvidenceCheck = NoEvidence): (List[Finding], List[String], List[String]) =
    val checked = as.zipWithIndex.map { (a, i) =>
      val label = s"finding ${i + 1} (${Option(a.problem).getOrElse("").take(60)})"
      Severity.parse(Option(a.severity).getOrElse("")) match
        case None => Left(s"$label: severity '${a.severity}' is neither major nor minor")
        case Some(_) if Option(a.problem).forall(_.trim.isEmpty) => Left(s"$label: no problem stated")
        case Some(s) => Right((s, a))
    }
    val built = checked.collect { case Right(v) => v }.zipWithIndex.map { case ((s, a), i) =>
      val id = s"${r.prefix}${i + 1}"
      val evs = Option(a.evidence).getOrElse(Nil).take(MaxEvidence).map(ev)
      val f = Finding(id, r, s, Option(a.kind).map(_.trim).getOrElse(""), Option(a.where).map(_.trim).getOrElse(""),
        Option(a.quote).map(_.trim).getOrElse(""), a.problem.trim, Option(a.fix).map(_.trim).getOrElse(""),
        evs.collect { case Right(e) => e })
      (f, evs.collect { case Left(m) => s"${r.wire}: $id evidence dropped — $m" })
    }
    (built.map(_._1), checked.collect { case Left(m) => m }, built.flatMap(_._2))

  /** The stated verdict must not be worse than the findings show (a MAJOR with no major finding is a
    * finding left out); a milder one is simply corrected. On the last attempt the findings decide. */
  private def verdictOf(stated: String, fs: List[Finding], last: Boolean): Either[String, Verdict] =
    val derived = Verdict.of(fs)
    Verdict.parse(Option(stated).getOrElse("")) match
      case None if !last => Left(s"verdict '$stated' is not PASS, MINOR or MAJOR")
      case Some(v) if v.rank > derived.rank && !last =>
        Left(s"the verdict is ${v.wire} but no finding is ${v.wire.toLowerCase} — list the finding that makes it so")
      case _ => Right(derived)

  private def settle(r: Reviewer, verdict: Either[String, Verdict], fs: List[Finding], problems: List[String],
      dropped: List[String], last: Boolean)(build: Verdict => Report): Either[List[String], (Report, List[String])] =
    val all = problems ++ verdict.left.toOption.toList
    if all.nonEmpty && !last then Left(all)
    else Right((build(verdict.getOrElse(Verdict.of(fs))), all.map(p => s"${r.wire}: dropped — $p") ++ dropped))

  def checkRules(a: RulesAnswer, last: Boolean, ev: EvidenceCheck = NoEvidence,
      prior: Option[Prior] = None): Either[List[String], (Report, List[String])] =
    val (fs, ps, ds) = checkFindings(Reviewer.Rules, a.findings, ev)
    val (cs, cps, cws) = checksOf(Reviewer.Rules, prior, a.checks, last)
    settle(Reviewer.Rules, verdictOf(a.verdict, fs, last), fs, ps ++ cps, ds ++ cws, last)(v =>
      Report(Reviewer.Rules, v, fs, checks = cs))

  /** The cold reader's severity rules are partly mechanical: an empty explain-back slot, or more than
    * eight undefined terms, is MAJOR whether or not the model said so — code adds the finding. */
  def checkColdReader(a: ColdReaderAnswer, last: Boolean, prior: Option[Prior] = None): Either[List[String], (Report, List[String])] =
    val (stated, ps, _) = checkFindings(Reviewer.ColdReader, a.findings.map(_.asFinding))
    val slots   = a.emptySlots.map(_.trim.toLowerCase).filter(_.nonEmpty).distinct
    val unknown = slots.filterNot(ExplainSlots.contains).map(s => s"empty slot '$s' is not one of ${ExplainSlots.mkString(", ")}")
    val explain = Option(a.explainBack).map(_.trim).filter(_.nonEmpty)
    val extra =
      slots.filter(ExplainSlots.contains).map(s =>
        ("empty-slot", s"the explain-back has no '$s': the article does not say it", "state it in the opening section")) ++
        Option.when(a.termsUndefined > 8)(("term", s"${a.termsUndefined} terms are undefined (more than 8)",
          "define each on first use, and add a Glossary"))
    val added = extra.zipWithIndex.map { case ((k, p, fx), i) =>
      Finding(s"C${stated.size + i + 1}", Reviewer.ColdReader, Severity.Major, k, "explain-back", "", p, fx)
    }
    val fs = stated ++ added
    val (cs, cps, cws) = checksOf(Reviewer.ColdReader, prior, a.checks, last)
    val problems = ps ++ unknown ++ explain.fold(List("no explain-back"))(_ => Nil) ++ cps
    settle(Reviewer.ColdReader, verdictOf(a.verdict, fs, last), fs, problems, cws, last)(v =>
      Report(Reviewer.ColdReader, v, fs, explain, slots.filter(ExplainSlots.contains), checks = cs))

  /** The skeptic's proposals are verified like every other effect: vocabularies, citekeys, and a quote
    * that is really in the record. Unverifiable ones are dropped with a warning (they are not
    * findings; nothing is lost by dropping them). Its findings' evidence is checked the same way. */
  def checkSkeptic(a: SkepticAnswer, last: Boolean, isCitekey: String => Boolean, text: String => Option[String],
      minRatio: Double, prior: Option[Prior] = None): Either[List[String], (Report, List[String])] =
    val (fs, ps, ds) = checkFindings(Reviewer.Skeptic, a.findings, evidenceCheck(isCitekey, text, minRatio))
    val props    = a.proposals.map(AgentProposals.check(_, isCitekey, text, minRatio))
    val (cs, cps, cws) = checksOf(Reviewer.Skeptic, prior, a.checks, last)
    settle(Reviewer.Skeptic, verdictOf(a.verdict, fs, last), fs, ps ++ cps, ds ++ cws, last)(v =>
      Report(Reviewer.Skeptic, v, fs, proposals = props.collect { case Right(p) => p }, checks = cs))
      .map((r, ws) => (r, ws ++ props.collect { case Left(m) => s"skeptic: dropped $m" }))

  // ── checks of earlier findings, and the verification pass (the review ledger) ──────────────────

  /** What a reviewer's checks are verified against: its earlier findings it was asked about (open in
    * the ledger), and the article as it is now. */
  final case class Prior(asked: List[Finding], body: String, minRatio: Double)

  /** A full panel's checks: only when the ledger listed findings of this reviewer; otherwise ignored. */
  private def checksOf(r: Reviewer, prior: Option[Prior], as: List[CheckAnswer], last: Boolean)
      : (List[Check], List[String], List[String]) =
    prior.filter(_.asked.nonEmpty).fold((Nil, Nil, Nil))(p => checkChecks(r, p, as, last))

  /** Verify a reviewer's checks of its earlier findings: one per finding asked about (a missing one
    * leaves it open), each id one of them, and each quote IN the current body — the fixed text, or the
    * problem still there. An empty quote passes only when there is nothing left to quote: the finding
    * quoted nothing, or its passage is gone. Returns (verified checks, problems to send back, warnings);
    * on the last attempt the problems become warnings (what failed stays open). */
  def checkChecks(r: Reviewer, prior: Prior, as: List[CheckAnswer], last: Boolean): (List[Check], List[String], List[String]) =
    val byId = prior.asked.map(f => f.id -> f).toMap
    def gone(f: Finding) = f.quote.trim.isEmpty || !AgentVerify.findQuote(f.quote, prior.body, prior.minRatio).accepted
    val results = Option(as).getOrElse(Nil).map { c =>
      val id   = Option(c.id).map(_.trim).getOrElse("")
      val q    = Option(c.quote).map(_.trim).getOrElse("")
      val note = Option(c.note).map(_.trim).getOrElse("")
      byId.get(id) match
        case None => Left(s"check of '$id': not one of the findings you were asked to check (${byId.keys.toList.sorted.mkString(", ")})")
        case Some(_) if q.nonEmpty =>
          if AgentVerify.findQuote(q, prior.body, prior.minRatio).accepted then Right(Check(id, c.fixed, q, note))
          else Left(s"check of $id: the quote is not in the current article — copy it exactly: \"${q.take(80)}\"")
        case Some(f) if gone(f) => Right(Check(id, c.fixed, "", note))
        case Some(_) => Left(s"check of $id: no quote — quote the current text that shows it ${if c.fixed then "fixed" else "still there"}")
    }
    val checks  = results.collect { case Right(c) => c }.foldLeft(Vector.empty[Check])((acc, c) =>
      if acc.exists(_.id == c.id) then acc else acc :+ c).toList
    val missing = prior.asked.map(_.id).filterNot(checks.map(_.id).toSet).map(id => s"$id: no check — say whether it is fixed")
    val problems = results.collect { case Left(p) => p } ++ missing
    if last then (checks, Nil, problems.map(p => s"${r.wire}: $p (left open)"))
    else (checks, problems, Nil)

  /** The kinds of finding that are factual errors: the only MAJORs a verification pass may raise outside
    * the edited passages, and only with evidence code verified. */
  val FactualKinds: Set[String] =
    Set("contradictory", "extrapolatory", "decorative", "recall", "date", "status", "settled-but-contested", "number")

  /** At most this many lessons per answer, each at most this long. */
  val MaxLessonsPerAnswer = 3
  val LessonChars = 200

  /** A verification pass's finding, scoped to the edited passages: inside them anything stands; outside
    * them a MAJOR stands only as a factual error with evidence, else it is recorded as minor (with the
    * warning saying so). */
  def scope(f: Finding, edited: String, minRatio: Double): (Finding, Option[String]) =
    val inside = f.quote.trim.nonEmpty && AgentVerify.findQuote(f.quote, edited, minRatio).accepted
    if !f.major || inside || (FactualKinds(f.kind.trim.toLowerCase(java.util.Locale.ROOT)) && f.evidence.nonEmpty) then (f, None)
    else (f.copy(severity = Severity.Minor),
      Some(s"${f.reviewer.wire}: ${f.id} is outside the edited passages and not a factual error with evidence — recorded as minor"))

  /** Verify a verification pass's answer: the checks ([[checkChecks]]), the new findings
    * ([[checkFindings]], then [[scope]]d to `edited`, the text of the edited passages) and the lessons. */
  def checkRecheck(r: Reviewer, a: RecheckAnswer, prior: Prior, edited: String, ev: EvidenceCheck,
      last: Boolean): Either[List[String], (Recheck, List[String])] =
    val (cs, cps, cws) = checkChecks(r, prior, a.checks, last)
    val (fs, ps, ds)   = checkFindings(r, Option(a.findings).getOrElse(Nil), ev)
    val scoped  = fs.map(scope(_, edited, prior.minRatio))
    val lessons = Option(a.lessons).getOrElse(Nil).map(_.trim).filter(_.nonEmpty).distinct.take(MaxLessonsPerAnswer).map(_.take(LessonChars))
    val problems = cps ++ ps
    if problems.nonEmpty && !last then Left(problems)
    else Right((Recheck(r, cs, scoped.map(_._1), lessons), cws ++ ps.map(p => s"${r.wire}: dropped — $p") ++ ds ++ scoped.flatMap(_._2)))

  // ── merge and gate (§4.3 steps 4–5) ─────────────────────────────────────────────────────────────

  private def key(f: Finding): (String, String) = (AgentVerify.normalise(f.where), AgentVerify.normalise(f.problem))

  /** Did a reviewer raise `f` before? Same reviewer, and the same quoted passage — or the same
    * `where` + `problem` when there is no quote (a model rewords its problem, rarely its quote). */
  def sameFinding(a: Finding, b: Finding): Boolean =
    a.reviewer == b.reviewer && {
      val (qa, qb) = (AgentVerify.normalise(a.quote), AgentVerify.normalise(b.quote))
      if qa.nonEmpty && qb.nonEmpty then qa == qb else key(a) == key(b)
    }

  /** One findings list for the round: mechanical first, then rules, cold reader, skeptic; duplicates
    * (the same `where` + `problem`, whoever raised it) dropped; a cold-reader and a skeptic finding on
    * the same passage both KEPT (the fixer must satisfy both). A finding the fixer rejected in an
    * earlier round and a reviewer raises again is flagged `escalated`. A dropped duplicate's evidence
    * joins the finding that is kept. The verdict is the worst of every report and every kept finding. */
  def merge(round: Int, mechanicalFindings: List[Finding], reports: List[Report], rejectedBefore: List[Finding]): Merged =
    val all  = mechanicalFindings ++ reports.sortBy(_.reviewer.ordinal).flatMap(_.findings)
    // a duplicate is dropped, but the records it cites join the finding that is kept
    val kept = all.foldLeft(Vector.empty[Finding]) { (acc, f) =>
      acc.indexWhere(g => key(g) == key(f)) match
        case -1 => acc :+ f
        case i  => acc.updated(i, acc(i).copy(evidence = mergeEvidence(acc(i).evidence, f.evidence)))
    }.toList.map(f => if rejectedBefore.exists(sameFinding(_, f)) then f.copy(escalated = true) else f)
    val verdict = Verdict.worst(reports.map(_.verdict) :+ Verdict.of(kept))
    val cold    = reports.find(_.reviewer == Reviewer.ColdReader)
    Merged(round, verdict, kept, cold.flatMap(_.explainBack), cold.toList.flatMap(_.emptySlots),
      reports.flatMap(_.proposals).distinct)

  private def mergeEvidence(a: List[Evidence], b: List[Evidence]): List[Evidence] =
    (a ++ b).foldLeft(Vector.empty[Evidence]) { (acc, e) =>
      if acc.exists(x => x.citekey == e.citekey && AgentVerify.normalise(x.quote) == AgentVerify.normalise(e.quote)) then acc
      else acc :+ e
    }.take(MaxEvidence).toList

  /** After a round: stop when nothing major is open, when no fixing was asked for, or when the rounds
    * are used up. */
  def done(m: Merged, round: Int, rounds: Int, fix: Boolean): Boolean =
    m.majors.isEmpty || !fix || round >= rounds

  /** The panel rounds a run gets: what it asked for, else the configured default, never more than the
    * cap. The fixer works BETWEEN rounds, so a run that fixes gets at least two (one to find, one to
    * check the fix). */
  def rounds(requested: Option[Int], default: Int, cap: Int, fix: Boolean): Int =
    val floor = if fix then 2 else 1
    math.max(floor, math.min(math.max(floor, cap), requested.getOrElse(default)))

  // ── the fixer (§4.3 step 6) ─────────────────────────────────────────────────────────────────────

  def words(s: String): Int = s.split("\\s+").count(_.nonEmpty)

  /** The fixer's turns: its agent's `max-turns`, plus `perFinding` for every finding that may need a
    * lookup — each MAJOR, and each minor of the rules reviewer or the skeptic (a factual one) — never
    * more than `cap` (and never fewer than `base`). Eighteen findings in twelve turns ran out. */
  def fixerTurns(base: Int, m: Merged, perFinding: Int, cap: Int): Int =
    val b = math.max(1, base)
    val n = m.findings.count(f => f.major || f.reviewer == Reviewer.Rules || f.reviewer == Reviewer.Skeptic)
    math.max(b, math.min(cap, b + math.max(0, perFinding) * n))

  /** What a body check needs from the corpus: does `[[x]]` name a source, does it resolve at all
    * (a source, a short form, an article), and the formatting ERRORS of a text (one line each, with no
    * position, so the lines of two texts compare). */
  final case class BodyCorpus(isCitekey: String => Boolean, isTarget: String => Boolean,
      formatErrors: String => List[String], generated: Boolean = false)

  /** THE check of a draft — the fixer's edits against the body the round reviewed, or a whole body —
    * shared by the pipeline's validation of the fixer's answer and the `check_edits` tool, so the two
    * can never disagree. Edits apply in order ([[ArticleEdits.applyAll]]); an edit whose text links
    * to nothing is never applied. The result's problems: each edit that did not apply; links to
    * nothing; a hand-written Sources cited heading; up to three uncited paragraphs; banned filler; and
    * formatting errors the draft introduced (ones already in `before` are not the draft's). FATAL (never
    * written, even on a last attempt): an empty body, or a link problem `before` did not have. */
  def checkBody(slug: String, before: String, kind: String, draft: ArticleEdits.Draft, corpus: BodyCorpus): ArticleEdits.ArticleCheck =
    def links(t: String) = AgentVerify.linkProblems(t, corpus.isCitekey, corpus.isTarget)
    val applied = draft match
      case ArticleEdits.Draft.Edits(es) =>
        ArticleEdits.applyAll(before, es, a => a.text.flatMap(t => links(t).headOption).map(p => s"its text is refused: $p"))
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
          again.filter(f => f.kind == "citation" && f.problem.contains("no [[citekey]]")).take(3)
            .map(f => s"still ${f.problem}: \"${f.quote.take(80)}\"") ++
          again.filter(_.kind == "language").map(_.problem) ++
          corpus.formatErrors(body).filterNot(formatBefore).map(e => s"formatting: $e")
    val fatal = body.trim.isEmpty || links(body).toSet.diff(links(before).toSet).nonEmpty
    ArticleEdits.ArticleCheck(slug, kind, applied.results, applied.problems ++ bodyProblems, fatal,
      words(before), words(body), body)

  /** Verify a revision: its edits applied to the body the round reviewed and the result checked
    * ([[checkBody]]); every MAJOR finding either applied or rejected; no mechanical or cold-reader
    * finding rejected (a reader's confusion is a fact; a mechanical hit is certain); and every
    * rejection backed by a quote that IS in the record of the source it names — "a rejection you
    * cannot quote is not a rejection". On the last attempt the edits that applied are kept if the
    * result is not fatal; the others, and every remaining problem, are reported ("kept despite"), and
    * rejections that cannot be verified are listed as `unverified` (still open). */
  def checkFix(a: FixerAnswer, m: Merged, slug: String, before: String, kind: String, corpus: BodyCorpus,
      isCitekey: String => Boolean, text: String => Option[String], minRatio: Double,
      last: Boolean, coverage: Option[Coverage] = None): Either[List[String], (Fix, List[String])] =
    val ids   = m.findings.map(_.id).toSet
    val byId  = m.findings.map(f => f.id -> f).toMap
    val applied  = a.applied.map(_.trim).filter(_.nonEmpty).distinct
    val unknown  = (applied ++ a.rejected.map(_.id.trim)).filterNot(ids).distinct.map(i => s"there is no finding '$i'")
    val rejections = a.rejected.map { r =>
      val id = r.id.trim
      val ck = r.citekey.trim
      byId.get(id) match
        case None => Left(id -> s"rejection of '$id': no such finding")
        case Some(f) if f.reviewer == Reviewer.Mechanical =>
          Left(id -> s"$id is a mechanical check; it cannot be rejected, only fixed")
        case Some(f) if f.reviewer == Reviewer.ColdReader =>
          Left(id -> s"$id is a reader's confusion; it cannot be rejected — define or restructure instead")
        case Some(_) if !isCitekey(ck) => Left(id -> s"rejection of $id names '$ck', which is not a citekey")
        case Some(_) =>
          val q = Option(r.quote).map(_.trim).getOrElse("")
          val hit = text(ck).map(t => AgentVerify.findQuote(q, t, minRatio))
          hit.filter(_.accepted) match
            case Some(qm) => Right(Rejection(id, r.reason.trim, ck, q, qm.ratio))
            case None => Left(id -> s"rejection of $id: the quote is not in the record of $ck — quote it exactly or apply the finding")
    }
    val rejectedOk  = rejections.collect { case Right(r) => r }
    val rejectedBad = rejections.collect { case Left(v) => v }
    val wont = Option(a.wontFix).getOrElse(Nil).map { w =>
      val id = Option(w.id).map(_.trim).getOrElse("")
      byId.get(id) match
        case None => Left(s"won't fix '$id': there is no such finding")
        case Some(f) if f.major => Left(s"$id is MAJOR: apply it or reject it — only a minor finding can be declined")
        case Some(_) if Option(w.reason).forall(_.trim.isEmpty) => Left(s"won't fix $id: give the reason")
        case Some(_) => Right(WontFix(id, w.reason.trim))
    }
    val decided  = applied.toSet ++ rejectedOk.map(_.id) ++ rejectedBad.map(_._1)
    val undecided = m.majors.map(_.id).filterNot(decided).map(i => s"major finding $i is neither applied nor rejected")
    val edits = Option(a.edits).getOrElse(Nil)
    val check = checkBody(slug, before, kind, ArticleEdits.Draft.Edits(edits), corpus)
    val stated = Option(a.reasons).getOrElse(Nil)
    val (reasons, reasonProblems) = checkReasons(stated, coverage, check.body, corpus.isCitekey, m, applied.toSet)
    val noEdit = Option.when(applied.exists(ids) && !check.edits.exists(_.applied) && reasons.isEmpty)(
      s"you list ${applied.mkString(", ")} as applied, but no edit changed the article")
    val problems = check.problems ++ reasonProblems ++ noEdit.toList ++ unknown ++ undecided ++ rejectedBad.map(_._2) ++
      wont.collect { case Left(p) => p }
    if problems.isEmpty || (last && !check.fatal) then
      val fix = Fix(check.body, check.edits, applied.filter(ids), rejectedOk, rejectedBad.map(_._1).filter(ids).distinct,
        a.unresolvedMoved.map(_.trim).filter(_.nonEmpty), Option(a.notes).map(_.trim).getOrElse(""), check.wordsBefore,
        check.wordsAfter, reasons, wont.collect { case Right(w) => w }.filterNot(w => applied.contains(w.id)).distinctBy(_.id))
      Right((fix, problems.map(p => s"fixer: kept despite — $p")))
    else Left(problems)

  /** The fixer's coverage reasons (#958) against the coverage the round reviewed: each names a source
    * the filter returns that the revised body leaves out (an empty one may remove any stored reason);
    * an unplaced-sources finding listed as applied leaves none unplaced. Returns the reasons that
    * pass (written on the last attempt) and the problems. */
  def checkReasons(stated: List[CoverageReason], coverage: Option[Coverage], body: String, isCitekey: String => Boolean,
      m: Merged, applied: Set[String]): (List[CoverageReason], List[String]) =
    coverage match
      case None =>
        (Nil, Option.when(stated.nonEmpty)("the article records no filter, so it has no generated Not used list: " +
          "leave `reasons` empty").toList)
      case Some(c) =>
        val citedAfter = AgentVerify.wikilinks(ArticleCoverage.withoutNotUsed(body)).filter(isCitekey).toSet
        val inFilter   = (c.notUsed.map(_.source.citekey) ++ c.cited.map(_.citekey)).toSet
        val stored     = c.reasons.map(r => r.citekey -> r.reason).toMap
        val checked = stated.map { r =>
          val ck = Option(r.citekey).map(_.trim.stripPrefix("[[").stripSuffix("]]").trim.toLowerCase).getOrElse("")
          val removes = Option(r.reason).forall(_.trim.isEmpty)
          if removes && stored.contains(ck) then Right(r.copy(citekey = ck))
          else if removes then Left(s"reason for [[$ck]]: empty, and no reason is stored for it")
          else if !inFilter(ck) then Left(s"reason for [[$ck]]: the filter does not return it — give reasons only for its sources")
          else if citedAfter(ck) then Left(s"reason for [[$ck]]: the article cites it — a reason is only for a source it leaves out")
          else CoverageReason.merge(Map.empty, List(r.copy(citekey = ck))).map(_ => r.copy(citekey = ck))
        }
        val ok = checked.collect { case Right(r) => r }
        val merged = CoverageReason.merge(stored, ok).getOrElse(stored)
        val unplacedIds = m.findings.filter(f => f.reviewer == Reviewer.Mechanical && f.kind == CoverageKind &&
          f.major && f.problem.contains("neither cited nor given a reason")).map(_.id)
        val left = ArticleCoverage.unplacedAfter(c, citedAfter, merged)
        val still = Option.when(unplacedIds.exists(applied) && left.nonEmpty)(
          s"${unplacedIds.filter(applied).mkString(", ")}: still neither cited nor given a reason: " +
            left.take(20).map(ck => s"[[$ck]]").mkString(", ") + (if left.size > 20 then s" (+${left.size - 20} more)" else ""))
        (ok, checked.collect { case Left(p) => p } ++ still.toList)

  // ── what the reviewers and the fixer are sent ───────────────────────────────────────────────────

  /** One evidence record, as the fixer reads it. */
  def evidenceLine(e: Evidence): String =
    val at = e.offset.fold("")(o => s" @$o")
    s"[[${e.citekey}]]$at: \"${e.quote.replace("\n", " ").take(400)}\""

  /** The findings, as the fixer reads them — each with the records the reviewer found, verified. */
  def findingsText(m: Merged, notes: Map[String, String] = Map.empty): String =
    m.findings.map { f =>
      val esc = if f.escalated then " [raised again after a rejection]" else ""
      s"- ${f.id} [${f.reviewer.wire}, ${f.severity.wire}${if f.kind.nonEmpty then s", ${f.kind}" else ""}]$esc " +
        s"where: ${f.where}\n  quote: ${f.quote}\n  problem: ${f.problem}\n  fix: ${f.fix}" +
        f.evidence.map(e => s"\n  evidence: ${evidenceLine(e)}").mkString +
        notes.get(f.id).fold("")(n => s"\n  note: $n")
    }.mkString("\n")

  // ── bookkeeping (§4.3 step 8) ──────────────────────────────────────────────────────────────────

  /** The worklog line, in the form the review-article skill writes. */
  def logLine(o: Outcome, last: Merged, runId: String): String =
    val counts = List(Reviewer.Rules, Reviewer.ColdReader, Reviewer.Skeptic, Reviewer.Mechanical)
      .map(r => s"${r.prefix}:${last.count(r)}").mkString(" ")
    val fixer = if o.fixed then s", fixer applied ${o.applied} / rejected ${o.rejected}" else ""
    s"review-article [[${o.slug}]]: round ${o.rounds} → ${o.verdict.wire} ($counts)$fixer (agent run ${runId.take(8)})"
