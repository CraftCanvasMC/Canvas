# Reviewing PRs

Reviewing PRs for Canvas is honestly pretty simple, but can be time-consuming
depending on the PR in question. Overall, we expect people to review the diff
thoroughly, and also `checkout` the PR in question and actually apply patches
if applicable and view the **full** diff in its entirety, since sometimes we
may add code that is a rewrite of other code(or based on other code), and the
raw diff from the PR won't actually show the original.

We also expect people to test the changes in question. Assume we add a new
command or something, reviewers should always try and test locally the new
feature to make sure it all works properly and consistently. If you did test,
feel free to comment saying you tested. However, that does not mean if
someone has already commented saying they tested, that means you shouldnt.
The more testing that happens, the better, since that means we can ensure
top quality code is entering our master branch.

## For Maintainers

If you are wanting to merge it, ask the other maintainers first to see if
anyone is waiting on something before it can be merged(like say, waiting for
another person to review or to test later on). Also, always ask the author
of the PR if it's ready to be merged too, since they might want to make
further changes, or they noticed something they want to fix for example. Just
generally, communicate before merging.
