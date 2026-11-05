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
    require(decisions.map(_.head).distinct.size == decisions.size, "Overlapping adjudications require explicit resolution")
    require(decisions.forall(r => baseline.contains(r.head) && baseline(r.head)(1) == r(1)), "Unknown adjudication identity/scope")
    require(decisions.forall(r => hash(Files.readString(Paths.get(r(2)))) == r(7)), "Source changed since adjudication; re-review required")
    val stream = Files.list(dir.resolve("observations"))
    val latest = try stream.iterator.asScala.toList.sortBy(_.toString).last finally stream.close()
    require(Files.readString(latest.resolve("status.txt")).startsWith("PASS\n"))
    val current = rows(latest.resolve("findings.tsv"))
    val byId = decisions.map(r => r.head -> r(5)).toMap
    val summary = List("production","test").flatMap(scope => List("confirmed","candidate","accepted-exception","non-violation").map { classification =>
      List(scope,classification,current.count(r => r(1) == scope && byId.getOrElse(r.head,"candidate") == classification).toString).mkString("\t")
    })
    val missing = decisions.filterNot(r => current.exists(_.head == r.head))
    require(missing.isEmpty,"Adjudicated relationship absent; repair verification or explicit correction required")
    Files.write(dir.resolve("treatment-status.tsv"),("scope\tclassification\toutstanding-relationships" :: summary).asJava)
    Files.write(dir.resolve("treatment-status.txt"),List("PASS: review identities, scope, source hashes and current presence reconciled",
      "observation=" + latest.toString,
      "Confirmed relationships describe one currently known source-proposal ownership design problem; no fixes claimed.",
      "Classification does not suppress detection or grant permission to silently defer confirmed isolation work.").asJava)
    println(summary.mkString("\n"))
  }
}
