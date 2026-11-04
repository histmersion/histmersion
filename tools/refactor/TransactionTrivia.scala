import scalafix.v1._
import scala.meta._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class TransactionTrivia extends SyntacticRule("TransactionTrivia") {
  override def fix(implicit doc: SyntacticDocument): Patch = {
    val name = doc.input match { case Input.File(p, _) => p.toNIO.toString; case Input.VirtualFile(p, _) => p }
    if (!name.endsWith("/GazetteerRepo.scala") && !name.endsWith("/QueryRepo.scala")) return Patch.empty
    val edits = scala.collection.mutable.ListBuffer.empty[(Int, Int, String)]
    doc.tokens.foreach {
      case t: Token.Comment if name.endsWith("/QueryRepo.scala") && t.text.contains("`SET LOCAL statement_timeout` here") =>
        edits += ((t.pos.start, t.pos.end, t.text.replace("`SET LOCAL statement_timeout` here", "`SET LOCAL statement_timeout` in the caller's Transactor scope")))
      case t: Token.Space =>
        val end = doc.input.text.indexOf('\n', t.pos.end) match { case -1 => doc.input.text.length; case n => n }
        if (doc.input.text.substring(t.pos.start, end).forall(c => c == ' ' || c == '\r')) edits += ((t.pos.start, t.pos.end, ""))
      case _ =>
    }
    if (edits.isEmpty) return Patch.empty
    val result = edits.toList.sortBy(-_._1).foldLeft(doc.input.text) { case (text, (start, end, replacement)) => text.patch(start, replacement, end - start) }
    implicit val dialect: Dialect = dialects.Scala3
    require(result.parse[Source].get.structure == doc.tree.structure, "Trivia patch changed the AST")
    def hash(s: String): String = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    val out = Paths.get("refactor-audit/transaction-repair/trivia")
    Files.createDirectories(out)
    Files.write(out.resolve(if(name.endsWith("/QueryRepo.scala")) "QueryRepo.txt" else "GazetteerRepo.txt"), List("AST_UNCHANGED", "before=" + hash(doc.input.text), "after=" + hash(result)).asJava)
    Patch.replaceTree(doc.tree, result)
  }
}
