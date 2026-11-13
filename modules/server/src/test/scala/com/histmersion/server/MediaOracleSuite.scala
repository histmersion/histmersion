package com.histmersion
package server
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.RawJson
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.api.problem.Text.sentence

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.media.imports.BuiltinMedia

import com.histmersion.config.*
import com.histmersion.domain.Attribution
import com.histmersion.domain.Licence
import com.histmersion.pluginhost.{HostLicences, HttpAnswer, PluginMedia}
import com.histmersion.plugin as spi
import com.histmersion.server.media.{FakeMediaHttp, MediaWire}
import com.augustnagro.magnum.*
import org.ekrich.config.ConfigFactory
import ox.supervised

import java.awt.image.BufferedImage
import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import javax.imageio.ImageIO
import javax.sql.DataSource
import com.histmersion.util.database.Sql
import com.histmersion.db.*
import com.histmersion.api.media.{MediaCoversRequest, MediaImportReport, MediaImportRequest}
import com.histmersion.api.Api
import com.histmersion.config.DbConfig
import com.histmersion.plugin.LicenceReader
import com.histmersion.plugin.MediaRights
import com.histmersion.domain.AuthorName
import com.histmersion.config.BackupsConfig
import com.histmersion.config.BundlesConfig
import com.histmersion.config.ConverterConfig
import com.histmersion.config.AcquireConfig
import com.histmersion.config.InitConfig
import com.histmersion.config.RuntimeConfig
import com.histmersion.config.BasemapConfig
import com.histmersion.config.MediaConfig
import com.histmersion.config.ServerConfig
import com.histmersion.util.data.exchange.BundleFormat
import com.histmersion.plugin.Plugin
import com.histmersion.plugin.Spi
import com.histmersion.plugin.Registry
import com.histmersion.plugin.MediaCollection
import com.histmersion.plugin.MediaMatchRule
import com.histmersion.plugin.MediaTarget
import com.histmersion.plugin.MediaPlace
import com.histmersion.plugin.MediaItem
import com.histmersion.plugin.MediaEvent
import com.histmersion.plugin.MediaFound
import com.histmersion.plugin.MediaUse
import com.histmersion.plugin.Period
import com.histmersion.plugin.Year
import com.histmersion.api.catalogue.Place
import com.histmersion.api.catalogue.Event
import com.histmersion.api.library.RepairAcquire
import com.histmersion.api.library.Citekey
import com.histmersion.plugins.calibre.CalibreFixture
import com.histmersion.cli.Calibre
import com.histmersion.config.CalibreCliConfig
import com.histmersion.cli.Client
import com.histmersion.cli.MediaCovers

/** What media answers, recorded on the code before media moved into its own domain:
  *
  *   - licences: every licence statement and URL classified, the allowlists a config gives (both key families: a
  *     first-party collection's `media.<provider>.licences`, a plugin's `media.collections`), what each allows, the
  *     host's reading lent to plugins and a stated attribution as the host takes it (no database);
  *   - pictures: a crafted collection over REST and MCP — a source's figures stored, read, described and patched, asset
  *     links to every kind of target, IIIF info and images, a cover uploaded and refused, the illustrations chosen, the
  *     figures' licences filled, the media list, facets, a picture's file and thumbnail, the map feed, the cards'
  *     pictures, a bookmark on a picture, the media layer's tiles, `lint --rule media-licence`;
  *   - imports: every first-party collection's dry run and single import over fixture HTTP, a plugin's collection of
  *     its targets' own pictures (`MediaMatchRule.Own`) and one searched by a place's name, Open Library's covers, the
  *     Calibre covers read from a fixture library — the reports and every stored picture, link and check;
  *   - exchange: media's bundle entries, the bundle into an empty collection, an incremental import, and a merge apply
  *     over figures and asset links changed on both sides, with every media row afterwards.
  *
  * Uids are written as the record they name, timestamps as `TS`, job ids as `UUID`. `HISTMERSION_UPDATE_ORACLE=1`
  * rewrites the files. The database tests are skipped without HISTMERSION_TEST_DB_URL.
  */
