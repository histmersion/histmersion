package com.histmersion.cli

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.core.*

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** `histmersion article …` — the compiled wiki over HTTP (plan §5.2 stream 2.7).
  *
  * The CLI stays a pure HTTP client: it reads the host's `wiki/<kind>/<slug>.md` files and POSTs
  * their text to `/api/articles/import`, exactly as `histmersion seed` does for source notes, because the
  * containerised server cannot see the host filesystem.
  */
object ArticleCli:

  // ── host-side wiki layout ────────────────────────────────────────────────────────────────────

  /** Where the article notes are, given whatever directory the user pointed at.
    *
    * `histmersion seed ~/research/wiki` and `histmersion seed ~/research/wiki/sources` must both work: the
    * first is the wiki root (source notes in `sources/`, articles in the sibling directories), the
    * second is the source-note directory itself (articles are then one level up). Anything else is
    * treated as a plain directory of notes, as before.
    */
  final case class WikiLayout(sources: Path, root: Option[Path])

  object WikiLayout:
    val SourcesDir = "sources"

    def of(dir: Path): WikiLayout =
      val here = Option(dir.getFileName).map(_.toString).getOrElse("")
      if Files.isDirectory(dir.resolve(SourcesDir)) then WikiLayout(dir.resolve(SourcesDir), Some(dir))
      else if here == SourcesDir then WikiLayout(dir, Option(dir.toAbsolutePath.getParent))
      else WikiLayout(dir, None)

  /** Article note files under `root`: every `*.md` in a subdirectory other than `sources` (the
    * directory name is the fallback KIND — `concepts/` holds `concept`s), plus any `*.md` sitting at
    * the top level (e.g. `wiki/index.md`). Frontmatter `type:` always wins over the directory. */
  def files(root: Path, skip: Set[String] = Set(WikiLayout.SourcesDir)): List[(Path, String)] =
    val top = markdown(root).map(_ -> kindOf(root))
    val sub = dirs(root)
      .filterNot(d => skip.contains(d.getFileName.toString.toLowerCase))
      .flatMap(d => markdown(d).map(_ -> kindOf(d)))
    (top ++ sub).sortBy((p, _) => p.toString)

  private def kindOf(dir: Path): String =
    com.histmersion.core.Articles.kindOfDir(Option(dir.getFileName).map(_.toString).getOrElse(""))

  private def markdown(dir: Path): List[Path] =
    if !Files.isDirectory(dir) then Nil
    else
      val s = Files.list(dir)
      try s.iterator.asScala.filter(p => Files.isRegularFile(p) && p.getFileName.toString.endsWith(".md"))
        .toList.sortBy(_.getFileName.toString)
      finally s.close()

  private def dirs(dir: Path): List[Path] =
    if !Files.isDirectory(dir) then Nil
    else
      val s = Files.list(dir)
      try s.iterator.asScala.filter(Files.isDirectory(_)).toList.sortBy(_.getFileName.toString)
      finally s.close()

  /** Package one article note for the server: text, fallback slug/kind, and the mtime that drives the
    * "stored row is newer than the file" skip. */
  def toImport(file: Path, kind: String, force: Boolean): ArticleImport =
    ArticleImport(
      text = Files.readString(file),
      slug = Some(file.getFileName.toString.stripSuffix(".md")),
      kind = Some(kind),
      mtime = Some(Files.getLastModifiedTime(file).toMillis),
      force = force)

  /** Import every article note under `root`, printing progress. Returns the accumulated result so a
    * caller (`histmersion seed`) can fold it into its own report. */
  def importAll(c: Client, root: Path, force: Boolean, skip: Set[String]): SeedResult =
    val fs    = files(root, skip)
    val total = fs.size
    if total > 0 then println(s"importing $total article(s) from $root")
    fs.zipWithIndex.foldLeft(SeedResult(0, Nil, 0, Nil)) { case (sum, ((f, kind), i)) =>
      val name = f.getFileName.toString.stripSuffix(".md")
      println(s"${i + 1}/$total $kind/$name")
      c.importArticle(toImport(f, kind, force)) match
        case Right(r) => sum.copy(imported = sum.imported + r.imported,
            failed = sum.failed ++ r.failed, skipped = sum.skipped ++ r.skipped)
        case Left(e)  => sum.copy(failed = sum.failed :+ s"$name: ${e.message}")
    }

  /** Write every article to `<dir>/<kind>/<slug>.md`. Returns how many were written. */
  def exportAll(c: Client, out: Path): Either[CliError, Int] =
    c.articleSlugs.flatMap { slugs =>
      val total  = slugs.size
      val failed = slugs.zipWithIndex.flatMap { (slug, i) =>
        println(s"${i + 1}/$total $slug")
        c.exportArticle(slug) match
          case Right(text) =>
            val dir = out.resolve(kindOfNote(text))
            val _   = Files.createDirectories(dir)
            val _   = Files.writeString(dir.resolve(s"$slug.md"), text)
            None
          case Left(e) => Some(s"$slug: ${e.message}")
      }
      println(s"exported ${total - failed.size} article(s) to $out, failed ${failed.size}")
      failed.take(10).foreach(f => Console.err.println(s"  ! $f"))
      if failed.isEmpty then Right(total)
      else Left(CliError(s"${failed.size} of $total article(s) could not be exported", exitCode = 2))
    }

  /** The `type:` of an exported note — which subdirectory it belongs in. `ArticleNote.render` always
    * writes the key, so the fallback only guards a hand-edited file. */
  private val TypeKey = """(?m)^type:\s*["']?([^"'\r\n]+)["']?\s*$""".r
  def kindOfNote(text: String): String =
    TypeKey.findFirstMatchIn(text).map(_.group(1).trim).filter(_.nonEmpty)
      .getOrElse(com.histmersion.core.Articles.Kinds.head)

  // ── the `histmersion article …` subcommands ──────────────────────────────────────────────────────

  /** Progress report of an article import (also used by `histmersion seed`). */
  def report(r: SeedResult): Unit =
    println(s"imported ${r.imported}, skipped ${r.skipped.size} (newer in DB), failed ${r.failed.size}")
    r.skipped.take(10).foreach(s => println(s"  ~ $s"))
    r.failed.take(10).foreach(f => Console.err.println(s"  ! $f"))

  /** The filter flags are the shared ones: they select SOURCES, and an article is listed when it
    * cites one of them (`--title` is the article's own title). The positional query is the
    * article's own full text. */
  private val list = Opts.subcommand("list", "List/search compiled articles (the filter flags select the sources they cite)") {
    (
      Opts.argument[String]("query").orNone,
      Opts.option[String]("kind", s"Article kind (${com.histmersion.core.Articles.Kinds.mkString("|")})").orNone,
      FilterFlags.opts
    ).mapN { (q, kind, f) => (c: Client) =>
      c.listArticles(Api.ArticleQuery(f, kind, q.map(_.trim).filter(_.nonEmpty)))
        .map(r => println(ArticleMarkdown.list(r)))
    }
  }

  private val get = Opts.subcommand("get", "Show one article (parts: body,summary,cited,backlinks — default all)") {
    (
      Opts.argument[String]("slug"),
      Opts.option[String]("parts", "Comma-separated parts to return: body,summary,cited,backlinks (default: all)").orNone
    ).mapN { (slug, parts) => (c: Client) =>
      c.getArticle(slug).map { v =>
        val ps = parts.map(_.split(',').map(_.trim.toLowerCase).toList).getOrElse(Nil)
        println(ArticleMarkdown.article(v, ps))
      }
    }
  }

  private val strictFlag =
    Opts.flag("strict", "Refuse the write when the Markdown has a formatting error (default: write, then list them)").orFalse

  private val put = Opts.subcommand("put", "Send ONE article note file (frontmatter + body) as a full create/replace") {
    (Opts.argument[String]("file.md"), Opts.option[String]("kind", "Kind when the frontmatter has no `type:`").orNone, strictFlag)
      .mapN { (file, kind, strict) => (c: Client) =>
        val p = Path.of(file)
        if !Files.isRegularFile(p) then Left(CliError(s"no such file: $file", exitCode = 1))
        else
          // an explicit `put` is always deliberate → force past the "newer in DB" guard
          val fallback = kind.getOrElse(kindOf(p.toAbsolutePath.getParent))
          val req      = toImport(p, fallback, force = true).copy(strict = strict)
          c.importArticle(req).map { r =>
            report(r)
            // the import answers with counts only: ask for the article's formatting problems by the
            // slug the note itself declares (as the server parsed it), so the writer sees them now
            val slug = ArticleNote.parse(req.text, req.slug.getOrElse(""), fallback).map(_.slug).filter(_.nonEmpty)
            if r.imported > 0 then slug.foreach(s => c.lintArticle(s).foreach(f => println(FormatMarkdown.notice(f.issues))))
          }
      }
  }

  /** `article lint <slug> [--fix]` — what in the article will not display as written. */
  private val lint = Opts.subcommand("lint", "Formatting problems of one article's body and summary (--fix applies the wording-safe repairs)") {
    (
      Opts.argument[String]("slug"),
      Opts.flag("fix", "Apply the repairs that never change wording, then report what is left").orFalse,
      Opts.flag("json", "Print raw JSON instead of Markdown").orFalse
    ).mapN { (slug, fix, json) => (c: Client) =>
      (if fix then c.fixArticleFormat(slug) else c.lintArticle(slug))
        .map(r => println(if json then io.circe.syntax.EncoderOps(r).asJson.spaces2 else FormatMarkdown.report(r)))
    }
  }

  /** `article check <slug> (--edits-file f.json | --body-file f.md) [--kind k]` — dry-run a change as
    * the review pipeline checks its fixer's answer: nothing is written. */
  private val check = Opts.subcommand("check",
      "Dry-run edits to an article (or a whole draft body) and list the problems the review checks would find — writes nothing") {
    (
      Opts.argument[String]("slug"),
      Opts.option[String]("edits-file", "A JSON list of edits: [{\"op\": \"replace|insertAfter|insertBefore|delete\", \"anchor\": \"…\", \"text\": \"…\"}]").orNone,
      Opts.option[String]("body-file", "Instead of edits: a whole draft body (Markdown) to check").orNone,
      Opts.option[String]("kind", "Check the section set of this kind instead of the article's (index = none, for a fragment)").orNone,
      Opts.flag("json", "Print raw JSON instead of Markdown").orFalse
    ).mapN { (slug, editsFile, bodyFile, kind, json) => (c: Client) =>
      def read(f: String) =
        val p = Path.of(f)
        if Files.isRegularFile(p) then Right(Files.readString(p)) else Left(CliError(s"no such file: $f", exitCode = 1))
      val req = (editsFile, bodyFile) match
        case (Some(e), None) => read(e).flatMap(t => io.circe.parser.decode[List[ArticleEdits.EditAnswer]](t)
                                  .left.map(err => CliError(s"$e: not a JSON list of edits (${err.getMessage})", exitCode = 1)))
                                  .map(es => ArticleEdits.ArticleCheckRequest(edits = es, kind = kind))
        case (None, Some(b)) => read(b).map(t => ArticleEdits.ArticleCheckRequest(body = Some(FileFormat.splitFrontmatter(t)._2), kind = kind))
        case _               => Left(CliError("give --edits-file or --body-file (one of them)", exitCode = 1))
      req.flatMap(c.checkArticle(slug, _)).map(r =>
        println(if json then io.circe.syntax.EncoderOps(r).asJson.spaces2 else ArticleEdits.markdown(r)))
    }
  }

  private val delete = Opts.subcommand("delete", "Remove one article (its links go with it)") {
    Opts.argument[String]("slug").map(slug => (c: Client) =>
      c.deleteArticle(slug).map(d => println(s"deleted ${d.slug}")))
  }

  private val exportCmd = Opts.subcommand("export", "Write each article to <dir>/<kind>/<slug>.md") {
    Opts.argument[String]("dir").map { dir => (c: Client) =>
      val out = Path.of(dir)
      val _   = Files.createDirectories(out)
      exportAll(c, out).map(_ => ())
    }
  }

  private val importCmd = Opts.subcommand("import", "Import <dir>/<kind>/*.md (and any *.md in <dir> itself)") {
    (Opts.argument[String]("dir"), Opts.flag("force", "Overwrite articles newer than the files").orFalse)
      .mapN { (dir, force) => (c: Client) =>
        val p = Path.of(dir)
        if !Files.isDirectory(p) then Left(CliError(s"not a directory: $dir", exitCode = 1))
        else Right(report(importAll(c, p, force, Set(WikiLayout.SourcesDir))))
      }
  }

  private val patch = Opts.subcommand("patch", "Extend or correct one article — only the parts you pass change") {
    (
      Opts.argument[String]("slug"),
      Opts.option[String]("json", "A whole ArticlePatch as JSON (the flags below override its parts)").orNone,
      Opts.option[String]("title", "New title").orNone,
      Opts.option[String]("summary", "New summary (the one-paragraph lede)").orNone,
      Opts.option[String]("kind", s"New kind (${com.histmersion.core.Articles.Kinds.mkString("|")})").orNone,
      Opts.option[String]("body-file", "File whose Markdown REPLACES the body").orNone,
      Opts.option[String]("append-file", "File whose Markdown is APPENDED to the body").orNone,
      Opts.option[String]("tags-add", "Comma-separated tags to add").orNone,
      Opts.option[String]("tags-remove", "Comma-separated tags to remove").orNone,
      Opts.option[String]("tags-replace", "Comma-separated tags replacing the current set").orNone,
      Opts.option[String]("regions", "Comma-separated regions replacing the current list").orNone,
      Opts.option[String]("period", "start,end as signed years, BCE negative (e.g. -1300,-1100)").orNone,
      Opts.option[String]("if-updated-at", "Precondition: the updatedAt you last read").orNone,
      strictFlag
    ).mapN { (slug, json, title, summary, kind, bodyF, appendF, tAdd, tRem, tRep, regions, period, ifUpd, strict) =>
      (c: Client) =>
        ArticlePatches
          .build(json, title, summary, kind, bodyF, appendF, tAdd, tRem, tRep, regions, period, ifUpd)
          .flatMap(p =>
            c.patchArticle(slug, p, strict).map { v =>
              println(ArticleMarkdown.article(v))
              v.article.updatedAt.foreach(u => println(s"\nupdatedAt: $u"))
            })
    }
  }

  private val versions = Opts.subcommand("versions", "Show the edit history of one article") {
    Opts.argument[String]("slug").map(slug => (c: Client) =>
      c.articleVersions(slug).map(vs => println(ArticleMarkdown.versions(slug, vs))))
  }

  private val versionCmd = Opts.subcommand("version", "Show one historical version's full body") {
    (Opts.argument[String]("slug"), Opts.argument[Long]("id")).mapN { (slug, id) => (c: Client) =>
      c.articleVersion(slug, id).map(v => println(ArticleMarkdown.version(v)))
    }
  }

  /** The `article` command group, added to `histmersion`'s subcommands by [[Main]]. */
  val command: Opts[Client => Either[CliError, Unit]] =
    Opts.subcommand("article", "The compiled wiki: list/get/put/patch/lint/check/rename/export/import/versions articles") {
      list orElse get orElse put orElse patch orElse lint orElse check orElse delete orElse exportCmd orElse importCmd orElse versions orElse versionCmd orElse
        LabelRenameCmd(BundleKind.Article)
    }

