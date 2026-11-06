package com.histmersion.plugin

/** The gazetteer extension point added within SPI 2: a context written before it refuses a source (so a plugin built
  * against it still compiles and links), and a place's defaults are the plain ones.
  */
class GazetteerSpiSuite extends munit.FunSuite:

  /** A context as a plugin written before gazetteer sources existed has it. */
  private object EarlyContext extends Plugin.Context:
    def http = new Plugin.Http { def get(url: _root_.com.histmersion.plugin.Plugin.Http.GetUrl, headers: Map[_root_.com.histmersion.plugin.Plugin.Http.GetHeadersPart1, _root_.com.histmersion.plugin.Plugin.Http.GetHeadersPart2]): Either[_root_.com.histmersion.plugin.Plugin.Http.GetResultPart1, _root_.com.histmersion.plugin.Plugin.HttpResponse.Body] = Left(_root_.com.histmersion.plugin.Plugin.Http.GetResultPart1("offline")) }
    def log = new Plugin.Log { def info(m: _root_.com.histmersion.plugin.Plugin.Log.InfoMessage): Unit = (); def warn(m: _root_.com.histmersion.plugin.Plugin.Log.WarnMessage): Unit = () }
    def env(name: _root_.com.histmersion.plugin.Plugin.Context.EnvName): _root_.scala.Option[_root_.com.histmersion.plugin.Plugin.Context.EnvResult] = None
    def secret(s: Secret): Either[_root_.com.histmersion.plugin.Plugin.Context.SecretResultPart1, _root_.com.histmersion.plugin.Plugin.Context.SecretResultPart2] = Left(_root_.com.histmersion.plugin.Plugin.Context.SecretResultPart1("none"))
    def secretVaults = Registry.refusing(_root_.com.histmersion.plugin.Registry.RefusingWhy("no vaults"))

  private object Nowhere extends GazetteerSource:
    def title = _root_.com.histmersion.plugin.GazetteerSource.TitleResult("Nowhere")
    def rights = GazetteerRights(Licence("CC0-1.0"), _root_.com.histmersion.plugin.GazetteerRights.Credit("Nowhere atlas"))
    def fetch(http: Plugin.Http) = Right(List(GazetteerPlace(_root_.com.histmersion.plugin.GazetteerPlace.Id("nowhere_1"), _root_.com.histmersion.plugin.GazetteerPlace.Label("Nowhere"), _root_.com.histmersion.plugin.GazetteerPlace.Kind("region"))))

  test("a host without gazetteers refuses a source, saying why"):
    assertEquals(
      ((EarlyContext.gazetteerSources.add(_root_.com.histmersion.plugin.Registry.AddId("nowhere"), Nowhere)).left.map(refactorError => (refactorError).value): Either[String, Unit]),
      Left("'nowhere': this host has no gazetteer import")
    )

  test("a place, its names and relations default to always valid, no shape, the dataset's credit"):
    val p = Nowhere.fetch(EarlyContext.http).toOption.get.head
    assertEquals((p.geometry, p.period, p.open, p.source, p.properties), (None, None, None, None, None))
    assertEquals((p.names, p.relations), (Nil, Nil))
    assertEquals(GazetteerRelation(_root_.com.histmersion.plugin.GazetteerRelation.ChildId("a"), _root_.com.histmersion.plugin.GazetteerRelation.ParentId("b")), GazetteerRelation(_root_.com.histmersion.plugin.GazetteerRelation.ChildId("a"), _root_.com.histmersion.plugin.GazetteerRelation.ParentId("b"), _root_.com.histmersion.plugin.GazetteerRelation.Kind("part_of"), None, None))
    assertEquals(GazetteerName(_root_.com.histmersion.plugin.GazetteerName.Name("Hatti")), GazetteerName(_root_.com.histmersion.plugin.GazetteerName.Name("Hatti"), _root_.com.histmersion.plugin.GazetteerName.Lang(""), None, None))
    assertEquals(((Nowhere.description).value: String), "")
    assertEquals(Nowhere.rights.licenceUrl, None)
