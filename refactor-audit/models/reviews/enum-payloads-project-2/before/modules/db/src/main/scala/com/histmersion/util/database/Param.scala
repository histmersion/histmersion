package com.histmersion.util.database
enum Param:
  case S(v: String)
  case L(v: Long)
  case I(v: Int)
  case D(v: Double)

  /** A `text[]` parameter — bound as a real SQL array, so a list crosses as ONE value. */
  case A(v: List[String])
