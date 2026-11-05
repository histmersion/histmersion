import scalafix.v1._
import java.nio.file.{Files,Paths}
import scala.jdk.CollectionConverters._

class SharedTitleReconciliation extends SyntacticRule("SharedTitleReconciliation") {
  override def afterComplete(): Unit = {
    def lines(p: String) = Files.readAllLines(Paths.get(p)).asScala.toList
    def rows(p: String) = lines(p).drop(1).map(_.split("\t",-1).toList)
    def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    val root = Paths.get("").toAbsolutePath.normalize
    def relative(s: String) = root.relativize(Paths.get(s).toAbsolutePath.normalize).toString
    val out = Paths.get("refactor-audit/shared-title/reconciliation-1")
    require(!Files.exists(out))
    require(lines("refactor-audit/shared-title/build-1/status.txt").head == "PASS")
    val built = rows("refactor-audit/shared-title/build-1/sources.tsv").map(r => r.head -> r(1)).toMap
    built.foreach { case(p,h) => require(hash(Files.readString(Paths.get(p))) == h, "Stale build: " + p) }
    List("architecture","models").foreach { family =>
      require(rows(s"refactor-audit/$family/sources.tsv").map(r => relative(r.head) -> r(1)).toMap == built, "Measurement source universe differs: " + family)
    }
    val observations = Files.list(Paths.get("refactor-audit/isolation-history/observations"))
    val latest = try observations.iterator.asScala.filter(Files.isDirectory(_)).toList.sortBy(_.toString).last finally observations.close()
    require(lines(latest.resolve("status.txt").toString).head == "PASS")
    require(lines(latest.resolve("inputs.txt").toString).contains("sources=" + hash(lines("refactor-audit/architecture/sources.tsv").mkString("\n"))))
    require(lines(latest.resolve("inputs.txt").toString).contains("crossings=" + hash(Files.readString(Paths.get("refactor-audit/architecture/dependency-crossings.tsv")))))
    val before = rows("refactor-audit/shared-title/review-1/observation.tsv").map(r => r.head -> r).toMap
    val reviewed = rows("refactor-audit/shared-title/review-1/findings.tsv")
    val selected = reviewed.map(_.head).toSet
    val after = rows(latest.resolve("findings.tsv").toString).map(r => r.head -> r).toMap
    require(before.keySet -- after.keySet == selected, "Unexpected removal; absence is not repair proof")
    require(after.keySet -- before.keySet == Set.empty[String], "New isolation identities")
    require(after.forall { case(id,r) => r.take(9) == before(id).take(9) }, "Residual relationship changed")
    val crossing = rows("refactor-audit/architecture/dependency-crossings.tsv")
    val replacementEvidence = reviewed.flatMap { r =>
      val original = before(r.head)
      val target = r(6).replace("/domain/library/Title$package", "/domain/Title$package")
      val found = crossing.filter(c => relative(c.head) == r(4) && c(6) == target && c(5) == r(5))
      require(found.nonEmpty,"Missing replacement relationship for " + r.head)
      require(found.forall(_(10) == "unclassified-direction"), "Unexpected kernel classification")
      found.map(c => r.head + "\t" + c.mkString("\t"))
    }
    val constraints = rows("refactor-audit/models/newtype-constraints.tsv")
    val title = constraints.filter(_.head == "com/histmersion/domain/Title$package.Title.")
    require(title.size == 1 && title.head(5) == "String" && title.head(6) == "Pure" && title.head(7) == "pure")
    require(!constraints.exists(_.head == "com/histmersion/domain/library/Title$package.Title."))
    val plan = rows("refactor-audit/shared-title/preview-1/plan.tsv")
    val moves = rows("refactor-audit/shared-title/preview-1/moves.tsv").map(r => r.head -> r(1)).toMap
    plan.foreach(r => require(built(moves.getOrElse(r.head,r.head)) == r(2),"Applied patch differs from preview"))
    val testLog = lines("refactor-audit/shared-title/build-1/build.log")
    require(testLog.exists(_.contains("NewtypeCodecsSuite finished: 0 failed, 0 ignored, 5 total")))
    require(testLog.exists(_.contains("PersonalTypesSuite finished: 0 failed, 0 ignored, 9 total")))
    Files.createDirectories(out)
    def write(name: String, data: List[String]) = Files.write(out.resolve(name),data.asJava)
    write("findings.tsv", "id\tscope\tclassification\ttreatment\tstatus\tidentity-confidence" :: reviewed.map(r =>
      List(r.head,r(1),r(2),r(3),if(r(1)=="production") "verified-fixed" else "caller-updated-no-repair-credit",r(7)).mkString("\t")))
    write("events.tsv", "id\tevent\tevidence" :: reviewed.map(r => r.head + "\t" + (if(r(1)=="production") "verified-fixed" else "caller-updated") + "\t" + latest))
    write("replacement-evidence.tsv","original-id\tpath\tline\tscope\tfrom-package\tto-package\towner\ttarget\tevidence-kind\tfrom-region\tto-region\tstatus\tmodule" :: replacementEvidence)
    write("newtype.tsv",lines("refactor-audit/models/newtype-constraints.tsv").head :: title.map(_.mkString("\t")))
    write("status.txt",List("PASS", "production-relationships-fixed=" + reviewed.count(_(1)=="production"),
      "test-relationships-updated-no-repair-credit=" + reviewed.count(_(1)=="test"),"other-identities-preserved=" + after.size,
      "new=0","reopened=0","source-files=" + built.size,"isolation-observation=" + latest,
      "One shared-type ownership defect repaired. Kernel direction is not classified by the generic region detector; explicit ownership review and unchanged String/Pure evidence establish this repair. Other unclassified directions remain unresolved.",
      "Fourteen tests passed without skips. Database runtime and independent family precision/recall remain unestablished."))
    println(lines(out.resolve("status.txt").toString).mkString("\n"))
  }
}
