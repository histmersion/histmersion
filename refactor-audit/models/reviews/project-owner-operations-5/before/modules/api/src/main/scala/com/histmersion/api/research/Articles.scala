package com.histmersion.api.research

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.Write
import com.histmersion.api.catalogue.Period
import com.histmersion.api.given
import com.histmersion.api.Api
object Articles:

  type HeadingResult = HeadingResult.T
  object HeadingResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeadingBody = HeadingBody.T
  object HeadingBody extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LedeResult = LedeResult.T
  object LedeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LedeBody = LedeBody.T
  object LedeBody extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SummaryOfResultPart1 = SummaryOfResultPart1.T
  object SummaryOfResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SummaryOfResultPart2 = SummaryOfResultPart2.T
  object SummaryOfResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SummaryOfRaw = SummaryOfRaw.T
  object SummaryOfRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NotFoundSlug = NotFoundSlug.T
  object NotFoundSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KindOfResultPart1 = KindOfResultPart1.T
  object KindOfResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KindOfResultPart2 = KindOfResultPart2.T
  object KindOfResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KindOfRaw = KindOfRaw.T
  object KindOfRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KindOfDirResult = KindOfDirResult.T
  object KindOfDirResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KindOfDirDirName = KindOfDirDirName.T
  object KindOfDirDirName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DirOfKindResult = DirOfKindResult.T
  object DirOfKindResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DirOfKindKind = DirOfKindKind.T
  object DirOfKindKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyPutResultPart1 = ApplyPutResultPart1.T
  object ApplyPutResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyPutSlug = ApplyPutSlug.T
  object ApplyPutSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PreconditionResultPart1 = PreconditionResultPart1.T
  object PreconditionResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyPatchResultPart1 = ApplyPatchResultPart1.T
  object ApplyPatchResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BodyOfResultPart1 = BodyOfResultPart1.T
  object BodyOfResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BodyOfResultPart2 = BodyOfResultPart2.T
  object BodyOfResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BodyOfCurrent = BodyOfCurrent.T
  object BodyOfCurrent extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AppendUnderSectionResultPart1 = AppendUnderSectionResultPart1.T
  object AppendUnderSectionResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AppendUnderSectionResultPart2 = AppendUnderSectionResultPart2.T
  object AppendUnderSectionResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AppendUnderSectionBody = AppendUnderSectionBody.T
  object AppendUnderSectionBody extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AppendUnderSectionSection = AppendUnderSectionSection.T
  object AppendUnderSectionSection extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AppendUnderSectionText = AppendUnderSectionText.T
  object AppendUnderSectionText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodOfResultPart1 = PeriodOfResultPart1.T
  object PeriodOfResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodOfStart = PeriodOfStart.T
  object PeriodOfStart extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodOfEnd = PeriodOfEnd.T
  object PeriodOfEnd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def heading(body: com.histmersion.api.research.Articles.HeadingBody): Option[com.histmersion.api.research.Articles.HeadingResult] =
    ((body).value.linesIterator
      .map(_.trim)
      .find(_.nonEmpty)
      .filter(_.startsWith("# "))
      .map(_.drop(2).trim)
      .filter(_.nonEmpty)).map(refactorValue3 => _root_.com.histmersion.api.research.Articles.HeadingResult(refactorValue3))

  def lede(body: com.histmersion.api.research.Articles.LedeBody): Option[com.histmersion.api.research.Articles.LedeResult] =
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
    (Option(plain).filter(s => s.nonEmpty && !s.startsWith("#") && !s.startsWith("!"))).map(refactorValue4 => _root_.com.histmersion.api.research.Articles.LedeResult(refactorValue4))

  private val LedeHeadings = Set("## summary", "## lede", "## abstract")

  private val StandIns =
    Set("placeholder", "todo", "tbd", "tba", "fixme", "xxx", "summary", "tk", "na", "n a", "none", "wip")

  def summaryOf(raw: Option[com.histmersion.api.research.Articles.SummaryOfRaw]): Either[com.histmersion.api.research.Articles.SummaryOfResultPart1, Option[com.histmersion.api.research.Articles.SummaryOfResultPart2]] =
    raw.map((_).value.trim).filter(_.nonEmpty) match
      case None    => Right(None)
      case Some(s) =>
        val norm = s.toLowerCase.replaceAll("[^a-z]+", " ").trim
        if StandIns.contains(norm) || norm.startsWith("lorem ipsum") then
          Left(
            _root_.com.histmersion.api.research.Articles.SummaryOfResultPart1(s"summary '$s' is a stand-in, not a summary — write the one-paragraph lede, or omit it " +
              "and the article's opening paragraph (or its `## Summary` section) is used")
          )
        else Right(Some(_root_.com.histmersion.api.research.Articles.SummaryOfResultPart2(s)))

  val Kinds = List("concept", "myth", "culture", "region", "timeline", "report", "index")

  private val Kind = """^[a-z][a-z0-9_-]*$""".r

  def notFound(slug: com.histmersion.api.research.Articles.NotFoundSlug): Api.Error = Api.Error(_root_.com.histmersion.api.Api.Error.Message(s"No article '$slug'"), Some(_root_.com.histmersion.api.Api.Error.Code(Write.CodeNotFound)))

  def kindOf(raw: com.histmersion.api.research.Articles.KindOfRaw): Either[com.histmersion.api.research.Articles.KindOfResultPart1, com.histmersion.api.research.Articles.KindOfResultPart2] =
    val k = (raw).value.trim.toLowerCase
    if Kind.matches(k) then Right(_root_.com.histmersion.api.research.Articles.KindOfResultPart2(k))
    else Left(_root_.com.histmersion.api.research.Articles.KindOfResultPart1(s"invalid kind '$raw' — one of ${Kinds.mkString(", ")} (or another lower-case word)"))

  def kindOfDir(dirName: com.histmersion.api.research.Articles.KindOfDirDirName): com.histmersion.api.research.Articles.KindOfDirResult =
    val d = (dirName).value.trim.toLowerCase
    if d.length > 1 && d.endsWith("s") && !Kinds.contains(d) then _root_.com.histmersion.api.research.Articles.KindOfDirResult(d.dropRight(1)) else _root_.com.histmersion.api.research.Articles.KindOfDirResult(d)

  def dirOfKind(kind: com.histmersion.api.research.Articles.DirOfKindKind): com.histmersion.api.research.Articles.DirOfKindResult = _root_.com.histmersion.api.research.Articles.DirOfKindResult((kind).value.trim.toLowerCase)

  /** The article a put makes, or why it is refused; `slug` is the server's, already checked. */
  def applyPut(slug: com.histmersion.api.research.Articles.ApplyPutSlug, p: Article.Put): Either[com.histmersion.api.research.Articles.ApplyPutResultPart1, Article] =
    val s = slug
    for
      k <- kindOf(p.kind)
      _ <- if p.title.trim.nonEmpty then Right(()) else Left("title must not be empty")
      period <- periodOf(p.periodStart, p.periodEnd)
      summary <- summaryOf(p.summary)
      filter <- p.filter.fold[Either[String, Option[Article.Filter]]](Right(None))(Article.Filter.normalise)
      reasons <- Coverage.Reason.merge(Map.empty, p.reasons)
    yield Article(
      slug = s,
      title = p.title.trim,
      kind = k,
      body = Article.Coverage.stripGenerated(p.body),
      summary = summary.orElse(lede(p.body)),
      tags = p.tags.map(_.trim).filter(_.nonEmpty).distinct,
      regions = p.regions.map(_.trim).filter(_.nonEmpty).distinct,
      period = period,
      filter = filter,
      reasons = reasons
    )

  def precondition(existing: Article, patch: Article.Patch): Either[com.histmersion.api.research.Articles.PreconditionResultPart1, Unit] =
    patch.ifUpdatedAt match
      case None       => Right(())
      case Some(want) =>
        val actual = existing.updatedAt.getOrElse("")
        if want == actual then Right(())
        else
          Left(
            _root_.com.histmersion.api.research.Articles.PreconditionResultPart1(s"'${existing.slug}' has moved on: you sent ifUpdatedAt='$want' but the stored " +
              s"updatedAt is '$actual'. Re-read the article and re-apply your patch.")
          )

  def applyPatch(existing: Article, patch: Article.Patch): Either[com.histmersion.api.research.Articles.ApplyPatchResultPart1, Article] =
    for
      _ <- precondition(existing, patch)
      kind <- patch.kind.fold[Either[String, String]](Right(existing.kind))(kindOf)
      period <- patch.period.fold[Either[String, Option[Period]]](Right(existing.period))(p =>
        periodOf(Some(p.start), Some(p.end))
      )
      body <- bodyOf(existing.body, patch)
      summary <- summaryOf(patch.summary)
      filter <- patch.filter.fold[Either[String, Option[Article.Filter]]](Right(existing.filter))(
        Article.Filter.normalise
      )
      reasons <- Coverage.Reason.merge(existing.reasons, patch.reasons)
    yield existing.modify(
      _.title).setTo(patch.title.map(_.trim).filter(_.nonEmpty).getOrElse(existing.title)).modify(_.kind).setTo(kind).modify(_.body).setTo(body).modify(_.summary).setTo(summary.orElse(existing.summary)).modify(_.tags).setTo(patch.tags.fold(existing.tags)(Write.applyList(existing.tags, _))).modify(_.regions).setTo(patch.regions.getOrElse(existing.regions).map(_.trim).filter(_.nonEmpty).distinct).modify(_.period).setTo(period).modify(_.filter).setTo(filter).modify(_.reasons).setTo(reasons
    )

  private def bodyOf(current: com.histmersion.api.research.Articles.BodyOfCurrent, patch: Article.Patch): Either[com.histmersion.api.research.Articles.BodyOfResultPart1, com.histmersion.api.research.Articles.BodyOfResultPart2] =
    val base = patch.body.map(Article.Coverage.stripGenerated).getOrElse(current)
    patch.append.map(Article.Coverage.stripGenerated).map(_.trim).filter(_.nonEmpty) match
      case None    => Right(base)
      case Some(a) =>
        patch.section.map(_.trim).filter(_.nonEmpty) match
          case None    => Right(if base.trim.isEmpty then a else s"${base.stripTrailing}\n\n$a")
          case Some(s) => appendUnderSection(base, s, a)

  def appendUnderSection(body: com.histmersion.api.research.Articles.AppendUnderSectionBody, section: com.histmersion.api.research.Articles.AppendUnderSectionSection, text: com.histmersion.api.research.Articles.AppendUnderSectionText): Either[com.histmersion.api.research.Articles.AppendUnderSectionResultPart1, com.histmersion.api.research.Articles.AppendUnderSectionResultPart2] =
    val sNorm = (section).value.replaceAll("^#+\\s*", "").trim.toLowerCase
    val lines = (body).value.split("\n", -1).toList
    val idx = lines.indexWhere { line =>
      val t = line.trim
      t.startsWith("#") && t.replaceAll("^#+\\s*", "").trim.toLowerCase == sNorm
    }
    if idx < 0 then Left(_root_.com.histmersion.api.research.Articles.AppendUnderSectionResultPart1(s"no heading matching '$section' in the article body"))
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
      Right(result)

  private def periodOf(start: Option[com.histmersion.api.research.Articles.PeriodOfStart], end: Option[com.histmersion.api.research.Articles.PeriodOfEnd]): Either[com.histmersion.api.research.Articles.PeriodOfResultPart1, Option[Period]] =
    (start, end) match
      case (Some(a), Some(b)) if a <= b => Right(Some(Period(a, b)))
      case (Some(a), Some(b))           => Left(_root_.com.histmersion.api.research.Articles.PeriodOfResultPart1(s"period_start ($a) must not be after period_end ($b)"))
      case (None, None)                 => Right(None)
      case _                            => Left(_root_.com.histmersion.api.research.Articles.PeriodOfResultPart1("give both periodStart and periodEnd, or neither"))
