package com.histmersion.cli

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.core.*

import io.circe.syntax.*
import java.nio.file.Path

/** `histmersion calibre search|import|push|update|audit|sync|gaps` — the host-side Calibre integration.
  *
  * This is the ONE command family that does not go entirely through the server: the Calibre library
  * lives on the user's machine and the server may be a container, so the CLI reads `metadata.db`
  * (read-only) and shells out to `calibredb` here, and uses the server only for the corpus side of
  * each operation.
  *
  * Nothing in `search`/`gaps` can modify the library. `push`/`update`/`sync --apply` are the only writes, and
  * they refuse to run while the Calibre GUI holds the database.
  */
object CalibreCmd:

  private val jsonFlag = Opts.flag("json", "Print raw JSON instead of Markdown").orFalse

  private def calibre(c: Client): Calibre = Calibre(AppConfig.load().cli.calibre)

  private def fail(msg: String) = CliError(msg, exitCode = 2)

  // ── search ──────────────────────────────────────────────────────────────────────────────────

  private val search = Opts.subcommand("search", "Search the local Calibre library (read-only)") {
    (Opts.argument[String]("query"), Opts.option[Int]("limit", "Max results").orNone, jsonFlag)
      .mapN { (q, limit, json) => (c: Client) =>
        calibre(c).search(q, limit.getOrElse(20)).left.map(fail).map { books =>
          if json then println(booksJson(books)) else println(Calibre.markdownSearch(books))
        }
      }
  }

  private def booksJson(books: List[CalibreBook]): String =
    io.circe.Json.arr(books.map(b => io.circe.Json.obj(
      "id" -> b.id.asJson, "title" -> b.title.asJson, "authors" -> b.authors.asJson,
      "year" -> b.year.asJson, "publisher" -> b.publisher.asJson, "tags" -> b.tags.asJson,
      "identifiers" -> b.identifiers.asJson, "formats" -> b.formats.asJson))*).spaces2

  // ── import (Calibre → corpus) ───────────────────────────────────────────────────────────────

  private val importCmd = Opts.subcommand("import",
    "Import a purchased book from Calibre into the corpus (the Calibre original is never touched)") {
    (
      Opts.argument[Int]("calibre-id"),
      Opts.option[String]("citekey", "Override the generated citekey").orNone,
      Opts.option[String]("format", "pdf | epub").orNone,
      jsonFlag
    ).mapN { (id, citekey, format, json) => (c: Client) =>
      for
        book <- calibre(c).byId(id).left.map(fail)
          .flatMap(_.toRight(fail(s"no Calibre book with id $id")))
        _    <- Calibre.guard(book.title, book.authors).left.map(e =>
                  fail(s"Calibre id $id has $e"))
        file <- book.pick(format).toRight(fail(
                  s"Calibre id $id has no pdf/epub (formats: ${book.formats.mkString(", ")}); " +
                    "KFX/MOBI must be converted in Calibre first"))
        ck    = citekey.getOrElse(Acquire.citekey(book.authors, book.year, Some(book.title)))
        res  <- upsert(c, ck, book)
      yield
        if json then println(res.asJson.spaces2)
        else
          println(s"Imported Calibre id $id as `$ck` — ${book.title}")
          println(s"File (left in place, never modified): $file")
          println(
            "The body still needs converting: run `histmersion ingest " + file + " --citekey " + ck +
              "` once the upload path (stream 2.4) is in, or re-run this after it lands.")
    }
  }

  /** Create the source, or enrich it if the citekey already exists — a re-import must never wipe the
    * findings and notes an agent compiled. */
  private def upsert(c: Client, ck: String, b: CalibreBook): Either[CliError, SourceWithFindings] =
    val summary = b.comments.map(stripHtml).filter(_.nonEmpty).map(_.take(500))
    c.getSource(ck) match
      case Right(_) =>
        c.patchSource(ck, SourcePatch(
          metadata = Some(MetadataPatch(
            title = Some(b.title), authors = Some(b.authors), year = b.year,
            venue = b.publisher, doi = b.doi)),
          summary = summary))
      case Left(_) =>
        c.putSource(ck, SourcePut(
          title = b.title, authors = b.authors, doi = b.doi, year = b.year, venue = b.publisher,
          status = Some(SourceStatus.Unassessed),
          tags = b.tags, summary = summary,
          provenance = Some("calibre"), calibreId = Some(b.id.toString)))

  private def stripHtml(s: String): String = s.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim

  // ── push / sync (corpus → Calibre) ──────────────────────────────────────────────────────────

  private val push = Opts.subcommand("push",
    "Add a stored source's file to Calibre with title/authors/DOI/tags (Calibre GUI must be closed)") {
    (
      Opts.argument[String]("citekey"),
      Opts.option[String]("file", "The file to add (default: <cli.calibre.originals-dir>/<citekey>/paper.*)").orNone,
      jsonFlag
    ).mapN { (ck, file, json) => (c: Client) =>
      pushOne(c, ck, file.map(Path.of(_))).map { case (id, title) =>
        if json then println(io.circe.Json.obj("citekey" -> ck.asJson, "calibreId" -> id.asJson).spaces2)
        else println(s"added `$ck` to Calibre as id $id — $title")
      }
    }
  }

  /** One push: read the source's metadata from the SERVER, find the file on THIS host, `calibredb
    * add`, then record the new id back in the corpus so it stops counting as a gap. */
  private def pushOne(c: Client, ck: String, file: Option[Path]): Either[CliError, (Int, String)] =
    val cfg = AppConfig.load().cli.calibre
    for
      // the authors must come back as a LIST: "Stocker, Sharon R." is ONE author, so the
      // comma-joined `SourceDetail.authors` cannot be split apart again
      s    <- AcquireClient(c).calibreMeta(ck)
      path <- file.orElse(Calibre.originalFor(cfg.originals, ck)).toRight(fail(
                s"no file for '$ck' — pass --file, or set cli.calibre.originals-dir " +
                  "(HISTMERSION_CALIBRE_ORIGINALS_DIR) to the directory holding <citekey>/paper.pdf"))
      id   <- Calibre(cfg).add(path, pushMeta(s)).left.map(fail)
      _    <- AcquireClient(c).calibrePushed(CalibrePushRequest(ck, id.toString))
    yield (id, s.title)

  private val update = Opts.subcommand("update",
    "Rewrite a source's existing Calibre entry (title, authors, publisher, year, comments) from the corpus (Calibre GUI must be closed)") {
    (Opts.argument[String]("citekey"), jsonFlag).mapN { (ck, json) => (c: Client) =>
      for
        s    <- AcquireClient(c).calibreMeta(ck)
        id   <- s.calibreId.flatMap(_.toIntOption).toRight(fail(
                  s"'$ck' has no Calibre entry yet — use `histmersion calibre push $ck`"))
        _    <- Calibre(AppConfig.load().cli.calibre).update(id, pushMeta(s)).left.map(fail)
      yield
        if json then println(io.circe.Json.obj("citekey" -> ck.asJson, "calibreId" -> id.asJson).spaces2)
        else println(s"updated Calibre id $id from `$ck` — ${s.title}")
    }
  }

  /** What the corpus record sends into Calibre, for `push` and `update` alike. */
  private def pushMeta(s: CalibreGap): CalibrePush =
    CalibrePush(
      title = s.title,
      authors = s.authors,
      doi = s.doi,
      year = s.year,
      publisher = s.venue,
      summary = s.summary.map(_.take(500)),
      tags = s.topics)

  private val audit = Opts.subcommand("audit",
    "Compare every source's Calibre entry with the corpus record (title, authors, year) — read-only") {
    jsonFlag.map { json => (c: Client) =>
      for
        q    <- c.query("SELECT citekey, calibre_id, title, array_to_string(authors, '|') AS authors, year " +
                  "FROM sources WHERE calibre_id ~ '^[0-9]+$' ORDER BY citekey")
        _    <- Either.cond(!q.truncated, (), fail("too many linked sources for one query — raise query.max-rows"))
        recs  = q.rows.flatMap {
                  case List(Some(ck), Some(id), title, authors, year) =>
                    Some(AuditRecord(ck, id.toInt, title.getOrElse(""),
                      authors.toList.flatMap(_.split('|')).map(_.trim).filter(_.nonEmpty), year.flatMap(_.toIntOption)))
                  case _ => None
                }
        books <- calibre(c).all().left.map(fail)
      yield
        val found = CalibreAudit.audit(recs, books.map(b => b.id -> b).toMap)
        if json then println(found.map(f => io.circe.Json.obj(
          "calibreId" -> f.calibreId.asJson, "citekeys" -> f.citekeys.asJson,
          "issues" -> f.issues.map(_.toString).asJson, "fix" -> Option.when(f.fixable)(f.record.citekey).asJson)).asJson.spaces2)
        else println(CalibreAudit.markdown(found, recs.map(_.calibreId).distinct.size))
    }
  }

  private val sync = Opts.subcommand("sync",
    "Push every source that is not in Calibre yet (dry run unless --apply)") {
    (
      Opts.flag("apply", "Actually add them (Calibre GUI must be closed)").orFalse,
      Opts.option[Int]("limit", "Only consider this many gaps").orNone,
      jsonFlag
    ).mapN { (apply, limit, json) => (c: Client) =>
      val cfg = AppConfig.load().cli.calibre
      AcquireClient(c).calibreGaps(limit).flatMap { gaps =>
        if json && !apply then Right(println(gaps.asJson.spaces2))
        else
          // a gap we cannot push is not a failure — the file simply is not on this host
          val (ready, missing) = gaps.gaps.partition(g =>
            Calibre.originalFor(cfg.originals, g.citekey).isDefined)
          println(AcquireMarkdown.calibreGaps(gaps))
          if missing.nonEmpty then
            println(s"\n${missing.size} of ${gaps.count} have no file on this host " +
              s"(cli.calibre.originals-dir = ${cfg.originals.getOrElse("<unset>")}) — skipped.")
          if !apply then
            println(s"\nDry run: ${ready.size} source(s) would be pushed. Re-run with --apply.")
            Right(())
          else
            val results = ready.map(g => g.citekey -> pushOne(c, g.citekey, None))
            results.foreach {
              case (ck, Right((id, _))) => println(s"  + $ck → Calibre id $id")
              case (ck, Left(e))        => Console.err.println(s"  ! $ck: ${e.message}")
            }
            val failed = results.count(_._2.isLeft)
            println(s"pushed ${results.size - failed} source(s), ${failed} failed, ${missing.size} skipped")
            if failed == 0 then Right(())
            else Left(fail(s"$failed of ${results.size} push(es) failed"))
      }
    }
  }

  private val gaps = Opts.subcommand("gaps",
    "List sources missing from Calibre; --quiet prints one line and exits 1 when there are any (the reminder hook)") {
    (Opts.flag("quiet", "One line, exit 1 when gaps exist").orFalse,
     Opts.option[Int]("limit", "Max gaps to list").orNone,
     jsonFlag).mapN { (quiet, limit, json) => (c: Client) =>
      AcquireClient(c).calibreGaps(limit).flatMap { g =>
        if json then Right(println(g.asJson.spaces2))
        else if !quiet then Right(println(AcquireMarkdown.calibreGaps(g)))
        else if g.count == 0 then Right(())
        else
          // exit 1 with ONE line: this is what the Stop/PostToolUse reminder hook reads
          Left(CliError(
            s"${g.count} source(s) are not in Calibre yet (${g.gaps.take(3).map(_.citekey).mkString(", ")}" +
              (if g.count > 3 then ", …" else "") + ") — run `histmersion calibre sync --apply`",
            exitCode = 1))
      }
    }
  }

  val all: Opts[Client => Either[CliError, Unit]] =
    Opts.subcommand("calibre", "The local Calibre library: search, import, push, update, audit, sync, gaps") {
      search orElse importCmd orElse push orElse update orElse audit orElse sync orElse gaps
    }
