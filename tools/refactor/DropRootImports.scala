import scalafix.v1._
import scala.meta._

class DropRootImports extends SyntacticRule("DropRootImports") {

  private def rootChained(tree: Tree): Boolean = tree match {
    case Source(List(Pkg(Term.Select(Term.Name("com"), Term.Name("histmersion")), _), _*)) => true
    case _                                                                               => false
  }

  private def root(ref: Term.Ref): Boolean = ref match {
    case Term.Select(Term.Name("com"), Term.Name("histmersion")) => true
    case _                                                       => false
  }

  private def isType(i: Importee): Boolean = i match {
    case Importee.Name(Name(n)) => n.headOption.exists(_.isUpper)
    case _                      => false
  }

  override def fix(implicit doc: SyntacticDocument): Patch =
    if (!rootChained(doc.tree)) Patch.empty
    else
      doc.tree.collect {
        case imp @ Import(List(Importer(ref, importees))) if root(ref) && importees.forall(isType) =>
          Patch.removeTokens(imp.tokens) + trailingNewline(imp)
        case Importer(ref, importees) if root(ref) && importees.exists(isType) && !importees.forall(isType) =>
          Patch.fromIterable(importees.filter(isType).map(Patch.removeImportee))
      }.asPatch

  private def trailingNewline(imp: Import)(implicit doc: SyntacticDocument): Patch =
    doc.tree.tokens
      .dropWhile(_.start < imp.pos.end)
      .headOption
      .collect { case nl if nl.is[Token.LF] => Patch.removeToken(nl) }
      .getOrElse(Patch.empty)
}
