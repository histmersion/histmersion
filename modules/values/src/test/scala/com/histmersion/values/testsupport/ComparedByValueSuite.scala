package com.histmersion
package values.testsupport

import io.github.iltotore.iron.constraint.any.Pure

import com.histmersion.values.testsupport.ComparedByValue.given

object ComparedByValueSuite:
  type Name = Name.T
  object Name extends Newtype[String, Pure]

  type Label = Label.T
  object Label extends Newtype[String, Pure]

  type Count = Count.T
  object Count extends Newtype[Long, Pure]

class ComparedByValueSuite extends munit.FunSuite:
  import ComparedByValueSuite.*

  test("a newtype compares with a value of its own base type, in either order"):
    assertEquals(Name("a"), "a")
    assertEquals("a", Name("a"))
    assertEquals(Count(3L), 3L)
    assertEquals(3L, Count(3L))

  test("the rule passes through matching shapes: options, collections, maps, eithers and tuples"):
    assertEquals(Option(Name("a")), Some("a"))
    assertEquals(Option.empty[Name], None)
    assertEquals(List(Name("a")), List("a"))
    assertEquals(Set(Name("a")), Set("a"))
    assertEquals(Map(Name("a") -> List(Count(1L))), Map("a" -> List(1L)))
    assertEquals(Right(Name("a")): Either[String, Name], Right("a"))
    assertEquals((Name("a"), Count(1L), Option(Label("b"))), ("a", 1L, Some("b")))

  test("values of one type compare as munit compares them"):
    assertEquals(Name("a"), Name("a"))
    assertEquals(List(Name("a")), List(Name("a")))

  test("a newtype does not compare with another newtype or another base type, at the top or inside a shape"):
    val errors = List(
      compileErrors("assertEquals(Name(\"a\"), Label(\"a\"))"),
      compileErrors("assertEquals(Count(3L), 3)"),
      compileErrors("assertEquals(Name(\"a\"), 1)"),
      compileErrors("assertEquals(List(Name(\"a\")), List(1))"),
      compileErrors("assertEquals(Option(Name(\"a\")), Some(Label(\"a\")))"),
      compileErrors("assertEquals(Map(Name(\"a\") -> 1), Map(\"a\" -> 1L))"),
      compileErrors("assertEquals((Name(\"a\"), 1), (\"a\", \"b\"))"),
      compileErrors("assertEquals(Vector(Name(\"a\")), List(\"a\"))")
    )
    assert(errors.forall(_.nonEmpty), errors.mkString("\n"))
