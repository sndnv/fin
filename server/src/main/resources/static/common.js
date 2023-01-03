// toasts
function showInfoToast(text) {
    Toastify({text: text, duration: 3000}).showToast();
}

function showSuccessToast(text) {
    Toastify({
        text: text,
        style: {
            background: '#198754',
        },
        duration: 3000,
    }).showToast();
}

function showErrorToast(text) {
    Toastify({
        text: text,
        style: {
            background: '#fd7e14',
        },
        duration: 3000,
    }).showToast();
}

// modals
function showInfoModal(title, content) {
    let modal = $('#default-modal');
    let modalContent = modal.find('.modal-content');

    modalContent.empty().append(
        `
            <div class="modal-header">
              <h5 class="modal-title">${title}</h5>
              <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
            </div>
        `
    ).append(`<div class="modal-body">${content}</div>`)

    new bootstrap.Modal(modal).show();
}

function showConfirmationModal(title, confirmationHandler) {
    let modal = $('#default-modal');
    let modalContent = modal.find('.modal-content');

    modalContent.empty().append(
        `
            <div class="modal-header">
              <h5 class="modal-title">${title}</h5>
              <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
            </div>
        `
    ).append(
        `
            <div class="modal-footer">
              <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Close</button>
              <button type="button" class="confirmation-button btn btn-danger">Confirm</button>
            </div>
        `
    );

    let confirmationButton = modalContent.find('.confirmation-button');
    confirmationButton.click(function (e) {
        confirmationButton.prop('disabled', true);
        confirmationButton.empty().append('<span role="status" aria-hidden="true" class="spinner-border spinner-grow-sm"></span>');
        let reEnableButton = confirmationHandler();
        if(reEnableButton) {
            confirmationButton.empty().append(confirmationText);
            confirmationButton.prop('disabled', false);
        }
    });

    new bootstrap.Modal(modal).show();
}

function showFormModal(title, content, confirmationText, confirmationHandler) {
    let modal = $('#default-modal');
    let modalContent = modal.find('.modal-content');

    modalContent.empty().append(
        `
            <div class="modal-header">
              <h5 class="modal-title">${title}</h5>
              <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
            </div>
        `
    ).append(
        `<div class="modal-body">${content}</div>`
    ).append(
        `
            <div class="modal-footer">
              <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Close</button>
              <button type="button" class="confirmation-button btn btn-success">${confirmationText}</button>
            </div>
        `
    );

    let confirmationButton = modalContent.find('.confirmation-button');
    confirmationButton.click(function (e) {
        confirmationButton.prop('disabled', true);
        confirmationButton.empty().append('<span role="status" aria-hidden="true" class="spinner-border spinner-grow-sm"></span>');
        let reEnableButton = confirmationHandler();
        if(reEnableButton) {
            confirmationButton.empty().append(confirmationText);
            confirmationButton.prop('disabled', false);
        }
    });

    new bootstrap.Modal(modal, {backdrop: 'static'}).show();
}

function hideModal() {
    $('#default-modal').modal('hide');
}

// periods
function initPeriodControl(attach) {
    let control = `
        <div id="period-controls" class="btn-group btn-group-sm" role="group" aria-label="Transaction Period Controls">
            <button id="period-previous" type="button" class="btn btn-outline-primary" title="Go to Previous Period">
                <i class="bi bi-chevron-left"></i>
            </button>
            <button id="period-current" type="button" class="btn btn-outline-primary ${selectedPeriod() === currentPeriod() ? 'active' : ''}" title="Go to Current Period">
                <i class="bi bi-calendar-event"></i>
            </button>
            <button id="period-selected" type="button" class="btn btn-outline-primary active" title="Select Period">
                ${selectedPeriod()}
            </button>
            <button id="period-next" type="button" class="btn btn-outline-primary" title="Go to Next Period">
                <i class="bi bi-chevron-right"></i>
            </button>
        </div>
    `;

    attach(control);

    $('#period-previous').click(function (e) {
        let previous = previousPeriod(selectedPeriod());
        goToPeriod(previous);
    });

    $('#period-current').click(function (e) {
        goToPeriod(currentPeriod());
    });

    $("#period-selected").datepicker({
        format: "yyyy-mm-dd",
        minViewMode: 1,
        autoclose: true,
    }).datepicker('setDate', parsePeriod(selectedPeriod())).on('changeDate', function (e) {
        let selected = renderPeriod(e.date);
        goToPeriod(selected);
    });

    $('#period-next').click(function (e) {
        let next = nextPeriod(selectedPeriod());
        goToPeriod(next);
    });
}

function goToPeriod(period) {
    let params = new URLSearchParams(document.location.search);
    params.set('period', period);
    window.location.search = params;
}

function selectedPeriod() {
    let params = new URLSearchParams(document.location.search);

    if (params.has('period')) {
        try {
            return renderPeriod(parsePeriod(params.get('period')));
        } catch (e) {
            return currentPeriod();
        }
    } else {
        return currentPeriod();
    }
}

function currentPeriod() {
    let now = new Date();
    now.setDate(1);
    return renderPeriod(now);
}

function previousPeriod(fromPeriod) {
    let provided = parsePeriod(fromPeriod);
    provided.setMonth(provided.getMonth() - 1)
    return renderPeriod(provided);
}

function nextPeriod(fromPeriod) {
    let provided = parsePeriod(fromPeriod);
    provided.setMonth(provided.getMonth() + 1)
    return renderPeriod(provided);
}

function isCurrentPeriod(period) {
    return period === currentPeriod();
}

function isBeforeCurrentPeriod(period) {
    let provided = parsePeriod(period);
    let now = new Date();
    now.setDate(1);
    now.setHours(0);
    now.setMinutes(0);
    now.setSeconds(0);
    now.setMilliseconds(0);

    return provided != now && now > provided;
}

function renderPeriod(date) {
    let year = date.getFullYear().toString();
    let month = String(date.getMonth() + 1).padStart(2, '0');
    return `${year}-${month}`;
}

function parsePeriod(string) {
    let period = string.split('-');
    let year = parseInt(period[0]);
    let month = parseInt(period[1]) - 1;

    return new Date(year, month, 1, 0, 0, 0, 0);
}

function currentDay() {
    return new Date().getDate();
}
