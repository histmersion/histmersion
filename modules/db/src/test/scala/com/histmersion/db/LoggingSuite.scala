package com.histmersion.db

/** Library logs (Flyway, HikariCP, pgjdbc) reach scribe through SLF4J (#824). slf4j-api 2.x finds its binding through
  * ServiceLoader; with a 1.7-style binding on the classpath it found none, printed "No SLF4J providers were found" and
  * dropped every library log on the floor.
  */
class LoggingSuite extends munit.FunSuite:
  test("SLF4J is bound to scribe, not to the no-op fallback"):
    val factory = org.slf4j.LoggerFactory.getILoggerFactory
    assert(!factory.isInstanceOf[org.slf4j.helpers.NOPLoggerFactory], factory.getClass.getName)
    assert(factory.getClass.getName.startsWith("scribe."), factory.getClass.getName)
