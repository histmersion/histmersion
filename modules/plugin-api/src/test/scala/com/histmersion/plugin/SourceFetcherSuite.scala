package com.histmersion.plugin

/** The source-fetching part of the SPI (docs/research-plugins.md §5.1). */
class SourceFetcherSuite extends munit.FunSuite:

  test("a WorkKey with no identifier is empty; title, authors, year and format identify nothing"):
    assert(WorkKey().isEmpty)
    assert(WorkKey(title = Some("Black Athena"), authors = List("Martin Bernal"), year = Some(1987),
      format = Some("pdf")).isEmpty)
    assert(!WorkKey(doi = Some("10.1038/nature14317")).isEmpty)
    assert(!WorkKey(archive = Some("racesofeurope")).isEmpty)
    assert(!WorkKey(pmcid = Some("PMC6091220")).isEmpty)

  test("a refusing registry takes nothing and says why, naming the id"):
    val r = Registry.refusing[SourceFetcher]("no chain here")
    val f = new SourceFetcher:
      val access = Access.Open
      def fetch(work: WorkKey, ctx: FetchContext) = Fetch.Miss("nothing")
    assertEquals(r.add("portal", f), Left("'portal': no chain here"))

  test("a context written before source fetchers existed still compiles and refuses a fetcher"):
    val ctx = new PluginContext:
      def http = new PluginHttp { def get(url: String, headers: Map[String, String]) = Left("offline") }
      def log = new PluginLog { def info(m: String) = (); def warn(m: String) = () }
      def env(name: String) = None
      def secret(s: Secret) = Left("none")
      def secretVaults = Registry.refusing("no vaults")
    val f = new SourceFetcher:
      val access = Access.Subscription
      def fetch(work: WorkKey, ctx: FetchContext) = Fetch.Refused("no entitlement")
    assert(ctx.sourceFetchers.add("portal", f).left.exists(_.contains("no acquisition chain")))

  test("a FetchedFile's licence is optional: the four-field forms still build it, without one (#2560)"):
    val pdf = "%PDF-1.7".getBytes("US-ASCII")
    val old = FetchedFile("oabooks", Some("https://example.org/b.pdf"), pdf, "pdf")
    val viaNew = new FetchedFile("oabooks", None, pdf, "pdf")
    assertEquals((old.licence, viaNew.licence), (None, None))
    val withIt = FetchedFile("oabooks", None, pdf, "pdf", Some("CC-BY-4.0"))
    assertEquals(withIt.licence, Some("CC-BY-4.0"))
    assertEquals(old.copy(licence = Some("public-domain")).licence, Some("public-domain"))
    // the factory a plugin compiled against the first SPI 1 links to is still there
    val fourArgs = FetchedFile.getClass.getMethods.exists(m => m.getName == "apply" && m.getParameterCount == 4)
    val fourCtor = classOf[FetchedFile].getConstructors.exists(_.getParameterCount == 4)
    assert(fourArgs && fourCtor, "the four-field apply and constructor")
