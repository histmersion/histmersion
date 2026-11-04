import scalafix.v1._
import scala.meta._

class ConsoleTerminal extends SyntacticRule("ConsoleTerminal") {
  override def fix(implicit doc: SyntacticDocument): Patch = {
    val path = doc.input match { case Input.File(p, _) => p.toString; case Input.VirtualFile(p, _) => p; case x => x.toString }
    val patches = scala.collection.mutable.ListBuffer.empty[Patch]
    if (path.endsWith("/util/console/Console.scala")) doc.tree.collect {
      case c: Defn.Class if c.name.value == "Streams" && !c.ctor.paramClauses.flatMap(_.values).exists(_.name.value == "terminal") =>
        patches += Patch.addRight(c.ctor.paramClauses.last.values.last, ", terminal: String => Option[String] = _ => None")
      case d: Defn.Def if d.name.value == "system" && !d.body.syntax.contains("System.console") =>
        patches += Patch.replaceTree(d.body, """Streams(() => System.out, () => System.err, () => scala.io.StdIn.readLine(), prompt => Option(System.console()).flatMap(c => Option(c.readLine(prompt))))""")
      case d: Defn.Def if d.name.value == "streams" && !d.body.syntax.contains("err.print") =>
        patches += Patch.replaceTree(d.body, """Streams(() => out, () => err, input, prompt => { err.print(prompt); err.flush(); Option(input()) })""")
      case d: Defn.Def if d.name.value == "readTerminalLine" && d.body.syntax.contains("System.console") =>
        patches += Patch.replaceTree(d.body, "terminal(prompt)")
      case t: Defn.Trait if t.name.value == "Console" && !t.templ.stats.exists(_.syntax.contains("def readTerminalLine")) =>
        patches += Patch.addLeft(t.templ.stats.head, "def readTerminalLine(prompt: String): Option[String] = None\n  ")
      case c: Defn.Class if c.name.value == "Streams" && !c.templ.stats.exists(_.syntax.contains("def readTerminalLine")) =>
        patches += Patch.addLeft(c.templ.stats.head, "override def readTerminalLine(prompt: String): Option[String] =\n      Option(System.console()).flatMap(c => Option(c.readLine(prompt)))\n    ")
    }
    if (path.endsWith("/cli/PluginOps.scala")) doc.tree.collect {
      case d: Defn.Def if d.name.value == "ask" && d.body.syntax.contains("System.console") =>
        patches += Patch.replaceTree(d, """def ask(question: String)(using console: Console): Boolean =
    console.readTerminalLine(s"$question [y/N] ").map(_.trim.toLowerCase).exists(a => a == "y" || a == "yes")""")
    }
    patches.toList.asPatch
  }
}
