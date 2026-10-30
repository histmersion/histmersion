package com.histmersion.server

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
  * A file's place is its row in `architecture/places.tsv`, the design's table of where each file moves, while it is not
  * there yet; then its package (`com.histmersion.domain.<domain>.<feature>`, `com.histmersion.util.<area>`). The violations that exist today are listed in `architecture/exceptions.tsv`; the list is exact both ways, so
  * a new violation fails and so does a row nothing violates any more. Each slice of the refactor removes rows and adds
  * none; `HISTMERSION_UPDATE_ARCHITECTURE=1` rewrites the list from the sources.
  */
class ArchitectureSuite extends munit.FunSuite:
  import ArchitectureSuite.*

  private lazy val sources: List[SourceFile] = Modules.flatMap(SourceFile.all)
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
    if sys.env.get("HISTMERSION_UPDATE_ARCHITECTURE").contains("1") then
      val header = "# rule\tfile\tdetail\tcount — the known violations; a slice removes rows and adds none"
      val _ = Files.write(file, (header :: actual).asJava, UTF_8)
    else
      val listed = Files.readAllLines(file, UTF_8).asScala.filterNot(_.startsWith("#")).toList
      val added = actual.filterNot(listed.toSet)
      val gone = listed.filterNot(actual.toSet)
      val where = violations.groupBy(_.row).view.mapValues(_.head.at).toMap
      assert(
        added.isEmpty && gone.isEmpty,
        (added.map(r => s"new violation at ${where(r)}: $r") ++
          gone.map(r => s"no longer violated — remove it from architecture/exceptions.tsv: $r")).mkString("\n")
      )

object ArchitectureSuite:

  val Modules: List[String] = List("api", "config", "files", "core", "db", "server")
  val Resources: Path = Path.of("modules/server/src/test/resources/architecture")
  val Unplaced = "?"

  /** One Scala source: `key` is `<module>:<path under com/histmersion>`, `code` its text with comments and string
    * literals blanked (line breaks kept, so offsets still give line numbers).
    */
  final case class SourceFile(key: String, pkg: String, code: String):
    def lineOf(index: Int): Int = code.substring(0, index).count(_ == '\n') + 1

  object SourceFile:
    def all(module: String): List[SourceFile] =
      val main = Path.of("modules", module, "src", "main")
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
              SourceFile(s"$module:${base.relativize(p).toString.replace('\\', '/')}", pkg, Blank(text))
            }
        }
        .sortBy(_.key)

  /** The text with every comment and string literal replaced by spaces. */
  object Blank:
    def apply(text: String): String =
      val out = new StringBuilder(text.length)
      val n = text.length
      def keep(c: Char): Char = if c == '\n' then '\n' else ' '
      var i = 0
      while i < n do
        val c = text(i)
        if text.startsWith("//", i) then
          while i < n && text(i) != '\n' do { out += ' '; i += 1 }
        else if text.startsWith("/*", i) then
          var depth = 0
          while i < n && (depth > 0 || text.startsWith("/*", i)) do
            if text.startsWith("/*", i) then { depth += 1; out ++= "  "; i += 2 }
            else if text.startsWith("*/", i) then { depth -= 1; out ++= "  "; i += 2 }
            else { out += keep(text(i)); i += 1 }
        else if text.startsWith("\"\"\"", i) then
          out ++= "   "
          i += 3
          while i < n && !text.startsWith("\"\"\"", i) do { out += keep(text(i)); i += 1 }
          while i < n && text(i) == '"' do { out += ' '; i += 1 }
        else if c == '"' then
          out += ' '
          i += 1
          while i < n && text(i) != '"' && text(i) != '\n' do
            if text(i) == '\\' && i + 1 < n then { out ++= "  "; i += 2 }
            else { out += ' '; i += 1 }
          if i < n && text(i) == '"' then { out += ' '; i += 1 }
        else if c == '\'' && i + 2 < n && (text(i + 2) == '\'' || text(i + 1) == '\\') then
          val end = text.indexOf('\'', i + 2)
          val stop = if end < 0 || end - i > 8 then i + 1 else end + 1
          while i < stop do { out += ' '; i += 1 }
        else { out += c; i += 1 }
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
        case "com" :: "histmersion" :: "domain" :: Nil        => Some("kernel")
        case _                                                => None

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

    /** The names a file uses, each with the offset of its first use outside its own definitions. */
    private def uses(s: SourceFile): Map[String, Int] =
      Word.findAllMatchIn(s.code).toList.groupBy(_.matched).view.mapValues(_.head.start).toMap

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

    private val FileSystem: List[(String, scala.util.matching.Regex)] = List(
      "java.nio.file" -> "java\\.nio\\.file".r,
      "java.io.File" -> "java\\.io\\.File\\b".r,
      "Files.*" -> "\\bFiles\\.\\w+".r,
      "Path.of" -> "\\bPath\\.of\\b".r,
      "Paths.get" -> "\\bPaths\\.get\\b".r,
      "FileChannel" -> "\\bFileChannel\\b".r,
      "RandomAccessFile" -> "\\bRandomAccessFile\\b".r,
      "File streams" -> "\\bFile(?:Input|Output)Stream\\b|\\bFile(?:Reader|Writer)\\b".r,
      "MappedByteBuffer" -> "\\bMappedByteBuffer\\b".r,
      "StandardCopyOption" -> "\\bStandardCopyOption\\b".r,
      ".toFile" -> "\\.toFile\\b".r,
      "File(" -> "(?<![\\w.])File\\(".r
    )

    /** The files that keep paths at their edge: they build or receive the locations. */
    private val Tools = Set(
      "server:server/plugins/PluginTool.scala",
      "server:server/agents/GeneratePlugin.scala",
      "api:api/ApiIndex.scala"
    )

    private def fileSystem(placed: List[(SourceFile, String)]): List[Violation] =
      placed.flatMap { (s, place) =>
        val l = Place.layer(place)
        val fsAllowed = l == "util.storage" || Place.Composition(place) || place == "config" || Tools(s.key)
        val fs =
          if fsAllowed then Nil
          else
            FileSystem.flatMap { (kind, re) =>
              val hits = re.findAllMatchIn(s.code).toList
              hits.headOption.map(h => Violation("filesystem", s.key, kind, hits.size, s"${s.key}:${s.lineOf(h.start)}"))
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