/** Build an [[ArticlePatch]] from CLI flags — pure, so the flag semantics are unit-tested without a
  * server. Mirrors [[Patches]] on the source side: `--json` is the baseline, each flag overrides the
  * part it names, and an absent part stays absent (= leave alone). */
object ArticlePatches:

  def build(
      json: Option[String],
      title: Option[String],
      summary: Option[String],
      kind: Option[String],
      bodyFile: Option[String],
      appendFile: Option[String],
      tagsAdd: Option[String],
      tagsRemove: Option[String],
      tagsReplace: Option[String],
      regions: Option[String],
      period: Option[String],
      ifUpdatedAt: Option[String]
  ): Either[CliError, ArticlePatch] =
    for
      base <- parse(json)
      body <- read(bodyFile)
      app  <- read(appendFile)
      per  <- periodOf(period)
    yield
      val tags =
        if tagsAdd.isEmpty && tagsRemove.isEmpty && tagsReplace.isEmpty then base.tags
        else Some(ListPatch(csv(tagsAdd), csv(tagsRemove), tagsReplace.map(csv1)))
      base.copy(
        title = title.orElse(base.title),
        summary = summary.orElse(base.summary),
        kind = kind.orElse(base.kind),
        body = body.orElse(base.body),
        append = app.orElse(base.append),
        tags = tags,
        regions = regions.map(csv1).orElse(base.regions),
        period = per.orElse(base.period),
        ifUpdatedAt = ifUpdatedAt.orElse(base.ifUpdatedAt))

  private def parse(json: Option[String]): Either[CliError, ArticlePatch] = json match
    case None => Right(ArticlePatch())
    case Some(s) =>
      io.circe.parser.decode[ArticlePatch](s).left.map(e => CliError(s"--json: ${e.getMessage}", exitCode = 1))

  private def read(file: Option[String]): Either[CliError, Option[String]] = file match
    case None => Right(None)
    case Some(f) =>
      val p = java.nio.file.Path.of(f)
      if java.nio.file.Files.isRegularFile(p) then Right(Some(java.nio.file.Files.readString(p)))
      else Left(CliError(s"no such file: $f", exitCode = 1))

  private def periodOf(s: Option[String]): Either[CliError, Option[PeriodInput]] = s match
    case None => Right(None)
    case Some(v) =>
      v.split(",").map(_.trim).toList match
        case a :: b :: Nil =>
          (a.toLongOption, b.toLongOption) match
            case (Some(x), Some(y)) => Right(Some(PeriodInput(x, y)))
            case _                  => Left(CliError(s"--period: '$v' is not start,end", exitCode = 1))
        case _ => Left(CliError(s"--period: '$v' is not start,end", exitCode = 1))

  private def csv(s: Option[String]): List[String] = s.map(csv1).getOrElse(Nil)
  private def csv1(s: String): List[String] = s.split(",").toList.map(_.trim).filter(_.nonEmpty)
