import scalafix.v1._
import metaconfig.{ConfDecoder, Configured, generic}
import scala.meta._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

object CapabilityReview {
  case class Policy(observation: String = "", decisions: String = "", report: String = "")
  object Policy {
    implicit val surface: generic.Surface[Policy] = generic.deriveSurface[Policy]
    implicit val decoder: ConfDecoder[Policy] = generic.deriveDecoder(Policy()).noTypos
  }
}
class CapabilityReview(val policy: CapabilityReview.Policy) extends SyntacticRule("CapabilityReview") {
  def this() = this(CapabilityReview.Policy())
  override def withConfiguration(c: Configuration): Configured[Rule] =
    c.conf.getOrElse("CapabilityReview")(CapabilityReview.Policy()).map(new CapabilityReview(_))
  override def afterComplete(): Unit = {
    implicit val dialect: Dialect = dialects.Scala3
    def read(p: String) = Files.readString(Paths.get(p))
    def lines(p: String) = Files.readAllLines(Paths.get(p)).asScala.toList
    def rows(p: String) = lines(p).tail.map(_.split("\t", -1).toList)
    def hash(s: String): String = java.security.MessageDigest.getInstance("SHA-256")
      .digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    def escape(s: String) = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")
    val out = Paths.get(policy.report)
    require(!Files.exists(out), "Never overwrite a review event")
    val stream = Files.walk(Paths.get("modules"))
    val sources = try stream.iterator.asScala.filter(p => Files.isRegularFile(p) && p.toString.endsWith(".scala") &&
      (p.toString.contains("/src/main/") || p.toString.contains("/src/test/"))).toList finally stream.close()
    val fingerprint = hash(sources.sortBy(_.toString).map(p => p.toString + "\t" + hash(Files.readString(p))).mkString("\n"))
    require(lines(policy.observation + "/sources.txt") == List(fingerprint), "Review source universe is stale")
    require(lines(policy.observation + "/status.txt").head == "PASS", "Use a passed observation")
    val findings = rows(policy.observation + "/findings.tsv")
    require(findings.forall(_.size == 9) && findings.map(_.head).distinct.size == findings.size)
    val decisions = rows(policy.decisions)
    val selected = scala.collection.mutable.Map.empty[String, List[String]]
    decisions.foreach { d =>
      require(d.size == 8, "Expected selector/key/rule/count/classification/treatment/batch/reason")
      val matched = findings.filter(r => d.head match {
        case "id" => r.head == d(1) && r(4) == d(2)
        case "file" => r(5) == d(1) && r(4) == d(2)
        case _ => throw new IllegalArgumentException("Unknown selector")
      })
      require(matched.nonEmpty && matched.map(_(1).toInt).sum == d(3).toInt, "Review membership/count changed: " + d(1))
      require(Set("confirmed", "non-violation", "unresolved", "accepted-exception")(d(4)))
      require(d(4) != "confirmed" || Set("bulk-rewrite", "targeted-rewrite", "manual/design")(d(5)))
      require(d.drop(5).forall(_.trim.nonEmpty))
      matched.foreach { r =>
        require(!selected.contains(r.head), "Overlapping review: " + r.head)
        selected(r.head) = d
      }
    }
    val evidence = findings.filter(r => selected.contains(r.head)).groupBy(_(5)).toList.sortBy(_._1).flatMap { case(path, fs) =>
      val source = read(path)
      val tree = source.parse[Source].get
      fs.groupBy(_(8)).toList.sortBy(_._1).flatMap { case(ast, sameShape) =>
        val nodes = tree.collect { case t: Term.Select if t.structure.replace("\n", "\\n") == ast => t }
        val allSameShape = findings.filter(r => r(5) == path && r(8) == ast)
        require(nodes.size == allSameShape.map(_(1).toInt).sum, "AST evidence mismatch: " + path)
        nodes.map(t => List(path, hash(source), (t.pos.startLine + 1).toString,
          allSameShape.map(_.head).sorted.mkString(","), t.parent.getOrElse(t).syntax, t.structure).map(escape).mkString("\t"))
      }
    }
    val ledger = findings.map { r =>
      val d = selected.get(r.head)
      r ++ d.fold(List("candidate", "unassigned", "unreviewed", "No source decision in this bounded review"))(v => v.drop(4))
    }
    require(ledger.map(_.take(9)) == findings, "Never shrink or rewrite observed evidence")
    val summary = List("observation=" + policy.observation, "sources=" + fingerprint,
      "observation-sha256=" + hash(read(policy.observation + "/findings.tsv")),
      "decisions-sha256=" + hash(read(policy.decisions)), "verified-fixes=0", "test-policy=unmeasured",
      "independent-precision-recall=unestablished", "detector-refinement-rounds-consumed=0") ++
      ledger.groupBy(r => (r(2), r(9))).toList.sortBy(_._1).map { case((scope, kind), rs) =>
        s"$scope-$kind-anchors=${rs.size};occurrences=${rs.map(_(1).toInt).sum}"
      }
    Files.createDirectories(out)
    Files.write(out.resolve("findings.tsv"), (lines(policy.observation + "/findings.tsv").head +
      "\tclassification\ttreatment\tbatch\treason" :: ledger.map(_.mkString("\t"))).asJava)
    Files.write(out.resolve("evidence.tsv"), ("path\tsha256\tline\tpossible-anchor-ids\tsource\tast" :: evidence).asJava)
    Files.write(out.resolve("status.txt"), summary.asJava)
    Files.copy(Paths.get(policy.decisions), out.resolve("decisions.tsv"))
    Files.write(out.resolve("events.tsv"), ("id\tevent\tclassification\ttreatment\treason" ::
      ledger.filter(r => selected.contains(r.head)).map(r => List(r.head, "source-review", r(9), r(10), r(12)).mkString("\t"))).asJava)
    println(summary.mkString("\n"))
  }
}
