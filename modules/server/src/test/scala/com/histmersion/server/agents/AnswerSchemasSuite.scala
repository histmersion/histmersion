package com.histmersion.server.agents

import io.circe.{Json, JsonObject, parser}
import io.circe.syntax.*
import sttp.ai.core.agent.*

import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters.*

/** Every step's answer schema as Codex is sent it — OpenAI's strict structured-output form, which the
  * Responses API demands with `strict: true` and answers anything else with a 400 before the model
  * runs — and every answer Codex sends back under it: `null` for each formerly optional field decodes
  * exactly like the field left out. Iterated over [[AnswerSchemas.all]], not a hand-picked schema. */
class AnswerSchemasSuite extends munit.FunSuite:

  // ── strict-mode invariants, checked independently of the converter's own constants ─────────────

  /** OpenAI structured outputs: the keywords strict mode supports. */
  private val Supported = Set("type", "description", "title", "properties", "required", "additionalProperties",
    "items", "anyOf", "enum", "const", "$ref", "$defs", "definitions", "pattern", "format", "multipleOf",
    "minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum", "minItems", "maxItems")
  private val StringFormats = Set("date-time", "time", "date", "duration", "email", "hostname", "ipv4", "ipv6", "uuid")

  private def typesOf(o: JsonObject): List[String] =
    o("type").toList.flatMap(t => t.asString.toList ++ t.asArray.toList.flatMap(_.flatMap(_.asString)))

  /** Every schema node under `j` (not the property-name and definition-name maps), with its path. */
  private def nodes(j: Json, path: String): List[(String, JsonObject)] = j.asObject.toList.flatMap { o =>
    def sub(k: String) = o(k).flatMap(_.asObject).toList.flatMap(_.toList).flatMap((n, v) => nodes(v, s"$path/$k/$n"))
    (path -> o) :: sub("properties") ++ sub("$defs") ++ sub("definitions") ++
      o("items").toList.flatMap(nodes(_, s"$path/items")) ++
      o("anyOf").flatMap(_.asArray).toList.flatten.zipWithIndex.flatMap((b, i) => nodes(b, s"$path/anyOf/$i"))
  }

  /** The strict-mode violations of a schema (empty: Codex accepts it). */
  private def violations(schema: Json): List[String] =
    val root = schema.asObject.get
    val rootRules = List(
      Option.when(typesOf(root) != List("object"))("the root is not an object"),
      Option.when(root.contains("anyOf"))("the root is an anyOf")).flatten
    rootRules ++ nodes(schema, "#").flatMap { (p, o) =>
      val unsupported = o.keys.filterNot(Supported).map(k => s"$p: unsupported keyword $k").toList
      val badFormat = o("format").filterNot(f => typesOf(o).contains("string") && f.asString.exists(StringFormats))
        .map(f => s"$p: format $f").toList
      val bareRef = Option.when(o.contains("$ref") && o.size > 1)(s"$p: $$ref with siblings ${o.keys.mkString(",")}").toList
      val untyped = Option.when(!o.contains("type") && !o.contains("$ref") && !o.contains("anyOf") && !o.contains("enum") &&
        !o.contains("const"))(s"$p: no type").toList
      val obj = Option.when(typesOf(o).contains("object") || o.contains("properties")) {
        val keys = o("properties").flatMap(_.asObject).map(_.keys.toList).getOrElse(Nil)
        val req = o("required").flatMap(_.asArray).map(_.flatMap(_.asString).toList)
        List(
          Option.when(!o("additionalProperties").contains(Json.False))(s"$p: additionalProperties is not false"),
          Option.when(req.map(_.toSet) != Some(keys.toSet))(s"$p: required ${req.getOrElse(Nil)} != properties $keys")).flatten
      }.toList.flatten
      unsupported ++ badFormat ++ bareRef ++ untyped ++ obj
    }

  private def strict(s: ResponseSchema[?]): Json = parser.parse(Codex.outputSchemaJson(s)).toOption.get
  private def derived(s: ResponseSchema[?]): Json = parser.parse(Codex.schemaJson(s)).toOption.get

  test("every step schema has a name, and they are all listed"):
    assertEquals(AnswerSchemas.all.map(_._1).distinct.size, AnswerSchemas.all.size)
    assert(AnswerSchemas.all.size >= 12, AnswerSchemas.all.map(_._1))

  test("no step derives its schema at the call: every ResponseSchema the server sends is in AnswerSchemas"):
    val main = Iterator.iterate(Paths.get(sys.props("user.dir")).toAbsolutePath)(_.getParent).takeWhile(_ != null)
      .map(_.resolve("modules/server/src/main/scala")).find(Files.isDirectory(_)).get
    val stray = Files.walk(main).iterator.asScala.filter(_.toString.endsWith(".scala"))
      .filterNot(_.getFileName.toString == "AnswerSchemas.scala")
      .filter(f => Files.readString(f).contains("ResponseSchema.derived")).map(main.relativize).toList
    assertEquals(stray, Nil, "a step schema derived outside AnswerSchemas escapes the strict-form tests")

  test("Codex is sent every step schema in OpenAI's strict form"):
    for (name, s) <- AnswerSchemas.all do
      assertEquals(violations(strict(s)), Nil, s"$name: ${strict(s).noSpaces}")

  test("the derived schemas are NOT strict (what Codex refused): the conversion is what makes them pass"):
    val refused = AnswerSchemas.all.filter((_, s) => violations(derived(s)).exists(_.contains("additionalProperties")))
    assertEquals(refused.map(_._1), AnswerSchemas.all.map(_._1))

  test("the conversion keeps every property, description and the fields that were required"):
    for (name, s) <- AnswerSchemas.all do
      val (d, c) = (nodes(derived(s), "#").toMap, nodes(strict(s), "#").toMap)
      for (p, o) <- d if c.contains(p) do
        assertEquals(o("description"), c(p)("description"), s"$name $p")
        o("required").flatMap(_.asArray).toList.flatten.foreach(r =>
          assert(c(p)("required").exists(_.asArray.exists(_.contains(r))), s"$name $p: $r"))

  // ── the converter on a schema with every awkward case ─────────────────────────────────────────

  test("strict form: optional scalars, arrays, refs, enums, objects and anyOfs become nullable; unsupported keywords go"):
    val in = parser.parse("""{
      "$defs": {"Y": {"type":"object","required":["v"],"properties":{"v":{"type":"integer","format":"int64","default":0}}}},
      "type":"object","required":["a"],
      "properties":{
        "a":{"type":"string","minLength":1,"description":"A"},
        "b":{"type":"string","format":"date-time"},
        "c":{"type":"array","items":{"type":"string"},"uniqueItems":true},
        "d":{"$ref":"#/$defs/Y"},
        "e":{"description":"E","$ref":"#/$defs/Y"},
        "f":{"type":"string","enum":["x","y"],"description":"F"},
        "g":{"type":"object","properties":{"h":{"type":"boolean"}}},
        "i":{"anyOf":[{"type":"string"},{"type":"integer"}]},
        "j":{"type":["string","null"]},
        "k":{"type":"integer","format":"int32","minimum":0}
      }}""").toOption.get
    val out = StrictJsonSchema(in)
    val y = """{"type":"object","required":["v"],"properties":{"v":{"type":"integer"}},"additionalProperties":false}"""
    val expected = parser.parse(s"""{
      "$$defs": {"Y": $y},
      "type":"object",
      "required":["a","b","c","d","e","f","g","i","j","k"],
      "properties":{
        "a":{"type":"string","description":"A"},
        "b":{"type":["string","null"],"format":"date-time"},
        "c":{"type":["array","null"],"items":{"type":"string"}},
        "d":{"anyOf":[{"$$ref":"#/$$defs/Y"},{"type":"null"}]},
        "e":{"description":"E","anyOf":[{"type":"object","required":["v"],"properties":{"v":{"type":"integer"}},"additionalProperties":false},{"type":"null"}]},
        "f":{"description":"F","anyOf":[{"type":"string","enum":["x","y"]},{"type":"null"}]},
        "g":{"anyOf":[{"type":"object","properties":{"h":{"type":["boolean","null"]}},"required":["h"],"additionalProperties":false},{"type":"null"}]},
        "i":{"anyOf":[{"type":"string"},{"type":"integer"},{"type":"null"}]},
        "j":{"type":["string","null"]},
        "k":{"type":["integer","null"],"minimum":0}
      },
      "additionalProperties":false}""").toOption.get
    assertEquals(out, expected)
    assertEquals(violations(out), Nil)

  test("#1461 the cold reader's emptySlots is an array of the four slot names, in both forms Claude and Codex get"):
    for js <- List(derived(AnswerSchemas.coldReader), strict(AnswerSchemas.coldReader)) do
      val p = js.hcursor.downField("properties").downField("emptySlots")
      val items = p.downField("items").success.orElse(p.downField("anyOf").downN(0).downField("items").success)
      assertEquals(items.flatMap(_.downField("enum").as[List[String]].toOption),
        Some(com.histmersion.domain.ReviewArticle.ExplainSlots), js.noSpaces)

  test("the strict form is idempotent"):
    for (name, s) <- AnswerSchemas.all do assertEquals(StrictJsonSchema(strict(s)), strict(s), name)

  // ── the answers: null decodes as absent ────────────────────────────────────────────────────────

  /** A minimal instance of the DERIVED schema: every required field filled; every optional one left
    * out (`nulls = false`, what Claude sends) or `null` (`nulls = true`, what Codex sends under the
    * strict schema). Arrays get one element, so optional fields of nested objects are exercised too. */
  private def instance(schema: Json, nulls: Boolean): Json =
    val root = schema.asObject.get
    val defs = root("$defs").flatMap(_.asObject).getOrElse(JsonObject.empty)
    def gen(j: Json, depth: Int): Json =
      val o = j.asObject.get
      assert(depth < 12, s"a recursive schema: $j")
      o("$ref").flatMap(_.asString) match
        case Some(r) => gen(defs(r.stripPrefix("#/$defs/")).get, depth + 1)
        case None if o.contains("anyOf") =>
          gen(o("anyOf").get.asArray.get.find(b => !b.asObject.exists(typesOf(_) == List("null"))).get, depth + 1)
        case None if o.contains("enum") => o("enum").get.asArray.get.find(!_.isNull).get
        case None => typesOf(o).filterNot(_ == "null").headOption match
          case Some("string")  => Json.fromString("x")
          case Some("integer") => Json.fromInt(1)
          case Some("number")  => Json.fromDoubleOrNull(1.5)
          case Some("boolean") => Json.True
          case Some("array")   => Json.arr(gen(o("items").get, depth + 1))
          case _ =>
            val req = o("required").flatMap(_.asArray).toList.flatten.flatMap(_.asString).toSet
            Json.fromFields(o("properties").flatMap(_.asObject).toList.flatMap(_.toList).flatMap((k, v) =>
              if req(k) then Some(k -> gen(v, depth + 1)) else Option.when(nulls)(k -> Json.Null)))
    gen(schema, 0)

  /** A JSON Schema check over the strict subset: what the Responses API enforces on Codex's answer. */
  private def conforms(schema: Json, value: Json): Boolean =
    val defs = schema.asObject.flatMap(_("$defs")).flatMap(_.asObject).getOrElse(JsonObject.empty)
    def ok(s: Json, v: Json): Boolean =
      val o = s.asObject.get
      o("$ref").flatMap(_.asString) match
        case Some(r) => ok(defs(r.stripPrefix("#/$defs/")).get, v)
        case None =>
          val any = o("anyOf").flatMap(_.asArray).forall(_.exists(ok(_, v)))
          val typed = typesOf(o) match
            case Nil => true
            case ts => ts.exists {
              case "null"    => v.isNull
              case "string"  => v.isString
              case "integer" => v.asNumber.exists(_.toLong.isDefined)
              case "number"  => v.isNumber
              case "boolean" => v.isBoolean
              case "array"   => v.asArray.exists(_.forall(e => o("items").forall(ok(_, e))))
              case "object"  => v.asObject.exists { vo =>
                val props = o("properties").flatMap(_.asObject).getOrElse(JsonObject.empty)
                val req = o("required").flatMap(_.asArray).toList.flatten.flatMap(_.asString)
                req.forall(vo.contains) && vo.toList.forall((k, x) => props(k).exists(ok(_, x)))
              }
              case _ => false
            }
          any && typed
    ok(schema, value)

  private def decode(s: ResponseSchema[?], answer: Json): Either[AgentFailure, Any] =
    CliOutcomes.result(CliOutcome(Vector.empty, Vector.empty, Some(answer.noSpaces), TokenUsage.Zero, None, 1), "u", s, None)
      .finalAnswer

  test("a Codex answer (every optional field null) is valid under the strict schema; one leaving them out is not"):
    for (name, s) <- AnswerSchemas.all do
      val (codex, claude) = (instance(derived(s), nulls = true), instance(derived(s), nulls = false))
      assert(conforms(strict(s), codex), s"$name: ${codex.noSpaces}")
      if codex != claude then assert(!conforms(strict(s), claude), s"$name: strict mode requires every field")

  test("every step's answer decodes the same with its optional fields null as with them left out"):
    for (name, s) <- AnswerSchemas.all do
      val (codex, claude) = (instance(derived(s), nulls = true), instance(derived(s), nulls = false))
      val (a, b) = (decode(s, codex), decode(s, claude))
      assert(b.isRight, s"$name: ${claude.noSpaces} → $b")
      assertEquals(a, b, s"$name: ${codex.noSpaces}")
      // and re-encoded, the decoded answer is the same JSON — what the step's validate and checkpoint see
      def enc(x: Either[AgentFailure, Any]) = x.toOption.map(v => s.codec.asInstanceOf[io.circe.Codec[Any]](v))
      assertEquals(enc(a), enc(b), name)

  test("a required field sent as null is still refused"):
    val probe = Json.obj("citekey" -> Json.Null, "title" -> "X".asJson, "year" -> Json.Null)
    assert(decode(AnswerSchemas.probe, probe).isLeft)
