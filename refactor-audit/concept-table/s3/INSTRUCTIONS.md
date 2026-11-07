# Deciding what each newtype really is (module core, the domain logic)

A tool wrapped primitives in Histmersion's Scala code with one type per position (`ClassifyRaw`, `DescribeResult`, `ActivityPart1Part2`).
The user wants one concept, one type within a layer; different purposes, different types. Output one line per type, tab-separated, every type exactly once, nothing else:

    <fqn>	<decision>

Decisions:
- `keep`: a real property of its owner with its own meaning: `Plugin.Id`, `LandingConfig.AllowHosts`. In config, a setting usually stays its own type. Default for `original` types.
- `<fqn>`: the same thing as a type under CONCEPTS NEARBY or in concepts-kernel.txt, with the SAME base type.
- `new:<full owner path>.<Name>`: a real concept with no type yet, when the name is positional or says where it is used (ending Result, Part1, Arg1, S, Raw, Value, Str). The owner is the entity or feature object in the type's own package. Give the full path starting `com.histmersion.`. Use the same name for the same meaning.
- `native`: plain type, ONLY for a Boolean predicate's result or a loop index over a collection. Never for a record field.

Rules: never map across base types. Never map to `com.histmersion.api...`: the domain does not use API types. `core` sees its own types and those in concepts-kernel.txt. Error messages are `new:<Owner>.Problem`. Counts, limits, offsets, ids and years stay distinct.
