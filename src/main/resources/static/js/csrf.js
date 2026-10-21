// Adds the CSRF token to every HTMX request so partial updates are protected like full-page posts.
document.addEventListener('htmx:configRequest', function (event) {
    var token = document.querySelector('meta[name="_csrf"]');
    var header = document.querySelector('meta[name="_csrf_header"]');
    if (token && header) {
        event.detail.headers[header.content] = token.content;
    }
});
