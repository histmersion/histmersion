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

  /** Pure: a citekey as a SQL literal for the read-only query (the only user value in it). */
  type LitResult = LitResult.T
  object LitResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LitS = LitS.T
  object LitS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkedQueryResult = LinkedQueryResult.T
  object LinkedQueryResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkedQueryOnly = LinkedQueryOnly.T
  object LinkedQueryOnly extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkedQueryRefresh = LinkedQueryRefresh.T
  object LinkedQueryRefresh extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkedResultPart2Part1 = LinkedResultPart2Part1.T
  object LinkedResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkedResultPart2Part2 = LinkedResultPart2Part2.T
  object LinkedResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkedOnly = LinkedOnly.T
  object LinkedOnly extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkedRefresh = LinkedRefresh.T
  object LinkedRefresh extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OneCk = OneCk.T
  object OneCk extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OneDryRun = OneDryRun.T
  object OneDryRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CalibreStepResultPart2Part2Part1 = CalibreStepResultPart2Part2Part1.T
  object CalibreStepResultPart2Part2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CalibreStepResultPart2Part2Part2 = CalibreStepResultPart2Part2Part2.T
  object CalibreStepResultPart2Part2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CalibreStepOnly = CalibreStepOnly.T
  object CalibreStepOnly extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CalibreStepRefresh = CalibreStepRefresh.T
  object CalibreStepRefresh extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CalibreStepDryRun = CalibreStepDryRun.T
  object CalibreStepDryRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CalibreStepLimit = CalibreStepLimit.T
  object CalibreStepLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private def lit(s: com.histmersion.cli.MediaCovers.LitS): com.histmersion.cli.MediaCovers.LitResult = "'" + s.replace("'", "''") + "'"

  /** Pure: the read-only query for the Calibre-linked sources to look at — those with no cover yet (every one with
    * `refresh`), or just `only`.
    */
  def linkedQuery(only: Option[com.histmersion.cli.MediaCovers.LinkedQueryOnly], refresh: com.histmersion.cli.MediaCovers.LinkedQueryRefresh): com.histmersion.cli.MediaCovers.LinkedQueryResult =
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
  def linked(c: Client, only: Option[com.histmersion.cli.MediaCovers.LinkedOnly], refresh: com.histmersion.cli.MediaCovers.LinkedRefresh): Either[Cli.Error, List[(com.histmersion.cli.MediaCovers.LinkedResultPart2Part1, com.histmersion.cli.MediaCovers.LinkedResultPart2Part2)]] =
    c.query(linkedQuery(only, refresh))
      .map(_.rows.flatMap { r =>
        for ck <- r.headOption.flatten; id <- r.lift(1).flatten.flatMap(_.trim.toIntOption) yield (ck, id)
      })

  /** One source's cover from its Calibre entry: uploaded (unless `dryRun`), or the ISBN Open Library should be asked
    * by, or why there is none.
    */
  enum Outcome:
    case Uploaded(line: MediaSourceLine)
    case AskOpenLibrary(citekey: AskOpenLibrary.Citekey, isbn: AskOpenLibrary.Isbn)
    case Skipped(reason: Skipped.Reason)
    case Failed(message: Failed.Message)

  object Outcome:
    object AskOpenLibrary:
      type Citekey = Citekey.T
      object Citekey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Isbn = Isbn.T
      object Isbn extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    object Skipped:
      type Reason = Reason.T
      object Reason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    object Failed:
      type Message = Message.T
      object Message extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def one(c: Client, ck: com.histmersion.cli.MediaCovers.OneCk, book: Calibre.Book, dryRun: com.histmersion.cli.MediaCovers.OneDryRun): Outcome =
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
      limit: Option[com.histmersion.cli.MediaCovers.CalibreStepLimit]
  ): Either[Cli.Error, (MediaCoversReport, List[(com.histmersion.cli.MediaCovers.CalibreStepResultPart2Part2Part1, com.histmersion.cli.MediaCovers.CalibreStepResultPart2Part2Part2)])] =
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
