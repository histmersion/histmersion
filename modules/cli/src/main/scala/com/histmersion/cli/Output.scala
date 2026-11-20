package com.histmersion
package cli
import com.histmersion.util.json.RawJson.*
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec

import com.histmersion.util.console.Console

/** The one way a command prints its answer (#1946): the shared Markdown rendering — for people and LLMs — or, with
  * `--json`, the response as the server sent it, for scripts. Every command that offers JSON takes `--json` (one name,
  * one help line) and prints through [[emit]], so the JSON is always the wire DTO's own codec, pretty-printed the same
  * way, and never a hand-built object that could drift from it.
  */
object Output:
  /** `--json` — the same flag, with the same help, on every command that has one. */

  val JsonHelp: String = "Print the raw JSON response instead of Markdown"

  /** `--json` on a command whose answer is text only (G112): its failures are JSON. */
  val FailureJsonHelp: String =
    "Print a failure as JSON ({\"problems\": [...]}, as the API answers it); the answer itself is text"

  /** A response as `--json` prints it: the DTO's own codec, two-space indented. Pure. */
  def json[A: JsonValueCodec](a: A): String = a.asJson.spaces2

  /** What [[emit]] prints. Pure. */
  def render[A: JsonValueCodec](json: Cli.JsonOutput, a: A)(markdown: A => String): String =
    if json.value then Output.json(a) else markdown(a)

  /** Print `a` as JSON when `json`, else as its Markdown. */
  def emit[A: JsonValueCodec](json: Cli.JsonOutput, a: A)(markdown: A => String)(using console: Console): Unit =
    Cli.out.println(render(json, a)(markdown))
