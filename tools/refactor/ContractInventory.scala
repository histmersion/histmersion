import scalafix.v1._
import scala.meta._
import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters._

class ContractInventory extends SyntacticRule("ContractInventory") {
  private final case class Finding(goal: Int, path: String, line: Int, column: Int, reason: String, tree: Tree,
      token: Option[Token.Comment] = None)
  private final case class Definition(layer: String, pkg: String, name: String, path: String, tree: Tree)
  private val findings = new ConcurrentLinkedQueue[Finding]()
  private val definitions = new ConcurrentLinkedQueue[Definition]()
  private val visited = new ConcurrentLinkedQueue[String]()
  private val primitives = Set("String", "Int", "Long", "Double", "Float", "Boolean")
  private val codecNames = Set("Encoder", "Decoder", "Codec", "Schema", "ConfigReader", "KindlingsCodecAsObject", "KindlingsSchema")
  private def names(t: Tree): List[String] = t.collect { case n: Name => n.value }
  private def hidden(mods: List[Mod]): Boolean = mods.exists { case _: Mod.Private | _: Mod.Protected => true; case _ => false }
  private def top(t: Tree): Boolean = t.parent.exists { case _: Pkg | _: Source => true; case p if p.productPrefix == "Pkg.Body" => true; case _ => false }
  private def params(t: Tree): List[Term.Param] = t.children.flatMap {
    case p: Term.Param => List(p)
    case _: Term | _: Type => Nil
    case x => params(x)
  }

