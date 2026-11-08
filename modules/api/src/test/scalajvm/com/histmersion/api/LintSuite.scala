package com.histmersion
package api

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.library.CitationLint

import com.histmersion.api.checks.Lint.*
import com.histmersion.api.checks.{FormatField, FormatIssue, FormatRule, FormatSeverity, Lint}
import com.histmersion.api.library.{Citekey, Edge}
import com.histmersion.api.research.Article
import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.AuthorName

/** The lint classifiers (plan §5.2 stream 2.8) — everything that decides whether a row offends and what the report says
  * about it, with no database in sight.
  */
class LintSuite extends munit.FunSuite:

  private val corpus = Set(
    Citekey("heggarty2023_language"),
    Citekey("anthony2015_ringe"),
    Citekey("kassian2025_critique"),
    Citekey("knapp2016_collapse"),
    Citekey("knapp2016_seapeoples")
  )

  private def edge(src: Citekey, dst: Citekey, kind: Edge.Kind = Edge.Kind.References) = Edge(src, dst, kind)

  // ── rule selection ───────────────────────────────────────────────────────────────────────────

  test("no rules requested = every rule"):
    assertEquals(Lint.select(Nil).map(_.map(_.name)), Right(Lint.names))

  test("a subset is honoured, deduplicated, and blanks are ignored"):
    assertEquals(
      Lint
        .select(List(Rule.Name("no-findings"), Rule.Name(" "), Rule.Name("no-findings"), Rule.Name("no-period")))
        .map(_.map(_.name)),
      Right(List(NoFindings, NoPeriod))
    )

  test("an unknown rule is an error, never a silently clean report"):
    val e = Lint.select(List(Rule.Name("no-findings"), Rule.Name("no-vibes"))).fold(_.value, _ => "")
    assert(e.contains("no-vibes"), e)
    assert(e.contains("no-findings"), "the error lists the known rules")

  test("the catalog has a description for every rule and no duplicate names"):
    assertEquals(Lint.names.distinct.size, Lint.names.size)
    assert(Lint.all.forall(_.description.value.trim.nonEmpty))
    assertEquals(Lint.names.size, 34)

  // ── suggestions ──────────────────────────────────────────────────────────────────────────────

  test("a short citekey suggests its full form first"):
    assertEquals(Lint.suggest(Citekey("heggarty2023"), corpus, ItemLimit(3)), List("heggarty2023_language"))

  test("an ambiguous short citekey suggests all of them, shortest first"):
    assertEquals(
      Lint.suggest(Citekey("knapp2016"), corpus, ItemLimit(3)),
      List("knapp2016_collapse", "knapp2016_seapeoples")
    )

  test("a typo suggests the key it shares a prefix with"):
    assertEquals(Lint.suggest(Citekey("anthony2015_ringes"), corpus, ItemLimit(1)), List("anthony2015_ringe"))

  test("nothing close suggests nothing, and the cap is honoured"):
    assertEquals(Lint.suggest(Citekey("zzz1999_nothing"), corpus, ItemLimit(3)), Nil)
    assertEquals(Lint.suggest(Citekey("knapp2016"), corpus, ItemLimit(1)), List("knapp2016_collapse"))
    assertEquals(Lint.suggest(Citekey("knapp2016"), corpus, ItemLimit(0)), Nil)
    assertEquals(Lint.suggest(Citekey("   "), corpus, ItemLimit(3)), Nil)

  // ── dangling edges ───────────────────────────────────────────────────────────────────────────

  test("dangling-edges lists only targets that are not citekeys, with their closest matches"):
    val edges = List(
      edge(Citekey("anthony2015_ringe"), Citekey("heggarty2023_language")), // fine
      edge(Citekey("anthony2015_ringe"), Citekey("heggarty2023")), // short form
      edge(Citekey("kassian2025_critique"), Citekey("gone2001_missing"))
    ) // truly dead
    val items = CitationLint.danglingEdges(edges, corpus, ItemLimit(3))
    assertEquals(items.size, 2)
    assert(
      items.exists(i => i.citekey.value == "anthony2015_ringe" && i.detail.value.contains("heggarty2023_language")),
      items.toString
    )
    assert(
      items.exists(i => i.citekey.value == "kassian2025_critique" && !i.detail.value.contains("closest")),
      items.toString
    )

  test("a target with a four-digit year is a source reference; a slug is an article link"):
    assert(Lint.looksLikeCitekey(Citekey("gray_atkinson_2003")))
    assert(Lint.looksLikeCitekey(Citekey("heggarty2023_language")))
    assert(!Lint.looksLikeCitekey(Citekey("bronze_age_collapse")))
    assert(!Lint.looksLikeCitekey(Citekey("oral_tradition")))

  test("citekey-shaped dangling targets sort FIRST, so the item cap keeps the actionable ones"):
    // the seeded corpus has 1 050 article links and exactly ONE dead source reference; the report
    // must not bury it.
    val edges = (1 to 5).toList.map(i => edge(Citekey("s"), Citekey(s"concept_$i"))) :+ edge(
      Citekey("s"),
      Citekey("gray_atkinson_2003")
    )
    val items = CitationLint.danglingEdges(edges, corpus, ItemLimit(3))
    assert(items.head.detail.value.contains("gray_atkinson_2003"), items.head.detail)
    assert(items.head.detail.value.endsWith("(no such source)"), items.head.detail)
    assert(items(1).detail.value.contains("looks like a concept/article link"), items(1).detail)

  test("dangling-edges deduplicates and orders by (src, kind, dst)"):
    val e = edge(Citekey("b_src"), Citekey("gone"), Edge.Kind.Cites)
    val items = CitationLint.danglingEdges(
      List(e, e, edge(Citekey("a_src"), Citekey("gone"), Edge.Kind.Cites)),
      corpus,
      ItemLimit(1)
    )
    assertEquals(items.map(_.citekey), List("a_src", "b_src"))

  test("unresolved-short-citekeys covers only prefixes of real citekeys, and flags the fixable ones"):
    val edges = List(
      edge(Citekey("a"), Citekey("heggarty2023")), // exactly one expansion → fixable
      edge(Citekey("b"), Citekey("knapp2016")), // two expansions → needs a human
      edge(Citekey("c"), Citekey("gone2001_missing"))
    ) // not a short form at all
    val items = CitationLint.unresolvedShortCitekeys(edges, corpus, ItemLimit(3))
    assertEquals(items.map(_.citekey), List("a", "b"))
    assert(items.head.detail.value.contains("resolves to heggarty2023_language"), items.head.detail)
    assert(items.head.detail.value.contains("lint --fix"))
    assert(
      items(1).detail.value.contains("ambiguous between knapp2016_collapse, knapp2016_seapeoples"),
      items(1).detail
    )

  // ── duplicates ───────────────────────────────────────────────────────────────────────────────

  test("duplicates groups case- and whitespace-insensitively, naming the partners"):
    val items = Lint.duplicates(
      List(
        Citekey("a") -> Lint.DuplicateKey("10.1/X"),
        Citekey("b") -> Lint.DuplicateKey(" 10.1/x "),
        Citekey("c") -> Lint.DuplicateKey("10.2/y")
      ),
      "doi"
    )
    assertEquals(items.map(_.citekey), List("a", "b"))
    assert(items.head.detail.value.contains("also on b"), items.head.detail)
    assert(items(1).detail.value.contains("also on a"), items(1).detail)

  test("a key repeated for the SAME citekey is not a duplicate"):
    assertEquals(
      Lint.duplicates(
        List(Citekey("a") -> Lint.DuplicateKey("10.1/x"), Citekey("a") -> Lint.DuplicateKey("10.1/x")),
        "doi"
      ),
      Nil
    )

  test("blank keys are skipped (an unpopulated sha256 column reports nothing)"):
    assertEquals(
      Lint.duplicates(List(Citekey("a") -> Lint.DuplicateKey(""), Citekey("b") -> Lint.DuplicateKey("  ")), "sha256"),
      Nil
    )

  test("blank() is true for absent and whitespace-only values"):
    assert(Lint.blank(None) && Lint.blank(Some("")) && Lint.blank(Some("  \n ")))
    assert(!Lint.blank(Some("x")))

  // ── report assembly and rendering ────────────────────────────────────────────────────────────

  private val rule = Lint.rule(Lint.Rule.Name(NoFindings)).get

  test("count is the true total; items are capped"):
    val items = (1 to 10).toList.map(i => LintItem(Citekey(s"s$i"), LintItem.Detail("no findings")))
    val r = Lint.report(rule, items, ItemLimit(3))
    assertEquals(r.count, 10)
    assertEquals(r.items.size, 3)

  test("Markdown groups by rule, shows counts and folds the clean rules into one line"):
    val rep = LintReport(
      List(
        Lint.report(
          rule,
          List(LintItem(Citekey("a"), LintItem.Detail("x")), LintItem(Citekey("b"), LintItem.Detail("y"))),
          ItemLimit(1)
        ),
        Lint.report(Lint.rule(Lint.Rule.Name(NoPeriod)).get, Nil, ItemLimit(50))
      )
    )
    val md = Lint.markdown(rep)
    assert(md.value.contains("### Lint: 2 finding(s) across 1 of 2 rule(s)"), md)
    assert(md.value.contains("#### no-findings (2)"), md)
    assert(md.value.contains("- **a** — x"), md)
    assert(md.value.contains("… and 1 more"), md)
    assert(md.value.contains("_clean: no-period_"), md)

  test("a clean corpus says so"):
    val md = Lint.markdown(LintReport(List(Lint.report(rule, Nil, ItemLimit(50)))))
    assert(md.value.startsWith("### Lint: clean (1 rule(s) checked)"), md)

  test("applied fixes are reported above the verdict"):
    val md =
      Lint.markdown(LintReport(List(Lint.report(rule, Nil, ItemLimit(50))), List(LintReport.Fixed("resolve-edges: 3"))))
    assert(md.value.startsWith("- fixed: resolve-edges: 3"), md)

  // ── wave 3 rules ─────────────────────────────────────────────────────────────────────────────

  test(
    "the catalog now has 34 rules (13 original + 6 wave-3 + 2 UX + superseded-without-edge + article-critique-pairing + 2 formatting + finding-geocode + finding-unplaced + place-kind + place-grid-point + place-imported-point-area + finding-placeholder + media-licence + event-correction-conflict) with no duplicates"
  ):
    assertEquals(Lint.names.distinct.size, Lint.names.size)
    assertEquals(Lint.all.size, 34)
    assert(Lint.all.forall(_.description.value.trim.nonEmpty))

  test("isCanonicalTag accepts lowercase_snake_case"):
    assert(Lint.isCanonicalTag(Tag("sea_peoples")))
    assert(Lint.isCanonicalTag(Tag("bronze_age")))
    assert(Lint.isCanonicalTag(Tag("a1")))
    assert(!Lint.isCanonicalTag(Tag("Sea-Peoples")))
    assert(!Lint.isCanonicalTag(Tag("BRONZE_AGE")))
    assert(!Lint.isCanonicalTag(Tag("sea peoples")))
    assert(!Lint.isCanonicalTag(Tag("sea-peoples")))

  test("normaliseTag converts to canonical form"):
    assertEquals(Lint.normaliseTag(Tag("Sea-Peoples")), "sea_peoples")
    assertEquals(Lint.normaliseTag(Tag("BRONZE AGE")), "bronze_age")
    assertEquals(Lint.normaliseTag(Tag(" sea  peoples ")), "sea_peoples")
    assertEquals(Lint.normaliseTag(Tag("sea_peoples")), "sea_peoples")

  test("nonCanonicalTags flags tags not in lowercase_snake_case"):
    val items = Lint.nonCanonicalTags(
      List(
        (Citekey("a"), List(Tag("sea_peoples"), Tag("Bronze-Age"))),
        (Citekey("b"), List(Tag("good_tag"))),
        (Citekey("c"), List(Tag("BAD TAG")))
      )
    )
    assertEquals(items.map(_.citekey), List("a", "c"))
    assert(items.head.detail.value.contains("Bronze-Age"))
    assert(items.head.detail.value.contains("bronze_age"))

  test("yearCitekeyMismatch flags when citekey year != stored year"):
    val items = Lint.yearCitekeyMismatch(
      List(
        (Citekey("heggarty2023_language"), Some(Year(2023))), // fine
        (Citekey("gray2003_language"), Some(Year(2004))), // mismatch
        (Citekey("noyr_book"), Some(Year(2010))), // no year in citekey
        (Citekey("chang2015"), None)
      )
    ) // no stored year
    assertEquals(items.size, 1)
    assertEquals(items.head.citekey, "gray2003_language")
    assert(items.head.detail.value.contains("2003"))
    assert(items.head.detail.value.contains("2004"))

  test("authorPipe flags authors with pipe characters"):
    val items = Lint.authorPipe(
      List(
        (Citekey("a"), List(AuthorName("Eric H. Cline"))),
        (Citekey("b"), List(AuthorName("Cline| Eric H."))),
        (Citekey("c"), List(AuthorName("Good Author"), AuthorName("Bad| Author")))
      )
    )
    assertEquals(items.map(_.citekey), List("b", "c"))
    assert(items.head.detail.value.contains("Cline| Eric H."))

  test("articleFiller flags banned phrases"):
    val items = Article.Lint.articleFiller(
      List(
        (Article.Slug("clean"), Article.Body("The sources agree on this point.")),
        (
          Article.Slug("bad"),
          Article.Body("It is worth noting that the evidence is clear. Interestingly, this sheds light on...")
        )
      )
    )
    assertEquals(items.size, 1)
    assertEquals(items.head.citekey, "bad")
    assert(items.head.detail.value.contains("filler:"))

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
    assertEquals(Article.Lint.articleUncitedParagraphs(List((Article.Slug("ok"), Article.Body(bodyOk)))), Nil)
    val items = Article.Lint.articleUncitedParagraphs(List((Article.Slug("bad"), Article.Body(bodyBad))))
    assertEquals(items.size, 1)
    assertEquals(items.head.citekey, "bad")
    assert(items.head.detail.value.contains("1 paragraph(s)"))

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
    val paras = Lint.extractParagraphs(Lint.ProseBody(body))
    assertEquals(paras.size, 2)
    assert(paras.head.value.contains("Prose paragraph one"))
    assert(paras(1).value.contains("Prose paragraph two"))

  test(
    "extractParagraphs: a short lead-in ending in ':' before a list is not a paragraph; anything else ending in ':' is"
  ):
    // the fixer's Not-used intro on theseus_origins (run dd1de366), rejected twice as uncited
    val leadIn = "These sources mention Theseus only in their full text, and their records carry no finding on him:"
    def paras(b: String) = Lint.extractParagraphs(Lint.ProseBody(b))
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
    assertEquals(
      Article.Lint.articleUncitedParagraphs(List(Article.Slug("t") -> Article.Body(s"$leadIn\n\n- [[a2001]]"))),
      Nil
    )
    assertEquals(
      Article.Lint.articleUncitedParagraphs(List(Article.Slug("t") -> Article.Body(s"$two\n\n- [[a2001]]"))).size,
      1
    )

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
        Article.Slug("a_missing") -> Article.SummaryText(""),
        Article.Slug("b_placeholder") -> Article.SummaryText("placeholder"),
        Article.Slug("c_chatter") -> Article.SummaryText("Origin article for the Greek god origins program."),
        Article.Slug("d_real") -> Article.SummaryText(real)
      )
    )
    assertEquals(items.map(_.citekey), List("a_missing", "b_placeholder", "c_chatter"))
    assert(items(1).detail.value.contains("stand-in"), items(1).detail)
    assert(items(2).detail.value.contains("49 chars"), items(2).detail)

  // ── formatting rules (the check itself is the server's MarkdownLint; here, the item shape) ────

  test("article-markdown / note-markdown: one item per problem, by id then field, an article body checked as one"):
    val seen = List.newBuilder[(FormatField, Boolean)]
    val check: FormatChecker = (text, field, article) =>
      seen += ((field, article))
      if text.contains("**") then
        List(
          FormatIssue(
            FormatRule.Emphasis,
            FormatSeverity.Error,
            field,
            FormatIssue.Line(2),
            FormatIssue.Column(5),
            FormatIssue.Excerpt("a ** b"),
            FormatIssue.Message("unpaired"),
            Some(FormatIssue.Fix("trim"))
          )
        )
      else Nil
    val items = Lint.formatItems(
      List(
        (Citekey("zeta"), FormatField.Summary, FormatItemDetail("fine")),
        (Citekey("alpha"), FormatField.Summary, FormatItemDetail("x ** y")),
        (Citekey("alpha"), FormatField.Body, FormatItemDetail("a ** b")),
        (Citekey("beta"), FormatField.Notes, FormatItemDetail("   "))
      ),
      check
    )
    assertEquals(items.map(_.citekey), List("alpha", "alpha"))
    assert(items.head.detail.value.startsWith("body L2:5 error emphasis — unpaired"), items.head.detail)
    assert(items(1).detail.value.startsWith("summary L2:5"), items(1).detail)
    assert(items.head.detail.value.contains("fix: trim"))
    // blank texts are not checked at all; only a body is checked as an article
    assertEquals(seen.result().toSet, Set((FormatField.Body, true), (FormatField.Summary, false)))
    assert(Lint.rule(Lint.Rule.Name(ArticleFormat)).nonEmpty && Lint.rule(Lint.Rule.Name(NoteFormat)).nonEmpty)
