# Release Process Documentation

## 1. Overview

This document outlines the release process for the Meshtastic Android application. We use several types of releases:

*   **Nightly:** Automated builds generated daily from the latest development branch. Useful for testing cutting-edge features but may be unstable.
*   **Alpha:** Early pre-releases for testing new major features or changes. Expected to have bugs.
*   **Beta:** Later pre-releases, more stable than alpha, typically feature-complete. Used for wider testing.
*   **RC (Release Candidate):** Builds that are potentially final. If no critical bugs are found, an RC may become a production release.
*   **Production:** Official stable releases available to all users.

## 2. Branching Strategy

We aim to follow a Gitflow-like branching strategy:

*   **`main`:** This branch always reflects the latest production-ready state. All production releases are tagged from this branch. Direct commits to `main` are discouraged; changes should come via merging `release/*` or `hotfix/*` branches.
*   **`develop`:** (Recommended if not already in use) This branch serves as an integration branch for new features. Nightly builds should ideally be generated from this branch. Feature branches are merged into `develop`.
*   **`feature/*`:** For new features or significant changes. Branched from `develop` (or `main` if `develop` is not used). Example: `feature/new-ui`. Once complete, merged back into `develop`.
*   **`release/*`:** Used to prepare for a new production release. Branched from `develop` (or `main`). Allows for stabilization, bug fixing, and documentation updates specific to the release. Example: `release/v2.7.0`. Once ready, `release/*` is merged into `main` and `develop`.
*   **`hotfix/*`:** For critical bug fixes that need to be applied to a production release urgently. Branched from `main` (from the production tag). Once complete, merged back into both `main` and `develop` (and any active `release/*` branch). Example: `hotfix/v2.6.21`.

## 3. Versioning

Our versioning scheme helps track releases systematically.

*   **`versionName` Structure:**
    *   The full `versionName` combines a base version with a suffix based on the release type.
    *   **`BASE_VERSION_NAME`:** Standard semantic versioning (e.g., `2.7.0`). This is set in `buildSrc/src/main/kotlin/Configs.kt`.
    *   **Suffixes:**
        *   Nightly: `-nightly` (e.g., `2.7.0-nightly`). The GitHub release tag includes the date: `nightly-YYYYMMDD`.
        *   Alpha: `-alpha.N` (e.g., `2.7.0-alpha.1`). `N` is a counter, currently fixed at `.1` by the workflow.
        *   Beta: `-beta.N` (e.g., `2.7.0-beta.1`). `N` is a counter, currently fixed at `.1`.
        *   RC: `-rc.N` (e.g., `2.7.0-rc.1`). `N` is a counter, currently fixed at `.1`.
        *   Production: No suffix (e.g., `2.7.0`).
*   **`versionCode`:**
    *   An integer that must increment with each release submitted to Google Play.
    *   It is automatically incremented by 1 by the "Make Release" workflow (`.github/workflows/release.yml`) during any Alpha, Beta, RC, or Production release.
*   **Location of Version Configuration:**
    *   `BASE_VERSION_NAME` and `VERSION_CODE`: Defined in `buildSrc/src/main/kotlin/Configs.kt`. The `release.yml` workflow modifies these values.
    *   `versionNameSuffix` (for `-nightly`, `-alpha`, `-beta`, `-rc`, `-debug`): Defined in `app/build.gradle.kts` within the corresponding build type configurations. The `release.yml` workflow effectively overrides this for release builds by constructing the full version name from the base and a suffix like `-alpha.1`.

## 4. CI/CD Workflows

Our CI/CD pipelines automate building, testing, and releasing the app.

*   **Nightly Builds (`.github/workflows/android.yml`):**
    *   **Trigger:** Scheduled daily at midnight UTC. It typically targets the `main` branch (or `develop` if adopted as the primary development integration branch).
    *   **Action:**
        *   Builds the `nightly` variant of the app (e.g., `assembleFdroidNightly`, `bundleGoogleNightly`). The `versionName` will be like `X.Y.Z-nightly`.
        *   Creates a GitHub pre-release tagged `nightly-YYYYMMDD` (e.g., `nightly-20231028`).
        *   Uploads build artifacts (APK/AAB) to this pre-release.

