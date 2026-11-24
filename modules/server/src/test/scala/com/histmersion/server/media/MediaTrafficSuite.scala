package com.histmersion
package server.media
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.RawJson.*

import com.histmersion.util.problem.Text.{sentence, sentences}
import com.histmersion.util.database.Transactor

import com.histmersion.server.MediaWiring

import com.histmersion.server.ServerPlugins

import com.histmersion.config.{DbConfig, LoadedConfig}
import com.histmersion.db.TestDb
import com.histmersion.util.jobs.{Job, JobRepo}
import com.histmersion.server.Deps

import java.nio.charset.StandardCharsets.UTF_8
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.media.{MediaImportReport, MediaImportRequest, MediaItem}
import com.histmersion.api.Api
import com.histmersion.db.DbSuite
import com.histmersion.domain.library.acquisition.CoreBudget
import com.histmersion.api.media.ImportJobKinds
import com.histmersion.api.catalogue.Place
import com.histmersion.api.catalogue.Event
import com.histmersion.util.database.TestCreators.given

/** The recorded traffic of the media imports: every request each of the ten first-party collections sends through
  * `media import` (dry runs over every place and event the map holds, single-target imports, a refresh, a job), on the
  * wire below the polite client — URL and every header, in order — with each report and, at the end, every stored
  * picture, link, check and Commons category. The answers are the fixtures the media suites use; nothing reaches the
  * network. The database is a scratch copy of its own. The transcript is `traffic/media.txt`, compared byte for byte
  * (`HISTMERSION_UPDATE_TRAFFIC=1` rewrites it): moving the collections into plugins changes no request, and stores and
  * refuses the same records with the same reasons and roles.
  */
