package com.histmersion
package server.media
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.problem.{Legacy, Problem, Problems}

import com.histmersion.domain.catalogue.places.PlaceFixtures

import com.augustnagro.magnum.*
import com.histmersion.config.{AppConfig, LoadedConfig}
import com.histmersion.domain.CoreQuota
import com.histmersion.plugin.Plugin
import com.histmersion.pluginhost.{HttpAnswer, HttpTransport}
import com.histmersion.util.ocr.ConverterClient
import org.ekrich.config.ConfigFactory

import java.nio.charset.StandardCharsets.UTF_8
import javax.sql.DataSource
import scala.jdk.CollectionConverters.*
import com.histmersion.api.catalogue.{Period, Place, Year}
import com.histmersion.api.ocr.ConvertedDoc
import com.histmersion.util.storage.Blob
import com.histmersion.util.ocr.Pixels

/** The answers of every first-party media collection's provider, the settings that switch them all on at no pause, the
  * places and events a map holds for them, and what an import stored — the fixtures the media traffic and the media
  * oracle run the imports over. Nothing reaches the network: [[route]] answers every request.
  */
object MediaWire:

  def fx(name: String): String = FakeMediaHttp.fixture(name)
  def jpeg(tag: String): Array[Byte] = Array[Byte](-1, -40, -1) ++ tag.getBytes(UTF_8)
  private def json(s: String): Either[String, HttpAnswer] = FakeMediaHttp.json(s)

  private def param(url: String, name: String): Option[String] =
    url.split("[?&]").toList.map(_.split("=", 2)).collectFirst {
      case Array(k, v) if k == name => java.net.URLDecoder.decode(v, "UTF-8")
    }

  /** The fixture's imageinfo titles: a batch naming one of them is answered by the fixture, any other by [[info]]. */
  private lazy val fixtureTitles: Set[String] =
    "\"title\":\"(File:[^\"]+)\"".r.findAllMatchIn(fx("imageinfo.json")).map(_.group(1)).toSet

  /** Commons' imageinfo for any titles: CC BY-SA, but a title naming "NC" is CC BY-NC and one naming "anon" has no
    * author.
    */
  private def info(titles: List[String], width: String): String =
    val pages = titles.zipWithIndex.map { (t, i) =>
      val meta =
        if t.contains("NC") then
          """{"Artist":{"value":"Zqmt"},"LicenseShortName":{"value":"CC BY-NC 4.0"},"License":{"value":"cc-by-nc-4.0"}}"""
        else if t.toLowerCase.contains("anon") then
          """{"LicenseShortName":{"value":"CC BY 4.0"},"License":{"value":"cc-by-4.0"}}"""
        else
          """{"Artist":{"value":"Zqmt"},"LicenseShortName":{"value":"CC BY-SA 4.0"},"License":{"value":"cc-by-sa-4.0"}}"""
      s"""{"pageid":${i + 1},"ns":6,"title":${t.asJson.noSpaces},"imageinfo":[{"size":100,"width":10,"height":10,
         "thumburl":"https://upload.example/g$i-$width.jpg","thumbwidth":$width,"thumbheight":$width,
         "url":"https://upload.example/g$i.jpg","descriptionurl":"https://commons.wikimedia.org/wiki/G$i","mime":"image/jpeg",
         "extmetadata":$meta}]}"""
    }
    s"""{"query":{"pages":[${pages.mkString(",")}]}}"""

  private val works =
    """{"entities":{"Q5002":{"id":"Q5002","claims":{"P18":[{"mainsnak":{"snaktype":"value",
      "property":"P18","datavalue":{"value":"Kadesh relief.jpg","type":"string"}},"rank":"normal"}]}},
      "Q5001":{"id":"Q5001","claims":{}}}}"""

  private def metObject(url: String): Either[String, HttpAnswer] =
    val id = url.reverse.takeWhile(_ != '/').reverse
    val obj = fx("met-object.json").replace("252448", id)
    id match
      case "252448" => json(obj)
      case "248284" => json(obj.replace("\"isPublicDomain\": true", "\"isPublicDomain\": false"))
      case "252447" => json(obj.replace("\"excavation\": \"Mycenae\"", "\"excavation\": \"Argos\""))
      case _        => Right(HttpAnswer(HttpAnswer.Code(404), Map.empty, HttpAnswer.Body(Array.emptyByteArray)))

  /** Every provider's answers, by the URL's host and path; anything else is a picture (its bytes name its URL). */
  def route(url: String): Either[String, HttpAnswer] =
    if url.contains("action=wbgetentities") then
      val ids = param(url, "ids").getOrElse("")
      if ids.contains("Q203729") && !ids.contains("Q132564") then json(fx("wd-depicted-by.json"))
      else if ids.contains("Q5002") || ids.contains("Q5001") then json(works)
      else json(fx("wbgetentities.json"))
    else if url.contains("list=search") then json(fx("commons-depicts.json"))
    else if url.contains("prop=imageinfo") then
      val titles = param(url, "titles").toList.flatMap(_.split('|'))
      val width = param(url, "iiurlwidth").getOrElse("0")
      if titles.exists(fixtureTitles) then
        json(if width == "1280" then fx("imageinfo.json") else fx("imageinfo.json").replace("1280px-", s"${width}px-"))
      else json(info(titles, width))
    else if url.contains("pleiades.stoa.org/places/") then json(fx("pleiades-place.json"))
    else if url.contains("/v1.1/search?") then json(fx("met-search.json"))
    else if url.contains("collectionapi.metmuseum.org") && url.contains("/v1/objects/") then metObject(url)
    else if url.contains("openaccess-api.clevelandart.org") then json(fx("cma-search.json"))
    else if url.contains("api.artic.edu") then json(fx("aic-search.json"))
    else if url.contains("www.loc.gov") && url.contains("/item/") then json(fx("loc-item.json"))
    else if url.contains("www.loc.gov") && url.contains("fo=json") then json(fx("loc-search.json"))
    else if url.contains("opencontext.org/query/") then json(fx("oc-search.json"))
    else if url.contains("opencontext.org/media/") then json(fx("oc-media.json"))
    else if url.contains("api.si.edu") then json(fx("si-search.json"))
    else if url.contains("api.europeana.eu/record/v2/search.json") then json(fx("eu-search.json"))
    else if url.contains("api.europeana.eu/record/v2/") then json(fx("eu-record.json"))
    else FakeMediaHttp.bytes(jpeg(url), "image/jpeg")

  /** Answers from `answer` ([[route]] by default); every request written down, in order (a job runs on a thread of its
    * own).
    */
  final class Wire(answer: String => Either[String, HttpAnswer] = route) extends HttpTransport:
    private val out = StringBuilder()
    def get(
        url: HttpTransport.Url,
        headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue]
    ): Either[Problem, HttpAnswer] = synchronized {
      val a = answer(url.value)
      out ++= s"GET $url\n"
      headers.toList.sortBy(_._1.value.toLowerCase(java.util.Locale.ROOT)).foreach((k, v) => out ++= s"  $k: $v\n")
      out ++= s"  -> ${a.fold(e => s"error $e", a => s"${a.code} ${a.body.value.length} bytes")}\n"
      a.left.map(m => Problem.Unclassified(Legacy.Text(m)))
    }
    def take(): String = synchronized {
      val s = out.result()
      out.clear()
      s
    }

  /** Thumbnails made here: one per call, named after the file. */
  final class Thumbs extends ConverterClient:
    def convert(
        file: Blob,
        filename: ConverterClient.Filename,
        quality: ConverterClient.HighQuality
    ): Either[Problem, ConvertedDoc] =
      Left(Problem.Unclassified(Legacy.Text("not used")))
    def health: Either[Problem, ConverterClient.HealthSummary] = Right(
      ConverterClient.HealthSummary("ok")
    )
    override def thumbnail(
        bytes: ConverterClient.DocumentBytes,
        filename: ConverterClient.Filename,
        maxPx: Pixels
    ): Either[Problem, ConverterClient.Thumb] =
      Right(
        ConverterClient.Thumb(
          ConverterClient.Thumb.Bytes(jpeg(s"thumb:$filename:$maxPx")),
          ConverterClient.Thumb.Mime("image/jpeg"),
          ConverterClient.Thumb.Width(maxPx.value),
          ConverterClient.Thumb.Height(maxPx.value * 3 / 4)
        )
      )

  val env: Map[String, String] = Map("ZQMT_SI_KEY" -> "si-fixture-key", "ZQMT_EU_KEY" -> "eu-fixture-key")

  /** Every collection at no pause, the keyed two switched on with keys from [[env]]. */
  val text: String =
    """histmersion.media {
      |  request-interval-ms = 0
      |  met { request-interval-ms = 0, refusal-backoff-seconds = "" }
      |  cleveland.request-interval-ms = 0
      |  aic.request-interval-ms = 0
      |  loc.request-interval-ms = 0
      |  pleiades.request-interval-ms = 0
      |  open-context.request-interval-ms = 0
      |  smithsonian.request-interval-ms = 0
      |  europeana.request-interval-ms = 0
      |  import-batch = 3
      |}
      |histmersion.plugins.media-smithsonian { enabled = true, api-key = "env:ZQMT_SI_KEY" }
      |histmersion.plugins.media-europeana { enabled = true, api-key = "env:ZQMT_EU_KEY" }
      |""".stripMargin

  def load(more: String): Either[Problems, LoadedConfig] =
    AppConfig.loadWithUserText(ConfigFactory.parseMap(env.asJava), text + more)

  private def place(
      id: Place.Id,
      label: Place.Label,
      kind: Place.KindValue,
      lon: Longitude,
      lat: Latitude,
      period: Option[Period]
  ) =
    Place.Input(
      Place(
        id,
        label,
        kind,
        Some(Place.Geometry(s"""{"type":"Point","coordinates":[${lon.value},${lat.value}]}""")),
        period
      ),
      List(Place.Name(Place.Name.Value(label.value)))
    )

  private def span(from: Long, to: Long) = Some(Period(Year(from), Year(to)))

  /** The places on the map (each holds a finding), their Wikidata and Pleiades links, and two events. */
  def seed(ds: DataSource): Unit =
    val _ = PlaceFixtures.upsertPlaces(
      ds,
      List(
        place(
          Place.Id("pleiades_570491"),
          Place.Label("Mycenae"),
          Place.KindValue(com.histmersion.domain.catalogue.Place.Kind.Site),
          Longitude(22.756),
          Latitude(37.73),
          span(-1600L, -1100L)
        ),
        place(
          Place.Id("geonames_390903"),
          Place.Label("Greece"),
          Place.KindValue(com.histmersion.domain.catalogue.Place.Kind.Region),
          Longitude(22.0),
          Latitude(39.0),
          span(-3000L, -100L)
        ),
        place(
          Place.Id("geonames_146669"),
          Place.Label("Cyprus"),
          Place.KindValue(com.histmersion.domain.catalogue.Place.Kind.Region),
          Longitude(33.0),
          Latitude(35.0),
          span(-1200L, -300L)
        ),
        place(
          Place.Id("pleiades_697725"),
          Place.Label("Petra"),
          Place.KindValue(com.histmersion.domain.catalogue.Place.Kind.Site),
          Longitude(35.44),
          Latitude(30.33),
          span(-300L, 100L)
        ),
        place(
          Place.Id("geonames_248816"),
          Place.Label("Jordan"),
          Place.KindValue(com.histmersion.domain.catalogue.Place.Kind.Region),
          Longitude(36.0),
          Latitude(31.0),
          span(-3000L, 2000L)
        ),
        place(
          Place.Id("geonames_357994"),
          Place.Label("Egypt"),
          Place.KindValue(com.histmersion.domain.catalogue.Place.Kind.Region),
          Longitude(30.0),
          Latitude(26.0),
          span(-3100L, -30L)
        ),
        place(
          Place.Id("geonames_163843"),
          Place.Label("Syria"),
          Place.KindValue(com.histmersion.domain.catalogue.Place.Kind.Region),
          Longitude(38.0),
          Latitude(35.0),
          span(-3000L, 1500L)
        ),
        place(
          Place.Id("pleiades_757208100"),
          Place.Label("Zygouries"),
          Place.KindValue(com.histmersion.domain.catalogue.Place.Kind.Site),
          Longitude(22.85),
          Latitude(37.8),
          span(-3000L, -1100L)
        ),
        place(
          Place.Id("x_site"),
          Place.Label("X"),
          Place.KindValue(com.histmersion.domain.catalogue.Place.Kind.Site),
          Longitude(10.0),
          Latitude(10.0),
          None
        )
      )
    )
    connect(ds):
      val links = List(
        ("pleiades_570491", "Q132564"),
        ("pleiades_697725", "Q5788"),
        ("x_site", "Q999"),
        ("geonames_390903", "Q41"),
        ("geonames_146669", "Q229")
      )
      links.foreach((p, q) =>
        sql"""INSERT INTO place_links (place_id, scheme, external_id, relation, import_source, import_ref)
              VALUES ($p, 'wikidata', $q, 'exact_match', 'zqmt', 'zqmt')""".update.run()
      )
      sql"INSERT INTO sources (citekey, title) VALUES ('zqmt2020_x', 'Zqmt')".update.run()
      val ids = List(
        "pleiades_570491",
        "geonames_390903",
        "geonames_146669",
        "pleiades_697725",
        "geonames_248816",
        "geonames_357994",
        "geonames_163843",
        "pleiades_757208100",
        "x_site"
      )
      ids.zipWithIndex.foreach { (p, i) =>
        val f = s"zqmt2020_x#${i + 1}"
        sql"""INSERT INTO findings (id, citekey, claim, period)
              VALUES ($f, 'zqmt2020_x', 'a', int8range(-1500, -1200, '[]'))""".update.run()
        sql"INSERT INTO finding_places (finding_id, place_id) VALUES ($f, $p)".update.run()
      }
      sql"INSERT INTO events (id, label, wikidata) VALUES ('fall_of_x', 'Fall of X', 'Q404')".update.run()
      sql"INSERT INTO events (id, label, wikidata) VALUES ('battle_of_kadesh', 'Battle of Kadesh', 'Q203729')".update
        .run()
      ()

  /** Rows as stored, without the columns a run's clock or a fresh uid fills; pictures by their bytes' digest. */
  def stored(ds: DataSource): String =
    connect(ds):
      val assets =
        sql"""SELECT jsonb_build_object('provider', provider, 'key', provider_key, 'name', name, 'mime', mime,
              'bytes', length(bytes), 'sha256', sha256, 'width', width, 'height', height, 'kind', kind,
              'license', license, 'licenseUrl', license_url, 'author', author, 'credit', credit,
              'sourceUrl', source_url, 'originalUrl', original_url, 'thumbMime', thumb_mime,
              'thumbBytes', length(thumb), 'thumbWidth', thumb_width, 'thumbHeight', thumb_height,
              'point', ST_AsText(geom), 'period', period::text)::text
            FROM assets WHERE provider IS NOT NULL ORDER BY provider, provider_key""".query[String].run()
      val links = sql"""SELECT jsonb_build_object('provider', a.provider, 'key', a.provider_key,
              'target', m.target_type || ':' || m.target_id, 'role', m.role, 'property', m.property, 'item', m.item,
              'rank', m.rank, 'basis', m.basis)::text
            FROM media_links m JOIN assets a ON a.uid = m.asset_uid
            ORDER BY a.provider, a.provider_key, m.target_type, m.target_id, m.role""".query[String].run()
      val checks = sql"""SELECT jsonb_build_object('provider', provider, 'target', target_type || ':' || target_id,
              'found', found, 'qid', qid)::text FROM media_checks ORDER BY provider, target_type, target_id"""
        .query[String]
        .run()
      val cats = sql"""SELECT jsonb_build_object('place', place_id, 'category', external_id, 'ref', import_ref)::text
            FROM place_links WHERE scheme = 'commons' ORDER BY place_id, external_id""".query[String].run()
      "assets:\n" + assets.map(_ + "\n").mkString + "media_links:\n" + links.map(_ + "\n").mkString +
        "media_checks:\n" + checks.map(_ + "\n").mkString + "commons categories:\n" + cats.map(_ + "\n").mkString
