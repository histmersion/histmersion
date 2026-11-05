import scalafix.v1._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class AdoptionVerification extends SyntacticRule("AdoptionVerification") {
  override def afterComplete(): Unit = {
    def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    val original = Paths.get("refactor-audit/isolation-history")
    val event = original.resolve("adoptions/approval-tests")
    val decisions = Files.readAllLines(event.resolve("decisions.tsv")).asScala.toList
    val evidence = Files.readAllLines(event.resolve("event.txt")).asScala.toList
    val root = Paths.get("target/approval-adoption-verification",java.time.Instant.now().toString.replace(':','-'))
    List("accepted","changed-source","changed-classification","changed-baseline","reopened").foreach { scenario =>
      val dir = root.resolve(scenario); val adoption = dir.resolve("adoption"); Files.createDirectories(adoption)
      Files.copy(original.resolve("baseline.tsv"),dir.resolve("baseline.tsv"))
      Files.copy(original.resolve("contract.txt"),dir.resolve("contract.txt"))
      val first = decisions(1).split("\t",-1).toList
      val changed = scenario match {
        case "changed-source" => decisions.updated(1,first.updated(2,"0" * 64).mkString("\t"))
        case "changed-classification" => decisions.updated(1,first.updated(3,"confirmed").mkString("\t"))
        case _ => decisions
      }
      Files.write(adoption.resolve("decisions.tsv"),changed.asJava)
      val sealedEvidence = evidence.map { line =>
        if(line.startsWith("decisions-sha256=")) "decisions-sha256=" + hash(Files.readString(adoption.resolve("decisions.tsv")))
        else if(scenario == "changed-baseline" && line.startsWith("baseline-sha256=")) "baseline-sha256=" + "0" * 64
        else line
      }
      Files.write(adoption.resolve("event.txt"),sealedEvidence.asJava)
      if(scenario == "reopened") {
        val prior = dir.resolve("observations/prior"); Files.createDirectories(prior)
        Files.write(prior.resolve("absent.txt"),List(first.head).asJava)
      }
      val conf = dir.resolve("check.conf")
      Files.write(conf,List("rules = [\"file:tools/refactor/IsolationHistory.scala\"]",
        "IsolationHistory.ledger = \"" + dir.toString + "\"",
        "IsolationHistory.reviewedAdditions = [\"" + adoption.toString + "\"]").asJava)
      val command = List("scala-cli","run","--server=false","-q","-S","2.13.18","--dep","ch.epfl.scala:scalafix-cli_2.13.18:0.14.9","--main-class","scalafix.cli.Cli","--",
        "--config",conf.toString,"--scala-version","3.10.0","--files","tools/refactor/IsolationHistory.scala")
      val log = dir.resolve("run.log")
      val exit = new ProcessBuilder(command.asJava).redirectErrorStream(true).redirectOutput(log.toFile).start().waitFor()
      val output = Files.readString(log)
      val expected = scenario match {
        case "accepted" => "reviewed-additional-test-nonviolations=10"
        case "changed-source" => "Review source changed"
        case "changed-classification" => "Unsupported adoption classification/scope"
        case "changed-baseline" => "Adoption baseline changed"
        case "reopened" => "reopened=1"
      }
      require((exit == 0) == (scenario == "accepted") && output.contains(expected),"Wrong outcome for " + scenario)
      println("PASS: " + scenario)
    }
    Files.write(Paths.get("refactor-audit/approval-isolation/adoption-verification.txt"),List("PASS: actual CLI accepted exact reviewed additions and rejected source drift, changed classification, wrong baseline and reappearance.",
      "Evidence: " + root.toString,"Original ledger and application files unchanged by verification.").asJava)
  }
}
