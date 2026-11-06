package com.histmersion.cli

import hearth.kindlings.optics.syntax.*

import scala.annotation.tailrec
import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.api.ListPatch
import com.histmersion.config.*

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import com.histmersion.api.research.CritiqueReason
import com.histmersion.api.catalogue.Period
import com.histmersion.api.research.Article
import com.histmersion.api.research.Coverage

/** Build an [[ArticlePatch]] from CLI flags — pure, so the flag semantics are unit-tested without a server. Mirrors
  * [[Patches]] on the source side: `--json` is the baseline, each flag overrides the part it names, and an absent part
  * stays absent (= leave alone).
  */
object ArticlePatches:

  type BuildJson = _root_.com.histmersion.cli.ArticlePatches.ParseJson
  val BuildJson: _root_.com.histmersion.cli.ArticlePatches.ParseJson.type = _root_.com.histmersion.cli.ArticlePatches.ParseJson
  type BuildTitle = BuildTitle.T
  object BuildTitle extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BuildSummary = BuildSummary.T
  object BuildSummary extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BuildKind = BuildKind.T
  object BuildKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BuildBodyFile = BuildBodyFile.T
  object BuildBodyFile extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BuildAppendFile = BuildAppendFile.T
  object BuildAppendFile extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BuildTagsAdd = BuildTagsAdd.T
  object BuildTagsAdd extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BuildTagsRemove = BuildTagsRemove.T
  object BuildTagsRemove extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BuildTagsReplace = BuildTagsReplace.T
  object BuildTagsReplace extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BuildRegions = BuildRegions.T
  object BuildRegions extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BuildPeriod = BuildPeriod.T
  object BuildPeriod extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BuildIfUpdatedAt = BuildIfUpdatedAt.T
  object BuildIfUpdatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WithCoverageReasons = WithCoverageReasons.T
  object WithCoverageReasons extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WithCritiquesReasons = WithCritiquesReasons.T
  object WithCritiquesReasons extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseJson = ParseJson.T
  object ParseJson extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadResultPart2 = ReadResultPart2.T
  object ReadResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadFile = ReadFile.T
  object ReadFile extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodOfS = PeriodOfS.T
  object PeriodOfS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CsvResult = CsvResult.T
  object CsvResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CsvS = CsvS.T
  object CsvS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Csv1Result = Csv1Result.T
  object Csv1Result extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Csv1S = Csv1S.T
  object Csv1S extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def build(
      json: Option[com.histmersion.cli.ArticlePatches.BuildJson],
      title: Option[com.histmersion.cli.ArticlePatches.BuildTitle],
      summary: Option[com.histmersion.cli.ArticlePatches.BuildSummary],
      kind: Option[com.histmersion.cli.ArticlePatches.BuildKind],
      bodyFile: Option[com.histmersion.cli.ArticlePatches.BuildBodyFile],
      appendFile: Option[com.histmersion.cli.ArticlePatches.BuildAppendFile],
      tagsAdd: Option[com.histmersion.cli.ArticlePatches.BuildTagsAdd],
      tagsRemove: Option[com.histmersion.cli.ArticlePatches.BuildTagsRemove],
      tagsReplace: Option[com.histmersion.cli.ArticlePatches.BuildTagsReplace],
      regions: Option[com.histmersion.cli.ArticlePatches.BuildRegions],
      period: Option[com.histmersion.cli.ArticlePatches.BuildPeriod],
      ifUpdatedAt: Option[com.histmersion.cli.ArticlePatches.BuildIfUpdatedAt]
  ): Either[Cli.Error, Article.Patch] =
    for
      base <- parse(json)
      body <- read(bodyFile)
      app <- read(appendFile)
      per <- periodOf(period)
    yield
      val tags =
        if tagsAdd.isEmpty && tagsRemove.isEmpty && tagsReplace.isEmpty then base.tags
        else Some(ListPatch(csv(tagsAdd), csv(tagsRemove), tagsReplace.map(csv1)))
      base.modify(
        _.title).setTo(title.orElse(base.title)).modify(_.summary).setTo(summary.orElse(base.summary)).modify(_.kind).setTo(kind.orElse(base.kind)).modify(_.body).setTo(body.orElse(base.body)).modify(_.append).setTo(app.orElse(base.append)).modify(_.tags).setTo(tags).modify(_.regions).setTo(regions.map(csv1).orElse(base.regions)).modify(_.period).setTo(per.orElse(base.period)).modify(_.ifUpdatedAt).setTo(ifUpdatedAt.orElse(base.ifUpdatedAt)
      )

  /** The `--filter-*` flags as given; [[withCoverage]] turns them into the patch's `filter`. */
  final case class FilterFlags(
      query: Option[FilterFlags.Query] = None,
      topics: Option[FilterFlags.Topics] = None,
      tags: Option[FilterFlags.Tags] = None,
      status: Option[FilterFlags.Status] = None,
      places: Option[FilterFlags.Places] = None,
      period: Option[FilterFlags.Period] = None,
      extraQueries: List[FilterFlags.ExtraQueries] = Nil,
      clear: FilterFlags.Clear = false
  ):
    def anyGiven: com.histmersion.cli.ArticlePatches.FilterFlags.AnyGivenResult = query.isDefined || topics.isDefined || tags.isDefined || status.isDefined ||
      places.isDefined || period.isDefined || extraQueries.nonEmpty

  object FilterFlags:
    type AnyGivenResult = AnyGivenResult.T
    object AnyGivenResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Query = Query.T
    object Query extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Topics = Topics.T
    object Topics extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Tags = Tags.T
    object Tags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Status = Status.T
    object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Places = Places.T
    object Places extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Period = Period.T
    object Period extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ExtraQueries = ExtraQueries.T
    object ExtraQueries extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Clear = Clear.T
    object Clear extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The coverage flags (#958) over a built patch: any `--filter-*` flag REPLACES the filter with the one the flags
    * spell (the server validates it); `--clear-filter` sends the empty filter that clears it; each `--reason ck=text`
    * sets one reason, `ck=` removes it. Both override `--json`.
    */
  def withCoverage(p: Article.Patch, f: FilterFlags, reasons: List[com.histmersion.cli.ArticlePatches.WithCoverageReasons]): Either[Cli.Error, Article.Patch] =
    for
      period <- periodOf(f.period)
      _ <- Either.cond(
        !(f.clear && f.anyGiven),
        (),
        Cli.Error("--clear-filter with --filter-* flags: give one or the other", exitCode = ExitCode.Usage)
      )
      rs <- reasons.traverse { r =>
        r.indexOf('=') match
          case i if i > 0 => Right(Coverage.Reason(r.take(i).trim, r.drop(i + 1).trim))
          case _          => Left(Cli.Error(s"--reason: '$r' is not <citekey>=<reason>", exitCode = ExitCode.Usage))
      }
    yield
      val filter =
        if f.clear then Some(Article.Filter())
        else if f.anyGiven then
          Some(
            Article.Filter(
              f.query.map(_.trim).filter(_.nonEmpty),
              csv(f.topics),
              csv(f.tags),
              csv(f.status),
              csv(f.places),
              Nil,
              period.map(_.start),
              period.map(_.end),
              f.extraQueries.map(_.trim).filter(_.nonEmpty)
            )
          )
        else p.filter
      p.modify(_.filter).setTo(filter).modify(_.reasons).setTo(if rs.isEmpty then p.reasons else p.reasons ++ rs)

  /** Each `--critique-reason src>dst=text` sets the reason of one critique-checklist pair, `src>dst=` removes it; added
    * to those of `--json`.
    */
  def withCritiques(p: Article.Patch, reasons: List[com.histmersion.cli.ArticlePatches.WithCritiquesReasons]): Either[Cli.Error, Article.Patch] =
    reasons
      .traverse(r =>
        CritiqueReason.parse(r).left.map(m => Cli.Error(s"--critique-reason: $m", exitCode = ExitCode.Usage))
      )
      .map(rs => if rs.isEmpty then p else p.modify(_.critiqueReasons).setTo(p.critiqueReasons ++ rs))

  private def parse(json: Option[com.histmersion.cli.ArticlePatches.ParseJson]): Either[Cli.Error, Article.Patch] = json match
    case None    => Right(Article.Patch())
    case Some(s) =>
      io.circe.parser
        .decode[Article.Patch](s)
        .left
        .map(e => Cli.Error(s"--json: ${e.getMessage}", exitCode = ExitCode.Usage))

  private def read(file: Option[com.histmersion.cli.ArticlePatches.ReadFile]): Either[Cli.Error, Option[com.histmersion.cli.ArticlePatches.ReadResultPart2]] = file match
    case None    => Right(None)
    case Some(f) =>
      val p = java.nio.file.Path.of(f)
      if java.nio.file.Files.isRegularFile(p) then Right(Some(java.nio.file.Files.readString(p)))
      else Left(Cli.Error(s"no such file: $f", exitCode = ExitCode.Usage))

  private def periodOf(s: Option[com.histmersion.cli.ArticlePatches.PeriodOfS]): Either[Cli.Error, Option[Period.Input]] = s match
    case None    => Right(None)
    case Some(v) =>
      v.split(",").map(_.trim).toList match
        case a :: b :: Nil =>
          (a.toLongOption, b.toLongOption) match
            case (Some(x), Some(y)) => Right(Some(Period.Input(x, y)))
            case _                  => Left(Cli.Error(s"--period: '$v' is not start,end", exitCode = ExitCode.Usage))
        case _ => Left(Cli.Error(s"--period: '$v' is not start,end", exitCode = ExitCode.Usage))

  private def csv(s: Option[com.histmersion.cli.ArticlePatches.CsvS]): List[com.histmersion.cli.ArticlePatches.CsvResult] = s.map(csv1).getOrElse(Nil)
  private def csv1(s: com.histmersion.cli.ArticlePatches.Csv1S): List[com.histmersion.cli.ArticlePatches.Csv1Result] = s.split(",").toList.map(_.trim).filter(_.nonEmpty)
