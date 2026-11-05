import scalafix.v1._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class PluginCapabilityPreviewVerification extends SyntacticRule("PluginCapabilityPreviewVerification") {
  override def afterComplete(): Unit = {
    val exported = Files.readAllLines(Paths.get("target/agent-storage-verification/classpath.log")).asScala
      .filter(s => s.startsWith("List(") && s.endsWith(")") && s.contains("magnum_3")).toList
    require(exported.size == 1)
    val entries = exported.head.stripPrefix("List(").stripSuffix(")").split(", ").toList.map(_.takeWhile(_ != '>')
      .replace("${OUT}", Paths.get("target/out").toAbsolutePath.toString)
      .replace("${CSR_CACHE}", Paths.get(System.getProperty("user.home"), "Library/Caches/Coursier/v1").toString))
    require(entries.forall(p => Files.isRegularFile(Paths.get(p))))
    val logs = Paths.get("target/plugin-capability-verification"); Files.createDirectories(logs)
    val args = List("scala-cli", "compile", "target/plugin-capability-preview", "--test", "--server=false", "-S", "3.10.0",
      "--scalac-option", "-source:future", "--classpath", entries.mkString(java.io.File.pathSeparator))
    val code = new ProcessBuilder(args.asJava).redirectErrorStream(true).redirectOutput(logs.resolve("preview.log").toFile).start().waitFor()
    require(code == 0, s"Preview compilation failed ($code); see $logs/preview.log")
    Files.write(Paths.get("refactor-audit/plugin-capabilities/preview-verification.txt"), List("PASS",
      "Combined preview definitions and callers, including tests, compile against evaluated server test classpath.",
      "Module-local dependency validation is still required; combined compilation does not prove module isolation.").asJava)
    println("PASS: combined preview definitions and callers compile; module-local validation remains")
  }
}
