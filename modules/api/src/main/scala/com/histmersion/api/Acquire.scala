package com.histmersion.api

import com.histmersion.values.SourceStatus

object Acquire:

  enum Ident:
    case Doi(value: String)

    case Arxiv(value: String)

    case Citekey(value: String)

  private val DoiCore = """10\.\d{4,9}/[-._;()/:A-Za-z0-9<>\[\]]+""".r
  private val ArxivNew = """^(?:arxiv:)?(\d{4}\.\d{4,5})(?:v\d+)?$""".r
  private val ArxivOld = """^(?:arxiv:)?([a-z-]+(?:\.[A-Z]{2})?/\d{7})(?:v\d+)?$""".r

  /** DOIs are case-insensitive, so they are stored lower-case. */
  def normalizeDoi(raw: String): Option[String] =
    val s = Option(raw)
      .getOrElse("")
      .trim
      .replaceFirst("(?i)^https?://(dx\\.)?doi\\.org/", "")
      .replaceFirst("(?i)^doi:\\s*", "")
      .trim
    DoiCore
      .findFirstIn(s)
      .map(
        _.stripSuffix(".")
          .stripSuffix(",")
          .stripSuffix(";")
          .stripSuffix(")")
          .stripSuffix("}")
          .stripSuffix("|")
          .toLowerCase
      )

  def normalizeArxiv(raw: String): Option[String] =
    Option(raw).map(_.trim).filter(_.nonEmpty).flatMap { s =>
      s.toLowerCase match
        case ArxivNew(id) => Some(id)
        case _            =>
          s match
            case ArxivOld(id) => Some(id)
            case _            => None
    }

  def parseIdent(raw: String): Either[String, Ident] =
    val s = Option(raw).getOrElse("").trim
    if s.isEmpty then Left("empty identifier — give a DOI, arxiv:<id>, or a citekey")
    else
      normalizeDoi(s)
        .map(Ident.Doi.apply)
        .orElse(normalizeArxiv(s).map(Ident.Arxiv.apply))
        .orElse(Option.when(isCitekey(s))(Ident.Citekey(s)))
        .toRight(s"'$s' is neither a DOI, an arXiv id, nor a citekey")

  /** Deliberately permissive: the corpus holds hand-written keys and keys of older conventions that the generator would
    * not produce, so the store, not a regular expression, decides whether a citekey exists.
    */
  def isCitekey(s: String): Boolean =
    s.nonEmpty && s.head.isLetter && !s.exists(_.isWhitespace) && !s.contains("/")

  private val Stop = Set("the", "a", "an", "of", "on", "and", "in", "to", "for", "study", "new")

  val MinCitekeyYear = 1000

  /** Follows the convention every existing key uses (`drews1993_collapse`, `haak2015_massive`), so a re-ingest of a
    * stored paper lands on its existing citekey instead of a duplicate.
    */
  def citekey(authors: List[String], year: Option[Int], title: Option[String]): String =
    legacyCitekey(authors.map(LatinFold.fold), year, title.map(LatinFold.fold))

  /** Records created before accented letters were folded keep keys that dropped them (`gterbock1938`), so a lookup by
    * generated key tries this form too.
    */
  def legacyCitekey(authors: List[String], year: Option[Int], title: Option[String]): String =
    val first = authors.map(_.trim).find(_.nonEmpty)
    val surname = first
      .map { a =>
        if a.contains(",") then a.takeWhile(_ != ',')
        else a.split("\\s+").filter(_.nonEmpty).lastOption.getOrElse(a)
      }
      .map(_.toLowerCase.replaceAll("[^a-z]", ""))
      .filter(_.nonEmpty)
      .getOrElse("anon")
    // Calibre's "no date" is 0101-01-01: a year below 1000 is a placeholder, not a publication year
    val yr = year.filter(_ >= MinCitekeyYear).map(_.toString).getOrElse("nd")
    val word = """[A-Za-z]+""".r
      .findAllIn(title.getOrElse(""))
      .map(_.toLowerCase)
      .find(w => w.length > 3 && !Stop.contains(w))
    s"$surname$yr" + word.fold("")("_" + _)

  def citekeyCandidates(authors: List[String], year: Option[Int], title: Option[String]): List[String] =
    List(citekey(authors, year, title), legacyCitekey(authors, year, title)).distinct

  object Verdicts:
    val Retracted = "retracted"
    val Corrected = "corrected"
    val Current = "current"
    val LowImpact = "low-impact"
    val Unassessed = "unassessed"
    val all: List[String] = List(Retracted, Corrected, Current, LowImpact, Unassessed)

  private val RetractingUpdate = Set("retraction", "withdrawal", "removal")
  private val CorrectingUpdate =
    Set(
      "correction",
      "corrigendum",
      "erratum",
      "addendum",
      "expression_of_concern",
      "expression of concern",
      "partial_retraction",
      "partial retraction"
    )

  private def isRetractingNature(n: String): Boolean =
    val s = n.toLowerCase
    s.contains("retraction") && !s.contains("reinstatement") || s.contains("withdrawal")

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
        case None                                                      => Verdicts.Unassessed
        case Some(c) if c >= wellCitedAt                               => Verdicts.Current
        case Some(c) if c < lowCitedAt && year.exists(_ < staleBefore) => Verdicts.LowImpact
        case Some(_)                                                   => Verdicts.Current

  /** Some publishers answer scripts with a 403, an empty file or a challenge, so an open-access work they host is
    * downloaded in the browser, under its licence.
    */
  val BrowserDownloadTopic = "browser download"

  def browserDownloadText(work: WorkRef, doi: String, citekey: String, refusals: List[String] = Nil): Option[String] =
    Option.when(work.isOa) {
      val who = work.authors.headOption.map(a => if work.authors.size > 1 then s"$a et al." else a)
      val head = List(who, work.year.map(y => s"($y)")).flatten.mkString(" ")
      val title = work.title.map(_.trim).filter(_.nonEmpty).getOrElse(doi)
      val oa = work.oaStatus.map(_.trim.toLowerCase).filter(_.nonEmpty).fold("open access")(s => s"$s open access")
      val at = work.oaUrl
        .map(_.trim)
        .filter(u => u.nonEmpty && !u.toLowerCase.contains(doi.toLowerCase))
        .fold("")(u => s" at $u")
      val said = refusals.map(_.trim).filter(_.nonEmpty).distinct.mkString("; ")
      s"${if head.isEmpty then "" else s"$head. "}$title. https://doi.org/$doi — $oa$at, but no script may download " +
        s"it: download it in the browser${
            if said.isEmpty then "" else s" ($said)"
          }, then histmersion ingest <file> --citekey $citekey"
    }

  def flagged(verdict: String): Boolean = verdict == Verdicts.Retracted || verdict == Verdicts.Corrected

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

  def statusFor(verdict: String): Option[SourceStatus] = verdict match
    case Verdicts.Retracted => Some(SourceStatus.Retracted)
    case Verdicts.Corrected => Some(SourceStatus.Contested)
    case Verdicts.Current   => Some(SourceStatus.Current)
    case _                  => None

  def statusDecision(
      verdict: String,
      current: SourceStatus,
      force: Boolean
  ): Option[Either[SourceStatus, SourceStatus]] =
    statusFor(verdict).map { target =>
      if target == SourceStatus.Retracted || force || current == SourceStatus.Unassessed || current == target
      then Right(target)
      else Left(current)
    }

  object EdgeKinds:
    val Confirms = "confirms"
    val Challenges = "challenges"
    val Refutes = "refutes"
    val Supersedes = "supersedes"
    val Cites = "cites"
    val all: List[String] = List(Confirms, Challenges, Refutes, Supersedes, Cites)

  private val RefutesCues = List(
    "refut",
    "disprov",
    "contradict",
    "falsif",
    "not replicat",
    "not be replicat",
    "fails to replicate",
    "failed to reproduce",
    "no support for",
    "no evidence for",
    "cannot be sustained",
    "is untenable",
    "demonstrably wrong"
  )
  private val SupersedeCues =
    List("supersed", "obsolete", "outdated", "superseded by", "has been replaced", "no longer tenable")
  private val ReviseCues =
    List("revis", "updat", "reanalys", "re-analys", "reanalyz", "larger dataset", "more data", "extend")
  private val ChallengeCues = List(
    "challeng",
    "question",
    "dispute",
    "criticis",
    "criticiz",
    "problematic",
    "contrary to",
    "at odds with",
    "inconsisten",
    "however",
    "but see",
    "contra "
  )
  private val ConfirmCues = List(
    "confirm",
    "corroborat",
    "consistent with",
    "in agreement",
    "support",
    "replicat",
    "as shown by",
    "in line with"
  )

  def classify(
      intents: List[String],
      contexts: List[String],
      isInfluential: Boolean,
      citingYear: Option[Int],
      targetYear: Option[Int],
      gapYears: Int = 5
  ): String =
    val text = contexts.mkString(" ").toLowerCase
    val strong = intents.map(_.toLowerCase).exists(i => i == "result" || i == "methodology")
    val newer = (citingYear, targetYear) match
      case (Some(c), Some(t)) => c - t >= gapYears
      case _                  => false
    def has(cues: List[String]) = cues.exists(text.contains)

    if has(RefutesCues) then EdgeKinds.Refutes
    else if has(SupersedeCues) then EdgeKinds.Supersedes
    else if isInfluential && strong && newer && has(ReviseCues) then EdgeKinds.Supersedes
    else if has(ChallengeCues) then EdgeKinds.Challenges
    else if has(ConfirmCues) then EdgeKinds.Confirms
    else EdgeKinds.Cites

  def rankFollowups(rows: List[FollowupRow]): List[FollowupRow] =
    rows.sortBy { r =>
      val strong = r.intents.map(_.toLowerCase).exists(i => i == "result" || i == "methodology")
      (if r.isInfluential then 0 else 1, if strong then 0 else 1, -r.year.getOrElse(0))
    }

  final case class RetractionRow(doi: String, reason: Option[String], date: Option[String], nature: Option[String])

  /** The export's `Reason` column is full of quoted commas and embedded newlines, so it needs a real CSV reader.
    * Columns are located by header name because their order has changed between exports, and the last row for a DOI
    * wins because the export lists amendments chronologically.
    */
  def parseRetractionWatch(csv: String): List[RetractionRow] =
    Csv.parse(csv) match
      case Nil            => Nil
      case header :: rows =>
        val idx = header.map(_.trim.replaceAll("^﻿", "")).zipWithIndex.toMap
        def col(r: List[String], name: String): Option[String] =
          idx.get(name).flatMap(r.lift).map(_.trim).filter(_.nonEmpty)
        rows.flatMap { r =>
          col(r, "OriginalPaperDOI").flatMap(normalizeDoi).map { doi =>
            RetractionRow(doi, col(r, "Reason"), col(r, "RetractionDate"), col(r, "RetractionNature"))
          }
        }

  /** Retraction Watch writes dates as `MM/DD/YYYY 0:00`; Postgres wants `YYYY-MM-DD`. */
  def rwDate(raw: Option[String]): Option[String] =
    raw.map(_.trim.takeWhile(_ != ' ')).flatMap { s =>
      s.split("[/-]").toList match
        case m :: d :: y :: Nil if y.length == 4 && m.forall(_.isDigit) && d.forall(_.isDigit) =>
          Some(f"${y.toInt}%04d-${m.toInt}%02d-${d.toInt}%02d")
        case y :: m :: d :: Nil if y.length == 4 && m.forall(_.isDigit) && d.forall(_.isDigit) =>
          Some(f"${y.toInt}%04d-${m.toInt}%02d-${d.toInt}%02d")
        case _ => None
    }
