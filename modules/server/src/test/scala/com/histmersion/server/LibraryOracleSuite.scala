package com.histmersion
package server
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.RawJson
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.json.RawJson.*

import hearth.kindlings.optics.syntax.*

import com.histmersion.config.*
import com.augustnagro.magnum.*
import ox.supervised

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.{URI, URLEncoder}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import javax.imageio.ImageIO
import javax.sql.DataSource
import com.histmersion.db.*
import com.histmersion.api.exchange.{MergePlan, MergeResolutions}
import com.histmersion.config.DbConfig
import com.histmersion.config.BackupsConfig
import com.histmersion.config.BundlesConfig
import com.histmersion.config.ConverterConfig
import com.histmersion.config.AcquireConfig
import com.histmersion.config.InitConfig
import com.histmersion.config.RuntimeConfig
import com.histmersion.config.BasemapConfig
import com.histmersion.config.ServerConfig
import com.histmersion.util.data.exchange.BundleFormat
import com.histmersion.util.database.TestPermits.given
import com.histmersion.util.database.TestCreators.given

/** What the library answers, on a crafted collection, recorded on the code before the library's sources, findings,
  * citations, labels and search moved into their own domain:
  *
  *   - sources: put (with findings, edges to a source not stored yet, to an article and to a work), refused puts, every
  *     read of one source (the record with and without its body, each Markdown section, a heading, a slice, passages,
  *     the note, the formatting report and its repair, the resolution of an id), patched field by field (metadata and
  *     its clearing, summary, body, notes, topics, regions, period, tags, findings — one removed, one added, the stable
  *     ids — and edges), the refusals (a stale token, a strict patch, an unknown key), through REST and the MCP tools;
  *     a note imported; the chunks rebuilt;
  *   - citations: neighbors, the graph and the contradictions over every kind of edge, an edge parked and promoted, the
  *     short forms resolved;
  *   - search: the filtered search under every criterion (text, tags, authors, places by name and by gazetteer id,
  *     topics, statuses, publication years, deep time, a box and one over the antimeridian, every order, pages), the
  *     legacy search, every facet field with prefixes, the map feed and its pages, the histogram and its trees in every
  *     grouping and unit and its refusals, the read-only query and its refusals, the schema; the MCP tools on the same;
  *   - referrers: a source renamed (an old citekey kept as an alias, a rename back) and one deleted (a dry run, the
  *     refusals, the delete), with every row research, media, automation and the catalogue hold of them before and
  *     after — article texts and links, coverage reasons, figures' links, attestations, events, goal links, the work
  *     lists;
  *   - the exchange (`exchange.txt`, recorded on the code before the library's carried kinds and merge rules moved): a
  *     collection's whole bundle and the part since a shared history, imported into an empty collection and into one
  *     that holds that history, and merged into one that went on too — findings and a body changed on both sides, edges
  *     added on both, a citekey renamed on one and edited under its old name on the other, works matched by DOI and by
  *     their fingerprint, a citekey taken by two works, a source deleted —, every round of the plan, the resolution
  *     file, the apply, the merges, the ledger and the library's rows;
  *   - the checks (`lint.txt`): a collection on which every library rule finds something, the place rules included, the
  *     report, the safe fixes, the place rules' repairs named, and the rows after.
  *
  * Uids are written as the record they name, timestamps as `TS`, durations as `N`, today's date as `DAY`.
  * `HISTMERSION_UPDATE_ORACLE=1` rewrites the files. Skipped without HISTMERSION_TEST_DB_URL.
  */
