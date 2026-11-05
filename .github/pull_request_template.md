## What and why

<!-- What changed, and the reason. Link the issue if there is one. -->

## How it was tested

<!-- Commands run and what you checked by hand. -->

## Checklist

- [ ] Existing e2e tests updated if labels, buttons or flows changed
- [ ] New user-facing behaviour has an e2e test, or this PR says why unit tests are enough
- [ ] `cd e2e; npm run typecheck; npm run test:smoke` pass locally
- [ ] e2e code: setup through the API, behaviour through the UI; role/label locators; no `waitForTimeout` or `test.only`
