import scalafix.v1._
import java.nio.file.{Files,Paths}
import scala.jdk.CollectionConverters._

class TransactionModeVerification extends SyntacticRule("TransactionModeVerification") {
  override def afterComplete(): Unit = {
    val root = Paths.get("target/transaction-mode-verification"); Files.createDirectories(root)
    def run(label: String, args: List[String], expected: Int = 0): Unit = {
      val log = root.resolve(label + ".log")
      val result = new ProcessBuilder(args.asJava).redirectErrorStream(true).redirectOutput(log.toFile).start().waitFor()
      require(result == expected,s"$label exited $result, expected $expected; see $log")
    }
    val fixture = "tools/refactor/transaction-mode-fixture/Examples.scala"
    run("compile",List("scala-cli","compile",fixture,"--server=false","-S","3.10.0","--scalac-option","-source:future",
      "--scalac-option","-Xsemanticdb","--scalac-option","-sourceroot","--scalac-option",Paths.get("").toAbsolutePath.toString,
      "--compilation-output","target/transaction-mode-fixture-classes"))
    val cli = List("scala-cli","run","--server=false","-q","-S","2.13.18","--dep","ch.epfl.scala:scalafix-cli_2.13.18:0.14.9","--main-class","scalafix.cli.Cli","--")
    val arguments = List("--scala-version","3.10.0","--classpath","target/transaction-mode-fixture-classes","--files",fixture)
    run("check",cli ++ List("--config","tools/refactor/transaction-mode-fixture/check.conf") ++ arguments)
    val report = Paths.get("refactor-audit/transaction-mode-fixture")
    val findings = Files.readAllLines(report.resolve("findings.tsv")).asScala.drop(1)
    require(findings.size == 6,"Expected five direct and one helper-mediated transaction diagnostics")
    val unresolved = Files.readAllLines(report.resolve("unresolved.tsv")).asScala.drop(1)
    require(unresolved.size == 3 && List("dynamic().","commented().","compound().").forall(owner => unresolved.count(_.contains(owner)) == 1),
      "Unsupported SQL cases must remain explicit unresolved evidence")
    run("reject",cli ++ List("--config","tools/refactor/transaction-mode-fixture/reject.conf") ++ arguments,1)
    Files.write(report.resolve("round-trip.txt"),List("PASS","Compiled Scala 3 fixture; six exact expected diagnostics; negative lookalikes unchanged.",
      "Dynamic, comment-prefixed and compound SQL retained as three unresolved cases.","Normal enforcement rejects direct and helper-mediated violations with exit 1.").asJava)
    println("PASS: transaction mode fixtures, unresolved evidence and negative enforcement")
  }
}
