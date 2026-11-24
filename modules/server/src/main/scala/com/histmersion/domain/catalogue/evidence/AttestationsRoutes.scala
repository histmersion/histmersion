package com.histmersion
package domain.catalogue.evidence

import com.histmersion.api.Api
import com.histmersion.api.catalogue.Attestation
import com.histmersion.domain.Caller
import com.histmersion.{Offset, PageSize}
import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint
import com.histmersion.domain.Creator

/** The attestations' endpoints bound to their controllers, in their old order. */
object AttestationsRoutes:
  def apply(attestations: AttestationsControllers): List[ServerEndpoint[Any, Identity]] =
    given Creator.New = Creator.New.Unattributed(Creator.Surface.Rest)
    val me = Caller.Local
    List(
      Api.attestations.serverLogicSuccess[Identity](q =>
        attestations.list(
          q.citekey,
          q.targetType.map(t => Attestation.TargetType(t.value)),
          q.targetId.map(t => Attestation.TargetId(t.value)),
          q.limit.flatMap(l => PageSize.option(l.value)),
          q.offset.flatMap(o => Offset.option(o.value))
        )
      ),
      Api.getAttestation.serverLogic[Identity](id => attestations.get(id)),
      Api.createAttestation.serverLogic[Identity](in => attestations.create(me, in)),
      Api.patchAttestation.serverLogic[Identity]((id, p) => attestations.patch(me, id, p)),
      Api.deleteAttestation.serverLogic[Identity](id => attestations.delete(me, id))
    )
