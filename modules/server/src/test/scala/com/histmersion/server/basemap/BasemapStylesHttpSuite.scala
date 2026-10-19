package com.histmersion.server.basemap

import com.histmersion.core.*
import com.histmersion.server.{ConfigSource, Main, OfflineConverter}
import org.ekrich.config.ConfigFactory
import ox.supervised

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.file.{Files, Path}
import java.nio.file.attribute.FileTime

/** The terrain styles over HTTP on a running server started from a config file: the built-in styles,
  * a style file put in place and changed (picked up with no restart), a broken one (the built-in styles
  * stay; health and about say why), a config reload that names another file, the check of a file and
  * the example. Skipped without HISTMERSION_TEST_DB_URL. */
class BasemapStylesHttpSuite extends com.histmersion.db.DbSuite:
  private val testDb                = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private val http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()
  private def get(url: String): (Int, String) =
    val r = http.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString())
    (r.statusCode, r.body)
  private def post(url: String, body: String): (Int, String) =
    val r = http.send(HttpRequest.newBuilder(URI.create(url)).POST(HttpRequest.BodyPublishers.ofString(body)).build(),
      HttpResponse.BodyHandlers.ofString())
    (r.statusCode, r.body)
  private def styles(url: String): TerrainStyles =
    val (c, b) = get(s"$url/api/basemap/styles")
    assertEquals(c, 200, b)
    io.circe.parser.decode[TerrainStyles](b).fold(e => fail(s"$e in $b"), identity)

  private def write(p: Path, text: String, bump: Int): Unit =
    Files.writeString(p, text)
    Files.setLastModifiedTime(p, FileTime.fromMillis(System.currentTimeMillis() + bump * 1000L))

  test("styles served, a style file picked up and refused while the server runs, a reload naming another file, check and example"):
    val dir    = Files.createTempDirectory("styles-http")
    val conf   = dir.resolve("histmersion.conf")
    val styleF = dir.resolve("basemap-style.conf")
    val spool  = Files.createDirectories(dir.resolve("spool"))
    def config(extra: String) = Files.writeString(conf,
      s"""histmersion {
         |  db.url = "${testDb.get}"
         |  converter.spool-dir = "$spool"
         |  $extra
         |}
         |""".stripMargin)
    config("")
    val source = ConfigSource(() => AppConfig.loadWithUserFile(ConfigFactory.empty, Some(conf)))
    val l      = source.read().fold(e => fail(e), identity)
    supervised {
      val b   = Main.server(l.config, "127.0.0.1", 0, OfflineConverter(), source = Some((l, source))).start()
      val url = s"http://127.0.0.1:${b.port}"
      try
        val s0 = styles(url)
        assertEquals(s0.styles.map(_.id), List("standard", "muted", "dark", "terrain", "terrain-vegetation", "relief", "parchment"))
        assertEquals((s0.source, s0.file, s0.warnings), (TerrainStyles.BuiltIn, Some(styleF.toString), Nil))

        // a file put in place: the next read serves it
        write(styleF, """styles = [ { id = "dusk", extends = "terrain", name = "Dusk", background = "#0b1018" } ]""", 0)
        val s1 = styles(url)
        assertEquals(s1.styles.last.name, "Dusk")
        assertNotEquals(s1.version, s0.version)

        // broken: the built-in styles, and the reason in health and about
        write(styleF, """styles = [ { id = "dusk", extends = "terrain", name = "Dusk", background = "#0b1018", relief { azimuth = 400 } } ]""", 2)
        val s2 = styles(url)
        assertEquals((s2.source, s2.styles.size), (TerrainStyles.BuiltIn, 7))
        assert(s2.warnings.exists(_.contains("styles[0].relief.azimuth: 400.0 is outside 0…360")), s2.warnings.toString)
        val (hc, hb) = get(s"$url/api/health")
        assertEquals(hc, 200, hb)
        val h = io.circe.parser.decode[Health](hb).fold(e => fail(e.toString), identity)
        assertEquals(h.status, "ok", "a refused style file degrades nothing")
        assert(h.warnings.exists(_.contains("basemap-style.conf is not used")), h.warnings.toString)
        val (_, ab) = get(s"$url/api/about")
        assert(ab.contains("basemap-style.conf is not used"), ab)

        // a config reload naming another file (and another language) applies with no restart
        val other = dir.resolve("night.json")
        Files.writeString(other, """{"default": "night", "styles": [{"id": "night", "extends": "relief", "name": "Night"}]}""")
        config("basemap.style = \"night.json\"\n  basemap.labels.language = \"local\"")
        val (rc, rb) = post(s"$url/api/config/reload", "")
        assertEquals(rc, 200, rb)
        assert(rb.contains("\"published\":true"), rb)
        val s3 = styles(url)
        assertEquals((s3.default, s3.language, s3.file), ("night", "local", Some(other.toString)))
        assertEquals(s3.warnings, Nil)

        // check (not used) and the example
        val (cc, cb) = post(s"$url/api/basemap/styles/check", """styles = [ { id = "x", name = "X" } ]""")
        assertEquals(cc, 200, cb)
        val chk = io.circe.parser.decode[TerrainStyleCheck](cb).fold(e => fail(e.toString), identity)
        assert(!chk.ok && chk.errors.exists(_.contains("style 'x' needs a background colour")), chk.toString)
        assertEquals(styles(url).version, s3.version, "a check changes nothing")
        val (ec, eb) = get(s"$url/api/basemap/styles/example")
        assertEquals(ec, 200)
        assert(eb.contains("id = \"parchment\""), eb.take(200))
      finally b.stop()
    }
