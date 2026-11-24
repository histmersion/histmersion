package com.histmersion
package plugin

import com.histmersion.util.problem.Text.{sentence, sentences}

import com.histmersion.util.problem.{External, Problem}
import com.histmersion.lint.{LintRule, tolerated}

/** SPI-11, the source collections: a context written before them refuses a collection, and a collection that declares
  * nothing but reading refuses every write by default, naming why.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class SourceCollectionSpiSuite extends munit.FunSuite:

  private object Bodies extends Plugin.Http:
    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    def get(
        url: Plugin.Http.Url,
        headers: Map[Header.Name, Header.Value]
    ): Either[Problem, Plugin.HttpResponse.Body] =
      Left(Problem.Config.Unavailable(Problem.Config.Capability.Network))

  private object EarlyContext extends Plugin.Context:
    def http = Bodies
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

  /** A read-only collection with one item. */
  private object Shelf extends SourceCollection:
    def title: SourceCollection.Title = SourceCollection.Title("Shelf")
    private val one = CollectionItem(
      CollectionId("b1"),
      Some(CollectionItem.Title("A book")),
      List(CollectionItem.Authors("An Author"))
    )
    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    def search(query: CollectionQuery): Either[Problem, List[CollectionItem]] = Right(
      List(one)
    )
    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    def item(id: CollectionId): Either[Problem, Option[CollectionItem]] = Right(
      Option.when(id == one.id)(one)
    )
    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    def file(
        item: CollectionItem,
        format: Option[SourceCollection.FileFormat]
    ): Either[Problem, CollectionFile] = Left(
      Problem.Plugin.Said(Problem.Plugin.Id("test"), Problem.Plugin.Claim.Failed, External.PluginSaid.of("no files"))
    )
    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    def original(
        item: CollectionItem,
        file: CollectionFile
    ): Either[Problem, FetchedFile] = Left(
      Problem.Plugin.Said(Problem.Plugin.Id("test"), Problem.Plugin.Claim.Failed, External.PluginSaid.of("no files"))
    )

  test("a context written before source collections refuses one, naming why"):
    assertEquals(
      ((EarlyContext.sourceCollections
        .add(Registry.AddId("shelf"), Shelf))
        .left
        .map(refactorError3 => refactorError3.sentences.mkString("; ")): Either[String, Unit]),
      Left("this server has no source collections")
    )

  test("a collection reads only, admits every item, has no cover, is reachable and finds nothing to audit by default"):
    val item = Shelf.item(CollectionId("b1")).toOption.flatten.getOrElse(fail("no item"))
    assertEquals(Shelf.capabilities, Set(CollectionCapability.Read))
    assertEquals(Shelf.admits(item), Right(()))
    assertEquals(Shelf.cover(item), Right(None))
    assertEquals(Shelf.reach, Right(()))
    assertEquals(
      Shelf.audit(
        List(CollectionRecord(CollectionRecord.Key("k"), item.id, CollectionRecord.Title("A book"), Nil, None))
      ),
      Right(Nil)
    )
    assertEquals(Shelf.item(CollectionId("nope")), Right(None))

  test("every write is refused by default, with the collection's reason"):
    val meta = CollectionMetadata(
      CollectionMetadata.Key("k"),
      CollectionMetadata.Title("A book"),
      List(CollectionMetadata.Authors("An Author"))
    )
    val file =
      FetchedFile(FetchedFile.Source("test"), None, FetchedFile.Bytes(Array[Byte](1)), FetchedFile.Extension("pdf"))
    val id = CollectionId("b1")
    assertEquals(
      ((Shelf
        .add(meta, file))
        .left
        .map(refactorError => refactorError.sentences.mkString("; ")): Either[String, CollectionId]),
      Left("the collection is read-only")
    )
    assertEquals(
      (((Shelf
        .replace(id, SourceCollection.ReplaceKey("k"), file))
        .left
        .map(refactorError4 => refactorError4.sentences.mkString("; ")))
        .map(refactorValue2 => (refactorValue2).value): Either[String, String]),
      Left("the collection is read-only")
    )
    assertEquals(
      ((Shelf
        .update(id, meta))
        .left
        .map(refactorError2 => refactorError2.sentences.mkString("; ")): Either[String, List[CollectionChange]]),
      Left("the collection is read-only")
    )
    assertEquals(
      Shelf.updateAll(List(CollectionLink(id, meta)), apply = SourceCollection.UpdateAllApply(false)).isLeft,
      true
    )
    assertEquals(
      Shelf
        .set(
          id,
          List {
            val refactorTuple = "title" -> "B";
            (SourceCollection.FieldName(refactorTuple._1), SourceCollection.FieldValue(refactorTuple._2))
          }
        )
        .isLeft,
      true
    )

  test("an item and a query carry what they were given, with empty defaults"):
    val i = CollectionItem(CollectionId("x"))
    assertEquals(
      ({
        val refactorTuple2 = (i.title, i.authors, i.files, i.restricted, i.identifiers);
        (
          (refactorTuple2._1).map(refactorValue => (refactorValue).value),
          (refactorTuple2._2).map(refactorValue3 => (refactorValue3).value),
          refactorTuple2._3,
          (refactorTuple2._4).value,
          (refactorTuple2._5).iterator.map { case (refactorKey, refactorItem) =>
            ((refactorKey).value, (refactorItem).value)
          }.toMap
        )
      }: (Option[String], List[String], List[CollectionFile], Boolean, Map[String, String])),
      (None, Nil, Nil, false, Map.empty)
    )
    assertEquals(CollectionQuery(None).limit, None)
    assertEquals(CollectionId("x").value, "x")
