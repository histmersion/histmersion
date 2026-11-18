package com.histmersion
package server

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import scala.collection.mutable.{LinkedHashSet, ListBuffer}
import scala.concurrent.duration.Duration
import scala.jdk.CollectionConverters.*

import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Text.sentence

/** The sentence ledger (G60, speed plan B): every place in main code that builds a problem a user reads has a row in
  * `architecture/sentences.tsv`, holding the sentence it wrote when the row was made (its template). A row is `same`
  * while the place still writes a sentence of that shape: an unclassified place the same text, a classified one a case
  * whose example renders to the template's shape (DocQuotedSentencesSuite's matcher, every `{splice}` any text). A row
  * is `changed` when it does not, with the new sentence written beside the old one, so the change shows in review; a
  * row whose place is gone is `removed`. The file is regenerated with `HISTMERSION_UPDATE_ARCHITECTURE=1 sbt
  * 'server/testOnly *SentenceLedgerSuite'`; until then any new place, changed sentence or dropped place fails here.
  *
  * The places come from `tools/refactor/SentenceSites.scala`, which the guideline inventory's run also runs
  * (`guideline-ratchet.conf`). A place is keyed by the SemanticDB symbol of the definition around it and its rank there
  * (`<symbol>#<n>`): the key survives moved lines and moved files; a renamed definition keeps its rows when its places
  * are the same.
  */
class SentenceLedgerSuite extends munit.FunSuite:
  import SentenceLedgerSuite.*

  override val munitTimeout = Duration(20, "min")

  test("every place that builds a problem has its row, and every row says whether its sentence is the same"):
    val _ = GuidelineRatchetSuite.Inventory.findings
    val sites = Site.parse(Files.readAllLines(SitesFile, UTF_8).asScala.toList)
    val listed =
      if Files.isRegularFile(LedgerFile) then Row.parse(Files.readAllLines(LedgerFile, UTF_8).asScala.toList) else Nil
    val expected = Ledger.update(listed, sites, Examples.sentences)
    if sys.env.get("HISTMERSION_UPDATE_ARCHITECTURE").contains("1") then
      val _ = Files.write(LedgerFile, Row.render(expected).asJava, UTF_8)
    else
      val differences = Ledger.differences(listed, expected)
      assert(
        differences.isEmpty,
        s"architecture/sentences.tsv is not what the code writes — review, then regenerate it:\n${differences.mkString("\n")}"
      )

  test("a template's splices are any text; doubled braces are braces"):
    assertEquals(Ledger.shape("no such file: {p} ({n + 1} {{tries}})"), "no such file: … (… {tries})")
    assert(DocQuotedSentencesSuite.Shape.matches(Ledger.shape("no such file: {p}"), "no such file: /tmp/a b"))
    assert(!DocQuotedSentencesSuite.Shape.matches(Ledger.shape("no such file: {p}"), "cannot read /tmp/a b"))

  test("a cell reads back as it was written: tabs, line breaks and backslashes"):
    val text = "a\tb\nc\\d"
    assertEquals(Row.unescape(Row.escape(text)), text)
    val row = Row("k#0", "p.scala", "Unclassified", "", "a\nb", Status.Same, "")
    assertEquals(Row.parse(Row.render(List(row))), List(row))

  test("an example's case and the vocabulary written in it are its class names"):
    assertEquals(Examples.caseOf(Problem.Run.Interrupted), "Run.Interrupted")
    val setting = Problem.examples.collectFirst { case s: Problem.Config.Setting => s }.get
    assertEquals(Examples.caseOf(setting), "Config.Setting")
    assert(Examples.variantsOf(setting).exists(_.startsWith("Config.Fault.")), Examples.variantsOf(setting))

  private val unclassified = Site("e#0", "m", "p.scala", "e", "Unclassified", "", "no such file: {p}")
  private val examples = Map(
    "Io.File" -> Vector(Examples.Rendered(Set("Io.Access.Read"), "cannot read /tmp/x: gone")),
    "NotFound.Entity" -> Vector(Examples.Rendered(Set.empty, "no such file: /tmp/x"))
  )

  test("a new place gets a row: its text, or its case's sentence"):
    val classified = Site("e#1", "m", "p.scala", "e", "Io.File", "Io.Access.Read", "-")
    assertEquals(
      Ledger.update(Nil, List(unclassified, classified), examples),
      List(
        Row("e#0", "p.scala", "Unclassified", "", "no such file: {p}", Status.Same, ""),
        Row("e#1", "p.scala", "Io.File", "Io.Access.Read", "cannot read /tmp/x: gone", Status.Same, "")
      )
    )

  test("a place classified with a case of the same shape is the same; another shape is changed, with its sentence"):
    val before = Ledger.update(Nil, List(unclassified), examples)
    val same = unclassified.copy(site = "NotFound.Entity", template = "-")
    assertEquals(Ledger.update(before, List(same), examples).map(_.status), List(Status.Same))
    val other = unclassified.copy(site = "Io.File", variants = "Io.Access.Read", template = "-")
    assertEquals(
      Ledger.update(before, List(other), examples).map(r => (r.status, r.sentence)),
      List((Status.Changed, "cannot read /tmp/x: gone"))
    )

  test("an unclassified place whose text changed is changed; a gone place is removed, a moved one keeps its row"):
    val before = Ledger.update(Nil, List(unclassified), examples)
    val reworded = unclassified.copy(template = "no file: {p}")
    assertEquals(
      Ledger.update(before, List(reworded), examples).map(r => (r.status, r.sentence)),
      List((Status.Changed, "no file: {p}"))
    )
    assertEquals(Ledger.update(before, Nil, examples).map(_.status), List(Status.Removed))
    val renamed = unclassified.copy(key = "f#0", enclosing = "f")
    assertEquals(Ledger.update(before, List(renamed), examples).map(r => (r.key, r.status)), List(("f#0", Status.Same)))

  test("places of one definition pair by what they build first, then in order"):
    val a = unclassified.copy(key = "e#0", template = "a")
    val b = unclassified.copy(key = "e#1", template = "b")
    val before = Ledger.update(Nil, List(a, b), examples)
    // a place added in front: `b` is still `b`, the new one is new
    val n = unclassified.copy(key = "e#0", template = "new")
    val after = Ledger.update(before, List(n, a.copy(key = "e#1"), b.copy(key = "e#2")), examples)
    assertEquals(
      after.map(r => (r.key, r.template, r.status.word)).sorted,
      List(("e#0", "new", "same"), ("e#1", "a", "same"), ("e#2", "b", "same"))
    )

  test("the differences name the rows added, gone and changed"):
    val row = Row("e#0", "p.scala", "Unclassified", "", "a", Status.Same, "")
    assertEquals(
      Ledger.differences(List(row), List(row.copy(status = Status.Changed, sentence = "b"))),
      List("- e#0\tUnclassified\ta\tsame\t", "+ e#0\tUnclassified\ta\tchanged\tb")
    )

