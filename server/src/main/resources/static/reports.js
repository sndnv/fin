$(document).ready(function () {
    login(function () {
        $('#logout').on('click', function () { logout(); });
        initReports();
    });
});

function initReports() {
    $.when(getAccounts()).then(
        function (accountData) {
            if (accountData && accountData.length > 0) {
                let accounts = accountData.reduce(function (collected, current) {
                    collected[current.id] = current;
                    return collected;
                }, {});

                let selectedType = selectedParameter('type', 'by-month');
                let selectedStart = selectedParameter('start', startOfCurrentYear());
                let selectedEnd = selectedParameter('end', endOfCurrentYear());
                let selectedAccount = selectedParameter('account', Math.min(...Object.keys(accounts)));

                $.when(getTransactionBreakdown(selectedType, selectedStart, selectedEnd, selectedAccount)).then(
                    function (breakdownData) {
                        initReportsWithData(accounts, breakdownData, selectedType, selectedStart, selectedEnd, selectedAccount);
                    }
                );
            } else {
                $('#no-data').removeAttr('hidden');
            }
        }
    );
};

function initReportsWithData(accounts, breakdown, selectedType, selectedStart, selectedEnd, selectedAccount) {
    let currencies = Object.keys(breakdown.currencies);
    currencies.sort();

    let currencyParam = selectedParameter('currency', currencies[0]);
    let selectedCurrency = breakdown.currencies[currencyParam] ? currencyParam : currencies[0];

    initControls(accounts, currencies, selectedType, selectedStart, selectedEnd, selectedAccount, selectedCurrency);
    initCharts(breakdown, selectedType, selectedCurrency);
}

function initControls(accounts, currencies, selectedType, selectedStart, selectedEnd, selectedAccount, selectedCurrency) {
    let accountIDs = Object.keys(accounts);
    accountIDs.sort();

    let accountOptions = accountIDs.map(function (account) {
        return `<option value="${account}" ${account == selectedAccount ? 'selected' : ''}>${accounts[account].name}</option>`;
    }).join('');

    let accountSelect = `
        <select class="form-select form-select-sm" id="account" aria-label="Account">
            ${accountOptions}
        </select>
    `;

    let breakdownSelect = `
        <select class="form-select form-select-sm" id="breakdownType" aria-label="Breakdown">
            <option value="by-year" ${selectedType == 'by-year' ? 'selected' : ''}>By Year</option>
            <option value="by-month" ${selectedType == 'by-month' ? 'selected' : ''}>By Month</option>
            <option value="by-week" ${selectedType == 'by-week' ? 'selected' : ''}>By Week</option>
            <option value="by-day" ${selectedType == 'by-day' ? 'selected' : ''}>By Day</option>
        </select>
    `;

    let rangePicker = `
        <div class="input-group input-group-sm input-daterange" id="range">
            <input type="text" class="form-control" value="${selectedStart}" id="start">
            <span class="input-group-text">to</span>
            <input type="text" class="form-control" value="${selectedEnd}" id="end">
        </div>
    `;

    let quickRangePicker = `
        <div class="dropdown" id="quick-range" title="Select predefined range">
            <button class="btn btn-outline-secondary btn-sm dropdown-toggle" type="button" data-bs-toggle="dropdown" aria-expanded="false">
                <i class="bi bi-calendar3"></i>
            </button>
            <ul class="dropdown-menu">
                <li><a class="dropdown-item" href="#" onClick="goToReportWithParams(calculateRange('default'))">Current year (default)</a></li>
                <li><hr class="dropdown-divider"></li>
                <li><a class="dropdown-item" href="#" onClick="goToReportWithParams(calculateRange('last-7-days'))">Last 7 days</a></li>
                <li><a class="dropdown-item" href="#" onClick="goToReportWithParams(calculateRange('last-30-days'))">Last 30 days</a></li>
                <li><a class="dropdown-item" href="#" onClick="goToReportWithParams(calculateRange('last-90-days'))">Last 90 days</a></li>
                <li><a class="dropdown-item" href="#" onClick="goToReportWithParams(calculateRange('last-6-months'))">Last 6 months</a></li>
                <li><a class="dropdown-item" href="#" onClick="goToReportWithParams(calculateRange('last-1-year'))">Last 1 year</a></li>
                <li><a class="dropdown-item" href="#" onClick="goToReportWithParams(calculateRange('last-2-years'))">Last 2 years</a></li>
                <li><a class="dropdown-item" href="#" onClick="goToReportWithParams(calculateRange('last-5-years'))">Last 5 years</a></li>
            </ul>
        </div>
    `;

    let currencyOptions = currencies.map(function (currency) {
        return `<option value="${currency}" ${currency == selectedCurrency ? 'selected' : ''}>${currency}</option>`;
    }).join('');

    let currencySelect = `
        <select class="form-select form-select-sm" id="currency" aria-label="Currency" ${currencies && currencies.length > 0 ? '' : 'disabled'}>
            ${currencyOptions}
        </select>
    `;

    let group = `
        <ul class="list-group list-group-horizontal p-2">
            <li class="list-group-item d-flex justify-content-between align-items-start flex-fill">
                <div class="ms-2 me-auto">
                  <div class="fw-bold">Account</div>
                  ${accountSelect}
                </div>
            </li>
            <li class="list-group-item d-flex justify-content-between align-items-start flex-fill">
                <div class="ms-2 me-auto">
                  <div class="fw-bold">Breakdown</div>
                  ${breakdownSelect}
                </div>
            </li>
            <li class="list-group-item d-flex justify-content-between align-items-start flex-fill">
                <div class="ms-2 me-auto">
                  <div class="fw-bold">Range</div>
                  <div class="d-flex">
                    ${rangePicker}&nbsp;or&nbsp;${quickRangePicker}
                  </div>
                </div>
            </li>
            <li class="list-group-item d-flex justify-content-between align-items-start flex-fill">
                <div class="ms-2 me-auto">
                  <div class="fw-bold">Currency</div>
                  ${currencySelect}
                </div>
            </li>
        </ul>
    `;

    $('#report-controls').empty().append(group);

    $('#account').on('change', function() {
        goToReport('account', this.value);
    });

    $('#breakdownType').on('change', function() {
        goToReport('type', this.value);
    });

    $('#currency').on('change', function() {
        goToReport('currency', this.value);
    });

    $('#range').datepicker({
        format: "yyyy-mm-dd",
        weekStart: 1,
        inputs: $('.form-control'),
        todayBtn: true,
        todayHighlight: true,
    }).datepicker('setDate', parsePeriod(selectedPeriod())).on('changeDate', function (e) {
        let target = e.target.getAttribute('id');
        let selected = renderDate(e.date);
        goToReport(target, selected);
    });
}

