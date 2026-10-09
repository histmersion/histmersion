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
    val a = ColdReaderAnswer("MINOR", "It is about tin.", List("disagreement"),
      List(ReaderFindingAnswer("minor", "term", "Intro", "", "term X", "define it")), 9, 0)
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

  import ArticleEdits.{EditAnswer, Op}
  private def edit(op: Op, anchor: String, text: String = null) = EditAnswer(op, anchor, Option(text))
  /** The glossed sentence the fixer adds for C1. */
  private val gloss = edit(Op.Replace, "Tin reached the Aegean", "Tin, the scarce half of bronze, reached the Aegean")
  private val corpus = BodyCorpus(isCk, isTarget, t => if t.contains("| a | b |") then List("table-not-rendered — a table") else Nil)
  private def fix(a: FixerAnswer, last: Boolean, before: String = Good) =
    checkFix(a, merged, "tin", before, "concept", corpus, isCk, record.get, 0.9, last)

  test("fixer: edits applied to the reviewed body, every major decided and a quoted rejection pass"):
    val a = FixerAnswer(List(gloss), List("C1"), List(RejectionAnswer("S1", "the record says so", "knapp2016_tin",
      "carried ten tons of copper and one ton of tin")), Nil, "")
    val (f, ws) = fix(a, last = false).toOption.get
    assertEquals(f.body, Good.replace("Tin reached", "Tin, the scarce half of bronze, reached"))
    assertEquals(f.edits.map(e => (e.index, e.op, e.applied)), List((1, Op.Replace, true)), "the step output records each edit")
    assertEquals(f.applied, List("C1"))
    assertEquals(f.rejected.map(_.id), List("S1"))
    assertEquals(f.unverified, Nil)
    assertEquals((f.wordsBefore, f.wordsAfter), (words(Good), words(Good) + 5))
    assertEquals(ws, Nil)

  test("fixer: an unquotable rejection, a rejected reader finding, an undecided major, a bad edit are sent back"):
    val a = FixerAnswer(List(gloss), Nil, List(
      RejectionAnswer("S1", "no", "knapp2016_tin", "the ship sank in a storm off Lycia in 1320 BCE"),
      RejectionAnswer("C1", "it is clear", "knapp2016_tin", "ten tons of copper")), Nil, "")
    val ps = fix(a, last = false).left.toOption.get
    assert(ps.exists(_.contains("rejection of S1: the quote is not in the record")), ps)
    assert(ps.exists(_.contains("C1 is a reader's confusion")), ps)
    val bad = FixerAnswer(List(edit(Op.InsertAfter, "is open [[knapp2016_tin]].", " Or [[ghost2020_x]]."),
      edit(Op.InsertBefore, "## Not used", "## Sources cited\n\n- x [[knapp2016_tin]]\n\n"),
      edit(Op.Replace, "by land", "by sea")), List("S1", "C1"), Nil, Nil, "")
    val ps2 = fix(bad, last = false).left.toOption.get
    assert(ps2.exists(p => p.startsWith("edit 1 (insertAfter") && p.contains("ghost2020_x")), ps2)
    assert(ps2.exists(_.contains("Sources cited")), ps2)
    assert(ps2.exists(_.startsWith("edit 3 (replace \"by land\"): the anchor is not in the article")), ps2)
    val undecided = FixerAnswer(List(gloss), List("C1"), Nil, Nil, "")
    assert(fix(undecided, last = false).left.exists(_.exists(_.contains("S1 is neither applied nor rejected"))))
    val idle = FixerAnswer(Nil, List("C1", "S1"), Nil, Nil, "")
    assert(fix(idle, last = false).left.exists(_.exists(_.contains("no edit changed the article"))))

  test("fixer: an uncited paragraph and a formatting error the edits introduce are sent back"):
    val a = FixerAnswer(List(edit(Op.InsertAfter, "by sea [[knapp2016_tin]].", "\n\nBronze needs tin.\n\n| a | b |")),
      List("C1", "S1"), Nil, Nil, "")
    val ps = fix(a, last = false).left.toOption.get
    assert(ps.exists(_.contains("no [[citekey]]")), ps)
    assert(ps.exists(_ == "formatting: table-not-rendered — a table"), ps)
    // a formatting error the body already had is not the edits' doing
    val had = Good + "\n| a | b | [[knapp2016_tin]]\n"
    assert(!fix(FixerAnswer(List(gloss), List("C1", "S1"), Nil, Nil, ""), last = false, had).left.exists(_.exists(_.contains("formatting"))))

  test("fixer, last attempt: the edits that apply are kept, the others reported; unverifiable rejections stay open"):
    val a = FixerAnswer(List(edit(Op.Replace, "no such passage", "x"), gloss), List("C1"),
      List(RejectionAnswer("S1", "no", "knapp2016_tin", "nothing like this is in it")), Nil, "")
    assert(fix(a, last = false).isLeft)
    val (f, ws) = fix(a, last = true).toOption.get
    assertEquals(f.body, Good.replace("Tin reached", "Tin, the scarce half of bronze, reached"))
    assertEquals(f.edits.map(_.applied), List(false, true))
    assertEquals(f.unverified, List("S1"))
    assertEquals(f.rejected, Nil)
    assert(ws.exists(w => w.startsWith("fixer: kept despite — edit 1 (replace \"no such passage\")")), ws)

  test("fixer, last attempt: an edit that links to nothing is never applied, and the rest still are"):
    val a = FixerAnswer(List(edit(Op.InsertAfter, "is open [[knapp2016_tin]].", " See [[ghost2020_x]]."), gloss), List("C1", "S1"), Nil, Nil, "")
    val (f, ws) = fix(a, last = true).toOption.get
    assert(!f.body.contains("ghost2020_x"), f.body)
    assert(f.body.contains("the scarce half of bronze"))
    assert(ws.exists(_.contains("ghost2020_x")), ws)

  test("fixer, last attempt: a result with a NEW link problem or no text is fatal; an old one is not"):
    // a delete that leaves nothing
    val empty = FixerAnswer(List(edit(Op.Delete, Good.trim)), List("C1", "S1"), Nil, Nil, "")
    assert(fix(empty, last = true).isLeft)
    val withGhost = Good + "\nOld [[ghost2020_x]] link [[knapp2016_tin]].\n"
    val (f, ws) = fix(FixerAnswer(List(gloss), List("C1", "S1"), Nil, Nil, ""), last = true, withGhost).toOption.get
    assert(f.body.contains("the scarce half of bronze"))
    assert(ws.exists(_.contains("ghost2020_x")), ws)

  test("checkBody: a whole body (a writer's draft) is checked with the same rules"):
    val c = checkBody("tin", "", "concept", ArticleEdits.Draft.Body("Tin [[ghost2020_x]].\n\n## Sources cited\n"), corpus)
    assert(c.fatal)
    assert(c.problems.exists(_.contains("ghost2020_x")) && c.problems.exists(_.contains("Sources cited")), c.problems)
    val ok = checkBody("tin", "", "concept", ArticleEdits.Draft.Body(Good), corpus)
    assertEquals((ok.problems, ok.fatal, ok.edits), (Nil, false, Nil))
    // an index fragment needs no section set
    assertEquals(checkBody("tin", "", "index", ArticleEdits.Draft.Body("More tin [[knapp2016_tin]]."), corpus).problems, Nil)

  // ── #958: coverage ─────────────────────────────────────────────────────────────────────────────

  /** `Good` with its hand-written Not used section removed: what an article with a filter holds. */
  private val Generated = Good.substring(0, Good.indexOf("## Not used")).trim + "\n"
  private val cov = ArticleCoverage.compute("tin", ArticleFilter(topics = List("tin")), 3,
    List(CoverageSource("knapp2016_tin", "Tin", Some(2016), SourceStatus.Current),
      CoverageSource("egypt2001_old", "Egypt", Some(2001), SourceStatus.Current),
      CoverageSource("iso2024_new", "Isotopes", Some(2024), SourceStatus.Contested)),
    Set("knapp2016_tin"), List("knapp2016_tin"), Map("egypt2001_old" -> "about Egypt", "knapp2016_tin" -> "stale"), Nil, Nil)
  private val ckCov = (t: String) => Set("knapp2016_tin", "egypt2001_old", "iso2024_new")(t)

  test("mechanical (#958): with a filter, no Not used section is required and a hand-written one or a Method note is MAJOR"):
    assertEquals(mechanical("a", "concept", Generated, isTarget, generated = true), Nil)
    assert(mechanical("a", "concept", Generated, isTarget).exists(_.problem.contains("'not used'")), "without a filter it is still required")
    val written = mechanical("a", "concept", Good + "\n_Method: search {topics:[tin]} → 3._\n", isTarget, generated = true)
    assertEquals(written.map(f => (f.kind, f.major)), List((CoverageKind, true), (CoverageKind, true)))
    assert(written.head.problem.startsWith("a hand-written Not used section") && written(1).problem.startsWith("a hand-written Method note"))

  test("coverageDrift (#958): an unplaced source is MAJOR, stale reasons minor; ids follow the mechanical ones"):
    val fs = coverageDrift(cov, 4)
    assertEquals(fs.map(f => (f.id, f.severity, f.kind)), List(("M4", Severity.Major, CoverageKind), ("M5", Severity.Minor, CoverageKind)))
    assertEquals(fs.head.problem, "1 source the filter returns is neither cited nor given a reason: [[iso2024_new]]")
    assert(fs(1).problem.contains("[[knapp2016_tin]]"), fs(1).problem)

  test("checkBody (#958): with a filter, a Not used section the edits write is a problem"):
    val c = checkBody("tin", Generated, "concept", ArticleEdits.Draft.Edits(List(edit(Op.InsertAfter, "is open [[knapp2016_tin]].",
      "\n\n## Not used\n\n- [[knapp2016_tin]] — x"))), corpus.copy(generated = true))
    assert(c.problems.exists(_.startsWith("a hand-written Not used section")), c.problems)

  private val unplaced = Merged(1, Verdict.Major, coverageDrift(cov, 1), None, Nil, Nil)
  private def fixCov(a: FixerAnswer, last: Boolean = false, c: Option[Coverage] = Some(cov)) =
    checkFix(a, unplaced, "tin", Generated, "concept", corpus.copy(isCitekey = ckCov, generated = true), ckCov, record.get, 0.9,
      last, c)

  test("fixer (#958): an unplaced source is answered with a reason — no edit needed; the reasons are checked"):
    val ok = FixerAnswer(Nil, List("M1", "M2"), Nil, Nil, "",
      List(CoverageReason("[[iso2024_new]]", "Its isotopes date a later trade."), CoverageReason("knapp2016_tin", "")))
    val (f, ws) = fixCov(ok).fold(ps => fail(ps.mkString("; ")), identity)
    assertEquals(f.reasons, List(CoverageReason("iso2024_new", "Its isotopes date a later trade."), CoverageReason("knapp2016_tin", "")))
    assertEquals((f.body, ws), (Generated, Nil))
    // M1 applied but nothing placed; a reason for a cited source; one outside the filter; one for a source with no reason to remove
    val bad = FixerAnswer(Nil, List("M1", "M2"), Nil, Nil, "", List(CoverageReason("knapp2016_tin", "not needed"),
      CoverageReason("zz1999_gone", "never read"), CoverageReason("egypt2001_old", "")))
    val ps = fixCov(bad).left.toOption.get
    assert(ps.contains("reason for [[knapp2016_tin]]: the article cites it — a reason is only for a source it leaves out"), ps)
    assert(ps.contains("reason for [[zz1999_gone]]: the filter does not return it — give reasons only for its sources"), ps)
    // removing egypt2001_old's stored reason is allowed — and leaves it unplaced too
    assert(!ps.exists(_.startsWith("reason for [[egypt2001_old]]")), ps)
    assert(ps.contains("M1: still neither cited nor given a reason: [[egypt2001_old]], [[iso2024_new]]"), ps)
    // citing it places it as well
    val cite = FixerAnswer(List(edit(Op.InsertAfter, "is open [[knapp2016_tin]].", " Isotopes point east [[iso2024_new]] (contested).")),
      List("M1", "M2"), Nil, Nil, "", List(CoverageReason("knapp2016_tin", "")))
    assert(fixCov(cite).isRight, fixCov(cite))
    // an article without a filter takes no reasons
    assert(fixCov(ok, c = None).left.exists(_.exists(_.contains("records no filter"))))

  test("evidence: verified against the record, dropped with a warning when not there, kept by the merge"):
    val ev = evidenceCheck(isCk, record.get, 0.9)
    val f = FindingAnswer("major", "contradictory", "Intro", "by sea", "the record says otherwise", "fix", List(
      EvidenceAnswer("knapp2016_tin", Some(120), "carried ten tons of copper and one ton of tin"),
      EvidenceAnswer("knapp2016_tin", None, "the cargo was Cornish"),
      EvidenceAnswer("ghost2020_x", None, "anything at all here")))
    val (r, ws) = checkSkeptic(SkepticAnswer("MAJOR", List(f), Nil, 1), last = false, isCk, record.get, 0.9).toOption.get
    assertEquals(r.findings.head.evidence.map(e => (e.citekey, e.offset, e.ratio)), List(("knapp2016_tin", Some(120), 1.0)))
    assertEquals(ws.count(_.contains("S1 evidence dropped")), 2, ws)
    assert(ws.exists(_.contains("not in the record of [[knapp2016_tin]]")) && ws.exists(_.contains("'ghost2020_x' is not a citekey")), ws)
    // the rules reviewer's evidence is checked the same way; a missing check drops it
    val (rr, _) = checkRules(RulesAnswer("MAJOR", List(f), "f", Nil, Nil, 1), last = false, ev).toOption.get
    assertEquals(rr.findings.head.evidence.size, 1)
    assertEquals(checkRules(RulesAnswer("MAJOR", List(f), "f", Nil, Nil, 1), last = false).toOption.get._1.findings.head.evidence, Nil)
    // a duplicate's evidence joins the kept finding, and the fixer reads it under the finding
    val e2 = Evidence("knapp2016_tin", None, "one ton of tin", 1.0)
    val dup = rr.findings.head.copy(id = "R9", evidence = List(e2))
    val m = merge(1, Nil, List(rr, rr.copy(findings = List(dup))), Nil)
    assertEquals(m.findings.map(_.id), List("R1"))
    assertEquals(m.findings.head.evidence.map(_.quote), List("carried ten tons of copper and one ton of tin", "one ton of tin"))
    val text = findingsText(m)
    assert(text.contains("\n  evidence: [[knapp2016_tin]] @120: \"carried ten tons of copper and one ton of tin\""), text)
    assert(text.contains("\n  evidence: [[knapp2016_tin]]: \"one ton of tin\""), text)

  test("the fixer's turns grow with the majors and the factual minors, up to the cap"):
    // S1 major, C1 major, R1 a rules minor: 3 findings that may need a lookup
    assertEquals(fixerTurns(12, merged, 1, 40), 15)
    assertEquals(fixerTurns(12, merged, 2, 40), 18)
    assertEquals(fixerTurns(12, merged, 10, 20), 20, "capped")
    assertEquals(fixerTurns(12, merged, 1, 5), 12, "a cap below the agent's own turns never cuts them")
    val readerMinor = merged.copy(findings = List(Finding("C2", Reviewer.ColdReader, Severity.Minor, "term", "x", "", "p", "")))
    assertEquals(fixerTurns(12, readerMinor, 1, 40), 12, "a reader's minor needs no lookup")

  test("bookkeeping: the log line"):
    val open = List(Finding("S1", Reviewer.Skeptic, Severity.Major, "date", "Dates", "", "the date is wrong", "", escalated = true))
    val m = Merged(2, Verdict.Major, open, None, Nil, Nil)
    val o = Outcome("sea_peoples", 2, Verdict.Major, open, 3, 1, fixed = true, 0)
    assertEquals(logLine(o, m, "abcdef0123"),
      "review-article [[sea_peoples]]: round 2 → MAJOR (R:0 C:0 S:1 M:0), fixer applied 3 / rejected 1 (agent run abcdef01)")

  test("verdicts order PASS < MINOR < MAJOR and parse in any case"):
    assertEquals(Verdict.worst(List(Verdict.Minor, Verdict.Pass)), Verdict.Minor)
    assertEquals(Verdict.parse("major"), Some(Verdict.Major))
    assertEquals(Verdict.of(Nil), Verdict.Pass)

  // ── the review ledger: checks, the verification pass, declining a minor ──────────────────────────

  private val revised = Good.replace("Tin reached", "Tin, the scarce half of bronze, reached")
  private val askedTin  = Finding("R1", Reviewer.Rules, Severity.Major, "term", "Intro", "Tin reached the Aegean by sea", "tin is not defined", "")
  private val askedDate = Finding("R4", Reviewer.Rules, Severity.Major, "date", "Disagreements", "Two dates are argued", "no date", "")
  private val prior = Prior(List(askedTin, askedDate), revised, 0.9)

  test("checks: one per finding asked about, each id one of them, each quote in the CURRENT article"):
    val ok = List(CheckAnswer("R1", fixed = true, "Tin, the scarce half of bronze", "glossed"),
      CheckAnswer("R4", fixed = false, "Two dates are argued", "still undated"))
    assertEquals(checkChecks(Reviewer.Rules, prior, ok, last = false),
      (List(Check("R1", true, "Tin, the scarce half of bronze", "glossed"), Check("R4", false, "Two dates are argued", "still undated")), Nil, Nil))
    val bad = List(CheckAnswer("R1", fixed = true, "Tin, the precious half of bronze", ""), CheckAnswer("S2", fixed = true, "x", ""),
      CheckAnswer("R4", fixed = false, "", ""))
    val (cs, ps, ws) = checkChecks(Reviewer.Rules, prior, bad, last = false)
    assertEquals((cs, ws), (Nil, Nil))
    assert(ps.exists(_.startsWith("check of R1: the quote is not in the current article")), ps)
    assert(ps.exists(_.startsWith("check of 'S2': not one of the findings you were asked to check (R1, R4)")), ps)
    assert(ps.exists(_.startsWith("check of R4: no quote — quote the current text that shows it still there")), ps)
    assert(ps.contains("R1: no check — say whether it is fixed") && ps.contains("R4: no check — say whether it is fixed"), ps)
    // the last attempt keeps what passed; the rest stays open, with a warning
    val (cs2, ps2, ws2) = checkChecks(Reviewer.Rules, prior, ok.take(1), last = true)
    assertEquals((cs2.map(_.id), ps2), (List("R1"), Nil))
    assertEquals(ws2, List("rules: R4: no check — say whether it is fixed (left open)"))

  test("checks: an empty quote passes only when nothing is left to quote (the passage is gone, or there never was one)"):
    val gone = Prior(List(askedDate), Good.replace("Two dates are argued", "The wreck dates to 1320 BCE"), 0.9)
    assertEquals(checkChecks(Reviewer.Rules, gone, List(CheckAnswer("R4", fixed = true, "", "deleted")), last = false)._1.map(_.id), List("R4"))
    val count = Finding("C3", Reviewer.ColdReader, Severity.Major, "term", "explain-back", "", "12 terms are undefined (more than 8)", "")
    assertEquals(checkChecks(Reviewer.ColdReader, Prior(List(count), Good, 0.9),
      List(CheckAnswer("C3", fixed = false, "", "still 12")), last = false)._2, Nil)

  test("a full panel's answer carries checks only when the ledger asked; they are verified like the findings"):
    val withChecks = RulesAnswer("PASS", Nil, "f", accuracyChecked = 3,
      checks = List(CheckAnswer("R1", fixed = true, "Tin, the scarce half of bronze", "")))
    assertEquals(checkRules(withChecks, last = false, prior = Some(Prior(List(askedTin), revised, 0.9))).map(_._1.checks.map(_.id)),
      Right(List("R1")))
    assert(checkRules(withChecks.copy(checks = Nil), last = false, prior = Some(Prior(List(askedTin), revised, 0.9))).isLeft,
      "an open finding of its own must be checked")
    assertEquals(checkRules(withChecks, last = false, prior = None).map(_._1.checks), Right(Nil), "no ledger: no checks")
    val reader = ColdReaderAnswer("PASS", "It asks. It answers. They disagree. It is open. Done.", Nil, Nil, 0, 0, None,
      List(CheckAnswer("C1", fixed = true, "the scarce half of bronze", "")))
    val readerPrior = Some(Prior(List(Finding("C1", Reviewer.ColdReader, Severity.Major, "term", "Intro", "Tin reached", "tin", "")), revised, 0.9))
    assertEquals(checkColdReader(reader, last = false, readerPrior).map(_._1.checks.map(_.fixed)), Right(List(true)))

  test("scope: inside the edited passages anything stands; outside, a MAJOR only as a factual error with evidence"):
    val edited = "Tin, the scarce half of bronze, reached the Aegean by sea [[knapp2016_tin]]."
    val ev = Evidence("knapp2016_tin", None, "ten tons of copper", 1.0)
    val inside = Finding("S1", Reviewer.Skeptic, Severity.Major, "synth", "Intro", "the scarce half of bronze", "a bridge claim", "")
    val outside = inside.copy(quote = "Two dates are argued")
    assertEquals(scope(inside, edited, 0.9), (inside, None))
    assertEquals(scope(outside.copy(kind = "contradictory", evidence = List(ev)), edited, 0.9)._2, None)
    val (down, w) = scope(outside, edited, 0.9)
    assertEquals(down.severity, Severity.Minor)
    assert(w.exists(_.contains("\"a bridge claim\" is outside the edited passages")), w)
    assertEquals(scope(outside.copy(kind = "date"), edited, 0.9)._1.severity, Severity.Minor, "a factual kind still needs its evidence")
    assertEquals(scope(outside.copy(severity = Severity.Minor), edited, 0.9)._2, None)

  test("checkRecheck: checks, scoped findings with verified evidence, at most three lessons"):
    val ev = evidenceCheck(isCk, record.get, 0.9)
    val a = RecheckAnswer(
      List(CheckAnswer("R1", fixed = true, "Tin, the scarce half of bronze", ""), CheckAnswer("R4", fixed = false, "Two dates are argued", "")),
      List(FindingAnswer("major", "recall", "Open questions", "The source of the tin is open", "the record names the source", "say it",
        List(EvidenceAnswer("knapp2016_tin", None, "one ton of tin"))),
        FindingAnswer("major", "weight", "Open questions", "The source of the tin is open", "too short", "expand")),
      List("one", "two", "three", "four"))
    val (rc, ws) = checkRecheck(Reviewer.Rules, a, prior, "Tin, the scarce half of bronze", ev, last = false).toOption.get
    assertEquals(rc.checks.map(c => (c.id, c.fixed)), List(("R1", true), ("R4", false)))
    assertEquals(rc.findings.map(f => (f.kind, f.severity)), List(("recall", Severity.Major), ("weight", Severity.Minor)))
    assertEquals(rc.lessons, List("one", "two", "three"))
    assert(ws.exists(_.contains("\"too short\" is outside the edited passages")), ws)
    assert(checkRecheck(Reviewer.Rules, a.copy(checks = Nil), prior, "", ev, last = false).isLeft)
    assert(checkRecheck(Reviewer.Rules, RecheckAnswer(), prior, "", ev, last = true).isRight, "the last attempt keeps what passed")

  test("fixer: a minor may be declined with a reason; a MAJOR may not; an unknown id is sent back"):
    val ok = FixerAnswer(List(gloss), List("C1"), List(RejectionAnswer("S1", "the record says so", "knapp2016_tin",
      "carried ten tons of copper and one ton of tin")), Nil, "", wontFix = List(WontFixAnswer("R1", "wordy, but exact")))
    assertEquals(fix(ok, last = false).map(_._1.wontFix), Right(List(WontFix("R1", "wordy, but exact"))))
    val bad = ok.copy(wontFix = List(WontFixAnswer("S1", "no"), WontFixAnswer("R9", "x"), WontFixAnswer("R1", " ")))
    val ps = fix(bad, last = false).left.toOption.get
    assert(ps.contains("S1 is MAJOR: apply it or reject it — only a minor finding can be declined"), ps)
    assert(ps.contains("won't fix 'R9': there is no such finding") && ps.contains("won't fix R1: give the reason"), ps)
