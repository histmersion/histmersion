package com.histmersion
package domain.library.collections
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.{sentence, sentences}

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.library.*
import com.histmersion.domain.Caller
import com.histmersion.plugin.*

import java.util.concurrent.atomic.AtomicReference
import com.histmersion.pluginhost.Registries
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
import com.histmersion.lint.{tolerated, LintRule}

/** The collections' rules on the in-memory service (the database one runs the same rules; the calibre commands' suite
  * runs it end to end): what a listing leaves out, what a push sends and records, what an update and an audit are
  * given, how gaps count, and the refusals — no such collection, a collection the library does not link to, a write the
  * collection does not declare.
  */
class CollectionsServiceSuite extends munit.FunSuite:

  private def book(id: CollectionId, title: CollectionItem.Title) =
    CollectionItem(id, Some(title), List(CollectionItem.Authors("An Author")), Some(Year(2000)))

  /** A shelf of three books that records what it was asked to write. */
  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  final private class Shelf(writes: Boolean) extends SourceCollection:
    val written = scala.collection.mutable.ListBuffer.empty[String]
    def title = SourceCollection.Title("Calibre")
    override def capabilities =
      if writes then Set(CollectionCapability.Read, CollectionCapability.Add, CollectionCapability.Update)
      else Set(CollectionCapability.Read)
    @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
    override def readOnlyReason = Problem.Unclassified(Legacy.Text("write-back is off"))
    private val books = List(
      book(CollectionId("1"), CollectionItem.Title("One")),
      book(CollectionId("2"), CollectionItem.Title("Two")),
      book(CollectionId("3"), CollectionItem.Title("Three"))
    )
    def search(query: CollectionQuery) = Right(books)
    def item(id: CollectionId) = Right(books.find(_.id == id))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def file(i: CollectionItem, format: Option[SourceCollection.FileFormat]) = Right(
      CollectionFile(CollectionFile.Name(s"/lib/${i.id.value}.pdf"))
    )
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def original(i: CollectionItem, f: CollectionFile) = Right(
      FetchedFile(FetchedFile.Source("calibre"), None, FetchedFile.Bytes(Array[Byte](1)), FetchedFile.Extension("pdf"))
    )
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    override def add(m: CollectionMetadata, f: FetchedFile) =
      written += s"add ${m.key} ${m.title} ${f.extension} ${f.bytes.value.length}"; Right(CollectionId("9"))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    override def updateAll(links: List[CollectionLink], apply: SourceCollection.UpdateAllApply) =
      written += s"updateAll ${links.map(l => s"${l.id.value}:${l.metadata.key}").mkString(",")} $apply"
      Right(
        links.map(l =>
          CollectionUpdateEntry(
            l.id,
            List(CollectionUpdateEntry.Keys(l.metadata.key.value)),
            CollectionUpdateOutcome.UpToDate
          )
        )
      )
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    override def audit(records: List[CollectionRecord]) =
      Right(
        records
          .filter(_.id.value == "99")
          .map(r =>
            CollectionFinding(
              r.id,
              List(CollectionFinding.Keys(r.key.value)),
              List(CollectionIssue(CollectionIssue.Code("Missing"), CollectionIssue.Label("gone"))),
              None,
              r,
              CollectionFinding.Fixable(false)
            )
          )
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def gap(
      ck: Citekey,
      stored: Option[CalibreGap.StoredOriginal],
      provenance: CalibreGap.Provenance = CalibreGap.Provenance("oa"),
      id: Option[Source.CalibreId] = None
  ) =
    CalibreGap(
      ck,
      CalibreGap.Title(s"Title of $ck"),
      List(CalibreGap.Authors("A B")),
      None,
      Some(api.catalogue.Year(2001)),
      None,
      None,
      List(CalibreGap.Topics("t")),
      Some(provenance),
      id,
      storedOriginal = stored
    )

  final private class Library extends RefusingCollectionLibrary:
    val pushedIds = scala.collection.mutable.ListBuffer.empty[(Citekey, Source.CalibreId)]
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    override def pushMeta(citekey: domain.library.Citekey) =
      Right(gap(Citekey(citekey.value), Some(CalibreGap.StoredOriginal(true)), id = Some(Source.CalibreId("2"))))
    override def pushed(req: CalibrePushRequest) =
      pushedIds += req.citekey -> req.calibreId;
      Right(CalibrePushResult(req.citekey, req.calibreId, ok = CalibrePushResult.Ok(true)))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
    override def original(citekey: domain.library.Citekey) =
      if citekey.value == "zz_none" then
        Left(Api.Error(Problem.Unclassified(Legacy.Text(s"no original stored for '$citekey'"))))
      else
        Right(
          StoredOriginal(
            StoredOriginal.Bytes(Array[Byte](1, 2, 3)),
            StoredOriginal.Disposition(s"""attachment; filename="$citekey.pdf"""")
          )
        )
    override def gaps(limit: Option[CollectionLibrary.Limit]) =
      CalibreGaps(
        CalibreGaps.Count(4),
        List(
          gap(Citekey("zz_a"), Some(CalibreGap.StoredOriginal(true))),
          gap(Citekey("zz_b"), Some(CalibreGap.StoredOriginal(false))),
          gap(Citekey("zz_c"), None),
          gap(Citekey("zz_d"), Some(CalibreGap.StoredOriginal(true)), CalibreGap.Provenance("calibre"))
        )
      )

  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  private val linked = List(
    LinkedSource(
      domain.library.Citekey("zz_two"),
      LinkedSource.Id("2"),
      LinkedSource.Title("Two"),
      List(domain.AuthorName("An Author")),
      Some(com.histmersion.Year(2000))
    ),
    LinkedSource(
      domain.library.Citekey("zz_lost"),
      LinkedSource.Id("99"),
      LinkedSource.Title("Lost"),
      List(domain.AuthorName("Nobody")),
      None
    )
  )

  private def service(shelf: SourceCollection, library: CollectionLibrary = Library()) =
    CollectionsService.InMemory(
      AtomicReference(linked),
      library,
      () => ListMap(Registries.SourceCollectionsKey("calibre") -> shelf),
      CollectionsService.Limit(8)
    )

  test("the unlinked listing leaves out the linked entries, newest first, and counts them all"):
    val r = service(Shelf(writes = true))
      .search(
        CollectionsService.CollectionName("calibre"),
        None,
        Some(CollectionsService.Limit(1)),
        unlinked = CollectionsService.SearchUnlinked(true)
      )
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals((r.entries.map(_.id), r.total), (List("3"), 2))
    assertEquals(
      service(Shelf(writes = true))
        .search(
          CollectionsService.CollectionName("calibre"),
          None,
          None,
          unlinked = CollectionsService.SearchUnlinked(false)
        )
        .left
        .map(_.code),
      Left(Some("usage"))
    )

  test("a push sends the record with its stored original and records the new id"):
    val shelf = Shelf(writes = true)
    val lib = Library()
    val p = service(shelf, lib)
      .push(Caller.Local, CollectionsService.CollectionName("calibre"), CollectionPushRequest(Citekey("zz_new")))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals((p.id, p.title), ("9", "Title of zz_new"))
    assertEquals(shelf.written.toList, List("add zz_new Title of zz_new pdf 3"))
    assertEquals(lib.pushedIds.toList, List("zz_new" -> "9"))
    val none = service(shelf, lib).push(
      Caller.Local,
      CollectionsService.CollectionName("calibre"),
      CollectionPushRequest(Citekey("zz_none"))
    )
    assert(
      none.left.exists(_.problems.sentences.mkString("; ").endsWith("to a directory holding <citekey>/paper.pdf")),
      none
    )

  test("update-all is given every linked source, in citekey order; the audit counts the linked entries"):
    val shelf = Shelf(writes = true)
    val u = service(shelf)
      .updateAll(
        Caller.Local,
        CollectionsService.CollectionName("calibre"),
        apply = CollectionsService.UpdateAllApply(false)
      )
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(shelf.written.toList, List("updateAll 99:zz_lost,2:zz_two false"))
    assertEquals(u.rows.map(_.state).distinct, List("up-to-date"))
    val a =
      service(shelf)
        .audit(CollectionsService.CollectionName("calibre"))
        .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals((a.checked, a.findings.map(f => (f.id, f.codes, f.fix))), (2, List(("99", List("Missing"), None))))

  test("gaps count only a source with an original that is not in Calibre already"):
    val g = service(Shelf(writes = true))
      .gaps(CollectionsService.CollectionName("calibre"), None)
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(
      g.gaps.map(r => (r.citekey, r.original, r.counted)),
      List(("zz_a", "stored", true), ("zz_b", "none", false), ("zz_c", "unknown", true), ("zz_d", "stored", false))
    )
    assertEquals((g.count, g.uncounted, g.total), (2, 2, 4))
    assertEquals(
      CollectionsMarkdown.gaps(g.gaps).linesIterator.toList.last,
      CollectionsMarkdown.uncountedLine(CollectionsMarkdown.Missing(1))
    )

  test("refusals: no such collection, a collection the library does not link to, a write not declared"):
    val s = service(Shelf(writes = true))
    assertEquals(
      s.collection(CollectionsService.CollectionName("zotero")).left.map(_.code),
      Left(Some(CollectionsApi.NoCollection))
    )
    val other = CollectionsService.InMemory(
      AtomicReference(Nil),
      Library(),
      () => ListMap(Registries.SourceCollectionsKey("shelf") -> Shelf(true)),
      CollectionsService.Limit(8)
    )
    assert(
      other
        .audit(CollectionsService.CollectionName("shelf"))
        .left
        .exists(_.problems.sentences.mkString("; ").contains("links sources to Calibre only"))
    )
    val ro = service(Shelf(writes = false))
      .push(Caller.Local, CollectionsService.CollectionName("calibre"), CollectionPushRequest(Citekey("zz_new")))
    assertEquals(ro.left.map(_.problems.head).left.map(_.sentence), Left("write-back is off"))
    assertEquals(
      service(Shelf(writes = false)).collection(CollectionsService.CollectionName("calibre")).map(_.capabilities),
      Right(List("read"))
    )
