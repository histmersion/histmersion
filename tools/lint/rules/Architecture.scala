package com.histmersion.linting

import scalafix.v1.*

final class ArchitectureLint extends SyntacticRule("ArchitectureLint"):
  override def fix(using doc: SyntacticDocument): Patch =
    Tolerance.patch(Tolerance.diagnostics(Architecture.findings(doc), doc.tree, Architecture.Owned))

final class TextBaseline extends SyntacticRule("TextBaseline"):
  override def fix(using doc: SyntacticDocument): Patch =
    Baseline.tolerations(
      Contracts.findings(doc) ++ Architecture.findings(doc),
      doc.tree,
      Project.relativeOf(doc.input)
    )

final class TextPrune extends SyntacticRule("TextPrune"):
  override def fix(using doc: SyntacticDocument): Patch =
    Baseline.prune(
      Contracts.findings(doc) ++ Architecture.findings(doc),
      doc.tree,
      Contracts.Owned ++ Architecture.Owned
    )

object Architecture:

  val Owned: Set[String] = Set("Placement", "Direction", "FileSystem", "Process", "Repository", "TotalOrder")

  def findings(doc: SyntacticDocument): List[Finding] = Lints.textFindings(doc.input, violations, Names)

  val Modules: List[String] = List("api", "config", "files", "core", "db", "server")

  val SqlModules: List[String] = List("db", "server")

  val Names: Map[String, String] = Map(
    "placement" -> "Placement",
    "direction" -> "Direction",
    "filesystem" -> "FileSystem",
    "process" -> "Process",
    "repository" -> "Repository",
    "total-order" -> "TotalOrder"
  )

  lazy val violations: Map[String, List[Violation]] =
    val sources = Modules.flatMap(SourceFile.all(_))
    val placed = sources.map(s => s -> placeOf(s))
    val orders = TotalOrder
      .partial(SqlModules.flatMap(SourceFile.all(_)), TotalOrder.migrationKeys)
      .map(c => Violation("total-order", c.file, s"ORDER BY with ties: end it with a unique key — ${c.text}", 1, s"${c.file}:${c.line}", List(c.offset)))
    (Rules.all(placed) ++ placement(sources) ++ orders).groupBy(_.file)

  def placeOf(s: SourceFile): String =
    Project.movesToOf(s.raw).orElse(Place.ofPackage(s.pkg)).getOrElse(Unplaced)

  val Unplaced = "?"

  private val FirstDefinition =
    "(?m)^(?:@[^\\n]*\\n)*(?:(?:final|sealed|abstract|private|protected|open|case|implicit|transparent|inline|opaque)(?:\\[\\w+\\])?\\s+)*(?:class|trait|object|enum|type|def|val|given)\\b".r

  def placement(sources: List[SourceFile]): List[Violation] =
    sources.flatMap { s =>
      val target = Project.movesToOf(s.raw)
      val here = Place.ofPackage(s.pkg)
      val at = FirstDefinition.findFirstMatchIn(s.code).map(_.start).getOrElse(0)
      (here, target) match
        case (None, None) =>
          List(Violation("placement", s.key, s"package ${s.pkg} is no feature package: move the file, or name where it goes with @movesTo", 1, s.key, List(at)))
        case (None, Some(t)) =>
          List(Violation("placement", s.key, s"moves to $t", 1, s.key, List(at)))
        case (Some(h), Some(t)) if h != t =>
          List(Violation("placement", s.key, s"in $h, moves to $t", 1, s.key, List(at)))
        case _ => Nil
    }

  object Place:
    val Domains: List[String] = List("automation", "catalogue", "media", "library", "research")

    val Personal = "personal"

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

    val ApiAreas: Map[String, String] = Map(
      "aliases" -> "util.identity",
      "backup" -> "util.data.backup",
      "basemap" -> "util.basemap",
      "checks" -> "util.data.checks",
      "exchange" -> "util.data.exchange",
      "jobs" -> "util.jobs",
      "json" -> "util.json",
      "log" -> "util.log",
      "maintenance" -> "util.data.maintenance",
      "ocr" -> "util.ocr",
      "plugins" -> "util.plugins",
      "settings" -> "util.settings",
      "text" -> "util.text",
      "time" -> "util.time",
      "problem" -> "util.problem",
      "instance" -> "server.instance",
      "views" -> "server.views"
    )

    def layer(place: String): String = place.split('.').toList match
      case "util" :: area :: _ => s"util.$area"
      case d :: _              => d
      case Nil                 => place

    val Composition: Set[String] = Set("server", "server.instance", "split")
    val Shared: Set[String] = Set("kernel", "values", "api", "api.ui", "config")

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

  object Rules:
    val Shared: Set[String] = Set("Period", "Filter", "Paging", "Label", "Wikidata", "ExternalId", "Source", "Target")

    private val TopLevel =
      "(?m)^(?:(?:final|sealed|abstract|private|protected|open|case|implicit|transparent|inline|opaque)(?:\\[\\w+\\])?\\s+)*(?:class|trait|object|enum|type)\\s+([A-Z]\\w*)".r
    private val Word = "\\b[A-Z]\\w*\\b".r

    def all(placed: List[(SourceFile, String)]): List[Violation] =
      direction(placed) ++ fileSystem(placed) ++ repositories(placed)

    private def owners(placed: List[(SourceFile, String)]): Map[String, (String, Set[String])] =
      placed
        .flatMap((s, p) => TopLevel.findAllMatchIn(s.code).map(m => (m.group(1), p, s.key)))
        .groupBy(_._1)
        .collect {
          case (name, defs) if !Shared(name) && defs.map(_._2).distinct.size == 1 =>
            name -> (defs.head._2, defs.map(_._3).toSet)
        }

    private val Member = "[A-Z]\\w*\\s*\\.\\s*$".r

    private def isMember(code: String, start: Int): Boolean =
      Member.findFirstIn(code.substring(math.max(0, start - 64), start)).isDefined

    private val CaseDefinition = "\\bcase\\s+$".r
    private val ExtendsAfter = "^\\s+extends\\b".r

    private def isCaseDefinition(code: String, start: Int, end: Int): Boolean =
      CaseDefinition.findFirstIn(code.substring(math.max(0, start - 16), start)).isDefined &&
        ExtendsAfter.findFirstIn(code.substring(end, math.min(code.length, end + 16))).isDefined

    private def uses(s: SourceFile): Map[String, Int] =
      Word
        .findAllMatchIn(s.code)
        .filterNot(m => isMember(s.code, m.start) || isCaseDefinition(s.code, m.start, m.end))
        .toList
        .groupBy(_.matched)
        .view
        .mapValues(_.head.start)
        .toMap

    private val OwnDefinition =
      "(?m)^[ \\t]*(?:(?:final|sealed|abstract|private|protected|open|case|implicit|transparent|inline|opaque)(?:\\[\\w+\\])?\\s+)*(?:class|trait|object|enum|type)\\s+([A-Z]\\w*)".r

    def ownNames(code: String): Set[String] = OwnDefinition.findAllMatchIn(code).map(_.group(1)).toSet

    private def direction(placed: List[(SourceFile, String)]): List[Violation] =
      val defined = owners(placed)
      placed.flatMap { (s, from) =>
        val own = ownNames(s.code)
        uses(s).toList.sortBy(_._1).flatMap { (name, at) =>
          defined.get(name).collect {
            case (to, files) if !files(s.key) && !own(name) && !Place.mayDependOn(from, to) =>
              Violation(
                "direction",
                s.key,
                s"${Place.layer(from)} -> ${Place.layer(to)}: $name",
                1,
                s"${s.key}:${s.lineOf(at)}",
                List(at)
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

    object NioPath:
      private val Qualified = "\\bnio\\s*\\.\\s*file\\s*\\.\\s*Path\\s*\\.\\s*of\\b".r
      private val Single = "\\bimport\\s+(?:_root_\\.)?java\\.nio\\.file\\.Path\\b(?!\\s*\\.)".r
      private val Wildcard = "\\bimport\\s+(?:_root_\\.)?java\\.nio\\.file\\.(?:\\*|_)(?!\\w)".r
      private val Braces = "\\bimport\\s+(?:_root_\\.)?java\\.nio\\.file\\.\\{([^}]*)\\}".r
      private val Selector = "^\\s*(\\w+|\\*|_)(?:\\s*(?:as|=>)\\s*(\\w+))?\\s*$".r

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

      def of(code: String): List[Int] =
        val byName =
          names(code).toList.flatMap(n => s"(?<![\\w.])\\Q$n\\E\\s*\\.\\s*of\\b".r.findAllMatchIn(code).map(_.start))
        (Qualified.findAllMatchIn(code).map(_.start).toList ++ byName).distinct.sorted

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
              hits.headOption.map(h => Violation("filesystem", s.key, kind, hits.size, s"${s.key}:${s.lineOf(h)}", hits))
            }
        val processes =
          if l == "util.process" then Nil
          else
            val hits = "\\bProcessBuilder\\b".r.findAllMatchIn(s.code).map(_.start).toList
            hits.headOption.toList.map(h =>
              Violation("process", s.key, "ProcessBuilder", hits.size, s"${s.key}:${s.lineOf(h)}", hits)
            )
        fs ++ processes
      }

    private def repositories(placed: List[(SourceFile, String)]): List[Violation] =
      val repos = owners(placed).filter((name, _) => name.endsWith("Repo"))
      placed.flatMap { (s, place) =>
        uses(s).toList.sortBy(_._1).flatMap { (name, at) =>
          repos.get(name).collect {
            case (owner, files) if !files(s.key) && owner != place =>
              Violation("repository", s.key, s"$name of $owner", 1, s"${s.key}:${s.lineOf(at)}", List(at))
          }
        }
      }
