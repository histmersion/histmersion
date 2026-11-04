package com.histmersion.cli

import com.monovore.decline.*
import io.circe.Encoder
import io.circe.syntax.*

/** The one way a command prints its answer (#1946): the shared Markdown rendering — for people and LLMs — or, with
  * `--json`, the response as the server sent it, for scripts. Every command that offers JSON takes [[jsonFlag]] (one
  * name, one help line) and prints through [[emit]], so the JSON is always the wire DTO's own codec, pretty-printed the
  * same way, and never a hand-built object that could drift from it.
  */
object Output:

  /** `--json` — the same flag, with the same help, on every command that has one. */
  val jsonFlag: Opts[Boolean] = Opts.flag("json", "Print the raw JSON response instead of Markdown").orFalse

  /** A response as `--json` prints it: the DTO's own codec, two-space indented. Pure. */
  def json[A: Encoder](a: A): String = a.asJson.spaces2

  /** What [[emit]] prints. Pure. */
  def render[A: Encoder](json: Boolean, a: A)(markdown: A => String): String =
    if json then Output.json(a) else markdown(a)

  /** Print `a` as JSON when `json`, else as its Markdown. */
  def emit[A: Encoder](json: Boolean, a: A)(markdown: A => String): Unit =
    com.histmersion.cli.Cli.writeLine(render(json, a)(markdown))
