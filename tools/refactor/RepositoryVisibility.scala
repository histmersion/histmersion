import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, Configured, generic}
import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets.UTF_8
import scala.jdk.CollectionConverters._
import java.util.concurrent.ConcurrentLinkedQueue

object RepositoryVisibility {
  case class Target(symbol: String = "", scope: String = "")
  object Target {
    implicit val surface: generic.Surface[Target] = generic.deriveSurface[Target]
    implicit val decoder: ConfDecoder[Target] = generic.deriveDecoder(Target()).noTypos
  }
  case class Policy(mode: String = "inventory", report: String = "refactor-audit/repository-visibility", roots: List[String] = List("modules"), targets: List[Target] = Nil, approved: List[String] = Nil)
  object Policy {
    implicit val surface: generic.Surface[Policy] = generic.deriveSurface[Policy]
    implicit val decoder: ConfDecoder[Policy] = generic.deriveDecoder(Policy()).noTypos
  }
  def hash(text: String): String = java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(UTF_8)).map(b => f"${b & 255}%02x").mkString
}
class RepositoryVisibility(val policy: RepositoryVisibility.Policy) extends SemanticRule("RepositoryVisibility") {
  def this() = this(RepositoryVisibility.Policy())
  import RepositoryVisibility._
  private val files = new ConcurrentLinkedQueue[(String, String)]()
  private val declarations = new ConcurrentLinkedQueue[List[String]]()
  private val references = new ConcurrentLinkedQueue[List[String]]()
  override def withConfiguration(c: Configuration): Configured[Rule] = c.conf.getOrElse("RepositoryVisibility")(Policy()).andThen { p =>
    if (!Set("inventory", "apply", "check")(p.mode) || p.targets.isEmpty || p.targets.map(_.symbol).distinct.size != p.targets.size ||
      p.targets.exists(t => t.symbol.isEmpty || t.scope.isEmpty || !t.symbol.startsWith(t.scope.replace('.', '/') + "/")) ||
      !p.approved.forall(s => p.targets.exists(_.symbol == s)) || (p.mode != "inventory" && p.approved.isEmpty)) Configured.error("Require unique exact repository symbols, owning packages and approved subset")
    else {
      if (p.mode == "apply") {
        val plan = Files.readAllLines(Paths.get(p.report, "checklist.tsv"), UTF_8).asScala.drop(1).map(_.split("\t").toList)
        require(p.approved.nonEmpty && p.approved.forall(s => plan.exists(r => r.head == s && r(1) == "candidate")), "Only audited, unblocked candidates may be applied")
        val manifest = Files.readAllLines(Paths.get(p.report, "sources.tsv"), UTF_8).asScala.drop(1).map(_.split("\t").toList)
        require(manifest.nonEmpty, "Missing source coverage manifest")
        val current = p.roots.flatMap { root =>
          val walk = Files.walk(Paths.get(root).toAbsolutePath.normalize)
          try walk.iterator.asScala.filter(f => Files.isRegularFile(f) && f.toString.endsWith(".scala") && (f.toString.contains("/src/main/") || f.toString.contains("/src/test/"))).map(_.toString).toList
          finally walk.close()
        }.toSet
        require(current == manifest.map(_.head).toSet, "Source file set changed; regenerate repository inventory")
        manifest.foreach(r => require(hash(Files.readString(Paths.get(r.head))) == r(1), s"Stale repository inventory: ${r.head}"))
      }
      Configured.ok(new RepositoryVisibility(p))
    }
  }
  private def parents(t: Tree): List[Tree] = t.parent.toList.flatMap(p => p :: parents(p))
  private def pkg(t: Tree): String = parents(t).collect { case p: Pkg => p.ref.syntax }.reverse.mkString(".")
  override def fix(implicit doc: SemanticDocument): Patch = {
    val path = doc.input match { case Input.File(p, _) => p.toString; case Input.VirtualFile(p, _) => p; case x => x.toString }
    files.add((path, hash(doc.input.text)))
    val patches = scala.collection.mutable.ListBuffer.empty[Patch]
    doc.tree.collect {
      case d: Defn.Object if policy.targets.exists(_.symbol == d.name.symbol.value) =>
        val t = policy.targets.find(_.symbol == d.name.symbol.value).get
        require(pkg(d) == t.scope, s"Owner mismatch for ${t.symbol}")
        val hidden = d.mods.exists { case m: Mod.Private => m.within.syntax == t.scope.split('.').last; case _ => false }
        declarations.add(List(t.symbol, path, (d.pos.startLine + 1).toString, hidden.toString, d.name.structure))
        if (policy.approved.contains(t.symbol) && !hidden) {
          require(d.mods.forall(m => !m.is[Mod.Private] && !m.is[Mod.Protected]), "Existing access modifier requires review")
          if (policy.mode == "apply") patches += Patch.addLeft(d, "private[" + t.scope.split('.').last + "] ").atomic
          if (policy.mode == "check") patches += Patch.lint(Diagnostic("remaining", "Repository is still public", d.name.pos))
        }
      case n: Name =>
        policy.targets.filter(t => n.symbol.value.startsWith(t.symbol)).foreach { t =>
          val definition = n.parent.exists { case d: Defn.Object => d.name eq n; case d: Defn.Def => d.name eq n; case _ => false }
          if (!definition) {
            val p = pkg(n)
            val inside = p == t.scope || p.startsWith(t.scope + ".")
            val context = n.parent.getOrElse(n)
            references.add(List(t.symbol, path, (n.pos.startLine + 1).toString, p, inside.toString, n.symbol.value, context.pos.text, context.structure))
          }
        }
    }
    patches.toList.asPatch
  }
  override def afterComplete(): Unit = {
    if (policy.mode != "inventory") {
      require(policy.approved.forall(s => declarations.asScala.count(_.head == s) == 1), "Every approved repository must be visited exactly once")
      return
    }
    val expected = policy.roots.flatMap { r =>
      val walk = Files.walk(Paths.get(r).toAbsolutePath.normalize)
      try walk.iterator.asScala.filter(p => Files.isRegularFile(p) && p.toString.endsWith(".scala") && (p.toString.contains("/src/main/") || p.toString.contains("/src/test/"))).map(_.toString).toList
      finally walk.close()
    }.toSet
    val seen = files.asScala.map(_._1).toSet
    val missing = expected -- seen
    val ds = declarations.asScala.toList
    val refs = references.asScala.toList.distinct
    val out = Paths.get(policy.report)
    Files.createDirectories(out)
    def write(name: String, header: String, rows: List[List[String]]): Unit = {
      def esc(s: String) = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r")
      Files.write(out.resolve(name), (header :: rows.map(_.map(esc).mkString("\t"))).asJava, UTF_8)
    }
    write("sources.tsv", "path\tsha256", files.asScala.toList.sortBy(_._1).map { case (p, h) => List(p, h) })
    write("declarations.tsv", "symbol\tpath\tline\tprivate\tast", ds.sortBy(_.head))
    write("references.tsv", "repository\tpath\tline\tpackage\tinside-feature\tsymbol\tsource\tast", refs.sortBy(r => (r.head, r(1), r(2).toInt)))
    write("outside-files.tsv", "repository\tpath\tpackage", refs.filter(_(4) == "false").map(r => List(r.head, r(1), r(3))).distinct.sortBy(r => (r.head, r(1))))
    write("checklist.tsv", "repository\taction\tproduction-outside-files\ttest-outside-files\treferences", policy.targets.map { t =>
      val uses = refs.filter(_.head == t.symbol)
      val outside = uses.filter(_(4) == "false")
      val action = if (outside.nonEmpty) "blocked" else if (ds.exists(d => d.head == t.symbol && d(3) == "true")) "private" else "candidate"
      List(t.symbol, action, outside.filter(_(1).contains("/src/main/")).map(_(1)).distinct.size.toString, outside.filter(_(1).contains("/src/test/")).map(_(1)).distinct.size.toString, uses.size.toString)
    })
    Files.write(out.resolve("coverage.txt"), List(s"expected=${expected.size}", s"visited=${seen.size}", s"missing=${missing.size}", "scope=resolved source references including imports, aliases and member symbols; inferred API escapes and reflection require review and compilation").asJava, UTF_8)
    require(missing.isEmpty, s"Missing source coverage: ${missing.toList.sorted.mkString(", ")}")
    require(policy.targets.forall(t => ds.count(_.head == t.symbol) == 1), "Each configured repository must resolve to exactly one declaration")
  }
}
