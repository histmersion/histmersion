package com.histmersion.api

/** Markdown rendering for the article surfaces — the wire format for MCP, the CLI and (through flexmark) the HTML page.
  * Kept beside the model rather than in [[Markdown]] so stream 2.7 adds no lines to a file three other streams are
  * editing.
  */
object ArticleMarkdown:

  val AllParts = List("body", "summary", "coverage", "cited", "backlinks")

  /** One article, exactly as `/article/{slug}` renders it: the body, then its backlinks, then the sources it cites.
    * Wikilinks are left as `[[…]]` — the HTML layer resolves them, the terminal shows them as the agent wrote them.
    *
    * `parts` filters which sections appear. Empty = the full rendering (body + the generated coverage sections +
    * backlinks + cited). Valid parts: body, summary, coverage, cited, backlinks. Teasers in cited/backlinks are
    * truncated to 200 chars with markdown stripped.
    */
  def article(v: ArticleView, parts: List[String] = Nil): String =
    if parts.isEmpty then articleFull(v)
    else articleParts(v, parts.filter(AllParts.contains))

  private def articleFull(v: ArticleView): String =
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
    // Item 23: several compiled articles end with their own hand-written "## Sources" list. Rendered
    // under the generated footer that is the same list twice, with different formatting. The
    // generated one wins — it carries the year and the status and it cannot go stale.
    val trimmed = if footer.isEmpty then head else stripSourcesSection(head)
    trimmed + coverage(v) + backlinks(v.backlinks) + footer + FormatMarkdown.notice(v.formatting)

  /** The sections code generates from the article's filter (#958), after the body; nothing when it records none.
    */
  private def coverage(v: ArticleView): String = v.coverage.fold("")(c => "\n\n" + ArticleCoverage.sections(c))

  /** The line under an article's title: kind · regions · period · tags. Regions and tags are web UI links with readable
    * labels (`greek religion` → `/#tag=greek_religion`), as on a source page.
    */
  private[api] def metaLine(kind: String, regions: List[String], period: String, tags: List[String]): String =
    val parts = List(Label.display(kind.trim), chipLinksList(regions, "place"), period.trim, chipLinksList(tags, "tag"))
      .filter(_.nonEmpty)
    if parts.isEmpty then "" else s"_${parts.mkString(" · ")}_"

  /** Chip links from a proper list (replaces the comma-split variant in Markdown). */
  private def chipLinksList(values: List[String], hashKey: String): String =
    values
      .filter(_.nonEmpty)
      .map(v => s"[${Label.display(v).replace("[", "\\[").replace("]", "\\]")}](/#$hashKey=${Markdown.enc(v)})")
      .mkString(", ")

  /** A `## Sources` / `## Sources cited` / `## References` section at the END of a body, removed. Only a TRAILING one:
    * a section in the middle of an article is prose about sources, not a list of them. Pure, so "which heading counts"
    * is a test rather than a guess.
    */
  def stripSourcesSection(body: String): String =
    val heading = """^#{1,6}\s*(sources\b.*|references|bibliography|works cited)\s*$""".r
    val lines = body.linesIterator.toList
    lines.lastIndexWhere(l => heading.matches(l.trim.toLowerCase)) match
      case -1 => body
      case i  =>
        // trailing only: nothing but list items, links and blank lines may follow it
        val rest = lines.drop(i + 1).map(_.trim).filter(_.nonEmpty)
        if rest.forall(l => l.startsWith("-") || l.startsWith("*") || l.startsWith("[[") || l.startsWith("1."))
        then lines.take(i).mkString("\n").stripTrailing
        else body

  private def articleParts(v: ArticleView, show: List[String]): String =
    val a = v.article
    val out = List.newBuilder[String]

    if show.contains("summary") then
      a.summary.map(_.trim).filter(_.nonEmpty).foreach(s => out += s"_${stripMd(s).take(200)}_")

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

    if show.contains("coverage") then v.coverage.foreach(c => out += ArticleCoverage.sections(c))

    if show.contains("backlinks") then
      val bl = backlinks(v.backlinks, truncate = true)
      if bl.nonEmpty then out += bl

    if show.contains("cited") then
      val sc = sourcesCited(v.sourcesCited, truncate = true)
      if sc.nonEmpty then out += sc

    // the formatting problems travel with the text they are about
    if (show.contains("body") || show.contains("summary")) && v.formatting.nonEmpty then
      val shown = v.formatting.filter(i => show.contains(if i.field == FormatField.Summary then "summary" else "body"))
      if shown.nonEmpty then out += FormatMarkdown.notice(shown).trim

    val result = out.result()
    if result.isEmpty then s"# ${a.title}\n\n_(no parts selected)_"
    else result.mkString("\n\n")

  /** Strip markdown formatting for teaser display: bold, italic, links, wikilinks. */
  private def stripMd(s: String): String =
    s.replaceAll("""\[\[([^\]|]+)\|([^\]]+)\]\]""", "$2")
      .replaceAll("""\[\[([^\]]+)\]\]""", "$1")
      .replaceAll("""\[([^\]]+)\]\([^)]+\)""", "$1")
      .replaceAll("""[*_]{1,2}""", "")
      .replaceAll("""`""", "")

  def backlinks(rows: List[Backlink], truncate: Boolean = false): String =
    if rows.isEmpty then ""
    else
      val items = rows.map { b =>
        val title = b.title.map(t => if truncate then s" — ${stripMd(t).take(200)}" else s" — $t").getOrElse("")
        s"- [[${b.id}]]$title"
      }
      s"\n\n## Backlinks (${rows.size})\n\n${items.mkString("\n")}"

  /** `authors year · title · status` per cited source; an unknown citekey is shown as such rather than silently
    * dropped, because that is a dangling citation the agent should fix.
    */
  def sourcesCited(rows: List[CitedSource], truncate: Boolean = false): String =
    if rows.isEmpty then ""
    else
      val items = rows.map { s =>
        val t = s.title
          .map(v => if truncate then stripMd(v).take(200) else v)
          .getOrElse("_(not in the corpus)_")
        val auth = SourceRef.shortAuthors(s.authors)
        val yr = s.year.map(_.toString).getOrElse("")
        val byline = List(auth, yr).filter(_.nonEmpty).mkString(" ")
        val tail = List(byline, s"_${t}_", s.status.map(st => s"**${st.wire}**").getOrElse(""))
          .filter(_.nonEmpty)
          .mkString(" · ")
        s"- [[${s.citekey}]] — $tail"
      }
      s"\n\n## Sources cited (${rows.size})\n\n${items.mkString("\n")}"

  /** The version history of one article, newest first. */
  def versions(slug: String, vs: List[ArticleVersion]): String =
    if vs.isEmpty then s"_No versions recorded for **$slug**._"
    else
      val items = vs.map { v =>
        val sum = v.summary.map(s => s" — ${s.take(120)}").getOrElse("")
        s"- **${v.id}** · ${v.updatedAt} · ${Plural.count(v.wordCount, "word")}$sum"
      }
      s"### ${vs.size} version(s) of $slug\n\n${items.mkString("\n")}"

  /** One historical version's full body. */
  def version(v: ArticleVersionFull): String =
    val sum = v.summary.map(s => s"\n\n_${s.take(200)}_").getOrElse("")
    s"### Version ${v.id} of ${v.slug}\n\n_saved ${v.updatedAt}, archived ${v.createdAt}_$sum\n\n---\n\n${v.body}"

  /** Headed and footed like every offset-paged list ([[OffsetPage.markdown]]): a cut list says where the next page
    * starts.
    */
  def list(r: ArticleList): String =
    OffsetPage.markdown(r.page, "article(s)", "_No matching articles._", r.articles) { a =>
      val tagsStr = a.tags.mkString(", ")
      val meta = List(a.kind, YearFmt.period(a.periodStart, a.periodEnd), tagsStr)
        .map(_.trim)
        .filter(_.nonEmpty)
        .mkString(" · ")
      val sum = a.summary.map(s => s"\n  ${s.trim}").getOrElse("")
      s"- **${a.slug}** — ${a.title}\n  _${meta}_$sum"
    }
