# MiniMessage chat completion

Enable **MiniMessage Completion** in the **Misc** category, or run `.toggle minimessage`.
The module is disabled by default, uses the ordinary saved module configuration, and has Chinese/English labels.

Typing an unescaped `<` opens Minecraft's native suggestions list. Type a prefix, then use Tab to accept/cycle,
Shift+Tab to cycle backwards, or the arrow keys and mouse to select an entry. Examples:

- `<re` completes to `<red>` or `<reset>`.
- `<color:da` suggests named dark colors.
- `<gradient:red:bl` suggests the next gradient color.
- `<click:ru` completes to `<click:run_command:` so the command value can be entered.
- `<red><bold>text </` suggests closing tags for the open scopes.

Suggestions cover standard colors, decorations and aliases, gradients, shadows, click/hover actions,
fonts, keybinds and other standard tag names. Custom hexadecimal colors can be completed once all six
digits have been entered (eight digits are supported for shadow alpha). Free-form values and quoted
arguments remain editable as typed. Escaped brackets and quoted nested text do not open unrelated suggestions.
Completing in the middle of an existing token replaces that token and preserves the following message text.
Ordinary chat and commands retain their native suggestions outside a tag; Pupper's dot commands retain their own completion.

This is a local input aid. The original tag text is sent normally; rendering depends on the server/plugin's
MiniMessage support and allowed tags. Syntax reference: [Adventure MiniMessage format](https://docs.papermc.io/adventure/minimessage/format/).

`./gradlew verifyChatCompletion` checks prefixes, parameters, closing scopes, escaping, replacement ranges and
the actual Minecraft 26.2 completion fields/methods and Tab re-entry contract. It also runs in `build`/`check`.
These checks are headless; native popup navigation should additionally be verified in the running client.
