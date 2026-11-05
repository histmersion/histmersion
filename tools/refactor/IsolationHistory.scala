import scalafix.v1._
import metaconfig.{ConfDecoder, Configured, generic}
import java.nio.file.{Files, Path, Paths}
import scala.jdk.CollectionConverters._

object IsolationHistory {
  case class Policy(mode: String = "check", ledger: String = "refactor-audit/isolation-history", reviewedAdditions: List[String] = Nil, reviewedContributions: List[String] = Nil, selftestReport: String = "")
  object Policy {
    implicit val surface: generic.Surface[Policy] = generic.deriveSurface[Policy]
    implicit val decoder: ConfDecoder[Policy] = generic.deriveDecoder(Policy()).noTypos
  }
  def hash(s: String): String = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
  case class Edge(scope: String, module: String, path: String, owner: String, target: String) {
    def key: String = List(scope,module,path,owner,target).mkString("\t")
    def id: String = "ISO-" + hash(key)
    def confidence: String = if(owner.isEmpty) "file-level-owner-unresolved" else if(owner.startsWith("local") || owner.contains("::local")) "unstable-local-owner" else "symbol-relationship"
  }
  def regressions(before: Set[String], now: Set[String], absent: Set[String]): Set[String] = (now -- before) ++ (now intersect absent)
  def contributionWithinPrior(d: List[String], prior: List[String]): Boolean =
    d.size == 9 && prior.size == 11 && d(1) == "production" && d(3).contains("/src/main/") &&
      prior(1) == "production" && prior(2) == "accepted-exception" && prior(3) == "retain-exact-contribution" &&
      d(7) == prior.head && d(3) == prior(5) && d(8).trim.nonEmpty &&
      d(5).split('/').dropRight(1).toList == prior(7).split('/').dropRight(1).toList &&
      d.head == Edge(d(1),d(2),d(3),d(4),d(5)).id
  def verify(): Unit = {
    val a = Edge("production","core","modules/core/src/main/scala/A.scala","p/A#run().","p/B#")
    require(a.id == a.copy().id)
    require(regressions(Set(a.id),Set(a.copy(target="p/C#").id),Set.empty).nonEmpty, "Equal-count replacement must fail")
    require(regressions(Set(a.id),Set(a.id),Set(a.id)).nonEmpty, "Reappearance must fail")
    require(regressions(Set(a.id),Set.empty,Set.empty).isEmpty, "Absence alone is not a repair")
    require(a.id != a.copy(scope="test").id, "Scopes must remain separate")
    require(a.copy(owner="local12").confidence == "unstable-local-owner")
    require(a.copy(owner="/repo/A.scala::local12").confidence == "unstable-local-owner")
    require(a.copy(owner="").confidence == "file-level-owner-unresolved")
    val p = List(a.id,"production","accepted-exception","retain-exact-contribution","contribution",a.path,a.owner,a.target,"symbol-relationship","hash","reviewed contribution")
    val n = a.copy(owner="p/A.project().",target="p/B.Value.")
    val d = List(n.id,n.scope,n.module,n.path,n.owner,n.target,"hash",a.id,"Reviewed receiver projection")
    require(contributionWithinPrior(d,p))
    require(!contributionWithinPrior(d,p.updated(2,"candidate")), "Unreviewed parent cannot authorize a contribution")
    require(!contributionWithinPrior(d,p.updated(5,"modules/core/src/main/scala/Other.scala")), "Contribution permission is file-scoped")
    require(!contributionWithinPrior(d,p.updated(7,"other/B#")), "Contribution permission cannot cross receiver packages")
    require(!contributionWithinPrior(d.updated(1,"test"),p), "Production and test approvals remain separate")
  }
}

