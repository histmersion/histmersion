import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, Configured, generic}

object MoveImport {
  case class Config(moves: Map[String, String] = Map.empty)
  object Config {
    implicit val surface: generic.Surface[Config] = generic.deriveSurface[Config]
    implicit val decoder: ConfDecoder[Config] = generic.deriveDecoder(Config()).noTypos
  }
}

class MoveImport(config: MoveImport.Config) extends SyntacticRule("MoveImport") {
  def this() = this(MoveImport.Config())

  override def withConfiguration(c: Configuration): Configured[Rule] =
    c.conf.getOrElse("MoveImport")(MoveImport.Config()).map(new MoveImport(_))

  override def fix(implicit doc: SyntacticDocument): Patch =
    doc.tree.collect {
      case i: Importer if i.importees.size == 1 =>
        i.importees.head match {
          case n: Importee.Name if config.moves.contains(s"${i.ref.syntax}.${n.name.value}") =>
            Patch.replaceTree(i, config.moves(s"${i.ref.syntax}.${n.name.value}"))
          case _ => Patch.empty
        }
    }.asPatch
}
