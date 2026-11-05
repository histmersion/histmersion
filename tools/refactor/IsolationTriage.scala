import scalafix.v1._
import scala.meta._
import java.nio.file.{Files,Paths}
import scala.jdk.CollectionConverters._

class IsolationTriage extends SyntacticRule("IsolationTriage") {
  override def afterComplete(): Unit = {
    def rows(p: String) = Files.readAllLines(Paths.get(p)).asScala.drop(1).map(_.split("\t",-1).toList).toList
    def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    val observation = "refactor-audit/isolation-history/observations/2026-11-05T04-09-21.771592Z"
    val findings = rows(observation + "/findings.tsv")
    require(findings.size == 346 && findings.map(_.head).distinct.size == findings.size)
    val built = rows("refactor-audit/shared-title/build-1/sources.tsv").map(r => r.head -> r(1)).toMap
    val out = Paths.get("refactor-audit/isolation-triage/review-2")
    require(!Files.exists(out)); Files.createDirectories(out)
    val groups = findings.groupBy(r => (r(1),r(3))).toList.sortBy(_._1)
    val summaries = groups.map { case((scope,path),items) =>
      val source = Files.readString(Paths.get(path)); require(hash(source) == built(path),"Stale source: " + path)
      List(scope,path,items.size.toString,items.count(_(6)!="symbol-relationship").toString,hash(source),items.map(_.head).sorted.mkString(","))
    }
    Files.copy(Paths.get(observation,"findings.tsv"),out.resolve("observation.tsv"))
    Files.write(out.resolve("groups.tsv"),("scope\tpath\trelationships\tambiguous-identities\tsha256\tids" :: summaries.map(_.mkString("\t"))).asJava)
    Files.write(out.resolve("groups-summary.tsv"),("scope\tpath\trelationships\tambiguous-identities" :: summaries.map(_.take(4).mkString("\t"))).asJava)
    Files.write(out.resolve("status.txt"),List("GROUPED_NOT_ADJUDICATED","observation=" + observation,"relationships=" + findings.size,
      "All original identities preserved. Counts group existing evidence, not newly confirmed violations or detector-quality samples.").asJava)
    println(summaries.map(_.take(4).mkString("\t")).mkString("\n"))
  }
}
