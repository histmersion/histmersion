package com.histmersion.cli

import cats.data.Validated
import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.api.Api
import com.histmersion.config.*

import java.nio.file.Path
import com.histmersion.api.library.*
import com.histmersion.api.automation.Queue
import com.histmersion.api.library.{
  CalibreGap,
  CalibrePushRequest,
  CollectionFieldValue,
  CollectionImportRequest,
  CollectionLinkRequest,
  CollectionPushRequest,
  CollectionsApi,
  CollectionSetRequest,
  CollectionsMarkdown,
  Ingest,
  MetadataPatch
}
import com.histmersion.api.media.MediaCoversRequest

/** `histmersion calibre search|import|link|push|update|set|audit|sync|gaps`.
  *
  * The server runs them when it has the `calibre` collection and reaches the library (`GET /api/collections/calibre`):
  * then each command is an HTTP client of the library's collections. Otherwise — an older server, the plugin off, a
  * library the server cannot read, a server on another machine — the CLI reads `metadata.db` (read-only) and shells out
  * to `calibredb` here, as it always did, and uses the server only for the corpus side. A command that takes a file on
  * this machine (`push --file`, or a `cli.calibre.originals-dir` copy, which `gaps` and `sync` classify by) always runs
  * here.
  *
  * Nothing in `search`/`gaps`/`audit`/`link` can modify the library. `push` (with or without
  * `--replace`)/`update`/`set`/`sync --apply` are the only writes, and they refuse to run while the Calibre GUI holds
  * the database.
  */
