import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, Configured, generic}
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

object RecipeReconciliation {
  case class Policy(report: String = "", build: String = "", baselineBuild: String = "", preview: String = "", before: String = "", treatments: String = "", batch: String = "", expected: Int = 0, suites: Map[String,Int] = Map.empty, identityReview: String = "", retiredIdentities: Map[String,String] = Map.empty, pureNewtypes: List[String] = Nil, followupPreviews: List[String] = Nil, callerRelocations: Map[String,String] = Map.empty, absentNewtypes: List[String] = Nil)
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
    val contributions = if(policy.identityReview.isEmpty) Nil else rows(policy.identityReview + "/contributions/decisions.tsv")
    val testReviews = if(policy.identityReview.isEmpty) Nil else rows(policy.identityReview + "/tests/decisions.tsv")
    val reviewedIds = (contributions ++ testReviews).map(_.head).toSet
    require(reviewedIds.size == contributions.size + testReviews.size && (reviewedIds intersect before.keySet).isEmpty)
    contributions.foreach { d =>
      val r = after(d.head)
      val parent = decisions.find(_.head == d(7)).get
      require(parent(2) == "accepted-exception" && parent(5) == d(3))
      require(r.slice(1,6) == d.slice(1,6) && built(d(3)) == d(6))
    }
    testReviews.foreach { d =>
      val r = after(d.head)
      require(r(1) == "test" && r(3) == d(1) && built(d(1)) == d(2) && d(3) == "non-violation")
    }
    policy.retiredIdentities.foreach { case(old,next) =>
      require(!ids(old) && before.contains(old) && reviewedIds(next))
      val a = before(old); val b = after(next)
      require(a(1) == b(1) && a(3) == b(3))
      if(a(1) == "production") {
        require(decisions.find(_.head == old).get(2) == "accepted-exception")
        require(contributions.exists(d => d.head == next && d(7) == old) && a(5) == b(5))
      } else {
        require(a(4).isEmpty && b(4).isEmpty && a(5).stripSuffix("#") == b(5).stripSuffix("."))
        require(built(a(3)) == baseline(a(3)), "Import-symbol correction requires unchanged test source")
      }
    }
    val crossings = rows("refactor-audit/architecture/dependency-crossings.tsv")
    policy.callerRelocations.foreach { case(id,target) =>
      val old = before(id)
      require(old(1) == "test" && old(5) != target && !ids(id) && !policy.retiredIdentities.contains(id))
      require(crossings.exists(r => relative(r.head) == old(3) && r(5) == old(4) && r(6) == target), "Missing relocated caller evidence: " + id)
    }
    require(before.keySet -- after.keySet == ids ++ policy.retiredIdentities.keySet ++ policy.callerRelocations.keySet, "Unexpected removals")
    require(after.keySet -- before.keySet == reviewedIds, "Unreviewed new identities")
    require(after.filter { case(id,_) => before.contains(id) }.forall { case (id,r) => r.take(9) == before(id).take(9) }, "Residual evidence changed")
    val originalTestSources = scala.collection.mutable.Map.empty[String,String]
    val expectedSources = (policy.preview :: policy.followupPreviews).foldLeft(baseline) { (expected,preview) =>
      val plan = rows(preview + "/plan.tsv")
      plan.foreach { r =>
        require(expected.get(r.head).contains(r(1)), "Patch-chain baseline mismatch: " + r.head)
        val snapshot = preview + "/before/" + r.head
        require(hash(read(snapshot)) == r(1), "Before snapshot mismatch")
        originalTestSources.getOrElseUpdate(r.head,snapshot)
      }
      val patched = expected ++ plan.map(r => r.head -> r(2))
      val movesPath = preview + "/moves.tsv"
      val moves = if(Files.exists(Paths.get(movesPath))) rows(movesPath).map(r => r.head -> r(1)).toMap else Map.empty[String,String]
      require(moves.values.forall(p => !patched.contains(p)), "Move destination already exists")
      patched.map { case(p,h) => moves.getOrElse(p,p) -> h }
    }
    require(built == expectedSources, "Compiled sources differ from complete patch chain")
    def tests(p: String) = dialects.Scala3(read(p)).parse[Source].get.collect {
      case a: Term.Apply if a.fun.syntax.startsWith("test(") => a.structure
    }
    policy.suites.foreach { case(suite,count) =>
      val paths = built.keys.filter(_.endsWith("/" + suite + ".scala")).toList
      require(paths.size == 1)
      val p = paths.head
      val original = originalTestSources.getOrElse(p,p)
      require(tests(p).size == count && tests(p) == tests(original), "Test assertions changed: " + suite)
      List(policy.build,policy.baselineBuild).foreach(b => require(read(b + "/build.log").contains(suite + " finished: 0 failed, 0 ignored, " + count + " total")))
    }
    val newtypes = rows("refactor-audit/models/newtype-constraints.tsv")
    policy.pureNewtypes.foreach { symbol =>
      val found = newtypes.filter(_.head == symbol)
      require(found.size == 1 && found.head.slice(5,8) == List("String","Pure","pure"), "Unexpected projection type: " + symbol)
    }
    policy.absentNewtypes.foreach(symbol => require(!newtypes.exists(_.head == symbol), "Superseded/duplicate newtype remains: " + symbol))
    Files.createDirectories(out)
    def write(name: String, data: List[String]) = Files.write(out.resolve(name), data.asJava)
    val retired = policy.retiredIdentities.toList.sorted.map { case(a,b) => a + "\tidentity-replaced-no-repair-credit\t" + b } ++
      policy.callerRelocations.toList.sorted.map { case(id,target) => id + "\tcaller-relocated-no-repair-credit\t" + target }
    val reviewed = contributions.map(d => d.head + "\taccepted-contribution-identity\t" + d(7)) ++ testReviews.map(d => d.head + "\tadjudicated-test-non-violation\t" + policy.identityReview)
    write("events.tsv", "id\tevent\tevidence" :: (selected.map(r => r.head + "\tverified-fixed\t" + latest) ++ retired ++ reviewed))
    val contributionTreatments = contributions.map { d => val r = after(d.head)
      List(d.head,"production","accepted-exception","retain-exact-contribution",decisions.find(_.head == d(7)).get(4),d(3),d(4),d(5),r(6),d(6),d(8)) }
    val testTreatments = testReviews.map { d => val r = after(d.head)
      List(d.head,"test","non-violation","retain-test-evidence","reviewed-test-contribution",r(3),r(4),r(5),r(6),d(2),d(4)) }
    write("current-treatment.tsv", lines(policy.treatments).head :: (decisions.filterNot(r => ids(r.head) || policy.retiredIdentities.contains(r.head) || policy.callerRelocations.contains(r.head)) ++ contributionTreatments ++ testTreatments).sortBy(_.head).map(_.mkString("\t")))
    write("status.txt", List("PASS", "verified-production-relationships=" + selected.size,
      "remaining-production=" + after.values.count(_(1) == "production"), "remaining-test=" + after.values.count(_(1) == "test"),
      "unreviewed-new=0", "reviewed-added-identities=" + reviewedIds.size, "retired-identities-no-repair-credit=" + policy.retiredIdentities.size,
      "caller-relocations-no-repair-credit=" + policy.callerRelocations.size,
      "tests-before-and-after=" + policy.suites.values.sum, "source-files=" + built.size, "isolation-observation=" + latest,
      "Exact configured AST patch, compilation, unchanged test assertions and original-ID reconciliation establish bounded repair evidence. Not proof of semantic equivalence for arbitrary inputs or independent detector quality."))
    println(read(out.resolve("status.txt").toString))
  }
}
