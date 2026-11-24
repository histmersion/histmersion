import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, Configured, generic}

object DisplayAsIs {
  case class Config(types: List[String] = Nil, instance: String = "given Display[T] = Display(_.value)",
      typeClass: String = "com.histmersion.Display")
  object Config {
    implicit val surface: generic.Surface[Config] = generic.deriveSurface[Config]
    implicit val decoder: ConfDecoder[Config] = generic.deriveDecoder(Config()).noTypos
  }
}

/** Gives each listed newtype (its companion's SemanticDB symbol) the display form that equals its raw value: one
  * `given` line in the companion, the owner's per-type decision written down where the type is.
  */
class DisplayAsIs(config: DisplayAsIs.Config) extends SemanticRule("DisplayAsIs") {
  def this() = this(DisplayAsIs.Config())

  override def withConfiguration(c: Configuration): Configured[Rule] =
    c.conf.getOrElse("DisplayAsIs")(DisplayAsIs.Config()).map(new DisplayAsIs(_))

  private val wanted = config.types.toSet

  override def fix(implicit doc: SemanticDocument): Patch = {
    val objects = doc.tree.collect {
      case o: Defn.Object if wanted(o.name.symbol.value.replaceAll("[^/.#]*\\$package\\.", "")) && !o.templ.stats.exists(_.syntax == config.instance) => o
    }
    if (objects.isEmpty) Patch.empty
    else {
      val added = objects.map { o =>
        val indent = " " * (o.pos.startColumn + 2)
        o.templ.stats.headOption match {
          case Some(first) => Patch.addLeft(first, s"${config.instance}\n\n$indent")
          case None => Patch.addRight(o, s":\n$indent${config.instance}")
        }
      }
      val packages = doc.tree.collect { case p: Pkg => p.ref.syntax }
      val name = config.typeClass.split('.').last
      val owner = config.typeClass.stripSuffix("." + name)
      val visible = packages.headOption.contains(owner) ||
        doc.tree.collect { case i: Importer => i.syntax }.exists(s => s == config.typeClass || s.startsWith(owner + ".{") && s.contains(name))
      (added :+ (if (visible) Patch.empty else Patch.addGlobalImport(dialects.Scala3(s"$owner.$name").parse[Importer].get)))
        .asPatch
    }
  }
}
