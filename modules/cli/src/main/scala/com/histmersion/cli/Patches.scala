package com.histmersion
package cli

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
import com.histmersion.api.library.{Citekey, Title}
import com.histmersion.api.catalogue.Year
import com.histmersion.api.Write
import io.github.iltotore.iron.constraint.any.Pure

/** Assemble a [[SourcePatch]] from `histmersion patch` flags.
  *
  * `--json` carries a whole patch for the parts that have no flag (edges, per-finding replace, …); the individual flags
  * then override the parts they name. A part nobody mentions stays absent, which is what tells the server to leave it
  * alone.
  */
object Patches:
  type BodyFile = BodyFile.T
  object BodyFile extends Newtype[String, Pure]
  type NotesFile = NotesFile.T
  object NotesFile extends Newtype[String, Pure]

  def build(
      json: Option[String],
      tagsAdd: Option[String],
      tagsRemove: Option[String],
      tagsReplace: Option[String],
      topics: Option[String],
      regions: Option[String],
      summary: Option[Source.Summary],
      status: Option[Source.Status],
      title: Option[Title],
      notesFile: Option[NotesFile],
      period: Option[String],
      bodyFile: Option[BodyFile],
      addFindings: List[String],
      removeFindings: List[String],
      ifUpdatedAt: Option[Source.UpdatedAt],
      addEdges: List[String] = Nil,
      removeEdges: List[String] = Nil
  ): Either[Cli.Error, Source.Patch] =
    for
      base <- json.fold[Either[Cli.Error, Source.Patch]](
        Right(Source.Patch())
      )(parse[Source.Patch]("--json", _))
      adds <- traverse(addFindings)(parse[Finding.Input]("--add-finding", _))
      eAdd <- traverse(addEdges)(parseEdge("--add-edge", _))
      eRem <- traverse(removeEdges)(parseEdge("--remove-edge", _))
      p <- period.fold[Either[Cli.Error, Option[Period.Input]]](Right(base.period))(parsePeriod(_).map(Some(_)))
      notes <- notesFile.fold[Either[Cli.Error, Option[Source.Notes]]](Right(base.notes))(f =>
        readFile("--notes-file", f.value).map(t => Some(Source.Notes(t)))
      )
      body <- bodyFile.fold[Either[Cli.Error, Option[Source.OcrMarkdown]]](Right(base.body))(f =>
        readFile("--body-file", f.value).map(t => Some(Source.OcrMarkdown(t)))
      )
      meta = mergeMetadata(base.metadata, title, status)
      tags = mergeTags(base.tags, tagsAdd, tagsRemove, tagsReplace)
      finds = mergeFindings(base.findings, adds, removeFindings)
      edges = mergeEdges(base.edges, eAdd, eRem)
    yield base
      .modify(_.metadata)
      .setTo(meta)
      .modify(_.summary)
      .setTo(summary.orElse(base.summary))
      .modify(_.notes)
      .setTo(notes)
      .modify(_.body)
      .setTo(body)
      .modify(_.topics)
      .setTo(topics.map(csv(_).map(Source.Topics(_))).orElse(base.topics))
      .modify(_.regions)
      .setTo(regions.map(csv(_).map(Source.Regions(_))).orElse(base.regions))
      .modify(_.period)
      .setTo(p)
      .modify(_.tags)
      .setTo(tags)
      .modify(_.findings)
      .setTo(finds)
      .modify(_.edges)
      .setTo(edges)
      .modify(_.ifUpdatedAt)
      .setTo(ifUpdatedAt.orElse(base.ifUpdatedAt))

  // ── part merges: a flag wins over the same part of --json, absent parts stay absent ────────────
  private def mergeMetadata(base: Option[MetadataPatch], title: Option[Title], status: Option[Source.Status]) =
    if title.isEmpty && status.isEmpty then base
    else
      val m = base.getOrElse(MetadataPatch())
      Some(m.modify(_.title).setTo(title.orElse(m.title)).modify(_.status).setTo(status.orElse(m.status)))

  private def mergeTags(
      base: Option[ListPatch],
      add: Option[String],
      remove: Option[String],
      replace: Option[String]
  ) =
    if add.isEmpty && remove.isEmpty && replace.isEmpty then base
    else
      val l = base.getOrElse(ListPatch())
      Some(
        ListPatch(
          add = add.fold(l.add)(csvItems),
          remove = remove.fold(l.remove)(csvItems),
          replace = replace.map(csvItems).orElse(l.replace)
        )
      )

  private def mergeFindings(base: Option[FindingsPatch], add: List[Finding.Input], remove: List[String]) =
    if add.isEmpty && remove.isEmpty then base
    else
      val f = base.getOrElse(FindingsPatch())
      Some(f.modify(_.add).setTo(f.add ++ add).modify(_.remove).setTo(f.remove ++ remove.map(FindingsPatch.Remove(_))))

  private def mergeEdges(
      base: Option[EdgesPatch],
      add: List[Edge.Input],
      remove: List[Edge.Input]
  ) =
    if add.isEmpty && remove.isEmpty then base
    else
      val e = base.getOrElse(EdgesPatch())
      Some(e.modify(_.add).setTo(e.add ++ add).modify(_.remove).setTo(e.remove ++ remove))

  // ── parsing ──────────────────────────────────────────────────────────────────────────────────
  private def csv(s: String): List[String] = s.split(",").map(_.trim).filter(_.nonEmpty).toList

  private def csvItems(s: String): List[Write.ListItem] = csv(s).map(Write.ListItem(_))

  private def parse[A: io.circe.Decoder](flag: String, s: String): Either[Cli.Error, A] =
    decode[A](s).left.map(e =>
      Cli.Error(
        Problem(s"$flag: not valid JSON for this option (${e.getMessage})"),
        exitCode = ExitCode.Usage
      )
    )

  /** `--add-edge refutes:heggarty2023_language` — an edge FROM the patched source, so only the kind and the target are
    * given.
    */
  private[cli] def parseEdge(flag: String, s: String): Either[Cli.Error, Edge.Input] =
    s.split(":", 2).map(_.trim) match
      case Array(k, dst) if dst.nonEmpty =>
        Edge.Kind
          .fromWire(Edge.Kind.Wire(k))
          .map(Edge.Input(Citekey(dst), _))
          .toRight(
            Cli.Error(
              Problem(s"$flag: unknown edge kind '$k' (one of: ${Edge.Kind.allWire.mkString(", ")})"),
              exitCode = ExitCode.Usage
            )
          )
      case _ =>
        Left(
          Cli.Error(
            Problem(s"$flag: expected '<kind>:<citekey>', e.g. 'challenges:lord1968_oralpoet', got '$s'"),
            exitCode = ExitCode.Usage
          )
        )

  private def parsePeriod(s: String): Either[Cli.Error, Period.Input] =
    csv(s).map(_.toLongOption) match
      case List(Some(a), Some(b)) => Right(Period.Input(Year(a), Year(b)))
      case _                      =>
        Left(
          Cli.Error(
            Problem(s"--period: expected 'start,end' as signed years, got '$s'"),
            exitCode = ExitCode.Usage
          )
        )

  /** A file a flag names, read whole — a large body (a book's OCR) cannot travel through `--json`. */
  private def readFile(flag: String, file: String): Either[Cli.Error, String] =
    val p = Path.of(file)
    if Files.isRegularFile(p) then Right(Files.readString(p))
    else Left(Cli.Error(Problem(s"$flag: no such file: $file"), exitCode = ExitCode.Usage))

  private def traverse[A, B](as: List[A])(f: A => Either[Cli.Error, B]): Either[Cli.Error, List[B]] =
    as.foldLeft[Either[Cli.Error, List[B]]](Right(Nil))((acc, a) => acc.flatMap(bs => f(a).map(bs :+ _)))
