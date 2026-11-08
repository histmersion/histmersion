package com.histmersion
package api.library

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
import com.histmersion.api.catalogue.Year
import com.histmersion.api.Api
import com.histmersion.api.exchange.PlanMatchKind

final case class Source(
    citekey: Citekey,
    title: Title,
    authors: List[AuthorName],
    doi: Option[Doi],
    year: Option[Year],
    venue: Option[Source.Venue],
    status: Source.Status,
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
    object Line extends Newtype[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AuthorSurname = AuthorSurname.T
  object AuthorSurname extends Newtype[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Body = Body.T
  object Body extends Newtype[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EdgePart = EdgePart.T
  object EdgePart extends Newtype[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EdgeRole = EdgeRole.T
  object EdgeRole extends Newtype[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Export = Export.T
  object Export extends Newtype[String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Venue = Venue.T
  object Venue extends Newtype[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type View = View.T
  object View extends Newtype[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Topics = Topics.T
  object Topics extends Newtype[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Regions = Regions.T
  object Regions extends Newtype[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Tags = Tags.T
  object Tags extends Newtype[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Summary = Summary.T
  object Summary extends Newtype[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CitedByCount = CitedByCount.T
  object CitedByCount extends Newtype[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InfluentialCitations = InfluentialCitations.T
  object InfluentialCitations extends Newtype[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Provenance = Provenance.T
  object Provenance extends Newtype[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CalibreId = CalibreId.T
  object CalibreId extends Newtype[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OcrMarkdown = OcrMarkdown.T
  object OcrMarkdown extends Newtype[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Notes = Notes.T
  object Notes extends Newtype[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends Newtype[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  enum Status:
    case Foundational, Current, Contested, Superseded, Debunked, Retracted, Unassessed

    /** The wire / storage spelling (`current`), as the JSON codec writes it. */
    def wire: com.histmersion.api.library.Source.Status.Wire = _root_.com.histmersion.api.library.Source.Status.Wire(Ascii.lower(toString))

  object Status:
    type Wire = Wire.T
    object Wire extends Newtype[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


    def fromString(s: Wire): Status =
      values.find(_.toString.equalsIgnoreCase((s).value)).getOrElse(Unassessed)

    given WireEnumValues[Status]:
      def all: Array[Status] = Status.values
      def wire(e: Status): _root_.com.histmersion.api.WireEnumValues.Wire = _root_.com.histmersion.api.WireEnumValues.Wire((Wire(e.toString.toLowerCase)).value)
      def fromWire(s: WireEnumValues.Wire): Option[Status] =
        values.find(_.toString.equalsIgnoreCase((s).value))

  final case class Alias(alias: Alias.Value, citekey: Citekey)
      derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object Alias:
    export com.histmersion.api.library.{Citekey as Value}

  final case class Candidate(
      id: Candidate.Id,
      goal: Candidate.Goal,
      doi: Doi,
      title: Title,
      year: Option[Year] = None,
      authors: List[AuthorName] = (Nil).map(com.histmersion.api.library.AuthorName.apply),
      venue: Option[com.histmersion.api.library.Source.Venue] = None,
      abstractText: Option[Candidate.AbstractText] = None,
      isOa: Candidate.IsOa = _root_.com.histmersion.api.library.Source.Candidate.IsOa(false),
      arxivId: Option[Candidate.ArxivId] = None,
      foundBy: List[Candidate.FoundBy] = Nil,
      state: Candidate.State = Candidate.State(Candidates.State.New.wire.value),
      firstSeen: Candidate.FirstSeen = _root_.com.histmersion.api.library.Source.Candidate.FirstSeen(""),
      lastSeen: Candidate.LastSeen = _root_.com.histmersion.api.library.Source.Candidate.LastSeen("")
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object Candidate:
    type Id = Id.T
    object Id extends Newtype[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    export com.histmersion.api.research.ReviewGoal.{Slug as Goal}
    type AbstractText = AbstractText.T
    object AbstractText extends Newtype[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type IsOa = IsOa.T
    object IsOa extends Newtype[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ArxivId = ArxivId.T
    object ArxivId extends Newtype[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FoundBy = FoundBy.T
    object FoundBy extends Newtype[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type State = State.T
    object State extends Newtype[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FirstSeen = FirstSeen.T
    object FirstSeen extends Newtype[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LastSeen = LastSeen.T
    object LastSeen extends Newtype[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  object Delete:
    type CalibreLine = CalibreLine.T
    object CalibreLine extends Newtype[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LogText = LogText.T
    object LogText extends Newtype[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Markdown = Markdown.T
    object Markdown extends Newtype[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Problem = Problem.T
    object Problem extends Newtype[String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    export com.histmersion.api.library.Source.{CalibreId as CalibreLineCalibreId}

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
        s"${k.label} ${bs.map(b => b.id.value + b.title.fold("")(t => s" ($t)")).mkString(", ")}"
      }
      _root_.com.histmersion.api.library.Source.Delete.Problem(s"'$ck' cannot be deleted — ${groups.mkString("; ")}. Fix those first (remove the citation from " +
        "each article, re-source the events, let the job finish); there is no override")

    def logText(d: Deletion): LogText =
      def short(p: DeletePart) = p match
        case DeletePart.EdgesIn => "edges in (parked)"
        case other              => other.wire.value.replace('-', ' ')
      val parts = d.counts.filter(_.rows.value > 0).map(c => s"${c.rows} ${short(c.part)}")
      val files = if d.files.isEmpty then Nil else List(Plural.count(_root_.com.histmersion.api.Plural.CountN((d.files.size).toLong), _root_.com.histmersion.api.Plural.CountSingular("stored original")))
      val calibre = d.calibreId.map(id => s"Calibre entry $id kept").toList
      _root_.com.histmersion.api.library.Source.Delete.LogText(s"deleted ${d.citekey} (${d.title})" +
        (parts ++ files ++ calibre match
          case Nil => ""
          case xs  => ": " + xs.mkString(", ")))

    def markdown(d: Deletion, calibre: Option[CalibreEntryCheck] = None): Markdown =
      val head =
        if ((d.applied) == Deletion.Applied(true)) then s"# Deleted `${d.citekey}`"
        else if ((d.refused) == (true)) then s"# Cannot delete `${d.citekey}`"
        else s"# Would delete `${d.citekey}` (dry run)"
      val blockers =
        if d.blockers.isEmpty then Nil
        else
          List("", "**Refused — fix these first (there is no override):**", "") ++
            d.blockers.map(b => s"- ${b.kind.label}: `${b.id}`${b.title.fold("")(t => s" — $t")}")
      val verb = if ((d.applied) == Deletion.Applied(true)) then "removed" else "would go"
      val counts =
        if d.counts.isEmpty then List("", s"Nothing besides the record itself $verb.")
        else
          List("", s"**${if ((d.applied) == Deletion.Applied(true)) then "Removed" else "Would remove"}** (with the record itself):", "") ++
            d.counts.map(c => s"- ${c.part.label}: ${c.rows}")
      val aliases = if d.aliases.isEmpty then Nil
      else List("", s"Old citekeys that stop resolving: ${d.aliases.map(a => s"`$a`").mkString(", ")}")
      val edgesIn = if d.edgesIn.isEmpty then Nil
      else
        List(
          "",
          s"Edges of other sources to it (${if ((d.applied) == Deletion.Applied(true)) then "parked" else "parked, not lost"}: " +
            "they come back if it is ingested again):",
          ""
        ) ++
          d.edgesIn.map(e => s"- `${e.src}` ${Edge.Kind.wireOf(e.kind)} → `${e.dst}`")
      val coverage = if d.coverage.isEmpty then Nil
      else List("", s"Coverage reasons dropped from: ${d.coverage.map(s => s"`$s`").mkString(", ")}")
      val files =
        if d.files.isEmpty then List("", "Stored original: none on the server.")
        else
          List("", s"Stored original (${if ((d.applied) == Deletion.Applied(true)) then "removed" else "removed after the commit"}):", "") ++
            d.files.map(f => s"- `$f`")
      val failed = if d.filesFailed.isEmpty then Nil
      else List("", "**Could not remove** (logged; remove by hand):", "") ++ d.filesFailed.map(f => s"- $f")
      val mentions = if d.mentions.isEmpty then Nil
      else List("", s"Queue items that mention it (left as they are): ${d.mentions.map(i => s"#$i").mkString(", ")}")
      val calibreLines = List("", calibreLine(d.calibreId, calibre))
      val tail =
        if ((d.applied) == Deletion.Applied(true)) then
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

  final case class DeleteRequest(apply: DeleteRequest.Apply = Api.Apply(false)) derives KindlingsSchema, com.histmersion.api.json.Schema
  object DeleteRequest:

    export Api.Apply

    given Codec.AsObject[DeleteRequest] = StrictJson.codec(KindlingsCodecAsObject.derived[DeleteRequest])

  final case class Deletion(
      citekey: Citekey,
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
      calibreId: Option[CalibreId] = None,
      logEntry: Option[Deletion.LogEntry] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema:
    def refused: Boolean = (blockers.nonEmpty)
    def count(p: DeletePart): com.histmersion.api.RowCount = counts.find(_.part == p).fold(com.histmersion.api.RowCount(0))(c => com.histmersion.api.RowCount(c.rows.value))

  object Deletion:

    export com.histmersion.api.library.Title
    type Applied = Applied.T
    object Applied extends Newtype[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Aliases = Aliases.T
    object Aliases extends Newtype[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Coverage = Coverage.T
    object Coverage extends Newtype[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Files = Files.T
    object Files extends Newtype[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FilesFailed = FilesFailed.T
    object FilesFailed extends Newtype[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Mentions = Mentions.T
    object Mentions extends Newtype[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LogEntry = LogEntry.T
    object LogEntry extends Newtype[Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Detail(
      citekey: Citekey,
      title: Title,
      authors: List[AuthorName],
      year: Option[Year],
      venue: Option[Venue],
      status: Status,
      topics: List[Topics],
      regions: List[Regions],
      tags: List[Tags],
      periodStart: Option[Year],
      periodEnd: Option[Year],
      summary: Option[Summary],
      doi: Option[Doi],
      ocrMarkdown: Option[Detail.OcrMarkdown],
      notes: Option[Notes] = None,
      updatedAt: Option[Detail.UpdatedAt] = None,
      provenance: Option[Provenance] = None,
      uid: Option[java.util.UUID] = None,
      origin: Option[java.util.UUID] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object Detail:
    export com.histmersion.api.library.Source.{OcrMarkdown, UpdatedAt}

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
    object DepthCount extends Newtype[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    export com.histmersion.api.library.{Citekey as Root}
    type Depth = com.histmersion.api.library.Source.Graph.DepthCount
    val Depth: _root_.com.histmersion.api.library.Source.Graph.DepthCount.type = _root_.com.histmersion.api.library.Source.Graph.DepthCount

  object Kinds:
    type Wire = Wire.T
    object Wire extends Newtype[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


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

    private val BookProvenance = Set("calibre", com.histmersion.api.library.Provenance.ExternalFile, "archive", "archive_org")

    def of(
        doi: Option[Doi],
        venue: Option[Venue],
        provenance: Option[Provenance],
        tags: List[com.histmersion.api.library.Source.Tags],
        topics: List[com.histmersion.api.library.Source.Topics],
        hasCover: Boolean
    ): com.histmersion.api.library.Source.Kinds.Wire =
      val words = (tags.map(_.value) ++ topics.map(_.value)).map(_.toLowerCase(java.util.Locale.ROOT))
      val where = venue.map(_.value).getOrElse("").toLowerCase(java.util.Locale.ROOT)
      val id = doi.map((_).value.trim.toLowerCase(java.util.Locale.ROOT)).filter(_.nonEmpty)
      def says(w: String*) = words.exists(t => w.exists(t.contains)) || w.exists(where.contains)
      if says("thesis", "dissertation") then _root_.com.histmersion.api.library.Source.Kinds.Wire(Thesis)
      else if words.exists(_.contains("dataset")) || id.exists(d => DataDoiPrefixes.exists(d.startsWith)) then _root_.com.histmersion.api.library.Source.Kinds.Wire(Dataset)
      else
        id.flatMap({ val refactorFunction2 = isbnDoi; (refactorValue14) => refactorFunction2(_root_.com.histmersion.api.library.Doi(refactorValue14)) }) match
          case Some(chapter)        => if (chapter) then _root_.com.histmersion.api.library.Source.Kinds.Wire(Chapter) else _root_.com.histmersion.api.library.Source.Kinds.Wire(Book)
          case None if id.isDefined => _root_.com.histmersion.api.library.Source.Kinds.Wire(Paper)
          case None                 =>
            if ((hasCover) == (true)) || provenance.map((_).value.trim.toLowerCase(java.util.Locale.ROOT)).exists(BookProvenance.contains)
            then _root_.com.histmersion.api.library.Source.Kinds.Wire(Book)
            else _root_.com.histmersion.api.library.Source.Kinds.Wire(Unknown)

    def isbnDoi(doi: Doi): Option[Boolean] =
      val suffix = (doi).value.dropWhile(_ != '/').drop(1).toLowerCase(java.util.Locale.ROOT).stripPrefix("cbo")
      if !(suffix.startsWith("978") || suffix.startsWith("979")) then None
      else
        @tailrec def isbnEnd(i: Int, digits: Int): Option[Int] =
          if digits >= 13 then Some(i)
          else if (i) < suffix.length && suffix((i)).isDigit then isbnEnd(((i) + 1), ((digits) + 1))
          else if (i) < suffix.length && suffix((i)) == '-' then isbnEnd(((i) + 1), digits)
          else None
        isbnEnd((0), (0)).map(end => (suffix.drop((end)).exists(_.isLetterOrDigit)))

  final case class Match(
      rightUid: UUID,
      rightCitekey: Citekey,
      leftUid: Option[UUID],
      leftCitekey: Option[Citekey],
      method: MatchMethod,
      outcome: MatchOutcome,
      score: Option[Match.Score] = None,
      why: List[Match.Why] = Nil,
      issue: Option[Match.Issue] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema
  object Match:
    export com.histmersion.api.exchange.PlanMatchKind.{MatchLine as Line}

    /** A source's match in a merge plan: the plan's Markdown lists every one but the new sources. */

    type Score = Score.T
    object Score extends Newtype[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Why = Why.T
    object Why extends Newtype[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Issue = Issue.T
    object Issue extends Newtype[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val Matches: PlanMatchKind[Match] =
      new com.histmersion.api.exchange.PlanMatchKind(m => PlanMatchKind.ListedResult(m.outcome != MatchOutcome.New), line)

    /** A source's match as the plan's Markdown lists it. */
    def line(s: Match): Match.Line =
      val target = s.leftCitekey.fold("")(c => s" → `$c`")
      val score = s.score.fold("")(x => s" · score ${math.round((x).value * 10) / 10.0}")
      Match.Line(s"- `${s.rightCitekey}`$target · ${MatchOutcome.wireOf(s.outcome)}$score" + s.issue.fold("")(i => s" · $i"))

  final case class Patch(
      metadata: Option[MetadataPatch] = None,
      summary: Option[Summary] = None,
      body: Option[OcrMarkdown] = None,
      notes: Option[Notes] = None,
      topics: Option[List[Topics]] = None,
      regions: Option[List[Regions]] = None,
      period: Option[Period.Input] = None,
      tags: Option[ListPatch] = None,
      findings: Option[FindingsPatch] = None,
      edges: Option[EdgesPatch] = None,
      ifUpdatedAt: Option[UpdatedAt] = None
  ) derives KindlingsSchema,
        com.histmersion.api.json.Schema
  object Patch:

    given Codec.AsObject[Patch] = StrictJson.codec(KindlingsCodecAsObject.derived[Patch])

  final case class Put(
      title: Title,
      authors: List[AuthorName] = (Nil).map(AuthorName.apply),
      doi: Option[Doi] = None,
      year: Option[Year] = None,
      venue: Option[Venue] = None,
      status: Option[Status] = None,
      topics: List[Topics] = Nil,
      regions: List[Regions] = Nil,
      tags: List[Tags] = Nil,
      periodStart: Option[Year] = None,
      periodEnd: Option[Year] = None,
      summary: Option[Summary] = None,
      body: Option[OcrMarkdown] = None,
      notes: Option[Notes] = None,
      citedByCount: Option[CitedByCount] = None,
      influentialCitations: Option[InfluentialCitations] = None,
      provenance: Option[Provenance] = None,
      calibreId: Option[CalibreId] = None,
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
      edges = s.edges.map(e => Edge.Input(e.dst, e.kind))
    )

  /** `idLabel` is the DOI when present, else the citekey: the owner's decision to show DOIs in the UI while keeping the
    * internal naming.
    */
  final case class Ref(
      citekey: Citekey,
      doi: Option[Doi],
      authors: List[AuthorName],
      year: Option[Year],
      title: Title,
      status: com.histmersion.api.library.Source.Status
  ):
    def idLabel: com.histmersion.api.library.Source.Ref.Label = _root_.com.histmersion.api.library.Source.Ref.Label(doi.map(_.value).getOrElse(citekey.value.toString))

    def shortCite: com.histmersion.api.library.Source.Ref.ShortCite =
      val auth = Ref.shortAuthors(authors)
      val yr = year.map(_.toString).getOrElse("n.d.")
      if (auth).value.isEmpty then _root_.com.histmersion.api.library.Source.Ref.ShortCite(yr) else _root_.com.histmersion.api.library.Source.Ref.ShortCite(s"$auth $yr")

  object Ref:
    type Label = Label.T
    object Label extends Newtype[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ShortAuthors = ShortAuthors.T
    object ShortAuthors extends Newtype[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ShortCite = ShortCite.T
    object ShortCite extends Newtype[String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def surname(full: com.histmersion.api.library.AuthorName): com.histmersion.api.library.Source.AuthorSurname =
      val t = (full).value.trim
      if t.contains(",") then _root_.com.histmersion.api.library.Source.AuthorSurname(t.takeWhile(_ != ',').trim)
      else _root_.com.histmersion.api.library.Source.AuthorSurname(t.split("\\s+").lastOption.getOrElse(t))

    def shortAuthors(authors: List[com.histmersion.api.library.AuthorName]): com.histmersion.api.library.Source.Ref.ShortAuthors =
      val names = authors.map((_).value.trim).filter(_.nonEmpty)
      names match
        case Nil           => _root_.com.histmersion.api.library.Source.Ref.ShortAuthors("")
        case a :: Nil      => _root_.com.histmersion.api.library.Source.Ref.ShortAuthors((surname(_root_.com.histmersion.api.library.AuthorName(a))).value)
        case a :: b :: Nil => _root_.com.histmersion.api.library.Source.Ref.ShortAuthors((surname(_root_.com.histmersion.api.library.AuthorName(a))).value + " & " + surname(_root_.com.histmersion.api.library.AuthorName(b)))
        case a :: _        => ShortAuthors((surname(AuthorName(a))).value + " et al.")

  final case class Renamed(
      oldCitekey: Citekey,
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
    object Wire extends Newtype[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val Summary = "summary"
    val Findings = "findings"
    val Notes = "notes"
    val Toc = "toc"
    val Body = "body"
    val All = "all"
    val all: List[com.histmersion.api.library.Source.Section.Wire] = List(_root_.com.histmersion.api.library.Source.Section.Wire(Summary), _root_.com.histmersion.api.library.Source.Section.Wire(Findings), _root_.com.histmersion.api.library.Source.Section.Wire(Notes), _root_.com.histmersion.api.library.Source.Section.Wire(Toc), _root_.com.histmersion.api.library.Source.Section.Wire(Body), _root_.com.histmersion.api.library.Source.Section.Wire(All))

    def parse(s: Wire): Option[Wire] =
      val lower = (s).value.trim.toLowerCase
      all.find(_ == Wire(lower))

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
    object Problem extends Newtype[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def findingId(citekey: Citekey, n: Finding.Ordinal): Finding.Id = Finding.Id(s"$citekey#$n")

    def applyPut(citekey: com.histmersion.api.library.Citekey, p: Put, aliases: Set[com.histmersion.api.library.Citekey] = Set.empty): Either[com.histmersion.api.library.Source.Write.Problem, Source] =
      for
        period <- periodOf(p.periodStart, p.periodEnd, "source")
        fs <- numbered(citekey, p.findings)
        edges <- traverse(p.edges)(edgeOf(citekey, aliases, EdgePart("edges"), EdgeRole("source being put"), _))
        _ <- edges
          .find(e => e.dst == citekey || aliases.contains(e.dst))
          .fold(Right(()))(e =>
            Left(Problem(s"edges: '${e.dst}' --${Edge.Kind.wireOf(e.kind)}--> itself is a self-edge; dst must be another source"))
          )
      yield Source(
        citekey = citekey,
        title = p.title,
        authors = p.authors,
        doi = (blankToNone(p.doi.map(_.value))).map(Doi.apply),
        year = p.year,
        venue = (blankToNone(p.venue.map(_.value))).map(Venue.apply),
        status = p.status.getOrElse(Status.Unassessed),
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
          val actual = existing.updatedAt.map(_.value).getOrElse("")
          if want.value == actual then Right(())
          else
            Left(
              Problem(s"'${existing.citekey}' has moved on: you sent ifUpdatedAt='$want' but the stored " +
                s"updatedAt is '$actual'. Re-read the source and re-apply your patch.")
            )

    def applyPatch(existing: Source, patch: Patch): Either[Problem, Source] =
      for
        _ <- precondition(existing, patch)
        period <- patch.period.fold[Either[Problem, Option[Period]]](Right(existing.period))(p =>
          periodOf(Some(p.start), Some(p.end), "source")
        )
        fs <- patch.findings.fold[Either[Problem, List[Finding]]](Right(existing.findings))(fp =>
          applyFindings(existing.citekey, existing.findings, fp)
        )
        meta = patch.metadata.getOrElse(MetadataPatch())
        _ <- metadataCheck(meta)
        newEdges <- patch.edges.fold[Either[Problem, List[Edge]]](Right(existing.edges))(
          applyEdges(existing.citekey, existing.edges, _)
        )
        newStatus = meta.status.getOrElse(existing.status)
        _ <- supersededEdgeCheck(newStatus, newEdges)
      yield
        def cleared(f: MetadataField) = meta.clear.contains(f)
        existing.copy(
          title = meta.title.getOrElse(existing.title),
          authors = meta.authors.getOrElse(existing.authors),
          year = if cleared(MetadataField.Year) then None else meta.year.orElse(existing.year),
          venue = if cleared(MetadataField.Venue) then None
          else meta.venue.fold(existing.venue)(v => blankToNone(Some(v.value)).map(Venue.apply)),
          doi = if cleared(MetadataField.Doi) then None
          else meta.doi.fold(existing.doi)(d => blankToNone(Some(d.value)).map(Doi.apply)),
          status = newStatus,
          summary = patch.summary.orElse(existing.summary),
          ocrMarkdown = patch.body.orElse(existing.ocrMarkdown),
          notes = patch.notes.orElse(existing.notes),
          topics = patch.topics.getOrElse(existing.topics),
          regions = patch.regions.getOrElse(existing.regions),
          tags = patch.tags.fold(existing.tags)(lp =>
            applyList(existing.tags.map(t => com.histmersion.api.Write.ListItem(t.value)), lp).map(i => Tags(i.value))
          ),
          period = period,
          findings = fs,
          edges = newEdges,
          provenance = if cleared(MetadataField.Provenance) then None
          else meta.provenance.orElse(existing.provenance),
          calibreId = if cleared(MetadataField.CalibreId) then None
          else meta.calibreId.map(c => CalibreId(c.value.trim)).orElse(existing.calibreId)
        )

    def srcAliased(p: Patch, requested: Citekey, canonical: Citekey): Patch =
      if requested == canonical then p
      else
        def fix(e: Edge.Input) = if e.src.map(_.value.trim).contains(requested.value) then e.copy(src = None) else e
        p.copy(edges = p.edges.map(ep => EdgesPatch(ep.add.map(fix), ep.remove.map(fix))))

    def applyEdges(citekey: com.histmersion.api.library.Citekey, current: List[Edge], p: EdgesPatch): Either[com.histmersion.api.library.Source.Write.Problem, List[Edge]] =
      for
        removed <- traverse(p.remove)(edgeOf(citekey, Set.empty, EdgePart("edges.remove"), EdgeRole("patched source"), _))
        added <- traverse(p.add)(edgeOf(citekey, Set.empty, EdgePart("edges.add"), EdgeRole("patched source"), _))
        _ <- added
          .find(_.dst == citekey)
          .fold(Right(()))(e =>
            Left(Problem(s"edges.add: '${e.dst}' --${Edge.Kind.wireOf(e.kind)}--> itself is a self-edge; dst must be another source"))
          )
      yield
        val gone = removed.toSet
        (current.map(_.copy(src = citekey)).filterNot(gone) ++ added.filterNot(gone)).distinct

    private[api] def edgeOf(
        citekey: Citekey,
        aliases: Set[Citekey],
        part: EdgePart,
        role: EdgeRole,
        in: Edge.Input
    ): Either[Problem, Edge] =
      in.src.map(s => com.histmersion.api.library.Citekey(s.value.trim)).filter(_.value.nonEmpty) match
        case Some(other) if other != citekey && !aliases.contains(other) =>
          Left(
            Problem(s"$part: src '$other' is not the $role '$citekey'. An edge is stored on its " +
              s"source: patch '$other' to record '$other' --${Edge.Kind.wireOf(in.kind)}--> '${in.dst}' " +
              s"(src may be omitted; it defaults to the $role)")
          )
        case _ => Right(Edge(citekey, Citekey(in.dst.value.trim), in.kind))

    private[api] def metadataCheck(m: MetadataPatch): Either[Problem, Unit] =
      val both = m.clear.filter {
        case MetadataField.Year       => m.year.isDefined
        case MetadataField.Venue      => m.venue.isDefined
        case MetadataField.Doi        => m.doi.isDefined
        case MetadataField.Provenance => m.provenance.isDefined
        case MetadataField.CalibreId  => m.calibreId.isDefined
      }
      if both.nonEmpty then
        Left(Problem(s"metadata: ${both.map(MetadataField.wireOf).mkString(", ")} is both set and cleared in one patch"))
      else
        m.calibreId.map(_.value.trim) match
          case Some(id) if !id.matches("[0-9]+") =>
            Left(Problem(s"metadata.calibreId: '$id' is not a Calibre id (the entry's number, e.g. 1805)"))
          case _ => Right(())

    def applyFindings(citekey: Citekey, current: List[Finding], p: FindingsPatch): Either[Problem, List[Finding]] =
      for
        base <- p.replace.fold[Either[Problem, List[Finding]]](Right(current))(numbered(citekey, _))
        removing = p.remove.map(_.value).toSet
        kept = base.filterNot(f => removing.contains(f.id.value))
        added <- allocate(citekey, kept, p.add)
      yield kept ++ added

    private[api] def numbered(citekey: Citekey, inputs: List[Finding.Input]): Either[Problem, List[Finding]] =
      traverse(inputs.zipWithIndex)((in, i) => toFinding(citekey, findingId(citekey, Finding.Ordinal(i)), in))

    private[api] def allocate(
        citekey: Citekey,
        existing: List[Finding],
        adds: List[Finding.Input]
    ): Either[Problem, List[Finding]] =
      @tailrec def loop(
          rest: List[Finding.Input],
          used: Set[Finding.Id],
          acc: List[Either[Problem, Finding]]
      ): List[Either[Problem, Finding]] =
        rest match
          case Nil        => acc.reverse
          case in :: tail =>
            val id = Iterator.from(0).map(n => findingId(citekey, Finding.Ordinal(n))).find(n => !used.contains(n)).get
            loop(tail, used + id, toFinding(citekey, id, in) :: acc)
      traverse(loop(adds, existing.map(_.id).toSet, Nil))(identity)

    private[api] def toFinding(citekey: Citekey, id: Finding.Id, in: Finding.Input): Either[Problem, Finding] =
      val what = s"finding '${in.claim.value.take(40)}'"
      for
        p <- periodOf(in.periodStart, in.periodEnd, what)
        conf <- confidenceOf(in.confidence, what)
      yield
        val geo = (in.lon, in.lat) match
          case (Some(x), Some(y)) => Some(GeoPoint(x, y))
          case _                  => None
        Finding(id, citekey, in.claim, in.region, geo, p, in.periodName, in.tags, conf, in.significance)

    private[api] def confidenceOf(raw: Option[Confidence.Wire], what: String): Either[Problem, Option[Confidence]] =
      raw.map((_).value.trim).filter(_.nonEmpty) match
        case None    => Right(None)
        case Some(c) =>
          Confidence
            .fromString(_root_.com.histmersion.api.library.Confidence.Wire(c))
            .map(Some(_))
            .toRight(Problem(s"$what: confidence '$c' is not one of ${Confidence.values.map(_.wire).mkString("|")}"))

    /** Postgres rejects an inverted int8range. */
    private[api] def periodOf(start: Option[Year], end: Option[Year], what: String): Either[Problem, Option[Period]] =
      def sane(v: Year) = v.value >= Filter.Earliest.value && v.value <= Filter.Latest.value
      (start, end) match
        case (Some(a), _) if !sane(a)             => Left(outOfRange(what, "period_start", a))
        case (_, Some(b)) if !sane(b)             => Left(outOfRange(what, "period_end", b))
        case (Some(a), Some(b)) if a.value <= b.value => Right(Some(Period(a, b)))
        case (Some(a), Some(b))                   => Left(Problem(s"$what: period_start ($a) must not be after period_end ($b)"))
        case (None, None)                         => Right(None)
        case _                                    => Left(Problem(s"$what: give both period_start and period_end, or neither"))

    private[api] def outOfRange(what: String, field: String, v: Year): com.histmersion.api.library.Source.Write.Problem =
      Problem(s"$what: $field ($v) is outside the representable range " +
        s"[${Filter.Earliest}, ${Filter.Latest}] — deep time is signed YEARS, BCE negative")

    private[api] def supersededEdgeCheck(
        status: Status,
        edges: List[Edge]
    ): Either[Problem, Unit] =
      if status == Status.Superseded && !edges.exists(_.kind == com.histmersion.api.library.Edge.Kind.SupersededBy)
      then
        Left(
          Problem("status=superseded requires a superseded_by edge — add one with edges.add " +
            "(e.g. {\"edges\":{\"add\":[{\"dst\":\"newer_source\",\"kind\":\"superseded_by\"}]}}, " +
            "or the CLI flag --add-edge superseded_by:newer_source)")
        )
      else Right(())

    private[api] def traverse[A, B](as: List[A])(f: A => Either[Problem, B]): Either[Problem, List[B]] =
      as.foldLeft[Either[Problem, List[B]]](Right(Nil))((acc, a) => acc.flatMap(bs => f(a).map(bs :+ _)))
