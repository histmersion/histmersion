package com.histmersion.domain

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import com.histmersion.domain.ReviewArticle.*
import com.histmersion.domain.ReviewArticle.Finding
import com.histmersion.domain.ReviewLedger.*
import io.circe.parser.decode
import io.circe.syntax.*

/** The review ledger's pure half: matching, every status transition, the lessons, what a prompt shows, the edited
  * passages and the one backlog line.
  */
class ReviewLedgerSuite extends munit.FunSuite:

  private def f(
      id: Finding.Id,
      r: Reviewer,
      sev: Severity,
      where: Finding.Where,
      problem: Finding.Problem,
      quote: Finding.Quote = Finding.Quote(""),
      kind: Finding.Kind = Finding.Kind("term")
  ) =
    Finding(id, r, sev, kind, where, quote, problem, com.histmersion.domain.ReviewArticle.Finding.Fix("fix it"))

  private val tinMajor =
    f(Finding.Id("R1"), Reviewer.Rules, Severity.Major, Finding.Where("Intro"), Finding.Problem("tin is not defined"), Finding.Quote("Tin reached the Aegean"))
  private val dateMajor =
    f(Finding.Id("S1"), Reviewer.Skeptic, Severity.Major, Finding.Where("Disagreements"), Finding.Problem("the date has no support"), Finding.Quote("The date is argued"), Finding.Kind("date"))
  private val readerMin =
    f(Finding.Id("C1"), Reviewer.ColdReader, Severity.Minor, Finding.Where("Open questions"), Finding.Problem("opaque sentence"), Finding.Quote("Where the tin was mined"))

  private def fresh: Ledger = absorb(Ledger(com.histmersion.domain.ReviewLedger.Ledger.Slug("tin")), List(tinMajor, dateMajor, readerMin), com.histmersion.domain.automation.Run.Id("b7f86a38-be91-c3c1-64ae-ba52940975e8"), com.histmersion.domain.ReviewLedger.Closure.Round(1))._1

  test("absorb: new findings get ledger ids per reviewer; the same one again keeps its id"):
    val l = fresh
    assertEquals(
      l.entries.map(e => (e.id, e.status, e.round)),
      List(("R1", Status.Open, 1), ("S1", Status.Open, 1), ("C1", Status.Open, 1))
    )
    // pass 2 numbers its findings from 1 again: the ledger maps them
    val again = f(Finding.Id("R1"), Reviewer.Rules, Severity.Major, Finding.Where("Intro"), Finding.Problem("tin is still not defined"), Finding.Quote("Tin reached the Aegean"))
    val other = f(Finding.Id("R2"), Reviewer.Rules, Severity.Minor, Finding.Where("Summary"), Finding.Problem("a filler phrase"), Finding.Quote("it is worth noting"))
    val (l2, ids) = absorb(l, List(other, again), com.histmersion.domain.automation.Run.Id("53df4857-3ea7-7141-2c78-e3a48bf0b437"), com.histmersion.domain.ReviewLedger.Closure.Round(2))
    assertEquals(ids.map(_.id), List("R2", "R1"))
    assertEquals(l2.entries.size, 4)
    assertEquals(l2.entry(com.histmersion.domain.ReviewArticle.Finding.Id("R1")).map(_.finding.problem), Some("tin is still not defined"))
    assertEquals(nextId(l2, Reviewer.Rules), "R3")
    assertEquals(nextId(l2, Reviewer.Mechanical), "M1")

  test("same: one reviewer only; the quoted passage, or the same where with a similar problem"):
    assert(same(tinMajor, tinMajor.modify(_.quote).setTo(Finding.Quote("Tin reached the Aegean by sea, in ingots")).modify(_.problem).setTo(Finding.Problem("x"))))
    assert(same(tinMajor, tinMajor.modify(_.quote).setTo(Finding.Quote("")).modify(_.problem).setTo(Finding.Problem("tin is not defined at all"))))
    assert(!same(tinMajor, tinMajor.modify(_.reviewer).setTo(Reviewer.Skeptic)))
    assert(!same(tinMajor, tinMajor.modify(_.quote).setTo(Finding.Quote("")).modify(_.where).setTo(Finding.Where("Summary")).modify(_.problem).setTo(Finding.Problem("a number lacks its unit"))))
    // mechanical: same kind, place, problem up to the colon and quote — a coverage count may change
    val cov = f(
      Finding.Id("M1"),
      Reviewer.Mechanical,
      Severity.Major,
      Finding.Where("Not used"),
      Finding.Problem("3 sources the filter returns are neither cited nor given a reason: [[a]]"),
      Finding.Quote(""),
      Finding.Kind("coverage")
    )
    assert(
      same(
        cov,
        cov.modify(_.id).setTo(Finding.Id("M4")).modify(_.problem).setTo(Finding.Problem("3 sources the filter returns are neither cited nor given a reason: [[b]]"))
      )
    )
    assert(!same(cov, cov.modify(_.kind).setTo(Finding.Kind("citation"))))

  test("transitions: claimed by the fixer, then fixed by its reviewer; not fixed carries the note"):
    val fixed = Fix(com.histmersion.domain.ReviewArticle.Fix.Body("body"), Nil, List(com.histmersion.domain.ReviewArticle.Finding.Id("R1"), com.histmersion.domain.ReviewArticle.Finding.Id("S1")), Nil, Nil, Nil, com.histmersion.domain.ReviewArticle.Fix.Notes(""), com.histmersion.domain.ReviewArticle.Fix.WordsBefore(1), com.histmersion.domain.ReviewArticle.Fix.WordsAfter(1))
    val l = applyFix(fresh, fixed, com.histmersion.domain.ReviewLedger.ApplyFixRun("b7f86a38-be91-c3c1-64ae-ba52940975e8"), com.histmersion.domain.ReviewLedger.ApplyFixRound(1))
    assertEquals(l.claimed(Reviewer.Rules).map(_.id), List("R1"))
    assertEquals(l.claimed(Reviewer.Skeptic).map(_.id), List("S1"))
    val checked =
      applyChecks(l, Reviewer.Rules, List(Check(com.histmersion.domain.ReviewArticle.Check.Id("R1"), fixed = com.histmersion.domain.ReviewArticle.Check.Fixed(true), com.histmersion.domain.ReviewArticle.Check.Quote("Tin, a metal"), com.histmersion.domain.ReviewArticle.Check.Note("defined now"))), com.histmersion.domain.ReviewLedger.ApplyChecksRun("b7f86a38-be91-c3c1-64ae-ba52940975e8"), com.histmersion.domain.ReviewLedger.ApplyChecksRound(2))
    assertEquals(
      checked.entry(com.histmersion.domain.ReviewArticle.Finding.Id("R1")).map(e => (e.status, e.claimed, e.closed)),
      Some((Status.Fixed, false, Some(Closure(com.histmersion.domain.ReviewLedger.Closure.By("rules"), com.histmersion.domain.ReviewLedger.Closure.Round(2), com.histmersion.domain.ReviewLedger.Closure.Run("b7f86a38-be91-c3c1-64ae-ba52940975e8"), com.histmersion.domain.ReviewLedger.Closure.Note("defined now"), com.histmersion.domain.ReviewLedger.Closure.Quote("Tin, a metal")))))
    )
    val notYet = applyChecks(
      checked,
      Reviewer.Skeptic,
      List(Check(com.histmersion.domain.ReviewArticle.Check.Id("S1"), fixed = com.histmersion.domain.ReviewArticle.Check.Fixed(false), com.histmersion.domain.ReviewArticle.Check.Quote("The date is argued"), com.histmersion.domain.ReviewArticle.Check.Note("still no year"))),
      com.histmersion.domain.ReviewLedger.ApplyChecksRun("b7f86a38-be91-c3c1-64ae-ba52940975e8"),
      com.histmersion.domain.ReviewLedger.ApplyChecksRound(2)
    )
    assertEquals(
      notYet.entry(com.histmersion.domain.ReviewArticle.Finding.Id("S1")).map(e => (e.status, e.claimed, e.notFixed)),
      Some((Status.Open, false, Some("still no year")))
    )
    assertEquals(notes(notYet), Map("S1" -> "not fixed yet — still no year"))
    // a reviewer cannot close another's finding, nor a closed one
    val foreign = applyChecks(notYet, Reviewer.Rules, List(Check(com.histmersion.domain.ReviewArticle.Check.Id("S1"), fixed = com.histmersion.domain.ReviewArticle.Check.Fixed(true), com.histmersion.domain.ReviewArticle.Check.Quote("x"), com.histmersion.domain.ReviewArticle.Check.Note(""))), com.histmersion.domain.ReviewLedger.ApplyChecksRun("b7f86a38-be91-c3c1-64ae-ba52940975e8"), com.histmersion.domain.ReviewLedger.ApplyChecksRound(2))
    assertEquals(foreign.entry(com.histmersion.domain.ReviewArticle.Finding.Id("S1")).map(_.status), Some(Status.Open))
    assertEquals(notYet.verdict, Verdict.Major)

  test(
    "transitions: a verified rejection closes with the record's sentence and teaches a lesson; a minor may be declined"
  ):
    val fix = Fix(
      com.histmersion.domain.ReviewArticle.Fix.Body("body"),
      Nil,
      Nil,
      List(Rejection(com.histmersion.domain.ReviewArticle.Rejection.Id("S1"), com.histmersion.domain.ReviewArticle.Rejection.Reason("the record dates it"), com.histmersion.domain.library.Citekey("ck2019"), com.histmersion.domain.ReviewArticle.Rejection.Quote("Its cargo dates to 1320 BCE."), com.histmersion.domain.ReviewArticle.Rejection.Ratio(1.0))),
      Nil,
      Nil,
      com.histmersion.domain.ReviewArticle.Fix.Notes(""),
      com.histmersion.domain.ReviewArticle.Fix.WordsBefore(1),
      com.histmersion.domain.ReviewArticle.Fix.WordsAfter(1),
      wontFix = List(com.histmersion.domain.ReviewArticle.WontFix(com.histmersion.domain.ReviewArticle.WontFix.Id("C1"), com.histmersion.domain.ReviewArticle.WontFix.Reason("defined in the glossary")), com.histmersion.domain.ReviewArticle.WontFix(com.histmersion.domain.ReviewArticle.WontFix.Id("R1"), com.histmersion.domain.ReviewArticle.WontFix.Reason("a major cannot be declined")))
    )
    val l = applyFix(fresh, fix, com.histmersion.domain.ReviewLedger.ApplyFixRun("b7f86a38-be91-c3c1-64ae-ba52940975e8"), com.histmersion.domain.ReviewLedger.ApplyFixRound(1))
    assertEquals(
      l.entry(com.histmersion.domain.ReviewArticle.Finding.Id("S1")).map(e => (e.status, e.closed.flatMap(_.citekey), e.closed.map(_.quote))),
      Some((Status.Rejected, Some("ck2019"), Some("Its cargo dates to 1320 BCE.")))
    )
    assertEquals(l.entry(com.histmersion.domain.ReviewArticle.Finding.Id("C1")).map(_.status), Some(Status.WontFix))
    assertEquals(l.entry(com.histmersion.domain.ReviewArticle.Finding.Id("R1")).map(_.status), Some(Status.Open), "a MAJOR is never declined")
    assertEquals(
      l.lessons.map(x => (x.text, x.source)),
      List(("[[ck2019]]: the record dates it", LessonSource.Rejection))
    )

  test("transitions: a fixed finding raised again is re-opened; a rejected one is re-opened and escalated"):
    val fixedAll =
      applyFix(fresh, Fix(com.histmersion.domain.ReviewArticle.Fix.Body("b"), Nil, Nil, List(Rejection(com.histmersion.domain.ReviewArticle.Rejection.Id("S1"), com.histmersion.domain.ReviewArticle.Rejection.Reason("dated"), com.histmersion.domain.library.Citekey("ck"), com.histmersion.domain.ReviewArticle.Rejection.Quote("q"), com.histmersion.domain.ReviewArticle.Rejection.Ratio(1.0))), Nil, Nil, com.histmersion.domain.ReviewArticle.Fix.Notes(""), com.histmersion.domain.ReviewArticle.Fix.WordsBefore(1), com.histmersion.domain.ReviewArticle.Fix.WordsAfter(1)), com.histmersion.domain.ReviewLedger.ApplyFixRun("b7f86a38-be91-c3c1-64ae-ba52940975e8"), com.histmersion.domain.ReviewLedger.ApplyFixRound(1))
    val l = applyChecks(
      applyFix(fixedAll, Fix(com.histmersion.domain.ReviewArticle.Fix.Body("b"), Nil, List(com.histmersion.domain.ReviewArticle.Finding.Id("R1")), Nil, Nil, Nil, com.histmersion.domain.ReviewArticle.Fix.Notes(""), com.histmersion.domain.ReviewArticle.Fix.WordsBefore(1), com.histmersion.domain.ReviewArticle.Fix.WordsAfter(1)), com.histmersion.domain.ReviewLedger.ApplyFixRun("b7f86a38-be91-c3c1-64ae-ba52940975e8"), com.histmersion.domain.ReviewLedger.ApplyFixRound(1)),
      Reviewer.Rules,
      List(Check(com.histmersion.domain.ReviewArticle.Check.Id("R1"), fixed = com.histmersion.domain.ReviewArticle.Check.Fixed(true), com.histmersion.domain.ReviewArticle.Check.Quote("q"), com.histmersion.domain.ReviewArticle.Check.Note(""))),
      com.histmersion.domain.ReviewLedger.ApplyChecksRun("b7f86a38-be91-c3c1-64ae-ba52940975e8"),
      com.histmersion.domain.ReviewLedger.ApplyChecksRound(2)
    )
    val (back, ids) = absorb(l, List(tinMajor.modify(_.id).setTo(Finding.Id("R7")), dateMajor.modify(_.id).setTo(Finding.Id("S9"))), com.histmersion.domain.automation.Run.Id("53df4857-3ea7-7141-2c78-e3a48bf0b437"), com.histmersion.domain.ReviewLedger.Closure.Round(1))
    assertEquals(ids.map(_.id), List("R1", "S1"))
    assertEquals(back.entry(Finding.Id("R1")).map(e => (e.status, e.reopened, e.closed)), Some((Status.Open, 1, None)))
    assert(back.entry(Finding.Id("R1")).flatMap(_.notFixed).exists(_.value.contains("raised again")))
    assertEquals(back.entry(Finding.Id("S1")).map(e => (e.status, e.finding.escalated)), Some((Status.Open, true)))
    assert(back.entry(Finding.Id("S1")).flatMap(_.notFixed).exists(_.value.contains("rejected before ([[ck]]): dated")))
    // a declined minor raised again stays declined; raised as MAJOR it opens
    val declined =
      applyFix(fresh, Fix(com.histmersion.domain.ReviewArticle.Fix.Body("b"), Nil, Nil, Nil, Nil, Nil, com.histmersion.domain.ReviewArticle.Fix.Notes(""), com.histmersion.domain.ReviewArticle.Fix.WordsBefore(1), com.histmersion.domain.ReviewArticle.Fix.WordsAfter(1), wontFix = List(WontFix(com.histmersion.domain.ReviewArticle.WontFix.Id("C1"), com.histmersion.domain.ReviewArticle.WontFix.Reason("fine")))), com.histmersion.domain.ReviewLedger.ApplyFixRun("b7f86a38-be91-c3c1-64ae-ba52940975e8"), com.histmersion.domain.ReviewLedger.ApplyFixRound(1))
    assertEquals(absorb(declined, List(readerMin), com.histmersion.domain.automation.Run.Id("53df4857-3ea7-7141-2c78-e3a48bf0b437"), com.histmersion.domain.ReviewLedger.Closure.Round(1))._1.entry(com.histmersion.domain.ReviewArticle.Finding.Id("C1")).map(_.status), Some(Status.WontFix))
    assertEquals(
      absorb(declined, List(readerMin.modify(_.severity).setTo(Severity.Major)), com.histmersion.domain.automation.Run.Id("53df4857-3ea7-7141-2c78-e3a48bf0b437"), com.histmersion.domain.ReviewLedger.Closure.Round(1))._1.entry(Finding.Id("C1")).map(_.status),
      Some(Status.Open)
    )

  test("the mechanical re-check: gone → fixed by code; recurring stays open; new is opened"):
    val uncited = f(
      Finding.Id("M1"),
      Reviewer.Mechanical,
      Severity.Major,
      Finding.Where("Bronze needs tin"),
      Finding.Problem("a paragraph with no [[citekey]]"),
      Finding.Quote("Bronze needs tin."),
      Finding.Kind("citation")
    )
    val filler =
      f(Finding.Id("M2"), Reviewer.Mechanical, Severity.Minor, Finding.Where("body"), Finding.Problem("banned filler — it is worth noting"), Finding.Quote(""), Finding.Kind("language"))
    val l0 = absorb(Ledger(com.histmersion.domain.ReviewLedger.Ledger.Slug("tin")), List(uncited, filler), com.histmersion.domain.automation.Run.Id("b7f86a38-be91-c3c1-64ae-ba52940975e8"), com.histmersion.domain.ReviewLedger.Closure.Round(1))._1
    val dup = f(Finding.Id("M1"), Reviewer.Mechanical, Severity.Minor, Finding.Where("Summary"), Finding.Problem("a duplicated heading"), Finding.Quote("## Summary"), Finding.Kind("section"))
    val (l1, _) = recheckMechanical(l0, List(filler.modify(_.id).setTo(Finding.Id("M1")), dup.modify(_.id).setTo(Finding.Id("M2"))), com.histmersion.domain.ReviewLedger.RecheckMechanicalRun("b7f86a38-be91-c3c1-64ae-ba52940975e8"), com.histmersion.domain.ReviewLedger.RecheckMechanicalRound(2))
    assertEquals(
      l1.entries.map(e => (e.id, e.status, e.closed.map(_.by))),
      List(("M1", Status.Fixed, Some("mechanical")), ("M2", Status.Open, None), ("M3", Status.Open, None))
    )

  test("the term count: a MAJOR count stored by an earlier run is superseded by the pass's minor one, under its id"):
    val legacy = f(Finding.Id("C2"), Reviewer.ColdReader, Severity.Major, Finding.Where("explain-back"), Finding.Problem("27 terms are undefined (more than 8)"))
    val stored = absorb(fresh, List(legacy), com.histmersion.domain.automation.Run.Id("b7f86a38-be91-c3c1-64ae-ba52940975e8"), com.histmersion.domain.ReviewLedger.Closure.Round(1))._1
    assertEquals(stored.openMajors.map(_.id), List("R1", "S1", "C2"))
    // the next full pass: the reader counts 12 — a different count and wording, the same finding
    val count = Finding(
      com.histmersion.domain.ReviewArticle.Finding.Id("C1"),
      Reviewer.ColdReader,
      Severity.Minor,
      TermCount.Kind,
      TermCount.Where,
      com.histmersion.domain.ReviewArticle.Finding.Quote(""),
      TermCount.problem(com.histmersion.domain.ReviewArticle.ColdReaderAnswer.TermsUndefined(12)),
      TermCount.Fix
    )
    assert(same(legacy, count))
    assert(!same(readerMin, count))
    val (l, ids) = absorb(recheckTermCount(stored, List(count), com.histmersion.domain.ReviewLedger.RecheckTermCountRun("53df4857-3ea7-7141-2c78-e3a48bf0b437"), com.histmersion.domain.ReviewLedger.RecheckTermCountRound(1)), List(count), com.histmersion.domain.automation.Run.Id("53df4857-3ea7-7141-2c78-e3a48bf0b437"), com.histmersion.domain.ReviewLedger.Closure.Round(1))
    assertEquals(ids.map(_.id), List("C2"))
    assertEquals(l.entries.size, stored.entries.size)
    assertEquals(
      l.entry(com.histmersion.domain.ReviewArticle.Finding.Id("C2")).map(e => (e.status, e.finding.severity, e.finding.problem)),
      Some((Status.Open, Severity.Minor, TermCount.problem(com.histmersion.domain.ReviewArticle.ColdReaderAnswer.TermsUndefined(12))))
    )
    assertEquals(l.openMajors.map(_.id), List("R1", "S1"))
    // with the other MAJORs fixed, the count alone leaves the article MINOR
    val onlyCount = l.modify(_.entries).setTo(l.entries.filter(e => e.id == Finding.Id("C2") || e.id == Finding.Id("C1")))
    assertEquals(onlyCount.verdict, Verdict.Minor)
    assertEquals(taskText(onlyCount, com.histmersion.domain.automation.Run.Id("53df4857-3ea7-7141-2c78-e3a48bf0b437")), None)

  test("the term count: a pass that does not raise it again (8 or fewer) closes it; a declined one stays declined"):
    val legacy = f(Finding.Id("C2"), Reviewer.ColdReader, Severity.Major, Finding.Where("explain-back"), Finding.Problem("27 terms are undefined (more than 8)"))
    val stored = absorb(fresh, List(legacy), com.histmersion.domain.automation.Run.Id("b7f86a38-be91-c3c1-64ae-ba52940975e8"), com.histmersion.domain.ReviewLedger.Closure.Round(1))._1
    val closed = recheckTermCount(stored, List(readerMin), com.histmersion.domain.ReviewLedger.RecheckTermCountRun("53df4857-3ea7-7141-2c78-e3a48bf0b437"), com.histmersion.domain.ReviewLedger.RecheckTermCountRound(3))
    assertEquals(
      closed.entry(com.histmersion.domain.ReviewArticle.Finding.Id("C2")).map(e => (e.status, e.closed.map(c => (c.by, c.round)))),
      Some((Status.Fixed, Some(("mechanical", 3))))
    )
    assertEquals(closed.entry(com.histmersion.domain.ReviewArticle.Finding.Id("C1")).map(_.status), Some(Status.Open))
    // a minor count the fixer declined is not re-opened by the next pass's minor count
    val count = Finding(
      com.histmersion.domain.ReviewArticle.Finding.Id("C1"),
      Reviewer.ColdReader,
      Severity.Minor,
      TermCount.Kind,
      TermCount.Where,
      com.histmersion.domain.ReviewArticle.Finding.Quote(""),
      TermCount.problem(com.histmersion.domain.ReviewArticle.ColdReaderAnswer.TermsUndefined(10)),
      TermCount.Fix
    )
    val minor = absorb(Ledger(com.histmersion.domain.ReviewLedger.Ledger.Slug("tin")), List(count), com.histmersion.domain.automation.Run.Id("b7f86a38-be91-c3c1-64ae-ba52940975e8"), com.histmersion.domain.ReviewLedger.Closure.Round(1))._1
    val declined = applyFix(
      minor,
      Fix(com.histmersion.domain.ReviewArticle.Fix.Body("body"), Nil, Nil, Nil, Nil, Nil, com.histmersion.domain.ReviewArticle.Fix.Notes(""), com.histmersion.domain.ReviewArticle.Fix.WordsBefore(1), com.histmersion.domain.ReviewArticle.Fix.WordsAfter(1), wontFix = List(WontFix(com.histmersion.domain.ReviewArticle.WontFix.Id("C1"), com.histmersion.domain.ReviewArticle.WontFix.Reason("names, not terms")))),
      com.histmersion.domain.ReviewLedger.ApplyFixRun("b7f86a38-be91-c3c1-64ae-ba52940975e8"),
      com.histmersion.domain.ReviewLedger.ApplyFixRound(1)
    )
    assertEquals(declined.entry(com.histmersion.domain.ReviewArticle.Finding.Id("C1")).map(_.status), Some(Status.WontFix))
    assertEquals(
      absorb(declined, List(count.modify(_.problem).setTo(TermCount.problem(com.histmersion.domain.ReviewArticle.ColdReaderAnswer.TermsUndefined(11)))), com.histmersion.domain.automation.Run.Id("53df4857-3ea7-7141-2c78-e3a48bf0b437"), com.histmersion.domain.ReviewLedger.Closure.Round(1))._1.entry(com.histmersion.domain.ReviewArticle.Finding.Id("C1")).map(_.status),
      Some(Status.WontFix)
    )

  test("lessons: trimmed, never twice, the newest 15 kept"):
    val l = learn(
      Ledger(com.histmersion.domain.ReviewLedger.Ledger.Slug("tin")),
      List(com.histmersion.domain.ReviewArticle.Recheck.Lessons("  every date needs  its [[citekey]] "), com.histmersion.domain.ReviewArticle.Recheck.Lessons(""), com.histmersion.domain.ReviewArticle.Recheck.Lessons("Every date needs its [[citekey]]")),
      LessonSource.Recheck,
      com.histmersion.domain.ReviewLedger.LearnRun("4b43b0ae-e356-24cd-95b9-10189b3dc231"),
      com.histmersion.domain.ReviewLedger.LearnRound(2)
    )
    assertEquals(l.lessons.map(_.text), List("every date needs its [[citekey]]"))
    val many = learn(l, (1 to 20).map(i => com.histmersion.domain.ReviewArticle.Recheck.Lessons(s"lesson $i")).toList, LessonSource.Recheck, com.histmersion.domain.ReviewLedger.LearnRun("4b43b0ae-e356-24cd-95b9-10189b3dc231"), com.histmersion.domain.ReviewLedger.LearnRound(3))
    assertEquals(many.lessons.size, MaxLessons)
    assertEquals(many.lessons.last.text, "lesson 20")
    assertEquals(learn(l, List(com.histmersion.domain.ReviewArticle.Recheck.Lessons("x" * 500)), LessonSource.Recheck, com.histmersion.domain.ReviewLedger.LearnRun("4b43b0ae-e356-24cd-95b9-10189b3dc231"), com.histmersion.domain.ReviewLedger.LearnRound(3)).lessons.last.text.value.length, LessonChars)

  test("a verification pass applies checks, absorbs its findings and keeps its lessons"):
    val l = applyFix(fresh, Fix(com.histmersion.domain.ReviewArticle.Fix.Body("b"), Nil, List(com.histmersion.domain.ReviewArticle.Finding.Id("S1")), Nil, Nil, Nil, com.histmersion.domain.ReviewArticle.Fix.Notes(""), com.histmersion.domain.ReviewArticle.Fix.WordsBefore(1), com.histmersion.domain.ReviewArticle.Fix.WordsAfter(1)), com.histmersion.domain.ReviewLedger.ApplyFixRun("b7f86a38-be91-c3c1-64ae-ba52940975e8"), com.histmersion.domain.ReviewLedger.ApplyFixRound(1))
    val rc = Recheck(
      Reviewer.Skeptic,
      List(Check(com.histmersion.domain.ReviewArticle.Check.Id("S1"), fixed = com.histmersion.domain.ReviewArticle.Check.Fixed(true), com.histmersion.domain.ReviewArticle.Check.Quote("1320 BCE"), com.histmersion.domain.ReviewArticle.Check.Note(""))),
      List(f(Finding.Id("S1"), Reviewer.Skeptic, Severity.Minor, Finding.Where("Open questions"), Finding.Problem("a source is missing"), Finding.Quote("Where the tin"))),
      List(com.histmersion.domain.ReviewArticle.Recheck.Lessons("cite the year"))
    )
    val after = applyRecheck(l, rc, com.histmersion.domain.ReviewLedger.ApplyRecheckRun("b7f86a38-be91-c3c1-64ae-ba52940975e8"), com.histmersion.domain.ReviewLedger.ApplyRecheckRound(2))
    assertEquals(
      after.entries.map(e => (e.id, e.status)),
      List(("R1", Status.Open), ("S1", Status.Fixed), ("C1", Status.Open), ("S2", Status.Open))
    )
    assertEquals(after.lessons.map(_.text), List("cite the year"))

  test("merged: the open findings in reviewer order, under ledger ids, with their verdict"):
    val l = applyChecks(
      applyFix(fresh, Fix(com.histmersion.domain.ReviewArticle.Fix.Body("b"), Nil, List(com.histmersion.domain.ReviewArticle.Finding.Id("R1")), Nil, Nil, Nil, com.histmersion.domain.ReviewArticle.Fix.Notes(""), com.histmersion.domain.ReviewArticle.Fix.WordsBefore(1), com.histmersion.domain.ReviewArticle.Fix.WordsAfter(1)), com.histmersion.domain.ReviewLedger.ApplyFixRun("4b43b0ae-e356-24cd-95b9-10189b3dc231"), com.histmersion.domain.ReviewLedger.ApplyFixRound(1)),
      Reviewer.Rules,
      List(Check(com.histmersion.domain.ReviewArticle.Check.Id("R1"), fixed = com.histmersion.domain.ReviewArticle.Check.Fixed(true), com.histmersion.domain.ReviewArticle.Check.Quote("q"), com.histmersion.domain.ReviewArticle.Check.Note(""))),
      com.histmersion.domain.ReviewLedger.ApplyChecksRun("4b43b0ae-e356-24cd-95b9-10189b3dc231"),
      com.histmersion.domain.ReviewLedger.ApplyChecksRound(2)
    )
    val m = merged(l, com.histmersion.domain.ReviewLedger.Closure.Round(2))
    assertEquals((m.round, m.verdict, m.findings.map(_.id)), (2, Verdict.Major, List("C1", "S1")))
    assertEquals(merged(Ledger(com.histmersion.domain.ReviewLedger.Ledger.Slug("tin")), com.histmersion.domain.ReviewLedger.Closure.Round(1)).verdict, Verdict.Pass)

  test("promptText: open findings in full or as lines, closed ones as one line each, the lessons; empty when empty"):
    assertEquals(promptText(Ledger(com.histmersion.domain.ReviewLedger.Ledger.Slug("tin")), OpenStyle.Full, com.histmersion.domain.ReviewLedger.ClosedLimit(60)), "")
    val l = learn(
      applyChecks(
        applyFix(fresh, Fix(com.histmersion.domain.ReviewArticle.Fix.Body("b"), Nil, List(com.histmersion.domain.ReviewArticle.Finding.Id("R1"), com.histmersion.domain.ReviewArticle.Finding.Id("S1")), Nil, Nil, Nil, com.histmersion.domain.ReviewArticle.Fix.Notes(""), com.histmersion.domain.ReviewArticle.Fix.WordsBefore(1), com.histmersion.domain.ReviewArticle.Fix.WordsAfter(1)), com.histmersion.domain.ReviewLedger.ApplyFixRun("4b43b0ae-e356-24cd-95b9-10189b3dc231"), com.histmersion.domain.ReviewLedger.ApplyFixRound(1)),
        Reviewer.Rules,
        List(Check(com.histmersion.domain.ReviewArticle.Check.Id("R1"), fixed = com.histmersion.domain.ReviewArticle.Check.Fixed(true), com.histmersion.domain.ReviewArticle.Check.Quote("q"), com.histmersion.domain.ReviewArticle.Check.Note(""))),
        com.histmersion.domain.ReviewLedger.ApplyChecksRun("4b43b0ae-e356-24cd-95b9-10189b3dc231"),
        com.histmersion.domain.ReviewLedger.ApplyChecksRound(2)
      ),
      List(com.histmersion.domain.ReviewArticle.Recheck.Lessons("cite the year")),
      LessonSource.Recheck,
      com.histmersion.domain.ReviewLedger.LearnRun("4b43b0ae-e356-24cd-95b9-10189b3dc231"),
      com.histmersion.domain.ReviewLedger.LearnRound(2)
    )
    val full = promptText(l, OpenStyle.Full, com.histmersion.domain.ReviewLedger.ClosedLimit(60))
    assert(
      full.contains("Open findings:\n- C1 [cold-reader, minor, term]") && full.contains("- S1 [skeptic, major, date]"),
      full
    )
    assert(full.contains("(The fixer claims to have fixed: S1.)"), full)
    assert(
      full.contains(
        "Closed findings (one line each):\n- R1 major (Intro): tin is not defined → fixed, verified by rules in pass 2"
      ),
      full
    )
    assert(full.endsWith("Lessons:\n- cite the year"), full)
    val mine = promptText(l, OpenStyle.Lines, com.histmersion.domain.ReviewLedger.ClosedLimit(60), Some(Reviewer.Skeptic))
    assert(mine.contains("- S1 major: the date has no support") && !mine.contains("C1") && !mine.contains("R1"), mine)
    assert(!promptText(l, OpenStyle.Omit, com.histmersion.domain.ReviewLedger.ClosedLimit(60)).contains("Open findings"))
    assert(!promptText(l, OpenStyle.Full, com.histmersion.domain.ReviewLedger.ClosedLimit(0)).contains("Closed findings"))

  test("editedPassages: the changed paragraphs with a paragraph of margin, under their heading; what was removed"):
    val before =
      com.histmersion.domain.ReviewArticle.Fix.Body("Intro one.\n\n## Disagreements\n\nThe date is argued.\n\nA second point.\n\n## Open\n\nMines.\n\nLast.")
    val after =
      com.histmersion.domain.ReviewArticle.Fix.Body("Intro one.\n\n## Disagreements\n\nThe cargo dates to 1320 BCE.\n\nA second point.\n\n## Open\n\nMines.\n\nLast.")
    val e = editedPassages(before, after)
    assertEquals(
      e.passages,
      List(Passage(Some(com.histmersion.domain.ReviewLedger.Passage.Heading("Disagreements")), com.histmersion.domain.ReviewLedger.Passage.Text("## Disagreements\n\nThe cargo dates to 1320 BCE.\n\nA second point.")))
    )
    assertEquals(e.removed, List("The date is argued."))
    assert(
      e.render.contains("[under: Disagreements]") && e.render.contains("Removed:\n- The date is argued."),
      e.render
    )
    assert(!e.text.value.contains("Mines"))
    // two edits far apart are two passages; an insertion with no removal; no change at all
    val two = editedPassages(before, com.histmersion.domain.ReviewArticle.Fix.Body(after.value.replace("Last.", "Last, revised.")))
    assertEquals(two.passages.size, 2)
    assertEquals(editedPassages(before, com.histmersion.domain.ReviewArticle.Fix.Body(before.value + "\n\nAdded.")).removed, Nil)
    assert(editedPassages(before, before).isEmpty)
    assertEquals(editedPassages(before, after, margin = com.histmersion.domain.ReviewLedger.PassageMargin(0)).passages.map(_.text), List("The cargo dates to 1320 BCE."))

  test("taskText (#934): one line per article listing its open MAJORs, cut to size; none when none is open"):
    val esc = absorb(
      applyFix(fresh, Fix(com.histmersion.domain.ReviewArticle.Fix.Body("b"), Nil, Nil, List(Rejection(com.histmersion.domain.ReviewArticle.Rejection.Id("S1"), com.histmersion.domain.ReviewArticle.Rejection.Reason("dated"), com.histmersion.domain.library.Citekey("ck"), com.histmersion.domain.ReviewArticle.Rejection.Quote("q"), com.histmersion.domain.ReviewArticle.Rejection.Ratio(1.0))), Nil, Nil, com.histmersion.domain.ReviewArticle.Fix.Notes(""), com.histmersion.domain.ReviewArticle.Fix.WordsBefore(1), com.histmersion.domain.ReviewArticle.Fix.WordsAfter(1)), com.histmersion.domain.ReviewLedger.ApplyFixRun("4b43b0ae-e356-24cd-95b9-10189b3dc231"), com.histmersion.domain.ReviewLedger.ApplyFixRound(1)),
      List(dateMajor),
      com.histmersion.domain.automation.Run.Id("d2791864-28a7-5016-b17e-4df5ea43d080"),
      com.histmersion.domain.ReviewLedger.Closure.Round(1)
    )._1
    assertEquals(
      taskText(esc, com.histmersion.domain.automation.Run.Id("abcdef01-93ac-ef8b-cd70-71607816bfcb")),
      Some(
        "review-article [[tin]]: 2 open MAJORs — R1 (Intro): tin is not defined; " +
          "S1 (Disagreements): the date has no support [raised again after a rejection] (agent run abcdef01)"
      )
    )
    val one = esc.modify(_.entries).setTo(esc.entries.filterNot(_.id == Finding.Id("S1")))
    assert(taskText(one, com.histmersion.domain.automation.Run.Id("abcdef01-93ac-ef8b-cd70-71607816bfcb")).exists(_.value.contains(": 1 open MAJOR — R1 (Intro)")))
    assertEquals(taskText(fresh.copy(entries = fresh.entries.filterNot(_.major)), com.histmersion.domain.automation.Run.Id("9dd4e461-268c-8034-f5c8-564e155c67a6")), None)
    val long = absorb(
      Ledger(com.histmersion.domain.ReviewLedger.Ledger.Slug("tin")),
      (1 to 40).toList.map(i => f(Finding.Id(s"R$i"), Reviewer.Rules, Severity.Major, Finding.Where(s"Section $i"), Finding.Problem(s"problem number $i " * 5))),
      com.histmersion.domain.automation.Run.Id("4b43b0ae-e356-24cd-95b9-10189b3dc231"),
      com.histmersion.domain.ReviewLedger.Closure.Round(1)
    )._1
    val t = taskText(long, com.histmersion.domain.automation.Run.Id("abcdef01-93ac-ef8b-cd70-71607816bfcb"), maxChars = com.histmersion.domain.ReviewLedger.TaskTextLimit(300)).get
    assert(t.value.length <= 300 && t.value.contains("…") && t.value.endsWith("(agent run abcdef01)"), t)
    assert(legacyTask(com.histmersion.domain.research.Slug("tin"), com.histmersion.domain.ReviewLedger.TaskText("review-article [[tin]]: MAJOR open — S1 major: x")))
    assert(legacyTask(com.histmersion.domain.research.Slug("tin"), com.histmersion.domain.ReviewLedger.TaskText("review-article [[tin]]: MAJOR raised again after the fixer rejected it — S1")))
    assert(
      !legacyTask(com.histmersion.domain.research.Slug("tin"), com.histmersion.domain.ReviewLedger.TaskText(t.value.replace("[[tin]]", "[[tin]]"))) && !legacyTask(com.histmersion.domain.research.Slug("tin2"), com.histmersion.domain.ReviewLedger.TaskText("review-article [[tin]]: MAJOR open"))
    )
    // the collapsed item, as a run or the interactive skill writes it, is found again by its article
    assert(articleTask(com.histmersion.domain.ReviewLedger.ArticleTaskSlug("tin"), t))
    assert(articleTask(com.histmersion.domain.ReviewLedger.ArticleTaskSlug("tin"), com.histmersion.domain.ReviewLedger.TaskText("review-article tin: 2 open MAJORs — R4 (Intro): x; S2: y")))
    assert(!articleTask(com.histmersion.domain.ReviewLedger.ArticleTaskSlug("tin2"), t) && !articleTask(com.histmersion.domain.ReviewLedger.ArticleTaskSlug("ti"), t))
    assert(
      !articleTask(com.histmersion.domain.ReviewLedger.ArticleTaskSlug("tin"), com.histmersion.domain.ReviewLedger.TaskText("review-article [[tin]]: MAJOR open — S1 major: x")),
      "a legacy item is closed, not reused"
    )
    assert(!articleTask(com.histmersion.domain.ReviewLedger.ArticleTaskSlug("tin"), com.histmersion.domain.ReviewLedger.TaskText("compile-article [[tin]]: 2 open MAJORs")))

  test("the ledger round-trips through JSON (what review_ledgers stores and a step checkpoints)"):
    val l = learn(
      applyFix(
        fresh,
        Fix(
          com.histmersion.domain.ReviewArticle.Fix.Body("b"),
          Nil,
          List(com.histmersion.domain.ReviewArticle.Finding.Id("R1")),
          List(Rejection(com.histmersion.domain.ReviewArticle.Rejection.Id("S1"), com.histmersion.domain.ReviewArticle.Rejection.Reason("dated"), com.histmersion.domain.library.Citekey("ck"), com.histmersion.domain.ReviewArticle.Rejection.Quote("q"), com.histmersion.domain.ReviewArticle.Rejection.Ratio(1.0))),
          Nil,
          Nil,
          com.histmersion.domain.ReviewArticle.Fix.Notes(""),
          com.histmersion.domain.ReviewArticle.Fix.WordsBefore(1),
          com.histmersion.domain.ReviewArticle.Fix.WordsAfter(1),
          wontFix = List(com.histmersion.domain.ReviewArticle.WontFix(com.histmersion.domain.ReviewArticle.WontFix.Id("C1"), com.histmersion.domain.ReviewArticle.WontFix.Reason("fine")))
        ),
        com.histmersion.domain.ReviewLedger.ApplyFixRun("4b43b0ae-e356-24cd-95b9-10189b3dc231"),
        com.histmersion.domain.ReviewLedger.ApplyFixRound(1)
      ),
      List(com.histmersion.domain.ReviewArticle.Recheck.Lessons("x")),
      LessonSource.Recheck,
      com.histmersion.domain.ReviewLedger.LearnRun("4b43b0ae-e356-24cd-95b9-10189b3dc231"),
      com.histmersion.domain.ReviewLedger.LearnRound(1)
    ).copy(task = Some(Ledger.Task(12)))
    assertEquals(decode[Ledger](l.asJson.noSpaces), Right(l))
    assert(l.asJson.noSpaces.contains("\"status\":\"wont-fix\""))
    // an older ledger without the optional fields still decodes
    assertEquals(decode[Ledger]("""{"slug":"tin"}""").map(_.entries), Right(Nil))
