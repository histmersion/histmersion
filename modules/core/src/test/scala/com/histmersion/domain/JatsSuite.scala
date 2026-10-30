package com.histmersion.domain

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

/** JATS → Markdown (#1523): the PMC full text an ingest falls back to when no PDF is published. */
class JatsSuite extends munit.FunSuite:

  private val article = scala.io.Source.fromResource("jats/article.xml").mkString
  private lazy val md = Jats.toMarkdown(article)

  test("a JATS article is recognised through its prolog, comments and DOCTYPE"):
    assert(Jats.isJats(article))
    assert(Jats.isJats("\n<!DOCTYPE article PUBLIC \"x\" \"y\">\n<article dtd-version=\"1.4\"><front/></article>"))
    assert(Jats.isJats("<article/>"))

  test("anything else is not JATS"):
    assert(!Jats.isJats("%PDF-1.4 binary"))
    assert(!Jats.isJats("<?xml version=\"1.0\"?><ListBucketResult/>"))
    assert(!Jats.isJats("<html><body>Just a moment...</body></html>"))
    assert(!Jats.isJats("<articles/>"))

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
    val out = Jats.toMarkdown("<article><body><sec><title>Only</title><p>half a para")
    assert(out.contains("## Only"), out)
    assert(out.contains("half a para"), out)
    assertEquals(Jats.toMarkdown("").trim, "")
    assert(Jats.toMarkdown("<article><body><p>a</q></p></body></article>").contains("a"))

  test("an attribute value may hold a '>'"):
    val out = Jats.toMarkdown("""<article><body><p><ext-link href="a>b">link</ext-link> text</p></body></article>""")
    assertEquals(out.trim, "link text")

  test("a body that opens with running text is set off from the abstract"):
    val out = Jats.toMarkdown(
      "<article><front><article-meta><abstract><p>Abs.</p></abstract></article-meta></front>" +
        "<body><p>Intro.</p><sec><title>Results</title><p>R.</p></sec></body></article>"
    )
    assertEquals(out, "## Abstract\n\nAbs.\n\n---\n\nIntro.\n\n## Results\n\nR.\n")

  test("a nested reference list with its parent's title gets no second heading"):
    val out = Jats.toMarkdown(
      "<article><back><ref-list><title>References</title><ref-list><title>References</title>" +
        "<ref><mixed-citation>A. B.</mixed-citation></ref></ref-list></ref-list></back></article>"
    )
    assertEquals(out, "## References\n\n- A. B.\n")
