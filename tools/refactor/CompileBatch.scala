import scalafix.v1._
import metaconfig.{ConfDecoder, Configured, generic}
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

object CompileBatch {
  case class Policy(report: String = "", commands: List[String] = List("compile", "Test/compile"), sourceOverlay: Option[String] = None)
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
        (p.toString.contains("/src/main/") || p.toString.contains("/src/test/"))).map(p => p.toString -> hash(Files.readString(p))).toMap +
        ("build.sbt" -> hash(Files.readString(Paths.get("build.sbt")))) finally stream.close()
    }
    val before = sources()
    val root = Paths.get("").toAbsolutePath.normalize
    def overlaySources(): Map[String,String] = policy.sourceOverlay.map { directory =>
      val overlay = root.resolve(directory).normalize
      require(overlay.startsWith(root) && overlay != root && Files.isDirectory(overlay),"Overlay must be an existing workspace directory")
      val stream = Files.walk(overlay)
      try stream.iterator.asScala.filter(p => Files.isRegularFile(p) && p.toString.endsWith(".scala")).map(p =>
        root.relativize(p).toString -> hash(Files.readString(p))).toMap finally stream.close()
    }.getOrElse(Map.empty)
    val overlayBefore = overlaySources()
    val overlaySettings = policy.sourceOverlay.toList.flatMap { directory =>
      require(overlayBefore.nonEmpty,"Empty source overlay")
      def quoted(s: String) = scala.meta.Lit.String(s).syntax
      val variables = "val root = file(" + quoted(root.toString) + ").toPath; val overlay = file(" + quoted(root.resolve(directory).normalize.toString) + ").toPath; "
      val projects = Files.readAllLines(Paths.get("refactor-audit/build/projects.tsv")).asScala.tail.map(_.split("\t",-1).head).toList.distinct
      require(projects.nonEmpty)
      val settings = projects.flatMap { project => List("Compile","Test").flatMap { scope =>
        val prefix = "LocalProject(" + quoted(project) + ") / " + scope
        List(
          prefix + " / unmanagedSourceDirectories ~= { directories => " + variables + "directories ++ directories.filter(d => d.toPath.toAbsolutePath.normalize.startsWith(root)).map(d => overlay.resolve(root.relativize(d.toPath.toAbsolutePath.normalize)).toFile) }",
          prefix + " / unmanagedSources ~= { sources => " + variables + "sources.map { f => val p = f.toPath.toAbsolutePath.normalize; if (p.startsWith(root)) { val replacement = overlay.resolve(root.relativize(p)).toFile; if (replacement.isFile) replacement else f } else f }.distinct }"
        )
      }}
      List(settings.mkString("set Seq(",",",")"))
    }
    if(policy.sourceOverlay.nonEmpty) Files.write(report.resolve("overlay-sources.tsv"),("path\tsha256" :: overlayBefore.toList.sorted.map { case(p,h) => p + "\t" + h }).asJava)
    Files.write(report.resolve("sources.tsv"),("path\tsha256" :: before.toList.sorted.map { case(p,h) => p + "\t" + h }).asJava)
    Files.writeString(report.resolve("status.txt"),"RUNNING\n")
    val args = List("sbt","--server","-batch","set every scalafmtOnCompile := false","set ThisBuild / scalacOptions += \"-Xsemanticdb\"") ++ overlaySettings ++ policy.commands
    Files.write(report.resolve("arguments.txt"),args.asJava)
    println("Running actual build: " + policy.commands.mkString(", "))
    val code = new ProcessBuilder(args.asJava).redirectErrorStream(true).redirectOutput(report.resolve("build.log").toFile).start().waitFor()
    require(sources() == before,"Source universe changed during compilation")
    require(overlaySources() == overlayBefore,"Overlay changed during compilation")
    Files.writeString(report.resolve("status.txt"),(if(code == 0) "PASS" else "COMPILE_FAILED") + "\nexit=" + code + "\nsource-files=" + before.size + "\nAll source hashes stable during compilation. Later commands may not execute after the first failed command.\n")
    println(Files.readString(report.resolve("status.txt")))
    require(code == 0, "Compilation failed; subsequent migration stages must not run")
  }
}
