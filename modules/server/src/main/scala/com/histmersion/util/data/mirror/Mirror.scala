package com.histmersion
package util.data.mirror

import com.histmersion.api.Problem

/** The Markdown mirror over the registered kinds: the note of one record of a registered kind, rendered by its owner.
  */
import com.histmersion.api.Api
import com.histmersion.util.data.DataOwner
import com.histmersion.util.data.mirror.MirrorPlacement
import io.github.iltotore.iron.*
final class Mirror(exports: List[Mirror.Export], notes: List[Mirror.Note]):
  def kinds: List[Mirror.Records] = exports.map(_.records)

  def note(records: Mirror.Records, id: MirrorPlacement.RecordId): Either[Api.Error, Frontmatter.NoteBody] =
    exports
      .find(_.records == records)
      .flatMap(_ => notes.find(_.records == records))
      .toRight(Api.Error(Problem(s"no mirror notes of ${records.value} are registered")))
      .flatMap(_.note(id))

object Mirror:

  /** The Markdown mirror's notes of one kind of record: whose they are and where they go in the mirror's layout. Each
    * note is rendered by the owner's [[MirrorNote]].
    */

  trait Export:
    def owner: DataOwner
    def records: Records
    def placement: MirrorPlacement

  /** How the owner of one kind of record renders a record's note, over its own services: the composition hands the
    * mirror one per registered kind (an old identifier answers with the record's current note).
    */
  trait Note:
    def records: Records
    def note(id: MirrorPlacement.RecordId): Either[Api.Error, Frontmatter.NoteBody]

  /** The kind of record a mirror contribution writes notes of (`sources`, `articles`, …). */
  type Records = Records.T
  object Records extends Newtype[String, Pure]
