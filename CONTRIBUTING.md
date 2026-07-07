# CanvasMC Contributing Documentation

The primary documentation on how to contribute to Canvas' source code and
understanding said source has been moved to [here](https://docs.canvasmc.io/canvas/developers/contributing/canvas/)

This page is specifically for PRing guidelines

## Policies

This has been split off into the `/policies` directory of our repository.
Please review those alongside this for guidelines on how to contribute to
the CanvasMC organization

## Guidelines

For starters, we have the general expectation that your code is tested
thoroughly before PRing. We will test ourselves, however it's important to
test your work before submitting it as a PR to us.

Make sure your description includes the following:
- Authors/co-authors
- What your PR actually changes and why
- [If adding a new feature] Why is this feature useful? Who benefits from it?
- [If fixing something] Describe what the issue is you are trying to fix
- [If fixing something] Describe why your PR fixes this

Make sure all patches are neat and tidy, no unnecessary diff is there, and make
sure to try and follow the code-style of the surrounding source. That way the
code we are patching/adding is a lot more clean rather than a random sharp
change in code style amongst the rest of the consistent code style.
