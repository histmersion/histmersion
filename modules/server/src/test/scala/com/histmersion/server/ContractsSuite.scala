package com.histmersion
package server

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

import com.histmersion.server.ArchitectureSuite.{Blank, SourceFile, Violation}

/** The coding practices of AGENTS.md (rules 1–14) that a reading of the sources can check, over every main module:
  *
  *   - `primitive` (rules 1, 3): a public method of a service, repository or controller whose parameter or result is a
  *     `String`/`Int`/`Long` (alone or in an `Option`/collection) named for a concept that has a newtype;
  *   - `unwrap` (rules 1, 4): `.value` outside boundary code (the db, cli, web and config modules; routes, endpoints,
  *     tools, codecs, repositories, SQL, Markdown, JSON, wiring and transformer files; a newtype's own file);
  *   - `concept` (rule 3): a top-level type name defined in more than one file of one layer;
  *   - `derives` (rule 7): a codec, schema or config given written by hand, or a `.derived` call;
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
  *   - `unrecognised` (G60, no exception allowed): `Stored.Unrecognised` built in main code anywhere but the stored
  *     column's reader (`api.problem.Column`) and the cases' own file — a sentence no case writes is read, never made.
  *
  * Besides the listed rules, two hold with no exception at all: a module G60 has classified ([[Classified]]) holds no
  * `Unclassified`, `legacyText` or `Legacy.Text`, in its main or its test code; and no case of the problem module has a
  * default parameter — a caller states every field (an empty path through a named constructor such as `here`), so no
  * value is filled in that the caller did not choose.
  *
  * The violations that exist today are listed in `architecture/contracts.tsv`, exact both ways (a new violation fails,
  * a row nothing violates any more fails, a count must match); each step of the refactor shrinks the list.
  * `HISTMERSION_UPDATE_ARCHITECTURE=1` rewrites it from the sources.
  */
class ContractsSuite extends munit.FunSuite:
  import ContractsSuite.*

  private lazy val sources: List[SourceFile] = Modules.flatMap(SourceFile.all(_))

  test("no contract violation but the listed exceptions, and every listed exception still exists"):
    val file = ArchitectureSuite.Resources.resolve("contracts.tsv")
    val violations = Rules.all(sources)
    val actual = violations.map(_.row).distinct.sorted
    val listed =
      if Files.isRegularFile(file) then Files.readAllLines(file, UTF_8).asScala.filterNot(_.startsWith("#")).toList
      else Nil
    if sys.env.get("HISTMERSION_UPDATE_ARCHITECTURE").contains("1") then
      val risen = ArchitectureSuite.Ratchet.risenRules(listed, actual)
      assert(
        risen.isEmpty || !Files.isRegularFile(file),
        s"not rewritten: these rules' totals rose (fix the new violations first): ${risen.mkString(", ")}"
      )
      val header = "# rule\tfile\tdetail\tcount — the known violations of AGENTS.md's rules 1–14; shrink, never grow"
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
        ArchitectureSuite.SourceFile("core:x/Shape.scala", "com.histmersion.x", ArchitectureSuite.Blank(text), text)
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
    assertEquals(found.map(v => s"${v.at}: ${v.detail}"), Nil)

  test("no case of the problem module has a default parameter"):
    val found = Rules.caseDefaults(SourceFile.all("problem"))
    assertEquals(found.map(v => s"${v.at}: ${v.detail}"), Nil)

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

  test("Stored.Unrecognised is built in main code only by the stored column's reader"):
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
    assertEquals(found("api:api/problem/Column.scala"), Nil)
    assertEquals(found("problem:util/problem/Problem.scala"), Nil)

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

  test("a type named for an entity and a property is the property, a component of the entity is not"):
    val entities = Set("Approval", "Source", "PeriodO", "Citekey")
    assertEquals(Rules.propertyOf("ApprovalKind", entities), Some("Approval"))
    assertEquals(Rules.propertyOf("SourceService", entities), None)
    assertEquals(Rules.propertyOf("CitekeyRule", entities), None)
    assertEquals(Rules.propertyOf("PeriodODataset", entities), Some("PeriodO"))
    assertEquals(Rules.propertyOf("Sources", entities), None)

object ContractsSuite:

  /** The modules G60 has classified: none of their sites builds `Problem.Unclassified` any more (step 2 the leaf
    * layers; each later step adds its own).
    */
  val Classified: List[String] =
    List("values", "config", "files", "plugin-api", "plugin-resolver", "upstreams", "plugin-vaults") ++
      List("plugins/events", "plugins/fetchers", "plugins/gazetteers") ++
      List(
        "plugin-fixtures/hello",
        "plugin-fixtures/hello-ai",
        "plugin-fixtures/hello-deps",
        "plugin-fixtures/hello-lib"
      )

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
          case ((layer, name), defs) if defs.map(_._3.key).distinct.size > 1 =>
            val files = defs.map(_._3.key).distinct.sorted
            val (_, _, s, at) = defs.minBy(_._3.key)
            Violation(
              "concept",
              s"layer $layer",
              s"$name in ${files.mkString(" ")}",
              files.size,
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

    /** The JSON module is the codec library itself: its givens are the base instances (a string, a list) that every
      * derivation builds on, as circe's own were.
      */
    private def derives(s: SourceFile): List[Violation] =
      if module(s) == "json" then Nil
      else
        val hits =
          HandGiven.findAllMatchIn(s.code).map(_.start) ++ "\\.derived\\b".r.findAllMatchIn(s.code).map(_.start)
        counted("derives", s, "codec given written by hand", hits.toList.distinct.sorted)

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
              s.code.startsWith(SharedJson, n.start)
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

    /** The files that may build `Stored.Unrecognised`: the stored column's reader and the cases' own file. */
    private val Unrecognisers: Set[String] = Set(ProblemFile, "api:api/problem/Column.scala")
    private val Unrecognised = "\\bUnrecognised\\s*\\(".r

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
