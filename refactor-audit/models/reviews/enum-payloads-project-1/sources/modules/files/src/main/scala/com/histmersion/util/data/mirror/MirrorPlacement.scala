package com.histmersion.util.data.mirror

/** Where the Markdown mirror puts the notes of one kind of record, under the directory `histmersion export <dir>` is
  * given (and `histmersion seed <dir>` reads back): the source notes in `<dir>/sources/<citekey>.md`, each article in
  * `<dir>/<kind>/<slug>.md`, the bookkeeping in `<dir>/meta/`. Paths are relative names; the client resolves them under
  * the directories it was given.
  */
enum MirrorPlacement:
  /** `<dir>/<directory>/<id>.md`: one directory of their own. */
  case InDirectory(name: InDirectory.Name)

  /** `<dir>/<type>/<id>.md`: a directory per the `type:` each note's frontmatter names. */
  case ByType

  /** `<dir>/<directory>/<file>`: one named file each. */
  case Named(name: Named.Name)

  /** The directory the notes go in, under the export's directory; `None` when each note's type decides it. */
  def directory: Option[com.histmersion.util.data.mirror.MirrorPlacement.DirectoryResult] = this match
    case InDirectory(d) => Some(d)
    case Named(d)       => Some(d)
    case ByType         => None

object MirrorPlacement:
  object InDirectory:
    type Name = Name.T
    object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  object Named:
    type Name = Name.T
    object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type DirectoryResult = DirectoryResult.T
  object DirectoryResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NoteFileResult = NoteFileResult.T
  object NoteFileResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NoteFileId = NoteFileId.T
  object NoteFileId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val SourcesDirectory = "sources"
  val MetaDirectory = "meta"

  val SourceFiles: MirrorPlacement = InDirectory(SourcesDirectory)
  val ArticleFiles: MirrorPlacement = ByType
  val MetaFiles: MirrorPlacement = Named(MetaDirectory)

  /** The file name of the note of the record `id`. */
  def noteFile(id: com.histmersion.util.data.mirror.MirrorPlacement.NoteFileId): com.histmersion.util.data.mirror.MirrorPlacement.NoteFileResult = s"$id.md"
