package com.histmersion
package server

import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.{Json, JsonObject}

final class SchemaConformance(document: Json):

  private val instances = SchemaInstances(document)

  def violations(schema: Json, value: Json, at: String = "$"): Vector[String] =
    schema.asObject.fold(Vector.empty[String]) { raw =>
      val (_, node) = instances.resolve(raw)
      val oneOf = node("oneOf").flatMap(_.asArray).map { branches =>
        val fitting = branches.count(b => violations(b, value, at).isEmpty)
        if fitting == 1 then Vector.empty
        else Vector(s"$at: ${value.noSpaces} fits $fitting of the ${branches.size} oneOf branches")
      }
      val anyOf = node("anyOf").flatMap(_.asArray).map { branches =>
        if branches.exists(b => violations(b, value, at).isEmpty) then Vector.empty
        else Vector(s"$at: ${value.noSpaces} fits none of the ${branches.size} anyOf branches")
      }
      val listed = node("enum").flatMap(_.asArray).map { values =>
        if values.exists(_.noSpaces == value.noSpaces) then Vector.empty
        else Vector(s"$at: ${value.noSpaces} is none of ${Json.fromValues(values).noSpaces}")
      }
      val types = node("type").toVector.flatMap(t => t.asArray.getOrElse(Vector(t))).flatMap(_.asString)
      val typed =
        if types.isEmpty then Vector.empty
        else
          types.find(fits(_, value)) match
            case None    => Vector(s"$at: ${value.noSpaces} is not of type ${types.mkString(" or ")}")
            case Some(t) => inside(node, t, value, at)
      val patterned = (node("pattern").flatMap(_.asString), value.asString) match
        case (Some(p), Some(s)) if p.r.findFirstIn(s).isEmpty => Vector(s"$at: ${value.noSpaces} does not match $p")
        case _                                                => Vector.empty
      oneOf.getOrElse(Vector.empty) ++ anyOf.getOrElse(Vector.empty) ++ listed.getOrElse(Vector.empty) ++ typed ++
        patterned
    }

  private def fits(tpe: String, value: Json): Boolean = tpe match
    case "null"    => value.isNull
    case "boolean" => value.isBoolean
    case "integer" => value.asNumber.exists(_.toBigDecimal.exists(_.isWhole))
    case "number"  => value.isNumber
    case "string"  => value.isString
    case "array"   => value.isArray
    case "object"  => value.isObject
    case _         => true

  private def inside(node: JsonObject, tpe: String, value: Json, at: String): Vector[String] = tpe match
    case "array" =>
      val items = value.asArray.getOrElse(Vector.empty)
      val least = node("minItems").flatMap(_.asNumber).flatMap(_.toInt).getOrElse(0)
      val short = if items.size < least then Vector(s"$at: ${items.size} items, at least $least") else Vector.empty
      short ++ node("items").toVector.flatMap(s => items.zipWithIndex.flatMap((v, i) => violations(s, v, s"$at[$i]")))
    case "object" =>
      val fields = value.asObject.getOrElse(JsonObject.empty)
      val properties = node("properties").flatMap(_.asObject).getOrElse(JsonObject.empty)
      val required = node("required").flatMap(_.asArray).getOrElse(Vector.empty).flatMap(_.asString)
      val extra = node("additionalProperties")
      val missing = required.filterNot(fields.contains).map(k => s"$at: required field '$k' is missing")
      val checked = fields.toList.toVector.flatMap { (k, v) =>
        properties(k) match
          case Some(s) => violations(s, v, s"$at.$k")
          case None    =>
            extra match
              case Some(s) if s.isObject              => violations(s, v, s"$at.$k")
              case Some(Json.Bool(true))              => Vector.empty
              case None if node("properties").isEmpty => Vector.empty
              case _                                  => Vector(s"$at: field '$k' is not declared")
      }
      missing ++ checked
    case _ => Vector.empty
