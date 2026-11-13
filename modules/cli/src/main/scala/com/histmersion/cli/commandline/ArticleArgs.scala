package com.histmersion
package cli
package commandline

import com.histmersion.api.Api
import com.histmersion.api.aliases.{LabelRename, LabelRenames}
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.library.Filter
import com.histmersion.api.research.{Article, Articles, ReviewGoal}
import com.histmersion.cli.CommandLine.{given, *}
import com.histmersion.util.problem.{Legacy, Problem}
import org.rogach.scallop.*

import java.nio.file.Path

/** `histmersion article …` on the command line: the compiled wiki's commands, parsed into [[ArticleCommand]]. */
object ArticleArgs extends CommandLine.Entry:
  val name = "article"
  val help = "The compiled wiki: list/get/put/patch/lint/check/coverage/review/rename/export/import/versions articles"
  private val Parent = "histmersion article"

  def conf(): Subcommand = new Group("histmersion", name, help):
    holds(
      ArticleList(),
      ArticleGet(),
      ArticlePut(),
      ArticlePatch(),
      ArticleLint(),
      ArticleCheck(),
      ArticleCoverage(),
      ArticleReview(),
      ArticleDelete(),
      ArticleExport(),
      ArticleImport(),
      ArticleVersions(),
      ArticleVersion(),
      ArticleRename()
    )

  private val StrictHelp =
    "Refuse the write when the Markdown has a formatting error (default: write, then list them)"

  final private class ArticleList()
      extends Leaf(Parent, "list", "List/search compiled articles (the filter flags select the sources they cite)"):
    private val query = arg[String]("query", required = false)
    private val kind = value[Api.ArticleQuery.Kind]("kind", s"Article kind (${Articles.Kinds.mkString("|")})")
    private val filter = filterFlags()
    private val asJson = json()

    def command: Either[Problem, Cli.Command] =
      Right(
        ArticleCommand.Listing(
          query.toOption.map(_.trim).filter(_.nonEmpty).map(Api.ArticleQuery.Text(_)),
          kind.toOption,
          filter(),
          asJson()
        )
      )

  final private class ArticleGet()
      extends Leaf(Parent, "get", "Show one article (parts: body,summary,coverage,cited,backlinks — default all)"):
    private val slug = arg[ReviewGoal.Slug]("slug")
    private val parts =
      value[String]("parts", "Comma-separated parts to return: body,summary,coverage,cited,backlinks (default: all)")
    private val asJson = json()

    def command: Either[Problem, Cli.Command] =
      Right(ArticleCommand.Get(slug(), ArticleCommand.parts(parts.toOption), asJson()))

  final private class ArticlePut()
      extends Leaf(Parent, "put", "Send ONE article note file (frontmatter + body) as a full create/replace"):
    private val file = arg[Path]("file.md")
    private val kind = value[Article.Kind.Wire]("kind", "Kind when the frontmatter has no `type:`")
    private val strict = flag("strict", StrictHelp)

    def command: Either[Problem, Cli.Command] =
      Right(ArticleCommand.Put(file(), kind.toOption, Cli.Strict(strict.getOrElse(false))))

  final private class ArticlePatch()
      extends Leaf(Parent, "patch", "Extend or correct one article — only the parts you pass change"):
    private val slug = arg[ReviewGoal.Slug]("slug")
    private val json =
      value[ArticlePatches.PatchJson]("json", "A whole ArticlePatch as JSON (the flags below override its parts)")
    private val title = value[Article.Title]("title", "New title")
    private val lede = value[Article.SummaryText]("summary", "New summary (the one-paragraph lede)")
    private val kind = value[Article.Kind.Wire]("kind", s"New kind (${Articles.Kinds.mkString("|")})")
    private val bodyFile = value[ArticlePatches.TextFile]("body-file", "File whose Markdown REPLACES the body")
    private val appendFile =
      value[ArticlePatches.TextFile]("append-file", "File whose Markdown is APPENDED to the body")
    private val tagsAdd = value[ArticlePatches.CsvText]("tags-add", "Comma-separated tags to add")
    private val tagsRemove = value[ArticlePatches.CsvText]("tags-remove", "Comma-separated tags to remove")
    private val tagsReplace =
      value[ArticlePatches.CsvText]("tags-replace", "Comma-separated tags replacing the current set")
    private val regions = value[ArticlePatches.CsvText]("regions", "Comma-separated regions replacing the current list")
    private val period =
      value[ArticlePatches.FilterFlags.Period]("period", "start,end as signed years, BCE negative (e.g. -1300,-1100)")
    private val ifUpdatedAt = value[Article.UpdatedAt]("if-updated-at", "Precondition: the updatedAt you last read")
    private val strict = flag("strict", StrictHelp)
    private val filterQuery = value[ArticlePatches.FilterFlags.Query](
      "filter-query",
      "Coverage filter: the free-text query the article is compiled from (replaces the recorded filter)"
    )
    private val filterTopics =
      value[ArticlePatches.CsvText]("filter-topics", "Coverage filter: comma-separated topics the sources all have")
    private val filterTags =
      value[ArticlePatches.CsvText]("filter-tags", "Coverage filter: comma-separated tags the sources all have")
    private val filterStatus =
      value[ArticlePatches.CsvText]("filter-status", "Coverage filter: comma-separated statuses, any of")
    private val filterPlaces =
      value[ArticlePatches.CsvText]("filter-places", "Coverage filter: comma-separated places, any of")
    private val filterPeriod = value[ArticlePatches.FilterFlags.Period](
      "filter-period",
      "Coverage filter: start,end as signed years, BCE negative"
    )
    private val extraQuery = values[ArticlePatches.FilterFlags.ExtraQueries](
      "extra-query",
      "A further search the writer ran, reported in the Method note (repeatable)"
    )
    private val clearFilter =
      flag("clear-filter", "Remove the recorded filter (the Not used list and Method note stop being generated)")
    private val reason = values[ArticlePatches.ReasonText](
      "reason",
      "<citekey>=<why the article leaves it out> — sets one source's reason; <citekey>= removes it (repeatable)"
    )
    private val critiqueReason = values[String](
      "critique-reason",
      "<src>><dst>=<why the critique does not bear on the article> — sets the reason of one critique-checklist pair " +
        "(the critic first); <src>><dst>= removes it (repeatable)"
    )

    def command: Either[Problem, Cli.Command] =
      Right(
        ArticleCommand.Patch(
          slug(),
          ArticleCommand.Edit(
            json.toOption,
            title.toOption,
            lede.toOption,
            kind.toOption,
            bodyFile.toOption,
            appendFile.toOption,
            tagsAdd.toOption,
            tagsRemove.toOption,
            tagsReplace.toOption,
            regions.toOption,
            period.toOption,
            ifUpdatedAt.toOption
          ),
          Cli.Strict(strict.getOrElse(false)),
          ArticlePatches.FilterFlags(
            filterQuery.toOption,
            filterTopics.toOption,
            filterTags.toOption,
            filterStatus.toOption,
            filterPlaces.toOption,
            filterPeriod.toOption,
            extraQuery.getOrElse(Nil),
            ArticlePatches.FilterFlags.Clear(clearFilter.getOrElse(false))
          ),
          reason.getOrElse(Nil),
          critiqueReason.getOrElse(Nil).map(text => Problem.Unclassified(Legacy.Text(text)))
        )
      )

  final private class ArticleLint()
      extends Leaf(
        Parent,
        "lint",
        "Formatting problems of one article's body and summary (--fix applies the wording-safe repairs)"
      ):
    private val slug = arg[ReviewGoal.Slug]("slug")
    private val fix = flag("fix", "Apply the repairs that never change wording, then report what is left")
    private val asJson = json()

    def command: Either[Problem, Cli.Command] =
      Right(
        ArticleCommand.Lint(slug(), ArticleCommand.Fix(fix.getOrElse(false)), asJson())
      )

  final private class ArticleCheck()
      extends Leaf(
        Parent,
        "check",
        "Dry-run edits to an article (or a whole draft body) and list the problems the review checks would find — writes nothing"
      ):
    private val slug = arg[ReviewGoal.Slug]("slug")
    // decline's `<string>`, until the help may change: the paths are read in `command`
    private val editsFile = value[String](
      "edits-file",
      "A JSON list of edits: [{\"op\": \"replace|insertAfter|insertBefore|delete\", \"anchor\": \"…\", \"text\": \"…\"}]"
    )
    private val bodyFile = value[String]("body-file", "Instead of edits: a whole draft body (Markdown) to check")
    private val kind = value[Article.Kind.Wire](
      "kind",
      "Check the section set of this kind instead of the article's (index = none, for a fragment)"
    )
    private val asJson = json()

    def command: Either[Problem, Cli.Command] =
      for
        edits <- pathOf("edits-file", editsFile.toOption)
        body <- pathOf("body-file", bodyFile.toOption)
        draft <- ArticleCommand.draft(edits, body)
      yield ArticleCommand.Check(slug(), draft, kind.toOption, asJson())

  final private class ArticleCoverage()
      extends Leaf(
        Parent,
        "coverage",
        "The article's filter against the corpus: sources cited, not used (with reasons), statuses, edges, drift, the critique checklist (disagreements checked), and the generated sections"
      ):
    private val slug = arg[ReviewGoal.Slug]("slug")
    private val asJson = json()

    def command: Either[Problem, Cli.Command] =
      Right(ArticleCommand.Coverage(slug(), asJson()))

  final private class ArticleReview()
      extends Leaf(
        Parent,
        "review",
        "The article's review state (unreviewed, queued, in review, passed, majors open, stopped), the goals linking it, and every review finding by run and round with what became of it"
      ):
    private val slug = arg[ReviewGoal.Slug]("slug")
    private val asJson = json()

    def command: Either[Problem, Cli.Command] =
      Right(ArticleCommand.Review(slug(), asJson()))

  final private class ArticleDelete() extends Leaf(Parent, "delete", "Remove one article (its links go with it)"):
    private val slug = arg[ReviewGoal.Slug]("slug")

    def command: Either[Problem, Cli.Command] = Right(ArticleCommand.Delete(slug()))

  final private class ArticleExport() extends Leaf(Parent, "export", "Write each article to <dir>/<kind>/<slug>.md"):
    private val dir = arg[Path]("dir")

    def command: Either[Problem, Cli.Command] = Right(ArticleCommand.Export(dir()))

  final private class ArticleImport()
      extends Leaf(Parent, "import", "Import <dir>/<kind>/*.md (and any *.md in <dir> itself)"):
    private val dir = arg[Path]("dir")
    private val force = flag("force", "Overwrite articles newer than the files")

    def command: Either[Problem, Cli.Command] =
      Right(ArticleCommand.Import(dir(), Api.Overwrite(force.getOrElse(false))))

  final private class ArticleVersions() extends Leaf(Parent, "versions", "Show the edit history of one article"):
    private val slug = arg[ReviewGoal.Slug]("slug")

    def command: Either[Problem, Cli.Command] = Right(ArticleCommand.Versions(slug()))

  final private class ArticleVersion() extends Leaf(Parent, "version", "Show one historical version's full body"):
    private val slug = arg[ReviewGoal.Slug]("slug")
    private val id = arg[Article.VersionId]("id")

    def command: Either[Problem, Cli.Command] = Right(ArticleCommand.Version(slug(), id()))

  final private class ArticleRename() extends Leaf(Parent, "rename", LabelRenameCmd.help(Bundle.Kind.Article)):
    private val from = arg[LabelRenames.Label]("old")
    private val to = arg[LabelRenames.Label]("new")
    private val asJson = json()

    def command: Either[Problem, Cli.Command] =
      LabelRenames
        .validate(Bundle.Kind.Article, from(), to())
        .left
        .map(_.message)
        .map(t =>
          ArticleCommand.Rename(
            ReviewGoal.Slug(from().value.trim),
            LabelRename.To(t.value),
            asJson()
          )
        )
