import scalafix.v1._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class TimingPolicyUpdate extends SyntacticRule("TimingPolicyUpdate") {
  override def afterComplete(): Unit = {
    def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    val base = Paths.get("refactor-audit/capability-history")
    val out = base.resolve("corrections/monotonic-timing"); require(!Files.exists(out))
    val names = List("CapabilityAccess.scala","capabilities.conf","capabilities-source.conf","capability-source-examples.conf")
    val before = names.map(n => n -> Files.readString(Paths.get("tools/refactor",n))).toMap
    def policy(s: Map[String,String]) = hash(names.map(n => n + "\t" + hash(s(n))).mkString("\n"))
    val previous = Files.readAllLines(base.resolve("corrections/wall-clock/contract.txt")).asScala.toList
    require(previous.contains("detector-and-policy=" + policy(before)))
    val ban = "    { id = timer-factory, prefixes = [\"com/histmersion/util/time/SystemTimer#\", \"com/histmersion/util/time/ThreadPause#\"], message = \"Receive Timer/Pause; concrete timing adapters belong to composition.\" },\n"
    val permissions = """    { id = monotonic-adapter, owner = "com/histmersion/util/time/SystemTimer#", capabilities = [clock], reason = "Explicit Timer adapter measures monotonic elapsed time; domain consumers receive Timer." },
    { id = deps-timing-composition, owner = "com/histmersion/server/Deps#", capabilities = [timer-factory], reason = "The object graph chooses timing adapters and allows supplied replacements." },
    { id = deps-timing-factory, owner = "com/histmersion/server/Deps.", capabilities = [timer-factory], reason = "The fixed object-graph factory selects adapters once and forwards supplied overrides." },
    { id = system-timing-composition, owner = "com/histmersion/server/SystemContext#", capabilities = [timer-factory], reason = "Composition selects Timer/Pause instances passed through the hot graph." },
"""
    require(!before("capabilities.conf").contains("timer-factory"))
    val changed = before("capabilities.conf").replace("  bans = [\n","  bans = [\n" + ban).replace("  permissions = [\n","  permissions = [\n" + permissions)
    val after = before.updated("capabilities.conf",changed)
    val oldRules = Files.readAllLines(Paths.get("refactor-audit/wall-clock/measurement-2/rules.txt")).asScala.toList
    Files.createDirectories(out)
    List("before" -> before,"after" -> after).foreach { case(dir,inputs) => Files.createDirectories(out.resolve(dir)); inputs.foreach { case(n,s) => Files.writeString(out.resolve(dir).resolve(n),s) } }
    Files.writeString(Paths.get("tools/refactor/capabilities.conf"),changed)
    Files.write(out.resolve("previous-contract.txt"),previous.asJava)
    Files.write(out.resolve("contract.txt"),previous.map(s =>
      if(s.startsWith("detector-and-policy=")) "detector-and-policy=" + policy(after)
      else if(s.startsWith("rules=")) "rules=" + hash((oldRules :+ "timer-factory").sorted.mkString("\n")) else s).asJava)
    Files.write(out.resolve("event.txt"),List("ADAPTER_BOUNDARY_AND_FACTORY_ENFORCEMENT", "No original finding or baseline removed. Two raw clock calls belong to the explicit SystemTimer adapter; domain factory access is newly forbidden.",
      "Factory selection permitted only in Deps and SystemContext; existing bans and permissions retained.","Independent detector acceptance not established; new boundary fixtures and project scan required.",
      "baseline-sha256=" + hash(Files.readString(base.resolve("baseline.tsv")))).asJava)
    println("Captured timing adapter boundary policy; enforcement verification remains required")
  }
}
