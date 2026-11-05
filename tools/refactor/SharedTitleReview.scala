import scalafix.v1._
import java.nio.file.{Files,Paths}
import scala.jdk.CollectionConverters._

class SharedTitleReview extends SyntacticRule("SharedTitleReview") {
  override def afterComplete(): Unit = {
    def rows(p: String) = Files.readAllLines(Paths.get(p)).asScala.drop(1).map(_.split("\t",-1).toList).toList
    def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    val observation = "refactor-audit/isolation-history/observations/2026-11-05T03-56-36.979728Z/findings.tsv"
    val all = rows(observation)
    val targets = Set("com/histmersion/domain/library/Title$package.Title#","com/histmersion/domain/library/Title$package.Title.")
    val selected = all.filter(r => targets(r(5)))
    require(selected.nonEmpty && selected.exists(_(1) == "production"))
    val sources = rows("refactor-audit/shared-title/preview-1/sources.tsv").map(r => r.head -> r(1)).toMap
    selected.map(_(3)).distinct.foreach(p => require(hash(Files.readString(Paths.get(p))) == sources(p)))
    val out = Paths.get("refactor-audit/shared-title/review-1")
    require(!Files.exists(out)); Files.createDirectories(out)
    Files.writeString(out.resolve("observation-sha256.txt"),hash(Files.readString(Paths.get(observation))) + "\n")
    Files.copy(Paths.get(observation),out.resolve("observation.tsv"))
    val findings = selected.map { r =>
      val production = r(1) == "production"
      List(r.head,r(1),if(production) "confirmed" else "candidate",if(production) "bulk-rewrite" else "caller-propagation",r(3),r(4),r(5),r(6),
        if(production) "Shared record title is owned by library; move existing opaque type to shared kernel without changing representation" else "Test reference follows relocated type; no independent architectural violation or repair credit inferred")
    }
    Files.write(out.resolve("findings.tsv"),("id\tscope\tclassification\ttreatment\tpath\towner\ttarget\tidentity-confidence\treason" :: findings.map(_.mkString("\t"))).asJava)
    Files.write(out.resolve("sources.tsv"),("path\tsha256" :: selected.map(_(3)).distinct.sorted.map(p => p + "\t" + sources(p))).asJava)
    Files.write(out.resolve("status.txt"),List("REVIEWED_NOT_FIXED","production-confirmed-relationships=" + findings.count(_(1)=="production"),
      "test-candidate-relationships=" + findings.count(_(1)=="test"),"other-relationships-retained=" + (all.size-selected.size),
      "One ownership move; relationship count is not an independent conceptual defect count. Ambiguous identities remain ambiguous. No independent detector quality claim.").asJava)
    println(Files.readString(out.resolve("status.txt")))
  }
}
