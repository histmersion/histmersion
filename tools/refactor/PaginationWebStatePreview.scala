import scalafix.v1._
import scala.meta._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class PaginationWebStatePreview extends SyntacticRule("PaginationWebStatePreview") {
  private implicit val dialect: Dialect = dialects.Scala3
  private def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
  override def afterComplete(): Unit = {
    val report = Paths.get("refactor-audit/models/pagination/web-state-v1")
    require(!Files.exists(report), "Preserve previous observations")
    val frozen = Files.readAllLines(Paths.get("refactor-audit/models/pagination/build-v2/sources.tsv")).asScala.drop(1).map(_.split("\t")).map(a => a(0) -> a(1)).toMap
    val count = "com.histmersion.api.RowCount"
    val outputs = scala.collection.mutable.ListBuffer.empty[(String,String,String)]
    val evidence = scala.collection.mutable.ListBuffer.empty[List[String]]
    def change(file: String)(f: (Source, (Tree,String) => Unit) => Unit): Unit = {
      val path = "modules/web/src/main/scala/com/histmersion/web/" + file + ".scala"
      val source = Files.readString(Paths.get(path))
      require(hash(source) == frozen(path), "Source changed since inspected compilation: " + path)
      val tree = source.parse[Source].get
      val edits = scala.collection.mutable.ListBuffer.empty[(Int,Int,String)]
      def edit(t: Tree, replacement: String): Unit = {
        edits += ((t.pos.start,t.pos.end,replacement))
        evidence += List(path,(t.pos.startLine+1).toString,t.syntax,t.structure,replacement)
      }
      f(tree,edit)
      val sorted = edits.toList.sortBy(_._1)
      require(sorted.nonEmpty && sorted.sliding(2).forall { case List(a,b) => a._2 <= b._1; case _ => true }, "Empty or overlapping changes: " + path)
      val next = sorted.reverse.foldLeft(source) { case (s,(a,b,v)) => s.patch(a,v,b-a) }
      next.parse[Source].get
      outputs += ((path,source,next))
    }
    def one[A](xs: List[A]): A = { require(xs.size == 1, "Expected exactly one AST match, got " + xs.size); xs.head }
    def method(t: Tree, n: String) = one(t.collect { case d: Defn.Def if d.name.value == n => d })
    def value(t: Tree, n: String) = one(t.collect { case d: Defn.Val if d.pats.map(_.syntax) == List(n) => d })
    def int(t: Tree, edit: (Tree,String) => Unit): Unit = edit(one(t.collect { case n: Type.Name if n.value == "Int" => n }),count)
    def term(t: Tree, before: String, after: String, edit: (Tree,String) => Unit, expected: Int = 1): Unit = {
      val shape = before.parse[Term].get.structure
      after.parse[Term].get
      val matches = t.collect { case n: Term if n.structure == shape => n }
      require(matches.size == expected, "Unexpected occurrence count for " + before + ": " + matches.size)
      matches.foreach(edit(_,after))
    }
    change("ResultsList") { (t,e) =>
      val v = one(t.collect { case d: Defn.Var if d.pats.map(_.syntax) == List("pageTotal") => d })
      int(v.decltpe.get,e)
      term(v,"0",count + "(0)",e)
      int(value(t,"total").decltpe.get,e)
      List("countLine","nextLabel").foreach(n => term(method(t,n).body,"pageTotal","pageTotal.value",e))
    }
    List("ArticlesTab","EventsTab").foreach(file => change(file) { (t,e) =>
      val v = value(t,"total")
      int(v.decltpe.get,e)
      term(v.rhs,"math.max(s.total, s.rows.size)",count + "(math.max(s.total.value, s.rows.size))",e)
    })
    change("ResultsPanel") { (t,e) =>
      val d = method(t,"counts")
      int(d.decltpe.get,e)
      term(d.body,"Some(s.total)","Some(" + count + "(s.total))",e,2)
    }
    change("library/LibraryLists") { (t,e) =>
      val d = method(t,"everything")
      int(d.decltpe.get,e)
      term(d.body,"s.total + a.total",count + "(s.total.value + a.total.value)",e)
    }
    change("library/MyLibrary") { (t,e) =>
      List("everything","unread").foreach(n => int(value(t,n).rhs,e))
      val d = method(t,"count")
      int(d.decltpe.get,e)
      List("bs.size","ns.size","ms.values.count(_.openedAt.isDefined)").foreach(s => term(d.body,s,count + "(" + s + ")",e))
      term(method(t,"refresh").body,"s.total + a.total",count + "(s.total + a.total)",e)
      term(t,"ListPage.grouped(n)","ListPage.grouped(n.value)",e)
    }
    change("library/LibraryPage") { (t,e) =>
      val d = method(t,"tab")
      val p = one(d.paramClauseGroups.flatMap(_.paramClauses).flatMap(_.values).filter(_.name.value == "total"))
      int(p.decltpe.get,e)
      term(d.body,"ListPage.grouped(n)","ListPage.grouped(n.value)",e)
    }
    change("App") { (t,e) => term(method(t,"loadTagline").body,"r.total","r.total.value",e) }
    require(outputs.size == 8)
    Files.createDirectories(report)
    outputs.foreach { case (path,_,next) =>
      val out = report.resolve("sources").resolve(path)
      Files.createDirectories(out.getParent); Files.writeString(out,next)
    }
    Files.write(report.resolve("plan.tsv"),("path\tbefore-sha256\tafter-sha256" :: outputs.toList.map { case(p,s,n) => List(p,hash(s),hash(n)).mkString("\t") }).asJava)
    def escape(s: String) = s.replace("\\","\\\\").replace("\t","\\t").replace("\n","\\n")
    Files.write(report.resolve("shapes.tsv"),("path\tline\tsource\tast\treplacement" :: evidence.toList.map(_.map(escape).mkString("\t"))).asJava)
    println(s"Preview: ${outputs.size} files, ${evidence.size} guarded AST changes; application unchanged")
  }
}
