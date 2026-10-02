const express = require('express');
const admin = require('firebase-admin');
const axios = require('axios');
const crypto = require('crypto');

// Khởi tạo Firebase Admin SDK
if (process.env.FIREBASE_SERVICE_ACCOUNT) {
  try {
    const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
    admin.initializeApp({
      credential: admin.credential.cert(serviceAccount)
    });
  } catch (e) {
    console.error('Error parsing FIREBASE_SERVICE_ACCOUNT JSON:', e);
    admin.initializeApp({
      projectId: 'casiback-5b7e2'
    });
  }
} else {
  admin.initializeApp({
    projectId: 'casiback-5b7e2'
  });
}

const db = admin.firestore();
const app = express();
app.get("/health", (req, res) => {
  res.status(200).json({
    status: "ok",
    service: "casi-staging-backend",
    firebaseProject: "casiback-5b7e2"
  });
});

app.get("/api/firestore-health", async (req, res) => {
  try {
    const testDocRef = db.collection("backend_health_tests").doc("health_check_" + Date.now());
    const testData = {
      timestamp: admin.firestore.FieldValue.serverTimestamp(),
      status: "testing_write_read",
      nodeEnv: process.env.NODE_ENV || "development"
    };

    // 1. WRITE test
    await testDocRef.set(testData);

    // 2. READ test
    const docSnap = await testDocRef.get();
    if (!docSnap.exists) {
      throw new Error("Failed to read back test document from Firestore.");
    }

    const readData = docSnap.data();

    res.status(200).json({
      success: true,
      message: "Firestore WRITE and READ verified successfully.",
      collection: "backend_health_tests",
      documentId: testDocRef.id,
      readData: {
        status: readData.status,
        hasTimestamp: !!readData.timestamp
      }
    });
  } catch (err) {
    console.error("Firestore health check failed:", err);
    res.status(500).json({
      success: false,
      error: err.message
    });
  }
});

app.use(express.json());

// Middleware: Verify Firebase Auth ID Token
async function verifyAuthToken(req, res, next) {
  const authHeader = req.headers.authorization;
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return res.status(401).json({ error: 'Unauthorized: Missing or invalid token' });
  }
  const token = authHeader.split('Bearer ')[1];
  try {
    const decodedToken = await admin.auth().verifyIdToken(token);
    req.user = decodedToken;
    next();
  } catch (error) {
    console.error('Auth verification error:', error);
    return res.status(403).json({ error: 'Unauthorized: Invalid token' });
  }
}

// Middleware: Webhook Secret Verification (Fail-closed in production)
function verifyWebhookSecret(req, res, next) {
  const webhookSecret = process.env.WEBHOOK_SECRET;
  if (!webhookSecret) {
    if (process.env.NODE_ENV === 'production') {
      console.error('CRITICAL: WEBHOOK_SECRET is missing in production environment. Webhook rejected.');
      return res.status(401).json({ error: 'Unauthorized: Webhook secret not configured in production' });
    }
    // Non-production fallback if secret not set
    return next();
  }

  const headerSecret = req.headers['x-webhook-secret'] || req.query.secret;
  if (!headerSecret || headerSecret !== webhookSecret) {
    console.warn('Unauthorized webhook attempt with invalid or missing secret');
    return res.status(401).json({ error: 'Unauthorized webhook: Invalid or missing secret' });
  }
  next();
}

// Business Rule: Isolated Cashback Calculation Function
function calculateCashback(commission, transactionData = {}) {
  const comm = Number(commission);
  if (isNaN(comm) || comm < 0) return 0;
  // Business rule hiện tại: 1:1 với hoa hồng Publisher (commission)
  // Có thể mở rộng tỷ lệ/campaign rule tại đây mà không ảnh hưởng state machine
  return Math.round(comm);
}

// Status Mapping Adapter: AccessTrade status → Casi internal status
// AccessTrade: 0 = Pending, 1 = Approved, 2 = Rejected / Cancelled
function mapAccessTradeStatus(status) {
  const s = Number(status);
  if (s === 0) return 'pending';
  if (s === 1) return 'approved';
  if (s === 2) return 'cancelled';
  throw new Error(`Unsupported AccessTrade status code: ${status}`);
}

// User Resolver: aff_sub1 -> aff_sub2 -> aff_sub3 -> aff_sub4 -> sub_id
function resolveUserId(body) {
  const sub1 = body.aff_sub1 || body.affSub1;
  const sub2 = body.aff_sub2 || body.affSub2;
  const sub3 = body.aff_sub3 || body.affSub3;
  const sub4 = body.aff_sub4 || body.affSub4;
  const subId = body.sub_id || body.subId;
  const resolved = (sub1 || sub2 || sub3 || sub4 || subId || '').trim();
  return resolved;
}