class MediaTrafficSuite extends DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private val Transcript = "traffic/media.txt"

  private def load(more: String): LoadedConfig =
    MediaWire.load(more).fold(e => fail(e.sentences.mkString("; ")), identity)

  private lazy val scratch = ScratchDatabases("mediatraffic", load("").config.db.copy(url = DbConfig.Url(testDb.get)))
  private lazy val ds = scratch("media")

  override def afterSuite(): Unit = if testDb.isDefined then scratch.dropAll()

  private def seed(): Unit = MediaWire.seed(ds)

  private def stored: String = MediaWire.stored(ds)

  private def transcript: String =
    val sb = StringBuilder()
    val loaded = load("")
    val wire = MediaWire.Wire()
    val plugins = ServerPlugins.load(
      loaded,
      transport = _ => wire,
      core = CoreBudget(() => java.time.Instant.now(java.time.Clock.systemUTC()))
    )
    val handlers = MediaWiring(Deps(loaded.config, ds, ds, plugins = plugins), wire, MediaWire.Thumbs())

    def report(r: Either[Api.Error, MediaImportReport]): String =
      r.fold(e => s"refused: ${e.problems.sentences.mkString("; ")}\n", r => s"report: ${r.asJson.noSpaces}\n")

    def run(name: String, req: MediaImportRequest): Unit =
      val r = handlers.imports.importMedia(req)
      sb ++= s"## $name\n" + wire.take() + report(r)

    @annotation.tailrec
    def until(id: JobInfo.Id, n: Int): JobInfo =
      val j = Transactor(ds).read(JobRepo.get(Job.Id.assume(id.value))).getOrElse(fail(s"no job $id"))
      if j.isTerminal || n <= 0 then j else { Thread.sleep(50); until(id, n - 1) }

    def job(name: MediaItem.Provider, req: MediaImportRequest): Unit =
      handlers.imports.importJob(req) match
        case Left(e)  => sb ++= s"## job $name\n" + wire.take() + s"refused: ${e.problems.sentences.mkString("; ")}\n"
        case Right(j) =>
          val end = until(j.id, 600)
          sb ++= s"## job $name\n" + wire.take() +
            s"job: ${end.status} ${end.detail.flatMap(ImportJobKinds.Media.of).getOrElse("")} — ${end.progress.getOrElse("")}\n"

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
    ).map(MediaItem.Provider(_))
    all.foreach(p =>
      run(
        s"$p (dry run)",
        MediaImportRequest(p, dryRun = MediaImportRequest.DryRun(true), limit = Some(MediaImportRequest.Limit(20)))
      )
    )

    run(
      "wikidata, one place",
      MediaImportRequest(
        MediaItem.Provider("wikidata"),
        dryRun = MediaImportRequest.DryRun(false),
        place = Some(Place.Id("pleiades_570491"))
      )
    )
    run(
      "wikidata, one event",
      MediaImportRequest(
        MediaItem.Provider("wikidata"),
        dryRun = MediaImportRequest.DryRun(false),
        event = Some(Event.Id("fall_of_x"))
      )
    )
    run(
      "depicts, one event",
      MediaImportRequest(
        MediaItem.Provider("depicts"),
        dryRun = MediaImportRequest.DryRun(false),
        event = Some(Event.Id("battle_of_kadesh"))
      )
    )
    run(
      "met, one place",
      MediaImportRequest(
        MediaItem.Provider("met"),
        dryRun = MediaImportRequest.DryRun(false),
        place = Some(Place.Id("pleiades_570491"))
      )
    )
    run(
      "cleveland, one place",
      MediaImportRequest(
        MediaItem.Provider("cleveland"),
        dryRun = MediaImportRequest.DryRun(false),
        place = Some(Place.Id("geonames_146669"))
      )
    )
    run(
      "aic, one place",
      MediaImportRequest(
        MediaItem.Provider("aic"),
        dryRun = MediaImportRequest.DryRun(false),
        place = Some(Place.Id("geonames_146669"))
      )
    )
    run(
      "loc, one place",
      MediaImportRequest(
        MediaItem.Provider("loc"),
        dryRun = MediaImportRequest.DryRun(false),
        place = Some(Place.Id("pleiades_570491"))
      )
    )
    run(
      "pleiades, one place",
      MediaImportRequest(
        MediaItem.Provider("pleiades"),
        dryRun = MediaImportRequest.DryRun(false),
        place = Some(Place.Id("pleiades_757208100"))
      )
    )
    run(
      "opencontext, one place",
      MediaImportRequest(
        MediaItem.Provider("opencontext"),
        dryRun = MediaImportRequest.DryRun(false),
        place = Some(Place.Id("pleiades_697725"))
      )
    )
    run(
      "smithsonian, one place",
      MediaImportRequest(
        MediaItem.Provider("smithsonian"),
        dryRun = MediaImportRequest.DryRun(false),
        place = Some(Place.Id("geonames_357994"))
      )
    )
    run(
      "europeana, one place",
      MediaImportRequest(
        MediaItem.Provider("europeana"),
        dryRun = MediaImportRequest.DryRun(false),
        place = Some(Place.Id("pleiades_570491"))
      )
    )

    run(
      "wikidata, one place again, --refresh",
      MediaImportRequest(
        MediaItem.Provider("wikidata"),
        dryRun = MediaImportRequest.DryRun(false),
        place = Some(Place.Id("pleiades_570491")),
        refresh = MediaImportRequest.Refresh(true)
      )
    )
    run(
      "met, one place again, --refresh",
      MediaImportRequest(
        MediaItem.Provider("met"),
        dryRun = MediaImportRequest.DryRun(false),
        place = Some(Place.Id("pleiades_570491")),
        refresh = MediaImportRequest.Refresh(true)
      )
    )

    all.foreach(p => job(p, MediaImportRequest(p, dryRun = MediaImportRequest.DryRun(false))))

    // the names an import is refused under
    run("an unknown provider", MediaImportRequest(MediaItem.Provider("rijks")))
    run(
      "an alias",
      MediaImportRequest(
        MediaItem.Provider("commons"),
        dryRun = MediaImportRequest.DryRun(true),
        limit = Some(MediaImportRequest.Limit(2))
      )
    )
    run(
      "a collection asked about an event",
      MediaImportRequest(MediaItem.Provider("met"), event = Some(Event.Id("fall_of_x")))
    )
    val off = load(
      """histmersion.plugins.media-cleveland.enabled = false
        |histmersion.media.met.enabled = false
        |histmersion.plugins.media-europeana.api-key = null
        |histmersion.plugins.media-smithsonian.enabled = false
        |""".stripMargin
    )
    val offHandlers =
      MediaWiring(
        Deps(
          off.config,
          ds,
          ds,
          plugins = ServerPlugins.load(
            off,
            transport = _ => wire,
            core = CoreBudget(() => java.time.Instant.now(java.time.Clock.systemUTC()))
          )
        ),
        wire,
        MediaWire.Thumbs()
      )
    List("cleveland", "met", "europeana", "smithsonian").map(MediaItem.Provider(_)).foreach { p =>
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
