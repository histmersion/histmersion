package com.histmersion
package api

import com.github.plokhotnyuk.jsoniter_scala.core.{readFromString, writeToString}

import util.json.JsonCodec

enum Shown derives CanEqual:
  case Visible, Hidden

object Shown extends TwoStates[Shown]

final case class ShownLayer(shown: Shown, kept: Option[Shown]) derives JsonCodec, CanEqual

class TwoStatesSuite extends munit.FunSuite:

  test("a state is written as its case's name, in a record too"):
    assertEquals(writeToString(Shown.Hidden), "\"Hidden\"")
    assertEquals(
      writeToString(ShownLayer(Shown.Visible, Some(Shown.Hidden))),
      """{"shown":"Visible","kept":"Hidden"}"""
    )

  test("a case's name reads back"):
    assertEquals(readFromString[ShownLayer]("""{"shown":"Hidden","kept":null}"""), ShownLayer(Shown.Hidden, None))

  test("the Boolean a former version stored reads as the state it meant: true the first case, false the second"):
    assertEquals(
      readFromString[ShownLayer]("""{"shown":true,"kept":false}"""),
      ShownLayer(Shown.Visible, Some(Shown.Hidden))
    )
    assertEquals(readFromString[Shown]("false"), Shown.Hidden)

  test("any other word is refused"):
    intercept[Exception](readFromString[Shown]("\"Maybe\""))
    intercept[Exception](readFromString[Shown]("1"))
