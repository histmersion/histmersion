package com.histmersion
package plugin

/** The source-fetching part of the SPI (docs/research-plugins.md §5.1). */
class SourceFetcherSuite extends munit.FunSuite:

  test("a WorkKey with no identifier is empty; title, authors, year and format identify nothing"):
    assert((WorkKey().isEmpty).value)
    assert(
      (WorkKey(
        title = Some(WorkKey.Title("Black Athena")),
        authors = List(WorkKey.Authors("Martin Bernal")),
        year = Some(Year(1987)),
        format = Some(WorkKey.Format("pdf"))
      ).isEmpty).value
    )
    assert(!(WorkKey(doi = Some(Doi("10.1038/nature14317"))).isEmpty).value)
    assert(!(WorkKey(archive = Some(WorkKey.Archive("racesofeurope"))).isEmpty).value)
    assert(!(WorkKey(pmcid = Some(WorkKey.Pmcid("PMC6091220"))).isEmpty).value)

  test("a refusing registry takes nothing and says why, naming the id"):
    val r = Registry.refusing[SourceFetcher](Registry.RefusingWhy("no chain here"))
    val f = new SourceFetcher:
      val access = Access.Open
      def fetch(work: WorkKey, ctx: Fetch.Context) = Fetch.Miss(Fetch.Miss.Why("nothing"))
    assertEquals(((r.add(Registry.AddId("portal"), f)).left.map(refactorError => (refactorError).value): Either[String, Unit]), Left("'portal': no chain here"))

  test("a context written before source fetchers existed still compiles and refuses a fetcher"):
    val ctx = new Plugin.Context:
      def http = new Plugin.Http { def get(url: Plugin.Http.GetUrl, headers: Map[Plugin.Http.GetHeadersPart1, Plugin.Http.GetHeadersPart2]): Either[Plugin.Http.GetResultPart1, Plugin.HttpResponse.Body] = Left(Plugin.Http.GetResultPart1("offline")) }
      def log = new Plugin.Log { def info(m: Plugin.Log.InfoMessage): Unit = (); def warn(m: Plugin.Log.WarnMessage): Unit = () }
      def env(name: Plugin.Context.EnvName): Option[Plugin.Context.EnvResult] = None
      def secret(s: Secret): Either[Plugin.Context.SecretResultPart1, Plugin.Context.SecretResultPart2] = Left(Plugin.Context.SecretResultPart1("none"))
      def secretVaults = Registry.refusing(Registry.RefusingWhy("no vaults"))
    val f = new SourceFetcher:
      val access = Access.Subscription
      def fetch(work: WorkKey, ctx: Fetch.Context) = Fetch.Refused(Fetch.Refused.Why("no entitlement"))
    assert(ctx.sourceFetchers.add(Registry.AddId("portal"), f).left.exists((_).value.contains("no acquisition chain")))

  test("a FetchedFile's licence is optional"):
    val pdf = "%PDF-1.7".getBytes("US-ASCII")
    val none = FetchedFile(FetchedFile.Source("oabooks"), Some(FetchedFile.Url("https://example.org/b.pdf")), FetchedFile.Bytes(pdf), FetchedFile.Extension("pdf"))
    assertEquals(none.licence, None)
    val withIt = FetchedFile(FetchedFile.Source("oabooks"), None, FetchedFile.Bytes(pdf), FetchedFile.Extension("pdf"), Some(Licence("CC-BY-4.0")))
    assertEquals(withIt.licence.map(l => l.value: String), Some("CC-BY-4.0"))
    assertEquals(
      none.copy(licence = Some(Licence("public-domain"))).licence.map(l => l.value: String),
      Some("public-domain")
    )
