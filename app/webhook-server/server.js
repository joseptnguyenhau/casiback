const express = require('express');
const admin = require('firebase-admin');
const axios = require('axios');

// Khởi tạo Firebase Admin SDK kết nối trực tiếp với dự án 'casiback-5b7e2'
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

// Helper: Verify Firebase Auth ID Token from Authorization header
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

// Endpoint kiểm tra server
app.get('/', (req, res) => {
  res.send('Casi Production Secure Backend & Webhook Server is running! 🚀');
});

// ==========================================
// 1. CREATE AFFILIATE LINK (Trusted Backend)
// ==========================================
app.post('/api/create-affiliate-link', verifyAuthToken, async (req, res) => {
  try {
    const userId = req.user.uid;
    const { originalUrl } = req.body;

    if (!originalUrl || (!originalUrl.includes('shopee.vn') && !originalUrl.includes('shp.ee') && !originalUrl.includes('shope.ee'))) {
      return res.status(400).json({ error: 'Link Shopee không hợp lệ.' });
    }

    const atToken = process.env.ACCESSTRADE_API_TOKEN || 'Token access_trade_token_sample_abc123';
    
    let affiliateLink = `https://shope.ee/${userId}_${Date.now().toString().slice(-6)}`;
    let productName = 'Sản phẩm Shopee (AccessTrade)';
    const estimatedCashback = 25000L; // Long integer VND (hoặc tính toán dựa trên business rules)

    try {
      const response = await axios.post(
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
          timeout: 8000
        }
      );

      const data = response.data?.data;
      if (Array.isArray(data) && data.length > 0) {
        affiliateLink = data[0].short_link || data[0].product_link || affiliateLink;
        productName = data[0].product_name || data[0].title || productName;
      } else if (data && typeof data === 'object') {
        affiliateLink = data.short_link || data.product_link || affiliateLink;
        productName = data.product_name || productName;
      }
    } catch (atError) {
      console.warn('AccessTrade API call failed, using fallback tracking link:', atError.message);
    }

    // Tạo tracking record trên Firestore (Tracking không chứa monetary fields)
    const trackingId = 'TRK_' + Date.now();
    await db.collection('tracking').doc(trackingId).set({
      trackingId: trackingId,
      userId: userId,
      originalLink: originalUrl,
      affiliateLink: affiliateLink,
      subId: userId,
      status: 'created',
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp()
    });

    return res.status(200).json({
      success: true,
      affiliateLink: affiliateLink,
      estimatedCashback: 25000,
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
    const { bankName, accountNumber, accountHolder, amount } = req.body;

    const withdrawalAmount = Number(amount) || 0;
    if (withdrawalAmount < 20000) {
      return res.status(400).json({ error: 'Số tiền rút tối thiểu là 20.000đ' });
    }

    if (!bankName || !accountNumber || !accountHolder) {
      return res.status(400).json({ error: 'Vui lòng cung cấp đầy đủ thông tin ngân hàng.' });
    }

    const userRef = db.collection('users').doc(userId);
    const withdrawalId = 'WD_' + Date.now();
    const withdrawalRef = db.collection('withdrawals').doc(withdrawalId);

    await db.runTransaction(async (transaction) => {
      const userDoc = await transaction.get(userRef);
      if (!userDoc.exists) {
        throw new Error('Tài khoản người dùng không tồn tại.');
      }

      const userData = userDoc.data();
      const currentAvailable = userData.balanceAvailable || userData.balance_available || 0;

      if (currentAvailable < withdrawalAmount) {
        throw new Error('Số dư khả dụng không đủ để rút số tiền này.');
      }

      const newAvailable = Math.max(0, currentAvailable - withdrawalAmount);

      // Cập nhật số dư user (trừ balanceAvailable)
      transaction.update(userRef, {
        balanceAvailable: newAvailable,
        balance_available: newAvailable,
        updatedAt: admin.firestore.FieldValue.serverTimestamp()
      });

      // Tạo withdrawal record với trạng thái pending (chỉ backend/admin đổi status)
      transaction.set(withdrawalRef, {
        withdrawalId: withdrawalId,
        userId: userId,
        bankName: bankName,
        accountNumber: accountNumber,
        accountHolder: accountHolder.toUpperCase(),
        amount: withdrawalAmount,
        status: 'pending',
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
        updatedAt: admin.firestore.FieldValue.serverTimestamp()
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
app.post('/webhook', async (req, res) => {
  try {
    const { order_id, sub_id, pub_revenue, status } = req.body;

    console.log('Received AccessTrade Webhook:', req.body);

    if (!order_id || !sub_id) {
      return res.status(400).json({ error: 'Missing order_id or sub_id (userId)' });
    }

    const userId = sub_id;
    const revenue = Math.round(Number(pub_revenue) || 0); // Long integer VND
    const orderStatus = Number(status); // 1: Pending, 2: Approved, 3: Cancelled

    const userRef = db.collection('users').doc(userId);
    const txRef = db.collection('transactions').doc(order_id);

    await db.runTransaction(async (transaction) => {
      const userDoc = await transaction.get(userRef);
      const txDoc = await transaction.get(txRef);

      let currentPending = userDoc.exists 
        ? (userDoc.data().balanceAvailable !== undefined || userDoc.data().balancePending !== undefined ? 
           (userDoc.data().balancePending || userDoc.data().balance_pending || 0) : 0) 
        : 0;
      let currentAvailable = userDoc.exists 
        ? (userDoc.data().balanceAvailable || userDoc.data().balance_available || 0) 
        : 0;

      if (!userDoc.exists) {
        transaction.set(userRef, {
          userId: userId,
          balanceAvailable: 0,
          balancePending: 0,
          createdAt: admin.firestore.FieldValue.serverTimestamp(),
          updatedAt: admin.firestore.FieldValue.serverTimestamp()
        }, { merge: true });
      }

      const txData = txDoc.exists ? txDoc.data() : null;
      const oldStatus = txData ? txData.status : null;
      const oldAmount = txData ? (txData.cashbackAmount || revenue) : revenue;

      switch (orderStatus) {
        case 1: {
          // Status 1: Tạm tính (Pending)
          if (!txDoc.exists) {
            currentPending += revenue;
            transaction.set(txRef, {
              orderId: order_id,
              userId: userId,
              cashbackAmount: revenue,
              status: 'pending',
              createdAt: admin.firestore.FieldValue.serverTimestamp(),
              updatedAt: admin.firestore.FieldValue.serverTimestamp()
            });
          }
          break;
        }
        case 2: {
          // Status 2: Thành công (Approved) -> Chuyển từ pending sang available
          if (!txDoc.exists) {
            currentAvailable += revenue;
            transaction.set(txRef, {
              orderId: order_id,
              userId: userId,
              cashbackAmount: revenue,
              status: 'approved',
              createdAt: admin.firestore.FieldValue.serverTimestamp(),
              updatedAt: admin.firestore.FieldValue.serverTimestamp(),
              approvedAt: admin.firestore.FieldValue.serverTimestamp()
            });
          } else if (oldStatus === 'pending') {
            currentPending = Math.max(0, currentPending - oldAmount);
            currentAvailable += oldAmount;
            transaction.update(txRef, {
              status: 'approved',
              updatedAt: admin.firestore.FieldValue.serverTimestamp(),
              approvedAt: admin.firestore.FieldValue.serverTimestamp()
            });
          } else if (oldStatus === 'cancelled') {
            currentAvailable += revenue;
            transaction.update(txRef, {
              status: 'approved',
              updatedAt: admin.firestore.FieldValue.serverTimestamp(),
              approvedAt: admin.firestore.FieldValue.serverTimestamp()
            });
          }
          break;
        }
        case 3: {
          // Status 3: Hủy (Cancelled) -> Trừ khỏi pending hoặc available
          if (!txDoc.exists) {
            transaction.set(txRef, {
              orderId: order_id,
              userId: userId,
              cashbackAmount: revenue,
              status: 'cancelled',
              createdAt: admin.firestore.FieldValue.serverTimestamp(),
              updatedAt: admin.firestore.FieldValue.serverTimestamp(),
              cancelledAt: admin.firestore.FieldValue.serverTimestamp()
            });
          } else if (oldStatus === 'pending') {
            currentPending = Math.max(0, currentPending - oldAmount);
            transaction.update(txRef, {
              status: 'cancelled',
              updatedAt: admin.firestore.FieldValue.serverTimestamp(),
              cancelledAt: admin.firestore.FieldValue.serverTimestamp()
            });
          } else if (oldStatus === 'approved') {
            currentAvailable = Math.max(0, currentAvailable - oldAmount);
            transaction.update(txRef, {
              status: 'cancelled',
              updatedAt: admin.firestore.FieldValue.serverTimestamp(),
              cancelledAt: admin.firestore.FieldValue.serverTimestamp()
            });
          }
          break;
        }
        default:
          console.warn(`Unknown AccessTrade status: ${orderStatus}`);
      }

      transaction.update(userRef, {
        balancePending: currentPending,
        balance_pending: currentPending,
        balanceAvailable: currentAvailable,
        balance_available: currentAvailable,
        updatedAt: admin.firestore.FieldValue.serverTimestamp()
      });
    });

    return res.status(200).json({ success: true, message: 'Webhook processed successfully' });
  } catch (error) {
    console.error('Webhook Error:', error);
    return res.status(500).json({ error: error.message || 'Internal Server Error' });
  }
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
  console.log(`Casi Secure Backend & Webhook server is running on port ${PORT}`);
});
