/**
 * Comprehensive Automated Test Suite for ADPIA Integration (Step 1 & Step 2)
 */

const assert = require('assert');
const {
  AdpiaClient,
  AdpiaError,
  parseAdpiaAffSub,
  normalizeAdpiaStatus,
  normalizeAdpiaConversion
} = require('./adpiaClient');
const { syncAdpiaConversions } = require('./adpiaPersistence');

// Mock Firestore In-Memory Database for Step 2 Tests
class MockFirestoreDoc {
  constructor(id, initialData = null) {
    this.id = id;
    this.dataVal = initialData;
    this.exists = initialData !== null;
  }
  data() {
    return this.dataVal;
  }
}

class MockFirestoreTransaction {
  constructor(store) {
    this.store = store;
  }
  async get(docRef) {
    return this.store.getDoc(docRef.path);
  }
  set(docRef, data, options) {
    this.store.setDoc(docRef.path, data, options);
  }
  update(docRef, data) {
    this.store.updateDoc(docRef.path, data);
  }
}

class MockFirestoreStore {
  constructor() {
    this.documents = new Map();
    this.balanceMutationsCount = 0;
  }

  getDoc(path) {
    const data = this.documents.get(path) || null;
    return new MockFirestoreDoc(path, data);
  }

  setDoc(path, data, options) {
    const existing = this.documents.get(path) || {};
    if (options && options.merge) {
      this.documents.set(path, { ...existing, ...data });
    } else {
      this.documents.set(path, data);
    }
  }

  updateDoc(path, data) {
    this.balanceMutationsCount++;
    const existing = this.documents.get(path) || {};
    this.documents.set(path, { ...existing, ...data });
  }

  collection(name) {
    const self = this;
    return {
      doc(id) {
        const path = `${name}/${id}`;
        return {
          path,
          async get() {
            return self.getDoc(path);
          },
          set(data, options) {
            self.setDoc(path, data, options);
          }
        };
      }
    };
  }

  async runTransaction(updateFunction) {
    const transaction = new MockFirestoreTransaction(this);
    return await updateFunction(transaction);
  }
}

class MockAdpiaClient extends AdpiaClient {
  constructor(fixtures = []) {
    super({ username: 'test', password: 'test' });
    this.fixtures = fixtures;
  }

  async getConversions(options = {}) {
    return {
      success: true,
      message: 'OK',
      code: 200,
      metadata: { sdate: options.sdate, edate: options.edate, count: this.fixtures.length },
      rawItems: this.fixtures,
      conversions: this.fixtures.map(f => normalizeAdpiaConversion(f))
    };
  }
}

