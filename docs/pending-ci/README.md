# Pending CI workflow

This `ci.yml` (from `repo-template`) should live at `.github/workflows/ci.yml`
but the bootstrap push from `gh` could not include it because the OAuth token
lacked the `workflow` scope.

To install it:

```bash
gh auth refresh -s workflow
git mv docs/pending-ci/ci.yml .github/workflows/ci.yml
git commit -m "ci: enable workflow"
git push
```
