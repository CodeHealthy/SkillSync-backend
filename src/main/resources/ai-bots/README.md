# AI Bot Prompt Files

SkillSync keeps bot instructions outside Java so each bot can evolve without changing service logic.

## Current bot

- `assessment-generator-professional.prompt`: main assessment generator persona, output contract, and shared generation rules.
- `rules/coding-challenge.prompt`: extra rules for coding challenge output.
- `rules/mcq-short-answer.prompt`: extra rules for MCQ and short-answer output.

## Adding another bot

1. Add a new prompt file under this folder.
2. Keep placeholders explicit, for example `{{role_title}}` or `{{context}}`.
3. Add a builder method in `AiBotPromptService` that loads the prompt and replaces placeholders.
4. Keep strict JSON output contracts in the prompt when backend parsing depends on the response.
