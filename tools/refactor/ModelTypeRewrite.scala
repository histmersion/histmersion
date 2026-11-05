import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, Configured, generic}

object ModelTypeRewrite {
  case class Record(owner: String, parameters: List[String], fields: Map[String,String], optionalFields: List[String] = Nil)
  object Record {
    implicit val surface: generic.Surface[Record] = generic.deriveSurface[Record]
    implicit val decoder: ConfDecoder[Record] = generic.deriveDecoder(Record("",Nil,Map.empty)).noTypos
  }
  case class Policy(records: List[Record] = Nil, results: Map[String,String] = Map.empty, optionalResults: List[String] = Nil,
    scalarBoundaryOwners: List[String] = Nil, scalarBoundaryCalls: Map[String,List[Int]] = Map.empty, codecImport: Option[String] = None,
    knownValues: Map[String,String] = Map.empty, optionalKnownValues: List[String] = Nil,
    methods: List[Record] = Nil)
  object Policy {
    implicit val surface: generic.Surface[Policy] = generic.deriveSurface[Policy]
    implicit val decoder: ConfDecoder[Policy] = generic.deriveDecoder(Policy()).noTypos
  }
}

class ModelTypeRewrite(val policy: ModelTypeRewrite.Policy) extends SemanticRule("ModelTypeRewrite") {
  def this() = this(ModelTypeRewrite.Policy())
  import ModelTypeRewrite._
  private implicit val dialect: Dialect = dialects.Scala3
  override def withConfiguration(c: Configuration): Configured[Rule] = c.conf.getOrElse("ModelTypeRewrite")(Policy()).andThen { p =>
    require(p.records.nonEmpty || p.methods.nonEmpty,"Explicit reviewed records or methods required")
    require(p.records.map(_.owner).distinct.size == p.records.size)
    p.records.foreach(r => require(r.owner.endsWith("#") && r.parameters.distinct.size == r.parameters.size && r.fields.keySet.subsetOf(r.parameters.toSet) && r.optionalFields.toSet.subsetOf(r.fields.keySet)))
    require(p.methods.map(_.owner).distinct.size == p.methods.size)
    p.methods.foreach(r => require(r.owner.endsWith("().") && r.parameters.distinct.size == r.parameters.size && r.fields.keySet.subsetOf(r.parameters.toSet) && r.optionalFields.toSet.subsetOf(r.fields.keySet)))
    require(p.optionalResults.toSet.subsetOf(p.results.keySet))
    require(p.optionalKnownValues.toSet.subsetOf(p.knownValues.keySet))
    require(p.scalarBoundaryCalls.values.forall(_.forall(_ >= 0)))
    Configured.ok(new ModelTypeRewrite(p))
  }
  override def fix(implicit doc: SemanticDocument): Patch = {
    def checkMethod(name: Term.Name, clauses: List[Term.ParamClause]): Unit = policy.methods.find(_.owner == name.symbol.value).foreach { r =>
      require(clauses.size == 1 && clauses.head.values.map(_.name.value) == r.parameters,
        "Reviewed method parameter order changed: " + r.owner)
    }
    doc.tree.collect {
      case d: Defn.Def => checkMethod(d.name,d.paramClauseGroups.flatMap(_.paramClauses))
      case d: Decl.Def => checkMethod(d.name,d.paramClauseGroups.flatMap(_.paramClauses))
    }
    doc.tree.collect { case c: Defn.Class =>
      policy.records.find(_.owner == c.name.symbol.value).foreach { r =>
        require(c.ctor.paramClauses.size == 1 && c.ctor.paramClauses.head.values.map(_.name.value) == r.parameters,
          "Reviewed constructor parameter order changed: " + r.owner)
      }
    }
    val declaredFields = (policy.records.flatMap(r => r.fields.map { case(n,t) => (r.owner + n + ".") -> t }) ++
      policy.methods.flatMap(r => r.fields.map { case(n,t) => (r.owner + "(" + n + ")") -> t })).toMap
    require(declaredFields.keySet.intersect(policy.knownValues.keySet).isEmpty,"A known typed value must not also be a primitive rewrite target")
    val fields = policy.knownValues ++ declaredFields
    val optionalSymbols = policy.records.flatMap(r => r.optionalFields.map(r.owner + _ + ".")).toSet ++
      policy.methods.flatMap(r => r.optionalFields.map(n => r.owner + "(" + n + ")")) ++ policy.optionalResults ++ policy.optionalKnownValues
    def callTarget(target: String): Option[Record] = policy.methods.find(_.owner == target).orElse(policy.records.find(r =>
      target == r.owner.stripSuffix("#") + "." || target == r.owner.stripSuffix("#") + ".apply()." || target == r.owner + "copy()."))
    def ancestors(t: Tree): List[Tree] = t.parent.toList.flatMap(p => p :: ancestors(p))
    def scalarBoundary(t: Term): Boolean = {
      val parents = ancestors(t)
      val forwarded = parents.exists {
        case a: Term.Apply =>
          val target = resultSymbol(a.fun)
          callTarget(target).exists { r =>
            a.argClause.values.zipWithIndex.exists { case(arg,i) =>
              val (name,value) = arg match {
                case Term.Assign(n: Term.Name,v: Term) => (n.value,v)
                case v => (r.parameters.lift(i).getOrElse(""),v)
              }
              (value eq t) && r.fields.get(name).exists(x => fields.get(t.symbol.value).contains(x))
            }
          }
        case _ => false
      }
      !forwarded && (parents.collectFirst { case d: Defn.Def => d.name.symbol.value }.exists(policy.scalarBoundaryOwners.contains) ||
        parents.exists {
          case a: Term.Apply => policy.scalarBoundaryCalls.get(a.fun.symbol.value).exists(indices =>
            indices.exists(i => a.argClause.values.lift(i).exists(arg => arg.pos.start <= t.pos.start && arg.pos.end >= t.pos.end)))
          case _ => false
        })
    }
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
      case a: Term.ApplyType => resultSymbol(a.fun)
      case _ => t.symbol.value
    }
    val wrappedScalars = scala.collection.mutable.Set.empty[(Int,Int)]
    def wrap(t: Term, replacement: String, optional: Boolean): Patch = {
      val symbol = resultSymbol(t)
      if((fields.get(symbol).contains(replacement) || policy.results.get(symbol).contains(replacement)) && optionalSymbols(symbol) == optional && !scalarBoundary(t)) Patch.empty
      else if(optional && t.symbol.value == "scala/None.") Patch.empty
      else if(optional) Patch.addLeft(t,"(") + Patch.addRight(t,").map(" + replacement + ".apply)")
      else if(fields.contains(symbol) && scalarBoundary(t)) {
        wrappedScalars += ((t.pos.start,t.pos.end))
        Patch.addLeft(t,replacement + "(") + Patch.addRight(t,".value)")
      }
      else Patch.addLeft(t,replacement + "(") + Patch.addRight(t,")")
    }
    val declarations = doc.tree.collect {
      case p: Term.Param if declaredFields.contains(p.name.symbol.value) =>
        change(p.decltpe.getOrElse(sys.error("Missing explicit model field type")),fields(p.name.symbol.value),optionalSymbols(p.name.symbol.value)) +
          p.default.map(wrap(_,fields(p.name.symbol.value),optionalSymbols(p.name.symbol.value))).getOrElse(Patch.empty)
      case d: Defn.Def if policy.results.contains(d.name.symbol.value) =>
        change(d.decltpe.getOrElse(sys.error("Missing explicit result type")),policy.results(d.name.symbol.value),optionalSymbols(d.name.symbol.value)) +
          wrap(d.body,policy.results(d.name.symbol.value),optionalSymbols(d.name.symbol.value))
    }
    val calls = doc.tree.collect { case a: Term.Apply =>
      val target = resultSymbol(a.fun)
      val record = callTarget(target)
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
    val boundaries = doc.tree.collect {
      case t: Term.Select if fields.contains(t.symbol.value) && !optionalSymbols(t.symbol.value) && scalarBoundary(t) && !wrappedScalars((t.pos.start,t.pos.end)) => Patch.addRight(t,".value")
      case n: Term.Name if fields.contains(n.symbol.value) && !optionalSymbols(n.symbol.value) && scalarBoundary(n) && !wrappedScalars((n.pos.start,n.pos.end)) && !n.parent.exists {
        case s: Term.Select => s.name eq n
        case p: Term.Param => p.name eq n
        case _ => false
      } => Patch.addRight(n,".value")
    }
    val codec = policy.codecImport.filter(_ => doc.tree.collect {
      case c: Defn.Class if policy.records.exists(_.owner == c.name.symbol.value) && c.templ.derives.nonEmpty => c
    }.nonEmpty).map(i => Patch.addGlobalImport(i.parse[Importer].get)).getOrElse(Patch.empty)
    ((declarations ++ calls ++ boundaries).asPatch + codec).atomic
  }
}
