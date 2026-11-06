package com.histmersion.api.library
import hearth.kindlings.optics.syntax.*

import com.histmersion.api.exchange.MergeIssue
import com.histmersion.api.{Ascii, WireEnumValues}
import com.histmersion.api.catalogue.Period
import com.histmersion.api.given
import com.histmersion.api.Dataset
import com.histmersion.api.GeoPoint
import com.histmersion.api.Plural
import com.histmersion.api.StrictJson
import com.histmersion.api.Write.*
import com.histmersion.api.checks.FormatIssue
import com.histmersion.api.media.MediaThumb
import com.histmersion.api.{ListPatch, StrictJson}
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.Codec
import java.util.UUID
import scala.annotation.tailrec
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given

final case class Source(
    citekey: com.histmersion.api.library.Citekey,
    title: com.histmersion.api.library.Title,
    authors: List[com.histmersion.api.library.AuthorName],
    doi: Option[com.histmersion.api.library.Doi],
    year: Option[com.histmersion.api.catalogue.Year],
    venue: Option[com.histmersion.api.library.Venue],
    status: com.histmersion.api.library.Source.Status,
    topics: List[Source.Topics],
    regions: List[Source.Regions],
    tags: List[Source.Tags],
    period: Option[Period],
    summary: Option[Source.Summary],
    citedByCount: Option[Source.CitedByCount],
    influentialCitations: Option[Source.InfluentialCitations],
    provenance: Option[Source.Provenance],
    calibreId: Option[Source.CalibreId],
    ocrMarkdown: Option[Source.OcrMarkdown] = None,
    findings: List[Finding] = Nil,
    edges: List[Edge] = Nil,
    notes: Option[Source.Notes] = None,
    updatedAt: Option[Source.UpdatedAt] = None
)

