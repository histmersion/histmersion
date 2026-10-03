package com.histmersion.core

import com.histmersion.core.AgentProposals.ProposalAnswer
import com.histmersion.core.ReviewArticle.*

/** The pure half of `review-article`: mechanical checks, reviewer validation, the merge (dedupe,
  * worst verdict, escalation), the gate, the rounds, the fixer's validation, the bookkeeping lines. */
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
  private val isTarget = (t: String) => known(t)
  private val isCk     = (t: String) => t == "knapp2016_tin"
  private val record   = Map("knapp2016_tin" -> "The Uluburun ship carried ten tons of copper and one ton of tin.")

  private def fa(sev: String, where: String, problem: String, quote: String = "") =
    FindingAnswer(sev, "synth", where, quote, problem, "fix it")

  test("mechanical: a clean article has no findings"):
    assertEquals(mechanical("a", "concept", Good, isTarget), Nil)

  test("mechanical: uncited paragraph, escaped links, Sources cited, missing sections, dangling link, filler"):
    val body = "It is important to note that tin moved.\n\nSee \\[\\[x\\]\\] and [[nobody2020_x]].\n\n## Sources cited\n\n- a\n"
    val fs = mechanical("a", "concept", body, isTarget)
    assert(fs.forall(_.reviewer == Reviewer.Mechanical))
    assertEquals(fs.map(_.id), fs.indices.map(i => s"M${i + 1}").toList)
    val problems = fs.map(_.problem)
    assert(problems.exists(_.contains("banned filler")), problems)
    assert(problems.exists(_.contains("no [[citekey]]")), problems)
    assert(problems.exists(_.contains("escaped link brackets")), problems)
    assert(problems.exists(_.contains("Sources cited")), problems)
    assert(problems.exists(_.contains("'disagreements'")), problems)
    assert(problems.exists(_.contains("'unresolved' or 'open questions'")), problems)
    assert(problems.exists(_.contains("[[nobody2020_x]] is neither")), problems)
    // filler is minor; a missing section is major
    assert(fs.find(_.problem.contains("filler")).exists(_.severity == Severity.Minor))
    assert(fs.find(_.problem.contains("'not used'")).exists(_.major))

  test("mechanical: an index article needs no section set; a link to an article is not a citation"):
    assertEquals(mechanical("i", "index", "See [[knapp2016_tin]].", isTarget), Nil)
    // the lint rule's own test: a target shaped like a citekey (a year or an underscore) cites
    val fs = mechanical("a", "index", "See [[atlantis]].", isTarget)
    assertEquals(fs.map(_.problem), List("a paragraph with no [[citekey]]"))

  test("a reviewer's findings get code ids; a bad severity is sent back, then dropped on the last attempt"):
    val a = RulesAnswer("MAJOR", List(fa("major", "Intro", "bridge claim"), fa("severe", "x", "y")), "f", Nil, Nil, 5)
    assert(checkRules(a, last = false).left.exists(_.exists(_.contains("'severe'"))))
    val (r, ws) = checkRules(a, last = true).toOption.get
    assertEquals(r.findings.map(f => (f.id, f.severity)), List(("R1", Severity.Major)))
    assertEquals(r.verdict, Verdict.Major)
    assert(ws.exists(_.contains("dropped")), ws)

  test("a verdict worse than the findings is a finding left out; a milder one is corrected"):
    val tooBad = RulesAnswer("MAJOR", List(fa("minor", "x", "y")), "f", Nil, Nil, 1)
    assert(checkRules(tooBad, last = false).left.exists(_.exists(_.contains("no finding is major"))))
    assertEquals(checkRules(tooBad, last = true).map(_._1.verdict), Right(Verdict.Minor))
    val tooMild = RulesAnswer("PASS", List(fa("major", "x", "y")), "f", Nil, Nil, 1)
    assertEquals(checkRules(tooMild, last = false).map(_._1.verdict), Right(Verdict.Major))

  test("cold reader: an empty explain-back slot and more than 8 undefined terms are MAJOR, added by code"):
    val a = ColdReaderAnswer("MINOR", "It is about tin.", List("disagreement"), List(fa("minor", "Intro", "term X")), 9, 0)
    val (r, _) = checkColdReader(a, last = false).toOption.get
    assertEquals(r.verdict, Verdict.Major)
    assertEquals(r.findings.map(_.id), List("C1", "C2", "C3"))
    assert(r.findings.exists(f => f.major && f.problem.contains("'disagreement'")))
    assert(r.findings.exists(f => f.major && f.problem.contains("9 terms")))
    assertEquals(r.emptySlots, List("disagreement"))
    assert(checkColdReader(a.copy(explainBack = " "), last = false).isLeft)
    assert(checkColdReader(a.copy(emptySlots = List("why")), last = false).isLeft)

  test("skeptic: proposals are verified — quote in the record, vocabulary, citekeys — the rest dropped"):
    val ok  = ProposalAnswer("status", "knapp2016_tin", Some("contested"), None, None, "a rebuttal", "ten tons of copper and one ton of tin")
    val bad = ProposalAnswer("status", "knapp2016_tin", Some("contested"), None, None, "a rebuttal", "Cornish tin went east every summer")
    val (r, ws) = checkSkeptic(SkepticAnswer("PASS", Nil, List(ok, bad), 3), last = false, isCk, record.get, 0.9).toOption.get
    assertEquals(r.proposals.map(_.status), List(Some(SourceStatus.Contested)))
    assert(ws.exists(_.contains("not in the record")), ws)

  test("merge: dedupe by where + problem, worst verdict, C and S on the same passage both kept"):
    val m1   = Finding("M1", Reviewer.Mechanical, Severity.Minor, "language", "body", "", "banned filler", "")
    val rule = Report(Reviewer.Rules, Verdict.Minor, List(Finding("R1", Reviewer.Rules, Severity.Minor, "x", "Intro", "q", "Banned filler", "")))
    val cold = Report(Reviewer.ColdReader, Verdict.Minor, List(Finding("C1", Reviewer.ColdReader, Severity.Minor, "term", "Intro", "q", "term undefined", "")))
    val skep = Report(Reviewer.Skeptic, Verdict.Major, List(Finding("S1", Reviewer.Skeptic, Severity.Major, "date", "Intro", "q", "the date is wrong", "")))
    val m = merge(1, List(m1), List(skep, cold, rule), Nil)
    assertEquals(m.verdict, Verdict.Major)
    assertEquals(m.findings.map(_.id), List("M1", "R1", "C1", "S1"))
    val dup = rule.copy(findings = rule.findings.map(_.copy(where = "body", problem = "banned  FILLER")))
    assertEquals(merge(1, List(m1), List(dup), Nil).findings.map(_.id), List("M1"))

  test("merge: a finding the fixer rejected and a reviewer raises again is escalated"):
    val before = Finding("S2", Reviewer.Skeptic, Severity.Major, "date", "Dates", "1320 BCE", "the date is wrong", "")
    val again  = before.copy(id = "S1", problem = "the date has no support")
    val other  = before.copy(id = "S3", reviewer = Reviewer.Rules, problem = "x")
    val m = merge(2, Nil, List(Report(Reviewer.Skeptic, Verdict.Major, List(again)), Report(Reviewer.Rules, Verdict.Major, List(other))), List(before))
    assertEquals(m.escalated.map(_.id), List("S1"))

  test("gate and rounds"):
    val major = Merged(1, Verdict.Major, List(Finding("S1", Reviewer.Skeptic, Severity.Major, "", "", "", "p", "")), None, Nil, Nil)
    val minor = major.copy(verdict = Verdict.Minor, findings = major.findings.map(_.copy(severity = Severity.Minor)))
    assert(done(minor, 1, 3, fix = true))
    assert(!done(major, 1, 3, fix = true))
    assert(done(major, 1, 3, fix = false))
    assert(done(major, 3, 3, fix = true))
    assertEquals(rounds(None, 3, 5, fix = true), 3)
    assertEquals(rounds(Some(9), 3, 5, fix = false), 5)
    assertEquals(rounds(Some(1), 3, 5, fix = true), 2, "a fix needs a round to check it")
    assertEquals(rounds(Some(0), 3, 5, fix = false), 1)

  private val merged = Merged(1, Verdict.Major, List(
    Finding("S1", Reviewer.Skeptic, Severity.Major, "date", "Intro", "", "the date is wrong", ""),
    Finding("C1", Reviewer.ColdReader, Severity.Major, "term", "Intro", "", "undefined term", ""),
    Finding("R1", Reviewer.Rules, Severity.Minor, "language", "Intro", "", "wordy", "")), None, Nil, Nil)

  test("fixer: a sound revision with every major decided and a quoted rejection passes"):
    val a = FixerAnswer(Good, List("C1"), List(RejectionAnswer("S1", "the record says so", "knapp2016_tin",
      "carried ten tons of copper and one ton of tin")), Nil, "")
    val (f, ws) = checkFix(a, merged, "old body", "concept", isTarget, isCk, record.get, 0.9, last = false).toOption.get
    assertEquals(f.applied, List("C1"))
    assertEquals(f.rejected.map(_.id), List("S1"))
    assertEquals(f.unverified, Nil)
    assertEquals(ws, Nil)

  test("fixer: an unquotable rejection, a rejected reader finding, an undecided major, a bad body are sent back"):
    val a = FixerAnswer(Good, Nil, List(
      RejectionAnswer("S1", "no", "knapp2016_tin", "the ship sank in a storm off Lycia in 1320 BCE"),
      RejectionAnswer("C1", "it is clear", "knapp2016_tin", "ten tons of copper")), Nil, "")
    val ps = checkFix(a, merged, "old", "concept", isTarget, isCk, record.get, 0.9, last = false).left.toOption.get
    assert(ps.exists(_.contains("rejection of S1: the quote is not in the record")), ps)
    assert(ps.exists(_.contains("C1 is a reader's confusion")), ps)
    val bad = FixerAnswer("Tin [[ghost2020_x]].\n\n## Sources cited\n", List("S1", "C1"), Nil, Nil, "")
    val ps2 = checkFix(bad, merged, "old", "concept", isTarget, isCk, record.get, 0.9, last = false).left.toOption.get
    assert(ps2.exists(_.contains("ghost2020_x")), ps2)
    assert(ps2.exists(_.contains("Sources cited")), ps2)
    val undecided = FixerAnswer(Good, List("C1"), Nil, Nil, "")
    assert(checkFix(undecided, merged, "old", "concept", isTarget, isCk, record.get, 0.9, last = false)
      .left.exists(_.exists(_.contains("S1 is neither applied nor rejected"))))

  test("fixer, last attempt: a sound body is kept and unverifiable rejections stay open; a broken body never is"):
    val a = FixerAnswer(Good, List("C1"), List(RejectionAnswer("S1", "no", "knapp2016_tin", "nothing like this is in it")), Nil, "")
    val (f, ws) = checkFix(a, merged, "old", "concept", isTarget, isCk, record.get, 0.9, last = true).toOption.get
    assertEquals(f.unverified, List("S1"))
    assertEquals(f.rejected, Nil)
    assert(ws.nonEmpty)
    val broken = a.copy(body = "Tin [[ghost2020_x]].")
    assert(checkFix(broken, merged, "old", "concept", isTarget, isCk, record.get, 0.9, last = true).isLeft)

  test("bookkeeping: the log line and one task per open major (escalated ones say so)"):
    val open = List(Finding("S1", Reviewer.Skeptic, Severity.Major, "date", "Dates", "", "the date is wrong", "", escalated = true))
    val m = Merged(2, Verdict.Major, open, None, Nil, Nil)
    val o = Outcome("sea_peoples", 2, Verdict.Major, open, 3, 1, fixed = true, 0)
    assertEquals(logLine(o, m, "abcdef0123"),
      "review-article [[sea_peoples]]: round 2 → MAJOR (R:0 C:0 S:1 M:0), fixer applied 3 / rejected 1 (agent run abcdef01)")
    assertEquals(taskLines(o), List(
      "review-article [[sea_peoples]]: MAJOR raised again after the fixer rejected it — S1 major (Dates): the date is wrong"))
    assertEquals(taskLines(o.copy(open = Nil)), Nil)

  test("verdicts order PASS < MINOR < MAJOR and parse in any case"):
    assertEquals(Verdict.worst(List(Verdict.Minor, Verdict.Pass)), Verdict.Minor)
    assertEquals(Verdict.parse("major"), Some(Verdict.Major))
    assertEquals(Verdict.of(Nil), Verdict.Pass)
