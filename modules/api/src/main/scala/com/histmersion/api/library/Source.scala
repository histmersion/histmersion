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
    venue: Option[com.histmersion.api.library.Source.Venue],
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
  object Neighbor:
    type Line = Line.T
    object Line extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AuthorName = AuthorName.T
  object AuthorName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AuthorSurname = AuthorSurname.T
  object AuthorSurname extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Body = Body.T
  object Body extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EdgePart = EdgePart.T
  object EdgePart extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EdgeRole = EdgeRole.T
  object EdgeRole extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Export = Export.T
  object Export extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Venue = Venue.T
  object Venue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type View = View.T
  object View extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

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
  type Notes = Notes.T
  object Notes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  enum Status:
    case Foundational, Current, Contested, Superseded, Debunked, Retracted, Unassessed

    /** The wire / storage spelling (`current`), as the JSON codec writes it. */
    def wire: com.histmersion.api.library.Source.Status.Wire = Ascii.lower((toString))

  object Status:
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


    def fromString(s: com.histmersion.api.library.Source.Status.Wire): Status =
      values.find(_.toString.equalsIgnoreCase((s).value)).getOrElse(Unassessed)

    given WireEnumValues[Status]:
      def all: Array[Status] = Status.values
      def wire(e: Status): _root_.com.histmersion.api.WireEnumValues.Wire = _root_.com.histmersion.api.WireEnumValues.Wire((_root_.com.histmersion.api.library.Source.Status.Wire(e.toString.toLowerCase)).value)
      def fromWire(s: _root_.com.histmersion.api.WireEnumValues.Wire): _root_.scala.Option[Status] =
        Status.values.find(_.toString.equalsIgnoreCase((s).value))

  final case class Alias(alias: Alias.Value, citekey: com.histmersion.api.library.Citekey)
      derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema


  final case class Candidate(
      id: Candidate.Id,
      goal: Candidate.Goal,
      doi: com.histmersion.api.library.Doi,
      title: com.histmersion.api.library.Title,
      year: Option[com.histmersion.api.catalogue.Year] = None,
      authors: List[com.histmersion.api.library.AuthorName] = (Nil).map(com.histmersion.api.library.AuthorName.apply),
      venue: Option[com.histmersion.api.library.Source.Venue] = None,
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
    type CalibreLine = CalibreLine.T
    object CalibreLine extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LogText = LogText.T
    object LogText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Markdown = Markdown.T
    object Markdown extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Problem = Problem.T
    object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type CalibreLineCalibreId = _root_.com.histmersion.api.library.Source.CalibreId
    val CalibreLineCalibreId: _root_.com.histmersion.api.library.Source.CalibreId.type = _root_.com.histmersion.api.library.Source.CalibreId

    def calibreLine(calibreId: Option[com.histmersion.api.library.Source.Delete.CalibreLineCalibreId], check: Option[CalibreEntryCheck]): com.histmersion.api.library.Source.Delete.CalibreLine =
      calibreId match
        case None     => _root_.com.histmersion.api.library.Source.Delete.CalibreLine("Calibre: no linked entry.")
        case Some(id) =>
          check match
            case Some(CalibreEntryCheck.Present) =>
              _root_.com.histmersion.api.library.Source.Delete.CalibreLine(s"Calibre: entry $id is linked and is NOT touched — remove it in Calibre if it should go too.")
            case Some(CalibreEntryCheck.Missing) =>
              _root_.com.histmersion.api.library.Source.Delete.CalibreLine(s"Calibre: entry $id no longer exists in the library — nothing to remove there.")
            case Some(CalibreEntryCheck.Unchecked(why)) =>
              _root_.com.histmersion.api.library.Source.Delete.CalibreLine(s"Calibre: the record links entry $id (the library could not be checked: $why); it is NOT touched — " +
                "remove it in Calibre if it is there and should go too.")
            case None =>
              _root_.com.histmersion.api.library.Source.Delete.CalibreLine(s"Calibre: the record links entry $id (not checked against the library); it is NOT touched — " +
                "remove it in Calibre if it is there and should go too.")

    def refusal(ck: com.histmersion.api.library.Citekey, blockers: List[DeleteBlocker]): com.histmersion.api.library.Source.Delete.Problem =
      val groups = blockers.groupBy(_.kind).toList.sortBy(_._1.ordinal).map { (k, bs) =>
        s"${k.label} ${bs.map(b => b.id + b.title.fold("")(t => s" ($t)")).mkString(", ")}"
      }
      _root_.com.histmersion.api.library.Source.Delete.Problem(s"'$ck' cannot be deleted — ${groups.mkString("; ")}. Fix those first (remove the citation from " +
        "each article, re-source the events, let the job finish); there is no override")

    def logText(d: Deletion): com.histmersion.api.library.Source.Delete.LogText =
      def short(p: DeletePart) = p match
        case DeletePart.EdgesIn => "edges in (parked)"
        case other              => other.wire.replace('-', ' ')
      val parts = d.counts.filter((_.rows).value > 0).map(c => s"${c.rows} ${short(c.part)}")
      val files = if d.files.isEmpty then Nil else List(Plural.count(_root_.com.histmersion.api.Plural.CountN((d.files.size).toLong), _root_.com.histmersion.api.Plural.CountSingular("stored original")))
      val calibre = d.calibreId.map(id => s"Calibre entry $id kept").toList
      _root_.com.histmersion.api.library.Source.Delete.LogText(s"deleted ${d.citekey} (${d.title})" +
        (parts ++ files ++ calibre match
          case Nil => ""
          case xs  => ": " + xs.mkString(", ")))

    def markdown(d: Deletion, calibre: Option[CalibreEntryCheck] = None): com.histmersion.api.library.Source.Delete.Markdown =
      val head =
        if ((d.applied) == _root_.com.histmersion.api.library.Source.Deletion.Applied(true)) then s"# Deleted `${d.citekey}`"
        else if ((d.refused) == (true)) then s"# Cannot delete `${d.citekey}`"
        else s"# Would delete `${d.citekey}` (dry run)"
      val blockers =
        if d.blockers.isEmpty then Nil
        else
          List("", "**Refused — fix these first (there is no override):**", "") ++
            d.blockers.map(b => s"- ${b.kind.label}: `${b.id}`${b.title.fold("")(t => s" — $t")}")
      val verb = if ((d.applied) == _root_.com.histmersion.api.library.Source.Deletion.Applied(true)) then "removed" else "would go"
      val counts =
        if d.counts.isEmpty then List("", s"Nothing besides the record itself $verb.")
        else
          List("", s"**${if ((d.applied) == _root_.com.histmersion.api.library.Source.Deletion.Applied(true)) then "Removed" else "Would remove"}** (with the record itself):", "") ++
            d.counts.map(c => s"- ${c.part.label}: ${c.rows}")
      val aliases = if d.aliases.isEmpty then Nil
      else List("", s"Old citekeys that stop resolving: ${d.aliases.map(a => s"`$a`").mkString(", ")}")
      val edgesIn = if d.edgesIn.isEmpty then Nil
      else
        List(
          "",
          s"Edges of other sources to it (${if ((d.applied) == _root_.com.histmersion.api.library.Source.Deletion.Applied(true)) then "parked" else "parked, not lost"}: " +
            "they come back if it is ingested again):",
          ""
        ) ++
          d.edgesIn.map(e => s"- `${e.src}` ${e.kind} → `${e.dst}`")
      val coverage = if d.coverage.isEmpty then Nil
      else List("", s"Coverage reasons dropped from: ${d.coverage.map(s => s"`$s`").mkString(", ")}")
      val files =
        if d.files.isEmpty then List("", "Stored original: none on the server.")
        else
          List("", s"Stored original (${if ((d.applied) == _root_.com.histmersion.api.library.Source.Deletion.Applied(true)) then "removed" else "removed after the commit"}):", "") ++
            d.files.map(f => s"- `$f`")
      val failed = if d.filesFailed.isEmpty then Nil
      else List("", "**Could not remove** (logged; remove by hand):", "") ++ d.filesFailed.map(f => s"- $f")
      val mentions = if d.mentions.isEmpty then Nil
      else List("", s"Queue items that mention it (left as they are): ${d.mentions.map(i => s"#$i").mkString(", ")}")
      val calibreLines = List("", calibreLine(d.calibreId, calibre))
      val tail =
        if ((d.applied) == _root_.com.histmersion.api.library.Source.Deletion.Applied(true)) then
          List(
            "",
            s"Logged as item #${d.logEntry.fold("?")(_.toString)} of the log queue. A tombstone keeps a " +
              s"bundle merge from bringing it back. The exported mirror's `sources/${d.citekey}.md` is not removed by " +
              "`histmersion export` — delete it there by hand."
          )
        else if ((d.refused) == (true)) then Nil
        else List("", s"_Dry run. Re-run with `--apply` to delete._")
      _root_.com.histmersion.api.library.Source.Delete.Markdown((List(head, "", s"**${d.title}**") ++ blockers ++ counts ++ aliases ++ edgesIn ++ coverage ++ files ++ failed ++
        mentions ++ calibreLines ++ tail).mkString("\n"))

  final case class DeleteRequest(apply: DeleteRequest.Apply = _root_.com.histmersion.api.Api.Apply(false)) derives KindlingsSchema, com.histmersion.api.json.Schema
  object DeleteRequest:


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
    def refused: Boolean = (blockers.nonEmpty)
    def count(p: DeletePart): com.histmersion.api.RowCount = counts.find(_.part == p).fold(0)(_.rows)

  object Deletion:

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
      venue: Option[com.histmersion.api.library.Source.Venue],
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


  final case class Graph(
      root: Graph.Root,
      depth: Graph.Depth,
      nodes: List[GraphNode],
      links: List[GraphLink]
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object Graph:
    type DepthCount = DepthCount.T
    object DepthCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  object Kinds:
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


    val Book = "book"
    val Chapter = "chapter"
    val Paper = "paper"
    val Thesis = "thesis"
    val Dataset = "dataset"
    val Unknown = "unknown"
    val all: List[com.histmersion.api.library.Source.Kinds.Wire] = List(_root_.com.histmersion.api.library.Source.Kinds.Wire(Book), _root_.com.histmersion.api.library.Source.Kinds.Wire(Chapter), _root_.com.histmersion.api.library.Source.Kinds.Wire(Paper), _root_.com.histmersion.api.library.Source.Kinds.Wire(Thesis), _root_.com.histmersion.api.library.Source.Kinds.Wire(Dataset), _root_.com.histmersion.api.library.Source.Kinds.Wire(Unknown))

    /** DOI prefixes of data repositories (Dryad, figshare, Mendeley Data, Harvard Dataverse). Zenodo is absent: it
      * holds papers as often as data.
      */
    private val DataDoiPrefixes = List("10.5061/", "10.6084/", "10.17632/", "10.7910/")

    private val BookProvenance = Set("calibre", Provenance.ExternalFile, "archive", "archive_org")

    def of(
        doi: Option[com.histmersion.api.library.Doi],
        venue: Option[com.histmersion.api.library.Source.Venue],
        provenance: Option[com.histmersion.api.library.Source.Provenance],
        tags: List[com.histmersion.api.library.Source.Tags],
        topics: List[com.histmersion.api.library.Source.Topics],
        hasCover: Boolean
    ): com.histmersion.api.library.Source.Kinds.Wire =
      val words = (tags ++ topics).map(_.toLowerCase(java.util.Locale.ROOT))
      val where = venue.getOrElse("").toLowerCase(java.util.Locale.ROOT)
      val id = doi.map((_).value.trim.toLowerCase(java.util.Locale.ROOT)).filter(_.nonEmpty)
      def says(w: String*) = words.exists(t => w.exists(t.contains)) || w.exists(where.contains)
      if says("thesis", "dissertation") then _root_.com.histmersion.api.library.Source.Kinds.Wire(Thesis)
      else if words.exists(_.contains("dataset")) || id.exists(d => DataDoiPrefixes.exists(d.startsWith)) then _root_.com.histmersion.api.library.Source.Kinds.Wire(Dataset)
      else
        id.flatMap({ val refactorFunction2 = isbnDoi; (refactorValue14) => refactorFunction2(_root_.com.histmersion.api.library.Doi(refactorValue14)) }) match
          case Some(chapter)        => if (chapter).value then _root_.com.histmersion.api.library.Source.Kinds.Wire(Chapter) else _root_.com.histmersion.api.library.Source.Kinds.Wire(Book)
          case None if id.isDefined => _root_.com.histmersion.api.library.Source.Kinds.Wire(Paper)
          case None                 =>
            if ((hasCover) == (true)) || provenance.map((_).value.trim.toLowerCase(java.util.Locale.ROOT)).exists(BookProvenance.contains)
            then _root_.com.histmersion.api.library.Source.Kinds.Wire(Book)
            else _root_.com.histmersion.api.library.Source.Kinds.Wire(Unknown)

    def isbnDoi(doi: com.histmersion.api.library.Doi): Option[Boolean] =
      val suffix = (doi).value.dropWhile(_ != '/').drop(1).toLowerCase(java.util.Locale.ROOT).stripPrefix("cbo")
      if !(suffix.startsWith("978") || suffix.startsWith("979")) then None
      else
        @tailrec def isbnEnd(i: Int, digits: Int): Option[Int] =
          if digits >= 13 then Some(i)
          else if (i).value < suffix.length && suffix((i).value).isDigit then isbnEnd(((i).value + 1), ((digits).value + 1))
          else if (i).value < suffix.length && suffix((i).value) == '-' then isbnEnd(((i).value + 1), digits)
          else None
        isbnEnd((0), (0)).map(end => (suffix.drop((end).value).exists(_.isLetterOrDigit)))

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
    type Line = Line.T
    object Line extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    /** A source's match in a merge plan: the plan's Markdown lists every one but the new sources. */

    type Score = Score.T
    object Score extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Why = Why.T
    object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Issue = Issue.T
    object Issue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val Matches: com.histmersion.api.exchange.PlanMatchKind[Match] =
      com.histmersion.api.exchange.PlanMatchKind(_root_.com.histmersion.api.exchange.PlanMatchKind.ListedResult(_.outcome != MatchOutcome.New), line)

    /** A source's match as the plan's Markdown lists it. */
    def line(s: Match): com.histmersion.api.library.Source.Match.Line =
      val target = s.leftCitekey.fold("")(c => s" → `$c`")
      val score = s.score.fold("")(x => s" · score ${math.round((x).value * 10) / 10.0}")
      _root_.com.histmersion.api.library.Source.Match.Line(s"- `${s.rightCitekey}`$target · ${MatchOutcome.wireOf(s.outcome)}$score" + s.issue.fold("")(i => s" · $i"))

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
      venue: Option[com.histmersion.api.library.Source.Venue] = None,
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
    def idLabel: com.histmersion.api.library.Source.Ref.Label = _root_.com.histmersion.api.library.Source.Ref.Label(doi.map(_.value).getOrElse(citekey.value.toString))

    def shortCite: com.histmersion.api.library.Source.Ref.ShortCite =
      val auth = Ref.shortAuthors(authors.map(_.value))
      val yr = year.map(_.toString).getOrElse("n.d.")
      if (auth).value.isEmpty then _root_.com.histmersion.api.library.Source.Ref.ShortCite(yr) else _root_.com.histmersion.api.library.Source.Ref.ShortCite(s"$auth $yr")

  object Ref:
    type Label = Label.T
    object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ShortAuthors = ShortAuthors.T
    object ShortAuthors extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ShortCite = ShortCite.T
    object ShortCite extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def surname(full: com.histmersion.api.library.Source.AuthorName): com.histmersion.api.library.Source.AuthorSurname =
      val t = (full).value.trim
      if t.contains(",") then _root_.com.histmersion.api.library.Source.AuthorSurname(t.takeWhile(_ != ',').trim)
      else _root_.com.histmersion.api.library.Source.AuthorSurname(t.split("\\s+").lastOption.getOrElse(t))

    def shortAuthors(authors: List[com.histmersion.api.library.Source.AuthorName]): com.histmersion.api.library.Source.Ref.ShortAuthors =
      val names = authors.map((_).value.trim).filter(_.nonEmpty)
      names match
        case Nil           => _root_.com.histmersion.api.library.Source.Ref.ShortAuthors("")
        case a :: Nil      => _root_.com.histmersion.api.library.Source.Ref.ShortAuthors((surname(_root_.com.histmersion.api.library.Source.AuthorName(a))).value)
        case a :: b :: Nil => _root_.com.histmersion.api.library.Source.Ref.ShortAuthors((surname(_root_.com.histmersion.api.library.Source.AuthorName(a))).value + " & " + surname(_root_.com.histmersion.api.library.Source.AuthorName(b)))
        case a :: _        => _root_.com.histmersion.api.library.Source.Ref.ShortAuthors((surname(_root_.com.histmersion.api.library.Source.AuthorName(a))).value + " et al.")

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
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val Summary = "summary"
    val Findings = "findings"
    val Notes = "notes"
    val Toc = "toc"
    val Body = "body"
    val All = "all"
    val all: List[com.histmersion.api.library.Source.Section.Wire] = List(_root_.com.histmersion.api.library.Source.Section.Wire(Summary), _root_.com.histmersion.api.library.Source.Section.Wire(Findings), _root_.com.histmersion.api.library.Source.Section.Wire(Notes), _root_.com.histmersion.api.library.Source.Section.Wire(Toc), _root_.com.histmersion.api.library.Source.Section.Wire(Body), All)

    def parse(s: com.histmersion.api.library.Source.Section.Wire): Option[com.histmersion.api.library.Source.Section.Wire] =
      val lower = (s).value.trim.toLowerCase
      all.find(_ == _root_.com.histmersion.api.library.Source.Section.Wire(lower))

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
    type Problem = Problem.T
    object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


    type EdgeOfAliases = EdgeOfAliases.T
    object EdgeOfAliases extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ToFindingId = _root_.com.histmersion.api.library.Finding.Id
    val ToFindingId: _root_.com.histmersion.api.library.Finding.Id.type = _root_.com.histmersion.api.library.Finding.Id
    type PeriodOfWhat = String

    def findingId(citekey: com.histmersion.api.library.Citekey, n: com.histmersion.api.library.Finding.Ordinal): com.histmersion.api.library.Finding.Id = _root_.com.histmersion.api.library.Finding.Id(s"$citekey#$n")

    def applyPut(citekey: com.histmersion.api.library.Citekey, p: Put, aliases: Set[com.histmersion.api.library.Citekey] = (Set.empty).map(com.histmersion.api.library.Citekey.apply)): Either[com.histmersion.api.library.Source.Write.Problem, Source] =
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
        venue = (blankToNone(p.venue.map(_.value))).map(com.histmersion.api.library.Source.Venue.apply),
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

    def precondition(existing: Source, patch: Patch): Either[com.histmersion.api.library.Source.Write.Problem, Unit] =
      patch.ifUpdatedAt match
        case None       => Right(())
        case Some(want) =>
          val actual = existing.updatedAt.getOrElse("")
          if want == actual then Right(())
          else
            Left(
              _root_.com.histmersion.api.library.Source.Write.Problem(s"'${existing.citekey}' has moved on: you sent ifUpdatedAt='$want' but the stored " +
                s"updatedAt is '$actual'. Re-read the source and re-apply your patch.")
            )

    def applyPatch(existing: Source, patch: Patch): Either[com.histmersion.api.library.Source.Write.Problem, Source] =
      for
        _ <- precondition(existing, patch)
        period <- patch.period.fold[Either[String, Option[Period]]](Right(existing.period))(p =>
          periodOf(Some(p.start), Some(p.end), _root_.com.histmersion.api.library.Source.Write.PeriodOfWhat("source"))
        )
        fs <- patch.findings.fold[Either[String, List[Finding]]](Right(existing.findings))(fp =>
          (applyFindings(existing.citekey, existing.findings, fp)).left.map(refactorError8 => (refactorError8).value)
        )
        meta = patch.metadata.getOrElse(MetadataPatch())
        _ <- metadataCheck(meta)
        newEdges <- patch.edges.fold[Either[String, List[Edge]]](Right(existing.edges))(
          (applyEdges(existing.citekey, existing.edges, _)).left.map(refactorError9 => (refactorError9).value)
        )
        newStatus = meta.status.getOrElse(existing.status)
        _ <- supersededEdgeCheck(newStatus, newEdges)
      yield
        def cleared(f: MetadataField) = meta.clear.contains(f)
        existing.modify(
          _.title).setTo(meta.title.getOrElse(existing.title)).modify(_.authors).setTo(meta.authors.getOrElse(existing.authors)).modify(_.year).setTo(if cleared(MetadataField.Year) then None else meta.year.orElse(existing.year)).modify(_.venue).setTo(if cleared(MetadataField.Venue) then None
          else meta.venue.fold(existing.venue)(v => blankToNone(Some(v.value)).map(com.histmersion.api.library.Source.Venue.apply))).modify(_.doi).setTo(if cleared(MetadataField.Doi) then None
          else meta.doi.fold(existing.doi)(d => blankToNone(Some(d.value)).map(com.histmersion.api.library.Doi.apply))).modify(_.status).setTo(newStatus).modify(_.summary).setTo(patch.summary.orElse(existing.summary)).modify(_.ocrMarkdown).setTo(patch.body.orElse(existing.ocrMarkdown)).modify(_.notes).setTo(patch.notes.orElse(existing.notes)).modify(_.topics).setTo(patch.topics.getOrElse(existing.topics)).modify(_.regions).setTo(patch.regions.getOrElse(existing.regions)).modify(_.tags).setTo(patch.tags.fold(existing.tags)(applyList(existing.tags, _))).modify(_.period).setTo(period).modify(_.findings).setTo(fs).modify(_.edges).setTo(newEdges).modify(_.provenance).setTo(if cleared(MetadataField.Provenance) then None
          else meta.provenance.orElse(existing.provenance)).modify(_.calibreId).setTo(if cleared(MetadataField.CalibreId) then None
          else meta.calibreId.map(_.trim).orElse(existing.calibreId)
        )

    def srcAliased(p: Patch, requested: com.histmersion.api.library.Citekey, canonical: com.histmersion.api.library.Citekey): Patch =
      if requested == canonical then p
      else
        def fix(e: Edge.Input) = if e.src.map(_.trim).contains(requested) then e.modify(_.src).setTo(None) else e
        p.modify(_.edges).setTo(p.edges.map(ep => EdgesPatch(ep.add.map(fix), ep.remove.map(fix))))

    def applyEdges(citekey: com.histmersion.api.library.Citekey, current: List[Edge], p: EdgesPatch): Either[com.histmersion.api.library.Source.Write.Problem, List[Edge]] =
      for
        removed <- traverse(p.remove)((refactorCallback => (edgeOf(_root_.com.histmersion.api.library.Citekey(citekey.value.toString), Set.empty, _root_.com.histmersion.api.library.Source.EdgePart("edges.remove"), _root_.com.histmersion.api.library.Source.EdgeRole("patched source"), refactorCallback)).left.map(refactorError7 => _root_.com.histmersion.api.library.Source.Write.Problem((refactorError7).value))))
        added <- traverse(p.add)((refactorCallback => (edgeOf(_root_.com.histmersion.api.library.Citekey(citekey.value.toString), Set.empty, _root_.com.histmersion.api.library.Source.EdgePart("edges.add"), _root_.com.histmersion.api.library.Source.EdgeRole("patched source"), refactorCallback)).left.map(refactorError10 => _root_.com.histmersion.api.library.Source.Write.Problem((refactorError10).value))))
        _ <- added
          .find(_.dst == citekey)
          .fold(Right(()))(e =>
            Left(s"edges.add: '${e.dst}' --${e.kind}--> itself is a self-edge; dst must be another source")
          )
      yield
        val gone = removed.toSet
        (current.map(_.modify(_.src).setTo(citekey)).filterNot(gone) ++ added.filterNot(gone)).distinct

    private[api] def edgeOf(
        citekey: com.histmersion.api.library.Citekey,
        aliases: Set[com.histmersion.api.library.Source.Write.EdgeOfAliases],
        part: com.histmersion.api.library.Source.EdgePart,
        role: com.histmersion.api.library.Source.EdgeRole,
        in: Edge.Input
    ): Either[com.histmersion.api.library.Source.Write.Problem, Edge] =
      in.src.map(_.trim).filter(_.nonEmpty) match
        case Some(other) if other != citekey && !aliases.contains(other) =>
          Left(
            _root_.com.histmersion.api.library.Source.Write.Problem(s"$part: src '$other' is not the $role '$citekey'. An edge is stored on its " +
              s"source: patch '$other' to record '$other' --${com.histmersion.api.library.Edge.Kind.wireOf(in.kind)}--> '${in.dst}' " +
              s"(src may be omitted; it defaults to the $role)")
          )
        case _ => Right(Edge(com.histmersion.api.library.Citekey((citekey).value), com.histmersion.api.library.Citekey(in.dst.trim), com.histmersion.api.library.Edge.Kind.wireOf(in.kind)))

    private[api] def metadataCheck(m: MetadataPatch): Either[com.histmersion.api.library.Source.Write.Problem, Unit] =
      val both = m.clear.filter {
        case MetadataField.Year       => m.year.isDefined
        case MetadataField.Venue      => m.venue.isDefined
        case MetadataField.Doi        => m.doi.isDefined
        case MetadataField.Provenance => m.provenance.isDefined
        case MetadataField.CalibreId  => m.calibreId.isDefined
      }
      if both.nonEmpty then
        Left(_root_.com.histmersion.api.library.Source.Write.Problem(s"metadata: ${both.map(MetadataField.wireOf).mkString(", ")} is both set and cleared in one patch"))
      else
        m.calibreId.map(_.trim) match
          case Some(id) if !id.matches("[0-9]+") =>
            Left(_root_.com.histmersion.api.library.Source.Write.Problem(s"metadata.calibreId: '$id' is not a Calibre id (the entry's number, e.g. 1805)"))
          case _ => Right(())

    def applyFindings(citekey: com.histmersion.api.library.Citekey, current: List[Finding], p: FindingsPatch): Either[com.histmersion.api.library.Source.Write.Problem, List[Finding]] =
      for
        base <- p.replace.fold[Either[String, List[Finding]]](Right(current))((numbered(citekey, _)).left.map(refactorError5 => (refactorError5).value))
        kept = base.filterNot(f => p.remove.contains(f.id))
        added <- allocate(citekey, kept, p.add)
      yield kept ++ added

    private[api] def numbered(citekey: com.histmersion.api.library.Citekey, inputs: List[Finding.Input]): Either[com.histmersion.api.library.Source.Write.Problem, List[Finding]] =
      traverse(inputs.zipWithIndex)((in, i) => toFinding(citekey, findingId(citekey, _root_.com.histmersion.api.library.Finding.Ordinal(i)), in))

    private[api] def allocate(
        citekey: com.histmersion.api.library.Citekey,
        existing: List[Finding],
        adds: List[Finding.Input]
    ): Either[com.histmersion.api.library.Citekey, List[Finding]] =
      @tailrec def loop(
          rest: List[Finding.Input],
          used: Set[com.histmersion.api.library.Citekey],
          acc: List[Either[String, Finding]]
      ): List[Either[com.histmersion.api.library.Citekey, Finding]] =
        rest match
          case Nil        => acc.reverse
          case in :: tail =>
            val id = Iterator.from(0).map(findingId(citekey, _root_.com.histmersion.api.library.Finding.Ordinal(_))).find(n => !used.contains(n)).get
            loop(tail, used + id, toFinding(citekey, id, in) :: acc)
      traverse(loop(adds, existing.map(_.id).toSet, Nil))(Predef.identity)

    private[api] def toFinding(citekey: com.histmersion.api.library.Citekey, id: com.histmersion.api.library.Source.Write.ToFindingId, in: Finding.Input): Either[com.histmersion.api.library.Source.Write.Problem, Finding] =
      val what = s"finding '${in.claim.take(40)}'"
      for
        p <- periodOf(in.periodStart, in.periodEnd, what)
        conf <- confidenceOf(in.confidence, what)
      yield
        val geo = (in.lon, in.lat) match
          case (Some(x), Some(y)) => Some(GeoPoint(x, y))
          case _                  => None
        Finding(id, citekey, in.claim, in.region, geo, p, in.periodName, in.tags, conf, in.significance)

    private[api] def confidenceOf(raw: Option[com.histmersion.api.library.Confidence.Wire], what: String): Either[com.histmersion.api.library.Source.Write.Problem, Option[Confidence]] =
      raw.map((_).value.trim).filter(_.nonEmpty) match
        case None    => Right(None)
        case Some(c) =>
          Confidence
            .fromString(_root_.com.histmersion.api.library.Confidence.Wire(c))
            .map(Some(_))
            .toRight(_root_.com.histmersion.api.library.Source.Write.Problem(s"$what: confidence '$c' is not one of ${Confidence.values.map(_.wire).mkString("|")}"))

    /** Postgres rejects an inverted int8range. */
    private[api] def periodOf(start: Option[com.histmersion.api.catalogue.Year], end: Option[com.histmersion.api.catalogue.Year], what: com.histmersion.api.library.Source.Write.PeriodOfWhat): Either[com.histmersion.api.library.Source.Write.Problem, Option[Period]] =
      def sane(v: com.histmersion.api.catalogue.Year) = v >= Filter.Earliest && v <= Filter.Latest
      (start, end) match
        case (Some(a), _) if !sane(a)     => Left(_root_.com.histmersion.api.library.Source.Write.Problem((outOfRange(what, ("period_start"), _root_.com.histmersion.api.catalogue.Year((a).value))).value))
        case (_, Some(b)) if !sane(b)     => Left(_root_.com.histmersion.api.library.Source.Write.Problem((outOfRange(what, ("period_end"), _root_.com.histmersion.api.catalogue.Year((b).value))).value))
        case (Some(a), Some(b)) if a <= b => Right(Some(Period(a, b)))
        case (Some(a), Some(b))           => Left(_root_.com.histmersion.api.library.Source.Write.Problem(s"$what: period_start ($a) must not be after period_end ($b)"))
        case (None, None)                 => Right(None)
        case _                            => Left(_root_.com.histmersion.api.library.Source.Write.Problem(s"$what: give both period_start and period_end, or neither"))

    private[api] def outOfRange(what: String, field: String, v: com.histmersion.api.catalogue.Year): com.histmersion.api.library.Source.Write.Problem =
      _root_.com.histmersion.api.library.Source.Write.Problem(s"$what: $field ($v) is outside the representable range " +
        s"[${Filter.Earliest}, ${Filter.Latest}] — deep time is signed YEARS, BCE negative")

    private[api] def supersededEdgeCheck(
        status: com.histmersion.api.library.Source.Status,
        edges: List[Edge]
    ): Either[com.histmersion.api.library.Source.Write.Problem, Unit] =
      if status == com.histmersion.api.library.Source.Status.Superseded && !edges.exists(_.kind == "superseded_by")
      then
        Left(
          _root_.com.histmersion.api.library.Source.Write.Problem("status=superseded requires a superseded_by edge — add one with edges.add " +
            "(e.g. {\"edges\":{\"add\":[{\"dst\":\"newer_source\",\"kind\":\"superseded_by\"}]}}, " +
            "or the CLI flag --add-edge superseded_by:newer_source)")
        )
      else Right(())

    private[api] def traverse[A, B](as: List[A])(f: A => Either[com.histmersion.api.library.Source.Write.Problem, B]): Either[com.histmersion.api.library.Source.Write.Problem, List[B]] =
      (as.foldLeft[Either[String, List[B]]](Right(Nil))((acc, a) => acc.flatMap(bs => (f(a).map(bs :+ _)).left.map(refactorError14 => (refactorError14).value)))).left.map(refactorError4 => _root_.com.histmersion.api.library.Source.Write.Problem(refactorError4))
