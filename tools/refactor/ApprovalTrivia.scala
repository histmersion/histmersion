import scalafix.v1._
import scala.meta._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class ApprovalTrivia extends SyntacticRule("ApprovalTrivia") {
  private val changes = scala.collection.mutable.Map.empty[String,(String,String)]
  private def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
  private val root = Paths.get("").toAbsolutePath.normalize
  override def fix(implicit doc: SyntacticDocument): Patch = {
    val file = doc.input match { case Input.File(p,_) => p.toNIO; case Input.VirtualFile(p,_) => Paths.get(p) }
    val path = root.relativize(file.toAbsolutePath.normalize).toString
    if(!List("/agents/ApprovalService.scala","/agents/AgentRepo.scala","/approvals/Approvals.scala").exists(path.endsWith)) return Patch.empty
    val edits = doc.tokens.collect {
      case t: Token.Comment if List("A proposal about a source from its parts", "Was this change of a source ever proposed", "Record a proposal about a source from its parts", "Was this change ever proposed (in any state)").exists(t.text.contains) => (t.pos.start,t.pos.end,"")
      case t: Token.Space if doc.input.text.substring(t.pos.start,doc.input.text.indexOf('\n',t.pos.end) match { case -1 => doc.input.text.length; case n => n }).forall(c => c == ' ' || c == '\r') => (t.pos.start,t.pos.end,"")
    }.toList
    val result = edits.sortBy(-_._1).foldLeft(doc.input.text) { case(s,(a,b,v)) => s.patch(a,v,b-a) }
    implicit val dialect: Dialect = dialects.Scala3
    require(result.parse[Source].get.structure == doc.tree.structure,"Trivia changed AST")
    changes(path) = hash(doc.input.text) -> hash(result)
    Patch.replaceTree(doc.tree,result)
  }
  override def afterComplete(): Unit = {
    val rows = Files.readAllLines(Paths.get("refactor-audit/approval-isolation/wiring/final-plan.tsv")).asScala.drop(1).map(_.split("\t",-1).toList).toList
    require(changes.size == 3)
    val dest = Paths.get("target/approval-final-preview-v2"); require(!Files.exists(dest))
    val finalRows = rows.map { r =>
      val text = Files.readString(Paths.get(r.head)); val current = hash(text)
      require(changes.get(r.head).fold(current == r(2)) { case(before,after) => before == r(2) && current == after },"Unreviewed source change: " + r.head)
      val p = dest.resolve(r.head); Files.createDirectories(p.getParent); Files.writeString(p,text)
      List(r.head,r(1),current).mkString("\t")
    }
    val out = Paths.get("refactor-audit/approval-isolation/wiring")
    Files.write(out.resolve("final-plan-v2.tsv"),("path\tbefore-sha256\tafter-sha256" :: finalRows).asJava)
    Files.write(out.resolve("trivia-v2.tsv"),("path\tbefore-sha256\tafter-sha256\tverification" :: changes.toList.sortBy(_._1).map { case(p,(a,b)) => List(p,a,b,"AST_UNCHANGED").mkString("\t") }).asJava)
    println("PASS: three trivia-only corrections; final 14-file source plan retained")
  }
}
