package com.histmersion
package server

import scala.collection.mutable

import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.{Json, JsonObject}

final class SchemaInstances(document: Json, supplied: Map[String, Vector[Json]] = Map.empty):
  import SchemaInstances.*

  private val components: JsonObject =
    document.asObject
      .flatMap(_("components"))
      .flatMap(_.asObject)
      .flatMap(_("schemas"))
      .flatMap(_.asObject)
      .getOrElse(JsonObject.empty)

  def resolve(schema: JsonObject): (Option[String], JsonObject) =
    schema("$ref").flatMap(_.asString) match
      case Some(ref) =>
        val name = ref.stripPrefix("#/components/schemas/")
        (Some(name), components(name).flatMap(_.asObject).getOrElse(JsonObject.empty))
      case None => (None, schema)

  def covering(schema: Json, maxRounds: Int = 64): Coverage =
    val emitted = mutable.LinkedHashMap.empty[String, mutable.Set[Int]]
    val options = mutable.LinkedHashMap.empty[String, Int]
    val turns = mutable.Map.empty[String, Int]

    def choose(key: String, count: Int): Int =
      options(key) = count
      val seen = emitted.getOrElseUpdate(key, mutable.Set.empty)
      val start = turns.getOrElse(key, 0)
      val pick = (0 until count).map(i => (start + i) % count).find(i => !seen(i)).getOrElse(start % count)
      turns(key) = pick + 1
      seen += pick
      pick

    def generate(node: Json, depth: Int): Json =
      node.asObject.fold(Json.obj()) { raw =>
        val (name, schema) = resolve(raw)
        val key = name.getOrElse(schema.toList.sortBy(_._1).map((k, v) => s"$k=${v.noSpaces}").mkString(","))
        lazy val types = schema("type").toVector.flatMap(t => t.asArray.getOrElse(Vector(t))).flatMap(_.asString)
        val examples = name.flatMap(supplied.get).filter(_.nonEmpty)
        val own = schema("examples").flatMap(_.asArray).orElse(schema("example").map(Vector(_))).filter(_.nonEmpty)
        schema("enum").flatMap(_.asArray) match
          case _ if examples.isDefined         => examples.fold(Json.Null)(es => es(choose(s"supplied:$key", es.size)))
          case _ if own.isDefined              => own.fold(Json.Null)(es => es(choose(s"example:$key", es.size)))
          case Some(values) if values.nonEmpty => values(choose(s"enum:$key", values.size))
          case _                               =>
            alternatives(schema) match
              case Some(branches)         => generate(branches(choose(s"one-of:$key", branches.size)), depth + 1)
              case None if types.size > 1 =>
                generateOf(schema, types(choose(s"type:$key", types.size)), depth)
              case None => generateOf(schema, types.headOption.getOrElse(""), depth)
      }

    def generateOf(schema: JsonObject, tpe: String, depth: Int): Json = tpe match
      case "null"    => Json.Null
      case "boolean" => Json.fromBoolean(true)
      case "integer" => Json.fromInt(1)
      case "number"  => Json.fromBigDecimal(BigDecimal("1.5"))
      case "string"  =>
        if schema("format").flatMap(_.asString).contains("uuid") then Json.fromString(Uuid)
        else Json.fromString("x")
      case "array" =>
        val least = schema("minItems").flatMap(_.asNumber).flatMap(_.toInt).getOrElse(0)
        val size = if depth < Deep then math.max(least, 2) else least
        Json.fromValues(Vector.fill(size)(schema("items").fold(Json.obj())(generate(_, depth + 1))))
      case "object" =>
        val required = schema("required").flatMap(_.asArray).getOrElse(Vector.empty).flatMap(_.asString).toSet
        val properties = schema("properties").flatMap(_.asObject).getOrElse(JsonObject.empty).toList
        val fields = properties.filter((k, _) => required(k) || depth < Deep)
        val extra = schema("additionalProperties").filter(_.isObject).filter(_ => depth < Deep)
        Json.fromFields(
          fields.map((k, v) => k -> generate(v, depth + 1)) ++ extra.map(v => "k" -> generate(v, depth + 1))
        )
      case _ => Json.obj()

    def coverageComplete: Boolean =
      options.forall((key, count) => emitted.get(key).exists(_.size >= count))

    def rounds(done: Vector[Json], round: Int): Vector[Json] =
      if round >= maxRounds || (round > 0 && coverageComplete) then done
      else rounds(done :+ generate(schema, 0), round + 1)

    val instances = rounds(Vector.empty, 0)
    Coverage(
      instances,
      options.toVector.map((key, count) => Choice(key, count, emitted.get(key).fold(0)(_.size)))
    )

  private def alternatives(schema: JsonObject): Option[Vector[Json]] =
    schema("oneOf").orElse(schema("anyOf")).flatMap(_.asArray).filter(_.nonEmpty)

object SchemaInstances:
  val Deep = 5
  val Uuid = "00000000-0000-4000-8000-000000000001"

  final case class Choice(key: String, options: Int, covered: Int):
    def complete: Boolean = covered >= options

  final case class Coverage(instances: Vector[Json], choices: Vector[Choice])
