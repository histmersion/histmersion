package com.histmersion
package api

import com.histmersion.domain.research.workflows.CritiqueRule
import com.histmersion.domain.research.workflows.Ledger
import com.histmersion.domain.research.workflows.Finding
import com.histmersion.domain.research.workflows.Status
import com.histmersion.domain.research.workflows.Entry
import com.histmersion.domain.research.workflows.Closure
import com.histmersion.domain.research.workflows.Severity
import com.histmersion.domain.research.workflows.Verdict
import com.histmersion.domain.research.workflows.Reviewer
import com.histmersion.domain.research.workflows.Check
import com.histmersion.domain.research.workflows.BodyCorpus
import com.histmersion.api.research.critiquepairing.Work
import com.histmersion.domain.research.workflows.FixGuards
import com.histmersion.domain.research.workflows.Merged
import com.histmersion.domain.research.workflows.FixerAnswer
import com.histmersion.domain.research.workflows.Snapshot
import com.histmersion.domain.research.workflows.WontFix
import com.histmersion.domain.research.workflows.SetAsideAnswer
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.RawJson
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Text.sentence

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.config.given
import com.histmersion.domain.given

import com.histmersion.domain.ReviewArticle.*
import com.histmersion.domain.ReviewLedger.*
import com.histmersion.api.research.{CritiquePair, CritiquePairing, CritiqueRef, CritiqueSide, CritiqueState}
import com.histmersion.api.research.Article
import com.histmersion.api.research.Coverage
import com.histmersion.domain.ReviewArticle
import com.histmersion.domain.automation.Run
import com.histmersion.domain.ReviewLedger
import com.histmersion.api.library.Citekey
import com.histmersion.domain.research.Slug
import com.histmersion.api.catalogue.Year
import com.histmersion.lint.{tolerated, LintRule}

