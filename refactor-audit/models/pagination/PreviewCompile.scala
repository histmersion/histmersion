import scalafix.v1._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class PreviewCompile extends SyntacticRule("PreviewCompile") {
  override def afterComplete(): Unit = {
    val target = Paths.get("target/pagination-api-compile-v3")
    val preview = Paths.get("target/pagination-preview-v3")
    val report = Paths.get("refactor-audit/models/pagination/preview-v3")
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
    List("Offset","RowCount").foreach { name =>
      val relative = Paths.get("modules/api/src/main/scala/com/histmersion/api/" + name + ".scala")
      Files.copy(preview.resolve(relative),target.resolve(relative))
    }
    val classpathLog = Paths.get("target/approval-extraction-verification-v3/classpath.log")
    val exported = Files.readAllLines(classpathLog).asScala.filter(s => s.startsWith("List(") && s.endsWith(")") && s.contains("magnum_3")).toList
    require(exported.size == 1)
    val entries = exported.head.stripPrefix("List(").stripSuffix(")").split(", ").toList.map(_.takeWhile(_ != '>')
      .replace("${OUT}",Paths.get("target/out").toAbsolutePath.toString)
      .replace("${CSR_CACHE}",Paths.get(System.getProperty("user.home"),"Library/Caches/Coursier/v1").toString))
    require(entries.forall(p => Files.isRegularFile(Paths.get(p))))
    println("Compiling full API production overlay: " + (originals.size + 2) + " sources")
    val args = List("scala-cli","compile",target.toString,"--server=false","-S","3.10.0","--scalac-option","-source:future","--classpath",entries.mkString(java.io.File.pathSeparator))
    val code = new ProcessBuilder(args.asJava).redirectErrorStream(true).redirectOutput(report.resolve("api-compile.log").toFile).start().waitFor()
    sourceRows.foreach(r => require(hash(Files.readString(Paths.get(r.head))) == r(1),"Application changed during preview compilation"))
    Files.writeString(report.resolve("api-compile-status.txt"),(if(code == 0) "PASS" else "COMPILE_FAILED") + "\nexit=" + code + "\nAPI production overlay sources=" + (originals.size + 2) + "\nDependency classpath from evaluated server build; not a substitute for actual API JVM/JS and dependent-module builds.\nAll original source hashes unchanged.\n")
    println(Files.readString(report.resolve("api-compile-status.txt")))
  }
}
