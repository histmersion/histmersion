package com.histmersion.domain.library.sources
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

/** A source's note in the Markdown mirror (`wiki/sources/<citekey>.md`): its YAML frontmatter carries the catalog
  * fields and findings, its body [[Source.notes]], the agent-written article about the source. [[note]] renders a
  * stored [[Source]] and [[parseNote]] reads one back, with the same key names and shapes as the research wiki's
  * schema, so `export` then `seed --force` round-trips. `ocrMarkdown` (the paper's converted text, often megabytes) is
  * not in the note: it lives in `md/<citekey>/paper.md`, referenced by `md_path`.
  */
object SourceNote:
  type Body = Body.T
  object Body extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Frontmatter = Frontmatter.T
  object Frontmatter extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FullText = FullText.T
  object FullText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkTarget = LinkTarget.T
  object LinkTarget extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Topic = Topic.T
  object Topic extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Yaml = Yaml.T
  object Yaml extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Frontmatter key order — matches the notes the research wiki already writes. */
  type ParseNoteText = _root_.com.histmersion.domain.library.sources.SourceNote.Text
  val ParseNoteText: _root_.com.histmersion.domain.library.sources.SourceNote.Text.type = _root_.com.histmersion.domain.library.sources.SourceNote.Text
  type ParseNoteFallbackCitekey = _root_.com.histmersion.domain.library.Citekey
  val ParseNoteFallbackCitekey: _root_.com.histmersion.domain.library.Citekey.type = _root_.com.histmersion.domain.library.Citekey
  type YamlMapText = _root_.com.histmersion.domain.library.sources.SourceNote.Text
  val YamlMapText: _root_.com.histmersion.domain.library.sources.SourceNote.Text.type = _root_.com.histmersion.domain.library.sources.SourceNote.Text
  type EdgesOfMPart1 = String
  type EdgesOfNoteText = _root_.com.histmersion.domain.library.sources.SourceNote.Text
  val EdgesOfNoteText: _root_.com.histmersion.domain.library.sources.SourceNote.Text.type = _root_.com.histmersion.domain.library.sources.SourceNote.Text
  type WithLinkedTopicsNoteText = _root_.com.histmersion.domain.library.sources.SourceNote.Text
  val WithLinkedTopicsNoteText: _root_.com.histmersion.domain.library.sources.SourceNote.Text.type = _root_.com.histmersion.domain.library.sources.SourceNote.Text
  type ExtractYamlText = _root_.com.histmersion.domain.library.sources.SourceNote.Text
  val ExtractYamlText: _root_.com.histmersion.domain.library.sources.SourceNote.Text.type = _root_.com.histmersion.domain.library.sources.SourceNote.Text
  type BodyOfText = _root_.com.histmersion.domain.library.sources.SourceNote.Text
  val BodyOfText: _root_.com.histmersion.domain.library.sources.SourceNote.Text.type = _root_.com.histmersion.domain.library.sources.SourceNote.Text
  type StrMPart1 = String
  type StrK = String
  type ListMPart1 = String
  type ListK = String
  type IntMPart1 = String
  type IntK = String
  type LongMPart1 = String
  type LongK = String
  type DoubleMPart1 = String
  type DoubleK = String
  type PeriodMPart1 = String
  type ToSourceMPart1 = String
  type FindingsMPart1 = String

  def note(s: Source): com.histmersion.domain.library.sources.SourceNote.Text =
    frontmatterOf(s).note(s.notes.map(_.trim).filter(_.nonEmpty))

  /** The YAML block between the `---` fences (with a trailing newline). */
  def frontmatter(s: Source): com.histmersion.domain.library.sources.SourceNote.Frontmatter = frontmatterOf(s).yaml

  private def frontmatterOf(s: Source): Frontmatter =
    Frontmatter(
      List(
        "citekey" -> s.citekey.value.toString,
        "doi" -> s.doi.map(_.value).orNull,
        "title" -> s.title.value,
        "authors" -> s.authors.map(_.value).asJava,
        "year" -> s.year.map(_.value.toInt).map(Int.box).orNull,
        "venue" -> s.venue.map(_.value).orNull,
        "source" -> s.provenance.orNull,
        "calibre_id" -> s.calibreId.orNull,
        "status" -> s.status.toString.toLowerCase,
        // `superseded_by` is stored as an edge; Frontmatter turns this key back into that edge.
        "superseded_by" -> s.edges.find(_.kind == "superseded_by").map(_.dst.value.toString).orNull,
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

  def parseFile(p: Path)(using files: com.histmersion.util.storage.TextFiles): Option[Source] = parseFile(p, None)

  /** `confineTo` is `corpus.dir` when it is configured: `md_path` is a path out of a note's frontmatter — content an
    * AGENT writes — and it used to be resolved and read with no bound at all. `None` keeps the old behaviour, for the
    * host-side workflow where the server legitimately sees the user's own tree.
    */
  def parseFile(p: Path, confineTo: Option[String])(using files: com.histmersion.util.storage.TextFiles): Option[Source] =
    val text = files.read(p)
    val fallbackCitekey = p.getFileName.toString.stripSuffix(".md")
    parseNote(text, fallbackCitekey).map(src => src.modify(_.ocrMarkdown).setTo(fullText(p, yamlMap(text), confineTo)))

  /** Parse a note's text (frontmatter + body). Pure: no file access, `ocrMarkdown` left empty. The BODY becomes
    * [[Source.notes]] — the agent-written article about the source — which is what makes [[note]] a true inverse of
    * this function.
    */
  def parseNote(text: com.histmersion.domain.library.sources.SourceNote.ParseNoteText, fallbackCitekey: com.histmersion.domain.library.sources.SourceNote.ParseNoteFallbackCitekey): Option[Source] =
    extractYaml(text).map { y =>
      val m = parse(y)
      val src = toSource(m, fallbackCitekey)
      src.modify(
        _.edges).setTo(edgesOf(src.citekey.value.toString, m, text)).modify(_.notes).setTo(bodyOf(text)).modify(_.topics).setTo(withLinkedTopics(src.topics, src.citekey.value.toString, text)
      )
    }

  private def yamlMap(text: com.histmersion.domain.library.sources.SourceNote.YamlMapText): Map[String, Any] = extractYaml(text).map(parse).getOrElse(Map.empty)

  private val CkLink = """\[\[([a-z][a-z0-9]*_[a-z0-9_]+)(?:\|[^\]]*)?\]\]""".r
  private val RelTag = """^(refutes|challenges|updates|supersedes)_(.+)$""".r

  /** The `[[…]]` targets of a note, split by what they name: a year-bearing target is a CITATION
    * (`Lint.looksLikeCitekey`, the rule V026 migrated the stored edges by), anything else — a topic slug like
    * `[[bronze_age_collapse]]` — is a TOPIC. Topic slugs used to become `references` edges to things that are not
    * sources.
    */
  private def linkTargets(citekey: com.histmersion.domain.library.Citekey, noteText: com.histmersion.domain.library.sources.SourceNote.Text): (List[com.histmersion.domain.library.sources.SourceNote.LinkTarget], List[com.histmersion.domain.library.sources.SourceNote.LinkTarget]) =
    CkLink
      .findAllMatchIn(noteText)
      .map(_.group(1))
      .filter(_ != citekey)
      .toList
      .distinct
      .partition(Lint.looksLikeCitekey)

  /** Typed, directed reference edges out of this note. */
  private def edgesOf(citekey: com.histmersion.domain.library.Citekey, m: Map[com.histmersion.domain.library.sources.SourceNote.EdgesOfMPart1, Any], noteText: com.histmersion.domain.library.sources.SourceNote.EdgesOfNoteText): List[Edge] =
    val superseded = str(m, "superseded_by").map(d => Edge(com.histmersion.api.library.Citekey(citekey), com.histmersion.api.library.Citekey(d), "superseded_by")).toList
    val rel = list(m, "topics").collect { case RelTag(kind, dst) => Edge(com.histmersion.api.library.Citekey(citekey), com.histmersion.api.library.Citekey(dst), kind) }
    val links = linkTargets(citekey, noteText)._1.map(dst => Edge(com.histmersion.api.library.Citekey(citekey), com.histmersion.api.library.Citekey(dst), "references"))
    (superseded ++ rel ++ links).distinct

  /** The note's topics plus the topic slugs its body links to, without case-insensitive duplicates. */
  private def withLinkedTopics(topics: List[com.histmersion.domain.library.sources.SourceNote.Topic], citekey: com.histmersion.domain.library.Citekey, noteText: com.histmersion.domain.library.sources.SourceNote.WithLinkedTopicsNoteText): List[com.histmersion.domain.library.sources.SourceNote.Text] =
    val have = topics.map(_.toLowerCase).toSet
    topics ++ linkTargets(citekey, noteText)._2.filterNot(t => have(t.toLowerCase))

  /** Read the converted body `md/<citekey>/paper.md` (path in `md_path`, relative to the repo root = three parents up
    * from `wiki/sources/<ck>.md`). None if absent.
    */
  private def fullText(notePath: Path, m: Map[String, Any], confineTo: Option[String])(using files: com.histmersion.util.storage.TextFiles): Option[com.histmersion.domain.library.sources.SourceNote.FullText] =
    str(m, "md_path").flatMap { rel =>
      Option(notePath.getParent)
        .flatMap(a => Option(a.getParent))
        .flatMap(a => Option(a.getParent))
        .map(_.resolve(rel))
        .filter(SafePath.within(confineTo, _))
        .filter(files.exists(_))
        .map(files.read)
        .map(_.filterNot(_.toInt == 0)) // Postgres text rejects NUL bytes (present in some OCR output)
    }

  private def extractYaml(text: com.histmersion.domain.library.sources.SourceNote.ExtractYamlText): Option[com.histmersion.domain.library.sources.SourceNote.Yaml] = split(text).map(_._1)

  /** The Markdown after the closing `---` fence: the note's article (`notes`). */
  private def bodyOf(text: com.histmersion.domain.library.sources.SourceNote.BodyOfText): Option[com.histmersion.domain.library.sources.SourceNote.Body] =
    split(text).map(_._2).map(_.trim).filter(_.nonEmpty)

  /** Split a note into (yaml, body). `None` when there is no frontmatter fence at all. */
  private def split(text: com.histmersion.domain.library.sources.SourceNote.Text): Option[(com.histmersion.domain.library.sources.SourceNote.Yaml, com.histmersion.domain.library.sources.SourceNote.Body)] =
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

  private def parse(yaml: com.histmersion.domain.library.sources.SourceNote.Yaml): Map[String, Any] =
    Option(new Yaml().load[AnyRef](yaml)) match
      case Some(m: java.util.Map[?, ?]) => m.asScala.toMap.map((k, v) => k.toString -> v)
      case _                            => Map.empty

  // ── null-safe accessors over a decoded YAML map ─────────────────────────────────────────────
  // decoded YAML values are type-tested (E165, #879): `asMatchable` states that is intended
  private def raw(m: Map[String, Any], k: String): Option[Matchable] =
    m.get(k).flatMap(v => Option(v)).map(_.asMatchable)
  private def str(m: Map[com.histmersion.domain.library.sources.SourceNote.StrMPart1, Any], k: com.histmersion.domain.library.sources.SourceNote.StrK): Option[String] =
    raw(m, k).map(_.toString).filter(s => s.nonEmpty && s != "null")
  private def list(m: Map[com.histmersion.domain.library.sources.SourceNote.ListMPart1, Any], k: com.histmersion.domain.library.sources.SourceNote.ListK): List[String] = raw(m, k) match
    case Some(l: java.util.List[?]) => l.asScala.toList.flatMap(x => Option(x)).map(_.toString)
    case _                          => Nil
  private def int(m: Map[com.histmersion.domain.library.sources.SourceNote.IntMPart1, Any], k: com.histmersion.domain.library.sources.SourceNote.IntK): Option[Int] = raw(m, k) match
    case Some(n: java.lang.Number) => Some(n.intValue)
    case Some(o)                   => o.toString.toIntOption
    case None                      => None
  private def long(m: Map[com.histmersion.domain.library.sources.SourceNote.LongMPart1, Any], k: com.histmersion.domain.library.sources.SourceNote.LongK): Option[Long] = raw(m, k) match
    case Some(n: java.lang.Number) => Some(n.longValue)
    case Some(o)                   => o.toString.toLongOption
    case None                      => None
  private def double(m: Map[com.histmersion.domain.library.sources.SourceNote.DoubleMPart1, Any], k: com.histmersion.domain.library.sources.SourceNote.DoubleK): Option[Double] = raw(m, k) match
    case Some(n: java.lang.Number) => Some(n.doubleValue)
    case Some(o)                   => o.toString.toDoubleOption
    case None                      => None

  /** ORDERED, like [[ArticleNote.parse]] does. A hand-written note with `period_start: -1200` and `period_end: -1400`
    * (an easy slip when both are negative) used to build an inverted `Period`, which Postgres rejects on the way in —
    * so importing that one note failed the whole request with a 500 instead of reading what the author obviously meant.
    */
  private def period(m: Map[com.histmersion.domain.library.sources.SourceNote.PeriodMPart1, Any]): Option[Period] =
    (long(m, "period_start"), long(m, "period_end")) match
      case (Some(a), Some(b)) if a <= b => Some(Period(a, b))
      case (Some(a), Some(b))           => Some(Period(b, a))
      case _                            => None

  private def toSource(m: Map[com.histmersion.domain.library.sources.SourceNote.ToSourceMPart1, Any], fallbackCitekey: com.histmersion.domain.library.Citekey): Source =
    val citekey = str(m, "citekey").getOrElse(fallbackCitekey)
    Source(
      citekey = com.histmersion.api.library.Citekey(citekey),
      title = com.histmersion.api.library.Title(str(m, "title").getOrElse(fallbackCitekey)),
      authors = (list(m, "authors")).map(com.histmersion.api.library.AuthorName.apply),
      doi = (str(m, "doi")).map(com.histmersion.api.library.Doi.apply),
      year = (int(m, "year")).map(com.histmersion.api.catalogue.Year.apply),
      venue = (str(m, "venue")).map(com.histmersion.api.library.Source.Venue.apply),
      status = com.histmersion.api.library.Source.Status.fromString(str(m, "status").getOrElse("unassessed")),
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

  private def findings(m: Map[com.histmersion.domain.library.sources.SourceNote.FindingsMPart1, Any], citekey: com.histmersion.domain.library.Citekey): List[Finding] =
    raw(m, "findings") match
      case Some(l: java.util.List[?]) =>
        l.asScala.toList.map(_.asMatchable).zipWithIndex.collect { case (fm: java.util.Map[?, ?], i) =>
          val f = fm.asScala.toMap.map((k, v) => k.toString -> v)
          val geo = (double(f, "lon"), double(f, "lat")) match
            case (Some(x), Some(y)) => Some(GeoPoint(x, y))
            case _                  => None
          Finding(
            id = s"$citekey#$i",
            citekey = com.histmersion.api.library.Citekey(citekey),
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
