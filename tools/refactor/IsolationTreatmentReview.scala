import scalafix.v1._
import java.nio.file.{Files,Paths}
import scala.jdk.CollectionConverters._

class IsolationTreatmentReview extends SyntacticRule("IsolationTreatmentReview") {
  override def afterComplete(): Unit = {
    def rows(p: String) = Files.readAllLines(Paths.get(p)).asScala.drop(1).map(_.split("\t",-1).toList).toList
    def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    val triage = "refactor-audit/isolation-triage/review-2"
    val original = rows(triage + "/observation.tsv")
    val groups = rows(triage + "/groups.tsv")
    val decisions = rows("tools/refactor/isolation-treatment-2.tsv")
    require(decisions.forall(_.size==5) && decisions.map(_.head).distinct.size==decisions.size)
    require(decisions.map(_.head).toSet == original.filter(_(1)=="production").map(_(3)).toSet,"Production denominator changed")
    groups.foreach(r => require(hash(Files.readString(Paths.get(r(1)))) == r(4),"Source changed since review: " + r(1)))
    val inherited = rows("refactor-audit/isolation-history/review-1.tsv").filter(r => r(1)=="test" && r(5)=="non-violation").map(_.head).toSet ++
      rows("refactor-audit/isolation-history/adoptions/approval-tests/decisions.tsv").filter(_(3)=="non-violation").map(_.head)
    val findings = original.map { r =>
      val d = decisions.find(_.head==r(3))
      val classification = d.map(_(1)).getOrElse(if(inherited(r.head)) "non-violation" else "candidate")
      d.foreach { decision =>
        if(decision(1)=="accepted-exception") {
          val expected = if(decision(3)=="source-referrers") "com/histmersion/domain/library/sources/referrers/" else "com/histmersion/domain/personal/marks/"
          require(r(5).startsWith(expected),"Unreviewed exception target")
        }
      }
      List(r.head,r(1),classification,d.map(_(2)).getOrElse(if(inherited(r.head)) "no-rewrite" else "unassigned"),
        d.map(_(3)).getOrElse("prior-test-evidence"),r(3),r(4),r(5),r(6),hash(Files.readString(Paths.get(r(3)))),
        d.map(_(4)).getOrElse(if(inherited(r.head)) "Retained exact previously adjudicated test identity; no broadened file-level waiver" else "Unreviewed test relationship"))
    }
    require(findings.map(_.head).toSet==original.map(_.head).toSet)
    val out = Paths.get(triage,"treatment"); require(!Files.exists(out)); Files.createDirectories(out)
    Files.copy(Paths.get("tools/refactor/isolation-treatment-2.tsv"),out.resolve("decisions.tsv"))
    Files.write(out.resolve("findings.tsv"),("id\tscope\tclassification\ttreatment\tbatch\tpath\towner\ttarget\tidentity-confidence\tsource-sha256\tevidence" :: findings.map(_.mkString("\t"))).asJava)
    Files.write(out.resolve("events.tsv"),("id\tevent\tevidence" :: findings.map(r => r.head + "\t" + r(2) + "\t" + r(4))).asJava)
    val counts = findings.groupBy(r => (r(1),r(2),r(3))).toList.sortBy(_._1).map { case((scope,kind,treatment),rs) => List(scope,kind,treatment,rs.size.toString).mkString("\t") }
    Files.write(out.resolve("counts.tsv"),("scope\tclassification\ttreatment\trelationships" :: counts).asJava)
    Files.write(out.resolve("policy-evidence.txt"),List("AGENTS.md=" + hash(Files.readString(Paths.get("AGENTS.md"))),
      "docs/research-domain-model.md=" + hash(Files.readString(Paths.get("docs/research-domain-model.md"))),
      "source-referrers=AGENTS domain.library; approved design section 23 explicitly lists all eight table owners",
      "personal-readable=AGENTS domain.research; lower-owned interfaces; Readable SPI source contract",
      "No detector suppression or baseline change. Accepted decisions bind only these frozen identities; new relationships still fail the identity gate.").asJava)
    Files.writeString(out.resolve("status.txt"),"REVIEWED_NOT_REPAIRED\n346 original relationships retained; production adjudicated; earlier test decisions retained by exact ID.\nNo independent detector sample or precision/recall claim; no refinement round spent.\n")
    println(counts.mkString("\n"))
  }
}
