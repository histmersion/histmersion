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

  final case class FindingAnswer(
      severity: String,
      kind: String,
      where: String,
      quote: String,
      problem: String,
      fix: String,
      evidence: Option[String] = None
  ) derives KindlingsCodecAsObject
  object FindingAnswer:
    given Schema[FindingAnswer] = KindlingsSchema.derived[FindingAnswer].schema
      .modify(_.severity)(_.description("major | minor — by the severity rules of your instructions"))
      .modify(_.kind)(_.description("The rule or kind your instructions name (e.g. synth, decorative, term, missing-critique)"))
      .modify(_.where)(_.description("The section heading, or the first 8 words of the passage"))
      .modify(_.quote)(_.description("The article's words the finding is about, copied exactly"))
      .modify(_.problem)(_.description("What is wrong, in one or two sentences"))
      .modify(_.fix)(_.description("What would fix it"))
      .modify(_.evidence)(_.description("The citekey(s), edge or finding id that show it; null if none"))

  final case class RulesAnswer(
      verdict: String,
      findings: List[FindingAnswer],
      filter: String,
      uncitedRelevant: List[String],
      statusesSilent: List[String],
      accuracyChecked: Int
  ) derives KindlingsCodecAsObject
  object RulesAnswer:
    given Schema[RulesAnswer] = KindlingsSchema.derived[RulesAnswer].schema
      .modify(_.verdict)(_.description("PASS | MINOR | MAJOR — the worst of your findings"))
      .modify(_.filter)(_.description("The filter you reconstructed and how many sources it returned"))
      .modify(_.uncitedRelevant)(_.description("Citekeys the filter returns that clearly belong and are neither cited nor under Not used"))
      .modify(_.statusesSilent)(_.description("Cited non-current, non-foundational citekeys whose status the article does not state"))
      .modify(_.accuracyChecked)(_.description("How many cited claims you checked against the database"))

  final case class ColdReaderAnswer(
      verdict: String,
      explainBack: String,
      emptySlots: List[String],
      findings: List[FindingAnswer],
      termsUndefined: Int,
      opaqueSentences: Int,
      lostAt: Option[String] = None
  ) derives KindlingsCodecAsObject
  object ColdReaderAnswer:
    given Schema[ColdReaderAnswer] = KindlingsSchema.derived[ColdReaderAnswer].schema
      .modify(_.verdict)(_.description("PASS | MINOR | MAJOR — the worst of your findings"))
      .modify(_.explainBack)(_.description("Five plain sentences: the question, the answer, who disagrees with whom, what is open"))
      .modify(_.emptySlots)(_.description("Slots you could not fill from the text: question | answer | disagreement | open"))
      .modify(_.lostAt)(_.description("The section where you stopped following; null if you never did"))

  final case class SkepticAnswer(
      verdict: String,
      findings: List[FindingAnswer],
      proposals: List[ProposalAnswer],
      claimsChecked: Int
  ) derives KindlingsCodecAsObject
  object SkepticAnswer:
    given Schema[SkepticAnswer] = KindlingsSchema.derived[SkepticAnswer].schema
      .modify(_.verdict)(_.description("PASS | MINOR | MAJOR — the worst of your findings"))
      .modify(_.proposals)(_.description("Database errors you noticed, as changes for a person to approve: a status, or a disagreement edge — each with a quote from the record"))
      .modify(_.claimsChecked)(_.description("How many load-bearing claims you checked"))

  final case class RejectionAnswer(id: String, reason: String, citekey: String, quote: String)
      derives KindlingsCodecAsObject
  object RejectionAnswer:
    given Schema[RejectionAnswer] = KindlingsSchema.derived[RejectionAnswer].schema
      .modify(_.id)(_.description("The id of the finding you reject (R2, S1…)"))
      .modify(_.citekey)(_.description("The source whose record contradicts the finding"))
      .modify(_.quote)(_.description("The sentence of that record that contradicts it, copied EXACTLY (from get_source)"))

  final case class FixerAnswer(
      body: String,
      applied: List[String],
      rejected: List[RejectionAnswer],
      unresolvedMoved: List[String],
      notes: String
  ) derives KindlingsCodecAsObject
  object FixerAnswer:
    given Schema[FixerAnswer] = KindlingsSchema.derived[FixerAnswer].schema
      .modify(_.body)(_.description("The WHOLE revised article body, Markdown"))
      .modify(_.applied)(_.description("Ids of the findings you applied"))
      .modify(_.rejected)(_.description("Findings you reject, each with the record's contradicting sentence"))
      .modify(_.unresolvedMoved)(_.description("Claims you moved to the unresolved section"))
      .modify(_.notes)(_.description("What the next review round should know"))

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
      evidence: Option[String] = None,
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

  /** One reviewer's verified answer. */
  final case class Report(
      reviewer: Reviewer,
      verdict: Verdict,
      findings: List[Finding],
      explainBack: Option[String] = None,
      emptySlots: List[String] = Nil,
      proposals: List[Proposal] = Nil
  ) derives KindlingsCodecAsObject

  /** The article as one round reviews it: its text and the token a patch must present. */
  final case class Snapshot(slug: String, title: String, kind: String, body: String, updatedAt: Option[String],
      truncated: Boolean) derives KindlingsCodecAsObject

  /** The panel's merged result for one round. */
  final case class Merged(round: Int, verdict: Verdict, findings: List[Finding], explainBack: Option[String],
      emptySlots: List[String], proposals: List[Proposal]) derives KindlingsCodecAsObject:
    def majors: List[Finding]   = findings.filter(_.major)
    def escalated: List[Finding] = findings.filter(_.escalated)
    def count(r: Reviewer): Int = findings.count(_.reviewer == r)

  final case class Rejection(id: String, reason: String, citekey: String, quote: String, ratio: Double)
      derives KindlingsCodecAsObject

  /** The fixer's verified revision. `unverified` = rejections whose quote code could not find in the
    * record: they do not count as rejections (the finding stays open). */
  final case class Fix(body: String, applied: List[String], rejected: List[Rejection], unverified: List[String],
      unresolvedMoved: List[String], notes: String, wordsBefore: Int, wordsAfter: Int) derives KindlingsCodecAsObject

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
    * article (a map of content) has none. */
  def requiredSections(kind: String): List[List[String]] =
    if kind.trim.equalsIgnoreCase("index") then Nil
    else List(List("disagreements"), List("unresolved", "open questions"), List("not used"))

  /** The checks code can make with certainty, as `M<n>` findings: banned filler, a paragraph with no
    * `[[citekey]]`, escaped link brackets, a hand-written Sources cited heading, a missing compulsory
    * section, a duplicated heading, and `[[links]]` that resolve to nothing. */
  def mechanical(slug: String, kind: String, body: String, isTarget: String => Boolean): List[Finding] =
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
    val missing = requiredSections(kind).filterNot(alts => hs.exists(h => alts.exists(a => norm(h).startsWith(a))))
      .map(alts => f(Severity.Major, "section", "headings", "",
        s"no ${alts.map(a => s"'$a'").mkString(" or ")} section (writing guide rule 4)", "add the section"))
    val dup = hs.groupBy(norm).collect { case (_, xs) if xs.size > 1 => xs.head }.toList.sorted.map(h =>
      f(Severity.Minor, "section", h, s"## $h", "a duplicated heading", "merge the two sections"))
    val dangling = AgentVerify.wikilinks(body).filterNot(isTarget).map(t =>
      f(Severity.Major, "citation", s"[[$t]]", s"[[$t]]", s"[[$t]] is neither a citekey nor an article", "link a real citekey or article"))
    (filler ++ uncited ++ escaped ++ sourcesCited ++ missing ++ dup ++ dangling).zipWithIndex.map {
      case ((sev, k, w, q, p, fx), i) => Finding(s"M${i + 1}", Reviewer.Mechanical, sev, k, w, q, p, fx)
    }

  // ── validating a reviewer's answer ──────────────────────────────────────────────────────────────

  /** Findings as stated → verified findings with code-assigned ids (`R1`…). A finding with no
    * problem or a severity outside the vocabulary is a problem to send back. */
  def checkFindings(r: Reviewer, as: List[FindingAnswer]): (List[Finding], List[String]) =
    val checked = as.zipWithIndex.map { (a, i) =>
      val label = s"finding ${i + 1} (${Option(a.problem).getOrElse("").take(60)})"
      Severity.parse(Option(a.severity).getOrElse("")) match
        case None => Left(s"$label: severity '${a.severity}' is neither major nor minor")
        case Some(_) if Option(a.problem).forall(_.trim.isEmpty) => Left(s"$label: no problem stated")
        case Some(s) => Right((s, a))
    }
    val ok = checked.collect { case Right(v) => v }.zipWithIndex.map { case ((s, a), i) =>
      Finding(s"${r.prefix}${i + 1}", r, s, Option(a.kind).map(_.trim).getOrElse(""), Option(a.where).map(_.trim).getOrElse(""),
        Option(a.quote).map(_.trim).getOrElse(""), a.problem.trim, Option(a.fix).map(_.trim).getOrElse(""),
        a.evidence.map(_.trim).filter(_.nonEmpty))
    }
    (ok, checked.collect { case Left(m) => m })

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
      last: Boolean)(build: Verdict => Report): Either[List[String], (Report, List[String])] =
    val all = problems ++ verdict.left.toOption.toList
    if all.nonEmpty && !last then Left(all)
    else Right((build(verdict.getOrElse(Verdict.of(fs))), all.map(p => s"${r.wire}: dropped — $p")))

  def checkRules(a: RulesAnswer, last: Boolean): Either[List[String], (Report, List[String])] =
    val (fs, ps) = checkFindings(Reviewer.Rules, a.findings)
    settle(Reviewer.Rules, verdictOf(a.verdict, fs, last), fs, ps, last)(v => Report(Reviewer.Rules, v, fs))

  /** The cold reader's severity rules are partly mechanical: an empty explain-back slot, or more than
    * eight undefined terms, is MAJOR whether or not the model said so — code adds the finding. */
  def checkColdReader(a: ColdReaderAnswer, last: Boolean): Either[List[String], (Report, List[String])] =
    val (stated, ps) = checkFindings(Reviewer.ColdReader, a.findings)
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
    val problems = ps ++ unknown ++ explain.fold(List("no explain-back"))(_ => Nil)
    settle(Reviewer.ColdReader, verdictOf(a.verdict, fs, last), fs, problems, last)(v =>
      Report(Reviewer.ColdReader, v, fs, explain, slots.filter(ExplainSlots.contains)))

  /** The skeptic's proposals are verified like every other effect: vocabularies, citekeys, and a quote
    * that is really in the record. Unverifiable ones are dropped with a warning (they are not
    * findings; nothing is lost by dropping them). */
  def checkSkeptic(a: SkepticAnswer, last: Boolean, isCitekey: String => Boolean, text: String => Option[String],
      minRatio: Double): Either[List[String], (Report, List[String])] =
    val (fs, ps) = checkFindings(Reviewer.Skeptic, a.findings)
    val props    = a.proposals.map(AgentProposals.check(_, isCitekey, text, minRatio))
    settle(Reviewer.Skeptic, verdictOf(a.verdict, fs, last), fs, ps, last)(v =>
      Report(Reviewer.Skeptic, v, fs, proposals = props.collect { case Right(p) => p }))
      .map((r, ws) => (r, ws ++ props.collect { case Left(m) => s"skeptic: dropped $m" }))

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
    * earlier round and a reviewer raises again is flagged `escalated`. The verdict is the worst of
    * every report and every kept finding. */
  def merge(round: Int, mechanicalFindings: List[Finding], reports: List[Report], rejectedBefore: List[Finding]): Merged =
    val all  = mechanicalFindings ++ reports.sortBy(_.reviewer.ordinal).flatMap(_.findings)
    val kept = all.foldLeft(Vector.empty[Finding]) { (acc, f) =>
      if acc.exists(g => key(g) == key(f)) then acc else acc :+ f
    }.toList.map(f => if rejectedBefore.exists(sameFinding(_, f)) then f.copy(escalated = true) else f)
    val verdict = Verdict.worst(reports.map(_.verdict) :+ Verdict.of(kept))
    val cold    = reports.find(_.reviewer == Reviewer.ColdReader)
    Merged(round, verdict, kept, cold.flatMap(_.explainBack), cold.toList.flatMap(_.emptySlots),
      reports.flatMap(_.proposals).distinct)

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

  /** Verify a revision: a body (whole, with valid links, no escaped brackets, no Sources cited
    * heading, every paragraph cited, no banned filler), every MAJOR finding either applied or rejected,
    * no mechanical or cold-reader finding rejected (a reader's confusion is a fact; a mechanical hit is
    * certain), and every rejection backed by a quote that IS in the record of the source it names — "a
    * rejection you cannot quote is not a rejection". On the last attempt the body is kept if it is
    * sound, and rejections that cannot be verified are listed as `unverified` (still open). */
  def checkFix(a: FixerAnswer, m: Merged, before: String, kind: String, isTarget: String => Boolean,
      isCitekey: String => Boolean, text: String => Option[String], minRatio: Double,
      last: Boolean): Either[List[String], (Fix, List[String])] =
    val body  = Option(a.body).map(_.trim).getOrElse("")
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
    val decided  = applied.toSet ++ rejectedOk.map(_.id) ++ rejectedBad.map(_._1)
    val undecided = m.majors.map(_.id).filterNot(decided).map(i => s"major finding $i is neither applied nor rejected")
    val bodyProblems =
      if body.isEmpty then List("the body is empty — return the whole revised article")
      else
        val again = mechanical("article", kind, body, isTarget)
        AgentVerify.linkProblems(body, isCitekey, isTarget) ++
          again.filter(f => f.kind == "section" && f.problem.contains("Sources cited")).map(_.problem) ++
          again.filter(f => f.kind == "citation" && f.problem.contains("no [[citekey]]")).take(3)
            .map(f => s"still ${f.problem}: \"${f.quote.take(80)}\"") ++
          again.filter(_.kind == "language").map(_.problem)
    val fatal    = body.isEmpty || AgentVerify.linkProblems(body, isCitekey, isTarget).nonEmpty
    val problems = bodyProblems ++ unknown ++ undecided ++ rejectedBad.map(_._2)
    if problems.isEmpty || (last && !fatal) then
      val fix = Fix(body, applied.filter(ids), rejectedOk, rejectedBad.map(_._1).filter(ids).distinct,
        a.unresolvedMoved.map(_.trim).filter(_.nonEmpty), Option(a.notes).map(_.trim).getOrElse(""), words(before), words(body))
      Right((fix, problems.map(p => s"fixer: kept despite — $p")))
    else Left(problems)

  // ── what the reviewers and the fixer are sent ───────────────────────────────────────────────────

  /** The findings, as the fixer reads them. */
  def findingsText(m: Merged): String =
    m.findings.map { f =>
      val esc = if f.escalated then " [raised again after a rejection]" else ""
      s"- ${f.id} [${f.reviewer.wire}, ${f.severity.wire}${if f.kind.nonEmpty then s", ${f.kind}" else ""}]$esc " +
        s"where: ${f.where}\n  quote: ${f.quote}\n  problem: ${f.problem}\n  fix: ${f.fix}" +
        f.evidence.fold("")(e => s"\n  evidence: $e")
    }.mkString("\n")

  // ── bookkeeping (§4.3 step 8) ──────────────────────────────────────────────────────────────────

  /** The worklog line, in the form the review-article skill writes. */
  def logLine(o: Outcome, last: Merged, runId: String): String =
    val counts = List(Reviewer.Rules, Reviewer.ColdReader, Reviewer.Skeptic, Reviewer.Mechanical)
      .map(r => s"${r.prefix}:${last.count(r)}").mkString(" ")
    val fixer = if o.fixed then s", fixer applied ${o.applied} / rejected ${o.rejected}" else ""
    s"review-article [[${o.slug}]]: round ${o.rounds} → ${o.verdict.wire} ($counts)$fixer (agent run ${runId.take(8)})"

  /** One backlog item per MAJOR still open (an escalated one says so). */
  def taskLines(o: Outcome): List[String] =
    o.open.map { f =>
      val what = if f.escalated then "MAJOR raised again after the fixer rejected it" else "MAJOR open"
      s"review-article [[${o.slug}]]: $what — ${f.line.take(300)}"
    }
