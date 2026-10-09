package com.histmersion.core

import com.histmersion.core.ReviewArticle.*
import com.histmersion.core.ReviewLedger.*
import io.circe.parser.decode
import io.circe.syntax.*

/** The review ledger's pure half: matching, every status transition, the lessons, what a prompt shows,
  * the edited passages and the one backlog line. */
class ReviewLedgerSuite extends munit.FunSuite:

  private def f(id: String, r: Reviewer, sev: Severity, where: String, problem: String, quote: String = "", kind: String = "term") =
    Finding(id, r, sev, kind, where, quote, problem, "fix it")

  private val tinMajor  = f("R1", Reviewer.Rules, Severity.Major, "Intro", "tin is not defined", "Tin reached the Aegean")
  private val dateMajor = f("S1", Reviewer.Skeptic, Severity.Major, "Disagreements", "the date has no support", "The date is argued", "date")
  private val readerMin = f("C1", Reviewer.ColdReader, Severity.Minor, "Open questions", "opaque sentence", "Where the tin was mined")

  private def fresh: Ledger = absorb(Ledger("tin"), List(tinMajor, dateMajor, readerMin), "run1", 1)._1

  test("absorb: new findings get ledger ids per reviewer; the same one again keeps its id"):
    val l = fresh
    assertEquals(l.entries.map(e => (e.id, e.status, e.round)), List(("R1", Status.Open, 1), ("S1", Status.Open, 1), ("C1", Status.Open, 1)))
    // pass 2 numbers its findings from 1 again: the ledger maps them
    val again = f("R1", Reviewer.Rules, Severity.Major, "Intro", "tin is still not defined", "Tin reached the Aegean")
    val other = f("R2", Reviewer.Rules, Severity.Minor, "Summary", "a filler phrase", "it is worth noting")
    val (l2, ids) = absorb(l, List(other, again), "run2", 2)
    assertEquals(ids.map(_.id), List("R2", "R1"))
    assertEquals(l2.entries.size, 4)
    assertEquals(l2.entry("R1").map(_.finding.problem), Some("tin is still not defined"))
    assertEquals(nextId(l2, Reviewer.Rules), "R3")
    assertEquals(nextId(l2, Reviewer.Mechanical), "M1")

  test("same: one reviewer only; the quoted passage, or the same where with a similar problem"):
    assert(same(tinMajor, tinMajor.copy(quote = "Tin reached the Aegean by sea, in ingots", problem = "x")))
    assert(same(tinMajor, tinMajor.copy(quote = "", problem = "tin is not defined at all")))
    assert(!same(tinMajor, tinMajor.copy(reviewer = Reviewer.Skeptic)))
    assert(!same(tinMajor, tinMajor.copy(quote = "", where = "Summary", problem = "a number lacks its unit")))
    // mechanical: same kind, place, problem up to the colon and quote — a coverage count may change
    val cov = f("M1", Reviewer.Mechanical, Severity.Major, "Not used", "3 sources the filter returns are neither cited nor given a reason: [[a]]", "", "coverage")
    assert(same(cov, cov.copy(id = "M4", problem = "3 sources the filter returns are neither cited nor given a reason: [[b]]")))
    assert(!same(cov, cov.copy(kind = "citation")))

  test("transitions: claimed by the fixer, then fixed by its reviewer; not fixed carries the note"):
    val fixed = Fix("body", Nil, List("R1", "S1"), Nil, Nil, Nil, "", 1, 1)
    val l = applyFix(fresh, fixed, "run1", 1)
    assertEquals(l.claimed(Reviewer.Rules).map(_.id), List("R1"))
    assertEquals(l.claimed(Reviewer.Skeptic).map(_.id), List("S1"))
    val checked = applyChecks(l, Reviewer.Rules, List(Check("R1", fixed = true, "Tin, a metal", "defined now")), "run1", 2)
    assertEquals(checked.entry("R1").map(e => (e.status, e.claimed, e.closed)),
      Some((Status.Fixed, false, Some(Closure("rules", 2, "run1", "defined now", "Tin, a metal")))))
    val notYet = applyChecks(checked, Reviewer.Skeptic, List(Check("S1", fixed = false, "The date is argued", "still no year")), "run1", 2)
    assertEquals(notYet.entry("S1").map(e => (e.status, e.claimed, e.notFixed)), Some((Status.Open, false, Some("still no year"))))
    assertEquals(notes(notYet), Map("S1" -> "not fixed yet — still no year"))
    // a reviewer cannot close another's finding, nor a closed one
    val foreign = applyChecks(notYet, Reviewer.Rules, List(Check("S1", fixed = true, "x", "")), "run1", 2)
    assertEquals(foreign.entry("S1").map(_.status), Some(Status.Open))
    assertEquals(notYet.verdict, Verdict.Major)

  test("transitions: a verified rejection closes with the record's sentence and teaches a lesson; a minor may be declined"):
    val fix = Fix("body", Nil, Nil, List(Rejection("S1", "the record dates it", "ck2019", "Its cargo dates to 1320 BCE.", 1.0)),
      Nil, Nil, "", 1, 1, wontFix = List(WontFix("C1", "defined in the glossary"), WontFix("R1", "a major cannot be declined")))
    val l = applyFix(fresh, fix, "run1", 1)
    assertEquals(l.entry("S1").map(e => (e.status, e.closed.flatMap(_.citekey), e.closed.map(_.quote))),
      Some((Status.Rejected, Some("ck2019"), Some("Its cargo dates to 1320 BCE."))))
    assertEquals(l.entry("C1").map(_.status), Some(Status.WontFix))
    assertEquals(l.entry("R1").map(_.status), Some(Status.Open), "a MAJOR is never declined")
    assertEquals(l.lessons.map(x => (x.text, x.source)), List(("[[ck2019]]: the record dates it", LessonSource.Rejection)))

  test("transitions: a fixed finding raised again is re-opened; a rejected one is re-opened and escalated"):
    val fixedAll = applyFix(fresh, Fix("b", Nil, Nil, List(Rejection("S1", "dated", "ck", "q", 1.0)), Nil, Nil, "", 1, 1), "run1", 1)
    val l = applyChecks(applyFix(fixedAll, Fix("b", Nil, List("R1"), Nil, Nil, Nil, "", 1, 1), "run1", 1),
      Reviewer.Rules, List(Check("R1", fixed = true, "q", "")), "run1", 2)
    val (back, ids) = absorb(l, List(tinMajor.copy(id = "R7"), dateMajor.copy(id = "S9")), "run2", 1)
    assertEquals(ids.map(_.id), List("R1", "S1"))
    assertEquals(back.entry("R1").map(e => (e.status, e.reopened, e.closed)), Some((Status.Open, 1, None)))
    assert(back.entry("R1").flatMap(_.notFixed).exists(_.contains("raised again")))
    assertEquals(back.entry("S1").map(e => (e.status, e.finding.escalated)), Some((Status.Open, true)))
    assert(back.entry("S1").flatMap(_.notFixed).exists(_.contains("rejected before ([[ck]]): dated")))
    // a declined minor raised again stays declined; raised as MAJOR it opens
    val declined = applyFix(fresh, Fix("b", Nil, Nil, Nil, Nil, Nil, "", 1, 1, wontFix = List(WontFix("C1", "fine"))), "run1", 1)
    assertEquals(absorb(declined, List(readerMin), "run2", 1)._1.entry("C1").map(_.status), Some(Status.WontFix))
    assertEquals(absorb(declined, List(readerMin.copy(severity = Severity.Major)), "run2", 1)._1.entry("C1").map(_.status), Some(Status.Open))

  test("the mechanical re-check: gone → fixed by code; recurring stays open; new is opened"):
    val uncited = f("M1", Reviewer.Mechanical, Severity.Major, "Bronze needs tin", "a paragraph with no [[citekey]]", "Bronze needs tin.", "citation")
    val filler  = f("M2", Reviewer.Mechanical, Severity.Minor, "body", "banned filler — it is worth noting", "", "language")
    val l0 = absorb(Ledger("tin"), List(uncited, filler), "run1", 1)._1
    val dup = f("M1", Reviewer.Mechanical, Severity.Minor, "Summary", "a duplicated heading", "## Summary", "section")
    val (l1, _) = recheckMechanical(l0, List(filler.copy(id = "M1"), dup.copy(id = "M2")), "run1", 2)
    assertEquals(l1.entries.map(e => (e.id, e.status, e.closed.map(_.by))), List(
      ("M1", Status.Fixed, Some("mechanical")), ("M2", Status.Open, None), ("M3", Status.Open, None)))

  test("lessons: trimmed, never twice, the newest 15 kept"):
    val l = learn(Ledger("tin"), List("  every date needs  its [[citekey]] ", "", "Every date needs its [[citekey]]"), LessonSource.Recheck, "r", 2)
    assertEquals(l.lessons.map(_.text), List("every date needs its [[citekey]]"))
    val many = learn(l, (1 to 20).map(i => s"lesson $i").toList, LessonSource.Recheck, "r", 3)
    assertEquals(many.lessons.size, MaxLessons)
    assertEquals(many.lessons.last.text, "lesson 20")
    assertEquals(learn(l, List("x" * 500), LessonSource.Recheck, "r", 3).lessons.last.text.length, LessonChars)

  test("a verification pass applies checks, absorbs its findings and keeps its lessons"):
    val l  = applyFix(fresh, Fix("b", Nil, List("S1"), Nil, Nil, Nil, "", 1, 1), "run1", 1)
    val rc = Recheck(Reviewer.Skeptic, List(Check("S1", fixed = true, "1320 BCE", "")),
      List(f("S1", Reviewer.Skeptic, Severity.Minor, "Open questions", "a source is missing", "Where the tin")), List("cite the year"))
    val after = applyRecheck(l, rc, "run1", 2)
    assertEquals(after.entries.map(e => (e.id, e.status)), List(("R1", Status.Open), ("S1", Status.Fixed), ("C1", Status.Open),
      ("S2", Status.Open)))
    assertEquals(after.lessons.map(_.text), List("cite the year"))

  test("merged: the open findings in reviewer order, under ledger ids, with their verdict"):
    val l = applyChecks(applyFix(fresh, Fix("b", Nil, List("R1"), Nil, Nil, Nil, "", 1, 1), "r", 1),
      Reviewer.Rules, List(Check("R1", fixed = true, "q", "")), "r", 2)
    val m = merged(l, 2)
    assertEquals((m.round, m.verdict, m.findings.map(_.id)), (2, Verdict.Major, List("C1", "S1")))
    assertEquals(merged(Ledger("tin"), 1).verdict, Verdict.Pass)

  test("promptText: open findings in full or as lines, closed ones as one line each, the lessons; empty when empty"):
    assertEquals(promptText(Ledger("tin"), OpenStyle.Full, 60), "")
    val l = learn(applyChecks(applyFix(fresh, Fix("b", Nil, List("R1", "S1"), Nil, Nil, Nil, "", 1, 1), "r", 1),
      Reviewer.Rules, List(Check("R1", fixed = true, "q", "")), "r", 2), List("cite the year"), LessonSource.Recheck, "r", 2)
    val full = promptText(l, OpenStyle.Full, 60)
    assert(full.contains("Open findings:\n- C1 [cold-reader, minor, term]") && full.contains("- S1 [skeptic, major, date]"), full)
    assert(full.contains("(The fixer claims to have fixed: S1.)"), full)
    assert(full.contains("Closed findings (one line each):\n- R1 major (Intro): tin is not defined → fixed, verified by rules in pass 2"), full)
    assert(full.endsWith("Lessons:\n- cite the year"), full)
    val mine = promptText(l, OpenStyle.Lines, 60, Some(Reviewer.Skeptic))
    assert(mine.contains("- S1 major: the date has no support") && !mine.contains("C1") && !mine.contains("R1"), mine)
    assert(!promptText(l, OpenStyle.Omit, 60).contains("Open findings"))
    assert(!promptText(l, OpenStyle.Full, 0).contains("Closed findings"))

  test("editedPassages: the changed paragraphs with a paragraph of margin, under their heading; what was removed"):
    val before = "Intro one.\n\n## Disagreements\n\nThe date is argued.\n\nA second point.\n\n## Open\n\nMines.\n\nLast."
    val after  = "Intro one.\n\n## Disagreements\n\nThe cargo dates to 1320 BCE.\n\nA second point.\n\n## Open\n\nMines.\n\nLast."
    val e = editedPassages(before, after)
    assertEquals(e.passages, List(Passage(Some("Disagreements"), "## Disagreements\n\nThe cargo dates to 1320 BCE.\n\nA second point.")))
    assertEquals(e.removed, List("The date is argued."))
    assert(e.render.contains("[under: Disagreements]") && e.render.contains("Removed:\n- The date is argued."), e.render)
    assert(!e.text.contains("Mines"))
    // two edits far apart are two passages; an insertion with no removal; no change at all
    val two = editedPassages(before, after.replace("Last.", "Last, revised."))
    assertEquals(two.passages.size, 2)
    assertEquals(editedPassages(before, before + "\n\nAdded.").removed, Nil)
    assert(editedPassages(before, before).isEmpty)
    assertEquals(editedPassages(before, after, margin = 0).passages.map(_.text), List("The cargo dates to 1320 BCE."))

  test("taskText (#934): one line per article listing its open MAJORs, cut to size; none when none is open"):
    val esc = absorb(applyFix(fresh, Fix("b", Nil, Nil, List(Rejection("S1", "dated", "ck", "q", 1.0)), Nil, Nil, "", 1, 1), "r", 1),
      List(dateMajor), "r2", 1)._1
    assertEquals(taskText(esc, "abcdef0123"), Some("review-article [[tin]]: 2 open MAJORs — R1 (Intro): tin is not defined; " +
      "S1 (Disagreements): the date has no support [raised again after a rejection] (agent run abcdef01)"))
    val one = esc.copy(entries = esc.entries.filterNot(_.id == "S1"))
    assert(taskText(one, "abcdef0123").exists(_.contains(": 1 open MAJOR — R1 (Intro)")))
    assertEquals(taskText(fresh.copy(entries = fresh.entries.filterNot(_.major)), "x"), None)
    val long = absorb(Ledger("tin"), (1 to 40).toList.map(i => f(s"R$i", Reviewer.Rules, Severity.Major, s"Section $i", s"problem number $i " * 5)), "r", 1)._1
    val t = taskText(long, "abcdef0123", maxChars = 300).get
    assert(t.length <= 300 && t.contains("…") && t.endsWith("(agent run abcdef01)"), t)
    assert(legacyTask("tin", "review-article [[tin]]: MAJOR open — S1 major: x"))
    assert(legacyTask("tin", "review-article [[tin]]: MAJOR raised again after the fixer rejected it — S1"))
    assert(!legacyTask("tin", t.replace("[[tin]]", "[[tin]]")) && !legacyTask("tin2", "review-article [[tin]]: MAJOR open"))

  test("the ledger round-trips through JSON (what review_ledgers stores and a step checkpoints)"):
    val l = learn(applyFix(fresh, Fix("b", Nil, List("R1"), List(Rejection("S1", "dated", "ck", "q", 1.0)), Nil, Nil, "", 1, 1,
      wontFix = List(WontFix("C1", "fine"))), "r", 1), List("x"), LessonSource.Recheck, "r", 1).copy(task = Some(12))
    assertEquals(decode[Ledger](l.asJson.noSpaces), Right(l))
    assert(l.asJson.noSpaces.contains("\"status\":\"wont-fix\""))
    // an older ledger without the optional fields still decodes
    assertEquals(decode[Ledger]("""{"slug":"tin"}""").map(_.entries), Right(Nil))
