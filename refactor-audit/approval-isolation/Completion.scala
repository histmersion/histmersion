import scalafix.v1._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class Completion extends SyntacticRule("Completion") {
  override def afterComplete(): Unit = {
    val dir = Paths.get("refactor-audit/approval-isolation")
    def rows(path: java.nio.file.Path) = Files.readAllLines(path).asScala.drop(1).map(_.split("\t",-1).toList).toList
    def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    val plan = rows(dir.resolve("wiring/final-plan-v2.tsv")); require(plan.size == 14)
    plan.foreach(r => require(hash(Files.readString(Paths.get(r.head))) == r(2),"Final source drift: " + r.head))
    List("tests" -> 8,"engine-tests-after-codec" -> 30).foreach { case(testDir,count) =>
      def tests(phase: String) = rows(dir.resolve(testDir).resolve(phase + ".tsv")).map(r => r.take(2) -> r(2)).toMap
      val before = tests("baseline"); val after = tests("after")
      require(before == after && after.size == count && after.values.forall(_ == "passed"),"Test identities/outcomes changed")
    }
    val failures = rows(dir.resolve("engine-tests/baseline.tsv"))
    val corrected = rows(dir.resolve("engine-tests-after-codec/baseline.tsv"))
    require(failures.map(_.take(2)).toSet == corrected.map(_.take(2)).toSet)
    require(failures.size == 30 && failures.forall(r => r(2) == "failure" && r(3).contains("unassessed not convertible to Status")),"Unexpected original engine failure evidence")
    val history = Paths.get("refactor-audit/isolation-history")
    val stream = Files.list(history.resolve("observations"))
    val latest = try stream.iterator.asScala.toList.sortBy(_.toString).last finally stream.close()
    require(latest.getFileName.toString > "2026-11-05T00-57-34.783327Z","Missing post-repair observation")
    val expectedInput = "crossings=" + hash(Files.readString(Paths.get("refactor-audit/architecture/dependency-crossings.tsv")))
    require(Files.readAllLines(latest.resolve("inputs.txt")).asScala.contains(expectedInput))
    val before = rows(history.resolve("baseline.tsv"))
    val now = rows(latest.resolve("findings.tsv"))
    val confirmed = rows(history.resolve("review-1.tsv")).filter(_(5) == "confirmed") ++ rows(dir.resolve("confirmed-additions.tsv"))
    val ids = confirmed.map(_.head).toSet; require(ids.size == 54)
    val originalProduction = before.filter(_(1) == "production").map(_.head).toSet
    val currentProduction = now.filter(_(1) == "production").map(_.head).toSet
    require(currentProduction == originalProduction -- ids,"Unexpected production relationship change")
    val originalTest = before.filter(_(1) == "test").map(_.head).toSet
    val testAdditions = now.filter(r => r(1) == "test" && !originalTest(r.head))
    val testAbsences = originalTest -- now.filter(_(1) == "test").map(_.head).toSet
    val adopted = rows(history.resolve("adoptions/approval-tests/decisions.tsv"))
    require(adopted.map(_.head).toSet == testAdditions.map(_.head).toSet && testAbsences.isEmpty,"Unreviewed test relationship change")
    require(adopted.forall(r => r(3) == "non-violation" && hash(Files.readString(Paths.get(r(1)))) == r(2)))
    require(Files.readString(latest.resolve("status.txt")).startsWith("PASS\n"),"Identity gate remains failing")
    val header = "id\tscope\tmodule\tpath\towner\ttarget\tidentity-confidence\tclassification\tevidence-count\tlocations"
    Files.write(dir.resolve("test-relationships-for-review.tsv"),(header :: testAdditions.map(_.mkString("\t"))).asJava)
    Files.write(dir.resolve("test-relationship-absences.txt"),testAbsences.toList.sorted.asJava)
    Files.write(dir.resolve("verified-findings.tsv"),("id\tscope\tstatus\tevidence" :: ids.toList.sorted.map(id =>
      List(id,"production","verified-targeted-rewrite","wiring/final-plan-v2.tsv; tests/after.tsv; engine-tests-after-codec/after.tsv; " + latest.toString).mkString("\t"))).asJava)
    val prior = dir.resolve("completion-before-test-review.txt")
    if(!Files.exists(prior)) Files.copy(dir.resolve("completion.txt"),prior)
    Files.write(dir.resolve("completion.txt"),List("PRODUCTION_REPAIR_VERIFIED", "54 confirmed production relationships removed; all other production relationship identities retained.",
      "38 unchanged passing test identities; original 30 codec failures retained separately and corrected before ownership application.",
      "New test relationships explicitly reviewed as non-violations=" + testAdditions.size,"Test relationship absences=" + testAbsences.size,
      "Latest identity gate passed; integrated assessment status must also be inspected.","Overall migration and independent detector quality remain open.").asJava)
    val codecPath = "modules/db/src/main/scala/com/histmersion/domain/library/sources/SourceRepo.scala"
    val codecBefore = Files.readAllLines(dir.resolve("codec-prerequisite/evidence.txt")).asScala.find(_.startsWith("before-sha256=")).get.stripPrefix("before-sha256=")
    Files.write(dir.resolve("final-source-plan.tsv"),("path\tbefore-sha256\tafter-sha256" ::
      (plan :+ List(codecPath,codecBefore,hash(Files.readString(Paths.get(codecPath))))).sortBy(_.head).map(_.mkString("\t"))).asJava)
    println("PASS: 54 production repairs and 38 test identities; new test relationships=" + testAdditions.size + "; test absences=" + testAbsences.size)
  }
}
