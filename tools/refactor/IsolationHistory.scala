import scalafix.v1._
import metaconfig.{ConfDecoder, Configured, generic}
import java.nio.file.{Files, Path, Paths}
import scala.jdk.CollectionConverters._

object IsolationHistory {
  case class Policy(mode: String = "check", ledger: String = "refactor-audit/isolation-history", reviewedAdditions: List[String] = Nil)
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
  }
}

class IsolationHistory(val policy: IsolationHistory.Policy) extends SyntacticRule("IsolationHistory") {
  def this() = this(IsolationHistory.Policy())
  import IsolationHistory._
  override def withConfiguration(c: Configuration): Configured[Rule] = c.conf.getOrElse("IsolationHistory")(Policy()).map(new IsolationHistory(_))
  override def afterComplete(): Unit = {
    verify()
    require(Set("capture","check","selftest")(policy.mode))
    if(policy.mode == "selftest") { println("PASS: isolation identity regression contracts"); return }
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
    require(adopted.distinct.size == adopted.size && (adopted.toSet intersect original).isEmpty,"Repeated/replaced baseline identity")
    val before = original ++ adopted
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
      "new=" + added.size, "reopened=" + reopened.size,
      "No confirmed violations or verified repairs inferred. Repeated uses share a relationship; occurrence multiplicity is not a gate.",
      "Missing/local owners remain ambiguous. Build dependencies and permitted crossings are separate evidence, outside this identity gate.")
    write(observation.resolve("status.txt"),summary)
    println(summary.mkString("\n"))
    require(regressions(before,now,prior).isEmpty,"New or reopened isolation relationships")
  }
}
