let allPurchases = [];
let suppliersCache = [];
let vehiclesCache = [];

async function loadPurchasesPage() {
  try {
    [suppliersCache, vehiclesCache] = await Promise.all([
      Api.get('/api/suppliers'),
      Api.get('/api/vehicles'),
    ]);
    document.getElementById('pu-supplier').innerHTML = suppliersCache
      .map(s => `<option value="${s.id}">${escapeHtml(s.supplierName)}</option>`).join('');
    document.getElementById('pu-vehicle').innerHTML = '<option value="">None</option>' + vehiclesCache
      .map(v => `<option value="${escapeHtml(v.vehicleNumber)}">${escapeHtml(v.vehicleNumber)}</option>`).join('');
    renderSuppliers(suppliersCache);
    await refreshPurchases();
    await refreshTripSummaries();
  } catch (err) {
    handleError(err);
  }
}

function renderSuppliers(list) {
  const body = document.getElementById('suppliers-body');
  body.innerHTML = list.length ? list.map(s => `
    <tr>
      <td><a href="supplier-detail.html?id=${s.id}">${escapeHtml(s.supplierName)}</a></td>
      <td>${escapeHtml(s.contactPerson || '-')}</td>
      <td>${escapeHtml(s.phoneNumber || '-')}</td>
      <td>${escapeHtml(s.address || '-')}</td>
      <td class="text-right mono">${fmtMoney(s.openingPayableBalance)}</td>
      <td>${escapeHtml(s.notes || '-')}</td>
    </tr>
  `).join('') : '<tr><td colspan="6" class="table-empty">No suppliers yet. Click "Add Supplier" to get started.</td></tr>';
}

async function refreshPurchases() {
  const from = document.getElementById('from-filter').value;
  const to = document.getElementById('to-filter').value;
  let url = '/api/purchases';
  if (from && to) url += `?from=${from}&to=${to}`;
  try {
    allPurchases = await Api.get(url);
    renderPurchases(allPurchases);
  } catch (err) {
    handleError(err);
  }
}

function renderPurchases(list) {
  const admin = isAdmin();
  const body = document.getElementById('purchases-body');
  body.innerHTML = list.length ? list.map(p => `
    <tr>
      <td>${fmtDate(p.purchaseDate)}</td>
      <td><a href="supplier-detail.html?id=${p.supplierId}">${escapeHtml(p.supplierName)}</a></td>
      <td>${escapeHtml(p.vehicleNumber || '-')}</td>
      <td>${p.numberOfBirds ?? '-'}</td>
      <td>${p.numberOfBoxes ?? '-'}</td>
      <td>${fmtKg(p.purchaseWeight)}</td>
      <td>
        ${admin ? `<button class="btn-outline btn-sm" onclick="editPurchase(${p.id})">Edit</button>
        <button class="btn-danger btn-sm" onclick="deletePurchaseRow(${p.id})">Delete</button>` : ''}
      </td>
    </tr>
  `).join('') : '<tr><td colspan="7" class="table-empty">No purchases found for this period</td></tr>';
}

async function refreshTripSummaries() {
  const from = document.getElementById('from-filter').value;
  const to = document.getElementById('to-filter').value;
  let url = '/api/trips/summary';
  if (from && to) url += `?from=${from}&to=${to}`;
  try {
    const trips = await Api.get(url);
    renderTripSummaries(trips);
  } catch (err) {
    handleError(err);
  }
}

function renderTripSummaries(list) {
  const body = document.getElementById('trips-body');
  body.innerHTML = list.length ? list.map(t => {
    const diff = Number(t.weightDifference || 0);
    return `
    <tr>
      <td>${fmtDate(t.tripDate)}</td>
      <td><strong>${escapeHtml(t.vehicleNumber)}</strong></td>
      <td>${escapeHtml(t.companyName || '-')}</td>
      <td class="text-right">${t.customerCount}</td>
      <td class="text-right">${t.totalLoadedWeight != null ? fmtKg(t.totalLoadedWeight) : '-'}</td>
      <td class="text-right">${fmtKg(t.totalDeliveredWeight)}</td>
      <td class="text-right ${diff !== 0 ? (diff > 0 ? 'text-red' : 'text-green') : ''}">${t.totalLoadedWeight != null ? fmtKg(diff) : '-'}</td>
      <td class="text-right mono">${fmtMoney(t.totalSalesAmount)}</td>
    </tr>
  `;
  }).join('') : '<tr><td colspan="8" class="table-empty">No trips recorded yet</td></tr>';
}

