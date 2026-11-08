package com.histmersion
package cli

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.exchange.Bundle

import scala.annotation.tailrec
import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.api.Api
import com.histmersion.config.*

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import com.histmersion.util.data.mirror.MirrorPlacement
import com.histmersion.domain.research.articles.ArticleNote
import com.histmersion.api.checks.FormatMarkdown
import com.histmersion.api.library.SeedResult
import com.histmersion.api.research.ArticleMarkdown
import com.histmersion.api.research.Article
import com.histmersion.api.research.CritiqueReason
import com.histmersion.util.console.Console
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.research.Articles
import com.histmersion.api.research.ReviewGoal

/** `histmersion article …` — the compiled wiki over HTTP (plan §5.2 stream 2.7).
  *
  * The CLI stays a pure HTTP client: it reads the host's `wiki/<kind>/<slug>.md` files and POSTs their text to
  * `/api/articles/import`, exactly as `histmersion seed` does for source notes, because the containerised server cannot
  * see the host filesystem.
  */
object ArticleCli:
  type Exported = Exported.T
  object Exported extends Newtype[Int, Pure]

  // ── host-side wiki layout ────────────────────────────────────────────────────────────────────

  /** Where the article notes are, given whatever directory the user pointed at.
    *
    * `histmersion seed ~/research/wiki` and `histmersion seed ~/research/wiki/sources` must both work: the first is the
    * wiki root (source notes in `sources/`, articles in the sibling directories), the second is the source-note
    * directory itself (articles are then one level up). Anything else is treated as a plain directory of notes, as
    * before.
    */

  final case class WikiLayout(sources: Path, root: Option[Path])

  object WikiLayout:
    val SourcesDir: MirrorPlacement.Directory = MirrorPlacement.SourcesDirectory

    def of(dir: Path): WikiLayout =
      val here = Option(dir.getFileName).map(_.toString).getOrElse("")
      if Files.isDirectory(dir.resolve(SourcesDir.value)) then WikiLayout(dir.resolve(SourcesDir.value), Some(dir))
      else if here == SourcesDir.value then WikiLayout(dir, Option(dir.toAbsolutePath.getParent))
      else WikiLayout(dir, None)

  /** Article note files under `root`: every `*.md` in a subdirectory other than `sources` (the directory name is the
    * fallback KIND — `concepts/` holds `concept`s), plus any `*.md` sitting at the top level (e.g. `wiki/index.md`).
    * Frontmatter `type:` always wins over the directory.
    */
  def files(
      root: Path,
      skip: Set[MirrorPlacement.Directory] = Set(WikiLayout.SourcesDir)
  ): List[(Path, Article.Kind.Wire)] =
    val top = markdown(root).map(_ -> kindOf(root))
    val sub = dirs(root)
      .filterNot(d => skip.contains(MirrorPlacement.Directory(d.getFileName.toString.toLowerCase)))
      .flatMap(d => markdown(d).map(_ -> kindOf(d)))
    (top ++ sub).sortBy((p, _) => p.toString)

  private def kindOf(dir: Path): Article.Kind.Wire =
    Articles.kindOfDir(Option(dir.getFileName).map(_.toString).getOrElse(""))

  private def markdown(dir: Path): List[Path] =
    if !Files.isDirectory(dir) then Nil
    else
      val s = Files.list(dir)
      try
        s.iterator.asScala
          .filter(p => Files.isRegularFile(p) && p.getFileName.toString.endsWith(".md"))
          .toList
          .sortBy(_.getFileName.toString)
      finally s.close()

  private def dirs(dir: Path): List[Path] =
    if !Files.isDirectory(dir) then Nil
    else
      val s = Files.list(dir)
      try s.iterator.asScala.filter(Files.isDirectory(_)).toList.sortBy(_.getFileName.toString)
      finally s.close()

  /** Package one article note for the server: text, fallback slug/kind, and the mtime that drives the "stored row is
    * newer than the file" skip.
    */
  def toImport(file: Path, kind: Article.Kind.Wire, force: Api.Overwrite): Article.Import =
    Article.Import(
      text = Article.Import.Text(Files.readString(file)),
      slug = Some(Article.Slug(file.getFileName.toString.stripSuffix(".md"))),
      kind = Some(kind),
      mtime = Some(Article.Import.Mtime(Files.getLastModifiedTime(file).toMillis)),
      force = Article.Import.Force(force.value)
    )

  /** Import every article note under `root`, printing progress. Returns the accumulated result so a caller
    * (`histmersion seed`) can fold it into its own report.
    */
  def importAll(c: Client, root: Path, force: Api.Overwrite, skip: Set[MirrorPlacement.Directory])(using
      console: Console
  ): SeedResult =
    val fs = files(root, skip)
    val total = fs.size
    if total > 0 then Cli.out.println(s"importing $total article(s) from $root")
    @tailrec def loop(
        rest: List[(Path, Article.Kind.Wire)],
        number: Int,
        imported: Int,
        failed: List[String],
        skipped: List[String]
    )(using console: Console): SeedResult =
      rest match
        case Nil =>
          SeedResult(
            SeedResult.Imported(imported),
            failed.map(SeedResult.Failed(_)),
            SeedResult.EdgesResolved(0),
            skipped.map(SeedResult.Skipped(_))
          )
        case (f, kind) :: tail =>
          val name = f.getFileName.toString.stripSuffix(".md")
          Cli.out.println(s"$number/$total $kind/$name")
          c.importArticle(toImport(f, kind, force)) match
            case Right(r) =>
              loop(
                tail,
                number + 1,
                imported + r.imported.value,
                failed ++ r.failed.map(_.value),
                skipped ++ r.skipped.map(_.value)
              )
            case Left(e) => loop(tail, number + 1, imported, failed :+ s"$name: ${e.message}", skipped)
    loop(fs, 1, 0, Nil, Nil)

  /** Write every article to `<dir>/<kind>/<slug>.md`. Returns how many were written. */
  def exportAll(c: Client, out: Path)(using console: Console): Either[Cli.Error, Exported] =
    c.articleSlugs.flatMap { slugs =>
      val total = slugs.size
      val failed = slugs.zipWithIndex.flatMap { (slug, i) =>
        Cli.out.println(s"${i + 1}/$total $slug")
        c.exportArticle(slug) match
          case Right(text) =>
            val dir = out.resolve(kindOfNote(text).value)
            val _ = Files.createDirectories(dir)
            val _ = Files.writeString(
              dir.resolve(MirrorPlacement.noteFile(MirrorPlacement.RecordId(slug.value)).value),
              text.value
            )
            None
          case Left(e) => Some(s"$slug: ${e.message}")
      }
      Cli.out.println(s"exported ${total - failed.size} article(s) to $out, failed ${failed.size}")
      failed.take(10).foreach(f => Cli.err.println(s"  ! $f"))
      if failed.isEmpty then Right(Exported(total))
      else
        Left(
          Cli.Error(
            Cli.Error.Message(s"${failed.size} of $total article(s) could not be exported"),
            exitCode = ExitCode.Refused
          )
        )
    }

  /** The `type:` of an exported note — which subdirectory it belongs in. `ArticleNote.render` always writes the key, so
    * the fallback only guards a hand-edited file.
    */
  private val TypeKey = """(?m)^type:\s*["']?([^"'\r\n]+)["']?\s*$""".r
  def kindOfNote(text: Article.Export): Article.Kind.Wire =
    Article.Kind.Wire(
      TypeKey
        .findFirstMatchIn(text.value)
        .map(_.group(1).trim)
        .filter(_.nonEmpty)
        .getOrElse(Articles.Kinds.head)
    )

  // ── the `histmersion article …` subcommands ──────────────────────────────────────────────────────

  /** Progress report of an article import (also used by `histmersion seed`). */
  def report(r: SeedResult)(using console: Console): Unit =
    Cli.out.println(s"imported ${r.imported}, skipped ${r.skipped.size} (newer in DB), failed ${r.failed.size}")
    r.skipped.take(10).foreach(s => Cli.out.println(s"  ~ $s"))
    r.failed.take(10).foreach(f => Cli.err.println(s"  ! $f"))

  /** The filter flags are the shared ones: they select SOURCES, and an article is listed when it cites one of them
    * (`--title` is the article's own title). The positional query is the article's own full text.
    */
  private def list(using console: Console) =
    Opts.subcommand("list", "List/search compiled articles (the filter flags select the sources they cite)") {
      (
        Opts.argument[String]("query").orNone,
        Opts
          .option[String]("kind", s"Article kind (${Articles.Kinds.mkString("|")})")
          .orNone,
        FilterFlags.opts,
        Output.jsonFlag
      ).mapN { (q, kind, f, json) => (c: Client) =>
        c.listArticles(
          Api.ArticleQuery(
            f,
            kind.map(Api.ArticleQuery.Kind(_)),
            q.map(_.trim).filter(_.nonEmpty).map(Api.ArticleQuery.Text(_))
          )
        ).map(r => Output.emit(json, r)(ArticleMarkdown.list))
      }
    }

  private def get(using console: Console) =
    Opts.subcommand("get", "Show one article (parts: body,summary,coverage,cited,backlinks — default all)") {
      (
        Opts.argument[String]("slug"),
        Opts
          .option[String](
            "parts",
            "Comma-separated parts to return: body,summary,coverage,cited,backlinks (default: all)"
          )
          .orNone,
        Output.jsonFlag
      ).mapN { (slug, parts, json) => (c: Client) =>
        c.getArticle(ReviewGoal.Slug(slug)).map { v =>
          val ps = parts.map(_.split(',').map(_.trim.toLowerCase).toList).getOrElse(Nil)
          Output.emit(json, v)(ArticleMarkdown.article(_, ps.map(Article.Part(_))))
        }
      }
    }

  private val strictFlag =
    Opts
      .flag("strict", "Refuse the write when the Markdown has a formatting error (default: write, then list them)")
      .orFalse

  private def put(using console: Console) =
    Opts.subcommand("put", "Send ONE article note file (frontmatter + body) as a full create/replace") {
      (
        Opts.argument[String]("file.md"),
        Opts.option[String]("kind", "Kind when the frontmatter has no `type:`").orNone,
        strictFlag
      )
        .mapN { (file, kind, strict) => (c: Client) =>
          val p = Path.of(file)
          if !Files.isRegularFile(p) then
            Left(Cli.Error(Cli.Error.Message(s"no such file: $file"), exitCode = ExitCode.Usage))
          else
            // an explicit `put` is always deliberate → force past the "newer in DB" guard
            val fallback = kind.map(Article.Kind.Wire(_)).getOrElse(kindOf(p.toAbsolutePath.getParent))
            val req =
              toImport(p, fallback, force = Api.Overwrite(true)).modify(_.strict).setTo(Article.Import.Strict(strict))
            c.importArticle(req).map { r =>
              report(r)
              // the import answers with counts only: ask for the article's formatting problems by the
              // slug the note itself declares (as the server parsed it), so the writer sees them now
              val slug = ArticleNote
                .parse(req.text.value, req.slug.getOrElse(Article.Slug("")), fallback)
                .map(_.slug)
                .filter(_.value.nonEmpty)
              if r.imported.value > 0 then
                slug.foreach(s =>
                  c.lintArticle(ReviewGoal.Slug(s.value)).foreach(f => Cli.out.println(FormatMarkdown.notice(f.issues)))
                )
            }
        }
    }

  /** `article lint <slug> [--fix]` — what in the article will not display as written. */
  private def lint(using console: Console) = Opts.subcommand(
    "lint",
    "Formatting problems of one article's body and summary (--fix applies the wording-safe repairs)"
  ) {
    (
      Opts.argument[String]("slug"),
      Opts.flag("fix", "Apply the repairs that never change wording, then report what is left").orFalse,
      Output.jsonFlag
    ).mapN { (slug, fix, json) => (c: Client) =>
      (if fix then c.fixArticleFormat(ReviewGoal.Slug(slug)) else c.lintArticle(ReviewGoal.Slug(slug)))
        .map(r => Output.emit(json, r)(FormatMarkdown.report))
    }
  }

  /** `article check <slug> (--edits-file f.json | --body-file f.md) [--kind k]` — dry-run a change as the review
    * pipeline checks its fixer's answer: nothing is written.
    */
  private def check(using console: Console) = Opts.subcommand(
    "check",
    "Dry-run edits to an article (or a whole draft body) and list the problems the review checks would find — writes nothing"
  ) {
    (
      Opts.argument[String]("slug"),
      Opts
        .option[String](
          "edits-file",
          "A JSON list of edits: [{\"op\": \"replace|insertAfter|insertBefore|delete\", \"anchor\": \"…\", \"text\": \"…\"}]"
        )
        .orNone,
      Opts.option[String]("body-file", "Instead of edits: a whole draft body (Markdown) to check").orNone,
      Opts
        .option[String](
          "kind",
          "Check the section set of this kind instead of the article's (index = none, for a fragment)"
        )
        .orNone,
      Output.jsonFlag
    ).mapN { (slug, editsFile, bodyFile, kind, json) => (c: Client) =>
      def read(f: String) =
        val p = Path.of(f)
        if Files.isRegularFile(p) then Right(Files.readString(p))
        else Left(Cli.Error(Cli.Error.Message(s"no such file: $f"), exitCode = ExitCode.Usage))
      val req = (editsFile, bodyFile) match
        case (Some(e), None) =>
          read(e)
            .flatMap(t =>
              io.circe.parser
                .decode[List[Article.Edits.EditAnswer]](t)
                .left
                .map(err =>
                  Cli.Error(
                    Cli.Error.Message(s"$e: not a JSON list of edits (${err.getMessage})"),
                    exitCode = ExitCode.Usage
                  )
                )
            )
            .map(es => Article.Edits.ArticleCheckRequest(edits = es, kind = kind.map(Article.Kind.Wire(_))))
        case (None, Some(b)) =>
          read(b).map(t =>
            Article.Edits.ArticleCheckRequest(
              body = Some(Article.Body(FileFormat.splitFrontmatter(FileFormat.Text(t))._2.value)),
              kind = kind.map(Article.Kind.Wire(_))
            )
          )
        case _ =>
          Left(
            Cli.Error(Cli.Error.Message("give --edits-file or --body-file (one of them)"), exitCode = ExitCode.Usage)
          )
      req
        .flatMap(c.checkArticle(ReviewGoal.Slug(slug), _))
        .map(r => Output.emit(json, r)(Article.Edits.markdown(_).value))
    }
  }

  private def delete(using console: Console) = Opts.subcommand("delete", "Remove one article (its links go with it)") {
    Opts
      .argument[String]("slug")
      .map(slug =>
        (c: Client) => c.deleteArticle(ReviewGoal.Slug(slug)).map(d => Cli.out.println(s"deleted ${d.slug}"))
      )
  }

  private def exportCmd(using console: Console) =
    Opts.subcommand("export", "Write each article to <dir>/<kind>/<slug>.md") {
      Opts.argument[String]("dir").map { dir => (c: Client) =>
        val out = Path.of(dir)
        val _ = Files.createDirectories(out)
        exportAll(c, out).map(_ => ())
      }
    }

  private def importCmd(using console: Console) =
    Opts.subcommand("import", "Import <dir>/<kind>/*.md (and any *.md in <dir> itself)") {
      (Opts.argument[String]("dir"), Opts.flag("force", "Overwrite articles newer than the files").orFalse)
        .mapN { (dir, force) => (c: Client) =>
          val p = Path.of(dir)
          if !Files.isDirectory(p) then
            Left(Cli.Error(Cli.Error.Message(s"not a directory: $dir"), exitCode = ExitCode.Usage))
          else Right(report(importAll(c, p, Api.Overwrite(force), Set(WikiLayout.SourcesDir))))
        }
    }

  /** The filter flags of `article patch` (#958): any of them REPLACES the article's recorded filter with the one they
    * spell; `--clear-filter` removes it.
    */
  private val filterFlags: Opts[ArticlePatches.FilterFlags] =
    (
      Opts
        .option[String](
          "filter-query",
          "Coverage filter: the free-text query the article is compiled from (replaces the recorded filter)"
        )
        .orNone,
      Opts.option[String]("filter-topics", "Coverage filter: comma-separated topics the sources all have").orNone,
      Opts.option[String]("filter-tags", "Coverage filter: comma-separated tags the sources all have").orNone,
      Opts.option[String]("filter-status", "Coverage filter: comma-separated statuses, any of").orNone,
      Opts.option[String]("filter-places", "Coverage filter: comma-separated places, any of").orNone,
      Opts.option[String]("filter-period", "Coverage filter: start,end as signed years, BCE negative").orNone,
      Opts
        .options[String]("extra-query", "A further search the writer ran, reported in the Method note (repeatable)")
        .orEmpty,
      Opts
        .flag("clear-filter", "Remove the recorded filter (the Not used list and Method note stop being generated)")
        .orFalse
    ).mapN { (query, topics, tags, status, places, period, extra, clear) =>
      ArticlePatches.FilterFlags(
        query.map(ArticlePatches.FilterFlags.Query(_)),
        topics.map(ArticlePatches.CsvText(_)),
        tags.map(ArticlePatches.CsvText(_)),
        status.map(ArticlePatches.CsvText(_)),
        places.map(ArticlePatches.CsvText(_)),
        period.map(ArticlePatches.FilterFlags.Period(_)),
        extra.map(ArticlePatches.FilterFlags.ExtraQueries(_)),
        ArticlePatches.FilterFlags.Clear(clear)
      )
    }

  private val reasonFlags: Opts[List[ArticlePatches.ReasonText]] =
    Opts
      .options[String](
        "reason",
        "<citekey>=<why the article leaves it out> — sets one source's reason; <citekey>= removes it (repeatable)"
      )
      .orEmpty
      .map(_.map(ArticlePatches.ReasonText(_)))

  private val critiqueReasonFlags: Opts[List[CritiqueReason.Reason]] =
    Opts
      .options[String](
        "critique-reason",
        "<src>><dst>=<why the critique does not bear on the article> — sets the reason of one critique-checklist pair " +
          "(the critic first); <src>><dst>= removes it (repeatable)"
      )
      .orEmpty
      .map(_.map(CritiqueReason.Reason(_)))

  /** `article coverage <slug>` (#958) — the article's recorded filter against the live corpus. */
  private def coverage(using console: Console) = Opts.subcommand(
    "coverage",
    "The article's filter against the corpus: sources cited, not used (with reasons), statuses, edges, drift, the critique checklist (disagreements checked), and the generated sections"
  ) {
    (Opts.argument[String]("slug"), Output.jsonFlag).mapN { (slug, json) => (c: Client) =>
      c.articleCoverage(ReviewGoal.Slug(slug)).map(r => Output.emit(json, r)(Article.Coverage.markdown(_).value))
    }
  }

  /** `article review <slug>` — where the article stands with the review panel, and its review history. */
  private def review(using console: Console) = Opts.subcommand(
    "review",
    "The article's review state (unreviewed, queued, in review, passed, majors open, stopped), the goals linking it, and every review finding by run and round with what became of it"
  ) {
    (Opts.argument[String]("slug"), Output.jsonFlag).mapN { (slug, json) => (c: Client) =>
      c.articleReview(ReviewGoal.Slug(slug)).map(r => Output.emit(json, r)(Article.ReviewView.markdown(_).value))
    }
  }

  private def patch(using console: Console) =
    Opts.subcommand("patch", "Extend or correct one article — only the parts you pass change") {
      (
        Opts.argument[String]("slug"),
        Opts.option[String]("json", "A whole ArticlePatch as JSON (the flags below override its parts)").orNone,
        Opts.option[String]("title", "New title").orNone,
        Opts.option[String]("summary", "New summary (the one-paragraph lede)").orNone,
        Opts.option[String]("kind", s"New kind (${Articles.Kinds.mkString("|")})").orNone,
        Opts.option[String]("body-file", "File whose Markdown REPLACES the body").orNone,
        Opts.option[String]("append-file", "File whose Markdown is APPENDED to the body").orNone,
        Opts.option[String]("tags-add", "Comma-separated tags to add").orNone,
        Opts.option[String]("tags-remove", "Comma-separated tags to remove").orNone,
        Opts.option[String]("tags-replace", "Comma-separated tags replacing the current set").orNone,
        Opts.option[String]("regions", "Comma-separated regions replacing the current list").orNone,
        Opts.option[String]("period", "start,end as signed years, BCE negative (e.g. -1300,-1100)").orNone,
        Opts.option[String]("if-updated-at", "Precondition: the updatedAt you last read").orNone,
        strictFlag,
        filterFlags,
        reasonFlags,
        critiqueReasonFlags
      ).mapN {
        (
            slug,
            json,
            title,
            summary,
            kind,
            bodyF,
            appendF,
            tAdd,
            tRem,
            tRep,
            regions,
            period,
            ifUpd,
            strict,
            ff,
            rs,
            crs
        ) => (c: Client) =>
          ArticlePatches
            .build(
              json.map(ArticlePatches.PatchJson(_)),
              title.map(Article.Title(_)),
              summary.map(Article.SummaryText(_)),
              kind.map(Article.Kind.Wire(_)),
              bodyF.map(ArticlePatches.TextFile(_)),
              appendF.map(ArticlePatches.TextFile(_)),
              tAdd.map(ArticlePatches.CsvText(_)),
              tRem.map(ArticlePatches.CsvText(_)),
              tRep.map(ArticlePatches.CsvText(_)),
              regions.map(ArticlePatches.CsvText(_)),
              period.map(ArticlePatches.FilterFlags.Period(_)),
              ifUpd.map(Article.UpdatedAt(_))
            )
            .flatMap(ArticlePatches.withCoverage(_, ff, rs))
            .flatMap(ArticlePatches.withCritiques(_, crs))
            .flatMap(p =>
              c.patchArticle(ReviewGoal.Slug(slug), p, Cli.Strict(strict)).map { v =>
                Cli.out.println(ArticleMarkdown.article(v))
                v.article.updatedAt.foreach(u => Cli.out.println(s"\nupdatedAt: $u"))
              }
            )
      }
    }

  private def versions(using console: Console) = Opts.subcommand("versions", "Show the edit history of one article") {
    Opts
      .argument[String]("slug")
      .map(slug =>
        (c: Client) =>
          c.articleVersions(ReviewGoal.Slug(slug))
            .map(vs => Cli.out.println(ArticleMarkdown.versions(Article.Slug(slug), vs)))
      )
  }

  private def versionCmd(using console: Console) =
    Opts.subcommand("version", "Show one historical version's full body") {
      (Opts.argument[String]("slug"), Opts.argument[Long]("id")).mapN { (slug, id) => (c: Client) =>
        c.articleVersion(ReviewGoal.Slug(slug), Article.VersionId(id))
          .map(v => Cli.out.println(ArticleMarkdown.version(v)))
      }
    }

  /** The `article` command group, added to `histmersion`'s subcommands by [[Cli]]. */
  def command(using console: Console): Opts[Client => Either[Cli.Error, Unit]] =
    Opts.subcommand(
      "article",
      "The compiled wiki: list/get/put/patch/lint/check/coverage/review/rename/export/import/versions articles"
    ) {
      list orElse get orElse put orElse patch orElse lint orElse check orElse coverage orElse review orElse delete orElse exportCmd orElse importCmd orElse versions orElse versionCmd orElse
        LabelRenameCmd(Bundle.Kind.Article)
    }
