package histdb.core

/** The wikilink graph (plan §5.2 stream 2.7). Pure: this is the function that decides what ends up
  * in `article_links`, so every shape the existing wiki actually contains is pinned here. */
class ArticleLinksSuite extends munit.FunSuite:

  private val sources = Set("cline2014_year", "haak2015_massive")
  private val isSource: String => Boolean = sources.contains

  test("plain, aliased and anchored links all yield the bare target, once each"):
    val body = "see [[sea_peoples]], [[cline2014_year|Cline]] and [[sea_peoples#The textual record]]"
    assertEquals(ArticleLinks.targets(body), List("sea_peoples", "cline2014_year"))

  test("backslash-escaped links (as timeline/deep_time.md writes them) are real links"):
    assertEquals(ArticleLinks.targets("""a \[\[haak2015_massive\]\] b \[\[europe\]\]"""),
      List("haak2015_massive", "europe"))

  test("image embeds and anchor-only links are not links"):
    assertEquals(ArticleLinks.targets("![[migrations_timeline.png]] and [[#Source catalog|the catalog]]"), Nil)

  test("a bracketed phrase that is not slug-shaped is left alone"):
    assertEquals(ArticleLinks.targets("[[Not A Key]] [[wiki/sources/]]"), Nil)

  test("two links on one line do not merge into one match"):
    assertEquals(ArticleLinks.targets("[[a_one]] and [[b_two]]"), List("a_one", "b_two"))

  test("a source link is citekey-shaped AND present; otherwise it is an article slug"):
    assertEquals(
      ArticleLinks.links("[[cline2014_year]] [[sea_peoples]] [[nobody2019_ghost]]", isSource),
      List(
        ArticleLink(ArticleLinks.TargetSource, "cline2014_year"),
        ArticleLink(ArticleLinks.TargetArticle, "sea_peoples"),
        // citekey-shaped but not in the corpus: an article slug, i.e. a forward link, not a citation
        ArticleLink(ArticleLinks.TargetArticle, "nobody2019_ghost")))

  test("citekey shape"):
    assert(ArticleLinks.isCitekeyShaped("haak2015_massive"))
    assert(ArticleLinks.isCitekeyShaped("cline2014_year"))
    // an article slug can have the citekey shape too — which is precisely why EXISTENCE in `sources`,
    // not the shape alone, decides whether a link is a citation
    assert(ArticleLinks.isCitekeyShaped("sea_peoples"))
    assert(!ArticleLinks.isCitekeyShaped("nodigits"))
    assert(!ArticleLinks.isCitekeyShaped("Upper_Case"))

  test("resolveMarkdown sends citekeys to /source and everything else to /article"):
    assertEquals(
      ArticleLinks.resolveMarkdown("[[cline2014_year]] vs [[sea_peoples]]", isSource),
      "[cline2014_year](/source/cline2014_year) vs [sea_peoples](/article/sea_peoples)")

  test("resolveMarkdown keeps the label, drops the anchor, un-escapes"):
    assertEquals(ArticleLinks.resolveMarkdown("[[cline2014_year|Cline 2014]]", isSource),
      "[Cline 2014](/source/cline2014_year)")
    assertEquals(ArticleLinks.resolveMarkdown("[[europe#Neolithic]]", isSource),
      "[europe](/article/europe)")
    assertEquals(ArticleLinks.resolveMarkdown("""\[\[haak2015_massive\]\]""", isSource),
      "[haak2015_massive](/source/haak2015_massive)")

  test("resolveMarkdown leaves embeds and non-links exactly as they were"):
    val md = "![[map.png]] and [[Not A Key]] and $5 [[#anchor|x]]"
    assertEquals(ArticleLinks.resolveMarkdown(md, isSource), md)
