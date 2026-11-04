import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, Configured, generic}
import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters._

object SourceContracts {
  final case class Goal(id: Int = 0, title: String = "", checks: List[String] = Nil, remaining: String = "")
  object Goal {
    implicit val surface: generic.Surface[Goal] = generic.deriveSurface[Goal]
    implicit val decoder: ConfDecoder[Goal] = generic.deriveDecoder(Goal()).noTypos
  }
  final case class Concept(owner: String = "", parameter: String = "", raw: String = "", required: String = "")
  object Concept {
    implicit val surface: generic.Surface[Concept] = generic.deriveSurface[Concept]
    implicit val decoder: ConfDecoder[Concept] = generic.deriveDecoder(Concept()).noTypos
  }
  final case class Example(path: String = "", line: Int = 0, snippet: String = "", rule: String = "",
      count: Int = 0, reason: String = "")
  object Example {
    implicit val surface: generic.Surface[Example] = generic.deriveSurface[Example]
    implicit val decoder: ConfDecoder[Example] = generic.deriveDecoder(Example()).noTypos
  }
  final case class Policy(concepts: List[Concept] = Nil, examples: List[Example] = Nil,
      sourceRoots: List[String] = List("modules"), report: String = "refactor-audit/source-contracts",
      fixtureMode: Boolean = false, fixturePaths: List[String] = Nil, goals: List[Goal] = Nil,
      requireReady: Boolean = false, controllerPackages: List[String] = List("com.histmersion.domain."))
  object Policy {
    implicit val surface: generic.Surface[Policy] = generic.deriveSurface[Policy]
    implicit val decoder: ConfDecoder[Policy] = generic.deriveDecoder(Policy()).noTypos
  }
  final case class Hit(rule: String, path: String, line: Int, column: Int, message: String, source: String, ast: String)
}

class SourceContracts(val policy: SourceContracts.Policy) extends SyntacticRule("SourceContracts") {
  def this() = this(SourceContracts.Policy())
  import SourceContracts._
  private val hits = new ConcurrentLinkedQueue[Hit]()
  private val files = new ConcurrentLinkedQueue[String]()
  private val fingerprints = new ConcurrentLinkedQueue[(String, String)]()
  private val failures = new ConcurrentLinkedQueue[String]()
  private val checked = new ConcurrentLinkedQueue[Example]()
  private val rules = Set("primitive-role", "repo-visibility", "bucket", "handlers", "object-main")
  private val externalChecks = Set("semantic-capabilities", "candidate-inventory")
  private val fixtureChecked = new ConcurrentLinkedQueue[String]()
  private def absolute(p: String): String = Paths.get(p).toAbsolutePath.normalize.toString
  private def digest(bytes: Array[Byte]): String = java.security.MessageDigest.getInstance("SHA-256")
    .digest(bytes).map(b => f"${b & 0xff}%02x").mkString

  override def withConfiguration(config: Configuration): Configured[Rule] =
    config.conf.getOrElse("SourceContracts")(Policy()).andThen { p =>
      val invalid = p.concepts.exists(c => c.owner.isEmpty || c.parameter.isEmpty || c.raw.isEmpty || c.required.isEmpty) ||
        p.concepts.map(c => (c.owner, c.parameter, c.raw)).distinct.size != p.concepts.size ||
        p.examples.exists(e => e.path.isEmpty || e.line < 1 || e.snippet.isEmpty || !rules(e.rule) || e.count < 0 || e.reason.isEmpty) ||
        p.examples.map(e => (e.path, e.line, e.rule)).distinct.size != p.examples.size || p.report.isEmpty ||
        (p.goals.nonEmpty && p.goals.map(_.id).sorted != (1 to 43).toList) ||
        p.goals.exists(g => g.title.isEmpty || !g.checks.forall(r => rules(r) || externalChecks(r))) ||
        (p.requireReady && (p.goals.isEmpty || p.fixturePaths.isEmpty))
      if (invalid) Configured.error("Invalid/duplicate concept or example, unknown check, or incomplete goal register; require exactly G1-G43 and fixture paths for readiness")
      else Configured.ok(new SourceContracts(p))
    }

