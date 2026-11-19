package com.histmersion
package server

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** The direction between domains and supporting areas, read from the sources of `api`, `config`, `files`, `core`, `db`
  * and `server`:
  *
  *   1. direction — a file names a type of a place it may not depend on: a supporting area naming a domain, a domain
  *      naming a higher domain, anything but the composition root naming the composition root, personal naming another
  *      domain;
  *   2. file system — `java.nio.file`, `java.io.File`, file channels and streams outside `util.storage`, the
  *      composition root, the config loading and the stand-alone tools; process — `ProcessBuilder` outside
  *      `util.process`;
  *   3. repository — a `…Repo` object named outside its feature.
  *
  * A member of a type (`Queue.Kind.Acquire`, and an enum case where the enum defines it) is the type's own word.
  *
  * A file's place is its row in `architecture/places.tsv`, the design's table of where each file moves, while it is not
  * there yet; then its package (`com.histmersion.domain.<domain>.<feature>`, `com.histmersion.util.<area>`). The
  * violations that exist today are listed in `architecture/exceptions.tsv`; the list is exact both ways, so a new
  * violation fails and so does a row nothing violates any more. Each slice of the refactor removes rows and adds none;
  * `HISTMERSION_UPDATE_ARCHITECTURE=1` rewrites the list from the sources, falls only: never a missing file, and never
  * a row new or higher than the file at the merge base with the pilot ([[RatchetBase]]) that the owner's accept script
  * did not accept.
  */
