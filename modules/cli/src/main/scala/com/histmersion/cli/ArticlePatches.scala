package com.histmersion.cli

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
      base.copy(
        title = title.orElse(base.title),
        summary = summary.orElse(base.summary),
        kind = kind.orElse(base.kind),
        body = body.orElse(base.body),
        append = app.orElse(base.append),
        tags = tags,
        regions = regions.map(csv1).orElse(base.regions),
        period = per.orElse(base.period),
        ifUpdatedAt = ifUpdatedAt.orElse(base.ifUpdatedAt)
      )

  /** The `--filter-*` flags as given; [[withCoverage]] turns them into the patch's `filter`. */
  final case class FilterFlags(
      query: Option[String] = None,
      topics: Option[String] = None,
      tags: Option[String] = None,
      status: Option[String] = None,
      places: Option[String] = None,
      period: Option[String] = None,
      extraQueries: List[String] = Nil,
      clear: Boolean = false
  ):
    def anyGiven: Boolean = query.isDefined || topics.isDefined || tags.isDefined || status.isDefined ||
      places.isDefined || period.isDefined || extraQueries.nonEmpty

  /** The coverage flags (#958) over a built patch: any `--filter-*` flag REPLACES the filter with the one the flags
    * spell (the server validates it); `--clear-filter` sends the empty filter that clears it; each `--reason ck=text`
    * sets one reason, `ck=` removes it. Both override `--json`.
    */
  def withCoverage(p: Article.Patch, f: FilterFlags, reasons: List[String]): Either[Cli.Error, Article.Patch] =
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
      p.copy(filter = filter, reasons = if rs.isEmpty then p.reasons else p.reasons ++ rs)

  /** Each `--critique-reason src>dst=text` sets the reason of one critique-checklist pair, `src>dst=` removes it; added
    * to those of `--json`.
    */
  def withCritiques(p: Article.Patch, reasons: List[String]): Either[Cli.Error, Article.Patch] =
    reasons
      .traverse(r =>
        CritiqueReason.parse(r).left.map(m => Cli.Error(s"--critique-reason: $m", exitCode = ExitCode.Usage))
      )
      .map(rs => if rs.isEmpty then p else p.copy(critiqueReasons = p.critiqueReasons ++ rs))

  private def parse(json: Option[String]): Either[Cli.Error, Article.Patch] = json match
    case None    => Right(Article.Patch())
    case Some(s) =>
      io.circe.parser
        .decode[Article.Patch](s)
        .left
        .map(e => Cli.Error(s"--json: ${e.getMessage}", exitCode = ExitCode.Usage))

  private def read(file: Option[String]): Either[Cli.Error, Option[String]] = file match
    case None    => Right(None)
    case Some(f) =>
      val p = java.nio.file.Path.of(f)
      if java.nio.file.Files.isRegularFile(p) then Right(Some(java.nio.file.Files.readString(p)))
      else Left(Cli.Error(s"no such file: $f", exitCode = ExitCode.Usage))

  private def periodOf(s: Option[String]): Either[Cli.Error, Option[Period.Input]] = s match
    case None    => Right(None)
    case Some(v) =>
      v.split(",").map(_.trim).toList match
        case a :: b :: Nil =>
          (a.toLongOption, b.toLongOption) match
            case (Some(x), Some(y)) => Right(Some(Period.Input(x, y)))
            case _                  => Left(Cli.Error(s"--period: '$v' is not start,end", exitCode = ExitCode.Usage))
        case _ => Left(Cli.Error(s"--period: '$v' is not start,end", exitCode = ExitCode.Usage))

  private def csv(s: Option[String]): List[String] = s.map(csv1).getOrElse(Nil)
  private def csv1(s: String): List[String] = s.split(",").toList.map(_.trim).filter(_.nonEmpty)
