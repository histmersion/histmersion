import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, Configured, generic}
import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets.UTF_8
import scala.jdk.CollectionConverters._
import java.util.concurrent.ConcurrentLinkedQueue

object ModelInventory {
  case class Policy(roots: List[String] = List("modules"), report: String = "refactor-audit/models", expected: List[String] = Nil, forbidden: List[String] = Nil,
    layers: Map[String,String] = Map.empty,
    pureConstraints: List[String] = List("io/github/iltotore/iron/constraint/any.Pure#"),
    nonPureConstraints: List[String] = List("io/github/iltotore/iron/constraint/numeric.Greater#"),
    clientModules: List[String] = List("api","web","cli","plugin-api"), boundaryOwners: List[String] = Nil)
  object Policy {
    implicit val surface: generic.Surface[Policy] = generic.deriveSurface[Policy]
    implicit val decoder: ConfDecoder[Policy] = generic.deriveDecoder(Policy()).noTypos
  }
  case class Position(id: String, path: String, module: String, scope: String, line: Int, kind: String, owner: String,
    symbol: String, name: String, visibility: String, declared: String, ast: String, signature: String)
  case class Leaf(position: String, route: String, status: String, symbol: String, detail: String)
  case class TypeRow(symbol: String, path: String, line: Int, kind: String, name: String, owner: String, parameters: String, definition: String, ast: String, signature: String)
  case class Use(target: String, path: String, line: Int, owner: String, syntax: String)
  case class Role(position: String, category: String, declaration: String, enclosingType: String, visibility: String)
  val primitives = Set("scala/Boolean#", "scala/Byte#", "scala/Short#", "scala/Int#", "scala/Long#", "scala/Float#", "scala/Double#", "scala/Char#", "java/lang/String#", "scala/Predef.String#")
  def sources(roots: List[String]): Set[String] = roots.flatMap { root =>
    val stream = Files.walk(Paths.get(root).toAbsolutePath.normalize)
    try stream.iterator.asScala.filter(p => Files.isRegularFile(p) && p.toString.endsWith(".scala") &&
      (p.toString.contains("/src/main/") || p.toString.contains("/src/test/"))).map(_.toString).toList finally stream.close()
  }.toSet
  def hash(text: String): String = java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(UTF_8)).map(b => f"${b & 255}%02x").mkString
  def module(path: String): String = Paths.get("").toAbsolutePath.normalize.relativize(Paths.get(path).toAbsolutePath.normalize).toString.stripPrefix("modules/").split("/src/").head
}

