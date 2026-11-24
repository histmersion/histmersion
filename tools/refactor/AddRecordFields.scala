import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, Configured, generic}
import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets.UTF_8
import scala.collection.mutable

object AddRecordFields {
  case class Config(
      fields: Map[String, String] = Map.empty,
      parents: Map[String, String] = Map.empty,
      imports: List[String] = Nil,
      report: String = "refactor-audit/provenance/add-record-fields.tsv"
  )
  object Config {
    implicit val surface: generic.Surface[Config] = generic.deriveSurface[Config]
    implicit val decoder: ConfDecoder[Config] = generic.deriveDecoder(Config()).noTypos
  }

  val rows: mutable.LinkedHashSet[String] = mutable.LinkedHashSet.empty

  def flush(path: String): Unit = synchronized {
    val out = Paths.get(path)
    Option(out.getParent).foreach(Files.createDirectories(_))
    Files.write(out, (List("kind\tclass\twhat") ++ rows).mkString("", "\n", "\n").getBytes(UTF_8))
  }
}

class AddRecordFields(config: AddRecordFields.Config) extends SemanticRule("AddRecordFields") {
  import AddRecordFields._

  def this() = this(AddRecordFields.Config())

  override def withConfiguration(c: Configuration): Configured[Rule] =
    c.conf.getOrElse("AddRecordFields")(this.config).map(new AddRecordFields(_))

  private def importer(path: String): Importer = {
    implicit val dialect: Dialect = dialects.Scala3
    s"import $path".parse[Stat].get.asInstanceOf[Import].importers.head
  }

  override def fix(implicit doc: SemanticDocument): Patch = {
    val patches = doc.tree.collect {
      case c: Defn.Class if config.fields.contains(c.name.symbol.value) || config.parents.contains(c.name.symbol.value) =>
        val sym = c.name.symbol.value
        val params = c.ctor.paramClauses.headOption.toList.flatMap(_.values)
        val fieldPatch = config.fields.get(sym) match {
          case Some(text) if params.nonEmpty =>
            rows += List("fields", sym, text).mkString("\t")
            Patch.addRight(params.last, s", $text")
          case Some(text) =>
            rows += List("stop", sym, "no parameters").mkString("\t")
            Patch.empty
          case None => Patch.empty
        }
        val parentPatch = config.parents.get(sym) match {
          case Some(parent) if c.templ.inits.isEmpty =>
            rows += List("parent", sym, parent).mkString("\t")
            Patch.addRight(c.ctor, s" extends $parent")
          case Some(parent) =>
            rows += List("parent", sym, parent).mkString("\t")
            Patch.addRight(c.templ.inits.last, s" with $parent")
          case None => Patch.empty
        }
        fieldPatch + parentPatch
    }
    flush(config.report)
    if (patches.isEmpty) Patch.empty
    else patches.asPatch + config.imports.map(i => Patch.addGlobalImport(importer(i))).asPatch
  }
}
