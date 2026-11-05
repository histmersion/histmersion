package com.histmersion.cli

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.ListPatch
import com.histmersion.config.*

import io.circe.parser.decode
import java.nio.file.{Files, Path}
import com.histmersion.api.library.{EdgesPatch, FindingsPatch, MetadataPatch}
import com.histmersion.api.library.Edge
import com.histmersion.api.library.Source
import com.histmersion.api.catalogue.Period
import com.histmersion.api.library.Finding

/** Assemble a [[SourcePatch]] from `histmersion patch` flags.
  *
  * `--json` carries a whole patch for the parts that have no flag (edges, per-finding replace, …); the individual flags
  * then override the parts they name. A part nobody mentions stays absent, which is what tells the server to leave it
  * alone.
  */
object Patches:

  type BuildJson = BuildJson.T
  object BuildJson extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BuildTagsAdd = BuildTagsAdd.T
  object BuildTagsAdd extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BuildTagsRemove = BuildTagsRemove.T
  object BuildTagsRemove extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BuildTagsReplace = BuildTagsReplace.T
  object BuildTagsReplace extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BuildTopics = BuildTopics.T
  object BuildTopics extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BuildRegions = BuildRegions.T
  object BuildRegions extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BuildSummary = BuildSummary.T
  object BuildSummary extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BuildTitle = BuildTitle.T
  object BuildTitle extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BuildNotesFile = BuildNotesFile.T
  object BuildNotesFile extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BuildPeriod = BuildPeriod.T
  object BuildPeriod extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BuildBodyFile = BuildBodyFile.T
  object BuildBodyFile extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BuildAddFindings = BuildAddFindings.T
  object BuildAddFindings extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BuildRemoveFindings = BuildRemoveFindings.T
  object BuildRemoveFindings extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BuildIfUpdatedAt = BuildIfUpdatedAt.T
  object BuildIfUpdatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BuildAddEdges = BuildAddEdges.T
  object BuildAddEdges extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BuildRemoveEdges = BuildRemoveEdges.T
  object BuildRemoveEdges extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergeTagsAdd = MergeTagsAdd.T
  object MergeTagsAdd extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergeTagsRemove = MergeTagsRemove.T
  object MergeTagsRemove extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergeTagsReplace = MergeTagsReplace.T
  object MergeTagsReplace extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergeFindingsRemove = MergeFindingsRemove.T
  object MergeFindingsRemove extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CsvResult = CsvResult.T
  object CsvResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CsvS = CsvS.T
  object CsvS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseFlag = ParseFlag.T
  object ParseFlag extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseS = ParseS.T
  object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseEdgeFlag = ParseEdgeFlag.T
  object ParseEdgeFlag extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseEdgeS = ParseEdgeS.T
  object ParseEdgeS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParsePeriodS = ParsePeriodS.T
  object ParsePeriodS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadFileResultPart2 = ReadFileResultPart2.T
  object ReadFileResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadFileFlag = ReadFileFlag.T
  object ReadFileFlag extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadFileFile = ReadFileFile.T
  object ReadFileFile extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def build(
      json: Option[com.histmersion.cli.Patches.BuildJson],
      tagsAdd: Option[com.histmersion.cli.Patches.BuildTagsAdd],
      tagsRemove: Option[com.histmersion.cli.Patches.BuildTagsRemove],
      tagsReplace: Option[com.histmersion.cli.Patches.BuildTagsReplace],
      topics: Option[com.histmersion.cli.Patches.BuildTopics],
      regions: Option[com.histmersion.cli.Patches.BuildRegions],
      summary: Option[com.histmersion.cli.Patches.BuildSummary],
      status: Option[Source.Status],
      title: Option[com.histmersion.cli.Patches.BuildTitle],
      notesFile: Option[com.histmersion.cli.Patches.BuildNotesFile],
      period: Option[com.histmersion.cli.Patches.BuildPeriod],
      bodyFile: Option[com.histmersion.cli.Patches.BuildBodyFile],
      addFindings: List[com.histmersion.cli.Patches.BuildAddFindings],
      removeFindings: List[com.histmersion.cli.Patches.BuildRemoveFindings],
      ifUpdatedAt: Option[com.histmersion.cli.Patches.BuildIfUpdatedAt],
      addEdges: List[com.histmersion.cli.Patches.BuildAddEdges] = Nil,
      removeEdges: List[com.histmersion.cli.Patches.BuildRemoveEdges] = Nil
  ): Either[Cli.Error, com.histmersion.api.library.Source.Patch] =
    for
      base <- json.fold[Either[Cli.Error, com.histmersion.api.library.Source.Patch]](
        Right(com.histmersion.api.library.Source.Patch())
      )(parse[com.histmersion.api.library.Source.Patch]("--json", _))
      adds <- traverse(addFindings)(parse[Finding.Input]("--add-finding", _))
      eAdd <- traverse(addEdges)(parseEdge("--add-edge", _))
      eRem <- traverse(removeEdges)(parseEdge("--remove-edge", _))
      p <- period.fold[Either[Cli.Error, Option[Period.Input]]](Right(base.period))(parsePeriod(_).map(Some(_)))
      notes <- notesFile.fold[Either[Cli.Error, Option[String]]](Right(base.notes))(
        readFile("--notes-file", _).map(Some(_))
      )
      body <- bodyFile.fold[Either[Cli.Error, Option[String]]](Right(base.body))(
        readFile("--body-file", _).map(Some(_))
      )
      meta = mergeMetadata(base.metadata, title.map(com.histmersion.api.library.Title.apply), status)
      tags = mergeTags(base.tags, tagsAdd, tagsRemove, tagsReplace)
      finds = mergeFindings(base.findings, adds, removeFindings)
      edges = mergeEdges(base.edges, eAdd, eRem)
    yield base.modify(
      _.metadata).setTo(meta).modify(_.summary).setTo(summary.orElse(base.summary)).modify(_.notes).setTo(notes).modify(_.body).setTo(body).modify(_.topics).setTo(topics.map(csv).orElse(base.topics)).modify(_.regions).setTo(regions.map(csv).orElse(base.regions)).modify(_.period).setTo(p).modify(_.tags).setTo(tags).modify(_.findings).setTo(finds).modify(_.edges).setTo(edges).modify(_.ifUpdatedAt).setTo(ifUpdatedAt.orElse(base.ifUpdatedAt)
    )

  // ── part merges: a flag wins over the same part of --json, absent parts stay absent ────────────
  private def mergeMetadata(base: Option[MetadataPatch], title: Option[com.histmersion.api.library.Title], status: Option[Source.Status]) =
    if title.isEmpty && status.isEmpty then base
    else
      val m = base.getOrElse(MetadataPatch())
      Some(m.modify(_.title).setTo(title.orElse(m.title)).modify(_.status).setTo(status.orElse(m.status)))

  private def mergeTags(
      base: Option[ListPatch],
      add: Option[com.histmersion.cli.Patches.MergeTagsAdd],
      remove: Option[com.histmersion.cli.Patches.MergeTagsRemove],
      replace: Option[com.histmersion.cli.Patches.MergeTagsReplace]
  ) =
    if add.isEmpty && remove.isEmpty && replace.isEmpty then base
    else
      val l = base.getOrElse(ListPatch())
      Some(
        ListPatch(
          add = add.fold(l.add)(csv),
          remove = remove.fold(l.remove)(csv),
          replace = replace.map(csv).orElse(l.replace)
        )
      )

  private def mergeFindings(base: Option[FindingsPatch], add: List[Finding.Input], remove: List[com.histmersion.cli.Patches.MergeFindingsRemove]) =
    if add.isEmpty && remove.isEmpty then base
    else
      val f = base.getOrElse(FindingsPatch())
      Some(f.modify(_.add).setTo(f.add ++ add).modify(_.remove).setTo(f.remove ++ remove))

  private def mergeEdges(
      base: Option[EdgesPatch],
      add: List[com.histmersion.api.library.Edge.Input],
      remove: List[com.histmersion.api.library.Edge.Input]
  ) =
    if add.isEmpty && remove.isEmpty then base
    else
      val e = base.getOrElse(EdgesPatch())
      Some(e.modify(_.add).setTo(e.add ++ add).modify(_.remove).setTo(e.remove ++ remove))

  // ── parsing ──────────────────────────────────────────────────────────────────────────────────
  private def csv(s: com.histmersion.cli.Patches.CsvS): List[com.histmersion.cli.Patches.CsvResult] = s.split(",").map(_.trim).filter(_.nonEmpty).toList

  private def parse[A: io.circe.Decoder](flag: com.histmersion.cli.Patches.ParseFlag, s: com.histmersion.cli.Patches.ParseS): Either[Cli.Error, A] =
    decode[A](s).left.map(e =>
      Cli.Error(s"$flag: not valid JSON for this option (${e.getMessage})", exitCode = ExitCode.Usage)
    )

  /** `--add-edge refutes:heggarty2023_language` — an edge FROM the patched source, so only the kind and the target are
    * given.
    */
  private[cli] def parseEdge(flag: com.histmersion.cli.Patches.ParseEdgeFlag, s: com.histmersion.cli.Patches.ParseEdgeS): Either[Cli.Error, com.histmersion.api.library.Edge.Input] =
    s.split(":", 2).map(_.trim) match
      case Array(k, dst) if dst.nonEmpty =>
        Edge.Kind
          .fromWire(k)
          .map(com.histmersion.api.library.Edge.Input(dst, _))
          .toRight(
            Cli.Error(
              s"$flag: unknown edge kind '$k' (one of: ${Edge.Kind.allWire.mkString(", ")})",
              exitCode = ExitCode.Usage
            )
          )
      case _ =>
        Left(
          Cli.Error(
            s"$flag: expected '<kind>:<citekey>', e.g. 'challenges:lord1968_oralpoet', got '$s'",
            exitCode = ExitCode.Usage
          )
        )

  private def parsePeriod(s: com.histmersion.cli.Patches.ParsePeriodS): Either[Cli.Error, Period.Input] =
    csv(s).map(_.toLongOption) match
      case List(Some(a), Some(b)) => Right(Period.Input(a, b))
      case _ => Left(Cli.Error(s"--period: expected 'start,end' as signed years, got '$s'", exitCode = ExitCode.Usage))

  /** A file a flag names, read whole — a large body (a book's OCR) cannot travel through `--json`. */
  private def readFile(flag: com.histmersion.cli.Patches.ReadFileFlag, file: com.histmersion.cli.Patches.ReadFileFile): Either[Cli.Error, com.histmersion.cli.Patches.ReadFileResultPart2] =
    val p = Path.of(file)
    if Files.isRegularFile(p) then Right(Files.readString(p))
    else Left(Cli.Error(s"$flag: no such file: $file", exitCode = ExitCode.Usage))

  private def traverse[A, B](as: List[A])(f: A => Either[Cli.Error, B]): Either[Cli.Error, List[B]] =
    as.foldLeft[Either[Cli.Error, List[B]]](Right(Nil))((acc, a) => acc.flatMap(bs => f(a).map(bs :+ _)))
