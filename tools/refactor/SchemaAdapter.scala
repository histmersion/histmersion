import scalafix.v1._
import scala.meta._
import java.nio.file.{Files, Paths}

class SchemaAdapter extends SyntacticRule("SchemaAdapter") {
  private implicit val dialect: Dialect = dialects.Scala3
  private val template = Files.readString(Paths.get("tools/refactor/schema-fixture/adapter/Schema.scala")).parse[Source].get
  private val definition = template.collect { case c: Defn.Class if c.name.value == "Schema" => c }.head
  private val companion = template.collect { case o: Defn.Object if o.name.value == "Schema" => o }.head
  override def fix(implicit doc: SyntacticDocument): Patch = {
    val target = doc.tree.collect { case p: Pkg => p.ref.syntax }.contains("com.histmersion.api.json")
    if (!target) Patch.empty
    else (doc.tree.collect {
      case t: Defn.Type if t.name.value == "Schema" && t.body.syntax == "sttp.tapir.Schema[A]" => Patch.replaceTree(t, definition.syntax)
      case o: Defn.Object if o.name.value == "Schema" => Patch.replaceTree(o, companion.syntax)
    }.asPatch + Patch.addGlobalImport("scala.language.adhocExtensions".parse[Importer].get)).atomic
  }
}
