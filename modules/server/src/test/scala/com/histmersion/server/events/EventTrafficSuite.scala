package com.histmersion
package server.events
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.RawJson

import com.histmersion.util.problem.Text.sentence
import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.server.CatalogueWiring

import com.histmersion.server.ServerPlugins

import com.histmersion.config.{AppConfig, DbConfig, LoadedConfig}
import com.histmersion.db.TestDb
import com.histmersion.util.database.Migrate
import com.histmersion.pluginhost.HttpAnswer
import com.histmersion.server.Deps
import com.histmersion.server.media.FakeMediaHttp

import com.augustnagro.magnum.*
import org.ekrich.config.ConfigFactory

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import com.histmersion.api.catalogue.{CatalogRecord, EventImportRequest}
import com.histmersion.db.DbSuite
import com.histmersion.domain.library.acquisition.CoreBudget
import com.histmersion.domain.DeepTime

/** The recorded traffic and the stored results of the event catalogues: imports of GVP, NOAA and Wikidata from the
  * fixtures (no socket) on the stock generation's catalogues — every request with its headers, in order — then the
  * stored catalogue records and events, then `events rederive` over rows made stale (a pre-V088 GVP row, spans that no
  * longer match their records), and the stored results again. Against a real PostGIS (HISTMERSION_TEST_DB_URL; skipped
  * otherwise). The transcript is `traffic/events.txt`, compared byte for byte (`HISTMERSION_UPDATE_TRAFFIC=1` rewrites
  * it): moving the catalogues changes no request and no stored result.
  */
