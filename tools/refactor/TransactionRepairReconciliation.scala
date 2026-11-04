import scalafix.v1._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class TransactionRepairReconciliation extends SyntacticRule("TransactionRepairReconciliation") {
  override def afterComplete(): Unit = {
    def lines(path: String): List[String] = Files.readAllLines(Paths.get(path)).asScala.toList
    def hash(s: String): String = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    val out = "refactor-audit/transaction-repair/"
    val ledger = "refactor-audit/capability-history/"
    val baseline = lines(ledger + "baseline.tsv").drop(1).map(_.split("\t", -1)).map(r => (r(0), r(1).toInt, r(4), r(5)))
    val additions = lines(ledger + "adoptions/transaction-round1/additions.tsv").drop(1).map(_.split("\t", -1)).map(r => (r(0), r(1).toInt, r(5), r(6)))
    val before = baseline ++ additions
    require(before.map(_._1).distinct.size == before.size, "Duplicate baseline identity")
    val root = Paths.get("").toAbsolutePath.normalize
    val current = lines(out + "capabilities/findings.tsv").drop(1).map(_.split("\t", -1)).map { r =>
      require(r.length == 9)
      val relative = root.relativize(Paths.get(r(1)).toAbsolutePath.normalize).toString
      "CAP-" + hash(List(r(0), relative, r(4), r(5), r(7)).mkString("\t"))
    }.groupBy(identity).view.mapValues(_.size).toMap
    require(lines(out + "capabilities/missing-files.txt").isEmpty)
    val expected = before.filterNot(_._3 == "repo-transaction").map(r => r._1 -> r._2).toMap
    require(current == expected, "Unexpected new, removed, or multiplicity-changed non-transaction identity")
    val removed = before.filter(_._3 == "repo-transaction")
    require(removed.map(_._2).sum == 22 && !removed.exists(r => current.contains(r._1)))
    require(lines(out + "database/after.txt") == List("tests=62", "failures=0", "skipped=0", "db-exit=0", "server-exit=0"))
    def tests(path: String): List[String] = lines(path).drop(1).sorted
    require(tests(out + "database/baseline.tsv") == tests(out + "database/after.tsv"), "Database test identities or outcomes differ")
    val owners = Map("QueryRepo.scala" -> "TX-0001", "GazetteerRepo.scala" -> "TX-0002", "VacuumRepo.scala" -> "TX-0003")
    val rows = removed.sortBy(_._1).map { case (id, count, _, path) =>
      val boundary = owners.getOrElse(Paths.get(path).getFileName.toString, sys.error("Unexpected transaction owner: " + path))
      List(id, count.toString, boundary, "verified-fixed", "targeted-rewrite", path).mkString("\t")
    }
    Files.write(Paths.get(out + "resolved-identities.tsv"), ("id\tcount\tboundary-id\tstatus\ttreatment\tpath" :: rows).asJava)
    Files.write(Paths.get(out + "identity-verification.txt"), List("PASS", "removed-transaction-occurrences=22", "verified-boundaries=3", "unchanged-other-occurrences=209", "new-identities=0", "unexpected-removals=0", "multiplicity-changes=0", "scope=production", "test-capability-regressions=unmeasured", "database-test-identities=62; unchanged; no failures or skips").asJava)
    println("PASS: 22 removed transaction occurrences; all 209 other occurrences retain their identities and multiplicities")
  }
}
