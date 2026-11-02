package com.histmersion.plugins.fetchers

import com.histmersion.plugin.*
import com.histmersion.upstreams.archiveorg.ArchiveOrg
import com.histmersion.domain.library.acquisition.Endpoint

import java.nio.charset.StandardCharsets.UTF_8

/** archive.org as a read-only source collection: its search and items as collection items (lending-only kept as
  * `restricted`), the file an import takes, and no writing at all. Answers are canned; nothing reaches the network.
  */
class ArchiveOrgCollectionSuite extends munit.FunSuite:

  private val answers = Map(
    "https://archive.org/metadata/zqbook" ->
      """{"metadata":{"identifier":"zqbook","title":"A Book","creator":"An Author","date":"1901"},
        |"files":[{"name":"zqbook.pdf","format":"Text PDF","size":"40000"},{"name":"zqbook.epub","format":"EPUB","size":"30000"}]}""".stripMargin,
    "https://archive.org/metadata/zqlent" ->
      """{"metadata":{"identifier":"zqlent","title":"Lent","access-restricted-item":"true"},"files":[{"name":"zqlent.pdf"}]}"""
  )

  private val asked = scala.collection.mutable.ListBuffer.empty[String]

  private object Canned extends PluginHttp:
    def get(url: String, headers: Map[String, String]) =
      asked += url
      if url.contains("/advancedsearch.php") then
        Right("""{"response":{"docs":[{"identifier":"zqbook"},{"identifier":"zqlent"}]}}""".getBytes(UTF_8))
      else if url.contains("/download/zqbook/") then Right(("PK " + "e" * 20480).getBytes(UTF_8))
      else answers.get(url).map(_.getBytes(UTF_8)).toRight(s"$url: HTTP 404 not found")

  private val collection = ArchiveOrgCollection(() => ArchiveOrg(Endpoint("https://archive.org"), Canned))

  test("search: each hit's own metadata, the lending-only one marked restricted"):
    val hits = collection.search(CollectionQuery(Some("bronze"), Some(2))).fold(fail(_), identity)
    assertEquals(
      hits.map(h => (h.id.value: String, h.title, h.restricted)),
      List(("zqbook", Option("A Book"), false), ("zqlent", Option("Lent"), true))
    )
    assert(asked.head.contains("rows=2"), asked.head)

  test("an item, the file an import takes, and its download; a lending-only one is never downloaded"):
    val book = collection.item(CollectionId("zqbook")).fold(fail(_), identity).getOrElse(fail("no item"))
    assertEquals((book.authors, book.year.map(y => y.value: Int)), (List("An Author"), Option(1901)))
    assertEquals(collection.file(book, None).map(_.name), Right("zqbook.epub"))
    assertEquals(collection.file(book, Some("pdf")).map(_.name), Right("zqbook.pdf"))
    val epub = collection.file(book, None).fold(fail(_), identity)
    assertEquals(collection.original(book, epub).map(_.extension), Right("epub"))
    val lent = collection.item(CollectionId("zqlent")).fold(fail(_), identity).getOrElse(fail("no item"))
    asked.clear()
    assert(collection.original(lent, CollectionFile("zqlent.pdf")).left.exists(_.contains("lending-only")))
    assertEquals(asked.toList, Nil)
    assert(collection.item(CollectionId("zqgone")).left.exists(_.contains("404")))

  test("read-only: it declares reading alone and refuses every write"):
    assertEquals(collection.capabilities, Set(CollectionCapability.Read))
    assert(
      collection.add(CollectionMetadata("k", "T", List("A")), FetchedFile("x", None, Array[Byte](1), "pdf")).isLeft
    )
