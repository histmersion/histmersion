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
    run("classpath", List("sbt", "--server", "-batch", "set every scalafmtOnCompile := false", "set ThisBuild / scalacOptions += \"-Xsemanticdb\"", "show server/Test/fullClasspath"))
    // `sbt show`'s lines, one `[info] * Attributed(…)` per entry (sbt 2.1's `export` prints nothing on a cache hit), or
    // an older log's `export` line, `List(…)`
    val lines = Files.readAllLines(logs.resolve("classpath.log")).asScala.toList
    val shown = lines.collect { case l if l.startsWith("[info] * Attributed(") && l.endsWith(")") => l.stripPrefix("[info] * Attributed(").stripSuffix(")") }
    val exported = lines.filter(s => s.startsWith("List(") && s.endsWith(")") && s.contains("magnum_3"))
    require(shown.exists(_.contains("magnum_3")) || exported.size == 1)
    val entries = (if (shown.nonEmpty) shown else exported.head.stripPrefix("List(").stripSuffix(")").split(", ").toList).map(_.takeWhile(_ != '>')
      .replace("${OUT}", Paths.get("target/out").toAbsolutePath.toString)
      .replace("${CSR_CACHE}", Paths.get(System.getProperty("user.home"), "Library/Caches/Coursier/v1").toString))
    require(entries.forall(p => Files.isRegularFile(Paths.get(p))))
    // the build's Scala, as sbt writes it from project/Dependencies.scala
    val scalaVersion = Files.readAllLines(Paths.get("target/build-versions.properties")).asScala.collectFirst { case l if l.startsWith("scala=") => l.stripPrefix("scala=") }
      .getOrElse(sys.error("target/build-versions.properties names no scala=: load the build once"))
    run("contracts", List("scala-cli", "run", "tools/refactor/agent-storage", "--server=false", "-S", scalaVersion, "--scalac-option", "-source:future",
      "--classpath", entries.mkString(java.io.File.pathSeparator), "--main-class", "com.histmersion.domain.automation.agents.AgentStorageCheck"))
    val out = Paths.get("refactor-audit/agent-storage"); Files.createDirectories(out)
    Files.write(out.resolve("template-verification.txt"), List("PASS", "Replacement templates compile and execute capability contracts against the evaluated server classpath.",
      "This is template validation, not an applied application repair or caller-compilation proof.").asJava)
  }
}
