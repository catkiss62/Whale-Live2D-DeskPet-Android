# Repository handoff rules

1. Before changing source, read `docs/WHALE_TASK_LEDGER.md` completely.
2. This repository is only for the Q-version whale Live2D Android pet. Do not copy Sen-specific
   appearance, outfit or performance parameters into it.
3. Never commit the purchased model ZIP, moc3, textures, expressions, motions or full VTube Studio
   configuration. They are imported from local storage at runtime.
4. Every source/UI/build change must update the task ledger in the same work round.
5. Compilation success is not real-device confirmation. Move an item to confirmed only after the
   user explicitly reports the result.
6. The checked-in debug keystore is public test signing only and must never be used for a release.
