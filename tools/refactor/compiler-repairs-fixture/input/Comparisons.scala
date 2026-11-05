package compilerfixture

object Comparisons extends munit.Assertions:
  def check(): Unit =
    assertEquals(Values.Text("one"), "one")
    assertEquals(List(Values.Text("one")), List("one"))
    assertEquals(Option(Values.Text("one")), Some("one"))
    assertEquals(Map("key" -> Values.Text("one")), Map("key" -> "one"))
    assertEquals((Values.Text("one"), Values.Count(7)), ("one", 7))
  def mismatch(): Unit = assertEquals(Values.Text("one"), "two")
  def leftError(value: Either[Values.Other, Int]): Unit = assertEquals(value, Left("bad"))
  def refinedTuple(value: Values.Text): Unit = assertEquals((value.value, Values.Count(7)), ("one", 7))
