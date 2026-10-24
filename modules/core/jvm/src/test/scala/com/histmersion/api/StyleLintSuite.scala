package com.histmersion.api

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.config.given
import com.histmersion.domain.given

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Styling lives in `style.css`, not in Scala: the web UI's look must be adjustable by media queries
  * and swappable as a theme without touching code. What code may still put on an element is DATA —
  * a size, a position, a colour computed from a period — and only as a CSS custom property
  * (`--d:12px`), which a stylesheet rule then uses.
  *
  * The same kind of grep as [[ConfigSuite]]'s "no hardcoded ports" test, over the sources that build
  * the UI (the Scala.js front end, and `core`, which renders the server's HTML atoms). */
class StyleLintSuite extends munit.FunSuite:

  private val root = Path.of(sys.props.getOrElse("histmersion.repoRoot", "."))

  private val sources: List[Path] =
    List("modules/web/src/main/scala", "modules/core/shared/src/main/scala").map(root.resolve)
      .filter(Files.isDirectory(_))
      .flatMap(d => Files.walk(d).iterator.asScala.filter(_.toString.endsWith(".scala")).toList)

  /** A finding: where, and the offending text. */
  private final case class Hit(file: String, line: Int, text: String):
    override def toString = s"$file:$line  $text"

  /** Known exceptions: (file name, a fragment of the offending line, why). */
  private val allowed: List[(String, String, String)] = List(
    // canvas plumbing: the backing store is sized in device pixels, the element in CSS pixels
    ("GraphPanel.scala", "canvas.style.width", "canvas CSS size for device-pixel-ratio scaling"),
    ("GraphPanel.scala", "canvas.style.height", "canvas CSS size for device-pixel-ratio scaling")
  )

  private def isAllowed(h: Hit): Boolean =
    allowed.exists((f, frag, _) => h.file == f && h.text.contains(frag))

  /** Only custom properties: `--a:1px;--b:x` (spaces allowed). */
  private def onlyCustomProperties(decls: String): Boolean =
    decls.split(';').map(_.trim).filter(_.nonEmpty).forall(_.startsWith("--"))

  private def scan(p: Path)(find: (String, Int, Array[String]) => Option[String]): List[Hit] =
    val lines = Files.readAllLines(p).asScala.toArray
    lines.indices.toList.flatMap(i => find(lines(i), i, lines).map(t => Hit(p.getFileName.toString, i + 1, t.trim)))

  private val StyleProps =
    "display|left|top|right|bottom|width|height|zIndex|color|background|backgroundColor|opacity|cursor|" +
      "visibility|position|fontSize|padding|margin|transform|maxWidth|maxHeight|minWidth|minHeight|boxShadow"
  private val LaminarStyle = s"""(?<![.\\w])($StyleProps)\\s*(<--|:=)""".r
  private val StyleAttr    = """styleAttr\s*(<--|:=)""".r
  private val FirstLiteral = """s?"([^"]*)"""".r
  private val HtmlStyle    = """style=\\?"([^"]*)"""".r
  private val DomStyle     = """\.style\.(?!setProperty\("--)\w+|cssText""".r
  /** A colour VALUE: quoted hex, or rgb()/rgba() with numbers — not a function that formats one. */
  private val ColourLit    = """"#[0-9a-fA-F]{3,8}"|#[0-9a-fA-F]{6}\b|rgba?\(\s*\d""".r

  private def findings: List[Hit] = sources.flatMap { p =>
    scan(p) { (line, i, all) =>
      val code = line
      if code.trim.startsWith("//") || code.trim.startsWith("*") then None
      else
        LaminarStyle.findFirstIn(code).map(_ => code)
          .orElse(StyleAttr.findFirstIn(code).flatMap { _ =>
            // the value may start on a following line (`styleAttr <-- a.map { … =>` then the string)
            val window = code.drop(code.indexOf("styleAttr")) + "\n" + all.slice(i + 1, i + 6).mkString("\n")
            FirstLiteral.findFirstMatchIn(window).map(_.group(1)).filterNot(onlyCustomProperties).map(_ => code)
          })
          .orElse(HtmlStyle.findAllMatchIn(code).map(_.group(1)).find(!onlyCustomProperties(_)).map(_ => code))
          .orElse(DomStyle.findFirstIn(code).map(_ => code))
          .orElse(ColourLit.findFirstIn(code).map(_ => code))
    }
  }

  test("the UI's Scala sets no styles, only data as CSS custom properties (and no colour literals)"):
    assert(sources.nonEmpty, s"no sources found under $root")
    val offenders = findings.filterNot(isAllowed)
    assertEquals(offenders.map(_.toString), Nil,
      "move the styling into modules/server/src/main/resources/web/style.css; pass data as --custom-properties")

  test("every error line is announced: a `form-error` carries role=alert (or is web.ErrorLine) — #1798"):
    val unannounced = sources.filter(_.toString.contains("modules/web")).flatMap { p =>
      scan(p)((line, _, _) => Option.when(line.contains("\"form-error\"") && !line.contains("role := \"alert\""))(line))
    }
    assertEquals(unannounced.map(_.toString), Nil, "use ErrorLine(message) — a screen reader must hear the error")

  test("every allowed exception still matches something (so the list shrinks as the code does)"):
    val hits = findings
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
