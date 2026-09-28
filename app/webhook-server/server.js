const express = require('express');
const admin = require('firebase-admin');

// Khởi tạo Firebase Admin SDK kết nối trực tiếp với dự án 'casiback-5b7e2'
if (process.env.FIREBASE_SERVICE_ACCOUNT) {
  const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
  admin.initializeApp({
    credential: admin.credential.cert(serviceAccount)
  });
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
      return res.status(400).json({ error: 'Missing order_id or sub_id (userId)' });
    }

    const userId = sub_id;
    const revenue = Number(pub_revenue) || 0;
    const orderStatus = Number(status); // 1: Pending, 2: Approved, 3: Cancelled

    const userRef = db.collection('users').doc(userId);
    const txRef = db.collection('transactions').doc(order_id);

    await db.runTransaction(async (transaction) => {
      const userDoc = await transaction.get(userRef);
      const txDoc = await transaction.get(txRef);

      const currentPending = userDoc.exists 
        ? (userDoc.data().balance_pending || userDoc.data().balancePending || 0) 
        : 0;
      const currentAvailable = userDoc.exists 
        ? (userDoc.data().balance_available || userDoc.data().balanceAvailable || 0) 
        : 0;

      // Khởi tạo user nếu chưa tồn tại
      if (!userDoc.exists) {
        transaction.set(userRef, {
          userId: userId,
          balance_pending: 0,
          balance_available: 0,
          createdAt: admin.firestore.FieldValue.serverTimestamp()
        }, { merge: true });
      }

      switch (orderStatus) {
        case 1: {
          // Status 1: Tạm tính (Pending)
          let newPending = currentPending;
          if (!txDoc.exists) {
            newPending += revenue;
            transaction.set(txRef, {
              orderId: order_id,
              userId: userId,
              cashbackAmount: revenue,
              status: 'pending',
              createdAt: admin.firestore.FieldValue.serverTimestamp()
            });
          }
          transaction.update(userRef, {
            balance_pending: newPending,
            balancePending: newPending
          });
          break;
        }
        case 2: {
          // Status 2: Thành công (Approved) -> Chuyển từ pending sang available
          let newPending = currentPending;
          let newAvailable = currentAvailable;

          if (txDoc.exists) {
            const txData = txDoc.data();
            const oldStatus = txData.status;
            const oldAmount = txData.cashbackAmount || revenue;

            if (oldStatus === 'pending') {
              newPending = Math.max(0, currentPending - oldAmount);
              newAvailable += oldAmount;
            } else if (oldStatus !== 'approved') {
              newAvailable += revenue;
            }

            transaction.update(txRef, {
              status: 'approved',
              updatedAt: admin.firestore.FieldValue.serverTimestamp()
            });
          } else {
            newAvailable += revenue;
            transaction.set(txRef, {
              orderId: order_id,
              userId: userId,
              cashbackAmount: revenue,
              status: 'approved',
              createdAt: admin.firestore.FieldValue.serverTimestamp()
            });
          }

          transaction.update(userRef, {
            balance_pending: newPending,
            balancePending: newPending,
            balance_available: newAvailable,
            balanceAvailable: newAvailable
          });
          break;
        }
        case 3: {
          // Status 3: Hủy (Cancelled) -> Trừ khỏi balance_pending
          let newPending = currentPending;

          if (txDoc.exists) {
            const txData = txDoc.data();
            const oldStatus = txData.status;
            const oldAmount = txData.cashbackAmount || revenue;

            if (oldStatus === 'pending') {
              newPending = Math.max(0, currentPending - oldAmount);
            }

            transaction.update(txRef, {
              status: 'cancelled',
              updatedAt: admin.firestore.FieldValue.serverTimestamp()
            });
          }

          transaction.update(userRef, {
            balance_pending: newPending,
            balancePending: newPending
          });
          break;
        }
        default:
          console.log(`Unknown status received: ${orderStatus}`);
      }
    });

    return res.status(200).json({ success: true, message: 'Webhook processed successfully' });
  } catch (error) {
    console.error('Webhook Error:', error);
    return res.status(500).json({ error: error.message });
  }
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
  console.log(`Casi Webhook server is running on port ${PORT}`);
});
