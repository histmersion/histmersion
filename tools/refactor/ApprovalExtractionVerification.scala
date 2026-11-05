import scalafix.v1._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class ApprovalExtractionVerification extends SyntacticRule("ApprovalExtractionVerification") {
  override def afterComplete(): Unit = {
    val logs = Paths.get("target/approval-extraction-verification-v3"); Files.createDirectories(logs)
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
    run("preview", List("scala-cli", "compile", "target/approval-extraction-preview-v3", "--server=false", "-S", "3.10.0", "--scalac-option", "-source:future", "--classpath", entries.mkString(java.io.File.pathSeparator)))
    Files.write(Paths.get("refactor-audit/approval-isolation/extraction/v3/verification.txt"),List("PASS: extracted definitions compile together on the evaluated server classpath.",
      "This is a preview only. Wiring, old-method removal, module-local compilation and before/after contract execution remain required.").asJava)
  }
}
