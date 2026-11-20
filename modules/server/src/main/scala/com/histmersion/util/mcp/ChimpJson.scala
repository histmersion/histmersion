package com.histmersion
package util.mcp

import chimp.server.{NoStructuredOutput, PartialTool, ServerContext, ServerTool, ToolResult}
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import hearth.kindlings.jsoniterjson.Json
import io.circe
import sttp.shared.Identity

import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*

/** Chimp, the MCP server library, reads a tool's arguments with circe; our records have jsoniter codecs. This is the
  * one place the two meet: a tool's input is read through its record's own codec, and a tool that takes raw JSON gets
  * Kindlings' AST.
  */
object ChimpJson:
  /** A record's arguments, read by its own codec; a refusal names where in the arguments it is (`.article`). */
  given decoder: [A] => (codec: JsonValueCodec[A]) => circe.Decoder[A] = circe.Decoder.instance { cursor =>
    RawJson.decodeAt[A](cursor.value.noSpaces).left.map { (at, why) =>
      val ops = at.flatMap {
        case Left(key)    => List(circe.CursorOp.DownField(key))
        case Right(index) => circe.CursorOp.DownArray :: List.fill(index)(circe.CursorOp.MoveRight)
      }
      circe.DecodingFailure(why, ops.reverse ++ cursor.history)
    }
  }

  /** `decoder`, refusing arguments whose JSON holds more characters than `fits` takes, before anything is decoded. */
  def bounded[A](decoder: circe.Decoder[A], fits: Int => Boolean): circe.Decoder[A] =
    circe.Decoder.instance { cursor =>
      val chars = cursor.value.noSpaces.length
      if fits(chars) then decoder(cursor)
      else
        Left(circe.DecodingFailure(s"the arguments hold $chars characters, more than the server takes", cursor.history))
    }

  extension [A](value: A)(using codec: JsonValueCodec[A])
    /** The value as a tool result's structured content, written by its own codec. */
    def structured: circe.Json = circe.parser.parse(value.asJson.noSpaces).getOrElse(circe.Json.Null)

  /** A tool whose arguments are any JSON object of `schema`. */
  def rawTool(tool: PartialTool, schema: Json)(
      handle: Json => ToolResult[NoStructuredOutput]
  ): ServerTool[circe.Json, NoStructuredOutput, Identity, ServerContext[Identity]] =
    tool.inputJson(circe.parser.parse(schema.noSpaces).fold(e => throw e, identity)).handle { arguments =>
      RawJson.parse(arguments.noSpaces).fold(e => ToolResult.error(s"arguments are not JSON: $e"), handle)
    }