class MediaOracleSuite extends DbSuite:
  private val testDb = TestDb.url
  override val munitTimeout = scala.concurrent.duration.Duration(10, "min")

  private val oracleDir = Path.of("modules/server/src/test/resources/oracle/media")
  private val update = sys.env.get("HISTMERSION_UPDATE_ORACLE").contains("1")

  private val tmp = Files.createTempDirectory("histmersion-media-oracle")
  private lazy val base = AppConfig.load()
  private lazy val scratch = ScratchDatabases("histmersion_s9o", base.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))

  override def afterSuite(): Unit =
    if testDb.isDefined then scratch.dropAll()

  private def compare(name: String, actual: String): Unit =
    val file = oracleDir.resolve(name)
    if update then
      Files.createDirectories(oracleDir)
      val _ = Files.writeString(file, actual, UTF_8)
    else
      assert(Files.exists(file), s"$file is missing; write it with HISTMERSION_UPDATE_ORACLE=1")
      assertNoDiff(actual, Files.readString(file, UTF_8))

  // ── licences ───────────────────────────────────────────────────────────────────────────────

  private val Statements = List(
    "",
    "   ",
    "cc0",
    "CC0 1.0",
    "CC0-1.0",
    "cc-zero",
    "cc-0",
    "pd",
    "PD-old",
    "PD-US",
    "Public domain",
    "public-domain-mark",
    "publicdomain",
    "PDM",
    "cc-by-4.0",
    "CC BY 4.0",
    "CC-BY-4.0",
    "cc-by-sa-3.0",
    "CC BY-SA 3.0 de",
    "cc-by-sa-3",
    "CC_BY_SA_2.5",
    "cc-by-nc-4.0",
    "CC BY-NC 4.0",
    "cc-by-nc-sa-2.5",
    "CC BY-ND 4.0",
    "cc-by-nd-2.0-fr",
    "cc-by-nc-nd-3.0-us",
    "cc-by",
    "cc-by-sa",
    "GFDL",
    "GFDL-1.2",
    "gfdl-self",
    "arr",
    "All rights reserved",
    "copyrighted",
    "© Zq Museum",
    "Attribution",
    "unknown-licence",
    "  CC BY-SA 4.0  ",
    "https://creativecommons.org/licenses/by/4.0/"
  ).map(Licence.Statement(_))

  private val Urls = List(
    "",
    "https://creativecommons.org/licenses/by-sa/4.0",
    "https://creativecommons.org/licenses/by/4.0/",
    "http://www.creativecommons.org/licenses/by/3.0/de/",
    "https://creativecommons.org/licenses/by-nc/4.0/deed.en",
    "https://creativecommons.org/licenses/by-nc-sa/2.0/fr/deed.fr",
    "https://creativecommons.org/licenses/by-nd/2.5",
    "https://creativecommons.org/publicdomain/zero/1.0/",
    "https://creativecommons.org/publicdomain/mark/1.0/",
    "HTTPS://CreativeCommons.org/publicdomain/zero/1.0/deed.de",
    "https://rightsstatements.org/vocab/InC/1.0/",
    "https://creativecommons.org/licenses/by/x/",
    "not a url"
  ).map(Licence.Url(_))

  private val AllowLists = List(
    "cc0,public-domain,cc-by,cc-by-sa",
    " CC-BY , gfdl ,all-rights-reserved,unknown,bogus",
    "cc-by-nc,cc-by-nc-sa,cc-by-nd,cc-by-nc-nd",
    "",
    ",,,",
    "public-domain"
  ).map(Licence.AllowList(_))

  private def lic(l: Licence): String = s"${l.id.value}|${l.family.wire}"

  private def licences: String =
    val out = StringBuilder()
    val default = Licence.allowList(Licence.AllowList("cc0,public-domain,cc-by,cc-by-sa"))
    out ++= "# classify: statement → id|family · needs attribution · public domain · allowed by default · host's read\n"
    Statements.foreach { s =>
      val l = Licence.classify(Licence.Id(s.value))
      val r = HostLicences.readName(LicenceReader.ReadNameName(s.value))
      out ++= s"${(s.value: String).asJson.noSpaces} → ${lic(l)} · ${l.family.needsAttribution} · " +
        s"${l.family.isPublicDomain} · ${Licence.allowed(l, default)} · " +
        s"${r.id}|${r.family}|${r.needsAttribution}\n"
    }
    out ++= "# ofUrl: url → id|family · host's read\n"
    Urls.foreach { u =>
      val r = HostLicences.readUrl(LicenceReader.ReadUrlUrl(u.value))
      out ++= s"${(u.value: String).asJson.noSpaces} → ${lic(Licence.ofUrl(u))} · ${r.id}|${r.family}|${r.needsAttribution}\n"
    }
    out ++= "# parse(machine, short) and firstKnown\n"
    val pairs = List(
      (Some("cc-by-sa-4.0"), Some("CC BY-SA 4.0")),
      (Some("nonsense"), Some("CC BY 2.0")),
      (None, Some("Public domain")),
      (Some(""), Some("")),
      (Some("odd"), Some("odder")),
      (None, None)
    ).map((m, s) => (m.map(Licence.Statement(_)), s.map(Licence.Statement(_))))
    pairs.foreach((m, s) =>
      out ++= s"${m.map(x => (x.value: String)).asJson.noSpaces}, ${s.map(x => (x.value: String)).asJson.noSpaces} → ${lic(Licence.parse(m, s))}\n"
    )
    List(List("x", "cc0"), List("", " ", "y"), Nil, List("CC BY-NC 4.0", "cc-by-4.0"))
      .map(_.map(Licence.Statement(_)))
      .foreach { ss =>
        val r = HostLicences.readFirst(ss.map(x => LicenceReader.ReadFirstStatements(x.value)))
        out ++= s"${ss.map(x => (x.value: String)).asJson.noSpaces} → ${lic(Licence.firstKnown(ss))} · ${r.id}|${r.family}\n"
      }
    out ++= "# allowList: csv → the families · each family allowed\n"
    val families = Licence.Family.allowable ++ List(Licence.Family.AllRightsReserved, Licence.Family.Unknown)
    AllowLists.foreach { csv =>
      val allow = Licence.allowList(csv)
      out ++= s"${(csv.value: String).asJson.noSpaces} → ${allow.toList.map(_.wire).sorted.mkString(",")} · " +
        families.map(f => s"${f.wire}=${Licence.allowed(Licence(Licence.Id("x"), f), allow)}").mkString(" ") + "\n"
    }
    out ++= "# the allowlist each collection is judged by: defaults, then a config with both key families\n"
    val names = BuiltinMedia.entries.map(_.name) ++ List(
      BuiltinMedia.Entry.Name("zq-portal"),
      BuiltinMedia.Entry.Name("zq-other")
    )
    val defaults = AppConfig.load(ConfigFactory.empty).media
    val configured = AppConfig
      .loadWithUserText(
        ConfigFactory.empty,
        """histmersion.media {
          |  commons.licences = "cc0,cc-by"
          |  met.licences = "public-domain"
          |  open-context.licences = "cc0,public-domain,cc-by,cc-by-sa,cc-by-nc"
          |  europeana.licences = "gfdl,all-rights-reserved"
          |  collections { licences = "cc0", per-collection { "zq-portal" = "cc0,cc-by-nc" } }
          |}
          |""".stripMargin
      )
      .fold(e => fail(e.sentence), _.config.media)
    List("defaults" -> defaults, "configured" -> configured).foreach { (what, cfg) =>
      names.foreach { n =>
        val csv = BuiltinMedia.licencesFor(n, cfg)
        val allow = Licence.allowList(Licence.AllowList(csv.value))
        out ++= s"$what $n: ${(csv.value: String).asJson.noSpaces} → " +
          families.filter(f => Licence.allowed(Licence(Licence.Id("x"), f), allow)).map(_.wire).mkString(",") + "\n"
      }
    }
    out ++= "# a stated attribution as the host takes it\n"
    val stated = List(
      MediaRights(
        spi.Licence("CC BY-SA 4.0"),
        author = Some(MediaRights.Author(" Zq Photographer ")),
        credit = MediaRights.Credit(" Zq, CC BY-SA ")
      ),
      MediaRights(
        spi.Licence("https://creativecommons.org/publicdomain/zero/1.0/"),
        credit = MediaRights.Credit("Zq Museum")
      ),
      MediaRights(
        spi.Licence("see the licence"),
        licenceUrl = Some(MediaRights.LicenceUrl("https://creativecommons.org/licenses/by/3.0/")),
        credit = MediaRights.Credit("Zq")
      ),
      MediaRights(
        spi.Licence("see the licence"),
        licenceUrl = Some(MediaRights.LicenceUrl("https://zq.example/terms")),
        credit = MediaRights.Credit("Zq")
      ),
      MediaRights(
        spi.Licence("cc0"),
        licenceUrl = Some(MediaRights.LicenceUrl(" ")),
        author = Some(MediaRights.Author("")),
        credit = MediaRights.Credit("Zq")
      ),
      MediaRights(spi.Licence("cc-by-4.0"), credit = MediaRights.Credit("  ")),
      MediaRights(spi.Licence("All rights reserved"), credit = MediaRights.Credit("Zq Museum"))
    )
    stated.foreach { r =>
      out ++= s"${(r.licence.value: String).asJson.noSpaces} ${r.licenceUrl.map(x => (x.value: String)).asJson.noSpaces} → " +
        PluginMedia
          .rights(r)
          .fold(
            why => s"refused: ${why.sentence}",
            a =>
              s"${lic(a.licence)} url=${a.licenceUrl.map(x => (x.value: String)).asJson.noSpaces} author=${a.author.map(x => (x.value: String)).asJson.noSpaces} " +
                s"credit=${(a.credit.value: String).asJson.noSpaces}"
          ) + "\n"
    }
    out ++= "# the host's own statements\n"
    List(
      Attribution.cc0(Some(AuthorName("Zq")), Attribution.Cc0Credit("Zq, CC0")),
      Attribution.publicDomain(None, Attribution.PublicDomainCredit("Zq Museum")),
      Attribution.publicDomain(Some(AuthorName("Zq")), Attribution.PublicDomainCredit("Zq"), None)
    ).foreach(a =>
      out ++= s"${lic(a.licence)} url=${a.licenceUrl.map(x => (x.value: String)).asJson.noSpaces} author=${a.author.map(x => (x.value: String)).asJson.noSpaces} " +
        s"credit=${(a.credit.value: String).asJson.noSpaces}\n"
    )
    out.result()

  test("licences: classification, the allowlists of both key families, attributions, as recorded"):
    compare("licences.txt", licences)

  // ── the servers ────────────────────────────────────────────────────────────────────────────

  private def cfgFor(name: String): AppConfig =
    base
      .modify(_.db)
      .setTo(base.db.modify(_.url).setTo(scratch.urlOf(s"histmersion_s9o_${name}_test")))
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
      .modify(_.media)
      .setTo(base.media.modify(_.requestIntervalMs).setTo(MediaConfig.RequestIntervalMs(0)))

  private def withServer[A](name: String)(body: String => A): A =
    supervised {
      val b = Composition
        .server(cfgFor(name), ServerConfig.Host("127.0.0.1"), ServerConfig.Port(0), MediaWire.Thumbs())
        .start()
      try body(s"http://127.0.0.1:${b.port}")
      finally b.stop()
    }

  private val http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()

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

  private def putBytes(url: String, body: Array[Byte], contentType: String): String =
    val r = raw(
      HttpRequest
        .newBuilder(URI.create(url))
        .header("content-type", contentType)
        .PUT(HttpRequest.BodyPublishers.ofByteArray(body))
    )
    s"${r.statusCode} ${String(r.body, UTF_8)}"

  private def postBytes(url: String, body: Array[Byte]): String =
    val r = raw(
      HttpRequest
        .newBuilder(URI.create(url))
        .header("content-type", "application/octet-stream")
        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
    )
    s"${r.statusCode} ${String(r.body, UTF_8)}"

  /** Status, type, length and digest of an answer whose body is bytes. */
  private def binary(url: String): String =
    val r = raw(HttpRequest.newBuilder(URI.create(url)))
    val kind = r.headers().firstValue("content-type").orElse("-")
    if r.statusCode == 200 then s"${r.statusCode} $kind ${r.body.length} ${BundleFormat.sha256Hex(r.body)}"
    else s"${r.statusCode} $kind ${String(r.body, UTF_8)}"

  private def bytes(url: String): Array[Byte] =
    val r = raw(HttpRequest.newBuilder(URI.create(url)))
    assertEquals(r.statusCode, 200, String(r.body, UTF_8).take(500))
    r.body

  private def ok(answer: String): String =
    assert(answer.startsWith("200 "), answer.take(800))
    answer.drop(4)

  private def field(json: String, name: String): String =
    s""""$name"\\s*:\\s*"([^"]*)"""".r.findFirstMatchIn(json).map(_.group(1)).getOrElse(fail(s"no $name in $json"))

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

  private def awaitJob(url: String, id: String, left: Int = 600): String =
    val j = get(s"$url/api/jobs/$id")
    if j.contains("\"done\"") || j.contains("\"failed\"") || left == 0 then j
    else { Thread.sleep(100); awaitJob(url, id, left - 1) }

  private def exec(ds: DataSource, statement: String): Unit =
    connect(ds)(Sql.raw(Sql.Text(statement)).frag.update.run(): Unit)

  // ── pictures ───────────────────────────────────────────────────────────────────────────────

  /** An image of `w`×`h` whose pixels follow `seed`, encoded as `format` (`png`, `jpg`). */
  private def image(w: Int, h: Int, seed: Int, format: String): Array[Byte] =
    val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
    for x <- 0 until w; y <- 0 until h do
      img.setRGB(x, y, ((x * 31 + y * 17 + seed * 101) * 2654435761L).toInt & 0xffffff)
    val out = ByteArrayOutputStream()
    val _ = ImageIO.write(img, format, out)
    out.toByteArray

  private def b64(b: Array[Byte]): String = java.util.Base64.getEncoder.encodeToString(b)

  private def asset(name: String, mime: String, b: Array[Byte], size: Option[(Int, Int)] = None): Json =
    Json.fromFields(
      List("name" -> name.asJson, "mime" -> mime.asJson, "base64" -> b64(b).asJson) ++
        size.toList.flatMap((w, h) => List("width" -> w.asJson, "height" -> h.asJson))
    )

  private def finding(claim: String, lon: Double, lat: Double, from: Long, to: Long): Json =
    Json.obj(
      "claim" -> claim.asJson,
      "region" -> "Zqland".asJson,
      "lon" -> lon.asJson,
      "lat" -> lat.asJson,
      "periodStart" -> from.asJson,
      "periodEnd" -> to.asJson,
      "tags" -> List("oracle").asJson,
      "confidence" -> "high".asJson
    )

  private def source(title: String, year: Int, body: String, findings: List[Json], doi: Option[String] = None): String =
    Json
      .fromFields(
        List(
          "title" -> title.asJson,
          "authors" -> List("Doe, Jane").asJson,
          "year" -> year.asJson,
          "topics" -> List("bronze age").asJson,
          "regions" -> List("Zqland").asJson,
          "body" -> body.asJson,
          "findings" -> Json.fromValues(findings)
        ) ++ doi.map("doi" -> _.asJson).toList
      )
      .noSpaces

  private val sites =
    """{"type":"FeatureCollection","features":[
      |{"@id":"zq_tell","type":"Feature","properties":{"title":"Zqtell"},
      | "when":{"timespans":[{"start":{"in":"-1650"},"end":{"in":"-1180"}}]},
      | "geometry":{"type":"Point","coordinates":[34.6,40.0]}},
      |{"@id":"zq_cove","type":"Feature","properties":{"title":"Zqcove"},
      | "geometry":{"type":"Point","coordinates":[35.5,39.2]}}
      |]}""".stripMargin

  private val bodyFigs =
    """# Zqtell
      |
      |The tell of Zqtell is shown in ![the plan](assets/fig-a.png) and again in ![](assets/fig-a.png).
      |The cove is ![a photograph](assets/fig-c.jpg).
      |""".stripMargin

  private val figA = image(64, 48, 1, "png")
  private val figB = image(8, 8, 2, "png")
  private val figC = image(120, 90, 3, "jpg")
  private val coverJpeg = image(40, 60, 4, "jpg")

  /** Writes, each recorded with its answer. */
  private def craft(url: String, ds: DataSource): String =
    val log = StringBuilder()
    def step(name: String, answer: String): String =
      log ++= s"== $name\n$answer\n"
      answer
    val _ = step("places import sites", post(s"$url/api/places/import?kind=site", sites, "text/plain"))
    val _ = step(
      "source with figures",
      send(
        "PUT",
        s"$url/api/sources/zqfig2001_tell",
        source(
          "Zqtell excavated",
          2001,
          bodyFigs,
          List(finding("the tell was settled", 34.6, 40.0, -1600, -1200), finding("the cove", 35.5, 39.2, -1400, -1300))
        )
      )
    )
    val _ = step(
      "source, a book",
      send("PUT", s"$url/api/sources/zqbook1999_ships", source("Zq ships", 1999, "# Ships\n\nNo figures.\n", Nil))
    )
    exec(ds, "UPDATE sources SET license = 'CC-BY-4.0' WHERE citekey = 'zqfig2001_tell'")
    exec(
      ds,
      """INSERT INTO source_identifiers (source_uid, scheme, value)
         SELECT uid, 'isbn', '9780000000002' FROM sources WHERE citekey = 'zqbook1999_ships'"""
    )
    exec(ds, "UPDATE sources SET calibre_id = '5' WHERE citekey = 'zqbook1999_ships'")
    val _ = step(
      "event",
      send(
        "PUT",
        s"$url/api/events/zq_siege",
        """{"label":"The siege of Zqtell","kind":"battle","timeSpan":{"notBefore":-1300,"notAfter":-1290},"placeId":"zq_tell"}"""
      )
    )
    val figures = Json.arr(
      asset("fig-a.png", "image/png", figA, Some((64, 48))),
      asset("fig-b.png", "image/png", figB),
      asset("fig-c.jpg", "image/jpeg", figC),
      asset("fig-a-again.png", "image/png", figA)
    )
    val _ = step("figures", post(s"$url/api/sources/zqfig2001_tell/assets", figures.noSpaces))
    val _ = step(
      "figures, again (left alone)",
      post(s"$url/api/sources/zqfig2001_tell/assets", Json.arr(asset("fig-b.png", "image/png", figC)).noSpaces)
    )
    val _ = step(
      "figures, refused",
      post(s"$url/api/sources/zqfig2001_tell/assets", """[{"name":"bad.png","mime":"image/png","base64":"%%%"}]""")
    )
    val _ = step(
      "figures, empty",
      post(s"$url/api/sources/zqfig2001_tell/assets", """[{"name":"none.png","mime":"image/png","base64":""}]""")
    )
    val _ = step(
      "patch fig-a",
      send(
        "PATCH",
        s"$url/api/sources/zqfig2001_tell/assets/fig-a.png",
        """{"kind":"plan","caption":"The plan of Zqtell","lat":40.0,"lon":34.6,"periodStart":-1600}"""
      )
    )
    val _ = step(
      "patch fig-a, its end",
      send("PATCH", s"$url/api/sources/zqfig2001_tell/assets/fig-a.png", """{"periodEnd":-1200}""")
    )
    val _ = step(
      "patch fig-c",
      send(
        "PATCH",
        s"$url/api/sources/zqfig2001_tell/assets/fig-c.jpg",
        """{"kind":"photo","caption":"The cove","license":"CC0-1.0","lat":39.2,"lon":35.5,"periodStart":-1400,"periodEnd":-1300}"""
      )
    )
    val _ = step(
      "patch, an end before the start",
      send("PATCH", s"$url/api/sources/zqfig2001_tell/assets/fig-c.jpg", """{"periodEnd":-1500}""")
    )
    val _ = step(
      "patch, no such asset",
      send("PATCH", s"$url/api/sources/zqfig2001_tell/assets/nope.png", """{"caption":"x"}""")
    )
    def link(asset: String, relation: String, tt: String, id: String, extra: String = ""): String =
      post(
        s"$url/api/asset-links",
        s"""{"citekey":"zqfig2001_tell","assetName":"$asset","relation":"$relation","targetType":"$tt","targetId":"$id"$extra}"""
      )
    val _ = step("link to a place", link("fig-a.png", "depicts", "place", "zq_tell", ""","xywh":"0,0,32,24""""))
    val _ = step("link to an event", link("fig-a.png", "illustrates", "event", "zq_siege", ""","notes":"the siege""""))
    val _ = step("link to a finding", link("fig-c.jpg", "taken_at", "finding", "zqfig2001_tell#2"))
    val _ = step("link to a source", link("fig-c.jpg", "page_of", "source", "zqbook1999_ships"))
    val _ = step("link to an article", link("fig-b.png", "illustrates", "article", "zq-article"))
    val _ = step("link, no such asset", link("nope.png", "depicts", "place", "zq_tell"))
    val _ = step("link, a bad relation", link("fig-a.png", "eats", "place", "zq_tell"))
    val gone = step("link to delete", link("fig-b.png", "depicts", "place", "zq_cove"))
    val _ = step(
      "delete a link",
      send(
        "DELETE",
        s"$url/api/asset-links/${"\"id\"\\s*:\\s*(\\d+)".r
            .findFirstMatchIn(gone)
            .map(_.group(1))
            .getOrElse("0")}",
        ""
      )
    )
    val _ = step("delete a link, again", send("DELETE", s"$url/api/asset-links/999", ""))
    val _ = step(
      "cover from the Calibre library",
      putBytes(s"$url/api/media/sources/zqbook1999_ships/cover?key=5", coverJpeg, "application/octet-stream")
    )
    val _ = step(
      "cover, another provider",
      putBytes(
        s"$url/api/media/sources/zqbook1999_ships/cover?key=5&provider=open-library",
        coverJpeg,
        "application/octet-stream"
      )
    )
    val _ = step(
      "cover, not an image",
      putBytes(s"$url/api/media/sources/zqbook1999_ships/cover?key=5", "text".getBytes(UTF_8), "text/plain")
    )
    val _ = step(
      "cover, no such source",
      putBytes(s"$url/api/media/sources/zqnone1900_x/cover?key=5", coverJpeg, "application/octet-stream")
    )
    val _ = step(
      "cover, empty",
      putBytes(s"$url/api/media/sources/zqbook1999_ships/cover?key=5", Array.emptyByteArray, "application/octet-stream")
    )
    val _ = step(
      "covers and illustrations, a dry run",
      post(s"$url/api/media/covers", """{"dryRun":true,"covers":true,"illustrations":true}""")
    )
    val _ = step(
      "illustrations of one source",
      post(
        s"$url/api/media/covers",
        """{"dryRun":false,"citekey":"zqfig2001_tell","covers":false,"illustrations":true}"""
      )
    )
    val _ = step("covers over every source, refused", post(s"$url/api/media/covers", """{"dryRun":false}"""))
    val _ = step("fill licences, a dry run", post(s"$url/api/media/fill-licences"))
    val _ = step("fill licences", post(s"$url/api/media/fill-licences?apply=true"))
    val _ = step(
      "bookmark a picture",
      post(
        s"$url/api/bookmarks",
        s"""{"target":{"kind":"picture","id":"${uidOf(
            ds,
            "fig-c.jpg"
          )}"}}"""
      )
    )
    log.result()

  private def uidOf(ds: DataSource, name: String): String =
    connect(ds)(sql"SELECT uid::text FROM assets WHERE name = $name".query[String].run().headOption.getOrElse("none"))

  /** The z/x/y of the tile holding a point. */
  private def tileOf(z: Int, lon: Double, lat: Double): (Int, Long, Long) =
    val n = 1L << z
    val x = ((lon + 180) / 360 * n).toLong
    val r = math.toRadians(lat)
    val y = ((1 - math.log(math.tan(r) + 1 / math.cos(r)) / math.Pi) / 2 * n).toLong
    (z, x, y)

  /** A tile's status, length and digest, the pictures' uids it carries written as the records they name first. */
  private def tileLine(url: String, path: String, names: Map[String, String]): String =
    val r = raw(HttpRequest.newBuilder(URI.create(s"$url/api/tiles/$path")))
    val named = UuidRe.replaceAllIn(
      String(r.body, java.nio.charset.StandardCharsets.ISO_8859_1),
      m => scala.util.matching.Regex.quoteReplacement(names.getOrElse(m.matched, "UUID"))
    )
    val sha = BundleFormat
      .sha256Hex(named.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1))
    val etag = r.headers().firstValue("etag").orElse("-")
    s"$path ${r.statusCode} ${r.body.length} $sha $etag"

  /** Every read: REST, the MCP tools, the bytes as digests, the media layer's tiles. */
  private def reads(url: String, ds: DataSource): String =
    val c = uidOf(ds, "fig-c.jpg")
    val a = uidOf(ds, "fig-a.png")
    val cover = connect(ds)(
      sql"SELECT uid::text FROM assets WHERE provider = 'calibre'".query[String].run().headOption.getOrElse("none")
    )
    val gets = List(
      "api/sources/zqfig2001_tell/assets",
      "api/sources/zqnone1900_x/assets",
      "api/sources/zqfig2001_tell/assets/fig-a.png/detail",
      "api/sources/zqfig2001_tell/assets/fig-c.jpg/detail",
      "api/sources/zqfig2001_tell/assets/nope.png/detail",
      "api/asset-links",
      "api/asset-links?citekey=zqfig2001_tell&asset=fig-a.png",
      "api/asset-links?target_type=event",
      "api/asset-links?target_type=place&target_id=zq_tell",
      "iiif/zqfig2001_tell/fig-a.png/info.json",
      "iiif/zqfig2001_tell/fig-c.jpg/info.json",
      "iiif/zqfig2001_tell/nope.png/info.json",
      "iiif/zqfig2001_tell/fig-a.png/0,0,10,10/full/0/default.png",
      "iiif/zqfig2001_tell/fig-a.png/full/90,/0/default.tiff",
      "iiif/zqfig2001_tell/fig-a.png/nonsense/full/0/default.jpg",
      "iiif/zqfig2001_tell/fig-a.png/full/full/90/default.jpg",
      "api/media",
      "api/media?provider=source",
      "api/media?provider=calibre",
      "api/media?license=none",
      "api/media?license=CC0-1.0",
      "api/media?place=zq_tell",
      "api/media?q=cover&limit=1",
      "api/media?offset=1&limit=2",
      "api/media/facets",
      "api/media/providers",
      s"api/media/$c",
      s"api/media/$cover",
      "api/media/not-a-uid",
      "api/media/00000000-0000-0000-0000-000000000000",
      "api/media-geo",
      "api/media-geo?kind=plan",
      "api/media-geo?from=-1350&to=-1320",
      "api/media-geo?provider=source&license=CC0-1.0",
      "api/places/zq_tell",
      "api/places/zq_cove",
      "api/events/zq_siege",
      "api/sources/zqfig2001_tell",
      "api/sources/zqbook1999_ships",
      "api/bookmarks",
      "api/lint?rule=media-licence"
    )
    val files = List(
      "source/zqfig2001_tell/assets/fig-a.png",
      "source/zqfig2001_tell/assets/fig-c.jpg",
      "source/zqfig2001_tell/assets/fig-a-again.png",
      "source/zqfig2001_tell/assets/nope.png",
      "iiif/zqfig2001_tell/fig-a.png/full/full/0/default.png",
      "iiif/zqfig2001_tell/fig-a.png/0,0,32,24/full/0/default.jpg",
      "iiif/zqfig2001_tell/fig-c.jpg/full/60,/0/default.jpg",
      "iiif/zqfig2001_tell/fig-c.jpg/pct:10,10,50,50/30,20/0/default.png",
      s"api/media/$c/thumb",
      s"api/media/$c/file",
      s"api/media/$a/thumb",
      s"api/media/$cover/thumb",
      s"api/media/$cover/file",
      "api/media/not-a-uid/file"
    )
    val tools = List(
      "assets" -> Json.obj("citekey" -> "zqfig2001_tell".asJson),
      "assets" -> Json.obj("citekey" -> "zqnone1900_x".asJson),
      "patch_asset" -> Json.obj(
        "citekey" -> "zqfig2001_tell".asJson,
        "name" -> "fig-b.png".asJson,
        "caption" -> "A tiny sherd".asJson,
        "kind" -> "figure".asJson
      ),
      "patch_asset" -> Json.obj("citekey" -> "zqfig2001_tell".asJson, "name" -> "nope.png".asJson),
      "asset_links" -> Json.obj("citekey" -> "zqfig2001_tell".asJson),
      "asset_links" -> Json.obj(
        "citekey" -> "zqfig2001_tell".asJson,
        "asset" -> "fig-b.png".asJson,
        "relation" -> "depicts".asJson,
        "targetType" -> "place".asJson,
        "targetId" -> "zq_cove".asJson
      ),
      "asset_links" -> Json.obj("targetType" -> "place".asJson),
      "delete_asset_link" -> Json.obj("id" -> 1.asJson),
      "delete_asset_link" -> Json.obj("id" -> 999.asJson),
      "lint" -> Json.obj("rules" -> List("media-licence").asJson)
    )
    val coords = List(0, 4, 8, 12).flatMap(z => List(tileOf(z, 34.6, 40.0), tileOf(z, 35.5, 39.2))).distinct
    val variants = List(
      "",
      "?from=-1350&to=-1320",
      "?from=-1700&to=-1650",
      "?kind=plan",
      "?kind=photo",
      "?provider=source",
      "?license=none",
      "?license=CC0-1.0"
    )
    val named = names(List("med" -> ds))
    val tiles = for
      (z, x, y) <- coords
      v <- variants
    yield tileLine(url, s"media/$z/$x/$y$v", named)
    (gets.map(g => s"== GET /$g\n${get(s"$url/$g")}") ++
      files.map(f => s"== GET /$f (bytes)\n${binary(s"$url/$f")}") ++
      tools.map((n, args) => s"== tool $n ${args.noSpaces}\n${tool(url, n, args)}") ++
      List("== tiles: path status length sha256 etag", tiles.mkString("\n"))).mkString("", "\n", "\n")

  // ── normalisation ──────────────────────────────────────────────────────────────────────────

  private val UuidRe =
    "\\b[A-Z]-[0-9a-f]{8}\\b|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}".r
  private val TimeRe =
    "\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?(?:Z|[+-]\\d{2}(?::?\\d{2})?)?".r
  private val AddedRe = "(\"added(?:Date|_date)\" ?: ?)\"\\d{4}-\\d{2}-\\d{2}\"".r
  private val PlanRe = "plan [0-9a-f]{8}\\b".r
  private val DurationRe = "(\"(?:durationMs|elapsedMs|tookMillis|ageSeconds)\" ?: ?)\\d+".r
  private val StampRe = "\\d{8}T\\d{6}Z(?:-\\d+)?".r
  private val ShortRe = "(?<![-\\w])[0-9a-f]{8}(?![-\\w])".r

  /** uid → the record it names. */
  private def names(dss: List[(String, DataSource)]): Map[String, String] =
    val queries = List(
      "SELECT uid::text, 'source:' || citekey FROM sources",
      "SELECT uid::text, 'finding:' || id FROM findings",
      "SELECT uid::text, 'event:' || id FROM events",
      "SELECT uid::text, 'place:' || id FROM places",
      "SELECT uid::text, 'asset:' || coalesce(citekey || '/' || name, provider || ':' || provider_key) FROM assets",
      "SELECT uid::text, 'asset-link:' || citekey || '/' || asset_name || ':' || relation || ':' || target_type || ':' || target_id FROM asset_links",
      "SELECT uid::text, 'tombstone:' || kind FROM tombstones",
      "SELECT alias_uid::text, 'alias-of:' || uid::text FROM uid_aliases",
      "SELECT id::text, 'bookmark' FROM bookmarks"
    )
    def run(ds: DataSource, q: String): List[(String, String)] =
      scala.util.Try(connect(ds)(Sql.raw(Sql.Text(q)).frag.query[(String, String)].run().toList)).getOrElse(Nil)
    val records = dss.flatMap((_, ds) => queries.flatMap(run(ds, _)))
    val instances =
      dss.flatMap((n, ds) => run(ds, "SELECT id::text, 'x' FROM instance").map((id, _) => id -> s"instance:$n"))
    (records ++ instances).distinctBy(_._1).toMap

  private def normalise(text: String, names: Map[String, String]): String =
    val t1 = List(tmp.toString -> "TMP").foldLeft(text)((t, r) => t.replace(r._1, r._2))
    val t2 = UuidRe.replaceAllIn(
      t1,
      m => scala.util.matching.Regex.quoteReplacement(names.getOrElse(m.matched, "UUID"))
    )
    val t3 = DurationRe.replaceAllIn(AddedRe.replaceAllIn(TimeRe.replaceAllIn(t2, "TS"), "$1\"DAY\""), "$1N")
    PlanRe.replaceAllIn(t3, "plan PLAN")

  /** A line's revision, a bundle's digest (both hash random uids). */
  private def digest(key: String): Boolean =
    key == "bundle" || key.toLowerCase.matches(".*(rev|hash)(_?(after|before))?")

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
    RawJson.parse(text) match
      case Right(j) => normalise(walk(j).sortedKeys.spaces2, names)
      case Left(_)  => normalise(text, names)

  /** A recorded answer: its status, then its body as sorted JSON when it is JSON. */
  private def answers(text: String, names: Map[String, String]): String =
    text.linesIterator
      .map { l =>
        val m = "^(\\d{3}) (.*)$".r.findFirstMatchIn(l)
        m.fold(normalise(l, names))(m => s"${m.group(1)} ${json(m.group(2), names)}")
      }
      .mkString("", "\n", "\n")

  /** Media's tables, every row normalised; bytes as their length and digest. */
  private val MediaTables = List("assets", "asset_links", "media_links", "media_checks")

  private def rows(ds: DataSource, names: Map[String, String]): String =
    MediaTables
      .map { t =>
        val q =
          if t == "assets" then
            """SELECT (to_jsonb(x) - 'bytes' - 'thumb' || jsonb_build_object('bytes', length(x.bytes), 'thumb',
                length(x.thumb), 'thumbSha', encode(sha256(coalesce(x.thumb, ''::bytea)), 'hex'),
                'geom', ST_AsText(x.geom)))::text FROM assets x"""
          else s"""SELECT to_jsonb(x)::text FROM "$t" x"""
        val rs = connect(ds)(Sql.raw(Sql.Text(q)).frag.query[String].run().toList)
        val normed = rs.map(r => exchanged(json(r, names), names).linesIterator.map(_.trim).mkString(" ")).sorted
        (s"## $t ${normed.size} rows" :: normed).mkString("\n")
      }
      .mkString("", "\n", "\n")

  /** The other collection's label a renamed figure arrives with: its instance's short id. */
  private val LabelledRe = "-[0-9a-f]{8}(-\\d+)?\\.(png|jpg)\\b".r

  private def exchanged(text: String, names: Map[String, String]): String =
    val stamped = LabelledRe.replaceAllIn(
      StampRe.replaceAllIn(text, "STAMP"),
      m => s"-LABEL${Option(m.group(1)).getOrElse("")}.${m.group(2)}"
    )
    resorted(ShortRe.replaceAllIn(stamped, m => names.getOrElse(m.matched, "SHORT")))

  private val HashOrdered = Set("issues", "resolve", "remembered")

  private def resorted(text: String): String =
    def walk(j: Json): Json =
      j.arrayOrObject(
        j,
        a => Json.fromValues(a.map(walk)),
        o =>
          Json.fromFields(o.toList.map { (k, v) =>
            val w = walk(v)
            if HashOrdered(k) then k -> w.asArray.fold(w)(a => Json.fromValues(a.sortBy(_.sortedKeys.spaces2)))
            else k -> w
          })
      )
    val (status, body) =
      "^(\\d{3} )([\\s\\S]*)$".r.findFirstMatchIn(text).fold(("", text))(m => (m.group(1), m.group(2)))
    RawJson
      .parse(body)
      .fold(_ => text, j => status + walk(j).sortedKeys.spaces2 + (if text.endsWith("\n") then "\n" else ""))

  test("pictures: figures, asset links, IIIF, covers, illustrations, the list, the map, MCP and lint, as recorded"):
    assume(testDb.isDefined, "HISTMERSION_TEST_DB_URL not set")
    val med = scratch("med")
    val (written, read) = withServer("med")(url => (craft(url, med), reads(url, med)))
    val n = names(List("med" -> med))
    compare("pictures.txt", s"${answers(written, n)}# reads\n${answers(read, n)}# rows\n${rows(med, n)}")

  // ── imports ────────────────────────────────────────────────────────────────────────────────

  /** A plugin's collection of its targets' own pictures: it is asked about the whole batch and states each licence. */
  final private class OwnPlugin extends Plugin:
    type Config = ZqOracleConfig
    val info = Plugin.Info(Plugin.Info.Id("zq-own"), Plugin.Info.Version("0.1.0"), Plugin.Info.SpiVersion(Spi.Version))
    override def defaultsResource = Plugin.DefaultsResource("zq-none.conf")
    def register(c: ZqOracleConfig, ctx: Plugin.Context) =
      ctx.mediaCollections
        .add(Registry.AddId("zq-own"), OwnCollection)
        .flatMap(_ => ctx.mediaCollections.add(Registry.AddId("zq-search"), Searched))

  private object OwnCollection extends MediaCollection:
    val title = MediaCollection.Title("Zq own pictures")
    override val description = MediaCollection.Description("A fixture's collection of its targets' own pictures")
    val role = MediaCollection.Image
    override val requestIntervalMs = MediaCollection.RequestIntervalMs(0)
    override val matchRule = MediaMatchRule.Own
    override val asks = Set(MediaTarget.Places, MediaTarget.Events)
    def search(place: MediaPlace, http: Plugin.Http) = Right(Nil)
    private def item(
        key: MediaItem.Key,
        licence: spi.Licence,
        author: Option[MediaRights.Author],
        role: Option[MediaItem.Role]
    ) =
      MediaItem(
        key,
        MediaItem.Title(s"Zq picture $key"),
        MediaItem.PageUrl(s"https://zq-own.example/page/$key"),
        Some(MediaItem.ImageUrl(s"https://zq-own.example/img/$key.jpg")),
        Right(MediaRights(licence, author = author, credit = MediaRights.Credit(s"Zq picture $key, $licence"))),
        role = role
      )
    override def searchTargets(
        places: List[MediaPlace],
        events: List[MediaEvent],
        http: Plugin.Http
    ): MediaFound =
      val _ = http.get(Plugin.Http.Url("https://zq-own.example/batch"), Map.empty)
      val placeUses = places.flatMap { p =>
        List(
          MediaUse(
            MediaTarget.Id(MediaTarget.Places, MediaTarget.Id.Value(p.id.value)),
            item(MediaItem.Key(s"${p.id}-by"), spi.Licence("CC BY 4.0"), Some(MediaRights.Author("Zq")), None),
            statement = Some(MediaUse.Statement("P18")),
            rank = MediaUse.Rank(0),
            note = Some(MediaUse.Note("its own picture"))
          ),
          MediaUse(
            MediaTarget.Id(MediaTarget.Places, MediaTarget.Id.Value(p.id.value)),
            item(
              MediaItem.Key(s"${p.id}-nc"),
              spi.Licence("CC BY-NC 4.0"),
              Some(MediaRights.Author("Zq")),
              Some(MediaItem.Role("plan"))
            ),
            rank = MediaUse.Rank(1)
          ),
          MediaUse(
            MediaTarget.Id(MediaTarget.Places, MediaTarget.Id.Value(p.id.value)),
            item(
              MediaItem.Key(s"${p.id}-anon"),
              spi.Licence("cc-by-sa-4.0"),
              None,
              Some(MediaItem.Role("locator-map"))
            ),
            rank = MediaUse.Rank(2)
          )
        )
      }
      val eventUses = events.map(e =>
        MediaUse(
          MediaTarget.Id(MediaTarget.Events, MediaTarget.Id.Value(e.id.value)),
          item(
            MediaItem.Key(s"${e.id}-pd"),
            spi.Licence("https://creativecommons.org/publicdomain/mark/1.0/"),
            None,
            None
          ),
          rank = MediaUse.Rank(0),
          basis = Some(MediaUse.Basis("the event's own item"))
        )
      )
      MediaFound(
        uses = placeUses ++ eventUses,
        asked = places.map(p => MediaTarget.Id(MediaTarget.Places, MediaTarget.Id.Value(p.id.value))) ++
          events.map(e => MediaTarget.Id(MediaTarget.Events, MediaTarget.Id.Value(e.id.value))),
        read = MediaFound.Read(placeUses.size + eventUses.size),
        notTaken = List(MediaFound.NotTaken("a statement the plugin did not take"))
      )

  private object Searched extends MediaCollection:
    val title = MediaCollection.Title("Zq searched objects")
    val role = MediaCollection.Object
    override val requestIntervalMs = MediaCollection.RequestIntervalMs(0)
    def search(place: MediaPlace, http: Plugin.Http) =
      http
        .get(Plugin.Http.Url(s"https://zq-search.example/q?label=${place.label}"), Map.empty)
        .map { _ =>
          def it(key: MediaItem.Key, licence: spi.Licence, where: MediaItem.Geography.Value) =
            Right(
              MediaItem(
                key,
                MediaItem.Title(s"Zq object $key"),
                MediaItem.PageUrl(s"https://zq-search.example/objects/$key"),
                Some(MediaItem.ImageUrl(s"https://zq-search.example/img/$key.jpg")),
                Right(
                  MediaRights(
                    licence,
                    author = Some(MediaRights.Author("Zq Museum")),
                    credit = MediaRights.Credit(s"Zq object $key")
                  )
                ),
                geography = List(MediaItem.Geography.Field("find_spot") -> where),
                period = Period.from(Year(-1500), Year(-1300)).toOption
              )
            )
          List(
            it(MediaItem.Key("s1"), spi.Licence("CC0-1.0"), MediaItem.Geography.Value(place.label.value)),
            it(MediaItem.Key("s2"), spi.Licence("All rights reserved"), MediaItem.Geography.Value(place.label.value)),
            it(MediaItem.Key("s3"), spi.Licence("CC BY 4.0"), MediaItem.Geography.Value("Nowhere at all")),
            Left(Plugin.Http.Failure.Transport(Problem.Unclassified(Legacy.Text("an unreadable record"))))
          )
        }

  private def ownRoute(url: String): Either[String, HttpAnswer] =
    if url.startsWith("https://zq-own.example/batch") || url.startsWith("https://zq-search.example/q") then
      FakeMediaHttp.json("""{"ok":true}""")
    else if url.contains("covers.openlibrary.org") then
      if url.contains("9780000000009") then
        Right(HttpAnswer(HttpAnswer.Code(404), Map.empty, HttpAnswer.Body(Array.emptyByteArray)))
      else FakeMediaHttp.bytes(MediaWire.jpeg(s"cover $url"), "image/jpeg")
    else MediaWire.route(url)

  private def imports(ds: DataSource): String =
    val sb = StringBuilder()
    val loaded = MediaWire
      .load("""histmersion.plugins.zq-own.enabled = true
              |histmersion.media.collections.per-collection { "zq-own" = "cc0,public-domain,cc-by,cc-by-sa" }
              |""".stripMargin)
      .fold(e => fail(e.sentence), identity)
    val wire = MediaWire.Wire(ownRoute)
    val plugins = ServerPlugins.offlineWith(
      List(OwnPlugin()),
      ConfigFactory.parseString(MediaWire.text + "histmersion.plugins.zq-own.enabled = true\n"),
      ConfigFactory.parseMap(scala.jdk.CollectionConverters.MapHasAsJava(MediaWire.env).asJava)
    )
    val handlers = MediaWiring(Deps(loaded.config, ds, ds, plugins = plugins), wire, MediaWire.Thumbs())
    def report(r: Either[Api.Error, MediaImportReport]): String =
      r.fold(e => s"refused: ${e.message.sentence}\n", r => s"report: ${r.asJson.sortedKeys.spaces2}\n")
    def run(name: String, req: MediaImportRequest): Unit =
      val r = handlers.imports.importMedia(req)
      sb ++= s"## $name\n" + wire.take() + report(r)
    sb ++= s"providers: ${handlers.imports.providers.asJson.sortedKeys.spaces2}\n"
    val builtins = List(
      ("wikidata", Some("pleiades_570491"), None),
      ("wikidata", None, Some("fall_of_x")),
      ("depicts", None, Some("battle_of_kadesh")),
      ("met", Some("pleiades_570491"), None),
      ("cleveland", Some("geonames_146669"), None),
      ("aic", Some("geonames_146669"), None),
      ("loc", Some("pleiades_570491"), None),
      ("pleiades", Some("pleiades_757208100"), None),
      ("opencontext", Some("pleiades_697725"), None),
      ("smithsonian", Some("geonames_357994"), None),
      ("europeana", Some("pleiades_570491"), None)
    ).map((p, place, event) =>
      (
        com.histmersion.api.media.MediaItem.Provider(p),
        place.map(Place.Id(_)),
        event.map(Event.Id(_))
      )
    )
    builtins.foreach((p, place, event) =>
      run(s"$p dry run", MediaImportRequest(p, dryRun = MediaImportRequest.DryRun(true), place = place, event = event))
    )
    builtins.foreach((p, place, event) =>
      run(
        s"$p ${place.map(_.value).orElse(event.map(_.value)).getOrElse("")}",
        MediaImportRequest(p, dryRun = MediaImportRequest.DryRun(false), place = place, event = event)
      )
    )
    run(
      "zq-own dry run",
      MediaImportRequest(
        com.histmersion.api.media.MediaItem.Provider("zq-own"),
        dryRun = MediaImportRequest.DryRun(true),
        limit = Some(MediaImportRequest.Limit(20))
      )
    )
    run(
      "zq-own a place",
      MediaImportRequest(
        com.histmersion.api.media.MediaItem.Provider("zq-own"),
        dryRun = MediaImportRequest.DryRun(false),
        place = Some(Place.Id("pleiades_697725"))
      )
    )
    run(
      "zq-own an event",
      MediaImportRequest(
        com.histmersion.api.media.MediaItem.Provider("zq-own"),
        dryRun = MediaImportRequest.DryRun(false),
        event = Some(Event.Id("fall_of_x"))
      )
    )
    run(
      "zq-own again, refresh",
      MediaImportRequest(
        com.histmersion.api.media.MediaItem.Provider("zq-own"),
        dryRun = MediaImportRequest.DryRun(false),
        place = Some(Place.Id("pleiades_697725")),
        refresh = MediaImportRequest.Refresh(true)
      )
    )
    run(
      "zq-search dry run",
      MediaImportRequest(
        com.histmersion.api.media.MediaItem.Provider("zq-search"),
        dryRun = MediaImportRequest.DryRun(true),
        limit = Some(MediaImportRequest.Limit(3))
      )
    )
    run(
      "zq-search a place",
      MediaImportRequest(
        com.histmersion.api.media.MediaItem.Provider("zq-search"),
        dryRun = MediaImportRequest.DryRun(false),
        place = Some(Place.Id("pleiades_570491"))
      )
    )
    run("an unknown collection", MediaImportRequest(com.histmersion.api.media.MediaItem.Provider("zq-nothing")))
    run(
      "many, not as a job",
      MediaImportRequest(
        com.histmersion.api.media.MediaItem.Provider("zq-own"),
        dryRun = MediaImportRequest.DryRun(false)
      )
    )
    exec(ds, "INSERT INTO sources (citekey, title) VALUES ('zqol2000_a', 'A'), ('zqol2000_b', 'B')")
    exec(
      ds,
      """INSERT INTO source_identifiers (source_uid, scheme, value)
         SELECT uid, 'isbn', CASE citekey WHEN 'zqol2000_a' THEN '9780000000001' ELSE '9780000000009' END
         FROM sources WHERE citekey IN ('zqol2000_a', 'zqol2000_b')"""
    )
    def covers(name: String, req: MediaCoversRequest): Unit =
      val r = handlers.covers.covers(req)
      sb ++= s"## covers: $name\n" + wire.take() +
        r.fold(e => s"refused: ${e.message.sentence}\n", r => s"report: ${r.asJson.sortedKeys.spaces2}\n")
    covers(
      "dry run",
      MediaCoversRequest(
        dryRun = RepairAcquire.RepairRequest.DryRun(true),
        illustrations = MediaCoversRequest.Illustrations(false)
      )
    )
    covers(
      "one with a cover",
      MediaCoversRequest(
        dryRun = RepairAcquire.RepairRequest.DryRun(false),
        citekey = Some(Citekey("zqol2000_a")),
        illustrations = MediaCoversRequest.Illustrations(false)
      )
    )
    covers(
      "one without",
      MediaCoversRequest(
        dryRun = RepairAcquire.RepairRequest.DryRun(false),
        citekey = Some(Citekey("zqol2000_b")),
        illustrations = MediaCoversRequest.Illustrations(false)
      )
    )
    covers(
      "an ISBN the request names",
      MediaCoversRequest(
        dryRun = RepairAcquire.RepairRequest.DryRun(false),
        citekey = Some(Citekey("zqmt2020_x")),
        illustrations = MediaCoversRequest.Illustrations(false),
        isbn = Some(MediaCoversRequest.Isbn("9780000000003"))
      )
    )
    sb ++= "## stored\n" + MediaWire.stored(ds)
    sb.result()

  /** The Calibre covers as `media covers` reads them on the host, from a fixture library, through a server. */
  private def calibreCovers(url: String, ds: DataSource): String =
    val lib = CalibreFixture.create(tmp.resolve("calibre-library"))
    exec(ds, "INSERT INTO sources (citekey, title, calibre_id) VALUES ('zqcal1998_hittite', 'Hittite Myths', '5')")
    exec(ds, "INSERT INTO sources (citekey, title, calibre_id) VALUES ('zqcal1956_linear', 'Linear B', '2')")
    exec(ds, "INSERT INTO sources (citekey, title, calibre_id) VALUES ('zqcal2019_tin', 'Tin', '1')")
    exec(ds, "INSERT INTO sources (citekey, title, calibre_id) VALUES ('zqcal0000_gone', 'Gone', '99')")
    val cal = Calibre(
      CalibreCliConfig(
        CalibreCliConfig.LibraryDir(lib.dir.toString),
        CalibreCliConfig.Calibredb(lib.calibredb.toString),
        CalibreCliConfig.Sqlite(lib.sqlite3.toString),
        CalibreCliConfig.OriginalsDir("")
      )
    )
    val client = Client(Client.ServerUrl(url))
    def step(
        name: String,
        only: Option[Citekey],
        refresh: MediaImportRequest.Refresh,
        dry: MediaImportRequest.DryRun
    ): String =
      MediaCovers
        .calibreStep(client, cal, only, refresh, dry, None)
        .fold(
          e => s"## $name\nrefused: ${e.message.sentence}\n",
          (r, asks) =>
            s"## $name\nreport: ${r.asJson.sortedKeys.spaces2}\nasks: ${Json.fromValues(asks.map((c, i) => Json.arr(Json.fromString(c.value), Json.fromString(i.value)))).noSpaces}\n"
        )
    step("dry run", None, refresh = MediaImportRequest.Refresh(false), dry = MediaImportRequest.DryRun(true)) +
      step(
        "every linked source",
        None,
        refresh = MediaImportRequest.Refresh(false),
        dry = MediaImportRequest.DryRun(false)
      ) +
      step(
        "again: nothing left",
        None,
        refresh = MediaImportRequest.Refresh(false),
        dry = MediaImportRequest.DryRun(false)
      ) +
      step(
        "one, refreshed",
        Some(Citekey("zqcal2019_tin")),
        refresh = MediaImportRequest.Refresh(true),
        dry = MediaImportRequest.DryRun(false)
      ) +
      s"## the covers stored\n${get(s"$url/api/media?provider=calibre")}\n"

  test("imports: every first-party collection, a plugin's own pictures and a searched one, covers, as recorded"):
    assume(testDb.isDefined, "HISTMERSION_TEST_DB_URL not set")
    val imp = scratch("imp")
    MediaWire.seed(imp)
    val text = imports(imp)
    val cal = scratch("cal")
    val calibre = withServer("cal")(url => calibreCovers(url, cal))
    val n = names(List("imp" -> imp, "cal" -> cal))
    compare("imports.txt", normalise(text, n) + s"# rows\n${rows(imp, n)}")
    compare("covers.txt", answers(calibre, n) + s"# rows\n${rows(cal, n)}")

  // ── exchange ───────────────────────────────────────────────────────────────────────────────

  private val MediaEntries = Set("entities/assets.ndjson", "entities/asset-links.ndjson", "entities/tombstones.ndjson")

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
        if MediaEntries(path) || MediaEntries(path.replaceFirst("^[^/]+/", "")) then
          val lines = String(data, UTF_8).linesIterator.toList
          out ++= s"== $path (${lines.size} lines)\n"
          lines.foreach(l => out ++= json(l, names).linesIterator.map(_.trim).mkString(" ") + "\n")
        else if path.contains("/blobs/") || path.startsWith("blobs/") then
          out ++= s"== $path $size ${BundleFormat.sha256Hex(data)}\n"
        else out ++= s"== $path\n"
        loop()
    loop()
    out.toString

  private def applyJob(url: String, job: String, left: Int = 600): String =
    val j = get(s"$url/api/merge/apply/$job")
    if j.contains("\"done\"") || j.contains("\"failed\"") || left == 0 then j
    else { Thread.sleep(100); applyJob(url, job, left - 1) }

  private def openIssues(plan: String): List[(String, List[String])] =
    RawJson
      .parse(plan)
      .toOption
      .flatMap(_.cursor.downField("issues").focus)
      .flatMap(_.asArray)
      .getOrElse(Vector.empty)
      .toList
      .flatMap { i =>
        val c = i.cursor
        for
          id <- c.downField("id").as[String].toOption
          if c.downField("condition").focus.forall(_.isNull)
          if c.downField("decision").focus.forall(_.isNull) || c.downField("decidedBy").as[String].contains("default")
        yield (id, c.downField("options").as[List[String]].getOrElse(Nil))
      }

  private def decide(options: List[String]): String =
    if options.contains("keep-right") then "keep-right"
    else options.filterNot(Set("map", "rename-right", "rename-left", "file")).headOption.getOrElse("keep-right")

  private def issueNamesOf(plan: String): Map[String, String] =
    RawJson
      .parse(plan)
      .toOption
      .flatMap(_.cursor.downField("issues").focus)
      .flatMap(_.asArray)
      .getOrElse(Vector.empty)
      .toList
      .flatMap { i =>
        val c = i.cursor
        for
          id <- c.downField("id").as[String].toOption
          kind <- c.downField("kind").as[String].toOption
          label = c
            .downField("right")
            .downField("label")
            .as[String]
            .toOption
            .orElse(c.downField("left").downField("label").as[String].toOption)
            .getOrElse("")
          title = c.downField("title").as[String].getOrElse("")
        yield id -> s"${id.take(1)}-issue:$kind:$label:${title.take(40)}"
      }
      .toMap

  private def mergeOf(url: String, bundle: Array[Byte]): (List[String], String, String) =
    val up = ok(postBytes(s"$url/api/bundle/upload", bundle))
    val sha = field(up, "sha256")
    @scala.annotation.tailrec
    def rounds(
        decided: List[(String, String)],
        plans: List[String],
        left: Int
    ): (List[(String, String)], List[String]) =
      val body =
        s"""{"resolve":[${decided.map((i, d) => s"""{"issue":"$i","decision":"$d"}""").mkString(",")}]}"""
      val p = ok(post(s"$url/api/merge/plan", s"""{"bundle":"$sha","resolutions":$body}"""))
      val open = openIssues(p).filterNot((id, _) => decided.exists(_._1 == id))
      if open.isEmpty || left == 0 then (decided, plans :+ p)
      else rounds(decided ++ open.map((id, o) => id -> decide(o)), plans :+ p, left - 1)
    val (decided, plans) = rounds(Nil, Nil, 4)
    val resolutions =
      s"""{"resolve":[${decided.map((i, d) => s"""{"issue":"$i","decision":"$d"}""").mkString(",")}]}"""
    val hash = field(plans.last, "planHash")
    val job = ok(post(s"$url/api/merge/apply", s"""{"bundle":"$sha","planHash":"$hash","resolutions":$resolutions}"""))
    (plans, applyJob(url, field(job, "job")), get(s"$url/api/merges/ledger?limit=1000"))

  /** Both sides' changes after the shared history: a figure's caption on both, a figure added on both sides under one
    * name (other bytes) and the same bytes under two names, an asset link added on each, one deleted here, a figure
    * removed by a new conversion there.
    */
  private def diverge(srcUrl: String, src: DataSource, homeUrl: String): String =
    val log = StringBuilder()
    def step(name: String, answer: String): String =
      log ++= s"== $name\n$answer\n"
      answer
    val figD = image(30, 30, 5, "png")
    val figE = image(30, 30, 6, "png")
    val _ = step(
      "src: caption",
      send("PATCH", s"$srcUrl/api/sources/zqfig2001_tell/assets/fig-a.png", """{"caption":"The plan, revised"}""")
    )
    val _ = step(
      "home: caption",
      send("PATCH", s"$homeUrl/api/sources/zqfig2001_tell/assets/fig-a.png", """{"caption":"The plan (home)"}""")
    )
    val _ = step(
      "src: a figure",
      post(s"$srcUrl/api/sources/zqfig2001_tell/assets", Json.arr(asset("fig-d.png", "image/png", figD)).noSpaces)
    )
    val _ = step(
      "home: the same name, other bytes",
      post(s"$homeUrl/api/sources/zqfig2001_tell/assets", Json.arr(asset("fig-d.png", "image/png", figE)).noSpaces)
    )
    val _ = step(
      "src: a figure under a name",
      post(s"$srcUrl/api/sources/zqfig2001_tell/assets", Json.arr(asset("fig-e.png", "image/png", figE)).noSpaces)
    )
    val _ = step(
      "src: a link",
      post(
        s"$srcUrl/api/asset-links",
        """{"citekey":"zqfig2001_tell","assetName":"fig-d.png","relation":"depicts","targetType":"place","targetId":"zq_cove"}"""
      )
    )
    val _ = step(
      "home: a link",
      post(
        s"$homeUrl/api/asset-links",
        """{"citekey":"zqfig2001_tell","assetName":"fig-c.jpg","relation":"depicts","targetType":"place","targetId":"zq_tell"}"""
      )
    )
    val _ = step("src: a link deleted", send("DELETE", s"$srcUrl/api/asset-links/1", ""))
    exec(src, "UPDATE sources SET updated_at = now() WHERE citekey = 'zqfig2001_tell'")
    log.result()

  test("exchange: media's bundle entries, an import into an empty collection, a merge apply and an increment"):
    assume(testDb.isDefined, "HISTMERSION_TEST_DB_URL not set")
    val src = scratch("xsrc")
    val home = scratch("xhome")
    val copy = scratch("xcopy")
    val all = List("xsrc" -> src, "xhome" -> home, "xcopy" -> copy)
    val shared = withServer("xsrc") { url =>
      val _ = craft(url, src)
      bytes(s"$url/api/bundle")
    }
    val imported = withServer("xhome")(url => postBytes(s"$url/api/bundle/import", shared))
    withServer("xcopy")(url => ok(postBytes(s"$url/api/bundle/import", shared)): Unit)
    val since = connect(src)(
      sql"SELECT to_char(now() AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS.US\"Z\"')".query[String].run().head
    )
    Thread.sleep(20)
    val written = withServer("xhome")(homeUrl => withServer("xsrc")(srcUrl => diverge(srcUrl, src, homeUrl)))
    val (whole, part) = withServer("xsrc")(url => (bytes(s"$url/api/bundle"), bytes(s"$url/api/bundle?since=$since")))
    val (plans, applied, ledger) = withServer("xhome")(url => mergeOf(url, whole))
    val incremental = withServer("xcopy")(url => postBytes(s"$url/api/bundle/import", part))
    val n = plans.flatMap(issueNamesOf).toMap ++ names(all)
    def j(text: String) = exchanged(json(text, n), n)
    def a(text: String) = exchanged(answers(text, n), n)
    compare(
      "exchange.txt",
      s"# the shared bundle's media entries\n${exchanged(bundleText(shared, n), n)}" +
        s"# imported into an empty collection\n${a(imported)}# its rows\n${rows(home, n)}" +
        s"# both sides' changes\n${a(written)}" +
        s"# the bundle's media entries\n${exchanged(bundleText(whole, n), n)}" +
        plans.zipWithIndex.map((p, i) => s"# the plan, round ${i + 1}\n${j(p)}\n").mkString +
        s"# applied\n${a(applied)}# the ledger\n${a(ledger)}# home's rows\n${rows(home, n)}" +
        s"# the changes since the shared history imported into a copy\n${a(incremental)}" +
        s"# the copy's rows\n${rows(copy, n)}"
    )

final case class ZqOracleConfig() derives hearth.kindlings.sconfigderivation.ConfigReader