  override def fix(implicit doc: SyntacticDocument): Patch = {
    val path = doc.input match {
      case Input.File(p, _) => p.toString
      case Input.VirtualFile(p, _) => p
      case x => x.toString
    }
    visited.add(path)
    val pkg = doc.tree.collect { case p: Pkg => p.ref.syntax }.mkString(".")
    val module = path.split("/modules/").last.split("/src/").head
    val layer = module match {
      case "api" | "web" => "api"
      case "plugin-api" => "plugin"
      case "cli" => "cli"
      case x if x.startsWith("plugins/") => x
      case _ => "domain"
    }
    val main = path.contains("/src/main/")
    val domain = pkg.startsWith("com.histmersion.domain")
    def add(goal: Int, tree: Tree, reason: String): Unit =
      findings.add(Finding(goal, path, tree.pos.startLine + 1, tree.pos.startColumn + 1, reason, tree))
    def method(t: Tree, mods: List[Mod]): Unit = {
      if (main && domain && !hidden(mods)) {
        params(t).foreach(p => p.decltpe.filter(x => names(x).exists(primitives)).foreach(x => add(1, p, "Candidate primitive model parameter; establish concept and boundary semantically")))
        params(t).flatMap(p => p.decltpe.map(_.structure -> p)).groupBy(_._1).values.filter(_.size > 1).foreach { ps =>
          add(4, ps.head._2, "Candidate distinct roles with the same declared type: " + ps.map(_._2.name.value).mkString(", "))
        }
      }
    }
    doc.tree.traverse {
      case d: Defn.Def =>
        method(d, d.mods)
        if (main && d.name.value == "main" && d.parent.exists(_.is[Template])) add(27, d, "Object main candidate for top-level @main; launcher configuration must move too")
      case d: Decl.Def => method(d, d.mods)
      case s: Term.Select if main && s.name.value == "value" && domain => add(2, s, "Candidate unwrap; resolve Iron symbol and boundary before rewriting")
      case s: Term.Select if main && s.name.value == "copy" => add(19, s.parent.getOrElse(s), "Candidate copy, including method values; resolve symbol and layer before lens rewrite")
      case s: Term.Select if main && Set("foldLeft", "foldRight")(s.name.value) => add(20, s.parent.getOrElse(s), "Candidate fold; allocation and evaluation order require typed inspection")
      case s: Term.Select if main && s.name.value == "derived" => add(18, s.parent.getOrElse(s), "Candidate derivation outside derives; resolve instance and configuration")
      case g: Defn.GivenAlias if main && names(g.decltpe).exists(codecNames) => add(18, g, "Candidate handwritten instance; distinguish custom encoding from derivation")
      case n: Type.Name if main && n.value == "DataSource" && !pkg.startsWith("com.histmersion.util.database") => add(14, n, "Candidate DataSource outside database mechanism; distinguish composition")
      case a: Type.Apply if main && names(a.tpe).exists(Set("RefinedType", "RefinedSubtype")) && Set("api", "web", "cli", "plugin-api")(module) && a.argClause.values.lastOption.exists(t => t.syntax != "Pure") =>
        add(8, a, "Client refinement; preserve local CLI authorities and move checks to server boundary")
      case s: Term.Select if main && Set("getenv", "getProperty", "getProperties", "currentTimeMillis", "now", "env", "props")(s.name.value) =>
        add(15, s, "Candidate global read; resolve owner and composition boundary")
      case s: Term.Select if main && domain && Set("Files", "Paths", "File").contains(s.qual.syntax) => add(23, s, "Candidate direct filesystem access; resolve owner")
      case n: Term.Name if main && path.endsWith("Repo.scala") && Set("transact", "connect", "getConnection")(n.value) => add(13, n, "Candidate repository transaction ownership; resolve symbol")
      case i: Import if main && domain && i.importers.exists(_.ref.syntax.startsWith("com.histmersion.api")) => add(7, i, "Candidate API dependency in domain; check parse/conversion boundary")
      case c: Defn.Class if main =>
        if (top(c)) definitions.add(Definition(layer, pkg, c.name.value, path, c))
        if (c.name.value.endsWith("Service")) add(42, c, "Concrete Service; requires trait, Implementation and equivalent InMemory")
        if (c.mods.exists(_.is[Mod.Case]) && !hidden(c.ctor.mods)) add(6, c.ctor, "Public case-class constructor; review only if aggregate has its own invariant (refined fields alone are not one)")
        if ((c.name.value.endsWith("Repo") || c.name.value == "Implementation") && !hidden(c.mods)) add(11, c.name, "Candidate implementation visibility")
      case t: Defn.Trait if main =>
        if (top(t)) definitions.add(Definition(layer, pkg, t.name.value, path, t))
        if (t.name.value.endsWith("Service")) {
          val cs = doc.tree.collect { case c: Defn.Class => c.name.value }.toSet
          if (!Set("Implementation", "InMemory").subsetOf(cs)) add(16, t.name, "Service missing Implementation or InMemory in file")
        }
      case e: Defn.Enum if main =>
        if (top(e)) definitions.add(Definition(layer, pkg, e.name.value, path, e))
        if (!e.templ.stats.exists(s => names(s).exists(Set("label", "display")))) add(28, e.name, "Candidate vocabulary without friendly form; inspect extensions and UI rendering")
      case o: Defn.Object if main =>
        if (top(o)) {
          definitions.add(Definition(layer, pkg, o.name.value, path, o))
          if (o.templ.stats.exists(_.is[Defn.Def])) add(3, o.name, "Object with functions; classify against approved operations-on-types plan")
        }
        val constants = o.templ.stats.collect { case v: Defn.Val if v.rhs.is[Lit.String] => v }
        if (constants.nonEmpty) add(35, constants.head, "Candidate string vocabulary in " + o.name.value + ": " + constants.size + " literal members; distinguish open identifiers and constants")
        if (o.name.value.endsWith("Markdown")) add(36, o.name, "Renderer object candidate for Markdown[A] instance")
        if (o.name.value.endsWith("Repo") && !hidden(o.mods)) add(11, o.name, "Repository object without package-private visibility")
      case _ => ()
    }
    if (main) {
      val root = doc.tree.collect { case p: Pkg => p }.lastOption
      root.foreach { p =>
        if (Set("com.histmersion.db", "com.histmersion.files", "com.histmersion.values", "com.histmersion.upstreams")(pkg)) add(10, p.ref, "Layer bucket package")
        if (pkg == "com.histmersion.cli") add(37, p.ref, "CLI root; classify entry/composition exemptions")
        if (Set("com.histmersion.domain", "com.histmersion.server", "com.histmersion.api", "com.histmersion.web")(pkg)) add(38, p.ref, "Root package; classify composition/shared-kernel exemptions")
        if (module == "config" && pkg == "com.histmersion.config") add(31, p.ref, "Config root; classify domain ownership")
      }
      doc.tokens.collect { case c: Token.Comment => c }.foreach { c =>
        if (!(module == "plugin-api" && c.text.startsWith("/**"))) {
          findings.add(Finding(22, path, c.pos.startLine + 1, c.pos.startColumn + 1, "Comment requires external-reason review", doc.tree, Some(c)))
        }
      }
    } else if (path.contains("/src/test/"))
      doc.tree.collect { case n: Name if Set("TestDb", "Postgres", "PostgreSQLContainer")(n.value) => n }.headOption.foreach { n =>
        add(17, n, "DB-test candidate; classify contract/repository/SQL suites before InMemory migration")
      }
    Patch.empty
  }

