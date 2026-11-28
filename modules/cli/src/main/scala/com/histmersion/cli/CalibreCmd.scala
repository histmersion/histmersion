package com.histmersion
package cli
import com.histmersion.cli.AllOrFirst.traverse
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.{sentence, sentences}

import hearth.kindlings.optics.syntax.*

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
import com.histmersion.util.console.Console
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.Paging
import com.histmersion.api.library.Query
import com.histmersion.api.library.Source
import com.histmersion.api.library.RepairAcquire
import com.histmersion.config.CalibreCliConfig
import com.histmersion.api.library.Title
import com.histmersion.api.library.CollectionSync
import com.histmersion.api.library.CollectionUpdateAll
import com.histmersion.api.library.AuthorName
import com.histmersion.api.library.CollectionEntry
import com.histmersion.api.library.CollectionAudit
import com.histmersion.api.library.AcquireApi
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

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
@tolerated(LintRule.G9PrefixRole, "pending G9 (namespaces, not prefixes)")
@atBoundary(BoundaryKind.CliArgs, "parses `histmersion calibre` arguments")
object CalibreCmd:

  enum Unlinked derives CanEqual:
    case OnlyUnlinked, Every
  object Unlinked:
    extension (unlinked: Unlinked) def onlyUnlinked: Boolean = unlinked == OnlyUnlinked
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def calibre(c: Client)(using console: Console): Calibre = Calibre(Settings.app.cli.calibre)

  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  private def fail(msg: String): Cli.Error = Cli.Error(Problem.Unclassified(Legacy.Text(msg)))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def failProblem(p: Problem): Cli.Error = Cli.Error(p)

  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  private def truncated: Cli.Error =
    Cli.Error.answered(
      Problem.Unclassified(Legacy.Text("too many linked sources for one query — raise query.max-rows"))
    )

  private val CalibreBook = Problem.TypeName("Calibre book")

  /** The server's `calibre` collection, when the server runs it (see the object's description). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def server(c: Client): Option[CollectionsClient] =
    Some(CollectionsClient(c, CollectionsClient.Name("calibre"))).filter(_.onServer)

  // ── search ──────────────────────────────────────────────────────────────────────────────────

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private def hostSearch(
      c: Client,
      q: Option[Api.Search.Text],
      unlinked: Unlinked,
      max: Paging.Limit,
      json: Cli.JsonOutput
  )(using console: Console): Either[Cli.Error, Unit] =
    if !unlinked.onlyUnlinked then
      calibre(c).search(q.getOrElse(Api.Search.Text("")), max).left.map(failProblem).map { books =>
        Output.emit(json, books.map(Calibre.Json.Book.of))(_ => Calibre.markdownSearch(books))
      }
    else
      for
        linked <- linkedIds(c)
        books <- q
          .map(_.value.trim)
          .filter(_.nonEmpty)
          .map(Api.Search.Text(_))
          .fold(calibre(c).all())(calibre(c).search(_, Paging.Limit(Int.MaxValue)))
          .left
          .map(failProblem)
      yield
        val all = Calibre.unlinked(books, linked, Paging.Limit(Int.MaxValue))
        val page = all.take(max.value.max(1))
        Output.emit(json, page.map(Calibre.Json.Book.of))(_ =>
          Calibre.markdownUnlinked(page, Paging.Page.Total(all.size))
        )

  /** The Calibre ids the corpus links, read through the server's read-only query. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private def linkedIds(c: Client): Either[Cli.Error, Set[Calibre.Book.Id]] =
    c.query(Query.Statement("SELECT DISTINCT calibre_id FROM sources WHERE calibre_id ~ '^[0-9]+$'")).flatMap { q =>
      Either.cond(
        !q.truncated.truncated,
        q.rows.flatMap(_.headOption.flatten).flatMap(_.value.toIntOption).map(Calibre.Book.Id(_)).toSet,
        truncated
      )
    }

  // ── import (Calibre → corpus) ───────────────────────────────────────────────────────────────

  /** `calibre import` on the server: the record, the cover and the conversion are made there; the lines are printed
    * here, and the conversion followed as `ingest` follows one.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private def serverImport(
      c: Client,
      s: CollectionsClient,
      id: Calibre.Book.Id,
      citekey: Option[Citekey],
      format: Option[CollectionImportRequest.Format],
      noConvert: Boolean,
      following: Cli.Following,
      item: Option[Queue.QueueItem.Id],
      json: Cli.JsonOutput
  )(using console: Console): Either[Cli.Error, Unit] =
    s.importItem(
      CollectionImportRequest(
        CollectionImportRequest.Id(id.value.toString),
        citekey,
        format,
        (if !noConvert then CollectionImportRequest.Convert.Convert else CollectionImportRequest.Convert.AsIs),
        (if !json.asJson then CollectionImportRequest.Cover.Cover else CollectionImportRequest.Cover.NoCover),
        item.map(i => CollectionImportRequest.AcquireItem(i.value))
      )
    ).flatMap { res =>
      if json.asJson then Cli.out.println(Output.json(res.source))
      else
        Cli.out.println(shown"Imported Calibre id $id as `${res.citekey}` — ${res.title}")
        res.relinkedFrom
          .foreach(prev => Cli.out.println(shown"Re-linked `${res.citekey}` from Calibre id $prev to $id"))
        Cli.out.println(shown"File (left in place, never modified): ${res.file}")
        res.cover.foreach(cv =>
          if cv.warning.warning then Cli.err.println(shown"warning: cover not stored — ${cv.line}")
          else Cli.out.println(cv.line)
        )
      if noConvert then Right(())
      else
        res.jobProblems match
          case Some(e) => Left(Cli.Error.answered(e))
          case None    =>
            res.job.fold(Right(())) { a =>
              Cli.out.println(
                s"queued job ${a.jobId} for ${a.citekey}" +
                  item.fold("")(i => shown" — acquisition item $i is ticked off when it succeeds")
              )
              json.choose(Cli.Following.Return, following).follow(IngestCmd.pollJob(c, a.jobId, 0))
            }
    }

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private def hostImport(
      c: Client,
      id: Calibre.Book.Id,
      citekey: Option[Citekey],
      format: Option[Calibre.Book.Format],
      noConvert: Boolean,
      following: Cli.Following,
      item: Option[Queue.QueueItem.Id],
      json: Cli.JsonOutput
  )(using console: Console): Either[Cli.Error, Unit] =
    for
      // checked BEFORE the record is written: a typo'd item must not leave a half-done import behind
      _ <- item.fold(Right(()))(i =>
        c.queueList(Queue.Wire(Queue.Kind.Acquire.label.value), all = true)
          .flatMap(l =>
            Queue
              .fulfillable(i, l.items.find(_.id == i))
              .left
              .map(e => Cli.Error(e))
              .map(_ => ())
          )
      )
      book <- calibre(c)
        .byId(id)
        .left
        .map(failProblem)
        .flatMap(_.toRight(Cli.Error(Problem.NotFound.Entity(CalibreBook, Problem.Raw.of(id)))))
      _ <- Calibre.guard(book.title, book.authors).left.map(e => fail(s"Calibre id $id has ${e.sentence}"))
      file <- book
        .pick(format)
        .toRight(
          fail(
            shown"Calibre id $id has no pdf/epub (formats: ${book.formats.mkString(", ")}); " +
              "KFX/MOBI must be converted in Calibre first"
          )
        )
      ck = citekey.getOrElse(Calibre.citekeyFor(book))
      existing <- AcquireClient(c).calibreMeta(ck) match
        case Right(m)              => Right(Some(m.calibreId))
        case Left(e) if e.notFound => Right(None)
        case Left(e)               => Left(e)
      target <- Calibre
        .importTarget(
          ck,
          id,
          (if citekey.isDefined then ImportTarget.Explicit.Asked else ImportTarget.Explicit.Generated),
          existing
        )
        .left
        .map(m => Cli.Error(m))
      res <- upsert(c, ck, book, target)
      _ =
        if json.asJson then Cli.out.println(Output.json(res))
        else
          Cli.out.println(shown"Imported Calibre id $id as `$ck` — ${book.title}")
          target match
            case ImportTarget.Update(Some(prev)) => Cli.out.println(s"Re-linked `$ck` from Calibre id $prev to $id")
            case _                               => ()
          Cli.out.println(shown"File (left in place, never modified): $file")
      // the book's cover for its hover card (#1446): Calibre's own, else Open Library's by its ISBN —
      // best-effort, a missing cover never fails the import
      _ = if !json.asJson then importCover(c, ck, book)
      // the conversion is the same upload `histmersion ingest <file>` makes, marked as Calibre's
      _ <-
        if noConvert then Right(())
        else
          IngestCmd.upload(
            c,
            Path.of(file.value),
            ck,
            quality = Ingest.Params.Quality.Fast,
            force = Ingest.Params.Force.Safe,
            title = None,
            authors = Nil,
            year = None,
            doi = None,
            following = json.choose(Cli.Following.Return, following),
            provenance = Some(Source.Provenance("calibre")),
            acquireItem = item.map(i => Ingest.Params.AcquireItem(i.value))
          )
    yield ()

  /** The imported book's cover, best-effort: a line saying what happened, never a failure. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private def importCover(c: Client, ck: Citekey, book: Calibre.Book)(using console: Console): Unit =
    MediaCovers.one(c, ck, book, dryRun = RepairAcquire.RepairRequest.DryRun.Apply) match
      case MediaCovers.Outcome.Uploaded(_)             => Cli.out.println(s"Cover: from the Calibre library")
      case MediaCovers.Outcome.AskOpenLibrary(_, isbn) =>
        c.mediaCovers(
          MediaCoversRequest(
            dryRun = RepairAcquire.RepairRequest.DryRun.Apply,
            citekey = Some(ck),
            illustrations = MediaCoversRequest.Illustrations.Skipped,
            isbn = Some(isbn)
          )
        ) match
          case Right(r) if r.covers.value > 0 => Cli.out.println(s"Cover: from Open Library (ISBN $isbn)")
          case _ => Cli.out.println(s"Cover: none (no cover file in Calibre, none at Open Library)")
      case MediaCovers.Outcome.Skipped(why) => Cli.out.println(s"Cover: none (${why.sentence})")
      case MediaCovers.Outcome.Failed(why)  => Cli.err.println(s"warning: cover not stored — $why")

  /** Create the source, or enrich it if the citekey already exists — a re-import must never wipe the findings and notes
    * an agent compiled. Either way the record ends up linked to THIS entry (#721: an import into an existing record
    * used to keep its old Calibre id).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private def upsert(
      c: Client,
      ck: Citekey,
      b: Calibre.Book,
      target: ImportTarget
  ): Either[Cli.Error, Source.WithFindings] =
    val summary = b.comments.map(cm => stripHtml(cm.value)).filter(_.nonEmpty).map(t => Source.Summary(t.take(500)))
    target match
      case ImportTarget.Update(_) =>
        for
          current <- c.call(Api.getSource)((ck, Api.SourceViewQuery.FullText.SummaryOnly))
          res <- c.patchSource(
            ck,
            Source.Patch(
              metadata = Some(
                MetadataPatch(
                  title = Some(b.title),
                  authors = Some(b.authors),
                  year = b.year,
                  venue = b.publisher.map(pb => Source.Venue(pb.value)),
                  doi = b.doi,
                  calibreId = Some(Source.CalibreId(b.id.value.toString))
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
            venue = b.publisher.map(pb => Source.Venue(pb.value)),
            status = Some(Source.Status.Unassessed),
            tags = b.tags.map(t => Source.Tags(t.value)),
            summary = summary,
            provenance = Some(Source.Provenance("calibre")),
            calibreId = Some(Source.CalibreId(b.id.value.toString))
          )
        )

  // ── link (re-point a record at another Calibre entry) ────────────────────────────────────────

  private def stripHtml(s: String): String = s.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim

  // ── push / sync (corpus → Calibre) ──────────────────────────────────────────────────────────

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private def hostPush(c: Client, ck: Citekey, file: Option[String], replace: Boolean, json: Cli.JsonOutput)(using
      console: Console
  ) =
    if replace then
      replaceOne(c, ck, file.map(f => Path.of(f))).map { case (id, fmt) =>
        Output.emit(json, Calibre.Json.Replaced(ck, id, Calibre.Json.Replaced.Value(fmt.value)))(_ =>
          shown"replaced the ${fmt.value.toUpperCase} of Calibre id $id with `$ck`'s original"
        )
      }
    else
      pushOne(c, ck, file.map(f => Path.of(f))).map { case (id, title) =>
        Output.emit(json, Calibre.Json.Linked(ck, id))(_ => shown"added `$ck` to Calibre as id $id — $title")
      }

  /** `push --replace` (#652): the file goes into the entry the source is ALREADY linked to, so the link stays valid and
    * no duplicate entry appears. The file is `--file`, else the original the server stored at ingest, downloaded to a
    * temporary directory and removed afterwards.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private def replaceOne(c: Client, ck: Citekey, file: Option[Path])(using
      console: Console
  ): Either[Cli.Error, (Calibre.Book.Id, Calibre.Book.Format)] =
    for
      s <- AcquireClient(c).calibreMeta(ck)
      id <- s.calibreId
        .flatMap(_.value.trim.toIntOption)
        .map(Calibre.Book.Id(_))
        .toRight(
          fail(
            shown"'$ck' is not linked to a Calibre entry — `histmersion calibre push $ck` adds one, " +
              shown"`histmersion calibre link $ck <id>` links an existing one"
          )
        )
      fmt <- file match
        case Some(f) => Calibre(Settings.app.cli.calibre).replaceFormat(id, f).left.map(failProblem)
        case None    =>
          withOriginal(c, ck)(f => Calibre(Settings.app.cli.calibre).replaceFormat(id, f).left.map(failProblem))
    yield (id, fmt)

  /** Download the server's stored original of `ck` as `<tmp>/<ck>.<ext>` (the extension is what tells Calibre the
    * format), run `f` on it, and delete it whatever happens.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  private def withOriginal[A](c: Client, ck: Citekey, hint: String = "")(
      f: Path => Either[Cli.Error, A]
  ): Either[Cli.Error, A] =
    val dir = java.nio.file.Files.createTempDirectory("histmersion-original-")
    val raw = dir.resolve("download")
    try
      for
        got <- c
          .original(ck, raw.toFile)
          .left
          .map(e =>
            if hint.isEmpty then e
            else
              e.modify(_.problems).setTo(Problem.Unclassified(Legacy.Text(e.problems.sentences.mkString("; ") + hint)))
          )
        ext <- Ingest
          .dispositionExtension(Ingest.Disposition(got._2.value))
          .toRight(fail(shown"the server did not say what format '$ck' is"))
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
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private[cli] def pushOne(using
      console: Console
  )(
      c: Client,
      ck: Citekey,
      file: Option[Path],
      calibre: Calibre = Calibre(Settings.app.cli.calibre),
      originals: Option[CalibreCliConfig.OriginalsDir] = Settings.app.cli.calibre.originals
  ): Either[Cli.Error, (Calibre.Book.Id, Title)] =
    for
      // the authors must come back as a LIST: "Stocker, Sharon R." is ONE author, so the
      // comma-joined `SourceDetail.authors` cannot be split apart again
      s <- AcquireClient(c).calibreMeta(ck)
      add = (p: Path) => calibre.add(p, pushMeta(s)).left.map(failProblem)
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
      _ <- AcquireClient(c).calibrePushed(CalibrePushRequest(ck, Source.CalibreId(id.value.toString)))
    yield (id, s.title)

  /** Every source linked to a Calibre entry, with what it would send there. One query for the list, then the push
    * metadata per source (the authors must come back as a LIST — see [[pushOne]]).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private def linkedRecords(c: Client): Either[Cli.Error, List[LinkedRecord]] =
    for
      q <- c.query(
        Query.Statement("SELECT citekey, calibre_id FROM sources WHERE calibre_id ~ '^[0-9]+$' ORDER BY citekey")
      )
      _ <- Either.cond(!q.truncated.truncated, (), truncated)
      keys = q.rows.collect { case List(Some(ck), Some(id)) => Citekey(ck.value) -> id.value.toInt }
      recs <- keys.traverse((ck, id) =>
        AcquireClient(c)
          .calibreMeta(ck)
          .map(s => LinkedRecord(LinkedRecord.Citekey(ck.value), LinkedRecord.CalibreId(id), pushMeta(s)))
      )
    yield recs

  /** What the corpus record sends into Calibre, for `push` and `update` alike. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private def pushMeta(s: CalibreGap): Calibre.Push =
    Calibre.Push(
      title = s.title,
      authors = s.authors.map(a => AuthorName(a.value)),
      doi = s.doi,
      isbn = s.isbn,
      year = s.year,
      publisher = s.venue.map(v => CollectionEntry.Publisher(v.value)),
      summary = s.summary.map(sm => Source.Summary(sm.value.take(500))),
      tags = s.topics.map(t => CollectionEntry.Tags(t.value))
    )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private def hostAudit(c: Client, json: Cli.JsonOutput)(using console: Console): Either[Cli.Error, Unit] =
    for
      q <- c.query(
        Query.Statement(
          "SELECT citekey, calibre_id, title, array_to_string(authors, '|') AS authors, year " +
            "FROM sources WHERE calibre_id ~ '^[0-9]+$' ORDER BY citekey"
        )
      )
      _ <- Either.cond(!q.truncated.truncated, (), truncated)
      recs = q.rows.flatMap {
        case List(Some(ck), Some(id), title, authors, year) =>
          Some(
            AuditRecord(
              AuditRecord.Citekey(ck.value),
              AuditRecord.CalibreId(id.value.toInt),
              AuditRecord.Title(title.fold("")(_.value)),
              authors.toList.flatMap(_.value.split('|')).map(_.trim).filter(_.nonEmpty).map(AuditRecord.Authors(_)),
              year.flatMap(_.value.toIntOption).map(AuditRecord.Year(_))
            )
          )
        case _ => None
      }
      books <- calibre(c).all().left.map(failProblem)
    yield
      val found = Calibre.Audit.audit(recs, books.map(b => b.id -> b).toMap)
      Output.emit(json, found.map(Calibre.Json.AuditRow.of))(_ =>
        Calibre.Audit.markdown(found, CollectionAudit.Checked(recs.map(_.calibreId).distinct.size))
      )

  /** The server's gap listing, each with where its original is and whether it counts ([[Calibre.classify]]): a record
    * with no fetched file anywhere is not a Calibre gap.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private def classifiedGaps(
      c: Client,
      limit: Option[Paging.Limit],
      originals: Option[CalibreCliConfig.OriginalsDir]
  ): Either[Cli.Error, List[Calibre.GapEntry]] =
    AcquireClient(c)
      .calibreGaps(limit.map(l => AcquireApi.Limit(l.value)))
      .map(g => Calibre.classify(g.gaps, Calibre.originalFor(originals, _)))

  /** `calibre sync`: push the COUNTED gaps (an original on this host or on the server); the records with no fetched
    * file are not gaps, so they are neither pushed nor reported as skipped — one summary line says how many there are.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private[cli] def syncGaps(using
      console: Console
  )(
      c: Client,
      apply: Api.Apply,
      limit: Option[Paging.Limit],
      json: Cli.JsonOutput,
      calibre: Calibre = Calibre(Settings.app.cli.calibre),
      originals: Option[CalibreCliConfig.OriginalsDir] = Settings.app.cli.calibre.originals
  ): Either[Cli.Error, Unit] =
    classifiedGaps(c, limit, originals).flatMap { entries =>
      if json.asJson && !apply.applies then
        Right(Output.emit(json, Calibre.Json.Gaps.of(entries))(_ => Calibre.markdownGaps(entries)))
      else
        val ready = entries.filter(_.counted.counts)
        val missing = entries.count(_.original == Calibre.GapOriginal.Missing)
        val note = if missing == 0 then "" else " · " + Calibre.uncountedLine(missing)
        if ready.nonEmpty then Cli.out.println(Calibre.markdownGaps(ready) + "\n")
        if !apply.applies then
          Cli.out.println(
            s"Dry run: ${ready.size} source(s) would be pushed$note" +
              (if ready.nonEmpty then " Re-run with --apply." else "")
          )
          Right(())
        else
          val results = ready.map(e => e.gap.citekey -> pushOne(c, e.gap.citekey, None, calibre, originals))
          results.foreach {
            case (ck, Right((id, _))) => Cli.out.println(shown"  + $ck → Calibre id $id")
            case (ck, Left(e))        => Cli.err.println(shown"  ! $ck: ${e.problems.sentences.mkString("; ")}")
          }
          val failed = results.count(_._2.isLeft)
          Cli.out.println(s"pushed ${results.size - failed} source(s), $failed failed$note")
          if failed == 0 then Right(())
          else Left(fail(s"$failed of ${results.size} push(es) failed"))
    }

  /** The server's collection for `gaps` and `sync`, unless this machine keeps originals of its own
    * (`cli.calibre.originals-dir`), which only the CLI can see and classify by.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def onServerUnlessLocalOriginals(c: Client)(using console: Console): Option[CollectionsClient] =
    if Settings.app.cli.calibre.originals.isDefined then None else server(c)

  /** `calibre sync` on the server: the same lines as [[syncGaps]], the pushes made there. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private def serverSync(
      s: CollectionsClient,
      apply: Api.Apply,
      limit: Option[Paging.Limit],
      json: Cli.JsonOutput
  )(using console: Console): Either[Cli.Error, Unit] =
    s.sync(
      CollectionSync
        .Request(
          (if apply.applies then CollectionSync.Request.Apply.Apply else CollectionSync.Request.Apply.DryRun),
          limit.map(l => CollectionSync.Request.Limit(l.value))
        )
    ).flatMap { r =>
      if json.asJson && !apply.applies then
        Right(
          Output.emit(json, Calibre.Json.Gaps.of(r.gaps))(_ => CollectionsMarkdown.gaps(r.gaps.gaps))
        )
      else
        val ready = r.gaps.gaps.filter(_.counted.counted)
        val missing = r.gaps.gaps.count(_.original.value == "none")
        val note =
          if missing == 0 then "" else " · " + CollectionsMarkdown.uncountedLine(CollectionsMarkdown.Missing(missing))
        if ready.nonEmpty then Cli.out.println(CollectionsMarkdown.gaps(ready) + "\n")
        if !apply.applies then
          Cli.out.println(
            s"Dry run: ${ready.size} source(s) would be pushed$note" +
              (if ready.nonEmpty then " Re-run with --apply." else "")
          )
          Right(())
        else
          r.pushes.foreach(p =>
            p.id match
              case Some(id) => Cli.out.println(shown"  + ${p.citekey} → Calibre id $id")
              case None     =>
                Cli.err.println(s"  ! ${p.citekey}: ${p.problems.map(_.sentences.mkString("; ")).getOrElse("")}")
          )
          val failed = r.pushes.count(_.id.isEmpty)
          Cli.out.println(s"pushed ${r.pushes.size - failed} source(s), $failed failed$note")
          if failed == 0 then Right(())
          else Left(fail(s"$failed of ${r.pushes.size} push(es) failed"))
    }

  /** `calibre gaps` on the server: the same table, `--quiet` line and exit codes as [[listGaps]]. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  private def serverGaps(
      s: CollectionsClient,
      quiet: Cli.QuietOutput,
      limit: Option[Paging.Limit],
      json: Cli.JsonOutput
  )(using console: Console): Either[Cli.Error, Unit] =
    s.gaps(limit).flatMap { g =>
      val counted = g.gaps.filter(_.counted.counted)
      if json.asJson then Right(Output.emit(json, Calibre.Json.Gaps.of(g))(_ => CollectionsMarkdown.gaps(g.gaps)))
      else if !quiet.quiet then Right(Cli.out.println(CollectionsMarkdown.gaps(g.gaps)))
      else if counted.isEmpty then Right(())
      else
        Left(
          Cli.Error(
            Problem.Unclassified(
              Legacy.Text(
                s"${counted.size} source(s) are not in Calibre yet (${counted.take(3).map(_.citekey).mkString(", ")}" +
                  (if counted.size > 3 then ", …" else "") + ") — run `histmersion calibre sync --apply`"
              )
            )
          )
        )
    }

  /** `calibre gaps`: only a source with an original to push counts — the table, the `--quiet` line and its exit 1. A
    * record with no fetched file is one informational line in the full report and a `counted: false` row in `--json`,
    * never a gap.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  private[cli] def listGaps(using
      console: Console
  )(
      c: Client,
      quiet: Cli.QuietOutput,
      limit: Option[Paging.Limit],
      json: Cli.JsonOutput,
      originals: Option[CalibreCliConfig.OriginalsDir] = Settings.app.cli.calibre.originals
  ): Either[Cli.Error, Unit] =
    classifiedGaps(c, limit, originals).flatMap { entries =>
      val counted = entries.filter(_.counted.counts)
      if json.asJson then Right(Output.emit(json, Calibre.Json.Gaps.of(entries))(_ => Calibre.markdownGaps(entries)))
      else if !quiet.quiet then Right(Cli.out.println(Calibre.markdownGaps(entries)))
      else if counted.isEmpty then Right(())
      else
        // exit 1 with ONE line: this is what the Stop/PostToolUse reminder hook reads
        Left(
          Cli.Error(
            Problem.Unclassified(
              Legacy.Text(
                s"${counted.size} source(s) are not in Calibre yet (${counted.take(3).map(_.gap.citekey).mkString(", ")}" +
                  (if counted.size > 3 then ", …" else "") + ") — run `histmersion calibre sync --apply`"
              )
            )
          )
        )
    }

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private def hostUpdate(c: Client, ck: Citekey, json: Cli.JsonOutput)(using console: Console) =
    for
      s <- AcquireClient(c).calibreMeta(ck)
      id <- s.calibreId
        .flatMap(_.value.toIntOption)
        .map(Calibre.Book.Id(_))
        .toRight(fail(shown"'$ck' has no Calibre entry yet — use `histmersion calibre push $ck`"))
      changes <- Calibre(Settings.app.cli.calibre).update(id, pushMeta(s)).left.map(failProblem)
    yield Output.emit(
      json,
      Calibre.Json.Updated(ck, id, changes.map(ch => Calibre.Json.Updated.Changed(ch.field.name.value)))
    )(u =>
      if u.changed.isEmpty then shown"Calibre id $id already agrees with `$ck` — nothing written"
      else shown"updated Calibre id $id from `$ck` (${u.changed.mkString(", ")}) — ${s.title}"
    )
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def hostUpdateAll(c: Client, apply: Api.Apply, json: Cli.JsonOutput)(using console: Console) =
    for
      linked <- linkedRecords(c)
      entries <- calibre(c)
        .updateAll(
          linked,
          (if apply.applies then CollectionSync.Request.Apply.Apply else CollectionSync.Request.Apply.DryRun)
        )
        .left
        .map(failProblem)
      _ = Output.emit(json, entries.map(Calibre.Json.UpdateRow.of))(_ =>
        Calibre.markdownUpdateAll(
          entries,
          (if apply.applies then CollectionUpdateAll.Applied.Applied else CollectionUpdateAll.Applied.Previewed)
        )
      )
      failed = entries.count(_.outcome match { case UpdateOutcome.Failed(_, _) => true; case _ => false })
      _ <- Either.cond(failed == 0, (), fail(s"$failed Calibre update(s) failed"))
    yield ()

  /** What `histmersion …` of this group was asked to do, every argument parsed. */
  enum Command extends Cli.Command:
    @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
    case Search(q: Option[String], unlinked: Boolean, limit: Option[Int], json: Cli.JsonOutput)
    @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
    case Import(
        id: Int,
        citekey: Option[String],
        format: Option[String],
        noConvert: Boolean,
        following: Cli.Following,
        item: Option[Int],
        json: Cli.JsonOutput
    )
    case UpdateOne(citekey: String, json: Cli.JsonOutput)
    case UpdateAll(apply: Api.Apply, json: Cli.JsonOutput)
    case Link(ckText: String, idValue: Int, json: Cli.JsonOutput)
    @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
    case Push(ckText: String, file: Option[String], replaceFlag: Boolean, json: Cli.JsonOutput)
    case SetCmd(idValue: Int, raw: List[String])
    case Audit(json: Cli.JsonOutput)
    case Sync(writes: Cli.Writes, limit: Option[Int], json: Cli.JsonOutput)
    @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
    case Gaps(quiet: Boolean, limit: Option[Int], json: Cli.JsonOutput)

    def run(c: Client)(using Console): Either[Cli.Error, Unit] = Perform(c)(this)

  /** Carries out Command commands against one server. */
  final private[cli] class Perform(c: Client)(using console: Console):
    @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
    @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
    def apply(cmd: Command): Either[Cli.Error, Unit] =
      cmd match
        case Command.Link(ckText, idValue, json) =>
          val ck = Citekey(ckText)
          val id = Calibre.Book.Id(idValue)
          server(c) match
            case Some(s) =>
              s.link(CollectionLinkRequest(ck, CollectionLinkRequest.Id(id.value.toString)))
                .map(l =>
                  Output.emit(json, Calibre.Json.Linked(l.citekey, id))(_ =>
                    shown"linked `${l.citekey}` to Calibre id $id — ${l.title}"
                  )
                )
            case None =>
              for
                book <- calibre(c)
                  .byId(id)
                  .left
                  .map(failProblem)
                  .flatMap(_.toRight(Cli.Error(Problem.NotFound.Entity(CalibreBook, Problem.Raw.of(id)))))
                sw <- c.patchSource(
                  ck,
                  Source.Patch(metadata = Some(MetadataPatch(calibreId = Some(Source.CalibreId(id.value.toString)))))
                )
              yield Output.emit(json, Calibre.Json.Linked(sw.source.citekey, id))(l =>
                shown"linked `${l.citekey}` to Calibre id $id — ${book.title}"
              )
        case Command.Push(ckText, file, replaceFlag, json) =>
          val ck = Citekey(ckText)
          val replace = replaceFlag
          val onHost =
            file.isDefined || (!replace && Calibre.originalFor(Settings.app.cli.calibre.originals, ck).isDefined)
          server(c).filter(_ => !onHost) match
            case Some(s) =>
              s.push(
                CollectionPushRequest(
                  ck,
                  (if replace then CollectionPushRequest.Replace.Replace else CollectionPushRequest.Replace.Keep)
                )
              ).map { p =>
                p.replaced match
                  case Some(fmt) =>
                    Output.emit(
                      json,
                      Calibre.Json.Replaced(
                        ck,
                        Calibre.Book.Id(p.id.value.toIntOption.getOrElse(0)),
                        Calibre.Json.Replaced.Value(fmt.value)
                      )
                    )(_ => shown"replaced the ${fmt.value.toUpperCase} of Calibre id ${p.id} with `$ck`'s original")
                  case None =>
                    Output.emit(json, Calibre.Json.Linked(ck, Calibre.Book.Id(p.id.value.toIntOption.getOrElse(0))))(
                      _ => shown"added `$ck` to Calibre as id ${p.id} — ${p.title}"
                    )
              }
            case None => hostPush(c, ck, file, replace, json)
        case Command.SetCmd(idValue, raw) =>
          val id = Calibre.Book.Id(idValue)
          raw
            .traverse(f =>
              f.split(":", 2) match
                case Array(k, v) if k.trim.nonEmpty =>
                  Right(CollectionFieldValue(CollectionFieldValue.Name(k.trim), CollectionFieldValue.Value(v)))
                case _ => Left(fail(s"'$f' is not <name>:<value>"))
            )
            .flatMap(fields =>
              server(c) match
                case Some(s) =>
                  s.set(CollectionSetRequest(CollectionSetRequest.Id(id.value.toString), fields)).map(_ => ())
                case None => calibre(c).set(id, fields).left.map(failProblem)
            )
            .map(_ => Cli.out.println(shown"set ${raw.map(_.takeWhile(_ != ':')).mkString(", ")} on Calibre id $id"))
        case Command.Audit(json) =>
          server(c) match
            case Some(s) =>
              s.audit.map(a =>
                Output.emit(json, a.findings.map(Calibre.Json.AuditRow.of(_)))(_ => CollectionsMarkdown.audit(a))
              )
            case None => hostAudit(c, json)
        case Command.Sync(writes, limit, json) =>
          val applying = (if writes.applies then Api.Apply.Apply else Api.Apply.DryRun)
          val max = limit.map(Paging.Limit(_))
          onServerUnlessLocalOriginals(c).fold(syncGaps(c, applying, max, json))(serverSync(_, applying, max, json))
        case Command.Gaps(quiet, limit, json) =>
          val quietOutput = (if quiet then Cli.QuietOutput.Quiet else Cli.QuietOutput.Verbose)
          val max = limit.map(Paging.Limit(_))
          onServerUnlessLocalOriginals(c).fold(listGaps(c, quietOutput, max, json))(
            serverGaps(_, quietOutput, max, json)
          )
        case Command.Search(q, unlinked, limit, json) =>
          val max = Paging.Limit(limit.getOrElse(20))
          val query = q.map(Api.Search.Text(_))
          server(c) match
            case Some(s) =>
              s.search(
                if unlinked then query else Some(query.getOrElse(Api.Search.Text(""))),
                Some(max),
                (if unlinked then Unlinked.OnlyUnlinked else Unlinked.Every)
              ).map { r =>
                Output.emit(json, r.entries.map(Calibre.Json.Book.of(_)))(_ =>
                  if unlinked then CollectionsMarkdown.unlinked(r.entries, CollectionsMarkdown.Total(r.total.value))
                  else CollectionsMarkdown.search(r.entries)
                )
              }
            case None => hostSearch(c, query, (if unlinked then Unlinked.OnlyUnlinked else Unlinked.Every), max, json)
        case Command.Import(id, citekey, format, noConvert, following, item, json) =>
          server(c) match
            case Some(s) =>
              serverImport(
                c,
                s,
                Calibre.Book.Id(id),
                citekey.map(Citekey(_)),
                format.map(CollectionImportRequest.Format(_)),
                noConvert = noConvert,
                following,
                item.map(Queue.QueueItem.Id(_)),
                json
              )
            case None =>
              hostImport(
                c,
                Calibre.Book.Id(id),
                citekey.map(Citekey(_)),
                format.map(Calibre.Book.Format(_)),
                noConvert = noConvert,
                following,
                item.map(Queue.QueueItem.Id(_)),
                json
              )
        case Command.UpdateOne(ckText, json) =>
          val ck = Citekey(ckText)
          server(c) match
            case Some(srv) =>
              srv.updateOne(ck).map { u =>
                Output.emit(
                  json,
                  Calibre.Json.Updated(ck, Calibre.Book.Id(u.id.value.toIntOption.getOrElse(0)), u.changed)
                )(x =>
                  if x.changed.isEmpty then shown"Calibre id ${u.id} already agrees with `$ck` — nothing written"
                  else shown"updated Calibre id ${u.id} from `$ck` (${x.changed.mkString(", ")}) — ${u.title}"
                )
              }
            case None => hostUpdate(c, ck, json)
        case Command.UpdateAll(apply, json) =>
          server(c) match
            case Some(srv) =>
              for
                u <- srv.updateAll(apply)
                _ = Output.emit(json, u.rows.map(Calibre.Json.UpdateRow.of(_)))(_ =>
                  CollectionsMarkdown.updateAll(u.rows, apply.applies)
                )
                failed = u.rows.count(_.state.value == "failed")
                _ <- Either.cond(failed == 0, (), fail(s"$failed Calibre update(s) failed"))
              yield ()
            case None => hostUpdateAll(c, apply, json)
