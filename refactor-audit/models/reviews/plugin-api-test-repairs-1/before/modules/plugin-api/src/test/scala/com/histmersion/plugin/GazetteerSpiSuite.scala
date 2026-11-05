package com.histmersion.plugin

/** The gazetteer extension point added within SPI 2: a context written before it refuses a source (so a plugin built
  * against it still compiles and links), and a place's defaults are the plain ones.
  */
class GazetteerSpiSuite extends munit.FunSuite:

  /** A context as a plugin written before gazetteer sources existed has it. */
  private object EarlyContext extends Plugin.Context:
    def http = new Plugin.Http { def get(url: String, headers: Map[String, String]) = Left("offline") }
    def log = new Plugin.Log { def info(m: String) = (); def warn(m: String) = () }
    def env(name: String) = None
    def secret(s: Secret) = Left("none")
    def secretVaults = Registry.refusing("no vaults")

  private object Nowhere extends GazetteerSource:
    def title = "Nowhere"
    def rights = GazetteerRights(Licence("CC0-1.0"), "Nowhere atlas")
    def fetch(http: Plugin.Http) = Right(List(GazetteerPlace("nowhere_1", "Nowhere", "region")))

  test("a host without gazetteers refuses a source, saying why"):
    assertEquals(
      EarlyContext.gazetteerSources.add("nowhere", Nowhere),
      Left("'nowhere': this host has no gazetteer import")
    )

  test("a place, its names and relations default to always valid, no shape, the dataset's credit"):
    val p = Nowhere.fetch(EarlyContext.http).toOption.get.head
    assertEquals((p.geometry, p.period, p.open, p.source, p.properties), (None, None, None, None, None))
    assertEquals((p.names, p.relations), (Nil, Nil))
    assertEquals(GazetteerRelation("a", "b"), GazetteerRelation("a", "b", "part_of", None, None))
    assertEquals(GazetteerName("Hatti"), GazetteerName("Hatti", "", None, None))
    assertEquals(Nowhere.description, "")
    assertEquals(Nowhere.rights.licenceUrl, None)