object CalibreCmd:

  private def calibre(c: Client): Calibre = Calibre(Settings.app.cli.calibre)

  private def fail(msg: String) = Cli.Error(msg, exitCode = ExitCode.Refused)

  /** The server's `calibre` collection, when the server runs it (see the object's description). */
  private def server(c: Client): Option[CollectionsClient] =
    Some(CollectionsClient(c, "calibre")).filter(_.onServer)

  // ── search ──────────────────────────────────────────────────────────────────────────────────

  private val search = Opts.subcommand(
    "search",
    "Search the local Calibre library (read-only); --unlinked lists the entries no source links yet, newest first"
  ) {
    (
      Opts.argument[String]("query").orNone,
      Opts.flag("unlinked", "Only entries no source is linked to (the query, if given, narrows them)").orFalse,
      Opts.option[Int]("limit", "Max results").orNone,
      Output.jsonFlag
    ).tupled
      .mapValidated { (q, unlinked, limit, json) =>
        // an EMPTY query is the whole library (`search "" --limit 2000`, the enrich-calibre audit);
        // only a missing one is refused
        Validated.condNel(q.isDefined || unlinked, (q, unlinked, limit, json), "give a query, or --unlinked")
      }
      .map { (q, unlinked, limit, json) => (c: Client) =>
        val max = limit.getOrElse(20)
        server(c) match
          case Some(s) =>
            s.search(if unlinked then q else Some(q.getOrElse("")), Some(max), unlinked).map { r =>
              Output.emit(json, r.entries.map(com.histmersion.cli.Calibre.Json.Book.of(_)))(_ =>
                if unlinked then CollectionsMarkdown.unlinked(r.entries, r.total)
                else CollectionsMarkdown.search(r.entries)
              )
            }
          case None => hostSearch(c, q, unlinked, max, json)
      }
  }

  private def hostSearch(
      c: Client,
      q: Option[String],
      unlinked: Boolean,
      max: Int,
      json: Boolean
  ): Either[Cli.Error, Unit] =
    if !unlinked then
      calibre(c).search(q.getOrElse(""), max).left.map(fail).map { books =>
        Output.emit(json, books.map(com.histmersion.cli.Calibre.Json.Book.of))(_ => Calibre.markdownSearch(books))
      }
    else
      for
        linked <- linkedIds(c)
        books <- q
          .map(_.trim)
          .filter(_.nonEmpty)
          .fold(calibre(c).all())(calibre(c).search(_, Int.MaxValue))
          .left
          .map(fail)
      yield
        val all = Calibre.unlinked(books, linked, Int.MaxValue)
        val page = all.take(max.max(1))
        Output.emit(json, page.map(com.histmersion.cli.Calibre.Json.Book.of))(_ =>
          Calibre.markdownUnlinked(page, all.size)
        )

  /** The Calibre ids the corpus links, read through the server's read-only query. */
  private def linkedIds(c: Client): Either[Cli.Error, Set[Int]] =
    c.query("SELECT DISTINCT calibre_id FROM sources WHERE calibre_id ~ '^[0-9]+$'").flatMap { q =>
      Either.cond(
        !q.truncated,
        q.rows.flatMap(_.headOption.flatten).flatMap(_.toIntOption).toSet,
        fail("too many linked sources for one query — raise query.max-rows")
      )
    }

  // ── import (Calibre → corpus) ───────────────────────────────────────────────────────────────

  private val importCmd = Opts.subcommand(
    "import",
    "Import a purchased book from Calibre into the corpus and convert it (the Calibre original is never touched)"
  ) {
    (
      Opts.argument[Int]("calibre-id"),
      Opts.option[String]("citekey", "Override the generated citekey").orNone,
      Opts.option[String]("format", "pdf | epub").orNone,
      Opts.flag("no-convert", "Create or enrich the record only; do not upload the file for OCR").orFalse,
      Opts.flag("no-wait", "Queue the conversion and return instead of following it").orFalse,
      Opts
        .option[Int](
          "acquire-item",
          "Id of the acquisition-queue item this book fulfils — ticked off when the conversion succeeds (without it, an open item naming the book's DOI is ticked off, and similar ones are listed)"
        )
        .orNone,
      Output.jsonFlag
    ).tupled
      .mapValidated { (id, citekey, format, noConvert, noWait, item, json) =>
        Validated.condNel(
          !(noConvert && item.isDefined),
          (id, citekey, format, noConvert, noWait, item, json),
          "--acquire-item is ticked off by the conversion; drop --no-convert, or tick the item off with `histmersion queue done <id>`"
        )
      }
      .map { (id, citekey, format, noConvert, noWait, item, json) => (c: Client) =>
        server(c) match
          case Some(s) => serverImport(c, s, id, citekey, format, noConvert, noWait, item, json)
          case None    => hostImport(c, id, citekey, format, noConvert, noWait, item, json)
      }
  }

  /** `calibre import` on the server: the record, the cover and the conversion are made there; the lines are printed
    * here, and the conversion followed as `ingest` follows one.
    */
  private def serverImport(
      c: Client,
      s: CollectionsClient,
      id: Int,
      citekey: Option[String],
      format: Option[String],
      noConvert: Boolean,
      noWait: Boolean,
      item: Option[Int],
      json: Boolean
  ): Either[Cli.Error, Unit] =
    s.importItem(CollectionImportRequest(id.toString, citekey, format, !noConvert, !json, item)).flatMap { res =>
      if json then println(Output.json(res.source))
      else
        println(s"Imported Calibre id $id as `${res.citekey}` — ${res.title}")
        res.relinkedFrom.foreach(prev => println(s"Re-linked `${res.citekey}` from Calibre id $prev to $id"))
        println(s"File (left in place, never modified): ${res.file}")
        res.cover.foreach(cv =>
          if cv.warning then Console.err.println(s"warning: cover not stored — ${cv.line}") else println(cv.line)
        )
      if noConvert then Right(())
      else
        res.jobError match
          case Some(e) =>
            val code = if res.jobErrorCode.contains(CollectionsApi.Usage) then ExitCode.Usage else ExitCode.Refused
            Left(Cli.Error(e, exitCode = code))
          case None =>
            res.job.fold(Right(())) { a =>
              println(
                s"queued job ${a.jobId} for ${a.citekey}" +
                  item.fold("")(i => s" — acquisition item $i is ticked off when it succeeds")
              )
              if noWait || json then Right(()) else IngestCmd.pollJob(c, a.jobId, 0)
            }
    }

  private def hostImport(
      c: Client,
      id: Int,
      citekey: Option[String],
      format: Option[String],
      noConvert: Boolean,
      noWait: Boolean,
      item: Option[Int],
      json: Boolean
  ): Either[Cli.Error, Unit] =
    for
      // checked BEFORE the record is written: a typo'd item must not leave a half-done import behind
      _ <- item.fold(Right(()))(i =>
        c.queueList(Queue.Kind.Acquire.label, all = true)
          .flatMap(l =>
            Queue
              .fulfillable(i, l.items.find(_.id == i))
              .left
              .map(e => Cli.Error(e, exitCode = ExitCode.Usage))
              .map(_ => ())
          )
      )
      book <- calibre(c)
        .byId(id)
        .left
        .map(fail)
        .flatMap(_.toRight(fail(s"no Calibre book with id $id")))
      _ <- Calibre.guard(book.title, book.authors).left.map(e => fail(s"Calibre id $id has $e"))
      file <- book
        .pick(format)
        .toRight(
          fail(
            s"Calibre id $id has no pdf/epub (formats: ${book.formats.mkString(", ")}); " +
              "KFX/MOBI must be converted in Calibre first"
          )
        )
      ck = citekey.getOrElse(Calibre.citekeyFor(book))
      existing <- AcquireClient(c).calibreMeta(ck) match
        case Right(m)                                                                       => Right(Some(m.calibreId))
        case Left(e) if e.exitCode == ExitCode.Refused && e.message.startsWith("No source") => Right(None)
        case Left(e)                                                                        => Left(e)
      target <- Calibre
        .importTarget(ck, id, citekey.isDefined, existing)
        .left
        .map(m => Cli.Error(m, exitCode = ExitCode.Usage))
      res <- upsert(c, ck, book, target)
      _ =
        if json then println(Output.json(res))
        else
          println(s"Imported Calibre id $id as `$ck` — ${book.title}")
          target match
            case ImportTarget.Update(Some(prev)) => println(s"Re-linked `$ck` from Calibre id $prev to $id")
            case _                               => ()
          println(s"File (left in place, never modified): $file")
      // the book's cover for its hover card (#1446): Calibre's own, else Open Library's by its ISBN —
      // best-effort, a missing cover never fails the import
      _ = if !json then importCover(c, ck, book)
      // the conversion is the same upload `histmersion ingest <file>` makes, marked as Calibre's
      _ <-
        if noConvert then Right(())
        else
          IngestCmd.upload(
            c,
            Path.of(file),
            ck,
            quality = false,
            force = false,
            title = None,
            authors = Nil,
            year = None,
            doi = None,
            noWait = noWait || json,
            provenance = Some("calibre"),
            acquireItem = item
          )
    yield ()

  /** The imported book's cover, best-effort: a line saying what happened, never a failure. */
  private def importCover(c: Client, ck: String, book: com.histmersion.cli.Calibre.Book): Unit =
    MediaCovers.one(c, ck, book, dryRun = false) match
      case MediaCovers.Outcome.Uploaded(_)             => println(s"Cover: from the Calibre library")
      case MediaCovers.Outcome.AskOpenLibrary(_, isbn) =>
        c.mediaCovers(
          MediaCoversRequest(dryRun = false, citekey = Some(ck), illustrations = false, isbn = Some(isbn))
        ) match
          case Right(r) if r.covers > 0 => println(s"Cover: from Open Library (ISBN $isbn)")
          case _                        => println(s"Cover: none (no cover file in Calibre, none at Open Library)")
      case MediaCovers.Outcome.Skipped(why) => println(s"Cover: none ($why)")
      case MediaCovers.Outcome.Failed(why)  => Console.err.println(s"warning: cover not stored — $why")

  /** Create the source, or enrich it if the citekey already exists — a re-import must never wipe the findings and notes
    * an agent compiled. Either way the record ends up linked to THIS entry (#721: an import into an existing record
    * used to keep its old Calibre id).
    */
  private def upsert(
      c: Client,
      ck: String,
      b: com.histmersion.cli.Calibre.Book,
      target: ImportTarget
  ): Either[Cli.Error, Source.WithFindings] =
    val summary = b.comments.map(stripHtml).filter(_.nonEmpty).map(_.take(500))
    target match
      case ImportTarget.Update(_) =>
        for
          current <- c.call(Api.getSource)((ck, false))
          res <- c.patchSource(
            ck,
            Source.Patch(
              metadata = Some(
                MetadataPatch(
                  title = Some(b.title),
                  authors = Some(b.authors),
                  year = b.year,
                  venue = b.publisher,
                  doi = b.doi,
                  calibreId = Some(b.id.toString)
                )
              ),
              summary = Calibre.importSummary(summary, current.source.summary)
            )
          )
        yield res
      case ImportTarget.Create =>
        c.putSource(
          ck,
          Source.Put(
            title = b.title,
            authors = b.authors,
            doi = b.doi,
            year = b.year,
            venue = b.publisher,
            status = Some(com.histmersion.domain.library.Source.Status.Unassessed),
            tags = b.tags,
            summary = summary,
            provenance = Some("calibre"),
            calibreId = Some(b.id.toString)
          )
        )

  // ── link (re-point a record at another Calibre entry) ────────────────────────────────────────

  private val link = Opts.subcommand(
    "link",
    "Link a source to an existing Calibre entry (checks the entry exists; Calibre itself is not changed)"
  ) {
    (Opts.argument[String]("citekey"), Opts.argument[Int]("calibre-id"), Output.jsonFlag).mapN {
      (ck, id, json) => (c: Client) =>
        server(c) match
          case Some(s) =>
            s.link(CollectionLinkRequest(ck, id.toString))
              .map(l =>
                Output.emit(json, com.histmersion.cli.Calibre.Json.Linked(l.citekey, id))(_ =>
                  s"linked `${l.citekey}` to Calibre id $id — ${l.title}"
                )
              )
          case None =>
            for
              book <- calibre(c).byId(id).left.map(fail).flatMap(_.toRight(fail(s"no Calibre book with id $id")))
              sw <- c.patchSource(ck, Source.Patch(metadata = Some(MetadataPatch(calibreId = Some(id.toString)))))
            yield Output.emit(json, com.histmersion.cli.Calibre.Json.Linked(sw.source.citekey, id))(l =>
              s"linked `${l.citekey}` to Calibre id $id — ${book.title}"
            )
    }
  }

  private def stripHtml(s: String): String = s.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim

  // ── push / sync (corpus → Calibre) ──────────────────────────────────────────────────────────

  private val push = Opts.subcommand(
    "push",
    "Add a stored source's file to Calibre with title/authors/DOI/tags; --replace swaps the file of its linked entry instead (Calibre GUI must be closed)"
  ) {
    (
      Opts.argument[String]("citekey"),
      Opts
        .option[String](
          "file",
          "The file to add (default: the original the server stored; without --replace, <cli.calibre.originals-dir>/<citekey>/paper.* first when present)"
        )
        .orNone,
      Opts
        .flag("replace", "Replace the file of the source's LINKED Calibre entry instead of adding a new entry")
        .orFalse,
      Output.jsonFlag
    ).mapN { (ck, file, replace, json) => (c: Client) =>
      val onHost = file.isDefined || (!replace && Calibre.originalFor(Settings.app.cli.calibre.originals, ck).isDefined)
      server(c).filter(_ => !onHost) match
        case Some(s) =>
          s.push(CollectionPushRequest(ck, replace)).map { p =>
            p.replaced match
              case Some(fmt) =>
                Output.emit(json, com.histmersion.cli.Calibre.Json.Replaced(ck, p.id.toIntOption.getOrElse(0), fmt))(
                  _ => s"replaced the ${fmt.toUpperCase} of Calibre id ${p.id} with `$ck`'s original"
                )
              case None =>
                Output.emit(json, com.histmersion.cli.Calibre.Json.Linked(ck, p.id.toIntOption.getOrElse(0)))(_ =>
                  s"added `$ck` to Calibre as id ${p.id} — ${p.title}"
                )
          }
        case None => hostPush(c, ck, file, replace, json)
    }
  }

  private def hostPush(c: Client, ck: String, file: Option[String], replace: Boolean, json: Boolean) =
    if replace then
      replaceOne(c, ck, file.map(Path.of(_))).map { case (id, fmt) =>
        Output.emit(json, com.histmersion.cli.Calibre.Json.Replaced(ck, id, fmt))(_ =>
          s"replaced the ${fmt.toUpperCase} of Calibre id $id with `$ck`'s original"
        )
      }
    else
      pushOne(c, ck, file.map(Path.of(_))).map { case (id, title) =>
        Output.emit(json, com.histmersion.cli.Calibre.Json.Linked(ck, id))(_ =>
          s"added `$ck` to Calibre as id $id — $title"
        )
      }

  /** `push --replace` (#652): the file goes into the entry the source is ALREADY linked to, so the link stays valid and
    * no duplicate entry appears. The file is `--file`, else the original the server stored at ingest, downloaded to a
    * temporary directory and removed afterwards.
    */
  private def replaceOne(c: Client, ck: String, file: Option[Path]): Either[Cli.Error, (Int, String)] =
    for
      s <- AcquireClient(c).calibreMeta(ck)
      id <- s.calibreId
        .flatMap(_.trim.toIntOption)
        .toRight(
          fail(
            s"'$ck' is not linked to a Calibre entry — `histmersion calibre push $ck` adds one, " +
              s"`histmersion calibre link $ck <id>` links an existing one"
          )
        )
      fmt <- file match
        case Some(f) => Calibre(Settings.app.cli.calibre).replaceFormat(id, f).left.map(fail)
        case None    => withOriginal(c, ck)(f => Calibre(Settings.app.cli.calibre).replaceFormat(id, f).left.map(fail))
    yield (id, fmt)

  /** Download the server's stored original of `ck` as `<tmp>/<ck>.<ext>` (the extension is what tells Calibre the
    * format), run `f` on it, and delete it whatever happens.
    */
  private def withOriginal[A](c: Client, ck: String, hint: String = "")(
      f: Path => Either[Cli.Error, A]
  ): Either[Cli.Error, A] =
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
      try s.forEach(p => java.nio.file.Files.deleteIfExists(p): Unit)
      finally s.close()
      java.nio.file.Files.deleteIfExists(dir): Unit

  /** One push: read the source's metadata from the SERVER, take the file ([[Calibre.pushFile]]: `--file`, else
    * `<originals-dir>/<citekey>/paper.*` on this host, else the original the server stored, downloaded to a temporary
    * directory), `calibredb add`, then record the new id back in the corpus so it stops counting as a gap.
    */
  private[cli] def pushOne(
      c: Client,
      ck: String,
      file: Option[Path],
      calibre: Calibre = Calibre(Settings.app.cli.calibre),
      originals: Option[String] = Settings.app.cli.calibre.originals
  ): Either[Cli.Error, (Int, String)] =
    for
      // the authors must come back as a LIST: "Stocker, Sharon R." is ONE author, so the
      // comma-joined `SourceDetail.authors` cannot be split apart again
      s <- AcquireClient(c).calibreMeta(ck)
      add = (p: Path) => calibre.add(p, pushMeta(s)).left.map(fail)
      id <- Calibre.pushFile(file, Calibre.originalFor(originals, ck)) match
        case Calibre.PushFile.Given(p) => add(p)
        case Calibre.PushFile.Local(p) => add(p)
        case Calibre.PushFile.Server   =>
          withOriginal(
            c,
            ck,
            " — or set cli.calibre.originals-dir (HISTMERSION_CALIBRE_ORIGINALS_DIR) " +
              "to a directory holding <citekey>/paper.pdf"
          )(add)
      _ <- AcquireClient(c).calibrePushed(CalibrePushRequest(ck, id.toString))
    yield (id, s.title)

  private val update = Opts.subcommand(
    "update",
    "Rewrite a source's Calibre entry from the corpus: title, authors, year, identifiers, tags (and publisher/comments where empty); --all for every linked entry (Calibre GUI must be closed)"
  ) {
    def hostUpdate(c: Client, ck: String, json: Boolean) =
      for
        s <- AcquireClient(c).calibreMeta(ck)
        id <- s.calibreId
          .flatMap(_.toIntOption)
          .toRight(fail(s"'$ck' has no Calibre entry yet — use `histmersion calibre push $ck`"))
        changes <- Calibre(Settings.app.cli.calibre).update(id, pushMeta(s)).left.map(fail)
      yield Output.emit(json, com.histmersion.cli.Calibre.Json.Updated(ck, id, changes.map(_.field.name)))(u =>
        if u.changed.isEmpty then s"Calibre id $id already agrees with `$ck` — nothing written"
        else s"updated Calibre id $id from `$ck` (${u.changed.mkString(", ")}) — ${s.title}"
      )
    def hostUpdateAll(c: Client, apply: Boolean, json: Boolean) =
      for
        linked <- linkedRecords(c)
        entries <- calibre(c).updateAll(linked, apply).left.map(fail)
        _ = Output.emit(json, entries.map(com.histmersion.cli.Calibre.Json.UpdateRow.of))(_ =>
          Calibre.markdownUpdateAll(entries, apply)
        )
        failed = entries.count(_.outcome match { case UpdateOutcome.Failed(_, _) => true; case _ => false })
        _ <- Either.cond(failed == 0, (), fail(s"$failed Calibre update(s) failed"))
      yield ()
    val one = (Opts.argument[String]("citekey"), Output.jsonFlag).mapN { (ck, json) => (c: Client) =>
      server(c) match
        case Some(srv) =>
          srv.updateOne(ck).map { u =>
            Output.emit(json, com.histmersion.cli.Calibre.Json.Updated(ck, u.id.toIntOption.getOrElse(0), u.changed))(
              x =>
                if x.changed.isEmpty then s"Calibre id ${u.id} already agrees with `$ck` — nothing written"
                else s"updated Calibre id ${u.id} from `$ck` (${x.changed.mkString(", ")}) — ${u.title}"
            )
          }
        case None => hostUpdate(c, ck, json)
    }
    val every = (
      Opts.flag("all", "Every Calibre entry a source is linked to (a dry run listing the changes, unless --apply)"),
      Opts.flag("apply", "With --all: write the changes (Calibre GUI must be closed)").orFalse,
      Output.jsonFlag
    ).mapN { (_, apply, json) => (c: Client) =>
      server(c) match
        case Some(srv) =>
          for
            u <- srv.updateAll(apply)
            _ = Output.emit(json, u.rows.map(com.histmersion.cli.Calibre.Json.UpdateRow.of(_)))(_ =>
              CollectionsMarkdown.updateAll(u.rows, apply)
            )
            failed = u.rows.count(_.state == "failed")
            _ <- Either.cond(failed == 0, (), fail(s"$failed Calibre update(s) failed"))
          yield ()
        case None => hostUpdateAll(c, apply, json)
    }
    one orElse every
  }

  /** Every source linked to a Calibre entry, with what it would send there. One query for the list, then the push
    * metadata per source (the authors must come back as a LIST — see [[pushOne]]).
    */
  private def linkedRecords(c: Client): Either[Cli.Error, List[LinkedRecord]] =
    for
      q <- c.query("SELECT citekey, calibre_id FROM sources WHERE calibre_id ~ '^[0-9]+$' ORDER BY citekey")
      _ <- Either.cond(!q.truncated, (), fail("too many linked sources for one query — raise query.max-rows"))
      keys = q.rows.collect { case List(Some(ck), Some(id)) => ck -> id.toInt }
      recs <- keys.traverse((ck, id) => AcquireClient(c).calibreMeta(ck).map(s => LinkedRecord(ck, id, pushMeta(s))))
    yield recs

  /** What the corpus record sends into Calibre, for `push` and `update` alike. */
  private def pushMeta(s: CalibreGap): com.histmersion.cli.Calibre.Push =
    com.histmersion.cli.Calibre.Push(
      title = s.title,
      authors = s.authors,
      doi = s.doi,
      isbn = s.isbn,
      year = s.year,
      publisher = s.venue,
      summary = s.summary.map(_.take(500)),
      tags = s.topics
    )

  private val set = Opts.subcommand(
    "set",
    s"Set fields on a Calibre entry, e.g. --field languages:eng (settable: ${Calibre.SettableFields.mkString(", ")}; Calibre GUI must be closed)"
  ) {
    (
      Opts.argument[Int]("calibre-id"),
      Opts.options[String]("field", "<name>:<value>, repeatable").map(_.toList)
    ).mapN { (id, raw) => (c: Client) =>
      raw
        .traverse(f =>
          f.split(":", 2) match
            case Array(k, v) if k.trim.nonEmpty => Right(k.trim -> v)
            case _                              => Left(fail(s"'$f' is not <name>:<value>"))
        )
        .flatMap(fields =>
          server(c) match
            case Some(s) =>
              s.set(CollectionSetRequest(id.toString, fields.map(CollectionFieldValue(_, _)))).map(_ => ())
            case None => calibre(c).set(id, fields).left.map(fail)
        )
        .map(_ => println(s"set ${raw.map(_.takeWhile(_ != ':')).mkString(", ")} on Calibre id $id"))
    }
  }

  private val audit = Opts.subcommand(
    "audit",
    "Compare every source's Calibre entry with the corpus record (title, authors, year) — read-only"
  ) {
    Output.jsonFlag.map { json => (c: Client) =>
      server(c) match
        case Some(s) =>
          s.audit.map(a =>
            Output.emit(json, a.findings.map(com.histmersion.cli.Calibre.Json.AuditRow.of(_)))(_ =>
              CollectionsMarkdown.audit(a)
            )
          )
        case None => hostAudit(c, json)
    }
  }

  private def hostAudit(c: Client, json: Boolean): Either[Cli.Error, Unit] =
    for
      q <- c.query(
        "SELECT citekey, calibre_id, title, array_to_string(authors, '|') AS authors, year " +
          "FROM sources WHERE calibre_id ~ '^[0-9]+$' ORDER BY citekey"
      )
      _ <- Either.cond(!q.truncated, (), fail("too many linked sources for one query — raise query.max-rows"))
      recs = q.rows.flatMap {
        case List(Some(ck), Some(id), title, authors, year) =>
          Some(
            AuditRecord(
              ck,
              id.toInt,
              title.getOrElse(""),
              authors.toList.flatMap(_.split('|')).map(_.trim).filter(_.nonEmpty),
              year.flatMap(_.toIntOption)
            )
          )
        case _ => None
      }
      books <- calibre(c).all().left.map(fail)
    yield
      val found = com.histmersion.cli.Calibre.Audit.audit(recs, books.map(b => b.id -> b).toMap)
      Output.emit(json, found.map(com.histmersion.cli.Calibre.Json.AuditRow.of))(_ =>
        com.histmersion.cli.Calibre.Audit.markdown(found, recs.map(_.calibreId).distinct.size)
      )

  /** The server's gap listing, each with where its original is and whether it counts ([[Calibre.classify]]): a record
    * with no fetched file anywhere is not a Calibre gap.
    */
  private def classifiedGaps(
      c: Client,
      limit: Option[Int],
      originals: Option[String]
  ): Either[Cli.Error, List[Calibre.GapEntry]] =
    AcquireClient(c).calibreGaps(limit).map(g => Calibre.classify(g.gaps, Calibre.originalFor(originals, _)))

  private val sync = Opts.subcommand(
    "sync",
    "Push every source with a fetched file that is not in Calibre yet (dry run unless --apply)"
  ) {
    (
      Opts.flag("apply", "Actually add them (Calibre GUI must be closed)").orFalse,
      Opts.option[Int]("limit", "Only consider this many gaps").orNone,
      Output.jsonFlag
    ).mapN((apply, limit, json) =>
      (c: Client) =>
        onServerUnlessLocalOriginals(c).fold(syncGaps(c, apply, limit, json))(serverSync(_, apply, limit, json))
    )
  }

  /** `calibre sync`: push the COUNTED gaps (an original on this host or on the server); the records with no fetched
    * file are not gaps, so they are neither pushed nor reported as skipped — one summary line says how many there are.
    */
  private[cli] def syncGaps(
      c: Client,
      apply: Boolean,
      limit: Option[Int],
      json: Boolean,
      calibre: Calibre = Calibre(Settings.app.cli.calibre),
      originals: Option[String] = Settings.app.cli.calibre.originals
  ): Either[Cli.Error, Unit] =
    classifiedGaps(c, limit, originals).flatMap { entries =>
      if json && !apply then
        Right(Output.emit(json, com.histmersion.cli.Calibre.Json.Gaps.of(entries))(_ => Calibre.markdownGaps(entries)))
      else
        val ready = entries.filter(_.counted)
        val missing = entries.count(_.original == Calibre.GapOriginal.Missing)
        val note = if missing == 0 then "" else " · " + Calibre.uncountedLine(missing)
        if ready.nonEmpty then println(Calibre.markdownGaps(ready) + "\n")
        if !apply then
          println(
            s"Dry run: ${ready.size} source(s) would be pushed$note" +
              (if ready.nonEmpty then " Re-run with --apply." else "")
          )
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

  /** The server's collection for `gaps` and `sync`, unless this machine keeps originals of its own
    * (`cli.calibre.originals-dir`), which only the CLI can see and classify by.
    */
  private def onServerUnlessLocalOriginals(c: Client): Option[CollectionsClient] =
    if Settings.app.cli.calibre.originals.isDefined then None else server(c)

  /** `calibre sync` on the server: the same lines as [[syncGaps]], the pushes made there. */
  private def serverSync(
      s: CollectionsClient,
      apply: Boolean,
      limit: Option[Int],
      json: Boolean
  ): Either[Cli.Error, Unit] =
    s.sync(CollectionSync.Request(apply, limit)).flatMap { r =>
      if json && !apply then
        Right(
          Output.emit(json, com.histmersion.cli.Calibre.Json.Gaps.of(r.gaps))(_ =>
            CollectionsMarkdown.gaps(r.gaps.gaps)
          )
        )
      else
        val ready = r.gaps.gaps.filter(_.counted)
        val missing = r.gaps.gaps.count(_.original == "none")
        val note = if missing == 0 then "" else " · " + CollectionsMarkdown.uncountedLine(missing)
        if ready.nonEmpty then println(CollectionsMarkdown.gaps(ready) + "\n")
        if !apply then
          println(
            s"Dry run: ${ready.size} source(s) would be pushed$note" +
              (if ready.nonEmpty then " Re-run with --apply." else "")
          )
          Right(())
        else
          r.pushes.foreach(p =>
            p.id match
              case Some(id) => println(s"  + ${p.citekey} → Calibre id $id")
              case None     => Console.err.println(s"  ! ${p.citekey}: ${p.error.getOrElse("")}")
          )
          val failed = r.pushes.count(_.id.isEmpty)
          println(s"pushed ${r.pushes.size - failed} source(s), $failed failed$note")
          if failed == 0 then Right(())
          else Left(fail(s"$failed of ${r.pushes.size} push(es) failed"))
    }

  /** `calibre gaps` on the server: the same table, `--quiet` line and exit codes as [[listGaps]]. */
  private def serverGaps(
      s: CollectionsClient,
      quiet: Boolean,
      limit: Option[Int],
      json: Boolean
  ): Either[Cli.Error, Unit] =
    s.gaps(limit).flatMap { g =>
      val counted = g.gaps.filter(_.counted)
      if json then
        Right(Output.emit(json, com.histmersion.cli.Calibre.Json.Gaps.of(g))(_ => CollectionsMarkdown.gaps(g.gaps)))
      else if !quiet then Right(println(CollectionsMarkdown.gaps(g.gaps)))
      else if counted.isEmpty then Right(())
      else
        Left(
          Cli.Error(
            s"${counted.size} source(s) are not in Calibre yet (${counted.take(3).map(_.citekey).mkString(", ")}" +
              (if counted.size > 3 then ", …" else "") + ") — run `histmersion calibre sync --apply`",
            exitCode = ExitCode.Usage
          )
        )
    }

  private val gaps = Opts.subcommand(
    "gaps",
    "List sources with a fetched file that are missing from Calibre; --quiet prints one line and exits 1 when there are any (the reminder hook)"
  ) {
    (
      Opts.flag("quiet", "One line, exit 1 when gaps exist (records with no fetched file never count)").orFalse,
      Opts.option[Int]("limit", "Max gaps to list").orNone,
      Output.jsonFlag
    ).mapN((quiet, limit, json) =>
      (c: Client) =>
        onServerUnlessLocalOriginals(c).fold(listGaps(c, quiet, limit, json))(serverGaps(_, quiet, limit, json))
    )
  }

  /** `calibre gaps`: only a source with an original to push counts — the table, the `--quiet` line and its exit 1. A
    * record with no fetched file is one informational line in the full report and a `counted: false` row in `--json`,
    * never a gap.
    */
  private[cli] def listGaps(
      c: Client,
      quiet: Boolean,
      limit: Option[Int],
      json: Boolean,
      originals: Option[String] = Settings.app.cli.calibre.originals
  ): Either[Cli.Error, Unit] =
    classifiedGaps(c, limit, originals).flatMap { entries =>
      val counted = entries.filter(_.counted)
      if json then
        Right(Output.emit(json, com.histmersion.cli.Calibre.Json.Gaps.of(entries))(_ => Calibre.markdownGaps(entries)))
      else if !quiet then Right(println(Calibre.markdownGaps(entries)))
      else if counted.isEmpty then Right(())
      else
        // exit 1 with ONE line: this is what the Stop/PostToolUse reminder hook reads
        Left(
          Cli.Error(
            s"${counted.size} source(s) are not in Calibre yet (${counted.take(3).map(_.gap.citekey).mkString(", ")}" +
              (if counted.size > 3 then ", …" else "") + ") — run `histmersion calibre sync --apply`",
            exitCode = ExitCode.Usage
          )
        )
    }

  val all: Opts[Client => Either[Cli.Error, Unit]] =
    Opts.subcommand("calibre", "The local Calibre library: search, import, link, push, update, set, audit, sync, gaps") {
      search orElse importCmd orElse link orElse push orElse update orElse set orElse audit orElse sync orElse gaps
    }
