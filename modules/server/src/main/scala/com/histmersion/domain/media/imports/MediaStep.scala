package com.histmersion.domain.media.imports

/** One write of an import that may fail on its own: logged and listed, never the end of the run. */
object MediaStep:
  type FailedKey = FailedKey.T
  object FailedKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def each[A](failed: scala.collection.mutable.Builder[com.histmersion.domain.media.imports.MediaStep.FailedKey, List[com.histmersion.domain.media.imports.MediaStep.Problem]])(what: => String)(write: => A): Option[A] =
    try Some(write)
    catch
      case e: InterruptedException        => throw e
      case scala.util.control.NonFatal(e) =>
        scribe.warn(s"media import: $what: ${e.getMessage}")
        failed += s"$what: ${e.getClass.getSimpleName}: ${e.getMessage}"
        None
