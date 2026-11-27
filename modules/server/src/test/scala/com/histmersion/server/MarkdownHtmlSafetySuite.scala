package com.histmersion
package server

import com.histmersion.lint.{tolerated, LintRule}

/** The one Markdown renderer of the pages, over random hostile Markdown (a seeded generator: a failure names its seed
  * and input): raw HTML blocks, inline HTML, Markdown links and images with script addresses, half-open tags. The
  * output is read back by this suite's own reader: no `<script>` element, no event-handler attribute, no `href`/`src`
  * of a script, VBScript or data scheme. And what the pages rely on still renders: tables, links, images, code.
  */
class MarkdownHtmlSafetySuite extends munit.FunSuite:

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def render(md: String): String = MarkdownHtml.render(FullText.Shown.Markdown(md)).markup

  private val Tag = """(?i)<([a-z][a-z0-9:-]*)([^<>]*)>""".r
  private val Attribute = """(?i)\s([a-z][a-z0-9:-]*)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+)))?""".r

  private def read(v: String): String =
    val decoded = """&#(\d+);?|&#[xX]([0-9a-fA-F]+);?""".r.replaceAllIn(
      v,
      m =>
        java.util.regex.Matcher.quoteReplacement(
          if m.group(1) != null then new String(Character.toChars(m.group(1).toInt.min(0x10ffff)))
          else new String(Character.toChars(Integer.parseInt(m.group(2), 16).min(0x10ffff)))
        )
    )
    decoded.replace("&amp;", "&").filterNot(c => c == '\t' || c == '\n' || c == '\r').dropWhile(_ <= ' ').toLowerCase

  /** The one inline image this suite accepts: a base64 raster of a fixed list, nothing else in the address. */
  private val RasterImage = """data:image/(png|jpeg|gif|webp);base64,[a-z0-9+/]+={0,2}""".r

  private def scriptAddress(element: String, attribute: String, v: String): Boolean =
    val address = read(v)
    List("javascript:", "vbscript:").exists(address.startsWith) ||
    (address.startsWith("data:") && !(element == "img" && attribute == "src" && RasterImage.matches(address)))

  private def violations(html: String): List[String] =
    Tag.findAllMatchIn(html).toList.flatMap { m =>
      val name = m.group(1).toLowerCase
      val attributes = Attribute.findAllMatchIn(m.group(2)).toList.map { a =>
        a.group(1).toLowerCase -> Option(a.group(2)).orElse(Option(a.group(3))).orElse(Option(a.group(4))).getOrElse("")
      }
      val relTokens = attributes.collectFirst { case ("rel", v) => read(v).split(' ').toList }.getOrElse(Nil)
      val rel = relTokens.toSet
      (if Set("script", "iframe", "svg", "math", "style", "object", "embed", "form", "base")(name) then
         List(s"the element ${m.matched}")
       else Nil) ++
        attributes.flatMap { (attr, value) =>
          if attr.startsWith("on") then List(s"the handler in ${m.matched}")
          else if (attr == "href" || attr == "src") && scriptAddress(name, attr, value) then
            List(s"the address in ${m.matched}")
          else if attr == "target" && (name != "a" || read(value) != "_blank") then List(s"the target in ${m.matched}")
          else if attr == "target" && !Set("noopener", "noreferrer").subsetOf(rel) then
            List(s"a new tab without noopener noreferrer in ${m.matched}")
          else if attr == "target" && rel("opener") then List(s"a new tab with opener in ${m.matched}")
          else if attr == "target" && relTokens.distinct.size != relTokens.size then
            List(s"a new tab with a repeated rel token in ${m.matched}")
          else Nil
        }
    }

  private val Pieces = Vector(
    "<script>alert(1)</script>",
    "<img src=x onerror=alert(1)>",
    """<a href="javascript:alert(1)">x</a>""",
    "[x](javascript:alert(1))",
    "[x](<javascript:alert(1)>)",
    "![x](javascript:alert(1))",
    "[x](JAVASCRIPT:alert(1))",
    "[x](&#106;avascript:alert(1))",
    "[x](java&#x09;script:alert(1))",
    "[x](vbscript:x)",
    "![x](data:image/svg+xml,x)",
    "![x](data:image/svg+xml;base64,PHN2ZyBvbmxvYWQ9YWxlcnQoMSk+)",
    "![x](DATA:IMAGE/SVG+XML;BASE64,PHN2Zz4=)",
    "![x](data:image/png,x)",
    "![x](data:text/html;base64,PHNjcmlwdD4=)",
    "![x](data:image/png;base64,iVBORw0KGgo=)",
    "[x](data:image/png;base64,iVBORw0KGgo=)",
    "<img src=\"data:image/svg+xml;base64,PHN2Zz4=\">",
    "<img src=\"data:image/gif;base64,R0lGODlh\" alt=\"g\">",
    "<a href=\"/ok\" target=\"_blank\">t</a>",
    "<a href=\"/ok\" target=\"_blank\" rel=\"opener\">t</a>",
    "<a href=\"/ok\" target=\"_blank\" rel=\"nofollow opener\">t</a>",
    "<a href=\"/ok\" rel=\"NoFollow noopener nofollow ugc noreferrer\" target=\"_blank\">t</a>",
    "<a href=\"/ok\" target=\"_top\">t</a>",
    "<a href=\"/ok\" target=\"w\">t</a>",
    "[x][r]\n\n[r]: javascript:alert(1)\n",
    "<javascript:alert(1)>",
    "<div onclick=\"x\">\n\nblock\n\n</div>",
    "<details ontoggle=x open>",
    "<svg/onload=alert(1)>",
    "<iframe src=javascript:x>",
    "<style>x</style>",
    "\n\n<script>\nalert(1)\n</script>\n\n",
    "<a href=\"/ok\" onmouseover=\"x\">y</a>",
    "<img src=x onerror=alert(1)",
    "`<script>`",
    "    <script>indented code</script>",
    "```\n<script>fenced</script>\n```",
    "**bold**",
    "*em*",
    "\n\n",
    "| a | b |\n|---|---|\n| 1 | 2 |\n",
    "[ok](https://example.org)",
    "![fig](/source/x/assets/f.png)",
    "text",
    " ",
    "<a class=\"src-ref\" data-ck=\"x\" href=\"/source/x\">X</a>",
    "<!-- c -->",
    "<",
    ">",
    "&",
    "\"",
    "'",
    "\n"
  )

  test("random hostile Markdown never renders a script, an event handler or a script address"):
    (1 to 3000).foreach { seed =>
      val r = new scala.util.Random(seed)
      val md = List.fill(1 + r.nextInt(10))(Pieces(r.nextInt(Pieces.length))).mkString
      val html = render(md)
      violations(html).foreach(v => fail(s"seed $seed: $v\ninput:  $md\noutput: $html"))
    }

  test("this suite's reader finds what it looks for"):
    assert(violations("<script>x</script>").nonEmpty)
    assert(violations("""<img src=x onerror=y>""").nonEmpty)
    assert(violations("""<a href="&#106;avascript:x">""").nonEmpty)
    assertEquals(violations("""<a href="/x" class="y">z</a>"""), Nil)
    assert(violations("""<img src="data:image/svg+xml;base64,PHN2Zz4=">""").nonEmpty)
    assert(violations("""<a href="data:image/png;base64,AAAA">""").nonEmpty)
    assert(violations("""<a href="/x" target="_top">""").nonEmpty)
    assert(violations("""<a href="/x" target="_blank">""").nonEmpty)
    assertEquals(violations("""<img src="data:image/png;base64,AAAA" alt="a" />"""), Nil)
    assertEquals(violations("""<a href="/x" target="_blank" rel="noopener noreferrer">"""), Nil)
    assert(violations("""<a href="/x" target="_blank" rel="opener noopener noreferrer">""").nonEmpty)
    assert(violations("""<a href="/x" target="_blank" rel="ugc ugc noopener noreferrer">""").nonEmpty)
    assertEquals(violations("""<a href="/x" target="_blank" rel="nofollow noopener noreferrer">"""), Nil)

  test("a new tab's link in Markdown keeps its other rel tokens and gains noopener noreferrer, once each"):
    val html = render("""See <a href="/ok" rel="NoFollow opener ugc nofollow" target="_blank">t</a> here.""")
    assert(html.contains("""<a href="/ok" target="_blank" rel="nofollow ugc noopener noreferrer">t</a>"""), html)
    (1 to 1000).foreach { seed =>
      val r = new scala.util.Random(seed)
      val tokens =
        List.fill(r.nextInt(6))(Vector("nofollow", "UGC", "opener", "noopener", "noreferrer", "me")(r.nextInt(6)))
      val md = s"""x <a href="/ok" target="_blank" rel="${tokens.mkString(" ")}">t</a> y"""
      val out = render(md)
      val kept = tokens.map(_.toLowerCase).filterNot(Set("opener", "noopener", "noreferrer")).distinct
      val want = (kept ++ List("noopener", "noreferrer")).mkString(" ")
      assert(out.contains(s"""rel="$want""""), s"seed $seed: $md\n$out")
      violations(out).foreach(v => fail(s"seed $seed: $v\ninput:  $md\noutput: $out"))
    }

  test("what the pages use still renders: a table, a link, an image, code, emphasis and the wikilink anchors"):
    val html = render(
      "| a | b |\n|:--|--:|\n| 1 | 2 |\n\n[ok](https://example.org/?a=1&b=2) ![Figure 1](/source/x/assets/f.png)\n\n" +
        "`code` **b** *i* <a class=\"src-ref\" data-ck=\"x\" href=\"/source/x\">X</a><sup class=\"cite-num\">" +
        "<a class=\"cite-jump\" href=\"#cite-1\">[1]</a></sup> <mark>m</mark> H<sub>2</sub>O"
    )
    List(
      "<table>",
      "<th",
      "<td",
      """<a href="https://example.org/?a=1&amp;b=2">ok</a>""",
      """<img src="/source/x/assets/f.png" alt="Figure 1" />""",
      "<code>code</code>",
      "<strong>b</strong>",
      "<em>i</em>",
      """<a class="src-ref" data-ck="x" href="/source/x">X</a>""",
      """<a class="cite-jump" href="#cite-1">[1]</a>""",
      "<mark>m</mark>",
      "<sub>2</sub>"
    ).foreach(want => assert(html.contains(want), s"$want\n$html"))

  test("hostile markup in a stored text stays readable, as text"):
    val html = render("Before <img src=x onerror=alert(1)> after.\n\n<script>alert(2)</script>\n")
    assert(html.contains("&lt;img src=x onerror=alert(1)&gt;"), html)
    assert(html.contains("&lt;script&gt;alert(2)&lt;/script&gt;"), html)

  test("a Markdown link with a script address keeps its text and loses its address"):
    val html = render("[click](javascript:alert(1)) and ![pic](data:image/svg+xml;base64,PHN2Zz4=)")
    assert(html.contains("click"), html)
    assert(!html.contains("javascript:") && !html.contains("data:image"), html)

  test("a Markdown image may be an inline base64 raster; a link to one is no link"):
    val html = render("![pic](data:image/png;base64,AAAA) and [link](data:image/png;base64,AAAA)")
    assert(html.contains("""<img src="data:image/png;base64,AAAA" alt="pic" />"""), html)
    assert(html.contains("link") && !html.contains("href=\"data:"), html)
