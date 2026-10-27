// Shows a plain message in the target when an HTMX request fails, instead of leaving the old content in place.
(function () {
    function show(event, text) {
        var target = event.detail.target;
        if (!target) {
            return;
        }
        var alert = document.createElement('div');
        alert.className = 'alert err';
        alert.textContent = text;
        target.replaceChildren(alert);
    }

    document.addEventListener('htmx:responseError', function (event) {
        var status = event.detail.xhr ? event.detail.xhr.status : 0;
        show(event, status === 403 ? 'You do not have permission to do this.'
            : status === 404 ? 'This item no longer exists.'
            : 'Something went wrong. Please try again.');
    });
    document.addEventListener('htmx:sendError', function (event) {
        show(event, 'The server could not be reached. Check your connection and try again.');
    });
})();
