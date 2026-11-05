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
    def secret(s: Secret) = Left("none")
    def secretVaults = Registry.refusing("no vaults")

  /** A read-only collection with one item. */
  private object Shelf extends SourceCollection:
    def title = "Shelf"
    private val one = CollectionItem(CollectionId("b1"), Some("A book"), List("An Author"))
    def search(query: CollectionQuery) = Right(List(one))
    def item(id: CollectionId) = Right(Option.when(id == one.id)(one))
    def file(item: CollectionItem, format: Option[String]) = Left("no files")
    def original(item: CollectionItem, file: CollectionFile) = Left("no files")

  test("a context written before source collections refuses one, naming why"):
    assertEquals(
      EarlyContext.sourceCollections.add("shelf", Shelf),
      Left("'shelf': this host has no source collections")
    )

  test("a collection reads only, admits every item, has no cover, is reachable and finds nothing to audit by default"):
    val item = Shelf.item(CollectionId("b1")).toOption.flatten.getOrElse(fail("no item"))
    assertEquals(Shelf.capabilities, Set(CollectionCapability.Read))
    assertEquals(Shelf.admits(item), Right(()))
    assertEquals(Shelf.cover(item), Right(None))
    assertEquals(Shelf.reach, Right(()))
    assertEquals(Shelf.audit(List(CollectionRecord("k", item.id, "A book", Nil, None))), Right(Nil))
    assertEquals(Shelf.item(CollectionId("nope")), Right(None))

  test("every write is refused by default, with the collection's reason"):
    val meta = CollectionMetadata("k", "A book", List("An Author"))
    val file = FetchedFile("test", None, Array[Byte](1), "pdf")
    val id = CollectionId("b1")
    assertEquals(Shelf.add(meta, file), Left("Shelf does not take new items: it is read-only"))
    assertEquals(Shelf.replace(id, "k", file), Left("Shelf does not take new files: it is read-only"))
    assertEquals(Shelf.update(id, meta), Left("Shelf does not rewrite its items: it is read-only"))
    assertEquals(Shelf.updateAll(List(CollectionLink(id, meta)), apply = false).isLeft, true)
    assertEquals(Shelf.set(id, List("title" -> "B")).isLeft, true)

  test("an item and a query carry what they were given, with empty defaults"):
    val i = CollectionItem(CollectionId("x"))
    assertEquals((i.title, i.authors, i.files, i.restricted, i.identifiers), (None, Nil, Nil, false, Map.empty))
    assertEquals(CollectionQuery(None).limit, None)
    assertEquals(CollectionId("x").value, "x")
