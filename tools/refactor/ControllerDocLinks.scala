import scalafix.v1._
import scala.meta._

class ControllerDocLinks extends SyntacticRule("ControllerDocLinks") {
  private val names = List("AgentsHandlers" -> "AgentsControllers", "ManagersHandlers" -> "ManagersControllers")
  override def fix(implicit doc: SyntacticDocument): Patch = doc.tokens.collect {
    case comment: Token.Comment =>
      val updated = names.foldLeft(comment.text) { case (text, (from, to)) =>
        text.replace("[[" + from + ".", "[[" + to + ".")
          .replace("[[" + from + "]]", "[[" + to + "]]")
          .replace("`" + from + "(", "`" + to + "(")
          .replace("`" + from + ".", "`" + to + ".")
      }
      if (updated == comment.text) Patch.empty else Patch.replaceToken(comment, updated).atomic
  }.asPatch
}