class ArchitectureSuite extends munit.FunSuite:
  import ArchitectureSuite.*

  private lazy val sources: List[SourceFile] = Modules.flatMap(SourceFile.all(_))
  private lazy val places: Map[String, String] = readPlaces()
  private lazy val placed: List[(SourceFile, String)] = sources.map(s => s -> placeOf(s))
  private lazy val violations: List[Violation] = Rules.all(placed)

  private def placeOf(s: SourceFile): String =
    places.get(s.key).orElse(Place.ofPackage(s.pkg)).getOrElse(Unplaced)

  private def readPlaces(): Map[String, String] =
    Files
      .readAllLines(Resources.resolve("places.tsv"), UTF_8)
      .asScala
      .filterNot(_.startsWith("#"))
      .map(_.split('\t'))
      .collect { case Array(file, place) => file -> place }
      .toMap

  test("every source file has a place: its feature package, or a row of places.tsv"):
    val unplaced = placed.collect { case (s, Unplaced) => s"${s.key} (package ${s.pkg})" }
    assertEquals(unplaced, Nil, "put the file in a feature package, or give it a row in architecture/places.tsv")

  test("places.tsv names only files that exist and are not in their place's package yet"):
    val keys = sources.map(s => s.key -> s).toMap
    val stale = places.toList.sorted.collect {
      case (k, place) if keys.get(k).forall(s => Place.ofPackage(s.pkg).contains(place)) => k
    }
    assertEquals(stale, Nil, "remove these rows from architecture/places.tsv")

  test("no violation but the listed exceptions, and every listed exception still exists"):
    val file = Resources.resolve("exceptions.tsv")
    val actual = violations.map(_.row).distinct.sorted
    assert(
      Files.isRegularFile(file),
      s"$file is missing: restore it from git (git checkout -- $file); it is never regenerated, since a regenerated " +
        "file would accept any violation"
    )
    val listed = Files.readAllLines(file, UTF_8).asScala.filterNot(_.startsWith("#")).toList
    if sys.env.get("HISTMERSION_UPDATE_ARCHITECTURE").contains("1") then
      val risen = Ratchet.risenRules(listed, actual)
      assert(
        risen.isEmpty,
        s"not rewritten: these rules' totals rose (fix the new violations first): ${risen.mkString(", ")}"
      )
      val rewritten = Ratchet.exceptionsFile(actual)
      RatchetBase.Spec.Exceptions.check(rewritten, RatchetBase.Git(Path.of(".")), sys.env) match
        case Left(why) => fail(s"not rewritten: $why")
        case Right(_)  => ()
      val _ = Files.write(file, rewritten.asJava, UTF_8)
    else
      val added = actual.filterNot(listed.toSet)
      val gone = listed.filterNot(actual.toSet)
      val where = violations.groupBy(_.row).view.mapValues(_.head.at).toMap
      assert(
        added.isEmpty && gone.isEmpty,
        (added.map(r => s"new violation at ${where(r)}: $r") ++
          gone.map(r => s"no longer violated — remove it from architecture/exceptions.tsv: $r")).mkString("\n")
      )

  test("exceptions.tsv is no higher than at the merge base with the pilot, but for the owner's accepted rises"):
    RatchetBase.Spec.Exceptions.checkFile() match
      case Left(why)   => fail(why)
      case Right(used) => RatchetBase.announce(used).foreach(println)

  test("a rewrite of a violation list may move rows but never raise a rule's total"):
    val listed = List("direction\ta:x.scala\tutil -> library: Source\t1", "filesystem\ta:y.scala\tFiles.*\t3")
    val moved = List("direction\ta:z.scala\tutil -> library: Source\t1", "filesystem\ta:y.scala\tFiles.*\t2")
    val grown = List("direction\ta:x.scala\tutil -> library: Source\t1", "filesystem\ta:y.scala\tFiles.*\t4")
    assertEquals(Ratchet.risenRules(listed, moved), Nil)
    assertEquals(Ratchet.risenRules(listed, grown), List("filesystem 3 -> 4"))
    assertEquals(Ratchet.exceptionsFile(listed.reverse), Ratchet.Header :: Ratchet.DirectionNote :: listed)

  test("a file's key names its platform's source root, and the shared root has no marker"):
    assertEquals(SourceFile.key("api", "scala", "api/problem/Answer.scala"), "api:api/problem/Answer.scala")
    assertEquals(
      SourceFile.key("api", "scalajs", "api/problem/ProblemJson.scala"),
      "api:scalajs:api/problem/ProblemJson.scala"
    )
    assertEquals(
      SourceFile.key("api", "scalajvm", "test/api/AboutSuite.scala"),
      "api:scalajvm:test/api/AboutSuite.scala"
    )
    def file(root: String) =
      val text = "object Io:\n  val p = Files.exists(q)\n"
      SourceFile(SourceFile.key("core", root, "x/Io.scala"), "com.histmersion.x", text, text)
    val (jvm, js) = (file("scalajvm"), file("scalajs"))
    assertEquals(List(jvm, js).map(_.platformless).distinct, List("core:x/Io.scala"))
    assertEquals(
      Rules.all(List(jvm -> "library.sources", js -> "library.sources")).map(_.row),
      List("filesystem\tcore:scalajvm:x/Io.scala\tFiles.*\t1", "filesystem\tcore:scalajs:x/Io.scala\tFiles.*\t1")
    )

  test("the file-system rule's Path.of is java.nio's: imported, renamed or written out, never another type's Path"):
    def pathOf(text: String): List[String] =
      val s = SourceFile("server:x/A.scala", "com.histmersion.x", Blank(text), text)
      Rules.all(List(s -> "library.sources")).map(_.row).filter(_.contains("\tPath.of\t"))
    def row(n: Int) = List(s"filesystem\tserver:x/A.scala\tPath.of\t$n")
    assertEquals(pathOf("import java.nio.file.Path\nval p = Path.of(\"a\")\n"), row(1))
    assertEquals(pathOf("import java.nio.file.{Files, Path}\nval p = Path.of(a)\nval q = Path\n  .of(b)\n"), row(2))
    assertEquals(pathOf("import java.nio.file.*\nval p = Path.of(a)\n"), row(1))
    assertEquals(pathOf("import java.nio.file.{Path as P}\nval p = P.of(a)\n"), row(1))
    assertEquals(pathOf("val p = java.nio.file.Path.of(a)\n"), row(1))
    assertEquals(pathOf("import java.nio\nval p = nio.file.Path.of(a)\n"), row(1))
    // another type's Path: a member, or a Path of a file importing no java.nio one
    assertEquals(pathOf("val p = Problem.Io.Path.of(store.where(key))\n"), Nil)
    assertEquals(pathOf("import java.nio.file.Path\nval p = Problem.Io.Path.of(a)\n"), Nil)
    assertEquals(pathOf("import Problem.Io.Path\nval p = Path.of(a)\n"), Nil)
    assertEquals(pathOf("// java.nio.file.Path.of(a)\nval s = \"Path.of\"\n"), Nil)

  test("the code a check reads is the same however the formatter breaks a string over lines"):
    def words(text: String): List[String] = Blank(text).split("\\s+").toList.filter(_.nonEmpty)
    val oneLine = "val a = s\"$owner${if (x).value then \" (here)\" else \"\"} until ${y.value}\".size"
    val broken = "val a = s\"$owner${\n    if (x).value then \" (here)\" else \"\"\n  } until ${y.value}\".size"
    assertEquals(words(broken), words(oneLine))
    // a splice is code: its `.value` is read like any other
    assertEquals(words(oneLine), List("val", "a", "=", "s", "if", "(x).value", "then", "else", "y.value", ".size"))
    assertEquals(words("f(\"a\\\"b\", /* c */ d) // e"), List("f(", ",", "d)"))
    assertEquals(
      words("val t = \"\"\"x \"y\" ${z}\"\"\" + 1"),
      List("val", "t", "=", "+", "1"),
      "no prefix: not interpolated"
    )

