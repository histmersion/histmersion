package com.histmersion.server.media

import com.histmersion.server.MediaWiring

import com.histmersion.server.ServerPlugins

import com.histmersion.api.*
import com.histmersion.config.LoadedConfig
import com.histmersion.db.TestDb
import com.histmersion.util.jobs.JobRepo
import com.histmersion.server.Deps
import io.circe.syntax.*

import java.nio.charset.StandardCharsets.UTF_8

/** The recorded traffic of the media imports: every request each of the ten first-party collections sends through
  * `media import` (dry runs over every place and event the map holds, single-target imports, a refresh, a job), on the
  * wire below the polite client — URL and every header, in order — with each report and, at the end, every stored
  * picture, link, check and Commons category. The answers are the fixtures the media suites use; nothing reaches the
  * network. The database is a scratch copy of its own. The transcript is `traffic/media.txt`, compared byte for byte
  * (`HISTMERSION_UPDATE_TRAFFIC=1` rewrites it): moving the collections into plugins changes no request, and stores and
  * refuses the same records with the same reasons and roles.
  */
class MediaTrafficSuite extends com.histmersion.db.DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private val Transcript = "traffic/media.txt"

  private def load(more: String): LoadedConfig = MediaWire.load(more).fold(e => fail(e), identity)

  private lazy val scratch = ScratchDatabases("mediatraffic", load("").config.db.copy(url = testDb.get))
  private lazy val ds = scratch("media")

  override def afterSuite(): Unit = if testDb.isDefined then scratch.dropAll()

  private def seed(): Unit = MediaWire.seed(ds)

  private def stored: String = MediaWire.stored(ds)

  private def transcript: String =
    val sb = StringBuilder()
    val loaded = load("")
    val wire = MediaWire.Wire()
    val plugins = ServerPlugins.load(loaded, transport = _ => wire)
    val handlers = MediaWiring(Deps(loaded.config, ds, ds, plugins = plugins), wire, MediaWire.Thumbs())

    def report(r: Either[ApiError, MediaImportReport]): String =
      r.fold(e => s"refused: ${e.message}\n", r => s"report: ${r.asJson.noSpaces}\n")

    def run(name: String, req: MediaImportRequest): Unit =
      val r = handlers.imports.importMedia(req)
      sb ++= s"## $name\n" + wire.take() + report(r)

    @annotation.tailrec
    def until(id: String, n: Int): JobInfo =
      val j = JobRepo.get(ds, id).getOrElse(fail(s"no job $id"))
      if j.isTerminal || n <= 0 then j else { Thread.sleep(50); until(id, n - 1) }

    def job(name: String, req: MediaImportRequest): Unit =
      handlers.imports.importJob(req) match
        case Left(e)  => sb ++= s"## job $name\n" + wire.take() + s"refused: ${e.message}\n"
        case Right(j) =>
          val end = until(j.id, 600)
          sb ++= s"## job $name\n" + wire.take() +
            s"job: ${end.status} ${end.detail.flatMap(com.histmersion.api.media.imports.ImportJobKinds.Media.of).getOrElse("")} — ${end.progress.getOrElse("")}\n"

    sb ++= s"providers: ${handlers.imports.providers.asJson.noSpaces}\n"
    sb ++= wire.take()

    val all = List(
      "wikidata",
      "depicts",
      "met",
      "cleveland",
      "aic",
      "loc",
      "pleiades",
      "opencontext",
      "smithsonian",
      "europeana"
    )
    all.foreach(p => run(s"$p (dry run)", MediaImportRequest(p, dryRun = true, limit = Some(20))))

    run("wikidata, one place", MediaImportRequest("wikidata", dryRun = false, place = Some("pleiades_570491")))
    run("wikidata, one event", MediaImportRequest("wikidata", dryRun = false, event = Some("fall_of_x")))
    run("depicts, one event", MediaImportRequest("depicts", dryRun = false, event = Some("battle_of_kadesh")))
    run("met, one place", MediaImportRequest("met", dryRun = false, place = Some("pleiades_570491")))
    run("cleveland, one place", MediaImportRequest("cleveland", dryRun = false, place = Some("geonames_146669")))
    run("aic, one place", MediaImportRequest("aic", dryRun = false, place = Some("geonames_146669")))
    run("loc, one place", MediaImportRequest("loc", dryRun = false, place = Some("pleiades_570491")))
    run("pleiades, one place", MediaImportRequest("pleiades", dryRun = false, place = Some("pleiades_757208100")))
    run("opencontext, one place", MediaImportRequest("opencontext", dryRun = false, place = Some("pleiades_697725")))
    run("smithsonian, one place", MediaImportRequest("smithsonian", dryRun = false, place = Some("geonames_357994")))
    run("europeana, one place", MediaImportRequest("europeana", dryRun = false, place = Some("pleiades_570491")))

    run(
      "wikidata, one place again, --refresh",
      MediaImportRequest("wikidata", dryRun = false, place = Some("pleiades_570491"), refresh = true)
    )
    run(
      "met, one place again, --refresh",
      MediaImportRequest("met", dryRun = false, place = Some("pleiades_570491"), refresh = true)
    )

    all.foreach(p => job(p, MediaImportRequest(p, dryRun = false)))

    // the names an import is refused under
    run("an unknown provider", MediaImportRequest("rijks"))
    run("an alias", MediaImportRequest("commons", dryRun = true, limit = Some(2)))
    run("a collection asked about an event", MediaImportRequest("met", event = Some("fall_of_x")))
    val off = load(
      """histmersion.plugins.media-cleveland.enabled = false
        |histmersion.media.met.enabled = false
        |histmersion.plugins.media-europeana.api-key = null
        |histmersion.plugins.media-smithsonian.enabled = false
        |""".stripMargin
    )
    val offHandlers =
      MediaWiring(
        Deps(off.config, ds, ds, plugins = ServerPlugins.load(off, transport = _ => wire)),
        wire,
        MediaWire.Thumbs()
      )
    List("cleveland", "met", "europeana", "smithsonian").foreach { p =>
      sb ++= s"## $p, switched off\n" + report(offHandlers.imports.importMedia(MediaImportRequest(p))) + wire.take()
    }
    sb ++= s"providers, some switched off: ${offHandlers.imports.providers.asJson.noSpaces}\n"

    sb ++= "## stored\n" + stored
    sb.result()

  test("the ten collections' requests, reports and stored pictures are the recorded ones"):
    seed()
    val got = transcript
    val path = java.nio.file.Path.of("modules/server/src/test/resources").resolve(Transcript)
    if sys.env.get("HISTMERSION_UPDATE_TRAFFIC").contains("1") then
      java.nio.file.Files.createDirectories(path.getParent)
      java.nio.file.Files.writeString(path, got, UTF_8)
    val want = java.nio.file.Files.readString(path, UTF_8)
    if got != want then
      val (g, w) = (got.linesIterator.toVector, want.linesIterator.toVector)
      val at = g.zipAll(w, "<end>", "<end>").indexWhere((a, b) => a != b)
      fail(
        s"the media traffic differs at line ${at + 1}:\n  got:  ${g.lift(at).getOrElse("<end>")}\n" +
          s"  want: ${w.lift(at).getOrElse("<end>")}"
      )
