import scalafix.v1._
import scala.meta._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class DomainClockTreatment extends SyntacticRule("DomainClockTreatment") {
  override def afterComplete(): Unit = {
    implicit val dialect: Dialect = dialects.Scala3
    def read(p: String) = Files.readAllLines(Paths.get(p)).asScala.toList
    def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    def escape(s: String) = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")
    val observation = "refactor-audit/capability-history/observations/2026-11-05T02-54-10.212274Z/findings.tsv"
    val rows = read(observation).tail.map(_.split("\t", -1).toList)
    require(rows.size == 125 && rows.map(_(1).toInt).sum == 147)
    val frozen = read("refactor-audit/models/pagination/build-v7/sources.tsv").tail.map(_.split("\t")).map(a => a(0) -> a(1)).toMap
    val selected = rows.filter(r => r(4) == "clock" && r(5).contains("/com/histmersion/domain/"))
    require(selected.nonEmpty)
    val evidence = scala.collection.mutable.ListBuffer.empty[String]
    selected.groupBy(_(5)).toList.sortBy(_._1).foreach { case (path, findings) =>
      val source = Files.readString(Paths.get(path))
      require(hash(source) == frozen(path), "Source changed since observation: " + path)
      val tree = source.parse[Source].get
      findings.groupBy(_(8)).foreach { case (ast, sameShape) =>
        val matches = tree.collect { case t: Term.Select if t.structure.replace("\n", "\\n") == ast => t }
        require(matches.size == sameShape.map(_(1).toInt).sum, "AST occurrence count changed: " + path)
        matches.foreach { t =>
          val context = t.parent.getOrElse(t)
          evidence += List(path, hash(source), (t.pos.startLine + 1).toString, sameShape.map(_.head).sorted.mkString(","), context.syntax, context.structure).map(escape).mkString("\t")
        }
      }
    }
    val decisions = selected.sortBy(_.head).map { r =>
      val monotonic = r(7) == "java/lang/System#nanoTime()."
      val deadline = r(5).endsWith("/AutopilotService.scala")
      val batch = if(monotonic) "monotonic-clock" else if(deadline) "polling-deadline" else "wall-clock"
      val treatment = if(monotonic || deadline) "manual/design" else "bulk-rewrite"
      val reason = if(monotonic) "Direct monotonic timer in domain code; supply a monotonic capability and preserve nanosecond elapsed/deadline semantics. Never substitute java.time.Clock."
        else if(deadline) "Direct wall-clock polling deadline; design supplied timing and waiting together, retaining bounded wait and cancellation behavior."
        else "Direct global wall-clock read or global default in domain code; thread the supplied Clock through definitions and callers. Preserve UTC date conversion and explicit time overrides."
      (r.take(8) ++ List("confirmed", treatment, batch, "outstanding", reason)).mkString("\t")
    }
    val out = Paths.get("refactor-audit/capability-treatment/review-2")
    require(!Files.exists(out), "Never overwrite review history")
    Files.createDirectories(out)
    Files.write(out.resolve("findings.tsv"), ("id\tcount\tscope\tidentity-confidence\trule\tpath\towner\tsymbol\tclassification\ttreatment\tbatch\tstatus\treason" :: decisions).asJava)
    Files.write(out.resolve("evidence.tsv"), ("path\tsource-sha256\tline\tpossible-anchor-ids\tsource\tast" :: evidence.sorted.toList).asJava)
    val events = selected.sortBy(_.head).map(r => List("CLOCK-REVIEW-" + r.head, r.head, "candidate", "confirmed", "Existing violation newly adjudicated; not a new code regression").mkString("\t"))
    Files.write(out.resolve("events.tsv"), ("event-id\tfinding-id\tfrom\tto\treason" :: events).asJava)
    Files.copy(Paths.get(observation), out.resolve("observation.tsv"))
    val summary = List("observation=" + observation, "observation-sha256=" + hash(Files.readString(Paths.get(observation))),
      "confirmed-production-anchors=" + selected.size, "confirmed-production-occurrences=" + selected.map(_(1).toInt).sum,
      "production-files=" + selected.map(_(5)).distinct.size,
      "other-observation-anchors-retained=" + (rows.size - selected.size),
      "verified-fixes=0", "new-exceptions=0", "test-policy=unmeasured", "independent-precision-recall=unestablished", "detector-refinement-rounds-consumed=0") ++
      decisions.groupBy(_.split("\t", -1)(10)).toList.sortBy(_._1).map { case (batch, rs) => batch + "-occurrences=" + rs.map(_.split("\t", -1)(1).toInt).sum }
    Files.write(out.resolve("status.txt"), summary.asJava)
    println(summary.mkString("\n"))
  }
}
