package com.histmersion.api

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.config.given
import com.histmersion.domain.given

import com.histmersion.domain.ReviewArticle.*
import com.histmersion.domain.ReviewLedger.*
import io.circe.parser.decode
import io.circe.syntax.*

/** Convergence of the review (docs/research-review-convergence.md §6.9), pure: the final pass that
  * defers what it finds new, the regression guard, a "fixed" check held to the checklist, the
  * fixer's guards (the pairing repair, a claimed pair still unanswered, pairs a revision brings in,
  * edits that reopen a closed finding) and the not-fixed record — with the live ledgers of 2026-10-19
  * as fixtures (`review/ledger-*.json`: the entries of three of the eight articles that ended MAJOR). */
class ReviewConvergenceSuite extends munit.FunSuite:

  private def live(slug: String): Ledger =
    decode[Ledger](scala.io.Source.fromResource(s"review/ledger-$slug.json")(using scala.io.Codec.UTF8).mkString)
      .fold(e => fail(s"$slug: $e"), identity)

  test("live ledgers decode — entries written before §6.9 have no not-fixed count and no deferral"):
    val a = live("achilles_origins")
    assertEquals(a.entries.map(_.id).sorted, List("C12", "R8", "S3", "S4", "S5", "S6"))
    assert(a.entries.forall(e => e.notFixedCount == 0 && e.deferred.isEmpty))
    assertEquals(a.entry("S6").flatMap(_.finding.pair).map(_.wire), Some("kelly2021_gods>bachvarova2016_hittite"))
    // and round-trips with the new fields
    assertEquals(decode[Ledger](a.asJson.noSpaces).map(_.entries.size), Right(6))
    assertEquals(live("gods_and_migrations").entries.count(_.reopened > 0), 3)

  // ── the final pass defers what it finds new ────────────────────────────────────────────────────

  test("deferFinal: a MAJOR new in the final pass is deferred; a reopened one, a regression and an earlier open one stand"):
    // the live gods_and_migrations entries: S24/S31/S7 were fixed in pass 2 and raised again in pass 3
    // (reopened), S35–S37 raised for the first time by the final pass
    val g = live("gods_and_migrations")
    val before = g.copy(entries = g.entries.filterNot(e => Set("S35", "S36", "S37")(e.id)).map(e =>
      if e.reopened > 0 then e.copy(status = Status.Fixed, reopened = e.reopened - 1, notFixed = None) else e))
    val after = g   // the final pass absorbed: S24/S31/S7 reopened, S35–S37 new
    val regression = (f: Finding) => f.id == "S37"
    val (d, ids) = deferFinal(before, after, "run-9", regression)
    assertEquals(ids.sorted, List("S35", "S36"))
    assertEquals(d.entries.filter(_.deferred.contains("run-9")).map(_.id).sorted, List("S35", "S36"))
    // the run's verdict counts them as minor; the next run sees them as open MAJORs
    val m = merged(d, 3, run = Some("run-9"))
    assertEquals(m.deferred.sorted, List("S35", "S36"))
    assert(!m.majors.exists(f => Set("S35", "S36")(f.id)))
    assertEquals(m.majors.map(_.id).sorted, List("S24", "S31", "S37", "S7"))
    assertEquals(merged(d, 1, run = Some("run-10")).majors.size, 6)
    assertEquals(d.openMajors.size, 6, "still open MAJOR in the ledger: the next run's work")
    // an open minor the final pass raised to MAJOR is new too
    val minorBefore = before.copy(entries = before.entries :+ after.entry("S35").get.copy(finding =
      after.entry("S35").get.finding.copy(severity = Severity.Minor)))
    assertEquals(deferFinal(minorBefore, after, "run-9", _ => false)._2.sorted, List("S35", "S36", "S37"))

  test("a run whose only open MAJORs are deferred ends below MAJOR; its backlog item says they are deferred"):
    val g = live("gods_and_migrations")
    val only = g.copy(entries = g.entries.filter(e => Set("S35", "S36")(e.id)))
    val (d, _) = deferFinal(Ledger("gods_and_migrations"), only, "run-9", _ => false)
    assertEquals(merged(d, 3, run = Some("run-9")).verdict, Verdict.Minor)
    val task = taskText(d, "run-9").get
    assert(task.startsWith("review-article [[gods_and_migrations]]: 2 open MAJORs — "), task)
    assert(task.contains("[new in the final pass: deferred to the next run]"), task)
    assert(!taskText(d, "run-10").get.contains("deferred"), "a later run's item does not repeat it")

  test("finalRegression: a pair the run had answered and broke; a factual error in the text the run wrote"):
    def p(state: CritiqueState) = CritiquePair(CritiqueSide("chang2015", cited = true), List("challenges"),
      CritiqueSide("gray2003_language", cited = true), state)
    val f = Finding("S24", Reviewer.Skeptic, Severity.Major, "missing-critique", "w", "Gray and Atkinson (2003) dated the first split",
      "p", "x", pair = Some(CritiqueRef("chang2015", "gray2003_language")))
    assert(finalRegression(f, List(p(CritiqueState.Addressed)), List(p(CritiqueState.Open)), "", 0.9))
    assert(!finalRegression(f, List(p(CritiqueState.Open)), List(p(CritiqueState.Open)), "", 0.9), "open from the start: a discovery")
    val fact = Finding("S9", Reviewer.Skeptic, Severity.Major, "date", "w", "dated to 1320 BCE by the cargo", "p", "x")
    assert(finalRegression(fact, Nil, Nil, "The wreck was dated to 1320 BCE by the cargo.", 0.9))
    assert(!finalRegression(fact, Nil, Nil, "Another passage altogether, edited.", 0.9))
    assert(!finalRegression(fact.copy(kind = "lost"), Nil, Nil, "The wreck was dated to 1320 BCE by the cargo.", 0.9),
      "a reader's confusion in edited text is a discovery, not a factual regression")

  // ── a "fixed" check held to the checklist ──────────────────────────────────────────────────────

  test("holdPairChecks: the live S6 checked fixed while its pair is still open is recorded NOT fixed, with why"):
    val a = live("achilles_origins")
    val open = CritiquePair(CritiqueSide("kelly2021_gods", cited = true), List("challenges"),
      CritiqueSide("bachvarova2016_hittite", cited = true), CritiqueState.Open)
    val checks = List(Check("S6", fixed = true, "Gilan nevertheless identifies", "Gilan's criticism is now described"),
      Check("S4", fixed = true, "q", "n"))
    val (held, ws) = holdPairChecks(checks, a.entries.map(_.finding), List(open))
    assertEquals(held.map(c => (c.id, c.fixed)), List(("S6", false), ("S4", true)))
    assert(held.head.note.startsWith("[[kelly2021_gods]] and [[bachvarova2016_hittite]] are still not cited in one paragraph"), held.head.note)
    assert(held.head.note.contains("the reviewer: Gilan's criticism is now described"), held.head.note)
    assertEquals(ws.size, 1)
    // addressed now: the check stands
    assertEquals(holdPairChecks(checks, a.entries.map(_.finding), List(open.copy(state = CritiqueState.Addressed)))._1.map(_.fixed),
      List(true, true))
    // the ledger counts it as not fixed
    val l = applyChecks(a, Reviewer.Skeptic, held, "run-9", 2)
    assertEquals(l.entry("S6").map(e => (e.status, e.notFixedCount)), Some((Status.Open, 1)))
    assertEquals(l.entry("S4").map(_.status), Some(Status.Fixed))

  // ── the regression guard ───────────────────────────────────────────────────────────────────────

  private val tinBefore =
    """Tin reached the Aegean by sea [[knapp2016_tin]]; [[zz2024_iso]] traces it to Central Asia.
      |
      |The date of the wreck is about 1320 BCE [[knapp2016_tin]].
      |""".stripMargin

  private def ledgerWith(es: Entry*) = Ledger("tin", es.toList)
  private val pairFixed = Entry(Finding("S1", Reviewer.Skeptic, Severity.Major, "missing-critique", "Intro", "Tin reached the Aegean by sea",
    "omits the isotope critique", "cite it", pair = Some(CritiqueRef("zz2024_iso", "knapp2016_tin"))), "r", 1, Status.Fixed,
    closed = Some(Closure("skeptic", 2, "r", "", "[[zz2024_iso]] traces it to Central Asia")))
  private val textFixed = Entry(Finding("R1", Reviewer.Rules, Severity.Major, "date", "Disagreements", "The date of the wreck is argued",
    "no date", "give the year"), "r", 1, Status.Fixed, closed = Some(Closure("rules", 2, "r", "", "The date of the wreck is about 1320 BCE [[knapp2016_tin]]")))
  private def together(b: String, r: CritiqueRef) = Critiques.together(b, r, Some(_))
  private def cites(b: String, ck: String) = AgentVerify.wikilinks(b).contains(ck)

  test("guard: a closed pair split apart, a fixed text written back, a citation the fix rests on dropped"):
    val l = ledgerWith(pairFixed, textFixed)
    assertEquals(guard(l, tinBefore, tinBefore, together, cites, 0.9), Nil)
    val split = tinBefore.replace("; [[zz2024_iso]] traces it to Central Asia.", ".\n\nIt came from Central Asia [[zz2024_iso]].")
    assertEquals(guard(l, tinBefore, split, together, cites, 0.9).map(_.id), List("S1"))
    assert(guard(l, tinBefore, split, together, cites, 0.9).head.why.contains("no longer cited in one paragraph"))
    val back = tinBefore.replace("The date of the wreck is about 1320 BCE", "The date of the wreck is argued")
    assertEquals(guard(l, tinBefore, back, together, cites, 0.9).map(r => (r.id, r.why.take(24))), List(("R1", "the text it fixed is bac")))
    val dropped = tinBefore.replace(" [[zz2024_iso]] traces it to Central Asia", " an isotope study traces it to Central Asia")
    val g = guard(l, tinBefore, dropped, together, cites, 0.9)
    assertEquals(g.map(_.id), List("S1"))
    // open and set-aside findings are not guarded
    assertEquals(guard(ledgerWith(pairFixed.copy(status = Status.Open)), tinBefore, split, together, cites, 0.9), Nil)

  // ── the fixer's guards ─────────────────────────────────────────────────────────────────────────

  private val body =
    """Tin reached the Aegean by sea [[knapp2016_tin]].
      |
      |Kullmann answers West's reading of the cargo [[zz2015_kull]].
      |
      |## Disagreements
      |
      |The date of the wreck is about 1320 BCE [[knapp2016_tin]].
      |
      |## Open questions
      |
      |Where the tin was mined is open [[knapp2016_tin]]. West's reading is older [[zz2011_west]].
      |
      |## Not used
      |
      |- none
      |""".stripMargin

  private val keys = Set("knapp2016_tin", "zz2024_iso", "zz2015_kull", "zz2011_west", "zz2020_new", "zz2021_crit")
  private val corpus = BodyCorpus(keys, keys, _ => Nil)
  private val edges = List(CoverageEdge("zz2024_iso", "refutes", "knapp2016_tin"), CoverageEdge("zz2015_kull", "challenges", "zz2011_west"),
    CoverageEdge("zz2021_crit", "challenges", "zz2020_new"))
  private val works: String => Option[CritiquePairing.Work] = Map(
    "zz2011_west" -> CritiquePairing.Work("zz2011_west", List("West"), Some(2011)),
    "zz2015_kull" -> CritiquePairing.Work("zz2015_kull", List("Kullmann"), Some(2015))).get
  private def pairsOf(b: String) = Critiques.build(b, t => Some(t).filter(keys), edges, Nil)
  private def guards(l: Ledger) = FixGuards(pairsOf,
    (b0, b1) => guard(l, b0, b1, (b, r) => Critiques.find(pairsOf(b), r).exists(!_.open), cites, 0.9),
    b => CritiquePairing.repair(b, CritiquePairing.hits(b, t => Some(t).filter(keys), edges, works)))

  private def mcFinding(id: String, pair: String, quote: String = "Tin reached the Aegean by sea") =
    Finding(id, Reviewer.Skeptic, Severity.Major, "missing-critique", "Intro", quote, "omits a critique", "cite the critic",
      pair = CritiqueRef.parse(pair).toOption)
  private def edit(op: String, anchor: String, text: String) = ArticleEdits.EditAnswer(ArticleEdits.Op.parse(op).get, anchor, Some(text))
  private def fix(m: Merged, a: FixerAnswer, last: Boolean, l: Ledger = Ledger("tin")) =
    checkFix(a, m, "tin", body, "concept", corpus, keys, _ => None, 0.9, last, None, pairsOf(body), Some(guards(l)))

  test("fixer: a missing critique listed as applied with its pair still apart goes back; on the last attempt it is not fixed, with why"):
    val m = Merged(1, Verdict.Major, List(mcFinding("S1", "zz2024_iso>knapp2016_tin")), None, Nil, Nil)
    // the fixer describes the isotope study in a new paragraph, apart from the claim: the pair stays open
    val apart = FixerAnswer(List(edit("insertAfter", "Tin reached the Aegean by sea [[knapp2016_tin]].",
      "\n\nAn isotope study disagrees [[zz2024_iso]].")), List("S1"), Nil, Nil, "")
    val back = fix(m, apart, last = false)
    assert(back.left.exists(_.exists(_.startsWith("S1 is listed as applied, but [[zz2024_iso]] and [[knapp2016_tin]] are not cited in one paragraph"))), back)
    val (kept, ws) = fix(m, apart, last = true).toOption.get
    assertEquals((kept.applied, kept.notFixed), (Nil, List(WontFix("S1", "listed as applied, but [[zz2024_iso]] and [[knapp2016_tin]] are not cited in one paragraph"))))
    assert(ws.exists(_.contains("kept despite")), ws)
    // the ledger: not claimed, not fixed once more
    val l = absorb(Ledger("tin"), m.findings, "r", 1)._1
    val after = applyFix(l, kept, "r", 1)
    assertEquals(after.entry("S1").map(e => (e.claimed, e.notFixedCount, e.notFixed.exists(_.contains("pass 1: listed as applied")))),
      Some((false, 1, true)))
    // beside the claim, it stands
    val beside = FixerAnswer(List(edit("replace", "Tin reached the Aegean by sea [[knapp2016_tin]].",
      "Tin reached the Aegean by sea [[knapp2016_tin]]; an isotope study disagrees [[zz2024_iso]].")), List("S1"), Nil, Nil, "")
    assertEquals(fix(m, beside, last = false).map(_._1.applied), Right(List("S1")))

  test("fixer: the pairing repair answers a named partner itself — the finding is 'repaired' and claimed for its re-check"):
    val m = Merged(1, Verdict.Major, List(mcFinding("S1", "zz2015_kull>zz2011_west", "Kullmann answers West's reading of the cargo")), None, Nil, Nil)
    // the fixer leaves it alone (it set nothing aside, applied nothing): the repair cites West where the paragraph names him
    val nothing = FixerAnswer(Nil, Nil, Nil, Nil, "")
    val (f, _) = fix(m, nothing, last = false).toOption.get
    assert(f.body.contains("Kullmann answers West's reading of the cargo [[zz2015_kull]] [[zz2011_west]]."), f.body)
    assertEquals((f.repaired, f.notFixed), (List("S1"), Nil))
    assert(f.repairs.head.contains("cite [[zz2011_west]] there (fixable)"), f.repairs)
    val l = applyFix(absorb(Ledger("tin"), m.findings, "r", 1)._1, f, "r", 1)
    assertEquals(l.entry("S1").map(_.claimed), Some(true))

  test("fixer: a citation the revision brings in whose recorded critique no paragraph discusses goes back; a pair-only set-aside answers it"):
    val m = Merged(1, Verdict.Minor, List(Finding("R1", Reviewer.Rules, Severity.Minor, "term", "Intro", "", "bronze undefined", "define")),
      None, Nil, Nil)
    val adds = List(edit("insertAfter", "Where the tin was mined is open [[knapp2016_tin]].", " A newer study proposes Cornwall [[zz2020_new]]."))
    val bare = FixerAnswer(adds, List("R1"), Nil, Nil, "")
    val back = fix(m, bare, last = false)
    assert(back.left.exists(_.exists(_.contains("your edits cite [[zz2020_new]], whose recorded critiques no paragraph discusses: zz2021_crit>zz2020_new (challenges)"))), back)
    val aside = bare.copy(setAside = List(SetAsideAnswer("", Some("zz2021_crit>zz2020_new"), "The critique concerns its dating, not the ore source cited here.")))
    val (f, ws) = fix(m, aside, last = false).toOption.get
    assertEquals((f.pairsAside.map(_.ref.wire), ws), (List("zz2021_crit>zz2020_new"), Nil))
    // on the last attempt it is kept, with the warning
    assert(fix(m, bare, last = true).toOption.get._2.exists(_.contains("whose recorded critiques no paragraph discusses")))

  test("fixer: edits that reopen a closed finding go back; on the last attempt they are dropped and the rest is kept"):
    val closed = Entry(Finding("R1", Reviewer.Rules, Severity.Major, "date", "Disagreements", "The date of the wreck is argued",
      "no date", "give the year"), "r", 1, Status.Fixed)
    val l = ledgerWith(closed)
    val m = Merged(2, Verdict.Minor, List(Finding("C1", Reviewer.ColdReader, Severity.Minor, "term", "Intro", "", "tin undefined", "define")),
      None, Nil, Nil)
    val gloss = edit("replace", "Tin reached the Aegean by sea", "Tin, the scarce half of bronze, reached the Aegean by sea")
    val undo  = edit("replace", "The date of the wreck is about 1320 BCE", "The date of the wreck is argued")
    val a = FixerAnswer(List(gloss, undo), List("C1"), Nil, Nil, "")
    val back = checkFix(a, m, "tin", body, "concept", corpus, keys, _ => None, 0.9, last = false, None, pairsOf(body), Some(guards(l)))
    assert(back.left.exists(_.exists(_.startsWith("your edits reopen a closed finding — R1 (closed): the text it fixed is back"))), back)
    val (f, ws) = checkFix(a, m, "tin", body, "concept", corpus, keys, _ => None, 0.9, last = true, None, pairsOf(body), Some(guards(l))).toOption.get
    assert(f.body.contains("Tin, the scarce half of bronze") && f.body.contains("about 1320 BCE"), f.body)
    assert(ws.exists(_.contains("edit(s) 2 dropped: they reopened R1")), ws)

  test("fixer: every open finding it leaves unanswered is recorded not fixed, with why — never silently skipped"):
    val m = Merged(1, Verdict.Major, List(
      Finding("R1", Reviewer.Rules, Severity.Major, "date", "Disagreements", "The date of the wreck", "no date", "give the year"),
      Finding("C1", Reviewer.ColdReader, Severity.Minor, "term", "Intro", "", "tin undefined", "define"),
      Finding("C2", Reviewer.ColdReader, Severity.Minor, "term", "Intro", "", "bronze undefined", "define")), None, Nil, Nil)
    val a = FixerAnswer(List(edit("replace", "Tin reached", "Tin, a metal, reached")), List("C1"), Nil, Nil, "")
    assert(fix(m, a, last = false).left.exists(_.contains("major finding R1 is neither applied nor rejected")))
    val (f, _) = fix(m, a, last = true).toOption.get
    assertEquals(f.notFixed, List(WontFix("R1", "the fixer neither applied nor rejected it"), WontFix("C2", "the fixer did not answer it")))
    val l = applyFix(absorb(Ledger("tin"), m.findings, "r", 1)._1, f, "r", 1)
    assertEquals(l.entries.map(e => (e.id, e.claimed, e.notFixedCount)), List(("R1", false, 1), ("C1", true, 0), ("C2", false, 1)))
    assert(notes(l)("R1").contains("pass 1: the fixer neither applied nor rejected it"), notes(l))
    // twice not fixed: stubborn, and the note says how often
    val twice = applyFix(l, f, "r", 2)
    assertEquals(stubborn(twice.copy(entries = twice.entries.map(e => e.copy(finding = e.finding.copy(pair = Some(CritiqueRef("a2000_x", "b2001_y"))))))).map(_.id),
      List("R1", "C2"))
    assert(notes(twice)("R1").startsWith("not fixed yet (2 times)"), notes(twice))
