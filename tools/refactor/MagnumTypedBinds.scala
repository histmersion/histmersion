import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, Configured, generic}

/** G58 (Magnum 2.0): a value spliced into `sql"..."` is bound through ITS codec, and Magnum 2 refuses a value without
  * one at compile time (1.3.1 bound it with `setObject` through `AnyCodec`). Iron's `.value` of a newtype is an
  * `IronType[A, C]`, which has no codec, so `${x.value}` in a `sql"..."` becomes `$x`: the newtype, bound through
  * `NewtypeColumns` (its base type's codec, so the same JDBC call). A file where that happens, or one named in
  * `importInto` (the files whose newtype splices the compiler reported without a codec in scope), gets
  * `import com.histmersion.util.database.NewtypeColumns.given` when it has none.
  *
  * Syntactic: only a path (`a`, `a.b`, `a.b.get`) followed by `.value`, only as a whole splice of a `sql` interpolation.
  * Whatever is not a newtype fails to compile afterwards and is fixed by hand.
  */
object MagnumTypedBinds {
  case class Config(importInto: List[String] = Nil)
  object Config {
    implicit val surface: generic.Surface[Config] = generic.deriveSurface[Config]
    implicit val decoder: ConfDecoder[Config] = generic.deriveDecoder(Config()).noTypos
  }
  val Import = "com.histmersion.util.database.NewtypeColumns.given"
}

class MagnumTypedBinds(config: MagnumTypedBinds.Config) extends SyntacticRule("MagnumTypedBinds") {
  import MagnumTypedBinds._

  def this() = this(MagnumTypedBinds.Config())

  override def withConfiguration(c: Configuration): Configured[Rule] =
    c.conf.getOrElse("MagnumTypedBinds")(this.config).map(new MagnumTypedBinds(_))

  private def isPath(t: Term): Boolean = t match {
    case _: Term.Name                       => true
    case Term.Select(q, _)                  => isPath(q)
    case Term.Apply.After_4_6_0(f, Term.ArgClause(args, _)) => isPath(f) && args.forall {
        case _: Lit => true
        case _      => false
      }
    case _ => false
  }

  override def fix(implicit doc: SyntacticDocument): Patch = {
    val path = doc.input match {
      case Input.VirtualFile(p, _) => p
      case Input.File(p, _)        => p.toString
      case other                   => other.syntax
    }
    val strips = doc.tree.collect {
      case i: Term.Interpolate if i.prefix.value == "sql" =>
        i.args.collect {
          case s @ Term.Select(q, Term.Name("value")) if isPath(q) => s
          case Term.Block(List(s @ Term.Select(q, Term.Name("value")))) if isPath(q) => s
        }
    }.flatten
    // `${x.value}` -> `${x}` (scalafmt's redundant-braces rewrite makes it `$x` where it can)
    val patches = strips.map(s => Patch.replaceTree(s, s.asInstanceOf[Term.Select].qual.syntax))
    val hasImport = doc.tree.collect {
      case i: Importer if i.syntax == Import || (i.ref.syntax == "com.histmersion.util.database.NewtypeColumns" &&
            i.importees.exists(_.is[Importee.Given])) => i
    }.nonEmpty
    val named = config.importInto.exists(f => path.endsWith(f))
    val addImport =
      if (!hasImport && (strips.nonEmpty || named)) Patch.addGlobalImport(importer"com.histmersion.util.database.NewtypeColumns.given")
      else Patch.empty
    patches.asPatch + addImport
  }
}
