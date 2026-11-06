package com.histmersion.domain.research.articles
import com.histmersion.api.given

import com.histmersion.util.data.mirror.Frontmatter as MirrorFrontmatter
import org.yaml.snakeyaml.Yaml
import scala.compiletime.asMatchable
import scala.jdk.CollectionConverters.*
import com.histmersion.api.catalogue.Period
import com.histmersion.api.research.{Article, Articles}

/** `wiki/<kind>/<slug>.md` ⇄ [[Article]] — the same frontmatter-plus-body shape as [[Export.note]] /
  * [[Frontmatter.parseNote]], for articles.
  *
  * The fence splitting is duplicated from [[Frontmatter]] rather than shared: those helpers are private, article
  * frontmatter is a different vocabulary, and keeping the two parsers independent means a change to one cannot silently
  * reshape the other.
  *
  * JVM-only (snakeyaml); the pure text extractors `heading` and `lede` live in [[Articles]].
  */
object ArticleNote:

  /** Frontmatter key order of a rendered article note. `generated` (#958) is the coverage sections code renders from
    * the filter; they go after the body between [[ArticleCoverage.GeneratedStart]] and
    * [[ArticleCoverage.GeneratedEnd]], which [[parse]] drops again — the stored body never holds them, and the filter
    * and reasons they come from travel in the frontmatter.
    */
  type FilterMapLstK = FilterMapLstK.T
  object FilterMapLstK extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FilterMapLstXs = FilterMapLstXs.T
  object FilterMapLstXs extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type RenderResult = RenderResult.T
  object RenderResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenderGenerated = RenderGenerated.T
  object RenderGenerated extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FilterMapResultPart1 = FilterMapResultPart1.T
  object FilterMapResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseText = _root_.com.histmersion.domain.research.articles.ArticleNote.SplitText
  val ParseText: _root_.com.histmersion.domain.research.articles.ArticleNote.SplitText.type = _root_.com.histmersion.domain.research.articles.ArticleNote.SplitText
  type ParseFallbackSlug = ParseFallbackSlug.T
  object ParseFallbackSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseFallbackKind = ParseFallbackKind.T
  object ParseFallbackKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SplitResultPart1 = SplitResultPart1.T
  object SplitResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SplitResultPart2 = SplitResultPart2.T
  object SplitResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SplitText = SplitText.T
  object SplitText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseYamlResultPart1 = ParseYamlResultPart1.T
  object ParseYamlResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseYamlYaml = ParseYamlYaml.T
  object ParseYamlYaml extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RawMPart1 = RawMPart1.T
  object RawMPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RawK = RawK.T
  object RawK extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StrResult = StrResult.T
  object StrResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StrMPart1 = _root_.com.histmersion.domain.research.articles.ArticleNote.RawMPart1
  val StrMPart1: _root_.com.histmersion.domain.research.articles.ArticleNote.RawMPart1.type = _root_.com.histmersion.domain.research.articles.ArticleNote.RawMPart1
  type StrK = _root_.com.histmersion.domain.research.articles.ArticleNote.RawK
  val StrK: _root_.com.histmersion.domain.research.articles.ArticleNote.RawK.type = _root_.com.histmersion.domain.research.articles.ArticleNote.RawK
  type ListResult = ListResult.T
  object ListResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ListMPart1 = _root_.com.histmersion.domain.research.articles.ArticleNote.RawMPart1
  val ListMPart1: _root_.com.histmersion.domain.research.articles.ArticleNote.RawMPart1.type = _root_.com.histmersion.domain.research.articles.ArticleNote.RawMPart1
  type ListK = _root_.com.histmersion.domain.research.articles.ArticleNote.RawK
  val ListK: _root_.com.histmersion.domain.research.articles.ArticleNote.RawK.type = _root_.com.histmersion.domain.research.articles.ArticleNote.RawK
  type LongResult = LongResult.T
  object LongResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LongMPart1 = _root_.com.histmersion.domain.research.articles.ArticleNote.RawMPart1
  val LongMPart1: _root_.com.histmersion.domain.research.articles.ArticleNote.RawMPart1.type = _root_.com.histmersion.domain.research.articles.ArticleNote.RawMPart1
  type LongK = _root_.com.histmersion.domain.research.articles.ArticleNote.RawK
  val LongK: _root_.com.histmersion.domain.research.articles.ArticleNote.RawK.type = _root_.com.histmersion.domain.research.articles.ArticleNote.RawK

  def render(a: Article, generated: Option[com.histmersion.domain.research.articles.ArticleNote.RenderGenerated] = None): com.histmersion.domain.research.articles.ArticleNote.RenderResult =
    val reasons = Option.when(a.reasons.nonEmpty) {
      val r = new java.util.LinkedHashMap[String, Any]()
      a.reasons.toList.sortBy(_._1).foreach((k, v) => r.put(k, v))
      "reasons" -> r
    }
    val fields = List(
      "type" -> a.kind,
      "slug" -> a.slug,
      "title" -> a.title,
      "summary" -> a.summary.orNull,
      "regions" -> a.regions.asJava,
      "tags" -> a.tags.asJava,
      "period_start" -> a.period.map(p => Long.box(p.start)).orNull,
      "period_end" -> a.period.map(p => Long.box(p.end)).orNull
    ) ++ a.filter.map(f => "filter" -> filterMap(f)).toList ++ reasons.toList
    val body = (a.body.trim :: generated.map(_.trim).filter(_.nonEmpty).toList).filter(_.nonEmpty).mkString("\n\n")
    MirrorFrontmatter(fields).note(Option.when(body.nonEmpty)(body))

  /** The filter as a frontmatter map: only the parts it has, in a fixed order. */
  private def filterMap(f: Article.Filter): java.util.Map[com.histmersion.domain.research.articles.ArticleNote.FilterMapResultPart1, Any] =
    val m = new java.util.LinkedHashMap[String, Any]()
    f.query.foreach(m.put("query", _))
    def lst(k: com.histmersion.domain.research.articles.ArticleNote.FilterMapLstK, xs: List[com.histmersion.domain.research.articles.ArticleNote.FilterMapLstXs]) = if xs.nonEmpty then m.put(k, xs.asJava)
    lst("topics", f.topics); lst("tags", f.tags); lst("status", f.status); lst("places", f.places)
    lst("authors", f.authors)
    f.from.foreach(v => m.put("from", Long.box(v)))
    f.to.foreach(v => m.put("to", Long.box(v)))
    lst("extra_queries", f.extraQueries)
    m

  /** Parse a note's text. `fallbackSlug` is normally the filename, `fallbackKind` the directory it came from —
    * frontmatter `type:`/`slug:` win when present. `None` when there is no frontmatter fence at all (the file is not an
    * article note).
    *
    * The wiki's own articles carry keys this model has no column for (`status`, `generated`, `updated`); they are
    * dropped. `question:` (a report's driving question) and `topics:`/ `cultures:` (list dimensions) ARE kept — as the
    * summary and as tags respectively — because they are the same thing under another name.
    */
  def parse(text: com.histmersion.domain.research.articles.ArticleNote.ParseText, fallbackSlug: com.histmersion.domain.research.articles.ArticleNote.ParseFallbackSlug, fallbackKind: com.histmersion.domain.research.articles.ArticleNote.ParseFallbackKind): Option[Article] =
    split(text).map { (yaml, rawBody) =>
      val m = parseYaml(yaml)
      val body = Article.Coverage.stripGenerated(rawBody).trim
      val slug = str(m, "slug").getOrElse(fallbackSlug).trim.toLowerCase
      Article(
        slug = slug,
        title = str(m, "title").orElse(Articles.heading(body)).getOrElse(slug),
        kind = str(m, "type").orElse(str(m, "kind")).getOrElse(fallbackKind).trim.toLowerCase,
        body = body,
        summary = str(m, "summary").orElse(str(m, "question")).orElse(Articles.lede(body)),
        tags = (list(m, "tags") ++ list(m, "topics") ++ list(m, "cultures"))
          .map(_.trim)
          .filter(_.nonEmpty)
          .distinct,
        regions = list(m, "regions").map(_.trim).filter(_.nonEmpty),
        period = (long(m, "period_start"), long(m, "period_end")) match
          case (Some(a), Some(b)) if a <= b => Some(Period(a, b))
          case (Some(a), Some(b))           => Some(Period(b, a))
          case _                            => None,
        filter = raw(m, "filter")
          .collect { case f: java.util.Map[?, ?] => f.asScala.toMap.map((k, v) => k.toString -> v) }
          .flatMap(f =>
            Article.Filter
              .normalise(
                Article.Filter(
                  str(f, "query"),
                  list(f, "topics"),
                  list(f, "tags"),
                  list(f, "status"),
                  list(f, "places"),
                  list(f, "authors"),
                  long(f, "from"),
                  long(f, "to"),
                  list(f, "extra_queries")
                )
              )
              .toOption
              .flatten
          ),
        reasons = raw(m, "reasons")
          .collect { case r: java.util.Map[?, ?] =>
            r.asScala.toList
              .flatMap((k, v) => Option(v).map(x => k.toString.trim.toLowerCase -> x.toString.trim))
              .filter((k, v) => k.nonEmpty && v.nonEmpty)
              .toMap
          }
          .getOrElse(Map.empty)
      )
    }

  // ── YAML plumbing (deliberately independent of Frontmatter's private copy) ────────────────────
  /** Split a note into (yaml, body); `None` when it has no opening `---` fence. */
  private def split(text: com.histmersion.domain.research.articles.ArticleNote.SplitText): Option[(com.histmersion.domain.research.articles.ArticleNote.SplitResultPart1, com.histmersion.domain.research.articles.ArticleNote.SplitResultPart2)] =
    val t = text.stripPrefix("﻿")
    if !t.startsWith("---") then None
    else
      val rest = t.drop(3)
      val end = rest.indexOf("\n---")
      if end < 0 then None
      else
        val after = rest.substring(end + "\n---".length)
        val body = after.indexOf('\n') match
          case -1 => ""
          case i  => after.substring(i + 1)
        Some((rest.substring(0, end), body))

  private def parseYaml(yaml: com.histmersion.domain.research.articles.ArticleNote.ParseYamlYaml): Map[com.histmersion.domain.research.articles.ArticleNote.ParseYamlResultPart1, Any] =
    Option(new Yaml().load[AnyRef](yaml)) match
      case Some(m: java.util.Map[?, ?]) => m.asScala.toMap.map((k, v) => k.toString -> v)
      case _                            => Map.empty

  // decoded YAML values are type-tested (E165, #879): `asMatchable` states that is intended
  private def raw(m: Map[com.histmersion.domain.research.articles.ArticleNote.RawMPart1, Any], k: com.histmersion.domain.research.articles.ArticleNote.RawK): Option[Matchable] =
    m.get(k).flatMap(v => Option(v)).map(_.asMatchable)
  private def str(m: Map[com.histmersion.domain.research.articles.ArticleNote.StrMPart1, Any], k: com.histmersion.domain.research.articles.ArticleNote.StrK): Option[com.histmersion.domain.research.articles.ArticleNote.StrResult] =
    raw(m, k).map(_.toString).map(_.trim).filter(s => s.nonEmpty && s != "null")
  private def list(m: Map[com.histmersion.domain.research.articles.ArticleNote.ListMPart1, Any], k: com.histmersion.domain.research.articles.ArticleNote.ListK): List[com.histmersion.domain.research.articles.ArticleNote.ListResult] = raw(m, k) match
    case Some(l: java.util.List[?]) => l.asScala.toList.flatMap(x => Option(x)).map(_.toString)
    case Some(s: String)            => s.split(",").toList.map(_.trim).filter(_.nonEmpty)
    case _                          => Nil
  private def long(m: Map[com.histmersion.domain.research.articles.ArticleNote.LongMPart1, Any], k: com.histmersion.domain.research.articles.ArticleNote.LongK): Option[com.histmersion.domain.research.articles.ArticleNote.LongResult] = raw(m, k) match
    case Some(n: java.lang.Number) => Some(n.longValue)
    case Some(o)                   => o.toString.toLongOption
    case None                      => None
