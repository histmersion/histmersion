package com.histmersion
package cli
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.RawJson
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.api.problem.Text.sentence

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.exchange.Bundle

import scala.annotation.tailrec
import com.histmersion.api.exchange.MergeMarkdown
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
            case Left(e) => loop(tail, number + 1, imported, failed :+ s"$name: ${e.message.sentence}", skipped)
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
          case Left(e) => Some(s"$slug: ${e.message.sentence}")
      }
      Cli.out.println(s"exported ${total - failed.size} article(s) to $out, failed ${failed.size}")
      failed.take(10).foreach(f => Cli.err.println(s"  ! $f"))
      if failed.isEmpty then Right(Exported(total))
      else
        Left(
          Cli.Error(
            Problem.Unclassified(Legacy.Text(s"${failed.size} of $total article(s) could not be exported")),
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

  private def usage(problem: Problem): Cli.Error = Cli.Error(problem, exitCode = ExitCode.Usage)

  private def readFile(p: Path): Either[Cli.Error, String] =
    if Files.isRegularFile(p) then Right(Files.readString(p))
    else Left(usage(Problem.Unclassified(Legacy.Text(s"no such file: $p"))))

  /** Carries out ArticleCommand commands against one server. */
  final private[cli] class Perform(c: Client)(using console: Console):
    def apply(cmd: ArticleCommand): Either[Cli.Error, Unit] =
      cmd match
        case ArticleCommand.Listing(text, kind, f, json) =>
          c.listArticles(Api.ArticleQuery(f, kind, text)).map(r => Output.emit(json, r)(ArticleMarkdown.list))
        case ArticleCommand.Get(slug, parts, json) =>
          c.getArticle(slug).map(v => Output.emit(json, v)(ArticleMarkdown.article(_, parts.toList)))
        case ArticleCommand.Put(p, kind, strict) =>
          if !Files.isRegularFile(p) then Left(usage(Problem.Unclassified(Legacy.Text(s"no such file: $p"))))
          else
            // an explicit `put` is always deliberate → force past the "newer in DB" guard
            val fallback = kind.getOrElse(kindOf(p.toAbsolutePath.getParent))
            val req = toImport(p, fallback, force = Api.Overwrite(true))
              .modify(_.strict)
              .setTo(Article.Import.Strict(strict.value))
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
        case ArticleCommand.Lint(slug, fix, json) =>
          (if fix.value then c.fixArticleFormat(slug) else c.lintArticle(slug))
            .map(r => Output.emit(json, r)(FormatMarkdown.report))
        case ArticleCommand.Check(slug, draft, kind, json) =>
          val req = draft match
            case ArticleCommand.Draft.Edits(e) =>
              readFile(e)
                .flatMap(t =>
                  RawJson
                    .decode[List[Article.Edits.EditAnswer]](t)
                    .left
                    .map(err => usage(Problem.Unclassified(Legacy.Text(s"$e: not a JSON list of edits ($err)"))))
                )
                .map(es => Article.Edits.ArticleCheckRequest(edits = es, kind = kind))
            case ArticleCommand.Draft.Body(b) =>
              readFile(b).map(t =>
                Article.Edits.ArticleCheckRequest(
                  body = Some(Article.Body(FileFormat.splitFrontmatter(FileFormat.Text(t))._2.value)),
                  kind = kind
                )
              )
          req.flatMap(c.checkArticle(slug, _)).map(r => Output.emit(json, r)(Article.Edits.markdown(_).value))
        case ArticleCommand.Coverage(slug, json) =>
          c.articleCoverage(slug).map(r => Output.emit(json, r)(Article.Coverage.markdown(_).value))
        case ArticleCommand.Review(slug, json) =>
          c.articleReview(slug).map(r => Output.emit(json, r)(Article.ReviewView.markdown(_).value))
        case ArticleCommand.Delete(slug) =>
          c.deleteArticle(slug).map(d => Cli.out.println(s"deleted ${d.slug}"))
        case ArticleCommand.Export(out) =>
          val _ = Files.createDirectories(out)
          exportAll(c, out).map(_ => ())
        case ArticleCommand.Import(p, force) =>
          if !Files.isDirectory(p) then Left(usage(Problem.Unclassified(Legacy.Text(s"not a directory: $p"))))
          else Right(report(importAll(c, p, force, Set(WikiLayout.SourcesDir))))
        case ArticleCommand.Patch(slug, e, strict, ff, rs, crs) =>
          ArticlePatches
            .build(
              e.json,
              e.title,
              e.summary,
              e.kind,
              e.body,
              e.append,
              e.tagsAdd,
              e.tagsRemove,
              e.tagsReplace,
              e.regions,
              e.period,
              e.ifUpdatedAt
            )
            .flatMap(ArticlePatches.withCoverage(_, ff, rs))
            .flatMap(ArticlePatches.withCritiques(_, crs))
            .flatMap(p =>
              c.patchArticle(slug, p, strict).map { v =>
                Cli.out.println(ArticleMarkdown.article(v))
                v.article.updatedAt.foreach(u => Cli.out.println(s"\nupdatedAt: $u"))
              }
            )
        case ArticleCommand.Versions(slug) =>
          c.articleVersions(slug).map(vs => Cli.out.println(ArticleMarkdown.versions(Article.Slug(slug.value), vs)))
        case ArticleCommand.Version(slug, id) =>
          c.articleVersion(slug, id).map(v => Cli.out.println(ArticleMarkdown.version(v)))
        case ArticleCommand.Rename(from, to, json) =>
          c.renameArticle(from, to).map(x => Output.emit(json, x)(MergeMarkdown.renamed))