  private def parents(t: Tree): List[Tree] = t.parent.toList.flatMap(p => p :: parents(p))
  private def packageOf(t: Tree): String = parents(t).reverse.collect { case p: Pkg => p.ref.syntax }.mkString(".")
  private def controllerName(n: Name): Boolean = n.value.endsWith("Handlers") &&
    policy.controllerPackages.exists(packageOf(n).startsWith)
  private def hidden(mods: List[Mod]): Boolean = mods.exists {
    case _: Mod.Private | _: Mod.Protected => true
    case _ => false
  }
  private def parameters(t: Tree): List[Term.Param] = t.children.flatMap {
    case p: Term.Param => List(p)
    case _: Term | _: Type => Nil
    case child => parameters(child)
  }
  private def owner(t: Tree, pkg: String): String = {
    val ns = parents(t).reverse.collect {
      case d: Defn.Class => d.name.value
      case d: Defn.Trait => d.name.value
      case d: Defn.Object => d.name.value
      case d: Defn.Enum => d.name.value
    }
    (pkg :: ns).mkString(".")
  }
  override def fix(implicit doc: SyntacticDocument): Patch = {
    val path = absolute(doc.input match {
      case Input.File(p, _) => p.toString
      case Input.VirtualFile(p, _) => p
      case x => x.toString
    })
    files.add(path)
    fingerprints.add(path -> digest(doc.input.text.getBytes(UTF_8)))
    val fixture = policy.fixtureMode || policy.fixturePaths.exists(p => absolute(p) == path)
    val local = scala.collection.mutable.ListBuffer.empty[Hit]
    def add(rule: String, t: Tree, message: String): Unit = local += Hit(rule, path, t.pos.startLine + 1,
      t.pos.startColumn + 1, message, t.pos.text, t.structure)
    if (path.contains("/src/main/") || fixture) {
      val methods = doc.tree.collect {
        case d: Defn.Def if !hidden(d.mods) => d
        case d: Decl.Def if !hidden(d.mods) => d
      }
      methods.flatMap(parameters).foreach { p =>
          val own = owner(p, packageOf(p))
          policy.concepts.filter(c => c.owner == own && c.parameter == p.name.value &&
            p.decltpe.exists(_.syntax == c.raw)).foreach { c =>
            add("primitive-role", p, s"${c.owner}.${c.parameter} requires ${c.required}, found ${c.raw}")
          }
      }
      def repository(n: Name, mods: List[Mod]): Unit = {
        val pkg = packageOf(n)
        val localOwners = owner(n, pkg).stripPrefix(pkg + ".").split("\\.").toSet + pkg.split("\\.").last
        val featurePrivate = mods.exists {
          case m: Mod.Private => m.within.syntax.isEmpty || m.within.syntax == "this" || localOwners(m.within.syntax)
          case _ => false
        }
        if (pkg.startsWith("com.histmersion.domain.") && n.value.endsWith("Repo") && !featurePrivate)
          add("repo-visibility", n, "Feature repository must be hidden outside its owning feature")
      }
      doc.tree.traverse {
        case d: Defn.Class => repository(d.name, d.mods)
        case d: Defn.Object => repository(d.name, d.mods)
        case d: Defn.Trait => repository(d.name, d.mods)
        case _ => ()
      }
      doc.tree.traverse {
        case p: Pkg if Set("com.histmersion.db", "com.histmersion.files", "com.histmersion.values", "com.histmersion.upstreams")(
            (List(packageOf(p), p.ref.syntax).filter(_.nonEmpty)).mkString(".")) =>
          add("bucket", p.ref, "Package declarations must name the owning feature or supporting mechanism")
        case d: Defn.Def if d.name.value == "main" && !hidden(d.mods) && parameters(d).size == 1 &&
            parameters(d).head.decltpe.exists(t => Set("Array[String]", "scala.Array[String]", "Array[java.lang.String]", "scala.Array[java.lang.String]")(t.syntax)) &&
            d.decltpe.forall(t => Set("Unit", "scala.Unit")(t.syntax)) && parents(d).collectFirst {
          case _: Defn.Object => true
          case _: Defn.Class | _: Defn.Trait | _: Defn.Enum => false
        }.contains(true) => add("object-main", d.name, "Move the entry point to a top-level @main definition and update launch configuration")
        case d: Defn.Class if controllerName(d.name) => add("handlers", d.name, "Feature adapters are named Controllers")
        case d: Defn.Trait if controllerName(d.name) => add("handlers", d.name, "Feature adapters are named Controllers")
        case d: Defn.Object if controllerName(d.name) => add("handlers", d.name, "Feature adapters are named Controllers")
        case d: Defn.Enum if controllerName(d.name) => add("handlers", d.name, "Feature adapters are named Controllers")
        case d: Defn.Type if controllerName(d.name) => add("handlers", d.name, "Feature adapters are named Controllers")
        case _ => ()
      }
    }
    if (!fixture) hits.addAll(local.asJava)
    val lines = doc.input.text.linesIterator.toVector
    policy.examples.filter(e => absolute(e.path) == path).foreach { e =>
      checked.add(e)
      val actual = local.count(h => h.rule == e.rule && h.line == e.line)
      if (!lines.lift(e.line - 1).exists(_.contains(e.snippet))) failures.add(s"Stale example: ${e.path}:${e.line}: ${e.snippet}")
      if (actual != e.count) failures.add(s"${e.path}:${e.line} ${e.rule}: expected ${e.count}, got $actual")
    }
    if (fixture) {
      fixtureChecked.add(path)
      val expected = doc.tokens.collect { case c: Token.Comment if c.text.startsWith("// expect: ") =>
        c.pos.startLine + 1 -> c.text.stripPrefix("// expect: ").trim.split(",").toList.sorted
      }.toMap
      val actual = local.toList.groupBy(_.line).view.mapValues(_.map(_.rule).sorted).toMap
      if (actual != expected) failures.add(s"$path: expected=$expected actual=$actual")
    }
    Patch.empty
  }
  override def afterComplete(): Unit = {
    val expected = policy.sourceRoots.flatMap { root =>
      val stream = Files.walk(Paths.get(root).toAbsolutePath.normalize)
      try stream.iterator.asScala.filter(p => Files.isRegularFile(p) && p.toString.endsWith(".scala")).map(_.toString).toList
      finally stream.close()
    }.toSet
    val seen = files.asScala.toSet
    val missing = (expected -- seen).toList.sorted
    val missingFixtures = policy.fixturePaths.map(absolute).filterNot(fixtureChecked.asScala.toSet)
    val changedSources = fingerprints.asScala.collect { case (path, hash) if
      !Files.isRegularFile(Paths.get(path)) || digest(Files.readAllBytes(Paths.get(path))) != hash => path }.toList
    val unchecked = policy.examples.filterNot(checked.asScala.toSet)
    val out = Paths.get(policy.report)
    Files.createDirectories(out)
    def esc(s: String): String = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\r", "\\r").replace("\n", "\\n")
    def write(n: String, ls: Iterable[String]): Unit = Files.write(out.resolve(n), ls.toList.asJava, UTF_8)
    val all = hits.asScala.toList.sortBy(h => (h.path, h.line, h.column, h.rule))
    write("findings.tsv", List("rule\tpath\tline\tcolumn\treason\tsource\tast") ++ all.map(h =>
      List(h.rule, h.path, h.line.toString, h.column.toString, h.message, h.source, h.ast).map(esc).mkString("\t")))
    write("files.txt", seen.toList.sorted)
    write("missing-files.txt", missing)
    write("input-manifest.tsv", List("path\tsha256") ++ fingerprints.asScala.toList.sorted.map { case (p, h) => s"$p\t$h" })
    write("policy-sha256.txt", List(digest(policy.toString.getBytes(UTF_8))))
    write("verification.txt", List(s"examples=${checked.size}/${policy.examples.size}", s"failures=${failures.size}") ++ failures.asScala)
    write("summary.txt", List(s"files=${seen.size}", s"expected-files=${expected.size}", s"missing-files=${missing.size}",
      s"findings=${all.size}", s"fixture-files=${fixtureChecked.size}",
      "scope=configured primitive roles, feature repository visibility, forbidden packages, Handlers declarations, object main; other goals NOT MEASURED",
      "mode=inventory; violations do not fail this run, but missing coverage or failed expectations do") ++
      rules.toList.sorted.map(r => s"$r=${all.count(_.rule == r)}"))
    val fullyMeasured = Map(10 -> Set("bucket"))
    def covered(g: Goal): Boolean = fullyMeasured.get(g.id).exists(rs => rs.subsetOf(g.checks.toSet)) && g.remaining.isEmpty
    val blocked = policy.goals.filterNot(covered)
    if (policy.goals.nonEmpty) {
      write("coverage-matrix.md", List("# Migration detector coverage", "",
        "Generated by SourceContracts from migration.conf. Counts are current only for checks in this invocation; external and candidate checks are not completion evidence.", "",
        "| Goal | Requirement | Coverage | Checks | Measured findings | Remaining evidence |", "|---|---|---|---|---|---|") ++
        policy.goals.sortBy(_.id).map { g =>
          val measured = g.checks.filter(rules).map(r => s"$r=${all.count(_.rule == r)}").mkString(", ")
          s"| G${g.id} | ${g.title} | ${if (covered(g)) "MEASURED" else if (g.checks.nonEmpty) "PARTIAL" else "UNMEASURED"} | ${g.checks.mkString(", ")} | ${if (measured.isEmpty) "unknown" else measured} | ${g.remaining} |"
        })
      write("readiness.txt", List(s"ready=${blocked.isEmpty && missing.isEmpty && missingFixtures.isEmpty && failures.isEmpty && unchecked.isEmpty && changedSources.isEmpty}",
        s"uncovered-goals=${blocked.map(g => s"G${g.id}").mkString(",")}", s"missing-files=${missing.size}",
        s"missing-fixtures=${missingFixtures.size}", s"assertion-failures=${failures.size}", s"unchecked-examples=${unchecked.size}", s"changed-during-scan=${changedSources.size}",
        "readiness concerns detector coverage, not whether migration violations are zero"))
    }
    require(seen.nonEmpty, "No sources analyzed")
    require(missing.isEmpty, s"Missing ${missing.size} Scala sources")
    require(missingFixtures.isEmpty, s"Missing fixtures: $missingFixtures")
    require(unchecked.isEmpty, s"Unchecked examples: $unchecked")
    require(failures.isEmpty, failures.asScala.mkString("\n"))
    require(changedSources.isEmpty, s"Sources changed during scan: $changedSources")
    if (policy.requireReady) require(blocked.isEmpty, s"Migration detectors NOT READY: ${blocked.size} goals remain partial or unmeasured; see coverage-matrix.md")
  }
}