class IsolationHistory(val policy: IsolationHistory.Policy) extends SyntacticRule("IsolationHistory") {
  def this() = this(IsolationHistory.Policy())
  import IsolationHistory._
  override def withConfiguration(c: Configuration): Configured[Rule] = c.conf.getOrElse("IsolationHistory")(Policy()).map(new IsolationHistory(_))
  override def afterComplete(): Unit = {
    verify()
    require(Set("capture","check","selftest")(policy.mode))
    if(policy.mode == "selftest") {
      if(policy.selftestReport.nonEmpty) {
        val out = Paths.get(policy.selftestReport); require(!Files.exists(out))
        Files.createDirectories(out.getParent)
        Files.write(out,List("PASS", "identity replacement, reopening, scope, ambiguous owners and contribution-review guard checks",
          "rule-sha256=" + hash(Files.readString(Paths.get("tools/refactor/IsolationHistory.scala")))).asJava)
      }
      println("PASS: isolation identity regression contracts"); return
    }
    def lines(p: Path) = Files.readAllLines(p).asScala.toList
    def rows(p: Path) = lines(p).drop(1).map(_.split("\t",-1).toList)
    def write(p: Path, data: List[String]): Unit = { Files.createDirectories(p.getParent); Files.write(p,data.asJava) }
    val root = Paths.get("").toAbsolutePath.normalize
    def relative(s: String) = root.relativize(Paths.get(s).toAbsolutePath.normalize).toString
    val report = Paths.get("refactor-audit/architecture")
    require(lines(report.resolve("status.txt")) == List("COMPLETE_ARCHITECTURE_EVIDENCE_WITH_REVIEW_CASES"))
    val sources = rows(report.resolve("sources.tsv"))
    require(sources.forall(r => hash(Files.readString(Paths.get(r.head))) == r(1)), "Stale architecture source evidence")
    val stream = Files.walk(Paths.get("modules"))
    val expected = try stream.iterator.asScala.filter(p => Files.isRegularFile(p) && p.toString.endsWith(".scala") &&
      (p.toString.contains("/src/main/") || p.toString.contains("/src/test/"))).map(_.toString).toSet finally stream.close()
    require(sources.map(r => relative(r.head)).toSet == expected, "Incomplete source denominator")
    val raw = rows(report.resolve("dependency-crossings.tsv"))
    require(raw.forall(_.size == 12), "Unknown crossing schema")
    val forbidden = raw.filter(_(10) == "forbidden-direction")
    val symbolRows = forbidden.filterNot(_(6).endsWith("/"))
    val grouped = symbolRows.groupBy(r => Edge(r(2),r(11),relative(r.head),r(5),r(6)))
    val table = "id\tscope\tmodule\tpath\towner\ttarget\tidentity-confidence\tclassification\tevidence-count\tlocations" ::
      grouped.toList.sortBy(_._1.id).map { case(e,rs) => List(e.id,e.key,e.confidence,"candidate",rs.size.toString,rs.map(_(1)).distinct.sorted.mkString(",")).mkString("\t") }
    val ledger = Paths.get(policy.ledger)
    val baseline = ledger.resolve("baseline.tsv")
    val contract = List("format=1", "unit=resolved-symbol-dependency-relationship;not-occurrence",
      "identity=scope,module,path,owner,target;line-independent", "classification=candidate-until-source-adjudication",
      "package-qualifiers=retained-in-raw-evidence-counts;not-symbol-relationships") ++
      List("ArchitectureInventory.scala","architecture-inventory.conf","dependency-directions.conf").map(n => n + "=" + hash(Files.readString(Paths.get("tools/refactor",n))))
    if(policy.mode == "capture") {
      require(!Files.exists(baseline), "Never overwrite baseline")
      write(baseline,table); write(ledger.resolve("contract.txt"),contract)
    }
    require(lines(ledger.resolve("contract.txt")) == contract, "Policy changed: explicit correction required")
    val original = rows(baseline).map(_.head).toSet
    val adopted = policy.reviewedAdditions.flatMap { event =>
      val path = Paths.get(event)
      val recorded = lines(path.resolve("event.txt"))
      require(recorded.contains("baseline-sha256=" + hash(Files.readString(baseline))),"Adoption baseline changed")
      require(recorded.contains("contract-sha256=" + hash(Files.readString(ledger.resolve("contract.txt")))),"Adoption detector contract changed")
      require(recorded.contains("decisions-sha256=" + hash(Files.readString(path.resolve("decisions.tsv")))),"Adoption decisions changed")
      val decisions = rows(path.resolve("decisions.tsv"))
      require(decisions.forall(r => r.size == 5 && r(1).contains("/src/test/") && r(3) == "non-violation" && r(4).trim.nonEmpty),"Unsupported adoption classification/scope")
      require(decisions.forall(r => hash(Files.readString(Paths.get(r(1)))) == r(2)),"Review source changed; explicit re-review required")
      decisions.map(_.head)
    }
    val contributions = policy.reviewedContributions.flatMap { event =>
      val path = Paths.get(event)
      val recorded = lines(path.resolve("event.txt"))
      require(recorded.contains("baseline-sha256=" + hash(Files.readString(baseline))))
      require(recorded.contains("contract-sha256=" + hash(Files.readString(ledger.resolve("contract.txt")))))
      require(recorded.contains("decisions-sha256=" + hash(Files.readString(path.resolve("decisions.tsv")))))
      require(recorded.contains("prior-treatments-sha256=" + hash(Files.readString(path.resolve("prior-treatments.tsv")))))
      val parents = rows(path.resolve("prior-treatments.tsv")).map(r => r.head -> r).toMap
      val decisions = rows(path.resolve("decisions.tsv"))
      require(decisions.forall(d => d.size == 9 && parents.get(d(7)).exists(p => original(p.head) && contributionWithinPrior(d,p))), "Contribution lacks an accepted original parent in the same file and receiver package")
      require(decisions.forall(d => hash(Files.readString(Paths.get(d(3)))) == d(6)), "Contribution source changed; explicit re-review required")
      decisions.map(_.head)
    }
    val reviewed = adopted ++ contributions
    require(reviewed.distinct.size == reviewed.size && (reviewed.toSet intersect original).isEmpty,"Repeated/replaced baseline identity")
    val before = original ++ reviewed
    val now = grouped.keys.map(_.id).toSet
    val history = ledger.resolve("observations")
    val prior = if(Files.exists(history)) {
      val s = Files.walk(history)
      try s.iterator.asScala.filter(_.getFileName.toString == "absent.txt").flatMap(lines).toSet finally s.close()
    } else Set.empty[String]
    val added = now -- before
    val reopened = now intersect prior
    val observation = history.resolve(java.time.Instant.now().toString.replace(':','-'))
    write(observation.resolve("findings.tsv"),table)
    write(observation.resolve("new.txt"),added.toList.sorted)
    write(observation.resolve("reopened.txt"),reopened.toList.sorted)
    write(observation.resolve("absent.txt"),(before -- now).toList.sorted)
    write(observation.resolve("inputs.txt"),List("sources=" + hash(lines(report.resolve("sources.tsv")).mkString("\n")),
      "crossings=" + hash(Files.readString(report.resolve("dependency-crossings.tsv")))))
    val summary = List(if(regressions(before,now,prior).isEmpty) "PASS" else "FAIL",
      "source-files=" + sources.size, "raw-forbidden-evidence=" + forbidden.size,
      "package-qualifier-evidence=" + (forbidden.size-symbolRows.size), "candidate-relationships=" + now.size,
      "production-candidates=" + grouped.keys.count(_.scope == "production"), "test-candidates=" + grouped.keys.count(_.scope == "test"),
      "ambiguous-owner-relationships=" + grouped.keys.count(_.confidence != "symbol-relationship"),
      "unclassified-direction-evidence=" + raw.count(_(10) == "unclassified-direction"),
      "reviewed-additional-test-nonviolations=" + adopted.size,
      "reviewed-contribution-identities=" + contributions.size,
      "new=" + added.size, "reopened=" + reopened.size,
      "No confirmed violations or verified repairs inferred. Repeated uses share a relationship; occurrence multiplicity is not a gate.",
      "Missing/local owners remain ambiguous. Build dependencies and permitted crossings are separate evidence, outside this identity gate.")
    write(observation.resolve("status.txt"),summary)
    println(summary.mkString("\n"))
    require(regressions(before,now,prior).isEmpty,"New or reopened isolation relationships")
  }
}
