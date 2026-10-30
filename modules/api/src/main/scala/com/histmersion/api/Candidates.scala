package com.histmersion.api

import io.circe.{ACursor, Json}

/** Deterministic new-source search (#3761, the design of #3645): what each research goal WATCHES — its saved literature
  * queries on each scholarly index, the works citing its sources, its sources' authors — each with a date WATERMARK
  * (the day it was last read: the next read asks only for what is newer), and the CANDIDATES found, deduplicated by DOI
  * across indexes and against the corpus and the acquisition list, stored on the server with a state. No model reads
  * anything here: a person or an agent only filters the unseen candidates (`histmersion candidates`, MCP `candidates`),
  * and a goal's scheduled acquisition sweep starts its ranking model only when there are new ones.
  */
object Candidates:

  /** What a watch reads: a saved `query`, the works `citing` one of the goal's sources (a citekey), the newer works of
    * an `author` of one (an OpenAlex author id).
    */
  enum WatchKind(val wire: String):
    case Query extends WatchKind("query")
    case Citing extends WatchKind("citing")
    case Author extends WatchKind("author")
  object WatchKind:
    def parse(s: String): Option[WatchKind] = values.find(_.wire == s.trim.toLowerCase)

  /** A candidate's state: `new` (nobody looked yet), `seen` (shown to a ranking or a person, not taken), `queued` (put
    * on the acquisition list or ingested), `dismissed` (not for this goal).
    */
  enum State(val wire: String):
    case New extends State("new")
    case Seen extends State("seen")
    case Queued extends State("queued")
    case Dismissed extends State("dismissed")
  object State:
    def parse(s: String): Option[State] = values.find(_.wire == s.trim.toLowerCase)

  /** The indexes searched for a goal's queries (the citing and author alerts read OpenAlex). */
  val Indexes: List[String] = List("openalex", "crossref", "europepmc", "arxiv", "core")

  /** Pure: a work's DOI as a key — lower case, without its resolver prefix; an arXiv preprint without one gets arXiv's
    * DataCite DOI (`10.48550/arxiv.<id>`), so the same preprint from two indexes is one.
    */
  def keyOf(w: WorkRef): Option[String] =
    w.doi
      .flatMap(Acquire.normalizeDoi)
      .orElse(w.arxivId.map(a => s"10.48550/arxiv.${a.trim.toLowerCase.replaceAll("v\\d+$", "")}"))

  /** Pure: the day a watch reads from — its watermark, or (never read) `lookbackDays` before `today`. */
  def since(watermark: Option[String], today: java.time.LocalDate, lookbackDays: Int): java.time.LocalDate =
    watermark
      .flatMap(w => scala.util.Try(java.time.LocalDate.parse(w.take(10))).toOption)
      .getOrElse(today.minusDays(math.max(0, lookbackDays).toLong))

  /** One work found, with what found it (`openalex query "…"`, `openalex citing [[ck]]`, …). */
  final case class Found(key: String, work: WorkRef, by: String)

  /** Pure: the works of one sweep, each once (by key), with every watch that found it; works without a DOI or arXiv id,
    * retracted ones, and those in `known` (the corpus, the acquisition list) are left out and counted by reason.
    */
  def dedupe(
      found: List[(String, WorkRef)],
      known: Set[String]
  ): (List[(String, WorkRef, List[String])], Map[String, Int]) =
    val kept = scala.collection.mutable.LinkedHashMap.empty[String, (WorkRef, List[String])]
    val skipped = scala.collection.mutable.Map.empty[String, Int].withDefaultValue(0)
    found.foreach { (by, w) =>
      keyOf(w) match
        case None                         => skipped("no DOI") += 1
        case Some(_) if w.isRetracted     => skipped("retracted") += 1
        case Some(k) if known.contains(k) => skipped("in the corpus or queued") += 1
        case Some(k)                      =>
          kept.get(k) match
            case Some((first, bys)) => kept(k) = (richer(first, w), if bys.contains(by) then bys else bys :+ by)
            case None               => kept(k) = (w, List(by))
    }
    (kept.toList.map { case (k, (w, bys)) => (k, w, bys) }, skipped.toMap)

  /** Pure: the fuller record of two of one work (each field from the first that has it). */
  def richer(a: WorkRef, b: WorkRef): WorkRef =
    a.copy(
      title = a.title.orElse(b.title),
      year = a.year.orElse(b.year),
      authors = if a.authors.nonEmpty then a.authors else b.authors,
      venue = a.venue.orElse(b.venue),
      isOa = a.isOa || b.isOa,
      abstractText = a.abstractText.orElse(b.abstractText),
      arxivId = a.arxivId.orElse(b.arxivId),
      openalexId = a.openalexId.orElse(b.openalexId)
    )

  // ── parsing the indexes' answers (pure) ──────────────────────────────────────────────────────────

  private def str(c: ACursor, k: String): Option[String] = c.get[String](k).toOption.map(_.trim).filter(_.nonEmpty)

  /** Europe PMC's `search` answer (`resultType=core`): each result's DOI, title, authors, year, journal, open access
    * and abstract.
    */
  def europepmcWorks(json: Json): List[WorkRef] =
    json.hcursor.downField("resultList").downField("result").values.toList.flatten.map { r =>
      val c = r.hcursor
      WorkRef(
        doi = str(c, "doi"),
        title = str(c, "title").map(_.stripSuffix(".")),
        year = str(c, "pubYear").flatMap(_.toIntOption),
        authors = str(c, "authorString").toList.flatMap(_.stripSuffix(".").split(",").map(_.trim).filter(_.nonEmpty)),
        venue =
          str(c, "journalTitle").orElse(c.downField("journalInfo").downField("journal").get[String]("title").toOption),
        isOa = str(c, "isOpenAccess").contains("Y"),
        abstractText = str(c, "abstractText").map(_.replaceAll("<[^>]+>", ""))
      )
    }

  /** CORE's `search/works` answer: each work's DOI, title, authors, year, publisher, abstract (open access by CORE's
    * nature when it holds a full text).
    */
  def coreWorks(json: Json): List[WorkRef] =
    json.hcursor.downField("results").values.toList.flatten.map { r =>
      val c = r.hcursor
      WorkRef(
        doi = str(c, "doi"),
        title = str(c, "title"),
        year = c.get[Int]("yearPublished").toOption,
        authors = c.downField("authors").values.toList.flatten.flatMap(a => str(a.hcursor, "name")),
        venue = str(c, "publisher"),
        isOa = str(c, "downloadUrl").isDefined,
        abstractText = str(c, "abstract"),
        arxivId = str(c, "arxivId")
      )
    }

  /** arXiv's Atom answer: each entry's id (without its version), title, authors, year, DOI, abstract — read without an
    * XML library (the feed's shape is fixed and small).
    */
  def arxivWorks(atom: String): List[WorkRef] =
    def tag(s: String, t: String): Option[String] =
      s"(?s)<$t[^>]*>(.*?)</$t>".r.findFirstMatchIn(s).map(_.group(1).replaceAll("\\s+", " ").trim).filter(_.nonEmpty)
    def unescape(s: String) =
      s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")
    "(?s)<entry>(.*?)</entry>".r.findAllMatchIn(atom).map(_.group(1)).toList.map { e =>
      val id = tag(e, "id").map(_.replaceFirst("^https?://arxiv.org/abs/", "").replaceAll("v\\d+$", ""))
      WorkRef(
        arxivId = id,
        title = tag(e, "title").map(unescape),
        year = tag(e, "published").flatMap(_.take(4).toIntOption),
        authors = "(?s)<author>\\s*<name>(.*?)</name>".r.findAllMatchIn(e).map(m => unescape(m.group(1).trim)).toList,
        doi = tag(e, "arxiv:doi"),
        venue = Some("arXiv"),
        isOa = true,
        abstractText = tag(e, "summary").map(unescape)
      )
    }

  /** OpenAlex's work: its authors' ids (`A…`), in byline order. */
  def openalexAuthorIds(json: Json): List[String] =
    json.hcursor
      .downField("authorships")
      .values
      .toList
      .flatten
      .flatMap(a => a.hcursor.downField("author").get[String]("id").toOption)
      .map(_.split("/").last)
      .filter(_.startsWith("A"))
