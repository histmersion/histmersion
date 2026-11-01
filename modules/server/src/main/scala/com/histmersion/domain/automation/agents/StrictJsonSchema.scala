package com.histmersion.domain.automation.agents

import com.histmersion.server.agents.{CliOutcomes, Codex}

import io.circe.{Json, JsonObject}
import com.histmersion.api.VocabularyCodecs.given

/** A step's answer schema in OpenAI's STRICT structured-output form — the only form the Codex CLI's `--output-schema`
  * is accepted in. Codex sends the file as `text.format` with `strict: true`, and the Responses API refuses anything
  * else outright (400 `invalid_json_schema`: "'additionalProperties' is required to be supplied and to be false"),
  * before the model generates a token. The schemas the server derives (Kindlings → Tapir → JSON Schema) are ordinary
  * JSON Schema: optional fields are left out of `required`, objects are open, integers carry Tapir's `int32`/`int64`
  * formats.
  *
  * The conversion (pure, applied once, in [[Codex.outputSchemaJson]]):
  *   - every object schema — the root, nested, in `items`, `$defs`, `anyOf` branches — gets
  *     `additionalProperties: false`, and `required` lists every property;
  *   - a property that was optional becomes nullable instead (`type: [T, "null"]` for a scalar or an array;
  *     `anyOf: [<it>, {type: null}]` for a `$ref`, an enum, an object or an `anyOf`), so the model answers `null` where
  *     it would have left the field out — the decode side ([[CliOutcomes.result]]) drops those nulls, so a null decodes
  *     exactly like an absent field;
  *   - keywords strict mode does not support are dropped (an allowlist: `default`, `minLength`, `uniqueItems`, … and
  *     `format` on anything but a string). What they constrained is the step's `validate`'s job, on the decoded answer,
  *     never the schema's;
  *   - a `$ref` with sibling keywords (strict mode takes a bare `$ref`) is replaced by its definition, with the
  *     siblings on top.
  *
  * The Claude paths keep the derived schema unchanged: they accept it as it is.
  */
object StrictJsonSchema:

  /** The keywords strict mode supports (OpenAI "Structured Outputs — supported schemas"). */
  val Keywords: Set[String] = Set(
    "type",
    "description",
    "title",
    "properties",
    "required",
    "additionalProperties",
    "items",
    "anyOf",
    "enum",
    "const",
    "$ref",
    "$defs",
    "definitions",
    "pattern",
    "format", // strings
    "multipleOf",
    "minimum",
    "maximum",
    "exclusiveMinimum",
    "exclusiveMaximum", // numbers
    "minItems",
    "maxItems"
  ) // arrays

  /** The string `format`s strict mode supports. */
  val StringFormats: Set[String] =
    Set("date-time", "time", "date", "duration", "email", "hostname", "ipv4", "ipv6", "uuid")

  /** `schema` (a root object schema) in strict form. */
  def apply(schema: Json): Json =
    val root = schema.asObject.getOrElse(JsonObject.empty)
    val defs = List("$defs", "definitions")
      .flatMap(k =>
        root(k)
          .flatMap(_.asObject)
          .toList
          .flatMap(_.toList)
          .map((n, d) => s"#/$k/$n" -> d)
      )
      .toMap
    convert(schema, defs, Set.empty)

  private def types(o: JsonObject): List[String] =
    o("type").toList.flatMap(t => t.asString.toList ++ t.asArray.toList.flatMap(_.flatMap(_.asString)))

  private def isObjectSchema(o: JsonObject): Boolean = types(o).contains("object") || o.contains("properties")

  private def isNullable(o: JsonObject): Boolean =
    types(o).contains("null") ||
      o("anyOf").flatMap(_.asArray).exists(_.exists(_.asObject.exists(isNullable))) ||
      o("enum").flatMap(_.asArray).exists(_.exists(_.isNull))

  /** `seen`: the `$ref`s being inlined on this path (a self-referencing definition is inlined once). */
  private def convert(node: Json, defs: Map[String, Json], seen: Set[String]): Json =
    node.asObject.fold(node)(o => Json.fromJsonObject(convertObject(o, defs, seen)))

  private def convertObject(o: JsonObject, defs: Map[String, Json], seen: Set[String]): JsonObject =
    val ref = o("$ref").flatMap(_.asString)
    ref.filter(r => o.size > 1 && !seen(r)).flatMap(r => defs.get(r).flatMap(_.asObject).map(r -> _)) match
      case Some((r, d)) =>
        // the definition, with this node's own keywords on top (its description wins)
        convertObject(o.remove("$ref").toList.foldLeft(d)((acc, kv) => acc.add(kv._1, kv._2)), defs, seen + r)
      case None =>
        val str = types(o).contains("string")
        val kept = o.filter((k, v) => Keywords(k) && (k != "format" || (str && v.asString.exists(StringFormats))))
        val sub = (j: Json) => convert(j, defs, seen)
        val mapped = kept.toList.map {
          case (k @ ("properties" | "$defs" | "definitions"), v) => k -> v.mapObject(_.mapValues(sub))
          case ("items", v)                                      => "items" -> sub(v)
          case ("anyOf", v)                                      => "anyOf" -> v.mapArray(_.map(sub))
          case kv                                                => kv
        }
        val c = JsonObject.fromIterable(mapped)
        if !isObjectSchema(c) then c
        else
          val props = c("properties").flatMap(_.asObject).getOrElse(JsonObject.empty)
          val required = o("required").flatMap(_.asArray).toList.flatten.flatMap(_.asString).toSet
          val strictProps = props.toList.map((k, v) => k -> (if required(k) then v else nullable(v)))
          c.add("properties", Json.fromFields(strictProps))
            .add("required", Json.fromValues(strictProps.map((k, _) => Json.fromString(k))))
            .add("additionalProperties", Json.False)

  private val NullSchema = Json.obj("type" -> Json.fromString("null"))

  /** A property schema that also accepts `null`. */
  private def nullable(v: Json): Json = v.asObject.fold(v) { o =>
    val ts = types(o)
    val structural = List("$ref", "enum", "const", "anyOf").exists(o.contains) || ts.contains("object")
    if isNullable(o) then v
    else if !structural && ts.nonEmpty then
      Json.fromJsonObject(o.add("type", Json.fromValues((ts :+ "null").map(Json.fromString))))
    else if o.contains("anyOf") && o.keys.forall(Set("anyOf", "description", "title")) then
      Json.fromJsonObject(o.add("anyOf", Json.fromValues(o("anyOf").flatMap(_.asArray).toVector.flatten :+ NullSchema)))
    else
      // the description stays on the outer schema, where a reader of the property looks for it
      val labels = List("description", "title").flatMap(k => o(k).map(k -> _))
      val inner = Json.fromJsonObject(o.filterKeys(k => k != "description" && k != "title"))
      Json.fromFields(labels :+ ("anyOf" -> Json.arr(inner, NullSchema)))
  }
