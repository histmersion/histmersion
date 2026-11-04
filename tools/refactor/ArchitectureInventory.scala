import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder,Configured,generic}
import java.nio.file.{Files,Paths}
import java.nio.charset.StandardCharsets.UTF_8
import scala.jdk.CollectionConverters._
import java.util.concurrent.ConcurrentLinkedQueue

object ArchitectureInventory {
  case class Target(symbol: String, scope: String)
  object Target {
    implicit val surface: generic.Surface[Target] = generic.deriveSurface[Target]
    implicit val decoder: ConfDecoder[Target] = generic.deriveDecoder(Target("","")).noTypos
  }
  case class Policy(roots: List[String] = List("modules"), report: String = "refactor-audit/architecture",
    objects: String = "docs/research-domain-model/objects.md", expectedObjects: Int = 645,
    requiredServices: List[String] = Nil, visibilityScopes: Map[String,String] = Map.empty,
    propertyOwners: Map[String,String] = Map.empty,
    dependencyDirections: Map[String,List[String]] = Map.empty, publicContracts: List[String] = Nil,
    crossingPermits: Map[String,List[String]] = Map.empty, projectPackage: String = "com.histmersion",
    invariantModels: Map[String,String] = Map.empty, plainRecords: List[String] = Nil,
    databasePrefixes: List[String] = List("com/histmersion/util/database/", "com/augustnagro/magnum/", "java/sql/", "javax/sql/"), visibilityTargets: List[Target] = Nil)
  object Policy {
    implicit val surface: generic.Surface[Policy] = generic.deriveSurface[Policy]
    implicit val decoder: ConfDecoder[Policy] = generic.deriveDecoder(Policy()).noTypos
  }
  case class Definition(path: String, line: Int, module: String, pkg: String, symbol: String, owner: String,
    name: String, kind: String, mods: String, parents: List[String], signature: String, source: String, ast: String,
    topLevel: Boolean, ctor: String, access: List[(String,String)])
  case class Reference(path: String, line: Int, owner: String, target: String, spelling: String, shape: String, pkg: String)
  def hash(s: String): String = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(UTF_8)).map(b => f"${b & 255}%02x").mkString
  def module(p: String): String = {
    val relative = Paths.get("").toAbsolutePath.normalize.relativize(Paths.get(p).toAbsolutePath.normalize).toString
    relative.stripPrefix("modules/").split("/src/").head
  }
  def sourceFiles(roots: List[String]): Set[String] = roots.flatMap { root =>
    val stream = Files.walk(Paths.get(root).toAbsolutePath.normalize)
    try stream.iterator.asScala.filter(p => Files.isRegularFile(p) && p.toString.endsWith(".scala") &&
      (p.toString.contains("/src/main/") || p.toString.contains("/src/test/"))).map(_.toString).toList finally stream.close()
  }.toSet
  def objectRows(text: String, expected: Int): List[List[String]] = {
    val rows = text.linesIterator.filter(_.startsWith("| ")).map(_.split("\\|",-1).toList.drop(1).dropRight(1).map(_.trim)).toList
    val start = rows.indexWhere(_.headOption.contains("module"))
    require(start >= 0,"Missing object-plan table")
    val entries = rows.drop(start+1).filterNot(_.head.startsWith("---"))
    require(entries.size == expected && entries.forall(r => r.size == 7 && r(3).forall(_.isDigit)),"Malformed/incomplete object plan")
    require(entries.map(_.take(3)).distinct.size == entries.size,"Duplicate object-plan identities")
    entries
  }
}

