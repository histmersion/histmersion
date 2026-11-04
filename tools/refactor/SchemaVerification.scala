import scalafix.v1._
import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets.UTF_8
import scala.jdk.CollectionConverters._

class SchemaVerification extends SyntacticRule("SchemaVerification") {
  override def afterComplete(): Unit = {
    val logs = Paths.get("target/schema-verification"); Files.createDirectories(logs)
    val report = Paths.get("refactor-audit/schema-derives/verification.txt")
    Files.createDirectories(report.getParent)
    Files.writeString(report, "RUNNING\n")
    def run(label: String, args: List[String], expected: Int = 0): Unit = {
      println("Schema verification: " + label)
      val result = new ProcessBuilder(args.asJava).redirectErrorStream(true).redirectOutput(logs.resolve(label + ".log").toFile).start().waitFor()
      require(result == expected, s"$label exited $result, expected $expected; see $logs/$label.log")
    }
    val scala = List("--server=false", "-S", "3.10.0", "--scalac-option", "-source:future",
      "--scalac-option", "-Xsemanticdb", "--scalac-option", "-sourceroot", "--scalac-option", Paths.get("").toAbsolutePath.toString,
      "--dep", "com.kubuszok::kindlings-tapir-schema-derivation:0.3.3", "--dep", "com.kubuszok::kindlings-circe-derivation:0.3.3")
    val cli = List("scala-cli", "run", "--server=false", "-q", "-S", "2.13.18", "--dep", "ch.epfl.scala:scalafix-cli_2.13.18:0.14.9", "--main-class", "scalafix.cli.Cli", "--")
    val fixture = "tools/refactor/schema-fixture/"
    val output = "target/schema-fixture-output/src/main/scala"
    def fix(config: String, source: String, cp: String, extra: List[String] = Nil, expected: Int = 0): Unit =
      run(config, cli ++ List("--config", fixture + config + ".conf", "--scala-version", "3.10.0", "--sourceroot", ".", "--classpath", cp, "--files", source) ++ extra, expected)
    run("input-compile", List("scala-cli", "compile", fixture + "input/src/main/scala", fixture + "adapter") ++ scala ++ List("--compilation-output", "target/schema-fixture-classes"))
    run("expected-run", List("scala-cli", "run", fixture + "expected", fixture + "adapter") ++ scala ++ List("--main-class", "schemafixture.verify"))
    fix("inspect", fixture + "input", "target/schema-fixture-classes")
    val inventory = Files.readAllLines(Paths.get("target/schema-fixture-report/inspect-summary.txt"), UTF_8).asScala
    require(inventory.contains("eligible=2") && inventory.contains("review=0"), "Fixture classifications differ")
    fix("check-before", fixture + "input", "target/schema-fixture-classes", expected = 1)
    fix("apply", fixture + "input", "target/schema-fixture-classes", List("--out-from", fixture + "input/src/main/scala", "--out-to", output))
    run("whitespace", cli ++ List("--rules", "file:tools/refactor/SchemaPatchWhitespace.scala", "--scala-version", "3.10.0", "--files", output))
    val rewritten = Files.readString(Paths.get(output, "Example.scala"))
    require(rewritten == Files.readString(Paths.get(fixture + "expected/Example.scala")), "Rewrite differs from expected output")
    run("output-run", List("scala-cli", "run", output, fixture + "adapter") ++ scala ++ List("--main-class", "schemafixture.verify", "--compilation-output", "target/schema-fixture-output-classes"))
    fix("check-after", output, "target/schema-fixture-output-classes")
    fix("inspect-after", output, "target/schema-fixture-output-classes")
    fix("apply-after", output, "target/schema-fixture-output-classes")
    require(rewritten == Files.readString(Paths.get(output, "Example.scala")), "Rewrite is not idempotent")
    run("adapter-behavior", List("scala-cli", "run", "tools/refactor/derivation-fixture/src/main/scala") ++ scala ++ List("--main-class", "derivationfixture.verifySchemas"))
    Files.write(report, List("PASS", "input and expected compile", "exact expected rewrite", "negative check: 2 diagnostics, exit 1", "rewritten output compiles and runs", "positive check: 0 diagnostics", "idempotent second application", "nested automatic derivation and all schema constructor fields preserved").asJava, UTF_8)
  }
}
