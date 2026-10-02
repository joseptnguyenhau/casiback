# CASI AI — Backup & Disaster Recovery Guide

## 1. Backup Audit Summary
- **Staging Project:** `casiback-5b7e2`
- **Current State:** Manual exports supported via Google Cloud Console / `gcloud` CLI. Automated daily scheduled backups require Cloud Scheduler + Cloud IAM setup.
- **Status:** `PARTIAL` (Manual export capability exists; automated scheduling requires cloud console setup).

## 2. Automated Backup Design (Firestore)
For Staging (`casiback-5b7e2`) & Future Production Disaster Recovery:
- **Tool:** Google Cloud Firestore Export (`gcloud firestore export gs://casi-backup-store-casiback-5b7e2`).
- **Frequency:** Daily automated cron job at 02:00 UTC via Cloud Scheduler triggering a Cloud Function / Cloud Run job or gcloud export command.
- **Storage Bucket:** `gs://casi-backup-store-casiback-5b7e2` with 30-day lifecycle retention policy.
- **IAM Least Privilege:** Service account assigned `Cloud Datastore Import Export Admin` role only on the specific Firestore database and GCS bucket.
- **Failure Detection:** Cloud Monitoring alerts on Cloud Function / Cloud Scheduler error status codes.

## 3. Disaster Recovery & Restore Procedure
In the event of data corruption or disaster:
1. **Isolate:** Stop write traffic (e.g., put backend in maintenance mode or pause webhook receiver).
2. **Select Backup:** Identify the latest healthy backup timestamp in Cloud Storage (`gs://casi-backup-store-casiback-5b7e2/[TIMESTAMP]/`).
3. **Test Restore Environment:** Import backup into an isolated staging/test Firestore database:
   ```bash
   gcloud firestore import gs://casi-backup-store-casiback-5b7e2/[TIMESTAMP]/ --database=test-restore-db
   ```
4. **Validate Data Integrity:**
   - Verify document counts in `users`, `transactions`, `withdrawals`, `ledger`, `paymentMethods`.
   - Ensure financial balance integrity (`balancePending`, `balanceAvailable`).
   - Confirm immutable ledger entries match transaction records.
   - Confirm ownership UID mapping and Firestore security rules enforcement.
5. **Live Restore:** Once validated, perform import into primary database or promote test database.
6. **Resume Traffic:** Re-enable backend webhook processing and user access.

## 4. Manual Cloud Actions & Verification Steps (Google Cloud Console)
1. **Bucket Creation:** `gsutil mb -p casiback-5b7e2 -l asia-southeast1 gs://casi-backup-store-casiback-5b7e2`
2. **Retention Policy:** `gsutil retention set 30d gs://casi-backup-store-casiback-5b7e2`
3. **IAM Grant:** Assign `roles/datastore.importExportAdmin` to the execution service account on project `casiback-5b7e2`.
4. **Cloud Scheduler Setup:** Schedule daily cron `0 2 * * *` targeting Firestore export API.
5. **Isolated Restore Test:** Execute `gcloud firestore import` into a sandbox test database `test-restore-db` and verify schema collections (`users`, `transactions`, `withdrawals`, `ledger`, `paymentMethods`).

