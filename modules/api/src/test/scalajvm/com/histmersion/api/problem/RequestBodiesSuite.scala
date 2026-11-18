package com.histmersion
package api
package problem

import sttp.tapir.{Schema, SchemaType}
import sttp.tapir.EndpointIO.Body
import sttp.tapir.internal.*

/** No request body holds a problem: a problem is what the server answers, never what a caller sends. Were one sent, the
  * browser writes it as the server does (the typed entry, TypedEntrySuite on both platforms), and a bare string in it
  * would read on the server as a master text ([[StoredShapes]]), not as the outside text the page meant.
  */
class RequestBodiesSuite extends munit.FunSuite:

  /** Does the schema reach the problem's (named `Problem`)? A named schema is followed once. */
  private def holdsProblem(schema: Schema[?], seen: Set[Schema.SName]): Boolean =
    schema.name match
      case Some(name) if name.fullName == "Problem" => true
      case Some(name) if seen(name)                 => false
      case named                                    =>
        val next = seen ++ named
        schema.schemaType match
          case SchemaType.SProduct(fields)            => fields.exists(f => holdsProblem(f.schema, next))
          case SchemaType.SCoproduct(subtypes, _)     => subtypes.exists(holdsProblem(_, next))
          case SchemaType.SOption(element)            => holdsProblem(element, next)
          case SchemaType.SArray(element)             => holdsProblem(element, next)
          case SchemaType.SOpenProduct(fields, value) =>
            fields.exists(f => holdsProblem(f.schema, next)) || holdsProblem(value, next)
          case _ => false

  private def bodies(endpoint: sttp.tapir.AnyEndpoint, inputs: Boolean): Vector[Body[?, ?]] =
    if inputs then endpoint.input.traverseInputs { case b: Body[?, ?] => Vector(b) }
    else endpoint.output.traverseOutputs { case b: Body[?, ?] => Vector(b) }

  test("no endpoint's request body holds a problem"):
    val holding = Api.all.filter(e => bodies(e, inputs = true).exists(b => holdsProblem(b.codec.schema, Set.empty)))
    assertEquals(holding.map(_.showShort), Nil)

  test("the check finds a problem where a body holds one: in the answers"):
    assert(Api.all.exists(e => bodies(e, inputs = false).exists(b => holdsProblem(b.codec.schema, Set.empty))))
