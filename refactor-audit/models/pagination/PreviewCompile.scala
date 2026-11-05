import scalafix.v1._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._
import metaconfig.{ConfDecoder, Configured, generic}

object PreviewCompile {
  case class Policy(target: String = "target/pagination-api-compile-v3", preview: String = "target/pagination-preview-v3",
    report: String = "refactor-audit/models/pagination/preview-v3", evaluatedClasspath: Boolean = false)
  object Policy {
    implicit val surface: generic.Surface[Policy] = generic.deriveSurface[Policy]
    implicit val decoder: ConfDecoder[Policy] = generic.deriveDecoder(Policy()).noTypos
  }
}
class PreviewCompile(val policy: PreviewCompile.Policy) extends SyntacticRule("PreviewCompile") {
  def this() = this(PreviewCompile.Policy())
  override def withConfiguration(c: Configuration): Configured[Rule] = c.conf.getOrElse("PreviewCompile")(PreviewCompile.Policy()).map(new PreviewCompile(_))
  override def afterComplete(): Unit = {
    val target = Paths.get(policy.target)
    val preview = Paths.get(policy.preview)
    val report = Paths.get(policy.report)
    require(!Files.exists(target),"Retain previous compile overlay")
    val sourceRows = Files.readAllLines(Paths.get("refactor-audit/models/sources.tsv")).asScala.drop(1).map(_.split("\t",-1).toList).toList
    def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    sourceRows.foreach(r => require(hash(Files.readString(Paths.get(r.head))) == r(1)))
    val root = Paths.get("").toAbsolutePath.normalize
    val originals = sourceRows.filter(r => r.head.contains("/modules/api/src/main/scala/") || r.head.contains("/modules/api/src/main/scalajvm/"))
    originals.foreach { r =>
      val relative = root.relativize(Paths.get(r.head)); val rewritten = preview.resolve(relative)
      val out = target.resolve(relative); Files.createDirectories(out.getParent)
      Files.copy(if(Files.exists(rewritten)) rewritten else Paths.get(r.head),out)
    }
    val stream = Files.walk(preview.resolve("modules/api/src/main"))
    val additions = try stream.iterator.asScala.filter(p => Files.isRegularFile(p) && p.toString.endsWith(".scala") &&
      !Files.exists(root.resolve(preview.relativize(p)))).toList finally stream.close()
    additions.foreach { p => val out = target.resolve(preview.relativize(p)); Files.createDirectories(out.getParent); Files.copy(p,out) }
    val classpathLog = Paths.get("target/approval-extraction-verification-v3/classpath.log")
    val rawEntries = if(policy.evaluatedClasspath) {
      val rows = Files.readAllLines(Paths.get("refactor-audit/build/classpath.tsv")).asScala.tail.map(_.split("\t",-1))
      val selected = rows.filter(r => r(0) == "api" && r(1) == "Compile" && r(2) != "api").map(_(3)).toList
      require(selected.nonEmpty,"Missing evaluated API dependency classpath")
      selected
    } else {
      val exported = Files.readAllLines(classpathLog).asScala.filter(s => s.startsWith("List(") && s.endsWith(")") && s.contains("magnum_3")).toList
      require(exported.size == 1)
      exported.head.stripPrefix("List(").stripSuffix(")").split(", ").toList
    }
    val entries = rawEntries.map(_.takeWhile(_ != '>')
      .replace("${OUT}",Paths.get("target/out").toAbsolutePath.toString)
      .replace("${CSR_CACHE}",Paths.get(System.getProperty("user.home"),"Library/Caches/Coursier/v1").toString))
    require(entries.forall(p => Files.isRegularFile(Paths.get(p))))
    println("Compiling full API production overlay: " + (originals.size + additions.size) + " sources")
    val args = List("scala-cli","compile",target.toString,"--server=false","-S","3.10.0","--scalac-option","-source:future","--classpath",entries.mkString(java.io.File.pathSeparator))
    val code = new ProcessBuilder(args.asJava).redirectErrorStream(true).redirectOutput(report.resolve("api-compile.log").toFile).start().waitFor()
    sourceRows.foreach(r => require(hash(Files.readString(Paths.get(r.head))) == r(1),"Application changed during preview compilation"))
    Files.writeString(report.resolve("api-compile-status.txt"),(if(code == 0) "PASS" else "COMPILE_FAILED") + "\nexit=" + code + "\nAPI production overlay sources=" + (originals.size + additions.size) + "\nDependency classpath from evaluated " + (if(policy.evaluatedClasspath) "API" else "server") + " build; not a substitute for actual API JVM/JS and dependent-module builds.\nAll original source hashes unchanged.\n")
    println(Files.readString(report.resolve("api-compile-status.txt")))
  }
}
