import scalafix.v1._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class SealPreview extends SyntacticRule("SealPreview") {
  override def afterComplete(): Unit = {
    val report = Paths.get("refactor-audit/models/pagination/preview-v1")
    val preview = Paths.get("target/pagination-preview-v1")
    def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    val rows = Files.readAllLines(report.resolve("plan.tsv")).asScala.drop(1).map(_.split("\t",-1).toList).toList
    require(rows.size == 18)
    rows.foreach { r =>
      val text = Files.readString(preview.resolve(r.head)); require(hash(text) == r(2))
      require(if(r(1) == "new") !Files.exists(Paths.get(r.head)) else hash(Files.readString(Paths.get(r.head))) == r(1))
      val dest = report.resolve("sources").resolve(r.head); Files.createDirectories(dest.getParent)
      if(Files.exists(dest)) require(Files.readString(dest) == text) else Files.writeString(dest,text)
    }
    val rule = Files.readString(Paths.get("tools/refactor/ModelTypeRewrite.scala"))
    val archivedRule = report.resolve("ModelTypeRewrite.scala")
    if(Files.exists(archivedRule)) require(Files.readString(archivedRule) == rule) else Files.writeString(archivedRule,rule)
    val clean = Files.readString(report.resolve("api-compile.log")).replaceAll("\u001b\\[[0-9;]*m", "")
    val errors = clean.linesIterator.filter(_.startsWith("-- [E")).toList
    require(errors.size == 11 && clean.contains("11 errors found"),"Unexpected compiler outcome; inspect before sealing")
    Files.write(report.resolve("compiler-errors.txt"),errors.asJava)
    Files.writeString(report.resolve("sealed.txt"),"18 preview files match frozen hashes; production sources unchanged.\n479-source API overlay failed with 11 retained compiler errors. No applied fixes or full-module compile claim.\nrule-sha256=" + hash(rule) + "\n")
    println(Files.readString(report.resolve("sealed.txt")))
  }
}
