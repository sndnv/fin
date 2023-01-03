$(document).ready(function () {
    login(function () {
        $('#logout').on('click', function () { logout(); });
        initForecasts();
    });
});

function initForecasts() {
    $.when(getAccounts(), getCategories()).then(
        function (accountData, categoryData) {
            let accounts = accountData[0].reduce(function (collected, current) {
                collected[current.id] = current;
                return collected;
            }, {});

            let categories = categoryData[0];
            categories.sort();

            let table = $('#forecasts').DataTable({
                dom: '<"container-fluid"<"row"<"col"B><"col"l><"col"f>>>rt<"container-fluid"<"row align-items-center"<"col"i><"col"p>>>',
                ajax: {
                    url: `/forecasts?period=${selectedPeriod()}`,
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
                        titleAttr: 'Add Forecast',
                        className: 'btn-success btn-sm',
                        action: function (e, dt, node, config) {
                            showFormModal(
                                'Add Forecast',
                                createForecastForm('add-forecast', accounts, categories, null),
                                'Add',
                                function () {
                                    let request = validateAndLoadForecastFormData('add-forecast');

                                    if (request) {
                                        $.ajax({
                                            type: 'POST',
                                            url: '/forecasts',
                                            contentType: "application/json",
                                            data: JSON.stringify(request),
                                            success: function () {
                                                showSuccessToast('Successfully crated forecast');
                                                table.ajax.reload();
                                                hideModal();
                                            },
                                            error: function (xhr, textStatus, errorThrown) {
                                                showErrorToast(`Failed to create forecast: [${errorThrown}]`);
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

                            $("#categoryInput").select2({
                                tags: true,
                                theme: 'bootstrap-5',
                                dropdownParent: $('#default-modal'),
                            });

                            $("#dateInput").datepicker({
                                format: "yyyy-mm-dd",
                                autoclose: true,
                                clearBtn: true,
                            });
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
                ],
                columns: [
                    { data: 'id' },
                    {
                        data: null,
                        render: function (data, type, row, meta) {
                            if (data.account in accounts) {
                                return accounts[data.account].name;
                            } else {
                                return data.account;
                            }
                        },
                    },
                    {
                        data: null,
                        render: function (data, type, row, meta) {
                            let amount = forecastAmount(data.type, data.amount);

                            if (type === 'sort') {
                                return amount;
                            } else {
                                let actualAmount = DataTable.render.number('.', ',', 2).display(amount);

                                if (data.type === 'credit') {
                                    return `<p class='text-success'>${actualAmount} ${data.currency}</p>`;
                                } else {
                                    return `${actualAmount} ${data.currency}`;
                                };
                            }
                        },
                    },
                    { data: 'date' },
                    { data: 'category' },
                    {
                        data: null,
                        render: function (data, type, row, meta) {
                            if (type === 'sort') {
                                return data.notes;
                            } else if (data.notes) {
                                return `
                                    <button type="button" class="btn btn-sm btn-link notes-info" style="--bs-btn-padding-y: 0; --bs-btn-padding-x: 0;">
                                        <span class="d-inline-block text-truncate" style="max-width: 8em;">
                                            ${data.notes}
                                        </span>
                                    </button>
                                `;
                            } else {
                                return '-';
                            }
                        }
                    },
                    {
                        data: null,
                        render: function (data, type, row) {
                            let day = data.disregard_after;

                            if (type === 'sort') {
                                return day;
                            } else {
                                let content = `After the <strong>${day}<sup>${dayOfMonthToString(day)}</sup></strong>`;

                                if (data.date) {
                                    return `<s title="Ignored because a specific forecast date is provided">${content}</s>`;
                                } else {
                                    return content;
                                }
                            }
                        }
                    },
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
                                '<button type="button" class="edit-forecast btn btn-warning btn-sm" title="Show Forecast Details"><i class="fab bi bi-pencil"></i></button>',
                                '<button type="button" class="remove-forecast btn btn-danger btn-sm" title="Remove Forecast"><i class="fab bi bi-trash"></i></button>',
                                '</div>'
                            ];
                            return buttons.join('');
                        },
                    }
                ],
                columnDefs: [
                    {
                        type: 'num',
                        targets: [0, 2, 6]
                    },
                    {
                        className: 'dt-body-right fw-bold',
                        targets: 2,
                    },
                    {
                        className: 'dt-body-right',
                        targets: 7,
                    },
                    {
                        defaultContent: '-',
                        targets: [3, 5],
                    },
                ],
                order: [[3, 'desc']],
                rowGroup: {
                    dataSrc: 'date',
                    startRender: function (rows, group) {
                        let groupTitle = group === 'No group' ? 'Recurring' : group;
                        let amounts = calculateForecastAmounts(rows.data());
                        return `
                            <div class="row">
                                <div class="col text-start">${groupTitle} (${rows.count()})</div>
                                <div class="col text-end">Total: ${amounts}</div>
                            </div>
                        `;
                    }
                }
            });

            initPeriodControl((control) => $('.dt-buttons').after(control));

            $('#forecasts tbody').on('click', '.edit-forecast', function () {
                let self = $(this);
                let row = table.row(self.parents('tr'));
                let data = row.data();

                if (!self.hasClass('disabled')) {
                    showFormModal(
                        'Update Forecast',
                        createForecastForm('update-forecast', accounts, categories, data),
                        'Update',
                        function () {
                            let request = validateAndLoadForecastFormData('update-forecast');

                            if (request) {
                                $.ajax({
                                    type: 'PUT',
                                    url: `/forecasts/${data['id']}`,
                                    contentType: "application/json",
                                    data: JSON.stringify(request),
                                    success: function () {
                                        showSuccessToast(`Successfully updated forecast [${data['id']}]`);
                                        table.ajax.reload();
                                        hideModal();
                                    },
                                    error: function (xhr, textStatus, errorThrown) {
                                        showErrorToast(`Failed to update forecast [${data['id']}]: [${errorThrown}]`);
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

                    $("#categoryInput").select2({
                        tags: true,
                        theme: 'bootstrap-5',
                        dropdownParent: $('#default-modal'),
                    });

                    $("#dateInput").datepicker({
                        format: "yyyy-mm-dd",
                        autoclose: true,
                        clearBtn: true,
                    });
                }
            });

            $('#forecasts tbody').on('click', '.remove-forecast', function () {
                let self = $(this);
                let row = table.row(self.parents('tr'));
                let data = row.data();

                if (!self.hasClass('disabled')) {
                    showConfirmationModal(
                        `Remove forecast [${data['id']}]?`,
                        function () {
                            $.ajax({
                                type: 'DELETE',
                                url: `/forecasts/${data['id']}`,
                                success: function () {
                                    showSuccessToast(`Successfully removed forecast [${data['id']}]`);
                                    table.ajax.reload();
                                    hideModal();
                                },
                                error: function (xhr, textStatus, errorThrown) {
                                    showErrorToast(`Failed to remove forecast [${data['id']}]: [${errorThrown}]`);
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

            $('#forecasts tbody').on('click', '.notes-info', function () {
                let self = $(this);
                let row = table.row(self.parents('tr'));
                let data = row.data();

                showInfoModal(`Notes for transaction [${data['id']}]`, `<p class="font-monospace">${data.notes.split(' ').join('\n')}</p>`);
            });
        }
    );
};

function dayOfMonthToString(day) {
    if (day > 3 && day < 21) {
        return 'th';
    } else {
        switch (day % 10) {
            case 1: return "st";
            case 2: return "nd";
            case 3: return "rd";
            default: return "th";
        }
    }
}

function forecastAmount(type, amount) {
    return type === 'debit' ? (amount * -1) : amount;
}

function calculateForecastAmounts(forecasts) {
    let amounts = forecasts.reduce(function (collected, current) {
        let updated = (collected[current.currency] ?? 0) + forecastAmount(current.type, current.amount);
        collected[current.currency] = updated;
        return collected;
    }, {});

    return Object
        .keys(amounts)
        .map(function (currency) {
            let amount = DataTable.render.number('.', ',', 2).display(amounts[currency]);
            if (amounts[currency] > 0) {
                return `<span class="badge text-bg-success">${amount} ${currency}</span>`;
            } else {
                return `<span class="badge text-bg-dark">${amount} ${currency}</span>`;
            }
        })
        .join('&nbsp;');
}

function createForecastForm(id, accounts, categories, existingForecast) {
    let existing = existingForecast || {};

    var extraFields = ``;
    if (existingForecast) {
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
            <div class="row py-2">
                <div class="col-9">
                    <div>
                        <label for="amountInput">Amount</label>
                        <input type="number" class="form-control" id="amountInput" min="0" step=".01" value="${existing.amount || ""}">
                        <div class="invalid-feedback">An amount must be provided</div>
                    </div>
                </div>
                <div class="col">
                    <div>
                        <label for="currencyInput">Currency</label>
                        <select class="form-select" id="currencyInput" aria-label="Currency">
                            <option value="EUR" ${existing.currency == 'EUR' ? 'selected' : ''}>EUR</option>
                            <option value="USD" ${existing.currency == 'USD' ? 'selected' : ''}>USD</option>
                            <option value="GBP" ${existing.currency == 'GBP' ? 'selected' : ''}>GBP</option>
                        </select>
                        <div class="invalid-feedback">A currency must be provided</div>
                    </div>
                </div>
            </div>
            <div class="py-2">
                <label for="typeInput">Type</label>
                <select class="form-select" id="typeInput" aria-label="Type">
                    <option value="debit" ${existing.type == 'debit' ? 'selected' : ''}>Debit</option>
                    <option value="credit" ${existing.type == 'credit' ? 'selected' : ''}>Credit</option>
                </select>
                <div class="invalid-feedback">A type must be provided</div>
            </div>
            ${accountsSelect('accountInput', 'Account', 'An account must be provided', accounts, false, existing.account)}
            <div class="py-2">
                <label for="dateInput">Date</label>
                <input type="text" class="form-control" id="dateInput" value="${existing.date || ''}">
            </div>
            ${categoriesSelect('categoryInput', 'Category', 'A category must be provided', categories, existing.category)}
            <div class="py-2">
                <label for="disregardAfterInput">Disregard After</label>
                <input type="number" class="form-control" id="disregardAfterInput" min="1" max="31" step="1" value="${existing.disregard_after || "31"}">
                <div class="invalid-feedback">A valid day of the month must be provided</div>
            </div>
            <div class="py-2">
                <label for="notesInput">Notes</label>
                <textarea class="form-control" id="notesInput" placeholder="(optional)" style="height: 100px">${existing.notes || ""}</textarea>
            </div>
        </form>
    `;
}

function validateAndLoadForecastFormData(id) {
    let form = $(`#${id}`);
    let amountInput = form.find('#amountInput');
    let currencyInput = form.find('#currencyInput');
    let typeInput = form.find('#typeInput');
    let accountInput = form.find('#accountInput');
    let dateInput = form.find('#dateInput');
    let categoryInput = form.find('#categoryInput');
    let disregardAfterInput = form.find('#disregardAfterInput');
    let notesInput = form.find('#notesInput');

    var inputs = {
        'amount': amountInput,
        'currency': currencyInput,
        'type': typeInput,
        'account': accountInput,
        'date': dateInput,
        'category': categoryInput,
        'disregard_after': disregardAfterInput,
        'notes': notesInput,
    };

    for (const [key, input] of Object.entries(inputs)) {
        let inputValue = (input.val() || '').trim();

        if (key !== 'notes' && key !== 'date' && (!inputValue || inputValue.length === 0)) {
            input.addClass('is-invalid');
        } else if (key === 'disregard_after' && (!parseInt(inputValue) || parseInt(inputValue) < 0 || parseInt(inputValue) > 31)) {
            input.addClass('is-invalid');
        } else {
            input.removeClass('is-invalid');
        }
    }

    if (Object.values(inputs).every((input) => !input.hasClass('is-invalid'))) {
        let request = Object.entries(inputs).reduce(function (collected, [key, input]) {
            let inputValue = input.val().trim();

            switch (key) {
                case 'amount': collected[key] = parseFloat(parseFloat(inputValue).toFixed(2)); break;
                case 'notes': collected[key] = inputValue.length === 0 ? null : inputValue; break;
                case 'date': collected[key] = inputValue.length === 0 ? null : inputValue; break;
                case 'account': collected[key] = parseInt(inputValue); break;
                case 'disregard_after': collected[key] = parseInt(inputValue); break;
                default: collected[key] = inputValue;
            }

            return collected;
        }, {});

        return request;
    } else {
        return null;
    }
}

function accountsSelect(id, label, errorMessage, accounts, optional, selected) {
    let options = Object
        .values(accounts)
        .map((acc) => `<option value="${acc.id}" ${selected == acc.id ? 'selected' : ''}>${acc.name}</option>`);

    if (optional) {
        options = ['<option value="external">None / External</option>', ...options];
    }

    return `
        <div class="py-2">
            <label for="${id}">${label}</label>
            <select class="form-select" id="${id}" aria-label="${label}">
                ${options.join('\n')}
            </select>
            <div class="invalid-feedback">${errorMessage}</div>
        </div>
    `;
}

function categoriesSelect(id, label, errorMessage, categories, selected) {
    let options = Object
        .values(categories)
        .map((cat) => `<option value="${cat}" ${selected == cat ? 'selected' : ''}>${cat}</option>`);

    return `
        <div class="py-2">
            <label for="${id}">${label}</label>
            <select class="form-select" id="${id}" aria-label="${label}">
                ${options.join('\n')}
            </select>
            <div class="invalid-feedback">${errorMessage}</div>
        </div>
    `;
}

function getAccounts() {
    return $.ajax({
        type: 'GET',
        url: '/accounts',
        contentType: "application/json",
        error: function (xhr, textStatus, errorThrown) {
            showErrorToast(`Failed to load accounts: [${errorThrown}]`);
        },
        beforeSend: function (xhr) {
            xhr.setRequestHeader("Authorization", `Bearer ${get_token()}`);
        },
    });
}

function getCategories() {
    return $.ajax({
        type: 'GET',
        url: '/reports/categories',
        contentType: "application/json",
        error: function (xhr, textStatus, errorThrown) {
            showErrorToast(`Failed to load categories: [${errorThrown}]`);
        },
        beforeSend: function (xhr) {
            xhr.setRequestHeader("Authorization", `Bearer ${get_token()}`);
        },
    })
}
