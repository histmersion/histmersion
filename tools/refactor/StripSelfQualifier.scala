import scalafix.v1._
import scala.meta._

class StripSelfQualifier extends SemanticRule("StripSelfQualifier") {

  private def objectsAround(t: Tree): List[Defn.Object] = t.parent match {
    case Some(o: Defn.Object) => o :: objectsAround(o)
    case Some(p) => objectsAround(p)
    case None => Nil
  }

  private def templatesBetween(t: Tree, stop: Defn.Object): List[Template] = t.parent match {
    case Some(o) if o eq stop => Nil
    case Some(tpl: Template) if tpl.parent.exists(_ eq stop) => Nil
    case Some(tpl: Template) => tpl :: templatesBetween(tpl, stop)
    case Some(p) => templatesBetween(p, stop)
    case None => Nil
  }

  private def defines(tpl: Template, name: String): Boolean = tpl.body.stats.exists {
    case d: Defn.Def => d.name.value == name
    case d: Defn.Val => d.pats.exists { case Pat.Var(n) => n.value == name; case _ => false }
    case d: Defn.Type => d.name.value == name
    case d: Defn.Object => d.name.value == name
    case d: Defn.Class => d.name.value == name
    case d: Defn.Trait => d.name.value == name
    case d: Defn.Enum => d.name.value == name
    case e: Export => e.importers.exists(_.importees.exists {
        case Importee.Name(n) => n.value == name
        case Importee.Rename(_, r) => r.value == name
        case _: Importee.Wildcard => true
        case _ => false
      })
    case _ => false
  }

  private def inherits(tpl: Template, name: String)(implicit doc: SemanticDocument): Boolean =
    tpl.inits.exists(i => hasMember(i.tpe.symbol, name, 8))

  private def hasMember(sym: Symbol, name: String, depth: Int)(implicit doc: SemanticDocument): Boolean =
    depth > 0 && !sym.isNone && sym.info.exists(_.signature match {
      case ClassSignature(_, parents, _, decls) =>
        decls.exists(_.displayName == name) || parents.exists {
          case TypeRef(_, parent, _) => hasMember(parent, name, depth - 1)
          case _ => false
        }
      case _ => false
    })

  private def inImport(t: Tree): Boolean = t.parent match {
    case Some(_: Importer) | Some(_: Import) | Some(_: Export) => true
    case Some(p) => inImport(p)
    case None => false
  }

  private def params(t: Tree, name: String): Boolean = t.parent match {
    case Some(d: Defn.Def) if d.paramClauseGroups.flatMap(_.paramClauses).flatMap(_.values).exists(_.name.value == name) => true
    case Some(d: Defn.Def) if d.name.value == name => true
    case Some(f: Term.Function) if f.paramClause.values.exists(_.name.value == name) => true
    case Some(b: Term.Block) if b.stats.exists {
          case d: Defn.Val => d.pats.exists(_.collect { case Pat.Var(n) if n.value == name => n }.nonEmpty)
          case d: Defn.Var => d.pats.exists(_.collect { case Pat.Var(n) if n.value == name => n }.nonEmpty)
          case d: Defn.Def => d.name.value == name
          case _ => false
        } => true
    case Some(c: Case) if c.pat.collect { case Pat.Var(n) if n.value == name => n }.nonEmpty => true
    case Some(e: Enumerator) if e.collect { case Pat.Var(n) if n.value == name => n }.nonEmpty => true
    case Some(p) => params(p, name)
    case None => false
  }

  override def fix(implicit doc: SemanticDocument): Patch = doc.tree.collect {
    case sel @ Term.Select(q: Term.Name, n) if !inImport(sel) && strip(sel, q, n.value) => Patch.replaceTree(sel, n.value)
    case sel @ Type.Select(q: Term.Name, n) if !inImport(sel) && strip(sel, q, n.value) => Patch.replaceTree(sel, n.value)
  }.asPatch

  private def imported(member: String)(implicit doc: SemanticDocument): Boolean = doc.tree.collect {
    case i: Importee.Name if i.name.value == member => i
    case i: Importee.Rename if i.rename.value == member => i
  }.nonEmpty

  private def strip(sel: Tree, q: Term.Name, member: String)(implicit doc: SemanticDocument): Boolean =
    member != "T" && !q.symbol.isNone && {
      val owner = objectsAround(sel).find(o => o.name.symbol == q.symbol)
      owner.exists { o =>
        !templatesBetween(sel, o).exists(t => defines(t, member) || inherits(t, member)) && !params(sel, member) &&
          defines(o.templ, member) && !imported(member)
      }
    }
}
