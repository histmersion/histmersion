import scalafix.v1._
import scala.meta._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class SourceStatusColumn extends SemanticRule("SourceStatusColumn") {
  override def fix(implicit doc: SemanticDocument): Patch = {
    val owners = doc.tree.collect { case o: Defn.Object if o.name.symbol.value == "com/histmersion/domain/library/sources/SourceRepo." => o }
    if(owners.isEmpty) return Patch.empty
    require(owners.size == 1)
    val owner = owners.head
    require(doc.tree.collect { case n: Name if n.symbol.value == "com/histmersion/api/library/Source." => n }.nonEmpty,
      "SourceRepo no longer uses the API Source model")
    require(!owner.templ.body.stats.exists(_.syntax.contains("DbCodec[Source.Status]")), "Status column already supplied")
    val dir = Paths.get("refactor-audit/approval-isolation/codec-prerequisite"); Files.createDirectories(dir)
    val input = doc.input.text
    val digest = java.security.MessageDigest.getInstance("SHA-256").digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    Files.write(dir.resolve("evidence.txt"),List("source=" + doc.input.syntax,"before-sha256=" + digest,
      "API Source.Status needs the same lowercase/default vocabulary semantics as the existing domain Source.Status codec.",
      "Original engine baseline: 30 failures before approval ownership application; unassessed not convertible to Status.").asJava)
    val first = owner.templ.body.stats.head
    Patch.addLeft(first,"private given DbCodec[Source.Status] =\n    com.histmersion.util.database.Vocab.codec(s => Source.Status.values.find(_.wire.equalsIgnoreCase(s)), _.wire, Source.Status.Unassessed)\n\n  ")
  }
}
