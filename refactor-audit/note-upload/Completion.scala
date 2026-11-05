import scalafix.v1._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class Completion extends SyntacticRule("Completion") {
  override def afterComplete(): Unit = {
    val dir = Paths.get("refactor-audit/note-upload")
    def rows(path: String) = Files.readAllLines(Paths.get(path)).asScala.drop(1).map(_.split("\t", -1).toList).toList
    def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    val observations = Paths.get("refactor-audit/capability-history/observations")
    val stream = Files.list(observations)
    val observation = try stream.iterator.asScala.toList.sortBy(_.toString).last finally stream.close()
    val previous = observations.resolve("2026-11-05T00-25-31.583908Z")
    require(observation != previous, "No new observation")
    def findings(p: java.nio.file.Path) = rows(p.resolve("findings.tsv").toString).map(r => r.head -> r(1).toInt).toMap
    val old = findings(previous)
    val now = findings(observation)
    val review = rows("refactor-audit/capability-treatment/review-1/findings.tsv")
    val scope = Set(
      "modules/files/src/main/scala/com/histmersion/domain/library/sources/SourceNote.scala",
      "modules/server/src/main/scala/com/histmersion/domain/library/ingest/Upload.scala")
    val repaired = review.filter(r => scope(r(5)) && r(6) == "confirmed")
    val legitimate = "CAP-f7e1cc51f4b1060694b76771bfe702a1982296b72ee46e6110dbfb8e38bfa5ae"
    require(repaired.size == 4 && repaired.map(_(1).toInt).sum == 4)
    require(review.exists(r => r.head == legitimate && r(6) != "confirmed"))
    require(now == (old -- repaired.map(_.head) - legitimate), "Unexpected identity or multiplicity change")
    require(now.size == 125 && now.values.sum == 147)
    require(Files.readString(observation.resolve("status.txt")).startsWith("PASS\n"))
    require(Files.readString(Paths.get("refactor-audit/assessment/current/status.txt")).startsWith("MEASUREMENT_COMPLETE\n"))
    val plan = rows(dir.resolve("plan.tsv").toString)
    require(plan.size == 14)
    plan.foreach(r => require(hash(Files.readString(Paths.get(r.head))) == r(2), "Source changed: " + r.head))
    List("files-tests" -> 22, "server-tests" -> 7).foreach { case (testDir, expected) =>
      def tests(phase: String) = rows(dir.resolve(testDir).resolve(phase + ".tsv").toString).map(r => r.take(2) -> r(2)).toMap
      val before = tests("baseline")
      val after = tests("after")
      require(before == after && after.size == expected && after.values.forall(_ == "passed"), "Test coverage/outcome changed")
    }
    val evidence = "plan.tsv; applied-verification.txt; preview-verification.txt; " + observation.toString
    Files.write(dir.resolve("verified-findings.tsv"), ("id\toccurrences\tscope\tstatus\tevidence" :: repaired.map(r =>
      List(r.head, r(1), "production", "verified-targeted-rewrite", evidence).mkString("\t"))).asJava)
    Files.write(dir.resolve("nonviolation-removal.tsv"), List("id\tstatus\tevidence", legitimate + "\tremoved-legitimate-operation-not-a-repair\t" + evidence).asJava)
    Files.write(dir.resolve("completion.txt"), List("PASS: note/upload batch reconciled",
      "4 confirmed production occurrences / 4 anchors repaired; 1 previously legitimate operation removed separately.",
      "147 raw production findings / 125 anchors remain; all other identities and multiplicities unchanged.",
      "14 final source hashes match the frozen preview; 29 tests retain passing identities and outcomes.",
      "Overall migration and independent detector-quality validation remain open.").asJava)
    println("PASS: note/upload source hashes, tests and finding identities reconciled")
  }
}
