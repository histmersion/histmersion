package com.histmersion.server

import com.histmersion.domain.catalogue.places.Geocoding

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.db.*
import com.histmersion.pluginhost.HttpAnswer
import com.histmersion.server.media.FakeMediaHttp
import com.augustnagro.magnum.*
import io.circe.{parser, Json}
import io.circe.syntax.*
import ox.supervised

import java.io.ByteArrayInputStream
import java.net.{URI, URLEncoder}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import javax.sql.DataSource

/** What the catalogue answers, on a crafted collection, recorded on the code before the catalogue moved into its own
  * domain: places and their names, overrides, merges, removals and renames, the hierarchy, footprints and the map's
  * shapes; the period presets seeded from the bundled file and a PeriodO dump imported, and period searches; events
  * put, patched, renamed and deleted, an event catalogue imported (GVP, from the fixture, no socket) and an imported
  * event corrected; attestations, an event's links and leads; the map feed (`geo-when`), the histogram, every tile
  * layer's tiles (their bytes' digest); passages; the lint report; the bundle's catalogue entries, their import into an
  * empty collection, and a merge plan over a changed copy. Every read over REST, the catalogue's MCP tools as well.
  *
  * Record ids are random, so a uid is written as the record it names (`place:<id>`, …), a timestamp as `TS` and a job
  * id as `UUID`. `HISTMERSION_UPDATE_ORACLE=1` rewrites the files. Skipped without HISTMERSION_TEST_DB_URL.
  */
