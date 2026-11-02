package com.histmersion.domain.catalogue.places

import com.histmersion.server.ServerPlugins

import com.histmersion.config.AppConfig
import com.histmersion.db.TestDb
import com.histmersion.plugin.PluginHttp
import com.histmersion.pluginhost.{HttpTransport, PluginGazetteers}
import com.histmersion.server.{Bootstrap, Deps, ServerWiring}

import com.augustnagro.magnum.*

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import javax.sql.DataSource
import com.histmersion.api.catalogue.Place

/** What the network gazetteers store: each fixture imported through the real runner (download from a loopback server,
  * transform, batch upsert, `finding_places` rebuilt) into a scratch database of its own, then its `places`,
  * `place_names` and `place_relations` rows (sorted; without the columns a clock, a fresh id or the instance fills);
  * then the names `places fetch` and `init.fetch-sources` take, and an unknown one's error. Against a real PostGIS
  * (HISTMERSION_TEST_DB_URL; skipped otherwise). The transcript is `traffic/gazetteers-stored.txt`, compared byte for
  * byte (`HISTMERSION_UPDATE_TRAFFIC=1` rewrites it).
  */
class GazetteerStoreSuite extends com.histmersion.db.DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private val loop = GazetteerLoopback()
  private lazy val cfg = AppConfig.load()
  private lazy val scratch = ScratchDatabases("gazetteers", cfg.db.copy(url = testDb.get))
  private lazy val ds = scratch("store")

  override def afterSuite(): Unit =
    loop.stop()
    if testDb.isDefined then scratch.dropAll()

  private def fixture(name: String): Array[Byte] =
    Files.readAllBytes(Path.of(s"modules/plugins/gazetteers/src/test/resources/gazetteers/$name"))

  private def zipped(name: String): Array[Byte] =
    GazetteerLoopback.zip(List("__MACOSX/._x" -> "mac".getBytes(UTF_8), "cliopatria.geojson" -> fixture(name)))

  // ── the code under test: the plugins' sources run by the host's runner ──────────────────────

  private lazy val sources = ServerPlugins.offline.registries.gazetteerSources

  /** The host's gazetteer client, its requests sent to the loopback server instead of the source's host. */
  private def http: PluginHttp =
    val wire = PluginGazetteers.transport(cfg.places)
    PluginGazetteers.http(new HttpTransport:
      def get(url: String, headers: Map[String, String]) = wire.get(loop.local(url), headers))

  private def importOnce(name: String): Either[String, Place.ImportResult] =
    val db = com.histmersion.util.database.Transactor(ds)
    ImportRunner.fetchAndImport(
      sources(name),
      http,
      PlaceService(db),
      PlaceGeometry(db),
      dryRun = false,
      cfg.places.pointToleranceKm
    )

  // ── the transcript ──────────────────────────────────────────────────────────────────────────

  private def steady(json: List[String]): String =
    json.map { j =>
      io.circe.parser
        .parse(j)
        .fold(e => fail(e.toString), identity)
        .mapObject(_.filterKeys(k => !k.endsWith("_at") && k != "uid" && k != "origin"))
        .noSpaces + "\n"
    }.mkString

  private def stored: String =
    connect(ds):
      val places = sql"SELECT to_jsonb(t)::text FROM places t ORDER BY id".query[String].run()
      val names =
        sql"SELECT to_jsonb(t)::text FROM place_names t ORDER BY place_id, name, lang, import_source, import_ref"
          .query[String]
          .run()
      val relations =
        sql"SELECT to_jsonb(t)::text FROM place_relations t ORDER BY child_id, parent_id, kind".query[String].run()
      "places:\n" + steady(places.toList) + "place_names:\n" + steady(names.toList) + "place_relations:\n" +
        steady(relations.toList)

  private def transcript: String =
    val sb = StringBuilder()
    def run(name: String, served: String, body: Array[Byte]): Unit =
      loop.answerWith(200, body)
      sb ++= s"## import $name: $served\nresult: ${importOnce(name)}\n"
      val _ = loop.take()
    run("cliopatria", "cliopatria.geojson, zipped", zipped("cliopatria.geojson"))
    run("awmc", "awmc.geojson", fixture("awmc.geojson"))
    run("chronontology", "chronontology.json", fixture("chronontology.json"))
    sb ++= "## stored after the three imports\n" + stored
    run("cliopatria", "cliopatria-more.geojson, zipped (over the first)", zipped("cliopatria-more.geojson"))
    run("awmc", "awmc-more.geojson", fixture("awmc-more.geojson"))
    run("chronontology", "chronontology-more.json", fixture("chronontology-more.json"))
    run("cliopatria", "cliopatria.geojson again, zipped", zipped("cliopatria.geojson"))
    sb ++= "## stored after the second imports\n" + stored
    // the names the server takes, and what it says to one it does not know
    val h = ServerWiring(Deps(cfg, ds))
    sb ++= s"## names\ninit.fetch-sources: ${Bootstrap.sources.mkString(", ")}\n"
    sb ++= s"places fetch nowhere: ${h.gazetteer.fetch(com.histmersion.domain.Caller.Local, "nowhere", dryRun = true)}\n"
    sb.result()

  private val transcriptFile = Path.of("modules/server/src/test/resources/traffic/gazetteers-stored.txt")

  test("the gazetteers store exactly the recorded places, names and relations"):
    val now = transcript
    if sys.env.get("HISTMERSION_UPDATE_TRAFFIC").contains("1") then
      Files.createDirectories(transcriptFile.getParent)
      Files.writeString(transcriptFile, now, UTF_8)
    val recorded = Files.readString(transcriptFile, UTF_8)
    if recorded != now then
      val (a, b) = (recorded.linesIterator.toVector, now.linesIterator.toVector)
      val at = a.indices.find(i => b.lift(i) != Some(a(i))).getOrElse(a.size)
      fail(
        s"the stored gazetteers differ from $transcriptFile at line ${at + 1}:\n  recorded: " +
          s"${a.lift(at).getOrElse("<end>")}\n  now:      ${b.lift(at).getOrElse("<end>")}"
      )
