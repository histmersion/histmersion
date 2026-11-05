package compilerfixture

object ComparisonNegative extends munit.Assertions:
  def unrelated(): Unit = assertEquals(Values.Text("one"), Values.Other("one"))
