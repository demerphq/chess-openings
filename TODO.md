# TODO

- Replace the current `Scripts/SeedBuilder` pipeline with node-based tooling under
  `node/`. The intent is to use `@chess-openings/eco.json` as the basis for a
  simpler unified opening dataset backed by the complete ECO opening catalogue,
  then combine that catalogue with game data to build a statistical picture of
  opening moves. This should eventually replace the current bespoke seed builder
  inputs and generated `Chess Openings/Resources/openings.json` workflow.
