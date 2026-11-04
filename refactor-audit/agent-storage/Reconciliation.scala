import scalafix.v1._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class Reconciliation extends SyntacticRule("Reconciliation") {
  override def afterComplete(): Unit = {
    def rows(path: String) = Files.readAllLines(Paths.get(path)).asScala.drop(1).map(_.split("\t", -1).toList).toList
    val root = "refactor-audit/"
    val beforePath = root + "capability-history/observations/2026-11-04T23-19-19.607608Z/findings.tsv"
    val afterPath = root + "capability-history/observations/2026-11-04T23-54-37.816223Z/findings.tsv"
    val before = rows(beforePath); val after = rows(afterPath)
    val decisions = rows(root + "capability-treatment/review-1/decisions.tsv").filter(_(5) == "agent-storage")
    require(decisions.forall(r => r.head == "group" && r(2) == "confirmed"))
    val repaired = decisions.flatMap { decision =>
      val selected = before.filter(r => r(4) + ":" + r(5) == decision(1))
      require(selected.map(_(1).toInt).sum == decision(4).toInt, "Reviewed multiplicity changed")
      selected
    }
    require(repaired.size == 21 && repaired.map(_(1).toInt).sum == 29)
    require(repaired.map(_.head).distinct.size == repaired.size)
    require(after.toSet == (before.toSet -- repaired.toSet), "Unexpected added, removed or changed identity")
    require(Files.readString(Paths.get(root + "agent-storage/applied-verification.txt")).startsWith("PASS"))
    require(Files.readString(Paths.get(root + "agent-storage/tests/reconciliation.txt")).startsWith("PASS"))
    require(Files.readString(Paths.get(root + "assessment/current/status.txt")).startsWith("MEASUREMENT_COMPLETE"))
    val output = List("id\toccurrences\tscope\tstatus\tevidence") ++ repaired.sortBy(_.head).map(r =>
      List(r.head, r(1), "production", "verified-targeted-rewrite", "plan.tsv; applied-verification.txt; tests/reconciliation.txt; capability-history observation 2026-11-04T23-54-37.816223Z").mkString("\t"))
    Files.write(Paths.get(root + "agent-storage/verified-findings.tsv"), output.asJava)
    println("PASS: exactly 21 reviewed anchors / 29 occurrences removed; every other finding and multiplicity unchanged")
  }
}
