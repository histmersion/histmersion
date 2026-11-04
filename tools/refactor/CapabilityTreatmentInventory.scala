import scalafix.v1._
import scala.meta._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class CapabilityTreatmentInventory extends SyntacticRule("CapabilityTreatmentInventory") {
  override def afterComplete(): Unit = {
    val ledger = Paths.get("refactor-audit/capability-history/observations/2026-11-04T23-19-19.607608Z")
    def read(path: java.nio.file.Path): List[String] = Files.readAllLines(path).asScala.toList
    def hash(s: String): String = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    val stream = Files.walk(Paths.get("modules"))
    val sources = try stream.iterator.asScala.filter(p => Files.isRegularFile(p) && p.toString.endsWith(".scala") && (p.toString.contains("/src/main/") || p.toString.contains("/src/test/"))).toList finally stream.close()
    val fingerprint = hash(sources.sortBy(_.toString).map(p => p.toString + "\t" + hash(Files.readString(p))).mkString("\n"))
    require(read(ledger.resolve("sources.txt")) == List(fingerprint), "Triage source evidence is stale")
    val rows = read(ledger.resolve("findings.tsv")).drop(1).map(_.split("\t", -1).toList)
    require(rows.forall(_.size == 9))
    val grouped = rows.groupBy(r => (r(4), r(5))).toList.sortBy(_._1)
    val out = Paths.get("refactor-audit/capability-treatment")
    Files.createDirectories(out)
    val groups = grouped.map { case ((rule, path), items) => List(rule, path, items.map(_(1).toInt).sum.toString, items.map(_(0)).sorted.mkString(",")).mkString("\t") }
    Files.write(out.resolve("groups.tsv"), ("rule\tpath\toccurrences\tanchor-ids" :: groups).asJava)
    Files.write(out.resolve("sources.txt"), List(fingerprint).asJava)
    val reviews = read(Paths.get("tools/refactor/capability-treatment-review.tsv")).drop(1).map(_.split("\t", -1).toList)
    require(reviews.forall(_.size == 7), "Invalid treatment review schema")
    val selected = scala.collection.mutable.Map.empty[String, List[String]]
    reviews.foreach { review =>
      val matched = rows.filter(r => (review.head == "id" && r.head == review(1)) ||
        (review.head == "group" && r(4) + ":" + r(5) == review(1) && !selected.contains(r.head)))
      require(matched.nonEmpty && matched.map(_(1).toInt).sum == review(4).toInt, "Review count changed: " + review(1))
      require(Set("confirmed", "non-violation", "unresolved")(review(2)))
      require(review(2) != "confirmed" || Set("bulk-rewrite", "targeted-rewrite", "manual/design")(review(3)))
      require(review(5).nonEmpty && review(6).nonEmpty)
      matched.foreach { r =>
        require(!selected.contains(r.head), "Overlapping review decision: " + r.head)
        selected(r.head) = review
      }
    }
    val decisions = rows.map { r =>
      val review = selected.get(r.head)
      List(r.head, r(1), r(2), r(3), r(4), r(5), review.fold("candidate")(_(2)),
        review.fold("unassigned")(_(3)), review.fold("unreviewed")(_(5)), review.fold("No semantic adjudication yet")(_(6))).mkString("\t")
    }
    val header = "anchor-id\toccurrences\tscope\tidentity-confidence\trule\tpath\tclassification\ttreatment\tbatch\tevidence"
    Files.write(out.resolve("findings.tsv"), (header :: decisions).asJava)
    val summary = List("scope=production", "anchors=" + rows.size, "occurrences=" + rows.map(_(1).toInt).sum,
      "test-policy=unmeasured", "sample-precision-and-recall=not-established", "accepted-exceptions=0",
      "unresolved-sql-expressions=2; separate from matched anchors", "additional-confirmed-detector-gaps=1") ++
      List("confirmed", "non-violation", "unresolved", "candidate").map { kind =>
        val matching = rows.filter(r => selected.get(r.head).fold("candidate")(_(2)) == kind)
        s"$kind-anchors=${matching.size};occurrences=${matching.map(_(1).toInt).sum}"
      }
    Files.write(out.resolve("summary.txt"), summary.asJava)
    val event = out.resolve("review-1")
    val evidence = List("sources=" + fingerprint, "review=" + hash(Files.readString(Paths.get("tools/refactor/capability-treatment-review.tsv"))),
      "baseline-observation=" + ledger.toString)
    if(Files.exists(event)) require(read(event.resolve("evidence.txt")) == evidence, "Never replace review history")
    else {
      Files.createDirectories(event)
      Files.write(event.resolve("evidence.txt"), evidence.asJava)
      Files.write(event.resolve("findings.tsv"), (header :: decisions).asJava)
      Files.copy(Paths.get("tools/refactor/capability-treatment-review.tsv"), event.resolve("decisions.tsv"))
    }
    println(summary.mkString("\n"))
    implicit val dialect: Dialect = dialects.Scala3
    val gapPath = "modules/server/src/main/scala/com/histmersion/domain/automation/agents/Prompts.scala"
    val gapSource = Files.readString(Paths.get(gapPath))
    val gaps = gapSource.parse[Source].get.collect { case select: Term.Select if select.syntax == "getClass.getClassLoader.getResourceAsStream" => select }
    require(gaps.size == 1, "Reviewed resource-access evidence changed")
    val gap = gaps.head
    val gapRows = List("id\tscope\tclassification\ttreatment\tbatch\tpath\tline\tsymbol\tast\tevidence",
      List("CAP-GAP-0001", "production", "confirmed", "targeted-rewrite", "agent-storage", gapPath,
        (gap.pos.startLine + 1).toString, "java/lang/ClassLoader#getResourceAsStream().", gap.structure.replace("\n", "\\n"),
        "Prompts.load opens bundled resources directly; inject a resource reader with the override Store. Existing capability rules do not cover classloader access.").mkString("\t"))
    Files.write(out.resolve("detector-gaps.tsv"), gapRows.asJava)
    val gapHistory = event.resolve("detector-gaps.tsv")
    if(Files.exists(gapHistory)) require(read(gapHistory) == gapRows, "Never overwrite confirmed gap history")
    else Files.write(gapHistory, gapRows.asJava)
  }
}
