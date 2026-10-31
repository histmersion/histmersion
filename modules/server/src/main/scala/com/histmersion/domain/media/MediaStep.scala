package com.histmersion.domain.media

/** One write of an import that may fail on its own: logged and listed, never the end of the run. */
object MediaStep:
  def each[A](failed: scala.collection.mutable.Builder[String, List[String]])(what: => String)(write: => A): Option[A] =
    try Some(write)
    catch
      case e: InterruptedException        => throw e
      case scala.util.control.NonFatal(e) =>
        scribe.warn(s"media import: $what: ${e.getMessage}")
        failed += s"$what: ${e.getClass.getSimpleName}: ${e.getMessage}"
        None
