package com.histmersion
package api

import com.histmersion.api.Html.html
import com.histmersion.api.library.Citekey

class HtmlSuite extends munit.FunSuite:

  private def clean(s: String): String = Html.sanitize(s).markup

  test("text escapes every character that starts markup or ends a double-quoted attribute value"):
    assertEquals(
      Html.text("""<a href="x" title='y'>&</a>""").markup,
      "&lt;a href=&quot;x&quot; title='y'&gt;&amp;&lt;/a&gt;"
    )

  test("the interpolator refuses a splice in a single-quoted or unquoted attribute"):
    assert(compileErrors("""val x = "a"; html"<p title='$x'>"""").nonEmpty)
    assert(compileErrors("""val x = "a"; html"<p title=$x>"""").nonEmpty)

  test("the interpolator keeps its literal markup, escapes a spliced text and keeps a spliced Html"):
    val name = "<script>alert(1)</script>"
    val bold = html"<b>${"B&B"}</b>"
    assertEquals(
      html"""<p title="$name">$bold ${3} ${4L}</p>""".markup,
      """<p title="&lt;script&gt;alert(1)&lt;/script&gt;"><b>B&amp;B</b> 3 4</p>"""
    )
    assertEquals(html"plain".markup, "plain")

  test("the interpolator splices a newtype as its value's text, escaped"):
    val ck = Citekey("a<b")
    assertEquals(html"""<i data-ck="$ck">$ck</i>""".markup, """<i data-ck="a&lt;b">a&lt;b</i>""")

  test("the interpolator splices a checked address escaped"):
    val u = Href.link("https://example.org/?a=1&b=\"2\"").get
    assertEquals(
      html"""<a href="$u">x</a>""".markup,
      """<a href="https://example.org/?a=1&amp;b=&quot;2&quot;">x</a>"""
    )

  test("an Html is made only by escaping, the interpolator's literals or the allow-list"):
    assert(compileErrors("""val h: Html = "<b>x</b>"""").nonEmpty)
    assert(compileErrors("""val parts = List("<b>"); StringContext(parts*).html()""").nonEmpty)
    assert(compileErrors("""case class C(x: Int); html"<b>${C(1)}</b>"""").nonEmpty)

  test("concat and join put markup together as it is"):
    assertEquals(Html.concat(List(html"<i>a</i>", Html.text("<"))).markup, "<i>a</i>&lt;")
    assertEquals(Html.join(List(html"a", html"b"), html" · ").markup, "a · b")
    assert(Html.empty.isEmpty && html"x".nonEmpty)

  test("a script element is shown as text, its content stays text"):
    assertEquals(clean("<p>a<script>alert(1)</script>b</p>"), "<p>a&lt;script&gt;alert(1)&lt;/script&gt;b</p>")

  test("an allowed element with an event handler is shown as text"):
    assertEquals(
      clean("""<img src="x.png" onerror="alert(1)">"""),
      "&lt;img src=&quot;x.png&quot; onerror=&quot;alert(1)&quot;&gt;"
    )
    assertEquals(
      clean("""<a href="/x" ONCLICK=alert(1)>x</a>"""),
      "&lt;a href=&quot;/x&quot; ONCLICK=alert(1)&gt;x</a>"
    )

  test("a link or image of a refused scheme loses its address and keeps its text"):
    assertEquals(clean("""<a href="javascript:alert(1)">x</a>"""), "<a>x</a>")
    assertEquals(clean("""<a href="JaVaScRiPt:alert(1)">x</a>"""), "<a>x</a>")
    assertEquals(clean("""<a href="  java&#x09;script:alert(1)">x</a>"""), "<a>x</a>")
    assertEquals(clean("""<a href="&#106;avascript:alert(1)">x</a>"""), "<a>x</a>")
    assertEquals(clean("""<a href="vbscript:msgbox(1)">x</a>"""), "<a>x</a>")
    assertEquals(clean("""<a href="data:text/html,<script>alert(1)</script>">x</a>"""), "<a>x</a>")
    assertEquals(clean("""<img src="data:image/svg+xml,<svg onload=alert(1)>" alt="a">"""), """<img alt="a">""")

  test("an image may be an inline base64 raster; an inline SVG or any other type loses its address"):
    assertEquals(
      clean("""<img src="data:image/png;base64,iVBORw0KGgo=" alt="a">"""),
      """<img src="data:image/png;base64,iVBORw0KGgo=" alt="a">"""
    )
    assertEquals(
      clean("""<img src="DATA:image/WEBP;base64,UklGRg==" alt="a">"""),
      """<img src="DATA:image/WEBP;base64,UklGRg==" alt="a">"""
    )
    assertEquals(clean("""<img src="data:image/svg+xml;base64,PHN2Zz4=" alt="a">"""), """<img alt="a">""")
    assertEquals(clean("""<img src="data&#58;image/svg+xml;base64,PHN2Zz4=" alt="a">"""), """<img alt="a">""")
    assertEquals(clean("""<img src="data:image/png,<svg onload=alert(1)>" alt="a">"""), """<img alt="a">""")
    assertEquals(clean("""<img src="data:text/html;base64,PHNjcmlwdD4=" alt="a">"""), """<img alt="a">""")
    assertEquals(clean("""<a href="data:image/png;base64,iVBORw0KGgo=">x</a>"""), "<a>x</a>")

  test("a link may open a new tab; it is always written with rel=\"noopener noreferrer\""):
    assertEquals(
      clean("""<a href="https://x.org" target="_blank">x</a>"""),
      """<a href="https://x.org" target="_blank" rel="noopener noreferrer">x</a>"""
    )
    assertEquals(
      clean("""<a rel="opener" TARGET=_BLANK href="/y">y</a>"""),
      """<a target="_blank" href="/y" rel="noopener noreferrer">y</a>"""
    )
    assertEquals(
      clean("""<a target="&#95;blank" rel="opener" rel="nofollow">z</a>"""),
      """<a target="_blank" rel="noopener noreferrer">z</a>"""
    )

  test("a new tab's link keeps its other rel tokens, once each, without opener, and gains noopener noreferrer"):
    assertEquals(
      clean("""<a href="/x" target="_blank" rel="nofollow">x</a>"""),
      """<a href="/x" target="_blank" rel="nofollow noopener noreferrer">x</a>"""
    )
    assertEquals(
      clean("""<a href="/x" rel=" NoFollow  opener ugc&#9;nofollow noreferrer " target="_blank">x</a>"""),
      """<a href="/x" target="_blank" rel="nofollow ugc noopener noreferrer">x</a>"""
    )
    assertEquals(
      clean("""<a target="&#95;blank" rel="nofollow" rel="opener">z</a>"""),
      """<a target="_blank" rel="nofollow noopener noreferrer">z</a>"""
    )
    assertEquals(
      clean("""<a target="_blank" rel="noopener noreferrer nofollow">z</a>"""),
      """<a target="_blank" rel="nofollow noopener noreferrer">z</a>"""
    )
    assertEquals(clean("""<a target="_blank" rel>z</a>"""), """<a target="_blank" rel="noopener noreferrer">z</a>""")
    assertEquals(
      clean("""<a target="_blank" rel="x&quot;y">z</a>"""),
      """<a target="_blank" rel="x&quot;y noopener noreferrer">z</a>"""
    )

  test("a link without a target keeps its rel as written"):
    assertEquals(clean("""<a href="/x" rel="opener nofollow">x</a>"""), """<a href="/x" rel="opener nofollow">x</a>""")

  test("any other target is refused: the tag is shown as text"):
    List(
      """<a href="/x" target="_top">""",
      """<a href="/x" target="_parent">""",
      """<a href="/x" target="_self">""",
      """<a href="/x" target="name">""",
      """<a href="/x" target=" _blank">""",
      """<a href="/x" target="_blank" target="_top">""",
      """<a href="/x" target>""",
      """<img src="/x.png" target="_blank">""",
      """<span target="_blank">"""
    ).foreach(t => assertEquals(clean(t), Html.text(t).markup, t))

  test("a named entity the cleaner does not decode stays text in an address, never a colon"):
    assertEquals(
      clean("""<a href="javascript&colon;alert(1)">x</a>"""),
      """<a href="javascript&amp;colon;alert(1)">x</a>"""
    )

  test("web, mail and relative addresses are kept"):
    assertEquals(
      clean("""<a href="https://doi.org/10.1/x?a=1&amp;b=2" rel="noopener">d</a>"""),
      """<a href="https://doi.org/10.1/x?a=1&amp;b=2" rel="noopener">d</a>"""
    )
    assertEquals(clean("""<a href="mailto:a@b.org">m</a>"""), """<a href="mailto:a@b.org">m</a>""")
    assertEquals(clean("""<a href="#cite-1">[1]</a>"""), """<a href="#cite-1">[1]</a>""")
    assertEquals(
      clean("""<img loading="lazy" src="/source/x/assets/f.png" alt="Figure 1" />"""),
      """<img loading="lazy" src="/source/x/assets/f.png" alt="Figure 1" />"""
    )

  test("the page's own anchors and data attributes survive unchanged"):
    val anchors =
      """<a class="src-ref" data-ck="cline2014" data-doi="10.1/x" href="/source/cline2014">Cline 2014</a>""" +
        """<sup class="cite-num"><a class="cite-jump" href="#cite-1">[1]</a></sup>""" +
        """<span class="dangling" title="unresolved link">x</span>""" +
        """<a class="chip chip-tag" data-field="tag" data-value="a&amp;b" href="/#tag=a%26b">a b</a>"""
    assertEquals(clean(anchors), anchors)

  test("GFM tables, code, figures and the figure-text block survive unchanged"):
    val page =
      "<table>\n<thead>\n<tr><th align=\"left\">a</th><th>b</th></tr>\n</thead>\n<tbody>\n<tr><td>1</td><td>2</td></tr>\n</tbody>\n</table>\n" +
        "<pre><code class=\"language-scala\">val x = &lt;y&gt;\n</code></pre>\n<blockquote>\n<p>q</p>\n</blockquote>\n" +
        "<details class=\"figtext\"><summary>figure text</summary><div>a<br>b</div></details>\n<hr />\n<ol start=\"3\"><li>x</li></ol>"
    assertEquals(clean(page), page)

  test("a tag no browser knows is dropped and its text kept; a comment is dropped"):
    assertEquals(
      clean("<p>Smith <etal>et al.</etal> <jats:italic>x</jats:italic><!-- image --></p>"),
      "<p>Smith et al. x</p>"
    )

  test("a half-open or malformed tag is text"):
    assertEquals(clean("""<img src=x onerror=alert(1)"""), "&lt;img src=x onerror=alert(1)")
    assertEquals(clean("""<a/href="javascript:alert(1)">x</a>"""), """&lt;a/href="javascript:alert(1)">x</a>""")
    assertEquals(clean("a < b > c"), "a &lt; b > c")
    assertEquals(clean("<<script>>"), "&lt;&lt;script&gt;>")

  test("svg, math, iframe, style and form controls are shown as text"):
    List(
      "<svg onload=alert(1)>",
      "<math>",
      "<iframe src=\"https://x\">",
      "<style>",
      "<form action=\"/x\">",
      "<input type=\"text\">",
      "<object data=\"x\">",
      "<base href=\"https://x/\">"
    ).foreach { t =>
      assertEquals(clean(t), Html.text(t).markup, t)
    }

  test("names and quoting are written in one form; attribute values are escaped again"):
    assertEquals(clean("""<P CLASS='a"b' title=x>t</P>"""), """<p class="a&quot;b" title="x">t</p>""")
    assertEquals(clean("""<span title="&eacute;&#233;&amp;">t</span>"""), """<span title="&eacute;é&amp;">t</span>""")

  test("what the cleaner returns it returns unchanged"):
    List(
      """<P CLASS='a"b' title=x>t</P>""",
      """<a href=" java&#x09;script:x">y</a><img src=x onerror=y>""",
      """<a rel="opener" target="_BLANK" href="/y">y</a><img src="data:image/gif;base64,R0lG">""",
      """<a rel="UGC opener nofollow ugc &amp;x &eacute;" target="_blank">y</a>""",
      """<span title="&eacute;&#233;&amp;&lt;">t</span>""",
      "a < b <!-- c --> <etal>d</etal> <script>e</script>"
    ).foreach(s => assertEquals(clean(clean(s)), clean(s), s))

  // ── markup derived from markup (moved here with the transforms, from IngestSuite and core's HighlightSuite) ──────

  test("lazyImages adds loading=lazy exactly once"):
    val out = Html.lazyImages(Html.sanitize("""<p><img src="/source/ck/assets/a.png" alt="A" /></p>""")).markup
    assertEquals(out.sliding("loading=".length).count(_ == "loading="), 1)
    assert(out.startsWith("""<p><img loading="lazy" src="""), out)

  test("lazyImages leaves an <img> that already declares loading alone"):
    val html = Html.sanitize("""<img loading="eager" src="x">""")
    assertEquals(Html.lazyImages(html).markup, html.markup)

  test("lazyImages: what it adds is markup the cleaner keeps"):
    List(
      """<p><img src="/source/ck/assets/a.png" alt="A" /></p>""",
      """<img src="a.png"><img loading="eager" src="b.png"> <IMG SRC='c.png' ALT=x>""",
      """<figure><img src="x" width="3" height="4"><figcaption>F</figcaption></figure>"""
    ).foreach { s =>
      assertEquals(Html.lazyImages(Html.sanitize(s)).markup, clean(Html.lazyImages(Html.sanitize(s)).markup), s)
    }

  // without `u`: Scala.js refuses \b with Unicode case before ES2018 (core's Highlight builds its own, on the JVM)
  private val sea = "(?i)\\bsea\\w*".r

  test("marked wraps the text's hits, numbered in document order, and counts them"):
    val (out, n) = Html.marked(Html.sanitize("<p>sea</p><p>The Sea</p>"), sea)
    assertEquals(n, 2)
    assertEquals(out.markup, """<p><mark id="hl1">sea</mark></p><p>The <mark id="hl2">Sea</mark></p>""")

  test("marked: script and style content is skipped"):
    val (out, n) = Html.markText("""<style>.sea{color:red}</style><script>var sea=1;</script><p>Sea</p>""", sea)
    assertEquals(n, 1)
    assert(out.startsWith("""<style>.sea{color:red}</style><script>var sea=1;</script>"""), out)

  test("marked: an unterminated tag does not lose text"):
    val (out, _) = Html.markText("<p>sea</p><b", sea)
    assert(out.endsWith("</p><b"), out)

  test("marked: what it adds is markup the cleaner keeps"):
    val (out, _) = Html.marked(Html.sanitize("""<p title="sea">sea &amp; Sea <a href="/source/sea">sea</a></p>"""), sea)
    assertEquals(clean(out.markup), out.markup)
