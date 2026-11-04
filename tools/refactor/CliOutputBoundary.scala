import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, Configured, generic}
import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets.UTF_8
import scala.jdk.CollectionConverters._
import java.util.concurrent.ConcurrentLinkedQueue

object CliOutputBoundary {
  final case class Policy(mode: String = "inspect", report: String = "refactor-audit/cli-output.tsv",
    boundary: String = "com/histmersion/cli/Cli.", target: String = "com.histmersion.cli.Cli",
    sourcePath: String = "/cli/src/main/scala/")
  object Policy {
    implicit val surface: generic.Surface[Policy] = generic.deriveSurface[Policy]
    implicit val decoder: ConfDecoder[Policy] = generic.deriveDecoder(Policy()).noTypos
  }
}
class CliOutputBoundary(val policy: CliOutputBoundary.Policy) extends SemanticRule("CliOutputBoundary") {
  def this() = this(CliOutputBoundary.Policy())
  import CliOutputBoundary._
  private val rows = new ConcurrentLinkedQueue[List[String]]()
  private val newline = SymbolMatcher.normalized("scala.Predef.println")
  private val plain = SymbolMatcher.normalized("scala.Predef.print")
  override def withConfiguration(c: Configuration): Configured[Rule] =
    c.conf.getOrElse("CliOutputBoundary")(Policy()).andThen { p =>
      if (!Set("inspect", "apply", "check")(p.mode) || p.boundary.isEmpty || p.target.isEmpty || p.sourcePath.isEmpty)
        Configured.error("Require mode, exact boundary owner, target and source scope")
      else Configured.ok(new CliOutputBoundary(p))
    }
  private def insideBoundary(t: Tree)(implicit doc: SemanticDocument): Boolean = t.parent.exists {
    case o: Defn.Object if o.name.symbol.value == policy.boundary => true
    case p => insideBoundary(p)
  }
  override def fix(implicit doc: SemanticDocument): Patch = {
    val path = doc.input match {
      case Input.File(p, _) => p.toString
      case Input.VirtualFile(p, _) => p
      case x => x.toString
    }
    if (!path.contains(policy.sourcePath)) Patch.empty
    else doc.tree.collect {
      case n: Term.Name if (newline.matches(n) || plain.matches(n)) && !insideBoundary(n) =>
        val tree: Tree = n.parent.collect { case s: Term.Select if s.name eq n => s }.getOrElse(n)
        val method = if (newline.matches(n)) "writeLine" else "write"
        val replacement = policy.target + "." + method
        val supported = tree match {
          case s: Term.Select => s.qual.symbol.value == "scala/Predef." && (s.qual.is[Term.Name] || s.qual.is[Term.Select])
          case _: Term.Name => true
          case _ => false
        }
        rows.add(List(path, (n.pos.startLine + 1).toString, n.symbol.value, tree.pos.text, tree.structure, replacement, if (supported) "rewrite" else "lint: receiver evaluation must be preserved"))
        if (supported && policy.mode == "apply") Patch.replaceTree(tree, replacement).atomic else Patch.empty
    }.asPatch
  }
  override def afterComplete(): Unit = {
    def esc(s: String) = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r")
    val all = rows.asScala.toList.sortBy(r => (r.head, r(1).toInt))
    val path = Paths.get(policy.report)
    Files.createDirectories(path.toAbsolutePath.getParent)
    Files.write(path, ("path\tline\tsymbol\tsource\tast\treplacement\taction" :: all.map(_.map(esc).mkString("\t"))).asJava, UTF_8)
    val rewrites = all.count(_.last == "rewrite")
    Files.write(Paths.get(policy.report + ".summary"), List(s"rewrites=$rewrites", s"unsupported=${all.size - rewrites}", s"files=${all.map(_.head).distinct.size}").asJava, UTF_8)
    require(policy.mode != "check" || rewrites == 0, s"$rewrites CLI stdout rewrites remain")
  }
}
