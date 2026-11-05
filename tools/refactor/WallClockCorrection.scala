import scalafix.v1._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class WallClockCorrection extends SyntacticRule("WallClockCorrection") {
  override def afterComplete(): Unit = {
    def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    val base = Paths.get("refactor-audit/capability-history")
    val preceding = base.resolve("corrections/approval-codec-coordinate")
    val out = base.resolve("corrections/wall-clock"); require(!Files.exists(out))
    val before = Files.readString(preceding.resolve("after-examples.conf"))
    val after = Files.readString(Paths.get("tools/refactor/capability-source-examples.conf"))
    val old = "snippet = \"System.currentTimeMillis()\", rule = clock, count = 1, reason = \"Default arguments must also use injected clocks.\""
    val next = "snippet = \"wallClock.millis()\", rule = clock, count = 0, reason = \"Wall-clock batch: default uses the supplied Clock; global clock positives remain in clock-fixtures.scala.\""
    require(before.contains(old) && before.replace(old,next) == after)
    require(Files.readString(Paths.get("refactor-audit/wall-clock/tests-2/status.txt")).startsWith("PASS\n"))
    val source = Files.readAllLines(Paths.get("modules/server/src/main/scala/com/histmersion/domain/automation/agents/RunTokens.scala")).asScala
    require(source(19).contains("wallClock.millis()"))
    val names = List("CapabilityAccess.scala","capabilities.conf","capabilities-source.conf","capability-source-examples.conf")
    def policy(examples: String) = hash(names.map(n => n + "\t" + hash(if(n == names.last) examples else Files.readString(Paths.get("tools/refactor",n)))).mkString("\n"))
    val previous = Files.readAllLines(preceding.resolve("contract.txt")).asScala.toList
    require(previous.contains("detector-and-policy=" + policy(before)))
    Files.createDirectories(out)
    Files.writeString(out.resolve("before-examples.conf"),before)
    Files.writeString(out.resolve("after-examples.conf"),after)
    Files.write(out.resolve("previous-contract.txt"),previous.asJava)
    Files.write(out.resolve("contract.txt"),previous.map(s => if(s.startsWith("detector-and-policy=")) "detector-and-policy=" + policy(after) else s).asJava)
    Files.write(out.resolve("event.txt"),List("VERIFIED_SOURCE_REPAIR_ASSERTION", "RunTokens default now uses its injected Clock. Expected clock diagnostic changes from one to zero, with both versions retained.",
      "Global-clock detector positives remain unchanged in clock-fixtures.scala; no ban or permission changed.",
      "before-examples-sha256=" + hash(before),"after-examples-sha256=" + hash(after),"baseline-sha256=" + hash(Files.readString(base.resolve("baseline.tsv")))).asJava)
    println("Retained source assertion correction and unchanged detector policy")
  }
}
