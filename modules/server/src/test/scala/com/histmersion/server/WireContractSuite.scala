package com.histmersion
package server

import com.histmersion.api.Api
import com.histmersion.api.json.NewtypeCodecs
import com.histmersion.util.problem.Problem
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.{Json, JsonObject}
import sttp.tapir.*
import sttp.tapir.internal.{RichEndpointInput, RichEndpointOutput}

class WireContractSuite extends munit.FunSuite:
  import WireContractSuite.*

  override val munitTimeout = scala.concurrent.duration.Duration(10, "min")

  test("every JSON body of every endpoint: each case its schema lists decodes, and what decodes encodes to the schema"):
    val checks = Api.all.flatMap(bodies)
    val findings = checks.flatMap(_.findings)
    val report = java.nio.file.Path.of("target/wire-contract-findings.txt")
    java.nio.file.Files.createDirectories(report.getParent)
    val _ = java.nio.file.Files.writeString(report, findings.distinct.mkString("\n"))
    val counted = checks.flatMap(_.coverage.choices).groupMapReduce(_.key.takeWhile(_ != ':'))(_ => 1)(_ + _)
    val _ = java.nio.file.Files.writeString(
      report.resolveSibling("wire-contract-coverage.txt"),
      s"${checks.size} bodies, ${checks.map(_.coverage.instances.size).sum} instances; choices by kind: $counted\n"
    )
    val choices = checks.flatMap(_.coverage.choices).distinctBy(_.key)
    val uncovered = checks.flatMap(c =>
      c.coverage.choices
        .filterNot(ch => ch.complete || ch.key.startsWith("supplied:"))
        .map(ch => s"${c.label}: ${ch.key}")
    )
    assert(checks.size > 200, s"only ${checks.size} JSON bodies found")
    assert(choices.count(_.key.startsWith("enum:")) > 10, choices.map(_.key).mkString("\n"))
    assertEquals(uncovered.distinct, Nil, "a case no generated example reached")
    assert(
      findings.isEmpty,
      s"${findings.size} disagreements between a codec and its schema in openapi.json:\n${findings.distinct.take(80).mkString("\n")}"
    )

  test("every problem example is written as the Problem schema says, and reads back as the same problem"):
    val doc = OpenApiDocument.json(Api.all)
    val conformance = SchemaConformance(doc)
    val schema = parse("""{"$ref":"#/components/schemas/Problem"}""")
    val misfits = Problem.examples.flatMap { p =>
      val written = p.asJson(using NewtypeCodecs.problemCodec)
      val back = written.as(using NewtypeCodecs.problemCodec)
      conformance.violations(schema, written).map(v => s"$p: $v") ++
        Option.when(!back.toOption.contains(p))(s"$p reads back as $back")
    }
    assert(Problem.examples.size > 100, Problem.examples.size)
    assertEquals(misfits, Vector.empty)

  test("an instance is generated for every case of an enum and every branch of a oneOf"):
    val doc = document(
      """{"Who":{"type":"string","enum":["Owner","Agents"]},
        | "Shape":{"oneOf":[{"type":"object","required":["Dot"],"properties":{"Dot":{"type":"object"}}},
        |                   {"type":"object","required":["Line"],"properties":{"Line":{"$ref":"#/components/schemas/Who"}}}]}}""".stripMargin
    )
    val coverage = SchemaInstances(doc).covering(parse("""{"$ref":"#/components/schemas/Shape"}"""))
    assertEquals(
      coverage.instances.map(_.noSpaces).toSet,
      Set("""{"Dot":{}}""", """{"Line":"Owner"}""", """{"Line":"Agents"}""")
    )
    assert(coverage.choices.forall(_.complete), coverage.choices)

  test("an optional field is generated present, and a nullable one both null and set"):
    val doc = document(
      """{"R":{"type":"object","required":["a"],"properties":{"a":{"type":"integer"},"b":{"type":["string","null"]}}}}"""
    )
    val instances =
      SchemaInstances(doc).covering(parse("""{"$ref":"#/components/schemas/R"}""")).instances.map(_.noSpaces)
    assertEquals(instances.toSet, Set("""{"a":1,"b":"x"}""", """{"a":1,"b":null}"""))

  test("a value conforms when its type, enum, required fields and oneOf branch fit; each misfit is named"):
    val doc = document(
      """{"Who":{"type":"string","enum":["Owner","Agents"]},
        | "R":{"type":"object","required":["who"],"properties":{"who":{"$ref":"#/components/schemas/Who"},"n":{"type":"integer"}}}}""".stripMargin
    )
    val conformance = SchemaConformance(doc)
    val r = parse("""{"$ref":"#/components/schemas/R"}""")
    assertEquals(conformance.violations(r, parse("""{"who":"Owner","n":2}""")), Vector.empty)
    assertEquals(
      conformance.violations(r, parse("""{"who":{"Owner":{}}}""")),
      Vector("""$.who: {"Owner":{}} is none of ["Owner","Agents"]""", """$.who: {"Owner":{}} is not of type string""")
    )
    assertEquals(conformance.violations(r, parse("""{"n":1.5}""")).size, 2)
    assertEquals(conformance.violations(r, parse("""{"who":"Agents","x":1}""")), Vector("$: field 'x' is not declared"))

  test("oneOf: a value fitting no branch, or two, is a misfit"):
    val doc = document("""{"S":{"oneOf":[{"type":"string"},{"type":"string","enum":["a"]}]}}""")
    val s = parse("""{"$ref":"#/components/schemas/S"}""")
    assertEquals(SchemaConformance(doc).violations(s, parse("\"b\"")), Vector.empty)
    assertEquals(SchemaConformance(doc).violations(s, parse("\"a\"")).size, 1)
    assertEquals(SchemaConformance(doc).violations(s, parse("1")).size, 1)

  test("a decode failure is documented as the problem body, the options marked nullable as the codecs write them"):
    val doc = OpenApiDocument.json(List(api.research.ResearchApi.state))
    val responses = doc.cursor
      .downField("paths")
      .downField("/api/research/state")
      .downField("get")
      .downField("responses")
      .focus
      .flatMap(_.asObject)
      .getOrElse(JsonObject.empty)
    assertEquals(
      responses("400")
        .map(_.noSpaces)
        .exists(_.contains(""""application/json":{"schema":{"$ref":"#/components/schemas/Error"}}""")),
      true,
      responses.toList.map((k, v) => s"$k ${v.noSpaces}").mkString("\n")
    )

