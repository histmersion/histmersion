package com.histmersion
package util.mcp
import com.histmersion.util.json.JsonCodec


/** How an MCP tool's arguments decode: a field the model leaves out takes its Scala default (without it every
  * non-`Option` field is required, so `get_source {"citekey":"x"}` was refused for a missing `fullText`), and an
  * argument the tool does not know is refused.
  */
object ToolInputs:
  given unknown: JsonCodec.Unknown = JsonCodec.Unknown.Refused
