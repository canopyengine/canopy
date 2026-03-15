<p align="center">
  <img src="docs/assets/canopy-logo-no-bg.png" width="420" alt="Canopy Engine logo">
</p>

# Contributors guidelines

This document summarizes the most important points for people interested in
contributing to Canopy, especially via bug reports, feature proposals and pull requests.

Before contributing to the engine, it's recommend you read the [engine details](https://github.com/canopyengine/canopy-docs/tree/main/markdown/engine-details) documents.


## Table of contents

- [Reporting bugs](#reporting-bugs)
- [Proposing features or improvements](#proposing-features-or-improvements)
- [Contributing pull requests](#contributing-pull-requests)

## Reporting bugs

Report bugs [here](https://github.com/canopyengine/canopy/issues/new?assignees=&labels=&template=bug_report.md).
Please follow the instructions in the template when you do.

> [!IMPORTANT]
> Make sure that the bug you are experiencing is reproducible in the latest Canopy releases.

If you run into a bug which wasn't present in an earlier Canopy version (what we call a _regression_), please mention it 
and clarify which versions you tested(both the one(s) working and the one(s) exhibiting the bug).

## Proposing features or improvements

You can propose new features for Canopy [here](https://github.com/canopyengine/canopy/issues/new?assignees=&labels=&template=feature_proposal.md).

### Rules for feature proposals
1. Do your research. Before you post, discuss your idea with other people, to find if others are experiencing the 
same problem and brainstorm possible solutions together. Also make sure other similar proposals weren't already made.

2. Use your own words. It's recommended to write the proposal with your own words, instead of an AI/LLM, as they can
generate verbose proposals, or mud the original intent with hallucinations.

3. Contextualize your problem. Instead of simply stating your need, provide an overarching context. From the [Godot Proposal](https://github.com/godotengine/godot-proposals): 
"To give an example in cooking: Say you wanted to bake a cake and found a recipe. Don't write your problem as "I need flour", 
write "I want to bake a cake". To add flour is the proposed solution."

4. Provide a well-founded motivation. You should also describe other ways you tried to solve your problem, and link
other related proposals if necessary. The lack of a valid motivation can result in your proposal being closed.

5. Be specific with your solution. Simply describing it isn't enough; the proposal must contain the necessary design
decisions so that it can be discussed. Include images, mock-ups, diagrams, code, if applicable. If your solutions lacks
in detail, is ambiguous, or otherwise unactionable, the proposal may be closed.

Use one feature per proposal. Do not cram multiple feature requests into a single proposal as this makes it harder to 
discuss features individually.

## Contributing pull requests

If you want to add new engine features, please make sure that:

- It solves a common use case that several users face in their real-life projects.
- You discussed it with other developers on how to implement it best. See also
  [Proposing features or improvements](#proposing-features-or-improvements).
- Even if it doesn't get merged, your PR is useful for future work by another
  developer.

> [!NOTE]
> The same rules apply for contributing bug fixes - it's always best to discuss the implementation in the bug report
> if you're not sure about the most effective fix.

### Be mindful of your commits

Try to make simple PRs that handle one specific topic. Just like for reporting
issues, it's better to open 3 different PRs that each address a different issue
than one big PR with three commits. This makes it easier to review, approve, and
merge the changes independently.

Try to make commits that bring the engine from one stable state to another
stable state, i.e. if your first commit has a bug that you fixed in the second
commit, try to merge them together before making your pull request. This
includes fixing build issues or typos, adding documentation, etc.

See our [PR workflow](https://github.com/canopyengine/canopy-docs/blob/main/markdown/contributing/contributing.md)
documentation for tips on using Git, amending commits and rebasing branches.

See our [Git naming conventions](https://github.com/canopyengine/canopy-docs/blob/main/markdown/contributing/git-naming-conventions.md) file for conventions on branch, issues and PR naming for coherence across all the 
contributors.

This [Git style guide](https://github.com/agis-/git-style-guide) also has some
good practices to have in mind.

### Format your commit messages with readability in mind

The way you format your commit messages is quite important to ensure that the
commit history and changelog will be easy to read and understand. A Git commit
message is formatted as a short title (first line) and an extended description
(everything after the first line and an empty separation line).

The short title is the most important part, as it is what will appear in the
changelog or in the GitHub interface unless you click the "expand" button.
Try to keep that first line under 72 characters, but you can go slightly above
if necessary to keep the sentence clear.

It should be written in English, starting with a capital letter, and usually
with a verb in imperative form. A typical bugfix would start with "Fix", while
the addition of a new feature would start with "Add". A prefix can be added to
specify the engine area affected by the commit. Some examples:

- Fix GLES3 instanced rendering color and custom data defaults
- Core: Fix `event.emit()` not calling listeners.

If your commit fixes a reported issue, please include it in the _description_
of the PR (not in the title, or the commit message) using one of the
[GitHub closing keywords](https://docs.github.com/en/issues/tracking-your-work-with-issues/linking-a-pull-request-to-an-issue)
such as "Fixes #1234". This will cause the issue to be closed automatically if
the PR is merged. Adding it to the commit message is easier, but adds a lot of
unnecessary updates in the issue distracting from the thread.

Here's an example of a well-formatted commit message (note how the extended
description is also manually wrapped at 80 chars for readability):

```text
Prevent French fries carbonization by fixing heat regulation

When using the French fries frying module, Canopy would not regulate the heat
and thus bring the oil bath to supercritical liquid conditions, thus causing
unwanted side effects in the physics engine.

By fixing the regulation system via an added binding to the internal feature,
this commit now ensures that Canopy will not go past the ebullition temperature
of cooking oil under normal atmospheric conditions.
```

**Note:** When using the GitHub online editor or its drag-and-drop
feature, *please* edit the commit title to something meaningful. Commits named
"Update my_file.kt" won't be accepted.

### Document your changes

If your pull request adds methods, properties or signals that are exposed to
scripting APIs, you **must** update the class reference to document those.
This is to ensure the documentation coverage doesn't decrease as contributions
are merged.

> [!NOTE]
> Documents must be added/updated to our [Canopy Engine Documents](https://github.com/canopyengine/canopy-docs) repository.

If your pull request modifies parts of the code in a non-obvious way, make sure
to add comments in the code as well. This helps other people understand the
change without having to dive into the Git history.

Check the [Canopy code style guidelines](https://github.com/canopyengine/canopy-docs/blob/main/markdown/contributing/code-style-guidelines.md)
for information regarding style guidelines on the repository code.

### Write unit tests

When fixing a bug or contributing a new feature, we recommend including unit
tests in the same commit as the rest of the pull request. Unit tests are pieces
of code that compare the output to a predetermined *expected result* to detect
regressions. Tests are compiled and run on GitHub Actions for every commit and
pull request.

Pull requests that include tests are more likely to be merged, since we can have
greater confidence in them not being the target of regressions in the future.

For bugs, the unit tests should cover the functionality that was previously
broken. If done well, this ensures regressions won't appear in the future
again. For new features, the unit tests should cover the newly added
functionality, testing both the "success" and "expected failure" cases if
applicable.

Feel free to contribute standalone pull requests to add new tests or improve
existing tests as well.

See [Unit testing](https://github.com/canopyengine/canopy-docs/blob/main/markdown/contributing/unit-testing.md)
for information on writing tests for the codebase.

Thanks for your interest in contributing!

— The Canopy development team

---

<p align="center">
  Canopy Engine • 2026
</p>
