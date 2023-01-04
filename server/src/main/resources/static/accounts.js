$(document).ready(function () {
    login(function () {
        $('#logout').on('click', function () { logout(); });
        initAccounts();
    });
});

function initAccounts() {
    let table = $('#accounts').DataTable({
        dom: '<"container-fluid"<"row"<"col"B><"col"l><"col"f>>>rt<"container-fluid"<"row align-items-center"<"col"i><"col"p>>>',
        ajax: {
            url: '/accounts',
            dataSrc: '',
            beforeSend: function (xhr) {
                xhr.setRequestHeader("Authorization", `Bearer ${get_token()}`);
            },
        },
        pageLength: 15,
        lengthMenu: [
            [15, 25, 50, -1],
            [15, 25, 50, 'All'],
        ],
        buttons: [
            {
                text: '<i class="fab bi bi-plus"></i>',
                titleAttr: 'Add Account',
                className: 'btn-success btn-sm',
                action: function (e, dt, node, config) {
                    showFormModal(
                        'Add Account',
                        `
                            <form id="add-account" class="needs-validation" novalidate>
                                <div class="py-2">
                                    <label for="externalIdInput">External ID</label>
                                    <input type="text" class="form-control" id="externalIdInput">
                                    <div class="invalid-feedback">An external ID must be provided</div>
                                </div>
                                <div class="py-2">
                                    <label for="nameInput">Name</label>
                                    <input type="text" class="form-control" id="nameInput">
                                    <div class="invalid-feedback">A name must be provided</div>
                                </div>
                                <div class="py-2">
                                    <label for="descriptionInput">Description</label>
                                    <input type="text" class="form-control" id="descriptionInput">
                                    <div class="invalid-feedback">A description must be provided</div>
                                </div>
                            </form>
                        `,
                        'Add',
                        function () {
                            let form = $('form#add-account');

                            let externalIdInput = form.find('#externalIdInput');
                            let nameInput = form.find('#nameInput');
                            let descriptionInput = form.find('#descriptionInput');

                            let externalId = externalIdInput.val().trim();
                            let name = nameInput.val().trim();
                            let description = descriptionInput.val().trim();

                            if (!externalId || externalId.length === 0) {
                                externalIdInput.addClass('is-invalid');
                            } else {
                                externalIdInput.removeClass('is-invalid');
                            }

                            if (!name || name.length === 0) {
                                nameInput.addClass('is-invalid');
                            } else {
                                nameInput.removeClass('is-invalid');
                            }

                            if (!description || description.length === 0) {
                                descriptionInput.addClass('is-invalid');
                            } else {
                                descriptionInput.removeClass('is-invalid');
                            }

                            if (!externalIdInput.hasClass('is-invalid') && !nameInput.hasClass('is-invalid') && !descriptionInput.hasClass('is-invalid')) {
                                let request = {
                                    'external_id': externalId,
                                    'name': name,
                                    'description': description,
                                };

                                $.ajax({
                                    type: 'POST',
                                    url: '/accounts',
                                    contentType: "application/json",
                                    data: JSON.stringify(request),
                                    success: function () {
                                        showSuccessToast('Successfully crated account');
                                        table.ajax.reload();
                                        hideModal();
                                    },
                                    error: function (xhr, textStatus, errorThrown) {
                                        showErrorToast(`Failed to create account: [${errorThrown}]`);
                                    },
                                    beforeSend: function (xhr) {
                                        xhr.setRequestHeader("Authorization", `Bearer ${get_token()}`);
                                    },
                                });

                                return false;
                            } else {
                                return true;
                            }
                        },
                    );
                },
            },
            {
                extend: 'excel',
                className: 'btn-primary btn-sm',
                text: '<i class="bi bi-file-earmark-spreadsheet"></i>',
                titleAttr: 'Export as Excel',
            },
            {
                extend: 'pdf',
                className: 'btn-primary btn-sm',
                text: '<i class="bi bi-file-pdf"></i>',
                titleAttr: 'Export as PDF',
            },
            {
                className: 'btn-primary btn-sm',
                text: '<i class="bi bi-filetype-json"></i>',
                titleAttr: 'Export as JSON',
                action: function (e, dt, button, config) {
                    let data = dt.buttons.exportData().body
                        .map(function (row) {
                            return {
                                'external_id': row[1],
                                'name': row[2],
                                'description': row[3]
                            };
                        });

                    $.fn.dataTable.fileSave(new Blob([JSON.stringify(data)]), 'accounts.json');
                }
            },
            {
                text: '<i class="bi bi-file-earmark-arrow-up"></i>',
                titleAttr: 'Import Accounts from JSON',
                className: 'btn-warning btn-sm',
                action: function (e, dt, node, config) {
                    showFormModal(
                        'Import Accounts from JSON',
                        createImportForm('import-accounts'),
                        'Import',
                        function () {
                            let request = validateAndLoadImportFormData('import-accounts');

                            if (request) {
                                $.ajax({
                                    type: 'POST',
                                    url: `/accounts/import`,
                                    processData: false,
                                    contentType: false,
                                    data: request.file,
                                    success: function () {
                                        showSuccessToast('Successfully imported accounts');
                                        table.ajax.reload();
                                        hideModal();
                                    },
                                    error: function (xhr, textStatus, errorThrown) {
                                        showErrorToast(`Failed to imported accounts: [${errorThrown === 'Conflict' ? 'One or more accounts already exist' : errorThrown}]`);
                                    },
                                    beforeSend: function (xhr) {
                                        xhr.setRequestHeader("Authorization", `Bearer ${get_token()}`);
                                    },
                                });

                                return false;
                            } else {
                                return true;
                            }
                        }
                    );
                }
            },
        ],
        columns: [
            { data: 'id' },
            { data: 'external_id' },
            { data: 'name' },
            { data: 'description' },
            { data: 'created' },
            { data: 'updated' },
            {
                data: null,
                render: function (data, type, row) {
                    let buttons = [
                        '<div class="btn-group" role="group">',
                        '<button type="button" class="update-account btn btn-success btn-sm disabled" title="Update Account"><i class="fab bi bi-save"></i></button>',
                        '<button type="button" class="remove-account btn btn-danger btn-sm" title="Remove Account"><i class="fab bi bi-trash"></i></button>',
                        '</div>'
                    ];
                    return buttons.join('');
                },
            }
        ],
        columnDefs: [
            {
                render: function (data, type, row) {
                    return data.replace('T', ' ').split('.')[0];
                },
                targets: [4, 5],
            },
            {
                createdCell: function (cell) {
                    $(cell).addClass('input-cell');
                    cell.setAttribute('contenteditable', true);
                    cell.addEventListener("keyup", editEventHandler(table, cell, "external_id"));
                },
                targets: 1,
            },
            {
                createdCell: function (cell) {
                    $(cell).addClass('input-cell');
                    cell.setAttribute('contenteditable', true);
                    cell.addEventListener("keyup", editEventHandler(table, cell, "name"));
                },
                targets: 2,
            },
            {
                createdCell: function (cell) {
                    $(cell).addClass('input-cell');
                    cell.setAttribute('contenteditable', true);
                    cell.addEventListener("keyup", editEventHandler(table, cell, "description"));
                },
                targets: 3,
            },
        ]
    });

    $('#accounts tbody').on('click', '.update-account', function () {
        let self = $(this);
        let row = table.row(self.parents('tr'));
        let data = row.data();

        if (!self.hasClass('disabled')) {
            let request = buildRequest(data);

            $.ajax({
                type: 'PUT',
                url: `/accounts/${data['id']}`,
                contentType: "application/json",
                data: JSON.stringify(request),
                success: function () {
                    resetData(data, request);
                    resetRow(self.parents('tr'));
                    resetUpdateButtonState(self);
                    showSuccessToast(`Successfully updated account [${data['id']}]`);
                },
                error: function (xhr, textStatus, errorThrown) {
                    showErrorToast(`Failed to update account [${data['id']}]: [${errorThrown}]`);
                },
                beforeSend: function (xhr) {
                    xhr.setRequestHeader("Authorization", `Bearer ${get_token()}`);
                },
            });
        }
    });

    $('#accounts tbody').on('click', '.remove-account', function () {
        let self = $(this);
        let row = table.row(self.parents('tr'));
        let data = row.data();

        if (!self.hasClass('disabled')) {
            showConfirmationModal(
                `Remove account [${data['id']}]?`,
                function () {
                    $.ajax({
                        type: 'DELETE',
                        url: `/accounts/${data['id']}`,
                        success: function () {
                            showSuccessToast(`Successfully removed account [${data['id']}]`);
                            table.ajax.reload();
                            hideModal();
                        },
                        error: function (xhr, textStatus, errorThrown) {
                            showErrorToast(`Failed to remove account [${data['id']}]: [${errorThrown}]`);
                        },
                        beforeSend: function (xhr) {
                            xhr.setRequestHeader("Authorization", `Bearer ${get_token()}`);
                        },
                    });
                    return false;
                }
            );
        }
    });
};

