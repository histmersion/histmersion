import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, Configured, generic}

import java.nio.file.{Files, Path, Paths}
import scala.jdk.CollectionConverters._

object MovePackage {
  case class Move(from: String = "", to: String = "")
  object Move {
    implicit val surface: generic.Surface[Move] = generic.deriveSurface[Move]
    implicit val decoder: ConfDecoder[Move] = generic.deriveDecoder(Move()).noTypos
  }
  case class Config(moves: List[Move] = Nil, report: String = "")
  object Config {
    implicit val surface: generic.Surface[Config] = generic.deriveSurface[Config]
    implicit val decoder: ConfDecoder[Config] = generic.deriveDecoder(Config()).noTypos
  }

  def symbolOf(pkg: String): String = pkg.replace('.', '/') + "/"

  def renamed(text: String, m: Move): Option[String] = {
    val from = m.from.split('.').toList
    val to = m.to.split('.').toList
    val segs = text.split('.').toList
    val drop = from.length - segs.length
    val common = from.zip(to).takeWhile { case (a, b) => a == b }.length
    if (drop < 0 || drop > common || from.drop(drop) != segs) None else Some(to.drop(drop).mkString("."))
  }
}

class MovePackage(config: MovePackage.Config) extends SemanticRule("MovePackage") {
  import MovePackage._

  def this() = this(MovePackage.Config())

  override def withConfiguration(c: Configuration): Configured[Rule] =
    c.conf.getOrElse("MovePackage")(MovePackage.Config()).map(new MovePackage(_))

  private val pending = new java.util.concurrent.ConcurrentLinkedQueue[(Path, Path)]()

  private def outermost(t: Tree): Boolean = t.parent match {
    case Some(s: Term.Select) => !(s.name eq t)
    case _ => true
  }

  override def fix(implicit doc: SemanticDocument): Patch = {
    val pkgs = doc.tree.collect { case p: Pkg => p }
    val full = pkgs.map(_.ref.syntax).mkString(".")
    val clause = config.moves.find(_.from == full).flatMap { m =>
      pkgs.lastOption.map { last =>
        val outer = pkgs.init.map(_.ref.syntax).mkString(".")
        val inner = if (outer.isEmpty) m.to else m.to.stripPrefix(outer + ".")
        val file = doc.input match {
          case Input.File(p, _) => Some(p.toNIO)
          case Input.VirtualFile(p, _) => Some(Paths.get(p))
          case _ => None
        }
        file.foreach { f =>
          val abs = f.toAbsolutePath.toString
          val fromDir = "/" + m.from.replace('.', '/') + "/"
          if (abs.contains(fromDir)) pending.add(f -> Paths.get(abs.replace(fromDir, "/" + m.to.replace('.', '/') + "/")))
        }
        Patch.replaceTree(last.ref, inner)
      }
    }.getOrElse(Patch.empty)
    val clauses = pkgs.map(_.ref).toSet
    val refs = doc.tree.collect {
      case t @ (_: Term.Select | _: Term.Name) if outermost(t) && !clauses.exists(c => c.pos.start <= t.pos.start && c.pos.end >= t.pos.end) =>
        config.moves.find(m => t.symbol.value == symbolOf(m.from)).flatMap(m => renamed(t.syntax, m)).map(Patch.replaceTree(t, _))
    }.flatten
    clause + refs.asPatch
  }

  override def afterComplete(): Unit = if (config.report.nonEmpty) {
    val root = Paths.get("").toAbsolutePath
    val rows = pending.asScala.toList.map { case (from, to) =>
      s"${root.relativize(from.toAbsolutePath)}\t${root.relativize(to.toAbsolutePath)}"
    }.sorted
    Files.write(Paths.get(config.report), rows.mkString("", "\n", "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8))
  }
}
