package com.histmersion.web

import com.histmersion.api.automation.Queue

/** What an acquisition item names, read best-effort from its text so that "Upload…" and "Link existing source…" start
  * from a filled-in form rather than a blank one. Everything it returns is a SUGGESTION the reader edits before
  * anything is sent.
  *
  * The items are written in two shapes:
  *   - `Author, Title (Publisher, Year; DOI, …) — why it is wanted` — the usual one, with the authors as `Surname, I.`,
  *     `First Last`, or several joined by `&`, and `(ed.)`/`(eds.)`;
  *   - `Author, I. Year. 'Title', Venue …` — an author-year reference.
  *
  * The DOI is taken only from the slot right after the `;` of the publication parenthesis (or from that parenthesis
  * when it has no `;`): an item may name a DOI of something ELSE further on — a review it was found through — and that
  * one must not become the source's DOI.
  */
object QueueItemRef:



  final case class Ref(authors: List[Ref.Authors], title: Option[Ref.Title], year: Option[Ref.Year], doi: Option[Ref.Doi])

  object Ref:
    type Authors = Authors.T
    object Authors extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Title = Title.T
    object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Year = Year.T
    object Year extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Doi = Doi.T
    object Doi extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val AuthorYear = """^(.+?)\s+((?:1[5-9]|20)\d\d)[a-z]?\.\s+['‘"“](.+?)['’"”][,.]\s*(.*)$""".r
  private val Paren = """\(([^()]*)\)""".r
  private val EdMark = """(?i)\s*\((?:eds?|hrsg)\.?\)\s*"""
  // no look-behind: Scala.js targets a RegExp without it
  private val Year = """(?:^|[^\d/.])((?:1[5-9]|20)\d\d)(?![\d/])""".r
  private val DoiLead = """(?i)^(?:doi|jstor)[:\s]+"""
  // plain classes: Scala.js targets a RegExp without Unicode property escapes
  private val Initials = """^(?:[A-ZÀ-ÖØ-Þ]\.\s?)+$"""
  private val Stop = Set(
    "the",
    "a",
    "an",
    "of",
    "on",
    "in",
    "and",
    "und",
    "der",
    "die",
    "das",
    "le",
    "la",
    "les",
    "for",
    "to",
    "with",
    "from",
    "at",
    "by"
  )

  /** Pure: the parts of an item's first line, as far as they can be told. */
  def parse(text: com.histmersion.api.Api.Search.Text): Ref =
    val line = text.linesIterator.nextOption().getOrElse("").replace("**", "").trim
    line match
      case AuthorYear(who, year, title, venue) =>
        Ref(authorsOf(who.split(""",\s+""").toList)._1, clean(title), year.toIntOption, firstDoi(venue))
      case _ =>
        val cited = line.take(topLevelDash(line))
        val paren = Paren.findAllMatchIn(cited).find(m => !m.group(0).matches(EdMark))
        val head = paren.fold(cited)(m => cited.substring(0, m.start)).trim.stripSuffix(",").trim
        val (pub, slot) = paren
          .map(_.group(1))
          .map { in =>
            in.indexOf(';') match
              case -1 => (in, in)
              case i  => (in.take(i), in.drop(i + 1))
          }
          .getOrElse(("", ""))
        val parts = head.split(""",\s+""").toList.filter(_.nonEmpty)
        val (authors, rest) = if parts.size < 2 then (Nil, parts) else authorsOf(parts)
        Ref(authors, clean(rest.mkString(", ")), Year.findFirstMatchIn(pub).map(_.group(1).toInt), leadingDoi(slot))

  /** Where the citation ends: the first ` — ` that is not inside a parenthesis. */
  private def topLevelDash(s: com.histmersion.api.Api.Search.Text): Int =
    var depth = 0
    var i = 0
    var cut = s.length
    while i < s.length && cut == s.length do
      s(i) match
        case '(' => depth += 1
        case ')' => depth = math.max(0, depth - 1)
        case _   => if depth == 0 && s.startsWith(" — ", i) then cut = i
      i += 1
    cut

  /** The leading comma-parts that are names, and what is left (the title). The first part is always a name; a following
    * part joins it when it is the initials of a `Surname, I.` or another group of names joined by `&`.
    */
  private def authorsOf(parts: List[com.histmersion.web.QueueItemRef.Ref.Authors]): (List[com.histmersion.web.QueueItemRef.Ref.Authors], List[com.histmersion.web.QueueItemRef.Ref.Authors]) =
    def names(group: com.histmersion.web.QueueItemRef.Ref.Authors): List[com.histmersion.web.QueueItemRef.Ref.Authors] =
      group.replaceAll(EdMark, " ").split("""\s+&\s+""").toList.map(_.trim).filter(_.nonEmpty)
    def isGroup(p: com.histmersion.web.QueueItemRef.Ref.Authors): Boolean =
      p.contains(" & ") && !p.contains(":") &&
        p.replaceAll(EdMark, " ").split("""\s+""").forall(w => w == "&" || w.headOption.exists(_.isUpper))
    parts match
      case Nil           => (Nil, Nil)
      case first :: more =>
        var acc = names(first).toVector
        var left = more
        var go = true
        while go && left.nonEmpty do
          val p = left.head.trim
          if p.matches(Initials) && acc.nonEmpty then
            acc = acc.init :+ s"$p ${acc.last}"
            left = left.tail
          else if isGroup(p) then
            acc = acc ++ names(p)
            left = left.tail
          else go = false
        (acc.toList, left)

  private def clean(s: com.histmersion.api.Api.Search.Text): Option[com.histmersion.api.UiLabels.Text] =
    Some(s.trim.stripPrefix("*").stripSuffix("*").trim).filter(_.nonEmpty)

  /** The DOI that OPENS a slot (`10.…`, `doi 10.…`, `JSTOR 10.…`), else none. */
  private def leadingDoi(slot: com.histmersion.api.Api.Search.Text): Option[com.histmersion.web.QueueItemRef.Ref.Doi] =
    QueueView.segments(slot.trim.replaceFirst(DoiLead, "")).headOption.collect { case QueueView.Seg.Doi(d) => d }

  private def firstDoi(s: com.histmersion.api.Api.Search.Text): Option[com.histmersion.web.QueueItemRef.Ref.Doi] =
    QueueView.segments(s).collectFirst { case QueueView.Seg.Doi(d) => d }

  /** A citekey in the corpus's `surnameYEAR_word` shape, through [[UploadView.suggestCitekey]] so it is always a valid
    * one.
    */
  def citekey(r: Ref): Option[com.histmersion.web.Preview.Target.Source.Citekey] =
    val surname = r.authors.headOption.flatMap(_.split("""\s+""").lastOption).map(_.replace(".", ""))
    val word = r.title.flatMap(t => words(t).find(w => w.length > 2 && !Stop(w.toLowerCase)))
    val raw = List(surname.map(_ + r.year.fold("")(_.toString)), word).flatten.mkString(" ")
    Option.when(raw.nonEmpty)(raw).flatMap(UploadView.suggestCitekey)

  /** The runs of letters in `s`. */
  private def words(s: com.histmersion.api.Api.Search.Text): List[com.histmersion.api.UiLabels.Text] =
    s.foldLeft(List(""))((acc, c) => if c.isLetter then (acc.head + c) :: acc.tail else "" :: acc)
      .reverse
      .filter(_.nonEmpty)

  /** The upload form filled in from an item, linked to it so the accepted upload ticks it off. */
  def uploadDraft(item: Queue.QueueItem): UploadView.Draft =
    val r = parse(item.text)
    UploadView.Draft(
      citekey = citekey(r).getOrElse(""),
      title = r.title.getOrElse(""),
      authors = r.authors,
      year = r.year.fold("")(_.toString),
      doi = r.doi.getOrElse(""),
      forItem = Some(item)
    )

  /** What to search the corpus for when linking an item to a source already there: its title, else its first author,
    * else the start of its text.
    */
  def searchQuery(item: Queue.QueueItem): com.histmersion.api.Api.Search.Text =
    val r = parse(item.text)
    r.title
      .map(_.split("""[:.]""").head.trim)
      .filter(_.nonEmpty)
      .orElse(r.authors.headOption)
      .getOrElse(item.text.linesIterator.nextOption().getOrElse("").take(60).trim)
