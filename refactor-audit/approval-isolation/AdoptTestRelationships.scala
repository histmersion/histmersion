import scalafix.v1._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class AdoptTestRelationships extends SyntacticRule("AdoptTestRelationships") {
  override def afterComplete(): Unit = {
    def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    val history = Paths.get("refactor-audit/isolation-history")
    val out = history.resolve("adoptions/approval-tests"); require(!Files.exists(out))
    val input = Paths.get("refactor-audit/approval-isolation/test-relationships-for-review.tsv")
    val rows = Files.readAllLines(input).asScala.drop(1).map(_.split("\t",-1).toList).toList
    val paths = Set("EngineStores.scala","EngineStoreSuite.scala","ApprovalServiceContract.scala","TestRuns.scala")
    require(rows.size == 10 && rows.map(_.head).distinct.size == 10)
    require(rows.forall(r => r(1) == "test" && paths(Paths.get(r(3)).getFileName.toString) && r(5).startsWith("com/histmersion/domain/library/workflows/SourceApprovalService")))
    require(Files.readString(Paths.get("refactor-audit/approval-isolation/completion.txt")).startsWith("PRODUCTION_REPAIR_VERIFIED\n"))
    val decisionRows = rows.map(r => List(r.head,r(3),hash(Files.readString(Paths.get(r(3)))),"non-violation",
      "Test fixture/contract invokes the library-owned proposal service while preserving the same database and in-memory assertions; no production automation dependency. Local owner IDs remain explicitly ambiguous.").mkString("\t"))
    Files.createDirectories(out)
    Files.write(out.resolve("decisions.tsv"),("id\tpath\tsource-sha256\tclassification\treason" :: decisionRows).asJava)
    Files.write(out.resolve("event.txt"),List("ADDITIVE_REVIEWED_TEST_NONVIOLATIONS",
      "All ten exact relationships were inspected after the gate rejected them. No detector or direction policy changed; raw evidence remains detected.",
      "baseline-sha256=" + hash(Files.readString(history.resolve("baseline.tsv"))),
      "contract-sha256=" + hash(Files.readString(history.resolve("contract.txt"))),
      "decisions-sha256=" + hash(Files.readString(out.resolve("decisions.tsv"))),
      "review-input-sha256=" + hash(Files.readString(input))).asJava)
    println("Recorded ten reviewed test relationships without replacing the baseline")
  }
}
