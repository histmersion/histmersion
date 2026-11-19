package com.histmersion
package server.agents
import com.histmersion.domain.automation.agents.SttpAiJson
import com.histmersion.domain.automation.agents.SttpAiJson.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.problem.{Legacy, Problem}

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.library.sources.SourceFixtures

import com.histmersion.domain.automation.agents.Toolbox

import com.histmersion.domain.automation.workflows.ToolName

import com.histmersion.util.ocr.ConverterClient

import com.augustnagro.magnum.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.util.database.{Db, Migrate}
import com.histmersion.server.{Deps, ServerWiring}
import com.histmersion.util.ocr.ConverterClient

import java.nio.file.Path
import com.histmersion.db.*
import com.histmersion.api.ocr.ConvertedDoc
import com.histmersion.api.library.Source
import com.histmersion.api.library.Finding
import com.histmersion.db.DbSuite
import com.histmersion.db.TestDb
import com.histmersion.config.AgentToolsConfig
import com.histmersion.config.DbConfig
import com.histmersion.util.storage.Blob
import com.histmersion.api.catalogue.Year
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.library.workflows.LibraryAgentTools

/** The agent tools under `agents.tools` against a real Postgres (#token-diet, 2026-10-16): a record keeps its findings
  * and loses the notes' tail, a body read without `chars` is a short slice that says where to read on, `passages` still
  * finds a quotation past the slice, and `query` shows few rows. Exactly what the agent MCP endpoint answers (it calls
  * the same tools). Skipped without HISTMERSION_TEST_DB_URL; writes only `zztool*` rows of a `*_test` database.
  */
