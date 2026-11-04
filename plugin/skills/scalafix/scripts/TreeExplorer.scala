import scalafix.v1._
import scala.meta._

import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters._
import scala.util.matching.Regex

/** A read-only rule that reports how real occurrences of a name look as trees. Run it with explore.sh.
  *
  * Configured with environment variables:
  *   EXPLORE_NAME     regex for the name to look for, e.g. `mapValues|filterKeys` (required)
  *   EXPLORE_SYMBOL   regex the SemanticDB symbol must contain, e.g. `^scala/collection/MapOps#` (optional)
  *   EXPLORE_UP       how many enclosing call/select/for nodes to include as context (default 4)
  *   EXPLORE_EXAMPLES how many full examples to print per shape (default 1)
  */
class TreeExplorer extends SemanticRule("TreeExplorer") {
  private val namePattern: Regex = sys.env.getOrElse("EXPLORE_NAME", "").r
  private val symbolPattern: Option[Regex] = sys.env.get("EXPLORE_SYMBOL").filter(_.nonEmpty).map(_.r)
  private val maxUp: Int = sys.env.get("EXPLORE_UP").map(_.toInt).getOrElse(4)
  private val examples: Int = sys.env.get("EXPLORE_EXAMPLES").map(_.toInt).getOrElse(1)

  private final case class Occurrence(symbol: String, shape: String, location: String, details: String)
  private val found = new ConcurrentLinkedQueue[Occurrence]()

  override def fix(implicit doc: SemanticDocument): Patch = {
    if (namePattern.regex.isEmpty) sys.error("set EXPLORE_NAME")
    doc.tree.traverse {
      case name: Name if !isDefinition(name) && namePattern.pattern.matcher(name.value).matches() =>
        val sym = name.symbol
        if (symbolPattern.forall(_.findFirstIn(sym.value).isDefined)) found.add(occurrence(name, sym))
    }
    Patch.empty
  }

  override def afterComplete(): Unit = {
    val groups = found.asScala.toList.groupBy(o => (o.symbol, o.shape)).toList.sortBy { case (k, os) => (-os.size, k) }
    println(s"== TreeExplorer: ${found.size} occurrence(s) of /${namePattern.regex}/ in ${groups.size} shape(s)")
    groups.zipWithIndex.foreach { case (((symbol, shape), os), i) =>
      println()
      println(s"=== Shape ${i + 1}: ${os.size} occurrence(s)")
      println(s"symbol: ${if (symbol.isEmpty) "<none: no SemanticDB info, or not a reference>" else symbol}")
      println(s"shape:  $shape")
      println(s"at:     ${os.map(_.location).sorted.mkString(", ")}")
      os.sortBy(_.location).take(examples).foreach(o => println(o.details))
    }
  }

  private def occurrence(name: Name, sym: Symbol)(implicit doc: SemanticDocument): Occurrence = {
    val context = enclosing(name)
    val path = ancestors(name).takeWhile(_ ne context).toSet + context + name
    val location = s"${relative(doc.input)}:${name.pos.startLine + 1}"
    val details = new StringBuilder
    details ++= s"--- example at $location\n"
    details ++= indent(context.syntax, "  code: ") + "\n"
    details ++= indent(pretty(context), "  tree: ") + "\n"
    val names = context.collect { case n: Term.Name => n; case n: Type.Name => n }
    names.map(n => (n.value, n.symbol)).distinct.foreach { case (value, s) =>
      if (!s.isNone) {
        val info = s.info.map(_.signature.toString).getOrElse("?")
        details ++= s"  symbol $value: ${s.value}  --  $info\n"
      }
    }
    context.collect { case t: Term => t }.foreach { t =>
      val synthetics = scala.util.Try(t.synthetics).getOrElse(Nil)
      if (synthetics.nonEmpty) details ++= s"  synthetic for `${oneLine(t.syntax)}`: ${synthetics.mkString(", ")}\n"
    }
    Occurrence(sym.value, shape(context, path, name), location, details.result().stripSuffix("\n"))
  }

  private def relative(input: Input): String = {
    val path = input match {
      case Input.File(path, _) => path.toString
      case Input.VirtualFile(path, _) => path
      case other => other.toString
    }
    sys.env.get("EXPLORE_ROOT").map(_.stripSuffix("/") + "/").filter(path.startsWith).fold(path)(path.stripPrefix)
  }

  private def ancestors(t: Tree): List[Tree] = t.parent.toList.flatMap(p => p :: ancestors(p))

  private def enclosing(name: Name): Tree = {
    def isContext(t: Tree): Boolean = t match {
      case _: Term.Select | _: Term.Apply | _: Term.ApplyType | _: Term.ApplyInfix | _: Term.ApplyUnary |
          _: Term.ArgClause | _: Type.ArgClause | _: Term.For | _: Term.ForYield | _: Term.EnumeratorsBlock |
          _: Enumerator | _: Term.Ascribe | _: Term.Eta | _: Type.Select | _: Type.Apply =>
        true
      case _ => false
    }
    def counts(t: Tree): Boolean = !t.is[Term.ArgClause] && !t.is[Type.ArgClause] && !t.is[Term.EnumeratorsBlock]
    @annotation.tailrec
    def loop(t: Tree, left: Int): Tree = t.parent match {
      case Some(p) if isContext(p) && (left > 0 || !counts(p)) => loop(p, if (counts(p)) left - 1 else left)
      case _ => t
    }
    loop(name, maxUp)
  }

  /** Unrelated subtrees are abstracted away, so occurrences group by the parts that decide the rewrite. */
  private def shape(t: Tree, path: Set[Tree], name: Name): String = t match {
    case n: Name if n eq name => s"""${n.productPrefix}("${n.value}")"""
    case n: Name if isMemberName(n) => s"""${n.productPrefix}("${n.value}")"""
    case a: Term.ArgClause if !path.contains(t) =>
      // the kind of each argument matters: a lambda, a partial function and a value need different rewrites
      s"Term.ArgClause(${a.values.map(v => s"<${v.productPrefix}>").mkString(", ")})"
    case _ if !path.contains(t) => s"<${t.productPrefix}>"
    case _ => s"${t.productPrefix}(${t.children.map(shape(_, path, name)).mkString(", ")})"
  }

  private def isDefinition(n: Name): Boolean = n.is[Name.Anonymous] || n.parent.exists {
    case m: Member => m.name eq n
    case _ => false
  }

  private def isMemberName(n: Name): Boolean = n.parent.exists {
    case s: Term.Select => s.name eq n
    case s: Type.Select => s.name eq n
    case i: Term.ApplyInfix => i.op eq n
    case _ => false
  }

  private def pretty(t: Tree, depth: Int = 0): String = {
    // Recent Scalameta versions print `structure` over many lines; join it and break it only where it's too long.
    val flat = t.structure.replaceAll("\\(\\s*\n\\s*", "(").replaceAll(",\\s*\n\\s*", ", ").replaceAll("\\s*\n\\s*\\)", ")")
    if (flat.length + depth * 2 <= 100 || t.children.isEmpty) flat
    else {
      val pad = "  " * (depth + 1)
      t.children.map(c => pad + pretty(c, depth + 1)).mkString(s"${t.productPrefix}(\n", ",\n", ")")
    }
  }

  private def oneLine(s: String): String = {
    val l = s.replaceAll("\\s*\n\\s*", " ")
    if (l.length > 60) l.take(57) + "..." else l
  }

  private def indent(s: String, first: String): String =
    s.linesIterator.zipWithIndex.map { case (l, i) => (if (i == 0) first else " " * first.length) + l }.mkString("\n")
}
