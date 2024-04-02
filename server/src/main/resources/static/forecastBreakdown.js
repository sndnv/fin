$(document).ready(function () {
    login(function () {
        $('#logout').on('click', function () { logout(); });
        initBreakdown();
    });
});

function initBreakdown() {
    let period = selectedPeriod();

    $.when(getAccounts(), getCategories(), getForecasts(period), getForecastBreakdownEntries(period)).then(
        function (accountData, categoryData, forecastData, entriesData) {
            let accounts = accountData[0].reduce(function (collected, current) {
                collected[current.id] = current;
                return collected;
            }, {});

            let categories = categoryData[0];
            categories.sort();

            let forecastsByCategory = forecastData[0].reduce(function (collected, current) {
                let existing = collected[current.category] || {};
                existing[current.currency] = (existing[current.currency] ?? 0) + entryAmount(current.type, current.amount);
                collected[current.category] = existing;
                return collected;
            }, {});

            let entries = entriesData[0];

            let table = $('#forecastBreakdown').DataTable({
                dom: '<"container-fluid"<"row"<"col"B><"col"l><"col"f>>>rt<"container-fluid"<"row align-items-center"<"col"i><"col"p>>>',
                data: entries,
                pageLength: -1,
                lengthMenu: [
                    [15, 25, 50, -1],
                    [15, 25, 50, 'All'],
                ],
                buttons: [
                    {
                        text: '<i class="fab bi bi-plus"></i>',
                        titleAttr: 'Add Forecast Breakdown Entry',
                        className: 'btn-success btn-sm',
                        action: function (e, dt, node, config) {
                            showFormModal(
                                'Add Forecast Breakdown Entry',
                                createForecastBreakdownEntryForm('add-forecast-breakdown-entry', accounts, categories, null),
                                'Add',
                                function () {
                                    let request = validateAndLoadForecastBreakdownEntryFormData('add-forecast-breakdown-entry');

                                    if (request) {
                                        $.ajax({
                                            type: 'POST',
                                            url: '/forecasts/breakdown',
                                            contentType: "application/json",
                                            data: JSON.stringify(request),
                                            success: function () {
                                                showSuccessToast('Successfully crated forecast breakdown entry');
                                                window.location.reload();
                                                hideModal();
                                            },
                                            error: function (xhr, textStatus, errorThrown) {
                                                showErrorToast(`Failed to create forecast breakdown entry: [${errorThrown}]`);
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
                            let amount = entryAmount(data.type, data.amount);

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
                                '<button type="button" class="edit-forecast-breakdown-entry btn btn-warning btn-sm" title="Show Forecast Breakdown Entry Details"><i class="fab bi bi-pencil"></i></button>',
                                '<button type="button" class="remove-forecast-breakdown-entry btn btn-danger btn-sm" title="Remove Forecast Breakdown Entry"><i class="fab bi bi-trash"></i></button>',
                                '</div>'
                            ];
                            return buttons.join('');
                        },
                    }
                ],
                columnDefs: [
                    {
                        orderable: false,
                        targets: [0, 1, 2, 3, 5, 6, 7]
                    },
                    {
                        type: 'num',
                        targets: [0, 2]
                    },
                    {
                        className: 'dt-body-right fw-bold',
                        targets: 2,
                    },
                    {
                        className: 'dt-body-right',
                        targets: 6,
                    },
                    {
                        defaultContent: '-',
                        targets: [5],
                    },
                ],
                order: [[4, 'asc'],[3, 'desc']],
                rowGroup: {
                    dataSrc: 'category',
                    startRender: function (rows, group) {
                        let rawBreakdownAmounts = rows.data().reduce(function (collected, current) {
                            let updated = (collected[current.currency] ?? 0) + entryAmount(current.type, current.amount);
                            collected[current.currency] = updated;
                            return collected;
                        }, {});

                        let forecastAmounts = calculateAmounts(forecastsByCategory[group] || {});
                        let breakdownAmounts = calculateAmounts(rawBreakdownAmounts);

                        return `
                            <div class="row">
                                <div class="col text-start">${group} (${rows.count()})</div>
                                <div class="col text-end breakdown-text">Forecast: ${forecastAmounts} Breakdown: ${breakdownAmounts}</div>
                            </div>
                        `;
                    },
                    endRender: function (rows, group) {
                        let rawBreakdownAmounts = rows.data().reduce(function (collected, current) {
                            let updated = (collected[current.currency] ?? 0) + entryAmount(current.type, current.amount);
                            collected[current.currency] = updated;
                            return collected;
                        }, {});

                        let rawForecastAmounts = forecastsByCategory[group] || {};

                        let rawRemainingAmounts = [...new Set([...Object.keys(rawBreakdownAmounts) ,... Object.keys(rawForecastAmounts)])]
                            .reduce(function (collected, currency) {
                                let breakdown = rawBreakdownAmounts[currency];
                                let forecast = rawForecastAmounts[currency] || 0;
                                collected[currency] = forecast - breakdown;
                                return collected;
                            }, {});

                        let remainingAmounts = Object.keys(rawForecastAmounts).length === 0
                            ? calculateAmounts({})
                            : calculateAmounts(rawRemainingAmounts);
                        return `
                            <div class="row">
                                <div class="col text-end">Remaining: ${remainingAmounts}</div>
                            </div>
                        `;
                    }
                }
            });

            initPeriodControl((control) => $('.dt-buttons').after(control));

            $('#forecastBreakdown tbody').on('click', '.edit-forecast-breakdown-entry', function () {
                let self = $(this);
                let row = table.row(self.parents('tr'));
                let data = row.data();

                if (!self.hasClass('disabled')) {
                    showFormModal(
                        'Update Forecast Breakdown Entry',
                        createForecastBreakdownEntryForm('update-forecast-breakdown-entry', accounts, categories, data),
                        'Update',
                        function () {
                            let request = validateAndLoadForecastBreakdownEntryFormData('update-forecast-breakdown-entry');

                            if (request) {
                                $.ajax({
                                    type: 'PUT',
                                    url: `/forecasts/breakdown/${data['id']}`,
                                    contentType: "application/json",
                                    data: JSON.stringify(request),
                                    success: function () {
                                        showSuccessToast(`Successfully updated forecast breakdown entry [${data['id']}]`);
                                        window.location.reload();
                                        hideModal();
                                    },
                                    error: function (xhr, textStatus, errorThrown) {
                                        showErrorToast(`Failed to update forecast breakdown entry [${data['id']}]: [${errorThrown}]`);
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

            $('#forecastBreakdown tbody').on('click', '.remove-forecast-breakdown-entry', function () {
                let self = $(this);
                let row = table.row(self.parents('tr'));
                let data = row.data();

                if (!self.hasClass('disabled')) {
                    showConfirmationModal(
                        `Remove forecast [${data['id']}]?`,
                        function () {
                            $.ajax({
                                type: 'DELETE',
                                url: `/forecasts/breakdown/${data['id']}`,
                                success: function () {
                                    showSuccessToast(`Successfully removed forecast breakdown entry [${data['id']}]`);
                                    window.location.reload();
                                    hideModal();
                                },
                                error: function (xhr, textStatus, errorThrown) {
                                    showErrorToast(`Failed to remove forecast breakdown entry [${data['id']}]: [${errorThrown}]`);
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

            $('#forecastBreakdown tbody').on('click', '.notes-info', function () {
                let self = $(this);
                let row = table.row(self.parents('tr'));
                let data = row.data();

                showInfoModal(`Notes for entry [${data['id']}]`, `<p class="font-monospace">${data.notes.split(' ').join('\n')}</p>`);
            });
        }
    );
};

function entryAmount(type, amount) {
    return type === 'debit' ? (amount * -1) : amount;
}

function calculateAmounts(amounts) {
    if (Object.keys(amounts).length > 0) {
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
    } else {
        return `<span class="badge bg-secondary">none</span>`;
    }
}

function calculateBreakdownAmounts(entries) {
    let amounts = entries.reduce(function (collected, current) {
        let updated = (collected[current.currency] ?? 0) + entryAmount(current.type, current.amount);
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

function createForecastBreakdownEntryForm(id, accounts, categories, existingEntry) {
    let existing = existingEntry || {};

    return `
        <form id="${id}" class="needs-validation" novalidate>
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
                <label for="notesInput">Notes</label>
                <textarea class="form-control" id="notesInput" placeholder="(optional)" style="height: 100px">${existing.notes || ""}</textarea>
            </div>
        </form>
    `;
}

function validateAndLoadForecastBreakdownEntryFormData(id) {
    let form = $(`#${id}`);
    let amountInput = form.find('#amountInput');
    let currencyInput = form.find('#currencyInput');
    let typeInput = form.find('#typeInput');
    let accountInput = form.find('#accountInput');
    let dateInput = form.find('#dateInput');
    let categoryInput = form.find('#categoryInput');
    let notesInput = form.find('#notesInput');

    var inputs = {
        'amount': amountInput,
        'currency': currencyInput,
        'type': typeInput,
        'account': accountInput,
        'date': dateInput,
        'category': categoryInput,
        'notes': notesInput,
    };

    for (const [key, input] of Object.entries(inputs)) {
        let inputValue = (input.val() || '').trim();

        if (key !== 'notes' && (!inputValue || inputValue.length === 0)) {
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
                case 'account': collected[key] = parseInt(inputValue); break;
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

function getForecasts(forPeriod) {
    return $.ajax({
        type: 'GET',
        url: `/forecasts?period=${forPeriod}`,
        contentType: "application/json",
        error: function (xhr, textStatus, errorThrown) {
            showErrorToast(`Failed to load forecasts for period [${forPeriod}]: [${errorThrown}]`);
        },
        beforeSend: function (xhr) {
            xhr.setRequestHeader("Authorization", `Bearer ${get_token()}`);
        },
    });
}

function getForecastBreakdownEntries(forPeriod) {
    return $.ajax({
        type: 'GET',
        url: `/forecasts/breakdown?period=${forPeriod}`,
        contentType: "application/json",
        error: function (xhr, textStatus, errorThrown) {
            showErrorToast(`Failed to load forecast breakdown entries for period [${forPeriod}]: [${errorThrown}]`);
        },
        beforeSend: function (xhr) {
            xhr.setRequestHeader("Authorization", `Bearer ${get_token()}`);
        },
    });
}
