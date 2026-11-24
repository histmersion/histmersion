import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, Configured, generic}
import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets.UTF_8
import scala.jdk.CollectionConverters._
import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue}
import scala.meta.internal.{semanticdb => s}

object ProblemSymbols {
  case class Config(
    report: String = "target/guidelines/problem-symbols.tsv",
    roots: List[String] = List("modules"),
    mainMarker: String = "/src/main/",
    exclude: List[String] = List("modules/problem/src/main/scala/com/histmersion/util/problem/Text.scala"),
    ownFile: String = "modules/problem/src/main/scala/com/histmersion/util/problem/Problem.scala",
    ownSkipped: List[String] = List("examples", "within", "inside"),
    edges: List[String] = List("modules/problem/src/main/scala/com/histmersion/util/problem/Problem.scala",
      "modules/problem/src/main/scala/com/histmersion/util/problem/Text.scala",
      "modules/api/src/main/scala/com/histmersion/api/problem/Answer.scala",
      "modules/api/src/main/scala/com/histmersion/api/problem/ContentCodec.scala",
      "modules/api/src/main/scalajvm/com/histmersion/api/problem/ProblemJson.scala",
      "modules/api/src/main/scalajs/com/histmersion/api/problem/ProblemJson.scala",
      "modules/cli/src/main/scala/com/histmersion/cli/ExitCode.scala"),
    problemRoot: String = "com/histmersion/util/problem/Problem.",
    problemSemanticdb: List[String] = List("target/out/jvm/scala-{scalaVersion}/histmersion-problem/meta"),
    expected: String = ""
  )
  object Config {
    implicit val surface: generic.Surface[Config] = generic.deriveSurface[Config]
    implicit val decoder: ConfDecoder[Config] = generic.deriveDecoder(Config()).noTypos
  }

  case class Row(kind: String, name: String, module: String, path: String, line: Int) {
    def tsv: String = List(kind, name, module, path, line.toString).mkString("\t")
  }
  val Header: String = List("kind", "name", "module", "path", "line").mkString("\t")

  private val indexes = new ConcurrentHashMap[List[String], Map[String, s.SymbolInformation]]()
  def index(roots: List[String]): Map[String, s.SymbolInformation] = indexes.computeIfAbsent(roots, r => buildIndex(r))
  private def buildIndex(roots: List[String]): Map[String, s.SymbolInformation] = {
    val files = roots.filter(r => Files.isDirectory(Paths.get(r))).flatMap { r =>
      val stream = Files.walk(Paths.get(r))
      try stream.iterator.asScala.filter(p => p.toString.endsWith(".semanticdb") && Files.isRegularFile(p)).toList finally stream.close()
    }
    files.flatMap(f => s.TextDocuments.parseFrom(Files.readAllBytes(f)).documents.flatMap(_.symbols)).map(i => i.symbol -> i).toMap
  }

  private[ProblemSymbols] val rows = new ConcurrentLinkedQueue[Row]()
}

class ProblemSymbols(config: ProblemSymbols.Config, index: Map[String, s.SymbolInformation]) extends SemanticRule("ProblemSymbols") {
  import ProblemSymbols._
  def this() = this(ProblemSymbols.Config(), Map.empty)

  override def withConfiguration(c: Configuration): Configured[Rule] =
    c.conf.getOrElse("ProblemSymbols")(Config()).map(cfg => new ProblemSymbols(cfg, ProblemSymbols.index(cfg.problemSemanticdb.map(_.replace("{scalaVersion}", c.scalaVersion)))))

  override def beforeStart(): Unit = rows.clear()

  private val Root = config.problemRoot
  private val ProblemTrait = Root.stripSuffix(".") + "#"
  private val EnumParent = "scala/reflect/Enum#"
  private val EnumFlag = s.SymbolInformation.Property.ENUM.value

  private def parents(sym: String): List[String] = index.get(sym).map(_.signature).toList.flatMap {
    case c: s.ClassSignature => c.parents.toList.collect { case r: s.TypeRef => r.symbol }
    case _ => Nil
  }
  private def isGroup(sym: String): Boolean =
    index.get(sym).exists(_.kind == s.SymbolInformation.Kind.TRAIT) && parents(sym).contains(ProblemTrait)
  private def isCase(sym: String): Boolean =
    !isGroup(sym) && parents(sym).exists(p => p == ProblemTrait || isGroup(p))
  private def isEnum(sym: String): Boolean = parents(sym).contains(EnumParent)
  private def ownerClass(sym: String): String = {
    val body = sym.stripSuffix(".").stripSuffix("#")
    body.substring(0, body.lastIndexWhere(c => c == '.' || c == '#') + 1).stripSuffix(".") + "#"
  }
  private def isValue(sym: String): Boolean = {
    val owner = ownerClass(sym)
    isEnum(owner) && index.get(sym).exists { i =>
      (i.kind == s.SymbolInformation.Kind.CLASS && parents(sym).contains(owner)) ||
        (i.kind == s.SymbolInformation.Kind.METHOD && (i.properties & EnumFlag) != 0)
    }
  }

  private def name(sym: String): String = sym.stripPrefix(Root).stripSuffix(".").stripSuffix("#").replace('#', '.')

