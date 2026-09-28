const express = require('express');
const admin = require('firebase-admin');

// Khởi tạo Firebase Admin SDK
// Sử dụng biến môi trường FIREBASE_SERVICE_ACCOUNT (chuỗi JSON) hoặc GOOGLE_APPLICATION_CREDENTIALS
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

// Endpoint kiểm tra server
app.get('/', (req, res) => {
  res.send('Casi AccessTrade Webhook Server is running! 🚀');
});

// Endpoint POST /webhook nhận dữ liệu từ AccessTrade
app.post('/webhook', async (req, res) => {
  try {
    const { order_id, sub_id, pub_revenue, status } = req.body;

    console.log('Received AccessTrade Webhook:', req.body);

    if (!order_id || !sub_id) {
      return res.status(400).json({ error: 'Missing required fields: order_id or sub_id (userId)' });
    }

    const userId = sub_id;
    const revenue = Number(pub_revenue) || 0;
    const orderStatus = Number(status); // 1: Pending, 2: Approved, 3: Cancelled

    const userRef = db.collection('users').doc(userId);
    const txRef = db.collection('transactions').doc(order_id);

    await db.runTransaction(async (transaction) => {
      const userDoc = await transaction.get(userRef);
      const txDoc = await transaction.get(txRef);

      let currentPending = userDoc.exists 
        ? (userDoc.data().balance_pending || userDoc.data().balancePending || 0) 
        : 0;
      let currentAvailable = userDoc.exists 
        ? (userDoc.data().balance_available || userDoc.data().balanceAvailable || 0) 
        : 0;

      // Khởi tạo user nếu chưa tồn tại trên Firestore
      if (!userDoc.exists) {
        transaction.set(userRef, {
          userId: userId,
          balance_pending: 0,
          balance_available: 0,
          createdAt: admin.firestore.FieldValue.serverTimestamp()
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
              createdAt: admin.firestore.FieldValue.serverTimestamp()
            });
          } else {
            console.log(`Order ${order_id} already exists with status ${oldStatus}. Skipping duplicate pending credit.`);
          }
          break;
        }
        case 2: {
          // Status 2: Thành công (Approved)
          if (!txDoc.exists) {
            currentAvailable += revenue;
            transaction.set(txRef, {
              orderId: order_id,
              userId: userId,
              cashbackAmount: revenue,
              status: 'approved',
              createdAt: admin.firestore.FieldValue.serverTimestamp(),
              updatedAt: admin.firestore.FieldValue.serverTimestamp()
            });
          } else if (oldStatus === 'pending') {
            currentPending = Math.max(0, currentPending - oldAmount);
            currentAvailable += oldAmount;
            transaction.update(txRef, {
              status: 'approved',
              updatedAt: admin.firestore.FieldValue.serverTimestamp()
            });
          } else if (oldStatus === 'cancelled') {
            currentAvailable += revenue;
            transaction.update(txRef, {
              status: 'approved',
              updatedAt: admin.firestore.FieldValue.serverTimestamp()
            });
          } else {
            console.log(`Order ${order_id} is already approved. No action needed.`);
          }
          break;
        }
        case 3: {
          // Status 3: Hủy (Cancelled)
          if (!txDoc.exists) {
            transaction.set(txRef, {
              orderId: order_id,
              userId: userId,
              cashbackAmount: revenue,
              status: 'cancelled',
              createdAt: admin.firestore.FieldValue.serverTimestamp(),
              updatedAt: admin.firestore.FieldValue.serverTimestamp()
            });
          } else if (oldStatus === 'pending') {
            currentPending = Math.max(0, currentPending - oldAmount);
            transaction.update(txRef, {
              status: 'cancelled',
              updatedAt: admin.firestore.FieldValue.serverTimestamp()
            });
          } else if (oldStatus === 'approved') {
            currentAvailable = Math.max(0, currentAvailable - oldAmount);
            transaction.update(txRef, {
              status: 'cancelled',
              updatedAt: admin.firestore.FieldValue.serverTimestamp()
            });
          }
          break;
        }
        default:
          console.warn(`Unknown AccessTrade status received: ${orderStatus}`);
      }

      // Cập nhật lại số dư user trên Firestore
      transaction.update(userRef, {
        balance_pending: currentPending,
        balancePending: currentPending,
        balance_available: currentAvailable,
        balanceAvailable: currentAvailable,
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
  console.log(`Casi Webhook server is running on port ${PORT}`);
});
