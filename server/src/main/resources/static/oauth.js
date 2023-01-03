function login(callback) {
    if (!get_token()) {
        make_authorization_request();
    } else if (callback) {
        callback();
    }
}

function logout() {
    delete_token();
    window.location = '/manage/login';
}

async function make_authorization_request() {
    const state = base64url(randomBytes(FIN_SERVER_UI_CONFIG.authentication.state_size));

    const code_verifier = base64url(randomBytes(FIN_SERVER_UI_CONFIG.authentication.code_verifier_size));
    const code_challenge = base64url(await createHash('SHA-256', code_verifier));
    const code_challenge_method = 'S256';

    const params = {
        response_type: 'code',
        client_id: FIN_SERVER_UI_CONFIG.authentication.client_id,
        redirect_uri: encodeURIComponent(FIN_SERVER_UI_CONFIG.authentication.redirect_uri),
        scope: encodeURIComponent(FIN_SERVER_UI_CONFIG.authentication.scope),
        state: state,
        code_challenge: code_challenge,
        code_challenge_method: code_challenge_method
    };

    const query = params_to_query(params);

    store_verifier(code_verifier);
    store_state(state);

    window.location = `${FIN_SERVER_UI_CONFIG.authorization_endpoint}?${query}`;
}

function make_token_request(code, callback) {
    const code_verifier = get_and_delete_verifier();

    const params = {
        grant_type: 'authorization_code',
        code: code,
        redirect_uri: FIN_SERVER_UI_CONFIG.authentication.redirect_uri,
        client_id: FIN_SERVER_UI_CONFIG.authentication.client_id,
        code_verifier: code_verifier
    };

    $.ajax({
        type: 'POST',
        url: FIN_SERVER_UI_CONFIG.token_endpoint,
        data: params,
        success: function (response) {
            let result = handle_token_response(response);
            if (result.token) {
                store_token(result.token, new Date(new Date().getTime() + ((result.expires_in - FIN_SERVER_UI_CONFIG.cookies.expiration_tolerance) * 1000)));
            }
            callback(result);
        },
        error: function (xhr, textStatus, errorThrown) {
            callback({error: 'request_failed', error_description: errorThrown});
        },
    });
}

function handle_authorization_response() {
    const expected_state = get_and_delete_state();
    const searchParams = new URLSearchParams(document.location.search);

    const error = searchParams.get('error');
    const error_description = searchParams.get('error_description');

    const provided_state = searchParams.get('state');
    const code = searchParams.get('code');
    const scope = searchParams.get('scope');

    if (error) {
        return {
            error: error,
            error_description: error_description
        };
    } else if (!code) {
        return {
            error: 'missing_code',
            error_description: 'Authorization code not found in response'
        };
    } else if (expected_state !== provided_state) {
        return {
            error: 'invalid_state',
            error_description: 'Invalid state found in response'
        };
    } else {
        return {
            code: code,
            scope: scope
        };
    }
}

function handle_token_response(response) {
    const error = response.error;
    const error_description = response.error_description;
    const token = response.access_token
    const expires_in = response.expires_in

    if (error) {
        return {
            error: error,
            error_description: error_description
        };
    } else if (!token) {
        return {
            error: 'missing_token',
            error_description: 'Access token not found in response'
        };
    } else {
        return {
            token: token,
            expires_in: expires_in
        };
    }
}

function store_token(token, expiration) {
    Cookies.set(
        /* key */ FIN_SERVER_UI_CONFIG.cookies.authentication_token,
        /* value */ token,
        {
            expires: expiration, // in days or a Date
            secure: FIN_SERVER_UI_CONFIG.cookies.secure,
            sameSite: 'strict',
        }
    );
}

function get_token() {
    return Cookies.get(FIN_SERVER_UI_CONFIG.cookies.authentication_token);
}

function delete_token() {
    Cookies.remove(FIN_SERVER_UI_CONFIG.cookies.authentication_token);
}

function store_verifier(code_verifier) {
    Cookies.set(
        /* key */ FIN_SERVER_UI_CONFIG.cookies.code_verifier,
        /* value */ code_verifier,
        {
            expires: 1/48,
            secure: FIN_SERVER_UI_CONFIG.cookies.secure,
            sameSite: 'strict',
        }
    )
}

function get_and_delete_verifier() {
    const verifier = Cookies.get(FIN_SERVER_UI_CONFIG.cookies.code_verifier);
    Cookies.remove(FIN_SERVER_UI_CONFIG.cookies.code_verifier);
    return verifier;
}

function store_state(state) {
    Cookies.set(
        /* key */ FIN_SERVER_UI_CONFIG.cookies.state,
        /* value */ state,
        {
            expires: 1/48,
            secure: FIN_SERVER_UI_CONFIG.cookies.secure,
            sameSite: 'strict',
        }
    )
}

function get_and_delete_state() {
    const verifier = Cookies.get(FIN_SERVER_UI_CONFIG.cookies.state);
    Cookies.remove(FIN_SERVER_UI_CONFIG.cookies.state);
    return verifier;
}

function params_to_query(params) {
    return Object.entries(params).map(entry => entry.join("=")).join("&");
}

function base64url(bytes) {
    return btoa(String.fromCharCode(...bytes)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '');
}

function randomBytes(size) {
    let bytes = new Uint8Array(size);
    crypto.getRandomValues(bytes);
    return Array.from(bytes);
}

async function createHash(algo, message) {
    const encoded = new TextEncoder().encode(message);
    const bytes = await crypto.subtle.digest(algo, encoded);
    return Array.from(new Uint8Array(bytes));
}
