package com.histmersion
package api.settings

import com.histmersion.util.problem.Problem

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.Write
import com.histmersion.api.library.Source
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

object ConfigEdit:

  enum Source(val wire: Source.Wire, val label: Source.Label) derives CanEqual:
    case Default extends Source(Source.Wire("default"), Source.Label("the default"))
    case File extends Source(Source.Wire("file"), Source.Label("the settings file"))
    case Environment extends Source(Source.Wire("environment"), Source.Label("an environment variable"))

  object Source:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]
    type Label = Label.T
    object Label extends Newtype[String, Pure]

    def parse(s: Wire): Option[Source] = values.find(_.wire == Wire((s).value.trim.toLowerCase))

  final case class Credential(rule: Credential.Rule, form: Credential.Form, shown: Option[Credential.Shown] = None)
      derives JsonCodec,
        KindlingsSchema:
    def isSet: Boolean = (form != Credential.Form("unset"))

  object Credential:

    type Rule = Rule.T
    object Rule extends Newtype[String, Pure]
    type Form = Form.T
    object Form extends Newtype[String, Pure]
    type Shown = Shown.T
    object Shown extends Newtype[String, Pure]

  final case class Field(
      key: Field.Key,
      section: Field.Section,
      kind: Field.Kind,
      doc: Field.Doc = Field.Doc(""),
      default: Option[Field.Default] = None,
      value: Option[Field.Value] = None,
      effective: Option[Field.Effective] = None,
      source: Field.Source = _root_.com.histmersion.api.settings.ConfigEdit.Source.Default.wire,
      envVar: Option[Field.EnvVar] = None,
      restart: Option[Field.Restart] = None,
      credential: Option[Credential] = None,
      locked: Option[Field.Locked] = None
  ) derives JsonCodec,
        KindlingsSchema:
    def relative: Field.LocalKey = Field.LocalKey((key).value.stripPrefix("histmersion."))

  object Field:
    type LocalKey = LocalKey.T
    object LocalKey extends Newtype[String, Pure]

    type Key = Key.T
    object Key extends Newtype[String, Pure]:
      extension (key: Key)
        /** The key with the `histmersion.` prefix every full key has. */
        def qualified: Key = if key.value.startsWith("histmersion.") then key else Key(s"histmersion.${key.value}")
    export ConfigEdit.Section.Id as Section
    type Kind = Kind.T
    object Kind extends Newtype[String, Pure]
    type Doc = Doc.T
    object Doc extends Newtype[String, Pure]
    type Default = Default.T
    object Default extends Newtype[String, Pure]
    type Value = Value.T
    object Value extends Newtype[String, Pure]
    type Effective = Effective.T
    object Effective extends Newtype[String, Pure]
    export ConfigEdit.Source.Wire as Source
    type EnvVar = EnvVar.T
    object EnvVar extends Newtype[String, Pure]
    type Restart = Restart.T
    object Restart extends Newtype[String, Pure]
    type Locked = Locked.T
    object Locked extends Newtype[String, Pure]

  final case class Section(id: Section.Id, doc: Section.Doc = Section.Doc(""), fields: List[Field] = Nil)
      derives JsonCodec,
        KindlingsSchema

  object Section:
    type Id = Id.T
    object Id extends Newtype[String, Pure]
    type Doc = Doc.T
    object Doc extends Newtype[String, Pure]

  final case class Page(
      file: Option[Page.File] = None,
      exists: Page.Exists = Page.Exists(false),
      version: Page.Version = Page.Version("none"),
      sections: List[Section] = Nil,
      problems: List[Page.Problems] = Nil,
      restartable: Page.Restartable = Page.Restartable(false),
      setupDue: Page.SetupDue = Page.SetupDue(false)
  ) derives JsonCodec,
        KindlingsSchema,
        com.histmersion.api.json.Schema:
    def fields: List[Field] = sections.flatMap(_.fields)
    def field(key: Field.Key): Option[Field] =
      fields.find(_.key == key.qualified)

  object Page:
    type Selector = Selector.T
    object Selector extends Newtype[String, Pure]

    type File = File.T
    object File extends Newtype[String, Pure]
    type Exists = Exists.T
    object Exists extends Newtype[Boolean, Pure]
    type Version = Version.T
    object Version extends Newtype[String, Pure]
    type Problems = Problems.T
    object Problems extends Newtype[String, Pure]
    type Restartable = Restartable.T
    object Restartable extends Newtype[Boolean, Pure]
    type SetupDue = SetupDue.T
    object SetupDue extends Newtype[Boolean, Pure]

  final case class Change(key: Change.Key, value: Option[Change.Value] = None) derives JsonCodec, KindlingsSchema

  object Change:
    export Field.Key
    type Value = Value.T
    object Value extends Newtype[String, Pure]

  final case class Write(
      changes: List[Change],
      version: Option[Write.Version] = None,
      literalConsent: Write.LiteralConsent = Write.LiteralConsent(false),
      reload: Write.Reload = Write.Reload(true)
  ) derives JsonCodec,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object Write:
    type Version = Version.T
    object Version extends Newtype[String, Pure]
    type LiteralConsent = LiteralConsent.T
    object LiteralConsent extends Newtype[Boolean, Pure]
    type Reload = Reload.T
    object Reload extends Newtype[Boolean, Pure]

  final case class RestartNote(key: RestartNote.Key, why: Problem) derives JsonCodec, KindlingsSchema

  object RestartNote:
    export Field.Key

  final case class Written(
      file: Written.File,
      changed: List[Written.Changed] = Nil,
      unchanged: List[Written.Unchanged] = Nil,
      backup: Option[Written.Backup] = None,
      version: Written.Version = Written.Version("none"),
      created: Written.Created = Written.Created(false),
      restart: List[RestartNote] = Nil,
      warnings: List[Written.Warnings] = Nil,
      reload: Option[ReloadReport] = None,
      restartable: Written.Restartable = Written.Restartable(false)
  ) derives JsonCodec,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object Written:
    type File = File.T
    object File extends Newtype[String, Pure]
    type Changed = Changed.T
    object Changed extends Newtype[String, Pure]
    type Unchanged = Unchanged.T
    object Unchanged extends Newtype[String, Pure]
    type Backup = Backup.T
    object Backup extends Newtype[String, Pure]
    type Version = Version.T
    object Version extends Newtype[String, Pure]
    type Created = Created.T
    object Created extends Newtype[Boolean, Pure]
    type Warnings = Warnings.T
    object Warnings extends Newtype[String, Pure]
    type Restartable = Restartable.T
    object Restartable extends Newtype[Boolean, Pure]

  /** What the server says once a restart is under way. */
  type Notice = Notice.T
  object Notice extends Newtype[String, Pure]

  final case class Restart(accepted: Restart.Accepted, notice: Notice)
      derives JsonCodec,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object Restart:
    type Accepted = Accepted.T
    object Accepted extends Newtype[Boolean, Pure]

  val CodeChanged = "settings_changed"

  val CodeRefused = "settings_refused"

  val CodeUnavailable = "settings_unavailable"
