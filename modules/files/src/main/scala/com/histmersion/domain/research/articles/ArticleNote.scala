package com.histmersion
package domain.research.articles

import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentence

import com.histmersion.api.given

import com.histmersion.util.data.mirror.Frontmatter as MirrorFrontmatter
import org.yaml.snakeyaml.Yaml
import scala.compiletime.asMatchable
import scala.jdk.CollectionConverters.*
import com.histmersion.api.catalogue.{Period, Year}
import com.histmersion.api.library.Citekey
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

  def render(a: Article, generated: Option[String] = None): String =
    val reasons = Option.when(a.reasons.nonEmpty) {
      val r = new java.util.LinkedHashMap[String, Any]()
      a.reasons.toList.sortBy(_._1.value).foreach((k, v) => r.put(k.value, v.sentence))
      "reasons" -> r
    }
    val fields = List(
      "type" -> a.kind.value,
      "slug" -> a.slug.value,
      "title" -> a.title.value,
      "summary" -> a.summary.map(_.value).orNull,
      "regions" -> a.regions.map(_.value).asJava,
      "tags" -> a.tags.map(_.value).asJava,
      "period_start" -> a.period.map(p => Long.box(p.start.value)).orNull,
      "period_end" -> a.period.map(p => Long.box(p.end.value)).orNull
    ) ++ a.filter.map(f => "filter" -> filterMap(f)).toList ++ reasons.toList
    val body =
      (a.body.value.trim :: generated.map(_.trim).filter(_.nonEmpty).toList).filter(_.nonEmpty).mkString("\n\n")
    MirrorFrontmatter(fields.map((k, v) => MirrorFrontmatter.Key(k) -> v))
      .note(Option.when(body.nonEmpty)(MirrorFrontmatter.NoteBody(body)))
      .value

  /** The filter as a frontmatter map: only the parts it has, in a fixed order. */
  private def filterMap(f: Article.Filter): java.util.Map[String, Any] =
    val m = new java.util.LinkedHashMap[String, Any]()
    f.query.foreach(m.put("query", _))
    def lst(k: String, xs: List[String]) = if xs.nonEmpty then m.put(k, xs.asJava)
    lst("topics", f.topics.map(_.value)); lst("tags", f.tags.map(_.value)); lst("status", f.status.map(_.value))
    lst("places", f.places.map(_.value))
    lst("authors", f.authors.map(_.value))
    f.from.foreach(v => m.put("from", Long.box(v.value)))
    f.to.foreach(v => m.put("to", Long.box(v.value)))
    lst("extra_queries", f.extraQueries.map(_.value))
    m

  /** Parse a note's text. `fallbackSlug` is normally the filename, `fallbackKind` the directory it came from —
    * frontmatter `type:`/`slug:` win when present. `None` when there is no frontmatter fence at all (the file is not an
    * article note).
    *
    * The wiki's own articles carry keys this model has no column for (`status`, `generated`, `updated`); they are
    * dropped. `question:` (a report's driving question) and `topics:`/ `cultures:` (list dimensions) ARE kept — as the
    * summary and as tags respectively — because they are the same thing under another name.
    */
  def parse(text: String, fallbackSlug: Article.Slug, fallbackKind: Article.KindValue): Option[Article] =
    split(text).map { (yaml, rawBody) =>
      val m = parseYaml(yaml)
      val body = Article.Body(Article.Coverage.stripGenerated(Article.Body(rawBody)).value.trim)
      val slug = str(m, "slug").getOrElse(fallbackSlug.value).trim.toLowerCase
      Article(
        slug = Article.Slug(slug),
        title = Article.Title(str(m, "title").orElse(Articles.heading(body).map(_.value)).getOrElse(slug)),
        kind = Article.KindValue(
          str(m, "type").orElse(str(m, "kind")).getOrElse(fallbackKind.value).trim.toLowerCase
        ),
        body = body,
        summary = str(m, "summary")
          .orElse(str(m, "question"))
          .orElse(Articles.lede(body).map(_.value))
          .map(Article.SummaryText(_)),
        tags = (list(m, "tags") ++ list(m, "topics") ++ list(m, "cultures"))
          .map(_.trim)
          .filter(_.nonEmpty)
          .distinct
          .map(Article.Tags(_)),
        regions = list(m, "regions").map(_.trim).filter(_.nonEmpty).map(Article.Regions(_)),
        period = (long(m, "period_start"), long(m, "period_end")) match
          case (Some(a), Some(b)) if a <= b => Some(Period(Year(a), Year(b)))
          case (Some(a), Some(b))           => Some(Period(Year(b), Year(a)))
          case _                            => None,
        filter = raw(m, "filter")
          .collect { case f: java.util.Map[?, ?] => f.asScala.toMap.map((k, v) => k.toString -> v) }
          .flatMap(f =>
            Article.Filter
              .normalise(
                Article.Filter(
                  str(f, "query").map(Article.Filter.Query(_)),
                  list(f, "topics").map(Article.Filter.Topics(_)),
                  list(f, "tags").map(Article.Filter.Tags(_)),
                  list(f, "status").map(Article.Filter.Status(_)),
                  list(f, "places").map(Article.Filter.Places(_)),
                  list(f, "authors").map(Article.Filter.Authors(_)),
                  long(f, "from").map(Article.Filter.From(_)),
                  long(f, "to").map(Article.Filter.To(_)),
                  list(f, "extra_queries").map(Article.Filter.ExtraQueries(_))
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
              .map((k, v) => Citekey(k) -> Problem.Unclassified(Legacy.Text(v)))
              .toMap
          }
          .getOrElse(Map.empty)
      )
    }

  // ── YAML plumbing (deliberately independent of Frontmatter's private copy) ────────────────────
  /** Split a note into (yaml, body); `None` when it has no opening `---` fence. */
  private def split(text: String): Option[(String, String)] =
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

  private def parseYaml(yaml: String): Map[String, Any] =
    Option(new Yaml().load[AnyRef](yaml)) match
      case Some(m: java.util.Map[?, ?]) => m.asScala.toMap.map((k, v) => k.toString -> v)
      case _                            => Map.empty

  // decoded YAML values are type-tested (E165, #879): `asMatchable` states that is intended
  private def raw(m: Map[String, Any], k: String): Option[Matchable] =
    m.get(k).flatMap(v => Option(v)).map(_.asMatchable)
  private def str(m: Map[String, Any], k: String): Option[String] =
    raw(m, k).map(_.toString).map(_.trim).filter(s => s.nonEmpty && s != "null")
  private def list(m: Map[String, Any], k: String): List[String] = raw(m, k) match
    case Some(l: java.util.List[?]) => l.asScala.toList.flatMap(x => Option(x)).map(_.toString)
    case Some(s: String)            => s.split(",").toList.map(_.trim).filter(_.nonEmpty)
    case _                          => Nil
  private def long(m: Map[String, Any], k: String): Option[Long] = raw(m, k) match
    case Some(n: java.lang.Number) => Some(n.longValue)
    case Some(o)                   => o.toString.toLongOption
    case None                      => None