class ArchitectureInventory(val policy: ArchitectureInventory.Policy) extends SemanticRule("ArchitectureInventory") {
  def this() = this(ArchitectureInventory.Policy())
  import ArchitectureInventory._
  private implicit val dialect: Dialect = dialects.Scala3
  private val visited = new ConcurrentLinkedQueue[(String,String)]()
  private val definitions = new ConcurrentLinkedQueue[Definition]()
  private val references = new ConcurrentLinkedQueue[Reference]()
  private val signatureReferences = new ConcurrentLinkedQueue[Reference]()
  private val aliasTargets = new java.util.concurrent.ConcurrentHashMap[String,List[String]]()
  private val comments = new ConcurrentLinkedQueue[List[String]]()
  private val expressions = new ConcurrentLinkedQueue[List[String]]()
  private val constraintOwners = new ConcurrentLinkedQueue[(String,String)]()
  private case class Construction(path: String, line: Int, owner: String, callee: String, targets: List[String], kind: String, source: String, ast: String, offset: Int)
  private case class Result(method: String, path: String, line: Int, targets: List[String], shape: String)
  private val constructions = new ConcurrentLinkedQueue[Construction]()
  private val results = new ConcurrentLinkedQueue[Result]()
  private val constructorAccess = new java.util.concurrent.ConcurrentHashMap[String,String]()
  private val callResults = new java.util.concurrent.ConcurrentHashMap[String,List[String]]()
  override def withConfiguration(c: Configuration): Configured[Rule] = c.conf.getOrElse("ArchitectureInventory")(Policy()).map { p =>
    require(p.roots.nonEmpty && p.report.nonEmpty)
    require(p.visibilityTargets.map(_.symbol).distinct.size == p.visibilityTargets.size && p.visibilityTargets.forall(t => t.symbol.nonEmpty && t.scope.nonEmpty),"Invalid visibility target list")
    require(p.visibilityTargets.forall(t => p.visibilityScopes.get(t.symbol).forall(_ == t.scope)),"Conflicting visibility scopes")
    Files.createDirectories(Paths.get(p.report)); Files.writeString(Paths.get(p.report,"status.txt"),"RUNNING\n")
    new ArchitectureInventory(p)
  }
  private def ancestors(t: Tree): List[Tree] = t.parent.toList.flatMap(p => p :: ancestors(p))
  override def fix(implicit doc: SemanticDocument): Patch = {
    val path = doc.input match { case Input.File(p,_) => p.toString; case Input.VirtualFile(p,_) => p }
    visited.add(path -> hash(doc.input.text))
    def id(s: Symbol): String = if(s.isLocal) path + "::" + s.value else s.value
    def named(t: Tree): Option[Name] = t match {
      case d: Defn.Class => Some(d.name)
      case d: Defn.Trait => Some(d.name)
      case d: Defn.Object => Some(d.name)
      case d: Defn.Enum => Some(d.name)
      case d: Defn.Type => Some(d.name)
      case d: Defn.Def => Some(d.name)
      case d: Decl.Def => Some(d.name)
      case d: Decl.Type => Some(d.name)
      case d: Defn.Given => Some(d.name)
      case d: Defn.GivenAlias => Some(d.name)
      case d: Decl.Given => Some(d.name)
      case d: Defn.EnumCase => Some(d.name)
      case _ => None
    }
    def owner(t: Tree): String = ancestors(t).flatMap(named).headOption.map(n => id(n.symbol)).getOrElse("")
    def pkg(t: Tree): String = ancestors(t).collect { case p: Pkg => p.ref.syntax }.reverse.mkString(".")
    def typeSymbols(t: SemanticType): List[Symbol] = {
      val here = t match {
        case TypeRef(_,s,_) => List(s)
        case SingleType(_,s) => List(s)
        case _ => Nil
      }
      here ++ t.productIterator.toList.flatMap {
        case child: SemanticType => typeSymbols(child)
        case xs: List[_] => xs.collect { case child: SemanticType => child }.flatMap(typeSymbols)
        case _ => Nil
      }
    }
    def signatureSymbols(s: Signature): List[Symbol] = s match {
      case ValueSignature(t) => typeSymbols(t)
      case MethodSignature(ps,params,result) => typeSymbols(result) ++ (ps ++ params.flatten).flatMap(p => signatureSymbols(p.signature))
      case TypeSignature(ps,lo,hi) => typeSymbols(lo) ++ typeSymbols(hi) ++ ps.flatMap(p => signatureSymbols(p.signature))
      case ClassSignature(ps,bases,self,_) => (bases :+ self).flatMap(typeSymbols) ++ ps.flatMap(p => signatureSymbols(p.signature))
      case _ => Nil
    }
    def recordSignature(t: Tree, n: Name, declaration: String): Unit = n.symbol.info.foreach { info =>
      signatureSymbols(info.signature).filterNot(_.isNone).distinct.foreach { target =>
        signatureReferences.add(Reference(path,t.pos.startLine+1,declaration,id(target),target.displayName,"declared-or-inferred-signature",pkg(t)))
      }
    }
    def rememberCalls(symbol: Symbol): Unit = symbol.info.foreach(_.signature match {
      case ClassSignature(_,_,_,members) =>
        members.foreach(m => m.signature match {
          case MethodSignature(_,_,result) => callResults.put(id(m.symbol),typeSymbols(result).filterNot(_.isNone).map(id).distinct)
          case _ => ()
        })
        val applyTargets = members.filter(_.symbol.displayName == "apply").flatMap(m => Option(callResults.get(id(m.symbol))).toList.flatten).distinct
        if(applyTargets.nonEmpty) callResults.put(id(symbol),applyTargets)
      case MethodSignature(_,_,result) => callResults.put(id(symbol),typeSymbols(result).filterNot(_.isNone).map(id).distinct)
      case _ => ()
    })
    def top(t: Tree): Boolean = ancestors(t).exists(_.is[Source]) && !ancestors(t).exists {
      case _: Defn.Class | _: Defn.Trait | _: Defn.Object | _: Defn.Enum | _: Defn.Def | _: Defn.Given | _: Term.Block => true
      case _ => false
    }
    def add(t: Tree, n: Name, kind: String, mods: List[Mod], template: Option[Template] = None, ctor: String = ""): Unit = {
      def parentSymbol(t: Type): Symbol = t match {
        case a: Type.Apply => parentSymbol(a.tpe)
        case a: Type.Annotate => parentSymbol(a.tpe)
        case _ => t.symbol
      }
      val bases = template.toList.flatMap(_.inits).map(i => parentSymbol(i.tpe)).filterNot(_.isNone).map(id).distinct
      n.symbol.info.foreach(_.signature match {
        case ClassSignature(_,ps,_,_) => ps.foreach {
          case TypeRef(_,parent,List(_,TypeRef(_,constraint,_))) if Set("io/github/iltotore/iron/RefinedType#","io/github/iltotore/iron/RefinedSubtype#")(parent.value) =>
            constraintOwners.add(id(constraint) -> id(n.symbol))
          case _ => ()
        }
        case _ => ()
      })
      definitions.add(Definition(path,t.pos.startLine+1,module(path),pkg(t),id(n.symbol),owner(t),n.value,kind,
        mods.map(_.syntax).mkString(" "),bases,n.symbol.info.fold("<unresolved>")(_.signature.toString),t.pos.text,t.structure,top(t),ctor,
        mods.collect { case m: Mod.Private => "private" -> m.within.syntax; case m: Mod.Protected => "protected" -> m.within.syntax }))
      recordSignature(t,n,id(n.symbol))
      rememberCalls(n.symbol)
      if((kind == "class" || kind == "case-class") && !n.symbol.isLocal && !n.symbol.isNone) rememberCalls(Symbol(n.symbol.value.stripSuffix("#") + "."))
      if(kind == "method" || kind == "abstract-method") n.symbol.info.foreach(_.signature match {
        case MethodSignature(_,_,result) => results.add(Result(id(n.symbol),path,t.pos.startLine+1,typeSymbols(result).filterNot(_.isNone).map(id).distinct,result.toString))
        case _ => ()
      })
      t match {
        case d: Defn.Class =>
          val access = d.ctor.mods.collectFirst {
            case m: Mod.Private => if(m.within.syntax.isEmpty || m.within.syntax == "this") "private" else "qualified-private:" + m.within.syntax
            case m: Mod.Protected => "protected:" + m.within.syntax
          }.getOrElse("public")
          constructorAccess.put(id(n.symbol),access)
        case _ => ()
      }
      if(kind == "alias" && !mods.exists(_.is[Mod.Opaque])) n.symbol.info.foreach(_.signature match {
        case TypeSignature(_,lo,hi) if lo == hi && hi.nonEmpty => aliasTargets.put(id(n.symbol),typeSymbols(hi).filterNot(_.isNone).map(id).distinct)
        case _ => ()
      })
    }
    doc.tree.collect {
      case d: Defn.Class => add(d,d.name,if(d.mods.exists(_.is[Mod.Case])) "case-class" else "class",d.mods,Some(d.templ),d.ctor.syntax)
      case d: Defn.Trait => add(d,d.name,"trait",d.mods,Some(d.templ),d.ctor.syntax)
      case d: Defn.Object => add(d,d.name,"object",d.mods,Some(d.templ))
      case d: Defn.Enum => add(d,d.name,"enum",d.mods,Some(d.templ),d.ctor.syntax)
      case d: Defn.Type => add(d,d.name,"alias",d.mods)
      case d: Decl.Type => add(d,d.name,"abstract-type",d.mods)
      case d: Defn.Def => add(d,d.name,"method",d.mods)
      case d: Decl.Def => add(d,d.name,"abstract-method",d.mods)
      case d: Defn.Val => d.pats.flatMap(_.collect { case v: Pat.Var => v.name }).foreach(n => add(d,n,"val",d.mods))
      case d: Defn.Var => d.pats.flatMap(_.collect { case v: Pat.Var => v.name }).foreach(n => add(d,n,"var",d.mods))
      case d: Defn.GivenAlias => add(d,d.name,"given",d.mods)
      case d: Defn.Given => add(d,d.name,"given-template",d.mods,Some(d.templ))
      case d: Decl.Given => add(d,d.name,"abstract-given",d.mods)
      case d: Defn.EnumCase => add(d,d.name,"enum-case",d.mods,ctor=d.ctor.syntax)
      case d: Defn.RepeatedEnumCase => d.cases.foreach(n => add(d,n,"enum-case",d.mods))
      case d: Decl.Val => d.pats.flatMap(_.collect { case v: Pat.Var => v.name }).foreach(n => add(d,n,"abstract-val",d.mods))
      case d: Decl.Var => d.pats.flatMap(_.collect { case v: Pat.Var => v.name }).foreach(n => add(d,n,"abstract-var",d.mods))
      case p: Term.Param => recordSignature(p,p.name,owner(p))
    }
    doc.tree.collect { case n: Name if n.value.nonEmpty =>
      val isDefinition = named(n.parent.getOrElse(n)).exists(_ eq n) || n.parent.exists {
        case p: Pat.Var => p.name eq n
        case p: Term.Param => p.name eq n
        case p: Type.Param => p.name eq n
        case d: Defn.RepeatedEnumCase => d.cases.exists(_ eq n)
        case _ => false
      }
      if(!isDefinition) references.add(Reference(path,n.pos.startLine+1,owner(n),id(n.symbol),n.value,n.parent.fold(n.structure)(_.productPrefix),pkg(n)))
    }
    doc.tree.tokens.collect { case c: Token.Comment =>
      val text = c.text
      val internal = text.contains("docs/") || text.contains("§") || text.contains("github.com/") && text.contains("/issues/")
      val category = if(internal) "internal-reference-review" else if(text.startsWith("/**") && module(path) == "plugin-api") "plugin-api-scaladoc-review-visibility"
        else "external-reason-or-naming-review"
      comments.add(List(path,(c.pos.startLine+1).toString,category,text))
    }
    def recordInvocation(a: Tree, s: Symbol): Unit = {
        val companionResults = s.info.toList.flatMap(_.signature match {
          case ClassSignature(_,_,_,members) => members.filter(_.symbol.displayName == "apply").flatMap(_.signature match {
            case MethodSignature(_,_,result) => typeSymbols(result).filterNot(_.isNone).map(id)
            case _ => Nil
          })
          case _ => Nil
        }).distinct
        val targets = companionResults ++ s.info.toList.flatMap(_.signature match {
            case MethodSignature(_,_,result) => typeSymbols(result).filterNot(_.isNone).map(id)
            case _ => Nil
          }).distinct
        val kind = if(s.isNone) "unresolved-call"
          else if(companionResults.nonEmpty) "companion-apply-candidates"
          else if(Set("apply","copy")(s.displayName)) s.displayName
          else s.info.map(_.signature) match {
            case Some(_: MethodSignature) => "method-call"
            case Some(_) => "callable-value-or-companion"
            case None => "unresolved-call-metadata"
          }
        constructions.add(Construction(path,a.pos.startLine+1,owner(a),id(s),targets,kind,a.pos.text,a.structure,a.pos.start))
    }
    doc.tree.collect {
      case a: Term.Apply => recordInvocation(a,a.fun.symbol)
      case a: Term.ApplyInfix => recordInvocation(a,a.op.symbol)
      case a: Term.ApplyUnary => recordInvocation(a,a.op.symbol)
      case i: Init =>
        def base(t: Type): Symbol = t match {
          case a: Type.Apply => base(a.tpe)
          case a: Type.Annotate => base(a.tpe)
          case _ => t.symbol
        }
        val target = base(i.tpe)
        constructions.add(Construction(path,i.pos.startLine+1,owner(i),id(i.symbol),List(id(target)).filter(_.nonEmpty),
          if(i.parent.exists(_.is[Term.New])) "new" else "parent-initializer",i.pos.text,i.structure,i.pos.start))
    }
    doc.tree.collect {
      case v: Defn.Val if v.rhs.is[Lit.String] => expressions.add(List("string-constant",path,(v.pos.startLine+1).toString,owner(v),v.pos.text,v.structure))
      case a: Mod.Annot => expressions.add(List("annotation",path,(a.pos.startLine+1).toString,owner(a),a.pos.text,a.structure))
      case i: Init => expressions.add(List("parent-or-construction",path,(i.pos.startLine+1).toString,owner(i),i.pos.text,i.structure))
      case e: Defn.ExtensionGroup => expressions.add(List("extension-group",path,(e.pos.startLine+1).toString,owner(e),e.pos.text,e.structure))
    }
    Patch.empty
  }
  override def afterComplete(): Unit = {
    val out = Paths.get(policy.report); Files.createDirectories(out); Files.writeString(out.resolve("status.txt"),"INCOMPLETE\n")
    require(visited.asScala.map(_._1).toSet == sourceFiles(policy.roots),"Incomplete architecture source coverage")
    visited.asScala.foreach { case(p,h) => require(hash(Files.readString(Paths.get(p))) == h,"Source changed: " + p) }
    val defs = definitions.asScala.toList.distinct.sortBy(d => (d.path,d.line,d.symbol))
    val refs = references.asScala.toList.distinct.sortBy(r => (r.path,r.line,r.target,r.spelling))
    val signatureRefs = signatureReferences.asScala.toList.distinct.sortBy(r => (r.path,r.line,r.target,r.owner))
    val bySymbol = defs.filter(_.symbol.nonEmpty).groupBy(_.symbol)
    def esc(s: String): String = s.replace("\\","\\\\").replace("\t","\\t").replace("\n","\\n").replace("\r","\\r")
    def write(name: String, header: String, rows: List[List[String]]): Unit = Files.write(out.resolve(name),(header :: rows.map(_.map(esc).mkString("\t"))).asJava,UTF_8)
    write("sources.tsv","path\tsha256",visited.asScala.toList.sorted.map { case(p,h) => List(p,h) })
    write("declarations.tsv","path\tline\tmodule\tpackage\tsymbol\towner\tname\tkind\tmodifiers\tparents\tsignature\ttop-level\tconstructor\tsource\tast",
      defs.map(d => List(d.path,d.line.toString,d.module,d.pkg,d.symbol,d.owner,d.name,d.kind,d.mods,d.parents.mkString(";"),d.signature,d.topLevel.toString,d.ctor,d.source,d.ast)))
    val ownershipTypes = defs.filter(d => Set("class","case-class","trait","object","enum","alias")(d.kind))
    policy.propertyOwners.foreach { case(child,parent) =>
      require(ownershipTypes.exists(_.symbol == child),"Configured property is absent: " + child)
      require(ownershipTypes.exists(d => d.symbol == parent && d.kind == "object"),"Configured property owner is not a current object: " + parent)
      require(child != parent,"A property cannot own itself: " + child)
    }
    val ownershipRows = ownershipTypes.map { d =>
      val configured = policy.propertyOwners.get(d.symbol)
      val prefixCandidates = ownershipTypes.filter(p => p.module == d.module && p.pkg == d.pkg && p.owner == d.owner &&
        p.name.length < d.name.length && d.name.startsWith(p.name) && d.name.charAt(p.name.length).isUpper &&
        !d.name.endsWith("Rule")).map(_.symbol).distinct.sorted
      val status = configured.fold(if(d.topLevel) "top-level-owner-review" else "nested-owner-review") { p =>
        if(d.owner == p) "configured-owner-satisfied" else "configured-owner-violation"
      }
      List(d.symbol,d.path,d.line.toString,d.module,d.kind,d.owner,configured.getOrElse(""),status,prefixCandidates.mkString(";"),
        "Prefixes are hypotheses only; components, unrelated names and namespace-only owners require semantic review")
    }
    write("type-ownership.tsv","symbol\tpath\tline\tmodule\tkind\tactual-owner\tconfigured-owner\tstatus\tprefix-candidates\tuncertainty",ownershipRows)
    write("type-ownership-counts.tsv","scope\tmodule\tstatus\tcount",ownershipRows.groupBy(r =>
      (if(r(1).contains("/src/test/")) "test" else "production",r(3),r(7))).toList.sortBy(_._1).map {
        case((scope,module,status),rs) => List(scope,module,status,rs.size.toString)
      })
    val resolvedRefs = refs.map { r =>
      val targets = bySymbol.getOrElse(r.target,Nil)
      List(r.path,r.line.toString,r.owner,r.target,r.spelling,r.shape,
        if(targets.nonEmpty) "project-declaration" else if(r.target.isEmpty) "unresolved" else "external-or-generated",
        targets.map(_.path).distinct.mkString(";"),targets.map(_.pkg).distinct.mkString(";"))
    }
    write("references.tsv","path\tline\towner\ttarget\tspelling\tshape\tresolution\ttarget-files\ttarget-packages",resolvedRefs)
    write("type-references.tsv","path\tline\towner\ttarget\tspelling\tshape\tpackage",signatureRefs.map(r => List(r.path,r.line.toString,r.owner,r.target,r.spelling,r.shape,r.pkg)))
    write("comments.tsv","path\tline\tclassification\tcomment",comments.asScala.toList.sortBy(r => (r.head,r(1).toInt)))
    write("expressions.tsv","family\tpath\tline\towner\tsource\tast",expressions.asScala.toList.sortBy(r => (r.head,r(1),r(2).toInt)))
    val types = defs.filter(d => Set("class","case-class","trait","enum","object","alias","abstract-type")(d.kind))
    val typeIds = types.map(_.symbol).toSet
    def enclosingType(symbol: String, seen: Set[String]): Option[String] =
      if(symbol.isEmpty || seen(symbol)) None else if(typeIds(symbol)) Some(symbol)
      else bySymbol.getOrElse(symbol,Nil).headOption.flatMap(d => enclosingType(d.owner,seen + symbol))
    def expanded(r: Reference, seen: Set[String]): List[Reference] = {
      if(seen(r.target)) Nil else Option(aliasTargets.get(r.target)).toList.flatten.flatMap { target =>
        val next = r.copy(target=target,shape=r.shape + "; alias=" + r.target)
        next :: expanded(next,seen + r.target)
      }
    }
    val directRefs = refs ++ signatureRefs
    val aliasRefs = directRefs.flatMap(r => expanded(r,Set.empty)).distinct
    write("alias-references.tsv","path\tline\towner\ttarget\tevidence\tpackage",aliasRefs.map(r => List(r.path,r.line.toString,r.owner,r.target,r.shape,r.pkg)))
    val allRefs = directRefs ++ aliasRefs
    val classes = defs.filter(d => Set("class","case-class")(d.kind))
    val classIds = classes.map(_.symbol).toSet
    require(policy.invariantModels.keySet.intersect(policy.plainRecords.toSet).isEmpty,"Conflicting model invariant policy")
    require((policy.invariantModels.keySet ++ policy.plainRecords).forall(classIds),"Configured invariant model or plain record is absent")
    require(policy.invariantModels.values.forall(_.trim.nonEmpty),"Invariant policy needs a stated obligation")
    def resultClasses(target: String, seen: Set[String]): List[String] =
      if(seen(target)) Nil else if(classIds(target)) List(target)
      else Option(aliasTargets.get(target)).toList.flatten.flatMap(s => resultClasses(s,seen + target))
    val sites = constructions.asScala.toList.map { c =>
      val recovered = Option(callResults.get(c.callee)).toList.flatten
      c.copy(targets=(c.targets ++ recovered).distinct,
        kind=if(c.targets.isEmpty && recovered.nonEmpty) "project-signature-call-candidate" else c.kind)
    }.distinct.sortBy(c => (c.path,c.offset,c.callee,c.source))
    val siteClasses = sites.map(c => c -> c.targets.flatMap(s => resultClasses(s,Set.empty)).distinct)
    write("construction-sites.tsv","path\tline\towner\tcallee\tkind\tproject-classes\tresult-symbols\tresolution\tsource\tast\toffset",siteClasses.map { case(c,cs) =>
      List(c.path,c.line.toString,c.owner,c.callee,c.kind,cs.mkString(";"),c.targets.mkString(";"),
        if(cs.nonEmpty) "project-model-result-candidate" else if(c.targets.isEmpty) "unresolved" else "no-project-class-resolved",c.source,c.ast,c.offset.toString)
    })
    write("invocation-counts.tsv","scope\tmodule\tkind\tresolution\tcount",siteClasses.groupBy { case(c,cs) =>
      (if(c.path.contains("/src/test/")) "test" else "production",module(c.path),c.kind,
        if(cs.nonEmpty) "project-model-result-candidate" else if(c.targets.isEmpty) "unresolved" else "no-project-class-resolved")
    }.toList.sortBy(_._1).map { case((scope,module,kind,resolution),rs) => List(scope,module,kind,resolution,rs.size.toString) })
    val returns = results.asScala.toList.distinct
    val constructedByOwner = siteClasses.groupBy(_._1.owner).view.mapValues(_.flatMap(_._2).distinct).toMap
    val constructionCounts = siteClasses.flatMap(_._2).groupMapReduce(identity)(_ => 1)(_ + _)
    val factoryRows = returns.flatMap { r =>
      val constructed = constructedByOwner.getOrElse(r.method,Nil)
      r.targets.flatMap(s => resultClasses(s,Set.empty)).distinct.map { model =>
        List(model,r.method,r.path,r.line.toString,r.shape,constructed.mkString(";"),
          if(constructed.contains(model)) "returns-model-and-has-model-result-call; invariant-unproven" else "returns-model; delegation-or-non-factory-review")
      }
    }.sortBy(r => (r.head,r(2),r(3).toInt,r(1)))
    write("factory-candidates.tsv","model\tmethod\tpath\tline\tresult-type\tmodel-result-call-candidates\tstatus",factoryRows)
    val factoryCounts = factoryRows.groupMapReduce(_.head)(_ => 1)(_ + _)
    val constructionRows = classes.map { d =>
      val access = Option(constructorAccess.get(d.symbol)).getOrElse("unresolved")
      val obligation = policy.invariantModels.get(d.symbol)
      val status = if(policy.plainRecords.contains(d.symbol)) "configured-no-aggregate-invariant"
        else if(obligation.nonEmpty && access == "public") "required-invariant; public-constructor-review"
        else if(obligation.nonEmpty) "required-invariant; preservation-unproven"
        else "invariant-obligation-review"
      List(d.symbol,d.path,d.line.toString,d.module,d.kind,access,d.mods,obligation.getOrElse(""),status,
        constructionCounts.getOrElse(d.symbol,0).toString,factoryCounts.getOrElse(d.symbol,0).toString)
    }
    write("model-construction.tsv","model\tpath\tline\tmodule\tkind\tconstructor-access\tclass-modifiers\tinvariant\tstatus\tmodel-result-call-candidates\tfactory-candidates",constructionRows)
    write("model-construction-counts.tsv","scope\tmodule\tconstructor-access\tstatus\tcount",constructionRows.groupBy(r =>
      (if(r(1).contains("/src/test/")) "test" else "production",r(3),r(5),r(8))).toList.sortBy(_._1).map {
        case((scope,module,access,status),rs) => List(scope,module,access,status,rs.size.toString)
      })
    val visibilityScopes = policy.visibilityScopes ++ policy.visibilityTargets.map(t => t.symbol -> t.scope)
    val databaseOwners = allRefs.filter(r => policy.databasePrefixes.exists(r.target.startsWith)).flatMap(r => enclosingType(r.owner,Set.empty)).toSet
    require(visibilityScopes.keys.forall(bySymbol.contains),"Missing configured visibility target")
    val privateCandidates = types.filter(d => d.path.contains("/src/main/") && !d.symbol.contains("::local") && d.symbol.nonEmpty &&
      (d.name.endsWith("Repo") || d.name.endsWith("Row") || d.name == "Implementation" || databaseOwners(d.symbol) || visibilityScopes.contains(d.symbol)))
    def enclosing(d: Definition, seen: Set[String]): List[Definition] =
      if(d.owner.isEmpty || seen(d.owner)) Nil else bySymbol.getOrElse(d.owner,Nil).headOption.toList.flatMap(p => p :: enclosing(p,seen + d.owner))
    def restriction(d: Definition, qualifier: String): (String,String) = {
      if(qualifier.isEmpty || qualifier == "this") {
        if(d.owner.isEmpty) "package" -> d.pkg else "owner" -> d.owner
      } else {
        val owners = (d :: enclosing(d,Set.empty)).find(_.name == qualifier)
        owners.map(o => "owner" -> o.symbol).getOrElse {
          val segments = d.pkg.split("\\.").toList
          val index = segments.lastIndexOf(qualifier)
          if(index >= 0) "package" -> segments.take(index+1).mkString(".") else "unresolved" -> qualifier
        }
      }
    }
    val visibilityRows = privateCandidates.map { d =>
      val expected = visibilityScopes.getOrElse(d.symbol,d.pkg)
      val bounds = (d :: enclosing(d,Set.empty)).flatMap(x => x.access.map { case(kind,q) => (kind,restriction(x,q),x.symbol) })
      val narrow = bounds.exists { case(kind,(scope,value),_) => kind == "private" &&
        (scope == "owner" || scope == "package" && (value == expected || value.startsWith(expected + "."))) }
      val status = if(d.pkg != expected && !d.pkg.startsWith(expected + ".")) "declared-outside-expected-feature"
        else if(narrow) "feature-restricted" else if(bounds.exists(_._2._1 == "unresolved")) "visibility-unresolved"
        else if(bounds.exists(_._1 == "protected")) "protected; review-subclass-access"
        else if(bounds.nonEmpty) "broader-than-feature" else "public"
      val reason = List(if(d.name.endsWith("Repo")) "repository-name" else "",if(d.name.endsWith("Row")) "row-name" else "",
        if(d.name == "Implementation") "implementation-name" else "",if(databaseOwners(d.symbol)) "database-dependency" else "",
        if(visibilityScopes.contains(d.symbol)) "configured-owner" else "").filter(_.nonEmpty)
      List(d.symbol,d.path,d.line.toString,d.module,d.pkg,expected,d.kind,reason.mkString(";"),status,
        bounds.map { case(k,(s,v),o) => s"$o:$k:$s:$v" }.mkString(";"),
        if(visibilityScopes.contains(d.symbol)) "configured-scope" else "candidate-role-and-feature-scope-review")
    }
    write("visibility-candidates.tsv","symbol\tpath\tline\tmodule\tpackage\texpected-scope\tkind\treasons\teffective-visibility\taccess-evidence\tpolicy-status",visibilityRows)
    val namespaceCache = scala.collection.mutable.Map.empty[String,String]
    def namespace(raw: String): String = namespaceCache.getOrElseUpdate(raw, {
      if(raw.isEmpty || raw.contains("::local")) "" else {
        var symbol = Symbol(raw)
        while(!symbol.isNone && !symbol.value.endsWith("/")) symbol = symbol.owner
        symbol.value
      }
    })
    val refsByNamespace = allRefs.groupBy(r => namespace(r.target))
    require(policy.publicContracts.forall(bySymbol.contains),"Missing configured public contract")
    require(policy.crossingPermits.forall { case(owner,targets) => bySymbol.contains(owner) && targets.nonEmpty && targets.forall(bySymbol.contains) },"Missing configured crossing permit owner or target")
    val regions = policy.dependencyDirections.keySet
    require(policy.dependencyDirections.values.flatten.forall(regions),"Unknown permitted dependency region")
    def region(pkg: String): Option[String] = regions.filter(p => pkg == p || pkg.startsWith(p + ".")).toList.sortBy(p => -p.length).headOption
    val contractCache = scala.collection.mutable.Map.empty[String,Boolean]
    def contract(target: String): Boolean = contractCache.getOrElseUpdate(target, {
      var current = Symbol(target)
      var approved = false
      while(!current.isNone && !approved) {
        approved = policy.publicContracts.contains(current.value)
        current = current.owner
      }
      approved
    })
    val crossingRows = allRefs.flatMap { r =>
      val targetPackage = namespace(r.target).stripSuffix("/").replace('/','.')
      val projectTarget = targetPackage == policy.projectPackage || targetPackage.startsWith(policy.projectPackage + ".")
      if(r.target.nonEmpty && (!projectTarget || r.pkg == targetPackage)) Nil else {
        val from = region(r.pkg)
        val to = region(targetPackage)
        val direction = if(r.target.isEmpty || r.pkg.isEmpty) "unresolved-reference"
          else if(from.isEmpty || to.isEmpty) "unclassified-direction"
          else if(from == to) "same-region; feature-contract-review"
          else if(policy.dependencyDirections(from.get).contains(to.get)) "allowed-direction; contract-review"
          else "forbidden-direction"
        val permit = policy.crossingPermits.getOrElse(r.owner,Nil).contains(r.target)
        val approved = r.target.nonEmpty && contract(r.target)
        val status = if(permit) "configured-crossing-permit"
          else if(direction == "forbidden-direction" || direction == "unresolved-reference") direction
          else if(approved) "configured-contract; " + direction.stripSuffix("; contract-review").stripSuffix("; feature-contract-review")
          else direction
        List(List(r.path,r.line.toString,if(r.path.contains("/src/test/")) "test" else "production",r.pkg,targetPackage,
          r.owner,r.target,r.shape,from.getOrElse(""),to.getOrElse(""),status,module(r.path)))
      }
    }.distinct.sortBy(r => (r.head,r(1).toInt,r(6),r(7)))
    write("dependency-crossings.tsv","path\tline\tscope\tfrom-package\tto-package\towner\ttarget\tevidence-kind\tfrom-region\tto-region\tstatus\tmodule",crossingRows)
    write("dependency-crossing-counts.tsv","scope\tmodule\tfrom-region\tto-region\tstatus\tcount",crossingRows.groupBy(r => (r(2),r(11),r(8),r(9),r(10))).toList.sortBy(_._1).map {
      case((scope,module,from,to,status),rs) => List(scope,module,from,to,status,rs.size.toString)
    })
    val consumerRows = privateCandidates.flatMap { d =>
      val expected = visibilityScopes.getOrElse(d.symbol,d.pkg)
      refsByNamespace.getOrElse(namespace(d.symbol),Nil).filter(_.target.startsWith(d.symbol)).map { r =>
        val inside = r.pkg == expected || r.pkg.startsWith(expected + ".")
        List(d.symbol,r.path,r.line.toString,if(r.path.contains("/src/test/")) "test" else "production",r.pkg,r.owner,r.target,r.shape,
          if(r.pkg.isEmpty) "consumer-package-unresolved" else if(inside) "inside-expected-feature" else "outside-expected-feature")
      }
    }.distinct.sortBy(r => (r.head,r(1),r(2).toInt,r(6),r(7)))
    write("visibility-consumers.tsv","candidate\tpath\tline\tscope\tpackage\towner\ttarget\tevidence-kind\tclassification",consumerRows)
    write("visibility-counts.tsv","category\tclassification\tcount",
      visibilityRows.groupBy(_(8)).toList.sortBy(_._1).map { case(s,rs) => List("candidate",s,rs.size.toString) } ++
      consumerRows.groupBy(r => (r(3),r(8))).toList.sortBy(_._1).map { case((scope,s),rs) => List("consumer-" + scope,s,rs.size.toString) })
    val services = types.filter(d => d.name.endsWith("Service") || d.name.endsWith("Repo") || d.name.endsWith("Controllers") || policy.requiredServices.contains(d.symbol))
    val serviceRows = services.map { d =>
      val impls = types.filter(_.parents.contains(d.symbol))
      val methods = defs.filter(x => x.owner == d.symbol && Set("method","abstract-method")(x.kind))
      val consumers = refs.filter(_.target == d.symbol)
      List(d.symbol,d.path,d.kind,d.mods,d.ctor,methods.map(_.symbol).mkString(";"),impls.map(_.symbol).mkString(";"),
        impls.filter(_.name == "Implementation").map(_.symbol).mkString(";"),impls.filter(_.name == "InMemory").map(_.symbol).mkString(";"),
        consumers.filter(_.path.contains("/src/test/")).map(_.path).distinct.sorted.mkString(";"),"structural-evidence; behavioral-parity-unproven")
    }
    write("services.tsv","symbol\tpath\tkind\tvisibility\tconstructor\tmethods\tdirect-implementations\tnamed-Implementation\tnamed-InMemory\ttest-consumers\tstatus",serviceRows)
    require(policy.requiredServices.forall(bySymbol.contains),"Configured service is absent; reconcile its destination instead of accepting zero findings")
    val serviceTypes = services.filter(d => d.path.contains("/src/main/") && d.kind != "object")
    def inherits(d: Definition, target: String, seen: Set[String] = Set.empty): Boolean =
      d.parents.contains(target) || d.parents.filterNot(seen).exists(p => bySymbol.getOrElse(p,Nil).exists(x => inherits(x,target,seen + p)))
    val obligations = serviceTypes.filter(d => d.name.endsWith("Service") || policy.requiredServices.contains(d.symbol)).flatMap { service =>
      val companion = service.symbol.stripSuffix("#") + "."
      val implementations = types.filter(d => d.symbol != service.symbol && inherits(d,service.symbol))
      val production = implementations.filter(_.path.contains("/src/main/"))
      val nested = production.filter(_.owner == companion)
      val actual = nested.filter(_.name == "Implementation")
      val memory = nested.filter(_.name == "InMemory")
      val required = policy.requiredServices.contains(service.symbol)
      def row(check: String, satisfied: Boolean, evidence: List[String], uncertainty: String = ""): List[String] =
        List(service.symbol,service.path,check,if(satisfied) "structural-evidence-present" else if(required) "required-shape-missing" else "candidate-gap; confirm-service-role",
          evidence.mkString(";"),uncertainty)
      List(
        row("service-trait",service.kind == "trait",List(service.kind)),
        row("companion-Implementation",actual.nonEmpty,actual.map(_.symbol),"Inherited aliases and unresolved parent symbols need review"),
        row("private-Implementation",actual.nonEmpty && actual.forall(_.mods.split(" ").exists(m => m == "private" || m == "private[" + service.name + "]")),actual.map(d => d.symbol + " " + d.mods),"Accepts lexical or explicitly companion-qualified private; broader package-private requires review"),
        row("companion-InMemory",memory.nonEmpty,memory.map(_.symbol),"A same-named unrelated class does not count"),
        List(service.symbol,service.path,"behavioral-parity","unproven; contract-suite-evidence-required",
          refs.filter(r => r.target == service.symbol && r.path.contains("/src/test/")).map(_.path).distinct.sorted.mkString(";"),
          "Test references establish consumers, not equivalent behavior"),
        List(service.symbol,service.path,"other-implementations","inventory",production.filterNot(d => nested.contains(d)).map(_.symbol).mkString(";"),"Includes indirect resolved inheritance"))
    }
    write("service-obligations.tsv","service\tpath\tcheck\tstatus\tevidence\tuncertainty",obligations)
    val serviceCounts = obligations.groupBy(r => (r(2),r(3))).toList.sortBy(_._1)
    write("service-counts.tsv","check\tstatus\tcount",serviceCounts.map { case((c,s),rs) => List(c,s,rs.size.toString) })
    val plan = if(policy.objects.isEmpty) Nil else objectRows(Files.readString(Paths.get(policy.objects)),policy.expectedObjects)
    val objects = types.filter(d => d.kind == "object" && d.topLevel && d.path.contains("/src/main/"))
    val planRows = plan.map { row =>
      val p = "com.histmersion." + row(1).stripPrefix("`").stripSuffix("`")
      val n = row(2).stripPrefix("`").stripSuffix("`")
      val exact = objects.filter(d => d.module == row.head && d.pkg == p && d.name == n)
      val sameName = types.filter(d => d.name == n && d.path.contains("/src/main/"))
      val status = if(exact.nonEmpty) "original-object-present; destination-review" else if(sameName.nonEmpty) "same-name-candidates; relocation-unproven" else "original-absent; destination-proof-required"
      row ++ List(status,exact.map(_.symbol).mkString(";"),sameName.map(d => d.symbol + "@" + d.path).mkString(";"))
    }
    write("object-plan.tsv","module\told-package\told-object\told-lines\told-kind\tdestination\treason\tstatus\texact-current\tsame-name-candidates",planRows)
    write("objects.tsv","symbol\tpath\tcompanion-types\tmethods\tstatus",objects.map { d =>
      val companions = types.filter(x => x.symbol != d.symbol && x.pkg == d.pkg && x.owner == d.owner && x.name == d.name && x.kind != "object")
      List(d.symbol,d.path,companions.map(_.symbol).mkString(";"),defs.filter(x => x.owner == d.symbol && x.kind == "method").map(_.symbol).mkString(";"),
        if(companions.nonEmpty) "companion; operation-ownership-review" else "standalone; operation-ownership-review")
    })
    val topTypes = types.filter(_.topLevel)
    val constraints = constraintOwners.asScala.toSet
    val fileGroups = topTypes.groupBy(_.path).toList.sortBy(_._1).map { case(p,ds) =>
      val groups = scala.collection.mutable.Map(ds.map(d => d.name -> d.name): _*)
      val evidence = scala.collection.mutable.ListBuffer.empty[String]
      def root(name: String): String = if(groups(name) == name) name else root(groups(name))
      def join(a: String,b: String,reason: String): Unit = {
        if(root(a) != root(b)) groups(root(a)) = root(b)
        evidence += reason
      }
      ds.filter(_.mods.split(" ").contains("sealed")).foreach { base =>
        ds.filter(d => d.symbol != base.symbol && inherits(d,base.symbol)).foreach { child =>
          join(child.name,base.name,"sealed-parent:" + child.symbol + "->" + base.symbol)
        }
      }
      ds.filter(d => d.kind == "alias" && d.name.endsWith("Rule")).foreach { rule =>
        ds.filter(d => d.name == rule.name.stripSuffix("Rule") && constraints(rule.symbol -> d.symbol)).foreach { owner =>
          join(rule.name,owner.name,"Iron-constraint:" + rule.symbol + "->" + owner.symbol)
        }
      }
      val logical = groups.keys.toList.groupBy(root).toList.sortBy(_._1).map { case(_,names) => names.sorted.mkString("+") }
      List(p,ds.map(_.name).distinct.sorted.mkString(";"),ds.map(d => d.name + ":" + d.kind).mkString(";"),
        if(logical.size <= 1) "single-logical-group" else "multiple-logical-groups; review-tightly-coupled-helpers",logical.mkString(";"),evidence.sorted.mkString(";"))
    }
    write("file-groups.tsv","path\ttop-level-names\tkinds\tstatus\tlogical-groups\tgrouping-evidence",fileGroups)
    write("file-group-counts.tsv","scope\tstatus\tcount",fileGroups.groupBy(r => (if(r.head.contains("/src/test/")) "test" else "production",r(3))).toList.sortBy(_._1).map {
      case((s,c),rs) => List(s,c,rs.size.toString)
    })
    val counts = List("files" -> visited.size,"declarations" -> defs.size,"references" -> refs.size,"unresolved-references" -> refs.count(_.target.isEmpty),
      "signature-type-references" -> signatureRefs.size,"alias-expanded-references" -> aliasRefs.size,
      "visibility-candidates" -> privateCandidates.size,"visibility-consumer-relationships" -> consumerRows.size,
      "services-repositories-controllers" -> services.size,"comments" -> comments.size,"top-level-objects" -> objects.size,"object-plan-rows" -> plan.size)
    write("counts.tsv","family\tcount",counts.map { case(k,n) => List(k,n.toString) })
    Files.write(out.resolve("summary.md"),(List("# Architecture evidence inventory","",
      "Full source and test coverage. Candidate inventories preserve declarations, signatures, constructors, ASTs, references and source hashes. Counts are evidence rows, not confirmed policy violations.","") ++
      counts.map { case(k,n) => s"- $k: $n" } ++ List("",
      "Service implementations are linked through resolved parent symbols; a class called InMemory elsewhere is not evidence of service parity. Inherited methods, indirect implementations and behavioral parity require further analysis. Unresolved, generated and external references are retained explicitly.",
      "service-obligations.tsv checks trait shape, companion Implementation/InMemory and private Implementation through resolved transitive inheritance. Required service identities come from configuration; disappearance fails the scan. Other Service names are candidates requiring role confirmation. Behavioral parity is always separately unproven by structural evidence.",
      "visibility-candidates.tsv includes configured owners, Repo/Row/Implementation names and owners with database dependencies. Effective visibility includes enclosing owners. visibility-consumers.tsv includes source names, declared/inferred signatures and transparent aliases; opaque representations are not expanded. Automatic candidates require role/scope confirmation. External aliases, implicit arguments and generated typeclass instances remain coverage gaps. Counts are relationships, not independent violations.",
      "Every row of the object plan is reconciled. An absent original object is never marked completed: destination and behavior still need proof. Same-name candidates do not establish conceptual equivalence.",
      "Comments, string constants, annotations and construction ASTs are review inputs. File groups combine same-named companions/aliases, resolved sealed hierarchies and named Iron constraints proven by parent type arguments. Remaining multiple groups require tightly-coupled-helper review; naming alone grants no constraint exception. Visibility, transaction ownership, invariants and layer crossings require the corresponding policy checks.")).asJava,UTF_8)
    Files.writeString(out.resolve("status.txt"),"COMPLETE_ARCHITECTURE_EVIDENCE_WITH_REVIEW_CASES\n")
  }
}