class ModelInventory(val policy: ModelInventory.Policy) extends SemanticRule("ModelInventory") {
  def this() = this(ModelInventory.Policy())
  import ModelInventory._
  private implicit val dialect: Dialect = dialects.Scala3
  private val positions = new ConcurrentLinkedQueue[Position]()
  private val leaves = new ConcurrentLinkedQueue[Leaf]()
  private val types = new ConcurrentLinkedQueue[TypeRow]()
  private val uses = new ConcurrentLinkedQueue[Use]()
  private val files = new ConcurrentLinkedQueue[(String,String)]()
  private case class Info(signature: Signature, opaque: Boolean, parameter: Boolean)
  private val symbols = new java.util.concurrent.ConcurrentHashMap[String,Info]()
  private val pending = new ConcurrentLinkedQueue[(SemanticType,String,String,String)]()
  private val roles = new ConcurrentLinkedQueue[Role]()
  override def withConfiguration(c: Configuration): Configured[Rule] = c.conf.getOrElse("ModelInventory")(Policy()).andThen { p =>
    if (p.roots.isEmpty || p.report.isEmpty) Configured.error("ModelInventory requires roots and report") else {
      require(p.pureConstraints.intersect(p.nonPureConstraints).isEmpty,"Conflicting constraint classification policy")
      Files.createDirectories(Paths.get(p.report)); Files.writeString(Paths.get(p.report,"status.txt"),"RUNNING\n")
      Configured.ok(new ModelInventory(p))
    }
  }
  private def parents(t: Tree): List[Tree] = t.parent.toList.flatMap(p => p :: parents(p))
  private def owner(t: Tree)(implicit d: SemanticDocument): String = parents(t).filterNot {
    case x: Defn.Def => x.name eq t
    case x: Decl.Def => x.name eq t
    case x: Defn.Class => x.name eq t
    case x: Defn.Trait => x.name eq t
    case x: Defn.Enum => x.name eq t
    case x: Defn.Object => x.name eq t
    case _ => false
  }.collectFirst {
    case x: Defn.Def => x.name.symbol.value
    case x: Defn.Class => x.name.symbol.value
    case x: Defn.Trait => x.name.symbol.value
    case x: Defn.Enum => x.name.symbol.value
    case x: Defn.Object => x.name.symbol.value
    case x: Decl.Def => x.name.symbol.value
  }.getOrElse("")
  private def key(s: Symbol, path: String): String = if (s.isLocal) path + "::" + s.value else s.value
  private def children(t: SemanticType): List[SemanticType] = t.productIterator.toList.flatMap {
    case s: SemanticType => List(s)
    case xs: List[_] => xs.collect { case s: SemanticType => s }
    case _ => Nil
  }
  private def iron(s: Symbol)(implicit d: SemanticDocument): Boolean = s.info.exists(_.signature match {
    case ClassSignature(_, ps, _, _) => ps.exists {
      case TypeRef(_, parent, _) => Set("io/github/iltotore/iron/RefinedType#", "io/github/iltotore/iron/RefinedSubtype#")(parent.value)
      case _ => false
    }
    case _ => false
  })
  private def inspect(t: SemanticType, id: String, route: String, path: String, visited: Set[String] = Set.empty,
      bindings: Map[String,SemanticType] = Map.empty): Unit = {
    def lookup(s: Symbol): Option[Info] = Option(symbols.get(key(s,path)))
    def isIron(s: Symbol): Boolean = lookup(s).exists(_.signature match {
      case ClassSignature(_,ps,_,_) => ps.exists { case TypeRef(_,p,_) => Set("io/github/iltotore/iron/RefinedType#","io/github/iltotore/iron/RefinedSubtype#")(p.value); case _ => false }
      case _ => false
    })
    def recurse(a: SemanticType, next: String, seen: Set[String] = visited, bs: Map[String,SemanticType] = bindings): Unit = inspect(a,id,next,path,seen,bs)
    def emit(status: String, symbol: String, detail: String): Unit = leaves.add(Leaf(id, route, status, symbol, detail))
    if (visited.size > 32) emit("unresolved", "", "alias-depth-limit: " + t)
    else t match {
      case TypeRef(prefix, s, args) if bindings.contains(s.value) => recurse(bindings(s.value), route + "/substitution", visited, bindings - s.value)
      case TypeRef(_, s, _) if primitives(s.value) => emit("primitive", if(s.value == "scala/Predef.String#") "java/lang/String#" else s.value, t.toString)
      case TypeRef(_,s,Nil) if Set("scala/Unit#","scala/Nothing#","scala/Any#","scala/AnyRef#","scala/Null#","java/lang/Object#")(s.value) => emit("type",s.value,t.toString)
      case ConstantType(c) =>
        val name = c.productPrefix.stripSuffix("Constant")
        if (name == "String") emit("primitive","java/lang/String#","singleton: " + t)
        else if (Set("Boolean","Byte","Short","Int","Long","Float","Double","Char")(name)) emit("primitive","scala/" + name + "#","singleton: " + t)
        else emit("type","scala/" + name + "#",t.toString)
      case TypeRef(_, s, args) if lookup(s).exists(_.opaque) =>
        emit("newtype", s.value, "opaque: " + t)
        args.zipWithIndex.foreach { case (a,i) => recurse(a,route + s"/argument[$i]") }
      case TypeRef(prefix, s, args) if (prefix match { case SingleType(_, p) => isIron(p); case _ => false }) =>
        emit("newtype", prefix.toString + "." + s.displayName, "Iron projection: " + t)
      case TypeRef(_, s, args) =>
        lookup(s) match {
          case Some(info) if info.parameter => emit("type-parameter", s.value, t.toString)
          case Some(info) => info.signature match {
            case TypeSignature(params, lo, hi) if lo == hi && lo.nonEmpty && !visited(s.value) =>
              emit("alias", s.value, t.toString)
              recurse(hi,route + "/alias:" + s.value,visited + s.value,bindings ++ params.map(_.symbol.value).zip(args))
            case _: TypeSignature =>
              emit("unresolved",s.value,"abstract, recursive or bounded alias: " + info.signature)
              args.zipWithIndex.foreach { case (a,i) => recurse(a,route + s"/argument[$i]") }
            case _ =>
              emit("type",s.value,t.toString)
              args.zipWithIndex.foreach { case (a,i) => recurse(a,route + s"/argument[$i]") }
          }
          case None =>
            emit("unresolved",s.value,"missing symbol information: " + t)
            args.zipWithIndex.foreach { case (a,i) => recurse(a,route + s"/argument[$i]") }
        }
      case _ =>
        val nested = children(t)
        if (nested.isEmpty && t.nonEmpty) emit("unresolved","",t.productPrefix + ": " + t)
        else if (t.isEmpty) emit("unresolved","","missing semantic type")
        else nested.zipWithIndex.foreach { case (a,i) => recurse(a,route + "/" + t.productPrefix + s"[$i]") }
    }
  }
  override def fix(implicit doc: SemanticDocument): Patch = {
    val path = doc.input match { case Input.File(p, _) => p.toString; case Input.VirtualFile(p, _) => p }
    files.add(path -> hash(doc.input.text))
    def remember(i: SymbolInformation): Unit = {
      val previous = symbols.putIfAbsent(key(i.symbol,path),Info(i.signature,i.isOpaque,i.isTypeParameter))
      if (previous == null) i.signature match {
        case ClassSignature(ps,_,_,ds) => (ps ++ ds).foreach(remember)
        case TypeSignature(ps,_,_) => ps.foreach(remember)
        case MethodSignature(ps,params,_) => (ps ++ params.flatten).foreach(remember)
        case _ => ()
      }
    }
    doc.tree.collect { case n: Name => n.symbol.info.foreach(remember) }
    val sourceModule = module(path)
    val test = if (path.contains("/src/test/")) "test" else "production"
    val declarations = scala.collection.mutable.Set.empty[Int]
    def position(n: Name, kind: String, declared: Option[Type]): Unit = {
      declarations += n.pos.start
      val s = n.symbol
      val info = s.info
      val local = kind == "lambda-parameter" || parents(n).exists(_.is[Term.Block]) || (kind == "value" && parents(n).exists(_.is[Defn.Def]))
      val scope = test + (if (local) "/local" else "/member")
      val id = (if (!s.isNone && !s.isLocal) s.value else path + ":" + n.pos.start) + ":" + kind
      val visibility = info.fold("unknown")(i => if (i.isPrivate || i.isPrivateThis || i.isPrivateWithin) "private" else if (i.isProtected || i.isProtectedThis || i.isProtectedWithin) "protected" else "public")
      positions.add(Position(id,path,sourceModule,scope,n.pos.startLine+1,kind,owner(n),key(s,path),n.value,visibility,
        declared.fold("<inferred>")(_.syntax),declared.fold(n.structure)(_.structure),info.fold("<missing>")(_.signature.toString)))
      val context = parents(n)
      val method = context.collectFirst { case d: Defn.Def => d.name.symbol; case d: Decl.Def => d.name.symbol }
      val enclosingType = context.collectFirst {
        case d: Defn.Class => (d.name.symbol,d.mods.exists(_.is[Mod.Case]),false)
        case d: Defn.Trait => (d.name.symbol,false,true)
        case d: Defn.Enum => (d.name.symbol,false,false)
        case d: Defn.Object => (d.name.symbol,false,false)
      }
      val access = context.flatMap {
        case d: Defn.Class => d.mods
        case d: Defn.Trait => d.mods
        case d: Defn.Object => d.mods
        case d: Defn.Enum => d.mods
        case d: Defn.Def => d.mods
        case d: Decl.Def => d.mods
        case d: Defn.Val => d.mods
        case d: Defn.Var => d.mods
        case d: Term.Param => d.mods
        case _ => Nil
      }.exists(m => m.is[Mod.Private] || m.is[Mod.Protected])
      val declaration = method.map(key(_,path)).getOrElse(key(s,path))
      val category = if(test == "test") "test-position" else if(local) "local-position"
        else if(Set("alias-definition","type-bound")(kind)) "type-definition"
        else if(policy.boundaryOwners.contains(declaration)) "configured-boundary"
        else if(enclosingType.exists(_._2) && Set("constructor-parameter","value")(kind) && method.isEmpty) "record-field"
        else if(kind == "constructor-parameter") "other-constructor-review"
        else if(access || visibility == "private" || visibility == "protected") "non-public-member-review"
        else if(enclosingType.exists(_._3) && Set("parameter","return","value")(kind)) "trait-contract"
        else if(Set("parameter","return")(kind)) "public-method-review"
        else "member-value-review"
      roles.add(Role(id,category,declaration,enclosingType.map(t => key(t._1,path)).getOrElse(""),if(access) "enclosing-access-restriction" else visibility))
      info.map(_.signature) match {
        case Some(ValueSignature(t)) => pending.add((t,id,"value",path))
        case Some(MethodSignature(_,_,t)) => pending.add((t,id,"result",path))
        case Some(TypeSignature(_,lo,hi)) => pending.add((hi,id,"upper",path)); if (lo != hi && lo.nonEmpty) pending.add((lo,id,"lower",path))
        case _ =>
          leaves.add(Leaf(id,"","unresolved",s.value,"missing or unsupported declaration signature"))
          declared.foreach(_.collect { case name: Type.Name if !name.symbol.isNone =>
            pending.add((TypeRef(NoType,name.symbol,Nil),id,"syntax-fallback:" + name.pos.start,path))
          })
      }
    }
    def catalogue(n: Name, kind: String, tree: Tree, definition: String): Unit = {
      declarations += n.pos.start
      val info = n.symbol.info
      val params = info.toList.flatMap(_.signature match {
        case ClassSignature(ps,_,_,_) => ps.map(_.symbol.value)
        case TypeSignature(ps,_,_) => ps.map(_.symbol.value)
        case _ => Nil
      }).mkString("; ")
      types.add(TypeRow(key(n.symbol,path),path,n.pos.startLine+1,kind,n.value,owner(n),params,definition,tree.structure,info.fold("<missing>")(_.signature.toString)))
    }
    doc.tree.collect {
      case p: Term.Param =>
        val kind = parents(p).collectFirst {
          case _: Term.Function => "lambda-parameter"
          case _: Term.ContextFunction => "lambda-parameter"
          case _: Ctor.Primary => "constructor-parameter"
          case _: Defn.Def => "parameter"
          case _: Decl.Def => "parameter"
        }.getOrElse("parameter")
        position(p.name,kind,p.decltpe)
      case d: Defn.Def => position(d.name,"return",d.decltpe)
      case d: Decl.Def => position(d.name,"return",Some(d.decltpe))
      case g: Defn.GivenAlias => position(g.name,"given",Some(g.decltpe))
      case g: Decl.Given => position(g.name,"given",Some(g.decltpe))
      case v: Defn.Val => v.pats.flatMap(_.collect { case p: Pat.Var => p.name }).foreach(position(_,"value",v.decltpe))
      case v: Defn.Var => v.pats.flatMap(_.collect { case p: Pat.Var => p.name }).foreach(position(_,"value",v.decltpe))
      case v: Decl.Val => v.pats.flatMap(_.collect { case p: Pat.Var => p.name }).foreach(position(_,"value",Some(v.decltpe)))
      case v: Decl.Var => v.pats.flatMap(_.collect { case p: Pat.Var => p.name }).foreach(position(_,"value",Some(v.decltpe)))
      case t: Defn.Type =>
        catalogue(t.name,if(t.mods.exists(_.is[Mod.Opaque])) "opaque" else "alias",t,t.body.syntax)
        position(t.name,"alias-definition",Some(t.body))
      case t: Decl.Type => catalogue(t.name,"abstract-type",t,t.syntax); position(t.name,"type-bound",None)
      case c: Defn.Class => catalogue(c.name,if(c.mods.exists(_.is[Mod.Case])) "case-class" else "class",c.ctor,c.templ.inits.map(_.syntax).mkString("; "))
      case c: Defn.Trait => catalogue(c.name,"trait",c.name,c.templ.inits.map(_.syntax).mkString("; "))
      case c: Defn.Enum => catalogue(c.name,"enum",c.ctor,c.templ.inits.map(_.syntax).mkString("; "))
      case o: Defn.Object => catalogue(o.name,if(iron(o.name.symbol)) "iron-newtype" else "object",o.name,o.templ.inits.map(_.syntax).mkString("; "))
    }
    doc.tree.collect { case n: Name if !n.symbol.isNone && !declarations(n.pos.start) =>
      uses.add(Use(key(n.symbol,path),path,n.pos.startLine+1,owner(n),n.parent.getOrElse(n).syntax.take(240)))
    }
    Patch.empty
  }
  override def afterComplete(): Unit = {
    val out = Paths.get(policy.report); Files.createDirectories(out)
    Files.writeString(out.resolve("status.txt"),"INCOMPLETE\n")
    require(files.asScala.map(_._1).toSet == sources(policy.roots),"Incomplete source coverage")
    files.asScala.foreach { case(p,h) => require(hash(Files.readString(Paths.get(p))) == h,"Source changed: " + p) }
    pending.asScala.foreach { case(t,id,route,path) => inspect(t,id,route,path) }
    val ps = positions.asScala.toList.sortBy(p => (p.path,p.line,p.id))
    val ls = leaves.asScala.toList.distinct.sortBy(l => (l.position,l.route,l.status,l.symbol))
    val ts = types.asScala.toList.sortBy(t => (t.path,t.line))
    val ids = ps.map(_.symbol).filter(_.nonEmpty).toSet ++ ts.map(_.symbol).filter(_.nonEmpty)
    val us = uses.asScala.toList.filter(u => ids(u.target)).distinct.sortBy(u => (u.path,u.line,u.target))
    def esc(s: String): String = s.replace("\\","\\\\").replace("\t","\\t").replace("\n","\\n").replace("\r","\\r")
    def write(name: String, header: String, rows: List[List[String]]): Unit = Files.write(out.resolve(name),(header :: rows.map(_.map(esc).mkString("\t"))).asJava,UTF_8)
    write("positions.tsv","id\tpath\tmodule\tscope\tline\tkind\towner\tsymbol\tname\tvisibility\tdeclared\tast\tsignature",ps.map(p => p.productIterator.map(_.toString).toList))
    write("leaves.tsv","position\troute\tstatus\tsymbol\tdetail",ls.map(l => l.productIterator.map(_.toString).toList))
    write("types.tsv","symbol\tpath\tline\tkind\tname\towner\tparameters\tdefinition\tast\tsignature",ts.map(t => t.productIterator.map(_.toString).toList))
    def constraint(t: SemanticType, path: String, seen: Set[String] = Set.empty): (String,String) = t match {
      case TypeRef(_,s,_) if policy.pureConstraints.contains(s.value) => ("pure",s.value)
      case TypeRef(_,s,_) if policy.nonPureConstraints.contains(s.value) => ("non-Pure-constraint",s.value + " " + t)
      case TypeRef(_,s,args) if !seen(s.value) => Option(symbols.get(key(s,path))) match {
        case Some(Info(TypeSignature(params,lo,hi),false,_)) if params.isEmpty && args.isEmpty && lo == hi && hi.nonEmpty => constraint(hi,path,seen + s.value)
        case Some(Info(_: ClassSignature,_,_)) => ("non-Pure-constraint",s.value + " " + t)
        case _ => ("unresolved-constraint",s.value + " " + t)
      }
      case _ => ("unresolved-constraint",t.toString)
    }
    val constraints = ts.flatMap { t =>
      Option(symbols.get(t.symbol)).toList.flatMap(_.signature match {
        case ClassSignature(_,parents,_,_) => parents.collect {
          case TypeRef(_,parent,List(base,rule)) if Set("io/github/iltotore/iron/RefinedType#","io/github/iltotore/iron/RefinedSubtype#")(parent.value) =>
            val (classification,resolved) = constraint(rule,t.path)
            val sourceModule = module(t.path)
            List(t.symbol,t.path,t.line.toString,sourceModule,parent.value,base.toString,rule.toString,classification,resolved,
              if(policy.clientModules.contains(sourceModule) && classification == "non-Pure-constraint") "client-constraint-review" else "inventory")
        }
        case _ => Nil
      })
    }
    write("newtype-constraints.tsv","symbol\tpath\tline\tmodule\tparent\tbase\tconstraint\tclassification\tresolved\tstatus",constraints)
    write("constraint-counts.tsv","module\tscope\tclassification\tcount",constraints.groupBy(r => (r(3),if(r(1).contains("/src/test/")) "test" else "production",r(7))).toList.sortBy(_._1).map {
      case((m,s,c),rows) => List(m,s,c,rows.size.toString)
    })
    write("uses.tsv","target\tpath\tline\towner\tsyntax",us.map(u => u.productIterator.map(_.toString).toList))
    write("sources.tsv","path\tsha256",files.asScala.toList.sorted.map { case(p,h) => List(p,h) })
    val byId = ps.map(p => p.id -> p).toMap
    val roleRows = roles.asScala.toList.sortBy(_.position)
    require(roleRows.map(_.position).toSet == byId.keySet,"Missing primitive-role context")
    require(policy.boundaryOwners.forall(s => roleRows.exists(_.declaration == s)),"Configured boundary is absent; reconcile its identity")
    val roleById = roleRows.map(r => r.position -> r).toMap
    val primitive = ls.filter(_.status == "primitive")
    val unresolved = ls.filter(_.status == "unresolved")
    val grouped = primitive.groupBy(l => (byId(l.position).name,l.symbol)).toList.sortBy(_._1)
    write("proposals.tsv","name\tprimitive\tproposed-type\tstatus\tpositions\texisting-name-candidates\tdecision",grouped.map { case ((name,base),rows) =>
      val proposed = if(name.isEmpty || name == "_") "UNASSIGNED" else name.head.toUpper + name.drop(1)
      val existing = ts.filter(t => t.name.equalsIgnoreCase(proposed) && Set("iron-newtype","opaque","alias","class","case-class")(t.kind)).map(_.symbol).distinct
      List(name,base,proposed,"unreviewed-name-hypothesis",rows.map(_.position).distinct.sorted.mkString("; "),existing.mkString("; "),"Meaning, units, owner, constraints and phantom parameters require review; equal names/types do not prove equivalence")
    })
    val candidates = primitive.filter(l => byId(l.position).scope == "production/member" && byId(l.position).kind != "alias-definition")
    write("position-roles.tsv","position\tcategory\tdeclaration\tenclosing-type\tvisibility",roleRows.map(r => r.productIterator.map(_.toString).toList))
    write("primitive-role-counts.tsv","category\tmodule\tpositions\tprimitive-leaves",primitive.groupBy(l => (roleById(l.position).category,byId(l.position).module)).toList.sortBy(_._1).map {
      case((role,m),rows) => List(role,m,rows.map(_.position).distinct.size.toString,rows.size.toString)
    })
    write("model-contract-candidates.tsv","position\tcategory\tmodule\towner\tname\troute\tprimitive\tstatus",primitive.filter(l => Set("record-field","trait-contract")(roleById(l.position).category)).map { l =>
      val p = byId(l.position)
      List(p.id,roleById(p.id).category,p.module,p.owner,p.name,l.route,l.symbol,"assign-purpose-type-owner-and-layer; not-a-confirmed-violation")
    })
    write("model-candidates.tsv","position\tmodule\tlayer-hint\towner\tname\tkind\troute\tprimitive\tproposed-type\tstatus",candidates.map { l =>
      val p = byId(l.position)
      List(p.id,p.module,policy.layers.getOrElse(p.module,"unclassified"),p.owner,p.name,p.kind,l.route,l.symbol,
        if(p.name.isEmpty || p.name == "_") "UNASSIGNED" else p.name.head.toUpper + p.name.drop(1),"requires-purpose-review")
    })
    write("parameterization.tsv","owner\tparameter\tconstructor-positions\tmember-positions\tstatus",ts.filter(_.parameters.nonEmpty).flatMap { t =>
      t.parameters.split("; ").toList.map { parameter =>
        val occurrences = ls.filter(l => l.status == "type-parameter" && l.symbol == parameter).map(l => byId(l.position)).distinct
        val fields = occurrences.filter(_.kind == "constructor-parameter")
        List(t.symbol,parameter,fields.map(_.id).sorted.mkString("; "),occurrences.map(_.id).sorted.mkString("; "),
          if(fields.isEmpty) "no-constructor-use; review phantom role, parents and members" else "represented-in-constructor")
      }
    })
    write("pagination-review.tsv","position\tname\towner\tprimitive\troute\tdecision",candidates.filter(l =>
      Set("pagesize","page","pagenumber","pageindex","offset","cursor","limit")(byId(l.position).name.toLowerCase)).map { l =>
      val p = byId(l.position)
      List(p.id,p.name,p.owner,l.symbol,l.route,"Decide count/offset/cursor/result meaning; shared vs entity-specific; phantom parameter only when cross-entity use is an error")
    })
    write("resolution-summary.tsv","status\tdetail-kind\tcount",ls.groupBy(l => (l.status,l.detail.takeWhile(_ != ':'))).toList.sortBy(-_._2.size).map { case((s,d),rs) => List(s,d,rs.size.toString) })
    write("unresolved.tsv","position\troute\tsymbol\tdetail",unresolved.map(l => List(l.position,l.route,l.symbol,l.detail)))
    write("unresolved-symbols.tsv","symbol\toccurrences\tpositions\texample",unresolved.groupBy(_.symbol).toList.sortBy(-_._2.size).map { case(s,rows) =>
      List(s,rows.size.toString,rows.map(_.position).distinct.size.toString,rows.head.detail)
    })
    val reviewGroups = candidates.groupBy(l => (byId(l.position).name,l.symbol)).toList.sortBy(g => -g._2.size)
    def md(s: String): String = s.replace("|","\\|").replace("\n"," ")
    Files.write(out.resolve("review.md"),(List("# Primitive concept review queue","",
      "Name/base groups are discovery aids, not approved equivalence classes. Each occurrence still needs a purpose, unit, owner, layer, constraint and generic/phantom decision. Complete rows and evidence are in model-candidates.tsv, positions.tsv, leaves.tsv, types.tsv and uses.tsv.","",
      "Review pagination-review.tsv together with parameterization.tsv. A page number, offset, cursor and page of results are different concepts. Reusing a page size across entities may be intentional; a phantom parameter is warranted only when it prevents a meaningful error.","",
      "## Largest production-member groups (first 80)","","| Name | Base | Leaves | Owners | Modules |","|---|---|---:|---:|---|") ++ reviewGroups.take(80).map { case((name,base),rows) =>
      val members = rows.map(l => byId(l.position))
      s"| ${md(name)} | ${md(base)} | ${rows.size} | ${members.map(_.owner).distinct.size} | ${members.map(_.module).distinct.sorted.map(md).mkString(", ")} |"
    }).asJava,UTF_8)
    val observed = ls.map(l => byId(l.position).name + "|" + byId(l.position).kind + "|" + l.status + "|" + l.symbol).toSet
    policy.expected.foreach(e => require(observed(e),"Missing fixture expectation: " + e))
    policy.forbidden.foreach(e => require(!observed(e),"Forbidden fixture classification: " + e))
    val modules = ps.map(_.module).distinct.sorted
    write("counts.tsv","module\tscope\tpositions\tprimitive-leaves\tunresolved-leaves",modules.flatMap(m => ps.filter(_.module == m).map(_.scope).distinct.sorted.map { scope =>
      val selected = ps.filter(p => p.module == m && p.scope == scope).map(_.id).toSet
      List(m,scope,selected.size.toString,primitive.count(l => selected(l.position)).toString,unresolved.count(l => selected(l.position)).toString)
    }))
    Files.write(out.resolve("summary.md"),List("# Primitive and existing-type inventory","",
      s"${files.size} files; ${modules.size} source modules; ${ps.size} declared/inferred positions; ${primitive.size} primitive leaves in ${primitive.map(_.position).distinct.size} positions; ${unresolved.size} unresolved leaves.",
      s"${ts.count(_.kind == "iron-newtype")} Iron companions; ${ts.count(_.kind == "opaque")} opaque definitions; ${ts.count(_.kind == "alias")} aliases; ${grouped.size} unreviewed name/base proposals.","",
      s"${candidates.size} primitive leaves in ${candidates.map(_.position).distinct.size} production member positions (excluding alias definitions). See model-candidates.tsv; module layer hints are configuration, not verified ownership.","",
      "All main/test sources are scanned; member and local positions are reported separately. Counts include mechanisms and intentional wire/storage primitives: these are inventory candidates, not confirmed violations.",
      "position-roles.tsv distinguishes record fields, trait contracts, other constructors, non-public members, locals, tests and explicitly configured boundaries. model-contract-candidates.tsv prioritizes production record fields and trait contracts without deleting other candidates. Roles describe declaration context, not business meaning or permission to keep a primitive.",
      "Declared and inferred symbol signatures are traversed, including container/function/tuple type arguments and transparent aliases. Opaque and Iron types are recorded as existing newtypes. Abstract types, missing metadata and unsupported semantic forms remain explicit unresolved entries.",
      "newtype-constraints.tsv inventories direct Iron parents, base types and constraint symbols. Pure and known non-Pure identities are configured; transparent parameterless aliases are expanded. Generic/external unresolved constraints remain explicit. Client constraint matches require authority review; inherited newtype definitions still need further analysis.",
      "Uses are resolved symbol references, not whole-program data-flow proofs. Proposal groups share a name and base type only; no equivalence or exemption is approved automatically. Review units, purpose, ownership, constraints, layers and phantom type parameters before migration.",
      "Generated implicit arguments and synthetic declarations are not enumerated as source positions. Scala 3 SemanticDB does not expose complete expression types; dispatch, inferred local widening and implicit conversion flows require further analysis.").asJava,UTF_8)
    Files.writeString(out.resolve("status.txt"),"COMPLETE_INVENTORY_WITH_EXPLICIT_UNRESOLVED\n")
  }
}
