package com.histmersion
package plugin

/** SPI-11, the source collections: a context written before them refuses a collection, and a collection that declares
  * nothing but reading refuses every write by default, naming why.
  */
class SourceCollectionSpiSuite extends munit.FunSuite:

  private object Bodies extends Plugin.Http:
    def get(url: Plugin.Http.GetUrl, headers: Map[Plugin.Http.GetHeadersPart1, Plugin.Http.GetHeadersPart2]): Either[Plugin.Http.GetResultPart1, Plugin.HttpResponse.Body] = Left(Plugin.Http.GetResultPart1("offline"))

  private object EarlyContext extends Plugin.Context:
    def http = Bodies
    def log = new Plugin.Log { def info(m: Plugin.Log.InfoMessage): Unit = (); def warn(m: Plugin.Log.WarnMessage): Unit = () }
    def env(name: Plugin.Context.EnvName): Option[Plugin.Context.EnvResult] = None
    def secret(s: Secret): Either[Plugin.Context.SecretResultPart1, Plugin.Context.SecretResultPart2] = Left(Plugin.Context.SecretResultPart1("none"))
    def secretVaults = Registry.refusing(Registry.RefusingWhy("no vaults"))

  /** A read-only collection with one item. */
  private object Shelf extends SourceCollection:
    def title: SourceCollection.TitleResult = SourceCollection.TitleResult("Shelf")
    private val one = CollectionItem(CollectionId("b1"), Some(CollectionItem.Title("A book")), List(CollectionItem.Authors("An Author")))
    def search(query: CollectionQuery): Either[SourceCollection.SearchResultPart1, List[CollectionItem]] = Right(List(one))
    def item(id: CollectionId): Either[SourceCollection.ItemResultPart1, Option[CollectionItem]] = Right(Option.when(id == one.id)(one))
    def file(item: CollectionItem, format: Option[SourceCollection.FileFormat]): Either[SourceCollection.FileResultPart1, CollectionFile] = Left(SourceCollection.FileResultPart1("no files"))
    def original(item: CollectionItem, file: CollectionFile): Either[SourceCollection.OriginalResultPart1, FetchedFile] = Left(SourceCollection.OriginalResultPart1("no files"))

  test("a context written before source collections refuses one, naming why"):
    assertEquals(
      ((EarlyContext.sourceCollections.add(Registry.AddId("shelf"), Shelf)).left.map(refactorError3 => (refactorError3).value): Either[String, Unit]),
      Left("'shelf': this host has no source collections")
    )

  test("a collection reads only, admits every item, has no cover, is reachable and finds nothing to audit by default"):
    val item = Shelf.item(CollectionId("b1")).toOption.flatten.getOrElse(fail("no item"))
    assertEquals(Shelf.capabilities, Set(CollectionCapability.Read))
    assertEquals(Shelf.admits(item), Right(()))
    assertEquals(Shelf.cover(item), Right(None))
    assertEquals(Shelf.reach, Right(()))
    assertEquals(Shelf.audit(List(CollectionRecord(CollectionRecord.Key("k"), item.id, CollectionRecord.Title("A book"), Nil, None))), Right(Nil))
    assertEquals(Shelf.item(CollectionId("nope")), Right(None))

  test("every write is refused by default, with the collection's reason"):
    val meta = CollectionMetadata(CollectionMetadata.Key("k"), CollectionMetadata.Title("A book"), List(CollectionMetadata.Authors("An Author")))
    val file = FetchedFile(FetchedFile.Source("test"), None, FetchedFile.Bytes(Array[Byte](1)), FetchedFile.Extension("pdf"))
    val id = CollectionId("b1")
    assertEquals(((Shelf.add(meta, file)).left.map(refactorError => (refactorError).value): Either[String, CollectionId]), Left("Shelf does not take new items: it is read-only"))
    assertEquals((((Shelf.replace(id, SourceCollection.ReplaceKey("k"), file)).left.map(refactorError4 => (refactorError4).value)).map(refactorValue2 => (refactorValue2).value): Either[String, String]), Left("Shelf does not take new files: it is read-only"))
    assertEquals(((Shelf.update(id, meta)).left.map(refactorError2 => (refactorError2).value): Either[String, List[CollectionChange]]), Left("Shelf does not rewrite its items: it is read-only"))
    assertEquals(Shelf.updateAll(List(CollectionLink(id, meta)), apply = SourceCollection.UpdateAllApply(false)).isLeft, true)
    assertEquals(Shelf.set(id, List({ val refactorTuple = "title" -> "B"; (SourceCollection.SetFieldsPart1(refactorTuple._1), SourceCollection.SetFieldsPart2(refactorTuple._2)) })).isLeft, true)

  test("an item and a query carry what they were given, with empty defaults"):
    val i = CollectionItem(CollectionId("x"))
    assertEquals(({ val refactorTuple2 = (i.title, i.authors, i.files, i.restricted, i.identifiers); ((refactorTuple2._1).map(refactorValue => (refactorValue).value), (refactorTuple2._2).map(refactorValue3 => (refactorValue3).value), refactorTuple2._3, (refactorTuple2._4).value, (refactorTuple2._5).iterator.map { case (refactorKey, refactorItem) => ((refactorKey).value, (refactorItem).value) }.toMap) }: (Option[String], List[String], List[CollectionFile], Boolean, Map[String, String])), (None, Nil, Nil, false, Map.empty))
    assertEquals(CollectionQuery(None).limit, None)
    assertEquals(CollectionId("x").value, "x")
