//> using scala 3.10.1
//> using file ../../modules/server/src/test/scala/com/histmersion/server/comments/SourceLanguage.scala
//> using file ../../modules/server/src/test/scala/com/histmersion/server/comments/SourceComment.scala
//> using file ../../modules/server/src/test/scala/com/histmersion/server/comments/CommentLexer.scala
//> using file ../../modules/server/src/test/scala/com/histmersion/server/comments/CommentBlock.scala
//> using file ../../modules/server/src/test/scala/com/histmersion/server/comments/CommentedFile.scala
//> using file ../../modules/server/src/test/scala/com/histmersion/server/comments/CommentScope.scala
//> using file ../../modules/server/src/test/scala/com/histmersion/server/comments/CommentRemoval.scala

import com.histmersion.server.comments.*
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

final case class Decision(path: String, lines: String, language: String, category: String, reason: String)

val categories: Set[String] = Set("tautology", "stale", "excuse", "decision", "keep")

val removedCategories: Set[String] = categories - "keep"

val tautologyReason: String = "restates what the names, types and code beside it already say"

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

def show(root: Path, prefixes: Seq[String]): Unit =
  CommentScope.default.files(root).filter(under(prefixes)).filter(_.counted.nonEmpty).foreach { f =>
    println(s"== ${f.path}")
    f.counted.foreach(b => println(s"${b.lineRange} ${squash(b.text)} => ${nextCode(f, b)}"))
  }

def decide(root: Path, overridesFile: Path, out: Path, prefixes: Seq[String]): Unit =
  val overrides = readDecisions(overridesFile).map(d => (d.path, d.lines) -> d).toMap
  val files = CommentScope.default.files(root).filter(under(prefixes))
  val rows = files.flatMap { f =>
    f.counted.map { b =>
      overrides.getOrElse(
        (f.path, b.lineRange),
        Decision(f.path, b.lineRange, f.language.label, "tautology", tautologyReason)
      )
    }
  }
  val known = files.flatMap(f => f.counted.map(b => (f.path, b.lineRange))).toSet
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
      val status = f.exemption.map(why => s"exempt:$why").getOrElse(if b.directive then "directive" else "counted")
      List(f.path, f.module, f.language.label, b.lineRange, b.lines.toString, b.kind, status, b.summary).mkString("\t")
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
    val byRange = file.counted.map(b => b.lineRange -> b).toMap
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
    case ("show", prefixes) if prefixes.nonEmpty                       => show(root, prefixes)
    case ("decide", overrides :: out :: prefixes) if prefixes.nonEmpty =>
      decide(root, Path.of(overrides), Path.of(out), prefixes)
    case ("apply", decisions :: prefixes) => apply(root, Path.of(decisions), prefixes)
    case _                                =>
      sys.error(
        "usage: inventory <out.tsv> | show <path prefix>... | decide <overrides.tsv> <decisions.tsv> <path prefix>... | " +
          "apply <decisions.tsv> [path prefix]..."
      )
