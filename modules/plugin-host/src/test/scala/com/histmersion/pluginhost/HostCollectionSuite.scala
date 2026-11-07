package com.histmersion.pluginhost

import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given
import hearth.kindlings.sconfigderivation.ConfigReader
import org.ekrich.config.ConfigFactory

/** SPI-11 on the host: a plugin registers a source collection (its report says so); a third party may not take the name
  * of one of Histmersion's own; and every call the host makes is guarded, a write refused unless the collection
  * declares it.
  */
class HostCollectionSuite extends munit.FunSuite:

  private object Shelf extends SourceCollection:
    def title = com.histmersion.plugin.SourceCollection.TitleResult("Shelf")
    private val one = CollectionItem(CollectionId("b1"), Some(com.histmersion.plugin.CollectionItem.Title("A book")), List(com.histmersion.plugin.CollectionItem.Authors("An Author")))
    def search(query: CollectionQuery) = Right(List(one))
    def item(id: CollectionId) = if id.value == "boom" then throw IllegalStateException("broken") else Right(Some(one))
    def file(item: CollectionItem, format: Option[String]) = Right(CollectionFile(com.histmersion.plugin.CollectionFile.Name("a.pdf")))
    def original(item: CollectionItem, file: CollectionFile) = Right(FetchedFile(com.histmersion.plugin.FetchedFile.Source("t"), None, Array[Byte](1), com.histmersion.plugin.FetchedFile.Extension("pdf")))
    override def add(metadata: CollectionMetadata, file: FetchedFile) = Right(CollectionId("written"))
    override def readOnlyReason = com.histmersion.plugin.SourceCollection.ReadOnlyReasonResult("the shelf is full")

  final private case class NoSettings() derives ConfigReader

  final private class ShelfPlugin(id: String, name: String) extends Plugin:
    type Config = NoSettings
    val info = Plugin.Info(id, com.histmersion.plugin.Plugin.Info.Version("0.1.0"), Spi.Version)
    def register(c: NoSettings, ctx: Plugin.Context) = ctx.sourceCollections.add(name, Shelf)

  private def build(p: Plugin, firstParty: String => Boolean = _ => false) =
    PluginHost.build(
      List(p),
      ConfigFactory.parseString(s"histmersion.plugins.${p.info.id}.enabled = true"),
      ConfigFactory.empty,
      HostServices(
        () => new Plugin.Http { def get(u: String, h: Map[String, String]) = Left("offline") },
        ConfigFactory.empty,
        (_, _, _) => ()
      ),
      firstParty = firstParty
    )

  test("a registered collection is in the registry and the plugin's report"):
    val g = build(ShelfPlugin("zq-shelf", "shelf"))
    assertEquals(g.registries.sourceCollections.keySet, Set("shelf"))
    assertEquals(g.report.plugins.head.provides, List("source-collection:shelf"))

  test("Histmersion's own collection names are kept for the first-party plugins"):
    val third = build(ShelfPlugin("zq-shelf", "calibre"))
    assert(third.report.plugins.head.error.exists(_.contains("Histmersion's own source collections")), third.report)
    val first = build(ShelfPlugin("calibre", "calibre"), ReservedNames.firstPartyPlugins)
    assertEquals(first.registries.sourceCollections.keySet, Set("calibre"))

  test("the host refuses a write the collection does not declare, and catches what it throws"):
    val c = HostCollection(com.histmersion.pluginhost.HostCollection.Name("shelf"), Shelf)
    assertEquals(
      c.add(CollectionMetadata(com.histmersion.plugin.CollectionMetadata.Key("k"), com.histmersion.plugin.CollectionMetadata.Title("T"), List(com.histmersion.plugin.CollectionMetadata.Authors("A"))), FetchedFile(com.histmersion.plugin.FetchedFile.Source("t"), None, Array[Byte](1), com.histmersion.plugin.FetchedFile.Extension("pdf"))),
      Left("Shelf is read-only here: the shelf is full")
    )
    assert(c.item(CollectionId("boom")).left.exists(_.contains("source collection 'shelf' failed")))
    assertEquals(c.search(CollectionQuery(None)).map(_.size), Right(1))
    assert(c.updateAll(Nil, apply = com.histmersion.plugin.SourceCollection.UpdateAllApply(false)).isLeft, "the collection's own default refusal")
