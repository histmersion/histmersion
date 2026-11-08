package com.histmersion
package api.settings
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.Write
import com.histmersion.api.library.Source
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

object ConfigEdit:

  enum Source(val wire: Source.Wire, val label: Source.Label):
    case Default extends Source(Source.Wire("default"), Source.Label("the default"))
    case File extends Source(Source.Wire("file"), Source.Label("the settings file"))
    case Environment extends Source(Source.Wire("environment"), Source.Label("an environment variable"))

  object Source:
    type Wire = Wire.T
    object Wire extends RefinedType[String, Pure]
    type Label = Label.T
    object Label extends RefinedType[String, Pure]


    def parse(s: Wire): Option[Source] = values.find(_.wire == Wire((s).value.trim.toLowerCase))

  final case class Credential(rule: Credential.Rule, form: Credential.Form, shown: Option[Credential.Shown] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema:
    def isSet: Boolean = (form != Credential.Form("unset"))

  object Credential:

    type Rule = Rule.T
    object Rule extends RefinedType[String, Pure]
    type Form = Form.T
    object Form extends RefinedType[String, Pure]
    type Shown = Shown.T
    object Shown extends RefinedType[String, Pure]

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
  ) derives KindlingsCodecAsObject,
        KindlingsSchema:
    def relative: Field.LocalKey = Field.LocalKey((key).value.stripPrefix("histmersion."))

  object Field:
    type LocalKey = LocalKey.T
    object LocalKey extends RefinedType[String, Pure]

    type Key = Key.T
    object Key extends RefinedType[String, Pure]
    export ConfigEdit.Section.{Id as Section}
    type Kind = Kind.T
    object Kind extends RefinedType[String, Pure]
    type Doc = Doc.T
    object Doc extends RefinedType[String, Pure]
    type Default = Default.T
    object Default extends RefinedType[String, Pure]
    type Value = Value.T
    object Value extends RefinedType[String, Pure]
    type Effective = Effective.T
    object Effective extends RefinedType[String, Pure]
    export ConfigEdit.Source.{Wire as Source}
    type EnvVar = EnvVar.T
    object EnvVar extends RefinedType[String, Pure]
    type Restart = Restart.T
    object Restart extends RefinedType[String, Pure]
    type Locked = Locked.T
    object Locked extends RefinedType[String, Pure]

  final case class Section(id: Section.Id, doc: Section.Doc = Section.Doc(""), fields: List[Field] = Nil)
      derives KindlingsCodecAsObject,
        KindlingsSchema

  object Section:
    type Id = Id.T
    object Id extends RefinedType[String, Pure]
    type Doc = Doc.T
    object Doc extends RefinedType[String, Pure]

  final case class Page(
      file: Option[Page.File] = None,
      exists: Page.Exists = Page.Exists(false),
      version: Page.Version = Page.Version("none"),
      sections: List[Section] = Nil,
      problems: List[Page.Problems] = Nil,
      restartable: Page.Restartable = Page.Restartable(false),
      setupDue: Page.SetupDue = Page.SetupDue(false)
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema:
    def fields: List[Field] = sections.flatMap(_.fields)
    def field(key: Field.Key): Option[Field] =
      val full = if (key).value.startsWith("histmersion.") then key else s"histmersion.$key"
      fields.find(_.key == full)

  object Page:
    type Selector = Selector.T
    object Selector extends RefinedType[String, Pure]

    type File = File.T
    object File extends RefinedType[String, Pure]
    type Exists = Exists.T
    object Exists extends RefinedType[Boolean, Pure]
    type Version = Version.T
    object Version extends RefinedType[String, Pure]
    type Problems = Problems.T
    object Problems extends RefinedType[String, Pure]
    type Restartable = Restartable.T
    object Restartable extends RefinedType[Boolean, Pure]
    type SetupDue = SetupDue.T
    object SetupDue extends RefinedType[Boolean, Pure]



  final case class Change(key: Change.Key, value: Option[Change.Value] = None) derives KindlingsCodecAsObject, KindlingsSchema

  object Change:
    export Field.Key
    type Value = Value.T
    object Value extends RefinedType[String, Pure]

  final case class Write(
      changes: List[Change],
      version: Option[Write.Version] = None,
      literalConsent: Write.LiteralConsent = Write.LiteralConsent(false),
      reload: Write.Reload = Write.Reload(true)
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Write:
    type Version = Version.T
    object Version extends RefinedType[String, Pure]
    type LiteralConsent = LiteralConsent.T
    object LiteralConsent extends RefinedType[Boolean, Pure]
    type Reload = Reload.T
    object Reload extends RefinedType[Boolean, Pure]



  final case class RestartNote(key: RestartNote.Key, why: RestartNote.Why) derives KindlingsCodecAsObject, KindlingsSchema

  object RestartNote:
    export Field.Key
    type Why = Why.T
    object Why extends RefinedType[String, Pure]

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
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Written:
    type File = File.T
    object File extends RefinedType[String, Pure]
    type Changed = Changed.T
    object Changed extends RefinedType[String, Pure]
    type Unchanged = Unchanged.T
    object Unchanged extends RefinedType[String, Pure]
    type Backup = Backup.T
    object Backup extends RefinedType[String, Pure]
    type Version = Version.T
    object Version extends RefinedType[String, Pure]
    type Created = Created.T
    object Created extends RefinedType[Boolean, Pure]
    type Warnings = Warnings.T
    object Warnings extends RefinedType[String, Pure]
    type Restartable = Restartable.T
    object Restartable extends RefinedType[Boolean, Pure]



  final case class Restart(accepted: Restart.Accepted, message: Restart.Message) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object Restart:
    type Accepted = Accepted.T
    object Accepted extends RefinedType[Boolean, Pure]
    type Message = Message.T
    object Message extends RefinedType[String, Pure]



  val CodeChanged = "settings_changed"

  val CodeRefused = "settings_refused"

  val CodeUnavailable = "settings_unavailable"