object Source:

  type Topics = Topics.T
  object Topics extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Regions = Regions.T
  object Regions extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Tags = Tags.T
  object Tags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Summary = Summary.T
  object Summary extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CitedByCount = CitedByCount.T
  object CitedByCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InfluentialCitations = InfluentialCitations.T
  object InfluentialCitations extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Provenance = Provenance.T
  object Provenance extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CalibreId = CalibreId.T
  object CalibreId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OcrMarkdown = OcrMarkdown.T
  object OcrMarkdown extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Notes = Notes.T
  object Notes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  enum Status:
    case Foundational, Current, Contested, Superseded, Debunked, Retracted, Unassessed

    /** The wire / storage spelling (`current`), as the JSON codec writes it. */
    def wire: com.histmersion.api.library.Source.Status.WireResult = Ascii.lower(_root_.com.histmersion.api.Ascii.LowerS(toString))

  object Status:

    type WireResult = WireResult.T
    object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FromStringS = FromStringS.T
    object FromStringS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FromWireS = FromWireS.T
    object FromWireS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def fromString(s: com.histmersion.api.library.Source.Status.FromStringS): Status =
      values.find(_.toString.equalsIgnoreCase(s)).getOrElse(Unassessed)

    given WireEnumValues[Status]:
      def all: Array[Status] = Status.values
      def wire(e: Status): com.histmersion.api.library.Source.Status.WireResult = _root_.com.histmersion.api.library.Source.Status.WireResult(e.toString.toLowerCase)
      def fromWire(s: com.histmersion.api.library.Source.Status.FromWireS): Option[Status] =
        Status.values.find(_.toString.equalsIgnoreCase(s))

  final case class Alias(alias: Alias.Value, citekey: com.histmersion.api.library.Citekey)
      derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object Alias:
    type Value = Value.T
    object Value extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Candidate(
      id: Candidate.Id,
      goal: Candidate.Goal,
      doi: com.histmersion.api.library.Doi,
      title: com.histmersion.api.library.Title,
      year: Option[com.histmersion.api.catalogue.Year] = None,
      authors: List[com.histmersion.api.library.AuthorName] = (Nil).map(com.histmersion.api.library.AuthorName.apply),
      venue: Option[com.histmersion.api.library.Venue] = None,
      abstractText: Option[Candidate.AbstractText] = None,
      isOa: Candidate.IsOa = _root_.com.histmersion.api.library.Source.Candidate.IsOa(false),
      arxivId: Option[Candidate.ArxivId] = None,
      foundBy: List[Candidate.FoundBy] = Nil,
      state: Candidate.State = Candidates.State.New.wire,
      firstSeen: Candidate.FirstSeen = _root_.com.histmersion.api.library.Source.Candidate.FirstSeen(""),
      lastSeen: Candidate.LastSeen = _root_.com.histmersion.api.library.Source.Candidate.LastSeen("")
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object Candidate:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Goal = Goal.T
    object Goal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AbstractText = AbstractText.T
    object AbstractText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type IsOa = IsOa.T
    object IsOa extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ArxivId = ArxivId.T
    object ArxivId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FoundBy = FoundBy.T
    object FoundBy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type State = State.T
    object State extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FirstSeen = FirstSeen.T
    object FirstSeen extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LastSeen = LastSeen.T
    object LastSeen extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  object Delete:

    type CalibreLineResult = CalibreLineResult.T
    object CalibreLineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CalibreLineCalibreId = CalibreLineCalibreId.T
    object CalibreLineCalibreId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RefusalResult = RefusalResult.T
    object RefusalResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RefusalCk = RefusalCk.T
    object RefusalCk extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LogTextResult = LogTextResult.T
    object LogTextResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MarkdownResult = MarkdownResult.T
    object MarkdownResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def calibreLine(calibreId: Option[com.histmersion.api.library.Source.Delete.CalibreLineCalibreId], check: Option[CalibreEntryCheck]): com.histmersion.api.library.Source.Delete.CalibreLineResult =
      calibreId match
        case None     => _root_.com.histmersion.api.library.Source.Delete.CalibreLineResult("Calibre: no linked entry.")
        case Some(id) =>
          check match
            case Some(CalibreEntryCheck.Present) =>
              _root_.com.histmersion.api.library.Source.Delete.CalibreLineResult(s"Calibre: entry $id is linked and is NOT touched — remove it in Calibre if it should go too.")
            case Some(CalibreEntryCheck.Missing) =>
              _root_.com.histmersion.api.library.Source.Delete.CalibreLineResult(s"Calibre: entry $id no longer exists in the library — nothing to remove there.")
            case Some(CalibreEntryCheck.Unchecked(why)) =>
              _root_.com.histmersion.api.library.Source.Delete.CalibreLineResult(s"Calibre: the record links entry $id (the library could not be checked: $why); it is NOT touched — " +
                "remove it in Calibre if it is there and should go too.")
            case None =>
              _root_.com.histmersion.api.library.Source.Delete.CalibreLineResult(s"Calibre: the record links entry $id (not checked against the library); it is NOT touched — " +
                "remove it in Calibre if it is there and should go too.")

    def refusal(ck: com.histmersion.api.library.Source.Delete.RefusalCk, blockers: List[DeleteBlocker]): com.histmersion.api.library.Source.Delete.RefusalResult =
      val groups = blockers.groupBy(_.kind).toList.sortBy(_._1.ordinal).map { (k, bs) =>
        s"${k.label} ${bs.map(b => b.id + b.title.fold("")(t => s" ($t)")).mkString(", ")}"
      }
      _root_.com.histmersion.api.library.Source.Delete.RefusalResult(s"'$ck' cannot be deleted — ${groups.mkString("; ")}. Fix those first (remove the citation from " +
        "each article, re-source the events, let the job finish); there is no override")

    def logText(d: Deletion): com.histmersion.api.library.Source.Delete.LogTextResult =
      def short(p: DeletePart) = p match
        case DeletePart.EdgesIn => "edges in (parked)"
        case other              => other.wire.replace('-', ' ')
      val parts = d.counts.filter(_.rows > 0).map(c => s"${c.rows} ${short(c.part)}")
      val files = if d.files.isEmpty then Nil else List(Plural.count(_root_.com.histmersion.api.Plural.CountN((d.files.size).toLong), _root_.com.histmersion.api.Plural.CountSingular("stored original")))
      val calibre = d.calibreId.map(id => s"Calibre entry $id kept").toList
      _root_.com.histmersion.api.library.Source.Delete.LogTextResult(s"deleted ${d.citekey} (${d.title})" +
        (parts ++ files ++ calibre match
          case Nil => ""
          case xs  => ": " + xs.mkString(", ")))

    def markdown(d: Deletion, calibre: Option[CalibreEntryCheck] = None): com.histmersion.api.library.Source.Delete.MarkdownResult =
      val head =
        if d.applied then s"# Deleted `${d.citekey}`"
        else if d.refused then s"# Cannot delete `${d.citekey}`"
        else s"# Would delete `${d.citekey}` (dry run)"
      val blockers =
        if d.blockers.isEmpty then Nil
        else
          List("", "**Refused — fix these first (there is no override):**", "") ++
            d.blockers.map(b => s"- ${b.kind.label}: `${b.id}`${b.title.fold("")(t => s" — $t")}")
      val verb = if d.applied then "removed" else "would go"
      val counts =
        if d.counts.isEmpty then List("", s"Nothing besides the record itself $verb.")
        else
          List("", s"**${if d.applied then "Removed" else "Would remove"}** (with the record itself):", "") ++
            d.counts.map(c => s"- ${c.part.label}: ${c.rows}")
      val aliases = if d.aliases.isEmpty then Nil
      else List("", s"Old citekeys that stop resolving: ${d.aliases.map(a => s"`$a`").mkString(", ")}")
      val edgesIn = if d.edgesIn.isEmpty then Nil
      else
        List(
          "",
          s"Edges of other sources to it (${if d.applied then "parked" else "parked, not lost"}: " +
            "they come back if it is ingested again):",
          ""
        ) ++
          d.edgesIn.map(e => s"- `${e.src}` ${e.kind} → `${e.dst}`")
      val coverage = if d.coverage.isEmpty then Nil
      else List("", s"Coverage reasons dropped from: ${d.coverage.map(s => s"`$s`").mkString(", ")}")
      val files =
        if d.files.isEmpty then List("", "Stored original: none on the server.")
        else
          List("", s"Stored original (${if d.applied then "removed" else "removed after the commit"}):", "") ++
            d.files.map(f => s"- `$f`")
      val failed = if d.filesFailed.isEmpty then Nil
      else List("", "**Could not remove** (logged; remove by hand):", "") ++ d.filesFailed.map(f => s"- $f")
      val mentions = if d.mentions.isEmpty then Nil
      else List("", s"Queue items that mention it (left as they are): ${d.mentions.map(i => s"#$i").mkString(", ")}")
      val calibreLines = List("", calibreLine(d.calibreId, calibre))
      val tail =
        if d.applied then
          List(
            "",
            s"Logged as item #${d.logEntry.fold("?")(_.toString)} of the log queue. A tombstone keeps a " +
              s"bundle merge from bringing it back. The exported mirror's `sources/${d.citekey}.md` is not removed by " +
              "`histmersion export` — delete it there by hand."
          )
        else if d.refused then Nil
        else List("", s"_Dry run. Re-run with `--apply` to delete._")
      _root_.com.histmersion.api.library.Source.Delete.MarkdownResult((List(head, "", s"**${d.title}**") ++ blockers ++ counts ++ aliases ++ edgesIn ++ coverage ++ files ++ failed ++
        mentions ++ calibreLines ++ tail).mkString("\n"))

  final case class DeleteRequest(apply: DeleteRequest.Apply = _root_.com.histmersion.api.library.Source.DeleteRequest.Apply(false)) derives KindlingsSchema, com.histmersion.api.json.Schema
  object DeleteRequest:

    type Apply = Apply.T
    object Apply extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given Codec.AsObject[DeleteRequest] = StrictJson.codec(KindlingsCodecAsObject.derived[DeleteRequest])

  final case class Deletion(
      citekey: com.histmersion.api.library.Citekey,
      title: Deletion.Title,
      applied: Deletion.Applied,
      blockers: List[DeleteBlocker] = Nil,
      counts: List[DeleteCount] = Nil,
      aliases: List[Deletion.Aliases] = Nil,
      edgesIn: List[Edge] = Nil,
      coverage: List[Deletion.Coverage] = Nil,
      files: List[Deletion.Files] = Nil,
      filesFailed: List[Deletion.FilesFailed] = Nil,
      mentions: List[Deletion.Mentions] = Nil,
      calibreId: Option[com.histmersion.api.library.Source.CalibreId] = None,
      logEntry: Option[Deletion.LogEntry] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema:
    def refused: com.histmersion.api.library.Source.Deletion.RefusedResult = _root_.com.histmersion.api.library.Source.Deletion.RefusedResult(blockers.nonEmpty)
    def count(p: DeletePart): com.histmersion.api.library.Source.Deletion.CountResult = counts.find(_.part == p).fold(0)(_.rows)

  object Deletion:
    type RefusedResult = RefusedResult.T
    object RefusedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CountResult = CountResult.T
    object CountResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Title = Title.T
    object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Applied = Applied.T
    object Applied extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Aliases = Aliases.T
    object Aliases extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Coverage = Coverage.T
    object Coverage extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Files = Files.T
    object Files extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FilesFailed = FilesFailed.T
    object FilesFailed extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Mentions = Mentions.T
    object Mentions extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LogEntry = LogEntry.T
    object LogEntry extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Detail(
      citekey: com.histmersion.api.library.Citekey,
      title: com.histmersion.api.library.Title,
      authors: List[com.histmersion.api.library.AuthorName],
      year: Option[com.histmersion.api.catalogue.Year],
      venue: Option[com.histmersion.api.library.Venue],
      status: com.histmersion.api.library.Source.Status,
      topics: List[com.histmersion.api.library.Source.Topics],
      regions: List[com.histmersion.api.library.Source.Regions],
      tags: List[com.histmersion.api.library.Source.Tags],
      periodStart: Option[com.histmersion.api.catalogue.Year],
      periodEnd: Option[com.histmersion.api.catalogue.Year],
      summary: Option[com.histmersion.api.library.Source.Summary],
      doi: Option[com.histmersion.api.library.Doi],
      ocrMarkdown: Option[Detail.OcrMarkdown],
      notes: Option[com.histmersion.api.library.Source.Notes] = None,
      updatedAt: Option[Detail.UpdatedAt] = None,
      provenance: Option[com.histmersion.api.library.Source.Provenance] = None,
      uid: Option[java.util.UUID] = None,
      origin: Option[java.util.UUID] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object Detail:
    type OcrMarkdown = OcrMarkdown.T
    object OcrMarkdown extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpdatedAt = UpdatedAt.T
    object UpdatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Graph(
      root: Graph.Root,
      depth: Graph.Depth,
      nodes: List[GraphNode],
      links: List[GraphLink]
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object Graph:
    type Root = Root.T
    object Root extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Depth = Depth.T
    object Depth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  object Kinds:
    type IsbnDoiIsbnEndResult = IsbnDoiIsbnEndResult.T
    object IsbnDoiIsbnEndResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type IsbnDoiIsbnEndI = IsbnDoiIsbnEndI.T
    object IsbnDoiIsbnEndI extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type IsbnDoiIsbnEndDigits = IsbnDoiIsbnEndDigits.T
    object IsbnDoiIsbnEndDigits extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type All = All.T
    object All extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OfResult = OfResult.T
    object OfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OfDoi = OfDoi.T
    object OfDoi extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OfVenue = OfVenue.T
    object OfVenue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OfProvenance = OfProvenance.T
    object OfProvenance extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OfTags = OfTags.T
    object OfTags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OfTopics = OfTopics.T
    object OfTopics extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OfHasCover = OfHasCover.T
    object OfHasCover extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type IsbnDoiResult = IsbnDoiResult.T
    object IsbnDoiResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type IsbnDoiDoi = IsbnDoiDoi.T
    object IsbnDoiDoi extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val Book = "book"
    val Chapter = "chapter"
    val Paper = "paper"
    val Thesis = "thesis"
    val Dataset = "dataset"
    val Unknown = "unknown"
    val all: List[com.histmersion.api.library.Source.Kinds.All] = List(_root_.com.histmersion.api.library.Source.Kinds.All(Book), _root_.com.histmersion.api.library.Source.Kinds.All(Chapter), _root_.com.histmersion.api.library.Source.Kinds.All(Paper), _root_.com.histmersion.api.library.Source.Kinds.All(Thesis), _root_.com.histmersion.api.library.Source.Kinds.All(Dataset), _root_.com.histmersion.api.library.Source.Kinds.All(Unknown))

    /** DOI prefixes of data repositories (Dryad, figshare, Mendeley Data, Harvard Dataverse). Zenodo is absent: it
      * holds papers as often as data.
      */
    private val DataDoiPrefixes = List("10.5061/", "10.6084/", "10.17632/", "10.7910/")

    private val BookProvenance = Set("calibre", Provenance.ExternalFile, "archive", "archive_org")

    def of(
        doi: Option[com.histmersion.api.library.Source.Kinds.OfDoi],
        venue: Option[com.histmersion.api.library.Source.Kinds.OfVenue],
        provenance: Option[com.histmersion.api.library.Source.Kinds.OfProvenance],
        tags: List[com.histmersion.api.library.Source.Kinds.OfTags],
        topics: List[com.histmersion.api.library.Source.Kinds.OfTopics],
        hasCover: com.histmersion.api.library.Source.Kinds.OfHasCover
    ): com.histmersion.api.library.Source.Kinds.OfResult =
      val words = (tags ++ topics).map(_.toLowerCase(java.util.Locale.ROOT))
      val where = venue.getOrElse("").toLowerCase(java.util.Locale.ROOT)
      val id = doi.map(_.trim.toLowerCase(java.util.Locale.ROOT)).filter(_.nonEmpty)
      def says(w: String*) = words.exists(t => w.exists(t.contains)) || w.exists(where.contains)
      if says("thesis", "dissertation") then _root_.com.histmersion.api.library.Source.Kinds.OfResult(Thesis)
      else if words.exists(_.contains("dataset")) || id.exists(d => DataDoiPrefixes.exists(d.startsWith)) then _root_.com.histmersion.api.library.Source.Kinds.OfResult(Dataset)
      else
        id.flatMap(isbnDoi) match
          case Some(chapter)        => if chapter then _root_.com.histmersion.api.library.Source.Kinds.OfResult(Chapter) else _root_.com.histmersion.api.library.Source.Kinds.OfResult(Book)
          case None if id.isDefined => _root_.com.histmersion.api.library.Source.Kinds.OfResult(Paper)
          case None                 =>
            if hasCover || provenance.map(_.trim.toLowerCase(java.util.Locale.ROOT)).exists(BookProvenance.contains)
            then _root_.com.histmersion.api.library.Source.Kinds.OfResult(Book)
            else _root_.com.histmersion.api.library.Source.Kinds.OfResult(Unknown)

    def isbnDoi(doi: com.histmersion.api.library.Source.Kinds.IsbnDoiDoi): Option[com.histmersion.api.library.Source.Kinds.IsbnDoiResult] =
      val suffix = doi.dropWhile(_ != '/').drop(1).toLowerCase(java.util.Locale.ROOT).stripPrefix("cbo")
      if !(suffix.startsWith("978") || suffix.startsWith("979")) then None
      else
        @tailrec def isbnEnd(i: com.histmersion.api.library.Source.Kinds.IsbnDoiIsbnEndI, digits: com.histmersion.api.library.Source.Kinds.IsbnDoiIsbnEndDigits): Option[com.histmersion.api.library.Source.Kinds.IsbnDoiIsbnEndResult] =
          if digits >= 13 then Some(i)
          else if i < suffix.length && suffix(i).isDigit then isbnEnd(i + 1, digits + 1)
          else if i < suffix.length && suffix(i) == '-' then isbnEnd(i + 1, digits)
          else None
        isbnEnd(0, 0).map(end => suffix.drop(end).exists(_.isLetterOrDigit))

  final case class Match(
      rightUid: UUID,
      rightCitekey: com.histmersion.api.library.Citekey,
      leftUid: Option[UUID],
      leftCitekey: Option[com.histmersion.api.library.Citekey],
      method: MatchMethod,
      outcome: MatchOutcome,
      score: Option[Match.Score] = None,
      why: List[Match.Why] = Nil,
      issue: Option[Match.Issue] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema
  object Match:

    /** A source's match in a merge plan: the plan's Markdown lists every one but the new sources. */
    type LineResult = LineResult.T
    object LineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Score = Score.T
    object Score extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Why = Why.T
    object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Issue = Issue.T
    object Issue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val Matches: com.histmersion.api.exchange.PlanMatchKind[Match] =
      com.histmersion.api.exchange.PlanMatchKind(_root_.com.histmersion.api.exchange.PlanMatchKind.ListedResult(_.outcome != MatchOutcome.New), line)

    /** A source's match as the plan's Markdown lists it. */
    def line(s: Match): com.histmersion.api.library.Source.Match.LineResult =
      val target = s.leftCitekey.fold("")(c => s" → `$c`")
      val score = s.score.fold("")(x => s" · score ${math.round(x * 10) / 10.0}")
      _root_.com.histmersion.api.library.Source.Match.LineResult(s"- `${s.rightCitekey}`$target · ${MatchOutcome.wireOf(s.outcome)}$score" + s.issue.fold("")(i => s" · $i"))

  final case class Patch(
      metadata: Option[MetadataPatch] = None,
      summary: Option[com.histmersion.api.library.Source.Summary] = None,
      body: Option[com.histmersion.api.library.Source.OcrMarkdown] = None,
      notes: Option[com.histmersion.api.library.Source.Notes] = None,
      topics: Option[List[com.histmersion.api.library.Source.Topics]] = None,
      regions: Option[List[com.histmersion.api.library.Source.Regions]] = None,
      period: Option[Period.Input] = None,
      tags: Option[ListPatch] = None,
      findings: Option[FindingsPatch] = None,
      edges: Option[EdgesPatch] = None,
      ifUpdatedAt: Option[com.histmersion.api.library.Source.UpdatedAt] = None
  ) derives KindlingsSchema,
        com.histmersion.api.json.Schema
  object Patch:

    given Codec.AsObject[Patch] = StrictJson.codec(KindlingsCodecAsObject.derived[Patch])

  final case class Put(
      title: com.histmersion.api.library.Title,
      authors: List[com.histmersion.api.library.AuthorName] = (Nil).map(com.histmersion.api.library.AuthorName.apply),
      doi: Option[com.histmersion.api.library.Doi] = None,
      year: Option[com.histmersion.api.catalogue.Year] = None,
      venue: Option[com.histmersion.api.library.Venue] = None,
      status: Option[com.histmersion.api.library.Source.Status] = None,
      topics: List[com.histmersion.api.library.Source.Topics] = Nil,
      regions: List[com.histmersion.api.library.Source.Regions] = Nil,
      tags: List[com.histmersion.api.library.Source.Tags] = Nil,
      periodStart: Option[com.histmersion.api.catalogue.Year] = None,
      periodEnd: Option[com.histmersion.api.catalogue.Year] = None,
      summary: Option[com.histmersion.api.library.Source.Summary] = None,
      body: Option[com.histmersion.api.library.Source.OcrMarkdown] = None,
      notes: Option[com.histmersion.api.library.Source.Notes] = None,
      citedByCount: Option[com.histmersion.api.library.Source.CitedByCount] = None,
      influentialCitations: Option[com.histmersion.api.library.Source.InfluentialCitations] = None,
      provenance: Option[com.histmersion.api.library.Source.Provenance] = None,
      calibreId: Option[com.histmersion.api.library.Source.CalibreId] = None,
      findings: List[Finding.Input] = Nil,
      edges: List[Edge.Input] = Nil
  ) derives KindlingsSchema,
        com.histmersion.api.json.Schema
  object Put:

    given Codec.AsObject[Put] = StrictJson.codec(KindlingsCodecAsObject.derived[Put])

    def of(s: Source): Put = Put(
      title = s.title,
      authors = s.authors,
      doi = s.doi,
      year = s.year,
      venue = s.venue,
      status = Some(s.status),
      topics = s.topics,
      regions = s.regions,
      tags = s.tags,
      periodStart = s.period.map(_.start),
      periodEnd = s.period.map(_.end),
      summary = s.summary,
      body = s.ocrMarkdown,
      notes = s.notes,
      citedByCount = s.citedByCount,
      influentialCitations = s.influentialCitations,
      provenance = s.provenance,
      calibreId = s.calibreId,
      findings = s.findings.map(Finding.Input.of),
      edges = s.edges.flatMap(e => com.histmersion.api.library.Edge.Kind.fromWire(e.kind).map(Edge.Input(e.dst.value.toString, _)))
    )

  /** `idLabel` is the DOI when present, else the citekey: the owner's decision to show DOIs in the UI while keeping the
    * internal naming.
    */
  final case class Ref(
      citekey: com.histmersion.api.library.Citekey,
      doi: Option[com.histmersion.api.library.Doi],
      authors: List[com.histmersion.api.library.AuthorName],
      year: Option[com.histmersion.api.catalogue.Year],
      title: com.histmersion.api.library.Title,
      status: com.histmersion.api.library.Source.Status
  ):
    def idLabel: com.histmersion.api.library.Source.Ref.IdLabelResult = _root_.com.histmersion.api.library.Source.Ref.IdLabelResult(doi.map(_.value).getOrElse(citekey.value.toString))

    def shortCite: com.histmersion.api.library.Source.Ref.ShortCiteResult =
      val auth = Ref.shortAuthors(authors.map(_.value))
      val yr = year.map(_.toString).getOrElse("n.d.")
      if auth.isEmpty then _root_.com.histmersion.api.library.Source.Ref.ShortCiteResult(yr) else _root_.com.histmersion.api.library.Source.Ref.ShortCiteResult(s"$auth $yr")

  object Ref:
    type IdLabelResult = IdLabelResult.T
    object IdLabelResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ShortCiteResult = ShortCiteResult.T
    object ShortCiteResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SurnameResult = SurnameResult.T
    object SurnameResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SurnameFull = SurnameFull.T
    object SurnameFull extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ShortAuthorsResult = ShortAuthorsResult.T
    object ShortAuthorsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ShortAuthorsAuthors = ShortAuthorsAuthors.T
    object ShortAuthorsAuthors extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def surname(full: com.histmersion.api.library.Source.Ref.SurnameFull): com.histmersion.api.library.Source.Ref.SurnameResult =
      val t = full.trim
      if t.contains(",") then t.takeWhile(_ != ',').trim
      else t.split("\\s+").lastOption.getOrElse(t)

    def shortAuthors(authors: List[com.histmersion.api.library.Source.Ref.ShortAuthorsAuthors]): com.histmersion.api.library.Source.Ref.ShortAuthorsResult =
      val names = authors.map(_.trim).filter(_.nonEmpty)
      names match
        case Nil           => _root_.com.histmersion.api.library.Source.Ref.ShortAuthorsResult("")
        case a :: Nil      => surname(a)
        case a :: b :: Nil => surname(a) + " & " + surname(b)
        case a :: _        => surname(a) + " et al."

  final case class Renamed(
      oldCitekey: com.histmersion.api.library.Citekey,
      newCitekey: com.histmersion.api.library.Citekey,
      source: Detail,
      counts: Rename.Counts,
      /** Calibre never stores the citekey, but the host-side originals directory is keyed by it. */
      calibreId: Option[com.histmersion.api.library.Source.CalibreId] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object Section:
    type All = All.T
    object All extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ParseResult = ParseResult.T
    object ParseResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ParseS = ParseS.T
    object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val Summary = "summary"
    val Findings = "findings"
    val Notes = "notes"
    val Toc = "toc"
    val Body = "body"
    val All = "all"
    val all: List[com.histmersion.api.library.Source.Section.All] = List(_root_.com.histmersion.api.library.Source.Section.All(Summary), _root_.com.histmersion.api.library.Source.Section.All(Findings), _root_.com.histmersion.api.library.Source.Section.All(Notes), _root_.com.histmersion.api.library.Source.Section.All(Toc), _root_.com.histmersion.api.library.Source.Section.All(Body), All)

    def parse(s: com.histmersion.api.library.Source.Section.ParseS): Option[com.histmersion.api.library.Source.Section.ParseResult] =
      val lower = s.trim.toLowerCase
      all.find(_ == lower)

  final case class WithFindings(
      source: Detail,
      findings: List[Finding.Row],
      formatting: List[FormatIssue] = Nil,
      illustration: Option[MediaThumb] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  /** A source's put and patch checked and applied to the stored record: the findings numbered, the edges and the
    * metadata checked, an old citekey in a patch followed to the current one.
    */
  object Write:

    type AllocateLoopResultPart1 = AllocateLoopResultPart1.T
    object AllocateLoopResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AllocateLoopUsed = AllocateLoopUsed.T
    object AllocateLoopUsed extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AllocateLoopAccPart1 = AllocateLoopAccPart1.T
    object AllocateLoopAccPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PeriodOfSaneV = PeriodOfSaneV.T
    object PeriodOfSaneV extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type FindingIdResult = FindingIdResult.T
    object FindingIdResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FindingIdN = FindingIdN.T
    object FindingIdN extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ApplyPutResultPart1 = ApplyPutResultPart1.T
    object ApplyPutResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PreconditionResultPart1 = PreconditionResultPart1.T
    object PreconditionResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ApplyPatchResultPart1 = ApplyPatchResultPart1.T
    object ApplyPatchResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SrcAliasedRequested = SrcAliasedRequested.T
    object SrcAliasedRequested extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SrcAliasedCanonical = SrcAliasedCanonical.T
    object SrcAliasedCanonical extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ApplyEdgesResultPart1 = ApplyEdgesResultPart1.T
    object ApplyEdgesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type EdgeOfResultPart1 = EdgeOfResultPart1.T
    object EdgeOfResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type EdgeOfCitekey = EdgeOfCitekey.T
    object EdgeOfCitekey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type EdgeOfAliases = EdgeOfAliases.T
    object EdgeOfAliases extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type EdgeOfPart = EdgeOfPart.T
    object EdgeOfPart extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type EdgeOfRole = EdgeOfRole.T
    object EdgeOfRole extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MetadataCheckResultPart1 = MetadataCheckResultPart1.T
    object MetadataCheckResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ApplyFindingsResultPart1 = ApplyFindingsResultPart1.T
    object ApplyFindingsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NumberedResultPart1 = NumberedResultPart1.T
    object NumberedResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AllocateResultPart1 = AllocateResultPart1.T
    object AllocateResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ToFindingResultPart1 = ToFindingResultPart1.T
    object ToFindingResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ToFindingId = ToFindingId.T
    object ToFindingId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ConfidenceOfResultPart1 = ConfidenceOfResultPart1.T
    object ConfidenceOfResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ConfidenceOfRaw = ConfidenceOfRaw.T
    object ConfidenceOfRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ConfidenceOfWhat = ConfidenceOfWhat.T
    object ConfidenceOfWhat extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PeriodOfResultPart1 = PeriodOfResultPart1.T
    object PeriodOfResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PeriodOfStart = PeriodOfStart.T
    object PeriodOfStart extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PeriodOfEnd = PeriodOfEnd.T
    object PeriodOfEnd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PeriodOfWhat = PeriodOfWhat.T
    object PeriodOfWhat extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OutOfRangeResult = OutOfRangeResult.T
    object OutOfRangeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OutOfRangeWhat = OutOfRangeWhat.T
    object OutOfRangeWhat extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OutOfRangeField = OutOfRangeField.T
    object OutOfRangeField extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OutOfRangeV = OutOfRangeV.T
    object OutOfRangeV extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SupersededEdgeCheckResultPart1 = SupersededEdgeCheckResultPart1.T
    object SupersededEdgeCheckResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TraverseResultPart1 = TraverseResultPart1.T
    object TraverseResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TraverseFResultPart1 = TraverseFResultPart1.T
    object TraverseFResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def findingId(citekey: com.histmersion.api.library.Citekey, n: com.histmersion.api.library.Source.Write.FindingIdN): com.histmersion.api.library.Source.Write.FindingIdResult = _root_.com.histmersion.api.library.Source.Write.FindingIdResult(s"$citekey#$n")

    def applyPut(citekey: com.histmersion.api.library.Citekey, p: Put, aliases: Set[com.histmersion.api.library.Citekey] = (Set.empty).map(com.histmersion.api.library.Citekey.apply)): Either[com.histmersion.api.library.Source.Write.ApplyPutResultPart1, Source] =
      for
        period <- periodOf(p.periodStart, p.periodEnd, _root_.com.histmersion.api.library.Source.Write.PeriodOfWhat("source"))
        fs <- numbered(citekey, p.findings)
        edges <- traverse(p.edges)(edgeOf(citekey.value.toString, aliases.map(_.value.toString), "edges", "source being put", _))
        _ <- edges
          .find(e => e.dst == citekey || aliases.contains(e.dst))
          .fold(Right(()))(e =>
            Left(s"edges: '${e.dst}' --${e.kind}--> itself is a self-edge; dst must be another source")
          )
      yield Source(
        citekey = citekey,
        title = p.title,
        authors = p.authors,
        doi = (blankToNone(p.doi.map(_.value))).map(com.histmersion.api.library.Doi.apply),
        year = p.year,
        venue = (blankToNone(p.venue.map(_.value))).map(com.histmersion.api.library.Venue.apply),
        status = p.status.getOrElse(com.histmersion.api.library.Source.Status.Unassessed),
        topics = p.topics,
        regions = p.regions,
        tags = p.tags,
        period = period,
        summary = p.summary,
        citedByCount = p.citedByCount,
        influentialCitations = p.influentialCitations,
        provenance = p.provenance,
        calibreId = p.calibreId,
        ocrMarkdown = p.body,
        findings = fs,
        edges = edges.distinct,
        notes = p.notes,
        updatedAt = None
      )

    def precondition(existing: Source, patch: Patch): Either[com.histmersion.api.library.Source.Write.PreconditionResultPart1, Unit] =
      patch.ifUpdatedAt match
        case None       => Right(())
        case Some(want) =>
          val actual = existing.updatedAt.getOrElse("")
          if want == actual then Right(())
          else
            Left(
              _root_.com.histmersion.api.library.Source.Write.PreconditionResultPart1(s"'${existing.citekey}' has moved on: you sent ifUpdatedAt='$want' but the stored " +
                s"updatedAt is '$actual'. Re-read the source and re-apply your patch.")
            )

    def applyPatch(existing: Source, patch: Patch): Either[com.histmersion.api.library.Source.Write.ApplyPatchResultPart1, Source] =
      for
        _ <- precondition(existing, patch)
        period <- patch.period.fold[Either[String, Option[Period]]](Right(existing.period))(p =>
          periodOf(Some(p.start), Some(p.end), _root_.com.histmersion.api.library.Source.Write.PeriodOfWhat("source"))
        )
        fs <- patch.findings.fold[Either[String, List[Finding]]](Right(existing.findings))(fp =>
          applyFindings(existing.citekey, existing.findings, fp)
        )
        meta = patch.metadata.getOrElse(MetadataPatch())
        _ <- metadataCheck(meta)
        newEdges <- patch.edges.fold[Either[String, List[Edge]]](Right(existing.edges))(
          applyEdges(existing.citekey, existing.edges, _)
        )
        newStatus = meta.status.getOrElse(existing.status)
        _ <- supersededEdgeCheck(newStatus, newEdges)
      yield
        def cleared(f: MetadataField) = meta.clear.contains(f)
        existing.modify(
          _.title).setTo(meta.title.getOrElse(existing.title)).modify(_.authors).setTo(meta.authors.getOrElse(existing.authors)).modify(_.year).setTo(if cleared(MetadataField.Year) then None else meta.year.orElse(existing.year)).modify(_.venue).setTo(if cleared(MetadataField.Venue) then None
          else meta.venue.fold(existing.venue)(v => blankToNone(Some(v.value)).map(com.histmersion.api.library.Venue.apply))).modify(_.doi).setTo(if cleared(MetadataField.Doi) then None
          else meta.doi.fold(existing.doi)(d => blankToNone(Some(d.value)).map(com.histmersion.api.library.Doi.apply))).modify(_.status).setTo(newStatus).modify(_.summary).setTo(patch.summary.orElse(existing.summary)).modify(_.ocrMarkdown).setTo(patch.body.orElse(existing.ocrMarkdown)).modify(_.notes).setTo(patch.notes.orElse(existing.notes)).modify(_.topics).setTo(patch.topics.getOrElse(existing.topics)).modify(_.regions).setTo(patch.regions.getOrElse(existing.regions)).modify(_.tags).setTo(patch.tags.fold(existing.tags)(applyList(existing.tags, _))).modify(_.period).setTo(period).modify(_.findings).setTo(fs).modify(_.edges).setTo(newEdges).modify(_.provenance).setTo(if cleared(MetadataField.Provenance) then None
          else meta.provenance.orElse(existing.provenance)).modify(_.calibreId).setTo(if cleared(MetadataField.CalibreId) then None
          else meta.calibreId.map(_.trim).orElse(existing.calibreId)
        )

    def srcAliased(p: Patch, requested: com.histmersion.api.library.Source.Write.SrcAliasedRequested, canonical: com.histmersion.api.library.Source.Write.SrcAliasedCanonical): Patch =
      if requested == canonical then p
      else
        def fix(e: Edge.Input) = if e.src.map(_.trim).contains(requested) then e.modify(_.src).setTo(None) else e
        p.modify(_.edges).setTo(p.edges.map(ep => EdgesPatch(ep.add.map(fix), ep.remove.map(fix))))

    def applyEdges(citekey: com.histmersion.api.library.Citekey, current: List[Edge], p: EdgesPatch): Either[com.histmersion.api.library.Source.Write.ApplyEdgesResultPart1, List[Edge]] =
      for
        removed <- traverse(p.remove)(edgeOf(_root_.com.histmersion.api.library.Source.Write.EdgeOfCitekey(citekey.value.toString), Set.empty, _root_.com.histmersion.api.library.Source.Write.EdgeOfPart("edges.remove"), _root_.com.histmersion.api.library.Source.Write.EdgeOfRole("patched source"), _))
        added <- traverse(p.add)(edgeOf(_root_.com.histmersion.api.library.Source.Write.EdgeOfCitekey(citekey.value.toString), Set.empty, _root_.com.histmersion.api.library.Source.Write.EdgeOfPart("edges.add"), _root_.com.histmersion.api.library.Source.Write.EdgeOfRole("patched source"), _))
        _ <- added
          .find(_.dst == citekey)
          .fold(Right(()))(e =>
            Left(s"edges.add: '${e.dst}' --${e.kind}--> itself is a self-edge; dst must be another source")
          )
      yield
        val gone = removed.toSet
        (current.map(_.modify(_.src).setTo(citekey)).filterNot(gone) ++ added.filterNot(gone)).distinct

    private[api] def edgeOf(
        citekey: com.histmersion.api.library.Source.Write.EdgeOfCitekey,
        aliases: Set[com.histmersion.api.library.Source.Write.EdgeOfAliases],
        part: com.histmersion.api.library.Source.Write.EdgeOfPart,
        role: com.histmersion.api.library.Source.Write.EdgeOfRole,
        in: Edge.Input
    ): Either[com.histmersion.api.library.Source.Write.EdgeOfResultPart1, Edge] =
      in.src.map(_.trim).filter(_.nonEmpty) match
        case Some(other) if other != citekey && !aliases.contains(other) =>
          Left(
            _root_.com.histmersion.api.library.Source.Write.EdgeOfResultPart1(s"$part: src '$other' is not the $role '$citekey'. An edge is stored on its " +
              s"source: patch '$other' to record '$other' --${com.histmersion.api.library.Edge.Kind.wireOf(in.kind)}--> '${in.dst}' " +
              s"(src may be omitted; it defaults to the $role)")
          )
        case _ => Right(Edge(com.histmersion.api.library.Citekey(citekey), com.histmersion.api.library.Citekey(in.dst.trim), com.histmersion.api.library.Edge.Kind.wireOf(in.kind)))

    private[api] def metadataCheck(m: MetadataPatch): Either[com.histmersion.api.library.Source.Write.MetadataCheckResultPart1, Unit] =
      val both = m.clear.filter {
        case MetadataField.Year       => m.year.isDefined
        case MetadataField.Venue      => m.venue.isDefined
        case MetadataField.Doi        => m.doi.isDefined
        case MetadataField.Provenance => m.provenance.isDefined
        case MetadataField.CalibreId  => m.calibreId.isDefined
      }
      if both.nonEmpty then
        Left(_root_.com.histmersion.api.library.Source.Write.MetadataCheckResultPart1(s"metadata: ${both.map(MetadataField.wireOf).mkString(", ")} is both set and cleared in one patch"))
      else
        m.calibreId.map(_.trim) match
          case Some(id) if !id.matches("[0-9]+") =>
            Left(_root_.com.histmersion.api.library.Source.Write.MetadataCheckResultPart1(s"metadata.calibreId: '$id' is not a Calibre id (the entry's number, e.g. 1805)"))
          case _ => Right(())

    def applyFindings(citekey: com.histmersion.api.library.Citekey, current: List[Finding], p: FindingsPatch): Either[com.histmersion.api.library.Source.Write.ApplyFindingsResultPart1, List[Finding]] =
      for
        base <- p.replace.fold[Either[String, List[Finding]]](Right(current))(numbered(citekey, _))
        kept = base.filterNot(f => p.remove.contains(f.id))
        added <- allocate(citekey, kept, p.add)
      yield kept ++ added

    private[api] def numbered(citekey: com.histmersion.api.library.Citekey, inputs: List[Finding.Input]): Either[com.histmersion.api.library.Source.Write.NumberedResultPart1, List[Finding]] =
      traverse(inputs.zipWithIndex)((in, i) => toFinding(citekey, findingId(citekey, _root_.com.histmersion.api.library.Source.Write.FindingIdN(i)), in))

    private[api] def allocate(
        citekey: com.histmersion.api.library.Citekey,
        existing: List[Finding],
        adds: List[Finding.Input]
    ): Either[com.histmersion.api.library.Source.Write.AllocateResultPart1, List[Finding]] =
      @tailrec def loop(
          rest: List[Finding.Input],
          used: Set[com.histmersion.api.library.Source.Write.AllocateLoopUsed],
          acc: List[Either[com.histmersion.api.library.Source.Write.AllocateLoopAccPart1, Finding]]
      ): List[Either[com.histmersion.api.library.Source.Write.AllocateLoopResultPart1, Finding]] =
        rest match
          case Nil        => acc.reverse
          case in :: tail =>
            val id = Iterator.from(0).map(findingId(citekey, _)).find(n => !used.contains(n)).get
            loop(tail, used + id, toFinding(citekey, id, in) :: acc)
      traverse(loop(adds, existing.map(_.id).toSet, Nil))(Predef.identity)

    private[api] def toFinding(citekey: com.histmersion.api.library.Citekey, id: com.histmersion.api.library.Source.Write.ToFindingId, in: Finding.Input): Either[com.histmersion.api.library.Source.Write.ToFindingResultPart1, Finding] =
      val what = s"finding '${in.claim.take(40)}'"
      for
        p <- periodOf(in.periodStart, in.periodEnd, what)
        conf <- confidenceOf(in.confidence, what)
      yield
        val geo = (in.lon, in.lat) match
          case (Some(x), Some(y)) => Some(GeoPoint(x, y))
          case _                  => None
        Finding(id, citekey, in.claim, in.region, geo, p, in.periodName, in.tags, conf, in.significance)

    private[api] def confidenceOf(raw: Option[com.histmersion.api.library.Source.Write.ConfidenceOfRaw], what: com.histmersion.api.library.Source.Write.ConfidenceOfWhat): Either[com.histmersion.api.library.Source.Write.ConfidenceOfResultPart1, Option[Confidence]] =
      raw.map(_.trim).filter(_.nonEmpty) match
        case None    => Right(None)
        case Some(c) =>
          Confidence
            .fromString(c)
            .map(Some(_))
            .toRight(_root_.com.histmersion.api.library.Source.Write.ConfidenceOfResultPart1(s"$what: confidence '$c' is not one of ${Confidence.values.map(_.wire).mkString("|")}"))

    /** Postgres rejects an inverted int8range. */
    private[api] def periodOf(start: Option[com.histmersion.api.library.Source.Write.PeriodOfStart], end: Option[com.histmersion.api.library.Source.Write.PeriodOfEnd], what: com.histmersion.api.library.Source.Write.PeriodOfWhat): Either[com.histmersion.api.library.Source.Write.PeriodOfResultPart1, Option[Period]] =
      def sane(v: com.histmersion.api.library.Source.Write.PeriodOfSaneV) = v >= Filter.Earliest && v <= Filter.Latest
      (start, end) match
        case (Some(a), _) if !sane(a)     => Left(outOfRange(what, _root_.com.histmersion.api.library.Source.Write.OutOfRangeField("period_start"), a))
        case (_, Some(b)) if !sane(b)     => Left(outOfRange(what, _root_.com.histmersion.api.library.Source.Write.OutOfRangeField("period_end"), b))
        case (Some(a), Some(b)) if a <= b => Right(Some(Period(a, b)))
        case (Some(a), Some(b))           => Left(_root_.com.histmersion.api.library.Source.Write.PeriodOfResultPart1(s"$what: period_start ($a) must not be after period_end ($b)"))
        case (None, None)                 => Right(None)
        case _                            => Left(_root_.com.histmersion.api.library.Source.Write.PeriodOfResultPart1(s"$what: give both period_start and period_end, or neither"))

    private[api] def outOfRange(what: com.histmersion.api.library.Source.Write.OutOfRangeWhat, field: com.histmersion.api.library.Source.Write.OutOfRangeField, v: com.histmersion.api.library.Source.Write.OutOfRangeV): com.histmersion.api.library.Source.Write.OutOfRangeResult =
      _root_.com.histmersion.api.library.Source.Write.OutOfRangeResult(s"$what: $field ($v) is outside the representable range " +
        s"[${Filter.Earliest}, ${Filter.Latest}] — deep time is signed YEARS, BCE negative")

    private[api] def supersededEdgeCheck(
        status: com.histmersion.api.library.Source.Status,
        edges: List[Edge]
    ): Either[com.histmersion.api.library.Source.Write.SupersededEdgeCheckResultPart1, Unit] =
      if status == com.histmersion.api.library.Source.Status.Superseded && !edges.exists(_.kind == "superseded_by")
      then
        Left(
          _root_.com.histmersion.api.library.Source.Write.SupersededEdgeCheckResultPart1("status=superseded requires a superseded_by edge — add one with edges.add " +
            "(e.g. {\"edges\":{\"add\":[{\"dst\":\"newer_source\",\"kind\":\"superseded_by\"}]}}, " +
            "or the CLI flag --add-edge superseded_by:newer_source)")
        )
      else Right(())

    private[api] def traverse[A, B](as: List[A])(f: A => Either[com.histmersion.api.library.Source.Write.TraverseFResultPart1, B]): Either[com.histmersion.api.library.Source.Write.TraverseResultPart1, List[B]] =
      as.foldLeft[Either[String, List[B]]](Right(Nil))((acc, a) => acc.flatMap(bs => f(a).map(bs :+ _)))
