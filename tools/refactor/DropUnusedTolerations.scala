import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, Configured, generic}

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

object DropUnusedTolerations {
  case class Config(sites: String = "", importLine: String = "import com.histmersion.lint.{tolerated, LintRule}")
  object Config {
    implicit val surface: generic.Surface[Config] = generic.deriveSurface[Config]
    implicit val decoder: ConfDecoder[Config] = generic.deriveDecoder(Config()).noTypos
  }

  def relative(p: String): String = {
    val here = Paths.get("").toAbsolutePath.normalize
    val abs = Paths.get(p).toAbsolutePath.normalize
    if (abs.startsWith(here)) here.relativize(abs).toString else p
  }
}

class DropUnusedTolerations(config: DropUnusedTolerations.Config) extends SyntacticRule("DropUnusedTolerations") {
  import DropUnusedTolerations._

  def this() = this(DropUnusedTolerations.Config())

  override def withConfiguration(c: Configuration): Configured[Rule] =
    c.conf.getOrElse("DropUnusedTolerations")(DropUnusedTolerations.Config()).map(new DropUnusedTolerations(_))

  private val sites: Set[(String, Int, Int)] =
    if (config.sites.isEmpty || !Files.isRegularFile(Paths.get(config.sites))) Set.empty
    else Files.readAllLines(Paths.get(config.sites), UTF_8).asScala.toList.map(_.split("\t")).collect {
      case Array(p, l, c) => (p, l.toInt, c.toInt)
    }.toSet

  override def fix(implicit doc: SyntacticDocument): Patch = {
    val path = relative(doc.input match {
      case Input.File(p, _) => p.toString
      case Input.VirtualFile(p, _) => p
      case other => other.toString
    })
    val tokens = doc.tree.tokens
    def wholeLines(start: Int, end: Int): Patch = {
      val first = tokens.indexWhere(_.start == start)
      val last = tokens.lastIndexWhere(_.end == end)
      val leading = tokens.take(first).reverse.takeWhile(t => t.is[Token.Whitespace] && !t.is[Token.LF]).toList
      val trailing = tokens.drop(last + 1).takeWhile(t => t.is[Token.Whitespace] && !t.is[Token.LF]).toList
      val newline = tokens.drop(last + 1 + trailing.size).headOption.filter(_.is[Token.LF]).toList
      Patch.removeTokens(leading.reverse ++ tokens.slice(first, last + 1) ++ trailing ++ newline)
    }
    val tolerations = doc.tree.collect { case a: Mod.Annot if a.init.tpe.syntax == "tolerated" => a }
    def listed(a: Mod.Annot): Boolean = sites.exists { case (p, l, c) =>
      (path == p || path.endsWith("/" + p)) && l == a.pos.startLine + 1 && c == a.pos.startColumn + 1
    }
    val dropped = tolerations.filter(listed)
    val removeImport =
      if (dropped.nonEmpty && dropped.size == tolerations.size)
        doc.tree.collect { case i: Import if i.syntax == config.importLine =>
          val after = tokens.dropWhile(_.start < i.pos.end).drop(1).headOption.filter(_.is[Token.LF])
          val before = tokens.takeWhile(_.end <= i.pos.start).reverse.take(2).toList
          val blankAround = after.nonEmpty && before.size == 2 && before.forall(_.is[Token.LF])
          wholeLines(i.pos.start, i.pos.end) + (if (blankAround) Patch.removeTokens(after.toList) else Patch.empty)
        }.asPatch
      else Patch.empty
    dropped.map(a => wholeLines(a.pos.start, a.pos.end)).asPatch + removeImport
  }
}
