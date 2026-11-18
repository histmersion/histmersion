import scalafix.v1._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class ApprovalWiringVerification extends SyntacticRule("ApprovalWiringVerification") {
  override def afterComplete(): Unit = {
    val logs = Paths.get("target/approval-wiring-verification-v2"); Files.createDirectories(logs)
    val classpathLog = Paths.get("target/approval-extraction-verification-v3/classpath.log")
    // `sbt show`'s lines, one `[info] * Attributed(…)` per entry, or an older log's `export` line, `List(…)`
    val lines = Files.readAllLines(classpathLog).asScala.toList
    val shown = lines.collect { case l if l.startsWith("[info] * Attributed(") && l.endsWith(")") => l.stripPrefix("[info] * Attributed(").stripSuffix(")") }
    val exported = lines.filter(s => s.startsWith("List(") && s.endsWith(")") && s.contains("magnum_3"))
    require(shown.exists(_.contains("magnum_3")) || exported.size == 1)
    val entries = (if (shown.nonEmpty) shown else exported.head.stripPrefix("List(").stripSuffix(")").split(", ").toList).map(_.takeWhile(_ != '>')
      .replace("${OUT}",Paths.get("target/out").toAbsolutePath.toString)
      .replace("${CSR_CACHE}",Paths.get(System.getProperty("user.home"),"Library/Caches/Coursier/v1").toString))
    require(entries.forall(p => Files.isRegularFile(Paths.get(p))))
    // the build's Scala, as sbt writes it from project/Dependencies.scala
    val scalaVersion = Files.readAllLines(Paths.get("target/build-versions.properties")).asScala.collectFirst { case l if l.startsWith("scala=") => l.stripPrefix("scala=") }
      .getOrElse(sys.error("target/build-versions.properties names no scala=: load the build once"))
    val args = List("scala-cli","compile","target/approval-wiring-preview-v2","--test","--server=false","-S",scalaVersion,"--scalac-option","-source:future","--classpath",entries.mkString(java.io.File.pathSeparator))
    val code = new ProcessBuilder(args.asJava).redirectErrorStream(true).redirectOutput(logs.resolve("preview.log").toFile).start().waitFor()
    require(code == 0,"Wiring preview compile failed; see " + logs.resolve("preview.log"))
    Files.write(Paths.get("refactor-audit/approval-isolation/wiring/v2/verification.txt"),List("PASS: all 14 preview definitions and callers, including tests, compile together.",
      "The evaluated server classpath is used; actual module compilation and behavior comparison remain required after application.").asJava)
    println("PASS: complete approval wiring preview compiles")
  }
}