class AgentToolCapSuite extends DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private val Ck = "zztool2020_dragon"
  private val Caps = AgentToolsConfig(
    resultMaxChars = AgentToolsConfig.ResultMaxChars(3000),
    bodyChars = AgentToolsConfig.BodyChars(1200),
    queryMaxRows = AgentToolsConfig.QueryMaxRows(5)
  )

  private lazy val cfg =
    val b = AppConfig.load()
    b.modify(_.db).setTo(b.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
  private lazy val deps =
    val ds = testPool(cfg.db)
    Migrate.run(ds)
    Deps(cfg, ds, testQueryPool(cfg.db, cfg.query))
  private lazy val h = ServerWiring(deps, NoConverter)

  private object NoConverter extends ConverterClient:
    def health: Either[Problem, ConverterClient.HealthSummary] = Right(
      ConverterClient.HealthSummary("ok")
    )
    def convert(
        file: Blob,
        filename: ConverterClient.Filename,
        quality: ConverterClient.HighQuality
    ): Either[Problem, ConvertedDoc] = Left(Problem.Unclassified(Legacy.Text("not used")))

  private val Body = (1 to 400).map(i => s"Paragraph $i of the book about serpents and heroes.").mkString("\n\n") +
    "\n\nThe hero slew the serpent at the end."
  private val Notes = (1 to 200).map(i => s"Note $i on the argument of the book.").mkString("\n")

  private def clean(): Unit =
    connect(deps.ds):
      sql"DELETE FROM source_aliases WHERE alias LIKE 'zztool%' OR citekey LIKE 'zztool%'".update.run()
      sql"DELETE FROM sources WHERE citekey LIKE 'zztool%'".update.run()

  override def beforeEach(context: BeforeEach): Unit =
    clean()
    val put = Source.Put(
      api.library.Title("How to Kill a Dragon"),
      authors = (List("Calvert Watkins")).map(api.library.AuthorName.apply),
      year = (Some(1995)).map(Year.apply),
      status = Some(Source.Status.Foundational),
      topics = List(Source.Topics("dragon_slaying_myth"), Source.Topics("pie_religion")),
      summary = Some(Source.Summary("The book reconstructs the dragon-slaying formula.")),
      body = Some(Source.OcrMarkdown(Body)),
      notes = Some(Source.Notes(Notes)),
      findings = List(
        Finding.Input(Finding.Claim("The formula *gʷhen- dhgʷhim recurs across the traditions.")),
        Finding.Input(Finding.Claim("The formula is Proto-Indo-European."))
      )
    )
    assert(h.library.sources.put(Citekey("zztool2020_dragon"), put).isRight)
  override def afterSuite(): Unit = if testDb.isDefined then clean()

  private def call(box: Toolbox, t: ToolName, args: Json): String =
    val tool = box.tool(t).get
    SttpAiJson.arguments(tool, args).fold(e => fail(s"bad arguments: $e"), in => tool.execute(in))

  private lazy val capped = TestToolbox(h, Caps)
  private lazy val uncapped = TestToolbox(h)

  test("the record: summary and findings whole, chip links gone, the notes cut and saying how to read on"):
    val full = call(
      uncapped,
      LibraryAgentTools.GetSource,
      Json.obj("citekey" -> Ck.asJson)
    )
    assert(full.length > Caps.resultMaxChars.value, s"the handler's own answer is long: ${full.length}")
    val r = call(
      capped,
      LibraryAgentTools.GetSource,
      Json.obj("citekey" -> Ck.asJson, "section" -> "all".asJson)
    )
    assert(r.length <= Caps.resultMaxChars.value, r.length)
    assert(
      r.contains("The book reconstructs the dragon-slaying formula.") && r.contains(
        "The formula is Proto-Indo-European."
      ),
      r
    )
    assert(!r.contains("(/#") && r.contains("dragon slaying myth"), "chips without links")
    assert(r.contains("Note 1 on") && !r.contains("Note 200 on") && r.contains("get_source section=\"notes\""), r)

  test("a body read without chars is a short slice that says where to read on; passages reaches past it"):
    val r = call(
      capped,
      LibraryAgentTools.GetSource,
      Json.obj("citekey" -> Ck.asJson, "section" -> "body".asJson)
    )
    assert(
      r.length <= Caps.resultMaxChars.value && r.contains("Paragraph 1 of") && !r.contains("Paragraph 100 of"),
      s"${r.length} ${r.take(300)} … ${r.takeRight(300)}"
    )
    assert(r.contains("more chars — pass offset=1200"), r.takeRight(120))
    val asked =
      call(
        capped,
        LibraryAgentTools.GetSource,
        Json.obj("citekey" -> Ck.asJson, "offset" -> 0.asJson, "chars" -> 50000.asJson)
      )
    assert(asked.length <= Caps.resultMaxChars.value, s"an asked-for slice is held to the cap too: ${asked.length}")
    val p =
      call(
        capped,
        LibraryAgentTools.Passages,
        Json.obj("citekey" -> Ck.asJson, "query" -> "slew the serpent".asJson)
      )
    assert(p.contains("**slew the serpent**") || p.toLowerCase.contains("slew the serpent"), p)
    val off = "· offset ([0-9]+)".r.findFirstMatchIn(p).map(_.group(1).toInt).getOrElse(fail(s"no offset: $p"))
    val at =
      call(
        capped,
        LibraryAgentTools.GetSource,
        Json.obj("citekey" -> Ck.asJson, "offset" -> off.asJson, "chars" -> 1500.asJson)
      )
    assert(at.contains("slew the serpent"), at)

  test("query: a few rows, marked, with how to ask for less"):
    val r = call(
      capped,
      LibraryAgentTools.Query,
      Json.obj("sql" -> "SELECT g AS n FROM generate_series(1, 40) g".asJson)
    )
    assert(r.startsWith("### 5 row(s) (truncated by the row cap)"), r.take(80))
    assert(r.contains("35 more rows not shown") && r.contains("never a whole table"), r)
    val all =
      call(
        uncapped,
        LibraryAgentTools.Query,
        Json.obj("sql" -> "SELECT g AS n FROM generate_series(1, 40) g".asJson)
      )
    assert(all.startsWith("### 40 row(s)"), all.take(40))

  test("search answers with a topic alone, compact and without chip links"):
    val r = call(
      capped,
      LibraryAgentTools.Search,
      Json.obj("topics" -> List("dragon_slaying_myth").asJson)
    )
    assert(r.contains(Ck) && !r.contains("(/#"), r)
