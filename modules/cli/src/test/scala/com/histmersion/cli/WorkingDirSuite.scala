package com.histmersion.cli

/** #901: a working directory macOS denies the native CLI becomes one plain message, not a stack trace. */
class WorkingDirSuite extends munit.FunSuite:

  // what GraalVM's SystemPropertiesSupport throws when getcwd fails
  private val graal = new Error("Properties init: Could not determine current working directory.")

  test("the GraalVM working-directory error is recognised, also as a cause or an error text"):
    assert(WorkingDir.denied(graal))
    assert(WorkingDir.denied(new ExceptionInInitializerError(graal)))
    assert(WorkingDir.denied(new RuntimeException("wrapped", new IllegalStateException("x", graal))))
    assert(WorkingDir.denied("server unreachable: Properties init: Could not determine current working directory."))

  test("any other failure is not it"):
    assert(!WorkingDir.denied(new RuntimeException("connection refused")))
    assert(!WorkingDir.denied(new RuntimeException(null: String)))
    assert(!WorkingDir.denied("connection refused"))

  test("the message says what to do, and is no stack trace"):
    val m = WorkingDir.message
    assert(m.contains("Files and Folders") && m.contains("another directory"), m)
    assert(!m.contains("at com.") && !m.contains("Exception"), m)