object WireContractSuite:

  final case class Check(label: String, coverage: SchemaInstances.Coverage, findings: Vector[String])

  val Supplied: Map[String, Vector[Json]] =
    Map("Problem" -> Problem.examples.map(_.asJson(using NewtypeCodecs.problemCodec)))

  def parse(text: String): Json = RawJson.parse(text).fold(e => throw AssertionError(e), identity)

  def document(schemas: String): Json = parse(s"""{"components":{"schemas":$schemas}}""")

  private def jsonBody(b: EndpointIO.Body[?, ?]): Option[EndpointIO.Body[String, Any]] =
    (b.bodyType, b.codec.format) match
      case (_: RawBodyType.StringBody, _: CodecFormat.Json) => Some(b.asInstanceOf[EndpointIO.Body[String, Any]])
      case _                                                => None

  def bodies(e: AnyEndpoint): List[Check] =
    val doc = OpenApiDocument.json(List(e))
    val operation = doc.cursor
      .downField("paths")
      .focus
      .flatMap(_.asObject)
      .flatMap(_.values.headOption)
      .flatMap(_.asObject)
      .flatMap(_.values.headOption)
      .flatMap(_.asObject)
      .getOrElse(JsonObject.empty)
    val label = s"${e.method.fold("GET")(_.method)} ${e.showPathTemplate(showQueryParam = None)}"
    val requests = e.input.asVectorOfBasicInputs().collect { case b: EndpointIO.Body[?, ?] => b }.flatMap(jsonBody)
    val request = requests.toList.flatMap { body =>
      operation("requestBody").flatMap(contentSchema).map(s => check(doc, s"$label request", body, s)).toList
    }
    val responses = operation("responses").flatMap(_.asObject).getOrElse(JsonObject.empty)
    def outputs(output: EndpointOutput[?], fallback: String, kind: String): List[Check] =
      output.asBasicOutputsList.flatMap { variant =>
        val status = variant
          .collectFirst { case s: EndpointOutput.FixedStatusCode[?] => s"${s.statusCode.code}" }
          .getOrElse(fallback)
        variant.collect { case b: EndpointIO.Body[?, ?] => b }.flatMap(jsonBody).toList.map { body =>
          responses(status).flatMap(contentSchema) match
            case Some(schema) => check(doc, s"$label $kind $status", body, schema)
            case None         =>
              Check(
                s"$label $kind $status",
                SchemaInstances.Coverage(Vector.empty, Vector.empty),
                Vector(s"$label $kind $status: openapi.json documents no JSON body for it")
              )
        }
      }
    request ++ outputs(e.output, "200", "response") ++ outputs(e.errorOutput, "default", "error")

  private def contentSchema(holder: Json): Option[Json] =
    holder.cursor.downField("content").downField("application/json").downField("schema").focus

  def check(doc: Json, label: String, body: EndpointIO.Body[String, Any], schema: Json): Check =
    val coverage = SchemaInstances(doc, Supplied).covering(schema)
    val conformance = SchemaConformance(doc)
    val findings = coverage.instances.flatMap { instance =>
      body.codec.decode(instance.noSpaces) match
        case DecodeResult.Value(value) =>
          val written = parse(body.codec.encode(value))
          conformance
            .violations(schema, written)
            .map(v => s"$label: ${instance.noSpaces} encodes back off its schema — $v")
        case failure => Vector(s"$label: the schema's example ${instance.noSpaces} does not decode — $failure")
    }
    Check(label, coverage, findings)
