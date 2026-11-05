import scalafix.v1._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class Completion extends SyntacticRule("Completion") {
  override def afterComplete(): Unit = {
    val dir = Paths.get("refactor-audit/plugin-capabilities")
    def rows(path: String) = Files.readAllLines(Paths.get(path)).asScala.drop(1).map(_.split("\t", -1).toList).toList
    def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    val old = rows("refactor-audit/capability-history/observations/2026-11-04T23-54-37.816223Z/findings.tsv").map(r => r.head -> r(1).toInt).toMap
    val observation = "refactor-audit/capability-history/observations/2026-11-05T00-25-31.583908Z"
    val now = rows(observation + "/findings.tsv").map(r => r.head -> r(1).toInt).toMap
    val repaired = rows(dir.resolve("confirmed.tsv").toString)
    val accepted = rows(dir.resolve("accepted-boundary.tsv").toString)
    require(accepted.size == 1 && accepted.head(2) == "3" && accepted.head(3) == "accepted-exception")
    require(repaired.size == 23 && repaired.map(_(1).toInt).sum == 25)
    require(now == (old -- repaired.map(_.head) - accepted.head.last), "Unexpected final identity change")
    require(Files.readString(Paths.get(observation, "status.txt")).startsWith("PASS\n"))
    require(Files.readString(Paths.get("refactor-audit/assessment/current/capabilities/summary.txt")).contains("permission:plugin-process-monotonic-wait:uses=3"))
    rows(dir.resolve("final-plan.tsv").toString).foreach { r => require(hash(Files.readString(Paths.get(r.head))) == r(2), "Final source changed") }
    List("calibre-tests", "vault-tests").foreach { testDir =>
      val before = rows(dir.resolve(testDir).resolve("baseline.tsv").toString).map(r => r.take(2) -> r(2)).toMap
      val after = rows(dir.resolve(testDir).resolve("after.tsv").toString).map(r => r.take(2) -> r(2)).toMap
      require(before == after, "Test identity/outcome changed")
      require(after.values.forall(s => s == "passed" || testDir == "vault-tests" && s == "skipped"))
    }
    val evidence = "final-plan.tsv; final-source-verification.txt; compatibility-checks.tsv; integrated observation 2026-11-05T00-25-31.583908Z"
    Files.write(dir.resolve("verified-findings.tsv"), ("id\toccurrences\tscope\tstatus\tevidence" :: repaired.map(r =>
      List(r.head, r(1), "production", "verified-targeted-rewrite", evidence).mkString("\t"))).asJava)
    Files.write(dir.resolve("completion.txt"), List("PASS: plugin capability batch reconciled",
      "25 confirmed occurrences / 23 anchors repaired; 3 clock occurrences explicitly accepted at one exact host-adapter method.",
      "152 raw findings / 130 anchors remain; no new/reopened identities or multiplicity changes.",
      "This completes this batch, not the overall migration or independent detector-quality validation.").asJava)
    println("PASS: verified repair ledger; accepted boundary separate; final sources and test identities unchanged")
  }
}
