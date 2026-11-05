import scalafix.v1._
import scala.meta._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class ImportCodecCoordinateCorrection extends SyntacticRule("ImportCodecCoordinateCorrection") {
  override def afterComplete(): Unit = {
    implicit val dialect: Dialect = dialects.Scala3
    def read(p: String) = Files.readString(Paths.get(p))
    def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    val base = Paths.get("refactor-audit/capability-history")
    val prior = base.resolve("corrections/monotonic-timing")
    val out = base.resolve("corrections/import-codec-coordinate")
    require(!Files.exists(out))
    val config = Paths.get("tools/refactor/capability-source-examples.conf")
    val before = Files.readString(config)
    require(before == Files.readString(prior.resolve("after-examples.conf")))
    val path = "modules/db/src/main/scala/com/histmersion/domain/library/sources/SourceRepo.scala"
    val previousSource = read("refactor-audit/import-registry/prerequisite-preview-2/before/" + path)
    val currentSource = read(path)
    def method(s: String) = s.parse[Source].get.collect { case d: Defn.Def if d.name.value == "putIn" => d }
    require(method(previousSource).size == 1 && method(currentSource).size == 1)
    require(method(previousSource).head.structure == method(currentSource).head.structure)
    require(method(previousSource).head.pos.startLine == 57 && method(currentSource).head.pos.startLine == 56)
    val old = "SourceRepo.scala\", line = 58, snippet = \"(using DbCon)\""
    val next = "SourceRepo.scala\", line = 57, snippet = \"(using DbCon)\""
    require(before.sliding(old.length).count(_ == old) == 1)
    val after = before.replace(old,next)
    val names = List("CapabilityAccess.scala","capabilities.conf","capabilities-source.conf","capability-source-examples.conf")
    def policy(examples: String) = hash(names.map(n => n + "\t" + hash(if(n == names.last) examples else read("tools/refactor/" + n))).mkString("\n"))
    val contract = Files.readAllLines(prior.resolve("contract.txt")).asScala.toList
    require(contract.contains("detector-and-policy=" + policy(before)))
    Files.createDirectories(out)
    Files.writeString(out.resolve("before-examples.conf"),before)
    Files.writeString(out.resolve("after-examples.conf"),after)
    Files.write(out.resolve("previous-contract.txt"),contract.asJava)
    Files.write(out.resolve("contract.txt"),contract.map(s => if(s.startsWith("detector-and-policy=")) "detector-and-policy=" + policy(after) else s).asJava)
    Files.writeString(out.resolve("method-ast.txt"),method(currentSource).head.structure)
    Files.write(out.resolve("event.txt"),List("SOURCE_COORDINATE_CORRECTION",
      "SourceRepo.putIn moved from line 58 to 57 after API status codec extraction. Its full method AST, expected zero and snippet are unchanged.",
      "No detector semantics, assertion count or original finding denominator changed.",
      "before-examples-sha256=" + hash(before), "after-examples-sha256=" + hash(after),
      "baseline-sha256=" + hash(Files.readString(base.resolve("baseline.tsv")))).asJava)
    Files.writeString(config,after)
    println("PASS: unchanged putIn AST; coordinate 58 to 57; original baseline retained")
  }
}
