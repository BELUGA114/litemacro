# Repository Guidelines

## Project Structure & Module Organization

Litemacro is a Fabric client mod. Each folder under `versions/` (`mc1.21.1`, `mc1.21.8`, `mc1.21.11`, `mc26.2`, `mc26.3`) is an independent Gradle project.

- Java code: `src/main/java/com/alfre/macrobuilder/`; mixins: its `mixin/` subfolder.
- Resources: `src/main/resources/`, including Fabric metadata, mixin configuration, translations, and bundled macros.
- `test-macros/`: importable JSON scenarios; `wiki/`: user documentation.
- `database/`: marketplace SQL; `config/`: example marketplace configuration.

## Build, Test, and Development Commands

Use JDK 25 for `mc26.2`/`mc26.3` and JDK 21 for `mc1.21.*`. Set `JAVA_HOME` accordingly. Run commands inside the selected version folder, for example `cd versions/mc26.3`:

- `.\build.ps1 build`: build the mod into `build/libs/` and run checks.
- `.\build.ps1 numberComponentTest`: run numeric-component and cloud-vault regression checks (`mc26.3` only).
- `.\build.ps1 runClient`: launch the Fabric development client.

The PowerShell scripts in `mc26.2` and `mc26.3` download Gradle 9.4.1 when needed. Wrapper JARs are absent from this checkout; for older versions, use installed Gradle 9.4.1, such as `gradle build` or `gradle runClient`.

## Coding Style & Naming Conventions

Use three-space Java indentation and four-space Gradle indentation, with opening braces on the same line. Name classes `PascalCase`, methods and fields `camelCase`, and constants `UPPER_SNAKE_CASE`. Match surrounding imports and comments; use Chinese for new explanatory comments where appropriate. No formatter or linter is configured.

Keep changes within the intended version. Inspect `MacroModel`, `MacroRunner`, and builder screens together when changing component contracts; preserve compatibility with saved macro JSON.

## Testing Guidelines

`mc26.3` uses the standalone Java assertion harness `TextNumberObservationTest` under `src/numberComponentTest/java/`. Extend it for relevant regressions; use `*Test.java` names. Gradle `check` includes this harness. No coverage threshold is configured.

For gameplay or UI changes, import relevant `test-macros/` scenarios in the matching client and verify success, failure, stopping, and held-input release. Build each version you change.

## Commit & Pull Request Guidelines

History uses imperative subjects (`Fix creative inventory action recording`) and scoped prefixes (`feat(mc26.3): ...`, `chore(git): ...`). Keep commits focused.

Pull requests are prohibited without user approval