object SentenceLedgerSuite:

  val LedgerFile: Path = Path.of("architecture/sentences.tsv")
  val SitesFile: Path = Path.of("target/guidelines/sentence-sites.tsv")

  /** A place that builds a problem, as SentenceSites reports it. */
  final case class Site(
      key: String,
      module: String,
      path: String,
      enclosing: String,
      site: String,
      variants: String,
      template: String
  ):
    /** What the place builds: its case, the vocabulary written in it, and an unclassified place's text. */
    def built: (String, String, String) = (site, variants, template)

    /** The sentences a classified place may write: its case's examples holding every value it writes, else all of them.
      */
    def candidates(examples: Map[String, Vector[Examples.Rendered]]): Vector[String] =
      val all = examples.getOrElse(site, Vector.empty)
      val wanted = variants.split(';').filter(_.nonEmpty).toSet
      val holding = all.filter(e => wanted.subsetOf(e.variants))
      (if holding.nonEmpty then holding else all).map(_.sentence)

    /** The place's first row: its text when unclassified, else its case's example sentence (`-` when there is none). */
    def fresh(examples: Map[String, Vector[Examples.Rendered]]): Row =
      val first =
        if site == Ledger.Unclassified then template else candidates(examples).headOption.fold("-")(Ledger.literal)
      Row(key, path, site, variants, first, Status.Same, "")

  object Site:
    def parse(rows: List[String]): List[Site] =
      rows.filterNot(_.startsWith("key\t")).filter(_.nonEmpty).map { row =>
        val c = row.split("\t", -1).map(Row.unescape)
        Site(c(0), c(1), c(2), c(4), c(5), c(6), c(7))
      }

  enum Status(val word: String) derives CanEqual:
    case Same extends Status("same")
    case Changed extends Status("changed")
    case Removed extends Status("removed")

  /** A row of the ledger: the place's key and file, what it builds now, the sentence it wrote when the row was made,
    * and whether it still writes one of that shape (with the new sentence when not).
    */
  final case class Row(
      key: String,
      path: String,
      site: String,
      variants: String,
      template: String,
      status: Status,
      sentence: String
  ):
    def enclosing: String = key.substring(0, math.max(0, key.lastIndexOf('#')))

    /** What the place built when the row was last written: an unclassified place's latest text. */
    def built: (String, String, String) =
      (
        site,
        variants,
        if site != Ledger.Unclassified then "-" else if status == Status.Changed then sentence else template
      )

    /** The row for the place it pairs with now: the template it had, the status the place gives it. */
    def checked(now: Site, examples: Map[String, Vector[Examples.Rendered]]): Row =
      val (status, sentence) =
        if template == "-" then (Status.Same, "")
        else if now.site == Ledger.Unclassified then
          if now.template == template then (Status.Same, "") else (Status.Changed, now.template)
        else
          val rendered = now.candidates(examples)
          if rendered.exists(DocQuotedSentencesSuite.Shape.matches(Ledger.shape(template), _)) then (Status.Same, "")
          else (Status.Changed, rendered.headOption.getOrElse("-"))
      Row(now.key, now.path, now.site, now.variants, template, status, sentence)

  object Row:
    val Header: String =
      "# key\tpath\tsite\tvariants\ttemplate\tstatus\tsentence — the sentence ledger (G60): every place that builds a " +
        "problem, the sentence it wrote when the row was made, and whether it still does; regenerate with " +
        "HISTMERSION_UPDATE_ARCHITECTURE=1 sbt 'server/testOnly *SentenceLedgerSuite'"

    def escape(text: String): String =
      text.replace("\\", "\\\\").replace("\t", "\\t").replace("\r", "\\r").replace("\n", "\\n")

    def unescape(cell: String): String =
      val out = new StringBuilder
      var i = 0
      while i < cell.length do
        if cell.charAt(i) == '\\' && i + 1 < cell.length then
          out.append(cell.charAt(i + 1) match
            case 't'   => '\t'
            case 'n'   => '\n'
            case 'r'   => '\r'
            case other => other)
          i += 2
        else
          out.append(cell.charAt(i))
          i += 1
      out.toString

    def render(rows: List[Row]): List[String] =
      Header :: rows.map(r =>
        List(r.key, r.path, r.site, r.variants, r.template, r.status.word, r.sentence).map(escape).mkString("\t")
      )

    def parse(lines: List[String]): List[Row] =
      lines.filterNot(l => l.startsWith("#") || l.isBlank).map { l =>
        val c = l.split("\t", -1).map(unescape)
        Row(c(0), c(1), c(2), c(3), c(4), Status.values.find(_.word == c(5)).getOrElse(Status.Changed), c(6))
      }

  /** The sentences of [[Problem.examples]], by case: what a classified place is checked against. */
  object Examples:
    /** One example's sentence and the vocabulary written in it (`Config.Fault.Empty`). */
    final case class Rendered(variants: Set[String], sentence: String)

    private val Prefix = "com.histmersion.util.problem.Problem$"

    /** The case of a problem by its class (`Run.Interrupted`), as SentenceSites names a place's case. */
    def caseOf(value: Product): String = nameOf(value).getOrElse("")

    /** `Problem$Config$Setting` is `Config.Setting`; an enum's case without fields (an anonymous class of the enum) is
      * the enum's name and its own (`Config.Fault.Empty`).
      */
    private def nameOf(value: Product): Option[String] =
      Option(value).map(_.getClass.getName).filter(_.startsWith(Prefix)).map { full =>
        val parts = full.stripPrefix(Prefix).split('$').toVector.filter(_.nonEmpty)
        val named = parts.takeWhile(p => !p.forall(_.isDigit) && p != "anon")
        val last = Option.when(named.size < parts.size && !named.lastOption.contains(value.productPrefix))(
          value.productPrefix
        )
        (named ++ last).mkString(".")
      }

    /** The vocabulary values and nested problems inside a problem's fields. */
    def variantsOf(problem: Product): Set[String] =
      // a field is `Any` (as PluginHost.walk reads one): a record, a collection, or a value with no parts
      def walk(value: Any): Set[String] =
        if value.isInstanceOf[Product] then
          val p = value.asInstanceOf[Product]
          nameOf(p).toSet ++ p.productIterator.flatMap(walk)
        else if value.isInstanceOf[Iterable[?]] then value.asInstanceOf[Iterable[?]].toSet.flatMap(walk)
        else Set.empty
      problem.productIterator.flatMap(walk).toSet.filter(_.contains('.'))

    lazy val sentences: Map[String, Vector[Rendered]] =
      (Problem.examples ++ DocQuotedSentencesSuite.Quoted).toVector
        .groupBy(caseOf)
        .view
        .mapValues(_.map(p => Rendered(variantsOf(p), p.sentence)))
        .toMap

  object Ledger:
    val Unclassified = "Unclassified"

    /** A template as a shape: every `{splice}` any text, a doubled brace a brace. */
    def shape(template: String): String =
      val out = new StringBuilder
      var i = 0
      while i < template.length do
        template.charAt(i) match
          case '{' if template.startsWith("{{", i) => out.append('{'); i += 2
          case '}' if template.startsWith("}}", i) => out.append('}'); i += 2
          case '{'                                 =>
            out.append('…');
            i = template.indexOf('}', i) match
              case -1  => template.length
              case end => end + 1
          case c => out.append(c); i += 1
      out.toString

    /** A literal sentence as a template: its braces doubled. */
    def literal(sentence: String): String = sentence.replace("{", "{{").replace("}", "}}")

    /** The rows `listed` become for the places `sites`: places pair with rows of the same definition, first by what
      * they build and then in order; a place left over pairs with a row of another definition of its file that built
      * the same and is the only one to; rows left are removed, places left are new.
      */
    def update(listed: List[Row], sites: List[Site], examples: Map[String, Vector[Examples.Rendered]]): List[Row] =
      val (removed, live) = listed.partition(_.status == Status.Removed)
      val pairs = ListBuffer.empty[(Row, Site)]
      val rowsLeft = LinkedHashSet.from(live)
      val sitesLeft = LinkedHashSet.from(sites)
      def pair(r: Row, s: Site): Unit =
        pairs += r -> s; rowsLeft -= r; sitesLeft -= s
      val rowsBy = live.groupBy(_.enclosing)
      sites.groupBy(_.enclosing).foreach { (enclosing, here) =>
        val rows = rowsBy.getOrElse(enclosing, Nil)
        here.foreach(s => rows.find(r => rowsLeft(r) && r.built == s.built).foreach(pair(_, s)))
        here.filter(sitesLeft).zip(rows.filter(rowsLeft)).foreach((s, r) => pair(r, s))
      }
      // a renamed or moved definition: the same thing built in the same file, by one place and one row only
      sitesLeft.toList.groupBy(s => (s.path, s.built)).foreach { (k, ss) =>
        rowsLeft.toList.filter(r => (r.path, r.built) == k) match
          case List(r) if ss.size == 1 => pair(r, ss.head)
          case _                       => ()
      }
      val rows = pairs.toList.map((r, s) => r.checked(s, examples)) ++
        sitesLeft.toList.map(_.fresh(examples)) ++
        (rowsLeft.toList ++ removed).map(_.copy(status = Status.Removed, sentence = ""))
      rows.sortBy(r => (r.path, r.key, r.status.ordinal))

    /** The rows of `listed` and `expected` that differ, as `- row` / `+ row` (key, site, template, status, sentence).
      */
    def differences(listed: List[Row], expected: List[Row]): List[String] =
      def line(r: Row) =
        List(r.key, r.site, r.template, r.status.word, r.sentence).map(Row.escape).mkString("\t")
      val was = listed.map(line).toSet
      val now = expected.map(line).toSet
      listed.map(line).filterNot(now).map("- " + _) ++ expected.map(line).filterNot(was).map("+ " + _)
