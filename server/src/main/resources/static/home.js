$(document).ready(function () {
    login(function () {
        $('#logout').on('click', function () { logout(); });
        initHome();
    });
});

function initHome() {
    let period = selectedPeriod();
    let parsed = parsePeriod(period);

    $('#overview-title').text(`Overview - ${parsed.toLocaleString('default', { month: 'long' })} ${parsed.getFullYear()}`);

    $.when(getAccounts(), getTransactions(period), getForecasts(period), getSummary(previousPeriod(period))).then(function (accountData, transactionData, forecastData, summaryData) {
        let accounts = accountData[0].reduce(function (collected, current) {
            collected[current.id] = current;
            return collected;
        }, {});

        let transactions = transactionData[0].map(function (t) {
            t['entry_type'] = 'transaction';
            return t;
        });

        let forecasts = forecastData[0].map(function (f) {
            f['entry_type'] = 'forecast';
            f['from'] = f.account;
            return f;
        });

        let summary = summaryData[0];

        let entries = transactions.concat(forecasts);

        let dailyTransactionsChart = new ApexCharts(document.querySelector('#daily-transactions-chart'), {
            chart: { type: 'area' },
            title: { text: 'Daily Transactions' },
            series: [],
            noData: { text: 'Loading...' },
            zoom: {
                type: 'x',
                enabled: true,
                autoScaleYaxis: true
            },
            toolbar: {
                autoSelected: 'zoom'
            },
            dataLabels: {
                enabled: false
            },
            markers: {
                size: 0,
            },
            fill: {
                type: 'gradient',
                gradient: {
                    shadeIntensity: 1,
                    inverseColors: false,
                    opacityFrom: 0.5,
                    opacityTo: 0,
                    stops: [0, 90, 100]
                },
            },
            yaxis: {
                labels: {
                    formatter: (val) => DataTable.render.number('.', ',', 0).display(val),
                },
                title: {
                    text: 'Total Amount'
                },
            },
            xaxis: {
                type: 'datetime',
            },
            tooltip: {
                shared: false,
                y: {
                    formatter: (val) => DataTable.render.number('.', ',', 0).display(val),
                }
            },
        });

        let categoryBreakdownChart = new ApexCharts(document.querySelector('#category-breakdown-chart'), {
            chart: { type: 'pie' },
            title: { text: 'Expenses by Category' },
            series: [],
            noData: { text: 'Loading...' },
            tooltip: {
                shared: false,
                y: {
                    formatter: (val) => DataTable.render.number('.', ',', 0).display(val),
                }
            },
        });

        dailyTransactionsChart.render();
        categoryBreakdownChart.render();

        let table = $('#entries').DataTable({
            dom: '<"container-fluid"<"row"<"col"B><"col"f>>>rt<"container-fluid"<"row align-items-center"<"col"i><"col"p>>>',
            data: entries,
            pageLength: -1,
            language: {
                searchPanes: {
                    collapse: {
                        0: '<i class="fab bi bi-funnel"></i>',
                        _: `
                            <i class="fab bi bi-funnel-fill"></i>
                            <span class="position-absolute top-0 start-100 translate-middle p-1 bg-danger border border-light rounded-circle">
                            </span>
                        `
                    }
                }
            },
            buttons: [
                {
                    extend: 'searchPanes',
                    className: 'btn-primary btn-sm',
                    titleAttr: 'Filter Entries',
                    config: {
                        columns: [0, 1, 3, 4],
                        cascadePanes: true,
                    }
                }
            ],
            columns: [
                {
                    data: null,
                    render: function (data, type, row, meta) {
                        if (data.from in accounts) {
                            return accounts[data.from].name;
                        } else {
                            return data.account;
                        }
                    },
                },
                {
                    data: null,
                    render: function (data, type, row, meta) {
                        if (data.to in accounts) {
                            return accounts[data.to].name;
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
                {
                    data: null,
                    render: function (data, type, row, meta) {
                        if (type === 'sort' || data.entry_type === 'forecast') {
                            return data.category;
                        } else {
                            return `
                                <button type="button" class="btn btn-link category-info" style="--bs-btn-padding-y: 0; --bs-btn-padding-x: 0;">
                                <span class="d-inline-block text-truncate" style="max-width: 8em;">
                                    ${data.category}
                                    </span>
                                </button>
                            `;
                        }
                    }
                },
            ],
            columnDefs: [
                {
                    type: 'num',
                    className: 'dt-body-right fw-bold',
                    targets: 2,
                },
                {
                    defaultContent: '-',
                    targets: [1, 3],
                },
            ],
            order: [[3, 'desc']],
            rowGroup: {
                dataSrc: 'date',
                startRender: function (rows, group) {
                    let groupTitle = group === 'No group' ? 'Recurring' : group;
                    let amounts = calculateEntryAmounts(rows.data());
                    return `
                        <div class="row">
                            <div class="col text-start">${groupTitle} (${rows.count()})</div>
                            <div class="col text-end">Total: ${amounts}</div>
                        </div>
                    `;
                }
            },
            createdRow: function (row, data, dataIndex) {
                if (data.entry_type === 'forecast') {
                    $(row).addClass('table-primary');
                }
            },
            drawCallback: debounce(200, function (settings) {
                let data = Object.values(this.api().rows({ page: 'current' }).data());

                let currencies = data.reduce(function (collected, current) {
                    if (current.entry_type === 'transaction') {
                        let existing = collected[current.currency] || 0;
                        collected[current.currency] = existing + 1;
                        return collected;
                    } else {
                        return collected;
                    }
                }, {});

                let mostPopularCurrency = Object
                    .entries(currencies)
                    .reduce((max, current) => max[1] > current[1] ? max : current, ['EUR', 0])[0];

                let summaryForCurrency = summary.currencies[mostPopularCurrency] || { 'income': 0, 'expenses': 0 };

                let amounts = data.reduce(function (collected, current) {
                    if (current.entry_type === 'transaction' && current.currency === mostPopularCurrency) {
                        if (current.type === 'debit') {
                            collected['expenses'] = (collected['expenses'] || 0) + current.amount;
                            return collected;
                        } else {
                            collected['income'] = (collected['income'] || 0) + current.amount;
                            return collected;
                        }
                    } else if (current.entry_type === 'forecast' && current.currency === mostPopularCurrency) {
                        collected['forecast'] = (collected['forecast'] || 0) + entryAmount(current.type, current.amount);
                        return collected;
                    } else {
                        return collected;
                    }
                }, { 'income': 0, 'expenses': 0, 'forecast': 0, 'remaining': 0 });

                amounts['previous'] = summaryForCurrency['income'] - summaryForCurrency['expenses'];
                amounts['remaining'] = amounts['previous'] + amounts['income'] - amounts['expenses'] + amounts['forecast'];

                function renderAmount(amount) {
                    return `${DataTable.render.number('.', ',', 2).display(amount || 0)} ${mostPopularCurrency}`;
                }

                let remainingAmountPrevious = $('#remaining-amount-previous');
                let incomeAmountCurrent = $('#income-amount-current');
                let expensesAmountCurrent = $('#expenses-amount-current');
                let forecastAmountCurrent = $('#forecast-amount-current');
                let remainingAmountCurrent = $('#remaining-amount-current');

                remainingAmountPrevious.text(renderAmount(amounts['previous']));
                incomeAmountCurrent.text(renderAmount(amounts['income']));
                expensesAmountCurrent.text(renderAmount(amounts['expenses']));
                forecastAmountCurrent.text(renderAmount(amounts['forecast']));
                remainingAmountCurrent.text(renderAmount(amounts['remaining']));

                if (amounts['previous'] > 0) {
                    remainingAmountPrevious.removeClass('text-bg-dark');
                    remainingAmountPrevious.addClass('text-bg-success');
                } else {
                    remainingAmountPrevious.removeClass('text-bg-success');
                    remainingAmountPrevious.addClass('text-bg-dark');
                }

                if (amounts['forecast'] > 0) {
                    forecastAmountCurrent.removeClass('text-bg-dark');
                    forecastAmountCurrent.addClass('text-bg-success');
                } else {
                    forecastAmountCurrent.removeClass('text-bg-success');
                    forecastAmountCurrent.addClass('text-bg-dark');
                }

                if (amounts['remaining'] > 0) {
                    remainingAmountCurrent.removeClass('text-bg-dark');
                    remainingAmountCurrent.addClass('text-bg-success');
                } else {
                    remainingAmountCurrent.removeClass('text-bg-success');
                    remainingAmountCurrent.addClass('text-bg-dark');
                }

                let transactionAmountsPerDay = data.reduce(function (collected, current) {
                    if (current.entry_type === 'transaction' && current.currency === mostPopularCurrency) {
                        let existing = collected[current.date] || 0.0;
                        collected[current.date] = existing + entryAmount(current.type, current.amount);
                        return collected;
                    } else {
                        return collected;
                    }
                }, {});

                let transactionDays = [...Object.keys(transactionAmountsPerDay)].sort();
                if (transactionDays.length === 0) {
                    dailyTransactionsChart.updateOptions({
                        title: { text: 'Daily Transactions' },
                        series: [],
                        xaxis: { type: 'datetime', categories: [] },
                        noData: { text: 'No Data' },
                    });
                } else {
                    dailyTransactionsChart.updateOptions({
                        title: { text: `Daily Transactions (${mostPopularCurrency})` },
                        series: [{
                            name: 'Total',
                            data: transactionDays.map((day) => Math.round(transactionAmountsPerDay[day])),
                        }],
                        xaxis: {
                            type: 'datetime',
                            categories: transactionDays,
                        },
                    });
                }

                let expensesByCategory = data.reduce(function (collected, current) {
                    if (current.entry_type === 'transaction' && current.currency === mostPopularCurrency) {
                        let existing = collected[current.category] || 0.0;
                        collected[current.category] = existing + entryAmount(current.type, current.amount);
                        return collected;
                    } else {
                        return collected;
                    }
                }, {});

                Object.keys(expensesByCategory).forEach(function (category, _) {
                    if (expensesByCategory[category] >= 0) {
                        delete expensesByCategory[category];
                    } else {
                        expensesByCategory[category] = Math.abs(expensesByCategory[category]);
                    }
                });

                let expenseCategories = [...Object.keys(expensesByCategory)].sort();
                if (expenseCategories.length === 0) {
                    categoryBreakdownChart.updateOptions({
                        title: { text: 'Expenses by Category' },
                        series: [],
                        labels: [],
                        noData: { text: 'No Data' },
                    });
                } else {
                    categoryBreakdownChart.updateOptions({
                        title: { text: `Expenses by Category (${mostPopularCurrency})` },
                        series: expenseCategories.map((category) => Math.round(expensesByCategory[category])),
                        labels: expenseCategories,
                    });
                }
            }),
        });

        $('#entries tbody').on('click', '.category-info', function () {
            let self = $(this);
            let row = table.row(self.parents('tr'));
            let data = row.data();

            showInfoModal(`Notes for entry [${data['external_id']}]`, `<p class="font-monospace">${data.notes.split(' ').join('\n')}</p>`);
        });

        initPeriodControl((control) => $('.dt-buttons').after(control));
    });
};

function debounce(delay, fn) {
    var timer = null;
    return function () {
        let context = this;
        let args = arguments;
        clearTimeout(timer);
        timer = setTimeout(function () { fn.apply(context, args); }, delay);
    };
}

function entryAmount(type, amount) {
    return type === 'debit' ? (amount * -1) : amount;
}

function calculateEntryAmounts(entries) {
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

function getTransactions(forPeriod) {
    return $.ajax({
        type: 'GET',
        url: `/transactions?period=${forPeriod}`,
        contentType: "application/json",
        error: function (xhr, textStatus, errorThrown) {
            showErrorToast(`Failed to load transactions for period [${forPeriod}]: [${errorThrown}]`);
        },
        beforeSend: function (xhr) {
            xhr.setRequestHeader("Authorization", `Bearer ${get_token()}`);
        },
    });
}

function getForecasts(forPeriod) {
    if (!isBeforeCurrentPeriod(forPeriod)) {
        let url = isCurrentPeriod(forPeriod)
            ? `/forecasts?period=${forPeriod}&disregard_after=${currentDay()}`
            : `/forecasts?period=${forPeriod}`;

        return $.ajax({
            type: 'GET',
            url: url,
            contentType: "application/json",
            error: function (xhr, textStatus, errorThrown) {
                showErrorToast(`Failed to load forecasts for period [${forPeriod}]: [${errorThrown}]`);
            },
            beforeSend: function (xhr) {
                xhr.setRequestHeader("Authorization", `Bearer ${get_token()}`);
            },
        });
    } else {
        return $.Deferred().resolve([[]]);
    }
}

function getSummary(forPeriod) {
    return $.ajax({
        type: 'GET',
        url: `/reports/summary?period=${forPeriod}`,
        contentType: "application/json",
        error: function (xhr, textStatus, errorThrown) {
            showErrorToast(`Failed to load summary for period [${forPeriod}]: [${errorThrown}]`);
        },
        beforeSend: function (xhr) {
            xhr.setRequestHeader("Authorization", `Bearer ${get_token()}`);
        },
    });
}
