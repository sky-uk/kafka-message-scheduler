# Contributing

Contributions are welcomed!

When contributing to this repository, please first discuss the change you wish to make via GitHub
issue before making a change.  This saves everyone from wasted effort in the event that the proposed
changes need some adjustment before they are ready for submission.

## Pull Request Process

1. Fork the repo, push your commits to a branch of your fork, open the PR.
2. Make sure you update the README.md where relevant.
3. Project maintainers will squash-merge Pull Requests once they are happy.  There may be one or more
   cycles of feedback on the PR before they are satisfied.

## Build and run tests locally

The software is written in [Scala](https://scala-lang.org/) and is built with [SBT](http://www.scala-sbt.org/).

To compile and run tests: `sbt test`.

## Performing a release (for project maintainers)

1. Run `docker login --username=<username>` and when prompted add the password for your Docker Hub account. Note that you need to be a member of the `skyuk` Docker Hub organization.
2. Run `sbt ciRelease` to perform the release of the Docker artifact to Docker Hub.
3. The Docker Image will be immediately live and therefore will need to be deleted if there has been a problem.

## Contributor Code of Conduct

As contributors and maintainers of this project, and in the interest of fostering an open and
welcoming community, we pledge to respect all people who contribute through reporting issues,
posting feature requests, updating documentation, submitting pull requests or patches, and other
activities.

We are committed to making participation in this project a harassment-free experience for everyone,
regardless of level of experience, gender, gender identity and expression, sexual orientation,
disability, personal appearance, body size, race, ethnicity, age, religion, or nationality.

Examples of unacceptable behavior by participants include:

* The use of sexualized language or imagery
* Personal attacks
* Trolling or insulting/derogatory comments
* Public or private harassment
* Publishing other's private information, such as physical or electronic addresses, without explicit
  permission
* Other unethical or unprofessional conduct.

Project maintainers have the right and responsibility to remove, edit, or reject comments, commits,
code, wiki edits, issues, and other contributions that are not aligned to this Code of Conduct. By
adopting this Code of Conduct, project maintainers commit themselves to fairly and consistently
applying these principles to every aspect of managing this project. Project maintainers who do not
follow or enforce the Code of Conduct may be permanently removed from the project team.

This code of conduct applies both within project spaces and in public spaces when an individual is
representing the project or its community.

Instances of abusive, harassing, or otherwise unacceptable behavior may be reported by opening an
issue or contacting one or more of the project maintainers.

This Code of Conduct is adapted from the [Contributor Covenant](http://contributor-covenant.org),
version 1.2.0, available at
[http://contributor-covenant.org/version/1/2/0/](http://contributor-covenant.org/version/1/2/0/)
