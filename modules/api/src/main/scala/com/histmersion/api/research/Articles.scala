package com.histmersion
package api.research
import com.histmersion.util.problem.{NonEmptyVector, Problem}

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.Write
import com.histmersion.api.catalogue.Period
import com.histmersion.api.given
import com.histmersion.api.Api
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.catalogue.Year
object Articles:

  def heading(body: Article.Body): Option[Article.Heading] =
    ((body).value.linesIterator
      .map(_.trim)
      .find(_.nonEmpty)
      .filter(_.startsWith("# "))
      .map(_.drop(2).trim)
      .filter(_.nonEmpty))
      .map(refactorValue3 => Article.Heading(refactorValue3))

  def lede(body: Article.Body): Option[Article.SummaryText] =
    val lines = (body).value.linesIterator.toList.dropWhile(_.trim.isEmpty)
    val rest0 = lines.headOption.filter(_.trim.startsWith("# ")).fold(lines)(_ => lines.tail)
    val rest = rest0.dropWhile(_.trim.isEmpty) match
      case h :: tail if LedeHeadings.contains(h.trim.toLowerCase) => tail
      case other                                                  => other
    val para = rest.dropWhile(_.trim.isEmpty).takeWhile(_.trim.nonEmpty)
    val text = para.map(_.trim).mkString(" ").trim
    val plain = List("**", "__", "*", "_")
      .foldLeft(text) { (s, mark) =>
        if s.length > 2 * mark.length && s.startsWith(mark) && s.endsWith(mark) then
          s.drop(mark.length).dropRight(mark.length)
        else s
      }
      .trim
    (Option(plain)
      .filter(s => s.nonEmpty && !s.startsWith("#") && !s.startsWith("!")))
      .map(refactorValue4 => Article.SummaryText(refactorValue4))

  private val LedeHeadings = Set("## summary", "## lede", "## abstract")

  private val StandIns =
    Set("placeholder", "todo", "tbd", "tba", "fixme", "xxx", "summary", "tk", "na", "n a", "none", "wip")

  def summaryOf(raw: Option[Article.SummaryText]): Either[Problem, Option[Article.SummaryText]] =
    raw.map((_).value.trim).filter(_.nonEmpty) match
      case None    => Right(None)
      case Some(s) =>
        val norm = s.toLowerCase.replaceAll("[^a-z]+", " ").trim
        if StandIns.contains(norm) || norm.startsWith("lorem ipsum") then
          Left(Problem.Content.Proposal(Problem.Content.Requirement.StandIn, Problem.Where("summary")))
        else Right(Some(Article.SummaryText(s)))

  val Kinds = List("concept", "myth", "culture", "region", "timeline", "report", "index")

  private val Kind = """^[a-z][a-z0-9_-]*$""".r

  def notFound(slug: Article.Slug): Api.Error =
    Api.Error(
      Problem.NotFound.Entity(Problem.TypeName.of(Article.Slug), Problem.Raw(s"$slug")),
      Some(Api.Error.Code(Write.CodeNotFound))
    )

  def kindOf(raw: Article.Kind.Wire): Either[Problem, Article.Kind.Wire] =
    val k = (raw).value.trim.toLowerCase
    if Kind.matches(k) then Right(Article.Kind.Wire(k))
    else
      Left(
        Problem.Invalid.Unreadable(
          Problem.TypeName.of(Article.Kind.Wire),
          Problem.Raw(s"$raw"),
          Kinds.toVector.map(k => Problem.Raw(s"$k")),
          Vector(Problem.Field("kind"))
        )
      )

  def kindOfDir(dirName: String): Article.Kind.Wire =
    val d = (dirName).trim.toLowerCase
    if d.length > 1 && d.endsWith("s") && !Kinds.contains(d) then Article.Kind.Wire(d.dropRight(1))
    else Article.Kind.Wire(d)

  def dirOfKind(kind: Article.Kind.Wire): String = ((kind).value.trim.toLowerCase)

  private val SlugPattern = """^[a-z0-9][a-z0-9_.-]{0,120}$""".r

  def slugOf(raw: Article.Slug): Either[Problem, Article.Slug] =
    val s = (raw).value.trim.toLowerCase
    if SlugPattern.matches(s) then Right(Article.Slug(s))
    else
      Left(
        Problem.Invalid.Unreadable(
          Problem.TypeName.of(Article.Slug),
          Problem.Raw(s"$raw"),
          Vector(Problem.Raw("sea_peoples")),
          Vector(Problem.Field("slug"))
        )
      )

  /** The article a put makes, or why it is refused. */
  def applyPut(slug: Article.Slug, p: Article.Put): Either[Problem, Article] =
    for
      s <- slugOf(slug)
      k <- kindOf(p.kind)
      _ <-
        if p.title.value.trim.nonEmpty then Right(())
        else Left(Problem.Invalid.Missing.here(Problem.Field("title")))
      period <- periodOf(p.periodStart, p.periodEnd)
      summary <- summaryOf(p.summary)
      filter <- p.filter.fold[Either[Problem, Option[Article.Filter]]](Right(None))(f => Article.Filter.normalise(f))
      reasons <- Coverage.Reason.merge(Map.empty, p.reasons)
    yield Article(
      slug = s,
      title = Article.Title(p.title.value.trim),
      kind = k,
      body = Article.Coverage.stripGenerated(p.body),
      summary = summary.orElse(lede(p.body)),
      tags = p.tags.map(t => Article.Tags(t.value.trim)).filter(_.value.nonEmpty).distinct,
      regions = p.regions.map(r => Article.Regions(r.value.trim)).filter(_.value.nonEmpty).distinct,
      period = period,
      filter = filter,
      reasons = reasons
    )

  def precondition(existing: Article, patch: Article.Patch): Either[Problem, Unit] =
    patch.ifUpdatedAt match
      case None       => Right(())
      case Some(want) =>
        val actual = existing.updatedAt.getOrElse(Article.UpdatedAt(""))
        if want == actual then Right(())
        else
          Left(
            Problem.Conflict.Stale(
              Problem.TypeName.of(Article.Slug),
              Problem.Raw(s"${existing.slug}"),
              Problem.Conflict.Stamp(s"$want"),
              Problem.Conflict.Stamp(s"$actual")
            )
          )

  def applyPatch(existing: Article, patch: Article.Patch): Either[Problem, Article] =
    for
      _ <- precondition(existing, patch)
      kind <- patch.kind.fold[Either[Problem, Article.KindValue]](Right(existing.kind))(kindOf)
      period <- patch.period.fold[Either[Problem, Option[Period]]](Right(existing.period))(p =>
        periodOf(Some(p.start), Some(p.end))
      )
      body <- bodyOf(existing.body, patch)
      summary <- summaryOf(patch.summary)
      filter <- patch.filter.fold[Either[Problem, Option[Article.Filter]]](Right(existing.filter))(f =>
        Article.Filter.normalise(f)
      )
      reasons <- Coverage.Reason
        .merge(existing.reasons, patch.reasons)
    yield existing
      .modify(_.title)
      .setTo(patch.title.map(t => Article.Title(t.value.trim)).filter(_.value.nonEmpty).getOrElse(existing.title))
      .modify(_.kind)
      .setTo(kind)
      .modify(_.body)
      .setTo(body)
      .modify(_.summary)
      .setTo(summary.orElse(existing.summary))
      .modify(_.tags)
      .setTo(
        patch.tags.fold(existing.tags)(lp =>
          Write.applyList(existing.tags.map(t => Write.ListItem(t.value)), lp).map(i => Article.Tags(i.value))
        )
      )
      .modify(_.regions)
      .setTo(
        patch.regions
          .getOrElse(existing.regions)
          .map(r => Article.Regions(r.value.trim))
          .filter(_.value.nonEmpty)
          .distinct
      )
      .modify(_.period)
      .setTo(period)
      .modify(_.filter)
      .setTo(filter)
      .modify(_.reasons)
      .setTo(reasons)

  private def bodyOf(current: Article.Body, patch: Article.Patch): Either[Problem, Article.Body] =
    val base = patch.body.map(Article.Coverage.stripGenerated).getOrElse(current)
    patch.append.map(a => Article.Coverage.stripGenerated(Article.Body(a.value)).value.trim).filter(_.nonEmpty) match
      case None    => Right(base)
      case Some(a) =>
        patch.section.map(_.value.trim).filter(_.nonEmpty) match
          case None => Right(Article.Body(if base.value.trim.isEmpty then a else s"${base.value.stripTrailing}\n\n$a"))
          case Some(s) => appendUnderSection(base, Article.Section(s), Article.Prose(a))

  def appendUnderSection(
      body: Article.Body,
      section: Article.Section,
      text: Article.Prose
  ): Either[Problem, Article.Body] =
    val sNorm = (section).value.replaceAll("^#+\\s*", "").trim.toLowerCase
    val lines = (body).value.split("\n", -1).toList
    val idx = lines.indexWhere { line =>
      val t = line.trim
      t.startsWith("#") && t.replaceAll("^#+\\s*", "").trim.toLowerCase == sNorm
    }
    if idx < 0 then Left(Problem.NotFound.Entity(Problem.TypeName.of(Article.Section), Problem.Raw(s"$section")))
    else
      val level = lines(idx).trim.takeWhile(_ == '#').length
      val rest = lines.drop(idx + 1)
      val nextIdx = rest.indexWhere { line =>
        val t = line.trim
        t.startsWith("#") && t.takeWhile(_ == '#').length <= level
      }
      val insertAt = if nextIdx < 0 then lines.length else idx + 1 + nextIdx
      val before = lines.take(insertAt)
      val after = lines.drop(insertAt)
      val result = (before.mkString("\n").stripTrailing + "\n\n" + (text).value.trim +
        (if after.isEmpty then "" else "\n\n" + after.mkString("\n")))
      Right(Article.Body(result))

  private def periodOf(start: Option[Year], end: Option[Year]): Either[Problem, Option[Period]] =
    (start, end) match
      case (Some(a), Some(b)) if a <= b => Right(Some(Period(a, b)))
      case (Some(a), Some(b))           =>
        Left(
          Problem.Invalid.Inconsistent.here(
            NonEmptyVector.of(Problem.Field("periodStart"), Problem.Field("periodEnd")),
            Problem.Invalid.Exclusion.Ordered
          )
        )
      case (None, None) => Right(None)
      case _            =>
        Left(
          Problem.Invalid.Inconsistent.here(
            NonEmptyVector.of(Problem.Field("periodStart"), Problem.Field("periodEnd")),
            Problem.Invalid.Exclusion.Together
          )
        )
