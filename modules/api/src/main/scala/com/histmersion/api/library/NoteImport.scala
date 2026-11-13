package com.histmersion
package api.library
import scala.annotation.tailrec
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.media.AssetInput
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class NoteImport(
    text: NoteImport.Text,
    citekey: Option[Citekey] = None,
    body: Option[NoteImport.Body] = None,
    mtime: Option[NoteImport.Mtime] = None,
    force: NoteImport.Force = NoteImport.Force(false),
    assets: List[AssetInput] = Nil
) derives JsonCodec,
      KindlingsSchema,
      com.histmersion.api.json.Schema

object NoteImport:
  export Source.{Notes as Body, Notes as Text}
  type Mtime = Mtime.T
  object Mtime extends Newtype[Long, Pure]
  type Force = Force.T
  object Force extends Newtype[Boolean, Pure]
