package com.histmersion
package api.research

import com.histmersion.api.{Markdown, OffsetPage, Plural}
import com.histmersion.api.checks.{FormatField, FormatMarkdown}
import com.histmersion.api.library.Label
import com.histmersion.api.given
import com.histmersion.api.library.Source
import com.histmersion.api.automation.Creation
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.catalogue.Year
@tolerated(LintRule.G9PrefixRole, "pending G9 (namespaces, not prefixes)")
object ArticleMarkdown:

  val AllParts = List("body", "summary", "coverage", "cited", "backlinks")

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def article(v: Article.View, parts: List[Article.Part] = Nil): String =
    if parts.isEmpty then ((articleFull(v)))
    else ((articleParts(v, (parts.filter(AllParts.contains)).map(refactorValue => refactorValue))))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def articleFull(v: Article.View): String =
    val a = v.article
    val body = a.body.value.trim
    val line =
      metaLine(a.kind.value, a.regions.map(_.value), Year.period(a.periodStart, a.periodEnd), a.tags.map(_.value))
    val head =
      if Articles.heading(Article.Body(body)).isEmpty then
        s"# ${a.title}\n\n" + (if line.isEmpty then "" else s"$line\n\n") + body
      else if line.isEmpty then body
      else
        body.indexOf('\n') match
          case -1 => s"$body\n\n$line"
          case i  => s"${body.take(i)}\n\n$line${body.drop(i)}"
    val footer = sourcesCited(v.sourcesCited)
    val trimmed = if (footer).isEmpty then head else stripSourcesSection(Article.Body(head)).value
    val created = Creation.line(a.creator, a.createdAt).map(l => s"\n\n_${l}_").getOrElse("")
    trimmed + created + coverage(v) + backlinks(v.backlinks) + footer + FormatMarkdown.notice(v.formatting)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def coverage(v: Article.View): String = (v.coverage.fold("")(c => "\n\n" + Article.Coverage.sections(c)))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private[api] def metaLine(kind: String, regions: List[String], period: String, tags: List[String]): String =
    val parts = List(
      Label.display(Label.Text(kind.trim)).value,
      chipLinksList(regions, ("place")),
      period.trim,
      chipLinksList(tags, ("tag"))
    )
      .filter(_.nonEmpty)
    if parts.isEmpty then ("") else s"_${parts.mkString(" · ")}_"

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def chipLinksList(values: List[String], hashKey: String): String =
    values
      .filter((_).nonEmpty)
      .map(v =>
        s"[${Label.display(Label.Text(v)).value.replace("[", "\\[").replace("]", "\\]")}](/#$hashKey=${Markdown.enc(v)})"
      )
      .mkString(", ")

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def stripSourcesSection(body: Article.Body): Article.Body =
    val heading = """^#{1,6}\s*(sources\b.*|references|bibliography|works cited)\s*$""".r
    val lines = (body).value.linesIterator.toList
    lines.lastIndexWhere(l => heading.matches(l.trim.toLowerCase)) match
      case -1 => Article.Body((body).value)
      case i  =>
        val rest = lines.drop(i + 1).map(_.trim).filter(_.nonEmpty)
        if rest.forall(l => l.startsWith("-") || l.startsWith("*") || l.startsWith("[[") || l.startsWith("1."))
        then Article.Body(lines.take(i).mkString("\n").stripTrailing)
        else Article.Body((body).value)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.SilentCompare, "pending G53 (no comparison of unrelated types)")
  private def articleParts(v: Article.View, show: List[Article.Part]): String =
    val a = v.article
    val out = List.newBuilder[String]

    if show.contains("summary") then
      a.summary.map(_.value.trim).filter(_.nonEmpty).foreach(s => out += s"_${(stripMd(s)).take(200)}_")

    if show.contains("body") then
      val body = a.body.value.trim
      val line =
        metaLine(a.kind.value, a.regions.map(_.value), Year.period(a.periodStart, a.periodEnd), a.tags.map(_.value))
      val head =
        if Articles.heading(Article.Body(body)).isEmpty then
          s"# ${a.title}\n\n" + (if line.isEmpty then "" else s"$line\n\n") + body
        else if line.isEmpty then body
        else
          body.indexOf('\n') match
            case -1 => s"$body\n\n$line"
            case i  => s"${body.take(i)}\n\n$line${body.drop(i)}"
      out += head

    if show.contains("coverage") then v.coverage.foreach(c => out += Article.Coverage.sections(c).value)

    if show.contains("backlinks") then
      val bl = backlinks(v.backlinks, truncate = (true))
      if (bl).nonEmpty then out += (bl)

    if show.contains("cited") then
      val sc = sourcesCited(v.sourcesCited, truncate = (true))
      if (sc).nonEmpty then out += (sc)

    if (show.contains("body") || show.contains("summary")) && v.formatting.nonEmpty then
      val shown = v.formatting.filter(i => show.contains(if i.field == FormatField.Summary then "summary" else "body"))
      if shown.nonEmpty then out += FormatMarkdown.notice(shown).trim

    val result = out.result()
    if result.isEmpty then (s"# ${a.title}\n\n_(no parts selected)_")
    else (result.mkString("\n\n"))

  private def stripMd(s: String): String =
    ((s)
      .replaceAll("""\[\[([^\]|]+)\|([^\]]+)\]\]""", "$2")
      .replaceAll("""\[\[([^\]]+)\]\]""", "$1")
      .replaceAll("""\[([^\]]+)\]\([^)]+\)""", "$1")
      .replaceAll("""[*_]{1,2}""", "")
      .replaceAll("""`""", ""))

  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def backlinks(rows: List[Backlink], truncate: Boolean = (false)): String =
    if rows.isEmpty then ("")
    else
      val items = rows.map { b =>
        val title =
          b.title.map(t => if (truncate) == (true) then s" — ${stripMd(t.value).take(200)}" else s" — $t").getOrElse("")
        s"- [[${b.id}]]$title"
      }
      (s"\n\n## Backlinks (${rows.size})\n\n${items.mkString("\n")}")

  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def sourcesCited(rows: List[CitedSource], truncate: Boolean = (false)): String =
    if rows.isEmpty then ("")
    else
      val items = rows.map { s =>
        val t = s.title
          .map(_.value)
          .map(v => if (truncate) == (true) then stripMd(v).take(200) else v)
          .getOrElse("_(not in the corpus)_")
        val auth = Source.Ref.shortAuthors(s.authors).value
        val yr = s.year.map(_.value.toInt).map(_.toString).getOrElse("")
        val byline = List(auth, yr).filter(_.nonEmpty).mkString(" ")
        val tail = List(byline, s"_${t}_", s.status.map(st => s"**${st.wire}**").getOrElse(""))
          .filter(_.nonEmpty)
          .mkString(" · ")
        s"- [[${s.citekey}]] — $tail"
      }
      (s"\n\n## Sources cited (${rows.size})\n\n${items.mkString("\n")}")

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def versions(slug: Article.Slug, vs: List[Article.Version]): String =
    if vs.isEmpty then (s"_No versions recorded for **$slug**._")
    else
      val items = vs.map { v =>
        val sum = v.summary.map(s => s" — ${s.value.take(120)}").getOrElse("")
        s"- **${v.id}** · ${v.updatedAt} · ${Plural.count(Plural.CountN(v.wordCount.value.toLong), Plural.CountSingular("word"))}$sum"
      }
      (s"### ${vs.size} version(s) of $slug\n\n${items.mkString("\n")}")

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def version(v: Article.VersionFull): String =
    val sum = v.summary.map(s => s"\n\n_${s.value.take(200)}_").getOrElse("")
    (s"### Version ${v.id} of ${v.slug}\n\n_saved ${v.updatedAt}, archived ${v.createdAt}_$sum\n\n---\n\n${v.body}")

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def list(r: Article.List): String =
    OffsetPage
      .markdown(r.page, ("article(s)"), ("_No matching articles._"), r.articles) { a =>
        val tagsStr = a.tags.mkString(", ")
        val meta = List(a.kind.value, Year.period(a.periodStart, a.periodEnd), tagsStr)
          .map(_.trim)
          .filter(_.nonEmpty)
          .mkString(" · ")
        val sum = a.summary.map(s => s"\n  ${s.value.trim}").getOrElse("")
        (s"- **${a.slug}** — ${a.title}\n  _${meta}_$sum")
      }
      .value
