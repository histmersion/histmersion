package com.histmersion
package api

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Styling lives in `style.css`, not in Scala: the web UI's look must be adjustable by media queries and swappable as a
  * theme without touching code. What code may still put on an element is DATA — a size, a position, a colour computed
  * from a period — and only as a CSS custom property (`--d:12px`), which a stylesheet rule then uses.
  *
  * The same kind of reading as [[ConfigSuite]]'s "no hardcoded ports" test, over the sources that build the UI (the
  * Scala.js front end, and `core`, which renders the server's HTML atoms). Each check reads an expression — a match in
  * the code, an argument, an element's attributes — never a line, so a formatter that breaks an expression over lines
  * does not change what it finds.
  */
class StyleLintSuite extends munit.FunSuite:
  import StyleLintSuite.*

  private val root = Path.of(sys.props.getOrElse("histmersion.repoRoot", "."))
  private val sources: List[UiSource] = UiSource.all(root)

  test("the UI's Scala sets no styles, only data as CSS custom properties (and no colour literals)"):
    assert(sources.nonEmpty, s"no sources found under $root")
    val offenders = sources.flatMap(styleFindings).filterNot(isAllowed)
    assertEquals(
      offenders.map(_.toString),
      Nil,
      "move the styling into modules/server/src/main/resources/web/style.css; pass data as --custom-properties"
    )

  test("every error line is announced: a `form-error` carries role=alert (or is web.ErrorLine) — #1798"):
    val unannounced = sources.filter(_.path.toString.contains("modules/web")).flatMap(unannouncedErrors)
    assertEquals(unannounced.map(_.toString), Nil, "use ErrorLine(message) — a screen reader must hear the error")

  test("every allowed exception still matches something (so the list shrinks as the code does)"):
    val hits = sources.flatMap(styleFindings)
    val stale = allowed.filterNot((f, frag, _) => hits.exists(h => h.file == f && h.text.contains(frag)))
    assertEquals(stale.map((f, frag, why) => s"$f [$frag] — $why"), Nil, "remove these from `allowed`")

  test("the checks themselves catch what they are for"):
    assert(!onlyCustomProperties("color:red"))
    assert(!onlyCustomProperties("--x:1px;left:3px"))
    assert(onlyCustomProperties("--x:1px; --y: 2px;"))
    assert(LaminarStyle.findFirstIn("""left <-- s.map(x => s"${x}px"),""").isDefined)
    assert(LaminarStyle.findFirstIn("""svg.width := "10",""").isEmpty)
    assert(DomStyle.findFirstIn("""box.style.left = "1px"""").isDefined)
    assert(DomStyle.findFirstIn("""box.style.setProperty("--tip-x", v)""").isEmpty)
    assert(ColourLit.findFirstIn("""val c = "#c2410c"""").isDefined)
    assert(ColourLit.findFirstIn("""desaturate("rgb(33,145,140)", c)""").isDefined)
    assert(ColourLit.findFirstIn("""s"rgb(${r},${g},${b})"""").isEmpty, "formatting a computed colour is not a literal")
    assert(ColourLit.findFirstIn("""def rgb(value: String)""").isEmpty)

  test("an error line is judged by its element's attributes, however they are laid out"):
    def unannounced(text: String): Int = unannouncedErrors(UiSource(Path.of("V.scala"), text)).size
    assertEquals(unannounced("""p(cls := "form-error", role := "alert", m)"""), 0)
    assertEquals(unannounced("div(\n  cls := \"form-error\",\n  role := \"alert\",\n  p_(\"x\")\n)"), 0)
    assertEquals(unannounced("div(\n  cls := \"form-error\",\n  p_(\"x\")\n)"), 1)
    assertEquals(
      unannounced("""div(cls := "form-error", p(role := "alert"))"""),
      1,
      "the role of a child is not the element's"
    )
    assertEquals(unannounced("// a \"form-error\" in a comment\nval x = 1"), 0)
    assertEquals(unannounced("p(cls := \"form-error\", s\"${f(\"a\", \"b\")}\",\n  role := \"alert\")"), 0)

  test("a style attribute's value is read from its argument, however far the formatter moves it"):
    def found(text: String): Int = styleFindings(UiSource(Path.of("V.scala"), text)).size
    assertEquals(found("styleAttr <-- s.map { v =>\n\n\n\n\n\n\n  s\"left:${v}px\"\n}"), 1)
    assertEquals(found("styleAttr <-- s.map { v =>\n\n\n\n\n\n\n  s\"--x:${v}px\"\n}"), 0)
    assertEquals(found("styleAttr := s\"--x:1px\",\nlabel := \"color:red\""), 0)

object StyleLintSuite:

  /** One source: its path, text, and the text with comments and string literals masked (offsets kept). */
  final case class UiSource(path: Path, text: String):
    val mask: Array[Byte] = Mask(text)

    /** The text with comments blanked: code and string literals as written. */
    lazy val withoutComments: String = text.indices.map(i => if mask(i) == Mask.Comment then ' ' else text(i)).mkString

    /** The text with comments and string literals blanked: the code alone. */
    lazy val code: String = text.indices.map(i => if mask(i) == Mask.Code then text(i) else ' ').mkString

    def lineOf(offset: Int): Int = text.substring(0, offset).count(_ == '\n') + 1

    def lineText(offset: Int): String =
      val start = text.lastIndexOf('\n', offset - 1) + 1
      val end = text.indexOf('\n', offset) match
        case -1 => text.length
        case e  => e
      text.substring(start, end).trim

    def hit(offset: Int): Hit = Hit(path.getFileName.toString, lineOf(offset), lineText(offset))

  object UiSource:
    def all(root: Path): List[UiSource] =
      List("modules/web/src/main/scala", "modules/api/src/main/scala")
        .map(root.resolve)
        .filter(Files.isDirectory(_))
        .flatMap(d => Files.walk(d).iterator.asScala.filter(_.toString.endsWith(".scala")).toList)
        .map(p => UiSource(p, Files.readString(p)))

  /** A finding: where (the line, for the reader), and the line's text. */
  final case class Hit(file: String, line: Int, text: String):
    override def toString = s"$file:$line  $text"

  /** Known exceptions: (file name, a fragment of the offending line, why). */
  val allowed: List[(String, String, String)] = List(
    // canvas plumbing: the backing store is sized in device pixels, the element in CSS pixels
    ("GraphPanel.scala", "canvas.style.width", "canvas CSS size for device-pixel-ratio scaling"),
    ("GraphPanel.scala", "canvas.style.height", "canvas CSS size for device-pixel-ratio scaling")
  )

  def isAllowed(h: Hit): Boolean =
    allowed.exists((f, frag, _) => h.file == f && h.text.contains(frag))

  /** Only custom properties: `--a:1px;--b:x` (spaces allowed). */
  def onlyCustomProperties(decls: String): Boolean =
    decls.split(';').map(_.trim).filter(_.nonEmpty).forall(_.startsWith("--"))

  private val StyleProps =
    "display|left|top|right|bottom|width|height|zIndex|color|background|backgroundColor|opacity|cursor|" +
      "visibility|position|fontSize|padding|margin|transform|maxWidth|maxHeight|minWidth|minHeight|boxShadow"
  val LaminarStyle = s"""(?<![.\\w])($StyleProps)\\s*(<--|:=)""".r
  private val StyleAttr = """\bstyleAttr\s*(<--|:=)""".r
  private val FirstLiteral = """s?"([^"]*)"""".r
  private val HtmlStyle = """style=\\?"([^"]*)"""".r
  val DomStyle = """\.style\.(?!setProperty\("--)\w+|cssText""".r

  /** A colour VALUE: quoted hex, or rgb()/rgba() with numbers — not a function that formats one. */
  val ColourLit = """"#[0-9a-fA-F]{3,8}"|#[0-9a-fA-F]{6}\b|rgba?\(\s*\d""".r

  /** Every styling the UI's code does: a Laminar style property set, a `styleAttr` whose value sets more than custom
    * properties, an HTML `style="…"` that does, a DOM style written, a colour literal.
    */
  def styleFindings(s: UiSource): List[Hit] =
    val laminar = LaminarStyle.findAllMatchIn(s.code).map(_.start)
    val styleAttr = StyleAttr.findAllMatchIn(s.code).filter { m =>
      val value = s.withoutComments.substring(m.end, argumentEnd(s, m.end))
      FirstLiteral.findFirstMatchIn(value).map(_.group(1)).exists(!onlyCustomProperties(_))
    }
    val html = HtmlStyle.findAllMatchIn(s.withoutComments).filterNot(m => onlyCustomProperties(m.group(1)))
    val dom = DomStyle.findAllMatchIn(s.withoutComments).filter(m => s.mask(m.start) == Mask.Code)
    val colours = ColourLit.findAllMatchIn(s.withoutComments)
    (laminar ++ styleAttr.map(_.start) ++ html.map(_.start) ++ dom.map(_.start) ++ colours.map(
      _.start
    )).toList.distinct.sorted
      .map(s.hit)

  /** The `"form-error"` literals whose element does not carry `role := "alert"` among its own attributes. */
  def unannouncedErrors(s: UiSource): List[Hit] =
    val FormError = "\"form-error\""
    Iterator
      .iterate(s.text.indexOf(FormError))(i => s.text.indexOf(FormError, i + 1))
      .takeWhile(_ >= 0)
      .filter(i => s.mask(i) == Mask.Literal)
      .filterNot(i => elementArguments(s, i).exists(_.exists(a => Announced.matches(a.trim))))
      .map(s.hit)
      .toList

  private val Announced = """role\s*:=\s*"alert"""".r

  /** The arguments of the call whose argument list holds `offset` (`div(a, b)` → `a`, `b`), or none when the offset is
    * inside a block or a type argument rather than directly in an argument list.
    */
  def elementArguments(s: UiSource, offset: Int): Option[List[String]] =
    val code = s.code
    var i = offset - 1
    var depth = 0
    var open = -1
    while i >= 0 && open < 0 do
      code(i) match
        case ')' | ']' | '}'              => depth += 1
        case '(' | '[' | '{' if depth > 0 => depth -= 1
        case '(' | '[' | '{'              => open = i
        case _                            => ()
      i -= 1
    Option.when(open >= 0 && code(open) == '(') {
      val args = List.newBuilder[String]
      var start = open + 1
      var at = start
      var end = -1
      depth = 0
      while at < code.length && end < 0 do
        code(at) match
          case '(' | '[' | '{'              => depth += 1
          case ')' | ']' | '}' if depth > 0 => depth -= 1
          case ')' | ']' | '}'              => end = at
          case ',' if depth == 0            => args += s.text.substring(start, at); start = at + 1
          case _                            => ()
        at += 1
      if end >= 0 then args += s.text.substring(start, end)
      args.result()
    }

  /** Where the argument starting at `from` ends: the first `,` or closing bracket outside nested brackets. */
  def argumentEnd(s: UiSource, from: Int): Int =
    val code = s.code
    var at = from
    var depth = 0
    var end = -1
    while at < code.length && end < 0 do
      code(at) match
        case '(' | '[' | '{'                     => depth += 1
        case ')' | ']' | '}' if depth > 0        => depth -= 1
        case ')' | ']' | '}' | ',' if depth == 0 => end = at
        case _                                   => ()
      at += 1
    if end < 0 then code.length else end

  /** What each character of a source is: code, part of a string literal (a splice `${…}` is code), or a comment. */
  object Mask:
    val Code: Byte = 0
    val Literal: Byte = 1
    val Comment: Byte = 2

    def apply(text: String): Array[Byte] =
      val out = new Array[Byte](text.length)
      val n = text.length
      def fill(from: Int, until: Int, kind: Byte): Int =
        java.util.Arrays.fill(out, from, math.min(until, n), kind)
        math.min(until, n)

      def code(from: Int, splice: Boolean): Int =
        var i = from
        var depth = 0
        var done = false
        while i < n && !done do
          val c = text(i)
          if text.startsWith("//", i) then
            i = fill(i, text.indexOf('\n', i) match { case -1 => n; case e => e }, Comment)
          else if text.startsWith("/*", i) then
            var nested = 0
            val start = i
            while i < n && (nested > 0 || i == start) do
              if text.startsWith("/*", i) then { nested += 1; i += 2 }
              else if text.startsWith("*/", i) then { nested -= 1; i += 2 }
              else i += 1
            val _ = fill(start, i, Comment)
          else if c == '"' then i = string(i)
          else if c == '\'' && i + 2 < n && (text(i + 2) == '\'' || text(i + 1) == '\\') then
            val end = text.indexOf('\'', i + 2)
            i = fill(i, if end < 0 || end - i > 8 then i + 1 else end + 1, Literal)
          else if splice && c == '}' && depth == 0 then done = true
          else
            if splice && c == '{' then depth += 1
            else if splice && c == '}' then depth -= 1
            i += 1
        i

      def string(from: Int): Int =
        val interpolated = from > 0 && (text(from - 1).isLetterOrDigit || text(from - 1) == '_')
        val triple = text.startsWith("\"\"\"", from)
        var i = fill(from, from + (if triple then 3 else 1), Literal)
        var done = false
        while i < n && !done do
          val c = text(i)
          if triple && text.startsWith("\"\"\"", i) then
            i = fill(i, i + 3, Literal)
            while i < n && text(i) == '"' do i = fill(i, i + 1, Literal)
            done = true
          else if !triple && (c == '"' || c == '\n') then
            if c == '"' then i = fill(i, i + 1, Literal)
            done = true
          else if interpolated && text.startsWith("$$", i) then i = fill(i, i + 2, Literal)
          else if interpolated && text.startsWith("${", i) then
            i = code(fill(i, i + 2, Literal), splice = true)
            if i < n then i = fill(i, i + 1, Literal)
          else if !triple && c == '\\' && i + 1 < n && text(i + 1) != '\n' then i = fill(i, i + 2, Literal)
          else i = fill(i, i + 1, Literal)
        i

      val _ = code(0, splice = false)
      out
