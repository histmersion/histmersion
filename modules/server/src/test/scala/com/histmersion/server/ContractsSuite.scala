package com.histmersion
package server

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import scala.annotation.tailrec
import scala.jdk.CollectionConverters.*

import com.histmersion.server.ArchitectureSuite.{Blank, SourceFile, Violation}

/** The coding practices of AGENTS.md (rules 1–14) that a reading of the sources can check, over every main module:
  *
  *   - `primitive` (rules 1, 3): a public method of a service, repository or controller whose parameter or result is a
  *     `String`/`Int`/`Long` (alone or in an `Option`/collection) named for a concept that has a newtype;
  *   - `unwrap` (rules 1, 4): `.value` outside boundary code (the db, cli, web and config modules; routes, endpoints,
  *     tools, codecs, repositories, SQL, Markdown, JSON, wiring and transformer files; a newtype's own file);
  *   - `concept` (rule 3): a top-level type name defined in more than one file of one layer (the JVM's and the Scala.js
  *     copy of one file are one definition);
  *   - `derives` (rule 7, G66): a codec, schema or config given written by hand, generic ones included, or a `.derived`
  *     call — a CanEqual given is none of them (G67);
  *   - `copy` (rule 8): `.copy(`;
  *   - `one-type` (rule 9): a file with more than one top-level type (a companion, a type alias of a newtype, its
  *     constraint `<Name>Rule` and the cases of a sealed type in its file not counted);
  *   - `transaction` (rule 11): a repository that opens a connection or transaction (magnum's `connect`/`transact`, a
  *     `Transactor`, `getConnection`), or takes a `DataSource`;
  *   - `global` (rule 12): the environment, system properties, the home or working directory, or the wall clock read
  *     outside an entry point (`@main def`, `SystemContext`);
  *   - `service` (rule 13): a `…Service` trait whose file has no `Implementation` and no `InMemory`;
  *   - `helper` (rule 14): a top-level object without a type of its name whose name is another type's name plural or
  *     with a helper suffix (`Ops`, `Utils`, `Helpers`, `Support`, `Syntax`, `Functions`);
  *   - `layer` (rule 3): the client layer (the api and web modules) naming a package outside `com.histmersion.api` and
  *     the web's own, or an api file outside `com.histmersion.api`; a domain package (`com.histmersion.domain…`) naming
  *     the api outside a boundary file (routes, endpoints, tools, transformers, controllers);
  *   - `client-refined` (rule 2): a refined (not `Pure`) constraint in a client module (api, web, cli, plugin-api) —
  *     the server validates, once, where an api value becomes a domain value; the CLI's local plugin management, where
  *     the CLI is the only judge, is allowed by name (`ClientAuthorities`);
  *   - `prefix` (rule 5): a type `<Entity><Property>` while a type `<Entity>` exists in the same layer and package —
  *     the property belongs on the entity's companion (`Approval.Kind`); a component that works on the entity
  *     (`…Service`, `…Routes`, …, `Components`), a newtype's constraint `<Name>Rule` and the web page's own components
  *     are not properties;
  *   - `bucket` (rule 10): a type in a layer bucket (`com.histmersion.db`, `com.histmersion.files`,
  *     `com.histmersion.values`, the flat `com.histmersion.upstreams`) instead of its owner's package;
  *   - `circe` (G62): circe named outside the one adapter file per library that still speaks it (Chimp, sttp-ai): JSON
  *     is jsoniter-scala with Kindlings' derivation and AST;
  *   - `scallop` (G62b): scallop's mutable configuration (`ScallopConf`, `Subcommand`, `opt[`, `trailArg[`,
  *     `addSubcommand(`, the `org.rogach` package) outside the CLI's command-line core (`cli/CommandLine.scala`) and
  *     its per-group adapters (package `cli.commandline`): everything else works on the typed commands they build;
  *   - `nonempty-pattern` (G60): a type pattern `case …: NonEmptyVector[…] =>` outside the problem module — the union
  *     `Problems` is folded (`fold`, `toVector`), never matched on;
  *   - `problem-field` (rule 1, G60): a case of the closed `util.problem.Problem` (or a small type of its file) with a
  *     `String`, `Int`, `Long`, `Double` or `Boolean` field, or the free text of an unclassified site (`Legacy.Text`) —
  *     free text or an unnamed number where a newtype or an enum says what it is. A ratchet whose one row is the
  *     temporary `Unclassified`; the text a column held (`Stored.Text`) is the stored reader's, and no finding;
  *   - `unrecognised` (G60, no exception allowed): `Stored.Unrecognised` built in main code anywhere but the reader of
  *     what a master version stored (`api.problem.StoredShapes`) and the cases' own file — a text no case reads is
  *     kept, never made;
  *   - `relayed` (G60, no exception allowed): `Problem.Relayed` built in main code anywhere but the browser's decoder
  *     (the Scala.js `api.problem.ProblemJson`), the shared entry builder it needs (`api.problem.Answer`) and the
  *     cases' own file — a problem known only by its sentence; built on the JVM, a typed problem would silently become
  *     text;
  *   - `first-problem` (G60 step 5, pinned at 0): one problem picked from several (`.toNonEmpty.head`, the head of a
  *     `problems` list or of the problems' sentences) — a job keeps every problem; only the answer's HTTP status
  *     (`Answer.status`, whose body lists every problem) picks the first;
  *   - `html-sink` (G96/G104, pinned at 0): markup written into the page's DOM (`innerHTML =`, `outerHTML =`,
  *     `insertAdjacentHTML(`, a popup's `setHTML(`, `document.write(`) anywhere but the web page's `HtmlSink`, whose
  *     every method takes an `api.Html` — so no raw String reaches a sink;
  *   - `html-newtype` (G96/W1, a ratchet to 0): a newtype `…Html` over String, which takes any text as markup, beside
  *     the one `api.Html`.
  *
  * Besides the listed rules, a foreign type's `CanEqual` (`given CanEqual[UUID, UUID]`) is defined in one file,
  * imported by name where it is needed (G67): only the types of [[CrossLayerEqualities]], whose users share no module,
  * have two.
  *
  * Besides the listed rules, two hold with no exception at all: a module G60 has classified ([[Classified]]) holds no
  * `Unclassified`, `legacyText` or `Legacy.Text`, in its main or its test code (the api's problem renderers keep
  * exactly the sites [[UnclassifiedKept]] counts until step 7); and no case of the problem module has a default
  * parameter — a caller states every field (an empty path through a named constructor such as `here`), so no value is
  * filled in that the caller did not choose.
  *
  * A file is named by its key ([[ArchitectureSuite.SourceFile.key]]: `api:api/problem/Answer.scala`, or
  * `api:scalajs:api/problem/ProblemJson.scala` in a platform's source root), in every rule, allow-list and row, so a
  * JVM and a Scala.js file at the same path are two files.
  *
  * The violations that exist today are listed in `architecture/contracts.tsv`, exact both ways (a new violation fails,
  * a row nothing violates any more fails, a count must match); each step of the refactor shrinks the list.
  * `HISTMERSION_UPDATE_ARCHITECTURE=1` rewrites it from the sources, falls only: never a missing file, and never a row
  * new or higher than the file at the merge base with the pilot ([[RatchetBase]]) that the owner's accept script did
  * not accept. The same base holds `total-order.tsv`: a reviewed row added or changed in the branch fails.
  */
