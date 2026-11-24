import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, generic}

object ProblemCaseRewrite {
  case class Rewrite(from: String = "", to: String = "")
  object Rewrite {
    implicit val surface: generic.Surface[Rewrite] = generic.deriveSurface[Rewrite]
    implicit val decoder: ConfDecoder[Rewrite] = generic.deriveDecoder(Rewrite())
  }
  case class Config(
    problemRoot: String = "com/histmersion/util/problem/Problem.",
    renames: Map[String, String] = Map.empty,
    rewrites: List[Rewrite] = Nil,
    only: List[String] = Nil,
    skip: List[String] = List("modules/problem/src/main/scala/com/histmersion/util/problem/Problem.scala")
  )
  object Config {
    implicit val surface: generic.Surface[Config] = generic.deriveSurface[Config]
    implicit val decoder: ConfDecoder[Config] = generic.deriveDecoder(Config()).noTypos
  }
}

class ProblemCaseRewrite(config: ProblemCaseRewrite.Config) extends SemanticRule("ProblemCaseRewrite") {
  import ProblemCaseRewrite._
  def this() = this(ProblemCaseRewrite.Config())

  override def withConfiguration(c: Configuration): metaconfig.Configured[Rule] =
    c.conf.getOrElse("ProblemCaseRewrite")(Config()).map(new ProblemCaseRewrite(_))

  private val Root = config.problemRoot

  private def named(sym: String): Option[String] =
    if (!sym.startsWith(Root)) None
    else {
      val parts = sym.stripPrefix(Root).split("[.#]").toList.filter(_.nonEmpty)
      val path = parts.takeWhile(p => p.headOption.exists(_.isUpper) && !p.contains("("))
      val rest = sym.stripPrefix(Root).drop(path.mkString(".").length)
      if (path.isEmpty || (rest.startsWith("#") && rest.length > 1)) None else Some(path.mkString("."))
    }

  private val rewrites: Map[String, String] = config.rewrites.map(r => r.from -> r.to).toMap

  private def qualifierOf(fun: Tree, name: String): Option[String] = {
    val written = fun.syntax.replaceAll("\\s+", "")
    val groupAt = written.lastIndexOf(name)
    if (groupAt < 0) None else Some(written.substring(0, groupAt))
  }

  private def filled(template: String, q: String, args: List[String]): String =
    args.zipWithIndex.foldLeft(template.replace("{q}", q)) { case (t, (a, i)) => t.replace("$" + (i + 1), a) }

  private def caseFun(t: Tree)(implicit doc: SemanticDocument): Option[(String, Tree)] = t match {
    case n: Term.Name => named(n.symbol.value).filter(rewrites.contains).map(_ -> n)
    case s @ Term.Select(_, n) => named(n.symbol.value).filter(rewrites.contains).map(_ -> s)
    case _ => None
  }

  override def fix(implicit doc: SemanticDocument): Patch = {
    val path = doc.input match { case Input.File(p, _) => p.toString; case Input.VirtualFile(p, _) => p; case o => o.toString }
    if (config.only.nonEmpty && !config.only.exists(path.contains)) return Patch.empty
    if (config.skip.exists(path.endsWith)) return Patch.empty
    val done = scala.collection.mutable.Set.empty[Tree]
    val patches = scala.collection.mutable.ListBuffer.empty[Patch]
    def inside(t: Tree): Boolean = done.exists(d => d.pos.start <= t.pos.start && t.pos.end <= d.pos.end)
    def replaceWhole(whole: Tree, from: String, fun: Tree, args: List[Tree]): Unit = {
      val group = from.split('.').head
      qualifierOf(fun, group + "." ) match {
        case Some(q) =>
          patches += Patch.replaceTree(whole, filled(rewrites(from), q, args.map(_.syntax))).atomic
          done += whole
        case None =>
          patches += Patch.lint(Diagnostic("unqualified", s"$from written without its group: rewrite by hand", whole.pos))
          done += whole
      }
    }
    doc.tree.traverse {
      case a: Term.Apply if !inside(a) && caseFun(a.fun).isDefined =>
        val (from, fun) = caseFun(a.fun).get
        replaceWhole(a, from, fun, a.argClause.values)
      case p: Pat.Extract if !inside(p) && caseFun(p.fun).isDefined =>
        val (from, fun) = caseFun(p.fun).get
        replaceWhole(p, from, fun, p.argClause.values)
      case t @ (_: Term.Name | _: Term.Select) if !inside(t) && !t.parent.exists {
            case Term.Select(q, _) => q eq t
            case a: Term.Apply => a.fun eq t
            case p: Pat.Extract => p.fun eq t
            case s @ Term.Select(_, n) => n eq t
            case _ => false
          } && caseFun(t).isDefined && !t.parent.exists { case _: Importee | _: Import => true; case _ => false } =>
        val (from, fun) = caseFun(t).get
        replaceWhole(t, from, fun, Nil)
    }
    doc.tree.traverse {
      case n: Term.Name => named(n.symbol.value).flatMap(config.renames.get).foreach { to =>
        if (!inside(n) && n.value != to) patches += Patch.replaceTree(n, to).atomic
      }
    }
    patches.asPatch
  }
}
