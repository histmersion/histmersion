//> using scala 3.10.1
//> using file ../../modules/server/src/test/scala/com/histmersion/server/comments/SourceLanguage.scala
//> using file ../../modules/server/src/test/scala/com/histmersion/server/comments/SourceComment.scala
//> using file ../../modules/server/src/test/scala/com/histmersion/server/comments/CommentLexer.scala
//> using file ../../modules/server/src/test/scala/com/histmersion/server/comments/CommentBlock.scala
//> using file ../../modules/server/src/test/scala/com/histmersion/server/comments/CommentedFile.scala
//> using file ../../modules/server/src/test/scala/com/histmersion/server/comments/CommentScope.scala
//> using file ../../modules/server/src/test/scala/com/histmersion/server/comments/CommentRemoval.scala
//> using file ../../modules/server/src/test/scala/com/histmersion/server/comments/ApiDoc.scala
//> using file ../../modules/lint/src/main/scala/com/histmersion/lint/tolerated.scala
//> using file ../../modules/lint/src/main/scala/com/histmersion/lint/LintRule.scala

import com.histmersion.server.comments.*
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

final case class Decision(path: String, lines: String, language: String, category: String, reason: String)

val categories: Set[String] = Set("tautology", "stale", "excuse", "decision", "keep")

val removedCategories: Set[String] = categories - "keep"

val tautologyReason: String = "restates what the names, types and code beside it already say"

val keptDocReason: String = "public-API Scaladoc that explains the contract beyond its signature"

def reviewable(f: CommentedFile): Vector[CommentBlock] = f.blocks.filter(b => f.counted.contains(b) || f.allowedDoc(b))

def squash(comment: String): String =
  comment.linesIterator
    .map(
      _.trim.stripPrefix("/**").stripPrefix("/*").stripSuffix("*/").stripPrefix("//").stripPrefix("*").stripPrefix("#")
    )
    .map(_.trim)
    .filter(_.nonEmpty)
    .mkString(" ")

def nextCode(file: CommentedFile, block: CommentBlock): String =
  file.text.substring(block.end).linesIterator.map(_.trim).find(_.nonEmpty).getOrElse("").take(90)

def under(prefixes: Seq[String])(file: CommentedFile): Boolean = prefixes.exists(p => file.path.startsWith(p))

def show(root: Path, prefixes: Seq[String], kept: Boolean): Unit =
  CommentScope.default.files(root).filter(under(prefixes)).foreach { f =>
    val shown = if kept then f.blocks.filter(f.allowedDoc) else f.counted
    if shown.nonEmpty then println(s"== ${f.path}")
    shown.foreach(b => println(s"${b.lineRange} [${f.verdict(b)}] ${squash(b.text)} => ${nextCode(f, b)}"))
  }

def trimTags(root: Path, prefixes: Seq[String]): Unit =
  var trimmed = 0
  CommentScope.default.files(root).filter(under(prefixes)).foreach { f =>
    val edits = f.blocks.flatMap { b =>
      f.apiDoc(b)
        .filter(d => d.public && d.excuse.isEmpty && d.restatedTagLines.nonEmpty && !d.restatedTagLines.contains(0))
        .map(d => b -> d.restatedTagLines.toSet)
    }
    if edits.nonEmpty then
      val result = edits.sortBy((b, _) => -b.start).foldLeft(f.text) { case (text, (b, drop)) =>
        val lines = b.text.linesIterator.toList
        val indent = lines.lift(1).map(_.takeWhile(_ == ' ')).getOrElse(" ")
        val kept = lines.indices.filterNot(drop).map(lines).toList
        val closed = if drop.exists(i => lines(i).contains("*/")) then kept :+ s"$indent*/" else kept
        text.substring(0, b.start) + closed.mkString("\n") + text.substring(b.end)
      }
      Files.write(root.resolve(f.path), result.getBytes(UTF_8))
      trimmed += edits.size
  }
  println(s"trimmed the restating tags of $trimmed docs")

def decide(root: Path, overridesFile: Path, out: Path, prefixes: Seq[String]): Unit =
  val overrides = readDecisions(overridesFile).map(d => (d.path, d.lines) -> d).toMap
  val files = CommentScope.default.files(root).filter(under(prefixes))
  val rows = files.flatMap { f =>
    reviewable(f).map { b =>
      val default =
        if f.allowedDoc(b) then Decision(f.path, b.lineRange, f.language.label, "keep", keptDocReason)
        else Decision(f.path, b.lineRange, f.language.label, "tautology", tautologyReason)
      overrides.getOrElse((f.path, b.lineRange), default)
    }
  }
  val known = files.flatMap(f => reviewable(f).map(b => (f.path, b.lineRange))).toSet
  val unmatched = overrides.keySet.filter((p, _) => prefixes.exists(p.startsWith)).diff(known)
  if unmatched.nonEmpty then sys.error(s"overrides that match no block: ${unmatched.toVector.sorted.mkString(", ")}")
  val existing =
    if Files.isRegularFile(out) then Files.readAllLines(out, UTF_8).asScala.toVector.filterNot(_.isBlank)
    else Vector.empty
  val kept = existing.filterNot(l => prefixes.exists(p => l.startsWith(p)) && !l.startsWith("path\t"))
  val header = "path\tlines\tlanguage\tcategory\treason"
  val body = (kept.filterNot(_.startsWith("path\t")) ++
    rows.map(d => List(d.path, d.lines, d.language, d.category, d.reason).mkString("\t"))).sorted
  Files.write(out, (header +: body).asJava, UTF_8)
  val tally = rows.groupMapReduce(_.category)(_ => 1)(_ + _)
  println(s"${rows.size} decisions under ${prefixes.mkString(" ")}: ${tally.toVector.sorted.mkString(", ")}")

