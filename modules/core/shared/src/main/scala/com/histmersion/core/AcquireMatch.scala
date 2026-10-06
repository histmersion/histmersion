package com.histmersion.core

/** Which open acquisition items a source that just came in fulfils (#970).
  *
  * An import used to tick its item off only when the caller named it (`--acquire-item`). Every
  * successful ingest/import job now also looks at the open items itself, and the two kinds of
  * evidence are treated very differently, because real data has near-misses of both kinds:
  *
  *   - the source's DOI written in the item ("(Brill, 2021; 10.1163/9789004441385)") is the SAME work:
  *     the item is CLOSED, `ingested as [[citekey]]`;
  *   - a similar title (plus the first author's surname) is only PROPOSED, with its score, and never
  *     closes anything. A preprint and its published version share a title and have different DOIs;
  *     an article and the author's later book often share most title words. Both are listed for a
  *     person to decide, and an item that names a DIFFERENT DOI says so.
  *
  * Pure: the job (server) supplies the items and the source, and writes what this decides. */
object AcquireMatch:

  /** What the job does about one item. */
  enum Decision:
    /** The item names the source's DOI: it is closed. */
    case Close(item: Queue.QueueItem)
    /** The item's text resembles the source: listed, never closed. `otherDoi` = the item names a DOI,
      * and it is not the source's (a preprint vs its published version, say). */
    case Propose(item: Queue.QueueItem, score: Double, otherDoi: Option[String])

  private val DoiIn = """(?i)10\.\d{4,9}/\S+""".r

  /** The trailing characters a DOI written in prose drags along: sentence and list punctuation,
    * quotes, and a closing bracket that is not part of the DOI itself. */
  private val Trailing = ".,;:'\"!?>]}|*_`"

  /** A DOI as a key: lower case, no resolver prefix or `doi:` scheme, no trailing punctuation, and a
    * closing parenthesis kept only when the DOI opened one (`10.1002/(SICI)1097-…` keeps its pair). */
  def doiKey(raw: String): Option[String] =
    val s = Option(raw).getOrElse("").trim
      .replaceFirst("(?i)^https?://(dx\\.)?doi\\.org/", "")
      .replaceFirst("(?i)^doi:\\s*", "")
    DoiIn.findFirstIn(s).map(trim).map(_.toLowerCase).filter(_.contains('/'))

  @annotation.tailrec
  private def trim(d: String): String =
    if d.isEmpty then d
    else
      val last = d.last
      if Trailing.contains(last) then trim(d.dropRight(1))
      else if last == ')' && d.count(_ == ')') > d.count(_ == '(') then trim(d.dropRight(1))
      else d

  /** Every DOI written anywhere in `text`, as keys. */
  def doisIn(text: String): Set[String] =
    DoiIn.findAllIn(Option(text).getOrElse("")).flatMap(doiKey).toSet

  /** Words too common to say anything about which work a title names. */
  private val Stop = Set("the", "and", "for", "from", "with", "into", "upon", "about", "between", "its", "their",
    "der", "die", "das", "und", "den", "dem", "von", "zur", "zum", "les", "des", "une", "aux", "del",
    "della", "dei", "las", "los", "new", "study", "studies", "book", "paper", "edition", "vol", "volume")

  /** The distinctive words of a title or item text: accents folded, lower case, three letters or more
    * (or a number, like a year or a volume), stop words dropped. */
  def tokens(text: String): Set[String] =
    LatinFold.fold(Option(text).getOrElse("")).toLowerCase
      .split("[^a-z0-9]+").iterator
      .filter(w => (w.length >= 3 || w.forall(_.isDigit) && w.nonEmpty) && !Stop.contains(w))
      .toSet

  /** The first author's surname, folded: the part before the comma of `Last, First`, else the last word. */
  def surname(authors: List[String]): Option[String] =
    authors.map(_.trim).find(_.nonEmpty).map { a =>
      val last = if a.contains(",") then a.takeWhile(_ != ',') else a.split("\\s+").lastOption.getOrElse(a)
      LatinFold.fold(last).toLowerCase.replaceAll("[^a-z]", "")
    }.filter(_.length >= 2)

  /** How much `itemText` looks like a request for the work `title` by `authors`, 0…1: the share of the
    * title's distinctive words the item contains (three quarters of the weight), and whether it names
    * the first author's surname (the rest). Without authors the title words are the whole score. Two
    * decimals, so a listing is stable. */
  def score(itemText: String, title: String, authors: List[String]): Double =
    val want = tokens(title)
    if want.isEmpty then 0.0
    else
      val have  = tokens(itemText)
      val words = want.count(have.contains).toDouble / want.size
      val raw = surname(authors) match
        case Some(s) => 0.75 * words + (if have.contains(s) then 0.25 else 0.0)
        case None    => words
      math.round(raw * 100) / 100.0

  /** The decision for every open ACQUISITION item, given the source's DOI, title and authors:
    * DOI-exact items are closed; other items scoring at least `proposeAt` are proposed, best first.
    * Items already done, notes and other lists are never touched. */
  def decide(items: List[Queue.QueueItem], doi: Option[String], title: String, authors: List[String],
      proposeAt: Double): List[Decision] =
    val key  = doi.flatMap(doiKey)
    val open = items.filter(i => i.list == Queue.Kind.Acquire.label && i.status == Queue.Open)
    val (closes, rest) = open.partition(i => key.exists(doisIn(i.text + " " + i.reason.getOrElse("")).contains))
    val proposals = rest.flatMap { i =>
      val s = score(i.text, title, authors)
      Option.when(s >= proposeAt) {
        val named = doisIn(i.text).toList.sorted
        Decision.Propose(i, s, named.find(d => !key.contains(d)))
      }
    }.sortBy { case Decision.Propose(i, s, _) => (-s, i.id); case _ => (0.0, 0) }
    closes.map(Decision.Close(_)) ++ proposals

  /** One line per decision, for a job's progress and an import's output. `closed` maps an item to the
    * outcome of the write that closed it (`Left(reason)` when it could not be). */
  def describe(d: Decision, closed: Int => Either[String, Unit] = _ => Right(())): String = d match
    case Decision.Close(i) =>
      closed(i.id).fold(e => s"acquisition item #${i.id} matches by DOI but was NOT ticked off: $e",
        _ => s"ticked off acquisition item #${i.id} (same DOI)")
    case Decision.Propose(i, s, other) =>
      val whole = i.text.linesIterator.mkString(" ")
      val text  = if whole.length > 100 then whole.take(99) + "…" else whole
      val doiNote = other.fold("")(d => s"; it names another DOI, $d")
      s"possible acquisition item #${i.id} (similarity ${decimal(s)}$doiNote): $text — tick it off with " +
        s"`histmersion queue done ${i.id}` if it is this work"

  /** `0.83`, with a dot whatever the default locale (`f"%.2f"` writes `0,83` under en_PL). */
  def decimal(x: Double): String =
    val cents = math.round(x * 100)
    f"${cents / 100}.${cents % 100}%02d"
end AcquireMatch
