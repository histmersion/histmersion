import scalafix.v1._
import scala.meta._
import java.nio.file.{Files,Paths}

class CodecProbeSuites extends SyntacticRule("CodecProbeSuites") {
  override def afterComplete(): Unit = {
    implicit val dialect: Dialect = dialects.Scala3
    val base = "modules/db/src/test/scala/com/histmersion/"
    val files = List(
      "util/database/ColumnProbe.scala" -> """package com.histmersion.util.database

import com.augustnagro.magnum.DbCodec
import java.lang.reflect.Proxy
import java.sql.{PreparedStatement, ResultSet}

object ColumnProbe:
  def read[A](raw: String)(using codec: DbCodec[A]): A =
    val result = Proxy.newProxyInstance(classOf[ResultSet].getClassLoader, Array(classOf[ResultSet]),
      (_, method, _) => method.getName match
        case "getString" => raw
        case "wasNull" => Boolean.box(raw == null)
        case name => throw UnsupportedOperationException(name)
    ).asInstanceOf[ResultSet]
    codec.readSingle(result, 1)

  def write[A](value: A)(using codec: DbCodec[A]): Option[String] =
    var recorded: Option[Option[String]] = None
    val statement = Proxy.newProxyInstance(classOf[PreparedStatement].getClassLoader, Array(classOf[PreparedStatement]),
      (_, method, args) => method.getName match
        case "setString" | "setObject" =>
          recorded = Some(Option(args(1)).map(_.toString))
          null
        case "setNull" =>
          recorded = Some(None)
          null
        case name => throw UnsupportedOperationException(name)
    ).asInstanceOf[PreparedStatement]
    codec.writeSingle(value, statement, 1)
    recorded.getOrElse(throw IllegalStateException("No column was written"))
""",
      "domain/library/sources/SourceColumnsSuite.scala" -> """package com.histmersion.domain.library.sources

import com.histmersion.domain.library.Source
import com.histmersion.util.database.ColumnProbe
import com.histmersion.util.database.given

class SourceColumnsSuite extends munit.FunSuite:
  test("stored source statuses decode trimmed and case-insensitive"):
    assertEquals(ColumnProbe.read[Source.Status](" CURRENT "), Source.Status.Current)
  test("unknown and null statuses remain unassessed"):
    assertEquals(ColumnProbe.read[Source.Status]("bogus"), Source.Status.Unassessed)
    assertEquals(ColumnProbe.read[Source.Status](null), Source.Status.Unassessed)
  test("source status writes its storage spelling"):
    assertEquals(ColumnProbe.write[Source.Status](Source.Status.Current), Some("current"))
""",
      "domain/library/findings/FindingColumnsSuite.scala" -> """package com.histmersion.domain.library.findings

import com.histmersion.domain.library.Confidence
import com.histmersion.util.database.ColumnProbe
import com.histmersion.util.database.given

class FindingColumnsSuite extends munit.FunSuite:
  test("stored confidence decodes trimmed and case-insensitive"):
    assertEquals(ColumnProbe.read[Option[Confidence]](" LOW "), Some(Confidence.Low))
  test("unknown and null confidence remain absent"):
    assertEquals(ColumnProbe.read[Option[Confidence]]("certain"), None)
    assertEquals(ColumnProbe.read[Option[Confidence]](null), None)
  test("confidence writes its storage spelling or SQL null"):
    assertEquals(ColumnProbe.write[Option[Confidence]](Some(Confidence.Low)), Some("low"))
    assertEquals(ColumnProbe.write[Option[Confidence]](None), None)
""",
      "domain/catalogue/places/PlaceColumnsSuite.scala" -> """package com.histmersion.domain.catalogue.places

import com.histmersion.domain.catalogue.Place
import com.histmersion.util.database.ColumnProbe
import com.histmersion.util.database.given

class PlaceColumnsSuite extends munit.FunSuite:
  test("stored precision decodes trimmed and case-insensitive"):
    assertEquals(ColumnProbe.read[Place.Precision](" GRID-CELL "), Place.Precision.GridCell)
  test("null means stated while an unknown precision stays conservative"):
    assertEquals(ColumnProbe.read[Place.Precision](null), Place.Precision.Stated)
    assertEquals(ColumnProbe.read[Place.Precision]("unknown"), Place.Precision.GridCell)
  test("precision writes grid-cell or SQL null for stated"):
    assertEquals(ColumnProbe.write[Place.Precision](Place.Precision.GridCell), Some("grid-cell"))
    assertEquals(ColumnProbe.write[Place.Precision](Place.Precision.Stated), None)
"""
    )
    files.foreach { case(p,s) => require(!Files.exists(Paths.get(base+p))); s.parse[Source].get }
    files.foreach { case(p,s) => val path = Paths.get(base+p); Files.createDirectories(path.getParent); Files.writeString(path,s) }
    println("Generated four parsed codec probe test sources; assertions frozen before extraction")
  }
}
