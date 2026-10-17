package com.histmersion.cli

import cats.data.Validated
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

  private def calibre(c: Client): Calibre = Calibre(Settings.app.cli.calibre)

  private def fail(msg: String) = CliError(msg, exitCode = ExitCode.Refused)

  // ── search ──────────────────────────────────────────────────────────────────────────────────

  private val search = Opts.subcommand("search",
    "Search the local Calibre library (read-only); --unlinked lists the entries no source links yet, newest first") {
    (
      Opts.argument[String]("query").orNone,
      Opts.flag("unlinked", "Only entries no source is linked to (the query, if given, narrows them)").orFalse,
      Opts.option[Int]("limit", "Max results").orNone,
      jsonFlag
    ).tupled.mapValidated { (q, unlinked, limit, json) =>
      // an EMPTY query is the whole library (`search "" --limit 2000`, the enrich-calibre audit);
      // only a missing one is refused
      Validated.condNel(q.isDefined || unlinked, (q, unlinked, limit, json), "give a query, or --unlinked")
    }.map { (q, unlinked, limit, json) => (c: Client) =>
      val max = limit.getOrElse(20)
      if !unlinked then
        calibre(c).search(q.getOrElse(""), max).left.map(fail).map { books =>
          if json then println(booksJson(books)) else println(Calibre.markdownSearch(books))
        }
      else
        for
          linked <- linkedIds(c)
          books  <- q.map(_.trim).filter(_.nonEmpty).fold(calibre(c).all())(calibre(c).search(_, Int.MaxValue)).left.map(fail)
        yield
          val all  = Calibre.unlinked(books, linked, Int.MaxValue)
          val page = all.take(max.max(1))
          if json then println(booksJson(page)) else println(Calibre.markdownUnlinked(page, all.size))
    }
  }

  /** The Calibre ids the corpus links, read through the server's read-only query. */
  private def linkedIds(c: Client): Either[CliError, Set[Int]] =
    c.query("SELECT DISTINCT calibre_id FROM sources WHERE calibre_id ~ '^[0-9]+$'").flatMap { q =>
      Either.cond(!q.truncated, q.rows.flatMap(_.headOption.flatten).flatMap(_.toIntOption).toSet,
        fail("too many linked sources for one query — raise query.max-rows"))
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
      Opts.option[Int]("acquire-item", "Id of the acquisition-queue item this book fulfils — ticked off when the conversion succeeds (without it, an open item naming the book's DOI is ticked off, and similar ones are listed)").orNone,
      jsonFlag
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
        _     = if json then println(res.asJson.spaces2)
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
      Opts.option[String]("file", "The file to add (default: the original the server stored; without --replace, <cli.calibre.originals-dir>/<citekey>/paper.* first when present)").orNone,
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
    val one = (Opts.argument[String]("citekey"), jsonFlag).mapN { (ck, json) => (c: Client) =>
      for
        s       <- AcquireClient(c).calibreMeta(ck)
        id      <- s.calibreId.flatMap(_.toIntOption).toRight(fail(
                     s"'$ck' has no Calibre entry yet — use `histmersion calibre push $ck`"))
        changes <- Calibre(Settings.app.cli.calibre).update(id, pushMeta(s)).left.map(fail)
      yield
        if json then println(io.circe.Json.obj("citekey" -> ck.asJson, "calibreId" -> id.asJson,
          "changed" -> changes.map(_.field.name).asJson).spaces2)
        else if changes.isEmpty then println(s"Calibre id $id already agrees with `$ck` — nothing written")
        else println(s"updated Calibre id $id from `$ck` (${changes.map(_.field.name).mkString(", ")}) — ${s.title}")
    }
    val every = (
      Opts.flag("all", "Every Calibre entry a source is linked to (a dry run listing the changes, unless --apply)"),
      Opts.flag("apply", "With --all: write the changes (Calibre GUI must be closed)").orFalse,
      jsonFlag
    ).mapN { (_, apply, json) => (c: Client) =>
      for
        linked  <- linkedRecords(c)
        entries <- calibre(c).updateAll(linked, apply).left.map(fail)
        _        = if json then println(updateJson(entries)) else println(Calibre.markdownUpdateAll(entries, apply))
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

  private def updateJson(entries: List[UpdateEntry]): String =
    entries.map { e =>
      val (state, changes) = e.outcome match
        case UpdateOutcome.Change(cs)     => ("would-change", cs)
        case UpdateOutcome.Applied(cs)    => ("updated", cs)
        case UpdateOutcome.Failed(cs, _)  => ("failed", cs)
        case UpdateOutcome.UpToDate       => ("up-to-date", Nil)
        case UpdateOutcome.Shared         => ("shared", Nil)
        case UpdateOutcome.Missing        => ("missing", Nil)
        case UpdateOutcome.Refused(_)     => ("refused", Nil)
      val reason = e.outcome match
        case UpdateOutcome.Failed(_, r) => Some(r)
        case UpdateOutcome.Refused(r)   => Some(r)
        case _                          => None
      io.circe.Json.obj("calibreId" -> e.calibreId.asJson, "citekeys" -> e.citekeys.asJson, "state" -> state.asJson,
        "changes" -> changes.map(ch => io.circe.Json.obj("field" -> ch.field.name.asJson, "from" -> ch.from.asJson,
          "to" -> ch.to.asJson)).asJson, "reason" -> reason.asJson)
    }.asJson.spaces2

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
      val cfg = Settings.app.cli.calibre
      AcquireClient(c).calibreGaps(limit).flatMap { gaps =>
        if json && !apply then Right(println(gaps.asJson.spaces2))
        else
          // a gap we cannot push is not a failure — neither the server nor this host has its file
          val (ready, missing) = Calibre.pushable(gaps.gaps, Calibre.originalFor(cfg.originals, _))
          println(AcquireMarkdown.calibreGaps(gaps))
          if missing.nonEmpty then
            println(s"\n${missing.size} of ${gaps.count} have no stored original on the server and no file on this host " +
              s"(cli.calibre.originals-dir = ${cfg.originals.getOrElse("<unset>")}) — skipped: " +
              s"${missing.map(_.citekey).mkString(", ")}. Push one with `histmersion calibre push <citekey> --file <path>`.")
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
            exitCode = ExitCode.Usage))
      }
    }
  }

  val all: Opts[Client => Either[CliError, Unit]] =
    Opts.subcommand("calibre", "The local Calibre library: search, import, link, push, update, set, audit, sync, gaps") {
      search orElse importCmd orElse link orElse push orElse update orElse set orElse audit orElse sync orElse gaps
    }
