import scalafix.v1._
import scala.meta._

class ConsolePolish extends SyntacticRule("ConsolePolish") {
  private implicit val dialect: Dialect = dialects.Scala3
  override def fix(implicit doc: SyntacticDocument): Patch = {
    val path = doc.input match { case Input.File(p, _) => p.toString; case Input.VirtualFile(p, _) => p; case x => x.toString }
    if (!path.contains("/src/main/") || path.contains("/console/src/")) return Patch.empty
    val patches = scala.collection.mutable.ListBuffer.empty[Patch]
    var needsImport = false
    doc.tree.collect {
      case t: Type.Select if t.syntax == "com.histmersion.util.console.Console" =>
        needsImport = true; patches += Patch.replaceTree(t, "Console")
      case t: Term.Select if t.syntax == "com.histmersion.util.console.Console" =>
        needsImport = true; patches += Patch.replaceTree(t, "Console")
      case t: Term.Select if Set("Cli.out.println", "Cli.err.println")(t.syntax) && t.parent.exists(_.is[Term.ArgClause]) =>
        patches += Patch.replaceTree(t, "value => " + t.syntax + "(value)")
      case o: Defn.Object if path.endsWith("/cli/Cli.scala") && o.name.value == "Cli" =>
        val colon = o.tokens.find(_.is[Token.Colon]).get
        val first = o.templ.stats.head
        val between = doc.tokens.filter(t => t.pos.start >= colon.pos.end && t.pos.end <= first.pos.start)
        if (between.forall(_.is[Token.Whitespace])) {
          patches += Patch.removeTokens(between)
          patches += Patch.addLeft(first, "\n\n  ")
        }
    }
    if (needsImport) patches += Patch.addGlobalImport("com.histmersion.util.console.Console".parse[Importer].get)
    patches.toList.asPatch
  }
}
