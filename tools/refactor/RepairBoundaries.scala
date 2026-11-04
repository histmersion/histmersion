import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, Configured, generic}
import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets.UTF_8
import scala.jdk.CollectionConverters._
import java.util.concurrent.ConcurrentLinkedQueue

object RepairBoundaries {
  final case class Rewrite(id: String = "", paths: List[String] = Nil, owner: String = "", kind: String = "term", from: String = "", to: String = "")
  object Rewrite {
    implicit val surface: generic.Surface[Rewrite] = generic.deriveSurface[Rewrite]
    implicit val decoder: ConfDecoder[Rewrite] = generic.deriveDecoder(Rewrite()).noTypos
  }
  final case class Policy(mode: String = "inspect", report: String = "refactor-audit/repairs.tsv", rewrites: List[Rewrite] = Nil)
  object Policy {
    implicit val surface: generic.Surface[Policy] = generic.deriveSurface[Policy]
    implicit val decoder: ConfDecoder[Policy] = generic.deriveDecoder(Policy()).noTypos
  }
}

class RepairBoundaries(val policy: RepairBoundaries.Policy) extends SyntacticRule("RepairBoundaries") {
  def this() = this(RepairBoundaries.Policy())
  import RepairBoundaries._
  private implicit val dialect: Dialect = dialects.Scala3
  private def parse(kind: String, code: String): Tree = kind match {
    case "term" => code.parse[Term].get
    case "type" => code.parse[Type].get
    case "stat" => code.parse[Stat].get
  }
  private val compiled = policy.rewrites.map(r => (r, parse(r.kind, r.from), parse(r.kind, r.to)))
  private val changes = new ConcurrentLinkedQueue[List[String]]()
  override def withConfiguration(c: Configuration): Configured[Rule] =
    c.conf.getOrElse("RepairBoundaries")(Policy()).andThen { p =>
      if (!Set("inspect", "apply", "check")(p.mode) || p.rewrites.map(_.id).distinct.size != p.rewrites.size ||
        p.rewrites.exists(r => r.id.isEmpty || r.paths.isEmpty || r.from.isEmpty || r.to.isEmpty || !Set("term", "type", "stat")(r.kind)))
        Configured.error("Require scoped, uniquely named AST rewrites and inspect/apply/check mode")
      else Configured.ok(new RepairBoundaries(p))
    }
  private def parents(t: Tree): List[Tree] = t.parent.toList.flatMap(p => p :: parents(p))
  private def unify(pattern: Any, actual: Any, captures: scala.collection.mutable.Map[String, Tree]): Boolean = (pattern, actual) match {
    case (n: Term.Name, t: Term) if n.value.startsWith("__") => bind(n.value, t, captures)
    case (n: Type.Name, t: Type) if n.value.startsWith("__") => bind(n.value, t, captures)
    case (a: Tree, b: Tree) => a.productPrefix == b.productPrefix && unify(a.productIterator.toList, b.productIterator.toList, captures)
    case (a: List[_], b: List[_]) => a.size == b.size && a.zip(b).forall { case (x, y) => unify(x, y, captures) }
    case (Some(a), Some(b)) => unify(a, b, captures)
    case _ => pattern == actual
  }
  private def bind(name: String, tree: Tree, captures: scala.collection.mutable.Map[String, Tree]): Boolean =
    captures.get(name).fold { captures(name) = tree; true }(_.structure == tree.structure)
  override def fix(implicit doc: SyntacticDocument): Patch = {
    val path = doc.input match {
      case Input.File(p, _) => p.toString
      case Input.VirtualFile(p, _) => p
      case x => x.toString
    }
    val applicable = compiled.filter { case (r, _, _) => r.paths.exists(path.endsWith) }
    val patches = scala.collection.mutable.ListBuffer.empty[Patch]
    def loop(t: Tree): Unit = {
      val found = applicable.iterator.flatMap { case (r, from, to) =>
        val scoped = r.owner.isEmpty || parents(t).exists { case d: Defn.Def => d.name.value == r.owner; case _ => false }
        val captures = scala.collection.mutable.Map.empty[String, Tree]
        if (scoped && unify(from, t, captures)) {
          val slots = to.collect {
            case n: Term.Name if captures.contains(n.value) => (n.pos, captures(n.value))
            case n: Type.Name if captures.contains(n.value) => (n.pos, captures(n.value))
          }.sortBy(-_._1.start)
          val rendered = slots.foldLeft(r.to) { case (code, (pos, captured)) =>
            code.patch(pos.start, captured.pos.text, pos.end - pos.start)
          }
          val result = parse(r.kind, rendered)
          val expected = to.transform {
            case n: Term.Name if captures.contains(n.value) => captures(n.value)
            case n: Type.Name if captures.contains(n.value) => captures(n.value)
          }
          require(result.structure == expected.structure, s"${r.id}: captured syntax changed AST grouping")
          if (rendered == t.pos.text) None else Some((r, result, rendered))
        } else None
      }.take(1).toList.headOption
      found match {
        case Some((r, result, rendered)) =>
          changes.add(List(r.id, path, (t.pos.startLine + 1).toString, t.pos.text, rendered, t.structure, result.structure))
          if (policy.mode == "apply") patches += Patch.replaceTree(t, rendered).atomic
        case None => t.children.foreach(loop)
      }
    }
    loop(doc.tree)
    patches.toList.asPatch
  }
  override def afterComplete(): Unit = {
    def esc(s: String): String = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r")
    val p = Paths.get(policy.report)
    Files.createDirectories(p.toAbsolutePath.getParent)
    Files.write(p, ("rule\tpath\tline\tbefore\tafter\tbefore-ast\tafter-ast" :: changes.asScala.toList.sortBy(r => (r(1), r(2).toInt)).map(_.map(esc).mkString("\t"))).asJava, UTF_8)
    require(policy.mode != "check" || changes.isEmpty, s"${changes.size} boundary repairs remain")
  }
}
