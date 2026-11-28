package com.histmersion
package api

import com.github.plokhotnyuk.jsoniter_scala.core.{readFromString, writeToString}
import sttp.tapir.{Schema, SchemaType}

import util.json.JsonCodec
import api.json.TwoStates

enum Shown derives CanEqual:
  case Visible, Hidden

object Shown extends TwoStates[Shown]

final case class Panel(shown: Shown, kept: Option[Shown]) derives JsonCodec, CanEqual

class TwoStatesSuite extends munit.FunSuite:

  test("a state is written as the Boolean it replaced, true the first case and false the second, in a record too"):
    assertEquals(writeToString(Shown.Hidden), "false")
    assertEquals(writeToString(Shown.Visible), "true")
    assertEquals(
      writeToString(Panel(Shown.Visible, Some(Shown.Hidden))),
      """{"shown":true,"kept":false}"""
    )

  test("the Boolean reads back as the state it means"):
    assertEquals(
      readFromString[Panel]("""{"shown":true,"kept":false}"""),
      Panel(Shown.Visible, Some(Shown.Hidden))
    )
    assertEquals(readFromString[Panel]("""{"shown":false,"kept":null}"""), Panel(Shown.Hidden, None))

  test("its schema is a Boolean"):
    assertEquals(summon[Schema[Shown]].schemaType, SchemaType.SBoolean[Shown]())

  test("anything but a Boolean is refused, a case's name too"):
    intercept[Exception](readFromString[Shown]("\"Hidden\""))
    intercept[Exception](readFromString[Shown]("1"))
