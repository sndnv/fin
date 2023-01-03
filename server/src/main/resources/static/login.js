$(document).ready(function () {
    let authorization = handle_authorization_response();
    let title = $('#processing-title');
    let content = $('#processing-content');

    if (authorization.error) {
        title.text(errorAsTitle(authorization.error));
        content.text(authorization.error_description);
    } else {
        make_token_request(authorization.code, function (result) {
            if (result.error) {
                title.text(errorAsTitle(result.error));
                content.text(result.error_description);
                showErrorToast(`Failed to login: [${result.error} - ${result.error_description}]`);
            } else {
                title.text('Processing Complete');
                content.text('Redirecting ...');
                window.location = '/manage';
            }
        });
    }
});

function errorAsTitle(error) {
    return error.split('_').join(' ');
}
