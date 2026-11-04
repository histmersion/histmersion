import scalafix.v1._
import metaconfig.{ConfDecoder, Configured, generic}
import java.nio.file.{Files, Path, Paths}
import java.nio.charset.StandardCharsets.UTF_8
import scala.jdk.CollectionConverters._

object CapabilityHistory {
  case class Policy(mode: String = "check", report: String = "refactor-audit/assessment/current", ledger: String = "refactor-audit/capability-history",
    adoption: String = "", evidence: String = "", additions: String = "", reason: String = "", sourceEvidence: String = "")
  object Policy {
    implicit val surface: generic.Surface[Policy] = generic.deriveSurface[Policy]
    implicit val decoder: ConfDecoder[Policy] = generic.deriveDecoder(Policy()).noTypos
  }
  def hash(s: String): String = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(UTF_8)).map(b => f"${b & 255}%02x").mkString
  case class Finding(rule: String, path: String, owner: String, symbol: String, ast: String) {
    def scope: String = if(path.contains("/src/test/")) "test" else "production"
    def key: String = List(rule,path,owner,symbol,ast).mkString("\t")
    def id: String = "CAP-" + hash(key)
    def stableOwner: Boolean = owner.nonEmpty && !owner.startsWith("local")
  }
  case class Delta(added: Set[String], absent: Set[String], changedMultiplicity: Set[String], reopened: Set[String]) {
    def passes: Boolean = added.isEmpty && changedMultiplicity.isEmpty && reopened.isEmpty
  }
  def compare(old: Map[String,Int], now: Map[String,Int], tombstones: Set[String]): Delta =
    Delta(now.keySet -- old.keySet, old.keySet -- now.keySet,
      (old.keySet intersect now.keySet).filter(k => old(k) != now(k)), now.keySet intersect tombstones)
  def verify(): Unit = {
    val a = Finding("clock","modules/core/src/main/scala/A.scala","pkg/A.run().","clock/now().","Term.Name(\"now\")")
    val b = a.copy(owner = "pkg/A.other().")
    val original = Map(a.id -> 1)
    require(a.id == a.copy().id, "Identity must be deterministic")
    require(compare(original,original,Set.empty).passes, "Unchanged observations must pass")
    require(!compare(original,Map(b.id -> 1),Set.empty).passes, "Equal-count substitution must fail")
    val removed = compare(original,Map.empty,Set.empty)
    require(removed.passes && removed.absent == Set(a.id), "Absence is recorded, not declared a verified fix")
    require(!compare(original,original,removed.absent).passes, "Reappearance must fail")
    require(!compare(original,Map(a.id -> 2),Set.empty).passes, "Duplicate insertion must fail")
    require(!compare(Map(a.id -> 2),original,Set.empty).passes, "Ambiguous duplicate removal needs reconciliation")
    require(!a.copy(owner = "local12").stableOwner, "Local compiler symbols cannot establish stable ownership")
    require(a.id != a.copy(path = "modules/core/src/test/scala/A.scala").id, "Scopes must not share identity")
  }
}

