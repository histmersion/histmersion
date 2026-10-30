package com.histmersion.server

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import MarkdownLint.Context
import com.histmersion.values.SourceStatus

/** The formatting lint, pure: real-looking article and note Markdown in, findings out — and, for each problem the lint
  * calls an error, the rendered HTML to prove the page really does get it wrong (the lint parses with the page's own
  * flexmark configuration, so the two must agree).
  */
class MarkdownLintSuite extends munit.FunSuite:

  private val article = Context(article = true)
  private def body(md: String, ctx: Context = article) = MarkdownLint.issues(md, FormatField.Body, ctx)
  private def rules(md: String, ctx: Context = article): List[FormatRule] = body(md, ctx).map(_.rule)
  private def fixed(md: String, ctx: Context = article): String = MarkdownLint.fix(md, FormatField.Body, ctx)._1
  private def html(md: String): String = MarkdownHtml.render(md)

  /** A clean article in the corpus's own conventions: `# Title`, sections, `[[citekey]]` citations (with labels, in
    * bold, in a Glossary TABLE with a `|label`), a `_Method_` line with snake_case citekeys, starred reconstructions
    * written as literals, a numbered list, a quote.
    */
  private val clean =
    """# Sea Peoples
      |
      |## Summary
      |
      |The Sea Peoples attacked Egypt around 1177 BCE [[cline2014_year]]. Drews argues for a military
      |explanation [[drews1993_end|Drews 1993]] (contested; challenged by [[cline2014]] in 2014).
      |
      |## What the sources say
      |
      |1. **Medinet Habu.** The reliefs show ships [[cline2014_year]].
      |2. **Ugarit.** The letters ask for help [[yon1989_ugarit]].
      |
      |The reconstructed sky god \*Dyēus and the form \*deiwos are cited as literals [[west2007_indo]].
      |Italic forms like *a/e/o*RC and single-*l* stems are fine [[nikolaev2007_achilles]].
      |
      |> "The year civilization collapsed" — the book's title [[cline2014_year]].
      |
      |## Not used
      |
      |- **[[frazer1922_goldenbough]]**: superseded method.
      |
      |## Glossary
      |
      || Term | Meaning |
      ||---|---|
      || **Wanax** | the Mycenaean king's title [[palaima1995_nature|Palaima]] |
      || *Tephra* | volcanic ash; dated layers link sites |
      |
      |_Method: search {place:"Aegean"} → frazer1922_goldenbough, harrison1912_themis; statuses checked._
      |""".stripMargin

  test("an article in the corpus's own conventions has no formatting problem"):
    assertEquals(body(clean), Nil)
    // and the Glossary table really renders as a table, wikilink label and all
    assert(html(Pages.linkProse(clean, _ => None)).contains("<table>"))

  test("[[citekey]] and [[slug]] links are the corpus's link syntax, never a problem"):
    val md = "Text [[haak2015_massive]], [[bronze_age_collapse]], [[knapp2016]] and [[haak2015_massive|Haak]].\n"
    assertEquals(body(md), Nil)
    assertEquals(MarkdownLint.issues(md, FormatField.Notes), Nil)

  // ── the user's four examples ───────────────────────────────────────────────────────────────

  test("example 1: a table right under a paragraph does not render — and --fix adds the blank line"):
    val md =
      """## Festivals
        |The four festivals mark the quarters [[koch2006_celtic]]:
        || Festival | Date |
        ||---|---|
        || Samain | 1 November |
        |""".stripMargin
    assert(!html(md).contains("<table>"), "the page shows pipes, which is what the lint reports")
    val is = body(md)
    assertEquals(is.map(_.rule), List(FormatRule.TableNotRendered))
    assertEquals(is.head.severity, FormatSeverity.Error)
    assertEquals((is.head.line, is.head.column), (3, 1))
    assert(is.head.message.contains("no blank line"), is.head.message)
    val f = fixed(md)
    assert(html(f).contains("<table>"), f)
    assertEquals(body(f), Nil)

  test("example 1: a table with no delimiter row, or a delimiter row of the wrong width, is shown as text"):
    val noDelim = "| Term | Meaning |\n| Wanax | king |\n| Lawagetas | leader |\n"
    assert(!html(noDelim).contains("<table>"))
    assertEquals(rules(noDelim), List(FormatRule.TableNotRendered))
    assert(body(noDelim).head.message.contains("delimiter row"))
    val narrow = "| Term | Meaning | Source |\n|---|---|\n| Wanax | king | Linear B |\n"
    assert(!html(narrow).contains("<table>"))
    val m = body(narrow).head
    assertEquals(m.rule, FormatRule.TableNotRendered)
    assert(m.message.contains("3 cells") && m.message.contains("2"), m.message)

  test("a table row with extra cells (an unescaped pipe) or split across lines"):
    val md =
      """| Site | Date |
        ||---|---|
        || Ugarit | 1185 BCE | burnt |
        || Pylos |
        |destroyed about 1180 BCE
        |""".stripMargin
    val is = body(md)
    assertEquals(is.map(_.rule), List(FormatRule.TableColumns, FormatRule.TableColumns, FormatRule.TableColumns))
    assert(is.head.message.contains("dropped"), is.head.message)
    assertEquals(is(2).severity, FormatSeverity.Error) // the continuation line lands under the table
    // a `[[x|label]]` inside a cell is NOT an extra cell: the page rewrites the link before parsing
    assertEquals(body("| A | B |\n|---|---|\n| [[cline2014_year|Cline]] | x |\n"), Nil)

  test("example 2: bold that does not pair — a space inside, an odd count — shows literally"):
    val md = "The ** Medinet Habu** reliefs and **Ugarit letters are primary [[cline2014_year]].\n"
    assert(html(md).contains("**"), html(md))
    val is = body(md)
    assertEquals(is.map(_.rule), List(FormatRule.Emphasis))
    assertEquals(is.head.severity, FormatSeverity.Error)
    val spaced = "The ** Medinet Habu** reliefs are primary [[cline2014_year]].\n"
    assertEquals(fixed(spaced), "The **Medinet Habu** reliefs are primary [[cline2014_year]].\n")
    assert(html(fixed(spaced)).contains("<strong>Medinet Habu</strong>"))

  test("example 2: starred reconstructions inside bold or between forms turn into italics"):
    // all four are live corpus lines; the page renders `*<em>…<em>…</em></em>`
    val lines = List(
      "- **Why no Greek *Yemo?** The PIE creation-by-sacrifice [[lincoln1975_creation]].",
      "- **pe-re-*82** — a Pylos goddess on tablets Tn 316 [[chadwick1976_mycenaean]].",
      "*An inheritance supplies the cosmogonic sacrifice (*Manu/*Yemo) and the kingship motif [[lincoln1975_creation]].*",
      "- ***Trito**: the \"Third\" — a PIE warrior-hero [[lincoln1975_creation]]."
    )
    lines.foreach { l =>
      assert(html(l + "\n").contains("<em>") || html(l + "\n").contains("*<strong>"), html(l))
      assertEquals(rules(l + "\n"), List(FormatRule.Emphasis), l)
    }

  test("an underscore that opens italics with no partner, but never snake_case or a citekey"):
    assertEquals(rules("A _Method line that never closes [[cline2014_year]].\n"), List(FormatRule.Emphasis))
    assertEquals(rules("snake_case words and frazer1922_goldenbough are fine [[cline2014_year]].\n"), Nil)

  test("example 3: links the renderer cannot read"):
    val md =
      """See [the reliefs] (https://example.org/habu) and [the letters](https://example.org/ugarit and
        |<https://example.org/open and [](https://example.org/x) and [text]() [[cline2014_year]].
        |""".stripMargin
    val is = body(md)
    assertEquals(is.map(_.rule).distinct.sortBy(_.ordinal), List(FormatRule.LinkSyntax, FormatRule.LinkEmpty))
    assertEquals(is.count(_.rule == FormatRule.LinkSyntax), 3)
    assertEquals(is.count(_.rule == FormatRule.LinkEmpty), 2)
    val f = fixed(md)
    assert(f.contains("[the reliefs](https://example.org/habu)"), f)
    assert(f.contains("<https://example.org/open>"), f)

  test("example 3: malformed wikilinks — short a bracket, escaped, padded, not a slug, an embed"):
    val md =
      """A [[cline2014_year] and [drews1993_end]] and \[\[haak2015_massive\]\] and [[ cline2014_year ]]
        |and [[Bronze Age Collapse]] and ![[map.png]] and [[md/haak2015/paper.md]].
        |""".stripMargin
    val is = body(md)
    assert(is.forall(_.rule == FormatRule.Wikilink), is.mkString("\n"))
    assertEquals(is.size, 7)
    assertEquals(is.filter(_.severity == FormatSeverity.Warning).size, 2) // escaped and padded render on the page
    val f = fixed(md)
    assert(f.contains("A [[cline2014_year]] and [[drews1993_end]] and [[haak2015_massive]] and [[cline2014_year]]"), f)
    assert(f.contains("[[Bronze Age Collapse]]"), "never rewritten — that would be guessing a slug")

  test("a Markdown link to a wiki page should be a [[wikilink]] — fixed with its label"):
    val md = "See [the collapse](../concepts/bronze_age_collapse.md) and [Cline](/source/cline2014_year).\n"
    assertEquals(rules(md), List(FormatRule.WikiPathLink, FormatRule.WikiPathLink))
    assertEquals(fixed(md), "See [[bronze_age_collapse|the collapse]] and [[cline2014_year|Cline]].\n")

  test("unresolved wikilinks and bare citekeys, only when the caller can resolve them"):
    val md = "Cline [[cline2014_year]] and [[no_such_page]]; see drews1993_end too.\n"
    assertEquals(rules(md), Nil)
    val ctx =
      article.copy(resolves = Some(Set("cline2014_year")), isCitekey = Some(Set("drews1993_end", "cline2014_year")))
    assertEquals(rules(md, ctx), List(FormatRule.WikilinkUnresolved, FormatRule.BareCitekey))

  test("example 4: a YAML frontmatter block pasted into the body renders as a rule and a heading"):
    val md =
      """---
        |title: Sea Peoples
        |slug: sea_peoples
        |tags: [bronze_age_collapse]
        |---
        |
        |# Sea Peoples
        |
        |Text [[cline2014_year]].
        |""".stripMargin
    assert(html(md).contains("<hr />") && html(md).contains("<h2>"), html(md))
    val is = body(md)
    assertEquals(is.map(_.rule), List(FormatRule.Frontmatter))
    assertEquals(fixed(md), "# Sea Peoples\n\nText [[cline2014_year]].\n")

  test("example 4: bare key: value header lines at the top, and a ```markdown wrapper"):
    val keys = "title: Sea Peoples\nkind: concept\n\n# Sea Peoples\n\nText [[cline2014_year]].\n"
    assertEquals(rules(keys), List(FormatRule.Frontmatter))
    assertEquals(fixed(keys), "# Sea Peoples\n\nText [[cline2014_year]].\n")
    val wrapped = "```markdown\n# Sea Peoples\n\nText [[cline2014_year]].\n```\n"
    assert(html(wrapped).contains("<pre>"))
    assertEquals(rules(wrapped), List(FormatRule.WrappedInFence))
    assertEquals(fixed(wrapped), "# Sea Peoples\n\nText [[cline2014_year]].\n")
    // a frontmatter block with keys that are not metadata is reported but never stripped
    val odd = "---\nfoo: bar\nbaz: qux\n---\n\nText [[cline2014_year]].\n"
    assertEquals(rules(odd), List(FormatRule.Frontmatter))
    assertEquals(fixed(odd), odd)

  // ── structure ──────────────────────────────────────────────────────────────────────────────

  test("headings: no space, a stuck #, a skipped level, a second title, duplicates, Sources cited"):
    val md =
      """# Title
        |
        |##Summary
        |
        |## Evidence#
        |
        |#### Deep detail
        |
        |# Second title
        |
        |## Disagreements
        |
        |## Disagreements
        |
        |## Sources cited
        |
        |- [[cline2014_year]]
        |""".stripMargin
    val is = body(md)
    assertEquals(
      is.map(_.rule),
      List(
        FormatRule.HeadingSyntax,
        FormatRule.HeadingSyntax,
        FormatRule.HeadingLevel,
        FormatRule.HeadingLevel,
        FormatRule.DuplicateHeading,
        FormatRule.SourcesCitedSection
      )
    )
    val f = fixed(md)
    assert(f.contains("## Summary\n") && f.contains("## Evidence\n"), f)

  test("a --- right under a paragraph makes it a heading"):
    val md = "The reliefs date to Ramesses III [[cline2014_year]].\n---\n\nNext [[cline2014_year]].\n"
    assert(html(md).contains("<h2>"))
    assertEquals(rules(md), List(FormatRule.SetextHeading))
    assert(!html(fixed(md)).contains("<h2>"))

  test("list items glued to a paragraph, mixed bullets, numbers that jump"):
    val glued = "The destruction layers:\n2. Ugarit\n3. Pylos\n"
    assert(!html(glued).contains("<ol"))
    assertEquals(rules(glued), List(FormatRule.ListGlued))
    assert(html(fixed(glued)).contains("<ol"))
    // a wrapped line that starts with a year is NOT a list
    assertEquals(rules("It was destroyed around\n1200. Later layers [[cline2014_year]].\n"), Nil)
    assertEquals(rules("- Ugarit\n- Pylos\n* Mycenae\n"), List(FormatRule.ListMarkers))
    assertEquals(rules("1. Ugarit\n2. Pylos\n5. Mycenae\n"), List(FormatRule.ListMarkers))
    assertEquals(rules("1. Ugarit\n1. Pylos\n1. Mycenae\n"), Nil) // lazy numbering is fine

  test("an unclosed fence, a fence closed by a shorter one, indented prose"):
    assertEquals(rules("Text [[cline2014_year]].\n\n```\ncode\n"), List(FormatRule.UnclosedFence))
    val shorter = body("Text [[cline2014_year]].\n\n````\ncode\n```\n\nMore.\n")
    assertEquals(shorter.map(_.rule), List(FormatRule.UnclosedFence))
    assert(shorter.head.message.contains("shorter"))
    val indented = "Text [[cline2014_year]].\n\n    An indented paragraph [[cline2014_year]].\n"
    assertEquals(rules(indented), List(FormatRule.IndentedCode))
    assert(!html(fixed(indented)).contains("<pre>"))
    // a continuation paragraph inside a numbered list item is NOT code
    assertEquals(rules("1. **The name.** Text [[cline2014_year]].\n\n    More of item one [[cline2014_year]].\n"), Nil)

  test("code spans: an unpaired backtick; a curly quote closing one"):
    assertEquals(rules("Run `histmersion get and stop [[cline2014_year]].\n"), List(FormatRule.CodeSpan))
    assert(body("Run `histmersion get’ now [[cline2014_year]].\n").head.message.contains("curly"))
    assertEquals(rules("Run `histmersion get` now [[cline2014_year]].\n"), Nil)

  test("escapes, entities, HTML, footnotes, math, strikethrough"):
    assertEquals(
      rules("Line one\\n\\nLine two [[cline2014_year]].\n"),
      List(FormatRule.LiteralEscape, FormatRule.LiteralEscape)
    )
    assertEquals(fixed("One\\n\\nTwo [[cline2014_year]].\n"), "One\n\nTwo [[cline2014_year]].\n")
    assertEquals(rules("Ecology &amp;amp; Evolution [[cline2014_year]].\n"), List(FormatRule.HtmlEntity))
    assertEquals(fixed("Ecology &amp;amp; Evolution.\n"), "Ecology &amp; Evolution.\n")
    assertEquals(rules("Ecology &amp; Evolution [[cline2014_year]].\n"), Nil) // renders "&" — fine
    val tags = body("The <etal>et al</etal> and <div>open [[cline2014_year]].\n")
    assertEquals(tags.map(_.rule), List(FormatRule.HtmlTag, FormatRule.HtmlTag))
    assertEquals(tags.find(_.message.contains("never closed")).map(_.severity), Some(FormatSeverity.Error))
    assertEquals(rules("The 12<sup>th</sup> century <i>c</i>. [[cline2014_year]]\n"), Nil)
    val fn = body("Claim[^1] here [[cline2014_year]].\n\n[^2]: Smith 2000.\n")
    assertEquals(fn.map(_.rule), List(FormatRule.Footnote, FormatRule.Footnote))
    assertEquals(rules("Energy $$E=mc^2$$ [[cline2014_year]].\n"), List(FormatRule.Math))
    assertEquals(rules("It cost $5 and $6 [[cline2014_year]].\n"), Nil)
    assertEquals(rules("~~old claim~~ [[cline2014_year]].\n"), List(FormatRule.Strikethrough))

  test("chatter and placeholders left in the text"):
    val md =
      """Here is the revised article with the changes you asked for.
        |
        |# Sea Peoples
        |
        |The date is TODO [citation needed] and <insert map here> [[cline2014_year]].
        |
        |Let me know if you want more detail.
        |""".stripMargin
    val is = body(md)
    assertEquals(is.count(_.rule == FormatRule.AgentChatter), 2)
    assertEquals(is.count(_.rule == FormatRule.Placeholder), 3)
    assert(is.forall(_.severity == FormatSeverity.Warning))
    assertEquals(fixed(md), md, "wording is never changed")
    // the words in ordinary prose are not chatter
    assertEquals(rules("Sure enough, the layer is burnt; of the four sites [[cline2014_year]].\n"), Nil)

  test("images: no source, a local path; a figures/ link in a source's notes is the page's to rewrite"):
    assertEquals(
      rules("![map]() and ![map](/Users/me/map.png) [[cline2014_year]].\n"),
      List(FormatRule.Image, FormatRule.Image)
    )
    assertEquals(MarkdownLint.issues("![Fig. 1](figures/fig1.png)\n", FormatField.Notes), Nil)

  test("bare URLs are not clickable — wrapped by --fix; autolinks and links are fine"):
    val md = "Data at https://doi.org/10.1038/nature14317. and <https://example.org> [x](https://e.org).\n"
    assertEquals(rules(md), List(FormatRule.BareUrl))
    assertEquals(
      fixed(md).linesIterator.next(),
      "Data at <https://doi.org/10.1038/nature14317>. and <https://example.org> [x](https://e.org)."
    )

  test("nothing inside code is checked"):
    val md = "Use:\n\n```\n**not bold | a | b |\n[x] (http://y)\n```\n\nand `**x` [[cline2014_year]].\n"
    assertEquals(rules(md), Nil)

  test("positions are 1-based lines and columns of the stored text, with a short excerpt"):
    val is = body("# T\n\nFirst [[cline2014_year]] then ** x** here.\n")
    assertEquals((is.head.line, is.head.column), (3, 31))
    assert(is.head.excerpt.contains("** x**"), is.head.excerpt)
    assert(is.head.fix.nonEmpty)

  test("fix applies only wording-safe repairs and reports each one"):
    val md = "Text\n| A | B |\n|---|---|\n| 1 | 2 |\n\nSee \\[\\[cline2014_year\\]\\] and https://e.org/x.\n"
    val (out, done) = MarkdownLint.fix(md, FormatField.Body, article)
    assertEquals(out, "Text\n\n| A | B |\n|---|---|\n| 1 | 2 |\n\nSee [[cline2014_year]] and <https://e.org/x>.\n")
    assertEquals(done.size, 3)
    assert(done.head.startsWith("table-not-rendered at line 2"), done.head)
    assertEquals(MarkdownLint.fix(out, FormatField.Body, article), (out, Nil))

  test("the summary and notes fields are checked the same way and say so"):
    val is = MarkdownLint.issues("A ** bad** summary.", FormatField.Summary)
    assertEquals(is.map(i => (i.rule, i.field)), List((FormatRule.Emphasis, FormatField.Summary)))
    // a source's notes have no generated Sources cited footer: the heading is fine there
    assertEquals(MarkdownLint.issues("## Sources cited\n\n- x\n", FormatField.Notes), Nil)

  test("the wire spellings round-trip and every rule is documented"):
    FormatRule.values.foreach { r =>
      assertEquals(FormatRule.fromWire(r.wire), Some(r))
      assert(r.description.nonEmpty && !r.wire.contains("_"), r.wire)
    }
    val i =
      FormatIssue(FormatRule.TableNotRendered, FormatSeverity.Error, FormatField.Body, 3, 1, "| a |", "m", Some("f"))
    val j = io.circe.parser.decode[FormatIssue](io.circe.syntax.EncoderOps(i).asJson.noSpaces)
    assertEquals(j, Right(i))
    assert(io.circe.syntax.EncoderOps(i).asJson.noSpaces.contains("\"rule\":\"table-not-rendered\""))

  // ── the page notice ─────────────────────────────────────────────────────────────────────────

  test("the page notice: folded, counted, one line per problem, escaped; nothing when clean"):
    val is = body("Intro [[cline2014_year]]:\n| A | B |\n|---|---|\n| 1 | 2 |\n\nThe ** x** <b>bold</b> here.\n")
    val html = Pages.formatNotice(is, "This article", Some("sea_peoples"))
    assert(
      html.startsWith(
        """<details class="format-notice has-errors"><summary>This article may not display correctly: 2 formatting problems</summary>"""
      ),
      html
    )
    assert(
      html.contains(
        """<li class="fi fi-error"><span class="fi-where">line 2</span> <span class="fi-rule">table not rendered</span>"""
      ),
      html
    )
    assert(!html.contains("<b>") && html.contains("&lt;b&gt;bold"), "excerpts are data: escaped")
    assert(html.contains("<code>histmersion article lint sea_peoples --fix</code> repairs all of them"), html)
    assertEquals(Pages.formatNotice(Nil, "This article", None), "")
    // on the article page it sits under the meta line, above the prose
    val row = ArticleRow(
      "sea_peoples",
      "Sea Peoples",
      "concept",
      "# Sea Peoples\n\nThe ** x** here.",
      None,
      Nil,
      Nil,
      None,
      None
    )
    val page =
      Pages.article(ArticlePage(row, Nil, Nil, MarkdownLint.issues(row.body, FormatField.Body, article)), _ => None)
    assert(
      page.indexOf("format-notice") > page.indexOf("</h1>") && page.indexOf("format-notice") < page.indexOf("<p>The"),
      page
    )

  test(
    "#958: the generated coverage sections have no formatting problem, and the page renders them as a list and one note"
  ):
    val src = (ck: String, st: SourceStatus) => CoverageSource(ck, ck, Some(2000), st)
    val c = ArticleCoverage.compute(
      "tin",
      ArticleFilter(query = Some("tin_trade *x*"), topics = List("bronze_age")),
      812,
      List(
        src("knapp2016_tin", SourceStatus.Current),
        src("egypt2001_old", SourceStatus.Contested),
        src("iso2024_new", SourceStatus.Current)
      ),
      Set("knapp2016_tin"),
      List("knapp2016_tin", "other2020_x"),
      Map("egypt2001_old" -> "About Egypt, not the Aegean"),
      List(CoverageEdge("crit2022_x", "superseded_by", "knapp2016_tin")),
      List(CoverageSearch("synoikism", 3, Nil))
    )
    val md = "# Tin\n\nTin moved [[knapp2016_tin]].\n\n" + ArticleCoverage.sections(c)
    assertEquals(body(md), Nil)
    val page = Pages.article(
      ArticlePage(
        ArticleRow("tin", "Tin", "concept", "# Tin\n\nTin moved [[knapp2016_tin]].", None, Nil, Nil, None, None),
        Nil,
        Nil,
        Nil,
        Some(c)
      ),
      _ => None
    )
    assert(page.contains("<h2>Not used</h2>") && page.contains("<ul>") && page.contains("<em>Method: "), page)
    assert(!page.contains("_Method"), "the emphasis renders")
