package com.histmersion.core

import org.yaml.snakeyaml.{DumperOptions, Yaml}
import scala.compiletime.asMatchable
import scala.jdk.CollectionConverters.*

/** `wiki/<kind>/<slug>.md` ⇄ [[Article]] — the same frontmatter-plus-body shape as
  * [[Export.note]] / [[Frontmatter.parseNote]], for articles.
  *
  * The fence splitting is duplicated from [[Frontmatter]] rather than shared: those helpers are
  * private, article frontmatter is a different vocabulary, and keeping the two parsers independent
  * means a change to one cannot silently reshape the other.
  *
  * JVM-only (snakeyaml); the pure text extractors `heading` and `lede` live in [[Articles]].
  */
object ArticleNote:

  /** Frontmatter key order of a rendered article note. */
  def render(a: Article): String =
    val m = new java.util.LinkedHashMap[String, Any]()
    m.put("type", a.kind)
    m.put("slug", a.slug)
    m.put("title", a.title)
    m.put("summary", a.summary.orNull)
    m.put("regions", a.regions.asJava)
    m.put("tags", a.tags.asJava)
    m.put("period_start", a.period.map(p => Long.box(p.start)).orNull)
    m.put("period_end", a.period.map(p => Long.box(p.end)).orNull)
    val body = a.body.trim
    s"---\n${dump(m)}---\n" + (if body.isEmpty then "" else s"\n$body\n")

  /** Parse a note's text. `fallbackSlug` is normally the filename, `fallbackKind` the directory it
    * came from — frontmatter `type:`/`slug:` win when present. `None` when there is no frontmatter
    * fence at all (the file is not an article note).
    *
    * The wiki's own articles carry keys this model has no column for (`status`, `generated`,
    * `updated`); they are dropped. `question:` (a report's driving question) and `topics:`/
    * `cultures:` (list dimensions) ARE kept — as the summary and as tags respectively — because they
    * are the same thing under another name. */
  def parse(text: String, fallbackSlug: String, fallbackKind: String): Option[Article] =
    split(text).map { (yaml, rawBody) =>
      val m    = parseYaml(yaml)
      val body = rawBody.trim
      val slug = str(m, "slug").getOrElse(fallbackSlug).trim.toLowerCase
      Article(
        slug = slug,
        title = str(m, "title").orElse(Articles.heading(body)).getOrElse(slug),
        kind = str(m, "type").orElse(str(m, "kind")).getOrElse(fallbackKind).trim.toLowerCase,
        body = body,
        summary = str(m, "summary").orElse(str(m, "question")).orElse(Articles.lede(body)),
        tags = (list(m, "tags") ++ list(m, "topics") ++ list(m, "cultures")).map(_.trim)
          .filter(_.nonEmpty).distinct,
        regions = list(m, "regions").map(_.trim).filter(_.nonEmpty),
        period = (long(m, "period_start"), long(m, "period_end")) match
          case (Some(a), Some(b)) if a <= b => Some(Period(a, b))
          case (Some(a), Some(b))           => Some(Period(b, a))
          case _                            => None
      )
    }

  // ── YAML plumbing (deliberately independent of Frontmatter's private copy) ────────────────────
  /** Split a note into (yaml, body); `None` when it has no opening `---` fence. */
  private def split(text: String): Option[(String, String)] =
    val t = text.stripPrefix("﻿")
    if !t.startsWith("---") then None
    else
      val rest = t.drop(3)
      val end  = rest.indexOf("\n---")
      if end < 0 then None
      else
        val after = rest.substring(end + "\n---".length)
        val body  = after.indexOf('\n') match
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

  /** Block style, no folding — the same dumper settings as [[Export]], so both mirrors diff cleanly. */
  private def dump(m: java.util.Map[String, Any]): String =
    val opts = new DumperOptions()
    opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK)
    opts.setSplitLines(false)
    new Yaml(opts).dump(m)
