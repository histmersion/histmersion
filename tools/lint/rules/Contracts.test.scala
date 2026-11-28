package com.histmersion.linting

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import scala.jdk.CollectionConverters.*

import Contracts.Rules

class ContractsSuite extends munit.FunSuite:

  private def file(key: String, code: String, pkg: String = "com.histmersion.x"): SourceFile =
    SourceFile(key, pkg, Blank(code), code)

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
      Rules.all(List(file("core:x/Shape.scala", text))).filter(_.rule == "one-type").map(_.detail)
    val oneLine = "sealed trait Shape\nfinal case class Circle(r: Int = 1) extends Shape\nobject Other\n"
    val broken =
      "sealed trait Shape\nfinal case class Circle(\n    r: Int = 1\n) extends Shape\n    derives CanEqual\nobject Other\n"
    assertEquals(oneType(oneLine), List("Shape Other"))
    assertEquals(oneType(broken), oneType(oneLine))

  test("scallop's configuration is declared only in the command-line core and its adapters"):
    def hits(key: String, text: String): List[String] =
      Rules.all(List(file(key, text, "com.histmersion.cli"))).filter(_.rule == "scallop").map(_.file)
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
    val problem = SourceFile(Rules.ProblemFile, "com.histmersion.util.problem", code, code)
    assertEquals(
      Rules.all(List(problem)).filter(_.rule == "problem-field").map(_.detail),
      List("Said.plugin: String", "Said.count: Int", "Unclassified.text: Legacy.Text", "Length.expected: Long")
    )

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
    assertEquals(
      Rules.caseDefaults(List(file("problem:x/Cases.scala", code))).map(v => (v.at, v.detail)),
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
    assertEquals(
      Rules.unclassified(List(file("values:x/Sites.scala", code))).map(v => (v.at, v.detail)),
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
    def found(key: String) = Rules.all(List(file(key, code))).filter(_.rule == "unrecognised")
    assertEquals(found("server:x/Reads.scala").map(v => (v.count, v.at)), List((1, "server:x/Reads.scala:2")))
    assertEquals(found("api:api/problem/StoredShapes.scala"), Nil)
    assertEquals(found("problem:util/problem/Problem.scala"), Nil)
    Vector("api:scalajvm:api/problem/ProblemJson.scala", "api:scalajs:api/problem/ProblemJson.scala").foreach(key =>
      assertEquals(found(key).map(_.count), List(1), key)
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
    def found(key: String) = Rules.all(List(file(key, code))).filter(_.rule == "first-problem")
    assertEquals(found("server:x/Jobs.scala").map(v => (v.count, v.at)), List((5, "server:x/Jobs.scala:2")))
    assertEquals(found("server:x/Jobs.scala").flatMap(_.offsets).map(o => code.substring(0, o).count(_ == '\n') + 1), List(2, 3, 4, 6, 7))
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
    def found(key: String) = Rules.all(List(file(key, code))).filter(_.rule == "html-sink")
    assertEquals(found("web:web/View.scala").map(v => (v.count, v.at)), List((5, "web:web/View.scala:2")))
    assertEquals(found("web:web/HtmlSink.scala"), Nil)

  test("no request of the web page is sent from a failure branch (the New research area's former flow is found)"):
    val former =
      """object Form:
        |  def submit(a: NewArea) =
        |    ApiClient
        |      .getGoal(a.slug)
        |      .transformWith {
        |        case scala.util.Success(_) =>
        |          Future.failed(new Exception("exists"))
        |        case scala.util.Failure(_) => ApiClient.putGoal(a.slug, a.put)
        |      }
        |      .flatMap(_ => ApiClient.startRun(req))
        |""".stripMargin
    val others =
      """object View:
        |  def a(f: Future[Int]) = f.onComplete {
        |    case Failure(e) =>
        |      shown.set(e.getMessage)
        |      ApiClient.log(e)
        |    case Success(v) => ApiClient.save(v)
        |  }
        |  def b(f: Future[Int]) = f.recover { case _ => ApiClient.retry() }.map(x => ApiClient.read(x))
        |  def c(f: Future[Int]) = f.failed.foreach(e => ApiClient.report(e))
        |  def d(t: Try[Int]) = t match
        |    case Failure(e) => note(e)
        |    case Success(v) =>
        |      ApiClient.write(v)
        |  def e = ApiClient.read(1).recover { case _ => 0 }
        |  // case Failure(e) => ApiClient.inAComment()
        |  val words = "case Failure(_) => ApiClient.inAString()"
        |""".stripMargin
    def found(code: String, key: String = "web:web/View.scala") =
      Rules.all(List(file(key, code))).filter(_.rule == "failure-request")
    assertEquals(found(former).map(v => (v.count, v.at)), List((1, "web:web/View.scala:8")))
    assertEquals(found(others).map(v => (v.count, v.at)), List((3, "web:web/View.scala:5")))
    assertEquals(found(others, "server:x/View.scala"), Nil)

  test("a request a view consumes outside the one request type is counted; one asked through it is not"):
    val code =
      """object View:
        |  val r = Request[Int]()
        |  def a() = r.ask(ApiClient.count(Filter()))
        |  def b() = r.askThen(ApiClient.managers.flatMap(ms => ApiClient.items(ms)))(k)
        |  def c() = ApiClient.count(Filter()).foreach(n => shown.set(n))
        |  def d() = ApiClient.save(x).onComplete(_ => ())
        |  def e() = ApiClient.tileTemplate(q)
        |  val u = ApiClient.Url("x")
        |""".stripMargin
    def found(key: String) = Rules.all(List(file(key, code))).filter(_.rule == "raw-request")
    assertEquals(found("web:web/View.scala").map(v => (v.count, v.at)), List((2, "web:web/View.scala:5")))
    assertEquals(found("web:web/Request.scala"), Nil)
    assertEquals(found("server:x/View.scala"), Nil)

  test("a schema is derived only where the codecs' config is in scope, and every JsoniterConfig given is that one"):
    def found(files: SourceFile*) = Rules.all(files.toList).filter(_.rule == "schema-config").map(v => (v.count, v.at))
    val derived = "final case class A(n: Int) derives JsonCodec,\n  KindlingsSchema\n"
    val head = "package com.histmersion\npackage api.x\n"
    assertEquals(found(file("api:api/x/A.scala", s"${head}import com.histmersion.api.given\n$derived", "com.histmersion.api.x")), Nil)
    assertEquals(found(file("api:api/x/A.scala", s"$head$derived", "com.histmersion.api.x")), List((1, "api:api/x/A.scala:4")))
    val write = "package com.histmersion\npackage api\ngiven JsoniterConfig = JsonCodec.schemas\n"
    val chained = s"package com.histmersion\npackage api\npackage problem\n$derived"
    assertEquals(
      found(
        file("api:api/Write.scala", write, "com.histmersion.api"),
        file("api:api/problem/A.scala", chained, "com.histmersion.api.problem")
      ),
      Nil
    )
    assertEquals(found(file("api:api/problem/A.scala", chained, "com.histmersion.api.problem")).size, 1)
    val shared = file("server:w/G.scala", "private given JsoniterConfig = JsonCodec.schemas\n", "com.histmersion.w")
    assertEquals(found(shared, file("server:w/A.scala", derived, "com.histmersion.w")), Nil)
    assertEquals(
      found(file("server:w/G.scala", "given JsoniterConfig = JsoniterConfig.default\n", "com.histmersion.w")),
      List((1, "server:w/G.scala:1"))
    )
    assertEquals(found(file("api:api/x/B.scala", "import x.KindlingsSchema\n", "com.histmersion.api.x")), Nil)

  test("a newtype …Html over String is a violation: the one markup type is api.Html"):
    val code =
      """object Render:
        |  type Html = Html.T
        |  object Html extends Newtype[String, Pure]
        |  object BubbleHtml extends Newtype[String, Pure]
        |  object Count extends Newtype[Int, Pure]
        |  object Htmls extends Newtype[String, Pure]
        |""".stripMargin
    val found = Rules.all(List(file("web:web/Render.scala", code))).filter(_.rule == "html-newtype")
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
    def found(key: String) = Rules.all(List(file(key, code))).filter(_.rule == "relayed")
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
      Rules.all(List(SourceFile(key, "com.histmersion.x", code, code))).filter(_.rule == "nonempty-pattern")
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
      Rules.all(List(file(key, code))).filter(_.rule == "derives").map(v => (v.count, v.at))
    assertEquals(lines("core:x/Codecs.scala"), List((8, "core:x/Codecs.scala:2")))
    assertEquals(Rules.genericCodecGivens(code).map(code.substring(0, _).count(_ == '\n') + 1), List(4, 5, 6, 7, 8, 11))
    assertEquals(lines("json:x/Codecs.scala"), Nil)

  test("a newtype's instance lifted from its base type's is no codec written by hand; any other generic given is"):
    val code =
      """object NewtypeCodecs:
        |  given schema: [T] => (m: RefinedType.Mirror[T]) => (base: Schema[m.BaseType]) => Schema[T] = base.as[T]
        |  inline given codec: [T] => (m: RefinedType.Mirror[T]) => (base: JsonValueCodec[m.BaseType]) =>
        |    JsonValueCodec[T] = make
        |  given plain: [T] => (m: RefinedType.Mirror[T] { type ConstraintType = Pure }) =>
        |    (base: Codec[String, m.BaseType, TextPlain]) => Codec[String, T, TextPlain] = make
        |  given wrapped: [T] => (m: Mirror[T]) => (b: JsonKeyCodec[m.Base]) => JsonKeyCodec[T] = make
        |  given box: [A] => (c: JsonValueCodec[A]) => JsonValueCodec[Box[A]] = make
        |""".stripMargin
    assertEquals(Rules.genericCodecGivens(code).map(code.substring(0, _).count(_ == '\n') + 1), List(7, 8))

  test("a two-state enum's column lifted from its Boolean's is no codec written by hand; its own codec home is listed"):
    val lifted =
      """object NewtypeColumns:
        |  given twoStates: [E <: reflect.Enum] => (states: TwoStates[E]) => DbCodec[E] = make
        |  given box: [A] => (c: DbCodec[A]) => DbCodec[Box[A]] = make
        |""".stripMargin
    assertEquals(Rules.genericCodecGivens(lifted).map(lifted.substring(0, _).count(_ == '\n') + 1), List(3))
    val home =
      """trait TwoStates[E <: reflect.Enum]:
        |  given twoStates: JsonValueCodec[E] = make
        |""".stripMargin
    def lines(key: String) =
      Rules.all(List(file(key, home))).filter(_.rule == "derives").map(v => (v.count, v.at))
    assertEquals(lines("api:api/json/TwoStates.scala"), Nil)
    assertEquals(lines("api:api/Other.scala"), List((1, "api:api/Other.scala:2")))

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
      Rules.all(List(file(key, code))).filter(_.rule == "derives").map(v => (v.count, v.at))
    assertEquals(lines("core:x/Equalities.scala"), List((3, "core:x/Equalities.scala:6")))
    assertEquals(
      Rules.Derived.findAllMatchIn(code).map(m => code.substring(0, m.start).count(_ == '\n') + 1).toList,
      List(7, 8)
    )

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
    val pair = List(file("core:x/A.scala", code), file("db:x/B.scala", code))
    assertEquals(
      Rules.equalityTwice(pair),
      Map(
        "UUID" -> List("core:x/A.scala", "db:x/B.scala"),
        "WatchEvent.Kind[?]" -> List("core:x/A.scala", "db:x/B.scala")
      )
    )
    assertEquals(Rules.equalityHomes(pair).map(v => (v.file, v.at)).distinct.size, 6)

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
    val rows = Rules.all(found).map(v => s"${v.rule}\t${v.file}").distinct.sorted
    assertEquals(
      rows,
      List(
        "concept\tcore:scalajs:x/Pair.scala",
        "concept\tcore:scalajvm:x/Pair.scala",
        "concept\tcore:y/Pair.scala",
        "copy\tcore:scalajs:x/Pair.scala",
        "copy\tcore:scalajvm:x/Pair.scala",
        "one-type\tcore:scalajs:x/Pair.scala",
        "one-type\tcore:scalajvm:x/Pair.scala"
      )
    )

  test("a type named for an entity and a property is the property, a component of the entity is not"):
    val entities = Set("Approval", "Source", "PeriodO", "Citekey")
    assertEquals(Rules.propertyOf("ApprovalKind", entities), Some("Approval"))
    assertEquals(Rules.propertyOf("SourceService", entities), None)
    assertEquals(Rules.propertyOf("CitekeyRule", entities), None)
    assertEquals(Rules.propertyOf("PeriodODataset", entities), Some("PeriodO"))
    assertEquals(Rules.propertyOf("Sources", entities), None)

  test("every rule of the scanner has a name of the LintRule enum"):
    val enumText = Files.readString(java.nio.file.Path.of("modules/lint/src/main/scala/com/histmersion/lint/LintRule.scala"))
    Contracts.Owned.foreach(r => assert(s"\\b$r\\b".r.findFirstIn(enumText).isDefined, r))
