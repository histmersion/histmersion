package com.histmersion
package api

import com.histmersion.testsupport.ComparedByValue.given

/** The wikilink graph (plan §5.2 stream 2.7). Pure: this is the function that decides what ends up in `article_links`,
  * so every shape the existing wiki actually contains is pinned here.
  */

import com.histmersion.api.research.Article
import com.histmersion.api.text.WikiLinks
import com.histmersion.api.library.Citekey
import com.histmersion.lint.{tolerated, LintRule}
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class ArticleLinksSuite extends munit.FunSuite:

  private val sources = Set("cline2014_year", "haak2015_massive")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val isSource: Article.Link.Target => Boolean = t => sources.contains(t.value)

  test("plain, aliased and anchored links all yield the bare target, once each"):
    val body = WikiLinks.Body("see [[sea_peoples]], [[cline2014_year|Cline]] and [[sea_peoples#The textual record]]")
    assertEquals(Article.Links.targets(body), List("sea_peoples", "cline2014_year"))

  test("backslash-escaped links (as timeline/deep_time.md writes them) are real links"):
    assertEquals(
      Article.Links.targets(WikiLinks.Body("""a \[\[haak2015_massive\]\] b \[\[europe\]\]""")),
      List("haak2015_massive", "europe")
    )

  test("image embeds and anchor-only links are not links"):
    assertEquals(
      Article.Links.targets(WikiLinks.Body("![[migrations_timeline.png]] and [[#Source catalog|the catalog]]")),
      Nil
    )

  test("a bracketed phrase that is not slug-shaped is left alone"):
    assertEquals(Article.Links.targets(WikiLinks.Body("[[Not A Key]] [[wiki/sources/]]")), Nil)

  test("two links on one line do not merge into one match"):
    assertEquals(Article.Links.targets(WikiLinks.Body("[[a_one]] and [[b_two]]")), List("a_one", "b_two"))

  test("a source link is citekey-shaped AND present; otherwise it is an article slug"):
    assertEquals(
      Article.Links.links(Article.Body("[[cline2014_year]] [[sea_peoples]] [[nobody2019_ghost]]"), isSource),
      List(
        Article.Link(Article.Link.TargetKind(Article.Links.TargetSource), Article.Link.Target("cline2014_year")),
        Article.Link(Article.Link.TargetKind(Article.Links.TargetArticle), Article.Link.Target("sea_peoples")),
        // citekey-shaped but not in the corpus: an article slug, i.e. a forward link, not a citation
        Article.Link(Article.Link.TargetKind(Article.Links.TargetArticle), Article.Link.Target("nobody2019_ghost"))
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
      Article.Links.resolveMarkdown(Article.Links.ResolveMarkdownMd("[[cline2014_year]] vs [[sea_peoples]]"), isSource),
      "[cline2014_year](/source/cline2014_year) vs [sea_peoples](/article/sea_peoples)"
    )

  test("resolveMarkdown keeps the label, drops the anchor, un-escapes"):
    assertEquals(
      Article.Links.resolveMarkdown(Article.Links.ResolveMarkdownMd("[[cline2014_year|Cline 2014]]"), isSource),
      "[Cline 2014](/source/cline2014_year)"
    )
    assertEquals(
      Article.Links.resolveMarkdown(Article.Links.ResolveMarkdownMd("[[europe#Neolithic]]"), isSource),
      "[europe](/article/europe)"
    )
    assertEquals(
      Article.Links.resolveMarkdown(Article.Links.ResolveMarkdownMd("""\[\[haak2015_massive\]\]"""), isSource),
      "[haak2015_massive](/source/haak2015_massive)"
    )

  test("resolveMarkdown leaves embeds and non-links exactly as they were"):
    val md = "![[map.png]] and [[Not A Key]] and $5 [[#anchor|x]]"
    assertEquals(Article.Links.resolveMarkdown(Article.Links.ResolveMarkdownMd(md), isSource), md)

/** Wikilink RESOLUTION (UX item 5): a citekey alias, an article slug, or nothing at all. Pure. */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class ArticleLinkResolutionSuite extends munit.FunSuite:

  private val corpus = Map(
    Article.Link.Target("allentoft2015") -> Citekey("allentoft2015_population"),
    Article.Link.Target("knapp2016") -> Citekey("knapp2016_seven")
  )
  private val slugs = Set(Article.Link.Target("bronze_age_collapse"), Article.Link.Target("sea_peoples"))
  private def href(t: Article.Link.Target) = Article.Links.hrefFor(t, corpus.get, slugs.contains)

  test("a citekey alias resolves to the full source, not to an article slug"):
    assertEquals(href(Article.Link.Target("allentoft2015")), Some("/source/allentoft2015_population"))

  test("an article slug resolves to the article"):
    assertEquals(href(Article.Link.Target("bronze_age_collapse")), Some("/article/bronze_age_collapse"))

  test("a source wins over an article of the same name — a citekey is a citation"):
    val both = Article.Links.hrefFor(Article.Link.Target("x"), _ => Some(Citekey("x_paper")), _ => true)
    assertEquals(both, Some("/source/x_paper"))

  test("nothing in the corpus answers to it → no link at all"):
    assertEquals(href(Article.Link.Target("phantom_topic")), None)

  test("linkMarkdown renders the three cases, keeping labels and dropping anchors"):
    val md = "See [[allentoft2015]], [[bronze_age_collapse|the collapse]] and [[phantom#bit]]."
    val out = Article.Links.linkMarkdown(Article.Body(md), s => href(Article.Link.Target(s)))
    assert(out.contains("[allentoft2015](/source/allentoft2015_population)"), out)
    assert(out.contains("[the collapse](/article/bronze_age_collapse)"), out)
    assert(out.contains("""<span class="dangling" title="unresolved link">phantom</span>"""), out)

  test("an image embed is still not a link"):
    assertEquals(Article.Links.linkMarkdown(Article.Body("![[map.png]]"), _ => Some("/x")), "![[map.png]]")

  test("the wave-1 two-way form still behaves as it did"):
    val out = Article.Links
      .resolveMarkdown(
        Article.Links.ResolveMarkdownMd("[[a2015_x]] [[concept]]"),
        Set(Article.Link.Target("a2015_x")).contains
      )
      .value
    assert(out.contains("[a2015_x](/source/a2015_x)"), out)
    assert(out.contains("[concept](/article/concept)"), out)
