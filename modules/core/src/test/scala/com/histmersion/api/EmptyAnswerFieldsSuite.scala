package com.histmersion
package api
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.RawJson
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentence
import com.histmersion.api.research.Coverage
import com.histmersion.api.automation.Goal.Rationale as WorkRationale
import com.histmersion.domain.library.workflows.AcquisitionSweepRules.Rationale as PickRationale

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
import com.histmersion.api.library.Citekey
import com.histmersion.api.catalogue.Year
import com.histmersion.domain.CompileArticle
import com.histmersion.domain.AgentProposals
import com.histmersion.domain.Title
import com.histmersion.api.automation.Goal
import com.histmersion.domain.research.Slug
import com.histmersion.domain.library.Doi
import com.histmersion.api.library.WorkRef

/** A strict-schema provider (Codex) sends `""` or `null` for a field it has nothing for, and `null` decodes like an
  * absent field. An answer whose NEEDED field is empty or null must fail its step's check with a problem that says
  * which field — so the step asks again — never pass as an answer (2026-10-08: the probe accepted
  * `{"citekey":"huxley1956_catalogue","title":"","year":null}`). One test per answer type; genuinely optional fields
  * stay optional.
  */
class EmptyAnswerFieldsSuite extends munit.FunSuite:

  private val minRatio = AgentVerify.MinRatio(0.9)
  test("probe: an empty title or citekey, and a null year for a record that has one, are problems"):
    val empty = ProbeAnswer(Citekey("huxley1956_catalogue"), com.histmersion.api.library.Title(""), None)
    assertEquals(
      empty.problems(Some(ProbeAnswer.RecordYear(1956))).map(_.sentence),
      List(
        "the title is empty — copy the source's exact title from get_source",
        "the year is missing — give the source's publication year from get_source"
      )
    )
    assert(
      ProbeAnswer(Citekey(" "), com.histmersion.api.library.Title("A Catalogue"), Some(Year(1956)))
        .problems(Some(ProbeAnswer.RecordYear(1956)))
        .head
        .sentence
        .startsWith("the citekey is empty")
    )
    // a record with no year is rightly answered with null
    assertEquals(
      ProbeAnswer(Citekey("x1900_y"), com.histmersion.api.library.Title("A title"), None).problems(None),
      Nil
    )
    assertEquals(
      ProbeAnswer(Citekey("huxley1956_catalogue"), com.histmersion.api.library.Title("A Catalogue"), Some(Year(1956)))
        .problems(Some(ProbeAnswer.RecordYear(1956))),
      Nil
    )
    // from the wire, as the live probe answered
    val wire = RawJson
      .decode[ProbeAnswer]("""{"citekey":"huxley1956_catalogue","title":"","year":null}""")
      .fold(e => fail(e), identity)
    assertEquals(wire.problems(Some(ProbeAnswer.RecordYear(1956))).size, 2)

  // ── enrich-source ─────────────────────────────────────────────────────────────────────────────

  import EnrichSource.*

  private def classification(
      summary: Classification.Summary = Classification.Summary("Tin by sea."),
      status: com.histmersion.domain.library.Source.Status.Wire =
        com.histmersion.domain.library.Source.Status.Wire("current"),
      evidence: Classification.StatusEvidence = Classification.StatusEvidence("the text says so")
  ) =
    Classification(summary, Nil, Nil, Nil, None, status, evidence)

  test("classify: an empty summary or status fails; a status other than unassessed needs its evidence"):
    val noSummary = checkClassification(
      classification(summary = Classification.Summary(" ")),
      CheckClassificationMaxAbsYear(50000),
      last = Last(false)
    )
    assertEquals(noSummary.left.toOption.map(_.map(_.sentence)), Some(List("the summary is empty")))
    assert(
      checkClassification(
        classification(status = com.histmersion.domain.library.Source.Status.Wire("")),
        CheckClassificationMaxAbsYear(50000),
        last = Last(true)
      ).left
        .exists(_.exists(_.sentence.contains("is not a status")))
    )
    val noEvidence = checkClassification(
      classification(evidence = Classification.StatusEvidence("")),
      CheckClassificationMaxAbsYear(50000),
      last = Last(false)
    )
    assertEquals(
      noEvidence.left.toOption.map(_.map(_.sentence)),
      Some(List("the status evidence is empty — say, from the text, why that status"))
    )
    // unassessed needs none (the schema: "empty if unassessed")
    assert(
      checkClassification(
        classification(
          status = com.histmersion.domain.library.Source.Status.Wire("unassessed"),
          evidence = Classification.StatusEvidence("")
        ),
        CheckClassificationMaxAbsYear(50000),
        last = Last(false)
      ).isRight
    )
    // the last attempt keeps the classification, with a warning
    val (c, ws) = checkClassification(
      classification(evidence = Classification.StatusEvidence("")),
      CheckClassificationMaxAbsYear(50000),
      last = Last(true)
    ).toOption.get
    assertEquals(c.status, Source.Status.Current)
    assert(ws.exists(_.value.contains("status evidence is empty")), ws)

  private val window =
    Window(Window.Index(1), None, Window.Text("The Uluburun ship carried ten tons of copper and one ton of tin."))
  private val open =
    FindingGuards(FindingGuards.MinPlacedShare(0.0), FindingGuards.CenturyGapYears(1000000L), Nil, (_, _, _) => true)
  private def proposal(
      claim: FindingProposal.Claim,
      quote: AgentProposals.Proposal.Quote =
        AgentProposals.Proposal.Quote("The Uluburun ship carried ten tons of copper and one ton of tin."),
      confidence: com.histmersion.domain.library.Confidence.Wire =
        com.histmersion.domain.library.Confidence.Wire("high")
  ) = FindingProposal(claim, quote, None, None, None, None, None, Nil, confidence)

  test("findings: an empty claim, quote or confidence fails, naming the finding"):
    def problems(p: FindingProposal) =
      checkFindings(
        Findings(List(p)),
        window,
        minRatio,
        CheckFindingsMaxAbsYear(50000),
        None,
        Set.empty,
        open,
        last = Last(false)
      ).left.toOption.get
    assert(problems(proposal(FindingProposal.Claim(""))).head.sentence.contains("the claim is empty"))
    assert(
      problems(proposal(FindingProposal.Claim("Tin by sea."), quote = AgentProposals.Proposal.Quote(""))).head.sentence
        .contains("the quote is not in the text")
    )
    assert(
      problems(
        proposal(FindingProposal.Claim("Tin by sea."), confidence = com.histmersion.domain.library.Confidence.Wire(""))
      ).head.sentence.contains("is not a confidence")
    )

  test("notes: empty notes fail"):
    assertEquals(
      checkNotes(Notes(Notes.Value("  ")), _ => true, _ => true).left.map(_.map(_.sentence)),
      Left(List("the notes are empty"))
    )

  // ── compile-article ───────────────────────────────────────────────────────────────────────────

  import CompileArticle.*

  private val record =
    AgentVerify.SourceBody("Summary:\nThe Uluburun ship carried ten tons of copper and one ton of tin.")

  test("extract: an empty claim, quote or confidence fails; an unused record must say why"):
    def problems(c: ClaimAnswer) =
      checkExtract(
        ExtractAnswer(ExtractAnswer.Relevant(true), Coverage.Rationale("tin"), List(c)),
        com.histmersion.domain.library.Citekey("ck"),
        record,
        minRatio,
        MaxClaims(10),
        last = CompileArticle.Last(false)
      ).left.toOption.get
    val quote = AgentProposals.Proposal.Quote("The Uluburun ship carried ten tons of copper and one ton of tin.")
    assertEquals(
      problems(
        ClaimAnswer(ClaimAnswer.Claim(""), quote, None, None, com.histmersion.domain.library.Confidence.Wire("high"))
      ).map(_.sentence),
      List("claim 1 (): no claim")
    )
    assert(
      problems(
        ClaimAnswer(
          ClaimAnswer.Claim("Tin."),
          AgentProposals.Proposal.Quote(""),
          None,
          None,
          com.histmersion.domain.library.Confidence.Wire("high")
        )
      ).head.sentence.endsWith("no quote")
    )
    assert(
      problems(
        ClaimAnswer(ClaimAnswer.Claim("Tin."), quote, None, None, com.histmersion.domain.library.Confidence.Wire(""))
      ).head.sentence.contains("is not a confidence")
    )
    assertEquals(
      checkExtract(
        ExtractAnswer(ExtractAnswer.Relevant(false), Coverage.Rationale(""), Nil),
        com.histmersion.domain.library.Citekey("ck"),
        record,
        minRatio,
        MaxClaims(10),
        last = CompileArticle.Last(false)
      ).left.toOption.map(_.map(_.sentence)),
      Some(List("say why the record is not used"))
    )

  private val facts = List(
    SourceFact(
      com.histmersion.domain.library.Citekey("a2019_tin"),
      Title("Tin"),
      Some(com.histmersion.Year(2019)),
      Source.Status.Current
    )
  )
  private val ev = evidence(
    Goal.Plans.ArticleDraft.Filter("f"),
    com.histmersion.RowCount(1),
    facts,
    List(
      Extracted(
        com.histmersion.domain.library.Citekey("a2019_tin"),
        List(
          Claim(
            com.histmersion.domain.library.Citekey("a2019_tin"),
            ClaimAnswer.Claim("c"),
            AgentProposals.Proposal.Quote("q"),
            None,
            None,
            Confidence.High,
            AgentVerify.Ratio(1.0)
          )
        ),
        None
      )
    ),
    Nil
  )
  private val corpus = Corpus(
    Set(com.histmersion.domain.library.Citekey("a2019_tin")),
    _.value == "a2019_tin",
    _ => Some(Source.Status.Current)
  )
  private val body = Article.Body("""Tin reached the Aegean by sea [[a2019_tin]].
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
        current: Option[Title] = None,
        last: CompileArticle.Last = CompileArticle.Last(false)
    ) =
      checkDraft(
        a,
        Slug("tin"),
        mode,
        Article.Kind.Wire("concept"),
        current,
        if current.isDefined then body else Article.Body(""),
        ev,
        Goal.Plans.ArticleDraft.Filter("f"),
        corpus,
        last
      )
    assert(
      check(DraftAnswer(Title("Tin"), Article.SummaryText("Where tin came from."), body)).isRight,
      check(DraftAnswer(Title("Tin"), Article.SummaryText("Where tin came from."), body))
    )
    val ps = check(DraftAnswer(Title(""), Article.SummaryText(""), body)).left.toOption.get.map(_.sentence)
    assert(ps.contains("the title is empty — give the new article's title"), ps)
    assert(ps.contains("the summary is empty — say in one or two sentences what the article answers"), ps)
    assertEquals(
      check(DraftAnswer(Title("Tin"), Article.SummaryText("x"), Article.Body(""))).left.toOption
        .map(_.head)
        .map(_.sentence),
      Some("the text is empty")
    )
    // extending: the title and summary are the article's own
    val more = Article.Body("More on tin [[a2019_tin]].")
    assert(check(DraftAnswer(Title(""), Article.SummaryText(""), more), DraftMode.Extend, Some(Title("Tin"))).isRight)

  // ── review-article ────────────────────────────────────────────────────────────────────────────

  import ReviewArticle.*

  private def finding(
      where: FindingAnswer.Where = FindingAnswer.Where("Intro"),
      problem: FindingAnswer.Problem = FindingAnswer.Problem("wordy"),
      severity: FindingAnswer.Severity = FindingAnswer.Severity("minor")
  ) =
    FindingAnswer(
      severity,
      FindingAnswer.Kind("language"),
      where,
      FindingAnswer.Quote(""),
      problem,
      FindingAnswer.Fix(""),
      Nil
    )

  test("rules reviewer: an empty verdict, or a finding with no where or no problem, fails"):
    def problems(a: RulesAnswer) = checkRules(a, last = CheckRulesLast(false)).left.toOption.get
    assertEquals(
      problems(RulesAnswer(RulesAnswer.Verdict(""), Nil, RulesAnswer.Filter("f"), Nil, Nil)),
      List("no verdict given — PASS, MINOR or MAJOR")
    )
    assertEquals(
      problems(
        RulesAnswer(
          RulesAnswer.Verdict("MINOR"),
          List(finding(where = FindingAnswer.Where(""))),
          RulesAnswer.Filter("f"),
          Nil,
          Nil
        )
      ).head,
      "finding 1 (wordy): no where stated — give the section heading or the passage's first words"
    )
    assertEquals(
      problems(
        RulesAnswer(
          RulesAnswer.Verdict("MINOR"),
          List(finding(problem = FindingAnswer.Problem(" "))),
          RulesAnswer.Filter("f"),
          Nil,
          Nil
        )
      ).head,
      "finding 1 ( ): no problem stated"
    )
    assert(
      problems(
        RulesAnswer(
          RulesAnswer.Verdict("MINOR"),
          List(finding(severity = FindingAnswer.Severity(""))),
          RulesAnswer.Filter("f"),
          Nil,
          Nil
        )
      ).head
        .contains("is neither major nor minor")
    )
    // the quote and fix stay optional
    assert(
      checkRules(
        RulesAnswer(RulesAnswer.Verdict("MINOR"), List(finding()), RulesAnswer.Filter("f"), Nil, Nil),
        last = CheckRulesLast(false)
      ).isRight
    )

  test("cold reader: an empty verdict, explain-back or finding where fails"):
    def problems(a: ColdReaderAnswer) = checkColdReader(a, last = CheckColdReaderLast(false)).left.toOption.get
    val reader = ReaderFindingAnswer(
      ReaderFindingAnswer.Severity("minor"),
      ReaderFindingAnswer.Kind("term"),
      ReaderFindingAnswer.Where(""),
      ReaderFindingAnswer.Quote(""),
      ReaderFindingAnswer.Problem("undefined term"),
      ReaderFindingAnswer.Fix("")
    )
    val ps = problems(
      ColdReaderAnswer(
        ColdReaderAnswer.Verdict(""),
        ColdReaderAnswer.ExplainBack(""),
        Nil,
        List(reader),
        ColdReaderAnswer.TermsUndefined(0),
        ColdReaderAnswer.OpaqueSentences(0),
        None
      )
    )
    assert(ps.contains("no explain-back"), ps)
    assert(ps.contains("no verdict given — PASS, MINOR or MAJOR"), ps)
    assert(ps.exists(_.contains("no where stated")), ps)

  test("skeptic: an empty verdict, a finding with no where, a proposal with no evidence fails or is dropped"):
    val a = SkepticAnswer(
      SkepticAnswer.Verdict(""),
      List(finding(where = FindingAnswer.Where(""))),
      List(
        AgentProposals.ProposalAnswer(
          AgentProposals.ProposalAnswer.Kind("status"),
          AgentProposals.ProposalAnswer.Citekey("a2019_tin"),
          Some(com.histmersion.domain.library.Source.Status.Wire("contested")),
          None,
          None,
          AgentProposals.ProposalAnswer.Evidence(""),
          AgentProposals.ProposalAnswer.Quote("q")
        )
      ),
      SkepticAnswer.ClaimsChecked(1)
    )
    val ps = checkSkeptic(
      a,
      last = CheckSkepticLast(false),
      _.value == "a2019_tin",
      _ => Some(record),
      minRatio
    ).left.toOption.get
    assert(ps.contains("no verdict given — PASS, MINOR or MAJOR") && ps.exists(_.contains("no where stated")), ps)
    val (_, ws) = checkSkeptic(
      a.modify(_.verdict).setTo(SkepticAnswer.Verdict("MINOR")).modify(_.findings).setTo(List(finding())),
      last = CheckSkepticLast(false),
      _.value == "a2019_tin",
      _ => Some(record),
      minRatio
    ).toOption.get
    assert(ws.exists(_.contains("no evidence given")), ws)

  test("fixer: a replace with null text, an empty anchor, a rejection with no reason fail"):
    import com.histmersion.api.research.Article.Edits.{EditAnswer, Op}
    val good = Article.Body(
      "Tin reached the Aegean by sea [[a2019_tin]].\n\n## Disagreements\n\nNone [[a2019_tin]].\n\n## Open questions\n\nOpen [[a2019_tin]]."
    )
    val s1 = Finding(
      Finding.Id("S1"),
      Reviewer.Skeptic,
      Severity.Major,
      Finding.Kind("date"),
      Finding.Where("Intro"),
      Finding.Quote(""),
      Finding.Problem("the date is wrong"),
      Finding.Fix("")
    )
    val m = Merged(Merged.Round(1), Verdict.Major, List(s1), None, Nil, Nil)
    val bc = BodyCorpus(_.value == "a2019_tin", _.value == "a2019_tin", _ => Nil)
    def problems(a: FixerAnswer) =
      checkFix(
        a,
        m,
        Slug("tin"),
        good,
        Snapshot.Kind("concept"),
        bc,
        _.value == "a2019_tin",
        _ => Some(record),
        minRatio,
        last = LastRound(false)
      ).left.toOption.get
    val nullText = problems(
      FixerAnswer(
        List(EditAnswer(Op.Replace, EditAnswer.Anchor("by sea"), None)),
        List(Finding.Id("S1")),
        Nil,
        Nil,
        FixerAnswer.Notes("")
      )
    )
    assert(
      nullText.exists(_.endsWith("a replace with no new text — give the text, or use delete to remove the passage")),
      nullText
    )
    val noAnchor = problems(
      FixerAnswer(
        List(EditAnswer(Op.Replace, EditAnswer.Anchor(""), Some(Article.Edits.NewText("by ship")))),
        List(Finding.Id("S1")),
        Nil,
        Nil,
        FixerAnswer.Notes("")
      )
    )
    assert(noAnchor.exists(_.contains("no anchor")), noAnchor)
    val noReason = problems(
      FixerAnswer(
        Nil,
        Nil,
        List(
          RejectionAnswer(
            RejectionAnswer.Id("S1"),
            Problem.Unclassified(Legacy.Text(" ")),
            RejectionAnswer.Citekey("a2019_tin"),
            RejectionAnswer.Quote("The Uluburun ship carried ten tons of copper and one ton of tin.")
          )
        ),
        Nil,
        FixerAnswer.Notes("")
      )
    )
    assert(noReason.contains("rejection of S1 gives no reason — say why the finding is wrong"), noReason)

  // ── the review ledger: checks, the verification pass, a declined minor ────────────────────────

  private val asked =
    Finding(
      Finding.Id("R1"),
      Reviewer.Rules,
      Severity.Major,
      Finding.Kind("term"),
      Finding.Where("Intro"),
      Finding.Quote("Tin reached the Aegean"),
      Finding.Problem("tin is not defined"),
      Finding.Fix("")
    )
  private val ledgerPrior = Prior(List(asked), Prior.Body("Tin reached the Aegean by sea [[a2019_tin]]."), minRatio)
  private var noCheckId: CheckAnswer.Id = scala.compiletime.uninitialized
  private var noCheckNote: CheckAnswer.Note = scala.compiletime.uninitialized
  private var noWontFixId: WontFixAnswer.Id = scala.compiletime.uninitialized
  private var noWontFixReason: ReviewArticle.Rationale = scala.compiletime.uninitialized
  private var noWorkDoi: PlanGoal.WorkAnswer.Doi = scala.compiletime.uninitialized

  test("check: an empty or null id, and a not-fixed check with no note, fail; a fixed check's note stays optional"):
    def problems(c: CheckAnswer) = checkChecks(Reviewer.Rules, ledgerPrior, List(c), last = LastRound(false))._2
    assert(
      problems(
        CheckAnswer(
          CheckAnswer.Id(""),
          fixed = CheckAnswer.Fixed(true),
          CheckAnswer.Quote("Tin reached the Aegean"),
          CheckAnswer.Note("glossed")
        )
      ).head.startsWith("a check with no id")
    )
    assert(
      problems(
        CheckAnswer(
          noCheckId,
          fixed = CheckAnswer.Fixed(true),
          CheckAnswer.Quote("Tin reached the Aegean"),
          CheckAnswer.Note("glossed")
        )
      ).head
        .startsWith("a check with no id")
    )
    assertEquals(
      problems(
        CheckAnswer(
          CheckAnswer.Id("R1"),
          fixed = CheckAnswer.Fixed(false),
          CheckAnswer.Quote("Tin reached the Aegean"),
          CheckAnswer.Note(" ")
        )
      ),
      List(
        "check of R1: no note — say why it is not fixed (the fixer reads it)",
        "R1: no check — say whether it is fixed"
      )
    )
    assertEquals(
      problems(
        CheckAnswer(
          CheckAnswer.Id("R1"),
          fixed = CheckAnswer.Fixed(false),
          CheckAnswer.Quote("Tin reached the Aegean"),
          noCheckNote
        )
      ).head,
      "check of R1: no note — say why it is not fixed (the fixer reads it)"
    )
    assertEquals(
      problems(
        CheckAnswer(
          CheckAnswer.Id("R1"),
          fixed = CheckAnswer.Fixed(true),
          CheckAnswer.Quote("Tin reached the Aegean"),
          CheckAnswer.Note("")
        )
      ),
      Nil
    )
    // a null `fixed` is not read as false: the answer does not decode, so the step asks again
    assert(RawJson.decode[CheckAnswer]("""{"id":"R1","fixed":null,"quote":"Tin reached","note":"x"}""").isLeft)

  test("recheck: a not-fixed check with no note, or a finding with no where, fails; empty lessons are dropped"):
    def recheck(a: RecheckAnswer) =
      checkRecheck(Reviewer.Rules, a, ledgerPrior, CheckRecheckEdited(""), NoEvidence, last = CheckRecheckLast(false))
    val noNote =
      recheck(
        RecheckAnswer(
          List(
            CheckAnswer(
              CheckAnswer.Id("R1"),
              fixed = CheckAnswer.Fixed(false),
              CheckAnswer.Quote("Tin reached the Aegean"),
              CheckAnswer.Note("")
            )
          )
        )
      ).left.toOption.get
    assert(noNote.contains("check of R1: no note — say why it is not fixed (the fixer reads it)"), noNote)
    val ok = CheckAnswer(
      CheckAnswer.Id("R1"),
      fixed = CheckAnswer.Fixed(true),
      CheckAnswer.Quote("Tin reached the Aegean"),
      CheckAnswer.Note("")
    )
    val noWhere = recheck(RecheckAnswer(List(ok), List(finding(where = FindingAnswer.Where(""))))).left.toOption.get
    assert(noWhere.exists(_.contains("no where stated")), noWhere)
    assertEquals(
      recheck(
        RecheckAnswer(
          List(ok),
          Nil,
          List(Recheck.Lessons(""), Recheck.Lessons(" "), Recheck.Lessons("Gloss the term where it first appears."))
        )
      ).map(_._1.lessons),
      Right(List("Gloss the term where it first appears."))
    )

  test("won't fix: an empty id or reason fails"):
    val good = Article.Body(
      "Tin reached the Aegean by sea [[a2019_tin]].\n\n## Disagreements\n\nNone [[a2019_tin]].\n\n## Open questions\n\nOpen [[a2019_tin]]."
    )
    val r1 = Finding(
      Finding.Id("R1"),
      Reviewer.Rules,
      Severity.Minor,
      Finding.Kind("language"),
      Finding.Where("Intro"),
      Finding.Quote(""),
      Finding.Problem("wordy"),
      Finding.Fix("")
    )
    val m = Merged(Merged.Round(1), Verdict.Minor, List(r1), None, Nil, Nil)
    val bc = BodyCorpus(_.value == "a2019_tin", _.value == "a2019_tin", _ => Nil)
    def problems(w: WontFixAnswer) =
      checkFix(
        FixerAnswer(Nil, Nil, Nil, Nil, FixerAnswer.Notes(""), wontFix = List(w)),
        m,
        Slug("tin"),
        good,
        Snapshot.Kind("concept"),
        bc,
        _.value == "a2019_tin",
        _ => Some(record),
        minRatio,
        last = LastRound(false)
      ).left.toOption.get
    assert(
      problems(WontFixAnswer(WontFixAnswer.Id(""), ReviewArticle.Rationale("wordy, but exact"))).contains(
        "a won't-fix with no id — give the id of the minor finding you decline"
      )
    )
    assert(
      problems(WontFixAnswer(noWontFixId, ReviewArticle.Rationale("wordy, but exact"))).contains(
        "a won't-fix with no id — give the id of the minor finding you decline"
      )
    )
    assert(
      problems(WontFixAnswer(WontFixAnswer.Id("R1"), ReviewArticle.Rationale(" ")))
        .contains("won't fix R1: give the reason")
    )
    assert(problems(WontFixAnswer(WontFixAnswer.Id("R1"), noWontFixReason)).contains("won't fix R1: give the reason"))

  // ── plan-goal and the acquisition sweep ───────────────────────────────────────────────────────

  test("plan: an empty rationale, sub-goal title or query, or a null DOI fails"):
    import PlanGoal.*
    val caps = Caps(Caps.SubGoals(3), Caps.Queries(3), Caps.Works(3), Caps.QueryChars(80))
    def problems(a: PlanAnswer) = check(
      a,
      com.histmersion.domain.automation.Goal.Slug("g"),
      _ => false,
      Set.empty,
      caps,
      last = LastRound(false)
    ).left.toOption.get
    assertEquals(
      problems(PlanAnswer(PlanAnswer.Rationale(""), Nil, List(PlanAnswer.Queries("bronze age tin")), Nil))
        .map(_.sentence),
      List("the rationale is empty — say how the plan covers the goal")
    )
    assert(
      problems(
        PlanAnswer(
          PlanAnswer.Rationale("why"),
          List(SubGoalAnswer(SubGoalAnswer.Slug("tin-trade"), SubGoalAnswer.Title(""))),
          Nil,
          Nil
        )
      ).map(_.sentence)
        .contains("sub-goal 'tin-trade' has no title")
    )
    assert(
      problems(PlanAnswer(PlanAnswer.Rationale("why"), Nil, List(PlanAnswer.Queries("")), Nil))
        .map(_.sentence)
        .contains("an empty query")
    )
    assert(
      problems(
        PlanAnswer(
          PlanAnswer.Rationale("why"),
          Nil,
          Nil,
          List(WorkAnswer(noWorkDoi, WorkRationale("x")))
        )
      )
        .exists(_.sentence.contains("is not a DOI"))
    )

  test("ranking: a pick with an empty DOI fails; its reason stays optional"):
    import com.histmersion.domain.library.workflows.AcquisitionSweepRules.*
    val offered = List(
      Candidate(
        Doi("10.1000/a"),
        com.histmersion.api.library.Title("A"),
        Some(Year(2020)),
        Nil,
        None,
        WorkRef.IsOa(false),
        Candidate.Query("q"),
        None
      )
    )
    assert(
      checkRanking(
        Ranking(List(Pick(Pick.DoiText(""), PickRationale("fits")))),
        offered,
        last = LastAttempt(false)
      ).left
        .exists(_.head.sentence.contains("is not one of the listed candidates"))
    )
    assert(
      checkRanking(
        Ranking(List(Pick(Pick.DoiText("10.1000/a"), PickRationale("")))),
        offered,
        last = LastAttempt(false)
      ).isRight
    )
