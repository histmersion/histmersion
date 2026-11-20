package com.histmersion
package util.mcp

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

/** The one size cap at the `/mcp` edge (`histmersion.mcp`): what a tool call may send and what it may answer, for every
  * tool at once. A tool's own paging (`get_source`'s `offset`/`chars`, `query`'s rows) stays; this cap is what holds
  * when a tool has none.
  *
  * @param resultMaxChars
  *   the characters of a tool's answer; a longer one is cut at a line and ends with a trailer saying how much was cut
  *   and how to read on. 0 = no cap.
  * @param argumentsMaxChars
  *   the characters of a call's arguments (a base64 upload to `ingest` included); a longer call is refused before the
  *   tool runs. 0 = no cap.
  */
final case class McpConfig(resultMaxChars: McpConfig.ResultMaxChars, argumentsMaxChars: McpConfig.ArgumentsMaxChars)
    derives ConfigReader

object McpConfig:
  type ResultMaxChars = ResultMaxChars.T
  object ResultMaxChars extends Newtype[Int, Pure]:
    extension (most: ResultMaxChars)
      /** Does an answer of `chars` characters fit? Everything fits under no cap (0). */
      def fits(chars: Int): Boolean = most.value <= 0 || chars <= most.value

      /** `text` within the cap: cut at the last line break that keeps at least half of what fits (else mid-line), and
        * closed with a trailer saying what was cut and `readOn`, how to read the rest. Text that fits is returned as it
        * is. Pure.
        */
      def cut(text: String, readOn: String): String =
        if fits(text.length) then text
        else
          val room = math.max(1, most.value - 160 - readOn.length)
          val nl = text.lastIndexOf('\n', room)
          val at = if nl >= room / 2 then nl else room
          text.substring(0, at).stripTrailing + s"\n\n_…cut at $at of ${text.length} characters — ${readOn}_"

  type ArgumentsMaxChars = ArgumentsMaxChars.T
  object ArgumentsMaxChars extends Newtype[Int, Pure]:
    extension (most: ArgumentsMaxChars)
      /** Do arguments of `chars` characters fit? Everything fits under no cap (0). */
      def fits(chars: Int): Boolean = most.value <= 0 || chars <= most.value
