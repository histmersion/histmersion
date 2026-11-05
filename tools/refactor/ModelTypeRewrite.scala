import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, Configured, generic}

object ModelTypeRewrite {
  case class Record(owner: String, parameters: List[String], fields: Map[String,String], optionalFields: List[String] = Nil, listFields: List[String] = Nil, setFields: List[String] = Nil)
  object Record {
    implicit val surface: generic.Surface[Record] = generic.deriveSurface[Record]
    implicit val decoder: ConfDecoder[Record] = generic.deriveDecoder(Record("",Nil,Map.empty)).noTypos
  }
  case class Policy(records: List[Record] = Nil, results: Map[String,String] = Map.empty, optionalResults: List[String] = Nil,
    scalarBoundaryOwners: List[String] = Nil, scalarBoundaryCalls: Map[String,List[Int]] = Map.empty, codecImport: Option[String] = None,
    knownValues: Map[String,String] = Map.empty, optionalKnownValues: List[String] = Nil,
    methods: List[Record] = Nil, primitiveTypes: Map[String,String] = Map.empty,
    listResults: List[String] = Nil, listKnownValues: List[String] = Nil,
    containerBoundaryOwners: List[String] = Nil, containerBoundaryCalls: Map[String,List[Int]] = Map.empty,
    widenNumericBoundaries: Boolean = false, setResults: List[String] = Nil, setKnownValues: List[String] = Nil)
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
    (p.records ++ p.methods).foreach { r =>
      require(r.listFields.toSet.subsetOf(r.fields.keySet) && r.listFields.toSet.intersect(r.optionalFields.toSet).isEmpty)
      require(r.setFields.toSet.subsetOf(r.fields.keySet) && r.setFields.toSet.intersect((r.optionalFields ++ r.listFields).toSet).isEmpty)
    }
    require(p.listResults.toSet.subsetOf(p.results.keySet) && p.listResults.toSet.intersect(p.optionalResults.toSet).isEmpty)
    require(p.listKnownValues.toSet.subsetOf(p.knownValues.keySet) && p.listKnownValues.toSet.intersect(p.optionalKnownValues.toSet).isEmpty)
    require(p.setResults.toSet.subsetOf(p.results.keySet) && p.setResults.toSet.intersect((p.optionalResults ++ p.listResults).toSet).isEmpty)
    require(p.setKnownValues.toSet.subsetOf(p.knownValues.keySet) && p.setKnownValues.toSet.intersect((p.optionalKnownValues ++ p.listKnownValues).toSet).isEmpty)
    require(p.primitiveTypes.values.forall(Set("scala/Int#", "scala/Long#", "scala/Double#", "java/lang/String#")), "Unsupported reviewed primitive base")
    require(p.scalarBoundaryCalls.values.forall(_.forall(_ >= 0)))
    require(p.containerBoundaryCalls.values.forall(_.forall(_ >= 0)))
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
    val listSymbols = policy.records.flatMap(r => r.listFields.map(r.owner + _ + ".")).toSet ++
      policy.methods.flatMap(r => r.listFields.map(n => r.owner + "(" + n + ")")) ++ policy.listResults ++ policy.listKnownValues
    val setSymbols = policy.records.flatMap(r => r.setFields.map(r.owner + _ + ".")).toSet ++
      policy.methods.flatMap(r => r.setFields.map(n => r.owner + "(" + n + ")")) ++ policy.setResults ++ policy.setKnownValues
    def container(symbol: String): String = if(optionalSymbols(symbol)) "option" else if(listSymbols(symbol)) "list" else if(setSymbols(symbol)) "set" else ""
    def callTarget(target: String): Option[Record] = policy.methods.find(_.owner == target).orElse(policy.records.find(r =>
      target == r.owner.stripSuffix("#") + "." || target == r.owner.stripSuffix("#") + ".apply()." || target == r.owner + "copy()."))
    def ancestors(t: Tree): List[Tree] = t.parent.toList.flatMap(p => p :: ancestors(p))
    def scalarBoundary(t: Term): Boolean = {
      val parents = ancestors(t)
      val owners = if(container(t.symbol.value).isEmpty) policy.scalarBoundaryOwners else policy.containerBoundaryOwners
      val calls = if(container(t.symbol.value).isEmpty) policy.scalarBoundaryCalls else policy.containerBoundaryCalls
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
      !forwarded && (parents.collectFirst { case d: Defn.Def => d.name.symbol.value }.exists(owners.contains) ||
        parents.exists {
          case a: Term.Apply => calls.get(a.fun.symbol.value).exists(indices =>
            indices.exists(i => a.argClause.values.lift(i).exists(arg => arg.pos.start <= t.pos.start && arg.pos.end >= t.pos.end)))
          case _ => false
        })
    }
    def change(t: Type, replacement: String, shape: String): Patch = {
      val accepted = shape match {
        case "option" => Set("scala/Option#")
        case "list" => Set("scala/package.List#", "scala/collection/immutable/List#")
        case "set" => Set("scala/Predef.Set#", "scala/collection/immutable/Set#")
        case "" => Set.empty[String]
      }
      val leaf = if(shape.nonEmpty) t match {
        case a: Type.Apply if accepted(a.tpe.symbol.value) && a.argClause.values.size == 1 => a.argClause.values.head
        case _ => sys.error("Expected explicit reviewed " + shape + " container: " + t.structure)
      } else t
      val actual = if(leaf.symbol.value == "scala/Predef.String#") "java/lang/String#" else leaf.symbol.value
      require(actual == policy.primitiveTypes.getOrElse(replacement,"scala/Int#"),"Unexpected primitive base for reviewed symbol: " + t.structure)
      Patch.replaceTree(leaf,replacement)
    }
    def resultSymbol(t: Term): String = t match {
      case a: Term.Apply => resultSymbol(a.fun)
      case a: Term.ApplyType => resultSymbol(a.fun)
      case _ => t.symbol.value
    }
    val wrappedScalars = scala.collection.mutable.Set.empty[(Int,Int)]
    def wrap(t: Term, replacement: String, shape: String): Patch = {
      val symbol = resultSymbol(t)
      if((fields.get(symbol).contains(replacement) || policy.results.get(symbol).contains(replacement)) && container(symbol) == shape && !scalarBoundary(t)) Patch.empty
      else if(shape == "option" && t.symbol.value == "scala/None.") Patch.empty
      else if(shape.nonEmpty) Patch.addLeft(t,"(") + Patch.addRight(t,").map(" + replacement + ".apply)")
      else if(fields.contains(symbol) && scalarBoundary(t)) {
        wrappedScalars += ((t.pos.start,t.pos.end))
        Patch.addLeft(t,replacement + "(") + Patch.addRight(t,".value)")
      }
      else Patch.addLeft(t,replacement + "(") + Patch.addRight(t,")")
    }
    val declarations = doc.tree.collect {
      case p: Term.Param if declaredFields.contains(p.name.symbol.value) =>
        change(p.decltpe.getOrElse(sys.error("Missing explicit model field type")),fields(p.name.symbol.value),container(p.name.symbol.value)) +
          p.default.map(wrap(_,fields(p.name.symbol.value),container(p.name.symbol.value))).getOrElse(Patch.empty)
      case d: Defn.Def if policy.results.contains(d.name.symbol.value) =>
        change(d.decltpe.getOrElse(sys.error("Missing explicit result type")),policy.results(d.name.symbol.value),container(d.name.symbol.value)) +
          wrap(d.body,policy.results(d.name.symbol.value),container(d.name.symbol.value))
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
          r.fields.get(name).map(wrap(value,_,if(r.optionalFields.contains(name)) "option" else if(r.listFields.contains(name)) "list" else if(r.setFields.contains(name)) "set" else "")).getOrElse(Patch.empty)
        }.asPatch
      }.getOrElse(Patch.empty)
    }
    def unwrapScalar(t: Term): Patch = {
      val base = policy.primitiveTypes.getOrElse(fields(t.symbol.value),"scala/Int#")
      if(policy.widenNumericBoundaries && Set("scala/Int#","scala/Long#","scala/Double#")(base))
        Patch.addRight(t,".value.to" + base.stripPrefix("scala/").stripSuffix("#"))
      else Patch.addRight(t,".value")
    }
    val boundaries = doc.tree.collect {
      case t: Term.Select if fields.contains(t.symbol.value) && container(t.symbol.value).nonEmpty && scalarBoundary(t) => Patch.addRight(t,".map(_.value)")
      case n: Term.Name if fields.contains(n.symbol.value) && container(n.symbol.value).nonEmpty && scalarBoundary(n) && !n.parent.exists {
        case s: Term.Select => s.name eq n
        case p: Term.Param => p.name eq n
        case _ => false
      } => Patch.addRight(n,".map(_.value)")
      case t: Term.Select if fields.contains(t.symbol.value) && container(t.symbol.value).isEmpty && scalarBoundary(t) && !wrappedScalars((t.pos.start,t.pos.end)) => unwrapScalar(t)
      case n: Term.Name if fields.contains(n.symbol.value) && container(n.symbol.value).isEmpty && scalarBoundary(n) && !wrappedScalars((n.pos.start,n.pos.end)) && !n.parent.exists {
        case s: Term.Select => s.name eq n
        case p: Term.Param => p.name eq n
        case _ => false
      } => unwrapScalar(n)
    }
    val codec = policy.codecImport.filter(_ => doc.tree.collect {
      case c: Defn.Class if policy.records.exists(_.owner == c.name.symbol.value) && c.templ.derives.nonEmpty => c
    }.nonEmpty).map(i => Patch.addGlobalImport(i.parse[Importer].get)).getOrElse(Patch.empty)
    ((declarations ++ calls ++ boundaries).asPatch + codec).atomic
  }
}
