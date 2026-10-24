package com.histmersion.cli

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

import java.nio.file.{Files, Path}

/** The host-side half of a source's cover (#1446): the cover the local Calibre library keeps beside
  * a linked book is READ here (the library is on the user's machine, like every `calibre` command)
  * and uploaded to the server as the source's cover. Nothing is written to the library. When Calibre
  * has no cover file but knows the book's ISBN, the server is asked to fetch Open Library's by it. */
object MediaCovers:

  /** Pure: a citekey as a SQL literal for the read-only query (the only user value in it). */
  private def lit(s: String): String = "'" + s.replace("'", "''") + "'"

  /** Pure: the read-only query for the Calibre-linked sources to look at — those with no cover yet
    * (every one with `refresh`), or just `only`. */
  def linkedQuery(only: Option[String], refresh: Boolean): String =
    s"""SELECT s.citekey, s.calibre_id FROM sources s
        WHERE s.calibre_id ~ '^[0-9]+$$'
          ${only.fold("")(c => s"AND s.citekey = ${lit(c)}")}
          ${if refresh then "" else
             "AND NOT EXISTS (SELECT 1 FROM media_links m WHERE m.target_type = 'source' AND m.target_id = s.citekey AND m.role = 'cover')"}
        ORDER BY s.citekey"""

  /** The Calibre-linked sources to look at, as (citekey, Calibre id). */
  def linked(c: Client, only: Option[String], refresh: Boolean): Either[CliError, List[(String, Int)]] =
    c.query(linkedQuery(only, refresh)).map(_.rows.flatMap { r =>
      for ck <- r.headOption.flatten; id <- r.lift(1).flatten.flatMap(_.trim.toIntOption) yield (ck, id)
    })

  /** One source's cover from its Calibre entry: uploaded (unless `dryRun`), or the ISBN Open Library
    * should be asked by, or why there is none. */
  enum Outcome:
    case Uploaded(line: MediaSourceLine)
    case AskOpenLibrary(citekey: String, isbn: String)
    case Skipped(reason: String)
    case Failed(message: String)

  def one(c: Client, ck: String, book: CalibreBook, dryRun: Boolean): Outcome =
    CoverChoice.choose(book.cover, p => Files.isRegularFile(Path.of(p)), book.isbn.toList, book.identifiers.get("oclc")) match
      case Some(CoverChoice.Pick.Calibre(path)) =>
        val line = MediaSourceLine(ck, MediaRole.Cover, MediaProvider.Calibre, s"Calibre id ${book.id}")
        if dryRun then Outcome.Uploaded(line)
        else
          scala.util.Try(Files.readAllBytes(Path.of(path))).toEither match
            case Left(e) => Outcome.Failed(s"$ck: cannot read $path (${e.getMessage})")
            case Right(bytes) =>
              c.putSourceCover(SourceCoverParams(ck, MediaProvider.Calibre, book.id.toString, None), bytes) match
                case Right(_) => Outcome.Uploaded(line)
                case Left(e)  => Outcome.Failed(s"$ck: ${e.message}")
      case Some(CoverChoice.Pick.OpenLibrary("isbn", isbn)) => Outcome.AskOpenLibrary(ck, isbn)
      case Some(CoverChoice.Pick.OpenLibrary(_, _))         => Outcome.Skipped("no cover file in Calibre")
      case None                                              => Outcome.Skipped("no cover file in Calibre, no ISBN")

  /** The Calibre step over the linked sources: what was uploaded, what Open Library is to be asked
    * for (by the server), and the counts. */
  def calibreStep(c: Client, cal: Calibre, only: Option[String], refresh: Boolean, dryRun: Boolean,
      limit: Option[Int]): Either[CliError, (MediaCoversReport, List[(String, String)])] =
    if !cal.libraryExists then
      Right((MediaCoversReport(dryRun, skipped = List(MediaCount("no Calibre library here", 1))), Nil))
    else
      for
        links <- linked(c, only, refresh)
        books <- if links.isEmpty then Right(Nil) else cal.all().left.map(e => CliError(e, exitCode = ExitCode.Refused))
      yield
        val byId = books.map(b => b.id -> b).toMap
        val outcomes = links.take(limit.filter(_ > 0).getOrElse(Int.MaxValue)).map { (ck, id) =>
          byId.get(id).fold(Outcome.Skipped(s"Calibre id not in the library"))(one(c, ck, _, dryRun))
        }
        val lines  = outcomes.collect { case Outcome.Uploaded(l) => l }
        val asks   = outcomes.collect { case Outcome.AskOpenLibrary(ck, i) => (ck, i) }
        val report = MediaCoversReport(dryRun, links.size min limit.filter(_ > 0).getOrElse(Int.MaxValue),
          covers = lines.size, lines = lines,
          skipped = MediaCount.of(outcomes.collect { case Outcome.Skipped(r) => s"cover: $r" }),
          failed = outcomes.collect { case Outcome.Failed(m) => m })
        (report, asks)
