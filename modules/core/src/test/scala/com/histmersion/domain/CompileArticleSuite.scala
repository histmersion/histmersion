package com.histmersion
package domain

import com.histmersion.util.problem.Text.sentence
import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import com.histmersion.domain.CompileArticle.*
import com.histmersion.api.automation.Goal.Plans.DraftMode
import com.histmersion.api.library.Confidence
import com.histmersion.api.library.Source
import com.histmersion.api.automation.Goal
import com.histmersion.api.research.Article
import com.histmersion.api.research.Coverage
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.research.Slug
import com.histmersion.api.library.Filter
import com.histmersion.domain.automation.Run
import com.histmersion.lint.{tolerated, LintRule}

/** compile-article's pure logic: the claim table (quotes found in the record), the evidence model, and the code
  * self-check of a draft — fatal problems always sent back, the rest sent back until the last attempt, then listed for
  * the person who approves.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class CompileArticleSuite extends munit.FunSuite:

  private val record = AgentVerify.SourceBody(
    "Summary:\nThe Uluburun ship carried ten tons of copper and one ton of tin.\n\nText:\nIts cargo dates to about 1320 BCE."
  )
  private val minRatio = AgentVerify.MinRatio(0.9)

  test("extraction: a claim stands only on a quote found in the record; confidence is a vocabulary"):
    val good = ClaimAnswer(
      ClaimAnswer.Claim("The ship carried tin."),
      AgentProposals.Proposal.Quote("The Uluburun ship carried ten tons of copper and one ton of tin."),
      None,
      Some(ClaimAnswer.Place("Uluburun")),
      library.Confidence.Wire("high")
    )
    val made = ClaimAnswer(
      ClaimAnswer.Claim("Tin came from Cornwall."),
      AgentProposals.Proposal.Quote("Cornish tin was shipped east every summer by traders."),
      None,
      None,
      library.Confidence.Wire("medium")
    )
    val conf =
      ClaimAnswer(
        ClaimAnswer.Claim("It dates to 1320 BCE."),
        AgentProposals.Proposal.Quote("Its cargo dates to about 1320 BCE."),
        Some(ClaimAnswer.Date("1320 BCE")),
        None,
        library.Confidence.Wire("certain")
      )
    val sent =
      checkExtract(
        ExtractAnswer(
          ExtractAnswer.Relevant(true),
          Coverage.Rationale("tin trade"),
          List(good, made, conf)
        ),
        Citekey("ck"),
        record,
        minRatio,
        MaxClaims(10),
        last = Last(false)
      )
    assert(
      sent.left.exists(ps =>
        ps.exists(_.sentence.contains("not in the record")) && ps.exists(_.sentence.contains("certain"))
      ),
      sent
    )
    val (kept, warns) = checkExtract(
      ExtractAnswer(
        ExtractAnswer.Relevant(true),
        Coverage.Rationale("tin trade"),
        List(good, made, conf)
      ),
      Citekey("ck"),
      record,
      minRatio,
      MaxClaims(10),
      last = Last(true)
    ).toOption.get
    assertEquals(kept.claims.map(_.claim), List("The ship carried tin."))
    assertEquals(kept.claims.head.confidence, Confidence.High)
    assertEquals(warns.size, 2)

  test("extraction: an irrelevant source must say why, and lands in Not used"):
    assert(
      checkExtract(
        ExtractAnswer(ExtractAnswer.Relevant(false), Coverage.Rationale(" "), Nil),
        Citekey("ck"),
        record,
        minRatio,
        MaxClaims(10),
        last = Last(false)
      ).isLeft
    )
    val (e, _) = checkExtract(
      ExtractAnswer(
        ExtractAnswer.Relevant(false),
        Coverage.Rationale("about Egypt, not the Aegean"),
        Nil
      ),
      Citekey("ck"),
      record,
      minRatio,
      MaxClaims(10),
      last = Last(false)
    ).toOption.get
    assertEquals(e.notUsed.map(r => s"$r"), Some("about Egypt, not the Aegean"))

  private val facts = List(
    SourceFact(Citekey("a2019_tin"), Title("Tin"), Some(Year(2019)), Source.Status.Current),
    SourceFact(Citekey("b2024_iso"), Title("Isotopes"), Some(Year(2024)), Source.Status.Contested),
    SourceFact(Citekey("c2001_old"), Title("Old"), Some(Year(2001)), Source.Status.Current)
  )
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def claim(ck: Citekey) = Claim(
    ck,
    ClaimAnswer.Claim(s"claim of $ck"),
    AgentProposals.Proposal.Quote("q"),
    None,
    None,
    Confidence.Medium,
    AgentVerify.Ratio(1.0)
  )
  private val extracted = List(
    Extracted(Citekey("a2019_tin"), List(claim(Citekey("a2019_tin")), claim(Citekey("a2019_tin"))), None),
    Extracted(Citekey("b2024_iso"), List(claim(Citekey("b2024_iso"))), None),
    Extracted(Citekey("c2001_old"), Nil, Some(Coverage.Rationale("about Egypt")))
  )
  private val ev = evidence(
    Goal.Plans.ArticleDraft.Filter("search {topics:[tin]}"),
    RowCount(3),
    facts,
    extracted,
    List(
      EdgeFact(Citekey("b2024_iso"), Article.Coverage.DisagreementKind("challenges"), Citekey("a2019_tin")),
      EdgeFact(Citekey("a2019_tin"), Article.Coverage.DisagreementKind("references"), Citekey("c2001_old")),
      EdgeFact(Citekey("b2024_iso"), Article.Coverage.DisagreementKind("refutes"), Citekey("z_outside"))
    )
  )

  test("evidence: disagreement edges among the filter's sources only, statuses, weight, Not used"):
    assertEquals(
      ev.edges,
      List(EdgeFact(Citekey("b2024_iso"), Article.Coverage.DisagreementKind("challenges"), Citekey("a2019_tin")))
    )
    assertEquals(ev.disputed, List("b2024_iso"))
    assertEquals(ev.claims, Map("a2019_tin" -> 2, "b2024_iso" -> 1, "c2001_old" -> 0))
    assertEquals(ev.notUsed, List(NotUsed(Citekey("c2001_old"), Coverage.Rationale("about Egypt"))))
    assertEquals(ev.used, List("a2019_tin", "b2024_iso"))
    val text = evidenceText(ev, extracted)
    assert(
      text.value.contains("[[b2024_iso]] challenges [[a2019_tin]]") && text.value.contains("status **contested**"),
      text
    )

  private val known = Set("a2019_tin", "b2024_iso", "c2001_old")
  private val statuses = facts.map(f => f.citekey -> f.status).toMap
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val corpus = Corpus(ck => known(ck.value), t => known(t.value) || t.value == "tin_article", statuses.get)

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

  private def draft(body: Article.Body, section: Option[Article.Section] = None, reasons: List[Coverage.Reason] = Nil) =
    DraftAnswer(Title("Tin"), Article.SummaryText("Where Aegean tin came from."), body, section, reasons)

  test("self-check: a clean new article passes, with no Not used section or Method note (code generates both)"):
    val checked =
      checkDraft(
        draft(Article.Body(goodBody)),
        Slug("tin"),
        DraftMode.Create,
        Article.Kind.Wire("concept"),
        None,
        Article.Body(""),
        ev,
        Goal.Plans.ArticleDraft.Filter("f"),
        corpus,
        last = Last(false)
      )
    val (d, warns) = checked.fold(ps => fail(ps.map(_.sentence).mkString("; ")), identity)
    assertEquals(warns, Nil)
    assertEquals(d.cited.toSet, Set("a2019_tin", "b2024_iso"))
    assertEquals(d.problems, Nil)
    assertEquals(d.notUsed, List("c2001_old: about Egypt"))
    // the extraction's reason is what the article stores for the source it leaves out
    assertEquals(
      d.reasons,
      List(
        Coverage.Reason(
          api.library.Citekey("c2001_old"),
          Coverage.Rationale("about Egypt")
        )
      )
    )

  test("self-check (#958): a hand-written Not used section or Method note is sent back"):
    val old = goodBody + "\n\n## Not used\n\n- [[c2001_old]]: about Egypt\n\n_Method: search {topics:[tin]} → 3._"
    val ps = checkDraft(
      draft(Article.Body(old)),
      Slug("tin"),
      DraftMode.Create,
      Article.Kind.Wire("concept"),
      None,
      Article.Body(""),
      ev,
      Goal.Plans.ArticleDraft.Filter("f"),
      corpus,
      last = Last(false)
    ).left.toOption.get
    assert(ps.exists(_.sentence.startsWith("a Not used section: code generates it")), ps)
    assert(ps.exists(_.sentence.startsWith("a _Method_ note: code generates it")), ps)
    // a source listed there is not cited
    val (d, _) =
      checkDraft(
        draft(Article.Body(old)),
        Slug("tin"),
        DraftMode.Create,
        Article.Kind.Wire("concept"),
        None,
        Article.Body(""),
        ev,
        Goal.Plans.ArticleDraft.Filter("f"),
        corpus,
        last = Last(true)
      ).toOption.get
    assertEquals(d.cited.toSet, Set("a2019_tin", "b2024_iso"))

  test("self-check (#958): a claim-table source the draft leaves out needs the writer's reason; reasons are checked"):
    val noIso = goodBody.replace(
      "[[b2024_iso]] (contested) places it in Central Asia, against [[a2019_tin]]",
      "the tin's origin is argued [[a2019_tin]]"
    )
    val ps = checkDraft(
      draft(Article.Body(noIso)),
      Slug("tin"),
      DraftMode.Create,
      Article.Kind.Wire("concept"),
      None,
      Article.Body(""),
      ev,
      Goal.Plans.ArticleDraft.Filter("f"),
      corpus,
      last = Last(false)
    ).left.toOption.get
    assertEquals(
      ps.map(_.sentence),
      List("[[b2024_iso]] has claims in the table but is neither cited nor given a reason")
    )
    val why = Coverage.Reason(
      api.library.Citekey("b2024_iso"),
      Coverage.Rationale("Its isotopes speak to a later period.")
    )
    val (d, _) = checkDraft(
      draft(Article.Body(noIso), reasons = List(why)),
      Slug("tin"),
      DraftMode.Create,
      Article.Kind.Wire("concept"),
      None,
      Article.Body(""),
      ev,
      Goal.Plans.ArticleDraft.Filter("f"),
      corpus,
      last = Last(false)
    ).fold(ps => fail(ps.map(_.sentence).mkString("; ")), identity)
    assertEquals(
      d.reasons,
      List(
        Coverage.Reason(
          api.library.Citekey("b2024_iso"),
          Coverage.Rationale("Its isotopes speak to a later period.")
        ),
        Coverage.Reason(
          api.library.Citekey("c2001_old"),
          Coverage.Rationale("about Egypt")
        )
      ).sortBy(_.citekey.value)
    )
    // the writer's reason wins over the extraction's; a reason for a cited source or one outside the table is a problem
    val bad = List(
      Coverage
        .Reason(api.library.Citekey("[[a2019_tin]]"), Coverage.Rationale("not needed")),
      Coverage
        .Reason(api.library.Citekey("zz1999_nope"), Coverage.Rationale("never read")),
      Coverage.Reason(
        api.library.Citekey("c2001_old"),
        Coverage.Rationale("covers Egypt only")
      )
    )
    val ps2 = checkDraft(
      draft(Article.Body(goodBody), reasons = bad),
      Slug("tin"),
      DraftMode.Create,
      Article.Kind.Wire("concept"),
      None,
      Article.Body(""),
      ev,
      Goal.Plans.ArticleDraft.Filter("f"),
      corpus,
      last = Last(false)
    ).left.toOption.get
    assertEquals(
      ps2.map(_.sentence),
      List(
        "a reason for [[a2019_tin]], which the draft cites (a reason is only for a source it leaves out)",
        "a reason for [[zz1999_nope]], which is not a source of the claim table"
      )
    )
    val (d2, _) = checkDraft(
      draft(Article.Body(goodBody), reasons = bad),
      Slug("tin"),
      DraftMode.Create,
      Article.Kind.Wire("concept"),
      None,
      Article.Body(""),
      ev,
      Goal.Plans.ArticleDraft.Filter("f"),
      corpus,
      last = Last(true)
    ).toOption.get
    assertEquals(
      d2.reasons,
      List(
        Coverage.Reason(
          api.library.Citekey("c2001_old"),
          Coverage.Rationale("covers Egypt only")
        )
      )
    )

  test("self-check: fatal problems are always sent back, even on the last attempt"):
    val dangling = goodBody.replace("[[a2019_tin]].\n", "[[nowhere_2020]].\n")
    assert(
      checkDraft(
        draft(Article.Body(dangling)),
        Slug("tin"),
        DraftMode.Create,
        Article.Kind.Wire("concept"),
        None,
        Article.Body(""),
        ev,
        Goal.Plans.ArticleDraft.Filter("f"),
        corpus,
        last = Last(true)
      ).isLeft
    )
    val sourcesCited = goodBody + "\n\n## Sources cited\n\n- [[a2019_tin]]"
    assert(
      checkDraft(
        draft(Article.Body(sourcesCited)),
        Slug("tin"),
        DraftMode.Create,
        Article.Kind.Wire("concept"),
        None,
        Article.Body(""),
        ev,
        Goal.Plans.ArticleDraft.Filter("f"),
        corpus,
        last = Last(true)
      ).left
        .exists(_.exists(_.sentence.contains("Sources cited")))
    )
    assert(
      checkDraft(
        draft(Article.Body("  ")),
        Slug("tin"),
        DraftMode.Create,
        Article.Kind.Wire("concept"),
        None,
        Article.Body(""),
        ev,
        Goal.Plans.ArticleDraft.Filter("f"),
        corpus,
        last = Last(true)
      ).isLeft
    )

  test("self-check: a silent status, an uncited paragraph, a missing section are sent back, then kept and listed"):
    val silent = goodBody
      .replace("[[b2024_iso]] (contested) places", "[[b2024_iso]] places")
      .replace("## Open questions", "## Notes")
      .replace("Where the tin was mined is open [[a2019_tin]].", "Nobody knows.")
    val ps = checkDraft(
      draft(Article.Body(silent)),
      Slug("tin"),
      DraftMode.Create,
      Article.Kind.Wire("concept"),
      None,
      Article.Body(""),
      ev,
      Goal.Plans.ArticleDraft.Filter("f"),
      corpus,
      last = Last(false)
    ).left.toOption.get
    assert(ps.exists(_.sentence.contains("does not say it is contested")), ps)
    assert(ps.exists(_.sentence.contains("no [[citekey]]")), ps)
    assert(ps.exists(_.sentence.contains("'unresolved' or 'open questions'")), ps)
    val (d, warns) =
      checkDraft(
        draft(Article.Body(silent)),
        Slug("tin"),
        DraftMode.Create,
        Article.Kind.Wire("concept"),
        None,
        Article.Body(""),
        ev,
        Goal.Plans.ArticleDraft.Filter("f"),
        corpus,
        last = Last(true)
      ).toOption.get
    assertEquals(d.problems.size, ps.size)
    assertEquals(warns.size, ps.size)

  test("self-check: a citation outside the claim table is a problem (a decorative citation)"):
    val outside = goodBody.replace(
      "Tin reached the Aegean by sea [[a2019_tin]].",
      "Tin reached the Aegean [[a2019_tin]] [[c2001_old]]."
    )
    val ps = checkDraft(
      draft(Article.Body(outside)),
      Slug("tin"),
      DraftMode.Create,
      Article.Kind.Wire("concept"),
      None,
      Article.Body(""),
      ev,
      Goal.Plans.ArticleDraft.Filter("f"),
      corpus,
      last = Last(false)
    ).left.toOption.get
    assert(ps.exists(_.sentence.contains("[[c2001_old]] is cited but no claim")), ps)

  test("extending: the text goes under an existing heading (or at the end); statuses are checked on the result"):
    val current = Article.Body(
      "Intro [[a2019_tin]].\n\n## Disagreements\n\nOld text [[a2019_tin]].\n\n## Open questions\n\nOpen [[a2019_tin]]."
    )
    val add = Article.Body("A newer study disputes the origin [[b2024_iso]] (contested).")
    val (d, _) = checkDraft(
      draft(add, Some(Article.Section("## Disagreements"))),
      Slug("tin"),
      DraftMode.Extend,
      Article.Kind.Wire("concept"),
      Some(Title("Tin")),
      current,
      ev,
      Goal.Plans.ArticleDraft.Filter("f"),
      corpus,
      last = Last(false)
    ).toOption.get
    assertEquals((d.mode, d.section, d.title), (DraftMode.Extend, Some("## Disagreements"), "Tin"))
    val whole = combined(DraftMode.Extend, current, add, Some(Article.Section("## Disagreements"))).toOption.get
    assert(whole.value.indexOf(add.value) < whole.value.indexOf("## Open questions"), whole)
    // a heading the article does not have is fatal: the append would fail
    assert(
      checkDraft(
        draft(add, Some(Article.Section("## Nope"))),
        Slug("tin"),
        DraftMode.Extend,
        Article.Kind.Wire("concept"),
        Some(Title("Tin")),
        current,
        ev,
        Goal.Plans.ArticleDraft.Filter("f"),
        corpus,
        last = Last(true)
      ).left.exists(_.exists(_.sentence.contains("no article section '## Nope'")))
    )
    // the required sections are not demanded of an extension
    assert(
      checkDraft(
        draft(add),
        Slug("tin"),
        DraftMode.Extend,
        Article.Kind.Wire("concept"),
        Some(Title("Tin")),
        Article.Body("Intro [[a2019_tin]]."),
        ev,
        Goal.Plans.ArticleDraft.Filter("f"),
        corpus,
        last = Last(false)
      ).isRight
    )

  test("the first citation of a non-current source must state its status"):
    val st = (ck: Citekey) => statuses.get(ck)
    assertEquals(
      statusesSilent(Article.Body("[[b2024_iso]] says X.\n\n[[b2024_iso]] (contested) again."), st).map(_.sentence),
      List("the first citation of [[b2024_iso]] does not say it is contested")
    )
    assertEquals(statusesSilent(Article.Body("[[b2024_iso]], contested by others, says X."), st), Nil)
    assertEquals(statusesSilent(Article.Body("[[a2019_tin]] says X."), st), Nil)

  test("the run's filter: its own, else the article's recorded one, else the title; and the log line"):
    val stored = Article.Filter(topics = List(Filter.Topics("tin")), status = List(Filter.Status("current")))
    assertEquals(
      runFilter(Some(Filter.Q("tin")), List(Filter.Topics("trade")), Nil, Some(stored), Some(Title("Tin"))),
      Some(Article.Filter(query = Some(Filter.Q("tin")), topics = List(Filter.Topics("trade"))))
    )
    assertEquals(runFilter(None, Nil, Nil, Some(stored), Some(Title("Tin"))), Some(stored))
    assertEquals(
      runFilter(None, Nil, Nil, None, Some(Title("Tin"))),
      Some(Article.Filter(query = Some(Filter.Q("Tin"))))
    )
    assertEquals(runFilter(Some(Filter.Q(" ")), Nil, Nil, None, None), None)
    assertEquals(
      Article.Filter(query = Some(Filter.Q("tin")), topics = List(Filter.Topics("trade"))).line,
      "search {query: \"tin\", topics: [\"trade\"]}"
    )
    val d = Goal.Plans.ArticleDraft(
      Article.Slug("tin"),
      DraftMode.Create,
      Article.Title("Tin"),
      Article.Kind.Wire("concept"),
      Goal.Plans.ArticleDraft.Text("x"),
      cited = List(api.library.Citekey("a"), api.library.Citekey("b"))
    )
    assert(
      logLine(
        d,
        written = Written(true),
        Rationale("created"),
        Some(Run.Id("abcdef12-fc74-0955-ef4d-fad670250ff4")),
        Run.Id("bc1cc32f-a9bb-9592-ac46-36b5b5c5da01")
      ).value.contains(
        "compiled a new article from 2 source(s); review run abcdef12 started"
      )
    )
    assert(
      logLine(
        d,
        written = Written(false),
        Rationale("the draft was rejected"),
        None,
        Run.Id("4b43b0ae-e356-24cd-95b9-10189b3dc231")
      ).value.contains(
        "nothing written — the draft was rejected"
      )
    )
