import scalafix.v1._
import scala.meta._

class CreatedAtBind extends SyntacticRule("CreatedAtBind") {
  override def fix(implicit doc: SyntacticDocument): Patch =
    doc.tree.collect {
      case s @ Term.Select(q, Term.Name("iso")) if q.syntax == "at" || q.syntax == "w.at" =>
        Patch.replaceTree(s, q.syntax)
    }.asPatch
}
