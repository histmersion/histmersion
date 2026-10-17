package com.histmersion.core

import com.histmersion.core.AgentProposals.Proposal
import com.histmersion.core.Agents.ApprovalKind
import com.histmersion.core.ReviewArticle.*
import com.histmersion.core.ReviewLedger.*

/** The critique checklist in the review (docs/research-review-convergence.md §6.8), pure: what a
  * missing-critique MAJOR is held to in each kind of pass, the skeptic's rulings on the open pairs,
  * the fixer's set-asides and how the ledger records them. */
class ReviewCritiqueSuite extends munit.FunSuite:

  private def side(ck: String, cited: Boolean) = CritiqueSide(ck, cited = cited, mentions = if cited then 1 else 0)
  private def pair(src: String, dst: String, state: CritiqueState = CritiqueState.Open, reason: Option[String] = None) =
    CritiquePair(side(src, cited = false), List("refutes"), side(dst, cited = true), state, reason)

  private val open1   = pair("smith1987_dying", "frazer1922_golden")
  private val open2   = pair("brown1999_rites", "frazer1922_golden")
  private val done    = pair("lee1990_ritual", "frazer1922_golden", CritiqueState.Addressed)
  private val aside   = pair("jones2001_myth", "frazer1922_golden", CritiqueState.Reasoned, Some("cited for the calendar only"))
  private val pairs   = List(open1, open2, done, aside)

  private def mc(id: String, pair: Option[String], sev: Severity = Severity.Major) =
    Finding(id, Reviewer.Skeptic, sev, "missing-critique", "Intro", s"quote $id", s"omits a critique $id", "cite it",
      pair = pair.flatMap(CritiqueRef.parse(_).toOption))
  private def edgeProposal(src: String, dst: String) =
    Proposal(ApprovalKind.Edge, src, edgeKind = Some(EdgeKind.Refutes), target = Some(dst), quote = Some("q"))

  private val pass1   = CritiqueRule(pairs, List(open1, open2), fixable = true, full = true)
  private val confirm = CritiqueRule(pairs, List(open1, open2), fixable = false, full = true)
  private val recheck = CritiqueRule(pairs, Nil, fixable = false, full = false)

  test("a missing-critique MAJOR naming an open pair stands, in the checklist's own direction"):
    val (fs, ps, ws) = checkCritiques(Reviewer.Skeptic, List(mc("S1", Some("frazer1922_golden>smith1987_dying"))), pass1, Nil, last = false)
    assertEquals((fs.map(f => (f.severity, f.pair)), ps, ws),
      (List((Severity.Major, Some(CritiqueRef("smith1987_dying", "frazer1922_golden")))), Nil, Nil))

  test("one naming an addressed or reasoned pair is recorded minor, with the note, in every pass"):
    List(pass1, confirm, recheck).foreach { rule =>
      val (fs, ps, ws) = checkCritiques(Reviewer.Skeptic,
        List(mc("S1", Some("lee1990_ritual>frazer1922_golden")), mc("S2", Some("jones2001_myth>frazer1922_golden"))), rule, Nil, last = false)
      assertEquals(fs.map(_.severity), List(Severity.Minor, Severity.Minor))
      assert(fs.head.problem.contains("(recorded minor: lee1990_ritual>frazer1922_golden is addressed — both sources are cited in one paragraph)"),
        fs.head.problem)
      assert(fs(1).problem.contains("set aside with a stored reason: \"cited for the calendar only\""), fs(1).problem)
      assertEquals((ps, ws.size), (Nil, 2))
    }

  test("no pair on the checklist: with a proposed edge it stands in pass 1, is minor (carried) in a confirmation or re-check"):
    val f = mc("S1", Some("new2020_critic>frazer1922_golden"))
    val proposed = List(edgeProposal("frazer1922_golden", "new2020_critic"))  // either direction
    val (p1, pp1, pw1) = checkCritiques(Reviewer.Skeptic, List(f), pass1, proposed, last = false)
    assertEquals((p1.map(_.severity), pp1, pw1), (List(Severity.Major), Nil, Nil))
    val (c1, cp1, cw1) = checkCritiques(Reviewer.Skeptic, List(f), confirm, proposed, last = false)
    assertEquals((c1.map(_.severity), cp1), (List(Severity.Minor), Nil))
    assert(cw1.exists(_.contains("carried to the next run")), cw1)
    // a re-check proposes nothing: no open pair and no edge → minor, never sent back (it was shown no checklist)
    val (r1, rp1, rw1) = checkCritiques(Reviewer.Skeptic, List(f), recheck, Nil, last = false)
    assertEquals((r1.map(_.severity), rp1), (List(Severity.Minor), Nil))
    assert(rw1.exists(_.contains("names no open checklist pair and no proposed edge")), rw1)

  test("no pair and no proposal: a full panel's answer is sent back; on the last attempt it is recorded minor"):
    val (_, ps, _) = checkCritiques(Reviewer.Skeptic, List(mc("S1", None)), pass1, Nil, last = false)
    assertEquals(ps.size, 1)
    assert(ps.head.contains("S1 (missing-critique, major) names no open pair of the critique checklist (its `pair` is empty)"), ps.head)
    val (fs, ps2, ws) = checkCritiques(Reviewer.Skeptic, List(mc("S1", Some("zz>frazer1922_golden"))), confirm, Nil, last = true)
    assertEquals((fs.map(_.severity), ps2, ws.size), (List(Severity.Minor), Nil, 1))
    // minors and other kinds pass untouched
    val other = mc("S2", None).copy(kind = "date")
    assertEquals(checkCritiques(Reviewer.Skeptic, List(other, mc("S3", None, Severity.Minor)), pass1, Nil, last = false)._1,
      List(other, mc("S3", None, Severity.Minor)))

  test("rulings: every open pair shown is named by a MAJOR or ruled not material; a ruling becomes a reason"):
    val fs = List(mc("S1", Some("smith1987_dying>frazer1922_golden")))
    val ok = List(NotMaterialAnswer("brown1999_rites > frazer1922_golden", "  It is cited for the\nseasons only. "))
    val (rs, ps, ws) = checkNotMaterial(ok, fs, pass1, last = false)
    assertEquals((rs, ps, ws), (List(CritiqueReason("brown1999_rites", "frazer1922_golden", "It is cited for the seasons only.")), Nil, Nil))
    // one pair left without a ruling: sent back, a warning on the last attempt
    val (_, ps2, _) = checkNotMaterial(Nil, fs, pass1, last = false)
    assert(ps2.size == 1 && ps2.head.startsWith("open pairs of the critique checklist without a ruling: brown1999_rites>frazer1922_golden — "), ps2)
    val (_, ps3, ws3) = checkNotMaterial(Nil, fs, pass1, last = true)
    assertEquals(ps3, Nil); assert(ws3.exists(_.contains("(left open)")), ws3)
    // dropped with a warning: a pair already addressed, one the MAJOR names too, one not on the checklist, no reason
    val bad = List(NotMaterialAnswer("lee1990_ritual>frazer1922_golden", "x"), NotMaterialAnswer("smith1987_dying>frazer1922_golden", "x"),
      NotMaterialAnswer("zz2000_a>zz2001_b", "x"), NotMaterialAnswer("brown1999_rites>frazer1922_golden", " "))
    val (rs4, _, ws4) = checkNotMaterial(bad, fs, pass1, last = true)
    assertEquals(rs4, Nil)
    assertEquals(ws4.count(_.startsWith("skeptic: dropped — not material")), 4, ws4)

  test("the skeptic's answer as a whole: findings held to the checklist, rulings kept on the report"):
    val a = SkepticAnswer("MAJOR", List(FindingAnswer("major", "missing-critique", "Intro", "", "omits Smith", "cite Smith",
      pair = Some("smith1987_dying>frazer1922_golden"))), Nil, 6, Nil,
      List(NotMaterialAnswer("brown1999_rites>frazer1922_golden", "Off the article's question.")))
    val (rep, _) = checkSkeptic(a, last = false, _ => true, _ => None, 0.9, None, Some(pass1)).toOption.get
    assertEquals(rep.findings.map(_.pair.map(_.wire)), List(Some("smith1987_dying>frazer1922_golden")))
    assertEquals(rep.notMaterial.map(_.ref.wire), List("brown1999_rites>frazer1922_golden"))
    assertEquals(rep.verdict, Verdict.Major)
    // a MAJOR verdict whose only MAJOR code records minor (an addressed pair) is corrected, not sent back
    val onDone = a.copy(findings = a.findings.map(_.copy(pair = Some("lee1990_ritual>frazer1922_golden"))),
      notMaterial = a.notMaterial :+ NotMaterialAnswer("smith1987_dying>frazer1922_golden", "Off the question."))
    assertEquals(checkSkeptic(onDone, last = false, _ => true, _ => None, 0.9, None, Some(pass1)).map(_._1.verdict), Right(Verdict.Minor))
    // without the ruling it goes back
    assert(checkSkeptic(a.copy(notMaterial = Nil), last = false, _ => true, _ => None, 0.9, None, Some(pass1)).isLeft)
    // a re-check: the same MAJOR on an addressed pair is recorded minor (then scoped as before)
    val rc = RecheckAnswer(Nil, List(FindingAnswer("major", "missing-critique", "Intro", "", "omits Lee", "cite Lee",
      pair = Some("lee1990_ritual>frazer1922_golden"))))
    val (r, ws) = checkRecheck(Reviewer.Skeptic, rc, Prior(Nil, "body", 0.9), "", NoEvidence, last = false, pairs).toOption.get
    assertEquals(r.findings.map(_.severity), List(Severity.Minor))
    assert(ws.exists(_.contains("is addressed")), ws)

  private val merged = Merged(1, Verdict.Major, List(mc("S1", Some("smith1987_dying>frazer1922_golden")), mc("S2", None),
    Finding("R1", Reviewer.Rules, Severity.Major, "synth", "Intro", "", "a bridge claim", "")), None, Nil, Nil)
  private val byId = merged.findings.map(f => f.id -> f).toMap

  test("set aside: a missing-critique finding, a pair of the checklist (its own or given), a reason"):
    val (ok, ps) = checkSetAside(List(SetAsideAnswer("S1", None, "Cited for the date only."),
      SetAsideAnswer("S2", Some("brown1999_rites>frazer1922_golden"), "Off the point.")), byId, pairs)
    assertEquals(ok, List(SetAside("S1", CritiqueRef("smith1987_dying", "frazer1922_golden"), "Cited for the date only."),
      SetAside("S2", CritiqueRef("brown1999_rites", "frazer1922_golden"), "Off the point.")))
    assertEquals(ps, Nil)
    val (none, bad) = checkSetAside(List(SetAsideAnswer("R1", None, "x"), SetAsideAnswer("S2", None, "x"),
      SetAsideAnswer("S1", Some("zz2000_a>zz2001_b"), "x"), SetAsideAnswer("S1", None, ""), SetAsideAnswer("S9", None, "x")), byId, pairs)
    assertEquals(none, Nil)
    assertEquals(bad.map(_.takeWhile(_ != ':')), List("set aside R1", "set aside S2", "set aside S1", "set aside S1", "set aside 'S9'"))

  test("the fixer's answer: a set-aside decides a MAJOR, with no edit; the ledger closes it as set aside, with the reason"):
    val body = "Tin [[knapp2016_tin]].\n\n## Disagreements\n\nArgued [[knapp2016_tin]].\n\n## Open questions\n\nOpen [[knapp2016_tin]].\n\n## Not used\n\n- none\n"
    val corpus = BodyCorpus(_ == "knapp2016_tin", _ == "knapp2016_tin", _ => Nil)
    val m = merged.copy(findings = merged.findings.take(1))
    val a = FixerAnswer(Nil, Nil, Nil, Nil, "", setAside = List(SetAsideAnswer("S1", None, "Cited for the date only.")))
    val (fix, ws) = checkFix(a, m, "tin", body, "concept", corpus, _ => true, _ => None, 0.9, last = false, None, pairs).toOption.get
    assertEquals((fix.setAside.map(_.id), ws), (List("S1"), Nil))
    // without it the MAJOR is undecided
    assert(checkFix(a.copy(setAside = Nil), m, "tin", body, "concept", corpus, _ => true, _ => None, 0.9, last = false, None, pairs).isLeft)
    val l = absorb(Ledger("tin"), m.findings, "run-1", 1)._1
    val closed = applyFix(l, fix, "run-1", 1)
    val e = closed.entry("S1").get
    assertEquals((e.status, e.closed.map(c => (c.by, c.note, c.quote))),
      (Status.SetAside, Some(("fixer", "Cited for the date only.", "smith1987_dying>frazer1922_golden"))))
    assertEquals(closed.openMajors, Nil)
    assert(closedLine(e).contains("set aside as not material (smith1987_dying>frazer1922_golden) — Cited for the date only."), closedLine(e))
    // raised again as a MINOR (its pair now reasoned): stays set aside; raised again MAJOR: re-opened, with why
    assertEquals(absorb(closed, List(e.finding.copy(severity = Severity.Minor)), "run-2", 1)._1.entry("S1").get.status, Status.SetAside)
    val again = absorb(closed, List(e.finding), "run-2", 1)._1.entry("S1").get
    assertEquals((again.status, again.notFixed), (Status.Open, Some("set aside before: Cited for the date only.")))

  test("a ledger written before the checklist still decodes (no pair, no set-aside)"):
    val old = """{"slug":"tin","entries":[{"finding":{"id":"S1","reviewer":"skeptic","severity":"major","kind":"missing-critique","where":"w","quote":"q","problem":"p","fix":"f","evidence":[],"escalated":false},"run":"r","round":1,"status":"open","claimed":false,"notFixed":null,"reopened":0,"closed":null}],"lessons":[],"task":null}"""
    val l = io.circe.parser.decode[Ledger](old).toOption.get
    assertEquals(l.entries.map(_.finding.pair), List(None))
