package com.histmersion.api

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
import com.histmersion.api.library.Edge

/** The critique checklist in the review (docs/research-review-convergence.md §6.8), pure: what a missing-critique MAJOR
  * is held to in each kind of pass, the skeptic's rulings on the open pairs, the fixer's set-asides and how the ledger
  * records them.
  */
class ReviewCritiqueSuite extends munit.FunSuite:

  private def side(ck: String, cited: Boolean) = CritiqueSide(ck, cited = cited, mentions = if cited then 1 else 0)
  private def pair(src: String, dst: String, state: CritiqueState = CritiqueState.Open, reason: Option[String] = None) =
    CritiquePair(side(src, cited = false), List(com.histmersion.api.research.CritiquePair.Kinds("refutes")), side(dst, cited = true), state, reason)

  private val open1 = pair("smith1987_dying", "frazer1922_golden")
  private val open2 = pair("brown1999_rites", "frazer1922_golden")
  private val done = pair("lee1990_ritual", "frazer1922_golden", CritiqueState.Addressed)
  private val aside =
    pair("jones2001_myth", "frazer1922_golden", CritiqueState.Reasoned, Some("cited for the calendar only"))
  private val pairs = List(open1, open2, done, aside)

  /** A missing-critique finding quoting the article's sentence `the dying god is a universal type (<id>)`. */
  private def mc(id: String, pair: Option[String], sev: Severity = Severity.Major) =
    Finding(
      id,
      Reviewer.Skeptic,
      sev,
      com.histmersion.domain.ReviewArticle.Finding.Kind("missing-critique"),
      com.histmersion.domain.ReviewArticle.Finding.Where("Intro"),
      s"the dying god is a universal type ($id)",
      s"omits a critique $id",
      com.histmersion.domain.ReviewArticle.Finding.Fix("cite it"),
      pair = pair.flatMap(CritiqueRef.parse(_).toOption)
    )
  private def edgeProposal(src: String, dst: String) =
    Proposal(ApprovalKind.Edge, src, edgeKind = Some(Edge.Kind.Refutes), target = Some(dst), quote = Some("q"))

  /** The article: S1–S3 state the claim unqualified; S8 hedged; S9 not in it at all. */
  private val body =
    """Frazer shows that the dying god is a universal type (S1), and that the dying god is a universal type (S2) [[frazer1922_golden]].
      |
      |The bough proves that the dying god is a universal type (S3) [[frazer1922_golden]].
      |
      |Frazer argued that the dying god may be a universal type, though the dying god is a universal type (S8) only perhaps [[frazer1922_golden]].
      |""".stripMargin

  private val pass1 = CritiqueRule(pairs, List(open1, open2), full = com.histmersion.domain.ReviewArticle.CritiqueRule.Full(true), body, 0.9)
  private val confirm = CritiqueRule(pairs, Nil, full = com.histmersion.domain.ReviewArticle.CritiqueRule.Full(true), body, 0.9)
  private val recheck = CritiqueRule(pairs, Nil, full = com.histmersion.domain.ReviewArticle.CritiqueRule.Full(false), body, 0.9)

  test("a missing-critique MAJOR naming an open pair stands, in the checklist's own direction"):
    val (fs, ps, ws) = checkCritiques(
      Reviewer.Skeptic,
      List(mc("S1", Some("frazer1922_golden>smith1987_dying"))),
      pass1,
      Nil,
      last = com.histmersion.domain.ReviewArticle.CheckCritiquesLast(false)
    )
    assertEquals(
      (fs.map(f => (f.severity, f.pair)), ps, ws),
      (List((Severity.Major, Some(CritiqueRef(com.histmersion.api.library.Citekey("smith1987_dying"), com.histmersion.api.library.Citekey("frazer1922_golden"))))), Nil, Nil)
    )

  test("one naming an addressed or reasoned pair is recorded minor, with the note, in every pass"):
    List(pass1, confirm, recheck).foreach { rule =>
      val (fs, ps, ws) = checkCritiques(
        Reviewer.Skeptic,
        List(mc("S1", Some("lee1990_ritual>frazer1922_golden")), mc("S2", Some("jones2001_myth>frazer1922_golden"))),
        rule,
        Nil,
        last = com.histmersion.domain.ReviewArticle.CheckCritiquesLast(false)
      )
      assertEquals(fs.map(_.severity), List(Severity.Minor, Severity.Minor))
      assert(
        fs.head.problem.contains(
          "(recorded minor: lee1990_ritual>frazer1922_golden is addressed — both sources are cited in one paragraph)"
        ),
        fs.head.problem
      )
      assert(fs(1).problem.contains("set aside with a stored reason: \"cited for the calendar only\""), fs(1).problem)
      assertEquals((ps, ws.size), (Nil, 2))
    }

  test(
    "calibration (§6.9): no recorded edge is minor in every pass, a proposal or not; an EMPTY pair goes back on a full panel"
  ):
    val f = mc("S1", Some("new2020_critic>frazer1922_golden"))
    val proposed = List(edgeProposal("frazer1922_golden", "new2020_critic")) // either direction
    List(pass1, confirm, recheck).foreach { rule =>
      val (fs, ps, ws) = checkCritiques(Reviewer.Skeptic, List(f), rule, proposed, last = com.histmersion.domain.ReviewArticle.CheckCritiquesLast(false))
      assertEquals((fs.map(_.severity), ps), (List(Severity.Minor), Nil))
      assert(
        fs.head.problem.contains(
          "no edge is recorded between [[new2020_critic]] and [[frazer1922_golden]] — its proposed edge waits for approval"
        ),
        fs.head.problem
      )
      assert(ws.exists(_.contains("recorded minor")), ws)
    }
    assert(
      !checkCritiques(Reviewer.Skeptic, List(f), pass1, Nil, last = com.histmersion.domain.ReviewArticle.CheckCritiquesLast(false))._1.head.problem.contains("proposed edge")
    )
    // an empty pair: back on a full panel (it may name a recorded pair); minor on the last attempt, and in a re-check
    val (_, ps, _) = checkCritiques(Reviewer.Skeptic, List(mc("S1", None)), pass1, Nil, last = com.histmersion.domain.ReviewArticle.CheckCritiquesLast(false))
    assert(
      ps.size == 1 && ps.head.contains(
        "S1 (missing-critique, major) names no pair of the critique checklist (its `pair` is empty)"
      ),
      ps
    )
    assertEquals(
      checkCritiques(Reviewer.Skeptic, List(mc("S1", None)), pass1, Nil, last = com.histmersion.domain.ReviewArticle.CheckCritiquesLast(true))._1.map(_.severity),
      List(Severity.Minor)
    )
    assertEquals(
      checkCritiques(Reviewer.Skeptic, List(mc("S1", None)), recheck, Nil, last = com.histmersion.domain.ReviewArticle.CheckCritiquesLast(false))._1.map(_.severity),
      List(Severity.Minor)
    )
    // minors and other kinds pass untouched
    val other = mc("S2", None).modify(_.kind).setTo("date")
    assertEquals(
      checkCritiques(Reviewer.Skeptic, List(other, mc("S3", None, Severity.Minor)), pass1, Nil, last = com.histmersion.domain.ReviewArticle.CheckCritiquesLast(false))._1,
      List(other, mc("S3", None, Severity.Minor))
    )

  test("calibration (§6.9): MAJOR only when the article cites the criticised work and states the claim unqualified"):
    def one(f: Finding, rule: CritiqueRule = pass1, last: Boolean = false) =
      checkCritiques(Reviewer.Skeptic, List(f), rule, Nil, last)
    // stands: open pair, frazer cited, the quoted sentence carries no hedge
    assertEquals(one(mc("S3", Some("smith1987_dying>frazer1922_golden")))._1.map(_.severity), List(Severity.Major))
    // the sentence is hedged ("may", "perhaps"): minor, with the sentence
    val (h, _, hw) = one(mc("S8", Some("smith1987_dying>frazer1922_golden")))
    assertEquals(h.map(_.severity), List(Severity.Minor))
    assert(
      h.head.problem.contains("the article already qualifies the claim: \"Frazer argued that the dying god may be"),
      h.head.problem
    )
    assert(hw.exists(_.contains("S8 (missing-critique, major)")), hw)
    // the quote is not in the article: back on a full panel's skeptic, minor on the last attempt and in a re-check
    val (_, ps, _) = one(mc("S9", Some("smith1987_dying>frazer1922_golden")))
    assert(ps.exists(_.contains("S9 (missing-critique, major): its quote is not in the article")), ps)
    assertEquals(
      one(mc("S9", Some("smith1987_dying>frazer1922_golden")), last = true)._1.map(_.severity),
      List(Severity.Minor)
    )
    assertEquals(
      one(mc("S9", Some("smith1987_dying>frazer1922_golden")), recheck)._1.map(_.severity),
      List(Severity.Minor)
    )
    // the criticised work is not cited: the article states none of its claims
    val uncited = CritiquePair(
      side("frazer1922_golden", cited = true),
      List(com.histmersion.api.research.CritiquePair.Kinds("refutes")),
      side("old1900_myth", cited = false),
      CritiqueState.Open
    )
    val (u, _, _) = one(mc("S1", Some("frazer1922_golden>old1900_myth")), pass1.modify(_.pairs).setTo(uncited :: pairs))
    assertEquals(u.map(_.severity), List(Severity.Minor))
    assert(
      u.head.problem.contains("the article does not cite [[old1900_myth]], so it states none of its claims"),
      u.head.problem
    )
    // superseded_by is stored on the OLDER work: there the source is the criticised side
    val older = CritiquePair(
      side("frazer1922_golden", cited = true),
      List(com.histmersion.api.research.CritiquePair.Kinds("superseded_by")),
      side("new2010_rites", cited = false),
      CritiqueState.Open
    )
    assertEquals(
      (older.criticised.citekey, older.critic.citekey, older.bearing),
      ("frazer1922_golden", "new2010_rites", true)
    )
    assertEquals(
      one(mc("S1", Some("frazer1922_golden>new2010_rites")), pass1.modify(_.pairs).setTo(older :: pairs))._1.map(_.severity),
      List(Severity.Major)
    )
    // no body to check against (a rule without one): the quote is not held
    assertEquals(
      one(mc("S9", Some("smith1987_dying>frazer1922_golden")), pass1.modify(_.body).setTo(""))._1.map(_.severity),
      List(Severity.Major)
    )

  test("claimSentence and qualified: the sentence that holds a quote; hedges and contest words, outside links"):
    assertEquals(
      claimSentence(body, com.histmersion.domain.ReviewArticle.Evidence.Quote("the dying god is a universal type (S3)"), 0.9),
      Some("The bough proves that the dying god is a universal type (S3) [[frazer1922_golden]].")
    )
    assertEquals(claimSentence(body, com.histmersion.domain.ReviewArticle.Evidence.Quote("nothing like this"), 0.9), None)
    // an abbreviation or an initial ends no sentence; a quote across two sentences gets both, not the paragraph
    val p =
      "Chang et al. (2015, contested) date the split to 6000 BP [[chang2015]]. M. L. West disagrees. It may be older. The end."
    assertEquals(
      sentences(p),
      List(
        "Chang et al. (2015, contested) date the split to 6000 BP [[chang2015]].",
        "M. L. West disagrees.",
        "It may be older.",
        "The end."
      )
    )
    assertEquals(claimSentence(p, com.histmersion.domain.ReviewArticle.Evidence.Quote("Chang et al. (2015, contested) date the split"), 0.9), Some(sentences(p).head))
    assertEquals(
      claimSentence(p, com.histmersion.domain.ReviewArticle.Evidence.Quote("M. L. West disagrees. It may be older"), 0.9),
      Some("M. L. West disagrees. It may be older.")
    )
    assert(qualified(com.histmersion.domain.ReviewArticle.Sentence("The date is disputed [[a2000_x]].")))
    assert(qualified(com.histmersion.domain.ReviewArticle.Sentence("It may date to 1200 BCE.")))
    assert(!qualified(com.histmersion.domain.ReviewArticle.Sentence("It dates to 1200 BCE [[mayer2000_may]].")), "a link is no hedge")
    assert(!qualified(com.histmersion.domain.ReviewArticle.Sentence("Mayhew shows the date.")))

  test(
    "qualifiedClaim: the claim's own clause and a status label on the criticised work — not another source's (live sentences)"
  ):
    // gods_and_migrations S24: Gray and Atkinson are stated plain; the "contested" belongs to Bouckaert, in the next clause
    val gray =
      "Earlier phylogenetic studies favoured an Anatolian farming origin: Gray and Atkinson (2003) dated the first split to " +
        "7,800–9,800 years before present [[gray2003_language]], and Bouckaert et al. (2012, contested; updated by Neureiter et al. 2022) " +
        "found decisive support for an Anatolian origin [[bouckaert2012]]."
    assert(
      !qualifiedClaim(
        gray,
        "Gray and Atkinson (2003) dated the first split to 7,800–9,800 years before present",
        com.histmersion.domain.ReviewArticle.QualifiedClaimCriticised("gray2003_language")
      )
    )
    assert(
      qualifiedClaim(
        gray,
        "Bouckaert et al. (2012, contested; updated by Neureiter et al. 2022) found decisive support",
        com.histmersion.domain.ReviewArticle.QualifiedClaimCriticised("bouckaert2012")
      )
    )
    // levant_near_east S36: the sentence labels Smith (1998) itself as contested
    val smith =
      "Comparing the myth with Ugaritic royal funeral texts, Smith (1998) suggests that Baal's death represents a king's death " +
        "and his return the living successor; he also proposes that divine death expresses the dynasty's fragility " +
        "([[smith1998_death]], contested — challenged by [[wyatt2019_problem]])."
    assert(
      qualifiedClaim(smith, "Smith (1998) suggests that Baal's death represents a king's death", com.histmersion.domain.ReviewArticle.QualifiedClaimCriticised("smith1998_death"))
    )
    // mycenaean S22: the status label follows a short-form link
    val kan =
      "Kaniewski and colleagues (2013) interpret pollen as evidence of prolonged drought [[kaniewski2013]] (contested — " +
        "challenged by [[middleton2020_collapse]], 2020)."
    assert(qualifiedClaim(kan, com.histmersion.domain.ReviewArticle.QualifiedClaimQuote("interpret pollen as evidence of prolonged drought"), com.histmersion.domain.ReviewArticle.QualifiedClaimCriticised("kaniewski2013_environmental")))
    // gods_and_migrations S37: Chadwick's interpretation is stated plain — "doubtful" is about another reading, after ", and"
    val chad =
      "Chadwick (1976) instead interprets the qualified Potnia titles as forms of a dominant Earth Mother, like “Our Lady” of " +
        "different places, and regards the reading Athana Potnia as doubtful [[chadwick1976_mycenaean]]."
    assert(
      !qualifiedClaim(
        chad,
        "Chadwick (1976) instead interprets the qualified Potnia titles as forms of a dominant Earth Mother",
        com.histmersion.domain.ReviewArticle.QualifiedClaimCriticised("chadwick1976_mycenaean")
      )
    )

  test("one finding per pair: the skeptic's kept, a duplicate in either direction and another reviewer's echo dropped"):
    val s1 = mc("S1", Some("smith1987_dying>frazer1922_golden"))
    val s2 = mc("S2", Some("frazer1922_golden>smith1987_dying")) // the reciprocal of the same two sources
    val r1 = mc("R1", Some("smith1987_dying>frazer1922_golden")).modify(_.reviewer).setTo(Reviewer.Rules)
    val c1 = mc("C1", Some("brown1999_rites>frazer1922_golden")).modify(_.reviewer).setTo(Reviewer.ColdReader)
    val other = mc("S3", None).modify(_.kind).setTo("date")
    val (kept, ws) =
      onePerPair(List(r1, s1, s2, other, c1), heldBySkeptic = List(CritiqueRef(com.histmersion.api.library.Citekey("frazer1922_golden"), com.histmersion.api.library.Citekey("brown1999_rites"))))
    assertEquals(kept.map(_.id), List("S1", "S3"))
    assertEquals(ws.size, 3)
    assert(ws.exists(_.startsWith("cold-reader: C1 dropped")), ws)

  test("rulings: every open pair shown is named by a MAJOR or ruled not material; a ruling becomes a reason"):
    val fs = List(mc("S1", Some("smith1987_dying>frazer1922_golden")))
    val ok = List(NotMaterialAnswer(com.histmersion.domain.ReviewArticle.NotMaterialAnswer.Pair("brown1999_rites > frazer1922_golden"), com.histmersion.domain.ReviewArticle.NotMaterialAnswer.Reason("  It is cited for the\nseasons only. ")))
    val (rs, ps, ws) = checkNotMaterial(ok, fs, pass1, last = com.histmersion.domain.ReviewArticle.CheckNotMaterialLast(false))
    assertEquals(
      (rs, ps, ws),
      (List(CritiqueReason(com.histmersion.api.library.Citekey("brown1999_rites"), com.histmersion.api.library.Citekey("frazer1922_golden"), com.histmersion.api.research.CritiqueReason.Reason("It is cited for the seasons only."))), Nil, Nil)
    )
    // one pair left without a ruling: sent back, a warning on the last attempt
    val (_, ps2, _) = checkNotMaterial(Nil, fs, pass1, last = com.histmersion.domain.ReviewArticle.CheckNotMaterialLast(false))
    assert(
      ps2.size == 1 && ps2.head.startsWith(
        "open pairs of the critique checklist without a ruling: brown1999_rites>frazer1922_golden — "
      ),
      ps2
    )
    val (_, ps3, ws3) = checkNotMaterial(Nil, fs, pass1, last = com.histmersion.domain.ReviewArticle.CheckNotMaterialLast(true))
    assertEquals(ps3, Nil); assert(ws3.exists(_.contains("(left open)")), ws3)
    // dropped with a warning: a pair already addressed, one the MAJOR names too, one not on the checklist, no reason
    val bad = List(
      NotMaterialAnswer(com.histmersion.domain.ReviewArticle.NotMaterialAnswer.Pair("lee1990_ritual>frazer1922_golden"), com.histmersion.domain.ReviewArticle.NotMaterialAnswer.Reason("x")),
      NotMaterialAnswer(com.histmersion.domain.ReviewArticle.NotMaterialAnswer.Pair("smith1987_dying>frazer1922_golden"), com.histmersion.domain.ReviewArticle.NotMaterialAnswer.Reason("x")),
      NotMaterialAnswer(com.histmersion.domain.ReviewArticle.NotMaterialAnswer.Pair("zz2000_a>zz2001_b"), com.histmersion.domain.ReviewArticle.NotMaterialAnswer.Reason("x")),
      NotMaterialAnswer(com.histmersion.domain.ReviewArticle.NotMaterialAnswer.Pair("brown1999_rites>frazer1922_golden"), com.histmersion.domain.ReviewArticle.NotMaterialAnswer.Reason(" "))
    )
    val (rs4, _, ws4) = checkNotMaterial(bad, fs, pass1, last = com.histmersion.domain.ReviewArticle.CheckNotMaterialLast(true))
    assertEquals(rs4, Nil)
    assertEquals(ws4.count(_.startsWith("skeptic: dropped — not material")), 4, ws4)

  test("the skeptic's answer as a whole: findings held to the checklist, rulings kept on the report"):
    val a = SkepticAnswer(
      com.histmersion.domain.ReviewArticle.SkepticAnswer.Verdict("MAJOR"),
      List(
        FindingAnswer(
          com.histmersion.domain.ReviewArticle.FindingAnswer.Severity("major"),
          com.histmersion.domain.ReviewArticle.FindingAnswer.Kind("missing-critique"),
          com.histmersion.domain.ReviewArticle.FindingAnswer.Where("Intro"),
          com.histmersion.domain.ReviewArticle.FindingAnswer.Quote("the dying god is a universal type (S3)"),
          com.histmersion.domain.ReviewArticle.FindingAnswer.Problem("omits Smith"),
          com.histmersion.domain.ReviewArticle.FindingAnswer.Fix("cite Smith"),
          pair = Some("smith1987_dying>frazer1922_golden")
        )
      ),
      Nil,
      com.histmersion.domain.ReviewArticle.SkepticAnswer.ClaimsChecked(6),
      Nil,
      List(NotMaterialAnswer(com.histmersion.domain.ReviewArticle.NotMaterialAnswer.Pair("brown1999_rites>frazer1922_golden"), "Off the article's question."))
    )
    val (rep, _) = checkSkeptic(a, last = com.histmersion.domain.ReviewArticle.CheckSkepticLast(false), _ => true, _ => None, 0.9, None, Some(pass1)).toOption.get
    assertEquals(rep.findings.map(_.pair.map(_.wire)), List(Some("smith1987_dying>frazer1922_golden")))
    assertEquals(rep.notMaterial.map(_.ref.wire), List("brown1999_rites>frazer1922_golden"))
    assertEquals(rep.verdict, Verdict.Major)
    // a MAJOR verdict whose only MAJOR code records minor (an addressed pair) is corrected, not sent back
    val onDone = a.modify(
      _.findings).setTo(a.findings.map(_.modify(_.pair).setTo(Some("lee1990_ritual>frazer1922_golden")))).modify(_.notMaterial).setTo(a.notMaterial :+ NotMaterialAnswer("smith1987_dying>frazer1922_golden", "Off the question.")
    )
    assertEquals(
      checkSkeptic(onDone, last = com.histmersion.domain.ReviewArticle.CheckSkepticLast(false), _ => true, _ => None, 0.9, None, Some(pass1)).map(_._1.verdict),
      Right(Verdict.Minor)
    )
    // without the ruling it goes back
    assert(checkSkeptic(a.modify(_.notMaterial).setTo(Nil), last = com.histmersion.domain.ReviewArticle.CheckSkepticLast(false), _ => true, _ => None, 0.9, None, Some(pass1)).isLeft)
    // a re-check: the same MAJOR on an addressed pair is recorded minor (then scoped as before)
    val rc = RecheckAnswer(
      Nil,
      List(
        FindingAnswer(
          com.histmersion.domain.ReviewArticle.FindingAnswer.Severity("major"),
          com.histmersion.domain.ReviewArticle.FindingAnswer.Kind("missing-critique"),
          com.histmersion.domain.ReviewArticle.FindingAnswer.Where("Intro"),
          com.histmersion.domain.ReviewArticle.FindingAnswer.Quote(""),
          com.histmersion.domain.ReviewArticle.FindingAnswer.Problem("omits Lee"),
          com.histmersion.domain.ReviewArticle.FindingAnswer.Fix("cite Lee"),
          pair = Some("lee1990_ritual>frazer1922_golden")
        )
      )
    )
    val (r, ws) =
      checkRecheck(Reviewer.Skeptic, rc, Prior(Nil, com.histmersion.domain.ReviewArticle.Prior.Body("body"), 0.9), com.histmersion.domain.ReviewArticle.CheckRecheckEdited(""), NoEvidence, last = com.histmersion.domain.ReviewArticle.CheckRecheckLast(false), pairs).toOption.get
    assertEquals(r.findings.map(_.severity), List(Severity.Minor))
    assert(ws.exists(_.contains("is addressed")), ws)

  private val merged = Merged(
    com.histmersion.domain.ReviewArticle.Merged.Round(1),
    Verdict.Major,
    List(
      mc("S1", Some("smith1987_dying>frazer1922_golden")),
      mc("S2", None),
      Finding(com.histmersion.domain.ReviewArticle.Finding.Id("R1"), Reviewer.Rules, Severity.Major, com.histmersion.domain.ReviewArticle.Finding.Kind("synth"), com.histmersion.domain.ReviewArticle.Finding.Where("Intro"), com.histmersion.domain.ReviewArticle.Finding.Quote(""), com.histmersion.domain.ReviewArticle.Finding.Problem("a bridge claim"), com.histmersion.domain.ReviewArticle.Finding.Fix(""))
    ),
    None,
    Nil,
    Nil
  )
  private val byId = merged.findings.map(f => f.id -> f).toMap

  test("set aside: a missing-critique finding, a pair of the checklist (its own or given), a reason"):
    val (ok, pairOnly, ps) = checkSetAside(
      List(
        SetAsideAnswer(com.histmersion.domain.ReviewArticle.SetAsideAnswer.Id("S1"), None, com.histmersion.domain.ReviewArticle.SetAsideAnswer.Reason("Cited for the date only.")),
        SetAsideAnswer(com.histmersion.domain.ReviewArticle.SetAsideAnswer.Id("S2"), Some(com.histmersion.api.research.CritiqueRef.Wire("brown1999_rites>frazer1922_golden")), com.histmersion.domain.ReviewArticle.SetAsideAnswer.Reason("Off the point.")),
        SetAsideAnswer(com.histmersion.domain.ReviewArticle.SetAsideAnswer.Id(""), Some(com.histmersion.api.research.CritiqueRef.Wire("frazer1922_golden>brown1999_rites")), com.histmersion.domain.ReviewArticle.SetAsideAnswer.Reason("No finding names it."))
      ),
      byId,
      pairs
    )
    assertEquals(
      ok,
      List(
        SetAside(com.histmersion.domain.ReviewArticle.SetAside.Id("S1"), CritiqueRef(com.histmersion.api.library.Citekey("smith1987_dying"), com.histmersion.api.library.Citekey("frazer1922_golden")), com.histmersion.domain.ReviewArticle.SetAside.Reason("Cited for the date only.")),
        SetAside(com.histmersion.domain.ReviewArticle.SetAside.Id("S2"), CritiqueRef(com.histmersion.api.library.Citekey("brown1999_rites"), com.histmersion.api.library.Citekey("frazer1922_golden")), com.histmersion.domain.ReviewArticle.SetAside.Reason("Off the point."))
      )
    )
    assertEquals(ps, Nil)
    // a pair alone (no id): the checklist's own direction, stored as a reason
    assertEquals(pairOnly, List(CritiqueReason(com.histmersion.api.library.Citekey("brown1999_rites"), com.histmersion.api.library.Citekey("frazer1922_golden"), com.histmersion.api.research.CritiqueReason.Reason("No finding names it."))))
    val (none, noPair, bad) = checkSetAside(
      List(
        SetAsideAnswer(com.histmersion.domain.ReviewArticle.SetAsideAnswer.Id("R1"), None, com.histmersion.domain.ReviewArticle.SetAsideAnswer.Reason("x")),
        SetAsideAnswer(com.histmersion.domain.ReviewArticle.SetAsideAnswer.Id("S2"), None, com.histmersion.domain.ReviewArticle.SetAsideAnswer.Reason("x")),
        SetAsideAnswer(com.histmersion.domain.ReviewArticle.SetAsideAnswer.Id("S1"), Some(com.histmersion.api.research.CritiqueRef.Wire("zz2000_a>zz2001_b")), com.histmersion.domain.ReviewArticle.SetAsideAnswer.Reason("x")),
        SetAsideAnswer(com.histmersion.domain.ReviewArticle.SetAsideAnswer.Id("S1"), None, com.histmersion.domain.ReviewArticle.SetAsideAnswer.Reason("")),
        SetAsideAnswer(com.histmersion.domain.ReviewArticle.SetAsideAnswer.Id("S9"), None, com.histmersion.domain.ReviewArticle.SetAsideAnswer.Reason("x")),
        SetAsideAnswer(com.histmersion.domain.ReviewArticle.SetAsideAnswer.Id(""), None, com.histmersion.domain.ReviewArticle.SetAsideAnswer.Reason("x")),
        SetAsideAnswer(com.histmersion.domain.ReviewArticle.SetAsideAnswer.Id(""), Some(com.histmersion.api.research.CritiqueRef.Wire("zz2000_a>zz2001_b")), com.histmersion.domain.ReviewArticle.SetAsideAnswer.Reason("x"))
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
    val body =
      "Tin [[knapp2016_tin]].\n\n## Disagreements\n\nArgued [[knapp2016_tin]].\n\n## Open questions\n\nOpen [[knapp2016_tin]].\n\n## Not used\n\n- none\n"
    val corpus = BodyCorpus(_ == "knapp2016_tin", _ == "knapp2016_tin", _ => Nil)
    val m = merged.modify(_.findings).setTo(merged.findings.take(1))
    val a = FixerAnswer(Nil, Nil, Nil, Nil, com.histmersion.domain.ReviewArticle.FixerAnswer.Notes(""), setAside = List(SetAsideAnswer(com.histmersion.domain.ReviewArticle.SetAsideAnswer.Id("S1"), None, com.histmersion.domain.ReviewArticle.SetAsideAnswer.Reason("Cited for the date only."))))
    val (fix, ws) =
      checkFix(a, m, com.histmersion.domain.research.Slug("tin"), body, com.histmersion.domain.ReviewArticle.Snapshot.Kind("concept"), corpus, _ => true, _ => None, 0.9, last = com.histmersion.domain.ReviewArticle.LastRound(false), None, pairs).toOption.get
    assertEquals((fix.setAside.map(_.id), ws), (List("S1"), Nil))
    // without it the MAJOR is undecided
    assert(
      checkFix(
        a.modify(_.setAside).setTo(Nil),
        m,
        com.histmersion.domain.research.Slug("tin"),
        body,
        com.histmersion.domain.ReviewArticle.Snapshot.Kind("concept"),
        corpus,
        _ => true,
        _ => None,
        0.9,
        last = com.histmersion.domain.ReviewArticle.LastRound(false),
        None,
        pairs
      ).isLeft
    )
    val l = absorb(Ledger(com.histmersion.domain.ReviewLedger.Ledger.Slug("tin")), m.findings, com.histmersion.domain.automation.Run.Id("run-1"), com.histmersion.domain.ReviewLedger.Closure.Round(1))._1
    val closed = applyFix(l, fix, com.histmersion.domain.ReviewLedger.ApplyFixRun("run-1"), com.histmersion.domain.ReviewLedger.ApplyFixRound(1))
    val e = closed.entry("S1").get
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
      absorb(closed, List(e.finding.modify(_.severity).setTo(Severity.Minor)), com.histmersion.domain.automation.Run.Id("run-2"), com.histmersion.domain.ReviewLedger.Closure.Round(1))._1.entry("S1").get.status,
      Status.SetAside
    )
    val again = absorb(closed, List(e.finding), com.histmersion.domain.automation.Run.Id("run-2"), com.histmersion.domain.ReviewLedger.Closure.Round(1))._1.entry("S1").get
    assertEquals((again.status, again.notFixed), (Status.Open, Some("set aside before: Cited for the date only.")))

  test("a ledger written before the checklist still decodes (no pair, no set-aside)"):
    val old =
      """{"slug":"tin","entries":[{"finding":{"id":"S1","reviewer":"skeptic","severity":"major","kind":"missing-critique","where":"w","quote":"q","problem":"p","fix":"f","evidence":[],"escalated":false},"run":"r","round":1,"status":"open","claimed":false,"notFixed":null,"reopened":0,"closed":null}],"lessons":[],"task":null}"""
    val l = io.circe.parser.decode[Ledger](old).toOption.get
    assertEquals(l.entries.map(_.finding.pair), List(None))
