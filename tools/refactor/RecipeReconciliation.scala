import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, Configured, generic}
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

object RecipeReconciliation {
  case class Policy(report: String = "", build: String = "", baselineBuild: String = "", preview: String = "", before: String = "", treatments: String = "", batch: String = "", expected: Int = 0, suites: Map[String,Int] = Map.empty)
  object Policy {
    implicit val surface: generic.Surface[Policy] = generic.deriveSurface[Policy]
    implicit val decoder: ConfDecoder[Policy] = generic.deriveDecoder(Policy()).noTypos
  }
}
class RecipeReconciliation(val policy: RecipeReconciliation.Policy) extends SyntacticRule("RecipeReconciliation") {
  def this() = this(RecipeReconciliation.Policy())
  override def withConfiguration(c: Configuration): Configured[Rule] = c.conf.getOrElse("RecipeReconciliation")(RecipeReconciliation.Policy()).map(new RecipeReconciliation(_))
  override def afterComplete(): Unit = {
    def read(p: String) = Files.readString(Paths.get(p))
    def lines(p: String) = Files.readAllLines(Paths.get(p)).asScala.toList
    def rows(p: String) = lines(p).drop(1).map(_.split("\t", -1).toList)
    def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    val root = Paths.get("").toAbsolutePath.normalize
    def relative(p: String) = root.relativize(Paths.get(p).toAbsolutePath.normalize).toString
    val out = Paths.get(policy.report)
    require(policy.expected > 0 && policy.suites.nonEmpty && !Files.exists(out))
    List(policy.build,policy.baselineBuild).foreach(p => require(lines(p + "/status.txt").head == "PASS"))
    val built = rows(policy.build + "/sources.tsv").map(r => r.head -> r(1)).toMap
    val baseline = rows(policy.baselineBuild + "/sources.tsv").map(r => r.head -> r(1)).toMap
    built.foreach { case (p,h) => require(hash(read(p)) == h, "Stale source: " + p) }
    List("architecture", "models").foreach { family =>
      require(lines(s"refactor-audit/$family/status.txt").head.startsWith("COMPLETE"))
      require(rows(s"refactor-audit/$family/sources.tsv").map(r => relative(r.head) -> r(1)).toMap == built)
    }
    val dirs = Files.list(Paths.get("refactor-audit/isolation-history/observations"))
    val latest = try dirs.iterator.asScala.filter(Files.isDirectory(_)).toList.sortBy(_.toString).last finally dirs.close()
    require(lines(latest.resolve("status.txt").toString).head == "PASS")
    val inputs = lines(latest.resolve("inputs.txt").toString)
    require(inputs.contains("sources=" + hash(lines("refactor-audit/architecture/sources.tsv").mkString("\n"))))
    require(inputs.contains("crossings=" + hash(read("refactor-audit/architecture/dependency-crossings.tsv"))))
    val before = rows(policy.before).map(r => r.head -> r).toMap
    val decisions = rows(policy.treatments)
    require(decisions.map(_.head).toSet == before.keySet && decisions.size == before.size)
    val selected = decisions.filter(_(4) == policy.batch)
    require(selected.size == policy.expected && selected.forall(r => r(1) == "production" && r(2) == "confirmed"))
    val ids = selected.map(_.head).toSet
    val after = rows(latest.resolve("findings.tsv").toString).map(r => r.head -> r).toMap
    require(before.keySet -- after.keySet == ids, "Unexpected removals")
    require((after.keySet -- before.keySet).isEmpty, "New identities")
    require(after.forall { case (id,r) => r.take(9) == before(id).take(9) }, "Residual evidence changed")
    val plan = rows(policy.preview + "/plan.tsv")
    plan.foreach(r => require(built(r.head) == r(2) && baseline(r.head) == r(1), "Patch differs from reviewed baseline/preview: " + r.head))
    require(built.keySet == baseline.keySet)
    require(built.filterNot { case(p,_) => plan.exists(_.head == p) } == baseline.filterNot { case(p,_) => plan.exists(_.head == p) }, "Unplanned source changes")
    def tests(p: String) = dialects.Scala3(read(p)).parse[Source].get.collect {
      case a: Term.Apply if a.fun.syntax.startsWith("test(") => a.structure
    }
    policy.suites.foreach { case(suite,count) =>
      val paths = built.keys.filter(_.endsWith("/" + suite + ".scala")).toList
      require(paths.size == 1)
      val p = paths.head
      val original = if(plan.exists(_.head == p)) policy.preview + "/before/" + p else p
      require(tests(p).size == count && tests(p) == tests(original), "Test assertions changed: " + suite)
      List(policy.build,policy.baselineBuild).foreach(b => require(read(b + "/build.log").contains(suite + " finished: 0 failed, 0 ignored, " + count + " total")))
    }
    Files.createDirectories(out)
    def write(name: String, data: List[String]) = Files.write(out.resolve(name), data.asJava)
    write("events.tsv", "id\tevent\tevidence" :: selected.map(r => r.head + "\tverified-fixed\t" + latest))
    write("current-treatment.tsv", lines(policy.treatments).head :: decisions.filterNot(r => ids(r.head)).map(_.mkString("\t")))
    write("status.txt", List("PASS", "verified-production-relationships=" + selected.size,
      "remaining-production=" + after.values.count(_(1) == "production"), "remaining-test=" + after.values.count(_(1) == "test"),
      "new=0", "tests-before-and-after=" + policy.suites.values.sum, "source-files=" + built.size, "isolation-observation=" + latest,
      "Exact configured AST patch, compilation, unchanged test assertions and original-ID reconciliation establish bounded repair evidence. Not proof of semantic equivalence for arbitrary inputs or independent detector quality."))
    println(read(out.resolve("status.txt").toString))
  }
}
