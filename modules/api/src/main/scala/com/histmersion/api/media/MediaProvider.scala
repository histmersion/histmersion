package com.histmersion
package api.media

import com.histmersion.api.library.Source
import com.histmersion.api.given
import com.histmersion.api.plugins.PluginDescription
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.TwoStates
object MediaProvider:
  type Id = Id.T
  object Id extends Newtype[String, Pure]

  val Source = "source"
  val Commons = "wikimedia-commons"

  val Calibre = "calibre"

  val OpenLibrary = "open-library"

  val Met = "met-museum"
  val Cleveland = "cleveland-museum"
  val ArtInstitute = "art-institute-chicago"

  val LibraryOfCongress = "library-of-congress"

  val CommonsDepicts = "wikimedia-commons-depicts"

  val OpenContext = "open-context"

  val Pleiades = "pleiades"

  val Smithsonian = "smithsonian"

  val Europeana = "europeana"
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val all: List[Id] = List(
    Id(Source),
    Id(Commons),
    Id(Calibre),
    Id(OpenLibrary),
    Id(Met),
    Id(Cleveland),
    Id(ArtInstitute),
    Id(LibraryOfCongress),
    Id(OpenContext),
    Id(Smithsonian),
    Id(Europeana)
  )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val museums: Set[Id] = Set(Id(Met), Id(Cleveland), Id(ArtInstitute))

  /** Covers carry the publisher's rights, no reuse licence: shown in the local UI only, never carried in a bundle. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val covers: Set[Id] = Set(Id(Calibre), Id(OpenLibrary))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def title(p: Id): Info.Title = p.value match
    case Source            => Info.Title("source figures")
    case Commons           => Info.Title("Wikimedia Commons")
    case Calibre           => Info.Title("Calibre library")
    case OpenLibrary       => Info.Title("Open Library")
    case Met               => Info.Title("The Metropolitan Museum of Art")
    case Cleveland         => Info.Title("The Cleveland Museum of Art")
    case ArtInstitute      => Info.Title("The Art Institute of Chicago")
    case LibraryOfCongress => Info.Title("Library of Congress")
    case CommonsDepicts    => Info.Title("Wikimedia Commons (depictions)")
    case OpenContext       => Info.Title("Open Context")
    case Pleiades          => Info.Title("Pleiades (linked Commons files)")
    case Smithsonian       => Info.Title("Smithsonian Institution")
    case Europeana         => Info.Title("Europeana")
    case other             => Info.Title(other)

  final case class Info(
      name: MediaItem.Provider,
      title: Info.Title,
      over: Info.Over,
      description: Info.Description,
      plugin: PluginDescription.Id,
      builtin: Info.Builtin,
      licences: Info.Licences
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object Info:
    type Title = Title.T
    object Title extends Newtype[String, Pure]
    type Over = Over.T
    object Over extends Newtype[String, Pure]
    type Description = Description.T
    object Description extends Newtype[String, Pure]
    enum Builtin derives CanEqual:
      case Builtin, Plugin
    object Builtin extends TwoStates[Builtin]:
      extension (builtin: Builtin) def builtin: Boolean = builtin == Builtin
    type Licences = Licences.T
    object Licences extends Newtype[String, Pure]
