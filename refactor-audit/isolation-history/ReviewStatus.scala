import scalafix.v1._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class ReviewStatus extends SyntacticRule("ReviewStatus") {
  override def afterComplete(): Unit = {
    val dir = Paths.get("refactor-audit/isolation-history")
    def rows(path: java.nio.file.Path) = Files.readAllLines(path).asScala.drop(1).map(_.split("\t",-1).toList).toList
    def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    val baseline = rows(dir.resolve("baseline.tsv")).map(r => r.head -> r).toMap
    val decisions = rows(dir.resolve("review-1.tsv")) ++ rows(Paths.get("refactor-audit/approval-isolation/confirmed-additions.tsv"))
    val repair = Paths.get("refactor-audit/approval-isolation")
    require(Files.readString(repair.resolve("completion.txt")).startsWith("PRODUCTION_REPAIR_VERIFIED\n"))
    val repaired = rows(repair.resolve("verified-findings.tsv")).map(_.head).toSet
    require(repaired.size == 54 && repaired.forall(id => decisions.exists(r => r.head == id && r(5) == "confirmed")))
    rows(repair.resolve("wiring/final-plan-v2.tsv")).foreach(r => require(hash(Files.readString(Paths.get(r.head))) == r(2),"Verified repair source changed"))
    require(decisions.map(_.head).distinct.size == decisions.size, "Overlapping adjudications require explicit resolution")
    require(decisions.forall(r => baseline.contains(r.head) && baseline(r.head)(1) == r(1)), "Unknown adjudication identity/scope")
    require(decisions.forall(r => repaired(r.head) || hash(Files.readString(Paths.get(r(2)))) == r(7)), "Source changed since adjudication; re-review required")
    val stream = Files.list(dir.resolve("observations"))
    val latest = try stream.iterator.asScala.toList.sortBy(_.toString).last finally stream.close()
    require(Files.readString(latest.resolve("status.txt")).startsWith("PASS\n"))
    val current = rows(latest.resolve("findings.tsv"))
    val additions = rows(dir.resolve("adoptions/approval-tests/decisions.tsv"))
    require(additions.forall(r => r(3) == "non-violation" && hash(Files.readString(Paths.get(r(1)))) == r(2)))
    val byId = decisions.map(r => r.head -> r(5)).toMap ++ additions.map(r => r.head -> r(3))
    val summary = List("production","test").flatMap(scope => List("confirmed","candidate","accepted-exception","non-violation").map { classification =>
      List(scope,classification,current.count(r => r(1) == scope && byId.getOrElse(r.head,"candidate") == classification).toString).mkString("\t")
    })
    val missing = decisions.filterNot(r => current.exists(_.head == r.head))
    require(missing.map(_.head).toSet == repaired && !current.exists(r => repaired(r.head)),"Unexplained absence or repair reappearance")
    val previous = dir.resolve("treatment-status-before-approval.tsv")
    if(!Files.exists(previous)) Files.copy(dir.resolve("treatment-status.tsv"),previous)
    Files.write(dir.resolve("treatment-status.tsv"),("scope\tclassification\toutstanding-relationships" :: summary).asJava)
    Files.write(dir.resolve("treatment-status.txt"),List("PASS: review identities, scope, source hashes and current presence reconciled",
      "observation=" + latest.toString,
      "54 confirmed production relationships verified repaired; original decisions and baseline retained. Ten new test relationships explicitly adjudicated non-violations.",
      "Classification does not suppress detection or grant permission to silently defer confirmed isolation work.").asJava)
    println(summary.mkString("\n"))
  }
}
