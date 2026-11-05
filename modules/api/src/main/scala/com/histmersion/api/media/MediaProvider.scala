package com.histmersion.api.media

import com.histmersion.api.library.Source
import com.histmersion.api.given
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
object MediaProvider:
  type All = All.T
  object All extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Museums = Museums.T
  object Museums extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Covers = Covers.T
  object Covers extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TitleResult = TitleResult.T
  object TitleResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TitleP = TitleP.T
  object TitleP extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

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
  val all: List[com.histmersion.api.media.MediaProvider.All] = List(
    Source,
    Commons,
    Calibre,
    OpenLibrary,
    Met,
    Cleveland,
    ArtInstitute,
    LibraryOfCongress,
    OpenContext,
    Smithsonian,
    Europeana
  )

  val museums: Set[com.histmersion.api.media.MediaProvider.Museums] = Set(Met, Cleveland, ArtInstitute)

  /** Covers carry the publisher's rights, no reuse licence: shown in the local UI only, never carried in a bundle. */
  val covers: Set[com.histmersion.api.media.MediaProvider.Covers] = Set(Calibre, OpenLibrary)

  def title(p: com.histmersion.api.media.MediaProvider.TitleP): com.histmersion.api.media.MediaProvider.TitleResult = p match
    case Source            => "source figures"
    case Commons           => "Wikimedia Commons"
    case Calibre           => "Calibre library"
    case OpenLibrary       => "Open Library"
    case Met               => "The Metropolitan Museum of Art"
    case Cleveland         => "The Cleveland Museum of Art"
    case ArtInstitute      => "The Art Institute of Chicago"
    case LibraryOfCongress => "Library of Congress"
    case CommonsDepicts    => "Wikimedia Commons (depictions)"
    case OpenContext       => "Open Context"
    case Pleiades          => "Pleiades (linked Commons files)"
    case Smithsonian       => "Smithsonian Institution"
    case Europeana         => "Europeana"
    case other             => other

  final case class Info(
      name: com.histmersion.api.media.MediaItem.Provider,
      title: Info.Title,
      over: Info.Over,
      description: Info.Description,
      plugin: Info.Plugin,
      builtin: Info.Builtin,
      licences: Info.Licences
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Info:
    type Title = Title.T
    object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Over = Over.T
    object Over extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Description = Description.T
    object Description extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Plugin = Plugin.T
    object Plugin extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Builtin = Builtin.T
    object Builtin extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Licences = Licences.T
    object Licences extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
