import scalafix.v1._
import scala.meta._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class ClockTestConstruction extends SyntacticRule("ClockTestConstruction") {
  override def fix(implicit doc: SyntacticDocument): Patch = {
    val path = "modules/server/src/test/scala/com/histmersion/domain/automation/agents/RunTokensClockSuite.scala"
    val source = Files.readString(Paths.get(path)); require(doc.input.text == source)
    val hash = java.security.MessageDigest.getInstance("SHA-256").digest(source.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    val frozen = Files.readAllLines(Paths.get("refactor-audit/wall-clock/tests-1/sources.tsv")).asScala.drop(1).map(_.split("\t")).map(a => a(0) -> a(1)).toMap
    require(hash == frozen(path))
    val calls = doc.tree.collect { case a: Term.Apply if a.fun.syntax == "RunTokens" && a.argClause.values.isEmpty && a.parent.exists(_.syntax.contains("(using ")) => a }
    require(calls.size == 4)
    val report = Paths.get("refactor-audit/wall-clock/test-construction-before.scala")
    require(!Files.exists(report)); Files.writeString(report,source)
    calls.map(a => Patch.replaceTree(a,a.fun.syntax)).asPatch
  }
}
