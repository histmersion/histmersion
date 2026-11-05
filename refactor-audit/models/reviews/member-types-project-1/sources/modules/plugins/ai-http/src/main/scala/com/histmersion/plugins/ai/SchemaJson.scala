package com.histmersion.plugins.ai

import io.circe.{Json, JsonObject}

/** A JSON Schema as a provider must be sent it: a tool's input or the step's answer, with the repairs each API needs —
  * an object type and properties for Anthropic's tools, `additionalProperties: false` on Anthropic's answer, and
  * OpenAI's strict form (every property required, the optional ones nullable, no extra properties).
  */
final case class SchemaJson(json: Json):

  /** A tool's input as the Anthropic API takes it: an object (`true`/`false` become one), with `properties` when it is
    * a plain object without them.
    */
  def objectTyped: SchemaJson =
    if json.isBoolean then SchemaJson(Json.obj("type" -> Json.fromString("object"))).objectTyped
    else
      json.asObject match
        case Some(obj) =>
          val typed = if obj.contains("type") then obj else obj.add("type", Json.fromString("object"))
          val repaired =
            if typed("type").contains(Json.fromString("object")) && !typed.contains("properties") &&
              !SchemaJson.shapedElsewhere(typed)
            then typed.add("properties", Json.obj())
            else typed
          SchemaJson(Json.fromJsonObject(repaired))
        case None => this

  /** The answer's schema as the Anthropic API takes it: `additionalProperties: false` on every object with properties
    * that does not say otherwise.
    */
  def closed: SchemaJson = SchemaJson(SchemaJson.close(json))

  /** OpenAI's strict form: `additionalProperties: false` on every object, every property `required`, the ones that were
    * optional made nullable (`null` added to an `enum` too), `properties` on every plain object.
    */
  def strict: SchemaJson =
    if json.isBoolean then SchemaJson(Json.obj("type" -> Json.fromString("object"))).strict
    else SchemaJson(SchemaJson.strictRoot(SchemaJson.strictNode(json)))

object SchemaJson:

  /** The keywords that give an object's shape without `properties`. */
  type ShapedElsewhereResult = ShapedElsewhereResult.T
  object ShapedElsewhereResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val ShapeKeywords = List("$ref", "oneOf", "anyOf", "allOf", "discriminator")

  /** Keywords whose value maps names (not keywords) to schemas. */
  private val NameToSchema = Set("$defs", "definitions", "patternProperties", "dependentSchemas", "dependencies")

  private def shapedElsewhere(obj: JsonObject): com.histmersion.plugins.ai.SchemaJson.ShapedElsewhereResult =
    ShapeKeywords.exists(obj.contains) || obj("additionalProperties").exists(ap =>
      !ap.isBoolean || ap.asBoolean.contains(true)
    )

  private def close(json: Json): Json =
    json.arrayOrObject(
      json,
      arr => Json.fromValues(arr.map(close)),
      obj =>
        val recursed = JsonObject.fromIterable(obj.toIterable.map((k, v) => k -> close(v)))
        val updated =
          if obj("type").contains(Json.fromString("object")) && obj.contains("properties") &&
            !obj.contains("additionalProperties")
          then recursed.add("additionalProperties", Json.False)
          else recursed
        Json.fromJsonObject(updated)
    )

  private def strictNode(json: Json): Json =
    json.arrayOrObject(json, arr => Json.fromValues(arr.map(strictNode)), obj => strictObject(obj))

  private def strictObject(value: JsonObject): Json =
    val originalRequired =
      value("required").flatMap(_.asArray).map(_.flatMap(_.asString).toSet).getOrElse(Set.empty[String])
    // fold from the right, as the fields are prepended: they come out in their order
    val (fields, addAdditional, requiredProperties) =
      value.toList.foldRight((List.empty[(String, Json)], false, List.empty[String])) {
        case ((k, v), (acc, add, req)) =>
          if k == "properties" then
            val folded = v.asObject match
              case Some(props) =>
                Json.fromJsonObject(JsonObject.fromIterable(props.toList.map((n, s) => n -> strictNode(s))))
              case None => strictNode(v)
            val nullable = folded.asObject match
              case Some(props) =>
                Json.fromJsonObject(JsonObject.fromIterable(props.toList.map { (name, s) =>
                  if originalRequired.contains(name) then name -> s else name -> nullableOf(s)
                }))
              case None => folded
            ((k, nullable) :: acc, true, v.asObject.fold(List.empty[String])(_.keys.toList))
          else if NameToSchema(k) then
            val folded = v.asObject match
              case Some(entries) =>
                Json.fromJsonObject(JsonObject.fromIterable(entries.toList.map((n, s) => n -> strictNode(s))))
              case None => strictNode(v)
            ((k, folded) :: acc, add, req)
          else if k == "type" then ((k, strictNode(v)) :: acc, add || v.asString.contains("object"), req)
          else ((k, strictNode(v)) :: acc, add, req)
      }
    val union = value.contains("discriminator")
    val (additionalRemove, additionalAdd) =
      if addAdditional && !union then (Set("additionalProperties"), List("additionalProperties" -> Json.False))
      else (Set.empty[String], Nil)
    val (requiredRemove, requiredAdd) =
      if requiredProperties.nonEmpty then
        (Set("required"), List("required" -> Json.fromValues(requiredProperties.map(Json.fromString))))
      else (Set.empty[String], Nil)
    val remove = additionalRemove ++ requiredRemove
    val all = additionalAdd ++ requiredAdd ++ fields.filterNot((k, _) => remove(k))
    val plainPropertyless =
      value("type").contains(Json.fromString("object")) && !value.contains("properties") && !shapedElsewhere(value)
    Json.fromFields(if plainPropertyless then all :+ ("properties" -> Json.obj()) else all)

  private def nullableOf(schema: Json): Json =
    schema.asObject match
      case Some(obj) =>
        obj("type") match
          case Some(t) if t.isString =>
            if t.asString.contains("null") then schema
            else Json.fromJsonObject(nullEnum(obj.add("type", Json.arr(t, Json.fromString("null")))))
          case Some(t) if t.isArray =>
            val types = t.asArray.getOrElse(Vector.empty)
            if types.contains(Json.fromString("null")) then schema
            else Json.fromJsonObject(nullEnum(obj.add("type", Json.fromValues(types :+ Json.fromString("null")))))
          case _ =>
            val nullableAlready =
              obj("anyOf").flatMap(_.asArray).exists(_.contains(Json.obj("type" -> Json.fromString("null"))))
            if nullableAlready then schema
            else Json.obj("anyOf" -> Json.arr(schema, Json.obj("type" -> Json.fromString("null"))))
      case None => schema

  private def nullEnum(obj: JsonObject): JsonObject =
    obj("enum").flatMap(_.asArray) match
      case Some(values) if !values.contains(Json.Null) => obj.add("enum", Json.fromValues(values :+ Json.Null))
      case _                                           => obj

  private def strictRoot(json: Json): Json =
    json.asObject match
      case Some(obj) if !obj.contains("properties") && !obj.contains("type") && !ShapeKeywords.exists(obj.contains) =>
        Json.fromJsonObject(
          obj
            .add("type", Json.fromString("object"))
            .add("properties", Json.obj())
            .add("additionalProperties", Json.False)
        )
      case _ => json
