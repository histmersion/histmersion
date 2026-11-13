package com.histmersion
package util.mcp

import chimp.server.{NoStructuredOutput, PartialTool, ServerContext, ServerTool, ToolResult}
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import hearth.kindlings.jsoniterjson.Json
import io.circe
import sttp.shared.Identity

import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*

/** Chimp, the MCP server library, reads a tool's arguments with circe; our records have jsoniter codecs. This is the one
  * place the two meet: a tool's input is read through its record's own codec, and a tool that takes raw JSON gets
  * Kindlings' AST.
  */
object ChimpJson:
  /** A record's arguments, read by its own codec. */
  given decoder: [A] => (codec: JsonValueCodec[A]) => circe.Decoder[A] =
    circe.Decoder.decodeJson.emap(json => RawJson.decode[A](json.noSpaces))

  /** A tool whose arguments are any JSON object of `schema`. */
  def rawTool(tool: PartialTool, schema: Json)(
      handle: Json => ToolResult[NoStructuredOutput]
  ): ServerTool[circe.Json, NoStructuredOutput, Identity, ServerContext[Identity]] =
    tool.inputJson(circe.parser.parse(schema.noSpaces).fold(e => throw e, identity)).handle { arguments =>
      RawJson.parse(arguments.noSpaces).fold(e => ToolResult.error(s"arguments are not JSON: $e"), handle)
    }
