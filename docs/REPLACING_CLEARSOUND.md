# Replacing the ClearSound repository

If you want to reuse **Ihsan113/Dolby** rather than create a new repository, replace the tracked project contents with those of this ZIP. Do **not** upload over the old files and leave both sets of source present.

From an existing local Git clone of your repository (Termux or computer), run a deliberate replacement after backing up important changes:

```bash
cd /path/to/your-cloned-repository
# Review git status before replacing anything

git status
# Remove only old application and build files that conflict with the new project.
git rm -r app || true
git rm -r .github/workflows || true
# Copy ALL extracted ZIP files (including hidden .github) to repo root.
# Then review changes before committing.
git status
git add -A
git commit -m "replace: YouTube on-device subtitle app"
git push
```

Avoid copying ZIP contents into an extra nested directory. `settings.gradle.kts` and `.github/workflows/android.yml` must be directly under the repository root.
