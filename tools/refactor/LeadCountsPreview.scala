import scalafix.v1._
import scala.meta._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class LeadCountsPreview extends SyntacticRule("LeadCountsPreview") {
  private implicit val dialect: Dialect = dialects.Scala3
  private def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
  override def afterComplete(): Unit = {
    val report = Paths.get("refactor-audit/models/pagination/lead-counts-v1")
    require(!Files.exists(report))
    val frozen = Files.readAllLines(Paths.get("refactor-audit/models/pagination/build-v4/sources.tsv")).asScala.drop(1).map(_.split("\t")).map(a => a(0) -> a(1)).toMap
    val outputs = scala.collection.mutable.ListBuffer.empty[(String,String,String)]
    val evidence = scala.collection.mutable.ListBuffer.empty[List[String]]
    def one[A](xs: List[A]): A = { require(xs.size == 1,"Expected one match, got " + xs.size); xs.head }
    def change(path: String)(f: (Source, (Tree,String) => Unit) => Unit): Unit = {
      val source = Files.readString(Paths.get(path)); require(hash(source) == frozen(path))
      val tree = source.parse[Source].get
      val edits = scala.collection.mutable.ListBuffer.empty[(Int,Int,String)]
      def edit(t: Tree, replacement: String): Unit = {
        edits += ((t.pos.start,t.pos.end,replacement))
        evidence += List(path,(t.pos.startLine+1).toString,t.syntax,t.structure,replacement)
      }
      f(tree,edit)
      val sorted = edits.toList.sortBy(_._1)
      require(sorted.nonEmpty && sorted.sliding(2).forall { case List(a,b) => a._2 <= b._1; case _ => true })
      val next = sorted.reverse.foldLeft(source) { case(s,(a,b,v)) => s.patch(a,v,b-a) }
      next.parse[Source].get; outputs += ((path,source,next))
    }
    def result(t: Type, edit: (Tree,String) => Unit): Unit = t match {
      case p: Type.Tuple if p.args.size == 2 && p.args.head.syntax == "Int" && p.args(1).syntax == "List[LeadRow]" => edit(p.args.head,"com.histmersion.RowCount")
      case _ => sys.error("Unexpected lead result: " + t.structure)
    }
    def term(t: Tree, before: String, after: String, edit: (Tree,String) => Unit): Unit = {
      val shape = before.parse[Term].get.structure
      edit(one(t.collect { case n: Term if n.structure == shape => n }),after)
    }
    change("modules/db/src/main/scala/com/histmersion/domain/catalogue/evidence/LeadRepo.scala") { (t,e) =>
      val d = one(t.collect { case d: Defn.Def if d.name.value == "leadRows" => d })
      result(d.decltpe.get,e)
      val tuple = d.body match { case b: Term.Block => b.stats.last.asInstanceOf[Term.Tuple]; case _ => sys.error("Expected lead query block") }
      require(tuple.args.size == 2 && tuple.args.head.syntax == "total")
      e(tuple.args.head,"com.histmersion.RowCount(total)")
    }
    change("modules/server/src/main/scala/com/histmersion/domain/catalogue/evidence/LeadService.scala") { (t,e) =>
      val decl = one(t.collect { case d: Decl.Def if d.name.value == "leads" => d })
      result(decl.decltpe,e)
      val defs = t.collect { case d: Defn.Def if d.name.value == "leads" => d }; require(defs.size == 2)
      defs.foreach(d => result(d.decltpe.get,e))
      term(t,"listed.total","com.histmersion.RowCount(listed.total.value)",e)
    }
    change("modules/server/src/main/scala/com/histmersion/domain/catalogue/evidence/EventLinksControllers.scala") { (t,e) =>
      val d = one(t.collect { case d: Defn.Def if d.name.value == "leads" => d })
      term(d.body,"EventLead.List(total, rows.flatMap(leadOf))","EventLead.List(total.value, rows.flatMap(leadOf))",e)
    }
    change("modules/server/src/test/scala/com/histmersion/domain/catalogue/evidence/LeadServiceContract.scala") { (t,e) =>
      term(t,"assertEquals(total, 3)","assertEquals(total.value, 3)",e)
      term(t,"assertEquals(catalogueOnly._1, 2)","assertEquals(catalogueOnly._1.value, 2)",e)
    }
    require(outputs.size == 4 && evidence.size == 9)
    Files.createDirectories(report)
    outputs.foreach { case(p,_,s) => val dest = report.resolve("sources").resolve(p); Files.createDirectories(dest.getParent); Files.writeString(dest,s) }
    Files.write(report.resolve("plan.tsv"),("path\tbefore-sha256\tafter-sha256" :: outputs.toList.map { case(p,s,n) => s"$p\t${hash(s)}\t${hash(n)}" }).asJava)
    def escape(s: String) = s.replace("\\","\\\\").replace("\t","\\t").replace("\n","\\n")
    Files.write(report.resolve("shapes.tsv"),("path\tline\tsource\tast\treplacement" :: evidence.toList.map(_.map(escape).mkString("\t"))).asJava)
    println("Four-file lead tuple preview generated; transaction ownership and expected test values preserved")
  }
}
