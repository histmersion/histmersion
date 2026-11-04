package com.histmersion.api.library
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

final case class Source(
    citekey: String,
    title: String,
    authors: List[String],
    doi: Option[String],
    year: Option[Int],
    venue: Option[String],
    status: com.histmersion.api.library.Source.Status,
    topics: List[String],
    regions: List[String],
    tags: List[String],
    period: Option[Period],
    summary: Option[String],
    citedByCount: Option[Int],
    influentialCitations: Option[Int],
    provenance: Option[String],
    calibreId: Option[String],
    ocrMarkdown: Option[String] = None,
    findings: List[Finding] = Nil,
    edges: List[Edge] = Nil,
    notes: Option[String] = None,
    updatedAt: Option[String] = None
)

object Source:

  enum Status:
    case Foundational, Current, Contested, Superseded, Debunked, Retracted, Unassessed

    /** The wire / storage spelling (`current`), as the JSON codec writes it. */
    def wire: String = Ascii.lower(toString)

  object Status:

    def fromString(s: String): Status =
      values.find(_.toString.equalsIgnoreCase(s)).getOrElse(Unassessed)

    given WireEnumValues[Status]:
      def all: Array[Status] = Status.values
      def wire(e: Status): String = e.toString.toLowerCase
      def fromWire(s: String): Option[Status] =
        Status.values.find(_.toString.equalsIgnoreCase(s))

  final case class Alias(alias: String, citekey: String) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema


  final case class Candidate(
      id: Long,
      goal: String,
      doi: String,
      title: String,
      year: Option[Int] = None,
      authors: List[String] = Nil,
      venue: Option[String] = None,
      abstractText: Option[String] = None,
      isOa: Boolean = false,
      arxivId: Option[String] = None,
      foundBy: List[String] = Nil,
      state: String = Candidates.State.New.wire,
      firstSeen: String = "",
      lastSeen: String = ""
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema


  object Delete:

    def calibreLine(calibreId: Option[String], check: Option[CalibreEntryCheck]): String =
      calibreId match
        case None     => "Calibre: no linked entry."
        case Some(id) =>
          check match
            case Some(CalibreEntryCheck.Present) =>
              s"Calibre: entry $id is linked and is NOT touched — remove it in Calibre if it should go too."
            case Some(CalibreEntryCheck.Missing) =>
              s"Calibre: entry $id no longer exists in the library — nothing to remove there."
            case Some(CalibreEntryCheck.Unchecked(why)) =>
              s"Calibre: the record links entry $id (the library could not be checked: $why); it is NOT touched — " +
                "remove it in Calibre if it is there and should go too."
            case None =>
              s"Calibre: the record links entry $id (not checked against the library); it is NOT touched — " +
                "remove it in Calibre if it is there and should go too."

    def refusal(ck: String, blockers: List[DeleteBlocker]): String =
      val groups = blockers.groupBy(_.kind).toList.sortBy(_._1.ordinal).map { (k, bs) =>
        s"${k.label} ${bs.map(b => b.id + b.title.fold("")(t => s" ($t)")).mkString(", ")}"
      }
      s"'$ck' cannot be deleted — ${groups.mkString("; ")}. Fix those first (remove the citation from " +
        "each article, re-source the events, let the job finish); there is no override"

    def logText(d: Deletion): String =
      def short(p: DeletePart) = p match
        case DeletePart.EdgesIn => "edges in (parked)"
        case other              => other.wire.replace('-', ' ')
      val parts = d.counts.filter(_.rows > 0).map(c => s"${c.rows} ${short(c.part)}")
      val files = if d.files.isEmpty then Nil else List(Plural.count(d.files.size, "stored original"))
      val calibre = d.calibreId.map(id => s"Calibre entry $id kept").toList
      s"deleted ${d.citekey} (${d.title})" +
        (parts ++ files ++ calibre match
          case Nil => ""
          case xs  => ": " + xs.mkString(", "))

    def markdown(d: Deletion, calibre: Option[CalibreEntryCheck] = None): String =
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
      (List(head, "", s"**${d.title}**") ++ blockers ++ counts ++ aliases ++ edgesIn ++ coverage ++ files ++ failed ++
        mentions ++ calibreLines ++ tail).mkString("\n")

  final case class DeleteRequest(apply: Boolean = false) derives KindlingsSchema, com.histmersion.api.json.Schema
  object DeleteRequest:


    given Codec.AsObject[DeleteRequest] = StrictJson.codec(KindlingsCodecAsObject.derived[DeleteRequest])

  final case class Deletion(
      citekey: String,
      title: String,
      applied: Boolean,
      blockers: List[DeleteBlocker] = Nil,
      counts: List[DeleteCount] = Nil,
      aliases: List[String] = Nil,
      edgesIn: List[Edge] = Nil,
      coverage: List[String] = Nil,
      files: List[String] = Nil,
      filesFailed: List[String] = Nil,
      mentions: List[Int] = Nil,
      calibreId: Option[String] = None,
      logEntry: Option[Int] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema:
    def refused: Boolean = blockers.nonEmpty
    def count(p: DeletePart): Int = counts.find(_.part == p).fold(0)(_.rows)



  final case class Detail(
      citekey: String,
      title: String,
      authors: List[String],
      year: Option[Int],
      venue: Option[String],
      status: com.histmersion.api.library.Source.Status,
      topics: List[String],
      regions: List[String],
      tags: List[String],
      periodStart: Option[Long],
      periodEnd: Option[Long],
      summary: Option[String],
      doi: Option[String],
      ocrMarkdown: Option[String],
      notes: Option[String] = None,
      updatedAt: Option[String] = None,
      provenance: Option[String] = None,
      uid: Option[java.util.UUID] = None,
      origin: Option[java.util.UUID] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema


  final case class Graph(
      root: String,
      depth: Int,
      nodes: List[GraphNode],
      links: List[GraphLink]
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema



  object Kinds:
    val Book = "book"
    val Chapter = "chapter"
    val Paper = "paper"
    val Thesis = "thesis"
    val Dataset = "dataset"
    val Unknown = "unknown"
    val all: List[String] = List(Book, Chapter, Paper, Thesis, Dataset, Unknown)

    /** DOI prefixes of data repositories (Dryad, figshare, Mendeley Data, Harvard Dataverse). Zenodo is absent: it
      * holds papers as often as data.
      */
    private val DataDoiPrefixes = List("10.5061/", "10.6084/", "10.17632/", "10.7910/")

    private val BookProvenance = Set("calibre", Provenance.ExternalFile, "archive", "archive_org")

    def of(
        doi: Option[String],
        venue: Option[String],
        provenance: Option[String],
        tags: List[String],
        topics: List[String],
        hasCover: Boolean
    ): String =
      val words = (tags ++ topics).map(_.toLowerCase(java.util.Locale.ROOT))
      val where = venue.getOrElse("").toLowerCase(java.util.Locale.ROOT)
      val id = doi.map(_.trim.toLowerCase(java.util.Locale.ROOT)).filter(_.nonEmpty)
      def says(w: String*) = words.exists(t => w.exists(t.contains)) || w.exists(where.contains)
      if says("thesis", "dissertation") then Thesis
      else if words.exists(_.contains("dataset")) || id.exists(d => DataDoiPrefixes.exists(d.startsWith)) then Dataset
      else
        id.flatMap(isbnDoi) match
          case Some(chapter)        => if chapter then Chapter else Book
          case None if id.isDefined => Paper
          case None                 =>
            if hasCover || provenance.map(_.trim.toLowerCase(java.util.Locale.ROOT)).exists(BookProvenance.contains)
            then Book
            else Unknown

    def isbnDoi(doi: String): Option[Boolean] =
      val suffix = doi.dropWhile(_ != '/').drop(1).toLowerCase(java.util.Locale.ROOT).stripPrefix("cbo")
      if !(suffix.startsWith("978") || suffix.startsWith("979")) then None
      else
        @tailrec def isbnEnd(i: Int, digits: Int): Option[Int] =
          if digits >= 13 then Some(i)
          else if i < suffix.length && suffix(i).isDigit then isbnEnd(i + 1, digits + 1)
          else if i < suffix.length && suffix(i) == '-' then isbnEnd(i + 1, digits)
          else None
        isbnEnd(0, 0).map(end => suffix.drop(end).exists(_.isLetterOrDigit))

  final case class Match(
      rightUid: UUID,
      rightCitekey: String,
      leftUid: Option[UUID],
      leftCitekey: Option[String],
      method: MatchMethod,
      outcome: MatchOutcome,
      score: Option[Double] = None,
      why: List[String] = Nil,
      issue: Option[String] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema
  object Match:


    /** A source's match in a merge plan: the plan's Markdown lists every one but the new sources. */
    val Matches: com.histmersion.api.exchange.PlanMatchKind[Match] =
      com.histmersion.api.exchange.PlanMatchKind(_.outcome != MatchOutcome.New, line)

    /** A source's match as the plan's Markdown lists it. */
    def line(s: Match): String =
      val target = s.leftCitekey.fold("")(c => s" → `$c`")
      val score = s.score.fold("")(x => s" · score ${math.round(x * 10) / 10.0}")
      s"- `${s.rightCitekey}`$target · ${MatchOutcome.wireOf(s.outcome)}$score" + s.issue.fold("")(i => s" · $i")

  final case class Patch(
      metadata: Option[MetadataPatch] = None,
      summary: Option[String] = None,
      body: Option[String] = None,
      notes: Option[String] = None,
      topics: Option[List[String]] = None,
      regions: Option[List[String]] = None,
      period: Option[Period.Input] = None,
      tags: Option[ListPatch] = None,
      findings: Option[FindingsPatch] = None,
      edges: Option[EdgesPatch] = None,
      ifUpdatedAt: Option[String] = None
  ) derives KindlingsSchema, com.histmersion.api.json.Schema
  object Patch:


    given Codec.AsObject[Patch] = StrictJson.codec(KindlingsCodecAsObject.derived[Patch])

  final case class Put(
      title: String,
      authors: List[String] = Nil,
      doi: Option[String] = None,
      year: Option[Int] = None,
      venue: Option[String] = None,
      status: Option[com.histmersion.api.library.Source.Status] = None,
      topics: List[String] = Nil,
      regions: List[String] = Nil,
      tags: List[String] = Nil,
      periodStart: Option[Long] = None,
      periodEnd: Option[Long] = None,
      summary: Option[String] = None,
      body: Option[String] = None,
      notes: Option[String] = None,
      citedByCount: Option[Int] = None,
      influentialCitations: Option[Int] = None,
      provenance: Option[String] = None,
      calibreId: Option[String] = None,
      findings: List[Finding.Input] = Nil,
      edges: List[Edge.Input] = Nil
  ) derives KindlingsSchema, com.histmersion.api.json.Schema
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
      edges = s.edges.flatMap(e => com.histmersion.api.library.Edge.Kind.fromWire(e.kind).map(Edge.Input(e.dst, _)))
    )

  /** `idLabel` is the DOI when present, else the citekey: the owner's decision to show DOIs in the UI while keeping the
    * internal naming.
    */
  final case class Ref(
      citekey: String,
      doi: Option[String],
      authors: List[String],
      year: Option[Int],
      title: String,
      status: com.histmersion.api.library.Source.Status
  ):
    def idLabel: String = doi.getOrElse(citekey)

    def shortCite: String =
      val auth = Ref.shortAuthors(authors)
      val yr = year.map(_.toString).getOrElse("n.d.")
      if auth.isEmpty then yr else s"$auth $yr"

  object Ref:
    def surname(full: String): String =
      val t = full.trim
      if t.contains(",") then t.takeWhile(_ != ',').trim
      else t.split("\\s+").lastOption.getOrElse(t)

    def shortAuthors(authors: List[String]): String =
      val names = authors.map(_.trim).filter(_.nonEmpty)
      names match
        case Nil           => ""
        case a :: Nil      => surname(a)
        case a :: b :: Nil => surname(a) + " & " + surname(b)
        case a :: _        => surname(a) + " et al."

  final case class Renamed(
      oldCitekey: String,
      newCitekey: String,
      source: Detail,
      counts: Rename.Counts,
      /** Calibre never stores the citekey, but the host-side originals directory is keyed by it. */
      calibreId: Option[String] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema


  object Section:
    val Summary = "summary"
    val Findings = "findings"
    val Notes = "notes"
    val Toc = "toc"
    val Body = "body"
    val All = "all"
    val all: List[String] = List(Summary, Findings, Notes, Toc, Body, All)

    def parse(s: String): Option[String] =
      val lower = s.trim.toLowerCase
      all.find(_ == lower)

  final case class WithFindings(
      source: Detail,
      findings: List[Finding.Row],
      formatting: List[FormatIssue] = Nil,
      illustration: Option[MediaThumb] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema


  /** A source's put and patch checked and applied to the stored record: the findings numbered, the edges and the
    * metadata checked, an old citekey in a patch followed to the current one.
    */
  object Write:

    def findingId(citekey: String, n: Int): String = s"$citekey#$n"

    def applyPut(citekey: String, p: Put, aliases: Set[String] = Set.empty): Either[String, Source] =
      for
        period <- periodOf(p.periodStart, p.periodEnd, "source")
        fs <- numbered(citekey, p.findings)
        edges <- traverse(p.edges)(edgeOf(citekey, aliases, "edges", "source being put", _))
        _ <- edges
          .find(e => e.dst == citekey || aliases.contains(e.dst))
          .fold(Right(()))(e =>
            Left(s"edges: '${e.dst}' --${e.kind}--> itself is a self-edge; dst must be another source")
          )
      yield Source(
        citekey = citekey,
        title = p.title,
        authors = p.authors,
        doi = blankToNone(p.doi),
        year = p.year,
        venue = blankToNone(p.venue),
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

    def precondition(existing: Source, patch: Patch): Either[String, Unit] =
      patch.ifUpdatedAt match
        case None       => Right(())
        case Some(want) =>
          val actual = existing.updatedAt.getOrElse("")
          if want == actual then Right(())
          else
            Left(
              s"'${existing.citekey}' has moved on: you sent ifUpdatedAt='$want' but the stored " +
                s"updatedAt is '$actual'. Re-read the source and re-apply your patch."
            )

    def applyPatch(existing: Source, patch: Patch): Either[String, Source] =
      for
        _ <- precondition(existing, patch)
        period <- patch.period.fold[Either[String, Option[Period]]](Right(existing.period))(p =>
          periodOf(Some(p.start), Some(p.end), "source")
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
        existing.copy(
          title = meta.title.getOrElse(existing.title),
          authors = meta.authors.getOrElse(existing.authors),
          year = if cleared(MetadataField.Year) then None else meta.year.orElse(existing.year),
          venue = if cleared(MetadataField.Venue) then None
          else meta.venue.fold(existing.venue)(v => blankToNone(Some(v))),
          doi = if cleared(MetadataField.Doi) then None
          else meta.doi.fold(existing.doi)(d => blankToNone(Some(d))),
          status = newStatus,
          summary = patch.summary.orElse(existing.summary),
          ocrMarkdown = patch.body.orElse(existing.ocrMarkdown),
          notes = patch.notes.orElse(existing.notes),
          topics = patch.topics.getOrElse(existing.topics),
          regions = patch.regions.getOrElse(existing.regions),
          tags = patch.tags.fold(existing.tags)(applyList(existing.tags, _)),
          period = period,
          findings = fs,
          edges = newEdges,
          provenance = if cleared(MetadataField.Provenance) then None
          else meta.provenance.orElse(existing.provenance),
          calibreId = if cleared(MetadataField.CalibreId) then None
          else meta.calibreId.map(_.trim).orElse(existing.calibreId)
        )

    def srcAliased(p: Patch, requested: String, canonical: String): Patch =
      if requested == canonical then p
      else
        def fix(e: Edge.Input) = if e.src.map(_.trim).contains(requested) then e.copy(src = None) else e
        p.copy(edges = p.edges.map(ep => EdgesPatch(ep.add.map(fix), ep.remove.map(fix))))

    def applyEdges(citekey: String, current: List[Edge], p: EdgesPatch): Either[String, List[Edge]] =
      for
        removed <- traverse(p.remove)(edgeOf(citekey, Set.empty, "edges.remove", "patched source", _))
        added <- traverse(p.add)(edgeOf(citekey, Set.empty, "edges.add", "patched source", _))
        _ <- added
          .find(_.dst == citekey)
          .fold(Right(()))(e =>
            Left(s"edges.add: '${e.dst}' --${e.kind}--> itself is a self-edge; dst must be another source")
          )
      yield
        val gone = removed.toSet
        (current.map(_.copy(src = citekey)).filterNot(gone) ++ added.filterNot(gone)).distinct

    private[api] def edgeOf(
        citekey: String,
        aliases: Set[String],
        part: String,
        role: String,
        in: Edge.Input
    ): Either[String, Edge] =
      in.src.map(_.trim).filter(_.nonEmpty) match
        case Some(other) if other != citekey && !aliases.contains(other) =>
          Left(
            s"$part: src '$other' is not the $role '$citekey'. An edge is stored on its " +
              s"source: patch '$other' to record '$other' --${com.histmersion.api.library.Edge.Kind.wireOf(in.kind)}--> '${in.dst}' " +
              s"(src may be omitted; it defaults to the $role)"
          )
        case _ => Right(Edge(citekey, in.dst.trim, com.histmersion.api.library.Edge.Kind.wireOf(in.kind)))

    private[api] def metadataCheck(m: MetadataPatch): Either[String, Unit] =
      val both = m.clear.filter {
        case MetadataField.Year       => m.year.isDefined
        case MetadataField.Venue      => m.venue.isDefined
        case MetadataField.Doi        => m.doi.isDefined
        case MetadataField.Provenance => m.provenance.isDefined
        case MetadataField.CalibreId  => m.calibreId.isDefined
      }
      if both.nonEmpty then
        Left(s"metadata: ${both.map(MetadataField.wireOf).mkString(", ")} is both set and cleared in one patch")
      else
        m.calibreId.map(_.trim) match
          case Some(id) if !id.matches("[0-9]+") =>
            Left(s"metadata.calibreId: '$id' is not a Calibre id (the entry's number, e.g. 1805)")
          case _ => Right(())

    def applyFindings(citekey: String, current: List[Finding], p: FindingsPatch): Either[String, List[Finding]] =
      for
        base <- p.replace.fold[Either[String, List[Finding]]](Right(current))(numbered(citekey, _))
        kept = base.filterNot(f => p.remove.contains(f.id))
        added <- allocate(citekey, kept, p.add)
      yield kept ++ added

    private[api] def numbered(citekey: String, inputs: List[Finding.Input]): Either[String, List[Finding]] =
      traverse(inputs.zipWithIndex)((in, i) => toFinding(citekey, findingId(citekey, i), in))

    private[api] def allocate(
        citekey: String,
        existing: List[Finding],
        adds: List[Finding.Input]
    ): Either[String, List[Finding]] =
      @tailrec def loop(
          rest: List[Finding.Input],
          used: Set[String],
          acc: List[Either[String, Finding]]
      ): List[Either[String, Finding]] =
        rest match
          case Nil        => acc.reverse
          case in :: tail =>
            val id = Iterator.from(0).map(findingId(citekey, _)).find(n => !used.contains(n)).get
            loop(tail, used + id, toFinding(citekey, id, in) :: acc)
      traverse(loop(adds, existing.map(_.id).toSet, Nil))(Predef.identity)

    private[api] def toFinding(citekey: String, id: String, in: Finding.Input): Either[String, Finding] =
      val what = s"finding '${in.claim.take(40)}'"
      for
        p <- periodOf(in.periodStart, in.periodEnd, what)
        conf <- confidenceOf(in.confidence, what)
      yield
        val geo = (in.lon, in.lat) match
          case (Some(x), Some(y)) => Some(GeoPoint(x, y))
          case _                  => None
        Finding(id, citekey, in.claim, in.region, geo, p, in.periodName, in.tags, conf, in.significance)

    private[api] def confidenceOf(raw: Option[String], what: String): Either[String, Option[Confidence]] =
      raw.map(_.trim).filter(_.nonEmpty) match
        case None    => Right(None)
        case Some(c) =>
          Confidence
            .fromString(c)
            .map(Some(_))
            .toRight(s"$what: confidence '$c' is not one of ${Confidence.values.map(_.wire).mkString("|")}")

    /** Postgres rejects an inverted int8range. */
    private[api] def periodOf(start: Option[Long], end: Option[Long], what: String): Either[String, Option[Period]] =
      def sane(v: Long) = v >= Filter.Earliest && v <= Filter.Latest
      (start, end) match
        case (Some(a), _) if !sane(a)     => Left(outOfRange(what, "period_start", a))
        case (_, Some(b)) if !sane(b)     => Left(outOfRange(what, "period_end", b))
        case (Some(a), Some(b)) if a <= b => Right(Some(Period(a, b)))
        case (Some(a), Some(b))           => Left(s"$what: period_start ($a) must not be after period_end ($b)")
        case (None, None)                 => Right(None)
        case _                            => Left(s"$what: give both period_start and period_end, or neither")

    private[api] def outOfRange(what: String, field: String, v: Long): String =
      s"$what: $field ($v) is outside the representable range " +
        s"[${Filter.Earliest}, ${Filter.Latest}] — deep time is signed YEARS, BCE negative"

    private[api] def supersededEdgeCheck(
        status: com.histmersion.api.library.Source.Status,
        edges: List[Edge]
    ): Either[String, Unit] =
      if status == com.histmersion.api.library.Source.Status.Superseded && !edges.exists(_.kind == "superseded_by")
      then
        Left(
          "status=superseded requires a superseded_by edge — add one with edges.add " +
            "(e.g. {\"edges\":{\"add\":[{\"dst\":\"newer_source\",\"kind\":\"superseded_by\"}]}}, " +
            "or the CLI flag --add-edge superseded_by:newer_source)"
        )
      else Right(())

    private[api] def traverse[A, B](as: List[A])(f: A => Either[String, B]): Either[String, List[B]] =
      as.foldLeft[Either[String, List[B]]](Right(Nil))((acc, a) => acc.flatMap(bs => f(a).map(bs :+ _)))
