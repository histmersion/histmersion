package com.histmersion
package plugin

import com.histmersion.util.problem.Text.sentence

import com.histmersion.util.problem.{External, Problem}
import com.histmersion.lint.{tolerated, LintRule}

/** The gazetteer extension point added within SPI 2: a context written before it refuses a source (so a plugin built
  * against it still compiles and links), and a place's defaults are the plain ones.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class GazetteerSpiSuite extends munit.FunSuite:

  /** A context as a plugin written before gazetteer sources existed has it. */
  private object EarlyContext extends Plugin.Context:
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
    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    def env(name: EnvVar.Name): Option[EnvVar.Value] = None
    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    def secret(s: Secret): Either[Problem, Secret.Resolved] = Left(
      Problem.Plugin.Said(Problem.Plugin.Id("test"), Problem.Plugin.Claim.Failed, External.PluginSaid.of("none"))
    )
    def secretVaults =
      Registry.refusing(Problem.Plugin.Vault(Problem.Plugin.Id("test"), Problem.Plugin.Retrieval.NoVault))

  private object Nowhere extends GazetteerSource:
    def title = GazetteerSource.Title("Nowhere")
    def rights = GazetteerRights(Licence("CC0-1.0"), GazetteerRights.Credit("Nowhere atlas"))
    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    def fetch(http: Plugin.Http) = Right(
      List(
        GazetteerPlace(GazetteerPlace.Id("nowhere_1"), GazetteerPlace.Label("Nowhere"), GazetteerPlace.Kind("region"))
      )
    )

  test("a host without gazetteers refuses a source, saying why"):
    assertEquals(
      ((EarlyContext.gazetteerSources
        .add(Registry.AddId("nowhere"), Nowhere))
        .left
        .map(refactorError => (refactorError).sentence): Either[String, Unit]),
      Left("this server has no gazetteer import")
    )

  test("a place, its names and relations default to always valid, no shape, the dataset's credit"):
    val p = Nowhere.fetch(EarlyContext.http).toOption.get.head
    assertEquals((p.geometry, p.period, p.open, p.source, p.properties), (None, None, None, None, None))
    assertEquals((p.names, p.relations), (Nil, Nil))
    assertEquals(
      GazetteerRelation(GazetteerRelation.ChildId("a"), GazetteerRelation.ParentId("b")),
      GazetteerRelation(
        GazetteerRelation.ChildId("a"),
        GazetteerRelation.ParentId("b"),
        GazetteerRelation.Kind("part_of"),
        None,
        None
      )
    )
    assertEquals(
      GazetteerName(GazetteerName.Name("Hatti")),
      GazetteerName(GazetteerName.Name("Hatti"), GazetteerName.Lang(""), None, None)
    )
    assertEquals(((Nowhere.description).value: String), "")
    assertEquals(Nowhere.rights.licenceUrl, None)
