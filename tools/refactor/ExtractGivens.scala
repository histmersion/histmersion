import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder,Configured,generic}
import java.nio.file.{Files,Paths}
import scala.jdk.CollectionConverters._

object ExtractGivens {
  case class Extraction(source: String = "", declaredType: String = "", target: String = "", pkg: String = "", owner: String = "", visibility: String = "", imports: List[String] = Nil, consumers: List[String] = Nil)
  object Extraction {
    implicit val surface: generic.Surface[Extraction] = generic.deriveSurface[Extraction]
    implicit val decoder: ConfDecoder[Extraction] = generic.deriveDecoder(Extraction()).noTypos
  }
  case class Removal(source: String = "", declaredType: String = "", compiledMember: String = "")
  object Removal {
    implicit val surface: generic.Surface[Removal] = generic.deriveSurface[Removal]
    implicit val decoder: ConfDecoder[Removal] = generic.deriveDecoder(Removal()).noTypos
  }
  case class Policy(manifest: String = "", compiledReferences: String = "", report: String = "", extractions: List[Extraction] = Nil, removals: List[Removal] = Nil, removeImports: Map[String,List[String]] = Map.empty)
  object Policy {
    implicit val surface: generic.Surface[Policy] = generic.deriveSurface[Policy]
    implicit val decoder: ConfDecoder[Policy] = generic.deriveDecoder(Policy()).noTypos
  }
}
class ExtractGivens(val policy: ExtractGivens.Policy) extends SyntacticRule("ExtractGivens") {
  def this() = this(ExtractGivens.Policy())
  override def withConfiguration(c: Configuration): Configured[Rule] = c.conf.getOrElse("ExtractGivens")(ExtractGivens.Policy()).map(new ExtractGivens(_))
  override def afterComplete(): Unit = {
    implicit val dialect: Dialect = dialects.Scala3
    def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    def rows(p: String) = Files.readAllLines(Paths.get(p)).asScala.drop(1).map(_.split("\t",-1).toList).toList
    val frozen = rows(policy.manifest).map(r => r.head -> r(1)).toMap
    frozen.foreach { case(p,h) => require(hash(Files.readString(Paths.get(p))) == h,"Stale source: " + p) }
    val out = Paths.get(policy.report); require(!Files.exists(out))
    val selectors = policy.extractions.map(e => (e.source,e.declaredType)) ++ policy.removals.map(e => (e.source,e.declaredType))
    require(selectors.distinct.size == selectors.size && selectors.nonEmpty)
    require(policy.extractions.map(_.target).distinct.size == policy.extractions.size)
    val compiled = rows(policy.compiledReferences)
    policy.removals.foreach(r => require(!compiled.exists(c => c(2)=="external-consumer" && c(4)==r.compiledMember),"Compiled consumer forbids removal: " + r.compiledMember))
    val source = scala.collection.mutable.Map.empty[String,String]
    val trees = scala.collection.mutable.Map.empty[String,Source]
    val edits = scala.collection.mutable.Map.empty[String,List[(Int,Int,String)]]
    def tree(path: String): Source = trees.getOrElseUpdate(path,{
      require(frozen.contains(path)); val s = Files.readString(Paths.get(path)); source(path)=s; s.parse[Source].get
    })
    def edit(path: String, start: Int, end: Int, next: String): Unit = edits(path) = edits.getOrElse(path,Nil) :+ ((start,end,next))
    def select(path: String, tpe: String): (Defn.GivenAlias,Int) = {
      val pkgs = tree(path).collect { case p: Pkg => p }; require(pkgs.size==1)
      val matches = pkgs.head.stats.collect { case g: Defn.GivenAlias if g.decltpe.syntax==tpe => g }
      require(matches.size==1,"Expected one top-level given: " + path + " " + tpe)
      val g = matches.head
      val index = pkgs.head.stats.indexWhere(_ eq g)
      val prior = if(index==0) pkgs.head.ref.pos.end else pkgs.head.stats(index-1).pos.end
      val leading = tree(path).tokens.filter(t => t.pos.start>=prior && t.pos.end<=g.pos.start)
      require(leading.forall(t => t.is[Token.Whitespace] || t.is[Token.Comment]),"Unreviewed leading syntax")
      val start = leading.collectFirst { case c: Token.Comment => c.pos.start }.getOrElse(g.pos.start)
      (g,start)
    }
    val created = scala.collection.mutable.Map.empty[String,String]
    val evidence = scala.collection.mutable.ListBuffer.empty[List[String]]
    val imports = scala.collection.mutable.Map.empty[String,Set[String]]
    policy.extractions.foreach { e =>
      require(!Files.exists(Paths.get(e.target)),"Target collision")
      val (g,start) = select(e.source,e.declaredType)
      val body = source(e.source).substring(start,g.pos.end)
      val next = "package " + e.pkg + "\n\n" + e.imports.map("import " + _).mkString("\n") + "\n\n" + e.visibility + " object " + e.owner + ":\n" + body.linesIterator.map("  " + _).mkString("\n") + "\n"
      val parsed = next.parse[Source].get
      require(parsed.collect { case a: Defn.GivenAlias => a.structure } == List(g.structure),"Given body changed")
      created(e.target)=next
      edit(e.source,start,g.pos.end,"")
      e.consumers.foreach { p => tree(p); imports(p)=imports.getOrElse(p,Set.empty) + (e.pkg + "." + e.owner + ".given") }
      evidence += List(e.source,e.declaredType,"extract",e.target,e.consumers.mkString(","),g.structure)
    }
    policy.removals.foreach { e =>
      val (g,start)=select(e.source,e.declaredType); edit(e.source,start,g.pos.end,"")
      evidence += List(e.source,e.declaredType,"remove-no-observed-compiled-consumer","compiler-probe-required",e.compiledMember,g.structure)
    }
    policy.removeImports.foreach { case(path,selected) =>
      selected.foreach { syntax =>
        val found = tree(path).collect { case i: Import if i.syntax==syntax => i }; require(found.size==1,"Import changed: " + syntax)
        edit(path,found.head.pos.start,found.head.pos.end,"")
      }
    }
    imports.foreach { case(path,entries) =>
      val pkgs = tree(path).collect { case p: Pkg => p }; require(pkgs.size==1)
      edit(path,pkgs.head.ref.pos.end,pkgs.head.ref.pos.end,"\n\n" + entries.toList.sorted.map("import " + _).mkString("\n"))
    }
    val outputs = edits.toList.sortBy(_._1).map { case(p,es) =>
      val sorted = es.sortBy(_._1)
      require(sorted.sliding(2).forall { case List(a,b) => a._2<=b._1; case _ => true },"Overlapping patches")
      val coalesced = sorted.foldLeft(List.empty[(Int,Int,String)]) { (acc,e) =>
        acc match {
          case last :: rest if last._3.isEmpty && e._3.isEmpty && tree(p).tokens.filter(t => t.pos.start>=last._2 && t.pos.end<=e._1).forall(_.is[Token.Whitespace]) =>
            (last._1,e._2,"") :: rest
          case _ => e :: acc
        }
      }.reverse.map { case(a,b,n) =>
        val end = if(n.isEmpty) tree(p).tokens.find(t => t.pos.start==b && t.is[Token.LF]).map(_.pos.end).getOrElse(b) else b
        (a,end,n)
      }
      val next = coalesced.reverse.foldLeft(source(p)) { case(s,(a,b,n)) => s.patch(a,n,b-a) }
      next.parse[Source].get
      (p,hash(source(p)),next)
    } ++ created.toList.sortBy(_._1).map { case(p,s) => (p,"new",s) }
    Files.createDirectories(out)
    outputs.foreach { case(p,b,s) =>
      val dest=out.resolve("sources").resolve(p); Files.createDirectories(dest.getParent); Files.writeString(dest,s)
      if(b!="new") { val old=out.resolve("before").resolve(p); Files.createDirectories(old.getParent); Files.writeString(old,source(p)) }
    }
    def escape(s: String)=s.replace("\\","\\\\").replace("\t","\\t").replace("\n","\\n")
    Files.write(out.resolve("plan.tsv"),("path\tbefore-sha256\tafter-sha256" :: outputs.map { case(p,b,n) => List(p,b,hash(n)).mkString("\t") }).asJava)
    Files.write(out.resolve("extractions.tsv"),("source\tdeclared-type\taction\ttarget\tconsumers-or-member\tast" :: evidence.toList.map(_.map(escape).mkString("\t"))).asJava)
    Files.writeString(out.resolve("status.txt"),"PREVIEW_ONLY\nfiles=" + outputs.size + "\nGiven ASTs preserved exactly; removal still requires compilation and fallback verification.\n")
    println("Preview: " + outputs.size + " files; " + policy.extractions.size + " unchanged given ASTs extracted")
  }
}
