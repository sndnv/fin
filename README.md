# fin

`fin` is a basic personal finance application.

## Installation

Official images and binaries are not yet available, but they can be created locally using the existing [dev tools](deployment/dev).

## Development

The majority of the code is [Scala](https://scala-lang.org/) so, at the very least, Java (JDK17) and SBT need to be
available on your dev machine.

There are also some Python and Bash [scripts](deployment/dev/scripts) to help with deployment and testing.

###### Downloads / Installation:
* [Adoptium JDK](https://adoptium.net/)
* [Scala](https://scala-lang.org/download/)
* [sbt](https://www.scala-sbt.org/download.html)
* [Python](https://www.python.org/downloads/)
* [Docker](https://www.docker.com/get-started)

### Getting Started

1) Clone or fork the repo
2) Run `sbt qa`

### Submodules

> To execute all tests and QA steps for the Scala submodules, simply run `sbt qa` from the root of the repo.

#### [`server`](server)

API and storage service.

* **Scala** code
* **Testing** - `sbt "project server" qa`
* **Packaging** - `sbt "project server" docker:publishLocal`

#### [`deployment`](deployment)

Deployment, artifact and certificate generation scripts and configuration.

* **Python** and **Bash** code; config files
* **Packaging** - `see ./deployment/dev/docker-compose.yml`

### Current State

**NOT** production ready but usable

* `server` - *fin server and web UI* - **operational**

## Contributing

Contributions are always welcome!

Refer to the [CONTRIBUTING.md](CONTRIBUTING.md) file for more details.

## Versioning
We use [SemVer](http://semver.org/) for versioning.

## License
This project is licensed under the Apache License, Version 2.0 - see the [LICENSE](LICENSE) file for details

> Copyright 2022 https://github.com/sndnv
>
> Licensed under the Apache License, Version 2.0 (the "License");
> you may not use this file except in compliance with the License.
> You may obtain a copy of the License at
>
> http://www.apache.org/licenses/LICENSE-2.0
>
> Unless required by applicable law or agreed to in writing, software
> distributed under the License is distributed on an "AS IS" BASIS,
> WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
> See the License for the specific language governing permissions and
> limitations under the License.
