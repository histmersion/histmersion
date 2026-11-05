import scalafix.v1._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class Inventory extends SyntacticRule("Inventory") {
  override def afterComplete(): Unit = {
    val dir = Paths.get("refactor-audit/approval-isolation")
    def rows(p: String) = Files.readAllLines(Paths.get(p)).asScala.drop(1).map(_.split("\t",-1).toList).toList
    def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    def write(name: String, header: String, data: List[List[String]]): Unit = Files.write(dir.resolve(name),(header :: data.map(_.mkString("\t"))).asJava)
    val owners = Set("com/histmersion/domain/automation/agents/ApprovalService#", "com/histmersion/domain/automation/agents/ApprovalService.Implementation#",
      "com/histmersion/domain/automation/agents/ApprovalService.InMemory#", "com/histmersion/domain/automation/agents/AgentRepo.", "com/histmersion/domain/automation/approvals/Approvals#")
    val definitions = rows("refactor-audit/architecture/declarations.tsv").filter(r => owners(r(5)) && Set("record","proposedBefore")(r(6)))
    val symbols = definitions.map(_(4)).toSet
    require(definitions.nonEmpty, "Missing semantic definitions")
    val references = rows("refactor-audit/architecture/references.tsv").filter(r => symbols(r(3)))
    require(references.nonEmpty, "Missing semantic callers")
    val paths = (definitions.map(_.head) ++ references.map(_.head)).distinct.sorted
    val recorded = rows("refactor-audit/architecture/sources.tsv").map(r => r.head -> r(1)).toMap
    require(paths.forall(p => recorded.get(p).contains(hash(Files.readString(Paths.get(p))))),"Stale definition/caller evidence")
    write("definitions.tsv","path\tline\tmodule\tpackage\tsymbol\towner\tname\tkind\tmodifiers\tparents\tsignature\ttop-level\tconstructor\tsource\tast",definitions)
    write("callers.tsv","path\tline\towner\ttarget\tspelling\tshape\tresolution\ttarget-files\ttarget-packages",references)
    write("sources.tsv","path\tsha256",paths.map(p => List(p,recorded(p))))
    val reviewedPaths = Set("modules/db/src/main/scala/com/histmersion/domain/automation/agents/AgentRepo.scala",
      "modules/server/src/main/scala/com/histmersion/domain/automation/approvals/Approvals.scala")
    val additions = rows("refactor-audit/isolation-history/baseline.tsv").filter(r => reviewedPaths(r(3)) && r(5).startsWith("com/histmersion/domain/library/"))
    require(additions.nonEmpty)
    val confirmed = additions.map(r =>
      List(r.head,r(1),r(3),r(4),r(5),"confirmed","manual/design-required-must-fix",hash(Files.readString(Paths.get(r(3)))),
        "Source-specific proposal signatures and SQL serialization continue the ApprovalService automation-to-library coupling; definitions.tsv and design.md retain the repair scope."))
    if(Files.exists(dir.resolve("confirmed-additions.tsv"))) require(rows(dir.resolve("confirmed-additions.tsv").toString) == confirmed,"Confirmed evidence changed")
    else write("confirmed-additions.tsv","id\tscope\tpath\towner\ttarget\tclassification\ttreatment\tsource-sha256\tevidence",confirmed)
    write("summary.tsv","metric\tcount",List(List("definitions",definitions.size.toString),List("resolved-reference-evidence",references.size.toString),List("source-files",paths.size.toString),List("additional-confirmed-relationships",additions.size.toString)))
    println("PASS: " + definitions.size + " definitions; " + references.size + " references; " + additions.size + " additional confirmed relationships")
  }
}
