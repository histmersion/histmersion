package com.histmersion
package util.json

import com.github.plokhotnyuk.jsoniter_scala.core.{readFromString, writeToString}

enum Shown derives CanEqual:
  case Visible, Hidden

object Shown extends TwoStates[Shown]

final case class Layer(shown: Shown, kept: Option[Shown]) derives JsonCodec, CanEqual

class TwoStatesSuite extends munit.FunSuite:

  test("a state is written as its case's name, in a record too"):
    assertEquals(writeToString(Shown.Hidden), "\"Hidden\"")
    assertEquals(writeToString(Layer(Shown.Visible, Some(Shown.Hidden))), """{"shown":"Visible","kept":"Hidden"}""")

  test("a case's name reads back"):
    assertEquals(readFromString[Layer]("""{"shown":"Hidden","kept":null}"""), Layer(Shown.Hidden, None))

  test("the Boolean a former version stored reads as the state it meant: true the first case, false the second"):
    assertEquals(readFromString[Layer]("""{"shown":true,"kept":false}"""), Layer(Shown.Visible, Some(Shown.Hidden)))
    assertEquals(readFromString[Shown]("false"), Shown.Hidden)

  test("any other word is refused"):
    intercept[Exception](readFromString[Shown]("\"Maybe\""))
    intercept[Exception](readFromString[Shown]("1"))
