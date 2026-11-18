import scalafix.v1._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class ApprovalWiringVerification extends SyntacticRule("ApprovalWiringVerification") {
  override def afterComplete(): Unit = {
    val logs = Paths.get("target/approval-wiring-verification-v2"); Files.createDirectories(logs)
    val classpathLog = Paths.get("target/approval-extraction-verification-v3/classpath.log")
    val exported = Files.readAllLines(classpathLog).asScala.filter(s => s.startsWith("List(") && s.endsWith(")") && s.contains("magnum_3")).toList
    require(exported.size == 1)
    val entries = exported.head.stripPrefix("List(").stripSuffix(")").split(", ").toList.map(_.takeWhile(_ != '>')
      .replace("${OUT}",Paths.get("target/out").toAbsolutePath.toString)
      .replace("${CSR_CACHE}",Paths.get(System.getProperty("user.home"),"Library/Caches/Coursier/v1").toString))
    require(entries.forall(p => Files.isRegularFile(Paths.get(p))))
    val args = List("scala-cli","compile","target/approval-wiring-preview-v2","--test","--server=false","-S","3.10.1","--scalac-option","-source:future","--classpath",entries.mkString(java.io.File.pathSeparator))
    val code = new ProcessBuilder(args.asJava).redirectErrorStream(true).redirectOutput(logs.resolve("preview.log").toFile).start().waitFor()
    require(code == 0,"Wiring preview compile failed; see " + logs.resolve("preview.log"))
    Files.write(Paths.get("refactor-audit/approval-isolation/wiring/v2/verification.txt"),List("PASS: all 14 preview definitions and callers, including tests, compile together.",
      "The evaluated server classpath is used; actual module compilation and behavior comparison remain required after application.").asJava)
    println("PASS: complete approval wiring preview compiles")
  }
}
