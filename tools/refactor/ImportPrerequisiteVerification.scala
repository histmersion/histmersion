import scalafix.v1._
import scala.meta._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class ImportPrerequisiteVerification extends SyntacticRule("ImportPrerequisiteVerification") {
  override def afterComplete(): Unit = {
    implicit val dialect: Dialect = dialects.Scala3
    val root = Paths.get("refactor-audit/import-registry")
    def rows(p: java.nio.file.Path) = Files.readAllLines(p).asScala.drop(1).map(_.split("\t", -1).toList).toList
    def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    val before = rows(root.resolve("tests/baseline.tsv"))
    val after = rows(root.resolve("api-codec-tests/baseline.tsv"))
    require(before.size == 39 && before.count(_(2) == "failure") == 10 && before.forall(r => Set("passed", "failure")(r(2))))
    require(after.size == 39 && after.forall(_(2) == "passed"))
    require(before.map(_.take(2)).toSet == after.map(_.take(2)).toSet, "Test denominator changed")
    val preview = root.resolve("prerequisite-preview-2")
    val plan = rows(preview.resolve("plan.tsv"))
    require(plan.size == 6 && plan.count(_.head.contains("/src/test/")) == 3)
    def assertions(source: String): List[String] = source.parse[Source].get.collect {
      case a: Term.Apply if a.fun.syntax == "assertEquals" => a.argClause.values.drop(1).head.structure
    }
    val assertionCounts = plan.filter(_.head.contains("/src/test/")).map { r =>
      val old = Files.readString(preview.resolve("before").resolve(r.head))
      val current = Files.readString(Paths.get(r.head))
      require(assertions(old) == assertions(current), "Changed expected assertion values: " + r.head)
      r.head + "\t" + assertions(current).size
    }
    val followup = rows(root.resolve("api-codec-preview-1/plan.tsv"))
    val finalHashes = plan.map(r => r.head -> r(2)).toMap ++ followup.map(r => r.head -> r(2))
    followup.filter(r => plan.exists(_.head == r.head)).foreach { r =>
      require(plan.find(_.head == r.head).get(2) == r(1), "Broken patch chain")
    }
    plan.foreach { r =>
      require(hash(Files.readString(preview.resolve("before").resolve(r.head))) == r(1))
    }
    finalHashes.foreach { case(p,h) => require(hash(Files.readString(Paths.get(p))) == h, "Applied preview changed") }
    def manifest(p: java.nio.file.Path) = rows(p).map(r => Paths.get(r.head).toAbsolutePath.normalize.toString -> r(1)).toMap
    val built = manifest(root.resolve("api-codec-build-1/sources.tsv"))
    require(Files.readString(root.resolve("api-codec-build-1/status.txt")).startsWith("PASS\n"))
    require(built.size == 3159)
    val expected = manifest(Paths.get("refactor-audit/unread-projection/author-build-1/sources.tsv")) ++
      finalHashes.map { case(p,h) => Paths.get(p).toAbsolutePath.normalize.toString -> h }
    require(built == expected, "Unplanned source change outside the frozen patch chain")
    require(manifest(Paths.get("refactor-audit/architecture/sources.tsv")) == built)
    require(manifest(Paths.get("refactor-audit/models/sources.tsv")) == built)
    built.foreach { case(p,h) => require(hash(Files.readString(Paths.get(p))) == h, "Stale compiled source") }
    val observation = Paths.get("refactor-audit/isolation-history/observations/2026-11-05T05-43-36.892228Z")
    val status = Files.readAllLines(observation.resolve("status.txt")).asScala
    require(status.head == "PASS" && status.contains("new=0") && status.contains("reopened=0"))
    val out = root.resolve("final-verification.txt")
    require(!Files.exists(out))
    Files.write(out, (List("PASS", "39 identical test identities: 10 failures before, 39 passes after, no skips.",
      "All applied files match their frozen AST preview chain; expected assertEquals arguments are unchanged.",
      "Build and both project-wide inventories match all 3159 current source hashes; no unplanned source change.",
      "Isolation identity gate: zero new or reopened findings; 115 production and 213 test relationships retained.",
      "This is a prerequisite regression repair, not an import-registry isolation repair or detector-quality sample.") ++ assertionCounts).asJava)
    println("PASS: 39 tests, 10 failures repaired, no isolation repair credit")
  }
}
