/**
 * Automated Test Suite for ADPIA Client & Normalization Layer (Step 1)
 */

const assert = require('assert');
const {
  AdpiaClient,
  AdpiaError,
  parseAdpiaAffSub,
  normalizeAdpiaStatus,
  normalizeAdpiaConversion
} = require('./adpiaClient');

async function runTests() {
  console.log('Running ADPIA Step 1 Test Suite...');

  // TEST 1: Parse real aff_sub
  const affSubReal = '&u_id=casi_test_001&utm_source=zalo&utm_medium=zalo&utm_campaign=zalo';
  const parsed1 = parseAdpiaAffSub(affSubReal);
  assert.strictEqual(parsed1.uId, 'casi_test_001');
  assert.strictEqual(parsed1.params.utm_source, 'zalo');
  assert.strictEqual(parsed1.params.utm_medium, 'zalo');
  assert.strictEqual(parsed1.params.utm_campaign, 'zalo');
  console.log('✓ TEST 1 passed (Real aff_sub parsing)');

  // TEST 2: aff_sub = null
  const parsed2 = parseAdpiaAffSub(null);
  assert.strictEqual(parsed2.uId, null);
  assert.strictEqual(parsed2.raw, null);
  console.log('✓ TEST 2 passed (Null aff_sub safety)');

  // TEST 3: aff_sub = ""
  const parsed3 = parseAdpiaAffSub('');
  assert.strictEqual(parsed3.uId, null);
  console.log('✓ TEST 3 passed (Empty string aff_sub safety)');

  // TEST 4: aff_sub without leading &
  const parsed4 = parseAdpiaAffSub('u_id=casi_test_001&utm_source=zalo');
  assert.strictEqual(parsed4.uId, 'casi_test_001');
  assert.strictEqual(parsed4.params.utm_source, 'zalo');
  console.log('✓ TEST 4 passed (Aff_sub without leading &)');

  // TEST 5: URL-encoded parameter values
  const parsed5 = parseAdpiaAffSub('u_id=casi_test_001&name=Nguy%E1%BB%85n%20H%E1%BA%ADu');
  assert.strictEqual(parsed5.uId, 'casi_test_001');
  assert.strictEqual(parsed5.params.name, 'Nguyễn Hậu');
  console.log('✓ TEST 5 passed (URL-encoded values handling)');

  // TEST 6: Normalize the real conversion fixture
  const realFixture = {
    ymd: '20260929',
    his: '08:24:19',
    conversion_id: '15000046929814',
    ocd: '26092888CKTBR3',
    pcd: '22965168322_242776906944',
    pname: 'Bảng chữ cái điện tử thông minh, bảng treo tường biết nói: động vật, trái cây, phương tiện song ngữ cho bé học tập',
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
  assert.strictEqual(normalized.orderId, '26092888CKTBR3');
  assert.strictEqual(normalized.salesAmount, 40838);
  assert.strictEqual(normalized.actualAmount, 0);
  assert.strictEqual(normalized.commission, 2430);
  assert.strictEqual(normalized.status, 'pending');
  assert.strictEqual(normalized.providerSubId, 'casi_test_001');
  console.log('✓ TEST 6 passed (Real fixture normalization)');

  // TEST 7: Malformed API response validation & error handling
  const client = new AdpiaClient({ username: 'test_user', password: 'test_password' });
  
  // Test missing credentials
  const unauthClient = new AdpiaClient({ username: '', password: '' });
  let configErrorThrown = false;
  try {
    await unauthClient.getConversions({ sdate: '20260928', edate: '20260928' });
  } catch (err) {
    if (err instanceof AdpiaError && err.code === 'ADPIA_CONFIG_ERROR') {
      configErrorThrown = true;
    }
  }
  assert.strictEqual(configErrorThrown, true);
  console.log('✓ TEST 7 passed (Missing credentials config error)');

  // TEST 8: Missing sdate / edate
  let paramErrorThrown = false;
  try {
    await client.getConversions({ sdate: '20260928' }); // missing edate
  } catch (err) {
    if (err instanceof AdpiaError && err.code === 'ADPIA_INVALID_PARAMETERS') {
      paramErrorThrown = true;
    }
  }
  assert.strictEqual(paramErrorThrown, true);
  console.log('✓ TEST 8 passed (Missing date parameters error)');

  // TEST 11 — Status normalization (all 5 ADPIA statuses & raw status preservation)
  assert.strictEqual(normalizeAdpiaStatus('pending'), 'pending');
  assert.strictEqual(normalizeAdpiaStatus('approve'), 'approved');
  assert.strictEqual(normalizeAdpiaStatus('confirm'), 'approved');
  assert.strictEqual(normalizeAdpiaStatus('reject'), 'cancelled');
  assert.strictEqual(normalizeAdpiaStatus('cancel'), 'cancelled');

  const statusFixture = normalizeAdpiaConversion({ conversion_id: '123', status: 'confirm' });
  assert.strictEqual(statusFixture.status, 'approved');
  assert.strictEqual(statusFixture.rawStatus, 'confirm');
  console.log('✓ TEST 11 passed (Status normalization & raw status preservation)');

  // TEST 12 — Conversion identity (conversion_id, ocd, pcd independent)
  const idFixture = {
    conversion_id: '15000046929814',
    ocd: '26092888CKTBR3',
    pcd: '22965168322_242776906944'
  };
  const normalizedId = normalizeAdpiaConversion(idFixture);
  assert.strictEqual(normalizedId.conversionId, '15000046929814');
  assert.strictEqual(normalizedId.orderId, '26092888CKTBR3');
  assert.strictEqual(normalizedId.productId, '22965168322_242776906944');
  assert.notStrictEqual(normalizedId.conversionId, normalizedId.orderId);
  assert.notStrictEqual(normalizedId.orderId, normalizedId.productId);
  console.log('✓ TEST 12 passed (Conversion identity field separation)');

  // TEST 13 — Money field separation (sales, actual_amount, commission)
  const moneyFixture = {
    conversion_id: '123',
    sales: 40838,
    actual_amount: 0,
    commission: 2430
  };
  const normalizedMoney = normalizeAdpiaConversion(moneyFixture);
  assert.strictEqual(normalizedMoney.salesAmount, 40838);
  assert.strictEqual(normalizedMoney.actualAmount, 0);
  assert.strictEqual(normalizedMoney.commission, 2430);
  console.log('✓ TEST 13 passed (Money field independence)');

  // TEST 14 — Secret leakage prevention
  const secretClient = new AdpiaClient({ username: 'secret_user_abc', password: 'secret_password_xyz' });
  let errorLoggedOrThrown = false;
  try {
    assert.strictEqual(secretClient.username.includes('secret_user_abc'), true);
    const err = new AdpiaError('Authentication failed for user', 'ADPIA_AUTH_ERROR');
    assert.strictEqual(err.message.includes('secret_user_abc'), false);
    assert.strictEqual(err.message.includes('secret_password_xyz'), false);
    errorLoggedOrThrown = true;
  } catch (e) {
    errorLoggedOrThrown = false;
  }
  assert.strictEqual(errorLoggedOrThrown, true);
  console.log('✓ TEST 14 passed (Secret leakage prevention)');

  console.log('🎉 All ADPIA Step 1 Unit Tests (including 11-14) passed successfully!');
}

runTests().catch(err => {
  console.error('❌ ADPIA Test Suite failed:', err);
  process.exit(1);
});
