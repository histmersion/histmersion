import scalafix.v1._
import scala.meta._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class ApprovalWiringPreview extends SyntacticRule("ApprovalWiringPreview") {
  private implicit val dialect: Dialect = dialects.Scala3
  private def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
  override def afterComplete(): Unit = {
    val extracted = Paths.get("target/approval-extraction-preview-v3")
    val output = Paths.get("target/approval-wiring-preview-v2")
    require(!Files.exists(output), "Preserve existing preview")
    val report = Paths.get("refactor-audit/approval-isolation/wiring/v2"); Files.createDirectories(report)
    val server = "modules/server/src/main/scala/com/histmersion/"
    val tests = "modules/server/src/test/scala/com/histmersion/"
    val db = "modules/db/src/main/scala/com/histmersion/"
    val base = Files.readAllLines(Paths.get("refactor-audit/approval-isolation/extraction/v3/plan.tsv")).asScala.drop(1).map(_.split("\t",-1).toList).toList
    val content = scala.collection.mutable.Map.empty[String,String]
    base.foreach { r =>
      require(r(1) == "new" && !Files.exists(Paths.get(r.head)) || r(1) != "new" && hash(Files.readString(Paths.get(r.head))) == r(1))
      val text = Files.readString(extracted.resolve(r.head)); require(hash(text) == r(2)); content(r.head) = text
    }
    def load(p: String) = content.getOrElseUpdate(p,Files.readString(Paths.get(p)))
    val evidence = scala.collection.mutable.ListBuffer.empty[List[String]]
    def change(p: String)(f: (Source, (Tree,String) => Unit, (Int,String) => Unit) => Unit): Unit = {
      val text = load(p); val tree = text.parse[Source].get
      val edits = scala.collection.mutable.ListBuffer.empty[(Int,Int,String)]
      def edit(t: Tree, s: String): Unit = {
        edits += ((t.pos.start,t.pos.end,s))
        evidence += List(p,(t.pos.startLine+1).toString,t.syntax,t.structure,s)
      }
      def insert(at: Int, s: String): Unit = edits += ((at,at,s))
      f(tree,edit,insert)
      val sorted = edits.toList.sortBy(_._1)
      require(sorted.sliding(2).forall { case List(a,b) => a._2 <= b._1; case _ => true },"Overlapping edits: " + p)
      content(p) = sorted.reverse.foldLeft(text) { case(s,(a,b,v)) => s.patch(a,v,b-a) }
    }
    def sourceMethod(t: Tree): Boolean = t match {
      case d: Defn.Def => Set("record","proposedBefore")(d.name.value) && d.paramClauseGroups.flatMap(_.paramClauses).flatMap(_.values).exists(_.name.value == "citekey")
      case d: Decl.Def => Set("record","proposedBefore")(d.name.value) && d.paramClauseGroups.flatMap(_.paramClauses).flatMap(_.values).exists(_.name.value == "citekey")
      case _ => false
    }
    List(server + "domain/automation/agents/ApprovalService.scala" -> 6,
      db + "domain/automation/agents/AgentRepo.scala" -> 2,
      server + "domain/automation/approvals/Approvals.scala" -> 1).foreach { case(p,count) => change(p) { (tree,edit,_) =>
      val methods = tree.collect { case t if sourceMethod(t) => t }
      require(methods.size == count,"Unexpected source-method count: " + p)
      methods.foreach(edit(_,""))
      val imports = tree.collect { case i: Import if Set("import com.histmersion.domain.library.Source","import com.histmersion.domain.library.Edge")(i.syntax) => i }
      require(imports.size == 2,"Unexpected library imports: " + p)
      imports.foreach(edit(_,""))
    }}
    val currencyPath = server + "domain/library/workflows/CurrencyCheckPipeline.scala"
    val currency = load(currencyPath).parse[Source].get
    val query = currency.collect { case a: Term.Apply if a.fun.syntax == "ctx.approvalStore.proposedBefore" => a }
    require(query.size == 1)
    val queryArgs = query.head.argClause.values.map(_.syntax).mkString(", ")
    change(currencyPath) { (_,edit,_) => edit(query.head,"approvals.proposedBefore(p)") }
    change(server + "domain/library/workflows/SourceProposals.scala") { (tree,edit,insert) =>
      val cls = tree.collect { case c: Defn.Class if c.name.value == "SourceProposals" => c }.head
      val param = cls.ctor.paramClauses.flatMap(_.values).find(_.name.value == "approvals").get
      require(param.decltpe.get.syntax == "Approvals")
      edit(param.decltpe.get,"SourceApprovalService")
      val first = cls.templ.body.stats.head
      insert(first.pos.start,"def proposedBefore(p: Proposal): Boolean = approvals.proposedBefore(" + queryArgs + ")\n\n  ")
      tree.collect { case i: Import if i.syntax == "import com.histmersion.domain.automation.approvals.Approvals" => edit(i,"") }
    }
    change(server + "domain/library/workflows/LibraryWorkflows.scala") { (tree,edit,_) =>
      val calls = tree.collect { case a: Term.Apply if a.fun.syntax == "SourceProposals" => a }
      require(calls.size == 1 && calls.head.argClause.values.head.syntax == "a.approvals")
      edit(calls.head.argClause.values.head,"SourceApprovalService(transactor, a.approvalStore, a.clock)")
    }
    change(tests + "domain/automation/agents/TestRuns.scala") { (tree,edit,insert) =>
      val method = tree.collect { case d: Defn.Def if d.name.value == "proposals" => d }.head
      val last = method.paramClauseGroups.head.paramClauses.head.values.last
      insert(last.pos.end,",\n      ds: DataSource")
      val call = method.body.collect { case a: Term.Apply if a.fun.syntax == "SourceProposals" => a }.head
      edit(call.argClause.values.head,"SourceApprovalService(Transactor(ds), a.approvalStore, a.clock)")
    }
    change(tests + "domain/automation/agents/AgentEngineSuite.scala") { (tree,_,insert) =>
      val calls = tree.collect { case a: Term.Apply if (a.fun match {
        case Term.Select(Term.Name("TestRuns"),Term.Name("proposals")) => true
        case _ => false
      }) => a }
      require(calls.size == 4)
      calls.foreach(a => insert(a.argClause.values.last.pos.end,", ds"))
    }
    val qualified = "com.histmersion.domain.library.workflows.SourceApprovalService"
    change(tests + "domain/automation/EngineStores.scala") { (tree,_,insert) =>
      val cls = tree.collect { case t: Defn.Trait if t.name.value == "EngineStores" => t }.head
      insert(cls.templ.body.stats.head.pos.start,"def sourceApprovals: " + qualified + "\n  ")
    }
    change(tests + "domain/automation/EngineStoreSuite.scala") { (tree,_,insert) =>
      val values = tree.collect { case v: Defn.Val if v.pats.map(_.syntax) == List("approvals") => v }
      require(values.size == 2)
      values.foreach { v =>
        val constructor = if(v.rhs.syntax.startsWith("ApprovalService.InMemory")) qualified + ".InMemory(state, approvals, clock)"
          else { require(v.rhs.syntax == "ApprovalService(db, Clock.systemUTC())"); qualified + "(db, approvals, Clock.systemUTC())" }
        insert(v.pos.end,"\n" + " " * v.pos.startColumn + "val sourceApprovals = " + constructor)
      }
    }
    change(tests + "domain/automation/agents/ApprovalServiceContract.scala") { (tree,edit,_) =>
      val selectors = tree.collect { case s: Term.Select if s.qual.syntax == "a(s)" && Set("record","proposedBefore")(s.name.value) => s }
      require(selectors.size == 3)
      selectors.foreach(s => edit(s.qual,"s.sourceApprovals"))
    }
    val plan = content.toList.sortBy(_._1).map { case(p,text) =>
      val parsed = text.parse[Source].get
      if(p.endsWith("/AgentRepo.scala")) require(parsed.collect { case d: Defn.Def if d.name.value == "record" => d }.size == 1,"Agent-step record must remain")
      val dest = output.resolve(p); Files.createDirectories(dest.getParent); Files.writeString(dest,text)
      List(p,if(Files.exists(Paths.get(p))) hash(Files.readString(Paths.get(p))) else "new",hash(text)).mkString("\t")
    }
    require(content.size == 14,"Unexpected batch file count: " + content.size)
    Files.write(report.resolve("plan.tsv"),("path\tbefore-sha256\tafter-sha256" :: plan).asJava)
    def escape(s: String) = s.replace("\\","\\\\").replace("\t","\\t").replace("\n","\\n")
    Files.write(report.resolve("shapes.tsv"),("path\tline\tsource\tast\treplacement" :: evidence.toList.map(_.map(escape).mkString("\t"))).asJava)
    println("14-file wiring preview generated; original engine methods removed; application unchanged")
  }
}
