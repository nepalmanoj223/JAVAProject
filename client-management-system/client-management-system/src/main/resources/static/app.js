/*
 * ClientDesk - frontend for the Client Management System.
 * Plain JavaScript. Every screen calls the real Spring Boot REST API under /api.
 */
'use strict';

/* ======================================================================
   Constants
   ====================================================================== */

const CLIENT_STATUSES = ['LEAD', 'ACTIVE', 'INACTIVE', 'CLOSED'];
const CLIENT_TYPES = ['INDIVIDUAL', 'BUSINESS'];
const PROJECT_STATUSES = ['PLANNED', 'IN_PROGRESS', 'ON_HOLD', 'COMPLETED', 'CANCELLED'];
const PRIORITIES = ['LOW', 'MEDIUM', 'HIGH'];
const INTERACTION_TYPES = ['CALL', 'EMAIL', 'MEETING', 'NOTE'];
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PHONE_PATTERN = /^\+?[0-9 ()\-]{7,20}$/;

const app = document.getElementById('app');

/* Remembered filters, so going back to a list keeps your search. */
const clientFilters = { search: '', status: '', clientType: '' };
const projectFilters = { status: '', priority: '', clientId: '' };

/* ======================================================================
   API helper
   ====================================================================== */

class ApiError extends Error {
    constructor(message, status, fieldErrors) {
        super(message);
        this.status = status;
        this.fieldErrors = fieldErrors || {};
    }
}

async function api(method, path, body) {
    const options = { method, headers: { Accept: 'application/json' } };
    if (body !== undefined) {
        options.headers['Content-Type'] = 'application/json';
        options.body = JSON.stringify(body);
    }

    let response;
    try {
        response = await fetch(path, options);
    } catch (networkError) {
        throw new ApiError('Cannot reach the server. Is the application running in IntelliJ IDEA?', 0);
    }

    if (response.status === 204) {
        return null;
    }

    const text = await response.text();
    let data = null;
    if (text) {
        try { data = JSON.parse(text); } catch (parseError) { data = null; }
    }

    if (!response.ok) {
        if (response.status === 503) {
            setDbStatus(false);
        }
        const message = (data && data.message) || `Request failed (HTTP ${response.status}).`;
        throw new ApiError(message, response.status, data && data.fieldErrors);
    }
    return data;
}

function query(params) {
    const qs = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
        if (value !== undefined && value !== null && String(value).trim() !== '') {
            qs.set(key, String(value).trim());
        }
    });
    const s = qs.toString();
    return s ? `?${s}` : '';
}

/* ======================================================================
   Formatting helpers
   ====================================================================== */

