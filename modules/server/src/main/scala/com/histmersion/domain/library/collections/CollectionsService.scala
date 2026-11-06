package com.histmersion.domain.library.collections

import hearth.kindlings.optics.syntax.*

import io.scalaland.chimney.dsl.*

import com.histmersion.api.library.*
import com.histmersion.domain.Caller
import com.histmersion.domain.media.covers.CoverChoice
import com.histmersion.plugin.{
  CollectionCapability,
  CollectionFile,
  CollectionId,
  CollectionItem,
  CollectionLink,
  CollectionMetadata,
  CollectionQuery,
  CollectionRecord,
  CollectionUpdateOutcome,
  FetchedFile,
  SourceCollection,
  Year
}
import com.histmersion.pluginhost.HostCollection
import com.histmersion.util.database.Transactor

import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec
import scala.collection.immutable.ListMap
import com.histmersion.api.automation.Queue
import com.histmersion.api.library.{
  ArchiveFile,
  ArchiveImportRequest,
  ArchiveItem,
  ArchiveSearchResult,
  CalibreGap,
  CalibrePushRequest,
  CollectionAudit,
  CollectionCover,
  CollectionEntry,
  CollectionFieldChange,
  CollectionGaps,
  CollectionImported,
  CollectionImportRequest,
  CollectionInfo,
  CollectionLinked,
  CollectionLinkRequest,
  CollectionPushed,
  CollectionPushRequest,
  CollectionRecordView,
  CollectionsApi,
  CollectionSearch,
  CollectionSetDone,
  CollectionSetRequest,
  CollectionSync,
  CollectionUpdateAll,
  CollectionUpdatedOne,
  CollectionUpdateRow,
  Ingest,
  MetadataPatch
}
import com.histmersion.api.media.{MediaCoversRequest, MediaProvider, SourceCoverParams}
import com.histmersion.api.Api

/** The library's source collections — the Calibre library, archive.org — run by the library: searching one, importing
  * an item into the library, linking a source to an item, pushing a source's original into one, rewriting its items
  * from the records, setting fields, the audit, the gaps (sources whose original is in no collection) and the sync. The
  * collection is the plugin's; every rule of what the library stores is here.
  */
trait CollectionsService:
  def collections(): List[CollectionInfo]
  def collection(name: com.histmersion.domain.library.collections.CollectionsService.CollectionName): Either[Api.Error, CollectionInfo]
  def search(
      name: com.histmersion.domain.library.collections.CollectionsService.SearchName,
      q: Option[com.histmersion.domain.library.collections.CollectionsService.SearchQ],
      limit: Option[com.histmersion.domain.library.collections.CollectionsService.SearchLimit],
      unlinked: com.histmersion.domain.library.collections.CollectionsService.SearchUnlinked
  ): Either[Api.Error, CollectionSearch]
  def importItem(caller: Caller, name: com.histmersion.domain.library.collections.CollectionsService.ImportItemName, req: CollectionImportRequest): Either[Api.Error, CollectionImported]
  def link(caller: Caller, name: com.histmersion.domain.library.collections.CollectionsService.LinkName, req: CollectionLinkRequest): Either[Api.Error, CollectionLinked]
  def push(caller: Caller, name: com.histmersion.domain.library.collections.CollectionsService.PushName, req: CollectionPushRequest): Either[Api.Error, CollectionPushed]
  def updateOne(caller: Caller, name: com.histmersion.domain.library.collections.CollectionsService.UpdateOneName, citekey: com.histmersion.domain.library.Citekey): Either[Api.Error, CollectionUpdatedOne]
  def updateAll(caller: Caller, name: com.histmersion.domain.library.collections.CollectionsService.UpdateAllName, apply: com.histmersion.domain.library.collections.CollectionsService.UpdateAllApply): Either[Api.Error, CollectionUpdateAll]
  def set(caller: Caller, name: com.histmersion.domain.library.collections.CollectionsService.SetName, req: CollectionSetRequest): Either[Api.Error, CollectionSetDone]
  def audit(name: com.histmersion.domain.library.collections.CollectionsService.AuditName): Either[Api.Error, CollectionAudit]
  def gaps(name: com.histmersion.domain.library.collections.CollectionsService.GapsName, limit: Option[com.histmersion.domain.library.collections.CollectionsService.GapsLimit]): Either[Api.Error, CollectionGaps]
  def sync(caller: Caller, name: com.histmersion.domain.library.collections.CollectionsService.SyncName, req: CollectionSync.Request): Either[Api.Error, CollectionSync]
  def archiveSearch(q: com.histmersion.domain.library.collections.CollectionsService.ArchiveSearchQ, rows: Option[com.histmersion.domain.library.collections.CollectionsService.ArchiveSearchRows]): Either[Api.Error, ArchiveSearchResult]
  def archiveInfo(id: com.histmersion.domain.library.collections.CollectionsService.ArchiveInfoId): Either[Api.Error, ArchiveItem]
  def archiveImport(caller: Caller, req: ArchiveImportRequest): Either[Api.Error, Ingest.Report]

