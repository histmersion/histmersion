import scalafix.v1._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class SealTimingPolicy extends SyntacticRule("SealTimingPolicy") {
  override def afterComplete(): Unit = {
    def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    val out = Paths.get("refactor-audit/capability-history/corrections/monotonic-timing")
    require(!Files.exists(out.resolve("sealed.txt")))
    val old = Files.readString(out.resolve("after/capabilities.conf"))
    val next = Files.readString(Paths.get("tools/refactor/capabilities.conf"))
    require(old.replace("prefixes = [\"com/histmersion/util/time/SystemTimer#\", \"com/histmersion/util/time/ThreadPause#\"]", "symbols = [\"com.histmersion.util.time.SystemTimer\", \"com.histmersion.util.time.ThreadPause\"]") == next)
    require(Files.readAllLines(Paths.get("refactor-audit/monotonic/boundary-fixture-2/verification.txt")).asScala.contains("failures=0"))
    Files.writeString(out.resolve("rejected-prefix-capabilities.conf"),old)
    Files.writeString(out.resolve("after/capabilities.conf"),next)
    val names = List("CapabilityAccess.scala","capabilities.conf","capabilities-source.conf","capability-source-examples.conf")
    val policy = hash(names.map(n => n + "\t" + hash(Files.readString(out.resolve("after").resolve(n)))).mkString("\n"))
    val contract = Files.readAllLines(out.resolve("contract.txt")).asScala.toList
    Files.write(out.resolve("contract.txt"),contract.map(s => if(s.startsWith("detector-and-policy=")) "detector-and-policy=" + policy else s).asJava)
    val examples = Files.readString(out.resolve("before/capability-source-examples.conf"))
    require(examples == Files.readString(out.resolve("after/capability-source-examples.conf")))
    Files.writeString(out.resolve("before-examples.conf"),examples)
    Files.writeString(out.resolve("after-examples.conf"),examples)
    val events = Files.readAllLines(out.resolve("event.txt")).asScala.toList ++ List("before-examples-sha256=" + hash(examples),"after-examples-sha256=" + hash(examples),"Prefix prototype wrongly matched an adapter parameter; exact concrete type symbols retain both prohibited factory positives and the supplied-capability negatives.")
    Files.write(out.resolve("event.txt"),events.asJava)
    Files.writeString(out.resolve("sealed.txt"),"PASS: exact-type factory fixture; source assertions unchanged; failed prefix observation retained.\n")
  }
}
