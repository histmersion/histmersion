package com.histmersion
package api

/** Properties of the allow-list cleaner over random hostile input (a seeded generator: a failure names its seed and
  * input, and replays). The output is read back by a reader of its own, not by the cleaner: every `<` must open a tag
  * of the canonical form, on the safe list, with no event handler and no address of a script, VBScript or data scheme.
  */
class HtmlSanitizePropertySuite extends munit.FunSuite:
  import HtmlSanitizePropertySuite.*

  test("random hostile markup never comes out with a script, an event handler or a script address"):
    (1 to Cases).foreach { seed =>
      val in = hostile(new scala.util.Random(seed))
      val out = Html.sanitize(in).markup
      violations(out).foreach(v => fail(s"seed $seed: $v\ninput:  $in\noutput: $out"))
    }

  test("the cleaner is idempotent over random input"):
    (1 to Cases).foreach { seed =>
      val once = Html.sanitize(hostile(new scala.util.Random(seed))).markup
      assertEquals(Html.sanitize(once).markup, once, s"seed $seed")
    }

  test("text with no `<` passes unchanged"):
    (1 to Cases).foreach { seed =>
      val r = new scala.util.Random(seed)
      val text = List.fill(r.nextInt(60))(TextChars(r.nextInt(TextChars.length))).mkString
      assertEquals(Html.sanitize(text).markup, text, s"seed $seed")
    }

  test("the reader of this suite does find what it looks for"):
    assert(violations("<script>x</script>").nonEmpty)
    assert(violations("""<img src="x" onerror="y">""").nonEmpty)
    assert(violations("""<a href="java&#x09;script:x">""").nonEmpty)
    assert(violations("""<a href="&#106;avascript:x">""").nonEmpty)
    assert(violations("a <b").nonEmpty)
    assertEquals(violations("""<a class="x" href="/y">z</a> &lt;script&gt;"""), Nil)
    assert(violations("""<img src="data:image/svg+xml;base64,PHN2Zz4=">""").nonEmpty)
    assert(violations("""<img src="data:image/png,x">""").nonEmpty)
    assert(violations("""<a href="data:image/png;base64,AAAA">""").nonEmpty)
    assert(violations("""<a href="/x" target="_top">""").nonEmpty)
    assert(violations("""<a href="/x" target="_blank">""").nonEmpty)
    assert(violations("""<a href="/x" target="_blank" rel="noopener">""").nonEmpty)
    assertEquals(violations("""<img src="data:image/png;base64,AAAA">"""), Nil)
    assertEquals(violations("""<a href="/x" target="_blank" rel="noopener noreferrer">"""), Nil)