class CapabilityHistory(val policy: CapabilityHistory.Policy) extends SyntacticRule("CapabilityHistory") {
  def this() = this(CapabilityHistory.Policy())
  import CapabilityHistory._
  override def withConfiguration(c: Configuration): Configured[Rule] = c.conf.getOrElse("CapabilityHistory")(Policy()).map(new CapabilityHistory(_))
  private def lines(p: Path): List[String] = Files.readAllLines(p,UTF_8).asScala.toList
  private def write(p: Path, rows: List[String]): Unit = { Files.createDirectories(p.getParent); Files.write(p,rows.asJava,UTF_8) }
  private def walk(p: Path): List[Path] = { val s = Files.walk(p); try s.iterator.asScala.filter(Files.isRegularFile(_)).toList finally s.close() }
  override def afterComplete(): Unit = {
    require(Set("capture","check","selftest","adopt")(policy.mode), "Unknown history mode")
    verify()
    if(policy.mode == "selftest") { println("PASS: unchanged, substitution, absence, reappearance, duplicates, unstable owners and scope isolation"); return }
    val report = Paths.get(policy.report)
    require(lines(report.resolve("status.txt")).headOption.contains("MEASUREMENT_COMPLETE"),"Requires a complete project assessment")
    val sources = walk(Paths.get("modules")).filter(p => p.toString.endsWith(".scala") && (p.toString.contains("/src/main/") || p.toString.contains("/src/test/")))
    val sourceHash = hash(sources.sortBy(_.toString).map(p => p.toString + "\t" + hash(Files.readString(p))).mkString("\n"))
    require(lines(report.resolve("fingerprints.txt")).contains("sources=" + sourceHash),"Assessment sources are stale")
    require(policy.evidence.isEmpty || policy.mode == "adopt", "Separate evidence is only allowed for explicit adoption")
    val capability = if(policy.evidence.isEmpty) report.resolve("capabilities") else Paths.get(policy.evidence)
    require(lines(capability.resolve("missing-files.txt")).isEmpty,"Incomplete capability coverage")
    val root = Paths.get("").toAbsolutePath.normalize
    def relative(s: String): String = root.relativize(Paths.get(s).toAbsolutePath.normalize).toString
    val covered = lines(capability.resolve("files.txt")).map(relative).toSet
    require(covered == sources.filter(_.toString.contains("/src/main/")).map(_.toString).toSet,"Production coverage changed or incomplete")
    val findings = lines(capability.resolve("findings.tsv")).drop(1).map { line =>
      val r = line.split("\t",-1); require(r.length == 9,"Unexpected capability report schema")
      Finding(r(0),relative(r(1)),r(4),r(5),r(7))
    }
    val grouped = findings.groupBy(_.id)
    require(grouped.values.forall(_.map(_.key).distinct.size == 1),"Identity hash collision")
    val counts = grouped.view.mapValues(_.size).toMap
    val ledger = Paths.get(policy.ledger)
    val baseline = ledger.resolve("baseline.tsv")
    val policyFiles = List("CapabilityAccess.scala","capabilities.conf","capabilities-source.conf","capability-source-examples.conf")
    val policyHash = hash(policyFiles.map(name => name + "\t" + hash(Files.readString(Paths.get("tools/refactor",name)))).mkString("\n"))
    require(lines(capability.resolve("identity-policy.txt")) == List(policyHash),"Capability report used a different detector/policy version")
    val contract = List("format=1", "scope=production-only", "identity=rule,path,owner,symbol,AST;line-independent",
      "classification=detector-evidence;not-blanket-confirmed", "ambiguity=local-owners-and-repeated-identical-anchors",
      "rules=" + hash(lines(capability.resolve("rules.txt")).sorted.mkString("\n")), "detector-and-policy=" + policyHash)
    val table = "id\tcount\tscope\tidentity-confidence\trule\tpath\towner\tsymbol\tast" :: grouped.toList.sortBy(_._1).map { case (id,rs) =>
      val f = rs.head; List(id,rs.size.toString,f.scope,if(rs.size == 1 && f.stableOwner) "unique-anchor" else "ambiguous",f.key).mkString("\t")
    }
    if(policy.mode == "capture") {
      require(!Files.exists(baseline),"Frozen history baseline already exists; explicit reconciliation required")
      write(ledger.resolve("contract.txt"),contract)
      write(baseline,table)
    }
    val originalContract = lines(ledger.resolve("contract.txt"))
    val original = lines(baseline).drop(1).map(_.split("\t",-1)).map(r => r(0) -> r(1).toInt).toMap
    val adoption = if(policy.adoption.isEmpty) None else Some(Paths.get(policy.adoption))
    if(policy.mode == "adopt") {
      require(adoption.nonEmpty && policy.additions.nonEmpty && policy.sourceEvidence.nonEmpty && policy.reason.trim.nonEmpty,"Adoption requires explicit reviewed additions, source evidence and reason")
      val destination = adoption.get
      require(!Files.exists(destination),"Adoption already exists; never overwrite history events")
      val evidence = lines(Paths.get(policy.sourceEvidence))
      require(evidence.contains("sources=" + sourceHash),"Additive correction source evidence is stale")
      val initialSources = walk(ledger.resolve("observations")).filter(_.getFileName.toString == "sources.txt").sortBy(_.toString).head
      require(lines(initialSources) == List(sourceHash),"Discovery-only adoption requires unchanged baseline application sources")
      val reviewed = lines(Paths.get(policy.additions))
      val additions = reviewed.drop(1).map(_.split("\t",-1).toList)
      require(additions.nonEmpty && additions.forall(r => r.size == 10 && r(2) == "confirmed" && r(3).nonEmpty && r(4).nonEmpty &&
        r(0) == "CAP-" + hash(r.drop(5).mkString("\t"))),"Invalid reviewed finding identities/classifications")
      val extra = additions.map(r => r.head -> r(1).toInt).toMap
      require(extra.size == additions.size && extra.values.forall(_ > 0) && (extra.keySet intersect original.keySet).isEmpty,"Invalid or duplicate additions")
      require(counts == original ++ extra,"Discovery-only adoption must preserve every original finding and add exactly the reviewed occurrences")
      require(originalContract != contract,"No detector-policy change to adopt")
      write(destination.resolve("previous-contract.txt"),originalContract)
      write(destination.resolve("contract.txt"),contract)
      write(destination.resolve("additions.tsv"),reviewed)
      write(destination.resolve("source-evidence.txt"),evidence)
      write(destination.resolve("event.txt"),List("ADOPTED_ADDITIVE_DETECTOR_CORRECTION",java.time.Instant.now().toString,policy.reason,
        "baseline-sha256=" + hash(Files.readString(baseline)),"additions-sha256=" + hash(reviewed.mkString("\n")),"source-sha256=" + sourceHash))
    }
    val extra = adoption.map { event =>
      require(lines(event.resolve("previous-contract.txt")) == originalContract,"Adoption has a different predecessor")
      require(lines(event.resolve("event.txt")).contains("baseline-sha256=" + hash(Files.readString(baseline))),"Original baseline changed after adoption")
      require(lines(event.resolve("event.txt")).contains("additions-sha256=" + hash(lines(event.resolve("additions.tsv")).mkString("\n"))),"Reviewed additions changed after adoption")
      require(lines(event.resolve("contract.txt")) == contract,"Adoption does not cover this detector-policy version")
      lines(event.resolve("additions.tsv")).drop(1).map(_.split("\t",-1)).map(r => r(0) -> r(1).toInt).toMap
    }.getOrElse {
      require(originalContract == contract,"History contract changed; explicit reconciliation required")
      Map.empty[String,Int]
    }
    require((original.keySet intersect extra.keySet).isEmpty,"Adoption cannot replace baseline findings")
    val old = original ++ extra
    val history = ledger.resolve("observations")
    val priorAbsences = if(Files.exists(history)) walk(history).filter(_.getFileName.toString == "absent.txt").flatMap(lines).toSet else Set.empty[String]
    val delta = compare(old,counts,priorAbsences)
    val observation = history.resolve(java.time.Instant.now().toString.replace(':','-'))
    write(observation.resolve("findings.tsv"),table)
    write(observation.resolve("absent.txt"),delta.absent.toList.sorted)
    write(observation.resolve("new.txt"),delta.added.toList.sorted)
    write(observation.resolve("reopened.txt"),delta.reopened.toList.sorted)
    write(observation.resolve("ambiguous-changes.txt"),delta.changedMultiplicity.toList.sorted)
    write(observation.resolve("sources.txt"),List(sourceHash))
    val summary = List(if(delta.passes) "PASS" else "FAIL",s"occurrences=${findings.size}",s"anchors=${counts.size}",
      s"ambiguous-occurrences=${grouped.values.filter(rs => rs.size != 1 || !rs.head.stableOwner).map(_.size).sum}",
      s"new=${delta.added.size}",s"absent-not-verified-fixed=${delta.absent.size}",s"reopened=${delta.reopened.size}",s"multiplicity-changes=${delta.changedMultiplicity.size}",
      "Tests, permission occurrences, dynamic dispatch and callback effects are not covered by this gate.",
      "Identical replacements with the same owner/path/symbol/AST cannot be distinguished; duplicate anchors have no per-occurrence identity claim.")
    write(observation.resolve("status.txt"),summary)
    println(summary.mkString("\n"))
    require(delta.passes,"Finding identity regression or ambiguous change; inspect " + observation)
  }
}
