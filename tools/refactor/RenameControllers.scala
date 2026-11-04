import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, Configured, generic}
import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets.UTF_8
import scala.jdk.CollectionConverters._
import java.util.concurrent.ConcurrentLinkedQueue

object RenameControllers {
  final case class Mapping(symbol: String = "", target: String = "")
  object Mapping {
    implicit val surface: generic.Surface[Mapping] = generic.deriveSurface[Mapping]
    implicit val decoder: ConfDecoder[Mapping] = generic.deriveDecoder(Mapping()).noTypos
  }
  final case class Policy(mode: String = "inspect", report: String = "refactor-audit/controller-renames.tsv", mappings: List[Mapping] = Nil)
  object Policy {
    implicit val surface: generic.Surface[Policy] = generic.deriveSurface[Policy]
    implicit val decoder: ConfDecoder[Policy] = generic.deriveDecoder(Policy()).noTypos
  }
}

class RenameControllers(val policy: RenameControllers.Policy) extends SemanticRule("RenameControllers") {
  def this() = this(RenameControllers.Policy())
  import RenameControllers._
  private val rows = new ConcurrentLinkedQueue[List[String]]()
  override def withConfiguration(c: Configuration): Configured[Rule] =
    c.conf.getOrElse("RenameControllers")(Policy()).andThen { p =>
      if (!Set("inspect", "apply", "check")(p.mode) || p.mappings.isEmpty ||
        p.mappings.map(_.symbol).distinct.size != p.mappings.size ||
        p.mappings.exists(m => !m.symbol.contains("/") || !m.target.matches("[A-Z][A-Za-z0-9]*")))
        Configured.error("Require unique semantic owner mappings, target identifiers and inspect/apply/check mode")
      else Configured.ok(new RenameControllers(p))
    }
  override def fix(implicit doc: SemanticDocument): Patch = {
    val path = doc.input match {
      case Input.File(p, _) => p.toString
      case Input.VirtualFile(p, _) => p
      case x => x.toString
    }
    doc.tree.collect { case n: Name =>
      policy.mappings.filter(m => n.value == m.symbol.split('/').last).map { m =>
        val sym = n.symbol.value
        val matched = sym == m.symbol + "#" || sym == m.symbol + "."
        val context = n.parent.filterNot(t => t.is[Defn.Class] || t.is[Defn.Object]).getOrElse(n)
        rows.add(List(if (matched) "rewrite" else "unchanged", path, (n.pos.startLine + 1).toString, sym, n.pos.text, context.structure, m.target))
        if (matched && policy.mode == "apply") Patch.replaceTree(n, m.target).atomic else Patch.empty
      }.asPatch
    }.asPatch
  }
  override def afterComplete(): Unit = {
    def escape(s: String) = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r")
    val all = rows.asScala.toList.sortBy(r => (r(1), r(2).toInt, r(3)))
    val p = Paths.get(policy.report)
    Files.createDirectories(p.toAbsolutePath.getParent)
    Files.write(p, ("action\tpath\tline\tsymbol\tsource\tast\ttarget" :: all.map(_.map(escape).mkString("\t"))).asJava, UTF_8)
    Files.write(Paths.get(policy.report + ".summary"), List(s"rewrites=${all.count(_.head == "rewrite")}", s"unchanged=${all.count(_.head == "unchanged")}", s"files=${all.map(_(1)).distinct.size}").asJava, UTF_8)
    require(policy.mode != "check" || !all.exists(_.head == "rewrite"), "Controller rename matches remain")
  }
}
