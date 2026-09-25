package com.histmersion.core

/** The PURE rules of the acquisition & assessment toolset (plan §5.2 stream 2.6) — everything the
  * Python tools decided in code rather than asked an API: how a citekey is spelled, what counts as a
  * DOI or an arXiv id, when a paper is retracted / corrected / low-impact, and what an edge from a
  * citing paper should be called.
  *
  * No I/O, no config lookups, no exceptions: every function here is a transformation, so all of it is
  * unit-testable without a network or a database. The network lives behind traits in the server
  * module; the JSON shapes upstreams return are parsed by [[AcquireParse]].
  */
object Acquire:

  // ── identifiers ─────────────────────────────────────────────────────────────────────────────

  /** What the user typed on the command line / sent in a request body. */
  enum Ident:
    /** A DOI, normalised (lower-case, no resolver prefix). */
    case Doi(value: String)

    /** An arXiv id, with any `arXiv:` prefix and version suffix stripped. */
    case Arxiv(value: String)

    /** A citekey of a source already in the corpus. */
    case Citekey(value: String)

  private val DoiCore   = """10\.\d{4,9}/[-._;()/:A-Za-z0-9<>\[\]]+""".r
  private val ArxivNew  = """^(?:arxiv:)?(\d{4}\.\d{4,5})(?:v\d+)?$""".r
  private val ArxivOld  = """^(?:arxiv:)?([a-z-]+(?:\.[A-Z]{2})?/\d{7})(?:v\d+)?$""".r

  /** Strip the resolver prefix, the `doi:` scheme and trailing punctuation a citation dragged along;
    * DOIs are case-insensitive and we store them lower-case, as the Python tools did. */
  def normalizeDoi(raw: String): Option[String] =
    val s = Option(raw).getOrElse("").trim
      .replaceFirst("(?i)^https?://(dx\\.)?doi\\.org/", "")
      .replaceFirst("(?i)^doi:\\s*", "")
      .trim
    DoiCore.findFirstIn(s).map(_.stripSuffix(".").stripSuffix(",").stripSuffix(";")
      .stripSuffix(")").stripSuffix("}").stripSuffix("|").toLowerCase)

  /** Strip `arXiv:` and the version suffix (`v2`), keeping both the new (`2301.01234`) and the old
    * (`math.GT/0211159`) id shapes. */
  def normalizeArxiv(raw: String): Option[String] =
    Option(raw).map(_.trim).filter(_.nonEmpty).flatMap { s =>
      s.toLowerCase match
        case ArxivNew(id) => Some(id)
        case _ =>
          s match
            case ArxivOld(id) => Some(id)
            case _            => None
    }

  /** Read a `<doi|arxiv:id|citekey>` argument. A DOI wins, then an arXiv id, then anything that looks
    * like one of our citekeys (`surnameYEAR_word`); nothing else is accepted. */
  def parseIdent(raw: String): Either[String, Ident] =
    val s = Option(raw).getOrElse("").trim
    if s.isEmpty then Left("empty identifier — give a DOI, arxiv:<id>, or a citekey")
    else
      normalizeDoi(s).map(Ident.Doi.apply)
        .orElse(normalizeArxiv(s).map(Ident.Arxiv.apply))
        .orElse(Option.when(isCitekey(s))(Ident.Citekey(s)))
        .toRight(s"'$s' is neither a DOI, an arXiv id, nor a citekey")

  /** Could this be one of our citekeys? Deliberately PERMISSIVE — the corpus contains keys the
    * generator would not produce today (hand-written ones, chapter keys, older conventions), so the
    * store, not a regular expression, decides whether a citekey exists. All this rejects is input that
    * cannot be an identifier at all: empty, containing whitespace, or containing the `/` that would
    * have made it a DOI. */
  def isCitekey(s: String): Boolean =
    s.nonEmpty && s.head.isLetter && !s.exists(_.isWhitespace) && !s.contains("/")

  // ── citekeys ────────────────────────────────────────────────────────────────────────────────

  /** Title words that never become the citekey's topic word (the `_STOP` set of `lib.make_citekey`). */
  private val Stop = Set("the", "a", "an", "of", "on", "and", "in", "to", "for", "study", "new")

  /** `<first-author-surname><year>_<first-significant-title-word>` — the SAME convention the whole
    * corpus already uses (`drews1993_collapse`, `haak2015_massive`), so a re-ingest of an existing
    * paper lands on the existing citekey instead of a duplicate.
    *
    * The surname is taken from the first author, handling both `Robert Drews` and `Drews, Robert`;
    * a missing year becomes `nd`; the topic word is the first title word longer than three letters
    * that is not a stop word, and is omitted entirely when the title has none.
    */
  def citekey(authors: List[String], year: Option[Int], title: Option[String]): String =
    val first   = authors.map(_.trim).find(_.nonEmpty)
    val surname = first.map { a =>
      if a.contains(",") then a.split("[,\\s]+").head
      else a.split("\\s+").filter(_.nonEmpty).lastOption.getOrElse(a)
    }.map(_.toLowerCase.replaceAll("[^a-z]", "")).filter(_.nonEmpty).getOrElse("anon")
    val yr   = year.map(_.toString).getOrElse("nd")
    val word = """[A-Za-z]+""".r.findAllIn(title.getOrElse("")).map(_.toLowerCase)
      .find(w => w.length > 3 && !Stop.contains(w))
    s"$surname$yr" + word.fold("")("_" + _)

  // ── assessment verdicts ─────────────────────────────────────────────────────────────────────

  /** The verdict vocabulary (plan §5.2 row 2.6). */
  object Verdicts:
    val Retracted   = "retracted"
    val Corrected   = "corrected"
    val Current     = "current"
    val LowImpact   = "low-impact"
    val Unassessed  = "unassessed"
    val all: List[String] = List(Retracted, Corrected, Current, LowImpact, Unassessed)

  private val RetractingUpdate = Set("retraction", "withdrawal", "removal")
  private val CorrectingUpdate =
    Set("correction", "corrigendum", "erratum", "addendum", "expression_of_concern",
      "expression of concern", "partial_retraction", "partial retraction")

  private def isRetractingNature(n: String): Boolean =
    val s = n.toLowerCase
    s.contains("retraction") && !s.contains("reinstatement") || s.contains("withdrawal")

  /** The verdict for one source, from every signal we have.
    *
    *   1. a Retraction Watch retraction, an OpenAlex `is_retracted`, or a Crossref
    *      retraction/withdrawal/removal notice ⇒ `retracted`;
    *   2. any other amendment (correction, corrigendum, erratum, expression of concern) ⇒ `corrected`;
    *   3. otherwise the citation metrics decide: well cited ⇒ `current`, old and barely cited ⇒
    *      `low-impact`, and no citation data at all ⇒ `unassessed`.
    *
    * `wellCitedAt`, `lowCitedAt` and `staleBefore` come from `acquire.defaults` — no thresholds live
    * in the code.
    */
  def verdict(
      retractions: List[RetractionNotice],
      updates: List[UpdateNotice],
      openalexRetracted: Boolean,
      citedByCount: Option[Int],
      year: Option[Int],
      wellCitedAt: Int,
      lowCitedAt: Int,
      staleBefore: Int
  ): String =
    val kinds = updates.flatMap(_.kind).map(_.trim.toLowerCase)
    if openalexRetracted || retractions.exists(r => r.nature.exists(isRetractingNature)) ||
      kinds.exists(RetractingUpdate.contains)
    then Verdicts.Retracted
    else if retractions.nonEmpty || kinds.exists(CorrectingUpdate.contains) then Verdicts.Corrected
    else
      citedByCount match
        case None                                  => Verdicts.Unassessed
        case Some(c) if c >= wellCitedAt           => Verdicts.Current
        case Some(c) if c < lowCitedAt && year.exists(_ < staleBefore) => Verdicts.LowImpact
        case Some(_)                               => Verdicts.Current

  /** True when a human should look before relying on the paper. */
  def flagged(verdict: String): Boolean = verdict == Verdicts.Retracted || verdict == Verdicts.Corrected

  /** The one-line advice printed under the verdict (the `recommendation` of `assess.py`). */
  def recommendation(verdict: String, citedByCount: Option[Int]): String =
    val cited = citedByCount.fold("no citation data")(c => s"$c citation(s)")
    verdict match
      case Verdicts.Retracted =>
        "EXCLUDE — a retraction, withdrawal or removal notice exists; set status=retracted and stop citing it"
      case Verdicts.Corrected =>
        "CHECK — an amendment (correction / erratum / expression of concern) exists; read it before relying on the paper"
      case Verdicts.Current   => s"KEEP — $cited, no amendment on record"
      case Verdicts.LowImpact => s"REVIEW — old and rarely cited ($cited); keep only if it is directly on topic"
      case _                  => s"UNASSESSED — $cited; judge relevance to the topic by hand"

  /** The `status` an `--apply` writes back, or `None` to leave the stored status alone. `low-impact`
    * is a judgement about reach rather than correctness, so it never overwrites a status a human or
    * an agent set deliberately. */
  def statusFor(verdict: String): Option[SourceStatus] = verdict match
    case Verdicts.Retracted => Some(SourceStatus.Retracted)
    case Verdicts.Corrected => Some(SourceStatus.Contested)
    case Verdicts.Current   => Some(SourceStatus.Current)
    case _                  => None

  // ── follow-up classification ────────────────────────────────────────────────────────────────

  /** Edge kinds a follow-up can propose. `cites` is the neutral fallback. */
  object EdgeKinds:
    val Confirms    = "confirms"
    val Challenges  = "challenges"
    val Refutes     = "refutes"
    val Supersedes  = "supersedes"
    val Cites       = "cites"
    val all: List[String] = List(Confirms, Challenges, Refutes, Supersedes, Cites)

  // NB: several of these OVERLAP with the confirm cues ("no support for" contains "support",
  // "not replicated" contains "replicat"), which is exactly why refutation is checked first.
  private val RefutesCues = List("refut", "disprov", "contradict", "falsif", "not replicat",
    "not be replicat", "fails to replicate", "failed to reproduce", "no support for",
    "no evidence for", "cannot be sustained", "is untenable", "demonstrably wrong")
  private val SupersedeCues = List("supersed", "obsolete", "outdated", "superseded by", "has been replaced",
    "no longer tenable")
  private val ReviseCues = List("revis", "updat", "reanalys", "re-analys", "reanalyz", "larger dataset",
    "more data", "extend")
  private val ChallengeCues = List("challeng", "question", "dispute", "criticis", "criticiz", "problematic",
    "contrary to", "at odds with", "inconsisten", "however", "but see", "contra ")
  private val ConfirmCues = List("confirm", "corroborat", "consistent with", "in agreement", "support",
    "replicat", "as shown by", "in line with")

  /** Read the citing paper's intents and citation contexts and propose the edge kind to record.
    *
    * The cues are checked most-specific first, because they overlap: "no support for" contains
    * "support", and a paper that supersedes usually also challenges. An INFLUENTIAL citation whose
    * intent is `result`/`methodology`, published well after the target, that talks about revising or
    * reanalysing, is the classic superseding paper — that is the only case where the metadata alone
    * (rather than a cue word) promotes the kind.
    *
    * @param gapYears how much newer the citing paper must be to count as potentially superseding
    */
  def classify(
      intents: List[String],
      contexts: List[String],
      isInfluential: Boolean,
      citingYear: Option[Int],
      targetYear: Option[Int],
      gapYears: Int = 5
  ): String =
    val text    = contexts.mkString(" ").toLowerCase
    val strong  = intents.map(_.toLowerCase).exists(i => i == "result" || i == "methodology")
    val newer   = (citingYear, targetYear) match
      case (Some(c), Some(t)) => c - t >= gapYears
      case _                  => false
    def has(cues: List[String]) = cues.exists(text.contains)

    if has(RefutesCues) then EdgeKinds.Refutes
    else if has(SupersedeCues) then EdgeKinds.Supersedes
    else if isInfluential && strong && newer && has(ReviseCues) then EdgeKinds.Supersedes
    else if has(ChallengeCues) then EdgeKinds.Challenges
    else if has(ConfirmCues) then EdgeKinds.Confirms
    else EdgeKinds.Cites

  /** Follow-ups worth reading first: influential, then result/methodology intent, then recency —
    * the same ordering `followups.py` sorted by. */
  def rankFollowups(rows: List[FollowupRow]): List[FollowupRow] =
    rows.sortBy { r =>
      val strong = r.intents.map(_.toLowerCase).exists(i => i == "result" || i == "methodology")
      (if r.isInfluential then 0 else 1, if strong then 0 else 1, -r.year.getOrElse(0))
    }

  // ── Retraction Watch CSV ────────────────────────────────────────────────────────────────────

  /** One row of the Retraction Watch export, reduced to what we store. */
  final case class RetractionRow(doi: String, reason: Option[String], date: Option[String], nature: Option[String])

  /** Parse the Retraction Watch CSV into rows keyed by the ORIGINAL paper's DOI.
    *
    * The export is a 60 000-row RFC 4180 file whose `Reason` column is full of quoted commas and
    * embedded newlines, so it needs a real CSV reader rather than `split(",")`. Columns are located by
    * HEADER NAME (`OriginalPaperDOI`, `RetractionNature`, `Reason`, `RetractionDate`) because their
    * order has changed between exports. Rows without a usable DOI are dropped, and the LAST row for a
    * DOI wins (the export lists amendments chronologically).
    */
  def parseRetractionWatch(csv: String): List[RetractionRow] =
    Csv.parse(csv) match
      case Nil => Nil
      case header :: rows =>
        val idx = header.map(_.trim.replaceAll("^﻿", "")).zipWithIndex.toMap
        def col(r: List[String], name: String): Option[String] =
          idx.get(name).flatMap(r.lift).map(_.trim).filter(_.nonEmpty)
        rows.flatMap { r =>
          col(r, "OriginalPaperDOI").flatMap(normalizeDoi).map { doi =>
            RetractionRow(doi, col(r, "Reason"), col(r, "RetractionDate"), col(r, "RetractionNature"))
          }
        }

  /** The Retraction Watch date column is `MM/DD/YYYY 0:00`; Postgres wants `YYYY-MM-DD`. Anything
    * unparseable becomes `None` rather than a failed insert. */
  def rwDate(raw: Option[String]): Option[String] =
    raw.map(_.trim.takeWhile(_ != ' ')).flatMap { s =>
      s.split("[/-]").toList match
        case m :: d :: y :: Nil if y.length == 4 && m.forall(_.isDigit) && d.forall(_.isDigit) =>
          Some(f"${y.toInt}%04d-${m.toInt}%02d-${d.toInt}%02d")
        case y :: m :: d :: Nil if y.length == 4 && m.forall(_.isDigit) && d.forall(_.isDigit) =>
          Some(f"${y.toInt}%04d-${m.toInt}%02d-${d.toInt}%02d")
        case _ => None
    }

