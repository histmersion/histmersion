package com.histmersion
package domain
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.json.RawJson

import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Text.sentence
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
import com.histmersion.api.library.Citekey as ApiCitekey
import com.histmersion.api.research.Article
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.research.Slug
import com.histmersion.domain.automation.agents.ToolUse
import com.histmersion.api.library.Filter
import com.histmersion.api.catalogue.Year
import com.histmersion.domain.automation.Run
import com.histmersion.lint.{tolerated, LintRule}

/** The pure half of `review-article`: mechanical checks, reviewer validation, the merge (dedupe, worst verdict,
  * escalation), the gate, the rounds, the fixer's validation, the bookkeeping lines.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val isTarget = (t: Slug) => known(t.value)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val isCk = (t: Citekey) => t.value == "knapp2016_tin"
  private val record = Map(
    Citekey("knapp2016_tin") -> AgentVerify.SourceBody(
      "The Uluburun ship carried ten tons of copper and one ton of tin."
    )
  )
  private val minRatio = AgentVerify.MinRatio(0.9)

  private def fa(
      sev: FindingAnswer.Severity,
      where: FindingAnswer.Where,
      problem: FindingAnswer.Problem,
      quote: FindingAnswer.Quote = FindingAnswer.Quote("")
  ) =
    FindingAnswer(sev, FindingAnswer.Kind("synth"), where, quote, problem, FindingAnswer.Fix("fix it"))

  test("mechanical: a clean article has no findings"):
    assertEquals(mechanical(Slug("a"), ReviewArticle.Snapshot.Kind("concept"), Article.Body(Good), isTarget), Nil)

  test("mechanical: uncited paragraph, escaped links, Sources cited, missing sections, dangling link, filler"):
    val body =
      "It is important to note that tin moved.\n\nSee \\[\\[x\\]\\] and [[nobody2020_x]].\n\n## Sources cited\n\n- a\n"
    val fs = mechanical(Slug("a"), ReviewArticle.Snapshot.Kind("concept"), Article.Body(body), isTarget)
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
    assertEquals(
      mechanical(
        Slug("i"),
        ReviewArticle.Snapshot.Kind("index"),
        ReviewArticle.MechanicalBody("See [[knapp2016_tin]]."),
        isTarget
      ),
      Nil
    )
    // the lint rule's own test: a target shaped like a citekey (a year or an underscore) cites
    val fs = mechanical(
      Slug("a"),
      ReviewArticle.Snapshot.Kind("index"),
      ReviewArticle.MechanicalBody("See [[atlantis]]."),
      isTarget
    )
    assertEquals(fs.map(_.problem), List("a paragraph with no [[citekey]]"))

  test("a reviewer's findings get code ids; a bad severity is sent back, then dropped on the last attempt"):
    val a = RulesAnswer(
      ReviewArticle.RulesAnswer.Verdict("MAJOR"),
      List(
        fa(FindingAnswer.Severity("major"), FindingAnswer.Where("Intro"), FindingAnswer.Problem("bridge claim")),
        fa(FindingAnswer.Severity("severe"), FindingAnswer.Where("x"), FindingAnswer.Problem("y"))
      ),
      ReviewArticle.RulesAnswer.Filter("f"),
      Nil,
      Nil
    )
    assert(
      checkRules(a, last = ReviewArticle.CheckRulesLast.More).left.exists(_.exists(_.sentence.contains("'severe'")))
    )
    val (r, ws) = checkRules(a, last = ReviewArticle.CheckRulesLast.Last).toOption.get
    assertEquals(r.findings.map(f => (f.id, f.severity)), List(("R1", Severity.Major)))
    assertEquals(r.verdict, Verdict.Major)
    assert(ws.exists(_.contains("dropped")), ws)

  test("a verdict worse than the findings is a finding left out; a milder one is corrected"):
    val tooBad = RulesAnswer(
      ReviewArticle.RulesAnswer.Verdict("MAJOR"),
      List(fa(FindingAnswer.Severity("minor"), FindingAnswer.Where("x"), FindingAnswer.Problem("y"))),
      ReviewArticle.RulesAnswer.Filter("f"),
      Nil,
      Nil
    )
    assert(
      checkRules(tooBad, last = ReviewArticle.CheckRulesLast.More).left
        .exists(_.exists(_.sentence.contains("no finding is major")))
    )
    assertEquals(checkRules(tooBad, last = ReviewArticle.CheckRulesLast.Last).map(_._1.verdict), Right(Verdict.Minor))
    val tooMild = RulesAnswer(
      ReviewArticle.RulesAnswer.Verdict("PASS"),
      List(fa(FindingAnswer.Severity("major"), FindingAnswer.Where("x"), FindingAnswer.Problem("y"))),
      ReviewArticle.RulesAnswer.Filter("f"),
      Nil,
      Nil
    )
    assertEquals(
      checkRules(tooMild, last = ReviewArticle.CheckRulesLast.More).map(_._1.verdict),
      Right(Verdict.Major)
    )

  test("cold reader: code adds an empty explain-back slot as MAJOR and more than 8 undefined terms as a MINOR count"):
    val a = ColdReaderAnswer(
      ReviewArticle.ColdReaderAnswer.Verdict("MINOR"),
      ReviewArticle.ColdReaderAnswer.ExplainBack("It is about tin."),
      List(ReviewArticle.ExplainSlotsValue("disagreement")),
      List(
        ReaderFindingAnswer(
          ReviewArticle.ReaderFindingAnswer.Severity("minor"),
          ReviewArticle.ReaderFindingAnswer.Kind("term"),
          ReviewArticle.ReaderFindingAnswer.Where("Intro"),
          ReviewArticle.ReaderFindingAnswer.Quote(""),
          ReviewArticle.ReaderFindingAnswer.Problem("term X"),
          ReviewArticle.ReaderFindingAnswer.Fix("define it")
        )
      ),
      ReviewArticle.ColdReaderAnswer.TermsUndefined(9),
      ReviewArticle.ColdReaderAnswer.OpaqueSentences(0)
    )
    val (r, _) = checkColdReader(a, last = ReviewArticle.CheckColdReaderLast.More).toOption.get
    assertEquals(r.verdict, Verdict.Major)
    assertEquals(r.findings.map(_.id), List("C1", "C2", "C3"))
    assert(r.findings.exists(f => f.major && f.problem.value.contains("'disagreement'")))
    assertEquals(
      r.findings.filter(TermCount.is).map(f => (f.severity, f.kind, f.problem)),
      List((Severity.Minor, "term-count", "9 terms undefined (count only; the individual findings carry the severity)"))
    )
    assertEquals(r.emptySlots, List("disagreement"))
    assert(
      checkColdReader(
        a.modify(_.explainBack).setTo(ColdReaderAnswer.ExplainBack(" ")),
        last = ReviewArticle.CheckColdReaderLast.More
      ).isLeft
    )
    assert(
      checkColdReader(
        a.modify(_.emptySlots).setTo(List(ExplainSlotsValue("why"))),
        last = ReviewArticle.CheckColdReaderLast.More
      ).isLeft
    )

  test("#1461 cold reader: 'none…' and blank empty slots mean none; prose naming a slot is that slot"):
    val none = ExplainSlotsValue("none: question, answer, disagreement and open questions are all stated in the text")
    assertEquals(emptySlotsOf(List(none)), (Nil, Nil))
    assertEquals(
      emptySlotsOf(
        List(
          ReviewArticle.ExplainSlotsValue(""),
          ReviewArticle.ExplainSlotsValue("  "),
          ReviewArticle.ExplainSlotsValue("N/A"),
          ReviewArticle.ExplainSlotsValue("No empty slots"),
          ReviewArticle.ExplainSlotsValue("all four are filled"),
          ReviewArticle.ExplainSlotsValue("-")
        )
      ),
      (Nil, Nil)
    )
    assertEquals(
      emptySlotsOf(
        List(
          ReviewArticle.ExplainSlotsValue("Open questions"),
          ReviewArticle.ExplainSlotsValue("disagreements: nobody is named"),
          ReviewArticle.ExplainSlotsValue("answer"),
          ReviewArticle.ExplainSlotsValue("open")
        )
      ),
      (List("open", "disagreement", "answer"), Nil)
    )
    assertEquals(emptySlotsOf(List(ReviewArticle.ExplainSlotsValue("why"))), (Nil, List("why")))
    val a = ColdReaderAnswer(
      ReviewArticle.ColdReaderAnswer.Verdict("PASS"),
      ReviewArticle.ColdReaderAnswer.ExplainBack("It is about tin."),
      List(none),
      Nil,
      ReviewArticle.ColdReaderAnswer.TermsUndefined(0),
      ReviewArticle.ColdReaderAnswer.OpaqueSentences(0)
    )
    val (r, warnings) =
      checkColdReader(a, last = ReviewArticle.CheckColdReaderLast.More).fold(ps => fail(ps.mkString("; ")), identity)
    assertEquals((r.verdict, r.emptySlots, r.findings), (Verdict.Pass, Nil, Nil))
    assertEquals(warnings, Nil)

  test("cold reader: the term count is MINOR at any count, and none at 8 or fewer"):
    def countOf(n: ColdReaderAnswer.TermsUndefined) =
      checkColdReader(
        ColdReaderAnswer(
          ReviewArticle.ColdReaderAnswer.Verdict("PASS"),
          ReviewArticle.ColdReaderAnswer.ExplainBack("It is about tin."),
          Nil,
          Nil,
          n,
          ReviewArticle.ColdReaderAnswer.OpaqueSentences(0)
        ),
        last = ReviewArticle.CheckColdReaderLast.More
      ).toOption.get._1.findings
    assertEquals(countOf(ColdReaderAnswer.TermsUndefined(8)), Nil)
    for n <- List(9, 27, 500).map(ColdReaderAnswer.TermsUndefined(_)) do
      assertEquals(
        countOf(n).map(f => (f.severity, f.problem.value.takeWhile(_ != ' '))),
        List((Severity.Minor, n.toString)),
        n
      )

  test("cold reader: an article whose only MAJOR was the term count is MINOR, and PASS without it"):
    val named = List(
      ReaderFindingAnswer(
        ReviewArticle.ReaderFindingAnswer.Severity("minor"),
        ReviewArticle.ReaderFindingAnswer.Kind("term"),
        ReviewArticle.ReaderFindingAnswer.Where("Intro"),
        ReviewArticle.ReaderFindingAnswer.Quote("Hittite"),
        ReviewArticle.ReaderFindingAnswer.Problem("Hittite is not defined"),
        ReviewArticle.ReaderFindingAnswer.Fix("define it")
      )
    )
    // the reader's instructions may still call the count major: the stated verdict is corrected, not sent back
    val (r, ws) =
      checkColdReader(
        ColdReaderAnswer(
          ReviewArticle.ColdReaderAnswer.Verdict("MAJOR"),
          ReviewArticle.ColdReaderAnswer.ExplainBack("It is about tin."),
          Nil,
          named,
          ReviewArticle.ColdReaderAnswer.TermsUndefined(27),
          ReviewArticle.ColdReaderAnswer.OpaqueSentences(0)
        ),
        last = ReviewArticle.CheckColdReaderLast.More
      ).toOption.get
    assertEquals(r.verdict, Verdict.Minor)
    assertEquals(ws, Nil)
    assertEquals(r.findings.map(_.severity), List(Severity.Minor, Severity.Minor))
    assertEquals(merge(ReviewArticle.MergeRound(1), Nil, List(r), Nil).verdict, Verdict.Minor)
    assertEquals(
      checkColdReader(
        ColdReaderAnswer(
          ReviewArticle.ColdReaderAnswer.Verdict("PASS"),
          ReviewArticle.ColdReaderAnswer.ExplainBack("It is about tin."),
          Nil,
          Nil,
          ReviewArticle.ColdReaderAnswer.TermsUndefined(3),
          ReviewArticle.ColdReaderAnswer.OpaqueSentences(0)
        ),
        last = ReviewArticle.CheckColdReaderLast.More
      ).map(_._1.verdict),
      Right(Verdict.Pass)
    )
    // a named term the reader marks MAJOR keeps its severity
    val major = checkColdReader(
      ColdReaderAnswer(
        ReviewArticle.ColdReaderAnswer.Verdict("MAJOR"),
        ReviewArticle.ColdReaderAnswer.ExplainBack("It is about tin."),
        Nil,
        named.map(_.modify(_.severity).setTo(ReaderFindingAnswer.Severity("major"))),
        ReviewArticle.ColdReaderAnswer.TermsUndefined(27),
        ReviewArticle.ColdReaderAnswer.OpaqueSentences(0)
      ),
      last = ReviewArticle.CheckColdReaderLast.More
    ).toOption.get._1
    assertEquals(major.verdict, Verdict.Major)
    assertEquals(
      major.findings.map(f => (f.kind, f.severity)),
      List(("term", Severity.Major), ("term-count", Severity.Minor))
    )
    // a MAJOR verdict with no major finding and no count is still a finding left out
    assert(
      checkColdReader(
        ColdReaderAnswer(
          ReviewArticle.ColdReaderAnswer.Verdict("MAJOR"),
          ReviewArticle.ColdReaderAnswer.ExplainBack("It is about tin."),
          Nil,
          named,
          ReviewArticle.ColdReaderAnswer.TermsUndefined(3),
          ReviewArticle.ColdReaderAnswer.OpaqueSentences(0)
        ),
        last = ReviewArticle.CheckColdReaderLast.More
      ).isLeft
    )

  test("TermCount.is: the count finding, and the MAJOR one earlier runs stored; nothing else"):
    val legacy = Finding(
      Finding.Id("C4"),
      Reviewer.ColdReader,
      Severity.Major,
      Finding.Kind("term"),
      Finding.Where("explain-back"),
      Finding.Quote(""),
      Finding.Problem("27 terms are undefined (more than 8)"),
      Finding.Fix("define each on first use, and add a Glossary")
    )
    assert(TermCount.is(legacy))
    assert(
      TermCount.is(
        legacy
          .modify(_.kind)
          .setTo(Finding.Kind("term-count"))
          .modify(_.problem)
          .setTo(TermCount.problem(ReviewArticle.ColdReaderAnswer.TermsUndefined(12)))
      )
    )
    assert(!TermCount.is(legacy.modify(_.reviewer).setTo(Reviewer.Rules)))
    assert(!TermCount.is(legacy.modify(_.problem).setTo(Finding.Problem("Hittite is undefined"))))
    assert(!TermCount.is(legacy.modify(_.where).setTo(Finding.Where("Intro"))))
    assert(!TermCount.is(legacy.modify(_.quote).setTo(Finding.Quote("27 terms"))))

  test("skeptic: proposals are verified — quote in the record, vocabulary, citekeys — the rest dropped"):
    val ok = ProposalAnswer(
      ProposalAnswer.Kind("status"),
      ProposalAnswer.Citekey("knapp2016_tin"),
      Some(library.Source.Status.Wire("contested")),
      None,
      None,
      ProposalAnswer.Evidence("a rebuttal"),
      ProposalAnswer.Quote("ten tons of copper and one ton of tin")
    )
    val bad = ProposalAnswer(
      ProposalAnswer.Kind("status"),
      ProposalAnswer.Citekey("knapp2016_tin"),
      Some(library.Source.Status.Wire("contested")),
      None,
      None,
      ProposalAnswer.Evidence("a rebuttal"),
      ProposalAnswer.Quote("Cornish tin went east every summer")
    )
    val (r, ws) =
      checkSkeptic(
        SkepticAnswer(
          ReviewArticle.SkepticAnswer.Verdict("PASS"),
          Nil,
          List(ok, bad),
          ReviewArticle.SkepticAnswer.ClaimsChecked(3)
        ),
        last = ReviewArticle.CheckSkepticLast.More,
        isCk,
        record.get,
        minRatio
      ).toOption.get
    assertEquals(r.proposals.map(_.status), List(Some(Source.Status.Contested)))
    assert(ws.exists(_.contains("not in the record")), ws)

  test("merge: dedupe by where + problem, worst verdict, C and S on the same passage both kept"):
    val m1 = Finding(
      Finding.Id("M1"),
      Reviewer.Mechanical,
      Severity.Minor,
      Finding.Kind("language"),
      Finding.Where("body"),
      Finding.Quote(""),
      Finding.Problem("banned filler"),
      Finding.Fix("")
    )
    val rule = Report(
      Reviewer.Rules,
      Verdict.Minor,
      List(
        Finding(
          Finding.Id("R1"),
          Reviewer.Rules,
          Severity.Minor,
          Finding.Kind("x"),
          Finding.Where("Intro"),
          Finding.Quote("q"),
          Finding.Problem("Banned filler"),
          Finding.Fix("")
        )
      )
    )
    val cold = Report(
      Reviewer.ColdReader,
      Verdict.Minor,
      List(
        Finding(
          Finding.Id("C1"),
          Reviewer.ColdReader,
          Severity.Minor,
          Finding.Kind("term"),
          Finding.Where("Intro"),
          Finding.Quote("q"),
          Finding.Problem("term undefined"),
          Finding.Fix("")
        )
      )
    )
    val skep = Report(
      Reviewer.Skeptic,
      Verdict.Major,
      List(
        Finding(
          Finding.Id("S1"),
          Reviewer.Skeptic,
          Severity.Major,
          Finding.Kind("date"),
          Finding.Where("Intro"),
          Finding.Quote("q"),
          Finding.Problem("the date is wrong"),
          Finding.Fix("")
        )
      )
    )
    val m = merge(ReviewArticle.MergeRound(1), List(m1), List(skep, cold, rule), Nil)
    assertEquals(m.verdict, Verdict.Major)
    assertEquals(m.findings.map(_.id), List("M1", "R1", "C1", "S1"))
    val dup = rule
      .modify(_.findings)
      .setTo(
        rule.findings.map(
          _.modify(_.where).setTo(Finding.Where("body")).modify(_.problem).setTo(Finding.Problem("banned  FILLER"))
        )
      )
    assertEquals(merge(ReviewArticle.MergeRound(1), List(m1), List(dup), Nil).findings.map(_.id), List("M1"))

  test("merge: a finding the fixer rejected and a reviewer raises again is escalated"):
    val before = Finding(
      Finding.Id("S2"),
      Reviewer.Skeptic,
      Severity.Major,
      Finding.Kind("date"),
      Finding.Where("Dates"),
      Finding.Quote("1320 BCE"),
      Finding.Problem("the date is wrong"),
      Finding.Fix("")
    )
    val again =
      before.modify(_.id).setTo(Finding.Id("S1")).modify(_.problem).setTo(Finding.Problem("the date has no support"))
    val other = before
      .modify(_.id)
      .setTo(Finding.Id("S3"))
      .modify(_.reviewer)
      .setTo(Reviewer.Rules)
      .modify(_.problem)
      .setTo(Finding.Problem("x"))
    val m = merge(
      ReviewArticle.MergeRound(2),
      Nil,
      List(Report(Reviewer.Skeptic, Verdict.Major, List(again)), Report(Reviewer.Rules, Verdict.Major, List(other))),
      List(before)
    )
    assertEquals(m.escalated.map(_.id), List("S1"))

  test("gate and rounds"):
    val major = Merged(
      ReviewArticle.Merged.Round(1),
      Verdict.Major,
      List(
        Finding(
          Finding.Id("S1"),
          Reviewer.Skeptic,
          Severity.Major,
          Finding.Kind(""),
          Finding.Where(""),
          Finding.Quote(""),
          Finding.Problem("p"),
          Finding.Fix("")
        )
      ),
      None,
      Nil,
      Nil
    )
    val minor = major
      .modify(_.verdict)
      .setTo(Verdict.Minor)
      .modify(_.findings)
      .setTo(major.findings.map(_.modify(_.severity).setTo(Severity.Minor)))
    assert(done(minor, ReviewArticle.Merged.Round(1), ReviewArticle.RoundCount(3), fix = ReviewArticle.Pass.Fix))
    assert(!done(major, ReviewArticle.Merged.Round(1), ReviewArticle.RoundCount(3), fix = ReviewArticle.Pass.Fix))
    assert(done(major, ReviewArticle.Merged.Round(1), ReviewArticle.RoundCount(3), fix = ReviewArticle.Pass.ReviewOnly))
    assert(done(major, ReviewArticle.Merged.Round(3), ReviewArticle.RoundCount(3), fix = ReviewArticle.Pass.Fix))
    assertEquals(
      rounds(None, ReviewArticle.DefaultRounds(3), ReviewArticle.RoundCap(5), fix = ReviewArticle.Pass.Fix),
      3
    )
    assertEquals(
      rounds(
        Some(ReviewArticle.RequestedRounds(9)),
        ReviewArticle.DefaultRounds(3),
        ReviewArticle.RoundCap(5),
        fix = ReviewArticle.Pass.ReviewOnly
      ),
      5
    )
    assertEquals(
      rounds(
        Some(ReviewArticle.RequestedRounds(1)),
        ReviewArticle.DefaultRounds(3),
        ReviewArticle.RoundCap(5),
        fix = ReviewArticle.Pass.Fix
      ),
      2,
      "a fix needs a round to check it"
    )
    assertEquals(
      rounds(
        Some(ReviewArticle.RequestedRounds(0)),
        ReviewArticle.DefaultRounds(3),
        ReviewArticle.RoundCap(5),
        fix = ReviewArticle.Pass.ReviewOnly
      ),
      1
    )

  private val merged = Merged(
    ReviewArticle.Merged.Round(1),
    Verdict.Major,
    List(
      Finding(
        Finding.Id("S1"),
        Reviewer.Skeptic,
        Severity.Major,
        Finding.Kind("date"),
        Finding.Where("Intro"),
        Finding.Quote(""),
        Finding.Problem("the date is wrong"),
        Finding.Fix("")
      ),
      Finding(
        Finding.Id("C1"),
        Reviewer.ColdReader,
        Severity.Major,
        Finding.Kind("term"),
        Finding.Where("Intro"),
        Finding.Quote(""),
        Finding.Problem("undefined term"),
        Finding.Fix("")
      ),
      Finding(
        Finding.Id("R1"),
        Reviewer.Rules,
        Severity.Minor,
        Finding.Kind("language"),
        Finding.Where("Intro"),
        Finding.Quote(""),
        Finding.Problem("wordy"),
        Finding.Fix("")
      )
    ),
    None,
    Nil,
    Nil
  )

  import com.histmersion.api.research.Article.Edits.{EditAnswer, Op}
  private def edit(op: Op, anchor: EditAnswer.Anchor, text: EditAnswer.Text | Null = null) =
    EditAnswer(op, anchor, Option(text))

  /** The glossed sentence the fixer adds for C1. */
  private val gloss = edit(
    Op.Replace,
    EditAnswer.Anchor("Tin reached the Aegean"),
    EditAnswer.Text("Tin, the scarce half of bronze, reached the Aegean")
  )
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val corpus =
    BodyCorpus(
      isCk,
      isTarget,
      t =>
        if t.value.contains("| a | b |") then
          List(
            Problem.Content.Answer(
              Problem.Content.Requirement
                .FormatClean(Problem.Raw("table-not-rendered"), Problem.Raw("a table"), Problem.Raw(""))
            )
          )
        else Nil
    )
  private def fix(a: FixerAnswer, last: LastRound, before: Article.Body = Article.Body(Good)) =
    checkFix(
      a,
      merged,
      Slug("tin"),
      before,
      ReviewArticle.Snapshot.Kind("concept"),
      corpus,
      isCk,
      record.get,
      minRatio,
      last
    ).left.map(_.map(_.sentence))

  test("fixer: edits applied to the reviewed body, every major decided and a quoted rejection pass"):
    val a = FixerAnswer(
      List(gloss),
      List(Finding.Id("C1")),
      List(
        RejectionAnswer(
          ReviewArticle.RejectionAnswer.Id("S1"),
          ReviewArticle.Rationale("the record says so"),
          ReviewArticle.RejectionAnswer.Citekey("knapp2016_tin"),
          ReviewArticle.RejectionAnswer.Quote("carried ten tons of copper and one ton of tin")
        )
      ),
      Nil,
      ReviewArticle.FixerAnswer.Notes("")
    )
    val (f, ws) = fix(a, last = LastRound.More).toOption.get
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
        RejectionAnswer(
          ReviewArticle.RejectionAnswer.Id("S1"),
          ReviewArticle.Rationale("no"),
          ReviewArticle.RejectionAnswer.Citekey("knapp2016_tin"),
          ReviewArticle.RejectionAnswer.Quote("the ship sank in a storm off Lycia in 1320 BCE")
        ),
        RejectionAnswer(
          ReviewArticle.RejectionAnswer.Id("C1"),
          ReviewArticle.Rationale("it is clear"),
          ReviewArticle.RejectionAnswer.Citekey("knapp2016_tin"),
          ReviewArticle.RejectionAnswer.Quote("ten tons of copper")
        )
      ),
      Nil,
      ReviewArticle.FixerAnswer.Notes("")
    )
    val ps = fix(a, last = LastRound.More).left.toOption.get
    assert(ps.exists(_.contains("rejection of S1: the quote is not in the record")), ps)
    assert(ps.exists(_.contains("C1 is a reader's confusion")), ps)
    val bad = FixerAnswer(
      List(
        edit(Op.InsertAfter, EditAnswer.Anchor("is open [[knapp2016_tin]]."), EditAnswer.Text(" Or [[ghost2020_x]].")),
        edit(
          Op.InsertBefore,
          EditAnswer.Anchor("## Not used"),
          EditAnswer.Text("## Sources cited\n\n- x [[knapp2016_tin]]\n\n")
        ),
        edit(Op.Replace, EditAnswer.Anchor("by land"), EditAnswer.Text("by sea"))
      ),
      List(Finding.Id("S1"), Finding.Id("C1")),
      Nil,
      Nil,
      ReviewArticle.FixerAnswer.Notes("")
    )
    val ps2 = fix(bad, last = LastRound.More).left.toOption.get
    assert(ps2.exists(p => p.startsWith("edit 1 (insertAfter") && p.contains("ghost2020_x")), ps2)
    assert(ps2.exists(_.contains("Sources cited")), ps2)
    assert(
      ps2.exists(
        _ == "edit 3 (replace \"by land\"): anchor: it is not in the article — copy it exactly from the current body (earlier edits included)"
      ),
      ps2
    )
    val undecided = FixerAnswer(List(gloss), List(Finding.Id("C1")), Nil, Nil, ReviewArticle.FixerAnswer.Notes(""))
    assert(
      fix(undecided, last = LastRound.More).left.exists(_.exists(_.contains("S1 is neither applied nor rejected")))
    )
    val idle = FixerAnswer(Nil, List(Finding.Id("C1"), Finding.Id("S1")), Nil, Nil, ReviewArticle.FixerAnswer.Notes(""))
    assert(fix(idle, last = LastRound.More).left.exists(_.exists(_.contains("no edit changed the article"))))

  test("fixer: an uncited paragraph and a formatting error the edits introduce are sent back"):
    val a = FixerAnswer(
      List(
        edit(
          Op.InsertAfter,
          EditAnswer.Anchor("by sea [[knapp2016_tin]]."),
          EditAnswer.Text("\n\nBronze needs tin.\n\n| a | b |")
        )
      ),
      List(Finding.Id("C1"), Finding.Id("S1")),
      Nil,
      Nil,
      ReviewArticle.FixerAnswer.Notes("")
    )
    val ps = fix(a, last = LastRound.More).left.toOption.get
    assert(ps.exists(_.contains("no [[citekey]]")), ps)
    assert(ps.exists(_ == "formatting: table-not-rendered — a table"), ps)
    // a formatting error the body already had is not the edits' doing
    val had = Good + "\n| a | b | [[knapp2016_tin]]\n"
    assert(
      !fix(
        FixerAnswer(
          List(gloss),
          List(Finding.Id("C1"), Finding.Id("S1")),
          Nil,
          Nil,
          ReviewArticle.FixerAnswer.Notes("")
        ),
        last = LastRound.More,
        Article.Body(had)
      ).left
        .exists(_.exists(_.contains("formatting")))
    )

  test("fixer, last attempt: the edits that apply are kept, the others reported; unverifiable rejections stay open"):
    val a = FixerAnswer(
      List(edit(Op.Replace, EditAnswer.Anchor("no such passage"), EditAnswer.Text("x")), gloss),
      List(Finding.Id("C1")),
      List(
        RejectionAnswer(
          ReviewArticle.RejectionAnswer.Id("S1"),
          ReviewArticle.Rationale("no"),
          ReviewArticle.RejectionAnswer.Citekey("knapp2016_tin"),
          ReviewArticle.RejectionAnswer.Quote("nothing like this is in it")
        )
      ),
      Nil,
      ReviewArticle.FixerAnswer.Notes("")
    )
    assert(fix(a, last = LastRound.More).isLeft)
    val (f, ws) = fix(a, last = LastRound.Last).toOption.get
    assertEquals(f.body, Good.replace("Tin reached", "Tin, the scarce half of bronze, reached"))
    assertEquals(f.edits.map(_.applied), List(false, true))
    assertEquals(f.unverified, List("S1"))
    assertEquals(f.rejected, Nil)
    assert(ws.exists(w => w.startsWith("fixer: kept despite — edit 1 (replace \"no such passage\")")), ws)

  test("fixer, last attempt: an edit that links to nothing is never applied, and the rest still are"):
    val a = FixerAnswer(
      List(
        edit(Op.InsertAfter, EditAnswer.Anchor("is open [[knapp2016_tin]]."), EditAnswer.Text(" See [[ghost2020_x]].")),
        gloss
      ),
      List(Finding.Id("C1"), Finding.Id("S1")),
      Nil,
      Nil,
      ReviewArticle.FixerAnswer.Notes("")
    )
    val (f, ws) = fix(a, last = LastRound.Last).toOption.get
    assert(!f.body.value.contains("ghost2020_x"), f.body)
    assert(f.body.value.contains("the scarce half of bronze"))
    assert(ws.exists(_.contains("ghost2020_x")), ws)

  test("fixer, last attempt: a result with a NEW link problem or no text is fatal; an old one is not"):
    // a delete that leaves nothing
    val empty = FixerAnswer(
      List(edit(Op.Delete, EditAnswer.Anchor(Good.trim))),
      List(Finding.Id("C1"), Finding.Id("S1")),
      Nil,
      Nil,
      ReviewArticle.FixerAnswer.Notes("")
    )
    assert(fix(empty, last = LastRound.Last).isLeft)
    val withGhost = Good + "\nOld [[ghost2020_x]] link [[knapp2016_tin]].\n"
    val (f, ws) = fix(
      FixerAnswer(List(gloss), List(Finding.Id("C1"), Finding.Id("S1")), Nil, Nil, ReviewArticle.FixerAnswer.Notes("")),
      last = LastRound.Last,
      Article.Body(withGhost)
    ).toOption.get
    assert(f.body.value.contains("the scarce half of bronze"))
    assert(ws.exists(_.contains("ghost2020_x")), ws)

  test("checkBody: a whole body (a writer's draft) is checked with the same rules"):
    val c =
      checkBody(
        Slug("tin"),
        ReviewArticle.Fix.Body(""),
        ReviewArticle.Snapshot.Kind("concept"),
        Article.Edits.Draft.Body(Article.Edits.Draft.Body.Text("Tin [[ghost2020_x]].\n\n## Sources cited\n")),
        corpus
      )
    assert(c.fatal.fatal)
    assert(
      c.problems.exists(_.sentence.contains("ghost2020_x")) && c.problems.exists(_.sentence.contains("Sources cited")),
      c.problems
    )
    val ok = checkBody(
      Slug("tin"),
      ReviewArticle.Fix.Body(""),
      ReviewArticle.Snapshot.Kind("concept"),
      Article.Edits.Draft.Body(Article.Edits.Draft.Body.Text(Good)),
      corpus
    )
    assertEquals((ok.problems, ok.fatal.fatal, ok.edits), (Nil, false, Nil))
    // an index fragment needs no section set
    assertEquals(
      checkBody(
        Slug("tin"),
        ReviewArticle.Fix.Body(""),
        ReviewArticle.Snapshot.Kind("index"),
        Article.Edits.Draft.Body(Article.Edits.Draft.Body.Text("More tin [[knapp2016_tin]].")),
        corpus
      ).problems,
      Nil
    )

  // ── #958: coverage ─────────────────────────────────────────────────────────────────────────────

  /** `Good` with its hand-written Not used section removed: what an article with a filter holds. */
  private val Generated = Good.substring(0, Good.indexOf("## Not used")).trim + "\n"
  private val cov = Article.Coverage.compute(
    Article.Coverage.ComputeSlug("tin"),
    Article.Filter(topics = List(Filter.Topics("tin"))),
    Article.Coverage.ComputeTotal(3),
    List(
      Coverage.Source(
        api.library.Citekey("knapp2016_tin"),
        api.library.Title("Tin"),
        Some(Year(2016)),
        Source.Status.Current
      ),
      Coverage.Source(
        api.library.Citekey("egypt2001_old"),
        api.library.Title("Egypt"),
        Some(Year(2001)),
        Source.Status.Current
      ),
      Coverage.Source(
        api.library.Citekey("iso2024_new"),
        api.library.Title("Isotopes"),
        Some(Year(2024)),
        Source.Status.Contested
      )
    ),
    Set(api.library.Citekey("knapp2016_tin")),
    List(ApiCitekey("knapp2016_tin")),
    Map(
      api.library.Citekey("egypt2001_old") -> Coverage.Rationale("about Egypt"),
      api.library.Citekey("knapp2016_tin") -> Coverage.Rationale("stale")
    ),
    Nil,
    Nil
  )
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val ckCov = (t: Citekey) => Set("knapp2016_tin", "egypt2001_old", "iso2024_new")(t.value)

  test(
    "mechanical (#958): with a filter, no Not used section is required and a hand-written one or a Method note is MAJOR"
  ):
    assertEquals(
      mechanical(
        Slug("a"),
        ReviewArticle.Snapshot.Kind("concept"),
        Article.Body(Generated),
        isTarget,
        generated = ReviewArticle.MechanicalGenerated.Generated
      ),
      Nil
    )
    assert(
      mechanical(Slug("a"), ReviewArticle.Snapshot.Kind("concept"), Article.Body(Generated), isTarget)
        .exists(_.problem.value.contains("'not used'")),
      "without a filter it is still required"
    )
    val written =
      mechanical(
        Slug("a"),
        ReviewArticle.Snapshot.Kind("concept"),
        Article.Body(Good + "\n_Method: search {topics:[tin]} → 3._\n"),
        isTarget,
        generated = ReviewArticle.MechanicalGenerated.Generated
      )
    assertEquals(written.map(f => (f.kind, f.major)), List((CoverageKind, true), (CoverageKind, true)))
    assert(
      written.head.problem.value.startsWith("a hand-written Not used section") && written(1).problem.value.startsWith(
        "a hand-written Method note"
      )
    )

  test("coverageDrift (#958): an unplaced source is MAJOR, stale reasons minor; ids follow the mechanical ones"):
    val fs = coverageDrift(cov, ReviewArticle.Merged.Round(4))
    assertEquals(
      fs.map(f => (f.id, f.severity, f.kind)),
      List(("M4", Severity.Major, CoverageKind), ("M5", Severity.Minor, CoverageKind))
    )
    assertEquals(fs.head.problem, "1 source the filter returns is neither cited nor given a reason: [[iso2024_new]]")
    assert(fs(1).problem.value.contains("[[knapp2016_tin]]"), fs(1).problem)

  test("checkBody (#958): with a filter, a Not used section the edits write is a problem"):
    val c = checkBody(
      Slug("tin"),
      Article.Body(Generated),
      ReviewArticle.Snapshot.Kind("concept"),
      Article.Edits.Draft.Edits(
        List(
          edit(
            Op.InsertAfter,
            EditAnswer.Anchor("is open [[knapp2016_tin]]."),
            EditAnswer.Text("\n\n## Not used\n\n- [[knapp2016_tin]] — x")
          )
        )
      ),
      corpus.modify(_.generated).setTo(BodyCorpus.Generated.Generated)
    )
    assert(c.problems.exists(_.sentence.startsWith("a hand-written Not used section")), c.problems)

  private val unplaced = Merged(
    ReviewArticle.Merged.Round(1),
    Verdict.Major,
    coverageDrift(cov, ReviewArticle.Merged.Round(1)),
    None,
    Nil,
    Nil
  )
  private def fixCov(a: FixerAnswer, last: LastRound = LastRound.More, c: Option[Coverage] = Some(cov)) =
    checkFix(
      a,
      unplaced,
      Slug("tin"),
      Article.Body(Generated),
      ReviewArticle.Snapshot.Kind("concept"),
      corpus.modify(_.isCitekey).setTo(ckCov).modify(_.generated).setTo(BodyCorpus.Generated.Generated),
      ckCov,
      record.get,
      minRatio,
      last,
      c
    ).left.map(_.map(_.sentence))

  test("fixer (#958): an unplaced source is answered with a reason — no edit needed; the reasons are checked"):
    val ok = FixerAnswer(
      Nil,
      List(Finding.Id("M1"), Finding.Id("M2")),
      Nil,
      Nil,
      ReviewArticle.FixerAnswer.Notes(""),
      List(
        Coverage.Reason(
          api.library.Citekey("[[iso2024_new]]"),
          Coverage.Rationale("Its isotopes date a later trade.")
        ),
        Coverage.Reason(api.library.Citekey("knapp2016_tin"), Coverage.Rationale(""))
      )
    )
    val (f, ws) = fixCov(ok).fold(ps => fail(ps.mkString("; ")), identity)
    assertEquals(
      f.reasons,
      List(
        Coverage.Reason(
          api.library.Citekey("iso2024_new"),
          Coverage.Rationale("Its isotopes date a later trade.")
        ),
        Coverage.Reason(api.library.Citekey("knapp2016_tin"), Coverage.Rationale(""))
      )
    )
    assertEquals((f.body, ws), (Generated, Nil))
    // M1 applied but nothing placed; a reason for a cited source; one outside the filter; one for a source with no reason to remove
    val bad = FixerAnswer(
      Nil,
      List(Finding.Id("M1"), Finding.Id("M2")),
      Nil,
      Nil,
      ReviewArticle.FixerAnswer.Notes(""),
      List(
        Coverage.Reason(
          api.library.Citekey("knapp2016_tin"),
          Coverage.Rationale("not needed")
        ),
        Coverage
          .Reason(api.library.Citekey("zz1999_gone"), Coverage.Rationale("never read")),
        Coverage.Reason(api.library.Citekey("egypt2001_old"), Coverage.Rationale(""))
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
      List(
        edit(
          Op.InsertAfter,
          EditAnswer.Anchor("is open [[knapp2016_tin]]."),
          EditAnswer.Text(" Isotopes point east [[iso2024_new]] (contested).")
        )
      ),
      List(Finding.Id("M1"), Finding.Id("M2")),
      Nil,
      Nil,
      ReviewArticle.FixerAnswer.Notes(""),
      List(Coverage.Reason(api.library.Citekey("knapp2016_tin"), Coverage.Rationale("")))
    )
    assert(fixCov(cite).isRight, fixCov(cite))
    // an article without a filter takes no reasons
    assert(fixCov(ok, c = None).left.exists(_.exists(_.contains("records no filter"))))

  test("evidence: verified against the record, dropped with a warning when not there, kept by the merge"):
    val ev = evidenceCheck(isCk, record.get, minRatio)
    val f = FindingAnswer(
      ReviewArticle.FindingAnswer.Severity("major"),
      ReviewArticle.FindingAnswer.Kind("contradictory"),
      ReviewArticle.FindingAnswer.Where("Intro"),
      ReviewArticle.FindingAnswer.Quote("by sea"),
      ReviewArticle.FindingAnswer.Problem("the record says otherwise"),
      ReviewArticle.FindingAnswer.Fix("fix"),
      List(
        EvidenceAnswer(
          ReviewArticle.EvidenceAnswer.Citekey("knapp2016_tin"),
          Some(ReviewArticle.EvidenceAnswer.Offset(120)),
          ReviewArticle.EvidenceAnswer.Quote("carried ten tons of copper and one ton of tin")
        ),
        EvidenceAnswer(
          ReviewArticle.EvidenceAnswer.Citekey("knapp2016_tin"),
          None,
          ReviewArticle.EvidenceAnswer.Quote("the cargo was Cornish")
        ),
        EvidenceAnswer(
          ReviewArticle.EvidenceAnswer.Citekey("ghost2020_x"),
          None,
          ReviewArticle.EvidenceAnswer.Quote("anything at all here")
        )
      )
    )
    val (r, ws) =
      checkSkeptic(
        SkepticAnswer(
          ReviewArticle.SkepticAnswer.Verdict("MAJOR"),
          List(f),
          Nil,
          ReviewArticle.SkepticAnswer.ClaimsChecked(1)
        ),
        last = ReviewArticle.CheckSkepticLast.More,
        isCk,
        record.get,
        minRatio
      ).toOption.get
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
    val (rr, _) = checkRules(
      RulesAnswer(ReviewArticle.RulesAnswer.Verdict("MAJOR"), List(f), ReviewArticle.RulesAnswer.Filter("f"), Nil, Nil),
      last = ReviewArticle.CheckRulesLast.More,
      ev
    ).toOption.get
    assertEquals(rr.findings.head.evidence.size, 1)
    assertEquals(
      checkRules(
        RulesAnswer(
          ReviewArticle.RulesAnswer.Verdict("MAJOR"),
          List(f),
          ReviewArticle.RulesAnswer.Filter("f"),
          Nil,
          Nil
        ),
        last = ReviewArticle.CheckRulesLast.More
      ).toOption.get._1.findings.head.evidence,
      Nil
    )
    // a duplicate's evidence joins the kept finding, and the fixer reads it under the finding
    val e2 =
      Evidence(Citekey("knapp2016_tin"), None, ReviewArticle.Evidence.Quote("one ton of tin"), Evidence.Ratio(1.0))
    val dup = rr.findings.head.copy(id = Finding.Id("R9"), evidence = List(e2))
    val m = merge(ReviewArticle.MergeRound(1), Nil, List(rr, rr.modify(_.findings).setTo(List(dup))), Nil)
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
    assertEquals(fixerTurns(12, merged, 1, ReviewArticle.TurnCap(40)), 15)
    assertEquals(fixerTurns(12, merged, 2, ReviewArticle.TurnCap(40)), 18)
    assertEquals(fixerTurns(12, merged, 10, ReviewArticle.TurnCap(20)), 20, "capped")
    assertEquals(
      fixerTurns(12, merged, 1, ReviewArticle.TurnCap(5)),
      12,
      "a cap below the agent's own turns never cuts them"
    )
    val readerMinor =
      merged
        .modify(_.findings)
        .setTo(
          List(
            Finding(
              Finding.Id("C2"),
              Reviewer.ColdReader,
              Severity.Minor,
              Finding.Kind("term"),
              Finding.Where("x"),
              Finding.Quote(""),
              Finding.Problem("p"),
              Finding.Fix("")
            )
          )
        )
    assertEquals(fixerTurns(12, readerMinor, 1, ReviewArticle.TurnCap(40)), 12, "a reader's minor needs no lookup")

  test("bookkeeping: the log line"):
    val open = List(
      Finding(
        Finding.Id("S1"),
        Reviewer.Skeptic,
        Severity.Major,
        Finding.Kind("date"),
        Finding.Where("Dates"),
        Finding.Quote(""),
        Finding.Problem("the date is wrong"),
        Finding.Fix(""),
        escalated = Finding.Escalated.Escalated
      )
    )
    val m = Merged(ReviewArticle.Merged.Round(2), Verdict.Major, open, None, Nil, Nil)
    val o = Outcome(
      ReviewArticle.Outcome.Slug("sea_peoples"),
      ReviewArticle.Outcome.Rounds(2),
      Verdict.Major,
      open,
      ReviewArticle.Outcome.Applied(3),
      ReviewArticle.Outcome.Rejected(1),
      fixed = ReviewArticle.Outcome.Fixed.Fixed,
      ReviewArticle.Outcome.Approvals(0)
    )
    assertEquals(
      logLine(o, m, Run.Id("abcdef01-93ac-ef8b-cd70-71607816bfcb")),
      "review-article [[sea_peoples]]: round 2 → MAJOR (R:0 C:0 S:1 M:0), fixer applied 3 / rejected 1 (agent run abcdef01)"
    )

  test("verdicts order PASS < MINOR < MAJOR and parse in any case"):
    assertEquals(Verdict.worst(List(Verdict.Minor, Verdict.Pass)), Verdict.Minor)
    assertEquals(Verdict.parse(ReviewArticle.Verdict.Wire("major")), Some(Verdict.Major))
    assertEquals(Verdict.of(Nil), Verdict.Pass)

  // ── the review ledger: checks, the verification pass, declining a minor ──────────────────────────

  private val revised = Article.Body(Good.replace("Tin reached", "Tin, the scarce half of bronze, reached"))
  private val askedTin = Finding(
    Finding.Id("R1"),
    Reviewer.Rules,
    Severity.Major,
    Finding.Kind("term"),
    Finding.Where("Intro"),
    Finding.Quote("Tin reached the Aegean by sea"),
    Finding.Problem("tin is not defined"),
    Finding.Fix("")
  )
  private val askedDate =
    Finding(
      Finding.Id("R4"),
      Reviewer.Rules,
      Severity.Major,
      Finding.Kind("date"),
      Finding.Where("Disagreements"),
      Finding.Quote("Two dates are argued"),
      Finding.Problem("no date"),
      Finding.Fix("")
    )
  private val prior = Prior(List(askedTin, askedDate), revised, minRatio)

  test("checks: one per finding asked about, each id one of them, each quote in the CURRENT article"):
    val ok = List(
      CheckAnswer(
        ReviewArticle.CheckAnswer.Id("R1"),
        fixed = ReviewArticle.CheckAnswer.Fixed.Fixed,
        ReviewArticle.CheckAnswer.Quote("Tin, the scarce half of bronze"),
        ReviewArticle.CheckAnswer.Note("glossed")
      ),
      CheckAnswer(
        ReviewArticle.CheckAnswer.Id("R4"),
        fixed = ReviewArticle.CheckAnswer.Fixed.Unfixed,
        ReviewArticle.CheckAnswer.Quote("Two dates are argued"),
        ReviewArticle.CheckAnswer.Note("still undated")
      )
    )
    assertEquals(
      checkChecks(Reviewer.Rules, prior, ok, last = ReviewArticle.LastRound.More),
      (
        List(
          Check(
            ReviewArticle.Check.Id("R1"),
            ReviewArticle.Check.Fixed.Fixed,
            ReviewArticle.Check.Quote("Tin, the scarce half of bronze"),
            ReviewArticle.Check.Note("glossed")
          ),
          Check(
            ReviewArticle.Check.Id("R4"),
            ReviewArticle.Check.Fixed.Unfixed,
            ReviewArticle.Check.Quote("Two dates are argued"),
            ReviewArticle.Check.Note("still undated")
          )
        ),
        Nil,
        Nil
      )
    )
    val bad = List(
      CheckAnswer(
        ReviewArticle.CheckAnswer.Id("R1"),
        fixed = ReviewArticle.CheckAnswer.Fixed.Fixed,
        ReviewArticle.CheckAnswer.Quote("Tin, the precious half of bronze"),
        ReviewArticle.CheckAnswer.Note("")
      ),
      CheckAnswer(
        ReviewArticle.CheckAnswer.Id("S2"),
        fixed = ReviewArticle.CheckAnswer.Fixed.Fixed,
        ReviewArticle.CheckAnswer.Quote("x"),
        ReviewArticle.CheckAnswer.Note("")
      ),
      CheckAnswer(
        ReviewArticle.CheckAnswer.Id("R4"),
        fixed = ReviewArticle.CheckAnswer.Fixed.Unfixed,
        ReviewArticle.CheckAnswer.Quote(""),
        ReviewArticle.CheckAnswer.Note("")
      )
    )
    val (cs, problems, ws) = checkChecks(Reviewer.Rules, prior, bad, last = ReviewArticle.LastRound.More)
    val ps = problems.map(_.sentence)
    assertEquals((cs, ws), (Nil, Nil))
    assert(ps.exists(_.startsWith("check of R1: the quote is not in the current article")), ps)
    assert(ps.exists(_.startsWith("check of 'S2': not one of the findings you were asked to check (R1, R4)")), ps)
    assert(ps.exists(_.startsWith("check of R4: no quote — quote the current text that shows it still there")), ps)
    assert(
      ps.contains("R1: no check — say whether it is fixed") && ps.contains("R4: no check — say whether it is fixed"),
      ps
    )
    // the last attempt keeps what passed; the rest stays open, with a warning
    val (cs2, ps2, ws2) = checkChecks(Reviewer.Rules, prior, ok.take(1), last = ReviewArticle.LastRound.Last)
    assertEquals((cs2.map(_.id), ps2), (List("R1"), Nil))
    assertEquals(ws2, List("rules: R4: no check — say whether it is fixed (left open)"))

  test(
    "checks: an empty quote passes only when nothing is left to quote (the passage is gone, or there never was one)"
  ):
    val gone = Prior(
      List(askedDate),
      Article.Body(Good.replace("Two dates are argued", "The wreck dates to 1320 BCE")),
      minRatio
    )
    assertEquals(
      checkChecks(
        Reviewer.Rules,
        gone,
        List(
          CheckAnswer(
            ReviewArticle.CheckAnswer.Id("R4"),
            fixed = ReviewArticle.CheckAnswer.Fixed.Fixed,
            ReviewArticle.CheckAnswer.Quote(""),
            ReviewArticle.CheckAnswer.Note("deleted")
          )
        ),
        last = ReviewArticle.LastRound.More
      )._1
        .map(_.id),
      List("R4")
    )
    val count = Finding(
      Finding.Id("C3"),
      Reviewer.ColdReader,
      Severity.Major,
      Finding.Kind("term"),
      Finding.Where("explain-back"),
      Finding.Quote(""),
      Finding.Problem("12 terms are undefined (more than 8)"),
      Finding.Fix("")
    )
    assertEquals(
      checkChecks(
        Reviewer.ColdReader,
        Prior(List(count), Article.Body(Good), minRatio),
        List(
          CheckAnswer(
            ReviewArticle.CheckAnswer.Id("C3"),
            fixed = ReviewArticle.CheckAnswer.Fixed.Unfixed,
            ReviewArticle.CheckAnswer.Quote(""),
            ReviewArticle.CheckAnswer.Note("still 12")
          )
        ),
        last = ReviewArticle.LastRound.More
      )._2,
      Nil
    )

  test("a full panel's answer carries checks only when the ledger asked; they are verified like the findings"):
    val withChecks = RulesAnswer(
      ReviewArticle.RulesAnswer.Verdict("PASS"),
      Nil,
      ReviewArticle.RulesAnswer.Filter("f"),
      checks = List(
        CheckAnswer(
          ReviewArticle.CheckAnswer.Id("R1"),
          fixed = ReviewArticle.CheckAnswer.Fixed.Fixed,
          ReviewArticle.CheckAnswer.Quote("Tin, the scarce half of bronze"),
          ReviewArticle.CheckAnswer.Note("")
        )
      )
    )
    assertEquals(
      checkRules(
        withChecks,
        last = ReviewArticle.CheckRulesLast.More,
        prior = Some(Prior(List(askedTin), revised, minRatio))
      )
        .map(_._1.checks.map(_.id)),
      Right(List("R1"))
    )
    assert(
      checkRules(
        withChecks.modify(_.checks).setTo(Nil),
        last = ReviewArticle.CheckRulesLast.More,
        prior = Some(Prior(List(askedTin), revised, minRatio))
      ).isLeft,
      "an open finding of its own must be checked"
    )
    assertEquals(
      checkRules(withChecks, last = ReviewArticle.CheckRulesLast.More, prior = None).map(_._1.checks),
      Right(Nil),
      "no ledger: no checks"
    )
    val reader = ColdReaderAnswer(
      ReviewArticle.ColdReaderAnswer.Verdict("PASS"),
      ReviewArticle.ColdReaderAnswer.ExplainBack("It asks. It answers. They disagree. It is open. Done."),
      Nil,
      Nil,
      ReviewArticle.ColdReaderAnswer.TermsUndefined(0),
      ReviewArticle.ColdReaderAnswer.OpaqueSentences(0),
      None,
      List(
        CheckAnswer(
          ReviewArticle.CheckAnswer.Id("C1"),
          fixed = ReviewArticle.CheckAnswer.Fixed.Fixed,
          ReviewArticle.CheckAnswer.Quote("the scarce half of bronze"),
          ReviewArticle.CheckAnswer.Note("")
        )
      )
    )
    val readerPrior = Some(
      Prior(
        List(
          Finding(
            Finding.Id("C1"),
            Reviewer.ColdReader,
            Severity.Major,
            Finding.Kind("term"),
            Finding.Where("Intro"),
            Finding.Quote("Tin reached"),
            Finding.Problem("tin"),
            Finding.Fix("")
          )
        ),
        revised,
        minRatio
      )
    )
    assertEquals(
      checkColdReader(reader, last = ReviewArticle.CheckColdReaderLast.More, readerPrior)
        .map(_._1.checks.map(_.fixed)),
      Right(List(true))
    )

  test("scope: inside the edited passages anything stands; outside, a MAJOR only as a factual error with evidence"):
    val edited = Article.Body("Tin, the scarce half of bronze, reached the Aegean by sea [[knapp2016_tin]].")
    val ev =
      Evidence(Citekey("knapp2016_tin"), None, ReviewArticle.Evidence.Quote("ten tons of copper"), Evidence.Ratio(1.0))
    val inside = Finding(
      Finding.Id("S1"),
      Reviewer.Skeptic,
      Severity.Major,
      Finding.Kind("synth"),
      Finding.Where("Intro"),
      Finding.Quote("the scarce half of bronze"),
      Finding.Problem("a bridge claim"),
      Finding.Fix("")
    )
    val outside = inside.modify(_.quote).setTo(Finding.Quote("Two dates are argued"))
    assertEquals(scope(inside, edited, minRatio), (inside, None))
    assertEquals(
      scope(
        outside.modify(_.kind).setTo(Finding.Kind("contradictory")).modify(_.evidence).setTo(List(ev)),
        edited,
        minRatio
      )._2,
      None
    )
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
        CheckAnswer(
          ReviewArticle.CheckAnswer.Id("R1"),
          fixed = ReviewArticle.CheckAnswer.Fixed.Fixed,
          ReviewArticle.CheckAnswer.Quote("Tin, the scarce half of bronze"),
          ReviewArticle.CheckAnswer.Note("")
        ),
        CheckAnswer(
          ReviewArticle.CheckAnswer.Id("R4"),
          fixed = ReviewArticle.CheckAnswer.Fixed.Unfixed,
          ReviewArticle.CheckAnswer.Quote("Two dates are argued"),
          ReviewArticle.CheckAnswer.Note("still undated")
        )
      ),
      List(
        FindingAnswer(
          ReviewArticle.FindingAnswer.Severity("major"),
          ReviewArticle.FindingAnswer.Kind("recall"),
          ReviewArticle.FindingAnswer.Where("Open questions"),
          ReviewArticle.FindingAnswer.Quote("The source of the tin is open"),
          ReviewArticle.FindingAnswer.Problem("the record names the source"),
          ReviewArticle.FindingAnswer.Fix("say it"),
          List(
            EvidenceAnswer(
              ReviewArticle.EvidenceAnswer.Citekey("knapp2016_tin"),
              None,
              ReviewArticle.EvidenceAnswer.Quote("one ton of tin")
            )
          )
        ),
        FindingAnswer(
          ReviewArticle.FindingAnswer.Severity("major"),
          ReviewArticle.FindingAnswer.Kind("weight"),
          ReviewArticle.FindingAnswer.Where("Open questions"),
          ReviewArticle.FindingAnswer.Quote("The source of the tin is open"),
          ReviewArticle.FindingAnswer.Problem("too short"),
          ReviewArticle.FindingAnswer.Fix("expand")
        )
      ),
      List(
        ReviewArticle.Recheck.Lessons("one"),
        ReviewArticle.Recheck.Lessons("two"),
        ReviewArticle.Recheck.Lessons("three"),
        ReviewArticle.Recheck.Lessons("four")
      )
    )
    val (rc, ws) =
      checkRecheck(
        Reviewer.Rules,
        a,
        prior,
        ReviewArticle.CheckRecheckEdited("Tin, the scarce half of bronze"),
        ev,
        last = ReviewArticle.CheckRecheckLast.More
      ).toOption.get
    assertEquals(rc.checks.map(c => (c.id, c.fixed)), List(("R1", true), ("R4", false)))
    assertEquals(
      rc.findings.map(f => (f.kind, f.severity)),
      List(("recall", Severity.Major), ("weight", Severity.Minor))
    )
    assertEquals(rc.lessons, List("one", "two", "three"))
    assert(ws.exists(_.contains("\"too short\" is outside the edited passages")), ws)
    assert(
      checkRecheck(
        Reviewer.Rules,
        a.modify(_.checks).setTo(Nil),
        prior,
        ReviewArticle.CheckRecheckEdited(""),
        ev,
        last = ReviewArticle.CheckRecheckLast.More
      ).isLeft
    )
    assert(
      checkRecheck(
        Reviewer.Rules,
        RecheckAnswer(),
        prior,
        ReviewArticle.CheckRecheckEdited(""),
        ev,
        last = ReviewArticle.CheckRecheckLast.Last
      ).isRight,
      "the last attempt keeps what passed"
    )

  test("fixer: a minor may be declined with a reason; a MAJOR may not; an unknown id is sent back"):
    val ok = FixerAnswer(
      List(gloss),
      List(Finding.Id("C1")),
      List(
        RejectionAnswer(
          ReviewArticle.RejectionAnswer.Id("S1"),
          ReviewArticle.Rationale("the record says so"),
          ReviewArticle.RejectionAnswer.Citekey("knapp2016_tin"),
          ReviewArticle.RejectionAnswer.Quote("carried ten tons of copper and one ton of tin")
        )
      ),
      Nil,
      ReviewArticle.FixerAnswer.Notes(""),
      wontFix = List(
        WontFixAnswer(ReviewArticle.WontFixAnswer.Id("R1"), ReviewArticle.Rationale("wordy, but exact"))
      )
    )
    assertEquals(
      fix(ok, last = LastRound.More).map(_._1.wontFix),
      Right(List(WontFix(ReviewArticle.WontFix.Id("R1"), ReviewArticle.Rationale("wordy, but exact"))))
    )
    val bad = ok
      .modify(_.wontFix)
      .setTo(
        List(
          WontFixAnswer(ReviewArticle.WontFixAnswer.Id("S1"), ReviewArticle.Rationale("no")),
          WontFixAnswer(ReviewArticle.WontFixAnswer.Id("R9"), ReviewArticle.Rationale("x")),
          WontFixAnswer(ReviewArticle.WontFixAnswer.Id("R1"), ReviewArticle.Rationale(" "))
        )
      )
    val ps = fix(bad, last = LastRound.More).left.toOption.get
    assert(ps.contains("S1 is MAJOR: apply it or reject it — only a minor finding can be declined"), ps)
    assert(ps.contains("won't fix 'R9': there is no such finding") && ps.contains("won't fix R1: give the reason"), ps)

  test(
    "fixer: a coverage MAJOR on an article that records no filter may be declined (it stays open); with a filter it may not"
  ):
    val cover = Finding(
      Finding.Id("R8"),
      Reviewer.Rules,
      Severity.Major,
      Finding.Kind("coverage"),
      Finding.Where("Not used"),
      Finding.Quote(""),
      Finding.Problem("the reconstructed search returns 94 sources; most are neither cited nor excluded"),
      Finding.Fix("record the filter")
    )
    val m = merged.modify(_.findings).setTo(merged.findings :+ cover)
    def run(a: FixerAnswer, c: Option[Coverage]) =
      checkFix(
        a,
        m,
        Slug("tin"),
        Article.Body(Good),
        ReviewArticle.Snapshot.Kind("concept"),
        corpus,
        isCk,
        record.get,
        minRatio,
        ReviewArticle.LastRound.More,
        c
      ).left.map(_.map(_.sentence))
    val base = FixerAnswer(
      List(gloss),
      List(Finding.Id("C1")),
      List(
        RejectionAnswer(
          ReviewArticle.RejectionAnswer.Id("S1"),
          ReviewArticle.Rationale("the record says so"),
          ReviewArticle.RejectionAnswer.Citekey("knapp2016_tin"),
          ReviewArticle.RejectionAnswer.Quote("carried ten tons of copper and one ton of tin")
        )
      ),
      Nil,
      ReviewArticle.FixerAnswer.Notes("")
    )
    // left out: sent back, told how to answer it
    val missing = run(base, None).left.toOption.get
    assert(
      missing.exists(p =>
        p.startsWith("major finding R8 is neither applied, rejected nor declined") && p.contains("wontFix")
      ),
      missing
    )
    val declined = base
      .modify(_.wontFix)
      .setTo(
        List(
          WontFixAnswer(
            ReviewArticle.WontFixAnswer.Id("R8"),
            ReviewArticle.Rationale("recording the filter is not an edit")
          )
        )
      )
    assertEquals(run(declined, None).map(_._1.wontFix.map(_.id)), Right(List("R8")))
    assert(needsFilter(cover, None) && !needsFilter(cover, Some(cov)) && !needsFilter(merged.findings.head, None))
    // the ledger keeps a declined MAJOR open, and not claimed
    val led = ReviewLedger.Ledger(
      ReviewLedger.Ledger.Slug("tin"),
      m.findings.map(
        ReviewLedger.Entry(
          _,
          ReviewLedger.Entry.Run("b7f86a38-be91-c3c1-64ae-ba52940975e8"),
          ReviewLedger.Entry.Round(1)
        )
      )
    )
    val after = ReviewLedger.applyFix(
      led,
      run(declined, None).toOption.get._1,
      ReviewLedger.ApplyFixRun("b7f86a38-be91-c3c1-64ae-ba52940975e8"),
      ReviewLedger.ApplyFixRound(1)
    )
    assert(after.entry(Finding.Id("R8")).exists(e => e.open && !e.claimed.claimed), after.entries)
    // with a recorded filter the article's Not used list is generated: the finding is answered in `reasons`
    val withFilter = run(declined, Some(cov)).left.toOption.get
    assert(withFilter.exists(_.startsWith("R8 is MAJOR: apply it or reject it")), withFilter)
    assert(withFilter.exists(_ == "major finding R8 is neither applied nor rejected"), withFilter)

  test("the cited sources reach a reviewer as one line each: citekey, year, status, a short title"):
    val long = "A " * 80
    val lines = ReviewArticle.citedLines(
      List(
        CitedSource(
          api.library.Citekey("watkins1995_dragon"),
          (Some("How to Kill a Dragon")).map(api.library.Title.apply),
          (Some(1995)).map(Year.apply),
          Some(Source.Status.Foundational)
        ),
        CitedSource(
          api.library.Citekey("x2020_y"),
          (Some(long)).map(api.library.Title.apply),
          None,
          Some(Source.Status.Contested)
        ),
        CitedSource(api.library.Citekey("ghost2001_z"), None, None, None)
      )
    )
    val ls = lines.split("\n").toList
    assertEquals(ls.head, "- [[watkins1995_dragon]] · 1995 · foundational · How to Kill a Dragon")
    assert(ls(1).startsWith("- [[x2020_y]] · contested · A A") && ls(1).endsWith("…") && ls(1).length < 160, ls(1))
    assertEquals(ls(2), "- [[ghost2001_z]] · not in the corpus")
    assertEquals(ReviewArticle.citedLines(Nil), "")

  // ── spot-checks (#1840) ─────────────────────────────────────────────────────────────────────────

  private val records2 =
    record + (Citekey("tin2024_iso") -> AgentVerify.SourceBody(
      "We show that the tin of the Uluburun cargo came from Central Asia."
    ))
  private val spotEv = evidenceCheck(records2.contains, records2.get, minRatio)
  private val spotBody = Article.Body(Good + "\nThe tin came from Central Asia [[tin2024_iso]].\n")
  private val cited2 = Set(Citekey("knapp2016_tin"), Citekey("tin2024_iso"))
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def read(ck: String, tool: ToolUse.Tool = ToolUse.Tool("get_source")) =
    AgentVerify.ToolUse(tool, ToolUse.Input(s"""{"citekey":"$ck","section":"findings"}"""))
  private val spotTin = SpotCheckAnswer(
    ReviewArticle.SpotCheckAnswer.Citekey("knapp2016_tin"),
    ReviewArticle.SpotCheckAnswer.Claim("Tin reached the Aegean by sea"),
    ReviewArticle.SpotCheckAnswer.Verdict("attributable"),
    ReviewArticle.SpotCheckAnswer.RecordQuote("The Uluburun ship carried ten tons of copper and one ton of tin.")
  )
  private val spotIso = SpotCheckAnswer(
    ReviewArticle.SpotCheckAnswer.Citekey("tin2024_iso"),
    ReviewArticle.SpotCheckAnswer.Claim("The tin came from Central Asia"),
    ReviewArticle.SpotCheckAnswer.Verdict("attributable"),
    ReviewArticle.SpotCheckAnswer.RecordQuote("the tin of the Uluburun cargo came from Central Asia")
  )
  private def rule(calls: List[AgentVerify.ToolUse], required: SpotRule.Required = SpotRule.Required(2)) =
    SpotRule(required, cited2, spotBody, calls, minRatio)

  test("spot-checks: how many are required — the configured number, fewer when fewer sources are cited"):
    assertEquals(spotChecksRequired(ReviewArticle.SpotCheckMin(3), ReviewArticle.CitedInCorpusCount(10)), 3)
    assertEquals(spotChecksRequired(ReviewArticle.SpotCheckMin(3), ReviewArticle.CitedInCorpusCount(2)), 2)
    assertEquals(spotChecksRequired(ReviewArticle.SpotCheckMin(3), ReviewArticle.CitedInCorpusCount(0)), 0)
    assertEquals(
      spotChecksRequired(ReviewArticle.SpotCheckMin(0), ReviewArticle.CitedInCorpusCount(5)),
      0,
      "0 = not enforced"
    )

  test("spot-checks: a get_source or passages call naming the citekey reads it; nothing else does"):
    assert(reads(read("knapp2016_tin"), Citekey("knapp2016_tin")))
    assert(reads(read("Knapp2016_Tin", ToolUse.Tool("passages")), Citekey("knapp2016_tin")), "case does not matter")
    assert(!reads(read("knapp2016_tin", ToolUse.Tool("search")), Citekey("knapp2016_tin")))
    assert(
      !reads(
        AgentVerify
          .ToolUse(ToolUse.Tool("query"), ToolUse.Input("SELECT * FROM sources WHERE citekey = 'knapp2016_tin'")),
        Citekey("knapp2016_tin")
      )
    )
    assert(!reads(read("tin2024_iso"), Citekey("knapp2016_tin")))

  test("spot-checks: each one verified by a tool call of the step and a quote found in the record"):
    val (ok, ps, ws) = checkSpotChecks(
      List(spotTin, spotIso),
      rule(List(read("knapp2016_tin"), read("tin2024_iso", ToolUse.Tool("passages")))),
      spotEv,
      last = ReviewArticle.CheckSpotChecksLast.More
    )
    assertEquals((ps, ws), (Nil, Nil))
    assertEquals(
      ok.map(s => (s.citekey, s.tool, s.verdict)),
      List(("knapp2016_tin", "get_source", "attributable"), ("tin2024_iso", "passages", "attributable"))
    )

  test(
    "spot-checks: an answer from the brief alone (no tool call) is sent back, and kept with a warning on the last attempt"
  ):
    val (ok, problems, ws) =
      checkSpotChecks(List(spotTin, spotIso), rule(Nil), spotEv, last = ReviewArticle.CheckSpotChecksLast.More)
    val ps = problems.map(_.sentence)
    assertEquals((ok, ws), (Nil, Nil))
    assert(ps.head.startsWith("spot-checks: 0 of the 2 required are verified"), ps)
    assert(ps.head.contains("0 get_source/passages call(s)"), ps)
    assert(ps.exists(_.contains("[[knapp2016_tin]]): no get_source or passages call for it")), ps)
    val (ok2, ps2, ws2) =
      checkSpotChecks(List(spotTin, spotIso), rule(Nil), spotEv, last = ReviewArticle.CheckSpotChecksLast.Last)
    assertEquals((ok2, ps2), (Nil, Nil))
    assert(ws2.nonEmpty && ws2.forall(_.startsWith("rules: unverified — ")), ws2)
    // no spot-check at all is the same
    assert(
      checkSpotChecks(Nil, rule(Nil), spotEv, last = ReviewArticle.CheckSpotChecksLast.More)._2.head.sentence
        .startsWith("spot-checks: 0 of the 2")
    )

  test("spot-checks: the required ones must be on different sources; a check of one source twice counts once"):
    val twice = List(spotTin, spotTin.modify(_.claim).setTo(SpotCheckAnswer.Claim("Two dates are argued")))
    val (_, ps, _) =
      checkSpotChecks(twice, rule(List(read("knapp2016_tin"))), spotEv, last = ReviewArticle.CheckSpotChecksLast.More)
    assert(ps.head.sentence.startsWith("spot-checks: 1 of the 2 required"), ps)
    assertEquals(
      checkSpotChecks(
        twice,
        rule(List(read("knapp2016_tin")), required = SpotRule.Required(1)),
        spotEv,
        last = ReviewArticle.CheckSpotChecksLast.More
      )._1.size,
      2
    )

  test("spot-checks: an uncited source, a claim not in the article, a bad verdict or a quote not in the record fails"):
    val calls = List(read("knapp2016_tin"), read("tin2024_iso"), read("atlantis"))
    def problem(a: SpotCheckAnswer) =
      checkSpotChecks(
        List(a),
        rule(calls, required = ReviewArticle.SpotRule.Required(1)),
        spotEv,
        last = ReviewArticle.CheckSpotChecksLast.More
      )._2.drop(1).head.sentence
    assert(
      problem(spotTin.modify(_.citekey).setTo(SpotCheckAnswer.Citekey("atlantis")))
        .contains("the article does not cite it")
    )
    assert(
      problem(spotTin.modify(_.claim).setTo(SpotCheckAnswer.Claim("Tin reached Crete by land in the Iron Age")))
        .contains("the claim is not in the article")
    )
    assert(
      problem(spotTin.modify(_.verdict).setTo(SpotCheckAnswer.Verdict("supported")))
        .contains("verdict 'supported' is not one of")
    )
    assert(
      problem(
        spotTin.modify(_.recordQuote).setTo(SpotCheckAnswer.RecordQuote("The ship carried amber from the Baltic coast"))
      )
        .contains("the quote is not in the record")
    )
    assert(problem(spotTin.modify(_.citekey).setTo(SpotCheckAnswer.Citekey(""))).contains("no citekey"))

  test("spot-checks: beyond the required number a failing one is dropped with a warning, not sent back"):
    val bad =
      spotIso.modify(_.recordQuote).setTo(SpotCheckAnswer.RecordQuote("an invented sentence that is in no record"))
    val (ok, ps, ws) = checkSpotChecks(
      List(spotTin, bad),
      rule(List(read("knapp2016_tin"), read("tin2024_iso")), required = SpotRule.Required(1)),
      spotEv,
      last = ReviewArticle.CheckSpotChecksLast.More
    )
    assertEquals((ok.map(_.citekey), ps), (List("knapp2016_tin"), Nil))
    assert(ws.head.startsWith("rules: dropped — spot-check 2 ([[tin2024_iso]])"), ws)

  test("checkRules: spot-checks are enforced only when a rule is given, and land in the report"):
    val a = RulesAnswer(
      ReviewArticle.RulesAnswer.Verdict("PASS"),
      Nil,
      ReviewArticle.RulesAnswer.Filter("f"),
      spotChecks = List(spotTin, spotIso)
    )
    assert(
      checkRules(a.modify(_.spotChecks).setTo(Nil), last = ReviewArticle.CheckRulesLast.More).isRight,
      "no rule: nothing required (a verification pass, a test)"
    )
    val missing = checkRules(a, last = ReviewArticle.CheckRulesLast.More, spotEv, spots = Some(rule(Nil)))
    assert(missing.left.exists(_.head.sentence.startsWith("spot-checks: 0 of the 2")), missing)
    val done = checkRules(
      a,
      last = ReviewArticle.CheckRulesLast.More,
      spotEv,
      spots = Some(rule(List(read("knapp2016_tin"), read("tin2024_iso"))))
    )
    assertEquals(done.map(_._1.spotChecks.map(_.citekey)), Right(List("knapp2016_tin", "tin2024_iso")))
    // the last attempt keeps the answer, the shortfall a warning
    val kept = checkRules(a, last = ReviewArticle.CheckRulesLast.Last, spotEv, spots = Some(rule(Nil)))
    assert(kept.exists(_._2.exists(_.startsWith("rules: unverified — spot-checks: 0 of the 2"))), kept)

  test("a Report checkpointed before spot-checks existed still decodes"):
    val old =
      """{"reviewer":"rules","verdict":"PASS","findings":[],"explainBack":null,"emptySlots":[],"proposals":[],"checks":[]}"""
    assertEquals(RawJson.decode[Report](old).map(_.spotChecks), Right(Nil))

  // ── a finding's problem is stored as a sentence (review_ledgers.ledger, agent_steps.output) ──────────────────

  private val manyKeys = (1 to 40).map(i => s"[[k${i}2000_x]]").mkString(", ")

  /** The sentences the code of every version wrote (the mechanical lint, the coverage drift, the cold reader's count,
    * the model's free text), as stored: the first come from `mechanical` itself.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val storedSentences: List[Finding.Problem] =
    mechanical(
      Slug("a"),
      ReviewArticle.Snapshot.Kind("concept"),
      Article.Body("Tin moved.\n\nSee [[nobody2020_x]].\n\n## Sources cited\n\n- a\n"),
      isTarget
    ).map(_.problem) ++ List(
      Finding.Problem("a paragraph with no [[citekey]]"),
      Finding.Problem("a hand-written Sources cited section (the page generates it)"),
      Finding.Problem("no 'disagreements' section (writing guide rule 4)"),
      Finding.Problem("no 'unresolved' or 'open questions' section (writing guide rule 4)"),
      Finding.Problem("1 source the filter returns is neither cited nor given a reason: [[knapp2016_tin]]"),
      Finding.Problem(
        "3 sources the filter returns are neither cited nor given a reason: [[knapp2016_tin]], [[sea2000_x]], [[a2001_b]]"
      ),
      Finding.Problem(s"45 sources the filter returns are neither cited nor given a reason: $manyKeys (+5 more)"),
      Finding.Problem("a reason is stored for 2 sources the article cites: [[knapp2016_tin]], [[sea2000_x]]"),
      Finding.Problem("27 terms are undefined (more than 8)"),
      Finding.Problem("12 terms are undefined (more than 8)"),
      Finding.Problem("9 terms undefined (count only; the individual findings carry the severity)"),
      Finding.Problem("banned filler — it is important to note"),
      Finding.Problem("escaped link brackets render as text and link nothing"),
      Finding.Problem("The critique is not cited."),
      Finding.Problem(""),
      Finding.Problem("  a paragraph with no [[citekey]]"),
      Finding.Problem("007 terms undefined (count only)"),
      Finding.Problem("2 sources the filter returns are neither cited nor given a reason: [[a2000_x]], not a key"),
      Finding.Problem("no 'x' section (writing guide rule 4) and more")
    )

  test("a stored problem sentence reads as a cause and writes back as the same sentence"):
    storedSentences.foreach(p => assertEquals(Finding.Problem.of(p.cause), p, p))

  test("the sentences the pipeline acts on read as their causes; any other sentence is Other"):
    import Finding.Problem.Cause
    assertEquals(Finding.Problem("a paragraph with no [[citekey]]").cause, Cause.UncitedParagraph)
    assertEquals(
      Finding.Problem("a hand-written Sources cited section (the page generates it)").cause,
      Cause.HandWrittenSourcesCited
    )
    assertEquals(
      Finding.Problem("no 'unresolved' or 'open questions' section (writing guide rule 4)").cause,
      Cause.MissingSection(List(Finding.Problem.Section("unresolved"), Finding.Problem.Section("open questions")))
    )
    assertEquals(
      Finding.Problem("1 source the filter returns is neither cited nor given a reason: [[knapp2016_tin]]").cause,
      Cause.UnplacedSources(Finding.Problem.Count(1), List(ApiCitekey("knapp2016_tin")))
    )
    Finding
      .Problem(s"45 sources the filter returns are neither cited nor given a reason: $manyKeys (+5 more)")
      .cause match
      case Cause.UnplacedSources(n, listed) => assertEquals((n.toInt, listed.size), (45, 40))
      case _                                => fail("not read as UnplacedSources")
    assert(Finding.Problem("27 terms are undefined (more than 8)").isTermCount)
    assert(
      Finding.Problem("  9 terms undefined (count only; the individual findings carry the severity)  ").isTermCount
    )
    assert(!Finding.Problem("27 terms are fine").isTermCount)
    List(
      Finding.Problem("The critique is not cited."),
      Finding.Problem(""),
      Finding.Problem("007 terms undefined (count only)"),
      Finding.Problem("no 'x' section (writing guide rule 4) and more")
    ).foreach(p => assertEquals(p.cause, Cause.Other(p), p))

  test("a ledger finding keeps its stored problem sentence through the JSON codec"):
    storedSentences.foreach { p =>
      val json =
        s"""{"id":"M1","reviewer":"mechanical","severity":"major","kind":"citation","where":"body","quote":"","problem":${Json
            .fromString(s"$p")
            .noSpaces},"fix":"f","evidence":[],"escalated":false,"pair":null}"""
      val f = RawJson.decode[Finding](json).fold(e => fail(e), identity)
      assertEquals(f.problem, p)
      assertEquals(
        RawJson.decode[Finding]((f).asJson.noSpaces).map(_.problem),
        Right(p)
      )
    }