app.get('/', (req, res) => {
  res.send('Casi Secure Production Backend & Webhook Server is running! 🚀');
});

// ==========================================
// 1. CREATE AFFILIATE LINK (Trusted Backend)
// ==========================================
app.post('/api/create-affiliate-link', verifyAuthToken, async (req, res) => {
  try {
    const userId = req.user.uid;
    const { originalUrl } = req.body;

    if (!originalUrl || (!originalUrl.includes('shopee.vn') && !originalUrl.includes('shp.ee') && !originalUrl.includes('shope.ee'))) {
      return res.status(400).json({ error: 'Link Shopee không hợp lệ. Vui lòng cung cấp link Shopee chính xác.' });
    }

    const atToken = process.env.ACCESSTRADE_API_TOKEN;
    if (!atToken) {
      console.error('CRITICAL: ACCESSTRADE_API_TOKEN is not configured in environment variables.');
      return res.status(500).json({ error: 'Hệ thống cấu hình AccessTrade chưa sẵn sàng. Vui lòng thử lại sau.' });
    }

    let response;
    try {
      response = await axios.post(
        'https://api.accesstrade.vn/v1/product_link/create',
        {
          urls: [originalUrl],
          utm_source: 'casi_app',
          sub_id: userId
        },
        {
          headers: {
            'Authorization': atToken,
            'Content-Type': 'application/json'
          },
          timeout: 10000
        }
      );
    } catch (atError) {
      console.error('AccessTrade API failure:', atError.message);
      return res.status(502).json({ error: 'Không thể kết nối tới hệ thống AccessTrade. Vui lòng thử lại sau.' });
    }

    const data = response.data?.data;
    let affiliateLink = '';
    let productName = 'Sản phẩm Shopee';

    if (Array.isArray(data) && data.length > 0) {
      affiliateLink = data[0].short_link || data[0].product_link || '';
      productName = data[0].product_name || data[0].title || productName;
    } else if (data && typeof data === 'object') {
      affiliateLink = data.short_link || data.product_link || '';
      productName = data.product_name || productName;
    }

    if (!affiliateLink) {
      return res.status(502).json({ error: 'AccessTrade không trả về link affiliate hợp lệ.' });
    }

    const trackingId = 'TRK_' + crypto.randomUUID();
    const trackingRef = db.collection('tracking').doc(trackingId);

    await trackingRef.set({
      trackingId: trackingId,
      userId: userId,
      originalLink: originalUrl,
      affiliateLink: affiliateLink,
      subId: userId,
      status: 'created',
      createdAt: admin.firestore.FieldValue.serverTimestamp()
    });

    return res.status(200).json({
      success: true,
      affiliateLink: affiliateLink,
      productName: productName
    });
  } catch (error) {
    console.error('Create Affiliate Link Error:', error);
    return res.status(500).json({ error: error.message || 'Internal Server Error' });
  }
});

