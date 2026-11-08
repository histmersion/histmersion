package com.histmersion
package domain.catalogue.evidence.passages

import com.histmersion.api.catalogue.MatchSpan
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.Title

/** A passage of a source's text a search found: where it starts in the body, its text, and where in it the matches are.
  */
final case class Passage(offset: Passage.Offset, text: Passage.Text, matches: List[MatchSpan])

object Passage:

  /** The sources whose text attests a data point, as the catalogue's evidence reads them: which source a citekey names
    * now (an old citekey resolves to it), and the passages of its text. The library implements it.
    */
  type Offset = Offset.T
  object Offset extends RefinedType[Int, Pure]
  type Text = Text.T
  object Text extends RefinedType[String, Pure]

  trait Source:
    /** The citekey the source named `citekey` has now; `None` when no source has it. */
    def canonical(citekey: Citekey): Option[Citekey]

    /** The sources whose text names `name` as a phrase, those naming it most often first, at most `limit`. */
    def naming(name: Title, limit: PageSize): List[Citekey]

    /** A reader of one source's text, read once however many searches are made in it. */
    def reader(citekey: Citekey): PassageReader

