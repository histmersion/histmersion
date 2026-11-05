package com.histmersion.domain.automation.agents

import com.histmersion.api.automation.AgentStateEntry
import com.histmersion.domain.automation.AutomationState
import com.histmersion.util.database.Transactor
import hearth.kindlings.optics.syntax.*
import io.circe.{Json, JsonObject}

import java.time.{Clock, Instant, ZoneOffset}
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicReference

/** The agents' scratch state: a JSON value under a key, by key. */
trait AgentStateService:
  def list(): List[AgentStateEntry]
  def get(key: com.histmersion.domain.automation.agents.AgentStateService.GetKey): Option[AgentStateEntry]

  /** Set a key's value; a value that is not JSON fails, as the database's cast to `jsonb` does. */
  def set(key: com.histmersion.domain.automation.agents.AgentStateService.SetKey, value: com.histmersion.domain.automation.agents.AgentStateService.SetValue): Unit

  /** Remove a key; false when there was none. */
  def delete(key: com.histmersion.domain.automation.agents.AgentStateService.DeleteKey): com.histmersion.domain.automation.agents.AgentStateService.DeleteResult

object AgentStateService:
  type GetKey = GetKey.T
  object GetKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetKey = SetKey.T
  object SetKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetValue = SetValue.T
  object SetValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeleteResult = DeleteResult.T
  object DeleteResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeleteKey = DeleteKey.T
  object DeleteKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(db: Transactor): AgentStateService = Implementation(db)

  final private class Implementation(db: Transactor) extends AgentStateService:
    def list(): List[AgentStateEntry] = db.read(AgentStateRepo.list())
    def get(key: com.histmersion.domain.automation.agents.AgentStateService.Implementation.GetKey): Option[AgentStateEntry] = db.read(AgentStateRepo.get(key))
    def set(key: com.histmersion.domain.automation.agents.AgentStateService.Implementation.SetKey, value: com.histmersion.domain.automation.agents.AgentStateService.Implementation.SetValue): Unit = db.transact(AgentStateRepo.set(key, value))
    def delete(key: com.histmersion.domain.automation.agents.AgentStateService.Implementation.DeleteKey): com.histmersion.domain.automation.agents.AgentStateService.Implementation.DeleteResult = db.transact(AgentStateRepo.delete(key))

  object Implementation:
    type GetKey = GetKey.T
    object GetKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetKey = SetKey.T
    object SetKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetValue = SetValue.T
    object SetValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DeleteResult = DeleteResult.T
    object DeleteResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DeleteKey = DeleteKey.T
    object DeleteKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The values in memory, printed back as `jsonb::text` prints them (an object's keys shortest first, then in byte
    * order, the last of a repeated key kept; `", "` and `": "` between); `clock` stands in for the database's `now()`.
    */
  final class InMemory(state: AtomicReference[AutomationState], clock: Clock) extends AgentStateService:
    private val Updated = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC)

    /** `updated_at::text` in a UTC session: seconds, the fraction without its trailing zeros, the offset. */
    private def updatedText(at: Instant): com.histmersion.domain.automation.agents.AgentStateService.InMemory.UpdatedTextResult =
      val micros = at.getNano / 1000
      val fraction = if micros == 0 then "" else "." + f"$micros%06d".reverse.dropWhile(_ == '0').reverse
      s"${Updated.format(at)}$fraction+00"

    /** jsonb's order of an object's keys: the shorter first, then by their UTF-8 bytes (unsigned, as `memcmp`). */
    private def keyOrder(k: com.histmersion.domain.automation.agents.AgentStateService.InMemory.KeyOrderK): (com.histmersion.domain.automation.agents.AgentStateService.InMemory.KeyOrderResultPart1, com.histmersion.domain.automation.agents.AgentStateService.InMemory.KeyOrderResultPart2) =
      val b = k.getBytes("UTF-8")
      (b.length, b.map(x => f"${x & 0xff}%02x").mkString)

    private def jsonb(j: Json): Json =
      j.arrayOrObject(
        j,
        a => Json.fromValues(a.map(jsonb)),
        o =>
          Json.fromJsonObject(
            JsonObject.fromIterable(
              o.toList.sortBy((k, _) => keyOrder(k)).map((k, v) => k -> jsonb(v))
            )
          )
      )

    private val printer =
      io.circe.Printer.noSpaces.copy(colonRight = " ", objectCommaRight = " ", arrayCommaRight = " ")

    private def entry(key: com.histmersion.domain.automation.agents.AgentStateService.InMemory.EntryKey, r: AgentStateRecord) = AgentStateEntry(key, r.value, Some(updatedText(r.updatedAt)))

    def list(): List[AgentStateEntry] = state.get.agentState.toList.sortBy(_._1).map(entry)
    def get(key: com.histmersion.domain.automation.agents.AgentStateService.InMemory.GetKey): Option[AgentStateEntry] = state.get.agentState.get(key).map(entry(key, _))
    def set(key: com.histmersion.domain.automation.agents.AgentStateService.InMemory.SetKey, value: com.histmersion.domain.automation.agents.AgentStateService.InMemory.SetValue): Unit =
      val j = io.circe.parser
        .parse(value)
        .fold(e => throw IllegalArgumentException(s"invalid input syntax for type json: $e"), identity)
      val at = Instant.now(clock)
      val _ =
        state.updateAndGet(_.modify(_.agentState).using(_.updated(key, AgentStateRecord(printer.print(jsonb(j)), at))))
    def delete(key: com.histmersion.domain.automation.agents.AgentStateService.InMemory.DeleteKey): com.histmersion.domain.automation.agents.AgentStateService.InMemory.DeleteResult =
      state.getAndUpdate(_.modify(_.agentState).using(_ - key)).agentState.contains(key)

  object InMemory:
    type UpdatedTextResult = UpdatedTextResult.T
    object UpdatedTextResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type KeyOrderResultPart1 = KeyOrderResultPart1.T
    object KeyOrderResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type KeyOrderResultPart2 = KeyOrderResultPart2.T
    object KeyOrderResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type KeyOrderK = KeyOrderK.T
    object KeyOrderK extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type EntryKey = EntryKey.T
    object EntryKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GetKey = GetKey.T
    object GetKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetKey = SetKey.T
    object SetKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetValue = SetValue.T
    object SetValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DeleteResult = DeleteResult.T
    object DeleteResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DeleteKey = DeleteKey.T
    object DeleteKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
