package com.histmersion
package server
package comments

final case class CommentedFile(path: String, language: SourceLanguage, text: String, exemption: Option[String]):
  import CommentedFile.*
  lazy val blocks: Vector[CommentBlock] = CommentBlock.runs(language, text, language.comments(text))

  private lazy val textLines: Vector[String] = text.linesIterator.toVector

  private lazy val printedLines: Vector[(Int, Int)] =
    language match
      case SourceLanguage.Shell =>
        selfPrint.findAllMatchIn(text).toVector.map { m =>
          val from = m.group(1).toInt
          val until = Option(m.group(2)).map(_.toInt).getOrElse {
            val pattern = m.group(3).r
            textLines.indices.drop(from - 1).find(i => pattern.findFirstIn(textLines(i)).isDefined).fold(from)(_ + 1)
          }
          from -> until
        }
      case SourceLanguage.Python if text.contains("__doc__") =>
        blocks.find(!_.directive).filter(_.kind == "doc").map(b => b.firstLine -> b.lastLine).toVector
      case _ => Vector.empty

  def selfPrinted(block: CommentBlock): Boolean =
    printedLines.exists((from, until) => block.firstLine <= until && block.lastLine >= from)

  def apiDoc(block: CommentBlock): Option[ApiDoc] =
    Option.when(
      language == SourceLanguage.Scala && block.kind == "doc" && path.contains("/src/main/") &&
        ApiDoc.PublicApiModules.contains(module)
    )(ApiDoc(block.text, ApiDoc.declarationAfter(text, block.end)))

  def allowedDoc(block: CommentBlock): Boolean = apiDoc(block).exists(_.allowed)

  def verdict(block: CommentBlock): String = apiDoc(block).fold(block.kind)(_.verdict)

  private def kept(block: CommentBlock): Boolean = block.directive || selfPrinted(block) || allowedDoc(block)

  def counted: Vector[CommentBlock] = if exemption.isDefined then Vector.empty else blocks.filterNot(kept)

  def exempt: Vector[CommentBlock] = if exemption.isDefined then blocks else blocks.filter(kept)

  def lineCount: Int = if text.isEmpty then 0 else text.linesIterator.size

  def module: String =
    path.split('/').toList match
      case "modules" :: group :: sub :: _ :: _ if groupedModules.contains(group) => s"$group/$sub"
      case "modules" :: module :: _ :: _                                         => module
      case top :: _                                                              => s"repo/$top"
      case Nil                                                                   => path

  def removing(chosen: Vector[CommentBlock]): CommentRemoval = CommentRemoval(text, chosen)

object CommentedFile:
  val groupedModules: Set[String] = Set("plugins", "plugin-fixtures")

  val selfPrint: scala.util.matching.Regex = """sed -n '(\d+),(?:(\d+)|/([^/']*)/)p' "\$0"""".r
