package com.histmersion
package upstreams

/** JATS → Markdown (#1523): the PMC full text an ingest falls back to when no PDF is published. */

import com.histmersion.upstreams.jats.Jats
import com.histmersion.upstreams.testsupport.ComparedByValue.given
import com.histmersion.lint.{tolerated, LintRule}
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class JatsSuite extends munit.FunSuite:

  private val article = scala.io.Source.fromResource("jats/article.xml").mkString
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private lazy val md = Jats.toMarkdown(Jats.Xml(article)).value

  test("a JATS article is recognised through its prolog, comments and DOCTYPE"):
    assert(Jats.isJats(Jats.Xml(article)))
    assert(
      Jats.isJats(Jats.Xml("\n<!DOCTYPE article PUBLIC \"x\" \"y\">\n<article dtd-version=\"1.4\"><front/></article>"))
    )
    assert(Jats.isJats(Jats.Xml("<article/>")))

  test("anything else is not JATS"):
    assert(!Jats.isJats(Jats.Xml("%PDF-1.4 binary")))
    assert(!Jats.isJats(Jats.Xml("<?xml version=\"1.0\"?><ListBucketResult/>")))
    assert(!Jats.isJats(Jats.Xml("<html><body>Just a moment...</body></html>")))
    assert(!Jats.isJats(Jats.Xml("<articles/>")))

  test("title, authors (not editors) and the abstract head the text; a teaser abstract is left out"):
    val lines = md.linesIterator.toList
    assertEquals(lines.head, "# The first horse herders & the *steppe*")
    assert(md.contains("\nPeter de Barros Damgaard, Eske Willerslev\n"), md)
    assert(!md.contains("Nobody"), md)
    assert(md.contains("## Abstract\n\nThe Yamnaya expansions — around 3000 BCE — reshaped Europe."), md)
    assert(!md.contains("Teaser"), md)
    assert(!md.contains("Not part of the text"), md)

  test("sections become headings by depth, with their labels"):
    assert(md.contains("\n## Introduction\n"), md)
    assert(md.contains("\n### 1.1 Dating\n"), md)

  test("running text keeps emphasis and bracketed citations"):
    assert(md.contains("Horses were first herded at Botai[1], and **not** by the Yamnaya."), md)

  test("a list inside a paragraph follows it as a Markdown list"):
    assert(md.contains("A list follows:\n\n1. first\n2. second"), md)

  test("TeX is kept, MathML is dropped, CDATA is read verbatim"):
    assert(md.contains("Radiocarbon $\\delta^{13}C$ values; a ratio without TeX."), md)
    assert(md.contains("$$a < b$$"), md)
    assert(!md.contains("mml"), md)

  test("figures and tables keep their captions; table cells become a pipe table"):
    assert(md.contains("**Figure 1.** Sampled sites. Map of the 74 genomes."), md)
    assert(md.contains("**Table 1.** Dates\n\n| Site | Date (BCE) |\n| --- | --- |\n| Botai | 3500\\|3000 |"), md)
    assert(!md.contains("nihms-1.jpg"), md)

  test("back matter: acknowledgements and references, element- and mixed-citations"):
    assert(md.contains("## Acknowledgements\n\nWe thank the museums."), md)
    assert(
      md.contains(
        "## References\n\n- 1. Outram AK, et al. The earliest horse harnessing and milking. Science. 2009. 323(5919):1332\u20131335. doi:10.1126/science.1168594."
      ),
      md
    )
    assert(md.contains("- Anthony DW. *The Horse, the Wheel, and Language*. Princeton; 2007."), md)
    assert(!md.contains("19265018"), md)

  test("entities: named, decimal and hexadecimal; an unknown one is left as written"):
    assertEquals(Jats.entities("a &amp; b &lt;c&gt; &#8212; &#x2014; &local;"), "a & b <c> — — &local;")

  test("a truncated or malformed document still yields its text, never an exception"):
    val out = Jats.toMarkdown(Jats.Xml("<article><body><sec><title>Only</title><p>half a para")).value
    assert(out.contains("## Only"), out)
    assert(out.contains("half a para"), out)
    assertEquals(Jats.toMarkdown(Jats.Xml("")).value.trim, "")
    assert(Jats.toMarkdown(Jats.Xml("<article><body><p>a</q></p></body></article>")).value.contains("a"))

  test("an attribute value may hold a '>'"):
    val out =
      Jats.toMarkdown(Jats.Xml("""<article><body><p><ext-link href="a>b">link</ext-link> text</p></body></article>"""))
    assertEquals(out.value.trim, "link text")

  test("a body that opens with running text is set off from the abstract"):
    val out = Jats.toMarkdown(
      Jats.Xml(
        "<article><front><article-meta><abstract><p>Abs.</p></abstract></article-meta></front>" +
          "<body><p>Intro.</p><sec><title>Results</title><p>R.</p></sec></body></article>"
      )
    )
    assertEquals(out, "## Abstract\n\nAbs.\n\n---\n\nIntro.\n\n## Results\n\nR.\n")

  test("a nested reference list with its parent's title gets no second heading"):
    val out = Jats.toMarkdown(
      Jats.Xml(
        "<article><back><ref-list><title>References</title><ref-list><title>References</title>" +
          "<ref><mixed-citation>A. B.</mixed-citation></ref></ref-list></ref-list></back></article>"
      )
    )
    assertEquals(out, "## References\n\n- A. B.\n")
