# Primitive and existing-type inventory

3159 files; 28 source modules; 107460 declared/inferred positions; 62229 primitive leaves in 57168 positions; 46914 unresolved leaves.
130 Iron companions; 2 opaque definitions; 228 aliases; 11477 unreviewed name/base proposals.

30393 primitive leaves in 28205 production member positions (excluding alias definitions). See model-candidates.tsv; module layer hints are configuration, not verified ownership.

All main/test sources are scanned; member and local positions are reported separately. Counts include mechanisms and intentional wire/storage primitives: these are inventory candidates, not confirmed violations.
position-roles.tsv distinguishes record fields, trait contracts, other constructors, non-public members, locals, tests and explicitly configured boundaries. model-contract-candidates.tsv prioritizes production record fields and trait contracts without deleting other candidates. Roles describe declaration context, not business meaning or permission to keep a primitive.
Declared and inferred symbol signatures are traversed, including container/function/tuple type arguments and transparent aliases. Opaque and Iron types are recorded as existing newtypes. Abstract types, missing metadata and unsupported semantic forms remain explicit unresolved entries.
newtype-constraints.tsv inventories direct Iron parents, base types and constraint symbols. Pure and known non-Pure identities are configured; transparent parameterless aliases are expanded. Generic/external unresolved constraints remain explicit. Client constraint matches require authority review; inherited newtype definitions still need further analysis.
Uses are resolved symbol references, not whole-program data-flow proofs. Proposal groups share a name and base type only; no equivalence or exemption is approved automatically. Review units, purpose, ownership, constraints, layers and phantom type parameters before migration.
Generated implicit arguments and synthetic declarations are not enumerated as source positions. Scala 3 SemanticDB does not expose complete expression types; dispatch, inferred local widening and implicit conversion flows require further analysis.
