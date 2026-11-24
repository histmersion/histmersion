import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import scala.sys.process.*

object Suppressions:

  val LintRuleFile = "modules/lint/src/main/scala/com/histmersion/lint/LintRule.scala"
  val AnnotationFiles: Map[String, String] = Map(
    "movesTo" -> "modules/lint/src/main/scala/com/histmersion/lint/movesTo.scala",
    "atBoundary" -> "modules/lint/src/main/scala/com/histmersion/lint/atBoundary.scala"
  )
  val UnusedFile = "modules/db/src/main/scala/com/histmersion/util/database/Unused.scala"

  private val Tolerated = "@tolerated\\(\\s*LintRule\\.(\\w+)".r
  private val MovesTo = "@movesTo\\(".r
  private val AtBoundary = "@atBoundary\\(".r
  private val UnusedCall = "(?<![\\w.])Unused\\s*\\(".r
  private val UnusedPattern = "\\bcase\\s+Unused\\s*\\(".r
  private val RuleCase = "\\b([A-Z]\\w*)\\b".r

  def counts(files: Iterable[(String, String)]): Map[String, Int] =
    files.toList
      .flatMap { (path, text) =>
        Tolerated.findAllMatchIn(text).map(_.group(1)).toList ++
          MovesTo.findAllMatchIn(text).map(_ => "movesTo") ++
          AtBoundary.findAllMatchIn(text).map(_ => "atBoundary") ++
          (if path.contains("/src/main/") && path != UnusedFile then
             List.fill(UnusedCall.findAllMatchIn(text).size - UnusedPattern.findAllMatchIn(text).size)("acceptedOutcome")
           else Nil)
      }
      .groupMapReduce(identity)(_ => 1)(_ + _)

  def ruleCases(text: String): Set[String] =
    RuleCase.findAllMatchIn(text).map(_.group(1)).toSet - "LintRule"

  final case class Row(key: String, base: Int, head: Int, introduced: Boolean):
    def rose: Boolean = head > base && !introduced

  def compare(base: Map[String, Int], head: Map[String, Int], knownAtBase: String => Boolean): List[Row] =
    (base.keySet ++ head.keySet).toList.sorted.map { k =>
      Row(k, base.getOrElse(k, 0), head.getOrElse(k, 0), !knownAtBase(k))
    }

  def git(args: String*): Option[String] =
    val out = new StringBuilder
    val code = Process("git" +: args).!(ProcessLogger(l => out.append(l).append('\n'), _ => ()))
    Option.when(code == 0)(out.toString)

  def blobs(commit: String, paths: List[String]): Map[String, String] =
    if paths.isEmpty then Map.empty
    else
      val input = paths.map(p => s"$commit:$p").mkString("\n").getBytes(UTF_8)
      val bytes = new ByteArrayOutputStream
      val code = (Process(Seq("git", "cat-file", "--batch")) #< new java.io.ByteArrayInputStream(input) #> bytes).!
      require(code == 0, "git cat-file --batch failed")
      val data = bytes.toByteArray
      var at = 0
      val found = Map.newBuilder[String, String]
      paths.foreach { p =>
        val nl = data.indexOf('\n'.toByte, at)
        val header = new String(data, at, nl - at, UTF_8)
        at = nl + 1
        if !header.endsWith("missing") then
          val size = header.split(' ').last.toInt
          found += p -> new String(data, at, size, UTF_8)
          at += size + 1
      }
      found.result()

  def headFiles(): List[(String, String)] =
    val root = Path.of("modules")
    val stream = Files.walk(root)
    try
      stream.iterator.asScala
        .filter(p => Files.isRegularFile(p) && p.toString.endsWith(".scala"))
        .map(p => p.toString.replace('\\', '/') -> Files.readString(p, UTF_8))
        .toList
    finally stream.close()

  def baseRef(): String =
    sys.env
      .get("HISTMERSION_MERGE_BASE")
      .filter(_.nonEmpty)
      .orElse(List("origin/contracts3-pilot", "contracts3-pilot").flatMap(r => git("merge-base", "HEAD", r)).headOption)
      .map(_.trim)
      .getOrElse(sys.error("no merge base: name it in HISTMERSION_MERGE_BASE, or fetch origin/contracts3-pilot"))

@main def countSuppressions(): Unit =
  import Suppressions.*
  val base = baseRef()
  val listed = git("ls-tree", "-r", "--name-only", base, "--", "modules").getOrElse("").linesIterator.toList
  val scala = listed.filter(_.endsWith(".scala"))
  val atBase = blobs(base, scala)
  val rulesAtBase = atBase.get(LintRuleFile).map(ruleCases).getOrElse(Set.empty)
  def knownAtBase(key: String): Boolean = key match
    case "acceptedOutcome" => true
    case k if AnnotationFiles.contains(k) => atBase.contains(AnnotationFiles(k))
    case rule => rulesAtBase(rule)
  val rows = compare(counts(atBase), counts(headFiles()), knownAtBase)
  println(s"suppressions against the merge base ${base.take(9)}: key, base, head")
  rows.foreach(r =>
    println(f"  ${r.key}%-20s ${r.base}%6d ${r.head}%6d${if r.introduced then "  (new at this change)" else ""}")
  )
  val risen = rows.filter(_.rose)
  println(s"  total ${rows.map(_.base).sum} -> ${rows.map(_.head).sum}")
  if risen.nonEmpty then
    System.err.println(
      "more suppressions than at the merge base (fix the finding instead; the count only falls):\n" +
        risen.map(r => s"  ${r.key}: ${r.base} -> ${r.head}").mkString("\n")
    )
    sys.exit(1)
