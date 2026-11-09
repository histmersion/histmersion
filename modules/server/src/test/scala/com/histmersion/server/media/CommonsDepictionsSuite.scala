package com.histmersion
package server.media

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.domain.media.imports.OwnImport
import com.histmersion.domain.media.imports.Target

import com.histmersion.domain.media.assets.ProviderAsset
import com.histmersion.pluginhost.{HttpAnswer, HttpTransport, Registries}

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.domain.media.MediaRole
import com.histmersion.api.media.{MediaCount, MediaProvider}
import com.histmersion.domain.media.imports.MediaImporter
import com.histmersion.domain.media.imports.ImportService

/** Pictures that depict events (#1446) against a FAKE Wikidata and Commons — the `media-depicts` plugin's collection,
  * as the host builds it, run by the host's import of a collection of its targets' own pictures: P1299 works' images
  * first, then the P180 search's files, capped per event, judged by the Commons licence like every Commons picture,
  * linked as `depiction`, and the checks recorded under the depictions' own name.
  */
class CommonsDepictionsSuite extends munit.FunSuite:

  private val kadesh = Target(
    Target.TargetType("event"),
    Target.Id("battle_of_kadesh"),
    Target.Qid("Q203729"),
    Target.Label("Battle of Kadesh")
  )

  /** Commons' imageinfo answers every title asked with a CC BY-SA file (the No-author one refused). */
  private def info(titles: List[String], width: String): String =
    val pages = titles.zipWithIndex.map { (t, i) =>
      val meta = if t.contains("Dictionnaire") then
        """{"LicenseShortName":{"value":"CC BY 4.0"},"License":{"value":"cc-by-4.0"}}"""
      else
        """{"Artist":{"value":"Zqmd"},"LicenseShortName":{"value":"CC BY-SA 4.0"},"License":{"value":"cc-by-sa-4.0"}}"""
      s"""{"pageid":${i + 1},"ns":6,"title":${io.circe.Json
          .fromString(t)
          .noSpaces},"imageinfo":[{"size":100,"width":10,"height":10,
         "thumburl":"https://upload.example/$i-$width.jpg","thumbwidth":$width,"thumbheight":$width,
         "url":"https://upload.example/$i.jpg","descriptionurl":"https://commons.wikimedia.org/wiki/F$i","mime":"image/jpeg",
         "extmetadata":$meta}]}"""
    }
    s"""{"query":{"pages":[${pages.mkString(",")}]}}"""

  private def param(url: String, name: String): Option[String] =
    url.split("[?&]").toList.map(_.split("=", 2)).collectFirst {
      case Array(k, v) if k == name => java.net.URLDecoder.decode(v, "UTF-8")
    }

  private def provider(): FakeMediaHttp = FakeMediaHttp { url =>
    if url.contains("action=wbgetentities") && param(url, "ids").exists(_.contains("Q203729")) then
      FakeMediaHttp.json(FakeMediaHttp.fixture("wd-depicted-by.json"))
    else if url.contains("action=wbgetentities") then
      // the works: Q5002 has an image, Q5001 none
      FakeMediaHttp.json("""{"entities":{"Q5002":{"id":"Q5002","claims":{"P18":[{"mainsnak":{"snaktype":"value",
        "property":"P18","datavalue":{"value":"Kadesh relief.jpg","type":"string"}},"rank":"normal"}]}},
        "Q5001":{"id":"Q5001","claims":{}}}}""")
    else if url.contains("list=search") then FakeMediaHttp.json(FakeMediaHttp.fixture("commons-depicts.json"))
    else if url.contains("prop=imageinfo") then
      FakeMediaHttp.json(
        info(param(url, "titles").toList.flatMap(_.split('|')), param(url, "iiurlwidth").getOrElse("0"))
      )
    else if url.startsWith("https://upload.example/") then
      FakeMediaHttp.bytes(Array[Byte](-1, -40, -1) ++ url.getBytes("UTF-8"), "image/jpeg")
    else Left(s"unexpected $url")
  }

  private val cfg = FakeMediaHttp.cfg

  test("dry run: the P1299 work's image first, then P180 files, capped per event; the licence read from Commons"):
    val http = provider()
    val (polite, _) = FakeMediaHttp.polite(http, cfg)
    val store = CollStore(Map.empty, Map.empty)
    val r = OwnImport(
      MediaImporter.ImporterName("depicts"),
      FakeMediaHttp.collection(Registries.MediaCollectionsKey("depicts")),
      polite,
      cfg
    ).run(
      List(kadesh),
      dryRun = MediaImporter.DryRun(true),
      refresh = ImportService.Refresh(false),
      store
    )
    assertEquals(r.provider, MediaProvider.CommonsDepicts)
    assertEquals(r.statements, 3, "media.depicts.per-event")
    assertEquals(r.byRole, List(MediaCount(MediaCount.Key(MediaRole.Depiction), MediaCount.N(3))))
    assertEquals(
      r.samples.map(_.basis),
      List("P1299 depicted by Q203729", "P180 depicts Q203729", "P180 depicts Q203729")
    )
    assertEquals(r.samples.head.key, "File:Kadesh relief.jpg")
    assert(store.saved.isEmpty && store.checks.isEmpty)
    assert(http.requests.map(_._1).exists(_.contains("srsearch=haswbstatement%3AP180%3DQ203729")))

  test("real run: stored as Commons pictures, linked as depictions, checked under the depictions' name"):
    val (polite, _) = FakeMediaHttp.polite(provider(), cfg)
    val store = CollStore(Map.empty, Map.empty)
    val r = OwnImport(
      MediaImporter.ImporterName("depicts"),
      FakeMediaHttp.collection(Registries.MediaCollectionsKey("depicts")),
      polite,
      cfg
    ).run(
      List(kadesh),
      dryRun = MediaImporter.DryRun(false),
      refresh = ImportService.Refresh(false),
      store
    )
    assertEquals((r.stored, r.linked), (3, 3))
    assert(store.saved.values.forall(_._2.provider == ProviderAsset.Provider(MediaProvider.Commons)))
    assertEquals(
      store.links.toList.map(l => (l._3, l._4, l._5)),
      List(
        ("battle_of_kadesh", MediaRole.Depiction, 0),
        ("battle_of_kadesh", MediaRole.Depiction, 1),
        ("battle_of_kadesh", MediaRole.Depiction, 2)
      )
    )
    assertEquals(store.checks.toMap, Map((MediaProvider.CommonsDepicts, "battle_of_kadesh") -> 3))

  test("a failed search leaves the event unrecorded, so it is asked again"):
    val http = FakeMediaHttp { url =>
      if url.contains("list=search") then
        Right(HttpAnswer(HttpAnswer.Code(404), Map.empty, HttpAnswer.Body(Array.emptyByteArray)))
      else provider().get(HttpTransport.Url(url), Map.empty).left.map(_.problem.value)
    }
    val (polite, _) = FakeMediaHttp.polite(http, cfg)
    val store = CollStore(Map.empty, Map.empty)
    val r = OwnImport(
      MediaImporter.ImporterName("depicts"),
      FakeMediaHttp.collection(Registries.MediaCollectionsKey("depicts")),
      polite,
      cfg
    ).run(
      List(kadesh),
      dryRun = MediaImporter.DryRun(false),
      refresh = ImportService.Refresh(false),
      store
    )
    assert(r.failed.exists(_.value.contains("Commons search for battle_of_kadesh")), r.failed.toString)
    assert(store.checks.isEmpty)