*   **Manual Releases (`.github/workflows/release.yml`):**
    *   **Trigger:** Manual `workflow_dispatch` from the GitHub Actions tab.
    *   **Inputs:**
        *   `release_type`: A choice: `alpha`, `beta`, `rc`, or `production`.
        *   `version_name`: The base semantic version for the release (e.g., `2.7.0`). This will become the `BASE_VERSION_NAME`.
    *   **Action:**
        1.  Determines the appropriate `versionNameSuffix` (e.g., `-alpha.1` or empty for production).
        2.  Updates `BASE_VERSION_NAME` in `buildSrc/src/main/kotlin/Configs.kt` to the provided `version_name`.
        3.  Reads the current `VERSION_CODE` from `Configs.kt` and increments it by 1, then updates `Configs.kt`.
        4.  Builds the Android app variant corresponding to the selected `release_type` (e.g., `alpha` tasks for `release_type: alpha`, `release` tasks for `release_type: production`).
        5.  Creates/updates `version_info.txt` with the new `versionCode` and full `versionName`.
        6.  Creates a GitHub release:
            *   Tagged `v<full_version_name>` (e.g., `v2.7.0-alpha.1` or `v2.7.0` for production).
            *   Marked as a pre-release if `release_type` is `alpha`, `beta`, or `rc`.
            *   Marked as a full release (not pre-release) if `release_type` is `production`.
            *   Uploads build artifacts (APKs, AAB, `version_info.txt`) to the release.

## 5. How to Create a Release

Follow these steps to create a new release:

*   **For Alpha/Beta/RC Pre-releases:**
    1.  **Branch Prep:** Ensure your `release/*` branch (e.g., `release/v2.7.0`) is created from `develop` (or `main`), is up-to-date with all necessary changes, and all changes are committed and pushed.
    2.  **Navigate to Workflow:** Go to the repository's "Actions" tab, select the "Make Release" workflow from the list.
    3.  **Run Workflow:** Click "Run workflow".
        *   Set **`release_type`**: Choose `alpha`, `beta`, or `rc`.
        *   Set **`version_name`**: Enter the base semantic version (e.g., `2.7.0`).
        *   Click "Run workflow".
    4.  **Outcome:** The workflow will:
        *   Update `Configs.kt` on the branch the workflow is run against (typically your `release/*` branch if you selected it, otherwise it defaults to `main` - **be sure to select your release branch if changes to `Configs.kt` are not yet on `main`**).
        *   Create a tag like `v2.7.0-alpha.1`.
        *   Create a corresponding GitHub pre-release with build artifacts.
        *   Commit the changes to `Configs.kt` back to the branch the workflow ran on. **Ensure these changes are pulled if others need to work with them, or merge them appropriately.**

*   **For Production Releases:**
    1.  **Merge to Main:** Ensure the thoroughly tested `release/*` branch (e.g., `release/v2.7.0`) has been merged into `main`. All changes in `Configs.kt` (version name and code) should be part of this merge.
    2.  **Create Tag (Recommended):**
        *   Locally, checkout the `main` branch and pull the latest changes.
        *   Create an annotated git tag for the version:
            ```bash
            git tag -a v2.7.0 -m "Version 2.7.0"
            ```
            (Replace `v2.7.0` with the correct version).
        *   Push the tag to the remote repository:
            ```bash
            git push origin v2.7.0
            ```
    3.  **Navigate to Workflow:** Go to the repository's "Actions" tab, select the "Make Release" workflow.
    4.  **Run Workflow:** Click "Run workflow".
        *   Select the `main` branch.
        *   Set **`release_type`**: `production`.
        *   Set **`version_name`**: Enter the base semantic version (e.g., `2.7.0`). This should match the tag you just pushed.
        *   Click "Run workflow".
    5.  **Outcome:**
        *   The workflow will verify/update `Configs.kt` if needed (though it should match if merged from `release/*`).
        *   It will use the provided `version_name` for the release. The tag created by the workflow will be `v<version_name>` (e.g., `v2.7.0`). If you manually pushed an annotated tag with the same name, the workflow's release will attach to your existing tag.
        *   A GitHub release (marked as latest, not pre-release) will be created/updated with build artifacts.

## 6. Notes

*   **Source of Truth for Version:** `buildSrc/src/main/kotlin/Configs.kt` is the source of truth for `BASE_VERSION_NAME` and `VERSION_CODE` at build time. The `release.yml` workflow modifies this file directly.
*   **Committing Workflow Changes:** When the `release.yml` workflow updates `Configs.kt`, these changes are committed directly to the branch the workflow was run on. If you run the workflow on a branch other than `main` (e.g., a `release/*` branch), ensure these changes are eventually merged into `main`.
*   **Local Development:** If the `release.yml` workflow updates `Configs.kt` on a shared branch, remember to `git pull` to get these changes before doing further local development on that branch.
*   **Pre-release Suffix Counter:** Currently, suffixes like `-alpha.N` use a fixed `.1`. Future enhancements could involve dynamically incrementing `N`.

This documentation should help in managing releases effectively.
