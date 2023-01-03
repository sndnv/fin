$(document).ready(function () {
    login(function () {
        $('#logout').on('click', function () { logout(); });
        initTransactions();
    });
});

function initTransactions() {
    $.when(getAccounts(), getCategories()).then(
        function (accountData, categoryData) {
            let accounts = accountData[0].reduce(function (collected, current) {
                collected[current.id] = current;
                return collected;
            }, {});

            let categories = categoryData[0];
            categories.sort();

            let table = $('#transactions').DataTable({
                dom: '<"container-fluid"<"row"<"col"B><"col"l><"col"f>>>rt<"container-fluid"<"row align-items-center"<"col"i><"col"p>>>',
                ajax: {
                    url: `/transactions?period=${selectedPeriod()}`,
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
                        titleAttr: 'Manually Add Transaction',
                        className: 'btn-success btn-sm',
                        action: function (e, dt, node, config) {
                            showFormModal(
                                'Add Transaction',
                                createTransactionForm('add-transaction', accounts, categories, null),
                                'Add',
                                function () {
                                    let request = validateAndLoadTransactionFormData('add-transaction');

                                    if (request) {
                                        $.ajax({
                                            type: 'POST',
                                            url: '/transactions',
                                            contentType: "application/json",
                                            data: JSON.stringify(request),
                                            success: function () {
                                                showSuccessToast('Successfully created transaction');
                                                table.ajax.reload();
                                                hideModal();
                                            },
                                            error: function (xhr, textStatus, errorThrown) {
                                                showErrorToast(`Failed to create transaction: [${errorThrown}]`);
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
                    {
                        text: '<i class="bi bi-file-earmark-arrow-up"></i>',
                        titleAttr: 'Import Transactions',
                        className: 'btn-warning btn-sm',
                        action: function (e, dt, node, config) {
                            showFormModal(
                                'Import Transactions',
                                createImportForm('import-transactions', accounts),
                                'Import',
                                function () {
                                    let request = validateAndLoadImportFormData('import-transactions');

                                    if (request) {
                                        let importType = `import_type=${request.import_type}`;
                                        let forAccount = `for_account=${request.for_account}`;
                                        let uploadType = `upload_type=${request.upload_type}`;

                                        $.ajax({
                                            type: 'POST',
                                            url: `/transactions/import?${importType}&${forAccount}&${uploadType}`,
                                            processData: false,
                                            contentType: false,
                                            data: request.file,
                                            dataType: 'json',
                                            success: function (result) {
                                                showSuccessToast('Successfully imported transactions');
                                                table.ajax.reload();
                                                hideModal();
                                                showInfoModal(
                                                    'Import Results',
                                                    renderImportResult(result)
                                                );
                                            },
                                            error: function (xhr, textStatus, errorThrown) {
                                                showErrorToast(`Failed to imported transactions: [${errorThrown}]`);
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
                    {
                        data: null,
                        render: function (data, type, row, meta) {
                            if (data.from in accounts) {
                                return accounts[data.from].name;
                            } else {
                                return data.from;
                            }
                        },
                    },
                    {
                        data: null,
                        render: function (data, type, row, meta) {
                            if (data.to in accounts) {
                                return accounts[data.to].name;
                            } else {
                                return data.to;
                            }
                        },
                    },
                    {
                        data: null,
                        render: function (data, type, row, meta) {
                            let amount = transactionAmount(data.type, data.amount);

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
                                    <button type="button" class="btn btn-link notes-info" style="--bs-btn-padding-y: 0; --bs-btn-padding-x: 0;">
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
                                '<button type="button" class="edit-transaction btn btn-warning btn-sm" title="Show Transaction Details"><i class="fab bi bi-pencil"></i></button>',
                                '<button type="button" class="remove-transaction btn btn-danger btn-sm" title="Remove Transaction"><i class="fab bi bi-trash"></i></button>',
                                '</div>'
                            ];
                            return buttons.join('');
                        },
                    }
                ],
                columnDefs: [
                    {
                        type: 'num',
                        className: 'dt-body-right fw-bold',
                        targets: 2,
                    },
                    {
                        className: 'dt-body-right',
                        targets: 6,
                    },
                    {
                        defaultContent: '-',
                        targets: 1,
                    },
                ],
                order: [[3, 'desc']],
                rowGroup: {
                    dataSrc: 'date',
                    startRender: function (rows, group) {
                        let amounts = calculateTransactionAmounts(rows.data());
                        return `
                            <div class="row">
                                <div class="col text-start">${group} (${rows.count()})</div>
                                <div class="col text-end">Total: ${amounts}</div>
                            </div>
                        `;
                    }
                }
            });

            initPeriodControl((control) => $('.dt-buttons').after(control));

            $('#transactions tbody').on('click', '.edit-transaction', function () {
                let self = $(this);
                let row = table.row(self.parents('tr'));
                let data = row.data();

                if (!self.hasClass('disabled')) {
                    showFormModal(
                        'Update Transaction',
                        createTransactionForm('update-transaction', accounts, categories, data),
                        'Update',
                        function () {
                            let request = validateAndLoadTransactionFormData('update-transaction');

                            if (request) {
                                $.ajax({
                                    type: 'PUT',
                                    url: `/transactions/${data['id']}`,
                                    contentType: "application/json",
                                    data: JSON.stringify(request),
                                    success: function () {
                                        showSuccessToast(`Successfully updated transaction [${data['id']}]`);
                                        table.ajax.reload();
                                        hideModal();
                                    },
                                    error: function (xhr, textStatus, errorThrown) {
                                        showErrorToast(`Failed to update transaction [${data['id']}]: [${errorThrown}]`);
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
                    });
                }
            });

            $('#transactions tbody').on('click', '.remove-transaction', function () {
                let self = $(this);
                let row = table.row(self.parents('tr'));
                let data = row.data();

                if (!self.hasClass('disabled')) {
                    showConfirmationModal(
                        `Remove transaction from [${data['date']}] with ID [${data['external_id'].split('-').at(-1)}]?`,
                        function () {
                            $.ajax({
                                type: 'DELETE',
                                url: `/transactions/${data['id']}`,
                                success: function () {
                                    showSuccessToast(`Successfully removed transaction [${data['external_id']}]`);
                                    table.ajax.reload();
                                    hideModal();
                                },
                                error: function (xhr, textStatus, errorThrown) {
                                    showErrorToast(`Failed to remove transaction [${data['external_id']}]: [${errorThrown}]`);
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

            $('#transactions tbody').on('click', '.notes-info', function () {
                let self = $(this);
                let row = table.row(self.parents('tr'));
                let data = row.data();

                showInfoModal(`Notes for transaction [${data['external_id']}]`, `<p class="font-monospace">${data.notes.split(' ').join('\n')}</p>`);
            });
        }
    );
};

function transactionAmount(type, amount) {
    return type === 'debit' ? (amount * -1) : amount;
}

function calculateTransactionAmounts(transactions) {
    let amounts = transactions.reduce(function (collected, current) {
        let updated = (collected[current.currency] ?? 0) + transactionAmount(current.type, current.amount);
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

function createTransactionForm(id, accounts, categories, existingTransaction) {
    let existing = existingTransaction || {};

    var extraFields = ``;
    if (existingTransaction) {
        extraFields = `
            <div class="py-2">
                <label for="idInput">ID</label>
                <input type="text" class="form-control" id="idInput" value="${existing.id}" readonly>
            </div>
            <div class="py-2">
                <label for="externalIdInput">External ID</label>
                <input type="text" class="form-control" id="externalIdInput" value="${existing.external_id}">
                <div class="invalid-feedback">An external ID must be provided</div>
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
                        <input type="number" class="form-control" id="amountInput" min="0" step=".01" value="${existingTransaction ? existing.amount : ""}">
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
            ${accountsSelect('fromInput', 'From', 'A valid source account must be provided', accounts, false, existing.from)}
            ${accountsSelect('toInput', 'To', 'A valid target account must be provided', accounts, true, existing.to)}
            <div class="py-2">
                <label for="dateInput">Date</label>
                <input type="text" class="form-control" id="dateInput" value="${existing.date || ''}">
                <div class="invalid-feedback">A date must be provided</div>
            </div>
            ${categoriesSelect('categoryInput', 'Category', 'A category must be provided', categories, existing.category)}
            <div class="py-2">
                <label for="notesInput">Notes</label>
                <textarea class="form-control" id="notesInput" placeholder="(optional)" style="height: 100px">${existing.notes || ""}</textarea>
            </div>
        </form>
    `;
}

function createImportForm(id, accounts) {
    return `
        <form id="${id}" class="needs-validation" novalidate>
            <div class="py-2">
                <label for="importTypeInput">Import Type</label>
                <select class="form-select" id="importTypeInput" aria-label="Import Type">
                    <option value="camt053" selected>CAMT.053</option>
                </select>
            </div>
            <div class="py-2">
                <label for="uploadTypeInput">Upload Type</label>
                <select class="form-select" id="uploadTypeInput" aria-label="Upload Type">
                    <option value="archive" selected>Archive</option>
                    <option value="xml">XML</option>
                </select>
            </div>
            ${accountsSelect('forAccountInput', 'For Account', 'A target account must be provided', accounts, false)}
            <div class="input-group">
                <label class="input-group-text" for="fileInput">File</label>
                <input type="file" class="form-control" id="fileInput" placeholder="File">
                <div class="invalid-feedback">A file must be provided</div>
            </div>
        </form>
    `;
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

function renderImportResult(result) {
    return `
        <div class="py-2">
            <h5>Provided</h5>
            <ul class="list-group">
                ${importResultItem('Documents', 'Documents provided in the original import file', result.provided.documents, 'text-bg-secondary')}
                ${importResultItem('Statements', 'Statements provided in all documents', result.provided.statements, 'text-bg-secondary')}
                ${importResultItem('Entries', 'Entries provided in all statements and documents', result.provided.entries, 'text-bg-primary')}
            </ul>
        </div>
        <div class="py-2">
            <h5>Imported</h5>
            <ul class="list-group">
                ${importResultItem('Successful', 'Successfully imported transactions', result.imported.successful, 'text-bg-success')}
                ${importResultItem('Existing', 'Existing transactions that were not imported', result.imported.existing, 'text-bg-warning')}
            </ul>
        </div>
    `;
}

function importResultItem(title, subtitle, count, countClass) {
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

function validateAndLoadTransactionFormData(id) {
    let form = $(`#${id}`);
    let externalIdInput = form.find('#externalIdInput');
    let amountInput = form.find('#amountInput');
    let currencyInput = form.find('#currencyInput');
    let typeInput = form.find('#typeInput');
    let fromInput = form.find('#fromInput');
    let toInput = form.find('#toInput');
    let dateInput = form.find('#dateInput');
    let categoryInput = form.find('#categoryInput');
    let notesInput = form.find('#notesInput');

    var inputs = {
        'amount': amountInput,
        'currency': currencyInput,
        'type': typeInput,
        'from': fromInput,
        'to': toInput,
        'date': dateInput,
        'category': categoryInput,
        'notes': notesInput,
    };

    if (externalIdInput.length) {
        inputs['external_id'] = externalIdInput;
    }

    for (const [key, input] of Object.entries(inputs)) {
        let inputValue = (input.val() || '').trim();

        if (key !== 'notes' && (!inputValue || inputValue.length === 0)) {
            input.addClass('is-invalid');
        } else {
            input.removeClass('is-invalid');
        }
    }

    if (fromInput.val() && toInput.val() && fromInput.val() === toInput.val()) {
        fromInput.addClass('is-invalid');
        toInput.addClass('is-invalid');
    }

    if (Object.values(inputs).every((input) => !input.hasClass('is-invalid'))) {
        let request = Object.entries(inputs).reduce(function (collected, [key, input]) {
            let inputValue = input.val().trim();

            switch (key) {
                case 'amount': collected[key] = parseFloat(parseFloat(inputValue).toFixed(2)); break;
                case 'notes': collected[key] = inputValue.length === 0 ? null : inputValue; break;
                case 'from': collected[key] = parseInt(inputValue); break;
                case 'to': collected[key] = inputValue === 'external' ? null : parseInt(inputValue); break;
                default: collected[key] = inputValue;
            }

            return collected;
        }, {});

        return request;
    } else {
        return null;
    }
}

function validateAndLoadImportFormData(id) {
    let form = $(`#${id}`);
    let importTypeInput = form.find('#importTypeInput');
    let uploadTypeInput = form.find('#uploadTypeInput');
    let forAccountInput = form.find('#forAccountInput');
    let fileInput = form.find('#fileInput');

    var inputs = {
        'import_type': importTypeInput,
        'upload_type': uploadTypeInput,
        'for_account': forAccountInput,
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
