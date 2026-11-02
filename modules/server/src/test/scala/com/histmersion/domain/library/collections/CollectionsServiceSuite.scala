package com.histmersion.domain.library.collections

import com.histmersion.api.library.*
import com.histmersion.domain.Caller
import com.histmersion.plugin.*

import java.util.concurrent.atomic.AtomicReference
import scala.collection.immutable.ListMap
import com.histmersion.api.library.{
  CalibreGap,
  CalibreGaps,
  CalibrePushRequest,
  CalibrePushResult,
  CollectionPushRequest,
  CollectionsApi,
  CollectionsMarkdown
}
import com.histmersion.api.Api

/** The collections' rules on the in-memory service (the database one runs the same rules; the calibre commands' suite
  * runs it end to end): what a listing leaves out, what a push sends and records, what an update and an audit are
  * given, how gaps count, and the refusals — no such collection, a collection the library does not link to, a write the
  * collection does not declare.
  */
class CollectionsServiceSuite extends munit.FunSuite:

  private def book(id: Int, title: String) =
    CollectionItem(CollectionId(id.toString), Some(title), List("An Author"), Some(Year(2000)))

  /** A shelf of three books that records what it was asked to write. */
  final private class Shelf(writes: Boolean) extends SourceCollection:
    val written = scala.collection.mutable.ListBuffer.empty[String]
    def title = "Calibre"
    override def capabilities =
      if writes then Set(CollectionCapability.Read, CollectionCapability.Add, CollectionCapability.Update)
      else Set(CollectionCapability.Read)
    override def readOnlyReason = "write-back is off"
    private val books = List(book(1, "One"), book(2, "Two"), book(3, "Three"))
    def search(query: CollectionQuery) = Right(books)
    def item(id: CollectionId) = Right(books.find(_.id == id))
    def file(i: CollectionItem, format: Option[String]) = Right(CollectionFile(s"/lib/${i.id.value}.pdf"))
    def original(i: CollectionItem, f: CollectionFile) = Right(FetchedFile("calibre", None, Array[Byte](1), "pdf"))
    override def add(m: CollectionMetadata, f: FetchedFile) =
      written += s"add ${m.key} ${m.title} ${f.extension} ${f.bytes.length}"; Right(CollectionId("9"))
    override def updateAll(links: List[CollectionLink], apply: Boolean) =
      written += s"updateAll ${links.map(l => s"${l.id.value}:${l.metadata.key}").mkString(",")} $apply"
      Right(links.map(l => CollectionUpdateEntry(l.id, List(l.metadata.key), CollectionUpdateOutcome.UpToDate)))
    override def audit(records: List[CollectionRecord]) =
      Right(
        records
          .filter(_.id.value == "99")
          .map(r => CollectionFinding(r.id, List(r.key), List(CollectionIssue("Missing", "gone")), None, r, false))
      )

  private def gap(ck: String, stored: Option[Boolean], provenance: String = "oa", id: Option[String] = None) =
    CalibreGap(
      ck,
      s"Title of $ck",
      List("A B"),
      None,
      Some(2001),
      None,
      None,
      List("t"),
      Some(provenance),
      id,
      storedOriginal = stored
    )

  final private class Library extends RefusingCollectionLibrary:
    val pushedIds = scala.collection.mutable.ListBuffer.empty[(String, String)]
    override def pushMeta(citekey: String) = Right(gap(citekey, Some(true), id = Some("2")))
    override def pushed(req: CalibrePushRequest) =
      pushedIds += req.citekey -> req.calibreId; Right(CalibrePushResult(req.citekey, req.calibreId, ok = true))
    override def original(citekey: String) =
      if citekey == "zz_none" then Left(Api.Error(s"no original stored for '$citekey'"))
      else Right(StoredOriginal(Array[Byte](1, 2, 3), s"""attachment; filename="$citekey.pdf""""))
    override def gaps(limit: Option[Int]) =
      CalibreGaps(
        4,
        List(gap("zz_a", Some(true)), gap("zz_b", Some(false)), gap("zz_c", None), gap("zz_d", Some(true), "calibre"))
      )

  private val linked = List(
    LinkedSource("zz_two", "2", "Two", List("An Author"), Some(2000)),
    LinkedSource("zz_lost", "99", "Lost", List("Nobody"), None)
  )

  private def service(shelf: SourceCollection, library: CollectionLibrary = Library()) =
    CollectionsService.InMemory(AtomicReference(linked), library, () => ListMap("calibre" -> shelf), 8)

  test("the unlinked listing leaves out the linked entries, newest first, and counts them all"):
    val r = service(Shelf(writes = true))
      .search("calibre", None, Some(1), unlinked = true)
      .fold(e => fail(e.message), identity)
    assertEquals((r.entries.map(_.id), r.total), (List("3"), 2))
    assertEquals(
      service(Shelf(writes = true)).search("calibre", None, None, unlinked = false).left.map(_.code),
      Left(Some("usage"))
    )

  test("a push sends the record with its stored original and records the new id"):
    val shelf = Shelf(writes = true)
    val lib = Library()
    val p = service(shelf, lib)
      .push(Caller.Local, "calibre", CollectionPushRequest("zz_new"))
      .fold(e => fail(e.message), identity)
    assertEquals((p.id, p.title), ("9", "Title of zz_new"))
    assertEquals(shelf.written.toList, List("add zz_new Title of zz_new pdf 3"))
    assertEquals(lib.pushedIds.toList, List("zz_new" -> "9"))
    val none = service(shelf, lib).push(Caller.Local, "calibre", CollectionPushRequest("zz_none"))
    assert(none.left.exists(_.message.endsWith("to a directory holding <citekey>/paper.pdf")), none)

  test("update-all is given every linked source, in citekey order; the audit counts the linked entries"):
    val shelf = Shelf(writes = true)
    val u = service(shelf).updateAll(Caller.Local, "calibre", apply = false).fold(e => fail(e.message), identity)
    assertEquals(shelf.written.toList, List("updateAll 99:zz_lost,2:zz_two false"))
    assertEquals(u.rows.map(_.state).distinct, List("up-to-date"))
    val a = service(shelf).audit("calibre").fold(e => fail(e.message), identity)
    assertEquals((a.checked, a.findings.map(f => (f.id, f.codes, f.fix))), (2, List(("99", List("Missing"), None))))

  test("gaps count only a source with an original that is not in Calibre already"):
    val g = service(Shelf(writes = true)).gaps("calibre", None).fold(e => fail(e.message), identity)
    assertEquals(
      g.gaps.map(r => (r.citekey, r.original, r.counted)),
      List(("zz_a", "stored", true), ("zz_b", "none", false), ("zz_c", "unknown", true), ("zz_d", "stored", false))
    )
    assertEquals((g.count, g.uncounted, g.total), (2, 2, 4))
    assertEquals(CollectionsMarkdown.gaps(g.gaps).linesIterator.toList.last, CollectionsMarkdown.uncountedLine(1))

  test("refusals: no such collection, a collection the library does not link to, a write not declared"):
    val s = service(Shelf(writes = true))
    assertEquals(s.collection("zotero").left.map(_.code), Left(Some(CollectionsApi.NoCollection)))
    val other = CollectionsService.InMemory(AtomicReference(Nil), Library(), () => ListMap("shelf" -> Shelf(true)), 8)
    assert(other.audit("shelf").left.exists(_.message.contains("links sources to Calibre only")))
    val ro = service(Shelf(writes = false)).push(Caller.Local, "calibre", CollectionPushRequest("zz_new"))
    assertEquals(ro.left.map(_.message), Left("Calibre is read-only here: write-back is off"))
    assertEquals(service(Shelf(writes = false)).collection("calibre").map(_.capabilities), Right(List("read")))
