import scalafix.v1._
import scala.meta._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class ServerClockVerification extends SyntacticRule("ServerClockVerification") {
  override def afterComplete(): Unit = {
    def read(p: String) = Files.readString(Paths.get(p))
    def lines(p: String) = Files.readAllLines(Paths.get(p)).asScala.toList
    def rows(p: String) = lines(p).tail.map(_.split("\t", -1).toList)
    def hash(s: String): String = java.security.MessageDigest.getInstance("SHA-256")
      .digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    val base = "refactor-audit/server-clock/"
    val original = rows(base + "preview-1/sources.tsv").map(r => r.head -> r(1)).toMap
    val chain = List("probe-preview-2", "probe-constructor-fix", "preview-1")
    val expected = chain.foldLeft(original) { (state, step) =>
      val plan = rows(base + step + "/plan.tsv")
      plan.foreach { r =>
        require(state.get(r.head).contains(r(1)), "Broken preview chain")
        require(hash(read(base + step + "/sources/" + r.head)) == r(2))
      }
      state ++ plan.map(r => r.head -> r(2))
    }
    expected.foreach { case(p,h) => require(hash(read(p)) == h, "Unplanned source edit: " + p) }
    val built = rows(base + "build-1/sources.tsv").map(r => r.head -> r(1)).toMap
    require(built == expected, "Build did not compile the complete patched universe")
    require(lines(base + "build-1/status.txt").contains("PASS"), "Build failed")
    val initial = rows(base + "tests/baseline.tsv")
    val before = rows(base + "probe-tests-2/baseline.tsv")
    val after = rows(base + "probe-tests-2/after.tsv")
    require(initial.size == 38 && initial.forall(_(2) == "passed"))
    require(initial.map(_.take(2)) == before.map(_.take(2)) && before.map(_.take(2)) == after.map(_.take(2)))
    val failed = before.filter(_(2) != "passed")
    require(failed.size == 1 && failed.head(2) == "failure" &&
      failed.head(1) == "a fetch writes every layer; the status and the tiles follow; rm removes them" &&
      failed.head(3).contains("2000-01-02T03:04:05Z"), "Expected fixed-clock probe failure missing")
    require(after.forall(_(2) == "passed"), "Probe or unchanged baseline test still fails")
    val review = rows("refactor-audit/capability-treatment/review-3/findings.tsv")
    val changedPaths = rows(base + "preview-1/plan.tsv").map(_.head).toSet
    val repaired = review.filter(r => changedPaths(r(5)) && r(4) == "clock")
    require(repaired.size == 8 && repaired.map(_(1).toInt).sum == 9 && repaired.forall(_(9) == "confirmed"))
    val dirs = Files.list(Paths.get("refactor-audit/capability-history/observations"))
    val latest = try dirs.iterator.asScala.filter(Files.isDirectory(_)).toList.sortBy(_.toString).last.toString finally dirs.close()
    val sourceFingerprint = hash(expected.toList.sorted.map { case(p,h) => p + "\t" + h }.mkString("\n"))
    require(lines(latest + "/sources.txt") == List(sourceFingerprint), "Capability observation is stale")
    require(lines(latest + "/status.txt").head == "PASS")
    val remaining = rows(latest + "/findings.tsv")
    val repairedIds = repaired.map(_.head).toSet
    require(remaining == review.filterNot(r => repairedIds(r.head)).map(_.take(9)), "Unexpected identity or multiplicity change")
    require(lines("refactor-audit/assessment/current/status.txt").head == "MEASUREMENT_COMPLETE")
    val report = Paths.get(base + "verification-1")
    require(!Files.exists(report)); Files.createDirectories(report)
    Files.copy(Paths.get("target/batch-test-verification/server-clock-probe-tests/baseline.log"), report.resolve("probe-constructor-failure.log"))
    val ledger = review.map(r => r :+ (if(repairedIds(r.head)) "verified-fixed" else "retained"))
    Files.write(report.resolve("treatment.tsv"), (lines("refactor-audit/capability-treatment/review-3/findings.tsv").head +
      "\tstatus" :: ledger.map(_.mkString("\t"))).asJava)
    Files.write(report.resolve("events.tsv"), ("id\toccurrences\tevent" :: repaired.map(r => List(r.head,r(1),"verified-clock-injection").mkString("\t"))).asJava)
    Files.write(report.resolve("status.txt"), List("PASS", "compiled-sources=" + expected.size,
      "verified-anchors=8", "verified-occurrences=9", "unchanged-test-identities=38", "probe-fails-before-passes-after=true",
      "current-capability-occurrences=" + remaining.map(_(1).toInt).sum, "observation=" + latest,
      "source-fingerprint=" + sourceFingerprint, "independent-detector-quality=unestablished").asJava)
    println("Verified nine clock repairs with retained identities, full compilation and a failing-before/passing-after probe")
  }
}
