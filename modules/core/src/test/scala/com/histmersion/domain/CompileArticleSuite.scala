package com.histmersion.domain

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import com.histmersion.domain.CompileArticle.*
import com.histmersion.api.automation.Goal.Plans.DraftMode
import com.histmersion.domain.library.Confidence
import com.histmersion.domain.library.Source
import com.histmersion.api.automation.Goal
import com.histmersion.api.research.Article
import com.histmersion.api.research.Coverage

/** compile-article's pure logic: the claim table (quotes found in the record), the evidence model, and the code
  * self-check of a draft — fatal problems always sent back, the rest sent back until the last attempt, then listed for
  * the person who approves.
  */
class CompileArticleSuite extends munit.FunSuite:

  private val record =
    "Summary:\nThe Uluburun ship carried ten tons of copper and one ton of tin.\n\nText:\nIts cargo dates to about 1320 BCE."

  test("extraction: a claim stands only on a quote found in the record; confidence is a vocabulary"):
    val good = ClaimAnswer(
      "The ship carried tin.",
      "The Uluburun ship carried ten tons of copper and one ton of tin.",
      None,
      Some("Uluburun"),
      "high"
    )
    val made = ClaimAnswer(
      "Tin came from Cornwall.",
      "Cornish tin was shipped east every summer by traders.",
      None,
      None,
      "medium"
    )
    val conf =
      ClaimAnswer("It dates to 1320 BCE.", "Its cargo dates to about 1320 BCE.", Some("1320 BCE"), None, "certain")
    val sent =
      checkExtract(ExtractAnswer(true, "tin trade", List(good, made, conf)), "ck", record, 0.9, 10, last = false)
    assert(sent.left.exists(ps => ps.exists(_.contains("not in the record")) && ps.exists(_.contains("certain"))), sent)
    val (kept, warns) = checkExtract(
      ExtractAnswer(true, "tin trade", List(good, made, conf)),
      "ck",
      record,
      0.9,
      10,
      last = true
    ).toOption.get
    assertEquals(kept.claims.map(_.claim), List("The ship carried tin."))
    assertEquals(kept.claims.head.confidence, Confidence.High)
    assertEquals(warns.size, 2)

  test("extraction: an irrelevant source must say why, and lands in Not used"):
    assert(checkExtract(ExtractAnswer(false, " ", Nil), "ck", record, 0.9, 10, last = false).isLeft)
    val (e, _) = checkExtract(
      ExtractAnswer(false, "about Egypt, not the Aegean", Nil),
      "ck",
      record,
      0.9,
      10,
      last = false
    ).toOption.get
    assertEquals(e.notUsed, Some("about Egypt, not the Aegean"))

  private val facts = List(
    SourceFact("a2019_tin", "Tin", Some(2019), Source.Status.Current),
    SourceFact("b2024_iso", "Isotopes", Some(2024), Source.Status.Contested),
    SourceFact("c2001_old", "Old", Some(2001), Source.Status.Current)
  )
  private def claim(ck: String) = Claim(ck, s"claim of $ck", "q", None, None, Confidence.Medium, 1.0)
  private val extracted = List(
    Extracted("a2019_tin", List(claim("a2019_tin"), claim("a2019_tin")), None),
    Extracted("b2024_iso", List(claim("b2024_iso")), None),
    Extracted("c2001_old", Nil, Some("about Egypt"))
  )
  private val ev = evidence(
    "search {topics:[tin]}",
    3,
    facts,
    extracted,
    List(
      EdgeFact("b2024_iso", "challenges", "a2019_tin"),
      EdgeFact("a2019_tin", "references", "c2001_old"),
      EdgeFact("b2024_iso", "refutes", "z_outside")
    )
  )

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
      |Where the tin was mined is open [[a2019_tin]].""".stripMargin

  private def draft(body: String, section: Option[String] = None, reasons: List[Coverage.Reason] = Nil) =
    DraftAnswer("Tin", "Where Aegean tin came from.", body, section, reasons)

  test("self-check: a clean new article passes, with no Not used section or Method note (code generates both)"):
    val checked =
      checkDraft(draft(goodBody), "tin", DraftMode.Create, "concept", None, "", ev, "f", corpus, last = false)
    val (d, warns) = checked.fold(ps => fail(ps.mkString("; ")), identity)
    assertEquals(warns, Nil)
    assertEquals(d.cited.toSet, Set("a2019_tin", "b2024_iso"))
    assertEquals(d.problems, Nil)
    assertEquals(d.notUsed, List("c2001_old: about Egypt"))
    // the extraction's reason is what the article stores for the source it leaves out
    assertEquals(d.reasons, List(Coverage.Reason("c2001_old", "about Egypt")))

  test("self-check (#958): a hand-written Not used section or Method note is sent back"):
    val old = goodBody + "\n\n## Not used\n\n- [[c2001_old]]: about Egypt\n\n_Method: search {topics:[tin]} → 3._"
    val ps = checkDraft(
      draft(old),
      "tin",
      DraftMode.Create,
      "concept",
      None,
      "",
      ev,
      "f",
      corpus,
      last = false
    ).left.toOption.get
    assert(ps.exists(_.startsWith("a Not used section: code generates it")), ps)
    assert(ps.exists(_.startsWith("a _Method_ note: code generates it")), ps)
    // a source listed there is not cited
    val (d, _) =
      checkDraft(draft(old), "tin", DraftMode.Create, "concept", None, "", ev, "f", corpus, last = true).toOption.get
    assertEquals(d.cited.toSet, Set("a2019_tin", "b2024_iso"))

  test("self-check (#958): a claim-table source the draft leaves out needs the writer's reason; reasons are checked"):
    val noIso = goodBody.replace(
      "[[b2024_iso]] (contested) places it in Central Asia, against [[a2019_tin]]",
      "the tin's origin is argued [[a2019_tin]]"
    )
    val ps = checkDraft(
      draft(noIso),
      "tin",
      DraftMode.Create,
      "concept",
      None,
      "",
      ev,
      "f",
      corpus,
      last = false
    ).left.toOption.get
    assertEquals(ps, List("[[b2024_iso]] has claims in the table but is neither cited nor given a reason"))
    val why = Coverage.Reason("b2024_iso", "Its isotopes speak to a later period.")
    val (d, _) = checkDraft(
      draft(noIso, reasons = List(why)),
      "tin",
      DraftMode.Create,
      "concept",
      None,
      "",
      ev,
      "f",
      corpus,
      last = false
    ).fold(ps => fail(ps.mkString("; ")), identity)
    assertEquals(
      d.reasons,
      List(
        Coverage.Reason("b2024_iso", "Its isotopes speak to a later period."),
        Coverage.Reason("c2001_old", "about Egypt")
      ).sortBy(_.citekey)
    )
    // the writer's reason wins over the extraction's; a reason for a cited source or one outside the table is a problem
    val bad = List(
      Coverage.Reason("[[a2019_tin]]", "not needed"),
      Coverage.Reason("zz1999_nope", "never read"),
      Coverage.Reason("c2001_old", "covers Egypt only")
    )
    val ps2 = checkDraft(
      draft(goodBody, reasons = bad),
      "tin",
      DraftMode.Create,
      "concept",
      None,
      "",
      ev,
      "f",
      corpus,
      last = false
    ).left.toOption.get
    assertEquals(
      ps2,
      List(
        "a reason for [[a2019_tin]], which the draft cites (a reason is only for a source it leaves out)",
        "a reason for [[zz1999_nope]], which is not a source of the claim table"
      )
    )
    val (d2, _) = checkDraft(
      draft(goodBody, reasons = bad),
      "tin",
      DraftMode.Create,
      "concept",
      None,
      "",
      ev,
      "f",
      corpus,
      last = true
    ).toOption.get
    assertEquals(d2.reasons, List(Coverage.Reason("c2001_old", "covers Egypt only")))

  test("self-check: fatal problems are always sent back, even on the last attempt"):
    val dangling = goodBody.replace("[[a2019_tin]].\n", "[[nowhere_2020]].\n")
    assert(
      checkDraft(draft(dangling), "tin", DraftMode.Create, "concept", None, "", ev, "f", corpus, last = true).isLeft
    )
    val sourcesCited = goodBody + "\n\n## Sources cited\n\n- [[a2019_tin]]"
    assert(
      checkDraft(draft(sourcesCited), "tin", DraftMode.Create, "concept", None, "", ev, "f", corpus, last = true).left
        .exists(_.exists(_.contains("Sources cited")))
    )
    assert(checkDraft(draft("  "), "tin", DraftMode.Create, "concept", None, "", ev, "f", corpus, last = true).isLeft)

  test("self-check: a silent status, an uncited paragraph, a missing section are sent back, then kept and listed"):
    val silent = goodBody
      .replace("[[b2024_iso]] (contested) places", "[[b2024_iso]] places")
      .replace("## Open questions", "## Notes")
      .replace("Where the tin was mined is open [[a2019_tin]].", "Nobody knows.")
    val ps = checkDraft(
      draft(silent),
      "tin",
      DraftMode.Create,
      "concept",
      None,
      "",
      ev,
      "f",
      corpus,
      last = false
    ).left.toOption.get
    assert(ps.exists(_.contains("does not say it is contested")), ps)
    assert(ps.exists(_.contains("no [[citekey]]")), ps)
    assert(ps.exists(_.contains("'unresolved' or 'open questions'")), ps)
    val (d, warns) =
      checkDraft(draft(silent), "tin", DraftMode.Create, "concept", None, "", ev, "f", corpus, last = true).toOption.get
    assertEquals(d.problems.size, ps.size)
    assertEquals(warns.size, ps.size)

  test("self-check: a citation outside the claim table is a problem (a decorative citation)"):
    val outside = goodBody.replace(
      "Tin reached the Aegean by sea [[a2019_tin]].",
      "Tin reached the Aegean [[a2019_tin]] [[c2001_old]]."
    )
    val ps = checkDraft(
      draft(outside),
      "tin",
      DraftMode.Create,
      "concept",
      None,
      "",
      ev,
      "f",
      corpus,
      last = false
    ).left.toOption.get
    assert(ps.exists(_.contains("[[c2001_old]] is cited but no claim")), ps)

  test("extending: the text goes under an existing heading (or at the end); statuses are checked on the result"):
    val current =
      "Intro [[a2019_tin]].\n\n## Disagreements\n\nOld text [[a2019_tin]].\n\n## Open questions\n\nOpen [[a2019_tin]]."
    val add = "A newer study disputes the origin [[b2024_iso]] (contested)."
    val (d, _) = checkDraft(
      draft(add, Some("## Disagreements")),
      "tin",
      DraftMode.Extend,
      "concept",
      Some("Tin"),
      current,
      ev,
      "f",
      corpus,
      last = false
    ).toOption.get
    assertEquals((d.mode, d.section, d.title), (DraftMode.Extend, Some("## Disagreements"), "Tin"))
    val whole = combined(DraftMode.Extend, current, add, Some("## Disagreements")).toOption.get
    assert(whole.indexOf(add) < whole.indexOf("## Open questions"), whole)
    // a heading the article does not have is fatal: the append would fail
    assert(
      checkDraft(
        draft(add, Some("## Nope")),
        "tin",
        DraftMode.Extend,
        "concept",
        Some("Tin"),
        current,
        ev,
        "f",
        corpus,
        last = true
      ).left.exists(_.exists(_.contains("no heading")))
    )
    // the required sections are not demanded of an extension
    assert(
      checkDraft(
        draft(add),
        "tin",
        DraftMode.Extend,
        "concept",
        Some("Tin"),
        "Intro [[a2019_tin]].",
        ev,
        "f",
        corpus,
        last = false
      ).isRight
    )

  test("the first citation of a non-current source must state its status"):
    val st = (ck: String) => statuses.get(ck)
    assertEquals(
      statusesSilent("[[b2024_iso]] says X.\n\n[[b2024_iso]] (contested) again.", st),
      List("the first citation of [[b2024_iso]] does not say it is contested")
    )
    assertEquals(statusesSilent("[[b2024_iso]], contested by others, says X.", st), Nil)
    assertEquals(statusesSilent("[[a2019_tin]] says X.", st), Nil)

  test("the run's filter: its own, else the article's recorded one, else the title; and the log line"):
    val stored = Article.Filter(topics = List("tin"), status = List("current"))
    assertEquals(
      runFilter(Some("tin"), List("trade"), Nil, Some(stored), Some("Tin")),
      Some(Article.Filter(query = Some("tin"), topics = List("trade")))
    )
    assertEquals(runFilter(None, Nil, Nil, Some(stored), Some("Tin")), Some(stored))
    assertEquals(runFilter(None, Nil, Nil, None, Some("Tin")), Some(Article.Filter(query = Some("Tin"))))
    assertEquals(runFilter(Some(" "), Nil, Nil, None, None), None)
    assertEquals(
      Article.Filter(query = Some("tin"), topics = List("trade")).line,
      "search {query: \"tin\", topics: [\"trade\"]}"
    )
    val d = Goal.Plans.ArticleDraft("tin", DraftMode.Create, "Tin", "concept", "x", cited = List("a", "b"))
    assert(
      logLine(d, written = true, "created", Some("abcdef123"), "run12345xyz").contains(
        "compiled a new article from 2 source(s); review run abcdef12 started"
      )
    )
    assert(
      logLine(d, written = false, "the draft was rejected", None, "r").contains(
        "nothing written — the draft was rejected"
      )
    )
