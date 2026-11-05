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

  /** `--json` — the same flag, with the same help, on every command that has one. */
  type JsonFlag = JsonFlag.T
  object JsonFlag extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JsonResult = JsonResult.T
  object JsonResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenderResult = RenderResult.T
  object RenderResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenderJson = RenderJson.T
  object RenderJson extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenderMarkdownResult = RenderMarkdownResult.T
  object RenderMarkdownResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EmitJson = EmitJson.T
  object EmitJson extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EmitMarkdownResult = EmitMarkdownResult.T
  object EmitMarkdownResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val jsonFlag: Opts[com.histmersion.cli.Output.JsonFlag] = Opts.flag("json", "Print the raw JSON response instead of Markdown").orFalse

  /** A response as `--json` prints it: the DTO's own codec, two-space indented. Pure. */
  def json[A: Encoder](a: A): com.histmersion.cli.Output.JsonResult = a.asJson.spaces2

  /** What [[emit]] prints. Pure. */
  def render[A: Encoder](json: com.histmersion.cli.Output.RenderJson, a: A)(markdown: A => com.histmersion.cli.Output.RenderMarkdownResult): com.histmersion.cli.Output.RenderResult =
    if json then Output.json(a) else markdown(a)

  /** Print `a` as JSON when `json`, else as its Markdown. */
  def emit[A: Encoder](json: com.histmersion.cli.Output.EmitJson, a: A)(markdown: A => com.histmersion.cli.Output.EmitMarkdownResult)(using console: Console) : Unit =
    Cli.out.println(render(json, a)(markdown))
