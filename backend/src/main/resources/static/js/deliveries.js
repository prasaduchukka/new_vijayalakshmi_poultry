let allDeliveries = [];
let deliverySession = []; // running list saved this modal session (Save & Next)

async function loadDeliveriesPage() {
  try {
    const [customers, vehicles] = await Promise.all([
      Api.get('/api/customers'),
      Api.get('/api/vehicles'),
    ]);
    const options = customers.map(c => `<option value="${c.id}">${escapeHtml(c.chickenCenterName)}</option>`).join('');
    document.getElementById('d-customer').innerHTML = options;
    document.getElementById('p-customer').innerHTML = options;
    document.getElementById('d-vehicle').innerHTML = '<option value="">None</option>' + vehicles
      .map(v => `<option value="${escapeHtml(v.vehicleNumber)}">${escapeHtml(v.vehicleNumber)}</option>`).join('');
    await refreshDeliveries();
  } catch (err) {
    handleError(err);
  }
}

async function refreshDeliveries() {
  const from = document.getElementById('from-filter').value;
  const to = document.getElementById('to-filter').value;
  let url = '/api/deliveries';
  if (from && to) url += `?from=${from}&to=${to}`;
  try {
    allDeliveries = await Api.get(url);
    renderDeliveries(allDeliveries);
  } catch (err) {
    handleError(err);
  }
}

function renderDeliveries(list) {
  const admin = isAdmin();
  const body = document.getElementById('deliveries-body');
  body.innerHTML = list.length ? list.map(d => `
    <tr>
      <td>${fmtDate(d.deliveryDate)}</td>
      <td><a href="customer-detail.html?id=${d.customerId}">${escapeHtml(d.customerName)}</a></td>
      <td>${escapeHtml(d.vehicleNumber || '-')}</td>
      <td>${d.numberOfBirds ?? '-'}</td>
      <td>${fmtKg(d.dispatchWeight)}</td>
      <td>${fmtMoney(d.sellingRate)}</td>
      <td class="text-right mono">${fmtMoney(d.salesAmount)}</td>
      <td>
        ${admin ? `<button class="btn-outline btn-sm" onclick="editDelivery(${d.id})">Edit</button>
        <button class="btn-danger btn-sm" onclick="deleteDelivery(${d.id})">Delete</button>` : ''}
      </td>
    </tr>
  `).join('') : '<tr><td colspan="8" class="table-empty">No deliveries found for this period</td></tr>';

  const total = list.reduce((sum, d) => sum + Number(d.salesAmount), 0);
  const totalLine = document.getElementById('total-line');
  if (list.length) {
    totalLine.style.display = 'flex';
    totalLine.innerHTML = `<span>Total Sales</span><span class="mono">${fmtMoney(total)}</span>`;
  } else {
    totalLine.style.display = 'none';
  }
}

async function deleteDelivery(id) {
  if (!confirm("Permanently delete this delivery? This will also update the customer's ledger balance. This cannot be undone.")) return;
  try {
    await Api.del(`/api/deliveries/${id}`);
    showToast('Delivery deleted');
    await refreshDeliveries();
  } catch (err) {
    handleError(err);
  }
}

function recalcDeliveryPreview() {
  const dispatch = Number(document.getElementById('d-dispatch').value || 0);
  const rate = Number(document.getElementById('d-rate').value || 0);
  document.getElementById('d-sales-amount').value = '\u20b9 ' + (dispatch * rate).toFixed(2);
}
['d-dispatch', 'd-rate'].forEach(id =>
  document.getElementById(id).addEventListener('input', recalcDeliveryPreview));

document.getElementById('d-payment-toggle').addEventListener('change', (e) => {
  document.getElementById('d-payment-fields').style.display = e.target.checked ? '' : 'none';
});

function recalcMileage() {
  const distance = Number(document.getElementById('d-distance').value || 0);
  const fuel = Number(document.getElementById('d-fuel').value || 0);
  document.getElementById('d-mileage').value = fuel > 0 ? (distance / fuel).toFixed(2) : '';
}
['d-distance', 'd-fuel'].forEach(id =>
  document.getElementById(id).addEventListener('input', recalcMileage));

