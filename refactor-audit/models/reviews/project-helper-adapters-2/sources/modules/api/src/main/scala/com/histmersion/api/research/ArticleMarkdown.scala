package com.histmersion.api.research

import com.histmersion.api.{Markdown, OffsetPage, Plural, YearFmt}
import com.histmersion.api.checks.{FormatField, FormatMarkdown}
import com.histmersion.api.library.Label
import com.histmersion.api.given
import com.histmersion.api.library.Source
object ArticleMarkdown:

  type ArticleResult = ArticleResult.T
  object ArticleResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleParts = ArticleParts.T
  object ArticleParts extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleFullResult = ArticleFullResult.T
  object ArticleFullResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CoverageResult = CoverageResult.T
  object CoverageResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MetaLineResult = MetaLineResult.T
  object MetaLineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MetaLineKind = _root_.com.histmersion.api.research.Article.KindValue
  val MetaLineKind: _root_.com.histmersion.api.research.Article.KindValue.type = _root_.com.histmersion.api.research.Article.KindValue
  type MetaLineRegions = _root_.com.histmersion.api.research.Article.Row.Regions
  val MetaLineRegions: _root_.com.histmersion.api.research.Article.Row.Regions.type = _root_.com.histmersion.api.research.Article.Row.Regions
  type MetaLinePeriod = MetaLinePeriod.T
  object MetaLinePeriod extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MetaLineTags = _root_.com.histmersion.api.research.Article.Row.Tags
  val MetaLineTags: _root_.com.histmersion.api.research.Article.Row.Tags.type = _root_.com.histmersion.api.research.Article.Row.Tags
  type ChipLinksListResult = ChipLinksListResult.T
  object ChipLinksListResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChipLinksListValues = ChipLinksListValues.T
  object ChipLinksListValues extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChipLinksListHashKey = ChipLinksListHashKey.T
  object ChipLinksListHashKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StripSourcesSectionResult = StripSourcesSectionResult.T
  object StripSourcesSectionResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StripSourcesSectionBody = StripSourcesSectionBody.T
  object StripSourcesSectionBody extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticlePartsResult = ArticlePartsResult.T
  object ArticlePartsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticlePartsShow = ArticlePartsShow.T
  object ArticlePartsShow extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StripMdResult = StripMdResult.T
  object StripMdResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StripMdS = StripMdS.T
  object StripMdS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BacklinksResult = BacklinksResult.T
  object BacklinksResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BacklinksTruncate = BacklinksTruncate.T
  object BacklinksTruncate extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourcesCitedResult = SourcesCitedResult.T
  object SourcesCitedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourcesCitedTruncate = SourcesCitedTruncate.T
  object SourcesCitedTruncate extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VersionsResult = VersionsResult.T
  object VersionsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VersionsSlug = VersionsSlug.T
  object VersionsSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VersionResult = VersionResult.T
  object VersionResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ListResult = ListResult.T
  object ListResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val AllParts = List("body", "summary", "coverage", "cited", "backlinks")

  def article(v: Article.View, parts: List[com.histmersion.api.research.ArticleMarkdown.ArticleParts] = Nil): com.histmersion.api.research.ArticleMarkdown.ArticleResult =
    if parts.isEmpty then _root_.com.histmersion.api.research.ArticleMarkdown.ArticleResult((articleFull(v)).value)
    else _root_.com.histmersion.api.research.ArticleMarkdown.ArticleResult((articleParts(v, (parts.filter(AllParts.contains)).map(refactorValue => _root_.com.histmersion.api.research.ArticleMarkdown.ArticlePartsShow((refactorValue).value)))).value)

  private def articleFull(v: Article.View): com.histmersion.api.research.ArticleMarkdown.ArticleFullResult =
    val a = v.article
    val body = a.body.trim
    val line = metaLine(a.kind, a.regions, YearFmt.period(a.periodStart, a.periodEnd), a.tags)
    val head =
      if Articles.heading(body).isEmpty then s"# ${a.title}\n\n" + (if line.isEmpty then "" else s"$line\n\n") + body
      else if line.isEmpty then body
      else
        body.indexOf('\n') match
          case -1 => s"$body\n\n$line"
          case i  => s"${body.take(i)}\n\n$line${body.drop(i)}"
    val footer = sourcesCited(v.sourcesCited)
    val trimmed = if (footer).value.isEmpty then head else stripSourcesSection(_root_.com.histmersion.api.research.ArticleMarkdown.StripSourcesSectionBody(head))
    trimmed + coverage(v) + backlinks(v.backlinks) + footer + FormatMarkdown.notice(v.formatting)

  private def coverage(v: Article.View): com.histmersion.api.research.ArticleMarkdown.CoverageResult = _root_.com.histmersion.api.research.ArticleMarkdown.CoverageResult(v.coverage.fold("")(c => "\n\n" + Article.Coverage.sections(c)))

  private[api] def metaLine(kind: com.histmersion.api.research.ArticleMarkdown.MetaLineKind, regions: List[com.histmersion.api.research.ArticleMarkdown.MetaLineRegions], period: com.histmersion.api.research.ArticleMarkdown.MetaLinePeriod, tags: List[com.histmersion.api.research.ArticleMarkdown.MetaLineTags]): com.histmersion.api.research.ArticleMarkdown.MetaLineResult =
    val parts = List(Label.display(_root_.com.histmersion.api.library.Label.DisplayS((kind).value.trim)), chipLinksList(regions, _root_.com.histmersion.api.research.ArticleMarkdown.ChipLinksListHashKey("place")), (period).value.trim, chipLinksList(tags, _root_.com.histmersion.api.research.ArticleMarkdown.ChipLinksListHashKey("tag")))
      .filter(_.nonEmpty)
    if parts.isEmpty then _root_.com.histmersion.api.research.ArticleMarkdown.MetaLineResult("") else s"_${parts.mkString(" · ")}_"

  private def chipLinksList(values: List[com.histmersion.api.research.ArticleMarkdown.ChipLinksListValues], hashKey: com.histmersion.api.research.ArticleMarkdown.ChipLinksListHashKey): com.histmersion.api.research.ArticleMarkdown.ChipLinksListResult =
    values
      .filter((_).value.nonEmpty)
      .map(v => s"[${Label.display(v).replace("[", "\\[").replace("]", "\\]")}](/#$hashKey=${Markdown.enc(v)})")
      .mkString(", ")

  def stripSourcesSection(body: com.histmersion.api.research.ArticleMarkdown.StripSourcesSectionBody): com.histmersion.api.research.ArticleMarkdown.StripSourcesSectionResult =
    val heading = """^#{1,6}\s*(sources\b.*|references|bibliography|works cited)\s*$""".r
    val lines = (body).value.linesIterator.toList
    lines.lastIndexWhere(l => heading.matches(l.trim.toLowerCase)) match
      case -1 => _root_.com.histmersion.api.research.ArticleMarkdown.StripSourcesSectionResult((body).value)
      case i  =>
        val rest = lines.drop(i + 1).map(_.trim).filter(_.nonEmpty)
        if rest.forall(l => l.startsWith("-") || l.startsWith("*") || l.startsWith("[[") || l.startsWith("1."))
        then _root_.com.histmersion.api.research.ArticleMarkdown.StripSourcesSectionResult(lines.take(i).mkString("\n").stripTrailing)
        else _root_.com.histmersion.api.research.ArticleMarkdown.StripSourcesSectionResult((body).value)

  private def articleParts(v: Article.View, show: List[com.histmersion.api.research.ArticleMarkdown.ArticlePartsShow]): com.histmersion.api.research.ArticleMarkdown.ArticlePartsResult =
    val a = v.article
    val out = List.newBuilder[String]

    if show.contains("summary") then
      a.summary.map(_.trim).filter(_.nonEmpty).foreach(s => out += s"_${(stripMd(s)).value.take(200)}_")

    if show.contains("body") then
      val body = a.body.trim
      val line = metaLine(a.kind, a.regions, YearFmt.period(a.periodStart, a.periodEnd), a.tags)
      val head =
        if Articles.heading(body).isEmpty then s"# ${a.title}\n\n" + (if line.isEmpty then "" else s"$line\n\n") + body
        else if line.isEmpty then body
        else
          body.indexOf('\n') match
            case -1 => s"$body\n\n$line"
            case i  => s"${body.take(i)}\n\n$line${body.drop(i)}"
      out += head

    if show.contains("coverage") then v.coverage.foreach(c => out += Article.Coverage.sections(c))

    if show.contains("backlinks") then
      val bl = backlinks(v.backlinks, truncate = _root_.com.histmersion.api.research.ArticleMarkdown.BacklinksTruncate(true))
      if (bl).value.nonEmpty then out += (bl).value

    if show.contains("cited") then
      val sc = sourcesCited(v.sourcesCited, truncate = _root_.com.histmersion.api.research.ArticleMarkdown.SourcesCitedTruncate(true))
      if (sc).value.nonEmpty then out += (sc).value

    if (show.contains("body") || show.contains("summary")) && v.formatting.nonEmpty then
      val shown = v.formatting.filter(i => show.contains(if i.field == FormatField.Summary then "summary" else "body"))
      if shown.nonEmpty then out += FormatMarkdown.notice(shown).trim

    val result = out.result()
    if result.isEmpty then _root_.com.histmersion.api.research.ArticleMarkdown.ArticlePartsResult(s"# ${a.title}\n\n_(no parts selected)_")
    else _root_.com.histmersion.api.research.ArticleMarkdown.ArticlePartsResult(result.mkString("\n\n"))

  private def stripMd(s: com.histmersion.api.research.ArticleMarkdown.StripMdS): com.histmersion.api.research.ArticleMarkdown.StripMdResult =
    _root_.com.histmersion.api.research.ArticleMarkdown.StripMdResult((s).value.replaceAll("""\[\[([^\]|]+)\|([^\]]+)\]\]""", "$2")
      .replaceAll("""\[\[([^\]]+)\]\]""", "$1")
      .replaceAll("""\[([^\]]+)\]\([^)]+\)""", "$1")
      .replaceAll("""[*_]{1,2}""", "")
      .replaceAll("""`""", ""))

  def backlinks(rows: List[Backlink], truncate: com.histmersion.api.research.ArticleMarkdown.BacklinksTruncate = _root_.com.histmersion.api.research.ArticleMarkdown.BacklinksTruncate(false)): com.histmersion.api.research.ArticleMarkdown.BacklinksResult =
    if rows.isEmpty then _root_.com.histmersion.api.research.ArticleMarkdown.BacklinksResult("")
    else
      val items = rows.map { b =>
        val title = b.title.map(t => if ((truncate) == _root_.com.histmersion.api.research.ArticleMarkdown.BacklinksTruncate(true)) then s" — ${stripMd(t).take(200)}" else s" — $t").getOrElse("")
        s"- [[${b.id}]]$title"
      }
      _root_.com.histmersion.api.research.ArticleMarkdown.BacklinksResult(s"\n\n## Backlinks (${rows.size})\n\n${items.mkString("\n")}")

  def sourcesCited(rows: List[CitedSource], truncate: com.histmersion.api.research.ArticleMarkdown.SourcesCitedTruncate = _root_.com.histmersion.api.research.ArticleMarkdown.SourcesCitedTruncate(false)): com.histmersion.api.research.ArticleMarkdown.SourcesCitedResult =
    if rows.isEmpty then _root_.com.histmersion.api.research.ArticleMarkdown.SourcesCitedResult("")
    else
      val items = rows.map { s =>
        val t = s.title.map(_.value)
          .map(v => if ((truncate) == _root_.com.histmersion.api.research.ArticleMarkdown.SourcesCitedTruncate(true)) then stripMd(v).take(200) else v)
          .getOrElse("_(not in the corpus)_")
        val auth = Source.Ref.shortAuthors(s.authors.map(_.value))
        val yr = s.year.map(_.value.toInt).map(_.toString).getOrElse("")
        val byline = List(auth, yr).filter(_.nonEmpty).mkString(" ")
        val tail = List(byline, s"_${t}_", s.status.map(st => s"**${st.wire}**").getOrElse(""))
          .filter(_.nonEmpty)
          .mkString(" · ")
        s"- [[${s.citekey}]] — $tail"
      }
      _root_.com.histmersion.api.research.ArticleMarkdown.SourcesCitedResult(s"\n\n## Sources cited (${rows.size})\n\n${items.mkString("\n")}")

  def versions(slug: com.histmersion.api.research.ArticleMarkdown.VersionsSlug, vs: List[Article.Version]): com.histmersion.api.research.ArticleMarkdown.VersionsResult =
    if vs.isEmpty then _root_.com.histmersion.api.research.ArticleMarkdown.VersionsResult(s"_No versions recorded for **$slug**._")
    else
      val items = vs.map { v =>
        val sum = v.summary.map(s => s" — ${s.take(120)}").getOrElse("")
        s"- **${v.id}** · ${v.updatedAt} · ${Plural.count(v.wordCount, _root_.com.histmersion.api.Plural.CountSingular("word"))}$sum"
      }
      _root_.com.histmersion.api.research.ArticleMarkdown.VersionsResult(s"### ${vs.size} version(s) of $slug\n\n${items.mkString("\n")}")

  def version(v: Article.VersionFull): com.histmersion.api.research.ArticleMarkdown.VersionResult =
    val sum = v.summary.map(s => s"\n\n_${s.take(200)}_").getOrElse("")
    _root_.com.histmersion.api.research.ArticleMarkdown.VersionResult(s"### Version ${v.id} of ${v.slug}\n\n_saved ${v.updatedAt}, archived ${v.createdAt}_$sum\n\n---\n\n${v.body}")

  def list(r: Article.List): com.histmersion.api.research.ArticleMarkdown.ListResult =
    OffsetPage.markdown(r.page, _root_.com.histmersion.api.OffsetPage.MarkdownNoun("article(s)"), _root_.com.histmersion.api.OffsetPage.MarkdownEmpty("_No matching articles._"), r.articles) { a =>
      val tagsStr = a.tags.mkString(", ")
      val meta = List(a.kind, YearFmt.period(a.periodStart, a.periodEnd), tagsStr)
        .map(_.trim)
        .filter(_.nonEmpty)
        .mkString(" · ")
      val sum = a.summary.map(s => s"\n  ${s.trim}").getOrElse("")
      _root_.com.histmersion.api.OffsetPage.MarkdownLineResult(s"- **${a.slug}** — ${a.title}\n  _${meta}_$sum")
    }