function resetPurchaseForm() {
  document.getElementById('purchase-form').reset();
  document.getElementById('pu-id').value = '';
  document.getElementById('purchase-modal-title').textContent = 'Record Purchase';
  document.getElementById('pu-date').value = todayIso();
  document.getElementById('purchase-form-error').innerHTML = '';
}

document.getElementById('add-purchase-btn').addEventListener('click', () => {
  resetPurchaseForm();
  openModal('purchase-modal');
});

function editPurchase(id) {
  const p = allPurchases.find(x => x.id === id);
  if (!p) return;
  document.getElementById('purchase-modal-title').textContent = 'Edit Purchase';
  document.getElementById('pu-id').value = p.id;
  document.getElementById('pu-supplier').value = p.supplierId;
  document.getElementById('pu-date').value = p.purchaseDate;
  document.getElementById('pu-birds').value = p.numberOfBirds ?? '';
  document.getElementById('pu-boxes').value = p.numberOfBoxes ?? '';
  document.getElementById('pu-weight').value = p.purchaseWeight;
  document.getElementById('pu-vehicle').value = p.vehicleNumber || '';
  document.getElementById('pu-notes').value = p.notes || '';
  document.getElementById('purchase-form-error').innerHTML = '';
  openModal('purchase-modal');
}

document.getElementById('purchase-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const id = document.getElementById('pu-id').value;
  const vehicleNumber = document.getElementById('pu-vehicle').value;
  const payload = {
    supplierId: Number(document.getElementById('pu-supplier').value),
    purchaseDate: document.getElementById('pu-date').value,
    numberOfBirds: document.getElementById('pu-birds').value ? Number(document.getElementById('pu-birds').value) : null,
    numberOfBoxes: document.getElementById('pu-boxes').value ? Number(document.getElementById('pu-boxes').value) : null,
    purchaseWeight: Number(document.getElementById('pu-weight').value),
    notes: document.getElementById('pu-notes').value.trim(),
    createdBy: 'admin',
    trip: vehicleNumber ? { vehicleNumber } : null,
  };
  const errorBox = document.getElementById('purchase-form-error');
  errorBox.innerHTML = '';
  const btn = document.getElementById('purchase-save-btn');
  btn.disabled = true;
  try {
    if (id) {
      await Api.put(`/api/purchases/${id}`, payload);
      showToast('Purchase updated');
    } else {
      await Api.post('/api/purchases', payload);
      showToast('Purchase recorded');
    }
    closeModal('purchase-modal');
    await refreshPurchases();
    await refreshTripSummaries();
  } catch (err) {
    errorBox.innerHTML = `<div class="alert alert-error">${escapeHtml(err.message)}</div>`;
  } finally {
    btn.disabled = false;
  }
});

async function deletePurchaseRow(id) {
  if (!confirm("Permanently delete this purchase? This updates the supplier's ledger balance. This cannot be undone.")) return;
  try {
    await Api.del(`/api/purchases/${id}`);
    showToast('Purchase deleted');
    await refreshPurchases();
    await refreshTripSummaries();
  } catch (err) { handleError(err); }
}

// ---------- Add Supplier ----------
document.getElementById('add-supplier-btn').addEventListener('click', () => {
  document.getElementById('supplier-form').reset();
  document.getElementById('s-opening-balance').value = 0;
  document.getElementById('supplier-form-error').innerHTML = '';
  openModal('supplier-modal');
});

document.getElementById('supplier-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const payload = {
    supplierName: document.getElementById('s-name').value.trim(),
    contactPerson: document.getElementById('s-contact').value.trim(),
    phoneNumber: document.getElementById('s-phone').value.trim(),
    address: document.getElementById('s-address').value.trim(),
    openingPayableBalance: Number(document.getElementById('s-opening-balance').value || 0),
    status: 'ACTIVE',
    notes: document.getElementById('s-notes').value.trim(),
  };
  const errorBox = document.getElementById('supplier-form-error');
  errorBox.innerHTML = '';
  const btn = document.getElementById('supplier-save-btn');
  btn.disabled = true;
  try {
    await Api.post('/api/suppliers', payload);
    showToast('Supplier added');
    closeModal('supplier-modal');
    await loadPurchasesPage();
  } catch (err) {
    errorBox.innerHTML = `<div class="alert alert-error">${escapeHtml(err.message)}</div>`;
  } finally {
    btn.disabled = false;
  }
});

document.getElementById('apply-filter-btn').addEventListener('click', () => {
  refreshPurchases();
  refreshTripSummaries();
});

document.addEventListener('DOMContentLoaded', () => {
  setTimeout(loadPurchasesPage, 50);
});
