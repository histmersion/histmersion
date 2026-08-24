import sbt.*

/** Centralised dependency versions (meta-build; imported by build.sbt). */
object Dependencies {
  object V {
    val catsEffect = "3.6.1"
    val http4s     = "0.23.30"
    val tapir      = "1.11.24"
    val doobie     = "1.0.0-RC9"
    val jsoniter   = "2.31.3"
    val decline    = "2.4.1"
    val postgis    = "2023.1.0"
    val logback    = "1.5.12"
    val munit      = "1.0.4"
  }
}
