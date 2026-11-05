package com.histmersion.plugin

/** The source-fetching part of the SPI (docs/research-plugins.md §5.1). */
class SourceFetcherSuite extends munit.FunSuite:

  test("a WorkKey with no identifier is empty; title, authors, year and format identify nothing"):
    assert((WorkKey().isEmpty).value)
    assert(
      WorkKey(
        title = Some(_root_.com.histmersion.plugin.WorkKey.Title("Black Athena")),
        authors = List(_root_.com.histmersion.plugin.WorkKey.Authors("Martin Bernal")),
        year = Some(Year(1987)),
        format = Some(_root_.com.histmersion.plugin.WorkKey.Format("pdf"))
      ).isEmpty
    )
    assert(!(WorkKey(doi = Some(Doi("10.1038/nature14317"))).isEmpty).value)
    assert(!(WorkKey(archive = Some(_root_.com.histmersion.plugin.WorkKey.Archive("racesofeurope"))).isEmpty).value)
    assert(!(WorkKey(pmcid = Some(_root_.com.histmersion.plugin.WorkKey.Pmcid("PMC6091220"))).isEmpty).value)

  test("a refusing registry takes nothing and says why, naming the id"):
    val r = Registry.refusing[SourceFetcher](_root_.com.histmersion.plugin.Registry.RefusingWhy("no chain here"))
    val f = new SourceFetcher:
      val access = Access.Open
      def fetch(work: WorkKey, ctx: Fetch.Context) = Fetch.Miss(_root_.com.histmersion.plugin.Fetch.Miss.Why("nothing"))
    assertEquals(((r.add(_root_.com.histmersion.plugin.Registry.AddId("portal"), f)).left.map(refactorError => (refactorError).value): Either[String, Unit]), Left("'portal': no chain here"))

  test("a context written before source fetchers existed still compiles and refuses a fetcher"):
    val ctx = new Plugin.Context:
      def http = new Plugin.Http { def get(url: String, headers: Map[String, String]) = Left("offline") }
      def log = new Plugin.Log { def info(m: String) = (); def warn(m: String) = () }
      def env(name: String) = None
      def secret(s: Secret) = Left(_root_.com.histmersion.plugin.Plugin.Context.SecretResultPart1("none"))
      def secretVaults = Registry.refusing(_root_.com.histmersion.plugin.Registry.RefusingWhy("no vaults"))
    val f = new SourceFetcher:
      val access = Access.Subscription
      def fetch(work: WorkKey, ctx: Fetch.Context) = Fetch.Refused(_root_.com.histmersion.plugin.Fetch.Refused.Why("no entitlement"))
    assert(ctx.sourceFetchers.add(_root_.com.histmersion.plugin.Registry.AddId("portal"), f).left.exists((_).value.contains("no acquisition chain")))

  test("a FetchedFile's licence is optional"):
    val pdf = "%PDF-1.7".getBytes("US-ASCII")
    val none = FetchedFile(_root_.com.histmersion.plugin.FetchedFile.Source("oabooks"), Some(_root_.com.histmersion.plugin.FetchedFile.Url("https://example.org/b.pdf")), pdf, _root_.com.histmersion.plugin.FetchedFile.Extension("pdf"))
    assertEquals(none.licence, None)
    val withIt = FetchedFile(_root_.com.histmersion.plugin.FetchedFile.Source("oabooks"), None, pdf, _root_.com.histmersion.plugin.FetchedFile.Extension("pdf"), Some(Licence("CC-BY-4.0")))
    assertEquals(withIt.licence.map(l => l.value: String), Some("CC-BY-4.0"))
    assertEquals(
      none.copy(licence = Some(Licence("public-domain"))).licence.map(l => l.value: String),
      Some("public-domain")
    )
