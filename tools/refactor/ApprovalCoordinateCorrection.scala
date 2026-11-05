import scalafix.v1._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class ApprovalCoordinateCorrection extends SyntacticRule("ApprovalCoordinateCorrection") {
  override def afterComplete(): Unit = {
    def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    val base = Paths.get("refactor-audit/capability-history")
    val preceding = base.resolve("corrections/plugin-capabilities")
    val out = base.resolve("corrections/approval-codec-coordinate")
    require(!Files.exists(out))
    val before = Files.readString(preceding.resolve("after-examples.conf"))
    val after = Files.readString(Paths.get("tools/refactor/capability-source-examples.conf"))
    val old = "SourceRepo.scala\", line = 55, snippet = \"(using DbCon)\""
    val next = "SourceRepo.scala\", line = 58, snippet = \"(using DbCon)\""
    require(before.contains(old) && before.replace(old,next) == after,"More than one coordinate changed")
    val source = Files.readAllLines(Paths.get("modules/db/src/main/scala/com/histmersion/domain/library/sources/SourceRepo.scala")).asScala
    require(source(57).contains("def putIn(") && source(57).contains("(using DbCon)"))
    val names = List("CapabilityAccess.scala","capabilities.conf","capabilities-source.conf","capability-source-examples.conf")
    def policy(examples: String) = hash(names.map(n => n + "\t" + hash(if(n == names.last) examples else Files.readString(Paths.get("tools/refactor",n)))).mkString("\n"))
    val previous = Files.readAllLines(preceding.resolve("contract.txt")).asScala.toList
    require(previous.contains("detector-and-policy=" + policy(before)))
    Files.createDirectories(out)
    Files.writeString(out.resolve("before-examples.conf"),before)
    Files.writeString(out.resolve("after-examples.conf"),after)
    Files.write(out.resolve("previous-contract.txt"),previous.asJava)
    Files.write(out.resolve("contract.txt"),previous.map(s => if(s.startsWith("detector-and-policy=")) "detector-and-policy=" + policy(after) else s).asJava)
    Files.write(out.resolve("event.txt"),List("SOURCE_COORDINATE_CORRECTION", "SourceRepo.putIn moved three lines after owner-local API status codec insertion; expected zero and snippet unchanged.",
      "No detector semantics, assertion counts or original finding denominator changed.", "before-examples-sha256=" + hash(before),"after-examples-sha256=" + hash(after),"baseline-sha256=" + hash(Files.readString(base.resolve("baseline.tsv")))).asJava)
    println("PASS: coordinate-only source assertion correction retained")
  }
}
