package com.histmersion
package util.json

/** A whole number however a source writes it: Kindlings' `JsonNumber.toLong` and `toInt` read `-546.0` and `5e2` as
  * the whole numbers they are (a Cliopatria year is written `-546.0`), and refuse a fraction or a value out of range.
  */
class WholeNumbersSuite extends munit.FunSuite:

  private def number(text: String) = RawJson.parse(text).toOption.flatMap(_.asNumber)
  private def integer(text: String) = Cursor(RawJson.parse(text).toOption).integer

  test("an integral number written with a decimal point or an exponent is a whole number"):
    assertEquals(number("-546.0").flatMap(_.toLong), Some(-546L))
    assertEquals(number("-546.0").flatMap(_.toInt), Some(-546))
    assertEquals(number("5e2").flatMap(_.toLong), Some(500L))
    assertEquals(number("-546").flatMap(_.toLong), Some(-546L))

  test("a fraction or a value out of range is no whole number"):
    assertEquals(number("5.5").flatMap(_.toLong), None)
    assertEquals(number("9223372036854775808").flatMap(_.toLong), None)
    assertEquals(number("2147483648").flatMap(_.toInt), None)

  test("a cursor reads a whole number written as a number or as a string"):
    assertEquals(integer("-546.0"), Some(-546L))
    assertEquals(integer("\"-546\""), Some(-546L))
    assertEquals(integer("5.5"), None)
