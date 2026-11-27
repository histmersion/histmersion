package com.histmersion
package domain.library.sources
import hearth.kindlings.optics.syntax.*

import com.histmersion.api.GeoPoint
import com.histmersion.api.given

import org.yaml.snakeyaml.Yaml
import scala.compiletime.asMatchable
import scala.jdk.CollectionConverters.*
import java.nio.file.{Files, Path}
import com.histmersion.util.data.mirror.Frontmatter
import com.histmersion.api.library.Confidence
import com.histmersion.util.storage.SafePath
import com.histmersion.api.catalogue.Period
import com.histmersion.api.checks.Lint
import com.histmersion.api.library.{Edge, Finding, Source}
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.storage.TextFiles
import com.histmersion.api.library.Citekey
import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.Title
import com.histmersion.api.library.AuthorName
import com.histmersion.api.library.Doi
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.catalogue.Latitude
import com.histmersion.lint.{tolerated, LintRule}

/** A source's note in the Markdown mirror (`wiki/sources/<citekey>.md`): its YAML frontmatter carries the catalog
  * fields and findings, its body [[Source.notes]], the agent-written article about the source. [[note]] renders a
  * stored [[Source]] and [[parseNote]] reads one back, with the same key names and shapes as the research wiki's
  * schema, so `export` then `seed --force` round-trips. `ocrMarkdown` (the paper's converted text, often megabytes) is
  * not in the note: it lives in `md/<citekey>/paper.md`, referenced by `md_path`.
  */
