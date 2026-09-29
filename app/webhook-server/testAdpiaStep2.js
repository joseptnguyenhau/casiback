/**
 * Automated Test Suite for ADPIA Persistence Layer (Step 2)
 */

const assert = require('assert');
const { syncAdpiaConversions } = require('./adpiaPersistence');
const { AdpiaClient } = require('./adpiaClient');

// Mock Firestore In-Memory Database for robust unit testing
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
    this.balanceMutationsCount = 0; // Financial safety tracker
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

// Mock ADPIA Client returning test fixtures
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
      conversions: this.fixtures.map(f => {
        // use normalization from adpiaClient
        const { normalizeAdpiaConversion } = require('./adpiaClient');
        return normalizeAdpiaConversion(f);
      })
    };
  }
}

async function runStep2Tests() {
  console.log('Running ADPIA Step 2 Persistence Test Suite...');

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

  // TEST 1 — Create
  {
    const store = new MockFirestoreStore();
    const client = new MockAdpiaClient([realFixture]);
    const summary = await syncAdpiaConversions({ sdate: '20260929', edate: '20260929', client }, store);
    
    assert.strictEqual(summary.fetched, 1);
    assert.strictEqual(summary.persisted, 1);
    assert.strictEqual(summary.created, 1);
    assert.strictEqual(summary.updated, 0);

    const doc = store.getDoc('transactions/adpia_15000046929814');
    assert.strictEqual(doc.exists, true);
    assert.strictEqual(doc.data().conversionId, '15000046929814');
    assert.strictEqual(doc.data().userId, 'casi_test_001');
    console.log('✓ TEST 1 passed (Create single transaction)');
  }

  // TEST 2 — Idempotent repeat
  {
    const store = new MockFirestoreStore();
    const client = new MockAdpiaClient([realFixture]);
    await syncAdpiaConversions({ sdate: '20260929', edate: '20260929', client }, store);
    const summary2 = await syncAdpiaConversions({ sdate: '20260929', edate: '20260929', client }, store);

    assert.strictEqual(summary2.fetched, 1);
    assert.strictEqual(summary2.persisted, 1);
    assert.strictEqual(summary2.created, 0);
    assert.strictEqual(summary2.updated, 1);
    console.log('✓ TEST 2 passed (Idempotent repeat upsert)');
  }

  // TEST 3 — Update status and commission without duplication
  {
    const store = new MockFirestoreStore();
    const client1 = new MockAdpiaClient([{ ...realFixture, status: 'pending', commission: 2430 }]);
    await syncAdpiaConversions({ sdate: '20260929', edate: '20260929', client: client1 }, store);

    const client2 = new MockAdpiaClient([{ ...realFixture, status: 'approve', commission: 2500 }]);
    await syncAdpiaConversions({ sdate: '20260929', edate: '20260929', client: client2 }, store);

    const doc = store.getDoc('transactions/adpia_15000046929814');
    assert.strictEqual(doc.data().normalizedStatus, 'approved');
    assert.strictEqual(doc.data().commission, 2500);
    console.log('✓ TEST 3 passed (Update status and commission atomically)');
  }

  // TEST 4 — Money field separation
  {
    const store = new MockFirestoreStore();
    const client = new MockAdpiaClient([realFixture]);
    await syncAdpiaConversions({ sdate: '20260929', edate: '20260929', client }, store);

    const doc = store.getDoc('transactions/adpia_15000046929814');
    assert.strictEqual(doc.data().salesAmount, 40838);
    assert.strictEqual(doc.data().actualAmount, 0);
    assert.strictEqual(doc.data().commission, 2430);
    assert.strictEqual(doc.data().cashbackAmount, undefined); // No cashback calculated
    console.log('✓ TEST 4 passed (Money field separation and independence)');
  }

  // TEST 5 — User attribution
  {
    const store = new MockFirestoreStore();
    const fixtureWithAffSub = { ...realFixture, aff_sub: '&u_id=casi_test_001&utm_source=zalo' };
    const client = new MockAdpiaClient([fixtureWithAffSub]);
    await syncAdpiaConversions({ sdate: '20260929', edate: '20260929', client }, store);

    const doc = store.getDoc('transactions/adpia_15000046929814');
    assert.strictEqual(doc.data().userId, 'casi_test_001');
    assert.strictEqual(doc.data().attributionStatus, 'resolved');
    console.log('✓ TEST 5 passed (User attribution u_id extraction)');
  }

  // TEST 6 — Missing attribution
  {
    const store = new MockFirestoreStore();
    const fixtureNoAffSub = { ...realFixture, aff_sub: null };
    const client = new MockAdpiaClient([fixtureNoAffSub]);
    const summary = await syncAdpiaConversions({ sdate: '20260929', edate: '20260929', client }, store);

    assert.strictEqual(summary.unresolvedAttribution, 1);
    const doc = store.getDoc('transactions/adpia_15000046929814');
    assert.strictEqual(doc.data().userId, null);
    assert.strictEqual(doc.data().attributionStatus, 'unresolved');
    console.log('✓ TEST 6 passed (Missing attribution handling without user assignment)');
  }

  // TEST 7 — Multiple conversions same order
  {
    const store = new MockFirestoreStore();
    const fixA = { ...realFixture, conversion_id: '15000000000001', ocd: 'ORDER_X' };
    const fixB = { ...realFixture, conversion_id: '15000000000002', ocd: 'ORDER_X' };
    const client = new MockAdpiaClient([fixA, fixB]);
    const summary = await syncAdpiaConversions({ sdate: '20260929', edate: '20260929', client }, store);

    assert.strictEqual(summary.persisted, 2);
    assert.strictEqual(store.getDoc('transactions/adpia_15000000000001').exists, true);
    assert.strictEqual(store.getDoc('transactions/adpia_15000000000002').exists, true);
    console.log('✓ TEST 7 passed (Multiple conversions same order coexisting)');
  }

  // TEST 8 — Provider identity conflict
  {
    const store = new MockFirestoreStore();
    // Pre-populate with conflicting provider identity
    store.setDoc('transactions/adpia_15000046929814', {
      provider: 'accesstrade', // conflict
      providerConversionId: '15000046929814'
    });

    const client = new MockAdpiaClient([realFixture]);
    const summary = await syncAdpiaConversions({ sdate: '20260929', edate: '20260929', client }, store);

    assert.strictEqual(summary.failed, 1);
    assert.strictEqual(summary.persisted, 0);
    console.log('✓ TEST 8 passed (Provider identity conflict safe failure)');
  }

  // TEST 9 — Raw data safety
  {
    const store = new MockFirestoreStore();
    const unsafeFixture = {
      ...realFixture,
      password: 'secret_password_123',
      authorization: 'Basic secret_token',
      auth: 'secret'
    };
    const client = new MockAdpiaClient([unsafeFixture]);
    await syncAdpiaConversions({ sdate: '20260929', edate: '20260929', client }, store);

    const doc = store.getDoc('transactions/adpia_15000046929814');
    const rawData = doc.data().rawProviderData;
    assert.strictEqual(rawData.password, undefined);
    assert.strictEqual(rawData.authorization, undefined);
    assert.strictEqual(rawData.auth, undefined);
    console.log('✓ TEST 9 passed (Raw data safety & secret stripping)');
  }

  // TEST 10 — Firestore failure simulation
  {
    const faultyDb = {
      collection() {
        return {
          doc() {
            return {
              async get() { throw new Error('Firestore connection failure'); }
            };
          }
        };
      },
      async runTransaction(cb) {
        return await cb({
          async get() { throw new Error('Firestore connection failure'); }
        });
      }
    };

    const client = new MockAdpiaClient([realFixture]);
    const summary = await syncAdpiaConversions({ sdate: '20260929', edate: '20260929', client }, faultyDb);
    assert.strictEqual(summary.failed, 1);
    assert.strictEqual(summary.persisted, 0);
    console.log('✓ TEST 10 passed (Firestore failure handling)');
  }

  // FINANCIAL SAFETY ASSERTION
  {
    const store = new MockFirestoreStore();
    const client = new MockAdpiaClient([realFixture]);
    await syncAdpiaConversions({ sdate: '20260929', edate: '20260929', client }, store);

    assert.strictEqual(store.balanceMutationsCount, 0);
    console.log('✓ FINANCIAL SAFETY ASSERTION passed (Zero balance mutations)');
  }

  console.log('🎉 All ADPIA Step 2 Persistence Tests passed successfully!');
}

runStep2Tests().catch(err => {
  console.error('❌ ADPIA Step 2 Test Suite failed:', err);
  process.exit(1);
});
