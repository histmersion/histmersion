package com.histmersion.api

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.config.given
import com.histmersion.domain.given

import com.histmersion.api.automation.Agents.ProbeAnswer
import com.histmersion.api.automation.Goal.Plans.DraftMode
import com.histmersion.api.library.Confidence
import com.histmersion.api.library.Source
import com.histmersion.api.research.Article

/** A strict-schema provider (Codex) sends `""` or `null` for a field it has nothing for, and `null` decodes like an
  * absent field. An answer whose NEEDED field is empty or null must fail its step's check with a problem that says
  * which field — so the step asks again — never pass as an answer (2026-10-08: the probe accepted
  * `{"citekey":"huxley1956_catalogue","title":"","year":null}`). One test per answer type; genuinely optional fields
  * stay optional.
  */
class EmptyAnswerFieldsSuite extends munit.FunSuite:

  private val minRatio = AgentVerify.MinRatio(0.9)
  test("probe: an empty title or citekey, and a null year for a record that has one, are problems"):
    val empty = ProbeAnswer(com.histmersion.api.library.Citekey("huxley1956_catalogue"), com.histmersion.api.library.Title(""), None)
    assertEquals(
      empty.problems(Some(com.histmersion.api.automation.Agents.ProbeAnswer.RecordYear(1956))),
      List(
        "the title is empty — copy the source's exact title from get_source",
        "the year is missing — give the source's publication year from get_source"
      )
    )
    assert(ProbeAnswer(com.histmersion.api.library.Citekey(" "), com.histmersion.api.library.Title("A Catalogue"), Some(com.histmersion.api.catalogue.Year(1956))).problems(Some(com.histmersion.api.automation.Agents.ProbeAnswer.RecordYear(1956))).head.value.startsWith("the citekey is empty"))
    // a record with no year is rightly answered with null
    assertEquals(ProbeAnswer(com.histmersion.api.library.Citekey("x1900_y"), com.histmersion.api.library.Title("A title"), None).problems(None), Nil)
    assertEquals(ProbeAnswer(com.histmersion.api.library.Citekey("huxley1956_catalogue"), com.histmersion.api.library.Title("A Catalogue"), Some(com.histmersion.api.catalogue.Year(1956))).problems(Some(com.histmersion.api.automation.Agents.ProbeAnswer.RecordYear(1956))), Nil)
    // from the wire, as the live probe answered
    val wire = io.circe.parser
      .decode[ProbeAnswer]("""{"citekey":"huxley1956_catalogue","title":"","year":null}""")
      .fold(e => fail(e.getMessage), identity)
    assertEquals(wire.problems(Some(com.histmersion.api.automation.Agents.ProbeAnswer.RecordYear(1956))).size, 2)

  // ── enrich-source ─────────────────────────────────────────────────────────────────────────────

  import EnrichSource.*

  private def classification(
      summary: Classification.Summary = Classification.Summary("Tin by sea."),
      status: com.histmersion.domain.library.Source.Status.Wire = com.histmersion.domain.library.Source.Status.Wire("current"),
      evidence: Classification.StatusEvidence = Classification.StatusEvidence("the text says so")
  ) =
    Classification(summary, Nil, Nil, Nil, None, status, evidence)

  test("classify: an empty summary or status fails; a status other than unassessed needs its evidence"):
    val noSummary = checkClassification(classification(summary = Classification.Summary(" ")), com.histmersion.domain.EnrichSource.CheckClassificationMaxAbsYear(50000), last = com.histmersion.domain.EnrichSource.Last(false))
    assertEquals(noSummary.left.toOption, Some(List("the summary is empty")))
    assert(
      checkClassification(classification(status = com.histmersion.domain.library.Source.Status.Wire("")), com.histmersion.domain.EnrichSource.CheckClassificationMaxAbsYear(50000), last = com.histmersion.domain.EnrichSource.Last(true)).left
        .exists(_.exists(_.value.contains("is not a status")))
    )
    val noEvidence = checkClassification(classification(evidence = Classification.StatusEvidence("")), com.histmersion.domain.EnrichSource.CheckClassificationMaxAbsYear(50000), last = com.histmersion.domain.EnrichSource.Last(false))
    assertEquals(
      noEvidence.left.toOption,
      Some(List("the status evidence is empty — say, from the text, why that status"))
    )
    // unassessed needs none (the schema: "empty if unassessed")
    assert(checkClassification(classification(status = com.histmersion.domain.library.Source.Status.Wire("unassessed"), evidence = Classification.StatusEvidence("")), com.histmersion.domain.EnrichSource.CheckClassificationMaxAbsYear(50000), last = com.histmersion.domain.EnrichSource.Last(false)).isRight)
    // the last attempt keeps the classification, with a warning
    val (c, ws) = checkClassification(classification(evidence = Classification.StatusEvidence("")), com.histmersion.domain.EnrichSource.CheckClassificationMaxAbsYear(50000), last = com.histmersion.domain.EnrichSource.Last(true)).toOption.get
    assertEquals(c.status, Source.Status.Current)
    assert(ws.exists(_.value.contains("status evidence is empty")), ws)

  private val window = Window(com.histmersion.domain.EnrichSource.Window.Index(1), None, com.histmersion.domain.EnrichSource.Window.Text("The Uluburun ship carried ten tons of copper and one ton of tin."))
  private val open = FindingGuards(FindingGuards.MinPlacedShare(0.0), com.histmersion.domain.EnrichSource.FindingGuards.CenturyGapYears(1000000L), Nil, (_, _, _) => true)
  private def proposal(
      claim: FindingProposal.Claim,
      quote: AgentProposals.Proposal.Quote = AgentProposals.Proposal.Quote("The Uluburun ship carried ten tons of copper and one ton of tin."),
      confidence: com.histmersion.domain.library.Confidence.Wire = com.histmersion.domain.library.Confidence.Wire("high")
  ) = FindingProposal(claim, quote, None, None, None, None, None, Nil, confidence)

  test("findings: an empty claim, quote or confidence fails, naming the finding"):
    def problems(p: FindingProposal) =
      checkFindings(Findings(List(p)), window, minRatio, com.histmersion.domain.EnrichSource.CheckFindingsMaxAbsYear(50000), None, Set.empty, open, last = com.histmersion.domain.EnrichSource.Last(false)).left.toOption.get
    assert(problems(proposal(FindingProposal.Claim(""))).head.value.contains("the claim is empty"))
    assert(problems(proposal(FindingProposal.Claim("Tin by sea."), quote = AgentProposals.Proposal.Quote(""))).head.value.contains("the quote is not in the text"))
    assert(problems(proposal(FindingProposal.Claim("Tin by sea."), confidence = com.histmersion.domain.library.Confidence.Wire(""))).head.value.contains("is not a confidence"))

  test("notes: empty notes fail"):
    assertEquals(checkNotes(Notes(com.histmersion.domain.EnrichSource.Notes.Value("  ")), _ => true, _ => true), Left(List("the notes are empty")))

  // ── compile-article ───────────────────────────────────────────────────────────────────────────

  import CompileArticle.*

  private val record = AgentVerify.SourceBody("Summary:\nThe Uluburun ship carried ten tons of copper and one ton of tin.")

  test("extract: an empty claim, quote or confidence fails; an unused record must say why"):
    def problems(c: ClaimAnswer) =
      checkExtract(ExtractAnswer(com.histmersion.domain.CompileArticle.ExtractAnswer.Relevant(true), com.histmersion.domain.CompileArticle.ExtractAnswer.Reason("tin"), List(c)), com.histmersion.domain.library.Citekey("ck"), record, minRatio, com.histmersion.domain.CompileArticle.MaxClaims(10), last = com.histmersion.domain.CompileArticle.Last(false)).left.toOption.get
    val quote = AgentProposals.Proposal.Quote("The Uluburun ship carried ten tons of copper and one ton of tin.")
    assertEquals(problems(ClaimAnswer(com.histmersion.domain.CompileArticle.ClaimAnswer.Claim(""), quote, None, None, com.histmersion.domain.library.Confidence.Wire("high"))), List("claim 1 (): no claim"))
    assert(problems(ClaimAnswer(com.histmersion.domain.CompileArticle.ClaimAnswer.Claim("Tin."), com.histmersion.domain.AgentProposals.Proposal.Quote(""), None, None, com.histmersion.domain.library.Confidence.Wire("high"))).head.value.endsWith("no quote"))
    assert(problems(ClaimAnswer(com.histmersion.domain.CompileArticle.ClaimAnswer.Claim("Tin."), quote, None, None, com.histmersion.domain.library.Confidence.Wire(""))).head.value.contains("is not a confidence"))
    assertEquals(
      checkExtract(ExtractAnswer(com.histmersion.domain.CompileArticle.ExtractAnswer.Relevant(false), com.histmersion.domain.CompileArticle.ExtractAnswer.Reason(""), Nil), com.histmersion.domain.library.Citekey("ck"), record, minRatio, com.histmersion.domain.CompileArticle.MaxClaims(10), last = com.histmersion.domain.CompileArticle.Last(false)).left.toOption,
      Some(List("say why the record is not used"))
    )

  private val facts = List(SourceFact(com.histmersion.domain.library.Citekey("a2019_tin"), com.histmersion.domain.Title("Tin"), Some(com.histmersion.Year(2019)), Source.Status.Current))
  private val ev = evidence(
    com.histmersion.api.automation.Goal.Plans.ArticleDraft.Filter("f"),
    com.histmersion.RowCount(1),
    facts,
    List(Extracted(com.histmersion.domain.library.Citekey("a2019_tin"), List(Claim(com.histmersion.domain.library.Citekey("a2019_tin"), com.histmersion.domain.CompileArticle.ClaimAnswer.Claim("c"), com.histmersion.domain.AgentProposals.Proposal.Quote("q"), None, None, Confidence.High, AgentVerify.Ratio(1.0))), None)),
    Nil
  )
  private val corpus = Corpus(Set(com.histmersion.domain.library.Citekey("a2019_tin")), _.value == "a2019_tin", _ => Some(Source.Status.Current))
  private val body = Article.Body(
    """Tin reached the Aegean by sea [[a2019_tin]].
      |
      |## Disagreements
      |
      |None is recorded [[a2019_tin]].
      |
      |## Open questions
      |
      |Where the tin was mined is open [[a2019_tin]].""".stripMargin)

  test("draft: a new article's empty title or summary fails (not quietly the slug); an extension needs neither"):
    def check(
        a: DraftAnswer,
        mode: DraftMode = DraftMode.Create,
        current: Option[com.histmersion.domain.Title] = None,
        last: com.histmersion.domain.CompileArticle.Last = com.histmersion.domain.CompileArticle.Last(false)
    ) =
      checkDraft(a, com.histmersion.domain.research.Slug("tin"), mode, com.histmersion.api.research.Article.Kind.Wire("concept"), current, if current.isDefined then body else com.histmersion.api.research.Article.Body(""), ev, com.histmersion.api.automation.Goal.Plans.ArticleDraft.Filter("f"), corpus, last)
    assert(
      check(DraftAnswer(com.histmersion.domain.Title("Tin"), com.histmersion.api.research.Article.SummaryText("Where tin came from."), body)).isRight,
      check(DraftAnswer(com.histmersion.domain.Title("Tin"), com.histmersion.api.research.Article.SummaryText("Where tin came from."), body))
    )
    val ps = check(DraftAnswer(com.histmersion.domain.Title(""), com.histmersion.api.research.Article.SummaryText(""), body)).left.toOption.get.map(_.value)
    assert(ps.contains("the title is empty — give the new article's title"), ps)
    assert(ps.contains("the summary is empty — say in one or two sentences what the article answers"), ps)
    assertEquals(check(DraftAnswer(com.histmersion.domain.Title("Tin"), com.histmersion.api.research.Article.SummaryText("x"), com.histmersion.api.research.Article.Body(""))).left.toOption.map(_.head), Some("the text is empty"))
    // extending: the title and summary are the article's own
    val more = Article.Body("More on tin [[a2019_tin]].")
    assert(check(DraftAnswer(com.histmersion.domain.Title(""), com.histmersion.api.research.Article.SummaryText(""), more), DraftMode.Extend, Some(com.histmersion.domain.Title("Tin"))).isRight)

  // ── review-article ────────────────────────────────────────────────────────────────────────────

  import ReviewArticle.*

  private def finding(
      where: FindingAnswer.Where = FindingAnswer.Where("Intro"),
      problem: FindingAnswer.Problem = FindingAnswer.Problem("wordy"),
      severity: FindingAnswer.Severity = FindingAnswer.Severity("minor")
  ) =
    FindingAnswer(severity, com.histmersion.domain.ReviewArticle.FindingAnswer.Kind("language"), where, com.histmersion.domain.ReviewArticle.FindingAnswer.Quote(""), problem, com.histmersion.domain.ReviewArticle.FindingAnswer.Fix(""), Nil)

  test("rules reviewer: an empty verdict, or a finding with no where or no problem, fails"):
    def problems(a: RulesAnswer) = checkRules(a, last = com.histmersion.domain.ReviewArticle.CheckRulesLast(false)).left.toOption.get
    assertEquals(problems(RulesAnswer(com.histmersion.domain.ReviewArticle.RulesAnswer.Verdict(""), Nil, com.histmersion.domain.ReviewArticle.RulesAnswer.Filter("f"), Nil, Nil)), List("no verdict given — PASS, MINOR or MAJOR"))
    assertEquals(
      problems(RulesAnswer(com.histmersion.domain.ReviewArticle.RulesAnswer.Verdict("MINOR"), List(finding(where = FindingAnswer.Where(""))), com.histmersion.domain.ReviewArticle.RulesAnswer.Filter("f"), Nil, Nil)).head,
      "finding 1 (wordy): no where stated — give the section heading or the passage's first words"
    )
    assertEquals(
      problems(RulesAnswer(com.histmersion.domain.ReviewArticle.RulesAnswer.Verdict("MINOR"), List(finding(problem = FindingAnswer.Problem(" "))), com.histmersion.domain.ReviewArticle.RulesAnswer.Filter("f"), Nil, Nil)).head,
      "finding 1 ( ): no problem stated"
    )
    assert(
      problems(RulesAnswer(com.histmersion.domain.ReviewArticle.RulesAnswer.Verdict("MINOR"), List(finding(severity = FindingAnswer.Severity(""))), com.histmersion.domain.ReviewArticle.RulesAnswer.Filter("f"), Nil, Nil)).head
        .contains("is neither major nor minor")
    )
    // the quote and fix stay optional
    assert(checkRules(RulesAnswer(com.histmersion.domain.ReviewArticle.RulesAnswer.Verdict("MINOR"), List(finding()), com.histmersion.domain.ReviewArticle.RulesAnswer.Filter("f"), Nil, Nil), last = com.histmersion.domain.ReviewArticle.CheckRulesLast(false)).isRight)

  test("cold reader: an empty verdict, explain-back or finding where fails"):
    def problems(a: ColdReaderAnswer) = checkColdReader(a, last = com.histmersion.domain.ReviewArticle.CheckColdReaderLast(false)).left.toOption.get
    val reader = ReaderFindingAnswer(com.histmersion.domain.ReviewArticle.ReaderFindingAnswer.Severity("minor"), com.histmersion.domain.ReviewArticle.ReaderFindingAnswer.Kind("term"), com.histmersion.domain.ReviewArticle.ReaderFindingAnswer.Where(""), com.histmersion.domain.ReviewArticle.ReaderFindingAnswer.Quote(""), com.histmersion.domain.ReviewArticle.ReaderFindingAnswer.Problem("undefined term"), com.histmersion.domain.ReviewArticle.ReaderFindingAnswer.Fix(""))
    val ps = problems(ColdReaderAnswer(com.histmersion.domain.ReviewArticle.ColdReaderAnswer.Verdict(""), com.histmersion.domain.ReviewArticle.ColdReaderAnswer.ExplainBack(""), Nil, List(reader), com.histmersion.domain.ReviewArticle.ColdReaderAnswer.TermsUndefined(0), com.histmersion.domain.ReviewArticle.ColdReaderAnswer.OpaqueSentences(0), None))
    assert(ps.contains("no explain-back"), ps)
    assert(ps.contains("no verdict given — PASS, MINOR or MAJOR"), ps)
    assert(ps.exists(_.contains("no where stated")), ps)

  test("skeptic: an empty verdict, a finding with no where, a proposal with no evidence fails or is dropped"):
    val a = SkepticAnswer(
      com.histmersion.domain.ReviewArticle.SkepticAnswer.Verdict(""),
      List(finding(where = FindingAnswer.Where(""))),
      List(AgentProposals.ProposalAnswer(com.histmersion.domain.AgentProposals.ProposalAnswer.Kind("status"), com.histmersion.domain.AgentProposals.ProposalAnswer.Citekey("a2019_tin"), Some(com.histmersion.domain.library.Source.Status.Wire("contested")), None, None, com.histmersion.domain.AgentProposals.ProposalAnswer.Evidence(""), com.histmersion.domain.AgentProposals.ProposalAnswer.Quote("q"))),
      com.histmersion.domain.ReviewArticle.SkepticAnswer.ClaimsChecked(1)
    )
    val ps = checkSkeptic(a, last = com.histmersion.domain.ReviewArticle.CheckSkepticLast(false), _.value == "a2019_tin", _ => Some(record), minRatio).left.toOption.get
    assert(ps.contains("no verdict given — PASS, MINOR or MAJOR") && ps.exists(_.contains("no where stated")), ps)
    val (_, ws) = checkSkeptic(
      a.modify(_.verdict).setTo(SkepticAnswer.Verdict("MINOR")).modify(_.findings).setTo(List(finding())),
      last = com.histmersion.domain.ReviewArticle.CheckSkepticLast(false),
      _.value == "a2019_tin",
      _ => Some(record),
      minRatio
    ).toOption.get
    assert(ws.exists(_.contains("no evidence given")), ws)

  test("fixer: a replace with null text, an empty anchor, a rejection with no reason fail"):
    import com.histmersion.api.research.Article.Edits.{EditAnswer, Op}
    val good = Article.Body(
      "Tin reached the Aegean by sea [[a2019_tin]].\n\n## Disagreements\n\nNone [[a2019_tin]].\n\n## Open questions\n\nOpen [[a2019_tin]].")
    val s1 = Finding(com.histmersion.domain.ReviewArticle.Finding.Id("S1"), Reviewer.Skeptic, Severity.Major, com.histmersion.domain.ReviewArticle.Finding.Kind("date"), com.histmersion.domain.ReviewArticle.Finding.Where("Intro"), com.histmersion.domain.ReviewArticle.Finding.Quote(""), com.histmersion.domain.ReviewArticle.Finding.Problem("the date is wrong"), com.histmersion.domain.ReviewArticle.Finding.Fix(""))
    val m = Merged(com.histmersion.domain.ReviewArticle.Merged.Round(1), Verdict.Major, List(s1), None, Nil, Nil)
    val bc = BodyCorpus(_.value == "a2019_tin", _.value == "a2019_tin", _ => Nil)
    def problems(a: FixerAnswer) =
      checkFix(
        a,
        m,
        com.histmersion.domain.research.Slug("tin"),
        good,
        com.histmersion.domain.ReviewArticle.Snapshot.Kind("concept"),
        bc,
        _.value == "a2019_tin",
        _ => Some(record),
        minRatio,
        last = com.histmersion.domain.ReviewArticle.LastRound(false)
      ).left.toOption.get
    val nullText = problems(FixerAnswer(List(EditAnswer(Op.Replace, com.histmersion.api.research.Article.Edits.EditAnswer.Anchor("by sea"), None)), List(com.histmersion.domain.ReviewArticle.Finding.Id("S1")), Nil, Nil, com.histmersion.domain.ReviewArticle.FixerAnswer.Notes("")))
    assert(
      nullText.exists(_.endsWith("a replace with no new text — give the text, or use delete to remove the passage")),
      nullText
    )
    val noAnchor = problems(FixerAnswer(List(EditAnswer(Op.Replace, com.histmersion.api.research.Article.Edits.EditAnswer.Anchor(""), Some(com.histmersion.api.research.Article.Edits.NewText("by ship")))), List(com.histmersion.domain.ReviewArticle.Finding.Id("S1")), Nil, Nil, com.histmersion.domain.ReviewArticle.FixerAnswer.Notes("")))
    assert(noAnchor.exists(_.contains("no anchor")), noAnchor)
    val noReason = problems(
      FixerAnswer(
        Nil,
        Nil,
        List(
          RejectionAnswer(com.histmersion.domain.ReviewArticle.RejectionAnswer.Id("S1"), com.histmersion.domain.ReviewArticle.RejectionAnswer.Reason(" "), com.histmersion.domain.ReviewArticle.RejectionAnswer.Citekey("a2019_tin"), com.histmersion.domain.ReviewArticle.RejectionAnswer.Quote("The Uluburun ship carried ten tons of copper and one ton of tin."))
        ),
        Nil,
        com.histmersion.domain.ReviewArticle.FixerAnswer.Notes("")
      )
    )
    assert(noReason.contains("rejection of S1 gives no reason — say why the finding is wrong"), noReason)

  // ── the review ledger: checks, the verification pass, a declined minor ────────────────────────

  private val asked =
    Finding(com.histmersion.domain.ReviewArticle.Finding.Id("R1"), Reviewer.Rules, Severity.Major, com.histmersion.domain.ReviewArticle.Finding.Kind("term"), com.histmersion.domain.ReviewArticle.Finding.Where("Intro"), com.histmersion.domain.ReviewArticle.Finding.Quote("Tin reached the Aegean"), com.histmersion.domain.ReviewArticle.Finding.Problem("tin is not defined"), com.histmersion.domain.ReviewArticle.Finding.Fix(""))
  private val ledgerPrior = Prior(List(asked), com.histmersion.domain.ReviewArticle.Prior.Body("Tin reached the Aegean by sea [[a2019_tin]]."), minRatio)
  private var noCheckId: CheckAnswer.Id = scala.compiletime.uninitialized
  private var noCheckNote: CheckAnswer.Note = scala.compiletime.uninitialized
  private var noWontFixId: WontFixAnswer.Id = scala.compiletime.uninitialized
  private var noWontFixReason: WontFixAnswer.Reason = scala.compiletime.uninitialized
  private var noWorkDoi: PlanGoal.WorkAnswer.Doi = scala.compiletime.uninitialized

  test("check: an empty or null id, and a not-fixed check with no note, fail; a fixed check's note stays optional"):
    def problems(c: CheckAnswer) = checkChecks(Reviewer.Rules, ledgerPrior, List(c), last = com.histmersion.domain.ReviewArticle.LastRound(false))._2
    assert(
      problems(CheckAnswer(com.histmersion.domain.ReviewArticle.CheckAnswer.Id(""), fixed = com.histmersion.domain.ReviewArticle.CheckAnswer.Fixed(true), com.histmersion.domain.ReviewArticle.CheckAnswer.Quote("Tin reached the Aegean"), com.histmersion.domain.ReviewArticle.CheckAnswer.Note("glossed"))).head.startsWith("a check with no id")
    )
    assert(
      problems(CheckAnswer(noCheckId, fixed = com.histmersion.domain.ReviewArticle.CheckAnswer.Fixed(true), com.histmersion.domain.ReviewArticle.CheckAnswer.Quote("Tin reached the Aegean"), com.histmersion.domain.ReviewArticle.CheckAnswer.Note("glossed"))).head
        .startsWith("a check with no id")
    )
    assertEquals(
      problems(CheckAnswer(com.histmersion.domain.ReviewArticle.CheckAnswer.Id("R1"), fixed = com.histmersion.domain.ReviewArticle.CheckAnswer.Fixed(false), com.histmersion.domain.ReviewArticle.CheckAnswer.Quote("Tin reached the Aegean"), com.histmersion.domain.ReviewArticle.CheckAnswer.Note(" "))),
      List(
        "check of R1: no note — say why it is not fixed (the fixer reads it)",
        "R1: no check — say whether it is fixed"
      )
    )
    assertEquals(
      problems(CheckAnswer(com.histmersion.domain.ReviewArticle.CheckAnswer.Id("R1"), fixed = com.histmersion.domain.ReviewArticle.CheckAnswer.Fixed(false), com.histmersion.domain.ReviewArticle.CheckAnswer.Quote("Tin reached the Aegean"), noCheckNote)).head,
      "check of R1: no note — say why it is not fixed (the fixer reads it)"
    )
    assertEquals(problems(CheckAnswer(com.histmersion.domain.ReviewArticle.CheckAnswer.Id("R1"), fixed = com.histmersion.domain.ReviewArticle.CheckAnswer.Fixed(true), com.histmersion.domain.ReviewArticle.CheckAnswer.Quote("Tin reached the Aegean"), com.histmersion.domain.ReviewArticle.CheckAnswer.Note(""))), Nil)
    // a null `fixed` is not read as false: the answer does not decode, so the step asks again
    assert(io.circe.parser.decode[CheckAnswer]("""{"id":"R1","fixed":null,"quote":"Tin reached","note":"x"}""").isLeft)

  test("recheck: a not-fixed check with no note, or a finding with no where, fails; empty lessons are dropped"):
    def recheck(a: RecheckAnswer) = checkRecheck(Reviewer.Rules, a, ledgerPrior, com.histmersion.domain.ReviewArticle.CheckRecheckEdited(""), NoEvidence, last = com.histmersion.domain.ReviewArticle.CheckRecheckLast(false))
    val noNote =
      recheck(RecheckAnswer(List(CheckAnswer(com.histmersion.domain.ReviewArticle.CheckAnswer.Id("R1"), fixed = com.histmersion.domain.ReviewArticle.CheckAnswer.Fixed(false), com.histmersion.domain.ReviewArticle.CheckAnswer.Quote("Tin reached the Aegean"), com.histmersion.domain.ReviewArticle.CheckAnswer.Note(""))))).left.toOption.get
    assert(noNote.contains("check of R1: no note — say why it is not fixed (the fixer reads it)"), noNote)
    val ok = CheckAnswer(com.histmersion.domain.ReviewArticle.CheckAnswer.Id("R1"), fixed = com.histmersion.domain.ReviewArticle.CheckAnswer.Fixed(true), com.histmersion.domain.ReviewArticle.CheckAnswer.Quote("Tin reached the Aegean"), com.histmersion.domain.ReviewArticle.CheckAnswer.Note(""))
    val noWhere = recheck(RecheckAnswer(List(ok), List(finding(where = "")))).left.toOption.get
    assert(noWhere.exists(_.contains("no where stated")), noWhere)
    assertEquals(
      recheck(RecheckAnswer(List(ok), Nil, List(com.histmersion.domain.ReviewArticle.Recheck.Lessons(""), com.histmersion.domain.ReviewArticle.Recheck.Lessons(" "), com.histmersion.domain.ReviewArticle.Recheck.Lessons("Gloss the term where it first appears.")))).map(_._1.lessons),
      Right(List("Gloss the term where it first appears."))
    )

  test("won't fix: an empty id or reason fails"):
    val good = Article.Body(
      "Tin reached the Aegean by sea [[a2019_tin]].\n\n## Disagreements\n\nNone [[a2019_tin]].\n\n## Open questions\n\nOpen [[a2019_tin]].")
    val r1 = Finding(com.histmersion.domain.ReviewArticle.Finding.Id("R1"), Reviewer.Rules, Severity.Minor, com.histmersion.domain.ReviewArticle.Finding.Kind("language"), com.histmersion.domain.ReviewArticle.Finding.Where("Intro"), com.histmersion.domain.ReviewArticle.Finding.Quote(""), com.histmersion.domain.ReviewArticle.Finding.Problem("wordy"), com.histmersion.domain.ReviewArticle.Finding.Fix(""))
    val m = Merged(com.histmersion.domain.ReviewArticle.Merged.Round(1), Verdict.Minor, List(r1), None, Nil, Nil)
    val bc = BodyCorpus(_.value == "a2019_tin", _.value == "a2019_tin", _ => Nil)
    def problems(w: WontFixAnswer) =
      checkFix(
        FixerAnswer(Nil, Nil, Nil, Nil, com.histmersion.domain.ReviewArticle.FixerAnswer.Notes(""), wontFix = List(w)),
        m,
        com.histmersion.domain.research.Slug("tin"),
        good,
        com.histmersion.domain.ReviewArticle.Snapshot.Kind("concept"),
        bc,
        _.value == "a2019_tin",
        _ => Some(record),
        minRatio,
        last = com.histmersion.domain.ReviewArticle.LastRound(false)
      ).left.toOption.get
    assert(
      problems(WontFixAnswer(com.histmersion.domain.ReviewArticle.WontFixAnswer.Id(""), com.histmersion.domain.ReviewArticle.WontFixAnswer.Reason("wordy, but exact"))).contains(
        "a won't-fix with no id — give the id of the minor finding you decline"
      )
    )
    assert(
      problems(WontFixAnswer(noWontFixId, com.histmersion.domain.ReviewArticle.WontFixAnswer.Reason("wordy, but exact"))).contains(
        "a won't-fix with no id — give the id of the minor finding you decline"
      )
    )
    assert(problems(WontFixAnswer(com.histmersion.domain.ReviewArticle.WontFixAnswer.Id("R1"), com.histmersion.domain.ReviewArticle.WontFixAnswer.Reason(" "))).contains("won't fix R1: give the reason"))
    assert(problems(WontFixAnswer(com.histmersion.domain.ReviewArticle.WontFixAnswer.Id("R1"), noWontFixReason)).contains("won't fix R1: give the reason"))

  // ── plan-goal and the acquisition sweep ───────────────────────────────────────────────────────

  test("plan: an empty rationale, sub-goal title or query, or a null DOI fails"):
    import PlanGoal.*
    val caps = Caps(com.histmersion.domain.PlanGoal.Caps.SubGoals(3), com.histmersion.domain.PlanGoal.Caps.Queries(3), com.histmersion.domain.PlanGoal.Caps.Works(3), com.histmersion.domain.PlanGoal.Caps.QueryChars(80))
    def problems(a: PlanAnswer) = check(a, com.histmersion.domain.automation.Goal.Slug("g"), _ => false, Set.empty, caps, last = com.histmersion.domain.PlanGoal.LastRound(false)).left.toOption.get
    assertEquals(
      problems(PlanAnswer(com.histmersion.domain.PlanGoal.PlanAnswer.Rationale(""), Nil, List(com.histmersion.domain.PlanGoal.PlanAnswer.Queries("bronze age tin")), Nil)),
      List("the rationale is empty — say how the plan covers the goal")
    )
    assert(
      problems(PlanAnswer(com.histmersion.domain.PlanGoal.PlanAnswer.Rationale("why"), List(SubGoalAnswer(com.histmersion.domain.PlanGoal.SubGoalAnswer.Slug("tin-trade"), com.histmersion.domain.PlanGoal.SubGoalAnswer.Title(""))), Nil, Nil))
        .contains("sub-goal 'tin-trade' has no title")
    )
    assert(problems(PlanAnswer(com.histmersion.domain.PlanGoal.PlanAnswer.Rationale("why"), Nil, List(com.histmersion.domain.PlanGoal.PlanAnswer.Queries("")), Nil)).contains("an empty query"))
    assert(problems(PlanAnswer(com.histmersion.domain.PlanGoal.PlanAnswer.Rationale("why"), Nil, Nil, List(WorkAnswer(noWorkDoi, com.histmersion.domain.PlanGoal.WorkAnswer.Why("x"))))).exists(_.contains("is not a DOI")))

  test("ranking: a pick with an empty DOI fails; its reason stays optional"):
    import com.histmersion.domain.library.workflows.AcquisitionSweepRules.*
    val offered = List(Candidate(com.histmersion.domain.library.Doi("10.1000/a"), com.histmersion.api.library.Title("A"), Some(com.histmersion.api.catalogue.Year(2020)), Nil, None, com.histmersion.api.library.WorkRef.IsOa(false), com.histmersion.domain.library.workflows.AcquisitionSweepRules.Candidate.Query("q"), None))
    assert(
      checkRanking(Ranking(List(Pick(com.histmersion.domain.library.workflows.AcquisitionSweepRules.Pick.DoiText(""), com.histmersion.domain.library.workflows.AcquisitionSweepRules.Pick.Why("fits")))), offered, last = com.histmersion.domain.library.workflows.AcquisitionSweepRules.LastAttempt(false)).left
        .exists(_.head.value.contains("is not one of the listed candidates"))
    )
    assert(checkRanking(Ranking(List(Pick(com.histmersion.domain.library.workflows.AcquisitionSweepRules.Pick.DoiText("10.1000/a"), com.histmersion.domain.library.workflows.AcquisitionSweepRules.Pick.Why("")))), offered, last = com.histmersion.domain.library.workflows.AcquisitionSweepRules.LastAttempt(false)).isRight)
