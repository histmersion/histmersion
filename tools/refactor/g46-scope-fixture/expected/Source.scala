package com.histmersion
package api.library

import com.histmersion.api.library.Values.Doi
import com.histmersion.api.library.Values.Title as Heading
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

// C1 REWRITE: a pair in the companion of a case class, used by the class's own signature and by creator applications
final case class Source(doi: Source.SourceDoi, title: Source.Title)

object Source:
  export com.histmersion.api.library.Values.{Doi as SourceDoi}
  // C2 REWRITE: a name imported under another name is exported from its owner under the alias's name
  export com.histmersion.api.library.Values.Title

object Matcher:
  type Text = Text.T
  object Text extends RefinedType[String, Pure]
  // C3 UNCHANGED: a member of the alias's own object cannot be exported
  type Query = Text
  val Query: Text.type = Text
  // C4 REWRITE: an imported name is exported from its owner
  export com.histmersion.api.library.Values.{Doi as OtherDoi}

object Uses:
  def make: Source = Source(Source.SourceDoi("10.1/x"), Source.Title("t"))
  def other: Matcher.OtherDoi = Matcher.OtherDoi("10.1/y")
  def query: Matcher.Query = Matcher.Query("q")
