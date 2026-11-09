package com.histmersion
package api
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.config.given
import com.histmersion.domain.given

import com.histmersion.domain.AgentProposals.Proposal
import com.histmersion.api.automation.Agents.ApprovalKind
import com.histmersion.domain.ReviewArticle.*
import com.histmersion.domain.ReviewLedger.*
import com.histmersion.api.research.{CritiquePair, CritiqueReason, CritiqueRef, CritiqueSide, CritiqueState}
import com.histmersion.api.library.{Citekey, Edge}
import com.histmersion.domain.ReviewArticle
import com.histmersion.domain.research.Slug
import com.histmersion.domain.ReviewLedger
import com.histmersion.domain.automation.Run

/** The critique checklist in the review (docs/research-review-convergence.md §6.8), pure: what a missing-critique MAJOR
  * is held to in each kind of pass, the skeptic's rulings on the open pairs, the fixer's set-asides and how the ledger
  * records them.
  */
class ReviewCritiqueSuite extends munit.FunSuite:

  private val minRatio = CritiqueRule.MinRatio(0.9)

  private def side(ck: Citekey, cited: CritiqueSide.Cited) =
    CritiqueSide(ck, cited = cited, mentions = CritiqueSide.Mentions(if cited.value then 1 else 0))
  private def pair(
      src: Citekey,
      dst: Citekey,
      state: CritiqueState = CritiqueState.Open,
      reason: Option[Problem] = None
  ) =
    CritiquePair(
      side(src, cited = CritiqueSide.Cited(false)),
      List(CritiquePair.Kinds("refutes")),
      side(dst, cited = CritiqueSide.Cited(true)),
      state,
      reason
    )

  private val open1 = pair(Citekey("smith1987_dying"), Citekey("frazer1922_golden"))
  private val open2 = pair(Citekey("brown1999_rites"), Citekey("frazer1922_golden"))
  private val done = pair(Citekey("lee1990_ritual"), Citekey("frazer1922_golden"), CritiqueState.Addressed)
  private val aside =
    pair(
      Citekey("jones2001_myth"),
      Citekey("frazer1922_golden"),
      CritiqueState.Reasoned,
      Some(Problem.Unclassified(Legacy.Text("cited for the calendar only")))
    )
  private val pairs = List(open1, open2, done, aside)

  /** A missing-critique finding quoting the article's sentence `the dying god is a universal type (<id>)`. */
  private def mc(id: Finding.Id, pair: Option[CritiqueRef.Wire], sev: Severity = Severity.Major) =
    Finding(
      id,
      Reviewer.Skeptic,
      sev,
      ReviewArticle.Finding.Kind("missing-critique"),
      ReviewArticle.Finding.Where("Intro"),
      Finding.Quote(s"the dying god is a universal type ($id)"),
      Finding.Problem(s"omits a critique $id"),
      ReviewArticle.Finding.Fix("cite it"),
      pair = pair.flatMap(CritiqueRef.parse(_).toOption)
    )
  private def edgeProposal(src: com.histmersion.domain.library.Citekey, dst: com.histmersion.domain.library.Citekey) =
    Proposal(
      ApprovalKind.Edge,
      src,
      edgeKind = Some(Edge.Kind.Refutes),
      target = Some(dst),
      quote = Some(Proposal.Quote("q"))
    )

  /** The article: S1–S3 state the claim unqualified; S8 hedged; S9 not in it at all. */
  private val body = ReviewArticle.Fix.Body(
    """Frazer shows that the dying god is a universal type (S1), and that the dying god is a universal type (S2) [[frazer1922_golden]].
      |
      |The bough proves that the dying god is a universal type (S3) [[frazer1922_golden]].
      |
      |Frazer argued that the dying god may be a universal type, though the dying god is a universal type (S8) only perhaps [[frazer1922_golden]].
      |""".stripMargin
  )

  private val pass1 =
    CritiqueRule(pairs, List(open1, open2), full = ReviewArticle.CritiqueRule.Full(true), body, minRatio)
  private val confirm = CritiqueRule(pairs, Nil, full = ReviewArticle.CritiqueRule.Full(true), body, minRatio)
  private val recheck = CritiqueRule(pairs, Nil, full = ReviewArticle.CritiqueRule.Full(false), body, minRatio)

  test("a missing-critique MAJOR naming an open pair stands, in the checklist's own direction"):
    val (fs, ps, ws) = checkCritiques(
      Reviewer.Skeptic,
      List(mc(Finding.Id("S1"), Some(CritiqueRef.Wire("frazer1922_golden>smith1987_dying")))),
      pass1,
      Nil,
      last = ReviewArticle.CheckCritiquesLast(false)
    )
    assertEquals(
      (fs.map(f => (f.severity, f.pair)), ps, ws),
      (List((Severity.Major, Some(CritiqueRef(Citekey("smith1987_dying"), Citekey("frazer1922_golden"))))), Nil, Nil)
    )

  test("one naming an addressed or reasoned pair is recorded minor, with the note, in every pass"):
    List(pass1, confirm, recheck).foreach { rule =>
      val (fs, ps, ws) = checkCritiques(
        Reviewer.Skeptic,
        List(
          mc(Finding.Id("S1"), Some(CritiqueRef.Wire("lee1990_ritual>frazer1922_golden"))),
          mc(Finding.Id("S2"), Some(CritiqueRef.Wire("jones2001_myth>frazer1922_golden")))
        ),
        rule,
        Nil,
        last = ReviewArticle.CheckCritiquesLast(false)
      )
      assertEquals(fs.map(_.severity), List(Severity.Minor, Severity.Minor))
      assert(
        fs.head.problem.value.contains(
          "(recorded minor: lee1990_ritual>frazer1922_golden is addressed — both sources are cited in one paragraph)"
        ),
        fs.head.problem
      )
      assert(
        fs(1).problem.value.contains("set aside with a stored reason: \"cited for the calendar only\""),
        fs(1).problem
      )
      assertEquals((ps, ws.size), (Nil, 2))
    }

  test(
    "calibration (§6.9): no recorded edge is minor in every pass, a proposal or not; an EMPTY pair goes back on a full panel"
  ):
    val f = mc(Finding.Id("S1"), Some(CritiqueRef.Wire("new2020_critic>frazer1922_golden")))
    val proposed = List(
      edgeProposal(
        com.histmersion.domain.library.Citekey("frazer1922_golden"),
        com.histmersion.domain.library.Citekey("new2020_critic")
      )
    ) // either direction
    List(pass1, confirm, recheck).foreach { rule =>
      val (fs, ps, ws) =
        checkCritiques(Reviewer.Skeptic, List(f), rule, proposed, last = ReviewArticle.CheckCritiquesLast(false))
      assertEquals((fs.map(_.severity), ps), (List(Severity.Minor), Nil))
      assert(
        fs.head.problem.value.contains(
          "no edge is recorded between [[new2020_critic]] and [[frazer1922_golden]] — its proposed edge waits for approval"
        ),
        fs.head.problem
      )
      assert(ws.exists(_.contains("recorded minor")), ws)
    }
    assert(
      !checkCritiques(
        Reviewer.Skeptic,
        List(f),
        pass1,
        Nil,
        last = ReviewArticle.CheckCritiquesLast(false)
      )._1.head.problem.value.contains("proposed edge")
    )
    // an empty pair: back on a full panel (it may name a recorded pair); minor on the last attempt, and in a re-check
    val (_, ps, _) = checkCritiques(
      Reviewer.Skeptic,
      List(mc(Finding.Id("S1"), None)),
      pass1,
      Nil,
      last = ReviewArticle.CheckCritiquesLast(false)
    )
    assert(
      ps.size == 1 && ps.head.contains(
        "S1 (missing-critique, major) names no pair of the critique checklist (its `pair` is empty)"
      ),
      ps
    )
    assertEquals(
      checkCritiques(
        Reviewer.Skeptic,
        List(mc(Finding.Id("S1"), None)),
        pass1,
        Nil,
        last = ReviewArticle.CheckCritiquesLast(true)
      )._1.map(_.severity),
      List(Severity.Minor)
    )
    assertEquals(
      checkCritiques(
        Reviewer.Skeptic,
        List(mc(Finding.Id("S1"), None)),
        recheck,
        Nil,
        last = ReviewArticle.CheckCritiquesLast(false)
      )._1.map(_.severity),
      List(Severity.Minor)
    )
    // minors and other kinds pass untouched
    val other = mc(Finding.Id("S2"), None).modify(_.kind).setTo(Finding.Kind("date"))
    assertEquals(
      checkCritiques(
        Reviewer.Skeptic,
        List(other, mc(Finding.Id("S3"), None, Severity.Minor)),
        pass1,
        Nil,
        last = ReviewArticle.CheckCritiquesLast(false)
      )._1,
      List(other, mc(Finding.Id("S3"), None, Severity.Minor))
    )

  test("calibration (§6.9): MAJOR only when the article cites the criticised work and states the claim unqualified"):
    def one(f: Finding, rule: CritiqueRule = pass1, last: CheckCritiquesLast = CheckCritiquesLast(false)) =
      checkCritiques(Reviewer.Skeptic, List(f), rule, Nil, last)
    // stands: open pair, frazer cited, the quoted sentence carries no hedge
    assertEquals(
      one(mc(Finding.Id("S3"), Some(CritiqueRef.Wire("smith1987_dying>frazer1922_golden"))))._1.map(_.severity),
      List(Severity.Major)
    )
    // the sentence is hedged ("may", "perhaps"): minor, with the sentence
    val (h, _, hw) = one(mc(Finding.Id("S8"), Some(CritiqueRef.Wire("smith1987_dying>frazer1922_golden"))))
    assertEquals(h.map(_.severity), List(Severity.Minor))
    assert(
      h.head.problem.value
        .contains("the article already qualifies the claim: \"Frazer argued that the dying god may be"),
      h.head.problem
    )
    assert(hw.exists(_.contains("S8 (missing-critique, major)")), hw)
    // the quote is not in the article: back on a full panel's skeptic, minor on the last attempt and in a re-check
    val (_, ps, _) = one(mc(Finding.Id("S9"), Some(CritiqueRef.Wire("smith1987_dying>frazer1922_golden"))))
    assert(ps.exists(_.contains("S9 (missing-critique, major): its quote is not in the article")), ps)
    assertEquals(
      one(
        mc(Finding.Id("S9"), Some(CritiqueRef.Wire("smith1987_dying>frazer1922_golden"))),
        last = CheckCritiquesLast(true)
      )._1.map(_.severity),
      List(Severity.Minor)
    )
    assertEquals(
      one(mc(Finding.Id("S9"), Some(CritiqueRef.Wire("smith1987_dying>frazer1922_golden"))), recheck)._1
        .map(_.severity),
      List(Severity.Minor)
    )
    // the criticised work is not cited: the article states none of its claims
    val uncited = CritiquePair(
      side(Citekey("frazer1922_golden"), cited = CritiqueSide.Cited(true)),
      List(CritiquePair.Kinds("refutes")),
      side(Citekey("old1900_myth"), cited = CritiqueSide.Cited(false)),
      CritiqueState.Open
    )
    val (u, _, _) = one(
      mc(Finding.Id("S1"), Some(CritiqueRef.Wire("frazer1922_golden>old1900_myth"))),
      pass1.modify(_.pairs).setTo(uncited :: pairs)
    )
    assertEquals(u.map(_.severity), List(Severity.Minor))
    assert(
      u.head.problem.value.contains("the article does not cite [[old1900_myth]], so it states none of its claims"),
      u.head.problem
    )
    // superseded_by is stored on the OLDER work: there the source is the criticised side
    val older = CritiquePair(
      side(Citekey("frazer1922_golden"), cited = CritiqueSide.Cited(true)),
      List(CritiquePair.Kinds("superseded_by")),
      side(Citekey("new2010_rites"), cited = CritiqueSide.Cited(false)),
      CritiqueState.Open
    )
    assertEquals(
      (older.criticised.citekey, older.critic.citekey, older.bearing),
      ("frazer1922_golden", "new2010_rites", true)
    )
    assertEquals(
      one(
        mc(Finding.Id("S1"), Some(CritiqueRef.Wire("frazer1922_golden>new2010_rites"))),
        pass1.modify(_.pairs).setTo(older :: pairs)
      )._1.map(_.severity),
      List(Severity.Major)
    )
    // no body to check against (a rule without one): the quote is not held
    assertEquals(
      one(
        mc(Finding.Id("S9"), Some(CritiqueRef.Wire("smith1987_dying>frazer1922_golden"))),
        pass1.modify(_.body).setTo(ReviewArticle.Fix.Body(""))
      )._1.map(_.severity),
      List(Severity.Major)
    )

  test("claimSentence and qualified: the sentence that holds a quote; hedges and contest words, outside links"):
    assertEquals(
      claimSentence(body, ReviewArticle.Evidence.Quote("the dying god is a universal type (S3)"), minRatio),
      Some("The bough proves that the dying god is a universal type (S3) [[frazer1922_golden]].")
    )
    assertEquals(claimSentence(body, ReviewArticle.Evidence.Quote("nothing like this"), minRatio), None)
    // an abbreviation or an initial ends no sentence; a quote across two sentences gets both, not the paragraph
    val p = ReviewArticle.Fix.Body(
      "Chang et al. (2015, contested) date the split to 6000 BP [[chang2015]]. M. L. West disagrees. It may be older. The end."
    )
    assertEquals(
      sentences(p),
      List(
        "Chang et al. (2015, contested) date the split to 6000 BP [[chang2015]].",
        "M. L. West disagrees.",
        "It may be older.",
        "The end."
      )
    )
    assertEquals(
      claimSentence(p, ReviewArticle.Evidence.Quote("Chang et al. (2015, contested) date the split"), minRatio),
      Some(sentences(p).head)
    )
    assertEquals(
      claimSentence(p, ReviewArticle.Evidence.Quote("M. L. West disagrees. It may be older"), minRatio),
      Some("M. L. West disagrees. It may be older.")
    )
    assert(qualified(ReviewArticle.Sentence("The date is disputed [[a2000_x]].")))
    assert(qualified(ReviewArticle.Sentence("It may date to 1200 BCE.")))
    assert(!qualified(ReviewArticle.Sentence("It dates to 1200 BCE [[mayer2000_may]].")), "a link is no hedge")
    assert(!qualified(ReviewArticle.Sentence("Mayhew shows the date.")))

  test(
    "qualifiedClaim: the claim's own clause and a status label on the criticised work — not another source's (live sentences)"
  ):
    // gods_and_migrations S24: Gray and Atkinson are stated plain; the "contested" belongs to Bouckaert, in the next clause
    val gray = ReviewArticle.Sentence(
      "Earlier phylogenetic studies favoured an Anatolian farming origin: Gray and Atkinson (2003) dated the first split to " +
        "7,800–9,800 years before present [[gray2003_language]], and Bouckaert et al. (2012, contested; updated by Neureiter et al. 2022) " +
        "found decisive support for an Anatolian origin [[bouckaert2012]]."
    )
    assert(
      !qualifiedClaim(
        gray,
        ReviewArticle.QualifiedClaimQuote(
          "Gray and Atkinson (2003) dated the first split to 7,800–9,800 years before present"
        ),
        ReviewArticle.QualifiedClaimCriticised("gray2003_language")
      )
    )
    assert(
      qualifiedClaim(
        gray,
        ReviewArticle.QualifiedClaimQuote(
          "Bouckaert et al. (2012, contested; updated by Neureiter et al. 2022) found decisive support"
        ),
        ReviewArticle.QualifiedClaimCriticised("bouckaert2012")
      )
    )
    // levant_near_east S36: the sentence labels Smith (1998) itself as contested
    val smith = ReviewArticle.Sentence(
      "Comparing the myth with Ugaritic royal funeral texts, Smith (1998) suggests that Baal's death represents a king's death " +
        "and his return the living successor; he also proposes that divine death expresses the dynasty's fragility " +
        "([[smith1998_death]], contested — challenged by [[wyatt2019_problem]])."
    )
    assert(
      qualifiedClaim(
        smith,
        ReviewArticle.QualifiedClaimQuote("Smith (1998) suggests that Baal's death represents a king's death"),
        ReviewArticle.QualifiedClaimCriticised("smith1998_death")
      )
    )
    // mycenaean S22: the status label follows a short-form link
    val kan = ReviewArticle.Sentence(
      "Kaniewski and colleagues (2013) interpret pollen as evidence of prolonged drought [[kaniewski2013]] (contested — " +
        "challenged by [[middleton2020_collapse]], 2020)."
    )
    assert(
      qualifiedClaim(
        kan,
        ReviewArticle.QualifiedClaimQuote("interpret pollen as evidence of prolonged drought"),
        ReviewArticle.QualifiedClaimCriticised("kaniewski2013_environmental")
      )
    )
    // gods_and_migrations S37: Chadwick's interpretation is stated plain — "doubtful" is about another reading, after ", and"
    val chad = ReviewArticle.Sentence(
      "Chadwick (1976) instead interprets the qualified Potnia titles as forms of a dominant Earth Mother, like “Our Lady” of " +
        "different places, and regards the reading Athana Potnia as doubtful [[chadwick1976_mycenaean]]."
    )
    assert(
      !qualifiedClaim(
        chad,
        ReviewArticle.QualifiedClaimQuote(
          "Chadwick (1976) instead interprets the qualified Potnia titles as forms of a dominant Earth Mother"
        ),
        ReviewArticle.QualifiedClaimCriticised("chadwick1976_mycenaean")
      )
    )

  test("one finding per pair: the skeptic's kept, a duplicate in either direction and another reviewer's echo dropped"):
    val s1 = mc(Finding.Id("S1"), Some(CritiqueRef.Wire("smith1987_dying>frazer1922_golden")))
    val s2 = mc(
      Finding.Id("S2"),
      Some(CritiqueRef.Wire("frazer1922_golden>smith1987_dying"))
    ) // the reciprocal of the same two sources
    val r1 = mc(Finding.Id("R1"), Some(CritiqueRef.Wire("smith1987_dying>frazer1922_golden")))
      .modify(_.reviewer)
      .setTo(Reviewer.Rules)
    val c1 = mc(Finding.Id("C1"), Some(CritiqueRef.Wire("brown1999_rites>frazer1922_golden")))
      .modify(_.reviewer)
      .setTo(Reviewer.ColdReader)
    val other = mc(Finding.Id("S3"), None).modify(_.kind).setTo(Finding.Kind("date"))
    val (kept, ws) =
      onePerPair(
        List(r1, s1, s2, other, c1),
        heldBySkeptic = List(CritiqueRef(Citekey("frazer1922_golden"), Citekey("brown1999_rites")))
      )
    assertEquals(kept.map(_.id), List("S1", "S3"))
    assertEquals(ws.size, 3)
    assert(ws.exists(_.startsWith("cold-reader: C1 dropped")), ws)

  test("rulings: every open pair shown is named by a MAJOR or ruled not material; a ruling becomes a reason"):
    val fs = List(mc(Finding.Id("S1"), Some(CritiqueRef.Wire("smith1987_dying>frazer1922_golden"))))
    val ok = List(
      NotMaterialAnswer(
        ReviewArticle.NotMaterialAnswer.Pair("brown1999_rites > frazer1922_golden"),
        Problem.Unclassified(Legacy.Text("  It is cited for the\nseasons only. "))
      )
    )
    val (rs, ps, ws) = checkNotMaterial(ok, fs, pass1, last = ReviewArticle.CheckNotMaterialLast(false))
    assertEquals(
      (rs, ps, ws),
      (
        List(
          CritiqueReason(
            Citekey("brown1999_rites"),
            Citekey("frazer1922_golden"),
            Problem.Unclassified(Legacy.Text("It is cited for the seasons only."))
          )
        ),
        Nil,
        Nil
      )
    )
    // one pair left without a ruling: sent back, a warning on the last attempt
    val (_, ps2, _) = checkNotMaterial(Nil, fs, pass1, last = ReviewArticle.CheckNotMaterialLast(false))
    assert(
      ps2.size == 1 && ps2.head.startsWith(
        "open pairs of the critique checklist without a ruling: brown1999_rites>frazer1922_golden — "
      ),
      ps2
    )
    val (_, ps3, ws3) = checkNotMaterial(Nil, fs, pass1, last = ReviewArticle.CheckNotMaterialLast(true))
    assertEquals(ps3, Nil); assert(ws3.exists(_.contains("(left open)")), ws3)
    // dropped with a warning: a pair already addressed, one the MAJOR names too, one not on the checklist, no reason
    val bad = List(
      NotMaterialAnswer(
        ReviewArticle.NotMaterialAnswer.Pair("lee1990_ritual>frazer1922_golden"),
        Problem.Unclassified(Legacy.Text("x"))
      ),
      NotMaterialAnswer(
        ReviewArticle.NotMaterialAnswer.Pair("smith1987_dying>frazer1922_golden"),
        Problem.Unclassified(Legacy.Text("x"))
      ),
      NotMaterialAnswer(
        ReviewArticle.NotMaterialAnswer.Pair("zz2000_a>zz2001_b"),
        Problem.Unclassified(Legacy.Text("x"))
      ),
      NotMaterialAnswer(
        ReviewArticle.NotMaterialAnswer.Pair("brown1999_rites>frazer1922_golden"),
        Problem.Unclassified(Legacy.Text(" "))
      )
    )
    val (rs4, _, ws4) = checkNotMaterial(bad, fs, pass1, last = ReviewArticle.CheckNotMaterialLast(true))
    assertEquals(rs4, Nil)
    assertEquals(ws4.count(_.startsWith("skeptic: dropped — not material")), 4, ws4)

  test("the skeptic's answer as a whole: findings held to the checklist, rulings kept on the report"):
    val a = SkepticAnswer(
      ReviewArticle.SkepticAnswer.Verdict("MAJOR"),
      List(
        FindingAnswer(
          ReviewArticle.FindingAnswer.Severity("major"),
          ReviewArticle.FindingAnswer.Kind("missing-critique"),
          ReviewArticle.FindingAnswer.Where("Intro"),
          ReviewArticle.FindingAnswer.Quote("the dying god is a universal type (S3)"),
          ReviewArticle.FindingAnswer.Problem("omits Smith"),
          ReviewArticle.FindingAnswer.Fix("cite Smith"),
          pair = Some(CritiqueRef.Wire("smith1987_dying>frazer1922_golden"))
        )
      ),
      Nil,
      ReviewArticle.SkepticAnswer.ClaimsChecked(6),
      Nil,
      List(
        NotMaterialAnswer(
          ReviewArticle.NotMaterialAnswer.Pair("brown1999_rites>frazer1922_golden"),
          Problem.Unclassified(Legacy.Text("Off the article's question."))
        )
      )
    )
    val (rep, _) = checkSkeptic(
      a,
      last = ReviewArticle.CheckSkepticLast(false),
      _ => true,
      _ => None,
      minRatio,
      None,
      Some(pass1)
    ).toOption.get
    assertEquals(rep.findings.map(_.pair.map(_.wire)), List(Some("smith1987_dying>frazer1922_golden")))
    assertEquals(rep.notMaterial.map(_.ref.wire), List("brown1999_rites>frazer1922_golden"))
    assertEquals(rep.verdict, Verdict.Major)
    // a MAJOR verdict whose only MAJOR code records minor (an addressed pair) is corrected, not sent back
    val onDone = a
      .modify(_.findings)
      .setTo(a.findings.map(_.modify(_.pair).setTo(Some(CritiqueRef.Wire("lee1990_ritual>frazer1922_golden")))))
      .modify(_.notMaterial)
      .setTo(
        a.notMaterial :+ NotMaterialAnswer(
          ReviewArticle.NotMaterialAnswer.Pair("smith1987_dying>frazer1922_golden"),
          Problem.Unclassified(Legacy.Text("Off the question."))
        )
      )
    assertEquals(
      checkSkeptic(
        onDone,
        last = ReviewArticle.CheckSkepticLast(false),
        _ => true,
        _ => None,
        minRatio,
        None,
        Some(pass1)
      ).map(_._1.verdict),
      Right(Verdict.Minor)
    )
    // without the ruling it goes back
    assert(
      checkSkeptic(
        a.modify(_.notMaterial).setTo(Nil),
        last = ReviewArticle.CheckSkepticLast(false),
        _ => true,
        _ => None,
        minRatio,
        None,
        Some(pass1)
      ).isLeft
    )
    // a re-check: the same MAJOR on an addressed pair is recorded minor (then scoped as before)
    val rc = RecheckAnswer(
      Nil,
      List(
        FindingAnswer(
          ReviewArticle.FindingAnswer.Severity("major"),
          ReviewArticle.FindingAnswer.Kind("missing-critique"),
          ReviewArticle.FindingAnswer.Where("Intro"),
          ReviewArticle.FindingAnswer.Quote(""),
          ReviewArticle.FindingAnswer.Problem("omits Lee"),
          ReviewArticle.FindingAnswer.Fix("cite Lee"),
          pair = Some(CritiqueRef.Wire("lee1990_ritual>frazer1922_golden"))
        )
      )
    )
    val (r, ws) =
      checkRecheck(
        Reviewer.Skeptic,
        rc,
        Prior(Nil, ReviewArticle.Prior.Body("body"), minRatio),
        ReviewArticle.CheckRecheckEdited(""),
        NoEvidence,
        last = ReviewArticle.CheckRecheckLast(false),
        pairs
      ).toOption.get
    assertEquals(r.findings.map(_.severity), List(Severity.Minor))
    assert(ws.exists(_.contains("is addressed")), ws)

  private val merged = Merged(
    ReviewArticle.Merged.Round(1),
    Verdict.Major,
    List(
      mc(Finding.Id("S1"), Some(CritiqueRef.Wire("smith1987_dying>frazer1922_golden"))),
      mc(Finding.Id("S2"), None),
      Finding(
        ReviewArticle.Finding.Id("R1"),
        Reviewer.Rules,
        Severity.Major,
        ReviewArticle.Finding.Kind("synth"),
        ReviewArticle.Finding.Where("Intro"),
        ReviewArticle.Finding.Quote(""),
        ReviewArticle.Finding.Problem("a bridge claim"),
        ReviewArticle.Finding.Fix("")
      )
    ),
    None,
    Nil,
    Nil
  )
  private val byId = merged.findings.map(f => f.id -> f).toMap

  test("set aside: a missing-critique finding, a pair of the checklist (its own or given), a reason"):
    val (ok, pairOnly, ps) = checkSetAside(
      List(
        SetAsideAnswer(
          ReviewArticle.SetAsideAnswer.Id("S1"),
          None,
          Problem.Unclassified(Legacy.Text("Cited for the date only."))
        ),
        SetAsideAnswer(
          ReviewArticle.SetAsideAnswer.Id("S2"),
          Some(CritiqueRef.Wire("brown1999_rites>frazer1922_golden")),
          Problem.Unclassified(Legacy.Text("Off the point."))
        ),
        SetAsideAnswer(
          ReviewArticle.SetAsideAnswer.Id(""),
          Some(CritiqueRef.Wire("frazer1922_golden>brown1999_rites")),
          Problem.Unclassified(Legacy.Text("No finding names it."))
        )
      ),
      byId,
      pairs
    )
    assertEquals(
      ok,
      List(
        SetAside(
          ReviewArticle.SetAside.Id("S1"),
          CritiqueRef(Citekey("smith1987_dying"), Citekey("frazer1922_golden")),
          Problem.Unclassified(Legacy.Text("Cited for the date only."))
        ),
        SetAside(
          ReviewArticle.SetAside.Id("S2"),
          CritiqueRef(Citekey("brown1999_rites"), Citekey("frazer1922_golden")),
          Problem.Unclassified(Legacy.Text("Off the point."))
        )
      )
    )
    assertEquals(ps, Nil)
    // a pair alone (no id): the checklist's own direction, stored as a reason
    assertEquals(
      pairOnly,
      List(
        CritiqueReason(
          Citekey("brown1999_rites"),
          Citekey("frazer1922_golden"),
          Problem.Unclassified(Legacy.Text("No finding names it."))
        )
      )
    )
    val (none, noPair, bad) = checkSetAside(
      List(
        SetAsideAnswer(ReviewArticle.SetAsideAnswer.Id("R1"), None, Problem.Unclassified(Legacy.Text("x"))),
        SetAsideAnswer(ReviewArticle.SetAsideAnswer.Id("S2"), None, Problem.Unclassified(Legacy.Text("x"))),
        SetAsideAnswer(
          ReviewArticle.SetAsideAnswer.Id("S1"),
          Some(CritiqueRef.Wire("zz2000_a>zz2001_b")),
          Problem.Unclassified(Legacy.Text("x"))
        ),
        SetAsideAnswer(ReviewArticle.SetAsideAnswer.Id("S1"), None, Problem.Unclassified(Legacy.Text(""))),
        SetAsideAnswer(ReviewArticle.SetAsideAnswer.Id("S9"), None, Problem.Unclassified(Legacy.Text("x"))),
        SetAsideAnswer(ReviewArticle.SetAsideAnswer.Id(""), None, Problem.Unclassified(Legacy.Text("x"))),
        SetAsideAnswer(
          ReviewArticle.SetAsideAnswer.Id(""),
          Some(CritiqueRef.Wire("zz2000_a>zz2001_b")),
          Problem.Unclassified(Legacy.Text("x"))
        )
      ),
      byId,
      pairs
    )
    assertEquals((none, noPair), (Nil, Nil))
    assertEquals(
      bad.map(_.takeWhile(_ != ':')),
      List(
        "set aside R1",
        "set aside S2",
        "set aside S1",
        "set aside S1",
        "set aside 'S9'",
        "a set-aside with neither an id nor a pair — give the finding's id, or the pair (src>dst)",
        "set aside zz2000_a>zz2001_b"
      )
    )

  test(
    "the fixer's answer: a set-aside decides a MAJOR, with no edit; the ledger closes it as set aside, with the reason"
  ):
    val body = ReviewArticle.Fix.Body(
      "Tin [[knapp2016_tin]].\n\n## Disagreements\n\nArgued [[knapp2016_tin]].\n\n## Open questions\n\nOpen [[knapp2016_tin]].\n\n## Not used\n\n- none\n"
    )
    val corpus = BodyCorpus(_.value == "knapp2016_tin", _.value == "knapp2016_tin", _ => Nil)
    val m = merged.modify(_.findings).setTo(merged.findings.take(1))
    val a = FixerAnswer(
      Nil,
      Nil,
      Nil,
      Nil,
      ReviewArticle.FixerAnswer.Notes(""),
      setAside = List(
        SetAsideAnswer(
          ReviewArticle.SetAsideAnswer.Id("S1"),
          None,
          Problem.Unclassified(Legacy.Text("Cited for the date only."))
        )
      )
    )
    val (fix, ws) =
      checkFix(
        a,
        m,
        Slug("tin"),
        body,
        ReviewArticle.Snapshot.Kind("concept"),
        corpus,
        _ => true,
        _ => None,
        minRatio,
        last = ReviewArticle.LastRound(false),
        None,
        pairs
      ).toOption.get
    assertEquals((fix.setAside.map(_.id), ws), (List("S1"), Nil))
    // without it the MAJOR is undecided
    assert(
      checkFix(
        a.modify(_.setAside).setTo(Nil),
        m,
        Slug("tin"),
        body,
        ReviewArticle.Snapshot.Kind("concept"),
        corpus,
        _ => true,
        _ => None,
        minRatio,
        last = ReviewArticle.LastRound(false),
        None,
        pairs
      ).isLeft
    )
    val l = absorb(
      Ledger(ReviewLedger.Ledger.Slug("tin")),
      m.findings,
      Run.Id("5b746968-0d99-0015-9c65-3ddb9ff22ae7"),
      ReviewLedger.Closure.Round(1)
    )._1
    val closed =
      applyFix(l, fix, ReviewLedger.ApplyFixRun("5b746968-0d99-0015-9c65-3ddb9ff22ae7"), ReviewLedger.ApplyFixRound(1))
    val e = closed.entry(ReviewArticle.Finding.Id("S1")).get
    assertEquals(
      (e.status, e.closed.map(c => (c.by, c.note, c.quote))),
      (Status.SetAside, Some(("fixer", "Cited for the date only.", "smith1987_dying>frazer1922_golden")))
    )
    assertEquals(closed.openMajors, Nil)
    assert(
      closedLine(e).contains(
        "set aside as not material (smith1987_dying>frazer1922_golden) — Cited for the date only."
      ),
      closedLine(e)
    )
    // raised again as a MINOR (its pair now reasoned): stays set aside; raised again MAJOR: re-opened, with why
    assertEquals(
      absorb(
        closed,
        List(e.finding.modify(_.severity).setTo(Severity.Minor)),
        Run.Id("13d17bd3-fd7e-e870-9798-400086850fa9"),
        ReviewLedger.Closure.Round(1)
      )._1.entry(ReviewArticle.Finding.Id("S1")).get.status,
      Status.SetAside
    )
    val again = absorb(
      closed,
      List(e.finding),
      Run.Id("13d17bd3-fd7e-e870-9798-400086850fa9"),
      ReviewLedger.Closure.Round(1)
    )._1.entry(ReviewArticle.Finding.Id("S1")).get
    assertEquals((again.status, again.notFixed), (Status.Open, Some("set aside before: Cited for the date only.")))

  test("a ledger written before the checklist still decodes (no pair, no set-aside)"):
    val old =
      """{"slug":"tin","entries":[{"finding":{"id":"S1","reviewer":"skeptic","severity":"major","kind":"missing-critique","where":"w","quote":"q","problem":"p","fix":"f","evidence":[],"escalated":false},"run":"4b43b0ae-e356-24cd-95b9-10189b3dc231","round":1,"status":"open","claimed":false,"notFixed":null,"reopened":0,"closed":null}],"lessons":[],"task":null}"""
    val l = io.circe.parser.decode[Ledger](old).toOption.get
    assertEquals(l.entries.map(_.finding.pair), List(None))
