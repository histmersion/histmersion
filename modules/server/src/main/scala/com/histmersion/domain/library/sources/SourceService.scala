package com.histmersion
package domain.library.sources
import com.histmersion.util.problem.{Problem, Raised}

import com.histmersion.api.Write
import com.histmersion.domain.Caller
import com.histmersion.domain.Licence
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.library.LibraryState
import com.histmersion.domain.library.search.FilterSql
import com.histmersion.domain.library.sources.referrers.{DeleteReport, SourceReferrers}
import com.histmersion.util.database.Transactor
import com.histmersion.util.ocr.{Chunk, Chunker}
import com.histmersion.api.text.WikiLinks

import hearth.kindlings.optics.syntax.*

import java.time.{Clock, Instant, ZoneOffset}
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec
import com.histmersion.api.library.{
  CalibreGap,
  Citekey as ApiCitekey,
  DeleteCount,
  DeletePart,
  Edge,
  Rename,
  Source,
  TocEntry
}
import com.histmersion.api.Api
import com.histmersion.api.library.Finding
import com.histmersion.domain.library.Doi
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.catalogue.places.FindingPlacement
import com.histmersion.api.catalogue.Place
import com.histmersion.util.database.WritePermit
import com.histmersion.domain.{CreatedAt, Creator}

/** The library's sources: a record with its findings and the edges it makes, its old citekeys, its rename and delete
  * (what other domains hold of it follows through the referrers), and its body as read by part.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
trait SourceService:

  /** The citekey a source has now: `id` itself, or the source an old citekey was renamed to. */
  def current(id: Citekey): Option[Citekey]

  /** The source `citekey` was renamed to, when it is an old citekey. */
  def aliasOf(citekey: Citekey): Option[Citekey]

  /** The old citekeys that name `citekey` now. */
  def aliasesOf(citekey: Citekey): Set[Citekey]

  /** Every old citekey and the source it names now, by old citekey. */
  def aliases: List[Source.Alias]

  def citekeys: List[Citekey]

  /** `citekey -> its last write` in epoch milliseconds, for the seed's staleness guard. */
  def updatedAtMillis: Map[Citekey, CitekeyMillis.Millis]

  def calibreId(citekey: Citekey): Option[FullSourceRow.CalibreId]

  /** A source as read: the record and its findings, in id order. */
  def get(citekey: Citekey): Option[(Source.Detail, List[Finding.Row])]

  /** A source as written: the record with its findings and edges. */
  def load(citekey: Citekey): Option[Source]

  /** The citation-link view of the stored sources among `citekeys`, in citekey order. */
  def refs(citekeys: List[Citekey]): List[Source.Ref]

  /** The citekey each wikilink target names: exact, an old citekey, or the one source a short form fits. */
  def resolveLinks(targets: List[WikiLinks.Occurrence.Target]): Map[WikiLinks.Occurrence.Target, Citekey]

  def toc(citekey: Citekey): List[TocEntry]
  def bodyText(citekey: Citekey): FullSourceRow.OcrMarkdown
  def bodyUnderHeading(citekey: Citekey, heading: SourceService.Heading): FullSourceRow.OcrMarkdown
  def bodyChunks(citekey: Citekey): List[Chunk]

  /** The source's converted text as stored, where its figures are linked from. */
  def markdown(citekey: Citekey): FullSourceRow.OcrMarkdown

  /** Replace everything under `source.citekey`; the new last-write token. */
  def put(source: Source, caller: Caller)(using WritePermit)(using Creator.New): Source.UpdatedAt

  /** Merge `patch` into the source `requested` names (an old citekey follows); the new last-write token. */
  def patch(requested: Citekey, patch: Source.Patch, caller: Caller)(using
      WritePermit
  )(using Creator.New): Either[Api.Error, Source.UpdatedAt]

  def rename(from: Citekey, to: Citekey, caller: Caller)(using
      WritePermit
  )(using Creator.New): Either[Api.Error, Rename.Counts]

  /** What deleting the source would do; nothing is changed. */
  def deletion(citekey: Citekey): Either[Api.Error, SourceDeletePlan]

  /** Delete the source unless something refuses it; `files` names its stored originals, `day` is the log's topic. */
  def delete(
      citekey: Citekey,
      day: SourceReferrers.Day,
      files: SourceDeletePlan => List[Source.Deletion.Files],
      caller: Caller
  )(using WritePermit)(using Creator.New): Either[Api.Error, SourceDeletePlan]

  /** Promote the parked edges whose target now names one stored source; how many. */
  def resolveEdges(caller: Caller)(using WritePermit)(using Creator.New): RowCount

  // ── what acquisition, ingest and the collections read and write of a source ───────────────

  def exists(citekey: Citekey): Boolean

  /** `lower(doi) -> citekey` for the whole corpus. */
  def doiIndex: Map[Doi, Citekey]

  /** The citekey of the source holding `doi` (the first by citekey), if one does. */
  def citekeyOfDoi(doi: Doi): Option[Citekey]

  /** Store edges routed by their target (one to a source not stored yet is parked); how many were new. */
  def addEdges(edges: List[Edge], caller: Caller)(using WritePermit)(using Creator.New): RowCount

  /** The digest of the source's stored original. */
  def original(citekey: Citekey): Option[Sha256]

  /** The source that holds an original with this digest. */
  def holderOf(sha256: Sha256): Option[Citekey]

  /** The digests of the stored originals of those of `citekeys` that have one. */
  def originals(citekeys: List[Citekey]): Map[Citekey, Sha256]

  def setOriginal(citekey: Citekey, sha256: Sha256, caller: Caller)(using WritePermit): Unit

  /** The licence the source is published under, classified as it is read (Q26). */
  def licence(citekey: Citekey): Option[Licence]

  /** `licence` recorded as the source's when it has none; `true` when it was written. */
  def recordLicence(citekey: Citekey, licence: Licence.Id, caller: Caller)(using WritePermit): Boolean

  /** The sources not in Calibre yet, in citekey order, at most `limit` (none: all). */
  def calibreGaps(limit: Option[PageSize]): List[CalibreGap]

  /** What a push of the source into Calibre names it by. */
  def calibrePushMeta(citekey: Citekey): Option[CalibreGap]

  /** The Calibre id written back after a push. */
  def setCalibreId(citekey: Citekey, calibreId: FullSourceRow.CalibreId, caller: Caller)(using
      WritePermit
  ): Either[Api.Error, Unit]

