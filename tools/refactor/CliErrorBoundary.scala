import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, Configured, generic}
import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets.UTF_8
import scala.jdk.CollectionConverters._
import java.util.concurrent.ConcurrentLinkedQueue

object CliErrorBoundary {
  final case class Policy(mode: String = "inspect", report: String = "refactor-audit/cli-error.tsv",
    boundary: String = "com/histmersion/cli/Cli.", target: String = "com.histmersion.cli.Cli",
    sourcePath: String = "/cli/src/main/scala/")
  object Policy {
    implicit val surface: generic.Surface[Policy] = generic.deriveSurface[Policy]
    implicit val decoder: ConfDecoder[Policy] = generic.deriveDecoder(Policy()).noTypos
  }
}
class CliErrorBoundary(val policy: CliErrorBoundary.Policy) extends SemanticRule("CliErrorBoundary") {
  def this() = this(CliErrorBoundary.Policy())
  import CliErrorBoundary._
  private val rows = new ConcurrentLinkedQueue[List[String]]()
  private val console = SymbolMatcher.normalized("scala.Console.err")
  private val process = SymbolMatcher.normalized("java.lang.System.err")
  private val roots = SymbolMatcher.normalized("scala.Console", "java.lang.System")
  private def inside(t: Tree)(implicit doc: SemanticDocument): Boolean = t.parent.exists {
    case o: Defn.Object if o.name.symbol.value == policy.boundary => true
    case p => inside(p)
  }
  override def withConfiguration(c: Configuration): Configured[Rule] =
    c.conf.getOrElse("CliErrorBoundary")(Policy()).andThen { p =>
      if (!Set("inspect", "apply", "check")(p.mode) || p.boundary.isEmpty || p.target.isEmpty || p.sourcePath.isEmpty)
        Configured.error("Require mode, boundary, target and source scope")
      else Configured.ok(new CliErrorBoundary(p))
    }
  override def fix(implicit doc: SemanticDocument): Patch = {
    val path = doc.input match {
      case Input.File(p, _) => p.toString
      case Input.VirtualFile(p, _) => p
      case x => x.toString
    }
    if (!path.contains(policy.sourcePath)) Patch.empty
    else doc.tree.collect {
      case receiver @ Term.Select(_, n) if (console.matches(n) || process.matches(n)) && !inside(receiver) =>
        val selected = receiver.parent.collect { case s: Term.Select if s.qual eq receiver => s }
        val call = selected.flatMap(s => s.parent.collect { case a: Term.Apply if a.fun eq s => a })
        val name = selected.map(_.name.value).getOrElse("")
        val signature = selected.flatMap(_.name.symbol.info).map(_.signature)
        val stringOnly = signature.exists {
          case MethodSignature(_, lists, _) => lists.flatten.map(_.signature) match {
            case List(ValueSignature(TypeRef(_, tpe, _))) => tpe.value == "java/lang/String#"
            case _ => false
          }
          case _ => false
        }
        val noArgs = signature.exists { case MethodSignature(_, lists, _) => lists.flatten.isEmpty; case _ => false }
        val supported = call.exists(a =>
          ((name == "print" || name == "println") && stringOnly && a.argClause.values.size == 1) ||
          ((name == "println" || name == "flush") && noArgs && a.argClause.values.isEmpty)) &&
          selected.exists(_.name.symbol.value.startsWith("java/io/PrintStream#")) &&
          roots.matches(receiver.qual.symbol) && (receiver.qual.is[Term.Name] || receiver.qual.is[Term.Select])
        val prefix = if (console.matches(n)) "error" else "processError"
        val suffix = name match { case "print" => "Write"; case "println" => "Line"; case "flush" => "Flush"; case _ => "Unsupported" }
        val target = policy.target + "." + prefix + suffix
        val context = call.map(x => x: Tree).getOrElse(receiver)
        rows.add(List(path, (n.pos.startLine + 1).toString, selected.map(_.name.symbol.value).getOrElse(n.symbol.value),
          signature.toString, context.pos.text, context.structure, target, if (supported) "rewrite" else "unsupported"))
        if (supported && policy.mode == "apply") Patch.replaceTree(selected.get, target).atomic else Patch.empty
    }.asPatch
  }
  override def afterComplete(): Unit = {
    def esc(s: String) = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r")
    val all = rows.asScala.toList.sortBy(r => (r.head, r(1).toInt))
    val p = Paths.get(policy.report)
    Files.createDirectories(p.toAbsolutePath.getParent)
    Files.write(p, ("path\tline\tsymbol\tsignature\tsource\tast\ttarget\taction" :: all.map(_.map(esc).mkString("\t"))).asJava, UTF_8)
    val changes = all.count(_.last == "rewrite")
    Files.write(Paths.get(policy.report + ".summary"), List(s"rewrites=$changes", s"unsupported=${all.size - changes}", s"files=${all.map(_.head).distinct.size}").asJava, UTF_8)
    require(policy.mode != "check" || changes == 0, s"$changes CLI stderr rewrites remain")
  }
}