// ==========================================
// 2. REQUEST WITHDRAWAL (Trusted Backend)
// ==========================================
app.post('/api/request-withdrawal', verifyAuthToken, async (req, res) => {
  try {
    const userId = req.user.uid;
    const { bankName, accountNumber, accountHolder, amount, requestId } = req.body;

    const withdrawalAmount = Number(amount);
    if (!Number.isInteger(withdrawalAmount) || withdrawalAmount < 20000) {
      return res.status(400).json({ error: 'Số tiền rút tối thiểu là 20.000đ và phải là số nguyên.' });
    }

    if (!bankName || !accountNumber || !accountHolder) {
      return res.status(400).json({ error: 'Vui lòng cung cấp đầy đủ thông tin ngân hàng.' });
    }

    const idempotencyKey = requestId || req.headers['x-idempotency-key'];
    const withdrawalId = 'WD_' + crypto.randomUUID();
    const userRef = db.collection('users').doc(userId);
    const withdrawalRef = db.collection('withdrawals').doc(withdrawalId);
    const ledgerRef = db.collection('ledger').doc('LEDGER_' + crypto.randomUUID());

    const maskedAcc = accountNumber.length > 4 ? '******' + accountNumber.slice(-4) : '******';
    console.log(`Processing withdrawal request for user ${userId}, amount: ${withdrawalAmount}, bank: ${bankName}`);

    await db.runTransaction(async (transaction) => {
      if (idempotencyKey) {
        const existingQuery = await transaction.get(
          db.collection('withdrawals').where('userId', '==', userId).where('idempotencyKey', '==', idempotencyKey).limit(1)
        );
        if (!existingQuery.empty) {
          throw new Error('Yêu cầu rút tiền này đã được xử lý trước đó (Idempotent duplicate).');
        }
      }

      const userDoc = await transaction.get(userRef);
      if (!userDoc.exists) {
        throw new Error('Tài khoản người dùng không tồn tại.');
      }

      const userData = userDoc.data();
      const currentAvailable = userData.balanceAvailable || 0;
      const currentPending = userData.balancePending || 0;

      if (currentAvailable < withdrawalAmount) {
        throw new Error('Số dư khả dụng không đủ để rút số tiền này.');
      }

      const newAvailable = currentAvailable - withdrawalAmount;
      if (newAvailable < 0) {
        throw new Error('Phát hiện lỗi logic số dư âm.');
      }

      // 1. Cập nhật số dư user
      transaction.update(userRef, {
        balanceAvailable: newAvailable,
        updatedAt: admin.firestore.FieldValue.serverTimestamp()
      });

      // 2. Tạo withdrawal record
      transaction.set(withdrawalRef, {
        withdrawalId: withdrawalId,
        userId: userId,
        bankName: bankName,
        accountNumber: maskedAcc,
        accountHolder: accountHolder.toUpperCase(),
        amount: withdrawalAmount,
        currency: 'VND',
        status: 'pending',
        idempotencyKey: idempotencyKey || null,
        createdAt: admin.firestore.FieldValue.serverTimestamp()
      });

      // 3. Ghi ledger bất biến (WITHDRAWAL_RESERVED)
      transaction.set(ledgerRef, {
        ledgerId: ledgerRef.id,
        userId: userId,
        withdrawalId: withdrawalId,
        type: 'WITHDRAWAL_RESERVED',
        amount: withdrawalAmount,
        currency: 'VND',
        balancePendingBefore: currentPending,
        balancePendingAfter: currentPending,
        balanceAvailableBefore: currentAvailable,
        balanceAvailableAfter: newAvailable,
        createdAt: admin.firestore.FieldValue.serverTimestamp()
      });
    });

    return res.status(200).json({ success: true, message: 'Yêu cầu rút tiền đã được gửi thành công!' });
  } catch (error) {
    console.error('Request Withdrawal Error:', error);
    return res.status(400).json({ error: error.message || 'Lỗi yêu cầu rút tiền' });
  }
});