/** Convergence of the review (docs/research-review-convergence.md §6.9), pure: the final pass that defers what it finds
  * new, the regression guard, a "fixed" check held to the checklist, the fixer's guards (the pairing repair, a claimed
  * pair still unanswered, pairs a revision brings in, edits that reopen a closed finding) and the not-fixed record —
  * with the live ledgers of 2026-10-19 as fixtures (`review/ledger-*.json`: the entries of three of the eight articles
  * that ended MAJOR).
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class ReviewConvergenceSuite extends munit.FunSuite:

  private val minRatio = CritiqueRule.MinRatio(0.9)

  private def live(slug: String): Ledger =
    RawJson
      .decode[Ledger](scala.io.Source.fromResource(s"review/ledger-$slug.json")(using scala.io.Codec.UTF8).mkString)
      .fold(e => fail(s"$slug: $e"), identity)

  test("live ledgers decode — entries written before §6.9 have no not-fixed count and no deferral"):
    val a = live("achilles_origins")
    assertEquals(a.entries.map(_.id).sorted, List("C12", "R8", "S3", "S4", "S5", "S6"))
    assert(a.entries.forall(e => e.notFixedCount.value == 0 && e.deferred.isEmpty))
    assertEquals(
      a.entry(Finding.Id("S6")).flatMap(_.finding.pair).map(_.wire),
      Some("kelly2021_gods>bachvarova2016_hittite")
    )
    // and round-trips with the new fields
    assertEquals(RawJson.decode[Ledger](a.asJson.noSpaces).map(_.entries.size), Right(6))
    assertEquals(live("gods_and_migrations").entries.count(_.reopened.value > 0), 3)

  // ── the final pass defers what it finds new ────────────────────────────────────────────────────

  test(
    "deferFinal: a MAJOR new in the final pass is deferred; a reopened one, a regression and an earlier open one stand"
  ):
    // the live gods_and_migrations entries: S24/S31/S7 were fixed in pass 2 and raised again in pass 3
    // (reopened), S35–S37 raised for the first time by the final pass
    val g = live("gods_and_migrations")
    val before = g
      .modify(_.entries)
      .setTo(
        g.entries
          .filterNot(e => Set("S35", "S36", "S37")(e.id.value))
          .map(e =>
            if e.reopened.value > 0 then
              e.modify(_.status)
                .setTo(Status.Fixed)
                .modify(_.reopened)
                .setTo(Entry.Reopened(e.reopened.value - 1))
                .modify(_.notFixed)
                .setTo(None)
            else e
          )
      )
    val after = g // the final pass absorbed: S24/S31/S7 reopened, S35–S37 new
    val regression = (f: Finding) => f.id.value == "S37"
    val (d, ids) = before.deferFinal(after, Run.Id("6b1be16f-dd42-3eaf-e8d4-b3ef493b567b"), regression)
    assertEquals(ids.sorted, List("S35", "S36"))
    assertEquals(
      d.entries.filter(_.deferred.contains(Run.Id("6b1be16f-dd42-3eaf-e8d4-b3ef493b567b"))).map(_.id).sorted,
      List("S35", "S36")
    )
    // the run's verdict counts them as minor; the next run sees them as open MAJORs
    val m = d.merged(Closure.Round(3), run = Some(Run.Id("6b1be16f-dd42-3eaf-e8d4-b3ef493b567b")))
    assertEquals(m.deferred.sorted, List("S35", "S36"))
    assert(!m.majors.exists(f => Set("S35", "S36")(f.id.value)))
    assertEquals(m.majors.map(_.id).sorted, List("S24", "S31", "S37", "S7"))
    assertEquals(
      d.merged(Closure.Round(1), run = Some(Run.Id("cc1deb80-c51f-5da7-a8a6-4eeb589e61b3"))).majors.size,
      6
    )
    assertEquals(d.openMajors.size, 6, "still open MAJOR in the ledger: the next run's work")
    // an open minor the final pass raised to MAJOR is new too
    val minorBefore = before
      .modify(_.entries)
      .setTo(
        before.entries :+ after
          .entry(Finding.Id("S35"))
          .get
          .copy(finding = after.entry(Finding.Id("S35")).get.finding.copy(severity = Severity.Minor))
      )
    assertEquals(
      minorBefore.deferFinal(after, Run.Id("6b1be16f-dd42-3eaf-e8d4-b3ef493b567b"), _ => false)._2.sorted,
      List("S35", "S36", "S37")
    )

  test("a run whose only open MAJORs are deferred ends below MAJOR; its backlog item says they are deferred"):
    val g = live("gods_and_migrations")
    val only = g.modify(_.entries).setTo(g.entries.filter(e => Set("S35", "S36")(e.id.value)))
    val (d, _) = Ledger(Ledger.Slug("gods_and_migrations"))
      .deferFinal(only, Run.Id("6b1be16f-dd42-3eaf-e8d4-b3ef493b567b"), _ => false)
    assertEquals(
      d.merged(Closure.Round(3), run = Some(Run.Id("6b1be16f-dd42-3eaf-e8d4-b3ef493b567b"))).verdict,
      Verdict.Minor
    )
    val task = d.taskText(Run.Id("6b1be16f-dd42-3eaf-e8d4-b3ef493b567b")).get
    assert(task.value.startsWith("review-article [[gods_and_migrations]]: 2 open MAJORs — "), task)
    assert(task.value.contains("[new in the final pass: deferred to the next run]"), task)
    assert(
      !d.taskText(Run.Id("cc1deb80-c51f-5da7-a8a6-4eeb589e61b3")).get.value.contains("deferred"),
      "a later run's item does not repeat it"
    )

  test("finalRegression: a pair the run had answered and broke; a factual error in the text the run wrote"):
    def p(state: CritiqueState) = CritiquePair(
      CritiqueSide(Citekey("chang2015"), cited = CritiqueSide.Cited.Cited),
      List(CritiquePair.Kinds("challenges")),
      CritiqueSide(Citekey("gray2003_language"), cited = CritiqueSide.Cited.Cited),
      state
    )
    val f = Finding(
      Finding.Id("S24"),
      Reviewer.Skeptic,
      Severity.Major,
      Finding.Kind("missing-critique"),
      Finding.Where("w"),
      Finding.Quote("Gray and Atkinson (2003) dated the first split"),
      Finding.Problem("p"),
      Finding.Fix("x"),
      pair = Some(CritiqueRef(Citekey("chang2015"), Citekey("gray2003_language")))
    )
    assert(
      f.finalRegression(
        List(p(CritiqueState.Addressed)),
        List(p(CritiqueState.Open)),
        domain.research.workflows.Fix.Body(""),
        minRatio
      )
    )
    assert(
      !f.finalRegression(
        List(p(CritiqueState.Open)),
        List(p(CritiqueState.Open)),
        domain.research.workflows.Fix.Body(""),
        minRatio
      ),
      "open from the start: a discovery"
    )
    val fact = Finding(
      Finding.Id("S9"),
      Reviewer.Skeptic,
      Severity.Major,
      Finding.Kind("date"),
      Finding.Where("w"),
      Finding.Quote("dated to 1320 BCE by the cargo"),
      Finding.Problem("p"),
      Finding.Fix("x")
    )
    assert(
      fact.finalRegression(
        Nil,
        Nil,
        domain.research.workflows.Fix.Body("The wreck was dated to 1320 BCE by the cargo."),
        minRatio
      )
    )
    assert(
      !fact.finalRegression(
        Nil,
        Nil,
        domain.research.workflows.Fix.Body("Another passage altogether, edited."),
        minRatio
      )
    )
    assert(
      !fact
        .modify(_.kind)
        .setTo(Finding.Kind("lost"))
        .finalRegression(
          Nil,
          Nil,
          domain.research.workflows.Fix.Body("The wreck was dated to 1320 BCE by the cargo."),
          minRatio
        ),
      "a reader's confusion in edited text is a discovery, not a factual regression"
    )

  // ── a "fixed" check held to the checklist ──────────────────────────────────────────────────────

  test("holdPairChecks: the live S6 checked fixed while its pair is still open is recorded NOT fixed, with why"):
    val a = live("achilles_origins")
    val open = CritiquePair(
      CritiqueSide(Citekey("kelly2021_gods"), cited = CritiqueSide.Cited.Cited),
      List(CritiquePair.Kinds("challenges")),
      CritiqueSide(Citekey("bachvarova2016_hittite"), cited = CritiqueSide.Cited.Cited),
      CritiqueState.Open
    )
    val checks = List(
      Check(
        Check.Id("S6"),
        fixed = Check.Fixed(true),
        Check.Quote("Gilan nevertheless identifies"),
        Check.Note("Gilan's criticism is now described")
      ),
      Check(
        Check.Id("S4"),
        fixed = Check.Fixed(true),
        Check.Quote("q"),
        Check.Note("n")
      )
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
    val l = a.applyChecks(
      Reviewer.Skeptic,
      held,
      ReviewLedger.ApplyChecksRun("6b1be16f-dd42-3eaf-e8d4-b3ef493b567b"),
      ReviewLedger.ApplyChecksRound(2)
    )
    assertEquals(l.entry(Finding.Id("S6")).map(e => (e.status, e.notFixedCount)), Some((Status.Open, 1)))
    assertEquals(l.entry(Finding.Id("S4")).map(_.status), Some(Status.Fixed))

  // ── the regression guard ───────────────────────────────────────────────────────────────────────

  private val tinBefore = domain.research.workflows.Fix.Body(
    """Tin reached the Aegean by sea [[knapp2016_tin]]; [[zz2024_iso]] traces it to Central Asia.
      |
      |The date of the wreck is about 1320 BCE [[knapp2016_tin]].
      |""".stripMargin
  )

  private def ledgerWith(es: Entry*) = Ledger(Ledger.Slug("tin"), es.toList)
  private val pairFixed = Entry(
    Finding(
      Finding.Id("S1"),
      Reviewer.Skeptic,
      Severity.Major,
      Finding.Kind("missing-critique"),
      Finding.Where("Intro"),
      Finding.Quote("Tin reached the Aegean by sea"),
      Finding.Problem("omits the isotope critique"),
      Finding.Fix("cite it"),
      pair = Some(CritiqueRef(Citekey("zz2024_iso"), Citekey("knapp2016_tin")))
    ),
    Entry.Run("4b43b0ae-e356-24cd-95b9-10189b3dc231"),
    Entry.Round(1),
    Status.Fixed,
    closed = Some(
      Closure(
        Closure.By("skeptic"),
        Closure.Round(2),
        Closure.Run("4b43b0ae-e356-24cd-95b9-10189b3dc231"),
        Closure.Note(""),
        Closure.Quote("[[zz2024_iso]] traces it to Central Asia")
      )
    )
  )
  private val textFixed = Entry(
    Finding(
      Finding.Id("R1"),
      Reviewer.Rules,
      Severity.Major,
      Finding.Kind("date"),
      Finding.Where("Disagreements"),
      Finding.Quote("The date of the wreck is argued"),
      Finding.Problem("no date"),
      Finding.Fix("give the year")
    ),
    Entry.Run("4b43b0ae-e356-24cd-95b9-10189b3dc231"),
    Entry.Round(1),
    Status.Fixed,
    closed = Some(
      Closure(
        Closure.By("rules"),
        Closure.Round(2),
        Closure.Run("4b43b0ae-e356-24cd-95b9-10189b3dc231"),
        Closure.Note(""),
        Closure.Quote("The date of the wreck is about 1320 BCE [[knapp2016_tin]]")
      )
    )
  )
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def together(b: domain.research.workflows.Fix.Body, r: CritiqueRef) =
    CritiquePair.together(b, r, t => Some(Citekey(t.value)))
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def cites(b: domain.research.workflows.Fix.Body, ck: domain.library.Citekey) =
    AgentVerify.wikilinks(AgentVerify.Text(b.value)).exists(_.value == ck.value)

  test("guard: a closed pair split apart, a fixed text written back, a citation the fix rests on dropped"):
    val l = ledgerWith(pairFixed, textFixed)
    assertEquals(l.guard(tinBefore, tinBefore, together, cites, minRatio), Nil)
    val split = domain.research.workflows.Fix.Body(
      tinBefore.value
        .replace("; [[zz2024_iso]] traces it to Central Asia.", ".\n\nIt came from Central Asia [[zz2024_iso]].")
    )
    assertEquals(l.guard(tinBefore, split, together, cites, minRatio).map(_.id), List("S1"))
    assert(
      s"${l.guard(tinBefore, split, together, cites, minRatio).head.why}".contains("no longer cited in one paragraph")
    )
    val back = domain.research.workflows.Fix.Body(
      tinBefore.value.replace("The date of the wreck is about 1320 BCE", "The date of the wreck is argued")
    )
    assertEquals(
      l.guard(tinBefore, back, together, cites, minRatio).map(r => (r.id, s"${r.why}".take(24))),
      List(("R1", "the text it fixed is bac"))
    )
    val dropped = domain.research.workflows.Fix.Body(
      tinBefore.value
        .replace(" [[zz2024_iso]] traces it to Central Asia", " an isotope study traces it to Central Asia")
    )
    val g = l.guard(tinBefore, dropped, together, cites, minRatio)
    assertEquals(g.map(_.id), List("S1"))
    // open and set-aside findings are not guarded
    assertEquals(
      ledgerWith(pairFixed.modify(_.status).setTo(Status.Open)).guard(tinBefore, split, together, cites, minRatio),
      Nil
    )

  // ── the fixer's guards ─────────────────────────────────────────────────────────────────────────

  private val body = domain.research.workflows.Fix.Body(
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
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val isKey = (ck: domain.library.Citekey) => keys(ck.value)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val isTarget = (s: Slug) => keys(s.value)
  private val corpus = BodyCorpus(isKey, isTarget, _ => Nil)
  private val edges = List(
    Coverage.Edge(Citekey("zz2024_iso"), Coverage.Edge.Kind("refutes"), Citekey("knapp2016_tin")),
    Coverage.Edge(Citekey("zz2015_kull"), Coverage.Edge.Kind("challenges"), Citekey("zz2011_west")),
    Coverage.Edge(Citekey("zz2021_crit"), Coverage.Edge.Kind("challenges"), Citekey("zz2020_new"))
  )
  private val works: Citekey => Option[Work] = Map(
    Citekey("zz2011_west") -> Work(
      Citekey("zz2011_west"),
      List(Work.Names("West")),
      Some(Year(2011))
    ),
    Citekey("zz2015_kull") -> Work(
      Citekey("zz2015_kull"),
      List(Work.Names("Kullmann")),
      Some(Year(2015))
    )
  ).get
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val resolve = (t: Article.Link.Target) => Some(t.value).filter(keys).map(Citekey(_))
  private def pairsOf(b: domain.research.workflows.Fix.Body) = CritiquePair.build(b, resolve, edges, Nil)
  private def guards(l: Ledger) = FixGuards(
    pairsOf,
    (b0, b1) => l.guard(b0, b1, (b, r) => CritiquePair.find(pairsOf(b), r).exists(!_.open), cites, minRatio),
    b => CritiquePairing.repair(b, CritiquePairing.hits(b, resolve, edges, works))
  )

  private def mcFinding(
      id: Finding.Id,
      pair: CritiqueRef.Wire,
      quote: Finding.Quote = Finding.Quote("Tin reached the Aegean by sea")
  ) =
    Finding(
      id,
      Reviewer.Skeptic,
      Severity.Major,
      Finding.Kind("missing-critique"),
      Finding.Where("Intro"),
      quote,
      Finding.Problem("omits a critique"),
      Finding.Fix("cite the critic"),
      pair = CritiqueRef.parse(pair).toOption
    )
  private def edit(
      op: Article.Edits.Op.Wire,
      anchor: Article.Edits.EditAnswer.Anchor,
      text: Article.Edits.EditAnswer.Text
  ) =
    Article.Edits.EditAnswer(Article.Edits.Op.parse(op).get, anchor, Some(text))
  private def fix(m: Merged, a: FixerAnswer, last: LastRound, l: Ledger = Ledger(Ledger.Slug("tin"))) =
    a.checkFix(
      m,
      Slug("tin"),
      body,
      Snapshot.Kind("concept"),
      corpus,
      isKey,
      _ => None,
      minRatio,
      last,
      None,
      pairsOf(body),
      Some(guards(l))
    )

  test(
    "fixer: a missing critique listed as applied with its pair still apart goes back; on the last attempt it is not fixed, with why"
  ):
    val m = Merged(
      Merged.Round(1),
      Verdict.Major,
      List(mcFinding(Finding.Id("S1"), CritiqueRef.Wire("zz2024_iso>knapp2016_tin"))),
      None,
      Nil,
      Nil
    )
    // the fixer describes the isotope study in a new paragraph, apart from the claim: the pair stays open
    val apart = FixerAnswer(
      List(
        edit(
          Article.Edits.Op.Wire("insertAfter"),
          Article.Edits.EditAnswer.Anchor("Tin reached the Aegean by sea [[knapp2016_tin]]."),
          Article.Edits.EditAnswer.Text("\n\nAn isotope study disagrees [[zz2024_iso]].")
        )
      ),
      List(Finding.Id("S1")),
      Nil,
      Nil,
      FixerAnswer.Notes("")
    )
    val back = fix(m, apart, last = LastRound(false))
    assert(
      back.left.exists(
        _.exists(
          _.sentence.startsWith(
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
          WontFix(
            WontFix.Id("S1"),
            ReviewArticle.Rationale(
              "listed as applied, but [[zz2024_iso]] and [[knapp2016_tin]] are not cited in one paragraph"
            )
          )
        )
      )
    )
    assert(ws.exists(_.contains("kept despite")), ws)
    // the ledger: not claimed, not fixed once more
    val l =
      Ledger(Ledger.Slug("tin")).absorb(m.findings, Run.Id("4b43b0ae-e356-24cd-95b9-10189b3dc231"), Closure.Round(1))._1
    val after =
      l.applyFix(kept, ReviewLedger.ApplyFixRun("4b43b0ae-e356-24cd-95b9-10189b3dc231"), ReviewLedger.ApplyFixRound(1))
    assertEquals(
      after
        .entry(Finding.Id("S1"))
        .map(e => (e.claimed, e.notFixedCount, e.notFixed.exists(_.value.contains("pass 1: listed as applied")))),
      Some((false, 1, true))
    )
    // beside the claim, it stands
    val beside = FixerAnswer(
      List(
        edit(
          Article.Edits.Op.Wire("replace"),
          Article.Edits.EditAnswer.Anchor("Tin reached the Aegean by sea [[knapp2016_tin]]."),
          Article.Edits.EditAnswer
            .Text("Tin reached the Aegean by sea [[knapp2016_tin]]; an isotope study disagrees [[zz2024_iso]].")
        )
      ),
      List(Finding.Id("S1")),
      Nil,
      Nil,
      FixerAnswer.Notes("")
    )
    assertEquals(fix(m, beside, last = LastRound(false)).map(_._1.applied), Right(List("S1")))

  test(
    "fixer: the pairing repair answers a named partner itself — the finding is 'repaired' and claimed for its re-check"
  ):
    val m = Merged(
      Merged.Round(1),
      Verdict.Major,
      List(
        mcFinding(
          Finding.Id("S1"),
          CritiqueRef.Wire("zz2015_kull>zz2011_west"),
          Finding.Quote("Kullmann answers West's reading of the cargo")
        )
      ),
      None,
      Nil,
      Nil
    )
    // the fixer leaves it alone (it set nothing aside, applied nothing): the repair cites West where the paragraph names him
    val nothing = FixerAnswer(Nil, Nil, Nil, Nil, FixerAnswer.Notes(""))
    val (f, _) = fix(m, nothing, last = LastRound(false)).toOption.get
    assert(
      f.body.value.contains("Kullmann answers West's reading of the cargo [[zz2015_kull]] [[zz2011_west]]."),
      f.body
    )
    assertEquals((f.repaired, f.notFixed), (List("S1"), Nil))
    assert(f.repairs.head.value.contains("cite [[zz2011_west]] there (fixable)"), f.repairs)
    val l = Ledger(Ledger.Slug("tin"))
      .absorb(m.findings, Run.Id("4b43b0ae-e356-24cd-95b9-10189b3dc231"), Closure.Round(1))
      ._1
      .applyFix(f, ReviewLedger.ApplyFixRun("4b43b0ae-e356-24cd-95b9-10189b3dc231"), ReviewLedger.ApplyFixRound(1))
    assertEquals(l.entry(Finding.Id("S1")).map(_.claimed), Some(true))

  test(
    "fixer: a citation the revision brings in whose recorded critique no paragraph discusses goes back; a pair-only set-aside answers it"
  ):
    val m = Merged(
      Merged.Round(1),
      Verdict.Minor,
      List(
        Finding(
          Finding.Id("R1"),
          Reviewer.Rules,
          Severity.Minor,
          Finding.Kind("term"),
          Finding.Where("Intro"),
          Finding.Quote(""),
          Finding.Problem("bronze undefined"),
          Finding.Fix("define")
        )
      ),
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
    val bare = FixerAnswer(adds, List(Finding.Id("R1")), Nil, Nil, FixerAnswer.Notes(""))
    val back = fix(m, bare, last = LastRound(false))
    assert(
      back.left.exists(
        _.exists(
          _.sentence.contains(
            "your edits cite [[zz2020_new]], whose recorded critiques no paragraph discusses: zz2021_crit>zz2020_new (challenges)"
          )
        )
      ),
      back
    )
    val aside = bare
      .modify(_.setAside)
      .setTo(
        List(
          SetAsideAnswer(
            SetAsideAnswer.Id(""),
            Some(CritiqueRef.Wire("zz2021_crit>zz2020_new")),
            ReviewArticle.Rationale("The critique concerns its dating, not the ore source cited here.")
          )
        )
      )
    val (f, ws) = fix(m, aside, last = LastRound(false)).toOption.get
    assertEquals((f.pairsAside.map(_.ref.wire), ws), (List("zz2021_crit>zz2020_new"), Nil))
    // on the last attempt it is kept, with the warning
    assert(
      fix(m, bare, last = LastRound(true)).toOption.get._2
        .exists(_.contains("whose recorded critiques no paragraph discusses"))
    )

  test("fixer: edits that reopen a closed finding go back; on the last attempt they are dropped and the rest is kept"):
    val closed = Entry(
      Finding(
        Finding.Id("R1"),
        Reviewer.Rules,
        Severity.Major,
        Finding.Kind("date"),
        Finding.Where("Disagreements"),
        Finding.Quote("The date of the wreck is argued"),
        Finding.Problem("no date"),
        Finding.Fix("give the year")
      ),
      Entry.Run("4b43b0ae-e356-24cd-95b9-10189b3dc231"),
      Entry.Round(1),
      Status.Fixed
    )
    val l = ledgerWith(closed)
    val m = Merged(
      Merged.Round(2),
      Verdict.Minor,
      List(
        Finding(
          Finding.Id("C1"),
          Reviewer.ColdReader,
          Severity.Minor,
          Finding.Kind("term"),
          Finding.Where("Intro"),
          Finding.Quote(""),
          Finding.Problem("tin undefined"),
          Finding.Fix("define")
        )
      ),
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
    val a = FixerAnswer(
      List(gloss, undo),
      List(Finding.Id("C1")),
      Nil,
      Nil,
      FixerAnswer.Notes("")
    )
    val back = a.checkFix(
      m,
      Slug("tin"),
      body,
      Snapshot.Kind("concept"),
      corpus,
      isKey,
      _ => None,
      minRatio,
      last = ReviewArticle.LastRound(false),
      None,
      pairsOf(body),
      Some(guards(l))
    )
    assert(
      back.left.exists(
        _.exists(_.sentence.startsWith("your edits reopen a closed finding — R1 (closed): the text it fixed is back"))
      ),
      back
    )
    val (f, ws) = a
      .checkFix(
        m,
        Slug("tin"),
        body,
        Snapshot.Kind("concept"),
        corpus,
        isKey,
        _ => None,
        minRatio,
        last = ReviewArticle.LastRound(true),
        None,
        pairsOf(body),
        Some(guards(l))
      )
      .toOption
      .get
    assert(f.body.value.contains("Tin, the scarce half of bronze") && f.body.value.contains("about 1320 BCE"), f.body)
    assert(ws.exists(_.contains("edit(s) 2 dropped: they reopened R1")), ws)

  test("fixer: every open finding it leaves unanswered is recorded not fixed, with why — never silently skipped"):
    val m = Merged(
      Merged.Round(1),
      Verdict.Major,
      List(
        Finding(
          Finding.Id("R1"),
          Reviewer.Rules,
          Severity.Major,
          Finding.Kind("date"),
          Finding.Where("Disagreements"),
          Finding.Quote("The date of the wreck"),
          Finding.Problem("no date"),
          Finding.Fix("give the year")
        ),
        Finding(
          Finding.Id("C1"),
          Reviewer.ColdReader,
          Severity.Minor,
          Finding.Kind("term"),
          Finding.Where("Intro"),
          Finding.Quote(""),
          Finding.Problem("tin undefined"),
          Finding.Fix("define")
        ),
        Finding(
          Finding.Id("C2"),
          Reviewer.ColdReader,
          Severity.Minor,
          Finding.Kind("term"),
          Finding.Where("Intro"),
          Finding.Quote(""),
          Finding.Problem("bronze undefined"),
          Finding.Fix("define")
        )
      ),
      None,
      Nil,
      Nil
    )
    val a = FixerAnswer(
      List(
        edit(
          Article.Edits.Op.Wire("replace"),
          Article.Edits.EditAnswer.Anchor("Tin reached"),
          Article.Edits.EditAnswer.Text("Tin, a metal, reached")
        )
      ),
      List(Finding.Id("C1")),
      Nil,
      Nil,
      FixerAnswer.Notes("")
    )
    assert(
      fix(m, a, last = LastRound(false)).left
        .exists(_.map(_.sentence).contains("major finding R1 is neither applied nor rejected"))
    )
    val (f, _) = fix(m, a, last = LastRound(true)).toOption.get
    assertEquals(
      f.notFixed,
      List(
        WontFix(
          WontFix.Id("R1"),
          ReviewArticle.Rationale("the fixer neither applied nor rejected it")
        ),
        WontFix(WontFix.Id("C2"), ReviewArticle.Rationale("the fixer did not answer it"))
      )
    )
    val l = Ledger(Ledger.Slug("tin"))
      .absorb(m.findings, Run.Id("4b43b0ae-e356-24cd-95b9-10189b3dc231"), Closure.Round(1))
      ._1
      .applyFix(f, ReviewLedger.ApplyFixRun("4b43b0ae-e356-24cd-95b9-10189b3dc231"), ReviewLedger.ApplyFixRound(1))
    assertEquals(
      l.entries.map(e => (e.id, e.claimed, e.notFixedCount)),
      List(("R1", false, 1), ("C1", true, 0), ("C2", false, 1))
    )
    assert(
      l.notes(Finding.Id("R1")).contains("pass 1: the fixer neither applied nor rejected it"),
      l.notes
    )
    // twice not fixed: stubborn, and the note says how often
    val twice =
      l.applyFix(f, ReviewLedger.ApplyFixRun("4b43b0ae-e356-24cd-95b9-10189b3dc231"), ReviewLedger.ApplyFixRound(2))
    assertEquals(
      twice
        .modify(_.entries)
        .setTo(
          twice.entries.map(e =>
            e.modify(_.finding)
              .setTo(e.finding.modify(_.pair).setTo(Some(CritiqueRef(Citekey("a2000_x"), Citekey("b2001_y")))))
          )
        )
        .stubborn()
        .map(_.id),
      List("R1", "C2")
    )
    assert(twice.notes(Finding.Id("R1")).startsWith("not fixed yet (2 times)"), twice.notes)
