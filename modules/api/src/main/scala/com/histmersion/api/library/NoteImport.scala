package com.histmersion.api.library
import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.media.AssetInput
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class NoteImport(
    text: NoteImport.Text,
    citekey: Option[com.histmersion.api.library.Citekey] = None,
    body: Option[NoteImport.Body] = None,
    mtime: Option[NoteImport.Mtime] = None,
    force: NoteImport.Force = _root_.com.histmersion.api.library.NoteImport.Force(false),
    assets: List[AssetInput] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object NoteImport:
  export com.histmersion.api.library.Source.{Notes as Text, Notes as Body}
  type Mtime = Mtime.T
  object Mtime extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Force = Force.T
  object Force extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
