package com.histmersion.linting

import Architecture.{Place, Rules}

class ArchitectureSuite extends munit.FunSuite:

  private def file(key: String, pkg: String, text: String): SourceFile = SourceFile(key, pkg, Blank(text), text)

  test("a name a file defines itself, nested too, is its own; another place's type of that name is still found"):
    val agents = "object Trace:\n  def of(x: Int) = x\n"
    val record =
      "final case class Event(trace: Trace)\nobject Event:\n  type Trace = Trace.T\n  object Trace extends Newtype[String, Pure]\n"
    val user = "final case class Step(trace: Trace)\n"
    val rows = Rules
      .all(
        List(
          file("server:domain/automation/agents/Trace.scala", "com.histmersion.domain.automation.agents", agents) ->
            "automation.agents",
          file("api:api/log/Event.scala", "com.histmersion.api.log", record) -> "util.log",
          file("api:api/log/Step.scala", "com.histmersion.api.log", user) -> "util.log"
        )
      )
      .map(_.row)
      .filter(_.startsWith("direction"))
    assertEquals(rows, List("direction\tapi:api/log/Step.scala\tutil.log -> automation: Trace\t1"))
    assertEquals(Rules.ownNames(record), Set("Event", "Trace"))

  test("a file's key names its platform's source root, and the shared root has no marker"):
    assertEquals(SourceFile.key("api", "scala", "api/problem/Answer.scala"), "api:api/problem/Answer.scala")
    assertEquals(SourceFile.key("api", "scalajs", "api/problem/ProblemJson.scala"), "api:scalajs:api/problem/ProblemJson.scala")
    def io(root: String) =
      val text = "object Io:\n  val p = Files.exists(q)\n"
      file(SourceFile.key("core", root, "x/Io.scala"), "com.histmersion.x", text)
    val (jvm, js) = (io("scalajvm"), io("scalajs"))
    assertEquals(List(jvm, js).map(_.platformless).distinct, List("core:x/Io.scala"))
    assertEquals(
      Rules.all(List(jvm -> "library.sources", js -> "library.sources")).map(_.row),
      List("filesystem\tcore:scalajvm:x/Io.scala\tFiles.*\t1", "filesystem\tcore:scalajs:x/Io.scala\tFiles.*\t1")
    )

  test("a source's path maps to the key its rules use"):
    assertEquals(
      Project.ref("modules/api/src/main/scalajs/com/histmersion/api/problem/ProblemJson.scala").map(_.key),
      Some("api:scalajs:api/problem/ProblemJson.scala")
    )
    assertEquals(
      Project.ref("modules/plugins/media/src/test/scala/com/histmersion/plugins/media/X.scala").map(_.key),
      Some("plugins/media:test/plugins/media/X.scala")
    )
    assertEquals(Project.ref("tools/lint/rules/X.scala"), None)

  test("the file-system rule's Path.of is java.nio's: imported, renamed or written out, never another type's Path"):
    def pathOf(text: String): List[String] =
      Rules.all(List(file("server:x/A.scala", "com.histmersion.x", text) -> "library.sources")).map(_.row).filter(_.contains("\tPath.of\t"))
    def row(n: Int) = List(s"filesystem\tserver:x/A.scala\tPath.of\t$n")
    assertEquals(pathOf("import java.nio.file.Path\nval p = Path.of(\"a\")\n"), row(1))
    assertEquals(pathOf("import java.nio.file.{Files, Path}\nval p = Path.of(a)\nval q = Path\n  .of(b)\n"), row(2))
    assertEquals(pathOf("import java.nio.file.*\nval p = Path.of(a)\n"), row(1))
    assertEquals(pathOf("import java.nio.file.{Path as P}\nval p = P.of(a)\n"), row(1))
    assertEquals(pathOf("val p = java.nio.file.Path.of(a)\n"), row(1))
    assertEquals(pathOf("import java.nio\nval p = nio.file.Path.of(a)\n"), row(1))
    assertEquals(pathOf("val p = Problem.Io.Path.of(store.where(key))\n"), Nil)
    assertEquals(pathOf("import java.nio.file.Path\nval p = Problem.Io.Path.of(a)\n"), Nil)
    assertEquals(pathOf("import Problem.Io.Path\nval p = Path.of(a)\n"), Nil)
    assertEquals(pathOf("// java.nio.file.Path.of(a)\nval s = \"Path.of\"\n"), Nil)

  test("the code a check reads is the same however the formatter breaks a string over lines"):
    def words(text: String): List[String] = Blank(text).split("\\s+").toList.filter(_.nonEmpty)
    val oneLine = "val a = s\"$owner${if (x).value then \" (here)\" else \"\"} until ${y.value}\".size"
    val broken = "val a = s\"$owner${\n    if (x).value then \" (here)\" else \"\"\n  } until ${y.value}\".size"
    assertEquals(words(broken), words(oneLine))
    assertEquals(words(oneLine), List("val", "a", "=", "s", "if", "(x).value", "then", "else", "y.value", ".size"))
    assertEquals(words("f(\"a\\\"b\", /* c */ d) // e"), List("f(", ",", "d)"))
    assertEquals(words("val t = \"\"\"x \"y\" ${z}\"\"\" + 1"), List("val", "t", "=", "+", "1"))

  test("a file outside its feature package is placed by @movesTo; one already there, or moving nowhere, is a finding"):
    def placed(pkg: String, text: String) =
      Architecture.placement(List(file("core:x/A.scala", pkg, text))).map(_.detail)
    assertEquals(placed("com.histmersion.x", "object A\n").map(_.take(30)), List("package com.histmersion.x is n"))
    assertEquals(placed("com.histmersion.domain.library.sources", "object A\n"), Nil)
    assertEquals(placed("com.histmersion.util.common", "object A\n"), Nil)
    val moving = "@movesTo(FeaturePlace.LibrarySources)\nobject A\n"
    assertEquals(placed("com.histmersion.x", moving), List("moves to library.sources"))
    assertEquals(placed("com.histmersion.domain.media", moving), List("in media, moves to library.sources"))
    assertEquals(placed("com.histmersion.domain.library.sources", moving), Nil)
    assertEquals(Place.ofPackage("com.histmersion.api.jobs"), Some("util.jobs"))
    assertEquals(Place.ofPackage("com.histmersion.domain"), Some("kernel"))
    assertEquals(Place.ofPackage("com.histmersion.server"), None)

  test("a place depends on lower domains, any util area and shared places; a util area only on util"):
    assert(Place.mayDependOn("research.articles", "library.sources"))
    assert(!Place.mayDependOn("library.sources", "research.articles"))
    assert(Place.mayDependOn("library.sources", "util.jobs"))
    assert(!Place.mayDependOn("util.jobs", "library.sources"))
    assert(!Place.mayDependOn("library.sources", "server"))
    assert(Place.mayDependOn("server", "library.sources"))
    assert(Place.mayDependOn("media", "personal.marks"))
    assert(!Place.mayDependOn("media", "personal"))

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
    assert(!total("e.label", "SELECT * FROM events e WHERE true"))
    assert(!total("x.label, x.id", "SELECT * FROM events e JOIN other x ON true"))
    assert(total("citekey", "SELECT * FROM event_datings WHERE event_id = $id"))
    assert(!total("citekey", "SELECT * FROM event_datings WHERE event_id = ANY($ids)"))
    assert(total("lower(p.name), p.wikidata DESC NULLS LAST", "SELECT * FROM places p"))
    assert(total("""e.id COLLATE "C"""", "SELECT * FROM events e"))
    val code =
      """object Repo:
        |  // ORDER BY label in a comment
        |  def a = sql\"\"\"SELECT id FROM events e
        |              WHERE e.label = $l ORDER BY e.label, e.id LIMIT $n\"\"\"
        |  def b = sql"SELECT id FROM events ORDER BY label"
        |""".stripMargin
    val source = file("db:x/Repo.scala", "com.histmersion.x", code)
    assertEquals(TotalOrder.clauses(source.key, source.raw).map(_._1.text), List("e.label, e.id", "label"))
    assertEquals(TotalOrder.partial(List(source), keys).map(c => (c.line, c.text)), List((5, "label")))
    val escaped = "def c = sql\"\"\"SELECT id FROM events e ORDER BY e.id COLLATE \"C\\\"\"\"\"\n"
    assertEquals(TotalOrder.clauses("db:x/Repo.scala", escaped).map(_._1.text), List("e.id COLLATE \"C\""))

  test("the ordinal of one set-returning function WITH ORDINALITY orders its rows totally, wherever the FROM stands"):
    val keys = TotalOrder.keys("")
    val code =
      """object Repo:
        |  def a = sql\"\"\"SELECT jsonb_agg(a.f ORDER BY a.n)
        |              FROM unnest($fs::text[], $ss::text[]) WITH ORDINALITY AS a(f, s, n)\"\"\"
        |  def b = sql\"\"\"SELECT x FROM unnest($xs::text[]) WITH ORDINALITY AS u(x, i) ORDER BY i LIMIT $n\"\"\"
        |  def c = sql\"\"\"SELECT jsonb_agg(a.f ORDER BY a.s) FROM unnest($fs, $ss) WITH ORDINALITY AS a(f, s, n)\"\"\"
        |  def d = sql\"\"\"SELECT jsonb_agg(x ORDER BY b.n) FROM unnest($xs) WITH ORDINALITY AS a(x, n)\"\"\"
        |""".stripMargin
    val source = file("db:x/Repo.scala", "com.histmersion.x", code)
    assertEquals(TotalOrder.ordinals("FROM unnest($fs, $ss) WITH ORDINALITY AS a(f, s, n)"), Set("a" -> "n"))
    assertEquals(TotalOrder.partial(List(source), keys).map(c => (c.line, c.text)), List((5, "a.s"), (6, "b.n")))
