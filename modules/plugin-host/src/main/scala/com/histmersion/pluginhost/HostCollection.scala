package com.histmersion
package pluginhost

import com.histmersion.plugin.*

import scala.util.control.NonFatal
import com.histmersion.domain.Title
import io.github.iltotore.iron.constraint.any.Pure

/** A plugin's [[SourceCollection]] as the host calls it: every call guarded (an exception or a linkage error becomes
  * the operation's `Left`, naming the collection), and every write refused unless the collection declares the
  * capability it needs — whatever the plugin's own member would do.
  */
final class HostCollection(val name: HostCollection.Name, collection: SourceCollection):
  def title: Title = Title(guardedValue(collection.title.value, name.value))
  def description: HostCollection.Description =
    HostCollection.Description(guardedValue(collection.description.value, ""))
  def capabilities: Set[CollectionCapability] = guardedValue(collection.capabilities, Set(CollectionCapability.Read))

  def reach: Either[HostCollection.Problem, Unit] = guarded(collection.reach)(_.value)
  def search(query: CollectionQuery): Either[HostCollection.Problem, List[CollectionItem]] =
    guarded(collection.search(query))(_.value)
  def item(id: CollectionId): Either[HostCollection.Problem, Option[CollectionItem]] =
    guarded(collection.item(id))(_.value)
  def admits(item: CollectionItem): Either[HostCollection.Problem, Unit] = guarded(collection.admits(item))(_.value)
  def file(
      item: CollectionItem,
      format: Option[SourceCollection.FileFormat]
  ): Either[HostCollection.Problem, CollectionFile] =
    guarded(collection.file(item, format))(_.value)
  def original(item: CollectionItem, file: CollectionFile): Either[HostCollection.Problem, FetchedFile] =
    guarded(collection.original(item, file))(_.value)
  def cover(item: CollectionItem): Either[HostCollection.Problem, Option[FetchedFile]] =
    guarded(collection.cover(item))(_.value)
  def audit(records: List[CollectionRecord]): Either[HostCollection.Problem, List[CollectionFinding]] =
    guarded(collection.audit(records))(_.value)

  def add(metadata: CollectionMetadata, file: FetchedFile): Either[HostCollection.Problem, CollectionId] =
    writing(CollectionCapability.Add)(collection.add(metadata, file))(_.value)
  def replace(
      id: CollectionId,
      key: SourceCollection.ReplaceKey,
      file: FetchedFile
  ): Either[HostCollection.Problem, HostCollection.Id] =
    writing(CollectionCapability.Add)(collection.replace(id, key, file))(_.value).map(r => HostCollection.Id(r.value))
  def update(id: CollectionId, metadata: CollectionMetadata): Either[HostCollection.Problem, List[CollectionChange]] =
    writing(CollectionCapability.Update)(collection.update(id, metadata))(_.value)
  def updateAll(
      links: List[CollectionLink],
      apply: SourceCollection.UpdateAllApply
  ): Either[HostCollection.Problem, List[CollectionUpdateEntry]] =
    if apply.value then writing(CollectionCapability.Update)(collection.updateAll(links, apply))(_.value)
    else guarded(collection.updateAll(links, apply))(_.value)
  def set(
      id: CollectionId,
      fields: List[(SourceCollection.FieldName, SourceCollection.FieldValue)]
  ): Either[HostCollection.Problem, Unit] =
    writing(CollectionCapability.Update)(collection.set(id, fields))(_.value)

  /** Whether the collection declares `capability`. */
  def can(capability: CollectionCapability): Boolean = capabilities.contains(capability)

  /** The refusal of a write the collection does not declare. */
  def readOnly: HostCollection.Problem =
    HostCollection.Problem(
      s"${title.value} is read-only here: ${guardedValue(collection.readOnlyReason.value, "it is read-only")}"
    )

  private def writing[E, A](capability: CollectionCapability)(call: => Either[E, A])(
      text: E => String
  ): Either[HostCollection.Problem, A] =
    if can(capability) then guarded(call)(text) else Left(readOnly)

  private def guarded[E, A](call: => Either[E, A])(text: E => String): Either[HostCollection.Problem, A] =
    try
      call match
        case null => Left(HostCollection.Problem(s"source collection '${name.value}' answered nothing"))
        case r    => r.left.map(e => HostCollection.Problem(text(e)))
    catch
      case e: LinkageError =>
        Left(
          HostCollection.Problem(
            s"source collection '${name.value}' failed: ${e.getClass.getSimpleName}: ${e.getMessage}"
          )
        )
      case NonFatal(e) =>
        Left(
          HostCollection.Problem(
            s"source collection '${name.value}' failed: ${e.getClass.getSimpleName}: ${e.getMessage}"
          )
        )

  private def guardedValue[A](value: => A, otherwise: A): A =
    try Option(value).getOrElse(otherwise)
    catch
      case _: LinkageError => otherwise
      case NonFatal(_)     => otherwise

object HostCollection:
  type Description = Description.T
  object Description extends Newtype[String, Pure]
  type Id = Id.T
  object Id extends Newtype[String, Pure]
  type Problem = Problem.T
  object Problem extends Newtype[String, Pure]
  type Name = Name.T
  object Name extends Newtype[String, Pure]
