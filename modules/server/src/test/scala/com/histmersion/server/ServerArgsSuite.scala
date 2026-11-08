package com.histmersion
package server

import com.histmersion.testsupport.ComparedByValue.given

class ServerArgsSuite extends munit.FunSuite:

  test("no arguments serves; --self-check and --help are the only flags"):
    assertEquals(ServerArgs.parse(Nil), Right(ServerMode.Serve))
    assertEquals(ServerArgs.parse(List("--self-check")), Right(ServerMode.SelfCheck))
    assertEquals(ServerArgs.parse(List("--help")), Right(ServerMode.Help))
    assertEquals(ServerArgs.parse(List("-h")), Right(ServerMode.Help))

  test("anything else is an error, so the binary exits before it opens anything"):
    assertEquals(ServerArgs.parse(List("--version")), Left("unknown argument '--version'"))
    assertEquals(ServerArgs.parse(List("serve")), Left("unknown argument 'serve'"))
    assertEquals(ServerArgs.parse(List("--self-check", "--help")), Left("one argument at most: --self-check --help"))
    assertEquals(ServerArgs.parse(List("--self-check", "x")), Left("one argument at most: --self-check x"))
    assert(
      ServerArgs.Usage.value.contains("--self-check") && ServerArgs.Usage.value.contains("opens no database connection")
    )
