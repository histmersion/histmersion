import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, Configured, generic}

object ModelTypeRewrite {
  case class TypeSite(owner: String, target: String, path: List[Int], constructors: List[String], factory: String = "")
  object TypeSite {
    implicit val surface: generic.Surface[TypeSite] = generic.deriveSurface[TypeSite]
    implicit val decoder: ConfDecoder[TypeSite] = generic.deriveDecoder(TypeSite("","",Nil,Nil)).noTypos
  }
  case class Record(owner: String, parameters: List[String], fields: Map[String,String], optionalFields: List[String] = Nil, listFields: List[String] = Nil, setFields: List[String] = Nil, nestedFields: Map[String,List[String]] = Map.empty, callbackFields: List[String] = Nil)
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
    widenNumericBoundaries: Boolean = false, setResults: List[String] = Nil, setKnownValues: List[String] = Nil,
    widenStringTypes: List[String] = Nil, optionalNormalizers: List[String] = Nil, typeSites: List[TypeSite] = Nil)
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
      require(r.callbackFields.toSet.subsetOf(r.fields.keySet) &&
        r.callbackFields.toSet.intersect((r.optionalFields ++ r.listFields ++ r.setFields ++ r.nestedFields.keys).toSet).isEmpty)
      require(r.callbackFields.isEmpty || p.methods.contains(r), "Callback input rewrites require a method parameter")
      require(r.listFields.toSet.subsetOf(r.fields.keySet) && r.listFields.toSet.intersect(r.optionalFields.toSet).isEmpty)
      require(r.setFields.toSet.subsetOf(r.fields.keySet) && r.setFields.toSet.intersect((r.optionalFields ++ r.listFields).toSet).isEmpty)
      require(r.nestedFields.keySet.subsetOf(r.fields.keySet) && r.nestedFields.keySet.intersect((r.optionalFields ++ r.listFields ++ r.setFields).toSet).isEmpty)
      require(r.nestedFields.values.forall(s => s.size >= 2 && s.forall(Set("option","list","set"))), "Unsupported nested container path")
    }
    require(p.listResults.toSet.subsetOf(p.results.keySet) && p.listResults.toSet.intersect(p.optionalResults.toSet).isEmpty)
    require(p.listKnownValues.toSet.subsetOf(p.knownValues.keySet) && p.listKnownValues.toSet.intersect(p.optionalKnownValues.toSet).isEmpty)
    require(p.setResults.toSet.subsetOf(p.results.keySet) && p.setResults.toSet.intersect((p.optionalResults ++ p.listResults).toSet).isEmpty)
    require(p.setKnownValues.toSet.subsetOf(p.knownValues.keySet) && p.setKnownValues.toSet.intersect((p.optionalKnownValues ++ p.listKnownValues).toSet).isEmpty)
    require(p.primitiveTypes.values.forall(Set("scala/Int#", "scala/Long#", "scala/Double#", "java/lang/String#")), "Unsupported reviewed primitive base")
    require(p.scalarBoundaryCalls.values.forall(_.forall(_ >= 0)))
    require(p.containerBoundaryCalls.values.forall(_.forall(_ >= 0)))
    require(p.widenStringTypes.forall(t => p.primitiveTypes.get(t).contains("java/lang/String#")), "String extraction requires a reviewed String base")
    require(p.typeSites.map(s => (s.owner,s.path,s.factory)).distinct.size == p.typeSites.size)
    p.typeSites.foreach(s => require(s.owner.nonEmpty && p.primitiveTypes.contains(s.target) && s.path.forall(_ >= 0) &&
      s.constructors.size == s.path.size && (s.factory.isEmpty || s.path.nonEmpty), "Invalid reviewed type site"))
    Configured.ok(new ModelTypeRewrite(p))
  }
  override def fix(implicit doc: SemanticDocument): Patch = {
    def checkMethod(name: Term.Name, clauses: List[Term.ParamClause]): Unit = policy.methods.find(_.owner == name.symbol.value).foreach { r =>
      val ordinary = clauses.filter(_.mod.isEmpty)
      require(ordinary.size == 1 && ordinary.head.values.map(_.name.value) == r.parameters &&
        clauses.filter(_.mod.nonEmpty).forall(_.mod.exists(_.is[Mod.Using])),
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
    val callbacks = policy.methods.flatMap(r => r.callbackFields.map(n => (r.owner + "(" + n + ")") -> r.fields(n))).toMap
    val declaredFields = (policy.records.flatMap(r => r.fields.map { case(n,t) => (r.owner + n + ".") -> t }) ++
      policy.methods.flatMap(r => r.fields.filterNot { case(n,_) => r.callbackFields.contains(n) }.map { case(n,t) => (r.owner + "(" + n + ")") -> t })).toMap
    require(declaredFields.keySet.intersect(policy.knownValues.keySet).isEmpty,"A known typed value must not also be a primitive rewrite target")
    val fields = policy.knownValues ++ declaredFields
    val optionalSymbols = policy.records.flatMap(r => r.optionalFields.map(r.owner + _ + ".")).toSet ++
      policy.methods.flatMap(r => r.optionalFields.map(n => r.owner + "(" + n + ")")) ++ policy.optionalResults ++ policy.optionalKnownValues
    val listSymbols = policy.records.flatMap(r => r.listFields.map(r.owner + _ + ".")).toSet ++
      policy.methods.flatMap(r => r.listFields.map(n => r.owner + "(" + n + ")")) ++ policy.listResults ++ policy.listKnownValues
    val setSymbols = policy.records.flatMap(r => r.setFields.map(r.owner + _ + ".")).toSet ++
      policy.methods.flatMap(r => r.setFields.map(n => r.owner + "(" + n + ")")) ++ policy.setResults ++ policy.setKnownValues
    val nestedSymbols = (policy.records.flatMap(r => r.nestedFields.map { case(n,s) => (r.owner + n + ".") -> s.mkString("/") }) ++
      policy.methods.flatMap(r => r.nestedFields.map { case(n,s) => (r.owner + "(" + n + ")") -> s.mkString("/") })).toMap
    def container(symbol: String): String = nestedSymbols.getOrElse(symbol,if(optionalSymbols(symbol)) "option" else if(listSymbols(symbol)) "list" else if(setSymbols(symbol)) "set" else "")
    def fieldShape(r: Record, name: String): String = r.nestedFields.get(name).map(_.mkString("/")).getOrElse(
      if(r.optionalFields.contains(name)) "option" else if(r.listFields.contains(name)) "list" else if(r.setFields.contains(name)) "set" else "")
    def mapped(shape: String, leafFunction: String): String =
      ".map(" + shape.split("/").drop(1).foldRight(leafFunction)((_,inner) => "_.map(" + inner + ")") + ")"
    def callTarget(target: String): Option[Record] = callbacks.get(target).map(t => Record(target,List("argument"),Map("argument" -> t))).orElse(policy.methods.find(_.owner == target)).orElse(policy.records.find(r =>
      target == r.owner.stripSuffix("#") + "." || target == r.owner.stripSuffix("#") + ".apply()." || target == r.owner + "copy()."))
    def ancestors(t: Tree): List[Tree] = t.parent.toList.flatMap(p => p :: ancestors(p))
    def scalarBoundary(t: Term): Boolean = {
      val parents = ancestors(t)
      val symbol = resultSymbol(t)
      val owners = if(container(symbol).isEmpty) policy.scalarBoundaryOwners else policy.containerBoundaryOwners
      val calls = if(container(symbol).isEmpty) policy.scalarBoundaryCalls else policy.containerBoundaryCalls
      val forwarded = parents.exists {
        case a: Term.Apply if a.argClause.mod.isEmpty =>
          val target = resultSymbol(a.fun)
          callTarget(target).exists { r =>
            a.argClause.values.zipWithIndex.exists { case(arg,i) =>
              val (name,value) = arg match {
                case Term.Assign(n: Term.Name,v: Term) => (n.value,v)
                case v => (r.parameters.lift(i).getOrElse(""),v)
              }
              (value eq t) && r.fields.get(name).exists(x => fields.get(symbol).orElse(policy.results.get(symbol)).contains(x))
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
      def accepted(kind: String): Set[String] = kind match {
        case "option" => Set("scala/Option#")
        case "list" => Set("scala/package.List#", "scala/collection/immutable/List#")
        case "set" => Set("scala/Predef.Set#", "scala/collection/immutable/Set#")
        case "" => Set.empty[String]
      }
      val leaf = (if(shape.isEmpty) Nil else shape.split("/").toList).foldLeft(t) { (current,kind) => current match {
        case a: Type.Apply if accepted(kind)(a.tpe.symbol.value) && a.argClause.values.size == 1 => a.argClause.values.head
        case _ => sys.error("Expected explicit reviewed " + shape + " container: " + t.structure)
      }}
      val actual = if(leaf.symbol.value == "scala/Predef.String#") "java/lang/String#" else leaf.symbol.value
      require(actual == policy.primitiveTypes.getOrElse(replacement,"scala/Int#"),"Unexpected primitive base for reviewed symbol: " + t.structure)
      Patch.replaceTree(leaf,replacement)
    }
    def resultSymbol(t: Term): String = t match {
      case a: Term.Apply => resultSymbol(a.fun)
      case a: Term.ApplyType => resultSymbol(a.fun)
      case _ => t.symbol.value
    }
    def atPath(t: Type, path: List[Int], constructors: List[String]): Type = path match {
      case Nil => t
      case index :: rest => t match {
        case a: Type.Apply if a.tpe.symbol.value == constructors.head && a.argClause.values.isDefinedAt(index) =>
          atPath(a.argClause.values(index),rest,constructors.tail)
        case _ => sys.error("Reviewed type path changed: " + t.structure)
      }
    }
    def typeSitePatches(names: List[Pat], declared: Option[Type], rhs: Option[Term]): Patch = {
      val selected = policy.typeSites.filter(s => names.exists {
        case Pat.Var(n) => n.symbol.value == s.owner
        case _ => false
      })
      selected.map { s =>
        require(names.size == 1,"Reviewed type site requires a single binding")
        if(s.factory.isEmpty) change(atPath(declared.getOrElse(sys.error("Missing reviewed binding type")),s.path,s.constructors),s.target,"")
        else rhs match {
          case Some(a: Term.ApplyType) if resultSymbol(a.fun) == s.factory && s.constructors.head == s.factory && a.targClause.values.isDefinedAt(s.path.head) =>
            change(atPath(a.targClause.values(s.path.head),s.path.tail,s.constructors.tail),s.target,"")
          case _ => sys.error("Reviewed binding factory changed: " + s.owner)
        }
      }.asPatch
    }
    val bindingTypes = doc.tree.collect {
      case d: Defn.Val => typeSitePatches(d.pats,d.decltpe,Some(d.rhs))
      case d: Defn.Var => typeSitePatches(d.pats,d.decltpe,d.rhs)
    }
    val wrappedScalars = scala.collection.mutable.Set.empty[(Int,Int)]
    def wrap(t: Term, replacement: String, shape: String): Patch = {
      val symbol = resultSymbol(t)
      if((fields.get(symbol).contains(replacement) || policy.results.get(symbol).contains(replacement)) && container(symbol) == shape && !scalarBoundary(t)) Patch.empty
      else if(shape.split("/").headOption.contains("option") && t.symbol.value == "scala/None.") Patch.empty
      else if(shape.nonEmpty) Patch.addLeft(t,"(") + Patch.addRight(t,")" + mapped(shape,replacement + ".apply"))
      else if(fields.contains(symbol) && scalarBoundary(t)) {
        wrappedScalars += ((t.pos.start,t.pos.end))
        Patch.addLeft(t,replacement + "(") + Patch.addRight(t,".value)")
      }
      else Patch.addLeft(t,replacement + "(") + Patch.addRight(t,")")
    }
    val declarations = doc.tree.collect {
      case p: Term.Param if callbacks.contains(p.name.symbol.value) =>
        p.decltpe match {
          case Some(f: Type.Function) if f.paramClause.values.size == 1 =>
            change(f.paramClause.values.head,callbacks(p.name.symbol.value),"")
          case _ => sys.error("Expected explicit unary callback type: " + p.structure)
        }
      case p: Term.Param if declaredFields.contains(p.name.symbol.value) =>
        change(p.decltpe.getOrElse(sys.error("Missing explicit model field type")),fields(p.name.symbol.value),container(p.name.symbol.value)) +
          p.default.map(wrap(_,fields(p.name.symbol.value),container(p.name.symbol.value))).getOrElse(Patch.empty)
      case d: Defn.Def if policy.results.contains(d.name.symbol.value) =>
        change(d.decltpe.getOrElse(sys.error("Missing explicit result type")),policy.results(d.name.symbol.value),container(d.name.symbol.value)) +
          wrap(d.body,policy.results(d.name.symbol.value),container(d.name.symbol.value))
    }
    def onlyContextApplications(t: Term): Boolean = t match {
      case a: Term.Apply => a.argClause.mod.exists(_.is[Mod.Using]) && onlyContextApplications(a.fun)
      case a: Term.ApplyType => onlyContextApplications(a.fun)
      case _ => true
    }
    val calls = doc.tree.collect { case a: Term.Apply if a.argClause.mod.isEmpty && onlyContextApplications(a.fun) =>
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
          if(r.callbackFields.contains(name)) Patch.empty
          else r.fields.get(name).map(wrap(value,_,fieldShape(r,name))).getOrElse(Patch.empty)
        }.asPatch
      }.getOrElse(Patch.empty)
    }
    def extraction(symbol: String): String = {
      val target = fields.get(symbol).orElse(policy.results.get(symbol)).get
      val base = policy.primitiveTypes.getOrElse(target,"scala/Int#")
      if(policy.widenNumericBoundaries && Set("scala/Int#","scala/Long#","scala/Double#")(base))
        ".value.to" + base.stripPrefix("scala/").stripSuffix("#")
      else if(policy.widenStringTypes.contains(target)) ".value.toString"
      else ".value"
    }
    def unwrapScalar(t: Term): Patch = Patch.addRight(t,extraction(t.symbol.value))
    def unwrapContainer(t: Term): Patch = Patch.addRight(t,mapped(container(t.symbol.value),"_" + extraction(t.symbol.value)))
    val boundaries = doc.tree.collect {
      case a: Term.Apply if policy.results.contains(resultSymbol(a)) && scalarBoundary(a) && !a.parent.exists {
        case p: Term.Apply => p.fun eq a
        case _ => false
      } =>
        val symbol = resultSymbol(a)
        Patch.addRight(a,if(container(symbol).isEmpty) extraction(symbol) else ".map(_" + extraction(symbol) + ")")
      case t: Term.Select if fields.contains(t.symbol.value) && container(t.symbol.value).nonEmpty && scalarBoundary(t) => unwrapContainer(t)
      case n: Term.Name if fields.contains(n.symbol.value) && container(n.symbol.value).nonEmpty && scalarBoundary(n) && !n.parent.exists {
        case s: Term.Select => s.name eq n
        case p: Term.Param => p.name eq n
        case _ => false
      } => unwrapContainer(n)
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
    val normalization = doc.tree.collect { case outer: Term.Apply =>
      (outer.fun,outer.argClause.values) match {
        case (first: Term.Apply,List(fn: Term.Function)) => first.fun match {
          case select: Term.Select if select.name.symbol.value == "scala/Option#fold()." &&
            fields.contains(select.qual.symbol.value) && container(select.qual.symbol.value) == "option" && fn.paramClause.values.size == 1 =>
            val target = fields(select.qual.symbol.value)
            fn.body match {
              case call: Term.Apply if policy.optionalNormalizers.contains(resultSymbol(call.fun)) && policy.primitiveTypes.get(target).contains("java/lang/String#") =>
                call.argClause.values match {
                  case List(some: Term.Apply) if Set("scala/Some.","scala/Some.apply().")(resultSymbol(some.fun)) => some.argClause.values match {
                    case List(value: Term.Name) if value.symbol == fn.paramClause.values.head.name.symbol =>
                      Patch.addRight(value,".value") + Patch.addRight(call,".map(" + target + ".apply)")
                    case _ => Patch.empty
                  }
                  case _ => Patch.empty
                }
              case _ => Patch.empty
            }
          case _ => Patch.empty
        }
        case _ => Patch.empty
      }
    }
    ((bindingTypes ++ declarations ++ calls ++ boundaries ++ normalization).asPatch + codec).atomic
  }
}
