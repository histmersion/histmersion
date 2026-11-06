package com.histmersion.api

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.library.CitationLint

import com.histmersion.api.checks.Lint.*
import com.histmersion.api.checks.{FormatField, FormatIssue, FormatRule, FormatSeverity, Lint}
import com.histmersion.api.library.Edge
import com.histmersion.api.research.Article

/** The lint classifiers (plan §5.2 stream 2.8) — everything that decides whether a row offends and what the report says
  * about it, with no database in sight.
  */
class LintSuite extends munit.FunSuite:

  private val corpus = Set(
    "heggarty2023_language",
    "anthony2015_ringe",
    "kassian2025_critique",
    "knapp2016_collapse",
    "knapp2016_seapeoples"
  )

  private def edge(src: String, dst: String, kind: String = "references") = Edge(com.histmersion.api.library.Citekey(src), com.histmersion.api.library.Citekey(dst), kind)

  // ── rule selection ───────────────────────────────────────────────────────────────────────────

  test("no rules requested = every rule"):
    assertEquals(Lint.select(Nil).map(_.map(_.name)), Right(Lint.names))

  test("a subset is honoured, deduplicated, and blanks are ignored"):
    assertEquals(
      Lint.select(List(com.histmersion.api.checks.Lint.Rule.Name("no-findings"), com.histmersion.api.checks.Lint.Rule.Name(" "), com.histmersion.api.checks.Lint.Rule.Name("no-findings"), com.histmersion.api.checks.Lint.Rule.Name("no-period"))).map(_.map(_.name)),
      Right(List(NoFindings, NoPeriod))
    )

  test("an unknown rule is an error, never a silently clean report"):
    val e = Lint.select(List(com.histmersion.api.checks.Lint.Rule.Name("no-findings"), com.histmersion.api.checks.Lint.Rule.Name("no-vibes"))).left.getOrElse("")
    assert(e.contains("no-vibes"), e)
    assert(e.contains("no-findings"), "the error lists the known rules")

  test("the catalog has a description for every rule and no duplicate names"):
    assertEquals(Lint.names.distinct.size, Lint.names.size)
    assert(Lint.all.forall(_.description.trim.nonEmpty))
    assertEquals(Lint.names.size, 34)

  // ── suggestions ──────────────────────────────────────────────────────────────────────────────

  test("a short citekey suggests its full form first"):
    assertEquals(Lint.suggest(com.histmersion.api.library.Citekey("heggarty2023"), corpus, com.histmersion.api.checks.Lint.ItemLimit(3)), List("heggarty2023_language"))

  test("an ambiguous short citekey suggests all of them, shortest first"):
    assertEquals(Lint.suggest(com.histmersion.api.library.Citekey("knapp2016"), corpus, com.histmersion.api.checks.Lint.ItemLimit(3)), List("knapp2016_collapse", "knapp2016_seapeoples"))

  test("a typo suggests the key it shares a prefix with"):
    assertEquals(Lint.suggest(com.histmersion.api.library.Citekey("anthony2015_ringes"), corpus, com.histmersion.api.checks.Lint.ItemLimit(1)), List("anthony2015_ringe"))

  test("nothing close suggests nothing, and the cap is honoured"):
    assertEquals(Lint.suggest(com.histmersion.api.library.Citekey("zzz1999_nothing"), corpus, com.histmersion.api.checks.Lint.ItemLimit(3)), Nil)
    assertEquals(Lint.suggest(com.histmersion.api.library.Citekey("knapp2016"), corpus, com.histmersion.api.checks.Lint.ItemLimit(1)), List("knapp2016_collapse"))
    assertEquals(Lint.suggest(com.histmersion.api.library.Citekey("knapp2016"), corpus, com.histmersion.api.checks.Lint.ItemLimit(0)), Nil)
    assertEquals(Lint.suggest(com.histmersion.api.library.Citekey("   "), corpus, com.histmersion.api.checks.Lint.ItemLimit(3)), Nil)

  // ── dangling edges ───────────────────────────────────────────────────────────────────────────

  test("dangling-edges lists only targets that are not citekeys, with their closest matches"):
    val edges = List(
      edge("anthony2015_ringe", "heggarty2023_language"), // fine
      edge("anthony2015_ringe", "heggarty2023"), // short form
      edge("kassian2025_critique", "gone2001_missing")
    ) // truly dead
    val items = CitationLint.danglingEdges(edges, (corpus).map(com.histmersion.api.library.Citekey.apply), com.histmersion.api.checks.Lint.ItemLimit(3))
    assertEquals(items.size, 2)
    assert(
      items.exists(i => i.citekey == "anthony2015_ringe" && i.detail.contains("heggarty2023_language")),
      items.toString
    )
    assert(items.exists(i => i.citekey == "kassian2025_critique" && !i.detail.contains("closest")), items.toString)

  test("a target with a four-digit year is a source reference; a slug is an article link"):
    assert(Lint.looksLikeCitekey(com.histmersion.api.library.Citekey("gray_atkinson_2003")))
    assert(Lint.looksLikeCitekey(com.histmersion.api.library.Citekey("heggarty2023_language")))
    assert(!Lint.looksLikeCitekey(com.histmersion.api.library.Citekey("bronze_age_collapse")))
    assert(!Lint.looksLikeCitekey(com.histmersion.api.library.Citekey("oral_tradition")))

  test("citekey-shaped dangling targets sort FIRST, so the item cap keeps the actionable ones"):
    // the seeded corpus has 1 050 article links and exactly ONE dead source reference; the report
    // must not bury it.
    val edges = (1 to 5).toList.map(i => edge("s", s"concept_$i")) :+ edge("s", "gray_atkinson_2003")
    val items = CitationLint.danglingEdges(edges, (corpus).map(com.histmersion.api.library.Citekey.apply), com.histmersion.api.checks.Lint.ItemLimit(3))
    assert(items.head.detail.contains("gray_atkinson_2003"), items.head.detail)
    assert(items.head.detail.endsWith("(no such source)"), items.head.detail)
    assert(items(1).detail.contains("looks like a concept/article link"), items(1).detail)

  test("dangling-edges deduplicates and orders by (src, kind, dst)"):
    val e = edge("b_src", "gone", "cites")
    val items = CitationLint.danglingEdges(List(e, e, edge("a_src", "gone", "cites")), (corpus).map(com.histmersion.api.library.Citekey.apply), com.histmersion.api.checks.Lint.ItemLimit(1))
    assertEquals(items.map(_.citekey), List("a_src", "b_src"))

  test("unresolved-short-citekeys covers only prefixes of real citekeys, and flags the fixable ones"):
    val edges = List(
      edge("a", "heggarty2023"), // exactly one expansion → fixable
      edge("b", "knapp2016"), // two expansions → needs a human
      edge("c", "gone2001_missing")
    ) // not a short form at all
    val items = CitationLint.unresolvedShortCitekeys(edges, (corpus).map(com.histmersion.api.library.Citekey.apply), com.histmersion.api.checks.Lint.ItemLimit(3))
    assertEquals(items.map(_.citekey), List("a", "b"))
    assert(items.head.detail.contains("resolves to heggarty2023_language"), items.head.detail)
    assert(items.head.detail.contains("lint --fix"))
    assert(items(1).detail.contains("ambiguous between knapp2016_collapse, knapp2016_seapeoples"), items(1).detail)

  // ── duplicates ───────────────────────────────────────────────────────────────────────────────

  test("duplicates groups case- and whitespace-insensitively, naming the partners"):
    val items = Lint.duplicates(List("a" -> "10.1/X", "b" -> " 10.1/x ", "c" -> "10.2/y"), "doi")
    assertEquals(items.map(_.citekey), List("a", "b"))
    assert(items.head.detail.contains("also on b"), items.head.detail)
    assert(items(1).detail.contains("also on a"), items(1).detail)

  test("a key repeated for the SAME citekey is not a duplicate"):
    assertEquals(Lint.duplicates(List("a" -> "10.1/x", "a" -> "10.1/x"), "doi"), Nil)

  test("blank keys are skipped (an unpopulated sha256 column reports nothing)"):
    assertEquals(Lint.duplicates(List("a" -> "", "b" -> "  "), "sha256"), Nil)

  test("blank() is true for absent and whitespace-only values"):
    assert(Lint.blank(None) && Lint.blank(Some("")) && Lint.blank(Some("  \n ")))
    assert(!Lint.blank(Some("x")))

  // ── report assembly and rendering ────────────────────────────────────────────────────────────

  private val rule = Lint.rule(NoFindings).get

  test("count is the true total; items are capped"):
    val items = (1 to 10).toList.map(i => LintItem(s"s$i", com.histmersion.api.checks.Lint.LintItem.Detail("no findings")))
    val r = Lint.report(rule, items, com.histmersion.api.checks.Lint.ItemLimit(3))
    assertEquals(r.count, 10)
    assertEquals(r.items.size, 3)

  test("Markdown groups by rule, shows counts and folds the clean rules into one line"):
    val rep = LintReport(
      List(
        Lint.report(rule, List(LintItem(com.histmersion.api.library.Citekey("a"), com.histmersion.api.checks.Lint.LintItem.Detail("x")), LintItem(com.histmersion.api.library.Citekey("b"), com.histmersion.api.checks.Lint.LintItem.Detail("y"))), com.histmersion.api.checks.Lint.ItemLimit(1)),
        Lint.report(Lint.rule(NoPeriod).get, Nil, com.histmersion.api.checks.Lint.ItemLimit(50))
      )
    )
    val md = Lint.markdown(rep)
    assert(md.contains("### Lint: 2 finding(s) across 1 of 2 rule(s)"), md)
    assert(md.contains("#### no-findings (2)"), md)
    assert(md.contains("- **a** — x"), md)
    assert(md.contains("… and 1 more"), md)
    assert(md.contains("_clean: no-period_"), md)

  test("a clean corpus says so"):
    val md = Lint.markdown(LintReport(List(Lint.report(rule, Nil, com.histmersion.api.checks.Lint.ItemLimit(50)))))
    assert(md.startsWith("### Lint: clean (1 rule(s) checked)"), md)

  test("applied fixes are reported above the verdict"):
    val md = Lint.markdown(LintReport(List(Lint.report(rule, Nil, com.histmersion.api.checks.Lint.ItemLimit(50))), List(com.histmersion.api.checks.Lint.LintReport.Fixed("resolve-edges: 3"))))
    assert(md.startsWith("- fixed: resolve-edges: 3"), md)

  // ── wave 3 rules ─────────────────────────────────────────────────────────────────────────────

  test(
    "the catalog now has 34 rules (13 original + 6 wave-3 + 2 UX + superseded-without-edge + article-critique-pairing + 2 formatting + finding-geocode + finding-unplaced + place-kind + place-grid-point + place-imported-point-area + finding-placeholder + media-licence + event-correction-conflict) with no duplicates"
  ):
    assertEquals(Lint.names.distinct.size, Lint.names.size)
    assertEquals(Lint.all.size, 34)
    assert(Lint.all.forall(_.description.trim.nonEmpty))

  test("isCanonicalTag accepts lowercase_snake_case"):
    assert(Lint.isCanonicalTag(com.histmersion.api.checks.Lint.Tag("sea_peoples")))
    assert(Lint.isCanonicalTag(com.histmersion.api.checks.Lint.Tag("bronze_age")))
    assert(Lint.isCanonicalTag(com.histmersion.api.checks.Lint.Tag("a1")))
    assert(!Lint.isCanonicalTag(com.histmersion.api.checks.Lint.Tag("Sea-Peoples")))
    assert(!Lint.isCanonicalTag(com.histmersion.api.checks.Lint.Tag("BRONZE_AGE")))
    assert(!Lint.isCanonicalTag(com.histmersion.api.checks.Lint.Tag("sea peoples")))
    assert(!Lint.isCanonicalTag(com.histmersion.api.checks.Lint.Tag("sea-peoples")))

  test("normaliseTag converts to canonical form"):
    assertEquals(Lint.normaliseTag(com.histmersion.api.checks.Lint.Tag("Sea-Peoples")), "sea_peoples")
    assertEquals(Lint.normaliseTag(com.histmersion.api.checks.Lint.Tag("BRONZE AGE")), "bronze_age")
    assertEquals(Lint.normaliseTag(com.histmersion.api.checks.Lint.Tag(" sea  peoples ")), "sea_peoples")
    assertEquals(Lint.normaliseTag(com.histmersion.api.checks.Lint.Tag("sea_peoples")), "sea_peoples")

  test("nonCanonicalTags flags tags not in lowercase_snake_case"):
    val items = Lint.nonCanonicalTags(
      List((com.histmersion.api.library.Citekey("a"), List(com.histmersion.api.checks.Lint.Tag("sea_peoples"), com.histmersion.api.checks.Lint.Tag("Bronze-Age"))), (com.histmersion.api.library.Citekey("b"), List(com.histmersion.api.checks.Lint.Tag("good_tag"))), (com.histmersion.api.library.Citekey("c"), List(com.histmersion.api.checks.Lint.Tag("BAD TAG"))))
    )
    assertEquals(items.map(_.citekey), List("a", "c"))
    assert(items.head.detail.contains("Bronze-Age"))
    assert(items.head.detail.contains("bronze_age"))

  test("yearCitekeyMismatch flags when citekey year != stored year"):
    val items = Lint.yearCitekeyMismatch(
      List(
        (com.histmersion.api.library.Citekey("heggarty2023_language"), Some(com.histmersion.api.catalogue.Year(2023))), // fine
        (com.histmersion.api.library.Citekey("gray2003_language"), Some(com.histmersion.api.catalogue.Year(2004))), // mismatch
        (com.histmersion.api.library.Citekey("noyr_book"), Some(com.histmersion.api.catalogue.Year(2010))), // no year in citekey
        (com.histmersion.api.library.Citekey("chang2015"), None)
      )
    ) // no stored year
    assertEquals(items.size, 1)
    assertEquals(items.head.citekey, "gray2003_language")
    assert(items.head.detail.contains("2003"))
    assert(items.head.detail.contains("2004"))

  test("authorPipe flags authors with pipe characters"):
    val items = Lint.authorPipe(
      List((com.histmersion.api.library.Citekey("a"), List(com.histmersion.api.library.AuthorName("Eric H. Cline"))), (com.histmersion.api.library.Citekey("b"), List(com.histmersion.api.library.AuthorName("Cline| Eric H."))), (com.histmersion.api.library.Citekey("c"), List(com.histmersion.api.library.AuthorName("Good Author"), com.histmersion.api.library.AuthorName("Bad| Author"))))
    )
    assertEquals(items.map(_.citekey), List("b", "c"))
    assert(items.head.detail.contains("Cline| Eric H."))

  test("articleFiller flags banned phrases"):
    val items = Article.Lint.articleFiller(
      List(
        (com.histmersion.api.research.Article.Slug("clean"), com.histmersion.api.research.Article.Body("The sources agree on this point.")),
        (com.histmersion.api.research.Article.Slug("bad"), "It is worth noting that the evidence is clear. Interestingly, this sheds light on...")
      )
    )
    assertEquals(items.size, 1)
    assertEquals(items.head.citekey, "bad")
    assert(items.head.detail.contains("filler:"))

  test("articleUncitedParagraphs flags paragraphs with no [[citekey]]"):
    val bodyOk = """# Title
                   |
                   |The evidence from [[cline2014_year]] supports this claim.
                   |
                   |Further analysis in [[haak2015_massive]] confirms the pattern.""".stripMargin
    val bodyBad = """# Title
                    |
                    |The evidence supports this claim.
                    |
                    |Further analysis in [[haak2015_massive]] confirms the pattern.""".stripMargin
    assertEquals(Article.Lint.articleUncitedParagraphs(List((com.histmersion.api.research.Article.Slug("ok"), bodyOk))), Nil)
    val items = Article.Lint.articleUncitedParagraphs(List((com.histmersion.api.research.Article.Slug("bad"), bodyBad)))
    assertEquals(items.size, 1)
    assertEquals(items.head.citekey, "bad")
    assert(items.head.detail.contains("1 paragraph(s)"))

  test("extractParagraphs skips headings, lists, code fences"):
    val body = """# Heading
                 |
                 |Prose paragraph one.
                 |
                 |- list item
                 |
                 |> blockquote
                 |
                 |```
                 |code block
                 |```
                 |
                 |Prose paragraph two.""".stripMargin
    val paras = Lint.extractParagraphs(body)
    assertEquals(paras.size, 2)
    assert(paras.head.contains("Prose paragraph one"))
    assert(paras(1).contains("Prose paragraph two"))

  test(
    "extractParagraphs: a short lead-in ending in ':' before a list is not a paragraph; anything else ending in ':' is"
  ):
    // the fixer's Not-used intro on theseus_origins (run dd1de366), rejected twice as uncited
    val leadIn = "These sources mention Theseus only in their full text, and their records carry no finding on him:"
    def paras(b: String) = Lint.extractParagraphs(b)
    assertEquals(paras(s"## Not used\n\n$leadIn\n\n- [[a2001]] — no finding\n- [[b2002]] — no finding"), Nil)
    assertEquals(paras(s"$leadIn\n- [[a2001]] — directly under it"), Nil)
    assertEquals(paras(s"$leadIn\n\n1. [[a2001]] — a numbered list"), Nil)
    // not followed by a list: a heading, a paragraph, the end of the text
    assertEquals(paras(s"$leadIn\n\n## Next\n\n- [[a2001]]"), List(leadIn))
    assertEquals(paras(s"$leadIn\n\nAnother paragraph [[a2001]]."), List(leadIn, "Another paragraph [[a2001]]."))
    assertEquals(paras(leadIn), List(leadIn))
    // too long to be a lead-in: a paragraph of claims that happens to end in ':'
    val long = ("Theseus was a Mycenaean king of Athens. " * 10) + "as follows:"
    assertEquals(paras(s"$long\n\n- [[a2001]]"), List(long))
    // two sentences are a paragraph of claims, however short (#937: one sentence, then the list)
    val two = "Theseus was a king of Athens. These sources say so:"
    assertEquals(paras(s"$two\n\n- [[a2001]]"), List(two))
    // and the lint rule agrees
    assertEquals(Article.Lint.articleUncitedParagraphs(List("t" -> s"$leadIn\n\n- [[a2001]]")), Nil)
    assertEquals(Article.Lint.articleUncitedParagraphs(List("t" -> s"$two\n\n- [[a2001]]")).size, 1)

  test("sentenceCount: abbreviations, initials and dates do not end a sentence"):
    assertEquals(Lint.sentenceCount(""), 0)
    assertEquals(Lint.sentenceCount("One sentence:"), 1)
    assertEquals(Lint.sentenceCount("Destroyed c. 1200 BCE, e.g. Pylos and Mt. Ida, as A. Evans held:"), 1)
    assertEquals(Lint.sentenceCount("Sources dated 1200 BCE. These others:"), 2)
    assertEquals(Lint.sentenceCount("Is it so? Yes. [[a2001]] says it."), 3)
    assertEquals(Lint.sentenceCount("A claim (\"quoted.\") Then more:"), 2)
    assert(Lint.isListLeadIn("The sources not used:"))
    assert(!Lint.isListLeadIn("The sources not used."))
    assert(!Lint.isListLeadIn("A claim. The sources not used:"))

  test("article-thin-summary flags missing, stand-in and label-length summaries, not real ones"):
    val real =
      "Hephaestus, the lame divine smith, has no Indo-European etymology and is the only major Olympian absent from Linear B."
    val items = Article.Lint.articleThinSummary(
      List(
        "a_missing" -> "",
        "b_placeholder" -> "placeholder",
        "c_chatter" -> "Origin article for the Greek god origins program.",
        "d_real" -> real
      )
    )
    assertEquals(items.map(_.citekey), List("a_missing", "b_placeholder", "c_chatter"))
    assert(items(1).detail.contains("stand-in"), items(1).detail)
    assert(items(2).detail.contains("49 chars"), items(2).detail)

  // ── formatting rules (the check itself is the server's MarkdownLint; here, the item shape) ────

  test("article-markdown / note-markdown: one item per problem, by id then field, an article body checked as one"):
    val seen = List.newBuilder[(FormatField, Boolean)]
    val check: FormatChecker = (text, field, article) =>
      seen += ((field, article))
      if text.contains("**") then
        List(FormatIssue(FormatRule.Emphasis, FormatSeverity.Error, field, com.histmersion.api.checks.FormatIssue.Line(2), com.histmersion.api.checks.FormatIssue.Column(5), com.histmersion.api.checks.FormatIssue.Excerpt("a ** b"), com.histmersion.api.checks.FormatIssue.Message("unpaired"), Some(com.histmersion.api.checks.FormatIssue.Fix("trim"))))
      else Nil
    val items = Lint.formatItems(
      List(
        (com.histmersion.api.library.Citekey("zeta"), FormatField.Summary, com.histmersion.api.checks.Lint.FormatItemDetail("fine")),
        (com.histmersion.api.library.Citekey("alpha"), FormatField.Summary, com.histmersion.api.checks.Lint.FormatItemDetail("x ** y")),
        (com.histmersion.api.library.Citekey("alpha"), FormatField.Body, com.histmersion.api.checks.Lint.FormatItemDetail("a ** b")),
        (com.histmersion.api.library.Citekey("beta"), FormatField.Notes, com.histmersion.api.checks.Lint.FormatItemDetail("   "))
      ),
      check
    )
    assertEquals(items.map(_.citekey), List("alpha", "alpha"))
    assert(items.head.detail.startsWith("body L2:5 error emphasis — unpaired"), items.head.detail)
    assert(items(1).detail.startsWith("summary L2:5"), items(1).detail)
    assert(items.head.detail.contains("fix: trim"))
    // blank texts are not checked at all; only a body is checked as an article
    assertEquals(seen.result().toSet, Set((FormatField.Body, true), (FormatField.Summary, false)))
    assert(Lint.rule(ArticleFormat).nonEmpty && Lint.rule(NoteFormat).nonEmpty)
