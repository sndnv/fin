@(config: fin.server.api.routes.Manage.Config.Ui)

const FIN_SERVER_UI_CONFIG = {
    authorization_endpoint: '@{config.authorizationEndpoint}',
    token_endpoint: '@{config.tokenEndpoint}',
    authentication: {
        state_size: @{config.authentication.stateSize},
        code_verifier_size: @{config.authentication.codeVerifierSize},
        client_id: '@{config.authentication.clientId}',
        redirect_uri: '@{config.authentication.redirectUri}',
        scope: '@{config.authentication.scope}',
    },
    cookies: {
        authentication_token: '@{config.cookies.authenticationToken}',
        code_verifier: '@{config.cookies.codeVerifier}',
        state: '@{config.cookies.state}',
        secure: @{config.cookies.secure},
        expiration_tolerance: @{config.cookies.expirationTolerance},
    },
}