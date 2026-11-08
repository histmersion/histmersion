package com.histmersion
package plugins.ai

import io.circe.parser.parse

/** The schema repairs each API needs, as pure functions over JSON. */
class SchemaJsonSuite extends munit.FunSuite:

  private def schema(s: String) = SchemaJson(parse(s).fold(e => fail(e.getMessage), identity))
  private def text(s: SchemaJson) = s.json.noSpaces

  test("a tool's input for Anthropic: an object type and empty properties where none are given"):
    assertEquals(text(schema("""{"title":"X"}""").objectTyped), """{"title":"X","type":"object","properties":{}}""")
    assertEquals(text(schema("true").objectTyped), """{"type":"object","properties":{}}""")
    // a shape given elsewhere is left alone
    assertEquals(
      text(schema("""{"anyOf":[{"type":"string"}]}""").objectTyped),
      """{"anyOf":[{"type":"string"}],"type":"object"}"""
    )
    assertEquals(text(schema("[1]").objectTyped), "[1]")

  test("the answer for Anthropic: additionalProperties false on every object with properties, unless it says"):
    val closed = schema(
      """{"type":"object","properties":{"a":{"type":"object","properties":{"b":{"type":"string"}}},"m":{"type":"object","properties":{},"additionalProperties":true}}}"""
    ).closed
    assertEquals(
      text(closed),
      """{"type":"object","properties":{"a":{"type":"object","properties":{"b":{"type":"string"}},"additionalProperties":false},"m":{"type":"object","properties":{},"additionalProperties":true}},"additionalProperties":false}"""
    )

  test("OpenAI's strict form: all required, the optional nullable (enums too), no extra properties, in order"):
    val strict = schema(
      """{"title":"T","type":"object","required":["a"],"properties":{"a":{"type":"string"},"b":{"type":"integer"},"c":{"type":"string","enum":["x","y"]},"d":{"$ref":"#/$defs/D"},"e":{"type":["string","null"]}},"$defs":{"D":{"type":"object"}}}"""
    ).strict
    assertEquals(
      text(strict),
      """{"additionalProperties":false,"required":["a","b","c","d","e"],"title":"T","type":"object","properties":{"a":{"type":"string"},"b":{"type":["integer","null"]},"c":{"type":["string","null"],"enum":["x","y",null]},"d":{"anyOf":[{"$ref":"#/$defs/D"},{"type":"null"}]},"e":{"type":["string","null"]}},"$defs":{"D":{"additionalProperties":false,"type":"object","properties":{}}}}"""
    )

  test("OpenAI's strict form: a discriminated union keeps its extra properties; a bare root becomes an object"):
    assertEquals(
      text(schema("""{"type":"object","discriminator":{},"properties":{"k":{"type":"string"}}}""").strict),
      """{"required":["k"],"type":"object","discriminator":{},"properties":{"k":{"type":["string","null"]}}}"""
    )
    assertEquals(text(schema("{}").strict), """{"type":"object","properties":{},"additionalProperties":false}""")
    assertEquals(text(schema("false").strict), """{"additionalProperties":false,"type":"object","properties":{}}""")
