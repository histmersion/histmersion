import scalafix.v1._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class AgentStoragePreviewVerification extends SyntacticRule("AgentStoragePreviewVerification") {
  override def afterComplete(): Unit = {
    val logs = Paths.get("target/agent-storage-verification")
    val exported = Files.readAllLines(logs.resolve("classpath.log")).asScala.filter(s => s.startsWith("List(") && s.endsWith(")") && s.contains("magnum_3")).toList
    require(exported.size == 1)
    val entries = exported.head.stripPrefix("List(").stripSuffix(")").split(", ").toList.map(_.takeWhile(_ != '>')
      .replace("${OUT}", Paths.get("target/out").toAbsolutePath.toString)
      .replace("${CSR_CACHE}", Paths.get(System.getProperty("user.home"), "Library/Caches/Coursier/v1").toString))
    require(entries.forall(p => Files.isRegularFile(Paths.get(p))))
    val arguments = List("scala-cli", "compile", "target/agent-storage-preview", "--test", "--server=false", "-S", "3.10.0", "--scalac-option", "-source:future",
      "--classpath", entries.mkString(java.io.File.pathSeparator))
    val code = new ProcessBuilder(arguments.asJava).redirectErrorStream(true).redirectOutput(logs.resolve("preview.log").toFile).start().waitFor()
    require(code == 0, s"Preview compilation failed ($code); see $logs/preview.log")
    Files.write(Paths.get("refactor-audit/agent-storage/preview-verification.txt"), List("PASS", "All preview sources compile together against the evaluated server test classpath; application unchanged.").asJava)
    println("PASS: preview definitions and callers compile together")
  }
}
