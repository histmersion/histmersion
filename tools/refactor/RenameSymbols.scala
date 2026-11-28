import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, Configured, generic}

object RenameSymbols {
  case class Config(
      renames: Map[String, String] = Map.empty,
      imports: Map[String, String] = Map.empty,
      inPlace: Boolean = false
  )
  object Config {
    implicit val surface: generic.Surface[Config] = generic.deriveSurface[Config]
    implicit val decoder: ConfDecoder[Config] = generic.deriveDecoder(Config()).noTypos
  }
}

class RenameSymbols(config: RenameSymbols.Config) extends SemanticRule("RenameSymbols") {
  def this() = this(RenameSymbols.Config())

  override def withConfiguration(c: Configuration): Configured[Rule] =
    c.conf.getOrElse("RenameSymbols")(RenameSymbols.Config()).map(new RenameSymbols(_))

  private def definition(n: Name): Boolean = n.parent.exists {
    case d: Defn.Class  => d.name eq n
    case d: Defn.Object => d.name eq n
    case d: Defn.Type   => d.name eq n
    case d: Defn.Trait  => d.name eq n
    case d: Defn.Enum   => d.name eq n
    case _              => false
  }

  private def inImporter(t: Tree): Boolean = t.parent match {
    case Some(_: Importer) => true
    case Some(p)           => inImporter(p)
    case None              => false
  }

  private def importerOf(path: String): Importer = {
    implicit val dialect: Dialect = dialects.Scala3
    s"import $path".parse[Stat].get.asInstanceOf[Import].importers.head
  }

  override def fix(implicit doc: SemanticDocument): Patch =
    if (config.inPlace) doc.tree.collect {
      case n: Name if config.renames.contains(n.symbol.value) => Patch.replaceTree(n, config.renames(n.symbol.value))
    }.asPatch
    else doc.tree.collect {
      case i: Importee.Name if config.renames.contains(i.name.symbol.value) =>
        Patch.removeImportee(i) + config.imports.get(i.name.symbol.value).fold(Patch.empty)(p =>
          Patch.addGlobalImport(importerOf(p)))
      case n: Name if !definition(n) && !inImporter(n) && config.renames.contains(n.symbol.value) =>
        Patch.replaceTree(n, config.renames(n.symbol.value))
    }.asPatch
}
