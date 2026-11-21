package com.histmersion
package server
package comments

class SourceCommentsSuite extends munit.FunSuite:

  private def texts(language: SourceLanguage, source: String): List[String] =
    language.comments(source).map(_.text).toList

  private def scalaFile(source: String): CommentedFile =
    CommentScope.default.parsed("A.scala", source).getOrElse(fail("A.scala is no Scala file"))

  private def blocks(language: SourceLanguage, source: String): List[CommentBlock] =
    CommentBlock.runs(language, source, language.comments(source)).toList

  test("scala: line, block, nested block and doc comments, never inside strings, chars or backticks"):
    val source =
      """|val url = "https://example.org" // trailing
         |/* outer /* inner */ still outer */
         |/** doc */
         |val q = '"'
         |val s = s"${x + "//no"} // no"
         |val r = QQQ/* no */QQQ
         |val `a//b` = 1
         |val c = '\''
         |""".stripMargin.replace("QQQ", "\"\"\"")
    assertEquals(
      texts(SourceLanguage.Scala, source),
      List("// trailing", "/* outer /* inner */ still outer */", "/** doc */")
    )

  test("scala: a comment inside an interpolation's code is found, and a quote is no char literal"):
    val source = "val s = s\"${ f(1) /* why */ }\"\nval e = '{ x }\n// after"
    assertEquals(texts(SourceLanguage.Scala, source), List("/* why */", "// after"))

  test("scala: comments carry their lines"):
    val found = SourceLanguage.Scala.comments("val a = 1\n/* one\n   two */\nval b = 2 // three\n")
    assertEquals(found.map(c => (c.firstLine, c.lastLine)).toList, List((2, 3), (4, 4)))

  test("javascript: template literals, regex literals and strings hide comment markers"):
    val source =
      """|const u = 'http://x' // one
         |const t = `a ${b /* two */} //no`
         |const r = /[/*]+/g.test(s) /* three */
         |const d = a / b // four
         |""".stripMargin
    assertEquals(texts(SourceLanguage.JavaScript, source), List("// one", "/* two */", "/* three */", "// four"))

  test("css: only block comments, never a url"):
    assertEquals(
      texts(SourceLanguage.Css, "a { background: url(http://x/y.png); } /* note */\n"),
      List("/* note */")
    )

  test("sql: line and block comments outside strings and dollar quotes"):
    val source = "select '--no', $$ -- no $$, \"a--b\" -- yes\n/* block */ from t where x = $1 -- two\n"
    assertEquals(texts(SourceLanguage.Sql, source), List("-- yes", "/* block */", "-- two"))

  test("python: hash comments and docstrings, never a hash inside a string or a plain string statement"):
    val source =
      """|QQQModule doc.QQQ
         |x = "#no"  # yes
         |def f(a):
         |    QQQDoc of f.QQQ
         |    y = '''# not a comment'''
         |    return f"{a['#']}"
         |class C:
         |    r'''Raw doc.'''
         |    z = 1
         |if a:
         |    "a plain string statement"
         |""".stripMargin.replace("QQQ", "\"\"\"")
    assertEquals(
      texts(SourceLanguage.Python, source),
      List("\"\"\"Module doc.\"\"\"", "# yes", "\"\"\"Doc of f.\"\"\"", "r'''Raw doc.'''")
    )

  test("shell: a hash starting a word, never inside quotes, parameter expansions or heredocs"):
    val source =
      """|#!/usr/bin/env bash
         |echo "#no" '#no' ${#x} ${x#y} # yes
         |cat <<'EOF'
         |# heredoc text
         |EOF
         |cat <<-END
         |	# indented heredoc text
         |	END
         |# after
         |""".stripMargin
    assertEquals(texts(SourceLanguage.Shell, source), List("#!/usr/bin/env bash", "# yes", "# after"))

  test("html: markup comments and the comments of inline scripts and styles"):
    val source = "<!-- one -->\n<script>\nlet a = 1 // two\n</script>\n<style>a{} /* three */</style>\n"
    assertEquals(texts(SourceLanguage.Html, source), List("<!-- one -->", "// two", "/* three */"))

  test("adjacent line comments form one block; a blank line or code separates blocks"):
    val found = blocks(SourceLanguage.Scala, "// a\n// b\n\n// c\nval x = 1\n// d\n")
    assertEquals(found.map(_.lineRange), List("1-2", "4", "6"))
    assertEquals(found.head.text, "// a\n// b")

  test("functional comments are directives, kept apart from their neighbours"):
    val found = blocks(SourceLanguage.Scala, "//> using scala 3.10.1\n// prose\n// format: off\n")
    assertEquals(found.map(b => (b.lineRange, b.directive)), List(("1", true), ("2", false), ("3", true)))
    assert(SourceLanguage.Shell.directive("# shellcheck disable=SC2086"))
    assert(SourceLanguage.Python.directive("# noqa: E501"))
    assert(SourceLanguage.JavaScript.directive("// eslint-disable-next-line"))
    assert(!SourceLanguage.Scala.directive("// the reason"))

  test("an inline script header is one directive block from its opening to its closing line"):
    val found = blocks(SourceLanguage.Python, "# /// script\n# dependencies = []\n# ///\n# prose\n")
    assertEquals(found.map(b => (b.lineRange, b.directive)), List(("1-3", true), ("4", false)))

  test("blocks say their kind"):
    val found = blocks(SourceLanguage.Scala, "/** d */\nval a = 1\n/* b */\nval c = 2\n// l\n")
    assertEquals(found.map(_.kind), List("doc", "block", "line"))

  test("languages are told by extension, or by the shebang of a file without one"):
    assertEquals(SourceLanguage.of("a/B.scala", ""), Some(SourceLanguage.Scala))
    assertEquals(SourceLanguage.of("build.sbt", ""), Some(SourceLanguage.Scala))
    assertEquals(SourceLanguage.of("x/y.mjs", ""), Some(SourceLanguage.JavaScript))
    assertEquals(SourceLanguage.of("bin/run", "#!/bin/sh"), Some(SourceLanguage.Shell))
    assertEquals(SourceLanguage.of("bin/tool", "#!/usr/bin/env python3"), Some(SourceLanguage.Python))
    assertEquals(SourceLanguage.of("README.md", ""), None)

  test("the scope leaves out audit output and generated trees, and exempts migrations, vendored code and fixtures"):
    val scope = CommentScope.default
    assert(!scope.includes("refactor-audit/x/A.scala"))
    assert(!scope.includes("modules/web/node_modules/a.js"))
    assert(scope.includes("modules/plugin-api/src/main/scala/com/histmersion/plugin/Plugin.scala"))
    assert(scope.exemption("modules/db/src/main/resources/db/migration/V1__a.sql", SourceLanguage.Sql).isDefined)
    assert(scope.exemption("modules/server/src/main/resources/web/vendor/x/x.js", SourceLanguage.JavaScript).isDefined)
    assert(scope.exemption("modules/server/src/test/resources/acquire/a.html", SourceLanguage.Html).isDefined)
    assert(scope.exemption("modules/server/src/main/resources/web/index.html", SourceLanguage.Html).isEmpty)

  test("an exempt file counts no comment"):
    val file =
      CommentScope.default.parsed("modules/db/src/main/resources/db/migration/V1__a.sql", "-- one\nselect 1;\n")
    assertEquals(file.map(f => (f.counted.size, f.exempt.size)), Some((0, 1)))

  test("a script's header that its own --help prints is functional, not a comment"):
    val shell =
      "#!/usr/bin/env bash\n# Usage: run.sh <dir>\n#   does things\nset -euo pipefail\n# prose\n" +
        "case \"$1\" in -h) sed -n '2,/^set -euo/p' \"$0\" | sed '$d'; exit 0 ;; esac\n"
    val numbered = "#!/bin/sh\n# usage\n# more\n\n# after\necho\n[ \"$1\" = -h ] && sed -n '2,3p' \"$0\"\n"
    val python = "QQQUsage: tool.py <x>QQQ\n# prose\nprint(__doc__)\n".replace("QQQ", "\"\"\"")
    def counted(path: String, text: String) =
      CommentScope.default.parsed(path, text).map(_.counted.map(_.lineRange).toList)
    assertEquals(counted("run.sh", shell), Some(List("5")))
    assertEquals(counted("n.sh", numbered), Some(List("5")))
    assertEquals(counted("tool.py", python), Some(List("2")))

  test("api doc: each excuse is found, word-bounded and in any case; ordinary words are not excuses"):
    val hits = List(
      "workaround" -> "A WORKAROUND for the upstream bug.",
      "workaround" -> "We work around the parser.",
      "hack" -> "A hack.",
      "for now" -> "Strings for now.",
      "temporary" -> "Temporarily public.",
      "TODO" -> "TODO: typed.",
      "TODO" -> "fixme",
      "until" -> "Blocks until the job ends.",
      "kept for old" -> "Kept so that old configs load.",
      "legacy" -> "The legacy name.",
      "kept for old builds" -> "The constructor of a provider compiled before reasoning existed.",
      "kept for old builds" -> "A default, so an earlier context still compiles.",
      "should be but" -> "It should be a Doi but the host sends text.",
      "cannot because" -> "It cannot be typed because the SPI is frozen."
    )
    hits.foreach((name, text) => assertEquals(ApiDoc(s"/** $text */", "def f: Int").excuse, Some(name), text))
    List("Hackathon entries.", "Temporal span in years.", "The old format is read.", "Untiled maps.").foreach(text =>
      assertEquals(ApiDoc(s"/** $text */", "def f: Int").excuse, None, text)
    )

  test("api doc: a doc made of its signature's own words restates it; one that adds meaning does not"):
    assert(ApiDoc("/** Returns the source id. */", "def sourceId: Source.Id").tautology)
    assert(ApiDoc("/** The plugin's settings. */", "def settings: Plugin.Settings").tautology)
    val explains = ApiDoc(
      "/** Seconds to wait, at most 300; the call fails with `Timeout` after it. */",
      "def timeout: Duration"
    )
    assert(!explains.tautology, explains.restatedShare.toString)
    assert(explains.restatedShare < ApiDoc.Threshold)

  test("api doc: a @param or @return that only names the parameter or type restates it"):
    val doc = "/** Fetches the record once, from the cache when it is fresh.\n  * @param citekey the citekey\n  */"
    assertEquals(ApiDoc(doc, "def fetch(citekey: Citekey): Fetched").restatedTags, List("@param citekey the citekey"))
    val useful = "/** Fetches the record once.\n  * @param timeout seconds before the call gives up, at most 300\n  */"
    assertEquals(ApiDoc(useful, "def fetch(timeout: Duration): Fetched").restatedTags, Nil)
    val continued = "/** Fetches.\n  * @param citekey the citekey,\n  *   lower-cased first\n  * @param id the id\n  */"
    val mixed = ApiDoc(continued, "def fetch(citekey: Citekey, id: Id): Fetched")
    assertEquals(mixed.restatedTags, List("@param id the id"))
    assertEquals(mixed.restatedTagLines, List(3))

  test("api doc: only a public definition may carry one"):
    assert(ApiDoc("/** x */", "def f: Int").public)
    assert(ApiDoc("/** x */", "final case class Box(width: Int)").public)
    assert(ApiDoc("/** x */", "@deprecated(\"y\") inline def f: Int").public)
    assert(!ApiDoc("/** x */", "private def f: Int").public)
    assert(!ApiDoc("/** x */", "private[plugin] final class Hidden").public)
    assert(!ApiDoc("/** x */", "protected def f: Int").public)
    assert(!ApiDoc("/** x */", "import a.b").public)
    assertEquals(
      ApiDoc.declarationAfter("/** x */\n@nowarn\ndef f(\n  a: Int\n): Int =\n  a\n", 8),
      "def f( a: Int ): Int ="
    )

  test("api doc: kept only in a designated public-API module's main sources, on a public definition"):
    val source =
      "/** Seconds to wait, at most 300; the call fails with `Timeout` after it. */\ndef timeout: Duration = d\n" +
        "/** Returns the source id. */\ndef sourceId: Source.Id = s\n" +
        "/** Seconds to wait, at most 300; an internal helper. */\nprivate def wait: Duration = d\n"
    def counted(path: String) = CommentScope.default.parsed(path, source).map(_.counted.map(_.lineRange).toList)
    assertEquals(counted("modules/plugin-api/src/main/scala/P.scala"), Some(List("3", "5")))
    assertEquals(counted("modules/plugin-testkit/src/main/scala/P.scala"), Some(List("3", "5")))
    assertEquals(counted("modules/plugin-api/src/test/scala/P.scala"), Some(List("1", "3", "5")))
    assertEquals(counted("modules/core/src/main/scala/P.scala"), Some(List("1", "3", "5")))

  test("files are counted in their module"):
    def module(path: String) = CommentScope.default.parsed(path, "").map(_.module)
    assertEquals(module("modules/core/src/main/scala/A.scala"), Some("core"))
    assertEquals(module("modules/plugins/media/x/src/main/scala/A.scala"), Some("plugins/media"))
    assertEquals(module("scripts/ci/check.py"), Some("repo/scripts"))
    assertEquals(module("db/places/build.py"), Some("repo/db"))
    assertEquals(module("build.sbt"), Some("repo/build.sbt"))

  test("removing a whole-line comment drops its lines; a trailing one leaves the code"):
    val source = "val a = 1 // why\n/** doc\n  * more */\ndef f = 2\n"
    val file = scalaFile(source)
    val removal = file.removing(file.counted)
    assertEquals(removal.result, "val a = 1\ndef f = 2\n")
    assertEquals(removal.linesShed, 2)

  test("removing a comment between blank lines drops one of the blank lines it separated"):
    val source = "val a = 1\n\n// note\n\nval b = 2\n"
    val file = scalaFile(source)
    val removal = file.removing(file.counted)
    assertEquals(removal.result, "val a = 1\n\nval b = 2\n")
    assertEquals((removal.linesShed, removal.separatorLinesShed), (2, 1))

  test("removing a comment before a closing brace drops the blank line left above it"):
    val source = "object A {\n  val a = 1\n\n  // note\n}\n"
    val file = scalaFile(source)
    assertEquals(file.removing(file.counted).result, "object A {\n  val a = 1\n}\n")

  test("removing an inline block comment keeps the code around it"):
    val source = "f(/* why */ 1)\n"
    val file = scalaFile(source)
    assertEquals(file.removing(file.counted).result, "f(1)\n")

  test("removing only the chosen blocks keeps the others"):
    val source = "// a\nval x = 1\n// b\nval y = 2\n"
    val file = scalaFile(source)
    assertEquals(file.removing(file.counted.take(1)).result, "val x = 1\n// b\nval y = 2\n")
