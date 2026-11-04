import scalafix.v1._
import scala.meta._

class ConsoleUsing extends SyntacticRule("ConsoleUsing") {
  override def fix(implicit doc: SyntacticDocument): Patch = {
    val path = doc.input match { case Input.File(p, _) => p.toString; case Input.VirtualFile(p, _) => p; case x => x.toString }
    val production = path.endsWith("/pluginresolver/DependencyResolver.scala") || path.endsWith("/plugins/RelinkPlugins.scala")
    val tests = path.endsWith("/pluginresolver/PluginResolverSuite.scala") || path.endsWith("/plugins/PluginToolSuite.scala")
    val patches = scala.collection.mutable.ListBuffer.empty[Patch]
    if (path.contains("/src/test/")) doc.tree.collect {
      case d: Defn.Def if d.name.value == "consoleFor" =>
        d.collect { case n: Term.Name if Set("out", "err")(n.value) => patches += Patch.replaceTree(n, if (n.value == "out") "stdout" else "stderr") }
    }
    if (path.endsWith("/util/console/Console.scala")) doc.tree.collect {
      case c: Defn.Class if c.name.value == "Streams" =>
        c.collect { case n: Term.Name if Set("out", "err")(n.value) => patches += Patch.replaceTree(n, if (n.value == "out") "stdout" else "stderr") }
    }
    if (production) doc.tree.collect {
      case d: Defn.Def =>
        d.paramClauseGroups.flatMap(_.paramClauses).filter(_.values.lastOption.exists(_.name.value == "console")).filter(_.values.size > 1).foreach { c =>
          val before = c.values.dropRight(1).last
          val p = c.values.last
          patches += Patch.removeTokens(doc.tokens.filter(t => t.pos.start >= before.pos.end && t.pos.end <= p.pos.end))
          patches += Patch.addRight(c, "(using " + p.pos.text + ")")
        }
    }
    if (production || tests) doc.tree.collect {
      case a: Term.Apply if a.argClause.values.size >= 2 && a.argClause.values.lastOption.exists(v => v.syntax == "console" || v.syntax == "com.histmersion.util.console.Console.system" || (tests && v.syntax.startsWith("consoleFor("))) =>
        val args = a.argClause.values
        val v = args.last
        patches += Patch.removeTokens(doc.tokens.filter(t => t.pos.start >= args(args.size - 2).pos.end && t.pos.end <= v.pos.end))
        patches += Patch.addRight(a, "(using " + v.pos.text + ")")
    }
    patches.toList.asPatch
  }
}
