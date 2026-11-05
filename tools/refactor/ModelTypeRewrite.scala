import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, Configured, generic}

object ModelTypeRewrite {
  case class Record(owner: String, parameters: List[String], fields: Map[String,String], optionalFields: List[String] = Nil)
  object Record {
    implicit val surface: generic.Surface[Record] = generic.deriveSurface[Record]
    implicit val decoder: ConfDecoder[Record] = generic.deriveDecoder(Record("",Nil,Map.empty)).noTypos
  }
  case class Policy(records: List[Record] = Nil, results: Map[String,String] = Map.empty, optionalResults: List[String] = Nil)
  object Policy {
    implicit val surface: generic.Surface[Policy] = generic.deriveSurface[Policy]
    implicit val decoder: ConfDecoder[Policy] = generic.deriveDecoder(Policy()).noTypos
  }
}

class ModelTypeRewrite(val policy: ModelTypeRewrite.Policy) extends SemanticRule("ModelTypeRewrite") {
  def this() = this(ModelTypeRewrite.Policy())
  import ModelTypeRewrite._
  override def withConfiguration(c: Configuration): Configured[Rule] = c.conf.getOrElse("ModelTypeRewrite")(Policy()).andThen { p =>
    require(p.records.nonEmpty,"Explicit reviewed records required")
    require(p.records.map(_.owner).distinct.size == p.records.size)
    p.records.foreach(r => require(r.owner.endsWith("#") && r.parameters.distinct.size == r.parameters.size && r.fields.keySet.subsetOf(r.parameters.toSet) && r.optionalFields.toSet.subsetOf(r.fields.keySet)))
    require(p.optionalResults.toSet.subsetOf(p.results.keySet))
    Configured.ok(new ModelTypeRewrite(p))
  }
  override def fix(implicit doc: SemanticDocument): Patch = {
    doc.tree.collect { case c: Defn.Class =>
      policy.records.find(_.owner == c.name.symbol.value).foreach { r =>
        require(c.ctor.paramClauses.size == 1 && c.ctor.paramClauses.head.values.map(_.name.value) == r.parameters,
          "Reviewed constructor parameter order changed: " + r.owner)
      }
    }
    val fields = policy.records.flatMap(r => r.fields.map { case(n,t) => (r.owner + n + ".") -> t }).toMap
    val optionalSymbols = policy.records.flatMap(r => r.optionalFields.map(r.owner + _ + ".")).toSet ++ policy.optionalResults
    def base(t: Type): Boolean = t.symbol.value == "scala/Int#"
    def change(t: Type, replacement: String, optional: Boolean): Patch = {
      val leaf = if(optional) t match {
        case a: Type.Apply if a.tpe.symbol.value == "scala/Option#" && a.argClause.values.size == 1 => a.argClause.values.head
        case _ => sys.error("Expected explicit Option[Int]: " + t.structure)
      } else t
      require(base(leaf),"Unsupported type shape for reviewed symbol: " + t.structure)
      Patch.replaceTree(leaf,replacement)
    }
    def resultSymbol(t: Term): String = t match {
      case a: Term.Apply => resultSymbol(a.fun)
      case _ => t.symbol.value
    }
    def wrap(t: Term, replacement: String, optional: Boolean): Patch = {
      val symbol = resultSymbol(t)
      if((fields.get(symbol).contains(replacement) || policy.results.get(symbol).contains(replacement)) && optionalSymbols(symbol) == optional) Patch.empty
      else if(optional && t.symbol.value == "scala/None.") Patch.empty
      else if(optional) Patch.addLeft(t,"(") + Patch.addRight(t,").map(" + replacement + ".apply)")
      else Patch.addLeft(t,replacement + "(") + Patch.addRight(t,")")
    }
    val declarations = doc.tree.collect {
      case p: Term.Param if fields.contains(p.name.symbol.value) =>
        change(p.decltpe.getOrElse(sys.error("Missing explicit model field type")),fields(p.name.symbol.value),optionalSymbols(p.name.symbol.value)) +
          p.default.map(wrap(_,fields(p.name.symbol.value),optionalSymbols(p.name.symbol.value))).getOrElse(Patch.empty)
      case d: Defn.Def if policy.results.contains(d.name.symbol.value) =>
        change(d.decltpe.getOrElse(sys.error("Missing explicit result type")),policy.results(d.name.symbol.value),optionalSymbols(d.name.symbol.value)) +
          wrap(d.body,policy.results(d.name.symbol.value),optionalSymbols(d.name.symbol.value))
    }
    val calls = doc.tree.collect { case a: Term.Apply =>
      val target = a.fun.symbol.value
      val record = policy.records.find(r => target == r.owner.stripSuffix("#") + "." ||
        target == r.owner.stripSuffix("#") + ".apply()." || target == r.owner + "copy().")
      record.map { r =>
        require(a.argClause.values.size <= r.parameters.size,"Unexpected argument arity")
        a.argClause.values.zipWithIndex.map { case(arg,index) =>
          val (name,value) = arg match {
            case Term.Assign(n: Term.Name,v: Term) => (n.value,v)
            case v => (r.parameters(index),v)
          }
          require(r.parameters.contains(name),"Unknown named argument for reviewed record: " + name)
          r.fields.get(name).map(wrap(value,_,r.optionalFields.contains(name))).getOrElse(Patch.empty)
        }.asPatch
      }.getOrElse(Patch.empty)
    }
    (declarations ++ calls).asPatch.atomic
  }
}
