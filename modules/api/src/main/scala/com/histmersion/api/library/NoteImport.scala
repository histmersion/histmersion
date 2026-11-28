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
import com.histmersion.api.automation.Creation
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.json.TwoStates

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class NoteImport(
    text: NoteImport.Text,
    citekey: Option[Citekey] = None,
    body: Option[NoteImport.Body] = None,
    mtime: Option[NoteImport.Mtime] = None,
    force: NoteImport.Force = NoteImport.Force.Safe,
    assets: List[AssetInput] = Nil,
    createdBy: Option[Creation.By] = None,
    via: Option[Creation.Via] = None
) extends Creation.Requested derives JsonCodec, KindlingsSchema, api.json.Schema

object NoteImport:
  export Source.{Notes as Body, Notes as Text}
  type Mtime = Mtime.T
  object Mtime extends Newtype[Long, Pure]
  enum Force derives CanEqual:
    case Force, Safe
  object Force extends TwoStates[Force]:
    extension (force: Force) def force: Boolean = force == Force