def readDecisions(file: Path): Vector[Decision] =
  Files
    .readAllLines(file, UTF_8)
    .asScala
    .toVector
    .filterNot(l => l.isBlank || l.startsWith("path\t"))
    .map { l =>
      l.split("\t", 5).toList match
        case path :: lines :: language :: category :: reason :: Nil if categories(category) =>
          Decision(path, lines, language, category, reason)
        case _ => sys.error(s"malformed decision (path, lines, language, category, reason): $l")
    }

def inventory(root: Path, out: Path): Unit =
  val files = CommentScope.default.files(root)
  val rows = files.flatMap { f =>
    f.blocks.map { b =>
      val status = f.exemption
        .map(why => s"exempt:$why")
        .getOrElse(
          if b.directive then "directive"
          else if f.selfPrinted(b) then "help-text"
          else if f.allowedDoc(b) then "api-doc"
          else "counted"
        )
      List(f.path, f.module, f.language.label, b.lineRange, b.lines.toString, f.verdict(b), status, b.summary)
        .mkString("\t")
    }
  }
  Files.write(out, ("path\tmodule\tlanguage\tlines\tspan\tkind\tstatus\tsummary" +: rows).asJava, UTF_8)
  val byLanguage = files.groupBy(_.language.label).toVector.sortBy(_._1)
  println(
    "language\tfiles\tcode lines\tcounted blocks\tcounted comment lines\tlines shed by deleting all\tof which separators\texempt blocks\texempt lines"
  )
  val totals = byLanguage.map { (language, group) =>
    val counted = group.flatMap(_.counted)
    val removals = group.map(f => f.removing(f.counted))
    val exempt = group.flatMap(_.exempt)
    val row = Vector(
      group.size,
      group.map(_.lineCount).sum,
      counted.size,
      counted.map(_.lines).sum,
      removals.map(_.linesShed).sum,
      removals.map(_.separatorLinesShed).sum,
      exempt.size,
      exempt.map(_.lines).sum
    )
    println((language +: row.map(_.toString)).mkString("\t"))
    row
  }
  println(("total" +: totals.transpose.map(_.sum.toString)).mkString("\t"))

def apply(root: Path, decisionsFile: Path, prefixes: Seq[String]): Unit =
  val decisions = readDecisions(decisionsFile)
    .filter(d => removedCategories(d.category))
    .filter(d => prefixes.isEmpty || prefixes.exists(d.path.startsWith))
  var shed = 0
  var separators = 0
  var removedBlocks = 0
  decisions.groupBy(_.path).toVector.sortBy(_._1).foreach { (path, rows) =>
    val file = CommentScope.default.file(root, path).getOrElse(sys.error(s"$path: not a file in the comment scope"))
    val byRange = reviewable(file).map(b => b.lineRange -> b).toMap
    val chosen = rows.map { d =>
      byRange.getOrElse(d.lines, sys.error(s"$path:${d.lines}: no comment block spans exactly these lines now"))
    }
    val removal = file.removing(chosen.distinct)
    Files.write(root.resolve(path), removal.result.getBytes(UTF_8))
    shed += removal.linesShed
    separators += removal.separatorLinesShed
    removedBlocks += chosen.distinct.size
  }
  println(s"removed $removedBlocks blocks; lines shed $shed (of which blank separator lines $separators)")

@main def deleteComments(mode: String, args: String*): Unit =
  val root = Path.of(".").toAbsolutePath.normalize
  (mode, args.toList) match
    case ("inventory", out :: Nil)                                     => inventory(root, Path.of(out))
    case ("show", prefixes) if prefixes.nonEmpty                       => show(root, prefixes, kept = false)
    case ("show-kept", prefixes) if prefixes.nonEmpty                  => show(root, prefixes, kept = true)
    case ("trim-tags", prefixes) if prefixes.nonEmpty                  => trimTags(root, prefixes)
    case ("decide", overrides :: out :: prefixes) if prefixes.nonEmpty =>
      decide(root, Path.of(overrides), Path.of(out), prefixes)
    case ("apply", decisions :: prefixes) => apply(root, Path.of(decisions), prefixes)
    case _                                =>
      sys.error(
        "usage: inventory <out.tsv> | show <path prefix>... | show-kept <path prefix>... | decide <overrides.tsv> <decisions.tsv> <path prefix>... | " +
          "apply <decisions.tsv> [path prefix]..."
      )
