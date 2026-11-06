package com.histmersion.util.data.mirror

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
  def directory: Option[com.histmersion.util.data.mirror.MirrorPlacement.Directory] = this match
    case InDirectory(d) => Some(d)
    case Named(d)       => Some(d)
    case ByType         => None

object MirrorPlacement:
  type Directory = Directory.T
  object Directory extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NoteFile = NoteFile.T
  object NoteFile extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordId = RecordId.T
  object RecordId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val SourcesDirectory: Directory = Directory("sources")
  val MetaDirectory: Directory = Directory("meta")

  val SourceFiles: MirrorPlacement = InDirectory(SourcesDirectory)
  val ArticleFiles: MirrorPlacement = ByType
  val MetaFiles: MirrorPlacement = Named(MetaDirectory)

  /** The file name of the note of the record `id`. */
  def noteFile(id: com.histmersion.util.data.mirror.MirrorPlacement.RecordId): com.histmersion.util.data.mirror.MirrorPlacement.NoteFile = NoteFile(s"$id.md")
