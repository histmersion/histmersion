package com.histmersion.domain.library.sources
import com.histmersion.api.GeoPoint
import com.histmersion.api.given

import org.yaml.snakeyaml.Yaml
import scala.compiletime.asMatchable
import scala.jdk.CollectionConverters.*
import java.nio.file.{Files, Path}
import com.histmersion.util.data.mirror.Frontmatter
import com.histmersion.domain.library.Confidence
import com.histmersion.util.storage.SafePath
import com.histmersion.api.catalogue.Period
import com.histmersion.api.checks.Lint
import com.histmersion.api.library.{Edge, Finding, Source}

/** A source's note in the Markdown mirror (`wiki/sources/<citekey>.md`): its YAML frontmatter carries the catalog
  * fields and findings, its body [[Source.notes]], the agent-written article about the source. [[note]] renders a
  * stored [[Source]] and [[parseNote]] reads one back, with the same key names and shapes as the research wiki's
  * schema, so `export` then `seed --force` round-trips. `ocrMarkdown` (the paper's converted text, often megabytes) is
  * not in the note: it lives in `md/<citekey>/paper.md`, referenced by `md_path`.
  */
object SourceNote:

  /** Frontmatter key order — matches the notes the research wiki already writes. */
  def note(s: Source): String =
    frontmatterOf(s).note(s.notes.map(_.trim).filter(_.nonEmpty))

  /** The YAML block between the `---` fences (with a trailing newline). */
  def frontmatter(s: Source): String = frontmatterOf(s).yaml

  private def frontmatterOf(s: Source): Frontmatter =
    Frontmatter(
      List(
        "citekey" -> s.citekey,
        "doi" -> s.doi.orNull,
        "title" -> s.title,
        "authors" -> s.authors.asJava,
        "year" -> s.year.map(Int.box).orNull,
        "venue" -> s.venue.orNull,
        "source" -> s.provenance.orNull,
        "calibre_id" -> s.calibreId.orNull,
        "status" -> s.status.toString.toLowerCase,
        // `superseded_by` is stored as an edge; Frontmatter turns this key back into that edge.
        "superseded_by" -> s.edges.find(_.kind == "superseded_by").map(_.dst).orNull,
        "topics" -> s.topics.asJava,
        "regions" -> s.regions.asJava,
        "tags" -> s.tags.asJava,
        "period_start" -> s.period.map(p => Long.box(p.start)).orNull,
        "period_end" -> s.period.map(p => Long.box(p.end)).orNull,
        "cited_by_count" -> s.citedByCount.map(Int.box).orNull,
        "influential_citations" -> s.influentialCitations.map(Int.box).orNull,
        "summary" -> s.summary.orNull,
        "findings" -> s.findings.map(finding).asJava
      )
    )

  private def finding(f: Finding): java.util.Map[String, Any] =
    val m = new java.util.LinkedHashMap[String, Any]()
    m.put("claim", f.claim)
    m.put("region", f.region.orNull)
    m.put("lat", f.geo.map(g => Double.box(g.lat)).orNull)
    m.put("lon", f.geo.map(g => Double.box(g.lon)).orNull)
    m.put("period_start", f.period.map(p => Long.box(p.start)).orNull)
    m.put("period_end", f.period.map(p => Long.box(p.end)).orNull)
    m.put("period_name", f.periodName.orNull)
    m.put("tags", f.tags.asJava)
    m.put("confidence", f.confidence.map(_.toString.toLowerCase).orNull)
    m.put("significance", f.significance.orNull)
    m

  def parseFile(p: Path): Option[Source] = parseFile(p, None)

  /** `confineTo` is `corpus.dir` when it is configured: `md_path` is a path out of a note's frontmatter — content an
    * AGENT writes — and it used to be resolved and read with no bound at all. `None` keeps the old behaviour, for the
    * host-side workflow where the server legitimately sees the user's own tree.
    */
  def parseFile(p: Path, confineTo: Option[String]): Option[Source] =
    val text = Files.readString(p)
    val fallbackCitekey = p.getFileName.toString.stripSuffix(".md")
    parseNote(text, fallbackCitekey).map(src => src.copy(ocrMarkdown = fullText(p, yamlMap(text), confineTo)))

  /** Parse a note's text (frontmatter + body). Pure: no file access, `ocrMarkdown` left empty. The BODY becomes
    * [[Source.notes]] — the agent-written article about the source — which is what makes [[note]] a true inverse of
    * this function.
    */
  def parseNote(text: String, fallbackCitekey: String): Option[Source] =
    extractYaml(text).map { y =>
      val m = parse(y)
      val src = toSource(m, fallbackCitekey)
      src.copy(
        edges = edgesOf(src.citekey, m, text),
        notes = bodyOf(text),
        topics = withLinkedTopics(src.topics, src.citekey, text)
      )
    }

  private def yamlMap(text: String): Map[String, Any] = extractYaml(text).map(parse).getOrElse(Map.empty)

  private val CkLink = """\[\[([a-z][a-z0-9]*_[a-z0-9_]+)(?:\|[^\]]*)?\]\]""".r
  private val RelTag = """^(refutes|challenges|updates|supersedes)_(.+)$""".r

  /** The `[[…]]` targets of a note, split by what they name: a year-bearing target is a CITATION
    * (`Lint.looksLikeCitekey`, the rule V026 migrated the stored edges by), anything else — a topic slug like
    * `[[bronze_age_collapse]]` — is a TOPIC. Topic slugs used to become `references` edges to things that are not
    * sources.
    */
  private def linkTargets(citekey: String, noteText: String): (List[String], List[String]) =
    CkLink
      .findAllMatchIn(noteText)
      .map(_.group(1))
      .filter(_ != citekey)
      .toList
      .distinct
      .partition(Lint.looksLikeCitekey)

  /** Typed, directed reference edges out of this note. */
  private def edgesOf(citekey: String, m: Map[String, Any], noteText: String): List[Edge] =
    val superseded = str(m, "superseded_by").map(d => Edge(citekey, d, "superseded_by")).toList
    val rel = list(m, "topics").collect { case RelTag(kind, dst) => Edge(citekey, dst, kind) }
    val links = linkTargets(citekey, noteText)._1.map(dst => Edge(citekey, dst, "references"))
    (superseded ++ rel ++ links).distinct

  /** The note's topics plus the topic slugs its body links to, without case-insensitive duplicates. */
  private def withLinkedTopics(topics: List[String], citekey: String, noteText: String): List[String] =
    val have = topics.map(_.toLowerCase).toSet
    topics ++ linkTargets(citekey, noteText)._2.filterNot(t => have(t.toLowerCase))

  /** Read the converted body `md/<citekey>/paper.md` (path in `md_path`, relative to the repo root = three parents up
    * from `wiki/sources/<ck>.md`). None if absent.
    */
  private def fullText(notePath: Path, m: Map[String, Any], confineTo: Option[String]): Option[String] =
    str(m, "md_path").flatMap { rel =>
      Option(notePath.getParent)
        .flatMap(a => Option(a.getParent))
        .flatMap(a => Option(a.getParent))
        .map(_.resolve(rel))
        .filter(SafePath.within(confineTo, _))
        .filter(Files.exists(_))
        .map(Files.readString)
        .map(_.filterNot(_.toInt == 0)) // Postgres text rejects NUL bytes (present in some OCR output)
    }

  private def extractYaml(text: String): Option[String] = split(text).map(_._1)

  /** The Markdown after the closing `---` fence: the note's article (`notes`). */
  private def bodyOf(text: String): Option[String] =
    split(text).map(_._2).map(_.trim).filter(_.nonEmpty)

  /** Split a note into (yaml, body). `None` when there is no frontmatter fence at all. */
  private def split(text: String): Option[(String, String)] =
    val t = text.stripPrefix("﻿")
    if !t.startsWith("---") then None
    else
      val rest = t.drop(3)
      val end = rest.indexOf("\n---")
      if end < 0 then None
      else
        // after the closing fence: skip the remainder of that line (it may carry trailing spaces/CR)
        val after = rest.substring(end + "\n---".length)
        val body = after.indexOf('\n') match
          case -1 => ""
          case i  => after.substring(i + 1)
        Some((rest.substring(0, end), body))

  private def parse(yaml: String): Map[String, Any] =
    Option(new Yaml().load[AnyRef](yaml)) match
      case Some(m: java.util.Map[?, ?]) => m.asScala.toMap.map((k, v) => k.toString -> v)
      case _                            => Map.empty

  // ── null-safe accessors over a decoded YAML map ─────────────────────────────────────────────
  // decoded YAML values are type-tested (E165, #879): `asMatchable` states that is intended
  private def raw(m: Map[String, Any], k: String): Option[Matchable] =
    m.get(k).flatMap(v => Option(v)).map(_.asMatchable)
  private def str(m: Map[String, Any], k: String): Option[String] =
    raw(m, k).map(_.toString).filter(s => s.nonEmpty && s != "null")
  private def list(m: Map[String, Any], k: String): List[String] = raw(m, k) match
    case Some(l: java.util.List[?]) => l.asScala.toList.flatMap(x => Option(x)).map(_.toString)
    case _                          => Nil
  private def int(m: Map[String, Any], k: String): Option[Int] = raw(m, k) match
    case Some(n: java.lang.Number) => Some(n.intValue)
    case Some(o)                   => o.toString.toIntOption
    case None                      => None
  private def long(m: Map[String, Any], k: String): Option[Long] = raw(m, k) match
    case Some(n: java.lang.Number) => Some(n.longValue)
    case Some(o)                   => o.toString.toLongOption
    case None                      => None
  private def double(m: Map[String, Any], k: String): Option[Double] = raw(m, k) match
    case Some(n: java.lang.Number) => Some(n.doubleValue)
    case Some(o)                   => o.toString.toDoubleOption
    case None                      => None

  /** ORDERED, like [[ArticleNote.parse]] does. A hand-written note with `period_start: -1200` and `period_end: -1400`
    * (an easy slip when both are negative) used to build an inverted `Period`, which Postgres rejects on the way in —
    * so importing that one note failed the whole request with a 500 instead of reading what the author obviously meant.
    */
  private def period(m: Map[String, Any]): Option[Period] =
    (long(m, "period_start"), long(m, "period_end")) match
      case (Some(a), Some(b)) if a <= b => Some(Period(a, b))
      case (Some(a), Some(b))           => Some(Period(b, a))
      case _                            => None

  private def toSource(m: Map[String, Any], fallbackCitekey: String): Source =
    val citekey = str(m, "citekey").getOrElse(fallbackCitekey)
    Source(
      citekey = citekey,
      title = str(m, "title").getOrElse(fallbackCitekey),
      authors = list(m, "authors"),
      doi = str(m, "doi"),
      year = int(m, "year"),
      venue = str(m, "venue"),
      status = com.histmersion.domain.library.Source.Status.fromString(str(m, "status").getOrElse("unassessed")),
      topics = list(m, "topics"),
      regions = list(m, "regions"),
      tags = list(m, "tags"),
      period = period(m),
      summary = str(m, "summary"),
      citedByCount = int(m, "cited_by_count"),
      influentialCitations = int(m, "influential_citations"),
      provenance = str(m, "source"),
      calibreId = str(m, "calibre_id"),
      findings = findings(m, citekey)
    )

  private def findings(m: Map[String, Any], citekey: String): List[Finding] =
    raw(m, "findings") match
      case Some(l: java.util.List[?]) =>
        l.asScala.toList.map(_.asMatchable).zipWithIndex.collect { case (fm: java.util.Map[?, ?], i) =>
          val f = fm.asScala.toMap.map((k, v) => k.toString -> v)
          val geo = (double(f, "lon"), double(f, "lat")) match
            case (Some(x), Some(y)) => Some(GeoPoint(x, y))
            case _                  => None
          Finding(
            id = s"$citekey#$i",
            citekey = citekey,
            claim = str(f, "claim").getOrElse(""),
            region = str(f, "region"),
            geo = geo,
            period = period(f),
            periodName = str(f, "period_name"),
            tags = list(f, "tags"),
            confidence = str(f, "confidence").flatMap(Confidence.fromString),
            significance = str(f, "significance")
          )
        }
      case _ => Nil
