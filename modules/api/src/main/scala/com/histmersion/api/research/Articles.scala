package com.histmersion.api.research

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.Write
import com.histmersion.api.catalogue.Period
import com.histmersion.api.given
import com.histmersion.api.Api
object Articles:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  def heading(body: com.histmersion.api.research.Article.Body): Option[com.histmersion.api.research.Article.Heading] =
    ((body).value.linesIterator
      .map(_.trim)
      .find(_.nonEmpty)
      .filter(_.startsWith("# "))
      .map(_.drop(2).trim)
      .filter(_.nonEmpty)).map(refactorValue3 => _root_.com.histmersion.api.research.Article.Heading(refactorValue3))

  def lede(body: com.histmersion.api.research.Article.Body): Option[com.histmersion.api.research.Article.SummaryText] =
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
    (Option(plain).filter(s => s.nonEmpty && !s.startsWith("#") && !s.startsWith("!"))).map(refactorValue4 => _root_.com.histmersion.api.research.Article.SummaryText(refactorValue4))

  private val LedeHeadings = Set("## summary", "## lede", "## abstract")

  private val StandIns =
    Set("placeholder", "todo", "tbd", "tba", "fixme", "xxx", "summary", "tk", "na", "n a", "none", "wip")

  def summaryOf(raw: Option[com.histmersion.api.research.Article.SummaryText]): Either[com.histmersion.api.research.Articles.Problem, Option[com.histmersion.api.research.Article.SummaryText]] =
    raw.map((_).value.trim).filter(_.nonEmpty) match
      case None    => Right(None)
      case Some(s) =>
        val norm = s.toLowerCase.replaceAll("[^a-z]+", " ").trim
        if StandIns.contains(norm) || norm.startsWith("lorem ipsum") then
          Left(
            _root_.com.histmersion.api.research.Articles.Problem(s"summary '$s' is a stand-in, not a summary — write the one-paragraph lede, or omit it " +
              "and the article's opening paragraph (or its `## Summary` section) is used")
          )
        else Right(Some(_root_.com.histmersion.api.research.Article.SummaryText(s)))

  val Kinds = List("concept", "myth", "culture", "region", "timeline", "report", "index")

  private val Kind = """^[a-z][a-z0-9_-]*$""".r

  def notFound(slug: com.histmersion.api.research.Article.Slug): Api.Error = Api.Error(_root_.com.histmersion.api.Api.Error.Problem(s"No article '$slug'"), Some(_root_.com.histmersion.api.Api.Error.Code(Write.CodeNotFound)))

  def kindOf(raw: com.histmersion.api.research.Article.Kind.Wire): Either[com.histmersion.api.research.Articles.Problem, com.histmersion.api.research.Article.Kind.Wire] =
    val k = (raw).value.trim.toLowerCase
    if Kind.matches(k) then Right(_root_.com.histmersion.api.research.Article.Kind.Wire(k))
    else Left(_root_.com.histmersion.api.research.Articles.Problem(s"invalid kind '$raw' — one of ${Kinds.mkString(", ")} (or another lower-case word)"))

  def kindOfDir(dirName: String): com.histmersion.api.research.Article.Kind.Wire =
    val d = (dirName).trim.toLowerCase
    if d.length > 1 && d.endsWith("s") && !Kinds.contains(d) then _root_.com.histmersion.api.research.Article.Kind.Wire(d.dropRight(1)) else _root_.com.histmersion.api.research.Article.Kind.Wire(d)

  def dirOfKind(kind: com.histmersion.api.research.Article.Kind.Wire): String = ((kind).value.trim.toLowerCase)

  /** The article a put makes, or why it is refused; `slug` is the server's, already checked. */
  def applyPut(slug: com.histmersion.api.research.Article.Slug, p: Article.Put): Either[com.histmersion.api.research.Articles.Problem, Article] =
    val s = slug
    for
      k <- kindOf(p.kind)
      _ <- if p.title.value.trim.nonEmpty then Right(()) else Left(Problem("title must not be empty"))
      period <- periodOf(p.periodStart, p.periodEnd)
      summary <- summaryOf(p.summary)
      filter <- p.filter.fold[Either[Problem, Option[Article.Filter]]](Right(None))(f => Article.Filter.normalise(f).left.map(e => Problem(e.value)))
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

  def precondition(existing: Article, patch: Article.Patch): Either[com.histmersion.api.research.Articles.Problem, Unit] =
    patch.ifUpdatedAt match
      case None       => Right(())
      case Some(want) =>
        val actual = existing.updatedAt.getOrElse("")
        if want == actual then Right(())
        else
          Left(
            _root_.com.histmersion.api.research.Articles.Problem(s"'${existing.slug}' has moved on: you sent ifUpdatedAt='$want' but the stored " +
              s"updatedAt is '$actual'. Re-read the article and re-apply your patch.")
          )

  def applyPatch(existing: Article, patch: Article.Patch): Either[com.histmersion.api.research.Articles.Problem, Article] =
    for
      _ <- precondition(existing, patch)
      kind <- patch.kind.fold[Either[Problem, Article.KindValue]](Right(existing.kind))(kindOf)
      period <- patch.period.fold[Either[Problem, Option[Period]]](Right(existing.period))(p =>
        periodOf(Some(p.start), Some(p.end))
      )
      body <- bodyOf(existing.body, patch)
      summary <- summaryOf(patch.summary)
      filter <- patch.filter.fold[Either[Problem, Option[Article.Filter]]](Right(existing.filter))(f =>
        Article.Filter.normalise(f).left.map(e => Problem(e.value))
      )
      reasons <- Coverage.Reason.merge(existing.reasons, patch.reasons)
    yield existing.modify(
      _.title).setTo(patch.title.map(t => Article.Title(t.value.trim)).filter(_.value.nonEmpty).getOrElse(existing.title)).modify(_.kind).setTo(kind).modify(_.body).setTo(body).modify(_.summary).setTo(summary.orElse(existing.summary)).modify(_.tags).setTo(patch.tags.fold(existing.tags)(Write.applyList(existing.tags, _))).modify(_.regions).setTo(patch.regions.getOrElse(existing.regions).map(r => Article.Regions(r.value.trim)).filter(_.value.nonEmpty).distinct).modify(_.period).setTo(period).modify(_.filter).setTo(filter).modify(_.reasons).setTo(reasons
    )

  private def bodyOf(current: com.histmersion.api.research.Article.Body, patch: Article.Patch): Either[com.histmersion.api.research.Articles.Problem, com.histmersion.api.research.Article.Body] =
    val base = patch.body.map(Article.Coverage.stripGenerated).getOrElse(current)
    patch.append.map(a => Article.Coverage.stripGenerated(Article.Body(a.value)).value.trim).filter(_.nonEmpty) match
      case None    => Right(base)
      case Some(a) =>
        patch.section.map(_.value.trim).filter(_.nonEmpty) match
          case None    => Right(Article.Body(if base.value.trim.isEmpty then a else s"${base.value.stripTrailing}\n\n$a"))
          case Some(s) => appendUnderSection(base, Article.Section(s), Article.Prose(a))

  def appendUnderSection(body: com.histmersion.api.research.Article.Body, section: com.histmersion.api.research.Article.Section, text: com.histmersion.api.research.Article.Prose): Either[com.histmersion.api.research.Articles.Problem, com.histmersion.api.research.Article.Body] =
    val sNorm = (section).value.replaceAll("^#+\\s*", "").trim.toLowerCase
    val lines = (body).value.split("\n", -1).toList
    val idx = lines.indexWhere { line =>
      val t = line.trim
      t.startsWith("#") && t.replaceAll("^#+\\s*", "").trim.toLowerCase == sNorm
    }
    if idx < 0 then Left(_root_.com.histmersion.api.research.Articles.Problem(s"no heading matching '$section' in the article body"))
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
      Right(_root_.com.histmersion.api.research.Article.Body(result))

  private def periodOf(start: Option[com.histmersion.api.catalogue.Year], end: Option[com.histmersion.api.catalogue.Year]): Either[com.histmersion.api.research.Articles.Problem, Option[Period]] =
    (start, end) match
      case (Some(a), Some(b)) if a.value <= b.value => Right(Some(Period(a, b)))
      case (Some(a), Some(b))           => Left(_root_.com.histmersion.api.research.Articles.Problem(s"period_start ($a) must not be after period_end ($b)"))
      case (None, None)                 => Right(None)
      case _                            => Left(_root_.com.histmersion.api.research.Articles.Problem("give both periodStart and periodEnd, or neither"))
