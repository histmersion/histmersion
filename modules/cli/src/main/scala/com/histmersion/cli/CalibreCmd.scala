package com.histmersion.cli

import cats.data.Validated
import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

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

  private def calibre(c: Client): Calibre = Calibre(Settings.app.cli.calibre)

  private def fail(msg: String) = CliError(msg, exitCode = ExitCode.Refused)

  // ── search ──────────────────────────────────────────────────────────────────────────────────

  private val search = Opts.subcommand("search",
    "Search the local Calibre library (read-only); --unlinked lists the entries no source links yet, newest first") {
    (
      Opts.argument[String]("query").orNone,
      Opts.flag("unlinked", "Only entries no source is linked to (the query, if given, narrows them)").orFalse,
      Opts.option[Int]("limit", "Max results").orNone,
      Output.jsonFlag
    ).tupled.mapValidated { (q, unlinked, limit, json) =>
      // an EMPTY query is the whole library (`search "" --limit 2000`, the enrich-calibre audit);
      // only a missing one is refused
      Validated.condNel(q.isDefined || unlinked, (q, unlinked, limit, json), "give a query, or --unlinked")
    }.map { (q, unlinked, limit, json) => (c: Client) =>
      val max = limit.getOrElse(20)
      if !unlinked then
        calibre(c).search(q.getOrElse(""), max).left.map(fail).map { books =>
          Output.emit(json, books.map(CalibreJson.Book.of))(_ => Calibre.markdownSearch(books))
        }
      else
        for
          linked <- linkedIds(c)
          books  <- q.map(_.trim).filter(_.nonEmpty).fold(calibre(c).all())(calibre(c).search(_, Int.MaxValue)).left.map(fail)
        yield
          val all  = Calibre.unlinked(books, linked, Int.MaxValue)
          val page = all.take(max.max(1))
          Output.emit(json, page.map(CalibreJson.Book.of))(_ => Calibre.markdownUnlinked(page, all.size))
    }
  }

  /** The Calibre ids the corpus links, read through the server's read-only query. */
  private def linkedIds(c: Client): Either[CliError, Set[Int]] =
    c.query("SELECT DISTINCT calibre_id FROM sources WHERE calibre_id ~ '^[0-9]+$'").flatMap { q =>
      Either.cond(!q.truncated, q.rows.flatMap(_.headOption.flatten).flatMap(_.toIntOption).toSet,
        fail("too many linked sources for one query — raise query.max-rows"))
    }

  // ── import (Calibre → corpus) ───────────────────────────────────────────────────────────────

  private val importCmd = Opts.subcommand("import",
    "Import a purchased book from Calibre into the corpus and convert it (the Calibre original is never touched)") {
    (
      Opts.argument[Int]("calibre-id"),
      Opts.option[String]("citekey", "Override the generated citekey").orNone,
      Opts.option[String]("format", "pdf | epub").orNone,
      Opts.flag("no-convert", "Create or enrich the record only; do not upload the file for OCR").orFalse,
      Opts.flag("no-wait", "Queue the conversion and return instead of following it").orFalse,
      Opts.option[Int]("acquire-item", "Id of the acquisition-queue item this book fulfils — ticked off when the conversion succeeds (without it, an open item naming the book's DOI is ticked off, and similar ones are listed)").orNone,
      Output.jsonFlag
    ).tupled.mapValidated { (id, citekey, format, noConvert, noWait, item, json) =>
      Validated.condNel(!(noConvert && item.isDefined), (id, citekey, format, noConvert, noWait, item, json),
        "--acquire-item is ticked off by the conversion; drop --no-convert, or tick the item off with `histmersion queue done <id>`")
    }.map { (id, citekey, format, noConvert, noWait, item, json) => (c: Client) =>
      for
        // checked BEFORE the record is written: a typo'd item must not leave a half-done import behind
        _    <- item.fold(Right(()))(i => c.queueList(Queue.Kind.Acquire.label, all = true).flatMap(l =>
                  Queue.fulfillable(i, l.items.find(_.id == i)).left.map(e => CliError(e, exitCode = ExitCode.Usage)).map(_ => ())))
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
                  case Left(e) if e.exitCode == ExitCode.Refused && e.message.startsWith("No source") => Right(None)
                  case Left(e) => Left(e)
        target <- Calibre.importTarget(ck, id, citekey.isDefined, existing).left.map(m => CliError(m, exitCode = ExitCode.Usage))
        res  <- upsert(c, ck, book, target)
        _     = if json then println(Output.json(res))
                else
                  println(s"Imported Calibre id $id as `$ck` — ${book.title}")
                  target match
                    case ImportTarget.Update(Some(prev)) => println(s"Re-linked `$ck` from Calibre id $prev to $id")
                    case _                               => ()
                  println(s"File (left in place, never modified): $file")
        // the book's cover for its hover card (#1446): Calibre's own, else Open Library's by its ISBN —
        // best-effort, a missing cover never fails the import
        _     = if !json then importCover(c, ck, book)
        // the conversion is the same upload `histmersion ingest <file>` makes, marked as Calibre's
        _    <- if noConvert then Right(())
                else IngestCmd.upload(c, Path.of(file), ck, quality = false, force = false, title = None,
                  authors = Nil, year = None, doi = None, noWait = noWait || json, provenance = Some("calibre"),
                  acquireItem = item)
      yield ()
    }
  }

  /** The imported book's cover, best-effort: a line saying what happened, never a failure. */
  private def importCover(c: Client, ck: String, book: CalibreBook): Unit =
    MediaCovers.one(c, ck, book, dryRun = false) match
      case MediaCovers.Outcome.Uploaded(_) => println(s"Cover: from the Calibre library")
      case MediaCovers.Outcome.AskOpenLibrary(_, isbn) =>
        c.mediaCovers(MediaCoversRequest(dryRun = false, citekey = Some(ck), illustrations = false, isbn = Some(isbn))) match
          case Right(r) if r.covers > 0 => println(s"Cover: from Open Library (ISBN $isbn)")
          case _                        => println(s"Cover: none (no cover file in Calibre, none at Open Library)")
      case MediaCovers.Outcome.Skipped(why)  => println(s"Cover: none ($why)")
      case MediaCovers.Outcome.Failed(why)   => Console.err.println(s"warning: cover not stored — $why")

  /** Create the source, or enrich it if the citekey already exists — a re-import must never wipe the
    * findings and notes an agent compiled. Either way the record ends up linked to THIS entry (#721:
    * an import into an existing record used to keep its old Calibre id). */
  private def upsert(c: Client, ck: String, b: CalibreBook, target: ImportTarget): Either[CliError, SourceWithFindings] =
    val summary = b.comments.map(stripHtml).filter(_.nonEmpty).map(_.take(500))
    target match
      case ImportTarget.Update(_) =>
        for
          current <- c.call(Api.getSource)((ck, false))
          res     <- c.patchSource(ck, SourcePatch(
          metadata = Some(MetadataPatch(
            title = Some(b.title), authors = Some(b.authors), year = b.year,
            venue = b.publisher, doi = b.doi, calibreId = Some(b.id.toString))),
          summary = Calibre.importSummary(summary, current.source.summary)))
        yield res
      case ImportTarget.Create =>
        c.putSource(ck, SourcePut(
          title = b.title, authors = b.authors, doi = b.doi, year = b.year, venue = b.publisher,
          status = Some(SourceStatus.Unassessed),
          tags = b.tags, summary = summary,
          provenance = Some("calibre"), calibreId = Some(b.id.toString)))

  // ── link (re-point a record at another Calibre entry) ────────────────────────────────────────

  private val link = Opts.subcommand("link",
    "Link a source to an existing Calibre entry (checks the entry exists; Calibre itself is not changed)") {
    (Opts.argument[String]("citekey"), Opts.argument[Int]("calibre-id"), Output.jsonFlag).mapN { (ck, id, json) => (c: Client) =>
      for
        book <- calibre(c).byId(id).left.map(fail).flatMap(_.toRight(fail(s"no Calibre book with id $id")))
        sw   <- c.patchSource(ck, SourcePatch(metadata = Some(MetadataPatch(calibreId = Some(id.toString)))))
      yield
        Output.emit(json, CalibreJson.Linked(sw.source.citekey, id))(l =>
          s"linked `${l.citekey}` to Calibre id $id — ${book.title}")
    }
  }

  private def stripHtml(s: String): String = s.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim

  // ── push / sync (corpus → Calibre) ──────────────────────────────────────────────────────────

  private val push = Opts.subcommand("push",
    "Add a stored source's file to Calibre with title/authors/DOI/tags; --replace swaps the file of its linked entry instead (Calibre GUI must be closed)") {
    (
      Opts.argument[String]("citekey"),
      Opts.option[String]("file", "The file to add (default: the original the server stored; without --replace, <cli.calibre.originals-dir>/<citekey>/paper.* first when present)").orNone,
      Opts.flag("replace", "Replace the file of the source's LINKED Calibre entry instead of adding a new entry").orFalse,
      Output.jsonFlag
    ).mapN { (ck, file, replace, json) => (c: Client) =>
      if replace then
        replaceOne(c, ck, file.map(Path.of(_))).map { case (id, fmt) =>
          Output.emit(json, CalibreJson.Replaced(ck, id, fmt))(_ =>
            s"replaced the ${fmt.toUpperCase} of Calibre id $id with `$ck`'s original")
        }
      else
        pushOne(c, ck, file.map(Path.of(_))).map { case (id, title) =>
          Output.emit(json, CalibreJson.Linked(ck, id))(_ => s"added `$ck` to Calibre as id $id — $title")
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
               case Some(f) => Calibre(Settings.app.cli.calibre).replaceFormat(id, f).left.map(fail)
               case None    => withOriginal(c, ck)(f => Calibre(Settings.app.cli.calibre).replaceFormat(id, f).left.map(fail))
    yield (id, fmt)

  /** Download the server's stored original of `ck` as `<tmp>/<ck>.<ext>` (the extension is what
    * tells Calibre the format), run `f` on it, and delete it whatever happens. */
  private def withOriginal[A](c: Client, ck: String, hint: String = "")(f: Path => Either[CliError, A]): Either[CliError, A] =
    val dir = java.nio.file.Files.createTempDirectory("histmersion-original-")
    val raw = dir.resolve("download")
    try
      for
        got <- c.original(ck, raw.toFile).left.map(e => if hint.isEmpty then e else e.copy(message = e.message + hint))
        ext <- Ingest.dispositionExtension(got._2).toRight(fail(s"the server did not say what format '$ck' is"))
        named = java.nio.file.Files.move(raw, dir.resolve(s"$ck.$ext"))
        out <- f(named)
      yield out
    finally
      val s = java.nio.file.Files.list(dir)
      try s.forEach(p => java.nio.file.Files.deleteIfExists(p): Unit) finally s.close()
      java.nio.file.Files.deleteIfExists(dir): Unit

  /** One push: read the source's metadata from the SERVER, take the file ([[Calibre.pushFile]]:
    * `--file`, else `<originals-dir>/<citekey>/paper.*` on this host, else the original the server
    * stored, downloaded to a temporary directory), `calibredb add`, then record the new id back in the
    * corpus so it stops counting as a gap. */
  private[cli] def pushOne(c: Client, ck: String, file: Option[Path],
      calibre: Calibre = Calibre(Settings.app.cli.calibre),
      originals: Option[String] = Settings.app.cli.calibre.originals): Either[CliError, (Int, String)] =
    for
      // the authors must come back as a LIST: "Stocker, Sharon R." is ONE author, so the
      // comma-joined `SourceDetail.authors` cannot be split apart again
      s    <- AcquireClient(c).calibreMeta(ck)
      add   = (p: Path) => calibre.add(p, pushMeta(s)).left.map(fail)
      id   <- Calibre.pushFile(file, Calibre.originalFor(originals, ck)) match
                case Calibre.PushFile.Given(p) => add(p)
                case Calibre.PushFile.Local(p) => add(p)
                case Calibre.PushFile.Server   =>
                  withOriginal(c, ck, " — or set cli.calibre.originals-dir (HISTMERSION_CALIBRE_ORIGINALS_DIR) " +
                    "to a directory holding <citekey>/paper.pdf")(add)
      _    <- AcquireClient(c).calibrePushed(CalibrePushRequest(ck, id.toString))
    yield (id, s.title)

  private val update = Opts.subcommand("update",
    "Rewrite a source's Calibre entry from the corpus: title, authors, year, identifiers, tags (and publisher/comments where empty); --all for every linked entry (Calibre GUI must be closed)") {
    val one = (Opts.argument[String]("citekey"), Output.jsonFlag).mapN { (ck, json) => (c: Client) =>
      for
        s       <- AcquireClient(c).calibreMeta(ck)
        id      <- s.calibreId.flatMap(_.toIntOption).toRight(fail(
                     s"'$ck' has no Calibre entry yet — use `histmersion calibre push $ck`"))
        changes <- Calibre(Settings.app.cli.calibre).update(id, pushMeta(s)).left.map(fail)
      yield
        Output.emit(json, CalibreJson.Updated(ck, id, changes.map(_.field.name)))(u =>
          if u.changed.isEmpty then s"Calibre id $id already agrees with `$ck` — nothing written"
          else s"updated Calibre id $id from `$ck` (${u.changed.mkString(", ")}) — ${s.title}")
    }
    val every = (
      Opts.flag("all", "Every Calibre entry a source is linked to (a dry run listing the changes, unless --apply)"),
      Opts.flag("apply", "With --all: write the changes (Calibre GUI must be closed)").orFalse,
      Output.jsonFlag
    ).mapN { (_, apply, json) => (c: Client) =>
      for
        linked  <- linkedRecords(c)
        entries <- calibre(c).updateAll(linked, apply).left.map(fail)
        _        = Output.emit(json, entries.map(CalibreJson.UpdateRow.of))(_ => Calibre.markdownUpdateAll(entries, apply))
        failed   = entries.count(_.outcome match { case UpdateOutcome.Failed(_, _) => true; case _ => false })
        _       <- Either.cond(failed == 0, (), fail(s"$failed Calibre update(s) failed"))
      yield ()
    }
    one orElse every
  }

  /** Every source linked to a Calibre entry, with what it would send there. One query for the list,
    * then the push metadata per source (the authors must come back as a LIST — see [[pushOne]]). */
  private def linkedRecords(c: Client): Either[CliError, List[LinkedRecord]] =
    for
      q    <- c.query("SELECT citekey, calibre_id FROM sources WHERE calibre_id ~ '^[0-9]+$' ORDER BY citekey")
      _    <- Either.cond(!q.truncated, (), fail("too many linked sources for one query — raise query.max-rows"))
      keys  = q.rows.collect { case List(Some(ck), Some(id)) => ck -> id.toInt }
      recs <- keys.traverse((ck, id) => AcquireClient(c).calibreMeta(ck).map(s => LinkedRecord(ck, id, pushMeta(s))))
    yield recs

  /** What the corpus record sends into Calibre, for `push` and `update` alike. */
  private def pushMeta(s: CalibreGap): CalibrePush =
    CalibrePush(
      title = s.title,
      authors = s.authors,
      doi = s.doi,
      isbn = s.isbn,
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
    Output.jsonFlag.map { json => (c: Client) =>
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
        Output.emit(json, found.map(CalibreJson.AuditRow.of))(_ =>
          CalibreAudit.markdown(found, recs.map(_.calibreId).distinct.size))
    }
  }

  /** The server's gap listing, each with where its original is and whether it counts
    * ([[Calibre.classify]]): a record with no fetched file anywhere is not a Calibre gap. */
  private def classifiedGaps(c: Client, limit: Option[Int], originals: Option[String]): Either[CliError, List[Calibre.GapEntry]] =
    AcquireClient(c).calibreGaps(limit).map(g => Calibre.classify(g.gaps, Calibre.originalFor(originals, _)))

  private val sync = Opts.subcommand("sync",
    "Push every source with a fetched file that is not in Calibre yet (dry run unless --apply)") {
    (
      Opts.flag("apply", "Actually add them (Calibre GUI must be closed)").orFalse,
      Opts.option[Int]("limit", "Only consider this many gaps").orNone,
      Output.jsonFlag
    ).mapN { (apply, limit, json) => (c: Client) => syncGaps(c, apply, limit, json) }
  }

  /** `calibre sync`: push the COUNTED gaps (an original on this host or on the server); the records
    * with no fetched file are not gaps, so they are neither pushed nor reported as skipped — one
    * summary line says how many there are. */
  private[cli] def syncGaps(c: Client, apply: Boolean, limit: Option[Int], json: Boolean,
      calibre: Calibre = Calibre(Settings.app.cli.calibre),
      originals: Option[String] = Settings.app.cli.calibre.originals): Either[CliError, Unit] =
    classifiedGaps(c, limit, originals).flatMap { entries =>
      if json && !apply then Right(Output.emit(json, CalibreJson.Gaps.of(entries))(_ => Calibre.markdownGaps(entries)))
      else
        val ready   = entries.filter(_.counted)
        val missing = entries.count(_.original == Calibre.GapOriginal.Missing)
        val note    = if missing == 0 then "" else " · " + Calibre.uncountedLine(missing)
        if ready.nonEmpty then println(Calibre.markdownGaps(ready) + "\n")
        if !apply then
          println(s"Dry run: ${ready.size} source(s) would be pushed$note" +
            (if ready.nonEmpty then " Re-run with --apply." else ""))
          Right(())
        else
          val results = ready.map(e => e.gap.citekey -> pushOne(c, e.gap.citekey, None, calibre, originals))
          results.foreach {
            case (ck, Right((id, _))) => println(s"  + $ck → Calibre id $id")
            case (ck, Left(e))        => Console.err.println(s"  ! $ck: ${e.message}")
          }
          val failed = results.count(_._2.isLeft)
          println(s"pushed ${results.size - failed} source(s), $failed failed$note")
          if failed == 0 then Right(())
          else Left(fail(s"$failed of ${results.size} push(es) failed"))
    }

  private val gaps = Opts.subcommand("gaps",
    "List sources with a fetched file that are missing from Calibre; --quiet prints one line and exits 1 when there are any (the reminder hook)") {
    (Opts.flag("quiet", "One line, exit 1 when gaps exist (records with no fetched file never count)").orFalse,
     Opts.option[Int]("limit", "Max gaps to list").orNone,
     Output.jsonFlag).mapN { (quiet, limit, json) => (c: Client) => listGaps(c, quiet, limit, json) }
  }

  /** `calibre gaps`: only a source with an original to push counts — the table, the `--quiet` line
    * and its exit 1. A record with no fetched file is one informational line in the full report and
    * a `counted: false` row in `--json`, never a gap. */
  private[cli] def listGaps(c: Client, quiet: Boolean, limit: Option[Int], json: Boolean,
      originals: Option[String] = Settings.app.cli.calibre.originals): Either[CliError, Unit] =
    classifiedGaps(c, limit, originals).flatMap { entries =>
      val counted = entries.filter(_.counted)
      if json then Right(Output.emit(json, CalibreJson.Gaps.of(entries))(_ => Calibre.markdownGaps(entries)))
      else if !quiet then Right(println(Calibre.markdownGaps(entries)))
      else if counted.isEmpty then Right(())
      else
        // exit 1 with ONE line: this is what the Stop/PostToolUse reminder hook reads
        Left(CliError(
          s"${counted.size} source(s) are not in Calibre yet (${counted.take(3).map(_.gap.citekey).mkString(", ")}" +
            (if counted.size > 3 then ", …" else "") + ") — run `histmersion calibre sync --apply`",
          exitCode = ExitCode.Usage))
    }

  val all: Opts[Client => Either[CliError, Unit]] =
    Opts.subcommand("calibre", "The local Calibre library: search, import, link, push, update, set, audit, sync, gaps") {
      search orElse importCmd orElse link orElse push orElse update orElse set orElse audit orElse sync orElse gaps
    }