class EventTrafficSuite extends DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private def fixture(name: String): String =
    scala.io.Source.fromResource(s"events/$name")(using scala.io.Codec.UTF8).mkString

  private val emptyGvp = """{"type":"FeatureCollection","features":[],"numberMatched":0}"""
  private val emptySparql = """{"head":{"vars":[]},"results":{"bindings":[]}}"""
  private val names =
    """{"entities":{"Q131386":{"labels":{"en":{"value":"Battle of Alesia"},"fr":{"value":"Siège d'Alésia"}},
    "aliases":{"en":[{"value":"Siege of Alesia"}]}}}}"""

  /** The three catalogues, answering from the fixtures. */
  private def route(url: String): Either[String, HttpAnswer] =
    if url.contains("GVP-VOTW") then
      FakeMediaHttp.json(if url.contains("StartDateYear%3E%3D-2000+") then fixture("gvp.json") else emptyGvp)
    else if url.contains("hazard-service") then
      val ds = List("earthquakes", "volcanoes", "tsunamis").find(d => url.contains(s"/$d")).get
      val page = "page=(\\d+)".r.findFirstMatchIn(url).map(_.group(1)).getOrElse("1")
      if ds == "earthquakes" && page == "2" then FakeMediaHttp.json("""{"items":[],"totalPages":2}""")
      else
        FakeMediaHttp.json(
          fixture(if ds == "tsunamis" then "noaa_tsunamis_events.json" else s"noaa_$ds.json")
            .replaceAll("\"totalPages\":\\d+", if ds == "earthquakes" then "\"totalPages\":2" else "\"totalPages\":1")
        )
    else if url.contains("query.wikidata.org") then
      FakeMediaHttp.json(if url.contains("Q188055") then fixture("wikidata_siege.json") else emptySparql)
    else if url.contains("wbgetentities") then FakeMediaHttp.json(names)
    else Left(s"unexpected $url")

  /** Every request, with its headers, as the wire saw it. */
  private def requests(f: FakeMediaHttp): String =
    f.requests.toList.map { (u, h) =>
      s"GET $u\n" + h.toList.sortBy(_._1.toLowerCase(java.util.Locale.ROOT)).map((k, v) => s"  $k: $v\n").mkString
    }.mkString

  private val text =
    """histmersion.media.request-interval-ms = 0
      |histmersion.events.gvp.request-interval-ms = 0
      |histmersion.events.noaa.request-interval-ms = 0
      |histmersion.events.wikidata { request-interval-ms = 0, api-interval-ms = 0, classes = "Q188055:siege,Q178561:battle" }
      |""".stripMargin

  private lazy val loaded: LoadedConfig =
    AppConfig.loadWithUserText(ConfigFactory.empty, text).fold(e => fail(e.sentence), identity)
  private def cfg = loaded.config

  private lazy val ds =
    val d = testPool(cfg.db.copy(url = DbConfig.Url(testDb.get)))
    Migrate.run(d)
    d

  private def clean(): Unit = connect(ds):
    val _ = sql"DELETE FROM events WHERE catalogue IS NOT NULL".update.run()
    val _ = sql"DELETE FROM event_catalogue".update.run()
    val _ = sql"DELETE FROM event_import_parts".update.run()

  override def beforeAll(): Unit = if testDb.isDefined then clean()
  override def afterSuite(): Unit = if testDb.isDefined then clean()

  /** Rows as stored, without the columns a run's clock, a fresh id or the database's own instance fills. */
  private def steady(json: List[String]): String =
    json.map { j =>
      RawJson
        .parse(j)
        .fold(e => fail(e.toString), identity)
        .mapObject(_.filterKeys(k => !k.endsWith("_at") && k != "uid" && k != "origin"))
        .noSpaces + "\n"
    }.mkString

  private def stored: String =
    connect(ds):
      val events =
        sql"SELECT to_jsonb(t)::text FROM events t WHERE catalogue IS NOT NULL ORDER BY id".query[String].run()
      val records =
        sql"SELECT to_jsonb(t)::text FROM event_catalogue t ORDER BY provider, provider_id".query[String].run()
      val parts = sql"SELECT to_jsonb(t)::text FROM event_import_parts t ORDER BY provider, part".query[String].run()
      val datings = sql"SELECT to_jsonb(t)::text FROM event_datings t ORDER BY event_id, citekey".query[String].run()
      "events:\n" + steady(events.toList) + "event_catalogue:\n" + steady(records.toList) + "event_import_parts:\n" +
        steady(parts.toList) + "event_datings:\n" + steady(datings.toList)

  private def transcript: String =
    val sb = StringBuilder()
    val plugins =
      ServerPlugins.load(loaded, core = CoreBudget(() => java.time.Instant.now(java.time.Clock.systemUTC())))
    def handlers(f: FakeMediaHttp) =
      CatalogueWiring(Deps(cfg, ds, ds, plugins = plugins), f, () => DeepTime.SignedYear(2026L))
    def run(name: String, req: EventImportRequest): Unit =
      val f = FakeMediaHttp(route)
      val r = handlers(f).eventImporter.runImport(CatalogRecord.Provider(req.provider.value), req, None, _ => ())
      sb ++= s"## import $name\n" + requests(f) + s"report: ${r.asJson.noSpaces}\n"
    sb ++= s"catalogues: ${handlers(FakeMediaHttp(route)).eventImporter.catalogues.asJson.noSpaces}\n"
    run(
      "gvp (dry run)",
      EventImportRequest(EventImportRequest.Provider("gvp"), dryRun = EventImportRequest.DryRun(true))
    )
    run("gvp", EventImportRequest(EventImportRequest.Provider("gvp"), dryRun = EventImportRequest.DryRun(false)))
    run("noaa", EventImportRequest(EventImportRequest.Provider("noaa"), dryRun = EventImportRequest.DryRun(false)))
    run(
      "wikidata",
      EventImportRequest(EventImportRequest.Provider("wikidata"), dryRun = EventImportRequest.DryRun(false))
    )
    run("gvp again", EventImportRequest(EventImportRequest.Provider("gvp"), dryRun = EventImportRequest.DryRun(false)))
    run(
      "noaa again",
      EventImportRequest(EventImportRequest.Provider("noaa"), dryRun = EventImportRequest.DryRun(false))
    )
    run(
      "wikidata again, --refresh",
      EventImportRequest(
        EventImportRequest.Provider("wikidata"),
        dryRun = EventImportRequest.DryRun(false),
        refresh = EventImportRequest.Refresh(true)
      )
    )
    run(
      "smithsonian, --limit 1",
      EventImportRequest(
        EventImportRequest.Provider("smithsonian"),
        dryRun = EventImportRequest.DryRun(false),
        refresh = EventImportRequest.Refresh(true),
        limit = Some(EventImportRequest.Limit(1))
      )
    )
    sb ++= "## stored after the imports\n" + stored
    // stale rows: a GVP record as V085 stored it, spans that no longer match the catalogues' own records
    connect(ds):
      val _ = sql"""UPDATE event_catalogue SET record = (record - 'central' - 'plusMinus' - 'datingMethod' - 'raw')
                      || '{"timeSpan":{"notBefore":-2251,"beginBy":null,"endFrom":null,"notAfter":2750},
                           "description":"Confirmed Eruption; VEI 5; dated by Tephrochronology"}'::jsonb
                    WHERE provider = 'gvp' AND provider_id = (SELECT min(provider_id) FROM event_catalogue WHERE provider = 'gvp')""".update
        .run()
      val _ = sql"""UPDATE event_catalogue SET record = record
                      || '{"timeSpan":{"notBefore":-5000,"beginBy":null,"endFrom":null,"notAfter":5000}}'::jsonb
                    WHERE provider IN ('noaa', 'wikidata')""".update.run()
    val dry = handlers(FakeMediaHttp(route)).eventImporter.runRederive(None, dryRun = true, _ => ())
    sb ++= s"## rederive (dry run)\nreport: ${dry.asJson.noSpaces}\n"
    val offline = FakeMediaHttp(url => Left(s"no network in a re-derivation: $url"))
    val r = handlers(offline).eventImporter.runRederive(None, dryRun = false, _ => ())
    sb ++= s"## rederive\n" + requests(offline) + s"report: ${r.asJson.noSpaces}\n"
    val one = handlers(FakeMediaHttp(route)).eventImporter
      .runRederive(Some(CatalogRecord.Provider("noaa")), dryRun = false, _ => ())
    sb ++= s"## rederive noaa\nreport: ${one.asJson.noSpaces}\n"
    sb ++= "## stored after rederive\n" + stored
    sb.result()

  private val transcriptFile = Path.of("modules/server/src/test/resources/traffic/events.txt")

  test("the event catalogues send exactly the recorded requests and store exactly the recorded results"):
    val now = transcript
    if sys.env.get("HISTMERSION_UPDATE_TRAFFIC").contains("1") then
      Files.createDirectories(transcriptFile.getParent)
      Files.writeString(transcriptFile, now, UTF_8)
    val recorded = Files.readString(transcriptFile, UTF_8)
    if recorded != now then
      val (a, b) = (recorded.linesIterator.toVector, now.linesIterator.toVector)
      val at = a.indices.find(i => b.lift(i) != Some(a(i))).getOrElse(a.size)
      fail(
        s"the events differ from $transcriptFile at line ${at + 1}:\n  recorded: ${a.lift(at).getOrElse("<end>")}\n" +
          s"  now:      ${b.lift(at).getOrElse("<end>")}"
      )
