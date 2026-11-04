import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder,Configured,generic}
import java.nio.file.{Files,Paths}
import java.nio.charset.StandardCharsets.UTF_8
import scala.jdk.CollectionConverters._
import java.util.concurrent.ConcurrentLinkedQueue

object OperationInventory {
  case class Policy(roots: List[String] = List("modules"), report: String = "refactor-audit/operations", expected: List[String] = Nil)
  object Policy {
    implicit val surface: generic.Surface[Policy] = generic.deriveSurface[Policy]
    implicit val decoder: ConfDecoder[Policy] = generic.deriveDecoder(Policy()).noTypos
  }
  case class Candidate(family: String, path: String, line: Int, owner: String, symbol: String, symbolOwner: String,
    receiver: String, receiverType: String, source: String, ast: String, allocations: List[String])
  def files(roots: List[String]): Set[String] = roots.flatMap { r =>
    val stream = Files.walk(Paths.get(r).toAbsolutePath.normalize)
    try stream.iterator.asScala.filter(p => Files.isRegularFile(p) && p.toString.endsWith(".scala") &&
      (p.toString.contains("/src/main/") || p.toString.contains("/src/test/"))).map(_.toString).toList finally stream.close()
  }.toSet
  def hash(s: String): String = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(UTF_8)).map(b => f"${b & 255}%02x").mkString
}
class OperationInventory(val policy: OperationInventory.Policy) extends SemanticRule("OperationInventory") {
  def this() = this(OperationInventory.Policy())
  import OperationInventory._
  private implicit val dialect: Dialect = dialects.Scala3
  private val visited = new ConcurrentLinkedQueue[(String,String)]()
  private val candidates = new ConcurrentLinkedQueue[Candidate]()
  private val cases = java.util.concurrent.ConcurrentHashMap.newKeySet[String]()
  private val declared = java.util.concurrent.ConcurrentHashMap.newKeySet[String]()
  override def withConfiguration(c: Configuration): Configured[Rule] = c.conf.getOrElse("OperationInventory")(Policy()).andThen { p =>
    if(p.roots.isEmpty || p.report.isEmpty) Configured.error("Require roots and report") else {
      Files.createDirectories(Paths.get(p.report)); Files.writeString(Paths.get(p.report,"status.txt"),"RUNNING\n")
      Configured.ok(new OperationInventory(p))
    }
  }
  private def parents(t: Tree): List[Tree] = t.parent.toList.flatMap(p => p :: parents(p))
  private def outer(t: Tree): Tree = t.parent match {
    case Some(a: Term.Apply) if a.fun eq t => outer(a)
    case Some(a: Term.ApplyType) if a.fun eq t => outer(a)
    case _ => t
  }
  private def clauses(t: Tree): List[List[Term]] = t match {
    case a: Term.Apply => clauses(a.fun) :+ a.argClause.values
    case a: Term.ApplyType => clauses(a.fun)
    case a: Term.ApplyInfix => List(a.argClause.values)
    case _ => Nil
  }
  override def fix(implicit doc: SemanticDocument): Patch = {
    val path = doc.input match { case Input.File(p,_) => p.toString; case Input.VirtualFile(p,_) => p }
    visited.add(path -> hash(doc.input.text))
    def scoped(s: Symbol): String = if(s.isLocal) path + "::" + s.value else s.value
    def owner(t: Tree): String = parents(t).collectFirst {
      case d: Defn.Def => scoped(d.name.symbol)
      case d: Defn.Class => scoped(d.name.symbol)
      case d: Defn.Object => scoped(d.name.symbol)
      case d: Defn.Trait => scoped(d.name.symbol)
    }.getOrElse("")
    doc.tree.collect {
      case c: Defn.Class =>
        declared.add(scoped(c.name.symbol))
        if(c.mods.exists(_.is[Mod.Case])) cases.add(scoped(c.name.symbol))
      case c: Defn.Trait => declared.add(scoped(c.name.symbol))
      case c: Defn.Enum => declared.add(scoped(c.name.symbol))
    }
    doc.tree.collect { case n: Name =>
      val selected = n.parent match {
        case Some(s: Term.Select) if s.name eq n => Some(outer(s))
        case Some(a: Term.Apply) if a.fun eq n => Some(outer(a))
        case Some(a: Term.ApplyType) if a.fun eq n => Some(outer(a))
        case Some(a: Term.ApplyInfix) if a.op eq n => Some(outer(a))
        case _ if n.symbol.info.exists(_.isMethod) && !n.parent.exists {
          case d: Defn.Def => d.name eq n
          case d: Decl.Def => d.name eq n
          case p: Term.Param => p.name eq n
          case p: Pat.Var => p.name eq n
          case _ => false
        } => Some(n)
        case _ => None
      }
      val semanticName = if(n.symbol.isNone) "" else n.symbol.displayName
      val name = if(Set("value","copy","foldLeft","foldRight","fold","derived")(semanticName)) semanticName else n.value
      val family = name match {
        case "value" => "unwrap"
        case "copy" => "copy"
        case "foldLeft" | "foldRight" | "fold" => "fold"
        case "derived" => "derivation-call"
        case _ => ""
      }
      if(family.nonEmpty) selected.foreach { tree =>
        val receiver = n.parent.collect { case s: Term.Select => s.qual }
        val arguments = clauses(tree)
        val callbacks = if(arguments.size >= 2) arguments.last else Nil
        val allocations = if(family == "fold") callbacks.flatMap(_.collect {
          case s: Term.Select if s.name.value == "copy" => "copy:" + scoped(s.name.symbol.owner)
          case a: Term.Apply if !a.fun.symbol.isNone => "apply:" + scoped(a.fun.symbol) + ":owner=" + scoped(a.fun.symbol.owner)
          case n: Term.New => "new:" + n.init.tpe.syntax
          case t: Term.Tuple => "tuple:" + t.args.size
        }).distinct else Nil
        candidates.add(Candidate(family,path,n.pos.startLine+1,owner(n),scoped(n.symbol),scoped(n.symbol.owner),
          receiver.fold("")(_.syntax),receiver.flatMap(_.symbol.info).fold("<unresolved-expression-type>")(_.signature.toString),
          tree.pos.text,tree.structure,allocations))
      }
    }
    doc.tree.collect { case g: Defn.GivenAlias =>
      val symbols = g.decltpe.collect { case n: Name if !n.symbol.isNone => n.symbol.value }.distinct
      candidates.add(Candidate("given-instance",path,g.pos.startLine+1,owner(g),scoped(g.name.symbol),"",g.decltpe.syntax,
        symbols.mkString("; "),g.pos.text,g.structure,Nil))
    }
    doc.tree.collect { case g: Defn.Given =>
      val symbols = g.templ.inits.flatMap(_.tpe.collect { case n: Name if !n.symbol.isNone => n.symbol.value }).distinct
      candidates.add(Candidate("given-instance",path,g.pos.startLine+1,owner(g),scoped(g.name.symbol),"",g.templ.inits.map(_.tpe.syntax).mkString("; "),
        symbols.mkString("; "),g.pos.text,g.structure,Nil))
    }
    Patch.empty
  }
  override def afterComplete(): Unit = {
    val out = Paths.get(policy.report)
    Files.writeString(out.resolve("status.txt"),"INCOMPLETE\n")
    require(visited.asScala.map(_._1).toSet == files(policy.roots),"Incomplete operation source coverage")
    visited.asScala.foreach { case(p,h) => require(hash(Files.readString(Paths.get(p))) == h,"Source changed: " + p) }
    def companion(s: String): String = s.stripSuffix(".") + "#"
    def classification(c: Candidate): String = c.family match {
      case "copy" if cases.contains(c.symbolOwner) => "case-class-copy"
      case "copy" if declared.contains(c.symbolOwner) => "non-case-class-copy"
      case "copy" => "unresolved-copy-owner"
      case "unwrap" if c.symbol.startsWith("io/github/iltotore/iron/") => "iron-accessor"
      case "unwrap" if declared.contains(c.symbolOwner) || c.symbol.startsWith("com/histmersion/") => "project-value-member-review"
      case "unwrap" => "accessor-or-inline-expansion-review"
      case "fold" =>
        val allocated = c.allocations.exists { a =>
          if(a.startsWith("copy:")) cases.contains(a.stripPrefix("copy:"))
          else if(a.startsWith("apply:")) {
            val parts = a.stripPrefix("apply:").split(":owner=",2)
            parts.exists(s => cases.contains(companion(s)))
          } else a.startsWith("new:") || a.startsWith("tuple:")
        }
        if(allocated) "visible-allocation-review" else "no-visible-allocation; indirect-effects-unresolved"
      case "derivation-call" if c.symbol.startsWith("hearth/kindlings/") => "kindlings-derived"
      case "derivation-call" if c.symbol.startsWith("com/augustnagro/magnum/") => "database-derived"
      case "derivation-call" => "other-derived-review"
      case "given-instance" => "instance-body-review"
    }
    val all = candidates.asScala.toList.distinct.sortBy(c => (c.family,c.path,c.line,c.symbol,c.source))
    def esc(s: String): String = s.replace("\\","\\\\").replace("\t","\\t").replace("\n","\\n").replace("\r","\\r")
    def write(name: String, header: String, rows: List[List[String]]): Unit = Files.write(out.resolve(name),(header :: rows.map(_.map(esc).mkString("\t"))).asJava,UTF_8)
    write("occurrences.tsv","family\tclassification\tpath\tscope\tline\towner\tsymbol\tsymbol-owner\treceiver\treceiver-signature\tsource\tast\tvisible-allocations",all.map { c =>
      List(c.family,classification(c),c.path,if(c.path.contains("/src/test/")) "test" else "production",c.line.toString,c.owner,c.symbol,c.symbolOwner,c.receiver,c.receiverType,c.source,c.ast,c.allocations.mkString("; "))
    })
    write("sources.tsv","path\tsha256",visited.asScala.toList.sorted.map { case(p,h) => List(p,h) })
    val totals = all.groupBy(c => (c.family,classification(c),if(c.path.contains("/src/test/")) "test" else "production")).toList.sortBy(_._1)
    write("counts.tsv","family\tclassification\tscope\tcount",totals.map { case((f,c,s),xs) => List(f,c,s,xs.size.toString) })
    policy.expected.foreach { e => require(all.exists(c => c.family + "|" + classification(c) + "|" + c.symbol == e),"Missing operation fixture: " + e) }
    Files.write(out.resolve("summary.md"),(List("# Semantic operation inventory","",s"${visited.size} files; ${all.size} source occurrences. Definitions and call-site ASTs remain available in occurrences.tsv.","",
      "These are operation inventories, not blanket violations. Case-class copy owners are resolved against project declarations; unrelated copy methods remain separate. Fold allocations are syntactically visible constructors/tuples or resolved case-class calls, not a proof about evaluation semantics or indirect effects. Iron accessors and unresolved inline expansions remain separate. Every value access still needs an approved boundary decision. Customized derivations/givens require behavioral review.","",
      "| Family | Classification | Scope | Count |","|---|---|---|---:|") ++ totals.map { case((f,c,s),xs) => s"| $f | $c | $s | ${xs.size} |" }).asJava,UTF_8)
    Files.writeString(out.resolve("status.txt"),"COMPLETE_OPERATION_INVENTORY_WITH_REVIEW_CASES\n")
  }
}