object HtmlSanitizePropertySuite:
  val Cases = 4000

  /** The elements this suite accepts in the output: inert ones only. */
  val Safe: Set[String] = Set(
    "a",
    "abbr",
    "article",
    "aside",
    "b",
    "bdi",
    "bdo",
    "blockquote",
    "br",
    "caption",
    "cite",
    "code",
    "col",
    "colgroup",
    "dd",
    "del",
    "details",
    "dfn",
    "div",
    "dl",
    "dt",
    "em",
    "figcaption",
    "figure",
    "footer",
    "h1",
    "h2",
    "h3",
    "h4",
    "h5",
    "h6",
    "header",
    "hr",
    "i",
    "img",
    "ins",
    "kbd",
    "li",
    "mark",
    "ol",
    "p",
    "pre",
    "q",
    "rp",
    "rt",
    "ruby",
    "s",
    "samp",
    "section",
    "small",
    "span",
    "strong",
    "sub",
    "summary",
    "sup",
    "table",
    "tbody",
    "td",
    "tfoot",
    "th",
    "thead",
    "time",
    "tr",
    "u",
    "ul",
    "var",
    "wbr"
  )

  private val Tag = """<(/?)([a-z][a-z0-9]*)((?: [a-z][a-z0-9:-]*(?:="[^"<>]*")?)*)( /)?>""".r
  private val Attribute = """ ([a-z][a-z0-9:-]*)(?:="([^"<>]*)")?""".r

  private def decode(v: String): String =
    """&(?:#(\d+);?|#[xX]([0-9a-fA-F]+);?|(amp|lt|gt|quot|apos);)""".r.replaceAllIn(
      v,
      m =>
        java.util.regex.Matcher.quoteReplacement(
          if m.group(1) != null then new String(Character.toChars(m.group(1).toInt.min(0x10ffff)))
          else if m.group(2) != null then new String(Character.toChars(Integer.parseInt(m.group(2), 16).min(0x10ffff)))
          else Map("amp" -> "&", "lt" -> "<", "gt" -> ">", "quot" -> "\"", "apos" -> "'")(m.group(3))
        )
    )

  private def read(v: String): String =
    decode(v).filterNot(c => c == '\t' || c == '\n' || c == '\r').dropWhile(_ <= ' ').toLowerCase

  /** The one inline image this suite accepts: a base64 raster of a fixed list, nothing else in the address. */
  private val RasterImage = """data:image/(png|jpeg|gif|webp);base64,[a-z0-9+/]+={0,2}""".r

  private def scriptAddress(element: String, attribute: String, v: String): Boolean =
    val address = read(v)
    List("javascript:", "vbscript:").exists(address.startsWith) ||
    (address.startsWith("data:") && !(element == "img" && attribute == "src" && RasterImage.matches(address)))

  def violations(out: String): List[String] =
    val starts = out.indices.filter(out.charAt(_) == '<').toList
    starts.flatMap { at =>
      Tag.findPrefixMatchOf(out.substring(at)) match
        case None    => List(s"a `<` at $at opens no canonical tag: ${out.substring(at).take(40)}")
        case Some(m) =>
          val name = m.group(2)
          val attributes = Attribute.findAllMatchIn(m.group(3)).map(a => a.group(1) -> Option(a.group(2))).toList
          val rel = attributes.collectFirst { case ("rel", Some(v)) => read(v).split(' ').toSet }.getOrElse(Set.empty)
          (if Safe(name) then Nil else List(s"the element <$name>")) ++
            attributes.collect {
              case (a, _) if a.startsWith("on")                      => s"the event handler $a in ${m.matched}"
              case (a, _) if Set("style", "srcdoc", "formaction")(a) => s"the attribute $a in ${m.matched}"
              case (a, Some(v)) if (a == "href" || a == "src") && scriptAddress(name, a, v) =>
                s"the address $v in ${m.matched}"
              case ("target", v) if name != "a" || !v.map(read).contains("_blank") => s"the target $v in ${m.matched}"
              case ("target", _) if !Set("noopener", "noreferrer").subsetOf(rel)   =>
                s"a new tab without noopener noreferrer in ${m.matched}"
            }
    }

  val TextChars: Vector[Char] = "abc xyz 0123 &;:/=\"'>.\n\t#é".toVector

  private val Pieces: Vector[String] = Vector(
    "<script>alert(1)</script>",
    "<SCRIPT SRC=//x.example/a.js></SCRIPT>",
    "<script",
    "</script>",
    """<img src=x onerror=alert(1)>""",
    """<img src="x" onerror="alert(1)">""",
    "<img src=x onerror=alert(1)",
    """<IMG SRC="javascript:alert(1)">""",
    """<img src="data:image/svg+xml,<svg onload=alert(1)>">""",
    """<img src="data:image/svg+xml;base64,PHN2ZyBvbmxvYWQ9YWxlcnQoMSk+">""",
    """<img src="DATA:IMAGE/SVG+XML;BASE64,PHN2Zz4=">""",
    """<img src="data&#58;image/svg+xml;base64,PHN2Zz4=">""",
    """<img src="data:image/png,<svg onload=alert(1)>">""",
    """<img src="data:image/png;charset=utf-8;base64,AAAA">""",
    """<img src="data:image/png;base64,AAAA%3Csvg">""",
    """<img src="data:image/png;base64,AA&#32;AA">""",
    """<img src="data:image/png;base64,AA&#x0A;AA">""",
    """<img src="data:image/png;base64,PHN2Zz4=" onerror="x">""",
    """<img src='data:image/png;base64,AAAA' x="">""",
    """<img src="data:text/html;base64,PHNjcmlwdD4=">""",
    """<img src="data:image/jpg;base64,AAAA">""",
    """<img src="data:;base64,AAAA">""",
    """<img src="data:image/png;base64,iVBORw0KGgo=">""",
    """<img src=data:image/gif;base64,R0lGODlh>""",
    """<img src="data:image/webp;base64,UklGRg==" alt="ok">""",
    """<a href="data:image/png;base64,iVBORw0KGgo=">""",
    """<a href="/ok" target="_blank">""",
    """<a href="/ok" target=_BLANK rel="opener">""",
    """<a href="/ok" target="&#95;blank" rel="noopener">""",
    """<a href="/ok" target="_top">""",
    """<a href="/ok" target="_parent">""",
    """<a href="/ok" target="win">""",
    """<a href="/ok" target=" _blank">""",
    """<a href="/ok" target>""",
    """<a target="_blank" target="_self">""",
    """<span target="_blank">""",
    """<img src="/x.png" target="_blank">""",
    """<a href="javascript:alert(1)">x</a>""",
    """<a href=" javascript:alert(1)">""",
    """<a href="java&#x09;script:x">""",
    """<a href="&#106;avascript:x">""",
    """<a href="&#x6A;avascript:x">""",
    """<a href="&#0000106avascript:x">""",
    """<a href='JaVaScRiPt:alert(1)'>""",
    """<a href=javascript:alert(1)>""",
    """<a href="vbscript:msgbox(1)">""",
    """<a href="data:text/html;base64,PHNjcmlwdD4=">""",
    """<a href="java\nscript:x">""",
    """<a/href="javascript:x">""",
    """<a href="/ok" onclick="x">""",
    """<a href="/ok" onmouseover=x>""",
    """<div onclick="x">""",
    """<p style="x">""",
    "<svg onload=alert(1)>",
    "<svg><script>alert(1)</script></svg>",
    "<math><mi xlink:href=\"javascript:x\">",
    """<iframe src="javascript:x">""",
    """<iframe srcdoc="<script>x</script>">""",
    "<style>@import 'x'</style>",
    """<form action="javascript:x"><button formaction="javascript:x">""",
    """<input onfocus=x autofocus>""",
    """<object data="javascript:x">""",
    """<embed src="javascript:x">""",
    """<base href="javascript:x/">""",
    """<meta http-equiv="refresh" content="0;url=javascript:x">""",
    """<link rel="import" href="x">""",
    "<!-- <script>x</script> -->",
    "<!--",
    "-->",
    "<![CDATA[<script>x</script>]]>",
    "<?xml x?>",
    "<!DOCTYPE x>",
    "<scr<script>ipt>alert(1)</scr</script>ipt>",
    "<<script>>",
    "< script>",
    "<script/x>",
    "<a b=\"c\"d>",
    "<a b=`c`>",
    """<details open ontoggle=x>""",
    """<a href="https://ok.example/?a=1&amp;b=2">ok</a>""",
    """<a href="#cite-1">[1]</a>""",
    """<a class="src-ref" data-ck="x" href="/source/x">X</a>""",
    "<table><tr><td>1</td></tr></table>",
    "<details class=\"figtext\"><summary>s</summary><div>t<br>u</div></details>",
    "<etal>",
    "</etal>",
    "<jats:p>",
    "&lt;script&gt;",
    "&#60;script&#62;",
    "&",
    "<",
    ">",
    "\"",
    "'",
    "=",
    "/",
    " ",
    "\n",
    "text",
    "[x](javascript:x)",
    "![x](javascript:x)",
    "<b>",
    "</b>",
    "<p>",
    "</p>",
    "<span title=\"a onerror=b\">",
    "<br/>",
    "<hr />"
  )

  def hostile(r: scala.util.Random): String =
    List
      .fill(1 + r.nextInt(12)) {
        if r.nextInt(5) == 0 then TextChars(r.nextInt(TextChars.length)).toString else Pieces(r.nextInt(Pieces.length))
      }
      .mkString
