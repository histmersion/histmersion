package com.histmersion.db

/** A bound parameter. Typed (rather than `Any`) so tests can assert on it and the writer can bind it without reflection
  * — which also keeps the native image happy.
  */
enum Param:
  case S(v: String)
  case L(v: Long)
  case I(v: Int)
  case D(v: Double)

  /** A `text[]` parameter — bound as a real SQL array, so a list crosses as ONE value. */
  case A(v: List[String])
