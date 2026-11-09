package com.histmersion
package cli

/** The sources of each kind's new plugin, with the placeholders `PluginTemplate` fills in (`@Name@`, `@id@`,
  * `@Plugin@`, `@Contribution@`). Each one works as written: it compiles, its tests pass, and the server registers it
  * with its defaults alone.
  */
private[cli] object KindSources:

  def main(kind: PluginKind): String = kind match
    case PluginKind.SourceFetcher   => SourceFetcherMain
    case PluginKind.MediaCollection => MediaCollectionMain
    case PluginKind.EventCatalogue  => EventCatalogueMain
    case PluginKind.AiProvider      => AiProviderMain
    case PluginKind.SecretVault     => SecretVaultMain

  def test(kind: PluginKind): String = TestImports + (kind match
    case PluginKind.SourceFetcher   => SourceFetcherTest + SourceFetcherLive
    case PluginKind.MediaCollection => MediaCollectionTest + MediaCollectionLive
    case PluginKind.EventCatalogue  => EventCatalogueTest + EventCatalogueLive
    case PluginKind.AiProvider      => AiProviderTest
    case PluginKind.SecretVault     => SecretVaultTest)

  def defaults(kind: PluginKind): String = kind match
    case PluginKind.SourceFetcher   => SourceFetcherDefaults
    case PluginKind.MediaCollection => MediaCollectionDefaults
    case PluginKind.EventCatalogue  => EventCatalogueDefaults
    case PluginKind.AiProvider      => AiProviderDefaults
    case PluginKind.SecretVault     => SecretVaultDefaults

  /** The case classes that `derives ConfigReader` in the kind's sources, in the order they are written. */
  def configTypes(kind: PluginKind): List[String] = kind match
    case PluginKind.MediaCollection => List("ListedPicture", "@Name@Config")
    case PluginKind.EventCatalogue  => List("ListedEvent", "@Name@Config")
    case _                          => List("@Name@Config")

  private val Imports =
    """import com.histmersion.Newtype
      |import com.histmersion.plugin.*
      |import com.histmersion.util.problem.{Legacy, Problem}
      |import com.histmersion.plugin.Plugin.Config.given
      |import hearth.kindlings.sconfigderivation.ConfigReader
      |import io.github.iltotore.iron.constraint.any.Pure
      |
      |""".stripMargin

  private val TestImports =
    """import com.histmersion.plugin.*
      |import com.histmersion.plugin.testkit.*
      |import com.histmersion.pluginhost.{HttpAnswer, HttpTransport, Registries}
      |import com.histmersion.domain.CoreQuota
      |
      |import java.nio.file.Path
      |
      |""".stripMargin

  /** How a live suite starts: the mode from the environment (a replay of the case's recordings, the real site when
    * asked, else skipped) and the plugin loaded as the server loads it. munit reports a skipped test without its
    * reason, in a run that still passes, so the reason is printed too.
    */
  private val LiveStart =
    """    val mode = HttpMode.fromEnv(Path.of("recordings", "@case@"))
      |    mode.left.foreach(why => println(s"SKIPPED ${getClass.getSimpleName}: $why"))
      |    assume(mode.isRight, mode.left.map(_.legacyText).getOrElse(""))
      |    val harness = PluginHarness.load(Plugin.Info.Id("@id@"), config, http = mode.toOption.get).fold(e => fail(e.legacyText), identity)
      |""".stripMargin

  // ── source-fetcher ────────────────────────────────────────────────────────────────────────────

  private val SourceFetcherMain = Imports +
    """/** The settings under `histmersion.plugins.@id@`; their defaults are in `resources/META-INF/histmersion/plugins/@id@.conf`.
      |  *
      |  * @param baseUrl where the copies are: a work with the DOI `10.1234/abc` is `<base-url>/10.1234/abc.pdf` */
      |final case class @Name@Config(baseUrl: @Name@Config.BaseUrl) derives ConfigReader
      |
      |object @Name@Config:
      |  type BaseUrl = BaseUrl.T
      |  object BaseUrl extends Newtype[String, Pure]
      |
      |final class @Plugin@ extends Plugin:
      |  type Config = @Name@Config
      |  val info = Plugin.Info(Plugin.Info.Id("@id@"), Plugin.Info.Version("0.1.0"), Plugin.Info.SpiVersion(Spi.Version))
      |
      |  def register(config: @Name@Config, ctx: Plugin.Context): Either[Problem, Unit] =
      |    ctx.sourceFetchers.add(Registry.AddId("@id@"), @Contribution@(config.baseUrl, ctx.http))
      |
      |/** One step of the acquisition chain: a copy of a work by its DOI, from a mirror that serves PDFs. */
      |final class @Contribution@(baseUrl: @Name@Config.BaseUrl, http: Plugin.Http) extends SourceFetcher:
      |  val access = Access.Open
      |
      |  def fetch(work: WorkKey, ctx: Fetch.Context): Fetch =
      |    work.doi match
      |      case None => Fetch.Miss(Problem.Unclassified(Legacy.Text("no DOI")))
      |      case Some(doi) =>
      |        val url = Plugin.Http.Url(s"${baseUrl.value.stripSuffix("/")}/${doi.value}.pdf")
      |        http.get(url).fold(e => Fetch.Miss(e.problem), body => Fetch.Found(file(url, body)))
      |
      |  private def file(url: Plugin.Http.Url, body: Plugin.HttpResponse.Body): FetchedFile =
      |    FetchedFile(FetchedFile.Source("@id@"), Some(FetchedFile.Url(url.value)), FetchedFile.Bytes(body.value), FetchedFile.Extension("pdf"))
      |""".stripMargin

  private val SourceFetcherTest =
    """/** The plugin loaded and run as the server runs it (the Histmersion plugin testkit), offline: each
      |  * request is answered by a stub. */
      |class @Name@Suite extends munit.FunSuite:
      |  /** A body the server takes for a PDF: `%PDF` in its first KiB, and more than a KiB of it. */
      |  private val pdf  = HttpAnswer.Body(("%PDF-1.7\n" + " " * 2048 + "\n%%EOF\n").getBytes)
      |  private val page = HttpTransport.Url("https://mirror.example/10.1234/abc.pdf")
      |  private val id   = Plugin.Info.Id("@id@")
      |
      |  private def harness(pages: (HttpTransport.Url, StubAnswer)*): PluginHarness =
      |    PluginHarness.load(id, "base-url = \"https://mirror.example/\"", http = HttpMode.Stub(pages.toMap))
      |      .fold(e => fail(e.legacyText), identity)
      |
      |  test("the server registers the fetcher under the plugin's id"):
      |    assertEquals(harness().contributions.sourceFetchers, List(Registries.SourceFetchersKey("@id@")))
      |
      |  test("a work the mirror has is found"):
      |    val work = WorkKey(doi = Some(Doi("10.1234/abc")))
      |    harness(page -> StubAnswer.ok(pdf, CoreQuota.HeaderValue("application/pdf"))).fetch(work).outcome match
      |      case FetchOutcome.Found(_, file, _) =>
      |        assertEquals(file.url, Some(FetchedFile.Url(page.value)))
      |        assertEquals(file.extension, FetchedFile.Extension("pdf"))
      |      case other => fail(s"expected a copy, got $other")
      |
      |  test("a page that is not a PDF is no copy: the server checks every PDF"):
      |    val html = StubAnswer.text(HttpAnswer.Text("<html>Sign in</html>"), CoreQuota.HeaderValue("text/html"))
      |    val report = harness(page -> html).fetch(WorkKey(doi = Some(Doi("10.1234/abc"))))
      |    assert(report.outcome.isInstanceOf[FetchOutcome.Miss], report.outcome.toString)
      |
      |  test("a work the mirror lacks, or one without a DOI, is a miss"):
      |    assert(harness().fetch(WorkKey(doi = Some(Doi("10.1234/xyz")))).outcome.isInstanceOf[FetchOutcome.Miss])
      |    assert(harness().fetch(WorkKey(title = Some(WorkKey.Title("A title")))).outcome.isInstanceOf[FetchOutcome.Miss])
      |""".stripMargin

  /** The live check: settings and a work in, a copy (and its file) out. */
  private val SourceFetcherLive =
    """
      |/** Against the real source: does the plugin, set up as below, find and download a copy of a work the
      |  * source has? Replays `recordings/known-work` offline when it holds a recording; otherwise runs only with
      |  * HISTMERSION_PLUGIN_LIVE=1 (HISTMERSION_PLUGIN_HTTP=record keeps the answers for the replay). README.md. */
      |class @Name@LiveSuite extends munit.FunSuite:
      |  override val munitTimeout = scala.concurrent.duration.Duration(5, "min")
      |
      |  /** The settings, as under `histmersion.plugins.@id@` in histmersion.conf. */
      |  private val config = "base-url = \"https://mirror.example\""
      |  /** A work the source has: replace with a real one. */
      |  private val doi = Doi("10.1234/abc")
      |
      |  test("finds and downloads a copy of a known work"):
""".stripMargin + LiveStart.replace("@case@", "known-work") +
      """      |    // the work as an ingest asks for it: its title, authors and year looked up by the DOI
      |    val work   = WorkKey.fromDoi(doi, harness).getOrElse(WorkKey(doi = Some(doi)))
      |    val report = harness.fetch(work, saveTo = Some(Path.of("target", "fetched")))
      |    assert(report.outcome.isInstanceOf[FetchOutcome.Found],
      |      (report.requests ++ report.log.map(_.toString) :+ s"=> ${report.outcome}").mkString("\n"))
      |""".stripMargin

  private val SourceFetcherDefaults =
    """# The @id@ plugin's settings: keys relative to histmersion.plugins.@id@ in histmersion.conf.
      |# Every setting the plugin reads has its default here; `enabled` is the server's (off unless set).
      |enabled = false
      |base-url = "https://mirror.example"
      |""".stripMargin

  // ── media-collection ──────────────────────────────────────────────────────────────────────────

  private val MediaCollectionMain = Imports +
    """/** One picture of a hand-kept list.
      |  *
      |  * @param place   the name of the place on the map it shows
      |  * @param licence the licence as its provider states it (`CC-BY-4.0`, `Public domain`) */
      |final case class ListedPicture(key: MediaItem.Key, title: MediaItem.Title, place: MediaPlace.Label,
      |    pageUrl: MediaItem.PageUrl, imageUrl: MediaItem.ImageUrl, licence: Licence, credit: MediaRights.Credit)
      |    derives ConfigReader
      |
      |/** The settings under `histmersion.plugins.@id@`; their defaults are in `resources/META-INF/histmersion/plugins/@id@.conf`. */
      |final case class @Name@Config(pictures: List[ListedPicture]) derives ConfigReader
      |
      |final class @Plugin@ extends Plugin:
      |  type Config = @Name@Config
      |  val info = Plugin.Info(Plugin.Info.Id("@id@"), Plugin.Info.Version("0.1.0"), Plugin.Info.SpiVersion(Spi.Version))
      |
      |  def register(config: @Name@Config, ctx: Plugin.Context): Either[Problem, Unit] =
      |    ctx.mediaCollections.add(Registry.AddId("@id@"), @Contribution@(config.pictures))
      |
      |/** A media collection kept in the settings: the pictures listed under a place's name. The server
      |  * judges each licence and each match to a place itself. */
      |final class @Contribution@(pictures: List[ListedPicture]) extends MediaCollection:
      |  val title = MediaCollection.Title("@Name@ pictures")
      |  override val description = MediaCollection.Description("pictures listed in the @id@ plugin's settings")
      |  val role = MediaCollection.Image
      |
      |  def search(place: MediaPlace, http: Plugin.Http): Either[Plugin.Http.Failure, List[Either[Plugin.Http.Failure, MediaItem]]] =
      |    Right(pictures.filter(_.place.value.equalsIgnoreCase(place.label.value)).map(p => Right(item(p))))
      |
      |  private def item(p: ListedPicture): MediaItem =
      |    MediaItem(p.key, p.title, p.pageUrl, Some(p.imageUrl), Right(MediaRights(p.licence, credit = p.credit)),
      |      geography = List(MediaItem.Geography.Field("place") -> MediaItem.Geography.Value(p.place.value)))
      |""".stripMargin

  private val MediaCollectionTest =
    """/** The plugin loaded and run as the server runs it (the Histmersion plugin testkit), offline. */
      |class @Name@Suite extends munit.FunSuite:
      |  private val id   = Plugin.Info.Id("@id@")
      |  private val key  = Registries.MediaCollectionsKey("@id@")
      |  private val troy = MediaPlace(MediaPlace.Id("pleiades_550595"), MediaPlace.Label("Troy"))
      |
      |  private def picture(licence: String) =
      |    "pictures = [{ key = troy-1, title = \"The walls of Troy\", place = Troy, page-url = \"https://pictures.example/troy-1\", " +
      |      s"image-url = \"https://pictures.example/troy-1.jpg\", licence = \"$licence\", credit = \"A. Photographer\" }]"
      |
      |  private def harness(licence: String = "CC-BY-4.0"): PluginHarness =
      |    PluginHarness.load(id, picture(licence)).fold(e => fail(e.legacyText), identity)
      |
      |  test("the server registers the collection under the plugin's id"):
      |    assertEquals(harness().contributions.mediaCollections, List(key))
      |
      |  test("a place's pictures are found by its name, and the server would keep them"):
      |    val items = harness().searchMedia(key, troy).kept.flatMap(_.item)
      |    assertEquals(items.map(_.key), List(MediaItem.Key("troy-1")))
      |    assertEquals(items.flatMap(_.rights.toOption).map(_.licence), List(Licence("CC-BY-4.0")))
      |
      |  test("another place has none"):
      |    val hattusa = MediaPlace(MediaPlace.Id("pleiades_570491"), MediaPlace.Label("Hattusa"))
      |    assertEquals(harness().searchMedia(key, hattusa).verdicts, Nil)
      |
      |  test("a licence the server does not allow is refused, whatever the plugin says"):
      |    val report = harness("CC BY-NC 4.0").searchMedia(key, troy)
      |    assert(report.kept.isEmpty && report.verdicts.forall(_.refused.isDefined), report.toString)
      |""".stripMargin

  private val MediaCollectionLive =
    """
      |/** Against the real collection: what one search answers and what the server would keep. Replays
      |  * `recordings/known-place` offline when it holds a recording; otherwise runs only with
      |  * HISTMERSION_PLUGIN_LIVE=1 (HISTMERSION_PLUGIN_HTTP=record keeps the answers). See README.md. */
      |class @Name@LiveSuite extends munit.FunSuite:
      |  /** The settings, as under `histmersion.plugins.@id@` in histmersion.conf. */
      |  private val config = ""
      |  /** A place the collection has pictures of. */
      |  private val place = MediaPlace(MediaPlace.Id("pleiades_550595"), MediaPlace.Label("Troy"))
      |
      |  test("a search for a known place answers pictures the server would keep"):
""".stripMargin + LiveStart.replace("@case@", "known-place") +
      """      |    val report = harness.searchMedia(Registries.MediaCollectionsKey("@id@"), place)
      |    assert(report.kept.nonEmpty, (report.requests ++ report.log.map(_.toString) ++ report.verdicts.map(_.toString)).mkString("\n"))
      |""".stripMargin

  private val MediaCollectionDefaults =
    """# The @id@ plugin's settings: keys relative to histmersion.plugins.@id@ in histmersion.conf.
      |# Every setting the plugin reads has its default here; `enabled` is the server's (off unless set).
      |enabled = false
      |# each: { key, title, place, page-url, image-url, licence, credit }
      |pictures = []
      |""".stripMargin

  // ── event-catalogue ───────────────────────────────────────────────────────────────────────────

  private val EventCatalogueMain = Imports +
    """/** One event of a hand-kept list.
      |  *
      |  * @param year the year it happened, signed (BCE negative: 1600 BCE is -1600)
      |  * @param kind `eruption`, `earthquake`, `battle`, `treaty`, …
      |  * @param lat  degrees north, with `lon` degrees east: where it happened */
      |final case class ListedEvent(id: CatalogueEvent.Id, label: CatalogueEvent.Label, kind: CatalogueEvent.Kind,
      |    year: Year, lat: Latitude, lon: Longitude) derives ConfigReader
      |
      |/** The settings under `histmersion.plugins.@id@`; their defaults are in `resources/META-INF/histmersion/plugins/@id@.conf`. */
      |final case class @Name@Config(source: EventCatalogue.Citation, events: List[ListedEvent]) derives ConfigReader
      |
      |final class @Plugin@ extends Plugin:
      |  type Config = @Name@Config
      |  val info = Plugin.Info(Plugin.Info.Id("@id@"), Plugin.Info.Version("0.1.0"), Plugin.Info.SpiVersion(Spi.Version))
      |
      |  def register(config: @Name@Config, ctx: Plugin.Context): Either[Problem, Unit] =
      |    ctx.eventCatalogues.add(Registry.AddId("@id@"), @Contribution@(config.source, config.events))
      |
      |/** An event catalogue kept in the settings, read in one part. The server keeps the events of the
      |  * years an import asks for and stores them as research leads. */
      |final class @Contribution@(source: EventCatalogue.Citation, events: List[ListedEvent]) extends EventCatalogue:
      |  val title    = EventCatalogue.Title("@Name@")
      |  val licence  = Licence("the user's own list")
      |  val citation = source
      |  val home     = EventCatalogue.Home("https://example.org/@id@")
      |
      |  def parts(query: CatalogueQuery): List[EventCatalogue.ReadPart] = List(EventCatalogue.ReadPart("all"))
      |
      |  def read(part: EventCatalogue.ReadPart, query: CatalogueQuery, http: Plugin.Http): Either[Problem, CataloguePage] =
      |    Right(CataloguePage(events.map(event)))
      |
      |  private def event(e: ListedEvent): CatalogueEvent =
      |    CatalogueEvent(e.id, CatalogueEvent.Url(s"${home.value}#${e.id.value}"), e.label, e.kind,
      |      notBefore = Some(e.year), notAfter = Some(e.year), lat = Some(e.lat), lon = Some(e.lon))
      |""".stripMargin

  private val EventCatalogueTest =
    """/** The plugin loaded and run as the server runs it (the Histmersion plugin testkit), offline. */
      |class @Name@Suite extends munit.FunSuite:
      |  private val thera =
      |    "source = \"my notes\", events = [{ id = thera, label = \"Eruption of Thera\", kind = eruption, year = -1600, lat = 36.4, lon = 25.4 }]"
      |
      |  private val harness = PluginHarness.load(Plugin.Info.Id("@id@"), thera).fold(e => fail(e.legacyText), identity)
      |
      |  test("the server registers the catalogue under the plugin's id"):
      |    assertEquals(harness.contributions.eventCatalogues, List(Registries.EventCataloguesKey("@id@")))
      |
      |  test("every listed event is read, and stored dated to its year"):
      |    val report = harness.readCatalogue(Registries.EventCataloguesKey("@id@"), CatalogueQuery(None, Year(2025)))
      |    assertEquals(
      |      report.accepted.map(e => (e.event.id, e.notBefore, e.notAfter)),
      |      List((CatalogueEvent.Id("thera"), Some(Year(-1600)), Some(Year(-1600))))
      |    )
      |    assertEquals(report.refused, Nil)
      |""".stripMargin

  private val EventCatalogueLive =
    """
      |/** Against the real catalogue: what one import reads and what the server would store. Replays
      |  * `recordings/an-import` offline when it holds a recording; otherwise runs only with
      |  * HISTMERSION_PLUGIN_LIVE=1 (HISTMERSION_PLUGIN_HTTP=record keeps the answers). See README.md. */
      |class @Name@LiveSuite extends munit.FunSuite:
      |  /** The settings, as under `histmersion.plugins.@id@` in histmersion.conf. */
      |  private val config = ""
      |  /** The years an import asks for. */
      |  private val query = CatalogueQuery(Some(Year(-2000)), Year(2025))
      |
      |  test("an import reads events the server would store"):
""".stripMargin + LiveStart.replace("@case@", "an-import") +
      """      |    val report = harness.readCatalogue(Registries.EventCataloguesKey("@id@"), query)
      |    assert(report.failure.isEmpty && report.accepted.nonEmpty,
      |      (report.requests.map(_.value) ++ report.log.map(_.toString) ++ report.refused.map(_.value) :+ report.pages.toString).mkString("\n"))
      |""".stripMargin

  private val EventCatalogueDefaults =
    """# The @id@ plugin's settings: keys relative to histmersion.plugins.@id@ in histmersion.conf.
      |# Every setting the plugin reads has its default here; `enabled` is the server's (off unless set).
      |enabled = false
      |source = "a list kept by hand"
      |# each: { id, label, kind, year (signed: BCE negative), lat, lon }
      |events = []
      |""".stripMargin

  // ── ai-provider ───────────────────────────────────────────────────────────────────────────────

  private val AiProviderMain = Imports +
    """/** The settings under `histmersion.plugins.@id@`; their defaults are in `resources/META-INF/histmersion/plugins/@id@.conf`.
      |  *
      |  * @param reply what every call answers: a stand-in until `turn` calls a real model through `http` */
      |final case class @Name@Config(reply: AiTurn.Text) derives ConfigReader
      |
      |final class @Plugin@ extends Plugin:
      |  type Config = @Name@Config
      |  val info = Plugin.Info(Plugin.Info.Id("@id@"), Plugin.Info.Version("0.1.0"), Plugin.Info.SpiVersion(Spi.Version))
      |
      |  def register(config: @Name@Config, ctx: Plugin.Context): Either[Problem, Unit] =
      |    ctx.aiProviders.add(Registry.AddId("@id@"), @Contribution@(config.reply))
      |
      |/** An AI provider, registered under the provider name `@id@` (`@id@:<model>` in the agents' roles).
      |  * The server runs the tools, the loop and the budgets; a provider answers one model call at a time. */
      |final class @Contribution@(reply: AiTurn.Text) extends AiProvider:
      |  val title = AiProvider.Title("@Name@")
      |  override val description = AiProvider.Description("answers every call with the reply in its settings")
      |  override val capabilities =
      |    AiCapabilities(tools = AiCapabilities.Tools(false), structuredOutput = AiCapabilities.StructuredOutput(false))
      |
      |  override def models(http: AiHttp): Either[Problem, List[AiProvider.ModelName]] =
      |    Right(List(AiProvider.ModelName("fixed")))
      |
      |  def turn(request: AiTurn.Request, http: AiHttp): Either[AiFailure, AiTurn] =
      |    Right(AiTurn(reply, usage = Some(AiUsage(AiUsage.Input(0L), AiUsage.Output(0L))),
      |      servedBy = Some(AiTurn.ServedBy(request.model.value))))
      |""".stripMargin

  private val AiProviderTest =
    """/** The plugin loaded and called as the server calls it (the Histmersion plugin testkit). Its requests
      |  * go through a ScriptedAiHttp: answer them with `ScriptedAiHttp(Right(AiHttp.Response(200, "…")))`
      |  * and assert on what it sent (`requests`, keys masked). */
      |class @Name@Suite extends munit.FunSuite:
      |  private val request = new AiTurn.Request("fixed", "You are a test.", List(AiMessage.User(AiMessage.User.Text("Hello"))),
      |    Nil, None, 100, 1, 1)
      |
      |  private val harness = PluginHarness.load(Plugin.Info.Id("@id@"), "reply = \"{}\"").fold(e => fail(e.legacyText), identity)
      |
      |  test("the server registers the provider under the plugin's id"):
      |    assertEquals(harness.contributions.aiProviders, List(Registries.AiProvidersKey("@id@")))
      |
      |  test("every call is answered with the reply"):
      |    val report = harness.aiTurn(Registries.AiProvidersKey("@id@"), request)
      |    assertEquals(report.check, Right(()))
      |    assertEquals(report.turn.map(_.map(_.text)), Some(Right(AiTurn.Text("{}"))))
      |""".stripMargin

  private val AiProviderDefaults =
    """# The @id@ plugin's settings: keys relative to histmersion.plugins.@id@ in histmersion.conf.
      |# Every setting the plugin reads has its default here; `enabled` is the server's (off unless set).
      |enabled = false
      |reply = "{}"
      |""".stripMargin

  // ── secret-vault ──────────────────────────────────────────────────────────────────────────────

  private val SecretVaultMain = Imports +
    """/** The settings under `histmersion.plugins.@id@`; their defaults are in `resources/META-INF/histmersion/plugins/@id@.conf`.
      |  *
      |  * @param directory the directory that holds one file per secret */
      |final case class @Name@Config(directory: @Name@Config.Directory) derives ConfigReader
      |
      |object @Name@Config:
      |  type Directory = Directory.T
      |  object Directory extends Newtype[String, Pure]
      |
      |final class @Plugin@ extends Plugin:
      |  type Config = @Name@Config
      |  val info = Plugin.Info(Plugin.Info.Id("@id@"), Plugin.Info.Version("0.1.0"), Plugin.Info.SpiVersion(Spi.Version))
      |
      |  def register(config: @Name@Config, ctx: Plugin.Context): Either[Problem, Unit] =
      |    ctx.secretVaults.add(Registry.AddId("@id@"), @Contribution@(java.nio.file.Path.of(config.directory.value)))
      |
      |/** A secret vault: `@id@:<name>` in a setting is the content of the file `<name>` in the directory. */
      |final class @Contribution@(directory: java.nio.file.Path) extends Secret.Vault:
      |  val name = Secret.Vault.Name("@id@")
      |
      |  def resolve(ref: Secret.Vault.ResolveRef): Either[Problem, Secret.Resolved] =
      |    val file = directory.resolve(ref.value).normalize
      |    if ref.value.isEmpty || ref.value.contains('/') || !file.startsWith(directory.normalize) then
      |      Left(Problem.Unclassified(Legacy.Text(s"'${ref.value}' is not a name in the directory")))
      |    else if !java.nio.file.Files.isRegularFile(file) then Left(Problem.Unclassified(Legacy.Text(s"no secret '${ref.value}'")))
      |    else Right(Secret.Resolved(java.nio.file.Files.readString(file).trim))
      |""".stripMargin

  private val SecretVaultTest =
    """/** The plugin loaded as the server loads it, and asked as the server asks it (the Histmersion plugin
      |  * testkit). */
      |class @Name@Suite extends munit.FunSuite:
      |  private val directory = FunFixture[java.nio.file.Path](
      |    _ =>
      |      val d = java.nio.file.Files.createTempDirectory("@id@")
      |      java.nio.file.Files.writeString(d.resolve("portal-token"), "s3cret\n")
      |      d
      |    ,
      |    d => java.nio.file.Files.walk(d).sorted(java.util.Comparator.reverseOrder()).forEach(p => java.nio.file.Files.delete(p))
      |  )
      |
      |  private def harness(d: Path): PluginHarness =
      |    PluginHarness.load(Plugin.Info.Id("@id@"), s"directory = \"$d\"").fold(e => fail(e.legacyText), identity)
      |
      |  private def resolved(d: Path, name: String) =
      |    harness(d).resolveSecret(Secret.Ref.Vault.Value("@id@"), Secret.Ref.Vault.Ref(name))
      |
      |  directory.test("the server registers the vault under the plugin's id"): d =>
      |    assertEquals(harness(d).contributions.secretVaults, List(Registries.SecretVaultsKey("@id@")))
      |
      |  directory.test("a secret is the content of its file"): d =>
      |    assertEquals(resolved(d, "portal-token").map(_.value.toString), Right("s3cret"))
      |
      |  directory.test("a name outside the directory is refused"): d =>
      |    assert(resolved(d, "../portal-token").isLeft)
      |    assert(resolved(d, "missing").isLeft)
      |""".stripMargin

  private val SecretVaultDefaults =
    """# The @id@ plugin's settings: keys relative to histmersion.plugins.@id@ in histmersion.conf.
      |# Every setting the plugin reads has its default here; `enabled` is the server's (off unless set).
      |enabled = false
      |directory = ""
      |""".stripMargin
