package com.histmersion.cli

import com.monovore.decline.*
import io.circe.Encoder
import io.circe.syntax.*
import com.histmersion.util.console.Console

/** The one way a command prints its answer (#1946): the shared Markdown rendering — for people and LLMs — or, with
  * `--json`, the response as the server sent it, for scripts. Every command that offers JSON takes [[jsonFlag]] (one
  * name, one help line) and prints through [[emit]], so the JSON is always the wire DTO's own codec, pretty-printed the
  * same way, and never a hand-built object that could drift from it.
  */
object Output:
  type JsonText = JsonText.T
  object JsonText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Markdown = Markdown.T
  object Markdown extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** `--json` — the same flag, with the same help, on every command that has one. */

  val jsonFlag: Opts[com.histmersion.cli.Cli.JsonOutput] = Opts.flag("json", "Print the raw JSON response instead of Markdown").orFalse

  /** A response as `--json` prints it: the DTO's own codec, two-space indented. Pure. */
  def json[A: Encoder](a: A): com.histmersion.cli.Output.JsonText = a.asJson.spaces2

  /** What [[emit]] prints. Pure. */
  def render[A: Encoder](json: com.histmersion.cli.Cli.JsonOutput, a: A)(markdown: A => com.histmersion.cli.Output.Markdown): com.histmersion.cli.Output.Text =
    if json then Output.json(a) else markdown(a)

  /** Print `a` as JSON when `json`, else as its Markdown. */
  def emit[A: Encoder](json: com.histmersion.cli.Cli.JsonOutput, a: A)(markdown: A => com.histmersion.cli.Output.Markdown)(using console: Console) : Unit =
    Cli.out.println(render(json, a)(markdown))
