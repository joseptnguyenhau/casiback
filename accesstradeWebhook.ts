import { onRequest } from "firebase-functions/v2/https";
import * as admin from "firebase-admin";

// Khởi tạo Firebase Admin SDK
if (!admin.apps.length) {
  admin.initializeApp();
}
const db = admin.firestore();

interface AccessTradeWebhookPayload {
  order_id: string;
  sub_id: string; // Mã ID người dùng trên Casi (userId)
  pub_revenue: number | string; // Số tiền hoa hồng nhận được
  status: number; // 1: Tạm tính (Pending), 2: Thành công (Approved), 3: Hủy (Rejected)
}

/**
 * Firebase Cloud Function (HTTP Endpoint): /accesstrade-webhook
 * Nhận webhook postback từ AccessTrade và tự động cập nhật số dư / trạng thái giao dịch trên Firestore an toàn.
 */
export const accesstradeWebhook = onRequest(async (req, res) => {
  // Chỉ chấp nhận phương thức POST từ AccessTrade server
  if (req.method !== "POST") {
    res.status(405).send("Method Not Allowed");
    return;
  }

  try {
    const payload = req.body as AccessTradeWebhookPayload;
    const orderId = payload.order_id;
    const userId = payload.sub_id;
    const revenue = Number(payload.pub_revenue) || 0;
    const status = Number(payload.status);

    if (!orderId || !userId) {
      res.status(400).json({ error: "Missing required fields: order_id or sub_id (userId)" });
      return;
    }

    const userRef = db.collection("users").doc(userId);
    const txRef = db.collection("transactions").doc(orderId);

    // Sử dụng Firestore Transaction để đảm bảo tính nguyên tử (Atomic & Safe) khi cập nhật số dư
    await db.runTransaction(async (transaction) => {
      const userSnapshot = await transaction.get(userRef);
      const txSnapshot = await transaction.get(txRef);

      const currentPending = userSnapshot.exists 
        ? (userSnapshot.data()?.balance_pending || userSnapshot.data()?.balancePending || 0) 
        : 0;
      const currentAvailable = userSnapshot.exists 
        ? (userSnapshot.data()?.balance_available || userSnapshot.data()?.balanceAvailable || 0) 
        : 0;

      // Nếu user chưa tồn tại trên Firestore, khởi tạo document mặc định
      if (!userSnapshot.exists) {
        transaction.set(userRef, {
          userId: userId,
          balance_pending: 0,
          balance_available: 0,
          createdAt: admin.firestore.FieldValue.serverTimestamp()
        }, { merge: true });
      }

      switch (status) {
        case 1: {
          // Status 1: Tạm tính (Pending)
          let newPending = currentPending;
          if (!txSnapshot.exists) {
            newPending += revenue;
            transaction.set(txRef, {
              orderId: orderId,
              userId: userId,
              cashbackAmount: revenue,
              status: "pending",
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

          if (txSnapshot.exists) {
            const txData = txSnapshot.data();
            const oldStatus = txData?.status;
            const oldAmount = txData?.cashbackAmount || revenue;

            if (oldStatus === "pending") {
              newPending = Math.max(0, currentPending - oldAmount);
              newAvailable += oldAmount;
            } else if (oldStatus !== "approved") {
              newAvailable += revenue;
            }

            transaction.update(txRef, {
              status: "approved",
              updatedAt: admin.firestore.FieldValue.serverTimestamp()
            });
          } else {
            newAvailable += revenue;
            transaction.set(txRef, {
              orderId: orderId,
              userId: userId,
              cashbackAmount: revenue,
              status: "approved",
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
          // Status 3: Hủy (Rejected) -> Trừ khỏi balance_pending
          let newPending = currentPending;

          if (txSnapshot.exists) {
            const txData = txSnapshot.data();
            const oldStatus = txData?.status;
            const oldAmount = txData?.cashbackAmount || revenue;

            if (oldStatus === "pending") {
              newPending = Math.max(0, currentPending - oldAmount);
            }

            transaction.update(txRef, {
              status: "cancelled",
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
          console.warn(`Unknown AccessTrade status received: ${status}`);
      }
    });

    res.status(200).json({ success: true, message: "AccessTrade webhook processed successfully" });
  } catch (error: any) {
    console.error("Error processing AccessTrade webhook:", error);
    res.status(500).json({ error: error.message || "Internal Server Error" });
  }
});
