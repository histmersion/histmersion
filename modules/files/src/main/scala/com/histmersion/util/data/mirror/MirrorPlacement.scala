package com.histmersion
package util.data.mirror

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** Where the Markdown mirror puts the notes of one kind of record, under the directory `histmersion export <dir>` is
  * given (and `histmersion seed <dir>` reads back): the source notes in `<dir>/sources/<citekey>.md`, each article in
  * `<dir>/<kind>/<slug>.md`, the bookkeeping in `<dir>/meta/`. Paths are relative names; the client resolves them under
  * the directories it was given.
  */
enum MirrorPlacement:
  /** `<dir>/<directory>/<id>.md`: one directory of their own. */
  case InDirectory(name: MirrorPlacement.Directory)

  /** `<dir>/<type>/<id>.md`: a directory per the `type:` each note's frontmatter names. */
  case ByType

  /** `<dir>/<directory>/<file>`: one named file each. */
  case Named(name: MirrorPlacement.Directory)

  /** The directory the notes go in, under the export's directory; `None` when each note's type decides it. */
  def directory: Option[MirrorPlacement.Directory] = this match
    case InDirectory(d) => Some(d)
    case Named(d)       => Some(d)
    case ByType         => None

object MirrorPlacement:
  type Directory = Directory.T
  object Directory extends RefinedType[String, Pure]
  type NoteFile = NoteFile.T
  object NoteFile extends RefinedType[String, Pure]
  type RecordId = RecordId.T
  object RecordId extends RefinedType[String, Pure]

  val SourcesDirectory: Directory = Directory("sources")
  val MetaDirectory: Directory = Directory("meta")

  val SourceFiles: MirrorPlacement = InDirectory(SourcesDirectory)
  val ArticleFiles: MirrorPlacement = ByType
  val MetaFiles: MirrorPlacement = Named(MetaDirectory)

  /** The file name of the note of the record `id`. */
  def noteFile(id: RecordId): NoteFile = NoteFile(s"$id.md")
