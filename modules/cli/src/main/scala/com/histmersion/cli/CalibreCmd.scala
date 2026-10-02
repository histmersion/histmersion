package com.histmersion.cli

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.core.*

import io.circe.syntax.*
import java.nio.file.Path

/** `histmersion calibre search|import|link|push|update|set|audit|sync|gaps` — the host-side Calibre integration.
  *
  * This is the ONE command family that does not go entirely through the server: the Calibre library
  * lives on the user's machine and the server may be a container, so the CLI reads `metadata.db`
  * (read-only) and shells out to `calibredb` here, and uses the server only for the corpus side of
  * each operation.
  *
  * Nothing in `search`/`gaps`/`audit`/`link` can modify the library. `push` (with or without `--replace`)/`update`/`set`/`sync --apply` are the only writes, and
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
    "Import a purchased book from Calibre into the corpus and convert it (the Calibre original is never touched)") {
    (
      Opts.argument[Int]("calibre-id"),
      Opts.option[String]("citekey", "Override the generated citekey").orNone,
      Opts.option[String]("format", "pdf | epub").orNone,
      Opts.flag("no-convert", "Create or enrich the record only; do not upload the file for OCR").orFalse,
      Opts.flag("no-wait", "Queue the conversion and return instead of following it").orFalse,
      jsonFlag
    ).mapN { (id, citekey, format, noConvert, noWait, json) => (c: Client) =>
      for
        book <- calibre(c).byId(id).left.map(fail)
          .flatMap(_.toRight(fail(s"no Calibre book with id $id")))
        _    <- Calibre.guard(book.title, book.authors).left.map(e =>
                  fail(s"Calibre id $id has $e"))
        file <- book.pick(format).toRight(fail(
                  s"Calibre id $id has no pdf/epub (formats: ${book.formats.mkString(", ")}); " +
                    "KFX/MOBI must be converted in Calibre first"))
        ck    = citekey.getOrElse(Calibre.citekeyFor(book))
        existing <- AcquireClient(c).calibreMeta(ck) match
                  case Right(m) => Right(Some(m.calibreId))
                  case Left(e) if e.exitCode == 2 && e.message.startsWith("No source") => Right(None)
                  case Left(e) => Left(e)
        target <- Calibre.importTarget(ck, id, citekey.isDefined, existing).left.map(m => CliError(m, exitCode = 1))
        res  <- upsert(c, ck, book, target)
        _     = if json then println(res.asJson.spaces2)
                else
                  println(s"Imported Calibre id $id as `$ck` — ${book.title}")
                  target match
                    case ImportTarget.Update(Some(prev)) => println(s"Re-linked `$ck` from Calibre id $prev to $id")
                    case _                               => ()
                  println(s"File (left in place, never modified): $file")
        // the conversion is the same upload `histmersion ingest <file>` makes, marked as Calibre's
        _    <- if noConvert then Right(())
                else IngestCmd.upload(c, Path.of(file), ck, quality = false, force = false, title = None,
                  authors = Nil, year = None, doi = None, noWait = noWait || json, provenance = Some("calibre"))
      yield ()
    }
  }

  /** Create the source, or enrich it if the citekey already exists — a re-import must never wipe the
    * findings and notes an agent compiled. Either way the record ends up linked to THIS entry (#721:
    * an import into an existing record used to keep its old Calibre id). */
  private def upsert(c: Client, ck: String, b: CalibreBook, target: ImportTarget): Either[CliError, SourceWithFindings] =
    val summary = b.comments.map(stripHtml).filter(_.nonEmpty).map(_.take(500))
    target match
      case ImportTarget.Update(_) =>
        c.patchSource(ck, SourcePatch(
          metadata = Some(MetadataPatch(
            title = Some(b.title), authors = Some(b.authors), year = b.year,
            venue = b.publisher, doi = b.doi, calibreId = Some(b.id.toString))),
          summary = summary))
      case ImportTarget.Create =>
        c.putSource(ck, SourcePut(
          title = b.title, authors = b.authors, doi = b.doi, year = b.year, venue = b.publisher,
          status = Some(SourceStatus.Unassessed),
          tags = b.tags, summary = summary,
          provenance = Some("calibre"), calibreId = Some(b.id.toString)))

  // ── link (re-point a record at another Calibre entry) ────────────────────────────────────────

  private val link = Opts.subcommand("link",
    "Link a source to an existing Calibre entry (checks the entry exists; Calibre itself is not changed)") {
    (Opts.argument[String]("citekey"), Opts.argument[Int]("calibre-id"), jsonFlag).mapN { (ck, id, json) => (c: Client) =>
      for
        book <- calibre(c).byId(id).left.map(fail).flatMap(_.toRight(fail(s"no Calibre book with id $id")))
        sw   <- c.patchSource(ck, SourcePatch(metadata = Some(MetadataPatch(calibreId = Some(id.toString)))))
      yield
        if json then println(io.circe.Json.obj("citekey" -> sw.source.citekey.asJson, "calibreId" -> id.asJson).spaces2)
        else println(s"linked `${sw.source.citekey}` to Calibre id $id — ${book.title}")
    }
  }

  private def stripHtml(s: String): String = s.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim

  // ── push / sync (corpus → Calibre) ──────────────────────────────────────────────────────────

  private val push = Opts.subcommand("push",
    "Add a stored source's file to Calibre with title/authors/DOI/tags; --replace swaps the file of its linked entry instead (Calibre GUI must be closed)") {
    (
      Opts.argument[String]("citekey"),
      Opts.option[String]("file", "The file to add (default: <cli.calibre.originals-dir>/<citekey>/paper.*; with --replace: the original the server stored)").orNone,
      Opts.flag("replace", "Replace the file of the source's LINKED Calibre entry instead of adding a new entry").orFalse,
      jsonFlag
    ).mapN { (ck, file, replace, json) => (c: Client) =>
      if replace then
        replaceOne(c, ck, file.map(Path.of(_))).map { case (id, fmt) =>
          if json then println(io.circe.Json.obj("citekey" -> ck.asJson, "calibreId" -> id.asJson,
            "replaced" -> fmt.asJson).spaces2)
          else println(s"replaced the ${fmt.toUpperCase} of Calibre id $id with `$ck`'s original")
        }
      else
        pushOne(c, ck, file.map(Path.of(_))).map { case (id, title) =>
          if json then println(io.circe.Json.obj("citekey" -> ck.asJson, "calibreId" -> id.asJson).spaces2)
          else println(s"added `$ck` to Calibre as id $id — $title")
        }
    }
  }

  /** `push --replace` (#652): the file goes into the entry the source is ALREADY linked to, so the
    * link stays valid and no duplicate entry appears. The file is `--file`, else the original the
    * server stored at ingest, downloaded to a temporary directory and removed afterwards. */
  private def replaceOne(c: Client, ck: String, file: Option[Path]): Either[CliError, (Int, String)] =
    for
      s   <- AcquireClient(c).calibreMeta(ck)
      id  <- s.calibreId.flatMap(_.trim.toIntOption).toRight(fail(
               s"'$ck' is not linked to a Calibre entry — `histmersion calibre push $ck` adds one, " +
                 s"`histmersion calibre link $ck <id>` links an existing one"))
      fmt <- file match
               case Some(f) => Calibre(AppConfig.load().cli.calibre).replaceFormat(id, f).left.map(fail)
               case None    => withOriginal(c, ck)(f => Calibre(AppConfig.load().cli.calibre).replaceFormat(id, f).left.map(fail))
    yield (id, fmt)

  /** Download the server's stored original of `ck` as `<tmp>/<ck>.<ext>` (the extension is what
    * tells Calibre the format), run `f` on it, and delete it whatever happens. */
  private def withOriginal[A](c: Client, ck: String)(f: Path => Either[CliError, A]): Either[CliError, A] =
    val dir = java.nio.file.Files.createTempDirectory("histmersion-original-")
    val raw = dir.resolve("download")
    try
      for
        got <- c.original(ck, raw.toFile)
        ext <- Ingest.dispositionExtension(got._2).toRight(fail(s"the server did not say what format '$ck' is"))
        named = java.nio.file.Files.move(raw, dir.resolve(s"$ck.$ext"))
        out <- f(named)
      yield out
    finally
      val s = java.nio.file.Files.list(dir)
      try s.forEach(p => java.nio.file.Files.deleteIfExists(p): Unit) finally s.close()
      java.nio.file.Files.deleteIfExists(dir): Unit

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

  private val set = Opts.subcommand("set",
    s"Set fields on a Calibre entry, e.g. --field languages:eng (settable: ${Calibre.SettableFields.mkString(", ")}; Calibre GUI must be closed)") {
    (
      Opts.argument[Int]("calibre-id"),
      Opts.options[String]("field", "<name>:<value>, repeatable").map(_.toList)
    ).mapN { (id, raw) => (c: Client) =>
      raw.traverse(f => f.split(":", 2) match
        case Array(k, v) if k.trim.nonEmpty => Right(k.trim -> v)
        case _ => Left(fail(s"'$f' is not <name>:<value>"))
      ).flatMap(fields => calibre(c).set(id, fields).left.map(fail))
        .map(_ => println(s"set ${raw.map(_.takeWhile(_ != ':')).mkString(", ")} on Calibre id $id"))
    }
  }

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
    Opts.subcommand("calibre", "The local Calibre library: search, import, link, push, update, set, audit, sync, gaps") {
      search orElse importCmd orElse link orElse push orElse update orElse set orElse audit orElse sync orElse gaps
    }