function initCharts(breakdown, selectedType, selectedCurrency) {
    let periodBreakdown = (breakdown.currencies[selectedCurrency] || {}).periods;
    if (periodBreakdown) {
        $('#graphs').removeAttr('hidden');

        let totalIncome = Object.values(periodBreakdown).reduce(function (total, current) {
            return total + current.income.value;
        }, 0);

        let totalExpenses = Object.values(periodBreakdown).reduce(function (total, current) {
            return total + current.expenses.value;
        }, 0);

        let totalRemaining = totalIncome - totalExpenses

        let periods = Object.keys(periodBreakdown);
        periods.sort();

        let categories = [...new Set(
            periods.reduce(function (collected, period) {
                return collected.concat(Object.keys(periodBreakdown[period].categories));
            }, [])
        )];
        categories.sort();

        let incomePerPeriod = periods.map(function (period) { return {x: period, y: periodBreakdown[period].income.value};});
        let expensesPerPeriod = periods.map(function (period) { return {x: period, y: periodBreakdown[period].expenses.value};});
        let remainingPerPeriod = periods.map(function (period) { return {x: period, y: periodBreakdown[period].income.value - periodBreakdown[period].expenses.value};});
        let incomeTransactionsPerPeriod = periods.map(function (period) { return {x: period, y: periodBreakdown[period].income.transactions};});
        let expenseTransactionsPerPeriod = periods.map(function (period) { return {x: period, y: periodBreakdown[period].expenses.transactions};});

        let groupedExpensesPerCategoryAndPeriod = periods.reduce(function (collected, period) {
            categories.forEach(function (category) {
                let existing = collected[category] || [];
                let categoryData = periodBreakdown[period].categories[category];
                existing.push({x: period, y: categoryData ? categoryData.expenses.value : 0});
                collected[category] = existing;
            });

            return collected;
        }, {});

        let expensesPerCategoryAndPeriod = categories.map(function (category) { return {name: category, data: groupedExpensesPerCategoryAndPeriod[category]} });

        let incomeChartOptions = totalChartOptions('Income', totalIncome, incomePerPeriod, selectedCurrency, selectedType, '#198754')
        new ApexCharts(document.querySelector('#income-total'), incomeChartOptions).render();

        let expensesChartOptions = totalChartOptions('Expenses', totalExpenses, expensesPerPeriod, selectedCurrency, selectedType, '#210029')
        new ApexCharts(document.querySelector('#expenses-total'), expensesChartOptions).render();

        let remainingChartOptions = totalChartOptions('Remaining', totalRemaining, remainingPerPeriod, selectedCurrency, selectedType, '#fd7e14')
        new ApexCharts(document.querySelector('#remaining-balance-total'), remainingChartOptions).render();

        let incomeExpensesPerPeriodOptions = periodChartOptions('income-expenses-per-period','Income and Expenses per Period', incomePerPeriod, expensesPerPeriod, remainingPerPeriod, selectedCurrency, selectedType);
        new ApexCharts(document.querySelector("#income-expenses-per-period"), incomeExpensesPerPeriodOptions).render();

        let expensesPerCategoryOptions = categoryChartOptions('expenses-per-category', 'Expenses per Period and Category', expensesPerCategoryAndPeriod, selectedCurrency, selectedType);
        new ApexCharts(document.querySelector("#expenses-per-category"), expensesPerCategoryOptions).render();

        let transactionsPerPeriodOptions = transactionChartOptions('transactions-per-period', 'Transactions per Period', incomeTransactionsPerPeriod, expenseTransactionsPerPeriod, selectedType);
        new ApexCharts(document.querySelector("#transactions-per-period"), transactionsPerPeriodOptions).render();
    } else {
        $('#no-data').removeAttr('hidden');
    }
}

