# Jarvis Feature Coverage

This map includes only behavior with an existing named JVM test. The CI validator checks that each referenced test method remains present in its declared test file.

| Feature | Implementation | Named test method |
|---|---|---|
| English wake-word prefix removal | `JarvisCommandNormalizer.stripWakeWord` | `app/src/test/java/com/openjarvis/agent/JarvisCommandNormalizerTest.kt::stripsEnglishWakeWordOnlyAtCommandStart` |
| Devanagari wake-word prefix removal | `JarvisCommandNormalizer.stripWakeWord` | `app/src/test/java/com/openjarvis/agent/JarvisCommandNormalizerTest.kt::stripsDevanagariWakeWord` |
| Wake-word text preserved inside query/message | `JarvisCommandNormalizer.stripWakeWord` | `app/src/test/java/com/openjarvis/agent/JarvisCommandNormalizerTest.kt::preservesWakeWordInsideQueryAndMessageBody` |
| Hindi trailing app-launch verbs | `StepIntentParser.parse` | `app/src/test/java/com/openjarvis/agent/StepIntentParserTest.kt::hindiTrailingVerbLaunchesInstagramInsteadOfNavigation` |
| App command LLM timeout cap | `AppCommandLlmPolicy` | `app/src/test/java/com/openjarvis/agent/StepIntentParserTest.kt::appCommandLlmFallbackIsCappedAtTenSeconds` |
| Alarm and timer intent parsing | `StepIntentParser.parse` | `app/src/test/java/com/openjarvis/agent/StepIntentParserTest.kt::alarms`; `app/src/test/java/com/openjarvis/agent/StepIntentParserTest.kt::timers` |
| Torch and volume intent parsing (parser only) | `StepIntentParser.parse` | `app/src/test/java/com/openjarvis/agent/StepIntentParserTest.kt::torchAndVolume` |
| Explicit navigation intent guard | `SkillRegistry.hasExplicitNavigationIntent` | `app/src/test/java/com/openjarvis/skills/SkillRegistryNavigationTest.kt::mapsRequiresExplicitNavigationIntent` |
| App launch is not classified as Maps | `SkillRegistry.parse` | `app/src/test/java/com/openjarvis/skills/SkillRegistryNavigationTest.kt::instagramLaunchNeverClassifiesAsMaps` |
| Hybrid chat stays local when cloud disabled | `HybridChatRouter.complete` | `app/src/test/java/com/openjarvis/llm/HybridChatRouterTest.kt::cloudDisabledUsesLocalProvider` |
| Cloud prompt excludes local private memory | `HybridChatRouter.complete` | `app/src/test/java/com/openjarvis/llm/HybridChatRouterTest.kt::cloudReceivesOnlyCloudSafePromptAndCurrentUserTurn` |
| Secondary cloud provider fallback | `HybridChatRouter.complete` | `app/src/test/java/com/openjarvis/llm/HybridChatRouterTest.kt::failedPrimaryTriesSecondaryBeforeLocal` |
| Local fallback receives full local prompt | `HybridChatRouter.complete` | `app/src/test/java/com/openjarvis/llm/HybridChatRouterTest.kt::cloudFailureFallsBackWithFullLocalPrompt` |
| Missing cloud keys and disabled local fallback | `HybridChatRouter.complete` | `app/src/test/java/com/openjarvis/llm/HybridChatRouterTest.kt::missingKeysUsesLocalAndDisabledFallbackFails` |
| Hybrid chat cloud-off and local-fallback defaults | `HybridChatSettingsPolicy` | `app/src/test/java/com/openjarvis/llm/HybridChatSettingsPolicyTest.kt::defaultsKeepCloudOffAndLocalFallbackOn` |
| Provider model defaults and normalization | `HybridChatSettingsPolicy.normalizeModel` | `app/src/test/java/com/openjarvis/llm/HybridChatSettingsPolicyTest.kt::providerModelsHaveSafeDefaults`; `app/src/test/java/com/openjarvis/llm/HybridChatSettingsPolicyTest.kt::modelInputIsTrimmedBoundedAndControlCharactersAreRemoved` |
| Invalid provider storage fallback | `HybridChatSettingsPolicy.providerFromStorage` | `app/src/test/java/com/openjarvis/llm/HybridChatSettingsPolicyTest.kt::invalidStoredProviderFallsBackToGemini` |
| Separate encrypted provider credential keys | `HybridChatSettingsStore` constants | `app/src/test/java/com/openjarvis/llm/HybridChatSettingsPolicyTest.kt::providerCredentialsUseDistinctEncryptedPreferenceKeys` |
| Command sequence splitting without splitting message body | `CommandSplitter.split` | `app/src/test/java/com/openjarvis/agent/CommandSplitterTest.kt::messageTextIsNeverSplit`; `app/src/test/java/com/openjarvis/agent/CommandSplitterTest.kt::commaAndWeakConnectorSplitCommandSequenceButNeverMessageBody` |
| YouTube query parsing (not playback) | `SearchCommandParser.parseYouTubeQuery` | `app/src/test/java/com/openjarvis/agent/SearchCommandParserTest.kt::youtubeOpenThenSearch`; `app/src/test/java/com/openjarvis/agent/SearchCommandParserTest.kt::youtubeVariants` |

## UNTESTED

The repository does not currently have a mapped named test method proving these end-to-end executor/device behaviors:

- Mute/unmute execution
- App launch foreground confirmation
- YouTube playback start/selection verification
- WhatsApp end-to-end contact resolution, confirmation, and send flow
- Volume executor
- Torch executor

Parser-level intent tests do not count as proof that Android device actions execute successfully.
