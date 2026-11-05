package com.histmersion.util.data.mirror

/** Where the Markdown mirror puts the notes of one kind of record, under the directory `histmersion export <dir>` is
  * given (and `histmersion seed <dir>` reads back): the source notes in `<dir>/sources/<citekey>.md`, each article in
  * `<dir>/<kind>/<slug>.md`, the bookkeeping in `<dir>/meta/`. Paths are relative names; the client resolves them under
  * the directories it was given.
  */
enum MirrorPlacement:
  /** `<dir>/<directory>/<id>.md`: one directory of their own. */
  case InDirectory(name: String)

  /** `<dir>/<type>/<id>.md`: a directory per the `type:` each note's frontmatter names. */
  case ByType

  /** `<dir>/<directory>/<file>`: one named file each. */
  case Named(name: String)

  /** The directory the notes go in, under the export's directory; `None` when each note's type decides it. */
  def directory: Option[String] = this match
    case InDirectory(d) => Some(d)
    case Named(d)       => Some(d)
    case ByType         => None

object MirrorPlacement:
  val SourcesDirectory = "sources"
  val MetaDirectory = "meta"

  val SourceFiles: MirrorPlacement = InDirectory(SourcesDirectory)
  val ArticleFiles: MirrorPlacement = ByType
  val MetaFiles: MirrorPlacement = Named(MetaDirectory)

  /** The file name of the note of the record `id`. */
  def noteFile(id: String): String = s"$id.md"
