import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, Configured, generic}
import java.nio.file.{Files, Paths, StandardOpenOption}
import java.nio.charset.StandardCharsets.UTF_8

/** G53 (`-language:strictEquality`): adds `derives CanEqual` to the definitions whose values are compared with `==`/`!=`.
  *
  *  - every `enum` (`allEnums`): its values are a closed set, so comparing two of them is always meaningful;
  *  - a case class, class or trait whose symbol is listed in `types` (fully qualified, `.`-separated, as the compiler
  *    names it in "Values of types X and X cannot be compared with == or !="): the records the code compares with a
  *    value of the same type. The list comes from the compile errors of the module, never from the text of the file.
  *
  * A `derives` clause that exists gets `, CanEqual` appended; otherwise ` derives CanEqual` follows the header (name,
  * type parameters, constructor, parents). A definition that already derives `CanEqual` is left alone, and so is every
  * comparison between two different types: those are decided by hand, never given a blanket instance.
  */
object DeriveCanEqual {
  case class Config(
      report: String = "",
      allEnums: Boolean = true,
      types: List[String] = Nil
  )
  object Config {
    implicit val surface: generic.Surface[Config] = generic.deriveSurface[Config]
    implicit val decoder: ConfDecoder[Config] = generic.deriveDecoder(Config()).noTypos
  }

  /** `com/histmersion/domain/Licence.Family#` as the compiler prints it: `com.histmersion.domain.Licence.Family`. */
  def dotted(sym: Symbol): String = sym.value.stripSuffix("#").stripSuffix(".").replace('/', '.').replace('#', '.')
}

class DeriveCanEqual(config: DeriveCanEqual.Config) extends SemanticRule("DeriveCanEqual") {
  import DeriveCanEqual._

  def this() = this(DeriveCanEqual.Config())

  private lazy val listed: Set[String] = config.types.toSet

  override def withConfiguration(c: Configuration): Configured[Rule] =
    c.conf.getOrElse("DeriveCanEqual")(this.config).map(new DeriveCanEqual(_))

  private def write(rows: List[String]): Unit = if (config.report.nonEmpty && rows.nonEmpty) this.synchronized {
    Option(Paths.get(config.report).getParent).foreach(Files.createDirectories(_))
    Files.write(
      Paths.get(config.report),
      rows.map(_ + "\n").mkString.getBytes(UTF_8),
      StandardOpenOption.CREATE,
      StandardOpenOption.APPEND
    )
  }

  private def nonEmpty(t: Tree): Boolean = t.pos != Position.None && t.pos.end > t.pos.start

  /** The last tree of the header: parents, else the constructor, else the type parameters, else the name. */
  private def headerEnd(name: Tree, tparams: Type.ParamClause, ctor: Ctor.Primary, templ: Template): Tree =
    templ.inits.lastOption
      .orElse(Some(ctor).filter(c => nonEmpty(c) && c.paramClauses.nonEmpty))
      .orElse(Some(tparams).filter(_.values.nonEmpty))
      .getOrElse(name)

  private def derive(
      kind: String,
      name: Type.Name,
      tparams: Type.ParamClause,
      ctor: Ctor.Primary,
      templ: Template
  )(implicit doc: SemanticDocument): Option[(Patch, String)] = {
    val already = templ.derives.exists {
      case Type.Name("CanEqual")                   => true
      case Type.Select(_, Type.Name("CanEqual")) => true
      case _                                       => false
    }
    if (already) None
    else {
      val patch = templ.derives.lastOption match {
        case Some(last) => Patch.addRight(last, ", CanEqual")
        case None       => Patch.addRight(headerEnd(name, tparams, ctor, templ), " derives CanEqual")
      }
      val path = doc.input match {
        case Input.VirtualFile(p, _) => p
        case Input.File(p, _)        => p.toString
        case other                   => other.syntax
      }
      val row = List(path, (name.pos.startLine + 1).toString, dotted(name.symbol), kind)
      Some((patch.atomic, row.mkString("\t")))
    }
  }

  override def fix(implicit doc: SemanticDocument): Patch = {
    val found = doc.tree.collect {
      case e: Defn.Enum if config.allEnums || listed(dotted(e.name.symbol)) =>
        derive("enum", e.name, e.tparamClause, e.ctor, e.templ)
      case c: Defn.Class if listed(dotted(c.name.symbol)) =>
        derive(if (c.mods.exists(_.is[Mod.Case])) "case-class" else "class", c.name, c.tparamClause, c.ctor, c.templ)
      case t: Defn.Trait if listed(dotted(t.name.symbol)) =>
        derive("trait", t.name, t.tparamClause, t.ctor, t.templ)
    }.flatten
    write(found.map(_._2))
    found.map(_._1).asPatch
  }
}