  private lazy val defined: Map[String, String] = index.keys.filter(_.startsWith(Root)).toList.flatMap { sym =>
    if (sym.endsWith("#") && isCase(sym)) List(name(sym) -> "case")
    else if (sym.endsWith(".") && isCase(sym)) List(name(sym) -> "case")
    else if (isValue(sym)) List(name(sym) -> "value")
    else Nil
  }.toMap

  override def afterComplete(): Unit = {
    val definitions = defined.toList.map { case (n, k) => Row(k, n, "problem", "-", 0) }
    val all = (definitions ++ rows.asScala.toList).distinct.sortBy(r => (r.kind, r.name, r.path, r.line))
    val lines = Header :: all.map(_.tsv)
    if (config.report.nonEmpty) {
      val p = Paths.get(config.report)
      Option(p.getParent).foreach(Files.createDirectories(_))
      Files.write(p, lines.asJava, UTF_8)
    }
    if (config.expected.nonEmpty) {
      val want = Files.readAllLines(Paths.get(config.expected), UTF_8).asScala.toList.filter(_.nonEmpty)
      val missing = want.filterNot(lines.contains)
      val extra = lines.filterNot(want.contains)
      require(missing.isEmpty && extra.isEmpty,
        s"rows differ from ${config.expected}\nmissing:\n${missing.mkString("\n")}\nunexpected:\n${extra.mkString("\n")}")
    }
  }

  private def written(sym: String): Option[String] =
    if (!sym.startsWith(Root)) None
    else {
      val parts = sym.stripPrefix(Root).split("[.#]").toList.filter(_.nonEmpty)
      val named = parts.takeWhile(p => p.headOption.exists(_.isUpper) && !p.contains("("))
      val rest = sym.stripPrefix(Root).drop(named.mkString(".").length)
      Some(named.mkString(".")).filter(n => defined.contains(n) && !(rest.startsWith("#") && rest.length > 1))
    }

  private val Lookups = List(".values().", ".valueOf().", ".fromOrdinal().")
  private def looked(sym: String): List[String] =
    Lookups.find(l => sym.startsWith(Root) && sym.endsWith(l)).toList.flatMap { l =>
      val owner = name(sym.stripSuffix(l) + ".")
      defined.collect { case (v, "value") if v.startsWith(owner + ".") && !v.drop(owner.length + 1).contains('.') => v }
    }

  private def ancestors(t: Tree): List[Tree] = t.parent.toList.flatMap(p => p :: ancestors(p))

  private def inPattern(t: Tree): Boolean = {
    def within(p: Tree): Boolean = p.pos.start <= t.pos.start && t.pos.end <= p.pos.end
    ancestors(t).exists {
      case c: Case => within(c.pat)
      case v: Defn.Val => v.pats.exists(within)
      case v: Defn.Var => v.pats.exists(within)
      case g: Enumerator.Generator => within(g.pat)
      case g: Enumerator.CaseGenerator => within(g.pat)
      case g: Enumerator.Val => within(g.pat)
      case _ => false
    }
  }
  private def inImportOrType(t: Tree): Boolean = ancestors(t).exists { case _: Import | _: Type => true; case _ => false }
  private def compared(t: Tree): Boolean = ancestors(t).exists {
    case i: Term.ApplyInfix => Set("==", "!=")(i.op.value)
    case _ => false
  }

  override def fix(implicit doc: SemanticDocument): Patch = {
    val abs = doc.input match { case Input.File(p, _) => p.toString; case Input.VirtualFile(p, _) => p; case o => o.toString }
    val file = config.roots.map(r => abs.indexOf(r + "/")).filter(_ >= 0).sorted.headOption.map(abs.substring).getOrElse(abs)
    if (config.mainMarker.nonEmpty && !file.contains(config.mainMarker)) return Patch.empty
    val module = file.stripPrefix("modules/").split("/src/").head
    val own = file.endsWith(config.ownFile)
    val builds = !config.exclude.exists(file.endsWith)
    val branches = !config.edges.exists(file.endsWith) && !own
    def skipped(n: Term.Name): Boolean = own && ancestors(n).exists {
      case d: Defn.Def => config.ownSkipped.contains(d.name.value)
      case v: Defn.Val => v.pats.exists { case Pat.Var(p) => config.ownSkipped.contains(p.value); case _ => false }
      case _ => false
    }
    def defining(n: Term.Name): Boolean = n.parent.exists {
      case d: Defn.Object => d.name eq n
      case e: Defn.EnumCase => e.name eq n
      case _: Defn.RepeatedEnumCase => true
      case _ => false
    }
    doc.tree.traverse {
      case n: Term.Name if builds && !inImportOrType(n) && looked(n.symbol.value).nonEmpty =>
        if (!skipped(n)) looked(n.symbol.value).foreach(v => rows.add(Row("build", v, module, file, n.pos.startLine + 1)))
      case n: Term.Name if !inImportOrType(n) && !defining(n) => written(n.symbol.value).foreach { w =>
        val line = n.pos.startLine + 1
        if (inPattern(n)) { if (branches) rows.add(Row("branch", w, module, file, line)) }
        else {
          if (builds && !skipped(n)) rows.add(Row("build", w, module, file, line))
          if (branches && compared(n)) rows.add(Row("branch", w, module, file, line))
        }
      }
    }
    Patch.empty
  }
}
