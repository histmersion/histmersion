package com.histmersion
package cli

import com.histmersion.api.Api
import com.histmersion.api.aliases.LabelRename
import com.histmersion.api.library.Filter
import com.histmersion.api.research.{Article, ReviewGoal}
import com.histmersion.util.problem.{NonEmptyVector, Problem}
import io.github.iltotore.iron.constraint.any.Pure

import java.nio.file.Path

/** What `histmersion article …` was asked to do, every argument already a typed value: the command line's parser
  * (decline in [[ArticleCli]], scallop in [[CommandLine]]) builds one, and [[ArticleCli.run]] carries it out against a
  * server.
  */
enum ArticleCommand derives CanEqual:
  case Listing(
      text: Option[Api.ArticleQuery.Text],
      kind: Option[Api.ArticleQuery.Kind],
      filter: Filter,
      json: Cli.JsonOutput
  )
  case Get(slug: ReviewGoal.Slug, parts: Vector[Article.Part], json: Cli.JsonOutput)
  case Put(file: Path, kind: Option[Article.Kind.Wire], strict: Cli.Strict)
  case Patch(
      slug: ReviewGoal.Slug,
      edit: ArticleCommand.Edit,
      strict: Cli.Strict,
      filter: ArticlePatches.FilterFlags,
      reasons: List[ArticlePatches.ReasonText],
      critiques: List[Problem]
  )
  case Lint(slug: ReviewGoal.Slug, fix: ArticleCommand.Fix, json: Cli.JsonOutput)
  case Check(slug: ReviewGoal.Slug, draft: ArticleCommand.Draft, kind: Option[Article.Kind.Wire], json: Cli.JsonOutput)
  case Coverage(slug: ReviewGoal.Slug, json: Cli.JsonOutput)
  case Review(slug: ReviewGoal.Slug, json: Cli.JsonOutput)
  case Delete(slug: ReviewGoal.Slug)
  case Export(dir: Path)
  case Import(dir: Path, force: Api.Overwrite)
  case Versions(slug: ReviewGoal.Slug)
  case Version(slug: ReviewGoal.Slug, id: Article.VersionId)
  case Rename(from: ReviewGoal.Slug, to: LabelRename.To, json: Cli.JsonOutput)

object ArticleCommand:
  type Fix = Fix.T
  object Fix extends Newtype[Boolean, Pure]

  /** The parts of the article `patch` changes, as given (absent = unchanged). */
  final case class Edit(
      json: Option[ArticlePatches.PatchJson],
      title: Option[Article.Title],
      summary: Option[Article.SummaryText],
      kind: Option[Article.Kind.Wire],
      body: Option[ArticlePatches.TextFile],
      append: Option[ArticlePatches.TextFile],
      tagsAdd: Option[ArticlePatches.CsvText],
      tagsRemove: Option[ArticlePatches.CsvText],
      tagsReplace: Option[ArticlePatches.CsvText],
      regions: Option[ArticlePatches.CsvText],
      period: Option[ArticlePatches.FilterFlags.Period],
      ifUpdatedAt: Option[Article.UpdatedAt]
  ) derives CanEqual

  /** What `check` dry-runs: a list of edits, or a whole draft body. */
  enum Draft derives CanEqual:
    case Edits(file: Path)
    case Body(file: Path)

  /** `--edits-file` and `--body-file` as given: exactly one of them names the draft. */
  def draft(edits: Option[Path], body: Option[Path]): Either[Problem, Draft] =
    (edits, body) match
      case (Some(e), None) => Right(Draft.Edits(e))
      case (None, Some(b)) => Right(Draft.Body(b))
      case _               =>
        Left(
          Problem.Invalid.Inconsistent(
            NonEmptyVector.of(Problem.Field("--edits-file"), Problem.Field("--body-file")),
            Problem.Invalid.Exclusion.OneOf
          )
        )

  /** `--parts body,summary`: the parts asked for, none = all. */
  def parts(csv: Option[String]): Vector[Article.Part] =
    csv.toVector.flatMap(_.split(',').map(_.trim.toLowerCase)).map(Article.Part(_))