@tolerated(LintRule.FileSystem, "pending G23 (files only through util.storage)")
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
object SourceNote:
  type Text = Text.T
  object Text extends Newtype[String, Pure]

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def note(s: Source): Text =
    Text(frontmatterOf(s).note(s.notes.map(n => Frontmatter.NoteBody(n.value.trim)).filter(_.value.nonEmpty)).value)

  /** The YAML block between the `---` fences (with a trailing newline). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def frontmatter(s: Source): Frontmatter.Block = frontmatterOf(s).yaml

  /** Frontmatter key order — matches the notes the research wiki already writes. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def frontmatterOf(s: Source): Frontmatter =
    Frontmatter(
      List(
        field("citekey", s.citekey.value),
        field("doi", s.doi.map(_.value).orNull),
        field("title", s.title.value),
        field("authors", s.authors.map(_.value).asJava),
        field("year", s.year.map(_.value.toInt).map(Int.box).orNull),
        field("venue", s.venue.map(_.value).orNull),
        field("source", s.provenance.orNull),
        field("calibre_id", s.calibreId.orNull),
        field("status", s.status.toString.toLowerCase),
        // `superseded_by` is stored as an edge; Frontmatter turns this key back into that edge.
        field("superseded_by", s.edges.find(_.kind == Edge.Kind.SupersededBy).map(_.dst.value).orNull),
        field("topics", s.topics.asJava),
        field("regions", s.regions.asJava),
        field("tags", s.tags.asJava),
        field("period_start", s.period.map(p => Long.box(p.start.value)).orNull),
        field("period_end", s.period.map(p => Long.box(p.end.value)).orNull),
        field("cited_by_count", s.citedByCount.map(c => Int.box(c.value)).orNull),
        field("influential_citations", s.influentialCitations.map(c => Int.box(c.value)).orNull),
        field("summary", s.summary.orNull),
        field("findings", s.findings.map(finding).asJava)
      )
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def field(key: String, value: Any): (Frontmatter.Key, Any) = (Frontmatter.Key(key), value)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def finding(f: Finding): java.util.Map[String, Any] =
    val m = new java.util.LinkedHashMap[String, Any]()
    m.put("claim", f.claim)
    m.put("region", f.region.orNull)
    m.put("lat", f.geo.map(g => Double.box(g.lat.value)).orNull)
    m.put("lon", f.geo.map(g => Double.box(g.lon.value)).orNull)
    m.put("period_start", f.period.map(p => Long.box(p.start.value)).orNull)
    m.put("period_end", f.period.map(p => Long.box(p.end.value)).orNull)
    m.put("period_name", f.periodName.orNull)
    m.put("tags", f.tags.asJava)
    m.put("confidence", f.confidence.map(_.toString.toLowerCase).orNull)
    m.put("significance", f.significance.orNull)
    m

  def parseFile(p: Path)(using files: TextFiles): Option[Source] = parseFile(p, None)

  /** `confineTo` is `corpus.dir` when it is configured: `md_path` is a path out of a note's frontmatter — content an
    * AGENT writes — and it used to be resolved and read with no bound at all. `None` keeps the old behaviour, for the
    * host-side workflow where the server legitimately sees the user's own tree.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parseFile(p: Path, confineTo: Option[SafePath.Base])(using files: TextFiles): Option[Source] =
    val text = Text(files.read(p).value)
    val fallbackCitekey = Citekey(p.getFileName.toString.stripSuffix(".md"))
    parseNote(text, fallbackCitekey).map(src => src.modify(_.ocrMarkdown).setTo(fullText(p, yamlMap(text), confineTo)))

  /** Parse a note's text (frontmatter + body). Pure: no file access, `ocrMarkdown` left empty. The BODY becomes
    * [[Source.notes]] — the agent-written article about the source — which is what makes [[note]] a true inverse of
    * this function.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def parseNote(text: Text, fallbackCitekey: Citekey): Option[Source] =
    extractYaml(text).map { y =>
      val m = parse(y)
      val src = toSource(m, fallbackCitekey)
      src
        .modify(_.edges)
        .setTo(edgesOf(src.citekey, m, text))
        .modify(_.notes)
        .setTo(bodyOf(text))
        .modify(_.topics)
        .setTo(withLinkedTopics(src.topics, src.citekey, text))
    }

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def yamlMap(text: Text): Map[String, Any] = extractYaml(text).map(parse).getOrElse(Map.empty)

  private val CkLink = """\[\[([a-z][a-z0-9]*_[a-z0-9_]+)(?:\|[^\]]*)?\]\]""".r
  private val RelTag = """^(refutes|challenges|updates|supersedes)_(.+)$""".r

  /** The `[[…]]` targets of a note, split by what they name: a year-bearing target is a CITATION
    * (`Lint.looksLikeCitekey`, the rule V026 migrated the stored edges by), anything else — a topic slug like
    * `[[bronze_age_collapse]]` — is a TOPIC. Topic slugs used to become `references` edges to things that are not
    * sources.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def linkTargets(citekey: Citekey, noteText: Text): (List[Citekey], List[Source.Topics]) =
    val (cites, topics) = CkLink
      .findAllMatchIn(noteText.value)
      .map(_.group(1))
      .filter(_ != citekey.value)
      .toList
      .distinct
      .partition(t => Lint.looksLikeCitekey(Citekey(t)))
    (cites.map(Citekey(_)), topics.map(Source.Topics(_)))

  /** Typed, directed reference edges out of this note. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def edgesOf(citekey: Citekey, m: Map[String, Any], noteText: Text): List[Edge] =
    val superseded = str(m, "superseded_by").map(d => Edge(citekey, Citekey(d), Edge.Kind.SupersededBy)).toList
    val rel = list(m, "topics").flatMap {
      case RelTag(kind, dst) => Edge.Kind.fromWire(Edge.Kind.Wire(kind)).map(k => Edge(citekey, Citekey(dst), k))
      case _                 => None
    }
    val links = linkTargets(citekey, noteText)._1.map(dst => Edge(citekey, dst, Edge.Kind.References))
    (superseded ++ rel ++ links).distinct

  /** The note's topics plus the topic slugs its body links to, without case-insensitive duplicates. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def withLinkedTopics(topics: List[Source.Topics], citekey: Citekey, noteText: Text): List[Source.Topics] =
    val have = topics.map(_.value.toLowerCase).toSet
    topics ++ linkTargets(citekey, noteText)._2.filterNot(t => have(t.value.toLowerCase))

  /** Read the converted body `md/<citekey>/paper.md` (path in `md_path`, relative to the repo root = three parents up
    * from `wiki/sources/<ck>.md`). None if absent.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def fullText(notePath: Path, m: Map[String, Any], confineTo: Option[SafePath.Base])(using
      files: TextFiles
  ): Option[Source.OcrMarkdown] =
    str(m, "md_path").flatMap { rel =>
      Option(notePath.getParent)
        .flatMap(a => Option(a.getParent))
        .flatMap(a => Option(a.getParent))
        .map(_.resolve(rel))
        .filter(SafePath.within(confineTo, _))
        .filter(files.exists(_))
        .map(f =>
          Source.OcrMarkdown(files.read(f).value.filterNot(_.toInt == 0))
        ) // Postgres text rejects NUL bytes (present in some OCR output)
    }

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def extractYaml(text: Text): Option[String] = split(text.value).map(_._1)

  /** The Markdown after the closing `---` fence: the note's article (`notes`). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def bodyOf(text: Text): Option[Source.Notes] =
    split(text.value).map(_._2).map(_.trim).filter(_.nonEmpty).map(Source.Notes(_))

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
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def period(m: Map[String, Any]): Option[Period] =
    (long(m, "period_start"), long(m, "period_end")) match
      case (Some(a), Some(b)) if a <= b => Some(Period(Year(a), Year(b)))
      case (Some(a), Some(b))           => Some(Period(Year(b), Year(a)))
      case _                            => None

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def toSource(m: Map[String, Any], fallbackCitekey: Citekey): Source =
    val citekey = str(m, "citekey").map(Citekey(_)).getOrElse(fallbackCitekey)
    Source(
      citekey = citekey,
      title = Title(str(m, "title").getOrElse(fallbackCitekey.value)),
      authors = (list(m, "authors")).map(AuthorName.apply),
      doi = (str(m, "doi")).map(Doi.apply),
      year = (int(m, "year")).map(Year.apply),
      venue = (str(m, "venue")).map(Source.Venue.apply),
      status = Source.Status.fromString(Source.Status.Wire(str(m, "status").getOrElse("unassessed"))),
      topics = list(m, "topics").map(Source.Topics(_)),
      regions = list(m, "regions").map(Source.Regions(_)),
      tags = list(m, "tags").map(Source.Tags(_)),
      period = period(m),
      summary = str(m, "summary").map(Source.Summary(_)),
      citedByCount = int(m, "cited_by_count").map(Source.CitedByCount(_)),
      influentialCitations = int(m, "influential_citations").map(Source.InfluentialCitations(_)),
      provenance = str(m, "source").map(Source.Provenance(_)),
      calibreId = str(m, "calibre_id").map(Source.CalibreId(_)),
      findings = findings(m, citekey)
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def findings(m: Map[String, Any], citekey: Citekey): List[Finding] =
    raw(m, "findings") match
      case Some(l: java.util.List[?]) =>
        l.asScala.toList.map(_.asMatchable).zipWithIndex.collect { case (fm: java.util.Map[?, ?], i) =>
          val f = fm.asScala.toMap.map((k, v) => k.toString -> v)
          val geo = (double(f, "lon"), double(f, "lat")) match
            case (Some(x), Some(y)) => Some(GeoPoint(Longitude(x), Latitude(y)))
            case _                  => None
          Finding(
            id = Finding.Id(s"${citekey.value}#$i"),
            citekey = citekey,
            claim = Finding.Claim(str(f, "claim").getOrElse("")),
            region = str(f, "region").map(Finding.Region(_)),
            geo = geo,
            period = period(f),
            periodName = str(f, "period_name").map(Finding.PeriodName(_)),
            tags = list(f, "tags").map(Finding.Tags(_)),
            confidence = str(f, "confidence").flatMap(c => Confidence.fromString(Confidence.Wire(c))),
            significance = str(f, "significance").map(Finding.Significance(_))
          )
        }
      case _ => Nil