class CatalogueOracleSuite extends DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  override val munitTimeout = scala.concurrent.duration.Duration(10, "min")

  private val oracleDir = Path.of("modules/server/src/test/resources/oracle/catalogue")
  private val update = sys.env.get("HISTMERSION_UPDATE_ORACLE").contains("1")

  private val tmp = Files.createTempDirectory("histmersion-catalogue-oracle")
  private lazy val base = AppConfig.load()
  private lazy val scratch = ScratchDatabases("histmersion_s8o", base.db.copy(url = testDb.get))

  override def afterSuite(): Unit =
    if testDb.isDefined then scratch.dropAll()

  // ── the servers ────────────────────────────────────────────────────────────────────────────

  /** The bundled period presets, in a data directory of the suite's own. */
  private lazy val dataDir: Path =
    val d = tmp.resolve("init-data")
    Files.createDirectories(d.resolve("periods"))
    val _ = Files.copy(Path.of("db/periods/periodo-seed.json"), d.resolve("periods/periodo-seed.json"))
    d

  private def cfgFor(name: String): AppConfig =
    base.copy(
      db = base.db.copy(url = scratch.urlOf(s"histmersion_s8o_${name}_test")),
      backups = base.backups.copy(dir = tmp.resolve(s"$name-backups").toString),
      bundles = base.bundles.copy(dir = tmp.resolve(s"$name-bundles").toString),
      converter = base.converter.copy(spoolDir = tmp.resolve(s"$name-spool").toString),
      acquire = base.acquire.copy(spoolDir = tmp.resolve(s"$name-acquire").toString),
      init = base.init.copy(auto = false, dataDir = dataDir.toString),
      runtime = base.runtime.copy(dataDir = tmp.resolve(s"$name-data").toString),
      basemap = base.basemap.copy(dir = tmp.resolve(s"$name-basemap").toString)
    )

  private def withServer[A](name: String)(body: String => A): A = withServerOn(cfgFor(name))(body)

  private def withServerOn[A](cfg: AppConfig)(body: String => A): A =
    supervised {
      val b = Composition.server(cfg, "127.0.0.1", 0, OfflineConverter()).start()
      try body(s"http://127.0.0.1:${b.port}")
      finally b.stop()
    }

  private val http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()
  private def enc(s: String) = URLEncoder.encode(s, UTF_8)

  private def raw(req: HttpRequest.Builder): HttpResponse[Array[Byte]] =
    http.send(req.build(), HttpResponse.BodyHandlers.ofByteArray())

  /** Status and body: every read is recorded as it answers, a refusal included. */
  private def get(url: String): String =
    val r = raw(HttpRequest.newBuilder(URI.create(url)))
    s"${r.statusCode} ${String(r.body, UTF_8)}"

  private def send(method: String, url: String, body: String, contentType: String = "application/json"): String =
    val r = raw(
      HttpRequest
        .newBuilder(URI.create(url))
        .header("content-type", contentType)
        .method(method, HttpRequest.BodyPublishers.ofString(body))
    )
    s"${r.statusCode} ${String(r.body, UTF_8)}"
  private def post(url: String, body: String = "", contentType: String = "application/json"): String =
    send("POST", url, body, contentType)

  private def ok(answer: String): String =
    assert(answer.startsWith("200 "), answer.take(800))
    answer.drop(4)

  /** An MCP tool's answer, its Markdown text. */
  private def tool(url: String, name: String, args: Json): String =
    val r = raw(
      HttpRequest
        .newBuilder(URI.create(s"$url/mcp"))
        .header("content-type", "application/json")
        .header("accept", "application/json, text/event-stream")
        .POST(
          HttpRequest.BodyPublishers.ofString(
            Json
              .obj(
                "jsonrpc" -> 2.asJson,
                "id" -> 1.asJson,
                "method" -> "tools/call".asJson,
                "params" -> Json.obj("name" -> name.asJson, "arguments" -> args)
              )
              .noSpaces
          )
        )
    )
    val text = String(r.body, UTF_8)
    val payload = text.linesIterator.find(_.startsWith("data:")).map(_.stripPrefix("data:").trim).getOrElse(text)
    parser
      .parse(payload)
      .toOption
      .flatMap(_.hcursor.downField("result").downField("content").downArray.downField("text").as[String].toOption)
      .getOrElse(s"${r.statusCode} $text")

  private def awaitJob(url: String, id: String, left: Int = 600): String =
    val j = get(s"$url/api/jobs/$id")
    if j.contains("\"done\"") || j.contains("\"failed\"") || left == 0 then j
    else { Thread.sleep(100); awaitJob(url, id, left - 1) }

  private def field(json: String, name: String): String =
    s""""$name"\\s*:\\s*"([^"]*)"""".r.findFirstMatchIn(json).map(_.group(1)).getOrElse(fail(s"no $name in $json"))

  // ── the crafted collection ─────────────────────────────────────────────────────────────────

  private def polygon(lon0: Double, lat0: Double, lon1: Double, lat1: Double): String =
    s"""{"type":"Polygon","coordinates":[[[$lon0,$lat0],[$lon1,$lat0],[$lon1,$lat1],[$lon0,$lat1],[$lon0,$lat0]]]}"""

  /** Two dated polities, one inside the other, and a sea; LPF, with names and a relation. */
  private val regions =
    s"""{"type":"FeatureCollection","features":[
       |{"@id":"zz_land","type":"Feature","properties":{"title":"Zzland"},
       | "when":{"timespans":[{"start":{"in":"-2000"},"end":{"in":"-1000"}}]},
       | "names":[{"toponym":"Zzland","lang":"en"},{"toponym":"Zzlandia","lang":"la"}],
       | "geometry":${polygon(30, 36, 40, 42)}},
       |{"@id":"zz_march","type":"Feature","properties":{"title":"Zzmarch"},
       | "when":{"timespans":[{"start":{"in":"-1600"},"end":{"in":"-1200"}}]},
       | "names":[{"toponym":"Zzmarch","lang":"en"}],
       | "geometry":${polygon(33, 38, 36, 41)},
       | "relations":[{"relationType":"gvp:broaderPartitive","relationTo":"zz_land"}]},
       |{"@id":"zz_sea","type":"Feature","properties":{"title":"Zzsea"},
       | "geometry":${polygon(20, 30, 29, 35)}}
       |]}""".stripMargin

  /** Sites: dated, open-ended, undated, and two that are one place. */
  private val sites =
    """{"type":"FeatureCollection","features":[
      |{"@id":"zz_hill","type":"Feature","properties":{"title":"Zzhill"},
      | "when":{"timespans":[{"start":{"in":"-1650"},"end":{"in":"-1180"}}]},
      | "names":[{"toponym":"Zzhill","lang":"en"},{"toponym":"Zzhügel","lang":"de"}],
      | "geometry":{"type":"Point","coordinates":[34.6,40.0]}},
      |{"@id":"zz_port","type":"Feature","properties":{"title":"Zzport"},
      | "when":{"timespans":[{"start":{"in":"-1400"}}]},
      | "geometry":{"type":"Point","coordinates":[35.5,39.2]}},
      |{"@id":"zz_well","type":"Feature","properties":{"title":"Zzwell"},
      | "geometry":{"type":"Point","coordinates":[34.9,39.7]}},
      |{"@id":"zz_mound","type":"Feature","properties":{"title":"Zzmound"},
      | "when":{"timespans":[{"start":{"in":"-1500"},"end":{"in":"-1300"}}]},
      | "geometry":{"type":"Point","coordinates":[37.2,38.1]}},
      |{"@id":"zz_mound_dup","type":"Feature","properties":{"title":"Zzmound (again)"},
      | "geometry":{"type":"Point","coordinates":[37.21,38.11]}},
      |{"@id":"zz_lonely","type":"Feature","properties":{"title":"Zzlonely"},
      | "geometry":{"type":"Point","coordinates":[25.0,32.0]}}
      |]}""".stripMargin

  private def finding(claim: String, lon: Double, lat: Double, from: Long, to: Long, region: String): Json =
    Json.obj(
      "claim" -> claim.asJson,
      "region" -> region.asJson,
      "lon" -> lon.asJson,
      "lat" -> lat.asJson,
      "periodStart" -> from.asJson,
      "periodEnd" -> to.asJson,
      "tags" -> List("oracle").asJson,
      "confidence" -> "high".asJson
    )

  private def source(title: String, year: Int, body: String, findings: List[Json]): String =
    Json
      .obj(
        "title" -> title.asJson,
        "authors" -> List("Doe, Jane").asJson,
        "year" -> year.asJson,
        "topics" -> List("bronze age").asJson,
        "regions" -> List("Zzland").asJson,
        "tags" -> List("zz-oracle").asJson,
        "body" -> body.asJson,
        "findings" -> Json.fromValues(findings)
      )
      .noSpaces

  private val bodyHill =
    """# Zzhill
      |
      |## The battle
      |
      |The battle of Zzhill was fought below the walls of Zzhill around 1300 BC; Zzport sent ships.
      |Zzhill burned soon after, and the battle of Zzhill was remembered in the songs of Zzland.
      |
      |## The well
      |
      |The Zzwell was dug in the time of the Zzmarch kings.
      |""".stripMargin

  private val bodyVolcano =
    """# Eruptions
      |
      |The eruption of Mount Zzvolc buried the fields east of Zzport in ash. Pumice from the eruption reached Zzhill.
      |""".stripMargin

  private def eventPut(
      label: String,
      kind: String,
      from: Long,
      to: Long,
      place: Option[String] = None,
      extra: List[(String, Json)] = Nil
  ): String =
    Json
      .fromFields(
        List(
          "label" -> label.asJson,
          "kind" -> kind.asJson,
          "timeSpan" -> Json.obj("notBefore" -> from.asJson, "notAfter" -> to.asJson)
        ) ++ place.map(p => "placeId" -> p.asJson).toList ++ extra
      )
      .noSpaces

  /** The GVP catalogue, answering from its fixture; nothing else answers. */
  private def gvp(url: String): Either[String, HttpAnswer] =
    def fixture = scala.io.Source.fromResource("events/gvp.json")(using scala.io.Codec.UTF8).mkString
    if url.contains("GVP-VOTW") then
      FakeMediaHttp.json(
        if url.contains("StartDateYear%3E%3D-2000+") then fixture
        else """{"type":"FeatureCollection","features":[],"numberMatched":0}"""
      )
    else Left(s"unexpected $url")

  /** Writes, each recorded with its answer. */
  private def craft(url: String, ds: DataSource): String =
    val log = StringBuilder()
    def step(name: String, answer: String): String =
      log ++= s"== $name\n$answer\n"
      answer
    val _ = step("periods seed", post(s"$url/api/periods/seed"))
    val dump = scala.io.Source.fromResource("periods/periodo_dump.json")(using scala.io.Codec.UTF8).mkString
    val _ = step("periods import", post(s"$url/api/periods/import?dataset=local", dump, "text/plain"))
    val _ = step("places import regions", post(s"$url/api/places/import?kind=region", regions, "text/plain"))
    val _ = step("places import sites", post(s"$url/api/places/import?kind=site", sites, "text/plain"))
    val _ = step(
      "places add",
      post(
        s"$url/api/places",
        """{"label":"Zzcamp","kind":"site","lon":36.4,"lat":39.9,"source":"a survey map","id":"zz_camp","names":["Zzlager"],"lang":"de","periodStart":-1350,"periodEnd":-1250}"""
      )
    )
    val _ = step(
      "places add, refused",
      post(s"$url/api/places", """{"label":"","kind":"site","lon":1,"lat":2,"source":"x"}""")
    )
    val h = ok(step("places build-hierarchy", post(s"$url/api/places/build-hierarchy")))
    val _ = step("places build-hierarchy job", awaitJob(url, field(h, "job")))
    val _ = step("places name add", post(s"$url/api/places/zz_hill/names", """{"name":"Zzhill Höyük","lang":"tr"}"""))
    val _ = step("places name add 2", post(s"$url/api/places/zz_port/names", """{"name":"Old Zzport"}"""))
    val _ = step("places name remove", post(s"$url/api/places/zz_port/names/remove", """{"name":"Old Zzport"}"""))
    val _ = step(
      "places name remove, imported",
      post(s"$url/api/places/zz_hill/names/remove", """{"name":"Zzhügel","lang":"de"}""")
    )
    val _ = step(
      "places override kind and period",
      post(
        s"$url/api/places/zz_well/override",
        """{"kind":"site","periodStart":-1500,"periodEnd":-1100,"reason":"the excavation report dates the well"}"""
      )
    )
    val _ = step(
      "places override point",
      post(s"$url/api/places/zz_port/override", """{"pointLat":39.25,"pointLon":35.55,"reason":"the harbour survey"}""")
    )
    val _ = step(
      "places merge",
      post(s"$url/api/places/zz_mound_dup/merge", """{"into":"zz_mound","reason":"one tell, two records"}""")
    )
    val _ = step("places remove", post(s"$url/api/places/zz_lonely/remove", """{"reason":"a phantom"}"""))
    val _ = step("places rename", post(s"$url/api/places/zz_camp/rename", """{"to":"zz_camp_north"}"""))
    val _ = step(
      "source hill",
      send(
        "PUT",
        s"$url/api/sources/zzhill1990_battle",
        source(
          "The Battle of Zzhill",
          1990,
          bodyHill,
          List(
            finding("the battle of Zzhill was fought around 1300 BC", 34.6, 40.0, -1310, -1290, "Zzhill"),
            finding("Zzport sent ships to Zzhill", 35.5, 39.2, -1310, -1290, "Zzport"),
            finding("the Zzwell is of the Zzmarch period", 34.9, 39.7, -1500, -1300, "Zzwell")
          )
        )
      )
    )
    val _ = step(
      "source volcano",
      send(
        "PUT",
        s"$url/api/sources/zzvolc2004_ash",
        source(
          "Ash over Zzport",
          2004,
          bodyVolcano,
          List(finding("ash from Mount Zzvolc fell east of Zzport", 36.0, 39.0, -1620, -1600, "Zzland"))
        )
      )
    )
    val _ = step("places rebuild", post(s"$url/api/places/rebuild"))
    val png = java.util.Base64.getEncoder.encodeToString(
      Array[Byte](0x89.toByte, 'P', 'N', 'G', 13, 10, 26, 10, 1, 2, 3)
    )
    val _ = step(
      "figures",
      post(
        s"$url/api/sources/zzhill1990_battle/assets",
        s"""[{"name":"fig1.png","mime":"image/png","base64":"$png"},{"name":"fig2.png","mime":"image/png","base64":"${png}AA=="}]"""
      )
    )
    val _ = step(
      "figure placed",
      send(
        "PATCH",
        s"$url/api/sources/zzhill1990_battle/assets/fig1.png",
        """{"caption":"The walls of Zzhill","lat":40.0,"lon":34.6,"periodStart":-1300,"periodEnd":-1250}"""
      )
    )
    val _ = step(
      "figure placed 2",
      send("PATCH", s"$url/api/sources/zzhill1990_battle/assets/fig2.png", """{"lat":39.2,"lon":35.5}""")
    )
    val _ = step(
      "event battle",
      send(
        "PUT",
        s"$url/api/events/zz_battle",
        eventPut(
          "Battle of Zzhill",
          "battle",
          -1310,
          -1290,
          Some("zz_hill"),
          List(
            "citekey" -> "zzhill1990_battle".asJson,
            "description" -> "fought below the walls".asJson,
            "datings" -> Json.arr(
              Json.obj(
                "eventId" -> "zz_battle".asJson,
                "citekey" -> "zzhill1990_battle".asJson,
                "timeSpan" -> Json.obj("notBefore" -> (-1310).asJson, "notAfter" -> (-1290).asJson),
                "method" -> "historical".asJson
              )
            ),
            "participants" -> Json.arr(
              Json.obj("eventId" -> "zz_battle".asJson, "actor" -> "the Zzport fleet".asJson, "role" -> "ally".asJson)
            )
          )
        )
      )
    )
    val _ = step(
      "event fire",
      send(
        "PUT",
        s"$url/api/events/zz_fire",
        eventPut(
          "Zzhill burns",
          "destruction",
          -1290,
          -1270,
          Some("zz_hill"),
          List(
            "edges" -> Json.arr(
              Json.obj(
                "srcId" -> "zz_fire".asJson,
                "dstId" -> "zz_battle".asJson,
                "kind" -> "after".asJson,
                "citekey" -> "zzhill1990_battle".asJson
              )
            ),
            "parentId" -> "zz_battle".asJson
          )
        )
      )
    )
    val _ = step(
      "event war",
      send(
        "PUT",
        s"$url/api/events/zz_war",
        eventPut(
          "The Zzmarch war",
          "war",
          -1320,
          -1280,
          None,
          List("geometry" -> parser.parse(polygon(33.5, 38.5, 35.8, 40.5)).toOption.get.noSpaces.asJson)
        )
      )
    )
    val _ = step(
      "event ash",
      send("PUT", s"$url/api/events/zz_ash", eventPut("Ash over Zzport", "eruption", -1620, -1600, Some("zz_port")))
    )
    val _ =
      step("event gone", send("PUT", s"$url/api/events/zz_gone", eventPut("A deleted event", "event", -900, -800)))
    val _ = step("event refused", send("PUT", s"$url/api/events/zz_bad", """{"label":"bad","scale":"huge"}"""))
    val _ = step(
      "event patch",
      send("PATCH", s"$url/api/events/zz_war", """{"description":"a war of the march lords","scale":"regional"}""")
    )
    val _ = step("event rename", post(s"$url/api/events/zz_ash/rename", """{"to":"zz_ashfall"}"""))
    val _ = step("event delete", send("DELETE", s"$url/api/events/zz_gone", ""))
    // the catalogue import, then a person's edit of an imported event: a correction
    val catalog = com.histmersion.server.CatalogueWiring(
      Deps(cfgFor("cat"), ds, ds, plugins = ServerPlugins.load(oracleLoaded)),
      FakeMediaHttp(gvp),
      () => 2026L
    )
    val job = catalog.eventImports
      .importJob(
        com.histmersion.domain.Caller.Local,
        EventImportRequest("gvp", dryRun = false, from = Some(-2000), to = Some(-1000))
      )
      .fold(e => fail(e.message), identity)
    val _ = step("events import gvp", awaitJob(url, job.id))
    val imported = connect(ds)(
      sql"SELECT id FROM events WHERE catalogue IS NOT NULL ORDER BY id".query[String].run().toList
    )
    imported.headOption.foreach { id =>
      val _ = step(
        "event patch imported",
        send("PATCH", s"$url/api/events/$id", """{"label":"An eruption, named by a person","description":"edited"}""")
      )
    }
    val _ = step(
      "attestation place",
      post(
        s"$url/api/attestations",
        """{"citekey":"zzhill1990_battle","targetType":"place","targetId":"zz_hill","quote":"The battle of Zzhill","charStart":30,"charEnd":50}"""
      )
    )
    val _ = step(
      "attestation event",
      post(
        s"$url/api/attestations",
        """{"citekey":"zzhill1990_battle","targetType":"event","targetId":"zz_battle","quote":"fought below the walls","page":"12"}"""
      )
    )
    val a3 = step(
      "attestation renamed place",
      post(
        s"$url/api/attestations",
        """{"citekey":"zzvolc2004_ash","targetType":"place","targetId":"zz_camp","quote":"Zzport","note":"by its old id"}"""
      )
    )
    val _ = step(
      "attestation refused",
      post(s"$url/api/attestations", """{"citekey":"zzhill1990_battle","targetType":"planet","targetId":"x"}""")
    )
    val _ = step(
      "attestation missing source",
      post(s"$url/api/attestations", """{"citekey":"zznone1900_x","targetType":"place","targetId":"zz_hill"}""")
    )
    val a3id = parser.parse(a3.drop(4)).toOption.flatMap(_.hcursor.downField("id").as[Long].toOption).getOrElse(0L)
    val _ = step("attestation patch", send("PATCH", s"$url/api/attestations/$a3id", """{"note":"patched"}"""))
    val _ = step("event links recompute", post(s"$url/api/events/zz_battle/links", """{"recompute":true}"""))
    val links = get(s"$url/api/events/zz_battle/links").drop(4)
    val firstCandidate = parser
      .parse(links)
      .toOption
      .flatMap(_.hcursor.downField("candidates").downArray.downField("citekey").as[String].toOption)
    firstCandidate.foreach(ck =>
      val _ = step("event links confirm", post(s"$url/api/events/zz_battle/links", s"""{"confirm":["$ck"]}"""))
    )
    val _ = step("event links ash recompute", post(s"$url/api/events/zz_ashfall/links", """{"recompute":true}"""))
    log.result()

  private lazy val oracleLoaded: LoadedConfig =
    AppConfig
      .loadWithUserText(org.ekrich.config.ConfigFactory.empty, "histmersion.events.gvp.request-interval-ms = 0\n")
      .fold(e => fail(e), identity)

  // ── the reads ──────────────────────────────────────────────────────────────────────────────

  private def places(url: String): String =
    val gets = List(
      "places",
      "places?q=zz",
      "places?q=Zzhill",
      "places?q=zzmound",
      "places?q=Zzhügel",
      "places?q=zz&asOf=-1700",
      "places?q=zz&asOf=-1300&limit=3",
      "places/zz_hill",
      "places/zz_hill?counts=true",
      "places/zz_hill?asOf=-1300",
      "places/zz_mound_dup",
      "places/zz_camp",
      "places/zz_lonely",
      "places/zz_none",
      "places/zz_land/children",
      "places/zz_land/children?asOf=-1300",
      "places/zz_land/tree",
      "places/zz_land/tree?asOf=-1300&depth=1",
      "places/zz_march/footprints",
      "places/zz_march/footprints?asOf=-1400",
      "places/zz_hill/links",
      "places/names",
      "places/names?place=zz_hill",
      "places/overrides",
      "places/overrides?place=zz_well",
      "places/geo?kind=region",
      "places/geo?kind=region&asOf=-1500",
      "places/geo?kind=region&from=-1700&to=-1100",
      "places/geo?kind=region&from=-1700&to=-1100&pick=latest",
      "places/geo?kind=site&asOf=-1300",
      "places/geo?kind=site&from=-1400&to=-1200&pick=start",
      "places/geo?pick=nonsense"
    )
    val tools = List(
      "places" -> Json.obj("query" -> "zz".asJson),
      "places" -> Json.obj("id" -> "zz_hill".asJson),
      "places" -> Json.obj("name" -> "Zzhill".asJson, "asOf" -> (-1300).asJson),
      "places" -> Json.obj("id" -> "zz_camp".asJson),
      "place_names" -> Json.obj("placeId" -> "zz_hill".asJson)
    )
    (gets.map(g => s"== GET /api/$g\n${get(s"$url/api/$g")}") ++
      tools.map((n, a) => s"== tool $n ${a.noSpaces}\n${tool(url, n, a)}")).mkString("\n")

  private def periods(url: String): String =
    val gets = List(
      "periods",
      "periods?q=bronze",
      "periods?q=Zzq",
      "periods?region=Zzqland",
      "periods?from=-1500&to=-1300",
      "periods?q=bronze&from=-2000&to=-1800",
      "periods/zzqauthlba",
      "periods/zzqauthbronze",
      "periods/zz_none"
    )
    val tools = List(
      "periods" -> Json.obj("query" -> "Zzq".asJson),
      "periods" -> Json.obj("id" -> "zzqauthlba".asJson),
      "periods" -> Json.obj("from" -> (-1500).asJson, "to" -> (-1300).asJson, "query" -> "bronze".asJson)
    )
    (gets.map(g => s"== GET /api/$g\n${get(s"$url/api/$g")}") ++
      tools.map((n, a) => s"== tool $n ${a.noSpaces}\n${tool(url, n, a)}")).mkString("\n")

  private def events(url: String): String =
    val gets = List(
      "events",
      "events?text=zz",
      "events?kind=battle",
      "events?placeId=zz_hill",
      "events?from=-1320&to=-1280",
      "events?place=zz_land",
      "events?q=battle",
      "events?tag=oracle",
      "events?tier=catalogue",
      "events?context=true",
      "events?area=33,38,36,41",
      "events?historicity=attested&limit=2&offset=1",
      "events/geo",
      "events/geo?from=-1320&to=-1280",
      "events/geo?place=zz_march",
      "events/zz_battle",
      "events/zz_fire",
      "events/zz_war",
      "events/zz_ash",
      "events/zz_ashfall",
      "events/zz_gone",
      "events/corrections",
      "events/corrections?status=open",
      "events/corrections?limit=1&offset=0",
      "events/catalogues",
      "events/leads",
      "events/leads?from=-1700&to=-1200"
    )
    val tools = List(
      "events" -> Json.obj("query" -> "zz".asJson),
      "events" -> Json.obj("id" -> "zz_battle".asJson),
      "events" -> Json.obj("from" -> (-1700).asJson, "to" -> (-1000).asJson, "limit" -> 5.asJson),
      "event_corrections" -> Json.obj(),
      "event_corrections" -> Json.obj("status" -> "open".asJson)
    )
    (gets.map(g => s"== GET /api/$g\n${get(s"$url/api/$g")}") ++
      tools.map((n, a) => s"== tool $n ${a.noSpaces}\n${tool(url, n, a)}")).mkString("\n")

  private def evidence(url: String): String =
    val gets = List(
      "attestations",
      "attestations?citekey=zzhill1990_battle",
      "attestations?targetType=place&targetId=zz_hill",
      "attestations?targetType=place&targetId=zz_camp",
      "attestations?targetType=event&targetId=zz_battle&limit=1",
      "attestations/1",
      "attestations/999",
      "events/zz_battle/links",
      "events/zz_ashfall/links",
      "events/zz_war/links"
    )
    val tools = List(
      "attestations" -> Json.obj("citekey" -> "zzhill1990_battle".asJson),
      "attestations" -> Json.obj("targetType" -> "place".asJson, "targetId" -> "zz_camp_north".asJson),
      "event_links" -> Json.obj("id" -> "zz_battle".asJson),
      "event_links" -> Json.obj("from" -> (-1700).asJson, "to" -> (-1200).asJson)
    )
    (gets.map(g => s"== GET /api/$g\n${get(s"$url/api/$g")}") ++
      tools.map((n, a) => s"== tool $n ${a.noSpaces}\n${tool(url, n, a)}")).mkString("\n")

  /** The z/x/y of the tile holding a point. */
  private def tileOf(z: Int, lon: Double, lat: Double): (Int, Long, Long) =
    val n = 1L << z
    val x = ((lon + 180) / 360 * n).toLong
    val r = math.toRadians(lat)
    val y = ((1 - math.log(math.tan(r) + 1 / math.cos(r)) / math.Pi) / 2 * n).toLong
    (z, x, y)

  private def tileLine(url: String, path: String): String =
    val r = raw(HttpRequest.newBuilder(URI.create(s"$url/api/tiles/$path")))
    val sha = com.histmersion.util.data.exchange.BundleFormat.sha256Hex(r.body)
    val etag = r.headers().firstValue("etag").orElse("-")
    s"$path ${r.statusCode} ${r.body.length} $sha $etag"

  private def map(url: String): String =
    val gets = List(
      "findings",
      "findings?place=zz_land",
      "findings?place=zz_march",
      "findings?place=zz_hill",
      "findings?place=Zzhill",
      "findings?from=-1320&to=-1280",
      "findings?minLon=34&minLat=39&maxLon=35&maxLat=40.5",
      "findings?place=zz_camp",
      "findings/page?place=zz_land&limit=2",
      "findings?region=Zzland",
      "aggregate",
      "aggregate?from=-1700&to=-1200",
      "aggregate?from=-1700&to=-1200&unit=findings",
      "aggregate?from=-1700&to=-1200&unit=starts",
      "aggregate?by=region&from=-1700&to=-1200",
      "aggregate?by=region&from=-1700&to=-1200&place=zz_land",
      "aggregate?by=time,region&from=-1700&to=-1200",
      "aggregate?by=region,time&from=-1700&to=-1200&depth=2&samples=1",
      "aggregate?by=time&from=-1700&to=-1200&depth=2",
      "aggregate?by=nonsense"
    )
    val tools = List(
      "geo_when" -> Json.obj("places" -> List("zz_land").asJson),
      "geo_when" -> Json.obj("from" -> (-1320).asJson, "to" -> (-1280).asJson),
      "histogram" -> Json.obj("from" -> (-1700).asJson, "to" -> (-1200).asJson),
      "histogram" -> Json.obj("places" -> List("zz_march").asJson, "unit" -> "findings".asJson)
    )
    val coords = List(0, 3, 6, 9, 11).flatMap(z => List(tileOf(z, 34.6, 40.0), tileOf(z, 35.5, 39.2))).distinct
    val variants = List(
      "",
      "?from=-1320&to=-1280",
      "?from=-1700&to=-1600",
      "?undated=true",
      "?undated=true&from=-1320&to=-1280",
      "?siteType=settlement",
      "?place=zz_land",
      "?kind=battle",
      "?tier=catalogue",
      "?text=Zz",
      "?kind=figure",
      "?provider=source",
      "?license=none"
    )
    val tiles = for
      layer <- List("sites", "events", "media")
      (z, x, y) <- coords
      v <- variants
    yield tileLine(url, s"$layer/$z/$x/$y$v")
    val refused = List("nonsense/0/0/0", "sites/40/0/0", "sites/2/9/0").map(tileLine(url, _))
    (gets.map(g => s"== GET /api/$g\n${get(s"$url/api/$g")}") ++
      tools.map((n, a) => s"== tool $n ${a.noSpaces}\n${tool(url, n, a)}") ++
      List("== tiles: path status length sha256 etag", (tiles ++ refused).mkString("\n"))).mkString("\n")

  private def passages(url: String): String =
    val gets = List(
      s"sources/zzhill1990_battle/passages?q=${enc("battle of Zzhill")}",
      s"sources/zzhill1990_battle/passages?q=zzhill&context=20&limit=2",
      s"sources/zzhill1990_battle/passages?q=${enc("\\bZz(port|well)\\b")}&regex=true",
      s"sources/zzvolc2004_ash/passages?q=eruption",
      s"sources/zznone1900_x/passages?q=x"
    )
    val tools = List(
      "passages" -> Json.obj("citekey" -> "zzhill1990_battle".asJson, "query" -> "Zzport".asJson),
      "passages" -> Json.obj("citekey" -> "zzvolc2004_ash".asJson, "query" -> "Zzvolc".asJson, "context" -> 10.asJson)
    )
    (gets.map(g => s"== GET /api/$g\n${get(s"$url/api/$g")}") ++
      tools.map((n, a) => s"== tool $n ${a.noSpaces}\n${tool(url, n, a)}")).mkString("\n")

  // ── normalisation ──────────────────────────────────────────────────────────────────────────

  private val UuidRe =
    "\\b[A-Z]-[0-9a-f]{8}\\b|[0-9a-f]{64}|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}".r
  private val TimeRe =
    "\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?(?:Z|[+-]\\d{2}(?::?\\d{2})?)?".r
  private val AddedRe = "(\"added(?:Date|_date)\" ?: ?)\"\\d{4}-\\d{2}-\\d{2}\"".r
  private val DayRe = "\\b\\d{4}-\\d{2}-\\d{2}\\b".r
  private val PlanRe = "plan [0-9a-f]{8}\\b".r
  private val DurationRe = "(\"(?:durationMs|elapsedMs|tookMillis|ageSeconds)\" ?: ?)\\d+".r

  /** uid → the record it names. */
  private def names(dss: List[(String, DataSource)]): Map[String, String] =
    val queries = List(
      "SELECT uid::text, 'source:' || citekey FROM sources",
      "SELECT uid::text, 'finding:' || id FROM findings",
      "SELECT uid::text, 'event:' || id FROM events",
      "SELECT uid::text, 'place:' || id FROM places",
      "SELECT uid::text, 'period:' || id FROM periods",
      "SELECT uid::text, 'attestation:' || citekey || ':' || target_id FROM attestations",
      "SELECT uid::text, 'tombstone:' || kind FROM tombstones",
      "SELECT alias_uid::text, 'alias-of:' || uid::text FROM uid_aliases"
    )
    def run(ds: DataSource, q: String): List[(String, String)] =
      scala.util.Try(connect(ds)(Sql.raw(q).frag.query[(String, String)].run().toList)).getOrElse(Nil)
    val records = dss.flatMap((_, ds) => queries.flatMap(run(ds, _)))
    val instances =
      dss.flatMap((n, ds) => run(ds, "SELECT id::text, 'x' FROM instance").map((id, _) => id -> s"instance:$n"))
    (records ++ instances).distinctBy(_._1).toMap

  private def normalise(text: String, names: Map[String, String]): String =
    val t1 = List(tmp.toString -> "TMP").foldLeft(text)((t, r) => t.replace(r._1, r._2))
    val t2 = UuidRe.replaceAllIn(
      t1,
      m =>
        scala.util.matching.Regex
          .quoteReplacement(names.getOrElse(m.matched, if m.matched.length != 36 then m.matched else "UUID"))
    )
    val t3 = DurationRe.replaceAllIn(AddedRe.replaceAllIn(TimeRe.replaceAllIn(t2, "TS"), "$1\"DAY\""), "$1N")
    PlanRe.replaceAllIn(t3, "plan PLAN")

  /** A line's revision, a bundle file's digest. */
  private def digest(key: String): Boolean = key.toLowerCase.matches(".*(rev|hash)(_?(after|before))?")

  private def json(text: String, names: Map[String, String]): String =
    def walk(j: Json): Json =
      j.arrayOrObject(
        j,
        a => Json.fromValues(a.map(walk)),
        o =>
          Json.fromFields(o.toList.map { (k, v) =>
            if digest(k) && v.isString then k -> Json.fromString("DIGEST") else k -> walk(v)
          })
      )
    parser.parse(text) match
      case Right(j) => normalise(walk(j).spaces2SortKeys, names)
      case Left(_)  => normalise(text, names)

  /** A recorded answer: its status, then its body as sorted JSON when it is JSON. */
  private def answers(text: String, names: Map[String, String]): String =
    text.linesIterator
      .map { l =>
        val m = "^(\\d{3}) (.*)$".r.findFirstMatchIn(l)
        m.fold(normalise(l, names))(m => s"${m.group(1)} ${json(m.group(2), names)}")
      }
      .mkString("", "\n", "\n")

  /** The bundle's entries of the catalogue's kinds: their lines. */
  private val CatalogueEntries = Set(
    "entities/places.ndjson",
    "entities/periods.ndjson",
    "entities/events.ndjson",
    "entities/attestations.ndjson",
    "entities/place-aliases.ndjson",
    "entities/event-aliases.ndjson",
    "entities/event-catalogue.ndjson",
    "entities/event-candidates.ndjson",
    "entities/event-checks.ndjson",
    "entities/corrections.ndjson",
    "entities/site-types.ndjson",
    "entities/tombstones.ndjson"
  )

  private def bundleText(bundle: Array[Byte], names: Map[String, String]): String =
    val in = java.util.zip.GZIPInputStream(ByteArrayInputStream(bundle))
    val out = StringBuilder()
    @scala.annotation.tailrec
    def loop(): Unit =
      val header = in.readNBytes(512)
      if header.length == 512 && header.exists(_ != 0) then
        val name = String(header.take(100), UTF_8).takeWhile(_ != 0)
        val prefix = String(header.slice(345, 500), UTF_8).takeWhile(_ != 0)
        val size = java.lang.Long.parseLong(String(header.slice(124, 136), UTF_8).trim.takeWhile(_ != 0), 8)
        val data = in.readNBytes(size.toInt)
        val _ = in.skip((512 - size % 512) % 512)
        val path = if prefix.isEmpty then name else s"$prefix/$name"
        if CatalogueEntries(path) || CatalogueEntries(path.replaceFirst("^[^/]+/", "")) then
          val lines = String(data, UTF_8).linesIterator.toList
          out ++= s"== $path (${lines.size} lines)\n"
          lines.foreach(l => out ++= json(l, names).linesIterator.map(_.trim).mkString(" ") + "\n")
        else out ++= s"== $path\n"
        loop()
    loop()
    out.toString

  /** The catalogue's tables, every row, normalised. */
  private val CatalogueTables = List(
    "places",
    "place_names",
    "place_relations",
    "place_aliases",
    "place_overrides",
    "periods",
    "events",
    "event_aliases",
    "event_datings",
    "event_edges",
    "event_participants",
    "event_catalogue",
    "event_candidates",
    "event_checks",
    "corrections",
    "attestations",
    "finding_places",
    "tombstones"
  )

  private def rows(ds: DataSource, names: Map[String, String]): String =
    val present = connect(ds)(
      sql"""SELECT table_name::text FROM information_schema.tables WHERE table_schema = 'public'""".query[String].run()
    ).toSet
    CatalogueTables
      .map { t =>
        if !present(t) then s"## $t (no such table)"
        else
          val rs = connect(ds)(Sql.raw(s"""SELECT to_jsonb(x)::text FROM "$t" x""").frag.query[String].run().toList)
          val normed = rs.map(r => json(r, names).linesIterator.map(_.trim).mkString(" ")).sorted
          (s"## $t ${normed.size} rows" :: normed).mkString("\n")
      }
      .mkString("", "\n", "\n")

  private def compare(name: String, actual: String): Unit =
    val file = oracleDir.resolve(name)
    if update then
      Files.createDirectories(oracleDir)
      val _ = Files.writeString(file, actual, UTF_8)
    else
      assert(Files.exists(file), s"$file is missing; write it with HISTMERSION_UPDATE_ORACLE=1")
      assertNoDiff(actual, Files.readString(file, UTF_8))

  private def bytes(url: String): Array[Byte] =
    val r = raw(HttpRequest.newBuilder(URI.create(url)))
    assertEquals(r.statusCode, 200, String(r.body, UTF_8).take(500))
    r.body

  test("places, periods, events, evidence, the map, passages, checks, bundle and merge, as recorded"):
    val cat = scratch("cat")
    val home = scratch("home")
    val all = List("cat" -> cat, "home" -> home)

    val (written, placesText, periodsText, eventsText, evidenceText, mapText, passagesText, lint, bundle) =
      withServer("cat") { url =>
        val w = craft(url, cat)
        (
          w,
          places(url),
          periods(url),
          events(url),
          evidence(url),
          map(url),
          passages(url),
          get(s"$url/api/lint"),
          bytes(s"$url/api/bundle")
        )
      }
    // the bundle into an empty collection; home takes it, cat goes on, home plans cat's next bundle
    val homeImport = withServer("home")(url => postBytes(s"$url/api/bundle/import", bundle))
    val next = withServer("cat") { url =>
      val _ = ok(post(s"$url/api/places/zz_port/rename", """{"to":"zz_harbour"}"""))
      val _ = ok(post(s"$url/api/places/zz_hill/names", """{"name":"Zzhill on the river"}"""))
      val _ = ok(send("PATCH", s"$url/api/events/zz_battle", """{"description":"fought below the walls, at dawn"}"""))
      val _ = ok(send("DELETE", s"$url/api/events/zz_fire", ""))
      val _ =
        ok(send("PUT", s"$url/api/events/zz_flood", eventPut("A flood", "flood", -1250, -1240, Some("zz_harbour"))))
      bytes(s"$url/api/bundle")
    }
    val plan = withServer("home") { url =>
      val _ = ok(send("PATCH", s"$url/api/events/zz_battle", """{"description":"fought below the walls, at dusk"}"""))
      val up = ok(postBytes(s"$url/api/bundle/upload", next))
      ok(post(s"$url/api/merge/plan", s"""{"bundle":"${field(up, "sha256")}"}"""))
    }
    val n = names(all)
    compare("written.txt", answers(written, n))
    compare("places.txt", answers(placesText, n))
    compare("periods.txt", answers(periodsText, n))
    compare("events.txt", answers(eventsText, n))
    compare("evidence.txt", answers(evidenceText, n))
    compare("map.txt", answers(mapText, n))
    compare("passages.txt", answers(passagesText, n))
    compare(
      "data.txt",
      s"# lint\n${answers(lint, n)}# the bundle's catalogue entries\n${bundleText(bundle, n)}" +
        s"# imported into an empty collection\n${answers(homeImport, n)}${rows(home, n)}" +
        s"# the merge plan of the next bundle\n${json(plan, n)}\n"
    )

  // ── the gazetteer's own loads: the bundled seed, a pinned extract, an LPF drop, the geocoder ──────

  /** A seed of the gazetteer as the bundle ships it: a top-level file, then a directory whose name gives the kind. */
  private val seedRegions =
    s"""{"type":"FeatureCollection","features":[
       |{"@id":"zzs_shore","type":"Feature","properties":{"title":"Zzshore"},
       | "names":[{"toponym":"Zzshore","lang":"en"},{"toponym":"Zzküste","lang":"de"}],
       | "geometry":${polygon(30, 36, 40, 42)}},
       |{"@id":"zzs_vale","type":"Feature","properties":{"title":"Zzvale"},
       | "when":{"timespans":[{"start":{"in":"-1800"},"end":{"in":"-1100"}}]},
       | "geometry":${polygon(33, 38, 36, 41)},
       | "relations":[{"relationType":"gvp:broaderPartitive","relationTo":"zzs_shore"}]}
       |]}""".stripMargin

  private val seedSites =
    """{"type":"FeatureCollection","features":[
      |{"@id":"zzs_tell","type":"Feature","properties":{"title":"Zztell"},
      | "when":{"timespans":[{"start":{"in":"-1700"},"end":{"in":"-1200"}}]},
      | "names":[{"toponym":"Zztell","lang":"en"},{"toponym":"Zzhöyük","lang":"tr"}],
      | "geometry":{"type":"Point","coordinates":[34.6,40.0]}},
      |{"@id":"zzs_ford","type":"Feature","properties":{"title":"Zzford"},
      | "geometry":{"type":"Point","coordinates":[35.1,39.4]}},
      |{"@id":"zzs_orphan","type":"Feature","properties":{"title":"Zzorphan"},
      | "geometry":{"type":"Point","coordinates":[34.0,39.0]},
      | "relations":[{"relationType":"gvp:broaderPartitive","relationTo":"zzs_nowhere"}]}
      |]}""".stripMargin

  private val FixtureSha = "20af2b1dd68f5bce2a5c9ea67bb06862038a7e007eb2d7872a4dfdb763954e5b"

  private def copyFixture(name: String, to: Path): Unit =
    val in = getClass.getResourceAsStream(s"/gazetteer/$name")
    try { val _ = Files.copy(in, to, java.nio.file.StandardCopyOption.REPLACE_EXISTING) }
    finally in.close()

  /** An init data directory with the seed, the period presets and a manifest pinning the GeoNames fixture (and a TGN
    * pin whose file is not there); the fixture in the extracts directory; the LPF fixtures in the drop folder.
    */
  private lazy val gazetteerCfg: AppConfig =
    val d = tmp.resolve("gaz-init")
    Files.createDirectories(d.resolve("places/sites"))
    Files.createDirectories(d.resolve("periods"))
    Files.createDirectories(d.resolve("gazetteer"))
    val _ = Files.copy(Path.of("db/periods/periodo-seed.json"), d.resolve("periods/periodo-seed.json"))
    val _ = Files.writeString(d.resolve("places/regions.json"), seedRegions, UTF_8)
    val _ = Files.writeString(d.resolve("places/sites/sites.json"), seedSites, UTF_8)
    val pins = List(
      GazetteerExtract(
        "geonames",
        "geonames-fixture.ndjson.gz",
        FixtureSha,
        438,
        2,
        "fixture-1",
        "GeoNames fixture",
        "CC BY 4.0",
        Some("GeoNames (geonames.org)")
      ),
      GazetteerExtract("tgn", "tgn-absent.ndjson.gz", "0" * 64, 1, 1, "fixture-2", "TGN fixture", "ODC-By 1.0")
    )
    val _ = Files.writeString(d.resolve("gazetteer/extracts.json"), GazetteerManifest(pins).asJson.spaces2, UTF_8)
    val extracts = tmp.resolve("gaz-extracts")
    Files.createDirectories(extracts)
    copyFixture("geonames-fixture.ndjson.gz", extracts.resolve("geonames-fixture.ndjson.gz"))
    val lpf = tmp.resolve("gaz-lpf")
    Files.createDirectories(lpf)
    List("zzgaz_hittite.jsonld", "zzgaz_kultepe.tsv", "zzgaz_kultepe.meta.json").foreach(f =>
      copyFixture(s"lpf/$f", lpf.resolve(f))
    )
    val c = cfgFor("gaz")
    c.copy(
      init = c.init.copy(dataDir = d.toString),
      places = c.places.copy(sources = c.places.sources.copy(dir = extracts.toString, lpfDir = lpf.toString))
    )

  private def gazetteer(url: String, ds: DataSource): String =
    val log = StringBuilder()
    def step(name: String, answer: String): String =
      log ++= s"== $name\n$answer\n"
      answer
    def job(name: String, answer: String): Unit =
      val started = step(name, answer)
      if started.startsWith("200 ") then
        val _ = step(s"$name, the job", awaitJob(url, field(started, "id")))
    val _ = step("sources before", get(s"$url/api/places/sources"))
    job("init", post(s"$url/api/init"))
    val _ = step("places after the seed", get(s"$url/api/places?q=Zztell"))
    val _ = step("the seed's tree", get(s"$url/api/places/zzs_shore/tree"))
    job("load geonames, a dry run", post(s"$url/api/places/load?source=geonames"))
    job("load geonames", post(s"$url/api/places/load?source=geonames&apply=true"))
    job("load tgn, its file missing", post(s"$url/api/places/load?source=tgn&apply=true"))
    job("load lpf", post(s"$url/api/places/load?source=lpf&apply=true"))
    val _ = step("load, unknown source", post(s"$url/api/places/load?source=nowhere"))
    val _ = step("sources after", get(s"$url/api/places/sources"))
    val _ = step("places after the loads", get(s"$url/api/places?q=zzgaz"))
    val _ = step("a loaded place", get(s"$url/api/places?q=Hattusas"))
    val _ = step("a name by hand", post(s"$url/api/places/zzs_ford/names", """{"name":"Zzcrossing","lang":"en"}"""))
    val _ = step(
      "an override",
      post(
        s"$url/api/places/zzs_ford/override",
        """{"kind":"polity","periodStart":-1500,"periodEnd":-1000,"reason":"dug"}"""
      )
    )
    val _ = step(
      "a place by hand",
      post(s"$url/api/places", """{"label":"Zzcairn","kind":"site","lon":34.7,"lat":40.1,"source":"survey"}""")
    )
    val _ = step("rebuild", post(s"$url/api/places/rebuild"))
    val tools = List(
      "place_names" -> Json.obj(),
      "place_names" -> Json.obj("placeId" -> "zzs_ford".asJson),
      "place_names" -> Json.obj("placeId" -> "zzs_tell".asJson, "name" -> "Zzmound of ash".asJson),
      "place_names" -> Json
        .obj("placeId" -> "zzs_tell".asJson, "name" -> "Zzmound of ash".asJson, "remove" -> true.asJson),
      "place_names" -> Json.obj("placeId" -> "zzs_tell".asJson, "name" -> "Zztell".asJson, "remove" -> true.asJson),
      "place_override" -> Json.obj(),
      "place_override" -> Json.obj("placeId" -> "zzs_ford".asJson),
      "places" -> Json.obj("query" -> "zzs".asJson),
      "places" -> Json.obj("id" -> "zzs_vale".asJson, "asOf" -> (-1500).asJson)
    )
    tools.foreach((n, a) => step(s"tool $n ${a.noSpaces}", tool(url, n, a)))
    // the geocoder, as the enrichment and the checks ask it
    val asks = List(
      ("Zztell", Geocode.KindHint.Site, Some(Period(-1500, -1400))),
      ("Zzhöyük", Geocode.KindHint.Unknown, None),
      ("Zzcrossing", Geocode.KindHint.Site, None),
      ("Zzvale", Geocode.KindHint.Region, Some(Period(-1300, -1200))),
      ("Eastern Zzshore", Geocode.KindHint.Region, None),
      ("Zztell, Zzshore", Geocode.KindHint.Site, None),
      ("Zzgaz Hattusas", Geocode.KindHint.Unknown, None),
      ("Zznowhere", Geocode.KindHint.Unknown, None)
    )
    asks.foreach { (name, hint, at) =>
      val r = Geocoding(com.histmersion.util.database.Transactor(ds)).resolve(name, hint, at, 4)
      val _ = step(
        s"geocode $name ${hint.wire} ${at.map(p => s"${p.start}..${p.end}").getOrElse("-")}",
        r.fold("none")(r =>
          s"${r.describe} at ${r.lon} ${r.lat} approximate=${r.approximate.map(_.describe).mkString("; ")}"
        )
      )
    }
    val _ = step("lint", get(s"$url/api/lint"))
    log.toString

  test(
    "the gazetteer: the bundled seed, its sources, a pinned extract and a drop folder loaded, names, overrides, the geocoder, as recorded"
  ):
    val gaz = scratch("gaz")
    val text = withServerOn(gazetteerCfg)(url => gazetteer(url, gaz))
    val n = names(List("gaz" -> gaz))
    compare("gazetteer.txt", answers(text, n) + rows(gaz, n))

  private def postBytes(url: String, body: Array[Byte]): String =
    val r = raw(
      HttpRequest
        .newBuilder(URI.create(url))
        .header("content-type", "application/octet-stream")
        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
    )
    s"${r.statusCode} ${String(r.body, UTF_8)}"
