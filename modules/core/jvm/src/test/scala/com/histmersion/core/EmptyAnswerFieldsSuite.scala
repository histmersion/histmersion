package com.histmersion.core

import com.histmersion.core.Agents.ProbeAnswer
import com.histmersion.core.GoalPlans.DraftMode

/** A strict-schema provider (Codex) sends `""` or `null` for a field it has nothing for, and `null`
  * decodes like an absent field. An answer whose NEEDED field is empty or null must fail its step's
  * check with a problem that says which field — so the step asks again — never pass as an answer
  * (2026-10-08: the probe accepted `{"citekey":"huxley1956_catalogue","title":"","year":null}`).
  * One test per answer type; genuinely optional fields stay optional. */
class EmptyAnswerFieldsSuite extends munit.FunSuite:

  test("probe: an empty title or citekey, and a null year for a record that has one, are problems"):
    val empty = ProbeAnswer("huxley1956_catalogue", "", None)
    assertEquals(empty.problems(Some(1956)), List(
      "the title is empty — copy the source's exact title from get_source",
      "the year is missing — give the source's publication year from get_source"))
    assert(ProbeAnswer(" ", "A Catalogue", Some(1956)).problems(Some(1956)).head.startsWith("the citekey is empty"))
    // a record with no year is rightly answered with null
    assertEquals(ProbeAnswer("x1900_y", "A title", None).problems(None), Nil)
    assertEquals(ProbeAnswer("huxley1956_catalogue", "A Catalogue", Some(1956)).problems(Some(1956)), Nil)
    // from the wire, as the live probe answered
    val wire = io.circe.parser.decode[ProbeAnswer]("""{"citekey":"huxley1956_catalogue","title":"","year":null}""")
      .fold(e => fail(e.getMessage), identity)
    assertEquals(wire.problems(Some(1956)).size, 2)

  // ── enrich-source ─────────────────────────────────────────────────────────────────────────────

  import EnrichSource.*

  private def classification(summary: String = "Tin by sea.", status: String = "current", evidence: String = "the text says so") =
    Classification(summary, Nil, Nil, Nil, None, status, evidence)

  test("classify: an empty summary or status fails; a status other than unassessed needs its evidence"):
    val noSummary = checkClassification(classification(summary = " "), 50000, last = false)
    assertEquals(noSummary.left.toOption, Some(List("the summary is empty")))
    assert(checkClassification(classification(status = ""), 50000, last = true).left.exists(_.exists(_.contains("is not a status"))))
    val noEvidence = checkClassification(classification(evidence = ""), 50000, last = false)
    assertEquals(noEvidence.left.toOption, Some(List("the status evidence is empty — say, from the text, why that status")))
    // unassessed needs none (the schema: "empty if unassessed")
    assert(checkClassification(classification(status = "unassessed", evidence = ""), 50000, last = false).isRight)
    // the last attempt keeps the classification, with a warning
    val (c, ws) = checkClassification(classification(evidence = ""), 50000, last = true).toOption.get
    assertEquals(c.status, SourceStatus.Current)
    assert(ws.exists(_.contains("status evidence is empty")), ws)

  private val window = Window(1, None, "The Uluburun ship carried ten tons of copper and one ton of tin.")
  private val open = FindingGuards(0.0, 1000000L, Nil, (_, _, _) => true)
  private def proposal(claim: String, quote: String = "The Uluburun ship carried ten tons of copper and one ton of tin.",
      confidence: String = "high") = FindingProposal(claim, quote, None, None, None, None, None, Nil, confidence)

  test("findings: an empty claim, quote or confidence fails, naming the finding"):
    def problems(p: FindingProposal) =
      checkFindings(Findings(List(p)), window, 0.9, 50000, None, Set.empty, open, last = false).left.toOption.get
    assert(problems(proposal("")).head.contains("the claim is empty"))
    assert(problems(proposal("Tin by sea.", quote = "")).head.contains("the quote is not in the text"))
    assert(problems(proposal("Tin by sea.", confidence = "")).head.contains("is not a confidence"))

  test("notes: empty notes fail"):
    assertEquals(checkNotes(Notes("  "), _ => true, _ => true), Left(List("the notes are empty")))

  // ── compile-article ───────────────────────────────────────────────────────────────────────────

  import CompileArticle.*

  private val record = "Summary:\nThe Uluburun ship carried ten tons of copper and one ton of tin."

  test("extract: an empty claim, quote or confidence fails; an unused record must say why"):
    def problems(c: ClaimAnswer) = checkExtract(ExtractAnswer(true, "tin", List(c)), "ck", record, 0.9, 10, last = false).left.toOption.get
    val quote = "The Uluburun ship carried ten tons of copper and one ton of tin."
    assertEquals(problems(ClaimAnswer("", quote, None, None, "high")), List("claim 1 (): no claim"))
    assert(problems(ClaimAnswer("Tin.", "", None, None, "high")).head.endsWith("no quote"))
    assert(problems(ClaimAnswer("Tin.", quote, None, None, "")).head.contains("is not a confidence"))
    assertEquals(checkExtract(ExtractAnswer(false, "", Nil), "ck", record, 0.9, 10, last = false).left.toOption,
      Some(List("say why the record is not used")))

  private val facts = List(SourceFact("a2019_tin", "Tin", Some(2019), SourceStatus.Current))
  private val ev = evidence("f", 1, facts, List(Extracted("a2019_tin",
    List(Claim("a2019_tin", "c", "q", None, None, Confidence.High, 1.0)), None)), Nil)
  private val corpus = Corpus(Set("a2019_tin"), _ == "a2019_tin", _ => Some(SourceStatus.Current))
  private val body =
    """Tin reached the Aegean by sea [[a2019_tin]].
      |
      |## Disagreements
      |
      |None is recorded [[a2019_tin]].
      |
      |## Open questions
      |
      |Where the tin was mined is open [[a2019_tin]].""".stripMargin

  test("draft: a new article's empty title or summary fails (not quietly the slug); an extension needs neither"):
    def check(a: DraftAnswer, mode: DraftMode = DraftMode.Create, current: Option[String] = None, last: Boolean = false) =
      checkDraft(a, "tin", mode, "concept", current, if current.isDefined then body else "", ev, "f", corpus, last)
    assert(check(DraftAnswer("Tin", "Where tin came from.", body)).isRight, check(DraftAnswer("Tin", "Where tin came from.", body)))
    val ps = check(DraftAnswer("", "", body)).left.toOption.get
    assert(ps.contains("the title is empty — give the new article's title"), ps)
    assert(ps.contains("the summary is empty — say in one or two sentences what the article answers"), ps)
    assertEquals(check(DraftAnswer("Tin", "x", "")).left.toOption.map(_.head), Some("the text is empty"))
    // extending: the title and summary are the article's own
    val more = "More on tin [[a2019_tin]]."
    assert(check(DraftAnswer("", "", more), DraftMode.Extend, Some("Tin")).isRight)

  // ── review-article ────────────────────────────────────────────────────────────────────────────

  import ReviewArticle.*

  private def finding(where: String = "Intro", problem: String = "wordy", severity: String = "minor") =
    FindingAnswer(severity, "language", where, "", problem, "", Nil)

  test("rules reviewer: an empty verdict, or a finding with no where or no problem, fails"):
    def problems(a: RulesAnswer) = checkRules(a, last = false).left.toOption.get
    assertEquals(problems(RulesAnswer("", Nil, "f", Nil, Nil)), List("no verdict given — PASS, MINOR or MAJOR"))
    assertEquals(problems(RulesAnswer("MINOR", List(finding(where = "")), "f", Nil, Nil)).head,
      "finding 1 (wordy): no where stated — give the section heading or the passage's first words")
    assertEquals(problems(RulesAnswer("MINOR", List(finding(problem = " ")), "f", Nil, Nil)).head,
      "finding 1 ( ): no problem stated")
    assert(problems(RulesAnswer("MINOR", List(finding(severity = "")), "f", Nil, Nil)).head.contains("is neither major nor minor"))
    // the quote and fix stay optional
    assert(checkRules(RulesAnswer("MINOR", List(finding()), "f", Nil, Nil), last = false).isRight)

  test("cold reader: an empty verdict, explain-back or finding where fails"):
    def problems(a: ColdReaderAnswer) = checkColdReader(a, last = false).left.toOption.get
    val reader = ReaderFindingAnswer("minor", "term", "", "", "undefined term", "")
    val ps = problems(ColdReaderAnswer("", "", Nil, List(reader), 0, 0, None))
    assert(ps.contains("no explain-back"), ps)
    assert(ps.contains("no verdict given — PASS, MINOR or MAJOR"), ps)
    assert(ps.exists(_.contains("no where stated")), ps)

  test("skeptic: an empty verdict, a finding with no where, a proposal with no evidence fails or is dropped"):
    val a = SkepticAnswer("", List(finding(where = "")), List(AgentProposals.ProposalAnswer("status", "a2019_tin",
      Some("contested"), None, None, "", "q")), 1)
    val ps = checkSkeptic(a, last = false, _ == "a2019_tin", _ => Some(record), 0.9).left.toOption.get
    assert(ps.contains("no verdict given — PASS, MINOR or MAJOR") && ps.exists(_.contains("no where stated")), ps)
    val (_, ws) = checkSkeptic(a.copy(verdict = "MINOR", findings = List(finding())), last = false, _ == "a2019_tin",
      _ => Some(record), 0.9).toOption.get
    assert(ws.exists(_.contains("no evidence given")), ws)

  test("fixer: a replace with null text, an empty anchor, a rejection with no reason fail"):
    import ArticleEdits.{EditAnswer, Op}
    val good = "Tin reached the Aegean by sea [[a2019_tin]].\n\n## Disagreements\n\nNone [[a2019_tin]].\n\n## Open questions\n\nOpen [[a2019_tin]]."
    val s1 = Finding("S1", Reviewer.Skeptic, Severity.Major, "date", "Intro", "", "the date is wrong", "")
    val m = Merged(1, Verdict.Major, List(s1), None, Nil, Nil)
    val bc = BodyCorpus(_ == "a2019_tin", _ == "a2019_tin", _ => Nil)
    def problems(a: FixerAnswer) =
      checkFix(a, m, "tin", good, "concept", bc, _ == "a2019_tin", _ => Some(record), 0.9, last = false).left.toOption.get
    val nullText = problems(FixerAnswer(List(EditAnswer(Op.Replace, "by sea", None)), List("S1"), Nil, Nil, ""))
    assert(nullText.exists(_.endsWith("a replace with no new text — give the text, or use delete to remove the passage")), nullText)
    val noAnchor = problems(FixerAnswer(List(EditAnswer(Op.Replace, "", Some("by ship"))), List("S1"), Nil, Nil, ""))
    assert(noAnchor.exists(_.contains("no anchor")), noAnchor)
    val noReason = problems(FixerAnswer(Nil, Nil, List(RejectionAnswer("S1", " ", "a2019_tin",
      "The Uluburun ship carried ten tons of copper and one ton of tin.")), Nil, ""))
    assert(noReason.contains("rejection of S1 gives no reason — say why the finding is wrong"), noReason)

  // ── the review ledger: checks, the verification pass, a declined minor ────────────────────────

  private val asked = Finding("R1", Reviewer.Rules, Severity.Major, "term", "Intro", "Tin reached the Aegean", "tin is not defined", "")
  private val ledgerPrior = Prior(List(asked), "Tin reached the Aegean by sea [[a2019_tin]].", 0.9)

  test("check: an empty or null id, and a not-fixed check with no note, fail; a fixed check's note stays optional"):
    def problems(c: CheckAnswer) = checkChecks(Reviewer.Rules, ledgerPrior, List(c), last = false)._2
    assert(problems(CheckAnswer("", fixed = true, "Tin reached the Aegean", "glossed")).head.startsWith("a check with no id"))
    assert(problems(CheckAnswer(null, fixed = true, "Tin reached the Aegean", "glossed")).head.startsWith("a check with no id"))
    assertEquals(problems(CheckAnswer("R1", fixed = false, "Tin reached the Aegean", " ")),
      List("check of R1: no note — say why it is not fixed (the fixer reads it)", "R1: no check — say whether it is fixed"))
    assertEquals(problems(CheckAnswer("R1", fixed = false, "Tin reached the Aegean", null)).head,
      "check of R1: no note — say why it is not fixed (the fixer reads it)")
    assertEquals(problems(CheckAnswer("R1", fixed = true, "Tin reached the Aegean", "")), Nil)
    // a null `fixed` is not read as false: the answer does not decode, so the step asks again
    assert(io.circe.parser.decode[CheckAnswer]("""{"id":"R1","fixed":null,"quote":"Tin reached","note":"x"}""").isLeft)

  test("recheck: a not-fixed check with no note, or a finding with no where, fails; empty lessons are dropped"):
    def recheck(a: RecheckAnswer) = checkRecheck(Reviewer.Rules, a, ledgerPrior, "", NoEvidence, last = false)
    val noNote = recheck(RecheckAnswer(List(CheckAnswer("R1", fixed = false, "Tin reached the Aegean", "")))).left.toOption.get
    assert(noNote.contains("check of R1: no note — say why it is not fixed (the fixer reads it)"), noNote)
    val ok = CheckAnswer("R1", fixed = true, "Tin reached the Aegean", "")
    val noWhere = recheck(RecheckAnswer(List(ok), List(finding(where = "")))).left.toOption.get
    assert(noWhere.exists(_.contains("no where stated")), noWhere)
    assertEquals(recheck(RecheckAnswer(List(ok), Nil, List("", " ", "Gloss the term where it first appears."))).map(_._1.lessons),
      Right(List("Gloss the term where it first appears.")))

  test("won't fix: an empty id or reason fails"):
    val good = "Tin reached the Aegean by sea [[a2019_tin]].\n\n## Disagreements\n\nNone [[a2019_tin]].\n\n## Open questions\n\nOpen [[a2019_tin]]."
    val r1 = Finding("R1", Reviewer.Rules, Severity.Minor, "language", "Intro", "", "wordy", "")
    val m = Merged(1, Verdict.Minor, List(r1), None, Nil, Nil)
    val bc = BodyCorpus(_ == "a2019_tin", _ == "a2019_tin", _ => Nil)
    def problems(w: WontFixAnswer) =
      checkFix(FixerAnswer(Nil, Nil, Nil, Nil, "", wontFix = List(w)), m, "tin", good, "concept", bc, _ == "a2019_tin",
        _ => Some(record), 0.9, last = false).left.toOption.get
    assert(problems(WontFixAnswer("", "wordy, but exact")).contains("a won't-fix with no id — give the id of the minor finding you decline"))
    assert(problems(WontFixAnswer(null, "wordy, but exact")).contains("a won't-fix with no id — give the id of the minor finding you decline"))
    assert(problems(WontFixAnswer("R1", " ")).contains("won't fix R1: give the reason"))
    assert(problems(WontFixAnswer("R1", null)).contains("won't fix R1: give the reason"))

  // ── plan-goal and the acquisition sweep ───────────────────────────────────────────────────────

  test("plan: an empty rationale, sub-goal title or query, or a null DOI fails"):
    import PlanGoal.*
    val caps = Caps(3, 3, 3, 80)
    def problems(a: PlanAnswer) = check(a, "g", _ => false, Set.empty, caps, last = false).left.toOption.get
    assertEquals(problems(PlanAnswer("", Nil, List("bronze age tin"), Nil)),
      List("the rationale is empty — say how the plan covers the goal"))
    assert(problems(PlanAnswer("why", List(SubGoalAnswer("tin-trade", "")), Nil, Nil)).contains("sub-goal 'tin-trade' has no title"))
    assert(problems(PlanAnswer("why", Nil, List(""), Nil)).contains("an empty query"))
    assert(problems(PlanAnswer("why", Nil, Nil, List(WorkAnswer(null, "x")))).exists(_.contains("is not a DOI")))

  test("ranking: a pick with an empty DOI fails; its reason stays optional"):
    import Scheduling.*
    val offered = List(Candidate("10.1000/a", "A", Some(2020), Nil, None, false, "q", None))
    assert(checkRanking(Ranking(List(Pick("", "fits"))), offered, last = false).left.exists(_.head.contains("is not one of the listed candidates")))
    assert(checkRanking(Ranking(List(Pick("10.1000/a", ""))), offered, last = false).isRight)
