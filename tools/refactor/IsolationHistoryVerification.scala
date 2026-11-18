import scalafix.v1._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class IsolationHistoryVerification extends SyntacticRule("IsolationHistoryVerification") {
  override def afterComplete(): Unit = {
    val root = Paths.get("target/isolation-history-verification",java.time.Instant.now().toString.replace(':','-'))
    val original = Paths.get("refactor-audit/isolation-history")
    val observations = Files.list(original.resolve("observations"))
    val latest = try observations.iterator.asScala.toList.sortBy(_.toString).last finally observations.close()
    val baseline = Files.readAllLines(latest.resolve("findings.tsv")).asScala.toList
    val first = baseline(1).split("\t",-1).head
    val scenarios = List("unchanged", "equal-count-substitution", "reopened")
    scenarios.foreach { scenario =>
      val dir = root.resolve(scenario)
      Files.createDirectories(dir)
      Files.copy(original.resolve("contract.txt"),dir.resolve("contract.txt"))
      val rows = if(scenario == "equal-count-substitution") baseline.updated(1,baseline(1).replace(first,"ISO-previous-different-relationship")) else baseline
      Files.write(dir.resolve("baseline.tsv"),rows.asJava)
      if(scenario == "reopened") {
        val previous = dir.resolve("observations/previous")
        Files.createDirectories(previous)
        Files.write(previous.resolve("absent.txt"),List(first).asJava)
      }
      val conf = dir.resolve("check.conf")
      Files.write(conf,List("rules = [\"file:tools/refactor/IsolationHistory.scala\"]", "IsolationHistory.ledger = \"" + dir.toString + "\"").asJava)
      val args = List("bash","tools/refactor/scalafix.sh",
        "--config",conf.toString,"--scala-version","3.10.0","--files","tools/refactor/IsolationHistory.scala")
      val code = new ProcessBuilder(args.asJava).redirectErrorStream(true).redirectOutput(dir.resolve("run.log").toFile).start().waitFor()
      require((code == 0) == (scenario == "unchanged"), "Unexpected exit for " + scenario)
      val stream = Files.list(dir.resolve("observations"))
      val observation = try stream.iterator.asScala.filter(_.getFileName.toString != "previous").toList.head finally stream.close()
      val status = Files.readAllLines(observation.resolve("status.txt")).asScala.toList
      require(status.head == (if(scenario == "unchanged") "PASS" else "FAIL"))
      if(scenario != "unchanged") {
        val event = if(scenario == "reopened") "reopened.txt" else "new.txt"
        require(Files.readAllLines(observation.resolve(event)).asScala.toList == List(first), "Wrong regression identity")
      }
      println("PASS: " + scenario)
    }
    Files.write(original.resolve("verification.txt"),List("PASS: actual Scalafix CLI unchanged/equal-count substitution/reopened scenarios",
      "logs=" + root.toString, "No application sources or original history were mutated.",
      "Detector precision/recall and automatic project-assessment integration remain unestablished.").asJava)
  }
}
