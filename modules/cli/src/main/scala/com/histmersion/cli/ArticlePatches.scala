package com.histmersion
package cli
import com.histmersion.cli.AllOrFirst.traverse
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.RawJson
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentence

import hearth.kindlings.optics.syntax.*

import scala.annotation.tailrec
import com.histmersion.api.ListPatch
import com.histmersion.config.*

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import com.histmersion.api.research.CritiqueReason
import com.histmersion.api.catalogue.Period
import com.histmersion.api.research.Article
import com.histmersion.api.research.Coverage
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.Api
import com.histmersion.api.library.Citekey
import com.histmersion.api.catalogue.Year

/** Build an [[ArticlePatch]] from CLI flags — pure, so the flag semantics are unit-tested without a server. Mirrors
  * [[Patches]] on the source side: `--json` is the baseline, each flag overrides the part it names, and an absent part
  * stays absent (= leave alone).
  */
object ArticlePatches:
  type CsvText = CsvText.T
  object CsvText extends Newtype[String, Pure]
  type PatchJson = PatchJson.T
  object PatchJson extends Newtype[String, Pure]
  type ReasonText = ReasonText.T
  object ReasonText extends Newtype[String, Pure]
  type TextFile = TextFile.T
  object TextFile extends Newtype[String, Pure]

  def build(
      json: Option[PatchJson],
      title: Option[Article.Title],
      summary: Option[Article.SummaryText],
      kind: Option[Article.Kind.Wire],
      bodyFile: Option[TextFile],
      appendFile: Option[TextFile],
      tagsAdd: Option[CsvText],
      tagsRemove: Option[CsvText],
      tagsReplace: Option[CsvText],
      regions: Option[CsvText],
      period: Option[FilterFlags.Period],
      ifUpdatedAt: Option[Article.UpdatedAt]
  ): Either[Cli.Error, Article.Patch] =
    for
      base <- parse(json)
      body <- read(bodyFile)(Article.Body(_))
      app <- read(appendFile)(Article.Patch.Append(_))
      per <- periodOf(period)
    yield
      val tags =
        if tagsAdd.isEmpty && tagsRemove.isEmpty && tagsReplace.isEmpty then base.tags
        else
          Some(
            ListPatch(
              csv(tagsAdd).map(ListPatch.Add(_)),
              csv(tagsRemove).map(ListPatch.Remove(_)),
              tagsReplace.map(t => csv1(t).map(ListPatch.Replace(_)))
            )
          )
      base
        .modify(_.title)
        .setTo(title.orElse(base.title))
        .modify(_.summary)
        .setTo(summary.orElse(base.summary))
        .modify(_.kind)
        .setTo(kind.orElse(base.kind))
        .modify(_.body)
        .setTo(body.orElse(base.body))
        .modify(_.append)
        .setTo(app.orElse(base.append))
        .modify(_.tags)
        .setTo(tags)
        .modify(_.regions)
        .setTo(regions.map(r => csv1(r).map(Article.Patch.Regions(_))).orElse(base.regions))
        .modify(_.period)
        .setTo(per.orElse(base.period))
        .modify(_.ifUpdatedAt)
        .setTo(ifUpdatedAt.orElse(base.ifUpdatedAt))

  /** The `--filter-*` flags as given; [[withCoverage]] turns them into the patch's `filter`. */
  final case class FilterFlags(
      query: Option[FilterFlags.Query] = None,
      topics: Option[CsvText] = None,
      tags: Option[CsvText] = None,
      status: Option[CsvText] = None,
      places: Option[CsvText] = None,
      period: Option[FilterFlags.Period] = None,
      extraQueries: List[FilterFlags.ExtraQueries] = Nil,
      clear: FilterFlags.Clear = FilterFlags.Clear(false)
  ):
    def anyGiven: Boolean = query.isDefined || topics.isDefined || tags.isDefined || status.isDefined ||
      places.isDefined || period.isDefined || extraQueries.nonEmpty

  object FilterFlags:

    type Query = Api.ArticleQuery.Text
    val Query: Api.ArticleQuery.Text.type = Api.ArticleQuery.Text
    type Period = Period.T
    object Period extends Newtype[String, Pure]
    type ExtraQueries = ExtraQueries.T
    object ExtraQueries extends Newtype[String, Pure]
    type Clear = Clear.T
    object Clear extends Newtype[Boolean, Pure]

  /** The coverage flags (#958) over a built patch: any `--filter-*` flag REPLACES the filter with the one the flags
    * spell (the server validates it); `--clear-filter` sends the empty filter that clears it; each `--reason ck=text`
    * sets one reason, `ck=` removes it. Both override `--json`.
    */
  def withCoverage(p: Article.Patch, f: FilterFlags, reasons: List[ReasonText]): Either[Cli.Error, Article.Patch] =
    for
      period <- periodOf(f.period)
      _ <- Either.cond(
        !(f.clear.value && f.anyGiven),
        (),
        Cli.Error(
          Problem.Unclassified(Legacy.Text("--clear-filter with --filter-* flags: give one or the other")),
          exitCode = ExitCode.Usage
        )
      )
      rs <- reasons.traverse { r =>
        r.value.indexOf('=') match
          case i if i > 0 =>
            Right(
              Coverage.Reason(
                Citekey(r.value.take(i).trim),
                Coverage.Rationale(s"${r.value.drop(i + 1).trim}")
              )
            )
          case _ =>
            Left(
              Cli.Error(
                Problem.Unclassified(Legacy.Text(s"--reason: '$r' is not <citekey>=<reason>")),
                exitCode = ExitCode.Usage
              )
            )
      }
    yield
      val filter =
        if f.clear.value then Some(Article.Filter())
        else if f.anyGiven then
          Some(
            Article.Filter(
              f.query.map(q => Article.Filter.Query(q.value.trim)).filter(_.value.nonEmpty),
              csv(f.topics).map(Article.Filter.Topics(_)),
              csv(f.tags).map(Article.Filter.Tags(_)),
              csv(f.status).map(Article.Filter.Status(_)),
              csv(f.places).map(Article.Filter.Places(_)),
              Nil,
              period.map(_.start),
              period.map(_.end),
              f.extraQueries.map(_.value.trim).filter(_.nonEmpty).map(Article.Filter.ExtraQueries(_))
            )
          )
        else p.filter
      p.modify(_.filter).setTo(filter).modify(_.reasons).setTo(if rs.isEmpty then p.reasons else p.reasons ++ rs)

  /** Each `--critique-reason src>dst=text` sets the reason of one critique-checklist pair, `src>dst=` removes it; added
    * to those of `--json`.
    */
  def withCritiques(p: Article.Patch, reasons: List[CritiqueReason.Spec]): Either[Cli.Error, Article.Patch] =
    reasons
      .traverse(r =>
        CritiqueReason
          .parse(r)
          .left
          .map(m =>
            Cli.Error(Problem.Unclassified(Legacy.Text(s"--critique-reason: ${m.sentence}")), exitCode = ExitCode.Usage)
          )
      )
      .map(rs => if rs.isEmpty then p else p.modify(_.critiqueReasons).setTo(p.critiqueReasons ++ rs))

  private def parse(json: Option[PatchJson]): Either[Cli.Error, Article.Patch] = json match
    case None    => Right(Article.Patch())
    case Some(s) =>
      RawJson
        .decode[Article.Patch](s.value)
        .left
        .map(e => Cli.Error(Problem.Unclassified(Legacy.Text(s"--json: $e")), exitCode = ExitCode.Usage))

  private def read[A](file: Option[TextFile])(wrap: String => A): Either[Cli.Error, Option[A]] = file match
    case None    => Right(None)
    case Some(f) =>
      val p = java.nio.file.Path.of(f.value)
      if java.nio.file.Files.isRegularFile(p) then Right(Some(wrap(java.nio.file.Files.readString(p))))
      else Left(Cli.Error(Problem.Unclassified(Legacy.Text(s"no such file: $f")), exitCode = ExitCode.Usage))

  private def periodOf(s: Option[FilterFlags.Period]): Either[Cli.Error, Option[Period.Input]] = s match
    case None    => Right(None)
    case Some(v) =>
      v.value.split(",").map(_.trim).toList match
        case a :: b :: Nil =>
          (a.toLongOption, b.toLongOption) match
            case (Some(x), Some(y)) =>
              Right(Some(Period.Input(Year(x), Year(y))))
            case _ =>
              Left(
                Cli.Error(
                  Problem.Unclassified(Legacy.Text(s"--period: '$v' is not start,end")),
                  exitCode = ExitCode.Usage
                )
              )
        case _ =>
          Left(
            Cli.Error(Problem.Unclassified(Legacy.Text(s"--period: '$v' is not start,end")), exitCode = ExitCode.Usage)
          )

  private def csv(s: Option[CsvText]): List[String] = s.map(csv1).getOrElse(Nil)
  private def csv1(s: CsvText): List[String] = s.value.split(",").toList.map(_.trim).filter(_.nonEmpty)