object SourceService:
  type Heading = Heading.T
  object Heading extends Newtype[String, Pure]

  def apply(db: Transactor, referrers: SourceReferrers, settings: () => SourceSettings, clock: Clock): SourceService =
    Implementation(db, referrers, settings, clock)

  final private class Implementation(
      db: Transactor,
      referrers: SourceReferrers,
      settings: () => SourceSettings,
      clock: Clock
  ) extends SourceService:
    def current(id: Citekey): Option[Citekey] = db.read(SourceRepo.canonical(id))
    def aliasOf(citekey: Citekey): Option[Citekey] = db.read(SourceRepo.aliasOf(citekey))
    def aliasesOf(citekey: Citekey): Set[Citekey] = db.read(SourceRepo.aliasesOf(citekey))
    def aliases: List[Source.Alias] = db.read(SourceRepo.aliases)
    def citekeys: List[Citekey] = db.read(SourceRepo.citekeys()).toList
    def updatedAtMillis: Map[Citekey, CitekeyMillis.Millis] = db.read(SourceRepo.updatedAtMillis())
    def calibreId(citekey: Citekey): Option[FullSourceRow.CalibreId] = db.read(SourceRepo.calibreId(citekey))
    def get(citekey: Citekey): Option[(Source.Detail, List[Finding.Row])] =
      db.read(SourceRepo.get(citekey)).map((s, fs) => (s, fs.toList))
    def load(citekey: Citekey): Option[Source] = db.read(SourceRepo.load(citekey))
    def refs(citekeys: List[Citekey]): List[Source.Ref] = db.read(SourceRepo.refs(citekeys))
    def resolveLinks(targets: List[WikiLinks.Occurrence.Target]): Map[WikiLinks.Occurrence.Target, Citekey] =
      db.read(SourceRepo.resolveAliases(targets))
    def toc(citekey: Citekey): List[TocEntry] = db.read(SourceRepo.toc(citekey))
    def bodyText(citekey: Citekey): FullSourceRow.OcrMarkdown = db.read(SourceRepo.bodyText(citekey))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def bodyUnderHeading(citekey: Citekey, heading: Heading): FullSourceRow.OcrMarkdown =
      db.read(SourceRepo.bodyUnderHeading(citekey, FilterSql.SnippetRow.Heading(heading.value)))
    def bodyChunks(citekey: Citekey): List[Chunk] = db.read(SourceRepo.bodyChunks(citekey))
    def markdown(citekey: Citekey): FullSourceRow.OcrMarkdown = db.read(SourceRepo.markdown(citekey))

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def put(source: Source, caller: Caller)(using WritePermit)(using Creator.New): Source.UpdatedAt =
      val s = settings()
      db.write:
        given CreatedAt = CreatedAt.now(clock)
        SourceRepo.putIn(source, Chunker.ChunkMaxChars(s.chunkChars.value), toleranceKm(s))

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def patch(requested: Citekey, patch: Source.Patch, caller: Caller)(using
        WritePermit
    )(using Creator.New): Either[Api.Error, Source.UpdatedAt] =
      val s = settings()
      db.write:
        given CreatedAt = CreatedAt.now(clock)
        SourceRepo.patchIn(requested, patch, Chunker.ChunkMaxChars(s.chunkChars.value), toleranceKm(s))

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def toleranceKm(s: SourceSettings): FindingPlacement.Implementation.ToleranceKm =
      FindingPlacement.Implementation.ToleranceKm(s.toleranceKm.value)

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def rename(from: Citekey, to: Citekey, caller: Caller)(using
        WritePermit
    )(using Creator.New): Either[Api.Error, Rename.Counts] =
      db.write:
        given CreatedAt = CreatedAt.now(clock)
        SourceRepo.rename(from, Citekeys.RenameToNewRaw(to.value), referrers)

    def deletion(citekey: Citekey): Either[Api.Error, SourceDeletePlan] =
      db.read(SourceDeleteRepo.planIn(citekey, referrers))

    def delete(
        citekey: Citekey,
        day: SourceReferrers.Day,
        files: SourceDeletePlan => List[Source.Deletion.Files],
        caller: Caller
    )(using WritePermit)(using Creator.New): Either[Api.Error, SourceDeletePlan] =
      db.write:
        given CreatedAt = CreatedAt.now(clock)
        SourceDeleteRepo.applyIn(citekey, day, files, referrers)

    def resolveEdges(caller: Caller)(using WritePermit)(using Creator.New): RowCount =
      db.write(SourceRepo.resolveEdges())

    def exists(citekey: Citekey): Boolean = db.read(SourceRepo.exists(citekey))
    def doiIndex: Map[Doi, Citekey] = db.read(SourceRepo.doiIndex())
    def citekeyOfDoi(doi: Doi): Option[Citekey] = db.read(SourceRepo.citekeyOfDoi(doi))
    def addEdges(edges: List[Edge], caller: Caller)(using WritePermit)(using Creator.New): RowCount =
      if edges.isEmpty then RowCount(0) else db.write(SourceRepo.insertEdges(edges))
    def original(citekey: Citekey): Option[Sha256] = db.read(SourceRepo.sha256Of(citekey))
    def holderOf(sha256: Sha256): Option[Citekey] = db.read(SourceRepo.citekeyOfSha256(sha256))
    def originals(citekeys: List[Citekey]): Map[Citekey, Sha256] =
      if citekeys.isEmpty then Map.empty else db.read(SourceRepo.sha256s(citekeys))
    def setOriginal(citekey: Citekey, sha256: Sha256, caller: Caller)(using WritePermit): Unit =
      db.write(SourceRepo.setSha256(citekey, sha256))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def licence(citekey: Citekey): Option[Licence] =
      db.read(SourceRepo.licenceOf(citekey))
        .map(l => Licence.Id(l.value.trim))
        .filter(_.value.nonEmpty)
        .map(Licence.classify)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def recordLicence(citekey: Citekey, licence: Licence.Id, caller: Caller)(using WritePermit): Boolean =
      val v = licence.value.trim
      v.nonEmpty && db.write(SourceRepo.recordLicence(citekey, Licence.Id(v)))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def calibreGaps(limit: Option[PageSize]): List[CalibreGap] =
      db.read(SourceRepo.calibreGaps(limit.map(l => SourceRepo.Limit.assume(l.value))))
    def calibrePushMeta(citekey: Citekey): Option[CalibreGap] = db.read(SourceRepo.calibrePushMeta(citekey))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def setCalibreId(citekey: Citekey, calibreId: FullSourceRow.CalibreId, caller: Caller)(using
        WritePermit
    ): Either[Api.Error, Unit] =
      db.write:
        if SourceRepo.setCalibreId(citekey, calibreId).value == 0 then Left(Write.notFound(ApiCitekey(citekey.value)))
        else Right(())

  /** The sources the library holds in memory, as a database holding only the library's rows answers: no other domain
    * stores anything of a source, so a rename rewrites no other record and nothing refuses a delete; `clock` stands in
    * for the database's `now()`.
    */
  final class InMemory(state: AtomicReference[LibraryState], clock: Clock, settings: () => SourceSettings)
      extends SourceService:

    private val Token = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'").withZone(ZoneOffset.UTC)
    private def token(at: Instant): String = Token.format(at)

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def sk(ck: Citekey): LibraryState.SourcesKey = LibraryState.SourcesKey(ck.value)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def ak(ck: Citekey): LibraryState.AliasesKey = LibraryState.AliasesKey(ck.value)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def av(ck: Citekey): LibraryState.AliasesValue = LibraryState.AliasesValue(ck.value)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def tk(ck: Citekey): LibraryState.AliasTombstones = LibraryState.AliasTombstones(ck.value)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def fk(id: Finding.Id): LibraryState.Stored.FindingUidsKey = LibraryState.Stored.FindingUidsKey(id.value)

    /** A citekey this library is handed (an api source's) or holds (a key of its state), parsed: one that is none is
      * [[Citekey.parse]]'s problem.
      */
    private def held(raw: String): Either[Problem, Citekey] = Citekey.parse(raw)

    /** [[held]] where the answer has no room for a problem (a lookup, a listing, the write inside the state's update):
      * a key that is no citekey is raised as its problem ([[Raised]]), as the database would refuse the row.
      */
    private def heldKey(raw: String): Citekey = held(raw).fold(problem => throw Raised(problem), identity)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def apiKey(ck: Citekey): ApiCitekey = ApiCitekey(ck.value)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def invalid(problem: Problem): Api.Error = Api.Error(problem, Some(Api.Error.Code(Write.CodeInvalid)))

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def currentIn(st: LibraryState, id: Citekey): Option[Citekey] =
      if st.sources.contains(sk(id)) then Some(id) else st.aliases.get(ak(id)).map(v => heldKey(v.value))

    def current(id: Citekey): Option[Citekey] = currentIn(state.get, id)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def aliasOf(citekey: Citekey): Option[Citekey] = state.get.aliases.get(ak(citekey)).map(v => heldKey(v.value))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def aliasesOf(citekey: Citekey): Set[Citekey] =
      state.get.aliases.collect { case (a, ck) if ck.value == citekey.value => heldKey(a.value) }.toSet
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def aliases: List[Source.Alias] =
      state.get.aliases.toList
        .sortBy[String](_._1.value)
        .map((alias, citekey) => Source.Alias(Source.Alias.Value(alias.value), ApiCitekey(citekey.value)))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def citekeys: List[Citekey] = state.get.sources.keys.toList.sortBy[String](_.value).map(k => heldKey(k.value))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def updatedAtMillis: Map[Citekey, CitekeyMillis.Millis] =
      state.get.sources.map((k, x) => heldKey(k.value) -> CitekeyMillis.Millis(x.updatedAt.toEpochMilli))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def calibreId(citekey: Citekey): Option[FullSourceRow.CalibreId] =
      state.get.sources.get(sk(citekey)).flatMap(_.source.calibreId).map(c => FullSourceRow.CalibreId(c.value))

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def get(citekey: Citekey): Option[(Source.Detail, List[Finding.Row])] =
      state.get.sources.get(sk(citekey)).map { st =>
        val s = st.source
        val detail = Source.Detail(
          s.citekey,
          s.title,
          s.authors,
          s.year,
          s.venue,
          s.status,
          s.topics,
          s.regions,
          s.tags,
          s.period.map(_.start),
          s.period.map(_.end),
          s.summary,
          s.doi,
          s.ocrMarkdown,
          s.notes,
          Some(Source.UpdatedAt(token(st.updatedAt))),
          s.provenance,
          Some(st.uid),
          None
        )
        val findings = s.findings.sortBy[String](_.id.value).map { f =>
          Finding.Row(
            f.claim,
            f.region.map(r => Place.Region(r.value)),
            f.geo.map(_.lon),
            f.geo.map(_.lat),
            f.period.map(_.start),
            f.period.map(_.end),
            f.periodName,
            f.tags,
            f.confidence,
            f.significance,
            st.findingUids.get(fk(f.id))
          )
        }
        (detail, findings)
      }

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def loadIn(st: LibraryState, citekey: Citekey): Option[Source] =
      st.sources.get(sk(citekey)).map { stored =>
        val edges = (st.edges.toList ++ st.parked.toList)
          .filter(_.src.value == citekey.value)
          .sortBy[(String, String)](e => (Edge.Kind.wireOf(e.kind).value, e.dst.value))
        stored.source
          .modify(_.findings)
          .using(_.sortBy[String](_.id.value))
          .modify(_.edges)
          .setTo(edges)
          .modify(_.updatedAt)
          .setTo(Some(Source.UpdatedAt(token(stored.updatedAt))))
      }

    def load(citekey: Citekey): Option[Source] = loadIn(state.get, citekey)

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def refs(citekeys: List[Citekey]): List[Source.Ref] =
      val st = state.get
      citekeys.distinct
        .sortBy[String](_.value)
        .flatMap(ck =>
          st.sources
            .get(sk(ck))
            .map(x =>
              Source.Ref(
                ApiCitekey(ck.value),
                x.source.doi,
                x.source.authors,
                x.source.year,
                x.source.title,
                x.source.status
              )
            )
        )

    /** The precedence of the database's resolution: the citekey itself, an old citekey, a short form. */
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def resolveIn(
        st: LibraryState,
        targets: List[WikiLinks.Occurrence.Target]
    ): Map[WikiLinks.Occurrence.Target, Citekey] =
      targets
        .map(_.value.trim.toLowerCase)
        .filter(_.nonEmpty)
        .distinct
        .flatMap { t =>
          val exact = st.sources.keys.filter(_.value == t).map(k => heldKey(k.value) -> 0)
          val short = st.sources.keys.filter(_.value.startsWith(t + "_")).map(k => heldKey(k.value) -> 2)
          val old = st.aliases.collectFirst { case (a, v) if a.value == t => heldKey(v.value) -> 1 }
          val cands = (exact ++ short ++ old).toList
          if cands.isEmpty then None
          else
            val best = cands.map(_._2).min
            cands.filter(_._2 == best).map(_._1).distinct match
              case one :: Nil => Some(WikiLinks.Occurrence.Target(t) -> one)
              case _          => None
        }
        .toMap

    def resolveLinks(targets: List[WikiLinks.Occurrence.Target]): Map[WikiLinks.Occurrence.Target, Citekey] =
      resolveIn(state.get, targets)

    private def chunksOf(citekey: Citekey): List[Chunk] = state.get.sources.get(sk(citekey)).fold(Nil)(_.chunks)

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def toc(citekey: Citekey): List[TocEntry] =
      chunksOf(citekey)
        .filter(_.heading.isDefined)
        .groupBy(_.heading.get)
        .toList
        .map((h, cs) =>
          TocEntry(
            TocEntry.Heading(h.value),
            TocEntry.Ord(cs.map(_.ord.value).min),
            TocEntry.Chars(cs.map(_.body.value.length).sum)
          )
        )
        .sortBy[Int](_.ord.value)

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def bodyText(citekey: Citekey): FullSourceRow.OcrMarkdown =
      chunksOf(citekey) match
        case Nil => markdown(citekey)
        case cs  => FullSourceRow.OcrMarkdown(cs.sortBy[Int](_.ord.value).map(_.body.value).mkString("\n\n"))

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def bodyUnderHeading(citekey: Citekey, heading: Heading): FullSourceRow.OcrMarkdown =
      val lower = heading.value.trim.toLowerCase
      FullSourceRow.OcrMarkdown(
        chunksOf(citekey)
          .sortBy[Int](_.ord.value)
          .filter(_.heading.exists(_.value.toLowerCase.startsWith(lower)))
          .map(_.body.value)
          .mkString("\n\n")
      )

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def bodyChunks(citekey: Citekey): List[Chunk] =
      chunksOf(citekey) match
        case Nil =>
          Option(markdown(citekey))
            .filter(_.value.nonEmpty)
            .map(md => Chunk(Chunk.Ord(0), None, Chunk.Body(md.value)))
            .toList
        case cs => cs.sortBy[Int](_.ord.value)

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def markdown(citekey: Citekey): FullSourceRow.OcrMarkdown =
      FullSourceRow.OcrMarkdown(state.get.sources.get(sk(citekey)).flatMap(_.source.ocrMarkdown).fold("")(_.value))

    // ── writes ───────────────────────────────────────────────────────────────────────────────

    /** The source written under its citekey: its findings kept by id, its edges routed by their targets, the parked
      * edges that named it promoted.
      */
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def written(st: LibraryState, s: Source, at: Instant): LibraryState =
      val ck = heldKey(s.citekey.value)
      st.aliases.get(ak(ck)).foreach { now =>
        throw IllegalStateException(s"'${ck.value}' was renamed to '${now.value}' — write to '${now.value}' instead")
      }
      val before = st.sources.get(sk(ck))
      val ids = s.findings.map(_.id).toSet
      val others = st.sources.view
        .mapValues(o =>
          o.modify(_.source.findings)
            .using(
              _.filterNot(f =>
                ids(f.id) &&
                  o.source.citekey != s.citekey
              )
            )
        )
        .toMap
      val stored = LibraryState.Stored(
        s.modify(_.findings)
          .using(_.map(_.modify(_.citekey).setTo(s.citekey)))
          .modify(_.edges)
          .setTo(Nil)
          .modify(_.updatedAt)
          .setTo(None),
        before.fold(UUID.randomUUID())(_.uid),
        s.findings.map(f => fk(f.id) -> before.flatMap(_.findingUids.get(fk(f.id))).getOrElse(UUID.randomUUID())).toMap,
        s.ocrMarkdown
          .map(md => Chunker.chunk(Chunker.ChunkMarkdown(md.value), Chunker.ChunkMaxChars(settings().chunkChars.value)))
          .getOrElse(Nil),
        at,
        before.flatMap(_.sha256),
        before.flatMap(_.licence)
      )
      val placed = st.modify(_.sources).setTo(others.updated(sk(ck), stored))
      val cleared =
        placed
          .modify(_.edges)
          .using(_.filterNot(_.src == s.citekey))
          .modify(_.parked)
          .using(_.filterNot(_.src == s.citekey))
      promoted(edgesInserted(cleared, s.edges.map(_.modify(_.src).setTo(s.citekey)).distinct), ck)

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def edgesInserted(st: LibraryState, edges: List[Edge]): LibraryState =
      @tailrec def loop(rest: List[Edge], resolved: Set[Edge], parked: Set[Edge]): LibraryState =
        rest match
          case e :: tail =>
            Citekey.option(e.dst.value).flatMap(currentIn(st, _)) match
              case Some(dst) => loop(tail, resolved + e.modify(_.dst).setTo(ApiCitekey(dst.value)), parked)
              case None      => loop(tail, resolved, parked + e)
          case Nil => st.modify(_.edges).setTo(resolved).modify(_.parked).setTo(parked)
      loop(edges, st.edges, st.parked)

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def promoted(st: LibraryState, citekey: Citekey): LibraryState =
      val (waiting, rest) = st.parked.partition(_.dst.value == citekey.value)
      st.modify(_.edges).using(_ ++ waiting).modify(_.parked).setTo(rest)

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def put(source: Source, caller: Caller)(using WritePermit)(using Creator.New): Source.UpdatedAt =
      val at = clock.instant()
      val _ = state.updateAndGet(written(_, source, at))
      Source.UpdatedAt(token(at))

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def patch(requested: Citekey, patch: Source.Patch, caller: Caller)(using
        WritePermit
    )(using Creator.New): Either[Api.Error, Source.UpdatedAt] =
      val at = clock.instant()
      var answer: Either[Api.Error, Source.UpdatedAt] = Left(Write.notFound(apiKey(requested)))
      val _ = state.updateAndGet { st =>
        val citekey = currentIn(st, requested).getOrElse(requested)
        loadIn(st, citekey) match
          case None           => { answer = Left(Write.notFound(apiKey(citekey))); st }
          case Some(existing) =>
            Source.Write.precondition(existing, patch) match
              case Left(msg) =>
                answer = Left(Api.Error(msg, Some(Api.Error.Code(Write.CodePrecondition))))
                st
              case Right(_) =>
                Source.Write.applyPatch(
                  existing,
                  Source.Write.srcAliased(patch, apiKey(requested), apiKey(citekey))
                ) match
                  case Left(refusal) =>
                    answer = Left(refusal.error)
                    st
                  case Right(updated) => { answer = Right(Source.UpdatedAt(token(at))); written(st, updated, at) }
      }
      answer

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def rename(from: Citekey, to: Citekey, caller: Caller)(using
        WritePermit
    )(using Creator.New): Either[Api.Error, Rename.Counts] =
      Citekeys
        .renameTo(from, Citekeys.RenameToNewRaw(to.value))
        .left
        .map(p => Write.invalid(p))
        .flatMap { validated =>
          val at = clock.instant()
          var answer: Either[Api.Error, Rename.Counts] = Left(Write.notFound(apiKey(from)))
          val _ = state.updateAndGet { st =>
            renamedIn(st, from, validated, at) match
              case Left(e)        => { answer = Left(e); st }
              case Right((n, rc)) => { answer = Right(rc); n }
          }
          answer
        }

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def renamedIn(
        st: LibraryState,
        from: Citekey,
        to: Citekey,
        at: Instant
    ): Either[Api.Error, (LibraryState, Rename.Counts)] =
      val idTaken = st.sources.values.flatMap(_.source.findings).map(_.id).find(_.value.startsWith(to.value + "#"))
      if !st.sources.contains(sk(from)) then
        Left(st.aliases.get(ak(from)) match
          case Some(now) =>
            invalid(
              Problem.Conflict
                .OldKey(
                  Problem.TypeName.of(Citekey),
                  Problem.Raw.of(from),
                  Problem.Raw(s"$now"),
                  Problem.Conflict.Action.Rename
                )
            )
          case None => Write.notFound(apiKey(from)))
      else if st.sources.contains(sk(to)) then
        Left(invalid(Problem.Conflict.AlreadyExists(Problem.TypeName.of(Citekey), Problem.Raw.of(to))))
      else
        st.aliases.get(ak(to)) match
          case Some(other) if other.value != from.value =>
            Left(
              invalid(
                Problem.Conflict.Aliased(Problem.TypeName.of(Citekey), Problem.Raw.of(to), Problem.Raw(s"$other"))
              )
            )
          case back =>
            idTaken match
              case Some(id) =>
                Left(
                  invalid(
                    Problem.Conflict.Collides(Problem.TypeName.of(Finding.Id), Problem.Raw.of(id), Problem.Raw.of(to))
                  )
                )
              case None =>
                val unaliased =
                  if back.isDefined then
                    st.modify(_.aliases).using(_ - ak(to)).modify(_.aliasTombstones).using(_ + tk(to))
                  else st
                Right(renamedNow(unaliased, from, to, at))

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def renamedNow(st: LibraryState, from: Citekey, to: Citekey, at: Instant): (LibraryState, Rename.Counts) =
      val fromApi = apiKey(from)
      val toApi = apiKey(to)
      val shorts = resolveIn(st, Rename.shortForms(fromApi).map(f => WikiLinks.Occurrence.Target(f.value))).collect {
        case (f, ck) if ck == from => f
      }.toSet
      val prefix = from.value + "#"
      val stored = st.sources(sk(from))
      val renumbered = stored.source.findings.count(_.id.value.startsWith(prefix))
      def newId(id: Finding.Id): Finding.Id = Rename.findingId(id, fromApi, toApi).getOrElse(id)
      val moved = stored
        .modify(_.source.citekey)
        .setTo(toApi)
        .modify(_.source.findings)
        .using(_.map(f => f.modify(_.id).using(newId).modify(_.citekey).setTo(toApi)))
        .modify(_.findingUids)
        .using(_.map((id, u) => fk(newId(Finding.Id(id.value))) -> u))
        .modify(_.updatedAt)
        .setTo(at)
      def follow(e: Edge): Edge =
        e.modify(_.src)
          .using(s => if s.value == from.value then toApi else s)
          .modify(_.dst)
          .using(d => if d.value == from.value then toApi else d)
      val keyed = st
        .modify(_.sources)
        .using(_ - sk(from) + (sk(to) -> moved))
        .modify(_.aliases)
        .using(_.map((a, ck) => a -> (if ck.value == from.value then av(to) else ck)))
        .modify(_.edges)
        .using(_.map(follow))
        .modify(_.parked)
        .using(_.map(e => e.modify(_.src).using(s => if s.value == from.value then toApi else s)))
      val waiting =
        keyed.parked.map(_.dst).filter(d => d.value == to.value || to.value.startsWith(d.value + "_")).toList.distinct
      val resolvedNow = resolveIn(keyed, waiting.map(d => WikiLinks.Occurrence.Target(d.value)))
      val promotable = waiting.filter(d => resolvedNow.get(WikiLinks.Occurrence.Target(d.value)).contains(to)).toSet
      val promoting = keyed.parked.filter(e => promotable(e.dst) && e.src.value != to.value)
      val newEdges = promoting.map(_.modify(_.dst).setTo(toApi)).filterNot(keyed.edges)
      val linked = keyed.modify(_.edges).using(_ ++ newEdges).modify(_.parked).using(_ -- promoting)
      val target: WikiLinks.Occurrence.Target => Option[WikiLinks.Occurrence.Target] =
        t => Option.when(t.value == from.value || shorts(t))(WikiLinks.Occurrence.Target(to.value))
      val needle = from.value.takeWhile(_ != '_')
      val retargeted = linked.sources.toList.sortBy[String](_._1.value).flatMap { (ck, x) =>
        val text = (x.source.notes.fold("")(_.value) + " " + x.source.summary.fold("")(_.value)).toLowerCase
        if !text.contains(needle) then None
        else
          val n = x.source.notes.map(note => WikiLinks.retarget(WikiLinks.Body(note.value), target))
          val s = x.source.summary.map(sum => WikiLinks.retarget(WikiLinks.Body(sum.value), target))
          val count = n.fold(0)(_._2.value) + s.fold(0)(_._2.value)
          Option.when(count > 0)(
            (ck, count, n.map(r => Source.Notes(r._1.value)), s.map(r => Source.Summary(r._1.value)))
          )
      }
      val rewritten = retargeted.map((ck, _, n, s) =>
        ck -> linked
          .sources(ck)
          .modify(_.source.notes)
          .setTo(n)
          .modify(_.source.summary)
          .setTo(s)
          .modify(_.updatedAt)
          .setTo(at)
      )
      val texts = linked.modify(_.sources).using(_ ++ rewritten)
      val aliased =
        texts.modify(_.aliases).using(_.updated(ak(from), av(to))).modify(_.aliasTombstones).using(_ - tk(from))
      (
        aliased,
        Rename.Counts(
          Rename.Counts.Findings(renumbered),
          Nil,
          retargeted.map(r => Rename.Counts.Sources(r._1.value)),
          Rename.Counts.Links(retargeted.map(_._2).sum),
          Nil,
          Rename.Counts.EdgesPromoted(newEdges.size)
        )
      )

    // ── delete ───────────────────────────────────────────────────────────────────────────────

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
    private def doiKey(doi: Option[com.histmersion.api.library.Doi]): Option[String] =
      doi.map(_.value.trim.toLowerCase.replaceFirst("^(https?://(dx\\.)?doi\\.org/|doi:\\s*)", "")).filter(_.nonEmpty)

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def planIn(st: LibraryState, requested: Citekey): Either[Api.Error, SourceDeletePlan] =
      st.sources.get(sk(requested)) match
        case None =>
          Left(st.aliases.get(ak(requested)) match
            case Some(now) =>
              invalid(
                Problem.Conflict
                  .OldKey(
                    Problem.TypeName.of(Citekey),
                    Problem.Raw.of(requested),
                    Problem.Raw(s"$now"),
                    Problem.Conflict.Action.Delete
                  )
              )
            case None => Write.notFound(apiKey(requested)))
        case Some(x) =>
          val ck = requested
          val aliases =
            st.aliases.collect { case (a, c) if c.value == ck.value => heldKey(a.value) }.toList.sortBy[String](_.value)
          val edgesIn = st.edges
            .filter(e => e.dst.value == ck.value && e.src.value != ck.value)
            .toList
            .sortBy[(String, String)](e => (e.src.value, Edge.Kind.wireOf(e.kind).value))
          import com.histmersion.api.library.DeletePart.*
          val counts = List(
            Findings -> x.source.findings.size,
            EdgesOut -> st.edges.count(_.src.value == ck.value),
            ParkedOut -> st.parked.count(_.src.value == ck.value),
            EdgesIn -> edgesIn.size,
            Chunks -> x.chunks.size,
            Aliases -> aliases.size,
            Identifiers -> doiKey(x.source.doi).size
          ).collect { case (p, rows) if rows > 0 => DeleteCount(p, DeleteCount.Rows(rows)) }
          val d = Source.Deletion(
            citekey = apiKey(ck),
            title = x.source.title,
            applied = Source.Deletion.Applied(false),
            counts = counts,
            aliases = aliases.map(a => Source.Deletion.Aliases(a.value)),
            edgesIn = edgesIn,
            calibreId = x.source.calibreId.map(c => Source.CalibreId(c.value.trim)).filter(_.value.nonEmpty)
          )
          Right(
            SourceDeletePlan(
              d,
              None,
              SourceDeletePlan.ShaShared(false),
              (ck :: aliases).map(k => SourceDeletePlan.Keys(k.value)),
              DeleteReport(counts = counts)
            )
          )

    def deletion(citekey: Citekey): Either[Api.Error, SourceDeletePlan] = planIn(state.get, citekey)

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def delete(
        citekey: Citekey,
        day: SourceReferrers.Day,
        files: SourceDeletePlan => List[Source.Deletion.Files],
        caller: Caller
    )(using WritePermit)(using Creator.New): Either[Api.Error, SourceDeletePlan] =
      var answer: Either[Api.Error, SourceDeletePlan] = Left(Write.notFound(apiKey(citekey)))
      val _ = state.updateAndGet { st =>
        planIn(st, citekey) match
          case Left(e)   => { answer = Left(e); st }
          case Right(p0) =>
            val p = p0
              .modify(_.deletion.files)
              .setTo(files(p0))
              .modify(_.deletion.applied)
              .setTo(Source.Deletion.Applied(true))
            answer = Right(p)
            val ck = p.deletion.citekey
            val incoming = st.edges.filter(e => e.dst == ck && e.src != ck)
            st.modify(_.sources)
              .using(_ - sk(citekey))
              .modify(_.aliases)
              .using(_.filterNot(_._2.value == citekey.value))
              .modify(_.edges)
              .using(_.filterNot(e => e.src == ck || e.dst == ck))
              .modify(_.parked)
              .using(ps => ps.filterNot(_.src == ck) ++ incoming)
      }
      answer

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def resolveEdges(caller: Caller)(using WritePermit)(using Creator.New): RowCount =
      var moved = 0
      val _ = state.updateAndGet { st =>
        val targets = resolveIn(st, st.parked.toList.map(e => WikiLinks.Occurrence.Target(e.dst.value)))
        val moves = st.parked.toList.flatMap(e =>
          targets
            .get(WikiLinks.Occurrence.Target(e.dst.value.trim.toLowerCase))
            .filter(_.value != e.src.value)
            .map(e -> _)
        )
        moved = moves.size
        st.modify(_.edges)
          .using(_ ++ moves.map((e, ck) => e.modify(_.dst).setTo(apiKey(ck))))
          .modify(_.parked)
          .using(_ -- moves.map(_._1))
      }
      RowCount(moved)

    // ── what acquisition, ingest and the collections read and write of a source ─────────────

    def exists(citekey: Citekey): Boolean = state.get.sources.contains(sk(citekey))

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def citekeyOfDoi(doi: Doi): Option[Citekey] =
      val d = doi.value.trim.toLowerCase
      state.get.sources.toList.sortBy[String](_._1.value).collectFirst {
        case (ck, x) if x.source.doi.exists(_.value.toLowerCase == d) => heldKey(ck.value)
      }

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def doiIndex: Map[Doi, Citekey] =
      state.get.sources.toList
        .sortBy[String](_._1.value)
        .flatMap((ck, x) =>
          x.source.doi
            .filter(_.value.nonEmpty)
            .flatMap(d => Doi.option(d.value.toLowerCase))
            .map(_ -> heldKey(ck.value))
        )
        .toMap

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def addEdges(edges: List[Edge], caller: Caller)(using WritePermit)(using Creator.New): RowCount =
      val es = edges.distinct
      if es.isEmpty then RowCount(0)
      else
        var added = 0
        val _ = state.updateAndGet { st =>
          val inserted = edgesInserted(st, es)
          added = (inserted.edges -- st.edges).size + (inserted.parked -- st.parked).size
          inserted
        }
        RowCount(added)

    private def stored(citekey: Citekey): Option[LibraryState.Stored] = state.get.sources.get(sk(citekey))

    def original(citekey: Citekey): Option[Sha256] = stored(citekey).flatMap(_.sha256)

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def holderOf(sha256: Sha256): Option[Citekey] =
      state.get.sources.toList.sortBy[String](_._1.value).collectFirst {
        case (ck, x) if x.sha256.contains(sha256) => heldKey(ck.value)
      }

    def originals(citekeys: List[Citekey]): Map[Citekey, Sha256] =
      citekeys.flatMap(ck => original(ck).map(ck -> _)).toMap

    private def updated(citekey: Citekey)(f: LibraryState.Stored => LibraryState.Stored): Boolean =
      val before = state.getAndUpdate(st =>
        st.modify(_.sources).using(ss => ss.get(sk(citekey)).fold(ss)(x => ss.updated(sk(citekey), f(x))))
      )
      before.sources.contains(sk(citekey))

    def setOriginal(citekey: Citekey, sha256: Sha256, caller: Caller)(using WritePermit): Unit =
      val _ = updated(citekey)(_.modify(_.sha256).setTo(Some(sha256)))

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def licence(citekey: Citekey): Option[Licence] =
      stored(citekey)
        .flatMap(_.licence)
        .map(l => Licence.Id(l.value.trim))
        .filter(_.value.nonEmpty)
        .map(Licence.classify)

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def recordLicence(citekey: Citekey, licence: Licence.Id, caller: Caller)(using WritePermit): Boolean =
      val v = licence.value.trim
      val open = stored(citekey).exists(_.licence.forall(_.value.trim.isEmpty))
      v.nonEmpty && open && updated(citekey)(
        _.modify(_.licence).setTo(Some(LibraryState.Stored.Licence(v))).modify(_.updatedAt).setTo(clock.instant())
      )

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def gapOf(x: LibraryState.Stored): CalibreGap =
      val s = x.source
      CalibreGap(
        s.citekey,
        s.title,
        s.authors.map(a => CalibreGap.Authors(a.value)),
        s.doi,
        s.year,
        s.venue,
        s.summary.map(m => CalibreGap.Summary(m.value)),
        s.topics.map(t => CalibreGap.Topics(t.value)),
        s.provenance.map(p => CalibreGap.Provenance(p.value)),
        s.calibreId.filter(_.value.nonEmpty)
      )

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def calibreGaps(limit: Option[PageSize]): List[CalibreGap] =
      state.get.sources.toList
        .sortBy[String](_._1.value)
        .map(_._2)
        .filter(x => x.source.calibreId.forall(_.value.isEmpty) && !x.source.provenance.exists(_.value == "calibre"))
        .take(limit.map(_.value).filter(_ > 0).getOrElse(Int.MaxValue))
        .map(gapOf)

    def calibrePushMeta(citekey: Citekey): Option[CalibreGap] = stored(citekey).map(gapOf)

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def setCalibreId(citekey: Citekey, calibreId: FullSourceRow.CalibreId, caller: Caller)(using
        WritePermit
    ): Either[Api.Error, Unit] =
      if updated(citekey)(
          _.modify(_.source.calibreId)
            .setTo(Some(Source.CalibreId(calibreId.value)))
            .modify(_.updatedAt)
            .setTo(clock.instant())
        )
      then Right(())
      else Left(Write.notFound(apiKey(citekey)))
