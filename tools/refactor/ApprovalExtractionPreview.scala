import scalafix.v1._
import scala.meta._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class ApprovalExtractionPreview extends SyntacticRule("ApprovalExtractionPreview") {
  private implicit val dialect: Dialect = dialects.Scala3
  override def afterComplete(): Unit = {
    val output = Paths.get("target/approval-extraction-preview-v3")
    require(!Files.exists(output), "Preview already exists; preserve it")
    val server = "modules/server/src/main/scala/com/histmersion/"
    val db = "modules/db/src/main/scala/com/histmersion/"
    val servicePath = server + "domain/automation/agents/ApprovalService.scala"
    val statePath = server + "domain/automation/AutomationState.scala"
    val repoPath = db + "domain/automation/agents/AgentRepo.scala"
    def read(p: String) = Files.readString(Paths.get(p))
    val service = read(servicePath).parse[Source].get
    val repo = read(repoPath).parse[Source].get
    val state = read(statePath).parse[Source].get
    val edits = scala.collection.mutable.Map.empty[String,scala.collection.mutable.ListBuffer[(Int,Int,String)]]
    def edit(p: String, t: Tree, replacement: String): Unit = edits.getOrElseUpdate(p,scala.collection.mutable.ListBuffer.empty) += ((t.pos.start,t.pos.end,replacement))
    def insert(p: String, at: Int, replacement: String): Unit = edits.getOrElseUpdate(p,scala.collection.mutable.ListBuffer.empty) += ((at,at,replacement))
    def patched(text: String, changes: List[(Int,Int,String)], offset: Int = 0): String = {
      val sorted = changes.sortBy(_._1)
      require(sorted.sliding(2).forall { case List(a,b) => a._2 <= b._1; case _ => true }, "Overlapping AST edits")
      sorted.reverse.foldLeft(text) { case(s,(start,end,value)) => s.patch(start-offset,value,end-start) }
    }
    def fragment(t: Tree, changes: List[(Int,Int,String)] = Nil): String = {
      val text = patched(t.syntax,changes,t.pos.start)
      text.linesIterator.zipWithIndex.map { case(line,i) => if(i == 0) line else line.drop(math.min(t.pos.startColumn,line.takeWhile(_ == ' ').length)) }.mkString("\n")
    }
    def indent(s: String, n: Int) = s.linesIterator.map(l => if(l.isEmpty) "" else " " * n + l).mkString("\n")
    def method(t: Template, name: String) = t.body.stats.collect { case d: Defn.Def if d.name.value == name => d } match {
      case List(d) => d
      case ds => sys.error("Expected one method " + name + ": " + ds.size)
    }
    val traitDef = service.collect { case t: Defn.Trait if t.name.value == "ApprovalService" => t }.head
    val impl = service.collect { case c: Defn.Class if c.name.value == "Implementation" => c }.head
    val memory = service.collect { case c: Defn.Class if c.name.value == "InMemory" => c }.head
    val declarations = traitDef.templ.body.stats.collect { case d: Decl.Def if Set("record","proposedBefore")(d.name.value) => fragment(d) }
    require(declarations.size == 2)
    val repoMethods = repo.collect { case d: Defn.Def if Set("record","proposedBefore")(d.name.value) &&
      d.paramClauseGroups.flatMap(_.paramClauses).flatMap(_.values).exists(_.name.value == "citekey") => d }
    require(repoMethods.size == 2)
    val repoCode = repoMethods.map { d =>
      if(d.name.value == "proposedBefore") fragment(d)
      else {
        val lookup = d.body.collect { case a: Term.Apply if a.fun.isInstanceOf[Term.Select] && a.fun.asInstanceOf[Term.Select].name.value == "flatMap" && a.argClause.values.map(_.syntax) == List("approval(_)") => a }
        require(lookup.size == 1)
        val call = lookup.head
        fragment(d,List((d.decltpe.get.pos.start,d.decltpe.get.pos.end,"Option[Long]"),
          (call.pos.start,call.pos.end,call.fun.asInstanceOf[Term.Select].qual.syntax)))
      }
    }.mkString("\n\n")
    val dbCode = List("record","proposedBefore").map { name =>
      val d = method(impl.templ,name)
      val names = d.collect { case n: Term.Name if n.value == "AgentRepo" => (n.pos.start,n.pos.end,"SourceApprovalRepo") }
      val lookup = if(name == "record") {
        val calls = d.body.collect { case a: Term.Apply if a.fun.syntax == "AgentRepo.record" => a }
        require(calls.size == 1)
        List((calls.head.pos.end,calls.head.pos.end,".flatMap(approvals.approvalIn(_))"))
      } else Nil
      fragment(d,names ++ lookup)
    }.mkString("\n\n")
    val memoryCode = List("record","proposedBefore").map { name =>
      val d = method(memory.templ,name)
      fragment(d,d.collect {
        case n: Term.Name if n.value == "insert" => (n.pos.start,n.pos.end,"AutomationState.insertApproval(this.state)")
        case n: Term.Name if n.value == "approval" => (n.pos.start,n.pos.end,"approvals.approval")
      })
    }.mkString("\n\n")
    val insertion = method(memory.templ,"insert")
    val shared = fragment(insertion,List((insertion.mods.head.pos.start,insertion.mods.head.pos.end,""),
      (insertion.name.pos.start,insertion.name.pos.end,"insertApproval"),
      (insertion.name.pos.end,insertion.name.pos.end,"(state: java.util.concurrent.atomic.AtomicReference[AutomationState])")))
    val stateObject = state.collect { case o: Defn.Object if o.name.value == "AutomationState" => o }.head
    val firstState = stateObject.templ.body.stats.head
    insert(statePath,firstState.pos.start,"import hearth.kindlings.optics.syntax.*\n\n" + indent(shared.trim,2) + "\n\n  ")
    edit(servicePath,insertion,"")
    memory.templ.collect { case n: Term.Name if n.value == "insert" && n.pos.start != insertion.name.pos.start =>
      edit(servicePath,n,"AutomationState.insertApproval(this.state)")
    }
    val readSignature = "def approvalIn(id: Long)(using com.augustnagro.magnum.DbCon): Option[ApprovalInfo]"
    val firstTrait = traitDef.templ.body.stats.head
    insert(servicePath,firstTrait.pos.start,readSignature + "\n  ")
    val firstImpl = impl.templ.body.stats.head
    insert(servicePath,firstImpl.pos.start,readSignature + " = AgentRepo.approval(id)\n    ")
    val firstMemory = memory.templ.body.stats.head
    insert(servicePath,firstMemory.pos.start,readSignature + " = approval(id)\n    ")
    val imports = "import com.histmersion.api.automation.Agents.{ApprovalKind, ApprovalState, ApprovalInfo}\nimport com.histmersion.domain.library.{Source, Edge}\nimport com.histmersion.domain.automation.AutomationState\nimport com.histmersion.domain.automation.agents.{ApprovalService, ApprovalRecord}\nimport com.histmersion.util.database.Transactor\nimport java.time.{Clock, Instant}\nimport java.util.concurrent.atomic.AtomicReference\n"
    val generatedService = "package com.histmersion.domain.library.workflows\n\n" + imports +
      "\ntrait SourceApprovalService:\n" + indent(declarations.mkString("\n\n"),2) +
      "\n\nobject SourceApprovalService:\n  def apply(db: Transactor, approvals: ApprovalService, clock: Clock): SourceApprovalService = Implementation(db, approvals, clock)\n\n" +
      "  private final class Implementation(db: Transactor, approvals: ApprovalService, clock: Clock) extends SourceApprovalService:\n    private def now: Instant = Instant.now(clock)\n" + indent(dbCode,4) +
      "\n\n  final class InMemory(state: AtomicReference[AutomationState], approvals: ApprovalService, clock: Clock) extends SourceApprovalService:\n    private def now: Instant = Instant.now(clock)\n    private def rows: Vector[ApprovalRecord] = state.get.approvals\n    private val Open = ApprovalState.Open.wire\n    private def decidedAt(st: ApprovalState): Option[Instant] = Option.when(st != ApprovalState.Open)(now)\n" + indent(memoryCode,4) + "\n"
    val generatedRepo = "package com.histmersion.domain.library.workflows\n\nimport com.augustnagro.magnum.*\nimport com.histmersion.util.database.given\nimport com.histmersion.api.automation.Agents.{ApprovalKind, ApprovalState}\nimport com.histmersion.domain.library.{Source, Edge}\n\nprivate[workflows] object SourceApprovalRepo:\n" + indent(repoCode,2) + "\n"
    val generated = Map(server + "domain/library/workflows/SourceApprovalService.scala" -> generatedService,
      db + "domain/library/workflows/SourceApprovalRepo.scala" -> generatedRepo)
    val changed = edits.toMap.map { case(p,es) => p -> patched(read(p),es.toList) }
    val all = changed ++ generated
    val plan = all.toList.sortBy(_._1).map { case(p,text) =>
      val parsed = text.parse[Source].get
      if(p == statePath) {
        val companion = parsed.collect { case o: Defn.Object if o.name.value == "AutomationState" => o }.head
        require(companion.templ.body.stats.exists { case d: Defn.Def => d.name.value == "insertApproval"; case _ => false }, "Shared insertion must remain a companion member")
      }
      val dest = output.resolve(p); Files.createDirectories(dest.getParent); Files.writeString(dest,text)
      List(p,if(Files.exists(Paths.get(p))) hash(read(p)) else "new",hash(text)).mkString("\t")
    }
    val report = Paths.get("refactor-audit/approval-isolation/extraction/v3"); Files.createDirectories(report)
    Files.write(report.resolve("plan.tsv"),("path\tbefore-sha256\tafter-sha256" :: plan).asJava)
    Files.write(report.resolve("status.txt"),List("PREVIEW_ONLY", "4 definitions/storage files; application unchanged.",
      "Source callers and removal of old source-specific engine methods remain required before application.", "No repair claimed; compile validation pending.").asJava)
    println("Generated four-file AST extraction preview; no application patches applied")
  }
  private def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
}
