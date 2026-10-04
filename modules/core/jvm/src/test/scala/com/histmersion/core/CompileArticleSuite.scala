package com.histmersion.core

import com.histmersion.core.CompileArticle.*
import com.histmersion.core.GoalPlans.DraftMode

/** compile-article's pure logic: the claim table (quotes found in the record), the evidence model,
  * and the code self-check of a draft — fatal problems always sent back, the rest sent back until the
  * last attempt, then listed for the person who approves. */
class CompileArticleSuite extends munit.FunSuite:

  private val record =
    "Summary:\nThe Uluburun ship carried ten tons of copper and one ton of tin.\n\nText:\nIts cargo dates to about 1320 BCE."

  test("extraction: a claim stands only on a quote found in the record; confidence is a vocabulary"):
    val good = ClaimAnswer("The ship carried tin.", "The Uluburun ship carried ten tons of copper and one ton of tin.", None, Some("Uluburun"), "high")
    val made = ClaimAnswer("Tin came from Cornwall.", "Cornish tin was shipped east every summer by traders.", None, None, "medium")
    val conf = ClaimAnswer("It dates to 1320 BCE.", "Its cargo dates to about 1320 BCE.", Some("1320 BCE"), None, "certain")
    val sent = checkExtract(ExtractAnswer(true, "tin trade", List(good, made, conf)), "ck", record, 0.9, 10, last = false)
    assert(sent.left.exists(ps => ps.exists(_.contains("not in the record")) && ps.exists(_.contains("certain"))), sent)
    val (kept, warns) = checkExtract(ExtractAnswer(true, "tin trade", List(good, made, conf)), "ck", record, 0.9, 10, last = true).toOption.get
    assertEquals(kept.claims.map(_.claim), List("The ship carried tin."))
    assertEquals(kept.claims.head.confidence, Confidence.High)
    assertEquals(warns.size, 2)

  test("extraction: an irrelevant source must say why, and lands in Not used"):
    assert(checkExtract(ExtractAnswer(false, " ", Nil), "ck", record, 0.9, 10, last = false).isLeft)
    val (e, _) = checkExtract(ExtractAnswer(false, "about Egypt, not the Aegean", Nil), "ck", record, 0.9, 10, last = false).toOption.get
    assertEquals(e.notUsed, Some("about Egypt, not the Aegean"))

  private val facts = List(
    SourceFact("a2019_tin", "Tin", Some(2019), SourceStatus.Current),
    SourceFact("b2024_iso", "Isotopes", Some(2024), SourceStatus.Contested),
    SourceFact("c2001_old", "Old", Some(2001), SourceStatus.Current))
  private def claim(ck: String) = Claim(ck, s"claim of $ck", "q", None, None, Confidence.Medium, 1.0)
  private val extracted = List(
    Extracted("a2019_tin", List(claim("a2019_tin"), claim("a2019_tin")), None),
    Extracted("b2024_iso", List(claim("b2024_iso")), None),
    Extracted("c2001_old", Nil, Some("about Egypt")))
  private val ev = evidence("search {topics:[tin]}", 3, facts, extracted,
    List(EdgeFact("b2024_iso", "challenges", "a2019_tin"), EdgeFact("a2019_tin", "references", "c2001_old"),
      EdgeFact("b2024_iso", "refutes", "z_outside")))

  test("evidence: disagreement edges among the filter's sources only, statuses, weight, Not used"):
    assertEquals(ev.edges, List(EdgeFact("b2024_iso", "challenges", "a2019_tin")))
    assertEquals(ev.disputed, List("b2024_iso"))
    assertEquals(ev.claims, Map("a2019_tin" -> 2, "b2024_iso" -> 1, "c2001_old" -> 0))
    assertEquals(ev.notUsed, List(NotUsed("c2001_old", "about Egypt")))
    assertEquals(ev.used, List("a2019_tin", "b2024_iso"))
    val text = evidenceText(ev, extracted)
    assert(text.contains("[[b2024_iso]] challenges [[a2019_tin]]") && text.contains("status **contested**"), text)

  private val known = Set("a2019_tin", "b2024_iso", "c2001_old")
  private val statuses = facts.map(f => f.citekey -> f.status).toMap
  private val corpus = Corpus(known, t => known(t) || t == "tin_article", statuses.get)

  private val goodBody =
    """Tin reached the Aegean by sea [[a2019_tin]].
      |
      |## Disagreements
      |
      |The source of the tin is disputed: [[b2024_iso]] (contested) places it in Central Asia, against [[a2019_tin]].
      |
      |## Open questions
      |
      |Where the tin was mined is open [[a2019_tin]].
      |
      |## Not used
      |
      |- [[c2001_old]]: about Egypt
      |
      |_Method: search {topics:[tin]} → 3._""".stripMargin

  private def draft(body: String, section: Option[String] = None) = DraftAnswer("Tin", "Where Aegean tin came from.", body, section)

  test("self-check: a clean new article passes and records what it cites and leaves out"):
    val checked = checkDraft(draft(goodBody), "tin", DraftMode.Create, "concept", None, "", ev, "f", corpus, last = false)
    val (d, warns) = checked.fold(ps => fail(ps.mkString("; ")), identity)
    assertEquals(warns, Nil)
    assertEquals(d.cited.toSet, Set("a2019_tin", "b2024_iso"), "a source under Not used is named, not cited")
    assertEquals(d.problems, Nil)
    assertEquals(d.notUsed, List("c2001_old: about Egypt"))

  test("self-check: fatal problems are always sent back, even on the last attempt"):
    val dangling = goodBody.replace("[[a2019_tin]].\n", "[[nowhere_2020]].\n")
    assert(checkDraft(draft(dangling), "tin", DraftMode.Create, "concept", None, "", ev, "f", corpus, last = true).isLeft)
    val sourcesCited = goodBody + "\n\n## Sources cited\n\n- [[a2019_tin]]"
    assert(checkDraft(draft(sourcesCited), "tin", DraftMode.Create, "concept", None, "", ev, "f", corpus, last = true)
      .left.exists(_.exists(_.contains("Sources cited"))))
    assert(checkDraft(draft("  "), "tin", DraftMode.Create, "concept", None, "", ev, "f", corpus, last = true).isLeft)

  test("self-check: a silent status, an uncited paragraph, a missing section are sent back, then kept and listed"):
    val silent = goodBody.replace("[[b2024_iso]] (contested) places", "[[b2024_iso]] places")
      .replace("## Open questions", "## Notes").replace("Where the tin was mined is open [[a2019_tin]].", "Nobody knows.")
    val ps = checkDraft(draft(silent), "tin", DraftMode.Create, "concept", None, "", ev, "f", corpus, last = false).left.toOption.get
    assert(ps.exists(_.contains("does not say it is contested")), ps)
    assert(ps.exists(_.contains("no [[citekey]]")), ps)
    assert(ps.exists(_.contains("'unresolved' or 'open questions'")), ps)
    val (d, warns) = checkDraft(draft(silent), "tin", DraftMode.Create, "concept", None, "", ev, "f", corpus, last = true).toOption.get
    assertEquals(d.problems.size, ps.size)
    assertEquals(warns.size, ps.size)

  test("self-check: a citation outside the claim table is a problem (a decorative citation)"):
    val outside = goodBody.replace("Tin reached the Aegean by sea [[a2019_tin]].", "Tin reached the Aegean [[a2019_tin]] [[c2001_old]].")
    val ps = checkDraft(draft(outside), "tin", DraftMode.Create, "concept", None, "", ev, "f", corpus, last = false).left.toOption.get
    assert(ps.exists(_.contains("[[c2001_old]] is cited but no claim")), ps)

  test("extending: the text goes under an existing heading (or at the end); statuses are checked on the result"):
    val current = "Intro [[a2019_tin]].\n\n## Disagreements\n\nOld text [[a2019_tin]].\n\n## Open questions\n\nOpen [[a2019_tin]]."
    val add = "A newer study disputes the origin [[b2024_iso]] (contested)."
    val (d, _) = checkDraft(draft(add, Some("## Disagreements")), "tin", DraftMode.Extend, "concept", Some("Tin"), current, ev,
      "f", corpus, last = false).toOption.get
    assertEquals((d.mode, d.section, d.title), (DraftMode.Extend, Some("## Disagreements"), "Tin"))
    val whole = combined(DraftMode.Extend, current, add, Some("## Disagreements")).toOption.get
    assert(whole.indexOf(add) < whole.indexOf("## Open questions"), whole)
    // a heading the article does not have is fatal: the append would fail
    assert(checkDraft(draft(add, Some("## Nope")), "tin", DraftMode.Extend, "concept", Some("Tin"), current, ev, "f", corpus,
      last = true).left.exists(_.exists(_.contains("no heading"))))
    // the required sections are not demanded of an extension
    assert(checkDraft(draft(add), "tin", DraftMode.Extend, "concept", Some("Tin"), "Intro [[a2019_tin]].", ev, "f", corpus,
      last = false).isRight)

  test("the first citation of a non-current source must state its status"):
    val st = (ck: String) => statuses.get(ck)
    assertEquals(statusesSilent("[[b2024_iso]] says X.\n\n[[b2024_iso]] (contested) again.", st),
      List("the first citation of [[b2024_iso]] does not say it is contested"))
    assertEquals(statusesSilent("[[b2024_iso]], contested by others, says X.", st), Nil)
    assertEquals(statusesSilent("[[a2019_tin]] says X.", st), Nil)

  test("the filter line and the log line"):
    assertEquals(filterLine(Some("tin"), List("trade"), Nil), "search {query:\"tin\", topics:[trade]}")
    val d = GoalPlans.ArticleDraft("tin", DraftMode.Create, "Tin", "concept", "x", cited = List("a", "b"))
    assert(logLine(d, written = true, "created", Some("abcdef123"), "run12345xyz").contains("compiled a new article from 2 source(s); review run abcdef12 started"))
    assert(logLine(d, written = false, "the draft was rejected", None, "r").contains("nothing written — the draft was rejected"))
