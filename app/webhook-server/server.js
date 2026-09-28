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

// Middleware: Optional Webhook Secret Verification
function verifyWebhookSecret(req, res, next) {
  const webhookSecret = process.env.WEBHOOK_SECRET;
  if (webhookSecret) {
    const headerSecret = req.headers['x-webhook-secret'] || req.query.secret;
    if (headerSecret !== webhookSecret) {
      console.warn('Unauthorized webhook attempt with invalid/missing secret');
      return res.status(401).json({ error: 'Unauthorized webhook' });
    }
  }
  next();
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

    // Gọi AccessTrade API thực tế
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
      // TUYỆT ĐỐI KHÔNG trả link giả / success giả khi AccessTrade lỗi
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
    console.log(`Processing withdrawal request for user ${userId}, amount: ${withdrawalAmount}, bank: ${bankName}, acc: ${maskedAcc}`);

    await db.runTransaction(async (transaction) => {
      // Kiểm tra idempotency nếu có requestId
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

      // 1. Cập nhật số dư user (trừ balanceAvailable)
      transaction.update(userRef, {
        balanceAvailable: newAvailable,
        updatedAt: admin.firestore.FieldValue.serverTimestamp()
      });

      // 2. Tạo withdrawal record
      transaction.set(withdrawalRef, {
        withdrawalId: withdrawalId,
        userId: userId,
        bankName: bankName,
        accountNumber: maskedAcc, // Không lưu số tài khoản đầy đủ thô
        accountHolder: accountHolder.toUpperCase(),
        amount: withdrawalAmount,
        status: 'pending',
        idempotencyKey: idempotencyKey || null,
        createdAt: admin.firestore.FieldValue.serverTimestamp()
      });

      // 3. Ghi ledger bất biến (withdrawal_reserved)
      transaction.set(ledgerRef, {
        ledgerId: ledgerRef.id,
        userId: userId,
        withdrawalId: withdrawalId,
        type: 'withdrawal_reserved',
        amount: withdrawalAmount,
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
    const { order_id, sub_id, pub_revenue, status } = req.body;

    console.log('Received AccessTrade Webhook:', { order_id, sub_id, pub_revenue, status });

    // Validate bắt buộc
    if (!order_id || typeof order_id !== 'string' || order_id.trim() === '') {
      return res.status(400).json({ error: 'Missing or invalid order_id' });
    }
    if (!sub_id || typeof sub_id !== 'string' || sub_id.trim() === '') {
      return res.status(400).json({ error: 'Missing or invalid sub_id (userId)' });
    }
    if (pub_revenue === undefined || pub_revenue === null || isNaN(Number(pub_revenue)) || Number(pub_revenue) <= 0) {
      return res.status(400).json({ error: 'Invalid pub_revenue amount' });
    }
    if (status === undefined || status === null || isNaN(Number(status))) {
      return res.status(400).json({ error: 'Invalid status' });
    }

    const userId = sub_id.trim();
    const revenue = Math.round(Number(pub_revenue));
    const orderStatus = Number(status); // 1: Pending, 2: Approved, 3: Cancelled

    // Idempotency key cho webhook event
    const webhookEventId = crypto.createHash('sha256').update(`${order_id}_${orderStatus}_${revenue}`).digest('hex');
    const webhookEventRef = db.collection('webhook_events').doc(webhookEventId);

    const userRef = db.collection('users').doc(userId);
    const txRef = db.collection('transactions').doc(order_id);

    await db.runTransaction(async (transaction) => {
      // Kiểm tra webhook idempotency
      const webhookDoc = await transaction.get(webhookEventRef);
      if (webhookDoc.exists && webhookDoc.data().processed) {
        console.log(`Duplicate webhook event ${webhookEventId} ignored (Idempotent).`);
        return;
      }

      const userDoc = await transaction.get(userRef);
      if (!userDoc.exists) {
        // KHÔNG tự động tạo user mới cho webhook từ sub_id lạ
        throw new Error(`Unknown user sub_id: ${userId}. Webhook rejected for reconciliation.`);
      }

      const txDoc = await transaction.get(txRef);
      const userData = userDoc.data();
      let currentPending = userData.balancePending || 0;
      let currentAvailable = userData.balanceAvailable || 0;

      const txData = txDoc.exists ? txDoc.data() : null;
      const oldStatus = txData ? txData.status : null;
      const oldAmount = txData ? (txData.cashbackAmount || revenue) : revenue;

      let newStatus = 'pending';
      let deltaPending = 0;
      let deltaAvailable = 0;
      let eventType = 'cashback_pending';

      switch (orderStatus) {
        case 1: // Pending
          newStatus = 'pending';
          if (!txDoc.exists) {
            deltaPending = revenue;
            eventType = 'cashback_pending';
          } else if (oldStatus === 'approved' || oldStatus === 'cancelled') {
            throw new Error(`Invalid state transition from ${oldStatus} to pending`);
          }
          break;

        case 2: // Approved
          newStatus = 'approved';
          if (!txDoc.exists) {
            deltaAvailable = revenue;
            eventType = 'cashback_approved';
          } else if (oldStatus === 'pending') {
            deltaPending = -oldAmount;
            deltaAvailable = oldAmount;
            eventType = 'pending_to_approved';
          } else if (oldStatus === 'cancelled') {
            deltaAvailable = revenue;
            eventType = 'cancelled_to_approved';
          } else if (oldStatus === 'approved') {
            // No-op idempotent
          }
          break;

        case 3: // Cancelled
          newStatus = 'cancelled';
          if (!txDoc.exists) {
            eventType = 'cashback_cancelled';
            // Không thay đổi balance vì đơn mới tạo đã hủy
          } else if (oldStatus === 'pending') {
            deltaPending = -oldAmount;
            eventType = 'pending_to_cancelled';
          } else if (oldStatus === 'approved') {
            deltaAvailable = -oldAmount;
            eventType = 'approved_to_cancelled';
          } else if (oldStatus === 'cancelled') {
            // No-op idempotent
          }
          break;

        default:
          throw new Error(`Unsupported AccessTrade status: ${orderStatus}`);
      }

      const newPending = currentPending + deltaPending;
      const newAvailable = currentAvailable + deltaAvailable;

      if (newPending < 0 || newAvailable < 0) {
        throw new Error('Financial invariant violation: resulting balance cannot be negative.');
      }

      // Cập nhật User Balance
      transaction.update(userRef, {
        balancePending: newPending,
        balanceAvailable: newAvailable,
        updatedAt: admin.firestore.FieldValue.serverTimestamp()
      });

      // Cập nhật Transaction record
      if (!txDoc.exists) {
        transaction.set(txRef, {
          orderId: order_id,
          userId: userId,
          cashbackAmount: revenue,
          status: newStatus,
          createdAt: admin.firestore.FieldValue.serverTimestamp(),
          updatedAt: admin.firestore.FieldValue.serverTimestamp()
        });
      } else {
        transaction.update(txRef, {
          status: newStatus,
          cashbackAmount: revenue,
          updatedAt: admin.firestore.FieldValue.serverTimestamp()
        });
      }

      // Ghi Transaction Event
      const txEventRef = db.collection('transaction_events').doc('TXE_' + crypto.randomUUID());
      transaction.set(txEventRef, {
        eventId: txEventRef.id,
        transactionId: order_id,
        orderId: order_id,
        userId: userId,
        oldStatus: oldStatus || 'none',
        newStatus: newStatus,
        amount: revenue,
        eventType: eventType,
        source: 'accesstrade_webhook',
        createdAt: admin.firestore.FieldValue.serverTimestamp()
      });

      // Ghi Ledger
      const ledgerRef = db.collection('ledger').doc('LEDGER_' + crypto.randomUUID());
      transaction.set(ledgerRef, {
        ledgerId: ledgerRef.id,
        userId: userId,
        transactionId: order_id,
        type: eventType,
        amount: revenue,
        balancePendingBefore: currentPending,
        balancePendingAfter: newPending,
        balanceAvailableBefore: currentAvailable,
        balanceAvailableAfter: newAvailable,
        createdAt: admin.firestore.FieldValue.serverTimestamp()
      });

      // Đánh dấu Webhook Event đã xử lý
      transaction.set(webhookEventRef, {
        eventId: webhookEventId,
        orderId: order_id,
        subId: sub_id,
        status: orderStatus,
        pubRevenue: revenue,
        processed: true,
        receivedAt: admin.firestore.FieldValue.serverTimestamp()
      });
    });

    return res.status(200).json({ success: true, message: 'Webhook processed successfully' });
  } catch (error) {
    console.error('Webhook Error:', error.message);
    // Ghi nhận webhook thất bại vào webhook_events để audit
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
      console.error('Failed to log webhook error:', logErr);
    }
    return res.status(400).json({ error: error.message || 'Internal Server Error' });
  }
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
  console.log(`Casi Secure Production Backend & Webhook server is running on port ${PORT}`);
});
