package com.histmersion.cli

import com.histmersion.domain.media.covers.CoverChoice
import com.histmersion.config.*

import java.nio.file.{Files, Path}
import com.histmersion.domain.media.MediaRole
import com.histmersion.api.media.{MediaCount, MediaCoversReport, MediaProvider, MediaSourceLine, SourceCoverParams}

/** The host-side half of a source's cover (#1446): the cover the local Calibre library keeps beside a linked book is
  * READ here (the library is on the user's machine, like every `calibre` command) and uploaded to the server as the
  * source's cover. Nothing is written to the library. When Calibre has no cover file but knows the book's ISBN, the
  * server is asked to fetch Open Library's by it.
  */
object MediaCovers:
  type LinkedFilter = LinkedFilter.T
  object LinkedFilter extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Message = Message.T
  object Message extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Reason = Reason.T
  object Reason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Refresh = Refresh.T
  object Refresh extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Pure: a citekey as a SQL literal for the read-only query (the only user value in it). */
  type LinkedOnly = _root_.com.histmersion.cli.MediaCovers.LinkedFilter
  val LinkedOnly: _root_.com.histmersion.cli.MediaCovers.LinkedFilter.type = _root_.com.histmersion.cli.MediaCovers.LinkedFilter
  type LinkedRefresh = _root_.com.histmersion.cli.MediaCovers.Refresh
  val LinkedRefresh: _root_.com.histmersion.cli.MediaCovers.Refresh.type = _root_.com.histmersion.cli.MediaCovers.Refresh
  type CalibreStepOnly = _root_.com.histmersion.cli.MediaCovers.LinkedFilter
  val CalibreStepOnly: _root_.com.histmersion.cli.MediaCovers.LinkedFilter.type = _root_.com.histmersion.cli.MediaCovers.LinkedFilter
  type CalibreStepRefresh = _root_.com.histmersion.cli.MediaCovers.Refresh
  val CalibreStepRefresh: _root_.com.histmersion.cli.MediaCovers.Refresh.type = _root_.com.histmersion.cli.MediaCovers.Refresh
  type CalibreStepDryRun = _root_.com.histmersion.cli.Cli.DryRun
  val CalibreStepDryRun: _root_.com.histmersion.cli.Cli.DryRun.type = _root_.com.histmersion.cli.Cli.DryRun

  private def lit(s: String): String = "'" + s.replace("'", "''") + "'"

  /** Pure: the read-only query for the Calibre-linked sources to look at — those with no cover yet (every one with
    * `refresh`), or just `only`.
    */
  def linkedQuery(only: Option[com.histmersion.cli.MediaCovers.LinkedFilter], refresh: com.histmersion.cli.MediaCovers.Refresh): com.histmersion.domain.PassageFinder.Query.Statement =
    s"""SELECT s.citekey, s.calibre_id FROM sources s
        WHERE s.calibre_id ~ '^[0-9]+$$'
          ${only.fold("")(c => s"AND s.citekey = ${lit(c)}")}
          ${
        if refresh then ""
        else
          "AND NOT EXISTS (SELECT 1 FROM media_links m WHERE m.target_type = 'source' AND m.target_id = s.citekey AND m.role = 'cover')"
      }
        ORDER BY s.citekey"""

  /** The Calibre-linked sources to look at, as (citekey, Calibre id). */
  def linked(c: Client, only: Option[com.histmersion.cli.MediaCovers.LinkedOnly], refresh: com.histmersion.cli.MediaCovers.LinkedRefresh): Either[Cli.Error, List[(com.histmersion.plugin.FetchedFile.Source.Citekey, com.histmersion.cli.Calibre.Book.Id)]] =
    c.query(linkedQuery(only, refresh))
      .map(_.rows.flatMap { r =>
        for ck <- r.headOption.flatten; id <- r.lift(1).flatten.flatMap(_.trim.toIntOption) yield (ck, id)
      })

  /** One source's cover from its Calibre entry: uploaded (unless `dryRun`), or the ISBN Open Library should be asked
    * by, or why there is none.
    */
  enum Outcome:
    case Uploaded(line: MediaSourceLine)
    case AskOpenLibrary(citekey: Outcome.AskOpenLibrary.Citekey, isbn: Outcome.AskOpenLibrary.Isbn)
    case Skipped(reason: Outcome.Skipped.Reason)
    case Failed(message: Outcome.Failed.Message)

  object Outcome:
    object AskOpenLibrary:
      type Citekey = com.histmersion.plugin.FetchedFile.Source.Citekey
      val Citekey: _root_.com.histmersion.plugin.FetchedFile.Source.Citekey.type = _root_.com.histmersion.plugin.FetchedFile.Source.Citekey
      type Isbn = com.histmersion.plugin.FetchedFile.Source.Isbn
      val Isbn: _root_.com.histmersion.plugin.FetchedFile.Source.Isbn.type = _root_.com.histmersion.plugin.FetchedFile.Source.Isbn
    object Skipped:
      type Reason = com.histmersion.cli.MediaCovers.Reason
      val Reason: _root_.com.histmersion.cli.MediaCovers.Reason.type = _root_.com.histmersion.cli.MediaCovers.Reason
    object Failed:
      type Message = com.histmersion.cli.MediaCovers.Problem
      val Message: _root_.com.histmersion.cli.MediaCovers.Problem.type = _root_.com.histmersion.cli.MediaCovers.Problem

  def one(c: Client, ck: com.histmersion.plugin.FetchedFile.Source.Citekey, book: Calibre.Book, dryRun: com.histmersion.cli.Cli.DryRun): Outcome =
    CoverChoice.choose(
      book.cover,
      p => Files.isRegularFile(Path.of(p)),
      book.isbn.toList,
      book.identifiers.get("oclc")
    ) match
      case Some(CoverChoice.Pick.Calibre(path)) =>
        val line = MediaSourceLine(ck, MediaRole.Cover, MediaProvider.Calibre, s"Calibre id ${book.id}")
        if dryRun then Outcome.Uploaded(line)
        else
          scala.util.Try(Files.readAllBytes(Path.of(path))).toEither match
            case Left(e)      => Outcome.Failed(s"$ck: cannot read $path (${e.getMessage})")
            case Right(bytes) =>
              c.putSourceCover(SourceCoverParams(ck, MediaProvider.Calibre, book.id.toString, None), bytes) match
                case Right(_) => Outcome.Uploaded(line)
                case Left(e)  => Outcome.Failed(s"$ck: ${e.message}")
      case Some(CoverChoice.Pick.OpenLibrary("isbn", isbn)) => Outcome.AskOpenLibrary(ck, isbn)
      case Some(CoverChoice.Pick.OpenLibrary(_, _))         => Outcome.Skipped("no cover file in Calibre")
      case None                                             => Outcome.Skipped("no cover file in Calibre, no ISBN")

  /** The Calibre step over the linked sources: what was uploaded, what Open Library is to be asked for (by the server),
    * and the counts.
    */
  def calibreStep(
      c: Client,
      cal: Calibre,
      only: Option[com.histmersion.cli.MediaCovers.CalibreStepOnly],
      refresh: com.histmersion.cli.MediaCovers.CalibreStepRefresh,
      dryRun: com.histmersion.cli.MediaCovers.CalibreStepDryRun,
      limit: Option[com.histmersion.api.Paging.Limit]
  ): Either[Cli.Error, (MediaCoversReport, List[(com.histmersion.cli.MediaCovers.Problem, com.histmersion.cli.MediaCovers.Message)])] =
    if !cal.libraryExists then
      Right((MediaCoversReport(dryRun, skipped = List(MediaCount("no Calibre library here", 1))), Nil))
    else
      for
        links <- linked(c, only, refresh)
        books <-
          if links.isEmpty then Right(Nil) else cal.all().left.map(e => Cli.Error(e, exitCode = ExitCode.Refused))
      yield
        val byId = books.map(b => b.id -> b).toMap
        val outcomes = links.take(limit.filter(_ > 0).getOrElse(Int.MaxValue)).map { (ck, id) =>
          byId.get(id).fold(Outcome.Skipped(s"Calibre id not in the library"))(one(c, ck, _, dryRun))
        }
        val lines = outcomes.collect { case Outcome.Uploaded(l) => l }
        val asks = outcomes.collect { case Outcome.AskOpenLibrary(ck, i) => (ck, i) }
        val report = MediaCoversReport(
          dryRun,
          links.size min limit.filter(_ > 0).getOrElse(Int.MaxValue),
          covers = lines.size,
          lines = lines,
          skipped = MediaCount.of(outcomes.collect { case Outcome.Skipped(r) => s"cover: $r" }),
          failed = outcomes.collect { case Outcome.Failed(m) => m }
        )
        (report, asks)
