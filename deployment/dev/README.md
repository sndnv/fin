# deployment / dev

The provided `docker-compose.yml` files define all `fin` services and their configuration, for testing and development
purposes.

The following deployments are available:

* `docker-compose.yml` - default deployment of all services

## Getting Started

1) Generate artifacts with `./scripts/generate_artifacts.py`
2) Start services with `docker-compose up` (or `docker compose -f <compose file name> up`)

## Deployment Components

### [`config`](./config)

Contains configuration files used by `fin` services.

### [`secrets`](./secrets)

Contains secrets used by `fin` services; **files in this directory must be generated locally and should not be part
of any commits**.

### [`scripts`](./scripts)

Contains scripts that run tests and help with setting up the test environment.

#### `generate_artifacts.py`

> Generate docker images or executables for all runnable components.

```
./generate_artifacts.py           # generates artifacts for all projects/submodules
./generate_artifacts.py -p server # generates artifact for "server" submodule
```

By default, Docker images for `server` will be generated; they are necessary for running the
services in the provided `docker-compose.yml` files.
