package com.histmersion.cli

/** The sources of each kind's new plugin, with the placeholders `PluginTemplate` fills in (`@Name@`,
  * `@id@`, `@Plugin@`, `@Contribution@`). Each one works as written: it compiles, its tests pass, and
  * the server registers it with its defaults alone. */
private[cli] object KindSources:

  def main(kind: PluginKind): String = kind match
    case PluginKind.SourceFetcher   => SourceFetcherMain
    case PluginKind.MediaCollection => MediaCollectionMain
    case PluginKind.EventCatalogue  => EventCatalogueMain
    case PluginKind.AiProvider      => AiProviderMain
    case PluginKind.SecretVault     => SecretVaultMain

  def test(kind: PluginKind): String = TestImports + (kind match
    case PluginKind.SourceFetcher   => SourceFetcherTest
    case PluginKind.MediaCollection => MediaCollectionTest
    case PluginKind.EventCatalogue  => EventCatalogueTest
    case PluginKind.AiProvider      => AiProviderTest
    case PluginKind.SecretVault     => SecretVaultTest) + fakeContext(kind)

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
      |import com.histmersion.plugin.PluginConfig.given
      |import hearth.kindlings.sconfigderivation.ConfigReader
      |
      |""".stripMargin

  private val TestImports =
    """import com.histmersion.plugin.*
      |
      |""".stripMargin

  /** A test context: the registry of this kind records what the plugin adds; every other refuses. */
  private def fakeContext(kind: PluginKind): String =
    val (field, registry, tpe) = kind match
      case PluginKind.SourceFetcher   => ("fetchers", "sourceFetchers", "SourceFetcher")
      case PluginKind.MediaCollection => ("collections", "mediaCollections", "MediaCollection")
      case PluginKind.EventCatalogue  => ("catalogues", "eventCatalogues", "EventCatalogue")
      case PluginKind.AiProvider      => ("providers", "aiProviders", "AiProvider")
      case PluginKind.SecretVault     => ("vaults", "secretVaults", "SecretVault")
    val vaults =
      if kind == PluginKind.SecretVault then ""
      else "  def secretVaults: Registry[SecretVault] = Registry.refusing(\"no vaults in this test\")\n"
    val overrides = if kind == PluginKind.SecretVault then "" else "override "
    s"""
       |/** The host as a test sees it: no network, no secrets, a silent log; the $tpe registry keeps
       |  * what the plugin adds. */
       |final class TestContext(pages: Map[String, Array[Byte]] = Map.empty) extends PluginContext:
       |  val $field = collection.mutable.Map.empty[String, $tpe]
       |  def http: PluginHttp = TestHttp(pages)
       |  def log: PluginLog = SilentLog
       |  def env(name: String): Option[String] = None
       |  def secret(s: Secret): Either[String, String] = Left("no secrets in this test")
       |$vaults  ${overrides}def $registry: Registry[$tpe] = new Registry[$tpe]:
       |    def add(id: String, a: $tpe): Either[String, Unit] = Right($field.update(id, a))
       |
       |final class TestHttp(pages: Map[String, Array[Byte]]) extends PluginHttp:
       |  def get(url: String, headers: Map[String, String]): Either[String, Array[Byte]] =
       |    pages.get(url).toRight(s"HTTP 404: $$url")
       |
       |object SilentLog extends PluginLog:
       |  def info(message: String): Unit = ()
       |  def warn(message: String): Unit = ()
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
      |  val info = PluginInfo("@id@", "0.1.0", Spi.Version)
      |
      |  def register(config: @Name@Config, ctx: PluginContext): Either[String, Unit] =
      |    ctx.sourceFetchers.add("@id@", @Contribution@(config.baseUrl, ctx.http))
      |
      |/** One step of the acquisition chain: a copy of a work by its DOI, from a mirror that serves PDFs. */
      |final class @Contribution@(baseUrl: String, http: PluginHttp) extends SourceFetcher:
      |  val access = Access.Open
      |
      |  def fetch(work: WorkKey, ctx: FetchContext): Fetch =
      |    work.doi match
      |      case None => Fetch.Miss("no DOI")
      |      case Some(doi) =>
      |        val url = s"${baseUrl.stripSuffix("/")}/$doi.pdf"
      |        http.get(url).fold(Fetch.Miss(_), bytes => Fetch.Found(FetchedFile("@id@", Some(url), bytes, "pdf")))
      |""".stripMargin

  private val SourceFetcherTest =
    """class @Name@Suite extends munit.FunSuite:
      |  private val pdf  = "%PDF-1.7".getBytes
      |  private val page = "https://mirror.example/10.1234/abc.pdf"
      |
      |  private object OneFetch extends FetchContext:
      |    def memo[A](key: String)(compute: => A): A = compute
      |    def log: PluginLog = SilentLog
      |
      |  test("a work the mirror has is found"):
      |    val fetcher = @Contribution@("https://mirror.example/", TestHttp(Map(page -> pdf)))
      |    fetcher.fetch(WorkKey(doi = Some("10.1234/abc")), OneFetch) match
      |      case Fetch.Found(file) => assertEquals((file.url, file.extension), (Some(page), "pdf"))
      |      case other             => fail(s"expected a copy, got $other")
      |
      |  test("a work the mirror lacks, or one without a DOI, is a miss"):
      |    val fetcher = @Contribution@("https://mirror.example", TestHttp(Map.empty))
      |    assert(fetcher.fetch(WorkKey(doi = Some("10.1234/xyz")), OneFetch).isInstanceOf[Fetch.Miss])
      |    assertEquals(fetcher.fetch(WorkKey(title = Some("A title")), OneFetch), Fetch.Miss("no DOI"))
      |
      |  test("register adds the fetcher under the plugin's id"):
      |    val ctx = TestContext()
      |    assertEquals(@Plugin@().register(@Name@Config("https://mirror.example"), ctx), Right(()))
      |    assertEquals(ctx.fetchers.keySet.toSet, Set("@id@"))
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
      |  val info = PluginInfo("@id@", "0.1.0", Spi.Version)
      |
      |  def register(config: @Name@Config, ctx: PluginContext): Either[String, Unit] =
      |    ctx.mediaCollections.add("@id@", @Contribution@(config.pictures))
      |
      |/** A media collection kept in the settings: the pictures listed under a place's name. The server
      |  * judges each licence and each match to a place itself. */
      |final class @Contribution@(pictures: List[ListedPicture]) extends MediaCollection:
      |  val title = "@Name@ pictures"
      |  override val description = "pictures listed in the @id@ plugin's settings"
      |  val role = MediaCollection.Image
      |
      |  def search(place: MediaPlace, http: PluginHttp): Either[String, List[Either[String, MediaItem]]] =
      |    Right(pictures.filter(_.place.equalsIgnoreCase(place.label)).map(p =>
      |      Right(MediaItem(p.key, p.title, p.pageUrl, Some(p.imageUrl), Right(MediaRights(p.licence, credit = p.credit)),
      |        geography = List("place" -> p.place)))))
      |""".stripMargin

  private val MediaCollectionTest =
    """class @Name@Suite extends munit.FunSuite:
      |  private val troy = ListedPicture("troy-1", "The walls of Troy", "Troy", "https://pictures.example/troy-1",
      |    "https://pictures.example/troy-1.jpg", "CC-BY-4.0", "A. Photographer")
      |
      |  test("a place's pictures are found by its name"):
      |    val found = @Contribution@(List(troy)).search(MediaPlace("pleiades_550595", "Troy"), TestHttp(Map.empty))
      |    assertEquals(found.map(_.map(_.map(_.key))), Right(List(Right("troy-1"))))
      |
      |  test("another place has none"):
      |    assertEquals(@Contribution@(List(troy)).search(MediaPlace("pleiades_570491", "Hattusa"), TestHttp(Map.empty)), Right(Nil))
      |
      |  test("register adds the collection under the plugin's id"):
      |    val ctx = TestContext()
      |    assertEquals(@Plugin@().register(@Name@Config(List(troy)), ctx), Right(()))
      |    assertEquals(ctx.collections.keySet.toSet, Set("@id@"))
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
      |  * @param kind `eruption`, `earthquake`, `battle`, `treaty`, … */
      |final case class ListedEvent(id: String, label: String, kind: String, year: Long, lat: Double, lon: Double)
      |    derives ConfigReader
      |
      |/** The settings under `histmersion.plugins.@id@`; their defaults are in `resources/META-INF/histmersion/plugins/@id@.conf`. */
      |final case class @Name@Config(source: String, events: List[ListedEvent]) derives ConfigReader
      |
      |final class @Plugin@ extends Plugin:
      |  type Config = @Name@Config
      |  val info = PluginInfo("@id@", "0.1.0", Spi.Version)
      |
      |  def register(config: @Name@Config, ctx: PluginContext): Either[String, Unit] =
      |    ctx.eventCatalogues.add("@id@", @Contribution@(config.source, config.events))
      |
      |/** An event catalogue kept in the settings, read in one part. The server keeps the events of the
      |  * years an import asks for and stores them as research leads. */
      |final class @Contribution@(source: String, events: List[ListedEvent]) extends EventCatalogue:
      |  val title    = "@Name@"
      |  val licence  = "the user's own list"
      |  val citation = source
      |  val home     = "https://example.org/@id@"
      |
      |  def parts(query: CatalogueQuery): List[String] = List("all")
      |
      |  def read(part: String, query: CatalogueQuery, http: PluginHttp): Either[String, CataloguePage] =
      |    Right(CataloguePage(events.map(e =>
      |      CatalogueEvent(e.id, s"$home#${e.id}", e.label, e.kind, notBefore = Some(e.year), notAfter = Some(e.year),
      |        lat = Some(e.lat), lon = Some(e.lon)))))
      |""".stripMargin

  private val EventCatalogueTest =
    """class @Name@Suite extends munit.FunSuite:
      |  private val thera = ListedEvent("thera", "Eruption of Thera", "eruption", -1600, 36.4, 25.4)
      |
      |  test("every listed event is read, dated to its year"):
      |    val page = @Contribution@("my notes", List(thera)).read("all", CatalogueQuery(None, 2025), TestHttp(Map.empty))
      |    assertEquals(page.map(_.events.map(e => (e.id, e.notBefore, e.notAfter))), Right(List(("thera", Some(-1600L), Some(-1600L)))))
      |
      |  test("register adds the catalogue under the plugin's id"):
      |    val ctx = TestContext()
      |    assertEquals(@Plugin@().register(@Name@Config("my notes", List(thera)), ctx), Right(()))
      |    assertEquals(ctx.catalogues.keySet.toSet, Set("@id@"))
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
      |  val info = PluginInfo("@id@", "0.1.0", Spi.Version)
      |
      |  def register(config: @Name@Config, ctx: PluginContext): Either[String, Unit] =
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
      |  def turn(request: AiTurnRequest, http: AiHttp): Either[AiFailure, AiTurn] =
      |    Right(AiTurn(reply, usage = Some(AiUsage(input = 0, output = 0)), servedBy = Some(request.model)))
      |""".stripMargin

  private val AiProviderTest =
    """class @Name@Suite extends munit.FunSuite:
      |  private val request = AiTurnRequest("fixed", "You are a test.", List(AiMessage.User("Hello")), Nil, None,
      |    maxTokens = 100, iteration = 1, maxIterations = 1)
      |
      |  test("every call is answered with the reply"):
      |    assertEquals(@Contribution@("{}").turn(request, AiHttp.Offline).map(_.text), Right("{}"))
      |
      |  test("register adds the provider under the plugin's id"):
      |    val ctx = TestContext()
      |    assertEquals(@Plugin@().register(@Name@Config("{}"), ctx), Right(()))
      |    assertEquals(ctx.providers.keySet.toSet, Set("@id@"))
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
      |  val info = PluginInfo("@id@", "0.1.0", Spi.Version)
      |
      |  def register(config: @Name@Config, ctx: PluginContext): Either[String, Unit] =
      |    ctx.secretVaults.add("@id@", @Contribution@(java.nio.file.Path.of(config.directory)))
      |
      |/** A secret vault: `@id@:<name>` in a setting is the content of the file `<name>` in the directory. */
      |final class @Contribution@(directory: java.nio.file.Path) extends SecretVault:
      |  val name = "@id@"
      |
      |  def resolve(ref: String): Either[String, String] =
      |    val file = directory.resolve(ref).normalize
      |    if ref.isEmpty || ref.contains('/') || !file.startsWith(directory.normalize) then Left(s"'$ref' is not a name in the directory")
      |    else if !java.nio.file.Files.isRegularFile(file) then Left(s"no secret '$ref'")
      |    else Right(java.nio.file.Files.readString(file).trim)
      |""".stripMargin

  private val SecretVaultTest =
    """class @Name@Suite extends munit.FunSuite:
      |  private val directory = FunFixture[java.nio.file.Path](
      |    _ =>
      |      val d = java.nio.file.Files.createTempDirectory("@id@")
      |      java.nio.file.Files.writeString(d.resolve("portal-token"), "s3cret\n")
      |      d
      |    ,
      |    d => java.nio.file.Files.walk(d).sorted(java.util.Comparator.reverseOrder()).forEach(p => java.nio.file.Files.delete(p))
      |  )
      |
      |  directory.test("a secret is the content of its file"): d =>
      |    assertEquals(@Contribution@(d).resolve("portal-token"), Right("s3cret"))
      |
      |  directory.test("a name outside the directory is refused"): d =>
      |    assert(@Contribution@(d).resolve("../portal-token").isLeft)
      |    assert(@Contribution@(d).resolve("missing").isLeft)
      |
      |  test("register adds the vault under the plugin's id"):
      |    val ctx = TestContext()
      |    assertEquals(@Plugin@().register(@Name@Config("/nonexistent"), ctx), Right(()))
      |    assertEquals(ctx.vaults.keySet.toSet, Set("@id@"))
      |""".stripMargin

  private val SecretVaultDefaults =
    """# The @id@ plugin's settings: keys relative to histmersion.plugins.@id@ in histmersion.conf.
      |# Every setting the plugin reads has its default here; `enabled` is the server's (off unless set).
      |enabled = false
      |directory = ""
      |""".stripMargin
