package com.histmersion
package cli

import com.histmersion.api.Problem
import com.histmersion.domain.media.covers.{CoverChoice, OpenLibrary}
import com.histmersion.config.*

import java.nio.file.{Files, Path}
import com.histmersion.domain.media.MediaRole
import com.histmersion.api.Api
import com.histmersion.api.library.{Citekey, Query, RepairAcquire}
import com.histmersion.api.media.{
  MediaCount,
  MediaCoversReport,
  MediaCoversRequest,
  MediaImportRequest,
  MediaItem,
  MediaProvider,
  MediaSourceLine,
  SourceCoverParams
}
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.library.Isbn
import com.histmersion.api.Paging

/** The host-side half of a source's cover (#1446): the cover the local Calibre library keeps beside a linked book is
  * READ here (the library is on the user's machine, like every `calibre` command) and uploaded to the server as the
  * source's cover. Nothing is written to the library. When Calibre has no cover file but knows the book's ISBN, the
  * server is asked to fetch Open Library's by it.
  */
object MediaCovers:

  /** Pure: a citekey as a SQL literal for the read-only query (the only user value in it). */
  private def lit(s: String): String = "'" + s.replace("'", "''") + "'"

  /** Pure: the read-only query for the Calibre-linked sources to look at — those with no cover yet (every one with
    * `refresh`), or just `only`.
    */
  def linkedQuery(only: Option[Citekey], refresh: MediaImportRequest.Refresh): Query.Statement =
    Query.Statement(s"""SELECT s.citekey, s.calibre_id FROM sources s
        WHERE s.calibre_id ~ '^[0-9]+$$'
          ${only.fold("")(c => s"AND s.citekey = ${lit(c.value)}")}
          ${
        if refresh.value then ""
        else
          "AND NOT EXISTS (SELECT 1 FROM media_links m WHERE m.target_type = 'source' AND m.target_id = s.citekey AND m.role = 'cover')"
      }
        ORDER BY s.citekey""")

  /** The Calibre-linked sources to look at, as (citekey, Calibre id). */
  def linked(
      c: Client,
      only: Option[Citekey],
      refresh: MediaImportRequest.Refresh
  ): Either[Cli.Error, List[(Citekey, Calibre.Book.Id)]] =
    c.query(linkedQuery(only, refresh))
      .map(_.rows.flatMap { r =>
        for
          ck <- r.headOption.flatten
          id <- r.lift(1).flatten.flatMap(_.value.trim.toIntOption)
        yield (Citekey(ck.value), Calibre.Book.Id(id))
      })

  /** One source's cover from its Calibre entry: uploaded (unless `dryRun`), or the ISBN Open Library should be asked
    * by, or why there is none.
    */
  enum Outcome derives CanEqual:
    case Uploaded(line: MediaSourceLine)
    case AskOpenLibrary(citekey: Citekey, isbn: MediaCoversRequest.Isbn)
    case Skipped(reason: Problem)
    case Failed(message: MediaCoversReport.Failed)


  def one(c: Client, ck: Citekey, book: Calibre.Book, dryRun: RepairAcquire.RepairRequest.DryRun): Outcome =
    CoverChoice.choose(
      book.cover.map(p => CoverChoice.Pick.Calibre.Path(p.value)),
      p => Files.isRegularFile(Path.of(p.value)),
      book.isbn.toList.map(i => Isbn(i.value)),
      book.identifiers.get(Calibre.Book.IdentifiersKey("oclc")).map(v => OpenLibrary.Oclc(v.value))
    ) match
      case Some(CoverChoice.Pick.Calibre(path)) =>
        val line = MediaSourceLine(
          ck,
          MediaSourceLine.Role(MediaRole.Cover),
          MediaItem.Provider(MediaProvider.Calibre),
          MediaSourceLine.Detail(s"Calibre id ${book.id}")
        )
        if dryRun.value then Outcome.Uploaded(line)
        else
          scala.util.Try(Files.readAllBytes(Path.of(path.value))).toEither match
            case Left(e) =>
              Outcome.Failed(MediaCoversReport.Failed(s"$ck: cannot read ${path.value} (${e.getMessage})"))
            case Right(bytes) =>
              c.putSourceCover(
                SourceCoverParams(
                  ck,
                  MediaItem.Provider(MediaProvider.Calibre),
                  SourceCoverParams.Key(book.id.toString),
                  None
                ),
                Api.FileBytes(bytes)
              ) match
                case Right(_) => Outcome.Uploaded(line)
                case Left(e)  => Outcome.Failed(MediaCoversReport.Failed(s"$ck: ${e.message}"))
      case Some(CoverChoice.Pick.OpenLibrary(kind, isbn)) if kind.value == "isbn" =>
        Outcome.AskOpenLibrary(ck, MediaCoversRequest.Isbn(isbn.value))
      case Some(CoverChoice.Pick.OpenLibrary(_, _)) =>
        Outcome.Skipped(Problem("no cover file in Calibre"))
      case None => Outcome.Skipped(Problem("no cover file in Calibre, no ISBN"))

  /** The Calibre step over the linked sources: what was uploaded, what Open Library is to be asked for (by the server),
    * and the counts.
    */
  def calibreStep(
      c: Client,
      cal: Calibre,
      only: Option[Citekey],
      refresh: MediaImportRequest.Refresh,
      dryRun: RepairAcquire.RepairRequest.DryRun,
      limit: Option[Paging.Limit]
  ): Either[Cli.Error, (MediaCoversReport, List[(Citekey, MediaCoversRequest.Isbn)])] =
    if !cal.libraryExists then
      Right(
        (
          MediaCoversReport(
            dryRun,
            skipped = List(MediaCount(MediaCount.Key("no Calibre library here"), MediaCount.N(1)))
          ),
          Nil
        )
      )
    else
      for
        links <- linked(c, only, refresh)
        books <-
          if links.isEmpty then Right(Nil)
          else cal.all().left.map(e => Cli.Error(Problem(e.value), exitCode = ExitCode.Refused))
      yield
        val byId = books.map(b => b.id -> b).toMap
        val cap = limit.map(_.value).filter(_ > 0).getOrElse(Int.MaxValue)
        val outcomes = links.take(cap).map { (ck, id) =>
          byId
            .get(id)
            .fold[Outcome](Outcome.Skipped(Problem("Calibre id not in the library")))(
              one(c, ck, _, dryRun)
            )
        }
        val lines = outcomes.collect { case Outcome.Uploaded(l) => l }
        val asks = outcomes.collect { case Outcome.AskOpenLibrary(ck, i) => (ck, i) }
        val report = MediaCoversReport(
          dryRun,
          MediaCoversReport.Sources(links.size min cap),
          covers = MediaCoversReport.Covers(lines.size),
          lines = lines,
          skipped = MediaCount.of(outcomes.collect { case Outcome.Skipped(r) => MediaCount.Key(s"cover: $r") }),
          failed = outcomes.collect { case Outcome.Failed(m) => m }
        )
        (report, asks)