object ArchitectureSuite:

  val Modules: List[String] = List("api", "config", "files", "core", "db", "server")
  val Resources: Path = Path.of("modules/server/src/test/resources/architecture")
  val Unplaced = "?"

  /** One Scala source: `key` is its identity in every rule and rule file ([[SourceFile.key]]), `code` its text with
    * comments and string literals blanked (line breaks kept, so offsets still give line numbers), `raw` the text as
    * written.
    */
  final case class SourceFile(key: String, pkg: String, code: String, raw: String):
    def lineOf(index: Int): Int = code.substring(0, index).count(_ == '\n') + 1

    /** The module and the path under `com/histmersion`, without the source root: one platform's copy of a file and the
      * other's (`scalajvm`, `scalajs`) are one definition compiled for two targets, never on one class path together. A
      * module and a path hold no `:`, so a key of three parts is one with a platform's root.
      */
    def platformless: String =
      key.split(':').toList match
        case module :: _ :: path :: Nil => s"$module:$path"
        case _                          => key

  object SourceFile:
    /** The source roots of one platform's copy of a file. The shared root `scala` has no marker in a key. */
    val PlatformRoots: Set[String] = Set("scalajvm", "scalajs")

    /** A file's key: `<module>:<path under com/histmersion>` in the shared root `scala`
      * (`api:api/problem/Answer.scala`), `<module>:<root>:<path>` in a platform's root
      * (`api:scalajs:api/problem/ProblemJson.scala`), so a JVM and a Scala.js file at the same path are two files in
      * every rule, allow-list and count; a test file's path starts with `test/`
      * (`api:scalajvm:test/api/AboutSuite.scala`).
      */
    def key(module: String, root: String, path: String): String =
      if PlatformRoots(root) then s"$module:$root:$path" else s"$module:$path"

    /** The sources of `module`'s `scope` (`main`, or `test`) under `modules` (the repository's, or a test's tree). */
    def all(module: String, scope: String = "main", modules: Path = Path.of("modules")): List[SourceFile] =
      val main = modules.resolve(module).resolve("src").resolve(scope)
      val prefix = if scope == "main" then "" else s"$scope/"
      if !Files.isDirectory(main) then Nil
      else
        Files
          .list(main)
          .iterator
          .asScala
          .toList
          .filter(_.getFileName.toString.startsWith("scala"))
          .flatMap { root =>
            val base = root.resolve("com/histmersion")
            if !Files.isDirectory(base) then Nil
            else
              Files.walk(base).iterator.asScala.filter(_.toString.endsWith(".scala")).toList.map { p =>
                val text = Files.readString(p, UTF_8)
                val pkg = "(?m)^package ([\\w.]+)".r.findAllMatchIn(text).map(_.group(1)).mkString(".")
                SourceFile(
                  key(module, root.getFileName.toString, s"$prefix${base.relativize(p).toString.replace('\\', '/')}"),
                  pkg,
                  Blank(text),
                  text
                )
              }
          }
          .sortBy(_.key)

  /** The text with every comment and string literal replaced by spaces. A splice (`s"…${expr}…"`) is code and is kept:
    * `.value` in one is an unwrap like any other (user, 2026-11-08). Line breaks stay, so a check reads the same code
    * whatever the layout.
    */
  object Blank:
    def apply(text: String): String =
      val out = new StringBuilder(text.length)
      val n = text.length
      def keep(c: Char): Char = if c == '\n' then '\n' else ' '
      def blankTo(stop: Int, from: Int): Int =
        var i = from
        while i < stop do { out += keep(text(i)); i += 1 }
        i

      /** Code from `from`, kept; a splice ends before the `}` that closes it. Returns where it stopped.
        */
      def code(from: Int, splice: Boolean): Int =
        var i = from
        var depth = 0
        var done = false
        while i < n && !done do
          val c = text(i)
          if text.startsWith("//", i) then while i < n && text(i) != '\n' do { out += ' '; i += 1 }
          else if text.startsWith("/*", i) then
            var nested = 0
            while i < n && (nested > 0 || text.startsWith("/*", i)) do
              if text.startsWith("/*", i) then { nested += 1; out ++= "  "; i += 2 }
              else if text.startsWith("*/", i) then { nested -= 1; out ++= "  "; i += 2 }
              else { out += keep(text(i)); i += 1 }
          else if c == '"' then i = string(i)
          else if c == '\'' && i + 2 < n && (text(i + 2) == '\'' || text(i + 1) == '\\') then
            val end = text.indexOf('\'', i + 2)
            i = blankTo(if end < 0 || end - i > 8 then i + 1 else end + 1, i)
          else if splice && c == '}' && depth == 0 then done = true
          else
            if splice && c == '{' then depth += 1
            else if splice && c == '}' then depth -= 1
            out += c
            i += 1
        i

      /** A string literal at `from` (its opening quote); returns the offset after its closing quote. */
      def string(from: Int): Int =
        val interpolated = from > 0 && (text(from - 1).isLetterOrDigit || text(from - 1) == '_')
        val triple = text.startsWith("\"\"\"", from)
        var i = blankTo(from + (if triple then 3 else 1), from)
        var done = false
        while i < n && !done do
          val c = text(i)
          if triple && text.startsWith("\"\"\"", i) then
            i = blankTo(i + 3, i)
            while i < n && text(i) == '"' do { out += ' '; i += 1 }
            done = true
          else if !triple && (c == '"' || c == '\n') then
            if c == '"' then { out += ' '; i += 1 }
            done = true
          else if interpolated && text.startsWith("$$", i) then i = blankTo(i + 2, i)
          else if interpolated && text.startsWith("${", i) then
            i = code(blankTo(i + 2, i), splice = true)
            if i < n then i = blankTo(i + 1, i)
          else if !triple && c == '\\' && i + 1 < n && text(i + 1) != '\n' then i = blankTo(i + 2, i)
          else { out += keep(c); i += 1 }
        i

      val _ = code(0, splice = false)
      out.toString

  object Place:
    val Domains: List[String] = List("automation", "catalogue", "media", "library", "research")
    val Personal = "personal"

    /** The place a feature package gives its files; `None` for a package that is not by feature yet. */
    def ofPackage(pkg: String): Option[String] =
      pkg.split('.').toList match
        case "com" :: "histmersion" :: "domain" :: d :: rest if Domains.contains(d) || d == Personal =>
          Some((d :: rest).mkString("."))
        case "com" :: "histmersion" :: "util" :: area :: rest => Some(("util" :: area :: rest).mkString("."))
        case "com" :: "histmersion" :: "api" :: d :: rest if Domains.contains(d) || d == Personal =>
          Some((d :: rest).mkString("."))
        case "com" :: "histmersion" :: "api" :: area :: _ if ApiAreas.contains(area)   => ApiAreas.get(area)
        case "com" :: "histmersion" :: "server" :: (sub @ ("views" | "instance")) :: _ => Some(s"server.$sub")
        case "com" :: "histmersion" :: "domain" :: Nil                                 => Some("kernel")
        case _                                                                         => None

    /** The api's packages of the supporting areas and the server's own pages: the place each stands for. */
    val ApiAreas: Map[String, String] = Map(
      "aliases" -> "util.identity",
      "backup" -> "util.data.backup",
      "basemap" -> "util.basemap",
      "checks" -> "util.data.checks",
      "exchange" -> "util.data.exchange",
      "jobs" -> "util.jobs",
      "json" -> "util.json",
      "maintenance" -> "util.data.maintenance",
      "ocr" -> "util.ocr",
      "plugins" -> "util.plugins",
      "settings" -> "util.settings",
      "text" -> "util.text",
      "problem" -> "util.problem",
      "instance" -> "server.instance",
      "views" -> "server.views"
    )

    /** The layer the direction rule compares: a domain, `util.<area>`, or a shared or composition place. */
    def layer(place: String): String = place.split('.').toList match
      case "util" :: area :: _ => s"util.$area"
      case d :: _              => d
      case Nil                 => place

    val Composition: Set[String] = Set("server", "server.instance", "split")
    val Shared: Set[String] = Set("kernel", "values", "api", "api.ui", "config")

    /** The interface packages a lower domain implements for a higher one (personal's marks, library's referrers, the
      * catalogue's map layers and passage sources): any domain may name them.
      */
    val Interfaces: List[String] = List(
      "personal.marks",
      "library.sources.referrers",
      "catalogue.maptiles.layers",
      "catalogue.evidence.passages"
    )

    def mayDependOn(from: String, to: String): Boolean =
      val (f, t) = (layer(from), layer(to))
      if f == t || Composition(f) || Shared(f) || Shared(t) then true
      else if Composition(t) then false
      else if Interfaces.exists(i => to == i || to.startsWith(i + ".")) then true
      else if f.startsWith("util.") then t.startsWith("util.")
      else if t.startsWith("util.") then true
      else if f == Personal || t == Personal then false
      else Domains.indexOf(t) < Domains.indexOf(f)

  final case class Violation(rule: String, file: String, detail: String, count: Int, at: String):
    def row: String = s"$rule\t$file\t$detail\t$count"

  /** The listed violations of ArchitectureSuite and ContractsSuite as ratchets: a rewrite of the list may move a row (a
    * file that moves renames its rows) but never raise a rule's total.
    */
  object Ratchet:
    /** Each rule's total over rows `rule, file, detail, count`. */
    def totals(rows: List[String]): Map[String, Int] =
      rows
        .map(_.split('\t'))
        .collect { case cols if cols.length >= 4 => cols(0) -> cols(3).toInt }
        .groupMapReduce(_._1)(_._2)(_ + _)

    /** The rules whose total in `actual` is above the one in `listed`. */
    def risenRules(listed: List[String], actual: List[String]): List[String] =
      val before = totals(listed)
      totals(actual).toList.collect {
        case (rule, n) if n > before.getOrElse(rule, 0) => s"$rule ${before.getOrElse(rule, 0)} -> $n"
      }.sorted

    val Header = "# rule\tfile\tdetail\tcount — the known violations; a slice removes rows and adds none"
    val DirectionNote =
      "# direction: G11/G12 work (package by feature, step 3) — the ratchet's baseline, not permanent exceptions; each move removes rows"

    /** The exceptions file: the header, then each rule's rows, the direction rule's under its G11/G12 note. */
    def exceptionsFile(rows: List[String]): List[String] =
      val (direction, rest) = rows.sorted.partition(_.startsWith("direction\t"))
      Header :: (if direction.isEmpty then Nil else DirectionNote :: direction) ++ rest

  object Rules:
    /** Names too general to say where they belong, defined in several places or meaning different things. */
    val Shared: Set[String] = Set("Period", "Filter", "Paging", "Label", "Wikidata", "ExternalId", "Source", "Target")

    private val TopLevel =
      "(?m)^(?:(?:final|sealed|abstract|private|protected|open|case|implicit|transparent|inline|opaque)(?:\\[\\w+\\])?\\s+)*(?:class|trait|object|enum|type)\\s+([A-Z]\\w*)".r
    private val Word = "\\b[A-Z]\\w*\\b".r

    def all(placed: List[(SourceFile, String)]): List[Violation] =
      direction(placed) ++ fileSystem(placed) ++ repositories(placed)

    /** Every top-level type name defined in exactly one place, with that place and its defining files. */
    private def owners(placed: List[(SourceFile, String)]): Map[String, (String, Set[String])] =
      placed
        .flatMap((s, p) => TopLevel.findAllMatchIn(s.code).map(m => (m.group(1), p, s.key)))
        .groupBy(_._1)
        .collect {
          case (name, defs) if !Shared(name) && defs.map(_._2).distinct.size == 1 =>
            name -> (defs.head._2, defs.map(_._3).toSet)
        }

    /** A member of a type or object (`BundleKind.Place`, `Queue.Kind.Acquire`): the qualifier before the dot is a
      * capitalised name, so the word is that type's own vocabulary, never the top-level type of the same name — which a
      * package path (`api.Place`) or the bare name reaches.
      */
    private val Member = "[A-Z]\\w*\\s*\\.\\s*$".r

    private def isMember(code: String, start: Int): Boolean =
      Member.findFirstIn(code.substring(math.max(0, start - 64), start)).isDefined

    /** An enum's case defined (`case Acquire extends Kind(…)`): the enum's own word, as its uses (`Kind.Acquire`) are,
      * never the top-level type of the same name — which a `case` of a match (`case Acquire =>`) may still name.
      */
    private val CaseDefinition = "\\bcase\\s+$".r
    private val ExtendsAfter = "^\\s+extends\\b".r

    private def isCaseDefinition(code: String, start: Int, end: Int): Boolean =
      CaseDefinition.findFirstIn(code.substring(math.max(0, start - 16), start)).isDefined &&
        ExtendsAfter.findFirstIn(code.substring(end, math.min(code.length, end + 16))).isDefined

    /** The names a file uses, each with the offset of its first use outside its own definitions. */
    private def uses(s: SourceFile): Map[String, Int] =
      Word
        .findAllMatchIn(s.code)
        .filterNot(m => isMember(s.code, m.start) || isCaseDefinition(s.code, m.start, m.end))
        .toList
        .groupBy(_.matched)
        .view
        .mapValues(_.head.start)
        .toMap

    private def direction(placed: List[(SourceFile, String)]): List[Violation] =
      val defined = owners(placed)
      placed.flatMap { (s, from) =>
        uses(s).toList.sortBy(_._1).flatMap { (name, at) =>
          defined.get(name).collect {
            case (to, files) if !files(s.key) && !Place.mayDependOn(from, to) =>
              Violation(
                "direction",
                s.key,
                s"${Place.layer(from)} -> ${Place.layer(to)}: $name",
                1,
                s"${s.key}:${s.lineOf(at)}"
              )
          }
        }
      }

    private def matches(re: scala.util.matching.Regex): String => List[Int] =
      code => re.findAllMatchIn(code).map(_.start).toList

    private val FileSystem: List[(String, String => List[Int])] = List(
      "java.nio.file" -> matches("java\\.nio\\.file".r),
      "java.io.File" -> matches("java\\.io\\.File\\b".r),
      "Files.*" -> matches("\\bFiles\\.\\w+".r),
      "Path.of" -> NioPath.of,
      "Paths.get" -> matches("\\bPaths\\.get\\b".r),
      "FileChannel" -> matches("\\bFileChannel\\b".r),
      "RandomAccessFile" -> matches("\\bRandomAccessFile\\b".r),
      "File streams" -> matches("\\bFile(?:Input|Output)Stream\\b|\\bFile(?:Reader|Writer)\\b".r),
      "MappedByteBuffer" -> matches("\\bMappedByteBuffer\\b".r),
      "StandardCopyOption" -> matches("\\bStandardCopyOption\\b".r),
      ".toFile" -> matches("\\.toFile\\b".r),
      "File(" -> matches("(?<![\\w.])File\\(".r)
    )

    /** `java.nio.file.Path.of`, the file system's own `Path`: written out (`java.nio.file.Path.of`,
      * `nio.file.Path.of`), or by a name a `java.nio.file` import brings into scope (`Path`, the `P` of `Path as P`, or
      * `Path` by a wildcard). Another type's `Path` (`Problem.Io.Path.of`, or a `Path` of a file importing no java.nio
      * one) is a member of that type, which builds no file system path.
      */
    object NioPath:
      private val Qualified = "\\bnio\\s*\\.\\s*file\\s*\\.\\s*Path\\s*\\.\\s*of\\b".r
      private val Single = "\\bimport\\s+(?:_root_\\.)?java\\.nio\\.file\\.Path\\b(?!\\s*\\.)".r
      private val Wildcard = "\\bimport\\s+(?:_root_\\.)?java\\.nio\\.file\\.(?:\\*|_)(?!\\w)".r
      private val Braces = "\\bimport\\s+(?:_root_\\.)?java\\.nio\\.file\\.\\{([^}]*)\\}".r
      private val Selector = "^\\s*(\\w+|\\*|_)(?:\\s*(?:as|=>)\\s*(\\w+))?\\s*$".r

      /** The names `java.nio.file.Path` goes by in a file, from its imports. */
      def names(code: String): Set[String] =
        val single = Single.findFirstIn(code).map(_ => "Path")
        val wildcard = Wildcard.findFirstIn(code).map(_ => "Path")
        val braced = Braces.findAllMatchIn(code).flatMap(_.group(1).split(',')).flatMap {
          case Selector("Path", null)    => Some("Path")
          case Selector("Path", rename)  => Option.when(rename != "_")(rename)
          case Selector("*" | "_", null) => Some("Path")
          case _                         => None
        }
        (single ++ wildcard ++ braced).toSet

      /** The offsets of the file system's `Path.of` in a file. */
      def of(code: String): List[Int] =
        val byName =
          names(code).toList.flatMap(n => s"(?<![\\w.])\\Q$n\\E\\s*\\.\\s*of\\b".r.findAllMatchIn(code).map(_.start))
        (Qualified.findAllMatchIn(code).map(_.start).toList ++ byName).distinct.sorted

    /** The files that keep paths at their edge: they build or receive the locations. */
    private val Tools = Set(
      "server:server/plugins/RelinkPlugins.scala",
      "server:domain/automation/agents/PluginTree.scala",
      "api:api/ApiIndex.scala"
    )

    private def fileSystem(placed: List[(SourceFile, String)]): List[Violation] =
      placed.flatMap { (s, place) =>
        val l = Place.layer(place)
        val fsAllowed = l == "util.storage" || Place.Composition(place) || place == "config" || Tools(s.key)
        val fs =
          if fsAllowed then Nil
          else
            FileSystem.flatMap { (kind, find) =>
              val hits = find(s.code)
              hits.headOption.map(h => Violation("filesystem", s.key, kind, hits.size, s"${s.key}:${s.lineOf(h)}"))
            }
        val processes =
          if l == "util.process" then Nil
          else
            val hits = "\\bProcessBuilder\\b".r.findAllMatchIn(s.code).toList
            hits.headOption.toList.map(h =>
              Violation("process", s.key, "ProcessBuilder", hits.size, s"${s.key}:${s.lineOf(h.start)}")
            )
        fs ++ processes
      }

    private def repositories(placed: List[(SourceFile, String)]): List[Violation] =
      val repos = owners(placed).filter((name, _) => name.endsWith("Repo"))
      placed.flatMap { (s, place) =>
        uses(s).toList.sortBy(_._1).flatMap { (name, at) =>
          repos.get(name).collect {
            case (owner, files) if !files(s.key) && owner != place =>
              Violation("repository", s.key, s"$name of $owner", 1, s"${s.key}:${s.lineOf(at)}")
          }
        }
      }
