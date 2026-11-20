package com.histmersion
package domain.library.collections
import com.histmersion.util.problem.{Legacy, Problem, Problems}
import com.histmersion.util.problem.Text.{sentence, sentences}

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
import com.histmersion.pluginhost.{HostCollection, Registries}
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
import com.histmersion.api.media.{MediaCoversRequest, MediaItem, MediaProvider, SourceCoverParams}
import com.histmersion.api.{Api, Write}
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.library.Source
import com.histmersion.domain.library.Isbn
import com.histmersion.domain.media.covers.OpenLibrary
import com.histmersion.api.library.RepairAcquire
import com.histmersion.plugin.CollectionUpdateEntry
import com.histmersion.plugin.CollectionChange

/** The library's source collections — the Calibre library, archive.org — run by the library: searching one, importing
  * an item into the library, linking a source to an item, pushing a source's original into one, rewriting its items
  * from the records, setting fields, the audit, the gaps (sources whose original is in no collection) and the sync. The
  * collection is the plugin's; every rule of what the library stores is here.
  */
trait CollectionsService:
  def collections(): List[CollectionInfo]
  def collection(name: CollectionsService.CollectionName): Either[Api.Error, CollectionInfo]
  def search(
      name: CollectionsService.CollectionName,
      q: Option[domain.library.search.Search.Query],
      limit: Option[CollectionsService.Limit],
      unlinked: CollectionsService.SearchUnlinked
  ): Either[Api.Error, CollectionSearch]
  def importItem(
      caller: Caller,
      name: CollectionsService.CollectionName,
      req: CollectionImportRequest
  ): Either[Api.Error, CollectionImported]
  def link(
      caller: Caller,
      name: CollectionsService.CollectionName,
      req: CollectionLinkRequest
  ): Either[Api.Error, CollectionLinked]
  def push(
      caller: Caller,
      name: CollectionsService.CollectionName,
      req: CollectionPushRequest
  ): Either[Api.Error, CollectionPushed]
  def updateOne(
      caller: Caller,
      name: CollectionsService.CollectionName,
      citekey: domain.library.Citekey
  ): Either[Api.Error, CollectionUpdatedOne]
  def updateAll(
      caller: Caller,
      name: CollectionsService.CollectionName,
      apply: CollectionsService.UpdateAllApply
  ): Either[Api.Error, CollectionUpdateAll]
  def set(
      caller: Caller,
      name: CollectionsService.CollectionName,
      req: CollectionSetRequest
  ): Either[Api.Error, CollectionSetDone]
  def audit(name: CollectionsService.CollectionName): Either[Api.Error, CollectionAudit]
  def gaps(
      name: CollectionsService.CollectionName,
      limit: Option[CollectionsService.Limit]
  ): Either[Api.Error, CollectionGaps]
  def sync(
      caller: Caller,
      name: CollectionsService.CollectionName,
      req: CollectionSync.Request
  ): Either[Api.Error, CollectionSync]
  def archiveSearch(
      q: domain.library.search.Search.Query,
      rows: Option[CollectionsService.Limit]
  ): Either[Api.Error, ArchiveSearchResult]
  def archiveInfo(id: domain.library.ArchiveId): Either[Api.Error, ArchiveItem]
  def archiveImport(caller: Caller, req: ArchiveImportRequest): Either[Api.Error, Ingest.Report]