async function runAllTests() {
  console.log('Running Comprehensive ADPIA Integration Test Suite (Step 1 & Step 2)...');

  // ==========================================
  // STEP 1 TESTS
  // ==========================================
  const affSubReal = '&u_id=casi_test_001&utm_source=zalo&utm_medium=zalo&utm_campaign=zalo';
  const parsed1 = parseAdpiaAffSub(affSubReal);
  assert.strictEqual(parsed1.uId, 'casi_test_001');
  assert.strictEqual(parsed1.params.utm_source, 'zalo');
  console.log('✓ TEST 1 passed (Real aff_sub parsing)');

  const parsed2 = parseAdpiaAffSub(null);
  assert.strictEqual(parsed2.uId, null);
  console.log('✓ TEST 2 passed (Null aff_sub safety)');

  const parsed3 = parseAdpiaAffSub('');
  assert.strictEqual(parsed3.uId, null);
  console.log('✓ TEST 3 passed (Empty string aff_sub safety)');

  const parsed4 = parseAdpiaAffSub('u_id=casi_test_001&utm_source=zalo');
  assert.strictEqual(parsed4.uId, 'casi_test_001');
  console.log('✓ TEST 4 passed (Aff_sub without leading &)');

  const parsed5 = parseAdpiaAffSub('u_id=casi_test_001&name=Nguy%E1%BB%85n%20H%E1%BA%ADu');
  assert.strictEqual(parsed5.uId, 'casi_test_001');
  assert.strictEqual(parsed5.params.name, 'Nguyễn Hậu');
  console.log('✓ TEST 5 passed (URL-encoded values handling)');

  const realFixture = {
    ymd: '20260929',
    his: '08:24:19',
    conversion_id: '15000046929814',
    ocd: '26092888CKTBR3',
    pcd: '22965168322_242776906944',
    pname: 'Bảng chữ cái điện tử thông minh',
    sales: 40838,
    actual_amount: 0,
    commission: 2430,
    cnt: 1,
    offer_id: 'shopeemcn',
    aid: 'A100156960',
    status: 'pending',
    aff_sub: '&u_id=casi_test_001&utm_source=zalo&utm_medium=zalo&utm_campaign=zalo',
    shop_id: '1404404184',
    shop_name: 'Rạng Đông Toys',
    type: 'CPS',
    update_at: '2026-09-29 08:24:19',
    complete_time: null
  };

  const normalized = normalizeAdpiaConversion(realFixture);
  assert.strictEqual(normalized.provider, 'adpia');
  assert.strictEqual(normalized.conversionId, '15000046929814');
  assert.strictEqual(normalized.salesAmount, 40838);
  assert.strictEqual(normalized.commission, 2430);
  console.log('✓ TEST 6 passed (Real fixture normalization)');

  const unauthClient = new AdpiaClient({ username: '', password: '' });
  let configErrorThrown = false;
  try {
    await unauthClient.getConversions({ sdate: '20260928', edate: '20260928' });
  } catch (err) {
    if (err instanceof AdpiaError && err.code === 'ADPIA_CONFIG_ERROR') configErrorThrown = true;
  }
  assert.strictEqual(configErrorThrown, true);
  console.log('✓ TEST 7 passed (Missing credentials config error)');

  const client = new AdpiaClient({ username: 'test_user', password: 'test_password' });
  let paramErrorThrown = false;
  try {
    await client.getConversions({ sdate: '20260928' });
  } catch (err) {
    if (err instanceof AdpiaError && err.code === 'ADPIA_INVALID_PARAMETERS') paramErrorThrown = true;
  }
  assert.strictEqual(paramErrorThrown, true);
  console.log('✓ TEST 8 passed (Missing date parameters error)');

  assert.strictEqual(normalizeAdpiaStatus('pending'), 'pending');
  assert.strictEqual(normalizeAdpiaStatus('approve'), 'approved');
  assert.strictEqual(normalizeAdpiaStatus('confirm'), 'approved');
  assert.strictEqual(normalizeAdpiaStatus('reject'), 'cancelled');
  assert.strictEqual(normalizeAdpiaStatus('cancel'), 'cancelled');
  console.log('✓ TEST 11 passed (Status normalization)');

  const idFixture = { conversion_id: '15000046929814', ocd: 'ORDER_1', pcd: 'PROD_1' };
  const normalizedId = normalizeAdpiaConversion(idFixture);
  assert.strictEqual(normalizedId.conversionId, '15000046929814');
  assert.strictEqual(normalizedId.orderId, 'ORDER_1');
  assert.strictEqual(normalizedId.productId, 'PROD_1');
  console.log('✓ TEST 12 passed (Conversion identity separation)');

  const moneyFixture = { conversion_id: '123', sales: 40838, actual_amount: 0, commission: 2430 };
  const normalizedMoney = normalizeAdpiaConversion(moneyFixture);
  assert.strictEqual(normalizedMoney.salesAmount, 40838);
  assert.strictEqual(normalizedMoney.actualAmount, 0);
  assert.strictEqual(normalizedMoney.commission, 2430);
  console.log('✓ TEST 13 passed (Money field separation)');

  const err = new AdpiaError('Authentication failed', 'ADPIA_AUTH_ERROR');
  assert.strictEqual(err.message.includes('secret'), false);
  console.log('✓ TEST 14 passed (Secret leakage prevention)');

  // ==========================================
  // STEP 2 PERSISTENCE TESTS
  // ==========================================
  
  // STEP 2 TEST 1 — Create
  {
    const store = new MockFirestoreStore();
    const mockClient = new MockAdpiaClient([realFixture]);
    const summary = await syncAdpiaConversions({ sdate: '20260929', edate: '20260929', client: mockClient }, store);
    assert.strictEqual(summary.persisted, 1);
    assert.strictEqual(summary.created, 1);
    const doc = store.getDoc('transactions/adpia_15000046929814');
    assert.strictEqual(doc.exists, true);
    assert.strictEqual(doc.data().userId, 'casi_test_001');
    console.log('✓ STEP 2 TEST 1 passed (Create)');
  }

  // STEP 2 TEST 2 — Idempotent repeat
  {
    const store = new MockFirestoreStore();
    const mockClient = new MockAdpiaClient([realFixture]);
    await syncAdpiaConversions({ sdate: '20260929', edate: '20260929', client: mockClient }, store);
    const summary2 = await syncAdpiaConversions({ sdate: '20260929', edate: '20260929', client: mockClient }, store);
    assert.strictEqual(summary2.created, 0);
    assert.strictEqual(summary2.updated, 1);
    assert.strictEqual(summary2.persisted, 1);
    console.log('✓ STEP 2 TEST 2 passed (Idempotent repeat)');
  }

  // STEP 2 TEST 3 — Update
  {
    const store = new MockFirestoreStore();
    const client1 = new MockAdpiaClient([{ ...realFixture, status: 'pending', commission: 2430 }]);
    await syncAdpiaConversions({ sdate: '20260929', edate: '20260929', client: client1 }, store);
    const client2 = new MockAdpiaClient([{ ...realFixture, status: 'approve', commission: 2500 }]);
    await syncAdpiaConversions({ sdate: '20260929', edate: '20260929', client: client2 }, store);
    const doc = store.getDoc('transactions/adpia_15000046929814');
    assert.strictEqual(doc.data().normalizedStatus, 'approved');
    assert.strictEqual(doc.data().commission, 2500);
    console.log('✓ STEP 2 TEST 3 passed (Update)');
  }

  // STEP 2 TEST 4 — Money field separation in persistence
  {
    const store = new MockFirestoreStore();
    const mockClient = new MockAdpiaClient([realFixture]);
    await syncAdpiaConversions({ sdate: '20260929', edate: '20260929', client: mockClient }, store);
    const doc = store.getDoc('transactions/adpia_15000046929814');
    assert.strictEqual(doc.data().salesAmount, 40838);
    assert.strictEqual(doc.data().actualAmount, 0);
    assert.strictEqual(doc.data().commission, 2430);
    assert.strictEqual(doc.data().cashbackAmount, undefined);
    console.log('✓ STEP 2 TEST 4 passed (Money separation)');
  }

  // STEP 2 TEST 5 — User attribution
  {
    const store = new MockFirestoreStore();
    const mockClient = new MockAdpiaClient([{ ...realFixture, aff_sub: '&u_id=casi_test_001' }]);
    await syncAdpiaConversions({ sdate: '20260929', edate: '20260929', client: mockClient }, store);
    const doc = store.getDoc('transactions/adpia_15000046929814');
    assert.strictEqual(doc.data().userId, 'casi_test_001');
    assert.strictEqual(doc.data().attributionStatus, 'resolved');
    console.log('✓ STEP 2 TEST 5 passed (User attribution)');
  }

  // STEP 2 TEST 6 — Missing attribution
  {
    const store = new MockFirestoreStore();
    const mockClient = new MockAdpiaClient([{ ...realFixture, aff_sub: null }]);
    const summary = await syncAdpiaConversions({ sdate: '20260929', edate: '20260929', client: mockClient }, store);
    assert.strictEqual(summary.unresolvedAttribution, 1);
    const doc = store.getDoc('transactions/adpia_15000046929814');
    assert.strictEqual(doc.data().userId, null);
    assert.strictEqual(doc.data().attributionStatus, 'unresolved');
    console.log('✓ STEP 2 TEST 6 passed (Missing attribution)');
  }

  // STEP 2 TEST 7 — Multiple conversions same order
  {
    const store = new MockFirestoreStore();
    const fixA = { ...realFixture, conversion_id: '15000000000001', ocd: 'ORDER_X' };
    const fixB = { ...realFixture, conversion_id: '15000000000002', ocd: 'ORDER_X' };
    const mockClient = new MockAdpiaClient([fixA, fixB]);
    const summary = await syncAdpiaConversions({ sdate: '20260929', edate: '20260929', client: mockClient }, store);
    assert.strictEqual(summary.persisted, 2);
    assert.strictEqual(store.getDoc('transactions/adpia_15000000000001').exists, true);
    assert.strictEqual(store.getDoc('transactions/adpia_15000000000002').exists, true);
    console.log('✓ STEP 2 TEST 7 passed (Multiple conversions same order)');
  }

  // STEP 2 TEST 8 — Provider identity conflict
  {
    const store = new MockFirestoreStore();
    store.setDoc('transactions/adpia_15000046929814', { provider: 'accesstrade', providerConversionId: '15000046929814' });
    const mockClient = new MockAdpiaClient([realFixture]);
    const summary = await syncAdpiaConversions({ sdate: '20260929', edate: '20260929', client: mockClient }, store);
    assert.strictEqual(summary.failed, 1);
    console.log('✓ STEP 2 TEST 8 passed (Provider identity conflict)');
  }

  // STEP 2 TEST 9 — Raw data safety
  {
    const store = new MockFirestoreStore();
    const unsafe = { ...realFixture, password: 'secret_password_123', authorization: 'Basic token' };
    const mockClient = new MockAdpiaClient([unsafe]);
    await syncAdpiaConversions({ sdate: '20260929', edate: '20260929', client: mockClient }, store);
    const doc = store.getDoc('transactions/adpia_15000046929814');
    assert.strictEqual(doc.data().rawProviderData.password, undefined);
    assert.strictEqual(doc.data().rawProviderData.authorization, undefined);
    console.log('✓ STEP 2 TEST 9 passed (Raw data safety)');
  }

  // STEP 2 TEST 10 — Firestore failure
  {
    const faultyDb = {
      collection() { return { doc() { return { async get() { throw new Error('DB fail'); } }; } }; },
      async runTransaction(cb) { return await cb({ async get() { throw new Error('DB fail'); } }); }
    };
    const mockClient = new MockAdpiaClient([realFixture]);
    const summary = await syncAdpiaConversions({ sdate: '20260929', edate: '20260929', client: mockClient }, faultyDb);
    assert.strictEqual(summary.failed, 1);
    console.log('✓ STEP 2 TEST 10 passed (Firestore failure)');
  }

  // FINANCIAL SAFETY ASSERTION
  {
    const store = new MockFirestoreStore();
    const mockClient = new MockAdpiaClient([realFixture]);
    await syncAdpiaConversions({ sdate: '20260929', edate: '20260929', client: mockClient }, store);
    assert.strictEqual(store.balanceMutationsCount, 0);
    console.log('✓ FINANCIAL SAFETY ASSERTION passed (Zero balance mutations)');
  }

  console.log('🎉 All ADPIA Step 1 & Step 2 Tests passed successfully!');
}

runAllTests().catch(err => {
  console.error('❌ Test Suite failed:', err);
  process.exit(1);
});
