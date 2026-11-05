import scalafix.v1._
import metaconfig.{ConfDecoder, Configured, generic}
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

object ReviewIsolationChanges {
  case class Policy(observation: String = "", priorTreatments: String = "", report: String = "", tests: Map[String,String] = Map.empty, contributions: Map[String,String] = Map.empty, contributionReason: String = "", testsFrom: String = "")
  object Policy {
    implicit val surface: generic.Surface[Policy] = generic.deriveSurface[Policy]
    implicit val decoder: ConfDecoder[Policy] = generic.deriveDecoder(Policy()).noTypos
  }
}
class ReviewIsolationChanges(val policy: ReviewIsolationChanges.Policy) extends SyntacticRule("ReviewIsolationChanges") {
  def this() = this(ReviewIsolationChanges.Policy())
  override def withConfiguration(c: Configuration): Configured[Rule] = c.conf.getOrElse("ReviewIsolationChanges")(ReviewIsolationChanges.Policy()).map(new ReviewIsolationChanges(_))
  override def afterComplete(): Unit = {
    def read(p: String) = Files.readString(Paths.get(p))
    def lines(p: String) = Files.readAllLines(Paths.get(p)).asScala.toList
    def rows(p: String) = lines(p).drop(1).map(_.split("\t",-1).toList)
    def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    val out = Paths.get(policy.report)
    require(!Files.exists(out))
    val current = rows(policy.observation + "/findings.tsv").map(r => r.head -> r).toMap
    val prior = rows(policy.priorTreatments).map(r => r.head -> r).toMap
    val inheritedTests = if(policy.testsFrom.isEmpty) Map.empty[String,String] else {
      val path = policy.testsFrom + "/decisions.tsv"
      require(lines(policy.testsFrom + "/event.txt").contains("decisions-sha256=" + hash(read(path))))
      rows(path).map { r =>
        require(r.size == 5 && r(3) == "non-violation" && hash(read(r(1))) == r(2), "Prior test review changed")
        r.head -> r(4)
      }.toMap
    }
    require(policy.tests.keySet.intersect(inheritedTests.keySet).isEmpty, "Do not silently override prior decisions")
    val tests = inheritedTests ++ policy.tests
    require(tests.keySet.intersect(policy.contributions.keySet).isEmpty)
    require(tests.keySet ++ policy.contributions.keySet == lines(policy.observation + "/new.txt").toSet, "Every new ID must have an explicit decision")
    require(lines(policy.observation + "/reopened.txt").isEmpty, "Reopenings need separate adjudication")
    val testRows = tests.toList.sorted.map { case(id,reason) =>
      val r = current(id); require(r(1) == "test" && r(3).contains("/src/test/") && reason.trim.nonEmpty)
      List(id,r(3),hash(read(r(3))),"non-violation",reason)
    }
    val contributionRows = policy.contributions.toList.sorted.map { case(id,parent) =>
      val r = current(id); val p = prior(parent)
      require(r(1) == "production" && p(1) == "production" && p(2) == "accepted-exception" && p(3) == "retain-exact-contribution")
      require(r(3) == p(5) && r(5).split('/').dropRight(1).toList == p(7).split('/').dropRight(1).toList)
      require(policy.contributionReason.trim.nonEmpty)
      List(id,r(1),r(2),r(3),r(4),r(5),hash(read(r(3))),parent,policy.contributionReason)
    }
    def write(part: String, header: String, data: List[List[String]], parents: Boolean): Unit = if(data.nonEmpty) {
      val dir = out.resolve(part); Files.createDirectories(dir)
      Files.write(dir.resolve("decisions.tsv"),(header :: data.map(_.mkString("\t"))).asJava)
      val extra = if(parents) {
        Files.copy(Paths.get(policy.priorTreatments),dir.resolve("prior-treatments.tsv"))
        List("prior-treatments-sha256=" + hash(read(dir.resolve("prior-treatments.tsv").toString)))
      } else Nil
      Files.write(dir.resolve("event.txt"),(List(
        "baseline-sha256=" + hash(read("refactor-audit/isolation-history/baseline.tsv")),
        "contract-sha256=" + hash(read("refactor-audit/isolation-history/contract.txt")),
        "decisions-sha256=" + hash(read(dir.resolve("decisions.tsv").toString)),
        "observation=" + policy.observation,
        "observation-sha256=" + hash(read(policy.observation + "/findings.tsv"))) ++ extra).asJava)
    }
    write("tests","id\tpath\tsource-sha256\tclassification\treason",testRows,false)
    write("contributions","id\tscope\tmodule\tpath\towner\ttarget\tsource-sha256\tparent-id\treason",contributionRows,true)
    println("Reviewed exact additions: " + contributionRows.size + " contribution identities; " + testRows.size + " test non-violations. Original baseline retained.")
  }
}
