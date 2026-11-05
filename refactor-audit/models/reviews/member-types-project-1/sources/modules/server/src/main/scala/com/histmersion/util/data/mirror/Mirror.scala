package com.histmersion.util.data.mirror

/** The Markdown mirror over the registered kinds: the note of one record of a registered kind, rendered by its owner.
  */
import com.histmersion.api.Api
import com.histmersion.util.data.DataOwner
import com.histmersion.util.data.mirror.MirrorPlacement
import io.github.iltotore.iron.*
final class Mirror(exports: List[Mirror.Export], notes: List[Mirror.Note]):
  def kinds: List[Mirror.Records] = exports.map(_.records)

  def note(records: Mirror.Records, id: com.histmersion.util.data.mirror.Mirror.NoteId): Either[Api.Error, com.histmersion.util.data.mirror.Mirror.NoteResultPart2] =
    exports
      .find(_.records == records)
      .flatMap(_ => notes.find(_.records == records))
      .toRight(Api.Error(s"no mirror notes of ${records.value} are registered"))
      .flatMap(_.note(id))

object Mirror:

  /** The Markdown mirror's notes of one kind of record: whose they are and where they go in the mirror's layout. Each
    * note is rendered by the owner's [[MirrorNote]].
    */
  type NoteResultPart2 = NoteResultPart2.T
  object NoteResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NoteId = NoteId.T
  object NoteId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  trait Export:
    def owner: DataOwner
    def records: Records
    def placement: MirrorPlacement

  /** How the owner of one kind of record renders a record's note, over its own services: the composition hands the
    * mirror one per registered kind (an old identifier answers with the record's current note).
    */
  trait Note:
    def records: Records
    def note(id: com.histmersion.util.data.mirror.Mirror.Note.NoteId): Either[Api.Error, com.histmersion.util.data.mirror.Mirror.Note.NoteResultPart2]

  object Note:
    type NoteResultPart2 = NoteResultPart2.T
    object NoteResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NoteId = NoteId.T
    object NoteId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The kind of record a mirror contribution writes notes of (`sources`, `articles`, …). */
  type Records = Records.T
  object Records extends RefinedType[String, Pure]
