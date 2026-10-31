package com.histmersion.util.data.mirror

import org.yaml.snakeyaml.{DumperOptions, Yaml}

/** A Markdown mirror note's YAML frontmatter: its keys in the order they are written (a value is a plain or Java value
  * snakeyaml writes; `null` writes `null`), and the note it heads.
  */
final case class Frontmatter(fields: List[(String, Any)]):

  /** The YAML block between the `---` fences, with a trailing newline.
    *
    * Block style, no line folding: folding a long scalar is lossless for YAML but makes diffs noisy, and
    * `git`-friendliness is the point of the mirror. `prettyFlow` is deliberately OFF so an empty list stays `[]` on one
    * line, as the research wiki's own notes write it. A fresh Yaml per call — it is not thread-safe.
    */
  def yaml: String =
    val m = new java.util.LinkedHashMap[String, Any]()
    fields.foreach((k, v) => m.put(k, v))
    val opts = new DumperOptions()
    opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK)
    opts.setSplitLines(false)
    new Yaml(opts).dump(m)

  /** The whole note: the fenced frontmatter, then the body after a blank line when there is one. */
  def note(body: Option[String]): String = s"---\n$yaml---\n" + body.fold("")(b => s"\n$b\n")
