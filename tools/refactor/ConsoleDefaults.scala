import scalafix.v1._
import scala.meta._

class ConsoleDefaults extends SyntacticRule("ConsoleDefaults") {
  override def fix(implicit doc: SyntacticDocument): Patch = {
    val path = doc.input match { case Input.File(p, _) => p.toString; case Input.VirtualFile(p, _) => p; case x => x.toString }
    val patches = scala.collection.mutable.ListBuffer.empty[Patch]
    doc.tree.collect {
      case d: Defn.Def if path.contains("/cli/src/main/") =>
        val clauses = d.paramClauseGroups.flatMap(_.paramClauses)
        val console = clauses.find(c => c.values.exists(p => p.name.value == "console" && p.decltpe.exists(_.syntax == "com.histmersion.util.console.Console")))
        if (clauses.exists(_.values.exists(_.default.nonEmpty)) && console.nonEmpty && clauses.head != console.get) {
          patches += Patch.removeTokens(console.get.tokens)
          patches += Patch.addLeft(clauses.head, console.get.pos.text)
        }
      case a: Term.Apply if path.endsWith("/cli/Cli.scala") && a.fun.syntax == "console.errorLine" && a.argClause.values.map(_.syntax) == List("help") =>
        patches += Patch.replaceTree(a.argClause.values.head, "help.toString")
      case c: Defn.Class if path.endsWith("/cli/PluginOps.scala") && c.name.value == "PluginOps" =>
        val clauses = c.ctor.paramClauses
        if (!clauses.exists(_.values.exists(_.name.value == "console"))) patches += Patch.addLeft(clauses.head, "(using console: com.histmersion.util.console.Console)")
        val params = clauses.flatMap(_.values)
        params.find(_.name.value == "out").foreach { p =>
          val next = params(params.indexOf(p) + 1)
          patches += Patch.removeTokens(doc.tokens.filter(t => t.pos.start >= p.pos.start && t.pos.end <= next.pos.start))
        }
      case n: Term.Name if path.endsWith("/cli/PluginOps.scala") && n.value == "out" && n.parent.exists { case a: Term.Apply => a.fun eq n; case c: Term.ArgClause => true; case _ => false } =>
        patches += Patch.replaceTree(n, "console.writeLine")
    }
    patches.toList.asPatch
  }
}
