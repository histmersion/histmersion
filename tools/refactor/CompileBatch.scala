import scalafix.v1._
import metaconfig.{ConfDecoder, Configured, generic}
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

object CompileBatch {
  case class Policy(report: String = "", commands: List[String] = List("compile", "Test/compile"))
  object Policy {
    implicit val surface: generic.Surface[Policy] = generic.deriveSurface[Policy]
    implicit val decoder: ConfDecoder[Policy] = generic.deriveDecoder(Policy()).noTypos
  }
}
class CompileBatch(val policy: CompileBatch.Policy) extends SyntacticRule("CompileBatch") {
  def this() = this(CompileBatch.Policy())
  override def withConfiguration(c: Configuration): Configured[Rule] = c.conf.getOrElse("CompileBatch")(CompileBatch.Policy()).map(new CompileBatch(_))
  override def afterComplete(): Unit = {
    require(policy.report.nonEmpty && policy.commands.nonEmpty)
    val report = Paths.get(policy.report); require(!Files.exists(report),"Never overwrite a build observation")
    Files.createDirectories(report)
    def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    def sources(): Map[String,String] = {
      val stream = Files.walk(Paths.get("modules"))
      try stream.iterator.asScala.filter(p => Files.isRegularFile(p) && p.toString.endsWith(".scala") &&
        (p.toString.contains("/src/main/") || p.toString.contains("/src/test/"))).map(p => p.toString -> hash(Files.readString(p))).toMap finally stream.close()
    }
    val before = sources()
    Files.write(report.resolve("sources.tsv"),("path\tsha256" :: before.toList.sorted.map { case(p,h) => p + "\t" + h }).asJava)
    Files.writeString(report.resolve("status.txt"),"RUNNING\n")
    val args = List("sbt","--server","-batch","set every scalafmtOnCompile := false","set ThisBuild / scalacOptions += \"-Xsemanticdb\"") ++ policy.commands
    Files.write(report.resolve("arguments.txt"),args.asJava)
    println("Running actual build: " + policy.commands.mkString(", "))
    val code = new ProcessBuilder(args.asJava).redirectErrorStream(true).redirectOutput(report.resolve("build.log").toFile).start().waitFor()
    require(sources() == before,"Source universe changed during compilation")
    Files.writeString(report.resolve("status.txt"),(if(code == 0) "PASS" else "COMPILE_FAILED") + "\nexit=" + code + "\nsource-files=" + before.size + "\nAll source hashes stable during compilation. Later commands may not execute after the first failed command.\n")
    println(Files.readString(report.resolve("status.txt")))
    require(code == 0, "Compilation failed; subsequent migration stages must not run")
  }
}
