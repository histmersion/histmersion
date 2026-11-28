package com.histmersion
package api

import com.github.plokhotnyuk.jsoniter_scala.core.{readFromString, writeToString}
import sttp.tapir.{Schema, SchemaType}

import util.json.JsonCodec
import api.json.NewtypeCodecs.given

enum Held derives CanEqual:
  case Held, Free

object Held extends TwoCases[Held]

final case class Slot(held: Held, kept: Option[Held]) derives JsonCodec, CanEqual

class TwoCasesSuite extends munit.FunSuite:

  test("a domain enum's lifted codec writes the Boolean it replaced, true the first case and false the second"):
    assertEquals(writeToString(Held.Free), "false")
    assertEquals(writeToString(Held.Held), "true")
    assertEquals(writeToString(Slot(Held.Held, Some(Held.Free))), """{"held":true,"kept":false}""")

  test("the Boolean reads back as the case it means"):
    assertEquals(readFromString[Slot]("""{"held":true,"kept":false}"""), Slot(Held.Held, Some(Held.Free)))
    assertEquals(readFromString[Slot]("""{"held":false,"kept":null}"""), Slot(Held.Free, None))

  test("its lifted schema is a Boolean"):
    assertEquals(summon[Schema[Held]].schemaType, SchemaType.SBoolean[Held]())

  test("anything but a Boolean is refused, a case's name too"):
    intercept[Exception](readFromString[Held]("\"Free\""))
    intercept[Exception](readFromString[Held]("0"))
