# Contributing to PostSync Pro

Thanks for your interest in contributing! Here's how to get started.

## Setup

1. Fork and clone the repo
2. Open in IntelliJ IDEA (recommended) or any Kotlin IDE
3. Run `./gradlew run` to verify it works
4. Run `./gradlew test` to verify tests pass (202 tests)

## Project Structure

- `domain/` — Models, repository interfaces, use cases. **No framework dependencies.**
- `data/` — Repository implementations, parsers, network clients.
- `presentation/` — Compose UI. Each screen has its own package with `Screen.kt` + `ViewModel.kt`.
- `di/` — Koin modules.
- `presentation/theme/` — Colors, typography, dimensions. **Always use `LocalAppPalette.current`**, never hardcode colors.
- `presentation/i18n/` — Translations. Add strings to all 4 language files.

## Rules

1. **No hardcoded colors.** Use `BrandColors`, `StatusColors`, `HttpMethodColors`, or `LocalAppPalette.current`.
2. **No hardcoded strings.** Add to `Strings.kt` (all 4 languages).
3. **RTL support.** Use `Icons.AutoMirrored.Filled.*` for directional icons.
4. **Write tests.** Add ViewModel tests for new features using MockK.
5. **Keep it compiling.** Run `./gradlew compileKotlin` before pushing.

## Adding a New Feature Screen

1. Create package: `presentation/screen/yourfeature/`
2. Create `YourFeatureViewModel.kt` with state data class
3. Create `YourFeatureScreen.kt` composable
4. Add to `Screen` enum in `navigation/NavigationState.kt`
5. Add to `App.kt` when-block
6. Add strings to all 4 languages in `i18n/Strings.kt`
7. Add sidebar entry in `components/Sidebar.kt`
8. Write tests in `test/.../viewmodel/YourFeatureViewModelTest.kt`

## Pull Requests

- Keep PRs focused — one feature or fix per PR
- Include before/after screenshots for UI changes
- Make sure `./gradlew test` passes
- Describe what and why in the PR description
