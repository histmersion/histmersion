package com.histmersion.api

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.config.given
import com.histmersion.domain.given

import com.histmersion.domain.ReviewArticle.*
import com.histmersion.domain.ReviewLedger.*
import io.circe.parser.decode
import io.circe.syntax.*
import com.histmersion.api.research.{CritiquePair, CritiquePairing, CritiqueRef, Critiques, CritiqueSide, CritiqueState}
import com.histmersion.api.research.Article
import com.histmersion.api.research.Coverage

/** Convergence of the review (docs/research-review-convergence.md §6.9), pure: the final pass that defers what it finds
  * new, the regression guard, a "fixed" check held to the checklist, the fixer's guards (the pairing repair, a claimed
  * pair still unanswered, pairs a revision brings in, edits that reopen a closed finding) and the not-fixed record —
  * with the live ledgers of 2026-10-19 as fixtures (`review/ledger-*.json`: the entries of three of the eight articles
  * that ended MAJOR).
  */
class ReviewConvergenceSuite extends munit.FunSuite:

  private val minRatio = com.histmersion.domain.ReviewArticle.CritiqueRule.MinRatio(0.9)

  private def live(slug: String): Ledger =
    decode[Ledger](scala.io.Source.fromResource(s"review/ledger-$slug.json")(using scala.io.Codec.UTF8).mkString)
      .fold(e => fail(s"$slug: $e"), identity)

  test("live ledgers decode — entries written before §6.9 have no not-fixed count and no deferral"):
    val a = live("achilles_origins")
    assertEquals(a.entries.map(_.id).sortBy(_.value), List("C12", "R8", "S3", "S4", "S5", "S6"))
    assert(a.entries.forall(e => e.notFixedCount.value == 0 && e.deferred.isEmpty))
    assertEquals(a.entry(com.histmersion.domain.ReviewArticle.Finding.Id("S6")).flatMap(_.finding.pair).map(_.wire), Some("kelly2021_gods>bachvarova2016_hittite"))
    // and round-trips with the new fields
    assertEquals(decode[Ledger](a.asJson.noSpaces).map(_.entries.size), Right(6))
    assertEquals(live("gods_and_migrations").entries.count(_.reopened.value > 0), 3)

  // ── the final pass defers what it finds new ────────────────────────────────────────────────────

  test(
    "deferFinal: a MAJOR new in the final pass is deferred; a reopened one, a regression and an earlier open one stand"
  ):
    // the live gods_and_migrations entries: S24/S31/S7 were fixed in pass 2 and raised again in pass 3
    // (reopened), S35–S37 raised for the first time by the final pass
    val g = live("gods_and_migrations")
    val before = g.modify(_.entries).setTo(g.entries
        .filterNot(e => Set("S35", "S36", "S37")(e.id.value))
        .map(e =>
          if e.reopened.value > 0 then e.modify(_.status).setTo(Status.Fixed).modify(_.reopened).setTo(Entry.Reopened(e.reopened.value - 1)).modify(_.notFixed).setTo(None) else e
        )
    )
    val after = g // the final pass absorbed: S24/S31/S7 reopened, S35–S37 new
    val regression = (f: Finding) => f.id.value == "S37"
    val (d, ids) = deferFinal(before, after, com.histmersion.domain.automation.Run.Id("run-9"), regression)
    assertEquals(ids.sortBy(_.value), List("S35", "S36"))
    assertEquals(d.entries.filter(_.deferred.contains("run-9")).map(_.id).sortBy(_.value), List("S35", "S36"))
    // the run's verdict counts them as minor; the next run sees them as open MAJORs
    val m = merged(d, com.histmersion.domain.ReviewLedger.Closure.Round(3), run = Some(com.histmersion.domain.automation.Run.Id("run-9")))
    assertEquals(m.deferred.sortBy(_.value), List("S35", "S36"))
    assert(!m.majors.exists(f => Set("S35", "S36")(f.id.value)))
    assertEquals(m.majors.map(_.id).sortBy(_.value), List("S24", "S31", "S37", "S7"))
    assertEquals(merged(d, com.histmersion.domain.ReviewLedger.Closure.Round(1), run = Some(com.histmersion.domain.automation.Run.Id("run-10"))).majors.size, 6)
    assertEquals(d.openMajors.size, 6, "still open MAJOR in the ledger: the next run's work")
    // an open minor the final pass raised to MAJOR is new too
    val minorBefore = before.modify(_.entries).setTo(before.entries :+ after
        .entry(com.histmersion.domain.ReviewArticle.Finding.Id("S35"))
        .get
        .copy(finding = after.entry(com.histmersion.domain.ReviewArticle.Finding.Id("S35")).get.finding.copy(severity = Severity.Minor))
    )
    assertEquals(deferFinal(minorBefore, after, com.histmersion.domain.automation.Run.Id("run-9"), _ => false)._2.sortBy(_.value), List("S35", "S36", "S37"))

  test("a run whose only open MAJORs are deferred ends below MAJOR; its backlog item says they are deferred"):
    val g = live("gods_and_migrations")
    val only = g.modify(_.entries).setTo(g.entries.filter(e => Set("S35", "S36")(e.id.value)))
    val (d, _) = deferFinal(Ledger(com.histmersion.domain.ReviewLedger.Ledger.Slug("gods_and_migrations")), only, com.histmersion.domain.automation.Run.Id("run-9"), _ => false)
    assertEquals(merged(d, com.histmersion.domain.ReviewLedger.Closure.Round(3), run = Some(com.histmersion.domain.automation.Run.Id("run-9"))).verdict, Verdict.Minor)
    val task = taskText(d, com.histmersion.domain.automation.Run.Id("run-9")).get
    assert(task.value.startsWith("review-article [[gods_and_migrations]]: 2 open MAJORs — "), task)
    assert(task.value.contains("[new in the final pass: deferred to the next run]"), task)
    assert(!taskText(d, com.histmersion.domain.automation.Run.Id("run-10")).get.value.contains("deferred"), "a later run's item does not repeat it")

  test("finalRegression: a pair the run had answered and broke; a factual error in the text the run wrote"):
    def p(state: CritiqueState) = CritiquePair(
      CritiqueSide(com.histmersion.api.library.Citekey("chang2015"), cited = com.histmersion.api.research.CritiqueSide.Cited(true)),
      List(com.histmersion.api.research.CritiquePair.Kinds("challenges")),
      CritiqueSide(com.histmersion.api.library.Citekey("gray2003_language"), cited = com.histmersion.api.research.CritiqueSide.Cited(true)),
      state
    )
    val f = Finding(
      com.histmersion.domain.ReviewArticle.Finding.Id("S24"),
      Reviewer.Skeptic,
      Severity.Major,
      com.histmersion.domain.ReviewArticle.Finding.Kind("missing-critique"),
      com.histmersion.domain.ReviewArticle.Finding.Where("w"),
      com.histmersion.domain.ReviewArticle.Finding.Quote("Gray and Atkinson (2003) dated the first split"),
      com.histmersion.domain.ReviewArticle.Finding.Problem("p"),
      com.histmersion.domain.ReviewArticle.Finding.Fix("x"),
      pair = Some(CritiqueRef(com.histmersion.api.library.Citekey("chang2015"), com.histmersion.api.library.Citekey("gray2003_language")))
    )
    assert(finalRegression(f, List(p(CritiqueState.Addressed)), List(p(CritiqueState.Open)), com.histmersion.domain.ReviewArticle.Fix.Body(""), minRatio))
    assert(
      !finalRegression(f, List(p(CritiqueState.Open)), List(p(CritiqueState.Open)), com.histmersion.domain.ReviewArticle.Fix.Body(""), minRatio),
      "open from the start: a discovery"
    )
    val fact = Finding(com.histmersion.domain.ReviewArticle.Finding.Id("S9"), Reviewer.Skeptic, Severity.Major, com.histmersion.domain.ReviewArticle.Finding.Kind("date"), com.histmersion.domain.ReviewArticle.Finding.Where("w"), com.histmersion.domain.ReviewArticle.Finding.Quote("dated to 1320 BCE by the cargo"), com.histmersion.domain.ReviewArticle.Finding.Problem("p"), com.histmersion.domain.ReviewArticle.Finding.Fix("x"))
    assert(finalRegression(fact, Nil, Nil, com.histmersion.domain.ReviewArticle.Fix.Body("The wreck was dated to 1320 BCE by the cargo."), minRatio))
    assert(!finalRegression(fact, Nil, Nil, com.histmersion.domain.ReviewArticle.Fix.Body("Another passage altogether, edited."), minRatio))
    assert(
      !finalRegression(fact.modify(_.kind).setTo(com.histmersion.domain.ReviewArticle.Finding.Kind("lost")), Nil, Nil, com.histmersion.domain.ReviewArticle.Fix.Body("The wreck was dated to 1320 BCE by the cargo."), minRatio),
      "a reader's confusion in edited text is a discovery, not a factual regression"
    )

  // ── a "fixed" check held to the checklist ──────────────────────────────────────────────────────

  test("holdPairChecks: the live S6 checked fixed while its pair is still open is recorded NOT fixed, with why"):
    val a = live("achilles_origins")
    val open = CritiquePair(
      CritiqueSide(com.histmersion.api.library.Citekey("kelly2021_gods"), cited = com.histmersion.api.research.CritiqueSide.Cited(true)),
      List(com.histmersion.api.research.CritiquePair.Kinds("challenges")),
      CritiqueSide(com.histmersion.api.library.Citekey("bachvarova2016_hittite"), cited = com.histmersion.api.research.CritiqueSide.Cited(true)),
      CritiqueState.Open
    )
    val checks = List(
      Check(com.histmersion.domain.ReviewArticle.Check.Id("S6"), fixed = com.histmersion.domain.ReviewArticle.Check.Fixed(true), com.histmersion.domain.ReviewArticle.Check.Quote("Gilan nevertheless identifies"), com.histmersion.domain.ReviewArticle.Check.Note("Gilan's criticism is now described")),
      Check(com.histmersion.domain.ReviewArticle.Check.Id("S4"), fixed = com.histmersion.domain.ReviewArticle.Check.Fixed(true), com.histmersion.domain.ReviewArticle.Check.Quote("q"), com.histmersion.domain.ReviewArticle.Check.Note("n"))
    )
    val (held, ws) = holdPairChecks(checks, a.entries.map(_.finding), List(open))
    assertEquals(held.map(c => (c.id, c.fixed)), List(("S6", false), ("S4", true)))
    assert(
      held.head.note.value
        .startsWith("[[kelly2021_gods]] and [[bachvarova2016_hittite]] are still not cited in one paragraph"),
      held.head.note
    )
    assert(held.head.note.value.contains("the reviewer: Gilan's criticism is now described"), held.head.note)
    assertEquals(ws.size, 1)
    // addressed now: the check stands
    assertEquals(
      holdPairChecks(checks, a.entries.map(_.finding), List(open.modify(_.state).setTo(CritiqueState.Addressed)))._1
        .map(_.fixed),
      List(true, true)
    )
    // the ledger counts it as not fixed
    val l = applyChecks(a, Reviewer.Skeptic, held, com.histmersion.domain.ReviewLedger.ApplyChecksRun("run-9"), com.histmersion.domain.ReviewLedger.ApplyChecksRound(2))
    assertEquals(l.entry(com.histmersion.domain.ReviewArticle.Finding.Id("S6")).map(e => (e.status, e.notFixedCount)), Some((Status.Open, 1)))
    assertEquals(l.entry(com.histmersion.domain.ReviewArticle.Finding.Id("S4")).map(_.status), Some(Status.Fixed))

  // ── the regression guard ───────────────────────────────────────────────────────────────────────

  private val tinBefore = com.histmersion.domain.ReviewArticle.Fix.Body(
    """Tin reached the Aegean by sea [[knapp2016_tin]]; [[zz2024_iso]] traces it to Central Asia.
      |
      |The date of the wreck is about 1320 BCE [[knapp2016_tin]].
      |""".stripMargin
  )

  private def ledgerWith(es: Entry*) = Ledger(com.histmersion.domain.ReviewLedger.Ledger.Slug("tin"), es.toList)
  private val pairFixed = Entry(
    Finding(
      com.histmersion.domain.ReviewArticle.Finding.Id("S1"),
      Reviewer.Skeptic,
      Severity.Major,
      com.histmersion.domain.ReviewArticle.Finding.Kind("missing-critique"),
      com.histmersion.domain.ReviewArticle.Finding.Where("Intro"),
      com.histmersion.domain.ReviewArticle.Finding.Quote("Tin reached the Aegean by sea"),
      com.histmersion.domain.ReviewArticle.Finding.Problem("omits the isotope critique"),
      com.histmersion.domain.ReviewArticle.Finding.Fix("cite it"),
      pair = Some(CritiqueRef(com.histmersion.api.library.Citekey("zz2024_iso"), com.histmersion.api.library.Citekey("knapp2016_tin")))
    ),
    com.histmersion.domain.ReviewLedger.Entry.Run("r"),
    com.histmersion.domain.ReviewLedger.Entry.Round(1),
    Status.Fixed,
    closed = Some(Closure(com.histmersion.domain.ReviewLedger.Closure.By("skeptic"), com.histmersion.domain.ReviewLedger.Closure.Round(2), com.histmersion.domain.ReviewLedger.Closure.Run("r"), com.histmersion.domain.ReviewLedger.Closure.Note(""), com.histmersion.domain.ReviewLedger.Closure.Quote("[[zz2024_iso]] traces it to Central Asia")))
  )
  private val textFixed = Entry(
    Finding(
      com.histmersion.domain.ReviewArticle.Finding.Id("R1"),
      Reviewer.Rules,
      Severity.Major,
      com.histmersion.domain.ReviewArticle.Finding.Kind("date"),
      com.histmersion.domain.ReviewArticle.Finding.Where("Disagreements"),
      com.histmersion.domain.ReviewArticle.Finding.Quote("The date of the wreck is argued"),
      com.histmersion.domain.ReviewArticle.Finding.Problem("no date"),
      com.histmersion.domain.ReviewArticle.Finding.Fix("give the year")
    ),
    com.histmersion.domain.ReviewLedger.Entry.Run("r"),
    com.histmersion.domain.ReviewLedger.Entry.Round(1),
    Status.Fixed,
    closed = Some(Closure(com.histmersion.domain.ReviewLedger.Closure.By("rules"), com.histmersion.domain.ReviewLedger.Closure.Round(2), com.histmersion.domain.ReviewLedger.Closure.Run("r"), com.histmersion.domain.ReviewLedger.Closure.Note(""), com.histmersion.domain.ReviewLedger.Closure.Quote("The date of the wreck is about 1320 BCE [[knapp2016_tin]]")))
  )
  private def together(b: com.histmersion.domain.ReviewArticle.Fix.Body, r: CritiqueRef) =
    Critiques.together(b, r, t => Some(com.histmersion.api.library.Citekey(t.value)))
  private def cites(b: com.histmersion.domain.ReviewArticle.Fix.Body, ck: com.histmersion.domain.library.Citekey) =
    AgentVerify.wikilinks(AgentVerify.Text(b.value)).exists(_.value == ck.value)

  test("guard: a closed pair split apart, a fixed text written back, a citation the fix rests on dropped"):
    val l = ledgerWith(pairFixed, textFixed)
    assertEquals(guard(l, tinBefore, tinBefore, together, cites, minRatio), Nil)
    val split = com.histmersion.domain.ReviewArticle.Fix.Body(
      tinBefore.value.replace("; [[zz2024_iso]] traces it to Central Asia.", ".\n\nIt came from Central Asia [[zz2024_iso]].")
    )
    assertEquals(guard(l, tinBefore, split, together, cites, minRatio).map(_.id), List("S1"))
    assert(guard(l, tinBefore, split, together, cites, minRatio).head.why.value.contains("no longer cited in one paragraph"))
    val back = com.histmersion.domain.ReviewArticle.Fix.Body(tinBefore.value.replace("The date of the wreck is about 1320 BCE", "The date of the wreck is argued"))
    assertEquals(
      guard(l, tinBefore, back, together, cites, minRatio).map(r => (r.id, r.why.value.take(24))),
      List(("R1", "the text it fixed is bac"))
    )
    val dropped = com.histmersion.domain.ReviewArticle.Fix.Body(
      tinBefore.value.replace(" [[zz2024_iso]] traces it to Central Asia", " an isotope study traces it to Central Asia")
    )
    val g = guard(l, tinBefore, dropped, together, cites, minRatio)
    assertEquals(g.map(_.id), List("S1"))
    // open and set-aside findings are not guarded
    assertEquals(guard(ledgerWith(pairFixed.modify(_.status).setTo(Status.Open)), tinBefore, split, together, cites, minRatio), Nil)

  // ── the fixer's guards ─────────────────────────────────────────────────────────────────────────

  private val body = com.histmersion.domain.ReviewArticle.Fix.Body(
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
  )

  private val keys = Set("knapp2016_tin", "zz2024_iso", "zz2015_kull", "zz2011_west", "zz2020_new", "zz2021_crit")
  private val isKey = (ck: com.histmersion.domain.library.Citekey) => keys(ck.value)
  private val isTarget = (s: com.histmersion.domain.research.Slug) => keys(s.value)
  private val corpus = BodyCorpus(isKey, isTarget, _ => Nil)
  private val edges = List(
    Coverage.Edge(com.histmersion.api.library.Citekey("zz2024_iso"), com.histmersion.api.research.Coverage.Edge.Kind("refutes"), com.histmersion.api.library.Citekey("knapp2016_tin")),
    Coverage.Edge(com.histmersion.api.library.Citekey("zz2015_kull"), com.histmersion.api.research.Coverage.Edge.Kind("challenges"), com.histmersion.api.library.Citekey("zz2011_west")),
    Coverage.Edge(com.histmersion.api.library.Citekey("zz2021_crit"), com.histmersion.api.research.Coverage.Edge.Kind("challenges"), com.histmersion.api.library.Citekey("zz2020_new"))
  )
  private val works: com.histmersion.api.library.Citekey => Option[CritiquePairing.Work] = Map(
    com.histmersion.api.library.Citekey("zz2011_west") -> CritiquePairing.Work(com.histmersion.api.library.Citekey("zz2011_west"), List(com.histmersion.api.research.CritiquePairing.Work.Names("West")), Some(com.histmersion.api.catalogue.Year(2011))),
    com.histmersion.api.library.Citekey("zz2015_kull") -> CritiquePairing.Work(com.histmersion.api.library.Citekey("zz2015_kull"), List(com.histmersion.api.research.CritiquePairing.Work.Names("Kullmann")), Some(com.histmersion.api.catalogue.Year(2015)))
  ).get
  private val resolve = (t: Article.Link.Target) => Some(t.value).filter(keys).map(com.histmersion.api.library.Citekey(_))
  private def pairsOf(b: com.histmersion.domain.ReviewArticle.Fix.Body) = Critiques.build(b, resolve, edges, Nil)
  private def guards(l: Ledger) = FixGuards(
    pairsOf,
    (b0, b1) => guard(l, b0, b1, (b, r) => Critiques.find(pairsOf(b), r).exists(!_.open), cites, minRatio),
    b => CritiquePairing.repair(b, CritiquePairing.hits(b, resolve, edges, works))
  )

  private def mcFinding(
      id: com.histmersion.domain.ReviewArticle.Finding.Id,
      pair: CritiqueRef.Wire,
      quote: com.histmersion.domain.ReviewArticle.Finding.Quote = com.histmersion.domain.ReviewArticle.Finding.Quote("Tin reached the Aegean by sea")
  ) =
    Finding(
      id,
      Reviewer.Skeptic,
      Severity.Major,
      com.histmersion.domain.ReviewArticle.Finding.Kind("missing-critique"),
      com.histmersion.domain.ReviewArticle.Finding.Where("Intro"),
      quote,
      com.histmersion.domain.ReviewArticle.Finding.Problem("omits a critique"),
      com.histmersion.domain.ReviewArticle.Finding.Fix("cite the critic"),
      pair = CritiqueRef.parse(pair).toOption
    )
  private def edit(op: Article.Edits.Op.Wire, anchor: Article.Edits.EditAnswer.Anchor, text: Article.Edits.EditAnswer.Text) =
    Article.Edits.EditAnswer(Article.Edits.Op.parse(op).get, anchor, Some(text))
  private def fix(m: Merged, a: FixerAnswer, last: LastRound, l: Ledger = Ledger(com.histmersion.domain.ReviewLedger.Ledger.Slug("tin"))) =
    checkFix(a, m, com.histmersion.domain.research.Slug("tin"), body, com.histmersion.domain.ReviewArticle.Snapshot.Kind("concept"), corpus, isKey, _ => None, minRatio, last, None, pairsOf(body), Some(guards(l)))

  test(
    "fixer: a missing critique listed as applied with its pair still apart goes back; on the last attempt it is not fixed, with why"
  ):
    val m = Merged(com.histmersion.domain.ReviewArticle.Merged.Round(1), Verdict.Major, List(mcFinding(Finding.Id("S1"), CritiqueRef.Wire("zz2024_iso>knapp2016_tin"))), None, Nil, Nil)
    // the fixer describes the isotope study in a new paragraph, apart from the claim: the pair stays open
    val apart = FixerAnswer(
      List(
        edit(
          Article.Edits.Op.Wire("insertAfter"),
          Article.Edits.EditAnswer.Anchor("Tin reached the Aegean by sea [[knapp2016_tin]]."),
          Article.Edits.EditAnswer.Text("\n\nAn isotope study disagrees [[zz2024_iso]].")
        )
      ),
      List(com.histmersion.domain.ReviewArticle.Finding.Id("S1")),
      Nil,
      Nil,
      com.histmersion.domain.ReviewArticle.FixerAnswer.Notes("")
    )
    val back = fix(m, apart, last = LastRound(false))
    assert(
      back.left.exists(
        _.exists(
          _.startsWith(
            "S1 is listed as applied, but [[zz2024_iso]] and [[knapp2016_tin]] are not cited in one paragraph"
          )
        )
      ),
      back
    )
    val (kept, ws) = fix(m, apart, last = LastRound(true)).toOption.get
    assertEquals(
      (kept.applied, kept.notFixed),
      (
        Nil,
        List(
          WontFix(com.histmersion.domain.ReviewArticle.WontFix.Id("S1"), com.histmersion.domain.ReviewArticle.WontFix.Reason("listed as applied, but [[zz2024_iso]] and [[knapp2016_tin]] are not cited in one paragraph"))
        )
      )
    )
    assert(ws.exists(_.contains("kept despite")), ws)
    // the ledger: not claimed, not fixed once more
    val l = absorb(Ledger(com.histmersion.domain.ReviewLedger.Ledger.Slug("tin")), m.findings, com.histmersion.domain.automation.Run.Id("r"), com.histmersion.domain.ReviewLedger.Closure.Round(1))._1
    val after = applyFix(l, kept, com.histmersion.domain.ReviewLedger.ApplyFixRun("r"), com.histmersion.domain.ReviewLedger.ApplyFixRound(1))
    assertEquals(
      after
        .entry(com.histmersion.domain.ReviewArticle.Finding.Id("S1"))
        .map(e => (e.claimed, e.notFixedCount, e.notFixed.exists(_.value.contains("pass 1: listed as applied")))),
      Some((false, 1, true))
    )
    // beside the claim, it stands
    val beside = FixerAnswer(
      List(
        edit(
          Article.Edits.Op.Wire("replace"),
          Article.Edits.EditAnswer.Anchor("Tin reached the Aegean by sea [[knapp2016_tin]]."),
          Article.Edits.EditAnswer.Text("Tin reached the Aegean by sea [[knapp2016_tin]]; an isotope study disagrees [[zz2024_iso]].")
        )
      ),
      List(com.histmersion.domain.ReviewArticle.Finding.Id("S1")),
      Nil,
      Nil,
      com.histmersion.domain.ReviewArticle.FixerAnswer.Notes("")
    )
    assertEquals(fix(m, beside, last = LastRound(false)).map(_._1.applied), Right(List("S1")))

  test(
    "fixer: the pairing repair answers a named partner itself — the finding is 'repaired' and claimed for its re-check"
  ):
    val m = Merged(
      com.histmersion.domain.ReviewArticle.Merged.Round(1),
      Verdict.Major,
      List(mcFinding(Finding.Id("S1"), CritiqueRef.Wire("zz2015_kull>zz2011_west"), Finding.Quote("Kullmann answers West's reading of the cargo"))),
      None,
      Nil,
      Nil
    )
    // the fixer leaves it alone (it set nothing aside, applied nothing): the repair cites West where the paragraph names him
    val nothing = FixerAnswer(Nil, Nil, Nil, Nil, com.histmersion.domain.ReviewArticle.FixerAnswer.Notes(""))
    val (f, _) = fix(m, nothing, last = LastRound(false)).toOption.get
    assert(f.body.value.contains("Kullmann answers West's reading of the cargo [[zz2015_kull]] [[zz2011_west]]."), f.body)
    assertEquals((f.repaired, f.notFixed), (List("S1"), Nil))
    assert(f.repairs.head.value.contains("cite [[zz2011_west]] there (fixable)"), f.repairs)
    val l = applyFix(absorb(Ledger(com.histmersion.domain.ReviewLedger.Ledger.Slug("tin")), m.findings, com.histmersion.domain.automation.Run.Id("r"), com.histmersion.domain.ReviewLedger.Closure.Round(1))._1, f, com.histmersion.domain.ReviewLedger.ApplyFixRun("r"), com.histmersion.domain.ReviewLedger.ApplyFixRound(1))
    assertEquals(l.entry(com.histmersion.domain.ReviewArticle.Finding.Id("S1")).map(_.claimed), Some(true))

  test(
    "fixer: a citation the revision brings in whose recorded critique no paragraph discusses goes back; a pair-only set-aside answers it"
  ):
    val m = Merged(
      com.histmersion.domain.ReviewArticle.Merged.Round(1),
      Verdict.Minor,
      List(Finding(com.histmersion.domain.ReviewArticle.Finding.Id("R1"), Reviewer.Rules, Severity.Minor, com.histmersion.domain.ReviewArticle.Finding.Kind("term"), com.histmersion.domain.ReviewArticle.Finding.Where("Intro"), com.histmersion.domain.ReviewArticle.Finding.Quote(""), com.histmersion.domain.ReviewArticle.Finding.Problem("bronze undefined"), com.histmersion.domain.ReviewArticle.Finding.Fix("define"))),
      None,
      Nil,
      Nil
    )
    val adds = List(
      edit(
        Article.Edits.Op.Wire("insertAfter"),
        Article.Edits.EditAnswer.Anchor("Where the tin was mined is open [[knapp2016_tin]]."),
        Article.Edits.EditAnswer.Text(" A newer study proposes Cornwall [[zz2020_new]].")
      )
    )
    val bare = FixerAnswer(adds, List(com.histmersion.domain.ReviewArticle.Finding.Id("R1")), Nil, Nil, com.histmersion.domain.ReviewArticle.FixerAnswer.Notes(""))
    val back = fix(m, bare, last = LastRound(false))
    assert(
      back.left.exists(
        _.exists(
          _.contains(
            "your edits cite [[zz2020_new]], whose recorded critiques no paragraph discusses: zz2021_crit>zz2020_new (challenges)"
          )
        )
      ),
      back
    )
    val aside = bare.modify(_.setAside).setTo(List(
        SetAsideAnswer(
          com.histmersion.domain.ReviewArticle.SetAsideAnswer.Id(""),
          Some(com.histmersion.api.research.CritiqueRef.Wire("zz2021_crit>zz2020_new")),
          com.histmersion.domain.ReviewArticle.SetAsideAnswer.Reason("The critique concerns its dating, not the ore source cited here.")
        )
      )
    )
    val (f, ws) = fix(m, aside, last = LastRound(false)).toOption.get
    assertEquals((f.pairsAside.map(_.ref.wire), ws), (List("zz2021_crit>zz2020_new"), Nil))
    // on the last attempt it is kept, with the warning
    assert(
      fix(m, bare, last = LastRound(true)).toOption.get._2.exists(_.contains("whose recorded critiques no paragraph discusses"))
    )

  test("fixer: edits that reopen a closed finding go back; on the last attempt they are dropped and the rest is kept"):
    val closed = Entry(
      Finding(
        com.histmersion.domain.ReviewArticle.Finding.Id("R1"),
        Reviewer.Rules,
        Severity.Major,
        com.histmersion.domain.ReviewArticle.Finding.Kind("date"),
        com.histmersion.domain.ReviewArticle.Finding.Where("Disagreements"),
        com.histmersion.domain.ReviewArticle.Finding.Quote("The date of the wreck is argued"),
        com.histmersion.domain.ReviewArticle.Finding.Problem("no date"),
        com.histmersion.domain.ReviewArticle.Finding.Fix("give the year")
      ),
      com.histmersion.domain.ReviewLedger.Entry.Run("r"),
      com.histmersion.domain.ReviewLedger.Entry.Round(1),
      Status.Fixed
    )
    val l = ledgerWith(closed)
    val m = Merged(
      com.histmersion.domain.ReviewArticle.Merged.Round(2),
      Verdict.Minor,
      List(Finding(com.histmersion.domain.ReviewArticle.Finding.Id("C1"), Reviewer.ColdReader, Severity.Minor, com.histmersion.domain.ReviewArticle.Finding.Kind("term"), com.histmersion.domain.ReviewArticle.Finding.Where("Intro"), com.histmersion.domain.ReviewArticle.Finding.Quote(""), com.histmersion.domain.ReviewArticle.Finding.Problem("tin undefined"), com.histmersion.domain.ReviewArticle.Finding.Fix("define"))),
      None,
      Nil,
      Nil
    )
    val gloss =
      edit(
        Article.Edits.Op.Wire("replace"),
        Article.Edits.EditAnswer.Anchor("Tin reached the Aegean by sea"),
        Article.Edits.EditAnswer.Text("Tin, the scarce half of bronze, reached the Aegean by sea")
      )
    val undo = edit(
      Article.Edits.Op.Wire("replace"),
      Article.Edits.EditAnswer.Anchor("The date of the wreck is about 1320 BCE"),
      Article.Edits.EditAnswer.Text("The date of the wreck is argued")
    )
    val a = FixerAnswer(List(gloss, undo), List(com.histmersion.domain.ReviewArticle.Finding.Id("C1")), Nil, Nil, com.histmersion.domain.ReviewArticle.FixerAnswer.Notes(""))
    val back = checkFix(
      a,
      m,
      com.histmersion.domain.research.Slug("tin"),
      body,
      com.histmersion.domain.ReviewArticle.Snapshot.Kind("concept"),
      corpus,
      isKey,
      _ => None,
      minRatio,
      last = com.histmersion.domain.ReviewArticle.LastRound(false),
      None,
      pairsOf(body),
      Some(guards(l))
    )
    assert(
      back.left.exists(
        _.exists(_.startsWith("your edits reopen a closed finding — R1 (closed): the text it fixed is back"))
      ),
      back
    )
    val (f, ws) = checkFix(
      a,
      m,
      com.histmersion.domain.research.Slug("tin"),
      body,
      com.histmersion.domain.ReviewArticle.Snapshot.Kind("concept"),
      corpus,
      isKey,
      _ => None,
      minRatio,
      last = com.histmersion.domain.ReviewArticle.LastRound(true),
      None,
      pairsOf(body),
      Some(guards(l))
    ).toOption.get
    assert(f.body.value.contains("Tin, the scarce half of bronze") && f.body.value.contains("about 1320 BCE"), f.body)
    assert(ws.exists(_.contains("edit(s) 2 dropped: they reopened R1")), ws)

  test("fixer: every open finding it leaves unanswered is recorded not fixed, with why — never silently skipped"):
    val m = Merged(
      com.histmersion.domain.ReviewArticle.Merged.Round(1),
      Verdict.Major,
      List(
        Finding(
          com.histmersion.domain.ReviewArticle.Finding.Id("R1"),
          Reviewer.Rules,
          Severity.Major,
          com.histmersion.domain.ReviewArticle.Finding.Kind("date"),
          com.histmersion.domain.ReviewArticle.Finding.Where("Disagreements"),
          com.histmersion.domain.ReviewArticle.Finding.Quote("The date of the wreck"),
          com.histmersion.domain.ReviewArticle.Finding.Problem("no date"),
          com.histmersion.domain.ReviewArticle.Finding.Fix("give the year")
        ),
        Finding(com.histmersion.domain.ReviewArticle.Finding.Id("C1"), Reviewer.ColdReader, Severity.Minor, com.histmersion.domain.ReviewArticle.Finding.Kind("term"), com.histmersion.domain.ReviewArticle.Finding.Where("Intro"), com.histmersion.domain.ReviewArticle.Finding.Quote(""), com.histmersion.domain.ReviewArticle.Finding.Problem("tin undefined"), com.histmersion.domain.ReviewArticle.Finding.Fix("define")),
        Finding(com.histmersion.domain.ReviewArticle.Finding.Id("C2"), Reviewer.ColdReader, Severity.Minor, com.histmersion.domain.ReviewArticle.Finding.Kind("term"), com.histmersion.domain.ReviewArticle.Finding.Where("Intro"), com.histmersion.domain.ReviewArticle.Finding.Quote(""), com.histmersion.domain.ReviewArticle.Finding.Problem("bronze undefined"), com.histmersion.domain.ReviewArticle.Finding.Fix("define"))
      ),
      None,
      Nil,
      Nil
    )
    val a = FixerAnswer(List(edit(Article.Edits.Op.Wire("replace"), Article.Edits.EditAnswer.Anchor("Tin reached"), Article.Edits.EditAnswer.Text("Tin, a metal, reached"))), List(com.histmersion.domain.ReviewArticle.Finding.Id("C1")), Nil, Nil, com.histmersion.domain.ReviewArticle.FixerAnswer.Notes(""))
    assert(fix(m, a, last = LastRound(false)).left.exists(_.contains("major finding R1 is neither applied nor rejected")))
    val (f, _) = fix(m, a, last = LastRound(true)).toOption.get
    assertEquals(
      f.notFixed,
      List(WontFix(com.histmersion.domain.ReviewArticle.WontFix.Id("R1"), com.histmersion.domain.ReviewArticle.WontFix.Reason("the fixer neither applied nor rejected it")), WontFix(com.histmersion.domain.ReviewArticle.WontFix.Id("C2"), com.histmersion.domain.ReviewArticle.WontFix.Reason("the fixer did not answer it")))
    )
    val l = applyFix(absorb(Ledger(com.histmersion.domain.ReviewLedger.Ledger.Slug("tin")), m.findings, com.histmersion.domain.automation.Run.Id("r"), com.histmersion.domain.ReviewLedger.Closure.Round(1))._1, f, com.histmersion.domain.ReviewLedger.ApplyFixRun("r"), com.histmersion.domain.ReviewLedger.ApplyFixRound(1))
    assertEquals(
      l.entries.map(e => (e.id, e.claimed, e.notFixedCount)),
      List(("R1", false, 1), ("C1", true, 0), ("C2", false, 1))
    )
    assert(notes(l)(com.histmersion.domain.ReviewArticle.Finding.Id("R1")).contains("pass 1: the fixer neither applied nor rejected it"), notes(l))
    // twice not fixed: stubborn, and the note says how often
    val twice = applyFix(l, f, com.histmersion.domain.ReviewLedger.ApplyFixRun("r"), com.histmersion.domain.ReviewLedger.ApplyFixRound(2))
    assertEquals(
      stubborn(
        twice.modify(_.entries).setTo(twice.entries.map(e => e.modify(_.finding).setTo(e.finding.modify(_.pair).setTo(Some(CritiqueRef(com.histmersion.api.library.Citekey("a2000_x"), com.histmersion.api.library.Citekey("b2001_y"))))))
        )
      ).map(_.id),
      List("R1", "C2")
    )
    assert(notes(twice)(com.histmersion.domain.ReviewArticle.Finding.Id("R1")).startsWith("not fixed yet (2 times)"), notes(twice))
