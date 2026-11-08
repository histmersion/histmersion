package com.histmersion
package plugins.fetchers

import com.histmersion.testsupport.ComparedByValue.given

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

  private object Canned extends Plugin.Http:
    def get(
        url: Plugin.Http.GetUrl,
        headers: Map[Plugin.Http.GetHeadersPart1, Plugin.Http.GetHeadersPart2]
    ): Either[Plugin.Http.GetResultPart1, Plugin.Http.GetResultPart2] =
      asked += url.value
      if url.value.contains("/advancedsearch.php") then
        Right(
          Plugin.HttpResponse.Body(
            """{"response":{"docs":[{"identifier":"zqbook"},{"identifier":"zqlent"}]}}""".getBytes(UTF_8)
          )
        )
      else if url.value.contains("/download/zqbook/") then
        Right(Plugin.HttpResponse.Body(("PK " + "e" * 20480).getBytes(UTF_8)))
      else
        answers
          .get(url.value)
          .map(a => Plugin.HttpResponse.Body(a.getBytes(UTF_8)))
          .toRight(Plugin.Http.GetResultPart1(s"${url.value}: HTTP 404 not found"))

  private val collection = ArchiveOrgCollection(() => ArchiveOrg(Endpoint(Endpoint.Url("https://archive.org")), Canned))

  test("search: each hit's own metadata, the lending-only one marked restricted"):
    val hits = collection
      .search(CollectionQuery(Some(CollectionQuery.Text("bronze")), Some(CollectionQuery.Limit(2))))
      .fold(e => fail(e.value), identity)
    assertEquals(
      hits.map(h => (h.id.value: String, h.title, h.restricted)),
      List(("zqbook", Option("A Book"), false), ("zqlent", Option("Lent"), true))
    )
    assert(asked.head.contains("rows=2"), asked.head)

  test("an item, the file an import takes, and its download; a lending-only one is never downloaded"):
    val book = collection.item(CollectionId("zqbook")).fold(e => fail(e.value), identity).getOrElse(fail("no item"))
    assertEquals((book.authors, book.year.map(y => y.value: Int)), (List("An Author"), Option(1901)))
    assertEquals(collection.file(book, None).map(_.name), Right("zqbook.epub"))
    assertEquals(collection.file(book, Some(SourceCollection.FileFormat("pdf"))).map(_.name), Right("zqbook.pdf"))
    val epub = collection.file(book, None).fold(e => fail(e.value), identity)
    assertEquals(collection.original(book, epub).map(_.extension), Right("epub"))
    val lent = collection.item(CollectionId("zqlent")).fold(e => fail(e.value), identity).getOrElse(fail("no item"))
    asked.clear()
    assert(
      collection
        .original(lent, CollectionFile(CollectionFile.Name("zqlent.pdf")))
        .left
        .exists(_.value.contains("lending-only"))
    )
    assertEquals(asked.toList, Nil)
    assert(collection.item(CollectionId("zqgone")).left.exists(_.value.contains("404")))

  test("read-only: it declares reading alone and refuses every write"):
    assertEquals(collection.capabilities, Set(CollectionCapability.Read))
    assert(
      collection
        .add(
          CollectionMetadata(
            CollectionMetadata.Key("k"),
            CollectionMetadata.Title("T"),
            List(CollectionMetadata.Authors("A"))
          ),
          FetchedFile(FetchedFile.Source("x"), None, FetchedFile.Bytes(Array[Byte](1)), FetchedFile.Extension("pdf"))
        )
        .isLeft
    )