class LibraryOracleSuite extends DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  override val munitTimeout = scala.concurrent.duration.Duration(10, "min")

  private val oracleDir = Path.of("modules/server/src/test/resources/oracle/library")
  private val update = sys.env.get("HISTMERSION_UPDATE_ORACLE").contains("1")

  private val tmp = Files.createTempDirectory("histmersion-library-oracle")
  private lazy val base = AppConfig.load()
  private lazy val scratch = ScratchDatabases("histmersion_s10o", base.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))

  override def afterSuite(): Unit =
    if testDb.isDefined then scratch.dropAll()

  // ── the server ─────────────────────────────────────────────────────────────────────────────

  private def cfgFor(name: String): AppConfig =
    base
      .modify(_.db)
      .setTo(base.db.modify(_.url).setTo(scratch.urlOf(s"histmersion_s10o_${name}_test")))
      .modify(_.backups)
      .setTo(base.backups.modify(_.dir).setTo(BackupsConfig.Dir(tmp.resolve(s"$name-backups").toString)))
      .modify(_.bundles)
      .setTo(base.bundles.modify(_.dir).setTo(BundlesConfig.Dir(tmp.resolve(s"$name-bundles").toString)))
      .modify(_.converter)
      .setTo(base.converter.modify(_.spoolDir).setTo(ConverterConfig.SpoolDir(tmp.resolve(s"$name-spool").toString)))
      .modify(_.acquire)
      .setTo(base.acquire.modify(_.spoolDir).setTo(AcquireConfig.SpoolDir(tmp.resolve(s"$name-acquire").toString)))
      .modify(_.init)
      .setTo(base.init.modify(_.auto).setTo(InitConfig.Auto(false)))
      .modify(_.runtime)
      .setTo(base.runtime.modify(_.dataDir).setTo(RuntimeConfig.DataDir(tmp.resolve(s"$name-data").toString)))
      .modify(_.basemap)
      .setTo(base.basemap.modify(_.dir).setTo(BasemapConfig.Dir(tmp.resolve(s"$name-basemap").toString)))

  private def withServer[A](name: String)(body: String => A): A =
    supervised {
      val b = Composition
        .server(cfgFor(name), ServerConfig.Host("127.0.0.1"), ServerConfig.Port(0), OfflineConverter())
        .start()
      try body(s"http://127.0.0.1:${b.port}")
      finally b.stop()
    }

  private val http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()
  private def enc(s: String) = URLEncoder.encode(s, UTF_8)

  private def raw(req: HttpRequest.Builder): HttpResponse[Array[Byte]] =
    http.send(req.build(), HttpResponse.BodyHandlers.ofByteArray())

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

  private def field(json: String, name: String): String =
    s""""$name"\\s*:\\s*"([^"]*)"""".r.findFirstMatchIn(json).map(_.group(1)).getOrElse(fail(s"no $name in $json"))

  private def awaitJob(url: String, id: String, left: Int = 600): String =
    val j = get(s"$url/api/jobs/$id")
    if j.contains("\"done\"") || j.contains("\"failed\"") || left == 0 then j
    else { Thread.sleep(100); awaitJob(url, id, left - 1) }

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
    RawJson
      .parse(payload)
      .toOption
      .flatMap(_.cursor.downField("result").downField("content").downArray.downField("text").as[String].toOption)
      .getOrElse(s"${r.statusCode} $text")

  private def exec(ds: DataSource, statement: Frag): Unit =
    connect(ds)(statement.update.run(): Unit)

  // ── the crafted collection ─────────────────────────────────────────────────────────────────

  private def polygon(lon0: Double, lat0: Double, lon1: Double, lat1: Double): String =
    s"""{"type":"Polygon","coordinates":[[[$lon0,$lat0],[$lon1,$lat0],[$lon1,$lat1],[$lon0,$lat1],[$lon0,$lat0]]]}"""

  private val regions =
    s"""{"type":"FeatureCollection","features":[
       |{"@id":"zq_land","type":"Feature","properties":{"title":"Zqland"},
       | "when":{"timespans":[{"start":{"in":"-2000"},"end":{"in":"-1000"}}]},
       | "geometry":${polygon(30, 36, 40, 42)}},
       |{"@id":"zq_isles","type":"Feature","properties":{"title":"Zqisles"},
       | "geometry":${polygon(175, -20, 179.5, -15)}}
       |]}""".stripMargin

  private val sites =
    """{"type":"FeatureCollection","features":[
      |{"@id":"zq_tell","type":"Feature","properties":{"title":"Zqtell"},
      | "when":{"timespans":[{"start":{"in":"-1650"},"end":{"in":"-1180"}}]},
      | "geometry":{"type":"Point","coordinates":[34.6,40.0]}},
      |{"@id":"zq_cove","type":"Feature","properties":{"title":"Zqcove"},
      | "geometry":{"type":"Point","coordinates":[35.5,39.2]}}
      |]}""".stripMargin

  private def finding(
      claim: String,
      lon: Double,
      lat: Double,
      from: Long,
      to: Long,
      region: String,
      tags: List[String] = List("zq-claim"),
      confidence: String = "high"
  ): Json =
    Json.obj(
      "claim" -> claim.asJson,
      "region" -> region.asJson,
      "lon" -> lon.asJson,
      "lat" -> lat.asJson,
      "periodStart" -> from.asJson,
      "periodEnd" -> to.asJson,
      "periodName" -> "Zq period".asJson,
      "tags" -> tags.asJson,
      "confidence" -> confidence.asJson
    )

  private def edge(dst: String, kind: String): Json = Json.obj("dst" -> dst.asJson, "kind" -> kind.asJson)

  private def source(
      title: String,
      year: Int,
      authors: List[String],
      body: String,
      findings: List[Json],
      tags: List[String],
      topics: List[String],
      status: String = "current",
      edges: List[Json] = Nil,
      extra: List[(String, Json)] = Nil
  ): String =
    Json
      .fromFields(
        List(
          "title" -> title.asJson,
          "authors" -> authors.asJson,
          "year" -> year.asJson,
          "venue" -> "Journal of Zq Studies".asJson,
          "status" -> status.asJson,
          "topics" -> topics.asJson,
          "regions" -> List("Zqland").asJson,
          "tags" -> tags.asJson,
          "periodStart" -> (-1700).asJson,
          "periodEnd" -> (-1100).asJson,
          "summary" -> s"A study of $title.".asJson,
          "body" -> body.asJson,
          "notes" -> s"Notes on $title.".asJson,
          "findings" -> Json.fromValues(findings),
          "edges" -> Json.fromValues(edges)
        ) ++ extra
      )
      .noSpaces

  private val bodyAlpha =
    """# Zqtell
      |
      |## The walls
      |
      |The walls of Zqtell were raised in the Zq period; the Ḫattuša-style gate faced the “sea road”.
      |The sea peoples came to Zqtell — twice — and the walls of Zqtell fell.
      |
      |## The harbour
      |
      |The harbour of Zqcove sent tin and copper to Zqtell.
      |
      |## References
      |
      |Zqauthor 1950; Zqtell excavations.
      |""".stripMargin

  private val bodyBeta =
    """# Zqcove
      |
      |The harbour at Zqcove traded tin with the islands. Ships of Zqcove reached the Zqisles.
      |""".stripMargin

  private val bodyGamma =
    """# Kings of Zqland
      |
      |The kings of Zqland ruled from Zqtell; a later dynasty moved the court to Zqcove.
      |""".stripMargin

  private def image(seed: Int): String =
    val img = BufferedImage(8, 6, BufferedImage.TYPE_INT_RGB)
    for x <- 0 until 8; y <- 0 until 6 do img.setRGB(x, y, ((x * 31 + y * 17 + seed * 101) * 2654435761L).toInt)
    val out = ByteArrayOutputStream()
    val _ = ImageIO.write(img, "png", out)
    java.util.Base64.getEncoder.encodeToString(out.toByteArray)

  /** Writes, each recorded with its answer. */
  private def craft(url: String, ds: DataSource): String =
    val log = StringBuilder()
    def step(name: String, answer: String): String =
      log ++= s"== $name\n$answer\n"
      answer
    val _ = step("places import regions", post(s"$url/api/places/import?kind=region", regions, "text/plain"))
    val _ = step("places import sites", post(s"$url/api/places/import?kind=site", sites, "text/plain"))
    val h = ok(step("places build-hierarchy", post(s"$url/api/places/build-hierarchy")))
    val _ = step("places build-hierarchy job", awaitJob(url, field(h, "job")))
    val _ = step(
      "put alpha (an edge to a source not stored yet, to an article, to a work)",
      send(
        "PUT",
        s"$url/api/sources/zqalpha1990_tell",
        source(
          "The Walls of Zqtell",
          1990,
          List("Zqauthor, Ann", "Zqwriter, Bo"),
          bodyAlpha,
          List(
            finding("the walls of Zqtell were raised around 1500 BC", 34.6, 40.0, -1520, -1480, "Zqtell"),
            finding("Zqcove sent tin to Zqtell", 35.5, 39.2, -1400, -1300, "Zqcove", List("zq-trade")),
            finding("the sea peoples reached Zqtell", 34.61, 40.01, -1200, -1180, "Zqtell", confidence = "contested")
          ),
          List("zq-walls", "zq-oracle"),
          List("bronze age", "fortification"),
          edges = List(
            edge("zqbeta2001_cove", "references"),
            edge("zq_article", "references"),
            edge("zqgamma1975", "challenges")
          )
        )
      )
    )
    val _ = step(
      "put beta (promotes the parked edge)",
      send(
        "PUT",
        s"$url/api/sources/zqbeta2001_cove",
        source(
          "The Harbour at Zqcove",
          2001,
          List("Zqwriter, Bo"),
          bodyBeta,
          List(
            finding("Zqcove traded tin with the islands", 35.5, 39.2, -1450, -1250, "Zqcove", List("zq-trade")),
            finding("ships of Zqcove reached the Zqisles", 178.0, -17.0, -1300, -1200, "Zqisles")
          ),
          List("zq-trade", "zq-oracle"),
          List("bronze age", "trade"),
          status = "contested",
          edges = List(edge("zqalpha1990_tell", "refutes"))
        )
      )
    )
    val _ = step(
      "put gamma (a short form of alpha's challenge)",
      send(
        "PUT",
        s"$url/api/sources/zqgamma1975_kings",
        source(
          "Kings of Zqland",
          1975,
          List("Zqauthor, Ann"),
          bodyGamma,
          List(finding("the kings of Zqland ruled from Zqtell", 34.6, 40.0, -1600, -1400, "Zqland")),
          List("zq-kings", "zq-oracle"),
          List("kingship"),
          status = "superseded",
          edges = List(edge("zqalpha1990_tell", "superseded_by"))
        )
      )
    )
    val _ = step(
      "put delta (to be deleted)",
      send(
        "PUT",
        s"$url/api/sources/zqdelta2010_gone",
        source(
          "A Lost Report",
          2010,
          List("Zqother, Cy"),
          "# A lost report\n\nNothing of Zqtell survives here.\n",
          List(finding("nothing survives", 34.7, 40.1, -1300, -1200, "Zqtell")),
          List("zq-oracle"),
          List("archive"),
          edges = List(edge("zqalpha1990_tell", "cites"))
        )
      )
    )
    val _ = step(
      "put epsilon (cited by an article)",
      send(
        "PUT",
        s"$url/api/sources/zqeps1999_cited",
        source(
          "A Cited Survey",
          1999,
          List("Zqother, Cy"),
          "# Survey\n\nThe survey of Zqcove.\n",
          Nil,
          List("zq-oracle"),
          List("survey")
        )
      )
    )
    val _ = step("put refused: a bad citekey", send("PUT", s"$url/api/sources/Bad%20Key", """{"title":"x"}"""))
    val _ =
      step("put refused: an unknown key", send("PUT", s"$url/api/sources/zqx2000_x", """{"title":"x","nope":1}"""))
    val _ = step(
      "put refused: an edge from another source",
      send(
        "PUT",
        s"$url/api/sources/zqx2000_x",
        """{"title":"x","edges":[{"dst":"zqbeta2001_cove","kind":"references","src":"zqgamma1975_kings"}]}"""
      )
    )
    val _ = step(
      "put refused: strict, a formatting error",
      send("PUT", s"$url/api/sources/zqx2000_x?strict=true", """{"title":"x","notes":"<div> never closed"}""")
    )
    // the other domains' records that point at the sources
    val _ = step(
      "article citing alpha, its short form, epsilon",
      send(
        "PUT",
        s"$url/api/articles/zq_article",
        Json
          .obj(
            "title" -> "Zq walls".asJson,
            "kind" -> "concept".asJson,
            "body" -> ("The walls fell [[zqalpha1990_tell]]; see also [[zqalpha1990|the walls]] and " +
              "[[zqeps1999_cited#Survey]].\n\nThe harbour [[zqbeta2001_cove]].").asJson,
            "summary" -> "On [[zqalpha1990_tell]].".asJson,
            "filter" -> Json.obj("tags" -> List("zq-oracle").asJson),
            "reasons" -> Json.arr(Json.obj("citekey" -> "zqdelta2010_gone".asJson, "reason" -> "lost".asJson))
          )
          .noSpaces
      )
    )
    val _ = step(
      "figures of beta",
      post(
        s"$url/api/sources/zqbeta2001_cove/assets",
        Json
          .arr(Json.obj("name" -> "fig-1.png".asJson, "mime" -> "image/png".asJson, "base64" -> image(1).asJson))
          .noSpaces
      )
    )
    List(
      ("source", "zqalpha1990_tell"),
      ("finding", "zqalpha1990_tell#1"),
      ("source", "zqdelta2010_gone"),
      ("finding", "zqdelta2010_gone#0")
    ).foreach { (t, id) =>
      val _ = step(
        s"asset link to $t $id",
        post(
          s"$url/api/asset-links",
          s"""{"citekey":"zqbeta2001_cove","assetName":"fig-1.png","relation":"depicts","targetType":"$t","targetId":"$id"}"""
        )
      )
    }
    List(
      ("finding", "zqalpha1990_tell#0"),
      ("edge", "zqalpha1990_tell|zqbeta2001_cove|references"),
      ("finding", "zqdelta2010_gone#0")
    ).foreach { (t, id) =>
      val _ = step(
        s"attestation by beta of $t $id",
        post(
          s"$url/api/attestations",
          s"""{"citekey":"zqbeta2001_cove","targetType":"$t","targetId":"$id","quote":"Zqcove"}"""
        )
      )
    }
    val _ = step(
      "event sourced to epsilon, dated by alpha and delta",
      send(
        "PUT",
        s"$url/api/events/zq_siege",
        Json
          .obj(
            "label" -> "The siege of Zqtell".asJson,
            "kind" -> "siege".asJson,
            "timeSpan" -> Json.obj("notBefore" -> (-1200).asJson, "notAfter" -> (-1180).asJson),
            "placeId" -> "zq_tell".asJson,
            "citekey" -> "zqeps1999_cited".asJson,
            "datings" -> Json.arr(
              Json.obj(
                "eventId" -> "zq_siege".asJson,
                "citekey" -> "zqalpha1990_tell".asJson,
                "timeSpan" -> Json.obj("notBefore" -> (-1200).asJson, "notAfter" -> (-1180).asJson),
                "method" -> "historical".asJson
              ),
              Json.obj(
                "eventId" -> "zq_siege".asJson,
                "citekey" -> "zqdelta2010_gone".asJson,
                "timeSpan" -> Json.obj("notBefore" -> (-1210).asJson, "notAfter" -> (-1170).asJson),
                "method" -> "historical".asJson
              )
            )
          )
          .noSpaces
      )
    )
    val _ = step(
      "a place whose source is alpha",
      post(
        s"$url/api/places",
        """{"label":"Zqgate","kind":"site","lon":34.62,"lat":40.02,"source":"zqalpha1990_tell","id":"zq_gate"}"""
      )
    )
    val _ = step(
      "a goal",
      send("PUT", s"$url/api/goals/zq-goal", """{"title":"Understand Zqtell","status":"active"}""")
    )
    exec(
      ds,
      sql"""INSERT INTO goal_links (goal_slug, kind, ref, note, creator) VALUES
        |('zq-goal', 'source', 'zqalpha1990_tell', 'the walls', jsonb_build_object('kind', 'unknown')),
        |('zq-goal', 'source', 'zqdelta2010_gone', 'the lost report', jsonb_build_object('kind', 'unknown'))""".stripMargin
    )
    val _ =
      step("a task mentioning delta", post(s"$url/api/queue/tasks", """{"text":"re-read [[zqdelta2010_gone]]"}"""))
    val _ = step("a task mentioning alpha", post(s"$url/api/queue/tasks", """{"text":"check zqalpha1990_tell"}"""))
    log.result()

  /** Reads of one source over REST and MCP, and its patches field by field. */
  private def sources(url: String): String =
    val log = StringBuilder()
    def step(name: String, answer: String): String =
      log ++= s"== $name\n$answer\n"
      answer
    val ck = "zqalpha1990_tell"
    val gets = List(
      s"sources/$ck",
      s"sources/$ck?body=false",
      s"sources/zqnone2000_x",
      s"sources/$ck/view",
      s"sources/$ck/view?fullText=true",
      s"sources/$ck/view?section=summary",
      s"sources/$ck/view?section=findings",
      s"sources/$ck/view?section=notes",
      s"sources/$ck/view?section=toc",
      s"sources/$ck/view?section=body",
      s"sources/$ck/view?section=body&offset=20&chars=60",
      s"sources/$ck/view?section=body&heading=the%20harb",
      s"sources/$ck/view?section=all",
      s"sources/$ck/view?section=all&offset=10&chars=40",
      s"sources/$ck/view?section=nonsense",
      s"sources/$ck/view?offset=5&chars=30&fullText=true",
      s"sources/$ck/passages?q=walls",
      s"sources/$ck/passages?q=hattusa",
      s"sources/$ck/passages?q=%22sea%20road%22",
      s"sources/$ck/passages?q=sea%20peoples&context=12&limit=1",
      s"sources/$ck/passages?q=Zq%5Ba-z%5D%2B&regex=true&limit=3",
      s"sources/$ck/passages?q=%5B&regex=true",
      s"sources/$ck/passages?q=",
      s"sources/zqnone2000_x/passages?q=x",
      s"sources/$ck/export",
      s"sources/$ck/lint",
      s"sources/$ck/articles",
      s"sources/zqnone2000_x/articles",
      s"resolve/$ck",
      s"resolve/zq_article",
      s"resolve/zq_nothing",
      "citekeys",
      "source-aliases"
    )
    gets.foreach(g => step(s"GET /api/$g", get(s"$url/api/$g")))
    val tools = List(
      "get_source" -> Json.obj("citekey" -> ck.asJson),
      "get_source" -> Json.obj("citekey" -> ck.asJson, "section" -> "toc".asJson),
      "get_source" -> Json.obj("citekey" -> ck.asJson, "section" -> "body".asJson, "heading" -> "The walls".asJson),
      "get_source" -> Json.obj("citekey" -> ck.asJson, "fullText" -> true.asJson, "offset" -> 3.asJson),
      "get_source" -> Json.obj("citekey" -> "zqnone2000_x".asJson),
      "passages" -> Json.obj("citekey" -> ck.asJson, "query" -> "Zqcove".asJson),
      "passages" -> Json.obj("citekey" -> ck.asJson, "query" -> "(".asJson, "regex" -> true.asJson),
      "passages" -> Json.obj("citekey" -> "zqnone2000_x".asJson, "query" -> "x".asJson),
      "export_source" -> Json.obj("citekey" -> ck.asJson),
      "export_source" -> Json.obj("citekey" -> "zqnone2000_x".asJson),
      "lint" -> Json.obj("source" -> ck.asJson)
    )
    tools.foreach((n, a) => step(s"tool $n ${a.noSpaces}", tool(url, n, a)))
    // patches, field by field
    val g = "zqgamma1975_kings"
    val patches = List(
      "metadata" -> """{"metadata":{"title":"Kings of Zqland, revised","authors":["Zqauthor, Ann","Zqhelper, Di"],"year":1976,"venue":"Zq Review","doi":"10.9999/zq.1976","status":"contested","provenance":"zq-press","calibreId":"77"}}""",
      "metadata cleared" -> """{"metadata":{"venue":null,"calibreId":null}}""",
      "metadata refused: a null title" -> """{"metadata":{"title":null}}""",
      "summary" -> """{"summary":"Kings, again; see [[zqalpha1990_tell]]."}""",
      "body" -> """{"body":"# Kings of Zqland\n\n## Court\n\nThe court sat at Zqtell, then at Zqcove.\n"}""",
      "notes" -> """{"notes":"Read with [[zqbeta2001_cove]] and [[zq_article]]."}""",
      "topics" -> """{"topics":["kingship","dynasties"]}""",
      "regions" -> """{"regions":["Zqland","Zqisles"]}""",
      "period" -> """{"period":{"start":-1650,"end":-1350}}""",
      "tags add" -> """{"tags":{"add":["zq-court"]}}""",
      "tags remove" -> """{"tags":{"remove":["zq-kings"]}}""",
      "tags replace" -> """{"tags":{"replace":["zq-oracle","zq-court","zq-dynasty"]}}""",
      "findings add" -> """{"findings":{"add":[{"claim":"the court moved to Zqcove","region":"Zqcove","lon":35.5,"lat":39.2,"periodStart":-1400,"periodEnd":-1350,"tags":["zq-court"],"confidence":"medium"}]}}""",
      "findings remove #0" -> """{"findings":{"remove":["zqgamma1975_kings#0"]}}""",
      "findings add again (the next id)" -> """{"findings":{"add":[{"claim":"the kings of Zqland ruled from Zqtell","region":"Zqtell","lon":34.6,"lat":40.0,"periodStart":-1600,"periodEnd":-1400}]}}""",
      "findings replace" -> """{"findings":{"replace":[{"claim":"one claim only","region":"Zqland"},{"claim":"and another","lon":36.0,"lat":38.0}]}}""",
      "edges add" -> """{"edges":{"add":[{"dst":"zqbeta2001_cove","kind":"updates"},{"dst":"zqnot2020_yet","kind":"references"}]}}""",
      "edges remove" -> """{"edges":{"remove":[{"dst":"zqalpha1990_tell","kind":"superseded_by"}]}}""",
      "refused: an unknown key" -> """{"status":"current"}""",
      "refused: a stale token" -> """{"summary":"x","ifUpdatedAt":"2000-01-01T00:00:00.000000Z"}""",
      "refused: an edge from another source" -> """{"edges":{"add":[{"dst":"zqbeta2001_cove","kind":"references","src":"zqalpha1990_tell"}]}}""",
      "refused: a self edge" -> """{"edges":{"add":[{"dst":"zqgamma1975_kings","kind":"references"}]}}"""
    )
    patches.foreach((n, p) => step(s"PATCH $g: $n", send("PATCH", s"$url/api/sources/$g", p)))
    val _ = step(
      "PATCH strict, a formatting error",
      send("PATCH", s"$url/api/sources/$g?strict=true", """{"notes":"<div> never closed"}""")
    )
    val _ = step("PATCH an unknown source", send("PATCH", s"$url/api/sources/zqnone2000_x", """{"summary":"x"}"""))
    val _ = step(
      "notes with a formatting problem (no strict)",
      send("PATCH", s"$url/api/sources/$g", """{"notes":"\\[\\[zqalpha1990_tell\\]\\] and a bare zqbeta2001_cove"}""")
    )
    val _ = step("GET lint", get(s"$url/api/sources/$g/lint"))
    val _ = step("POST lint/fix", post(s"$url/api/sources/$g/lint/fix"))
    val _ = step("GET after", get(s"$url/api/sources/$g?body=false"))
    val _ = step(
      "tool patch_source",
      tool(
        url,
        "patch_source",
        Json.obj("citekey" -> g.asJson, "patch" -> Json.obj("tags" -> Json.obj("add" -> List("zq-mcp").asJson)))
      )
    )
    val _ = step(
      "tool patch_source refused",
      tool(url, "patch_source", Json.obj("citekey" -> g.asJson, "patch" -> Json.obj("nope" -> 1.asJson)))
    )
    val _ = step(
      "tool put_source",
      tool(
        url,
        "put_source",
        Json.obj(
          "citekey" -> "zqzeta2015_mcp".asJson,
          "source" -> Json.obj(
            "title" -> "Put over MCP".asJson,
            "tags" -> List("zq-oracle").asJson,
            "findings" -> Json.arr(
              Json.obj("claim" -> "an MCP claim".asJson, "lon" -> 34.0.asJson, "lat" -> 39.0.asJson)
            )
          )
        )
      )
    )
    val note =
      """---
        |citekey: zqeta2020_note
        |title: An Imported Note
        |authors: [Zqauthor, Ann]
        |year: 2020
        |tags: [zq-oracle, zq-note]
        |findings:
        |  - claim: a claim from a note
        |    lon: 34.5
        |    lat: 39.9
        |    period_start: -1300
        |    period_end: -1250
        |---
        |
        |A note about [[zqalpha1990_tell]].
        |""".stripMargin
    val _ = step(
      "import a note",
      post(s"$url/api/sources/import", Json.obj("text" -> note.asJson, "body" -> "# Body\n\ntext".asJson).noSpaces)
    )
    val _ = step("import a note, refused", post(s"$url/api/sources/import", """{"text":"no frontmatter"}"""))
    val _ = step("GET the imported note", get(s"$url/api/sources/zqeta2020_note"))
    val _ = step("reindex", post(s"$url/api/reindex"))
    val _ = step("reindex after", post(s"$url/api/reindex?after=zqbeta2001_cove&limit=1"))
    log.result()

  /** The citation graph over every kind of edge. */
  private def citations(url: String): String =
    val log = StringBuilder()
    def step(name: String, answer: String): String =
      log ++= s"== $name\n$answer\n"
      answer
    val gets = List(
      "neighbors/zqalpha1990_tell",
      "neighbors/zqalpha1990_tell?kind=refutes",
      "neighbors/zqalpha1990_tell?kind=references&kind=challenges",
      "neighbors/zqgamma1975_kings",
      "neighbors/zqnone2000_x",
      "graph/zqalpha1990_tell",
      "graph/zqalpha1990_tell?depth=0",
      "graph/zqalpha1990_tell?depth=1",
      "graph/zqgamma1975_kings?depth=9",
      "contradictions",
      "contradictions?citekey=zqalpha1990_tell",
      "contradictions?since=1995",
      "contradictions?citekey=zqnone2000_x"
    )
    gets.foreach(g => step(s"GET /api/$g", get(s"$url/api/$g")))
    val _ = step("edges resolve", post(s"$url/api/edges/resolve"))
    val _ = step("neighbors after resolve", get(s"$url/api/neighbors/zqalpha1990_tell"))
    val tools = List(
      "neighbors" -> Json.obj("citekey" -> "zqalpha1990_tell".asJson),
      "neighbors" -> Json.obj("citekey" -> "zqalpha1990_tell".asJson, "kinds" -> List("refutes").asJson),
      "neighbors" -> Json.obj("citekey" -> "zqnone2000_x".asJson),
      "graph" -> Json.obj("citekey" -> "zqbeta2001_cove".asJson, "depth" -> 2.asJson),
      "contradictions" -> Json.obj(),
      "contradictions" -> Json.obj("citekey" -> "zqbeta2001_cove".asJson),
      "contradictions" -> Json.obj("since" -> 2000.asJson)
    )
    tools.foreach((n, a) => step(s"tool $n ${a.noSpaces}", tool(url, n, a)))
    log.result()

  /** Every criterion of the filter, the facets, the map feed, the histogram, the query. */
  private def search(url: String): String =
    val log = StringBuilder()
    def step(name: String, answer: String): String =
      log ++= s"== $name\n$answer\n"
      answer
    val filters = List(
      "",
      "q=walls",
      "q=" + enc("\"sea peoples\""),
      "q=" + enc("tin -islands"),
      "q=" + enc("tin or kings"),
      "q=the",
      "tag=zq-trade",
      "tag=zq-trade&tag=zq-oracle",
      "tag=zq-nothing",
      "author=" + enc("Zqwriter, Bo"),
      "author=" + enc("zqauthor"),
      "place=Zqcove",
      "place=zq_land",
      "place=zq_tell",
      "place=zq_isles",
      "place=zq_land&place=zq_isles",
      "topic=bronze%20age",
      "status=contested",
      "status=contested&status=superseded",
      "yearFrom=1990",
      "yearFrom=1980&yearTo=2005",
      "from=-1450&to=-1350",
      "from=-1190",
      "to=-1500",
      "minLon=34&minLat=39&maxLon=35&maxLat=41",
      "minLon=170&minLat=-25&maxLon=-170&maxLat=-10",
      "title=" + enc("zqtell"),
      "sort=cited",
      "sort=period",
      "sort=year",
      "sort=oldest",
      "sort=title",
      "q=zqtell&sort=relevance",
      "limit=2",
      "limit=2&offset=2",
      "limit=1&offset=9",
      "limit=100000",
      "q=tin&tag=zq-trade&from=-1500&to=-1200&place=zq_land&status=contested",
      "q=walls&place=zq_tell&from=-1600&to=-1100&minLon=30&minLat=36&maxLon=40&maxLat=42"
    )
    filters.foreach(f => step(s"GET /api/sources?$f", get(s"$url/api/sources?$f")))
    List("search?q=tin", "search?q=walls&limit=1", "search").foreach(g => step(s"GET /api/$g", get(s"$url/api/$g")))
    val facetQueries = List(
      "field=tags",
      "field=tags&prefix=zq-t",
      "field=tags&prefix=ZQ-",
      "field=authors",
      "field=authors&prefix=zqa",
      "field=places",
      "field=places&prefix=zqc",
      "field=topics",
      "field=status",
      "field=provenance",
      "field=tags&q=tin",
      "field=tags&status=contested",
      "field=places&place=zq_land",
      "field=tags&limit=1",
      "field=nonsense"
    )
    facetQueries.foreach(q => step(s"GET /api/facets?$q", get(s"$url/api/facets?$q")))
    val feeds = List(
      "findings",
      "findings?from=-1450&to=-1350",
      "findings?place=zq_land",
      "findings?region=Zqcove",
      "findings?tag=zq-trade",
      "findings?minLon=34&minLat=39&maxLon=35&maxLat=41",
      "findings?minLon=170&minLat=-25&maxLon=-170&maxLat=-10",
      "findings?q=tin&limit=1",
      "findings/page?limit=2",
      "findings/page?limit=2&offset=2",
      "findings/page?place=zq_tell"
    )
    feeds.foreach(f => step(s"GET /api/$f", get(s"$url/api/$f")))
    val aggregates = List(
      "aggregate",
      "aggregate?from=-1700&to=-1100",
      "aggregate?from=-1700&to=-1100&size=100",
      "aggregate?from=-1700&to=-1100&unit=findings",
      "aggregate?from=-1700&to=-1100&unit=starts",
      "aggregate?from=-1700&to=-1100&depth=2",
      "aggregate?from=-1700&to=-1100&samples=2",
      "aggregate?by=region&from=-1700&to=-1100",
      "aggregate?by=region&from=-1700&to=-1100&asOf=-1300&depth=2&samples=1",
      "aggregate?by=region&place=zq_land&from=-1700&to=-1100",
      "aggregate?by=time,region&from=-1700&to=-1100&depth=2",
      "aggregate?by=region,time&from=-1700&to=-1100&unit=findings",
      "aggregate?by=region&tag=zq-trade",
      "aggregate?by=nonsense",
      "aggregate?unit=nonsense",
      "aggregate?from=-1000000000000&to=1000000000000&size=1"
    )
    aggregates.foreach(a => step(s"GET /api/$a", get(s"$url/api/$a")))
    val tools = List(
      "search" -> Json.obj("query" -> "tin".asJson),
      "search" -> Json.obj("tags" -> List("zq-oracle").asJson, "verbose" -> false.asJson),
      "search" -> Json.obj("places" -> List("zq_land").asJson, "from" -> (-1500).asJson, "to" -> (-1200).asJson),
      "search" -> Json.obj("groupBy" -> "time".asJson, "from" -> (-1700).asJson, "to" -> (-1100).asJson),
      "search" -> Json.obj("groupBy" -> "region".asJson, "depth" -> 2.asJson, "samples" -> 1.asJson),
      "search" -> Json.obj("groupBy" -> "time,region".asJson, "from" -> (-1700).asJson, "to" -> (-1100).asJson),
      "search" -> Json.obj("groupBy" -> "nonsense".asJson),
      "facets" -> Json.obj("field" -> "tags".asJson),
      "facets" -> Json.obj("field" -> "places".asJson, "prefix" -> "zq".asJson),
      "facets" -> Json.obj("field" -> "nonsense".asJson),
      "geo_when" -> Json.obj("places" -> List("zq_land").asJson),
      "geo_when" -> Json.obj("region" -> "Zqcove".asJson, "periodStart" -> (-1500).asJson),
      "geo_when" -> Json.obj("limit" -> 2.asJson, "offset" -> 1.asJson),
      "histogram" -> Json.obj("from" -> (-1700).asJson, "to" -> (-1100).asJson),
      "histogram" -> Json.obj("from" -> (-1700).asJson, "to" -> (-1100).asJson, "unit" -> "findings".asJson),
      "histogram" -> Json.obj("unit" -> "nonsense".asJson)
    )
    tools.foreach((n, a) => step(s"tool $n ${a.noSpaces}", tool(url, n, a)))
    val statements = List(
      "SELECT citekey, year, status FROM sources ORDER BY citekey",
      "SELECT count(*) AS n, status FROM sources GROUP BY status ORDER BY status",
      "SELECT id, claim FROM findings ORDER BY id LIMIT 3",
      "SELECT 1; SELECT 2",
      "DELETE FROM sources",
      "SET TRANSACTION READ WRITE",
      "WITH x AS (DELETE FROM sources RETURNING 1) SELECT * FROM x",
      "SELECT pg_read_file('/etc/hosts')",
      "SELECT nonsense FROM nowhere",
      ""
    )
    statements.foreach(s => step(s"POST /api/query $s", post(s"$url/api/query", Json.obj("sql" -> s.asJson).noSpaces)))
    val _ = step(
      "tool query",
      tool(url, "query", Json.obj("sql" -> "SELECT citekey FROM sources ORDER BY citekey LIMIT 2".asJson))
    )
    val _ = step("tool query refused", tool(url, "query", Json.obj("sql" -> "DROP TABLE sources".asJson)))
    val _ = step("GET /api/schema/tables", get(s"$url/api/schema/tables"))
    log.result()

  /** A rename, a rename back, the refusals; a delete's dry run, its refusals, the delete. */
  private def referrers(url: String, ds: DataSource, names: () => Map[String, String]): String =
    val log = StringBuilder()
    def step(name: String, answer: String): String =
      log ++= s"== $name\n$answer\n"
      answer
    log ++= s"== rows before\n${rows(ds, names())}"
    val renames = List(
      ("zqalpha1990_tell", "zqalpha1991_walls", "rename alpha"),
      ("zqalpha1990_tell", "zqx2000_x", "refused: an old citekey"),
      ("zqalpha1991_walls", "zqbeta2001_cove", "refused: taken"),
      ("zqalpha1991_walls", "ZQ bad", "refused: invalid"),
      ("zqalpha1991_walls", "zqwalls", "refused: not link-shaped"),
      ("zqalpha1991_walls", "zqalpha1991_walls", "refused: the same"),
      ("zqnone2000_x", "zqnone2001_x", "refused: unknown")
    )
    renames.foreach((from, to, n) =>
      step(s"$n ($from → $to)", post(s"$url/api/sources/$from/rename", s"""{"newCitekey":"$to"}"""))
    )
    log ++= s"== rows after the rename\n${rows(ds, names())}"
    val reads = List(
      "sources/zqalpha1990_tell?body=false",
      "neighbors/zqalpha1990_tell",
      "graph/zqalpha1990_tell?depth=1",
      "source-aliases",
      "resolve/zqalpha1990_tell"
    )
    reads.foreach(g => step(s"GET /api/$g (an old citekey)", get(s"$url/api/$g")))
    val _ = step(
      "patch by the old citekey",
      send("PATCH", s"$url/api/sources/zqalpha1990_tell", """{"tags":{"add":["zq-renamed"]}}""")
    )
    val _ = step(
      "tool rename_source",
      tool(
        url,
        "rename_source",
        Json.obj("citekey" -> "zqbeta2001_cove".asJson, "newCitekey" -> "zqbeta2001_harbour".asJson)
      )
    )
    val _ = step(
      "rename back",
      post(s"$url/api/sources/zqalpha1991_walls/rename", """{"newCitekey":"zqalpha1990_tell"}""")
    )
    val _ = step("source aliases after", get(s"$url/api/source-aliases"))
    log ++= s"== rows after the rename back\n${rows(ds, names())}"
    val deletes = List(
      ("zqdelta2010_gone", false, "dry run"),
      ("zqeps1999_cited", false, "dry run, refused"),
      ("zqeps1999_cited", true, "refused"),
      ("zqnone2000_x", false, "unknown"),
      ("zqalpha1991_walls", false, "an old citekey"),
      ("zqdelta2010_gone", true, "delete")
    )
    deletes.foreach((ck, apply, n) =>
      step(s"delete $ck: $n", post(s"$url/api/sources/$ck/delete", s"""{"apply":$apply}"""))
    )
    val _ = step("GET the deleted", get(s"$url/api/sources/zqdelta2010_gone"))
    log ++= s"== rows after the delete\n${rows(ds, names())}"
    log.result()

  // ── normalisation ──────────────────────────────────────────────────────────────────────────

  private val UuidRe =
    "\\b[A-Z]-[0-9a-f]{8}\\b|[0-9a-f]{64}|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}".r
  private val TimeRe =
    "\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?(?:Z|[+-]\\d{2}(?::?\\d{2})?)?".r
  private val DayRe = "\\b\\d{4}-\\d{2}-\\d{2}\\b".r
  private val DurationRe = "(\"(?:durationMs|elapsedMs|tookMillis|ageSeconds)\" ?: ?)\\d+".r
  private val MsRe = "· \\d+ ms".r

  /** uid → the record it names. */
  private def names(ds: DataSource): Map[String, String] =
    val queries = List(
      "SELECT uid::text, 'source:' || citekey FROM sources",
      "SELECT origin::text, 'origin-of:' || citekey FROM sources WHERE origin IS NOT NULL",
      "SELECT uid::text, 'finding:' || id FROM findings",
      "SELECT uid::text, 'event:' || id FROM events",
      "SELECT uid::text, 'place:' || id FROM places",
      "SELECT uid::text, 'article:' || slug FROM articles",
      "SELECT uid::text, 'goal:' || slug FROM goals",
      "SELECT uid::text, 'asset:' || coalesce(citekey, '') || '/' || name FROM assets",
      "SELECT uid::text, 'attestation:' || citekey || ':' || target_id FROM attestations",
      "SELECT uid::text, 'tombstone:' || kind FROM tombstones",
      "SELECT id::text, 'instance' FROM instance"
    ).map(q => sql"${SqlLiteral(q)}")
    def run(q: Frag): List[(String, String)] =
      scala.util.Try(connect(ds)(q.query[(String, String)].run().toList)).getOrElse(Nil)
    queries.flatMap(run).distinctBy(_._1).toMap

  private def normalise(text: String, names: Map[String, String]): String =
    val t1 = text.replace(tmp.toString, "TMP")
    val t2 = UuidRe.replaceAllIn(
      t1,
      m =>
        scala.util.matching.Regex
          .quoteReplacement(names.getOrElse(m.matched, if m.matched.length != 36 then m.matched else "UUID"))
    )
    val t3 = DurationRe.replaceAllIn(TimeRe.replaceAllIn(t2, "TS"), "$1N")
    MsRe.replaceAllIn(DayRe.replaceAllIn(t3, "DAY"), "· N ms")

  private def json(text: String, names: Map[String, String]): String =
    RawJson.parse(text) match
      case Right(j) => normalise(j.sortedKeys.spaces2, names)
      case Left(_)  => normalise(text, names)

  /** A recorded answer: its status, then its body as sorted JSON when it is JSON. */
  private def answers(text: String, names: Map[String, String]): String =
    text.linesIterator
      .map { l =>
        val m = "^(\\d{3}) (.*)$".r.findFirstMatchIn(l)
        m.fold(normalise(l, names))(m => s"${m.group(1)} ${json(m.group(2), names)}")
      }
      .mkString("", "\n", "\n")

  /** The tables whose rows name a source, every row, normalised. */
  private val Tables = List(
    "sources" -> "citekey, title, year, status, tags, notes, summary, calibre_id",
    "source_aliases" -> "*",
    "alias_tombstones" -> "alias",
    "findings" -> "id, citekey, claim, tags",
    "finding_places" -> "*",
    "edges" -> "*",
    "unresolved_edges" -> "*",
    "source_chunks" -> "citekey, ord, heading",
    "articles" -> "slug, body, summary, coverage_reasons",
    "article_links" -> "*",
    "article_versions" -> "slug, body",
    "asset_links" -> "citekey, asset_name, relation, target_type, target_id",
    "assets" -> "citekey, name",
    "attestations" -> "citekey, target_type, target_id, quote",
    "events" -> "id, citekey",
    "event_datings" -> "event_id, citekey",
    "places" -> "id, source",
    "goal_links" -> "goal_slug, kind, ref, note",
    "queue_items" -> "list, text, topic, status",
    "jobs" -> "kind, citekey, status",
    "tombstones" -> "kind"
  )

  private def rows(ds: DataSource, names: Map[String, String]): String =
    Tables
      .map { (t, cols) =>
        val rs = connect(ds)(
          sql"""SELECT to_jsonb(x)::text FROM (SELECT ${SqlLiteral(cols)} FROM "${SqlLiteral(t)}") x"""
            .query[String]
            .run()
            .toList
        )
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

  // ── the exchange and the checks ────────────────────────────────────────────────────────────

  /** A source as the exchange test writes it: every field the bundle carries, `fields` over the defaults. */
  private def xput(title: String, year: Option[Int], fields: (String, Json)*): String =
    val defaults = List(
      "title" -> title.asJson,
      "authors" -> List("Zxauthor, Ann").asJson,
      "venue" -> "Zx Review".asJson,
      "status" -> "current".asJson,
      "topics" -> List("bronze age").asJson,
      "regions" -> List("Zxland").asJson,
      "tags" -> List("zx-oracle").asJson,
      "periodStart" -> (-1500).asJson,
      "periodEnd" -> (-1200).asJson,
      "summary" -> s"On $title.".asJson,
      "notes" -> s"Notes on $title.".asJson
    ) ++ year.map("year" -> _.asJson)
    val named = fields.map(_._1).toSet
    Json.fromFields(defaults.filterNot(f => named(f._1)) ++ fields).noSpaces

  private def xfinding(
      claim: String,
      at: Option[(Double, Double)],
      period: Option[(Long, Long)],
      region: String = "Zxland"
  ): Json =
    Json.fromFields(
      List("claim" -> claim.asJson, "tags" -> List("zx-claim").asJson, "confidence" -> "high".asJson) ++
        Option.when(region.nonEmpty)("region" -> region.asJson) ++
        at.toList.flatMap((lon, lat) => List("lon" -> lon.asJson, "lat" -> lat.asJson)) ++
        period.toList.flatMap((a, b) =>
          List("periodStart" -> a.asJson, "periodEnd" -> b.asJson, "periodName" -> "Zx".asJson)
        )
    )

  private def findingsOf(fs: Json*): (String, Json) = "findings" -> Json.fromValues(fs)
  private def edgesOf(es: (String, String)*): (String, Json) = "edges" -> Json.fromValues(es.map(edge(_, _)))

  private val ZxBody =
    "# Zxtown\n\n## The walls\n\nThe walls of Zxtown were raised in the Zx period.\n\n## The gate\n\nThe gate faced the sea.\n"

  /** The collection the exchange test starts from: sources with findings and edges (one to a work not stored), a body,
    * a renamed citekey.
    */
  private def craftExchange(url: String): String =
    val log = StringBuilder()
    def step(name: String, answer: String): Unit = log ++= s"== $name\n$answer\n"
    step("label", send("PUT", s"$url/api/instance", """{"label":"zxlib"}"""))
    step(
      "put one",
      send(
        "PUT",
        s"$url/api/sources/zxone2001_storm",
        xput(
          "The Storm of Zxtown",
          Some(2001),
          "doi" -> "10.9999/zx.one".asJson,
          "body" -> ZxBody.asJson,
          findingsOf(
            xfinding("the walls of Zxtown were raised around 1500 BC", Some((34.6, 40.0)), Some((-1520L, -1480L))),
            xfinding("the gate of Zxtown faced the sea", Some((34.61, 40.01)), Some((-1400L, -1300L)))
          ),
          edgesOf("zxtwo2005_kingdom" -> "references", "zxlost1900_gone" -> "references")
        )
      )
    )
    step(
      "put two",
      send(
        "PUT",
        s"$url/api/sources/zxtwo2005_kingdom",
        xput(
          "The Kingdom of Zxland",
          Some(2005),
          "status" -> "contested".asJson,
          findingsOf(xfinding("a kingdom ruled Zxland", Some((35.0, 39.5)), Some((-1450L, -1250L))))
        )
      )
    )
    step("put three", send("PUT", s"$url/api/sources/zxthree1903_old", xput("An Old Book on Zxland", Some(1903))))
    step(
      "rename three",
      post(s"$url/api/sources/zxthree1903_old/rename", """{"newCitekey":"zxthree1903_prolegomena"}""")
    )
    log.result()

  /** After the shared history both collections go on. */
  private def editExchange(lib: String, home: String): String =
    val log = StringBuilder()
    def step(name: String, answer: String): Unit = log ++= s"== $name\n$answer\n"
    // findings changed on both sides, the body edited on both (a conflict), edges added on both
    step(
      "lib: one's findings, body and edges",
      send(
        "PUT",
        s"$lib/api/sources/zxone2001_storm",
        xput(
          "The Storm of Zxtown",
          Some(2001),
          "doi" -> "10.9999/zx.one".asJson,
          "body" -> ZxBody.replace("sea.", "sea, as lib reads it.").asJson,
          findingsOf(
            xfinding("the walls of Zxtown were raised around 1490 BC", Some((34.6, 40.0)), Some((-1500L, -1480L))),
            xfinding("the gate of Zxtown faced the sea", Some((34.61, 40.01)), Some((-1400L, -1300L))),
            xfinding("a third claim, lib's", Some((34.62, 40.02)), Some((-1300L, -1200L)))
          ),
          edgesOf(
            "zxtwo2005_kingdom" -> "references",
            "zxlost1900_gone" -> "references",
            "zxthree1903_prolegomena" -> "challenges"
          )
        )
      )
    )
    step(
      "home: one's findings, body and edges",
      send(
        "PUT",
        s"$home/api/sources/zxone2001_storm",
        xput(
          "The Storm of Zxtown",
          Some(2001),
          "doi" -> "10.9999/zx.one".asJson,
          "body" -> ZxBody.replace("sea.", "sea, as home reads it.").asJson,
          "summary" -> "On The Storm of Zxtown, at home.".asJson,
          findingsOf(
            xfinding("the walls of Zxtown were raised around 1500 BC", Some((34.6, 40.0)), Some((-1520L, -1480L))),
            xfinding("the gate of Zxtown faced the harbour", Some((34.61, 40.01)), Some((-1400L, -1300L))),
            xfinding("a claim of home's", Some((34.63, 40.03)), Some((-1350L, -1250L)))
          ),
          edgesOf(
            "zxtwo2005_kingdom" -> "references",
            "zxlost1900_gone" -> "references",
            "zxthree1903_prolegomena" -> "cites"
          )
        )
      )
    )
    // a renamed citekey at lib, the old one at home followed by an edit there
    step(
      "lib: rename two",
      post(s"$lib/api/sources/zxtwo2005_kingdom/rename", """{"newCitekey":"zxtwo2005_hittites"}""")
    )
    step("home: patch two", send("PATCH", s"$home/api/sources/zxtwo2005_kingdom", """{"tags":{"add":["zx-home"]}}"""))
    // one work under two citekeys with one DOI; one under two citekeys with no DOI and the same title, authors, year
    step(
      "lib: a work by DOI",
      send(
        "PUT",
        s"$lib/api/sources/zxdoi2010_lib",
        xput("A Work Both Hold", Some(2010), "doi" -> "10.9999/ZX.Both".asJson)
      )
    )
    step(
      "home: the same work by DOI",
      send(
        "PUT",
        s"$home/api/sources/zxdoi2010_home",
        xput(
          "A Work Both Hold",
          Some(2010),
          "doi" -> "https://doi.org/10.9999/zx.both".asJson,
          "summary" -> "home's".asJson
        )
      )
    )
    step(
      "lib: a work by its fingerprint",
      send(
        "PUT",
        s"$lib/api/sources/zxfp1950_lib",
        xput(
          "On the Zxtown Hoard",
          Some(1950),
          "authors" -> List("Zxwriter, Bo").asJson,
          "body" -> "# Hoard\n\nCoins.\n".asJson
        )
      )
    )
    step(
      "home: the same work by its fingerprint",
      send(
        "PUT",
        s"$home/api/sources/zxfp1950_home",
        xput(
          "On the Zxtown hoard.",
          Some(1950),
          "authors" -> List("Zxwriter, B.").asJson,
          "body" -> "# Hoard\n\nCoins and rings.\n".asJson
        )
      )
    )
    // a citekey taken on both sides by different works
    step("lib: a taken citekey", send("PUT", s"$lib/api/sources/zxtaken2020_x", xput("Lib's Work", Some(2020))))
    step(
      "home: the same citekey",
      send("PUT", s"$home/api/sources/zxtaken2020_x", xput("Home's Other Work", Some(2020)))
    )
    // a source deleted at lib
    step("lib: delete three", post(s"$lib/api/sources/zxthree1903_prolegomena/delete", """{"apply":true}"""))
    log.result()

  /** A collection where every library rule finds something: the gazetteer the place rules ask, then a source for each
    * rule.
    */
  private def craftChecks(url: String, ds: DataSource): String =
    val log = StringBuilder()
    def step(name: String, answer: String): Unit = log ++= s"== $name\n$answer\n"
    // four sites on one point make it a placeholder; a region of the same name lies far away
    val sites = (1 to 4)
      .map(i =>
        s"""{"@id":"zx_pt$i","type":"Feature","properties":{"title":"${if i == 1 then "Zxville" else s"Zxpoint $i"}"},
           | "geometry":{"type":"Point","coordinates":[30.0,30.0]}}""".stripMargin
      )
      .mkString(",")
    step(
      "places import sites",
      post(
        s"$url/api/places/import?kind=site",
        s"""{"type":"FeatureCollection","features":[$sites,
      |{"@id":"zx_fort","type":"Feature","properties":{"title":"Zxfort"},"geometry":{"type":"Point","coordinates":[33.0,36.0]}}]}""".stripMargin,
        "text/plain"
      )
    )
    step(
      "places import regions",
      post(
        s"$url/api/places/import?kind=region",
        s"""{"type":"FeatureCollection","features":[{"@id":"zx_ville","type":"Feature","properties":{"title":"Zxville"},
           | "geometry":${polygon(40, 40, 42, 42)}}]}""".stripMargin,
        "text/plain"
      )
    )
    def put(ck: String, body: String): Unit = step(s"put $ck", send("PUT", s"$url/api/sources/$ck", body))
    val one = findingsOf(xfinding("a claim", Some((34.6, 40.0)), Some((-1500L, -1400L))))
    put(
      "zxa2001_edges",
      xput("Edges", Some(2001), one, edgesOf("zxnobody1900_lost" -> "references", "zxb2002" -> "cites"))
    )
    put("zxb2002_nofindings", xput("No findings", Some(2002)))
    put("zxc2003_noperiod", xput("No period", Some(2003), one, "periodStart" -> Json.Null, "periodEnd" -> Json.Null))
    put("zxd2004_noregions", xput("No regions", Some(2004), one, "regions" -> Json.arr()))
    put("zxe2005_nosummary", xput("No summary", Some(2005), one, "summary" -> "  ".asJson))
    put("zxf2006_unassessed", xput("Unassessed", Some(2006), one, "status" -> "unassessed".asJson))
    put("zxg2007_doia", xput("Doi A", Some(2007), one, "doi" -> "10.9999/zx.dup".asJson))
    put("zxh2008_doib", xput("Doi B", Some(2008), one, "doi" -> "10.9999/ZX.DUP".asJson))
    put("zxi2009_stale", xput("Stale chunks", Some(2009), one, "body" -> ZxBody.asJson))
    put(
      "zxj2010_findings",
      xput(
        "Undated and unlocated",
        Some(2010),
        findingsOf(
          xfinding("an undated claim", Some((34.6, 40.0)), None),
          xfinding("an unlocated claim", None, Some((-1500L, -1400L)), region = "")
        )
      )
    )
    put(
      "zxk2011_figures",
      xput("Legacy figures", Some(2011), one, "body" -> "# F\n\n![a figure](md/zxk/figures/fig1.png)\n".asJson)
    )
    put("zxl2012_tags", xput("Tags", Some(2012), one, "tags" -> List("Zx Oracle", "zx_tag").asJson))
    put("zxm2013_noyear", xput("No year", None, one))
    put("zxn2014_mismatch", xput("Year mismatch", Some(2016), one))
    put("zxo2015_pipe", xput("Author pipe", Some(2015), one, "authors" -> List("Zxauthor, Ann | Zxwriter, Bo").asJson))
    put("zxp2016_superseded", xput("Superseded", Some(2016), one, "status" -> "superseded".asJson))
    put("zxq2017_notes", xput("Note format", Some(2017), one, "notes" -> "<div> never closed".asJson))
    put("zxr2018_orphan", xput("Orphan", Some(2018), one))
    // the place rules: a finding on the old geocoder's point (the site; the current one takes the region), one with
    // a name and no point, one on the placeholder point whose name nothing resolves
    put(
      "zxs2019_places",
      xput(
        "Places",
        Some(2019),
        findingsOf(
          xfinding(
            "at Zxville, as the old geocoder placed it",
            Some((30.0, 30.0)),
            Some((-1500L, -1400L)),
            region = "Zxville"
          ),
          xfinding("at Zxfort, with no point", None, Some((-1500L, -1400L)), region = "Zxfort"),
          xfinding(
            "on a grid square's centre, named nowhere",
            Some((22.5, 37.5)),
            Some((-1500L, -1400L)),
            region = "Zxnowhere"
          )
        )
      )
    )
    // what the tables hold besides the writes: a stored original's digest twice, a body's chunks gone
    exec(
      ds,
      sql"UPDATE sources SET sha256 = 'aa' || repeat('0', 62) WHERE citekey IN ('zxg2007_doia', 'zxh2008_doib')"
    )
    exec(ds, sql"DELETE FROM source_chunks WHERE citekey = 'zxi2009_stale'")
    log.result()

  /** Every entry of a bundle: its name and size; a line file's lines; a blob by its length; the manifest without the
    * moment it was made, its id and its digests.
    */
  private def bundleText(bundle: Array[Byte], names: Map[String, String]): String =
    val in = java.util.zip.GZIPInputStream(java.io.ByteArrayInputStream(bundle))
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
        if path == "manifest.json" then
          val m = RawJson.parse(String(data, UTF_8)).fold(e => fail(e.toString), identity)
          val kept = m.mapObject { o =>
            val top = o.filterKeys(k => k != "createdAt" && k != "bundleId")
            top("files").fold(top)(f =>
              top.updated("files", f.mapArray(_.map(_.mapObject(_.filterKeys(_ != "sha256")))))
            )
          }
          out ++= s"== $path\n${xjson(kept.noSpaces, names)}\n"
        else if path.endsWith(".ndjson") then
          val lines = String(data, UTF_8).linesIterator.toList
          out ++= s"== $path (${lines.size} lines)\n"
          lines.foreach(l => out ++= xjson(l, names).linesIterator.mkString(" ") + "\n")
        else out ++= s"== blob ${data.length} bytes\n"
        loop()
    loop()
    out.toString

  /** uid → the record it names, over every database the exchange test made. */
  private def xnames(dss: List[(String, DataSource)]): Map[String, String] =
    val queries = List(
      "SELECT uid::text, 'source:' || citekey FROM sources",
      "SELECT origin::text, 'origin-of:' || citekey FROM sources WHERE origin IS NOT NULL",
      "SELECT uid::text, 'finding:' || id FROM findings",
      "SELECT uid::text, 'tombstone:' || kind FROM tombstones",
      "SELECT alias_uid::text, 'alias-of:' || uid::text FROM uid_aliases"
    ).map(q => sql"${SqlLiteral(q)}")
    def run(ds: DataSource, q: Frag): List[(String, String)] =
      scala.util.Try(connect(ds)(q.query[(String, String)].run().toList)).getOrElse(Nil)
    val records = dss.flatMap((_, ds) => queries.flatMap(run(ds, _)))
    val instances =
      dss.flatMap((n, ds) => run(ds, sql"SELECT id::text, 'x' FROM instance").map((id, _) => id -> s"instance:$n"))
    val merges = dss.flatMap((n, ds) =>
      run(ds, sql"SELECT id::text, 'x' FROM merges ORDER BY created_at").zipWithIndex.map { case ((id, _), i) =>
        id -> s"merge:$n#${i + 1}"
      }
    )
    (instances ++ records ++ merges).distinctBy(_._1).toMap

  private val PlanRe = "plan [0-9a-f]{8}\\b".r
  private val ShortRe = "(?<![-\\w])[0-9a-f]{8}(?![-\\w])".r
  private val Measured = Set("bytes", "files", "durationMs", "elapsedMs", "tookMillis", "ageSeconds", "totalBytes")
  private def digest(key: String): Boolean = key.toLowerCase.matches(".*(rev|hash|sha256)(_?(after|before))?")

  private def xjson(text: String, names: Map[String, String]): String =
    def walk(j: Json): Json =
      j.arrayOrObject(
        j,
        a => Json.fromValues(a.map(walk)),
        o =>
          Json.fromFields(o.toList.map { (k, v) =>
            if Measured(k) && v.isNumber then k -> Json.fromString("N")
            else if digest(k) && v.isString then k -> Json.fromString("DIGEST")
            else k -> walk(v)
          })
      )
    // the arrays a plan orders by its issue ids, which hash random uids: sorted by their normalised text
    def sortIssues(j: Json): Json =
      j.arrayOrObject(
        j,
        a => Json.fromValues(a.map(sortIssues)),
        o =>
          Json.fromFields(o.toList.map { (k, v) =>
            val inner = sortIssues(v)
            if IssueArrays(k) then k -> inner.mapArray(_.sortBy(_.sortedKeys.spaces2)) else k -> inner
          })
      )
    val t = RawJson.parse(text) match
      case Right(j) =>
        val n = normalise(walk(j).sortedKeys.spaces2, names)
        RawJson.parse(n).fold(_ => n, sortIssues(_).sortedKeys.spaces2)
      case Left(_) => normalise(text, names)
    stamps(t)

  /** A plan's short hash, a dump's moment, a short id. */
  private def stamps(t: String): String =
    ShortRe.replaceAllIn(PlanRe.replaceAllIn(StampRe.replaceAllIn(t, "STAMP"), "plan PLAN"), "SHORT")
  private val StampRe = "\\d{8}T\\d{6}Z(?:-\\d+)?".r

  private val IssueArrays =
    Set("issues", "resolve", "resolutions", "resolutionErrors", "problems", "decisions", "remembered")

  /** An issue id → `<letter>-issue:<kind>:<label>`, the label being the record's on either side (a second issue of the
    * same kind and label numbered by its text).
    */
  private def issueNames(plans: List[String]): Map[String, String] =
    val issues = plans
      .flatMap(p => RawJson.parse(p.dropWhile(_ != '{')).toOption)
      .flatMap(_.cursor.downField("issues").focus.flatMap(_.asArray).getOrElse(Vector.empty))
      .flatMap { i =>
        val c = i.cursor
        for
          id <- c.downField("id").as[String].toOption
          kind = c.downField("kind").as[String].getOrElse("?")
          label = c
            .downField("right")
            .downField("label")
            .as[String]
            .toOption
            .orElse(c.downField("left").downField("label").as[String].toOption)
            .getOrElse("?")
        yield (id, s"${id.take(1)}-issue:$kind:$label", i.cursor.downField("title").as[String].getOrElse(""))
      }
      .distinctBy(_._1)
    issues
      .groupBy(_._2)
      .toList
      .flatMap((name, same) =>
        if same.size == 1 then same.map(i => i._1 -> name)
        else same.sortBy(_._3).zipWithIndex.map((i, n) => i._1 -> s"$name#${n + 1}")
      )
      .toMap

  /** The library's tables and the identity rows that name its records, every row, normalised. */
  private val LibraryTables = List(
    "sources" -> "citekey, title, authors, doi, year, venue, status, topics, regions, tags, summary, notes, period::text, ocr_markdown, sha256, origin",
    "source_aliases" -> "alias, citekey",
    "source_chunks" -> "citekey, ord, heading",
    "source_identifiers" -> "*",
    "findings" -> "id, citekey, claim, region_name, ST_AsText(geom) AS geom, period::text, period_name, tags, confidence",
    "finding_places" -> "*",
    "edges" -> "*",
    "unresolved_edges" -> "*",
    "assessments" -> "*",
    "tombstones" -> "kind, uid",
    "uid_aliases" -> "*",
    "queue_items" -> "list, text, topic, status"
  )

  private def libraryRows(ds: DataSource, names: Map[String, String]): String =
    LibraryTables
      .map { (t, cols) =>
        val rs = connect(ds)(
          sql"""SELECT to_jsonb(x)::text FROM (SELECT ${SqlLiteral(cols)} FROM "${SqlLiteral(t)}") x"""
            .query[String]
            .run()
            .toList
        )
        val normed = rs.map(r => xjson(r, names).linesIterator.map(_.trim).mkString(" ")).sorted
        (s"## $t ${normed.size} rows" :: normed).mkString("\n")
      }
      .mkString("", "\n", "\n")

  private def postBytes(url: String, body: Array[Byte]): String =
    val r = raw(
      HttpRequest
        .newBuilder(URI.create(url))
        .header("content-type", "application/octet-stream")
        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
    )
    s"${r.statusCode} ${String(r.body, UTF_8)}"

  private def bytes(url: String): Array[Byte] =
    val r = raw(HttpRequest.newBuilder(URI.create(url)))
    assertEquals(r.statusCode, 200, String(r.body, UTF_8).take(500))
    r.body

  private def awaitApply(url: String, job: String, left: Int = 600): String =
    val j = get(s"$url/api/merge/apply/$job")
    if j.contains("\"done\"") || j.contains("\"failed\"") || left == 0 then j
    else { Thread.sleep(100); awaitApply(url, job, left - 1) }

  /** A merge of `bundle` into the collection at `url`: the first plan, every open issue answered keep-right, the plan
    * again, the resolution file, the apply, the merges, the merge and the ledger.
    */
  private def mergeInto(url: String, bundle: Array[Byte]): List[(String, String)] =
    val up = ok(postBytes(s"$url/api/bundle/upload", bundle))
    val sha = field(up, "sha256")
    val p0 = ok(post(s"$url/api/merge/plan", s"""{"bundle":"$sha"}"""))
    val open = RawJson
      .parse(p0)
      .toOption
      .flatMap(_.cursor.downField("issues").focus)
      .flatMap(_.asArray)
      .getOrElse(Vector.empty)
      .flatMap { i =>
        val options = i.cursor.downField("options").as[List[String]].getOrElse(Nil)
        val suggested = i.cursor.downField("suggested").as[String].toOption
        val choice =
          if options.isEmpty || options.contains("keep-right") then "keep-right" else suggested.getOrElse(options.head)
        i.cursor
          .downField("id")
          .as[String]
          .toOption
          .filter(_ => i.cursor.downField("decision").focus.forall(_.isNull))
          .map(_ -> choice)
      }
      .sorted
    val resolutions =
      s"""{"resolve":[${open.map((id, d) => s"""{"issue":"$id","decision":"$d"}""").mkString(",")}]}"""
    val p1 = ok(post(s"$url/api/merge/plan", s"""{"bundle":"$sha","resolutions":$resolutions}"""))
    val decided = RawJson.decode[MergeResolutions](resolutions).fold(e => fail(e.toString), identity)
    val template =
      MergeResolutions.Text.template(RawJson.decode[MergePlan](p1).fold(e => fail(e.toString), identity), decided)
    val read = ok(post(s"$url/api/merge/resolutions", template.value, "text/plain"))
    val hash = field(p1, "planHash")
    val job = ok(post(s"$url/api/merge/apply", s"""{"bundle":"$sha","planHash":"$hash","resolutions":$resolutions}"""))
    val done = awaitApply(url, field(job, "job"))
    val ml = get(s"$url/api/merges")
    val id = field(ml, "id")
    List(
      "the first plan" -> p0,
      "the plan, every issue answered" -> p1,
      "the resolution file" -> template.value,
      "the resolution file read back" -> read,
      "applied" -> done,
      "merges" -> ml,
      "the merge" -> get(s"$url/api/merges/$id"),
      "the ledger" -> get(s"$url/api/merges/ledger?limit=1000")
    )

  test("the library's kinds through the exchange and its checks, as recorded"):
    val lib = scratch("xlib")
    val home = scratch("xhome")
    val copy = scratch("xcopy")
    val inc = scratch("xinc")
    val checks = scratch("xchecks")
    val fixed = scratch("xfixed")
    val all =
      List("lib" -> lib, "home" -> home, "copy" -> copy, "inc" -> inc, "checks" -> checks, "fixed" -> fixed)
    val crafted = withServer("xlib")(craftExchange)
    // home and inc import lib once: the shared history
    val first = withServer("xlib")(url => bytes(s"$url/api/bundle"))
    val homeFirst = withServer("xhome")(url => ok(postBytes(s"$url/api/bundle/import", first)))
    val incFirst = withServer("xinc")(url => ok(postBytes(s"$url/api/bundle/import", first)))
    val since = connect(lib)(
      sql"SELECT to_char(now() AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS.US\"Z\"')".query[String].run().head
    )
    Thread.sleep(20)
    val edited = withServer("xlib")(l => withServer("xhome")(h => editExchange(l, h)))
    val (whole, part) = withServer("xlib")(url => (bytes(s"$url/api/bundle"), bytes(s"$url/api/bundle?since=$since")))
    val imported = withServer("xcopy")(url => ok(postBytes(s"$url/api/bundle/import", whole)))
    val incremental = withServer("xinc")(url => ok(postBytes(s"$url/api/bundle/import", part)))
    val merged = withServer("xhome")(url => mergeInto(url, whole))
    // the checks: every rule, the safe fixes, the place rules' repairs named
    val (checked, fixes, named, after) = withServer("xchecks") { url =>
      val w = craftChecks(url, checks)
      val report = get(s"$url/api/lint")
      val fix = post(s"$url/api/lint/fix")
      val byName = List("finding-geocode", "finding-unplaced", "finding-placeholder").map(r =>
        r -> (get(s"$url/api/lint?rule=$r") + "\n" + post(s"$url/api/lint/fix?rule=$r") + "\n" + get(
          s"$url/api/lint?rule=$r"
        ))
      )
      (w + "== every rule\n" + report + "\n", fix, byName, get(s"$url/api/lint"))
    }
    val n = xnames(all)
    def shaOf(b: Array[Byte]) = BundleFormat.sha256Hex(b)
    val names2 = issueNames(merged.map(_._2)) ++ n ++
      Map(
        shaOf(whole).value -> "BUNDLE-WHOLE",
        shaOf(first).value -> "BUNDLE-FIRST",
        shaOf(part).value -> "BUNDLE-PART"
      )
    // the resolution file lists its issues in the order of their ids
    def template(text: String): String =
      val (head, rest) = stamps(normalise(text, names2)).linesIterator.toList.span(_ != "resolve:")
      (head ++ rest.take(1) ++ rest.drop(1).sorted).mkString("", "\n", "\n")
    def lines(text: String): String =
      text.linesIterator
        .map { l =>
          "^(\\d{3}) (.*)$".r
            .findFirstMatchIn(l)
            .fold(xjson(l, names2))(m => s"${m.group(1)} ${xjson(m.group(2), names2)}")
        }
        .mkString("", "\n", "\n")
    compare(
      "exchange.txt",
      s"# lib, crafted\n${lines(crafted)}# home and inc imported lib\n${xjson(homeFirst, names2)}\n${xjson(incFirst, names2)}\n" +
        s"# both went on\n${lines(edited)}" +
        s"# lib, whole\n${bundleText(whole, names2)}# lib, since the shared history\n${bundleText(part, names2)}" +
        s"# the whole bundle imported into an empty collection\n${xjson(imported, names2)}\n${libraryRows(copy, names2)}" +
        s"# the part since imported into inc\n${xjson(incremental, names2)}\n${libraryRows(inc, names2)}" +
        merged
          .map((k, v) => s"# home merges lib: $k\n${if k == "the resolution file" then template(v) else lines(v)}")
          .mkString +
        s"# home's rows\n${libraryRows(home, names2)}"
    )
    compare(
      "lint.txt",
      s"# the collection\n${lines(checked)}# the safe fixes\n${lines(fixes)}" +
        named.map((r, t) => s"# $r: the report, the fix named, the report again\n${lines(t)}").mkString +
        s"# every rule after\n${lines(after)}# the rows\n${libraryRows(checks, names2)}"
    )

  test("sources, findings, citations, labels, search, query and what refers to a source, as recorded"):
    val ds = scratch("lib")
    val (written, sourcesText, citationsText, searchText, referrersText) =
      withServer("lib") { url =>
        val w = craft(url, ds)
        val c = citations(url)
        val s = search(url)
        val so = sources(url)
        (w, so, c, s, referrers(url, ds, () => names(ds)))
      }
    val n = names(ds)
    compare("written.txt", answers(written, n))
    compare("sources.txt", answers(sourcesText, n))
    compare("citations.txt", answers(citationsText, n))
    compare("search.txt", answers(searchText, n))
    compare("referrers.txt", answers(referrersText, n))
