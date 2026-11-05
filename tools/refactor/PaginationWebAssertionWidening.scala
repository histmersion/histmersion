import scalafix.v1._
import scala.meta._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class PaginationWebAssertionWidening extends SyntacticRule("PaginationWebAssertionWidening") {
  override def fix(implicit doc: SyntacticDocument): Patch = {
    val path = "modules/web/src/test/scala/com/histmersion/web/FilterTabSuite.scala"
    val text = Files.readString(Paths.get(path))
    require(doc.input.text == text)
    val frozen = Files.readAllLines(Paths.get("refactor-audit/models/pagination/web-tests-v2/sources.tsv")).asScala.drop(1).map(_.split("\t")).map(a => a(0) -> a(1)).toMap
    val hash = java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    require(hash == frozen(path))
    val matches = doc.tree.collect {
      case s: Term.Select if s.syntax == "_.shown.value" && s.parent.exists(_.isInstanceOf[Term.AnonymousFunction]) => s
    }
    require(matches.size == 2)
    matches.map(s => Patch.replaceTree(s,"(_.shown.value: Int)")).asPatch.atomic
  }
}
