package com.histmersion.api

/** ARTICLES — the compiled wiki (plan §5.2 stream 2.7).
  *
  * A [[Source]] is what the corpus says; `Source.notes` is what an agent compiled about ONE source. An **article** is
  * the other axis: prose compiled ACROSS sources — the concept / myth / culture / region / timeline / report pages of
  * the `wiki/concepts`, `wiki/myths`, … directories. The database is the store of record; `histmersion export` writes
  * each article back to `<kind>/<slug>.md` for Obsidian and git, and `ArticleNote` is the exact inverse pair that makes
  * that round-trip byte-stable.
  *
  * Everything an article points at is an Obsidian wikilink in its body — `[[other_slug]]` for an article, `[[citekey]]`
  * for a source — so the link graph never has to be maintained by hand: [[ArticleLinks]] extracts it from the body on
  * every write.
  *
  * Everything here is PURE (parse, render, link extraction, patch merge); `db.ArticleRepo` persists the results and
  * `server` renders them.
  */

/** Pure write rules for articles: validation, full replace, and the selective merge. The mirror of [[Write]] on the
  * source side.
  *
  * Also hosts `heading` and `lede` — pure text extractors used by [[ArticleMarkdown]] (shared) and [[ArticleNote]]
  * (JVM-only, snakeyaml-dependent).
  */
object Articles:

  /** The article's `# Title` heading, if the body opens with one. */
  def heading(body: String): Option[String] =
    body.linesIterator
      .map(_.trim)
      .find(_.nonEmpty)
      .filter(_.startsWith("# "))
      .map(_.drop(2).trim)
      .filter(_.nonEmpty)

  /** The lede: the first prose paragraph after the `# Title`, with the wiki's italic markers stripped. Used as the
    * summary when the frontmatter has none.
    */
  def lede(body: String): Option[String] =
    val lines = body.linesIterator.toList.dropWhile(_.trim.isEmpty)
    val rest0 = lines.headOption.filter(_.trim.startsWith("# ")).fold(lines)(_ => lines.tail)
    // a body that opens with its own `## Summary` (or `## Lede`/`## Abstract`) section: that is the lede
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

  /** Words an author leaves in place of a summary meaning to come back — which then shipped: three articles showed
    * "placeholder" as their card text for a month (2026-08-28 batch).
    */
  private val StandIns =
    Set("placeholder", "todo", "tbd", "tba", "fixme", "xxx", "summary", "tk", "na", "n a", "none", "wip")

  /** A summary as given: trimmed, blank = absent, and a stand-in refused rather than stored. */
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

  /** The kinds the compiled wiki uses today. Not a constraint — `kind` is free text so a new section needs no migration
    * — but the vocabulary the tools advertise and the CLI walks.
    */
  val Kinds = List("concept", "myth", "culture", "region", "timeline", "report", "index")

  // BOUNDED: a slug is a URL segment and, on the export path, a file name. The character class is
  // unchanged (existing slugs use '.' and '-'); only the missing length limit is added.
  private val Slug = """^[a-z0-9][a-z0-9_.-]{0,120}$""".r
  private val Kind = """^[a-z][a-z0-9_-]*$""".r

  def notFound(slug: String): ApiError = ApiError(s"No article '$slug'", Some(Write.CodeNotFound))

  /** Normalise and validate a slug (it is both the primary key and a filename). */
  def slugOf(raw: String): Either[String, String] =
    val s = raw.trim.toLowerCase
    if Slug.matches(s) then Right(s)
    else Left(s"invalid slug '$raw' — use lower-case letters, digits, '_', '-' or '.' (e.g. sea_peoples)")

  def kindOf(raw: String): Either[String, String] =
    val k = raw.trim.toLowerCase
    if Kind.matches(k) then Right(k)
    else Left(s"invalid kind '$raw' — one of ${Kinds.mkString(", ")} (or another lower-case word)")

  /** The singular kind a wiki directory holds: `concepts` → `concept`, `timeline` → `timeline`. */
  def kindOfDir(dirName: String): String =
    val d = dirName.trim.toLowerCase
    if d.length > 1 && d.endsWith("s") && !Kinds.contains(d) then d.dropRight(1) else d

  /** The directory an exported article belongs in — the inverse of [[kindOfDir]] for the directories the wiki actually
    * has, and the identity for anything else.
    */
  def dirOfKind(kind: String): String = kind.trim.toLowerCase

  /** Full replace (`put_article`). */
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

  /** The optimistic-concurrency check, separate so the endpoint can answer 412 specifically. */
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

  /** Selective merge (`patch_article`): a present part replaces only itself, an absent part is left exactly as it is.
    * The body is replaced wholesale when given — an article is prose, so there is no meaningful element-wise merge;
    * `append` covers the common "add a section" case without a re-upload.
    */
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

  /** `body` replaces, `append` adds text. When `section` is given (e.g. `"## Disagreements"`), the `append` text is
    * inserted immediately after that heading's content and before the next heading at the same or higher level. Without
    * `section`, `append` goes at the end (A16).
    */
  private def bodyOf(current: String, patch: ArticlePatch): Either[String, String] =
    // an exported note sent back as the body (#958): its generated sections never enter the store
    val base = patch.body.map(ArticleCoverage.stripGenerated).getOrElse(current)
    patch.append.map(ArticleCoverage.stripGenerated).map(_.trim).filter(_.nonEmpty) match
      case None    => Right(base)
      case Some(a) =>
        patch.section.map(_.trim).filter(_.nonEmpty) match
          case None    => Right(if base.trim.isEmpty then a else s"${base.stripTrailing}\n\n$a")
          case Some(s) => appendUnderSection(base, s, a)

  /** Append `text` under the heading that matches `section` (e.g. `"## Disagreements"`). The match is case-insensitive
    * and ignores leading `#` differences — `"Disagreements"` matches `## Disagreements`. The text is inserted before
    * the next heading at the same or higher level.
    */
  def appendUnderSection(body: String, section: String, text: String): Either[String, String] =
    val sNorm = section.replaceAll("^#+\\s*", "").trim.toLowerCase
    val lines = body.split("\n", -1).toList
    // find the heading line that matches
    val idx = lines.indexWhere { line =>
      val t = line.trim
      t.startsWith("#") && t.replaceAll("^#+\\s*", "").trim.toLowerCase == sNorm
    }
    if idx < 0 then Left(s"no heading matching '$section' in the article body")
    else
      // determine the heading level (number of leading #s)
      val level = lines(idx).trim.takeWhile(_ == '#').length
      // find the next heading at the same or higher level
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
