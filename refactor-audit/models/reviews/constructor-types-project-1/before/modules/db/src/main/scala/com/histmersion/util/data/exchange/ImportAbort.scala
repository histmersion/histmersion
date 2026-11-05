package com.histmersion.util.data.exchange

/** A failure inside an import's transaction that must roll it back and still be reported as a value. */
final class ImportAbort(val problems: List[String]) extends RuntimeException(problems.mkString("; "))
