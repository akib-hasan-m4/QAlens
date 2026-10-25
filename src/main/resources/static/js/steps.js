// Step editor: add and remove rows, keeping the indexed field names (steps[0].action ...) contiguous.
(function () {
    var list = document.getElementById('steps');
    var template = document.getElementById('step-template');
    var add = document.getElementById('step-add');
    if (!list || !template || !add) {
        return;
    }

    function rows() {
        return list.querySelectorAll('.step-row');
    }

    function renumber() {
        rows().forEach(function (row, index) {
            row.querySelector('.step-no').textContent = index + 1;
            var inputs = row.querySelectorAll('input');
            inputs[0].name = 'steps[' + index + '].action';
            inputs[1].name = 'steps[' + index + '].expected';
        });
    }

    add.addEventListener('click', function () {
        var html = template.innerHTML.replace(/__INDEX__/g, String(rows().length));
        var holder = document.createElement('div');
        holder.innerHTML = html;
        var row = holder.querySelector('.step-row');
        list.appendChild(row);
        renumber();
        row.querySelector('input').focus();
    });

    list.addEventListener('click', function (event) {
        var button = event.target.closest('.step-remove');
        if (!button) {
            return;
        }
        var row = button.closest('.step-row');
        if (rows().length > 1) {
            row.remove();
        } else {
            row.querySelectorAll('input').forEach(function (input) { input.value = ''; });
        }
        renumber();
    });
})();
