package com.histmersion.spike.cremabench

import java.util.function.Supplier

/** 25.5 re-run, question (a): the same out-of-surface JDK call as JdkUse (java.util.zip.Adler32, never used by the
  * host), but inside a catch-all. If the image threw a LinkageError/UnsatisfiedLinkError-like error instead of
  * aborting, this would print "caught …" and the process would exit normally.
  */
final class JdkCatch extends Supplier[String]:
  def get(): String =
    val r =
      try
        val a = java.util.zip.Adler32()
        a.update("crema".getBytes("UTF-8"))
        s"adler32=${a.getValue}"
      catch case e: Throwable => s"caught ${e.getClass.getName}: ${e.getMessage}"
    s"$r (process still alive)"