function clearDeliveryEntryFields() {
  // Clears only the per-stop fields, NOT the sticky vehicle/trip section above.
  document.getElementById('d-birds').value = '';
  document.getElementById('d-dispatch').value = '';
  document.getElementById('d-rate').value = '';
  document.getElementById('d-sales-amount').value = '';
  document.getElementById('d-notes').value = '';
  document.getElementById('d-payment-toggle').checked = false;
  document.getElementById('d-payment-fields').style.display = 'none';
  document.getElementById('d-payment-amount').value = '';
  document.getElementById('d-payment-reference').value = '';
}

document.getElementById('add-delivery-btn').addEventListener('click', () => {
  deliverySession = [];
  renderDeliverySession();
  document.getElementById('delivery-form').reset();
  document.getElementById('d-id').value = '';
  document.getElementById('delivery-modal-title').textContent = 'Record Delivery';
  document.getElementById('d-date').value = todayIso();
  document.getElementById('d-mileage').value = '';
  document.getElementById('d-payment-fields').style.display = 'none';
  document.getElementById('delivery-form-error').innerHTML = '';
  openModal('delivery-modal');
});

function editDelivery(id) {
  const d = allDeliveries.find(x => x.id === id);
  if (!d) return;
  deliverySession = [];
  renderDeliverySession();
  document.getElementById('delivery-modal-title').textContent = 'Edit Delivery';
  document.getElementById('d-id').value = d.id;
  document.getElementById('d-customer').value = d.customerId;
  document.getElementById('d-date').value = d.deliveryDate;
  document.getElementById('d-birds').value = d.numberOfBirds ?? '';
  document.getElementById('d-dispatch').value = d.dispatchWeight;
  document.getElementById('d-rate').value = d.sellingRate;
  document.getElementById('d-sales-amount').value = fmtMoney(d.salesAmount);
  document.getElementById('d-notes').value = d.notes || '';
  document.getElementById('d-vehicle').value = d.vehicleNumber || '';
  document.getElementById('d-company').value = d.companyName || '';
  document.getElementById('d-total-weight').value = d.totalWeightDispatched ?? '';
  document.getElementById('d-driver').value = d.driverName || '';
  document.getElementById('d-helper').value = d.helperName || '';
  document.getElementById('d-distance').value = d.distanceKm ?? '';
  document.getElementById('d-mileage').value = d.mileage ?? '';
  document.getElementById('d-fuel').value = (d.distanceKm && d.mileage) ? (Number(d.distanceKm) / Number(d.mileage)).toFixed(2) : '';
  document.getElementById('d-payment-toggle').checked = false;
  document.getElementById('d-payment-fields').style.display = 'none';
  document.getElementById('delivery-form-error').innerHTML = '';
  openModal('delivery-modal');
}

function renderDeliverySession() {
  const box = document.getElementById('delivery-session-list');
  if (!deliverySession.length) { box.innerHTML = ''; return; }
  const total = deliverySession.reduce((s, d) => s + d.amount, 0);
  box.innerHTML = `
    <div class="card" style="background:var(--green-100); border-color:#cfe3d4; padding:10px 14px; margin-bottom:14px;">
      <div style="font-size:12.5px; font-weight:700; color:var(--green-800); margin-bottom:6px;">
        Recorded this session (${deliverySession.length})
      </div>
      ${deliverySession.map(d => `<div class="summary-line"><span>${escapeHtml(d.customerName)}</span><span class="mono">${fmtMoney(d.amount)}</span></div>`).join('')}
      <div class="summary-line total"><span>Total</span><span class="mono">${fmtMoney(total)}</span></div>
    </div>
  `;
}