class ContractsSuite extends munit.FunSuite:
  import ContractsSuite.*

  private lazy val sources: List[SourceFile] = Modules.flatMap(SourceFile.all(_))

  test("no contract violation but the listed exceptions, and every listed exception still exists"):
    val file = ArchitectureSuite.Resources.resolve("contracts.tsv")
    val violations = Rules.all(sources)
    val actual = violations.map(_.row).distinct.sorted
    assert(
      Files.isRegularFile(file),
      s"$file is missing: restore it from git (git checkout -- $file); it is never regenerated, since a regenerated " +
        "file would accept any violation"
    )
    val listed = Files.readAllLines(file, UTF_8).asScala.filterNot(_.startsWith("#")).toList
    if sys.env.get("HISTMERSION_UPDATE_ARCHITECTURE").contains("1") then
      val risen = ArchitectureSuite.Ratchet.risenRules(listed, actual)
      assert(
        risen.isEmpty,
        s"not rewritten: these rules' totals rose (fix the new violations first): ${risen.mkString(", ")}"
      )
      val header = "# rule\tfile\tdetail\tcount — the known violations of AGENTS.md's rules 1–14; shrink, never grow"
      RatchetBase.Spec.Contracts.check(header :: actual, RatchetBase.Git(Path.of(".")), sys.env) match
        case Left(why) => fail(s"not rewritten: $why")
        case Right(_)  => ()
      val _ = Files.write(file, (header :: actual).asJava, UTF_8)
    else
      val added = actual.filterNot(listed.toSet)
      val gone = listed.filterNot(actual.toSet)
      val where = violations.groupBy(_.row).view.mapValues(_.head.at).toMap
      assert(
        added.isEmpty && gone.isEmpty,
        (added.map(r => s"new violation at ${where(r)}: $r") ++
          gone.map(r => s"no longer violated — remove it from architecture/contracts.tsv: $r")).mkString("\n")
      )

  test(
    "contracts.tsv and total-order.tsv are no higher than at the merge base with the pilot, but for the owner's " +
      "accepted rises"
  ):
    List(RatchetBase.Spec.Contracts, RatchetBase.Spec.TotalOrder).foreach { spec =>
      spec.checkFile() match
        case Left(why)   => fail(why)
        case Right(used) => RatchetBase.announce(used).foreach(println)
    }

  test("the scanner reads a signature across lines and skips private members"):
    val code =
      """trait FooService:
        |  def find(citekey: String,
        |           limit: Int): Option[String]
        |  private def hidden(doi: String): Unit
        |  def doi(x: Long): String
        |""".stripMargin
    val sigs = Rules.signatures(code).map(s => s.name -> s.params.map(_._1))
    assertEquals(sigs, List("find" -> List("citekey", "limit"), "doi" -> List("x")))

  test("a signature broken before its result type still has the result"):
    val code =
      """trait FooService:
        |  def find(
        |      citekey: String
        |  )
        |      : Option[String] = None
        |""".stripMargin
    assertEquals(
      Rules.signatures(code).map(s => (s.name, s.params.map(_._1), s.result)),
      List(("find", List("citekey"), "Option[String]"))
    )

  test("a case of a sealed type is one with its parent however its header is laid out"):
    def oneType(text: String): List[String] =
      val file =
        ArchitectureSuite.SourceFile(
          "core:x/Shape.scala",
          "com.histmersion.x",
          ArchitectureSuite.Blank(text),
          text
        )
      Rules.all(List(file)).filter(_.rule == "one-type").map(_.detail)
    val oneLine = "sealed trait Shape\nfinal case class Circle(r: Int = 1) extends Shape\nobject Other\n"
    val broken =
      "sealed trait Shape\nfinal case class Circle(\n    r: Int = 1\n) extends Shape\n    derives CanEqual\nobject Other\n"
    assertEquals(oneType(oneLine), List("Shape Other"))
    assertEquals(oneType(broken), oneType(oneLine))

  test("scallop's configuration is declared only in the command-line core and its adapters"):
    def hits(key: String, text: String): List[String] =
      val file = ArchitectureSuite.SourceFile(key, "com.histmersion.cli", ArchitectureSuite.Blank(text), text)
      Rules.all(List(file)).filter(_.rule == "scallop").map(_.file)
    val code =
      "object X:\n  class C extends ScallopConf(Nil):\n    val a = opt[String](\"a\")\n    val b = trailArg[Int]()\n"
    assertEquals(hits("cli:cli/Cli.scala", code), List("cli:cli/Cli.scala"))
    assertEquals(hits("cli:cli/CommandLine.scala", code), Nil)
    assertEquals(hits("cli:cli/commandline/ArticleArgs.scala", code), Nil)
    assertEquals(
      hits("cli:cli/Ops.scala", "object Ops:\n  // opt[String] in a comment\n  val s = \"Subcommand\"\n"),
      Nil
    )
    assertEquals(hits("cli:cli/Ops.scala", "import org.rogach.scallop.Subcommand\n"), List("cli:cli/Ops.scala"))
    assertEquals(hits("core:x/Y.scala", "object Y:\n  def opt[A](a: Option[A]): Option[A] = a\n"), Nil)

  test("a parameter names a concept by its newtype's name, or by its last words"):
    val concepts = Set("Citekey", "Doi", "JobId", "Slug", "GoalSlug")
    assertEquals(Rules.conceptOf("citekeys", concepts), Some("Citekey"))
    assertEquals(Rules.conceptOf("sourceDoi", concepts), Some("Doi"))
    assertEquals(Rules.conceptOf("goalSlug", concepts), Some("GoalSlug"))
    assertEquals(Rules.conceptOf("limit", concepts), None)

  test("a case of the closed Problem names its fields' types, and only the text cases hold text"):
    val code =
      """sealed trait Problem
        |object Problem:
        |  final case class Malformed(typeName: TypeName, raw: Raw, at: Vector[Field] = Vector.empty) extends Invalid
        |  final case class Said(plugin: String, count: Int) extends Plugin
        |  final case class Unclassified(text: Legacy.Text) extends Problem
        |  final case class Unrecognised(text: Text) extends Stored
        |  enum Damage:
        |    case Length(expected: Long, found: Count)
        |  def within(p: Problem) = p match
        |    case Invalid.Malformed(t, raw, at) => p
        |""".stripMargin
    assertEquals(
      Rules.caseFields(code).map((c, f, t, _) => s"$c.$f: $t"),
      List(
        "Malformed.typeName: TypeName",
        "Malformed.raw: Raw",
        "Malformed.at: Vector[Field]",
        "Said.plugin: String",
        "Said.count: Int",
        "Unclassified.text: Legacy.Text",
        "Unrecognised.text: Text",
        "Length.expected: Long",
        "Length.found: Count"
      )
    )
    val file = ArchitectureSuite.SourceFile(Rules.ProblemFile, "com.histmersion.util.problem", code, code)
    assertEquals(
      Rules.all(List(file)).filter(_.rule == "problem-field").map(_.detail),
      List("Said.plugin: String", "Said.count: Int", "Unclassified.text: Legacy.Text", "Length.expected: Long")
    )

  test("the modules G60 has classified hold no unclassified problem, in main or test code"):
    val found = Rules.unclassified(Classified.flatMap(m => SourceFile.all(m) ++ SourceFile.all(m, "test")))
    val (kept, rest) = found.partition(v => UnclassifiedKept.contains(v.file))
    assertEquals(rest.map(v => s"${v.at}: ${v.detail}"), Nil)
    assertEquals(kept.groupMapReduce(_.file)(_ => 1)(_ + _), UnclassifiedKept)

  test("no case of the problem module has a default parameter"):
    val found = Rules.caseDefaults(SourceFile.all("problem"))
    assertEquals(found.map(v => s"${v.at}: ${v.detail}"), Nil)

  test("every ORDER BY of the code's SQL orders totally, or is reviewed in architecture/total-order.tsv"):
    val file = ArchitectureSuite.Resources.resolve("total-order.tsv")
    val partial = TotalOrder.partial(SqlModules.flatMap(SourceFile.all(_)), TotalOrder.migrationKeys())
    val reviewed = Files
      .readAllLines(file, UTF_8)
      .asScala
      .filterNot(l => l.startsWith("#") || l.isBlank)
      .map(_.split('\t').take(2).mkString("\t"))
      .toList
    val unreviewed = partial.filterNot(c => reviewed.contains(c.row))
    val stale = reviewed.filterNot(r => partial.exists(_.row == r))
    assert(
      unreviewed.isEmpty && stale.isEmpty,
      (unreviewed.map(c => s"ORDER BY with ties at ${c.file}:${c.line}: end it with a unique key — ${c.text}") ++
        stale.map(r => s"orders totally now — remove it from architecture/total-order.tsv: $r")).mkString("\n")
    )

  test("an ORDER BY orders totally when its terms and pinned columns cover a unique key of a table it reads"):
    val keys = TotalOrder.keys(
      """CREATE TABLE public.events (
        |    id text NOT NULL,
        |    label text NOT NULL
        |);
        |ALTER TABLE ONLY public.events
        |    ADD CONSTRAINT events_pkey PRIMARY KEY (id);
        |ALTER TABLE ONLY public.event_datings
        |    ADD CONSTRAINT event_datings_pkey PRIMARY KEY (event_id, citekey);
        |CREATE UNIQUE INDEX places_wikidata ON public.places USING btree (wikidata);
        |""".stripMargin
    )
    assertEquals(keys("events"), List(Set("id")))
    assertEquals(keys("places"), List(Set("wikidata")))
    def total(clause: String, statement: String) = TotalOrder.total(clause, statement, keys)
    assert(total("e.label, e.id", "SELECT * FROM events e WHERE true"))
    assert(!total("e.label", "SELECT * FROM events e WHERE true"), "a label is no key")
    assert(!total("x.label, x.id", "SELECT * FROM events e JOIN other x ON true"), "the id of another table")
    assert(total("citekey", "SELECT * FROM event_datings WHERE event_id = $id"), "the rest of the key pinned")
    assert(!total("citekey", "SELECT * FROM event_datings WHERE event_id = ANY($ids)"), "ANY pins nothing")
    assert(total("lower(p.name), p.wikidata DESC NULLS LAST", "SELECT * FROM places p"))
    assert(total("""e.id COLLATE "C"""", "SELECT * FROM events e"))
    val code =
      """object Repo:
        |  // ORDER BY label in a comment
        |  def a = sql\"\"\"SELECT id FROM events e
        |              WHERE e.label = $l ORDER BY e.label, e.id LIMIT $n\"\"\"
        |  def b = sql"SELECT id FROM events ORDER BY label"
        |""".stripMargin
    val source = SourceFile("db:x/Repo.scala", "com.histmersion.x", Blank(code), code)
    assertEquals(TotalOrder.clauses(source.key, source.raw).map(_._1.text), List("e.label, e.id", "label"))
    assertEquals(TotalOrder.partial(List(source), keys).map(c => (c.line, c.text)), List((5, "label")))
    // a collation closing a `sql"""…"""`, its quote escaped so it does not end the literal
    val escaped = "def c = sql\"\"\"SELECT id FROM events e ORDER BY e.id COLLATE \"C\\\"\"\"\"\n"
    assertEquals(TotalOrder.clauses("db:x/Repo.scala", escaped).map(_._1.text), List("e.id COLLATE \"C\""))

  test("a default parameter of a case is found, in a class or an enum case, on one line or several"):
    val code =
      """object Cases:
        |  final case class A(x: Int, at: Vector[String] = Vector.empty)
        |  final case class B(
        |      x: Int,
        |      y: Option[Int] = None
        |  )
        |  final case class C(x: Int, at: Vector[String])
        |  enum E:
        |    case D(x: Int = 1)
        |    case F(x: Int)
        |""".stripMargin
    val file = ArchitectureSuite.SourceFile("problem:x/Cases.scala", "com.histmersion.x", Blank(code), code)
    assertEquals(
      Rules.caseDefaults(List(file)).map(v => (v.at, v.detail)),
      List(
        ("problem:x/Cases.scala:2", "A.at"),
        ("problem:x/Cases.scala:3", "B.y"),
        ("problem:x/Cases.scala:9", "D.x")
      )
    )

  test("an unclassified problem is found however it is spelled, never in a comment or a string"):
    val code =
      """import com.histmersion.util.problem.{Legacy, Problem}
        |// Problem.Unclassified(Legacy.Text("in a comment"))
        |object Sites:
        |  val a = Problem.Unclassified(Legacy.Text("x"))
        |  val b = Problem.Unclassified.of(e)
        |  def c(p: Problem) = p.legacyText
        |  val d = "Unclassified in a string"
        |  val e = Problem.Invalid.Missing(Problem.Field("ok"))
        |""".stripMargin
    val file = ArchitectureSuite.SourceFile("values:x/Sites.scala", "com.histmersion.x", Blank(code), code)
    assertEquals(
      Rules.unclassified(List(file)).map(v => (v.at, v.detail)),
      List(
        ("values:x/Sites.scala:1", "Legacy"),
        ("values:x/Sites.scala:4", "Unclassified"),
        ("values:x/Sites.scala:4", "Legacy"),
        ("values:x/Sites.scala:5", "Unclassified"),
        ("values:x/Sites.scala:6", "legacyText")
      )
    )

  test("Stored.Unrecognised is built in main code only by the reader of the texts a master version stored"):
    val code =
      """object Reads:
        |  def read(s: String): Problem = Stored.Unrecognised(Stored.Text(s))
        |  def shown(p: Problem) = p match
        |    case Stored.Unrecognised(text) => s"$text"
        |    case other                     => "x"
        |""".stripMargin
    def found(key: String) =
      Rules
        .all(List(ArchitectureSuite.SourceFile(key, "com.histmersion.x", Blank(code), code)))
        .filter(_.rule == "unrecognised")
    assertEquals(found("server:x/Reads.scala").map(v => (v.count, v.at)), List((1, "server:x/Reads.scala:2")))
    assertEquals(found("api:api/problem/StoredShapes.scala"), Nil)
    assertEquals(found("problem:util/problem/Problem.scala"), Nil)
    Vector("api:scalajvm:api/problem/ProblemJson.scala", "api:scalajs:api/problem/ProblemJson.scala").foreach(key =>
      assertEquals(found(key).map(_.count), List(1), s"$key reads a bare text through StoredShapes or as its sentence")
    )

  test("a problem picked from several, the rest dropped, is a violation; the answer's status alone may pick one"):
    val code =
      """object Jobs:
        |  def fail(ps: Problems) = Some(ps.toNonEmpty.head)
        |  def why(e: Api.Error) = e.problems.head
        |  def first(e: Api.Error) = e.problems
        |    .headOption
        |  def line(ps: Problems) = ps.sentences.head
        |  def picked(ps: NonEmptyVector[Problem]) = NonEmptyVector.toVector(ps).head
        |  def kept(ps: Problems) = Some(ps.toNonEmpty)
        |  def every(e: Api.Error) = e.problems.toVector.map(_.sentence)
        |  // a comment naming problems.head is no pick
        |  val words = "problems.head"
        |""".stripMargin
    def found(key: String) =
      Rules
        .all(List(ArchitectureSuite.SourceFile(key, "com.histmersion.x", Blank(code), code)))
        .filter(_.rule == "first-problem")
    assertEquals(found("server:x/Jobs.scala").map(v => (v.count, v.at)), List((5, "server:x/Jobs.scala:2")))
    assertEquals(found("api:api/problem/Answer.scala"), Nil)

  test("markup reaches the page's DOM only through the one sink that takes Html"):
    val code =
      """object View:
        |  def a(el: Element, s: String) = el.innerHTML = s
        |  def b(el: Element, s: String) = el.insertAdjacentHTML("beforeend", s)
        |  def c(p: GlPopup, s: String) = p.setHTML(s)
        |  def d(el: Element) = el.outerHTML = "<b>x</b>"
        |  def e(s: String) = document.write(s)
        |  def same(el: Element) = el.innerHTML == ""
        |  def read(el: Element) = el.innerHTML.length
        |  // el.innerHTML = s in a comment is no write
        |  val words = "el.innerHTML = s"
        |""".stripMargin
    def found(key: String) =
      Rules
        .all(List(ArchitectureSuite.SourceFile(key, "com.histmersion.x", Blank(code), code)))
        .filter(_.rule == "html-sink")
    assertEquals(found("web:web/View.scala").map(v => (v.count, v.at)), List((5, "web:web/View.scala:2")))
    assertEquals(found("web:web/HtmlSink.scala"), Nil)

  test("a newtype …Html over String is a violation: the one markup type is api.Html"):
    val code =
      """object Render:
        |  type Html = Html.T
        |  object Html extends Newtype[String, Pure]
        |  object BubbleHtml extends Newtype[String, Pure]
        |  object Count extends Newtype[Int, Pure]
        |  object Htmls extends Newtype[String, Pure]
        |""".stripMargin
    val found = Rules
      .all(List(ArchitectureSuite.SourceFile("web:web/Render.scala", "com.histmersion.x", Blank(code), code)))
      .filter(_.rule == "html-newtype")
    assertEquals(found.map(v => (v.count, v.at)), List((2, "web:web/Render.scala:3")))

  test("Problem.Relayed is built in main code only by the browser's decoder and the entry builder it needs"):
    val code =
      """object Reads:
        |  def read(s: Sentence): Problem = Problem.Relayed(s)
        |  def shown(p: Problem) = p match
        |    case Relayed(sentence) => s"$sentence"
        |    case other             => "x"
        |  val group = Problem.Group.Relayed
        |""".stripMargin
    def found(key: String) =
      Rules
        .all(List(ArchitectureSuite.SourceFile(key, "com.histmersion.x", Blank(code), code)))
        .filter(_.rule == "relayed")
    assertEquals(found("server:x/Reads.scala").map(v => (v.count, v.at)), List((1, "server:x/Reads.scala:2")))
    assertEquals(found("api:scalajs:api/problem/ProblemJson.scala"), Nil)
    assertEquals(found("api:api/problem/Answer.scala"), Nil)
    assertEquals(found("problem:util/problem/Problem.scala"), Nil)
    assertEquals(
      found("api:scalajvm:api/problem/ProblemJson.scala").map(v => (v.count, v.at)),
      List((1, "api:scalajvm:api/problem/ProblemJson.scala:2"))
    )

  test("a type pattern on the problems' NonEmptyVector is a violation outside the problem module"):
    val code =
      """object Answers:
        |  def shown(e: Either[Problems, Int]) = e match
        |    case Left(ps: NonEmptyVector[Problem]) => ps.toVector.size
        |    case Left(p)                           => 1
        |  val many: NonEmptyVector[Problem] = NonEmptyVector.of(a, b)
        |""".stripMargin
    def found(key: String) =
      Rules
        .all(List(ArchitectureSuite.SourceFile(key, "com.histmersion.x", code, code)))
        .filter(_.rule == "nonempty-pattern")
    assertEquals(found("server:x/Answers.scala").map(v => (v.count, v.at)), List((1, "server:x/Answers.scala:3")))
    assertEquals(found("problem:util/problem/Answers.scala"), Nil)

  test("a hand-written codec given counts for derives, generic and polymorphic forms included"):
    val code =
      """object Codecs:
        |  given JsonValueCodec[Foo] = make
        |  given foo: Schema[Foo] = make
        |  given box: [A] => (c: JsonValueCodec[A]) => JsonValueCodec[Box[A]] = make
        |  given [A] => Schema[A] => Schema[Box[A]] = make
        |  given [A](using c: JsonValueCodec[A]): JsonValueCodec[List[A]] = make
        |  given pair[A: Schema, B: Schema]: Schema[(A, B)] = make
        |  given split: [A] =>
        |    (c: JsonValueCodec[A]) =>
        |      JsonValueCodec[Split[A]] = make
        |  inline given wrapped: [T] => (m: Mirror[T]) => (b: JsonKeyCodec[m.Base]) => JsonKeyCodec[T] = make
        |  given ordering: [A] => (c: JsonValueCodec[A]) => Ordering[Box[A]] = make
        |  given [A] => CanEqual[Box[A], Box[A]] = CanEqual.derived
        |  given Conversion[Foo, Bar] = make
        |""".stripMargin
    def lines(key: String) =
      Rules
        .all(List(ArchitectureSuite.SourceFile(key, "com.histmersion.x", Blank(code), code)))
        .filter(_.rule == "derives")
        .map(v => (v.count, v.at))
    assertEquals(lines("core:x/Codecs.scala"), List((8, "core:x/Codecs.scala:2")))
    assertEquals(Rules.genericCodecGivens(code).map(code.substring(0, _).count(_ == '\n') + 1), List(4, 5, 6, 7, 8, 11))
    assertEquals(lines("json:x/Codecs.scala"), Nil)

  test("a CanEqual given is no codec given: CanEqual.derived and a hand-written CanEqual do not count for derives"):
    val code =
      """object Equalities:
        |  given CanEqual[UUID, UUID] = CanEqual.derived
        |  private given CanEqual[java.nio.file.Path, java.nio.file.Path] = scala.CanEqual.derived
        |  given [A] => CanEqual[Box[A], Box[A]] = CanEqual.canEqualAny
        |  inline given CanEqual[T, T] = CanEqual.derived
        |  given JsonValueCodec[Foo] = make
        |  val bar = Schema.derived[Bar]
        |  val baz = ConfigReader.derived[Baz]
        |""".stripMargin
    def lines(key: String) =
      Rules
        .all(List(ArchitectureSuite.SourceFile(key, "com.histmersion.x", Blank(code), code)))
        .filter(_.rule == "derives")
        .map(v => (v.count, v.at))
    assertEquals(lines("core:x/Equalities.scala"), List((3, "core:x/Equalities.scala:6")))
    assertEquals(
      Rules.Derived.findAllMatchIn(code).map(m => code.substring(0, m.start).count(_ == '\n') + 1).toList,
      List(7, 8)
    )

  test("a foreign type's CanEqual is defined in one file, but where its users share no module"):
    assertEquals(Rules.equalityTwice(sources), CrossLayerEqualities)

  test("a CanEqual given of one type with itself is found, its package left out; a lifted or mixed one is not"):
    val code =
      """object Equalities:
        |  given uid: CanEqual[java.util.UUID, java.util.UUID] = CanEqual.derived
        |  private given CanEqual[UUID, UUID] = CanEqual.derived
        |    given CanEqual[java.nio.file.WatchEvent.Kind[?], java.nio.file.WatchEvent.Kind[?]] = CanEqual.derived
        |  given [A] => CanEqual[Box[A], Box[A]] = CanEqual.canEqualAny
        |  given CanEqual[Int, Long] = CanEqual.derived
        |  def f(using CanEqual[K, K]) = 1
        |""".stripMargin
    assertEquals(
      Rules.equalityGivens(code).map((t, at) => (t, code.substring(0, at).count(_ == '\n') + 1)),
      List(("UUID", 2), ("UUID", 3), ("WatchEvent.Kind[?]", 4))
    )
    def file(key: String) = ArchitectureSuite.SourceFile(key, "com.histmersion.x", Blank(code), code)
    assertEquals(
      Rules.equalityTwice(List(file("core:x/A.scala"), file("db:x/B.scala"))),
      Map(
        "UUID" -> List("core:x/A.scala", "db:x/B.scala"),
        "WatchEvent.Kind[?]" -> List("core:x/A.scala", "db:x/B.scala")
      )
    )

  test("a JVM and a Scala.js file at the same path are two files, each counted under its own key"):
    val tree = Files.createTempDirectory("contracts-roots")
    def write(root: String, path: String, text: String): Unit =
      val p = tree.resolve(s"core/src/main/$root/com/histmersion/$path")
      Files.createDirectories(p.getParent)
      val _ = Files.writeString(p, text, UTF_8)
    val code =
      "package com.histmersion.x\n\nfinal case class Pair(a: Int)\nobject Use:\n  val b = Pair(1).copy(a = 2)\n"
    write("scalajvm", "x/Pair.scala", code)
    write("scalajs", "x/Pair.scala", code)
    write("scala", "y/Pair.scala", "package com.histmersion.y\n\nfinal case class Pair(a: Int)\n")
    val found =
      try SourceFile.all("core", modules = tree)
      finally Files.walk(tree).iterator.asScala.toList.reverse.foreach(Files.delete)
    assertEquals(found.map(_.key), List("core:scalajs:x/Pair.scala", "core:scalajvm:x/Pair.scala", "core:y/Pair.scala"))
    val rows = Rules.all(found).map(_.row).distinct.sorted
    assertEquals(
      rows,
      List(
        "concept\tlayer core\tPair in core:scalajs:x/Pair.scala core:scalajvm:x/Pair.scala core:y/Pair.scala\t2",
        "copy\tcore:scalajs:x/Pair.scala\t.copy(\t1",
        "copy\tcore:scalajvm:x/Pair.scala\t.copy(\t1",
        "one-type\tcore:scalajs:x/Pair.scala\tPair Use\t2",
        "one-type\tcore:scalajvm:x/Pair.scala\tPair Use\t2"
      ),
      "the platform pair is one definition of Pair (with y's, two), but each copy's own violations count"
    )
    assertEquals(ArchitectureSuite.Ratchet.totals(rows), Map("concept" -> 2, "copy" -> 2, "one-type" -> 4))

  test("a type named for an entity and a property is the property, a component of the entity is not"):
    val entities = Set("Approval", "Source", "PeriodO", "Citekey")
    assertEquals(Rules.propertyOf("ApprovalKind", entities), Some("Approval"))
    assertEquals(Rules.propertyOf("SourceService", entities), None)
    assertEquals(Rules.propertyOf("CitekeyRule", entities), None)
    assertEquals(Rules.propertyOf("PeriodODataset", entities), Some("PeriodO"))
    assertEquals(Rules.propertyOf("Sources", entities), None)

object ContractsSuite:

  /** The modules G60 has classified: none of their sites builds `Problem.Unclassified` any more (step 2 the leaf
    * layers, step 3 the plugin host, the testkit and the bundled plugins, step 4 the api but [[UnclassifiedKept]]; each
    * later step adds its own).
    */
  val Classified: List[String] =
    List("values", "config", "files", "plugin-api", "plugin-resolver", "upstreams", "plugin-vaults") ++
      List("plugins/events", "plugins/fetchers", "plugins/gazetteers") ++
      List("plugin-host", "plugin-testkit", "plugins/media", "plugins/calibre", "plugins/ai-http") ++
      List(
        "plugin-fixtures/hello",
        "plugin-fixtures/hello-ai",
        "plugin-fixtures/hello-deps",
        "plugin-fixtures/hello-lib"
      ) ++
      List("api")

  /** The sites a classified module keeps until G60 step 7 removes the temporary case, by file, exactly: the closed
    * Problem's own renderer in the api, which must name every case (the wire entry, which a stored column keeps too,
    * and its codecs). RenderSuite's row for the case is a string literal, which the check does not see.
    */
  val UnclassifiedKept: Map[String, Int] = Map("api:api/problem/Answer.scala" -> 4)

  /** The modules whose code writes SQL. */
  val SqlModules: List[String] = List("db", "server")

  val Modules: List[String] = List(
    "problem",
    "json",
    "values",
    "api",
    "config",
    "files",
    "core",
    "db",
    "upstreams",
    "plugin-api",
    "plugin-host",
    "plugin-resolver",
    "plugin-vaults",
    "chimney-iron",
    "server",
    "cli",
    "web",
    "plugins/ai-http",
    "plugins/calibre",
    "plugins/events",
    "plugins/fetchers",
    "plugins/gazetteers",
    "plugins/media"
  )

  /** The foreign types whose `CanEqual` is defined in two files, because their users share no module that could hold it
    * (G67): the server's config and the vault plugin (on the plugin SPI alone, which the config does not see) both
    * compare HOCON value kinds; the server and the plugin resolver (a tool of the relink kit, never on the server's
    * class path, below the config) both compare paths.
    */
  val CrossLayerEqualities: Map[String, List[String]] = Map(
    "ConfigValueType" -> List("config:config/edit/SettingKind.scala", "plugin-vaults:plugins/vaults/YubiKey.scala"),
    "Path" -> List("config:config/UserPaths.scala", "plugin-resolver:pluginresolver/DependencyResolver.scala")
  )

  /** The layer a module belongs to: a concept may have one type per layer. */
  def layerOf(module: String): String = module match
    case "api" | "web"                 => "api"
    case "db"                          => "db"
    case "cli"                         => "cli"
    case "plugin-api"                  => "plugin"
    case m if m.startsWith("plugins/") => m
    case "values" | "core" | "config" | "files" | "upstreams" | "server" | "chimney-iron" | "plugin-host" |
        "plugin-resolver" | "plugin-vaults" =>
      "core"
    case m => m

  final case class Signature(name: String, at: Int, params: List[(String, String)], result: String)

  object Rules:
    def all(sources: List[SourceFile]): List[Violation] =
      val concepts = newtypes(sources)
      sources.flatMap(s =>
        primitive(s, concepts) ++ unwrap(s) ++ derives(s) ++ copies(s) ++ oneType(s) ++ transaction(s) ++
          global(s) ++ service(s) ++ layer(s) ++ clientRefined(s) ++ bucket(s) ++
          circe(s) ++
          (if scallopAdapter(s.key) then Nil
           else
             counted(
               "scallop",
               s,
               "scallop outside the command-line adapters",
               Scallop.findAllMatchIn(s.code).map(_.start).toList
             )) ++
          counted(
            "nonempty-pattern",
            s,
            "a type pattern on NonEmptyVector outside the problem module",
            if module(s) == "problem" then Nil else NonEmptyPattern.findAllMatchIn(s.code).map(_.start).toList
          )
          ++ counted(
            "unrecognised",
            s,
            "Stored.Unrecognised built outside the stored column's reader",
            if Unrecognisers(s.key) then Nil
            else Unrecognised.findAllMatchIn(s.code).filterNot(m => inPattern(s.code, m.start)).map(_.start).toList
          )
          ++ counted(
            "relayed",
            s,
            "Problem.Relayed built outside the browser's decoder",
            if Relayers(s.key) then Nil
            else Relayed.findAllMatchIn(s.code).filterNot(m => inPattern(s.code, m.start)).map(_.start).toList
          )
          ++ counted(
            "first-problem",
            s,
            "the first of several problems kept, the rest dropped",
            if FirstProblemPickers(s.key) then Nil else FirstProblem.findAllMatchIn(s.code).map(_.start).toList
          )
          ++ counted(
            "html-sink",
            s,
            "a DOM markup sink written to outside the one that takes Html",
            if s.key == HtmlSinkFile then Nil else HtmlSinkWrite.findAllMatchIn(s.code).map(_.start).toList
          )
          ++ counted(
            "html-newtype",
            s,
            "an …Html newtype over String: markup any text builds, beside api.Html",
            HtmlNewtype.findAllMatchIn(s.code).map(_.start).toList
          )
      ) ++ concept(sources) ++ helper(sources) ++ prefix(sources) ++ problemFields(sources)

    private def module(s: SourceFile): String = s.key.takeWhile(_ != ':')
    private def baseName(s: SourceFile): String = s.key.split('/').last.stripSuffix(".scala")

    private def counted(rule: String, s: SourceFile, detail: String, hits: List[Int]): List[Violation] =
      hits.headOption.toList.map(h => Violation(rule, s.key, detail, hits.size, s"${s.key}:${s.lineOf(h)}"))

    private val Newtype =
      "\\bobject\\s+([A-Z]\\w*)\\s+extends\\s+Refined(?:Sub)?Type\\b|\\bopaque\\s+type\\s+([A-Z]\\w*)".r

    /** The newtypes' names, in every module (a concept with a newtype anywhere has one). */
    def newtypes(sources: List[SourceFile]): Set[String] =
      sources
        .flatMap(s =>
          Newtype.findAllMatchIn(s.code).map { m =>
            val name = Option(m.group(1)).getOrElse(m.group(2))
            val line = s.code.substring(s.code.lastIndexOf('\n', m.start) + 1, m.start)
            if line.isBlank && line.nonEmpty then
              Enclosing
                .findAllMatchIn(s.code.substring(0, m.start))
                .toList
                .lastOption
                .fold(name)(o => o.group(1) + name)
            else name
          }
        )
        .toSet - "X" - "T"

    /** The top-level object a nested newtype is defined in: `Run.Id` is the concept `RunId`. */
    private val Enclosing = "(?m)^(?:(?:final|private|case)(?:\\[\\w+\\])?\\s+)*object\\s+([A-Z]\\w*)".r

    /** The newtype a name stands for: the longest newtype name the name (a plural taken as its singular) ends with,
      * ignoring case — `sourceDoi` is a `Doi`, `goalSlug` a `GoalSlug`.
      */
    def conceptOf(name: String, concepts: Set[String]): Option[String] =
      val forms = List(name, name.stripSuffix("s")).distinct
      concepts.toList
        .filter(c => forms.exists(f => f.equalsIgnoreCase(c) || endsWithWord(f, c)))
        .sortBy(-_.length)
        .headOption

    /** `sourceDoi` ends with the word `Doi`; `radioid` does not end with `Id`. */
    private def endsWithWord(name: String, concept: String): Boolean =
      val at = name.length - concept.length
      at > 0 && name.substring(at).equalsIgnoreCase(concept) && name(at).isUpper

    private val Def = "\\bdef\\s+(\\w+)".r
    private val Member = "^  (?:(?:final|override|inline|transparent)\\s+)*$".r
    private val Param = "(\\w+)\\s*:\\s*(?:=>\\s*)?([^,()]*(?:\\([^()]*\\)[^,()]*)?)".r

    /** The public members (`def`s indented once, so of a top-level type) of a text: name, offset, parameters (name,
      * type) and result type.
      */
    def signatures(code: String): List[Signature] =
      Def
        .findAllMatchIn(code)
        .filter(m => Member.matches(code.substring(code.lastIndexOf('\n', m.start) + 1, m.start)))
        .map { m =>
          val n = code.length
          var i = m.end
          var depth = 0
          val params = List.newBuilder[(String, String)]
          val chunk = new StringBuilder
          var stop = false
          while i < n && !stop do
            val c = code(i)
            if c == '(' || c == '[' then
              if depth == 0 && c == '(' then chunk.clear() else chunk += c
              depth += 1
            else if c == ')' || c == ']' then
              depth -= 1
              if depth == 0 && c == ')' then
                Param.findAllMatchIn(chunk.toString).foreach(p => params += (p.group(1) -> p.group(2).trim))
              else chunk += c
            else if depth == 0 && c == '\n' && continues(code, i) then chunk += c
            else if depth == 0 && (c == '=' && !code.startsWith("=>", i) || c == '\n' || c == '{') then stop = true
            else chunk += c
            i += 1
          val tail = code.substring(m.end, i - 1).replaceAll("\\)\\s*:", "):")
          val result = tail.lastIndexOf("):") match
            case -1 if !tail.trim.startsWith("(") && tail.contains(":") => tail.substring(tail.indexOf(':') + 1).trim
            case -1                                                     => ""
            case k                                                      => tail.substring(k + 2).trim
          Signature(m.group(1), m.start, params.result(), result)
        }
        .toList

    /** A signature broken over lines goes on: the next line starts with its result type (`: T`) or a parameter list. */
    private def continues(code: String, newline: Int): Boolean =
      val next = code.indexWhere(c => !c.isWhitespace, newline)
      next >= 0 && (code(next) == '(' || code(next) == ':' && !code.startsWith("::", next))

    private val Primitive =
      "^(?:(?:Option|List|Set|Seq|Vector|Iterable|NonEmptyList|Chunk)\\[)?(?:String|Int|Long)\\]?$".r

    private def isContractFile(s: SourceFile): Boolean =
      val b = baseName(s)
      b.endsWith("Service") || b.endsWith("Repo") || b.endsWith("Controllers") || b.endsWith("Controller")

    private def primitive(s: SourceFile, concepts: Set[String]): List[Violation] =
      if !isContractFile(s) then Nil
      else
        val hits = signatures(s.code).flatMap { sig =>
          val ps = sig.params.collect {
            case (name, tpe) if Primitive.matches(tpe) && conceptOf(name, concepts).isDefined => sig.at
          }
          val r = if Primitive.matches(sig.result) && conceptOf(sig.name, concepts).isDefined then List(sig.at) else Nil
          ps ++ r
        }
        counted("primitive", s, "String/Int/Long for a concept with a newtype", hits)

    private val BoundaryModules = Set("db", "cli", "web", "config")
    private val BoundaryNames =
      "(?:Routes|Endpoints|Endpoint|Tools|Codec|Codecs|Repo|Sql|Markdown|Json|Wiring|Transformers|Conversions|Api|Main|Cli|Commands)$".r

    private def isBoundary(s: SourceFile): Boolean =
      BoundaryModules(module(s)) || module(s).startsWith("plugins/") ||
        BoundaryNames.findFirstIn(baseName(s)).isDefined || Newtype.findFirstIn(s.code).isDefined

    private def unwrap(s: SourceFile): List[Violation] =
      if isBoundary(s) then Nil
      else counted("unwrap", s, ".value outside a boundary", "\\.value\\b".r.findAllMatchIn(s.code).map(_.start).toList)

    private val TopLevel =
      "(?m)^(?:(?:final|sealed|abstract|private|protected|open|case|implicit|transparent|inline|opaque)(?:\\[\\w+\\])?\\s+)*(class|trait|object|enum|type)\\s+([A-Z]\\w*)".r

    /** A file's top-level definitions: (keyword, name, offset). */
    private def topLevel(s: SourceFile): List[(String, String, Int)] =
      TopLevel.findAllMatchIn(s.code).map(m => (m.group(1), m.group(2), m.start)).toList

    private def concept(sources: List[SourceFile]): List[Violation] =
      sources
        .flatMap(s => topLevel(s).map((_, name, at) => (layerOf(module(s)), name, s, at)))
        .groupBy(d => (d._1, d._2))
        .toList
        .collect {
          case ((layer, name), defs) if defs.map(_._3.platformless).distinct.size > 1 =>
            val files = defs.map(_._3.key).distinct.sorted
            val (_, _, s, at) = defs.minBy(_._3.key)
            Violation(
              "concept",
              s"layer $layer",
              s"$name in ${files.mkString(" ")}",
              defs.map(_._3.platformless).distinct.size,
              s"${s.key}:${s.lineOf(at)}"
            )
        }

    private val Codecs =
      "(?:JsonValueCodec|JsonKeyCodec|JsonCodec|Encoder|Decoder|Codec|Schema|KeyEncoder|KeyDecoder|DbCodec|ConfigReader|Kindlings\\w*)"

    /** The one file per library that speaks circe (Chimp, sttp-ai) where it meets our codecs. */
    private val CirceAdapters = Set(
      "server:util/mcp/ChimpJson.scala",
      "server:domain/automation/agents/SttpAiJson.scala"
    )
    private val Circe =
      "\\bio\\.circe\\b|\\bsttp\\.tapir\\.json\\.circe\\b|\\bsttp\\.apispec\\.circe\\b|\\bcircederivation\\b".r

    /** The CLI's command-line core and its per-group adapters: the only code that touches scallop. */
    def scallopAdapter(key: String): Boolean =
      key == "cli:cli/CommandLine.scala" || key.startsWith("cli:cli/commandline/")
    private val Scallop =
      "\\bScallopConf(?:Base)?\\b|\\bSubcommand\\b|(?<!def\\s)\\bopt\\[|(?<!def\\s)\\btrailArg\\[|\\baddSubcommand\\(|\\borg\\.rogach\\b".r

    private def circe(s: SourceFile): List[Violation] =
      if CirceAdapters(s.key) then Nil
      else counted("circe", s, "circe outside a listed adapter", Circe.findAllMatchIn(s.code).map(_.start).toList)
    private val HandGiven = ("(?m)^\\s*given\\s+(?:\\w+\\s*:\\s*)?" + Codecs + "\\[").r
    private val GivenStart =
      "(?m)^\\s*(?:(?:inline|transparent|private|protected|override)(?:\\[\\w+\\])?\\s+)*given\\b".r
    private val Generic = "^given\\s+(?:\\w+\\s*)?(?::\\s*)?[\\[(]".r
    private val CodecResult = ("^\\s*" + Codecs + "\\[").r

    /** A generic or polymorphic given of a codec type class — `given x: [A] => (c: JsonValueCodec[A]) =>
      * JsonValueCodec[F[A]]`, `given [A](using Codec[A]): Codec[List[A]]`, `given x[A: Schema]: Schema[F[A]]`, an
      * `inline given` too — whose result type (after its last `=>`, or its last `:`, outside brackets and parentheses)
      * is the codec.
      */
    def genericCodecGivens(code: String): List[Int] =
      GivenStart
        .findAllMatchIn(code)
        .flatMap { m =>
          val h = header(code, m.end - "given".length)
          Option.when(Generic.findFirstIn(h).isDefined && CodecResult.findFirstIn(resultType(h)).isDefined)(m.start)
        }
        .toList

    /** The text after a header's last `=>` or `:` at depth 0 (the whole header when it has neither). */
    private def resultType(h: String): String =
      var depth = 0
      var last = 0
      var i = 0
      while i < h.length do
        val c = h(i)
        if c == '(' || c == '[' then depth += 1
        else if c == ')' || c == ']' then depth -= 1
        else if depth == 0 && h.startsWith("=>", i) then
          last = i + 2
          i += 1
        else if depth == 0 && c == ':' then last = i + 1
        i += 1
      h.substring(last)

    /** A `.derived` call of a codec, schema or config type class — `CanEqual.derived` is none of them (G67): CanEqual
      * only lets the compiler compare, so a hand-written CanEqual given is not a codec given written by hand.
      */
    val Derived = "(?<!\\bCanEqual)\\.derived\\b".r

    /** The JSON module is the codec library itself: its givens are the base instances (a string, a list) that every
      * derivation builds on, as circe's own were.
      */
    private def derives(s: SourceFile): List[Violation] =
      if module(s) == "json" then Nil
      else
        val hits =
          HandGiven.findAllMatchIn(s.code).map(_.start) ++ genericCodecGivens(s.code) ++
            Derived.findAllMatchIn(s.code).map(_.start)
        counted("derives", s, "codec given written by hand", hits.toList.distinct.sorted)

    private val EqualityGiven =
      "(?m)^[ \\t]*(?:(?:private|protected|inline)(?:\\[\\w+\\])?\\s+)*given\\s+(?:\\w+\\s*:\\s*)?CanEqual\\[".r

    /** A type argument without its package (`java.util.UUID` is `UUID`) or spaces. */
    private def simpleType(t: String): String = t.replaceAll("\\s", "").replaceFirst("^(?:[a-z]\\w*\\.)+", "")

    /** The type arguments of the brackets opened just before `from`, split at their top-level commas. */
    private def typeArgs(code: String, from: Int): List[String] =
      @tailrec def loop(i: Int, depth: Int, start: Int, acc: List[String]): List[String] =
        if i >= code.length then Nil
        else
          code(i) match
            case '[' | '('         => loop(i + 1, depth + 1, start, acc)
            case ']' if depth == 0 => (code.substring(start, i) :: acc).reverse
            case ']' | ')'         => loop(i + 1, depth - 1, start, acc)
            case ',' if depth == 0 => loop(i + 1, depth, i + 1, code.substring(start, i) :: acc)
            case _                 => loop(i + 1, depth, start, acc)
      loop(from, 0, from, Nil)

    /** Each `given CanEqual[X, X]` of one type with itself: the type (its package left out) and where the given starts.
      * A lifted one (`given [A] => CanEqual[F[A], F[A]]`) is a type class's, not a foreign type's, and is not one.
      */
    def equalityGivens(code: String): List[(String, Int)] =
      EqualityGiven
        .findAllMatchIn(code)
        .flatMap(m =>
          typeArgs(code, m.end).map(simpleType) match
            case List(a, b) if a == b => Some(a -> m.start)
            case _                    => None
        )
        .toList

    /** Each type whose `CanEqual[X, X]` is given in more than one file: the type and those files. The JVM's and the
      * Scala.js copy of one file are one definition: they are never on one class path together.
      */
    def equalityTwice(sources: List[SourceFile]): Map[String, List[String]] =
      sources
        .flatMap(s => equalityGivens(s.code).map((t, _) => t -> s))
        .groupMap(_._1)(_._2)
        .collect {
          case (t, files) if files.map(_.platformless).distinct.size > 1 => t -> files.map(_.key).distinct.sorted
        }

    private def copies(s: SourceFile): List[Violation] =
      counted("copy", s, ".copy(", "\\.copy\\(".r.findAllMatchIn(s.code).map(_.start).toList)

    /** A definition's header, however it is broken over lines: from its keyword to its body (`:` ending a line, `{`,
      * `=`) or to the next line that does not continue it (a blank line, or one that starts at the margin).
      */
    private def header(code: String, at: Int): String =
      val n = code.length
      def restBlank(from: Int): Boolean =
        val k = code.indexWhere(c => c == '\n' || !c.isWhitespace, from)
        k < 0 || code(k) == '\n'
      def ends(i: Int): Boolean = code(i) match
        case '{'  => true
        case '='  => !code.startsWith("=>", i) && !code.startsWith("==", i)
        case ':'  => restBlank(i + 1)
        case '\n' => i + 1 >= n || !code(i + 1).isWhitespace || restBlank(i + 1)
        case _    => false
      var i = at
      var depth = 0
      while i < n && !(depth == 0 && ends(i)) do
        if code(i) == '(' || code(i) == '[' then depth += 1
        else if code(i) == ')' || code(i) == ']' then depth -= 1
        i += 1
      code.substring(at, i)

    private def oneType(s: SourceFile): List[Violation] =
      val defs = topLevel(s)
      val sealedNames = defs.collect { case (k, n, at) if s.code.startsWith("sealed", at) => n }.toSet
      val enums = defs.collect { case ("enum", n, _) => n }.toSet
      val parents = sealedNames ++ enums
      val cases = defs.collect {
        case (_, n, at)
            if parents.exists(p => ("\\bextends\\s+" + p + "\\b").r.findFirstIn(header(s.code, at)).isDefined) &&
              !parents(n) =>
          n
      }.toSet
      val all = defs.map(_._2).distinct
      val constraints = all.filter(n => n.endsWith("Rule") && all.contains(n.stripSuffix("Rule")))
      val names = all.filterNot(cases).filterNot(constraints.contains)
      if names.size <= 1 then Nil
      else List(Violation("one-type", s.key, names.mkString(" "), names.size, s"${s.key}:${s.lineOf(defs(1)._3)}"))

    private def transaction(s: SourceFile): List[Violation] =
      if !baseName(s).endsWith("Repo") then Nil
      else
        counted(
          "transaction",
          s,
          "opens a connection or transaction",
          "(?<![\\w.])(?:transact|connect)\\(|\\bTransactor\\b|\\.getConnection\\b".r
            .findAllMatchIn(s.code)
            .map(_.start)
            .toList
        ) ++ counted(
          "transaction",
          s,
          "takes a DataSource",
          signatures(s.code).filter(_.params.exists(_._2.contains("DataSource"))).map(_.at)
        )

    private val Globals: List[(String, scala.util.matching.Regex)] = List(
      "environment" -> "\\bSystem\\.getenv\\b|\\bsys\\.env\\b".r,
      "system property" -> "\\bSystem\\.getProperty\\b|\\bsys\\.props\\b|\\bSystem\\.getProperties\\b".r,
      "wall clock" -> "\\bSystem\\.currentTimeMillis\\b|\\b(?:Instant|LocalDate|LocalDateTime|OffsetDateTime|ZonedDateTime)\\.now\\(\\s*\\)|\\bClock\\.system\\w*".r
    )
    private val RawGlobals = "\"user\\.(?:home|dir)\"".r

    private def global(s: SourceFile): List[Violation] =
      val entry = baseName(s) == "SystemContext" || "@main\\s+def".r.findFirstIn(s.code).isDefined
      if entry then Nil
      else
        Globals.flatMap((kind, re) => counted("global", s, kind, re.findAllMatchIn(s.code).map(_.start).toList)) ++
          counted("global", s, "home or working directory", RawGlobals.findAllMatchIn(s.raw).map(_.start).toList)

    private val ServiceTrait = "(?m)^(?:sealed\\s+)?trait\\s+(\\w+Service)\\b".r

    private def service(s: SourceFile): List[Violation] =
      ServiceTrait.findAllMatchIn(s.code).toList.flatMap { m =>
        val missing =
          List("Implementation", "InMemory").filterNot(c => s"\\bclass\\s+$c\\b".r.findFirstIn(s.code).isDefined)
        if missing.isEmpty then Nil
        else
          List(
            Violation(
              "service",
              s.key,
              s"${m.group(1)} without ${missing.mkString(" and ")}",
              1,
              s"${s.key}:${s.lineOf(m.start)}"
            )
          )
      }

    private val HelperSuffix = "^([A-Z]\\w*?)(s|Ops|Utils|Util|Helpers|Helper|Support|Syntax|Functions)$".r

    private def helper(sources: List[SourceFile]): List[Violation] =
      val types = sources.flatMap(s => topLevel(s).collect { case (k, n, _) if k != "object" => n }).toSet
      sources.flatMap { s =>
        val defs = topLevel(s)
        val own = defs.collect { case (k, n, _) if k != "object" => n }.toSet
        defs.collect {
          case ("object", name @ HelperSuffix(stem, _), at) if !own(name) && types(stem) && !types(name) =>
            Violation("helper", s.key, s"$name about $stem", 1, s"${s.key}:${s.lineOf(at)}")
        }
      }

    private val ClientModules = Set("api", "web")
    private val DomainModules = Set("core", "db", "server", "files", "config", "plugin-host")
    private val Boundary = "(?:Routes|Endpoints|Endpoint|Tools|Transformers|Transformer|Controllers|Controller)$".r
    private val Named = "\\bcom\\.histmersion\\.(\\w+)".r

    /** The closed Problem's module, below the api: the contract's one failure type, which the clients share. */
    private val SharedProblem = "com.histmersion.util.problem."

    /** The JSON module, below the api: the codecs and raw JSON every client and plugin shares (G62). */
    private val SharedJson = "com.histmersion.util.json."

    /** The non-empty vector's module, below the problem module (G66): the type `Problems` and the contract's lists of
      * at least one element are made of, which the clients share as they share the problem module.
      */
    private val SharedNonEmpty = "com.histmersion.util.nonempty."

    /** The closed Problem's renderers (the sentence, the answer, the stored column): the problem module's edge, kept in
      * the api only because every layer that renders sees it; a domain file may render a problem through them.
      */
    private val ProblemRenderers = "com.histmersion.api.problem."

    private def layer(s: SourceFile): List[Violation] =
      val m = module(s)
      if ClientModules(m) then
        val outside = Named
          .findAllMatchIn(s.code)
          .filterNot(n =>
            n.group(1) == "api" || n.group(1) == "web" || s.code.startsWith(SharedProblem, n.start) ||
              s.code.startsWith(SharedJson, n.start) || s.code.startsWith(SharedNonEmpty, n.start)
          )
        val own = if m == "api" && !s.pkg.startsWith("com.histmersion.api") then List(0) else Nil
        counted("layer", s, "the client names a package outside api", outside.map(_.start).toList.take(1)) ++
          counted("layer", s, "an api file outside com.histmersion.api", own)
      else if DomainModules(m) && s.pkg.startsWith("com.histmersion.domain") &&
        Boundary.findFirstIn(baseName(s)).isEmpty
      then
        counted(
          "layer",
          s,
          "a domain file names the api outside a boundary",
          "\\bcom\\.histmersion\\.api\\b".r
            .findAllMatchIn(s.code)
            .map(_.start)
            .filterNot(s.code.startsWith(ProblemRenderers, _))
            .toList
            .take(1)
        )
      else Nil

    /** A type pattern on the problems' non-empty vector (`case Left(ps: NonEmptyVector[Problem])`): the union
      * `Problems` is never matched on, erasure makes the test unchecked; `fold` and `toVector` tell one from many.
      */
    private val NonEmptyPattern = "\\bcase\\b[^\\n]*?:\\s*NonEmptyVector\\[[^\\n]*?=>".r

    private val RefinedModules = Set("api", "web", "cli", "plugin-api")

    /** The client types that judge validity because their client is the authority: the CLI manages the local plugins
      * (`histmersion plugin …`) with no server to ask, so it alone checks a plugin's id, its repository and the Scala
      * package its scaffold writes.
      */
    val ClientAuthorities: Set[String] =
      Set("cli:cli/PluginId.scala", "cli:cli/PluginRepository.scala", "cli:cli/ScalaPackage.scala")
    private val Refinement = "\\bRefined(?:Sub)?Type\\s*\\[[^,]+,\\s*(\\w+)|:\\|\\s*(\\w+)".r

    private def clientRefined(s: SourceFile): List[Violation] =
      if !RefinedModules(module(s)) || ClientAuthorities(s.key) then Nil
      else
        val hits = Refinement
          .findAllMatchIn(s.code)
          .filter(m => Option(m.group(1)).getOrElse(m.group(2)) != "Pure")
          .map(_.start)
          .toList
        counted("client-refined", s, "a refined constraint in a client", hits)

    private val Buckets = Set(
      "com.histmersion.db",
      "com.histmersion.files",
      "com.histmersion.values",
      "com.histmersion.upstreams"
    )

    private def bucket(s: SourceFile): List[Violation] =
      if Buckets(s.pkg) then counted("bucket", s, s"package ${s.pkg}", List(0)) else Nil

    /** The file of the closed Problem, its cases and the small types they hold. */
    val ProblemFile = "problem:util/problem/Problem.scala"

    private val CaseWithFields = "\\bcase\\s+(?:class\\s+)?([A-Z]\\w*)\\s*\\(".r
    private val PrimitiveField =
      "^(?:(?:Option|List|Set|Seq|Vector|NonEmptyVector)\\[)?(?:String|Int|Long|Double|Boolean|Legacy\\.Text)\\]?$".r

    /** The files that may build `Stored.Unrecognised`: the reader of what a master version stored and the cases' own
      * file.
      */
    private val Unrecognisers: Set[String] = Set(ProblemFile, "api:api/problem/StoredShapes.scala")
    private val Unrecognised = "\\bUnrecognised\\s*\\(".r

    /** One problem picked from several where every one should be kept (a job's stored problems): `.toNonEmpty.head`,
      * the head of a `problems` list, of the problems' sentences, or of a non-empty vector's elements.
      */
    private val FirstProblem =
      ("\\.toNonEmpty\\s*\\.\\s*head\\b|\\b[pP]roblems\\s*\\.\\s*head(?:Option)?\\b|" +
        "\\.sentences\\s*\\.\\s*head(?:Option)?\\b|\\bNonEmptyVector\\.toVector\\([^)\\n]*\\)\\s*\\.\\s*head(?:Option)?\\b").r

    /** The files that may pick the first problem, each for a reason that keeps every problem elsewhere: the answer's
      * HTTP status is the first problem's (`Answer.status`; its body lists every problem).
      */
    private val FirstProblemPickers: Set[String] = Set("api:api/problem/Answer.scala")

    /** A write of markup into the page's DOM: `innerHTML =`, `outerHTML =`, `insertAdjacentHTML(`, a popup's
      * `.setHTML(`, `document.write(`. Only the web page's one sink file does it, and it takes an `Html`, which only
      * escaping, literal markup or the allow-list makes: no raw String reaches a sink.
      */
    private val HtmlSinkWrite =
      "\\.(?:innerHTML|outerHTML)\\s*=(?!=)|\\binsertAdjacentHTML\\s*\\(|\\.setHTML\\s*\\(|\\bdocument\\.write(?:ln)?\\s*\\(".r

    private val HtmlSinkFile = "web:web/HtmlSink.scala"

    /** A newtype named `…Html` over String: `X(s)` takes any text as markup. The one markup type is `api.Html`, which
      * only escaping, literal markup or the allow-list builds.
      */
    private val HtmlNewtype =
      "\\bobject\\s+\\w*Html\\s+extends\\s+(?:Newtype|Refined(?:Sub)?Type)\\s*\\[\\s*String\\b".r

    /** The files that may build `Problem.Relayed`: the browser's decoder (the Scala.js copy only, not the JVM's
      * `ProblemJson` at the same path), the shared entry builder it needs and the cases' own file.
      */
    private val Relayers: Set[String] = Set(
      ProblemFile,
      "api:api/problem/Answer.scala",
      "api:scalajs:api/problem/ProblemJson.scala"
    )
    private val Relayed = "\\bRelayed\\s*\\(".r

    /** Is `at` inside a `case … =>` pattern on its line (a match, not a construction)? */
    private def inPattern(code: String, at: Int): Boolean =
      val start = code.lastIndexOf('\n', at) + 1
      val end = code.indexOf('\n', at) match
        case -1 => code.length
        case n  => n
      val line = code.substring(start, end)
      line.trim.startsWith("case ") && line.indexOf("=>") > at - start

    private val UnclassifiedSite = "\\b(Unclassified|legacyText|Legacy)\\b".r

    /** Every unclassified site of the files: the temporary case, its text carrier, the accessor of its words. */
    /** The parameters with a default of every case (`case class X(…)`, an enum's `case X(…)`) in the sources. */
    def caseDefaults(sources: List[SourceFile]): List[Violation] =
      sources.flatMap { s =>
        CaseWithFields.findAllMatchIn(s.code).toList.flatMap { m =>
          val close = (m.end until s.code.length)
            .foldLeft((1, -1)) { case ((depth, at), i) =>
              if at >= 0 then (depth, at)
              else
                s.code(i) match
                  case '(' => (depth + 1, at)
                  case ')' => if depth == 1 then (0, i) else (depth - 1, at)
                  case _   => (depth, at)
            }
            ._2
          val params = if close < 0 then "" else s.code.substring(m.end, close)
          Param
            .findAllMatchIn(params)
            .filter(_.group(2).contains('='))
            .map(p => Violation("default", s.key, s"${m.group(1)}.${p.group(1)}", 1, s"${s.key}:${s.lineOf(m.start)}"))
            .toList
        }
      }

    def unclassified(sources: List[SourceFile]): List[Violation] =
      sources.flatMap(s =>
        UnclassifiedSite
          .findAllMatchIn(s.code)
          .map(m => Violation("unclassified", s.key, m.group(1), 1, s"${s.key}:${s.lineOf(m.start)}"))
      )

    /** The fields (name, type) of every case with fields in a text: `case class X(…)` and an enum's `case X(…)`. */
    def caseFields(code: String): List[(String, String, String, Int)] =
      CaseWithFields
        .findAllMatchIn(code)
        .toList
        .flatMap { m =>
          val close = code.indexOf(')', m.end)
          val params = if close < 0 then "" else code.substring(m.end, close)
          Param
            .findAllMatchIn(params)
            .map(p => (m.group(1), p.group(1), p.group(2).takeWhile(_ != '=').trim, m.start))
            .toList
        }

    private def problemFields(sources: List[SourceFile]): List[Violation] =
      sources
        .filter(_.key == ProblemFile)
        .flatMap(s =>
          caseFields(s.code).collect {
            case (name, field, tpe, at) if PrimitiveField.matches(tpe) =>
              Violation("problem-field", s.key, s"$name.$field: $tpe", 1, s"${s.key}:${s.lineOf(at)}")
          }
        )

    /** What works on an entity rather than describing it: `SourceService` is no property of `Source`. */
    val Components: List[String] = List(
      "Service",
      "Repo",
      "Controllers",
      "Controller",
      "Routes",
      "Tools",
      "Endpoints",
      "Endpoint",
      "Api",
      "Markdown",
      "Wiring",
      "Codecs",
      "Codec",
      "Client",
      "Fixtures",
      "JobKinds",
      "Jobs",
      "Data",
      "MergeRules",
      "Rules",
      "Referrer",
      "Carried",
      "Exchange",
      "Workflows",
      "Workflow",
      "Sql",
      "Plan",
      "Site",
      "Plugin",
      "Collection",
      "Search",
      "Imports",
      "Importer",
      "Importers",
      "Policy",
      "Pipeline",
      "Contributions",
      "Reader",
      "Writer",
      "Finder",
      "Resolver",
      "Converter",
      "Transport",
      "Store",
      "Cmd",
      "Views",
      "Html",
      "Page",
      "Pages"
    )
    private val Word = "[A-Z][a-z0-9]".r

    /** The entity a type's name starts with and the property it adds, when it is one (`ApprovalKind` → `Approval`). */
    def propertyOf(name: String, entities: Set[String]): Option[String] =
      (1 until name.length).iterator
        .filter(i => Word.matches(name.substring(i, math.min(name.length, i + 2))))
        .map(i => (name.take(i), name.drop(i)))
        .find((entity, property) => entities(entity) && !Components.exists(property.endsWith) && property != "Rule")
        .map(_._1)

    private def prefix(sources: List[SourceFile]): List[Violation] =
      val defs = sources.flatMap(s => topLevel(s).map((_, name, at) => ((layerOf(module(s)), s.pkg), name, s, at)))
      val byPackage = defs.groupBy(_._1).view.mapValues(_.map(_._2).toSet).toMap
      defs
        .filterNot(d => module(d._3) == "web")
        .flatMap { (place, name, s, at) =>
          propertyOf(name, byPackage(place)).map(entity =>
            Violation("prefix", s.key, s"$name → $entity.${name.drop(entity.length)}", 1, s"${s.key}:${s.lineOf(at)}")
          )
        }
        .distinctBy(_.row)
