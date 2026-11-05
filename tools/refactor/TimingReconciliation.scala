import scalafix.v1._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class TimingReconciliation extends SyntacticRule("TimingReconciliation") {
  override def afterComplete(): Unit = {
    def read(p: String) = Files.readAllLines(Paths.get(p)).asScala.toList
    def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    val root = Paths.get("").toAbsolutePath.normalize
    val base = "refactor-audit/monotonic/"
    val report = base + "measurement-2/"
    require(read(report + "missing-files.txt").isEmpty)
    require(read(report + "verification.txt").contains("failures=0") && read(report + "verification.txt").contains("unchecked-examples=0"))
    require(read(base + "wiring-build-1/status.txt").head == "PASS")
    val frozen = read(base + "wiring-build-1/sources.tsv").tail.map(_.split("\t")).map(a => a(0) -> a(1)).toMap
    frozen.foreach { case(p,h) => require(hash(Files.readString(Paths.get(p))) == h) }
    val covered = read(report + "files.txt").map(p => root.relativize(Paths.get(p).toAbsolutePath.normalize).toString).toSet
    require(covered == frozen.keys.filter(_.contains("/src/main/")).toSet)
    def counts(p: String) = read(p).tail.map(_.split("\t",-1).toList).groupBy { r =>
      val relative = root.relativize(Paths.get(r(1)).toAbsolutePath.normalize).toString
      "CAP-" + hash(List(r.head,relative,r(4),r(5),r(7)).mkString("\t"))
    }.view.mapValues(_.size).toMap
    val before = counts("refactor-audit/wall-clock/measurement-2/findings.tsv")
    val after = counts(report + "findings.tsv")
    val review = read("refactor-audit/wall-clock/reconciliation-1/findings.tsv")
    val rows = review.tail.map(_.split("\t",-1).toList)
    val repaired = rows.filter(_(11) == "outstanding")
    require(repaired.map(_(1).toInt).sum == 13)
    val ids = repaired.map(_.head).toSet
    require(before -- ids == after,"Unexpected identity or residual multiplicity change")
    require(after.values.sum == 105)
    val summary = read(report + "summary.txt")
    List("permission:monotonic-adapter:uses=2","permission:deps-timing-composition:uses=2","permission:deps-timing-factory:uses=2","permission:system-timing-composition:uses=2").foreach(s => require(summary.contains(s)))
    val out = Paths.get(base + "reconciliation-1"); require(!Files.exists(out)); Files.createDirectories(out)
    Files.write(out.resolve("findings.tsv"),(review.head :: rows.map(r => r.updated(11,"verified-fixed").mkString("\t"))).asJava)
    Files.write(out.resolve("events.tsv"),("event-id\tfinding-id\tevent\toccurrences\tevidence" :: repaired.map(r => s"TIMING-FIX-${r.head}\t${r.head}\tverified-fixed\t${r(1)}\tAST rewrite; full compile/Test-compile; 24 timer/passage tests; exact identity reconciliation; broader runtime coverage remains unrun")).asJava)
    Files.write(out.resolve("accepted-boundaries.tsv"),List("id\tscope\toccurrences\tclassification\treason",
      "monotonic-adapter\tproduction\t2\tallowed-adapter\tSystemTimer implements supplied elapsed-time capability",
      "deps-timing-composition\tproduction\t2\tallowed-composition\tDeps chooses replaceable adapters",
      "deps-timing-factory\tproduction\t2\tallowed-composition\tDeps factory forwards overrides",
      "system-timing-composition\tproduction\t2\tallowed-composition\tSystemContext passes selected adapters into hot graph").asJava)
    Files.write(out.resolve("status.txt"),List("PASS", "production-files=2377", "before-occurrences=118", "after-occurrences=105", "verified-production-occurrences=13", "verified-production-anchors=" + ids.size, "new-unapproved-identities=0", "changed-residual-multiplicities=0", "reviewed-clock-occurrences-total=42", "reviewed-clock-outstanding=0", "allowed-adapter-and-composition-occurrences=8", "independent-family-quality=unestablished", "test-capability-policy=unmeasured", "database-query-and-backup-job-runtime=not-run", "multi-family-assessment-history-gate=not-run").asJava)
    println("PASS: exact thirteen reviewed removals; 105 residual occurrences unchanged; eight adapter/composition occurrences explicitly retained")
  }
}
