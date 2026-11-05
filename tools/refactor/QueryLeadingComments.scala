import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, Configured, generic}
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

object QueryLeadingComments {
  case class Policy(mode: String = "regression")
  object Policy {
    implicit val surface: generic.Surface[Policy] = generic.deriveSurface[Policy]
    implicit val decoder: ConfDecoder[Policy] = generic.deriveDecoder(Policy()).noTypos
  }
}
class QueryLeadingComments(val policy: QueryLeadingComments.Policy) extends SyntacticRule("QueryLeadingComments") {
  def this() = this(QueryLeadingComments.Policy())
  override def withConfiguration(c: Configuration): Configured[Rule] = c.conf.getOrElse("QueryLeadingComments")(QueryLeadingComments.Policy()).map(new QueryLeadingComments(_))
  private implicit val dialect: Dialect = dialects.Scala3
  private var seen = false
  override def fix(implicit doc: SyntacticDocument): Patch = {
    require(Set("regression","repair")(policy.mode))
    val target = if(policy.mode == "regression") "modules/api/src/test/scalajvm/com/histmersion/api/QuerySuite.scala" else "modules/api/src/main/scala/com/histmersion/api/library/Query.scala"
    val input = doc.input match { case Input.File(p,_) => p.toNIO; case Input.VirtualFile(p,_) => Paths.get(p) }
    if(Paths.get("").toAbsolutePath.relativize(input.toAbsolutePath).toString != target) return Patch.empty
    require(!seen); seen = true
    val frozen = Files.readAllLines(Paths.get("refactor-audit/models/pagination/build-v7/sources.tsv")).asScala.drop(1).map(_.split("\t")).map(a => a(0) -> a(1)).toMap
    val hash = java.security.MessageDigest.getInstance("SHA-256").digest(doc.input.text.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    require(hash == frozen(target),"Reviewed source changed")
    val report = Paths.get("refactor-audit/transaction-query-comments",policy.mode)
    require(!Files.exists(report)); Files.createDirectories(report)
    Files.writeString(report.resolve("before.scala"),doc.input.text)
    if(policy.mode == "regression") {
      val cases = """

  test("nested leading comments cannot hide transaction or session control"):
    val prefixes = List(
      "/* outer /* inner */ outer */ ",
      "/* /* */ */",
      "-- header\n/* outer /* middle /* inner */ middle */ outer */\n-- tail\n"
    )
    val commands = List("COMMIT", "ROLLBACK", "BEGIN", "END", "ABORT", "START TRANSACTION", "SAVEPOINT s", "RELEASE SAVEPOINT s", "SET TRANSACTION READ WRITE", "RESET ALL", "DISCARD ALL", "DO $$ BEGIN END $$", "CALL something()")
    prefixes.foreach { prefix =>
      commands.foreach { command =>
        val sql = prefix + command
        assert(Query.check(sql).isLeft, s"control statement accepted: $sql")
      }
    }

  test("nested leading comments preserve allowed queries and their keyword"):
    val sql = "/* outer /* inner */ outer */ select '/* literal */'"
    assertEquals(Query.leadingKeyword(sql), "SELECT")
    assertEquals(Query.check(sql), Right(sql))
    assertEquals(Query.leadingKeyword("/* /* */ */ -- next\nwith q as (select 1) select * from q"), "WITH")

  test("unclosed leading comments contain no executable keyword"):
    assertEquals(Query.leadingKeyword("/* outer /* inner */ COMMIT"), "")
    assertEquals(Query.leadingKeyword("/*"), "")
"""
      val cls = doc.tree.collect { case c: Defn.Class if c.name.value == "QuerySuite" => c }
      require(cls.size == 1)
      val last = cls.head.templ.body.stats.last
      Patch.addRight(last,cases)
    } else {
      val defs = doc.tree.collect { case d: Defn.Def if d.name.value == "leadingKeyword" => d }
      require(defs.size == 1)
      val replacement = """def leadingKeyword(statement: String): String =
  def afterBlockComment(text: String): String =
    var at = 2
    var depth = 1
    while at < text.length && depth > 0 do
      if text.startsWith("/*", at) then
        depth += 1
        at += 2
      else if text.startsWith("*/", at) then
        depth -= 1
        at += 2
      else at += 1
    if depth == 0 then text.substring(at).trim else ""
  var s = statement.trim
  var go = true
  while go do
    if s.startsWith("--") then s = s.dropWhile(_ != '\n').trim
    else if s.startsWith("/*") then s = afterBlockComment(s)
    else go = false
  s.takeWhile(c => c.isLetter).toUpperCase"""
      replacement.parse[Stat].get
      val rendered = replacement.linesIterator.toList.zipWithIndex.map { case(s,i) => if(i == 0) s else " " * defs.head.pos.startColumn + s }.mkString("\n")
      Files.writeString(report.resolve("before-ast.txt"),defs.head.structure)
      Files.writeString(report.resolve("replacement.scala"),replacement)
      Patch.replaceTree(defs.head,rendered).atomic
    }
  }
  override def afterComplete(): Unit = require(seen,"Target was not traversed")
}
