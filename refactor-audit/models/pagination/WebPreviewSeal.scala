import scalafix.v1._
import scala.meta._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class WebPreviewSeal extends SyntacticRule("WebPreviewSeal") {
  override def afterComplete(): Unit = {
    implicit val dialect: Dialect = dialects.Scala3
    val base = Paths.get("refactor-audit/models/pagination")
    val baseline = Files.readAllLines(base.resolve("build-v1/sources.tsv")).asScala.drop(1).map(_.split("\t",-1)).map(r => r(0) -> r(1)).toMap
    def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    require(Files.readString(base.resolve("forwarding-fixture.txt")).startsWith("PASS"))
    (1 to 2).foreach { version =>
      val preview = Paths.get("target/pagination-web-preview-v" + version)
      val report = base.resolve("web-preview-v" + version)
      require(!Files.exists(report),"Retain prior observation")
      val walk = Files.walk(preview)
      val files = try walk.iterator.asScala.filter(p => Files.isRegularFile(p) && p.toString.endsWith(".scala")).toList finally walk.close()
      require(files.size == 6,"Unexpected web preview scope")
      Files.createDirectories(report)
      val plan = files.map { p =>
        val relative = preview.relativize(p).toString
        require(hash(Files.readString(Paths.get(relative))) == baseline(relative),"Source changed since original build")
        val text = Files.readString(p); text.parse[Source].get
        val dest = report.resolve("sources").resolve(relative); Files.createDirectories(dest.getParent); Files.writeString(dest,text)
        List(relative,baseline(relative),hash(text)).mkString("\t")
      }.sorted
      val state = Files.readString(preview.resolve("modules/web/src/main/scala/com/histmersion/web/TabState.scala")).parse[Source].get
      val appended = state.collect { case d: Defn.Def if d.name.value == "appended" => d }.head
      val total = appended.collect { case a: Term.Assign if a.lhs.syntax == "total" => a.rhs }.head
      require(total.syntax == (if(version == 1) "com.histmersion.api.RowCount(p.total).value" else "p.total"),"Unexpected forwarding shape")
      Files.writeString(report.resolve("plan.tsv"),("path\tbefore-sha256\tafter-sha256" :: plan).mkString("\n") + "\n")
      Files.writeString(report.resolve("status.txt"),(if(version == 1) "REJECTED: boundary/forwarding patch interaction; never applied.\n" else "READY_FOR_COMPILER_PROPAGATION: generic/known-value fixture passes; application compilation still required.\n") + "Six files; original source hashes unchanged.\n")
    }
    Files.copy(Paths.get("tools/refactor/ModelTypeRewrite.scala"),base.resolve("web-preview-v2/ModelTypeRewrite.scala"))
    println("Preserved rejected web preview and corrected six-file propagation plan")
  }
}
