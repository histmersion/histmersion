import scalafix.v1._
import scala.meta._

class TestNewtypeObservations extends SemanticRule("TestNewtypeObservations") {
  override def fix(implicit doc: SemanticDocument): Patch = {
    require(doc.input.toString.contains("/src/test/"),"Test observations only")
    val owners = List("library/SearchResult", "library/GeoHitPage", "automation/Goal.List", "catalogue/Attestation.List", "catalogue/EventList", "media/AssetLink.List", "media/MediaList", "research/Article.List", "OffsetPage")
    val fields = owners.flatMap(o => List("total","shown","offset","next").map(n => ("com/histmersion/api/" + o + "#" + n + ".") -> (n == "next"))).toMap
    val aliases = doc.tree.collect {
      case v: Defn.Val if v.pats.size == 1 && v.pats.head.isInstanceOf[Pat.Var] && fields.contains(v.rhs.symbol.value) =>
        v.pats.head.asInstanceOf[Pat.Var].name.symbol.value -> fields(v.rhs.symbol.value)
    }.toMap
    val known = fields ++ aliases
    def parents(t: Tree): List[Tree] = t.parent.toList.flatMap(p => p :: parents(p))
    def boundary(t: Term): Boolean = parents(t).exists {
      case a: Term.Apply if Set("assertEquals","assert")(a.fun.syntax) => a.argClause.values.headOption.exists(v => v.pos.start <= t.pos.start && v.pos.end >= t.pos.end)
      case _: Term.Interpolate => true
      case a: Term.ApplyInfix if Set("-", ">=", ">", "<=", "<")(a.op.value) => true
      case _ => false
    }
    def covered(t: Term): Boolean = t.parent.exists {
      case s: Term.Select if s.qual eq t => s.name.value == "value"
      case s: Term.Select => s.name eq t
      case _: Pat.Var => true
      case _ => false
    }
    doc.tree.collect {
      case t: Term if (t.isInstanceOf[Term.Select] || t.isInstanceOf[Term.Name]) && known.contains(t.symbol.value) && boundary(t) && !covered(t) =>
        Patch.replaceTree(t,if(known(t.symbol.value)) t.syntax + ".map(n => (n.value: Int))" else "(" + t.syntax + ".value: Int)")
    }.asPatch.atomic
  }
}
