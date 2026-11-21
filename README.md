# ForgeGens

Claim and share persistent, upgradeable lodestone generators in Minecraft Paper. Each blueprint trades production speed, output material, and buffer capacity.

| Blueprint | Output per cycle per level | Cycle | Buffer per level |
|---|---:|---:|---:|
| Standard | 1 iron nugget | 10s | 64 |
| Rapid | 1 gold nugget | 5s | 48 |
| Vault | 2 copper ingots | 20s | 128 |

## Build and install

Run `mvn clean verify` with Java 21. Copy the JAR from `target/` into the `plugins/` folder on Paper 1.21.11. GitHub Actions also packages a JAR when the workflow succeeds.

## Try it with two players

1. Place a lodestone. An operator with `forgegens.admin` looks at it and runs `/forge register rapid`.
2. A player looks at it and runs `/forge claim`. Use `/forge status` to inspect its buffer.
3. Grant a teammate collection access with `/forge grant <online-player-or-uuid>`. Have them collect with `/forge collect`, then verify `/forge upgrade` is reserved for the owner.
4. Try collecting with a full inventory. Free some space and collect again. Use `/forge audit` as owner to review the last 25 actions.
5. Restart the server and check elapsed production. `/forge revoke` removes a teammate's access. Operators can deregister a lodestone with `/forge remove`, which discards its buffer.

Production uses persisted timestamps, a twelve-hour catch-up cap and the blueprint buffer limit. It does not load generator chunks or spawn item entities. Forge state is stored in the plugin YAML. Existing generator records default to Standard and begin timestamp accrual on first evaluation. Upgrades cost four iron ingots per current level, up to level five.

## Network operations

`/forge network` groups available output by material for forges you own or share within 32 blocks. `/forge collectall` collects from eligible loaded lodestones nearest first and leaves overflow in the original buffers. Both commands work without targeting a block. A forge owner can use `/forge pause` and `/forge resume` while targeting a lodestone. Paused forges keep their buffers and produce no catch-up output while paused.

For a multiplayer demo, give a teammate access to one of two nearby generator types. Compare their network totals with yours, fill their inventory, and verify that a partial collection leaves the rest at the right forge. Restart with one forge paused and compare its production with a running forge.
