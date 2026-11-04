import scalafix.v1._
import scala.meta._
import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets.UTF_8
import scala.jdk.CollectionConverters._

class ConsoleTests extends SemanticRule("ConsoleTests") {
  private val selected = Files.readAllLines(Paths.get("refactor-audit/console-plan.tsv"), UTF_8).asScala.drop(1).map(_.split("\t")(0)).toSet
  private val helpers = Set("write", "writeLine", "errorWrite", "errorLine", "errorFlush", "processErrorWrite", "processErrorLine", "processErrorFlush")
  override def fix(implicit doc: SemanticDocument): Patch = {
    val path = doc.input.toString
    if (!path.contains("/src/test/")) return Patch.empty
    val patches = scala.collection.mutable.ListBuffer.empty[Patch]
    doc.tree.collect {
      case c: Defn.Class if c.collect { case n: Term.Name if selected(n.symbol.value) => n }.nonEmpty =>
        patches += Patch.addLeft(c.templ.stats.head, "private given console: com.histmersion.util.console.Console = com.histmersion.util.console.Console.standard\n\n  ")
      case n: Term.Name if n.symbol.value.startsWith("com/histmersion/cli/Cli.") && helpers(n.value) =>
        val t: Tree = n.parent.collect { case s: Term.Select if s.name eq n => s }.getOrElse(n)
        patches += Patch.replaceTree(t, "summon[com.histmersion.util.console.Console]." + n.value.replace("processError", "error"))
    }
    patches.toList.asPatch
  }
}
