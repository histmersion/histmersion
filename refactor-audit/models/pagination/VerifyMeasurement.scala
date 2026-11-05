import scalafix.v1._
import metaconfig.{ConfDecoder, Configured, generic}
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

object VerifyMeasurement {
  case class Policy(report: String = "refactor-audit/models/pagination/review-history/after-pagination-1", previous: Option[String] = None)
  object Policy {
    implicit val surface: generic.Surface[Policy] = generic.deriveSurface[Policy]
    implicit val decoder: ConfDecoder[Policy] = generic.deriveDecoder(Policy()).noTypos
  }
}
class VerifyMeasurement(val policy: VerifyMeasurement.Policy) extends SyntacticRule("VerifyMeasurement") {
  def this() = this(VerifyMeasurement.Policy())
  override def withConfiguration(c: Configuration): Configured[Rule] = c.conf.getOrElse("VerifyMeasurement")(VerifyMeasurement.Policy()).map(new VerifyMeasurement(_))
  private def lines(p: String) = Files.readAllLines(Paths.get(p)).asScala.toList
  private def table(p: String) = lines(p).drop(1).map(_.split("\t",-1).toList)
  private def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
  override def afterComplete(): Unit = {
    val out = Paths.get(policy.report); require(!Files.exists(out),"Never replace a review observation")
    val sourceRows = table("refactor-audit/models/pagination/build-v7/sources.tsv")
    require(lines("refactor-audit/models/pagination/build-v7/status.txt").head == "PASS")
    sourceRows.foreach(r => require(hash(Files.readString(Paths.get(r.head))) == r(1),"Source changed since full compilation: " + r.head))
    val fingerprint = "sources=" + hash(sourceRows.sortBy(_.head).map(_.mkString("\t")).mkString("\n"))
    require(lines("refactor-audit/assessment/current/fingerprints.txt").contains(fingerprint),"Measurement and compilation universes differ")
    require(lines("refactor-audit/assessment/current/status.txt").head == "MEASUREMENT_COMPLETE")
    val testsBefore = table("refactor-audit/models/pagination/tests/baseline.tsv").map(r => r.take(2) -> r).toMap
    val testsAfter = table("refactor-audit/models/pagination/tests/after.tsv").map(r => r.take(2) -> r).toMap
    require(testsBefore.keySet == testsAfter.keySet && testsBefore.size == 115)
    require(testsAfter.forall { case(id,r) => r(2) == "passed" || r == testsBefore(id) },"New or changed failing test evidence")
    require(testsAfter.values.count(_(2) == "passed") == 113 && testsAfter.values.count(_(2) == "failure") == 2)
    val reviewed = table("refactor-audit/models/pagination/review-1.tsv")
    require(reviewed.size == 11 && reviewed.map(_.head).distinct.size == reviewed.size)
    require(reviewed.forall(r => r(3) == "production" && r(4) == "confirmed"))
    val leaves = table("refactor-audit/models/leaves.tsv")
    val retainedEvidence = scala.collection.mutable.ListBuffer.empty[String]
    val rows = reviewed.map { r =>
      val found = leaves.filter(l => l.head == r(1) && (l(1) == r(2) || l(1).startsWith(r(2) + "/alias:")))
      require(found.nonEmpty,"A reviewed position disappeared: " + r.head)
      found.foreach(l => retainedEvidence += (r.head + "\t" + l.mkString("\t")))
      val dot = r(6).lastIndexOf('.')
      val pkg = r(6).take(dot).replace('.','/')
      val name = r(6).drop(dot+1)
      val expectedAlias = pkg + "/" + name + "$package." + name + "#"
      val primitive = found.exists(l => l(1) == r(2) && l(2) == "primitive")
      val typed = found.exists(l => l(1) == r(2) && l(2) == "alias" && l(3) == expectedAlias) &&
        found.exists(l => l(1) == r(2) + "/alias:" + expectedAlias && l(2) == "newtype")
      require(!(primitive && typed),"Conflicting evidence")
      val status = if(primitive) "outstanding" else if(typed) "verified-fixed" else "unresolved"
      List(r.head,status,r(1),r(2),r(6),r(10),"production")
    }
    val previous = policy.previous.map(p => table(p).map(r => r.head -> r).toMap)
    previous.foreach(p => require(p.keySet == rows.map(_.head).toSet,"Reviewed denominator changed; explicit reconciliation required"))
    val reopened = rows.filter(r => previous.exists(_.get(r.head).exists(_(1) == "verified-fixed")) && r(1) != "verified-fixed")
    Files.createDirectories(out)
    Files.write(out.resolve("findings.tsv"),("id\tstatus\tposition\troute\tintended-type\ttreatment\tscope" :: rows.map(_.mkString("\t"))).asJava)
    Files.write(out.resolve("evidence.tsv"),("id\tposition\troute\tstatus\tsymbol\tdetail" :: retainedEvidence.toList).asJava)
    Files.write(out.resolve("status.txt"),List(
      if(reopened.isEmpty) "PASS" else "REGRESSION",
      "reviewed-production=11", "reviewed-tests=0",
      "verified-fixed=" + rows.count(_(1) == "verified-fixed"),
      "outstanding=" + rows.count(_(1) == "outstanding"),
      "unresolved=" + rows.count(_(1) == "unresolved"),
      "reopened=" + reopened.size, fingerprint,
      "Scope: original eleven reviewed pagination findings only; no whole-model regression or 90% claim. Container-level unresolved evidence remains in the full inventory."
    ).asJava)
    require(reopened.isEmpty,"Previously fixed findings reopened")
    println(lines(out.resolve("status.txt").toString).mkString("\n"))
  }
}
