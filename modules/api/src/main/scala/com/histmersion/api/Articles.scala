package com.histmersion.api

object Articles:

  def heading(body: String): Option[String] =
    body.linesIterator
      .map(_.trim)
      .find(_.nonEmpty)
      .filter(_.startsWith("# "))
      .map(_.drop(2).trim)
      .filter(_.nonEmpty)

  def lede(body: String): Option[String] =
    val lines = body.linesIterator.toList.dropWhile(_.trim.isEmpty)
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
    Option(plain).filter(s => s.nonEmpty && !s.startsWith("#") && !s.startsWith("!"))

  private val LedeHeadings = Set("## summary", "## lede", "## abstract")

  private val StandIns =
    Set("placeholder", "todo", "tbd", "tba", "fixme", "xxx", "summary", "tk", "na", "n a", "none", "wip")

  def summaryOf(raw: Option[String]): Either[String, Option[String]] =
    raw.map(_.trim).filter(_.nonEmpty) match
      case None    => Right(None)
      case Some(s) =>
        val norm = s.toLowerCase.replaceAll("[^a-z]+", " ").trim
        if StandIns.contains(norm) || norm.startsWith("lorem ipsum") then
          Left(
            s"summary '$s' is a stand-in, not a summary — write the one-paragraph lede, or omit it " +
              "and the article's opening paragraph (or its `## Summary` section) is used"
          )
        else Right(Some(s))

  val Kinds = List("concept", "myth", "culture", "region", "timeline", "report", "index")

  private val Kind = """^[a-z][a-z0-9_-]*$""".r

  def notFound(slug: String): ApiError = ApiError(s"No article '$slug'", Some(Write.CodeNotFound))

  def slugOf(raw: String): Either[String, String] = com.histmersion.values.Slug.parse(raw).map(_.value)

  def kindOf(raw: String): Either[String, String] =
    val k = raw.trim.toLowerCase
    if Kind.matches(k) then Right(k)
    else Left(s"invalid kind '$raw' — one of ${Kinds.mkString(", ")} (or another lower-case word)")

  def kindOfDir(dirName: String): String =
    val d = dirName.trim.toLowerCase
    if d.length > 1 && d.endsWith("s") && !Kinds.contains(d) then d.dropRight(1) else d

  def dirOfKind(kind: String): String = kind.trim.toLowerCase

  def applyPut(slug: String, p: ArticlePut): Either[String, Article] =
    for
      s <- slugOf(slug)
      k <- kindOf(p.kind)
      _ <- if p.title.trim.nonEmpty then Right(()) else Left("title must not be empty")
      period <- periodOf(p.periodStart, p.periodEnd)
      summary <- summaryOf(p.summary)
      filter <- p.filter.fold[Either[String, Option[ArticleFilter]]](Right(None))(ArticleFilter.normalise)
      reasons <- CoverageReason.merge(Map.empty, p.reasons)
    yield Article(
      slug = s,
      title = p.title.trim,
      kind = k,
      body = ArticleCoverage.stripGenerated(p.body),
      summary = summary.orElse(lede(p.body)),
      tags = p.tags.map(_.trim).filter(_.nonEmpty).distinct,
      regions = p.regions.map(_.trim).filter(_.nonEmpty).distinct,
      period = period,
      filter = filter,
      reasons = reasons
    )

  def precondition(existing: Article, patch: ArticlePatch): Either[String, Unit] =
    patch.ifUpdatedAt match
      case None       => Right(())
      case Some(want) =>
        val actual = existing.updatedAt.getOrElse("")
        if want == actual then Right(())
        else
          Left(
            s"'${existing.slug}' has moved on: you sent ifUpdatedAt='$want' but the stored " +
              s"updatedAt is '$actual'. Re-read the article and re-apply your patch."
          )

  def applyPatch(existing: Article, patch: ArticlePatch): Either[String, Article] =
    for
      _ <- precondition(existing, patch)
      kind <- patch.kind.fold[Either[String, String]](Right(existing.kind))(kindOf)
      period <- patch.period.fold[Either[String, Option[Period]]](Right(existing.period))(p =>
        periodOf(Some(p.start), Some(p.end))
      )
      body <- bodyOf(existing.body, patch)
      summary <- summaryOf(patch.summary)
      filter <- patch.filter.fold[Either[String, Option[ArticleFilter]]](Right(existing.filter))(
        ArticleFilter.normalise
      )
      reasons <- CoverageReason.merge(existing.reasons, patch.reasons)
    yield existing.copy(
      title = patch.title.map(_.trim).filter(_.nonEmpty).getOrElse(existing.title),
      kind = kind,
      body = body,
      summary = summary.orElse(existing.summary),
      tags = patch.tags.fold(existing.tags)(Write.applyList(existing.tags, _)),
      regions = patch.regions.getOrElse(existing.regions).map(_.trim).filter(_.nonEmpty).distinct,
      period = period,
      filter = filter,
      reasons = reasons
    )

  private def bodyOf(current: String, patch: ArticlePatch): Either[String, String] =
    val base = patch.body.map(ArticleCoverage.stripGenerated).getOrElse(current)
    patch.append.map(ArticleCoverage.stripGenerated).map(_.trim).filter(_.nonEmpty) match
      case None    => Right(base)
      case Some(a) =>
        patch.section.map(_.trim).filter(_.nonEmpty) match
          case None    => Right(if base.trim.isEmpty then a else s"${base.stripTrailing}\n\n$a")
          case Some(s) => appendUnderSection(base, s, a)

  def appendUnderSection(body: String, section: String, text: String): Either[String, String] =
    val sNorm = section.replaceAll("^#+\\s*", "").trim.toLowerCase
    val lines = body.split("\n", -1).toList
    val idx = lines.indexWhere { line =>
      val t = line.trim
      t.startsWith("#") && t.replaceAll("^#+\\s*", "").trim.toLowerCase == sNorm
    }
    if idx < 0 then Left(s"no heading matching '$section' in the article body")
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
      val result = (before.mkString("\n").stripTrailing + "\n\n" + text.trim +
        (if after.isEmpty then "" else "\n\n" + after.mkString("\n")))
      Right(result)

  private def periodOf(start: Option[Long], end: Option[Long]): Either[String, Option[Period]] =
    (start, end) match
      case (Some(a), Some(b)) if a <= b => Right(Some(Period(a, b)))
      case (Some(a), Some(b))           => Left(s"period_start ($a) must not be after period_end ($b)")
      case (None, None)                 => Right(None)
      case _                            => Left("give both periodStart and periodEnd, or neither")
