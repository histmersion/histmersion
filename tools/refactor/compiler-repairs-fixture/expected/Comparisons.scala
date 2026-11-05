package compilerfixture

object Comparisons extends munit.Assertions:
  def check(): Unit =
    assertEquals(((Values.Text("one")).value: String), "one")
    assertEquals(((List(Values.Text("one"))).map(refactorValue => (refactorValue).value): List[String]), List("one"))
    assertEquals(((Option(Values.Text("one"))).map(refactorValue2 => (refactorValue2).value): Option[String]), Some("one"))
    assertEquals(((Map("key" -> Values.Text("one"))).iterator.map { case (refactorKey, refactorItem) => (refactorKey, (refactorItem).value) }.toMap: Map[String, String]), Map("key" -> "one"))
    assertEquals(({ val refactorTuple = (Values.Text("one"), Values.Count(7)); ((refactorTuple._1).value, (refactorTuple._2).value) }: (String, Int)), ("one", 7))
  def mismatch(): Unit = assertEquals(((Values.Text("one")).value: String), "two")
