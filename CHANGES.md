# dayzhud 1.1.0 hotfix - creative tab registry

**One changed file.** Unzip over the repo root, replacing
`src/main/java/com/dayzhud/mod/registry/ModCreativeTabs.java`. Nothing else in the 1.1.0
update needs to change.

## What was wrong

    DeferredRegister.create(ForgeRegistries.CREATIVE_MODE_TABS, ...)   // no such field

Creative tabs are a vanilla registry in 1.20.1, not a Forge one. `ForgeRegistries` exposes
only a ResourceKey for them under `ForgeRegistries.Keys`, so the usual
`ForgeRegistries.<THING>` pattern that works for ITEMS, MENU_TYPES and SOUND_EVENTS does not
work here. The fix builds the DeferredRegister from `Registries.CREATIVE_MODE_TAB`
(`net.minecraft.core.registries.Registries`) instead.

## The eleven warnings are pre-existing and harmless

`new ResourceLocation(String, String)` and `FMLJavaModLoadingContext.get()` are flagged
"deprecated and marked for removal" by this Forge build. Six of those call sites predate this
update (NetworkHandler, OverlayCanceller, DayzHudOverlay, SkillsScreen, ModSounds,
SkillCapability); they still compile and still work on 1.20.1. Worth a sweep at some point,
but not part of this fix - changing them is a separate, mechanical edit across the tree and
should not ride along with a build break.
