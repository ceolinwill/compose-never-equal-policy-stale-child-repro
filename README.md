# `neverEqualPolicy()` + same list instance: a child composable keeps showing the old list

Reported in https://github.com/android/skills/issues/281.

The Jetpack Compose performance skill in [android/skills](https://github.com/android/skills) (`jetpack-compose/jetpack-compose-performance/references/state-and-stability.md`, section "3.A Optimize whole-collection updates with `neverEqualPolicy()`", and the "Collection State Tracking" bullet in its `SKILL.md`) recommends keeping one `MutableList`, mutating it, and reassigning the same instance to `mutableStateOf(list, neverEqualPolicy())`. That invalidates every scope that reads the state. But with strong skipping (on by default), a restartable child that receives the list as a parameter compares it by instance (`===`). The instance never changes, so the child skips and keeps showing the old contents, while the parent that read the state shows the new size.

## Reproduction

Needs JDK 17+ and an Android SDK with platform 37 (set `ANDROID_HOME`, or `sdk.dir` in `local.properties`).

```sh
./gradlew :repro:testDebugUnitTest
```

The test is [`NeverEqualPolicyStaleChildTest.kt`](repro/src/test/kotlin/repro/NeverEqualPolicyStaleChildTest.kt) (Robolectric, Compose UI test). `ActiveQueueManager` is the skill's snippet verbatim, with `Item` = `String`. Each test composes:

```kotlin
val items = manager.items.value
Column {
    BasicText("parent: ${items.size} items")
    QueueList(items) // @Composable fun QueueList(items: List<String>)
}
```

then calls `manager.enqueue("b")` and checks that both the parent and the child show it.

Output:

```text
NeverEqualPolicyStaleChildTest > skillPattern_childShowsNewItem FAILED
    java.lang.AssertionError: Failed: assertExists.
    Reason: Expected exactly '1' node but could not find any node that satisfies: (Text + InputText + EditableText contains 'child: b' (ignoreCase: false))
NeverEqualPolicyStaleChildTest > newListEachUpdate_childShowsNewItem PASSED
NeverEqualPolicyStaleChildTest > snapshotStateList_childShowsNewItem PASSED
NeverEqualPolicyStaleChildTest > skillPattern_nonSkippableChild_showsNewItem PASSED
4 tests completed, 1 failed
```

| Test | Manager | Child | Result |
| --- | --- | --- | --- |
| `skillPattern_childShowsNewItem` | skill's pattern: same `MutableList`, `neverEqualPolicy()` | `QueueList(items: List<String>)` | parent shows "2 items", child still shows only "a" |
| `newListEachUpdate_childShowsNewItem` | `mutableStateOf(listOf())`, `items.value = items.value + item` | same | passes |
| `snapshotStateList_childShowsNewItem` | `mutableStateListOf()` | same | passes |
| `skillPattern_nonSkippableChild_showsNewItem` | skill's pattern | same child marked `@NonSkippableComposable` | passes, so skipping is what hides the update |

## Expected

Following the skill's pattern, every composable that shows the list updates after `enqueue()`.

## Actual

Only scopes that read `items.value` themselves recompose. `QueueList` gets the same `MutableList` instance as last time; with strong skipping, an unstable parameter is compared with `===` (the compiler's `Composer.changedInstance` check), so `QueueList` is skipped and keeps rendering the old contents. The pattern works only when every consumer reads the state object itself, which the skill doesn't say, and passing a list down to a child composable is the common case.

## Environment

- Compose BOM 2026.11.01 (`androidx.compose.runtime` / `ui` / `foundation` 1.13.0), strong skipping on (the default)
- Kotlin 2.4.21 (Compose compiler plugin), AGP 9.5.1, Gradle 9.9.2
- Robolectric 4.18.1, compileSdk 37.2, JDK 25
- macOS 27.2
