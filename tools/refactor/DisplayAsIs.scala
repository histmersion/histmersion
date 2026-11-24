import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, Configured, generic}

object DisplayAsIs {
  case class Form(newtype: String = "", instance: String = "", imports: List[String] = Nil)
  object Form {
    implicit val surface: generic.Surface[Form] = generic.deriveSurface[Form]
    implicit val decoder: ConfDecoder[Form] = generic.deriveDecoder(Form()).noTypos
  }

  case class Config(types: List[String] = Nil, forms: List[Form] = Nil,
      instance: String = "given Display[T] = Display(_.value)", typeClass: String = "com.histmersion.Display")
  object Config {
    implicit val surface: generic.Surface[Config] = generic.deriveSurface[Config]
    implicit val decoder: ConfDecoder[Config] = generic.deriveDecoder(Config()).noTypos
  }
}

class DisplayAsIs(config: DisplayAsIs.Config) extends SemanticRule("DisplayAsIs") {
  import DisplayAsIs._

  def this() = this(DisplayAsIs.Config())

  override def withConfiguration(c: Configuration): Configured[Rule] =
    c.conf.getOrElse("DisplayAsIs")(DisplayAsIs.Config()).map(new DisplayAsIs(_))

  private val wanted: Map[String, Form] =
    config.types.map(t => t -> Form(t, config.instance)).toMap ++ config.forms.map(f => f.newtype -> f).toMap

  override def fix(implicit doc: SemanticDocument): Patch = {
    val name = config.typeClass.split('.').last
    val objects = doc.tree.collect {
      case o: Defn.Object if wanted.contains(o.name.symbol.value.replaceAll("[^/.#]*\\$package\\.", "")) &&
          !o.templ.stats.exists {
            case g: Defn.Given => g.syntax.contains(name)
            case g: Defn.GivenAlias => g.syntax.contains(name)
            case _ => false
          } => o -> wanted(o.name.symbol.value.replaceAll("[^/.#]*\\$package\\.", ""))
    }
    if (objects.isEmpty) Patch.empty
    else {
      val added = objects.map { case (o, form) =>
        val indent = " " * (o.pos.startColumn + 2)
        o.templ.stats.headOption match {
          case Some(first) => Patch.addLeft(first, s"${form.instance}\n\n$indent")
          case None => Patch.addRight(o, s":\n$indent${form.instance}")
        }
      }
      val packages = doc.tree.collect { case p: Pkg => p.ref.syntax }
      val imported = doc.tree.collect { case i: Importer => i.syntax }
      def visible(full: String): Boolean = {
        val simple = full.split('.').last
        val owner = full.stripSuffix("." + simple)
        packages.headOption.contains(owner) || packages.mkString(".") == owner ||
          imported.exists(s => s == full || s.startsWith(owner + ".{") && s.contains(simple) || s == owner + ".*")
      }
      val needed = (config.typeClass :: objects.flatMap(_._2.imports)).distinct.filterNot(visible)
      (added ++ needed.map(i => Patch.addGlobalImport(dialects.Scala3(i).parse[Importer].get))).asPatch
    }
  }
}
