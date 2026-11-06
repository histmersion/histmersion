package com.histmersion.domain

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import com.histmersion.domain.AgentProposals.ProposalAnswer
import com.histmersion.domain.ReviewArticle.*
import com.histmersion.domain.ReviewArticle.Finding
import com.histmersion.api.research.{CitedSource, Coverage}
import com.histmersion.api.library.Source
import com.histmersion.api.research.Article
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.research.Slug
import com.histmersion.domain.automation.agents.ToolUse

/** The pure half of `review-article`: mechanical checks, reviewer validation, the merge (dedupe, worst verdict,
  * escalation), the gate, the rounds, the fixer's validation, the bookkeeping lines.
  */
class ReviewArticleSuite extends munit.FunSuite:

  private val Good =
    """Tin reached the Aegean by sea [[knapp2016_tin]].
      |
      |## Disagreements
      |
      |Two dates are argued [[knapp2016_tin]].
      |
      |## Open questions
      |
      |The source of the tin is open [[knapp2016_tin]].
      |
      |## Not used
      |
      |- none
      |""".stripMargin

  private val known = Set("knapp2016_tin", "sea_peoples", "atlantis")
  private val isTarget = (t: Slug) => known(t.value)
  private val isCk = (t: Citekey) => t.value == "knapp2016_tin"
  private val record = Map(Citekey("knapp2016_tin") -> AgentVerify.SourceBody("The Uluburun ship carried ten tons of copper and one ton of tin."))
  private val minRatio = AgentVerify.MinRatio(0.9)

  private def fa(sev: FindingAnswer.Severity, where: FindingAnswer.Where, problem: FindingAnswer.Problem, quote: FindingAnswer.Quote = FindingAnswer.Quote("")) =
    FindingAnswer(sev, FindingAnswer.Kind("synth"), where, quote, problem, FindingAnswer.Fix("fix it"))

  test("mechanical: a clean article has no findings"):
    assertEquals(mechanical(com.histmersion.domain.research.Slug("a"), com.histmersion.domain.ReviewArticle.Snapshot.Kind("concept"), Article.Body(Good), isTarget), Nil)

  test("mechanical: uncited paragraph, escaped links, Sources cited, missing sections, dangling link, filler"):
    val body =
      "It is important to note that tin moved.\n\nSee \\[\\[x\\]\\] and [[nobody2020_x]].\n\n## Sources cited\n\n- a\n"
    val fs = mechanical(com.histmersion.domain.research.Slug("a"), com.histmersion.domain.ReviewArticle.Snapshot.Kind("concept"), Article.Body(body), isTarget)
    assert(fs.forall(_.reviewer == Reviewer.Mechanical))
    assertEquals(fs.map(_.id), fs.indices.map(i => s"M${i + 1}").toList)
    val problems = fs.map(_.problem.value)
    assert(problems.exists(_.contains("banned filler")), problems)
    assert(problems.exists(_.contains("no [[citekey]]")), problems)
    assert(problems.exists(_.contains("escaped link brackets")), problems)
    assert(problems.exists(_.contains("Sources cited")), problems)
    assert(problems.exists(_.contains("'disagreements'")), problems)
    assert(problems.exists(_.contains("'unresolved' or 'open questions'")), problems)
    assert(problems.exists(_.contains("[[nobody2020_x]] is neither")), problems)
    // filler is minor; a missing section is major
    assert(fs.find(_.problem.value.contains("filler")).exists(_.severity == Severity.Minor))
    assert(fs.find(_.problem.value.contains("'not used'")).exists(_.major))

  test("mechanical: an index article needs no section set; a link to an article is not a citation"):
    assertEquals(mechanical(com.histmersion.domain.research.Slug("i"), com.histmersion.domain.ReviewArticle.Snapshot.Kind("index"), com.histmersion.domain.ReviewArticle.MechanicalBody("See [[knapp2016_tin]]."), isTarget), Nil)
    // the lint rule's own test: a target shaped like a citekey (a year or an underscore) cites
    val fs = mechanical(com.histmersion.domain.research.Slug("a"), com.histmersion.domain.ReviewArticle.Snapshot.Kind("index"), com.histmersion.domain.ReviewArticle.MechanicalBody("See [[atlantis]]."), isTarget)
    assertEquals(fs.map(_.problem), List("a paragraph with no [[citekey]]"))

  test("a reviewer's findings get code ids; a bad severity is sent back, then dropped on the last attempt"):
    val a = RulesAnswer(com.histmersion.domain.ReviewArticle.RulesAnswer.Verdict("MAJOR"), List(fa(FindingAnswer.Severity("major"), FindingAnswer.Where("Intro"), FindingAnswer.Problem("bridge claim")), fa(FindingAnswer.Severity("severe"), FindingAnswer.Where("x"), FindingAnswer.Problem("y"))), com.histmersion.domain.ReviewArticle.RulesAnswer.Filter("f"), Nil, Nil)
    assert(checkRules(a, last = com.histmersion.domain.ReviewArticle.CheckRulesLast(false)).left.exists(_.exists(_.contains("'severe'"))))
    val (r, ws) = checkRules(a, last = com.histmersion.domain.ReviewArticle.CheckRulesLast(true)).toOption.get
    assertEquals(r.findings.map(f => (f.id, f.severity)), List(("R1", Severity.Major)))
    assertEquals(r.verdict, Verdict.Major)
    assert(ws.exists(_.contains("dropped")), ws)

  test("a verdict worse than the findings is a finding left out; a milder one is corrected"):
    val tooBad = RulesAnswer(com.histmersion.domain.ReviewArticle.RulesAnswer.Verdict("MAJOR"), List(fa(FindingAnswer.Severity("minor"), FindingAnswer.Where("x"), FindingAnswer.Problem("y"))), com.histmersion.domain.ReviewArticle.RulesAnswer.Filter("f"), Nil, Nil)
    assert(checkRules(tooBad, last = com.histmersion.domain.ReviewArticle.CheckRulesLast(false)).left.exists(_.exists(_.contains("no finding is major"))))
    assertEquals(checkRules(tooBad, last = com.histmersion.domain.ReviewArticle.CheckRulesLast(true)).map(_._1.verdict), Right(Verdict.Minor))
    val tooMild = RulesAnswer(com.histmersion.domain.ReviewArticle.RulesAnswer.Verdict("PASS"), List(fa(FindingAnswer.Severity("major"), FindingAnswer.Where("x"), FindingAnswer.Problem("y"))), com.histmersion.domain.ReviewArticle.RulesAnswer.Filter("f"), Nil, Nil)
    assertEquals(checkRules(tooMild, last = com.histmersion.domain.ReviewArticle.CheckRulesLast(false)).map(_._1.verdict), Right(Verdict.Major))

  test("cold reader: code adds an empty explain-back slot as MAJOR and more than 8 undefined terms as a MINOR count"):
    val a = ColdReaderAnswer(
      com.histmersion.domain.ReviewArticle.ColdReaderAnswer.Verdict("MINOR"),
      com.histmersion.domain.ReviewArticle.ColdReaderAnswer.ExplainBack("It is about tin."),
      List(com.histmersion.domain.ReviewArticle.ExplainSlotsValue("disagreement")),
      List(ReaderFindingAnswer(com.histmersion.domain.ReviewArticle.ReaderFindingAnswer.Severity("minor"), com.histmersion.domain.ReviewArticle.ReaderFindingAnswer.Kind("term"), com.histmersion.domain.ReviewArticle.ReaderFindingAnswer.Where("Intro"), com.histmersion.domain.ReviewArticle.ReaderFindingAnswer.Quote(""), com.histmersion.domain.ReviewArticle.ReaderFindingAnswer.Problem("term X"), com.histmersion.domain.ReviewArticle.ReaderFindingAnswer.Fix("define it"))),
      com.histmersion.domain.ReviewArticle.ColdReaderAnswer.TermsUndefined(9),
      com.histmersion.domain.ReviewArticle.ColdReaderAnswer.OpaqueSentences(0)
    )
    val (r, _) = checkColdReader(a, last = com.histmersion.domain.ReviewArticle.CheckColdReaderLast(false)).toOption.get
    assertEquals(r.verdict, Verdict.Major)
    assertEquals(r.findings.map(_.id), List("C1", "C2", "C3"))
    assert(r.findings.exists(f => f.major && f.problem.value.contains("'disagreement'")))
    assertEquals(
      r.findings.filter(TermCount.is).map(f => (f.severity, f.kind, f.problem)),
      List((Severity.Minor, "term-count", "9 terms undefined (count only; the individual findings carry the severity)"))
    )
    assertEquals(r.emptySlots, List("disagreement"))
    assert(checkColdReader(a.modify(_.explainBack).setTo(ColdReaderAnswer.ExplainBack(" ")), last = com.histmersion.domain.ReviewArticle.CheckColdReaderLast(false)).isLeft)
    assert(checkColdReader(a.modify(_.emptySlots).setTo(List(ExplainSlotsValue("why"))), last = com.histmersion.domain.ReviewArticle.CheckColdReaderLast(false)).isLeft)

  test("#1461 cold reader: 'none…' and blank empty slots mean none; prose naming a slot is that slot"):
    val none = ExplainSlotsValue("none: question, answer, disagreement and open questions are all stated in the text")
    assertEquals(emptySlotsOf(List(none)), (Nil, Nil))
    assertEquals(emptySlotsOf(List(com.histmersion.domain.ReviewArticle.ExplainSlotsValue(""), com.histmersion.domain.ReviewArticle.ExplainSlotsValue("  "), com.histmersion.domain.ReviewArticle.ExplainSlotsValue("N/A"), com.histmersion.domain.ReviewArticle.ExplainSlotsValue("No empty slots"), com.histmersion.domain.ReviewArticle.ExplainSlotsValue("all four are filled"), com.histmersion.domain.ReviewArticle.ExplainSlotsValue("-"))), (Nil, Nil))
    assertEquals(
      emptySlotsOf(List(com.histmersion.domain.ReviewArticle.ExplainSlotsValue("Open questions"), com.histmersion.domain.ReviewArticle.ExplainSlotsValue("disagreements: nobody is named"), com.histmersion.domain.ReviewArticle.ExplainSlotsValue("answer"), com.histmersion.domain.ReviewArticle.ExplainSlotsValue("open"))),
      (List("open", "disagreement", "answer"), Nil)
    )
    assertEquals(emptySlotsOf(List(com.histmersion.domain.ReviewArticle.ExplainSlotsValue("why"))), (Nil, List("why")))
    val a = ColdReaderAnswer(com.histmersion.domain.ReviewArticle.ColdReaderAnswer.Verdict("PASS"), com.histmersion.domain.ReviewArticle.ColdReaderAnswer.ExplainBack("It is about tin."), List(none), Nil, com.histmersion.domain.ReviewArticle.ColdReaderAnswer.TermsUndefined(0), com.histmersion.domain.ReviewArticle.ColdReaderAnswer.OpaqueSentences(0))
    val (r, warnings) = checkColdReader(a, last = com.histmersion.domain.ReviewArticle.CheckColdReaderLast(false)).fold(ps => fail(ps.mkString("; ")), identity)
    assertEquals((r.verdict, r.emptySlots, r.findings), (Verdict.Pass, Nil, Nil))
    assertEquals(warnings, Nil)

  test("cold reader: the term count is MINOR at any count, and none at 8 or fewer"):
    def countOf(n: ColdReaderAnswer.TermsUndefined) =
      checkColdReader(
        ColdReaderAnswer(com.histmersion.domain.ReviewArticle.ColdReaderAnswer.Verdict("PASS"), com.histmersion.domain.ReviewArticle.ColdReaderAnswer.ExplainBack("It is about tin."), Nil, Nil, n, com.histmersion.domain.ReviewArticle.ColdReaderAnswer.OpaqueSentences(0)),
        last = com.histmersion.domain.ReviewArticle.CheckColdReaderLast(false)
      ).toOption.get._1.findings
    assertEquals(countOf(ColdReaderAnswer.TermsUndefined(8)), Nil)
    for n <- List(9, 27, 500).map(ColdReaderAnswer.TermsUndefined(_)) do
      assertEquals(
        countOf(n).map(f => (f.severity, f.problem.value.takeWhile(_ != ' '))),
        List((Severity.Minor, n.toString)),
        n
      )

  test("cold reader: an article whose only MAJOR was the term count is MINOR, and PASS without it"):
    val named = List(ReaderFindingAnswer(com.histmersion.domain.ReviewArticle.ReaderFindingAnswer.Severity("minor"), com.histmersion.domain.ReviewArticle.ReaderFindingAnswer.Kind("term"), com.histmersion.domain.ReviewArticle.ReaderFindingAnswer.Where("Intro"), com.histmersion.domain.ReviewArticle.ReaderFindingAnswer.Quote("Hittite"), com.histmersion.domain.ReviewArticle.ReaderFindingAnswer.Problem("Hittite is not defined"), com.histmersion.domain.ReviewArticle.ReaderFindingAnswer.Fix("define it")))
    // the reader's instructions may still call the count major: the stated verdict is corrected, not sent back
    val (r, ws) =
      checkColdReader(ColdReaderAnswer(com.histmersion.domain.ReviewArticle.ColdReaderAnswer.Verdict("MAJOR"), com.histmersion.domain.ReviewArticle.ColdReaderAnswer.ExplainBack("It is about tin."), Nil, named, com.histmersion.domain.ReviewArticle.ColdReaderAnswer.TermsUndefined(27), com.histmersion.domain.ReviewArticle.ColdReaderAnswer.OpaqueSentences(0)), last = com.histmersion.domain.ReviewArticle.CheckColdReaderLast(false)).toOption.get
    assertEquals(r.verdict, Verdict.Minor)
    assertEquals(ws, Nil)
    assertEquals(r.findings.map(_.severity), List(Severity.Minor, Severity.Minor))
    assertEquals(merge(com.histmersion.domain.ReviewArticle.MergeRound(1), Nil, List(r), Nil).verdict, Verdict.Minor)
    assertEquals(
      checkColdReader(ColdReaderAnswer(com.histmersion.domain.ReviewArticle.ColdReaderAnswer.Verdict("PASS"), com.histmersion.domain.ReviewArticle.ColdReaderAnswer.ExplainBack("It is about tin."), Nil, Nil, com.histmersion.domain.ReviewArticle.ColdReaderAnswer.TermsUndefined(3), com.histmersion.domain.ReviewArticle.ColdReaderAnswer.OpaqueSentences(0)), last = com.histmersion.domain.ReviewArticle.CheckColdReaderLast(false)).map(_._1.verdict),
      Right(Verdict.Pass)
    )
    // a named term the reader marks MAJOR keeps its severity
    val major = checkColdReader(
      ColdReaderAnswer(com.histmersion.domain.ReviewArticle.ColdReaderAnswer.Verdict("MAJOR"), com.histmersion.domain.ReviewArticle.ColdReaderAnswer.ExplainBack("It is about tin."), Nil, named.map(_.modify(_.severity).setTo(ReaderFindingAnswer.Severity("major"))), com.histmersion.domain.ReviewArticle.ColdReaderAnswer.TermsUndefined(27), com.histmersion.domain.ReviewArticle.ColdReaderAnswer.OpaqueSentences(0)),
      last = com.histmersion.domain.ReviewArticle.CheckColdReaderLast(false)
    ).toOption.get._1
    assertEquals(major.verdict, Verdict.Major)
    assertEquals(
      major.findings.map(f => (f.kind, f.severity)),
      List(("term", Severity.Major), ("term-count", Severity.Minor))
    )
    // a MAJOR verdict with no major finding and no count is still a finding left out
    assert(checkColdReader(ColdReaderAnswer(com.histmersion.domain.ReviewArticle.ColdReaderAnswer.Verdict("MAJOR"), com.histmersion.domain.ReviewArticle.ColdReaderAnswer.ExplainBack("It is about tin."), Nil, named, com.histmersion.domain.ReviewArticle.ColdReaderAnswer.TermsUndefined(3), com.histmersion.domain.ReviewArticle.ColdReaderAnswer.OpaqueSentences(0)), last = com.histmersion.domain.ReviewArticle.CheckColdReaderLast(false)).isLeft)

  test("TermCount.is: the count finding, and the MAJOR one earlier runs stored; nothing else"):
    val legacy = Finding(
      com.histmersion.domain.ReviewArticle.Finding.Id("C4"),
      Reviewer.ColdReader,
      Severity.Major,
      com.histmersion.domain.ReviewArticle.Finding.Kind("term"),
      com.histmersion.domain.ReviewArticle.Finding.Where("explain-back"),
      com.histmersion.domain.ReviewArticle.Finding.Quote(""),
      com.histmersion.domain.ReviewArticle.Finding.Problem("27 terms are undefined (more than 8)"),
      com.histmersion.domain.ReviewArticle.Finding.Fix("define each on first use, and add a Glossary")
    )
    assert(TermCount.is(legacy))
    assert(TermCount.is(legacy.modify(_.kind).setTo(Finding.Kind("term-count")).modify(_.problem).setTo(TermCount.problem(com.histmersion.domain.ReviewArticle.ColdReaderAnswer.TermsUndefined(12)))))
    assert(!TermCount.is(legacy.modify(_.reviewer).setTo(Reviewer.Rules)))
    assert(!TermCount.is(legacy.modify(_.problem).setTo(Finding.Problem("Hittite is undefined"))))
    assert(!TermCount.is(legacy.modify(_.where).setTo(Finding.Where("Intro"))))
    assert(!TermCount.is(legacy.modify(_.quote).setTo(Finding.Quote("27 terms"))))

  test("skeptic: proposals are verified — quote in the record, vocabulary, citekeys — the rest dropped"):
    val ok = ProposalAnswer(
      com.histmersion.domain.AgentProposals.ProposalAnswer.Kind("status"),
      com.histmersion.domain.AgentProposals.ProposalAnswer.Citekey("knapp2016_tin"),
      Some(com.histmersion.domain.library.Source.Status.Wire("contested")),
      None,
      None,
      com.histmersion.domain.AgentProposals.ProposalAnswer.Evidence("a rebuttal"),
      com.histmersion.domain.AgentProposals.ProposalAnswer.Quote("ten tons of copper and one ton of tin")
    )
    val bad = ProposalAnswer(
      com.histmersion.domain.AgentProposals.ProposalAnswer.Kind("status"),
      com.histmersion.domain.AgentProposals.ProposalAnswer.Citekey("knapp2016_tin"),
      Some(com.histmersion.domain.library.Source.Status.Wire("contested")),
      None,
      None,
      com.histmersion.domain.AgentProposals.ProposalAnswer.Evidence("a rebuttal"),
      com.histmersion.domain.AgentProposals.ProposalAnswer.Quote("Cornish tin went east every summer")
    )
    val (r, ws) =
      checkSkeptic(SkepticAnswer(com.histmersion.domain.ReviewArticle.SkepticAnswer.Verdict("PASS"), Nil, List(ok, bad), com.histmersion.domain.ReviewArticle.SkepticAnswer.ClaimsChecked(3)), last = com.histmersion.domain.ReviewArticle.CheckSkepticLast(false), isCk, record.get, minRatio).toOption.get
    assertEquals(r.proposals.map(_.status), List(Some(Source.Status.Contested)))
    assert(ws.exists(_.contains("not in the record")), ws)

  test("merge: dedupe by where + problem, worst verdict, C and S on the same passage both kept"):
    val m1 = Finding(com.histmersion.domain.ReviewArticle.Finding.Id("M1"), Reviewer.Mechanical, Severity.Minor, com.histmersion.domain.ReviewArticle.Finding.Kind("language"), com.histmersion.domain.ReviewArticle.Finding.Where("body"), com.histmersion.domain.ReviewArticle.Finding.Quote(""), com.histmersion.domain.ReviewArticle.Finding.Problem("banned filler"), com.histmersion.domain.ReviewArticle.Finding.Fix(""))
    val rule = Report(
      Reviewer.Rules,
      Verdict.Minor,
      List(Finding(com.histmersion.domain.ReviewArticle.Finding.Id("R1"), Reviewer.Rules, Severity.Minor, com.histmersion.domain.ReviewArticle.Finding.Kind("x"), com.histmersion.domain.ReviewArticle.Finding.Where("Intro"), com.histmersion.domain.ReviewArticle.Finding.Quote("q"), com.histmersion.domain.ReviewArticle.Finding.Problem("Banned filler"), com.histmersion.domain.ReviewArticle.Finding.Fix("")))
    )
    val cold = Report(
      Reviewer.ColdReader,
      Verdict.Minor,
      List(Finding(com.histmersion.domain.ReviewArticle.Finding.Id("C1"), Reviewer.ColdReader, Severity.Minor, com.histmersion.domain.ReviewArticle.Finding.Kind("term"), com.histmersion.domain.ReviewArticle.Finding.Where("Intro"), com.histmersion.domain.ReviewArticle.Finding.Quote("q"), com.histmersion.domain.ReviewArticle.Finding.Problem("term undefined"), com.histmersion.domain.ReviewArticle.Finding.Fix("")))
    )
    val skep = Report(
      Reviewer.Skeptic,
      Verdict.Major,
      List(Finding(com.histmersion.domain.ReviewArticle.Finding.Id("S1"), Reviewer.Skeptic, Severity.Major, com.histmersion.domain.ReviewArticle.Finding.Kind("date"), com.histmersion.domain.ReviewArticle.Finding.Where("Intro"), com.histmersion.domain.ReviewArticle.Finding.Quote("q"), com.histmersion.domain.ReviewArticle.Finding.Problem("the date is wrong"), com.histmersion.domain.ReviewArticle.Finding.Fix("")))
    )
    val m = merge(com.histmersion.domain.ReviewArticle.MergeRound(1), List(m1), List(skep, cold, rule), Nil)
    assertEquals(m.verdict, Verdict.Major)
    assertEquals(m.findings.map(_.id), List("M1", "R1", "C1", "S1"))
    val dup = rule.modify(_.findings).setTo(rule.findings.map(_.modify(_.where).setTo(Finding.Where("body")).modify(_.problem).setTo(Finding.Problem("banned  FILLER"))))
    assertEquals(merge(com.histmersion.domain.ReviewArticle.MergeRound(1), List(m1), List(dup), Nil).findings.map(_.id), List("M1"))

  test("merge: a finding the fixer rejected and a reviewer raises again is escalated"):
    val before = Finding(com.histmersion.domain.ReviewArticle.Finding.Id("S2"), Reviewer.Skeptic, Severity.Major, com.histmersion.domain.ReviewArticle.Finding.Kind("date"), com.histmersion.domain.ReviewArticle.Finding.Where("Dates"), com.histmersion.domain.ReviewArticle.Finding.Quote("1320 BCE"), com.histmersion.domain.ReviewArticle.Finding.Problem("the date is wrong"), com.histmersion.domain.ReviewArticle.Finding.Fix(""))
    val again = before.modify(_.id).setTo(Finding.Id("S1")).modify(_.problem).setTo(Finding.Problem("the date has no support"))
    val other = before.modify(_.id).setTo(Finding.Id("S3")).modify(_.reviewer).setTo(Reviewer.Rules).modify(_.problem).setTo(Finding.Problem("x"))
    val m = merge(
      com.histmersion.domain.ReviewArticle.MergeRound(2),
      Nil,
      List(Report(Reviewer.Skeptic, Verdict.Major, List(again)), Report(Reviewer.Rules, Verdict.Major, List(other))),
      List(before)
    )
    assertEquals(m.escalated.map(_.id), List("S1"))

  test("gate and rounds"):
    val major = Merged(
      com.histmersion.domain.ReviewArticle.Merged.Round(1),
      Verdict.Major,
      List(Finding(com.histmersion.domain.ReviewArticle.Finding.Id("S1"), Reviewer.Skeptic, Severity.Major, com.histmersion.domain.ReviewArticle.Finding.Kind(""), com.histmersion.domain.ReviewArticle.Finding.Where(""), com.histmersion.domain.ReviewArticle.Finding.Quote(""), com.histmersion.domain.ReviewArticle.Finding.Problem("p"), com.histmersion.domain.ReviewArticle.Finding.Fix(""))),
      None,
      Nil,
      Nil
    )
    val minor = major.modify(_.verdict).setTo(Verdict.Minor).modify(_.findings).setTo(major.findings.map(_.modify(_.severity).setTo(Severity.Minor)))
    assert(done(minor, com.histmersion.domain.ReviewArticle.Merged.Round(1), com.histmersion.domain.ReviewArticle.RoundCount(3), fix = com.histmersion.domain.ReviewArticle.FixRun(true)))
    assert(!done(major, com.histmersion.domain.ReviewArticle.Merged.Round(1), com.histmersion.domain.ReviewArticle.RoundCount(3), fix = com.histmersion.domain.ReviewArticle.FixRun(true)))
    assert(done(major, com.histmersion.domain.ReviewArticle.Merged.Round(1), com.histmersion.domain.ReviewArticle.RoundCount(3), fix = com.histmersion.domain.ReviewArticle.FixRun(false)))
    assert(done(major, com.histmersion.domain.ReviewArticle.Merged.Round(3), com.histmersion.domain.ReviewArticle.RoundCount(3), fix = com.histmersion.domain.ReviewArticle.FixRun(true)))
    assertEquals(rounds(None, com.histmersion.domain.ReviewArticle.DefaultRounds(3), com.histmersion.domain.ReviewArticle.RoundCap(5), fix = com.histmersion.domain.ReviewArticle.FixRun(true)), 3)
    assertEquals(rounds(Some(com.histmersion.domain.ReviewArticle.RequestedRounds(9)), com.histmersion.domain.ReviewArticle.DefaultRounds(3), com.histmersion.domain.ReviewArticle.RoundCap(5), fix = com.histmersion.domain.ReviewArticle.FixRun(false)), 5)
    assertEquals(rounds(Some(com.histmersion.domain.ReviewArticle.RequestedRounds(1)), com.histmersion.domain.ReviewArticle.DefaultRounds(3), com.histmersion.domain.ReviewArticle.RoundCap(5), fix = com.histmersion.domain.ReviewArticle.FixRun(true)), 2, "a fix needs a round to check it")
    assertEquals(rounds(Some(com.histmersion.domain.ReviewArticle.RequestedRounds(0)), com.histmersion.domain.ReviewArticle.DefaultRounds(3), com.histmersion.domain.ReviewArticle.RoundCap(5), fix = com.histmersion.domain.ReviewArticle.FixRun(false)), 1)

  private val merged = Merged(
    com.histmersion.domain.ReviewArticle.Merged.Round(1),
    Verdict.Major,
    List(
      Finding(com.histmersion.domain.ReviewArticle.Finding.Id("S1"), Reviewer.Skeptic, Severity.Major, com.histmersion.domain.ReviewArticle.Finding.Kind("date"), com.histmersion.domain.ReviewArticle.Finding.Where("Intro"), com.histmersion.domain.ReviewArticle.Finding.Quote(""), com.histmersion.domain.ReviewArticle.Finding.Problem("the date is wrong"), com.histmersion.domain.ReviewArticle.Finding.Fix("")),
      Finding(com.histmersion.domain.ReviewArticle.Finding.Id("C1"), Reviewer.ColdReader, Severity.Major, com.histmersion.domain.ReviewArticle.Finding.Kind("term"), com.histmersion.domain.ReviewArticle.Finding.Where("Intro"), com.histmersion.domain.ReviewArticle.Finding.Quote(""), com.histmersion.domain.ReviewArticle.Finding.Problem("undefined term"), com.histmersion.domain.ReviewArticle.Finding.Fix("")),
      Finding(com.histmersion.domain.ReviewArticle.Finding.Id("R1"), Reviewer.Rules, Severity.Minor, com.histmersion.domain.ReviewArticle.Finding.Kind("language"), com.histmersion.domain.ReviewArticle.Finding.Where("Intro"), com.histmersion.domain.ReviewArticle.Finding.Quote(""), com.histmersion.domain.ReviewArticle.Finding.Problem("wordy"), com.histmersion.domain.ReviewArticle.Finding.Fix(""))
    ),
    None,
    Nil,
    Nil
  )

  import com.histmersion.api.research.Article.Edits.{EditAnswer, Op}
  private def edit(op: Op, anchor: EditAnswer.Anchor, text: EditAnswer.Text | Null = null) = EditAnswer(op, anchor, Option(text))

  /** The glossed sentence the fixer adds for C1. */
  private val gloss = edit(Op.Replace, EditAnswer.Anchor("Tin reached the Aegean"), EditAnswer.Text("Tin, the scarce half of bronze, reached the Aegean"))
  private val corpus =
    BodyCorpus(isCk, isTarget, t => if t.value.contains("| a | b |") then List(com.histmersion.domain.ReviewArticle.BodyCorpus.FormatError("table-not-rendered — a table")) else Nil)
  private def fix(a: FixerAnswer, last: LastRound, before: Article.Body = Article.Body(Good)) =
    checkFix(a, merged, com.histmersion.domain.research.Slug("tin"), before, com.histmersion.domain.ReviewArticle.Snapshot.Kind("concept"), corpus, isCk, record.get, minRatio, last)

  test("fixer: edits applied to the reviewed body, every major decided and a quoted rejection pass"):
    val a = FixerAnswer(
      List(gloss),
      List(com.histmersion.domain.ReviewArticle.Finding.Id("C1")),
      List(
        RejectionAnswer(com.histmersion.domain.ReviewArticle.RejectionAnswer.Id("S1"), com.histmersion.domain.ReviewArticle.RejectionAnswer.Reason("the record says so"), com.histmersion.domain.ReviewArticle.RejectionAnswer.Citekey("knapp2016_tin"), com.histmersion.domain.ReviewArticle.RejectionAnswer.Quote("carried ten tons of copper and one ton of tin"))
      ),
      Nil,
      com.histmersion.domain.ReviewArticle.FixerAnswer.Notes("")
    )
    val (f, ws) = fix(a, last = LastRound(false)).toOption.get
    assertEquals(f.body, Good.replace("Tin reached", "Tin, the scarce half of bronze, reached"))
    assertEquals(
      f.edits.map(e => (e.index, e.op, e.applied)),
      List((1, Op.Replace, true)),
      "the step output records each edit"
    )
    assertEquals(f.applied, List("C1"))
    assertEquals(f.rejected.map(_.id), List("S1"))
    assertEquals(f.unverified, Nil)
    assertEquals((f.wordsBefore, f.wordsAfter), (words(Good), words(Good) + 5))
    assertEquals(ws, Nil)

  test("fixer: an unquotable rejection, a rejected reader finding, an undecided major, a bad edit are sent back"):
    val a = FixerAnswer(
      List(gloss),
      Nil,
      List(
        RejectionAnswer(com.histmersion.domain.ReviewArticle.RejectionAnswer.Id("S1"), com.histmersion.domain.ReviewArticle.RejectionAnswer.Reason("no"), com.histmersion.domain.ReviewArticle.RejectionAnswer.Citekey("knapp2016_tin"), com.histmersion.domain.ReviewArticle.RejectionAnswer.Quote("the ship sank in a storm off Lycia in 1320 BCE")),
        RejectionAnswer(com.histmersion.domain.ReviewArticle.RejectionAnswer.Id("C1"), com.histmersion.domain.ReviewArticle.RejectionAnswer.Reason("it is clear"), com.histmersion.domain.ReviewArticle.RejectionAnswer.Citekey("knapp2016_tin"), com.histmersion.domain.ReviewArticle.RejectionAnswer.Quote("ten tons of copper"))
      ),
      Nil,
      com.histmersion.domain.ReviewArticle.FixerAnswer.Notes("")
    )
    val ps = fix(a, last = LastRound(false)).left.toOption.get
    assert(ps.exists(_.contains("rejection of S1: the quote is not in the record")), ps)
    assert(ps.exists(_.contains("C1 is a reader's confusion")), ps)
    val bad = FixerAnswer(
      List(
        edit(Op.InsertAfter, EditAnswer.Anchor("is open [[knapp2016_tin]]."), EditAnswer.Text(" Or [[ghost2020_x]].")),
        edit(Op.InsertBefore, EditAnswer.Anchor("## Not used"), EditAnswer.Text("## Sources cited\n\n- x [[knapp2016_tin]]\n\n")),
        edit(Op.Replace, EditAnswer.Anchor("by land"), EditAnswer.Text("by sea"))
      ),
      List(com.histmersion.domain.ReviewArticle.Finding.Id("S1"), com.histmersion.domain.ReviewArticle.Finding.Id("C1")),
      Nil,
      Nil,
      com.histmersion.domain.ReviewArticle.FixerAnswer.Notes("")
    )
    val ps2 = fix(bad, last = LastRound(false)).left.toOption.get
    assert(ps2.exists(p => p.startsWith("edit 1 (insertAfter") && p.contains("ghost2020_x")), ps2)
    assert(ps2.exists(_.contains("Sources cited")), ps2)
    assert(ps2.exists(_.startsWith("edit 3 (replace \"by land\"): the anchor is not in the article")), ps2)
    val undecided = FixerAnswer(List(gloss), List(com.histmersion.domain.ReviewArticle.Finding.Id("C1")), Nil, Nil, com.histmersion.domain.ReviewArticle.FixerAnswer.Notes(""))
    assert(fix(undecided, last = LastRound(false)).left.exists(_.exists(_.contains("S1 is neither applied nor rejected"))))
    val idle = FixerAnswer(Nil, List(com.histmersion.domain.ReviewArticle.Finding.Id("C1"), com.histmersion.domain.ReviewArticle.Finding.Id("S1")), Nil, Nil, com.histmersion.domain.ReviewArticle.FixerAnswer.Notes(""))
    assert(fix(idle, last = LastRound(false)).left.exists(_.exists(_.contains("no edit changed the article"))))

  test("fixer: an uncited paragraph and a formatting error the edits introduce are sent back"):
    val a = FixerAnswer(
      List(edit(Op.InsertAfter, EditAnswer.Anchor("by sea [[knapp2016_tin]]."), EditAnswer.Text("\n\nBronze needs tin.\n\n| a | b |"))),
      List(com.histmersion.domain.ReviewArticle.Finding.Id("C1"), com.histmersion.domain.ReviewArticle.Finding.Id("S1")),
      Nil,
      Nil,
      com.histmersion.domain.ReviewArticle.FixerAnswer.Notes("")
    )
    val ps = fix(a, last = LastRound(false)).left.toOption.get
    assert(ps.exists(_.contains("no [[citekey]]")), ps)
    assert(ps.exists(_ == "formatting: table-not-rendered — a table"), ps)
    // a formatting error the body already had is not the edits' doing
    val had = Good + "\n| a | b | [[knapp2016_tin]]\n"
    assert(
      !fix(FixerAnswer(List(gloss), List(com.histmersion.domain.ReviewArticle.Finding.Id("C1"), com.histmersion.domain.ReviewArticle.Finding.Id("S1")), Nil, Nil, com.histmersion.domain.ReviewArticle.FixerAnswer.Notes("")), last = LastRound(false), Article.Body(had)).left
        .exists(_.exists(_.contains("formatting")))
    )

  test("fixer, last attempt: the edits that apply are kept, the others reported; unverifiable rejections stay open"):
    val a = FixerAnswer(
      List(edit(Op.Replace, EditAnswer.Anchor("no such passage"), EditAnswer.Text("x")), gloss),
      List(com.histmersion.domain.ReviewArticle.Finding.Id("C1")),
      List(RejectionAnswer(com.histmersion.domain.ReviewArticle.RejectionAnswer.Id("S1"), com.histmersion.domain.ReviewArticle.RejectionAnswer.Reason("no"), com.histmersion.domain.ReviewArticle.RejectionAnswer.Citekey("knapp2016_tin"), com.histmersion.domain.ReviewArticle.RejectionAnswer.Quote("nothing like this is in it"))),
      Nil,
      com.histmersion.domain.ReviewArticle.FixerAnswer.Notes("")
    )
    assert(fix(a, last = LastRound(false)).isLeft)
    val (f, ws) = fix(a, last = LastRound(true)).toOption.get
    assertEquals(f.body, Good.replace("Tin reached", "Tin, the scarce half of bronze, reached"))
    assertEquals(f.edits.map(_.applied), List(false, true))
    assertEquals(f.unverified, List("S1"))
    assertEquals(f.rejected, Nil)
    assert(ws.exists(w => w.startsWith("fixer: kept despite — edit 1 (replace \"no such passage\")")), ws)

  test("fixer, last attempt: an edit that links to nothing is never applied, and the rest still are"):
    val a = FixerAnswer(
      List(edit(Op.InsertAfter, EditAnswer.Anchor("is open [[knapp2016_tin]]."), EditAnswer.Text(" See [[ghost2020_x]].")), gloss),
      List(com.histmersion.domain.ReviewArticle.Finding.Id("C1"), com.histmersion.domain.ReviewArticle.Finding.Id("S1")),
      Nil,
      Nil,
      com.histmersion.domain.ReviewArticle.FixerAnswer.Notes("")
    )
    val (f, ws) = fix(a, last = LastRound(true)).toOption.get
    assert(!f.body.value.contains("ghost2020_x"), f.body)
    assert(f.body.value.contains("the scarce half of bronze"))
    assert(ws.exists(_.contains("ghost2020_x")), ws)

  test("fixer, last attempt: a result with a NEW link problem or no text is fatal; an old one is not"):
    // a delete that leaves nothing
    val empty = FixerAnswer(List(edit(Op.Delete, EditAnswer.Anchor(Good.trim))), List(com.histmersion.domain.ReviewArticle.Finding.Id("C1"), com.histmersion.domain.ReviewArticle.Finding.Id("S1")), Nil, Nil, com.histmersion.domain.ReviewArticle.FixerAnswer.Notes(""))
    assert(fix(empty, last = LastRound(true)).isLeft)
    val withGhost = Good + "\nOld [[ghost2020_x]] link [[knapp2016_tin]].\n"
    val (f, ws) = fix(FixerAnswer(List(gloss), List(com.histmersion.domain.ReviewArticle.Finding.Id("C1"), com.histmersion.domain.ReviewArticle.Finding.Id("S1")), Nil, Nil, com.histmersion.domain.ReviewArticle.FixerAnswer.Notes("")), last = LastRound(true), Article.Body(withGhost)).toOption.get
    assert(f.body.value.contains("the scarce half of bronze"))
    assert(ws.exists(_.contains("ghost2020_x")), ws)

  test("checkBody: a whole body (a writer's draft) is checked with the same rules"):
    val c =
      checkBody(com.histmersion.domain.research.Slug("tin"), com.histmersion.domain.ReviewArticle.Fix.Body(""), com.histmersion.domain.ReviewArticle.Snapshot.Kind("concept"), Article.Edits.Draft.Body(com.histmersion.api.research.Article.Edits.Draft.Body.Text("Tin [[ghost2020_x]].\n\n## Sources cited\n")), corpus)
    assert(c.fatal.value)
    assert(c.problems.exists(_.value.contains("ghost2020_x")) && c.problems.exists(_.value.contains("Sources cited")), c.problems)
    val ok = checkBody(com.histmersion.domain.research.Slug("tin"), com.histmersion.domain.ReviewArticle.Fix.Body(""), com.histmersion.domain.ReviewArticle.Snapshot.Kind("concept"), Article.Edits.Draft.Body(Article.Edits.Draft.Body.Text(Good)), corpus)
    assertEquals((ok.problems, ok.fatal, ok.edits), (Nil, false, Nil))
    // an index fragment needs no section set
    assertEquals(
      checkBody(com.histmersion.domain.research.Slug("tin"), com.histmersion.domain.ReviewArticle.Fix.Body(""), com.histmersion.domain.ReviewArticle.Snapshot.Kind("index"), Article.Edits.Draft.Body(com.histmersion.api.research.Article.Edits.Draft.Body.Text("More tin [[knapp2016_tin]].")), corpus).problems,
      Nil
    )

  // ── #958: coverage ─────────────────────────────────────────────────────────────────────────────

  /** `Good` with its hand-written Not used section removed: what an article with a filter holds. */
  private val Generated = Good.substring(0, Good.indexOf("## Not used")).trim + "\n"
  private val cov = Article.Coverage.compute(
    com.histmersion.api.research.Article.Coverage.ComputeSlug("tin"),
    Article.Filter(topics = List(com.histmersion.api.library.Filter.Topics("tin"))),
    com.histmersion.api.research.Article.Coverage.ComputeTotal(3),
    List(
      Coverage.Source(com.histmersion.api.library.Citekey("knapp2016_tin"), com.histmersion.api.library.Title("Tin"), Some(com.histmersion.api.catalogue.Year(2016)), Source.Status.Current),
      Coverage.Source(com.histmersion.api.library.Citekey("egypt2001_old"), com.histmersion.api.library.Title("Egypt"), Some(com.histmersion.api.catalogue.Year(2001)), Source.Status.Current),
      Coverage.Source(com.histmersion.api.library.Citekey("iso2024_new"), com.histmersion.api.library.Title("Isotopes"), Some(com.histmersion.api.catalogue.Year(2024)), Source.Status.Contested)
    ),
    Set(com.histmersion.api.library.Citekey("knapp2016_tin")),
    List(com.histmersion.api.library.Citekey("knapp2016_tin")),
    Map(com.histmersion.api.library.Citekey("egypt2001_old") -> Coverage.Entry.Reason("about Egypt"), com.histmersion.api.library.Citekey("knapp2016_tin") -> Coverage.Entry.Reason("stale")),
    Nil,
    Nil
  )
  private val ckCov = (t: Citekey) => Set("knapp2016_tin", "egypt2001_old", "iso2024_new")(t.value)

  test(
    "mechanical (#958): with a filter, no Not used section is required and a hand-written one or a Method note is MAJOR"
  ):
    assertEquals(mechanical(com.histmersion.domain.research.Slug("a"), com.histmersion.domain.ReviewArticle.Snapshot.Kind("concept"), Article.Body(Generated), isTarget, generated = com.histmersion.domain.ReviewArticle.MechanicalGenerated(true)), Nil)
    assert(
      mechanical(com.histmersion.domain.research.Slug("a"), com.histmersion.domain.ReviewArticle.Snapshot.Kind("concept"), Article.Body(Generated), isTarget).exists(_.problem.value.contains("'not used'")),
      "without a filter it is still required"
    )
    val written =
      mechanical(com.histmersion.domain.research.Slug("a"), com.histmersion.domain.ReviewArticle.Snapshot.Kind("concept"), Article.Body(Good + "\n_Method: search {topics:[tin]} → 3._\n"), isTarget, generated = com.histmersion.domain.ReviewArticle.MechanicalGenerated(true))
    assertEquals(written.map(f => (f.kind, f.major)), List((CoverageKind, true), (CoverageKind, true)))
    assert(
      written.head.problem.value.startsWith("a hand-written Not used section") && written(1).problem.value.startsWith(
        "a hand-written Method note"
      )
    )

  test("coverageDrift (#958): an unplaced source is MAJOR, stale reasons minor; ids follow the mechanical ones"):
    val fs = coverageDrift(cov, com.histmersion.domain.ReviewArticle.Merged.Round(4))
    assertEquals(
      fs.map(f => (f.id, f.severity, f.kind)),
      List(("M4", Severity.Major, CoverageKind), ("M5", Severity.Minor, CoverageKind))
    )
    assertEquals(fs.head.problem, "1 source the filter returns is neither cited nor given a reason: [[iso2024_new]]")
    assert(fs(1).problem.value.contains("[[knapp2016_tin]]"), fs(1).problem)

  test("checkBody (#958): with a filter, a Not used section the edits write is a problem"):
    val c = checkBody(
      com.histmersion.domain.research.Slug("tin"),
      Article.Body(Generated),
      com.histmersion.domain.ReviewArticle.Snapshot.Kind("concept"),
      Article.Edits.Draft.Edits(
        List(edit(Op.InsertAfter, EditAnswer.Anchor("is open [[knapp2016_tin]]."), EditAnswer.Text("\n\n## Not used\n\n- [[knapp2016_tin]] — x")))
      ),
      corpus.modify(_.generated).setTo(BodyCorpus.Generated(true))
    )
    assert(c.problems.exists(_.value.startsWith("a hand-written Not used section")), c.problems)

  private val unplaced = Merged(com.histmersion.domain.ReviewArticle.Merged.Round(1), Verdict.Major, coverageDrift(cov, com.histmersion.domain.ReviewArticle.Merged.Round(1)), None, Nil, Nil)
  private def fixCov(a: FixerAnswer, last: LastRound = LastRound(false), c: Option[Coverage] = Some(cov)) =
    checkFix(
      a,
      unplaced,
      com.histmersion.domain.research.Slug("tin"),
      Article.Body(Generated),
      com.histmersion.domain.ReviewArticle.Snapshot.Kind("concept"),
      corpus.modify(_.isCitekey).setTo(ckCov).modify(_.generated).setTo(BodyCorpus.Generated(true)),
      ckCov,
      record.get,
      minRatio,
      last,
      c
    )

  test("fixer (#958): an unplaced source is answered with a reason — no edit needed; the reasons are checked"):
    val ok = FixerAnswer(
      Nil,
      List(com.histmersion.domain.ReviewArticle.Finding.Id("M1"), com.histmersion.domain.ReviewArticle.Finding.Id("M2")),
      Nil,
      Nil,
      com.histmersion.domain.ReviewArticle.FixerAnswer.Notes(""),
      List(Coverage.Reason(com.histmersion.api.library.Citekey("[[iso2024_new]]"), com.histmersion.api.research.Coverage.Entry.Reason("Its isotopes date a later trade.")), Coverage.Reason(com.histmersion.api.library.Citekey("knapp2016_tin"), com.histmersion.api.research.Coverage.Entry.Reason("")))
    )
    val (f, ws) = fixCov(ok).fold(ps => fail(ps.mkString("; ")), identity)
    assertEquals(
      f.reasons,
      List(Coverage.Reason(com.histmersion.api.library.Citekey("iso2024_new"), com.histmersion.api.research.Coverage.Entry.Reason("Its isotopes date a later trade.")), Coverage.Reason(com.histmersion.api.library.Citekey("knapp2016_tin"), com.histmersion.api.research.Coverage.Entry.Reason("")))
    )
    assertEquals((f.body, ws), (Generated, Nil))
    // M1 applied but nothing placed; a reason for a cited source; one outside the filter; one for a source with no reason to remove
    val bad = FixerAnswer(
      Nil,
      List(com.histmersion.domain.ReviewArticle.Finding.Id("M1"), com.histmersion.domain.ReviewArticle.Finding.Id("M2")),
      Nil,
      Nil,
      com.histmersion.domain.ReviewArticle.FixerAnswer.Notes(""),
      List(
        Coverage.Reason(com.histmersion.api.library.Citekey("knapp2016_tin"), com.histmersion.api.research.Coverage.Entry.Reason("not needed")),
        Coverage.Reason(com.histmersion.api.library.Citekey("zz1999_gone"), com.histmersion.api.research.Coverage.Entry.Reason("never read")),
        Coverage.Reason(com.histmersion.api.library.Citekey("egypt2001_old"), com.histmersion.api.research.Coverage.Entry.Reason(""))
      )
    )
    val ps = fixCov(bad).left.toOption.get
    assert(
      ps.contains("reason for [[knapp2016_tin]]: the article cites it — a reason is only for a source it leaves out"),
      ps
    )
    assert(
      ps.contains("reason for [[zz1999_gone]]: the filter does not return it — give reasons only for its sources"),
      ps
    )
    // removing egypt2001_old's stored reason is allowed — and leaves it unplaced too
    assert(!ps.exists(_.startsWith("reason for [[egypt2001_old]]")), ps)
    assert(ps.contains("M1: still neither cited nor given a reason: [[egypt2001_old]], [[iso2024_new]]"), ps)
    // citing it places it as well
    val cite = FixerAnswer(
      List(edit(Op.InsertAfter, EditAnswer.Anchor("is open [[knapp2016_tin]]."), EditAnswer.Text(" Isotopes point east [[iso2024_new]] (contested)."))),
      List(com.histmersion.domain.ReviewArticle.Finding.Id("M1"), com.histmersion.domain.ReviewArticle.Finding.Id("M2")),
      Nil,
      Nil,
      com.histmersion.domain.ReviewArticle.FixerAnswer.Notes(""),
      List(Coverage.Reason(com.histmersion.api.library.Citekey("knapp2016_tin"), com.histmersion.api.research.Coverage.Entry.Reason("")))
    )
    assert(fixCov(cite).isRight, fixCov(cite))
    // an article without a filter takes no reasons
    assert(fixCov(ok, c = None).left.exists(_.exists(_.contains("records no filter"))))

  test("evidence: verified against the record, dropped with a warning when not there, kept by the merge"):
    val ev = evidenceCheck(isCk, record.get, minRatio)
    val f = FindingAnswer(
      com.histmersion.domain.ReviewArticle.FindingAnswer.Severity("major"),
      com.histmersion.domain.ReviewArticle.FindingAnswer.Kind("contradictory"),
      com.histmersion.domain.ReviewArticle.FindingAnswer.Where("Intro"),
      com.histmersion.domain.ReviewArticle.FindingAnswer.Quote("by sea"),
      com.histmersion.domain.ReviewArticle.FindingAnswer.Problem("the record says otherwise"),
      com.histmersion.domain.ReviewArticle.FindingAnswer.Fix("fix"),
      List(
        EvidenceAnswer(com.histmersion.domain.ReviewArticle.EvidenceAnswer.Citekey("knapp2016_tin"), Some(com.histmersion.domain.ReviewArticle.EvidenceAnswer.Offset(120)), com.histmersion.domain.ReviewArticle.EvidenceAnswer.Quote("carried ten tons of copper and one ton of tin")),
        EvidenceAnswer(com.histmersion.domain.ReviewArticle.EvidenceAnswer.Citekey("knapp2016_tin"), None, com.histmersion.domain.ReviewArticle.EvidenceAnswer.Quote("the cargo was Cornish")),
        EvidenceAnswer(com.histmersion.domain.ReviewArticle.EvidenceAnswer.Citekey("ghost2020_x"), None, com.histmersion.domain.ReviewArticle.EvidenceAnswer.Quote("anything at all here"))
      )
    )
    val (r, ws) =
      checkSkeptic(SkepticAnswer(com.histmersion.domain.ReviewArticle.SkepticAnswer.Verdict("MAJOR"), List(f), Nil, com.histmersion.domain.ReviewArticle.SkepticAnswer.ClaimsChecked(1)), last = com.histmersion.domain.ReviewArticle.CheckSkepticLast(false), isCk, record.get, minRatio).toOption.get
    assertEquals(
      r.findings.head.evidence.map(e => (e.citekey, e.offset, e.ratio)),
      List(("knapp2016_tin", Some(120), 1.0))
    )
    assertEquals(ws.count(_.contains("S1 evidence dropped")), 2, ws)
    assert(
      ws.exists(_.contains("not in the record of [[knapp2016_tin]]")) && ws.exists(
        _.contains("'ghost2020_x' is not a citekey")
      ),
      ws
    )
    // the rules reviewer's evidence is checked the same way; a missing check drops it
    val (rr, _) = checkRules(RulesAnswer(com.histmersion.domain.ReviewArticle.RulesAnswer.Verdict("MAJOR"), List(f), com.histmersion.domain.ReviewArticle.RulesAnswer.Filter("f"), Nil, Nil), last = com.histmersion.domain.ReviewArticle.CheckRulesLast(false), ev).toOption.get
    assertEquals(rr.findings.head.evidence.size, 1)
    assertEquals(
      checkRules(RulesAnswer(com.histmersion.domain.ReviewArticle.RulesAnswer.Verdict("MAJOR"), List(f), com.histmersion.domain.ReviewArticle.RulesAnswer.Filter("f"), Nil, Nil), last = com.histmersion.domain.ReviewArticle.CheckRulesLast(false)).toOption.get._1.findings.head.evidence,
      Nil
    )
    // a duplicate's evidence joins the kept finding, and the fixer reads it under the finding
    val e2 = Evidence(com.histmersion.domain.library.Citekey("knapp2016_tin"), None, com.histmersion.domain.ReviewArticle.Evidence.Quote("one ton of tin"), Evidence.Ratio(1.0))
    val dup = rr.findings.head.copy(id = com.histmersion.domain.ReviewArticle.Finding.Id("R9"), evidence = List(e2))
    val m = merge(com.histmersion.domain.ReviewArticle.MergeRound(1), Nil, List(rr, rr.modify(_.findings).setTo(List(dup))), Nil)
    assertEquals(m.findings.map(_.id), List("R1"))
    assertEquals(
      m.findings.head.evidence.map(_.quote),
      List("carried ten tons of copper and one ton of tin", "one ton of tin")
    )
    val text = findingsText(m)
    assert(
      text.contains("\n  evidence: [[knapp2016_tin]] @120: \"carried ten tons of copper and one ton of tin\""),
      text
    )
    assert(text.contains("\n  evidence: [[knapp2016_tin]]: \"one ton of tin\""), text)

  test("the fixer's turns grow with the majors and the factual minors, up to the cap"):
    // S1 major, C1 major, R1 a rules minor: 3 findings that may need a lookup
    assertEquals(fixerTurns(12, merged, 1, com.histmersion.domain.ReviewArticle.TurnCap(40)), 15)
    assertEquals(fixerTurns(12, merged, 2, com.histmersion.domain.ReviewArticle.TurnCap(40)), 18)
    assertEquals(fixerTurns(12, merged, 10, com.histmersion.domain.ReviewArticle.TurnCap(20)), 20, "capped")
    assertEquals(fixerTurns(12, merged, 1, com.histmersion.domain.ReviewArticle.TurnCap(5)), 12, "a cap below the agent's own turns never cuts them")
    val readerMinor =
      merged.modify(_.findings).setTo(List(Finding(com.histmersion.domain.ReviewArticle.Finding.Id("C2"), Reviewer.ColdReader, Severity.Minor, com.histmersion.domain.ReviewArticle.Finding.Kind("term"), com.histmersion.domain.ReviewArticle.Finding.Where("x"), com.histmersion.domain.ReviewArticle.Finding.Quote(""), com.histmersion.domain.ReviewArticle.Finding.Problem("p"), com.histmersion.domain.ReviewArticle.Finding.Fix(""))))
    assertEquals(fixerTurns(12, readerMinor, 1, com.histmersion.domain.ReviewArticle.TurnCap(40)), 12, "a reader's minor needs no lookup")

  test("bookkeeping: the log line"):
    val open = List(
      Finding(com.histmersion.domain.ReviewArticle.Finding.Id("S1"), Reviewer.Skeptic, Severity.Major, com.histmersion.domain.ReviewArticle.Finding.Kind("date"), com.histmersion.domain.ReviewArticle.Finding.Where("Dates"), com.histmersion.domain.ReviewArticle.Finding.Quote(""), com.histmersion.domain.ReviewArticle.Finding.Problem("the date is wrong"), com.histmersion.domain.ReviewArticle.Finding.Fix(""), escalated = com.histmersion.domain.ReviewArticle.Finding.Escalated(true))
    )
    val m = Merged(com.histmersion.domain.ReviewArticle.Merged.Round(2), Verdict.Major, open, None, Nil, Nil)
    val o = Outcome(com.histmersion.domain.ReviewArticle.Outcome.Slug("sea_peoples"), com.histmersion.domain.ReviewArticle.Outcome.Rounds(2), Verdict.Major, open, com.histmersion.domain.ReviewArticle.Outcome.Applied(3), com.histmersion.domain.ReviewArticle.Outcome.Rejected(1), fixed = com.histmersion.domain.ReviewArticle.Outcome.Fixed(true), com.histmersion.domain.ReviewArticle.Outcome.Approvals(0))
    assertEquals(
      logLine(o, m, com.histmersion.domain.automation.Run.Id("abcdef0123")),
      "review-article [[sea_peoples]]: round 2 → MAJOR (R:0 C:0 S:1 M:0), fixer applied 3 / rejected 1 (agent run abcdef01)"
    )

  test("verdicts order PASS < MINOR < MAJOR and parse in any case"):
    assertEquals(Verdict.worst(List(Verdict.Minor, Verdict.Pass)), Verdict.Minor)
    assertEquals(Verdict.parse(com.histmersion.domain.ReviewArticle.Verdict.Wire("major")), Some(Verdict.Major))
    assertEquals(Verdict.of(Nil), Verdict.Pass)

  // ── the review ledger: checks, the verification pass, declining a minor ──────────────────────────

  private val revised = Article.Body(Good.replace("Tin reached", "Tin, the scarce half of bronze, reached"))
  private val askedTin = Finding(
    com.histmersion.domain.ReviewArticle.Finding.Id("R1"),
    Reviewer.Rules,
    Severity.Major,
    com.histmersion.domain.ReviewArticle.Finding.Kind("term"),
    com.histmersion.domain.ReviewArticle.Finding.Where("Intro"),
    com.histmersion.domain.ReviewArticle.Finding.Quote("Tin reached the Aegean by sea"),
    com.histmersion.domain.ReviewArticle.Finding.Problem("tin is not defined"),
    com.histmersion.domain.ReviewArticle.Finding.Fix("")
  )
  private val askedDate =
    Finding(com.histmersion.domain.ReviewArticle.Finding.Id("R4"), Reviewer.Rules, Severity.Major, com.histmersion.domain.ReviewArticle.Finding.Kind("date"), com.histmersion.domain.ReviewArticle.Finding.Where("Disagreements"), com.histmersion.domain.ReviewArticle.Finding.Quote("Two dates are argued"), com.histmersion.domain.ReviewArticle.Finding.Problem("no date"), com.histmersion.domain.ReviewArticle.Finding.Fix(""))
  private val prior = Prior(List(askedTin, askedDate), revised, minRatio)

  test("checks: one per finding asked about, each id one of them, each quote in the CURRENT article"):
    val ok = List(
      CheckAnswer(com.histmersion.domain.ReviewArticle.CheckAnswer.Id("R1"), fixed = com.histmersion.domain.ReviewArticle.CheckAnswer.Fixed(true), com.histmersion.domain.ReviewArticle.CheckAnswer.Quote("Tin, the scarce half of bronze"), com.histmersion.domain.ReviewArticle.CheckAnswer.Note("glossed")),
      CheckAnswer(com.histmersion.domain.ReviewArticle.CheckAnswer.Id("R4"), fixed = com.histmersion.domain.ReviewArticle.CheckAnswer.Fixed(false), com.histmersion.domain.ReviewArticle.CheckAnswer.Quote("Two dates are argued"), com.histmersion.domain.ReviewArticle.CheckAnswer.Note("still undated"))
    )
    assertEquals(
      checkChecks(Reviewer.Rules, prior, ok, last = com.histmersion.domain.ReviewArticle.LastRound(false)),
      (
        List(
          Check(com.histmersion.domain.ReviewArticle.Check.Id("R1"), com.histmersion.domain.ReviewArticle.Check.Fixed(true), com.histmersion.domain.ReviewArticle.Check.Quote("Tin, the scarce half of bronze"), com.histmersion.domain.ReviewArticle.Check.Note("glossed")),
          Check(com.histmersion.domain.ReviewArticle.Check.Id("R4"), com.histmersion.domain.ReviewArticle.Check.Fixed(false), com.histmersion.domain.ReviewArticle.Check.Quote("Two dates are argued"), com.histmersion.domain.ReviewArticle.Check.Note("still undated"))
        ),
        Nil,
        Nil
      )
    )
    val bad = List(
      CheckAnswer(com.histmersion.domain.ReviewArticle.CheckAnswer.Id("R1"), fixed = com.histmersion.domain.ReviewArticle.CheckAnswer.Fixed(true), com.histmersion.domain.ReviewArticle.CheckAnswer.Quote("Tin, the precious half of bronze"), com.histmersion.domain.ReviewArticle.CheckAnswer.Note("")),
      CheckAnswer(com.histmersion.domain.ReviewArticle.CheckAnswer.Id("S2"), fixed = com.histmersion.domain.ReviewArticle.CheckAnswer.Fixed(true), com.histmersion.domain.ReviewArticle.CheckAnswer.Quote("x"), com.histmersion.domain.ReviewArticle.CheckAnswer.Note("")),
      CheckAnswer(com.histmersion.domain.ReviewArticle.CheckAnswer.Id("R4"), fixed = com.histmersion.domain.ReviewArticle.CheckAnswer.Fixed(false), com.histmersion.domain.ReviewArticle.CheckAnswer.Quote(""), com.histmersion.domain.ReviewArticle.CheckAnswer.Note(""))
    )
    val (cs, ps, ws) = checkChecks(Reviewer.Rules, prior, bad, last = com.histmersion.domain.ReviewArticle.LastRound(false))
    assertEquals((cs, ws), (Nil, Nil))
    assert(ps.exists(_.startsWith("check of R1: the quote is not in the current article")), ps)
    assert(ps.exists(_.startsWith("check of 'S2': not one of the findings you were asked to check (R1, R4)")), ps)
    assert(ps.exists(_.startsWith("check of R4: no quote — quote the current text that shows it still there")), ps)
    assert(
      ps.contains("R1: no check — say whether it is fixed") && ps.contains("R4: no check — say whether it is fixed"),
      ps
    )
    // the last attempt keeps what passed; the rest stays open, with a warning
    val (cs2, ps2, ws2) = checkChecks(Reviewer.Rules, prior, ok.take(1), last = com.histmersion.domain.ReviewArticle.LastRound(true))
    assertEquals((cs2.map(_.id), ps2), (List("R1"), Nil))
    assertEquals(ws2, List("rules: R4: no check — say whether it is fixed (left open)"))

  test(
    "checks: an empty quote passes only when nothing is left to quote (the passage is gone, or there never was one)"
  ):
    val gone = Prior(List(askedDate), Article.Body(Good.replace("Two dates are argued", "The wreck dates to 1320 BCE")), minRatio)
    assertEquals(
      checkChecks(Reviewer.Rules, gone, List(CheckAnswer(com.histmersion.domain.ReviewArticle.CheckAnswer.Id("R4"), fixed = com.histmersion.domain.ReviewArticle.CheckAnswer.Fixed(true), com.histmersion.domain.ReviewArticle.CheckAnswer.Quote(""), com.histmersion.domain.ReviewArticle.CheckAnswer.Note("deleted"))), last = com.histmersion.domain.ReviewArticle.LastRound(false))._1
        .map(_.id),
      List("R4")
    )
    val count = Finding(
      com.histmersion.domain.ReviewArticle.Finding.Id("C3"),
      Reviewer.ColdReader,
      Severity.Major,
      com.histmersion.domain.ReviewArticle.Finding.Kind("term"),
      com.histmersion.domain.ReviewArticle.Finding.Where("explain-back"),
      com.histmersion.domain.ReviewArticle.Finding.Quote(""),
      com.histmersion.domain.ReviewArticle.Finding.Problem("12 terms are undefined (more than 8)"),
      com.histmersion.domain.ReviewArticle.Finding.Fix("")
    )
    assertEquals(
      checkChecks(
        Reviewer.ColdReader,
        Prior(List(count), Article.Body(Good), minRatio),
        List(CheckAnswer(com.histmersion.domain.ReviewArticle.CheckAnswer.Id("C3"), fixed = com.histmersion.domain.ReviewArticle.CheckAnswer.Fixed(false), com.histmersion.domain.ReviewArticle.CheckAnswer.Quote(""), com.histmersion.domain.ReviewArticle.CheckAnswer.Note("still 12"))),
        last = com.histmersion.domain.ReviewArticle.LastRound(false)
      )._2,
      Nil
    )

  test("a full panel's answer carries checks only when the ledger asked; they are verified like the findings"):
    val withChecks = RulesAnswer(
      com.histmersion.domain.ReviewArticle.RulesAnswer.Verdict("PASS"),
      Nil,
      com.histmersion.domain.ReviewArticle.RulesAnswer.Filter("f"),
      checks = List(CheckAnswer(com.histmersion.domain.ReviewArticle.CheckAnswer.Id("R1"), fixed = com.histmersion.domain.ReviewArticle.CheckAnswer.Fixed(true), com.histmersion.domain.ReviewArticle.CheckAnswer.Quote("Tin, the scarce half of bronze"), com.histmersion.domain.ReviewArticle.CheckAnswer.Note("")))
    )
    assertEquals(
      checkRules(withChecks, last = com.histmersion.domain.ReviewArticle.CheckRulesLast(false), prior = Some(Prior(List(askedTin), revised, minRatio)))
        .map(_._1.checks.map(_.id)),
      Right(List("R1"))
    )
    assert(
      checkRules(withChecks.modify(_.checks).setTo(Nil), last = com.histmersion.domain.ReviewArticle.CheckRulesLast(false), prior = Some(Prior(List(askedTin), revised, minRatio))).isLeft,
      "an open finding of its own must be checked"
    )
    assertEquals(
      checkRules(withChecks, last = com.histmersion.domain.ReviewArticle.CheckRulesLast(false), prior = None).map(_._1.checks),
      Right(Nil),
      "no ledger: no checks"
    )
    val reader = ColdReaderAnswer(
      com.histmersion.domain.ReviewArticle.ColdReaderAnswer.Verdict("PASS"),
      com.histmersion.domain.ReviewArticle.ColdReaderAnswer.ExplainBack("It asks. It answers. They disagree. It is open. Done."),
      Nil,
      Nil,
      com.histmersion.domain.ReviewArticle.ColdReaderAnswer.TermsUndefined(0),
      com.histmersion.domain.ReviewArticle.ColdReaderAnswer.OpaqueSentences(0),
      None,
      List(CheckAnswer(com.histmersion.domain.ReviewArticle.CheckAnswer.Id("C1"), fixed = com.histmersion.domain.ReviewArticle.CheckAnswer.Fixed(true), com.histmersion.domain.ReviewArticle.CheckAnswer.Quote("the scarce half of bronze"), com.histmersion.domain.ReviewArticle.CheckAnswer.Note("")))
    )
    val readerPrior = Some(
      Prior(
        List(Finding(com.histmersion.domain.ReviewArticle.Finding.Id("C1"), Reviewer.ColdReader, Severity.Major, com.histmersion.domain.ReviewArticle.Finding.Kind("term"), com.histmersion.domain.ReviewArticle.Finding.Where("Intro"), com.histmersion.domain.ReviewArticle.Finding.Quote("Tin reached"), com.histmersion.domain.ReviewArticle.Finding.Problem("tin"), com.histmersion.domain.ReviewArticle.Finding.Fix(""))),
        revised,
        minRatio
      )
    )
    assertEquals(checkColdReader(reader, last = com.histmersion.domain.ReviewArticle.CheckColdReaderLast(false), readerPrior).map(_._1.checks.map(_.fixed)), Right(List(true)))

  test("scope: inside the edited passages anything stands; outside, a MAJOR only as a factual error with evidence"):
    val edited = Article.Body("Tin, the scarce half of bronze, reached the Aegean by sea [[knapp2016_tin]].")
    val ev = Evidence(com.histmersion.domain.library.Citekey("knapp2016_tin"), None, com.histmersion.domain.ReviewArticle.Evidence.Quote("ten tons of copper"), Evidence.Ratio(1.0))
    val inside = Finding(
      com.histmersion.domain.ReviewArticle.Finding.Id("S1"),
      Reviewer.Skeptic,
      Severity.Major,
      com.histmersion.domain.ReviewArticle.Finding.Kind("synth"),
      com.histmersion.domain.ReviewArticle.Finding.Where("Intro"),
      com.histmersion.domain.ReviewArticle.Finding.Quote("the scarce half of bronze"),
      com.histmersion.domain.ReviewArticle.Finding.Problem("a bridge claim"),
      com.histmersion.domain.ReviewArticle.Finding.Fix("")
    )
    val outside = inside.modify(_.quote).setTo(Finding.Quote("Two dates are argued"))
    assertEquals(scope(inside, edited, minRatio), (inside, None))
    assertEquals(scope(outside.modify(_.kind).setTo(Finding.Kind("contradictory")).modify(_.evidence).setTo(List(ev)), edited, minRatio)._2, None)
    val (down, w) = scope(outside, edited, minRatio)
    assertEquals(down.severity, Severity.Minor)
    assert(w.exists(_.contains("\"a bridge claim\" is outside the edited passages")), w)
    assertEquals(
      scope(outside.modify(_.kind).setTo(Finding.Kind("date")), edited, minRatio)._1.severity,
      Severity.Minor,
      "a factual kind still needs its evidence"
    )
    assertEquals(scope(outside.modify(_.severity).setTo(Severity.Minor), edited, minRatio)._2, None)

  test("checkRecheck: checks, scoped findings with verified evidence, at most three lessons"):
    val ev = evidenceCheck(isCk, record.get, minRatio)
    val a = RecheckAnswer(
      List(
        CheckAnswer(com.histmersion.domain.ReviewArticle.CheckAnswer.Id("R1"), fixed = com.histmersion.domain.ReviewArticle.CheckAnswer.Fixed(true), com.histmersion.domain.ReviewArticle.CheckAnswer.Quote("Tin, the scarce half of bronze"), com.histmersion.domain.ReviewArticle.CheckAnswer.Note("")),
        CheckAnswer(com.histmersion.domain.ReviewArticle.CheckAnswer.Id("R4"), fixed = com.histmersion.domain.ReviewArticle.CheckAnswer.Fixed(false), com.histmersion.domain.ReviewArticle.CheckAnswer.Quote("Two dates are argued"), com.histmersion.domain.ReviewArticle.CheckAnswer.Note("still undated"))
      ),
      List(
        FindingAnswer(
          com.histmersion.domain.ReviewArticle.FindingAnswer.Severity("major"),
          com.histmersion.domain.ReviewArticle.FindingAnswer.Kind("recall"),
          com.histmersion.domain.ReviewArticle.FindingAnswer.Where("Open questions"),
          com.histmersion.domain.ReviewArticle.FindingAnswer.Quote("The source of the tin is open"),
          com.histmersion.domain.ReviewArticle.FindingAnswer.Problem("the record names the source"),
          com.histmersion.domain.ReviewArticle.FindingAnswer.Fix("say it"),
          List(EvidenceAnswer(com.histmersion.domain.ReviewArticle.EvidenceAnswer.Citekey("knapp2016_tin"), None, com.histmersion.domain.ReviewArticle.EvidenceAnswer.Quote("one ton of tin")))
        ),
        FindingAnswer(com.histmersion.domain.ReviewArticle.FindingAnswer.Severity("major"), com.histmersion.domain.ReviewArticle.FindingAnswer.Kind("weight"), com.histmersion.domain.ReviewArticle.FindingAnswer.Where("Open questions"), com.histmersion.domain.ReviewArticle.FindingAnswer.Quote("The source of the tin is open"), com.histmersion.domain.ReviewArticle.FindingAnswer.Problem("too short"), com.histmersion.domain.ReviewArticle.FindingAnswer.Fix("expand"))
      ),
      List(com.histmersion.domain.ReviewArticle.Recheck.Lessons("one"), com.histmersion.domain.ReviewArticle.Recheck.Lessons("two"), com.histmersion.domain.ReviewArticle.Recheck.Lessons("three"), com.histmersion.domain.ReviewArticle.Recheck.Lessons("four"))
    )
    val (rc, ws) =
      checkRecheck(Reviewer.Rules, a, prior, com.histmersion.domain.ReviewArticle.CheckRecheckEdited("Tin, the scarce half of bronze"), ev, last = com.histmersion.domain.ReviewArticle.CheckRecheckLast(false)).toOption.get
    assertEquals(rc.checks.map(c => (c.id, c.fixed)), List(("R1", true), ("R4", false)))
    assertEquals(
      rc.findings.map(f => (f.kind, f.severity)),
      List(("recall", Severity.Major), ("weight", Severity.Minor))
    )
    assertEquals(rc.lessons, List("one", "two", "three"))
    assert(ws.exists(_.contains("\"too short\" is outside the edited passages")), ws)
    assert(checkRecheck(Reviewer.Rules, a.modify(_.checks).setTo(Nil), prior, com.histmersion.domain.ReviewArticle.CheckRecheckEdited(""), ev, last = com.histmersion.domain.ReviewArticle.CheckRecheckLast(false)).isLeft)
    assert(
      checkRecheck(Reviewer.Rules, RecheckAnswer(), prior, com.histmersion.domain.ReviewArticle.CheckRecheckEdited(""), ev, last = com.histmersion.domain.ReviewArticle.CheckRecheckLast(true)).isRight,
      "the last attempt keeps what passed"
    )

  test("fixer: a minor may be declined with a reason; a MAJOR may not; an unknown id is sent back"):
    val ok = FixerAnswer(
      List(gloss),
      List(com.histmersion.domain.ReviewArticle.Finding.Id("C1")),
      List(
        RejectionAnswer(com.histmersion.domain.ReviewArticle.RejectionAnswer.Id("S1"), com.histmersion.domain.ReviewArticle.RejectionAnswer.Reason("the record says so"), com.histmersion.domain.ReviewArticle.RejectionAnswer.Citekey("knapp2016_tin"), com.histmersion.domain.ReviewArticle.RejectionAnswer.Quote("carried ten tons of copper and one ton of tin"))
      ),
      Nil,
      com.histmersion.domain.ReviewArticle.FixerAnswer.Notes(""),
      wontFix = List(WontFixAnswer(com.histmersion.domain.ReviewArticle.WontFixAnswer.Id("R1"), com.histmersion.domain.ReviewArticle.WontFixAnswer.Reason("wordy, but exact")))
    )
    assertEquals(fix(ok, last = LastRound(false)).map(_._1.wontFix), Right(List(WontFix(com.histmersion.domain.ReviewArticle.WontFix.Id("R1"), com.histmersion.domain.ReviewArticle.WontFix.Reason("wordy, but exact")))))
    val bad = ok.modify(_.wontFix).setTo(List(WontFixAnswer(com.histmersion.domain.ReviewArticle.WontFixAnswer.Id("S1"), com.histmersion.domain.ReviewArticle.WontFixAnswer.Reason("no")), WontFixAnswer(com.histmersion.domain.ReviewArticle.WontFixAnswer.Id("R9"), com.histmersion.domain.ReviewArticle.WontFixAnswer.Reason("x")), WontFixAnswer(com.histmersion.domain.ReviewArticle.WontFixAnswer.Id("R1"), com.histmersion.domain.ReviewArticle.WontFixAnswer.Reason(" "))))
    val ps = fix(bad, last = LastRound(false)).left.toOption.get
    assert(ps.contains("S1 is MAJOR: apply it or reject it — only a minor finding can be declined"), ps)
    assert(ps.contains("won't fix 'R9': there is no such finding") && ps.contains("won't fix R1: give the reason"), ps)

  test(
    "fixer: a coverage MAJOR on an article that records no filter may be declined (it stays open); with a filter it may not"
  ):
    val cover = Finding(
      com.histmersion.domain.ReviewArticle.Finding.Id("R8"),
      Reviewer.Rules,
      Severity.Major,
      com.histmersion.domain.ReviewArticle.Finding.Kind("coverage"),
      com.histmersion.domain.ReviewArticle.Finding.Where("Not used"),
      com.histmersion.domain.ReviewArticle.Finding.Quote(""),
      Finding.Problem("the reconstructed search returns 94 sources; most are neither cited nor excluded"),
      com.histmersion.domain.ReviewArticle.Finding.Fix("record the filter")
    )
    val m = merged.modify(_.findings).setTo(merged.findings :+ cover)
    def run(a: FixerAnswer, c: Option[Coverage]) =
      checkFix(a, m, com.histmersion.domain.research.Slug("tin"), Article.Body(Good), com.histmersion.domain.ReviewArticle.Snapshot.Kind("concept"), corpus, isCk, record.get, minRatio, com.histmersion.domain.ReviewArticle.LastRound(false), c)
    val base = FixerAnswer(
      List(gloss),
      List(com.histmersion.domain.ReviewArticle.Finding.Id("C1")),
      List(
        RejectionAnswer(com.histmersion.domain.ReviewArticle.RejectionAnswer.Id("S1"), com.histmersion.domain.ReviewArticle.RejectionAnswer.Reason("the record says so"), com.histmersion.domain.ReviewArticle.RejectionAnswer.Citekey("knapp2016_tin"), com.histmersion.domain.ReviewArticle.RejectionAnswer.Quote("carried ten tons of copper and one ton of tin"))
      ),
      Nil,
      com.histmersion.domain.ReviewArticle.FixerAnswer.Notes("")
    )
    // left out: sent back, told how to answer it
    val missing = run(base, None).left.toOption.get
    assert(
      missing.exists(p =>
        p.startsWith("major finding R8 is neither applied, rejected nor declined") && p.contains("wontFix")
      ),
      missing
    )
    val declined = base.modify(_.wontFix).setTo(List(WontFixAnswer(com.histmersion.domain.ReviewArticle.WontFixAnswer.Id("R8"), com.histmersion.domain.ReviewArticle.WontFixAnswer.Reason("recording the filter is not an edit"))))
    assertEquals(run(declined, None).map(_._1.wontFix.map(_.id)), Right(List("R8")))
    assert(needsFilter(cover, None) && !needsFilter(cover, Some(cov)) && !needsFilter(merged.findings.head, None))
    // the ledger keeps a declined MAJOR open, and not claimed
    val led = ReviewLedger.Ledger(com.histmersion.domain.ReviewLedger.Ledger.Slug("tin"), m.findings.map(ReviewLedger.Entry(_, com.histmersion.domain.ReviewLedger.Entry.Run("run1"), com.histmersion.domain.ReviewLedger.Entry.Round(1))))
    val after = ReviewLedger.applyFix(led, run(declined, None).toOption.get._1, com.histmersion.domain.ReviewLedger.ApplyFixRun("run1"), com.histmersion.domain.ReviewLedger.ApplyFixRound(1))
    assert(after.entry(com.histmersion.domain.ReviewArticle.Finding.Id("R8")).exists(e => e.open && !e.claimed.value), after.entries)
    // with a recorded filter the article's Not used list is generated: the finding is answered in `reasons`
    val withFilter = run(declined, Some(cov)).left.toOption.get
    assert(withFilter.exists(_.startsWith("R8 is MAJOR: apply it or reject it")), withFilter)
    assert(withFilter.exists(_ == "major finding R8 is neither applied nor rejected"), withFilter)

  test("the cited sources reach a reviewer as one line each: citekey, year, status, a short title"):
    val long = "A " * 80
    val lines = ReviewArticle.citedLines(
      List(
        CitedSource(com.histmersion.api.library.Citekey("watkins1995_dragon"), (Some("How to Kill a Dragon")).map(com.histmersion.api.library.Title.apply), (Some(1995)).map(com.histmersion.api.catalogue.Year.apply), Some(Source.Status.Foundational)),
        CitedSource(com.histmersion.api.library.Citekey("x2020_y"), (Some(long)).map(com.histmersion.api.library.Title.apply), None, Some(Source.Status.Contested)),
        CitedSource(com.histmersion.api.library.Citekey("ghost2001_z"), None, None, None)
      )
    )
    val ls = lines.split("\n").toList
    assertEquals(ls.head, "- [[watkins1995_dragon]] · 1995 · foundational · How to Kill a Dragon")
    assert(ls(1).startsWith("- [[x2020_y]] · contested · A A") && ls(1).endsWith("…") && ls(1).length < 160, ls(1))
    assertEquals(ls(2), "- [[ghost2001_z]] · not in the corpus")
    assertEquals(ReviewArticle.citedLines(Nil), "")

  // ── spot-checks (#1840) ─────────────────────────────────────────────────────────────────────────

  private val records2 =
    record + (Citekey("tin2024_iso") -> AgentVerify.SourceBody("We show that the tin of the Uluburun cargo came from Central Asia."))
  private val spotEv = evidenceCheck(records2.contains, records2.get, minRatio)
  private val spotBody = Article.Body(Good + "\nThe tin came from Central Asia [[tin2024_iso]].\n")
  private val cited2 = Set(Citekey("knapp2016_tin"), Citekey("tin2024_iso"))
  private def read(ck: String, tool: ToolUse.Tool = ToolUse.Tool("get_source")) =
    AgentVerify.ToolUse(tool, ToolUse.Input(s"""{"citekey":"$ck","section":"findings"}"""))
  private val spotTin = SpotCheckAnswer(
    com.histmersion.domain.ReviewArticle.SpotCheckAnswer.Citekey("knapp2016_tin"),
    com.histmersion.domain.ReviewArticle.SpotCheckAnswer.Claim("Tin reached the Aegean by sea"),
    com.histmersion.domain.ReviewArticle.SpotCheckAnswer.Verdict("attributable"),
    com.histmersion.domain.ReviewArticle.SpotCheckAnswer.RecordQuote("The Uluburun ship carried ten tons of copper and one ton of tin.")
  )
  private val spotIso = SpotCheckAnswer(
    com.histmersion.domain.ReviewArticle.SpotCheckAnswer.Citekey("tin2024_iso"),
    com.histmersion.domain.ReviewArticle.SpotCheckAnswer.Claim("The tin came from Central Asia"),
    com.histmersion.domain.ReviewArticle.SpotCheckAnswer.Verdict("attributable"),
    com.histmersion.domain.ReviewArticle.SpotCheckAnswer.RecordQuote("the tin of the Uluburun cargo came from Central Asia")
  )
  private def rule(calls: List[AgentVerify.ToolUse], required: SpotRule.Required = SpotRule.Required(2)) =
    SpotRule(required, cited2, spotBody, calls, minRatio)

  test("spot-checks: how many are required — the configured number, fewer when fewer sources are cited"):
    assertEquals(spotChecksRequired(com.histmersion.domain.ReviewArticle.SpotCheckMin(3), com.histmersion.domain.ReviewArticle.CitedInCorpusCount(10)), 3)
    assertEquals(spotChecksRequired(com.histmersion.domain.ReviewArticle.SpotCheckMin(3), com.histmersion.domain.ReviewArticle.CitedInCorpusCount(2)), 2)
    assertEquals(spotChecksRequired(com.histmersion.domain.ReviewArticle.SpotCheckMin(3), com.histmersion.domain.ReviewArticle.CitedInCorpusCount(0)), 0)
    assertEquals(spotChecksRequired(com.histmersion.domain.ReviewArticle.SpotCheckMin(0), com.histmersion.domain.ReviewArticle.CitedInCorpusCount(5)), 0, "0 = not enforced")

  test("spot-checks: a get_source or passages call naming the citekey reads it; nothing else does"):
    assert(reads(read("knapp2016_tin"), com.histmersion.domain.library.Citekey("knapp2016_tin")))
    assert(reads(read("Knapp2016_Tin", ToolUse.Tool("passages")), com.histmersion.domain.library.Citekey("knapp2016_tin")), "case does not matter")
    assert(!reads(read("knapp2016_tin", ToolUse.Tool("search")), com.histmersion.domain.library.Citekey("knapp2016_tin")))
    assert(
      !reads(AgentVerify.ToolUse(com.histmersion.domain.automation.agents.ToolUse.Tool("query"), ToolUse.Input("SELECT * FROM sources WHERE citekey = 'knapp2016_tin'")), com.histmersion.domain.library.Citekey("knapp2016_tin"))
    )
    assert(!reads(read("tin2024_iso"), com.histmersion.domain.library.Citekey("knapp2016_tin")))

  test("spot-checks: each one verified by a tool call of the step and a quote found in the record"):
    val (ok, ps, ws) = checkSpotChecks(
      List(spotTin, spotIso),
      rule(List(read("knapp2016_tin"), read("tin2024_iso", ToolUse.Tool("passages")))),
      spotEv,
      last = com.histmersion.domain.ReviewArticle.CheckSpotChecksLast(false)
    )
    assertEquals((ps, ws), (Nil, Nil))
    assertEquals(
      ok.map(s => (s.citekey, s.tool, s.verdict)),
      List(("knapp2016_tin", "get_source", "attributable"), ("tin2024_iso", "passages", "attributable"))
    )

  test(
    "spot-checks: an answer from the brief alone (no tool call) is sent back, and kept with a warning on the last attempt"
  ):
    val (ok, ps, ws) = checkSpotChecks(List(spotTin, spotIso), rule(Nil), spotEv, last = com.histmersion.domain.ReviewArticle.CheckSpotChecksLast(false))
    assertEquals((ok, ws), (Nil, Nil))
    assert(ps.head.startsWith("spot-checks: 0 of the 2 required are verified"), ps)
    assert(ps.head.contains("0 get_source/passages call(s)"), ps)
    assert(ps.exists(_.contains("[[knapp2016_tin]]): no get_source or passages call for it")), ps)
    val (ok2, ps2, ws2) = checkSpotChecks(List(spotTin, spotIso), rule(Nil), spotEv, last = com.histmersion.domain.ReviewArticle.CheckSpotChecksLast(true))
    assertEquals((ok2, ps2), (Nil, Nil))
    assert(ws2.nonEmpty && ws2.forall(_.startsWith("rules: unverified — ")), ws2)
    // no spot-check at all is the same
    assert(checkSpotChecks(Nil, rule(Nil), spotEv, last = com.histmersion.domain.ReviewArticle.CheckSpotChecksLast(false))._2.head.startsWith("spot-checks: 0 of the 2"))

  test("spot-checks: the required ones must be on different sources; a check of one source twice counts once"):
    val twice = List(spotTin, spotTin.modify(_.claim).setTo(SpotCheckAnswer.Claim("Two dates are argued")))
    val (_, ps, _) = checkSpotChecks(twice, rule(List(read("knapp2016_tin"))), spotEv, last = com.histmersion.domain.ReviewArticle.CheckSpotChecksLast(false))
    assert(ps.head.startsWith("spot-checks: 1 of the 2 required"), ps)
    assertEquals(
      checkSpotChecks(twice, rule(List(read("knapp2016_tin")), required = SpotRule.Required(1)), spotEv, last = com.histmersion.domain.ReviewArticle.CheckSpotChecksLast(false))._1.size,
      2
    )

  test("spot-checks: an uncited source, a claim not in the article, a bad verdict or a quote not in the record fails"):
    val calls = List(read("knapp2016_tin"), read("tin2024_iso"), read("atlantis"))
    def problem(a: SpotCheckAnswer) =
      checkSpotChecks(List(a), rule(calls, required = com.histmersion.domain.ReviewArticle.SpotRule.Required(1)), spotEv, last = com.histmersion.domain.ReviewArticle.CheckSpotChecksLast(false))._2.drop(1).head
    assert(problem(spotTin.modify(_.citekey).setTo(SpotCheckAnswer.Citekey("atlantis"))).contains("the article does not cite it"))
    assert(
      problem(spotTin.modify(_.claim).setTo(SpotCheckAnswer.Claim("Tin reached Crete by land in the Iron Age")))
        .contains("the claim is not in the article")
    )
    assert(problem(spotTin.modify(_.verdict).setTo(SpotCheckAnswer.Verdict("supported"))).contains("verdict 'supported' is not one of"))
    assert(
      problem(spotTin.modify(_.recordQuote).setTo(SpotCheckAnswer.RecordQuote("The ship carried amber from the Baltic coast")))
        .contains("the quote is not in the record")
    )
    assert(problem(spotTin.modify(_.citekey).setTo(SpotCheckAnswer.Citekey(""))).contains("no citekey"))

  test("spot-checks: beyond the required number a failing one is dropped with a warning, not sent back"):
    val bad = spotIso.modify(_.recordQuote).setTo(SpotCheckAnswer.RecordQuote("an invented sentence that is in no record"))
    val (ok, ps, ws) = checkSpotChecks(
      List(spotTin, bad),
      rule(List(read("knapp2016_tin"), read("tin2024_iso")), required = SpotRule.Required(1)),
      spotEv,
      last = com.histmersion.domain.ReviewArticle.CheckSpotChecksLast(false)
    )
    assertEquals((ok.map(_.citekey), ps), (List("knapp2016_tin"), Nil))
    assert(ws.head.startsWith("rules: dropped — spot-check 2 ([[tin2024_iso]])"), ws)

  test("checkRules: spot-checks are enforced only when a rule is given, and land in the report"):
    val a = RulesAnswer(com.histmersion.domain.ReviewArticle.RulesAnswer.Verdict("PASS"), Nil, com.histmersion.domain.ReviewArticle.RulesAnswer.Filter("f"), spotChecks = List(spotTin, spotIso))
    assert(
      checkRules(a.modify(_.spotChecks).setTo(Nil), last = com.histmersion.domain.ReviewArticle.CheckRulesLast(false)).isRight,
      "no rule: nothing required (a verification pass, a test)"
    )
    val missing = checkRules(a, last = com.histmersion.domain.ReviewArticle.CheckRulesLast(false), spotEv, spots = Some(rule(Nil)))
    assert(missing.left.exists(_.head.startsWith("spot-checks: 0 of the 2")), missing)
    val done = checkRules(a, last = com.histmersion.domain.ReviewArticle.CheckRulesLast(false), spotEv, spots = Some(rule(List(read("knapp2016_tin"), read("tin2024_iso")))))
    assertEquals(done.map(_._1.spotChecks.map(_.citekey)), Right(List("knapp2016_tin", "tin2024_iso")))
    // the last attempt keeps the answer, the shortfall a warning
    val kept = checkRules(a, last = com.histmersion.domain.ReviewArticle.CheckRulesLast(true), spotEv, spots = Some(rule(Nil)))
    assert(kept.exists(_._2.exists(_.startsWith("rules: unverified — spot-checks: 0 of the 2"))), kept)

  test("a Report checkpointed before spot-checks existed still decodes"):
    val old =
      """{"reviewer":"rules","verdict":"PASS","findings":[],"explainBack":null,"emptySlots":[],"proposals":[],"checks":[]}"""
    assertEquals(io.circe.parser.decode[Report](old).map(_.spotChecks), Right(Nil))
