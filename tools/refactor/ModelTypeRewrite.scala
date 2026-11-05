import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, Configured, generic}

object ModelTypeRewrite {
  case class Record(owner: String, parameters: List[String], fields: Map[String,String])
  object Record {
    implicit val surface: generic.Surface[Record] = generic.deriveSurface[Record]
    implicit val decoder: ConfDecoder[Record] = generic.deriveDecoder(Record("",Nil,Map.empty)).noTypos
  }
  case class Policy(records: List[Record] = Nil, results: Map[String,String] = Map.empty)
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
    p.records.foreach(r => require(r.owner.endsWith("#") && r.parameters.distinct.size == r.parameters.size && r.fields.keySet.subsetOf(r.parameters.toSet)))
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
    def base(t: Type): Boolean = t.symbol.value == "scala/Int#"
    def change(t: Type, replacement: String): Patch = {
      require(base(t),"Unsupported type shape for reviewed symbol: " + t.structure)
      Patch.replaceTree(t,replacement)
    }
    def wrap(t: Term, replacement: String): Patch =
      if(fields.get(t.symbol.value).contains(replacement) || policy.results.get(t.symbol.value).contains(replacement)) Patch.empty
      else Patch.addLeft(t,replacement + "(") + Patch.addRight(t,")")
    val declarations = doc.tree.collect {
      case p: Term.Param if fields.contains(p.name.symbol.value) =>
        change(p.decltpe.getOrElse(sys.error("Missing explicit model field type")),fields(p.name.symbol.value)) +
          p.default.map(wrap(_,fields(p.name.symbol.value))).getOrElse(Patch.empty)
      case d: Defn.Def if policy.results.contains(d.name.symbol.value) =>
        change(d.decltpe.getOrElse(sys.error("Missing explicit result type")),policy.results(d.name.symbol.value))
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
          r.fields.get(name).map(wrap(value,_)).getOrElse(Patch.empty)
        }.asPatch
      }.getOrElse(Patch.empty)
    }
    (declarations ++ calls).asPatch.atomic
  }
}