  override def afterComplete(): Unit = {
    val defs = definitions.asScala.toList
    defs.groupBy(d => (d.layer, d.name)).values.filter(_.map(_.path).distinct.size > 1).foreach(_.foreach { d =>
      findings.add(Finding(5, d.path, d.tree.pos.startLine + 1, 1, "Candidate duplicate concept in layer " + d.layer, d.tree))
    })
    val out = Paths.get("refactor-audit")
    Files.createDirectories(out)
    val all = findings.asScala.toList.sortBy(f => (f.goal, f.path, f.line, f.column))
    def escape(s: String): String = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\r", "\\r").replace("\n", "\\n")
    def write(name: String, lines: Iterable[String]): Unit = Files.write(out.resolve(name), lines.toList.asJava, UTF_8)
    write("findings.tsv", List("goal\tpath\tline\tcolumn\tclassification\treason\tsource\tast") ++ all.map(f =>
      List("G" + f.goal, f.path, f.line.toString, f.column.toString, "REVIEW", f.reason,
        f.token.fold(f.tree.pos.text)(_.text), f.token.fold(f.tree.structure)(_.structure)).map(escape).mkString("\t")))
    write("files.txt", visited.asScala.toList.sorted)
    val implemented = Set(1,2,3,4,5,6,7,8,10,11,13,14,15,16,17,18,19,20,22,23,27,28,31,35,36,37,38,42)
    write("checklist.md", List("# AST audit checklist", "", "Syntactic candidates, not a claim of complete violations. No application source is changed. Each findings.tsv row includes exact source and AST. Zero candidates is not proof of compliance.", "", "| Goal | Candidates | Next step |", "|---|---:|---|") ++ (1 to 43).map { goal =>
      val count = all.count(_.goal == goal)
      val action = if (implemented(goal)) "[ ] Resolve candidates using semantic symbols and approved design; add fixtures before rewriting" else "[ ] NOT MEASURED: implement semantic/design/build/resource check; never treat as zero violations"
      s"| G$goal | ${if (implemented(goal)) count.toString else "unknown"} | $action |"
    } ++ List("", "## Execution", "", s"Parsed ${visited.size} files; ${all.size} candidate findings. The Scalafix process exit status must also be checked for parse failures.", "", "- [ ] Establish compiling baseline and fresh SemanticDB for definitions AND callers.", "- [ ] Group findings by semantic symbol and AST shape; record REWRITE/LINT/UNCHANGED/DECIDE.", "- [ ] Test every safe rewrite against expected output and look-alikes, then compile callers.", "- [ ] Apply safe rules across main and test sources; rerun inventory and test idempotence.", "- [ ] Separately implement final-type migrations, retaining compiler errors as a tracked repair queue.", "- [ ] Cover all unmeasured goals before claiming the refactor is complete."))
  }
}
