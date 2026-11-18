import scalafix.v1._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class PluginCapabilityPreviewVerification extends SyntacticRule("PluginCapabilityPreviewVerification") {
  override def afterComplete(): Unit = {
    // `sbt show`'s lines, one `[info] * Attributed(…)` per entry, or an older log's `export` line, `List(…)`
    val lines = Files.readAllLines(Paths.get("target/agent-storage-verification/classpath.log")).asScala.toList
    val shown = lines.collect { case l if l.startsWith("[info] * Attributed(") && l.endsWith(")") => l.stripPrefix("[info] * Attributed(").stripSuffix(")") }
    val exported = lines.filter(s => s.startsWith("List(") && s.endsWith(")") && s.contains("magnum_3"))
    require(shown.exists(_.contains("magnum_3")) || exported.size == 1)
    val entries = (if (shown.nonEmpty) shown else exported.head.stripPrefix("List(").stripSuffix(")").split(", ").toList).map(_.takeWhile(_ != '>')
      .replace("${OUT}", Paths.get("target/out").toAbsolutePath.toString)
      .replace("${CSR_CACHE}", Paths.get(System.getProperty("user.home"), "Library/Caches/Coursier/v1").toString))
    require(entries.forall(p => Files.isRegularFile(Paths.get(p))))
    val logs = Paths.get("target/plugin-capability-verification"); Files.createDirectories(logs)
    // the build's Scala, as sbt writes it from project/Dependencies.scala
    val scalaVersion = Files.readAllLines(Paths.get("target/build-versions.properties")).asScala.collectFirst { case l if l.startsWith("scala=") => l.stripPrefix("scala=") }
      .getOrElse(sys.error("target/build-versions.properties names no scala=: load the build once"))
    val args = List("scala-cli", "compile", "target/plugin-capability-preview", "--test", "--server=false", "-S", scalaVersion,
      "--scalac-option", "-source:future", "--classpath", entries.mkString(java.io.File.pathSeparator))
    val code = new ProcessBuilder(args.asJava).redirectErrorStream(true).redirectOutput(logs.resolve("preview.log").toFile).start().waitFor()
    require(code == 0, s"Preview compilation failed ($code); see $logs/preview.log")
    Files.write(Paths.get("refactor-audit/plugin-capabilities/preview-verification.txt"), List("PASS",
      "Combined preview definitions and callers, including tests, compile against evaluated server test classpath.",
      "Module-local dependency validation is still required; combined compilation does not prove module isolation.").asJava)
    println("PASS: combined preview definitions and callers compile; module-local validation remains")
  }
}
