import scalafix.v1._
import scala.meta._

class AddTargetRegression extends SyntacticRule("AddTargetRegression") {
  private val testName = "wire coordinates are checked before becoming domain coordinates"
  private val test = """

  test("wire coordinates are checked before becoming domain coordinates"):
    def point(lat: Double, lon: Double) = Target(
      kind = MarkKind.Point,
      lat = Some(com.histmersion.api.catalogue.Latitude(lat)),
      lon = Some(com.histmersion.api.catalogue.Longitude(lon))
    )
    assert(MarkTarget.fromWire(point(90.0, 180.0)).nonEmpty)
    assert(MarkTarget.fromWire(point(-90.0, -180.0)).nonEmpty)
    assertEquals(MarkTarget.fromWire(point(90.1, 0.0)), None)
    assertEquals(MarkTarget.fromWire(point(0.0, -180.1)), None)
    assertEquals(MarkTarget.fromWire(point(Double.NaN, 0.0)), None)
    assertEquals(MarkTarget.fromWire(point(0.0, Double.PositiveInfinity)), None)
"""
  override def fix(implicit doc: SyntacticDocument): Patch = {
    implicit val dialect: Dialect = dialects.Scala3
    test.parse[Stat].get
    val added = doc.tree.collect {
      case c: Defn.Class if c.name.value == "PersonalTypesSuite" &&
        !c.collect { case Lit.String(s) => s }.contains(testName) =>
          Patch.addRight(c.templ.body.stats.last, test)
    }.asPatch
    val trailing = doc.tokens.reverse.takeWhile(t => t.is[Token.EOF] || t.is[Token.Whitespace]).filter(_.is[Token.Whitespace])
    added + Patch.removeTokens(trailing.drop(1))
  }
}