object CollectionsService:
  type Limit = Limit.T
  object Limit extends Newtype[Int, Pure]

  /** A source collection, by the name the registry knows it under. */
  type CollectionName = CollectionName.T
  object CollectionName extends Newtype[String, Pure]
  type SearchUnlinked = SearchUnlinked.T
  object SearchUnlinked extends Newtype[Boolean, Pure]
  type UpdateAllApply = UpdateAllApply.T
  object UpdateAllApply extends Newtype[Boolean, Pure]

  /** The collection the library links its sources to (`sources.calibre_id`). */
  val Calibre: CollectionName = CollectionName("calibre")

  /** archive.org's texts. */
  val ArchiveOrg: CollectionName = CollectionName("archive-org")

  /** The library's collections over the database; `registry` answers the plugin generation's collections now. */
  def apply(
      transactor: Transactor,
      library: CollectionLibrary,
      registry: () => ListMap[Registries.SourceCollectionsKey, SourceCollection],
      archiveRows: Limit
  ): CollectionsService =
    Implementation(() => transactor.read(CollectionLinksRepo.linked()), library, registry, archiveRows)

  /** The service with the linked sources held in `linked`: the same operations, the same answers. */
  final class InMemory(
      linked: AtomicReference[List[LinkedSource]],
      library: CollectionLibrary,
      registry: () => ListMap[Registries.SourceCollectionsKey, SourceCollection],
      archiveRows: Limit
  ) extends CollectionsService:
    private val run = Implementation(() => linked.get.sortBy(_.citekey.value), library, registry, archiveRows)
    export run.*

  private val StoredOriginalHint =
    " — or set cli.calibre.originals-dir (HISTMERSION_CALIBRE_ORIGINALS_DIR) to a directory holding <citekey>/paper.pdf"

  final private class Implementation(
      linked: () => List[LinkedSource],
      library: CollectionLibrary,
      registry: () => ListMap[Registries.SourceCollectionsKey, SourceCollection],
      archiveRows: Limit
  ) extends CollectionsService:

    private def fail(message: String): Api.Error = Api.Error(Problem.Unclassified(Legacy.Text(message)))
    private def usage(message: String): Api.Error =
      Api.Error(Problem.Unclassified(Legacy.Text(message)), Some(Api.Error.Code(CollectionsApi.Usage)))

    /** A refusal of the request itself, with its own problems (their case gives the CLI's exit code). */
    private def usageOf(problems: Problems): Api.Error = Api.Error(problems, Some(Api.Error.Code(CollectionsApi.Usage)))
    private def failed(problems: Problems): Api.Error = fail(problems.sentences.mkString("; "))

    private def apiKey(ck: domain.library.Citekey): Citekey = Citekey(ck.value)

    /** The citekey a request names, or the 400 that refuses it. */
    private def checked(raw: Citekey): Either[Api.Error, domain.library.Citekey] =
      domain.library.Citekey
        .parse(raw.value)
        .left
        .map(p => Write.invalid(p))

    private def lookup(name: CollectionName): Either[Api.Error, HostCollection] =
      registry()
        .get(Registries.SourceCollectionsKey(name.value))
        .map(HostCollection(HostCollection.Name(name.value), _))
        .toRight(
          Api.Error(
            Problem.Unclassified(
              Legacy
                .Text(s"no source collection '$name' is registered on this server — its plugin is off or not installed")
            ),
            Some(Api.Error.Code(CollectionsApi.NoCollection))
          )
        )

    /** The collections the library links sources to: Calibre alone (`sources.calibre_id`). */
    private def linking(name: CollectionName): Either[Api.Error, HostCollection] =
      if name == Calibre then lookup(name)
      else Left(fail(s"the library links sources to Calibre only, not to '$name'"))

    private def info(c: HostCollection): CollectionInfo =
      val reach = c.reach
      CollectionInfo(
        CollectionInfo.Name(c.name.value),
        Title(c.title.value),
        CollectionInfo.Description(c.description.value),
        CollectionCapability.values.toList
          .filter(c.can)
          .map(k => CollectionInfo.Capabilities(k.toString.toLowerCase(java.util.Locale.ROOT))),
        CollectionInfo.Reachable(reach.isRight),
        reach.left.toOption.toList.flatMap(_.toVector)
      )

    def collections(): List[CollectionInfo] =
      registry().toList.map((n, c) => info(HostCollection(HostCollection.Name(n.value), c)))

    def collection(name: CollectionName): Either[Api.Error, CollectionInfo] = lookup(name).map(info)

    private def apiAuthors(authors: List[CollectionItem.Authors]): List[AuthorName] =
      authors.map(a => AuthorName(a.value))

    private def apiYear(year: Option[Year]): Option[com.histmersion.api.catalogue.Year] =
      year.map(y => com.histmersion.api.catalogue.Year(y.value.toLong))

    private def entry(i: CollectionItem): CollectionEntry =
      CollectionEntry(
        CollectionEntry.Id(i.id.value),
        Title(i.title.fold("")(_.value)),
        apiAuthors(i.authors),
        apiYear(i.year),
        i.publisher.map(p => CollectionEntry.Publisher(p.value)),
        i.tags.map(t => CollectionEntry.Tags(t.value)),
        i.identifiers.map((k, v) =>
          CollectionEntry.IdentifiersKey(k.value) -> CollectionEntry.IdentifiersValue(v.value)
        ),
        i.files.map(f => CollectionEntry.Formats(f.name.value))
      )

    private def linkedIds(): Set[String] = linked().flatMap(_.id.value.trim.toIntOption).map(_.toString).toSet

    def search(
        name: CollectionName,
        q: Option[domain.library.search.Search.Query],
        limit: Option[Limit],
        unlinked: SearchUnlinked
    ): Either[Api.Error, CollectionSearch] =
      val max = limit.getOrElse(SearchLimit)
      if q.isEmpty && !unlinked.value then Left(usage("give a query, or --unlinked"))
      else if !unlinked.value then
        lookup(name).flatMap(
          _.search(
            CollectionQuery(Some(CollectionQuery.Text(q.fold("")(_.value))), Some(CollectionQuery.Limit(max.value)))
          ).left
            .map(failed)
            .map(items => CollectionSearch(items.map(entry), CollectionSearch.Total(items.size)))
        )
      else
        for
          c <- linking(name)
          ids = linkedIds()
          query = q
            .map(_.value.trim)
            .filter(_.nonEmpty)
            .fold(CollectionQuery(None))(t =>
              CollectionQuery(Some(CollectionQuery.Text(t)), Some(CollectionQuery.Limit(Int.MaxValue)))
            )
          items <- c.search(query).left.map(failed)
        yield
          val all = items
            .filterNot(i => i.id.value.trim.toIntOption.exists(n => ids(n.toString)))
            .sortBy(i => -i.id.value.trim.toIntOption.getOrElse(0))
          CollectionSearch(all.take(max.value.max(1)).map(entry), CollectionSearch.Total(all.size))

    // ── import ────────────────────────────────────────────────────────────────────────────────

    def importItem(
        caller: Caller,
        name: CollectionName,
        req: CollectionImportRequest
    ): Either[Api.Error, CollectionImported] =
      for
        c <- linking(name)
        _ <- req.acquireItem.fold(Right(()))(i =>
          library
            .queueItem(CollectionLibrary.QueueItemId(i.value))
            .flatMap(found =>
              Queue.fulfillable(Queue.QueueItem.Id(i.value), found).left.map(usageOf).map(_ => ())
            )
        )
        item <- c
          .item(CollectionId(req.id.value))
          .left
          .map(failed)
          .flatMap(_.toRight(fail(s"no Calibre book with id ${req.id}")))
        _ <- c.admits(item).left.map(failed)
        file <- c.file(item, req.format.map(f => SourceCollection.FileFormat(f.value))).left.map(failed)
        ck <- req.citekey.fold(ImportTarget.citekeyFor(item).left.map(usageOf))(checked)
        existing <- library.pushMeta(ck) match
          case Right(m)                => Right(Some(m.calibreId.map(i => ImportTarget.Previous(i.value))))
          case Left(e) if e.isNotFound => Right(None)
          case Left(e)                 => Left(e)
        target <- ImportTarget
          .of(ck, ImportTarget.Id(req.id.value), ImportTarget.OfExplicit(req.citekey.isDefined), existing)
          .left
          .map(usageOf)
        record <- upsert(ck, item, target)
      yield
        val cover = Option.when(req.cover.value)(coverOf(c, ck, item))
        val conversion = if req.convert.value then Some(convert(c, item, file, ck, req.acquireItem)) else None
        CollectionImported(
          apiKey(ck),
          CollectionImported.Id(req.id.value),
          Title(item.title.fold("")(_.value)),
          target match
            case ImportTarget.Update(previous) => previous.map(p => CollectionImported.RelinkedFrom(p.value))
            case ImportTarget.Create           => None
          ,
          CollectionImported.File(file.name.value),
          record,
          cover,
          conversion.flatMap(_.toOption),
          conversion.flatMap(_.left.toOption.map(e => e.problems)),
          conversion.flatMap(_.left.toOption.flatMap(_.code).map(code => CollectionImported.JobErrorCode(code.value)))
        )

    private def stripHtml(s: String): String = s.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim

    /** Create the source, or enrich it if the citekey exists — a re-import never wipes the findings and notes an agent
      * compiled; either way the record ends up linked to this entry.
      */
    private def upsert(
        ck: domain.library.Citekey,
        item: CollectionItem,
        target: ImportTarget
    ): Either[Api.Error, Source.WithFindings] =
      val summary = item.description
        .map(d => stripHtml(d.value))
        .filter(_.nonEmpty)
        .map(text => Source.Summary(text.take(500)))
      val doi = item.identifiers.get(CollectionItem.IdentifiersKey("doi")).map(d => Doi(d.value))
      target match
        case ImportTarget.Update(_) =>
          for
            current <- library.source(ck)
            res <- library.patch(
              ck,
              Source.Patch(
                metadata = Some(
                  MetadataPatch(
                    title = Some(Title(item.title.fold("")(_.value))),
                    authors = Some(apiAuthors(item.authors)),
                    year = apiYear(item.year),
                    venue = item.publisher.map(p => Source.Venue(p.value)),
                    doi = doi,
                    calibreId = Some(Source.CalibreId(item.id.value))
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
              title = Title(item.title.fold("")(_.value)),
              authors = apiAuthors(item.authors),
              doi = doi,
              year = apiYear(item.year),
              venue = item.publisher.map(p => Source.Venue(p.value)),
              status = Some(
                domain.library.Source.Status.Unassessed
                  .transformInto[Source.Status]
              ),
              tags = item.tags.map(t => Source.Tags(t.value)),
              summary = summary,
              provenance = Some(Source.Provenance("calibre")),
              calibreId = Some(Source.CalibreId(item.id.value))
            )
          )

    /** The imported book's cover, best-effort: the collection's own, else Open Library's by its ISBN; a missing cover
      * never fails the import.
      */
    private def coverOf(
        c: HostCollection,
        ck: domain.library.Citekey,
        item: CollectionItem
    ): CollectionCover =
      c.cover(item) match
        case Left(why) =>
          CollectionCover(
            CollectionCover.Line(s"$ck: ${why.sentences.mkString("; ")}"),
            warning = CollectionCover.Warning(true)
          )
        case Right(Some(fc)) =>
          library.putCover(
            SourceCoverParams(
              apiKey(ck),
              MediaItem.Provider(MediaProvider.Calibre),
              SourceCoverParams.Key(item.id.value),
              None
            ),
            CollectionLibrary.Bytes(fc.bytes.value)
          ) match
            case Right(_) => CollectionCover(CollectionCover.Line("Cover: from the Calibre library"))
            case Left(e)  =>
              CollectionCover(
                CollectionCover.Line(s"$ck: ${e.problems.sentences.mkString("; ")}"),
                warning = CollectionCover.Warning(true)
              )
        case Right(None) =>
          CoverChoice.choose(
            None,
            _ => false,
            item.identifiers.get(CollectionItem.IdentifiersKey("isbn")).toList.map(v => Isbn(v.value)),
            item.identifiers.get(CollectionItem.IdentifiersKey("oclc")).map(v => OpenLibrary.Oclc(v.value))
          ) match
            case Some(CoverChoice.Pick.OpenLibrary(kind, isbn)) if kind.value == "isbn" =>
              library.openLibraryCover(
                MediaCoversRequest(
                  dryRun = RepairAcquire.RepairRequest.DryRun(false),
                  citekey = Some(apiKey(ck)),
                  illustrations = MediaCoversRequest.Illustrations(false),
                  isbn = Some(MediaCoversRequest.Isbn(isbn.value))
                )
              ) match
                case Right(r) if r.covers.value > 0 =>
                  CollectionCover(CollectionCover.Line(s"Cover: from Open Library (ISBN $isbn)"))
                case _ =>
                  CollectionCover(CollectionCover.Line("Cover: none (no cover file in Calibre, none at Open Library)"))
            case Some(_) => CollectionCover(CollectionCover.Line("Cover: none (no cover file in Calibre)"))
            case None    => CollectionCover(CollectionCover.Line("Cover: none (no cover file in Calibre, no ISBN)"))

    /** The conversion: the same upload `histmersion ingest <file>` makes, marked as Calibre's. */
    private def convert(
        c: HostCollection,
        item: CollectionItem,
        file: CollectionFile,
        ck: domain.library.Citekey,
        acquireItem: Option[CollectionImportRequest.AcquireItem]
    ): Either[Api.Error, Ingest.Accepted] =
      val fileName = file.name.value.reverse.takeWhile(_ != '/').reverse
      c.original(item, file) match
        case Right(f) if f.bytes.value.nonEmpty =>
          library.ingest(
            Ingest.Params(
              apiKey(ck),
              Some(Ingest.Params.Filename(fileName)),
              provenance = Some(Source.Provenance("calibre")),
              acquireItem = acquireItem.map(i => Ingest.Params.AcquireItem(i.value))
            ),
            CollectionLibrary.Bytes(f.bytes.value)
          )
        case _ => Left(usage(s"${file.name} is empty — there is nothing to convert"))

    // ── link, push, update, set ───────────────────────────────────────────────────────────────

    def link(caller: Caller, name: CollectionName, req: CollectionLinkRequest): Either[Api.Error, CollectionLinked] =
      for
        c <- linking(name)
        item <- c
          .item(CollectionId(req.id.value))
          .left
          .map(failed)
          .flatMap(_.toRight(fail(s"no Calibre book with id ${req.id}")))
        ck <- checked(req.citekey)
        sw <- library.patch(
          ck,
          Source.Patch(metadata = Some(MetadataPatch(calibreId = Some(Source.CalibreId(req.id.value)))))
        )
      yield CollectionLinked(sw.source.citekey, CollectionLinked.Id(req.id.value), Title(item.title.fold("")(_.value)))

    private def metadata(ck: domain.library.Citekey, s: CalibreGap): CollectionMetadata =
      CollectionMetadata(
        CollectionMetadata.Key(ck.value),
        CollectionMetadata.Title(s.title.value),
        s.authors.map(a => CollectionMetadata.Authors(a.value)),
        s.doi.map(d => CollectionMetadata.Doi(d.value)),
        s.isbn.map(i => CollectionMetadata.Isbn(i.value)),
        s.year.map(y => Year(y.value.toInt)),
        s.venue.map(v => CollectionMetadata.Publisher(v.value)),
        s.summary.map(x => CollectionMetadata.Summary(x.value.take(500))),
        s.topics.map(t => CollectionMetadata.Tags(t.value))
      )

    /** The stored original of `ck` as a file to hand a collection; `hint` is added to the refusal when there is none.
      */
    private def original(ck: domain.library.Citekey, hint: String): Either[Api.Error, FetchedFile] =
      for
        o <- library
          .original(ck)
          .left
          .map(e =>
            if hint.isEmpty then e
            else Api.Error(Problem.Unclassified(Legacy.Text(e.problems.sentences.mkString("; ") + hint)), e.code)
          )
        ext <- Ingest
          .dispositionExtension(Ingest.Disposition(o.disposition.value))
          .toRight(fail(s"the server did not say what format '$ck' is"))
      yield FetchedFile(
        FetchedFile.Source("original"),
        None,
        FetchedFile.Bytes(o.bytes.value),
        FetchedFile.Extension(ext.value)
      )

    /** One push: the source's metadata, its stored original into the collection as a new item, and the new id recorded
      * so it stops counting as a gap.
      */
    private def pushOne(
        c: HostCollection,
        ck: domain.library.Citekey
    ): Either[Api.Error, (CollectionId, Title)] =
      for
        s <- library.pushMeta(ck)
        file <- original(ck, StoredOriginalHint)
        id <- c.add(metadata(ck, s), file).left.map(failed)
        _ <- library.pushed(CalibrePushRequest(apiKey(ck), Source.CalibreId(id.value)))
      yield (id, s.title)

    def push(caller: Caller, name: CollectionName, req: CollectionPushRequest): Either[Api.Error, CollectionPushed] =
      linking(name).flatMap { c =>
        checked(req.citekey).flatMap { ck =>
          if !req.replace.value then
            pushOne(c, ck).map((id, title) => CollectionPushed(apiKey(ck), CollectionPushed.Id(id.value), title))
          else
            for
              s <- library.pushMeta(ck)
              id <- s.calibreId
                .flatMap(_.value.trim.toIntOption)
                .toRight(
                  fail(
                    s"'$ck' is not linked to a Calibre entry — `histmersion calibre push $ck` adds one, " +
                      s"`histmersion calibre link $ck <id>` links an existing one"
                  )
                )
              file <- original(ck, "")
              fmt <- c.replace(CollectionId(id.toString), SourceCollection.ReplaceKey(ck.value), file).left.map(failed)
            yield CollectionPushed(
              apiKey(ck),
              CollectionPushed.Id(id.toString),
              s.title,
              Some(CollectionPushed.Replaced(fmt.value))
            )
        }
      }

    def updateOne(
        caller: Caller,
        name: CollectionName,
        citekey: domain.library.Citekey
    ): Either[Api.Error, CollectionUpdatedOne] =
      for
        c <- linking(name)
        s <- library.pushMeta(citekey)
        id <- s.calibreId
          .flatMap(_.value.toIntOption)
          .toRight(fail(s"'$citekey' has no Calibre entry yet — use `histmersion calibre push $citekey`"))
        changes <- c.update(CollectionId(id.toString), metadata(citekey, s)).left.map(failed)
      yield CollectionUpdatedOne(
        apiKey(citekey),
        CollectionUpdatedOne.Id(id.toString),
        changes.map(ch => CollectionUpdatedOne.Changed(ch.field.value)),
        s.title
      )

    def updateAll(caller: Caller, name: CollectionName, apply: UpdateAllApply): Either[Api.Error, CollectionUpdateAll] =
      @tailrec def links(rest: List[LinkedSource], acc: List[CollectionLink]): Either[Api.Error, List[CollectionLink]] =
        rest match
          case Nil       => Right(acc.reverse)
          case l :: tail =>
            library.pushMeta(l.citekey) match
              case Left(e)  => Left(e)
              case Right(s) => links(tail, CollectionLink(CollectionId(l.id.value), metadata(l.citekey, s)) :: acc)
      for
        c <- linking(name)
        ls <- links(linked(), Nil)
        entries <- c.updateAll(ls, SourceCollection.UpdateAllApply(apply.value)).left.map(failed)
      yield CollectionUpdateAll(entries.map(updateRow), CollectionUpdateAll.Applied(apply.value))

    private def updateRow(e: CollectionUpdateEntry): CollectionUpdateRow =
      def changes(cs: List[CollectionChange]) =
        cs.map(ch =>
          CollectionFieldChange(
            CollectionFieldChange.Field(ch.field.value),
            CollectionFieldChange.From(ch.from.value),
            CollectionFieldChange.To(ch.to.value)
          )
        )
      val (state, cs, reason) = e.outcome match
        case CollectionUpdateOutcome.Change(cs)         => ("would-change", changes(cs), None)
        case CollectionUpdateOutcome.Applied(cs)        => ("updated", changes(cs), None)
        case CollectionUpdateOutcome.Failed(cs, reason) =>
          ("failed", changes(cs), Some(reason))
        case CollectionUpdateOutcome.UpToDate        => ("up-to-date", Nil, None)
        case CollectionUpdateOutcome.Shared          => ("shared", Nil, None)
        case CollectionUpdateOutcome.Missing         => ("missing", Nil, None)
        case CollectionUpdateOutcome.Refused(reason) => ("refused", Nil, Some(reason))
      CollectionUpdateRow(
        CollectionUpdateRow.Id(e.id.value),
        e.keys.map(k => CollectionUpdateRow.Citekeys(k.value)),
        CollectionUpdateRow.State(state),
        cs,
        reason.toList.flatMap(_.toVector)
      )

    def set(caller: Caller, name: CollectionName, req: CollectionSetRequest): Either[Api.Error, CollectionSetDone] =
      linking(name).flatMap(
        _.set(
          CollectionId(req.id.value),
          req.fields.map(f => SourceCollection.FieldName(f.name.value) -> SourceCollection.FieldValue(f.value.value))
        ).left
          .map(failed)
          .map(_ =>
            CollectionSetDone(
              CollectionSetDone.Id(req.id.value),
              req.fields.map(f => CollectionSetDone.Fields(f.name.value))
            )
          )
      )

    // ── audit, gaps, sync ─────────────────────────────────────────────────────────────────────

    def audit(name: CollectionName): Either[Api.Error, CollectionAudit] =
      val recs = linked()
      val records =
        recs.map(r =>
          CollectionRecord(
            CollectionRecord.Key(r.citekey.value),
            CollectionId(r.id.value),
            CollectionRecord.Title(r.title.value),
            r.authors.map(a => CollectionRecord.Authors(a.value)),
            r.year.map(y => Year(y.value))
          )
        )
      for
        c <- linking(name)
        found <- c.audit(records).left.map(failed)
      yield CollectionAudit(
        found.map { f =>
          CollectionAudit.Row(
            CollectionAudit.Row.Id(f.id.value),
            f.keys.map(k => CollectionAudit.Row.Citekeys(k.value)),
            f.issues.map(i => CollectionAudit.Row.Codes(i.code.value)),
            f.issues.map(i => CollectionAudit.Row.Labels(i.label.value)),
            CollectionRecordView(
              Title(f.record.title.value),
              f.record.authors.map(a => AuthorName(a.value)),
              apiYear(f.record.year)
            ),
            f.item.map(i =>
              CollectionRecordView(Title(i.title.fold("")(_.value)), apiAuthors(i.authors), apiYear(i.year))
            ),
            Option.when(f.fixable.value)(CollectionAudit.Row.Fix(f.record.key.value))
          )
        },
        CollectionAudit.Checked(recs.flatMap(_.id.value.trim.toIntOption).distinct.size)
      )

    def gaps(name: CollectionName, limit: Option[Limit]): Either[Api.Error, CollectionGaps] =
      linking(name).map { _ =>
        val rows = library.gaps(limit.map(l => CollectionLibrary.Limit(l.value))).gaps.map(GapOriginal.row)
        val n = rows.count(_.counted.value)
        CollectionGaps(
          CollectionGaps.Count(n),
          CollectionGaps.Uncounted(rows.size - n),
          CollectionGaps.Total(rows.size),
          rows
        )
      }

    def sync(caller: Caller, name: CollectionName, req: CollectionSync.Request): Either[Api.Error, CollectionSync] =
      for
        g <- gaps(name, req.limit.map(l => Limit(l.value)))
        c <- linking(name)
      yield
        val pushes =
          if !req.apply.value then Nil
          else
            g.gaps.filter(_.counted.value).map { r =>
              checked(r.citekey)
                .flatMap(pushOne(c, _))
                .fold(
                  e => CollectionSync.Push(r.citekey, None, Some(e.problems)),
                  (id, _) => CollectionSync.Push(r.citekey, Some(CollectionSync.Push.Id(id.value)), None)
                )
            }
        CollectionSync(g, CollectionSync.Applied(req.apply.value), pushes)

    // ── archive.org ───────────────────────────────────────────────────────────────────────────

    private def archiveFile(f: CollectionFile): ArchiveFile =
      ArchiveFile(
        ArchiveFile.Name(f.name.value),
        f.format.map(x => ArchiveFile.Format(x.value)),
        ArchiveFile.Megabytes(f.megabytes.fold(0.0)(_.value))
      )

    private def archiveItem(i: CollectionItem): ArchiveItem =
      ArchiveItem(
        ArchiveItem.Identifier(i.id.value),
        i.title.map(t => ArchiveItem.Title(t.value)),
        i.authors.map(a => ArchiveItem.Creator(a.value)),
        apiYear(i.year),
        i.publisher.map(p => ArchiveItem.Publisher(p.value)),
        i.description.map(d => ArchiveItem.Description(d.value)),
        ArchiveItem.LendingOnly(i.restricted.value),
        i.files.map(archiveFile)
      )

    def archiveSearch(
        q: domain.library.search.Search.Query,
        rows: Option[Limit]
    ): Either[Api.Error, ArchiveSearchResult] =
      val query = q.value.trim
      if query.isEmpty then Left(fail("give a search query"))
      else
        for
          c <- lookup(ArchiveOrg)
          items <- c
            .search(
              CollectionQuery(
                Some(CollectionQuery.Text(query)),
                Some(CollectionQuery.Limit(rows.filter(_.value > 0).getOrElse(archiveRows).value))
              )
            )
            .left
            .map(failed)
        yield ArchiveSearchResult(
          ArchiveSearchResult.Query(query),
          ArchiveSearchResult.Count(items.size),
          items.map(archiveItem)
        )

    private def archiveFound(
        c: HostCollection,
        id: domain.library.ArchiveId
    ): Either[Api.Error, CollectionItem] =
      c.item(CollectionId(id.value)).left.map(failed).flatMap(_.toRight(fail(s"no archive.org item '$id'")))

    def archiveInfo(id: domain.library.ArchiveId): Either[Api.Error, ArchiveItem] =
      lookup(ArchiveOrg).flatMap(archiveFound(_, id)).map(archiveItem)

    /** Import a PUBLIC archive.org item. A lending-only scan is refused, not worked around: it must be borrowed on
      * archive.org, and circumventing that is not something this pipeline does.
      */
    def archiveImport(caller: Caller, req: ArchiveImportRequest): Either[Api.Error, Ingest.Report] =
      for
        c <- lookup(ArchiveOrg)
        item <- archiveFound(c, domain.library.ArchiveId(req.id.value))
        _ <- Either.cond(
          !item.restricted.value,
          (),
          fail(
            s"'${item.id.value}' is a lending-only item — borrow it on archive.org; " +
              "it is not downloadable here (no DRM circumvention)"
          )
        )
        file <- c.file(item, req.format.map(f => SourceCollection.FileFormat(f.value))).left.map(failed)
        report <- library.archiveImport(
          archiveItem(item),
          archiveFile(file),
          req
        )
      yield report

  /** The most entries a search shows when not told. */
  val SearchLimit: Limit = Limit(20)
