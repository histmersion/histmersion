package com.histmersion
package plugin

/** The gazetteer extension point added within SPI 2: a context written before it refuses a source (so a plugin built
  * against it still compiles and links), and a place's defaults are the plain ones.
  */
class GazetteerSpiSuite extends munit.FunSuite:

  /** A context as a plugin written before gazetteer sources existed has it. */
  private object EarlyContext extends Plugin.Context:
    def http = new Plugin.Http { def get(url: Plugin.Http.GetUrl, headers: Map[Plugin.Http.GetHeadersPart1, Plugin.Http.GetHeadersPart2]): Either[Plugin.Http.GetResultPart1, Plugin.HttpResponse.Body] = Left(Plugin.Http.GetResultPart1("offline")) }
    def log = new Plugin.Log { def info(m: Plugin.Log.InfoMessage): Unit = (); def warn(m: Plugin.Log.WarnMessage): Unit = () }
    def env(name: Plugin.Context.EnvName): Option[Plugin.Context.EnvResult] = None
    def secret(s: Secret): Either[Plugin.Context.SecretResultPart1, Plugin.Context.SecretResultPart2] = Left(Plugin.Context.SecretResultPart1("none"))
    def secretVaults = Registry.refusing(Registry.RefusingWhy("no vaults"))

  private object Nowhere extends GazetteerSource:
    def title = GazetteerSource.TitleResult("Nowhere")
    def rights = GazetteerRights(Licence("CC0-1.0"), GazetteerRights.Credit("Nowhere atlas"))
    def fetch(http: Plugin.Http) = Right(List(GazetteerPlace(GazetteerPlace.Id("nowhere_1"), GazetteerPlace.Label("Nowhere"), GazetteerPlace.Kind("region"))))

  test("a host without gazetteers refuses a source, saying why"):
    assertEquals(
      ((EarlyContext.gazetteerSources.add(Registry.AddId("nowhere"), Nowhere)).left.map(refactorError => (refactorError).value): Either[String, Unit]),
      Left("'nowhere': this host has no gazetteer import")
    )

  test("a place, its names and relations default to always valid, no shape, the dataset's credit"):
    val p = Nowhere.fetch(EarlyContext.http).toOption.get.head
    assertEquals((p.geometry, p.period, p.open, p.source, p.properties), (None, None, None, None, None))
    assertEquals((p.names, p.relations), (Nil, Nil))
    assertEquals(GazetteerRelation(GazetteerRelation.ChildId("a"), GazetteerRelation.ParentId("b")), GazetteerRelation(GazetteerRelation.ChildId("a"), GazetteerRelation.ParentId("b"), GazetteerRelation.Kind("part_of"), None, None))
    assertEquals(GazetteerName(GazetteerName.Name("Hatti")), GazetteerName(GazetteerName.Name("Hatti"), GazetteerName.Lang(""), None, None))
    assertEquals(((Nowhere.description).value: String), "")
    assertEquals(Nowhere.rights.licenceUrl, None)
