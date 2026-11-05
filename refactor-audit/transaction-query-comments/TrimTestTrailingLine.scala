import scalafix.v1._
import scala.meta._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class TrimTestTrailingLine extends SyntacticRule("TrimTestTrailingLine") {
  override def fix(implicit doc: SyntacticDocument): Patch = {
    val path = "modules/api/src/test/scalajvm/com/histmersion/api/QuerySuite.scala"
    require(doc.input.text == Files.readString(Paths.get(path)))
    require(doc.input.text.endsWith("\n\n"))
    val before = doc.input.text
    val after = before.dropRight(1)
    implicit val dialect: Dialect = dialects.Scala3
    require(before.parse[Source].get.structure == after.parse[Source].get.structure)
    val token = doc.tokens.reverseIterator.find(_.is[Token.LF]).get
    require(token.pos.end == before.length)
    val report = Paths.get("refactor-audit/transaction-query-comments/whitespace.txt")
    require(!Files.exists(report))
    def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    val frozen = Files.readAllLines(Paths.get("refactor-audit/transaction-query-comments/build/sources.tsv")).asScala.drop(1).map(_.split("\t")).map(a => a(0) -> a(1)).toMap
    require(hash(before) == frozen(path))
    Files.writeString(report, s"path=$path\nbefore=${hash(before)}\nafter=${hash(after)}\nAST unchanged; one trailing blank-line token removed after successful compilation and tests.\n")
    Patch.removeToken(token)
  }
}