function totalChartOptions(name, total, data, selectedCurrency, selectedType, color) {
    return {
        series: [{
            name: name,
            data: data,
        }],
        chart: {
            id: `overview-${name}`,
            group: 'overview',
            type: 'area',
            height: 160,
            sparkline: { enabled: true },
        },
        stroke: {
            curve: 'smooth'
        },
        colors: [color],
        title: {
            text: `${total.toFixed(2)} ${selectedCurrency}`,
            style: {
                fontSize: '24px',
                color: color,
            },
        },
        subtitle: {
            text: `Total ${name}`,
        },
        tooltip: {
            x: {
                format: dateFormatFromType(selectedType),
            },
            y: {
                formatter: function(value, { series, seriesIndex, dataPointIndex, w }) {
                    return value != 0 ? `${value.toFixed(2)} ${selectedCurrency}` : 'none';
                },
            },
        },
        xaxis: {
            type: 'datetime',
        },
    };
}

function periodChartOptions(id, name, income, expenses, remaining, selectedCurrency, selectedType) {
    return {
        title: { text: name, },
        series: [
            { name: 'Income', data: income, group: 'left', },
            { name: 'Expenses', data: expenses, group: 'right', },
            { name: 'Remaining', data: remaining, group: 'right', },
        ],
        chart: {
            id: id,
            type: 'bar',
            height: 350,
            stacked: true,
            zoom: {
                type: 'x',
                enabled: true,
                autoScaleYaxis: true
            },
        },
        stroke: {
            show: true,
            width: 2,
            colors: ['transparent'],
        },
        yaxis: {
            title: { text: selectedCurrency, },
            labels: {
                formatter: function (value) { return value.toFixed(0); },
                minWidth: 40,
            },
        },
        dataLabels: {
            formatter: function(value, options) {
                return value.toFixed(0);
            },
        },
        tooltip: {
            x: {
                format: dateFormatFromType(selectedType),
            },
            y: {
                formatter: function(value, { series, seriesIndex, dataPointIndex, w }) {
                    return value != 0 ? `${value.toFixed(0)} ${selectedCurrency}` : 'none';
                },
            },
        },
        xaxis: {
            type: 'datetime',
            labels: {
                format: dateFormatFromType(selectedType),
            },
        },
        colors: INCOME_EXPENSE_COLORS,
    };
}

