package com.histmersion.cli

import com.histmersion.core.*

import io.circe.parser.decode
import java.nio.file.{Files, Path}

/** Assemble a [[SourcePatch]] from `histmersion patch` flags.
  *
  * `--json` carries a whole patch for the parts that have no flag (edges, per-finding replace, …);
  * the individual flags then override the parts they name. A part nobody mentions stays absent, which
  * is what tells the server to leave it alone. */
object Patches:

  def build(
      json: Option[String],
      tagsAdd: Option[String],
      tagsRemove: Option[String],
      tagsReplace: Option[String],
      topics: Option[String],
      regions: Option[String],
      summary: Option[String],
      status: Option[SourceStatus],
      title: Option[String],
      notesFile: Option[String],
      period: Option[String],
      bodyFile: Option[String],
      addFindings: List[String],
      removeFindings: List[String],
      ifUpdatedAt: Option[String]
  ): Either[CliError, SourcePatch] =
    for
      base   <- json.fold[Either[CliError, SourcePatch]](Right(SourcePatch()))(parse[SourcePatch]("--json", _))
      adds   <- traverse(addFindings)(parse[FindingInput]("--add-finding", _))
      p      <- period.fold[Either[CliError, Option[PeriodInput]]](Right(base.period))(parsePeriod(_).map(Some(_)))
      notes  <- notesFile.fold[Either[CliError, Option[String]]](Right(base.notes))(readFile("--notes-file", _).map(Some(_)))
      body   <- bodyFile.fold[Either[CliError, Option[String]]](Right(base.body))(readFile("--body-file", _).map(Some(_)))
      meta    = mergeMetadata(base.metadata, title, status)
      tags    = mergeTags(base.tags, tagsAdd, tagsRemove, tagsReplace)
      finds   = mergeFindings(base.findings, adds, removeFindings)
    yield base.copy(
      metadata = meta,
      summary = summary.orElse(base.summary),
      notes = notes,
      body = body,
      topics = topics.map(csv).orElse(base.topics),
      regions = regions.map(csv).orElse(base.regions),
      period = p,
      tags = tags,
      findings = finds,
      ifUpdatedAt = ifUpdatedAt.orElse(base.ifUpdatedAt)
    )

  // ── part merges: a flag wins over the same part of --json, absent parts stay absent ────────────
  private def mergeMetadata(base: Option[MetadataPatch], title: Option[String], status: Option[SourceStatus]) =
    if title.isEmpty && status.isEmpty then base
    else
      val m = base.getOrElse(MetadataPatch())
      Some(m.copy(title = title.orElse(m.title), status = status.orElse(m.status)))

  private def mergeTags(
      base: Option[ListPatch],
      add: Option[String],
      remove: Option[String],
      replace: Option[String]
  ) =
    if add.isEmpty && remove.isEmpty && replace.isEmpty then base
    else
      val l = base.getOrElse(ListPatch())
      Some(ListPatch(
        add = add.fold(l.add)(csv),
        remove = remove.fold(l.remove)(csv),
        replace = replace.map(csv).orElse(l.replace)))

  private def mergeFindings(base: Option[FindingsPatch], add: List[FindingInput], remove: List[String]) =
    if add.isEmpty && remove.isEmpty then base
    else
      val f = base.getOrElse(FindingsPatch())
      Some(f.copy(add = f.add ++ add, remove = f.remove ++ remove))

  // ── parsing ──────────────────────────────────────────────────────────────────────────────────
  private def csv(s: String): List[String] = s.split(",").map(_.trim).filter(_.nonEmpty).toList

  private def parse[A: io.circe.Decoder](flag: String, s: String): Either[CliError, A] =
    decode[A](s).left.map(e => CliError(s"$flag: not valid JSON for this option (${e.getMessage})", exitCode = 1))

  private def parsePeriod(s: String): Either[CliError, PeriodInput] =
    csv(s).map(_.toLongOption) match
      case List(Some(a), Some(b)) => Right(PeriodInput(a, b))
      case _ => Left(CliError(s"--period: expected 'start,end' as signed years, got '$s'", exitCode = 1))

  /** A file a flag names, read whole — a large body (a book's OCR) cannot travel through `--json`. */
  private def readFile(flag: String, file: String): Either[CliError, String] =
    val p = Path.of(file)
    if Files.isRegularFile(p) then Right(Files.readString(p))
    else Left(CliError(s"$flag: no such file: $file", exitCode = 1))

  private def traverse[A, B](as: List[A])(f: A => Either[CliError, B]): Either[CliError, List[B]] =
    as.foldLeft[Either[CliError, List[B]]](Right(Nil))((acc, a) => acc.flatMap(bs => f(a).map(bs :+ _)))
