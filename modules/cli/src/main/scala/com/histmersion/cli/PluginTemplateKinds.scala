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
    """import com.histmersion.plugin.*
      |import com.histmersion.plugin.Plugin.Config.given
      |import hearth.kindlings.sconfigderivation.ConfigReader
      |
      |""".stripMargin

  private val TestImports =
    """import com.histmersion.plugin.*
      |import com.histmersion.plugin.testkit.*
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
      |    assume(mode.isRight, mode.left.getOrElse(""))
      |    val harness = PluginHarness.load("@id@", config, http = mode.toOption.get).fold(e => fail(e), identity)
      |""".stripMargin

  // ── source-fetcher ────────────────────────────────────────────────────────────────────────────

  private val SourceFetcherMain = Imports +
    """/** The settings under `histmersion.plugins.@id@`; their defaults are in `resources/META-INF/histmersion/plugins/@id@.conf`.
      |  *
      |  * @param baseUrl where the copies are: a work with the DOI `10.1234/abc` is `<base-url>/10.1234/abc.pdf` */
      |final case class @Name@Config(baseUrl: String) derives ConfigReader
      |
      |final class @Plugin@ extends Plugin:
      |  type Config = @Name@Config
      |  val info = Plugin.Info("@id@", "0.1.0", Spi.Version)
      |
      |  def register(config: @Name@Config, ctx: Plugin.Context): Either[String, Unit] =
      |    ctx.sourceFetchers.add("@id@", @Contribution@(config.baseUrl, ctx.http))
      |
      |/** One step of the acquisition chain: a copy of a work by its DOI, from a mirror that serves PDFs. */
      |final class @Contribution@(baseUrl: String, http: Plugin.Http) extends SourceFetcher:
      |  val access = Access.Open
      |
      |  def fetch(work: WorkKey, ctx: Fetch.Context): Fetch =
      |    work.doi match
      |      case None => Fetch.Miss("no DOI")
      |      case Some(doi) =>
      |        val url = s"${baseUrl.stripSuffix("/")}/${doi.value}.pdf"
      |        http.get(url).fold(Fetch.Miss(_), bytes => Fetch.Found(FetchedFile("@id@", Some(url), bytes, "pdf")))
      |""".stripMargin

  private val SourceFetcherTest =
    """/** The plugin loaded and run as the server runs it (the Histmersion plugin testkit), offline: each
      |  * request is answered by a stub. */
      |class @Name@Suite extends munit.FunSuite:
      |  /** A body the server takes for a PDF: `%PDF` in its first KiB, and more than a KiB of it. */
      |  private val pdf  = ("%PDF-1.7\n" + " " * 2048 + "\n%%EOF\n").getBytes
      |  private val page = "https://mirror.example/10.1234/abc.pdf"
      |
      |  private def harness(pages: (String, StubAnswer)*): PluginHarness =
      |    PluginHarness.load("@id@", "base-url = \"https://mirror.example/\"", http = HttpMode.Stub(pages.toMap))
      |      .fold(e => fail(e), identity)
      |
      |  test("the server registers the fetcher under the plugin's id"):
      |    assertEquals(harness().contributions.sourceFetchers, List("@id@"))
      |
      |  test("a work the mirror has is found"):
      |    harness(page -> StubAnswer.ok(pdf, "application/pdf")).fetch(WorkKey(doi = Some(Doi("10.1234/abc")))).outcome match
      |      case FetchOutcome.Found(fetcher, file, _) => assertEquals((fetcher, file.url, file.extension), ("@id@", Some(page), "pdf"))
      |      case other                               => fail(s"expected a copy, got $other")
      |
      |  test("a page that is not a PDF is no copy: the server checks every PDF"):
      |    val report = harness(page -> StubAnswer.text("<html>Sign in</html>", "text/html")).fetch(WorkKey(doi = Some(Doi("10.1234/abc"))))
      |    assertEquals(report.outcome, FetchOutcome.Miss("@id@: not a PDF"))
      |
      |  test("a work the mirror lacks, or one without a DOI, is a miss"):
      |    assert(harness().fetch(WorkKey(doi = Some(Doi("10.1234/xyz")))).outcome.isInstanceOf[FetchOutcome.Miss])
      |    assertEquals(harness().fetch(WorkKey(title = Some("A title"))).outcome, FetchOutcome.Miss("@id@: no DOI"))
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
      |  private val doi = "10.1234/abc"
      |
      |  test("finds and downloads a copy of a known work"):
""".stripMargin + LiveStart.replace("@case@", "known-work") +
      """      |    // the work as an ingest asks for it: its title, authors and year looked up by the DOI
      |    val work   = WorkKey.fromDoi(doi, harness).getOrElse(WorkKey(doi = Doi.option(doi)))
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
      |final case class ListedPicture(key: String, title: String, place: String, pageUrl: String, imageUrl: String,
      |    licence: String, credit: String) derives ConfigReader
      |
      |/** The settings under `histmersion.plugins.@id@`; their defaults are in `resources/META-INF/histmersion/plugins/@id@.conf`. */
      |final case class @Name@Config(pictures: List[ListedPicture]) derives ConfigReader
      |
      |final class @Plugin@ extends Plugin:
      |  type Config = @Name@Config
      |  val info = Plugin.Info("@id@", "0.1.0", Spi.Version)
      |
      |  def register(config: @Name@Config, ctx: Plugin.Context): Either[String, Unit] =
      |    ctx.mediaCollections.add("@id@", @Contribution@(config.pictures))
      |
      |/** A media collection kept in the settings: the pictures listed under a place's name. The server
      |  * judges each licence and each match to a place itself. */
      |final class @Contribution@(pictures: List[ListedPicture]) extends MediaCollection:
      |  val title = "@Name@ pictures"
      |  override val description = "pictures listed in the @id@ plugin's settings"
      |  val role = MediaCollection.Image
      |
      |  def search(place: MediaPlace, http: Plugin.Http): Either[String, List[Either[String, MediaItem]]] =
      |    Right(pictures.filter(_.place.equalsIgnoreCase(place.label)).map(p =>
      |      Right(MediaItem(p.key, p.title, p.pageUrl, Some(p.imageUrl), Right(MediaRights(Licence(p.licence), credit = p.credit)),
      |        geography = List("place" -> p.place)))))
      |""".stripMargin

  private val MediaCollectionTest =
    """/** The plugin loaded and run as the server runs it (the Histmersion plugin testkit), offline. */
      |class @Name@Suite extends munit.FunSuite:
      |  private def picture(licence: String) =
      |    "pictures = [{ key = troy-1, title = \"The walls of Troy\", place = Troy, page-url = \"https://pictures.example/troy-1\", " +
      |      s"image-url = \"https://pictures.example/troy-1.jpg\", licence = \"$licence\", credit = \"A. Photographer\" }]"
      |
      |  private def harness(licence: String = "CC-BY-4.0"): PluginHarness =
      |    PluginHarness.load("@id@", picture(licence)).fold(e => fail(e), identity)
      |
      |  test("the server registers the collection under the plugin's id"):
      |    assertEquals(harness().contributions.mediaCollections, List("@id@"))
      |
      |  test("a place's pictures are found by its name, and the server would keep them"):
      |    val report = harness().searchMedia("@id@", MediaPlace("pleiades_550595", "Troy"))
      |    assertEquals(report.kept.map(v => (v.key, v.licence)), List(("troy-1", Some("CC-BY-4.0"))))
      |
      |  test("another place has none"):
      |    assertEquals(harness().searchMedia("@id@", MediaPlace("pleiades_570491", "Hattusa")).verdicts, Nil)
      |
      |  test("a licence the server does not allow is refused, whatever the plugin says"):
      |    val report = harness("CC BY-NC 4.0").searchMedia("@id@", MediaPlace("pleiades_550595", "Troy"))
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
      |  private val place = MediaPlace("pleiades_550595", "Troy")
      |
      |  test("a search for a known place answers pictures the server would keep"):
""".stripMargin + LiveStart.replace("@case@", "known-place") +
      """      |    val report = harness.searchMedia("@id@", place)
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
      |final case class ListedEvent(id: String, label: String, kind: String, year: Int, lat: Double, lon: Double)
      |    derives ConfigReader
      |
      |/** The settings under `histmersion.plugins.@id@`; their defaults are in `resources/META-INF/histmersion/plugins/@id@.conf`. */
      |final case class @Name@Config(source: String, events: List[ListedEvent]) derives ConfigReader
      |
      |final class @Plugin@ extends Plugin:
      |  type Config = @Name@Config
      |  val info = Plugin.Info("@id@", "0.1.0", Spi.Version)
      |
      |  def register(config: @Name@Config, ctx: Plugin.Context): Either[String, Unit] =
      |    ctx.eventCatalogues.add("@id@", @Contribution@(config.source, config.events))
      |
      |/** An event catalogue kept in the settings, read in one part. The server keeps the events of the
      |  * years an import asks for and stores them as research leads. */
      |final class @Contribution@(source: String, events: List[ListedEvent]) extends EventCatalogue:
      |  val title    = "@Name@"
      |  val licence  = Licence("the user's own list")
      |  val citation = source
      |  val home     = "https://example.org/@id@"
      |
      |  def parts(query: CatalogueQuery): List[String] = List("all")
      |
      |  def read(part: String, query: CatalogueQuery, http: Plugin.Http): Either[String, CataloguePage] =
      |    Right(CataloguePage(events.map { e =>
      |      // a position off the globe is left out: the event is still read, without one
      |      val position = for lat <- Latitude.option(e.lat); lon <- Longitude.option(e.lon) yield (lat, lon)
      |      CatalogueEvent(e.id, s"$home#${e.id}", e.label, e.kind, notBefore = Some(Year(e.year)), notAfter = Some(Year(e.year)),
      |        lat = position.map(_._1), lon = position.map(_._2))
      |    }))
      |""".stripMargin

  private val EventCatalogueTest =
    """/** The plugin loaded and run as the server runs it (the Histmersion plugin testkit), offline. */
      |class @Name@Suite extends munit.FunSuite:
      |  private val thera =
      |    "source = \"my notes\", events = [{ id = thera, label = \"Eruption of Thera\", kind = eruption, year = -1600, lat = 36.4, lon = 25.4 }]"
      |
      |  private val harness = PluginHarness.load("@id@", thera).fold(e => fail(e), identity)
      |
      |  test("the server registers the catalogue under the plugin's id"):
      |    assertEquals(harness.contributions.eventCatalogues, List("@id@"))
      |
      |  test("every listed event is read, and stored dated to its year"):
      |    val report = harness.readCatalogue("@id@", CatalogueQuery(None, Year(2025)))
      |    assertEquals(report.accepted.map(e => (e.event.id, e.notBefore, e.notAfter)), List(("thera", Some(Year(-1600)), Some(Year(-1600)))))
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
      """      |    val report = harness.readCatalogue("@id@", query)
      |    assert(report.failure.isEmpty && report.accepted.nonEmpty,
      |      (report.requests ++ report.log.map(_.toString) ++ report.refused :+ report.pages.toString).mkString("\n"))
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
      |final case class @Name@Config(reply: String) derives ConfigReader
      |
      |final class @Plugin@ extends Plugin:
      |  type Config = @Name@Config
      |  val info = Plugin.Info("@id@", "0.1.0", Spi.Version)
      |
      |  def register(config: @Name@Config, ctx: Plugin.Context): Either[String, Unit] =
      |    ctx.aiProviders.add("@id@", @Contribution@(config.reply))
      |
      |/** An AI provider, registered under the provider name `@id@` (`@id@:<model>` in the agents' roles).
      |  * The server runs the tools, the loop and the budgets; a provider answers one model call at a time. */
      |final class @Contribution@(reply: String) extends AiProvider:
      |  val title = "@Name@"
      |  override val description = "answers every call with the reply in its settings"
      |  override val capabilities = AiCapabilities(tools = false, structuredOutput = false)
      |
      |  override def models(http: AiHttp): Either[String, List[String]] = Right(List("fixed"))
      |
      |  def turn(request: AiTurn.Request, http: AiHttp): Either[AiFailure, AiTurn] =
      |    Right(AiTurn(reply, usage = Some(AiUsage(input = 0, output = 0)), servedBy = Some(request.model)))
      |""".stripMargin

  private val AiProviderTest =
    """/** The plugin loaded and called as the server calls it (the Histmersion plugin testkit). Its requests
      |  * go through a ScriptedAiHttp: answer them with `ScriptedAiHttp(Right(AiHttp.Response(200, "…")))`
      |  * and assert on what it sent (`requests`, keys masked). */
      |class @Name@Suite extends munit.FunSuite:
      |  private val request = AiTurn.Request("fixed", "You are a test.", List(AiMessage.User("Hello")), Nil, None,
      |    maxTokens = 100, iteration = 1, maxIterations = 1)
      |
      |  private val harness = PluginHarness.load("@id@", "reply = \"{}\"").fold(e => fail(e), identity)
      |
      |  test("the server registers the provider under the plugin's id"):
      |    assertEquals(harness.contributions.aiProviders, List("@id@"))
      |
      |  test("every call is answered with the reply"):
      |    val report = harness.aiTurn("@id@", request)
      |    assertEquals(report.check, Right(()))
      |    assertEquals(report.turn.map(_.map(_.text)), Some(Right("{}")))
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
      |final case class @Name@Config(directory: String) derives ConfigReader
      |
      |final class @Plugin@ extends Plugin:
      |  type Config = @Name@Config
      |  val info = Plugin.Info("@id@", "0.1.0", Spi.Version)
      |
      |  def register(config: @Name@Config, ctx: Plugin.Context): Either[String, Unit] =
      |    ctx.secretVaults.add("@id@", @Contribution@(java.nio.file.Path.of(config.directory)))
      |
      |/** A secret vault: `@id@:<name>` in a setting is the content of the file `<name>` in the directory. */
      |final class @Contribution@(directory: java.nio.file.Path) extends Secret.Vault:
      |  val name = "@id@"
      |
      |  def resolve(ref: String): Either[String, String] =
      |    val file = directory.resolve(ref).normalize
      |    if ref.isEmpty || ref.contains('/') || !file.startsWith(directory.normalize) then Left(s"'$ref' is not a name in the directory")
      |    else if !java.nio.file.Files.isRegularFile(file) then Left(s"no secret '$ref'")
      |    else Right(java.nio.file.Files.readString(file).trim)
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
      |    PluginHarness.load("@id@", s"directory = \"$d\"").fold(e => fail(e), identity)
      |
      |  directory.test("the server registers the vault under the plugin's id"): d =>
      |    assertEquals(harness(d).contributions.secretVaults, List("@id@"))
      |
      |  directory.test("a secret is the content of its file"): d =>
      |    assertEquals(harness(d).resolveSecret("@id@", "portal-token"), Right("s3cret"))
      |
      |  directory.test("a name outside the directory is refused"): d =>
      |    assert(harness(d).resolveSecret("@id@", "../portal-token").isLeft)
      |    assert(harness(d).resolveSecret("@id@", "missing").isLeft)
      |""".stripMargin

  private val SecretVaultDefaults =
    """# The @id@ plugin's settings: keys relative to histmersion.plugins.@id@ in histmersion.conf.
      |# Every setting the plugin reads has its default here; `enabled` is the server's (off unless set).
      |enabled = false
      |directory = ""
      |""".stripMargin