object CollectionsService:
  /** The collection the library links its sources to (`sources.calibre_id`). */
  type CollectionName = CollectionName.T
  object CollectionName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchName = SearchName.T
  object SearchName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchQ = SearchQ.T
  object SearchQ extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchLimit = SearchLimitValue
  type SearchLimitValue = SearchLimitValue.T
  object SearchLimitValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchUnlinked = SearchUnlinked.T
  object SearchUnlinked extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ImportItemName = ImportItemName.T
  object ImportItemName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkName = LinkName.T
  object LinkName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PushName = PushName.T
  object PushName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpdateOneName = UpdateOneName.T
  object UpdateOneName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpdateAllName = UpdateAllName.T
  object UpdateAllName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpdateAllApply = UpdateAllApply.T
  object UpdateAllApply extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetName = SetName.T
  object SetName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AuditName = AuditName.T
  object AuditName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GapsName = GapsName.T
  object GapsName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GapsLimit = GapsLimit.T
  object GapsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SyncName = SyncName.T
  object SyncName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArchiveSearchQ = ArchiveSearchQ.T
  object ArchiveSearchQ extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArchiveSearchRows = ArchiveSearchRows.T
  object ArchiveSearchRows extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArchiveInfoId = ArchiveInfoId.T
  object ArchiveInfoId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyRegistryResultPart1 = ApplyRegistryResultPart1.T
  object ApplyRegistryResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyArchiveRows = ApplyArchiveRows.T
  object ApplyArchiveRows extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Calibre = "calibre"

  /** archive.org's texts. */
  val ArchiveOrg = "archive-org"

  /** The library's collections over the database; `registry` answers the plugin generation's collections now. */
  def apply(
      transactor: Transactor,
      library: CollectionLibrary,
      registry: () => ListMap[com.histmersion.domain.library.collections.CollectionsService.ApplyRegistryResultPart1, SourceCollection],
      archiveRows: com.histmersion.domain.library.collections.CollectionsService.ApplyArchiveRows
  ): CollectionsService =
    Implementation(() => transactor.read(CollectionLinksRepo.linked()), library, registry, archiveRows)

  /** The service with the linked sources held in `linked`: the same operations, the same answers. */
  final class InMemory(
      linked: AtomicReference[List[LinkedSource]],
      library: CollectionLibrary,
      registry: () => ListMap[InMemory.RegistryResultKey, SourceCollection],
      archiveRows: InMemory.ArchiveRows
  ) extends CollectionsService:
    private val run = Implementation(() => linked.get.sortBy(_.citekey), library, registry, archiveRows)
    export run.*

  object InMemory:
    type RegistryResultKey = RegistryResultKey.T
    object RegistryResultKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ArchiveRows = ArchiveRows.T
    object ArchiveRows extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val StoredOriginalHint =
    " — or set cli.calibre.originals-dir (HISTMERSION_CALIBRE_ORIGINALS_DIR) to a directory holding <citekey>/paper.pdf"

  final private class Implementation(
      linked: () => List[LinkedSource],
      library: CollectionLibrary,
      registry: () => ListMap[Implementation.RegistryResultKey, SourceCollection],
      archiveRows: Implementation.ArchiveRows
  ) extends CollectionsService:

    private def fail(message: com.histmersion.domain.library.collections.CollectionsService.Implementation.FailMessage): Api.Error = Api.Error(message)
    private def usage(message: com.histmersion.domain.library.collections.CollectionsService.Implementation.UsageMessage): Api.Error = Api.Error(message, Some(CollectionsApi.Usage))

    private def lookup(name: com.histmersion.domain.library.collections.CollectionsService.Implementation.LookupName): Either[Api.Error, HostCollection] =
      registry()
        .get(name)
        .map(HostCollection(name, _))
        .toRight(
          Api.Error(
            s"no source collection '$name' is registered on this server — its plugin is off or not installed",
            Some(CollectionsApi.NoCollection)
          )
        )

    /** The collections the library links sources to: Calibre alone (`sources.calibre_id`). */
    private def linking(name: com.histmersion.domain.library.collections.CollectionsService.Implementation.LinkingName): Either[Api.Error, HostCollection] =
      if name == Calibre then lookup(name)
      else Left(fail(s"the library links sources to Calibre only, not to '$name'"))

    private def info(c: HostCollection): CollectionInfo =
      val reach = c.reach
      CollectionInfo(
        c.name,
        c.title,
        c.description,
        CollectionCapability.values.toList.filter(c.can).map(_.toString.toLowerCase(java.util.Locale.ROOT)),
        reach.isRight,
        reach.left.toOption
      )

    def collections(): List[CollectionInfo] = registry().toList.map((n, c) => info(HostCollection(n, c)))

    def collection(name: com.histmersion.domain.library.collections.CollectionsService.Implementation.CollectionName): Either[Api.Error, CollectionInfo] = lookup(name).map(info)

    private def entry(i: CollectionItem): CollectionEntry =
      CollectionEntry(
        i.id.value,
        i.title.getOrElse(""),
        i.authors,
        i.year.map(_.value),
        i.publisher,
        i.tags,
        i.identifiers,
        i.files.map(_.name)
      )

    private def linkedIds(): Set[com.histmersion.domain.library.collections.CollectionsService.Implementation.LinkedIdsResult] = linked().flatMap(_.id.trim.toIntOption).map(_.toString).toSet

    def search(
        name: com.histmersion.domain.library.collections.CollectionsService.Implementation.SearchName,
        q: Option[com.histmersion.domain.library.collections.CollectionsService.Implementation.SearchQ],
        limit: Option[com.histmersion.domain.library.collections.CollectionsService.Implementation.SearchLimit],
        unlinked: com.histmersion.domain.library.collections.CollectionsService.Implementation.SearchUnlinked
    ): Either[Api.Error, CollectionSearch] =
      val max = limit.getOrElse(SearchLimit)
      if q.isEmpty && !unlinked then Left(usage("give a query, or --unlinked"))
      else if !unlinked then
        lookup(name).flatMap(
          _.search(CollectionQuery(Some(q.getOrElse("")), Some(max))).left
            .map(fail)
            .map(items => CollectionSearch(items.map(entry), items.size))
        )
      else
        for
          c <- linking(name)
          ids = linkedIds()
          query = q
            .map(_.trim)
            .filter(_.nonEmpty)
            .fold(CollectionQuery(None))(t => CollectionQuery(Some(t), Some(Int.MaxValue)))
          items <- c.search(query).left.map(fail)
        yield
          val all = items
            .filterNot(i => i.id.value.trim.toIntOption.exists(n => ids(n.toString)))
            .sortBy(i => -i.id.value.trim.toIntOption.getOrElse(0))
          CollectionSearch(all.take(max.max(1)).map(entry), all.size)

    // ── import ────────────────────────────────────────────────────────────────────────────────

    def importItem(caller: Caller, name: com.histmersion.domain.library.collections.CollectionsService.Implementation.ImportItemName, req: CollectionImportRequest): Either[Api.Error, CollectionImported] =
      for
        c <- linking(name)
        _ <- req.acquireItem.fold(Right(()))(i =>
          library.queueItem(i).flatMap(found => Queue.fulfillable(i, found).left.map(usage).map(_ => ()))
        )
        item <- c
          .item(CollectionId(req.id))
          .left
          .map(fail)
          .flatMap(_.toRight(fail(s"no Calibre book with id ${req.id}")))
        _ <- c.admits(item).left.map(fail)
        file <- c.file(item, req.format).left.map(fail)
        ck = req.citekey.getOrElse(ImportTarget.citekeyFor(item))
        existing <- library.pushMeta(ck) match
          case Right(m)                                     => Right(Some(m.calibreId))
          case Left(e) if e.message.startsWith("No source") => Right(None)
          case Left(e)                                      => Left(e)
        target <- ImportTarget.of(ck, req.id, req.citekey.isDefined, existing).left.map(usage)
        record <- upsert(ck, item, target)
      yield
        val cover = Option.when(req.cover)(coverOf(c, ck, item))
        val conversion = if req.convert then Some(convert(c, item, file, ck, req.acquireItem)) else None
        CollectionImported(
          ck,
          req.id,
          item.title.getOrElse(""),
          target match
            case ImportTarget.Update(previous) => previous
            case ImportTarget.Create           => None
          ,
          file.name,
          record,
          cover,
          conversion.flatMap(_.toOption),
          conversion.flatMap(_.left.toOption.map(_.message)),
          conversion.flatMap(_.left.toOption.flatMap(_.code))
        )

    private def stripHtml(s: com.histmersion.domain.library.collections.CollectionsService.Implementation.StripHtmlS): com.histmersion.domain.library.collections.CollectionsService.Implementation.StripHtmlResult = s.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim

    /** Create the source, or enrich it if the citekey exists — a re-import never wipes the findings and notes an agent
      * compiled; either way the record ends up linked to this entry.
      */
    private def upsert(ck: com.histmersion.domain.library.collections.CollectionsService.Implementation.UpsertCk, item: CollectionItem, target: ImportTarget): Either[Api.Error, Source.WithFindings] =
      val summary = item.description.map(stripHtml).filter(_.nonEmpty).map(_.take(500))
      val doi = item.identifiers.get("doi")
      target match
        case ImportTarget.Update(_) =>
          for
            current <- library.source(ck)
            res <- library.patch(
              ck,
              Source.Patch(
                metadata = Some(
                  MetadataPatch(
                    title = (item.title.orElse(Some(""))).map(com.histmersion.api.library.Title.apply),
                    authors = (Some(item.authors)).map(_.map(com.histmersion.api.library.AuthorName.apply)),
                    year = (item.year.map(_.value)).map(com.histmersion.api.catalogue.Year.apply),
                    venue = (item.publisher).map(com.histmersion.api.library.Source.Venue.apply),
                    doi = (doi).map(com.histmersion.api.library.Doi.apply),
                    calibreId = Some(item.id.value)
                  )
                ),
                summary = ImportTarget.summary(summary, current.source.summary)
              )
            )
          yield res
        case ImportTarget.Create =>
          library.create(
            ck,
            Source.Put(
              title = com.histmersion.api.library.Title(item.title.getOrElse("")),
              authors = (item.authors).map(com.histmersion.api.library.AuthorName.apply),
              doi = (doi).map(com.histmersion.api.library.Doi.apply),
              year = (item.year.map(_.value)).map(com.histmersion.api.catalogue.Year.apply),
              venue = (item.publisher).map(com.histmersion.api.library.Source.Venue.apply),
              status = Some(
                com.histmersion.domain.library.Source.Status.Unassessed
                  .transformInto[com.histmersion.api.library.Source.Status]
              ),
              tags = item.tags,
              summary = summary,
              provenance = Some("calibre"),
              calibreId = Some(item.id.value)
            )
          )

    /** The imported book's cover, best-effort: the collection's own, else Open Library's by its ISBN; a missing cover
      * never fails the import.
      */
    private def coverOf(c: HostCollection, ck: com.histmersion.domain.library.collections.CollectionsService.Implementation.CoverOfCk, item: CollectionItem): CollectionCover =
      c.cover(item) match
        case Left(why)       => CollectionCover(s"$ck: $why", warning = true)
        case Right(Some(fc)) =>
          library.putCover(SourceCoverParams(ck, MediaProvider.Calibre, item.id.value, None), fc.bytes) match
            case Right(_) => CollectionCover("Cover: from the Calibre library")
            case Left(e)  => CollectionCover(s"$ck: ${e.message}", warning = true)
        case Right(None) =>
          CoverChoice.choose(None, _ => false, item.identifiers.get("isbn").toList, item.identifiers.get("oclc")) match
            case Some(CoverChoice.Pick.OpenLibrary("isbn", isbn)) =>
              library.openLibraryCover(
                MediaCoversRequest(dryRun = false, citekey = Some(ck), illustrations = false, isbn = Some(isbn))
              ) match
                case Right(r) if r.covers > 0 => CollectionCover(s"Cover: from Open Library (ISBN $isbn)")
                case _ => CollectionCover("Cover: none (no cover file in Calibre, none at Open Library)")
            case Some(_) => CollectionCover("Cover: none (no cover file in Calibre)")
            case None    => CollectionCover("Cover: none (no cover file in Calibre, no ISBN)")

    /** The conversion: the same upload `histmersion ingest <file>` makes, marked as Calibre's. */
    private def convert(
        c: HostCollection,
        item: CollectionItem,
        file: CollectionFile,
        ck: com.histmersion.domain.library.collections.CollectionsService.Implementation.ConvertCk,
        acquireItem: Option[com.histmersion.domain.library.collections.CollectionsService.Implementation.ConvertAcquireItem]
    ): Either[Api.Error, Ingest.Accepted] =
      val fileName = file.name.reverse.takeWhile(_ != '/').reverse
      c.original(item, file) match
        case Right(f) if f.bytes.nonEmpty =>
          library.ingest(
            Ingest.Params(ck, Some(fileName), provenance = Some("calibre"), acquireItem = acquireItem),
            f.bytes
          )
        case _ => Left(usage(s"${file.name} is empty — there is nothing to convert"))

    // ── link, push, update, set ───────────────────────────────────────────────────────────────

    def link(caller: Caller, name: com.histmersion.domain.library.collections.CollectionsService.Implementation.LinkName, req: CollectionLinkRequest): Either[Api.Error, CollectionLinked] =
      for
        c <- linking(name)
        item <- c
          .item(CollectionId(req.id))
          .left
          .map(fail)
          .flatMap(_.toRight(fail(s"no Calibre book with id ${req.id}")))
        sw <- library.patch(req.citekey, Source.Patch(metadata = Some(MetadataPatch(calibreId = Some(req.id)))))
      yield CollectionLinked(sw.source.citekey, req.id, item.title.getOrElse(""))

    private def metadata(ck: com.histmersion.domain.library.collections.CollectionsService.Implementation.MetadataCk, s: CalibreGap): CollectionMetadata =
      CollectionMetadata(
        ck,
        s.title,
        s.authors,
        s.doi,
        s.isbn,
        s.year.map(Year(_)),
        s.venue,
        s.summary.map(_.take(500)),
        s.topics
      )

    /** The stored original of `ck` as a file to hand a collection; `hint` is added to the refusal when there is none.
      */
    private def original(ck: com.histmersion.domain.library.collections.CollectionsService.Implementation.OriginalCk, hint: com.histmersion.domain.library.collections.CollectionsService.Implementation.OriginalHint): Either[Api.Error, FetchedFile] =
      for
        o <- library.original(ck).left.map(e => if hint.isEmpty then e else e.modify(_.message).setTo(e.message + hint))
        ext <- Ingest.dispositionExtension(o.disposition).toRight(fail(s"the server did not say what format '$ck' is"))
      yield FetchedFile("original", None, o.bytes, ext)

    /** One push: the source's metadata, its stored original into the collection as a new item, and the new id recorded
      * so it stops counting as a gap.
      */
    private def pushOne(c: HostCollection, ck: com.histmersion.domain.library.collections.CollectionsService.Implementation.PushOneCk): Either[Api.Error, (com.histmersion.domain.library.collections.CollectionsService.Implementation.PushOneResultPart2Part1, com.histmersion.domain.library.collections.CollectionsService.Implementation.PushOneResultPart2Part2)] =
      for
        s <- library.pushMeta(ck)
        file <- original(ck, StoredOriginalHint)
        id <- c.add(metadata(ck, s), file).left.map(fail)
        _ <- library.pushed(CalibrePushRequest(ck, id.value))
      yield (id.value, s.title)

    def push(caller: Caller, name: com.histmersion.domain.library.collections.CollectionsService.Implementation.PushName, req: CollectionPushRequest): Either[Api.Error, CollectionPushed] =
      val ck = req.citekey
      linking(name).flatMap { c =>
        if !req.replace then pushOne(c, ck).map((id, title) => CollectionPushed(ck, id, title))
        else
          for
            s <- library.pushMeta(ck)
            id <- s.calibreId
              .flatMap(_.trim.toIntOption)
              .toRight(
                fail(
                  s"'$ck' is not linked to a Calibre entry — `histmersion calibre push $ck` adds one, " +
                    s"`histmersion calibre link $ck <id>` links an existing one"
                )
              )
            file <- original(ck, "")
            fmt <- c.replace(CollectionId(id.toString), ck, file).left.map(fail)
          yield CollectionPushed(ck, id.toString, s.title, Some(fmt))
      }

    def updateOne(caller: Caller, name: com.histmersion.domain.library.collections.CollectionsService.Implementation.UpdateOneName, citekey: com.histmersion.domain.library.Citekey): Either[Api.Error, CollectionUpdatedOne] =
      for
        c <- linking(name)
        s <- library.pushMeta(citekey)
        id <- s.calibreId
          .flatMap(_.toIntOption)
          .toRight(fail(s"'$citekey' has no Calibre entry yet — use `histmersion calibre push $citekey`"))
        changes <- c.update(CollectionId(id.toString), metadata(citekey, s)).left.map(fail)
      yield CollectionUpdatedOne(citekey, id.toString, changes.map(_.field), s.title)

    def updateAll(caller: Caller, name: com.histmersion.domain.library.collections.CollectionsService.Implementation.UpdateAllName, apply: com.histmersion.domain.library.collections.CollectionsService.Implementation.UpdateAllApply): Either[Api.Error, CollectionUpdateAll] =
      @tailrec def links(rest: List[LinkedSource], acc: List[CollectionLink]): Either[Api.Error, List[CollectionLink]] =
        rest match
          case Nil       => Right(acc.reverse)
          case l :: tail =>
            library.pushMeta(l.citekey) match
              case Left(e)  => Left(e)
              case Right(s) => links(tail, CollectionLink(CollectionId(l.id), metadata(l.citekey, s)) :: acc)
      for
        c <- linking(name)
        ls <- links(linked(), Nil)
        entries <- c.updateAll(ls, apply).left.map(fail)
      yield CollectionUpdateAll(entries.map(updateRow), apply)

    private def updateRow(e: com.histmersion.plugin.CollectionUpdateEntry): CollectionUpdateRow =
      def changes(cs: List[com.histmersion.plugin.CollectionChange]) =
        cs.map(ch => CollectionFieldChange(ch.field, ch.from, ch.to))
      val (state, cs, reason) = e.outcome match
        case CollectionUpdateOutcome.Change(cs)         => ("would-change", changes(cs), None)
        case CollectionUpdateOutcome.Applied(cs)        => ("updated", changes(cs), None)
        case CollectionUpdateOutcome.Failed(cs, reason) => ("failed", changes(cs), Some(reason))
        case CollectionUpdateOutcome.UpToDate           => ("up-to-date", Nil, None)
        case CollectionUpdateOutcome.Shared             => ("shared", Nil, None)
        case CollectionUpdateOutcome.Missing            => ("missing", Nil, None)
        case CollectionUpdateOutcome.Refused(reason)    => ("refused", Nil, Some(reason))
      CollectionUpdateRow(e.id.value, e.keys, state, cs, reason)

    def set(caller: Caller, name: com.histmersion.domain.library.collections.CollectionsService.Implementation.SetName, req: CollectionSetRequest): Either[Api.Error, CollectionSetDone] =
      linking(name).flatMap(
        _.set(CollectionId(req.id), req.fields.map(f => f.name -> f.value)).left
          .map(fail)
          .map(_ => CollectionSetDone(req.id, req.fields.map(_.name)))
      )

    // ── audit, gaps, sync ─────────────────────────────────────────────────────────────────────

    def audit(name: com.histmersion.domain.library.collections.CollectionsService.Implementation.AuditName): Either[Api.Error, CollectionAudit] =
      val recs = linked()
      val records =
        recs.map(r => CollectionRecord(r.citekey, CollectionId(r.id), r.title, r.authors, r.year.map(Year(_))))
      for
        c <- linking(name)
        found <- c.audit(records).left.map(fail)
      yield CollectionAudit(
        found.map { f =>
          CollectionAudit.Row(
            f.id.value,
            f.keys,
            f.issues.map(_.code),
            f.issues.map(_.label),
            CollectionRecordView(f.record.title, f.record.authors, f.record.year.map(_.value)),
            f.item.map(i => CollectionRecordView(i.title.getOrElse(""), i.authors, i.year.map(_.value))),
            Option.when(f.fixable)(f.record.key)
          )
        },
        recs.flatMap(_.id.trim.toIntOption).distinct.size
      )

    def gaps(name: com.histmersion.domain.library.collections.CollectionsService.Implementation.GapsName, limit: Option[com.histmersion.domain.library.collections.CollectionsService.Implementation.GapsLimit]): Either[Api.Error, CollectionGaps] =
      linking(name).map { _ =>
        val rows = library.gaps(limit).gaps.map(GapOriginal.row)
        val n = rows.count(_.counted)
        CollectionGaps(n, rows.size - n, rows.size, rows)
      }

    def sync(caller: Caller, name: com.histmersion.domain.library.collections.CollectionsService.Implementation.SyncName, req: CollectionSync.Request): Either[Api.Error, CollectionSync] =
      for
        g <- gaps(name, req.limit)
        c <- linking(name)
      yield
        val pushes =
          if !req.apply then Nil
          else
            g.gaps.filter(_.counted).map { r =>
              pushOne(c, r.citekey).fold(
                e => CollectionSync.Push(r.citekey, None, Some(e.message)),
                (id, _) => CollectionSync.Push(r.citekey, Some(id), None)
              )
            }
        CollectionSync(g, req.apply, pushes)

    // ── archive.org ───────────────────────────────────────────────────────────────────────────

    private def archiveItem(i: CollectionItem): ArchiveItem =
      ArchiveItem(
        i.id.value,
        i.title,
        i.authors,
        i.year.map(_.value),
        i.publisher,
        i.description,
        i.restricted,
        i.files.map(f => ArchiveFile(f.name, f.format, f.megabytes.getOrElse(0.0)))
      )

    def archiveSearch(q: com.histmersion.domain.library.collections.CollectionsService.Implementation.ArchiveSearchQ, rows: Option[com.histmersion.domain.library.collections.CollectionsService.Implementation.ArchiveSearchRows]): Either[Api.Error, ArchiveSearchResult] =
      val query = q.trim
      if query.isEmpty then Left(fail("give a search query"))
      else
        for
          c <- lookup(ArchiveOrg)
          items <- c
            .search(CollectionQuery(Some(query), Some(rows.filter(_ > 0).getOrElse(archiveRows))))
            .left
            .map(fail)
        yield ArchiveSearchResult(query, items.size, items.map(archiveItem))

    private def archiveFound(c: HostCollection, id: com.histmersion.domain.library.collections.CollectionsService.Implementation.ArchiveFoundId): Either[Api.Error, CollectionItem] =
      c.item(CollectionId(id)).left.map(fail).flatMap(_.toRight(fail(s"no archive.org item '$id'")))

    def archiveInfo(id: com.histmersion.domain.library.collections.CollectionsService.Implementation.ArchiveInfoId): Either[Api.Error, ArchiveItem] =
      lookup(ArchiveOrg).flatMap(archiveFound(_, id)).map(archiveItem)

    /** Import a PUBLIC archive.org item. A lending-only scan is refused, not worked around: it must be borrowed on
      * archive.org, and circumventing that is not something this pipeline does.
      */
    def archiveImport(caller: Caller, req: ArchiveImportRequest): Either[Api.Error, Ingest.Report] =
      for
        c <- lookup(ArchiveOrg)
        item <- archiveFound(c, req.id)
        _ <- Either.cond(
          !item.restricted,
          (),
          fail(
            s"'${item.id.value}' is a lending-only item — borrow it on archive.org; " +
              "it is not downloadable here (no DRM circumvention)"
          )
        )
        file <- c.file(item, req.format).left.map(fail)
        report <- library.archiveImport(
          archiveItem(item),
          ArchiveFile(file.name, file.format, file.megabytes.getOrElse(0.0)),
          req
        )
      yield report

  object Implementation:
    type RegistryResultKey = RegistryResultKey.T
    object RegistryResultKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ArchiveRows = ArchiveRows.T
    object ArchiveRows extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type FailMessage = FailMessage.T
    object FailMessage extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UsageMessage = UsageMessage.T
    object UsageMessage extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LookupName = LookupName.T
    object LookupName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LinkingName = LinkingName.T
    object LinkingName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CollectionName = CollectionName.T
    object CollectionName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LinkedIdsResult = LinkedIdsResult.T
    object LinkedIdsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SearchName = SearchName.T
    object SearchName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SearchQ = SearchQ.T
    object SearchQ extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SearchLimit = SearchLimit.T
    object SearchLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SearchUnlinked = SearchUnlinked.T
    object SearchUnlinked extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ImportItemName = ImportItemName.T
    object ImportItemName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type StripHtmlResult = StripHtmlResult.T
    object StripHtmlResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type StripHtmlS = StripHtmlS.T
    object StripHtmlS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpsertCk = UpsertCk.T
    object UpsertCk extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CoverOfCk = CoverOfCk.T
    object CoverOfCk extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ConvertCk = ConvertCk.T
    object ConvertCk extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ConvertAcquireItem = ConvertAcquireItem.T
    object ConvertAcquireItem extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LinkName = LinkName.T
    object LinkName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MetadataCk = MetadataCk.T
    object MetadataCk extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OriginalCk = OriginalCk.T
    object OriginalCk extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OriginalHint = OriginalHint.T
    object OriginalHint extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PushOneResultPart2Part1 = PushOneResultPart2Part1.T
    object PushOneResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PushOneResultPart2Part2 = PushOneResultPart2Part2.T
    object PushOneResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PushOneCk = PushOneCk.T
    object PushOneCk extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PushName = PushName.T
    object PushName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpdateOneName = UpdateOneName.T
    object UpdateOneName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpdateAllName = UpdateAllName.T
    object UpdateAllName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpdateAllApply = UpdateAllApply.T
    object UpdateAllApply extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetName = SetName.T
    object SetName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AuditName = AuditName.T
    object AuditName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GapsName = GapsName.T
    object GapsName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GapsLimit = GapsLimit.T
    object GapsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SyncName = SyncName.T
    object SyncName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ArchiveSearchQ = ArchiveSearchQ.T
    object ArchiveSearchQ extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ArchiveSearchRows = ArchiveSearchRows.T
    object ArchiveSearchRows extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ArchiveFoundId = ArchiveFoundId.T
    object ArchiveFoundId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ArchiveInfoId = ArchiveInfoId.T
    object ArchiveInfoId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The most entries a search shows when not told. */
  val SearchLimit: com.histmersion.domain.library.collections.CollectionsService.SearchLimit = 20
