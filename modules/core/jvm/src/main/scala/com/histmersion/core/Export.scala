package com.histmersion.core

import org.yaml.snakeyaml.{DumperOptions, Yaml}
import scala.jdk.CollectionConverters.*

/** Render a stored [[Source]] back as a `wiki/sources/<citekey>.md` note — the Markdown mirror of the
  * database (plan §1.9a `export`). The inverse of [[Frontmatter.parseNote]]: same key names and shapes
  * as the research wiki's schema, so `export` then `seed --force` round-trips.
  *
  * The FRONTMATTER carries the catalog fields; the BODY carries [[Source.notes]] — the agent-written
  * article about the source. `ocrMarkdown` (the paper's own converted text, often megabytes) is NOT
  * exported: it lives in `md/<citekey>/paper.md` and is referenced from there, exactly as today. */
object Export:

  /** Frontmatter key order — matches the notes the research wiki already writes. */
  def note(s: Source): String =
    val body = s.notes.map(_.trim).filter(_.nonEmpty)
    s"---\n${frontmatter(s)}---\n" + body.fold("")(b => s"\n$b\n")

  /** The YAML block between the `---` fences (with a trailing newline). */
  def frontmatter(s: Source): String =
    val m = new java.util.LinkedHashMap[String, Any]()
    m.put("citekey", s.citekey)
    m.put("doi", s.doi.orNull)
    m.put("title", s.title)
    m.put("authors", s.authors.asJava)
    m.put("year", s.year.map(Int.box).orNull)
    m.put("venue", s.venue.orNull)
    m.put("source", s.provenance.orNull)
    m.put("calibre_id", s.calibreId.orNull)
    m.put("status", s.status.toString.toLowerCase)
    // `superseded_by` is stored as an edge; Frontmatter turns this key back into that edge.
    m.put("superseded_by", s.edges.find(_.kind == "superseded_by").map(_.dst).orNull)
    m.put("topics", s.topics.asJava)
    m.put("regions", s.regions.asJava)
    m.put("tags", s.tags.asJava)
    m.put("period_start", s.period.map(p => Long.box(p.start)).orNull)
    m.put("period_end", s.period.map(p => Long.box(p.end)).orNull)
    m.put("cited_by_count", s.citedByCount.map(Int.box).orNull)
    m.put("influential_citations", s.influentialCitations.map(Int.box).orNull)
    m.put("summary", s.summary.orNull)
    m.put("findings", s.findings.map(finding).asJava)
    dump(m)

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

  /** Block style, no line folding: folding a long scalar is lossless for YAML but makes diffs noisy,
    * and `git`-friendliness is the point of the export. `prettyFlow` is deliberately OFF so an empty
    * list stays `[]` on one line, as the research wiki's own notes write it. A fresh Yaml per call —
    * it is not thread-safe. */
  private def dump(m: java.util.Map[String, Any]): String =
    val opts = new DumperOptions()
    opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK)
    opts.setSplitLines(false)
    new Yaml(opts).dump(m)
