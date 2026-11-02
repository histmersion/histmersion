package com.histmersion.cli

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
import com.histmersion.api.research.{ArticleCoverage, ArticleEdits, ArticleImport, ArticleMarkdown, ArticleReviewView}
import com.histmersion.util.data.exchange.Bundle

/** `histmersion article …` — the compiled wiki over HTTP (plan §5.2 stream 2.7).
  *
  * The CLI stays a pure HTTP client: it reads the host's `wiki/<kind>/<slug>.md` files and POSTs their text to
  * `/api/articles/import`, exactly as `histmersion seed` does for source notes, because the containerised server cannot
  * see the host filesystem.
  */
object ArticleCli:

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
    val SourcesDir: String = MirrorPlacement.SourcesDirectory

    def of(dir: Path): WikiLayout =
      val here = Option(dir.getFileName).map(_.toString).getOrElse("")
      if Files.isDirectory(dir.resolve(SourcesDir)) then WikiLayout(dir.resolve(SourcesDir), Some(dir))
      else if here == SourcesDir then WikiLayout(dir, Option(dir.toAbsolutePath.getParent))
      else WikiLayout(dir, None)

  /** Article note files under `root`: every `*.md` in a subdirectory other than `sources` (the directory name is the
    * fallback KIND — `concepts/` holds `concept`s), plus any `*.md` sitting at the top level (e.g. `wiki/index.md`).
    * Frontmatter `type:` always wins over the directory.
    */
  def files(root: Path, skip: Set[String] = Set(WikiLayout.SourcesDir)): List[(Path, String)] =
    val top = markdown(root).map(_ -> kindOf(root))
    val sub = dirs(root)
      .filterNot(d => skip.contains(d.getFileName.toString.toLowerCase))
      .flatMap(d => markdown(d).map(_ -> kindOf(d)))
    (top ++ sub).sortBy((p, _) => p.toString)

  private def kindOf(dir: Path): String =
    com.histmersion.api.research.Articles.kindOfDir(Option(dir.getFileName).map(_.toString).getOrElse(""))

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
  def toImport(file: Path, kind: String, force: Boolean): ArticleImport =
    ArticleImport(
      text = Files.readString(file),
      slug = Some(file.getFileName.toString.stripSuffix(".md")),
      kind = Some(kind),
      mtime = Some(Files.getLastModifiedTime(file).toMillis),
      force = force
    )

  /** Import every article note under `root`, printing progress. Returns the accumulated result so a caller
    * (`histmersion seed`) can fold it into its own report.
    */
  def importAll(c: Client, root: Path, force: Boolean, skip: Set[String]): SeedResult =
    val fs = files(root, skip)
    val total = fs.size
    if total > 0 then println(s"importing $total article(s) from $root")
    @tailrec def loop(
        rest: List[(Path, String)],
        number: Int,
        imported: Int,
        failed: List[String],
        skipped: List[String]
    ): SeedResult =
      rest match
        case Nil               => SeedResult(imported, failed, 0, skipped)
        case (f, kind) :: tail =>
          val name = f.getFileName.toString.stripSuffix(".md")
          println(s"$number/$total $kind/$name")
          c.importArticle(toImport(f, kind, force)) match
            case Right(r) => loop(tail, number + 1, imported + r.imported, failed ++ r.failed, skipped ++ r.skipped)
            case Left(e)  => loop(tail, number + 1, imported, failed :+ s"$name: ${e.message}", skipped)
    loop(fs, 1, 0, Nil, Nil)

  /** Write every article to `<dir>/<kind>/<slug>.md`. Returns how many were written. */
  def exportAll(c: Client, out: Path): Either[CliError, Int] =
    c.articleSlugs.flatMap { slugs =>
      val total = slugs.size
      val failed = slugs.zipWithIndex.flatMap { (slug, i) =>
        println(s"${i + 1}/$total $slug")
        c.exportArticle(slug) match
          case Right(text) =>
            val dir = out.resolve(kindOfNote(text))
            val _ = Files.createDirectories(dir)
            val _ = Files.writeString(dir.resolve(MirrorPlacement.noteFile(slug)), text)
            None
          case Left(e) => Some(s"$slug: ${e.message}")
      }
      println(s"exported ${total - failed.size} article(s) to $out, failed ${failed.size}")
      failed.take(10).foreach(f => Console.err.println(s"  ! $f"))
      if failed.isEmpty then Right(total)
      else Left(CliError(s"${failed.size} of $total article(s) could not be exported", exitCode = ExitCode.Refused))
    }

  /** The `type:` of an exported note — which subdirectory it belongs in. `ArticleNote.render` always writes the key, so
    * the fallback only guards a hand-edited file.
    */
  private val TypeKey = """(?m)^type:\s*["']?([^"'\r\n]+)["']?\s*$""".r
  def kindOfNote(text: String): String =
    TypeKey
      .findFirstMatchIn(text)
      .map(_.group(1).trim)
      .filter(_.nonEmpty)
      .getOrElse(com.histmersion.api.research.Articles.Kinds.head)

  // ── the `histmersion article …` subcommands ──────────────────────────────────────────────────────

  /** Progress report of an article import (also used by `histmersion seed`). */
  def report(r: SeedResult): Unit =
    println(s"imported ${r.imported}, skipped ${r.skipped.size} (newer in DB), failed ${r.failed.size}")
    r.skipped.take(10).foreach(s => println(s"  ~ $s"))
    r.failed.take(10).foreach(f => Console.err.println(s"  ! $f"))

  /** The filter flags are the shared ones: they select SOURCES, and an article is listed when it cites one of them
    * (`--title` is the article's own title). The positional query is the article's own full text.
    */
  private val list =
    Opts.subcommand("list", "List/search compiled articles (the filter flags select the sources they cite)") {
      (
        Opts.argument[String]("query").orNone,
        Opts
          .option[String]("kind", s"Article kind (${com.histmersion.api.research.Articles.Kinds.mkString("|")})")
          .orNone,
        FilterFlags.opts,
        Output.jsonFlag
      ).mapN { (q, kind, f, json) => (c: Client) =>
        c.listArticles(Api.ArticleQuery(f, kind, q.map(_.trim).filter(_.nonEmpty)))
          .map(r => Output.emit(json, r)(ArticleMarkdown.list))
      }
    }

  private val get =
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
        c.getArticle(slug).map { v =>
          val ps = parts.map(_.split(',').map(_.trim.toLowerCase).toList).getOrElse(Nil)
          Output.emit(json, v)(ArticleMarkdown.article(_, ps))
        }
      }
    }

  private val strictFlag =
    Opts
      .flag("strict", "Refuse the write when the Markdown has a formatting error (default: write, then list them)")
      .orFalse

  private val put = Opts.subcommand("put", "Send ONE article note file (frontmatter + body) as a full create/replace") {
    (
      Opts.argument[String]("file.md"),
      Opts.option[String]("kind", "Kind when the frontmatter has no `type:`").orNone,
      strictFlag
    )
      .mapN { (file, kind, strict) => (c: Client) =>
        val p = Path.of(file)
        if !Files.isRegularFile(p) then Left(CliError(s"no such file: $file", exitCode = ExitCode.Usage))
        else
          // an explicit `put` is always deliberate → force past the "newer in DB" guard
          val fallback = kind.getOrElse(kindOf(p.toAbsolutePath.getParent))
          val req = toImport(p, fallback, force = true).copy(strict = strict)
          c.importArticle(req).map { r =>
            report(r)
            // the import answers with counts only: ask for the article's formatting problems by the
            // slug the note itself declares (as the server parsed it), so the writer sees them now
            val slug = ArticleNote.parse(req.text, req.slug.getOrElse(""), fallback).map(_.slug).filter(_.nonEmpty)
            if r.imported > 0 then
              slug.foreach(s => c.lintArticle(s).foreach(f => println(FormatMarkdown.notice(f.issues))))
          }
      }
  }

  /** `article lint <slug> [--fix]` — what in the article will not display as written. */
  private val lint = Opts.subcommand(
    "lint",
    "Formatting problems of one article's body and summary (--fix applies the wording-safe repairs)"
  ) {
    (
      Opts.argument[String]("slug"),
      Opts.flag("fix", "Apply the repairs that never change wording, then report what is left").orFalse,
      Output.jsonFlag
    ).mapN { (slug, fix, json) => (c: Client) =>
      (if fix then c.fixArticleFormat(slug) else c.lintArticle(slug))
        .map(r => Output.emit(json, r)(FormatMarkdown.report))
    }
  }

  /** `article check <slug> (--edits-file f.json | --body-file f.md) [--kind k]` — dry-run a change as the review
    * pipeline checks its fixer's answer: nothing is written.
    */
  private val check = Opts.subcommand(
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
        else Left(CliError(s"no such file: $f", exitCode = ExitCode.Usage))
      val req = (editsFile, bodyFile) match
        case (Some(e), None) =>
          read(e)
            .flatMap(t =>
              io.circe.parser
                .decode[List[ArticleEdits.EditAnswer]](t)
                .left
                .map(err => CliError(s"$e: not a JSON list of edits (${err.getMessage})", exitCode = ExitCode.Usage))
            )
            .map(es => ArticleEdits.ArticleCheckRequest(edits = es, kind = kind))
        case (None, Some(b)) =>
          read(b).map(t =>
            ArticleEdits.ArticleCheckRequest(body = Some(FileFormat.splitFrontmatter(t)._2), kind = kind)
          )
        case _ => Left(CliError("give --edits-file or --body-file (one of them)", exitCode = ExitCode.Usage))
      req.flatMap(c.checkArticle(slug, _)).map(r => Output.emit(json, r)(ArticleEdits.markdown))
    }
  }

  private val delete = Opts.subcommand("delete", "Remove one article (its links go with it)") {
    Opts
      .argument[String]("slug")
      .map(slug => (c: Client) => c.deleteArticle(slug).map(d => println(s"deleted ${d.slug}")))
  }

  private val exportCmd = Opts.subcommand("export", "Write each article to <dir>/<kind>/<slug>.md") {
    Opts.argument[String]("dir").map { dir => (c: Client) =>
      val out = Path.of(dir)
      val _ = Files.createDirectories(out)
      exportAll(c, out).map(_ => ())
    }
  }

  private val importCmd = Opts.subcommand("import", "Import <dir>/<kind>/*.md (and any *.md in <dir> itself)") {
    (Opts.argument[String]("dir"), Opts.flag("force", "Overwrite articles newer than the files").orFalse)
      .mapN { (dir, force) => (c: Client) =>
        val p = Path.of(dir)
        if !Files.isDirectory(p) then Left(CliError(s"not a directory: $dir", exitCode = ExitCode.Usage))
        else Right(report(importAll(c, p, force, Set(WikiLayout.SourcesDir))))
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
    ).mapN(ArticlePatches.FilterFlags.apply)

  private val reasonFlags: Opts[List[String]] =
    Opts
      .options[String](
        "reason",
        "<citekey>=<why the article leaves it out> — sets one source's reason; <citekey>= removes it (repeatable)"
      )
      .orEmpty

  private val critiqueReasonFlags: Opts[List[String]] =
    Opts
      .options[String](
        "critique-reason",
        "<src>><dst>=<why the critique does not bear on the article> — sets the reason of one critique-checklist pair " +
          "(the critic first); <src>><dst>= removes it (repeatable)"
      )
      .orEmpty

  /** `article coverage <slug>` (#958) — the article's recorded filter against the live corpus. */
  private val coverage = Opts.subcommand(
    "coverage",
    "The article's filter against the corpus: sources cited, not used (with reasons), statuses, edges, drift, the critique checklist (disagreements checked), and the generated sections"
  ) {
    (Opts.argument[String]("slug"), Output.jsonFlag).mapN { (slug, json) => (c: Client) =>
      c.articleCoverage(slug).map(r => Output.emit(json, r)(ArticleCoverage.markdown))
    }
  }

  /** `article review <slug>` — where the article stands with the review panel, and its review history. */
  private val review = Opts.subcommand(
    "review",
    "The article's review state (unreviewed, queued, in review, passed, majors open, stopped), the goals linking it, and every review finding by run and round with what became of it"
  ) {
    (Opts.argument[String]("slug"), Output.jsonFlag).mapN { (slug, json) => (c: Client) =>
      c.articleReview(slug).map(r => Output.emit(json, r)(ArticleReviewView.markdown))
    }
  }

  private val patch = Opts.subcommand("patch", "Extend or correct one article — only the parts you pass change") {
    (
      Opts.argument[String]("slug"),
      Opts.option[String]("json", "A whole ArticlePatch as JSON (the flags below override its parts)").orNone,
      Opts.option[String]("title", "New title").orNone,
      Opts.option[String]("summary", "New summary (the one-paragraph lede)").orNone,
      Opts.option[String]("kind", s"New kind (${com.histmersion.api.research.Articles.Kinds.mkString("|")})").orNone,
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
          .build(json, title, summary, kind, bodyF, appendF, tAdd, tRem, tRep, regions, period, ifUpd)
          .flatMap(ArticlePatches.withCoverage(_, ff, rs))
          .flatMap(ArticlePatches.withCritiques(_, crs))
          .flatMap(p =>
            c.patchArticle(slug, p, strict).map { v =>
              println(ArticleMarkdown.article(v))
              v.article.updatedAt.foreach(u => println(s"\nupdatedAt: $u"))
            }
          )
    }
  }

  private val versions = Opts.subcommand("versions", "Show the edit history of one article") {
    Opts
      .argument[String]("slug")
      .map(slug => (c: Client) => c.articleVersions(slug).map(vs => println(ArticleMarkdown.versions(slug, vs))))
  }

  private val versionCmd = Opts.subcommand("version", "Show one historical version's full body") {
    (Opts.argument[String]("slug"), Opts.argument[Long]("id")).mapN { (slug, id) => (c: Client) =>
      c.articleVersion(slug, id).map(v => println(ArticleMarkdown.version(v)))
    }
  }

  /** The `article` command group, added to `histmersion`'s subcommands by [[Cli]]. */
  val command: Opts[Client => Either[CliError, Unit]] =
    Opts.subcommand(
      "article",
      "The compiled wiki: list/get/put/patch/lint/check/coverage/review/rename/export/import/versions articles"
    ) {
      list orElse get orElse put orElse patch orElse lint orElse check orElse coverage orElse review orElse delete orElse exportCmd orElse importCmd orElse versions orElse versionCmd orElse
        LabelRenameCmd(Bundle.Kind.Article)
    }
