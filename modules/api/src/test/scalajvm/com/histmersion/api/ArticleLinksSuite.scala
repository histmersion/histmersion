package com.histmersion.api

import com.histmersion.testsupport.ComparedByValue.given

/** The wikilink graph (plan §5.2 stream 2.7). Pure: this is the function that decides what ends up in `article_links`,
  * so every shape the existing wiki actually contains is pinned here.
  */

import com.histmersion.api.research.Article
class ArticleLinksSuite extends munit.FunSuite:

  private val sources = Set("cline2014_year", "haak2015_massive")
  private val isSource: String => Boolean = sources.contains

  test("plain, aliased and anchored links all yield the bare target, once each"):
    val body = "see [[sea_peoples]], [[cline2014_year|Cline]] and [[sea_peoples#The textual record]]"
    assertEquals(Article.Links.targets(body), List("sea_peoples", "cline2014_year"))

  test("backslash-escaped links (as timeline/deep_time.md writes them) are real links"):
    assertEquals(
      Article.Links.targets("""a \[\[haak2015_massive\]\] b \[\[europe\]\]"""),
      List("haak2015_massive", "europe")
    )

  test("image embeds and anchor-only links are not links"):
    assertEquals(Article.Links.targets(com.histmersion.api.text.WikiLinks.Body("![[migrations_timeline.png]] and [[#Source catalog|the catalog]]")), Nil)

  test("a bracketed phrase that is not slug-shaped is left alone"):
    assertEquals(Article.Links.targets(com.histmersion.api.text.WikiLinks.Body("[[Not A Key]] [[wiki/sources/]]")), Nil)

  test("two links on one line do not merge into one match"):
    assertEquals(Article.Links.targets(com.histmersion.api.text.WikiLinks.Body("[[a_one]] and [[b_two]]")), List("a_one", "b_two"))

  test("a source link is citekey-shaped AND present; otherwise it is an article slug"):
    assertEquals(
      Article.Links.links(com.histmersion.api.research.Article.Body("[[cline2014_year]] [[sea_peoples]] [[nobody2019_ghost]]"), isSource),
      List(
        Article.Link(Article.Links.TargetSource, com.histmersion.api.research.Article.Link.Target("cline2014_year")),
        Article.Link(Article.Links.TargetArticle, com.histmersion.api.research.Article.Link.Target("sea_peoples")),
        // citekey-shaped but not in the corpus: an article slug, i.e. a forward link, not a citation
        Article.Link(Article.Links.TargetArticle, com.histmersion.api.research.Article.Link.Target("nobody2019_ghost"))
      )
    )

  test("citekey shape"):
    assert(Article.Links.isCitekeyShaped("haak2015_massive"))
    assert(Article.Links.isCitekeyShaped("cline2014_year"))
    assert(Article.Links.isCitekeyShaped("knapp2016"))
    assert(Article.Links.isCitekeyShaped("revisiting1177"))
    // an article slug can have the citekey shape too — which is precisely why EXISTENCE in `sources`,
    // not the shape alone, decides whether a link is a citation
    assert(Article.Links.isCitekeyShaped("sea_peoples"))
    assert(!Article.Links.isCitekeyShaped("nodigits"))
    assert(!Article.Links.isCitekeyShaped("Upper_Case"))

  test("resolveMarkdown sends citekeys to /source and everything else to /article"):
    assertEquals(
      Article.Links.resolveMarkdown(com.histmersion.api.research.Article.Links.ResolveMarkdownMd("[[cline2014_year]] vs [[sea_peoples]]"), isSource),
      "[cline2014_year](/source/cline2014_year) vs [sea_peoples](/article/sea_peoples)"
    )

  test("resolveMarkdown keeps the label, drops the anchor, un-escapes"):
    assertEquals(
      Article.Links.resolveMarkdown(com.histmersion.api.research.Article.Links.ResolveMarkdownMd("[[cline2014_year|Cline 2014]]"), isSource),
      "[Cline 2014](/source/cline2014_year)"
    )
    assertEquals(Article.Links.resolveMarkdown(com.histmersion.api.research.Article.Links.ResolveMarkdownMd("[[europe#Neolithic]]"), isSource), "[europe](/article/europe)")
    assertEquals(
      Article.Links.resolveMarkdown("""\[\[haak2015_massive\]\]""", isSource),
      "[haak2015_massive](/source/haak2015_massive)"
    )

  test("resolveMarkdown leaves embeds and non-links exactly as they were"):
    val md = "![[map.png]] and [[Not A Key]] and $5 [[#anchor|x]]"
    assertEquals(Article.Links.resolveMarkdown(md, isSource), md)

/** Wikilink RESOLUTION (UX item 5): a citekey alias, an article slug, or nothing at all. Pure. */
class ArticleLinkResolutionSuite extends munit.FunSuite:

  private val corpus = Map("allentoft2015" -> "allentoft2015_population", "knapp2016" -> "knapp2016_seven")
  private val slugs = Set("bronze_age_collapse", "sea_peoples")
  private def href(t: String) = Article.Links.hrefFor(t, corpus.get, slugs.contains)

  test("a citekey alias resolves to the full source, not to an article slug"):
    assertEquals(href("allentoft2015"), Some("/source/allentoft2015_population"))

  test("an article slug resolves to the article"):
    assertEquals(href("bronze_age_collapse"), Some("/article/bronze_age_collapse"))

  test("a source wins over an article of the same name — a citekey is a citation"):
    val both = Article.Links.hrefFor(com.histmersion.api.research.Article.Link.Target("x"), _ => Some(com.histmersion.api.library.Citekey("x_paper")), _ => true)
    assertEquals(both, Some("/source/x_paper"))

  test("nothing in the corpus answers to it → no link at all"):
    assertEquals(href("phantom_topic"), None)

  test("linkMarkdown renders the three cases, keeping labels and dropping anchors"):
    val md = "See [[allentoft2015]], [[bronze_age_collapse|the collapse]] and [[phantom#bit]]."
    val out = Article.Links.linkMarkdown(md, href)
    assert(out.contains("[allentoft2015](/source/allentoft2015_population)"), out)
    assert(out.contains("[the collapse](/article/bronze_age_collapse)"), out)
    assert(out.contains("""<span class="dangling" title="unresolved link">phantom</span>"""), out)

  test("an image embed is still not a link"):
    assertEquals(Article.Links.linkMarkdown(com.histmersion.api.research.Article.Body("![[map.png]]"), _ => Some("/x")), "![[map.png]]")

  test("the wave-1 two-way form still behaves as it did"):
    val out = Article.Links.resolveMarkdown(com.histmersion.api.research.Article.Links.ResolveMarkdownMd("[[a2015_x]] [[concept]]"), Set("a2015_x").contains)
    assert(out.contains("[a2015_x](/source/a2015_x)"), out)
    assert(out.contains("[concept](/article/concept)"), out)
