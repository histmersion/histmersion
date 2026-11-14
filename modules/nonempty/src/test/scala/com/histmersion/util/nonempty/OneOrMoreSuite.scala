package com.histmersion
package util.nonempty

class OneOrMoreSuite extends munit.FunSuite:

  test("a non-empty vector has a head and keeps its order"):
    val v = NonEmptyVector.of(1, 2, 3)
    assertEquals(v.head, 1)
    assertEquals(v.tail, Vector(2, 3))
    assertEquals(v.last, 3)
    assertEquals(v.size, 3)
    assertEquals(v.toVector, Vector(1, 2, 3))

  test("only a non-empty vector converts"):
    assertEquals(NonEmptyVector.from(Vector.empty[Int]), None)
    assertEquals(NonEmptyVector.from(Vector(4)).map(_.toVector), Some(Vector(4)))

  test("map, the tests and the concatenations"):
    val v = NonEmptyVector.of(1, 2)
    assertEquals(v.map(_ * 10).toVector, Vector(10, 20))
    assert(v.exists(_ == 2))
    assert(v.forall(_ > 0))
    assertEquals((v ++ NonEmptyVector.of(3)).toVector, Vector(1, 2, 3))
    assertEquals(v.concat(Vector(3, 4)).toVector, Vector(1, 2, 3, 4))
    assertEquals((v :+ 5).toVector, Vector(1, 2, 5))

  test("two non-empty vectors compare as their elements do"):
    assert(NonEmptyVector.of("a", "b") == NonEmptyVector.of("a", "b"))
    assert(NonEmptyVector.of("a") != NonEmptyVector.of("b"))
    assert(compileErrors("NonEmptyVector.of(1) == NonEmptyVector.of(\"a\")").contains("cannot be compared"))
