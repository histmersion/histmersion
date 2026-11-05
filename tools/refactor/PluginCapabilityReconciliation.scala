import scalafix.v1._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class PluginCapabilityReconciliation extends SyntacticRule("PluginCapabilityReconciliation") {
  override def afterComplete(): Unit = {
    def rows(path: String) = Files.readAllLines(Paths.get(path)).asScala.drop(1).map(_.split("\t", -1).toList).toList
    def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    val before = rows("refactor-audit/capability-history/observations/2026-11-04T23-54-37.816223Z/findings.tsv")
    val confirmed = rows("refactor-audit/plugin-capabilities/confirmed.tsv")
    val root = Paths.get("").toAbsolutePath.normalize
    val now = rows("refactor-audit/plugin-capabilities/capabilities/findings.tsv").map { r =>
      val key = List(r(0), root.relativize(Paths.get(r(1))).toString, r(4), r(5), r(7)).mkString("\t")
      "CAP-" + hash(key)
    }.groupBy(identity).view.mapValues(_.size).toMap
    val old = before.map(r => r.head -> r(1).toInt).toMap
    val fixed = confirmed.map(r => r.head -> r(1).toInt).toMap
    require(fixed.size == 23 && fixed.values.sum == 25 && fixed.forall { case (id, count) => old.get(id).contains(count) })
    val moved = before.filter(r => r(4) == "clock" && r(5) == "modules/plugin-vaults/src/main/scala/com/histmersion/plugins/vaults/Subprocess.scala")
    require(moved.size == 1 && moved.head(1) == "3")
    val prior = moved.head
    val nextKey = List(prior(4), "modules/plugin-host/src/main/scala/com/histmersion/pluginhost/SystemProcesses.scala",
      "com/histmersion/pluginhost/SystemProcesses#Session#await().", prior(7), prior(8)).mkString("\t")
    val nextId = "CAP-" + hash(nextKey)
    require(now == (old -- fixed.keySet - prior.head).updated(nextId, 3), "Unexpected identity or multiplicity change")
    val dir = Paths.get("refactor-audit/plugin-capabilities")
    Files.write(dir.resolve("identity-reconciliation.txt"), List("PASS: exact pre-policy identity delta",
      "23 reviewed anchors / 25 confirmed occurrences removed; all other identities/multiplicities unchanged except the explicit clock relocation.",
      "clock-prior=" + prior.head, "clock-current=" + nextId, "clock-occurrences=3",
      "Clock relocation remains a candidate requiring explicit host-adapter boundary adjudication; this is not a passing regression gate.",
      "Original history and policy remain unchanged; final source assertions and integrated assessment remain outstanding.").asJava)
    println("PASS: 25 confirmed removals plus exactly one 3-occurrence clock relocation; final policy reconciliation outstanding")
  }
}