function setUpdateButtonState(updateButton) {
    let anyUpdated = updateButton.hasClass('external_id-updated') || updateButton.hasClass('name-updated') || updateButton.hasClass('description-updated');
    let noInvalid = updateButton.closest('tr').find('.is-invalid').length === 0;

    if (anyUpdated && noInvalid) {
        updateButton.removeClass('disabled');
    } else {
        updateButton.addClass('disabled');
    }
}

function resetUpdateButtonState(updateButton) {
    updateButton.removeClass('external_id-updated name-updated description-updated');
    updateButton.addClass('disabled');
}

function buildRequest(data) {
    return {
        'external_id': data['updated_external_id'] ?? data['external_id'],
        'name': data['updated_name'] ?? data['name'],
        'description': data['updated_description'] ?? data['description'],
    };
}

function resetData(data, updateRequest) {
    data['external_id'] = updateRequest['external_id'];
    data['name'] = updateRequest['name'];
    data['description'] = updateRequest['description'];
    delete data['updated_external_id'];
    delete data['updated_name'];
    delete data['updated_description'];
}

function resetRow(row) {
    row.find('td').each((k, v) => $(v).removeClass('border-warning'));
}

function editEventHandler(table, cell, field) {
    return function (e) {
        let row = table.row(e.target.parentElement);
        let jqCell = $(cell);
        let updateButton = $($(e.target.parentElement).find('.update-account')[0]);

        let data = row.data();
        let original = data[field];
        let current = e.target.textContent.trim();

        if (original !== current) {
            if (!current || current.length === 0) {
                jqCell
                    .removeClass('border-warning')
                    .addClass('border-danger is-invalid')
                    .prop('title', `A valid ${field} must be provided.`);
                updateButton.removeClass(`${field}-updated`);
                delete data[`updated_${field}`];
            } else {
                jqCell
                    .addClass('border-warning')
                    .removeClass('border-danger is-invalid')
                    .removeAttr('title');
                updateButton.addClass(`${field}-updated`);
                data[`updated_${field}`] = current;
            }
        } else {
            jqCell.removeClass('border-warning');
            updateButton.removeClass(`${field}-updated`);
            delete data[`updated_${field}`];
        }

        setUpdateButtonState(updateButton);
    };
}

function createImportForm(id) {
    return `
        <form id="${id}" class="needs-validation" novalidate>
            <div class="input-group">
                <label class="input-group-text" for="fileInput">File</label>
                <input type="file" class="form-control" id="fileInput" placeholder="File">
                <div class="invalid-feedback">A file must be provided</div>
            </div>
        </form>
    `;
}

function validateAndLoadImportFormData(id) {
    let form = $(`#${id}`);
    let fileInput = form.find('#fileInput');

    var inputs = {
        'file': fileInput,
    };

    for (const [key, input] of Object.entries(inputs)) {
        let inputValue = (input.val() || '').trim();

        if (!inputValue || inputValue.length === 0) {
            input.addClass('is-invalid');
        } else {
            input.removeClass('is-invalid');
        }
    }

    if (Object.values(inputs).every((input) => !input.hasClass('is-invalid'))) {
        let request = Object.entries(inputs).reduce(function (collected, [key, input]) {
            let inputValue = input.val().trim();

            if (key === 'file') {
                let data = new FormData();
                data.append(key, input.prop('files')[0]);
                collected[key] = data;
            } else {
                collected[key] = inputValue;
            }

            return collected;
        }, {});

        return request;
    } else {
        return null;
    }
}
