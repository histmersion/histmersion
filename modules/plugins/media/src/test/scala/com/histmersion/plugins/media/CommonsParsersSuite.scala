package com.histmersion.plugins.media

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.config.AppConfig
import com.histmersion.plugin.*
import com.histmersion.pluginhost.{FirstPartySettings, HostLicences}
import hearth.kindlings.sconfigderivation.ConfigReader
import io.circe.Json
import io.circe.parser.parse as json
import org.ekrich.config.ConfigFactory

import java.nio.charset.StandardCharsets.UTF_8

/** Wikimedia Commons as every Commons-backed collection reads it (#1446): file titles, the `imageinfo` URL and answer,
  * and each file's licence, author and credit line as Commons states them ([[CommonsFiles]]) — a file whose terms
  * cannot be used withheld with the reason — and the Wikidata statement → role map. No network: Commons' answer is
  * built from the `extmetadata` each case names.
  */
class CommonsParsersSuite extends munit.FunSuite:

  /** Histmersion's own `media.*` defaults, as the host supplies them to a media plugin. */
  private lazy val media: MediaSettings =
    val loaded = AppConfig.loadWithUserText(ConfigFactory.empty, "").fold(e => fail(e), identity)
    val supplied = FirstPartySettings.fallback(com.histmersion.plugin.Plugin.Info.Id("media-wikidata"), loaded.merged)
    summon[ConfigReader[MediaPlugin.Config]].from(supplied.root).fold(e => fail(e.toString), _.media)

  // ── titles and URLs ──────────────────────────────────────────────────────────────────────────

  test("a Wikidata file name becomes its Commons title"):
    assertEquals(Commons.fileTitle(com.histmersion.plugins.media.Commons.File.Title("lions_Gate.jpg")), "File:Lions Gate.jpg")
    assertEquals(Commons.fileTitle(com.histmersion.plugins.media.Commons.File.Title("File:Mycenae  ruins.jpg")), "File:Mycenae ruins.jpg")
    assertEquals(Commons.fileTitle(com.histmersion.plugins.media.Commons.File.Title("  ")), "")

  test("the imageinfo URL asks for the rendition width and, with meta, only the licence fields"):
    val u = Commons.imageInfoUrl(com.histmersion.plugins.media.Commons.Settings.ApiUrl("https://c/w/api.php"), List(com.histmersion.plugins.media.Commons.File.Title("File:A b.jpg")), com.histmersion.plugins.media.Commons.File.Width(1280), com.histmersion.plugins.media.Commons.Settings.Maxlag(5), meta = com.histmersion.plugins.media.Commons.ImageInfoUrlMeta(true))
    assert(u.contains("iiurlwidth=1280") && u.contains("iiextmetadatafilter=LicenseShortName%7CLicense%7C"), u)
    assert(u.contains("titles=File%3AA+b.jpg") && u.endsWith("&maxlag=5"), u)
    val t = Commons.imageInfoUrl(com.histmersion.plugins.media.Commons.Settings.ApiUrl("https://c/w/api.php"), List(com.histmersion.plugins.media.Commons.File.Title("File:A.jpg")), com.histmersion.plugins.media.Commons.File.Width(330), com.histmersion.plugins.media.Commons.Settings.Maxlag(0), meta = com.histmersion.plugins.media.Commons.ImageInfoUrlMeta(false))
    assert(!t.contains("extmetadata") && !t.contains("maxlag"), t)

  private val imageinfo = json(
    """{"query":{
    "normalized":[{"from":"File:a.jpg","to":"File:A.jpg"}],
    "pages":[
      {"title":"File:Gone.jpg","missing":true},
      {"title":"File:A.jpg","imageinfo":[{"size":5,"width":3000,"height":2000,
        "thumburl":"https://up/t/1280px-A.jpg?utm_source=x","thumbwidth":1280,"thumbheight":853,
        "url":"https://up/A.jpg?utm_source=x","descriptionurl":"https://commons/wiki/File:A.jpg","mime":"image/jpeg",
        "extmetadata":{"License":{"value":"cc-by-sa-4.0"},"Artist":{"value":"<b>Ann</b>"},"Flag":{"value":true}}}]}]}}"""
  ).toOption.get

  test("imageinfo: files by the title asked (normalised too), missing files as Left, URLs untracked"):
    val m = Commons.imageInfo(imageinfo).toOption.get
    assertEquals(m(com.histmersion.plugins.media.Commons.File.Title("File:Gone.jpg")), Left("missing on Commons"))
    val a = m(com.histmersion.plugins.media.Commons.File.Title("File:A.jpg")).toOption.get
    assertEquals(m(com.histmersion.plugins.media.Commons.File.Title("File:a.jpg")), m(com.histmersion.plugins.media.Commons.File.Title("File:A.jpg")), "the asked title maps to the canonical one")
    assertEquals(a.originalUrl, "https://up/A.jpg")
    assertEquals(a.renditionUrl, Some("https://up/t/1280px-A.jpg"))
    assertEquals((a.renditionWidth, a.renditionHeight, a.width), (Some(1280), Some(853), Some(3000)))
    assertEquals(a.meta.get(com.histmersion.plugins.media.Commons.File.MetaKey("License")), Some("cc-by-sa-4.0"))
    assertEquals(a.meta.get(com.histmersion.plugins.media.Commons.File.MetaKey("Flag")), Some("true"))

  test("imageinfo: an API error is a Left for the whole answer"):
    val err = json("""{"error":{"code":"maxlag","info":"Waiting: 6 seconds lagged."}}""").toOption.get
    assertEquals(Commons.imageInfo(err).left.toOption.map(_.takeWhile(_ != ':')), Some("maxlag"))

  // ── rights ───────────────────────────────────────────────────────────────────────────────────

  /** Commons' answer for one file, `File:X.jpg`, with this `extmetadata`; every request recorded. */
  final private class OneFile(meta: Map[String, String]) extends Plugin.Http:
    val asked = scala.collection.mutable.ListBuffer.empty[String]
    def get(url: String, headers: Map[String, String]) =
      asked += url
      val ext = Json.fromFields(meta.toList.map((k, v) => k -> Json.obj("value" -> Json.fromString(v))))
      val body =
        s"""{"query":{"pages":[{"title":"File:X.jpg","imageinfo":[{"size":100,"width":2000,"height":1000,
           "thumburl":"https://up/t/1280px-X.jpg","thumbwidth":1280,"thumbheight":640,
           "url":"https://up/X.jpg","descriptionurl":"https://commons/wiki/File:X.jpg","mime":"image/jpeg",
           "extmetadata":${ext.noSpaces}}]}]}}"""
      Right(body.getBytes(UTF_8))

  private def file(meta: (String, String)*): MediaItem =
    val http = OneFile(meta.toMap)
    val (items, failed) = Commons.Files(http, media, HostLicences).items(List(com.histmersion.plugins.media.Commons.File.Title("File:X.jpg")))
    assertEquals(failed, Nil)
    assertEquals(http.asked.size, 1, "one imageinfo call for the batch")
    items(com.histmersion.plugins.media.Commons.File.Title("File:X.jpg"))

  private def refusal(meta: (String, String)*): Option[String] = file(meta*).withheld

  test("rights: licence, author and Commons' credit form read from extmetadata; the files as Commons gives them"):
    val it = file(
      "License" -> "cc-by-sa-3.0",
      "LicenseShortName" -> "CC BY-SA 3.0",
      "Artist" -> """<a href="//commons.wikimedia.org/wiki/User:D">David Monniaux</a>""",
      "LicenseUrl" -> "//creativecommons.org/licenses/by-sa/3.0/",
      "AttributionRequired" -> "true"
    )
    assertEquals(it.withheld, None)
    val r = it.rights.fold(fail(_), identity)
    assertEquals(r.licence, Licence("CC-BY-SA-3.0"))
    assertEquals(HostLicences.readName(r.licence.value).family, "cc-by-sa")
    assertEquals(r.credit, "David Monniaux, CC BY-SA 3.0, via Wikimedia Commons")
    assertEquals(r.licenceUrl, Some("https://creativecommons.org/licenses/by-sa/3.0/"))
    assertEquals(r.author, Some("David Monniaux"))
    assertEquals(
      (it.pageUrl, it.imageUrl, it.originalUrl),
      ("https://commons/wiki/File:X.jpg", Some("https://up/t/1280px-X.jpg"), Some("https://up/X.jpg"))
    )
    assertEquals(it.rendition, Some(MediaRendition(com.histmersion.plugin.MediaRendition.Mime("image/jpeg"), Some(com.histmersion.plugin.MediaRendition.Width(1280)), Some(com.histmersion.plugin.MediaRendition.Height(640)))))

  test("rights: a custom Attribution wins over Artist"):
    val it = file("License" -> "cc-by-4.0", "Artist" -> "user42", "Attribution" -> "Photo: Jane Roe")
    assertEquals(it.rights.map(_.credit), Right("Photo: Jane Roe, CC-BY-4.0, via Wikimedia Commons"))

  test("rights: public domain with no author credits Commons alone"):
    assertEquals(
      file("License" -> "pd", "LicenseShortName" -> "Public domain").rights.map(_.credit),
      Right("Public domain, via Wikimedia Commons")
    )

  test("rights refused (withheld): non-free, unreadable, all rights reserved, attribution with no author"):
    assertEquals(
      refusal("License" -> "cc-by-4.0", "Artist" -> "x", "NonFree" -> "true"),
      Some("marked non-free on Commons")
    )
    assertEquals(refusal("Artist" -> "x"), Some("no licence stated"))
    assert(
      refusal("LicenseShortName" -> "Some custom terms", "Artist" -> "x").exists(_.contains("not recognised")),
      "an unreadable licence"
    )
    assertEquals(refusal("LicenseShortName" -> "Copyrighted", "Artist" -> "x"), Some("all rights reserved"))
    assertEquals(refusal("License" -> "cc-by-4.0"), Some("no author to credit"))
    assertEquals(refusal("License" -> "cc0", "AttributionRequired" -> "true"), Some("no author to credit"))

  test("a file Commons does not answer for, or does not have, is stated with the reason it cannot be used"):
    val http = new Plugin.Http:
      def get(url: String, headers: Map[String, String]) =
        Right("""{"query":{"pages":[{"title":"File:Gone.jpg","missing":true}]}}""".getBytes(UTF_8))
    val (items, failed) = Commons.Files(http, media, HostLicences).items(List(com.histmersion.plugins.media.Commons.File.Title("File:Gone.jpg"), com.histmersion.plugins.media.Commons.File.Title("File:Other.jpg")))
    assertEquals(failed, Nil)
    assertEquals(items(com.histmersion.plugins.media.Commons.File.Title("File:Gone.jpg")).rights, Left("missing on Commons"))
    assertEquals(items(com.histmersion.plugins.media.Commons.File.Title("File:Other.jpg")).rights, Left("no answer from Commons"))
    val down = new Plugin.Http { def get(url: String, headers: Map[String, String]) = Left("HTTP 503") }
    val (none, lines) = Commons.Files(down, media, HostLicences).items(List(com.histmersion.plugins.media.Commons.File.Title("File:A.jpg")))
    assertEquals(none(com.histmersion.plugins.media.Commons.File.Title("File:A.jpg")).rights, Left("no answer from Commons"))
    assertEquals(lines, List("Commons File:A.jpg…: HTTP 503"))

  // ── roles ────────────────────────────────────────────────────────────────────────────────────

  test("the statement → role map parses, dropping malformed and repeated entries"):
    assertEquals(
      WikidataSettings.roles(com.histmersion.plugins.media.WikidataSettings.Spec("P18:image, P242:locator-map,bogus,P1:nope,P18:plan,P3311:plan")),
      List("P18" -> "image", "P242" -> "locator-map", "P3311" -> "plan")
    )
    assertEquals(WikidataSettings.roles(com.histmersion.plugins.media.WikidataSettings.Spec("")), Nil)
    assert(WikidataSettings.PictureRoles.forall(MediaCollection.Roles), "every picture role is one the host stores")
