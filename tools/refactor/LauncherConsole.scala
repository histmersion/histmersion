import scalafix.v1._
import scala.meta._

class LauncherConsole extends SyntacticRule("LauncherConsole") {
  private implicit val dialect: Dialect = dialects.Scala3
  private val consoleType = "com.histmersion.util.console.Console"
  private val helper = """private def consoleFor(out: String => Unit, err: String => Unit): com.histmersion.util.console.Console = new com.histmersion.util.console.Console {
    def write(value: Any): Unit = out(String.valueOf(value))
    def writeLine(value: Any): Unit = out(String.valueOf(value))
    def writeLine(): Unit = out("")
    def errorWrite(value: => String): Unit = err(value)
    def errorLine(value: => String): Unit = err(value)
    def errorLine(): Unit = err("")
    def errorFlush(): Unit = ()
    def readLine(): String = throw new UnsupportedOperationException("input not used")
  }"""
  helper.parse[Stat].get
  override def fix(implicit doc: SyntacticDocument): Patch = {
    val path = doc.input match { case Input.File(p, _) => p.toString; case Input.VirtualFile(p, _) => p; case x => x.toString }
    val resolver = path.endsWith("/pluginresolver/DependencyResolver.scala")
    val relink = path.endsWith("/plugins/RelinkPlugins.scala")
    val tests = path.endsWith("/pluginresolver/PluginResolverSuite.scala") || path.endsWith("/plugins/PluginToolSuite.scala") || path.endsWith("/cli/PluginCmdSuite.scala")
    if (!(resolver || relink || tests)) return Patch.empty
    val patches = scala.collection.mutable.ListBuffer.empty[Patch]
    if (tests) {
      doc.tree.collect { case c: Defn.Class if c.name.value.endsWith("Suite") =>
        if (!c.templ.stats.exists { case d: Defn.Def => d.name.value == "consoleFor"; case _ => false })
          patches += Patch.addLeft(c.templ.stats.head, helper + "\n\n  ")
      }
      doc.tree.collect {
        case a: Term.Apply if a.fun.syntax == "PluginOps" && a.argClause.values.exists { case Term.Assign(Term.Name("out"), _) => true; case _ => false } =>
          val callback = a.argClause.values.collectFirst { case Term.Assign(Term.Name("out"), rhs) => rhs }.get
          val rest = a.argClause.values.filterNot { case Term.Assign(Term.Name("out"), _) => true; case _ => false }
          patches += Patch.replaceTree(a, "PluginOps(using consoleFor(" + callback.pos.text + ", _ => ()))(" + rest.map(_.pos.text).mkString(", ") + ")")
        case a: Term.Apply if a.fun.is[Term.Select] && Set("DependencyResolver", "RelinkPlugins")(a.fun.asInstanceOf[Term.Select].qual.syntax) && Set("exitCode", "resolve", "run")(a.fun.asInstanceOf[Term.Select].name.value) && a.argClause.values.size >= 3 =>
          val args = a.argClause.values
          val pair = args.takeRight(2)
          patches += Patch.replaceTree(a, a.fun.pos.text + "(" + (args.dropRight(2).map(_.pos.text) :+ ("consoleFor(" + pair.map(_.pos.text).mkString(", ") + ")")).mkString(", ") + ")")
      }
    } else {
      doc.tree.collect {
        case d: Defn.Def =>
          val params = d.paramClauseGroups.flatMap(_.paramClauses).flatMap(_.values)
          val output = params.filter(p => Set("out", "stdout", "err", "stderr")(p.name.value) && p.decltpe.exists(_.is[Type.Function]))
          if (output.size == 2) {
            val from = output.head.pos.start
            val until = output.last.pos.end
            val tokens = doc.tokens.filter(t => t.pos.start >= from && t.pos.end <= until)
            patches += Patch.addLeft(tokens.head, "console: " + consoleType) + Patch.removeTokens(tokens)
          }
      }
      doc.tree.collect {
        case a: Term.Apply if a.argClause.values.size >= 2 && a.argClause.values.takeRight(2).map(_.syntax) == (if (resolver) List("out", "err") else List("stdout", "stderr")) =>
          val pair = a.argClause.values.takeRight(2)
          val tokens = doc.tokens.filter(t => t.pos.start >= pair.head.pos.start && t.pos.end <= pair.last.pos.end)
          patches += Patch.addLeft(tokens.head, "console") + Patch.removeTokens(tokens)
        case a: Term.Apply if a.fun.syntax == (if (resolver) "DependencyResolver.exitCode" else "RelinkPlugins.exitCode") && a.argClause.values.size == 3 =>
          patches += Patch.replaceTree(a, a.fun.syntax + "(" + a.argClause.values.head.pos.text + ", " + consoleType + ".system)")
        case n: Term.Name if Set("out", "err", "stdout", "stderr")(n.value) && !n.parent.exists(_.is[Term.Param]) =>
          val inCallPair = n.parent.exists { case c: Term.ArgClause => c.values.takeRight(2).map(_.syntax) == (if (resolver) List("out", "err") else List("stdout", "stderr")); case _ => false }
          val callable = n.parent.exists { case a: Term.Apply => a.fun eq n; case c: Term.ArgClause => resolver && n.value == "out"; case _ => false }
          if (!inCallPair && callable) patches += Patch.replaceTree(n, if (Set("out", "stdout")(n.value)) "console.writeLine" else "console.errorLine")
      }
    }
    patches.toList.asPatch
  }
}
