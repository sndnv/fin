# deployment / production

The provided `docker-compose.yml` defines all `fin` services and their configuration.

## Getting Started

1) Generate/pull artifacts
2) Provide environment files (example files with all required parameters are available in [`secrets/examples`](./secrets/examples))
3) Provide TLS certificates and signature keys
4) Enable bootstrap/DB init for `identity` and `server` (first-run only; should be disabled normally)
5) Make sure that:
   * the correct docker images are used for all services
   * the correct values for `AKKA_HTTP_CORS_ALLOWED_ORIGINS` are set for both `identity` and `server`
6) Start services with `docker-compose up`

## Deployment Components

### [`bootstrap`](./bootstrap)

Contains bootstrap configuration files for `identity`.

### [`local`](./local)

Default location for the data persisted by the database and `server`; **files in this directory must be generated
locally and should not be part of any commits**.

### [`secrets`](./secrets)

Contains secrets used by `fin` services; **files in this directory must be generated
locally and should not be part of any commits**.

### [`secrets/examples`](./secrets/examples)

Example environment files for configuring the services.

### [`scripts`](./scripts)

Contains basic scripts that help with the initial management of `server`.

#### `generate_cert.py`

> Generate new CSRs, x509 certificates and private keys.

```
./generate_cert.py -c <country> -l <location> -cc <CA certificate path> -ck <CA private key path>
```

Used to generate x509 certificates and private keys signed by the provided certificate authority.
