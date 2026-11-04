import scalafix.v1._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class AgentStorageTestReconciliation extends SyntacticRule("AgentStorageTestReconciliation") {
  override def afterComplete(): Unit = {
    val out = Paths.get("refactor-audit/agent-storage/tests")
    def read(name: String) = {
      val rows = Files.readAllLines(out.resolve(name + ".tsv")).asScala.drop(1).map(_.split("\t", -1).toList).toList
      require(rows.forall(_.size == 4) && rows.map(_.take(2)).distinct.size == rows.size)
      require(Files.readAllLines(out.resolve(name + ".txt")).asScala.toList == List("tests=93", "failures=1", "skipped=0", "exit=1"))
      rows.map(r => r.take(2) -> r).toMap
    }
    val before = read("baseline"); val after = read("after")
    require(before.keySet == after.keySet && after.size == 93, "Test identities changed")
    val marker = "files in plugin/ the generator does not write\\n=> Obtained\\n"
    def payload(s: String): String = {
      val start = s.indexOf(marker)
      require(start >= 0 && start == s.lastIndexOf(marker), "Missing or ambiguous assertion payload")
      val end = s.indexOf("\\n\\tat ", start)
      require(end > start && s.substring(start, end).contains("=> Diff (- expected, + obtained)"))
      s.substring(start, end)
    }
    after.foreach { case (id, row) =>
      require(row(2) == before(id)(2), "Test outcome changed: " + id)
      if(row(2) != "passed") {
        require(id == List("com.histmersion.server.agents.PluginTreeSuite", "plugin/ is up to date with agents/ and skills/ (run `just plugin` when this fails)"))
        require(row(2) == "failure" && payload(row(3)) == payload(before(id)(3)), "Failure payload changed")
      }
    }
    def sha(name: String) = java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(out.resolve(name))).map(b => f"${b & 255}%02x").mkString
    val evidence = List("PASS: saved test reports reconciled; tests were not rerun", "93 identical test identities; 92 passed; 1 unchanged known failure; 0 skipped",
      "Compared complete assertion, obtained value and expected/obtained diff; excluded source coordinates and stack trace only.",
      "Not an all-green test run. Original reports retained unchanged.", "baseline-sha256=" + sha("baseline.tsv"), "after-sha256=" + sha("after.tsv"))
    Files.write(out.resolve("reconciliation.txt"), evidence.asJava)
    println(evidence.mkString("\n"))
  }
}
