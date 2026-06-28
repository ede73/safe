# Workspace Rules

- **Stack Management**: When working with `gh stack`, never use normal `git` commands (like `git switch` or `git checkout`) to create, navigate, or manage branches. Always use the equivalent `gh stack` commands (e.g., `gh stack init`, `gh stack add`, `gh stack checkout`, `gh stack sync`, etc.) to keep the stack metadata synchronized. This is because `gh stack` stores its own resolutions/states in a local file, and using raw git commands will disrupt that state, requiring tedious manual amendments to repair.
- **Rebasing**: Never run `git rebase` on stack branches. Always run `gh stack rebase` instead to ensure the stack is properly aligned.

