package com.histmersion.api

import scala.annotation.tailrec
import hearth.kindlings.circederivation.Configuration
import com.histmersion.values.{Confidence, EdgeKind, SourceStatus}

/** Honour Scala default values when decoding: without this Kindlings requires every non-Option field in the JSON, so
  * `{"tags":{"add":["sea-peoples"]}}` would be refused for a missing `remove`. The domain and config packages import it
  * (`import com.histmersion.api.given`) for the codecs they derive.
  */
given Configuration = Configuration.default.withDefaults

object Write:

  private[api] def blankToNone(v: Option[String]): Option[String] = v.map(_.trim).filter(_.nonEmpty)

  val CodeNotFound = "not_found"
  val CodePrecondition = "precondition_failed"
  val CodeInvalid = "invalid"

  def notFound(citekey: String): ApiError = ApiError(s"No source '$citekey'", Some(CodeNotFound))

  def findingId(citekey: String, n: Int): String = s"$citekey#$n"

  def applyPut(citekey: String, p: SourcePut, aliases: Set[String] = Set.empty): Either[String, Source] =
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
      status = p.status.getOrElse(SourceStatus.Unassessed),
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

  def precondition(existing: Source, patch: SourcePatch): Either[String, Unit] =
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

  def applyPatch(existing: Source, patch: SourcePatch): Either[String, Source] =
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

  def srcAliased(p: SourcePatch, requested: String, canonical: String): SourcePatch =
    if requested == canonical then p
    else
      def fix(e: EdgeInput) = if e.src.map(_.trim).contains(requested) then e.copy(src = None) else e
      p.copy(edges = p.edges.map(ep => EdgesPatch(ep.add.map(fix), ep.remove.map(fix))))

  def applyList(current: List[String], p: ListPatch): List[String] =
    val base = p.replace.getOrElse(current)
    val removed = p.remove.toSet
    (base.filterNot(removed) ++ p.add.filterNot(removed)).distinct

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

  private def edgeOf(
      citekey: String,
      aliases: Set[String],
      part: String,
      role: String,
      in: EdgeInput
  ): Either[String, Edge] =
    in.src.map(_.trim).filter(_.nonEmpty) match
      case Some(other) if other != citekey && !aliases.contains(other) =>
        Left(
          s"$part: src '$other' is not the $role '$citekey'. An edge is stored on its " +
            s"source: patch '$other' to record '$other' --${EdgeKind.wireOf(in.kind)}--> '${in.dst}' " +
            s"(src may be omitted; it defaults to the $role)"
        )
      case _ => Right(Edge(citekey, in.dst.trim, EdgeKind.wireOf(in.kind)))

  private def metadataCheck(m: MetadataPatch): Either[String, Unit] =
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

  private def numbered(citekey: String, inputs: List[FindingInput]): Either[String, List[Finding]] =
    traverse(inputs.zipWithIndex)((in, i) => toFinding(citekey, findingId(citekey, i), in))

  private def allocate(
      citekey: String,
      existing: List[Finding],
      adds: List[FindingInput]
  ): Either[String, List[Finding]] =
    @tailrec def loop(
        rest: List[FindingInput],
        used: Set[String],
        acc: List[Either[String, Finding]]
    ): List[Either[String, Finding]] =
      rest match
        case Nil        => acc.reverse
        case in :: tail =>
          val id = Iterator.from(0).map(findingId(citekey, _)).find(n => !used.contains(n)).get
          loop(tail, used + id, toFinding(citekey, id, in) :: acc)
    traverse(loop(adds, existing.map(_.id).toSet, Nil))(identity)

  private def toFinding(citekey: String, id: String, in: FindingInput): Either[String, Finding] =
    val what = s"finding '${in.claim.take(40)}'"
    for
      p <- periodOf(in.periodStart, in.periodEnd, what)
      conf <- confidenceOf(in.confidence, what)
    yield
      val geo = (in.lon, in.lat) match
        case (Some(x), Some(y)) => Some(GeoPoint(x, y))
        case _                  => None
      Finding(id, citekey, in.claim, in.region, geo, p, in.periodName, in.tags, conf, in.significance)

  private def confidenceOf(raw: Option[String], what: String): Either[String, Option[Confidence]] =
    raw.map(_.trim).filter(_.nonEmpty) match
      case None    => Right(None)
      case Some(c) =>
        Confidence
          .fromString(c)
          .map(Some(_))
          .toRight(s"$what: confidence '$c' is not one of ${Confidence.values.map(_.wire).mkString("|")}")

  /** Postgres rejects an inverted int8range. */
  private def periodOf(start: Option[Long], end: Option[Long], what: String): Either[String, Option[Period]] =
    def sane(v: Long) = v >= Filter.Earliest && v <= Filter.Latest
    (start, end) match
      case (Some(a), _) if !sane(a)     => Left(outOfRange(what, "period_start", a))
      case (_, Some(b)) if !sane(b)     => Left(outOfRange(what, "period_end", b))
      case (Some(a), Some(b)) if a <= b => Right(Some(Period(a, b)))
      case (Some(a), Some(b))           => Left(s"$what: period_start ($a) must not be after period_end ($b)")
      case (None, None)                 => Right(None)
      case _                            => Left(s"$what: give both period_start and period_end, or neither")

  private def outOfRange(what: String, field: String, v: Long): String =
    s"$what: $field ($v) is outside the representable range " +
      s"[${Filter.Earliest}, ${Filter.Latest}] — deep time is signed YEARS, BCE negative"

  private def supersededEdgeCheck(status: SourceStatus, edges: List[Edge]): Either[String, Unit] =
    if status == SourceStatus.Superseded && !edges.exists(_.kind == "superseded_by") then
      Left(
        "status=superseded requires a superseded_by edge — add one with edges.add " +
          "(e.g. {\"edges\":{\"add\":[{\"dst\":\"newer_source\",\"kind\":\"superseded_by\"}]}}, " +
          "or the CLI flag --add-edge superseded_by:newer_source)"
      )
    else Right(())

  private def traverse[A, B](as: List[A])(f: A => Either[String, B]): Either[String, List[B]] =
    as.foldLeft[Either[String, List[B]]](Right(Nil))((acc, a) => acc.flatMap(bs => f(a).map(bs :+ _)))
