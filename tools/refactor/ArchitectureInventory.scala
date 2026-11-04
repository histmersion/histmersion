import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder,Configured,generic}
import java.nio.file.{Files,Paths}
import java.nio.charset.StandardCharsets.UTF_8
import scala.jdk.CollectionConverters._
import java.util.concurrent.ConcurrentLinkedQueue

object ArchitectureInventory {
  case class Policy(roots: List[String] = List("modules"), report: String = "refactor-audit/architecture",
    objects: String = "docs/research-domain-model/objects.md", expectedObjects: Int = 645)
  object Policy {
    implicit val surface: generic.Surface[Policy] = generic.deriveSurface[Policy]
    implicit val decoder: ConfDecoder[Policy] = generic.deriveDecoder(Policy()).noTypos
  }
  case class Definition(path: String, line: Int, module: String, pkg: String, symbol: String, owner: String,
    name: String, kind: String, mods: String, parents: List[String], signature: String, source: String, ast: String,
    topLevel: Boolean, ctor: String)
  case class Reference(path: String, line: Int, owner: String, target: String, spelling: String, shape: String)
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
  private val comments = new ConcurrentLinkedQueue[List[String]]()
  private val expressions = new ConcurrentLinkedQueue[List[String]]()
  override def withConfiguration(c: Configuration): Configured[Rule] = c.conf.getOrElse("ArchitectureInventory")(Policy()).map { p =>
    require(p.roots.nonEmpty && p.report.nonEmpty)
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
      definitions.add(Definition(path,t.pos.startLine+1,module(path),pkg(t),id(n.symbol),owner(t),n.value,kind,
        mods.map(_.syntax).mkString(" "),bases,n.symbol.info.fold("<unresolved>")(_.signature.toString),t.pos.text,t.structure,top(t),ctor))
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
    }
    doc.tree.collect { case n: Name if n.value.nonEmpty =>
      val isDefinition = named(n.parent.getOrElse(n)).exists(_ eq n) || n.parent.exists {
        case p: Pat.Var => p.name eq n
        case p: Term.Param => p.name eq n
        case p: Type.Param => p.name eq n
        case d: Defn.RepeatedEnumCase => d.cases.exists(_ eq n)
        case _ => false
      }
      if(!isDefinition) references.add(Reference(path,n.pos.startLine+1,owner(n),id(n.symbol),n.value,n.parent.fold(n.structure)(_.productPrefix)))
    }
    doc.tree.tokens.collect { case c: Token.Comment =>
      val text = c.text
      val internal = text.contains("docs/") || text.contains("§") || text.contains("github.com/") && text.contains("/issues/")
      val category = if(internal) "internal-reference-review" else if(text.startsWith("/**") && module(path) == "plugin-api") "plugin-api-scaladoc-review-visibility"
        else "external-reason-or-naming-review"
      comments.add(List(path,(c.pos.startLine+1).toString,category,text))
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
    val bySymbol = defs.filter(_.symbol.nonEmpty).groupBy(_.symbol)
    def esc(s: String): String = s.replace("\\","\\\\").replace("\t","\\t").replace("\n","\\n").replace("\r","\\r")
    def write(name: String, header: String, rows: List[List[String]]): Unit = Files.write(out.resolve(name),(header :: rows.map(_.map(esc).mkString("\t"))).asJava,UTF_8)
    write("sources.tsv","path\tsha256",visited.asScala.toList.sorted.map { case(p,h) => List(p,h) })
    write("declarations.tsv","path\tline\tmodule\tpackage\tsymbol\towner\tname\tkind\tmodifiers\tparents\tsignature\ttop-level\tconstructor\tsource\tast",
      defs.map(d => List(d.path,d.line.toString,d.module,d.pkg,d.symbol,d.owner,d.name,d.kind,d.mods,d.parents.mkString(";"),d.signature,d.topLevel.toString,d.ctor,d.source,d.ast)))
    val resolvedRefs = refs.map { r =>
      val targets = bySymbol.getOrElse(r.target,Nil)
      List(r.path,r.line.toString,r.owner,r.target,r.spelling,r.shape,
        if(targets.nonEmpty) "project-declaration" else if(r.target.isEmpty) "unresolved" else "external-or-generated",
        targets.map(_.path).distinct.mkString(";"),targets.map(_.pkg).distinct.mkString(";"))
    }
    write("references.tsv","path\tline\towner\ttarget\tspelling\tshape\tresolution\ttarget-files\ttarget-packages",resolvedRefs)
    write("comments.tsv","path\tline\tclassification\tcomment",comments.asScala.toList.sortBy(r => (r.head,r(1).toInt)))
    write("expressions.tsv","family\tpath\tline\towner\tsource\tast",expressions.asScala.toList.sortBy(r => (r.head,r(1),r(2).toInt)))
    val types = defs.filter(d => Set("class","case-class","trait","enum","object","alias","abstract-type")(d.kind))
    val services = types.filter(d => d.name.endsWith("Service") || d.name.endsWith("Repo") || d.name.endsWith("Controllers"))
    val serviceRows = services.map { d =>
      val impls = types.filter(_.parents.contains(d.symbol))
      val methods = defs.filter(x => x.owner == d.symbol && Set("method","abstract-method")(x.kind))
      val consumers = refs.filter(_.target == d.symbol)
      List(d.symbol,d.path,d.kind,d.mods,d.ctor,methods.map(_.symbol).mkString(";"),impls.map(_.symbol).mkString(";"),
        impls.filter(_.name == "Implementation").map(_.symbol).mkString(";"),impls.filter(_.name == "InMemory").map(_.symbol).mkString(";"),
        consumers.filter(_.path.contains("/src/test/")).map(_.path).distinct.sorted.mkString(";"),"structural-evidence; behavioral-parity-unproven")
    }
    write("services.tsv","symbol\tpath\tkind\tvisibility\tconstructor\tmethods\tdirect-implementations\tnamed-Implementation\tnamed-InMemory\ttest-consumers\tstatus",serviceRows)
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
    write("file-groups.tsv","path\ttop-level-names\tkinds\tstatus",topTypes.groupBy(_.path).toList.sortBy(_._1).map { case(p,ds) =>
      List(p,ds.map(_.name).distinct.sorted.mkString(";"),ds.map(d => d.name + ":" + d.kind).mkString(";"),
        if(ds.map(_.name).distinct.size <= 1) "single-name-group" else "review-alias-constraints-sealed-hierarchy-or-unrelated")
    })
    val counts = List("files" -> visited.size,"declarations" -> defs.size,"references" -> refs.size,"unresolved-references" -> refs.count(_.target.isEmpty),
      "services-repositories-controllers" -> services.size,"comments" -> comments.size,"top-level-objects" -> objects.size,"object-plan-rows" -> plan.size)
    write("counts.tsv","family\tcount",counts.map { case(k,n) => List(k,n.toString) })
    Files.write(out.resolve("summary.md"),(List("# Architecture evidence inventory","",
      "Full source and test coverage. Candidate inventories preserve declarations, signatures, constructors, ASTs, references and source hashes. Counts are evidence rows, not confirmed policy violations.","") ++
      counts.map { case(k,n) => s"- $k: $n" } ++ List("",
      "Service implementations are linked through resolved parent symbols; a class called InMemory elsewhere is not evidence of service parity. Inherited methods, indirect implementations and behavioral parity require further analysis. Unresolved, generated and external references are retained explicitly.",
      "Every row of the object plan is reconciled. An absent original object is never marked completed: destination and behavior still need proof. Same-name candidates do not establish conceptual equivalence.",
      "Comments, string constants, annotations and construction ASTs are review inputs. Multiple file names need companion/alias/sealed-hierarchy grouping before being classified as violations. Visibility, transaction ownership, invariants and layer crossings require the corresponding policy checks.")).asJava,UTF_8)
    Files.writeString(out.resolve("status.txt"),"COMPLETE_ARCHITECTURE_EVIDENCE_WITH_REVIEW_CASES\n")
  }
}