function buildDeliveryPayload() {
  const customerSelect = document.getElementById('d-customer');
  const vehicleNumber = document.getElementById('d-vehicle').value.trim();
  const payload = {
    customerId: Number(customerSelect.value),
    deliveryDate: document.getElementById('d-date').value,
    numberOfBirds: document.getElementById('d-birds').value ? Number(document.getElementById('d-birds').value) : null,
    dispatchWeight: Number(document.getElementById('d-dispatch').value),
    sellingRate: Number(document.getElementById('d-rate').value),
    notes: document.getElementById('d-notes').value.trim(),
    createdBy: 'admin',
    trip: vehicleNumber ? {
      vehicleNumber,
      companyName: document.getElementById('d-company').value.trim(),
      totalWeightDispatched: document.getElementById('d-total-weight').value ? Number(document.getElementById('d-total-weight').value) : null,
      driverName: document.getElementById('d-driver').value.trim(),
      helperName: document.getElementById('d-helper').value.trim(),
      distanceKm: document.getElementById('d-distance').value ? Number(document.getElementById('d-distance').value) : null,
      mileage: document.getElementById('d-mileage').value ? Number(document.getElementById('d-mileage').value) : null,
    } : null,
  };
  if (document.getElementById('d-payment-toggle').checked && document.getElementById('d-payment-amount').value) {
    payload.paymentAmount = Number(document.getElementById('d-payment-amount').value);
    payload.paymentMethod = document.getElementById('d-payment-method').value;
    payload.paymentReferenceNumber = document.getElementById('d-payment-reference').value.trim();
  }
  return { payload, customerName: customerSelect.options[customerSelect.selectedIndex].text };
}

async function saveCurrentDelivery() {
  const { payload, customerName } = buildDeliveryPayload();
  const saved = await Api.post('/api/deliveries', payload);
  deliverySession.push({ customerName, amount: saved.salesAmount });
  return saved;
}

// Save & Next: keep the modal open, clear only the per-stop fields, move to the next one.
document.getElementById('delivery-save-next-btn').addEventListener('click', async () => {
  const errorBox = document.getElementById('delivery-form-error');
  errorBox.innerHTML = '';
  const btn = document.getElementById('delivery-save-next-btn');
  btn.disabled = true;
  try {
    await saveCurrentDelivery();
    renderDeliverySession();
    showToast('Delivery saved \u2014 ready for the next stop');
    clearDeliveryEntryFields();
    document.getElementById('d-customer').focus();
  } catch (err) {
    errorBox.innerHTML = `<div class="alert alert-error">${escapeHtml(err.message)}</div>`;
  } finally {
    btn.disabled = false;
  }
});

// Save & Finish: save (create) or update (edit), then close.
document.getElementById('delivery-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const errorBox = document.getElementById('delivery-form-error');
  errorBox.innerHTML = '';
  const btn = document.getElementById('delivery-save-btn');
  btn.disabled = true;
  const id = document.getElementById('d-id').value;
  try {
    if (id) {
      const { payload } = buildDeliveryPayload();
      await Api.put(`/api/deliveries/${id}`, payload);
      showToast('Delivery updated');
    } else {
      await saveCurrentDelivery();
      showToast(`${deliverySession.length} deliver${deliverySession.length > 1 ? 'ies' : 'y'} recorded`);
    }
    closeModal('delivery-modal');
    await refreshDeliveries();
  } catch (err) {
    errorBox.innerHTML = `<div class="alert alert-error">${escapeHtml(err.message)}</div>`;
  } finally {
    btn.disabled = false;
  }
});

document.getElementById('apply-filter-btn').addEventListener('click', refreshDeliveries);

// ---------- Record Payment (unrelated to the delivery batch above) ----------
document.getElementById('add-payment-btn').addEventListener('click', () => {
  document.getElementById('payment-form').reset();
  document.getElementById('p-date').value = todayIso();
  document.getElementById('payment-form-error').innerHTML = '';
  openModal('payment-modal');
});

document.getElementById('payment-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const payload = {
    customerId: Number(document.getElementById('p-customer').value),
    paymentDate: document.getElementById('p-date').value,
    amount: Number(document.getElementById('p-amount').value),
    paymentMethod: document.getElementById('p-method').value,
    referenceNumber: document.getElementById('p-reference').value.trim(),
    notes: document.getElementById('p-notes').value.trim(),
    createdBy: 'admin',
  };
  const btn = document.getElementById('payment-save-btn');
  btn.disabled = true;
  try {
    await Api.post('/api/customer-payments', payload);
    showToast('Payment recorded');
    closeModal('payment-modal');
  } catch (err) {
    document.getElementById('payment-form-error').innerHTML =
      `<div class="alert alert-error">${escapeHtml(err.message)}</div>`;
  } finally {
    btn.disabled = false;
  }
});

document.addEventListener('DOMContentLoaded', () => {
  setTimeout(loadDeliveriesPage, 50);
});
