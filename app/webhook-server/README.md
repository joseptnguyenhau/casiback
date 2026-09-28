# Casi AccessTrade Webhook Server

Standalone Node.js & Express backend for receiving real-time order postbacks from AccessTrade and safely updating user cashback balances in Cloud Firestore (`casiback-5b7e2`).

## Features
- **HTTP POST Endpoint**: `/webhook`
- **Idempotency**: Prevents duplicate crediting for the same `order_id`.
- **Atomic Transactions**: Uses Firestore Transactions (`db.runTransaction`) to safely update `balance_pending`, `balance_available`, and `transactions` collection.
- **Status Handling**:
  - `status = 1`: Pending (Tạm tính)
  - `status = 2`: Approved (Thành công)
  - `status = 3`: Cancelled (Hủy)

---

## Environment Variables
Create a `.env` file or set environment variables on your hosting provider (Render, Railway, etc.):
- `PORT`: Server port (default: `3000`)
- `FIREBASE_SERVICE_ACCOUNT`: JSON string containing the Firebase Service Account private key credentials. **(DO NOT commit this file or string to GitHub!)**

---

## Local Development & Running
1. Install dependencies:
   ```bash
   npm install
   ```
2. Run the server:
   ```bash
   npm start
   ```
