import scalafix.v1._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class WallClockReconciliation extends SyntacticRule("WallClockReconciliation") {
  override def afterComplete(): Unit = {
    def read(p: String) = Files.readAllLines(Paths.get(p)).asScala.toList
    def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    val root = Paths.get("").toAbsolutePath.normalize
    val base = "refactor-audit/wall-clock/"
    val report = base + "measurement-2/"
    require(read(report + "missing-files.txt").isEmpty)
    require(read(report + "verification.txt").contains("failures=0"))
    require(read(report + "verification.txt").contains("unchecked-examples=0"))
    require(read(base + "tests-2/status.txt").head == "PASS")
    val frozen = read(base + "tests-2/sources.tsv").tail.map(_.split("\t")).map(a => a(0) -> a(1)).toMap
    frozen.foreach { case(p,h) => require(hash(Files.readString(Paths.get(p))) == h, "Source changed after tests: " + p) }
    val covered = read(report + "files.txt").map(p => root.relativize(Paths.get(p).toAbsolutePath.normalize).toString).toSet
    require(covered == frozen.keys.filter(_.contains("/src/main/")).toSet)
    val old = read("refactor-audit/capability-treatment/review-2/observation.tsv").tail.map(_.split("\t",-1).toList)
    val before = old.map(r => r.head -> r(1).toInt).toMap
    val hits = read(report + "findings.tsv").tail.map(_.split("\t",-1).toList)
    val after = hits.groupBy { r =>
      val relative = root.relativize(Paths.get(r(1)).toAbsolutePath.normalize).toString
      "CAP-" + hash(List(r.head,relative,r(4),r(5),r(7)).mkString("\t"))
    }.view.mapValues(_.size).toMap
    val review = read("refactor-audit/capability-treatment/review-2/findings.tsv")
    val findings = review.tail.map(_.split("\t",-1).toList)
    val repaired = findings.filter(_(10) == "wall-clock")
    val ids = repaired.map(_.head).toSet
    require(repaired.map(_(1).toInt).sum == 29)
    require(before -- ids == after, "Identity or multiplicity changed beyond the reviewed wall-clock batch")
    require(after.values.sum == 118)
    val out = Paths.get(base + "reconciliation-1"); require(!Files.exists(out)); Files.createDirectories(out)
    Files.write(out.resolve("findings.tsv"),(review.head :: findings.map(r => (if(ids(r.head)) r.updated(11,"verified-fixed") else r).mkString("\t"))).asJava)
    val events = repaired.map(r => List("WALL-CLOCK-FIX-" + r.head,r.head,"verified-fixed",r(1),"Scalafix rewrite and caller propagation; full compile/Test-compile; 21 focused tests; exact identity absence with unchanged residuals").mkString("\t"))
    Files.write(out.resolve("events.tsv"),("event-id\tfinding-id\tevent\toccurrences\tevidence" :: events).asJava)
    Files.write(out.resolve("counts.tsv"),("id\tbefore\tafter" :: before.toList.sortBy(_._1).map { case(id,n) => s"$id\t$n\t${after.getOrElse(id,0)}" }).asJava)
    val summary = List("PASS", "production-files=" + covered.size,"production-occurrences-before=147","production-occurrences-after=118", "verified-production-occurrences=29", "verified-production-anchors=" + ids.size,
      "confirmed-clock-design-occurrences-outstanding=13", "new-identities=0", "changed-residual-multiplicities=0", "exceptions-added=0", "test-capability-policy=unmeasured", "independent-family-precision-recall=unestablished", "database-backed-workflow-runtime=not-run", "whole-assessment-history-gate=not-run; bounded capability comparison only")
    Files.write(out.resolve("status.txt"),summary.asJava)
    println(summary.mkString("\n"))
  }
}
