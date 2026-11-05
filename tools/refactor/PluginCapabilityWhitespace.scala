import scalafix.v1._
import scala.meta._

class PluginCapabilityWhitespace extends SyntacticRule("PluginCapabilityWhitespace") {
  override def fix(implicit doc: SyntacticDocument): Patch = {
    if(!doc.input.syntax.endsWith("/plugins/vaults/Subprocess.scala")) return Patch.empty
    val emptyLines = doc.tree.tokens.groupBy(_.pos.startLine).values.filter(_.forall(t => t.is[Token.Space] || t.is[Token.LF]))
    Patch.removeTokens(emptyLines.flatten.filter(_.is[Token.Space]).toList)
  }
}
