import scalafix.v1._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class IsolationReview extends SyntacticRule("IsolationReview") {
  override def afterComplete(): Unit = {
    val dir = Paths.get("refactor-audit/isolation-history")
    def rows(path: java.nio.file.Path) = Files.readAllLines(path).asScala.drop(1).map(_.split("\t",-1).toList).toList
    def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    val decisions = rows(dir.resolve("review-decisions.tsv"))
    require(decisions.forall(_.size == 4) && decisions.map(_.head).distinct.size == decisions.size)
    val baseline = rows(dir.resolve("baseline.tsv"))
    require(decisions.forall(d => baseline.exists(_(3) == d.head)))
    val reviewed = baseline.flatMap { r => decisions.find(_.head == r(3)).map { d =>
      require(r(5).startsWith("com/histmersion/domain/"), "Review scope changed")
      List(r.head,r(1),r(3),r(4),r(5),d(1),d(2),hash(Files.readString(Paths.get(r(3)))),d(3)).mkString("\t")
    }}
    val destination = dir.resolve("review-1.tsv")
    require(!Files.exists(destination), "Never overwrite adjudication history")
    Files.write(destination,("id\tscope\tpath\towner\ttarget\tclassification\ttreatment\tsource-sha256\tevidence" :: reviewed).asJava)
    val counts = reviewed.map(_.split("\t",-1)).groupBy(r => (r(1),r(5))).toList.sortBy(_._1).map { case((scope,status),rs) => s"$scope\t$status\t${rs.size}" }
    Files.write(dir.resolve("review-1-counts.tsv"),("scope\tclassification\trelationships" :: counts).asJava)
    println(counts.mkString("\n"))
  }
}