function esc(value) {
    if (value === null || value === undefined) return '';
    return String(value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

function label(enumValue) {
    if (!enumValue) return '';
    const text = String(enumValue).replace(/_/g, ' ').toLowerCase();
    return text.charAt(0).toUpperCase() + text.slice(1);
}

function badge(value, extraClass) {
    if (!value) return '';
    return `<span class="badge ${esc(value)} ${extraClass || ''}">${esc(label(value))}</span>`;
}

function overdueBadge() {
    return '<span class="badge overdue">Overdue</span>';
}

/** "2026-10-16" -> "16 Oct 2026" (no time-zone shifting). */
function fmtDate(isoDate) {
    if (!isoDate) return '—';
    const [y, m, d] = isoDate.split('-').map(Number);
    const date = new Date(y, m - 1, d);
    return date.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' });
}

function fmtDateTime(isoInstant) {
    if (!isoInstant) return '—';
    return new Date(isoInstant).toLocaleString(undefined, {
        day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit'
    });
}

function fmtMoney(amount) {
    if (amount === null || amount === undefined || amount === '') return '—';
    return Number(amount).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

/** Today's date in the browser's local time zone as YYYY-MM-DD. */
function todayISO() {
    const now = new Date();
    const local = new Date(now.getTime() - now.getTimezoneOffset() * 60000);
    return local.toISOString().slice(0, 10);
}

function options(values, selected, placeholder) {
    let html = placeholder !== undefined ? `<option value="">${esc(placeholder)}</option>` : '';
    values.forEach(v => {
        html += `<option value="${esc(v)}" ${v === selected ? 'selected' : ''}>${esc(label(v))}</option>`;
    });
    return html;
}

/* ======================================================================
   UI states: loading, empty, error, toasts, confirmations, modal
   ====================================================================== */

function loadingHTML(text) {
    return `<div class="loading"><span class="spinner"></span>${esc(text || 'Loading…')}</div>`;
}

function emptyHTML(title, text, actionHtml) {
    return `<div class="empty"><strong>${esc(title)}</strong>${esc(text || '')}${actionHtml ? `<div>${actionHtml}</div>` : ''}</div>`;
}

function errorHTML(message) {
    return `<div class="error-box">${esc(message)}</div>`;
}

function toast(message, type) {
    const el = document.createElement('div');
    el.className = `toast ${type === 'error' ? 'error' : ''}`;
    el.textContent = message;
    document.getElementById('toasts').appendChild(el);
    setTimeout(() => el.remove(), type === 'error' ? 7000 : 3500);
}

function confirmAction(message, okLabel) {
    return new Promise(resolve => {
        const overlay = document.getElementById('confirm');
        const okBtn = document.getElementById('confirmOk');
        const cancelBtn = document.getElementById('confirmCancel');
        document.getElementById('confirmMessage').textContent = message;
        okBtn.textContent = okLabel || 'Delete';
        overlay.classList.remove('hidden');
        okBtn.focus();

        const finish = result => {
            overlay.classList.add('hidden');
            okBtn.removeEventListener('click', onOk);
            cancelBtn.removeEventListener('click', onCancel);
            resolve(result);
        };
        const onOk = () => finish(true);
        const onCancel = () => finish(false);
        okBtn.addEventListener('click', onOk);
        cancelBtn.addEventListener('click', onCancel);
    });
}

function openModal(title, html) {
    document.getElementById('modalTitle').textContent = title;
    const body = document.getElementById('modalBody');
    body.innerHTML = html;
    document.getElementById('modal').classList.remove('hidden');
    const first = body.querySelector('input, select, textarea');
    if (first) first.focus();
    return body;
}

function closeModal() {
    document.getElementById('modal').classList.add('hidden');
    document.getElementById('modalBody').innerHTML = '';
}

document.querySelectorAll('[data-close-modal]').forEach(btn => btn.addEventListener('click', closeModal));
document.getElementById('modal').addEventListener('click', e => {
    if (e.target.id === 'modal') closeModal();
});
document.addEventListener('keydown', e => {
    if (e.key === 'Escape') {
        if (!document.getElementById('confirm').classList.contains('hidden')) {
            document.getElementById('confirmCancel').click();
        } else {
            closeModal();
        }
    }
});

/* ======================================================================
   Database status (GET /api/health)
   ====================================================================== */

function setDbStatus(up) {
    const foot = document.getElementById('dbStatus');
    foot.querySelector('.dot').className = `dot ${up ? 'up' : 'down'}`;
    foot.querySelector('.dot-label').textContent = up ? 'MongoDB connected' : 'MongoDB not connected';
    document.getElementById('dbBanner').classList.toggle('hidden', up);
}

async function checkHealth() {
    try {
        const response = await fetch('/api/health');
        setDbStatus(response.ok);
    } catch (e) {
        setDbStatus(false);
    }
}

/* ======================================================================
   Forms: building fields, validation messages, submit handling
   ====================================================================== */

function field(name, text, control, opts) {
    const o = opts || {};
    return `
        <div class="field ${o.full ? 'full' : ''}" data-field="${name}">
            <label for="f-${name}">${esc(text)}${o.required ? ' <span class="req">*</span>' : ''}</label>
            ${control}
            ${o.hint ? `<span class="hint">${esc(o.hint)}</span>` : ''}
            <span class="error"></span>
        </div>`;
}

function input(name, value, attrs) {
    return `<input id="f-${name}" name="${name}" value="${esc(value)}" ${attrs || ''}>`;
}

function textarea(name, value, attrs) {
    return `<textarea id="f-${name}" name="${name}" ${attrs || ''}>${esc(value)}</textarea>`;
}

function select(name, html, attrs) {
    return `<select id="f-${name}" name="${name}" ${attrs || ''}>${html}</select>`;
}

function readForm(form) {
    const data = {};
    new FormData(form).forEach((value, key) => {
        const v = typeof value === 'string' ? value.trim() : value;
        data[key] = v === '' ? null : v;
    });
    return data;
}

function clearFormErrors(form) {
    form.querySelectorAll('.field.invalid').forEach(f => f.classList.remove('invalid'));
    form.querySelectorAll('.field .error').forEach(e => { e.textContent = ''; });
    const alert = form.querySelector('.form-alert');
    if (alert) alert.remove();
}

function showFormErrors(form, fieldErrors, generalMessage) {
    Object.entries(fieldErrors || {}).forEach(([name, message]) => {
        const f = form.querySelector(`.field[data-field="${name}"]`);
        if (f) {
            f.classList.add('invalid');
            f.querySelector('.error').textContent = message;
        }
    });
    if (generalMessage) {
        const alert = document.createElement('div');
        alert.className = 'form-alert';
        alert.textContent = generalMessage;
        form.prepend(alert);
    }
}

/** Validates in the browser first, then submits; server errors are shown inside the form. */
function bindForm(form, validate, submit) {
    form.addEventListener('submit', async e => {
        e.preventDefault();
        clearFormErrors(form);
        const data = readForm(form);
        const errors = validate(data);
        if (Object.keys(errors).length > 0) {
            showFormErrors(form, errors, 'Please fix the highlighted fields.');
            return;
        }
        const button = form.querySelector('button[type="submit"]');
        const originalText = button.textContent;
        button.disabled = true;
        button.textContent = 'Saving…';
        try {
            await submit(data);
        } catch (err) {
            showFormErrors(form, err.fieldErrors, err.message);
        } finally {
            button.disabled = false;
            button.textContent = originalText;
        }
    });
}

function formActions(submitLabel) {
    return `<div class="form-actions">
        <button type="button" class="btn btn-ghost" data-close-modal-inline>Cancel</button>
        <button type="submit" class="btn">${esc(submitLabel)}</button>
    </div>`;
}

function wireInlineCancel(container) {
    container.querySelectorAll('[data-close-modal-inline]').forEach(b => b.addEventListener('click', closeModal));
}

/* ======================================================================
   Router
   ====================================================================== */

let viewToken = 0;

/** Starts a new screen. Returns a token used to ignore late responses from a previous screen. */
function startView(title, actionsHtml) {
    viewToken += 1;
    document.getElementById('pageTitle').textContent = title;
    document.getElementById('topbarActions').innerHTML = actionsHtml || '';
    document.title = `${title} · Client Management System`;
    return viewToken;
}

function isStale(token) {
    return token !== viewToken;
}

const routes = [
    { pattern: /^#\/dashboard$/, nav: 'dashboard', render: () => renderDashboard() },
    { pattern: /^#\/clients$/, nav: 'clients', render: () => renderClients() },
    { pattern: /^#\/clients\/([^/]+)$/, nav: 'clients', render: m => renderClientDetail(m[1]) },
    { pattern: /^#\/projects$/, nav: 'projects', render: () => renderProjects() },
    { pattern: /^#\/projects\/([^/]+)$/, nav: 'projects', render: m => renderProjectDetail(m[1]) },
    { pattern: /^#\/interactions$/, nav: 'interactions', render: () => renderInteractions(null) },
    { pattern: /^#\/interactions\/([^/]+)$/, nav: 'interactions', render: m => renderInteractions(m[1]) }
];

function router() {
    const hash = location.hash || '#/dashboard';
    for (const route of routes) {
        const match = hash.match(route.pattern);
        if (match) {
            document.querySelectorAll('.nav a').forEach(a => a.classList.toggle('active', a.dataset.nav === route.nav));
            document.getElementById('sidebar').classList.remove('open');
            closeModal();
            route.render(match);
            window.scrollTo(0, 0);
            return;
        }
    }
    location.hash = '#/dashboard';
}

/** Re-draws the current screen after a change. */
function refresh() {
    router();
}

/* ======================================================================
   Dashboard
   ====================================================================== */

async function renderDashboard() {
    const token = startView('Dashboard', `
        <button class="btn" data-action="new-client">+ New client</button>
        <button class="btn btn-ghost" data-action="new-project">+ New project</button>`);
    app.innerHTML = loadingHTML('Loading dashboard…');

    try {
        const s = await api('GET', '/api/dashboard/summary');
        if (isStale(token)) return;

        const stat = (labelText, value, href, warn) =>
            `<a class="stat ${warn && value > 0 ? 'warn' : ''}" href="${href}">
                <div class="stat-label">${esc(labelText)}</div>
                <div class="stat-value">${esc(value)}</div>
            </a>`;

        const recent = s.recentClients.length === 0
            ? emptyHTML('No clients yet', 'Add your first client to get started.',
                '<button class="btn" data-action="new-client">+ New client</button>')
            : `<div class="table-wrap"><table>
                <thead><tr><th>Client</th><th>Status</th><th class="hide-sm">Added</th></tr></thead>
                <tbody>${s.recentClients.map(c => `
                    <tr>
                        <td><a href="#/clients/${esc(c.id)}">${esc(c.fullName)}</a>
                            <span class="sub">${esc(c.companyName || c.email)}</span></td>
                        <td>${badge(c.status)}</td>
                        <td class="hide-sm">${esc(fmtDateTime(c.createdAt))}</td>
                    </tr>`).join('')}</tbody></table></div>`;

        const overdue = s.overdueProjectList.length === 0
            ? emptyHTML('No overdue projects', 'Every open project is on schedule.')
            : `<div class="table-wrap"><table>
                <thead><tr><th>Project</th><th>Due</th><th>Status</th></tr></thead>
                <tbody>${s.overdueProjectList.map(p => `
                    <tr>
                        <td><a href="#/projects/${esc(p.id)}">${esc(p.name)}</a>
                            <span class="sub">${esc(p.clientName || '')}</span></td>
                        <td>${esc(fmtDate(p.dueDate))}</td>
                        <td>${badge(p.status)}</td>
                    </tr>`).join('')}</tbody></table></div>`;

        app.innerHTML = `
            <div class="stats">
                ${stat('Total clients', s.totalClients, '#/clients')}
                ${stat('Active clients', s.activeClients, '#/clients')}
                ${stat('Leads', s.leads, '#/clients')}
                ${stat('Inactive / closed', s.inactiveAndClosedClients, '#/clients')}
                ${stat('Total projects', s.totalProjects, '#/projects')}
                ${stat('In progress', s.projectsInProgress, '#/projects')}
                ${stat('Completed', s.completedProjects, '#/projects')}
                ${stat('Overdue', s.overdueProjects, '#/projects', true)}
                ${stat('Interactions', s.totalInteractions, '#/interactions')}
            </div>
            <div class="grid-2">
                <section class="panel">
                    <div class="panel-head"><h2>Recent clients</h2><a href="#/clients">View all</a></div>
                    <div class="panel-body flush">${recent}</div>
                </section>
                <section class="panel">
                    <div class="panel-head"><h2>Overdue projects</h2><a href="#/projects">View all projects</a></div>
                    <div class="panel-body flush">${overdue}</div>
                </section>
            </div>
            <section class="panel">
                <div class="panel-head"><h2>Quick navigation</h2></div>
                <div class="panel-body quick-links">
                    <button class="btn" data-action="new-client">+ Add client</button>
                    <button class="btn btn-ghost" data-action="new-project">+ Add project</button>
                    <a class="btn btn-ghost" href="#/clients">Browse clients</a>
                    <a class="btn btn-ghost" href="#/projects">Browse projects</a>
                    <a class="btn btn-ghost" href="#/interactions">Interaction history</a>
                </div>
            </section>`;
    } catch (err) {
        if (isStale(token)) return;
        app.innerHTML = errorHTML(err.message);
    }
}

/* ======================================================================
   Clients
   ====================================================================== */

async function renderClients() {
    const token = startView('Clients', '<button class="btn" data-action="new-client">+ New client</button>');
    app.innerHTML = `
        <section class="panel">
            <div class="toolbar">
                <input class="grow" id="clientSearch" type="search" placeholder="Search name, email or company…"
                       value="${esc(clientFilters.search)}" aria-label="Search clients">
                <select id="clientStatusFilter" aria-label="Filter by status">
                    ${options(CLIENT_STATUSES, clientFilters.status, 'All statuses')}
                </select>
                <select id="clientTypeFilter" aria-label="Filter by client type">
                    ${options(CLIENT_TYPES, clientFilters.clientType, 'All types')}
                </select>
                <button class="btn btn-ghost" id="clientClear">Clear</button>
            </div>
            <div id="clientList">${loadingHTML('Loading clients…')}</div>
        </section>`;

    let debounce;
    document.getElementById('clientSearch').addEventListener('input', e => {
        clientFilters.search = e.target.value;
        clearTimeout(debounce);
        debounce = setTimeout(() => loadClientList(token), 300);
    });
    document.getElementById('clientStatusFilter').addEventListener('change', e => {
        clientFilters.status = e.target.value;
        loadClientList(token);
    });
    document.getElementById('clientTypeFilter').addEventListener('change', e => {
        clientFilters.clientType = e.target.value;
        loadClientList(token);
    });
    document.getElementById('clientClear').addEventListener('click', () => {
        clientFilters.search = '';
        clientFilters.status = '';
        clientFilters.clientType = '';
        renderClients();
    });

    await loadClientList(token);
}

async function loadClientList(token) {
    const list = document.getElementById('clientList');
    if (!list) return;
    list.innerHTML = loadingHTML('Loading clients…');
    try {
        const clients = await api('GET', '/api/clients' + query(clientFilters));
        if (isStale(token)) return;
        const filtered = clientFilters.search || clientFilters.status || clientFilters.clientType;
        if (clients.length === 0) {
            list.innerHTML = filtered
                ? emptyHTML('No matching clients', 'Try a different search or clear the filters.')
                : emptyHTML('No clients yet', 'Clients you add will appear here.',
                    '<button class="btn" data-action="new-client">+ New client</button>');
            return;
        }
        list.innerHTML = `<div class="table-wrap"><table>
            <thead><tr>
                <th>Name</th><th>Email</th><th class="hide-sm">Phone</th><th>Type</th><th>Status</th>
                <th class="hide-sm">Added</th><th></th>
            </tr></thead>
            <tbody>${clients.map(c => `
                <tr>
                    <td><a href="#/clients/${esc(c.id)}">${esc(c.fullName)}</a>
                        ${c.companyName ? `<span class="sub">${esc(c.companyName)}</span>` : ''}</td>
                    <td>${esc(c.email)}</td>
                    <td class="hide-sm">${esc(c.phone)}</td>
                    <td>${esc(label(c.clientType))}</td>
                    <td>${badge(c.status)}</td>
                    <td class="hide-sm">${esc(c.createdAt ? new Date(c.createdAt).toLocaleDateString() : '—')}</td>
                    <td class="actions">
                        <a class="btn-link" href="#/clients/${esc(c.id)}">View</a>
                        <button class="btn-link" data-action="edit-client" data-id="${esc(c.id)}">Edit</button>
                        <button class="btn-link danger" data-action="delete-client" data-id="${esc(c.id)}"
                                data-name="${esc(c.fullName)}">Delete</button>
                    </td>
                </tr>`).join('')}</tbody></table></div>
            <div class="panel-body" style="color:var(--muted);font-size:13px">${clients.length} client(s)</div>`;
    } catch (err) {
        if (isStale(token)) return;
        list.innerHTML = `<div class="panel-body">${errorHTML(err.message)}</div>`;
    }
}

async function renderClientDetail(id) {
    const token = startView('Client details');
    app.innerHTML = loadingHTML('Loading client…');

    try {
        const [summary, projects, interactions] = await Promise.all([
            api('GET', `/api/clients/${encodeURIComponent(id)}/summary`),
            api('GET', `/api/clients/${encodeURIComponent(id)}/projects`),
            api('GET', `/api/clients/${encodeURIComponent(id)}/interactions`)
        ]);
        if (isStale(token)) return;

        const c = summary.client;
        const canAddProject = c.status === 'LEAD' || c.status === 'ACTIVE';
        document.getElementById('pageTitle').textContent = c.fullName;
        document.getElementById('topbarActions').innerHTML = `
            <button class="btn btn-ghost" data-action="edit-client" data-id="${esc(c.id)}">Edit</button>
            <button class="btn btn-danger" data-action="delete-client" data-id="${esc(c.id)}"
                    data-name="${esc(c.fullName)}">Delete</button>`;

        const mini = (labelText, value) =>
            `<div class="mini-stat"><div class="stat-label">${esc(labelText)}</div><div class="stat-value">${esc(value)}</div></div>`;

        const projectRows = projects.length === 0
            ? emptyHTML('No projects for this client', canAddProject ? '' : 'Projects can only be added to LEAD or ACTIVE clients.')
            : `<div class="table-wrap"><table>
                <thead><tr><th>Project</th><th>Status</th><th>Priority</th><th>Due</th></tr></thead>
                <tbody>${projects.map(p => `
                    <tr>
                        <td><a href="#/projects/${esc(p.id)}">${esc(p.name)}</a></td>
                        <td>${badge(p.status)} ${p.overdue ? overdueBadge() : ''}</td>
                        <td>${badge(p.priority)}</td>
                        <td>${esc(fmtDate(p.dueDate))}</td>
                    </tr>`).join('')}</tbody></table></div>`;

        app.innerHTML = `
            <a class="back-link" href="#/clients">← Back to clients</a>
            <section class="panel">
                <div class="panel-body">
                    <div class="detail-head">
                        <div>
                            <h2>${esc(c.fullName)}</h2>
                            <div class="meta">${badge(c.status)} <span>${esc(label(c.clientType))}</span>
                                ${c.companyName ? `<span>· ${esc(c.companyName)}</span>` : ''}</div>
                        </div>
                    </div>
                </div>
            </section>

            <section class="panel">
                <div class="panel-head"><h2>Client summary</h2></div>
                <div class="panel-body">
                    <div class="mini-stats">
                        ${mini('Total projects', summary.totalProjects)}
                        ${mini('In progress', summary.projectsInProgress)}
                        ${mini('Completed', summary.completedProjects)}
                        ${mini('Overdue', summary.overdueProjects)}
                        ${mini('Interactions', summary.totalInteractions)}
                    </div>
                </div>
            </section>

            <div class="grid-2">
                <section class="panel">
                    <div class="panel-head"><h2>Details</h2></div>
                    <div class="panel-body">
                        <dl class="kv">
                            <dt>Email</dt><dd><a href="mailto:${esc(c.email)}">${esc(c.email)}</a></dd>
                            <dt>Phone</dt><dd>${esc(c.phone)}</dd>
                            <dt>Company</dt><dd>${esc(c.companyName || '—')}</dd>
                            <dt>Address</dt><dd>${esc(c.address || '—')}</dd>
                            <dt>Notes</dt><dd class="notes">${esc(c.notes || '—')}</dd>
                            <dt>Created</dt><dd>${esc(fmtDateTime(c.createdAt))}</dd>
                            <dt>Last updated</dt><dd>${esc(fmtDateTime(c.updatedAt))}</dd>
                        </dl>
                    </div>
                </section>
                <section class="panel">
                    <div class="panel-head">
                        <h2>Projects</h2>
                        ${canAddProject
                            ? `<button class="btn btn-sm" data-action="new-project" data-client-id="${esc(c.id)}">+ Add project</button>`
                            : '<span class="hint" style="font-size:13px;color:var(--muted)">Client is not active</span>'}
                    </div>
                    <div class="panel-body flush">${projectRows}</div>
                </section>
            </div>

            <section class="panel">
                <div class="panel-head">
                    <h2>Interaction history</h2>
                    <button class="btn btn-sm" data-action="new-interaction" data-client-id="${esc(c.id)}">+ Add interaction</button>
                </div>
                <div class="panel-body flush">${interactionTimeline(interactions, c.id)}</div>
            </section>`;
    } catch (err) {
        if (isStale(token)) return;
        app.innerHTML = `<a class="back-link" href="#/clients">← Back to clients</a>${errorHTML(err.message)}`;
    }
}

function clientFormHTML(c) {
    const isEdit = Boolean(c.id);
    return `<form id="clientForm" novalidate>
        <div class="form-grid">
            ${field('firstName', 'First name', input('firstName', c.firstName, 'maxlength="50" autocomplete="off"'), { required: true })}
            ${field('lastName', 'Last name', input('lastName', c.lastName, 'maxlength="50" autocomplete="off"'), { required: true })}
            ${field('email', 'Email', input('email', c.email, 'type="email" maxlength="120" autocomplete="off"'), { required: true })}
            ${field('phone', 'Phone', input('phone', c.phone, 'maxlength="20" placeholder="+977 9800000000"'), { required: true })}
            ${field('clientType', 'Client type', select('clientType', options(CLIENT_TYPES, c.clientType || 'INDIVIDUAL')), { required: true })}
            ${field('status', 'Status',
                select('status', isEdit ? options(CLIENT_STATUSES, c.status) : options(CLIENT_STATUSES, null, 'Default (Lead)')),
                { hint: isEdit ? '' : 'New clients become Lead unless you choose otherwise.' })}
            ${field('companyName', 'Company name', input('companyName', c.companyName, 'maxlength="120"'),
                { hint: 'Required for business clients.' })}
            ${field('address', 'Address', input('address', c.address, 'maxlength="255"'))}
            ${field('notes', 'Notes', textarea('notes', c.notes, 'maxlength="2000"'), { full: true })}
        </div>
        ${formActions(isEdit ? 'Save changes' : 'Create client')}
    </form>`;
}

function validateClient(d) {
    const e = {};
    if (!d.firstName) e.firstName = 'First name is required.';
    if (!d.lastName) e.lastName = 'Last name is required.';
    if (!d.email) e.email = 'Email is required.';
    else if (!EMAIL_PATTERN.test(d.email)) e.email = 'Enter a valid email address.';
    if (!d.phone) e.phone = 'Phone is required.';
    else if (!PHONE_PATTERN.test(d.phone)) e.phone = 'Use 7-20 digits, spaces, (), - and an optional leading +.';
    if (!d.clientType) e.clientType = 'Choose a client type.';
    if (d.clientType === 'BUSINESS' && !d.companyName) e.companyName = 'Company name is required for business clients.';
    return e;
}

async function openClientForm(clientId) {
    let client = {};
    if (clientId) {
        openModal('Edit client', loadingHTML('Loading client…'));
        try {
            client = await api('GET', `/api/clients/${encodeURIComponent(clientId)}`);
        } catch (err) {
            closeModal();
            toast(err.message, 'error');
            return;
        }
    }
    const body = openModal(clientId ? 'Edit client' : 'New client', clientFormHTML(client));
    wireInlineCancel(body);
    bindForm(body.querySelector('form'), validateClient, async data => {
        if (clientId) {
            await api('PUT', `/api/clients/${encodeURIComponent(clientId)}`, data);
            closeModal();
            toast('Client updated.');
            refresh();
        } else {
            const created = await api('POST', '/api/clients', data);
            closeModal();
            toast(`Client "${created.fullName}" created with status ${label(created.status)}.`);
            location.hash = `#/clients/${created.id}`;
        }
    });
}

async function deleteClient(id, name) {
    const ok = await confirmAction(`Delete client "${name}"? This cannot be undone.`);
    if (!ok) return;
    try {
        await api('DELETE', `/api/clients/${encodeURIComponent(id)}`);
        toast('Client deleted.');
        if (location.hash === '#/clients') refresh();
        else location.hash = '#/clients';
    } catch (err) {
        toast(err.message, 'error');
    }
}

/* ======================================================================
   Projects
   ====================================================================== */

async function renderProjects() {
    const token = startView('Projects', '<button class="btn" data-action="new-project">+ New project</button>');
    app.innerHTML = `
        <section class="panel">
            <div class="panel-head"><h2>Overdue projects</h2></div>
            <div class="panel-body flush" id="overdueList">${loadingHTML('Checking for overdue projects…')}</div>
        </section>
        <section class="panel">
            <div class="panel-head"><h2>All projects</h2></div>
            <div class="toolbar">
                <select id="projectStatusFilter" aria-label="Filter by status">
                    ${options(PROJECT_STATUSES, projectFilters.status, 'All statuses')}
                </select>
                <select id="projectPriorityFilter" aria-label="Filter by priority">
                    ${options(PRIORITIES, projectFilters.priority, 'All priorities')}
                </select>
                <select id="projectClientFilter" aria-label="Filter by client">
                    <option value="">All clients</option>
                </select>
                <button class="btn btn-ghost" id="projectClear">Clear</button>
            </div>
            <div id="projectList">${loadingHTML('Loading projects…')}</div>
        </section>`;

    document.getElementById('projectStatusFilter').addEventListener('change', e => {
        projectFilters.status = e.target.value;
        loadProjectList(token);
    });
    document.getElementById('projectPriorityFilter').addEventListener('change', e => {
        projectFilters.priority = e.target.value;
        loadProjectList(token);
    });
    document.getElementById('projectClientFilter').addEventListener('change', e => {
        projectFilters.clientId = e.target.value;
        loadProjectList(token);
    });
    document.getElementById('projectClear').addEventListener('click', () => {
        projectFilters.status = '';
        projectFilters.priority = '';
        projectFilters.clientId = '';
        renderProjects();
    });

    // Fill the client filter, the overdue section and the list in parallel.
    api('GET', '/api/clients').then(clients => {
        if (isStale(token)) return;
        const sel = document.getElementById('projectClientFilter');
        clients.forEach(c => {
            const opt = document.createElement('option');
            opt.value = c.id;
            opt.textContent = c.fullName;
            if (c.id === projectFilters.clientId) opt.selected = true;
            sel.appendChild(opt);
        });
    }).catch(() => { /* the list itself will show the error */ });

    loadOverdueList(token);
    await loadProjectList(token);
}

async function loadOverdueList(token) {
    const box = document.getElementById('overdueList');
    try {
        const overdue = await api('GET', '/api/projects/overdue');
        if (isStale(token)) return;
        box.innerHTML = overdue.length === 0
            ? emptyHTML('No overdue projects', 'Every open project is on schedule.')
            : projectTable(overdue);
    } catch (err) {
        if (isStale(token)) return;
        box.innerHTML = `<div class="panel-body">${errorHTML(err.message)}</div>`;
    }
}

async function loadProjectList(token) {
    const list = document.getElementById('projectList');
    if (!list) return;
    list.innerHTML = loadingHTML('Loading projects…');
    try {
        const projects = await api('GET', '/api/projects' + query(projectFilters));
        if (isStale(token)) return;
        const filtered = projectFilters.status || projectFilters.priority || projectFilters.clientId;
        if (projects.length === 0) {
            list.innerHTML = filtered
                ? emptyHTML('No matching projects', 'Try different filters.')
                : emptyHTML('No projects yet', 'Projects belong to a client. Create one to get started.',
                    '<button class="btn" data-action="new-project">+ New project</button>');
            return;
        }
        list.innerHTML = projectTable(projects) +
            `<div class="panel-body" style="color:var(--muted);font-size:13px">${projects.length} project(s)</div>`;
    } catch (err) {
        if (isStale(token)) return;
        list.innerHTML = `<div class="panel-body">${errorHTML(err.message)}</div>`;
    }
}

function projectTable(projects) {
    return `<div class="table-wrap"><table>
        <thead><tr>
            <th>Project</th><th>Status</th><th>Priority</th><th class="hide-sm">Start</th><th>Due</th>
            <th class="num hide-sm">Budget</th><th></th>
        </tr></thead>
        <tbody>${projects.map(p => `
            <tr>
                <td><a href="#/projects/${esc(p.id)}">${esc(p.name)}</a>
                    <span class="sub">${p.clientName
                        ? `<a href="#/clients/${esc(p.clientId)}">${esc(p.clientName)}</a>` : 'Unknown client'}</span></td>
                <td>${badge(p.status)} ${p.overdue ? overdueBadge() : ''}</td>
                <td>${badge(p.priority)}</td>
                <td class="hide-sm">${esc(fmtDate(p.startDate))}</td>
                <td>${esc(fmtDate(p.dueDate))}</td>
                <td class="num hide-sm">${esc(fmtMoney(p.budget))}</td>
                <td class="actions">
                    <a class="btn-link" href="#/projects/${esc(p.id)}">View</a>
                    <button class="btn-link" data-action="edit-project" data-id="${esc(p.id)}">Edit</button>
                    <button class="btn-link danger" data-action="delete-project" data-id="${esc(p.id)}"
                            data-name="${esc(p.name)}">Delete</button>
                </td>
            </tr>`).join('')}</tbody></table></div>`;
}

async function renderProjectDetail(id) {
    const token = startView('Project details');
    app.innerHTML = loadingHTML('Loading project…');
    try {
        const p = await api('GET', `/api/projects/${encodeURIComponent(id)}`);
        if (isStale(token)) return;

        document.getElementById('pageTitle').textContent = p.name;
        document.getElementById('topbarActions').innerHTML = `
            <button class="btn btn-ghost" data-action="edit-project" data-id="${esc(p.id)}">Edit</button>
            <button class="btn btn-danger" data-action="delete-project" data-id="${esc(p.id)}"
                    data-name="${esc(p.name)}">Delete</button>`;

        const next = p.allowedNextStatuses || [];
        const statusButtons = next.length === 0
            ? `<p style="margin:0;color:var(--muted)">${esc(label(p.status))} is a final status. It cannot be changed.</p>`
            : next.map(s => `<button class="btn btn-ghost btn-sm" data-action="move-project" data-id="${esc(p.id)}"
                                data-status="${esc(s)}">Move to ${esc(label(s))}</button>`).join(' ');

        app.innerHTML = `
            <a class="back-link" href="#/projects">← Back to projects</a>
            <section class="panel">
                <div class="panel-body">
                    <div class="detail-head">
                        <div>
                            <h2>${esc(p.name)}</h2>
                            <div class="meta">${badge(p.status)} ${badge(p.priority)} ${p.overdue ? overdueBadge() : ''}
                                <span>· Client: ${p.clientName
                                    ? `<a href="#/clients/${esc(p.clientId)}">${esc(p.clientName)}</a>` : 'Unknown'}</span></div>
                        </div>
                    </div>
                </div>
            </section>
            <div class="grid-2">
                <section class="panel">
                    <div class="panel-head"><h2>Details</h2></div>
                    <div class="panel-body">
                        <dl class="kv">
                            <dt>Description</dt><dd class="notes">${esc(p.description || '—')}</dd>
                            <dt>Start date</dt><dd>${esc(fmtDate(p.startDate))}</dd>
                            <dt>Due date</dt><dd>${esc(fmtDate(p.dueDate))} ${p.overdue ? overdueBadge() : ''}</dd>
                            <dt>Budget</dt><dd>${esc(fmtMoney(p.budget))}</dd>
                            <dt>Created</dt><dd>${esc(fmtDateTime(p.createdAt))}</dd>
                            <dt>Last updated</dt><dd>${esc(fmtDateTime(p.updatedAt))}</dd>
                        </dl>
                    </div>
                </section>
                <section class="panel">
                    <div class="panel-head"><h2>Change status</h2></div>
                    <div class="panel-body">
                        <p style="margin-top:0">Current status: ${badge(p.status)}</p>
                        <div class="quick-links">${statusButtons}</div>
                    </div>
                </section>
            </div>`;
    } catch (err) {
        if (isStale(token)) return;
        app.innerHTML = `<a class="back-link" href="#/projects">← Back to projects</a>${errorHTML(err.message)}`;
    }
}

function projectFormHTML(p, clients, presetClientId) {
    const isEdit = Boolean(p.id);
    const selectedClient = p.clientId || presetClientId || '';

    let clientOptions = '<option value="">Choose a client…</option>';
    clients.forEach(c => {
        const blocked = (c.status === 'INACTIVE' || c.status === 'CLOSED') && c.id !== p.clientId;
        clientOptions += `<option value="${esc(c.id)}" ${c.id === selectedClient ? 'selected' : ''} ${blocked ? 'disabled' : ''}>
            ${esc(c.fullName)}${c.companyName ? ` (${esc(c.companyName)})` : ''}${blocked ? ` — ${esc(label(c.status))}` : ''}
        </option>`;
    });

    // New projects start as Planned or In progress; existing ones can only move along allowed transitions.
    const statusChoices = isEdit ? [p.status].concat(p.allowedNextStatuses || []) : ['PLANNED', 'IN_PROGRESS'];
    const statusFinal = isEdit && (p.allowedNextStatuses || []).length === 0;

    return `<form id="projectForm" novalidate>
        <div class="form-grid">
            ${field('name', 'Project name', input('name', p.name, 'maxlength="120" autocomplete="off"'), { required: true, full: true })}
            ${field('clientId', 'Client', select('clientId', clientOptions), {
                required: true, full: true,
                hint: 'Inactive and closed clients cannot receive new projects. A Lead client becomes Active.'
            })}
            ${field('status', 'Status', select('status', options(statusChoices, p.status || 'PLANNED'), statusFinal ? 'disabled' : ''),
                { hint: statusFinal ? 'This status is final.' : (isEdit ? 'Only allowed next statuses are listed.' : '') })}
            ${field('priority', 'Priority', select('priority', options(PRIORITIES, p.priority || 'MEDIUM')))}
            ${field('startDate', 'Start date', input('startDate', p.startDate, 'type="date"'), { required: true })}
            ${field('dueDate', 'Due date', input('dueDate', p.dueDate, 'type="date"'), { required: true })}
            ${field('budget', 'Budget', input('budget', p.budget, 'type="number" min="0" step="0.01" placeholder="0.00"'))}
            ${field('description', 'Description', textarea('description', p.description, 'maxlength="2000"'), { full: true })}
        </div>
        ${formActions(isEdit ? 'Save changes' : 'Create project')}
    </form>`;
}

function validateProject(d) {
    const e = {};
    if (!d.name) e.name = 'Project name is required.';
    if (!d.clientId) e.clientId = 'Choose a client.';
    if (!d.startDate) e.startDate = 'Start date is required.';
    if (!d.dueDate) e.dueDate = 'Due date is required.';
    if (d.startDate && d.dueDate && d.dueDate < d.startDate) e.dueDate = 'Due date cannot be earlier than the start date.';
    if (d.status === 'COMPLETED' && d.startDate && todayISO() < d.startDate) {
        e.status = 'A project cannot be completed before its start date.';
    }
    if (d.budget !== null && d.budget !== undefined) {
        const n = Number(d.budget);
        if (Number.isNaN(n)) e.budget = 'Budget must be a number.';
        else if (n < 0) e.budget = 'Budget cannot be negative.';
        else if (!/^\d+(\.\d{1,2})?$/.test(String(d.budget))) e.budget = 'Use at most 2 decimal places.';
    }
    return e;
}

function projectPayload(d) {
    return {
        name: d.name,
        description: d.description,
        clientId: d.clientId,
        status: d.status || null,
        priority: d.priority || null,
        startDate: d.startDate,
        dueDate: d.dueDate,
        budget: d.budget === null || d.budget === undefined ? null : Number(d.budget)
    };
}

async function openProjectForm(projectId, presetClientId) {
    openModal(projectId ? 'Edit project' : 'New project', loadingHTML('Loading…'));
    let project = {};
    let clients;
    try {
        const requests = [api('GET', '/api/clients')];
        if (projectId) requests.push(api('GET', `/api/projects/${encodeURIComponent(projectId)}`));
        const results = await Promise.all(requests);
        clients = results[0];
        if (projectId) project = results[1];
    } catch (err) {
        closeModal();
        toast(err.message, 'error');
        return;
    }

    if (!projectId && clients.length === 0) {
        openModal('New project', emptyHTML('No clients yet', 'A project must belong to a client. Create a client first.',
            '<button class="btn" data-action="new-client">+ New client</button>'));
        return;
    }

    const body = openModal(projectId ? 'Edit project' : 'New project', projectFormHTML(project, clients, presetClientId));
    wireInlineCancel(body);
    bindForm(body.querySelector('form'), validateProject, async data => {
        const payload = projectPayload(data);
        if (projectId) {
            await api('PUT', `/api/projects/${encodeURIComponent(projectId)}`, payload);
            closeModal();
            toast('Project updated.');
            refresh();
        } else {
            const client = clients.find(c => c.id === payload.clientId);
            const created = await api('POST', '/api/projects', payload);
            closeModal();
            toast(client && client.status === 'LEAD'
                ? `Project created. ${client.fullName} is now an Active client.`
                : 'Project created.');
            location.hash = `#/projects/${created.id}`;
        }
    });
}

/** Quick status change from the project detail page (PUT with the full project). */
async function moveProject(id, newStatus) {
    try {
        const p = await api('GET', `/api/projects/${encodeURIComponent(id)}`);
        const finalStatus = newStatus === 'COMPLETED' || newStatus === 'CANCELLED';
        if (finalStatus) {
            const ok = await confirmAction(`Mark "${p.name}" as ${label(newStatus)}? This is a final status and cannot be undone.`,
                `Mark ${label(newStatus)}`);
            if (!ok) return;
        }
        await api('PUT', `/api/projects/${encodeURIComponent(id)}`, {
            name: p.name, description: p.description, clientId: p.clientId, status: newStatus,
            priority: p.priority, startDate: p.startDate, dueDate: p.dueDate, budget: p.budget
        });
        toast(`Status changed to ${label(newStatus)}.`);
        refresh();
    } catch (err) {
        toast(err.message, 'error');
    }
}

async function deleteProject(id, name) {
    const ok = await confirmAction(`Delete project "${name}"? This cannot be undone.`);
    if (!ok) return;
    try {
        await api('DELETE', `/api/projects/${encodeURIComponent(id)}`);
        toast('Project deleted.');
        if (location.hash.startsWith('#/projects/')) location.hash = '#/projects';
        else refresh();
    } catch (err) {
        toast(err.message, 'error');
    }
}

/* ======================================================================
   Interactions
   ====================================================================== */

function interactionTimeline(interactions, clientId) {
    if (interactions.length === 0) {
        return emptyHTML('No interactions recorded', 'Log calls, emails, meetings and notes to build a history.',
            `<button class="btn btn-sm" data-action="new-interaction" data-client-id="${esc(clientId)}">+ Add interaction</button>`);
    }
    return `<ul class="timeline">${interactions.map(i => `
        <li>
            <div class="when">${esc(fmtDate(i.interactionDate))}</div>
            <div class="what">
                <h3>${badge(i.type)} ${esc(i.subject)}</h3>
                ${i.description ? `<p>${esc(i.description)}</p>` : ''}
            </div>
            <div class="row-actions">
                <button class="btn-link" data-action="edit-interaction" data-id="${esc(i.id)}"
                        data-client-id="${esc(clientId)}">Edit</button>
                <button class="btn-link danger" data-action="delete-interaction" data-id="${esc(i.id)}"
                        data-name="${esc(i.subject)}">Delete</button>
            </div>
        </li>`).join('')}</ul>`;
}

async function renderInteractions(clientId) {
    const token = startView('Interactions');
    app.innerHTML = loadingHTML('Loading clients…');
    try {
        const clients = await api('GET', '/api/clients');
        if (isStale(token)) return;

        if (clients.length === 0) {
            app.innerHTML = `<section class="panel">${emptyHTML('No clients yet',
                'Interactions are recorded per client. Create a client first.',
                '<button class="btn" data-action="new-client">+ New client</button>')}</section>`;
            return;
        }

        const clientOptions = clients.map(c =>
            `<option value="${esc(c.id)}" ${c.id === clientId ? 'selected' : ''}>${esc(c.fullName)}${c.companyName ? ` (${esc(c.companyName)})` : ''}</option>`
        ).join('');

        app.innerHTML = `
            <section class="panel">
                <div class="toolbar">
                    <label for="interactionClient" style="font-weight:600">Client</label>
                    <select class="grow" id="interactionClient">
                        <option value="">Choose a client to see their history…</option>${clientOptions}
                    </select>
                    ${clientId ? `<button class="btn" data-action="new-interaction" data-client-id="${esc(clientId)}">+ Add interaction</button>` : ''}
                </div>
                <div id="interactionList">${clientId ? loadingHTML('Loading history…')
                    : emptyHTML('Choose a client', 'Select a client above to see and manage their interaction history.')}</div>
            </section>`;

        document.getElementById('interactionClient').addEventListener('change', e => {
            location.hash = e.target.value ? `#/interactions/${e.target.value}` : '#/interactions';
        });

        if (clientId) {
            const list = await api('GET', `/api/clients/${encodeURIComponent(clientId)}/interactions`);
            if (isStale(token)) return;
            document.getElementById('interactionList').innerHTML = interactionTimeline(list, clientId);
        }
    } catch (err) {
        if (isStale(token)) return;
        const box = document.getElementById('interactionList');
        if (box) box.innerHTML = `<div class="panel-body">${errorHTML(err.message)}</div>`;
        else app.innerHTML = errorHTML(err.message);
    }
}

function interactionFormHTML(i) {
    const isEdit = Boolean(i.id);
    return `<form id="interactionForm" novalidate>
        <div class="form-grid">
            ${field('type', 'Type', select('type', options(INTERACTION_TYPES, i.type || 'CALL')), { required: true })}
            ${field('interactionDate', 'Date', input('interactionDate', i.interactionDate || todayISO(),
                `type="date" max="${todayISO()}"`), { required: true })}
            ${field('subject', 'Subject', input('subject', i.subject, 'maxlength="200" autocomplete="off"'), { required: true, full: true })}
            ${field('description', 'Description', textarea('description', i.description, 'maxlength="5000"'), { full: true })}
        </div>
        ${formActions(isEdit ? 'Save changes' : 'Add interaction')}
    </form>`;
}

function validateInteraction(d) {
    const e = {};
    if (!d.type) e.type = 'Choose a type.';
    if (!d.subject) e.subject = 'Subject is required.';
    if (!d.interactionDate) e.interactionDate = 'Date is required.';
    else if (d.interactionDate > todayISO()) e.interactionDate = 'Date cannot be in the future.';
    return e;
}

async function openInteractionForm(clientId, interactionId) {
    let interaction = {};
    if (interactionId) {
        openModal('Edit interaction', loadingHTML('Loading…'));
        try {
            interaction = await api('GET', `/api/interactions/${encodeURIComponent(interactionId)}`);
        } catch (err) {
            closeModal();
            toast(err.message, 'error');
            return;
        }
    }
    const body = openModal(interactionId ? 'Edit interaction' : 'New interaction', interactionFormHTML(interaction));
    wireInlineCancel(body);
    bindForm(body.querySelector('form'), validateInteraction, async data => {
        if (interactionId) {
            await api('PUT', `/api/interactions/${encodeURIComponent(interactionId)}`, data);
            toast('Interaction updated.');
        } else {
            await api('POST', `/api/clients/${encodeURIComponent(clientId)}/interactions`, data);
            toast('Interaction added.');
        }
        closeModal();
        refresh();
    });
}

async function deleteInteraction(id, subject) {
    const ok = await confirmAction(`Delete the interaction "${subject}"? This cannot be undone.`);
    if (!ok) return;
    try {
        await api('DELETE', `/api/interactions/${encodeURIComponent(id)}`);
        toast('Interaction deleted.');
        refresh();
    } catch (err) {
        toast(err.message, 'error');
    }
}

/* ======================================================================
   Button actions (one listener for the whole page)
   ====================================================================== */

document.addEventListener('click', e => {
    const el = e.target.closest('[data-action]');
    if (!el) return;
    const { action, id, name, clientId, status } = el.dataset;

    switch (action) {
        case 'new-client': openClientForm(null); break;
        case 'edit-client': openClientForm(id); break;
        case 'delete-client': deleteClient(id, name); break;
        case 'new-project': openProjectForm(null, clientId || null); break;
        case 'edit-project': openProjectForm(id, null); break;
        case 'delete-project': deleteProject(id, name); break;
        case 'move-project': moveProject(id, status); break;
        case 'new-interaction': openInteractionForm(clientId, null); break;
        case 'edit-interaction': openInteractionForm(clientId, id); break;
        case 'delete-interaction': deleteInteraction(id, name); break;
        default: break;
    }
});

document.getElementById('menuBtn').addEventListener('click', () => {
    document.getElementById('sidebar').classList.toggle('open');
});

/* ======================================================================
   Start
   ====================================================================== */

window.addEventListener('hashchange', router);
checkHealth();
setInterval(checkHealth, 20000);
router();
