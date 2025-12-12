# Build Policy

## CRITICAL: GitHub Actions Only

**ALL BUILDS MUST BE DONE VIA GITHUB ACTIONS**

- NEVER attempt local builds using `./gradlew`
- NEVER run build commands locally
- The project uses GitHub Actions exclusively for all build processes
- After making changes, commit and push to trigger CI/CD builds
- Monitor build results via `gh run list` and `gh run view`

## Workflow

1. Make code changes
2. Commit changes
3. Push to trigger GitHub Actions
4. Monitor builds with `gh run list` and `gh run view <run-id>`
5. Check logs with `gh run view <run-id> --log-failed` if needed
