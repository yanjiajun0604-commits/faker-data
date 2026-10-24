# faker-data interface system

## Direction

An Apple-inspired local data workbench: calm, precise, familiar controls and legible information. The person connects a test database, chooses tables, configures rules and monitors a task. Preserve the existing Vue 2 / Element UI behaviors, wording, localization and API contracts.

## Hierarchy and materials

Use a quiet silver sidebar and a translucent toolbar around opaque white content. Reserve blue for the current step and primary actions; use semantic green, amber and red only for status. Avoid decorative window controls, arbitrary gradients and motion that delays repeated work.

The field table and its rules are the signature surface. Relationships and generation settings should remain easy to scan. Keep the existing step flow, expandable tables, rule tabs, nested condition editors and scheme dialogs.

## Tokens

- Canvas: #f5f5f7; sidebar: rgba(237,239,243,.88); panel: #ffffff.
- Main text: #1d1d1f; secondary: #51525a; metadata: #67676d.
- Action: #0066cc; hover: #0057ad; selection: #e8f1fc.
- Success: #24834a; warning: #966212; danger: #c43636.
- Spacing base: 4px. Related controls: 8px; groups: 16px; panels: 24–32px.
- Radius: controls 9px; compact tags 6px; panels 18px; dialogs 20px.
- System font stack: -apple-system, BlinkMacSystemFont, Segoe UI, PingFang SC, Microsoft YaHei, sans-serif.
- Body/control: 13–14px; headings: 20–32px; metadata: 11–12px. Numeric values use tabular figures.
- Layering: subtle separators and restrained panel/dialog shadows. No translucent data tables.

## Component patterns

- Sidebar: 224px on desktop; current step has a blue icon tile and a pale selection background. Steps remain indicators, not new navigation actions.
- Toolbar: 68px; language control remains visible and operable on narrow screens. It must not cover open popovers or dialogs.
- Primary action: blue filled control; secondary action: neutral surface. Disabled state stays visibly distinct.
- Inputs: lightly inset fill, clear label and blue focus ring. Keep password reveal, validation and existing input behavior.
- Tables: quiet gray headers, readable rows, restrained hover, horizontal scrolling only within the table when needed.
- Rule dialogs: preserve Element UI tabs, radio controls, dropdowns, focus handling, footer actions and nested conditions. Ensure short and long labels wrap legibly.
- Feedback: preserve all existing success/error content and loading state; do not add a second notification layer.

## Verification

Check Chinese and English at desktop and narrow widths. Inspect dropdowns, rule categories, nested condition editors, dates, tree settings, saved schemes and generation feedback. Respect reduced motion and increased-contrast preferences. No external fonts, image dependencies or new component library.

## Selected homepage direction — A

On 2026-10-24 the user selected concept A from four image proposals. Match its full-width toolbar, restrained silver workflow rail, blue actions, white connection form, and pale-blue side-by-side users/orders relationship illustration. Homepage copy names the current task directly. Saved schemes move to the toolbar with their original handler; all existing form bindings and submission semantics remain unchanged. New copy must have an English translation. Do not copy generated claims about automatic schema relationship inference: references are configured by the user.

## Current implementation boundary

Only the homepage (`step === 0`, `.home-view`) uses this theme. Existing global styles remain, and later workflow steps retain their original appearance. The saved-scheme button uses mutually exclusive homepage-toolbar and later-step-sidebar instances of the same existing handler. Do not extend styling to other steps until requested.
