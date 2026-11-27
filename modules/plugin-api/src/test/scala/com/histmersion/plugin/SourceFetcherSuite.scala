package com.histmersion
package plugin

import com.histmersion.util.problem.Text.sentence

import com.histmersion.util.problem.{External, Problem}
import com.histmersion.lint.{tolerated, LintRule}

/** The source-fetching part of the SPI (docs/research-plugins.md §5.1). */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class SourceFetcherSuite extends munit.FunSuite:

  test("a WorkKey with no identifier is empty; title, authors, year and format identify nothing"):
    assert(WorkKey().isEmpty)
    assert(
      (WorkKey(
        title = Some(WorkKey.Title("Black Athena")),
        authors = List(WorkKey.Authors("Martin Bernal")),
        year = Some(Year(1987)),
        format = Some(WorkKey.Format("pdf"))
      ).isEmpty)
    )
    assert(!(WorkKey(doi = Some(Doi("10.1038/nature14317"))).isEmpty))
    assert(!(WorkKey(archive = Some(WorkKey.Archive("racesofeurope"))).isEmpty))
    assert(!(WorkKey(pmcid = Some(WorkKey.Pmcid("PMC6091220"))).isEmpty))

  test("a refusing registry takes nothing and says why, naming the id"):
    val r = Registry.refusing[SourceFetcher](Problem.Config.Unavailable(Problem.Config.Capability.AcquisitionChain))
    val f = new SourceFetcher:
      val access = Access.Open
      def fetch(work: WorkKey, ctx: Fetch.Context) = Fetch.Miss(
        Fetch.Absence.NotHeld,
        Problem.Plugin.Said(Problem.Plugin.Id("test"), Problem.Plugin.Claim.Failed, External.PluginSaid.of("nothing"))
      )
    assertEquals(
      ((r
        .add(Registry.AddId("portal"), f))
        .left
        .map(refactorError => (refactorError).sentence): Either[String, Unit]),
      Left("this server has no acquisition chain")
    )

  test("a context written before source fetchers existed still compiles and refuses a fetcher"):
    val ctx = new Plugin.Context:
      def http = new Plugin.Http {
        def get(
            url: Plugin.Http.Url,
            headers: Map[Header.Name, Header.Value]
        ): Either[Problem, Plugin.HttpResponse.Body] =
          Left(Problem.Config.Unavailable(Problem.Config.Capability.Network))
      }
      def log = new Plugin.Log {
        def info(m: Plugin.Log.Line): Unit = (); def warn(m: Plugin.Log.Line): Unit = ()
      }
      def env(name: EnvVar.Name): Option[EnvVar.Value] = None
      def secret(s: Secret): Either[Problem, Secret.Resolved] =
        Left(
          Problem.Plugin.Said(Problem.Plugin.Id("test"), Problem.Plugin.Claim.Failed, External.PluginSaid.of("none"))
        )
      def secretVaults =
        Registry.refusing(Problem.Plugin.Vault(Problem.Plugin.Id("test"), Problem.Plugin.Retrieval.NoVault))
    val f = new SourceFetcher:
      val access = Access.Subscription
      def fetch(work: WorkKey, ctx: Fetch.Context) = Fetch.Refused(
        Fetch.Refusal.NoEntitlement,
        Problem.Plugin
          .Said(Problem.Plugin.Id("test"), Problem.Plugin.Claim.Failed, External.PluginSaid.of("no entitlement"))
      )
    assert(
      ctx.sourceFetchers.add(Registry.AddId("portal"), f).left.exists(_.sentence.contains("no acquisition chain"))
    )

  test("a FetchedFile's licence is optional"):
    val pdf = "%PDF-1.7".getBytes("US-ASCII")
    val none = FetchedFile(
      FetchedFile.Source("oabooks"),
      Some(FetchedFile.Url("https://example.org/b.pdf")),
      FetchedFile.Bytes(pdf),
      FetchedFile.Extension("pdf")
    )
    assertEquals(none.licence, None)
    val withIt = FetchedFile(
      FetchedFile.Source("oabooks"),
      None,
      FetchedFile.Bytes(pdf),
      FetchedFile.Extension("pdf"),
      Some(Licence("CC-BY-4.0"))
    )
    assertEquals(withIt.licence.map(l => l.value: String), Some("CC-BY-4.0"))
    assertEquals(
      none.copy(licence = Some(Licence("public-domain"))).licence.map(l => l.value: String),
      Some("public-domain")
    )