function categoryChartOptions(id, name, expenses, selectedCurrency, selectedType) {
    return {
        series: expenses,
        chart: {
            id: id,
            type: 'bar',
            height: 350,
            stacked: true,
            zoom: {
                type: 'x',
                enabled: true,
                autoScaleYaxis: true,
            },
        },
        plotOptions: {
            bar: {
                dataLabels: {
                    total: { enabled: true, },
                },
            },
        },
        stroke: {
            show: true,
            width: 2,
            colors: ['transparent'],
        },
        title: { text: name, },
        xaxis: {
            type: 'datetime',
            labels: {
                format: dateFormatFromType(selectedType),
            },
        },
        yaxis: {
            title: { text: selectedCurrency, },
            labels: {
                formatter: function (value) { return value.toFixed(0); },
                minWidth: 40,
            },
        },
        dataLabels: {
            formatter: function(value, options) {
                return value.toFixed(0);
            },
        },
        tooltip: {
            x: {
                format: dateFormatFromType(selectedType),
            },
            y: {
                formatter: function(value, { series, seriesIndex, dataPointIndex, w }) {
                    return value != 0 ? `${value.toFixed(0)} ${selectedCurrency}` : 'none';
                },
            },
        },
        colors: CATEGORY_COLORS,
    };
}

function transactionChartOptions(id, name, incomeTransactions, expenseTransactions, selectedType) {
    return {
        series: [{
            name: "Income",
            data: incomeTransactions
        }, {
            name: "Expenses",
            data: expenseTransactions
        }],
        chart: {
            id: id,
            type: 'line',
            height: 350,
            zoom: {
                type: 'x',
                enabled: true,
                autoScaleYaxis: true
            },
        },
        colors: INCOME_EXPENSE_COLORS,
        dataLabels: {
            enabled: true,
        },
        stroke: {
            curve: 'smooth',
        },
        title: {
            text: name,
        },
        xaxis: {
            type: 'datetime',
            labels: {
                format: dateFormatFromType(selectedType),
            },
        },
        yaxis: {
            title: { text: 'Transactions', },
            labels: {
                minWidth: 40,
            },
            min: 0,
        },
        tooltip: {
            x: {
                format: dateFormatFromType(selectedType),
            },
        },
    };
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

function getTransactionBreakdown(type, start, end, account) {
    return $.ajax({
        type: 'GET',
        url: `/reports/breakdown?type=${type}&start=${start}&end=${end}&account=${account}`,
        contentType: "application/json",
        error: function (xhr, textStatus, errorThrown) {
            showErrorToast(`Failed to load transaction breakdown: [${errorThrown}]`);
        },
        beforeSend: function (xhr) {
            xhr.setRequestHeader("Authorization", `Bearer ${get_token()}`);
        },
    });
}

function selectedParameter(param, defaultParam) {
    let params = new URLSearchParams(document.location.search);

    if (params.has(param)) {
        try {
            return params.get(param);
        } catch (e) {
            return defaultParam;
        }
    } else {
        return defaultParam;
    }
}

function startOfCurrentYear() {
    let start = new Date(new Date().getFullYear(), 0, 1);
    return renderDate(start);
}

function endOfCurrentYear() {
    let end = new Date(new Date().getFullYear(), 11, 31);
    return renderDate(end);
}

function renderDate(date) {
    let year = date.getFullYear().toString();
    let month = String(date.getMonth() + 1).padStart(2, '0');
    let day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
}

function goToReport(param, paramValue) {
    goToReportWithParams([[param, paramValue]])
}

function goToReportWithParams(paramsList) {
    let params = new URLSearchParams(document.location.search);
    paramsList.forEach(function(p) { params.set(p[0], p[1]) });
    window.location.search = params;
}

function dateFormatFromType(type) {
    switch (type) {
        case 'by-year': return 'yyyy';
        case 'by-month': return 'MMM \'yy';
        default: return 'dd MMM \'yy';
    }
}

function calculateRange(type) {
    var start = luxon.DateTime.now();
    let end = luxon.DateTime.now()

    switch (type) {
        case 'last-7-days': start = start.minus({days: 7}); break;
        case 'last-30-days': start = start.minus({days: 30}); break;
        case 'last-90-days': start = start.minus({days: 90}); break;
        case 'last-6-months': start = start.minus({months: 6}); break;
        case 'last-1-year': start = start.minus({years: 1}); break;
        case 'last-2-years': start = start.minus({years: 2}); break;
        case 'last-5-years': start = start.minus({years: 5}); break;
        default: return [['start', startOfCurrentYear()], ['end', endOfCurrentYear()]];
    }

    return [['start', renderDate(start.toJSDate())], ['end', renderDate(end.toJSDate())]];
}

const INCOME_EXPENSE_COLORS = ['#198754', '#210029', '#FD7E14']; // [<income>, <expenses>, <remaining>]
const CATEGORY_COLORS = ['#008FFB', '#00E396', '#FEB019', '#FF4560', '#775DD0', '#4CAF50', '#546E7A', '#1B998B', '#8D5B4C', '#C5D86D', '#C7f464'];
