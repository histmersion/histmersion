import scalafix.v1._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class AgentStorageVerification extends SyntacticRule("AgentStorageVerification") {
  override def afterComplete(): Unit = {
    val logs = Paths.get("target/agent-storage-verification"); Files.createDirectories(logs)
    def run(label: String, args: List[String]): Unit = {
      val code = new ProcessBuilder(args.asJava).redirectErrorStream(true).redirectOutput(logs.resolve(label + ".log").toFile).start().waitFor()
      require(code == 0, s"$label failed ($code); see $logs/$label.log")
      println(label + ": PASS")
    }
    run("classpath", List("sbt", "--server", "-batch", "set every scalafmtOnCompile := false", "set ThisBuild / scalacOptions += \"-Xsemanticdb\"", "export server/Test/fullClasspath"))
    val exported = Files.readAllLines(logs.resolve("classpath.log")).asScala.filter(s => s.startsWith("List(") && s.endsWith(")") && s.contains("magnum_3")).toList
    require(exported.size == 1)
    val entries = exported.head.stripPrefix("List(").stripSuffix(")").split(", ").toList.map(_.takeWhile(_ != '>')
      .replace("${OUT}", Paths.get("target/out").toAbsolutePath.toString)
      .replace("${CSR_CACHE}", Paths.get(System.getProperty("user.home"), "Library/Caches/Coursier/v1").toString))
    require(entries.forall(p => Files.isRegularFile(Paths.get(p))))
    run("contracts", List("scala-cli", "run", "tools/refactor/agent-storage", "--server=false", "-S", "3.10.1", "--scalac-option", "-source:future",
      "--classpath", entries.mkString(java.io.File.pathSeparator), "--main-class", "com.histmersion.domain.automation.agents.AgentStorageCheck"))
    val out = Paths.get("refactor-audit/agent-storage"); Files.createDirectories(out)
    Files.write(out.resolve("template-verification.txt"), List("PASS", "Replacement templates compile and execute capability contracts against the evaluated server classpath.",
      "This is template validation, not an applied application repair or caller-compilation proof.").asJava)
  }
}
