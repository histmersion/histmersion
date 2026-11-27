package com.histmersion
package pluginhost

import com.histmersion.util.problem.{External, Problem}
import com.histmersion.util.problem.Text.sentences
import com.histmersion.util.problem.Text.sentence
import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given
import hearth.kindlings.sconfigderivation.ConfigReader
import org.ekrich.config.ConfigFactory
import com.histmersion.lint.{tolerated, LintRule}

/** SPI-11 on the host: a plugin registers a source collection (its report says so); a third party may not take the name
  * of one of Histmersion's own; and every call the host makes is guarded, a write refused unless the collection
  * declares it.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class HostCollectionSuite extends munit.FunSuite:

  private object Shelf extends SourceCollection:
    def title = SourceCollection.Title("Shelf")
    private val one = CollectionItem(
      CollectionId("b1"),
      Some(CollectionItem.Title("A book")),
      List(CollectionItem.Authors("An Author"))
    )
    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    def search(query: CollectionQuery) = Right(List(one))
    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def item(id: CollectionId) = if id.value == "boom" then throw IllegalStateException("broken") else Right(Some(one))
    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    def file(item: CollectionItem, format: Option[SourceCollection.FileFormat]) = Right(
      CollectionFile(CollectionFile.Name("a.pdf"))
    )
    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def original(item: CollectionItem, file: CollectionFile) = Right(
      FetchedFile(FetchedFile.Source("t"), None, FetchedFile.Bytes(Array[Byte](1)), FetchedFile.Extension("pdf"))
    )
    override def add(metadata: CollectionMetadata, file: FetchedFile) = Right(CollectionId("written"))
    override def readOnlyReason =
      Problem.Plugin.Said(
        Problem.Plugin.Id("zq-shelf"),
        Problem.Plugin.Claim.Rejected,
        External.PluginSaid("the shelf is full")
      )

  final private case class NoSettings() derives ConfigReader

  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  final private class ShelfPlugin(id: Plugin.Info.Id, name: Registry.AddId) extends Plugin:
    type Config = NoSettings
    val info = Plugin.Info(id, Plugin.Info.Version("0.1.0"), Plugin.Info.SpiVersion(Spi.Version))
    def register(c: NoSettings, ctx: Plugin.Context) =
      ctx.sourceCollections.add(name, Shelf)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def build(p: Plugin, firstParty: Plugin.Info.Id => Boolean = _ => false) =
    PluginHost.build(
      List(p),
      ConfigFactory.parseString(s"histmersion.plugins.${p.info.id}.enabled = true"),
      ConfigFactory.empty,
      HostServices(
        () =>
          new Plugin.Http {
            def get(u: Plugin.Http.Url, h: Map[Header.Name, Header.Value]) =
              Left(
                Problem.Upstream.Unreachable.here(
                  Problem.Upstream.Name("x"),
                  Problem.Upstream.Url("https://x.example"),
                  External.Reported("offline")
                )
              )
          },
        ConfigFactory.empty,
        (_, _, _) => ()
      ),
      firstParty = firstParty
    )

  test("a registered collection is in the registry and the plugin's report"):
    val g = build(ShelfPlugin(Plugin.Info.Id("zq-shelf"), Registry.AddId("shelf")))
    assertEquals(g.registries.sourceCollections.keySet, Set("shelf"))
    assertEquals(g.report.plugins.head.provides, List("source-collection:shelf"))

  test("Histmersion's own collection names are kept for the first-party plugins"):
    val third = build(ShelfPlugin(Plugin.Info.Id("zq-shelf"), Registry.AddId("calibre")))
    assert(
      third.report.plugins.head.errors == List(
        Problem.Conflict.AlreadyExists(Problem.TypeName.named[SourceCollection], Problem.Raw("calibre"))
      ),
      third.report
    )
    val first = build(
      ShelfPlugin(Plugin.Info.Id("calibre"), Registry.AddId("calibre")),
      id => ReservedNames.firstPartyPlugins.contains(ReservedNames.Name(id.value))
    )
    assertEquals(first.registries.sourceCollections.keySet, Set("calibre"))

  test("the host refuses a write the collection does not declare, and catches what it throws"):
    val c = HostCollection(HostCollection.Name("shelf"), Shelf)
    assertEquals(
      c.add(
        CollectionMetadata(
          CollectionMetadata.Key("k"),
          CollectionMetadata.Title("T"),
          List(CollectionMetadata.Authors("A"))
        ),
        FetchedFile(FetchedFile.Source("t"), None, FetchedFile.Bytes(Array[Byte](1)), FetchedFile.Extension("pdf"))
      ).left
        .map(_.sentences),
      Left(Vector("zq-shelf (rejected): the shelf is full"))
    )
    assert(
      c.item(CollectionId("boom"))
        .left
        .exists(_.exists {
          case Problem.Internal.Unexpected(_, where, _, _) => where == Problem.Where("source collection shelf")
          case _                                           => false
        })
    )
    assertEquals(c.search(CollectionQuery(None)).map(_.size), Right(1))
    assert(
      c.updateAll(Nil, apply = SourceCollection.UpdateAllApply(false)).isLeft,
      "the collection's own default refusal"
    )
