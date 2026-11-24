package com.histmersion
package domain.automation.agents

import com.github.plokhotnyuk.jsoniter_scala.core.{writeToString, JsonValueCodec}
import hearth.kindlings.jsoniterjson.Json
import hearth.kindlings.tapiropenapijsoniter.OpenApiJsoniter
import io.circe
import sttp.ai.core.agent.{AgentTool, ResponseSchema}
import sttp.apispec.Schema

import com.histmersion.util.json.RawJson
import com.histmersion.util.problem.{External, Problem}
import com.histmersion.util.json.RawJson.*
import com.histmersion.lint.{BoundaryKind, atBoundary}

/** sttp-ai, the agent loop's library, takes circe codecs for a tool's input and a step's typed answer; our records have
  * jsoniter codecs. This is the one place the two meet: sttp-ai gets each record's own codec as circe's, its JSON
  * Schemas are written by Kindlings' apispec codecs (what apispec's circe module wrote, byte for byte), and its
  * decoding failures are read here.
  */
@atBoundary(BoundaryKind.Json, "the one place sttp-ai's circe codecs and JSON Schemas meet ours")
object SttpAiJson:
  /** A record's own codec, as sttp-ai takes it. A failure names where in the answer it is (`.findings[0].quote`), which
    * the step's retry prompt shows the model.
    */
  given codec: [A] => (c: JsonValueCodec[A]) => circe.Codec[A] = circe.Codec.from(
    circe.Decoder.instance { cursor =>
      RawJson.decodeAt[A](cursor.value.noSpaces).left.map { (at, why) =>
        // a required field the record read without is named in the message: it goes on the path, as circe put it
        val (steps, reason) = why match
          case Missing(field) => (at :+ Left(field), "missing required field")
          case _              => (at, why)
        // circe writes an index as entering the array and moving right (`.findings[0]`)
        val ops = steps.flatMap {
          case Left(key)    => List(circe.CursorOp.DownField(key))
          case Right(index) => circe.CursorOp.DownArray :: List.fill(index)(circe.CursorOp.MoveRight)
        }
        circe.DecodingFailure(reason, ops.reverse ++ cursor.history)
      }
    },
    circe.Encoder.instance(a => circe.parser.parse(writeToString(a)).fold(e => throw e, identity))
  )

  /** Kindlings' wording of a required field the answer left out (jsoniter-scala's). */
  private val Missing = """missing required field "(.+)"""".r

  /** A JSON Schema as JSON, `null` fields left out. */
  def schema(s: Schema): Json =
    RawJson
      .parse(writeToString(s)(using OpenApiJsoniter.openapi_3_1.schemaCodec))
      .fold(e => throw IllegalStateException(e), identity)
      .withoutNulls

  /** A tool's input schema. */
  def inputSchema(t: AgentTool[?, ?]): Json = schema(t.jsonSchema)

  /** A step's answer schema. */
  def answerSchema(s: ResponseSchema[?]): Json = schema(s.schema)

  /** A tool's arguments, read by the tool's own codec. */
  def arguments[T](t: AgentTool[?, T], json: Json): Either[String, T] =
    circe.parser.parse(json.noSpaces).flatMap(t.codec.decodeJson).left.map(_.getMessage)

  /** A model's answer as the step's typed answer, or why it is not one (sttp-ai's decoding failure). */
  def answer[Out](schema: ResponseSchema[Out], json: Json): Either[Throwable, Out] =
    circe.parser.parse(json.noSpaces).flatMap(schema.codec.decodeJson)

  /** A typed answer as JSON, as the step's own codec writes it (what its validation and checkpoint see). */
  def encoded[Out](schema: ResponseSchema[Out], out: Out): Json =
    RawJson.parse(schema.codec(out).noSpaces).fold(e => throw IllegalStateException(e), identity)

  /** Why an answer did not decode, when the decoder said: where (the JSON path) and what, or that it is not JSON. */
  def decodingFailure(cause: Throwable): Option[Problem.Content.Decoding] = cause match
    case d: circe.DecodingFailure =>
      val path = circe.CursorOp.opsToPath(d.history)
      Some(
        Problem.Content.Decoding.Mismatch(
          Problem.Raw(if path.isEmpty then "the top level" else path),
          External.Reported.of(d.message)
        )
      )
    case p: circe.ParsingFailure => Some(Problem.Content.Decoding.NotJson(External.Reported.of(p.message)))
    case n: AnswerNotJson        => Some(Problem.Content.Decoding.NotJson(External.Reported.of(n.message)))
    case _                       => None
