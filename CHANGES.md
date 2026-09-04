# dayzhud 2.1.1 - build fix

**Complete.** 52 files. Unzip over the repo root, and see `DELETE.txt`.

## What broke - three leftovers from swapping the search implementations

1. `NetworkHandler` still imported and registered `SearchPackets`, from the duplicate search
   system I deleted two builds ago.
2. `SearchConfig` lost `SEARCH_CORPSES` and `SEARCH_CONTAINERS` when I replaced my version
   with the wired one, but both redirects were already calling them.
3. `DayzHudMod` registered `SearchConfig.SPEC` **twice** - the same config file registered
   twice would have thrown at load even after it compiled.

All three are the same root cause: I kept the interrupted turn's implementation and deleted
mine, and did not sweep for references to the half I removed.

## Why the checks missed all three

The first-party symbol scan matched symbol NAMES against a hand-written list of prefixes -
`Market|Wallet|Tacz|...` - which never included `Search`. A brand-new package was invisible
to it. Then two deeper problems: a name-based list cannot see a reference to a **deleted**
class, because the name is by definition no longer in the tree; and it never looked at
unresolved **constants** at all, which is what `SEARCH_CORPSES` is.

It now keys on javac's own `location:` line instead. `location: package com.dayzhud.mod.search`
or `location: class SearchConfig` means javac searched *our* package or *our* class and came
up empty - which is conclusive regardless of what Minecraft types are missing. Filtering out
absent MC imports needed two rules worth stating: a missing class only counts when the location
is one of our packages, and a missing "variable" only counts when it is SCREAMING_CASE, because
an unresolved Minecraft class used statically (`Commands.literal`) is reported as a missing
variable named after the class.

Confirmed against the shipped 2.1.0 tree - it now reports exactly the three CI errors:

    class SearchPackets missing from com.dayzhud.mod.search
    variable SEARCH_CONTAINERS missing from SearchConfig
    variable SEARCH_CORPSES missing from SearchConfig

Nine checks now, verdict line last.

## Verification

`RESULT: PASS (9 checks)` against the extracted zip.
