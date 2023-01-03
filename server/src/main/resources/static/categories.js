$(document).ready(function () {
    login(function () {
        $('#logout').on('click', function () { logout(); });
        initCategories();
    });
});

function initCategories() {
    let table = $('#categories').DataTable({
        dom: '<"container-fluid"<"row"<"col"B><"col"l><"col"f>>>rt<"container-fluid"<"row align-items-center"<"col"i><"col"p>>>',
        ajax: {
            url: `/categories`,
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
                titleAttr: 'Add Category Mapping',
                className: 'btn-success btn-sm',
                action: function (e, dt, node, config) {
                    showFormModal(
                        'Add Category Mapping',
                        createCategoryMappingForm('add-category-mapping', null),
                        'Add',
                        function () {
                            let request = validateAndLoadCategoryMappingFormData('add-category-mapping');

                            if (request) {
                                $.ajax({
                                    type: 'POST',
                                    url: '/categories',
                                    contentType: "application/json",
                                    data: JSON.stringify(request),
                                    success: function () {
                                        showSuccessToast('Successfully crated category mapping');
                                        table.ajax.reload();
                                        hideModal();
                                    },
                                    error: function (xhr, textStatus, errorThrown) {
                                        showErrorToast(`Failed to create category mapping: [${errorThrown}]`);
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
                text: '<i class="fab bi bi-tags"></i>',
                titleAttr: 'Add Category Mappings',
                className: 'btn-warning btn-sm',
                action: function (e, dt, node, config) {
                    showFormModal(
                        'Apply Category Mappings',
                        createCategoryMappingApplicationForm('apply-category-mappings'),
                        'Apply',
                        function () {
                            let request = validateAndLoadCategoryMappingApplicationFormData('apply-category-mappings');

                            if (request) {
                                $.ajax({
                                    type: 'PUT',
                                    url: '/categories/apply',
                                    contentType: "application/json",
                                    data: JSON.stringify(request),
                                    success: function (result) {
                                        showSuccessToast('Successfully applied category mappings');
                                        hideModal();
                                        showInfoModal(
                                            'Category Mapping Results',
                                            renderCategoryMappingResult(result, request.for_period)
                                        );
                                    },
                                    error: function (xhr, textStatus, errorThrown) {
                                        showErrorToast(`Failed to apply category mappings: [${errorThrown}]`);
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

                    $("#forPeriodInput").datepicker({
                        format: "yyyy-mm",
                        minViewMode: 1,
                        autoclose: true,
                    });
                }
            },
        ],
        columns: [
            { data: 'id' },
            {
                data: null,
                render: function (data, type, row) {
                    return `<p class="text-capitalize">${data.condition.split('_').join(' ')}</p>`;
                }
            },
            { data: 'matcher' },
            { data: 'category' },
            {
                data: null,
                render: function (data, type, row) {
                    let created = data.created.replace('T', ' ').split('.')[0];
                    let updated = data.updated.replace('T', ' ').split('.')[0];
                    return `
                                <div class="col">
                                    <div class="row"><small><strong>Created:</strong> ${created}</small></div>
                                    <div class="row"><small><strong>Updated:</strong> ${updated}</small></div>
                                </div>
                            `;
                }
            },
            {
                data: null,
                render: function (data, type, row) {
                    let buttons = [
                        '<div class="btn-group" role="group">',
                        '<button type="button" class="edit-category-mapping btn btn-warning btn-sm" title="Show Category Mapping Details"><i class="fab bi bi-pencil"></i></button>',
                        '<button type="button" class="remove-category-mapping btn btn-danger btn-sm" title="Remove Category Mapping"><i class="fab bi bi-trash"></i></button>',
                        '</div>'
                    ];
                    return buttons.join('');
                },
            }
        ],
        columnDefs: [
            {
                className: 'dt-body-right',
                targets: 5,
            },
        ],
        order: [[0, 'asc']],
    });

    $('#categories tbody').on('click', '.edit-category-mapping', function () {
        let self = $(this);
        let row = table.row(self.parents('tr'));
        let data = row.data();

        if (!self.hasClass('disabled')) {
            showFormModal(
                'Update Category Mapping',
                createCategoryMappingForm('update-category-mapping', data),
                'Update',
                function () {
                    let request = validateAndLoadCategoryMappingFormData('update-category-mapping');

                    if (request) {
                        $.ajax({
                            type: 'PUT',
                            url: `/categories/${data['id']}`,
                            contentType: "application/json",
                            data: JSON.stringify(request),
                            success: function () {
                                showSuccessToast(`Successfully updated category mapping [${data['id']}]`);
                                table.ajax.reload();
                                hideModal();
                            },
                            error: function (xhr, textStatus, errorThrown) {
                                showErrorToast(`Failed to update category mapping [${data['id']}]: [${errorThrown}]`);
                            },
                            beforeSend: function (xhr) {
                                xhr.setRequestHeader("Authorization", `Bearer ${get_token()}`);
                            },
                        });
                    } else {
                        return true;
                    }
                }
            );
        }
    });

    $('#categories tbody').on('click', '.remove-category-mapping', function () {
        let self = $(this);
        let row = table.row(self.parents('tr'));
        let data = row.data();

        if (!self.hasClass('disabled')) {
            showConfirmationModal(
                `Remove category mapping [${data['id']}]?`,
                function () {
                    $.ajax({
                        type: 'DELETE',
                        url: `/categories/${data['id']}`,
                        success: function () {
                            showSuccessToast(`Successfully removed category mapping [${data['id']}]`);
                            table.ajax.reload();
                            hideModal();
                        },
                        error: function (xhr, textStatus, errorThrown) {
                            showErrorToast(`Failed to remove category mapping [${data['id']}]: [${errorThrown}]`);
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
}

function createCategoryMappingForm(id, existingCategoryMapping) {
    let existing = existingCategoryMapping || {};

    var extraFields = ``;
    if (existingCategoryMapping) {
        extraFields = `
            <div class="py-2">
                <label for="idInput">ID</label>
                <input type="text" class="form-control" id="idInput" value="${existing.id}" readonly>
            </div>
        `;
    }

    return `
        <form id="${id}" class="needs-validation" novalidate>
            ${extraFields}
            <div class="py-2">
                <label for="conditionInput">Type</label>
                <select class="form-select" id="conditionInput" aria-label="Type">
                    <option value="starts_with" ${existing.condition == 'starts_with' ? 'selected' : ''}>Starts With</option>
                    <option value="ends_with" ${existing.condition == 'ends_with' ? 'selected' : ''}>Ends With</option>
                    <option value="contains" ${existing.condition == 'contains' ? 'selected' : ''}>Contains</option>
                    <option value="equals" ${existing.condition == 'equals' ? 'selected' : ''}>Equals</option>
                    <option value="matches" ${existing.condition == 'matches' ? 'selected' : ''}>Matches</option>
                </select>
                <div class="invalid-feedback">A condition must be provided</div>
            </div>
            <div class="py-2">
                <label for="matcherInput">Matcher</label>
                <input type="text" class="form-control" id="matcherInput" value="${existing.matcher || ""}">
                <div class="invalid-feedback">An matcher must be provided</div>
            </div>
            <div class="py-2">
                <label for="categoryInput">Category</label>
                <input type="text" class="form-control" id="categoryInput" value="${existing.category || ""}">
                <div class="invalid-feedback">An category must be provided</div>
            </div>
        </form>
    `;
}

function validateAndLoadCategoryMappingFormData(id) {
    let form = $(`#${id}`);
    let conditionInput = form.find('#conditionInput');
    let matcherInput = form.find('#matcherInput');
    let categoryInput = form.find('#categoryInput');

    var inputs = {
        'condition': conditionInput,
        'matcher': matcherInput,
        'category': categoryInput,
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
            collected[key] = input.val().trim();
            return collected;
        }, {});

        return request;
    } else {
        return null;
    }
}

function createCategoryMappingApplicationForm(id) {
    return `
        <form id="${id}" class="needs-validation" novalidate>
            <div class="py-2">
                <label for="forPeriodInput">Date</label>
                <input type="text" class="form-control" id="forPeriodInput">
                <div class="invalid-feedback">A valid transaction period must be provided</div>
            </div>
            <div class="form-check form-switch">
                <input class="form-check-input" type="checkbox" role="switch" id="overrideExistingInput">
                <label class="form-check-label" for="overrideExistingInput">Override Existing Categories</label>
            </div>
        </form>
    `;
}

function validateAndLoadCategoryMappingApplicationFormData(id) {
    let form = $(`#${id}`);
    let forPeriodInput = form.find('#forPeriodInput');
    let overrideExistingInput = form.find('#overrideExistingInput');

    var inputs = {
        'for_period': forPeriodInput,
        'override_existing': overrideExistingInput,
    };

    for (const [key, input] of Object.entries(inputs)) {
        let inputValue = (input.val() || '').trim();

        if (!inputValue || inputValue.length === 0) {
            input.addClass('is-invalid');
        } else if(key === 'for_period' && !/^(\d{4})-(0?[1-9]|1[012])$/.test(inputValue)) {
            input.addClass('is-invalid');
        } else {
            input.removeClass('is-invalid');
        }
    }

    if (Object.values(inputs).every((input) => !input.hasClass('is-invalid'))) {
        return {
            'for_period': forPeriodInput.val(),
            'override_existing': overrideExistingInput.is(":checked"),
        };
    } else {
        return null;
    }
}

function renderCategoryMappingResult(result, period) {
    return `
        <div class="py-2">
            <ul class="list-group">
                ${mappingResultItem('Mappings Found', 'Category mappings found', result.category_mappings_found, 'text-bg-secondary')}
                ${mappingResultItem('Transactions Found', `Transactions loaded for <strong>${period}</strong>`, result.transactions_found, 'text-bg-secondary')}
                ${mappingResultItem('Transactions Updated', 'Successfully updated transactions', result.transactions_updated, 'text-bg-success')}
            </ul>
        </div>
    `;
}

function mappingResultItem(title, subtitle, count, countClass) {
    return `
        <li class="list-group-item d-flex justify-content-between align-items-start">
            <div class="ms-2 me-auto">
                <div class="fw-bold">${title}</div>
                <small class="text-muted">${subtitle}</small>
            </div>
            <span class="badge ${countClass}">${count}</span>
        </li>
    `;
}