/** A minimal RFC 4180 reader: quoted fields, doubled quotes inside them, embedded commas and
  * newlines, and CRLF line endings. Enough for the Retraction Watch export and nothing more. */
object Csv:

  def parse(text: String): List[List[String]] =
    val rows   = List.newBuilder[List[String]]
    var row    = List.newBuilder[String]   // scanning a CSV is inherently a fold over characters with
    val field  = new StringBuilder         // two pieces of state; a var pair is the honest encoding
    var quoted = false
    var i      = 0
    var any    = false                     // did this row have any content at all (skip blank lines)

    def endField(): Unit = { row += field.toString; field.setLength(0) }
    def endRow(): Unit =
      endField()
      val r = row.result()
      if any then rows += r
      row = List.newBuilder[String]
      any = false

    val s = text.replace("\r\n", "\n").replace('\r', '\n')
    while i < s.length do
      val c = s.charAt(i)
      if quoted then
        if c == '"' then
          if i + 1 < s.length && s.charAt(i + 1) == '"' then { field += '"'; i += 1 }
          else quoted = false
        else field += c
        any = true
      else
        c match
          case '"'  => quoted = true; any = true
          case ','  => endField(); any = true
          case '\n' => endRow()
          case _    => field += c; if !c.isWhitespace then any = true
      i += 1
    endRow()
    rows.result()
