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

  /** Frontmatter key order — matches the notes the research wiki already writes. */
  type NoteResult = NoteResult.T
  object NoteResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FrontmatterResult = FrontmatterResult.T
  object FrontmatterResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FindingResultPart1 = FindingResultPart1.T
  object FindingResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseFileConfineTo = ParseFileConfineTo.T
  object ParseFileConfineTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseNoteText = _root_.com.histmersion.domain.library.sources.SourceNote.SplitText
  val ParseNoteText: _root_.com.histmersion.domain.library.sources.SourceNote.SplitText.type = _root_.com.histmersion.domain.library.sources.SourceNote.SplitText
  type ParseNoteFallbackCitekey = _root_.com.histmersion.domain.library.sources.SourceNote.ToSourceFallbackCitekey
  val ParseNoteFallbackCitekey: _root_.com.histmersion.domain.library.sources.SourceNote.ToSourceFallbackCitekey.type = _root_.com.histmersion.domain.library.sources.SourceNote.ToSourceFallbackCitekey
  type YamlMapResultPart1 = YamlMapResultPart1.T
  object YamlMapResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type YamlMapText = _root_.com.histmersion.domain.library.sources.SourceNote.SplitText
  val YamlMapText: _root_.com.histmersion.domain.library.sources.SourceNote.SplitText.type = _root_.com.histmersion.domain.library.sources.SourceNote.SplitText
  type LinkTargetsResultPart1 = LinkTargetsResultPart1.T
  object LinkTargetsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkTargetsResultPart2 = LinkTargetsResultPart2.T
  object LinkTargetsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkTargetsNoteText = LinkTargetsNoteText.T
  object LinkTargetsNoteText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EdgesOfMPart1 = _root_.com.histmersion.domain.library.sources.SourceNote.RawMPart1
  val EdgesOfMPart1: _root_.com.histmersion.domain.library.sources.SourceNote.RawMPart1.type = _root_.com.histmersion.domain.library.sources.SourceNote.RawMPart1
  type EdgesOfNoteText = _root_.com.histmersion.domain.library.sources.SourceNote.LinkTargetsNoteText
  val EdgesOfNoteText: _root_.com.histmersion.domain.library.sources.SourceNote.LinkTargetsNoteText.type = _root_.com.histmersion.domain.library.sources.SourceNote.LinkTargetsNoteText
  type WithLinkedTopicsResult = WithLinkedTopicsResult.T
  object WithLinkedTopicsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WithLinkedTopicsTopics = WithLinkedTopicsTopics.T
  object WithLinkedTopicsTopics extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WithLinkedTopicsNoteText = _root_.com.histmersion.domain.library.sources.SourceNote.LinkTargetsNoteText
  val WithLinkedTopicsNoteText: _root_.com.histmersion.domain.library.sources.SourceNote.LinkTargetsNoteText.type = _root_.com.histmersion.domain.library.sources.SourceNote.LinkTargetsNoteText
  type FullTextResult = FullTextResult.T
  object FullTextResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FullTextMPart1 = FullTextMPart1.T
  object FullTextMPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FullTextConfineTo = FullTextConfineTo.T
  object FullTextConfineTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExtractYamlResult = ExtractYamlResult.T
  object ExtractYamlResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExtractYamlText = _root_.com.histmersion.domain.library.sources.SourceNote.SplitText
  val ExtractYamlText: _root_.com.histmersion.domain.library.sources.SourceNote.SplitText.type = _root_.com.histmersion.domain.library.sources.SourceNote.SplitText
  type BodyOfResult = BodyOfResult.T
  object BodyOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BodyOfText = _root_.com.histmersion.domain.library.sources.SourceNote.SplitText
  val BodyOfText: _root_.com.histmersion.domain.library.sources.SourceNote.SplitText.type = _root_.com.histmersion.domain.library.sources.SourceNote.SplitText
  type SplitResultPart1 = SplitResultPart1.T
  object SplitResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SplitResultPart2 = SplitResultPart2.T
  object SplitResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SplitText = SplitText.T
  object SplitText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseResultPart1 = ParseResultPart1.T
  object ParseResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseYaml = ParseYaml.T
  object ParseYaml extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RawMPart1 = RawMPart1.T
  object RawMPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RawK = RawK.T
  object RawK extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StrResult = StrResult.T
  object StrResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StrMPart1 = _root_.com.histmersion.domain.library.sources.SourceNote.RawMPart1
  val StrMPart1: _root_.com.histmersion.domain.library.sources.SourceNote.RawMPart1.type = _root_.com.histmersion.domain.library.sources.SourceNote.RawMPart1
  type StrK = _root_.com.histmersion.domain.library.sources.SourceNote.RawK
  val StrK: _root_.com.histmersion.domain.library.sources.SourceNote.RawK.type = _root_.com.histmersion.domain.library.sources.SourceNote.RawK
  type ListResult = ListResult.T
  object ListResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ListMPart1 = _root_.com.histmersion.domain.library.sources.SourceNote.RawMPart1
  val ListMPart1: _root_.com.histmersion.domain.library.sources.SourceNote.RawMPart1.type = _root_.com.histmersion.domain.library.sources.SourceNote.RawMPart1
  type ListK = _root_.com.histmersion.domain.library.sources.SourceNote.RawK
  val ListK: _root_.com.histmersion.domain.library.sources.SourceNote.RawK.type = _root_.com.histmersion.domain.library.sources.SourceNote.RawK
  type IntResult = IntResult.T
  object IntResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IntMPart1 = _root_.com.histmersion.domain.library.sources.SourceNote.RawMPart1
  val IntMPart1: _root_.com.histmersion.domain.library.sources.SourceNote.RawMPart1.type = _root_.com.histmersion.domain.library.sources.SourceNote.RawMPart1
  type IntK = _root_.com.histmersion.domain.library.sources.SourceNote.RawK
  val IntK: _root_.com.histmersion.domain.library.sources.SourceNote.RawK.type = _root_.com.histmersion.domain.library.sources.SourceNote.RawK
  type LongResult = LongResult.T
  object LongResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LongMPart1 = _root_.com.histmersion.domain.library.sources.SourceNote.RawMPart1
  val LongMPart1: _root_.com.histmersion.domain.library.sources.SourceNote.RawMPart1.type = _root_.com.histmersion.domain.library.sources.SourceNote.RawMPart1
  type LongK = _root_.com.histmersion.domain.library.sources.SourceNote.RawK
  val LongK: _root_.com.histmersion.domain.library.sources.SourceNote.RawK.type = _root_.com.histmersion.domain.library.sources.SourceNote.RawK
  type DoubleResult = DoubleResult.T
  object DoubleResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DoubleMPart1 = _root_.com.histmersion.domain.library.sources.SourceNote.RawMPart1
  val DoubleMPart1: _root_.com.histmersion.domain.library.sources.SourceNote.RawMPart1.type = _root_.com.histmersion.domain.library.sources.SourceNote.RawMPart1
  type DoubleK = _root_.com.histmersion.domain.library.sources.SourceNote.RawK
  val DoubleK: _root_.com.histmersion.domain.library.sources.SourceNote.RawK.type = _root_.com.histmersion.domain.library.sources.SourceNote.RawK
  type PeriodMPart1 = _root_.com.histmersion.domain.library.sources.SourceNote.RawMPart1
  val PeriodMPart1: _root_.com.histmersion.domain.library.sources.SourceNote.RawMPart1.type = _root_.com.histmersion.domain.library.sources.SourceNote.RawMPart1
  type ToSourceMPart1 = _root_.com.histmersion.domain.library.sources.SourceNote.RawMPart1
  val ToSourceMPart1: _root_.com.histmersion.domain.library.sources.SourceNote.RawMPart1.type = _root_.com.histmersion.domain.library.sources.SourceNote.RawMPart1
  type ToSourceFallbackCitekey = ToSourceFallbackCitekey.T
  object ToSourceFallbackCitekey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FindingsMPart1 = _root_.com.histmersion.domain.library.sources.SourceNote.RawMPart1
  val FindingsMPart1: _root_.com.histmersion.domain.library.sources.SourceNote.RawMPart1.type = _root_.com.histmersion.domain.library.sources.SourceNote.RawMPart1

  def note(s: Source): com.histmersion.domain.library.sources.SourceNote.NoteResult =
    frontmatterOf(s).note(s.notes.map(_.trim).filter(_.nonEmpty))

  /** The YAML block between the `---` fences (with a trailing newline). */
  def frontmatter(s: Source): com.histmersion.domain.library.sources.SourceNote.FrontmatterResult = frontmatterOf(s).yaml

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

  private def finding(f: Finding): java.util.Map[com.histmersion.domain.library.sources.SourceNote.FindingResultPart1, Any] =
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
  def parseFile(p: Path, confineTo: Option[com.histmersion.domain.library.sources.SourceNote.ParseFileConfineTo])(using files: com.histmersion.util.storage.TextFiles): Option[Source] =
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

  private def yamlMap(text: com.histmersion.domain.library.sources.SourceNote.YamlMapText): Map[com.histmersion.domain.library.sources.SourceNote.YamlMapResultPart1, Any] = extractYaml(text).map(parse).getOrElse(Map.empty)

  private val CkLink = """\[\[([a-z][a-z0-9]*_[a-z0-9_]+)(?:\|[^\]]*)?\]\]""".r
  private val RelTag = """^(refutes|challenges|updates|supersedes)_(.+)$""".r

  /** The `[[…]]` targets of a note, split by what they name: a year-bearing target is a CITATION
    * (`Lint.looksLikeCitekey`, the rule V026 migrated the stored edges by), anything else — a topic slug like
    * `[[bronze_age_collapse]]` — is a TOPIC. Topic slugs used to become `references` edges to things that are not
    * sources.
    */
  private def linkTargets(citekey: com.histmersion.domain.library.Citekey, noteText: com.histmersion.domain.library.sources.SourceNote.LinkTargetsNoteText): (List[com.histmersion.domain.library.sources.SourceNote.LinkTargetsResultPart1], List[com.histmersion.domain.library.sources.SourceNote.LinkTargetsResultPart2]) =
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
  private def withLinkedTopics(topics: List[com.histmersion.domain.library.sources.SourceNote.WithLinkedTopicsTopics], citekey: com.histmersion.domain.library.Citekey, noteText: com.histmersion.domain.library.sources.SourceNote.WithLinkedTopicsNoteText): List[com.histmersion.domain.library.sources.SourceNote.WithLinkedTopicsResult] =
    val have = topics.map(_.toLowerCase).toSet
    topics ++ linkTargets(citekey, noteText)._2.filterNot(t => have(t.toLowerCase))

  /** Read the converted body `md/<citekey>/paper.md` (path in `md_path`, relative to the repo root = three parents up
    * from `wiki/sources/<ck>.md`). None if absent.
    */
  private def fullText(notePath: Path, m: Map[com.histmersion.domain.library.sources.SourceNote.FullTextMPart1, Any], confineTo: Option[com.histmersion.domain.library.sources.SourceNote.FullTextConfineTo])(using files: com.histmersion.util.storage.TextFiles): Option[com.histmersion.domain.library.sources.SourceNote.FullTextResult] =
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

  private def extractYaml(text: com.histmersion.domain.library.sources.SourceNote.ExtractYamlText): Option[com.histmersion.domain.library.sources.SourceNote.ExtractYamlResult] = split(text).map(_._1)

  /** The Markdown after the closing `---` fence: the note's article (`notes`). */
  private def bodyOf(text: com.histmersion.domain.library.sources.SourceNote.BodyOfText): Option[com.histmersion.domain.library.sources.SourceNote.BodyOfResult] =
    split(text).map(_._2).map(_.trim).filter(_.nonEmpty)

  /** Split a note into (yaml, body). `None` when there is no frontmatter fence at all. */
  private def split(text: com.histmersion.domain.library.sources.SourceNote.SplitText): Option[(com.histmersion.domain.library.sources.SourceNote.SplitResultPart1, com.histmersion.domain.library.sources.SourceNote.SplitResultPart2)] =
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

  private def parse(yaml: com.histmersion.domain.library.sources.SourceNote.ParseYaml): Map[com.histmersion.domain.library.sources.SourceNote.ParseResultPart1, Any] =
    Option(new Yaml().load[AnyRef](yaml)) match
      case Some(m: java.util.Map[?, ?]) => m.asScala.toMap.map((k, v) => k.toString -> v)
      case _                            => Map.empty

  // ── null-safe accessors over a decoded YAML map ─────────────────────────────────────────────
  // decoded YAML values are type-tested (E165, #879): `asMatchable` states that is intended
  private def raw(m: Map[com.histmersion.domain.library.sources.SourceNote.RawMPart1, Any], k: com.histmersion.domain.library.sources.SourceNote.RawK): Option[Matchable] =
    m.get(k).flatMap(v => Option(v)).map(_.asMatchable)
  private def str(m: Map[com.histmersion.domain.library.sources.SourceNote.StrMPart1, Any], k: com.histmersion.domain.library.sources.SourceNote.StrK): Option[com.histmersion.domain.library.sources.SourceNote.StrResult] =
    raw(m, k).map(_.toString).filter(s => s.nonEmpty && s != "null")
  private def list(m: Map[com.histmersion.domain.library.sources.SourceNote.ListMPart1, Any], k: com.histmersion.domain.library.sources.SourceNote.ListK): List[com.histmersion.domain.library.sources.SourceNote.ListResult] = raw(m, k) match
    case Some(l: java.util.List[?]) => l.asScala.toList.flatMap(x => Option(x)).map(_.toString)
    case _                          => Nil
  private def int(m: Map[com.histmersion.domain.library.sources.SourceNote.IntMPart1, Any], k: com.histmersion.domain.library.sources.SourceNote.IntK): Option[com.histmersion.domain.library.sources.SourceNote.IntResult] = raw(m, k) match
    case Some(n: java.lang.Number) => Some(n.intValue)
    case Some(o)                   => o.toString.toIntOption
    case None                      => None
  private def long(m: Map[com.histmersion.domain.library.sources.SourceNote.LongMPart1, Any], k: com.histmersion.domain.library.sources.SourceNote.LongK): Option[com.histmersion.domain.library.sources.SourceNote.LongResult] = raw(m, k) match
    case Some(n: java.lang.Number) => Some(n.longValue)
    case Some(o)                   => o.toString.toLongOption
    case None                      => None
  private def double(m: Map[com.histmersion.domain.library.sources.SourceNote.DoubleMPart1, Any], k: com.histmersion.domain.library.sources.SourceNote.DoubleK): Option[com.histmersion.domain.library.sources.SourceNote.DoubleResult] = raw(m, k) match
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

  private def toSource(m: Map[com.histmersion.domain.library.sources.SourceNote.ToSourceMPart1, Any], fallbackCitekey: com.histmersion.domain.library.sources.SourceNote.ToSourceFallbackCitekey): Source =
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
