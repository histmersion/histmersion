package com.histmersion
package util.database

import com.augustnagro.magnum.DbCodec
import java.lang.reflect.Proxy
import java.sql.{PreparedStatement, ResultSet}
import com.histmersion.lint.{tolerated, LintRule}

object ColumnProbe:
  def read[A](raw: String)(using codec: DbCodec[A]): A =
    val result = Proxy
      .newProxyInstance(
        classOf[ResultSet].getClassLoader,
        Array(classOf[ResultSet]),
        (_, method, _) =>
          method.getName match
            case "getString" => raw
            case "wasNull"   => Boolean.box(raw == null)
            case name        => throw UnsupportedOperationException(name)
      )
      .asInstanceOf[ResultSet]
    codec.readSingle(result, 1)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def write[A](value: A)(using codec: DbCodec[A]): Option[String] =
    var recorded: Option[Option[String]] = None
    val statement = Proxy
      .newProxyInstance(
        classOf[PreparedStatement].getClassLoader,
        Array(classOf[PreparedStatement]),
        (_, method, args) =>
          method.getName match
            case "setString" | "setObject" =>
              recorded = Some(Option(args(1)).map(_.toString))
              null
            case "setNull" =>
              recorded = Some(None)
              null
            case name => throw UnsupportedOperationException(name)
      )
      .asInstanceOf[PreparedStatement]
    codec.writeSingle(value, statement, 1)
    recorded.getOrElse(throw IllegalStateException("No column was written"))