// ==========================================
// 3. ACCESSTRADE WEBHOOK (Trusted Backend)
// ==========================================
app.post('/webhook', verifyWebhookSecret, async (req, res) => {
  try {
    const payload = req.body;
    console.log('Received AccessTrade Webhook Payload:', JSON.stringify(payload));

    const rawConversionId = payload.conversion_id || payload.conversionId;
    if (rawConversionId === undefined || rawConversionId === null || String(rawConversionId).trim() === '') {
      return res.status(400).json({ error: 'Missing or invalid conversion_id (Primary transaction identity required)' });
    }
    const conversionId = String(rawConversionId).trim();

    const orderId = String(payload.order_id || payload.orderId || '').trim();
    const transactionId = String(payload.transaction_id || payload.transactionId || '').trim();
    const userId = resolveUserId(payload);

    if (!userId) {
      return res.status(400).json({ error: 'Missing or invalid aff_sub / sub_id (User resolution failed)' });
    }

    const rawCommission = payload.commission;
    if (rawCommission === undefined || rawCommission === null || (typeof rawCommission !== 'number' && typeof rawCommission !== 'string')) {
      return res.status(400).json({ error: 'Missing or invalid commission (payload.commission required exclusively)' });
    }
    const commission = Number(rawCommission);
    if (isNaN(commission) || !isFinite(commission) || commission < 0) {
      return res.status(400).json({ error: 'Invalid commission value: must be a valid non-negative number' });
    }

    const rawStatus = payload.status;
    if (rawStatus === undefined || rawStatus === null || isNaN(Number(rawStatus))) {
      return res.status(400).json({ error: 'Missing or invalid status code' });
    }
    const accessTradeStatus = Number(rawStatus);
    const newStatus = mapAccessTradeStatus(accessTradeStatus);

    const isConfirmed = Number(payload.is_confirmed || payload.isConfirmed || 0) === 1;
    const salesAmount = Number(payload.sales_amount || payload.salesAmount || 0);
    const campaignId = String(payload.campaign_id || payload.campaignId || '').trim();
    const campaignName = String(payload.campaign_name || payload.campaignName || '').trim();
    const productId = String(payload.product_id || payload.productId || '').trim();
    const productName = String(payload.product_name || payload.productName || 'Sản phẩm Shopee').trim();
    const productPrice = Number(payload.product_price || payload.productPrice || 0);
    const productQuantity = Number(payload.product_quantity || payload.productQuantity || 1);
    const categoryName = String(payload.category_name || payload.categoryName || '').trim();

    // Idempotency event hash dựa trên conversionId + status + commission + isConfirmed
    const webhookEventId = crypto.createHash('sha256')
      .update(`${conversionId}_${userId}_${accessTradeStatus}_${commission}_${Number(isConfirmed)}`)
      .digest('hex');

    const webhookEventRef = db.collection('webhook_events').doc(webhookEventId);
    const userRef = db.collection('users').doc(userId);
    const txRef = db.collection('transactions').doc(conversionId);

    await db.runTransaction(async (transaction) => {
      // 1. Kiểm tra idempotency webhook event
      const webhookDoc = await transaction.get(webhookEventRef);
      if (webhookDoc.exists && webhookDoc.data().processed) {
        console.log(`Duplicate webhook event ${webhookEventId} ignored (Idempotent).`);
        return;
      }

      // 2. Validate user existence (FAIL CLOSED: không auto-create user)
      const userDoc = await transaction.get(userRef);
      if (!userDoc.exists) {
        throw new Error(`Unknown user id: ${userId}. Webhook rejected for reconciliation.`);
      }

      // 3. Fetch existing transaction theo conversionId
      const txDoc = await transaction.get(txRef);
      const userData = userDoc.data();
      const currentPending = userData.balancePending || 0;
      const currentAvailable = userData.balanceAvailable || 0;

      const txData = txDoc.exists ? txDoc.data() : null;
      
      // Kiểm tra data consistency nếu transaction đã tồn tại
      if (txData) {
        if (txData.userId && txData.userId !== userId) {
          throw new Error(`Data conflict: conversion ${conversionId} belongs to user ${txData.userId}, but webhook claims ${userId}`);
        }
      }

      const oldStatus = txData ? txData.status : 'none';
      const oldCashback = txData ? (txData.cashbackAmount || 0) : 0;
      const newCashback = calculateCashback(commission, payload);
      const cashbackDelta = newCashback - oldCashback;

      let deltaPending = 0;
      let deltaAvailable = 0;
      let ledgerType = 'CASHBACK_PENDING';

      // State Machine & Money Delta calculation
      switch (oldStatus) {
        case 'none': // NONE -> New
          if (newStatus === 'pending') {
            deltaPending = newCashback;
            ledgerType = 'CASHBACK_PENDING';
          } else if (newStatus === 'approved') {
            deltaAvailable = newCashback;
            ledgerType = 'CASHBACK_APPROVED';
          } else if (newStatus === 'cancelled') {
            // Không đổi balance khi đơn mới tạo đã hủy
            ledgerType = 'CASHBACK_CANCELLED';
          }
          break;

        case 'pending':
          if (newStatus === 'pending') {
            // Commission update delta
            deltaPending = cashbackDelta;
            ledgerType = cashbackDelta >= 0 ? 'CASHBACK_ADJUSTMENT' : 'CASHBACK_ADJUSTMENT';
          } else if (newStatus === 'approved') {
            // Pending -> Approved: rút toàn bộ oldCashback khỏi pending, cộng newCashback vào available
            deltaPending = -oldCashback;
            deltaAvailable = newCashback;
            ledgerType = 'CASHBACK_APPROVED';
          } else if (newStatus === 'cancelled') {
            // Pending -> Cancelled: rút toàn bộ oldCashback khỏi pending
            deltaPending = -oldCashback;
            ledgerType = 'CASHBACK_REVERSAL';
          }
          break;

        case 'approved':
          if (newStatus === 'approved') {
            // Commission update delta trên approved -> điều chỉnh trực tiếp balanceAvailable
            deltaAvailable = cashbackDelta;
            ledgerType = 'CASHBACK_ADJUSTMENT';
          } else if (newStatus === 'cancelled') {
            // Approved -> Cancelled: đảo ngược available balance
            deltaAvailable = -oldCashback;
            ledgerType = 'CASHBACK_REVERSAL';
          } else if (newStatus === 'pending') {
            throw new Error('Invalid state transition from approved to pending');
          }
          break;

        case 'cancelled':
          if (newStatus === 'cancelled') {
            // No-op idempotent
            deltaPending = 0;
            deltaAvailable = 0;
            ledgerType = 'CASHBACK_CANCELLED';
          } else {
            // Strict terminal state: cancelled -> approved or cancelled -> pending is REJECTED
            throw new Error(`Invalid state transition from cancelled to ${newStatus}`);
          }
          break;

        default:
          throw new Error(`Unsupported existing transaction status: ${oldStatus}`);
      }

      const newPending = currentPending + deltaPending;
      const newAvailable = currentAvailable + deltaAvailable;

      if (newPending < 0 || newAvailable < 0) {
        throw new Error('Financial invariant violation: resulting balance cannot be negative.');
      }

      // 4. Update User Balance atomically
      transaction.update(userRef, {
        balancePending: newPending,
        balanceAvailable: newAvailable,
        updatedAt: admin.firestore.FieldValue.serverTimestamp()
      });

      // 5. Update Transaction document (Key = conversionId)
      const now = admin.firestore.FieldValue.serverTimestamp();
      const transactionPayload = {
        conversionId: conversionId,
        transactionId: transactionId || (txData ? txData.transactionId : ''),
        orderId: orderId || (txData ? txData.orderId : ''),
        userId: userId,
        campaignId: campaignId,
        campaignName: campaignName,
        productId: productId,
        productName: productName,
        productPrice: productPrice,
        productQuantity: productQuantity,
        categoryName: categoryName,
        salesAmount: salesAmount,
        commission: commission,
        cashbackAmount: newCashback,
        accessTradeStatus: accessTradeStatus,
        isConfirmed: isConfirmed,
        status: newStatus,
        currency: 'VND',
        updatedAt: now
      };

      if (!txDoc.exists) {
        transactionPayload.createdAt = now;
        transaction.set(txRef, transactionPayload);
      } else {
        transaction.update(txRef, transactionPayload);
      }

      // 6. Write Transaction Event
      const txEventRef = db.collection('transaction_events').doc('TXE_' + crypto.randomUUID());
      transaction.set(txEventRef, {
        eventId: txEventRef.id,
        conversionId: conversionId,
        transactionId: transactionId,
        orderId: orderId,
        userId: userId,
        oldStatus: oldStatus,
        newStatus: newStatus,
        oldCashback: oldCashback,
        newCashback: newCashback,
        deltaCashback: deltaPending !== 0 ? deltaPending : deltaAvailable,
        eventType: ledgerType.toLowerCase(),
        source: 'accesstrade_webhook',
        createdAt: now
      });

      // 7. Write Immutable Ledger Entry
      const ledgerRef = db.collection('ledger').doc('LEDGER_' + crypto.randomUUID());
      const ledgerAmount = Math.abs(deltaPending !== 0 ? deltaPending : deltaAvailable);
      transaction.set(ledgerRef, {
        ledgerId: ledgerRef.id,
        userId: userId,
        conversionId: conversionId,
        transactionId: transactionId,
        orderId: orderId,
        type: ledgerType,
        amount: ledgerAmount,
        currency: 'VND',
        balancePendingBefore: currentPending,
        balancePendingAfter: newPending,
        balanceAvailableBefore: currentAvailable,
        balanceAvailableAfter: newAvailable,
        createdAt: now
      });

      // 8. Mark Webhook Event as Processed
      transaction.set(webhookEventRef, {
        eventId: webhookEventId,
        conversionId: conversionId,
        orderId: orderId,
        userId: userId,
        status: accessTradeStatus,
        commission: commission,
        processed: true,
        receivedAt: now
      });
    });

    return res.status(200).json({ success: true, message: 'Webhook processed successfully with conversionId primary identity' });
  } catch (error) {
    console.error('Webhook Processing Error:', error.message);
    try {
      const failEventId = 'FAIL_' + crypto.randomUUID();
      await db.collection('webhook_events').doc(failEventId).set({
        eventId: failEventId,
        payload: req.body,
        error: error.message,
        processed: false,
        receivedAt: admin.firestore.FieldValue.serverTimestamp()
      });
    } catch (logErr) {
      console.error('Failed to log webhook failure event:', logErr);
    }
    return res.status(400).json({ error: error.message || 'Internal Server Error' });
  }
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
  console.log(`Casi Secure Production Backend & Webhook server is running on port ${PORT}`);
});
