package com.histmersion.plugin

/** SPI-11, the source collections: a context written before them refuses a collection, and a collection that declares
  * nothing but reading refuses every write by default, naming why.
  */
class SourceCollectionSpiSuite extends munit.FunSuite:

  private object Bodies extends Plugin.Http:
    def get(url: String, headers: Map[String, String]) = Left("offline")

  private object EarlyContext extends Plugin.Context:
    def http = Bodies
    def log = new Plugin.Log { def info(m: String) = (); def warn(m: String) = () }
    def env(name: String) = None
    def secret(s: Secret) = Left(_root_.com.histmersion.plugin.Plugin.Context.SecretResultPart1("none"))
    def secretVaults = Registry.refusing(_root_.com.histmersion.plugin.Registry.RefusingWhy("no vaults"))

  /** A read-only collection with one item. */
  private object Shelf extends SourceCollection:
    def title = _root_.com.histmersion.plugin.SourceCollection.TitleResult("Shelf")
    private val one = CollectionItem(CollectionId("b1"), Some(_root_.com.histmersion.plugin.CollectionItem.Title("A book")), List(_root_.com.histmersion.plugin.CollectionItem.Authors("An Author")))
    def search(query: CollectionQuery) = Right(List(one))
    def item(id: CollectionId) = Right(Option.when(id == one.id)(one))
    def file(item: CollectionItem, format: Option[String]) = Left("no files")
    def original(item: CollectionItem, file: CollectionFile) = Left(_root_.com.histmersion.plugin.SourceCollection.OriginalResultPart1("no files"))

  test("a context written before source collections refuses one, naming why"):
    assertEquals(
      ((EarlyContext.sourceCollections.add(_root_.com.histmersion.plugin.Registry.AddId("shelf"), Shelf)).left.map(refactorError3 => (refactorError3).value): Either[String, Unit]),
      Left("'shelf': this host has no source collections")
    )

  test("a collection reads only, admits every item, has no cover, is reachable and finds nothing to audit by default"):
    val item = Shelf.item(CollectionId("b1")).toOption.flatten.getOrElse(fail("no item"))
    assertEquals(Shelf.capabilities, Set(CollectionCapability.Read))
    assertEquals(Shelf.admits(item), Right(()))
    assertEquals(Shelf.cover(item), Right(None))
    assertEquals(Shelf.reach, Right(()))
    assertEquals(Shelf.audit(List(CollectionRecord(_root_.com.histmersion.plugin.CollectionRecord.Key("k"), item.id, _root_.com.histmersion.plugin.CollectionRecord.Title("A book"), Nil, None))), Right(Nil))
    assertEquals(Shelf.item(CollectionId("nope")), Right(None))

  test("every write is refused by default, with the collection's reason"):
    val meta = CollectionMetadata(_root_.com.histmersion.plugin.CollectionMetadata.Key("k"), _root_.com.histmersion.plugin.CollectionMetadata.Title("A book"), List(_root_.com.histmersion.plugin.CollectionMetadata.Authors("An Author")))
    val file = FetchedFile(_root_.com.histmersion.plugin.FetchedFile.Source("test"), None, _root_.com.histmersion.plugin.FetchedFile.Bytes(Array[Byte](1)), _root_.com.histmersion.plugin.FetchedFile.Extension("pdf"))
    val id = CollectionId("b1")
    assertEquals(((Shelf.add(meta, file)).left.map(refactorError => (refactorError).value): Either[String, com.histmersion.plugin.CollectionId]), Left("Shelf does not take new items: it is read-only"))
    assertEquals((((Shelf.replace(id, _root_.com.histmersion.plugin.SourceCollection.ReplaceKey("k"), file)).left.map(refactorError4 => (refactorError4).value)).map(refactorValue2 => (refactorValue2).value): Either[String, String]), Left("Shelf does not take new files: it is read-only"))
    assertEquals(((Shelf.update(id, meta)).left.map(refactorError2 => (refactorError2).value): Either[String, List[com.histmersion.plugin.CollectionChange]]), Left("Shelf does not rewrite its items: it is read-only"))
    assertEquals(Shelf.updateAll(List(CollectionLink(id, meta)), apply = _root_.com.histmersion.plugin.SourceCollection.UpdateAllApply(false)).isLeft, true)
    assertEquals(Shelf.set(id, List({ val refactorTuple = "title" -> "B"; (_root_.com.histmersion.plugin.SourceCollection.SetFieldsPart1(refactorTuple._1), _root_.com.histmersion.plugin.SourceCollection.SetFieldsPart2(refactorTuple._2)) })).isLeft, true)

  test("an item and a query carry what they were given, with empty defaults"):
    val i = CollectionItem(CollectionId("x"))
    assertEquals(({ val refactorTuple2 = (i.title, i.authors, i.files, i.restricted, i.identifiers); ((refactorTuple2._1).map(refactorValue => (refactorValue).value), (refactorTuple2._2).map(refactorValue3 => (refactorValue3).value), refactorTuple2._3, (refactorTuple2._4).value, (refactorTuple2._5).iterator.map { case (refactorKey, refactorItem) => ((refactorKey).value, (refactorItem).value) }.toMap) }: (Option[String], List[String], List[com.histmersion.plugin.CollectionFile], Boolean, Map[String, String])), (None, Nil, Nil, false, Map.empty))
    assertEquals(CollectionQuery(None).limit, None)
    assertEquals(CollectionId("x").value, "x")
