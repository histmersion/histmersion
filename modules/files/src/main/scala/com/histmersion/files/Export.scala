package com.histmersion.files

import com.histmersion.api.*
import com.histmersion.api.given

import com.histmersion.util.data.mirror.Frontmatter as MirrorFrontmatter
import scala.jdk.CollectionConverters.*

/** Render a stored [[Source]] back as a `wiki/sources/<citekey>.md` note — the Markdown mirror of the database (plan
  * §1.9a `export`). The inverse of [[Frontmatter.parseNote]]: same key names and shapes as the research wiki's schema,
  * so `export` then `seed --force` round-trips.
  *
  * The FRONTMATTER carries the catalog fields; the BODY carries [[Source.notes]] — the agent-written article about the
  * source. `ocrMarkdown` (the paper's own converted text, often megabytes) is NOT exported: it lives in
  * `md/<citekey>/paper.md` and is referenced from there, exactly as today.
  */
object Export:

  /** Frontmatter key order — matches the notes the research wiki already writes. */
  def note(s: Source): String =
    frontmatterOf(s).note(s.notes.map(_.trim).filter(_.nonEmpty))

  /** The YAML block between the `---` fences (with a trailing newline). */
  def frontmatter(s: Source): String = frontmatterOf(s).yaml

  private def frontmatterOf(s: Source): MirrorFrontmatter =
    MirrorFrontmatter(
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
