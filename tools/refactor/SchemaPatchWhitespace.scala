import scalafix.v1._
import scala.meta._

class SchemaPatchWhitespace extends SyntacticRule("SchemaPatchWhitespace") {
  override def fix(implicit doc: SyntacticDocument): Patch = {
    val migrated = doc.tree.collect { case c: Defn.Class => c.templ.derives }.flatten.exists {
      case Type.Select(_, n) => n.value == "Schema"
      case _ => false
    }
    if (!migrated) Patch.empty
    else {
      val tokens = doc.tokens.toList
      def horizontal(t: Token) = t.is[Token.Space] || t.is[Token.Tab]
      def whitespace(t: Token) = horizontal(t) || t.is[Token.LF] || t.is[Token.CR]
      val trailing = tokens.zipWithIndex.collect {
        case (t, i) if horizontal(t) && tokens.drop(i + 1).dropWhile(horizontal).headOption.exists(n => n.is[Token.LF] || n.is[Token.CR] || n.is[Token.EOF]) => t
      }
      val end = tokens.dropRight(1).reverse.takeWhile(whitespace).reverse
      val extraEnd = end.dropWhile(horizontal).drop(1)
      Patch.removeTokens((trailing ++ extraEnd).distinct).atomic
    }
  }
}
